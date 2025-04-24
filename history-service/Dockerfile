FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/history-service-*.jar history-service.jar
EXPOSE 8184
ENTRYPOINT ["java", "-jar", "history-service.jar"]