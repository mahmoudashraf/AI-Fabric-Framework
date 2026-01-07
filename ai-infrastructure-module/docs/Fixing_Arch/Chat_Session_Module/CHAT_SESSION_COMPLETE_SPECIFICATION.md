# AI Chat Session Module - Complete Specification
## Comprehensive Implementation, Design & Architecture Document

**Version:** 3.0 - Final Consolidated  
**Date:** January 2026  
**Status:** ✅ Implementation Ready  
**Compliance:** Fully aligned with AI Fabric Framework Philosophy

---

## Document Purpose

This is the **single source of truth** for implementing the AI Chat Session module. It consolidates:
- Architecture & design decisions
- Implementation specifications
- Code standards & patterns
- Security requirements
- Testing strategies
- Integration approach

**Audience:** Developers implementing the chat session module  
**Prerequisites:** Read `AI_FABRIC_FRAMEWORK_PHILOSOPHY.md` and `AI_LLM_CODE_GENERATION_GUIDE.md`

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Core Design Decisions](#2-core-design-decisions)
3. [Module Architecture](#3-module-architecture)
4. [Data Models](#4-data-models)
5. [SPI Interfaces](#5-spi-interfaces)
6. [Service Implementation](#6-service-implementation)
7. [Integration with RAGOrchestrator](#7-integration-with-ragorchestrator)
8. [Security Model](#8-security-model)
9. [Configuration](#9-configuration)
10. [Code Standards](#10-code-standards)
11. [Testing Requirements](#11-testing-requirements)
12. [User Implementation Guide](#12-user-implementation-guide)
13. [Complete Code Examples](#13-complete-code-examples)
14. [Deployment & Operations](#14-deployment--operations)

---

## 1. Executive Summary

### 1.1 What This Module Does

The AI Chat Session module provides **conversation memory** for the AI Fabric Framework, enabling multi-turn conversations by:

1. ✅ **Tracking conversation history** (query/response pairs)
2. ✅ **Enriching queries** with relevant conversation context
3. ✅ **Supporting multiple conversations** per user
4. ✅ **Providing secure access control** to conversations
5. ✅ **Allowing user-provided storage** (Redis, Database, etc.)

### 1.2 Key Characteristics

**✅ Greenfield Design:**
- No backward compatibility
- Clean, modern architecture
- Framework provides SPIs, users provide implementations

**✅ Security-First:**
- Required access control policy
- Fail-closed model (deny if unauthorized)
- Conversations linked to owners
- Comprehensive audit logging

**✅ Minimal Core Changes:**
- Add `conversationId` to OrchestrationContext
- Add `Optional<ChatSessionService>` to RAGOrchestrator
- Two helper methods (~30 lines total)
- ZERO changes to existing orchestration logic

**✅ User Extensibility:**
- Users provide storage (SPI)
- Users provide access control (SPI)
- Users can provide custom memory strategies

### 1.3 Module Boundaries

```
ai-infrastructure-chat-session/        # NEW separate module
├─→ Provides: Domain models, service interface, SPIs
├─→ Requires: Users implement SPIs (storage + access control)
└─→ Integrates: Optionally with RAGOrchestrator

ai-infrastructure-core/                # Existing (minimal changes)
├─→ Add: conversationId field to OrchestrationContext
├─→ Add: Optional<ChatSessionService> to RAGOrchestrator
└─→ Unchanged: All existing orchestration logic
```

---

## 2. Core Design Decisions

### 2.1 The Three Identifiers

**Critical Design Decision:** We use THREE separate identifiers with distinct purposes:

| Field | Purpose | Required When | Example | Set By |
|-------|---------|---------------|---------|--------|
| **userId** | User identification | Authenticated requests | `"alice"` | Application/Auth |
| **sessionId** | Anonymous tracking | No userId (anonymous) | `"anon-xyz"` | Application |
| **conversationId** | Conversation tracking | Multi-turn chat | `"conv-001"` | Client |

**Why Three?**

1. **userId** - Security & personalization (who you are)
2. **sessionId** - Anonymous tracking (identify anonymous users)
3. **conversationId** - Conversation memory (which conversation)

**Key Insight:** One user can have multiple conversations, so `userId ≠ conversationId`

### 2.2 Conversation Ownership

```
User "alice" (userId)
├─ Conversation "conv-001" (Project A discussion) ownedBy: "alice"
├─ Conversation "conv-002" (Support ticket) ownedBy: "alice"
└─ Conversation "conv-003" (Research) ownedBy: "alice"

User "bob" (userId)
└─ Conversation "conv-004" (His project) ownedBy: "bob"
```

**Access Control:**
- Alice can access: conv-001, conv-002, conv-003
- Alice CANNOT access: conv-004 (owned by Bob)
- Fail-closed: Attempt to access others' conversations = DENIED

### 2.3 Session-Aware vs Non-Aware Queries

**Both modes supported in same application:**

```java
// Mode 1: Non-session-aware (single query)
OrchestrationContext context = OrchestrationContext.builder()
    .userId("alice")
    // No conversationId
    .build();
orchestrator.orchestrate("find users", context);
// → No history, no recording

// Mode 2: Session-aware (conversation)
OrchestrationContext context = OrchestrationContext.builder()
    .userId("alice")
    .conversationId("conv-001")  // ← Enables chat
    .build();
orchestrator.orchestrate("what about premium ones?", context);
// → Loads history, enriches query, records turn
```

**Decision:** Client chooses per query via `conversationId` presence

### 2.4 Storage: User-Provided via SPI

**Framework provides:** Storage interface (SPI)  
**Users provide:** Storage implementation (Redis, DB, S3, etc.)

**Why?**
- Framework can't know user's infrastructure
- Users might need Redis, PostgreSQL, Cassandra, MongoDB, custom
- No unused storage implementations shipped
- True extensibility

**Philosophy:** Trust users to know their systems

### 2.5 Integration Approach

**Enhance RAGOrchestrator (not replace):**

```
RAGOrchestrator.orchestrate(query, context)
    ↓
IF conversationId provided:
  1. Load conversation history
  2. Enrich query with history
ENDIF
    ↓
Execute existing orchestration logic (unchanged)
    ↓
Get OrchestrationResult
    ↓
IF conversationId provided:
  3. Record turn (query + response)
ENDIF
    ↓
Return result
```

**Changes to core:** ~30 lines (additive only)

---

## 3. Module Architecture

### 3.1 Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-core/              # Existing (minimal changes)
│   └── src/main/java/.../orchestration/
│       ├── OrchestrationContext.java    # Add conversationId field
│       └── RAGOrchestrator.java         # Add Optional<ChatSessionService>
│
└── ai-infrastructure-chat-session/     # NEW MODULE
    ├── pom.xml
    └── src/
        ├── main/java/com/ai/infrastructure/chat/
        │   ├── domain/
        │   │   ├── ChatSession.java
        │   │   ├── ChatTurn.java
        │   │   ├── SessionStatus.java
        │   │   └── MetadataConverter.java
        │   ├── service/
        │   │   ├── ChatSessionService.java (interface)
        │   │   └── ChatSessionServiceImpl.java
        │   ├── spi/
        │   │   ├── ChatSessionStorageProvider.java    # SPI - Users implement
        │   │   └── ChatSessionAccessControlPolicy.java # SPI - Users implement
        │   ├── strategy/
        │   │   ├── MemoryStrategy.java (interface)
        │   │   ├── SlidingWindowMemoryStrategy.java
        │   │   └── SummaryMemoryStrategy.java
        │   ├── config/
        │   │   ├── ChatSessionProperties.java
        │   │   └── ChatSessionAutoConfiguration.java
        │   └── exception/
        │       ├── SessionNotFoundException.java
        │       ├── SessionExpiredException.java
        │       └── AccessDeniedException.java
        └── test/java/...
```

### 3.2 Dependency Diagram

```
User Application
    ↓ provides
ChatSessionStorageProvider (SPI)
ChatSessionAccessControlPolicy (SPI)
    ↓ used by
ChatSessionService (framework)
    ↓ optionally used by
RAGOrchestrator (core)
    ↓ processes
User Queries
```

### 3.3 Package Names (Framework Standard)

```java
// ✅ CORRECT:
package com.ai.infrastructure.chat.domain;
package com.ai.infrastructure.chat.service;
package com.ai.infrastructure.chat.spi;

// ❌ WRONG:
package com.thebase.ai.session;  // Not framework standard
```

---

## 4. Data Models

### 4.1 ChatSession (Domain Entity)

```java
package com.ai.infrastructure.chat.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a conversation between a user and the AI system.
 * 
 * <p>A conversation consists of multiple turns (query/response pairs) and is
 * owned by a specific user or anonymous session. Users can have multiple
 * concurrent conversations.</p>
 * 
 * <p><strong>Ownership & Access Control:</strong></p>
 * <ul>
 *   <li>Each conversation has ONE owner (userId or sessionId for anonymous)</li>
 *   <li>Only the owner can access the conversation (enforced by ChatSessionAccessControlPolicy)</li>
 *   <li>Conversations are identified by conversationId (client-generated)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This entity is NOT thread-safe. Use optimistic
 * locking (@Version) for concurrent modifications.</p>
 * 
 * <p><strong>Lifecycle:</strong></p>
 * <ul>
 *   <li>ACTIVE - Accepting new turns</li>
 *   <li>EXPIRED - TTL exceeded, eligible for cleanup</li>
 *   <li>ARCHIVED - Moved to cold storage</li>
 *   <li>INVALIDATED - Manually terminated</li>
 * </ul>
 * 
 * @see ChatTurn for individual query/response pairs
 * @see ChatSessionService for conversation management
 * @see ChatSessionAccessControlPolicy for security enforcement
 */
@Entity
@Table(
    name = "chat_sessions",
    indexes = {
        @Index(name = "idx_owner_id", columnList = "ownerId"),
        @Index(name = "idx_last_interaction", columnList = "lastInteractionAt"),
        @Index(name = "idx_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    
    // Constants
    private static final int MAX_TURNS_DEFAULT = 100;

    /**
     * Conversation identifier (client-generated UUID).
     */
    @Id
    @Column(length = 36)
    private String id;

    /**
     * Owner of this conversation.
     * - For authenticated users: userId (e.g., "alice")
     * - For anonymous users: sessionId (e.g., "anon-xyz")
     * 
     * CRITICAL for access control.
     */
    @NotBlank
    @Column(nullable = false, length = 100, name = "owner_id")
    private String ownerId;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    /**
     * Conversation turns (query/response pairs).
     * Ordered chronologically (oldest first).
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<ChatTurn> turns = new ArrayList<>();

    /**
     * Extensible metadata for application-specific data.
     */
    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Optimistic locking for concurrent updates.
     */
    @Version
    private Long version;

    // Business Methods

    /**
     * Adds a turn to the conversation and updates last interaction time.
     * 
     * @param turn The turn to add
     */
    public void addTurn(ChatTurn turn) {
        if (this.turns == null) {
            this.turns = new ArrayList<>();
        }
        this.turns.add(turn);
        this.lastInteractionAt = LocalDateTime.now();
    }

    /**
     * Gets the N most recent turns for context window.
     * 
     * @param limit Maximum number of recent turns to return
     * @return List of recent turns (newest last)
     */
    public List<ChatTurn> getRecentTurns(int limit) {
        if (turns == null || turns.isEmpty()) {
            return Collections.emptyList();
        }
        int size = turns.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(turns.subList(fromIndex, size));
    }

    /**
     * Gets total number of turns in this conversation.
     */
    public int getTurnCount() {
        return turns != null ? turns.size() : 0;
    }

    /**
     * Checks if conversation has expired based on TTL.
     * 
     * @param ttlMinutes TTL in minutes
     * @return true if expired
     */
    public boolean isExpired(int ttlMinutes) {
        return LocalDateTime.now().isAfter(
            lastInteractionAt.plusMinutes(ttlMinutes)
        );
    }
    
    /**
     * Checks if this conversation is owned by the specified user.
     * CRITICAL for access control.
     * 
     * @param requestingUser userId or sessionId
     * @return true if requesting user owns this conversation
     */
    public boolean isOwnedBy(String requestingUser) {
        return ownerId != null && ownerId.equals(requestingUser);
    }
}
```

### 4.2 SessionStatus Enum

```java
package com.ai.infrastructure.chat.domain;

/**
 * Lifecycle status of a chat session.
 */
public enum SessionStatus {
    /**
     * Session is active and accepting new turns.
     */
    ACTIVE,
    
    /**
     * Session TTL expired, eligible for cleanup.
     */
    EXPIRED,
    
    /**
     * Session archived to cold storage.
     */
    ARCHIVED,
    
    /**
     * Session manually terminated by user.
     */
    INVALIDATED
}
```

### 4.3 ChatTurn (Value Object)

```java
package com.ai.infrastructure.chat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a single user-AI exchange within a conversation.
 * 
 * <p>Immutable by design to ensure conversation integrity.
 * Once recorded, turns should not be modified.</p>
 * 
 * @see ChatSession for the containing conversation
 */
@Entity
@Table(name = "chat_turns", indexes = {
    @Index(name = "idx_session_timestamp", columnList = "session_id,timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User's query/message.
     */
    @Column(nullable = false, columnDefinition = "TEXT", name = "user_query")
    private String userQuery;

    /**
     * AI's response.
     */
    @Column(nullable = false, columnDefinition = "TEXT", name = "ai_response")
    private String aiResponse;

    /**
     * When this exchange occurred.
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Document IDs used in RAG for this turn (optional).
     */
    @ElementCollection
    @CollectionTable(
        name = "turn_document_refs",
        joinColumns = @JoinColumn(name = "turn_id")
    )
    @Column(name = "document_id")
    @Builder.Default
    private List<String> documentIds = new ArrayList<>();

    /**
     * Token count for this turn (optional, for billing/metrics).
     */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /**
     * LLM model used for this turn (optional, for tracking).
     */
    @Column(length = 50, name = "model_used")
    private String modelUsed;

    /**
     * Turn-specific metadata.
     */
    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT", name = "turn_metadata")
    @Builder.Default
    private Map<String, Object> turnMetadata = new HashMap<>();

    /**
     * Formats turn for LLM prompt inclusion.
     * 
     * @return Formatted string: "User: ...\nAssistant: ..."
     */
    public String toPromptFormat() {
        return String.format("User: %s\nAssistant: %s", userQuery, aiResponse);
    }
}
```

### 4.4 MetadataConverter

```java
package com.ai.infrastructure.chat.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts Map<String, Object> to/from JSON for database storage.
 * 
 * <p>Thread-safe via static ObjectMapper instance.</p>
 */
@Slf4j
@Converter
public class MetadataConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMPTY_JSON = "{}";

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return EMPTY_JSON;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting metadata to JSON: {}", e.getMessage());
            return EMPTY_JSON;
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing metadata JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
```

---

## 5. SPI Interfaces

### 5.1 ChatSessionStorageProvider (Users MUST Implement)

```java
package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.chat.domain.ChatSession;
import java.util.List;
import java.util.Optional;

/**
 * SPI for chat session storage.
 * 
 * <p>Framework users MUST implement this interface to provide their storage backend.
 * The application will fail to start if no implementation is provided when chat session
 * module is enabled ({@code ai.chat.enabled=true}).</p>
 * 
 * <p><strong>Thread Safety:</strong> Implementations MUST be thread-safe as they will
 * be accessed concurrently by multiple requests.</p>
 * 
 * <p><strong>Implementation Examples:</strong></p>
 * <ul>
 *   <li><strong>Redis:</strong> For distributed systems, fast access</li>
 *   <li><strong>Database:</strong> For persistence, complex queries</li>
 *   <li><strong>Hybrid:</strong> Hot cache (in-memory/Redis) + cold database</li>
 *   <li><strong>Custom:</strong> S3, Cassandra, MongoDB, etc.</li>
 * </ul>
 * 
 * <p><strong>Example Implementation (Redis):</strong></p>
 * <pre>{@code
 * @Component
 * public class RedisChatStorage implements ChatSessionStorageProvider {
 *     
 *     private final RedisTemplate<String, ChatSession> redis;
 *     
 *     @Override
 *     public ChatSession save(ChatSession session) {
 *         String key = "chat:session:" + session.getId();
 *         redis.opsForValue().set(key, session, Duration.ofHours(1));
 *         return session;
 *     }
 *     
 *     @Override
 *     public Optional<ChatSession> findById(String conversationId) {
 *         String key = "chat:session:" + conversationId;
 *         return Optional.ofNullable(redis.opsForValue().get(key));
 *     }
 *     
 *     // ... other methods
 * }
 * }</pre>
 * 
 * @see ChatSession
 * @see ChatSessionService
 */
public interface ChatSessionStorageProvider {
    
    /**
     * Saves or updates a chat session.
     * 
     * <p>Implementation should be idempotent - calling save multiple times
     * with the same session should produce the same result.</p>
     * 
     * @param session The session to save (never null)
     * @return The saved session
     * @throws StorageException if save operation fails
     */
    ChatSession save(ChatSession session);
    
    /**
     * Retrieves a conversation by ID.
     * 
     * @param conversationId The conversation identifier (never null)
     * @return Optional containing the session if found, empty otherwise
     */
    Optional<ChatSession> findById(String conversationId);
    
    /**
     * Deletes a conversation by ID.
     * 
     * <p>Should be idempotent - deleting non-existent session should not fail.</p>
     * 
     * @param conversationId The conversation identifier (never null)
     */
    void deleteById(String conversationId);
    
    /**
     * Finds all conversations owned by a user.
     * 
     * <p>Used for:</p>
     * <ul>
     *   <li>Listing user's conversations</li>
     *   <li>Bulk operations (delete all user conversations)</li>
     *   <li>Analytics/reporting</li>
     * </ul>
     * 
     * @param ownerId The owner identifier (userId or sessionId)
     * @return List of conversations (may be empty, never null)
     */
    List<ChatSession> findByOwnerId(String ownerId);
    
    /**
     * Finds expired sessions for cleanup/archival.
     * 
     * <p>Used by scheduled cleanup job to remove stale conversations.</p>
     * 
     * @param ttlMinutes TTL in minutes
     * @return List of expired sessions (may be empty, never null)
     */
    List<ChatSession> findExpiredSessions(int ttlMinutes);
}
```

### 5.2 ChatSessionAccessControlPolicy (Users MUST Implement)

```java
package com.ai.infrastructure.chat.spi;

/**
 * SPI for chat session access control.
 * 
 * <p>Framework users MUST implement this interface when chat session module is enabled.
 * The application will fail to start if no implementation is provided.</p>
 * 
 * <p><strong>Security Model:</strong> Fail-closed. If access check returns false,
 * the operation is DENIED and an AccessDeniedException is thrown.</p>
 * 
 * <p><strong>Example Implementation:</strong></p>
 * <pre>{@code
 * @Component
 * public class MyChatSessionAccessPolicy implements ChatSessionAccessControlPolicy {
 *     
 *     private final ChatSessionStorageProvider storage;
 *     private final UserService userService;
 *     
 *     @Override
 *     public boolean canUserAccessConversation(String requestingUser, String conversationId) {
 *         // Load conversation
 *         Optional<ChatSession> session = storage.findById(conversationId);
 *         if (session.isEmpty()) {
 *             return true;  // New conversation, allow creation
 *         }
 *         
 *         // CRITICAL: Verify ownership
 *         if (session.get().getOwnerId().equals(requestingUser)) {
 *             return true;  // Owner can access
 *         }
 *         
 *         // Check if user has admin rights or conversation is shared
 *         return userService.isAdmin(requestingUser) || 
 *                session.get().isShared();
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>REQUIREMENT:</strong> This interface MUST be implemented when chat module
 * is enabled. Application fails at startup if missing.</p>
 * 
 * @see ChatSession
 * @see ChatSessionService
 */
public interface ChatSessionAccessControlPolicy {
    
    /**
     * Check if user can create new conversations.
     * 
     * <p>Use cases:</p>
     * <ul>
     *   <li>Rate limiting (max conversations per user)</li>
     *   <li>Quota enforcement (free tier limits)</li>
     *   <li>Account status checks (suspended users)</li>
     * </ul>
     * 
     * @param ownerId User identifier (userId or sessionId)
     * @return true if user can create conversations, false denies creation
     */
    boolean canUserCreateConversation(String ownerId);
    
    /**
     * Check if user can access a specific conversation.
     * 
     * <p><strong>CRITICAL:</strong> This is the primary security check. You MUST verify:</p>
     * <ul>
     *   <li>Conversation belongs to requesting user (ownership check)</li>
     *   <li>OR user has admin/shared access rights</li>
     * </ul>
     * 
     * <p>Failing to verify ownership creates a security vulnerability where users
     * can access others' conversations.</p>
     * 
     * @param requestingUser User requesting access (userId or sessionId)
     * @param conversationId Conversation being accessed
     * @return true if access allowed, false denies access
     */
    boolean canUserAccessConversation(String requestingUser, String conversationId);
    
    /**
     * Check if user can delete a conversation.
     * 
     * <p>Typically:</p>
     * <ul>
     *   <li>Owners can delete their conversations</li>
     *   <li>Admins can delete any conversation</li>
     *   <li>Regular users cannot delete others' conversations</li>
     * </ul>
     * 
     * @param requestingUser User requesting deletion
     * @param conversationId Conversation to delete
     * @return true if deletion allowed, false denies deletion
     */
    boolean canUserDeleteConversation(String requestingUser, String conversationId);
    
    /**
     * Check if user can view conversation history/turns.
     * 
     * <p>May have stricter permissions than access (e.g., can chat but not export history).</p>
     * 
     * @param requestingUser User requesting history
     * @param conversationId Conversation to view
     * @return true if viewing allowed, false denies viewing
     */
    boolean canUserViewHistory(String requestingUser, String conversationId);
}
```

---

## 6. Service Implementation

### 6.1 ChatSessionService Interface

```java
package com.ai.infrastructure.chat.service;

import com.ai.infrastructure.chat.domain.*;
import java.util.*;

/**
 * Primary service interface for AI chat session management.
 * 
 * <p>Thread-safe and designed for high-concurrency scenarios.</p>
 * 
 * <p><strong>Security:</strong> All methods enforce access control via
 * ChatSessionAccessControlPolicy. Unauthorized access throws AccessDeniedException.</p>
 * 
 * @see ChatSession
 * @see ChatSessionAccessControlPolicy
 */
public interface ChatSessionService {

    /**
     * Creates a new conversation for the specified owner.
     * 
     * <p>The conversationId is provided by the client. If a conversation with
     * this ID already exists, this method returns the existing conversation.</p>
     * 
     * @param conversationId Conversation identifier (client-generated UUID)
     * @param ownerId Owner identifier (userId or sessionId)
     * @param metadata Optional conversation metadata
     * @return The created (or existing) conversation
     * @throws AccessDeniedException if owner cannot create conversations
     */
    ChatSession createConversation(String conversationId, String ownerId, Map<String, Object> metadata);

    /**
     * Gets conversation context (formatted history) for LLM prompt enrichment.
     * 
     * <p>This is the primary method called by RAGOrchestrator to load conversation
     * history before processing a query.</p>
     * 
     * <p><strong>Access Control:</strong> Verifies requesting user owns the conversation.</p>
     * 
     * @param conversationId The conversation identifier
     * @param requestingUser User requesting context (for access control)
     * @return Formatted conversation history (empty string if new conversation)
     * @throws AccessDeniedException if user doesn't own this conversation
     */
    String getConversationContext(String conversationId, String requestingUser);

    /**
     * Records a new turn in the conversation.
     * 
     * <p>Called by RAGOrchestrator after processing a query to record the
     * query/response pair.</p>
     * 
     * <p><strong>Behavior:</strong></p>
     * <ul>
     *   <li>If conversation doesn't exist, creates it</li>
     *   <li>Adds turn to conversation</li>
     *   <li>Updates lastInteractionAt timestamp</li>
     *   <li>Persists via ChatSessionStorageProvider</li>
     * </ul>
     * 
     * @param conversationId Conversation identifier
     * @param ownerId Owner identifier (for new conversations)
     * @param query User's query
     * @param response AI's response
     * @throws AccessDeniedException if owner doesn't have access
     */
    void recordTurn(String conversationId, String ownerId, String query, String response);

    /**
     * Gets a conversation by ID (full object).
     * 
     * @param conversationId Conversation identifier
     * @param requestingUser User requesting access
     * @return Optional containing conversation if found and accessible
     * @throws AccessDeniedException if user cannot access
     */
    Optional<ChatSession> getConversation(String conversationId, String requestingUser);

    /**
     * Lists all conversations for a user.
     * 
     * @param ownerId Owner identifier
     * @return List of user's conversations (may be empty, never null)
     */
    List<ChatSession> getUserConversations(String ownerId);

    /**
     * Deletes a conversation.
     * 
     * @param conversationId Conversation to delete
     * @param requestingUser User requesting deletion
     * @throws AccessDeniedException if user cannot delete
     */
    void deleteConversation(String conversationId, String requestingUser);

    /**
     * Invalidates (soft delete) a conversation.
     * 
     * @param conversationId Conversation to invalidate
     * @param requestingUser User requesting invalidation
     * @throws AccessDeniedException if user cannot invalidate
     */
    void invalidateConversation(String conversationId, String requestingUser);
}
```

### 6.2 ChatSessionServiceImpl (Framework Implementation)

```java
package com.ai.infrastructure.chat.service;

import com.ai.infrastructure.chat.domain.*;
import com.ai.infrastructure.chat.exception.*;
import com.ai.infrastructure.chat.spi.*;
import com.ai.infrastructure.chat.strategy.MemoryStrategy;
import com.ai.infrastructure.chat.config.ChatSessionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Chat session management service with user-provided storage and access control.
 * 
 * <p><strong>REQUIREMENTS:</strong></p>
 * <ul>
 *   <li>ChatSessionStorageProvider - Users MUST provide storage implementation</li>
 *   <li>ChatSessionAccessControlPolicy - Users MUST provide access control</li>
 * </ul>
 * 
 * <p><strong>Security:</strong> All operations enforce fail-closed access control.
 * Unauthorized access is denied and logged.</p>
 * 
 * <p><strong>Performance:</strong> Application-level cache for hot conversations.</p>
 * 
 * @see ChatSessionStorageProvider for storage SPI
 * @see ChatSessionAccessControlPolicy for security SPI
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "enabled",
    havingValue = "true"
)
@ConditionalOnBean({
    ChatSessionStorageProvider.class,
    ChatSessionAccessControlPolicy.class
})
public class ChatSessionServiceImpl implements ChatSessionService {
    
    // Error codes (constants)
    private static final String ERROR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    private static final String ERROR_SESSION_EXPIRED = "SESSION_EXPIRED";
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_INVALID_SESSION = "INVALID_SESSION";
    private static final String ERROR_STORAGE_FAILED = "STORAGE_FAILED";
    
    // Default values
    private static final int DEFAULT_WINDOW_SIZE = 5;
    
    // Required SPIs (user-provided)
    private final ChatSessionStorageProvider storage;
    private final ChatSessionAccessControlPolicy accessPolicy;
    
    // Framework dependencies
    private final MemoryStrategy memoryStrategy;
    private final ChatSessionProperties properties;
    
    // Application-level cache (performance optimization)
    private final ConcurrentMap<String, ChatSession> hotConversationCache = new ConcurrentHashMap<>();
    
    @Override
    public ChatSession createConversation(String conversationId, String ownerId, Map<String, Object> metadata) {
        // Security check
        if (!accessPolicy.canUserCreateConversation(ownerId)) {
            log.warn("Access denied: {} cannot create conversations", ownerId);
            throw new AccessDeniedException(
                "You do not have permission to create conversations",
                ERROR_ACCESS_DENIED,
                Map.of("ownerId", ownerId)
            );
        }
        
        // Check if already exists
        Optional<ChatSession> existing = storage.findById(conversationId);
        if (existing.isPresent()) {
            log.debug("Conversation already exists: {}", conversationId);
            return existing.get();
        }
        
        // Create new
        ChatSession session = ChatSession.builder()
            .id(conversationId)
            .ownerId(ownerId)
            .createdAt(LocalDateTime.now())
            .lastInteractionAt(LocalDateTime.now())
            .status(SessionStatus.ACTIVE)
            .metadata(metadata != null ? metadata : new HashMap<>())
            .turns(new ArrayList<>())
            .build();
        
        ChatSession saved = storage.save(session);
        hotConversationCache.put(conversationId, saved);
        
        log.info("Conversation created: id={}, owner={}", conversationId, ownerId);
        return saved;
    }
    
    @Override
    public String getConversationContext(String conversationId, String requestingUser) {
        // Load conversation (or return empty for new conversations)
        Optional<ChatSession> session = loadConversationWithAccessCheck(conversationId, requestingUser);
        
        if (session.isEmpty()) {
            log.debug("New conversation (no history): {}", conversationId);
            return "";  // Empty context for new conversations
        }
        
        // Format history using memory strategy
        List<ChatTurn> turns = session.get().getTurns();
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        
        // Apply memory strategy (pruning, summarization, etc.)
        List<ChatTurn> processedTurns = memoryStrategy.prune(turns, properties.getDefaultWindowSize());
        String formattedHistory = memoryStrategy.processHistory(processedTurns);
        
        log.debug("Loaded conversation context: conversationId={}, turns={}", 
            conversationId, processedTurns.size());
        
        return formattedHistory;
    }
    
    @Override
    public void recordTurn(String conversationId, String ownerId, String query, String response) {
        // Load or create conversation
        ChatSession conversation = storage.findById(conversationId)
            .orElseGet(() -> createConversation(conversationId, ownerId, null));
        
        // Verify access (fail-closed security)
        if (!canUserAccessConversation(requestingUser, conversation)) {
            log.warn("Access denied: {} cannot record to conversation {} owned by {}", 
                requestingUser, conversationId, conversation.getOwnerId());
            throw new AccessDeniedException(
                "You do not have permission to record to this conversation",
                ERROR_ACCESS_DENIED,
                Map.of(
                    "conversationId", conversationId,
                    "requestingUser", ownerId,
                    "owner", conversation.getOwnerId()
                )
            );
        }
        
        // Create and add turn
        ChatTurn turn = ChatTurn.builder()
            .userQuery(query)
            .aiResponse(response)
            .timestamp(LocalDateTime.now())
            .build();
        
        conversation.addTurn(turn);
        
        // Persist
        try {
            ChatSession saved = storage.save(conversation);
            hotConversationCache.put(conversationId, saved);
            
            log.debug("Turn recorded: conversationId={}, turnCount={}", 
                conversationId, saved.getTurnCount());
                
        } catch (Exception ex) {
            log.error("Failed to save conversation {}: {}", conversationId, ex.getMessage());
            throw new StorageException(
                "Failed to record turn to conversation",
                ERROR_STORAGE_FAILED,
                Map.of("conversationId", conversationId, "error", ex.getMessage())
            );
        }
    }
    
    @Override
    public Optional<ChatSession> getConversation(String conversationId, String requestingUser) {
        return loadConversationWithAccessCheck(conversationId, requestingUser);
    }
    
    @Override
    public List<ChatSession> getUserConversations(String ownerId) {
        try {
            return storage.findByOwnerId(ownerId);
        } catch (Exception ex) {
            log.error("Failed to load conversations for owner {}: {}", ownerId, ex.getMessage());
            return List.of();  // Graceful degradation
        }
    }
    
    @Override
    public void deleteConversation(String conversationId, String requestingUser) {
        // Load conversation
        Optional<ChatSession> session = storage.findById(conversationId);
        if (session.isEmpty()) {
            log.debug("Conversation not found for deletion: {}", conversationId);
            return;  // Idempotent
        }
        
        // Security check
        if (!accessPolicy.canUserDeleteConversation(requestingUser, conversationId)) {
            log.warn("Access denied: {} cannot delete conversation {}", requestingUser, conversationId);
            throw new AccessDeniedException(
                "You do not have permission to delete this conversation",
                ERROR_ACCESS_DENIED,
                Map.of("conversationId", conversationId, "requestingUser", requestingUser)
            );
        }
        
        // Delete
        storage.deleteById(conversationId);
        hotConversationCache.remove(conversationId);
        
        log.info("Conversation deleted: id={}, requestedBy={}", conversationId, requestingUser);
    }
    
    @Override
    public void invalidateConversation(String conversationId, String requestingUser) {
        Optional<ChatSession> session = loadConversationWithAccessCheck(conversationId, requestingUser);
        if (session.isEmpty()) {
            return;
        }
        
        ChatSession conversation = session.get();
        conversation.setStatus(SessionStatus.INVALIDATED);
        storage.save(conversation);
        hotConversationCache.remove(conversationId);
        
        log.info("Conversation invalidated: id={}, owner={}", conversationId, conversation.getOwnerId());
    }
    
    // Private helper methods
    
    /**
     * Loads conversation with access control check.
     * Returns empty if not found or access denied.
     */
    private Optional<ChatSession> loadConversationWithAccessCheck(String conversationId, String requestingUser) {
        // Check hot cache first (performance)
        ChatSession cached = hotConversationCache.get(conversationId);
        if (cached != null && !cached.isExpired(properties.getTtlMinutes())) {
            // Verify access
            if (canUserAccessConversation(requestingUser, cached)) {
                log.debug("Conversation cache hit: {}", conversationId);
                return Optional.of(cached);
            } else {
                log.warn("Access denied (cache): {} cannot access {}", requestingUser, conversationId);
                throw new AccessDeniedException(
                    "You do not have permission to access this conversation",
                    ERROR_ACCESS_DENIED,
                    Map.of("conversationId", conversationId, "requestingUser", requestingUser)
                );
            }
        }
        
        // Load from storage
        Optional<ChatSession> session = storage.findById(conversationId);
        
        if (session.isEmpty()) {
            return Optional.empty();
        }
        
        ChatSession conversation = session.get();
        
        // Check expiration
        if (conversation.isExpired(properties.getTtlMinutes())) {
            log.info("Conversation expired: id={}, lastInteraction={}", 
                conversationId, conversation.getLastInteractionAt());
            return Optional.empty();
        }
        
        // Access control check (fail-closed)
        if (!canUserAccessConversation(requestingUser, conversation)) {
            log.warn("Access denied: {} attempted to access conversation {} owned by {}", 
                requestingUser, conversationId, conversation.getOwnerId());
            throw new AccessDeniedException(
                "You do not have permission to access this conversation",
                ERROR_ACCESS_DENIED,
                Map.of(
                    "conversationId", conversationId,
                    "requestingUser", requestingUser,
                    "owner", conversation.getOwnerId()
                )
            );
        }
        
        // Cache and return
        hotConversationCache.put(conversationId, conversation);
        log.debug("Conversation loaded: id={}, turns={}", conversationId, conversation.getTurnCount());
        
        return Optional.of(conversation);
    }
    
    /**
     * Checks if requesting user can access conversation.
     * Combines ownership check with policy check.
     */
    private boolean canUserAccessConversation(String requestingUser, ChatSession conversation) {
        // Primary check: Ownership
        if (conversation.isOwnedBy(requestingUser)) {
            return true;
        }
        
        // Secondary check: Policy (for shared conversations, admin access, etc.)
        return accessPolicy.canUserAccessConversation(requestingUser, conversation.getId());
    }
}
```

---

## 7. Integration with RAGOrchestrator

### 7.1 Changes to OrchestrationContext

**File:** `ai-infrastructure-core/src/main/java/.../orchestration/OrchestrationContext.java`

**Change:** Add ONE field

```java
/**
 * Conversation ID for multi-turn chat tracking (optional).
 * 
 * <p>When provided, enables conversation history:</p>
 * <ul>
 *   <li>Loads previous turns from conversation</li>
 *   <li>Enriches query with conversation context</li>
 *   <li>Records query/response as new turn</li>
 * </ul>
 * 
 * <p><strong>Multiple Conversations:</strong> One user can have multiple conversations
 * by providing different conversationIds. Each conversation maintains separate history.</p>
 * 
 * <p><strong>Access Control:</strong> Conversations are owned by userId (authenticated)
 * or sessionId (anonymous). Users can only access their own conversations.</p>
 * 
 * <p><strong>Optional:</strong> Omit conversationId for stateless, single queries.</p>
 */
private String conversationId;

// Add helper method
public boolean hasConversation() {
    return conversationId != null && !conversationId.isBlank();
}

// Add factory method
public static OrchestrationContext forConversation(String userId, String conversationId) {
    return OrchestrationContext.builder()
        .userId(userId)
        .conversationId(conversationId)
        .build();
}
```

### 7.2 Changes to RAGOrchestrator

**File:** `ai-infrastructure-core/src/main/java/.../orchestration/RAGOrchestrator.java`

**Changes:** Add 1 dependency, 2 methods, 2 method calls (~30 lines total)

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    // ============ EXISTING DEPENDENCIES (unchanged) ============
    private final IntentQueryExtractor intentQueryExtractor;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final RAGService ragService;
    private final ResponseSanitizer responseSanitizer;
    private final AISecurityService securityService;
    private final AIAccessControlService accessControlService;
    private final AIComplianceService complianceService;
    private final PIIDetectionService piiDetectionService;
    private final IntentHistoryService intentHistoryService;
    private final SmartSuggestionsProperties smartSuggestionsProperties;
    private final PIIDetectionProperties piiDetectionProperties;
    private final Clock clock;
    
    // ============ NEW DEPENDENCY (chat module - optional) ============
    /**
     * Chat session service for conversation tracking.
     * Optional - only present when ai-infrastructure-chat-session module is included.
     */
    private final Optional<ChatSessionService> chatSessionService;
    
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(context, "context must not be null");
        context.validate();

        String identifier = context.getIdentifier();
        String requestId = context.getOrGenerateRequestId();
        LocalDateTime requestTimestamp = LocalDateTime.now(clock);
        
        // ============ NEW: Enrich query with conversation history ============
        String processedQuery = query;
        if (context.hasConversation() && chatSessionService.isPresent()) {
            processedQuery = enrichQueryWithConversationHistory(
                query,
                context.getConversationId(),
                identifier
            );
        }
        
        // ============ EXISTING: All current orchestration logic (UNCHANGED) ============
        
        // Security checks
        AISecurityResponse securityResponse = securityService.analyzeRequest(
            AISecurityRequest.builder()
                .requestId(requestId)
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .content(processedQuery)  // Use enriched query
                .operationType("INTENT_QUERY")
                .timestamp(requestTimestamp)
                .metadata(buildSecurityMetadata(context))
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .build()
        );

        if (Boolean.TRUE.equals(securityResponse.getShouldBlock())) {
            return OrchestrationResult.error("Request blocked by security controls.");
        }

        // Access control
        AIAccessControlResponse accessResponse = accessControlService.checkAccess(
            AIAccessControlRequest.builder()
                .requestId(requestId)
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .resourceId("rag:intent")
                .operationType("READ")
                .context(processedQuery)
                .metadata(buildAccessControlMetadata(context))
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .timestamp(requestTimestamp)
                .build()
        );

        if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
            return OrchestrationResult.error("Access denied by policy.");
        }

        // PII detection & redaction
        PIIDetectionResult piiResult = piiDetectionService.detectAndProcess(
            PIIDetectionRequest.builder()
                .content(processedQuery)
                .userId(identifier)
                .requestId(requestId)
                .mode(piiDetectionProperties.getMode())
                .build()
        );
        
        List<String> detectedPiiTypes = piiResult.getDetectedTypes();
        processedQuery = piiResult.getProcessedQuery();

        // Compliance check
        AIComplianceResponse complianceResponse = complianceService.checkCompliance(
            AIComplianceRequest.builder()
                .requestId(requestId)
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .content(processedQuery)
                .piiDetected(!detectedPiiTypes.isEmpty())
                .piiTypes(detectedPiiTypes)
                .timestamp(requestTimestamp)
                .build()
        );

        if (Boolean.FALSE.equals(complianceResponse.getOverallCompliant())) {
            return OrchestrationResult.error("Request failed compliance validation.");
        }

        // Intent extraction
        MultiIntentResponse multiIntentResponse = intentQueryExtractor.extract(processedQuery, context);

        if (!multiIntentResponse.hasIntents()) {
            log.warn("No intents extracted for query '{}'", processedQuery);
            return OrchestrationResult.error("Unable to determine user intent.");
        }

        // Handle intents
        OrchestrationResult result;
        if (multiIntentResponse.isCompound() || multiIntentResponse.getIntents().size() > 1) {
            result = handleCompoundIntents(multiIntentResponse, context);
        } else {
            result = handleSingleIntent(multiIntentResponse.getIntents().getFirst(), context);
        }

        // Ensure result is never null
        if (result == null) {
            log.error("Intent handling produced null result - this should never happen. Query: '{}'", processedQuery);
            return OrchestrationResult.error("Internal error: orchestration failed to produce a result");
        }

        // Add metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("sessionId", context.getSessionId());
        metadata.put("conversationId", context.getConversationId());  // NEW: Include conversationId
        metadata.put("intentsCount", multiIntentResponse.getIntents().size());
        metadata.put("compound", multiIntentResponse.isCompound());
        metadata.put("authenticated", context.isAuthenticated());
        if (!CollectionUtils.isEmpty(multiIntentResponse.getMetadata())) {
            metadata.put("intentMetadata", multiIntentResponse.getMetadata());
        }
        result.setMetadata(Collections.unmodifiableMap(metadata));

        applySmartSuggestions(result, context);

        // Sanitize response
        Map<String, Object> sanitizedPayload = responseSanitizer.sanitize(result, identifier);

        // Add PII detection results
        boolean detectOutput = piiDetectionProperties.isEnabled() &&
            detectionDirection == PIIDetectionDirection.INPUT_OUTPUT;

        if ((!detectedPiiTypes.isEmpty() || detectOutput) && sanitizedPayload.containsKey("sanitization")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sanitization = (Map<String, Object>) sanitizedPayload.get("sanitization");
            Map<String, Object> updatedSanitization = new LinkedHashMap<>(sanitization);

            @SuppressWarnings("unchecked")
            List<String> existingTypes = (List<String>) sanitization.get("detectedTypes");
            List<String> mergedTypes = new ArrayList<>();
            if (existingTypes != null) {
                mergedTypes.addAll(existingTypes);
            }
            mergedTypes.addAll(detectedPiiTypes);

            List<String> finalTypes = mergedTypes.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());

            if (!finalTypes.isEmpty()) {
                updatedSanitization.put("detectedTypes", finalTypes);
            }

            if (!updatedSanitization.equals(sanitization)) {
                Map<String, Object> updatedPayload = new LinkedHashMap<>(sanitizedPayload);
                updatedPayload.put("sanitization", Collections.unmodifiableMap(updatedSanitization));
                sanitizedPayload = Collections.unmodifiableMap(updatedPayload);
            }
        }

        result.setSanitizedPayload(sanitizedPayload);
        
        // ============ NEW: Record turn to conversation ============
        if (context.hasConversation() && chatSessionService.isPresent()) {
            recordTurnToConversation(
                context.getConversationId(),
                identifier,  // Owner
                query,       // Original query (not processed/enriched)
                result
            );
        }
        
        // Persist intent history
        persistIntentHistory(processedQuery, context, multiIntentResponse, result);

        return result;
    }
    
    // ============ NEW METHOD 1: Enrich with conversation history ============
    
    /**
     * Enriches query with conversation history if available.
     * 
     * <p>Loads previous turns from conversation and prepends them to current query,
     * giving the LLM context about what was discussed before.</p>
     * 
     * <p><strong>Error Handling:</strong> Graceful degradation. If history loading fails,
     * returns original query. Request continues without history rather than failing.</p>
     * 
     * @param currentQuery The current user query
     * @param conversationId Conversation identifier
     * @param ownerId Owner of the conversation (for access control)
     * @return Enriched query with history, or original query if history unavailable
     */
    private String enrichQueryWithConversationHistory(String currentQuery, 
                                                      String conversationId, 
                                                      String ownerId) {
        try {
            String conversationHistory = chatSessionService.get()
                .getConversationContext(conversationId, ownerId);
            
            if (conversationHistory != null && !conversationHistory.isBlank()) {
                log.debug("Enriching query with conversation history: conversationId={}, " +
                    "historyLength={}", conversationId, conversationHistory.length());
                    
                return String.format(
                    "Conversation History:\n%s\n\nCurrent Query: %s",
                    conversationHistory,
                    currentQuery
                );
            }
            
            log.debug("No history found for conversation: {}", conversationId);
            return currentQuery;
            
        } catch (AccessDeniedException ex) {
            log.warn("Access denied loading conversation {}: {}", conversationId, ex.getMessage());
            // Don't fail request - continue without history
            return currentQuery;
            
        } catch (Exception ex) {
            log.warn("Failed to load conversation history for {}: {}. Continuing without history.", 
                conversationId, ex.getMessage());
            // Graceful degradation - don't fail request
            return currentQuery;
        }
    }
    
    // ============ NEW METHOD 2: Record turn to conversation ============
    
    /**
     * Records the query/response turn to conversation.
     * 
     * <p>Called after successful query processing to persist the exchange for
     * future context.</p>
     * 
     * <p><strong>Error Handling:</strong> Failures are logged but don't fail the request.
     * The user gets their response even if turn recording fails.</p>
     * 
     * @param conversationId Conversation identifier
     * @param ownerId Owner of the conversation
     * @param originalQuery Original user query (before enrichment/processing)
     * @param result Orchestration result containing AI response
     */
    private void recordTurnToConversation(String conversationId,
                                          String ownerId,
                                          String originalQuery,
                                          OrchestrationResult result) {
        try {
            String aiResponse = result.getMessage();
            if (aiResponse == null || aiResponse.isBlank()) {
                log.debug("No response message to record for conversation: {}", conversationId);
                return;
            }
            
            chatSessionService.get().recordTurn(
                conversationId,
                ownerId,
                originalQuery,
                aiResponse
            );
            
            log.debug("Turn recorded: conversationId={}, owner={}", conversationId, ownerId);
            
        } catch (AccessDeniedException ex) {
            log.error("Access denied recording turn to conversation {}: {}", 
                conversationId, ex.getMessage());
            // Don't fail request
            
        } catch (Exception ex) {
            log.error("Failed to record turn to conversation {}: {}. User still gets response.", 
                conversationId, ex.getMessage());
            // Don't fail request - turn recording failure shouldn't break user experience
        }
    }
    
    // ============ EXISTING METHODS (all unchanged) ============
    // handleSingleIntent(), handleCompoundIntents(), handleAction(), 
    // handleInformation(), etc. - all remain exactly as they are
}
```

**Total LOC Added:** ~30 lines  
**Total LOC Changed:** 0 lines (pure addition)  
**Breaking Changes:** ZERO

---

## 8. Security Model

### 8.1 Security Principles

**Fail-Closed Model:**
- If ownership check fails → DENY access
- If policy check fails → DENY access
- If any security check fails → Log & throw exception

**Audit Logging:**
```java
// All denials logged at WARN level
log.warn("Access denied: {} attempted to access conversation {} owned by {}", 
    requestingUser, conversationId, ownerId);

// All access grants logged at DEBUG level
log.debug("Access granted: {} accessing conversation {}", requestingUser, conversationId);
```

### 8.2 Access Control Enforcement Points

1. **Creating conversation:**
   ```java
   if (!accessPolicy.canUserCreateConversation(ownerId)) {
       throw new AccessDeniedException(...);
   }
   ```

2. **Loading conversation:**
   ```java
   if (!conversation.isOwnedBy(requestingUser) && 
       !accessPolicy.canUserAccessConversation(requestingUser, conversationId)) {
       throw new AccessDeniedException(...);
   }
   ```

3. **Recording turn:**
   ```java
   if (!canUserAccessConversation(ownerId, conversation)) {
       throw new AccessDeniedException(...);
   }
   ```

4. **Deleting conversation:**
   ```java
   if (!accessPolicy.canUserDeleteConversation(requestingUser, conversationId)) {
       throw new AccessDeniedException(...);
   }
   ```

### 8.3 Ownership Model

```sql
-- Each conversation has exactly ONE owner
CREATE TABLE chat_sessions (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,  -- userId or sessionId
    -- ...
    INDEX idx_owner (owner_id)
);

-- Access rule:
-- requestingUser == owner_id → Access granted
-- requestingUser != owner_id → Check access policy → Usually denied
```

**Philosophy:** Conversations are private by default. Sharing requires explicit policy logic.

---

## 9. Configuration

### 9.1 Module Configuration

**File:** `config/ChatSessionProperties.java`

```java
package com.ai.infrastructure.chat.config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for chat session module.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.chat")
public class ChatSessionProperties {
    
    // Configuration value constants
    public static final String STRATEGY_SLIDING_WINDOW = "SLIDING_WINDOW";
    public static final String STRATEGY_SUMMARY = "SUMMARY";
    
    /**
     * Enable chat session module.
     * Default: false (opt-in)
     */
    private boolean enabled = false;
    
    /**
     * Memory strategy for conversation context processing.
     * Options: SLIDING_WINDOW, SUMMARY
     * Default: SLIDING_WINDOW
     */
    @NotNull
    private String memoryStrategy = STRATEGY_SLIDING_WINDOW;
    
    /**
     * Default number of recent turns to include in context window.
     */
    @Min(1)
    @Max(50)
    private int defaultWindowSize = 5;
    
    /**
     * Conversation TTL in minutes.
     * Conversations inactive longer than this are eligible for cleanup.
     */
    @Positive
    private int ttlMinutes = 60;
    
    /**
     * Maximum number of conversations to keep in hot cache.
     */
    @Positive
    private int hotCacheSize = 1000;
    
    /**
     * Enable automatic cleanup of expired conversations.
     */
    private boolean enableAutoCleanup = true;
    
    /**
     * Cleanup schedule (cron expression).
     * Default: Every hour
     */
    @NotBlank
    private String cleanupSchedule = "0 0 * * * *";
}
```

### 9.2 Auto-Configuration

```java
package com.ai.infrastructure.chat.config;

import com.ai.infrastructure.chat.service.*;
import com.ai.infrastructure.chat.spi.*;
import com.ai.infrastructure.chat.strategy.*;
import com.ai.infrastructure.core.AICoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for Chat Session module.
 * 
 * <p><strong>REQUIREMENTS:</strong> Users MUST provide:</p>
 * <ul>
 *   <li>ChatSessionStorageProvider implementation</li>
 *   <li>ChatSessionAccessControlPolicy implementation</li>
 * </ul>
 * 
 * <p>Application fails to start if these SPIs are missing when module is enabled.</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChatSessionProperties.class)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "enabled",
    havingValue = "true"
)
public class ChatSessionAutoConfiguration {
    
    /**
     * Chat session service bean.
     * Requires both storage and access control SPIs.
     */
    @Bean
    @ConditionalOnBean({
        ChatSessionStorageProvider.class,
        ChatSessionAccessControlPolicy.class
    })
    public ChatSessionService chatSessionService(
            ChatSessionStorageProvider storage,
            ChatSessionAccessControlPolicy accessPolicy,
            MemoryStrategy memoryStrategy,
            ChatSessionProperties properties) {
        
        log.info("Initializing ChatSessionService with:");
        log.info("  Storage: {}", storage.getClass().getSimpleName());
        log.info("  Access Policy: {}", accessPolicy.getClass().getSimpleName());
        log.info("  Memory Strategy: {}", memoryStrategy.getStrategyName());
        log.info("  Default Window Size: {}", properties.getDefaultWindowSize());
        log.info("  TTL: {} minutes", properties.getTtlMinutes());
        
        return new ChatSessionServiceImpl(storage, accessPolicy, memoryStrategy, properties);
    }
    
    /**
     * Sliding window memory strategy (default).
     * Keeps N most recent turns verbatim.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ai.chat",
        name = "memory-strategy",
        havingValue = ChatSessionProperties.STRATEGY_SLIDING_WINDOW,
        matchIfMissing = true
    )
    public MemoryStrategy slidingWindowMemoryStrategy() {
        log.info("Using SLIDING_WINDOW memory strategy");
        return new SlidingWindowMemoryStrategy();
    }
    
    /**
     * Summary memory strategy.
     * Summarizes older turns, keeps recent ones verbatim.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ai.chat",
        name = "memory-strategy",
        havingValue = ChatSessionProperties.STRATEGY_SUMMARY
    )
    public MemoryStrategy summaryMemoryStrategy(AICoreService llmService) {
        log.info("Using SUMMARY memory strategy");
        return new SummaryMemoryStrategy(llmService);
    }
}
```

### 9.3 Application Configuration

```yaml
ai:
  chat:
    enabled: true                    # Enable chat session module
    memory-strategy: SLIDING_WINDOW  # Or SUMMARY
    default-window-size: 5           # Recent turns to include
    ttl-minutes: 60                  # Conversation expiration
    hot-cache-size: 1000             # In-memory cache size
    enable-auto-cleanup: true        # Clean expired conversations
    cleanup-schedule: "0 0 * * * *"  # Every hour
```

---

## 10. Code Standards

### 10.1 Constants (Framework Requirement)

**EVERY class MUST extract magic strings to constants:**

```java
public class ChatSessionServiceImpl {
    
    // Error codes
    private static final String ERROR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    private static final String ERROR_SESSION_EXPIRED = "SESSION_EXPIRED";
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_INVALID_SESSION = "INVALID_SESSION";
    private static final String ERROR_STORAGE_FAILED = "STORAGE_FAILED";
    
    // Data keys (for response maps)
    private static final String DATA_KEY_CONVERSATION_ID = "conversationId";
    private static final String DATA_KEY_OWNER_ID = "ownerId";
    private static final String DATA_KEY_TURN_COUNT = "turnCount";
    private static final String DATA_KEY_CREATED_AT = "createdAt";
    private static final String DATA_KEY_LAST_INTERACTION = "lastInteractionAt";
    
    // Status values
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_INVALIDATED = "INVALIDATED";
    
    // Default values
    private static final int DEFAULT_WINDOW_SIZE = 5;
    private static final int DEFAULT_TTL_MINUTES = 60;
    private static final int DEFAULT_CACHE_SIZE = 1000;
}
```

### 10.2 JavaDoc Standards (Framework Requirement)

**ALL public classes/methods MUST have comprehensive JavaDoc:**

✅ **Required elements:**
- Class purpose
- Thread safety notes
- Security considerations
- Usage examples
- @param for all parameters
- @return description
- @throws for checked exceptions
- @see links to related classes

**Example:**
```java
/**
 * Records a turn to conversation with access control.
 * 
 * <p>This method:</p>
 * <ol>
 *   <li>Loads or creates the conversation</li>
 *   <li>Verifies owner has access (fail-closed security)</li>
 *   <li>Adds the turn to conversation history</li>
 *   <li>Persists via ChatSessionStorageProvider</li>
 *   <li>Updates hot cache</li>
 * </ol>
 * 
 * <p><strong>Security:</strong> Enforces fail-closed access control. If requesting
 * user doesn't own conversation, throws AccessDeniedException.</p>
 * 
 * <p><strong>Performance:</strong> Updates hot cache for fast subsequent access.</p>
 * 
 * @param conversationId Conversation identifier (client-provided UUID)
 * @param ownerId Owner identifier (userId or sessionId for anonymous)
 * @param query User's query/message
 * @param response AI's response
 * @throws AccessDeniedException if owner doesn't have access to conversation
 * @throws StorageException if persistence fails
 * @see ChatSessionAccessControlPolicy#canUserAccessConversation
 */
public void recordTurn(String conversationId, String ownerId, String query, String response) {
    // Implementation
}
```

### 10.3 Error Handling Standards

**All exceptions MUST include:**

```java
public class AccessDeniedException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> errorData;
    
    public AccessDeniedException(String message, String errorCode, Map<String, Object> data) {
        super(message);
        this.errorCode = errorCode;
        this.errorData = Collections.unmodifiableMap(data);
    }
    
    /**
     * Converts exception to structured error response.
     */
    public ErrorResponse toErrorResponse() {
        return ErrorResponse.builder()
            .success(false)
            .errorCode(errorCode)
            .message(getMessage())
            .data(errorData)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

---

## 11. Testing Requirements

### 11.1 Unit Tests (Minimum Required)

**ChatSessionServiceTest.java** (15+ tests):
```java
✅ shouldCreateConversationSuccessfully
✅ shouldDenyConversationCreationWhenPolicyDisallows (Security)
✅ shouldLoadConversationContextWithHistory
✅ shouldReturnEmptyContextForNewConversation
✅ shouldDenyAccessWhenUserDoesNotOwnConversation (Fail-Closed Security)
✅ shouldRecordTurnAndUpdateLastInteraction
✅ shouldCacheConversationsForPerformance (Caching Verification)
✅ shouldHandleMultipleConversationsPerUser
✅ shouldExpireConversationsAfterTTL
✅ shouldDeleteConversationAndClearCache
✅ shouldDenyDeletionWhenPolicyDisallows
✅ shouldHandleStorageExceptionsGracefully
✅ shouldEnforceOptimisticLockingOnConcurrentUpdates
✅ shouldPruneHistoryUsingMemoryStrategy
✅ shouldLogAllSecurityDecisions
```

**MemoryStrategyTest.java** (10+ tests):
```java
✅ shouldFormatTurnsCorrectly (SlidingWindow)
✅ shouldPruneToWindowSizeCorrectly
✅ shouldSummarizeOlderTurns (Summary strategy)
✅ shouldKeepRecentTurnsVerbatim
✅ shouldHandleEmptyHistory
✅ shouldHandleNullHistory
✅ shouldCalculateTokensCorrectly
✅ shouldSelectStrategyBasedOnConversationComplexity
```

**AccessControlPolicyTest.java** (5+ tests):
```java
✅ shouldAllowOwnerToAccessConversation
✅ shouldDenyNonOwnerAccess
✅ shouldEnforceRateLimits
✅ shouldCheckUserQuotas
✅ exampleImplementations (Documentation)
```

### 11.2 Integration Tests (5+ tests)

**ChatSessionIntegrationTest.java:**
```java
✅ shouldCreateAndRetrieveConversation
✅ shouldMaintainMultipleConversationsPerUser
✅ shouldIntegrateWithRAGOrchestrator
✅ shouldEnrichQueriesWithHistory
✅ shouldRecordTurnsAutomatically
```

### 11.3 RealAPI Tests (in integration-Testing module)

**ChatSessionRealApiIntegrationTest.java:**
```java
✅ shouldMaintainContextAcrossMultipleTurns (With real OpenAI)
✅ shouldUnderstandReferenceFromPreviousTurn
✅ shouldHandleMultipleConversationsConcurrently
✅ shouldSummarizeWhenHistoryGetsLong
✅ shouldWorkWithAndWithoutConversationId
```

---

## 12. User Implementation Guide

### 12.1 Quick Start

**Step 1: Add Module Dependency**

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-chat-session</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Step 2: Implement Storage SPI**

```java
package com.myapp.chat;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MyChatSessionStorage implements ChatSessionStorageProvider {
    
    private final ChatSessionRepository repository;  // Your JPA repository
    
    @Override
    public ChatSession save(ChatSession session) {
        return repository.save(session);
    }
    
    @Override
    public Optional<ChatSession> findById(String conversationId) {
        return repository.findById(conversationId);
    }
    
    @Override
    public void deleteById(String conversationId) {
        repository.deleteById(conversationId);
    }
    
    @Override
    public List<ChatSession> findByOwnerId(String ownerId) {
        return repository.findByOwnerId(ownerId);
    }
    
    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        return repository.findByLastInteractionAtBefore(cutoff);
    }
}

// Your JPA Repository
@Repository
interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByOwnerId(String ownerId);
    List<ChatSession> findByLastInteractionAtBefore(LocalDateTime cutoff);
}
```

**Step 3: Implement Access Control SPI**

```java
package com.myapp.chat;

import com.ai.infrastructure.chat.spi.ChatSessionAccessControlPolicy;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MyChatSessionAccessPolicy implements ChatSessionAccessControlPolicy {
    
    private final ChatSessionStorageProvider storage;
    private final UserService userService;
    
    @Override
    public boolean canUserCreateConversation(String ownerId) {
        // Check user is active and under quota
        return userService.isActive(ownerId) && 
               !userService.hasExceededConversationQuota(ownerId);
    }
    
    @Override
    public boolean canUserAccessConversation(String requestingUser, String conversationId) {
        // Load conversation
        var session = storage.findById(conversationId);
        if (session.isEmpty()) {
            return true;  // New conversation, allow creation
        }
        
        // CRITICAL: Ownership check
        if (session.get().getOwnerId().equals(requestingUser)) {
            return true;  // Owner can access
        }
        
        // Check admin rights
        return userService.isAdmin(requestingUser);
    }
    
    @Override
    public boolean canUserDeleteConversation(String requestingUser, String conversationId) {
        var session = storage.findById(conversationId);
        if (session.isEmpty()) {
            return true;  // Already gone, allow (idempotent)
        }
        
        // Owner or admin can delete
        return session.get().getOwnerId().equals(requestingUser) ||
               userService.isAdmin(requestingUser);
    }
    
    @Override
    public boolean canUserViewHistory(String requestingUser, String conversationId) {
        // Same as access for now
        return canUserAccessConversation(requestingUser, conversationId);
    }
}
```

**Step 4: Enable Module**

```yaml
ai:
  chat:
    enabled: true
```

**Step 5: Use in Application**

```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final RAGOrchestrator orchestrator;
    
    @PostMapping
    public ResponseEntity<OrchestrationResult> chat(@RequestBody ChatRequest request) {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId(request.getUserId())
            .conversationId(request.getConversationId())  // ← Enables chat
            .build();
        
        OrchestrationResult result = orchestrator.orchestrate(request.getQuery(), context);
        return ResponseEntity.ok(result);
    }
}
```

---

## 13. Complete Code Examples

### 13.1 Example: Redis Storage Implementation

```java
package com.myapp.infrastructure.chat;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based chat session storage for distributed systems.
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Fast access (in-memory with persistence)</li>
 *   <li>TTL support (automatic expiration)</li>
 *   <li>Owner indexing for user conversation lookup</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> Thread-safe via RedisTemplate.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatSessionStorage implements ChatSessionStorageProvider {
    
    // Redis key prefixes (constants)
    private static final String KEY_PREFIX_SESSION = "chat:session:";
    private static final String KEY_PREFIX_OWNER_INDEX = "chat:owner:";
    
    private final RedisTemplate<String, ChatSession> redisTemplate;
    
    @Override
    public ChatSession save(ChatSession session) {
        String key = KEY_PREFIX_SESSION + session.getId();
        
        // Save session with TTL
        redisTemplate.opsForValue().set(
            key,
            session,
            Duration.ofHours(1)
        );
        
        // Maintain owner index for findByOwnerId
        String ownerIndexKey = KEY_PREFIX_OWNER_INDEX + session.getOwnerId();
        redisTemplate.opsForSet().add(ownerIndexKey, session.getId());
        
        log.debug("Session saved to Redis: conversationId={}, owner={}", 
            session.getId(), session.getOwnerId());
        
        return session;
    }
    
    @Override
    public Optional<ChatSession> findById(String conversationId) {
        String key = KEY_PREFIX_SESSION + conversationId;
        ChatSession session = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(session);
    }
    
    @Override
    public void deleteById(String conversationId) {
        String key = KEY_PREFIX_SESSION + conversationId;
        redisTemplate.delete(key);
        
        log.debug("Session deleted from Redis: {}", conversationId);
    }
    
    @Override
    public List<ChatSession> findByOwnerId(String ownerId) {
        String ownerIndexKey = KEY_PREFIX_OWNER_INDEX + ownerId;
        Set<String> conversationIds = redisTemplate.opsForSet().members(ownerIndexKey);
        
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }
        
        return conversationIds.stream()
            .map(this::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        // Redis TTL handles expiration automatically
        // This method can return empty or scan for sessions to archive
        return List.of();
    }
}
```

### 13.2 Example: Database Storage Implementation

```java
package com.myapp.infrastructure.chat;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA/Database chat session storage for persistent conversations.
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Full persistence (survives restarts)</li>
 *   <li>Complex queries (search, analytics)</li>
 *   <li>ACID guarantees</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseChatSessionStorage implements ChatSessionStorageProvider {
    
    private final ChatSessionRepository repository;
    
    @Override
    public ChatSession save(ChatSession session) {
        ChatSession saved = repository.save(session);
        log.debug("Session saved to database: conversationId={}", saved.getId());
        return saved;
    }
    
    @Override
    public Optional<ChatSession> findById(String conversationId) {
        return repository.findById(conversationId);
    }
    
    @Override
    public void deleteById(String conversationId) {
        repository.deleteById(conversationId);
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

// JPA Repository
@Repository
interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByOwnerId(String ownerId);
    List<ChatSession> findByLastInteractionAtBeforeAndStatus(
        LocalDateTime cutoff,
        SessionStatus status
    );
}
```

### 13.3 Example: Frontend Integration

```typescript
// React Component with Chat
import { useState } from 'react';
import { v4 as uuidv4 } from 'uuid';

interface ChatMessage {
  query: string;
  response: string;
  timestamp: string;
}

export const ChatComponent = () => {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  
  const sendMessage = async (query: string) => {
    // First message - generate conversation ID
    const convId = conversationId || uuidv4();
    if (!conversationId) {
      setConversationId(convId);
    }
    
    // Send to backend
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        query: query,
        userId: getCurrentUserId(),
        conversationId: convId  // ← Same ID for all messages in this conversation
      })
    });
    
    const result = await response.json();
    
    // Add to UI
    setMessages([...messages, {
      query: query,
      response: result.message,
      timestamp: new Date().toISOString()
    }]);
  };
  
  return (
    <div className="chat-container">
      <div className="messages">
        {messages.map((msg, i) => (
          <div key={i}>
            <div className="user-message">{msg.query}</div>
            <div className="ai-message">{msg.response}</div>
          </div>
        ))}
      </div>
      <ChatInput onSend={sendMessage} />
    </div>
  );
};
```

---

## 14. Deployment & Operations

### 14.1 Deployment Checklist

**Before deploying chat module:**

- [ ] ChatSessionStorageProvider implemented?
- [ ] ChatSessionAccessControlPolicy implemented?
- [ ] Ownership checks enforce security?
- [ ] Configuration values set (TTL, window size)?
- [ ] Database schema created (if using DB storage)?
- [ ] Redis configured (if using Redis storage)?
- [ ] Unit tests passing (15+ tests)?
- [ ] Integration tests passing?
- [ ] RealAPI tests passing?
- [ ] Monitoring/logging configured?
- [ ] Cleanup job scheduled (if enabled)?

### 14.2 Monitoring

**Key Metrics to Track:**

```java
// Metrics to expose
- chat.conversations.active (gauge)
- chat.conversations.created.total (counter)
- chat.turns.recorded.total (counter)
- chat.cache.hits.total (counter)
- chat.cache.misses.total (counter)
- chat.access.denied.total (counter)
- chat.errors.storage.total (counter)
```

### 14.3 Production Configuration

```yaml
ai:
  chat:
    enabled: true
    memory-strategy: SLIDING_WINDOW
    default-window-size: 5
    ttl-minutes: 120                 # 2 hours for production
    hot-cache-size: 5000             # Larger cache for production
    enable-auto-cleanup: true
    cleanup-schedule: "0 0 */6 * * *"  # Every 6 hours

# Storage-specific (user's choice)
spring:
  redis:  # If using Redis
    host: redis.production.com
    port: 6379
    
  datasource:  # If using Database
    url: jdbc:postgresql://db.production.com:5432/chatdb
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

---

## 15. Summary

### 15.1 What We Built

A **separate, optional chat session module** that:

✅ Tracks conversation history (query/response pairs)  
✅ Enriches queries with conversation context  
✅ Supports multiple conversations per user  
✅ Enforces fail-closed access control  
✅ Allows user-provided storage (SPI)  
✅ Integrates seamlessly with RAGOrchestrator  
✅ Requires ZERO changes to existing orchestration logic  
✅ Follows ALL framework philosophy principles  

### 15.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Separate Module** | Clean separation, optional feature |
| **Three Identifiers** | userId (auth), sessionId (anon), conversationId (chat) |
| **User-Provided Storage** | Users know their infrastructure best |
| **Required Access Control** | Security cannot be optional |
| **Optional in Core** | `Optional<ChatSessionService>` - graceful if absent |
| **Greenfield** | No backward compatibility, clean design |
| **All Constants** | Zero magic strings |
| **Comprehensive Tests** | 30+ tests required |

### 15.3 Compliance with Framework Philosophy

| Principle | Implementation | Compliant |
|-----------|---------------|-----------|
| **Greenfield Architecture** | No backward compatibility | ✅ YES |
| **Security-First** | Required access control, fail-closed | ✅ YES |
| **SPI Extensibility** | Storage & access control SPIs | ✅ YES |
| **Clean Separation** | Separate module, minimal core changes | ✅ YES |
| **No Magic Strings** | All constants extracted | ✅ YES |
| **Performance** | Application-level caching | ✅ YES |
| **Production Purity** | No test code in production | ✅ YES |
| **Comprehensive Docs** | Full JavaDoc, examples | ✅ YES |

**Overall Compliance:** 100% ✅

---

## 16. Implementation Roadmap

### Phase 1: Foundation (Week 1)
- [ ] Create module structure
- [ ] Implement domain models (ChatSession, ChatTurn)
- [ ] Define SPI interfaces
- [ ] Extract all constants
- [ ] Write comprehensive JavaDoc

### Phase 2: Service Layer (Week 1-2)
- [ ] Implement ChatSessionService
- [ ] Add security enforcement
- [ ] Add caching layer
- [ ] Implement memory strategies
- [ ] Error handling

### Phase 3: Core Integration (Week 2)
- [ ] Add conversationId to OrchestrationContext
- [ ] Add Optional<ChatSessionService> to RAGOrchestrator
- [ ] Implement enrichment & recording methods
- [ ] Test integration

### Phase 4: Testing (Week 2-3)
- [ ] Unit tests (30+ tests)
- [ ] Integration tests
- [ ] RealAPI tests
- [ ] Performance tests

### Phase 5: Documentation & Deployment (Week 3)
- [ ] User guide
- [ ] Example implementations
- [ ] Migration guide
- [ ] Production deployment

**Total Timeline:** 3 weeks

---

**Document Version:** 3.0 - Final  
**Status:** ✅ Ready for Implementation  
**Compliance:** 100% with Framework Standards  
**Author:** AI Fabric Framework Team  
**Date:** January 2026

---

**This is the single source of truth for chat session implementation. Follow this document exactly.**

