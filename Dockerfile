FROM eclipse-temurin:25-alpine
WORKDIR /app
COPY target/cine-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]