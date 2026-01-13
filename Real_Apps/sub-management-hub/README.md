# Subscription Management Hub (Advanced)

**Use case:** Subscription Management Hub (SaaS subscriptions)  
**Setup scenario:** Annotation-driven integration (shows AI Fabric annotations + indexing hooks)

## What This App Demonstrates

- Same realistic domain as the simple app (plans + subscriptions), but with “explicit” AI integration:
  - Entity annotations: `@AICapable`
  - Field annotations: `@AISearchable`, `@AIContext`
  - Service hooks: `@AIProcess` to keep search/index data synced with writes
- Local-first defaults (ONNX + Lucene + H2) while allowing a real LLM provider to be enabled via env vars.

## Key Integration Points

- Main entry point: `src/main/java/com/subscription/hub/SubscriptionManagementHubApplication.java`
- AI opt-in: `@EnableAIInfrastructure`
- AI entity config: `src/main/resources/ai-entity-config.yml`
- AI config wiring: `src/main/resources/application.yml`
- Example `@AIProcess` usage: `src/main/java/com/subscription/hub/service/SubscriptionService.java`
- Example `@AICapable` usage: `src/main/java/com/subscription/hub/entity/SubscriptionPlan.java`

## Run

1) Install the framework artifacts locally:

`cd ../../ai-infrastructure-module && mvn -DskipTests install`

2) Build + run the app:

`mvn -DskipTests package && java -jar target/*.jar`

The app runs on port `8081` by default (see `application.yml`).

### Optional: enable real LLM calls (Cohere)

By default, Cohere is disabled (`COHERE_ENABLED=false`). To enable:

`export COHERE_ENABLED=true`
`export COHERE_API_KEY=...`

## What To Try

- Get a test user:
  - `POST http://localhost:8081/api/auth/guest/login`
- Browse plans:
  - `GET http://localhost:8081/api/subscriptions/plans`
- Create a subscription (writes are hooked via `@AIProcess`):
  - `POST "http://localhost:8081/api/subscriptions/subscribe?userId=1&planId=<uuid>&billingCycle=MONTHLY"`
- Semantic plan search (uses `AICoreService.performSearch`):
  - `POST "http://localhost:8081/api/subscriptions/plans/search?query=enterprise&limit=10"`

## Data + Storage

- DB: `jdbc:h2:file:./data/subscriptiondb`
- Lucene index: `./data/lucene-vector-index`
- Seed data runs on startup (plans + users + subscriptions).
- Startup also indexes the 3 subscription plans for semantic search (`entityType=subscription-plan`).
