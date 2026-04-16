FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY ai-infrastructure-module/pom.xml ./ai-infrastructure-module/
COPY ai-fabric-product/pom.xml ./ai-fabric-product/
COPY ai-infrastructure-module ./ai-infrastructure-module
COPY ai-fabric-product ./ai-fabric-product

RUN mvn -f ai-infrastructure-module/pom.xml -DskipTests install
RUN mvn -f ai-fabric-product/pom.xml -pl ai-fabric-embedding-worker -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/ai-fabric-product/ai-fabric-embedding-worker/target/ai-fabric-embedding-worker-*.jar /app/embedding-worker.jar

EXPOSE 8097

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/embedding-worker.jar"]
