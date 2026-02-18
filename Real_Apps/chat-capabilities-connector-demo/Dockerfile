# Docker deployment (build from repo root).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /repo

COPY Real_Apps/chat-capabilities-connector-demo/ Real_Apps/chat-capabilities-connector-demo/

RUN mvn -f Real_Apps/chat-capabilities-connector-demo/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /repo/Real_Apps/chat-capabilities-connector-demo/target/*.jar /app/app.jar

RUN mkdir -p /app/data && chown -R spring:spring /app
USER spring:spring

EXPOSE 8096
ENV PORT=8096
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/app.jar"]
