# Conversation ID Design - Correct Architecture

**Issue:** Single user can have multiple conversations  
**Solution:** Add `conversationId` field separate from `sessionId`

---

## The Requirement

### User Story:
```
As an authenticated user,
I want to have multiple concurrent conversations with the AI,
So that I can track different topics/projects separately.

Example:
- User "alice" has:
  - Conversation A: "Help with Project X" (conversationId: "conv-001")
  - Conversation B: "Support for Issue Y" (conversationId: "conv-002")
  - Conversation C: "Research Topic Z" (conversationId: "conv-003")
```

### Access Control Requirement:
```
- Alice can access conversations: conv-001, conv-002, conv-003
- Bob CANNOT access Alice's conversations
- Each conversation linked to owner (userId)
```

---

## Solution: Add `conversationId` to OrchestrationContext

### 1. Update OrchestrationContext

```java
package com.ai.infrastructure.intent.orchestration;

/**
 * Context for orchestration requests.
 * 
 * <p><strong>Identifiers:</strong></p>
 * <ul>
 *   <li><strong>userId</strong> - User identification (authenticated users)</li>
 *   <li><strong>sessionId</strong> - Session identification (anonymous users, required if no userId)</li>
 *   <li><strong>conversationId</strong> - Conversation tracking (optional, enables chat history)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationContext {

    /**
     * User ID for authenticated users.
     */
    private String userId;

    /**
     * Session ID for anonymous user tracking.
     * Required when userId is null (anonymous requests).
     */
    private String sessionId;
    
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
     * <p><strong>Usage:</strong></p>
     * <ul>
     *   <li>Authenticated user can have multiple conversations (different conversationIds)</li>
     *   <li>Anonymous user's conversations tracked by conversationId</li>
     *   <li>Omit conversationId for single, stateless queries</li>
     * </ul>
     * 
     * <p><strong>Access Control:</strong> Conversations are linked to userId (or sessionId
     * for anonymous). Users can only access their own conversations.</p>
     */
    private String conversationId;  // NEW

    private String requestId;
    private String ipAddress;
    private String userAgent;
    private Locale locale;
    
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // Existing methods (unchanged)
    public boolean isAuthenticated() {
        return userId != null && !userId.isBlank();
    }

    public boolean isAnonymous() {
        return !isAuthenticated();
    }

    public String getIdentifier() {
        return isAuthenticated() ? userId : sessionId;
    }
    
    // NEW: Check if conversation tracking enabled
    public boolean hasConversation() {
        return conversationId != null && !conversationId.isBlank();
    }

    public void validate() {
        // Require userId OR sessionId for identification
        if (!isAuthenticated() && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException(
                "OrchestrationContext must include userId (authenticated) or sessionId (anonymous)"
            );
        }
    }
    
    // NEW: Factory for conversation
    public static OrchestrationContext forConversation(String userId, String conversationId) {
        return OrchestrationContext.builder()
            .userId(userId)
            .conversationId(conversationId)
            .build();
    }
}
```

---

## 2. Update RAGOrchestrator

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    // Existing dependencies...
    
    // NEW: Optional chat session service
    private final Optional<ChatSessionService> chatSessionService;
    
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(context, "context must not be null");
        context.validate();

        String identifier = context.getIdentifier();  // userId or sessionId
        String conversationId = context.getConversationId();  // NEW: Conversation ID
        
        // NEW: Load conversation history if conversationId provided
        String processedQuery = query;
        if (context.hasConversation() && chatSessionService.isPresent()) {
            processedQuery = enrichWithConversationHistory(
                query, 
                conversationId, 
                identifier  // For access control
            );
        }
        
        // EXISTING: All current orchestration logic (unchanged)
        // Security checks, PII detection, intent extraction, etc.
        AISecurityResponse securityResponse = securityService.analyzeRequest(...);
        // ... all existing code ...
        
        OrchestrationResult result = handleIntent(...);
        
        // NEW: Record turn to conversation if conversationId provided
        if (context.hasConversation() && chatSessionService.isPresent()) {
            recordTurnToConversation(
                conversationId,
                identifier,  // Owner
                query,
                result
            );
        }
        
        return result;
    }
    
    /**
     * Enriches query with conversation history.
     * 
     * @param query Current user query
     * @param conversationId Conversation identifier
     * @param ownerId User/session who owns the conversation (for access control)
     */
    private String enrichWithConversationHistory(String query, String conversationId, String ownerId) {
        try {
            // Access control: Verify owner can access this conversation
            String history = chatSessionService.get()
                .getConversationContext(conversationId, ownerId);  // Pass owner for security check
            
            if (history != null && !history.isBlank()) {
                log.debug("Enriching query with conversation history: conversationId={}", conversationId);
                return String.format(
                    "Conversation History:\n%s\n\nCurrent Query: %s",
                    history,
                    query
                );
            }
        } catch (AccessDeniedException ex) {
            log.warn("Access denied: {} cannot access conversation {}", ownerId, conversationId);
            // Don't fail request, just don't enrich
        } catch (Exception ex) {
            log.warn("Failed to load conversation history: {}", ex.getMessage());
        }
        
        return query;  // Original if history unavailable
    }
    
    /**
     * Records turn to conversation.
     */
    private void recordTurnToConversation(String conversationId, String ownerId, 
                                          String query, OrchestrationResult result) {
        try {
            chatSessionService.get().recordTurn(
                conversationId,
                ownerId,
                query,
                result.getMessage()
            );
            log.debug("Recorded turn: conversationId={}, owner={}", conversationId, ownerId);
        } catch (Exception ex) {
            log.error("Failed to record turn: {}", ex.getMessage());
            // Don't fail request if recording fails
        }
    }
}
```

---

## 3. Chat Session Service with Access Control

```java
package com.ai.infrastructure.chat.service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean({ChatSessionStorageProvider.class, ChatSessionAccessControlPolicy.class})
public class ChatSessionServiceImpl implements ChatSessionService {
    
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
    
    private final ChatSessionStorageProvider storage;
    private final ChatSessionAccessControlPolicy accessPolicy;
    
    /**
     * Gets conversation context with access control.
     * 
     * @param conversationId The conversation identifier
     * @param requestingUser The user requesting access (userId or sessionId)
     * @return Formatted conversation history
     * @throws AccessDeniedException if user doesn't own this conversation
     */
    public String getConversationContext(String conversationId, String requestingUser) {
        // Load conversation
        Optional<ChatSession> session = storage.findById(conversationId);
        
        if (session.isEmpty()) {
            log.debug("Conversation not found: {}", conversationId);
            return "";  // New conversation
        }
        
        ChatSession conversation = session.get();
        
        // CRITICAL: Access control check
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
        
        // Access granted - return history
        return formatHistory(conversation.getTurns());
    }
    
    /**
     * Records a turn to conversation with access control.
     */
    public void recordTurn(String conversationId, String ownerId, String query, String response) {
        ChatSession conversation = storage.findById(conversationId)
            .orElseGet(() -> createNewConversation(conversationId, ownerId));
        
        // Verify ownership
        if (!canUserAccessConversation(ownerId, conversation)) {
            log.warn("Access denied: {} cannot record to conversation {}", ownerId, conversationId);
            throw new AccessDeniedException("Cannot record to this conversation");
        }
        
        ChatTurn turn = ChatTurn.builder()
            .userQuery(query)
            .aiResponse(response)
            .timestamp(LocalDateTime.now())
            .build();
        
        conversation.addTurn(turn);
        storage.save(conversation);
        
        log.debug("Turn recorded: conversationId={}, turnCount={}", 
            conversationId, conversation.getTurnCount());
    }
    
    private boolean canUserAccessConversation(String requestingUser, ChatSession conversation) {
        // Owner check
        if (conversation.getOwnerId().equals(requestingUser)) {
            return true;
        }
        
        // Delegate to access policy for additional logic (e.g., shared conversations)
        return accessPolicy.canUserAccessConversation(requestingUser, conversation.getId());
    }
    
    private ChatSession createNewConversation(String conversationId, String ownerId) {
        ChatSession session = ChatSession.builder()
            .id(conversationId)
            .ownerId(ownerId)  // Link to user/session
            .createdAt(LocalDateTime.now())
            .lastInteractionAt(LocalDateTime.now())
            .status(SessionStatus.ACTIVE)
            .turns(new ArrayList<>())
            .build();
        
        log.info("New conversation created: id={}, owner={}", conversationId, ownerId);
        return session;
    }
}
```

---

## 4. ChatSession Domain Model

```java
@Entity
@Table(name = "chat_sessions")
@Data
@Builder
public class ChatSession {

    @Id
    private String id;  // conversationId
    
    /**
     * Owner of this conversation (userId for authenticated, sessionId for anonymous).
     * CRITICAL for access control.
     */
    @Column(nullable = false)
    private String ownerId;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime lastInteractionAt;
    
    @Enumerated(EnumType.STRING)
    private SessionStatus status;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<ChatTurn> turns = new ArrayList<>();
    
    @Convert(converter = MetadataConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata = new HashMap<>();
}
```

---

## 5. User Provides Storage SPI

```java
package com.myapp.chat;

import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.spi.ChatSessionStorageProvider;

@Component
public class MyChatStorage implements ChatSessionStorageProvider {
    
    private final ChatSessionRepository repository;
    
    @Override
    public Optional<ChatSession> findById(String conversationId) {
        return repository.findById(conversationId);
    }
    
    @Override
    public ChatSession save(ChatSession session) {
        return repository.save(session);
    }
    
    @Override
    public List<ChatSession> findByOwnerId(String ownerId) {
        // Return all conversations for this user
        return repository.findByOwnerId(ownerId);
    }
}

// User's JPA Repository
@Repository
interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByOwnerId(String ownerId);
}
```

---

## Usage Examples:

### Example 1: Authenticated User, Multiple Conversations

```javascript
// Frontend - Project A Chat
const ProjectAChatPage = () => {
  const conversationId = "project-a-chat-" + projectId;
  
  const sendMessage = async (message) => {
    await fetch('/api/ai/chat', {
      method: 'POST',
      body: JSON.stringify({
        query: message,
        userId: currentUser.id,           // ← User ID
        conversationId: conversationId     // ← Conversation A
      })
    });
  };
};

// Frontend - Project B Chat  
const ProjectBChatPage = () => {
  const conversationId = "project-b-chat-" + projectId;
  
  const sendMessage = async (message) => {
    await fetch('/api/ai/chat', {
      method: 'POST',
      body: JSON.stringify({
        query: message,
        userId: currentUser.id,           // ← Same user
        conversationId: conversationId     // ← Different conversation B
      })
    });
  };
};
```

**Backend:**
```java
@PostMapping("/api/ai/chat")
public ResponseEntity<OrchestrationResult> chat(@RequestBody ChatRequest request) {
    OrchestrationContext context = OrchestrationContext.builder()
        .userId(request.getUserId())              // alice
        .conversationId(request.getConversationId())  // project-a-chat-123
        .build();
    
    OrchestrationResult result = orchestrator.orchestrate(request.getQuery(), context);
    return ResponseEntity.ok(result);
}
```

**What Happens:**
```
Query 1 (Project A):
  userId: "alice"
  conversationId: "project-a-chat-123"
  → Loads history from conv "project-a-chat-123"
  → Conversation linked to "alice"

Query 2 (Project B):
  userId: "alice"
  conversationId: "project-b-chat-456"
  → Loads DIFFERENT history from conv "project-b-chat-456"
  → Also linked to "alice"
  → No confusion with Project A chat
```

---

### Example 2: Anonymous User with Conversation

```javascript
// Frontend
const AnonymousChatPage = () => {
  const sessionId = localStorage.getItem('sessionId') || generateSessionId();
  const conversationId = useState(uuidv4());  // New conversation
  
  const sendMessage = async (message) => {
    await fetch('/api/ai/chat', {
      method: 'POST',
      body: JSON.stringify({
        query: message,
        sessionId: sessionId,              // ← Anonymous ID
        conversationId: conversationId      // ← Conversation ID
      })
    });
  };
};
```

**Backend:**
```java
OrchestrationContext context = OrchestrationContext.builder()
    .sessionId(request.getSessionId())          // anon-xyz
    .conversationId(request.getConversationId())  // conv-abc
    .build();
```

**Access Control:**
```
Conversation "conv-abc" is owned by "anon-xyz"
Only "anon-xyz" can access this conversation
If different anonymous user tries to access → DENIED
```

---

### Example 3: Single Query (No Conversation)

```javascript
// Frontend - Search feature
const SearchPage = () => {
  const search = async (query) => {
    await fetch('/api/ai/search', {
      method: 'POST',
      body: JSON.stringify({
        query: query,
        userId: currentUser.id
        // NO conversationId ← Single query
      })
    });
  };
};
```

**Backend:**
```java
OrchestrationContext context = OrchestrationContext.builder()
    .userId(request.getUserId())
    // No conversationId ← Not a conversation
    .build();
```

**What Happens:**
```
No conversationId provided
  → context.hasConversation() = false
  → No history loaded
  → No turn recorded
  → Works exactly as before (stateless)
```

---

## Data Model:

### ChatSession Table:
```sql
CREATE TABLE chat_sessions (
    id VARCHAR(36) PRIMARY KEY,              -- conversationId
    owner_id VARCHAR(100) NOT NULL,          -- userId or sessionId
    created_at TIMESTAMP NOT NULL,
    last_interaction_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    metadata TEXT,
    
    INDEX idx_owner (owner_id),              -- Find user's conversations
    INDEX idx_last_interaction (last_interaction_at)  -- Cleanup expired
);

CREATE TABLE chat_turns (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,         -- Foreign key to conversation
    user_query TEXT NOT NULL,
    ai_response TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
);
```

### Example Data:
```sql
-- Alice has 2 conversations
INSERT INTO chat_sessions VALUES 
  ('conv-001', 'alice', '2026-01-01', '2026-01-01', 'ACTIVE', '{}'),
  ('conv-002', 'alice', '2026-01-01', '2026-01-01', 'ACTIVE', '{}');

-- Bob has 1 conversation  
INSERT INTO chat_sessions VALUES 
  ('conv-003', 'bob', '2026-01-01', '2026-01-01', 'ACTIVE', '{}');

-- Anonymous user has 1 conversation
INSERT INTO chat_sessions VALUES 
  ('conv-004', 'anon-session-xyz', '2026-01-01', '2026-01-01', 'ACTIVE', '{}');
```

**Access Control:**
- Alice can access: conv-001, conv-002
- Bob can access: conv-003
- Alice CANNOT access conv-003 (Bob's)
- Bob CANNOT access conv-001, conv-002 (Alice's)

---

## Summary:

### Who Sets conversationId?

**Answer:** The **client** sets it (just like they set userId/sessionId)

**When:**
- First message in a conversation: Client generates `conversationId = uuidv4()`
- Subsequent messages: Client provides same `conversationId`
- Single queries: Client omits `conversationId`

### Can we have session-aware AND non-aware?

**Answer:** **YES!**

| Scenario | userId | sessionId | conversationId | Mode |
|----------|--------|-----------|----------------|------|
| **Auth user, single query** | alice | null | null | Non-aware ✅ |
| **Auth user, conversation** | alice | null | conv-001 | Session-aware ✅ |
| **Auth user, multiple convos** | alice | null | conv-001, conv-002 | Multiple sessions ✅ |
| **Anonymous, single query** | null | anon-x | null | Non-aware ✅ |
| **Anonymous, conversation** | null | anon-x | conv-abc | Session-aware ✅ |

### The Three Fields:

- **userId:** User identification (authenticated users)
- **sessionId:** Anonymous identification (required if no userId)
- **conversationId:** Conversation tracking (optional, enables chat)

**This design supports everything you need!** ✅

Would you like me to update the implementation guide with this correct three-field design?
