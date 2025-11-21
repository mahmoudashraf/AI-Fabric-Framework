# Deep Dive: Finding Documents by Active Users

## 🎯 Query Goal
**Find documents where metadata contains `createdBy` pointing to active users**

## 📊 Complete Flow Breakdown

### Phase 1: Data Storage (When Indexing Documents)

#### Step 1.1: Document Entity Structure
```java
// Your domain entity (e.g., Document.java)
public class Document {
    private String id;           // "doc-123"
    private String title;        // "AI Research Paper"
    private String content;      // "Machine learning is..."
    private String createdBy;    // "user-456" ← This is the relationship!
    private String status;       // "published"
    // ... other fields
}
```

#### Step 1.2: Metadata Extraction (AICapabilityService)
```java
// When document is indexed via @AICapable annotation
@AICapable(entityType = "document")
public void saveDocument(Document doc) {
    // ...
    aiCapabilityService.processEntityForAI(doc, "document");
}
```

**Inside `AICapabilityService.extractMetadata()`:**
```java
private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    
    // Loop through configured metadata fields
    for (AIMetadataField field : config.getMetadataFields()) {
        // field.getName() = "createdBy"
        String value = getFieldValue(entity, field.getName());
        // value = "user-456" (extracted from doc.getCreatedBy())
        
        if (value != null && !value.trim().isEmpty()) {
            metadata.put(field.getName(), value);
            // metadata.put("createdBy", "user-456")
        }
    }
    
    return metadata;
    // Returns: {"createdBy": "user-456", "status": "published", ...}
}
```

#### Step 1.3: Metadata Serialization
```java
// In storeSearchableEntity()
String metadataJson = MetadataJsonSerializer.serialize(metadata, config);
// Converts Map to JSON string:
// {"createdBy":"user-456","status":"published"}
```

#### Step 1.4: Database Storage
```sql
-- Insert into ai_searchable_entities table
INSERT INTO ai_searchable_entities (
    id,
    entity_type,        -- "document"
    entity_id,          -- "doc-123"
    searchable_content, -- "AI Research Paper Machine learning is..."
    metadata,           -- '{"createdBy":"user-456","status":"published"}'
    vector_id,          -- "vec-789"
    created_at,
    updated_at
) VALUES (...);
```

**Result in Database:**
```
┌─────────────┬──────────────┬─────────────┬──────────────────────────────┐
│ entity_type │ entity_id    │ metadata    │ searchable_content           │
├─────────────┼──────────────┼─────────────┼──────────────────────────────┤
│ document    │ doc-123      │ {"createdBy"│ "AI Research Paper..."       │
│             │              │ :"user-456",│                              │
│             │              │ "status":   │                              │
│             │              │ "published"}│                              │
├─────────────┼──────────────┼─────────────┼──────────────────────────────┤
│ document    │ doc-789      │ {"createdBy"│ "Deep Learning Guide..."     │
│             │              │ :"user-123",│                              │
│             │              │ "status":   │                              │
│             │              │ "draft"}     │                              │
└─────────────┴──────────────┴─────────────┴──────────────────────────────┘
```

---

### Phase 2: Query Execution (Finding Documents)

#### Step 2.1: User Query
```java
// User asks: "Find documents created by active users"
RAGRequest request = RAGRequest.builder()
    .query("Find documents created by active users")
    .entityType("document")
    .limit(10)
    .build();
```

#### Step 2.2: LLM Query Planning
```java
// RelationshipQueryPlanner analyzes query
RelationshipQueryPlan plan = queryPlanner.planQuery(
    "Find documents created by active users",
    Arrays.asList("document", "user")
);

// LLM generates plan:
{
  "semanticQuery": "documents",
  "primaryEntityType": "document",
  "relationshipPaths": [{
    "fromEntityType": "document",
    "relationshipType": "createdBy",
    "toEntityType": "user",
    "direction": "REVERSE",
    "conditions": {"status": "active"}
  }],
  "strategy": "RELATIONSHIP_TRAVERSAL",
  "relationshipFilters": {"user.status": "active"}
}
```

#### Step 2.3: Relationship Traversal Execution

**Inside `RelationshipTraversalService.traverseRelationships()`:**

```java
public List<String> traverseRelationships(RelationshipQueryPlan plan) {
    List<String> resultEntityIds = new ArrayList<>();
    
    // For each relationship path
    for (RelationshipPath path : plan.getRelationshipPaths()) {
        // path: document --[createdBy]--> user
        
        // Step 2.3.1: Find all documents
        List<String> pathResults = traversePath(path, plan);
        resultEntityIds.addAll(pathResults);
    }
    
    // Step 2.3.2: Apply relationship filters
    if (plan.getRelationshipFilters() != null) {
        resultEntityIds = applyRelationshipFilters(resultEntityIds, plan);
    }
    
    return resultEntityIds;
}
```

#### Step 2.3.1: Traverse Path (Find Documents with createdBy)

```java
private List<String> traversePath(RelationshipPath path, RelationshipQueryPlan plan) {
    String fromType = "document";
    String toType = "user";
    String relationshipType = "createdBy";
    
    // Call: findEntitiesByRelationshipMetadata()
    return findEntitiesByRelationshipMetadata(fromType, toType, relationshipType, conditions);
}
```

**Inside `findEntitiesByRelationshipMetadata()`:**

```java
private List<String> findEntitiesByRelationshipMetadata(
    String fromType,      // "document"
    String toType,        // "user"
    String relationshipType, // "createdBy"
    Map<String, Object> conditions // {"status": "active"}
) {
    // Step 1: Get ALL documents from database
    List<AISearchableEntity> candidates = 
        searchableEntityRepository.findByEntityType("document");
    
    // SQL executed:
    // SELECT * FROM ai_searchable_entities WHERE entity_type = 'document'
    
    // Returns:
    // [
    //   {id: "1", entity_id: "doc-123", metadata: '{"createdBy":"user-456","status":"published"}'},
    //   {id: "2", entity_id: "doc-789", metadata: '{"createdBy":"user-123","status":"draft"}'},
    //   {id: "3", entity_id: "doc-999", metadata: '{"createdBy":"user-456","status":"published"}'}
    // ]
    
    List<String> matchingIds = new ArrayList<>();
    
    // Step 2: Filter each document by metadata
    for (AISearchableEntity entity : candidates) {
        // Check if metadata contains "createdBy" field
        if (matchesRelationshipMetadata(entity, relationshipType, conditions)) {
            matchingIds.add(entity.getEntityId());
            // Adds: "doc-123", "doc-789", "doc-999" (all have createdBy)
        }
    }
    
    return matchingIds;
    // Returns: ["doc-123", "doc-789", "doc-999"]
}
```

#### Step 2.3.2: Check Metadata Match

**Inside `matchesRelationshipMetadata()`:**

```java
private boolean matchesRelationshipMetadata(
    AISearchableEntity entity,
    String relationshipType,  // "createdBy"
    Map<String, Object> conditions // {"status": "active"}
) {
    // Step 1: Parse JSON metadata string
    String metadataJson = entity.getMetadata();
    // metadataJson = '{"createdBy":"user-456","status":"published"}'
    
    Map<String, Object> metadata = parseMetadata(metadataJson);
    // metadata = {"createdBy": "user-456", "status": "published"}
    
    // Step 2: Check if relationship field exists
    Object relationshipValue = metadata.get(relationshipType);
    // relationshipValue = "user-456"
    
    if (relationshipValue == null) {
        return false; // No createdBy field
    }
    
    // Step 3: Apply conditions (if any)
    if (conditions != null && !conditions.isEmpty()) {
        // conditions = {"status": "active"}
        // But wait! This is checking document status, not user status!
        // We need to check USER status, not document status.
        // This is the CHALLENGE!
    }
    
    return true; // Has createdBy field
}
```

---

### Phase 3: The Challenge - Checking User Status

#### Problem: Metadata Only Contains User ID, Not User Status

**Current Metadata:**
```json
{
  "createdBy": "user-456",  // ← Only user ID
  "status": "published"      // ← Document status, not user status!
}
```

**We Need:**
- User status: "active" or "inactive"
- But metadata only has user ID: "user-456"

#### Solution Options:

### Option A: Store User Status in Document Metadata (Denormalization)

**When indexing document, also store user status:**

```java
// In extractMetadata() - enhanced version
private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    
    // Extract createdBy
    String createdBy = getFieldValue(entity, "createdBy");
    metadata.put("createdBy", createdBy);
    
    // NEW: Fetch user status and store it
    if (createdBy != null) {
        User user = userRepository.findById(createdBy);
        if (user != null) {
            metadata.put("createdBy", createdBy);
            metadata.put("createdByUserStatus", user.getStatus()); // ← Store user status!
            // Now metadata = {"createdBy": "user-456", "createdByUserStatus": "active"}
        }
    }
    
    return metadata;
}
```

**Updated Database:**
```sql
-- Metadata now includes user status
metadata = '{"createdBy":"user-456","createdByUserStatus":"active","status":"published"}'
```

**Query Filter:**
```java
// Now we can filter directly
if (matchesRelationshipMetadata(entity, "createdBy", conditions)) {
    Map<String, Object> metadata = parseMetadata(entity.getMetadata());
    
    // Check user status from metadata
    String userStatus = (String) metadata.get("createdByUserStatus");
    if ("active".equals(userStatus)) {
        return true; // User is active!
    }
}
```

### Option B: Join with User Table (Relational Query)

**Query user status from separate user table:**

```java
private List<String> findDocumentsByActiveUsers(RelationshipQueryPlan plan) {
    // Step 1: Get all documents with createdBy
    List<AISearchableEntity> documents = 
        searchableEntityRepository.findByEntityType("document");
    
    // Step 2: Extract user IDs from metadata
    Set<String> userIds = new HashSet<>();
    Map<String, List<String>> userToDocuments = new HashMap<>();
    
    for (AISearchableEntity doc : documents) {
        Map<String, Object> metadata = parseMetadata(doc.getMetadata());
        String userId = (String) metadata.get("createdBy");
        
        if (userId != null) {
            userIds.add(userId);
            userToDocuments.computeIfAbsent(userId, k -> new ArrayList<>())
                          .add(doc.getEntityId());
        }
    }
    // userIds = ["user-456", "user-123"]
    
    // Step 3: Query user table for active users
    List<User> activeUsers = userRepository.findByStatus("active");
    // SQL: SELECT * FROM users WHERE status = 'active'
    // Returns: [User{id="user-456", status="active"}, User{id="user-999", status="active"}]
    
    Set<String> activeUserIds = activeUsers.stream()
        .map(User::getId)
        .collect(Collectors.toSet());
    // activeUserIds = ["user-456", "user-999"]
    
    // Step 4: Find documents created by active users
    List<String> resultDocumentIds = new ArrayList<>();
    for (String userId : activeUserIds) {
        List<String> docIds = userToDocuments.get(userId);
        if (docIds != null) {
            resultDocumentIds.addAll(docIds);
        }
    }
    // resultDocumentIds = ["doc-123", "doc-999"] (documents by user-456)
    
    return resultDocumentIds;
}
```

### Option C: Use JSON Query (PostgreSQL/MySQL JSON Support)

**If database supports JSON queries:**

```java
@Query("SELECT e FROM AISearchableEntity e " +
       "WHERE e.entityType = 'document' " +
       "AND JSON_EXTRACT(e.metadata, '$.createdBy') IS NOT NULL")
List<AISearchableEntity> findDocumentsWithCreatedBy();

// Then filter by joining with user table
@Query("SELECT e FROM AISearchableEntity e " +
       "JOIN User u ON JSON_EXTRACT(e.metadata, '$.createdBy') = u.id " +
       "WHERE e.entityType = 'document' AND u.status = 'active'")
List<AISearchableEntity> findDocumentsByActiveUsers();
```

---

## 🔍 Complete Implementation Flow

### Implementation: Option B (Join with User Table)

```java
@Service
public class RelationshipTraversalService {
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private UserRepository userRepository; // Your domain repository
    
    public List<String> findDocumentsByActiveUsers() {
        // Step 1: Get all documents
        List<AISearchableEntity> allDocuments = 
            searchableEntityRepository.findByEntityType("document");
        
        // Step 2: Extract user IDs from metadata
        Map<String, String> documentToUser = new HashMap<>();
        Set<String> userIds = new HashSet<>();
        
        for (AISearchableEntity doc : allDocuments) {
            Map<String, Object> metadata = parseMetadata(doc.getMetadata());
            String userId = (String) metadata.get("createdBy");
            
            if (userId != null) {
                documentToUser.put(doc.getEntityId(), userId);
                userIds.add(userId);
            }
        }
        // documentToUser = {"doc-123": "user-456", "doc-789": "user-123"}
        // userIds = ["user-456", "user-123"]
        
        // Step 3: Query active users
        List<User> activeUsers = userRepository.findByStatus("active");
        Set<String> activeUserIds = activeUsers.stream()
            .map(User::getId)
            .collect(Collectors.toSet());
        // activeUserIds = ["user-456"] (assuming user-123 is inactive)
        
        // Step 4: Find documents created by active users
        List<String> resultIds = documentToUser.entrySet().stream()
            .filter(entry -> activeUserIds.contains(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        // resultIds = ["doc-123"] (only doc-123 is by active user)
        
        return resultIds;
    }
    
    private Map<String, Object> parseMetadata(String metadataJson) {
        // Parse JSON string to Map
        // Implementation using Jackson ObjectMapper or simple parser
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
```

---

## 📊 Database Query Flow

### Query 1: Get All Documents
```sql
SELECT id, entity_id, metadata 
FROM ai_searchable_entities 
WHERE entity_type = 'document';
```

**Result:**
```
┌────┬─────────────┬────────────────────────────────────────────┐
│ id │ entity_id   │ metadata                                    │
├────┼─────────────┼────────────────────────────────────────────┤
│ 1  │ doc-123     │ {"createdBy":"user-456","status":"published"}│
│ 2  │ doc-789     │ {"createdBy":"user-123","status":"draft"}  │
│ 3  │ doc-999     │ {"createdBy":"user-456","status":"published"}│
└────┴─────────────┴────────────────────────────────────────────┘
```

### Query 2: Parse Metadata (In-Memory)
```java
// For each row, parse JSON:
doc-123: {"createdBy": "user-456"} → userId = "user-456"
doc-789: {"createdBy": "user-123"} → userId = "user-123"
doc-999: {"createdBy": "user-456"} → userId = "user-456"

// Collect unique user IDs:
userIds = ["user-456", "user-123"]
```

### Query 3: Get Active Users
```sql
SELECT id, status 
FROM users 
WHERE id IN ('user-456', 'user-123') 
AND status = 'active';
```

**Result:**
```
┌──────────┬────────┐
│ id       │ status │
├──────────┼────────┤
│ user-456 │ active │
└──────────┴────────┘
```

### Query 4: Match Documents to Active Users
```java
// In-memory matching:
doc-123 → createdBy = "user-456" → user-456 is active ✅ → Include
doc-789 → createdBy = "user-123" → user-123 is NOT active ❌ → Exclude
doc-999 → createdBy = "user-456" → user-456 is active ✅ → Include

// Final result:
resultDocumentIds = ["doc-123", "doc-999"]
```

---

## 🎯 Performance Considerations

### Current Approach (Option B)
- **Query 1**: Get all documents (could be thousands)
- **In-Memory**: Parse JSON for each document
- **Query 2**: Get active users (small set)
- **In-Memory**: Match documents to users

**Performance:** O(n) where n = number of documents

### Optimization: Database-Level JSON Query

**If using PostgreSQL with JSONB:**

```sql
-- Single query with JSON extraction and join
SELECT DISTINCT e.entity_id
FROM ai_searchable_entities e
JOIN users u ON (e.metadata::jsonb->>'createdBy') = u.id
WHERE e.entity_type = 'document'
  AND u.status = 'active';
```

**Performance:** O(log n) with proper indexes

### Optimization: Denormalization (Option A)

**Store user status in document metadata:**
- **Pro**: Single query, no joins needed
- **Con**: Data duplication, needs updates when user status changes

```sql
-- Single query with JSON filter
SELECT entity_id
FROM ai_searchable_entities
WHERE entity_type = 'document'
  AND (metadata::jsonb->>'createdByUserStatus') = 'active';
```

---

## 🔧 Complete Code Example

```java
@Service
public class DocumentRelationshipService {
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Find documents created by active users
     */
    public List<String> findDocumentsByActiveUsers() {
        // Step 1: Get all documents
        List<AISearchableEntity> documents = 
            searchableEntityRepository.findByEntityType("document");
        
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Step 2: Extract user IDs from metadata
        Map<String, List<String>> userToDocuments = new HashMap<>();
        Set<String> userIds = new HashSet<>();
        
        for (AISearchableEntity doc : documents) {
            Map<String, Object> metadata = parseMetadata(doc.getMetadata());
            String userId = (String) metadata.get("createdBy");
            
            if (userId != null && !userId.isEmpty()) {
                userIds.add(userId);
                userToDocuments.computeIfAbsent(userId, k -> new ArrayList<>())
                              .add(doc.getEntityId());
            }
        }
        
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Step 3: Get active users
        List<User> activeUsers = userRepository.findByIdInAndStatus(
            new ArrayList<>(userIds), 
            "active"
        );
        
        Set<String> activeUserIds = activeUsers.stream()
            .map(User::getId)
            .collect(Collectors.toSet());
        
        // Step 4: Collect documents by active users
        List<String> resultDocumentIds = new ArrayList<>();
        for (String userId : activeUserIds) {
            List<String> docIds = userToDocuments.get(userId);
            if (docIds != null) {
                resultDocumentIds.addAll(docIds);
            }
        }
        
        return resultDocumentIds;
    }
    
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty() || 
            "{}".equals(metadataJson.trim())) {
            return Collections.emptyMap();
        }
        
        try {
            return objectMapper.readValue(metadataJson, 
                new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", metadataJson, e);
            return Collections.emptyMap();
        }
    }
}
```

---

## 📝 Summary

**The Complete Flow:**

1. **Storage**: Document metadata stores `createdBy: "user-456"` as JSON string
2. **Query**: Get all documents, parse metadata JSON
3. **Extract**: Collect all user IDs from `createdBy` fields
4. **Filter**: Query user table for active users
5. **Match**: Find documents where `createdBy` matches active user IDs
6. **Return**: List of document IDs created by active users

**Key Points:**
- Metadata is stored as JSON string in database
- Must parse JSON to extract relationship values
- Need to join with user table to check user status
- Can optimize with database JSON queries or denormalization
