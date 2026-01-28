# AI Fabric Framework: Technical Plan for "AI Orchestra" Model

## Code-Validated Assessment

Based on deep code analysis, here's what the framework actually does:

---

## Part 1: Current Architecture (What the Code Shows)

### 1.1 Framework-Owned Tables (7 Tables)

The framework currently creates these tables in the customer's database:

| Table | Purpose | Required? | Can Externalize? |
|-------|---------|-----------|------------------|
| `ai_infrastructure_profiles` | AI-generated user profiles | No | Yes |
| `intent_history` | Query/intent logging | No | Yes |
| `chat_sessions` + `chat_turns` | Conversation state | No | Yes |
| `ai_behavior_insights` | Behavioral analytics | No | Yes |
| `ai_indexing_queue` | Async indexing work queue | Only if indexing | Yes |
| `ai_index_catalog` | Index metadata tracking | Only if indexing | Yes |
| `ai_migration_jobs` | Data migration tracking | Only if migration | Yes |

**Key Finding**: ALL framework tables are OPTIONAL based on which modules are enabled.

### 1.2 Vector Database Abstraction (Already Clean)

```java
// VectorDatabaseService.java - Already a clean interface
public interface VectorDatabaseService {
    String storeVector(String entityType, String entityId, String content,
                      List<Double> embedding, Map<String, Object> metadata);
    AISearchResponse search(List<Double> queryVector, AISearchRequest request);
    boolean removeVector(String entityType, String entityId);
    // ... etc
}
```

**Customer CAN already**:
- Bring their own Qdrant/Pinecone/Milvus/Weaviate instance
- Implement custom `VectorDatabaseService`
- Use embedded Lucene (default)

### 1.3 Orchestration WITHOUT Vectors (Already Supported)

The orchestrator can work in **three modes**:

| Mode | Requires Vectors? | Use Case |
|------|-------------------|----------|
| **Actions Only** | NO | Pure action execution (add to cart, cancel order) |
| **LLM-Direct** | NO | LLM answers directly without retrieval |
| **RAG** | YES | Semantic search + generation |

```yaml
# Configuration for actions-only mode
ai:
  service:
    features:
      enable-embeddings: false
      enable-search: false
  # No vector-db configured
```

### 1.4 Module Independence (Already Modular)

| Module | Default State | Dependencies |
|--------|---------------|--------------|
| Core | Always ON | LLM provider |
| Indexing | ON (can disable) | Vector DB + Embeddings |
| RAG | ON (can disable) | Vector DB + Embeddings |
| Chat Sessions | **OFF** | Database |
| Behavior | **OFF** | Database |
| PII | **OFF** | None |
| Governance | **OFF** | Optional Vector DB |
| Migration | ON (can disable) | Indexing |

---

## Part 2: The "AI Orchestra" Vision

### 2.1 New Architecture Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CUSTOMER'S INFRASTRUCTURE                            │
│                                                                              │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐      │
│  │  Customer's App  │    │  Customer's DB   │    │  Customer's      │      │
│  │  (Any Platform)  │    │  (PostgreSQL,    │    │  Vector DB       │      │
│  │                  │    │   MySQL, etc)    │    │  (Qdrant, etc)   │      │
│  └────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘      │
│           │                       │                       │                 │
└───────────┼───────────────────────┼───────────────────────┼─────────────────┘
            │                       │                       │
            │                       │                       │
┌───────────┼───────────────────────┼───────────────────────┼─────────────────┐
│           ▼                       ▼                       ▼                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     AI FABRIC ORCHESTRATION LAYER                    │   │
│  │                                                                      │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │   │
│  │  │   Intent    │  │   Action    │  │    RAG      │  │  Security   │ │   │
│  │  │  Extraction │  │  Execution  │  │  Provider   │  │   Layer     │ │   │
│  │  │    (LLM)    │  │  Framework  │  │  (Optional) │  │             │ │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘ │   │
│  │                                                                      │   │
│  │  CONNECTS TO (Customer Provides):                                    │   │
│  │  • LLM API Keys (OpenAI, Anthropic, etc.)                           │   │
│  │  • Database Connection (optional - for metadata)                     │   │
│  │  • Vector DB Connection (optional - for RAG)                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│                        AI FABRIC (Your Offering)                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 What AI Fabric IS (The Orchestra)

**Core Value**: Intent Understanding + Action Orchestration + Optional RAG

| Layer | Provides | Requires |
|-------|----------|----------|
| **Orchestration** | Pipeline execution, intent routing | LLM API key |
| **Intent Extraction** | Understanding user queries | LLM API key |
| **Action Framework** | Execute business logic with confirmations | Customer's action handlers |
| **Security** | Threat detection, rate limiting, PII | Nothing external |
| **RAG (Optional)** | Semantic search + generation | Vector DB connection |
| **Indexing (Optional)** | Sync relational → vector | DB + Vector DB connections |

### 2.3 What Customer Provides

| Component | Who Owns | Notes |
|-----------|----------|-------|
| Relational Database | Customer | Their schema, their data |
| Vector Database | Customer OR Managed | AI Fabric can optionally manage |
| LLM API Keys | Customer | They control costs |
| Business Logic | Customer | Action handlers |
| Entities/Data | Customer | Their domain model |

---

## Part 3: Technical Changes Required

### 3.1 Changes to Make Framework "Connection-Based"

Currently the framework auto-configures with embedded resources. We need to make ALL external connections explicit and optional.

#### Change 1: Make Framework Metadata Storage Pluggable

Create new SPI for metadata storage:

```java
// New interface - customers can implement or use defaults
public interface OrchestrationMetadataStore {
    // Intent history
    void saveIntentHistory(IntentHistoryRecord record);
    List<IntentHistoryRecord> getRecentHistory(String userId, int limit);

    // Optional - can return empty if not tracking
    default void saveIntentHistory(IntentHistoryRecord record) {}
    default List<IntentHistoryRecord> getRecentHistory(String userId, int limit) {
        return Collections.emptyList();
    }
}

// Default implementation uses JPA (current behavior)
@ConditionalOnProperty(prefix = "ai.metadata", name = "storage", havingValue = "jpa", matchIfMissing = true)
public class JpaOrchestrationMetadataStore implements OrchestrationMetadataStore {
    // Uses existing IntentHistoryRepository
}

// In-memory implementation (no database needed)
@ConditionalOnProperty(prefix = "ai.metadata", name = "storage", havingValue = "memory")
public class InMemoryOrchestrationMetadataStore implements OrchestrationMetadataStore {
    private final Map<String, List<IntentHistoryRecord>> history = new ConcurrentHashMap<>();
}

// No-op implementation (disabled)
@ConditionalOnProperty(prefix = "ai.metadata", name = "storage", havingValue = "none")
public class NoOpOrchestrationMetadataStore implements OrchestrationMetadataStore {
    // All methods do nothing - pure stateless orchestration
}
```

#### Change 2: Externalize Database Connection

New configuration model:

```yaml
ai:
  # Metadata storage configuration
  metadata:
    storage: none  # none | memory | jpa | external

    # If storage=jpa, use this datasource (can be different from customer's)
    datasource:
      url: ${AI_FABRIC_DB_URL:}
      username: ${AI_FABRIC_DB_USER:}
      password: ${AI_FABRIC_DB_PASS:}

    # If storage=external, customer implements OrchestrationMetadataStore

  # Vector database configuration
  vector-db:
    enabled: false  # Customer must explicitly enable
    type: none  # none | lucene | qdrant | pinecone | milvus | weaviate | custom

    # Connection provided by customer
    qdrant:
      host: ${CUSTOMER_QDRANT_HOST:}
      port: ${CUSTOMER_QDRANT_PORT:6334}
      api-key: ${CUSTOMER_QDRANT_API_KEY:}

  # LLM configuration (customer provides keys)
  providers:
    llm-provider: openai
    openai:
      api-key: ${CUSTOMER_OPENAI_API_KEY}  # Required
```

#### Change 3: Create "Stateless Orchestration" Mode

```yaml
ai:
  mode: stateless  # stateless | stateful

  # Stateless mode:
  # - No database tables created
  # - No intent history stored
  # - No chat sessions
  # - Pure request/response orchestration

  # Stateful mode (current behavior):
  # - Creates metadata tables
  # - Stores history, sessions, etc.
```

#### Change 4: Indexing as Separate Service

Currently indexing is tightly coupled. Make it a standalone service:

```java
// IndexingSyncService - can run independently
public interface IndexingSyncService {
    void syncEntity(String entityType, String entityId, Map<String, Object> data);
    void removeEntity(String entityType, String entityId);
    void fullSync(String entityType, Supplier<Stream<Map<String, Object>>> dataProvider);
    SyncStatus getStatus(String jobId);
}

// Customer calls this explicitly - not via AOP
@RestController
@RequestMapping("/api/sync")
public class IndexingSyncController {

    @PostMapping("/entity")
    public SyncResult syncEntity(@RequestBody SyncRequest request) {
        // Customer explicitly triggers sync
    }

    @PostMapping("/full")
    public SyncJob startFullSync(@RequestBody FullSyncRequest request) {
        // Customer triggers full migration
    }
}
```

### 3.2 New Module Structure

Reorganize modules for clarity:

```
ai-fabric-sdk/
├── ai-fabric-core/                    # Pure orchestration (no DB required)
│   ├── orchestration/                 # Pipeline, intent handling
│   ├── intent/                        # Intent extraction, types
│   ├── action/                        # Action framework
│   └── security/                      # Security analysis
│
├── ai-fabric-rag/                     # RAG capabilities (requires vector DB)
│   ├── search/                        # Semantic search
│   ├── generation/                    # Answer generation
│   └── providers/                     # RAG provider SPI
│
├── ai-fabric-sync/                    # Data synchronization (optional)
│   ├── indexing/                      # Entity → Vector sync
│   ├── migration/                     # Bulk migration
│   └── queue/                         # Work queue (requires DB)
│
├── ai-fabric-connectors/              # Database connectors
│   ├── connector-qdrant/
│   ├── connector-pinecone/
│   ├── connector-milvus/
│   ├── connector-weaviate/
│   └── connector-custom/              # SPI for custom connectors
│
├── ai-fabric-metadata/                # Metadata storage (optional)
│   ├── storage-jpa/                   # JPA implementation
│   ├── storage-redis/                 # Redis implementation
│   └── storage-memory/                # In-memory
│
└── ai-fabric-providers/               # LLM providers
    ├── provider-openai/
    ├── provider-anthropic/
    ├── provider-azure/
    └── provider-onnx/
```

---

## Part 4: Deployment Models

### 4.1 Model A: SDK Integration (Customer Self-Hosts)

Customer embeds AI Fabric SDK in their application:

```xml
<!-- Customer's pom.xml -->
<dependency>
    <groupId>com.ai-fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Optional: RAG capabilities -->
<dependency>
    <groupId>com.ai-fabric</groupId>
    <artifactId>ai-fabric-rag</artifactId>
</dependency>

<!-- Optional: Qdrant connector -->
<dependency>
    <groupId>com.ai-fabric</groupId>
    <artifactId>ai-fabric-connector-qdrant</artifactId>
</dependency>
```

Customer configures connections:

```yaml
# Customer's application.yml
ai:
  mode: stateless
  providers:
    openai:
      api-key: ${OPENAI_API_KEY}
  vector-db:
    type: qdrant
    qdrant:
      host: customer-qdrant.example.com
      api-key: ${QDRANT_API_KEY}
```

**Revenue Model**: License fee + support

### 4.2 Model B: Managed Orchestration Service (AI Fabric Hosts)

Customer connects to AI Fabric cloud for orchestration:

```
Customer App → AI Fabric API → Customer's Vector DB
                    ↓
              Customer's LLM (via their API key)
```

Customer registers their connections:

```bash
# Register customer's infrastructure
curl -X POST https://api.ai-fabric.dev/v1/tenants/acme/connections \
  -H "Authorization: Bearer $TENANT_API_KEY" \
  -d '{
    "llm": {
      "provider": "openai",
      "apiKey": "sk-..."
    },
    "vectorDb": {
      "type": "qdrant",
      "host": "acme-qdrant.cloud.qdrant.io",
      "apiKey": "..."
    }
  }'
```

Customer calls AI Fabric for orchestration:

```bash
# Orchestrate a query
curl -X POST https://api.ai-fabric.dev/v1/orchestrate \
  -H "Authorization: Bearer $TENANT_API_KEY" \
  -d '{
    "query": "Cancel my subscription",
    "userId": "user-123",
    "context": {...}
  }'
```

**Revenue Model**: Usage-based pricing (per orchestration call)

### 4.3 Model C: Fully Managed (AI Fabric Provides Everything)

AI Fabric provisions and manages all infrastructure:

```
Customer App → AI Fabric API → AI Fabric Managed Vector DB
                    ↓
              AI Fabric Managed LLM Pool
```

**Revenue Model**: Higher margin, full SaaS pricing

---

## Part 5: Connector Architecture

### 5.1 Vector Database Connector SPI

```java
// Connector interface - customer can implement for custom DBs
public interface VectorDatabaseConnector {

    // Connection lifecycle
    void connect(ConnectionConfig config);
    void disconnect();
    boolean isConnected();
    HealthStatus checkHealth();

    // Vector operations
    String upsertVector(VectorRecord record);
    List<SearchResult> search(SearchQuery query);
    void deleteVector(String id);

    // Batch operations
    BatchResult batchUpsert(List<VectorRecord> records);
    BatchResult batchDelete(List<String> ids);

    // Metadata
    ConnectorCapabilities getCapabilities();
    Map<String, Object> getStatistics();
}

// Capabilities declaration
public interface ConnectorCapabilities {
    boolean supportsFiltering();
    boolean supportsHybridSearch();
    boolean supportsBatchOperations();
    int maxBatchSize();
    List<String> supportedMetricTypes();  // cosine, euclidean, dot
}
```

### 5.2 Relational Database Connector (For Indexing)

```java
// For sync service - reads from customer's relational DB
public interface RelationalDataSource {

    // Schema discovery
    List<TableMetadata> discoverTables();
    TableMetadata getTableMetadata(String tableName);

    // Data access
    Stream<Map<String, Object>> streamTable(String tableName, SyncOptions options);
    Map<String, Object> getRecord(String tableName, String id);

    // Change detection (optional)
    default Stream<ChangeEvent> getChanges(String tableName, Instant since) {
        throw new UnsupportedOperationException("Change tracking not supported");
    }
}

// Implementations
public class JdbcRelationalDataSource implements RelationalDataSource { ... }
public class JpaRelationalDataSource implements RelationalDataSource { ... }
```

### 5.3 LLM Provider Connector

```java
// Already exists, but formalize as connector pattern
public interface LLMConnector {

    // Connection
    void configure(LLMConfig config);
    boolean isAvailable();

    // Core operations
    GenerationResponse generate(GenerationRequest request);
    EmbeddingResponse embed(EmbeddingRequest request);

    // Streaming (optional)
    default Flux<GenerationChunk> streamGenerate(GenerationRequest request) {
        throw new UnsupportedOperationException("Streaming not supported");
    }

    // Capabilities
    LLMCapabilities getCapabilities();
}
```

---

## Part 6: Configuration Schema

### 6.1 Customer-Provided Configuration

```yaml
# ai-fabric-config.yml - Customer provides this

# Core orchestration (required)
orchestration:
  mode: stateless  # stateless | stateful
  information-mode: LLM_DRIVEN  # LLM_DRIVEN | DETERMINISTIC_RAG_GENERATE

# LLM Connection (required)
llm:
  provider: openai
  connection:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o
    timeout: 60s

# Vector DB Connection (optional - only if using RAG)
vector-db:
  enabled: true
  provider: qdrant
  connection:
    host: ${QDRANT_HOST}
    port: 6334
    api-key: ${QDRANT_API_KEY}
    collection-prefix: ${TENANT_ID}_

# Relational DB Connection (optional - only if using sync)
relational-db:
  enabled: false
  connection:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}

# Metadata Storage (optional)
metadata:
  storage: memory  # none | memory | external

# Features (opt-in)
features:
  rag: true
  indexing: false
  chat-sessions: false
  behavior-analytics: false
  pii-detection: true
```

### 6.2 Entity Sync Configuration (When Indexing Enabled)

```yaml
# ai-sync-config.yml - Defines what to sync

sync:
  entities:
    - name: product
      source:
        table: products
        id-column: id
      target:
        collection: products
      fields:
        - source: name
          weight: 1.0
          embed: false
        - source: description
          weight: 0.8
          embed: true
        - source: category
          weight: 0.5
          embed: false
      metadata:
        - source: price
        - source: stock
        - source: sku
      strategy:
        full-sync: daily
        incremental: on-change  # requires change tracking

    - name: faq
      source:
        table: faqs
        id-column: id
      target:
        collection: knowledge_base
      fields:
        - source: question
          embed: true
        - source: answer
          embed: true
```

---

## Part 7: API Design for Managed Service

### 7.1 Tenant Management API

```yaml
# POST /v1/tenants
# Create a new tenant
{
  "tenantId": "acme-corp",
  "name": "Acme Corporation",
  "plan": "growth"
}

# POST /v1/tenants/{tenantId}/connections/llm
# Configure LLM connection
{
  "provider": "openai",
  "apiKey": "sk-...",
  "model": "gpt-4o"
}

# POST /v1/tenants/{tenantId}/connections/vector-db
# Configure Vector DB connection
{
  "provider": "qdrant",
  "host": "acme.cloud.qdrant.io",
  "apiKey": "...",
  "collectionPrefix": "acme_"
}

# GET /v1/tenants/{tenantId}/health
# Check all connections
{
  "llm": { "status": "healthy", "latency": "120ms" },
  "vectorDb": { "status": "healthy", "latency": "45ms" }
}
```

### 7.2 Orchestration API

```yaml
# POST /v1/orchestrate
# Main orchestration endpoint
Request:
{
  "query": "I want to cancel my subscription",
  "userId": "user-123",
  "sessionId": "session-456",  # Optional
  "context": {
    "currentPage": "account-settings",
    "userTier": "premium"
  },
  "options": {
    "includeRag": true,
    "maxResults": 5
  }
}

Response:
{
  "requestId": "req-789",
  "type": "ACTION",
  "intent": {
    "action": "cancel_subscription",
    "confidence": 0.95,
    "params": {
      "userId": "user-123"
    }
  },
  "requiresConfirmation": true,
  "confirmationMessage": "Cancel your Premium subscription? You'll lose access on Feb 28.",
  "suggestedActions": ["apply_discount", "downgrade_plan"]
}

# POST /v1/orchestrate/confirm
# Confirm a pending action
{
  "requestId": "req-789",
  "confirmed": true
}
```

### 7.3 Sync API (For Indexing)

```yaml
# POST /v1/sync/entity
# Sync a single entity
{
  "entityType": "product",
  "entityId": "prod-123",
  "data": {
    "name": "Running Shoes",
    "description": "Lightweight running shoes...",
    "price": 79.99
  }
}

# POST /v1/sync/batch
# Batch sync
{
  "entityType": "product",
  "entities": [
    { "id": "prod-123", "data": {...} },
    { "id": "prod-124", "data": {...} }
  ]
}

# DELETE /v1/sync/entity/{entityType}/{entityId}
# Remove from index

# POST /v1/sync/full
# Start full sync job
{
  "entityType": "product",
  "source": {
    "type": "api",
    "endpoint": "https://acme.com/api/products",
    "batchSize": 100
  }
}
```

---

## Part 8: Implementation Roadmap

### Phase 1: Core Refactoring (2-3 weeks)

**Goal**: Make orchestration work without any database

| Task | Priority | Effort |
|------|----------|--------|
| Create `OrchestrationMetadataStore` SPI | High | 2 days |
| Implement `InMemoryMetadataStore` | High | 1 day |
| Implement `NoOpMetadataStore` | High | 0.5 day |
| Add `ai.mode: stateless` configuration | High | 1 day |
| Remove hard dependency on JPA in core module | High | 3 days |
| Create connector abstraction for Vector DB | High | 2 days |
| Create connector abstraction for LLM | Medium | 1 day |
| Write tests for stateless mode | High | 2 days |

### Phase 2: Module Separation (2-3 weeks)

**Goal**: Clean separation between core, RAG, and sync modules

| Task | Priority | Effort |
|------|----------|--------|
| Extract `ai-fabric-rag` module | High | 3 days |
| Extract `ai-fabric-sync` module | High | 3 days |
| Create connector modules per vector DB | Medium | 4 days |
| Update auto-configuration for new structure | High | 2 days |
| Create SDK packaging (core, rag, sync) | High | 2 days |
| Update documentation | Medium | 2 days |

### Phase 3: Managed Service Layer (3-4 weeks)

**Goal**: Cloud API for managed orchestration

| Task | Priority | Effort |
|------|----------|--------|
| Tenant management API | High | 3 days |
| Connection management (LLM, Vector DB) | High | 4 days |
| Orchestration API endpoint | High | 3 days |
| Sync API endpoint | Medium | 3 days |
| Multi-tenant request routing | High | 4 days |
| Usage metering middleware | High | 3 days |
| Admin dashboard (Retool) | Low | 2 days |

### Phase 4: Production Hardening (2-3 weeks)

**Goal**: Production-ready managed service

| Task | Priority | Effort |
|------|----------|--------|
| Connection pooling for customer DBs | High | 2 days |
| Credential encryption (Secrets Manager) | High | 2 days |
| Rate limiting per tenant | High | 2 days |
| Error handling and retry logic | High | 2 days |
| Monitoring and alerting | High | 3 days |
| Load testing | Medium | 2 days |
| Documentation and SDK guides | Medium | 3 days |

---

## Part 9: Pricing Models

### 9.1 SDK License (Self-Hosted)

| Tier | Price | Includes |
|------|-------|----------|
| Starter | Free | Core orchestration only |
| Pro | $499/month | Core + RAG + Sync + Support |
| Enterprise | Custom | All modules + SLA + Custom features |

### 9.2 Managed Service (AI Fabric Hosts Orchestration)

| Component | Price |
|-----------|-------|
| Orchestration calls | $0.001 per call |
| RAG queries | $0.002 per query |
| Sync operations | $0.0001 per entity |
| Monthly minimum | $99 |

### 9.3 Fully Managed (AI Fabric Provides Vector DB)

| Tier | Price | Includes |
|------|-------|----------|
| Starter | $149/month | 10K vectors, 10K orchestrations |
| Growth | $499/month | 100K vectors, 50K orchestrations |
| Scale | $1,499/month | 1M vectors, 200K orchestrations |

---

## Part 10: File Changes Summary

### Files to Create

```
ai-infrastructure-module/
├── ai-infrastructure-core/
│   └── src/main/java/com/ai/infrastructure/
│       ├── spi/
│       │   ├── MetadataStore.java              # NEW
│       │   ├── VectorDatabaseConnector.java    # NEW
│       │   └── LLMConnector.java               # NEW
│       └── metadata/
│           ├── InMemoryMetadataStore.java      # NEW
│           └── NoOpMetadataStore.java          # NEW
│
├── ai-infrastructure-connectors/               # NEW MODULE
│   ├── connector-qdrant/
│   ├── connector-pinecone/
│   ├── connector-milvus/
│   └── connector-weaviate/
│
└── ai-infrastructure-api/                      # NEW MODULE
    └── src/main/java/com/ai/infrastructure/api/
        ├── TenantController.java
        ├── OrchestrationController.java
        └── SyncController.java
```

### Files to Modify

```
ai-infrastructure-core/
├── pom.xml                                     # Remove JPA dependency
├── AIInfrastructureAutoConfiguration.java      # Add stateless mode
├── OrchestrationProperties.java                # Add mode config
└── IntentHandlingStep.java                     # Use MetadataStore SPI
```

---

## Summary

### What AI Fabric IS (The Orchestra)

1. **Intent Understanding** - LLM-powered query analysis
2. **Action Orchestration** - Execute business logic with confirmations
3. **RAG Provider** - Semantic search + generation (when connected to vector DB)
4. **Security Layer** - Threat detection, PII, access control
5. **Sync Service** - Optional relational → vector synchronization

### What AI Fabric IS NOT

1. **Not a database provider** - Customer brings their own
2. **Not an LLM provider** - Customer brings their API keys
3. **Not required infrastructure** - Pure orchestration can be stateless

### The Value Proposition

> "Bring your data. Bring your LLM. We orchestrate the intelligence."

AI Fabric is the middleware that turns any application into an AI-native application, without requiring customers to rebuild their infrastructure.

---

*Document Version: 1.0*
*Based on: Deep code analysis*
*Date: January 2026*
