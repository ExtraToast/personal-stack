FROM gradle:8.13-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle :services:assistant-api:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS otel
RUN apk add --no-cache curl && \
    curl -fsSL -o /otel-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar"

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/assistant-api/build/libs/*.jar app.jar
COPY --from=otel /otel-javaagent.jar otel-javaagent.jar
EXPOSE 8082
ENTRYPOINT [ \
  "java", \
  "-javaagent:otel-javaagent.jar", \
  "-Dotel.service.name=assistant-api", \
  "-Dotel.exporter.otlp.endpoint=${OTEL_ENDPOINT:-http://otel-collector:4318}", \
  "-Dotel.logs.exporter=none", \
  "-jar", "app.jar" \
]
