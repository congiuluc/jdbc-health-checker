# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Package the application (skip tests for speed in this example, but run them in real CI)
RUN ./mvnw package -DskipTests || mvn package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy

# Create a non-root user
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

WORKDIR /app

# Copy the built artifact from the build stage
# Note: The shade plugin in pom.xml should produce a jar with dependencies, usually named original-artifactId-version.jar or artifactId-version.jar depending on config.
# Standard maven package produces target/sql-batch-job-1.0.0-SNAPSHOT.jar
COPY --from=build /src/target/sql-batch-job-1.0.0-SNAPSHOT.jar /app/app.jar

# Set ownership to non-root user
RUN chown appuser:appgroup /app/app.jar

# Switch to non-root user
USER appuser

# Expose the health check port
EXPOSE 8080

# Healthcheck instruction for Docker (optional, K8s uses probes)
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-jar", "/app/app.jar"]
