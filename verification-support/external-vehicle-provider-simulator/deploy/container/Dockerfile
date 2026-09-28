FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY verification-support/external-vehicle-provider-simulator/pom.xml ./pom.xml
COPY verification-support/external-vehicle-provider-simulator/src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --gid 10001 simulator \
    && useradd --uid 10001 --gid simulator --shell /usr/sbin/nologin simulator \
    && mkdir -p /app/data \
    && chown -R simulator:simulator /app

COPY --from=build /workspace/target/external-vehicle-provider-simulator-*.jar /app/simulator.jar

USER simulator
EXPOSE 8110
VOLUME ["/app/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/simulator.jar"]
