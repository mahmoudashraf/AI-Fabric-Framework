# 🧹 Cleanup Capabilities: When "Set It and Forget It" Meets "Compliance Gold"

*How we built automatic cleanup that removes orphaned vectors, enforces retention policies, and keeps your vector database healthy—all while protecting data integrity*

🚧 **Under active development | Q1 2026 release | Production-tested | GDPR/HIPAA-ready**

---

## The Problem: Orphaned Vectors and Data Bloat

**You're building an AI application. Over time:**
- ❌ Entities deleted but vectors remain (orphaned vectors)
- ❌ Failed indexing attempts accumulate
- ❌ Old data never gets cleaned up (data bloat)
- ❌ Vector database grows unbounded (costs increase)
- ❌ Compliance violations (retention policies not enforced)

**Result:** Slow searches. High costs. Failed audits.

---

## Our Approach: Automatic Cleanup

**Remove orphaned vectors. Enforce retention policies. Keep database healthy. Zero code.**

```java
// Automatic cleanup - zero code required
// Scheduled cleanup runs automatically:
// - Orphaned entities: Every Sunday at 4 AM
// - No-vector entities: Every Sunday at 5 AM
// - Retention policy: Daily at 3:30 AM

// Cleanup strategies:
// - SOFT_DELETE: Mark as deleted, keep metadata
// - ARCHIVE: Move to archive, remove from search
// - HARD_DELETE: Permanently delete
// - CASCADE: Delete entity and related vectors
```

**Zero code. Automatic. Configurable. Compliance-ready.**

---

## The Complete Flow

```
Scheduled Cleanup Trigger (Cron)
    ↓
┌──────────────────────────────────────────┐
│ STEP 1: ORPHANED ENTITIES CLEANUP        │
│ cleanupOrphanedEntities()                │
│ ════════════════════════════════════════│
│ 1. Find entities with vector IDs        │
│ 2. Check if vector exists in DB          │
│ 3. If vector missing → delete entity    │
│ 4. Remove orphaned metadata              │
│                                          │
│ Result: Orphaned entities removed       │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 2: NO-VECTOR ENTITIES CLEANUP        │
│ cleanupEntitiesWithoutVectors()            │
│ ════════════════════════════════════════│
│ 1. Find entities without vector IDs      │
│ 2. Check retention period (default: 24h) │
│ 3. If older than retention → delete      │
│ 4. Remove stale metadata                 │
│                                          │
│ Result: Stale entities removed           │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 3: RETENTION POLICY CLEANUP          │
│ cleanupByRetentionPolicy()                │
│ ════════════════════════════════════════│
│ 1. For each entity type:                  │
│    - Get retention days (config)        │
│    - Calculate cutoff date               │
│    - Find entities older than cutoff     │
│    - Apply cleanup strategy:             │
│      • SOFT_DELETE: Mark deleted         │
│      • ARCHIVE: Move to archive          │
│      • HARD_DELETE: Permanently delete   │
│      • CASCADE: Delete + vectors        │
│                                          │
│ Result: Retention policies enforced     │
└──────────────────────────────────────────┘
```

**All happening automatically. Zero code required.**

---

## Cleanup Strategies

**From CleanupStrategy.java (actual enum):**

```java
public enum CleanupStrategy {
    SOFT_DELETE,   // Mark as deleted, keep metadata
    ARCHIVE,       // Move to archive, remove from search
    HARD_DELETE,   // Permanently delete
    CASCADE        // Delete entity and related vectors
}
```

**Strategy details:**

**SOFT_DELETE:**
- Marks entity as deleted
- Keeps metadata for audit
- Removes from search index
- Vector evicted
- Recoverable

**ARCHIVE:**
- Moves to archive storage
- Removes from search index
- Vector evicted
- Can be restored

**HARD_DELETE:**
- Permanently deletes entity
- Removes from database
- Vector evicted
- Not recoverable

**CASCADE:**
- Deletes entity
- Deletes related vectors
- Removes from all indexes
- Not recoverable

---

## What Gets Cleaned Up

**From SearchableEntityCleanupScheduler.java (actual code):**

### 1. Orphaned Entities

```java
@Scheduled(cron = "${ai.cleanup.orphaned-entities.cron:0 0 4 * * SUN}")
public void cleanupOrphanedEntities() {
    // Find entities with vector IDs
    List<AISearchableEntity> entities = storageStrategy.findByVectorIdIsNotNull();
    
    // Check if vector exists
    for (AISearchableEntity entity : entities) {
        if (!vectorExists(entity)) {
            deleteEntity(entity);  // Orphaned → delete
        }
    }
}
```

**Cleans up:**
- ✅ Entities with vector IDs but no actual vector
- ✅ Orphaned metadata
- ✅ Stale searchable content

---

### 2. No-Vector Entities

```java
@Scheduled(cron = "${ai.cleanup.no-vector-entities.cron:0 0 5 * * SUN}")
public void cleanupEntitiesWithoutVectors() {
    // Find entities without vector IDs
    List<AISearchableEntity> entities = storageStrategy.findByVectorIdIsNull();
    
    // Check retention period (default: 24 hours)
    Duration retention = properties.getNoVectorEntities().getRetention();
    LocalDateTime cutoff = LocalDateTime.now().minus(retention);
    
    // Delete if older than retention
    for (AISearchableEntity entity : entities) {
        if (shouldCleanup(entity.getCreatedAt(), cutoff)) {
            deleteEntity(entity);
        }
    }
}
```

**Cleans up:**
- ✅ Entities without vector IDs
- ✅ Failed indexing attempts
- ✅ Stale entities (older than retention)

---

### 3. Retention Policy Cleanup

```java
@Scheduled(cron = "${ai.cleanup.retention-cron:0 30 3 * * *}")
public void cleanupByRetentionPolicy() {
    // For each entity type
    for (Map.Entry<String, Integer> entry : properties.getRetentionDays().entrySet()) {
        String entityType = entry.getKey();
        int retentionDays = entry.getValue();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        
        // Find entities older than retention
        List<AISearchableEntity> entities = storageStrategy.findByEntityType(entityType);
        
        // Apply cleanup strategy
        for (AISearchableEntity entity : entities) {
            if (shouldCleanup(entity.getCreatedAt(), cutoff)) {
                applyPolicy(entityType, entity);
            }
        }
    }
}
```

**Cleans up:**
- ✅ Entities older than retention period
- ✅ Per-entity-type retention policies
- ✅ Configurable cleanup strategies

---

## Real-World Example: E-Commerce Cleanup

**Challenge:** Clean up old orders, products, and analytics data.

**Without cleanup:**
```java
// Orphaned vectors accumulate
// Old data never deleted
// Vector database grows unbounded
// Costs increase
// Slow searches
```

**With cleanup:**
```yaml
ai:
  cleanup:
    enabled: true
    
    # Retention policies (days)
    retention-days:
      order: 2555      # 7 years (compliance)
      user: 365        # 1 year
      product: 180     # 6 months
      behavior: 90     # 3 months
      analytics: 30    # 1 month
      default: 180     # 6 months default
    
    # Cleanup strategies
    strategies:
      order: ARCHIVE      # Archive orders (compliance)
      user: ARCHIVE       # Archive users (GDPR)
      product: SOFT_DELETE # Soft delete products
      behavior: HARD_DELETE # Hard delete behavior data
      analytics: HARD_DELETE # Hard delete analytics
    
    # Schedule
    orphaned-entities:
      enabled: true
      cron: "0 0 4 * * SUN"  # Sunday 4 AM
    
    no-vector-entities:
      enabled: true
      cron: "0 0 5 * * SUN"  # Sunday 5 AM
      retention: PT24H       # 24 hours
    
    retention-cron: "0 30 3 * * *"  # Daily 3:30 AM
```

**Impact:**
- ✅ Orphaned vectors removed (Sunday 4 AM)
- ✅ Stale entities removed (Sunday 5 AM)
- ✅ Retention policies enforced (Daily 3:30 AM)
- ✅ Database size controlled
- ✅ Costs reduced
- ✅ Compliance maintained

---

## Configuration

**Zero configuration (default):**

```yaml
# Cleanup enabled by default
ai:
  cleanup:
    enabled: true
```

**Advanced configuration:**

```yaml
ai:
  cleanup:
    enabled: true
    
    # Retention policies (days)
    retention-days:
      order: 2555      # 7 years
      user: 365        # 1 year
      product: 180     # 6 months
      default: 180     # Default
    
    # Cleanup strategies
    strategies:
      order: ARCHIVE
      user: ARCHIVE
      product: SOFT_DELETE
      behavior: HARD_DELETE
    
    # Orphaned entities cleanup
    orphaned-entities:
      enabled: true
      cron: "0 0 4 * * SUN"  # Sunday 4 AM
    
    # No-vector entities cleanup
    no-vector-entities:
      enabled: true
      cron: "0 0 5 * * SUN"  # Sunday 5 AM
      retention: PT24H       # 24 hours
    
    # Retention policy cleanup
    retention-cron: "0 30 3 * * *"  # Daily 3:30 AM
```

---

## The Bottom Line

**Cleanup Capabilities = Automatic data cleanup.**  
**Zero code = Scheduled cleanup runs automatically.**  
**Compliance-ready = Retention policies enforced.**

**What you get:**
- 🧹 Orphaned vector cleanup (automatic detection & removal)
- ⏰ Retention policy enforcement (per entity type)
- 🗑️ Stale entity cleanup (failed indexing attempts)
- 📊 Configurable strategies (SOFT_DELETE, ARCHIVE, HARD_DELETE, CASCADE)
- ⚡ Zero code (automatic scheduled cleanup)
- 🔧 Configurable schedules (cron-based)
- 💰 Cost reduction (database size control)
- ✅ Compliance-ready (GDPR, HIPAA retention)

**What you configure:**
- Optional: Retention days per entity type
- Optional: Cleanup strategies per entity type
- Optional: Cleanup schedules (cron)

**Result:** Automatic cleanup. Retention policies enforced. Database healthy. Zero code.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Cleanup Capabilities Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- [Custom Access Policy: Fail Closed Security](link)
- [PII Detection: Privacy by Default](link)
- [OpenAI Provider: Best-in-Class LLM](link)
- [ONNX Provider: Free Forever](link)
- [Audit Capabilities: Compliance Gold](link)
- **Cleanup Capabilities: Set It and Forget It** (you are here)

---

*Built with ❤️ for developers who want automatic data cleanup*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your cleanup use cases
- 🔄 Follow for Q1 2026 launch

**Remove orphaned vectors. Enforce retention. Keep database healthy. Zero code.** 🧹

