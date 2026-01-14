# 🎯 Real Use Cases for AI Fabric Framework - Standalone Applications

**Document Purpose:** Comprehensive collection of real-world use case ideas for building separate, standalone applications using AI Fabric Framework  
**Target:** Applications requiring UI with backend REST endpoints  
**Framework:** AI Fabric Framework (Core, Web, Behavior, Migration, Relationship Query)  
**Last Updated:** January 2026

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Real_Apps Demo Suite (Selected & Supported)](#real_apps-demo-suite-selected--supported)
3. [Recommended Starter Apps (7 Focused Examples)](#recommended-starter-apps-7-focused-examples)
4. [Use Case Categories](#use-case-categories)
5. [E-Commerce & Retail](#e-commerce--retail)
6. [Healthcare & Life Sciences](#healthcare--life-sciences)
7. [Financial Services](#financial-services)
8. [Education & Learning](#education--learning)
9. [Legal & Compliance](#legal--compliance)
10. [HR & Talent Management](#hr--talent-management)
11. [Content & Media](#content--media)
12. [Customer Success & Support](#customer-success--support)
13. [Research & Analytics](#research--analytics)
14. [Implementation Guidelines](#implementation-guidelines)

---

## Overview

This document provides **real-world use case ideas** for building standalone applications that demonstrate the full power of AI Fabric Framework. Each use case:

- ✅ **Requires a UI** - Needs comprehensive REST API endpoints
- ✅ **Is a complete application** - Not just a feature, but a full product
- ✅ **Leverages AI Fabric capabilities** - Uses Core, Web, Behavior, Migration, Relationship Query
- ✅ **Is production-ready** - Includes security, compliance, and scalability considerations
- ✅ **Has clear business value** - Solves real problems with measurable impact

### Framework Capabilities Used

Each use case leverages one or more of these AI Fabric capabilities:

- **Semantic Search** - Understanding meaning, not just keywords (via `AISearchService`)
- **RAG (Retrieval-Augmented Generation)** - Context-aware AI responses (via `RAGProvider`)
- **Natural Language Queries** - Plain English to database queries (via `ReliableRelationshipQueryService`)
- **Behavior Analytics** - User behavior tracking and insights (via `BehaviorAnalysisService`)
- **PII/PHI Detection** - Privacy-first data handling (via `PIIDetectionService`)
- **Migration Tools** - Bulk data processing (via `DataMigrationService`)
- **Web Endpoints** - REST controllers for security/compliance/advanced RAG/migration (via `ai-fabric-web` module)
- **Automatic Indexing** - Via `@AICapable` annotation with `autoEmbedding` and `indexable`

### Framework Modules

- **ai-fabric-core** - Core AI services (search, RAG, embeddings, LLM)
- **ai-fabric-web** - Optional REST API layer (security/compliance/advanced RAG/migration controllers)
- **ai-infrastructure-behavior** - Behavior analytics, sentiment, churn prediction
- **ai-infrastructure-migration** - Bulk data migration with pause/resume
- **ai-infrastructure-relationship-query** - Natural language to JPQL queries
- **ai-infrastructure-onnx-starter** - Free local embeddings (ONNX)
- **ai-infrastructure-vector-lucene** - Embedded vector database

---

## Real_Apps Demo Suite (Selected & Supported)

The repo’s `Real_Apps/` folder should contain a **small set of runnable apps** that:
- each prove **one setup scenario**
- are realistic enough to demo to stakeholders
- can be built + booted regularly as an acceptance suite for AI Fabric integration

Below is a proposed demo suite selected from the use cases in this document, with a quick support check against the current framework modules.

| Real_Apps folder | Source idea | Setup scenario (one focus) | Suggested default setup | Framework support check |
|---|---|---|---|---|
| `sub-management-hub-simple` | [App 7](#app-7-subscription-management-hub) | **Minimal integration** (config-driven) | ONNX + Lucene + H2 (no external infra) | ✅ Supported (core + indexing + local embeddings + embedded vector DB) |
| `sub-management-hub` | [App 7](#app-7-subscription-management-hub) | **Annotation-driven** (shows `@AICapable`/`@AIProcess`) | ONNX + Lucene + H2 | ✅ Supported (core + indexing; annotations optional) |
| `smart-faq-assistant` | [App 1](#app-1-smart-faq-assistant) | **RAG + semantic search** over curated content | ONNX + Lucene + H2; optional LLM provider | ✅ Supported (RAG module + AdvancedRAG) |
| `document-intelligence-hub` | [App 2](#app-2-document-intelligence-hub) | **PII-aware ingestion + Q&A** over documents | ONNX + Lucene + Postgres; PII `REDACT` | ✅ Supported (PII + RAG + chunking) ⚠️ File parsing is external |
| `product-discovery-engine` | [App 3](#app-3-product-discovery-engine) | **Production-like vector search** + metadata filtering | OpenAI/Cohere + Qdrant + Postgres | ✅ Supported (Qdrant module exists) ⚠️ Needs external Qdrant |
| `team-sentiment-tracker` | [App 4](#app-4-team-sentiment-tracker) | **Behavior analytics** (sentiment + churn) | LLM provider required + Postgres | ✅ Supported (behavior module) ⚠️ App must provide `ExternalEventProvider` |
| `bi-analytics-platform` | [Use Case #21](#use-case-21-business-intelligence--analytics-platform) | **Natural language → relational queries** | Relationship Query + Postgres + LLM provider | ✅ Supported (`ReliableRelationshipQueryService`) ⚠️ LLM required |

**Notes (important for correctness):**
- **Index sync trigger:** Today, keeping vectors/searchables synced typically means annotating write methods with `@AIProcess` (or calling the indexing coordinator explicitly). Plan the demo apps accordingly.
- **Migration demo:** If an app needs to demonstrate bulk backfill/reindexing, include `ai-infrastructure-migration` and set `ai.migration.enabled=true` (optionally add `ai-fabric-web` to expose `/api/ai/migration/*`).

---

## Recommended Starter Apps (7 Focused Examples)

These **7 focused example applications** are recommended as starting points. They demonstrate core framework capabilities with practical, implementable solutions. Each includes complete data models, REST endpoints, and implementation guidance.

### Quick Comparison

| App | Primary AI Capability | Endpoints | Complexity | Business Value |
|-----|----------------------|-----------|------------|----------------|
| 1. Smart FAQ Assistant | RAG + Semantic Search | 8 | Medium | Customer support automation |
| 2. Document Intelligence Hub | PII Detection + RAG + Semantic Search | 12 | Medium-High | Document processing |
| 3. Product Discovery Engine | Vector Search + Recommendations | 10 | Medium | E-commerce conversion |
| 4. Team Sentiment Tracker | Behavior Analytics + Sentiment | 9 | Medium | HR/Team management |
| 5. Code Documentation Search | RAG + Code Understanding | 7 | Medium | Developer productivity |
| 6. Meeting Notes Analyzer | RAG + Semantic Search | 8 | Medium | Productivity tools |
| 7. Subscription Management Hub | Behavior Analytics + Churn Prediction | 10 | Medium-High | SaaS/Subscription platforms |

### App 1: Smart FAQ Assistant

**Problem:** Users can't find answers in traditional FAQ systems. Support teams are overwhelmed with repetitive questions.

**AI Fabric Capabilities:**
- Semantic Search (ONNX embeddings)
- RAG (OpenAI generation with context)
- Hybrid Search (vector + full-text)
- Query Expansion

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "faq-article",
    autoEmbedding = true,
    indexable = true
)
public class FAQArticle {
    @Id
    private UUID id;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String category;
    private List<String> tags;
    private int helpfulCount;
    private int viewCount;
    private LocalDateTime lastUpdated;
}
```

**Key Endpoints:**
- `POST /api/faq/ask` - Ask a question, get AI-generated answer
- `GET /api/faq/search?q={query}` - Semantic search for articles
- `POST /api/faq/articles` - Create new FAQ article
- `GET /api/faq/articles/{id}` - Get article by ID
- `POST /api/faq/feedback/{queryId}` - Submit helpfulness feedback

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
public class FAQService {
    @AIProcess(entityType = "faq-article", processType = "create")
    public FAQArticle createArticle(CreateArticleRequest request) {
        return repository.save(new FAQArticle(...));
    }
    
    @AIProcess(entityType = "faq-article", processType = "update")
    public FAQArticle updateArticle(UUID id, UpdateArticleRequest request) {
        FAQArticle article = repository.findById(id).get();
        // ... update fields
        return repository.save(article);
    }
}
```

**Implementation:** 2-3 weeks | **Value:** 40-60% reduction in support tickets

---

### App 2: Document Intelligence Hub

**Problem:** Manual document review is time-consuming. Key information is buried in long documents with no way to search across content.

**⚠️ IMPORTANT: Framework Support Only**

**AI Fabric Framework Provides:**
- ✅ **PII Detection** - `PIIDetectionService.detectAndProcess()` with redaction capabilities
- ✅ **Embedding Generation** - `AICoreService.generateEmbedding()` for vector creation
- ✅ **Vector Indexing** - Automatic via `@AICapable` and `@AIProcess` annotations
- ✅ **RAG for Q&A** - Via `RAGProvider` for question answering
- ✅ **Semantic Search** - `AISearchService` for searching indexed documents

**External Requirement (NOT in Framework):**
- ❌ **Text Extraction from Files** - Framework does NOT provide file parsing. You must extract text from files (PDF, DOCX, TXT, etc.) using external libraries (Apache Tika, PDFBox, POI, etc.) BEFORE passing text to the framework.

**Document Processing Pipeline:**
```
1. Upload File (PDF/DOCX/TXT/etc.)
   ↓
2. Text Extraction (EXTERNAL: You must implement - Apache Tika / PDFBox / POI)
   ↓
3. PII Detection & Redaction (✅ Framework: PIIDetectionService.detectAndProcess())
   ↓
4. Embedding Generation (✅ Framework: AICoreService.generateEmbedding())
   ↓
5. Vector Indexing (✅ Framework: Automatic via @AICapable/@AIProcess)
   ↓
6. Ready for Search & Q&A (✅ Framework: RAGProvider)
```

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "document",
    autoEmbedding = true,
    indexable = true,
    indexingStrategy = IndexingStrategy.ASYNC  // Background processing for large docs
)
public class Document {
    @Id
    private UUID id;
    
    @AISearchable(weight = 1.0)  // Filename is searchable
    private String filename;
    
    private String mimeType;  // application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document, etc.
    private String fileExtension;  // pdf, docx, txt, etc.
    private Long fileSizeBytes;
    
    @AISearchable(weight = 2.0, maxLength = 50000)  // Main content - higher weight
    @Column(columnDefinition = "TEXT")
    private String extractedText;  // Full extracted text from PDF/DOCX/etc.
    
    @AIContext(contextKey = "documentType")
    private String documentType;  // contract, report, invoice, research_paper, etc.
    
    @AIContext(contextKey = "uploadedBy")
    private UUID uploadedBy;
    
    @AIContext(contextKey = "uploadedAt", dataType = "datetime")
    private LocalDateTime uploadedAt;
    
    @AIContext(contextKey = "wordCount")
    private Integer wordCount;
    
    private ProcessingStatus status;  // PENDING, PROCESSING, COMPLETED, FAILED
    
    private Boolean hasPII;  // Whether PII was detected
    private String piiRedactedText;  // Text with PII redacted (if PII detection enabled)
}
```

**Key Endpoints:**
- `POST /api/docs/upload` - Upload document (text must be extracted externally first)
- `POST /api/docs/search` - Semantic search across all documents
- `POST /api/docs/{id}/ask` - Ask question about specific document (RAG)
- `GET /api/docs/{id}` - Get document by ID
- `POST /api/docs/bulk-upload` - Upload multiple documents at once
- `POST /api/docs/{id}/reprocess` - Reprocess document (e.g., after PII detection update)

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
@RequiredArgsConstructor
public class DocumentService {
    
    private final DocumentRepository repository;
    private final AICoreService aiCoreService;
    private final RAGProvider ragProvider;
    private final PIIDetectionService piiDetectionService;
    // ⚠️ EXTERNAL DEPENDENCY: Text extraction is NOT part of AI Fabric Framework
    // You must implement text extraction from files yourself (Apache Tika, PDFBox, POI, etc.)
    private final TextExtractionService textExtractionService;  // External: You implement this
    
    /**
     * Upload and process document (PDF, DOCX, TXT, etc.)
     * @AIProcess ensures vector is created and synced with DB
     */
    @AIProcess(
        entityType = "document",
        processType = "create",
        generateEmbedding = true,
        indexForSearch = true,
        indexingStrategy = IndexingStrategy.ASYNC  // Background for large documents
    )
    @Transactional
    public Document uploadDocument(MultipartFile file) {
        // 1. Extract text from file (EXTERNAL: You must implement this)
        String extractedText = textExtractionService.extractText(file);
        
        // 2. Detect and redact PII (✅ Framework: PIIDetectionService)
        PIIDetectionResult piiResult = piiDetectionService.detectAndProcess(extractedText);
        
        // 3. Create document entity
        Document doc = new Document();
        doc.setFilename(file.getOriginalFilename());
        doc.setMimeType(file.getContentType());
        doc.setFileExtension(getExtension(file.getOriginalFilename()));
        doc.setFileSizeBytes(file.getSize());
        doc.setExtractedText(piiResult.getProcessedQuery());  // Use PII-redacted text
        doc.setHasPII(piiResult.isPiiDetected());
        doc.setPiiRedactedText(piiResult.getProcessedQuery());
        doc.setWordCount(countWords(piiResult.getProcessedQuery()));
        doc.setStatus(ProcessingStatus.COMPLETED);
        
        // 4. @AIProcess ensures vector is created and synced with DB
        return repository.save(doc);
    }
    
    /**
     * Reprocess document (e.g., update PII detection, regenerate summary)
     * @AIProcess ensures vector is updated in sync with DB
     */
    @AIProcess(
        entityType = "document",
        processType = "update",
        generateEmbedding = true,
        indexForSearch = true,
        indexingStrategy = IndexingStrategy.ASYNC
    )
    @Transactional
    public Document reprocessDocument(UUID id) {
        Document doc = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Document not found"));
        
        // Reprocess PII detection with updated settings
        String originalText = doc.getExtractedText();
        PIIDetectionResult piiResult = piiDetectionService.detectAndProcess(originalText);
        
        doc.setHasPII(piiResult.isPiiDetected());
        doc.setPiiRedactedText(piiResult.getProcessedQuery());
        doc.setExtractedText(piiResult.getProcessedQuery());
        
        // @AIProcess ensures vector is updated in sync with DB
        return repository.save(doc);
    }
    
    /**
     * Ask question about document using RAG
     */
    public String askQuestion(UUID documentId, String question) {
        Document doc = repository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found"));
        
        // Use RAG to answer question based on document content
        return ragProvider.askQuestion(question, doc.getExtractedText());
    }
    
    /**
     * Semantic search across all documents
     */
    public List<Document> searchDocuments(String query, int limit) {
        // Use AISearchService for semantic search
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query(query)
            .entityType("document")
            .limit(limit)
            .build();
        
        AISearchResponse response = aiCoreService.performSearch(searchRequest);
        // Convert search results to Document entities
        return response.getResults().stream()
            .map(result -> repository.findById(UUID.fromString(result.get("id").toString()))
                .orElse(null))
            .filter(doc -> doc != null)
            .toList();
    }
}
```

**Implementation Notes:**

**✅ Framework Features (Ready to Use):**
- **PII Detection:** Framework provides `PIIDetectionService.detectAndProcess()` with detection and redaction
- **Embedding Generation:** Framework provides `AICoreService.generateEmbedding()` for vector creation
- **Vector Indexing:** Automatic via `@AICapable` and `@AIProcess` annotations
- **RAG for Q&A:** Framework provides `RAGProvider` for question answering
- **Semantic Search:** Framework provides `AISearchService` for searching indexed documents
- **Async Processing:** Use `IndexingStrategy.ASYNC` for large documents to avoid blocking

**⚠️ External Requirement:**
- **Text Extraction:** Framework does NOT provide file parsing. You must:
  1. Implement text extraction from files yourself (using Apache Tika, PDFBox, POI, etc.)
  2. Extract text BEFORE passing to Framework
  3. Framework only processes text strings, not file formats

**Implementation:** 3-4 weeks | **Value:** 50-70% time savings in document review

---

### App 3: Product Discovery Engine

**Problem:** Traditional search misses product intent. Users struggle with complex filter combinations. Recommendations are generic.

**AI Fabric Capabilities:**
- Semantic Search (ONNX)
- Behavior Module (user interaction tracking)
- Hybrid Search (semantic + filters)
- Query Understanding (natural language to filters)

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "product",
    autoEmbedding = true,
    indexable = true
)
public class Product {
    @Id
    private UUID id;
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String category;
    private String brand;
    private BigDecimal price;
    private List<String> features;
    private List<String> tags;
    private float rating;
    private boolean inStock;
}
```

**Key Endpoints:**
- `POST /api/products/search` - Natural language product search
- `GET /api/products/{id}/similar` - Find similar products
- `GET /api/products/recommendations` - Personalized recommendations
- `POST /api/products/interactions` - Track user interaction
- `GET /api/products/trending` - Get trending products

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
public class ProductService {
    @AIProcess(entityType = "product", processType = "create", indexingStrategy = IndexingStrategy.SYNC)
    public Product createProduct(CreateProductRequest request) {
        return repository.save(new Product(...));  // Vector synced immediately
    }
    
    @AIProcess(entityType = "product", processType = "update")
    public Product updateProduct(UUID id, UpdateProductRequest request) {
        Product product = repository.findById(id).get();
        // ... update fields
        return repository.save(product);  // Vector updated via @AIProcess
    }
}
```

**Implementation:** 2-3 weeks | **Value:** 30-50% increase in search conversion

---

### App 4: Team Sentiment Tracker

**Problem:** Managers unaware of team morale issues. Delayed detection of burnout/disengagement. No way to track sentiment over time.

**AI Fabric Capabilities:**
- Behavior Module (event tracking)
- Sentiment Analysis (6-level classification: DELIGHTED → CHURNING)
- Trend Detection (improving/declining)
- Churn Prediction (disengagement risk)
- PII Detection (anonymization option)

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "check-in",
    autoEmbedding = true,
    indexable = true
)
public class TeamCheckIn {
    @Id
    private UUID id;
    private UUID teamMemberId;
    private UUID teamId;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    private SentimentLevel sentiment; // DELIGHTED, SATISFIED, NEUTRAL, CONFUSED, FRUSTRATED, CHURNING
    private float sentimentScore;
    private LocalDate checkInDate;
    private List<String> detectedTopics;
}
```

**Key Endpoints:**
- `POST /api/sentiment/check-in` - Submit daily check-in
- `GET /api/sentiment/team/{teamId}/dashboard` - Team sentiment dashboard
- `GET /api/sentiment/member/{memberId}/history` - Member sentiment history
- `GET /api/sentiment/alerts` - Get active alerts
- `GET /api/sentiment/team/{teamId}/trends` - Team sentiment trends

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
public class SentimentService {
    @AIProcess(entityType = "check-in", processType = "create")
    public TeamCheckIn submitCheckIn(CheckInRequest request) {
        TeamCheckIn checkIn = analyzeSentiment(request);
        return repository.save(checkIn);  // Vector synced via @AIProcess
    }
}
```

**Implementation:** 2-3 weeks | **Value:** 25-40% reduction in team turnover

---

### App 5: Code Documentation Search

**Problem:** Developers can't find relevant code examples. Documentation is scattered and hard to search. Onboarding takes too long.

**AI Fabric Capabilities:**
- Semantic Search (code-aware embeddings)
- RAG (answer code questions)
- Hybrid Search (semantic + exact code matches)
- Entity Extraction (functions, classes, APIs)

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "code-doc",
    autoEmbedding = true,
    indexable = true
)
public class CodeDocumentation {
    @Id
    private UUID id;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String repository;
    private String filePath;
    private String language;
    private DocType type; // README, API_DOC, TUTORIAL, CODE_COMMENT
    private List<String> relatedApis;
    private LocalDateTime lastUpdated;
}
```

**Key Endpoints:**
- `POST /api/code/search` - Semantic search for code/docs
- `POST /api/code/ask` - Ask a coding question
- `GET /api/code/snippets/{id}` - Get code snippet details
- `POST /api/code/index-repo` - Index a repository
- `GET /api/code/repos` - List indexed repositories

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
public class CodeDocumentationService {
    @AIProcess(entityType = "code-doc", processType = "create")
    public CodeDocumentation indexRepository(String repoPath) {
        CodeDocumentation doc = parseRepository(repoPath);
        return repository.save(doc);  // Vector synced via @AIProcess
    }
}
```

**Implementation:** 2-3 weeks | **Value:** 40-60% reduction in onboarding time

---

### App 6: Meeting Notes Analyzer

**Problem:** Action items get lost after meetings. No easy way to find past meeting decisions. Meeting summaries are manual and inconsistent.

**AI Fabric Capabilities:**
- Summarization (multi-level)
- Entity Extraction (action items, decisions, attendees)
- Semantic Search (find past discussions)
- RAG (answer questions about meetings)

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "meeting",
    autoEmbedding = true,
    indexable = true
)
public class Meeting {
    @Id
    private UUID id;
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String transcript;
    
    @Column(columnDefinition = "TEXT")
    private String summary;
    
    private LocalDateTime meetingDate;
    private int durationMinutes;
    private List<UUID> attendeeIds;
    private String meetingType; // standup, planning, review, etc.
    private ProcessingStatus status;
}
```

**Key Endpoints:**
- `POST /api/meetings` - Create/upload meeting notes
- `GET /api/meetings/{id}/summary` - Get meeting summary
- `GET /api/meetings/{id}/actions` - Get action items
- `GET /api/meetings/{id}/decisions` - Get decisions made
- `POST /api/meetings/search` - Search across meetings
- `POST /api/meetings/ask` - Ask question about past meetings

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
public class MeetingService {
    @AIProcess(entityType = "meeting", processType = "create")
    public Meeting createMeeting(CreateMeetingRequest request) {
        Meeting meeting = processTranscript(request);
        return repository.save(meeting);  // Vector synced via @AIProcess
    }
}
```

**Implementation:** 2-3 weeks | **Value:** 50-70% time savings in meeting follow-up

---

### App 7: Subscription Management Hub

**Problem:** Users struggle to find the right subscription plan. No visibility into subscription health or churn risk. Manual address updates are error-prone. No intelligent upgrade recommendations.

**AI Fabric Capabilities:**
- Behavior Analytics (track subscription events: subscribe, unsubscribe, upgrade, address update)
- Churn Prediction (identify at-risk subscribers)
- Semantic Search (natural language plan search: "plans under $50/month")
- Recommendations (suggest upgrades based on usage patterns)
- Smart Validation (AI-powered address validation)

**User Actions Supported:**
- ✅ **Subscribe** - Subscribe to a plan
- ✅ **Unsubscribe** - Cancel subscription
- ✅ **Upgrade** - Upgrade to higher tier plan
- ✅ **Downgrade** - Downgrade to lower tier plan
- ✅ **Update Address** - Update billing/shipping address with validation

**Data Model:**
```java
@Entity
@AICapable(
    entityType = "subscription-plan",
    autoEmbedding = true,
    indexable = true,
    enableRecommendations = true
)
public class SubscriptionPlan {
    @Id
    private UUID id;
    
    @AISearchable(weight = 2.0)
    private String name;  // "Pro Plan", "Enterprise Plan"
    
    @AISearchable(weight = 1.5)
    @Column(columnDefinition = "TEXT")
    private String description;  // Full plan description
    
    @AIContext(contextKey = "price", dataType = "decimal")
    private BigDecimal monthlyPrice;
    
    @AIContext(contextKey = "annualPrice", dataType = "decimal")
    private BigDecimal annualPrice;
    
    @AIContext(contextKey = "tier")
    private String tier;  // BASIC, PRO, ENTERPRISE
    
    @AIContext(contextKey = "features")
    private List<String> features;  // ["Unlimited storage", "Priority support", ...]
    
    @AIContext(contextKey = "maxUsers")
    private Integer maxUsers;
    
    @AIContext(contextKey = "storageGB")
    private Integer storageGB;
    
    private Boolean isActive;
}

@Entity
@AICapable(
    entityType = "subscription",
    autoEmbedding = false,  // Subscription itself doesn't need embedding
    indexable = true
)
public class Subscription {
    @Id
    private UUID id;
    
    private UUID userId;
    private UUID planId;
    
    @AIContext(contextKey = "status")
    private SubscriptionStatus status;  // ACTIVE, CANCELLED, PAST_DUE, EXPIRED
    
    @AIContext(contextKey = "startDate", dataType = "datetime")
    private LocalDateTime startDate;
    
    @AIContext(contextKey = "endDate", dataType = "datetime")
    private LocalDateTime endDate;
    
    @AIContext(contextKey = "billingCycle")
    private BillingCycle billingCycle;  // MONTHLY, ANNUAL
    
    @AIContext(contextKey = "churnRisk", dataType = "decimal")
    private Double churnRiskScore;  // 0.0-1.0 from Behavior Analysis
    
    @AIContext(contextKey = "lastActivityDate", dataType = "datetime")
    private LocalDateTime lastActivityDate;
    
    @OneToOne(cascade = CascadeType.ALL)
    private Address billingAddress;
    
    @OneToOne(cascade = CascadeType.ALL)
    private Address shippingAddress;
}

@Entity
public class Address {
    @Id
    private UUID id;
    
    @AISearchable(weight = 1.0)  // Address is searchable
    private String streetAddress;
    
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    @AIContext(contextKey = "addressType")
    private AddressType type;  // BILLING, SHIPPING
    
    @AIContext(contextKey = "isValidated")
    private Boolean isValidated;  // AI validation result
    
    @AIContext(contextKey = "validationScore", dataType = "decimal")
    private Double validationScore;  // 0.0-1.0 confidence in address validity
}
```

**Key Endpoints:**
- `POST /api/subscriptions/subscribe` - Subscribe to a plan
- `POST /api/subscriptions/{id}/unsubscribe` - Cancel subscription
- `POST /api/subscriptions/{id}/upgrade` - Upgrade to higher tier
- `POST /api/subscriptions/{id}/downgrade` - Downgrade to lower tier
- `PUT /api/subscriptions/{id}/address` - Update billing/shipping address
- `GET /api/subscriptions/plans/search` - Semantic search for plans (e.g., "plans under $50")
- `GET /api/subscriptions/{id}/recommendations` - Get upgrade recommendations
- `GET /api/subscriptions/{id}/churn-risk` - Get churn risk score
- `GET /api/subscriptions/at-risk` - List subscribers at risk of churning
- `POST /api/subscriptions/events` - Track subscription events (for behavior analysis)

**Service Methods (REQUIRED @AIProcess for vector sync):**
```java
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BehaviorAnalysisService behaviorService;
    private final AICoreService aiCoreService;
    private final AISearchService searchService;
    
    /**
     * Subscribe to a plan
     * @AIProcess ensures plan is indexed for search
     */
    @AIProcess(
        entityType = "subscription",
        processType = "create",
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Subscription subscribe(UUID userId, UUID planId, BillingCycle billingCycle) {
        SubscriptionPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new NotFoundException("Plan not found"));
        
        // Create subscription
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setBillingCycle(billingCycle);
        subscription.setEndDate(calculateEndDate(billingCycle));
        
        // @AIProcess ensures subscription is indexed
        Subscription saved = subscriptionRepository.save(subscription);
        
        // Track event for behavior analysis
        trackEvent(userId, "SUBSCRIBE", Map.of(
            "planId", planId.toString(),
            "planName", plan.getName(),
            "billingCycle", billingCycle.toString()
        ));
        
        return saved;
    }
    
    /**
     * Unsubscribe (cancel subscription)
     * @AIProcess ensures subscription status is updated in index
     */
    @AIProcess(
        entityType = "subscription",
        processType = "update",
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Subscription unsubscribe(UUID subscriptionId, String reason) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));
        
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setEndDate(LocalDateTime.now());
        
        // @AIProcess ensures status update is synced
        Subscription saved = subscriptionRepository.save(subscription);
        
        // Track event for behavior analysis
        trackEvent(subscription.getUserId(), "UNSUBSCRIBE", Map.of(
            "subscriptionId", subscriptionId.toString(),
            "reason", reason
        ));
        
        return saved;
    }
    
    /**
     * Upgrade to higher tier plan
     * @AIProcess ensures subscription is updated in index
     */
    @AIProcess(
        entityType = "subscription",
        processType = "update",
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Subscription upgrade(UUID subscriptionId, UUID newPlanId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));
        
        SubscriptionPlan oldPlan = planRepository.findById(subscription.getPlanId())
            .orElseThrow();
        SubscriptionPlan newPlan = planRepository.findById(newPlanId)
            .orElseThrow(() -> new NotFoundException("New plan not found"));
        
        // Validate upgrade (new plan must be higher tier)
        if (!isValidUpgrade(oldPlan.getTier(), newPlan.getTier())) {
            throw new IllegalArgumentException("Invalid upgrade path");
        }
        
        subscription.setPlanId(newPlanId);
        subscription.setLastActivityDate(LocalDateTime.now());
        
        // @AIProcess ensures upgrade is synced
        Subscription saved = subscriptionRepository.save(subscription);
        
        // Track event for behavior analysis
        trackEvent(subscription.getUserId(), "UPGRADE", Map.of(
            "oldPlanId", subscription.getPlanId().toString(),
            "newPlanId", newPlanId.toString(),
            "oldTier", oldPlan.getTier(),
            "newTier", newPlan.getTier()
        ));
        
        return saved;
    }
    
    /**
     * Update billing or shipping address with AI validation
     * @AIProcess ensures address update is synced
     */
    @AIProcess(
        entityType = "subscription",
        processType = "update",
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Subscription updateAddress(UUID subscriptionId, AddressType addressType, UpdateAddressRequest request) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));
        
        Address address = addressType == AddressType.BILLING 
            ? subscription.getBillingAddress() 
            : subscription.getShippingAddress();
        
        if (address == null) {
            address = new Address();
            address.setType(addressType);
        }
        
        // Update address fields
        address.setStreetAddress(request.getStreetAddress());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        
        // AI-powered address validation
        AddressValidationResult validation = validateAddress(address);
        address.setIsValidated(validation.isValid());
        address.setValidationScore(validation.getConfidenceScore());
        
        if (addressType == AddressType.BILLING) {
            subscription.setBillingAddress(address);
        } else {
            subscription.setShippingAddress(address);
        }
        
        subscription.setLastActivityDate(LocalDateTime.now());
        
        // @AIProcess ensures address update is synced
        Subscription saved = subscriptionRepository.save(subscription);
        
        // Track event for behavior analysis
        trackEvent(subscription.getUserId(), "UPDATE_ADDRESS", Map.of(
            "addressType", addressType.toString(),
            "isValidated", validation.isValid().toString(),
            "validationScore", validation.getConfidenceScore().toString()
        ));
        
        return saved;
    }
    
    /**
     * Semantic search for subscription plans
     * Example: "plans under $50 per month" or "enterprise plans with unlimited storage"
     */
    public List<SubscriptionPlan> searchPlans(String query, int limit) {
        // Use semantic search to find matching plans
        return searchService.search(query, "subscription-plan", limit);
    }
    
    /**
     * Get upgrade recommendations based on usage patterns
     */
    public List<SubscriptionPlan> getUpgradeRecommendations(UUID userId) {
        // Get user's behavior insights
        BehaviorInsights insights = behaviorService.analyzeUser(userId.toString());
        
        // Use AI to recommend plans based on usage patterns
        String prompt = String.format(
            "User current plan: %s. Usage patterns: %s. Recommend upgrade plans.",
            getCurrentPlan(userId).getName(),
            insights.getPatterns()
        );
        
        // Use LLM to generate recommendations
        String recommendationsJson = aiCoreService.generateText(prompt);
        
        // Parse and return recommended plans
        return parseRecommendations(recommendationsJson);
    }
    
    /**
     * Get churn risk for a subscription
     */
    public ChurnRiskResponse getChurnRisk(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));
        
        // Get behavior insights for user
        BehaviorInsights insights = behaviorService.analyzeUser(subscription.getUserId().toString());
        
        return ChurnRiskResponse.builder()
            .subscriptionId(subscriptionId)
            .churnRisk(insights.getChurn().getRisk())
            .churnReason(insights.getChurn().getReason())
            .sentiment(insights.getSentiment().getLabel())
            .trend(insights.getTrend())
            .recommendations(insights.getRecommendations())
            .build();
    }
    
    /**
     * Get all subscriptions at risk of churning
     */
    public List<Subscription> getAtRiskSubscriptions(double riskThreshold) {
        // Get all active subscriptions
        List<Subscription> activeSubscriptions = subscriptionRepository
            .findByStatus(SubscriptionStatus.ACTIVE);
        
        // Filter by churn risk
        return activeSubscriptions.stream()
            .filter(sub -> {
                BehaviorInsights insights = behaviorService.analyzeUser(sub.getUserId().toString());
                return insights.getChurn().getRisk() >= riskThreshold;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Track subscription event for behavior analysis
     */
    private void trackEvent(UUID userId, String eventType, Map<String, String> eventData) {
        // Create event for behavior analysis service
        ExternalEvent event = ExternalEvent.builder()
            .userId(userId.toString())
            .eventType(eventType)
            .timestamp(LocalDateTime.now())
            .eventData(eventData)
            .build();
        
        // Behavior service will process this event
        // (Implementation depends on your ExternalEventProvider)
    }
    
    /**
     * AI-powered address validation
     */
    private AddressValidationResult validateAddress(Address address) {
        String addressString = String.format("%s, %s, %s %s, %s",
            address.getStreetAddress(),
            address.getCity(),
            address.getState(),
            address.getPostalCode(),
            address.getCountry()
        );
        
        // Use LLM to validate address format and detect issues
        String prompt = String.format(
            "Validate this address and provide confidence score (0.0-1.0): %s",
            addressString
        );
        
        String validationResult = aiCoreService.generateText(prompt);
        
        // Parse validation result
        return parseValidationResult(validationResult);
    }
}
```

**Behavior Event Tracking:**
The app tracks all user actions as events for behavior analysis:
- `SUBSCRIBE` - When user subscribes
- `UNSUBSCRIBE` - When user cancels
- `UPGRADE` - When user upgrades plan
- `DOWNGRADE` - When user downgrades plan
- `UPDATE_ADDRESS` - When user updates address

These events feed into `BehaviorAnalysisService` to generate:
- **Churn Risk Scores** - Predict which users are likely to cancel
- **Sentiment Tracking** - Monitor user satisfaction over time
- **Trend Detection** - Identify improving/declining engagement
- **Recommendations** - Suggest optimal plans based on usage

**Implementation:** 3-4 weeks | **Value:** 20-35% reduction in churn, 15-25% increase in upgrades

---

### Recommended Implementation Order

1. **Week 1-2:** Smart FAQ Assistant (simplest, demonstrates core RAG)
2. **Week 3-4:** Document Intelligence Hub (builds on FAQ, adds document processing)
3. **Week 5-6:** Product Discovery Engine (different domain, adds behavior tracking)
4. **Week 7-8:** Team Sentiment Tracker (behavior module showcase)
5. **Week 9-10:** Code Documentation Search (specialized embeddings)
6. **Week 11-12:** Meeting Notes Analyzer (summarization patterns)
7. **Week 13-14:** Subscription Management Hub (action-based app with behavior analytics)

---

## Use Case Categories

> **Note:** The **6 Recommended Starter Apps** (above) are the best starting points for implementation. The use cases below are **extended examples** for additional inspiration across different industries.

| Category | Use Cases | Primary Capability | Complexity |
|----------|-----------|-------------------|------------|
| E-Commerce & Retail | 3 | Semantic Search, Recommendations | Medium |
| Healthcare & Life Sciences | 3 | RAG, PHI Detection, Compliance | High |
| Financial Services | 3 | Relationship Query, Fraud Detection | High |
| Education & Learning | 3 | RAG, Content Analysis | Medium |
| Legal & Compliance | 2 | Document Search, Compliance | High |
| HR & Talent Management | 2 | Semantic Matching, Analytics | Medium |
| Content & Media | 2 | Summarization, Moderation | Medium |
| Customer Success & Support | 2 | Behavior Analytics, RAG | Medium |
| Research & Analytics | 2 | Natural Language Queries, Analytics | Medium |

**Total: 22 extended use cases** (plus 7 recommended starter apps above)

---

## E-Commerce & Retail

### Use Case #1: Intelligent Product Discovery Platform

**Problem Statement:**
Online shoppers struggle to find products that match their intent. Traditional keyword search fails when users describe needs in natural language ("comfortable running shoes for flat feet" or "gift for tech-savvy teenager"). Retailers lose sales due to poor search experience.

**Solution Architecture:**
```
User Query (Natural Language)
    ↓
Semantic Search (ONNX Embeddings)
    ↓
Product Catalog (100K+ SKUs)
    ↓
Hybrid Ranking (Semantic + Business Rules)
    ↓
Personalized Results + Explanations
```

**Key Features:**
- Natural language product search
- "Similar products" recommendations
- "Why this product?" explanations
- Visual search (image-to-product matching)
- Price comparison across vendors
- Review sentiment analysis

**Required Backend Endpoints:**

```
POST   /api/products/search              - Semantic product search
POST   /api/products/similar              - Find similar products
GET    /api/products/{id}/recommendations - Personalized recommendations
POST   /api/products/visual-search        - Image-based search
GET    /api/products/{id}/explanation     - Why this product matches
GET    /api/products/trending             - Trending products (behavior analytics)
POST   /api/products/compare              - Compare multiple products
GET    /api/products/categories           - Category tree with semantic grouping
POST   /api/products/filters              - Dynamic filter suggestions
GET    /api/products/{id}/reviews         - Review sentiment analysis
```

**Database Schema:**
```sql
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR UNIQUE,
    title VARCHAR,
    description CLOB,
    category_id UUID,
    price DECIMAL,
    embedding BLOB,  -- ONNX vector
    image_urls ARRAY,
    attributes JSON,  -- size, color, material, etc.
    vendor_id UUID,
    stock_qty INT,
    created_at TIMESTAMP
);

CREATE TABLE product_categories (
    id UUID PRIMARY KEY,
    name VARCHAR,
    parent_id UUID,
    embedding BLOB,  -- Semantic category representation
    created_at TIMESTAMP
);

CREATE TABLE user_searches (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    results_count INT,
    clicked_product_id UUID,
    conversion BOOLEAN,
    timestamp TIMESTAMP
);

CREATE TABLE product_recommendations (
    id UUID PRIMARY KEY,
    user_id UUID,
    product_id UUID,
    recommendation_type VARCHAR,  -- 'similar', 'collaborative', 'trending'
    score DECIMAL,
    reason TEXT,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Semantic Search, Embeddings)
- Web (REST API endpoints)
- Behavior (User search patterns, conversion tracking)

**Implementation Complexity:** Medium (3-4 weeks)
**Business Value:** 30-50% increase in search conversion, 20-35% revenue lift

---

### Use Case #2: Smart Inventory Management System

**Problem Statement:**
Retailers struggle with inventory optimization. They need to predict demand, identify slow-moving items, suggest reorder points, and understand product relationships (complementary items, substitutes). Manual analysis is time-consuming and error-prone.

**Solution Architecture:**
```
Inventory Data + Sales History
    ↓
Behavior Analytics (Demand Patterns)
    ↓
Natural Language Queries ("Which products need reordering?")
    ↓
AI-Powered Insights + Recommendations
    ↓
Automated Alerts + Action Plans
```

**Key Features:**
- Natural language inventory queries
- Demand forecasting using behavior analytics
- Automatic reorder point suggestions
- Product relationship discovery (complementary/substitute)
- Anomaly detection (unusual sales patterns)
- Multi-warehouse optimization

**Required Backend Endpoints:**

```
POST   /api/inventory/query               - Natural language inventory queries
GET    /api/inventory/low-stock            - Products below reorder point
GET    /api/inventory/forecast             - Demand forecasting
POST   /api/inventory/relationships        - Find product relationships
GET    /api/inventory/anomalies           - Unusual patterns detection
POST   /api/inventory/reorder-suggestions - AI-powered reorder recommendations
GET    /api/inventory/{id}/analytics       - Product-level analytics
POST   /api/inventory/optimize             - Multi-warehouse optimization
GET    /api/inventory/trends               - Sales trend analysis
POST   /api/inventory/alerts               - Configure alert rules
```

**Database Schema:**
```sql
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    product_id UUID,
    warehouse_id UUID,
    current_stock INT,
    reorder_point INT,
    max_stock INT,
    unit_cost DECIMAL,
    last_restocked_at TIMESTAMP,
    created_at TIMESTAMP
);

CREATE TABLE sales_transactions (
    id UUID PRIMARY KEY,
    product_id UUID,
    quantity INT,
    sale_price DECIMAL,
    sale_date DATE,
    customer_segment VARCHAR,
    created_at TIMESTAMP
);

CREATE TABLE inventory_insights (
    id UUID PRIMARY KEY,
    product_id UUID,
    insight_type VARCHAR,  -- 'demand_forecast', 'reorder_suggestion', 'anomaly'
    insight_data JSON,
    confidence DECIMAL,
    generated_at TIMESTAMP
);

CREATE TABLE product_relationships (
    id UUID PRIMARY KEY,
    product_a_id UUID,
    product_b_id UUID,
    relationship_type VARCHAR,  -- 'complementary', 'substitute', 'bundle'
    strength DECIMAL,
    discovered_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Semantic Search for product matching)
- Relationship Query (Natural language inventory queries)
- Behavior (Sales pattern analysis, demand forecasting)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 15-25% reduction in stockouts, 10-20% reduction in excess inventory

---

### Use Case #3: Personalized Shopping Assistant

**Problem Statement:**
Shoppers need help making purchase decisions. They want personalized recommendations, price drop alerts, style matching, and shopping list management. Current solutions are generic and don't understand user preferences deeply.

**Solution Architecture:**
```
User Profile + Purchase History
    ↓
Behavior Analytics (Preferences, Patterns)
    ↓
Semantic Product Matching
    ↓
Personalized Recommendations + Shopping Lists
    ↓
Price Tracking + Alerts
```

**Key Features:**
- Personalized product recommendations
- Shopping list with smart suggestions
- Price drop alerts
- Style matching ("find items that match this")
- Budget tracking and suggestions
- Gift recommendations

**Required Backend Endpoints:**

```
GET    /api/assistant/recommendations      - Personalized recommendations
POST   /api/assistant/shopping-list       - Create/manage shopping lists
GET    /api/assistant/shopping-list/{id}   - Get shopping list with suggestions
POST   /api/assistant/price-alerts         - Set price drop alerts
GET    /api/assistant/alerts               - Get active alerts
POST   /api/assistant/style-match          - Find items matching style/image
GET    /api/assistant/budget               - Budget tracking and suggestions
POST   /api/assistant/gift-suggestions     - Gift recommendations
GET    /api/assistant/preferences          - User preference insights
POST   /api/assistant/feedback             - Feedback on recommendations
```

**Database Schema:**
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE,
    preferences JSON,  -- style, budget, categories
    embedding BLOB,  -- User preference vector
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE shopping_lists (
    id UUID PRIMARY KEY,
    user_id UUID,
    name VARCHAR,
    items JSON,  -- Array of product IDs
    suggested_items JSON,  -- AI-suggested additions
    created_at TIMESTAMP
);

CREATE TABLE price_alerts (
    id UUID PRIMARY KEY,
    user_id UUID,
    product_id UUID,
    target_price DECIMAL,
    current_price DECIMAL,
    alert_sent BOOLEAN,
    created_at TIMESTAMP
);

CREATE TABLE recommendation_feedback (
    id UUID PRIMARY KEY,
    user_id UUID,
    product_id UUID,
    recommendation_id UUID,
    feedback_type VARCHAR,  -- 'liked', 'purchased', 'dismissed'
    timestamp TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Semantic Search, Embeddings)
- Behavior (User preference learning, pattern recognition)
- Web (REST API)

**Implementation Complexity:** Medium (3-4 weeks)
**Business Value:** 25-40% increase in user engagement, 15-25% conversion improvement

---

## Healthcare & Life Sciences

### Use Case #4: Clinical Decision Support System

**Problem Statement:**
Healthcare providers need instant access to relevant medical literature, treatment guidelines, and drug information when making clinical decisions. Current systems require manual searching through multiple databases, leading to delayed decisions and potential errors.

**Solution Architecture:**
```
Clinical Query (Natural Language)
    ↓
RAG System (Medical Literature + Guidelines)
    ↓
PHI Detection & Redaction
    ↓
Context-Aware Answers with Citations
    ↓
Drug Interaction Checks
```

**Key Features:**
- Natural language clinical queries
- RAG-powered answers from medical literature
- Drug interaction checking
- Treatment guideline recommendations
- Patient-specific risk assessment
- HIPAA-compliant data handling

**Required Backend Endpoints:**

```
POST   /api/clinical/query                - Natural language clinical queries
POST   /api/clinical/drug-interactions    - Check drug interactions
GET    /api/clinical/guidelines            - Treatment guidelines search
POST   /api/clinical/risk-assessment       - Patient risk assessment
GET    /api/clinical/literature            - Medical literature search
POST   /api/clinical/symptoms              - Symptom analysis and suggestions
GET    /api/clinical/drug-info             - Drug information lookup
POST   /api/clinical/diagnosis-support      - Diagnosis support suggestions
GET    /api/clinical/patient-history       - Patient history search (HIPAA-compliant)
POST   /api/clinical/audit-log             - Access audit logging
```

**Database Schema:**
```sql
CREATE TABLE medical_literature (
    id UUID PRIMARY KEY,
    title VARCHAR,
    content CLOB,
    document_type VARCHAR,  -- 'research_paper', 'guideline', 'textbook'
    specialty VARCHAR,
    embedding BLOB,  -- ONNX vector
    citations ARRAY,
    published_date DATE,
    created_at TIMESTAMP
);

CREATE TABLE drug_information (
    id UUID PRIMARY KEY,
    drug_name VARCHAR,
    generic_name VARCHAR,
    interactions JSON,  -- List of interacting drugs
    contraindications JSON,
    dosage_guidelines JSON,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE clinical_queries (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    redacted_query VARCHAR,  -- PHI removed
    response_text CLOB,
    sources ARRAY,  -- Literature IDs
    confidence DECIMAL,
    created_at TIMESTAMP
);

CREATE TABLE access_audit (
    id UUID PRIMARY KEY,
    user_id UUID,
    patient_id UUID,  -- Encrypted
    action VARCHAR,  -- 'query', 'view', 'export'
    resource_type VARCHAR,
    resource_id UUID,
    timestamp TIMESTAMP,
    ip_address VARCHAR
);
```

**AI Fabric Modules Used:**
- Core (RAG, Semantic Search, PHI Detection)
- Relationship Query (Complex medical queries)
- Web (REST API)
- Privacy/Compliance (HIPAA)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 40-60% reduction in time to find relevant information, improved patient outcomes

---

### Use Case #5: Medical Research Literature Platform

**Problem Statement:**
Researchers and clinicians struggle to keep up with the volume of medical research publications. They need intelligent search, summarization, and relationship discovery between studies. Current tools are keyword-based and miss semantic connections.

**Solution Architecture:**
```
Research Paper Upload/Ingestion
    ↓
Automatic Summarization (Multi-level)
    ↓
Semantic Indexing (ONNX Embeddings)
    ↓
Relationship Discovery (Citations, Similar Studies)
    ↓
Intelligent Search + RAG Q&A
```

**Key Features:**
- Automatic paper summarization (1-sentence, executive, full)
- Semantic search across millions of papers
- Citation network visualization
- Similar study discovery
- Research question answering (RAG)
- Trend analysis (what's hot in research)

**Required Backend Endpoints:**

```
POST   /api/research/upload                - Upload research paper
GET    /api/research/{id}/summary           - Get multi-level summaries
POST   /api/research/search                 - Semantic paper search
GET    /api/research/{id}/similar           - Find similar studies
GET    /api/research/{id}/citations         - Citation network
POST   /api/research/query                  - RAG-powered Q&A
GET    /api/research/trends                 - Research trend analysis
GET    /api/research/authors                - Author network and collaboration
POST   /api/research/compare                - Compare multiple papers
GET    /api/research/recommendations        - Personalized paper recommendations
```

**Database Schema:**
```sql
CREATE TABLE research_papers (
    id UUID PRIMARY KEY,
    title VARCHAR,
    abstract CLOB,
    full_text CLOB,
    authors ARRAY,
    journal VARCHAR,
    publication_date DATE,
    doi VARCHAR,
    embedding BLOB,  -- ONNX vector
    created_at TIMESTAMP
);

CREATE TABLE paper_summaries (
    id UUID PRIMARY KEY,
    paper_id UUID,
    summary_level VARCHAR,  -- 'one_liner', 'executive', 'medium', 'full'
    summary_text CLOB,
    key_points ARRAY,
    generated_at TIMESTAMP
);

CREATE TABLE paper_relationships (
    id UUID PRIMARY KEY,
    source_paper_id UUID,
    target_paper_id UUID,
    relationship_type VARCHAR,  -- 'cites', 'similar', 'contradicts', 'extends'
    strength DECIMAL,
    discovered_at TIMESTAMP
);

CREATE TABLE research_queries (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    results_count INT,
    clicked_paper_id UUID,
    timestamp TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, Summarization, Semantic Search)
- Relationship Query (Complex research queries)
- Web (REST API)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 50-70% time savings in literature review, better research insights

---

### Use Case #6: Patient Symptom Checker & Triage System

**Problem Statement:**
Patients need help understanding their symptoms and determining when to seek medical care. Healthcare systems need to triage patients efficiently. Current symptom checkers are rule-based and lack the nuance of medical knowledge.

**Solution Architecture:**
```
Patient Symptom Input (Natural Language)
    ↓
PHI Detection & Redaction
    ↓
RAG System (Medical Knowledge Base)
    ↓
Symptom Analysis + Risk Assessment
    ↓
Triage Recommendation + Next Steps
```

**Key Features:**
- Natural language symptom input
- Symptom analysis and risk assessment
- Triage recommendations (urgent, routine, self-care)
- Educational content delivery
- Follow-up question generation
- Integration with appointment scheduling

**Required Backend Endpoints:**

```
POST   /api/symptoms/analyze               - Analyze symptoms and provide assessment
POST   /api/symptoms/triage                 - Triage recommendation
GET    /api/symptoms/conditions              - Possible conditions matching symptoms
POST   /api/symptoms/follow-up-questions     - Generate follow-up questions
GET    /api/symptoms/education               - Educational content for conditions
POST   /api/symptoms/appointment-suggest    - Suggest appointment urgency
GET    /api/symptoms/history                 - Patient symptom history (PHI-protected)
POST   /api/symptoms/feedback                - Feedback on recommendations
```

**Database Schema:**
```sql
CREATE TABLE symptom_assessments (
    id UUID PRIMARY KEY,
    patient_id UUID,  -- Encrypted
    symptoms_input CLOB,
    redacted_input CLOB,  -- PHI removed
    possible_conditions JSON,
    risk_level VARCHAR,  -- 'low', 'medium', 'high', 'urgent'
    triage_recommendation VARCHAR,
    educational_content JSON,
    created_at TIMESTAMP
);

CREATE TABLE medical_conditions (
    id UUID PRIMARY KEY,
    condition_name VARCHAR,
    description CLOB,
    symptoms ARRAY,
    risk_factors ARRAY,
    treatment_guidelines JSON,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE symptom_patterns (
    id UUID PRIMARY KEY,
    symptom_combination JSON,
    associated_conditions ARRAY,
    urgency_score DECIMAL,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, PHI Detection)
- Behavior (Symptom pattern learning)
- Web (REST API)
- Privacy/Compliance (HIPAA)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 30-50% reduction in unnecessary ER visits, improved patient satisfaction

---

## Financial Services

### Use Case #7: Intelligent Financial Advisor Platform

**Problem Statement:**
Financial advisors and clients need to query complex financial data using natural language. They want insights on portfolios, market trends, risk analysis, and investment recommendations. Current systems require SQL knowledge or complex interfaces.

**Solution Architecture:**
```
Natural Language Financial Query
    ↓
Relationship Query (Portfolio, Transactions, Market Data)
    ↓
RAG System (Financial Knowledge Base)
    ↓
Risk Analysis + Recommendations
    ↓
Personalized Insights
```

**Key Features:**
- Natural language financial queries
- Portfolio analysis and recommendations
- Market trend analysis
- Risk assessment
- Investment opportunity discovery
- Regulatory compliance checking

**Required Backend Endpoints:**

```
POST   /api/finance/query                   - Natural language financial queries
GET    /api/finance/portfolio/{id}/analysis  - Portfolio analysis
POST   /api/finance/risk-assessment         - Risk assessment
GET    /api/finance/market-trends            - Market trend analysis
POST   /api/finance/investment-opportunities - Find investment opportunities
GET    /api/finance/transactions             - Transaction search and analysis
POST   /api/finance/compliance-check         - Regulatory compliance checking
GET    /api/finance/recommendations          - Personalized investment recommendations
POST   /api/finance/alerts                   - Set up financial alerts
GET    /api/finance/reports                  - Generate financial reports
```

**Database Schema:**
```sql
CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    client_id UUID,
    portfolio_name VARCHAR,
    holdings JSON,  -- Array of {asset_id, quantity, purchase_price}
    total_value DECIMAL,
    risk_profile VARCHAR,
    created_at TIMESTAMP
);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY,
    portfolio_id UUID,
    transaction_type VARCHAR,  -- 'buy', 'sell', 'dividend'
    asset_id UUID,
    quantity DECIMAL,
    price DECIMAL,
    transaction_date DATE,
    created_at TIMESTAMP
);

CREATE TABLE market_data (
    id UUID PRIMARY KEY,
    asset_id UUID,
    symbol VARCHAR,
    price DECIMAL,
    market_cap DECIMAL,
    sector VARCHAR,
    embedding BLOB,  -- For semantic asset matching
    date DATE,
    created_at TIMESTAMP
);

CREATE TABLE financial_queries (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    query_type VARCHAR,  -- 'portfolio', 'market', 'risk', 'compliance'
    results JSON,
    executed_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Relationship Query (Complex financial queries)
- Core (RAG for financial knowledge)
- Behavior (Investment pattern analysis)
- Web (REST API)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 60-80% reduction in query time, better investment decisions

---

### Use Case #8: Fraud Detection & Prevention System

**Problem Statement:**
Financial institutions need to detect fraudulent transactions in real-time. Fraud patterns are complex and evolve constantly. Traditional rule-based systems miss sophisticated fraud schemes and generate too many false positives.

**Solution Architecture:**
```
Transaction Data
    ↓
Behavior Analytics (Pattern Detection)
    ↓
Relationship Query (Account Relationships, Transaction Networks)
    ↓
Anomaly Detection + Risk Scoring
    ↓
Real-time Alerts + Investigation Tools
```

**Key Features:**
- Real-time fraud detection
- Transaction pattern analysis
- Account relationship mapping
- Anomaly detection
- Natural language fraud queries
- Investigation workflow support

**Required Backend Endpoints:**

```
POST   /api/fraud/analyze                   - Analyze transaction for fraud
GET    /api/fraud/suspicious-transactions    - Get suspicious transactions
POST   /api/fraud/query                     - Natural language fraud queries
GET    /api/fraud/account/{id}/risk          - Account risk assessment
POST   /api/fraud/relationships              - Map account relationships
GET    /api/fraud/patterns                   - Fraud pattern analysis
POST   /api/fraud/investigate                - Start fraud investigation
GET    /api/fraud/cases                      - Fraud case management
POST   /api/fraud/rules                      - Configure fraud detection rules
GET    /api/fraud/analytics                  - Fraud analytics dashboard
```

**Database Schema:**
```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID,
    transaction_type VARCHAR,
    amount DECIMAL,
    merchant_id UUID,
    location VARCHAR,
    timestamp TIMESTAMP,
    fraud_score DECIMAL,
    flagged BOOLEAN,
    created_at TIMESTAMP
);

CREATE TABLE fraud_cases (
    id UUID PRIMARY KEY,
    case_number VARCHAR UNIQUE,
    transaction_ids ARRAY,
    account_id UUID,
    fraud_type VARCHAR,
    risk_score DECIMAL,
    status VARCHAR,  -- 'open', 'investigating', 'resolved', 'false_positive'
    investigator_id UUID,
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE account_relationships (
    id UUID PRIMARY KEY,
    account_a_id UUID,
    account_b_id UUID,
    relationship_type VARCHAR,  -- 'same_owner', 'transfer_pattern', 'suspicious_link'
    strength DECIMAL,
    discovered_at TIMESTAMP
);

CREATE TABLE fraud_patterns (
    id UUID PRIMARY KEY,
    pattern_name VARCHAR,
    pattern_description CLOB,
    indicators JSON,
    risk_level VARCHAR,
    discovered_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Relationship Query (Complex fraud queries)
- Behavior (Pattern detection, anomaly detection)
- Core (Semantic analysis)
- Web (REST API)

**Implementation Complexity:** High (6-7 weeks)
**Business Value:** 40-60% reduction in fraud losses, 30-50% reduction in false positives

---

### Use Case #9: Credit Risk Assessment Platform

**Problem Statement:**
Lenders need to assess credit risk accurately and quickly. Traditional credit scoring models are static and don't adapt to changing economic conditions or individual circumstances. They need dynamic, explainable risk assessments.

**Solution Architecture:**
```
Applicant Data + Credit History
    ↓
Behavior Analytics (Spending Patterns, Payment History)
    ↓
RAG System (Credit Policy, Regulations)
    ↓
Risk Scoring + Explanation
    ↓
Recommendation Engine
```

**Key Features:**
- Dynamic credit risk assessment
- Explainable risk scores
- Natural language credit queries
- Policy compliance checking
- Alternative data integration
- Personalized loan recommendations

**Required Backend Endpoints:**

```
POST   /api/credit/assess                   - Assess credit risk
GET    /api/credit/{id}/score               - Get credit score with explanation
POST   /api/credit/query                    - Natural language credit queries
GET    /api/credit/policy-check             - Check policy compliance
POST   /api/credit/recommendations          - Loan product recommendations
GET    /api/credit/trends                   - Credit risk trends
POST   /api/credit/alternative-data         - Integrate alternative data sources
GET    /api/credit/reports                  - Generate credit reports
POST   /api/credit/appeal                   - Credit decision appeal process
GET    /api/credit/analytics                - Credit portfolio analytics
```

**Database Schema:**
```sql
CREATE TABLE credit_applications (
    id UUID PRIMARY KEY,
    applicant_id UUID,
    application_type VARCHAR,  -- 'personal_loan', 'credit_card', 'mortgage'
    requested_amount DECIMAL,
    application_data JSON,
    credit_score DECIMAL,
    risk_level VARCHAR,
    decision VARCHAR,  -- 'approved', 'rejected', 'pending'
    decision_reason TEXT,
    created_at TIMESTAMP
);

CREATE TABLE credit_history (
    id UUID PRIMARY KEY,
    applicant_id UUID,
    account_type VARCHAR,
    payment_history JSON,  -- Array of payment records
    outstanding_balance DECIMAL,
    credit_utilization DECIMAL,
    behavior_insights JSON,  -- From behavior module
    created_at TIMESTAMP
);

CREATE TABLE credit_policies (
    id UUID PRIMARY KEY,
    policy_name VARCHAR,
    policy_text CLOB,
    rules JSON,
    embedding BLOB,  -- For semantic policy matching
    effective_date DATE,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG for policy understanding)
- Behavior (Payment pattern analysis)
- Relationship Query (Complex credit queries)
- Web (REST API)

**Implementation Complexity:** High (6-7 weeks)
**Business Value:** 20-30% improvement in risk prediction accuracy, faster loan processing

---

## Education & Learning

### Use Case #10: Intelligent Learning Management System

**Problem Statement:**
Educational institutions need systems that help students find relevant learning materials, answer questions, and track progress. Current LMS platforms are static and don't adapt to individual learning styles or provide intelligent content recommendations.

**Solution Architecture:**
```
Student Query (Natural Language)
    ↓
RAG System (Course Materials, Textbooks, Notes)
    ↓
Behavior Analytics (Learning Patterns)
    ↓
Personalized Content Recommendations
    ↓
Progress Tracking + Insights
```

**Key Features:**
- Natural language course material search
- Intelligent Q&A from course content
- Personalized learning path recommendations
- Learning progress analytics
- Study group matching
- Assignment help and explanations

**Required Backend Endpoints:**

```
POST   /api/learning/search                - Search course materials
POST   /api/learning/query                  - Ask questions about course content
GET    /api/learning/recommendations        - Personalized content recommendations
GET    /api/learning/progress               - Learning progress tracking
POST   /api/learning/study-groups           - Find study group matches
GET    /api/learning/assignments            - Assignment help and explanations
POST   /api/learning/notes                 - Generate study notes from materials
GET    /api/learning/analytics              - Learning analytics dashboard
POST   /api/learning/feedback               - Feedback on learning materials
GET    /api/learning/path                   - Personalized learning path
```

**Database Schema:**
```sql
CREATE TABLE course_materials (
    id UUID PRIMARY KEY,
    course_id UUID,
    material_type VARCHAR,  -- 'lecture', 'textbook', 'assignment', 'video'
    title VARCHAR,
    content CLOB,
    embedding BLOB,
    tags ARRAY,
    created_at TIMESTAMP
);

CREATE TABLE student_progress (
    id UUID PRIMARY KEY,
    student_id UUID,
    course_id UUID,
    material_id UUID,
    completion_status VARCHAR,
    time_spent_minutes INT,
    quiz_scores JSON,
    behavior_insights JSON,
    created_at TIMESTAMP
);

CREATE TABLE learning_paths (
    id UUID PRIMARY KEY,
    student_id UUID,
    course_id UUID,
    recommended_materials ARRAY,
    learning_style VARCHAR,
    difficulty_level VARCHAR,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, Semantic Search)
- Behavior (Learning pattern analysis)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 25-40% improvement in student engagement, better learning outcomes

---

### Use Case #11: Research Paper Writing Assistant

**Problem Statement:**
Students and researchers struggle with writing research papers. They need help with literature review, citation management, writing suggestions, and plagiarism checking. Current tools are fragmented and don't provide intelligent assistance.

**Solution Architecture:**
```
Research Paper Draft
    ↓
Literature Review (RAG from Academic Databases)
    ↓
Citation Suggestions + Management
    ↓
Writing Quality Analysis
    ↓
Plagiarism Detection
    ↓
Improvement Suggestions
```

**Key Features:**
- Intelligent literature review assistance
- Automatic citation suggestions
- Writing quality analysis
- Plagiarism detection
- Research question answering
- Paper structure recommendations

**Required Backend Endpoints:**

```
POST   /api/writing/analyze                 - Analyze paper quality
POST   /api/writing/literature-review       - Find relevant literature
GET    /api/writing/citations                - Citation suggestions
POST   /api/writing/plagiarism-check        - Plagiarism detection
POST   /api/writing/suggestions              - Writing improvement suggestions
GET    /api/writing/structure               - Paper structure recommendations
POST   /api/writing/summarize               - Summarize research papers
GET    /api/writing/related-papers          - Find related research papers
POST   /api/writing/feedback                - Get feedback on specific sections
```

**Database Schema:**
```sql
CREATE TABLE research_drafts (
    id UUID PRIMARY KEY,
    user_id UUID,
    title VARCHAR,
    content CLOB,
    sections JSON,
    citations ARRAY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE academic_sources (
    id UUID PRIMARY KEY,
    title VARCHAR,
    authors ARRAY,
    abstract CLOB,
    full_text CLOB,
    doi VARCHAR,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE writing_analyses (
    id UUID PRIMARY KEY,
    draft_id UUID,
    analysis_type VARCHAR,  -- 'quality', 'plagiarism', 'structure'
    results JSON,
    suggestions ARRAY,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, Summarization, Semantic Search)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 40-60% time savings in literature review, improved paper quality

---

### Use Case #12: Adaptive Exam Preparation Platform

**Problem Statement:**
Students preparing for standardized tests need personalized study plans, practice questions, and performance insights. Current platforms offer generic content and don't adapt to individual weaknesses or learning pace.

**Solution Architecture:**
```
Student Performance Data
    ↓
Behavior Analytics (Weak Areas, Learning Pace)
    ↓
Semantic Question Matching (Practice Questions)
    ↓
Personalized Study Plan
    ↓
Progress Tracking + Recommendations
```

**Key Features:**
- Personalized study plan generation
- Adaptive practice questions
- Weak area identification
- Performance analytics
- Study schedule optimization
- Exam simulation and scoring

**Required Backend Endpoints:**

```
GET    /api/exam/study-plan                 - Generate personalized study plan
GET    /api/exam/practice-questions         - Get adaptive practice questions
GET    /api/exam/weak-areas                 - Identify weak areas
GET    /api/exam/performance                - Performance analytics
POST   /api/exam/simulate                    - Take exam simulation
GET    /api/exam/recommendations             - Study recommendations
GET    /api/exam/schedule                    - Optimized study schedule
POST   /api/exam/feedback                    - Feedback on practice questions
GET    /api/exam/progress                    - Track study progress
```

**Database Schema:**
```sql
CREATE TABLE exam_questions (
    id UUID PRIMARY KEY,
    exam_type VARCHAR,  -- 'SAT', 'GRE', 'MCAT', etc.
    subject VARCHAR,
    question_text CLOB,
    answer_options JSON,
    correct_answer VARCHAR,
    difficulty_level VARCHAR,
    topic_tags ARRAY,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE student_performance (
    id UUID PRIMARY KEY,
    student_id UUID,
    question_id UUID,
    answered_correctly BOOLEAN,
    time_taken_seconds INT,
    attempt_number INT,
    timestamp TIMESTAMP
);

CREATE TABLE study_plans (
    id UUID PRIMARY KEY,
    student_id UUID,
    exam_type VARCHAR,
    plan_data JSON,  -- Study schedule, topics, goals
    weak_areas ARRAY,
    target_score DECIMAL,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Semantic Search for question matching)
- Behavior (Performance pattern analysis)
- Web (REST API)

**Implementation Complexity:** Medium (3-4 weeks)
**Business Value:** 20-35% improvement in exam scores, better study efficiency

---

## Legal & Compliance

### Use Case #13: Legal Research & Case Law Platform

**Problem Statement:**
Lawyers spend significant time researching case law, statutes, and legal precedents. Current legal research tools are expensive and keyword-based, missing semantic connections between cases. They need intelligent search and Q&A capabilities.

**Solution Architecture:**
```
Legal Query (Natural Language)
    ↓
RAG System (Case Law, Statutes, Precedents)
    ↓
Semantic Search + Citation Network
    ↓
Case Similarity Analysis
    ↓
Comprehensive Legal Answers with Citations
```

**Key Features:**
- Natural language legal research queries
- Case law semantic search
- Citation network visualization
- Similar case discovery
- Legal Q&A with citations
- Statute and regulation search
- Case outcome prediction

**Required Backend Endpoints:**

```
POST   /api/legal/search                    - Semantic legal document search
POST   /api/legal/query                     - Natural language legal Q&A
GET    /api/legal/case/{id}/similar         - Find similar cases
GET    /api/legal/case/{id}/citations       - Citation network
POST   /api/legal/statutes                  - Search statutes and regulations
GET    /api/legal/precedents                - Find relevant precedents
POST   /api/legal/analyze                   - Analyze case outcomes
GET    /api/legal/trends                    - Legal trend analysis
POST   /api/legal/compare                    - Compare multiple cases
GET    /api/legal/jurisdiction               - Filter by jurisdiction
```

**Database Schema:**
```sql
CREATE TABLE legal_cases (
    id UUID PRIMARY KEY,
    case_name VARCHAR,
    case_number VARCHAR,
    court VARCHAR,
    jurisdiction VARCHAR,
    decision_date DATE,
    case_text CLOB,
    holding CLOB,
    citations ARRAY,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE statutes (
    id UUID PRIMARY KEY,
    statute_name VARCHAR,
    section_number VARCHAR,
    content CLOB,
    jurisdiction VARCHAR,
    effective_date DATE,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE case_relationships (
    id UUID PRIMARY KEY,
    source_case_id UUID,
    target_case_id UUID,
    relationship_type VARCHAR,  -- 'cites', 'distinguishes', 'overrules', 'follows'
    strength DECIMAL,
    discovered_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, Semantic Search)
- Relationship Query (Complex legal queries)
- Web (REST API)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 50-70% reduction in research time, better case preparation

---

### Use Case #14: Compliance Monitoring & Reporting System

**Problem Statement:**
Organizations need to monitor compliance with regulations (GDPR, HIPAA, SOX, etc.) across their operations. They need to query policies, detect violations, generate reports, and answer compliance questions. Current systems are manual and error-prone.

**Solution Architecture:**
```
Compliance Query (Natural Language)
    ↓
RAG System (Regulations, Policies, Procedures)
    ↓
Data Scanning (PII/PHI Detection)
    ↓
Compliance Checking + Violation Detection
    ↓
Automated Reporting
```

**Key Features:**
- Natural language compliance queries
- Automated policy compliance checking
- PII/PHI detection and reporting
- Violation detection and alerts
- Compliance report generation
- Regulatory change tracking

**Required Backend Endpoints:**

```
POST   /api/compliance/query                 - Natural language compliance queries
POST   /api/compliance/check                  - Check compliance status
GET    /api/compliance/violations             - Get compliance violations
POST   /api/compliance/scan                   - Scan data for PII/PHI
GET    /api/compliance/reports                 - Generate compliance reports
POST   /api/compliance/policies                - Search policies and regulations
GET    /api/compliance/alerts                  - Compliance alerts
POST   /api/compliance/audit                   - Compliance audit logging
GET    /api/compliance/trends                  - Compliance trend analysis
POST   /api/compliance/remediation             - Remediation recommendations
```

**Database Schema:**
```sql
CREATE TABLE compliance_policies (
    id UUID PRIMARY KEY,
    policy_name VARCHAR,
    regulation_type VARCHAR,  -- 'GDPR', 'HIPAA', 'SOX'
    policy_text CLOB,
    requirements JSON,
    embedding BLOB,
    effective_date DATE,
    created_at TIMESTAMP
);

CREATE TABLE compliance_checks (
    id UUID PRIMARY KEY,
    check_type VARCHAR,
    entity_type VARCHAR,
    entity_id UUID,
    policy_id UUID,
    compliance_status VARCHAR,  -- 'compliant', 'violation', 'warning'
    findings JSON,
    checked_at TIMESTAMP
);

CREATE TABLE compliance_violations (
    id UUID PRIMARY KEY,
    violation_type VARCHAR,
    severity VARCHAR,  -- 'low', 'medium', 'high', 'critical'
    entity_id UUID,
    policy_id UUID,
    description CLOB,
    remediation_steps JSON,
    status VARCHAR,  -- 'open', 'in_progress', 'resolved'
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, PII/PHI Detection)
- Relationship Query (Complex compliance queries)
- Web (REST API)
- Privacy/Compliance (GDPR, HIPAA)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 60-80% reduction in compliance audit time, reduced violation risk

---

## HR & Talent Management

### Use Case #15: Intelligent Talent Acquisition Platform

**Problem Statement:**
HR teams struggle to find the right candidates, match skills effectively, and reduce time-to-hire. Traditional ATS systems use keyword matching and miss qualified candidates. They need semantic understanding of skills and experience.

**Solution Architecture:**
```
Job Posting + Candidate Resumes
    ↓
Semantic Matching (Skills, Experience, Culture Fit)
    ↓
Behavior Analytics (Candidate Engagement)
    ↓
Ranked Recommendations + Explanations
    ↓
Interview Scheduling + Feedback
```

**Key Features:**
- Semantic resume-job matching
- Skills gap analysis
- Culture fit assessment
- Candidate ranking with explanations
- Interview question generation
- Candidate pipeline analytics

**Required Backend Endpoints:**

```
POST   /api/talent/match                    - Match candidates to jobs
GET    /api/talent/candidates               - Search and filter candidates
POST   /api/talent/skills-gap               - Analyze skills gap
GET    /api/talent/rankings                  - Get candidate rankings
POST   /api/talent/interview-questions       - Generate interview questions
GET    /api/talent/pipeline                  - Candidate pipeline analytics
POST   /api/talent/feedback                  - Collect interview feedback
GET    /api/talent/trends                    - Hiring trends analysis
POST   /api/talent/assessments               - Candidate assessment tools
GET    /api/talent/recommendations           - Personalized candidate recommendations
```

**Database Schema:**
```sql
CREATE TABLE job_postings (
    id UUID PRIMARY KEY,
    title VARCHAR,
    description CLOB,
    required_skills ARRAY,
    preferred_skills ARRAY,
    experience_years INT,
    location VARCHAR,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE candidates (
    id UUID PRIMARY KEY,
    resume_text CLOB,
    extracted_skills ARRAY,
    years_experience INT,
    education JSON,
    certifications ARRAY,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE candidate_matches (
    id UUID PRIMARY KEY,
    candidate_id UUID,
    job_id UUID,
    match_score DECIMAL,
    skills_match JSON,
    experience_match DECIMAL,
    culture_fit_score DECIMAL,
    reasoning TEXT,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Semantic Search, Embeddings)
- Behavior (Candidate engagement tracking)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 30-50% reduction in time-to-hire, better candidate quality

---

### Use Case #16: Employee Performance Analytics Platform

**Problem Statement:**
Organizations need to understand employee performance, identify high performers, detect burnout risks, and provide personalized development recommendations. Current systems are reactive and don't provide predictive insights.

**Solution Architecture:**
```
Employee Data + Performance Reviews
    ↓
Behavior Analytics (Work Patterns, Engagement)
    ↓
Natural Language Performance Queries
    ↓
Insights + Predictions (Burnout, Promotion Readiness)
    ↓
Personalized Development Recommendations
```

**Key Features:**
- Natural language performance queries
- Burnout risk prediction
- Promotion readiness assessment
- Skill gap identification
- Team performance analytics
- Personalized development plans

**Required Backend Endpoints:**

```
POST   /api/performance/query                - Natural language performance queries
GET    /api/performance/{employee_id}         - Employee performance insights
GET    /api/performance/burnout-risk          - Burnout risk assessment
GET    /api/performance/promotion-readiness   - Promotion readiness analysis
GET    /api/performance/skills-gap           - Identify skills gaps
GET    /api/performance/team                  - Team performance analytics
POST   /api/performance/development-plan      - Generate development plan
GET    /api/performance/trends                - Performance trends
POST   /api/performance/feedback              - Performance feedback
GET    /api/performance/benchmarks            - Performance benchmarks
```

**Database Schema:**
```sql
CREATE TABLE employees (
    id UUID PRIMARY KEY,
    employee_id VARCHAR UNIQUE,
    name VARCHAR,
    role VARCHAR,
    department VARCHAR,
    hire_date DATE,
    performance_data JSON,
    embedding BLOB,  -- For semantic role matching
    created_at TIMESTAMP
);

CREATE TABLE performance_reviews (
    id UUID PRIMARY KEY,
    employee_id UUID,
    review_period VARCHAR,
    review_data JSON,
    ratings JSON,
    feedback CLOB,
    reviewer_id UUID,
    review_date DATE,
    created_at TIMESTAMP
);

CREATE TABLE performance_insights (
    id UUID PRIMARY KEY,
    employee_id UUID,
    insight_type VARCHAR,  -- 'burnout_risk', 'promotion_readiness', 'skills_gap'
    insight_data JSON,
    confidence DECIMAL,
    generated_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Semantic Analysis)
- Behavior (Work pattern analysis, burnout prediction)
- Relationship Query (Complex performance queries)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 25-40% reduction in turnover, better talent retention

---

## Content & Media

### Use Case #17: Content Curation & Publishing Platform

**Problem Statement:**
Content creators and publishers need to discover relevant content, understand trends, generate summaries, and create engaging content. They struggle with information overload and need intelligent content assistance.

**Solution Architecture:**
```
Content Sources (Articles, Videos, Social Media)
    ↓
Automatic Summarization + Categorization
    ↓
Semantic Search + Trend Analysis
    ↓
Content Recommendations
    ↓
Publishing Workflow Support
```

**Key Features:**
- Automatic content summarization
- Semantic content search
- Trend analysis and prediction
- Content recommendations
- Plagiarism detection
- SEO optimization suggestions

**Required Backend Endpoints:**

```
POST   /api/content/upload                   - Upload content for analysis
GET    /api/content/{id}/summary              - Get content summary
POST   /api/content/search                    - Semantic content search
GET    /api/content/trends                    - Content trend analysis
GET    /api/content/recommendations           - Content recommendations
POST   /api/content/plagiarism-check         - Plagiarism detection
GET    /api/content/seo-suggestions          - SEO optimization suggestions
POST   /api/content/categorize                - Auto-categorize content
GET    /api/content/related                   - Find related content
POST   /api/content/publish                   - Publishing workflow
```

**Database Schema:**
```sql
CREATE TABLE content_items (
    id UUID PRIMARY KEY,
    title VARCHAR,
    content CLOB,
    content_type VARCHAR,  -- 'article', 'video', 'podcast'
    author_id UUID,
    category VARCHAR,
    tags ARRAY,
    embedding BLOB,
    summary CLOB,
    created_at TIMESTAMP
);

CREATE TABLE content_summaries (
    id UUID PRIMARY KEY,
    content_id UUID,
    summary_level VARCHAR,
    summary_text CLOB,
    key_points ARRAY,
    generated_at TIMESTAMP
);

CREATE TABLE content_trends (
    id UUID PRIMARY KEY,
    topic VARCHAR,
    trend_direction VARCHAR,  -- 'rising', 'falling', 'stable'
    popularity_score DECIMAL,
    related_topics ARRAY,
    analyzed_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Summarization, Semantic Search)
- Behavior (Trend analysis)
- Web (REST API)

**Implementation Complexity:** Medium (3-4 weeks)
**Business Value:** 40-60% time savings in content discovery, better engagement

---

### Use Case #18: Social Media Content Moderation Platform

**Problem Statement:**
Social media platforms and online communities need to moderate user-generated content at scale. They must detect harmful content (hate speech, spam, misinformation) while maintaining free expression. Manual moderation doesn't scale.

**Solution Architecture:**
```
User-Generated Content (Text, Images, Videos)
    ↓
Multi-Policy Content Analysis
    ↓
PII Detection + Redaction
    ↓
Classification + Risk Scoring
    ↓
Automated Actions + Human Review Queue
```

**Key Features:**
- Multi-policy content moderation
- Real-time content analysis
- PII detection and redaction
- Spam and abuse detection
- Appeal workflow
- Moderation analytics

**Required Backend Endpoints:**

```
POST   /api/moderation/analyze                - Analyze content for violations
GET    /api/moderation/queue                  - Get moderation queue
POST   /api/moderation/review                 - Human review decision
GET    /api/moderation/violations             - Get content violations
POST   /api/moderation/appeal                 - Content appeal process
GET    /api/moderation/analytics              - Moderation analytics
POST   /api/moderation/policies               - Configure moderation policies
GET    /api/moderation/trends                 - Violation trends
POST   /api/moderation/bulk-action            - Bulk moderation actions
GET    /api/moderation/audit                  - Moderation audit log
```

**Database Schema:**
```sql
CREATE TABLE user_content (
    id UUID PRIMARY KEY,
    user_id UUID,
    content_type VARCHAR,  -- 'text', 'image', 'video'
    content_text CLOB,
    moderation_status VARCHAR,  -- 'pending', 'approved', 'removed', 'flagged'
    violation_types ARRAY,
    risk_score DECIMAL,
    moderator_id UUID,
    created_at TIMESTAMP,
    moderated_at TIMESTAMP
);

CREATE TABLE moderation_policies (
    id UUID PRIMARY KEY,
    policy_name VARCHAR,
    policy_description CLOB,
    rules JSON,
    enabled BOOLEAN,
    created_at TIMESTAMP
);

CREATE TABLE moderation_appeals (
    id UUID PRIMARY KEY,
    content_id UUID,
    user_id UUID,
    appeal_reason CLOB,
    appeal_status VARCHAR,  -- 'pending', 'approved', 'rejected'
    reviewer_id UUID,
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Content Analysis, PII Detection)
- Behavior (Pattern detection)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 80-90% reduction in manual moderation workload, faster response times

---

## Customer Success & Support

### Use Case #19: Intelligent Customer Support Platform

**Problem Statement:**
Customer support teams handle thousands of inquiries daily. They need to quickly find relevant knowledge base articles, understand customer sentiment, route tickets appropriately, and provide accurate answers. Current systems are slow and don't understand context.

**Solution Architecture:**
```
Customer Support Ticket
    ↓
PII Detection + Redaction
    ↓
RAG System (Knowledge Base)
    ↓
Sentiment Analysis (Behavior Module)
    ↓
Answer Generation + Ticket Routing
    ↓
Customer Satisfaction Tracking
```

**Key Features:**
- Natural language ticket search
- Knowledge base Q&A
- Sentiment analysis and churn prediction
- Automatic ticket routing
- Answer suggestions for agents
- Customer satisfaction analytics

**Required Backend Endpoints:**

```
POST   /api/support/search                   - Search knowledge base
POST   /api/support/query                     - Natural language support queries
POST   /api/support/ticket                    - Create support ticket
GET    /api/support/tickets                   - Get tickets with routing suggestions
POST   /api/support/answer                    - Generate answer suggestions
GET    /api/support/sentiment                 - Customer sentiment analysis
POST   /api/support/routing                   - Intelligent ticket routing
GET    /api/support/analytics                 - Support analytics dashboard
POST   /api/support/feedback                  - Customer feedback
GET    /api/support/knowledge-base            - Knowledge base management
```

**Database Schema:**
```sql
CREATE TABLE support_tickets (
    id UUID PRIMARY KEY,
    ticket_number VARCHAR UNIQUE,
    customer_id UUID,
    subject VARCHAR,
    description CLOB,
    redacted_description CLOB,  -- PII removed
    category VARCHAR,
    priority VARCHAR,
    status VARCHAR,
    assigned_agent_id UUID,
    sentiment_score DECIMAL,
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE knowledge_base_articles (
    id UUID PRIMARY KEY,
    title VARCHAR,
    content CLOB,
    category VARCHAR,
    tags ARRAY,
    embedding BLOB,
    views INT,
    helpful_count INT,
    created_at TIMESTAMP
);

CREATE TABLE support_queries (
    id UUID PRIMARY KEY,
    ticket_id UUID,
    query_text VARCHAR,
    answer_text CLOB,
    source_articles ARRAY,
    confidence DECIMAL,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (RAG, PII Detection)
- Behavior (Sentiment analysis, churn prediction)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 40-60% reduction in resolution time, improved customer satisfaction

---

### Use Case #20: Customer Churn Prediction & Prevention Platform

**Problem Statement:**
Businesses lose customers due to churn, but often don't know which customers are at risk until it's too late. They need to predict churn, understand why customers leave, and take proactive actions to retain them.

**Solution Architecture:**
```
Customer Data + Interaction History
    ↓
Behavior Analytics (Engagement Patterns)
    ↓
Churn Risk Scoring + Prediction
    ↓
Root Cause Analysis
    ↓
Retention Recommendations + Alerts
```

**Key Features:**
- Churn risk prediction
- Root cause analysis
- Retention campaign recommendations
- Customer health scoring
- Engagement trend analysis
- Retention success tracking

**Required Backend Endpoints:**

```
GET    /api/churn/risk                       - Get churn risk scores
GET    /api/churn/customers                  - Get at-risk customers
POST   /api/churn/analyze                     - Analyze churn patterns
GET    /api/churn/recommendations             - Retention recommendations
GET    /api/churn/health-score                - Customer health scores
GET    /api/churn/trends                     - Churn trend analysis
POST   /api/churn/campaign                    - Create retention campaign
GET    /api/churn/effectiveness               - Campaign effectiveness tracking
POST   /api/churn/alerts                      - Configure churn alerts
GET    /api/churn/analytics                   - Churn analytics dashboard
```

**Database Schema:**
```sql
CREATE TABLE customers (
    id UUID PRIMARY KEY,
    customer_id VARCHAR UNIQUE,
    signup_date DATE,
    subscription_tier VARCHAR,
    behavior_insights JSON,  -- From behavior module
    churn_risk_score DECIMAL,
    churn_reason TEXT,
    created_at TIMESTAMP
);

CREATE TABLE customer_interactions (
    id UUID PRIMARY KEY,
    customer_id UUID,
    interaction_type VARCHAR,  -- 'login', 'purchase', 'support_ticket', 'feature_use'
    interaction_data JSON,
    timestamp TIMESTAMP
);

CREATE TABLE churn_predictions (
    id UUID PRIMARY KEY,
    customer_id UUID,
    churn_probability DECIMAL,
    predicted_churn_date DATE,
    risk_factors ARRAY,
    retention_recommendations JSON,
    generated_at TIMESTAMP
);

CREATE TABLE retention_campaigns (
    id UUID PRIMARY KEY,
    campaign_name VARCHAR,
    target_customers ARRAY,
    campaign_type VARCHAR,  -- 'discount', 'feature_highlight', 'support_outreach'
    success_rate DECIMAL,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Behavior (Churn prediction, pattern analysis)
- Core (Semantic analysis)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 30-50% reduction in churn rate, improved customer lifetime value

---

## Research & Analytics

### Use Case #21: Business Intelligence & Analytics Platform

**Problem Statement:**
Business users need to query complex business data using natural language instead of SQL or complex BI tools. They want insights on sales, customers, products, and operations without technical knowledge.

**Solution Architecture:**
```
Business Query (Natural Language)
    ↓
Relationship Query (Multi-Entity Data)
    ↓
Data Aggregation + Analysis
    ↓
Visualization-Ready Results
    ↓
Insights + Recommendations
```

**Key Features:**
- Natural language business queries
- Multi-entity relationship queries
- Automated insights generation
- Trend analysis
- Anomaly detection
- Custom report generation

**Required Backend Endpoints:**

```
POST   /api/analytics/query                   - Natural language business queries
GET    /api/analytics/insights                - Automated insights
GET    /api/analytics/trends                   - Trend analysis
GET    /api/analytics/anomalies                - Anomaly detection
POST   /api/analytics/reports                  - Generate custom reports
GET    /api/analytics/dashboard                - Dashboard data
POST   /api/analytics/compare                  - Compare time periods or segments
GET    /api/analytics/forecast                 - Forecasting
POST   /api/analytics/alerts                   - Configure alerts
GET    /api/analytics/metrics                  - Key metrics
```

**Database Schema:**
```sql
CREATE TABLE business_queries (
    id UUID PRIMARY KEY,
    user_id UUID,
    query_text VARCHAR,
    query_type VARCHAR,  -- 'sales', 'customer', 'product', 'operational'
    results JSON,
    execution_time_ms INT,
    executed_at TIMESTAMP
);

CREATE TABLE business_insights (
    id UUID PRIMARY KEY,
    insight_type VARCHAR,  -- 'trend', 'anomaly', 'opportunity', 'risk'
    insight_data JSON,
    entity_type VARCHAR,
    entity_id UUID,
    confidence DECIMAL,
    generated_at TIMESTAMP
);

CREATE TABLE analytics_dashboards (
    id UUID PRIMARY KEY,
    dashboard_name VARCHAR,
    user_id UUID,
    widgets JSON,
    refresh_interval INT,
    created_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Relationship Query (Complex business queries)
- Core (Insights generation)
- Behavior (Pattern analysis)
- Web (REST API)

**Implementation Complexity:** High (5-6 weeks)
**Business Value:** 70-90% reduction in time to get insights, democratized data access

---

### Use Case #22: Market Research & Competitive Intelligence Platform

**Problem Statement:**
Companies need to monitor competitors, track market trends, analyze customer feedback, and understand industry dynamics. Current tools are fragmented and don't provide intelligent synthesis of information.

**Solution Architecture:**
```
Market Data Sources (News, Social Media, Reviews, Reports)
    ↓
Content Ingestion + Summarization
    ↓
Semantic Search + Trend Analysis
    ↓
Competitive Intelligence Reports
    ↓
Market Insights + Recommendations
```

**Key Features:**
- Automated market data collection
- Competitive analysis
- Trend identification
- Sentiment analysis
- Market opportunity discovery
- Custom intelligence reports

**Required Backend Endpoints:**

```
POST   /api/market/ingest                    - Ingest market data
POST   /api/market/search                     - Search market intelligence
GET    /api/market/competitors                 - Competitive analysis
GET    /api/market/trends                      - Market trend analysis
GET    /api/market/sentiment                   - Market sentiment analysis
POST   /api/market/opportunities                - Discover market opportunities
GET    /api/market/reports                     - Generate intelligence reports
POST   /api/market/alerts                      - Configure market alerts
GET    /api/market/insights                    - Market insights
POST   /api/market/compare                     - Compare competitors
```

**Database Schema:**
```sql
CREATE TABLE market_data (
    id UUID PRIMARY KEY,
    source_type VARCHAR,  -- 'news', 'social_media', 'review', 'report'
    source_url VARCHAR,
    title VARCHAR,
    content CLOB,
    summary CLOB,
    sentiment VARCHAR,
    entities ARRAY,  -- Companies, products mentioned
    embedding BLOB,
    published_date DATE,
    created_at TIMESTAMP
);

CREATE TABLE competitors (
    id UUID PRIMARY KEY,
    competitor_name VARCHAR,
    industry VARCHAR,
    products ARRAY,
    market_share DECIMAL,
    embedding BLOB,
    created_at TIMESTAMP
);

CREATE TABLE market_insights (
    id UUID PRIMARY KEY,
    insight_type VARCHAR,  -- 'trend', 'opportunity', 'threat', 'competitor_move'
    insight_data JSON,
    related_entities ARRAY,
    confidence DECIMAL,
    generated_at TIMESTAMP
);
```

**AI Fabric Modules Used:**
- Core (Summarization, Semantic Search, Sentiment Analysis)
- Behavior (Trend analysis)
- Web (REST API)

**Implementation Complexity:** Medium-High (4-5 weeks)
**Business Value:** 50-70% time savings in market research, better strategic decisions

---

## Implementation Guidelines

### Common Architecture Patterns

All use cases follow these common patterns:

1. **REST API Layer** - Optional controllers via `ai-fabric-web` (security/compliance/advanced RAG/migration)
2. **Service Layer** - Business logic using AI Fabric Core services:
   - `AICoreService` - Core AI operations (embeddings, search, generation)
   - `AISearchService` - Semantic search operations
   - `RAGProvider` - RAG operations (implemented by `ai-infrastructure-rag` module)
   - `AIEmbeddingService` - Embedding generation
3. **Data Layer** - JPA entities with `@AICapable` annotation:
   ```java
   @AICapable(
       entityType = "your-entity-type",
       autoEmbedding = true,  // Auto-generate embeddings
       indexable = true,      // Enable search indexing
       indexingStrategy = IndexingStrategy.ASYNC  // Background indexing
   )
   ```
4. **AI Layer** - Semantic search, RAG, embeddings via AI Fabric Core
5. **Security Layer** - PII/PHI detection via `PIIDetectionService`, access control
6. **Analytics Layer** - Behavior module for insights via `BehaviorAnalysisService`

### Required Dependencies

```xml
<dependencies>
    <!-- Core AI Fabric - Required -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-fabric-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Web API Layer - For REST endpoints -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-fabric-web</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- RAG Module - For RAG capabilities -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-rag</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Behavior Analytics - For sentiment, churn prediction -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-behavior</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Relationship Query - For natural language queries -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-relationship-query</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Migration Module - For bulk data processing -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-migration</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Embedding Provider - Choose one -->
    <!-- Option 1: Free local embeddings (recommended for development) -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-onnx-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Option 2: OpenAI embeddings (for production) -->
    <!-- <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-provider-openai</artifactId>
        <version>1.0.0</version>
    </dependency> -->
    
    <!-- Vector Database - Choose one -->
    <!-- Option 1: Embedded Lucene (recommended for development) -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-vector-lucene</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Option 2: Production vector databases -->
    <!-- <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-vector-milvus</artifactId>
        <version>1.0.0</version>
    </dependency> -->
</dependencies>
```

### @AICapable Annotation Usage

The `@AICapable` annotation is the primary way to enable AI capabilities on entities:

```java
@AICapable(
    entityType = "product",           // Required: Entity type identifier
    autoEmbedding = true,              // Auto-generate embeddings on save
    indexable = true,                  // Enable search indexing
    enableSearch = true,               // Enable semantic search
    enableRecommendations = false,     // Enable recommendations (optional)
    indexingStrategy = IndexingStrategy.ASYNC,  // Background indexing
    onCreateStrategy = IndexingStrategy.SYNC,   // Immediate for creates
    onUpdateStrategy = IndexingStrategy.ASYNC, // Background for updates
    onDeleteStrategy = IndexingStrategy.SYNC     // Immediate for deletes
)
@Entity
public class Product {
    @Id
    private UUID id;
    
    @AISearchable(weight = 2.0)  // Field-level: Mark as searchable
    private String name;
    
    @AIContext(contextKey = "category")  // Field-level: Include in context
    private String category;
    
    // Other fields...
}
```

### ⚠️ CRITICAL: @AIProcess Annotation for Vector Synchronization

**@AIProcess is ESSENTIAL** for keeping vectors synchronized with database changes. It must be used on **ALL service methods** that save, update, or delete entities.

**Why it's needed:**
- `@AICapable` works for direct repository calls
- Service methods need `@AIProcess` to ensure AOP intercepts at the service layer
- Without `@AIProcess`, vectors may become out of sync with database

**Where to use @AIProcess:**

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    /**
     * CREATE - Must use @AIProcess to sync vectors
     */
    @AIProcess(
        entityType = "product",
        processType = "create",
        generateEmbedding = true,
        indexForSearch = true,
        indexingStrategy = IndexingStrategy.SYNC  // Immediate for creates
    )
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        
        // @AIProcess ensures vector is created and synced with DB
        return repository.save(product);
    }
    
    /**
     * UPDATE - Must use @AIProcess to sync vectors
     */
    @AIProcess(
        entityType = "product",
        processType = "update",
        generateEmbedding = true,
        indexForSearch = true,
        indexingStrategy = IndexingStrategy.ASYNC  // Background for updates
    )
    @Transactional
    public Product updateProduct(UUID id, UpdateProductRequest request) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        product.setName(request.getName());
        
        // @AIProcess ensures vector is updated in sync with DB
        return repository.save(product);
    }
    
    /**
     * DELETE - Must use @AIProcess to remove from index
     */
    @AIProcess(
        entityType = "product",
        processType = "delete",
        generateEmbedding = false,
        indexForSearch = false  // Removes from index
    )
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        // @AIProcess ensures vector is removed from index
        repository.delete(product);
    }
    
    /**
     * BULK OPERATIONS - Must use @AIProcess
     */
    @AIProcess(
        entityType = "product",
        processType = "create",
        indexingStrategy = IndexingStrategy.ASYNC  // Background for bulk
    )
    @Transactional
    public List<Product> bulkCreateProducts(List<CreateProductRequest> requests) {
        List<Product> products = requests.stream()
            .map(this::mapToProduct)
            .toList();
        
        // Bulk save - vectors indexed via @AIProcess
        return repository.saveAll(products);
    }
}
```

**Use @AIProcess on:**
- ✅ All CREATE methods (`createProduct()`, `saveProduct()`, `addProduct()`)
- ✅ All UPDATE methods (`updateProduct()`, `modifyProduct()`, `editProduct()`)
- ✅ All DELETE methods (`deleteProduct()`, `removeProduct()`)
- ✅ Bulk operations (`bulkCreate()`, `importProducts()`, `migrateData()`)
- ✅ Custom business logic methods that call `repository.save()`

### Core Services Usage

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository repository;
    
    // Core AI service
    private final AICoreService aiCoreService;
    
    // Search service
    private final AISearchService searchService;
    
    // RAG provider (from ai-infrastructure-rag)
    private final RAGProvider ragProvider;
    
    // Embedding service
    private final AIEmbeddingService embeddingService;
    
    // Behavior service (if using behavior module)
    private final BehaviorAnalysisService behaviorService;
    
    /**
     * ⚠️ CRITICAL: Use @AIProcess on ALL methods that save/update/delete entities
     * This ensures vectors stay synchronized with database changes
     */
    @AIProcess(
        entityType = "product",
        processType = "create",
        generateEmbedding = true,
        indexForSearch = true,
        indexingStrategy = IndexingStrategy.SYNC
    )
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        
        // @AIProcess ensures vector is created and synced with DB
        return repository.save(product);
    }
    
    @AIProcess(
        entityType = "product",
        processType = "update",
        indexingStrategy = IndexingStrategy.ASYNC
    )
    @Transactional
    public Product updateProduct(UUID id, UpdateProductRequest request) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        product.setName(request.getName());
        
        // @AIProcess ensures vector is updated in sync with DB
        return repository.save(product);
    }
    
    @AIProcess(
        entityType = "product",
        processType = "delete"
    )
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        // @AIProcess ensures vector is removed from index
        repository.delete(product);
    }
    
    public AISearchResponse searchProducts(String query) {
        AISearchRequest request = AISearchRequest.builder()
            .query(query)
            .entityType("product")
            .limit(20)
            .build();
        
        return searchService.search(request);
    }
    
    public RAGResponse askQuestion(String question) {
        RAGRequest request = RAGRequest.builder()
            .query(question)
            .entityType("product")
            .limit(5)
            .build();
        
        return ragProvider.performRAGQuery(request);
    }
}
```

### ⚠️ CRITICAL: @AIProcess for Vector Synchronization

**@AIProcess is ESSENTIAL** for keeping vectors synchronized with database changes. Use it on:

1. **All CREATE methods** - `createProduct()`, `saveProduct()`, `addProduct()`
2. **All UPDATE methods** - `updateProduct()`, `modifyProduct()`, `editProduct()`
3. **All DELETE methods** - `deleteProduct()`, `removeProduct()`
4. **Bulk operations** - `bulkCreate()`, `importProducts()`, `migrateData()`
5. **Custom business logic** - Any service method that calls `repository.save()`

**Why it's needed:**
- `@AICapable` works for direct repository calls
- Service methods need `@AIProcess` to ensure AOP intercepts at the service layer
- Without `@AIProcess`, vectors may become out of sync with database

### Implementation Phases

**Phase 1: Foundation (Week 1)**
- Setup project structure
- Configure AI Fabric modules
- Create database schema
- Setup basic entities with @AICapable

**Phase 2: Core Features (Weeks 2-3)**
- Implement core business logic
- Integrate AI Fabric services
- Create REST API endpoints
- Basic UI integration

**Phase 3: Advanced Features (Week 4)**
- Add behavior analytics
- Implement natural language queries
- Add security and compliance features
- Performance optimization

**Phase 4: Polish (Week 5)**
- Testing and bug fixes
- Documentation
- Deployment preparation
- User acceptance testing

### Testing Strategy

Each use case should include:

1. **Unit Tests** - Service layer, repository layer
2. **Integration Tests** - End-to-end API tests
3. **AI Tests** - Semantic search accuracy, RAG quality
4. **Performance Tests** - Latency, throughput benchmarks
5. **Security Tests** - PII detection, access control

### Deployment Considerations

- **Database**: PostgreSQL or MySQL for production
- **Vector Database**: Lucene (dev), Milvus/Qdrant (production)
- **Caching**: Redis for query caching
- **Monitoring**: Prometheus + Grafana
- **Logging**: ELK stack or similar

### Success Metrics

Each use case should track:

- **Functional Metrics**: Accuracy, relevance, user satisfaction
- **Performance Metrics**: Latency (p50, p95, p99), throughput
- **Business Metrics**: User engagement, conversion rates, cost savings
- **AI Metrics**: Search relevance, RAG quality, prediction accuracy

---

## Summary

This document provides **22 comprehensive use case ideas** for building standalone applications with AI Fabric Framework. Each use case:

- ✅ Solves a real business problem
- ✅ Requires UI with comprehensive REST endpoints
- ✅ Leverages AI Fabric Framework capabilities
- ✅ Is production-ready with security and compliance
- ✅ Has clear implementation guidelines

**Next Steps:**

1. Choose a use case that aligns with your domain/expertise
2. Review the detailed architecture and endpoints
3. Follow the implementation guidelines
4. Leverage AI Fabric Framework modules
5. Build, test, and deploy

**For questions or contributions, please refer to the main AI Fabric Framework documentation.**

---

---

## Intent Action Handling Demonstration

### 🎯 Best Use Case for Intent Action Handling: **App 7 - Subscription Management Hub**

**Why it's perfect:**
- ✅ **Multiple clear actions** - Subscribe, Unsubscribe, Upgrade, Downgrade, Update Address
- ✅ **Natural language friendly** - Users can say "cancel my subscription" or "upgrade to Pro plan"
- ✅ **Requires confirmation** - Actions like cancellation need user confirmation
- ✅ **Business logic complexity** - Each action has validation, permissions, and side effects
- ✅ **Real-world scenario** - Common SaaS pattern that developers understand

**Example Natural Language Queries:**
- "I want to cancel my subscription"
- "Upgrade me to the Enterprise plan"
- "Change my billing address to 123 Main Street, New York"
- "Downgrade to the Basic plan next month"
- "Subscribe to the Pro plan with annual billing"

**Intent Action Handler Implementation:**
```java
// Example: CancelSubscriptionActionHandler
@Component
public class CancelSubscriptionActionHandler implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_subscription")
            .description("Cancel an active subscription")
            .parameters(List.of("subscriptionId", "reason"))
            .requiresConfirmation(true)
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Check if user has active subscription
        return subscriptionService.hasActiveSubscription(userId);
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String subscriptionId = (String) params.get("subscriptionId");
        return String.format("Are you sure you want to cancel subscription %s? This action cannot be undone.", subscriptionId);
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String subscriptionId = (String) params.get("subscriptionId");
        String reason = (String) params.getOrDefault("reason", "User requested");
        
        Subscription subscription = subscriptionService.unsubscribe(
            UUID.fromString(subscriptionId), 
            reason
        );
        
        return ActionResult.builder()
            .success(true)
            .message("Your subscription has been cancelled successfully")
            .data(Map.of("subscriptionId", subscriptionId, "status", subscription.getStatus()))
            .build();
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        return ActionResult.builder()
            .success(false)
            .message("Failed to cancel subscription: " + e.getMessage())
            .build();
    }
}
```

**Other Use Cases with Action Potential:**
- **App 3: Product Discovery Engine** - Add to cart, Create wishlist, Set price alerts
- **App 4: Team Sentiment Tracker** - Submit check-in, Create alert, Schedule follow-up
- **E-Commerce use cases** - Place order, Request refund, Track shipment
- **HR use cases** - Schedule interview, Approve leave, Update employee record

---

## Summary

This document provides **29 total use case ideas** for building standalone applications with AI Fabric Framework:

### 7 Recommended Starter Apps (Focus on Implementation)

These are the **best starting points** with complete implementation details:
1. **Smart FAQ Assistant** - RAG + Semantic Search (2-3 weeks)
2. **Document Intelligence Hub** - PII Detection + RAG + Semantic Search (3-4 weeks)
3. **Product Discovery Engine** - Vector Search + Recommendations (2-3 weeks)
4. **Team Sentiment Tracker** - Behavior Analytics + Sentiment (2-3 weeks)
5. **Code Documentation Search** - RAG + Code Understanding (2-3 weeks)
6. **Meeting Notes Analyzer** - RAG + Semantic Search (2-3 weeks)
7. **Subscription Management Hub** - Behavior Analytics + Churn Prediction (3-4 weeks)

**Why start here?**
- ✅ Complete data models with `@AICapable` annotations
- ✅ Detailed REST endpoint specifications
- ✅ Request/response examples
- ✅ Clear implementation roadmap
- ✅ Practical, focused solutions

### 22 Extended Use Cases (Industry-Specific Inspiration)

These provide **additional inspiration** across different industries:
- E-Commerce & Retail (3 use cases)
- Healthcare & Life Sciences (3 use cases)
- Financial Services (3 use cases)
- Education & Learning (3 use cases)
- Legal & Compliance (2 use cases)
- HR & Talent Management (2 use cases)
- Content & Media (2 use cases)
- Customer Success & Support (2 use cases)
- Research & Analytics (2 use cases)

**Use these for:**
- Industry-specific requirements
- Complex domain problems
- Extended feature ideas
- Business case development

### Framework Alignment

All use cases are aligned with actual AI Fabric Framework capabilities:

✅ **@AICapable Annotation** - Correct usage with `autoEmbedding`, `indexable`, `indexingStrategy`  
✅ **@AIProcess Annotation** - **ESSENTIAL** for service methods to keep vectors synced with DB  
✅ **@AISearchable Annotation** - Field-level annotation for searchable content  
✅ **@AIContext Annotation** - Field-level annotation for metadata  
✅ **Core Services** - `AICoreService`, `AISearchService`, `RAGProvider`, `AIEmbeddingService`  
✅ **Behavior Module** - `BehaviorAnalysisService` with sentiment analysis and churn prediction  
✅ **Relationship Query** - `ReliableRelationshipQueryService` for natural language queries  
✅ **Web Module** - Optional controllers via `ai-fabric-web` (security/compliance/advanced RAG/migration)  
✅ **Migration Module** - `DataMigrationService` for bulk data processing  
✅ **Vector Databases** - Lucene (dev), Milvus/Qdrant (production)  
✅ **Embedding Providers** - ONNX (free local), OpenAI (production)

### Next Steps

1. **Choose a starter app** from the 7 recommended examples
2. **Review the implementation guidelines** for framework setup
3. **Follow the data model patterns** with `@AICapable` annotations
4. **Implement REST endpoints** using AI Fabric Web module
5. **Leverage framework services** for AI capabilities
6. **Test and deploy** following the testing strategy

---

**Document Status:** Complete and Aligned with Framework  
**Last Updated:** January 2026  
**Maintainer:** AI Fabric Framework Team  
**Framework Version:** 1.0.0
