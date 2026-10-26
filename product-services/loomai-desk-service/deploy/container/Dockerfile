FROM node:22-bookworm-slim AS ui-build
WORKDIR /build/ui
COPY product-services/loomai-desk-service/ui/package.json product-services/loomai-desk-service/ui/package-lock.json ./
RUN npm ci
COPY product-services/loomai-desk-service/ui/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY product-services/loomai-desk-service/pom.xml .
COPY product-services/loomai-desk-service/src ./src
COPY --from=ui-build /build/ui/dist ./src/main/resources/static/
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 desk
WORKDIR /app
COPY --from=build /build/target/loomai-desk-service-*.jar /app/desk.jar
USER 10001
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/desk.jar"]
