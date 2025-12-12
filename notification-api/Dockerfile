FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent pom first and install it to local Maven repository
COPY pom.xml .
RUN mvn install -N -DskipTests

# Copy common-proto and build it
COPY common-proto/pom.xml ./common-proto/
COPY common-proto/src ./common-proto/src
WORKDIR /app/common-proto
RUN mvn clean install -DskipTests

# Build notification-api
WORKDIR /app
COPY notification-api/pom.xml ./notification-api/
COPY notification-api/src ./notification-api/src
WORKDIR /app/notification-api
RUN mvn clean package spring-boot:repackage -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for healthchecks
RUN apk add --no-cache curl

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/notification-api/target/notification-api-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

