# Use the official OpenJDK 17 image as the base image
FROM amazoncorretto:17-alpine3.22

# Set the working directory inside the container
WORKDIR /app

# Copy the Spring Boot jar file into the container
COPY target/*.jar /app/admin-console.jar

EXPOSE 8097

ENTRYPOINT ["java", "-jar", "/app/admin-console.jar"]