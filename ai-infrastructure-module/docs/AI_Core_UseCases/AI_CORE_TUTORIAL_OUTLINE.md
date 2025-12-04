# 📚 AI-Core Module: Complete Tutorial Outline

**Comprehensive educational guide for learning and mastering the AI-Core infrastructure**

**Target Audience:** Developers new to AI-core, wanting to understand concepts and build applications  
**Prerequisites:** Java 21+, Spring Boot basics, REST API knowledge  
**Time to Complete:** 20-30 hours for full mastery  
**Status:** Ready for implementation

---

## 📖 Table of Contents

1. [Part 1: Fundamentals](#part-1-fundamentals)
2. [Part 2: Architecture Deep-Dive](#part-2-architecture-deep-dive)
3. [Part 3: Getting Started](#part-3-getting-started)
4. [Part 4: Core Patterns](#part-4-core-patterns)
5. [Part 5: Advanced Features](#part-5-advanced-features)
6. [Part 6: Building Real Applications](#part-6-building-real-applications)
7. [Part 7: Best Practices](#part-7-best-practices)
8. [Part 8: Troubleshooting](#part-8-troubleshooting)

---

# PART 1: FUNDAMENTALS

## 🎯 Section 1.1: What is AI-Core?

### Learning Objectives
- [ ] Understand what AI-Core is and why it exists
- [ ] Know what problems it solves
- [ ] Understand the philosophy behind the design
- [ ] See how it differs from other AI frameworks

### Topics

**1.1.1 Overview**
- What is AI-Core? (Definition, scope, capabilities)
- Why AI-Core? (Problem statement, market need)
- Who uses it? (Target users, use cases)
- Quick demo (show a working example)

**1.1.2 Core Philosophy**
- Rules-first, optional AI (security-first)
- Hook-based extensibility (customer controls logic)
- Minimal library principle (no utility methods)
- Multi-provider support (not vendor lock-in)
- Privacy & compliance first (GDPR/HIPAA/CCPA ready)

**1.1.3 Comparison with Alternatives**
- vs LangChain (differences, trade-offs)
- vs RAG-focused frameworks (philosophy difference)
- vs vendor-specific solutions
- When to use AI-Core vs alternatives

**1.1.4 Key Benefits**
- Security: Rules evaluated first, LLM optional
- Flexibility: Pluggable backends (OpenAI, Anthropic, Azure, etc.)
- Privacy: Built-in PII detection and compliance
- Performance: Local embeddings option (ONNX)
- Cost: Minimal API calls, intelligent caching
- Control: Full transparency, auditable decisions

### Hands-On Activity
- [ ] Install AI-Core in a sample project
- [ ] Run the demo application
- [ ] Review audit logs
- [ ] Understand the flow

---

## 🏗️ Section 1.2: Architecture Overview

### Learning Objectives
- [ ] Understand the 5-layer architecture
- [ ] Know what each layer does
- [ ] See how layers interact
- [ ] Understand data flow

### Topics

**1.2.1 The 5-Layer Architecture**

```
┌─────────────────────────────────┐
│  Layer 5: Application Layer     │  (Your code using @AICapable)
├─────────────────────────────────┤
│  Layer 4: Policy & Hook Layer   │  (Your business logic rules)
├─────────────────────────────────┤
│  Layer 3: Orchestration Layer   │  (Decision making, routing)
├─────────────────────────────────┤
│  Layer 2: AI Services Layer     │  (Embeddings, Search, Generation)
├─────────────────────────────────┤
│  Layer 1: Provider Layer        │  (ONNX, OpenAI, Lucene, H2)
└─────────────────────────────────┘
```

**Layer 1: Provider Layer**
- ONNX Runtime (local embeddings)
- OpenAI API (generation, embeddings)
- Azure OpenAI, Anthropic, Cohere (optional)
- Lucene (search indexing)
- H2, PostgreSQL (database)
- Milvus, Qdrant, Pinecone (vector DB)

**Layer 2: AI Services Layer**
- EmbeddingService (generate vectors)
- SearchService (semantic search)
- GenerationService (text generation)
- ClassificationService (categorization)
- PII Detection Service (privacy)
- Behavior Analytics Service (event tracking)

**Layer 3: Orchestration Layer**
- RAGOrchestrator (retrieval + generation)
- SearchOrchestrator (hybrid search)
- AccessControlService (entity-level control)
- AuditService (immutable logging)
- CacheService (smart caching)

**Layer 4: Policy & Hook Layer**
- EntityAccessPolicy (custom access control)
- DocumentAccessPolicy (document-level security)
- RetentionPolicyProvider (data lifecycle)
- ComplianceCheckProvider (compliance rules)
- BehaviorAnalysisPolicy (analytics policies)

**Layer 5: Application Layer**
- @AICapable entities (your domain objects)
- Controllers (REST endpoints)
- Your business logic

**1.2.2 Data Flow Example**
```
User Request
    ↓
Controller receives request
    ↓
AICapableProcessor triggers
    ↓
EntityAccessPolicy hook called (you decide yes/no)
    ↓
If allowed, continue to AI services
    ↓
Generate embedding (ONNX local)
    ↓
Search Lucene index
    ↓
Retrieve top results
    ↓
Call OpenAI if RAG needed
    ↓
Audit log recorded
    ↓
Response to user
```

**1.2.3 Core Components**
- AI-Core (main library)
- AI-Infrastructure-Core (base services)
- AI-Infrastructure-Behavior (analytics)
- Multiple provider modules (OpenAI, Azure, etc.)
- Multiple vector DB modules (Lucene, Pinecone, etc.)

### Hands-On Activity
- [ ] Draw the architecture on paper
- [ ] Trace a request through all layers
- [ ] Identify where YOUR code goes
- [ ] Identify where FRAMEWORK code goes

---

## 🎓 Section 1.3: Core Concepts

### Learning Objectives
- [ ] Understand embeddings
- [ ] Understand vector search
- [ ] Understand RAG
- [ ] Understand hooks and policies
- [ ] Understand audit logging

### Topics

**1.3.1 Embeddings**
- What are embeddings? (numerical representations)
- Why use embeddings? (semantic understanding)
- ONNX embeddings (local, fast, free)
- OpenAI embeddings (high quality, API cost)
- When to use which
- Embedding dimensions (384, 768, etc.)
- Vector similarity metrics

**1.3.2 Vector Search**
- How vector search works (similarity computation)
- Semantic search vs full-text (differences)
- Hybrid search (combining both)
- Lucene-based vector search
- Performance considerations
- Indexing strategies

**1.3.3 RAG (Retrieval-Augmented Generation)**
- What is RAG?
- Why RAG? (grounding LLM with facts)
- Retrieval phase (finding relevant documents)
- Augmentation phase (building context)
- Generation phase (LLM creates response)
- Quality metrics

**1.3.4 Hooks & Policies**
- What are hooks? (extension points)
- EntityAccessPolicy (entity-level rules)
- DocumentAccessPolicy (document-level rules)
- QueryAccessPolicy (query-level rules)
- RetentionPolicyProvider (lifecycle management)
- How to implement custom hooks

**1.3.5 Audit & Compliance**
- Why audit logging?
- Immutable audit trail
- What gets logged?
- GDPR compliance patterns
- HIPAA compliance patterns
- CCPA compliance patterns

### Hands-On Activity
- [ ] Generate embeddings using ONNX
- [ ] Compare with OpenAI embeddings
- [ ] Measure similarity between vectors
- [ ] Implement custom EntityAccessPolicy hook
- [ ] Review audit logs

---

# PART 2: ARCHITECTURE DEEP-DIVE

## 🏛️ Section 2.1: Provider Architecture

### Learning Objectives
- [ ] Understand provider abstraction
- [ ] Know how to add new providers
- [ ] Understand provider selection logic
- [ ] Know trade-offs of each provider

### Topics

**2.1.1 Provider Pattern**
```java
public interface AIProvider {
    AIGenerationResponse generateContent(AIGenerationRequest request);
    AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request);
    boolean supports(String model);
}
```

**2.1.2 Available Providers**

| Provider | Type | Cost | Quality | Speed | Use Case |
|----------|------|------|---------|-------|----------|
| ONNX | Embeddings | $0 | Good | Very Fast | Local, real-time |
| OpenAI | Generation, Embeddings | $$$ | Excellent | Fast | Complex tasks |
| Azure OpenAI | Generation, Embeddings | $$$ | Excellent | Fast | Enterprise |
| Anthropic | Generation | $$$ | Excellent | Fast | Long context |
| Cohere | Embeddings, Generation | $$ | Good | Fast | Balanced |
| Local LLama | Generation | $0 | Good | Slow | Private, offline |

**2.1.3 Provider Selection Strategy**
- Cost optimization (ONNX → OpenAI fallback)
- Quality vs speed trade-offs
- Region/compliance requirements
- Vendor lock-in considerations
- Redundancy and failover

**2.1.4 Implementing Custom Provider**
- Steps to add new provider
- Configuration required
- Testing requirements
- Documentation needed

### Hands-On Activity
- [ ] Configure multiple providers
- [ ] Test cost differences
- [ ] Implement provider failover
- [ ] Benchmark quality vs cost

---

## 🔐 Section 2.2: Security & Compliance Architecture

### Learning Objectives
- [ ] Understand security-first design
- [ ] Know how to implement access control
- [ ] Understand PII detection
- [ ] Know compliance requirements

### Topics

**2.2.1 Security-First Philosophy**
```
Rules Evaluated First (100% deterministic)
    ↓
LLM Enhancement (optional, for edge cases)
    ↓
Combined Decision (rule wins on conflict)
```

Why? 
- Rules are predictable and auditable
- LLM can make mistakes or be manipulated
- Compliance requires explainability
- Performance and cost optimization

**2.2.2 Access Control Patterns**

**Entity-Level Access**
```java
@Component
public class CustomEntityAccessPolicy implements EntityAccessPolicy {
    @Override
    public AccessDecision check(AccessContext context) {
        // Your business logic
        if (userRole.equals("ADMIN")) return ALLOW;
        if (userRole.equals("VIEWER") && context.isPublic()) return ALLOW;
        return DENY;
    }
}
```

**Document-Level Access**
```java
@Component
public class CustomDocumentAccessPolicy implements DocumentAccessPolicy {
    @Override
    public AccessDecision checkDocumentAccess(DocumentAccessContext ctx) {
        // Your document-specific logic
    }
}
```

**Query-Level Access**
```java
@Component
public class CustomQueryAccessPolicy implements QueryAccessPolicy {
    @Override
    public QueryDecision validateQuery(QueryContext ctx) {
        // Your query validation logic
    }
}
```

**2.2.3 PII Detection**
- What gets detected? (Credit cards, SSN, email, phone, etc.)
- Detection directions: INPUT, OUTPUT, BOTH
- Redaction strategies
- Audit logging of redactions
- GDPR Article 17 compliance

**2.2.4 Compliance Patterns**

**GDPR Compliance**
- Data subject access requests
- Right to be forgotten (Article 17)
- Data minimization
- Consent tracking
- Immutable audit trails

**HIPAA Compliance**
- 18 Protected Health Information (PHI) identifiers
- Encryption at rest and in transit
- Access logging
- Minimum necessary principle
- Business associate agreements

**CCPA Compliance**
- Consumer privacy requests
- Opt-out mechanisms
- Data sale disclosure
- Service provider contracts

### Hands-On Activity
- [ ] Implement custom access policy
- [ ] Test PII detection on sample data
- [ ] Generate GDPR compliance report
- [ ] Review audit trail for compliance

---

## 🎯 Section 2.3: Data Lifecycle Management

### Learning Objectives
- [ ] Understand data retention policies
- [ ] Know deletion mechanisms
- [ ] Understand redaction
- [ ] Know audit trail immutability

### Topics

**2.3.1 Retention Policies**

```
Active Data → Retention → Deletion
  (current)   (managed)   (purged)
```

Retention Tiers:
- 30 days (temporary, user activity)
- 90 days (privacy-sensitive)
- 1 year (business analytics)
- 7 years (tax/legal)
- Indefinite (archived, compliance)

**2.3.2 Deletion Mechanisms**
- User data deletion (GDPR Article 17)
- Cascading deletes (related entities)
- Soft deletes (audit preservation)
- Index cleanup
- Immutable audit preservation

**2.3.3 Redaction Strategies**
- Field-level redaction (mask values)
- Document-level redaction (hide content)
- Audit log redaction (preserve events, hide values)
- Reversible redaction (for recovery)
- Irreversible redaction (for destruction)

**2.3.4 Audit Immutability**
- Append-only logs
- Tamper detection
- Cryptographic signing
- Time-series database
- Retention rules

### Hands-On Activity
- [ ] Setup retention policies
- [ ] Trigger user deletion
- [ ] Verify cascading deletes
- [ ] Review audit preservation
- [ ] Test redaction

---

# PART 3: GETTING STARTED

## 🚀 Section 3.1: Installation & Setup

### Learning Objectives
- [ ] Install AI-Core
- [ ] Configure providers
- [ ] Setup database
- [ ] Verify installation

### Topics

**3.1.1 Prerequisites**
```bash
# System requirements
Java 21+
Maven 3.8+
Docker (optional, for services)
PostgreSQL (optional, production)

# Verify installation
java -version  # Should be 21+
mvn -version   # Should be 3.8+
```

**3.1.2 Add AI-Core to Your Project**

**Option A: Using Maven**
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- For specific providers -->
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

**Option B: Using Local Build**
```bash
cd ai-infrastructure-module
mvn clean install

# Now use in your project
mvn dependency:resolve
```

**3.1.3 Configuration**

**application.yml**
```yaml
spring:
  application:
    name: my-ai-app
  datasource:
    url: jdbc:h2:mem:testdb  # Development
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop

ai:
  embedding:
    provider: onnx  # or openai
    model-path: ./models/embeddings/model.onnx
    cache-enabled: true
  
  search:
    provider: lucene
    index-path: ./data/lucene-index
  
  providers:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4-turbo
      timeout: 30s
    
    onnx:
      model-path: ./models/embeddings/model.onnx
      timeout: 5s

  compliance:
    pii-detection:
      enabled: true
      detection-direction: BOTH  # INPUT, OUTPUT, or BOTH
```

**3.1.4 Download Models**
```bash
# Download ONNX embedding model
./scripts/download-onnx-model.sh

# Verify model exists
ls -la models/embeddings/model.onnx
```

**3.1.5 Setup Database**
```bash
# H2 is embedded, no setup needed

# For PostgreSQL (production)
createdb myapp_db
psql myapp_db < schema.sql
```

**3.1.6 Environment Variables**
```bash
export OPENAI_API_KEY="sk-..."
export ONNX_MODEL_PATH="./models/embeddings/model.onnx"
export LUCENE_INDEX_PATH="./data/lucene-index"
export SPRING_PROFILES_ACTIVE="dev"
```

**3.1.7 Verify Installation**
```bash
# Run test application
mvn spring-boot:run

# Check logs for
# ✓ Spring Boot started successfully
# ✓ AI-Core auto-configuration loaded
# ✓ ONNX model loaded
# ✓ Database initialized

# Test endpoint
curl http://localhost:8080/actuator/health
```

### Hands-On Activity
- [ ] Add AI-Core dependency
- [ ] Create application.yml
- [ ] Download ONNX model
- [ ] Start application
- [ ] Verify health endpoint
- [ ] Check logs

---

## 📝 Section 3.2: Your First AI-Capable Entity

### Learning Objectives
- [ ] Create @AICapable entity
- [ ] Configure AI settings
- [ ] Generate first embedding
- [ ] Search indexed data

### Topics

**3.2.1 Creating an AI-Capable Entity**

Step 1: Create entity class
```java
@Entity
@Table(name = "products")
@AICapable(entityType = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Lob
    private String description;
    
    @Column(columnDefinition = "BLOB")
    private byte[] embedding;  // Stores ONNX vector
    
    @CreatedDate
    private LocalDateTime createdAt;
}
```

Step 2: Create repository
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByName(String name);
}
```

Step 3: Configure in ai-entity-config.yml
```yaml
entities:
  product:
    entityType: product
    className: com.example.Product
    features:
      - embedding
      - search
    embeddable:
      fields:
        - description
        - name
    searchable:
      fields:
        - description
        - name
    metadata:
      - category
      - price
```

**3.2.2 How AI Processing Happens**

When you save a Product:
```
1. Controller receives POST /products with product data
2. AICapableProcessor intercepts
3. Checks @AICapable annotation on Product
4. Loads configuration from ai-entity-config.yml
5. Generates embedding from description + name
   (Using ONNX provider by default)
6. Stores embedding in database
7. Indexes in Lucene
8. Saves to H2 database
9. Returns product to user
```

**3.2.3 Controller Code**
```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductRepository repository;
    private final AICapabilityService aiService;
    
    @PostMapping
    public ResponseEntity<ProductDTO> create(@RequestBody CreateProductRequest req) {
        // AI processing happens automatically
        Product product = new Product();
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        
        Product saved = repository.save(product);  // ← AI magic happens here
        return ResponseEntity.status(201).body(toDTO(saved));
    }
}
```

**3.2.4 Search Indexed Data**
```java
@PostMapping("/search")
public ResponseEntity<List<ProductDTO>> search(@RequestBody SearchRequest req) {
    // Generate embedding for query
    String query = req.getQuery();
    
    // Search using ONNX embedding similarity
    List<Product> results = repository.findBySemantic(query);
    
    return ResponseEntity.ok(
        results.stream().map(this::toDTO).collect(Collectors.toList())
    );
}
```

### Hands-On Activity
- [ ] Create @AICapable Product entity
- [ ] Create repository
- [ ] Create controller
- [ ] Save product (auto-embed)
- [ ] Search products semantically
- [ ] Verify embedding stored
- [ ] Check Lucene index

---

# PART 4: CORE PATTERNS

## 🎯 Section 4.1: Semantic Search Pattern

### Learning Objectives
- [ ] Understand semantic search flow
- [ ] Implement keyword search
- [ ] Implement semantic search
- [ ] Combine for hybrid search
- [ ] Rank results effectively

### Topics

**4.1.1 Semantic Search Basics**

What happens:
```
User query: "comfortable modern couch"
    ↓
Generate embedding (ONNX)
    ↓
Find similar embeddings (Lucene)
    ↓
Return top 10 matches
    ↓
Rank by similarity score
```

**4.1.2 Implementation**

Basic semantic search:
```java
@Service
@RequiredArgsConstructor
public class ProductSearchService {
    private final EmbeddingService embeddingService;
    private final LuceneSearchService luceneService;
    private final ProductRepository repository;
    
    public List<Product> semanticSearch(String query, int topK) {
        // 1. Generate embedding for query
        byte[] queryEmbedding = embeddingService.embed(query);
        
        // 2. Search Lucene index
        List<String> ids = luceneService.searchSemantic(
            queryEmbedding, 
            topK
        );
        
        // 3. Fetch from database
        return repository.findAllById(ids);
    }
}
```

**4.1.3 Full-Text Search vs Semantic Search**

Full-text (BM25):
- Good for: exact keywords, fast
- Problem: misses synonyms ("couch" vs "sofa")
- Cost: minimal (local)

Semantic:
- Good for: understanding intent, synonyms
- Problem: can be slow, more expensive
- Cost: embedding generation

Hybrid:
- Combine both!
- Use RRF (Reciprocal Rank Fusion)
- Best results

**4.1.4 Hybrid Search Implementation**

```java
public List<Product> hybridSearch(String query, int topK) {
    // 1. Full-text search
    List<SearchResult> fullText = luceneService
        .searchFullText(query, topK);
    
    // 2. Semantic search
    byte[] embedding = embeddingService.embed(query);
    List<SearchResult> semantic = luceneService
        .searchSemantic(embedding, topK);
    
    // 3. Combine with RRF
    List<Product> combined = rankFusion(fullText, semantic);
    
    // 4. Return top K
    return combined.stream()
        .limit(topK)
        .collect(Collectors.toList());
}

private List<Product> rankFusion(List<SearchResult> r1, 
                                 List<SearchResult> r2) {
    Map<String, Double> scores = new HashMap<>();
    
    // Score from full-text (weight: 0.4)
    for (int i = 0; i < r1.size(); i++) {
        String id = r1.get(i).getId();
        scores.put(id, scores.getOrDefault(id, 0.0) + 
            0.4 * (1.0 / (i + 1)));
    }
    
    // Score from semantic (weight: 0.6)
    for (int i = 0; i < r2.size(); i++) {
        String id = r2.get(i).getId();
        scores.put(id, scores.getOrDefault(id, 0.0) + 
            0.6 * (1.0 / (i + 1)));
    }
    
    // Sort and return
    return scores.entrySet().stream()
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .map(e -> repository.findById(e.getKey()).get())
        .collect(Collectors.toList());
}
```

**4.1.5 Performance Optimization**

Caching:
```java
@Service
public class CachedSearchService {
    @Cacheable(value = "embeddings", key = "#query")
    public byte[] getEmbedding(String query) {
        return embeddingService.embed(query);
    }
}
```

Batching:
```java
public List<byte[]> batchEmbed(List<String> queries) {
    return queries.stream()
        .parallel()  // Parallel processing
        .map(q -> embeddingService.embed(q))
        .collect(Collectors.toList());
}
```

Indexing strategy:
```yaml
ai:
  entities:
    product:
      indexing:
        strategy: ASYNC      # Don't block on save
        batch-size: 100      # Batch commits
        refresh-interval: 5s # Lucene refresh
```

### Hands-On Activity
- [ ] Implement semantic search
- [ ] Implement full-text search
- [ ] Implement hybrid search
- [ ] Compare results
- [ ] Benchmark performance
- [ ] Add caching
- [ ] Test with large dataset

---

## 🤖 Section 4.2: RAG (Retrieval-Augmented Generation) Pattern

### Learning Objectives
- [ ] Understand RAG workflow
- [ ] Implement retrieval phase
- [ ] Implement augmentation phase
- [ ] Implement generation phase
- [ ] Handle edge cases

### Topics

**4.2.1 RAG Workflow**

```
User Question: "How do we handle GDPR requests?"
    ↓
[1] RETRIEVAL
Search documentation/knowledge base
Retrieve top 5 relevant documents
    ↓
[2] AUGMENTATION
Build context from documents
Format for LLM consumption
    ↓
[3] GENERATION
Call OpenAI with question + context
LLM generates answer grounded in facts
    ↓
Answer with Citations
```

**4.2.2 Retrieval Phase**

```java
@Service
@RequiredArgsConstructor
public class RAGService {
    private final SearchService searchService;
    private final DocumentRepository documentRepository;
    
    public List<Document> retrieve(String query, int topK) {
        // Search for relevant documents
        List<SearchResult> results = searchService
            .hybridSearch(query, topK);
        
        // Fetch full documents
        return results.stream()
            .map(r -> documentRepository.findById(r.getId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
}
```

**4.2.3 Augmentation Phase**

```java
public String buildContext(List<Document> documents) {
    StringBuilder context = new StringBuilder();
    context.append("Use the following documents to answer the question:\n\n");
    
    for (int i = 0; i < documents.size(); i++) {
        Document doc = documents.get(i);
        context.append("[Doc ").append(i + 1).append("] ")
            .append(doc.getTitle()).append("\n");
        context.append(doc.getContent()).append("\n\n");
    }
    
    return context.toString();
}
```

**4.2.4 Generation Phase**

```java
public String generate(String query, List<Document> context) {
    String contextStr = buildContext(context);
    
    String prompt = String.format("""
        %s
        
        Question: %s
        
        Answer based on the documents provided. If the answer is not 
        in the documents, say so. Include citations [Doc X] when relevant.
        """, contextStr, query);
    
    AIGenerationRequest request = new AIGenerationRequest();
    request.setPrompt(prompt);
    request.setModel("gpt-4-turbo");
    request.setTemperature(0.2);  // Lower for factual accuracy
    
    AIGenerationResponse response = providerManager
        .generateContent(request);
    
    return response.getContent();
}
```

**4.2.5 Complete RAG Implementation**

```java
@Service
@RequiredArgsConstructor
public class CompletedRAGService {
    private final RAGService ragService;
    private final DocumentService docService;
    private final AIProviderManager providerManager;
    
    public RAGResult answer(String question) {
        // 1. Retrieve
        List<Document> docs = ragService.retrieve(question, 5);
        
        // 2. Generate
        String answer = ragService.generate(question, docs);
        
        // 3. Extract citations
        List<Citation> citations = extractCitations(answer, docs);
        
        // 4. Return result with metadata
        return RAGResult.builder()
            .question(question)
            .answer(answer)
            .citations(citations)
            .documentsUsed(docs.size())
            .confidenceScore(calculateConfidence(docs))
            .build();
    }
    
    private double calculateConfidence(List<Document> docs) {
        if (docs.isEmpty()) return 0.0;
        return docs.stream()
            .mapToDouble(Document::getRelevanceScore)
            .average()
            .orElse(0.0);
    }
}
```

**4.2.6 RAG Quality Metrics**

```java
public class RAGMetrics {
    // Relevance: Are retrieved documents relevant?
    public double relevanceScore(List<Document> docs, String query) {
        return docs.stream()
            .mapToDouble(Document::getRelevanceScore)
            .average()
            .orElse(0.0);
    }
    
    // Citation Coverage: Does answer cite sources?
    public double citationCoverage(String answer, int docCount) {
        int citationCount = (int) answer.split("\\[Doc").length - 1;
        return (double) citationCount / docCount;
    }
    
    // Hallucination Detection: Is answer grounded in docs?
    public double hallucationScore(String answer, List<Document> docs) {
        // Check if answer contains facts from documents
        // Return score 0.0 (all hallucinated) to 1.0 (fully grounded)
        // Implementation: NLP text matching
        return 0.9;  // Example
    }
}
```

### Hands-On Activity
- [ ] Implement retrieval phase
- [ ] Build context from documents
- [ ] Generate answer with OpenAI
- [ ] Extract and format citations
- [ ] Measure confidence score
- [ ] Test with real questions
- [ ] Compare with non-RAG answers

---

# PART 5: ADVANCED FEATURES

## 🔐 Section 5.1: Access Control & Policies

### Learning Objectives
- [ ] Understand policy framework
- [ ] Implement custom policies
- [ ] Multi-level access control
- [ ] Audit access decisions

### Topics

**5.1.1 Policy Framework**

```
[Request arrives]
    ↓
Check EntityAccessPolicy
    ↓ (Decision: ALLOW/DENY)
    ├─ ALLOW → Continue
    ├─ DENY → Reject, log, return 403
    └─ DEFER → LLM decides (optional enhancement)
```

**5.1.2 Implementing EntityAccessPolicy**

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public AccessDecision checkAccess(AccessContext context) {
        // context contains:
        // - userId, userRole, userPermissions
        // - entityType, entityId, entityData
        // - operation (READ, CREATE, UPDATE, DELETE)
        // - requestMetadata (IP, timestamp, etc.)
        
        String userId = context.getUserId();
        String entityType = context.getEntityType();
        String operation = context.getOperation();
        Object entity = context.getEntity();
        
        // Business logic
        if (operation.equals("READ")) {
            // Everyone can read public entities
            if (entity instanceof PublicEntity) {
                return AccessDecision.ALLOW;
            }
            
            // Only owner can read private
            if (userId.equals(entity.getOwnerId())) {
                return AccessDecision.ALLOW;
            }
            
            // Admins can read everything
            if (context.hasRole("ADMIN")) {
                return AccessDecision.ALLOW;
            }
        }
        
        if (operation.equals("DELETE")) {
            // Only admins can delete
            if (context.hasRole("ADMIN")) {
                return AccessDecision.ALLOW;
            }
        }
        
        // Default: deny
        return AccessDecision.DENY;
    }
}
```

**5.1.3 Multi-Level Access Control**

Entity-level:
```java
// Which entities can user see?
customEntityPolicy.checkAccess(accessContext);
```

Document-level:
```java
// Which documents in entity can user see?
customDocumentPolicy.checkDocumentAccess(docContext);
```

Query-level:
```java
// Which queries are allowed?
customQueryPolicy.validateQuery(queryContext);
```

Attribute-level:
```java
// Which fields can user see?
// (Usually handled in DTO serialization)
```

**5.1.4 Audit Logging**

All policy decisions logged:
```
2025-11-25 10:30:45.123 | ACCESS_CHECK
  User: user@company.com
  Role: VIEWER
  Entity: Document #123
  Operation: READ
  Decision: ALLOW
  Timestamp: 2025-11-25T10:30:45Z
  IP: 192.168.1.100
```

### Hands-On Activity
- [ ] Implement custom EntityAccessPolicy
- [ ] Test ALLOW/DENY scenarios
- [ ] Implement multi-level control
- [ ] Review audit logs
- [ ] Test performance impact
- [ ] Test with concurrent requests

---

## 🛡️ Section 5.2: PII Detection & Privacy

### Learning Objectives
- [ ] Understand PII types
- [ ] Configure detection direction
- [ ] Implement redaction
- [ ] Maintain compliance
- [ ] Audit PII handling

### Topics

**5.2.1 PII Types Detected**

```yaml
# 18 HIPAA-defined PHI types
pii-types:
  - CREDIT_CARD         # 4111-1111-1111-1111
  - SSN                 # 123-45-6789
  - PHONE               # (123) 456-7890
  - EMAIL               # user@example.com
  - DOB                 # 1990-01-15
  - MEDICAL_RECORD      # MR: 12345678
  - ACCOUNT_NUMBER      # ACC: 987654321
  - DRIVER_LICENSE      # DL: A12345678
  - PASSPORT            # PP: 123456789
  - BANK_ROUTING        # RTN: 021000021
  - INSURANCE_POLICY    # POL: ABC123XYZ
  - IP_ADDRESS          # 192.168.1.1
  - BIOMETRIC           # Fingerprint, iris
  - GENETIC             # DNA markers
  - DEVICE_ID           # IMEI, MAC address
  - LOCATION_DATA       # GPS coordinates
  - MEDICAL_CONDITION   # Diabetes, HIV
  - RACIAL_ETHNIC       # Race, ethnicity
```

**5.2.2 Detection Directions**

```yaml
ai:
  pii-detection:
    detection-direction: BOTH  # INPUT, OUTPUT, or BOTH
```

**INPUT Detection** (privacy-first):
```
User Input: "My SSN is 123-45-6789"
    ↓
PII Detected: SSN
    ↓
Action: Redact before sending to LLM
    ↓
LLM Never Sees: 123-45-6789
```

**OUTPUT Detection** (safety-net):
```
LLM Generated: "User's SSN is 123-45-6789"
    ↓
PII Detected: SSN
    ↓
Action: Redact before returning to user
    ↓
User Never Sees: 123-45-6789
```

**BOTH** (Recommended):
```
Detects PII in both input and output
Maximum privacy protection
```

**5.2.3 Configuration**

```yaml
spring:
  config:
    activate:
      on-profile: dev

ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH
    redaction-strategy: MASK  # MASK, HASH, DELETE, ENCRYPT
    patterns:
      credit-card: '\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b'
      ssn: '\b\d{3}-\d{2}-\d{4}\b'
      phone: '\b(?:\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b'
      email: '\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'
    
    redaction:
      strategy: MASK
      mask-character: '*'
      preserve-format: true
      
    audit:
      log-detections: true
      immutable: true
```

**5.2.4 Implementation**

```java
@Service
@RequiredArgsConstructor
public class PIIDetectionService {
    private final PIIDetectionProperties properties;
    private final AuditService auditService;
    
    public PIIDetectionResult detectPII(String text, 
                                        PIIDetectionDirection direction) {
        if (!properties.isEnabled()) {
            return PIIDetectionResult.empty();
        }
        
        List<PIIMatch> matches = new ArrayList<>();
        
        // Check each pattern
        for (String piiType : properties.getPatterns().keySet()) {
            String pattern = properties.getPatterns().get(piiType);
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(text);
            
            while (m.find()) {
                PIIMatch match = new PIIMatch()
                    .setType(piiType)
                    .setStartIndex(m.start())
                    .setEndIndex(m.end())
                    .setDetectedAt(LocalDateTime.now());
                
                matches.add(match);
                
                // Audit log
                auditService.log(new PIIDetectionEvent()
                    .setDetectionDirection(direction)
                    .setPiiType(piiType)
                    .setRedactedHash(hash(m.group()))
                );
            }
        }
        
        return new PIIDetectionResult(matches, text);
    }
    
    public String redactPII(String text, PIIDetectionResult result) {
        String redacted = text;
        
        for (PIIMatch match : result.getMatches()) {
            String replacement = generateRedaction(
                match.getType(),
                match.getLength()
            );
            
            redacted = redacted.substring(0, match.getStartIndex()) +
                       replacement +
                       redacted.substring(match.getEndIndex());
        }
        
        return redacted;
    }
    
    private String generateRedaction(String piiType, int length) {
        switch (properties.getRedactionStrategy()) {
            case MASK:
                return "*".repeat(Math.min(length, 4));
            case HASH:
                return "[REDACTED-HASH]";
            case DELETE:
                return "";
            case ENCRYPT:
                return "[ENCRYPTED]";
            default:
                return "[REDACTED]";
        }
    }
}
```

**5.2.5 Testing PII Detection**

```java
@SpringBootTest
class PIIDetectionTest {
    @Autowired
    private PIIDetectionService service;
    
    @Test
    void testCreditCardDetection() {
        String text = "My card is 4111-1111-1111-1111";
        PIIDetectionResult result = service.detectPII(
            text, 
            PIIDetectionDirection.INPUT
        );
        
        assertTrue(result.hasPII());
        assertEquals(1, result.getMatches().size());
        assertEquals("CREDIT_CARD", result.getMatches().get(0).getType());
    }
    
    @Test
    void testRedaction() {
        String text = "SSN: 123-45-6789";
        PIIDetectionResult result = service.detectPII(text, 
            PIIDetectionDirection.INPUT);
        String redacted = service.redactPII(text, result);
        
        assertTrue(redacted.contains("***"));
        assertFalse(redacted.contains("123-45-6789"));
    }
}
```

### Hands-On Activity
- [ ] Configure PII detection
- [ ] Test all 18 PII types
- [ ] Test INPUT direction
- [ ] Test OUTPUT direction
- [ ] Implement redaction
- [ ] Review audit logs
- [ ] Test compliance

---

# PART 6: BUILDING REAL APPLICATIONS

(Comprehensive guides for each pattern, implementation examples, architecture decisions)

---

# PART 7: BEST PRACTICES

(Performance, security, compliance, maintainability, testing)

---

# PART 8: TROUBLESHOOTING

(Common issues, debugging, performance optimization)

---

## 📚 Learning Resources

### For Different Learning Styles

**Visual Learners**
- Architecture diagrams in PART 2
- Code examples in PART 4-5
- Sequence diagrams for flows

**Hands-On Learners**
- Activities in every section
- Sample projects
- Working code examples

**Conceptual Learners**
- Theory in PART 1-2
- Philosophy discussions
- Design patterns

### Suggested Learning Path

**Week 1: Foundations** (10 hours)
- PART 1: Fundamentals (4 hours)
- PART 2.1-2.2: Architecture & Security (6 hours)

**Week 2: Implementation** (10 hours)
- PART 3: Getting Started (4 hours)
- PART 4.1: Semantic Search (3 hours)
- PART 4.2: RAG (3 hours)

**Week 3: Advanced** (10 hours)
- PART 5: Advanced Features (6 hours)
- PART 6: Building Applications (4 hours)

**Ongoing: Reference**
- PART 7: Best Practices
- PART 8: Troubleshooting

---

## 🎯 Success Milestones

After completing each section, you should be able to:

**After Section 1.x:**
- [ ] Explain what AI-Core is and why to use it
- [ ] Describe the 5-layer architecture
- [ ] Understand core concepts (embeddings, vectors, hooks)

**After Section 2.x:**
- [ ] Implement custom access policies
- [ ] Design compliant systems
- [ ] Manage data lifecycle
- [ ] Handle PII correctly

**After Section 3.x:**
- [ ] Install AI-Core from scratch
- [ ] Configure providers
- [ ] Create first @AICapable entity
- [ ] Search indexed data

**After Section 4.x:**
- [ ] Implement semantic search
- [ ] Build RAG applications
- [ ] Generate grounded answers
- [ ] Cite sources correctly

**After Section 5.x:**
- [ ] Design multi-level access control
- [ ] Detect and redact PII
- [ ] Maintain compliance
- [ ] Audit all decisions

**After Section 6.x:**
- [ ] Build production applications
- [ ] Benchmark performance
- [ ] Ensure security
- [ ] Deploy with monitoring

**After Section 7.x:**
- [ ] Optimize performance
- [ ] Secure against attacks
- [ ] Maintain and upgrade
- [ ] Scale horizontally

**After Section 8.x:**
- [ ] Debug issues quickly
- [ ] Troubleshoot problems
- [ ] Optimize performance
- [ ] Help others learn

---

## 📞 Getting Help

When stuck:
1. Check the troubleshooting section in PART 8
2. Review related sections for context
3. Check code examples (they have comments)
4. Search documentation for keywords
5. Ask team members who completed sections

---

## ✅ Self-Assessment Quiz

**After completing each section, take the quiz:**

Section 1.x:
- [ ] What are the 5 layers?
- [ ] Why rules-first?
- [ ] What's a hook?

Section 2.x:
- [ ] How do policies work?
- [ ] What's the hook interface?
- [ ] How is data deleted?

Section 3.x:
- [ ] Steps to setup AI-Core?
- [ ] How do you create @AICapable entity?
- [ ] What happens on save?

(Continue for all sections...)

---

**This tutorial outline provides a comprehensive learning path for mastering AI-Core module.**

**Next: Start with PART 1: Fundamentals**

---

**Status:** Ready for Tutorial Creation  
**Last Updated:** November 2025  
**Estimated Reading Time:** 20-30 hours for full completion





