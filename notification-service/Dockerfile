FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/notification-service-*.jar notification-service.jar
EXPOSE 8183
ENTRYPOINT ["java", "-jar", "notification-service.jar"]