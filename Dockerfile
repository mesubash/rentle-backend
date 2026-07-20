# Rentle backend — multi-stage. Build the bootJar, then run it on a slim JRE as non-root.
# All runtime config comes from the environment (see docker-compose.yml / application-prod.yml).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Warm the dependency cache (best-effort; ignore failure when no sources yet).
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r rentle && useradd -r -g rentle rentle
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown -R rentle:rentle /app
USER rentle
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
