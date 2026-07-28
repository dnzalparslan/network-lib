package com.md.network.udp;
import com.md.network.Network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UdpServer extends Network {

    private static final int PORT = 5000;
    //The theoretical maximum UDP payload for IPv4.
    private static final int MAX_UDP_PAYLOAD_SIZE = 65_507;
    private DatagramSocket socket;
    private InetAddress clientAddress;
    private int clientPort;

    public static void main(String[] args) {
        UdpServer server = new UdpServer();
        //Register standalone console behavior before starting reception.
        server.setMessageHandler(server::handleConsoleMessage);
        server.connect();

        if (server.running) {
            server.startReceiveThread();
            server.startConsoleInput();
        }
    }

    @Override
    public void connect() {
        try {
            //Binds the DatagramSocket to port 5000 so that clients can send packets to server-address:5000
            socket = new DatagramSocket(PORT);
            running = true;
            connected = true;
            System.out.println( "UDP Server started on port " + PORT);
            System.out.println("Waiting for messages...");

        } catch (SocketException e) {
            System.out.println( "Could not open UDP port: " + e.getMessage() );

            running = false;
            connected = false;
            closeSocket();
        }
    }

    @Override
    public void receiveMessage() {
        while (running) {
            try {
                //Create fresh storage for one UDP datagram.
                byte[] buffer = new byte[MAX_UDP_PAYLOAD_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                //Block until one complete datagram arrives.
                socket.receive(packet);
                //Copy only the actual datagram bytes.
                byte[] messageData = new byte[packet.getLength()];

                System.arraycopy(
                        packet.getData(),
                        packet.getOffset(),
                        messageData,
                        0,
                        packet.getLength()
                );

                //Remember the sender before delivering the message.
                // A registered handler may immediately create and send a response. sendMessage() needs this address and port.
                clientAddress = packet.getAddress();
                clientPort = packet.getPort();

                //Pass the raw datagram bytes to the registered application handler.
                deliverMessage(messageData);

            } catch (IOException e) {
                if (running) {
                    System.out.println(
                            "Receive error: " + e.getMessage()
                    );
                }
            }
        }
    }

    //Console-specific handling for the standalone UDP server.
    private void handleConsoleMessage(byte[] data) {
        String message = bytesToText(data);

        if (message.equalsIgnoreCase("exit")) {
            System.out.println("Client left.");

            //UDP has no permanent connection to close. Forget the latest client's destination information so the server cannot accidentally send future console messages to a client that  has announced it is leaving.
            clientAddress = null;
            clientPort = 0;
            return;
        }

        System.out.println("Client: " + message);
    }

    @Override
    public synchronized void sendMessage(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException( "Message data cannot be null." );
        }

        if (!running  || !connected  || socket == null  || socket.isClosed()) {
            System.out.println( "UDP server is not running. Message not sent." );
            return;
        }

        //The server does not know where to send a packet until it has received at least one packet from a client.
        if (clientAddress == null || clientPort == 0) {
            System.out.println( "No client known yet. "  + "Wait until a client sends the first message." );
            return;
        }
        validateUdpPayloadSize(data.length);

        try {
            //The destination is the address and port remembered from the latest received client packet.
            DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress, clientPort );
            //Sends the byte array as one complete UDP datagram.
            socket.send(packet);

        } catch (IOException e) {
            System.out.println( "Send error: " + e.getMessage() );
        }
    }

    private void validateUdpPayloadSize(int dataLength) {
        if (dataLength > MAX_UDP_PAYLOAD_SIZE) {
            throw new IllegalArgumentException( "UDP message is too large: " + dataLength + " bytes. Maximum allowed size is " + MAX_UDP_PAYLOAD_SIZE  + " bytes." );
        }
    }

    @Override
    public void disconnect() {
        running = false;
        connected = false;
        closeSocket();

        clientAddress = null;
        clientPort = 0;
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }

    @Override
    protected String getClosedMessage() {
        return "UDP Server closed.";
    }
}