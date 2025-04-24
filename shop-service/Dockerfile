FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY build/libs/shop-service-*.jar shop-service.jar
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "shop-service.jar"]