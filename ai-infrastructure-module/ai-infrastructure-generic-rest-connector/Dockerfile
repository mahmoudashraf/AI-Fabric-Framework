# Build context: repo root
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

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-infrastructure-generic-rest-connector -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl gosu && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 10002 -g root connector

RUN mkdir -p /config && chown -R 10002:0 /config

COPY --from=build /workspace/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/target/ai-infrastructure-generic-rest-connector-*.jar /app/connector.jar
COPY ai-infrastructure-module/ai-infrastructure-generic-rest-connector/docker-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 8082

ENV JAVA_OPTS=""

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8082/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
