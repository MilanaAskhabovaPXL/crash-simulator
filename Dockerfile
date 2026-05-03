FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/crash-simulator-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
