# AI Infrastructure for Spring Boot

> **Transform your Spring Boot app into an AI-powered application in minutes, not months.**

Stop wrestling with LLM APIs, vector databases, and security guardrails. This framework gives you production-ready AI capabilities through simple annotations and configuration—no AI expertise required.

```xml
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## Why teams choose this framework

### ✨ Zero-code AI integration
Add one annotation to your entities. The framework handles embeddings, vector indexing, and semantic search automatically.

```java
@Entity
@AICapable(entityType = "product")  // ← That's it
public class Product {
    String description;
}
```

Your products are now searchable by meaning, not just keywords.

### 🚀 Production-ready from day one
Built-in security, PII detection, access control, audit logging, and compliance hooks. Everything enterprises need, nothing they don't.

### 🔌 Vendor freedom
Switch between OpenAI, Azure, Anthropic, or local models with one config change. Same for vector databases—Lucene, Pinecone, Qdrant, Weaviate. Your code never changes.

### ⚡ Performance that scales
Async indexing by default. Your users get instant responses while AI processing happens in the background. No blocking, no delays.

### 🛡️ Security built-in, not bolted-on
PII detection, content filtering, and access control are part of the framework. You define the rules, we enforce them.

---

## See it in action

### Before: Manual AI integration (weeks of work)

```java
// Manually embed text
String embedding = callOpenAI(product.getDescription());

// Manually store vector
pinecone.upsert(embedding, product.getId());

// Manually implement search
List<Result> results = pinecone.query(userQuery);

// Manually check PII
if (containsCreditCard(userQuery)) { /* what now? */ }

// Manually implement access control
if (!canAccess(userId, document)) { /* log? audit? */ }
```

### After: This framework (5 minutes)

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    String description;
}
```

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx  # Local, no API costs
  pii-detection:
    enabled: true
    mode: REDACT
```

**Done.** Embeddings, indexing, search, PII protection, and audit logging—all automatic.

---

## What you get

### 🎯 Smart search that understands meaning

```java
@Autowired RAGService ragService;

// Natural language queries work out of the box
RAGResponse answer = ragService.performRAGQuery(
    RAGRequest.builder()
        .query("affordable laptops for students")
        .entityType("product")
        .build()
);
```

Finds products by semantic similarity, not keyword matching. "affordable" matches "budget-friendly," "students" matches "education."

### 🔒 Enterprise security guardrails

**PII detection and redaction:**
```java
// User asks: "My credit card is 4111-1111-1111-1111"
// Framework automatically redacts before sending to LLM
// LLM receives: "My credit card is ****-****-****-****"
```

**Access control with your rules:**
```java
@Bean
EntityAccessPolicy accessPolicy() {
    return (userId, entity) -> {
        // Your business logic here
        return userService.canAccess(userId, entity);
    };
}
```

Framework calls your hook, logs the decision, and enforces it. You focus on business rules, not infrastructure.

### 📊 RAG (Retrieval-Augmented Generation)

Give your LLM access to your data without hallucinations:

```java
// Automatically retrieves relevant context from your indexed entities
// Generates accurate answers based on YOUR data
RAGResponse response = ragService.performRAGQuery(
    RAGRequest.builder()
        .query("What's our return policy for electronics?")
        .entityType("policy")
        .build()
);
```

### 🔄 Async processing that doesn't block users

```java
@PostMapping
Product create(@RequestBody Product p) {
    return repo.save(p);  // Returns instantly
}
// Embedding generation happens in background
// User never waits
```

### 🌐 59 REST endpoints (optional)

Add `ai-infrastructure-web` for instant monitoring, audit logs, and compliance reports:

```xml
<dependency>
  <artifactId>ai-infrastructure-web</artifactId>
</dependency>
```

- `/api/ai/monitoring/*` - Health checks, metrics, provider status
- `/api/ai/audit/*` - Complete audit trail
- `/api/ai/compliance/*` - Compliance reports
- `/api/ai/security/*` - Security analysis

---

## Quick start (5 minutes)

### 1. Add dependency

```xml
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-core</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-provider-openai</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>com.ai.infrastructure</groupId>
  <artifactId>ai-infrastructure-vector-lucene</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. Configure (application.yml)

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx  # Local, no API costs
    openai:
      api-key: ${OPENAI_API_KEY}
  
  vector-db:
    type: lucene  # Or: pinecone, qdrant, weaviate
  
  pii-detection:
    enabled: true
    mode: REDACT
```

### 3. Annotate your entities

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id private UUID id;
    private String name;
    private String description;
}
```

### 4. Define what to index (ai-entity-config.yml)

```yaml
ai-entities:
  product:
    searchable-fields:
      - name: description
        weight: 1.0
    embeddable-fields:
      - name: description
```

### 5. Use it

```java
// Entities are automatically indexed when saved
productRepo.save(new Product("Gaming Laptop", "High-performance..."));

// Search by meaning
RAGResponse answer = ragService.performRAGQuery(
    RAGRequest.builder()
        .query("powerful computers for gaming")
        .entityType("product")
        .build()
);
```

---

## Real-world use cases

### 🛍️ E-commerce: Semantic product search
"Show me comfortable shoes for running" finds products even if they don't mention "comfortable" or "running" explicitly.

### 📚 Knowledge bases: Intelligent Q&A
"What's our vacation policy?" retrieves relevant policy documents and generates accurate answers.

### 🏥 Healthcare: HIPAA-compliant document search
Built-in PII detection and access control ensure compliance while enabling semantic search across medical records.

### 💼 Enterprise: Multi-tenant SaaS
Hook-based access control makes tenant isolation simple. You define the rules, framework enforces them.

### 🔍 Customer support: Context-aware responses
RAG gives support agents instant access to relevant documentation, past tickets, and product information.

---

## Why it's different

### Other solutions make you choose:
- ❌ **LangChain:** Python-only, no Spring Boot integration
- ❌ **OpenAI API:** Vendor lock-in, manual security, no indexing
- ❌ **Vector DB SDKs:** Just storage, you build everything else
- ❌ **Build it yourself:** Months of work, security vulnerabilities, maintenance burden

### This framework gives you everything:
- ✅ **Spring Boot native** - Annotations, auto-configuration, dependency injection
- ✅ **Vendor agnostic** - Swap providers without code changes
- ✅ **Security first** - PII detection, access control, audit logging built-in
- ✅ **Production ready** - Async processing, error handling, monitoring
- ✅ **Extensible** - Hook-based architecture for custom business logic

---

## Architecture highlights

### Annotation-driven
No boilerplate. Add `@AICapable` to entities, framework handles the rest.

### Hook-based business logic
You provide business rules via simple interfaces. Framework provides infrastructure.

```java
@Bean
EntityAccessPolicy accessPolicy() {
    return (userId, entity) -> checkYourRules(userId, entity);
}
```

### Provider abstraction
One config change swaps entire backend:
```yaml
ai:
  providers:
    llm-provider: azure      # Was: openai
  vector-db:
    type: pinecone           # Was: lucene
```

### Async-first
Background workers handle expensive operations. Users never wait.

---

## Enterprise features

### 🔐 Security & Compliance
- PII detection with configurable patterns
- Content filtering and sanitization
- Access control with audit trails
- GDPR/CCPA deletion workflows
- Compliance check hooks

### 📊 Monitoring & Observability
- Health checks for all providers
- Performance metrics
- Audit logs for all AI operations
- Provider status dashboard

### 🎛️ Flexible Configuration
- YAML-based entity configuration
- Environment-specific settings
- Feature flags
- Retention policies

### 🔧 Extensibility
- Custom providers
- Custom vector databases
- Custom access policies
- Custom compliance rules
- Intent-based action handlers

---

## Get started

### 📖 Documentation
- **Quick start guide:** `ai-infrastructure-module/docs/USER_GUIDE.md`
- **Architecture deep dive:** `docs/PROJECT_OVERVIEW_TECHNICAL.md`
- **Security implementation:** `docs/privacy/implementation/README.md`
- **Integration examples:** `docs/AI_Enablment_Guide/README.md`

### 🚀 Next steps
1. Add the dependency
2. Configure your provider
3. Annotate one entity
4. See it work

### 💬 Support
- **Issues:** GitHub Issues
- **Discussions:** GitHub Discussions
- **Docs:** Full documentation in `/docs`

---

## License

TBD

---

**Built for Spring Boot developers who want AI capabilities without the complexity.**

Stop building infrastructure. Start building features.
