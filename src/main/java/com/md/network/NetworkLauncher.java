package com.md.network;

import com.md.network.tcp.TcpClient;
import com.md.network.tcp.TcpServer;
import com.md.network.udp.UdpClient;
import com.md.network.udp.UdpServer;

import java.util.Scanner;

/**
 * Entry point for the executable network-lib JAR.
 *
 * It lets the user choose which demonstration program to run.
 */
public final class NetworkLauncher {

    private NetworkLauncher() {
        // Prevent creation of unnecessary NetworkLauncher objects.
    }

    public static void main(String[] args) {
        printMenu();

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Select an option: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    TcpServer.main(new String[0]);
                    break;

                case "2":
                    TcpClient.main(new String[0]);
                    break;

                case "3":
                    UdpServer.main(new String[0]);
                    break;

                case "4":
                    UdpClient.main(new String[0]);
                    break;

                default:
                    System.out.println(
                            "Invalid option. Choose 1, 2, 3, or 4."
                    );
            }
        }
    }

    private static void printMenu() {
        System.out.println("Network Library Launcher");
        System.out.println("------------------------");
        System.out.println("1. Start TCP Server");
        System.out.println("2. Start TCP Client");
        System.out.println("3. Start UDP Server");
        System.out.println("4. Start UDP Client");
    }
}
