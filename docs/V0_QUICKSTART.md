# v0 Quickstart — Chat Capabilities Demo (Golden Path)

This quickstart is the **developer-friendly v0 path** to run AI Fabric end-to-end using the reference app:
- `Real_Apps/chat-capabilities-demo`

It is intentionally narrow:
- **LLM + embeddings:** OpenAI
- **Vector DB:** Lucene (embedded)

If you want productization-specific components (connector/relay/ingestion), start here first, then follow:
- `changes/Productization/PRODUCTIZATION_IMPLEMENTATION_PLAN.md`

---

## Prerequisites

- Java **21**
- Maven 3.9+
- An OpenAI API key

Environment variables (minimum):

```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY="..."
```

Optional (recommended for Lucene):

```bash
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
export OPENAI_EMBEDDING_DIMENSIONS="512"
```

---

## 1) Build the framework

From repo root:

```bash
mvn -f ai-infrastructure-module/pom.xml -DskipTests install
```

---

## 2) Run the demo app

```bash
mvn -f Real_Apps/chat-capabilities-demo/pom.xml spring-boot:run
```

The demo runs on:
- `http://localhost:8096`

Swagger:
- `http://localhost:8096/swagger-ui/index.html`

---

## 3) Run the “golden path” requests

Open:
- `Real_Apps/chat-capabilities-demo/requests/demo.http`

Execute in order:
1) Create products
2) Search products (vector search)
3) Chat over the catalog (RAG)
4) Execute an action from chat (purchase order)
5) Inspect stored conversation and orders

---

## Notes (v0 scope)

- v0 is a **public preview**: the goal is “easy to run and understand”, not “every module supported”.
- The demo is **not** a marketplace product; it is evidence and a reference app for building AI apps on AI Fabric.

