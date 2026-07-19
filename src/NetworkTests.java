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

        testTcpClient();
        pauseBetweenTests();
        testTcpServer();
        pauseBetweenTests();
        testUdpClient();
        pauseBetweenTests();
        testUdpServer();

        System.out.println();
        System.out.println("All four existing classes passed their tests.");
    }

    private static void testTcpClient() throws Exception {
        System.out.println("=== TcpClient.java ===");

        AtomicReference<byte[]> bytesReceivedFromClient = new AtomicReference<>();
        AtomicReference<Throwable> peerFailure = new AtomicReference<>();

        Thread temporaryServerThread = new Thread(() -> {
            try (
                    ServerSocket serverSocket = new ServerSocket(TCP_PORT);
                    Socket socket = serverSocket.accept();
                    DataInputStream input = new DataInputStream(
                            new BufferedInputStream(socket.getInputStream())
                    );
                    DataOutputStream output = new DataOutputStream(
                            new BufferedOutputStream(socket.getOutputStream())
                    )
            ) {
                bytesReceivedFromClient.set(readTcpFrame(input));

                writeTcpFrame(
                        output,
                        "message-from-temporary-server"
                                .getBytes(StandardCharsets.UTF_8)
                );

                Thread.sleep(150);
                writeTcpFrame(output, "exit".getBytes(StandardCharsets.UTF_8));

            } catch (Throwable e) {
                peerFailure.set(e);
            }
        }, "temporary-server-for-TcpClient");

        temporaryServerThread.start();

        CapturedOutput captured = new CapturedOutput();
        TcpClient client = new TcpClient();

        try {
            captured.start();
            client.running = true;
            client.connect();

            waitUntil(() -> client.connected, "TcpClient did not connect.");

            client.sendMessage(BINARY_TEST_DATA);

            waitUntil(
                    () -> bytesReceivedFromClient.get() != null,
                    "Temporary server did not receive TcpClient data."
            );

            assertBytesEqual(
                    "TcpClient outgoing bytes",
                    BINARY_TEST_DATA,
                    bytesReceivedFromClient.get()
            );

            waitUntil(
                    () -> captured.contains("Server: message-from-temporary-server"),
                    "TcpClient did not display the incoming UTF-8 message."
            );

        } finally {
            client.running = false;
            client.disconnect();
            temporaryServerThread.join(TIMEOUT_MS);
            captured.stop();
        }

        throwIfPeerFailed(peerFailure);

        System.out.println("PASS: TcpClient sent the exact arbitrary byte array.");
        System.out.println("PASS: TcpClient received and displayed a framed UTF-8 message.");
    }

    private static void testTcpServer() throws Exception {
        System.out.println("=== TcpServer.java ===");

        TcpServer server = new TcpServer();
        CapturedOutput captured = new CapturedOutput();
        server.running = true;

        Thread realServerConnectThread = new Thread(
                server::connect,
                "real-TcpServer-connect-thread"
        );

        try {
            captured.start();
            realServerConnectThread.start();

            waitUntil(
                    () -> captured.contains("Opening server on port " + TCP_PORT),
                    "TcpServer did not start opening its port."
            );

            try (
                    Socket temporaryClient = new Socket("localhost", TCP_PORT);
                    DataInputStream input = new DataInputStream(
                            new BufferedInputStream(temporaryClient.getInputStream())
                    );
                    DataOutputStream output = new DataOutputStream(
                            new BufferedOutputStream(temporaryClient.getOutputStream())
                    )
            ) {
                waitUntil(
                        () -> server.connected,
                        "TcpServer did not accept the temporary client."
                );

                writeTcpFrame(
                        output,
                        "message-from-temporary-client"
                                .getBytes(StandardCharsets.UTF_8)
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
            realServerConnectThread.join(TIMEOUT_MS);
            captured.stop();
        }

        System.out.println("PASS: TcpServer received and displayed a framed UTF-8 message.");
        System.out.println("PASS: TcpServer sent the exact arbitrary byte array.");
    }

    private static void testUdpClient() throws Exception {
        System.out.println("=== UdpClient.java ===");

        AtomicReference<byte[]> bytesReceivedFromClient = new AtomicReference<>();
        AtomicReference<Throwable> peerFailure = new AtomicReference<>();

        Thread temporaryUdpServerThread = new Thread(() -> {
            try (DatagramSocket temporaryServer = new DatagramSocket(UDP_PORT)) {
                temporaryServer.setSoTimeout(TIMEOUT_MS);

                byte[] buffer = new byte[65_507];
                DatagramPacket received = new DatagramPacket(buffer, buffer.length);
                temporaryServer.receive(received);

                byte[] actualPayload = Arrays.copyOfRange(
                        received.getData(),
                        received.getOffset(),
                        received.getOffset() + received.getLength()
                );

                bytesReceivedFromClient.set(actualPayload);

                sendUdpPacket(
                        temporaryServer,
                        "message-from-temporary-udp-server"
                                .getBytes(StandardCharsets.UTF_8),
                        received.getAddress(),
                        received.getPort()
                );

                Thread.sleep(150);

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

    private static void writeTcpFrame(DataOutputStream output, byte[] data)
            throws Exception {
        output.writeInt(data.length);
        output.write(data);
        output.flush();
    }

    private static byte[] readTcpFrame(DataInputStream input)
            throws Exception {
        int length = input.readInt();

        if (length < 0 || length > 1_048_576) {
            throw new IllegalStateException("Invalid TCP frame length: " + length);
        }

        byte[] data = new byte[length];
        input.readFully(data);
        return data;
    }

    private static void sendUdpPacket(
            DatagramSocket socket,
            byte[] data,
            InetAddress destinationAddress,
            int destinationPort
    ) throws Exception {
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                destinationAddress,
                destinationPort
        );
        socket.send(packet);
    }

    private static void assertBytesEqual(
            String testName,
            byte[] expected,
            byte[] actual
    ) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(
                    testName
                            + " failed.\nExpected: "
                            + Arrays.toString(expected)
                            + "\nActual:   "
                            + Arrays.toString(actual)
            );
        }
    }

    private static void waitUntil(
            BooleanSupplier condition,
            String failureMessage
    ) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException(failureMessage);
            }
            Thread.sleep(20);
        }
    }

    private static void throwIfPeerFailed(
            AtomicReference<Throwable> peerFailure
    ) {
        Throwable failure = peerFailure.get();
        if (failure != null) {
            throw new RuntimeException("Temporary network peer failed.", failure);
        }
    }

    private static void pauseBetweenTests() throws InterruptedException {
        Thread.sleep(250);
        System.out.println();
    }

    private static final class CapturedOutput {
        private final PrintStream originalOutput = System.out;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final PrintStream captureStream =
                new PrintStream(buffer, true, StandardCharsets.UTF_8);

        void start() {
            System.setOut(captureStream);
        }

        void stop() {
            System.setOut(originalOutput);
            captureStream.flush();
        }

        boolean contains(String expectedText) {
            captureStream.flush();
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8)
                        .contains(expectedText);
            }
        }
    }
}
