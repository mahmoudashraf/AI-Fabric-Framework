# Subscription Management Hub (Simple)

**Use case:** Subscription Management Hub (SaaS subscriptions)  
**Setup scenario:** Minimal integration (configuration-driven, minimal AI annotations)

## What This App Demonstrates

- A “greenfield” Spring Boot app that integrates AI Fabric with a single entry point: `@EnableAIInfrastructure`.
- AI entity behavior defined in `src/main/resources/ai-entity-config.yml` (no entity-level AI annotations required).
- Local-first defaults: H2 + Lucene vector DB + ONNX embeddings.
- A realistic domain: users, subscription plans, subscriptions, and seeded sample data.

## Key Integration Points

- Main entry point: `src/main/java/com/subscription/hub/SubscriptionManagementHubApplication.java`
- AI opt-in: `@EnableAIInfrastructure`
- AI entity config: `src/main/resources/ai-entity-config.yml`
- AI config wiring: `src/main/resources/application.yml` (`ai.config.default-file: ai-entity-config.yml`)

## Run

1) Install the framework artifacts locally:

`cd ../../ai-infrastructure-module && mvn -DskipTests install`

2) Build + run the app:

`mvn -DskipTests package && java -jar target/*.jar`

### Optional: enable real LLM calls (Cohere)

By default, Cohere is disabled (`COHERE_ENABLED=false`). To enable:

`export COHERE_ENABLED=true`
`export COHERE_API_KEY=...`

## What To Try

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL is in `application.yml`)
- Get a test user:
  - `POST /api/auth/guest/login`
- Browse plans:
  - `GET /api/subscriptions/plans`
- Create a subscription:
  - `POST /api/subscriptions/subscribe?userId=1&planId=<uuid>&billingCycle=MONTHLY`
- Plan search endpoint:
  - `POST /api/subscriptions/plans/search?query=pro&limit=10`

## Data + Storage

- DB: `jdbc:h2:file:./data/subscriptiondb`
- Lucene index: `./data/lucene-vector-index`
- Seed data runs on startup (plans + users + subscriptions).
- Startup also indexes the 3 subscription plans for semantic search (`entityType=subscription-plan`).
