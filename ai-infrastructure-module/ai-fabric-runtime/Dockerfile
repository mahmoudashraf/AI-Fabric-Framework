# syntax=docker/dockerfile:1.7

# Build context: repo root
# Example:
#   docker build -f ai-infrastructure-module/ai-fabric-runtime/Dockerfile -t ai-fabric-runtime:dev .

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY ai-infrastructure-module ai-infrastructure-module

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B --no-transfer-progress -f ai-infrastructure-module/pom.xml \
    -pl ai-fabric-runtime -am -DskipTests package

FROM eclipse-temurin:21-jre

ARG SOURCE_COMMIT=unknown
ARG SOURCE_BUILD_TIME=unknown

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl gosu && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 10001 -g root runtime

RUN mkdir -p /data /app/data /config && chown -R 10001:0 /data /app/data /config

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar /app/runtime.jar
COPY ai-infrastructure-module/ai-fabric-runtime/docker-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 8097

ENV JAVA_OPTS="" \
    APP_BUILD_COMMIT="${SOURCE_COMMIT}" \
    APP_BUILD_TIME="${SOURCE_BUILD_TIME}"

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8097/actuator/health/liveness || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
