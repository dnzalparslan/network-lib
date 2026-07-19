import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

public class TcpClient extends Network {

    private Socket socket;
    private DataInputStream input;  //DataInputStream can read both primitive values (int) and exact byte sequences unlike BufferedReader
    private DataOutputStream output; //DataOutputStream can write both unlike PrintWriter.

    private final String serverAddress = "localhost";
    private final int port = 5050;

    public static void main(String[] args) {
        TcpClient client = new TcpClient();
        client.running = true;
        client.startConnectThread();
        client.startConsoleInput();
    }

    public void startConnectThread() {
        Thread connectThread = new Thread(() -> {
            while (running) {
                if (!connected) {
                    connect();
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    System.out.println("Connect thread interrupted.");
                    Thread.currentThread().interrupt(); //Restores the thread's interrupted status.
                    break;
                }
            }
        });

        connectThread.start();
    }

    @Override
    public void connect() {
        try {
            System.out.println("Trying to connect to server...");
            socket = new Socket(serverAddress, port);

            /* socket.getInputStream() gives us the raw incoming byte stream.
             * BufferedInputStream improves efficiency by reading data into an internal buffer.
             * DataInputStream adds methods such as readInt() and readFully().
             */
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            /* socket.getOutputStream() gives us the raw outgoing byte stream.
             * BufferedOutputStream improves efficiency.
             * DataOutputStream adds methods such as writeInt() and write().
             */
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()) );

            connected = true;
            System.out.println("Connected to server.");
            startReceiveThread();

        } catch (IOException e) {
            System.out.println("Server not available. Will try again...");
            connected = false;
            closeConnectionResources();
        }
    }

    @Override
    public void receiveMessage() {
        try {
            while (connected) {
                int messageLength = input.readInt(); //Step 1: Read the 4-byte integer that tells us how many message bytes follow.
                validateMessageLength(messageLength); //Step 2: Validate the length before creating an array.
                byte[] messageData = new byte[messageLength]; //Step 3: Create an array exactly large enough for this message.
                input.readFully(messageData); //Step 4: Read exactly messageLength bytes.
                //A normal read() call is not guaranteed to fill the array. readFully() keeps reading until the array is full or the connection ends.

                String message = bytesToText(messageData); //Interpret the delivered bytes as UTF-8.

                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("Server disconnected.");
                    running = false;
                    disconnect();
                    break;
                }

                System.out.println("Server: " + message);
            }

        } catch (EOFException e) {
            //EOFException usually means the other side closed its socket while we were waiting for the next frame.
            if (connected) {
                System.out.println("Server closed the connection.");
            }
            disconnect();

        } catch (IOException e) {
            if (connected) {
                System.out.println(
                        "Connection lost: " + e.getMessage()
                );
            }
            disconnect();
        }
    }

    //synchronized means only one thread can execute this method on the same object at a time.
    @Override
    public synchronized void sendMessage(byte[] data) {
        //Reject null because it is not a valid byte array message.
        if (data == null) {
            throw new IllegalArgumentException( "Message data cannot be null.");
        }

        if (!connected || output == null) {
            System.out.println("Not connected. Message not sent.");
            return;
        }

        try {
            validateMessageLength(data.length);
            output.writeInt(data.length); //First write the 4-byte message length.
            output.write(data); //Then write the actual message bytes.
            output.flush(); //BufferedOutputStream may temporarily keep data in memory. flush() forces the complete frame toward the socket now.

        } catch (IOException e) {
            System.out.println(
                    "Could not send message: " + e.getMessage()
            );
            disconnect();
        }
    }

    private void validateMessageLength(int messageLength) throws IOException {

        if (messageLength < 0) {
            throw new IOException( "Received a negative message length: "  + messageLength );
        }

        if (messageLength > MAX_MESSAGE_SIZE) {
            throw new IOException( "Message is too large: "  + messageLength  + " bytes. Maximum allowed size is "  + MAX_MESSAGE_SIZE  + " bytes." );
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        closeConnectionResources();
    }

    /*
     * Closing the socket also closes the streams connected to it. Therefore, closing the socket is sufficient
     * and avoids repeatedly closing several wrappers around the same underlying resource.
     */
    private void closeConnectionResources() {

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println( "Error while disconnecting: " + e.getMessage() );
        } finally {
            socket = null;
            input = null;
            output = null;
        }
    }

    @Override
    protected String getClosedMessage() {
        return "Client closed.";
    }
}