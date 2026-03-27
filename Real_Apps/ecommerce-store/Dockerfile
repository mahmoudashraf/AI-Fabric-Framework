# Docker deployment (build from repo root).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /repo

COPY Real_Apps/ecommerce-store/ Real_Apps/ecommerce-store/

RUN mvn -f Real_Apps/ecommerce-store/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache su-exec && addgroup -S spring && adduser -S spring -G spring

COPY --from=build /repo/Real_Apps/ecommerce-store/target/*.jar /app/app.jar
COPY Real_Apps/ecommerce-store/docker-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh && mkdir -p /app/data && chown -R spring:spring /app

EXPOSE 8096
ENV PORT=8096
ENTRYPOINT ["/app/entrypoint.sh"]
