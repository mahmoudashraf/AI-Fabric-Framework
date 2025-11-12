/**
 * Complete Implementation Example: Finding Documents by Active Users
 * 
 * This shows the EXACT code flow from start to finish.
 */

// ============================================================================
// STEP 1: STORAGE - When Document is Created/Indexed
// ============================================================================

// Your domain entity
public class Document {
    private String id;           // "doc-123"
    private String title;         // "AI Research Paper"
    private String content;       // "Machine learning is..."
    private String createdBy;     // "user-456" ← RELATIONSHIP FIELD
    private String status;        // "published"
}

// When document is saved and indexed
@AICapable(entityType = "document")
public void saveDocument(Document doc) {
    documentRepository.save(doc);
    // Automatically triggers: aiCapabilityService.processEntityForAI(doc, "document")
}

// Inside AICapabilityService.extractMetadata()
private Map<String, Object> extractMetadata(Object entity, AIEntityConfig config) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    
    // Config defines: metadataFields = [AIMetadataField(name="createdBy", ...)]
    for (AIMetadataField field : config.getMetadataFields()) {
        // field.getName() = "createdBy"
        String value = getFieldValue(entity, field.getName());
        // Uses reflection: doc.getClass().getDeclaredField("createdBy").get(doc)
        // Returns: "user-456"
        
        if (value != null && !value.trim().isEmpty()) {
            metadata.put(field.getName(), value);
            // metadata = {"createdBy": "user-456"}
        }
    }
    
    return metadata;
    // Returns: {"createdBy": "user-456", "status": "published"}
}

// Inside storeSearchableEntity()
String metadataJson = MetadataJsonSerializer.serialize(metadata, config);
// Converts Map to JSON string:
// Result: '{"createdBy":"user-456","status":"published"}'

// Database INSERT
searchableEntityRepository.save(AISearchableEntity.builder()
    .entityType("document")
    .entityId("doc-123")
    .metadata(metadataJson)  // '{"createdBy":"user-456","status":"published"}'
    .searchableContent("AI Research Paper Machine learning is...")
    .vectorId("vec-789")
    .build());

// SQL executed:
// INSERT INTO ai_searchable_entities (entity_type, entity_id, metadata, ...)
// VALUES ('document', 'doc-123', '{"createdBy":"user-456","status":"published"}', ...);


// ============================================================================
// STEP 2: QUERY - Finding Documents by Active Users
// ============================================================================

@Service
public class DocumentRelationshipQueryService {
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private UserRepository userRepository; // Your domain repository
    
    @Autowired
    private ObjectMapper objectMapper; // Jackson ObjectMapper
    
    /**
     * Find documents created by active users
     * 
     * FLOW:
     * 1. Get all documents from ai_searchable_entities
     * 2. Parse metadata JSON to extract createdBy user IDs
     * 3. Query user table for active users
     * 4. Match documents to active user IDs
     * 5. Return document IDs
     */
    public List<String> findDocumentsByActiveUsers() {
        
        // ====================================================================
        // STEP 2.1: Get All Documents
        // ====================================================================
        List<AISearchableEntity> documents = 
            searchableEntityRepository.findByEntityType("document");
        
        // SQL executed:
        // SELECT * FROM ai_searchable_entities WHERE entity_type = 'document'
        
        // Result:
        // [
        //   {id: "1", entity_id: "doc-123", metadata: '{"createdBy":"user-456","status":"published"}'},
        //   {id: "2", entity_id: "doc-789", metadata: '{"createdBy":"user-123","status":"draft"}'},
        //   {id: "3", entity_id: "doc-999", metadata: '{"createdBy":"user-456","status":"published"}'}
        // ]
        
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }
        
        // ====================================================================
        // STEP 2.2: Parse Metadata and Extract User IDs
        // ====================================================================
        // Map: userId → List of document IDs created by that user
        Map<String, List<String>> userToDocuments = new HashMap<>();
        Set<String> userIds = new HashSet<>();
        
        for (AISearchableEntity doc : documents) {
            // Parse JSON string to Map
            Map<String, Object> metadata = parseMetadata(doc.getMetadata());
            // metadata = {"createdBy": "user-456", "status": "published"}
            
            // Extract createdBy field
            String userId = (String) metadata.get("createdBy");
            // userId = "user-456"
            
            if (userId != null && !userId.isEmpty()) {
                // Collect unique user IDs
                userIds.add(userId);
                // userIds = ["user-456", "user-123"]
                
                // Map user to documents
                userToDocuments.computeIfAbsent(userId, k -> new ArrayList<>())
                              .add(doc.getEntityId());
                // userToDocuments = {
                //   "user-456": ["doc-123", "doc-999"],
                //   "user-123": ["doc-789"]
                // }
            }
        }
        
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // ====================================================================
        // STEP 2.3: Query Active Users
        // ====================================================================
        // Query user table for users that are active
        List<User> activeUsers = userRepository.findByIdInAndStatus(
            new ArrayList<>(userIds),  // ["user-456", "user-123"]
            "active"
        );
        
        // SQL executed:
        // SELECT * FROM users 
        // WHERE id IN ('user-456', 'user-123') 
        // AND status = 'active'
        
        // Result (assuming user-456 is active, user-123 is inactive):
        // [
        //   User{id: "user-456", status: "active", name: "John Doe"}
        // ]
        
        // Extract active user IDs
        Set<String> activeUserIds = activeUsers.stream()
            .map(User::getId)
            .collect(Collectors.toSet());
        // activeUserIds = ["user-456"]
        
        // ====================================================================
        // STEP 2.4: Match Documents to Active Users
        // ====================================================================
        List<String> resultDocumentIds = new ArrayList<>();
        
        for (String userId : activeUserIds) {
            // userId = "user-456"
            List<String> docIds = userToDocuments.get(userId);
            // docIds = ["doc-123", "doc-999"]
            
            if (docIds != null) {
                resultDocumentIds.addAll(docIds);
            }
        }
        
        // resultDocumentIds = ["doc-123", "doc-999"]
        
        return resultDocumentIds;
    }
    
    /**
     * Parse JSON metadata string to Map
     */
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty() || 
            "{}".equals(metadataJson.trim())) {
            return Collections.emptyMap();
        }
        
        try {
            // Parse JSON string: '{"createdBy":"user-456","status":"published"}'
            // To Map: {"createdBy": "user-456", "status": "published"}
            return objectMapper.readValue(metadataJson, 
                new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", metadataJson, e);
            return Collections.emptyMap();
        }
    }
}


// ============================================================================
// STEP 3: ALTERNATIVE - Using Database JSON Queries (PostgreSQL)
// ============================================================================

@Repository
public interface AISearchableEntityRepository extends JpaRepository<AISearchableEntity, String> {
    
    /**
     * Find documents created by active users using JSON query
     * 
     * Requires PostgreSQL with JSONB support
     */
    @Query(value = """
        SELECT DISTINCT e.entity_id
        FROM ai_searchable_entities e
        JOIN users u ON (e.metadata::jsonb->>'createdBy') = u.id
        WHERE e.entity_type = 'document'
          AND u.status = 'active'
        """, nativeQuery = true)
    List<String> findDocumentsByActiveUsers();
    
    /**
     * Alternative: Using JSON path query
     */
    @Query(value = """
        SELECT e.entity_id
        FROM ai_searchable_entities e
        WHERE e.entity_type = 'document'
          AND EXISTS (
              SELECT 1 FROM users u
              WHERE u.id = (e.metadata::jsonb->>'createdBy')
              AND u.status = 'active'
          )
        """, nativeQuery = true)
    List<String> findDocumentsByActiveUsersAlternative();
}


// ============================================================================
// STEP 4: OPTIMIZATION - Denormalization (Store User Status in Metadata)
// ============================================================================

// Enhanced metadata extraction - also stores user status
private Map<String, Object> extractMetadataWithUserStatus(Object entity, AIEntityConfig config) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    
    // Extract createdBy
    String createdBy = getFieldValue(entity, "createdBy");
    metadata.put("createdBy", createdBy);
    
    // NEW: Also fetch and store user status
    if (createdBy != null) {
        User user = userRepository.findById(createdBy);
        if (user != null) {
            metadata.put("createdByUserStatus", user.getStatus());
            // Now metadata = {"createdBy": "user-456", "createdByUserStatus": "active"}
        }
    }
    
    return metadata;
}

// Now query becomes simpler:
@Query(value = """
    SELECT entity_id
    FROM ai_searchable_entities
    WHERE entity_type = 'document'
      AND (metadata::jsonb->>'createdByUserStatus') = 'active'
    """, nativeQuery = true)
List<String> findDocumentsByActiveUsersSimple();


// ============================================================================
// STEP 5: COMPLETE USAGE EXAMPLE
// ============================================================================

@Service
public class DocumentService {
    
    @Autowired
    private DocumentRelationshipQueryService relationshipQueryService;
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    /**
     * Get documents created by active users with full details
     */
    public List<DocumentDTO> getDocumentsByActiveUsers() {
        // Step 1: Get document IDs
        List<String> documentIds = relationshipQueryService.findDocumentsByActiveUsers();
        // Returns: ["doc-123", "doc-999"]
        
        // Step 2: Get full document details
        List<DocumentDTO> documents = new ArrayList<>();
        for (String docId : documentIds) {
            Optional<AISearchableEntity> entityOpt = 
                searchableEntityRepository.findByEntityTypeAndEntityId("document", docId);
            
            if (entityOpt.isPresent()) {
                AISearchableEntity entity = entityOpt.get();
                
                // Parse metadata
                Map<String, Object> metadata = parseMetadata(entity.getMetadata());
                
                // Build DTO
                DocumentDTO dto = DocumentDTO.builder()
                    .id(entity.getEntityId())
                    .content(entity.getSearchableContent())
                    .createdBy((String) metadata.get("createdBy"))
                    .status((String) metadata.get("status"))
                    .build();
                
                documents.add(dto);
            }
        }
        
        return documents;
    }
}


// ============================================================================
// SUMMARY: The Complete Flow
// ============================================================================

/*
STORAGE PHASE:
1. Document created with createdBy = "user-456"
2. AICapabilityService extracts metadata: {"createdBy": "user-456"}
3. Metadata serialized to JSON: '{"createdBy":"user-456"}'
4. Stored in database: ai_searchable_entities.metadata column

QUERY PHASE:
1. Get all documents: SELECT * FROM ai_searchable_entities WHERE entity_type = 'document'
2. Parse metadata JSON for each document
3. Extract createdBy values: ["user-456", "user-123"]
4. Query users: SELECT * FROM users WHERE id IN (...) AND status = 'active'
5. Match: Find documents where createdBy matches active user IDs
6. Return: ["doc-123", "doc-999"]

KEY POINTS:
- Metadata stored as JSON string in database
- Must parse JSON to extract relationship values
- Need to join with user table to check status
- Can optimize with database JSON queries or denormalization
*/
