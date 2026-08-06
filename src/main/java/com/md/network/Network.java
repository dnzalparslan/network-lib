package com.md.network;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Consumer;

public abstract class Network {

    protected volatile boolean running = false;
    protected volatile boolean connected = false;
    protected Thread receiveThread;

    // Limits the maximum accepted TCP message size to 1 MB.
    protected static final int MAX_MESSAGE_SIZE = 1_048_576;

    /* Stores the action that should happen whenever a message is received.
     * Consumer<byte[]> means:
     * - it receives a byte[]
     * - it performs an action
     * - it does not return a value */
    private volatile Consumer<byte[]> messageHandler =
            data -> System.out.println(bytesToText(data));
    /*Similar to:
    private void defaultHandleMessage(byte[] data) {
    System.out.println(bytesToText(data)); */

    public abstract void connect();

    public abstract void sendMessage(byte[] data);

    public abstract void receiveMessage();

    public abstract void disconnect();

    // Stops the complete networking component.
    // This is useful for applications that use the library without using Network.startConsoleInput().
    public void shutdown() {
        running = false;
        disconnect();
    }

    // Local console closing message.
    protected abstract String getClosedMessage();

    //Allows an application to define what should happen when bytes arrive.
    public void setMessageHandler(Consumer<byte[]> messageHandler) {
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler cannot be null");
    }

    //Sends received bytes to the currently registered handler.
    protected void deliverMessage(byte[] data) {
        Objects.requireNonNull(data, "received data cannot be null");
        //A copy is passed so that the handler does not accidentally modify the original receive buffer owned by the networking class.
        byte[] safeCopy = Arrays.copyOf(data, data.length);
        messageHandler.accept(safeCopy);
    }

    public void startReceiveThread() {
        receiveThread = new Thread(
                this::receiveMessage,
                getClass().getSimpleName() + "-receive"
        );

        //A daemon receive thread does not prevent the JVM from terminating after all normal application threads have finished.
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void startConsoleInput() {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            String message = scanner.nextLine();
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

    //Helper method used by the default message handler. This conversion is suitable for console text programs.
    // Binary applications such as Circle Rotation will replace the default handler and will not convert the data to text.
    protected String bytesToText(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        return connected;
    }
}