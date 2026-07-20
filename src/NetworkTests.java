import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Integration tests for the EXISTING classes:
 * TcpClient.java, TcpServer.java, UdpClient.java, and UdpServer.java.
 *
 * Keep this file in the same folder/default package as those classes.
 */
public class NetworkTests {

    private static final int TCP_PORT = 5050;
    private static final int UDP_PORT = 5000;
    private static final int TIMEOUT_MS = 5_000;

    private static final byte[] BINARY_TEST_DATA = {
            0, 1, 2, 10, 13, 127,
            (byte) 128, (byte) 200, (byte) 255
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== TESTING YOUR EXISTING NETWORK CLASSES ===");
        System.out.println();

        //TEST TCP
        testTcpClient();
       //Waits briefly and prints a blank line to ensure ports and background threads finish before the next one starts.
        pauseBetweenTests();
        testTcpServer();
        pauseBetweenTests();

        //TEST UDP
        testUdpClient();
        pauseBetweenTests();
        testUdpServer();
        System.out.println();
        System.out.println("All four existing classes passed their tests.");
    }

    private static void testTcpClient() throws Exception {
        System.out.println("=== TcpClient.java ===");

        //AtomicReference gives us an object whose internal value can be updated safely. The stored value is null until set to something else.
        AtomicReference<byte[]> bytesReceivedFromClient = new AtomicReference<>();
        AtomicReference<Throwable> peerFailure = new AtomicReference<>();

        //Create a new background thread. (The temporary server must run on another thread because serverSocket.accept() blocks while waiting for client)
        Thread temporaryServerThread = new Thread(() -> {
            try (
                    //Try-with-resources block, Java automatically closes it when the block finishes.
                    ServerSocket serverSocket = new ServerSocket(TCP_PORT);
                    Socket socket = serverSocket.accept(); //Waits for the client to connect
                    //Build the temp server's input and output stream
                    DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))
            ) {
                bytesReceivedFromClient.set(readTcpFrame(input)); //reads one complete length-prefixed message from client, then store that recevied byte array in the shared AtomicReference
                //send message
                writeTcpFrame(output, "message-from-temporary-server".getBytes(StandardCharsets.UTF_8));
                Thread.sleep(150); //gives time for the client's receive thread to process before the server exits
                //send a properly framed exit message
                writeTcpFrame(output, "exit".getBytes(StandardCharsets.UTF_8));

            } catch (Throwable e) {
                //store the problem if anything goes wrong.
                peerFailure.set(e);
            }
        }, "temporary-server-for-TcpClient"); //Ends the thread declaration and gives it a descriptive name. (Thread names help when reading stack traces or debugging)

        //start the thread
        temporaryServerThread.start();

        //Create a helper that can temporarily capture everything written to System.out.
        //We need this because the receiveMessage() method does not return the received bytes. It converts them to text and prints them. Therefore, the test checks the printed output.
        CapturedOutput captured = new CapturedOutput();

        TcpClient client = new TcpClient();

        //Begin the main testing operations
        try {
            captured.start(); //Redirects system.out away from the terminal and into an in-memory buffer
            client.running = true;
            client.connect();

            //wait until the actual tcp class sets connected=true.
            waitUntil(
                    () -> client.connected, //whenever you check me, return the current value of client.connected
                    "TcpClient did not connect."); //If the condition doesn't become true within 5 seconds, test fails with this message.

            //send message with the method from thr TCP class
            client.sendMessage(BINARY_TEST_DATA);

            //The temporary server thread stores received data in the AtomicReference
            waitUntil(
                    () -> bytesReceivedFromClient.get() != null, //Initially null, After the frame arrives, it returns a byte[]. (Waits for that condition to change)
                    "Temporary server did not receive TcpClient data."
            );

            //Compare expected with actual
            assertBytesEqual(
                    "TcpClient outgoing bytes",
                    BINARY_TEST_DATA,
                    bytesReceivedFromClient.get()
            );

            waitUntil(
                    //clients receive thread should print this message. Test inspects the captured system.out to see.
                    () -> captured.contains("Server: message-from-temporary-server"),
                    "TcpClient did not display the incoming UTF-8 message."
            );

        } finally {
            //TCP client cleanup
            client.running = false;
            client.disconnect();

            //Wait up to 5 seconds for the temporary server thread to finish.
            temporaryServerThread.join(TIMEOUT_MS);
            //Restores the original terminal output. (After this, system.out.println() appears normally again.
            captured.stop();
        }

        //Check wether the temp server recorded an exception. If so, rethrow it on the main test thread
        throwIfPeerFailed(peerFailure);
        System.out.println("PASS: TcpClient sent the exact arbitrary byte array.");
        System.out.println("PASS: TcpClient received and displayed a framed UTF-8 message.");
    }

    private static void testTcpServer() throws Exception {
        System.out.println("=== TcpServer.java ===");

        TcpServer server = new TcpServer();
        CapturedOutput captured = new CapturedOutput();
        server.running = true;

        //Create a thread that executes server.connect. (We run it in another thread since serverSocket.accept() blocks)
        Thread realServerConnectThread = new Thread(
                server::connect, //equivalent to " () -> server.connect() "
                "real-TcpServer-connect-thread"
        );

        try {
            captured.start();
            //Start the thread
            realServerConnectThread.start();

            waitUntil(
                    //Waits until your server prints that it is opening its port.
                    //This is used as evidence that the server has started its connection process.
                    () -> captured.contains("Opening server on port " + TCP_PORT),
                    "TcpServer did not start opening its port."
            );
            /*
            NOTE: This line assumes your exact server output includes: "Opening server on port 5050"
            If your server prints different wording, this test line would need to change.

             */

            try (
                    //Create a TCP client that connects to teh actual server
                    Socket temporaryClient = new Socket("localhost", TCP_PORT);
                    //Receie franes sent by the actual TcpServer.sendMessage().
                    DataInputStream input = new DataInputStream( new BufferedInputStream(temporaryClient.getInputStream()));
                    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(temporaryClient.getOutputStream()))
            ) {
                waitUntil(
                        () -> server.connected,
                        "TcpServer did not accept the temporary client."
                );

                writeTcpFrame(
                        output,
                        "message-from-temporary-client".getBytes(StandardCharsets.UTF_8)
                );

                waitUntil(
                        () -> captured.contains("Client: message-from-temporary-client"),
                        "TcpServer did not display the incoming UTF-8 message."
                );

                server.sendMessage(BINARY_TEST_DATA);
                byte[] bytesReceivedFromServer = readTcpFrame(input);

                assertBytesEqual(
                        "TcpServer outgoing bytes",
                        BINARY_TEST_DATA,
                        bytesReceivedFromServer
                );

                writeTcpFrame(output, "exit".getBytes(StandardCharsets.UTF_8));
            }

        } finally {
            server.running = false;
            server.disconnect();
            //Wait for the server thread to finish.
            realServerConnectThread.join(TIMEOUT_MS);
            captured.stop();
        }

        System.out.println("PASS: TcpServer received and displayed a framed UTF-8 message.");
        System.out.println("PASS: TcpServer sent the exact arbitrary byte array.");
    }

    private static void testUdpClient() throws Exception {
        System.out.println("=== UdpClient.java ===");

        //Stores the UDP payload received from your real client.
        AtomicReference<byte[]> bytesReceivedFromClient = new AtomicReference<>();
        //Stores failures from the temporary UDP server thread.
        AtomicReference<Throwable> peerFailure = new AtomicReference<>();

        Thread temporaryUdpServerThread = new Thread(() -> {
            try (DatagramSocket temporaryServer = new DatagramSocket(UDP_PORT)) {
                temporaryServer.setSoTimeout(TIMEOUT_MS); //Set a 5 sec timeout since temporaryServer.receive(...) could block forever without it.

                //Create a large receive buffer
                byte[] buffer = new byte[65_507];
                //Create an emoty packet linked to that buffer.
                DatagramPacket received = new DatagramPacket(buffer, buffer.length);
                temporaryServer.receive(received);  //Wait for one UDP datagram from the client.
                /* When it returns, received will contain:
                -payload bytes
                -actual payload length
                -sender address
                -sender port
                 */

                //Extract the actual payload. The packet's buffer is much larger than the message.
                //This creates a new array containing only the valid receive bytes.
                byte[] actualPayload = Arrays.copyOfRange(
                        received.getData(),
                        received.getOffset(),
                        received.getOffset() + received.getLength()
                );

                //Stores the payload so the main test thread can compare it.
                bytesReceivedFromClient.set(actualPayload);

                //Send the UDP response to the address and port of the client that sent the packet that arrived.
                sendUdpPacket(
                        temporaryServer,
                        "message-from-temporary-udp-server".getBytes(StandardCharsets.UTF_8),
                        received.getAddress(),
                        received.getPort()
                );

                //Gives the client some time to process the first message before the second one is sent.
                Thread.sleep(150);

                //Send the "exit" message
                sendUdpPacket(
                        temporaryServer,
                        "exit".getBytes(StandardCharsets.UTF_8),
                        received.getAddress(),
                        received.getPort()
                );

            } catch (Throwable e) {
                peerFailure.set(e);
            }
        }, "temporary-server-for-UdpClient");

        temporaryUdpServerThread.start();
        UdpClient client = new UdpClient();
        CapturedOutput captured = new CapturedOutput();

        try {
            captured.start();
            client.connect();
            client.startReceiveThread();

            waitUntil(
                    () -> client.running && client.connected,
                    "UdpClient did not start."
            );

            client.sendMessage(BINARY_TEST_DATA);

            waitUntil(
                    () -> bytesReceivedFromClient.get() != null,
                    "Temporary UDP server did not receive UdpClient data."
            );

            assertBytesEqual(
                    "UdpClient outgoing bytes",
                    BINARY_TEST_DATA,
                    bytesReceivedFromClient.get()
            );

            waitUntil(
                    () -> captured.contains("Server: message-from-temporary-udp-server"),
                    "UdpClient did not display the incoming UTF-8 message."
            );

        } finally {
            client.disconnect();
            temporaryUdpServerThread.join(TIMEOUT_MS);
            captured.stop();
        }

        throwIfPeerFailed(peerFailure);
        System.out.println("PASS: UdpClient sent the exact arbitrary byte array.");
        System.out.println("PASS: UdpClient received and displayed a UTF-8 datagram.");
    }

    private static void testUdpServer() throws Exception {
        System.out.println("=== UdpServer.java ===");

        UdpServer server = new UdpServer();
        CapturedOutput captured = new CapturedOutput();

        try (DatagramSocket temporaryClient = new DatagramSocket()) {
            temporaryClient.setSoTimeout(TIMEOUT_MS);
            captured.start();

            server.connect();
            server.startReceiveThread();

            waitUntil(
                    () -> server.running && server.connected,
                    "UdpServer did not start."
            );

            sendUdpPacket(
                    temporaryClient,
                    "message-from-temporary-udp-client"
                            .getBytes(StandardCharsets.UTF_8),
                    InetAddress.getByName("localhost"),
                    UDP_PORT
            );

            waitUntil(
                    () -> captured.contains("Client: message-from-temporary-udp-client"),
                    "UdpServer did not display the incoming UTF-8 message."
            );

            server.sendMessage(BINARY_TEST_DATA);

            byte[] buffer = new byte[65_507];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            temporaryClient.receive(response);

            byte[] bytesReceivedFromServer = Arrays.copyOfRange(
                    response.getData(),
                    response.getOffset(),
                    response.getOffset() + response.getLength()
            );

            assertBytesEqual(
                    "UdpServer outgoing bytes",
                    BINARY_TEST_DATA,
                    bytesReceivedFromServer
            );

            sendUdpPacket(
                    temporaryClient,
                    "exit".getBytes(StandardCharsets.UTF_8),
                    InetAddress.getByName("localhost"),
                    UDP_PORT
            );

        } finally {
            server.disconnect();
            captured.stop();
        }

        System.out.println("PASS: UdpServer received and displayed a UTF-8 datagram.");
        System.out.println("PASS: UdpServer sent the exact arbitrary byte array.");
    }

    //Writes the TCP frame according to the length-prefix protocol.
    private static void writeTcpFrame(DataOutputStream output, byte[] data) throws Exception {
        output.writeInt(data.length);
        output.write(data);
        output.flush();
    }
    //Reads one length-prefixed frame sent by your actual TCP class.
    private static byte[] readTcpFrame(DataInputStream input) throws Exception {
        int length = input.readInt();

        if (length < 0 || length > 1_048_576) {
            throw new IllegalStateException("Invalid TCP frame length: " + length);
        }

        byte[] data = new byte[length];
        input.readFully(data);
        return data;
    }

    //Helper method to send UDP packet
    private static void sendUdpPacket(DatagramSocket socket, byte[] data, InetAddress destinationAddress, int destinationPort) throws Exception {
        DatagramPacket packet = new DatagramPacket(data, data.length, destinationAddress, destinationPort);
        socket.send(packet);
    }

    private static void assertBytesEqual(String testName, byte[] expected, byte[] actual) {
        //Compare expected to actual.
        if (!Arrays.equals(expected, actual)) {
            //Stop the test if the expected condition fails. Builds a useful failure message showing both arrays.
            throw new AssertionError(testName + " failed.\nExpected: " + Arrays.toString(expected) + "\nActual:   " + Arrays.toString(actual));
        /* Example Failure Message:
        TcpClient outgoing bytes failed.
        Expected: [0, 1, 2, 10]
        Actual:   [0, 1, 3, 10]
         */
        }
    }

    //Waits for an asynchronous condition.
    //This is needed because network operations happen on different threads.(ex: client.connect() may not immediately set connected before the next line executes)
    private static void waitUntil(BooleanSupplier condition, String failureMessage) throws Exception {
        //Calculates the time when the waiting must stop.
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        //Repeatedly evaluates the supplied condition
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() >= deadline) {
                //Fail the test if waiting time has passed
                throw new IllegalStateException(failureMessage);
            }
            Thread.sleep(20);
        }
    }

    //Checks whether a temporary background peer failed.
    private static void throwIfPeerFailed( AtomicReference<Throwable> peerFailure) {
        Throwable failure = peerFailure.get();
        //No error was recorded if null.
        if (failure != null) {
            //Rethrows the failure on the main test thread
            throw new RuntimeException("Temporary network peer failed.", failure);
        }
    }

    private static void pauseBetweenTests() throws InterruptedException {
        Thread.sleep(250);
        System.out.println();
    }

    //Small, Nested helper class. Its purpose is to capture System.out
    private static final class CapturedOutput {
        //Saves the real terminal output stream. Before capturing, System.out points to the terminal. We need to restore it later.
        private final PrintStream originalOutput = System.out;
        //Creates an in-memory destination for captured output. (Unlike a fixed byte[], it grows automatically as more text is written)
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        //Creates a PrintStream that writes into the memory buffer.
        private final PrintStream captureStream = new PrintStream(buffer, true, StandardCharsets.UTF_8); //Arguments: destination, enable automatic flushing, use UTF-8 for printed text.

        void start() {
            System.setOut(captureStream); //Changes the System.out to write to memory.
            //Before: System.out → terminal
            //After: System.out → captureStream → memory buffer
        }

        void stop() {
            System.setOut(originalOutput); //Restores normal terminal output.
            captureStream.flush(); //Forces any remaining captured text into the buffer.
        }

        boolean contains(String expectedText) {
            captureStream.flush(); //ensures the output has reached the buffer

            //Locks the buffer while reading it. Synchronized helps prevent the two operations from interfering.
            //Your networking receive thread may be writing output while the test thread is reading it.
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8) //decodes into string
                        .contains(expectedText); //checks whether the required message appears anywhere inside it.
            }
        }
    }
}

/*
TcpClient test flow:
Temporary server opens port 5050
        ↓
Real TcpClient connects
        ↓
Real TcpClient.sendMessage(binary bytes)
        ↓
Temporary server reads frame
        ↓
Test compares exact bytes
        ↓
Temporary server sends framed UTF-8 text
        ↓
Real TcpClient.receiveMessage() processes it
        ↓
Real TcpClient prints the text
        ↓
Test checks captured output


TcpServer test flow:
Real TcpServer starts on port 5050
        ↓
Temporary client connects
        ↓
Temporary client sends framed UTF-8 text
        ↓
Real TcpServer prints it
        ↓
Test checks captured output
        ↓
Real TcpServer.sendMessage(binary bytes)
        ↓
Temporary client reads frame
        ↓
Test compares exact bytes


UdpClient test flow:
Temporary UDP server binds port 5000
        ↓
Real UdpClient starts
        ↓
Real UdpClient sends binary datagram
        ↓
Temporary server receives payload
        ↓
Test compares exact bytes
        ↓
Temporary server sends UTF-8 datagram back
        ↓
Real UdpClient prints it
        ↓
Test checks captured output


UdpServer test flow:
Real UdpServer binds port 5000
        ↓
Temporary UDP client sends UTF-8 datagram
        ↓
Real server learns client address and port
        ↓
Real server prints incoming message
        ↓
Test checks captured output
        ↓
Real UdpServer sends binary datagram
        ↓
Temporary client receives it
        ↓
Test compares exact bytes
 */