# AI Chat Session Module - Complete Implementation Specification
## Production-Ready, Framework-Compliant Design (Pipeline Architecture)

**Version:** 5.2 - Pipeline Architecture + Defensive Confirmation Workflow
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
IntentExtractionStep (50): LLM detects ACTION intent
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
        "pendingActionTimestamp": "2026-01-07T10:00:00",
        "pendingActionDescription": "cancel your subscription"
      }
    - Returns CONFIRMATION_REQUIRED result
    ↓
User sees: "You are about to cancel your subscription. This cannot be undone.
            Reply 'yes' to confirm or 'no' to cancel."


Turn 2: User confirms
─────────────────────
User: "yes"
    ↓
ConversationEnrichmentStep (25): Enriches query with pending action context
    ↓ Enriched query: "Context: User has a pending action to 'cancel subscription'.
                      Current message: 'yes'"
    ↓
IntentExtractionStep (50): LLM analyzes enriched query
    ↓ Detects: CONFIRMATION_POSITIVE intent (new intent type)
    ↓
ConfirmationResolutionStep (55):
    - Checks conversation metadata
    - Finds pending action: "cancel_subscription"
    - Detects CONFIRMATION_POSITIVE intent (from LLM)
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

### 10.4 New Intent Types for Confirmation

**File:** `ai-infrastructure-core/.../dto/IntentType.java`

**Add Enum Values:**
```java
public enum IntentType {
    // ... existing values ...
    ACTION,
    INFORMATION,
    OUT_OF_SCOPE,
    COMPOUND,

    /**
     * User confirmed a pending action (e.g., "yes", "proceed", "do it").
     *
     * <p>Detected by LLM when user responds affirmatively to a confirmation request.</p>
     */
    CONFIRMATION_POSITIVE,  // ← ADD THIS

    /**
     * User cancelled a pending action (e.g., "no", "cancel", "abort").
     *
     * <p>Detected by LLM when user responds negatively to a confirmation request.</p>
     */
    CONFIRMATION_NEGATIVE,  // ← ADD THIS

    // ... other values ...
}
```

---

### 10.5 New OrchestrationResultType

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

### 10.6 Enhanced ConversationEnrichmentStep

**CRITICAL UPDATE:** When there's a pending action, ConversationEnrichmentStep must enrich the query with **confirmation context** for the LLM.

**File:** `ai-infrastructure-chat-session/.../pipeline/ConversationEnrichmentStep.java`

**Enhanced Logic:**

```java
@Override
public PipelineContext process(PipelineContext context) {
    // ... existing code ...

    // Load conversation session
    ChatSession session = chatSessionService.get().getSession(conversationId, ownerId);
    Map<String, Object> metadata = session.getSessionMetadata();

    // Check for pending action (PRIORITY: Check this BEFORE loading history)
    if (metadata.containsKey(METADATA_KEY_PENDING_ACTION)) {
        String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);
        String pendingDescription = (String) metadata.get(METADATA_KEY_PENDING_ACTION_DESCRIPTION);

        // Enrich query with confirmation context for LLM
        String enrichedQuery = String.format(
            "CONFIRMATION CONTEXT: The user has a pending action to '%s'. " +
            "Their current message is: '%s'. " +
            "Determine if they are confirming (yes/proceed) or cancelling (no/abort) this action.",
            pendingDescription,
            originalQuery
        );

        log.debug("Enriched query with pending action context: action={}", pendingAction);

        return context.toBuilder()
            .processedQuery(enrichedQuery)
            .build();
    }

    // No pending action - load normal conversation history
    String conversationHistory = chatSessionService.get()
        .getConversationContext(conversationId, ownerId);

    if (conversationHistory != null && !conversationHistory.isBlank()) {
        String enrichedQuery = String.format(
            "Conversation History:\n%s\n\nCurrent Query: %s",
            conversationHistory,
            originalQuery
        );

        return context.toBuilder()
            .processedQuery(enrichedQuery)
            .build();
    }

    return context;
}
```

**Key Changes:**
1. **Check for pending action FIRST** - Before loading conversation history
2. **Enrich with confirmation context** - Tell LLM about pending action
3. **LLM detects intent** - IntentExtractionStep will detect CONFIRMATION_POSITIVE/NEGATIVE
4. **No hardcoded keywords** - LLM analyzes user intent naturally

---

### 10.7 Intent Resolver Pattern (Extensible Architecture) 🆕

**Problem:** Hardcoded conditionals for every edge case violates Open/Closed Principle and makes code fragile.

**Solution:** Use **Strategy Pattern** with pluggable IntentResolvers.

---

#### 10.7.1 IntentResolver SPI

**File:** `ai-infrastructure-chat-session/.../spi/IntentResolver.java`

```java
package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;

import java.util.Map;

/**
 * SPI for resolving specific intent scenarios in conversations.
 *
 * <p><strong>Extensibility:</strong> Users can implement custom resolvers for
 * new edge cases without modifying existing code.</p>
 *
 * <p><strong>Example Use Cases:</strong></p>
 * <ul>
 *   <li>Compound confirmations ("yes and show me laptops")</li>
 *   <li>Nested confirmations (confirm action A, request action B requiring confirmation)</li>
 *   <li>Timeout handling (expired confirmations)</li>
 *   <li>Custom business logic (domain-specific resolution rules)</li>
 * </ul>
 *
 * <p><strong>Registration:</strong> Simply annotate with {@code @Component} for auto-discovery.</p>
 *
 * @see ConfirmationResolutionStep
 * @since 5.1
 */
public interface IntentResolver {

    /**
     * Check if this resolver can handle the current context.
     *
     * <p>Called in priority order. First resolver returning {@code true} will be used.</p>
     *
     * @param intentResponse the detected intents from IntentExtractionStep
     * @param sessionMetadata the conversation session metadata (may contain pending actions)
     * @param context the current pipeline context
     * @return true if this resolver should handle this scenario
     */
    boolean canResolve(MultiIntentResponse intentResponse,
                       Map<String, Object> sessionMetadata,
                       PipelineContext context);

    /**
     * Resolve the intent and return modified context.
     *
     * <p>This method should:</p>
     * <ul>
     *   <li>Analyze the intent response and session metadata</li>
     *   <li>Perform necessary transformations (e.g., replace confirmation with action)</li>
     *   <li>Update session metadata if needed (e.g., clear pending actions)</li>
     *   <li>Return modified PipelineContext for next step</li>
     * </ul>
     *
     * @param intentResponse the detected intents
     * @param sessionMetadata the conversation session metadata
     * @param context the current pipeline context
     * @return modified PipelineContext
     */
    PipelineContext resolve(MultiIntentResponse intentResponse,
                           Map<String, Object> sessionMetadata,
                           PipelineContext context);

    /**
     * Priority for resolver ordering (lower = higher priority).
     *
     * <p>Priorities:</p>
     * <ul>
     *   <li>1-10: Critical resolvers (timeouts, security)</li>
     *   <li>11-50: Specific resolvers (compound, nested)</li>
     *   <li>51-100: General resolvers (single confirmation)</li>
     *   <li>101+: Fallback resolvers</li>
     * </ul>
     *
     * @return priority value (lower executes first)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Resolver name for debugging/logging.
     *
     * @return human-readable resolver name
     */
    String getResolverName();
}
```

---

#### 10.7.2 ConfirmationResolutionStep (Resolver Coordinator)

**File:** `ai-infrastructure-chat-session/.../pipeline/ConfirmationResolutionStep.java`

**Order:** 55 (between IntentExtractionStep (50) and IntentHandlingStep (60))

**Why this order?**
- ✅ After intent extraction (can see what user intent was detected)
- ✅ Before intent handling (can replace intent before execution)
- ✅ After security/access control (only process confirmed actions from authorized users)

**New Role:** Acts as **coordinator** that delegates to IntentResolvers instead of hardcoded conditionals.

```java
package com.ai.infrastructure.chat.pipeline;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.IntentResolver;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline step that coordinates intent resolution using pluggable resolvers.
 *
 * <p><strong>Execution Order:</strong> 55 (after IntentExtractionStep, before IntentHandlingStep)</p>
 *
 * <p><strong>Architecture:</strong></p>
 * <ul>
 *   <li>Acts as coordinator - delegates to {@link IntentResolver} implementations</li>
 *   <li>NO hardcoded conditionals - all logic in resolvers</li>
 *   <li>Resolvers auto-discovered via Spring dependency injection</li>
 *   <li>Executes first resolver that matches (priority-based ordering)</li>
 * </ul>
 *
 * <p><strong>Extensibility:</strong></p>
 * <ul>
 *   <li>Add new edge case? Just create new {@code @Component} resolver</li>
 *   <li>No modification to this step needed</li>
 *   <li>Open/Closed Principle: open for extension, closed for modification</li>
 * </ul>
 *
 * <p><strong>Built-in Resolvers:</strong></p>
 * <ul>
 *   <li>{@code ExpiredConfirmationResolver} (Priority 5) - Handles timeouts</li>
 *   <li>{@code CompoundConfirmationResolver} (Priority 10) - Handles "yes and show laptops"</li>
 *   <li>{@code SingleConfirmationPositiveResolver} (Priority 50) - Handles simple "yes"</li>
 *   <li>{@code SingleConfirmationNegativeResolver} (Priority 51) - Handles simple "no"</li>
 * </ul>
 *
 * @see IntentResolver
 * @see IntentHandlingStep
 * @see ConversationEnrichmentStep
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

    // =========================================================================
    // Dependencies (Auto-injected by Spring)
    // =========================================================================

    private final List<IntentResolver> resolvers;  // All @Component resolvers
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

        // Get intent from context (set by IntentExtractionStep)
        MultiIntentResponse intentResponse = context.getIntentResponse();
        if (intentResponse == null || intentResponse.getIntents().isEmpty()) {
            return context;  // No intent detected
        }

        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();

        try {
            // Load conversation session metadata
            ChatSession session = chatSessionService.get().getSession(conversationId, ownerId);
            Map<String, Object> metadata = session.getSessionMetadata();

            // Sort resolvers by priority (lower = higher priority)
            List<IntentResolver> sortedResolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(IntentResolver::getPriority))
                .collect(Collectors.toList());

            log.debug("Checking {} resolvers for conversation {}",
                sortedResolvers.size(), conversationId);

            // Find first resolver that can handle this scenario
            for (IntentResolver resolver : sortedResolvers) {
                if (resolver.canResolve(intentResponse, metadata, context)) {
                    log.info("Resolver '{}' (priority {}) handling intent resolution for conversation {}",
                        resolver.getResolverName(), resolver.getPriority(), conversationId);

                    return resolver.resolve(intentResponse, metadata, context);
                }
            }

            // No resolver matched - pass through unchanged
            log.debug("No resolver matched for conversation {} - passing through", conversationId);
            return context;

        } catch (Exception ex) {
            log.warn("Failed to process confirmation for conversation {}: {}. Continuing normally.",
                conversationId, ex.getMessage());
            return context;  // Graceful degradation
        }
    }
}
```

---

#### 10.7.3 Built-in Intent Resolvers

**File Structure:**
```
ai-infrastructure-chat-session/
└── src/main/java/com/ai/infrastructure/chat/
    └── resolver/                                    # NEW
        ├── AbstractConfirmationResolver.java        # Base class with utilities
        ├── ExpiredConfirmationResolver.java         # Priority 5
        ├── CompoundConfirmationResolver.java        # Priority 10
        ├── SingleConfirmationPositiveResolver.java  # Priority 50
        └── SingleConfirmationNegativeResolver.java  # Priority 51
```

---

##### 10.7.3.1 AbstractConfirmationResolver (Base Class)

**File:** `ai-infrastructure-chat-session/.../resolver/AbstractConfirmationResolver.java`

```java
package com.ai.infrastructure.chat.resolver;

import com.ai.infrastructure.chat.spi.IntentResolver;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for confirmation resolvers with shared utilities.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractConfirmationResolver implements IntentResolver {

    // Metadata keys
    protected static final String METADATA_KEY_PENDING_ACTION = "pendingAction";
    protected static final String METADATA_KEY_PENDING_PARAMS = "pendingActionParams";
    protected static final String METADATA_KEY_PENDING_TIMESTAMP = "pendingActionTimestamp";
    protected static final String METADATA_KEY_PENDING_DESCRIPTION = "pendingActionDescription";

    // Timeout
    protected static final long CONFIRMATION_TIMEOUT_MINUTES = 5;

    protected final ChatSessionService chatSessionService;

    /**
     * Check if confirmation has expired.
     */
    protected boolean isConfirmationExpired(Object timestampObj) {
        if (timestampObj == null) {
            return true;
        }

        try {
            LocalDateTime timestamp = LocalDateTime.parse(timestampObj.toString());
            long minutesElapsed = ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now());
            return minutesElapsed > CONFIRMATION_TIMEOUT_MINUTES;
        } catch (Exception ex) {
            log.warn("Failed to parse confirmation timestamp: {}", timestampObj);
            return true;
        }
    }

    /**
     * Clear pending action from metadata.
     */
    protected void clearPendingAction(String conversationId, String ownerId,
                                      Map<String, Object> metadata) {
        metadata.remove(METADATA_KEY_PENDING_ACTION);
        metadata.remove(METADATA_KEY_PENDING_PARAMS);
        metadata.remove(METADATA_KEY_PENDING_TIMESTAMP);
        metadata.remove(METADATA_KEY_PENDING_DESCRIPTION);

        try {
            chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);
            log.debug("Cleared pending action for conversation {}", conversationId);
        } catch (Exception ex) {
            log.error("Failed to clear pending action: {}", ex.getMessage());
        }
    }

    /**
     * Create ACTION intent from pending action data.
     */
    protected Intent createConfirmedActionIntent(Map<String, Object> metadata,
                                                 String originalText) {
        String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);
        Map<String, Object> pendingParams = (Map<String, Object>)
            metadata.get(METADATA_KEY_PENDING_PARAMS);

        return Intent.builder()
            .type(IntentType.ACTION)
            .action(pendingAction)
            .actionParams(pendingParams != null ? pendingParams : new HashMap<>())
            .confidence(1.0)
            .originalText(originalText)
            .build();
    }

    /**
     * Check if intent list contains confirmation intent.
     */
    protected boolean hasConfirmationIntent(List<Intent> intents) {
        return intents.stream()
            .anyMatch(i -> i.getType() == IntentType.CONFIRMATION_POSITIVE ||
                          i.getType() == IntentType.CONFIRMATION_NEGATIVE);
    }
}
```

---

##### 10.7.3.2 ExpiredConfirmationResolver (Priority 5)

**File:** `ai-infrastructure-chat-session/.../resolver/ExpiredConfirmationResolver.java`

```java
package com.ai.infrastructure.chat.resolver;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolver for expired pending confirmations.
 *
 * <p><strong>Priority:</strong> 5 (highest - check timeouts first)</p>
 * <p><strong>Handles:</strong> Pending actions that have exceeded timeout (5 minutes)</p>
 *
 * @since 5.1
 */
@Slf4j
@Component
public class ExpiredConfirmationResolver extends AbstractConfirmationResolver {

    public ExpiredConfirmationResolver(ChatSessionService chatSessionService) {
        super(chatSessionService);
    }

    @Override
    public boolean canResolve(MultiIntentResponse intentResponse,
                             Map<String, Object> metadata,
                             PipelineContext context) {
        return metadata.containsKey(METADATA_KEY_PENDING_ACTION) &&
               isConfirmationExpired(metadata.get(METADATA_KEY_PENDING_TIMESTAMP));
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse intentResponse,
                                   Map<String, Object> metadata,
                                   PipelineContext context) {
        String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();

        log.warn("Pending action '{}' expired for conversation {}", pendingAction, conversationId);

        // Clear expired pending action
        clearPendingAction(conversationId, ownerId, metadata);

        // Return context unchanged - expired confirmation ignored
        return context;
    }

    @Override
    public int getPriority() {
        return 5;  // Highest priority - check timeouts first
    }

    @Override
    public String getResolverName() {
        return "ExpiredConfirmationResolver";
    }
}
```

---

##### 10.7.3.3 CompoundConfirmationResolver (Priority 10)

**File:** `ai-infrastructure-chat-session/.../resolver/CompoundConfirmationResolver.java`

```java
package com.ai.infrastructure.chat.resolver;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Resolver for compound intents containing confirmation.
 *
 * <p><strong>Priority:</strong> 10 (high - check compound scenarios before single)</p>
 * <p><strong>Handles:</strong> "yes and show me laptops", "no, show me benefits"</p>
 *
 * <p><strong>Strategy:</strong></p>
 * <ol>
 *   <li>Extract confirmation intent from compound list</li>
 *   <li>If POSITIVE: Create ACTION from pending, add as first intent</li>
 *   <li>If NEGATIVE: Cancel pending, keep remaining intents</li>
 *   <li>Remove confirmation intent from list</li>
 *   <li>Pass modified intent list to next step</li>
 * </ol>
 *
 * @since 5.1
 */
@Slf4j
@Component
public class CompoundConfirmationResolver extends AbstractConfirmationResolver {

    public CompoundConfirmationResolver(ChatSessionService chatSessionService) {
        super(chatSessionService);
    }

    @Override
    public boolean canResolve(MultiIntentResponse intentResponse,
                             Map<String, Object> metadata,
                             PipelineContext context) {
        return intentResponse.isCompound() &&
               intentResponse.getIntents().size() > 1 &&
               hasConfirmationIntent(intentResponse.getIntents()) &&
               !ConfirmationStack.isEmpty(metadata);  // ✅ Stack-based check
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse intentResponse,
                                   Map<String, Object> metadata,
                                   PipelineContext context) {
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();

        // ✅ Get current pending action from stack
        ConfirmationStack.PendingAction pendingAction = getCurrentPendingAction(metadata);

        if (pendingAction == null) {
            return context;
        }

        List<Intent> intents = new ArrayList<>(intentResponse.getIntents());

        // Extract confirmation intent
        Intent confirmationIntent = intents.stream()
            .filter(i -> i.getType() == IntentType.CONFIRMATION_POSITIVE ||
                         i.getType() == IntentType.CONFIRMATION_NEGATIVE)
            .findFirst()
            .orElse(null);

        if (confirmationIntent == null) {
            return context;
        }

        // Remove confirmation intent from list
        intents.remove(confirmationIntent);

        if (confirmationIntent.getType() == IntentType.CONFIRMATION_POSITIVE) {
            log.info("Compound confirmation: confirmed '{}', {} additional intents",
                pendingAction.getAction(), intents.size());

            // Create ACTION from current pending (on top of stack)
            Intent confirmedIntent = Intent.builder()
                .type(IntentType.ACTION)
                .action(pendingAction.getAction())
                .actionParams(pendingAction.getParams())
                .confidence(1.0)
                .originalText("(confirmed: " + pendingAction.getAction() + ")")
                .build();

            // Add confirmed action as FIRST intent
            intents.add(0, confirmedIntent);

            // ✅ Pop from stack (clears current, may restore previous)
            clearCurrentPendingAction(conversationId, ownerId, metadata);

            // Build modified response
            MultiIntentResponse modifiedResponse = MultiIntentResponse.builder()
                .intents(intents)
                .compound(intents.size() > 1)
                .metadata(Map.of(
                    "confirmedAction", pendingAction.getAction(),
                    "originalCompound", true,
                    "stackDepth", ConfirmationStack.size(metadata)  // For debugging
                ))
                .build();

            return context.toBuilder()
                .intentResponse(modifiedResponse)
                .build();

        } else {  // CONFIRMATION_NEGATIVE
            log.info("Compound cancellation: cancelled '{}', {} additional intents",
                pendingAction.getAction(), intents.size());

            // ✅ Pop cancelled action from stack
            clearCurrentPendingAction(conversationId, ownerId, metadata);

            // ✅ Check if there's a previous action restored from stack
            ConfirmationStack.PendingAction restoredAction = getCurrentPendingAction(metadata);

            Map<String, Object> responseMetadata = new HashMap<>(intentResponse.getMetadata());

            if (restoredAction != null) {
                // Previous action restored - inform user
                log.info("Previous action '{}' restored from stack", restoredAction.getAction());

                responseMetadata.put("cancellationMessage",
                    "Action '" + pendingAction.getAction() + "' cancelled. " +
                    "Still want to " + restoredAction.getDescription() + "?");
                responseMetadata.put("restoredFromStack", true);
                responseMetadata.put("restoredAction", restoredAction.getAction());
            } else {
                // Stack empty - normal cancellation
                responseMetadata.put("cancellationMessage",
                    "Action '" + pendingAction.getAction() + "' cancelled.");
            }

            responseMetadata.put("originalCompound", true);
            responseMetadata.put("stackDepth", ConfirmationStack.size(metadata));

            MultiIntentResponse modifiedResponse = MultiIntentResponse.builder()
                .intents(intents)
                .compound(intents.size() > 1)
                .metadata(responseMetadata)
                .build();

            return context.toBuilder()
                .intentResponse(modifiedResponse)
                .build();
        }
    }

    @Override
    public int getPriority() {
        return 10;  // High priority - check compound before single
    }

    @Override
    public String getResolverName() {
        return "CompoundConfirmationResolver";
    }
}
```

---

##### 10.7.3.4 SingleConfirmationPositiveResolver (Priority 50)

**File:** `ai-infrastructure-chat-session/.../resolver/SingleConfirmationPositiveResolver.java`

```java
package com.ai.infrastructure.chat.resolver;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolver for single positive confirmation (e.g., "yes", "confirm", "proceed").
 *
 * <p><strong>Priority:</strong> 50 (general - after specific resolvers)</p>
 * <p><strong>Handles:</strong> Simple "yes" responses to pending actions</p>
 *
 * @since 5.1
 */
@Slf4j
@Component
public class SingleConfirmationPositiveResolver extends AbstractConfirmationResolver {

    public SingleConfirmationPositiveResolver(ChatSessionService chatSessionService) {
        super(chatSessionService);
    }

    @Override
    public boolean canResolve(MultiIntentResponse intentResponse,
                             Map<String, Object> metadata,
                             PipelineContext context) {
        return intentResponse.getIntents().size() == 1 &&
               intentResponse.getIntents().get(0).getType() == IntentType.CONFIRMATION_POSITIVE &&
               metadata.containsKey(METADATA_KEY_PENDING_ACTION);
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse intentResponse,
                                   Map<String, Object> metadata,
                                   PipelineContext context) {
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);

        log.info("Single confirmation: confirmed '{}'", pendingAction);

        // Create ACTION intent from pending
        Intent confirmedIntent = createConfirmedActionIntent(metadata,
            context.getOriginalQuery());

        // Clear pending action
        clearPendingAction(conversationId, ownerId, metadata);

        // Replace intent with confirmed action
        MultiIntentResponse confirmedResponse = MultiIntentResponse.builder()
            .intents(List.of(confirmedIntent))
            .compound(false)
            .build();

        return context.toBuilder()
            .intentResponse(confirmedResponse)
            .build();
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public String getResolverName() {
        return "SingleConfirmationPositiveResolver";
    }
}
```

---

##### 10.7.3.5 SingleConfirmationNegativeResolver (Priority 51)

**File:** `ai-infrastructure-chat-session/.../resolver/SingleConfirmationNegativeResolver.java`

```java
package com.ai.infrastructure.chat.resolver;

import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolver for single negative confirmation (e.g., "no", "cancel", "abort").
 *
 * <p><strong>Priority:</strong> 51 (general - after positive confirmation)</p>
 * <p><strong>Handles:</strong> Simple "no" responses to pending actions</p>
 *
 * @since 5.1
 */
@Slf4j
@Component
public class SingleConfirmationNegativeResolver extends AbstractConfirmationResolver {

    public SingleConfirmationNegativeResolver(ChatSessionService chatSessionService) {
        super(chatSessionService);
    }

    @Override
    public boolean canResolve(MultiIntentResponse intentResponse,
                             Map<String, Object> metadata,
                             PipelineContext context) {
        return intentResponse.getIntents().size() == 1 &&
               intentResponse.getIntents().get(0).getType() == IntentType.CONFIRMATION_NEGATIVE &&
               metadata.containsKey(METADATA_KEY_PENDING_ACTION);
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse intentResponse,
                                   Map<String, Object> metadata,
                                   PipelineContext context) {
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();
        String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);

        log.info("Single cancellation: cancelled '{}'", pendingAction);

        // Clear pending action
        clearPendingAction(conversationId, ownerId, metadata);

        // Create OUT_OF_SCOPE intent with cancellation message
        Intent cancelIntent = Intent.builder()
            .type(IntentType.OUT_OF_SCOPE)
            .confidence(1.0)
            .originalText(context.getOriginalQuery())
            .build();

        MultiIntentResponse cancelResponse = MultiIntentResponse.builder()
            .intents(List.of(cancelIntent))
            .compound(false)
            .metadata(Map.of(
                "cancellationMessage",
                "Action cancelled. Your " + pendingAction + " was not executed."
            ))
            .build();

        return context.toBuilder()
            .intentResponse(cancelResponse)
            .build();
    }

    @Override
    public int getPriority() {
        return 51;
    }

    @Override
    public String getResolverName() {
        return "SingleConfirmationNegativeResolver";
    }
}
```

---

#### 10.7.4 ConfirmationStack Utility (Chaining Support) 🆕

**Problem:** When custom resolver replaces pending action with another action requiring confirmation, original intent is lost.

**Solution:** Use a **stack data structure** to preserve confirmation chain.

**Pattern:** Command Pattern with History Stack (undo/redo)

**File:** `ai-infrastructure-chat-session/.../util/ConfirmationStack.java`

```java
package com.ai.infrastructure.chat.util;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages stack of pending confirmation actions.
 *
 * <p><strong>Purpose:</strong> Enables chained/nested confirmations where resolvers
 * can replace pending actions while preserving original intent.</p>
 *
 * <p><strong>Pattern:</strong> Command Pattern with History Stack</p>
 *
 * <p><strong>Operations:</strong></p>
 * <ul>
 *   <li>{@code push()} - Add new pending action on top</li>
 *   <li>{@code pop()} - Remove top, restore previous</li>
 *   <li>{@code peek()} - Get current pending without removing</li>
 *   <li>{@code clear()} - Clear entire stack</li>
 * </ul>
 *
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li>User confirms action A, resolver offers alternative action B requiring confirmation</li>
 *   <li>User rejects B → Stack pops → A is restored as pending</li>
 *   <li>User confirms B → Stack clears → B executes, A is discarded</li>
 * </ul>
 *
 * @since 5.2
 */
public class ConfirmationStack {

    private static final String METADATA_KEY_STACK = "confirmationStack";

    /**
     * Represents a pending action in the stack.
     */
    @Data
    @Builder
    public static class PendingAction {
        private String action;
        private Map<String, Object> params;
        private String description;
        private String timestamp;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("action", action);
            map.put("params", params != null ? params : new HashMap<>());
            map.put("description", description);
            map.put("timestamp", timestamp);
            return map;
        }

        public static PendingAction fromMap(Map<String, Object> map) {
            return PendingAction.builder()
                .action((String) map.get("action"))
                .params((Map<String, Object>) map.get("params"))
                .description((String) map.get("description"))
                .timestamp((String) map.get("timestamp"))
                .build();
        }
    }

    /**
     * Push new pending action onto stack.
     *
     * <p>Previous action is preserved and can be restored if new action is cancelled.</p>
     *
     * @param metadata conversation session metadata
     * @param action action name
     * @param params action parameters
     * @param description human-readable action description
     */
    public static void push(Map<String, Object> metadata,
                           String action,
                           Map<String, Object> params,
                           String description) {
        List<Map<String, Object>> stack = getStack(metadata);

        PendingAction pendingAction = PendingAction.builder()
            .action(action)
            .params(params)
            .description(description)
            .timestamp(LocalDateTime.now().toString())
            .build();

        stack.add(pendingAction.toMap());
        metadata.put(METADATA_KEY_STACK, stack);
    }

    /**
     * Pop top action from stack and return it.
     *
     * <p>Previous action (if any) becomes current pending action.</p>
     *
     * @param metadata conversation session metadata
     * @return popped action, or null if stack is empty
     */
    public static PendingAction pop(Map<String, Object> metadata) {
        List<Map<String, Object>> stack = getStack(metadata);

        if (stack.isEmpty()) {
            return null;
        }

        Map<String, Object> topMap = stack.remove(stack.size() - 1);
        metadata.put(METADATA_KEY_STACK, stack);

        return PendingAction.fromMap(topMap);
    }

    /**
     * Get current pending action without removing it.
     *
     * @param metadata conversation session metadata
     * @return current action, or null if stack is empty
     */
    public static PendingAction peek(Map<String, Object> metadata) {
        List<Map<String, Object>> stack = getStack(metadata);

        if (stack.isEmpty()) {
            return null;
        }

        Map<String, Object> topMap = stack.get(stack.size() - 1);
        return PendingAction.fromMap(topMap);
    }

    /**
     * Clear entire stack.
     *
     * @param metadata conversation session metadata
     */
    public static void clear(Map<String, Object> metadata) {
        metadata.remove(METADATA_KEY_STACK);
    }

    /**
     * Check if stack has pending actions.
     *
     * @param metadata conversation session metadata
     * @return true if stack is empty
     */
    public static boolean isEmpty(Map<String, Object> metadata) {
        return getStack(metadata).isEmpty();
    }

    /**
     * Get stack depth.
     *
     * @param metadata conversation session metadata
     * @return number of pending actions in stack
     */
    public static int size(Map<String, Object> metadata) {
        return getStack(metadata).size();
    }

    /**
     * Get stack as list.
     */
    private static List<Map<String, Object>> getStack(Map<String, Object> metadata) {
        Object stackObj = metadata.get(METADATA_KEY_STACK);

        if (stackObj == null) {
            return new ArrayList<>();
        }

        if (stackObj instanceof List) {
            return new ArrayList<>((List<Map<String, Object>>) stackObj);
        }

        return new ArrayList<>();
    }
}
```

**Example Stack State:**

```json
{
  "confirmationStack": [
    {
      "action": "cancel_order",
      "params": {"orderId": "12345"},
      "description": "cancel your order",
      "timestamp": "2026-01-08T10:00:00"
    },
    {
      "action": "offer_loyalty_conversion",
      "params": {"points": 5000},
      "description": "convert loyalty points",
      "timestamp": "2026-01-08T10:05:00"
    }
  ]
}
```

**Stack Operations:**
- `peek()` → Returns "offer_loyalty_conversion" (top, current pending)
- `pop()` → Removes "offer_loyalty_conversion", "cancel_order" becomes current
- `size()` → Returns 2
- `clear()` → Empties entire stack

---

#### 10.7.5 Updated AbstractConfirmationResolver (Stack Support)

**Add stack operation methods to base class:**

```java
public abstract class AbstractConfirmationResolver implements IntentResolver {

    // ... existing code ...

    /**
     * Store pending action on confirmation stack.
     *
     * <p>Use this instead of direct metadata manipulation to support chaining.</p>
     *
     * @param conversationId conversation ID
     * @param ownerId owner ID
     * @param metadata session metadata
     * @param action action name
     * @param params action parameters
     * @param description human-readable description
     */
    protected void storePendingAction(String conversationId,
                                      String ownerId,
                                      Map<String, Object> metadata,
                                      String action,
                                      Map<String, Object> params,
                                      String description) {
        // Push onto stack
        ConfirmationStack.push(metadata, action, params, description);

        try {
            chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);
            log.debug("Pushed action '{}' onto confirmation stack (depth: {})",
                action, ConfirmationStack.size(metadata));
        } catch (Exception ex) {
            log.error("Failed to push pending action: {}", ex.getMessage());
        }
    }

    /**
     * Get current pending action from stack.
     *
     * @param metadata session metadata
     * @return current pending action, or null if stack is empty
     */
    protected ConfirmationStack.PendingAction getCurrentPendingAction(Map<String, Object> metadata) {
        return ConfirmationStack.peek(metadata);
    }

    /**
     * Clear current pending action (pop from stack).
     *
     * <p>If stack has more actions, previous one becomes current.</p>
     *
     * @param conversationId conversation ID
     * @param ownerId owner ID
     * @param metadata session metadata
     */
    protected void clearCurrentPendingAction(String conversationId,
                                            String ownerId,
                                            Map<String, Object> metadata) {
        ConfirmationStack.PendingAction popped = ConfirmationStack.pop(metadata);

        try {
            chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);

            if (popped != null) {
                log.debug("Popped action '{}' from stack (remaining depth: {})",
                    popped.getAction(), ConfirmationStack.size(metadata));

                // Log if previous action restored
                ConfirmationStack.PendingAction restored = ConfirmationStack.peek(metadata);
                if (restored != null) {
                    log.debug("Previous action '{}' restored as current pending",
                        restored.getAction());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to pop pending action: {}", ex.getMessage());
        }
    }

    /**
     * Clear entire confirmation stack.
     *
     * <p>Use when all pending actions should be discarded (e.g., timeout, explicit cancel all).</p>
     *
     * @param conversationId conversation ID
     * @param ownerId owner ID
     * @param metadata session metadata
     */
    protected void clearAllPendingActions(String conversationId,
                                         String ownerId,
                                         Map<String, Object> metadata) {
        int depth = ConfirmationStack.size(metadata);
        ConfirmationStack.clear(metadata);

        try {
            chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);
            log.debug("Cleared entire confirmation stack ({} actions) for conversation {}",
                depth, conversationId);
        } catch (Exception ex) {
            log.error("Failed to clear confirmation stack: {}", ex.getMessage());
        }
    }

    // ... keep existing helper methods (isConfirmationExpired, createConfirmedActionIntent, etc.) ...
}
```

---

#### 10.7.6 Defensive Architecture (Preventing Metadata Corruption) 🆕

**Problem Statement:**

Current architecture has several vulnerabilities:

1. **❌ Type-Unsafe Metadata:** `Map<String, Object>` allows any data corruption
2. **❌ Mutable Shared State:** Resolvers get direct mutable access to metadata
3. **❌ No Contracts:** Resolvers can return anything, modify anything
4. **❌ Magic Strings:** Metadata keys are hardcoded strings (typo-prone)
5. **❌ No Validation:** Invalid data can be stored without detection
6. **❌ Exposed Stack:** ConfirmationStack operations callable by anyone

**Solution: Defensive Boundaries + Type Safety**

##### 10.7.6.1 Constants (Eliminates Magic Strings)

**File:** `ai-infrastructure-chat-session/.../constants/ConfirmationConstants.java`

```java
package com.ai.infrastructure.chat.constants;

/**
 * Constants for confirmation workflow to eliminate magic strings/numbers.
 *
 * <p><strong>Benefits:</strong></p>
 * <ul>
 *   <li>Compile-time safety (typos caught by IDE)</li>
 *   <li>Single source of truth for all keys/values</li>
 *   <li>Easy refactoring (rename in one place)</li>
 *   <li>Self-documenting code</li>
 * </ul>
 *
 * @since 5.2
 */
public final class ConfirmationConstants {

    private ConfirmationConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    // =========================================================================
    // Metadata Keys
    // =========================================================================

    /**
     * Metadata key for confirmation stack.
     * Value type: List&lt;Map&lt;String, Object&gt;&gt;
     */
    public static final String METADATA_KEY_CONFIRMATION_STACK = "confirmationStack";

    /**
     * Metadata key for legacy pending action (deprecated, use stack).
     * Value type: Map&lt;String, Object&gt;
     * @deprecated Use METADATA_KEY_CONFIRMATION_STACK instead
     */
    @Deprecated
    public static final String METADATA_KEY_PENDING_ACTION = "pendingAction";

    /**
     * Metadata key for pending action timestamp.
     * Value type: String (ISO-8601 format)
     */
    public static final String METADATA_KEY_TIMESTAMP = "timestamp";

    /**
     * Metadata key for action name.
     * Value type: String
     */
    public static final String METADATA_KEY_ACTION = "action";

    /**
     * Metadata key for action parameters.
     * Value type: Map&lt;String, Object&gt;
     */
    public static final String METADATA_KEY_PARAMS = "params";

    /**
     * Metadata key for action description (human-readable).
     * Value type: String
     */
    public static final String METADATA_KEY_DESCRIPTION = "description";

    // =========================================================================
    // Resolver Priorities
    // =========================================================================

    /**
     * Priority for critical resolvers (timeouts, security).
     * Lower priority = executes first.
     */
    public static final int PRIORITY_CRITICAL = 5;

    /**
     * Priority for high-importance resolvers (compound confirmations).
     */
    public static final int PRIORITY_HIGH = 10;

    /**
     * Priority for normal resolvers (single positive confirmation).
     */
    public static final int PRIORITY_NORMAL = 50;

    /**
     * Priority for low-priority resolvers (single negative confirmation).
     */
    public static final int PRIORITY_LOW = 51;

    /**
     * Priority for fallback resolvers (catch-all handlers).
     */
    public static final int PRIORITY_FALLBACK = 100;

    // =========================================================================
    // Configuration Values
    // =========================================================================

    /**
     * Timeout in minutes for pending confirmations.
     * After this time, pending actions are considered expired.
     */
    public static final long TIMEOUT_MINUTES = 5;

    /**
     * Maximum depth of confirmation stack.
     * Prevents stack overflow from malicious/buggy resolvers.
     */
    public static final int MAX_STACK_DEPTH = 10;

    /**
     * Default empty string for missing descriptions.
     */
    public static final String DEFAULT_DESCRIPTION = "pending action";

    // =========================================================================
    // Response Metadata Keys
    // =========================================================================

    /**
     * Response metadata key for cancellation message.
     */
    public static final String RESPONSE_KEY_CANCELLATION_MESSAGE = "cancellationMessage";

    /**
     * Response metadata key indicating action restored from stack.
     */
    public static final String RESPONSE_KEY_RESTORED_FROM_STACK = "restoredFromStack";

    /**
     * Response metadata key for restored action name.
     */
    public static final String RESPONSE_KEY_RESTORED_ACTION = "restoredAction";

    /**
     * Response metadata key for current stack depth.
     */
    public static final String RESPONSE_KEY_STACK_DEPTH = "stackDepth";
}
```

---

##### 10.7.6.2 Immutable Resolver Context (Read-Only Metadata)

**File:** `ai-infrastructure-chat-session/.../spi/ResolverContext.java`

```java
package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.chat.util.ConfirmationStack;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable context passed to resolvers (read-only view).
 *
 * <p><strong>Design Goals:</strong></p>
 * <ul>
 *   <li>Prevent resolvers from corrupting metadata</li>
 *   <li>Provide type-safe accessors (no raw Map operations)</li>
 *   <li>Enforce immutability (no setters)</li>
 *   <li>Hide internal metadata structure</li>
 * </ul>
 *
 * <p><strong>Usage:</strong> Resolvers receive this instead of mutable metadata.</p>
 *
 * @since 5.2
 */
@Value
@Builder(toBuilder = true)
public class ResolverContext {

    /**
     * Detected intents from IntentExtractionStep.
     */
    MultiIntentResponse intentResponse;

    /**
     * Current pipeline context.
     */
    PipelineContext pipelineContext;

    /**
     * Conversation ID (null if no conversation).
     */
    String conversationId;

    /**
     * Owner ID.
     */
    String ownerId;

    /**
     * Immutable snapshot of session metadata.
     */
    Map<String, Object> metadataSnapshot;

    // =========================================================================
    // Type-Safe Accessors (No Raw Map Operations)
    // =========================================================================

    /**
     * Get current pending action from stack.
     *
     * @return pending action, or empty if no pending actions
     */
    public Optional<ConfirmationStack.PendingAction> getCurrentPendingAction() {
        return Optional.ofNullable(ConfirmationStack.peek(metadataSnapshot));
    }

    /**
     * Check if confirmation stack has pending actions.
     *
     * @return true if stack not empty
     */
    public boolean hasPendingActions() {
        return !ConfirmationStack.isEmpty(metadataSnapshot);
    }

    /**
     * Get confirmation stack depth.
     *
     * @return number of pending actions
     */
    public int getStackDepth() {
        return ConfirmationStack.size(metadataSnapshot);
    }

    /**
     * Get immutable metadata snapshot.
     *
     * <p><strong>WARNING:</strong> Direct metadata access discouraged.
     * Use type-safe methods instead.</p>
     *
     * @return unmodifiable map
     */
    public Map<String, Object> getMetadataSnapshot() {
        return Collections.unmodifiableMap(metadataSnapshot);
    }

    /**
     * Check if in conversational context.
     *
     * @return true if conversation ID present
     */
    public boolean hasConversation() {
        return conversationId != null && !conversationId.isBlank();
    }
}
```

---

##### 10.7.6.3 Resolution Result (What Changed)

**File:** `ai-infrastructure-chat-session/.../spi/ResolutionResult.java`

```java
package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.chat.util.ConfirmationStack;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable result returned by resolvers (declares what should change).
 *
 * <p><strong>Design Goals:</strong></p>
 * <ul>
 *   <li>Resolvers declare changes, don't mutate directly</li>
 *   <li>Coordinator validates and applies changes</li>
 *   <li>Type-safe operations (no raw map manipulation)</li>
 *   <li>Explicit about what changed (clear intent)</li>
 * </ul>
 *
 * <p><strong>Architecture:</strong></p>
 * <pre>
 * Resolver.resolve(context) → ResolutionResult
 *     ↓
 * Coordinator validates result
 *     ↓
 * Coordinator applies changes safely
 * </pre>
 *
 * @since 5.2
 */
@Value
@Builder(toBuilder = true)
public class ResolutionResult {

    /**
     * Resolution type (what happened).
     */
    ResolutionType type;

    /**
     * Modified intent response (if intents changed).
     * Null means no modification.
     */
    MultiIntentResponse modifiedIntentResponse;

    /**
     * Modified pipeline context (if context changed).
     * Null means no modification.
     */
    PipelineContext modifiedPipelineContext;

    /**
     * Stack operation to perform (validated by coordinator).
     */
    StackOperation stackOperation;

    /**
     * Human-readable message for logging/debugging.
     */
    String message;

    /**
     * Additional response metadata for user feedback.
     */
    Map<String, Object> responseMetadata;

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * No changes needed (pass-through).
     */
    public static ResolutionResult noChange(String message) {
        return ResolutionResult.builder()
            .type(ResolutionType.NO_CHANGE)
            .message(message)
            .responseMetadata(Collections.emptyMap())
            .build();
    }

    /**
     * Intent modified (e.g., replaced confirmation with action).
     */
    public static ResolutionResult intentModified(
        MultiIntentResponse modifiedResponse,
        String message
    ) {
        return ResolutionResult.builder()
            .type(ResolutionType.INTENT_MODIFIED)
            .modifiedIntentResponse(modifiedResponse)
            .message(message)
            .responseMetadata(Collections.emptyMap())
            .build();
    }

    /**
     * Intent modified + stack operation.
     */
    public static ResolutionResult intentModifiedWithStackOp(
        MultiIntentResponse modifiedResponse,
        StackOperation stackOp,
        String message
    ) {
        return ResolutionResult.builder()
            .type(ResolutionType.INTENT_MODIFIED_WITH_STACK_OP)
            .modifiedIntentResponse(modifiedResponse)
            .stackOperation(stackOp)
            .message(message)
            .responseMetadata(Collections.emptyMap())
            .build();
    }

    /**
     * Stack operation only (no intent change).
     */
    public static ResolutionResult stackOperationOnly(
        StackOperation stackOp,
        String message
    ) {
        return ResolutionResult.builder()
            .type(ResolutionType.STACK_OPERATION_ONLY)
            .stackOperation(stackOp)
            .message(message)
            .responseMetadata(Collections.emptyMap())
            .build();
    }

    /**
     * Pipeline terminated early (resolver handled completely).
     */
    public static ResolutionResult terminated(
        PipelineContext modifiedContext,
        String message
    ) {
        return ResolutionResult.builder()
            .type(ResolutionType.PIPELINE_TERMINATED)
            .modifiedPipelineContext(modifiedContext)
            .message(message)
            .responseMetadata(Collections.emptyMap())
            .build();
    }

    // =========================================================================
    // Enums
    // =========================================================================

    public enum ResolutionType {
        /**
         * No changes needed (pass-through).
         */
        NO_CHANGE,

        /**
         * Intent response modified only.
         */
        INTENT_MODIFIED,

        /**
         * Intent modified + stack operation.
         */
        INTENT_MODIFIED_WITH_STACK_OP,

        /**
         * Stack operation only (no intent change).
         */
        STACK_OPERATION_ONLY,

        /**
         * Pipeline terminated early (resolver fully handled).
         */
        PIPELINE_TERMINATED
    }

    /**
     * Stack operations (validated before execution).
     */
    @Value
    @Builder
    public static class StackOperation {
        StackOperationType type;
        ConfirmationStack.PendingAction actionToPush;  // For PUSH operations

        public static StackOperation push(ConfirmationStack.PendingAction action) {
            return StackOperation.builder()
                .type(StackOperationType.PUSH)
                .actionToPush(action)
                .build();
        }

        public static StackOperation pop() {
            return StackOperation.builder()
                .type(StackOperationType.POP)
                .build();
        }

        public static StackOperation clear() {
            return StackOperation.builder()
                .type(StackOperationType.CLEAR)
                .build();
        }
    }

    public enum StackOperationType {
        PUSH,
        POP,
        CLEAR
    }
}
```

---

##### 10.7.6.4 Updated IntentResolver Interface

**Replace current interface with defensive version:**

```java
package com.ai.infrastructure.chat.spi;

/**
 * SPI for resolving specific intent scenarios in conversations.
 *
 * <p><strong>Defensive Architecture (v5.2):</strong></p>
 * <ul>
 *   <li>Receives immutable {@link ResolverContext} (read-only)</li>
 *   <li>Returns {@link ResolutionResult} (declares changes)</li>
 *   <li>Cannot corrupt metadata directly</li>
 *   <li>Coordinator validates and applies changes</li>
 * </ul>
 *
 * @since 5.1 (v5.2: defensive boundaries added)
 */
public interface IntentResolver {

    /**
     * Check if this resolver can handle the current context.
     *
     * <p><strong>MUST NOT</strong> have side effects (pure function).</p>
     *
     * @param context immutable resolver context
     * @return true if this resolver should handle this scenario
     */
    boolean canResolve(ResolverContext context);

    /**
     * Resolve the intent and return what should change.
     *
     * <p><strong>Contract:</strong></p>
     * <ul>
     *   <li>MUST return non-null result</li>
     *   <li>MUST NOT mutate context (immutable)</li>
     *   <li>MUST NOT perform stack operations (declare them in result)</li>
     *   <li>MUST be idempotent (same input → same output)</li>
     * </ul>
     *
     * @param context immutable resolver context
     * @return resolution result (what should change)
     */
    ResolutionResult resolve(ResolverContext context);

    /**
     * Priority for resolver ordering (lower = higher priority).
     *
     * <p><strong>Use constants from {@link ConfirmationConstants}:</strong></p>
     * <ul>
     *   <li>{@link ConfirmationConstants#PRIORITY_CRITICAL} (5) - Timeouts, security</li>
     *   <li>{@link ConfirmationConstants#PRIORITY_HIGH} (10) - Compound confirmations</li>
     *   <li>{@link ConfirmationConstants#PRIORITY_NORMAL} (50) - Single confirmation</li>
     *   <li>{@link ConfirmationConstants#PRIORITY_LOW} (51) - Negative confirmation</li>
     *   <li>{@link ConfirmationConstants#PRIORITY_FALLBACK} (100) - Catch-all</li>
     * </ul>
     *
     * @return priority value
     */
    default int getPriority() {
        return ConfirmationConstants.PRIORITY_FALLBACK;
    }

    /**
     * Resolver name for debugging/logging.
     *
     * @return human-readable resolver name
     */
    String getResolverName();
}
```

---

##### 10.7.6.5 ConfirmationStackManager (Validated Mutations)

**File:** `ai-infrastructure-chat-session/.../util/ConfirmationStackManager.java`

```java
package com.ai.infrastructure.chat.util;

import com.ai.infrastructure.chat.constants.ConfirmationConstants;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.chat.spi.ResolutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Centralized manager for confirmation stack operations with validation.
 *
 * <p><strong>Design Goals:</strong></p>
 * <ul>
 *   <li>Single point of mutation (no direct ConfirmationStack calls from resolvers)</li>
 *   <li>Validates all operations before applying</li>
 *   <li>Enforces invariants (max depth, data integrity)</li>
 *   <li>Atomic operations (all-or-nothing)</li>
 * </ul>
 *
 * @since 5.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmationStackManager {

    private final ChatSessionService chatSessionService;

    /**
     * Apply stack operation from resolution result (validated).
     *
     * @param stackOp the stack operation to apply
     * @param conversationId conversation ID
     * @param ownerId owner ID
     * @param metadata session metadata (will be modified)
     * @throws IllegalStateException if operation invalid
     */
    public void applyStackOperation(
        ResolutionResult.StackOperation stackOp,
        String conversationId,
        String ownerId,
        Map<String, Object> metadata
    ) {
        if (stackOp == null) {
            return;  // No operation
        }

        switch (stackOp.getType()) {
            case PUSH:
                validateAndPush(stackOp.getActionToPush(), conversationId, ownerId, metadata);
                break;

            case POP:
                validateAndPop(conversationId, ownerId, metadata);
                break;

            case CLEAR:
                validateAndClear(conversationId, ownerId, metadata);
                break;

            default:
                throw new IllegalStateException("Unknown stack operation type: " + stackOp.getType());
        }
    }

    /**
     * Validate and push action onto stack.
     */
    private void validateAndPush(
        ConfirmationStack.PendingAction action,
        String conversationId,
        String ownerId,
        Map<String, Object> metadata
    ) {
        // Validation 1: Action must be non-null
        if (action == null) {
            throw new IllegalStateException("Cannot push null action onto confirmation stack");
        }

        // Validation 2: Action must have required fields
        if (action.getAction() == null || action.getAction().isBlank()) {
            throw new IllegalStateException("Pending action must have non-blank action name");
        }

        // Validation 3: Check stack depth limit
        int currentDepth = ConfirmationStack.size(metadata);
        if (currentDepth >= ConfirmationConstants.MAX_STACK_DEPTH) {
            throw new IllegalStateException(String.format(
                "Confirmation stack depth limit exceeded (%d >= %d). " +
                "Possible malicious/buggy resolver.",
                currentDepth, ConfirmationConstants.MAX_STACK_DEPTH
            ));
        }

        // Apply operation
        ConfirmationStack.push(
            metadata,
            action.getAction(),
            action.getParams(),
            action.getDescription()
        );

        // Persist
        chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);

        log.debug("✅ Validated and pushed action '{}' (depth: {}/{})",
            action.getAction(),
            ConfirmationStack.size(metadata),
            ConfirmationConstants.MAX_STACK_DEPTH);
    }

    /**
     * Validate and pop action from stack.
     */
    private void validateAndPop(
        String conversationId,
        String ownerId,
        Map<String, Object> metadata
    ) {
        // Validation: Stack must not be empty
        if (ConfirmationStack.isEmpty(metadata)) {
            log.warn("Attempted to pop from empty confirmation stack (conversation: {})", conversationId);
            return;  // Graceful degradation
        }

        // Apply operation
        ConfirmationStack.PendingAction popped = ConfirmationStack.pop(metadata);

        // Persist
        chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);

        log.debug("✅ Validated and popped action '{}' (remaining depth: {})",
            popped != null ? popped.getAction() : "null",
            ConfirmationStack.size(metadata));
    }

    /**
     * Validate and clear entire stack.
     */
    private void validateAndClear(
        String conversationId,
        String ownerId,
        Map<String, Object> metadata
    ) {
        int depth = ConfirmationStack.size(metadata);

        if (depth == 0) {
            log.debug("Stack already empty, no clear needed (conversation: {})", conversationId);
            return;
        }

        // Apply operation
        ConfirmationStack.clear(metadata);

        // Persist
        chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);

        log.debug("✅ Validated and cleared stack ({} actions removed)", depth);
    }
}
```

---

##### 10.7.6.6 Updated ConfirmationResolutionStep (Coordinator)

**Replace process() method with defensive version:**

```java
@Override
public PipelineContext process(PipelineContext context) {
    // Skip if no conversation or service not available
    if (!context.getOrchestrationContext().hasConversation() ||
        chatSessionService.isEmpty()) {
        return context;
    }

    try {
        String conversationId = context.getOrchestrationContext().getConversationId();
        String ownerId = context.getIdentifier();

        // Load session metadata
        ChatSession session = chatSessionService.get().getSession(conversationId, ownerId);
        Map<String, Object> metadata = new HashMap<>(session.getSessionMetadata());

        // Extract intent response
        MultiIntentResponse intentResponse = context.getIntentResponse();
        if (intentResponse == null) {
            return context;  // No intents to resolve
        }

        // ✅ Create immutable resolver context (read-only)
        ResolverContext resolverContext = ResolverContext.builder()
            .intentResponse(intentResponse)
            .pipelineContext(context)
            .conversationId(conversationId)
            .ownerId(ownerId)
            .metadataSnapshot(Collections.unmodifiableMap(new HashMap<>(metadata)))
            .build();

        // Sort resolvers by priority
        List<IntentResolver> sortedResolvers = resolvers.stream()
            .sorted(Comparator.comparingInt(IntentResolver::getPriority))
            .collect(Collectors.toList());

        // Find first resolver that can handle
        for (IntentResolver resolver : sortedResolvers) {
            if (resolver.canResolve(resolverContext)) {
                log.debug("Resolver '{}' matched (priority {})",
                    resolver.getResolverName(), resolver.getPriority());

                // ✅ Resolver returns ResolutionResult (doesn't mutate)
                ResolutionResult result = resolver.resolve(resolverContext);

                // ✅ Validate result
                if (result == null) {
                    log.error("Resolver '{}' returned null result (contract violation)",
                        resolver.getResolverName());
                    continue;  // Skip to next resolver
                }

                // ✅ Apply changes through validated manager
                return applyResolutionResult(result, context, conversationId, ownerId, metadata);
            }
        }

        // No resolver matched
        return context;

    } catch (Exception ex) {
        log.error("Error in confirmation resolution: {}", ex.getMessage(), ex);
        return context;  // Graceful degradation
    }
}

/**
 * Apply resolution result (validates and applies changes).
 */
private PipelineContext applyResolutionResult(
    ResolutionResult result,
    PipelineContext originalContext,
    String conversationId,
    String ownerId,
    Map<String, Object> metadata
) {
    log.info("Applying resolution: {} - {}", result.getType(), result.getMessage());

    PipelineContext modifiedContext = originalContext;

    // Apply intent modifications
    if (result.getModifiedIntentResponse() != null) {
        modifiedContext = modifiedContext.toBuilder()
            .intentResponse(result.getModifiedIntentResponse())
            .build();
    }

    // Apply stack operations (validated)
    if (result.getStackOperation() != null) {
        try {
            stackManager.applyStackOperation(
                result.getStackOperation(),
                conversationId,
                ownerId,
                metadata
            );
        } catch (IllegalStateException ex) {
            log.error("Stack operation validation failed: {}", ex.getMessage());
            // Continue with other changes
        }
    }

    // Apply pipeline modifications
    if (result.getModifiedPipelineContext() != null) {
        modifiedContext = result.getModifiedPipelineContext();
    }

    // Add response metadata
    if (result.getResponseMetadata() != null && !result.getResponseMetadata().isEmpty()) {
        Map<String, Object> currentMetadata = new HashMap<>(
            modifiedContext.getOrchestrationContext().getResponseMetadata()
        );
        currentMetadata.putAll(result.getResponseMetadata());

        modifiedContext = modifiedContext.toBuilder()
            .orchestrationContext(
                modifiedContext.getOrchestrationContext().toBuilder()
                    .responseMetadata(currentMetadata)
                    .build()
            )
            .build();
    }

    return modifiedContext;
}
```

---

##### 10.7.6.7 Migration Guide

**For Existing Resolvers:**

```java
// ❌ OLD (v5.1): Mutable metadata, direct mutation
@Override
public boolean canResolve(MultiIntentResponse intentResponse,
                          Map<String, Object> sessionMetadata,
                          PipelineContext context) {
    ConfirmationStack.PendingAction pending = ConfirmationStack.peek(sessionMetadata);
    return pending != null && isExpired(pending);
}

@Override
public PipelineContext resolve(MultiIntentResponse intentResponse,
                               Map<String, Object> sessionMetadata,
                               PipelineContext context) {
    ConfirmationStack.clear(sessionMetadata);  // Direct mutation!
    chatSessionService.updateSessionMetadata(...);  // Direct DB write!

    return context.toBuilder()
        .intentResponse(modifiedResponse)
        .build();
}
```

```java
// ✅ NEW (v5.2): Immutable context, declared changes
@Override
public boolean canResolve(ResolverContext context) {
    return context.getCurrentPendingAction()
        .map(this::isExpired)
        .orElse(false);
}

@Override
public ResolutionResult resolve(ResolverContext context) {
    // Just return what should change - coordinator applies it
    return ResolutionResult.intentModifiedWithStackOp(
        modifiedResponse,
        ResolutionResult.StackOperation.clear(),
        "Expired confirmation cleared"
    );
}
```

**Benefits:**

1. ✅ **Type-Safe:** No more `Map<String, Object>` in business logic
2. ✅ **Testable:** Easy to mock immutable context
3. ✅ **Traceable:** All mutations logged by manager
4. ✅ **Protected:** Resolvers cannot corrupt metadata
5. ✅ **Validated:** All operations checked before applying
6. ✅ **No Magic Strings:** All keys in constants class

---

### 10.8 Enhanced IntentHandlingStep

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
 *
 * <p>Uses ConfirmationStack to support chaining/nesting of confirmations.</p>
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

    // Extract human-readable action description for LLM context
    String actionDescription = handler.getActionMetadata().getDescription();
    if (actionDescription == null || actionDescription.isBlank()) {
        actionDescription = actionName.replace("_", " ");
    }

    try {
        // Load session
        ChatSession session = chatSessionService.getSession(conversationId, ownerId);
        Map<String, Object> metadata = new HashMap<>(session.getSessionMetadata());

        // ✅ Push onto confirmation stack (supports chaining!)
        ConfirmationStack.push(
            metadata,
            actionName,
            params,
            actionDescription
        );

        chatSessionService.updateSessionMetadata(conversationId, ownerId, metadata);

        int stackDepth = ConfirmationStack.size(metadata);
        log.info("Pushed action '{}' onto confirmation stack (depth: {})",
            actionName, stackDepth);

        // Return confirmation request result
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.CONFIRMATION_REQUIRED)
            .success(false)  // Not executed yet
            .message(confirmationMessage + "\n\nReply 'yes' to confirm or 'no' to cancel.")
            .data(Map.of(
                "action", actionName,
                "requiresConfirmation", true,
                "confirmationMessage", confirmationMessage,
                "stackDepth", stackDepth,  // For debugging
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

**Key Addition:**
- `pendingActionDescription` - Human-readable description stored for ConversationEnrichmentStep to use in LLM prompt

---

### 10.9 ChatSessionService Enhancement

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

### 10.10 Example Flows (LLM-Based)

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

**Conversation Flow (LLM-Based Intent Detection):**

```
Turn 1:
───────
User: "I want to cancel my subscription because it's too expensive"

ConversationEnrichment: No pending action → loads conversation history
    ↓
IntentExtraction: LLM detects ACTION intent (cancel_subscription, reason="too expensive")
    ↓
ConfirmationResolution: No pending action found → pass through
    ↓
IntentHandling:
    - Detects requiresConfirmation() == true
    - Stores pending action metadata:
      {
        "pendingAction": "cancel_subscription",
        "pendingActionParams": {"reason": "too expensive"},
        "pendingActionDescription": "cancel your subscription",
        "pendingActionTimestamp": "2026-01-07T10:00:00"
      }
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

ConversationEnrichment: Detects pending action → enriches query:
    ↓ "CONFIRMATION CONTEXT: The user has a pending action to 'cancel your subscription'.
        Their current message is: 'yes'.
        Determine if they are confirming (yes/proceed) or cancelling (no/abort) this action."
    ↓
IntentExtraction: LLM analyzes enriched query
    ↓ Detects: CONFIRMATION_POSITIVE intent  ← LLM decision, not hardcoded!
    ↓
ConfirmationResolution:
    - Finds pending action: "cancel_subscription"
    - Detects intent.getType() == CONFIRMATION_POSITIVE
    - Creates ACTION intent from pending data
    - Clears pending action
    - REPLACES intent in context
    ↓
IntentHandling:
    - Receives ACTION intent
    - requiresConfirmation() bypassed (already confirmed)
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

#### Example 2: User Cancels Confirmation (LLM Detects Negative)

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

ConversationEnrichment: Enriches with pending action context
    ↓
IntentExtraction: LLM analyzes: "no, wait, I changed my mind"
    ↓ Detects: CONFIRMATION_NEGATIVE intent  ← LLM understands nuance!
    ↓
ConfirmationResolution:
    - Finds pending action: "delete_all_data"
    - Detects intent.getType() == CONFIRMATION_NEGATIVE
    - Clears pending action
    - Creates OUT_OF_SCOPE intent with cancellation message
    ↓
Response: "Action cancelled. Your delete_all_data was not executed."
```

#### Example 3: Ambiguous Response (LLM Detects Different Intent)

```
Turn 2 (alternative):
─────────────────────
User: "actually, can you tell me what features I'll lose?"

ConversationEnrichment: Enriches with pending action context
    ↓
IntentExtraction: LLM analyzes the question
    ↓ Detects: INFORMATION intent (not confirmation)  ← LLM recognizes clarification request
    ↓
ConfirmationResolution:
    - Finds pending action
    - Detects intent.getType() == INFORMATION (not confirmation)
    - Keeps pending action (doesn't clear)
    - Passes through unchanged
    ↓
IntentHandling: Processes INFORMATION intent normally
    ↓
Response: "You'll lose: Premium support, Advanced analytics, Custom integrations...
           Reply 'yes' to cancel or 'no' to keep your subscription."

[Pending action still active for next turn]
```

#### Example 4: Timeout Expired

```
Turn 1:
───────
User: "cancel my plan"

Response: "Confirm cancellation? Reply 'yes' or 'no'."

[User waits 6 minutes]

Turn 2:
───────
User: "yes"

ConversationEnrichment: Enriches with pending action context
    ↓
ConfirmationResolution:
    - Finds pending action: "cancel_subscription"
    - Checks timestamp: 6 minutes ago
    - TIMEOUT! (> 5 minutes)
    - Clears expired pending action
    ↓
IntentExtraction: LLM analyzes "yes" (no pending context)
    ↓ Detects: OUT_OF_SCOPE or INFORMATION
    ↓
Response: "I'm not sure what you're confirming. Could you clarify?"
```

---

### 10.11 Configuration

```yaml
ai:
  chat:
    confirmation:
      enabled: true
      timeout-minutes: 5           # Confirmation expiration (default: 5)
      # NO hardcoded keywords - LLM detects confirmation intent!
```

**Note:** Confirmation detection is **fully LLM-based**. No keyword configuration needed - the LLM analyzes user intent naturally.

---

### 10.12 Adding Custom Intent Resolvers 🆕 **EXTENSIBILITY**

**The Power of the Resolver Pattern:** Add new edge cases WITHOUT modifying existing code!

---

#### 10.12.1 Quick Example: Custom Nested Confirmation Resolver

**Scenario:** Handle case where user confirms action A and requests action B (which also requires confirmation):

```
User: "yes and also delete all my data"
  - Confirms pending "cancel_subscription"
  - Requests "delete_all_data" (also requires confirmation)
```

**Solution:** Create a custom resolver!

**File:** `YourApp/.../NestedConfirmationResolver.java`

```java
@Slf4j
@Component  // That's it! Auto-discovered by Spring
public class NestedConfirmationResolver extends AbstractConfirmationResolver {

    private final ActionHandlerRegistry actionRegistry;

    public NestedConfirmationResolver(ChatSessionService chatSessionService,
                                      ActionHandlerRegistry actionRegistry) {
        super(chatSessionService);
        this.actionRegistry = actionRegistry;
    }

    @Override
    public boolean canResolve(MultiIntentResponse intentResponse,
                             Map<String, Object> metadata,
                             PipelineContext context) {
        // Check: compound + confirmation + has pending + contains action requiring confirmation
        return intentResponse.isCompound() &&
               hasConfirmationIntent(intentResponse.getIntents()) &&
               metadata.containsKey(METADATA_KEY_PENDING_ACTION) &&
               hasActionRequiringConfirmation(intentResponse.getIntents());
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse intentResponse,
                                   Map<String, Object> metadata,
                                   PipelineContext context) {
        log.info("Handling nested confirmation scenario");

        // Extract confirmation intent
        Intent confirmationIntent = extractConfirmationIntent(intentResponse.getIntents());
        List<Intent> remainingIntents = removeConfirmationIntent(intentResponse.getIntents());

        if (confirmationIntent.getType() == IntentType.CONFIRMATION_POSITIVE) {
            // 1. Execute confirmed action
            Intent confirmedAction = createConfirmedActionIntent(metadata,
                "(confirmed: " + metadata.get(METADATA_KEY_PENDING_ACTION) + ")");

            // 2. Find new action requiring confirmation
            Intent newActionRequiringConfirmation = findActionRequiringConfirmation(remainingIntents);
            List<Intent> otherIntents = removeAction(remainingIntents, newActionRequiringConfirmation);

            // 3. Build intent list: [confirmed action, new pending action, others]
            List<Intent> modifiedIntents = new ArrayList<>();
            modifiedIntents.add(confirmedAction);  // Execute first confirmed action
            if (newActionRequiringConfirmation != null) {
                modifiedIntents.add(newActionRequiringConfirmation);  // Will trigger new confirmation
            }
            modifiedIntents.addAll(otherIntents);

            // Clear old pending action
            clearPendingAction(context.getOrchestrationContext().getConversationId(),
                context.getIdentifier(), metadata);

            // Return modified context
            return context.toBuilder()
                .intentResponse(MultiIntentResponse.builder()
                    .intents(modifiedIntents)
                    .compound(modifiedIntents.size() > 1)
                    .metadata(Map.of("nestedConfirmation", true))
                    .build())
                .build();
        }

        return context;
    }

    @Override
    public int getPriority() {
        return 8;  // Higher priority than CompoundConfirmationResolver (10)
    }

    @Override
    public String getResolverName() {
        return "NestedConfirmationResolver";
    }

    // Helper methods...
    private boolean hasActionRequiringConfirmation(List<Intent> intents) {
        return intents.stream()
            .filter(i -> i.getType() == IntentType.ACTION)
            .anyMatch(i -> {
                ActionHandler handler = actionRegistry.getHandler(i.getAction());
                return handler != null && handler.requiresConfirmation();
            });
    }
}
```

**That's it!** No modification to framework code. Just add your `@Component` resolver.

---

#### 10.12.2 How It Works

1. **Framework discovers your resolver** via Spring component scanning
2. **Priority determines execution order** (your Priority 8 runs before built-in Priority 10)
3. **First matching resolver wins** - `canResolve()` checked in priority order
4. **Your resolver processes the scenario** - `resolve()` called
5. **Framework continues with modified context** - next steps see your changes

---

#### 10.12.3 Built-in Resolver Flow Examples

##### Example 1: Simple Confirmation (SingleConfirmationPositiveResolver)

```
User: "yes"

ConversationEnrichment: Enriches with pending action context
    ↓
IntentExtraction: Detects CONFIRMATION_POSITIVE
    ↓
ConfirmationResolution: Checks resolvers in priority order
    ↓ ExpiredConfirmationResolver (5): canResolve() → false (not expired)
    ↓ CompoundConfirmationResolver (10): canResolve() → false (not compound)
    ↓ SingleConfirmationPositiveResolver (50): canResolve() → TRUE! ✅
    ↓
SingleConfirmationPositiveResolver.resolve():
    - Creates ACTION intent from pending
    - Clears pending action
    - Returns modified context
    ↓
IntentHandling: Executes confirmed action
    ↓
Response: "Subscription cancelled successfully"
```

##### Example 2: Compound Confirmation (CompoundConfirmationResolver)

```
User: "yes and show me laptops"

ConversationEnrichment: Enriches with pending action context
    ↓
IntentExtraction: Detects COMPOUND [CONFIRMATION_POSITIVE, INFORMATION]
    ↓
ConfirmationResolution: Checks resolvers
    ↓ ExpiredConfirmationResolver (5): canResolve() → false
    ↓ CompoundConfirmationResolver (10): canResolve() → TRUE! ✅
    ↓
CompoundConfirmationResolver.resolve():
    - Extracts CONFIRMATION_POSITIVE from list
    - Creates ACTION from pending
    - Removes confirmation from list
    - Adds confirmed ACTION as first intent
    - Returns: [ACTION(cancel_subscription), INFORMATION(laptops)]
    ↓
IntentHandling: Processes both intents
    ↓
Response: "Subscription cancelled. Here are laptops: Dell XPS..."
```

##### Example 3: Timeout (ExpiredConfirmationResolver)

```
User: "yes and give me programming laptop black colour"
    ↓
ConversationEnrichmentStep (25):
    - Detects pending action: "cancel_subscription"
    - Enriches query with confirmation context
    ↓ "CONFIRMATION CONTEXT: User has pending action 'cancel subscription'.
        Their message: 'yes and give me programming laptop black colour'.
        Determine if confirming/cancelling, and detect any additional requests."
    ↓
IntentExtractionStep (50): LLM analyzes enriched query
    ↓ Detects: COMPOUND intent with:
        - CONFIRMATION_POSITIVE ("yes")
        - INFORMATION ("give me programming laptop black colour")
    ↓
ConfirmationResolutionStep (55):
    - Detects intentResponse.isCompound() == true
    - Finds CONFIRMATION_POSITIVE in intent list
    - Executes pending action ("cancel_subscription")
    - Removes CONFIRMATION_POSITIVE from intent list
    - Keeps remaining intents: [INFORMATION]
    - Stores executed action result in context metadata
    - Updates context with modified intent list
    ↓
IntentHandlingStep (60):
    - Processes remaining INFORMATION intent
    - Retrieves laptop recommendations
    - Combines with pending action result
    ↓
Response: {
  "confirmedAction": {
    "action": "cancel_subscription",
    "success": true,
    "message": "Subscription cancelled"
  },
  "additionalRequests": {
    "laptops": [...]
  },
  "message": "Your subscription has been cancelled. Here are programming laptops in black: ..."
}
```

---

#### 10.12.2 Enhanced ConfirmationResolutionStep Logic

**Add handling for COMPOUND intents in ConfirmationResolutionStep:**

```java
@Override
public PipelineContext process(PipelineContext context) {
    // ... existing code ...

    // Get intent from context
    MultiIntentResponse intentResponse = context.getIntentResponse();
    if (intentResponse == null || intentResponse.getIntents().isEmpty()) {
        return context;
    }

    // Load conversation session and check for pending action
    ChatSession session = chatSessionService.get().getSession(conversationId, ownerId);
    Map<String, Object> metadata = session.getSessionMetadata();

    if (!metadata.containsKey(METADATA_KEY_PENDING_ACTION)) {
        return context;  // No pending action
    }

    String pendingAction = (String) metadata.get(METADATA_KEY_PENDING_ACTION);
    Map<String, Object> pendingParams = (Map<String, Object>) metadata.get(METADATA_KEY_PENDING_PARAMS);

    // Check if timeout expired
    if (isConfirmationExpired(metadata.get(METADATA_KEY_PENDING_TIMESTAMP))) {
        clearPendingAction(conversationId, ownerId, metadata);
        return context;
    }

    // ========== NEW: Handle COMPOUND intents with confirmation ==========
    if (intentResponse.isCompound() && intentResponse.getIntents().size() > 1) {
        return handleCompoundIntentWithConfirmation(
            context, intentResponse, pendingAction, pendingParams,
            conversationId, ownerId, metadata
        );
    }
    // ====================================================================

    // Handle single intent (existing logic)
    Intent detectedIntent = intentResponse.getIntents().get(0);

    if (detectedIntent.getType() == IntentType.CONFIRMATION_POSITIVE) {
        // ... existing confirmation logic ...
    } else if (detectedIntent.getType() == IntentType.CONFIRMATION_NEGATIVE) {
        // ... existing cancellation logic ...
    }

    return context;
}

/**
 * Handle COMPOUND intent where user confirms AND makes additional request.
 *
 * <p>Example: "yes and give me programming laptop black colour"</p>
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>Find CONFIRMATION_POSITIVE or CONFIRMATION_NEGATIVE in intent list</li>
 *   <li>If CONFIRMATION_POSITIVE: Execute pending action, store result</li>
 *   <li>If CONFIRMATION_NEGATIVE: Cancel pending action</li>
 *   <li>Remove confirmation intent from list</li>
 *   <li>Keep remaining intents for normal processing</li>
 *   <li>Pass modified intent list to IntentHandlingStep</li>
 * </ol>
 */
private PipelineContext handleCompoundIntentWithConfirmation(
    PipelineContext context,
    MultiIntentResponse intentResponse,
    String pendingAction,
    Map<String, Object> pendingParams,
    String conversationId,
    String ownerId,
    Map<String, Object> sessionMetadata
) {
    List<Intent> intents = new ArrayList<>(intentResponse.getIntents());

    // Find confirmation intent
    Intent confirmationIntent = intents.stream()
        .filter(i -> i.getType() == IntentType.CONFIRMATION_POSITIVE ||
                     i.getType() == IntentType.CONFIRMATION_NEGATIVE)
        .findFirst()
        .orElse(null);

    if (confirmationIntent == null) {
        // No confirmation intent in compound list - process normally
        return context;
    }

    // Remove confirmation intent from list
    intents.remove(confirmationIntent);

    if (confirmationIntent.getType() == IntentType.CONFIRMATION_POSITIVE) {
        log.info("LLM confirmed pending action '{}' in COMPOUND intent for conversation {}",
            pendingAction, conversationId);

        // Create ACTION intent from pending action
        Intent confirmedIntent = Intent.builder()
            .type(IntentType.ACTION)
            .action(pendingAction)
            .actionParams(pendingParams != null ? pendingParams : new HashMap<>())
            .confidence(1.0)
            .originalText("(confirmed: " + pendingAction + ")")
            .build();

        // Add confirmed action as FIRST intent (execute before others)
        intents.add(0, confirmedIntent);

        // Clear pending action
        clearPendingAction(conversationId, ownerId, sessionMetadata);

        // Update context with modified intent list
        MultiIntentResponse modifiedResponse = MultiIntentResponse.builder()
            .intents(intents)
            .compound(intents.size() > 1)  // Still compound if >1 remaining
            .metadata(Map.of(
                "confirmedAction", pendingAction,
                "originalCompound", true
            ))
            .build();

        return context.toBuilder()
            .intentResponse(modifiedResponse)
            .build();

    } else if (confirmationIntent.getType() == IntentType.CONFIRMATION_NEGATIVE) {
        log.info("LLM cancelled pending action '{}' in COMPOUND intent for conversation {}",
            pendingAction, conversationId);

        // Clear pending action
        clearPendingAction(conversationId, ownerId, sessionMetadata);

        // Add cancellation message to metadata
        Map<String, Object> responseMetadata = new HashMap<>(intentResponse.getMetadata());
        responseMetadata.put("cancellationMessage",
            "Action '" + pendingAction + "' was cancelled.");
        responseMetadata.put("originalCompound", true);

        // Process remaining intents normally
        MultiIntentResponse modifiedResponse = MultiIntentResponse.builder()
            .intents(intents)
            .compound(intents.size() > 1)
            .metadata(responseMetadata)
            .build();

        return context.toBuilder()
            .intentResponse(modifiedResponse)
            .build();
    }

    return context;
}
```

---

#### 10.12.3 Enhanced IntentHandlingStep for Compound Results

**Update IntentHandlingStep to combine results from confirmed action + additional intents:**

```java
// In IntentHandlingStep.handleCompoundIntent() method:

private OrchestrationResult handleCompoundIntent(
    MultiIntentResponse intentResponse,
    PipelineContext context
) {
    List<Intent> intents = intentResponse.getIntents();
    List<OrchestrationResult> results = new ArrayList<>();

    // Check if this was originally a compound confirmation
    boolean isConfirmationCompound = intentResponse.getMetadata()
        .containsKey("confirmedAction");

    // Process all intents
    for (Intent intent : intents) {
        OrchestrationResult result = processSingleIntent(intent, context);
        results.add(result);
    }

    // Combine results
    if (isConfirmationCompound) {
        // Special formatting for confirmation + additional request
        return combineConfirmationWithAdditionalRequests(results, intentResponse);
    } else {
        // Normal compound handling
        return combineResults(results);
    }
}

/**
 * Combine confirmed action result with additional requests.
 */
private OrchestrationResult combineConfirmationWithAdditionalRequests(
    List<OrchestrationResult> results,
    MultiIntentResponse intentResponse
) {
    OrchestrationResult confirmedActionResult = results.get(0);  // First is confirmed action
    List<OrchestrationResult> additionalResults = results.subList(1, results.size());

    StringBuilder combinedMessage = new StringBuilder();
    combinedMessage.append(confirmedActionResult.getMessage());

    if (!additionalResults.isEmpty()) {
        combinedMessage.append("\n\n");
        combinedMessage.append("Additionally:\n");

        for (OrchestrationResult additionalResult : additionalResults) {
            combinedMessage.append("- ").append(additionalResult.getMessage()).append("\n");
        }
    }

    Map<String, Object> combinedData = new LinkedHashMap<>();
    combinedData.put("confirmedAction", Map.of(
        "action", intentResponse.getMetadata().get("confirmedAction"),
        "result", confirmedActionResult.getData()
    ));

    if (!additionalResults.isEmpty()) {
        combinedData.put("additionalResults",
            additionalResults.stream()
                .map(OrchestrationResult::getData)
                .collect(Collectors.toList())
        );
    }

    return OrchestrationResult.builder()
        .type(OrchestrationResultType.COMPOUND_RESULT)
        .success(confirmedActionResult.isSuccess() &&
                 additionalResults.stream().allMatch(OrchestrationResult::isSuccess))
        .message(combinedMessage.toString())
        .data(combinedData)
        .metadata(Map.of(
            "compound", true,
            "confirmationIncluded", true,
            "intentCount", results.size()
        ))
        .build();
}
```

---

#### 10.12.4 Example Flow

**Full Example: Confirmation + New Request**

```
Turn 1:
───────
User: "cancel my subscription"

IntentExtraction: ACTION (cancel_subscription)
    ↓
IntentHandling: Requires confirmation → stores pending action
    ↓
Response: "You are about to cancel your subscription. Reply 'yes' to confirm."


Turn 2:
───────
User: "yes and give me programming laptop black colour"

ConversationEnrichment: Enriches with pending action context
    ↓
IntentExtraction: COMPOUND intent detected
    ↓ Intents:
        1. CONFIRMATION_POSITIVE ("yes")
        2. INFORMATION ("give me programming laptop black colour")
    ↓
ConfirmationResolution:
    - Detects compound intent with CONFIRMATION_POSITIVE
    - Creates ACTION intent from pending "cancel_subscription"
    - Removes CONFIRMATION_POSITIVE
    - Adds confirmed ACTION as first intent
    - Keeps INFORMATION intent
    ↓ Modified intents:
        1. ACTION (cancel_subscription) ← from pending
        2. INFORMATION (laptop query) ← original
    ↓
IntentHandling:
    - Processes ACTION: Executes subscription cancellation
    - Processes INFORMATION: Retrieves laptop recommendations
    - Combines results with special formatting
    ↓
Response: {
  "type": "COMPOUND_RESULT",
  "success": true,
  "message": "Your subscription has been cancelled successfully.

             Additionally:
             - Here are black programming laptops: Dell XPS 15, ThinkPad X1...",
  "data": {
    "confirmedAction": {
      "action": "cancel_subscription",
      "result": {
        "effectiveDate": "2026-01-07",
        "refundEligible": false
      }
    },
    "additionalResults": [
      {
        "laptops": [...]
      }
    ]
  },
  "metadata": {
    "compound": true,
    "confirmationIncluded": true,
    "intentCount": 2
  }
}
```

---

#### 10.12.5 Edge Cases

**Case 1: Confirmation + Multiple Requests**
```
User: "yes and also show my account history and tell me refund policy"

LLM Detects:
  - CONFIRMATION_POSITIVE
  - INFORMATION (account history)
  - INFORMATION (refund policy)

Result:
  1. Execute pending action
  2. Process account history request
  3. Process refund policy request
  4. Return combined response
```

**Case 2: Cancellation + New Request**
```
User: "no, actually I want to keep it. Show me subscription benefits instead"

LLM Detects:
  - CONFIRMATION_NEGATIVE
  - INFORMATION (subscription benefits)

Result:
  1. Cancel pending action
  2. Process benefits request
  3. Return: "Action cancelled. Here are your subscription benefits: ..."
```

**Case 3: Ambiguous + New Request**
```
User: "maybe later. What are alternative plans?"

LLM Detects:
  - INFORMATION (alternative plans) ← No confirmation intent!

ConfirmationResolution:
  - No confirmation intent found in compound list
  - Keeps pending action for next turn
  - Passes INFORMATION intent through unchanged

Result:
  - Pending action remains active
  - "Here are alternative plans: ..."
  - User can still confirm/cancel later
```

---

### 10.14 Confirmation Stack Pattern (Complete Flow) 🆕 **CRITICAL**

**This pattern solves nested/chained confirmations where resolvers replace pending actions.**

---

#### 10.14.1 The Problem

**Scenario:** User confirms action A, resolver intercepts and offers alternative action B (which also requires confirmation).

```
User: "cancel my order"
→ Stored: "cancel_order" (requires confirmation)

User: "yes"
→ Resolver intercepts, offers "offer_loyalty_conversion" (also requires confirmation)
→ PROBLEM: Original "cancel_order" is lost!

User: "no" (rejects offer)
→ What now? Original cancellation intent is GONE!
```

---

#### 10.14.2 The Solution: Confirmation Stack

**Use stack data structure to preserve confirmation chain:**

```
confirmationStack: [
  {action: "cancel_order", ...},           ← Bottom (original)
  {action: "offer_loyalty_conversion", ...} ← Top (current)
]
```

**Operations:**
- **Push:** Add new pending action on top (original preserved)
- **Pop:** Remove top, previous action restored
- **Peek:** Get current pending without removing

---

#### 10.14.3 Complete Flow Example

##### Turn 1: Initial Request

```
User: "cancel my order"

IntentHandlingStep:
  - ACTION detected: "cancel_order"
  - requiresConfirmation() → true
  - Pushes onto stack

Stack: [{action: "cancel_order", params: {...}, description: "cancel your order"}]
Stack depth: 1

Response: "You are about to cancel your order. Reply 'yes' to confirm or 'no' to cancel."
```

##### Turn 2: User Confirms, Resolver Intercepts

```
User: "yes"

ConversationEnrichmentStep (25):
  - Peek stack → "cancel_order"
  - Enriches query with confirmation context

IntentExtractionStep (50):
  - LLM detects: CONFIRMATION_POSITIVE

ConfirmationResolutionStep (55):
  - Checks resolvers in priority order...

LoyaltyPointsConfirmationResolver (Priority 8):
  - canResolve() → TRUE! (user has 5000 points)
  - Creates ACTION intent: "offer_loyalty_conversion"
  - Does NOT pop stack (keeps original)
  - Returns modified context

IntentHandlingStep (60):
  - Receives ACTION: "offer_loyalty_conversion"
  - requiresConfirmation() → true
  - Pushes onto stack

Stack: [
  {action: "cancel_order", ...},           ← Original preserved!
  {action: "offer_loyalty_conversion", ...} ← New top
]
Stack depth: 2

Response: "You have 5000 loyalty points worth $50. Would you like to convert them to store credit instead of canceling? Reply 'yes' to proceed or 'no' to cancel offer."
```

##### Turn 3a: User Accepts Offer

```
User: "yes"

ConfirmationResolutionStep (55):
  - SingleConfirmationPositiveResolver (Priority 50) matches
  - Peek stack → "offer_loyalty_conversion"
  - Creates ACTION from top of stack
  - Pops stack

Stack: [
  {action: "cancel_order", ...}  ← Restored as current!
]
Stack depth: 1

IntentHandlingStep (60):
  - Executes "offer_loyalty_conversion"
  - Success!

Response: "Converted 5000 points to $50 store credit!"

Note: "cancel_order" STILL pending at stack depth 1!
      (User could be prompted later to confirm/cancel the original order)
```

##### Turn 3b: User Rejects Offer

```
User: "no"

ConfirmationResolutionStep (55):
  - SingleConfirmationNegativeResolver matches
  - Peek stack → "offer_loyalty_conversion"
  - Pops stack

Stack: [
  {action: "cancel_order", ...}  ← Automatically restored!
]
Stack depth: 1

  - Peek again → "cancel_order" (previous action)
  - Detects restoration from stack
  - Builds response with restoration message

Response: "Offer cancelled. Still want to cancel your order? Reply 'yes' or 'no'."

User still has original intent - NO DATA LOSS! ✅
```

##### Turn 4: User Confirms Original

```
User: "yes"

SingleConfirmationPositiveResolver:
  - Peek stack → "cancel_order"
  - Creates ACTION from pending
  - Pops stack

Stack: []  ← Empty!
Stack depth: 0

IntentHandlingStep:
  - Executes "cancel_order"
  - Order cancelled

Response: "Order #12345 has been cancelled successfully."

Stack empty - all confirmations resolved! ✅
```

---

#### 10.14.4 Stack Behavior Summary

| Operation | Stack Before | Action | Stack After | Result |
|-----------|-------------|--------|-------------|--------|
| **Initial Request** | `[]` | Push "cancel_order" | `[cancel_order]` | User prompted for confirmation |
| **Resolver Intercepts** | `[cancel_order]` | Push "offer_loyalty" | `[cancel_order, offer_loyalty]` | New confirmation requested |
| **Accept Offer** | `[cancel_order, offer_loyalty]` | Pop | `[cancel_order]` | Offer executed, original pending |
| **Reject Offer** | `[cancel_order, offer_loyalty]` | Pop | `[cancel_order]` | Original restored, user re-prompted |
| **Confirm Original** | `[cancel_order]` | Pop | `[]` | Original executed, stack empty |

---

#### 10.14.5 Benefits

✅ **No Data Loss:** Original intent always preserved
✅ **Unlimited Depth:** Can chain multiple confirmations
✅ **Clean Rollback:** Pop automatically restores previous
✅ **User-Friendly:** Seamless "changed my mind" flow
✅ **Debuggable:** Stack depth in metadata
✅ **Atomic:** All operations are transaction-safe

---

#### 10.14.6 Edge Cases Handled

**Case 1: User confirms all the way down**
```
Stack: [A, B, C]
User: "yes" → C executes, stack: [A, B]
User: "yes" → B executes, stack: [A]
User: "yes" → A executes, stack: []
```

**Case 2: User rejects at any level**
```
Stack: [A, B, C]
User: "no" → C cancelled, stack: [A, B], B restored
User: "yes" → B executes, stack: [A]
User: "no" → B cancelled, stack: [A], A restored
```

**Case 3: Timeout clears entire stack**
```
Stack: [A, B, C]
Timeout (> 5 min) → ExpiredConfirmationResolver clears ALL
Stack: []
```

**Case 4: Multiple rejects navigate back**
```
Stack: [A, B, C]
User: "no" → Stack: [A, B], user sees "Still want B?"
User: "no" → Stack: [A], user sees "Still want A?"
User: "no" → Stack: [], all cancelled
```

---

### 10.13 Security Considerations (Updated for Stack)

1. **Ownership Verification:** All stack operations tied to conversation owner
2. **Timeout:** Entire stack cleared after 5 minutes (configurable)
3. **Stack Depth Limit:** Consider max depth (e.g., 10) to prevent stack overflow
4. **Atomic Stack Operations:** Push/pop are transaction-safe
5. **Stack Isolation:** Each conversation has independent stack
4. **Access Control:** Full access control checks on metadata updates
5. **Audit Trail:** All confirmations logged with user ID and timestamp
6. **LLM-Based Detection:** No bypass via hardcoded keywords - LLM analyzes full intent
7. **Context Enrichment:** ConversationEnrichmentStep provides context for accurate LLM detection
8. **Compound Intent Integrity:** Confirmed actions always execute first in compound intents
9. **Atomic Operations:** Pending action cleared atomically when confirmed/cancelled

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
✅ shouldDetectLLMPositiveConfirmation
✅ shouldDetectLLMNegativeConfirmation
✅ shouldHandleAmbiguousResponse (LLM detects different intent)
✅ shouldReplaceIntentOnConfirmation
✅ shouldCreateCancellationIntentOnNegative
✅ shouldClearPendingActionAfterConfirmation
✅ shouldHandleExpiredConfirmation
✅ shouldSkipWhenNoPendingAction
✅ shouldSkipWhenNoConversationId
✅ shouldKeepPendingOnAmbiguousIntent
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
✅ shouldExecuteActionAfterLLMDetectsPositive
✅ shouldCancelActionAfterLLMDetectsNegative
✅ shouldHandleConfirmationTimeout
✅ shouldBypassConfirmationForNormalActions
✅ shouldHandleAmbiguousLLMResponse
✅ shouldEnrichQueryWithPendingActionContext
✅ shouldStoreAndRetrievePendingActionMetadata
✅ shouldRespectOwnershipOnConfirmation
✅ shouldSupportNuancedResponses (e.g., "no, wait, changed my mind")
✅ shouldHandleCompoundConfirmationWithNewRequest (e.g., "yes and show me laptops")
✅ shouldHandleCompoundCancellationWithNewRequest (e.g., "no, show me benefits")
✅ shouldHandleCompoundConfirmationWithMultipleRequests
✅ shouldKeepPendingActionWhenCompoundHasNoConfirmation
✅ shouldExecuteConfirmedActionBeforeAdditionalRequests
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
- [ ] Add CONFIRMATION_POSITIVE and CONFIRMATION_NEGATIVE to IntentType enum
- [ ] Add requiresConfirmation() method to ActionHandler interface
- [ ] Add CONFIRMATION_REQUIRED to OrchestrationResultType enum
- [ ] Add updateSessionMetadata() to ChatSessionService
- [ ] **Implement ConfirmationStack utility class** 🆕
  - [ ] PendingAction inner class with toMap/fromMap
  - [ ] push(), pop(), peek(), clear(), isEmpty(), size() operations
  - [ ] Stack stored in conversation metadata
- [ ] **Implement IntentResolver SPI** 🆕
  - [ ] IntentResolver interface with canResolve(), resolve(), getPriority()
  - [ ] AbstractConfirmationResolver base class with stack operations
  - [ ] ExpiredConfirmationResolver (Priority 5)
  - [ ] CompoundConfirmationResolver (Priority 10) with stack restoration
  - [ ] SingleConfirmationPositiveResolver (Priority 50)
  - [ ] SingleConfirmationNegativeResolver (Priority 51)
- [ ] Update ConfirmationResolutionStep as resolver coordinator
- [ ] Update ConversationEnrichmentStep to enrich with stack top
- [ ] Update IntentHandlingStep to push onto stack (not direct metadata)
- [ ] Implement LLM-based confirmation detection (NO hardcoded keywords!)
- [ ] Add combineConfirmationWithAdditionalRequests() to IntentHandlingStep
- [ ] Test confirmation flow (positive, negative, ambiguous, timeout)
- [ ] Test compound confirmation scenarios (confirmation + new requests)
- [ ] **Test stack chaining scenarios** (nested confirmations, rollback) 🆕

### Phase 7.5: Defensive Architecture (v5.2) 🆕 **NEW**
- [ ] **Create ConfirmationConstants class** (eliminates magic strings/numbers)
  - [ ] METADATA_KEY_* constants for all metadata keys
  - [ ] PRIORITY_* constants for resolver priorities
  - [ ] Configuration constants (TIMEOUT_MINUTES, MAX_STACK_DEPTH)
  - [ ] RESPONSE_KEY_* constants for response metadata
- [ ] **Create ResolverContext (immutable)** - Read-only view for resolvers
  - [ ] getCurrentPendingAction(), hasPendingActions(), getStackDepth()
  - [ ] Type-safe accessors (no raw Map operations)
  - [ ] Unmodifiable metadata snapshot
- [ ] **Create ResolutionResult** - What resolvers return (declares changes)
  - [ ] ResolutionType enum (NO_CHANGE, INTENT_MODIFIED, etc.)
  - [ ] StackOperation with validation
  - [ ] Factory methods (noChange(), intentModified(), etc.)
- [ ] **Update IntentResolver interface** - Use defensive signatures
  - [ ] canResolve(ResolverContext) instead of mutable params
  - [ ] resolve(ResolverContext) returns ResolutionResult
  - [ ] Document contracts (immutability, idempotence)
- [ ] **Create ConfirmationStackManager** - Validated mutations only
  - [ ] applyStackOperation() with validation
  - [ ] validateAndPush() - checks depth limit, required fields
  - [ ] validateAndPop() - checks empty stack
  - [ ] validateAndClear() - atomic operation
- [ ] **Update ConfirmationResolutionStep** - Apply defensive pattern
  - [ ] Create immutable ResolverContext for resolvers
  - [ ] Validate ResolutionResult from resolvers
  - [ ] Apply changes through ConfirmationStackManager
  - [ ] Graceful degradation on validation failures
- [ ] **Update ConfirmationStack utility** - Use constants
  - [ ] Replace hardcoded "confirmationStack" with METADATA_KEY_CONFIRMATION_STACK
  - [ ] Replace hardcoded field names with constants
- [ ] **Migrate all resolvers** to defensive pattern
  - [ ] Update ExpiredConfirmationResolver
  - [ ] Update CompoundConfirmationResolver
  - [ ] Update SingleConfirmationPositiveResolver
  - [ ] Update SingleConfirmationNegativeResolver
- [ ] **Update AbstractConfirmationResolver** - Return ResolutionResult
  - [ ] Remove direct metadata mutation methods
  - [ ] Add helper methods for building ResolutionResult
- [ ] **Test defensive boundaries**
  - [ ] Test stack depth limit enforcement
  - [ ] Test invalid action validation
  - [ ] Test immutability (resolvers cannot corrupt)
  - [ ] Test null result handling
  - [ ] Test validation failures

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

| Aspect | v4.0 (Old) | v5.2 (New) |
|--------|------------|-------------|
| **Integration** | Modify RAGOrchestrator | PipelineSteps |
| **Core Changes** | ~40 lines | ~7 lines |
| **Pipeline Steps** | 2 (Enrich + Record) | 3 (Enrich + Confirm + Record) |
| **Architecture** | Monolithic | Pipeline-based |
| **Testability** | Hard (monolithic) | Easy (isolated steps) |
| **Extensibility** | Limited | High (composable + SPI) |
| **Confirmations** | Not supported | Defensive two-step workflow |
| **Type Safety** | N/A | Immutable contexts + validation |
| **Metadata Access** | N/A | Protected (no direct mutation) |

### Benefits of Pipeline Approach

✅ **Zero Core Changes:** `RAGOrchestrator` untouched
✅ **Better Architecture:** Separation of concerns
✅ **More Testable:** Isolated step testing
✅ **More Extensible:** Users can add custom steps
✅ **Auto-Discovery:** Spring automatically includes steps
✅ **Framework Aligned:** Follows current codebase patterns

### New in v5.2: Defensive Confirmation Workflow with Stack Pattern 🆕

**Core Workflow Features:**
✅ **Conversational Confirmations:** Natural "are you sure?" → "yes" flow
✅ **LLM-Based Detection:** NO hardcoded keywords - respects intelligence
✅ **Confirmation Stack Pattern:** Preserves original intent when resolvers intercept
✅ **Chained/Nested Confirmations:** Support unlimited depth with clean rollback
✅ **Context-Aware:** Enriches query with stack top for LLM analysis
✅ **Nuanced Understanding:** LLM handles "no, wait, I changed my mind" correctly
✅ **Compound Intent Support:** Handles "yes and show me laptops" naturally
✅ **Multi-Request Handling:** Execute confirmed action + process additional requests
✅ **Stack Restoration:** Automatically restores previous intent when user rejects offer

**Extensibility & Architecture:**
✅ **Extensible Resolver Pattern:** Add edge cases via @Component resolvers (no framework changes)
✅ **Open/Closed Principle:** Zero conditionals - each scenario is a separate resolver
✅ **User Extensible:** Framework users can inject custom resolvers for domain-specific logic
✅ **Flexible:** Action handlers opt-in via `requiresConfirmation()`

**Defensive Architecture (v5.2):** 🆕
✅ **Immutable Contexts:** Resolvers get read-only ResolverContext (cannot corrupt)
✅ **Type-Safe:** No raw `Map<String, Object>` in resolver APIs
✅ **Validated Mutations:** ConfirmationStackManager enforces all invariants
✅ **Contract Enforcement:** Resolvers must return non-null ResolutionResult
✅ **No Magic Strings:** All metadata keys in ConfirmationConstants
✅ **Stack Depth Limit:** MAX_STACK_DEPTH prevents malicious/buggy resolvers
✅ **Pure Functions:** canResolve() must be side-effect free
✅ **Idempotent:** Same input → same output (testable, predictable)
✅ **Traceable:** All mutations logged by central manager

**Safety & Reliability:**
✅ **Secure:** Ownership verification + timeout protection + stack isolation
✅ **Backward Compatible:** Actions without confirmation work as before
✅ **Graceful Degradation:** Storage failures fall back to immediate execution
✅ **Fail-Safe:** Invalid operations logged and skipped (system continues)

### Implementation Summary

**Core Changes:**
- `OrchestrationContext`: Add `conversationId` field + `hasConversation()` method (~6 lines)
- `IntentType`: Add `CONFIRMATION_POSITIVE` and `CONFIRMATION_NEGATIVE` enum values (~2 lines)
- `ActionHandler`: Add `requiresConfirmation()` default method (~5 lines)
- `OrchestrationResultType`: Add `CONFIRMATION_REQUIRED` enum value (~1 line)
- `MetadataBuildingStep`: Include conversationId in metadata (+1 line)
- `IntentHandlingStep`: Add confirmation detection logic (~85 lines)

**Module Changes (v5.2):**
- `ConfirmationConstants`: ~140 lines (eliminates magic strings) 🆕
- `ResolverContext` (immutable): ~80 lines (type-safe read-only) 🆕
- `ResolutionResult`: ~180 lines (declares changes) 🆕
- `ConfirmationStackManager`: ~140 lines (validated mutations) 🆕
- `ConfirmationStack` utility class: ~150 lines
- `IntentResolver` SPI interface: ~50 lines (defensive signatures) 🆕
- `AbstractConfirmationResolver` base class: ~180 lines
- `ExpiredConfirmationResolver`: ~50 lines
- `CompoundConfirmationResolver`: ~110 lines (with stack restoration)
- `SingleConfirmationPositiveResolver`: ~45 lines
- `SingleConfirmationNegativeResolver`: ~50 lines
- `ConfirmationResolutionStep` (coordinator): ~110 lines (with validation) 🆕
- `ConversationEnrichmentStep`: ~120 lines
- `ConversationRecordingStep`: ~100 lines
- `ChatSessionService.updateSessionMetadata()`: ~20 lines
- `IntentHandlingStep` updates: ~90 lines (stack push + combine methods)
- Support infrastructure (domain, storage, strategies): ~800 lines

**Total Lines of Code (v5.2):**
- Core changes: ~100 lines
- Defensive architecture: ~540 lines (constants, validation, immutability) 🆕
- Resolver pattern + stack: ~705 lines
- Conversation + session support: ~1,130 lines
- **Total:** ~2,475 lines

**Key Architectural Components:**
1. **Defensive Boundaries (v5.2):** 🆕
   - ResolverContext (immutable) - Read-only view
   - ResolutionResult - Declared changes
   - ConfirmationStackManager - Validated mutations
   - ConfirmationConstants - Zero magic strings
2. **ConfirmationStack** - Command Pattern with History (stack data structure)
3. **IntentResolver SPI** - Strategy Pattern (pluggable resolvers)
4. **4 Built-in Resolvers** - Priority-based execution
5. **AbstractConfirmationResolver** - Template Method Pattern (shared operations)
6. **ConfirmationResolutionStep** - Coordinator (validates & delegates)

**Key Architectural Decisions:**
- ✅ **NO Hardcoded Keywords** - All confirmation detection via LLM
- ✅ **Respects Intelligence** - Framework philosophy maintained
- ✅ **Immutable by Default** - Resolvers cannot corrupt state (v5.2) 🆕
- ✅ **Fail-Safe** - Validation prevents malicious/buggy resolvers (v5.2) 🆕

**Breaking Changes:** ZERO ✅

---

**Document Version:** 5.2 - Pipeline Architecture + Defensive Confirmation Workflow
**Status:** ✅ Ready for Implementation
**Compliance:** 100% AI Fabric Framework Standards
**Architecture:** Pipeline-Based + Defensive Boundaries (v5.2)

**Implement exactly as specified in this document.** 🎯

