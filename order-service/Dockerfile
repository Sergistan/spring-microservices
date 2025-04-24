FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/order-service-*.jar order-service.jar
EXPOSE 8180
ENTRYPOINT ["java", "-jar", "order-service.jar"]