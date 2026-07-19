import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpClient extends Network {

    private static final int SERVER_PORT = 5000;
    private static final String SERVER_ADDRESS = "localhost";

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

    public static void main(String[] args) {
        UdpClient client = new UdpClient();
        client.connect();

        //Only start the remaining operations if connect() successfully created the UDP socket.
        if (client.running) {
            client.startReceiveThread();
            client.startConsoleInput();
        }
    }

    @Override
    public void connect() {
        try {
            //Creates a UDP socket using an automatically selected local port.
            socket = new DatagramSocket(); //Unlike the server, the client does not need to request a particular port number.
            serverAddress = InetAddress.getByName(SERVER_ADDRESS); //Converts the hostname "localhost" into an InetAddress.
            running = true;
            connected = true;
            System.out.println("UDP Client started.");
            System.out.println( "Sending to server at "  + SERVER_ADDRESS  + ":" + SERVER_PORT);

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
                byte[] buffer = new byte[MAX_UDP_PAYLOAD_SIZE];

                /* The packet initially points to an empty byte array.
                 * When socket.receive(packet) succeeds, Java fills this packet with:
                 * - the received bytes
                 * - the sender's address
                 * - the sender's port
                 * - the actual number of received bytes
                 */
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                /* packet.getLength() tells us exactly how many bytes were received.
                 * The buffer may have a capacity of 65,507 bytes, but the actual message may be only 5 bytes long.
                 * We copy only the real message bytes into a correctly sized byte array.
                 */
                byte[] messageData = new byte[packet.getLength()];
                System.arraycopy( packet.getData(),  packet.getOffset(),  messageData, 0, packet.getLength());

                /* The UDP transport has now delivered byte[] data. This console application
                chooses to interpret those bytes as UTF-8 text so they can be displayed to the user. */
                String message = bytesToText(messageData);
                System.out.println("Server: " + message);

                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("Server left.");
                    disconnect();
                    break;
                }

            } catch (IOException e) {
                if (running) {
                    System.out.println( "Receive error: " + e.getMessage() );
                }
            }
        }
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
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
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