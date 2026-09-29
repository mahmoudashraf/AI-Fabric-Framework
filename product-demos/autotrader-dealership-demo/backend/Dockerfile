# Build context: repository root
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace/backend
COPY product-demos/autotrader-dealership-demo/backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline

COPY product-demos/autotrader-dealership-demo/backend/src ./src
RUN mvn -B -ntp clean package

FROM eclipse-temurin:21-jre

WORKDIR /app
ENV PORT=8108
ENV JAVA_OPTS="-Xms256m -Xmx768m"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/dealership-demo-backend-1.0.0-SNAPSHOT.jar /app/app.jar

RUN addgroup --system loomdemo \
    && adduser --system --ingroup loomdemo loomdemo \
    && mkdir -p /app/data \
    && chown -R loomdemo:loomdemo /app

USER loomdemo
EXPOSE 8108

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl --fail --silent --show-error http://127.0.0.1:${PORT}/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
