FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/getaway-server-*.jar getaway-server.jar
EXPOSE 8072
ENTRYPOINT ["java", "-jar", "getaway-server.jar"]