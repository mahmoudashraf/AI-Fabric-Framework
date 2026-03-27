# Railway-friendly Runtime image (includes the ecommerce-store demo runtime config)
#
# Build context: repo root
# Use this Dockerfile in Railway (instead of ai-fabric-runtime/Dockerfile) if you want
# a runnable runtime that:
# - loads `ai-actions.yml` + `ai-entity-config.yml`
# - has `ACTIONS_CONNECTOR_BASE_URL` / `ACTIONS_CONNECTOR_API_KEY` env bindings
#
# It bakes the config from:
#   Real_Apps/ecommerce-store/deploy/runtime/config
#
# Example local build:
#   docker build -f ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile -t ai-fabric-runtime:railway .

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY ai-infrastructure-module ai-infrastructure-module

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10001 -g root runtime

RUN mkdir -p /data /config \
  && chown -R 10001:0 /data /config

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar /app/runtime.jar

# Bake demo runtime config into the image so Railway doesn't need a config mount.
COPY Real_Apps/ecommerce-store/deploy/runtime/config/ /config/

EXPOSE 8097

USER 10001

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/runtime.jar --spring.config.additional-location=file:/config/ \"$@\"", "--"]
