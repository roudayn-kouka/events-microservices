FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/statistics-service-*.jar app.jar
USER app
EXPOSE 8105
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
