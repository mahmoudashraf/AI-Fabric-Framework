# Provider-neutral embedding worker image.
#
# Build context: repo root
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

ARG AI_FABRIC_FRAMEWORK_REPOSITORY=https://github.com/Loom-AI-Labs/ai-fabric-framework.git
ARG AI_FABRIC_FRAMEWORK_REF=ai-fabric-framework-v0.3.1

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "$AI_FABRIC_FRAMEWORK_REF" "$AI_FABRIC_FRAMEWORK_REPOSITORY" /tmp/ai-fabric-framework \
    && mvn -f /tmp/ai-fabric-framework/ai-infrastructure-module/pom.xml -B -V -DskipTests install \
        -pl '!integration-Testing/vector-contract-tests,!integration-Testing/testcontainers-support,!integration-Testing/integration-tests,!integration-Testing/relationship-query-integration-tests,!integration-Testing/chat-session-integration-tests,!integration-Testing/behavior-integration-tests' \
        -am \
    && rm -rf /tmp/ai-fabric-framework

COPY ai-fabric-product/pom.xml ./ai-fabric-product/
COPY ai-fabric-product ./ai-fabric-product

RUN mvn -f ai-fabric-product/pom.xml -pl ai-fabric-embedding-worker -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/ai-fabric-product/ai-fabric-embedding-worker/target/ai-fabric-embedding-worker-*.jar /app/embedding-worker.jar

EXPOSE 8097

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/embedding-worker.jar"]
