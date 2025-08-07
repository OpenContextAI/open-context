# Multi-stage build for Spring Boot application
FROM openjdk:21-jdk-slim as builder

# Install curl, wget and unzip
RUN apt-get update && apt-get install -y curl wget unzip && \
    wget -O /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.5-bin.zip && \
    unzip /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-8.5/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy build files
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY src/ src/

# Build the application
RUN gradle clean build -x test

# Runtime stage
FROM openjdk:21-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Set working directory
WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appuser /app

# Switch to app user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM arguments for production
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:+UseContainerSupport"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
