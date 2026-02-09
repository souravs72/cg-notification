# Uses pre-built JAR - run 'mvn -pl notification-api -am package -DskipTests' first.
# Faster builds, no Maven/network inside Docker.
FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app

COPY notification-api/target/notification-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
