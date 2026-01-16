# Railway / Docker deployment (build from repo root).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /repo

COPY ai-infrastructure-module/ ai-infrastructure-module/
COPY Real_Apps/migration-enabled-product-catalog/ Real_Apps/migration-enabled-product-catalog/

RUN mvn -f ai-infrastructure-module/pom.xml -DskipTests install
RUN mvn -f Real_Apps/migration-enabled-product-catalog/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /repo/Real_Apps/migration-enabled-product-catalog/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/app.jar"]

