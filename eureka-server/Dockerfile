FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/eureka-server-*.jar eureka-server.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "eureka-server.jar"]