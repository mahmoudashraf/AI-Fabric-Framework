# syntax=docker/dockerfile:1.7

# Build context: repo root
# Example:
#   docker build -f ai-infrastructure-module/ai-fabric-runtime/Dockerfile -t ai-fabric-runtime:dev .

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY ai-infrastructure-module ai-infrastructure-module
COPY ai-fabric-product ai-fabric-product

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -U --no-transfer-progress -f ai-infrastructure-module/pom.xml \
    -pl ai-fabric-runtime,ai-infrastructure-generic-rest-connector -am -DskipTests package

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -U --no-transfer-progress -f ai-fabric-product/pom.xml \
    -pl ai-fabric-vectorization-runner -am -DskipTests package

FROM eclipse-temurin:21-jre

ARG SOURCE_COMMIT=unknown
ARG SOURCE_BUILD_TIME=unknown

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl gosu && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 10001 -g root runtime \
    && useradd -r -u 10002 -g root connector \
    && useradd -r -u 10003 -g root vectorization

RUN mkdir -p /data /app/data /config && chown -R 10001:0 /data /app/data /config

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar /app/runtime.jar
COPY --from=build /workspace/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/target/ai-infrastructure-generic-rest-connector-*.jar /app/connector.jar
COPY --from=build /workspace/ai-fabric-product/ai-fabric-vectorization-runner/target/ai-fabric-vectorization-runner-*.jar /app/vectorization-runner.jar
COPY ai-infrastructure-module/ai-fabric-runtime/docker-entrypoint.sh /app/runtime-entrypoint.sh
COPY ai-infrastructure-module/ai-infrastructure-generic-rest-connector/docker-entrypoint.sh /app/connector-entrypoint.sh
COPY ai-fabric-product/ai-fabric-vectorization-runner/docker-entrypoint.sh /app/vectorization-runner-entrypoint.sh
COPY ai-infrastructure-module/ai-fabric-runtime/deployment-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh /app/runtime-entrypoint.sh /app/connector-entrypoint.sh /app/vectorization-runner-entrypoint.sh

EXPOSE 8082 8097 8099

ENV JAVA_OPTS="" \
    APP_BUILD_COMMIT="${SOURCE_COMMIT}" \
    APP_BUILD_TIME="${SOURCE_BUILD_TIME}"

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD if [ "${PLATFORM_COOLIFY_SERVICE_ROLE:-runtime}" = "connector" ]; then \
          curl -fsS http://127.0.0.1:8082/actuator/health/liveness; \
        elif [ "${PLATFORM_COOLIFY_SERVICE_ROLE:-runtime}" = "vectorization-runner" ]; then \
          curl -fsS http://127.0.0.1:8099/actuator/health/liveness; \
        else \
          curl -fsS http://127.0.0.1:8097/actuator/health/liveness; \
        fi || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
