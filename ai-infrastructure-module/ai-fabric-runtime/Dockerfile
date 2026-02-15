# Build context: repo root
# Example:
#   docker build -f ai-infrastructure-module/ai-fabric-runtime/Dockerfile -t ai-fabric-runtime:dev .

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache dependencies first
COPY ai-infrastructure-module/pom.xml ai-infrastructure-module/pom.xml
COPY ai-infrastructure-module/ai-fabric-runtime/pom.xml ai-infrastructure-module/ai-fabric-runtime/pom.xml

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am -DskipTests dependency:go-offline

# Copy the sources needed to build the runtime (and its parents/modules)
COPY ai-infrastructure-module ai-infrastructure-module

RUN mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10001 -g root runtime

COPY --from=build /workspace/ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar /app/runtime.jar

EXPOSE 8097

USER 10001

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/runtime.jar"]

