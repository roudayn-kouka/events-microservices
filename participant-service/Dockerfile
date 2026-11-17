FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/participant-service-*.jar app.jar
USER app
EXPOSE 8102
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
