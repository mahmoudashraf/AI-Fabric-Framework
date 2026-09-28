FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY verification-support/external-vehicle-provider-simulator/pom.xml ./pom.xml
COPY verification-support/external-vehicle-provider-simulator/src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 simulator \
    && useradd --uid 10001 --gid simulator --shell /usr/sbin/nologin simulator \
    && mkdir -p /app/data \
    && chown -R simulator:simulator /app

COPY --from=build /workspace/target/external-vehicle-provider-simulator-*.jar /app/simulator.jar

EXPOSE 8110
VOLUME ["/app/data"]

ENTRYPOINT ["sh", "-c", "chown -R simulator:simulator /app/data && exec gosu simulator java $JAVA_OPTS -jar /app/simulator.jar"]
