FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY product-services/shopify-bridge-service ./product-services/shopify-bridge-service

RUN mvn -f product-services/shopify-bridge-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/product-services/shopify-bridge-service/target/shopify-bridge-service-*.jar /app/shopify-bridge-service.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/shopify-bridge-service.jar"]
