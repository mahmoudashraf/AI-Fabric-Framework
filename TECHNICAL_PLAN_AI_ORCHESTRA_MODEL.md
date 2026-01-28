# AI Fabric Framework: Technical Plan for "AI Orchestra" Model

## Code-Validated Assessment

Based on deep code analysis, here's what the framework actually does:

---

## Part 1: Current Architecture (What the Code Shows)

### 1.1 Framework-Owned Tables (7 Tables)

The framework creates these tables for **its own internal operations**:

| Table | Purpose | Module |
|-------|---------|--------|
| `ai_infrastructure_profiles` | AI-generated user profiles | Profiles |
| `intent_history` | Query/intent logging | Core |
| `chat_sessions` + `chat_turns` | Conversation state | Chat |
| `ai_behavior_insights` | Behavioral analytics | Behavior |
| `ai_indexing_queue` | Async indexing work queue | Indexing |
| `ai_index_catalog` | Index metadata tracking | Indexing |
| `ai_migration_jobs` | Data migration tracking | Migration |

**Key Architecture Decision**: AI Fabric **PROVIDES** an internal database for each customer deployment to store this framework metadata. This is SEPARATE from the customer's business database.

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

### 2.1 Deployment Architecture

Each customer gets an **isolated deployment** with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CUSTOMER DEPLOYMENT (Isolated)                         │
│                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                         AI FABRIC SYSTEM (We Provide)                       │ │
│  │                                                                             │ │
│  │  ┌─────────────────────────────────────────────────────────────────────┐   │ │
│  │  │                    AI Fabric Internal Database                       │   │ │
│  │  │                    (PostgreSQL - We Manage)                          │   │ │
│  │  │                                                                      │   │ │
│  │  │  • ai_infrastructure_profiles   • intent_history                    │   │ │
│  │  │  • chat_sessions + chat_turns   • ai_behavior_insights              │   │ │
│  │  │  • ai_indexing_queue           • ai_index_catalog                   │   │ │
│  │  │  • ai_migration_jobs                                                 │   │ │
│  │  └─────────────────────────────────────────────────────────────────────┘   │ │
│  │                                                                             │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │ │
│  │  │   Intent    │  │   Action    │  │    RAG      │  │  Security   │       │ │
│  │  │  Extraction │  │  Execution  │  │  Provider   │  │   Layer     │       │ │
│  │  │    (LLM)    │  │  Framework  │  │             │  │             │       │ │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────────────┘       │ │
│  │         │                │                │                                │ │
│  └─────────┼────────────────┼────────────────┼────────────────────────────────┘ │
│            │                │                │                                   │
│            │   CONNECTS TO  │                │                                   │
│            ▼                ▼                ▼                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐  │
│  │  Customer's LLM │  │  Customer's App │  │  Vector Database                │  │
│  │  (API Keys)     │  │  (Actions)      │  │  (Customer provides             │  │
│  │                 │  │                 │  │   OR AI Fabric provides)        │  │
│  │  • OpenAI       │  │  • @AIAction    │  │                                 │  │
│  │  • Anthropic    │  │    handlers     │  │  • Qdrant (managed per tenant)  │  │
│  │  • Azure        │  │  • Business     │  │  • OR customer's own instance   │  │
│  │  • Cohere       │  │    logic        │  │                                 │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘  │
│                                │                                                 │
│                                ▼                                                 │
│                       ┌─────────────────┐                                       │
│                       │  Customer's     │                                       │
│                       │  Business DB    │                                       │
│                       │  (Their Data)   │                                       │
│                       │                 │                                       │
│                       │  • Products     │                                       │
│                       │  • Orders       │                                       │
│                       │  • Users        │                                       │
│                       └─────────────────┘                                       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 What AI Fabric PROVIDES (Per Customer Deployment)

| Component | Description | We Manage |
|-----------|-------------|-----------|
| **AI Fabric Runtime** | Orchestration engine, pipeline, intent handling | Yes |
| **Internal Database** | PostgreSQL for framework metadata (profiles, history, sessions, etc.) | Yes |
| **Vector Database** | Optional - Qdrant namespace per customer (if customer doesn't bring their own) | Optional |
| **Indexing/Sync Module** | Generic module to sync customer's relational data to vectors | Yes |

### 2.3 What Customer PROVIDES

| Component | Description | Notes |
|-----------|-------------|-------|
| **LLM API Keys** | OpenAI, Anthropic, Azure, Cohere, Gemini | Customer controls costs |
| **Business Database** | Their relational DB with their business data | We connect to read for indexing |
| **Action Handlers** | Business logic that AI can execute | @AIAction annotated code |
| **Vector Database** | Optional - if they have existing Qdrant/Pinecone/etc. | OR we provide managed |

### 2.4 Clear Separation of Data

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA OWNERSHIP MODEL                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  AI FABRIC OWNS & MANAGES:                                       │
│  ├── Framework metadata (profiles, history, sessions)            │
│  ├── Indexing state (queue, catalog, migration jobs)             │
│  └── Vector embeddings (if we provide vector DB)                 │
│                                                                  │
│  CUSTOMER OWNS & MANAGES:                                        │
│  ├── Business data (products, orders, users, etc.)               │
│  ├── LLM API keys and costs                                      │
│  ├── Action handler business logic                               │
│  └── Vector database (optional - can bring their own)            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Part 3: Technical Implementation for Cloud Deployment

### 3.1 Dual Database Architecture

Each customer deployment has TWO database contexts:

```java
// Configuration for dual database setup
@Configuration
public class DualDatabaseConfiguration {

    // AI Fabric Internal Database (We provide & manage)
    @Bean
    @Primary
    @ConfigurationProperties("ai.internal-datasource")
    public DataSource aiFabricInternalDataSource() {
        return DataSourceBuilder.create().build();
    }

    // Customer's Business Database (They provide - READ ONLY for indexing)
    @Bean
    @ConfigurationProperties("ai.customer-datasource")
    @ConditionalOnProperty("ai.indexing.enabled")
    public DataSource customerDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

**Configuration:**

```yaml
ai:
  # AI Fabric Internal Database (We manage per customer)
  internal-datasource:
    url: jdbc:postgresql://${AI_FABRIC_DB_HOST}:5432/tenant_${TENANT_ID}
    username: ${AI_FABRIC_DB_USER}
    password: ${AI_FABRIC_DB_PASS}
    hikari:
      maximum-pool-size: 10

  # Customer's Business Database (They provide - for indexing)
  customer-datasource:
    enabled: ${CUSTOMER_DB_ENABLED:false}
    url: ${CUSTOMER_DB_URL:}
    username: ${CUSTOMER_DB_USER:}
    password: ${CUSTOMER_DB_PASS:}
    read-only: true  # We only READ from customer's DB
```

### 3.2 Vector Database Options

Customer can choose between:

**Option A: AI Fabric Managed Vector DB**
```yaml
ai:
  vector-db:
    mode: managed  # AI Fabric provisions Qdrant namespace
    # Automatically configured per tenant
```

**Option B: Customer Provides Vector DB**
```yaml
ai:
  vector-db:
    mode: customer-provided
    type: qdrant  # or pinecone, milvus, weaviate
    qdrant:
      host: ${CUSTOMER_QDRANT_HOST}
      port: 6334
      api-key: ${CUSTOMER_QDRANT_API_KEY}
```

### 3.3 Generic Indexing/Sync Module

The indexing module connects to customer's database to sync data to vectors:

```java
// Generic sync service - reads from customer DB, writes to vector DB
public interface DataSyncService {

    // Sync single entity
    SyncResult syncEntity(SyncEntityRequest request);

    // Batch sync
    BatchSyncResult syncBatch(BatchSyncRequest request);

    // Full table sync
    SyncJob startFullSync(FullSyncRequest request);

    // Incremental sync (if customer DB supports change tracking)
    SyncJob startIncrementalSync(IncrementalSyncRequest request);

    // Status
    SyncStatus getJobStatus(String jobId);
}

// Request to sync - customer specifies what to sync
public class SyncEntityRequest {
    private String entityType;       // e.g., "product"
    private String entityId;         // Primary key
    private Map<String, Object> data; // Fields to embed
    private List<String> embeddableFields;  // Which fields to vectorize
    private Map<String, Object> metadata;    // Metadata to store
}
```

**Two Sync Modes:**

1. **Push Mode** - Customer pushes data to AI Fabric API:
```bash
# Customer calls our API when their data changes
POST /api/sync/entity
{
  "entityType": "product",
  "entityId": "prod-123",
  "data": {
    "name": "Running Shoes",
    "description": "Lightweight running shoes for marathons"
  },
  "embeddableFields": ["description"],
  "metadata": { "price": 79.99, "category": "footwear" }
}
```

2. **Pull Mode** - AI Fabric pulls from customer's DB:
```yaml
# Configuration for pull-based sync
ai:
  indexing:
    mode: pull
    source:
      datasource: customer  # Use customer-datasource
      entities:
        - type: product
          table: products
          id-column: id
          embeddable-columns: [description, name]
          metadata-columns: [price, sku, category]
          sync-strategy: incremental  # or full
          schedule: "0 */15 * * * *"  # Every 15 minutes
```

### 3.4 LLM Connection (Customer Provides Keys)

```yaml
ai:
  providers:
    llm-provider: ${LLM_PROVIDER:openai}

    openai:
      api-key: ${CUSTOMER_OPENAI_API_KEY}
      model: ${OPENAI_MODEL:gpt-4o}
      timeout: 60

    anthropic:
      api-key: ${CUSTOMER_ANTHROPIC_API_KEY}
      model: ${ANTHROPIC_MODEL:claude-3-haiku-20240307}

    # Customer chooses which provider to use
```

### 3.5 Module Structure (Current - Already Good)

The current module structure supports this architecture:

```
ai-infrastructure-module/
├── ai-infrastructure-core/           # Orchestration + Intent + Actions
├── ai-infrastructure-web/            # REST API endpoints
├── ai-infrastructure-rag/            # RAG capabilities
├── ai-infrastructure-indexing/       # Data sync/indexing
├── ai-infrastructure-migration/      # Bulk data migration
├── ai-infrastructure-chat-session/   # Conversation state
├── ai-infrastructure-behavior/       # Behavioral analytics
├── ai-infrastructure-pii/            # PII detection
├── ai-infrastructure-governance/     # Compliance
└── ai-infrastructure-relationship-query/  # NL to SQL

ai-provider-module/
├── providers/
│   ├── ai-infrastructure-openai-starter/
│   ├── ai-infrastructure-anthropic-starter/
│   ├── ai-infrastructure-azure-starter/
│   ├── ai-infrastructure-cohere-starter/
│   ├── ai-infrastructure-gemini-starter/
│   └── ai-infrastructure-onnx-starter/
└── vector-databases/
    ├── ai-infrastructure-qdrant-starter/
    ├── ai-infrastructure-pinecone-starter/
    ├── ai-infrastructure-milvus-starter/
    ├── ai-infrastructure-weaviate-starter/
    └── ai-infrastructure-lucene-starter/
```

---

## Part 4: Deployment Model (Single-Tenant Isolated)

### 4.1 Architecture Per Customer

Each customer gets a **completely isolated deployment**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CUSTOMER: ACME CORP                                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    AI FABRIC DEPLOYMENT (Isolated)                      │ │
│  │                                                                         │ │
│  │  ┌─────────────────────────┐    ┌─────────────────────────┐            │ │
│  │  │   AI Fabric Runtime     │    │   AI Fabric Internal DB │            │ │
│  │  │   (ECS/Kubernetes)      │────│   (RDS PostgreSQL)      │            │ │
│  │  │                         │    │   - acme_profiles       │            │ │
│  │  │   • Orchestration       │    │   - acme_history        │            │ │
│  │  │   • Intent Extraction   │    │   - acme_sessions       │            │ │
│  │  │   • Action Framework    │    │   - acme_indexing       │            │ │
│  │  │   • RAG Provider        │    │                         │            │ │
│  │  └───────────┬─────────────┘    └─────────────────────────┘            │ │
│  │              │                                                          │ │
│  └──────────────┼──────────────────────────────────────────────────────────┘ │
│                 │                                                             │
│    ┌────────────┼────────────┬─────────────────────┐                         │
│    │            │            │                     │                         │
│    ▼            ▼            ▼                     ▼                         │
│  ┌────────┐  ┌────────┐  ┌────────────────┐  ┌────────────────────┐         │
│  │Customer│  │Customer│  │ Vector DB      │  │ Customer's         │         │
│  │LLM Key │  │App     │  │ (Managed OR    │  │ Business DB        │         │
│  │        │  │        │  │  Customer's)   │  │ (Read-Only Access) │         │
│  │OpenAI  │  │Actions │  │                │  │                    │         │
│  │sk-...  │  │        │  │ Qdrant ns:acme │  │ Products, Orders   │         │
│  └────────┘  └────────┘  └────────────────┘  └────────────────────┘         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 What We Deploy Per Customer

| Component | Technology | We Manage | Cost (Monthly) |
|-----------|------------|-----------|----------------|
| AI Fabric Runtime | ECS Fargate / Kubernetes | Yes | $35-100 |
| Internal Database | PostgreSQL (RDS/Cloud SQL) | Yes | $25-50 |
| Vector DB (if managed) | Qdrant namespace | Yes | $25-100 |
| Load Balancer | ALB/Nginx | Yes | $20 |
| **Total** | | | **$105-270** |

### 4.3 Customer Connection Options

**Option A: Fully Managed (We Provide Vector DB)**
```yaml
# Customer provides:
customer:
  llm-api-key: sk-...
  business-db:  # Optional - for indexing
    url: jdbc:postgresql://customer-db.example.com:5432/app
    username: readonly_user
    password: ***

# We provide:
ai-fabric:
  internal-db: managed     # We provision
  vector-db: managed       # We provision Qdrant namespace
```

**Option B: BYOD (Bring Your Own Database)**
```yaml
# Customer provides:
customer:
  llm-api-key: sk-...
  vector-db:
    type: qdrant
    host: customer-qdrant.cloud.qdrant.io
    api-key: ***
  business-db:  # Optional - for indexing
    url: jdbc:postgresql://customer-db.example.com:5432/app

# We provide:
ai-fabric:
  internal-db: managed     # We still provision this
  vector-db: customer      # Customer's own
```

### 4.4 SDK Model (Self-Hosted by Customer)

For customers who want to run everything themselves:

```xml
<!-- Customer's pom.xml -->
<dependency>
    <groupId>com.ai-fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

Customer deploys and manages:
- Their own AI Fabric Runtime
- Their own Internal DB (for framework metadata)
- Their own Vector DB
- Their own LLM keys

**Revenue Model**: License fee + support tier

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

### 6.1 Deployment Configuration (Per Customer)

```yaml
# ai-fabric-deployment.yml - Generated per customer deployment

# Tenant identification
tenant:
  id: acme-corp
  name: Acme Corporation
  plan: growth

# AI Fabric Internal Database (We provision and manage)
ai-fabric:
  internal-datasource:
    url: jdbc:postgresql://ai-fabric-db.internal:5432/tenant_acme_corp
    username: aifabric_internal
    password: ${AI_FABRIC_DB_PASSWORD}  # From Secrets Manager

# LLM Provider (Customer provides API key)
providers:
  llm-provider: openai
  openai:
    api-key: ${CUSTOMER_OPENAI_API_KEY}  # Customer's key
    model: gpt-4o
    timeout: 60

# Vector Database Configuration
vector-db:
  mode: managed  # managed | customer-provided

  # If managed - we provision Qdrant namespace
  managed:
    provider: qdrant
    namespace: acme_corp

  # If customer-provided - they give us connection
  customer:
    type: qdrant
    host: ${CUSTOMER_QDRANT_HOST}
    api-key: ${CUSTOMER_QDRANT_API_KEY}

# Indexing Configuration (Optional - if customer wants data sync)
indexing:
  enabled: true
  mode: pull  # pull | push

  # Customer's business database (READ ONLY access)
  customer-datasource:
    url: ${CUSTOMER_DB_URL}
    username: ${CUSTOMER_DB_USER}
    password: ${CUSTOMER_DB_PASSWORD}
    read-only: true

# Feature flags
features:
  rag: true
  chat-sessions: true
  behavior-analytics: false
  pii-detection: true
  governance: false
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

### 9.1 Managed Deployment (Recommended)

Each customer gets isolated deployment with AI Fabric internal DB:

| Tier | Monthly Price | Includes | Our Cost | Margin |
|------|---------------|----------|----------|--------|
| **Starter** | $199 | 10K queries, BYOD vector | ~$80 | 60% |
| **Growth** | $499 | 50K queries, Managed vector (100K) | ~$150 | 70% |
| **Scale** | $1,499 | 200K queries, Managed vector (1M) | ~$400 | 73% |
| **Enterprise** | Custom | Unlimited, SLA, Support | Custom | Custom |

**What's Included in All Tiers:**
- Isolated AI Fabric deployment
- Managed internal database (framework metadata)
- All orchestration capabilities
- Security, PII detection
- Chat sessions, history tracking
- Data sync/indexing module

**Customer Provides:**
- LLM API keys (they pay directly to OpenAI/Anthropic)
- Vector DB (Starter tier) or use our managed (Growth+)
- Business database connection (for indexing)

### 9.2 SDK License (Self-Hosted)

For customers who want to run everything themselves:

| Tier | Price | Includes |
|------|-------|----------|
| Community | Free | Core orchestration (limited support) |
| Pro | $299/month | All modules + Email support |
| Enterprise | Custom | SLA + Dedicated support + Custom features |

### 9.3 Usage-Based Add-ons

| Add-on | Price |
|--------|-------|
| Additional orchestration calls | $0.001/call |
| Additional vector storage | $0.10/10K vectors |
| Data sync operations | $0.0001/entity |
| Priority support | +$200/month |

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

### What AI Fabric PROVIDES (Per Customer)

| Component | We Provide | We Manage |
|-----------|------------|-----------|
| **AI Fabric Runtime** | Orchestration, intent extraction, actions, RAG | Yes |
| **Internal Database** | PostgreSQL for framework metadata | Yes |
| **Vector Database** | Qdrant namespace (optional - or customer brings own) | Optional |
| **Sync Module** | Generic indexing from customer's DB to vectors | Yes |

### What Customer PROVIDES

| Component | They Provide | They Manage |
|-----------|--------------|-------------|
| **LLM API Keys** | OpenAI, Anthropic, etc. | Yes (cost control) |
| **Business Database** | Their relational DB (we read for indexing) | Yes |
| **Action Handlers** | Business logic (@AIAction code) | Yes |
| **Vector Database** | Optional - can bring their own | Optional |

### Architecture Principle: Clear Separation

```
┌─────────────────────────────────────────────────────────┐
│                    AI FABRIC PROVIDES                    │
│  ┌─────────────────────────────────────────────────┐    │
│  │  Internal DB (profiles, history, sessions, etc.) │    │
│  │  Vector DB (optional - managed per customer)     │    │
│  │  Orchestration Runtime                           │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
                           │
                    CONNECTS TO
                           │
┌─────────────────────────────────────────────────────────┐
│                    CUSTOMER PROVIDES                     │
│  ┌─────────────────────────────────────────────────┐    │
│  │  LLM API Keys (OpenAI, Anthropic, etc.)         │    │
│  │  Business Database (products, orders, users)     │    │
│  │  Action Handlers (business logic)                │    │
│  │  Vector DB (optional - can bring their own)      │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### The Value Proposition

> **"We are the orchestra of AI."**
>
> You bring your data. You bring your LLM keys.
> We orchestrate the intelligence.
> Every customer gets isolated deployment.
> You own your data. We power your AI.

### Key Differentiators

1. **Isolated Per Customer** - Not multi-tenant, truly separated
2. **Framework Metadata Managed** - We handle profiles, history, sessions
3. **Flexible Vector DB** - Customer brings their own OR we provide managed
4. **Generic Sync Module** - Connect any relational DB, sync to vectors
5. **Customer Controls LLM Costs** - They provide API keys directly

---

*Document Version: 2.0*
*Updated: January 2026*
*Based on: Deep code analysis + Architecture clarification*
