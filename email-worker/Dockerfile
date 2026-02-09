# Uses pre-built JAR - run 'mvn -pl email-worker -am package -DskipTests' first.
FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app

COPY email-worker/target/email-worker.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
