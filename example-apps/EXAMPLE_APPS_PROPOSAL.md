# AI Fabric Framework - Example Apps Proposal

**Purpose:** Define real-world example applications that demonstrate AI Fabric Framework capabilities
**Target:** Backend services with REST endpoints for frontend/UI integration
**Tech Stack:** Spring Boot + AI Fabric Framework + ONNX/OpenAI + Lucene + H2/PostgreSQL

---

## Executive Summary

This document outlines **6 practical example applications** to be built as standalone Spring Boot backends using the AI Fabric Framework. Each app demonstrates key framework capabilities while solving real business problems.

| App | Primary AI Capability | Endpoints | Complexity | Business Value |
|-----|----------------------|-----------|------------|----------------|
| 1. Smart FAQ Assistant | RAG + Semantic Search | 8 | Medium | Customer support automation |
| 2. Document Intelligence Hub | Entity Extraction + RAG | 12 | Medium-High | Document processing |
| 3. Product Discovery Engine | Vector Search + Recommendations | 10 | Medium | E-commerce conversion |
| 4. Team Sentiment Tracker | Behavior Analytics + Sentiment | 9 | Medium | HR/Team management |
| 5. Code Documentation Search | RAG + Code Understanding | 7 | Medium | Developer productivity |
| 6. Meeting Notes Analyzer | Summarization + Action Items | 8 | Medium | Productivity tools |

---

## App 1: Smart FAQ Assistant

### Overview
An intelligent FAQ/knowledge base system that answers user questions using semantic search and RAG, with automatic question routing and response generation.

### Problem Solved
- Users can't find answers in traditional FAQ systems
- Support teams overwhelmed with repetitive questions
- Keyword-based search misses relevant content

### AI Fabric Capabilities Used
- **Semantic Search** (ONNX embeddings)
- **RAG** (OpenAI generation with context)
- **Hybrid Search** (vector + full-text)
- **Query Expansion** (understanding intent)

### Data Model

```java
@Entity
@AICapable(entityType = "faq-article", autoEmbedding = true, indexable = true)
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

@Entity
public class FAQQuery {
    @Id
    private UUID id;
    private String question;
    private UUID answeredByArticleId;
    private float confidenceScore;
    private boolean wasHelpful;
    private LocalDateTime createdAt;
}
```

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/faq/ask` | Ask a question, get AI-generated answer |
| GET | `/api/faq/search?q={query}` | Semantic search for articles |
| POST | `/api/faq/articles` | Create new FAQ article |
| PUT | `/api/faq/articles/{id}` | Update FAQ article |
| DELETE | `/api/faq/articles/{id}` | Delete FAQ article |
| GET | `/api/faq/articles/{id}` | Get article by ID |
| GET | `/api/faq/categories` | List all categories |
| POST | `/api/faq/feedback/{queryId}` | Submit helpfulness feedback |

### Request/Response Examples

**Ask Question:**
```json
// POST /api/faq/ask
{
  "question": "How do I reset my password?",
  "category": "account",  // optional filter
  "includeRelated": true
}

// Response
{
  "answer": "To reset your password, go to Settings > Security > Change Password. You'll need to verify your email first.",
  "confidence": 0.94,
  "sourceArticles": [
    {
      "id": "art-123",
      "title": "Password Reset Guide",
      "relevanceScore": 0.94,
      "snippet": "..."
    }
  ],
  "relatedQuestions": [
    "How do I enable two-factor authentication?",
    "What if I forgot my email?"
  ],
  "queryId": "q-456"
}
```

### UI Components Needed
- Search bar with auto-suggestions
- Answer display with source citations
- Article browser by category
- Feedback thumbs up/down
- Admin: Article editor with preview

---

## App 2: Document Intelligence Hub

### Overview
A document processing system that extracts entities, generates summaries, and enables intelligent search across uploaded documents (PDFs, Word, etc.).

### Problem Solved
- Manual document review is time-consuming
- Key information buried in long documents
- No way to search across document content

### AI Fabric Capabilities Used
- **Entity Extraction** (OpenAI)
- **Document Chunking & Indexing**
- **RAG** for Q&A over documents
- **Summarization** (multi-level)
- **PII Detection** (for sensitive docs)

### Data Model

```java
@Entity
@AICapable(entityType = "document", autoEmbedding = true, indexable = true)
public class Document {
    @Id
    private UUID id;
    private String filename;
    private String mimeType;
    private long fileSize;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String documentType; // contract, report, invoice, etc.
    private UUID uploadedBy;
    private LocalDateTime uploadedAt;
    private ProcessingStatus status;
}

@Entity
public class DocumentChunk {
    @Id
    private UUID id;
    private UUID documentId;
    private int chunkIndex;

    @Column(columnDefinition = "TEXT")
    private String content;

    private int pageNumber;
    private String sectionTitle;
}

@Entity
public class ExtractedEntity {
    @Id
    private UUID id;
    private UUID documentId;
    private String entityType; // person, organization, date, amount, etc.
    private String entityValue;
    private String context;
    private float confidence;
}
```

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/docs/upload` | Upload document for processing |
| GET | `/api/docs/{id}` | Get document details |
| GET | `/api/docs/{id}/status` | Get processing status |
| GET | `/api/docs/{id}/summary` | Get document summary |
| GET | `/api/docs/{id}/entities` | Get extracted entities |
| POST | `/api/docs/search` | Semantic search across documents |
| POST | `/api/docs/{id}/ask` | Ask question about specific document |
| GET | `/api/docs` | List all documents |
| DELETE | `/api/docs/{id}` | Delete document |
| POST | `/api/docs/{id}/reprocess` | Reprocess document |
| GET | `/api/docs/stats` | Get processing statistics |
| POST | `/api/docs/bulk-upload` | Bulk document upload |

### Request/Response Examples

**Upload Document:**
```json
// POST /api/docs/upload (multipart/form-data)
// file: contract.pdf
// documentType: contract

// Response
{
  "id": "doc-789",
  "filename": "contract.pdf",
  "status": "PROCESSING",
  "estimatedCompletionSeconds": 30
}
```

**Ask Document Question:**
```json
// POST /api/docs/doc-789/ask
{
  "question": "What is the contract termination clause?"
}

// Response
{
  "answer": "The contract can be terminated with 30 days written notice by either party, as stated in Section 8.2.",
  "confidence": 0.91,
  "sourceChunks": [
    {
      "pageNumber": 12,
      "sectionTitle": "8. Termination",
      "relevantText": "Either party may terminate this agreement..."
    }
  ]
}
```

### UI Components Needed
- Drag-drop document uploader
- Processing status indicator
- Document viewer with highlights
- Entity cards/chips
- Q&A chat interface per document
- Search results with document previews

---

## App 3: Product Discovery Engine

### Overview
An AI-powered product search and recommendation system that understands natural language queries and provides personalized suggestions.

### Problem Solved
- Traditional search misses product intent
- Users struggle with complex filter combinations
- Recommendations are generic, not personalized

### AI Fabric Capabilities Used
- **Semantic Search** (ONNX)
- **Behavior Module** (user interaction tracking)
- **Hybrid Search** (semantic + filters)
- **Query Understanding** (natural language to filters)

### Data Model

```java
@Entity
@AICapable(entityType = "product", autoEmbedding = true, indexable = true)
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
    private int reviewCount;
    private boolean inStock;
    private String imageUrl;
}

@Entity
public class UserInteraction {
    @Id
    private UUID id;
    private UUID userId;
    private UUID productId;
    private InteractionType type; // VIEW, CART, PURCHASE, WISHLIST
    private LocalDateTime timestamp;
    private String sessionId;
}

@Entity
public class SearchSession {
    @Id
    private UUID id;
    private UUID userId;
    private String query;
    private List<UUID> viewedProducts;
    private List<UUID> cartedProducts;
    private LocalDateTime createdAt;
}
```

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products/search` | Natural language product search |
| GET | `/api/products/{id}` | Get product details |
| GET | `/api/products/{id}/similar` | Find similar products |
| GET | `/api/products/recommendations` | Personalized recommendations |
| POST | `/api/products/interactions` | Track user interaction |
| GET | `/api/products/trending` | Get trending products |
| GET | `/api/products/categories` | List categories with counts |
| POST | `/api/products` | Add new product (admin) |
| PUT | `/api/products/{id}` | Update product (admin) |
| POST | `/api/products/bulk-index` | Bulk index products |

### Request/Response Examples

**Natural Language Search:**
```json
// POST /api/products/search
{
  "query": "comfortable running shoes for marathon under $150",
  "userId": "user-123",  // optional, for personalization
  "limit": 20
}

// Response
{
  "products": [
    {
      "id": "prod-456",
      "name": "Marathon Pro Runner X",
      "description": "Ultra-cushioned running shoe...",
      "price": 129.99,
      "relevanceScore": 0.96,
      "matchReasons": ["marathon", "comfort", "under budget"]
    }
  ],
  "totalResults": 45,
  "suggestedFilters": [
    {"name": "brand", "values": ["Nike", "Adidas", "Brooks"]},
    {"name": "size", "values": ["8", "9", "10", "11"]}
  ],
  "queryUnderstanding": {
    "productType": "running shoes",
    "attributes": ["comfortable", "marathon"],
    "priceMax": 150
  }
}
```

**Get Recommendations:**
```json
// GET /api/products/recommendations?userId=user-123

// Response
{
  "forYou": [
    {
      "id": "prod-789",
      "name": "Running Socks Pack",
      "reason": "Complements your recent shoe purchase"
    }
  ],
  "recentlyViewed": [...],
  "trending": [...],
  "basedOnPurchases": [...]
}
```

### UI Components Needed
- Smart search bar with query understanding preview
- Product grid with cards
- Filter sidebar (auto-generated from search)
- Similar products carousel
- "For You" recommendations section
- Recently viewed widget

---

## App 4: Team Sentiment Tracker

### Overview
A team pulse/mood tracking system that analyzes check-in messages, detects sentiment trends, and provides early warning for team issues.

### Problem Solved
- Managers unaware of team morale issues
- Delayed detection of burnout/disengagement
- No way to track sentiment over time

### AI Fabric Capabilities Used
- **Behavior Module** (event tracking)
- **Sentiment Analysis** (6-level classification)
- **Trend Detection** (improving/declining)
- **Churn Prediction** (disengagement risk)
- **PII Detection** (anonymization option)

### Data Model

```java
@Entity
@AICapable(entityType = "check-in", autoEmbedding = true, indexable = true)
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

@Entity
public class TeamMember {
    @Id
    private UUID id;
    private String name;
    private String email;
    private UUID teamId;
    private String role;
    private LocalDate joinDate;
    private float currentMoodScore;
    private TrendDirection trendDirection;
    private float disengagementRisk;
}

@Entity
public class Team {
    @Id
    private UUID id;
    private String name;
    private UUID managerId;
    private float averageSentimentScore;
    private int memberCount;
}

@Entity
public class SentimentAlert {
    @Id
    private UUID id;
    private UUID teamMemberId;
    private AlertType type; // RAPID_DECLINE, HIGH_RISK, BURNOUT_INDICATORS
    private String description;
    private boolean acknowledged;
    private LocalDateTime triggeredAt;
}
```

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sentiment/check-in` | Submit daily check-in |
| GET | `/api/sentiment/team/{teamId}/dashboard` | Team sentiment dashboard |
| GET | `/api/sentiment/member/{memberId}/history` | Member sentiment history |
| GET | `/api/sentiment/team/{teamId}/trends` | Team sentiment trends |
| GET | `/api/sentiment/alerts` | Get active alerts |
| PUT | `/api/sentiment/alerts/{id}/acknowledge` | Acknowledge alert |
| GET | `/api/sentiment/team/{teamId}/topics` | Common topics analysis |
| POST | `/api/sentiment/anonymous-feedback` | Submit anonymous feedback |
| GET | `/api/sentiment/reports/weekly` | Weekly sentiment report |

### Request/Response Examples

**Submit Check-In:**
```json
// POST /api/sentiment/check-in
{
  "memberId": "member-123",
  "message": "Feeling overwhelmed with the sprint deadlines. Need help with the API integration.",
  "date": "2025-01-10"
}

// Response
{
  "id": "checkin-456",
  "analyzedSentiment": "FRUSTRATED",
  "sentimentScore": 0.35,
  "detectedTopics": ["workload", "deadlines", "help-needed"],
  "suggestedActions": [
    "Consider discussing workload with manager",
    "API integration resources available in docs"
  ]
}
```

**Team Dashboard:**
```json
// GET /api/sentiment/team/team-789/dashboard

// Response
{
  "teamId": "team-789",
  "teamName": "Backend Engineering",
  "currentScore": 0.72,
  "trend": "STABLE",
  "memberCount": 8,
  "checkInsToday": 6,
  "sentimentDistribution": {
    "DELIGHTED": 1,
    "SATISFIED": 3,
    "NEUTRAL": 1,
    "CONFUSED": 1,
    "FRUSTRATED": 0,
    "CHURNING": 0
  },
  "topTopics": [
    {"topic": "sprint-planning", "mentions": 4, "sentiment": "positive"},
    {"topic": "technical-debt", "mentions": 3, "sentiment": "neutral"}
  ],
  "alerts": [
    {
      "memberId": "member-456",
      "type": "DECLINING_TREND",
      "message": "3-week declining sentiment trend detected"
    }
  ],
  "weekOverWeek": {
    "change": -0.05,
    "direction": "slightly_down"
  }
}
```

### UI Components Needed
- Check-in form (daily pulse)
- Team dashboard with sentiment gauge
- Individual member trend charts
- Alert notification panel
- Topic cloud visualization
- Historical trend graphs
- Anonymous feedback form

---

## App 5: Code Documentation Search

### Overview
A semantic search engine for code repositories that understands code concepts and enables natural language queries over documentation and code comments.

### Problem Solved
- Developers can't find relevant code examples
- Documentation is scattered and hard to search
- Onboarding takes too long due to tribal knowledge

### AI Fabric Capabilities Used
- **Semantic Search** (code-aware embeddings)
- **RAG** (answer code questions)
- **Hybrid Search** (semantic + exact code matches)
- **Entity Extraction** (functions, classes, APIs)

### Data Model

```java
@Entity
@AICapable(entityType = "code-doc", autoEmbedding = true, indexable = true)
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

@Entity
@AICapable(entityType = "code-snippet", autoEmbedding = true, indexable = true)
public class CodeSnippet {
    @Id
    private UUID id;
    private String description;

    @Column(columnDefinition = "TEXT")
    private String code;

    private String language;
    private String functionName;
    private String className;
    private String repository;
    private String filePath;
    private int lineNumber;
    private List<String> tags;
}

@Entity
public class SearchQuery {
    @Id
    private UUID id;
    private UUID userId;
    private String query;
    private int resultsClicked;
    private boolean foundUseful;
    private LocalDateTime searchedAt;
}
```

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/code/search` | Semantic search for code/docs |
| POST | `/api/code/ask` | Ask a coding question |
| GET | `/api/code/snippets/{id}` | Get code snippet details |
| GET | `/api/code/docs/{id}` | Get documentation |
| POST | `/api/code/index-repo` | Index a repository |
| GET | `/api/code/repos` | List indexed repositories |
| POST | `/api/code/feedback` | Submit search feedback |

### Request/Response Examples

**Code Search:**
```json
// POST /api/code/search
{
  "query": "how to handle authentication with JWT",
  "repositories": ["backend-api"],  // optional filter
  "languages": ["java", "kotlin"],   // optional filter
  "limit": 10
}

// Response
{
  "results": [
    {
      "type": "CODE_SNIPPET",
      "id": "snip-123",
      "title": "JwtAuthenticationFilter.java",
      "description": "Filter that validates JWT tokens",
      "code": "public class JwtAuthenticationFilter extends...",
      "language": "java",
      "repository": "backend-api",
      "filePath": "src/main/java/auth/JwtAuthenticationFilter.java",
      "relevanceScore": 0.95
    },
    {
      "type": "DOCUMENTATION",
      "id": "doc-456",
      "title": "Authentication Guide",
      "snippet": "This guide covers JWT authentication setup...",
      "relevanceScore": 0.89
    }
  ],
  "totalResults": 23,
  "relatedConcepts": ["OAuth2", "Spring Security", "Token Refresh"]
}
```

**Ask Coding Question:**
```json
// POST /api/code/ask
{
  "question": "How do I add rate limiting to my REST endpoints?",
  "context": "Spring Boot application"
}

// Response
{
  "answer": "To add rate limiting in Spring Boot, you can use the bucket4j library or implement a custom filter. Here's an example approach:\n\n1. Add the dependency...\n2. Configure the rate limiter...",
  "codeExamples": [
    {
      "description": "Rate limiting filter implementation",
      "code": "@Component\npublic class RateLimitFilter...",
      "language": "java"
    }
  ],
  "relatedDocs": [
    {"id": "doc-789", "title": "API Rate Limiting Best Practices"}
  ],
  "confidence": 0.88
}
```

### UI Components Needed
- Search bar with language/repo filters
- Code-aware result display (syntax highlighting)
- Documentation viewer with table of contents
- Ask question chat interface
- Repository browser
- Copy-to-clipboard for code snippets

---

## App 6: Meeting Notes Analyzer

### Overview
A system that processes meeting transcripts/notes, extracts action items, generates summaries, and enables search across meeting history.

### Problem Solved
- Action items get lost after meetings
- No easy way to find past meeting decisions
- Meeting summaries are manual and inconsistent

### AI Fabric Capabilities Used
- **Summarization** (multi-level)
- **Entity Extraction** (action items, decisions, attendees)
- **Semantic Search** (find past discussions)
- **RAG** (answer questions about meetings)

### Data Model

```java
@Entity
@AICapable(entityType = "meeting", autoEmbedding = true, indexable = true)
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

@Entity
public class ActionItem {
    @Id
    private UUID id;
    private UUID meetingId;
    private String description;
    private UUID assigneeId;
    private LocalDate dueDate;
    private ActionStatus status; // TODO, IN_PROGRESS, DONE
    private String context; // relevant meeting excerpt
    private int priority;
}

@Entity
public class Decision {
    @Id
    private UUID id;
    private UUID meetingId;
    private String decision;
    private String rationale;
    private List<UUID> stakeholders;
    private LocalDate decidedDate;
}

@Entity
public class MeetingAttendee {
    @Id
    private UUID id;
    private String name;
    private String email;
    private String role;
}
```

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/meetings` | Create/upload meeting notes |
| GET | `/api/meetings/{id}` | Get meeting details |
| GET | `/api/meetings/{id}/summary` | Get meeting summary |
| GET | `/api/meetings/{id}/actions` | Get action items |
| GET | `/api/meetings/{id}/decisions` | Get decisions made |
| PUT | `/api/meetings/actions/{id}` | Update action item status |
| POST | `/api/meetings/search` | Search across meetings |
| POST | `/api/meetings/ask` | Ask question about past meetings |

### Request/Response Examples

**Upload Meeting Notes:**
```json
// POST /api/meetings
{
  "title": "Q1 Planning Review",
  "transcript": "Meeting started at 10am. John presented the Q1 roadmap...",
  "meetingDate": "2025-01-10T10:00:00Z",
  "durationMinutes": 60,
  "attendees": ["john@company.com", "jane@company.com"],
  "meetingType": "planning"
}

// Response
{
  "id": "mtg-123",
  "status": "PROCESSING",
  "estimatedCompletionSeconds": 15
}
```

**Get Meeting Summary:**
```json
// GET /api/meetings/mtg-123/summary

// Response
{
  "meetingId": "mtg-123",
  "title": "Q1 Planning Review",
  "summaryLevels": {
    "oneLiner": "Q1 roadmap approved with focus on API improvements and mobile app launch.",
    "executive": [
      "Q1 roadmap approved with 3 major initiatives",
      "Mobile app launch scheduled for March",
      "API v2 migration timeline agreed"
    ],
    "detailed": "The team reviewed the Q1 roadmap presented by John. Three major initiatives were discussed..."
  },
  "actionItems": [
    {
      "id": "action-1",
      "description": "Create detailed API migration plan",
      "assignee": "jane@company.com",
      "dueDate": "2025-01-17",
      "priority": 1
    }
  ],
  "decisions": [
    {
      "decision": "Mobile app will use React Native",
      "rationale": "Team expertise and code sharing benefits"
    }
  ],
  "keyTopics": ["roadmap", "mobile-app", "api-v2", "timeline"],
  "attendeeCount": 5
}
```

**Search Meetings:**
```json
// POST /api/meetings/search
{
  "query": "when did we decide on the database migration strategy",
  "dateRange": {
    "from": "2024-10-01",
    "to": "2025-01-10"
  }
}

// Response
{
  "results": [
    {
      "meetingId": "mtg-089",
      "title": "Architecture Review - Database Migration",
      "date": "2024-11-15",
      "relevantExcerpt": "After discussion, the team agreed to use a phased migration approach...",
      "decision": "Phased migration over 3 sprints with feature flags",
      "relevanceScore": 0.93
    }
  ],
  "totalResults": 3
}
```

### UI Components Needed
- Meeting upload form (text or file)
- Processing status indicator
- Summary view with expandable sections
- Action items list with status toggles
- Decisions timeline
- Search interface
- Calendar view of meetings

---

## Implementation Architecture

### Shared Components

All example apps will share:

```
example-apps/
├── common/
│   ├── config/           # Shared AI configuration
│   ├── dto/              # Common DTOs (PageRequest, ApiResponse)
│   ├── exception/        # Exception handlers
│   └── security/         # Auth filters
├── faq-assistant/
├── document-hub/
├── product-discovery/
├── sentiment-tracker/
├── code-search/
└── meeting-analyzer/
```

### Common Configuration

```yaml
# application.yml (shared base)
ai:
  enabled: true
  providers:
    embedding-provider: onnx
    llm-provider: openai
    openai-api-key: ${OPENAI_API_KEY}
  vector:
    database-type: lucene
  indexing:
    default-strategy: ASYNC
  privacy:
    pii-detection:
      enabled: true
      mode: DETECT_ONLY

spring:
  datasource:
    url: jdbc:h2:file:./data/${app.name}
  jpa:
    hibernate:
      ddl-auto: update
```

### Standard Response Format

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
}

public class PagedResponse<T> extends ApiResponse<List<T>> {
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
}
```

### Standard Error Handling

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(404)
            .body(ApiResponse.error("Resource not found: " + e.getMessage()));
    }

    @ExceptionHandler(AIProcessingException.class)
    public ResponseEntity<ApiResponse<Void>> handleAIError(AIProcessingException e) {
        return ResponseEntity.status(500)
            .body(ApiResponse.error("AI processing failed: " + e.getMessage()));
    }
}
```

---

## Recommended Implementation Order

### Phase 1: Foundation App (Week 1-2)
**App 1: Smart FAQ Assistant**
- Simplest data model
- Demonstrates core RAG capability
- Good template for other apps

### Phase 2: Document Processing (Week 3-4)
**App 2: Document Intelligence Hub**
- Builds on FAQ patterns
- Adds document processing complexity
- Demonstrates entity extraction

### Phase 3: E-commerce Pattern (Week 5-6)
**App 3: Product Discovery Engine**
- Different domain (e-commerce)
- Adds behavior tracking
- Recommendation patterns

### Phase 4: Analytics Pattern (Week 7-8)
**App 4: Team Sentiment Tracker**
- Behavior module showcase
- Sentiment analysis
- Alert systems

### Phase 5: Developer Tools (Week 9-10)
**App 5: Code Documentation Search**
- Specialized embeddings
- Code-aware search
- Developer-focused UX

### Phase 6: Productivity Tools (Week 11-12)
**App 6: Meeting Notes Analyzer**
- Summarization patterns
- Action item extraction
- Full RAG implementation

---

## Testing Strategy

Each app includes:

### Unit Tests
- Service layer tests with mocked AI services
- Repository tests with H2
- Controller tests with MockMvc

### Integration Tests
- Full flow tests with real ONNX embeddings
- Mock OpenAI for consistent results
- Lucene index tests

### Example Test Structure

```java
@SpringBootTest
@AutoConfigureMockMvc
class FAQControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void askQuestion_shouldReturnAIGeneratedAnswer() throws Exception {
        // Given
        createTestArticles();

        // When
        mockMvc.perform(post("/api/faq/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "question": "How do I reset my password?",
                    "category": "account"
                }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.answer").isNotEmpty())
            .andExpect(jsonPath("$.data.confidence").isNumber())
            .andExpect(jsonPath("$.data.sourceArticles").isArray());
    }
}
```

---

## Deployment Configuration

### Docker Compose (Development)

```yaml
version: '3.8'
services:
  faq-assistant:
    build: ./faq-assistant
    ports:
      - "8081:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    volumes:
      - faq-data:/app/data

  document-hub:
    build: ./document-hub
    ports:
      - "8082:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    volumes:
      - docs-data:/app/data

  # ... other apps

volumes:
  faq-data:
  docs-data:
```

### Kubernetes (Production)

Each app deployed as separate deployment with:
- Horizontal Pod Autoscaler
- Persistent Volume for data
- ConfigMap for configuration
- Secret for API keys

---

## Success Metrics

| App | Primary Metric | Target |
|-----|---------------|--------|
| FAQ Assistant | Answer accuracy | >85% helpful ratings |
| Document Hub | Processing speed | <30s per document |
| Product Discovery | Search relevance | >80% top-10 relevance |
| Sentiment Tracker | Prediction accuracy | >75% churn prediction |
| Code Search | Developer satisfaction | >4.0/5.0 rating |
| Meeting Analyzer | Action item accuracy | >90% extraction accuracy |

---

## Next Steps

1. **Review & Approve** this proposal
2. **Set up base project structure** with common components
3. **Implement App 1 (FAQ Assistant)** as the foundation
4. **Create UI mockups** for each app
5. **Iterate** based on feedback

---

**Document Status:** Ready for Review
**Created:** January 2025
**Author:** AI Fabric Framework Team
