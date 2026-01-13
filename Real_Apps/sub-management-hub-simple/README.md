# Subscription Management Hub (Simple)

**Scenario:** Minimal, configuration-driven AI Fabric integration (keep app code free of AI annotations).

## Setup

- Spring Boot `3.2.x`, Java `21`
- Database: H2 file (`./data/subscriptiondb`)
- AI: ONNX embeddings + Lucene vector DB
- AI config file: `src/main/resources/ai-entity-config.yml`

## Build + Run

1) Install the framework artifacts:

`cd ../../ai-infrastructure-module && mvn -DskipTests install`

2) Run the app (port `8080`):

`cd ../Real_Apps/sub-management-hub-simple && mvn -DskipTests clean package && java -jar target/*.jar`

## Validate Indexing + Search (Debug Endpoints)

- Check what AI/indexing beans are active:
  - `GET http://localhost:8080/api/ai/debug/indexing/components`
- Reindex seeded subscription plans (sync):
  - `POST http://localhost:8080/api/ai/debug/indexing/reindex/plans?mode=sync`
- Query plans via vector search:
  - `GET http://localhost:8080/api/ai/debug/indexing/search/plans?q=premium&limit=5`
- End-to-end demo (mutate + index + search):
  - `POST http://localhost:8080/api/ai/debug/indexing/demo?mode=sync`

### Queue-based (Async) Validation

- Enqueue indexing:
  - `POST http://localhost:8080/api/ai/debug/indexing/reindex/plans?mode=async`
- Process one worker tick manually (if you don’t want to wait for scheduling):
  - `POST http://localhost:8080/api/ai/debug/indexing/queue/run-once?strategy=async`
- Queue status:
  - `GET http://localhost:8080/api/ai/debug/indexing/queue`
