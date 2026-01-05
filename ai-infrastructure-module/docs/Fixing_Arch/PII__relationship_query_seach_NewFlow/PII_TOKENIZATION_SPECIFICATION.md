# PII Tokenization for Relationship Queries
## Secure PII-Based Search Without Exposing Data to LLMs

**Version:** 1.0  
**Date:** January 2026  
**Module:** ai-infrastructure-relationship-query  
**Status:** ✅ Production Ready  
**Compliance:** GDPR, HIPAA, PCI-DSS Compatible

---

## Executive Summary

### The Problem

Users need to search databases using PII (Personally Identifiable Information) like:
- Email addresses: `"john.doe@example.com"`
- Credit card numbers: `"4532-1234-5678-9010"`
- Phone numbers: `"555-123-4567"`
- Social Security Numbers: `"123-45-6789"`
- Dates of birth: `"1990-05-15"`

**But:** Sending PII to external LLMs violates privacy regulations and creates security risks.

### The Solution

**PII Tokenization Flow:**
1. Detect PII in user query
2. Replace PII with temporary tokens
3. Send tokenized query to LLM (LLM never sees real PII)
4. LLM generates query plan using tokens
5. Query builder substitutes tokens with real PII values
6. Execute database query with real PII
7. Clear tokens after execution

**Result:** Database searches work correctly while PII never leaves your infrastructure.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [PII Detection & Tokenization](#2-pii-detection--tokenization)
3. [LLM Plan Generation](#3-llm-plan-generation)
4. [Query Builder Token Substitution](#4-query-builder-token-substitution)
5. [Complete Flow Example](#5-complete-flow-example)
6. [Security Considerations](#6-security-considerations)
7. [Implementation](#7-implementation)
8. [Testing](#8-testing)
9. [Configuration](#9-configuration)
10. [Compliance & Audit](#10-compliance--audit)

---

## 1. Architecture Overview

### 1.1 The Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ User Query (with PII)                                            │
│ "find customer with email john.doe@example.com"                 │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: PII Detection & Tokenization                            │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ PIIDetectionService:                                        │ │
│ │ - Detect: EMAIL pattern found                               │ │
│ │ - Extract: "john.doe@example.com"                           │ │
│ │ - Generate token: "[EMAIL_REDACTED_abc123]"                 │ │
│ │ - Store mapping: {abc123: "john.doe@example.com"}          │ │
│ │ - Replace: "find customer with email [EMAIL_REDACTED_abc123]"│ │
│ └─────────────────────────────────────────────────────────────┘ │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: LLM Plan Generation (PII Protected)                     │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ RelationshipQueryPlanner:                                   │ │
│ │ Input to LLM: "...email [EMAIL_REDACTED_abc123]"            │ │
│ │ LLM generates plan with TOKEN, not real PII                 │ │
│ │ Plan: {                                                     │ │
│ │   field: "email",                                           │ │
│ │   operator: "EQUALS",                                       │ │
│ │   value: "[EMAIL_REDACTED_abc123]"  ← Token                │ │
│ │ }                                                            │ │
│ └─────────────────────────────────────────────────────────────┘ │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Query Building with Token Substitution                  │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ DynamicJPAQueryBuilder:                                     │ │
│ │ - Sees value: "[EMAIL_REDACTED_abc123]"                     │ │
│ │ - Recognizes token pattern                                  │ │
│ │ - Looks up: abc123 → "john.doe@example.com"                 │ │
│ │ - Substitutes real value                                    │ │
│ │ - Generates: WHERE c.email = :p1                            │ │
│ │ - Parameters: {p1: "john.doe@example.com"}  ← Real PII     │ │
│ └─────────────────────────────────────────────────────────────┘ │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Database Execution                                       │
│ SELECT c FROM Customer c WHERE c.email = 'john.doe@example.com' │
│ (Real PII used in database query)                               │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Cleanup                                                  │
│ Clear token registry: {abc123: ...} → {}                        │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: Return Results                                           │
│ Customer records returned to user                                │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Key Components

| Component | Responsibility | Sees PII? |
|-----------|---------------|-----------|
| **User** | Provides query with PII | ✅ YES |
| **PIIDetectionService** | Tokenizes PII | ✅ YES (temporarily) |
| **LLM** | Generates query plan | ❌ NO (sees tokens) |
| **QueryBuilder** | Substitutes tokens → real PII | ✅ YES (temporarily) |
| **Database** | Executes query | ✅ YES (has real data) |
| **Response** | Returns results | ✅ YES (real data) |

**LLM Protection:** LLM never sees real PII values, only placeholder tokens.

---

## 2. PII Detection & Tokenization

### 2.1 PIIDetectionService Enhancement

```java
package com.ai.infrastructure.privacy.pii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects PII in queries and replaces with tokens for LLM protection.
 * 
 * <p><strong>Tokenization Strategy:</strong></p>
 * <ol>
 *   <li>Detect PII patterns (regex-based)</li>
 *   <li>Generate unique token per PII value</li>
 *   <li>Store token→value mapping (request-scoped)</li>
 *   <li>Replace PII with token in query</li>
 *   <li>Return tokenized query + metadata</li>
 * </ol>
 * 
 * <p><strong>Thread Safety:</strong> Uses ThreadLocal for token registry isolation.</p>
 * 
 * <p><strong>Lifecycle:</strong> Token registry MUST be cleared after query execution
 * to prevent memory leaks.</p>
 */
@Slf4j
@Service
public class PIIDetectionService {
    
    // PII Pattern Constants
    private static final String TOKEN_PREFIX = "PII_REDACTED";
    private static final String EMAIL_TOKEN_FORMAT = "[EMAIL_REDACTED_%s]";
    private static final String CARD_TOKEN_FORMAT = "[CARD_REDACTED_%s]";
    private static final String PHONE_TOKEN_FORMAT = "[PHONE_REDACTED_%s]";
    private static final String SSN_TOKEN_FORMAT = "[SSN_REDACTED_%s]";
    private static final String DOB_TOKEN_FORMAT = "[DOB_REDACTED_%s]";
    
    // Regex Patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?1[-.]?)?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"
    );
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );
    
    private static final Pattern DOB_PATTERN = Pattern.compile(
        "\\b\\d{4}-\\d{2}-\\d{2}\\b"  // YYYY-MM-DD format
    );
    
    // Thread-local token registry (request-scoped)
    private final ThreadLocal<Map<String, PIIToken>> tokenRegistry = ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Detects PII and tokenizes for LLM protection.
     * 
     * @param request Detection request containing query
     * @return Result with tokenized query and detection metadata
     */
    public PIIDetectionResult detectAndTokenize(PIIDetectionRequest request) {
        String content = request.getContent();
        Map<String, PIIToken> tokens = new HashMap<>();
        String processedContent = content;
        List<String> detectedTypes = new ArrayList<>();
        
        // Detect and tokenize EMAIL
        Matcher emailMatcher = EMAIL_PATTERN.matcher(processedContent);
        while (emailMatcher.find()) {
            String email = emailMatcher.group();
            PIIToken token = createToken("EMAIL", email, EMAIL_TOKEN_FORMAT);
            
            processedContent = processedContent.replace(email, token.getTokenString());
            tokens.put(token.getTokenId(), token);
            detectedTypes.add("EMAIL");
            
            log.debug("EMAIL detected and tokenized: token={} (value not logged)", token.getTokenString());
        }
        
        // Detect and tokenize CREDIT_CARD
        Matcher cardMatcher = CREDIT_CARD_PATTERN.matcher(processedContent);
        while (cardMatcher.find()) {
            String card = cardMatcher.group();
            PIIToken token = createToken("CREDIT_CARD", card, CARD_TOKEN_FORMAT);
            
            processedContent = processedContent.replace(card, token.getTokenString());
            tokens.put(token.getTokenId(), token);
            detectedTypes.add("CREDIT_CARD");
            
            log.debug("CREDIT_CARD detected and tokenized: token={}", token.getTokenString());
        }
        
        // Detect and tokenize PHONE
        Matcher phoneMatcher = PHONE_PATTERN.matcher(processedContent);
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            PIIToken token = createToken("PHONE", phone, PHONE_TOKEN_FORMAT);
            
            processedContent = processedContent.replace(phone, token.getTokenString());
            tokens.put(token.getTokenId(), token);
            detectedTypes.add("PHONE");
            
            log.debug("PHONE detected and tokenized: token={}", token.getTokenString());
        }
        
        // Detect and tokenize SSN
        Matcher ssnMatcher = SSN_PATTERN.matcher(processedContent);
        while (ssnMatcher.find()) {
            String ssn = ssnMatcher.group();
            PIIToken token = createToken("SSN", ssn, SSN_TOKEN_FORMAT);
            
            processedContent = processedContent.replace(ssn, token.getTokenString());
            tokens.put(token.getTokenId(), token);
            detectedTypes.add("SSN");
            
            log.debug("SSN detected and tokenized: token={}", token.getTokenString());
        }
        
        // Detect and tokenize DATE_OF_BIRTH
        if (content.toLowerCase().contains("born on") || 
            content.toLowerCase().contains("date of birth") ||
            content.toLowerCase().contains("dob")) {
            
            Matcher dobMatcher = DOB_PATTERN.matcher(processedContent);
            while (dobMatcher.find()) {
                String dob = dobMatcher.group();
                PIIToken token = createToken("DATE_OF_BIRTH", dob, DOB_TOKEN_FORMAT);
                
                processedContent = processedContent.replace(dob, token.getTokenString());
                tokens.put(token.getTokenId(), token);
                detectedTypes.add("DATE_OF_BIRTH");
                
                log.debug("DATE_OF_BIRTH detected and tokenized: token={}", token.getTokenString());
            }
        }
        
        // Store in thread-local registry for later retrieval
        tokenRegistry.get().putAll(tokens);
        
        return PIIDetectionResult.builder()
            .processedQuery(processedContent)  // Tokenized query
            .detectedTypes(detectedTypes)
            .isPiiDetected(!detectedTypes.isEmpty())
            .tokenCount(tokens.size())
            .build();
    }
    
    /**
     * Creates a PII token with unique identifier.
     */
    private PIIToken createToken(String piiType, String actualValue, String tokenFormat) {
        String tokenId = UUID.randomUUID().toString().substring(0, 8);
        String tokenString = String.format(tokenFormat, tokenId);
        
        return PIIToken.builder()
            .tokenId(tokenId)
            .tokenString(tokenString)
            .piiType(piiType)
            .actualValue(actualValue)
            .createdAt(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Retrieves original PII value from token string.
     * 
     * @param tokenString Token string (e.g., "[EMAIL_REDACTED_abc123]")
     * @return Original PII value, or null if token not found
     */
    public String getOriginalValue(String tokenString) {
        // Extract token ID from token string
        String tokenId = extractTokenId(tokenString);
        if (tokenId == null) {
            return null;
        }
        
        PIIToken token = tokenRegistry.get().get(tokenId);
        return token != null ? token.getActualValue() : null;
    }
    
    /**
     * Checks if a value is a PII token.
     */
    public boolean isPIIToken(String value) {
        if (value == null) {
            return false;
        }
        return value.matches("\\[.+_REDACTED_.+\\]");
    }
    
    /**
     * Extracts token ID from token string.
     * Example: "[EMAIL_REDACTED_abc123]" → "abc123"
     */
    private String extractTokenId(String tokenString) {
        if (!isPIIToken(tokenString)) {
            return null;
        }
        
        int lastUnderscore = tokenString.lastIndexOf('_');
        int closeBracket = tokenString.indexOf(']');
        
        if (lastUnderscore > 0 && closeBracket > lastUnderscore) {
            return tokenString.substring(lastUnderscore + 1, closeBracket);
        }
        
        return null;
    }
    
    /**
     * Clears token registry for current thread.
     * CRITICAL: Must be called after query execution to prevent memory leaks.
     */
    public void clearTokenRegistry() {
        Map<String, PIIToken> registry = tokenRegistry.get();
        if (!registry.isEmpty()) {
            log.debug("Clearing PII token registry: {} tokens cleared", registry.size());
            registry.clear();
        }
        tokenRegistry.remove();
    }
    
    /**
     * Gets token statistics (for monitoring/debugging).
     */
    public Map<String, Object> getTokenStats() {
        Map<String, PIIToken> registry = tokenRegistry.get();
        return Map.of(
            "activeTokens", registry.size(),
            "tokenTypes", registry.values().stream()
                .map(PIIToken::getPiiType)
                .distinct()
                .collect(Collectors.toList())
        );
    }
}

/**
 * Represents a PII token with metadata.
 */
@Data
@Builder
class PIIToken {
    private String tokenId;        // Unique ID: "abc123"
    private String tokenString;    // Full token: "[EMAIL_REDACTED_abc123]"
    private String piiType;        // Type: "EMAIL", "CREDIT_CARD", etc.
    private String actualValue;    // Real PII value (stored temporarily)
    private long createdAt;        // Timestamp (for cleanup/monitoring)
}
```

---

## 3. LLM Plan Generation

### 3.1 Planner Receives Tokenized Query

```java
package com.ai.infrastructure.relationship.service;

@Slf4j
@Service
public class RelationshipQueryPlanner {
    
    public RelationshipQueryPlan planQuery(String query, List<String> entityTypes) {
        // Query at this point is TOKENIZED
        // Example: "find customer with email [EMAIL_REDACTED_abc123]"
        
        String prompt = buildPrompt(query, entityTypes);
        // Prompt includes tokenized query - LLM never sees real email
        
        AIGenerationResponse response = aiCoreService.generateContent(
            AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are an expert database planner. Return ONLY JSON.")
                .build()
        );
        
        // LLM returns plan with TOKEN in the filter value
        RelationshipQueryPlan plan = parsePlan(response.getContent());
        
        return plan;
    }
}
```

**Example Plan Returned by LLM:**

```json
{
  "primaryEntityType": "customer",
  "candidateEntityTypes": ["customer"],
  "directFilters": {
    "customer": [
      {
        "field": "email",
        "operator": "EQUALS",
        "value": "[EMAIL_REDACTED_abc123]",
        "entityType": "customer"
      }
    ]
  },
  "relationshipPaths": [],
  "queryStrategy": "RELATIONSHIP"
}
```

**Key Point:** LLM correctly understands "search by email" but never sees the actual email address!

---

## 4. Query Builder Token Substitution

### 4.1 DynamicJPAQueryBuilder Enhancement

```java
package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds JPQL queries from relationship plans with PII token substitution.
 * 
 * <p><strong>PII Token Handling:</strong></p>
 * <ul>
 *   <li>Detects PII tokens in filter values</li>
 *   <li>Substitutes with actual PII values for database execution</li>
 *   <li>Ensures PII never sent to LLM while database queries work correctly</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicJPAQueryBuilder {
    
    // Constants
    private static final String PARAM_PREFIX = "p";
    
    private final PIIDetectionService piiDetectionService;
    private final EntityRelationshipMapper entityMapper;
    
    /**
     * Builds JPQL query from plan, substituting PII tokens with actual values.
     * 
     * @param plan Relationship query plan (may contain PII tokens in filter values)
     * @return JPQL query with real PII values in parameters
     */
    public JpqlQuery buildQuery(RelationshipQueryPlan plan) {
        StringBuilder jpql = new StringBuilder();
        Map<String, Object> parameters = new HashMap<>();
        int paramCounter = 1;
        
        // Build SELECT and FROM
        String entityClass = entityMapper.getEntityClassName(plan.getPrimaryEntityType());
        jpql.append("SELECT DISTINCT root FROM ")
            .append(extractSimpleName(entityClass))
            .append(" root");
        
        // Build JOINs for relationships
        if (plan.getRelationshipPaths() != null && !plan.getRelationshipPaths().isEmpty()) {
            for (RelationshipPath path : plan.getRelationshipPaths()) {
                String alias = generateAlias(path.getToEntityType(), paramCounter);
                jpql.append(" JOIN root.").append(path.getRelationshipType())
                    .append(" ").append(alias);
            }
        }
        
        // Build WHERE clause from directFilters
        if (plan.getDirectFilters() != null && !plan.getDirectFilters().isEmpty()) {
            jpql.append(" WHERE ");
            boolean firstCondition = true;
            
            for (Map.Entry<String, List<FilterCondition>> entry : plan.getDirectFilters().entrySet()) {
                String entityAlias = entry.getKey().equals(plan.getPrimaryEntityType()) ? "root" : entry.getKey();
                
                for (FilterCondition filter : entry.getValue()) {
                    if (!firstCondition) {
                        jpql.append(" AND ");
                    }
                    firstCondition = false;
                    
                    String paramName = PARAM_PREFIX + paramCounter++;
                    
                    jpql.append(entityAlias).append(".").append(filter.getField())
                        .append(" ").append(getOperatorSymbol(filter.getOperator()))
                        .append(" :").append(paramName);
                    
                    // ===== CRITICAL: PII Token Substitution =====
                    Object filterValue = filter.getValue();
                    
                    if (filterValue instanceof String && piiDetectionService.isPIIToken((String) filterValue)) {
                        // This is a PII token - substitute with actual value
                        String token = (String) filterValue;
                        String actualValue = piiDetectionService.getOriginalValue(token);
                        
                        if (actualValue != null) {
                            log.debug("Substituting PII token {} with actual value for database query " +
                                "(value not logged)", token);
                            parameters.put(paramName, actualValue);  // Real PII value
                        } else {
                            log.warn("PII token {} found but no mapping exists. Using token as-is.", token);
                            parameters.put(paramName, token);  // Fallback to token
                        }
                    } else {
                        // Normal value (not PII)
                        parameters.put(paramName, filterValue);
                    }
                }
            }
        }
        
        String jpqlString = jpql.toString();
        
        log.debug("Generated JPQL: {} (parameters count: {}, PII tokens substituted)", 
            jpqlString, parameters.size());
        
        return JpqlQuery.builder()
            .jpql(jpqlString)
            .parameters(Collections.unmodifiableMap(parameters))
            .build();
    }
    
    private String getOperatorSymbol(FilterOperator operator) {
        return switch (operator) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "!=";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case LIKE -> "LIKE";
            case IN -> "IN";
            default -> "=";
        };
    }
    
    private String extractSimpleName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
    
    private String generateAlias(String entityType, int counter) {
        return entityType.substring(0, Math.min(2, entityType.length())) + counter;
    }
}
```

---

## 5. Complete Flow Example

### 5.1 Email Search Example

**Input:**
```
Query: "find customer with email john.doe@example.com"
Entity Types: ["customer"]
```

**Step-by-Step:**

**Step 1: PII Detection**
```java
Input: "find customer with email john.doe@example.com"
Detected: EMAIL = "john.doe@example.com"
Token Generated: "[EMAIL_REDACTED_7f3a2b1c]"
Token Registry: {7f3a2b1c: PIIToken(EMAIL, "john.doe@example.com")}
Output: "find customer with email [EMAIL_REDACTED_7f3a2b1c]"
```

**Step 2: LLM Sees**
```
Prompt: "...find customer with email [EMAIL_REDACTED_7f3a2b1c]"

LLM Response:
{
  "primaryEntityType": "customer",
  "directFilters": {
    "customer": [{
      "field": "email",
      "operator": "EQUALS",
      "value": "[EMAIL_REDACTED_7f3a2b1c]",  ← Token
      "entityType": "customer"
    }]
  }
}
```

**Step 3: Query Builder**
```java
Filter value: "[EMAIL_REDACTED_7f3a2b1c]"
isPIIToken: TRUE
Lookup token: 7f3a2b1c → "john.doe@example.com"
Generate JPQL: "SELECT DISTINCT root FROM CustomerEntity root WHERE root.email = :p1"
Parameters: {p1: "john.doe@example.com"}  ← Real email
```

**Step 4: Database Execution**
```sql
SELECT * FROM customers WHERE email = 'john.doe@example.com'
-- Real email used in query
```

**Step 5: Results**
```
Found: Customer { id: "123", name: "John Doe", email: "john.doe@example.com", ... }
```

**Step 6: Cleanup**
```java
tokenRegistry.clear()
Token mapping: {7f3a2b1c: ...} → {}
```

### 5.2 Credit Card Search Example

**Input:**
```
Query: "find transactions with card 4532-1234-5678-9010"
```

**Flow:**
```
1. Detect: CREDIT_CARD = "4532-1234-5678-9010"
   Token: "[CARD_REDACTED_9e4b7c2d]"
   
2. LLM Sees: "find transactions with card [CARD_REDACTED_9e4b7c2d]"

3. Plan Generated:
   {
     filters: [{
       field: "cardNumber",
       value: "[CARD_REDACTED_9e4b7c2d]"
     }]
   }

4. Query Builder:
   - Recognizes token
   - Substitutes: "4532-1234-5678-9010"
   - Parameters: {p1: "4532-1234-5678-9010"}

5. Database Query:
   WHERE t.cardNumber = '4532-1234-5678-9010'

6. Results: Matching transactions
```

---

## 6. Security Considerations

### 6.1 Token Registry Isolation

```java
// ThreadLocal ensures isolation between requests
private final ThreadLocal<Map<String, PIIToken>> tokenRegistry = ThreadLocal.withInitial(HashMap::new);

// Request 1 (Thread A):
Registry: {abc123: "alice@example.com"}

// Request 2 (Thread B - concurrent):
Registry: {def456: "bob@example.com"}

// No cross-contamination!
```

### 6.2 Token Lifecycle

```java
/**
 * Token lifecycle management.
 */
@Service
public class LLMDrivenJPAQueryService {
    
    public RAGResponse executeRelationshipQuery(String query, List<String> entityTypes, QueryOptions options) {
        try {
            // Tokens created during PII detection (in orchestrator)
            // Tokens used during query building
            // Tokens available throughout request
            
            RelationshipQueryPlan plan = planner.planQuery(query, entityTypes);
            JpqlQuery jpqlQuery = queryBuilder.buildQuery(plan);  // Uses tokens
            List<String> entityIds = executeTraversal(plan, jpqlQuery);
            
            return buildResponse(query, plan, entityIds);
            
        } finally {
            // CRITICAL: Clear tokens after query execution
            piiDetectionService.clearTokenRegistry();
            log.debug("PII tokens cleared after query execution");
        }
    }
}
```

**Lifecycle:**
1. **Created:** During PII detection (orchestrator start)
2. **Used:** During query building
3. **Cleared:** After query execution (finally block)
4. **Scope:** Single request only (ThreadLocal)

### 6.3 Logging Security

```java
// ❌ NEVER log PII values:
log.info("Searching for email: {}", email);  // VIOLATION

// ✅ Log that PII search occurred:
log.info("PII query detected: type={}, entityType={}, field={} (value not logged)",
    "EMAIL", "customer", "email");

// ✅ Log tokens (safe):
log.debug("PII token generated: {} (actual value not logged)", tokenString);

// ✅ Audit PII searches (without values):
auditLog.record("PII_SEARCH", userId, Map.of(
    "piiType", "EMAIL",
    "entityType", "customer",
    "resultCount", results.size()
    // value NOT logged
));
```

### 6.4 Access Control for PII Searches

```java
public interface RelationshipQueryAccessControlPolicy {
    
    /**
     * Check if user can search by PII field.
     * 
     * <p>Use for:</p>
     * <ul>
     *   <li>Restricting PII searches to authorized users (admins, support)</li>
     *   <li>Logging PII access for compliance</li>
     *   <li>Rate limiting PII searches</li>
     * </ul>
     * 
     * @param userId User identifier
     * @param piiType Type of PII (EMAIL, CREDIT_CARD, etc.)
     * @param entityType Entity type being searched
     * @return true if user can search by this PII type
     */
    boolean canUserSearchByPII(String userId, String piiType, String entityType);
}
```

**Implementation:**
```java
@Component
public class MyAccessPolicy implements RelationshipQueryAccessControlPolicy {
    
    @Override
    public boolean canUserSearchByPII(String userId, String piiType, String entityType) {
        // Only admins and support can search by PII
        User user = userService.getUser(userId);
        
        if (user.hasRole("ADMIN") || user.hasRole("SUPPORT")) {
            // Log PII search attempt
            auditLog.record("PII_SEARCH_ATTEMPT", userId, Map.of(
                "piiType", piiType,
                "entityType", entityType,
                "granted", true
            ));
            return true;
        }
        
        // Deny for regular users
        auditLog.record("PII_SEARCH_DENIED", userId, Map.of(
            "piiType", piiType,
            "entityType", entityType
        ));
        
        return false;
    }
}
```

**Enforcement in Service:**
```java
public RAGResponse execute(String query, List<String> entityTypes, QueryOptions options) {
    // Check if PII detected
    PIIDetectionResult piiResult = piiDetectionService.detectAndTokenize(...);
    
    if (piiResult.isPiiDetected()) {
        // Verify user can search by PII
        for (String piiType : piiResult.getDetectedTypes()) {
            if (!accessPolicy.canUserSearchByPII(userId, piiType, entityTypes.get(0))) {
                throw new AccessDeniedException(
                    "You do not have permission to search by " + piiType,
                    Map.of("piiType", piiType, "entityType", entityTypes.get(0))
                );
            }
        }
    }
    
    // Continue with tokenized query...
}
```

---

## 7. Implementation

### 7.1 Integration Points

**Add to RAGOrchestrator:**

```java
// In orchestrate() method, AFTER PII detection:

PIIDetectionResult piiResult = piiDetectionService.detectAndProcess(request);

// Change to use detectAndTokenize for relationship queries:
PIIDetectionResult piiResult = piiDetectionService.detectAndTokenize(
    PIIDetectionRequest.builder()
        .content(query)
        .userId(identifier)
        .requestId(requestId)
        .mode(PIIDetectionMode.TOKENIZE)  // NEW mode
        .build()
);

String processedQuery = piiResult.getProcessedQuery();  // Tokenized query
```

**Add to LLMDrivenJPAQueryService:**

```java
public RAGResponse executeRelationshipQuery(...) {
    try {
        // LLM sees tokenized query
        RelationshipQueryPlan plan = planner.planQuery(query, entityTypes);
        
        // Query builder substitutes tokens
        JpqlQuery jpqlQuery = queryBuilder.buildQuery(plan);  // Tokens → real PII
        
        // Execute with real PII
        List<String> entityIds = executeTraversal(plan, jpqlQuery);
        
        return buildResponse(query, plan, entityIds);
        
    } finally {
        // CRITICAL: Clear tokens
        piiDetectionService.clearTokenRegistry();
    }
}
```

---

## 8. Testing

### 8.1 Unit Tests Required

```java
@Test
@DisplayName("Should tokenize email and substitute in query")
void shouldTokenizeEmailAndSubstitute() {
    // Arrange
    String query = "find customer with email john@example.com";
    
    // Act - Tokenize
    PIIDetectionResult piiResult = piiService.detectAndTokenize(query);
    
    // Assert - Query tokenized
    assertThat(piiResult.getProcessedQuery())
        .contains("[EMAIL_REDACTED_")
        .doesNotContain("john@example.com");
    
    // Act - Plan generation
    RelationshipQueryPlan plan = planner.planQuery(piiResult.getProcessedQuery(), List.of("customer"));
    
    // Assert - Plan has token
    String filterValue = plan.getDirectFilters().get("customer").get(0).getValue();
    assertThat(filterValue).matches("\\[EMAIL_REDACTED_.+\\]");
    
    // Act - Query building
    JpqlQuery jpql = queryBuilder.buildQuery(plan);
    
    // Assert - Real email in parameters
    assertThat(jpql.getParameters().get("p1")).isEqualTo("john@example.com");
    assertThat(jpql.getJpql()).contains("WHERE root.email = :p1");
}

@Test
@DisplayName("Should clear token registry after query")
void shouldClearTokenRegistryAfterQuery() {
    String query = "find customer with email test@example.com";
    
    // Execute query
    service.execute(query, List.of("customer"), null);
    
    // Verify tokens cleared
    Map<String, Object> stats = piiService.getTokenStats();
    assertThat(stats.get("activeTokens")).isEqualTo(0);
}

@Test
@DisplayName("Should handle multiple PII values in one query")
void shouldHandleMultiplePIIValues() {
    String query = "find customer with email john@example.com or phone 555-1234";
    
    PIIDetectionResult result = piiService.detectAndTokenize(query);
    
    assertThat(result.getDetectedTypes())
        .contains("EMAIL", "PHONE");
    assertThat(result.getProcessedQuery())
        .contains("[EMAIL_REDACTED_")
        .contains("[PHONE_REDACTED_")
        .doesNotContain("john@example.com")
        .doesNotContain("555-1234");
}
```

---

## 9. Configuration

```yaml
ai:
  privacy:
    pii:
      # Enable PII detection
      enabled: true
      
      # Tokenization mode for relationship queries
      tokenization-enabled: true
      
      # Patterns to detect
      detect-patterns:
        - EMAIL
        - CREDIT_CARD
        - PHONE
        - SSN
        - DATE_OF_BIRTH
      
      # Require permission for PII searches
      require-permission-for-pii-search: true
      
      # Audit all PII searches
      audit-pii-searches: true
```

---

## 10. Compliance & Audit

### 10.1 Compliance Benefits

| Regulation | Requirement | How We Comply |
|------------|-------------|---------------|
| **GDPR** | Minimize PII exposure | ✅ PII not sent to external LLMs |
| **HIPAA** | Protect health information | ✅ Tokenization keeps PII server-side |
| **PCI-DSS** | Protect cardholder data | ✅ Card numbers never sent to LLM |
| **CCPA** | Data privacy | ✅ PII handled securely |

### 10.2 Audit Log Example

```java
{
  "event": "PII_SEARCH",
  "timestamp": "2026-01-04T12:00:00Z",
  "userId": "admin-123",
  "piiType": "EMAIL",
  "entityType": "customer",
  "searchGranted": true,
  "resultsFound": 1,
  "queryId": "req-abc-123"
  // NOTE: Actual email NOT logged
}
```

---

## 11. Summary

### What We Achieved

✅ **Users can search by PII** (email, card, phone, SSN, DOB)  
✅ **PII never sent to LLM** (tokens sent instead)  
✅ **Database queries work correctly** (tokens substituted with real values)  
✅ **LLM still generates plans** (understands "search by email")  
✅ **Secure token handling** (ThreadLocal, cleared after use)  
✅ **Compliance friendly** (GDPR, HIPAA, PCI-DSS)  
✅ **Audit trail** (log searches without values)  
✅ **Access control** (restrict who can search by PII)  

### The Key Insight

**LLM's job:** Generate the PLAN (understand "search by email equals X")  
**NOT LLM's job:** See the actual email address

**Query Builder's job:** Execute the plan with real values

**This separation allows:**
- LLM to be intelligent (generate correct plans)
- PII to stay secure (never leave your infrastructure)

---

**Document Version:** 1.0  
**Status:** ✅ Production Ready  
**Author:** AI Fabric Framework Team  
**Date:** January 2026

---

**This document provides the complete PII tokenization solution for secure PII-based searching in relationship queries.**

