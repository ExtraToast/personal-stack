FROM gradle:9.4-jdk21-alpine AS build
WORKDIR /app

# Layer 1: Copy only build scripts for dependency caching
COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY libs/kotlin-common/build.gradle.kts libs/kotlin-common/
COPY services/auth-api/build.gradle.kts services/auth-api/
COPY services/assistant-api/build.gradle.kts services/assistant-api/
COPY services/system-tests/build.gradle.kts services/system-tests/

# Resolve dependencies (cached unless build files change)
RUN gradle :services:auth-api:dependencies --no-daemon || true

# Layer 2: Copy source code and build
COPY libs/kotlin-common/src/main/ libs/kotlin-common/src/main/
COPY services/auth-api/src/main/ services/auth-api/src/main/
RUN gradle :services:auth-api:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine AS otel
RUN apk add --no-cache curl && \
    curl -fsSL -o /otel-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar"

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/auth-api/build/libs/*.jar app.jar
COPY --from=otel /otel-javaagent.jar otel-javaagent.jar
EXPOSE 8081
ENTRYPOINT ["java", "-javaagent:otel-javaagent.jar", "-jar", "app.jar"]
