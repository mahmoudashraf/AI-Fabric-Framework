# 🧵 AI Fabric Framework

> **Weaving Intelligence into the Very Core of Your Enterprise Applications.**

AI Fabric Framework is a high-performance, security-first AI enablement solution for Spring Boot. It doesn't just "add" AI; it weaves it into your existing data models, security policies, and business logic with surgical precision.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/#java21)
[![License](https://img.shields.io/badge/License-Enterprise-blue.svg)](#)

---

## 🎨 The Weaver's Vision

Most AI integrations are "patches"—clunky, external scripts that sit on top of your app. **AI Fabric** is different. It's the loom that takes your raw data (The Warp) and weaves it with semantic intelligence (The Weft) to create a seamless, AI-capable tapestry.

### ✨ Key Patterns in the Fabric

*   **🛡️ Security-First Warp**: PII detection, redaction, and hook-based access control are not afterthoughts—they are the foundation.
*   **⚡ High-Velocity Weave**: Asynchronous indexing and local ONNX embedding generation ensure your application stays lightning-fast.
*   **🔌 Universal Threading**: Swap LLMs (OpenAI, Anthropic, Azure) and Vector DBs (Lucene, Qdrant, Pinecone) without changing a single line of business code.
*   **🔄 Live-Woven Sync**: Automatic, real-time synchronization between your database and vector indices. When your data changes, your AI knows immediately.
*   **🎯 Semantic Precision**: Hybrid retrieval (Dense + BM25) and multi-stage document processing for 90%+ recall accuracy.

---

## 🛠️ Stitching AI into Your App (in 5 Minutes)

### 1. The Foundation (Dependencies)
```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-fabric-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. Weaving Your Entities
Just add one annotation to make any JPA entity AI-capable.

```java
@Entity
@AICapable(entityType = "product") // This entity is now part of the AI Fabric
public class Product {
    @Id private UUID id;
    private String name;
    private String description; // Automatically indexed for semantic search
}
```

### 3. Configuring the Loom
Define how the fabric should be woven in your `application.yml`.

```yaml
ai:
  fabric:
    providers:
      llm: openai
      embedding: onnx # Run locally, $0 cost, zero latency
    vector-db:
      type: lucene   # Embedded, no infra to manage
    security:
      pii-detection:
        enabled: true
        mode: REDACT # Safety built-in
```

---

## 🧵 The Framework Layers

| Layer | The Metaphor | Purpose |
| :--- | :--- | :--- |
| **The Warp** | Infrastructure | Core services for embeddings, RAG, and indexing. |
| **The Weft** | Security | PII detection, audit trails, and compliance hooks. |
| **The Shuttle** | Orchestrator | Moving data between your DB, Vector Store, and LLM. |
| **The Pattern** | Configuration | YAML-based entity definitions and search weights. |
| **The Tailor** | Hooks | YOUR business logic for access control and compliance. |

---

## 🚀 Why AI Fabric?

### 🚫 The Old Way (The Messy Knot)
*   Manual embedding logic in controllers.
*   Fragile PII checks scattered everywhere.
*   Hardcoded vendor APIs (Locked in!).
*   Blocking calls that slow down your users.

### ✅ The Fabric Way (The Masterpiece)
*   **Declarative AI**: Focus on features, not infrastructure.
*   **Real-time Synchronization**: Every `save()`, `update()`, or `delete()` on your JPA entities triggers an automatic vector update. Your RAG results never lag behind your database.
*   **Zero-Trust AI**: Access control hooks ensure LLMs only see what the user is allowed to see.
*   **Vendor Agnostic**: Switch from OpenAI to local ONNX in 10 seconds.
*   **Scale Ready**: Built-in async queues and performance monitoring.

---

## 📚 Deep Dives

*   📖 **[The Weaver's Guide](./docs/USER_GUIDE.md)**: Master entity indexing and semantic search.
*   🛡️ **[The Security Pattern](./docs/privacy/implementation/README.md)**: HIPAA, GDPR, and SOC2 compliance.
*   🔌 **[Universal Adapters](./docs/VECTOR_DATABASE_ABSTRACTION.md)**: Connect to any Vector DB or LLM.
*   🏛️ **[Architectural Blueprint](./PROJECT_OVERVIEW_TECHNICAL.md)**: Deep dive into the AOP and async engine.

---

## 🤝 Join the Weave

The AI Fabric is designed to be extensible. Want to add a custom Vector DB or a new PII detection algorithm? Our hook-based architecture makes it simple.

> "Stop building AI infrastructure. Start weaving AI features."

## ⚖️ License

The AI Fabric Framework is dual-licensed:
- **Community Core**: [Apache License 2.0](./LICENSE) (Free forever)
- **Enterprise Edition**: Commercial License for advanced security, compliance, and cloud-scale features.

See [LICENSE](./LICENSE) for full details and feature comparison.

---

&copy; 2025 AI Fabric Framework Team. Built with ❤️ for the Spring Boot Community.
