# Image d'exécution seule : le JAR est construit avant (mvn package), en local, par Jenkins ou avant rsync vers AWS.
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/config-server-*.jar app.jar
USER app
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
