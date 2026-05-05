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

# AppCDS training stage. Spring Boot's bootJar tools mode extracts the fat jar
# into a layered layout (loader + dependencies/ + application/) so HotSpot can
# share class metadata across launches. We then run the app with
# `-Dspring.context.exit=onRefresh` and `-XX:ArchiveClassesAtExit` so the JVM
# refreshes the full ApplicationContext (loading every Spring/jOOQ/jackson
# class we'll need at boot) and dumps `application.jsa` before exiting. The
# `cds-training` profile disables Flyway so this build-time run doesn't need
# a real Postgres. End result: a ~50% startup speedup at runtime.
FROM eclipse-temurin:25-jre-alpine AS cds-train
WORKDIR /train
COPY --from=build /app/services/auth-api/build/libs/*.jar app.jar
# Include the OTel agent in the training run so its auto-instrumented Spring
# MVC / JDBC class shapes are captured in the CDS archive — without this,
# every runtime class the agent rewrites would miss the cache and re-load
# from disk + retransform, eating most of the AppCDS speedup.
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
EXPOSE 8081
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/application.jsa", "-javaagent:otel-javaagent.jar", "-jar", "app.jar"]
