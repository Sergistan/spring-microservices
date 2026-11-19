FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY build/libs/history-service-*.jar app.jar
USER spring
EXPOSE 8184
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 CMD wget -qO- http://localhost:8184/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
