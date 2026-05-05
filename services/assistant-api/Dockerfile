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
RUN gradle :services:assistant-api:dependencies --no-daemon || true

# Layer 2: Copy source code and build
COPY libs/kotlin-common/src/main/ libs/kotlin-common/src/main/
COPY services/assistant-api/src/main/ services/assistant-api/src/main/
RUN gradle :services:assistant-api:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine AS otel
RUN apk add --no-cache curl && \
    curl -fsSL -o /otel-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar"

# AppCDS training stage — see auth-api/Dockerfile for the full rationale.
# Extracts the fat jar into a layered layout, refreshes the Spring context
# under the `cds-training` profile (Flyway off so no Postgres needed), and
# dumps `application.jsa` for the runtime stage to SharedArchiveFile.
FROM eclipse-temurin:25-jre-alpine AS cds-train
WORKDIR /train
COPY --from=build /app/services/assistant-api/build/libs/*.jar app.jar
COPY --from=otel /otel-javaagent.jar /otel-javaagent.jar
RUN java -Djarmode=tools -jar app.jar extract --destination /train/app && \
    cd /train/app && \
    java \
      -Dspring.profiles.active=cds-training \
      -Dspring.context.exit=onRefresh \
      -XX:ArchiveClassesAtExit=/train/app/application.jsa \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:+AllowArchivingWithJavaAgent \
      -javaagent:/otel-javaagent.jar \
      -jar app.jar && \
    test -s /train/app/application.jsa

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=cds-train /train/app/ ./
COPY --from=otel /otel-javaagent.jar otel-javaagent.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/application.jsa", "-javaagent:otel-javaagent.jar", "-jar", "app.jar"]
