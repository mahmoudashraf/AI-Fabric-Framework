# AI Chat Session Module - Complete Implementation Specification
## Production-Ready, Framework-Compliant Design (Pipeline Architecture)

**Version:** 5.1 - Pipeline Architecture + Action Confirmation
**Date:** January 2026
**Status:** ✅ Implementation Ready
**Compliance:** 100% AI Fabric Framework Standards
**Architecture:** Pipeline-Based (Current Codebase)

---

## Document Purpose

**This is the SINGLE source of truth** for implementing the AI Chat Session module using the **Pipeline architecture** (current codebase structure).

**Key Updates from v4.0:**
1. ✅ **Pipeline Architecture:** Integration via `PipelineStep`s instead of modifying `RAGOrchestrator`
2. ✅ **Zero Core Changes:** `RAGOrchestrator` remains unchanged (thin wrapper)
3. ✅ **Composable Steps:** `ConversationEnrichmentStep` (Order 25), `ConfirmationResolutionStep` (Order 55), and `ConversationRecordingStep` (Order 95)
4. ✅ **Auto-Discovery:** Steps automatically included via Spring dependency injection
5. ✅ **Better Separation:** Each step has single responsibility
6. ✅ **Action Confirmation:** Two-step conversational confirmation for high-risk actions

---

## Table of Contents

1. [Module Overview](#1-module-overview)
2. [The Three Identifiers](#2-the-three-identifiers)
3. [Architecture](#3-architecture)
4. [Data Models](#4-data-models)
5. [Storage SPI & Default Implementation](#5-storage-spi--default-implementation)
6. [Access Control SPI](#6-access-control-spi)
7. [Memory Strategies](#7-memory-strategies)
8. [Service Implementation](#8-service-implementation)
9. [Pipeline Integration](#9-pipeline-integration) ⚠️ **UPDATED**
10. [Action Confirmation Workflow](#10-action-confirmation-workflow) 🆕 **NEW**
11. [Security](#11-security)
12. [Configuration](#12-configuration)
13. [Testing](#13-testing)
14. [User Guide](#14-user-guide)
15. [Implementation Checklist](#15-implementation-checklist)

---

## 1. Module Overview

### 1.1 What This Module Does

Enables **multi-turn conversations** by:

✅ **Tracking conversation history** (all query/response pairs)  
✅ **Enriching queries** with conversation context automatically  
✅ **Supporting multiple conversations** per user  
✅ **Enforcing secure access** to conversations  
✅ **Providing default storage** (JPA/Database) + allowing custom storage  
✅ **Supporting sliding window** memory (keeps N recent turns)  
✅ **Supporting summarization** (summarizes old turns, keeps recent ones)  

### 1.2 Integration Model (Pipeline Architecture)

**Everything goes through the Pipeline:**

```
Client Request
    ↓
RAGOrchestrator.orchestrate(query, context)
    ↓
Pipeline.execute(query, context)
    ↓
PipelineSteps (in order):
    10: SecurityAnalysisStep
    20: AccessControlStep
    25: ConversationEnrichmentStep  ← NEW: Loads history, enriches query
    30: PIIDetectionStep
    40: ComplianceCheckStep
    50: IntentExtractionStep
    55: ConfirmationResolutionStep  ← NEW: Handles action confirmations
    60: IntentHandlingStep
    70: MetadataBuildingStep  ← UPDATED: Includes conversationId
    80: SmartSuggestionsStep
    90: ResponseSanitizationStep
    95: ConversationRecordingStep  ← NEW: Records turn
    100: HistoryPersistenceStep
    ↓
Return OrchestrationResult
```

**Can we have orchestrator-less conversations?**

**Answer:** **NO** - Conversations ONLY work through the Pipeline.

**Why?**
- Conversations need the full orchestration flow (security, intent extraction, etc.)
- Recording turns without orchestration would bypass security
- Conversation enrichment needs the pipeline's LLM integration

**Use Cases:**
- ✅ Chat with orchestrator + conversationId → Full conversation tracking
- ✅ Single query with orchestrator, no conversationId → Stateless (current behavior)
- ❌ Chat without orchestrator → Not supported (and shouldn't be)

---

## 2. The Three Identifiers

**CRITICAL DESIGN:** Three separate identifiers with distinct purposes

| Field | Purpose | Required When | Can Be Null | Example |
|-------|---------|---------------|-------------|---------|
| **userId** | User identification | Authenticated | When anonymous | `"alice"` |
| **sessionId** | Anonymous tracking | No userId | When authenticated | `"anon-xyz"` |
| **conversationId** | Conversation tracking | Multi-turn chat | Single queries | `"conv-001"` |

### 2.1 Validation Rules

```java
OrchestrationContext.validate():
  - userId OR sessionId MUST be present (at least one identifier)
  - conversationId is OPTIONAL (enables chat when present)
```

### 2.2 Usage Matrix

| Scenario | userId | sessionId | conversationId | Result |
|----------|--------|-----------|----------------|--------|
| **Auth user, single query** | alice | null | null | Stateless ✅ |
| **Auth user, chat** | alice | null | conv-001 | Chat ✅ |
| **Auth user, multiple chats** | alice | null | conv-001, conv-002, conv-003 | Multiple conversations ✅ |
| **Anonymous, single query** | null | anon-x | null | Stateless ✅ |
| **Anonymous, chat** | null | anon-x | conv-abc | Chat ✅ |

---

## 3. Architecture

### 3.1 Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-core/                     # MINIMAL CHANGES
│   └── src/main/java/.../orchestration/
│       └── OrchestrationContext.java           # Add: conversationId field
│
└── ai-infrastructure-chat-session/            # NEW MODULE
    ├── pom.xml
    └── src/
        ├── main/java/com/ai/infrastructure/chat/
        │   ├── domain/
        │   │   ├── ChatSession.java
        │   │   ├── ChatTurn.java
        │   │   ├── SessionStatus.java
        │   │   └── MetadataConverter.java
        │   ├── service/
        │   │   ├── ChatSessionService.java
        │   │   └── ChatSessionServiceImpl.java
        │   ├── spi/
        │   │   ├── ChatSessionStorageProvider.java       # SPI - Users implement
        │   │   └── ChatSessionAccessControlPolicy.java   # SPI - Users implement
        │   ├── storage/
        │   │   └── DefaultDatabaseChatSessionStorage.java  # Default implementation
        │   ├── strategy/
        │   │   ├── MemoryStrategy.java
        │   │   ├── SlidingWindowMemoryStrategy.java     # Keeps N recent turns
        │   │   └── SummaryMemoryStrategy.java            # Summarizes old, keeps recent
        │   ├── pipeline/                                 # NEW: Pipeline integration
        │   │   ├── ConversationEnrichmentStep.java       # Order 25
        │   │   ├── ConfirmationResolutionStep.java      # Order 55
        │   │   └── ConversationRecordingStep.java       # Order 95
        │   ├── config/
        │   │   ├── ChatSessionProperties.java
        │   │   └── ChatSessionAutoConfiguration.java
        │   ├── exception/
        │   │   ├── SessionNotFoundException.java
        │   │   ├── SessionExpiredException.java
        │   │   ├── AccessDeniedException.java
        │   │   └── StorageException.java
        │   └── repository/
        │       └── ChatSessionRepository.java            # JPA Repository for default storage
        └── test/java/...
```

### 3.2 Pipeline Integration Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    RAGOrchestrator                           │
│              (ZERO changes - thin wrapper)                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Pipeline                                │
│         (Auto-discovers all PipelineSteps)                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              PipelineSteps (in execution order)              │
│                                                              │
│  10: SecurityAnalysisStep                                    │
│  20: AccessControlStep                                       │
│  25: ConversationEnrichmentStep  ← NEW (chat module)         │
│  30: PIIDetectionStep                                        │
│  40: ComplianceCheckStep                                     │
│  50: IntentExtractionStep                                    │
│  55: ConfirmationResolutionStep  ← NEW (chat module)        │
│  60: IntentHandlingStep                                      │
│  70: MetadataBuildingStep  ← UPDATED (includes convId)     │
│  80: SmartSuggestionsStep                                    │
│  90: ResponseSanitizationStep                                │
│  95: ConversationRecordingStep  ← NEW (chat module)          │
│  100: HistoryPersistenceStep                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Data Models

### 4.1 ChatTurn

```java
package com.ai.infrastructure.chat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a single user-AI exchange within a conversation.
 * 
 * <p>Immutable by design to ensure conversation integrity.</p>
 */
@Entity
@Table(
    name = "chat_turns",
    indexes = {
        @Index(name = "idx_session_timestamp", columnList = "session_id,timestamp")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT", name = "user_query")
    private String userQuery;

    @Column(nullable = false, columnDefinition = "TEXT", name = "ai_response")
    private String aiResponse;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Entity IDs used in RAG/relationship queries for this turn.
     */
    @ElementCollection
    @CollectionTable(
        name = "turn_entity_refs",
        joinColumns = @JoinColumn(name = "turn_id")
    )
    @Column(name = "entity_id")
    @Builder.Default
    private List<String> entityIds = new ArrayList<>();

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(length = 50, name = "model_used")
    private String modelUsed;

    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT", name = "turn_metadata")
    @Builder.Default
    private Map<String, Object> turnMetadata = new HashMap<>();

    /**
     * Formats turn for inclusion in LLM prompt.
     */
    public String toPromptFormat() {
        return String.format("User: %s\nAssistant: %s", userQuery, aiResponse);
    }
}
```

### 4.2 ChatSession

```java
package com.ai.infrastructure.chat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a conversation session with multiple turns.
 */
@Entity
@Table(name = "chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    private String id;  // conversationId

    @Column(nullable = false, name = "owner_id")
    private String ownerId;  // userId or sessionId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastInteractionAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ChatTurn> turns = new ArrayList<>();

    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT", name = "session_metadata")
    @Builder.Default
    private Map<String, Object> sessionMetadata = new HashMap<>();

    /**
     * Check if this session is owned by the given identifier.
     */
    public boolean isOwnedBy(String identifier) {
        return ownerId != null && ownerId.equals(identifier);
    }
}
```

---

## 5. Storage SPI & Default Implementation

### 5.1 Storage SPI

```java
package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.chat.domain.ChatSession;
import java.util.List;
import java.util.Optional;

/**
 * SPI for chat session storage.
 * 
 * <p><strong>DEFAULT IMPLEMENTATION:</strong> Framework provides a JPA/Database implementation.
 * Users can override with custom storage (Redis, MongoDB, etc.).</p>
 */
public interface ChatSessionStorageProvider {
    ChatSession save(ChatSession session);
    Optional<ChatSession> findById(String conversationId);
    void deleteById(String conversationId);
    List<ChatSession> findByOwnerId(String ownerId);
    List<ChatSession> findExpiredSessions(int ttlMinutes);
}
```

### 5.2 Default Database Storage

```java
package com.ai.infrastructure.chat.storage;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.repository.ChatSessionRepository;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(ChatSessionStorageProvider.class)
public class DefaultDatabaseChatSessionStorage implements ChatSessionStorageProvider {
    
    private final ChatSessionRepository repository;
    
    @Override
    public ChatSession save(ChatSession session) {
        ChatSession saved = repository.save(session);
        log.debug("Session saved to database: conversationId={}, owner={}", 
            saved.getId(), saved.getOwnerId());
        return saved;
    }
    
    @Override
    public Optional<ChatSession> findById(String conversationId) {
        return repository.findById(conversationId);
    }
    
    @Override
    public void deleteById(String conversationId) {
        repository.deleteById(conversationId);
        log.debug("Session deleted from database: {}", conversationId);
    }
    
    @Override
    public List<ChatSession> findByOwnerId(String ownerId) {
        return repository.findByOwnerId(ownerId);
    }
    
    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        return repository.findByLastInteractionAtBeforeAndStatus(
            cutoff,
            SessionStatus.ACTIVE
        );
    }
}
```

---

## 6. Access Control SPI

**Users MUST Implement:**

```java
package com.ai.infrastructure.chat.spi;

/**
 * SPI for chat session access control.
 * 
 * <p><strong>REQUIREMENT:</strong> Users MUST implement when chat module enabled.
 * Application fails at startup if missing.</p>
 */
public interface ChatSessionAccessControlPolicy {
    
    boolean canUserCreateConversation(String ownerId);
    boolean canUserAccessConversation(String requestingUser, String conversationId);
    boolean canUserDeleteConversation(String requestingUser, String conversationId);
    boolean canUserViewHistory(String requestingUser, String conversationId);
}
```

---

## 7. Memory Strategies

### 7.1 Strategy Interface

```java
package com.ai.infrastructure.chat.strategy;

import com.ai.infrastructure.chat.domain.ChatTurn;
import java.util.List;

public interface MemoryStrategy {
    String processHistory(List<ChatTurn> history);
    List<ChatTurn> prune(List<ChatTurn> history, int limit);
    String getStrategyName();
}
```

### 7.2 Sliding Window Strategy

```java
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "memory-strategy",
    havingValue = "SLIDING_WINDOW",
    matchIfMissing = true
)
public class SlidingWindowMemoryStrategy implements MemoryStrategy {
    
    private static final String STRATEGY_NAME = "SLIDING_WINDOW";

    @Override
    public String processHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        return history.stream()
            .map(ChatTurn::toPromptFormat)
            .collect(Collectors.joining("\n\n"));
    }

    @Override
    public List<ChatTurn> prune(List<ChatTurn> history, int limit) {
        if (history == null || history.isEmpty() || history.size() <= limit) {
            return history != null ? history : List.of();
        }
        int startIndex = history.size() - limit;
        return history.subList(startIndex, history.size());
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
}
```

### 7.3 Summary Strategy

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "memory-strategy",
    havingValue = "SUMMARY"
)
public class SummaryMemoryStrategy implements MemoryStrategy {
    
    private static final String STRATEGY_NAME = "SUMMARY";
    private static final int RECENT_TURNS_TO_KEEP_VERBATIM = 3;
    
    private final AICoreService llmService;

    @Override
    public String processHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        if (history.size() <= RECENT_TURNS_TO_KEEP_VERBATIM) {
            return formatTurns(history);
        }
        int splitIndex = history.size() - RECENT_TURNS_TO_KEEP_VERBATIM;
        List<ChatTurn> olderTurns = history.subList(0, splitIndex);
        List<ChatTurn> recentTurns = history.subList(splitIndex, history.size());
        String summary = generateSummary(olderTurns);
        String recentContext = formatTurns(recentTurns);
        return String.format(
            "Previous Conversation Summary:\n%s\n\nRecent Exchanges:\n%s",
            summary, recentContext
        );
    }

    @Override
    public List<ChatTurn> prune(List<ChatTurn> history, int limit) {
        // Implementation similar to processHistory but returns ChatTurn list
        // ...
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }
    
    private String formatTurns(List<ChatTurn> turns) {
        return turns.stream()
            .map(ChatTurn::toPromptFormat)
            .collect(Collectors.joining("\n\n"));
    }
    
    private String generateSummary(List<ChatTurn> turns) {
        // LLM summarization logic
        // ...
    }
}
```

---

## 8. Service Implementation

### 8.1 ChatSessionService Interface

```java
package com.ai.infrastructure.chat.service;

public interface ChatSessionService {
    String getConversationContext(String conversationId, String ownerId);
    void recordTurn(String conversationId, String ownerId, String userQuery, String aiResponse);
    ChatSession getSession(String conversationId, String ownerId);
    List<ChatSession> getUserConversations(String ownerId);
    void deleteConversation(String conversationId, String ownerId);
}
```

### 8.2 ChatSessionServiceImpl

```java
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ChatSessionAccessControlPolicy.class)
public class ChatSessionServiceImpl implements ChatSessionService {
    
    private final ChatSessionStorageProvider storage;
    private final ChatSessionAccessControlPolicy accessPolicy;
    private final MemoryStrategy memoryStrategy;
    private final ChatSessionProperties properties;
    
    @Override
    public String getConversationContext(String conversationId, String ownerId) {
        // Access control check
        if (!accessPolicy.canUserAccessConversation(ownerId, conversationId)) {
            throw new AccessDeniedException("Access denied to conversation: " + conversationId);
        }
        
        // Load session
        ChatSession session = storage.findById(conversationId)
            .orElseGet(() -> createNewSession(conversationId, ownerId));
        
        // Verify ownership
        if (!session.isOwnedBy(ownerId)) {
            throw new AccessDeniedException("Conversation owned by different user");
        }
        
        // Get history and process with memory strategy
        List<ChatTurn> history = session.getTurns();
        if (history.isEmpty()) {
            return "";
        }
        
        // Prune if needed
        int windowSize = properties.getDefaultWindowSize();
        List<ChatTurn> pruned = memoryStrategy.prune(history, windowSize);
        
        // Format for LLM
        return memoryStrategy.processHistory(pruned);
    }
    
    @Override
    public void recordTurn(String conversationId, String ownerId, 
                          String userQuery, String aiResponse) {
        // Access control check
        if (!accessPolicy.canUserAccessConversation(ownerId, conversationId)) {
            throw new AccessDeniedException("Access denied to conversation: " + conversationId);
        }
        
        // Load or create session
        ChatSession session = storage.findById(conversationId)
            .orElseGet(() -> createNewSession(conversationId, ownerId));
        
        // Verify ownership
        if (!session.isOwnedBy(ownerId)) {
            throw new AccessDeniedException("Cannot record to conversation owned by different user");
        }
        
        // Create turn
        ChatTurn turn = ChatTurn.builder()
            .userQuery(userQuery)
            .aiResponse(aiResponse)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Add turn and update session
        session.getTurns().add(turn);
        session.setLastInteractionAt(LocalDateTime.now());
        storage.save(session);
    }
    
    private ChatSession createNewSession(String conversationId, String ownerId) {
        return ChatSession.builder()
            .id(conversationId)
            .ownerId(ownerId)
            .status(SessionStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .lastInteractionAt(LocalDateTime.now())
            .turns(new ArrayList<>())
            .build();
    }
}
```

---

## 9. Pipeline Integration ⚠️ **UPDATED FOR PIPELINE ARCHITECTURE**

### 9.1 Integration Design

**CRITICAL:** Integration is via **PipelineSteps**, NOT by modifying `RAGOrchestrator`.

**Current Architecture:**
- `RAGOrchestrator` is a **thin wrapper** (only has `Pipeline` dependency)
- All logic is in **PipelineSteps**
- Steps are **auto-discovered** by Spring
- Steps execute in **order** (10, 20, 30, ..., 100)

**Integration Points:**
1. **ConversationEnrichmentStep** (Order 25) - Enriches query with history
2. **ConversationRecordingStep** (Order 95) - Records turn after processing
3. **MetadataBuildingStep** (Order 70) - Includes conversationId in metadata

### 9.2 Changes to OrchestrationContext

**File:** `ai-infrastructure-core/.../orchestration/OrchestrationContext.java`

**Add ONE field:**

```java
/**
 * Conversation ID for multi-turn chat tracking (optional).
 * 
 * <p><strong>When provided:</strong></p>
 * <ul>
 *   <li>Pipeline loads conversation history before intent extraction</li>
 *   <li>Query is enriched with conversation context</li>
 *   <li>Query/response is recorded as new turn after processing</li>
 * </ul>
 * 
 * <p><strong>When omitted:</strong> Stateless query (no history, no recording)</p>
 */
private String conversationId;

/**
 * Check if conversation tracking is enabled.
 */
public boolean hasConversation() {
    return conversationId != null && !conversationId.isBlank();
}
```

### 9.3 ConversationEnrichmentStep (Order 25)

**Purpose:** Load conversation history and enrich query **before** intent extraction.

**Order:** 25 (between AccessControlStep (20) and PIIDetectionStep (30))

**Why this order?**
- ✅ After security/access control (safety first)
- ✅ Before PII detection (history may contain PII)
- ✅ Before intent extraction (LLM needs context)

**File:** `ai-infrastructure-chat-session/.../pipeline/ConversationEnrichmentStep.java`

```java
package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.exception.AccessDeniedException;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Pipeline step that enriches queries with conversation history.
 * 
 * <p><strong>Execution Order:</strong> 25 (after AccessControlStep, before PIIDetectionStep)</p>
 * 
 * <p><strong>Behavior:</strong></p>
 * <ul>
 *   <li>If conversationId present: Loads history and enriches processedQuery</li>
 *   <li>If conversationId absent: No-op (passes through unchanged)</li>
 *   <li>On error: Graceful degradation (continues without history)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This step is thread-safe.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatSessionService.class)
public class ConversationEnrichmentStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "ConversationEnrichment";
    private static final int STEP_ORDER = 25;
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final Optional<ChatSessionService> chatSessionService;
    
    // =========================================================================
    // PipelineStep Implementation
    // =========================================================================
    
    @Override
    public String getStepName() {
        return STEP_NAME;
    }
    
    @Override
    public int getOrder() {
        return STEP_ORDER;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        // Skip if no conversation or service not available
        if (!context.getOrchestrationContext().hasConversation() || 
            chatSessionService.isEmpty()) {
            return context;  // No-op
        }
        
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        String originalQuery = context.getOriginalQuery();
        
        try {
            // Load conversation history
            String conversationHistory = chatSessionService.get()
                .getConversationContext(conversationId, ownerId);
            
            if (conversationHistory != null && !conversationHistory.isBlank()) {
                // Enrich query with history
                String enrichedQuery = String.format(
                    "Conversation History:\n%s\n\nCurrent Query: %s",
                    conversationHistory,
                    originalQuery
                );
                
                log.debug("Enriched query with conversation history: conversationId={}, " +
                    "historyLength={} chars", conversationId, conversationHistory.length());
                
                // Update processedQuery in context (immutable pattern)
                return context.toBuilder()
                    .processedQuery(enrichedQuery)
                    .build();
            }
            
            log.debug("New conversation (no history): {}", conversationId);
            return context;
            
        } catch (AccessDeniedException ex) {
            log.warn("Access denied loading conversation {}: {}. Continuing without history.", 
                conversationId, ex.getMessage());
            return context;  // Graceful degradation
            
        } catch (Exception ex) {
            log.warn("Failed to load conversation history for {}: {}. Continuing without history.", 
                conversationId, ex.getMessage(), ex);
            return context;  // Graceful degradation
        }
    }
}
```

### 9.4 ConversationRecordingStep (Order 95)

**Purpose:** Record query/response turn **after** response sanitization.

**Order:** 95 (between ResponseSanitizationStep (90) and HistoryPersistenceStep (100))

**Why this order?**
- ✅ After response is sanitized (record final response)
- ✅ Before history persistence (conversation is part of history)
- ✅ Non-blocking (failures don't break the request)

**File:** `ai-infrastructure-chat-session/.../pipeline/ConversationRecordingStep.java`

```java
package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.exception.AccessDeniedException;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Pipeline step that records conversation turns.
 * 
 * <p><strong>Execution Order:</strong> 95 (after ResponseSanitizationStep, before HistoryPersistenceStep)</p>
 * 
 * <p><strong>Behavior:</strong></p>
 * <ul>
 *   <li>If conversationId present: Records query/response as new turn</li>
 *   <li>If conversationId absent: No-op (passes through unchanged)</li>
 *   <li>On error: Logs error but doesn't fail request (graceful degradation)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This step is thread-safe.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatSessionService.class)
public class ConversationRecordingStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "ConversationRecording";
    private static final int STEP_ORDER = 95;
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final Optional<ChatSessionService> chatSessionService;
    
    // =========================================================================
    // PipelineStep Implementation
    // =========================================================================
    
    @Override
    public String getStepName() {
        return STEP_NAME;
    }
    
    @Override
    public int getOrder() {
        return STEP_ORDER;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        // Skip if already terminated, no conversation, service not available, or no result
        if (context.isShouldTerminate() ||
            !context.getOrchestrationContext().hasConversation() ||
            chatSessionService.isEmpty() ||
            context.getIntentResult() == null) {
            return context;
        }
        
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        String originalQuery = context.getOriginalQuery();  // Use original, not enriched
        String aiResponse = context.getIntentResult().getMessage();
        
        // Skip if no response to record
        if (aiResponse == null || aiResponse.isBlank()) {
            log.debug("No response to record for conversation: {}", conversationId);
            return context;
        }
        
        try {
            // Record turn
            chatSessionService.get().recordTurn(
                conversationId,
                ownerId,
                originalQuery,  // Original query (before enrichment)
                aiResponse
            );
            
            log.debug("Turn recorded: conversationId={}, owner={}", 
                conversationId, ownerId);
            
            return context;
            
        } catch (AccessDeniedException ex) {
            log.error("Access denied recording turn to conversation {}: {}",
                conversationId, ex.getMessage());
            // Don't fail request - user still gets response
            return context;
            
        } catch (Exception ex) {
            log.error("Failed to record turn to conversation {}: {}. User still receives response.",
                conversationId, ex.getMessage(), ex);
            // Graceful degradation - recording failure shouldn't break UX
            return context;
        }
    }
}
```

### 9.5 Update MetadataBuildingStep (Order 70)

**Purpose:** Include `conversationId` in response metadata.

**File:** `ai-infrastructure-core/.../pipeline/steps/MetadataBuildingStep.java`

**Add one line:**

```java
@Override
public PipelineContext process(PipelineContext context) {
    // ... existing metadata building ...
    
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("requestId", context.getRequestId());
    metadata.put("sessionId", context.getOrchestrationContext().getSessionId());
    metadata.put("conversationId", context.getOrchestrationContext().getConversationId());  // ← ADD THIS
    // ... rest of metadata ...
    
    return context.toBuilder()
        .metadata(metadata)
        .build();
}
```

### 9.6 Summary of Changes

| Component | Change Type | Lines | Impact |
|-----------|-------------|-------|--------|
| `OrchestrationContext` | Add field | +3 | Minimal |
| `ConversationEnrichmentStep` | New class | ~120 | New |
| `ConfirmationResolutionStep` | New class | ~150 | New |
| `ConversationRecordingStep` | New class | ~100 | New |
| `MetadataBuildingStep` | Update | +1 | Minimal |
| `RAGOrchestrator` | **ZERO changes** | 0 | ✅ None |

**Total Core Changes:** ~4 lines (only OrchestrationContext)
**Total Module Changes:** ~370 lines (3 new PipelineSteps)
**Breaking Changes:** ZERO
**Architecture Alignment:** ✅ 100% Pipeline-based

---

## 10. Action Confirmation Workflow 🆕

### 10.1 Overview

**Problem:** Some actions are high-risk (e.g., "cancel my subscription", "delete all data") and should require **explicit user confirmation** before execution.

**Solution:** Two-step conversational confirmation using conversation metadata to track pending actions.

**Key Design:**
- ✅ **Conversational** - Natural "are you sure?" → "yes" flow
- ✅ **Stateful** - Pending actions stored in conversation metadata
- ✅ **Secure** - Confirmation tied to conversation + user ownership
- ✅ **Non-Blocking** - Actions without confirmations execute immediately (backward compatible)

---

### 10.2 Confirmation Flow Diagram

```
Turn 1: User requests high-risk action
─────────────────────────────────────
User: "cancel my subscription"
    ↓
IntentExtractionStep (50): Detects ACTION intent
    ↓
ConfirmationResolutionStep (55):
    - Checks if action requires confirmation
    - No pending confirmation found
    - Passes through to IntentHandlingStep
    ↓
IntentHandlingStep (60):
    - Detects handler.requiresConfirmation() == true
    - Stores pending action in conversation metadata:
      {
        "pendingAction": "cancel_subscription",
        "pendingActionParams": {...},
        "pendingActionTimestamp": "2026-01-07T10:00:00"
      }
    - Returns CONFIRMATION_REQUIRED result
    ↓
User sees: "You are about to cancel your subscription. This cannot be undone.
            Reply 'yes' to confirm or 'no' to cancel."


Turn 2: User confirms
─────────────────────
User: "yes"
    ↓
IntentExtractionStep (50): May detect INFORMATION or OUT_OF_SCOPE intent
    ↓
ConfirmationResolutionStep (55):
    - Checks conversation metadata
    - Finds pending action: "cancel_subscription"
    - Detects confirmation response ("yes")
    - Creates ACTION intent from pending data
    - Clears pending action from metadata
    - REPLACES extracted intent with confirmed action
    ↓
IntentHandlingStep (60):
    - Receives ACTION intent for "cancel_subscription"
    - Executes action (requiresConfirmation() bypassed - already confirmed)
    - Returns ACTION_EXECUTED result
    ↓
User sees: "Subscription cancelled successfully."
```

---

### 10.3 Enhanced ActionHandler Interface

**Current Interface:**
```java
public interface ActionHandler {
    AIActionMetaData getActionMetadata();
    boolean validateActionAllowed(String userId);
    String getConfirmationMessage(Map<String, Object> params);
    ActionResult executeAction(Map<String, Object> params, String userId);
    ActionResult handleError(Exception e, String userId);
}
```

**Add Method:**
```java
public interface ActionHandler {
    // ... existing methods ...

    /**
     * Indicates if this action requires explicit user confirmation.
     *
     * <p>When {@code true}:</p>
     * <ul>
     *   <li>First invocation stores action in conversation metadata</li>
     *   <li>User must explicitly confirm (e.g., "yes", "confirm")</li>
     *   <li>Second invocation executes the action</li>
     * </ul>
     *
     * <p>When {@code false} (default): Action executes immediately.</p>
     *
     * @return true if confirmation is required, false otherwise
     */
    default boolean requiresConfirmation() {
        return false;  // Default: no confirmation needed
    }
}
```

---

### 10.4 New OrchestrationResultType

**File:** `ai-infrastructure-core/.../orchestration/OrchestrationResult.java`

**Add Enum Value:**
```java
public enum OrchestrationResultType {
    // ... existing values ...
    ACTION_EXECUTED,
    ACTION_DENIED,

    /**
     * Action requires user confirmation before execution.
     *
     * <p>The action is stored in conversation metadata. User must
     * respond with confirmation (e.g., "yes") in next turn.</p>
     */
    CONFIRMATION_REQUIRED,  // ← ADD THIS

    // ... other values ...
}
```

---

### 10.5 ConfirmationResolutionStep Implementation

**File:** `ai-infrastructure-chat-session/.../pipeline/ConfirmationResolutionStep.java`

**Order:** 55 (between IntentExtractionStep (50) and IntentHandlingStep (60))

**Why this order?**
- ✅ After intent extraction (can see what user intent was detected)
- ✅ Before intent handling (can replace intent before execution)
- ✅ After security/access control (only process confirmed actions from authorized users)

```java
package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pipeline step that resolves action confirmation requests in conversations.
 *
 * <p><strong>Execution Order:</strong> 55 (after IntentExtractionStep, before IntentHandlingStep)</p>
 *
 * <p><strong>Behavior:</strong></p>
 * <ul>
 *   <li>Checks for pending actions in conversation metadata</li>
 *   <li>Detects confirmation responses ("yes", "confirm", "proceed")</li>
 *   <li>Detects cancellation responses ("no", "cancel", "abort")</li>
 *   <li>Replaces extracted intent with pending action intent when confirmed</li>
 *   <li>Clears pending action when confirmed or cancelled</li>
 * </ul>
 *
 * <p><strong>Timeout:</strong> Pending confirmations expire after configured TTL (default: 5 minutes)</p>
 *
 * @see IntentHandlingStep
 * @see ChatSessionService
 * @since 5.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatSessionService.class)
public class ConfirmationResolutionStep implements PipelineStep {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final String STEP_NAME = "ConfirmationResolution";
    private static final int STEP_ORDER = 55;

    // Metadata keys for pending actions
    private static final String METADATA_KEY_PENDING_ACTION = "pendingAction";
    private static final String METADATA_KEY_PENDING_PARAMS = "pendingActionParams";
    private static final String METADATA_KEY_PENDING_TIMESTAMP = "pendingActionTimestamp";
    private static final String METADATA_KEY_PENDING_CONFIRMATION_MSG = "pendingConfirmationMessage";

    // Confirmation timeout (5 minutes)
    private static final long CONFIRMATION_TIMEOUT_MINUTES = 5;

    // Confirmation keywords (positive)
    private static final Set<String> CONFIRMATION_KEYWORDS = Set.of(
        "yes", "y", "confirm", "confirmed", "proceed", "ok", "okay", "sure", "continue"
    );

    // Cancellation keywords (negative)
    private static final Set<String> CANCELLATION_KEYWORDS = Set.of(
        "no", "n", "cancel", "abort", "stop", "nevermind", "never mind"
    );

    // =========================================================================
    // Dependencies
    // =========================================================================

    private final Optional<ChatSessionService> chatSessionService;

    // =========================================================================
    // PipelineStep Implementation
    // =========================================================================

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        // Skip if no conversation or service not available
        if (!context.getOrchestrationContext().hasConversation() ||
            chatSessionService.isEmpty()) {
            return context;
        }

        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        String originalQuery = context.getOriginalQuery();

        try {
            // Load conversation session
            ChatSession session = chatSessionService.get().getSession(conversationId, ownerId);
            Map<String, Object> metadata = session.getSessionMetadata();

            // Check for pending action
            if (!metadata.containsKey(METADATA_KEY_PENDING_ACTION)) {
                return context;  // No pending action
            }

            // Extract pending action details
            String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);
            Map<String, Object> pendingParams = (Map<String, Object>) metadata.get(METADATA_KEY_PENDING_PARAMS);
            String timestampStr = (String) metadata.get(METADATA_KEY_PENDING_TIMESTAMP);

            // Check timeout
            if (isConfirmationExpired(timestampStr)) {
                log.warn("Pending action '{}' expired for conversation {}",
                    pendingAction, conversationId);
                clearPendingAction(conversationId, ownerId, metadata);
                return context;  // Expired - clear and continue normally
            }

            // Detect confirmation or cancellation
            ConfirmationResponse response = detectConfirmationResponse(originalQuery);

            if (response == ConfirmationResponse.CONFIRMED) {
                log.info("User confirmed pending action '{}' for conversation {}",
                    pendingAction, conversationId);

                // Create ACTION intent from pending action
                Intent confirmedIntent = Intent.builder()
                    .type(IntentType.ACTION)
                    .action(pendingAction)
                    .actionParams(pendingParams != null ? pendingParams : new HashMap<>())
                    .confidence(1.0)
                    .originalText(originalQuery)
                    .build();

                MultiIntentResponse confirmedResponse = MultiIntentResponse.builder()
                    .intents(List.of(confirmedIntent))
                    .compound(false)
                    .build();

                // Clear pending action
                clearPendingAction(conversationId, ownerId, metadata);

                // Replace intent in context
                return context.toBuilder()
                    .intentResponse(confirmedResponse)
                    .build();

            } else if (response == ConfirmationResponse.CANCELLED) {
                log.info("User cancelled pending action '{}' for conversation {}",
                    pendingAction, conversationId);

                // Clear pending action
                clearPendingAction(conversationId, ownerId, metadata);

                // Create OUT_OF_SCOPE intent with cancellation message
                Intent cancelIntent = Intent.builder()
                    .type(IntentType.OUT_OF_SCOPE)
                    .confidence(1.0)
                    .originalText(originalQuery)
                    .build();

                MultiIntentResponse cancelResponse = MultiIntentResponse.builder()
                    .intents(List.of(cancelIntent))
                    .compound(false)
                    .metadata(Map.of(
                        "cancellationMessage", "Action cancelled. Your " + pendingAction + " was not executed."
                    ))
                    .build();

                return context.toBuilder()
                    .intentResponse(cancelResponse)
                    .build();

            } else {
                // Ambiguous response - keep pending, let user know
                log.debug("Ambiguous response to pending action '{}' for conversation {}: {}",
                    pendingAction, conversationId, originalQuery);

                // Return context unchanged - user will get normal processing
                // but we keep the pending action for next turn
                return context;
            }

        } catch (Exception ex) {
            log.warn("Failed to process confirmation for conversation {}: {}. Continuing normally.",
                conversationId, ex.getMessage());
            return context;  // Graceful degradation
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Detect if query is a confirmation response.
     */
    private ConfirmationResponse detectConfirmationResponse(String query) {
        if (query == null || query.isBlank()) {
            return ConfirmationResponse.AMBIGUOUS;
        }

        String normalized = query.toLowerCase().trim();

        // Check for positive confirmation
        if (CONFIRMATION_KEYWORDS.contains(normalized)) {
            return ConfirmationResponse.CONFIRMED;
        }

        // Check for negative cancellation
        if (CANCELLATION_KEYWORDS.contains(normalized)) {
            return ConfirmationResponse.CANCELLED;
        }

        // Check for sentence-level confirmation
        if (normalized.matches(".*(yes|confirm|proceed).*") &&
            !normalized.matches(".*(no|cancel|don't|dont).*")) {
            return ConfirmationResponse.CONFIRMED;
        }

        if (normalized.matches(".*(no|cancel|abort|stop).*") &&
            !normalized.matches(".*(yes|confirm|proceed).*")) {
            return ConfirmationResponse.CANCELLED;
        }

        return ConfirmationResponse.AMBIGUOUS;
    }

    /**
     * Check if confirmation has expired.
     */
    private boolean isConfirmationExpired(String timestampStr) {
        if (timestampStr == null) {
            return true;  // No timestamp = expired
        }

        try {
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr);
            long minutesElapsed = ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now());
            return minutesElapsed > CONFIRMATION_TIMEOUT_MINUTES;
        } catch (Exception ex) {
            log.warn("Failed to parse confirmation timestamp: {}", timestampStr);
            return true;  // Parse error = expired
        }
    }

    /**
     * Clear pending action from conversation metadata.
     */
    private void clearPendingAction(String conversationId, String ownerId,
                                    Map<String, Object> metadata) {
        metadata.remove(METADATA_KEY_PENDING_ACTION);
        metadata.remove(METADATA_KEY_PENDING_PARAMS);
        metadata.remove(METADATA_KEY_PENDING_TIMESTAMP);
        metadata.remove(METADATA_KEY_PENDING_CONFIRMATION_MSG);

        try {
            chatSessionService.get().updateSessionMetadata(conversationId, ownerId, metadata);
            log.debug("Cleared pending action for conversation {}", conversationId);
        } catch (Exception ex) {
            log.error("Failed to clear pending action for conversation {}: {}",
                conversationId, ex.getMessage());
        }
    }

    // =========================================================================
    // Inner Enum
    // =========================================================================

    private enum ConfirmationResponse {
        CONFIRMED,   // User said yes
        CANCELLED,   // User said no
        AMBIGUOUS    // Unclear - process as normal query
    }
}
```

---

### 10.6 Enhanced IntentHandlingStep

**File:** `ai-infrastructure-core/.../pipeline/steps/IntentHandlingStep.java`

**Add logic to detect confirmation requirement:**

```java
// In handleAction() method, BEFORE executing action:

private OrchestrationResult handleAction(
    Intent intent,
    PipelineContext context,
    String actionName,
    Map<String, Object> params
) {
    // ... existing permission checks ...

    ActionHandler handler = registry.getHandler(actionName);

    // ... existing authorization check ...

    // ========== NEW: Check if confirmation required ==========
    if (handler.requiresConfirmation() && context.getOrchestrationContext().hasConversation()) {
        // This action requires confirmation and we're in a conversation
        return requestConfirmation(handler, actionName, params, context);
    }
    // =========================================================

    // Get confirmation message (for informational purposes if no confirmation needed)
    String confirmationMessage = handler.getConfirmationMessage(params);

    // Execute action
    ActionResult actionResult = handler.executeAction(params, identifier);

    // ... rest of existing code ...
}

/**
 * Request user confirmation for high-risk action.
 */
private OrchestrationResult requestConfirmation(
    ActionHandler handler,
    String actionName,
    Map<String, Object> params,
    PipelineContext context
) {
    String conversationId = context.getOrchestrationContext().getConversationId();
    String ownerId = context.getIdentifier();
    String confirmationMessage = handler.getConfirmationMessage(params);

    try {
        // Store pending action in conversation metadata
        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        Map<String, Object> metadata = new HashMap<>(session.getSessionMetadata());

        metadata.put("pendingAction", actionName);
        metadata.put("pendingActionParams", params);
        metadata.put("pendingActionTimestamp", LocalDateTime.now().toString());
        metadata.put("pendingConfirmationMessage", confirmationMessage);

        chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);

        log.info("Stored pending action '{}' for conversation {}", actionName, conversationId);

        // Return confirmation request result
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.CONFIRMATION_REQUIRED)
            .success(false)  // Not executed yet
            .message(confirmationMessage + "\n\nReply 'yes' to confirm or 'no' to cancel.")
            .data(Map.of(
                "action", actionName,
                "requiresConfirmation", true,
                "confirmationMessage", confirmationMessage,
                "metadata", handler.getActionMetadata()
            ))
            .build();

    } catch (Exception ex) {
        log.error("Failed to store pending confirmation for action '{}': {}",
            actionName, ex.getMessage());

        // Graceful fallback - execute without confirmation
        log.warn("Executing action '{}' without confirmation due to storage error", actionName);
        ActionResult result = handler.executeAction(params, ownerId);

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ACTION_EXECUTED)
            .success(result.isSuccess())
            .message(result.getMessage() + " (confirmation storage failed)")
            .data(Map.of("actionResult", result))
            .build();
    }
}
```

---

### 10.7 ChatSessionService Enhancement

**Add Method to Service Interface:**

```java
public interface ChatSessionService {
    // ... existing methods ...

    /**
     * Update session metadata without recording a turn.
     *
     * <p>Used for storing pending actions, user preferences, etc.</p>
     *
     * @param conversationId the conversation ID
     * @param ownerId the owner ID
     * @param metadata the updated metadata
     */
    void updateSessionMetadata(String conversationId, String ownerId, Map<String, Object> metadata);
}
```

**Implementation:**

```java
@Override
public void updateSessionMetadata(String conversationId, String ownerId,
                                  Map<String, Object> metadata) {
    // Access control check
    if (!accessPolicy.canUserAccessConversation(ownerId, conversationId)) {
        throw new AccessDeniedException("Access denied to conversation: " + conversationId);
    }

    // Load session
    ChatSession session = storage.findById(conversationId)
        .orElseThrow(() -> new SessionNotFoundException("Conversation not found: " + conversationId));

    // Verify ownership
    if (!session.isOwnedBy(ownerId)) {
        throw new AccessDeniedException("Conversation owned by different user");
    }

    // Update metadata
    session.setSessionMetadata(metadata);
    session.setLastInteractionAt(LocalDateTime.now());
    storage.save(session);

    log.debug("Updated metadata for conversation {}", conversationId);
}
```

---

### 10.8 Example Flows

#### Example 1: Cancel Subscription with Confirmation

**ActionHandler Implementation:**

```java
@Component
public class CancelSubscriptionHandler implements ActionHandler {

    @Override
    public String getActionName() {
        return "cancel_subscription";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;  // High-risk action
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String reason = (String) params.getOrDefault("reason", "unspecified");
        return String.format(
            "You are about to cancel your subscription. Reason: %s. " +
            "This action cannot be undone and you will lose access to premium features.",
            reason
        );
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Execute cancellation
        subscriptionService.cancelSubscription(userId);

        return ActionResult.builder()
            .success(true)
            .message("Subscription cancelled successfully")
            .data(Map.of(
                "effectiveDate", LocalDate.now().toString(),
                "refundEligible", false
            ))
            .build();
    }

    // ... other methods ...
}
```

**Conversation Flow:**

```
Turn 1:
───────
User: "I want to cancel my subscription because it's too expensive"

IntentExtraction: Detects ACTION intent (cancel_subscription, reason="too expensive")
    ↓
ConfirmationResolution: No pending action found → pass through
    ↓
IntentHandling:
    - Detects requiresConfirmation() == true
    - Stores pending action in conversation metadata
    - Returns CONFIRMATION_REQUIRED
    ↓
Response: {
  "type": "CONFIRMATION_REQUIRED",
  "success": false,
  "message": "You are about to cancel your subscription. Reason: too expensive.
              This action cannot be undone and you will lose access to premium features.

              Reply 'yes' to confirm or 'no' to cancel.",
  "data": {
    "action": "cancel_subscription",
    "requiresConfirmation": true,
    "confirmationMessage": "...",
    "metadata": {...}
  }
}


Turn 2:
───────
User: "yes"

IntentExtraction: May detect OUT_OF_SCOPE or INFORMATION intent
    ↓
ConfirmationResolution:
    - Finds pending action: "cancel_subscription"
    - Detects confirmation ("yes")
    - Creates ACTION intent from pending data
    - Clears pending action
    - REPLACES intent in context
    ↓
IntentHandling:
    - Receives ACTION intent
    - requiresConfirmation() is bypassed (already confirmed)
    - Executes cancellation
    ↓
Response: {
  "type": "ACTION_EXECUTED",
  "success": true,
  "message": "Subscription cancelled successfully",
  "data": {
    "actionResult": {
      "effectiveDate": "2026-01-07",
      "refundEligible": false
    }
  }
}
```

#### Example 2: User Cancels Confirmation

```
Turn 1:
───────
User: "delete all my data"

Response: "You are about to permanently delete all your data.
           This action cannot be undone and all your information will be lost.

           Reply 'yes' to confirm or 'no' to cancel."


Turn 2:
───────
User: "no, wait, I changed my mind"

ConfirmationResolution:
    - Finds pending action: "delete_all_data"
    - Detects cancellation ("no")
    - Clears pending action
    - Creates OUT_OF_SCOPE intent with cancellation message
    ↓
Response: "Action cancelled. Your delete_all_data was not executed."
```

#### Example 3: Confirmation Timeout

```
Turn 1:
───────
User: "cancel my plan"

Response: "Confirm cancellation? Reply 'yes' or 'no'."

[User waits 6 minutes]

Turn 2:
───────
User: "yes"

ConfirmationResolution:
    - Finds pending action: "cancel_subscription"
    - Checks timestamp: 6 minutes ago
    - TIMEOUT! (> 5 minutes)
    - Clears expired pending action
    - Passes through to normal processing
    ↓
Response: "I'm not sure what you're confirming. Could you clarify?"
```

---

### 10.9 Configuration

```yaml
ai:
  chat:
    confirmation:
      enabled: true
      timeout-minutes: 5           # Confirmation expiration
      keywords-positive:           # Custom confirmation words
        - yes
        - confirm
        - proceed
      keywords-negative:           # Custom cancellation words
        - no
        - cancel
        - abort
```

---

### 10.10 Security Considerations

1. **Ownership Verification:** Pending actions tied to conversation owner
2. **Timeout:** Confirmations expire after 5 minutes
3. **Single Use:** Pending action cleared after confirmation/cancellation
4. **Access Control:** Full access control checks on metadata updates
5. **Audit Trail:** All confirmations logged with user ID and timestamp

---

## 11. Security

### 11.1 Access Control Enforcement

**All operations check ownership:**

```java
// In ChatSessionServiceImpl
if (!accessPolicy.canUserAccessConversation(ownerId, conversationId)) {
    throw new AccessDeniedException("Access denied");
}

// Verify ownership
if (!session.isOwnedBy(ownerId)) {
    throw new AccessDeniedException("Conversation owned by different user");
}
```

**Fail-Closed Model:**
- If ownership check fails → DENY
- If policy check fails → DENY
- If ANY check fails → Log + throw exception

### 11.2 Pipeline Step Security

**ConversationEnrichmentStep:**
- ✅ Loads history only after access control (Order 20)
- ✅ Verifies ownership before loading
- ✅ Graceful degradation on access denied (continues without history)

**ConversationRecordingStep:**
- ✅ Records only after all security checks passed
- ✅ Verifies ownership before recording
- ✅ Non-blocking (errors don't fail request)

---

## 12. Configuration

### 12.1 Module Configuration

```yaml
ai:
  chat:
    enabled: true                      # Enable chat module
    memory-strategy: SLIDING_WINDOW    # Options: SLIDING_WINDOW, SUMMARY
    default-window-size: 5             # For SLIDING_WINDOW: how many recent turns
    ttl-minutes: 60                    # Conversation expiration
    hot-cache-size: 1000               # In-memory cache size
    enable-auto-cleanup: true          # Automatic cleanup
    cleanup-schedule: "0 0 * * * *"    # Every hour
```

### 12.2 Storage Configuration

**Default (Database - no config needed):**
```yaml
# Framework uses DefaultDatabaseChatSessionStorage automatically
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
  jpa:
    hibernate:
      ddl-auto: update
```

---

## 13. Testing

### 13.1 Pipeline Step Tests

**ConversationEnrichmentStepTest.java:**
```java
✅ shouldEnrichQueryWithHistory
✅ shouldSkipWhenNoConversationId
✅ shouldSkipWhenServiceNotAvailable
✅ shouldHandleAccessDeniedGracefully
✅ shouldHandleStorageErrorGracefully
✅ shouldUseOriginalQueryWhenNoHistory
✅ shouldUpdateProcessedQueryInContext
```

**ConfirmationResolutionStepTest.java:** 🆕
```java
✅ shouldDetectPositiveConfirmation
✅ shouldDetectNegativeConfirmation
✅ shouldHandleAmbiguousResponse
✅ shouldReplaceIntentOnConfirmation
✅ shouldCreateCancellationIntentOnNegative
✅ shouldClearPendingActionAfterConfirmation
✅ shouldHandleExpiredConfirmation
✅ shouldSkipWhenNoPendingAction
✅ shouldSkipWhenNoConversationId
✅ shouldHandleMultipleKeywordVariations
```

**ConversationRecordingStepTest.java:**
```java
✅ shouldRecordTurnWhenConversationIdPresent
✅ shouldSkipWhenNoConversationId
✅ shouldSkipWhenAlreadyTerminated
✅ shouldSkipWhenNoResponse
✅ shouldHandleAccessDeniedGracefully
✅ shouldHandleStorageErrorGracefully
✅ shouldUseOriginalQueryNotEnriched
```

### 13.2 Integration Tests

**ChatSessionPipelineIntegrationTest.java:**
```java
✅ shouldEnrichQueryAndRecordTurnInFullPipeline
✅ shouldHandleMultipleTurnsInConversation
✅ shouldRespectAccessControlInPipeline
✅ shouldWorkWithSlidingWindowStrategy
✅ shouldWorkWithSummaryStrategy
```

**ConfirmationWorkflowIntegrationTest.java:** 🆕
```java
✅ shouldRequestConfirmationForHighRiskAction
✅ shouldExecuteActionAfterPositiveConfirmation
✅ shouldCancelActionAfterNegativeConfirmation
✅ shouldHandleConfirmationTimeout
✅ shouldBypassConfirmationForNormalActions
✅ shouldHandleAmbiguousConfirmationResponse
✅ shouldStoreAndRetrievePendingActionMetadata
✅ shouldRespectOwnershipOnConfirmation
```

---

## 14. User Guide

### 14.1 Quick Start

**Step 1: Add Dependency**
```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-chat-session</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Step 2: Implement Access Control**
```java
@Component
public class MyChatAccessPolicy implements ChatSessionAccessControlPolicy {
    // Implementation
}
```

**Step 3: Enable Module**
```yaml
ai:
  chat:
    enabled: true
    memory-strategy: SLIDING_WINDOW
```

**Step 4: Use in Code**
```java
OrchestrationContext ctx = OrchestrationContext.builder()
    .userId("user-123")
    .conversationId("conv-001")  // ← Enable conversation tracking
    .build();

OrchestrationResult result = orchestrator.orchestrate("What did we discuss?", ctx);
// Pipeline automatically:
// 1. Loads conversation history
// 2. Enriches query with context
// 3. Processes through all steps
// 4. Records turn
```

---

## 15. Implementation Checklist

### Phase 1: Foundation
- [ ] Create module structure
- [ ] Implement ChatSession domain model
- [ ] Implement ChatTurn domain model (with entityIds)
- [ ] Implement MetadataConverter
- [ ] Define SessionStatus enum
- [ ] Extract all constants

### Phase 2: Storage
- [ ] Define ChatSessionStorageProvider SPI
- [ ] Implement DefaultDatabaseChatSessionStorage
- [ ] Implement ChatSessionRepository (JPA)
- [ ] Test default storage

### Phase 3: Access Control
- [ ] Define ChatSessionAccessControlPolicy SPI
- [ ] Document required implementation
- [ ] Add security enforcement in service

### Phase 4: Memory Strategies
- [ ] Define MemoryStrategy interface
- [ ] Implement SlidingWindowMemoryStrategy
- [ ] Implement SummaryMemoryStrategy
- [ ] Test both strategies

### Phase 5: Service
- [ ] Implement ChatSessionService interface
- [ ] Implement ChatSessionServiceImpl
- [ ] Add application-level caching
- [ ] Add error handling with constants

### Phase 6: Pipeline Integration ⚠️ **UPDATED**
- [ ] Add conversationId to OrchestrationContext
- [ ] Add hasConversation() method to OrchestrationContext
- [ ] Create ConversationEnrichmentStep (Order 25)
- [ ] Create ConfirmationResolutionStep (Order 55)
- [ ] Create ConversationRecordingStep (Order 95)
- [ ] Update MetadataBuildingStep (include conversationId)
- [ ] Test pipeline integration

### Phase 7: Action Confirmation Workflow 🆕 **NEW**
- [ ] Add requiresConfirmation() method to ActionHandler interface
- [ ] Add CONFIRMATION_REQUIRED to OrchestrationResultType enum
- [ ] Add updateSessionMetadata() to ChatSessionService
- [ ] Update IntentHandlingStep with confirmation detection
- [ ] Add confirmation constants (keywords, timeout)
- [ ] Test confirmation flow (positive, negative, timeout)

### Phase 8: Testing
- [ ] Unit tests for ConversationEnrichmentStep (7+ tests)
- [ ] Unit tests for ConfirmationResolutionStep (10+ tests)
- [ ] Unit tests for ConversationRecordingStep (7+ tests)
- [ ] Unit tests for strategies (18+ tests)
- [ ] Unit tests for storage (8+ tests)
- [ ] Integration tests - pipeline flow (10+ tests)
- [ ] Integration tests - confirmation workflow (8+ tests)
- [ ] RealAPI tests (5+ tests)

---

## Summary

### Key Changes from v4.0

| Aspect | v4.0 (Old) | v5.1 (New) |
|--------|------------|-------------|
| **Integration** | Modify RAGOrchestrator | PipelineSteps |
| **Core Changes** | ~40 lines | ~7 lines |
| **Pipeline Steps** | 2 (Enrich + Record) | 3 (Enrich + Confirm + Record) |
| **Architecture** | Monolithic | Pipeline-based |
| **Testability** | Hard (monolithic) | Easy (isolated steps) |
| **Extensibility** | Limited | High (composable) |
| **Confirmations** | Not supported | Two-step conversational |

### Benefits of Pipeline Approach

✅ **Zero Core Changes:** `RAGOrchestrator` untouched
✅ **Better Architecture:** Separation of concerns
✅ **More Testable:** Isolated step testing
✅ **More Extensible:** Users can add custom steps
✅ **Auto-Discovery:** Spring automatically includes steps
✅ **Framework Aligned:** Follows current codebase patterns

### New in v5.1: Action Confirmation Workflow

✅ **Conversational Confirmations:** Natural "are you sure?" → "yes" flow
✅ **Stateful Tracking:** Pending actions in conversation metadata
✅ **Secure:** Ownership verification + timeout protection
✅ **Flexible:** Action handlers opt-in via `requiresConfirmation()`
✅ **Backward Compatible:** Actions without confirmation work as before
✅ **Graceful Degradation:** Storage failures fall back to immediate execution

### Implementation Summary

**Core Changes:**
- `OrchestrationContext`: Add `conversationId` field + `hasConversation()` method (~6 lines)
- `ActionHandler`: Add `requiresConfirmation()` default method (~5 lines)
- `OrchestrationResultType`: Add `CONFIRMATION_REQUIRED` enum value (~1 line)
- `MetadataBuildingStep`: Include conversationId in metadata (+1 line)
- `IntentHandlingStep`: Add confirmation detection logic (~80 lines)

**Module Changes:**
- `ConversationEnrichmentStep`: ~120 lines
- `ConfirmationResolutionStep`: ~150 lines
- `ConversationRecordingStep`: ~100 lines
- `ChatSessionService.updateSessionMetadata()`: ~20 lines
- Support infrastructure (domain, storage, strategies): ~800 lines

**Total Lines of Code:**
- Core changes: ~93 lines
- Module changes: ~1,190 lines
- **Total:** ~1,283 lines

**Breaking Changes:** ZERO ✅

---

**Document Version:** 5.1 - Pipeline Architecture + Action Confirmation
**Status:** ✅ Ready for Implementation
**Compliance:** 100% AI Fabric Framework Standards
**Architecture:** Pipeline-Based (Current Codebase)

**Implement exactly as specified in this document.** 🎯

