# 📋 PHASE 1 Implementation Guide - Document-Level Access Control

**Document Purpose:** Step-by-step code implementation for Phase 1  
**Date:** November 8, 2025  
**Duration:** 2 weeks (5 business days of work)  
**Status:** Implementation Ready

---

## 🎯 Phase 1 Objective

**Implement document-level access control filtering to ensure users can only access documents they have permission to view.**

### Current Problem
```
User (Marketing) queries: "Show me financial reports"
    ↓
[System finds 5 financial reports] (All accessible)
    ↓
[Returns all 5 reports to user] ❌ SECURITY VIOLATION
    └─ User saw confidential documents!
```

### After Phase 1 (FIXED)
```
User (Marketing) queries: "Show me financial reports"
    ↓
[System finds 5 financial reports]
    ↓
[Filters by user permissions]
    ↓
[Returns only accessible documents] ✅
```

---

## 📅 Implementation Timeline

```
Week 1:
  Day 1-2: Design & Planning
  Day 3-5: Core Implementation
  Day 6-8: Integration & Testing
  Day 9-10: Security Review & Config

Week 2:
  Day 1-2: Final Testing
  Day 3: Staging Deployment
  Day 4-5: Ready for Production
```

---

## 🔧 STEP 1: Extend RAGService with User Context

### 1.1: Create AIUserContext Class

**File:** `/ai-infrastructure-module/.../context/AIUserContext.java`

```java
package com.ai.infrastructure.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * User context for access control checks during document retrieval.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIUserContext {
    private String userId;
    private String userName;
    private List<String> roles;
    private Map<String, Object> attributes;
    private int clearanceLevel;
    private String department;
    private List<String> allowedGroups;
    private String ipAddress;
    private long timestamp;
    private String requestId;
    
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    public boolean hasClearanceLevel(int requiredLevel) {
        return clearanceLevel >= requiredLevel;
    }
}
```

### 1.2: Update RAGService Signature

**File:** `/ai-infrastructure-module/.../rag/RAGService.java`

```java
// Add new parameter to method signature
public AISearchResponse performRAGQuery(
        String query, 
        String entityType, 
        int limit,
        AIUserContext userContext) {  // ← NEW
    
    // ... existing code ...
    
    // After retrieval, before returning
    List<AIDocument> filteredDocs = filterDocumentsByUserAccess(
        searchResponse.getDocuments(),
        userContext
    );
    
    searchResponse.setDocuments(filteredDocs);
    return searchResponse;
}

// Backward compatibility
public AISearchResponse performRAGQuery(String query, String entityType, int limit) {
    return performRAGQuery(query, entityType, limit,
        AIUserContext.builder()
            .userId("anonymous")
            .clearanceLevel(0)
            .roles(Collections.singletonList("ANONYMOUS"))
            .build()
    );
}
```

---

## 🔐 STEP 2: Add Document Access Control Model

### 2.1: Create DocumentAccessControl Class

**File:** `/ai-infrastructure-module/.../model/DocumentAccessControl.java`

```java
package com.ai.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Access control rules for a document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAccessControl {
    private List<String> allowedRoles;
    private List<String> deniedRoles;
    private int requiredClearanceLevel;
    private List<String> allowedDepartments;
    private List<String> allowedGroups;
    private String classification;
    private Long accessStartTime;
    private Long accessEndTime;
    private String ownerUserId;
    private List<String> explicitlyAllowedUsers;
    private List<String> explicitlyDeniedUsers;
    
    public boolean canUserAccess(AIUserContext userContext) {
        if (userContext == null) return false;
        
        // Check explicit denial
        if (explicitlyDeniedUsers != null && 
            explicitlyDeniedUsers.contains(userContext.getUserId())) {
            return false;
        }
        
        // Check explicit allow
        if (explicitlyAllowedUsers != null && 
            explicitlyAllowedUsers.contains(userContext.getUserId())) {
            return true;
        }
        
        // Check owner-only access
        if (ownerUserId != null) {
            return ownerUserId.equals(userContext.getUserId());
        }
        
        // Check role access
        if (!checkRoleAccess(userContext)) return false;
        
        // Check clearance level
        if (!checkClearanceLevel(userContext)) return false;
        
        // Check department access
        if (!checkDepartmentAccess(userContext)) return false;
        
        // Check temporal access
        if (!checkTemporalAccess()) return false;
        
        return true;
    }
    
    private boolean checkRoleAccess(AIUserContext userContext) {
        if (deniedRoles != null && !deniedRoles.isEmpty()) {
            boolean hasDeniedRole = userContext.getRoles().stream()
                .anyMatch(deniedRoles::contains);
            if (hasDeniedRole) return false;
        }
        
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            return userContext.getRoles().stream()
                .anyMatch(allowedRoles::contains);
        }
        
        return true;
    }
    
    private boolean checkClearanceLevel(AIUserContext userContext) {
        return userContext.hasClearanceLevel(requiredClearanceLevel);
    }
    
    private boolean checkDepartmentAccess(AIUserContext userContext) {
        if (allowedDepartments == null || allowedDepartments.isEmpty()) {
            return true;
        }
        return userContext.getDepartment() != null && 
               allowedDepartments.contains(userContext.getDepartment());
    }
    
    private boolean checkTemporalAccess() {
        long now = System.currentTimeMillis();
        if (accessStartTime != null && now < accessStartTime) return false;
        if (accessEndTime != null && now > accessEndTime) return false;
        return true;
    }
}
```

### 2.2: Update AIDocument Model

**File:** `/ai-infrastructure-module/.../model/AIDocument.java`

```java
@Data
public class AIDocument {
    // ... existing fields ...
    private DocumentAccessControl accessControl;  // ← ADD
    
    public boolean isAccessibleTo(AIUserContext userContext) {
        if (accessControl == null) return true;
        return accessControl.canUserAccess(userContext);
    }
}
```

---

## 🔍 STEP 3: Implement Filtering Logic

### 3.1: Add Filtering Method to RAGService

```java
private List<AIDocument> filterDocumentsByUserAccess(
        List<AIDocument> documents,
        AIUserContext userContext) {
    
    if (documents == null || documents.isEmpty()) {
        return documents;
    }
    
    List<AIDocument> filtered = new ArrayList<>();
    
    for (AIDocument doc : documents) {
        try {
            if (doc.isAccessibleTo(userContext)) {
                filtered.add(doc);
                logAccessGranted(doc, userContext);
            } else {
                logAccessDenied(doc, userContext);
            }
        } catch (Exception e) {
            log.error("Error checking document access", e);
            logAccessDenied(doc, userContext);
        }
    }
    
    return filtered;
}

private void logAccessGranted(AIDocument doc, AIUserContext userContext) {
    auditService.logDocumentAccess(
        AIDocumentAccessAuditLog.builder()
            .documentId(doc.getId())
            .userId(userContext.getUserId())
            .accessGranted(true)
            .timestamp(System.currentTimeMillis())
            .build()
    );
}

private void logAccessDenied(AIDocument doc, AIUserContext userContext) {
    auditService.logDocumentAccess(
        AIDocumentAccessAuditLog.builder()
            .documentId(doc.getId())
            .userId(userContext.getUserId())
            .accessGranted(false)
            .timestamp(System.currentTimeMillis())
            .build()
    );
}
```

---

## 🌐 STEP 4: Update Controller Layer

### 4.1: Update RAG Controller

**File:** `/backend/src/main/java/com/easyluxury/controller/RagController.java`

```java
@PostMapping("/api/rag/query")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<AISearchResponse> ragQuery(
        @RequestBody RAGQueryRequest request,
        Authentication authentication) {  // ← ADD
    
    AIUserContext userContext = extractUserContext(authentication);
    
    AISearchResponse response = ragService.performRAGQuery(
        request.getQuery(),
        request.getEntityType(),
        request.getLimit(),
        userContext  // ← PASS
    );
    return ResponseEntity.ok(response);
}

private AIUserContext extractUserContext(Authentication auth) {
    UserDetails userDetails = (UserDetails) auth.getPrincipal();
    
    List<String> roles = auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toList());
    
    UserProfile profile = userProfileService.getUserProfile(userDetails.getUsername());
    
    return AIUserContext.builder()
        .userId(userDetails.getUsername())
        .roles(roles)
        .clearanceLevel(profile.getClearanceLevel())
        .department(profile.getDepartment())
        .attributes(profile.getAttributes())
        .timestamp(System.currentTimeMillis())
        .requestId(generateRequestId())
        .build();
}
```

---

## 🗄️ STEP 5: Database Migration

**File:** `/backend/src/main/resources/db/migration/V1.X__add_document_access_control.sql`

```sql
ALTER TABLE ai_documents ADD COLUMN access_control_json JSONB DEFAULT NULL;
CREATE INDEX idx_document_access_control ON ai_documents USING GIN(access_control_json);

ALTER TABLE ai_documents ADD COLUMN access_control_version INT DEFAULT 1;
ALTER TABLE ai_documents ADD COLUMN access_control_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE ai_document_access_audit (
    id SERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    access_granted BOOLEAN NOT NULL,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES ai_documents(id)
);

CREATE INDEX idx_audit_document_user ON ai_document_access_audit(document_id, user_id);
```

---

## ✅ STEP 6: Testing

### 6.1: Unit Tests

```java
@Test
public void testDocumentFilteringByRole() {
    AIUserContext user = AIUserContext.builder()
        .userId("user1")
        .roles(Arrays.asList("USER"))
        .clearanceLevel(1)
        .build();
    
    DocumentAccessControl control = DocumentAccessControl.builder()
        .allowedRoles(Arrays.asList("ADMIN"))
        .requiredClearanceLevel(2)
        .build();
    
    AIDocument doc = new AIDocument();
    doc.setAccessControl(control);
    
    assertFalse(doc.isAccessibleTo(user));
}

@Test
public void testDepartmentRestriction() {
    AIUserContext user = AIUserContext.builder()
        .userId("user1")
        .department("Sales")
        .build();
    
    DocumentAccessControl control = DocumentAccessControl.builder()
        .allowedDepartments(Arrays.asList("Finance"))
        .build();
    
    assertFalse(control.canUserAccess(user));
}
```

### 6.2: Integration Tests

```java
@SpringBootTest
public class DocumentAccessControlIntegrationTest {
    
    @Test
    public void testRAGQueryFiltersDocuments() throws Exception {
        mockMvc.perform(
            post("/api/rag/query")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(queryJson)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.length()").value(greaterThan(0)));
    }
}
```

---

## 📋 DEPLOYMENT CHECKLIST

- [ ] All code reviewed
- [ ] Unit tests: >90% coverage
- [ ] Integration tests: All passing
- [ ] Security review: Approved
- [ ] Performance: <50ms overhead
- [ ] Database migration: Tested
- [ ] Staging deployment: Successful
- [ ] Production checklist reviewed
- [ ] Rollback plan: Ready
- [ ] Monitoring: Configured

---

## 🎯 SUCCESS CRITERIA

- ✅ Documents filtered by user permission
- ✅ Zero unauthorized access
- ✅ 100% audit logging
- ✅ <50ms performance overhead
- ✅ Security team approval

---

**Status:** ✅ Phase 1 Implementation Ready  
**Created:** November 8, 2025  
**Next:** Begin implementation following this guide

