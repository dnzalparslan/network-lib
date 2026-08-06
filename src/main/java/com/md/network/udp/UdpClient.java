package com.md.network.udp;
import com.md.network.Network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpClient extends Network {

    private static final int DEFAULT_SERVER_PORT = 5000;
    private static final String DEFAULT_SERVER_ADDRESS = "localhost";

    private final String serverHost;
    private final int serverPort;

    /*
     * The theoretical maximum UDP payload for IPv4 is 65,507 bytes.
     *
     * 65,535 bytes maximum IP packet size
     * - 20 bytes IP header
     * - 8 bytes UDP header
     * = 65,507 bytes of UDP data
     */
    private static final int MAX_UDP_PAYLOAD_SIZE = 65_507;
    private DatagramSocket socket;
    private InetAddress serverAddress; //Stores the resolved IP address of the UDP server

    //Preserves the original localhost:5000 behavior.
    public UdpClient() {
        this(DEFAULT_SERVER_ADDRESS, DEFAULT_SERVER_PORT);
    }

    // Allows another application to select the destination.
    public UdpClient(String serverHost, int serverPort) {
        if (serverHost == null || serverHost.isBlank()) {
            throw new IllegalArgumentException( "Server address cannot be blank.");
        }

        if (serverPort < 1 || serverPort > 65_535) {
            throw new IllegalArgumentException( "Port must be between 1 and 65535.");
        }

        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public static void main(String[] args) {
        UdpClient client = new UdpClient();
        client.setMessageHandler(client::handleConsoleMessage);
        client.start();

        if (client.running) {
            client.startConsoleInput();
        }
    }

    //Starts the UDP client without starting console input.
    public void start() {
        connect();

        if (running) {
            startReceiveThread();
        }
    }

    @Override
    public void connect() {
        try {
            //Creates a UDP socket using an automatically selected local port.
            socket = new DatagramSocket(); //Unlike the server, the client does not need to request a particular port number.
            serverAddress = InetAddress.getByName(serverHost); //Converts the hostname "localhost" into an InetAddress.
            running = true;
            connected = true;
            System.out.println("UDP Client started.");
            System.out.println( "Sending to server at " + serverHost + ":" + serverPort);

        } catch (IOException e) {
            System.out.println( "Could not start UDP client: " + e.getMessage() );
            running = false;
            connected = false;
            closeSocket();
        }
    }

    @Override
    public void receiveMessage() {
        while (running) {
            try {
                //Create storage for one UDP datagram.
                byte[] buffer = new byte[MAX_UDP_PAYLOAD_SIZE];
                // The DatagramPacket initially points to an empty receive buffer.
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                //Block until one complete UDP datagram arrives.
                socket.receive(packet);
                // Copy only the actual packet bytes. The receive buffer may contain 65,507 positions even when the received datagram contains only a few bytes.
                byte[] messageData = new byte[packet.getLength()];

                System.arraycopy(packet.getData(), packet.getOffset(), messageData, 0, packet.getLength());

                //UDP already preserves one-datagram-per-message boundaries. Deliver this complete datagram to the registered handler.
                deliverMessage(messageData);

            } catch (IOException e) {
                // socket.close() is used to stop a thread blocked in receive(). That normally causes receive() to throw an exception.
                // Only print an error when the client was supposed to continue running.
                if (running) {
                    System.out.println(
                            "Receive error: " + e.getMessage()
                    );
                }
            }
        }
    }

    //Console-specific handling for the standalone UDP client.
    private void handleConsoleMessage(byte[] data) {
        String message = bytesToText(data);

        if (message.equalsIgnoreCase("exit")) {
            System.out.println("Server left.");
            disconnect();
            return;
        }

        System.out.println("Server: " + message);
    }

    @Override
    public synchronized void sendMessage(byte[] data) {
       //null means that no byte-array object was supplied.
        if (data == null) {
            throw new IllegalArgumentException( "Message data cannot be null." );
        }

        //Make sure the socket was successfully created and is still open.
        if (!running || !connected || socket == null || socket.isClosed()) {
            System.out.println( "UDP client is not running. Message not sent.");
            return;
        }

        //UDP has a smaller maximum individual message size than the 1 MiB general limit in Network.java.
        validateUdpPayloadSize(data.length);

        try {
            /* A DatagramPacket contains:
             * - the byte array to send
             * - the number of bytes to send
             * - the destination IP address
             * - the destination port */
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);

            socket.send(packet); //Sends the entire byte array as one UDP datagram.

        } catch (IOException e) {
            System.out.println( "Send error: " + e.getMessage() );
        }
    }

    private void validateUdpPayloadSize(int dataLength) {
        if (dataLength > MAX_UDP_PAYLOAD_SIZE) {
            throw new IllegalArgumentException( "UDP message is too large: "  + dataLength  + " bytes. Maximum allowed size is "  + MAX_UDP_PAYLOAD_SIZE  + " bytes.");
        }
    }

    @Override
    public void disconnect() {
        //These flags cause the receiving loop and console loop to stop.
        running = false;
        connected = false;
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
        serverAddress = null;
    }

    @Override
    protected String getClosedMessage() {
        return "UDP Client closed.";
    }
}