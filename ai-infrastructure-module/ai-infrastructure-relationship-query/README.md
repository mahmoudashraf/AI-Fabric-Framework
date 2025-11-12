# AI Infrastructure Relationship Query Module

## 🎯 Overview

Optional module providing relationship-aware natural language query capabilities.

**Combines:**
- ✅ LLM intent understanding
- ✅ JPA relational queries
- ✅ Vector semantic search

**Result:** Intelligent queries that understand relationships and meaning.

---

## 🚀 Quick Start

### **1. Add Dependency**

```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <version>2.0.0</version>
</dependency>
```

### **2. Configure (Optional)**

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true
      default-similarity-threshold: 0.7
      max-traversal-depth: 3
```

### **3. Use It**

```java
@Autowired
private LLMDrivenJPAQueryService relationshipQueryService;

public RAGResponse search(String query) {
    return relationshipQueryService.executeRelationshipQuery(
        query,
        Arrays.asList("document", "user", "project")
    );
}
```

---

## 📖 Documentation

- [Quick Start Guide](../docs/QUICK_START.md)
- [Configuration Guide](../docs/CONFIGURATION_GUIDE.md)
- [API Reference](../docs/API_REFERENCE.md)
- [Architecture](../docs/ARCHITECTURE.md)
- [Use Cases](../docs/REAL_WORLD_UNIFIED_SEARCH_CASES.md)

---

## 🎯 Features

- Natural language queries
- JPA relationship traversal
- Semantic similarity ranking
- Automatic schema understanding
- Query validation & security
- Fallback strategies
- Performance optimization

---

## 📝 License

Same as parent project.
