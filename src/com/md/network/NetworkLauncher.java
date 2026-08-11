package com.md.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.md.network.tcp.TcpClient;
import com.md.network.tcp.TcpServer;
import com.md.network.udp.UdpClient;
import com.md.network.udp.UdpServer;

import java.io.File;
import java.io.IOException;

public class NetworkLauncher {

    private static final String CONFIG_PATH = "cfg/network.json";

    public static void main(String[] args) {

        try {
            NetworkConfig config = loadConfig();

            validateConfig(config);

            if (config.getProtocol().equalsIgnoreCase("tcp")) {

                if (config.getType().equalsIgnoreCase("server")) {
                    startTcpServer(config);

                } else if (config.getType().equalsIgnoreCase("client")) {
                    startTcpClient(config);

                } else {
                    throw new IllegalArgumentException(
                            "Network type must be either 'server' or 'client'."
                    );
                }

            } else if (config.getProtocol().equalsIgnoreCase("udp")) {

                if (config.getType().equalsIgnoreCase("server")) {
                    startUdpServer(config);

                } else if (config.getType().equalsIgnoreCase("client")) {
                    startUdpClient(config);

                } else {
                    throw new IllegalArgumentException(
                            "Network type must be either 'server' or 'client'."
                    );
                }

            } else {
                throw new IllegalArgumentException(
                        "Protocol must be either 'tcp' or 'udp'."
                );
            }

        } catch (IOException | IllegalArgumentException e) {
            System.err.println(
                    "Failed to start network: " + e.getMessage()
            );
        }
    }

    private static NetworkConfig loadConfig() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        File configFile = new File(CONFIG_PATH);

        return objectMapper.readValue(
                configFile,
                NetworkConfig.class
        );
    }

    private static void validateConfig(NetworkConfig config) {

        if (config.getProtocol() == null
                || config.getProtocol().isBlank()) {

            throw new IllegalArgumentException(
                    "Protocol cannot be blank."
            );
        }

        if (config.getType() == null
                || config.getType().isBlank()) {

            throw new IllegalArgumentException(
                    "Network type cannot be blank."
            );
        }

        if (config.getPort() < 1
                || config.getPort() > 65_535) {

            throw new IllegalArgumentException(
                    "Port must be between 1 and 65535."
            );
        }

        if (config.getType().equalsIgnoreCase("client")
                && (config.getIp() == null
                || config.getIp().isBlank())) {

            throw new IllegalArgumentException(
                    "IP address cannot be blank for a client."
            );
        }
    }

    private static void startTcpServer(NetworkConfig config) {

        TcpServer server =
                new TcpServer(config.getPort());

        server.setMessageHandler(data ->
                System.out.println(
                        "Client: " + new String(data)
                )
        );

        server.start();

        server.startConsoleInput();
    }

    private static void startTcpClient(NetworkConfig config) {

        TcpClient client =
                new TcpClient(
                        config.getIp(),
                        config.getPort()
                );

        client.setMessageHandler(data ->
                System.out.println(
                        "Server: " + new String(data)
                )
        );

        client.start();

        client.startConsoleInput();
    }

    private static void startUdpServer(NetworkConfig config) {

        UdpServer server =
                new UdpServer(config.getPort());

        server.setMessageHandler(data ->
                System.out.println(
                        "Received: " + new String(data)
                )
        );

        server.start();

        server.startConsoleInput();
    }

    private static void startUdpClient(NetworkConfig config) {

        UdpClient client =
                new UdpClient(
                        config.getIp(),
                        config.getPort()
                );

        client.setMessageHandler(data ->
                System.out.println(
                        "Received: " + new String(data)
                )
        );

        client.start();

        client.startConsoleInput();
    }
}