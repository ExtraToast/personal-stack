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

# Eclipse Temurin only used here for the otel jar download — its alpine
# variant has curl out of the box and is small. The runtime stage has
# moved to BellSoft Liberica below.
FROM eclipse-temurin:25-jre-alpine AS otel
RUN apk add --no-cache curl && \
    curl -fsSL -o /otel-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar"

# Runtime base: BellSoft Liberica JDK 21 with CRaC patches on Debian slim
# (glibc). CRaC is not in upstream Eclipse Temurin and CRIU does not work
# reliably on musl/Alpine, so the JDK switch is the precondition for the
# build-time checkpoint flow that lands in follow-up PRs (CI training +
# k8s capabilities). JDK 25 has no CRaC build yet; 21 LTS is the
# recommended ecosystem version.
#
# AppCDS is intentionally dropped here: Liberica CRaC's CDS handling
# differs from standard OpenJDK (`-XX:ArchiveClassesAtExit` warns
# "unsupported when base CDS archive is not loaded"), and CRaC restore
# will replace AppCDS as the cold-start optimisation in PR 4 anyway.
# Cold start temporarily regresses to the pre-AppCDS baseline (~250-
# 300 s); the 600 s startupProbe budget on auth-api covers it.
# assistant-api needs PR #254 (startupProbe 300 s -> 600 s, mirrors
# auth-api PR #250) merged first.
FROM bellsoft/liberica-runtime-container:jdk-21-crac-slim-glibc
WORKDIR /app
COPY --from=build /app/services/auth-api/build/libs/*.jar app.jar
COPY --from=otel /otel-javaagent.jar otel-javaagent.jar
EXPOSE 8081
ENTRYPOINT ["java", "-javaagent:otel-javaagent.jar", "-jar", "app.jar"]
