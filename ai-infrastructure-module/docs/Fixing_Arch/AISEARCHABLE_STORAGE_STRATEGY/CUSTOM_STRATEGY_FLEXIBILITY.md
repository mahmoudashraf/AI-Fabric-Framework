# 🎯 Custom Strategy - Complete Flexibility for Any Storage Backend

**Question**: Is the custom strategy flexible enough for users to use ANY type of storage (file-based, DB, S3, etc) and our library just uses the interface?

**Answer**: ✅ **YES - COMPLETELY FLEXIBLE!**

---

## 🏗️ Architecture Design

The core strength is the **Strategy Interface**:

```java
public interface AISearchableEntityStorageStrategy {
    void save(AISearchableEntity entity);
    Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId);
    List<AISearchableEntity> findByEntityType(String entityType);
    Optional<AISearchableEntity> findByVectorId(String vectorId);
    void delete(AISearchableEntity entity);
    void update(AISearchableEntity entity);
    boolean isHealthy();
    String getStrategyName();
    List<String> getSupportedEntityTypes();
}
```

**Key Point**: The library only depends on this interface!
- ✅ Doesn't care HOW entities are stored
- ✅ Only cares THAT they are stored & retrieved correctly
- ✅ Users implement it however they want!

---

## 🌟 What Users Can Implement

### 1️⃣ **Database Options**

#### MySQL/PostgreSQL
```java
public class RelationalDatabaseStrategy implements AISearchableEntityStorageStrategy {
    // Use JDBC, JPA, MyBatis, etc.
    // Single table, per-type tables, sharding, partitioning
    // Any relational model you want
}
```

#### NoSQL (MongoDB, DynamoDB)
```java
public class MongoDBStrategy implements AISearchableEntityStorageStrategy {
    // Use MongoDB driver
    // Store as BSON documents
    // No schema limitations
}
```

#### Vector Database (Milvus, Pinecone, Weaviate)
```java
public class MilvusStrategy implements AISearchableEntityStorageStrategy {
    // Use Milvus vector database
    // Optimize for vector search
    // Built for embeddings
}
```

### 2️⃣ **File-Based Storage**

#### File System
```java
public class FileSystemStrategy implements AISearchableEntityStorageStrategy {
    // Store as JSON/XML files
    // Organize in directories by entity type
    // Use local file system
}
```

#### Archive-Based
```java
public class ZipArchiveStrategy implements AISearchableEntityStorageStrategy {
    // Store in ZIP archives
    // One archive per entity type
    // Compressed storage
}
```

### 3️⃣ **Cloud Storage**

#### AWS S3
```java
public class S3Strategy implements AISearchableEntityStorageStrategy {
    // Store in S3 buckets
    // Organize with prefixes (s3://bucket/entity-type/)
    // Use S3 metadata for queries
}
```

#### Azure Blob Storage
```java
public class AzureBlobStrategy implements AISearchableEntityStorageStrategy {
    // Store in Azure Blob containers
    // Organize by entity type
    // Use Azure SDK
}
```

#### Google Cloud Storage
```java
public class GCPStorageStrategy implements AISearchableEntityStorageStrategy {
    // Store in GCS buckets
    // Organize with folders
    // Use GCP client library
}
```

### 4️⃣ **Specialized Storage**

#### Elasticsearch
```java
public class ElasticsearchStrategy implements AISearchableEntityStorageStrategy {
    // Store in Elasticsearch indices
    // Use document-based model
    // Built-in full-text search
}
```

#### Redis / Cache
```java
public class RedisStrategy implements AISearchableEntityStorageStrategy {
    // Store in Redis hashes/sets
    // Ultra-fast retrieval
    // In-memory storage
}
```

#### Hybrid (Multi-layer)
```java
public class HybridStrategy implements AISearchableEntityStorageStrategy {
    // Store in DB + cache + S3
    // Read from cache first
    // Fall back to DB
    // Archive to S3 periodically
}
```

---

## 💡 Real-World Examples

### Example 1: File-System Strategy

```java
/**
 * Store AISearchableEntity as JSON files in file system
 * Each entity type gets its own directory
 */
@Component
public class FileSystemStorageStrategy implements AISearchableEntityStorageStrategy {
    
    private final String baseDir;
    
    public FileSystemStorageStrategy(@Value("${storage.file-system.base-dir}") String baseDir) {
        this.baseDir = baseDir;
    }
    
    @Override
    public void save(AISearchableEntity entity) {
        // Save to: {baseDir}/{entityType}/{entityId}.json
        Path dir = Paths.get(baseDir, entity.getEntityType());
        Files.createDirectories(dir);
        
        Path filePath = dir.resolve(entity.getEntityId() + ".json");
        String json = objectMapper.writeValueAsString(entity);
        Files.write(filePath, json.getBytes());
        
        log.info("Saved entity to: {}", filePath);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        // Read from: {baseDir}/{entityType}/{entityId}.json
        Path filePath = Paths.get(baseDir, entityType, entityId + ".json");
        
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        
        String json = Files.readString(filePath);
        AISearchableEntity entity = objectMapper.readValue(json, AISearchableEntity.class);
        return Optional.of(entity);
    }
    
    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        // Read all files from: {baseDir}/{entityType}/*.json
        Path dir = Paths.get(baseDir, entityType);
        
        if (!Files.exists(dir)) {
            return List.of();
        }
        
        return Files.list(dir)
            .filter(path -> path.toString().endsWith(".json"))
            .map(path -> {
                try {
                    String json = Files.readString(path);
                    return objectMapper.readValue(json, AISearchableEntity.class);
                } catch (IOException e) {
                    log.error("Error reading file: {}", path, e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        Path filePath = Paths.get(baseDir, entity.getEntityType(), entity.getEntityId() + ".json");
        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted entity: {}", filePath);
        } catch (IOException e) {
            log.error("Error deleting file: {}", filePath, e);
        }
    }
    
    @Override
    public boolean isHealthy() {
        try {
            Path baseDir = Paths.get(this.baseDir);
            Files.createDirectories(baseDir);
            return true;
        } catch (IOException e) {
            log.error("File system storage not healthy", e);
            return false;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "FILE_SYSTEM";
    }
    
    @Override
    public List<String> getSupportedEntityTypes() {
        try {
            return Files.list(Paths.get(baseDir))
                .filter(Files::isDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
```

### Example 2: S3 Strategy

```java
/**
 * Store AISearchableEntity in AWS S3
 * Organize by entity type using S3 prefixes
 */
@Component
public class S3StorageStrategy implements AISearchableEntityStorageStrategy {
    
    private final AmazonS3 s3Client;
    private final String bucketName;
    
    @Override
    public void save(AISearchableEntity entity) {
        // S3 path: {entityType}/{entityId}.json
        String key = entity.getEntityType() + "/" + entity.getEntityId() + ".json";
        String json = objectMapper.writeValueAsString(entity);
        
        s3Client.putObject(bucketName, key, json);
        log.info("Saved entity to S3: {}", key);
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        String key = entityType + "/" + entityId + ".json";
        
        try {
            S3Object s3Object = s3Client.getObject(bucketName, key);
            String json = IOUtils.toString(s3Object.getObjectContent(), StandardCharsets.UTF_8);
            AISearchableEntity entity = objectMapper.readValue(json, AISearchableEntity.class);
            return Optional.of(entity);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            log.error("Error reading from S3: {}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        // List all objects with prefix: {entityType}/
        String prefix = entityType + "/";
        
        return s3Client.listObjects(bucketName, prefix)
            .getObjectSummaries()
            .stream()
            .map(summary -> {
                try {
                    S3Object s3Object = s3Client.getObject(bucketName, summary.getKey());
                    String json = IOUtils.toString(s3Object.getObjectContent(), StandardCharsets.UTF_8);
                    return objectMapper.readValue(json, AISearchableEntity.class);
                } catch (Exception e) {
                    log.error("Error reading from S3: {}", summary.getKey(), e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        String key = entity.getEntityType() + "/" + entity.getEntityId() + ".json";
        s3Client.deleteObject(bucketName, key);
        log.info("Deleted entity from S3: {}", key);
    }
    
    @Override
    public boolean isHealthy() {
        try {
            s3Client.headBucket(new HeadBucketRequest(bucketName));
            return true;
        } catch (AmazonServiceException e) {
            log.error("S3 storage not healthy", e);
            return false;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "S3";
    }
    
    @Override
    public List<String> getSupportedEntityTypes() {
        return s3Client.listObjects(bucketName)
            .getObjectSummaries()
            .stream()
            .map(s -> s.getKey().split("/")[0])
            .distinct()
            .toList();
    }
}
```

### Example 3: MongoDB Strategy

```java
/**
 * Store AISearchableEntity in MongoDB
 * One collection per entity type
 */
@Component
public class MongoDBStorageStrategy implements AISearchableEntityStorageStrategy {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public void save(AISearchableEntity entity) {
        // Save to collection: {entityType}
        String collectionName = "ai_searchable_" + entity.getEntityType().toLowerCase();
        mongoTemplate.save(entity, collectionName);
        
        log.info("Saved entity to MongoDB: {}/{}", collectionName, entity.getEntityId());
    }
    
    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        String collectionName = "ai_searchable_" + entityType.toLowerCase();
        Query query = new Query(Criteria.where("entityId").is(entityId));
        
        AISearchableEntity entity = mongoTemplate.findOne(query, AISearchableEntity.class, collectionName);
        return Optional.ofNullable(entity);
    }
    
    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        String collectionName = "ai_searchable_" + entityType.toLowerCase();
        
        return mongoTemplate.findAll(AISearchableEntity.class, collectionName);
    }
    
    @Override
    public void delete(AISearchableEntity entity) {
        String collectionName = "ai_searchable_" + entity.getEntityType().toLowerCase();
        Query query = new Query(Criteria.where("id").is(entity.getId()));
        
        mongoTemplate.remove(query, collectionName);
    }
    
    @Override
    public boolean isHealthy() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.error("MongoDB storage not healthy", e);
            return false;
        }
    }
    
    @Override
    public String getStrategyName() {
        return "MONGODB";
    }
    
    @Override
    public List<String> getSupportedEntityTypes() {
        return mongoTemplate.getDb().listCollectionNames()
            .stream()
            .filter(name -> name.startsWith("ai_searchable_"))
            .map(name -> name.substring("ai_searchable_".length()))
            .toList();
    }
}
```

---

## ✅ How Library Uses Strategies

The key is: **Library only depends on the interface!**

```java
@Service
public class AISearchableService {
    
    private final AISearchableEntityStorageStrategy storageStrategy;
    
    public void indexEntity(Entity entity, String vectorId) {
        AISearchableEntity searchableEntity = transform(entity, vectorId);
        
        // Library just calls the interface method
        // Doesn't care HOW it's implemented
        storageStrategy.save(searchableEntity);
    }
    
    public Optional<AISearchableEntity> findEntity(String entityType, String entityId) {
        // Library just calls the interface method
        // Works with ANY implementation!
        return storageStrategy.findByEntityTypeAndEntityId(entityType, entityId);
    }
    
    public List<AISearchableEntity> searchEntities(String entityType) {
        // Library just calls the interface method
        // S3, DB, File System, all the same!
        return storageStrategy.findByEntityType(entityType);
    }
}
```

---

## 🎯 Storage Options Users Can Choose

```yaml
# Option 1: Single Database Table (Provided by library)
ai-infrastructure:
  storage:
    strategy: SINGLE_TABLE

# Option 2: Per-Type Database Tables (Provided by library)
ai-infrastructure:
  storage:
    strategy: PER_TYPE_TABLE

# Option 3: File System (User implements)
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-class: "com.mycompany.FileSystemStorageStrategy"
    custom-properties:
      base-dir: "/data/ai-searchable"

# Option 4: S3 (User implements)
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-class: "com.mycompany.S3StorageStrategy"
    custom-properties:
      bucket-name: "my-ai-storage"
      region: "us-east-1"

# Option 5: MongoDB (User implements)
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-class: "com.mycompany.MongoDBStorageStrategy"
    custom-properties:
      connection-string: "mongodb://localhost:27017"

# Option 6: Multiple backends (User implements hybrid)
ai-infrastructure:
  storage:
    strategy: CUSTOM
    custom-class: "com.mycompany.HybridStorageStrategy"
    custom-properties:
      cache-backend: "REDIS"
      primary-backend: "MONGODB"
      archive-backend: "S3"
```

---

## 🔄 How Custom Strategies Work

### User Creates Custom Strategy

```java
@Component
public class MyCustomStorageStrategy implements AISearchableEntityStorageStrategy {
    // Implement all 8 methods
    // Can use ANY storage backend
    // Library doesn't care about implementation
}
```

### Library Configuration

```java
@Configuration
public class StorageStrategyConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        name = "ai-infrastructure.storage.strategy",
        havingValue = "CUSTOM"
    )
    public AISearchableEntityStorageStrategy customStrategy(
        @Value("${ai-infrastructure.storage.custom-class}") String customClass
    ) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(customClass);
        return (AISearchableEntityStorageStrategy) applicationContext.getBean(clazz);
    }
}
```

### Library Uses Strategy

```java
@Service
public class AISearchableService {
    
    private final AISearchableEntityStorageStrategy storageStrategy;
    
    // Works with ANY implementation!
    public void saveEntity(AISearchableEntity entity) {
        storageStrategy.save(entity); // ← Could be DB, S3, FileSystem, etc.
    }
}
```

---

## 🌟 Flexibility Benefits

✅ **Library doesn't impose storage choice**
- Works with your existing infrastructure
- Respect your organizational decisions
- Use what you're comfortable with

✅ **Users control their data**
- File-based? Sure!
- S3/Cloud? Absolutely!
- Their database? Perfect!
- Hybrid? Totally possible!

✅ **Easy to switch**
- Just implement the interface
- Update configuration
- Deploy
- No library code changes

✅ **Testable**
- Mock the strategy in tests
- Implement in-memory strategy for testing
- No real storage backend needed

✅ **Future-proof**
- New storage backend? Implement interface
- New requirements? Implement interface
- No library changes needed

---

## 📊 Comparison Matrix

| Storage | Complexity | Performance | Cost | Use Case |
|---------|-----------|-------------|------|----------|
| Single DB Table | Low | Good | Low | MVP, small scale |
| Per-Type DB Tables | Low | Better | Low | Medium scale |
| File System | Low | Moderate | Very Low | Development, testing |
| S3 | Medium | Moderate | Low-Medium | Large scale, archive |
| MongoDB | Medium | Good | Medium | Document-oriented |
| Elasticsearch | Medium | Excellent | Medium-High | Full-text search |
| Redis | Medium | Excellent | Medium | Cache layer |
| Hybrid | High | Optimal | Varies | Enterprise |

---

## 🎯 Why This Design is Perfect

✅ **Library provides interface** → Clear contract  
✅ **Library provides implementations** → Quick start (DB strategies)  
✅ **User can implement custom** → Full flexibility  
✅ **Library uses interface only** → Works with anything  
✅ **No lock-in** → Users choose what's best for them  
✅ **Future-proof** → New backends without library changes  

---

## 💡 Key Insight

**The library is BACKEND-AGNOSTIC!**

It doesn't care WHERE data is stored:
- Database ✅
- File System ✅
- S3 ✅
- Redis ✅
- MongoDB ✅
- Elasticsearch ✅
- Any storage ✅

**As long as you implement the interface!**

---

**This is why custom strategy is PERFECT for flexibility!** ✨

Users can implement ANY storage backend by just implementing 8 methods. The library doesn't dictate their infrastructure choices!


