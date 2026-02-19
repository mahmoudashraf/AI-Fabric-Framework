# Build context: repo root
# Example:
#   docker build -f ai-infrastructure-module/ai-infrastructure-generic-rest-connector/Dockerfile -t ai-fabric-generic-rest-connector:dev .

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache dependencies first
COPY ai-infrastructure-module/pom.xml ai-infrastructure-module/pom.xml
COPY ai-infrastructure-module/ai-infrastructure-generic-rest-connector/pom.xml ai-infrastructure-module/ai-infrastructure-generic-rest-connector/pom.xml

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-infrastructure-generic-rest-connector -am -DskipTests dependency:go-offline

# Copy the sources needed to build the connector (and its parents/modules)
COPY ai-infrastructure-module ai-infrastructure-module

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-infrastructure-generic-rest-connector -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10001 -g root connector

COPY --from=build /workspace/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/target/ai-infrastructure-generic-rest-connector-*.jar /app/connector.jar

EXPOSE 8082

USER 10001

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/connector.jar"]

