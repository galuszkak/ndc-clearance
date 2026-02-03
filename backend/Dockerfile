# Backend Dockerfile

FROM docker.io/gradle:jdk25 AS build
WORKDIR /app
COPY . .
# Skip tests during build as they are run separately (and require external volume)
RUN ./gradlew shadowJar --no-daemon

FROM docker.io/bellsoft/liberica-runtime-container:jre-25-glibc
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
COPY src/main/resources/schemas /app/schemas
ENV SCHEMA_ROOT=/app/schemas
CMD ["java", "-jar", "app.jar"]
