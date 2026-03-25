FROM gradle:8.13-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :services:assistant-api:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/assistant-api/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
