# JPA Repository Relationship Traversal Guide

## 🎯 Overview

Instead of parsing JSON metadata, we can use **JPA repositories** with proper entity relationships to traverse relationships efficiently. This is more type-safe, performant, and leverages database joins.

## 📊 Two Approaches Comparison

### Approach 1: Metadata-Based (Current)
- ✅ Works with existing design
- ✅ No entity changes needed
- ❌ Requires JSON parsing
- ❌ No type safety
- ❌ Slower (multiple queries)

### Approach 2: JPA Repository-Based (Recommended)
- ✅ Type-safe
- ✅ Single query with joins
- ✅ Database-optimized
- ✅ Leverages JPA relationships
- ⚠️ Requires entity relationships defined

---

## 🏗️ Step 1: Define Entity Relationships

### Example: Document Entity with JPA Relationships

```java
@Entity
@Table(name = "documents")
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    // JPA RELATIONSHIP: Document → User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;  // ← Direct relationship!
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    private Project project;
    
    @Enumerated(EnumType.STRING)
    private DocumentStatus status;
    
    // Getters and setters...
}

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String name;
    private String email;
    
    @Enumerated(EnumType.STRING)
    private UserStatus status;  // "active", "inactive"
    
    // REVERSE RELATIONSHIP: User → Documents
    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY)
    private List<Document> documents;
    
    // Getters and setters...
}

@Entity
@Table(name = "projects")
public class Project {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String name;
    private String category;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "id")
    private User owner;
    
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<Document> documents;
    
    // Getters and setters...
}
```

---

## 🔍 Step 2: Create JPA Repository Queries

### Repository with Relationship Queries

```java
@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    
    /**
     * Find documents created by active users
     * Uses JPA relationship traversal
     */
    @Query("SELECT d FROM Document d " +
           "JOIN d.createdBy u " +
           "WHERE u.status = :status")
    List<Document> findByCreatedByStatus(@Param("status") UserStatus status);
    
    /**
     * Find documents created by active users (using enum)
     */
    @Query("SELECT d FROM Document d " +
           "JOIN d.createdBy u " +
           "WHERE u.status = 'ACTIVE'")
    List<Document> findByActiveUsers();
    
    /**
     * Find documents by user ID and status
     */
    @Query("SELECT d FROM Document d " +
           "WHERE d.createdBy.id = :userId " +
           "AND d.createdBy.status = :userStatus")
    List<Document> findByUserIdAndUserStatus(
        @Param("userId") String userId,
        @Param("userStatus") UserStatus userStatus
    );
    
    /**
     * Multi-hop traversal: Documents from projects owned by user
     */
    @Query("SELECT d FROM Document d " +
           "JOIN d.project p " +
           "JOIN p.owner u " +
           "WHERE u.id = :userId " +
           "AND u.status = :userStatus")
    List<Document> findByProjectOwnerIdAndStatus(
        @Param("userId") String userId,
        @Param("userStatus") UserStatus userStatus
    );
    
    /**
     * Complex: Documents created by active users in AI projects
     */
    @Query("SELECT d FROM Document d " +
           "JOIN d.createdBy u " +
           "JOIN d.project p " +
           "WHERE u.status = 'ACTIVE' " +
           "AND p.category = :category")
    List<Document> findByActiveUsersInCategory(@Param("category") String category);
    
    /**
     * Using Spring Data method naming (automatic query generation)
     */
    List<Document> findByCreatedByStatus(UserStatus status);
    
    List<Document> findByCreatedByStatusAndProjectCategory(
        UserStatus status, 
        String category
    );
    
    /**
     * Count documents by user status
     */
    @Query("SELECT COUNT(d) FROM Document d " +
           "JOIN d.createdBy u " +
           "WHERE u.status = :status")
    long countByCreatedByStatus(@Param("status") UserStatus status);
}
```

---

## 🚀 Step 3: Service Using JPA Repositories

### Relationship Traversal Service

```java
@Service
@Transactional(readOnly = true)
public class JPARelationshipTraversalService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    /**
     * Find documents created by active users
     * Uses JPA relationship traversal - single query!
     */
    public List<Document> findDocumentsByActiveUsers() {
        // Single JPA query with JOIN
        return documentRepository.findByCreatedByStatus(UserStatus.ACTIVE);
        
        // Generated SQL:
        // SELECT d.* FROM documents d
        // JOIN users u ON d.created_by = u.id
        // WHERE u.status = 'ACTIVE'
    }
    
    /**
     * Find documents with multiple relationship filters
     */
    public List<Document> findDocumentsByActiveUsersInAICategory() {
        return documentRepository.findByActiveUsersInCategory("ai");
        
        // Generated SQL:
        // SELECT d.* FROM documents d
        // JOIN users u ON d.created_by = u.id
        // JOIN projects p ON d.project_id = p.id
        // WHERE u.status = 'ACTIVE'
        //   AND p.category = 'ai'
    }
    
    /**
     * Multi-hop traversal: Documents from projects owned by user
     */
    public List<Document> findDocumentsFromUserProjects(String userId) {
        return documentRepository.findByProjectOwnerIdAndStatus(
            userId, 
            UserStatus.ACTIVE
        );
        
        // Generated SQL:
        // SELECT d.* FROM documents d
        // JOIN projects p ON d.project_id = p.id
        // JOIN users u ON p.owner_id = u.id
        // WHERE u.id = ? AND u.status = ?
    }
    
    /**
     * Using Spring Data method naming
     */
    public List<Document> findByUserStatusAndCategory(UserStatus status, String category) {
        return documentRepository.findByCreatedByStatusAndProjectCategory(status, category);
    }
    
    /**
     * Count documents by relationship
     */
    public long countDocumentsByActiveUsers() {
        return documentRepository.countByCreatedByStatus(UserStatus.ACTIVE);
    }
}
```

---

## 🔄 Step 4: Integration with AI Infrastructure

### Hybrid Service: JPA + Vector Search

```java
@Service
public class HybridJPAVectorService {
    
    @Autowired
    private JPARelationshipTraversalService jpaService;
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    @Autowired
    private AIEmbeddingService embeddingService;
    
    /**
     * Find documents by active users, then rank by vector similarity
     */
    public List<DocumentDTO> findDocumentsByActiveUsersRankedBySimilarity(
            String query, int limit) {
        
        // Step 1: Use JPA to find documents by active users
        List<Document> documents = jpaService.findDocumentsByActiveUsers();
        // Returns: [Document{id: "doc-123"}, Document{id: "doc-999"}]
        
        // Step 2: Get their vector IDs from AISearchableEntity
        Map<String, String> documentToVectorId = new HashMap<>();
        for (Document doc : documents) {
            Optional<AISearchableEntity> entityOpt = 
                searchableEntityRepository.findByEntityTypeAndEntityId("document", doc.getId());
            
            if (entityOpt.isPresent() && entityOpt.get().getVectorId() != null) {
                documentToVectorId.put(doc.getId(), entityOpt.get().getVectorId());
            }
        }
        // documentToVectorId = {"doc-123": "vec-456", "doc-999": "vec-789"}
        
        // Step 3: Generate query embedding
        List<Double> queryVector = embeddingService.generateEmbedding(
            AIEmbeddingRequest.builder().text(query).build()
        ).getEmbedding();
        
        // Step 4: Compute similarity for each document
        List<DocumentScore> scoredDocs = new ArrayList<>();
        for (Map.Entry<String, String> entry : documentToVectorId.entrySet()) {
            String docId = entry.getKey();
            String vectorId = entry.getValue();
            
            Optional<VectorRecord> vectorOpt = vectorDatabaseService.getVector(vectorId);
            if (vectorOpt.isPresent()) {
                double similarity = computeCosineSimilarity(
                    queryVector, 
                    vectorOpt.get().getEmbedding()
                );
                
                scoredDocs.add(new DocumentScore(docId, similarity));
            }
        }
        
        // Step 5: Sort by similarity and limit
        scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> topDocIds = scoredDocs.stream()
            .limit(limit)
            .map(d -> d.docId)
            .collect(Collectors.toList());
        
        // Step 6: Fetch full documents
        return documentRepository.findAllById(topDocIds).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    private double computeCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        // Implementation...
    }
    
    private static class DocumentScore {
        String docId;
        double score;
        DocumentScore(String docId, double score) {
            this.docId = docId;
            this.score = score;
        }
    }
}
```

---

## 🎯 Step 5: Complete Example: Relationship-Aware RAG with JPA

```java
@Service
public class JPARelationshipRAGService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    @Autowired
    private AIEmbeddingService embeddingService;
    
    /**
     * Find documents created by active users, ranked by semantic similarity
     */
    public RAGResponse findDocumentsByActiveUsers(String query, int limit) {
        
        // ============================================================
        // STEP 1: Use JPA to filter by relationship
        // ============================================================
        List<Document> documents = documentRepository.findByCreatedByStatus(UserStatus.ACTIVE);
        // Single SQL query with JOIN - efficient!
        
        if (documents.isEmpty()) {
            return createEmptyResponse();
        }
        
        // Extract document IDs
        List<String> documentIds = documents.stream()
            .map(Document::getId)
            .collect(Collectors.toList());
        // documentIds = ["doc-123", "doc-999"]
        
        // ============================================================
        // STEP 2: Get vector IDs for these documents
        // ============================================================
        Map<String, String> docIdToVectorId = new HashMap<>();
        for (String docId : documentIds) {
            Optional<AISearchableEntity> entityOpt = 
                searchableEntityRepository.findByEntityTypeAndEntityId("document", docId);
            
            if (entityOpt.isPresent() && entityOpt.get().getVectorId() != null) {
                docIdToVectorId.put(docId, entityOpt.get().getVectorId());
            }
        }
        
        // ============================================================
        // STEP 3: Generate query embedding
        // ============================================================
        List<Double> queryVector = embeddingService.generateEmbedding(
            AIEmbeddingRequest.builder().text(query).build()
        ).getEmbedding();
        
        // ============================================================
        // STEP 4: Compute similarity and rank
        // ============================================================
        List<RAGResponse.RAGDocument> rankedDocs = new ArrayList<>();
        
        for (String docId : documentIds) {
            String vectorId = docIdToVectorId.get(docId);
            if (vectorId == null) continue;
            
            Optional<VectorRecord> vectorOpt = vectorDatabaseService.getVector(vectorId);
            if (vectorOpt.isPresent()) {
                double similarity = computeCosineSimilarity(
                    queryVector,
                    vectorOpt.get().getEmbedding()
                );
                
                // Get document details
                Document doc = documents.stream()
                    .filter(d -> d.getId().equals(docId))
                    .findFirst()
                    .orElse(null);
                
                if (doc != null) {
                    RAGResponse.RAGDocument ragDoc = RAGResponse.RAGDocument.builder()
                        .id(docId)
                        .content(doc.getContent())
                        .title(doc.getTitle())
                        .similarity(similarity)
                        .score(similarity)
                        .metadata(Map.of(
                            "createdBy", doc.getCreatedBy().getId(),
                            "createdByStatus", doc.getCreatedBy().getStatus().name(),
                            "projectId", doc.getProject() != null ? doc.getProject().getId() : null
                        ))
                        .build();
                    
                    rankedDocs.add(ragDoc);
                }
            }
        }
        
        // Sort by similarity
        rankedDocs.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        // Limit results
        List<RAGResponse.RAGDocument> limitedDocs = rankedDocs.stream()
            .limit(limit)
            .collect(Collectors.toList());
        
        return RAGResponse.builder()
            .documents(limitedDocs)
            .totalDocuments(rankedDocs.size())
            .usedDocuments(limitedDocs.size())
            .success(true)
            .build();
    }
}
```

---

## 📊 Performance Comparison

### Metadata-Based Approach
```java
// Query 1: Get all documents
List<AISearchableEntity> docs = repository.findByEntityType("document");
// SQL: SELECT * FROM ai_searchable_entities WHERE entity_type = 'document'

// In-memory: Parse JSON for each document
for (AISearchableEntity doc : docs) {
    Map<String, Object> metadata = parseMetadata(doc.getMetadata());
    String userId = (String) metadata.get("createdBy");
    // ...
}

// Query 2: Get active users
List<User> users = userRepository.findByIdInAndStatus(userIds, "active");
// SQL: SELECT * FROM users WHERE id IN (...) AND status = 'active'

// In-memory: Match documents to users
// ...
```
**Total: 2 database queries + in-memory processing**

### JPA Repository Approach
```java
// Single query with JOIN
List<Document> docs = documentRepository.findByCreatedByStatus(UserStatus.ACTIVE);
// SQL: SELECT d.* FROM documents d 
//      JOIN users u ON d.created_by = u.id 
//      WHERE u.status = 'ACTIVE'
```
**Total: 1 database query, database does the join**

---

## 🎯 Advanced JPA Query Examples

### Example 1: Multi-Hop Traversal
```java
// Find documents from projects owned by active users
@Query("SELECT d FROM Document d " +
       "JOIN d.project p " +
       "JOIN p.owner u " +
       "WHERE u.status = 'ACTIVE'")
List<Document> findDocumentsFromActiveUserProjects();
```

### Example 2: Complex Filters
```java
// Documents created by active users, in AI projects, published this month
@Query("SELECT d FROM Document d " +
       "JOIN d.createdBy u " +
       "JOIN d.project p " +
       "WHERE u.status = 'ACTIVE' " +
       "AND p.category = 'ai' " +
       "AND d.status = 'PUBLISHED' " +
       "AND d.createdAt >= :startDate")
List<Document> findActiveUserAIDocumentsPublishedSince(@Param("startDate") LocalDateTime startDate);
```

### Example 3: Aggregations
```java
// Count documents per user status
@Query("SELECT u.status, COUNT(d) " +
       "FROM Document d " +
       "JOIN d.createdBy u " +
       "GROUP BY u.status")
List<Object[]> countDocumentsByUserStatus();
```

### Example 4: Pagination
```java
// Paginated query with relationships
@Query("SELECT d FROM Document d " +
       "JOIN d.createdBy u " +
       "WHERE u.status = 'ACTIVE'")
Page<Document> findByActiveUsers(Pageable pageable);
```

---

## 🔧 Integration Pattern

### Pattern: JPA for Filtering, Vector for Ranking

```java
@Service
public class HybridRelationshipService {
    
    /**
     * 1. Use JPA to filter by relationships (fast, type-safe)
     * 2. Use vector search to rank by similarity (semantic)
     */
    public RAGResponse search(String query, RelationshipQueryPlan plan) {
        
        // Step 1: JPA filtering
        List<Document> filteredDocs = applyJPAFilters(plan);
        // Uses database joins - efficient!
        
        // Step 2: Vector ranking
        List<Document> rankedDocs = rankByVectorSimilarity(filteredDocs, query);
        // Uses embeddings - semantic!
        
        return buildRAGResponse(rankedDocs);
    }
    
    private List<Document> applyJPAFilters(RelationshipQueryPlan plan) {
        // Use JPA queries based on relationship paths
        if (plan.getRelationshipPaths() != null) {
            for (RelationshipPath path : plan.getRelationshipPaths()) {
                if ("createdBy".equals(path.getRelationshipType())) {
                    return documentRepository.findByCreatedByStatus(UserStatus.ACTIVE);
                }
                // ... handle other paths
            }
        }
        return Collections.emptyList();
    }
}
```

---

## ✅ Benefits of JPA Approach

1. **Type Safety**: Compile-time checking
2. **Performance**: Database-optimized joins
3. **Single Query**: No multiple round-trips
4. **Leverages Database**: Uses indexes, query optimization
5. **Clean Code**: No JSON parsing, no manual matching
6. **Pagination**: Built-in support
7. **Caching**: JPA/Hibernate caching works

---

## ⚠️ When to Use Each Approach

### Use JPA Repositories When:
- ✅ Entities have proper JPA relationships defined
- ✅ You need type safety
- ✅ Performance is critical
- ✅ Complex queries with multiple joins
- ✅ You want database-level optimization

### Use Metadata Approach When:
- ✅ Entities don't have JPA relationships
- ✅ Dynamic relationships
- ✅ Legacy system without entity changes
- ✅ Simple one-hop relationships
- ✅ Quick prototyping

---

## 🚀 Recommended Hybrid Approach

**Best of Both Worlds:**

1. **Use JPA for relationship filtering** (fast, type-safe)
2. **Use vector search for semantic ranking** (intelligent)
3. **Store both**: Keep metadata for flexibility, use JPA for queries

```java
// JPA filters relationships
List<Document> docs = documentRepository.findByCreatedByStatus(ACTIVE);

// Vector ranks by similarity
rankByVectorSimilarity(docs, query);

// Metadata provides flexibility
metadata.put("createdBy", user.getId());
metadata.put("createdByStatus", user.getStatus()); // Denormalized for quick access
```

This gives you **performance** (JPA) + **intelligence** (vector) + **flexibility** (metadata)!
