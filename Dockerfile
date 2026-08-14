FROM eclipse-temurin:22.0.2_9-jre-noble

WORKDIR /app

COPY target/network-lib-1.0.0.jar network-lib.jar

EXPOSE 5050/tcp
EXPOSE 5000/udp

CMD ["java", "-jar", "network-lib.jar"]
