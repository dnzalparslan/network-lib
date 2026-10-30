
# Network Library

A reusable Java networking library that supports client-server communication using TCP and UDP protocols.

The library provides a shared foundation for sending and receiving byte-array messages, managing network connections, and integrating networking functionality into other Java applications.

## Features

- TCP and UDP client-server communication
- Byte-array-based message transmission
- Configurable message handlers
- Connection and disconnection management
- Multithreaded message receiving
- Reusable networking components
- JAR packaging for integration into other Java projects

## Technologies Used

- Java
- TCP & UDP
- Java Socket API
- Multithreading
- Apache Ant

## How to Build

**Requirements:** JDK 22 or later and Apache Ant.

1. Clone the repository:

```bash
git clone https://github.com/dnzalparslan/network-lib.git
cd network-lib
```

2. Build the library:

```bash
ant jar
```

3. The compiled JAR will be available at:

`dist/network-lib.jar`

The JAR can then be added as a dependency to other Java applications.

## Example Application

This library is used in the [Circle Rotation Demo](https://github.com/dnzalparslan/circle-rotation-demo), which demonstrates remote control of a Java Swing application through TCP and UDP commands.

## What I Learned

This project helped me strengthen my understanding of network programming, reusable software design, and client-server communication. I also gained experience organizing Java networking functionality into a library that can be integrated into different applications.
