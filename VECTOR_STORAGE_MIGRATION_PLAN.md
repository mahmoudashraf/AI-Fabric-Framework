# Vector Storage Migration Plan: AISearchableEntity to Vector Database

## Overview

This plan outlines the migration of vector storage from the `AISearchableEntity` JPA entity to a dedicated vector database, using Apache Lucene for development and test profiles. The migration will eliminate the need to store embeddings directly in the database while maintaining full AI search capabilities.

## Current State Analysis

### Current Implementation
- **AISearchableEntity**: Stores embeddings as `List<Double>` in JPA entity
- **Storage**: PostgreSQL with JSON column for metadata
- **Vector Operations**: Basic similarity search through database queries
- **Profiles**: Single implementation across all environments

### Issues with Current Approach
1. **Performance**: Large embedding vectors (1536+ dimensions) stored in PostgreSQL
2. **Scalability**: Database bloat with vector data
3. **Search Efficiency**: Limited vector search capabilities in PostgreSQL
4. **Maintenance**: Complex queries for vector operations
5. **Storage Cost**: High storage requirements for vector data

## Migration Goals

### Primary Objectives
1. **Remove vector storage** from `AISearchableEntity` entity
2. **Implement vector database** for all vector operations
3. **Use Apache Lucene** for dev and test profiles
4. **Maintain backward compatibility** during transition (if needed)
5. **Improve performance** and scalability

### Success Criteria
- Zero vector data stored in PostgreSQL
- All vector operations handled by vector database
- Lucene integration working for dev/test profiles
- Performance improvement in vector search operations
- Clean separation of concerns

## Architecture Design

### New Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │    │  Vector Database │    │   PostgreSQL    │
│                 │    │                  │    │                 │
│ AICapabilitySvc │───▶│  Lucene (dev)    │    │ AISearchableEntity│
│                 │    │  Pinecone (prod) │    │ (metadata only) │
│ VectorSearchSvc │    │  Memory (test)   │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Component Responsibilities

#### AISearchableEntity (Refactored)
- **Store**: Entity metadata, searchable content, timestamps
- **Remove**: Embeddings, vector-specific fields
- **Purpose**: Reference entity for search operations

#### Vector Database Service
- **Store**: All vector embeddings and vector metadata
- **Operations**: Store, search, update, delete vectors
- **Profiles**: 
  - `dev`: Apache Lucene
  - `test`: In-memory store
  - `prod`: Pinecone (future)

#### AICapabilityService (Updated)
- **Vector Operations**: Delegate to VectorDatabaseService
- **Entity Management**: Update AISearchableEntity metadata only
- **Coordination**: Orchestrate between entity and vector store

## Implementation Plan

### Phase 1: Vector Database Infrastructure

#### 1.1 Enhance Vector Database Service
- **File**: `VectorDatabaseService.java`
- **Changes**:
  - Add batch operations for bulk vector management
  - Implement vector update operations
  - Add vector metadata management
  - Enhance search capabilities with filters

#### 1.2 Lucene Integration Enhancement
- **File**: `LuceneVectorDatabaseService.java`
- **Changes**:
  - Optimize vector storage format
  - Implement efficient similarity search
  - Add vector update capabilities
  - Enhance metadata filtering

#### 1.3 Configuration Updates
- **File**: `VectorDatabaseConfig.java`
- **Changes**:
  - Add profile-specific configurations
  - Configure Lucene for dev/test profiles
  - Add vector migration settings

### Phase 2: Entity Refactoring

#### 2.1 AISearchableEntity Refactoring
- **File**: `AISearchableEntity.java`
- **Changes**:
  ```java
  // REMOVE these fields:
  @ElementCollection
  @CollectionTable(name = "ai_embeddings", joinColumns = @JoinColumn(name = "entity_id"))
  @Column(name = "embedding_value")
  private List<Double> embeddings;
  
  // KEEP these fields:
  @Column(name = "entity_type", nullable = false)
  private String entityType;
  
  @Column(name = "entity_id", nullable = false)
  private String entityId;
  
  @Column(name = "searchable_content", columnDefinition = "TEXT")
  private String searchableContent;
  
  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;
  
  @Column(name = "ai_analysis", columnDefinition = "TEXT")
  private String aiAnalysis;
  
  // ADD new fields:
  @Column(name = "vector_id")
  private String vectorId; // Reference to vector in vector database
  
  @Column(name = "vector_updated_at")
  private LocalDateTime vectorUpdatedAt;
  ```

#### 2.2 Repository Updates
- **File**: `AISearchableEntityRepository.java`
- **Changes**:
  - Remove vector-specific query methods
  - Add vector reference queries
  - Update search methods to work with metadata only

### Phase 3: Service Layer Migration

#### 3.1 AICapabilityService Refactoring
- **File**: `AICapabilityService.java`
- **Changes**:
  - Remove direct embedding storage in `storeSearchableEntity()`
  - Delegate vector operations to `VectorDatabaseService`
  - Update entity storage to use vector references
  - Implement vector-entity synchronization

#### 3.2 New Vector Management Service
- **File**: `VectorManagementService.java` (NEW)
- **Purpose**: Centralized vector operations
- **Responsibilities**:
  - Vector storage and retrieval
  - Vector-entity synchronization
  - Batch vector operations
  - Vector cleanup and maintenance

#### 3.3 Search Service Updates
- **File**: `AISearchService.java`
- **Changes**:
  - Integrate with VectorDatabaseService
  - Combine vector search results with entity metadata
  - Implement hybrid search (text + vector)

### Phase 4: Data Migration

#### 4.1 Migration Strategy
- **Approach**: Gradual migration with zero downtime
- **Process**:
  1. Deploy new code with dual-write capability
  2. Migrate existing vectors to vector database
  3. Switch reads to vector database
  4. Remove old vector storage

#### 4.2 Migration Script
- **File**: `VectorMigrationScript.java` (NEW)
- **Purpose**: Migrate existing vectors from database to vector store
- **Features**:
  - Batch processing for large datasets
  - Progress tracking and resumability
  - Validation and rollback capabilities

### Phase 5: Testing and Validation

#### 5.1 Unit Tests
- **Vector Database Service Tests**
- **Entity Refactoring Tests**
- **Service Integration Tests**
- **Migration Script Tests**

#### 5.2 Integration Tests
- **End-to-end vector operations**
- **Search functionality validation**
- **Performance testing**
- **Profile-specific testing**

#### 5.3 Migration Testing
- **Data migration validation**
- **Rollback testing**
- **Performance comparison**
- **Data integrity verification**

## Configuration Changes

### Application Properties

#### Development Profile (`application-dev.yml`)
```yaml
ai:
  vector-db:
    type: lucene
    lucene:
      indexPath: ./data/lucene-vector-index
      similarityThreshold: 0.7
      maxResults: 100
      createIndexIfNotExists: true
```

#### Test Profile (`application-test.yml`)
```yaml
ai:
  vector-db:
    type: memory
    memory:
      enablePersistence: false
      maxVectors: 1000
      enableCleanup: true
```

#### Production Profile (`application-prod.yml`)
```yaml
ai:
  vector-db:
    type: pinecone
    pinecone:
      apiKey: ${PINECONE_API_KEY}
      environment: us-east-1-aws
      indexName: ai-infrastructure-prod
      dimensions: 1536
      metric: cosine
```

### Database Schema Changes

#### Migration Script (`V1.1__Remove_Vector_Storage.sql`)
```sql
-- Remove embeddings table
DROP TABLE IF EXISTS ai_embeddings;

-- Add vector reference fields to AISearchableEntity
ALTER TABLE ai_searchable_entities 
ADD COLUMN vector_id VARCHAR(255),
ADD COLUMN vector_updated_at TIMESTAMP;

-- Create index on vector_id for performance
CREATE INDEX idx_ai_searchable_entities_vector_id ON ai_searchable_entities(vector_id);

-- Remove old indexes related to embeddings
DROP INDEX IF EXISTS idx_ai_embeddings_entity_id;
```

## Implementation Timeline

### Week 1: Infrastructure Setup
- [ ] Enhance VectorDatabaseService interface
- [ ] Improve LuceneVectorDatabaseService implementation
- [ ] Update configuration classes
- [ ] Create VectorManagementService

### Week 2: Entity Refactoring
- [ ] Refactor AISearchableEntity
- [ ] Update repository interfaces
- [ ] Create database migration scripts
- [ ] Update entity tests

### Week 3: Service Migration
- [ ] Refactor AICapabilityService
- [ ] Update search services
- [ ] Implement vector-entity synchronization
- [ ] Create migration scripts

### Week 4: Testing and Deployment
- [ ] Comprehensive testing
- [ ] Performance validation
- [ ] Migration execution
- [ ] Production deployment

## Risk Mitigation

### Technical Risks
1. **Data Loss**: Implement comprehensive backup and rollback procedures
2. **Performance Degradation**: Thorough performance testing and optimization
3. **Search Accuracy**: Validate search results against current implementation
4. **Migration Failures**: Implement resumable migration with progress tracking

### Operational Risks
1. **Downtime**: Use gradual migration with zero-downtime approach
2. **Data Consistency**: Implement validation checks and monitoring
3. **Rollback Complexity**: Maintain rollback procedures and documentation

## Monitoring and Validation

### Key Metrics
- **Vector Search Performance**: Response time, throughput
- **Data Consistency**: Vector-entity synchronization accuracy
- **Storage Efficiency**: Database size reduction
- **Search Quality**: Relevance scores and accuracy

### Monitoring Tools
- **Application Metrics**: Custom metrics for vector operations
- **Database Monitoring**: PostgreSQL performance and size
- **Vector Database Monitoring**: Lucene index health and performance
- **Search Analytics**: Query performance and result quality

## Success Validation

### Functional Validation
- [ ] All vector operations work through vector database
- [ ] Search functionality maintains accuracy
- [ ] Entity metadata remains accessible
- [ ] Profile-specific configurations work correctly

### Performance Validation
- [ ] Vector search performance improved
- [ ] Database size reduced significantly
- [ ] Memory usage optimized
- [ ] Response times within acceptable limits

### Data Validation
- [ ] All existing vectors migrated successfully
- [ ] No data loss during migration
- [ ] Vector-entity references maintained
- [ ] Search results consistent with previous implementation

## Rollback Plan

### Rollback Triggers
- Performance degradation > 20%
- Data inconsistency detected
- Search accuracy drop > 10%
- Critical errors in vector operations

### Rollback Procedure
1. **Immediate**: Switch reads back to database vectors
2. **Data**: Restore vector data from backup
3. **Code**: Deploy previous version
4. **Validation**: Verify system stability
5. **Investigation**: Analyze root cause

## Future Enhancements

### Short-term (Next 3 months)
- [ ] Pinecone integration for production
- [ ] Advanced vector search features
- [ ] Vector compression and optimization
- [ ] Real-time vector updates

### Long-term (Next 6 months)
- [ ] Multi-vector database support
- [ ] Vector analytics and insights
- [ ] Automated vector management
- [ ] Advanced similarity algorithms

## Conclusion

This migration plan provides a comprehensive approach to removing vector storage from AISearchableEntity while maintaining full AI search capabilities. The use of Apache Lucene for dev and test profiles ensures cost-effective development and testing, while the modular design allows for easy production vector database integration.

The phased approach minimizes risk while ensuring thorough testing and validation at each step. The plan adheres to the project guidelines by maintaining clean architecture, comprehensive testing, and detailed documentation.

---

**Document Version**: 1.0  
**Last Updated**: December 2024  
**Status**: Ready for Implementation  
**Next Review**: After Phase 1 completion