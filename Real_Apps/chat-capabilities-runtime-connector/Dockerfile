FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /repo

COPY ai-infrastructure-module/ ai-infrastructure-module/
COPY Real_Apps/chat-capabilities-runtime-connector/ Real_Apps/chat-capabilities-runtime-connector/

RUN mvn -f ai-infrastructure-module/pom.xml -DskipTests install
RUN mvn -f Real_Apps/chat-capabilities-runtime-connector/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
RUN mkdir -p /app/data && chown -R spring:spring /app
USER spring:spring

COPY --from=build /repo/Real_Apps/chat-capabilities-runtime-connector/target/*.jar /app/app.jar
EXPOSE 8098
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/app.jar"]
