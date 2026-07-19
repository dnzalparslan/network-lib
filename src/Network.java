import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public abstract class Network {

    protected volatile boolean running = false;
    protected volatile boolean connected = false;
    protected Thread receiveThread;
    protected static final int MAX_MESSAGE_SIZE = 1_048_576;     //This limits the maximum accepted message size to 1 MB.

    public abstract void connect();

    public abstract void sendMessage(byte[] data);

    public abstract void receiveMessage();

    public abstract void disconnect();

    //Local console message
    protected abstract String getClosedMessage();

    public void startReceiveThread() {
        receiveThread = new Thread(this::receiveMessage);
        receiveThread.start();
    }

    public void startConsoleInput() {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            String message = scanner.nextLine();

            // Convert the text into UTF-8 bytes at the boundary. (Since the console works with text, but the network works with bytes)
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

            if (message.equalsIgnoreCase("exit")) {
                sendMessage(messageBytes);
                running = false;
                disconnect();
                System.out.println(getClosedMessage());
                break;
            }

            sendMessage(messageBytes);
        }

        scanner.close();
    }


    //Helper method to convert received UTF-8 bytes back into text.
    protected String bytesToText(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}