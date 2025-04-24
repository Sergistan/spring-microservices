FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/config-server-*.jar config-server.jar
EXPOSE 8071
ENTRYPOINT ["java", "-jar", "config-server.jar"]