FROM node:20-alpine AS ui-build
WORKDIR /workspace/ui

COPY product-services/shopify-bridge-service/ui/package.json product-services/shopify-bridge-service/ui/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY product-services/shopify-bridge-service/ui ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY product-services/shopify-bridge-service/pom.xml ./
COPY product-services/shopify-bridge-service/src ./src

RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/shopify-bridge-service-*.jar /app/shopify-bridge-service.jar
COPY --from=ui-build /workspace/ui/dist /app/ui

ENV SHOPIFY_BRIDGE_MERCHANT_UI_DIRECTORY=/app/ui

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/shopify-bridge-service.jar"]
