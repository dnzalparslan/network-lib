package com.md.network.tcp;
import com.md.network.Network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer extends Network {

    private static final int DEFAULT_PORT = 5050;
    private final int port;

    private ServerSocket serverSocket;
    private Socket clientSocket;

    private DataInputStream input;
    private DataOutputStream output;

    //Preserves the original port 5050 behavior.
    public TcpServer() {
        this(DEFAULT_PORT);
    }

    //Allows applications to choose a server port.
    public TcpServer(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(
                    "Port must be between 1 and 65535."
            );
        }

        this.port = port;
    }
    public static void main(String[] args) {
        TcpServer server = new TcpServer();
        server.setMessageHandler(server::handleConsoleMessage);
        server.start();
        server.startConsoleInput();
    }

    //Starts TCP networking without starting console input.
    public void start() {
        System.out.println("TCP Server started.");
        running = true;
        startConnectThread();
    }

    public void startConnectThread() {
        //
        Thread connectThread = new Thread(this::connect);
        connectThread.start();
    }

    @Override
    public void connect() {
        try {
            System.out.println("Opening server on port " + port);
            serverSocket = new ServerSocket(port);

            while (running) {
                if (!connected) {
                    System.out.println("Waiting for client...");
                    clientSocket = serverSocket.accept(); //accept() blocks until a client connects.
                    System.out.println("Client connected!");

                    input = new DataInputStream( new BufferedInputStream(clientSocket.getInputStream()));

                    output = new DataOutputStream( new BufferedOutputStream(clientSocket.getOutputStream()));

                    connected = true;

                    startReceiveThread();
                }

                Thread.sleep(1000);
            }

        } catch (IOException e) {
            if (running) {
                System.out.println("Connection error: " + e.getMessage());
            }

        } catch (InterruptedException e) {
            System.out.println("Connect thread interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void receiveMessage() {
        try {
            while (connected) {
                // Read the length prefix belonging to one complete TCP message.
                int messageLength = input.readInt();
                //Validate the announced size before allocating memory.
                validateMessageLength(messageLength);
                //Allocate the exact payload size.
                byte[] messageData = new byte[messageLength];
                //Read the complete payload.
                input.readFully(messageData);
                //Pass the raw bytes to the application-defined handler.
                deliverMessage(messageData);
            }

        } catch (EOFException e) {
            if (connected) {
                System.out.println("Client closed the connection.");
            }

            disconnect();

        } catch (IOException e) {
            if (connected) {
                System.out.println(
                        "Receive error: " + e.getMessage()
                );
            }

            disconnect();
        }
    }

    //Console behavior for the standalone TcpServer program.
    // A different application can register a different handler without changing the TCP transport code.
    private void handleConsoleMessage(byte[] data) {
        String message = bytesToText(data);

        if (message.equalsIgnoreCase("exit")) {
            System.out.println("Client disconnected.");

            // Do not set running to false. running remains true so the ServerSocket stays open and another client can connect later.
            disconnect();
            return;
        }

        System.out.println("Client: " + message);
    }

    //synchronized means only one thread can execute this method on the same object at a time.
    @Override
    public synchronized void sendMessage(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Message data cannot be null.");
        }

        if (!connected || output == null) {
            System.out.println("No client connected. Message not sent.");
            return;
        }

        try {
            validateMessageLength(data.length);

            //Frame format: [4-byte length][message bytes]
            output.writeInt(data.length);
            output.write(data);
            output.flush();

        } catch (IOException e) {
            System.out.println(
                    "Could not send message: " + e.getMessage()
            );
            disconnect();
        }
    }

    private void validateMessageLength(int messageLength) throws IOException {

        if (messageLength < 0) {
            throw new IOException( "Received a negative message length: " + messageLength);
        }

        if (messageLength > MAX_MESSAGE_SIZE) {
            throw new IOException( "Message is too large: " + messageLength + " bytes. Maximum allowed size is " + MAX_MESSAGE_SIZE + " bytes." );
        }
    }

    @Override
    public void disconnect() {
        System.out.println("Disconnecting...");
        connected = false;
        //If only one client disconnected and running is still true,the ServerSocket stays open so another client can connect.
        closeClientConnectionResources();

        //If the whole server is stopping, also close the ServerSocket.
        if (!running) {
            closeServerSocket();
        }
        System.out.println("Disconnected.");
    }

    private void closeClientConnectionResources() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.out.println( "Error while closing client connection: " + e.getMessage() );
        } finally {
            clientSocket = null;
            input = null;
            output = null;
        }
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println( "Error while closing server socket: "  + e.getMessage());
        } finally {
            serverSocket = null;
        }
    }

    @Override
    protected String getClosedMessage() {
        return "Server closed.";
    }
}