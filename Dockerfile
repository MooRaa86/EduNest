# Use a lightweight Java image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy JAR file into the container
COPY target/*.jar app.jar

# Expose application port (change if needed)
EXPOSE 8080

# Run the JAR when container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
