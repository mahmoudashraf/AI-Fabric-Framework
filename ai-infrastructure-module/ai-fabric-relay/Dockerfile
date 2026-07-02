# Build context: repo root
# Example:
#   docker build -f ai-infrastructure-module/ai-fabric-relay/Dockerfile -t ai-fabric-relay:dev .

FROM maven:3.9.6-eclipse-temurin-21 AS build

ARG AI_FABRIC_FRAMEWORK_REPOSITORY=https://github.com/Loom-AI-Labs/ai-fabric-framework.git
ARG AI_FABRIC_FRAMEWORK_REF=main

WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "$AI_FABRIC_FRAMEWORK_REF" "$AI_FABRIC_FRAMEWORK_REPOSITORY" /tmp/ai-fabric-framework \
    && mvn -f /tmp/ai-fabric-framework/ai-infrastructure-module/pom.xml -B -V -DskipTests install \
        -pl '!integration-Testing/vector-contract-tests,!integration-Testing/testcontainers-support,!integration-Testing/integration-tests,!integration-Testing/relationship-query-integration-tests,!integration-Testing/chat-session-integration-tests,!integration-Testing/behavior-integration-tests' \
        -am \
    && rm -rf /tmp/ai-fabric-framework

# Copy the sources needed to build the relay (and its parents/modules)
COPY ai-infrastructure-module ai-infrastructure-module

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-relay -am package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10001 -g root relay

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-relay/target/ai-fabric-relay-*.jar /app/relay.jar

EXPOSE 8081

USER 10001

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/relay.jar"]
