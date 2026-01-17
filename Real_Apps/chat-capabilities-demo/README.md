# Chat Capabilities Demo (AI Fabric)

This Real App demonstrates **chat-session** capabilities:
- Conversation context enrichment (multi-turn)
- Turn recording (with optional PII redaction if a detector is configured)
- Conversation lifecycle APIs (get/list/delete)

## Run

```bash
mvn -f ai-infrastructure-module/pom.xml -DskipTests install
mvn -f Real_Apps/chat-capabilities-demo/pom.xml spring-boot:run
```

Set an LLM provider (example OpenAI):
```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY="..."
```

Then open `requests/demo.http`.

