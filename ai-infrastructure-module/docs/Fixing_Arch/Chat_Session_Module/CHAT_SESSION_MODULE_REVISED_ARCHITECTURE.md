# Chat Session Module - Revised Architecture (Framework Standards Compliant)

**Version:** 2.0 (Revised)  
**Status:** Implementation Ready  
**Compliance:** ✅ Follows AI Infrastructure Framework Standards

---

## Executive Summary

The Chat Session Module is redesigned as a **separate, standalone module** (`ai-infrastructure-chat-session`) that follows our framework's core principles:

✅ **Greenfield Architecture** - No backward compatibility  
✅ **SPI Extensibility** - Users provide their own storage  
✅ **Security-First** - Required access control policy  
✅ **Clean Separation** - Independent module, not embedded  
✅ **No Magic Strings** - All constants extracted  
✅ **Production Purity** - No test code in production  

---

## Module Structure

### New Module: `ai-infrastructure-chat-session`

```
ai-infrastructure-module/
├── ai-infrastructure-core/              # Unchanged
├── ai-infrastructure-relationship-query/ # Unchanged
├── ai-infrastructure-behavior/           # Unchanged
└── ai-infrastructure-chat-session/      # ← NEW MODULE
    ├── src/main/java/com/ai/infrastructure/chat/
    │   ├── domain/
    │   │   ├── ChatSession.java
    │   │   ├── ChatTurn.java
    │   │   └── SessionMetadata.java
    │   ├── service/
    │   │   ├── ChatSessionService.java
    │   │   └── ChatSessionServiceImpl.java
    │   ├── spi/
    │   │   ├── ChatSessionStorageProvider.java     # ← SPI (users implement)
    │   │   └── ChatSessionAccessControlPolicy.java # ← SPI (users implement)
    │   ├── strategy/
    │   │   ├── MemoryStrategy.java                 # ← Interface
    │   │   ├── SlidingWindowStrategy.java          # Framework provides
    │   │   └── SummaryStrategy.java                # Framework provides
    │   ├── config/
    │   │   ├── ChatSessionProperties.java
    │   │   └── ChatSessionAutoConfiguration.java
    │   └── exception/
    │       ├── SessionNotFoundException.java
    │       ├── SessionExpiredException.java
    │       └── AccessDeniedException.java
    └── src/test/java/...
```

---

## SPI Pattern: User-Provided Storage

### 1. Storage SPI Interface (Framework Provides)

```java
package com.ai.infrastructure.chat.spi;

import com.ai.infrastructure.chat.domain.ChatSession;
import java.util.List;
import java.util.Optional;

/**
 * SPI for chat session storage.
 * 
 * <p>Framework users MUST implement this interface to provide their own storage backend.
 * The application will fail to start if no implementation is provided when chat session
 * module is enabled.</p>
 * 
 * <p><strong>Example implementations:</strong></p>
 * <ul>
 *   <li>Redis-based storage for distributed systems</li>
 *   <li>Database storage for persistence</li>
 *   <li>In-memory storage for development/testing</li>
 *   <li>Hybrid storage (hot cache + cold database)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> Implementations MUST be thread-safe as they will
 * be used concurrently by multiple requests.</p>
 * 
 * <p><strong>Example Implementation:</strong></p>
 * <pre>{@code
 * @Component
 * public class MyChatSessionStorage implements ChatSessionStorageProvider {
 *     
 *     private final RedisTemplate<String, ChatSession> redis;
 *     
 *     @Override
 *     public ChatSession save(ChatSession session) {
 *         redis.opsForValue().set(session.getId(), session, Duration.ofHours(1));
 *         return session;
 *     }
 *     
 *     @Override
 *     public Optional<ChatSession> findById(String sessionId) {
 *         return Optional.ofNullable(redis.opsForValue().get(sessionId));
 *     }
 *     
 *     @Override
 *     public void deleteById(String sessionId) {
 *         redis.delete(sessionId);
 *     }
 *     
 *     @Override
 *     public List<ChatSession> findByUserId(String userId) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>REQUIREMENT:</strong> This interface MUST be implemented when chat session
 * module is enabled ({@code ai.chat.enabled=true}). The application will fail to start
 * if no implementation is provided.</p>
 */
public interface ChatSessionStorageProvider {
    
    /**
     * Saves or updates a chat session.
     * 
     * @param session The session to save
     * @return The saved session
     * @throws StorageException if save fails
     */
    ChatSession save(ChatSession session);
    
    /**
     * Retrieves a session by ID.
     * 
     * @param sessionId The session identifier
     * @return Optional containing the session if found
     */
    Optional<ChatSession> findById(String sessionId);
    
    /**
     * Deletes a session by ID.
     * 
     * @param sessionId The session identifier
     */
    void deleteById(String sessionId);
    
    /**
     * Finds all sessions for a user.
     * 
     * @param userId The user identifier
     * @return List of sessions (may be empty, never null)
     */
    List<ChatSession> findByUserId(String userId);
    
    /**
     * Finds expired sessions for cleanup.
     * 
     * @param ttlMinutes TTL in minutes
     * @return List of expired sessions
     */
    List<ChatSession> findExpiredSessions(int ttlMinutes);
}
```

### 2. Access Control SPI (Framework Provides)

```java
package com.ai.infrastructure.chat.spi;

/**
 * SPI for chat session access control.
 * 
 * <p>Framework users MUST implement this interface when chat session module is enabled.
 * The application will fail to start if no implementation is provided.</p>
 * 
 * <p><strong>Example Implementation:</strong></p>
 * <pre>{@code
 * @Component
 * public class MyChatSessionAccessPolicy implements ChatSessionAccessControlPolicy {
 *     
 *     private final SessionRepository repository;
 *     
 *     @Override
 *     public boolean canUserCreateSession(String userId) {
 *         // Check user limits, quotas, etc.
 *         return userService.isActive(userId);
 *     }
 *     
 *     @Override
 *     public boolean canUserAccessSession(String userId, String sessionId) {
 *         // CRITICAL: Verify session belongs to user
 *         ChatSession session = repository.findById(sessionId);
 *         return session != null && session.getUserId().equals(userId);
 *     }
 * }
 * }</pre>
 */
public interface ChatSessionAccessControlPolicy {
    
    /**
     * Check if user can create new chat sessions.
     * 
     * @param userId User identifier
     * @return true if user can create sessions
     */
    boolean canUserCreateSession(String userId);
    
    /**
     * Check if user can access a specific session.
     * CRITICAL: Verify session ownership!
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @return true if user can access this session
     */
    boolean canUserAccessSession(String userId, String sessionId);
    
    /**
     * Check if user can delete a session.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @return true if user can delete this session
     */
    boolean canUserDeleteSession(String userId, String sessionId);
    
    /**
     * Check if user can view session history.
     * 
     * @param userId User identifier
     * @param sessionId Session identifier
     * @return true if user can view history
     */
    boolean canUserViewHistory(String userId, String sessionId);
}
```

---

## Service Implementation (Requires SPIs)

```java
package com.ai.infrastructure.chat.service;

import com.ai.infrastructure.chat.domain.*;
import com.ai.infrastructure.chat.spi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Chat session management service with user-provided storage and access control.
 * 
 * <p><strong>REQUIREMENTS:</strong></p>
 * <ul>
 *   <li>ChatSessionStorageProvider - Users MUST provide storage implementation</li>
 *   <li>ChatSessionAccessControlPolicy - Users MUST provide access control</li>
 * </ul>
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
})  // ← Required SPIs
public class ChatSessionServiceImpl implements ChatSessionService {
    
    // Constants
    private static final String ERROR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    private static final String ERROR_SESSION_EXPIRED = "SESSION_EXPIRED";
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_INVALID_SESSION = "INVALID_SESSION";
    
    // Required SPIs (provided by users)
    private final ChatSessionStorageProvider storage;
    private final ChatSessionAccessControlPolicy accessPolicy;
    
    // Framework dependencies
    private final MemoryStrategy memoryStrategy;
    private final ChatSessionProperties properties;
    
    // Application-level cache
    private final ConcurrentMap<String, ChatSession> hotSessionCache = new ConcurrentHashMap<>();
    
    @Override
    public ChatSession createSession(String userId, Map<String, Object> metadata) {
        // Security check
        if (!accessPolicy.canUserCreateSession(userId)) {
            log.warn("Access denied: user {} cannot create chat sessions", userId);
            throw new AccessDeniedException(
                "You do not have permission to create chat sessions",
                ERROR_ACCESS_DENIED,
                Map.of("userId", userId)
            );
        }
        
        ChatSession session = ChatSession.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .createdAt(LocalDateTime.now())
            .lastInteractionAt(LocalDateTime.now())
            .status(SessionStatus.ACTIVE)
            .metadata(metadata != null ? metadata : new HashMap<>())
            .turns(new ArrayList<>())
            .build();
        
        ChatSession saved = storage.save(session);
        hotSessionCache.put(saved.getId(), saved);
        
        log.info("Chat session created: sessionId={}, userId={}", saved.getId(), userId);
        return saved;
    }
    
    @Override
    public Optional<ChatSession> getSession(String sessionId, String userId) {
        // Security check (CRITICAL!)
        if (!accessPolicy.canUserAccessSession(userId, sessionId)) {
            log.warn("Access denied: user {} attempted to access session {}", userId, sessionId);
            throw new AccessDeniedException(
                "You do not have permission to access this session",
                ERROR_ACCESS_DENIED,
                Map.of("sessionId", sessionId, "userId", userId)
            );
        }
        
        // Check hot cache first
        ChatSession cached = hotSessionCache.get(sessionId);
        if (cached != null && !cached.isExpired(properties.getTtlMinutes())) {
            log.debug("Session cache hit: {}", sessionId);
            return Optional.of(cached);
        }
        
        // Load from user-provided storage
        Optional<ChatSession> session = storage.findById(sessionId);
        
        if (session.isEmpty()) {
            log.debug("Session not found: {}", sessionId);
            return Optional.empty();
        }
        
        // Check expiration
        if (session.get().isExpired(properties.getTtlMinutes())) {
            log.info("Session expired: sessionId={}, lastInteraction={}", 
                sessionId, session.get().getLastInteractionAt());
            return Optional.empty();
        }
        
        // Cache and return
        hotSessionCache.put(sessionId, session.get());
        log.debug("Session loaded from storage: {}", sessionId);
        
        return session;
    }
    
    @Override
    public void deleteSession(String sessionId, String userId) {
        // Security check
        if (!accessPolicy.canUserDeleteSession(userId, sessionId)) {
            log.warn("Access denied: user {} attempted to delete session {}", userId, sessionId);
            throw new AccessDeniedException(
                "You do not have permission to delete this session",
                ERROR_ACCESS_DENIED,
                Map.of("sessionId", sessionId, "userId", userId)
            );
        }
        
        storage.deleteById(sessionId);
        hotSessionCache.remove(sessionId);
        
        log.info("Session deleted: sessionId={}, userId={}", sessionId, userId);
    }
    
    // Additional methods...
}
```

---

## Configuration (Auto-Configuration)

```java
package com.ai.infrastructure.chat.config;

import com.ai.infrastructure.chat.service.*;
import com.ai.infrastructure.chat.spi.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Chat Session module.
 * 
 * <p><strong>REQUIREMENTS:</strong> Users MUST provide:</p>
 * <ul>
 *   <li>ChatSessionStorageProvider implementation</li>
 *   <li>ChatSessionAccessControlPolicy implementation</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChatSessionProperties.class)
@ConditionalOnProperty(
    prefix = "ai.chat",
    name = "enabled",
    havingValue = "true"
)
public class ChatSessionAutoConfiguration {
    
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
        
        log.info("Initializing ChatSessionService with user-provided storage: {}", 
            storage.getClass().getSimpleName());
        
        return new ChatSessionServiceImpl(storage, accessPolicy, memoryStrategy, properties);
    }
    
    // Framework provides default strategies (users can override)
    @Bean
    @ConditionalOnProperty(
        prefix = "ai.chat",
        name = "memory-strategy",
        havingValue = "SLIDING_WINDOW",
        matchIfMissing = true
    )
    public MemoryStrategy slidingWindowMemoryStrategy() {
        log.info("Using SLIDING_WINDOW memory strategy");
        return new SlidingWindowMemoryStrategy();
    }
    
    @Bean
    @ConditionalOnProperty(
        prefix = "ai.chat",
        name = "memory-strategy",
        havingValue = "SUMMARY"
    )
    public MemoryStrategy summaryMemoryStrategy(LLMService llmService) {
        log.info("Using SUMMARY memory strategy");
        return new SummaryMemoryStrategy(llmService);
    }
}
```

---

## User Implementation Example

### Example 1: Redis Storage

```java
package com.myapp.infrastructure;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based chat session storage implementation.
 */
@Component
@RequiredArgsConstructor
public class RedisChatSessionStorage implements ChatSessionStorageProvider {
    
    private static final String KEY_PREFIX = "chat:session:";
    private static final String USER_INDEX_PREFIX = "chat:user:";
    
    private final RedisTemplate<String, ChatSession> redisTemplate;
    
    @Override
    public ChatSession save(ChatSession session) {
        String key = KEY_PREFIX + session.getId();
        redisTemplate.opsForValue().set(key, session, Duration.ofHours(1));
        
        // Maintain user index
        String userKey = USER_INDEX_PREFIX + session.getUserId();
        redisTemplate.opsForSet().add(userKey, session.getId());
        
        return session;
    }
    
    @Override
    public Optional<ChatSession> findById(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
    
    @Override
    public void deleteById(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
    }
    
    @Override
    public List<ChatSession> findByUserId(String userId) {
        String userKey = USER_INDEX_PREFIX + userId;
        Set<String> sessionIds = redisTemplate.opsForSet().members(userKey);
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        
        return sessionIds.stream()
            .map(this::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        // Implementation depends on indexing strategy
        return List.of();
    }
}
```

### Example 2: Database Storage

```java
package com.myapp.infrastructure;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA/Database chat session storage implementation.
 */
@Component
@RequiredArgsConstructor
public class DatabaseChatSessionStorage implements ChatSessionStorageProvider {
    
    private final ChatSessionRepository repository;  // User's JPA repository
    
    @Override
    public ChatSession save(ChatSession session) {
        return repository.save(session);
    }
    
    @Override
    public Optional<ChatSession> findById(String sessionId) {
        return repository.findById(sessionId);
    }
    
    @Override
    public void deleteById(String sessionId) {
        repository.deleteById(sessionId);
    }
    
    @Override
    public List<ChatSession> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }
    
    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        return repository.findByLastInteractionAtBefore(cutoff);
    }
}

// User's JPA Repository
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByUserId(String userId);
    List<ChatSession> findByLastInteractionAtBefore(LocalDateTime cutoff);
}
```

### Example 3: Hybrid Storage (Hot Cache + Cold DB)

```java
package com.myapp.infrastructure;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid storage: In-memory cache for hot sessions, database for cold storage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridChatSessionStorage implements ChatSessionStorageProvider {
    
    private final ChatSessionRepository database;
    private final ConcurrentMap<String, ChatSession> hotCache = new ConcurrentHashMap<>();
    
    @Override
    public ChatSession save(ChatSession session) {
        // Save to both cache and database
        ChatSession saved = database.save(session);
        hotCache.put(saved.getId(), saved);
        log.debug("Session saved to hybrid storage: {}", saved.getId());
        return saved;
    }
    
    @Override
    public Optional<ChatSession> findById(String sessionId) {
        // Check hot cache first
        ChatSession cached = hotCache.get(sessionId);
        if (cached != null) {
            log.debug("Session cache hit: {}", sessionId);
            return Optional.of(cached);
        }
        
        // Load from database
        Optional<ChatSession> session = database.findById(sessionId);
        session.ifPresent(s -> {
            hotCache.put(sessionId, s);
            log.debug("Session loaded from database and cached: {}", sessionId);
        });
        
        return session;
    }
    
    @Override
    public void deleteById(String sessionId) {
        database.deleteById(sessionId);
        hotCache.remove(sessionId);
    }
    
    @Override
    public List<ChatSession> findByUserId(String userId) {
        return database.findByUserId(userId);
    }
    
    @Override
    public List<ChatSession> findExpiredSessions(int ttlMinutes) {
        return database.findExpiredSessions(ttlMinutes);
    }
}
```

---

## Module Dependencies

### pom.xml

```xml
<project>
    <artifactId>ai-infrastructure-chat-session</artifactId>
    <name>AI Infrastructure Chat Session Module</name>
    
    <dependencies>
        <!-- Framework dependency -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        
        <!-- JPA (optional - only if users choose database storage) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## Configuration

### application.yml

```yaml
ai:
  chat:
    enabled: true          # Enable chat session module
    memory-strategy: SLIDING_WINDOW  # Or SUMMARY
    default-window-size: 5
    ttl-minutes: 60
    cache-size: 1000       # Hot cache size
```

**Note:** No `storage-type` config! Users provide storage via SPI.

---

## User's Application Setup

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-chat-session</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Implement Storage SPI

```java
@Component
public class MyChatSessionStorage implements ChatSessionStorageProvider {
    // Your storage implementation (Redis, DB, etc.)
}
```

### Step 3: Implement Access Control SPI

```java
@Component
public class MyChatSessionAccessPolicy implements ChatSessionAccessControlPolicy {
    // Your access control logic
}
```

### Step 4: Enable Module

```yaml
ai:
  chat:
    enabled: true
```

**That's it!** The framework uses your implementations.

---

## Benefits of Separate Module + User Storage

### ✅ **Advantages:**

1. **Clean Separation**
   - Chat module is optional
   - Doesn't bloat core module
   - Users choose to include it or not

2. **User Control**
   - Users provide their own storage backend
   - Fits their architecture (Redis, Cassandra, MongoDB, etc.)
   - No framework-imposed storage choice

3. **No Unused Code**
   - If user uses Redis, no database code loaded
   - If user uses database, no Redis code loaded
   - Smaller deployment footprint

4. **Testability**
   - Users test with their own storage
   - Framework tests with mock storage
   - Clear boundaries

5. **Extensibility**
   - Users can implement exotic storage (S3, Kafka, etc.)
   - Framework doesn't need to support every backend
   - True SPI pattern

### ✅ **Follows Framework Philosophy:**

| Principle | How It's Applied |
|-----------|------------------|
| **Greenfield** | No backward compat, clean module |
| **Security-First** | Required access control SPI |
| **SPI Extensibility** | Users provide storage + access control |
| **Separation** | Separate module, not embedded |
| **No Magic Strings** | All constants extracted |
| **Required Dependencies** | SPIs required when enabled |

---

## Comparison: Embedded vs Separate

| Aspect | Embedded (Current Proposal) | Separate Module (Recommended) |
|--------|----------------------------|-------------------------------|
| **Core Module Size** | Larger (includes chat) | ✅ Smaller (no chat code) |
| **Optional Feature** | Harder to disable | ✅ Easy (don't include module) |
| **Storage Choice** | 3 implementations in framework | ✅ User provides (infinite choices) |
| **Unused Code** | Users get all 3 storage types | ✅ Users get only what they implement |
| **Testing** | Framework tests all storage types | ✅ Users test their implementation |
| **Extensibility** | Limited to 3 types | ✅ Unlimited (SPI) |
| **Dependencies** | Core depends on Redis, etc. | ✅ Core has no extra dependencies |

---

## Module Structure Summary

```
ai-infrastructure-module/
├── ai-infrastructure-core/                    # Core (unchanged)
├── ai-infrastructure-relationship-query/      # Relationship queries
├── ai-infrastructure-behavior/                # Behavior analytics
└── ai-infrastructure-chat-session/           # ← NEW SEPARATE MODULE
    ├── spi/
    │   ├── ChatSessionStorageProvider.java    # Users implement
    │   └── ChatSessionAccessControlPolicy.java # Users implement
    ├── service/
    │   └── ChatSessionService.java            # Framework provides
    ├── domain/
    │   ├── ChatSession.java                   # Framework provides
    │   └── ChatTurn.java                      # Framework provides
    └── strategy/
        ├── MemoryStrategy.java                # Interface
        ├── SlidingWindowStrategy.java         # Framework provides
        └── SummaryStrategy.java               # Framework provides
```

---

## Final Recommendation

**✅ YES - Make it a separate module with user-provided storage!**

**Implementation Approach:**

1. **Create new module:** `ai-infrastructure-chat-session`
2. **Define 2 SPIs:**
   - `ChatSessionStorageProvider` (users provide storage)
   - `ChatSessionAccessControlPolicy` (users provide security)
3. **Fail at startup** if SPIs missing when module enabled
4. **Framework provides:**
   - Domain models (ChatSession, ChatTurn)
   - Service interface & implementation
   - Memory strategies (Sliding Window, Summary)
   - Configuration properties
5. **Users provide:**
   - Storage implementation (Redis, DB, whatever they want)
   - Access control logic
   - Optional: Custom memory strategies

**This follows the EXACT same pattern as:**
- `ai-infrastructure-relationship-query` (separate module)
- `RelationshipQueryAccessControlPolicy` (user-provided SPI)
- `BehaviorContextProvider` (user-provided SPI)

**Clean, extensible, and follows framework philosophy perfectly!** 🎯

Would you like me to create the detailed implementation guide for the separate module with user-provided storage?
