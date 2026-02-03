# Build Stage
FROM docker.io/gradle:jdk25 AS build
WORKDIR /app
COPY . .
# Skip tests during build as they are run separately (and require external volume)
RUN ./gradlew shadowJar --no-daemon

# Runtime Stage
FROM docker.io/bellsoft/liberica-runtime-container:jre-25-musl
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar /app/app.jar
COPY src/main/resources/schemas /app/schemas
ENV SCHEMA_ROOT=/app/schemas
CMD ["/usr/bin/java", "-jar", "/app/app.jar"]
