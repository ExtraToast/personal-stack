FROM gradle:9.4-jdk21-alpine AS build
WORKDIR /app

COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY libs/kotlin-common/build.gradle.kts libs/kotlin-common/
COPY services/auth-api/build.gradle.kts services/auth-api/
COPY services/assistant-api/build.gradle.kts services/assistant-api/
COPY services/knowledge-api/build.gradle.kts services/knowledge-api/
COPY services/agent-gateway/build.gradle.kts services/agent-gateway/
COPY services/system-tests/build.gradle.kts services/system-tests/

RUN gradle :services:agent-gateway:dependencies --no-daemon || true

COPY libs/kotlin-common/src/main/ libs/kotlin-common/src/main/
COPY services/agent-gateway/src/main/ services/agent-gateway/src/main/
RUN gradle :services:agent-gateway:bootJar --no-daemon

FROM bellsoft/liberica-runtime-container:jdk-21-slim-glibc
WORKDIR /app
ARG GIT_SHA=unknown
ENV SERVICE_VERSION=${GIT_SHA}
COPY --from=build /app/services/agent-gateway/build/libs/*.jar /app/agent-gateway.jar
EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75", "-jar", "/app/agent-gateway.jar"]
