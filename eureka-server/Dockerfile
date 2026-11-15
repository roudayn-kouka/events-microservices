FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/eureka-server-*.jar app.jar
USER app
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
