# Railway-friendly Platform backend image.
#
# Build context: repo root
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY Platfrom/backend Platfrom/backend

RUN mvn -f Platfrom/backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10010 -g root platform && \
    mkdir -p /app && \
    chown -R 10010:0 /app

COPY --from=build /workspace/Platfrom/backend/target/ai-enablement-platform-backend-*.jar /app/platform-backend.jar

USER 10010

EXPOSE 8088

ENV JAVA_OPTS=""

ENTRYPOINT ["/bin/sh", "-lc", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8088} -jar /app/platform-backend.jar"]
