FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/payment-service-*.jar payment-service.jar
EXPOSE 8182
ENTRYPOINT ["java", "-jar", "payment-service.jar"]