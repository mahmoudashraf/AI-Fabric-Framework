# Provider-neutral Platform backend image.
#
# Build context: repo root
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY Platfrom/backend Platfrom/backend

RUN mvn -f Platfrom/backend/pom.xml -DskipTests package

FROM node:20-bookworm-slim AS node-runtime

FROM maven:3.9.6-eclipse-temurin-21

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends bash curl jq python3 && \
    rm -rf /var/lib/apt/lists/*

COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=node-runtime /usr/local/bin/npm /usr/local/bin/npm
COPY --from=node-runtime /usr/local/bin/npx /usr/local/bin/npx
COPY --from=node-runtime /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && \
    ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

RUN useradd -m -d /home/platform -r -u 10010 -g root platform && \
    mkdir -p /app /home/platform/.m2 /home/platform/.npm && \
    chown -R 10010:0 /app /home/platform

COPY --from=build /workspace/Platfrom/backend/target/ai-enablement-platform-backend-*.jar /app/platform-backend.jar
COPY Platfrom/backend /app/Platfrom/backend
COPY Platfrom/ui /app/Platfrom/ui
COPY ai-fabric-product /app/ai-fabric-product
COPY ai-infrastructure-module /app/ai-infrastructure-module
COPY scripts /app/scripts
RUN chown -R 10010:0 /app

USER 10010

EXPOSE 8088

ENV HOME="/home/platform"
ENV MAVEN_CONFIG="/home/platform/.m2"
ENV NPM_CONFIG_CACHE="/home/platform/.npm"
ENV JAVA_OPTS=""

ENTRYPOINT ["/bin/sh", "-lc", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8088} -jar /app/platform-backend.jar"]
