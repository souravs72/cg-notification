FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent POM and all modules (Maven validates all modules in parent POM)
COPY pom.xml .
COPY common-proto ./common-proto
COPY notification-api ./notification-api
COPY email-worker ./email-worker
COPY whatsapp-worker ./whatsapp-worker

# Build only notification-api and its dependencies (common-proto)
# Cache Maven dependencies for faster subsequent builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl notification-api -am clean package -DskipTests

FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app

COPY --from=build /app/notification-api/target/notification-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
