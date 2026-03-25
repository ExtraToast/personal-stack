FROM gradle:8.13-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :services:auth-api:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/auth-api/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
