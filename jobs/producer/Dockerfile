# Multi-stage build for event producer
# Stage 1: Build the JAR
FROM gradle:8.11-jdk11 AS builder

WORKDIR /build

# Copy Gradle files first for better caching
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle.kts ./common/
COPY producer/build.gradle.kts ./producer/

# Download dependencies (this layer will be cached)
RUN gradle :producer:dependencies --no-daemon || true

# Copy source code
COPY common/src ./common/src
COPY producer/src ./producer/src

# Build the fat JAR
RUN gradle :producer:shadowJar --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:11-jre

WORKDIR /app

# Copy the JAR from builder
COPY --from=builder /build/producer/build/libs/event-producer-*.jar /app/event-producer.jar

# Run the producer
ENTRYPOINT ["java", "-jar", "/app/event-producer.jar"]
