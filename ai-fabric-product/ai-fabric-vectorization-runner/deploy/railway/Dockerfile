# Railway-friendly vectorization runner image.
#
# Build context: repo root
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY ai-fabric-product ai-fabric-product

RUN mvn -f ai-fabric-product/pom.xml -pl ai-fabric-vectorization-runner -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10003 -g root vectorization

COPY --from=build /workspace/ai-fabric-product/ai-fabric-vectorization-runner/target/ai-fabric-vectorization-runner-*.jar /app/vectorization-runner.jar

EXPOSE 8099

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/vectorization-runner.jar"]
