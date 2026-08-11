# Network Library

A reusable Java networking library that supports TCP and UDP communication through configurable client and server implementations.

The project uses Apache Ant for building and running the application. Network behavior is configured through a JSON file rather than being hardcoded directly into the Java classes.

## Overview

Originally, the TCP and UDP implementations contained default IP addresses and port numbers inside the Java source code. The client and server classes also had their own entry points and decided how they should be initialized.

The project has been updated so that network configuration is externalized into a JSON configuration file.

The application now reads:

* the network protocol (`tcp` or `udp`)
* the network type (`client` or `server`)
* the IP address
* the port number

from `cfg/network.json`.

Based on those values, `NetworkLauncher` dynamically initializes the correct networking class.

The four possible configurations are:

```text
TCP + Server -> TcpServer
TCP + Client -> TcpClient
UDP + Server -> UdpServer
UDP + Client -> UdpClient
```

This removes hardcoded network configuration from the individual networking classes and makes the library easier to configure and reuse.

---

## Project Structure

The relevant project structure is:

```text
network-lib/
│
├── cfg/
│   └── network.json
│
├── lib/
│   ├── jackson-annotations-2.22.jar
│   ├── jackson-core-2.22.1.jar
│   └── jackson-databind-2.22.1.jar
│
├── src/
│   └── com/
│       └── md/
│           └── network/
│               ├── Network.java
│               ├── NetworkConfig.java
│               ├── NetworkLauncher.java
│               │
│               ├── tcp/
│               │   ├── TcpClient.java
│               │   └── TcpServer.java
│               │
│               └── udp/
│                   ├── UdpClient.java
│                   └── UdpServer.java
│
├── build.xml
└── README.md
```

---

# JSON Network Configuration

Network settings are stored in:

```text
cfg/network.json
```

Example:

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "localhost",
  "port": 5050
}
```

## Configuration Fields

### `protocol`

Determines which transport protocol the application uses.

Accepted values:

```text
tcp
udp
```

Example:

```json
"protocol": "tcp"
```

causes the launcher to use the TCP implementation.

```json
"protocol": "udp"
```

causes the launcher to use the UDP implementation.

### `type`

Determines whether the program operates as a client or server.

Accepted values:

```text
client
server
```

Example:

```json
"type": "client"
```

causes a client class to be initialized.

```json
"type": "server"
```

causes a server class to be initialized.

### `ip`

Specifies the address of the server that a client should communicate with.

For local testing:

```json
"ip": "localhost"
```

When using two different computers on the same network, this can instead contain the IP address of the computer running the server.

The IP value is required for client configurations.

### `port`

Specifies the network port used for communication.

Example:

```json
"port": 5050
```

The client and server must use the same port when communicating with each other.

---

# Configuration Examples

## TCP Server

```json
{
  "protocol": "tcp",
  "type": "server",
  "ip": "localhost",
  "port": 5050
}
```

This configuration causes `NetworkLauncher` to initialize:

```java
new TcpServer(5050);
```

## TCP Client

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "localhost",
  "port": 5050
}
```

This causes `NetworkLauncher` to initialize:

```java
new TcpClient("localhost", 5050);
```

## UDP Server

```json
{
  "protocol": "udp",
  "type": "server",
  "ip": "localhost",
  "port": 5000
}
```

This causes `NetworkLauncher` to initialize:

```java
new UdpServer(5000);
```

## UDP Client

```json
{
  "protocol": "udp",
  "type": "client",
  "ip": "localhost",
  "port": 5000
}
```

This causes `NetworkLauncher` to initialize:

```java
new UdpClient("localhost", 5000);
```

---

# NetworkConfig

`NetworkConfig.java` represents the JSON configuration as a Java object.

It contains the following properties:

```java
private String protocol;
private String type;
private String ip;
private int port;
```

The names intentionally match the fields in `network.json`.

For example:

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "localhost",
  "port": 5050
}
```

is mapped to a Java `NetworkConfig` object containing:

```text
protocol = "tcp"
type     = "client"
ip       = "localhost"
port     = 5050
```

Getters and setters allow Jackson to populate and retrieve these values.

---

# Jackson JSON Parsing

The project uses Jackson to deserialize the JSON configuration into a Java object.

The main class used for this is:

```java
ObjectMapper
```

Inside `NetworkLauncher`, Jackson reads the configuration with logic equivalent to:

```java
ObjectMapper objectMapper = new ObjectMapper();

File configFile = new File("cfg/network.json");

NetworkConfig config =
        objectMapper.readValue(
                configFile,
                NetworkConfig.class
        );
```

The flow is:

```text
cfg/network.json
       |
       v
Jackson ObjectMapper
       |
       v
NetworkConfig
       |
       v
NetworkLauncher
```

This means the Java source code no longer needs to contain specific IP addresses or port numbers.

---

# Jackson Libraries

This project currently uses Ant rather than Maven for dependency management.

Because Ant does not automatically download and resolve Maven dependencies, the required Jackson JAR files are stored manually inside:

```text
lib/
```

The Jackson libraries used are:

```text
jackson-databind
jackson-core
jackson-annotations
```

## jackson-databind

`jackson-databind` provides `ObjectMapper`, which converts between JSON data and Java objects.

In this project it performs the conversion:

```text
network.json -> NetworkConfig
```

## jackson-core

`jackson-core` provides Jackson's lower-level JSON parsing functionality.

It understands JSON structures such as:

```text
objects
strings
numbers
property names
arrays
```

Databind uses this functionality while reading the configuration file.

## jackson-annotations

`jackson-annotations` provides metadata annotations that Jackson can use when mapping JSON fields to Java properties.

The current `NetworkConfig` class does not require custom annotations because the JSON property names and Java property names match directly, but the dependency is part of Jackson's data-binding system.

---

# Ant Classpath Configuration

The Ant build file was updated so that Jackson can be used during compilation and at runtime.

The library directory is defined with:

```xml
<property name="lib.dir" value="lib"/>
```

A reusable classpath includes every JAR inside the directory:

```xml
<path id="project.classpath">
    <fileset dir="${lib.dir}">
        <include name="*.jar"/>
    </fileset>
</path>
```

The classpath is then supplied to the Java compiler:

```xml
<classpath refid="project.classpath"/>
```

This allows imports such as:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

to compile successfully.

---

# NetworkLauncher

`NetworkLauncher` is now the central entry point for starting the networking application.

Instead of directly starting `TcpClient`, `TcpServer`, `UdpClient`, or `UdpServer`, the launcher first loads the JSON configuration.

The decision process is:

```text
                    network.json
                         |
                         v
                   NetworkConfig
                         |
                         v
                  NetworkLauncher
                         |
                  Which protocol?
                   /           \
                 TCP           UDP
                 /               \
           Which type?        Which type?
            /     \            /     \
         Server  Client     Server  Client
           |       |          |       |
           v       v          v       v
      TcpServer TcpClient UdpServer UdpClient
```

The launcher first checks:

```java
config.getProtocol()
```

and then:

```java
config.getType()
```

to determine which class should be initialized.

---

# Configuration Validation

`NetworkLauncher` validates the configuration before starting the network.

It checks that:

* `protocol` is present
* `type` is present
* the port is within the valid range
* clients have an IP address

Invalid configurations cause an `IllegalArgumentException` with a descriptive error message.

For example, a configuration such as:

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "",
  "port": 5050
}
```

is rejected because a client requires a destination IP address.

---

# Changes to TCP and UDP Classes

Previously, networking classes could use internally defined default values such as:

```java
"localhost"
```

and fixed ports.

The updated design requires configuration to be supplied when the object is created.

For example, instead of:

```java
new TcpClient();
```

the launcher uses:

```java
new TcpClient(
    config.getIp(),
    config.getPort()
);
```

Similarly, instead of allowing a TCP server to choose a default port, the launcher supplies the configured value:

```java
new TcpServer(
    config.getPort()
);
```

The same pattern applies to the UDP implementations.

This separates two responsibilities:

```text
NetworkLauncher
    -> decides configuration

TCP/UDP classes
    -> perform network communication
```

The TCP and UDP classes therefore focus on networking rather than deciding how they should be configured.

---

# Console Messaging

Starting the network connection and reading terminal input are separate operations.

After initializing the networking object, `NetworkLauncher` starts both:

```java
client.start();
client.startConsoleInput();
```

or:

```java
server.start();
server.startConsoleInput();
```

`start()` starts the networking behavior.

`startConsoleInput()` reads messages entered in the terminal and sends them through the appropriate network implementation.

The message flow is therefore:

```text
User types message
       |
       v
startConsoleInput()
       |
       v
sendMessage()
       |
       v
TCP or UDP
       |
       v
Remote application
       |
       v
Message handler
       |
       v
Terminal output
```

---

# Building the Project

## Requirements

The project requires:

* Java 22
* Apache Ant
* the Jackson JAR files inside `lib/`

To confirm Java is available:

```bash
java -version
```

To confirm Ant is available:

```bash
ant -version
```

---

## Compile

From the project root:

```bash
ant compile
```

This:

1. creates the build directories
2. compiles the Java source files
3. includes Jackson through the configured classpath

---

## Clean Build

To delete generated build files and rebuild the project:

```bash
ant rebuild
```

The build process performs:

```text
clean
  |
  v
init
  |
  v
compile
  |
  v
jar
```

A successful build ends with:

```text
BUILD SUCCESSFUL
```

---

# Running the Application

The Ant `run` target starts:

```text
com.md.network.NetworkLauncher
```

Run the project with:

```bash
ant run
```

`NetworkLauncher` then reads:

```text
cfg/network.json
```

and decides which networking implementation to launch.

---

# Testing TCP Locally

Two terminal windows can be used to test the server and client on the same computer.

## Step 1: Configure the TCP server

Set `cfg/network.json` to:

```json
{
  "protocol": "tcp",
  "type": "server",
  "ip": "localhost",
  "port": 5050
}
```

Run:

```bash
ant run
```

The server should start and wait for a client.

Example output:

```text
TCP Server started.
Opening server on port 5050
Waiting for client...
```

## Step 2: Change the configuration to client

Keep the first terminal running.

Change `network.json` to:

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "localhost",
  "port": 5050
}
```

Open a second terminal and run:

```bash
ant run
```

The client should connect.

Example client output:

```text
Trying to connect to server...
Connected to server.
```

The server should display:

```text
Client connected!
```

Messages typed into either terminal can then be sent to the other side.

The already-running server is not affected when `network.json` is changed because the configuration is loaded when the application starts.

---

# Testing Between Two Computers

When testing on two computers, both devices must use the same protocol and port.

For example, the server configuration can use:

```json
{
  "protocol": "tcp",
  "type": "server",
  "ip": "localhost",
  "port": 5050
}
```

The client must use the server computer's reachable IP address:

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "192.168.1.20",
  "port": 5050
}
```

The exact IP address depends on the network being used.

---

# Why This Design Is More Generic

The previous approach coupled network configuration to Java source code.

For example:

```java
private static final String DEFAULT_SERVER_ADDRESS = "localhost";
private static final int DEFAULT_PORT = 5050;
```

Changing the network setup could therefore require changing Java source code.

The new approach separates configuration from implementation:

```text
network.json
     |
     | configuration
     v
NetworkLauncher
     |
     | initialization
     v
TCP / UDP implementation
```

Changing from TCP to UDP, client to server, or one IP/port to another can now be done through the JSON configuration rather than by modifying networking source code.

For example:

```json
{
  "protocol": "tcp",
  "type": "client",
  "ip": "localhost",
  "port": 5050
}
```

can be changed to:

```json
{
  "protocol": "udp",
  "type": "server",
  "ip": "localhost",
  "port": 6000
}
```

without changing `NetworkLauncher`, `TcpClient`, `TcpServer`, `UdpClient`, or `UdpServer`.

---

# Summary of Changes

This update introduces configuration-driven networking to the project.

The main changes are:

* Added `cfg/network.json`
* Added configurable protocol selection (`tcp` or `udp`)
* Added configurable network role selection (`client` or `server`)
* Moved IP and port configuration out of networking classes
* Added `NetworkConfig`
* Added `NetworkLauncher`
* Added Jackson for JSON parsing
* Added a `lib` directory for Jackson dependencies
* Updated the Ant classpath to compile with external libraries
* Updated runtime classpath support
* Added an Ant `run` target
* Updated TCP and UDP clients and servers to receive configuration externally
* Preserved console-based message sending and receiving
* Added validation for configuration values

The result is a networking library whose protocol, role, IP address, and port can all be selected through configuration rather than hardcoded Java values.
