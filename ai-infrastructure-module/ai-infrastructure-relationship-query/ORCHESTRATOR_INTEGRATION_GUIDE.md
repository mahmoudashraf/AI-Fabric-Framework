# Relationship Query + Orchestrator Integration Guide

## Overview

The Relationship Query module supports **two integration patterns**:

1. **Direct Usage** - Inject `ReliableRelationshipQueryService` directly (simple, specialized apps)
2. **Via Orchestrator** - Route through `RAGOrchestrator` (enterprise apps with security/compliance needs)

Both patterns are supported simultaneously. Choose based on your requirements.

---

## Pattern 1: Direct Usage (Recommended for Simple Apps)

### When to Use
- Focused relationship query applications
- Need full control over `QueryOptions`
- Don't need orchestrator features (PII detection, behavior insights, access control)
- Direct, predictable query execution

### Example

```java
@Service
public class CustomerSearchService {
    
    @Autowired
    private ReliableRelationshipQueryService queryService;
    
    public List<Customer> findPremiumCustomers(String query) {
        RAGResponse response = queryService.execute(
            query,
            List.of("customer"),
            QueryOptions.builder()
                .returnMode(ReturnMode.FULL)
                .limit(50)
                .similarityThreshold(0.8)
                .build()
        );
        
        return convertToCustomers(response.getDocuments());
    }
}
```

**Pros:**
- ✅ Simple and direct
- ✅ Full control over query options
- ✅ Minimal abstraction
- ✅ Fast execution

**Cons:**
- ❌ No automatic PII detection/redaction
- ❌ No behavior insights integration
- ❌ No access control enforcement
- ❌ Manual security implementation required

---

## Pattern 2: Via Orchestrator (Recommended for Enterprise Apps)

### When to Use
- Need PII detection and redaction
- Want behavior insights integration (user sentiment, churn risk)
- Require access control and compliance validation
- Unified entry point for all AI queries
- Enterprise security requirements

### Configuration

Enable orchestrator integration (enabled by default):

```yaml
ai:
  infrastructure:
    relationship:
      enable-orchestrator-integration: true  # Default: true
```

### Example

```java
@RestController
@RequestMapping("/api/query")
public class UnifiedQueryController {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @PostMapping
    public Response query(
        @RequestBody QueryRequest request,
        @RequestHeader("Authorization") String auth,
        HttpServletRequest httpRequest
    ) {
        String userId = extractUserId(auth);
        
        // Build orchestration context
        OrchestrationContext context = OrchestrationContext.builder()
            .userId(userId)
            .sessionId(httpRequest.getSession().getId())
            .locale(httpRequest.getLocale())
            .metadata(Map.of(
                "action", "relationship_query",
                "entityTypes", List.of("customer"),
                "limit", 50,
                "returnMode", "FULL"
            ))
            .build();
        
        // Single entry point for all queries
        OrchestrationResult result = orchestrator.orchestrate(
            request.getQuery(),
            context
        );
        
        return Response.ok(result);
    }
}
```

### User Query Format

Users phrase queries to trigger the relationship query action:

```
"Execute relationship query to find premium customers who ordered this month"
```

Or use natural intent:

```
"Find premium customers who ordered this month using relationship search"
```

### LLM Intent Extraction

The LLM extracts the intent and delegates to the relationship query handler:

```json
{
  "type": "ACTION",
  "action": "relationship_query",
  "actionParams": {
    "query": "find premium customers who ordered this month",
    "entityTypes": ["customer", "order"],
    "limit": 20,
    "returnMode": "FULL"
  }
}
```

### Automatic Features

When using the orchestrator, you get:

1. **PII Detection** - Automatic redaction of sensitive data in queries
2. **Security Checks** - Request analysis and blocking of malicious queries
3. **Access Control** - Policy-based access validation
4. **Compliance** - Automatic compliance checks (GDPR, HIPAA, etc.)
5. **Behavior Insights** - User sentiment/churn context added to queries
6. **Unified Logging** - Consistent request tracking

**Pros:**
- ✅ Automatic security and compliance
- ✅ Behavior insights enrichment
- ✅ PII detection/redaction
- ✅ Access control enforcement
- ✅ Unified API for all query types
- ✅ Enterprise-ready

**Cons:**
- ❌ Slightly more complex setup
- ❌ Additional orchestration overhead (~10-50ms)
- ❌ Less direct control over query execution

---

## Comparison Table

| Feature | Direct Usage | Via Orchestrator |
|---------|--------------|------------------|
| **Setup Complexity** | Simple - inject service | Moderate - configure orchestrator |
| **PII Detection** | Manual | Automatic |
| **Behavior Insights** | Not available | Automatic |
| **Access Control** | Manual | Automatic |
| **Compliance** | Manual | Automatic |
| **Query Control** | Full control | Standardized |
| **Latency** | Minimal | +10-50ms |
| **Use Case** | Specialized apps | Enterprise platforms |

---

## Action Handler Details

### Action Metadata

```json
{
  "name": "relationship_query",
  "description": "Execute natural language queries against relational data with automatic relationship traversal",
  "category": "data_query",
  "parameters": {
    "query": "Natural language query (required)",
    "entityTypes": "List of entity types to search (required)",
    "limit": "Maximum results to return (optional, default: 20)",
    "returnMode": "IDS or FULL (optional, default: IDS)",
    "forceMode": "STANDALONE or ENHANCED (optional, auto-detected)",
    "similarityThreshold": "Vector search threshold 0.0-1.0 (optional, default: 0.7)"
  }
}
```

### Action Parameters

```java
Map<String, Object> actionParams = Map.of(
    "query", "Find premium users who ordered this month",
    "entityTypes", List.of("user", "order"),
    "limit", 50,
    "returnMode", "FULL",
    "forceMode", "ENHANCED",
    "similarityThreshold", 0.8
);
```

### Action Result

```json
{
  "success": true,
  "message": "Found 15 results in 245ms using hybrid search (confidence: 0.85)",
  "data": {
    "documents": [...],
    "totalResults": 15,
    "returnedResults": 15,
    "queryProcessingTimeMs": 230,
    "orchestrationTimeMs": 245,
    "hybridSearchUsed": true,
    "confidenceScore": 0.85,
    "metadata": {...}
  }
}
```

---

## Disabling Orchestrator Integration

If you want to use only direct usage:

```yaml
ai:
  infrastructure:
    relationship:
      enable-orchestrator-integration: false
```

This disables the `RelationshipQueryActionHandler` registration. The orchestrator won't recognize relationship query actions, but direct usage via `ReliableRelationshipQueryService` still works.

---

## Migration Path

### From Direct to Orchestrator

1. **Keep existing code** - Direct usage continues to work
2. **Add orchestrator endpoint** - Create new controller for orchestrated queries
3. **Gradual migration** - Move critical flows to orchestrator first
4. **Monitor** - Track PII detections, security blocks, behavior insights usage
5. **Deprecate direct** - Once confident, migrate all to orchestrator

### Example Migration

**Before (Direct):**
```java
@PostMapping("/search/customers")
public Response searchCustomers(@RequestBody String query) {
    RAGResponse response = queryService.execute(query, List.of("customer"), null);
    return Response.ok(response);
}
```

**After (Orchestrator):**
```java
@PostMapping("/search/customers")
public Response searchCustomers(
    @RequestBody String query,
    @RequestHeader("Authorization") String auth,
    HttpServletRequest httpRequest
) {
    OrchestrationContext context = OrchestrationContext.builder()
        .userId(extractUserId(auth))
        .sessionId(httpRequest.getSession().getId())
        .metadata(Map.of("entityTypes", List.of("customer")))
        .build();
    
    OrchestrationResult result = orchestrator.orchestrate(query, context);
    return Response.ok(result);
}
```

---

## Best Practices

### For Direct Usage
- ✅ Use for specialized relationship query apps
- ✅ Implement your own PII detection if handling sensitive data
- ✅ Add custom access control if needed
- ✅ Log queries for audit trail
- ✅ Monitor query performance

### For Orchestrator Usage
- ✅ Use for enterprise applications
- ✅ Leverage behavior insights for personalization
- ✅ Trust automatic PII detection
- ✅ Configure access control policies
- ✅ Monitor orchestration metrics

---

## Troubleshooting

### Action Not Found

If orchestrator returns "No action handler registered for 'relationship_query'":

1. **Check configuration:**
   ```yaml
   ai.infrastructure.relationship.enable-orchestrator-integration: true
   ```

2. **Check Spring component scan:**
   Ensure `com.ai.infrastructure.relationship.action` is scanned

3. **Check logs:**
   Look for: `"Registered action handlers: [relationship_query, ...]"`

### Invalid Parameters

If getting "Invalid query parameters" errors:

1. **Check required fields:**
   - `query` (string, not empty)
   - `entityTypes` (list, not empty)

2. **Check parameter types:**
   - `limit` must be number
   - `returnMode` must be "IDS" or "FULL"
   - `forceMode` must be "STANDALONE" or "ENHANCED"
   - `similarityThreshold` must be 0.0-1.0

---

## Summary

**Choose Direct Usage when:**
- Building specialized relationship query app
- Need full control over query options
- Don't need enterprise security features
- Want minimal abstraction

**Choose Orchestrator when:**
- Building enterprise application
- Need PII detection, access control, compliance
- Want behavior insights integration
- Prefer unified API for all queries

**Or use both:**
- Direct for internal APIs
- Orchestrator for customer-facing APIs

Both patterns are fully supported and can coexist in the same application.

---

**Document Version:** 1.0  
**Created:** 2025-12-30  
**Status:** Production Ready

