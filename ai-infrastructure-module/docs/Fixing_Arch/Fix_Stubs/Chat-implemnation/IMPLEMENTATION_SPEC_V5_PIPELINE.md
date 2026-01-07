# AI Chat Session Module - Complete Implementation Specification
## Production-Ready, Framework-Compliant Design (Pipeline Architecture)

**Version:** 5.0 - Pipeline Architecture Update  
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
3. ✅ **Composable Steps:** `ConversationEnrichmentStep` (Order 25) and `ConversationRecordingStep` (Order 95)
4. ✅ **Auto-Discovery:** Steps automatically included via Spring dependency injection
5. ✅ **Better Separation:** Each step has single responsibility

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
10. [Security](#10-security)
11. [Configuration](#11-configuration)
12. [Testing](#12-testing)
13. [User Guide](#13-user-guide)
14. [Implementation Checklist](#14-implementation-checklist)

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
| `ConversationRecordingStep` | New class | ~100 | New |
| `MetadataBuildingStep` | Update | +1 | Minimal |
| `RAGOrchestrator` | **ZERO changes** | 0 | ✅ None |

**Total Core Changes:** ~4 lines (only OrchestrationContext)  
**Total Module Changes:** ~220 lines (2 new PipelineSteps)  
**Breaking Changes:** ZERO  
**Architecture Alignment:** ✅ 100% Pipeline-based

---

## 10. Security

### 10.1 Access Control Enforcement

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

### 10.2 Pipeline Step Security

**ConversationEnrichmentStep:**
- ✅ Loads history only after access control (Order 20)
- ✅ Verifies ownership before loading
- ✅ Graceful degradation on access denied (continues without history)

**ConversationRecordingStep:**
- ✅ Records only after all security checks passed
- ✅ Verifies ownership before recording
- ✅ Non-blocking (errors don't fail request)

---

## 11. Configuration

### 11.1 Module Configuration

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

### 11.2 Storage Configuration

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

## 12. Testing

### 12.1 Pipeline Step Tests

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

### 12.2 Integration Tests

**ChatSessionPipelineIntegrationTest.java:**
```java
✅ shouldEnrichQueryAndRecordTurnInFullPipeline
✅ shouldHandleMultipleTurnsInConversation
✅ shouldRespectAccessControlInPipeline
✅ shouldWorkWithSlidingWindowStrategy
✅ shouldWorkWithSummaryStrategy
```

---

## 13. User Guide

### 13.1 Quick Start

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

## 14. Implementation Checklist

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
- [ ] Create ConversationEnrichmentStep (Order 25)
- [ ] Create ConversationRecordingStep (Order 95)
- [ ] Update MetadataBuildingStep (include conversationId)
- [ ] Test pipeline integration

### Phase 7: Testing
- [ ] Unit tests for PipelineSteps (15+ tests)
- [ ] Unit tests for strategies (18+ tests)
- [ ] Unit tests for storage (8+ tests)
- [ ] Integration tests (10+ tests)
- [ ] RealAPI tests (5+ tests)

---

## Summary

### Key Changes from v4.0

| Aspect | v4.0 (Old) | v5.0 (New) |
|--------|------------|-------------|
| **Integration** | Modify RAGOrchestrator | PipelineSteps |
| **Core Changes** | ~40 lines | ~4 lines |
| **Architecture** | Monolithic | Pipeline-based |
| **Testability** | Hard (monolithic) | Easy (isolated steps) |
| **Extensibility** | Limited | High (composable) |

### Benefits of Pipeline Approach

✅ **Zero Core Changes:** `RAGOrchestrator` untouched  
✅ **Better Architecture:** Separation of concerns  
✅ **More Testable:** Isolated step testing  
✅ **More Extensible:** Users can add custom steps  
✅ **Auto-Discovery:** Spring automatically includes steps  
✅ **Framework Aligned:** Follows current codebase patterns  

---

**Document Version:** 5.0 - Pipeline Architecture  
**Status:** ✅ Ready for Implementation  
**Compliance:** 100% AI Fabric Framework Standards  
**Architecture:** Pipeline-Based (Current Codebase)

**Implement exactly as specified in this document.** 🎯

