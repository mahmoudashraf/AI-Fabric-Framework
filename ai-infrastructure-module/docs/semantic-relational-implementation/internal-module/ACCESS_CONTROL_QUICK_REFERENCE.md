# 🔐 Access Control Quick Reference

> **Quick guide to implementing entity type access control for Relationship Query module**

## 📍 Location in Main Document

The full access control documentation is in:
- **File:** `RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md`
- **Section:** `## 🔐 Access Control: Entity Type Filtering` (starts at line ~1167)
- **Direct Link:** Search for "🔐 Access Control: Entity Type Filtering" in the document

---

## 🎯 Quick Overview

**What is Access Control?**
- Control which entity types each user can query
- Filter entity types before query execution
- Support multiple access control patterns (role, permission, tenant, classification)

**Where Does It Happen?**
1. **Orchestrator Level** - Framework-level check via `EntityAccessPolicy`
2. **Action Handler Level** - Entity type filtering in `RelationshipQueryActionHandler`
3. **Result Level** - Entity-level filtering after query execution

---

## 🚀 Quick Start

### Step 1: Override Access Control Method

In your `RelationshipQueryActionHandler` implementation:

```java
private boolean canUserQueryEntityType(String userId, String entityType) {
    // YOUR ACCESS CONTROL LOGIC HERE
    // See patterns below
}
```

### Step 2: Choose Your Pattern

**Pattern 1: Role-Based (Simplest)**
```java
User user = userService.getUser(userId);
if (user.getRole().equals("ADMIN")) {
    return true;  // Admins can query all
}
return user.getAllowedEntityTypes().contains(entityType);
```

**Pattern 2: Permission-Based (Most Flexible)**
```java
String permission = "relationship_query:" + entityType;
return permissionService.hasPermission(userId, permission);
```

**Pattern 3: Tenant-Based (Multi-Tenant)**
```java
String userTenant = tenantService.getTenantId(userId);
return tenantMappingService.isEntityTypeAccessible(userTenant, entityType);
```

**Pattern 4: Data Classification (Security-Focused)**
```java
EntityClassification classification = classificationService.getClassification(entityType);
switch (classification) {
    case PUBLIC: return true;
    case INTERNAL: return userId != null;
    case SENSITIVE: return permissionService.hasPermission(userId, "relationship_query:sensitive");
    case RESTRICTED: return permissionService.hasRole(userId, "ADMIN");
    default: return false;
}
```

**Pattern 5: Hybrid (Recommended)**
```java
// Combine multiple patterns
User user = userService.getUser(userId);
if (user.getRole().equals("ADMIN")) return true;

EntityClassification classification = classificationService.getClassification(entityType);
if (classification == EntityClassification.RESTRICTED) return false;

if (permissionService.hasPermission(userId, "relationship_query:" + entityType)) {
    return true;
}

return user.getAllowedEntityTypes().contains(entityType);
```

---

## 📋 Implementation Checklist

- [ ] Override `canUserQueryEntityType()` method
- [ ] Implement your access control logic (choose pattern above)
- [ ] Override `getAllowedEntityTypesForUser()` if needed
- [ ] Test access control with different user roles/permissions
- [ ] Add logging for access denials (audit trail)
- [ ] Configure access control settings in `application.yml`

---

## 🔗 Full Documentation

For complete details, examples, and best practices, see:
- **Main Document:** `RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md`
- **Section:** `## 🔐 Access Control: Entity Type Filtering`
- **Line:** ~1167

**Key Sections:**
- Overview (line ~1169)
- How It Works (line ~1172)
- Implementation Patterns (line ~1148)
  - Pattern 1: Role-Based (line ~1150)
  - Pattern 2: Permission-Based (line ~1204)
  - Pattern 3: Tenant-Based (line ~1242)
  - Pattern 4: Data Classification (line ~1282)
  - Pattern 5: Hybrid (line ~1341)
- Integration with EntityAccessPolicy (line ~1388)
- Result-Level Access Control (line ~1436)
- Configuration (line ~1481)
- Testing Access Control (line ~1502)
- Best Practices (line ~1561)
- Summary (line ~1570)

---

## 💡 Common Use Cases

### Use Case 1: Admin Can Query All, Users Limited

```java
private boolean canUserQueryEntityType(String userId, String entityType) {
    User user = userService.getUser(userId);
    return user.getRole().equals("ADMIN") || 
           user.getAllowedEntityTypes().contains(entityType);
}
```

### Use Case 2: Multi-Tenant Isolation

```java
private boolean canUserQueryEntityType(String userId, String entityType) {
    String tenant = tenantService.getTenantId(userId);
    return tenantMappingService.isEntityTypeAccessible(tenant, entityType);
}
```

### Use Case 3: Sensitive Data Protection

```java
private boolean canUserQueryEntityType(String userId, String entityType) {
    EntityClassification classification = classificationService.getClassification(entityType);
    if (classification == EntityClassification.SENSITIVE) {
        return permissionService.hasPermission(userId, "relationship_query:sensitive");
    }
    return true;  // Public/internal entities accessible to all authenticated users
}
```

---

## ⚠️ Important Notes

1. **Access control is automatically called** - The handler calls `filterAllowedEntityTypes()` before query execution
2. **Fail closed** - If check fails or throws exception, access is denied
3. **Early filtering** - Entity types filtered before LLM call (saves tokens)
4. **Defense in depth** - Also filter results after query execution

---

## 🔍 Finding the Section in Your Editor

**Method 1: Search**
- Press `Ctrl+F` (or `Cmd+F` on Mac)
- Search for: `🔐 Access Control` or `Access Control: Entity Type Filtering`

**Method 2: Table of Contents**
- Scroll to top of document
- Use the table of contents (just added)
- Click on "🔐 Access Control: Entity Type Filtering"

**Method 3: Line Number**
- Go to line ~1167 in the document
- Look for heading: `## 🔐 Access Control: Entity Type Filtering`

---

**Last Updated:** 2025-12-30  
**Related Document:** `RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md`

