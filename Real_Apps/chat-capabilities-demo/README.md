# Chat Capabilities Demo (AI Fabric)

This Real App demonstrates:
- **Chat sessions**: multi-turn context + turn recording + conversation lifecycle APIs (get/list/delete)
- **Product catalog + RAG**: CRUD products, index into **Lucene**, and retrieve via chat (OpenAI embeddings + LLM)
- **Actions**: `create_purchase_order` action executed from chat

## Run

```bash
mvn -f ai-infrastructure-module/pom.xml -DskipTests install
mvn -f Real_Apps/chat-capabilities-demo/pom.xml spring-boot:run
```

## API Docs (Swagger)

- Swagger UI: `http://localhost:8096/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8096/v3/api-docs`

## OpenAI Setup

This app expects OpenAI for **LLM + embeddings** (required for RAG + intent extraction + actions).

```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY="..."
export OPENAI_MODEL="gpt-4o-mini"                         # optional
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"     # optional
```

Then open `requests/demo.http`.
