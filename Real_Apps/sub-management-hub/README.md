# Subscription Management Hub (Advanced)

**Scenario:** “Explicit” integration example (shows optional annotations) while still keeping `ai-entity-config.yml` as the primary source of truth.

## Setup

- Spring Boot `3.2.x`, Java `21`
- Database: H2 file (`./data/subscriptiondb`)
- AI: ONNX embeddings + Lucene vector DB
- AI config file: `src/main/resources/ai-entity-config.yml`
- Runs on port `8081` (see `src/main/resources/application.yml`)

## Build + Run

1) Install the framework artifacts:

`cd ../../ai-infrastructure-module && mvn -DskipTests install`

2) Run the app:

`cd ../Real_Apps/sub-management-hub && mvn -DskipTests clean package && java -jar target/*.jar`

## Validate Indexing + Search (Debug Endpoints)

- Check what AI/indexing beans are active:
  - `GET http://localhost:8081/api/ai/debug/indexing/components`
- Reindex seeded subscription plans (sync):
  - `POST http://localhost:8081/api/ai/debug/indexing/reindex/plans?mode=sync`
- Query plans via vector search:
  - `GET http://localhost:8081/api/ai/debug/indexing/search/plans?q=enterprise&limit=5`
- End-to-end demo (mutate + index + search):
  - `POST http://localhost:8081/api/ai/debug/indexing/demo?mode=sync`

### Queue-based (Async) Validation

- Enqueue indexing:
  - `POST http://localhost:8081/api/ai/debug/indexing/reindex/plans?mode=async`
- Process one worker tick manually:
  - `POST http://localhost:8081/api/ai/debug/indexing/queue/run-once?strategy=async`
- Queue status:
  - `GET http://localhost:8081/api/ai/debug/indexing/queue`
