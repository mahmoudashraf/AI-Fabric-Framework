FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY product-services/basic-loomai-inbox-service/pom.xml ./pom.xml
COPY product-services/basic-loomai-inbox-service/src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home loomai && mkdir -p /app/data && chown -R loomai:loomai /app
COPY --from=build /workspace/target/basic-loomai-inbox-service-*.jar /app/basic-loomai-inbox.jar
USER 10001
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/basic-loomai-inbox.jar"]
