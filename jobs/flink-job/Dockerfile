# Multi-stage build for JAR server
# Stage 1: Build the JAR
FROM gradle:8.11-jdk11 AS builder

WORKDIR /build

# Copy Gradle files first for better caching
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle.kts ./common/
COPY flink-job/build.gradle.kts ./flink-job/

# Download dependencies (this layer will be cached)
RUN gradle :flink-job:dependencies --no-daemon || true

# Copy source code
COPY common/src ./common/src
COPY flink-job/src ./flink-job/src

# Build the fat JAR
RUN gradle :flink-job:shadowJar --no-daemon

# Stage 2: Serve the JAR via nginx
FROM nginx:alpine

# Copy the JAR to nginx html directory
COPY --from=builder /build/flink-job/build/libs/flink-job-*.jar /usr/share/nginx/html/

# Simple nginx config to serve files
RUN echo 'server { \
    listen 8080; \
    location / { \
        root /usr/share/nginx/html; \
        autoindex on; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 8080
