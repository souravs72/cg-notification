# Uses pre-built JAR - run 'mvn -pl whatsapp-worker -am package -DskipTests' first.
FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app

COPY whatsapp-worker/target/whatsapp-worker.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
