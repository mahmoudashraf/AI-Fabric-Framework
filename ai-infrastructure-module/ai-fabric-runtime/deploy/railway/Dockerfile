# Railway-friendly Runtime image.
#
# Build context: repo root
# This image is packaging-only. Platform-managed deployments are expected to provide
# config via environment variables / remote artifacts, not via baked demo files.

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY ai-infrastructure-module ai-infrastructure-module

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends gosu && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 10001 -g root runtime

RUN mkdir -p /data /app/data /config && chown -R 10001:0 /data /app/data /config

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar /app/runtime.jar
COPY ai-infrastructure-module/ai-fabric-runtime/docker-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 8097

ENV JAVA_OPTS=""

ENTRYPOINT ["/app/entrypoint.sh"]
