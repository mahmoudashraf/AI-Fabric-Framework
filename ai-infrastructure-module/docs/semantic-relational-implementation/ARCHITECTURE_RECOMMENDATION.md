# Architecture Recommendation: Relationship-Aware Query System

## 🎯 Decision: Separate Module vs Inside AI Core

### **Recommendation: Separate Module** ✅

**Module Name:** `ai-infrastructure-relationship-query`

---

## 📊 Architecture Analysis

### **Option 1: Inside AI Core** ❌

```
ai-infrastructure-core/
├── relationship/
│   ├── RelationshipQueryPlanner.java
│   ├── DynamicJPAQueryBuilder.java
│   ├── LLMDrivenJPAQueryService.java
│   └── ...
├── rag/
├── core/
└── ...
```

**Pros:**
- ✅ Simpler structure
- ✅ Everything in one place
- ✅ No dependency management

**Cons:**
- ❌ **Tight coupling** - Forces relationship features on all users
- ❌ **Larger module** - Increases bundle size
- ❌ **Optional dependency** - Not all apps need this
- ❌ **Harder to evolve** - Changes affect entire core
- ❌ **Violates Single Responsibility** - Core should be foundational

---

### **Option 2: Separate Module** ✅

```
ai-infrastructure-module/
├── ai-infrastructure-core/          (foundational)
│   ├── rag/
│   ├── core/
│   └── ...
├── ai-infrastructure-relationship-query/  (NEW - optional)
│   ├── relationship/
│   │   ├── RelationshipQueryPlanner.java
│   │   ├── DynamicJPAQueryBuilder.java
│   │   ├── LLMDrivenJPAQueryService.java
│   │   └── ...
│   └── ...
└── pom.xml
```

**Pros:**
- ✅ **Separation of concerns** - Clear boundaries
- ✅ **Optional dependency** - Users opt-in if needed
- ✅ **Smaller core** - Core stays focused
- ✅ **Easier to evolve** - Changes isolated
- ✅ **Better testing** - Test independently
- ✅ **Follows Spring Boot pattern** - Like spring-boot-starter-*

**Cons:**
- ⚠️ Slightly more complex (but manageable)
- ⚠️ Need to manage dependencies

---

## 🏗️ Recommended Architecture

### **Module Structure:**

```
ai-infrastructure-module/
├── ai-infrastructure-core/                    (foundational - required)
│   ├── rag/                                  (RAG capabilities)
│   ├── core/                                 (AI core services)
│   ├── service/                              (AICapabilityService, etc.)
│   └── ...
│
├── ai-infrastructure-relationship-query/      (NEW - optional)
│   ├── src/main/java/com/ai/infrastructure/relationship/
│   │   ├── RelationshipQueryPlanner.java
│   │   ├── DynamicJPAQueryBuilder.java
│   │   ├── LLMDrivenJPAQueryService.java
│   │   ├── RelationshipTraversalService.java
│   │   ├── EntityRelationshipMapper.java
│   │   └── ...
│   ├── src/main/java/com/ai/infrastructure/dto/
│   │   └── RelationshipQueryPlan.java
│   └── pom.xml
│
└── pom.xml                                    (parent POM)
```

---

## 📦 Module Dependencies

### **ai-infrastructure-relationship-query/pom.xml:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-module</artifactId>
        <version>2.0.0</version>
    </parent>
    
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <name>AI Infrastructure Relationship Query</name>
    <description>
        Relationship-aware query system combining LLM intent understanding,
        JPA relational queries, and vector semantic search.
    </description>
    
    <dependencies>
        <!-- Core AI Infrastructure (required) -->
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- Spring Data JPA (for JPA queries) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <!-- JPA API -->
        <dependency>
            <groupId>jakarta.persistence</groupId>
            <artifactId>jakarta.persistence-api</artifactId>
        </dependency>
        
        <!-- Optional: Jackson for JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 🔗 Integration Points

### **1. Core Module Provides:**

```java
// ai-infrastructure-core provides:
- AICoreService (for LLM calls)
- AIEmbeddingService (for embeddings)
- VectorDatabaseService (for vector operations)
- AISearchableEntityRepository (for metadata)
- AISearchableEntity (entity)
```

### **2. Relationship Module Uses:**

```java
// ai-infrastructure-relationship-query uses:
- AICoreService.generateContent() → LLM query planning
- AIEmbeddingService.generateEmbedding() → Query embeddings
- VectorDatabaseService.getVector() → Document vectors
- AISearchableEntityRepository → Metadata access
```

### **3. Relationship Module Extends:**

```java
// ai-infrastructure-relationship-query extends:
- RelationshipQueryPlanner (uses AICoreService)
- DynamicJPAQueryBuilder (uses EntityManager)
- LLMDrivenJPAQueryService (orchestrates everything)
```

---

## 🎯 Auto-Configuration

### **Spring Boot Auto-Configuration Pattern:**

```java
// ai-infrastructure-relationship-query/src/main/resources/
// META-INF/spring.factories

org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ai.infrastructure.relationship.config.RelationshipQueryAutoConfiguration
```

### **Auto-Configuration Class:**

```java
package com.ai.infrastructure.relationship.config;

@Configuration
@ConditionalOnClass({RelationshipQueryPlanner.class, AICoreService.class})
@ConditionalOnProperty(
    prefix = "ai.infrastructure.relationship",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@AutoConfigureAfter(AIInfrastructureAutoConfiguration.class)
public class RelationshipQueryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public EntityRelationshipMapper entityRelationshipMapper() {
        return new EntityRelationshipMapper();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RelationshipQueryPlanner relationshipQueryPlanner(
            AICoreService aiCoreService,
            EntityRelationshipMapper entityMapper,
            RelationshipSchemaProvider schemaProvider) {
        return new RelationshipQueryPlanner(aiCoreService, entityMapper, schemaProvider);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public DynamicJPAQueryBuilder dynamicJPAQueryBuilder(
            EntityManager entityManager,
            EntityRelationshipMapper entityMapper) {
        return new DynamicJPAQueryBuilder(entityManager, entityMapper);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LLMDrivenJPAQueryService llmDrivenJPAQueryService(
            RelationshipQueryPlanner queryPlanner,
            DynamicJPAQueryBuilder queryBuilder,
            EntityManager entityManager,
            AISearchableEntityRepository searchableEntityRepository,
            VectorDatabaseService vectorDatabaseService,
            AIEmbeddingService embeddingService) {
        return new LLMDrivenJPAQueryService(
            queryPlanner, queryBuilder, entityManager,
            searchableEntityRepository, vectorDatabaseService, embeddingService
        );
    }
}
```

---

## 📝 Usage Pattern

### **For Users Who Want Relationship Queries:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <version>2.0.0</version>
</dependency>
```

```yaml
# application.yml
ai:
  infrastructure:
    relationship:
      enabled: true  # Opt-in
```

```java
// Use it
@Autowired
private LLMDrivenJPAQueryService relationshipQueryService;

public RAGResponse search(String query) {
    return relationshipQueryService.executeRelationshipQuery(query, entityTypes);
}
```

### **For Users Who Don't Need It:**

```xml
<!-- Just use core -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

```java
// Use standard RAG
@Autowired
private RAGService ragService;

public RAGResponse search(String query) {
    return ragService.performRag(request);
}
```

---

## 🎨 Package Structure

### **Recommended Package Layout:**

```
com.ai.infrastructure.relationship/
├── RelationshipQueryPlanner.java          (LLM query planning)
├── DynamicJPAQueryBuilder.java             (JPQL generation)
├── LLMDrivenJPAQueryService.java          (Main service)
├── RelationshipTraversalService.java      (Relationship traversal)
├── EntityRelationshipMapper.java          (Entity mapping)
├── RelationshipSchemaProvider.java        (Schema info)
│
├── config/
│   ├── RelationshipQueryAutoConfiguration.java
│   └── RelationshipQueryProperties.java
│
├── dto/
│   └── RelationshipQueryPlan.java         (Move from core)
│
└── util/
    └── QueryValidator.java                (Query validation)
```

---

## 🔄 Migration Strategy

### **Phase 1: Create Module (Current)**
```bash
# Create new module
mkdir ai-infrastructure-relationship-query
# Move relationship classes from core
# Update dependencies
```

### **Phase 2: Update Core**
```java
// Core remains foundational
// Remove relationship classes
// Keep only what's needed by relationship module
```

### **Phase 3: Update Users**
```xml
<!-- Users opt-in by adding dependency -->
<dependency>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
</dependency>
```

---

## 📊 Dependency Graph

```
User Application
    ↓
ai-infrastructure-relationship-query (optional)
    ↓ depends on
ai-infrastructure-core (required)
    ↓ depends on
Spring Boot, JPA, etc.
```

---

## ✅ Benefits of Separate Module

### **1. Clean Separation**
- Core = Foundational capabilities
- Relationship = Advanced feature
- Clear boundaries

### **2. Optional Dependency**
- Users opt-in if needed
- Smaller footprint for basic users
- Better performance (no unused code)

### **3. Easier Evolution**
- Can evolve independently
- Version separately
- Backward compatible changes

### **4. Better Testing**
- Test relationship features independently
- Mock core dependencies
- Isolated test suites

### **5. Follows Patterns**
- Spring Boot starter pattern
- Maven module pattern
- Industry best practices

---

## 🎯 Final Recommendation

### **Structure:**

```
ai-infrastructure-module/
├── ai-infrastructure-core/              ✅ Keep as-is (foundational)
│   └── (RAG, embeddings, vector search)
│
└── ai-infrastructure-relationship-query/ ✅ NEW (optional advanced feature)
    └── (Relationship-aware queries)
```

### **Why:**
1. ✅ **Separation of concerns** - Clear boundaries
2. ✅ **Optional dependency** - Users choose
3. ✅ **Smaller core** - Focused responsibility
4. ✅ **Easier evolution** - Independent changes
5. ✅ **Better architecture** - Follows best practices

### **Implementation:**
1. Create new module `ai-infrastructure-relationship-query`
2. Move relationship classes from core
3. Add dependency on core
4. Create auto-configuration
5. Update documentation

---

## 📝 Module Naming Convention

Following Spring Boot pattern:
- `ai-infrastructure-core` - Core capabilities
- `ai-infrastructure-relationship-query` - Relationship queries
- `ai-infrastructure-onnx-starter` - ONNX support (existing)

Future modules could be:
- `ai-infrastructure-graph-query` - Graph database support
- `ai-infrastructure-multi-tenant` - Multi-tenancy
- `ai-infrastructure-audit` - Enhanced auditing

---

## 🚀 Quick Start Guide

### **For Module Developers:**

```bash
# Create module structure
cd ai-infrastructure-module
mkdir -p ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship
mkdir -p ai-infrastructure-relationship-query/src/main/resources/META-INF

# Move relationship classes
mv ai-infrastructure-core/src/main/java/com/ai/infrastructure/relationship/* \
   ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/

# Create pom.xml
# Create auto-configuration
# Update parent pom.xml
```

### **For Users:**

```xml
<!-- Add dependency -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <version>2.0.0</version>
</dependency>
```

```java
// Use it
@Autowired
private LLMDrivenJPAQueryService queryService;
```

---

## ✅ Summary

**Recommendation: Separate Module** ✅

**Structure:**
- `ai-infrastructure-core` - Foundational (required)
- `ai-infrastructure-relationship-query` - Advanced feature (optional)

**Benefits:**
- Clean architecture
- Optional dependency
- Easier evolution
- Better testing
- Follows patterns

**This is the right architectural choice!** 🎯
