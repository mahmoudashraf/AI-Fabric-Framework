# Railway-friendly Runtime image.
#
# Build context: repo root
# This image is packaging-only. Platform-managed deployments are expected to provide
# config via environment variables / remote artifacts, not via baked demo files.

FROM maven:3.9.6-eclipse-temurin-21 AS build

ARG AI_FABRIC_FRAMEWORK_REPOSITORY=https://github.com/Loom-AI-Labs/ai-fabric-framework.git
ARG AI_FABRIC_FRAMEWORK_REF=ai-fabric-framework-v0.3.1

WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "$AI_FABRIC_FRAMEWORK_REF" "$AI_FABRIC_FRAMEWORK_REPOSITORY" /tmp/ai-fabric-framework \
    && mvn -f /tmp/ai-fabric-framework/ai-infrastructure-module/pom.xml -B -V -DskipTests install \
        -pl '!integration-Testing/vector-contract-tests,!integration-Testing/testcontainers-support,!integration-Testing/integration-tests,!integration-Testing/relationship-query-integration-tests,!integration-Testing/chat-session-integration-tests,!integration-Testing/behavior-integration-tests' \
        -am \
    && rm -rf /tmp/ai-fabric-framework

COPY ai-infrastructure-module ai-infrastructure-module

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl gosu && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 10001 -g root runtime

RUN mkdir -p /data /app/data /config && chown -R 10001:0 /data /app/data /config

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar /app/runtime.jar
COPY ai-infrastructure-module/ai-fabric-runtime/docker-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 8097

ENV JAVA_OPTS=""

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8097/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
