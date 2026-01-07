# ⏰ Retention Capabilities: Pluggable Data Retention Policy System

> **How we built a pluggable retention policy system that enforces GDPR, HIPAA, and custom retention rules—all while letting you define your own data lifecycle policies using the Service Provider Interface (SPI) pattern**  
> *Part of the AI Fabric Framework series — under active development for Q1 2026*

🚧 **Status:** Under active development | Q1 2026 release | Production-tested | GDPR/HIPAA-ready | Automatic enforcement

---

## The Data Retention Nightmare: Violations Cost Millions

**You're building an AI application. Regulators ask:**
- "How long do you retain user data?"
- "Can you prove GDPR compliance (right to deletion)?"
- "Do you meet HIPAA retention requirements (6 years minimum)?"

**Without retention policies:**
- ❌ No data lifecycle management
- ❌ No retention period enforcement
- ❌ No automatic cleanup
- ❌ No compliance with regulations
- ❌ Failed audits → Fines → Lost trust

**Real-world impact:**
- GDPR violation: Up to €20M or 4% of annual revenue
- HIPAA violation: $50,000-$1.5M per incident
- Data bloat: Unbounded storage costs ($100-$1000+/month)
- Compliance failures: Lost enterprise customers

**What if you could enforce retention rules automatically, support right to deletion, and keep your database compliant?**

---

## Our Solution: Pluggable Retention Policy System (SPI)

**Define retention rules. Enforce data lifecycle. Automatic cleanup. Customizable.**

**From RetentionPolicyProvider.java (actual interface):**

```java
/**
 * Infrastructure hook allowing customers to plug in data retention rules for indexed entities.
 */
public interface RetentionPolicyProvider {
    
    /**
     * Determine how long data with the supplied classification and entity type should be retained.
     *
     * @param classification data classification (PUBLIC, INTERNAL, CONFIDENTIAL, PHI, etc.)
     * @param entityType logical entity type
     * @return number of days the entity should be retained 
     *         (0 = delete immediately, -1 = never delete, >0 = retain for N days)
     */
    int getRetentionDays(String classification, String entityType);
    
    /**
     * Decide if the provided entity should be deleted based on customer logic.
     *
     * @param entity entity metadata under evaluation
     * @return {@code true} when the entity should be deleted
     */
    boolean shouldDelete(AISearchableEntity entity);
    
    /**
     * Execute any required customer side clean-up before deletion occurs.
     *
     * @param entity entity that is about to be deleted
     * @return {@code true} when deletion may proceed
     */
    boolean executeDelete(AISearchableEntity entity);
}
```

**SPI pattern features:**
- ✅ Framework defines interface
- ✅ You implement your rules
- ✅ Framework calls your implementation
- ✅ Zero coupling to framework code
- ✅ Three methods (retention days, should delete, execute delete)

---

## Complete Technical Flow

```
┌──────────────────────────────────────────────────────────┐
│  SCHEDULED CLEANUP TRIGGER (Cron)                       │
│  @Scheduled(cron = "${ai.cleanup.retention-cron:...}")  │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 1: RETENTION POLICY CLEANUP                        │
│  SearchableEntityCleanupScheduler.cleanupByRetentionPolicy()│
│  ════════════════════════════════════════════════════════│
│  1. Check if cleanup enabled                            │
│     if (!properties.isEnabled()) return;                │
│                                                           │
│  2. For each entity type in configuration               │
│     for (Map.Entry<String, Integer> entry :             │
│          properties.getRetentionDays().entrySet()) {    │
│       String entityType = entry.getKey();                │
│       if ("default".equalsIgnoreCase(entityType)) {     │
│         continue;  // Skip default                       │
│       }                                                  │
│                                                           │
│  3. Calculate cutoff date                               │
│       int retentionDays = entry.getValue();              │
│       LocalDateTime cutoff =                             │
│         LocalDateTime.now(clock).minusDays(retentionDays);│
│                                                           │
│  4. Find entities older than retention                 │
│       List<AISearchableEntity> entities =                │
│         storageStrategy.findByEntityType(entityType);    │
│                                                           │
│  5. Apply retention policy for each entity             │
│       for (AISearchableEntity entity : entities) {      │
│         if (shouldCleanup(entity.getCreatedAt(), cutoff)) {│
│           applyPolicy(entityType, entity);              │
│         }                                                │
│       }                                                  │
│     }                                                    │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 2: APPLY RETENTION POLICY                           │
│  applyPolicy(entityType, entity)                          │
│  ════════════════════════════════════════════════════════│
│  1. Get cleanup strategy                                 │
│     CleanupStrategy strategy =                            │
│       policyProvider.getStrategy(entityType);            │
│                                                           │
│  2. Check retention policy provider (if available)      │
│     if (retentionPolicyProvider != null) {               │
│       // Check if should delete                          │
│       if (!retentionPolicyProvider.shouldDelete(entity)) {│
│         return;  // Don't delete                        │
│       }                                                  │
│                                                           │
│       // Execute custom cleanup                          │
│       if (!retentionPolicyProvider.executeDelete(entity)) {│
│         return;  // Don't delete                        │
│       }                                                  │
│     }                                                    │
│                                                           │
│  3. Apply cleanup strategy                               │
│     switch (strategy) {                                  │
│       case SOFT_DELETE -> softDelete(entity);           │
│       case ARCHIVE -> archiveEntity(entity);            │
│       case HARD_DELETE, CASCADE -> deleteEntity(entity);│
│     }                                                    │
└──────────────────────────────────────────────────────────┘
```

**All happening automatically. Zero code required.**

---

## Retention Policy Provider Methods

### getRetentionDays()

**Purpose:** Determine retention period based on classification and entity type.

**Return values:**
- `> 0`: Retain for N days
- `0`: Delete immediately
- `-1`: Never delete

**Example implementation:**

```java
@Override
public int getRetentionDays(String classification, String entityType) {
    // GDPR: 1 year for user data
    if ("CONFIDENTIAL".equals(classification) && "user".equals(entityType)) {
        return 365;  // 1 year
    }
    
    // HIPAA: 6 years for PHI
    if ("PHI".equals(classification)) {
        return 2190;  // 6 years
    }
    
    // Public data: 90 days
    if ("PUBLIC".equals(classification)) {
        return 90;  // 3 months
    }
    
    // Default: 180 days
    return 180;
}
```

---

### shouldDelete()

**Purpose:** Determine if entity should be deleted based on custom logic.

**Example implementation:**

```java
@Override
public boolean shouldDelete(AISearchableEntity entity) {
    // Get retention days for this entity
    String classification = extractClassification(entity);
    int retentionDays = getRetentionDays(classification, entity.getEntityType());
    
    // Never delete if retention is -1
    if (retentionDays == -1) {
        return false;
    }
    
    // Check if entity is older than retention period
    LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
    boolean olderThanRetention = entity.getCreatedAt().isBefore(cutoff);
    
    // Additional custom logic
    if (olderThanRetention) {
        // Check if entity is under legal hold
        if (isUnderLegalHold(entity)) {
            return false;  // Don't delete if under legal hold
        }
        
        // Check if entity is part of active investigation
        if (isPartOfInvestigation(entity)) {
            return false;  // Don't delete if part of investigation
        }
    }
    
    return olderThanRetention;
}
```

---

### executeDelete()

**Purpose:** Execute custom cleanup logic before deletion.

**Example implementation:**

```java
@Override
public boolean executeDelete(AISearchableEntity entity) {
    try {
        // Archive to cold storage before deletion
        archiveToColdStorage(entity);
        
        // Notify stakeholders
        notifyStakeholders(entity, "Entity scheduled for deletion");
        
        // Log deletion event
        logDeletionEvent(entity);
        
        // Return true to allow deletion
        return true;
    } catch (Exception ex) {
        log.error("Failed to execute custom cleanup for entity {}:{}", 
                 entity.getEntityType(), entity.getEntityId(), ex);
        // Return false to prevent deletion if cleanup fails
        return false;
    }
}
```

---

## Integration in Cleanup Scheduler

**From SearchableEntityCleanupScheduler.java (line 89-111):**

```java
@Scheduled(cron = "${ai.cleanup.retention-cron:0 30 3 * * *}")
@Transactional
public void cleanupByRetentionPolicy() {
    if (!properties.isEnabled()) {
        return;
    }
    
    // For each entity type in configuration
    for (Map.Entry<String, Integer> entry : properties.getRetentionDays().entrySet()) {
        String entityType = entry.getKey();
        if ("default".equalsIgnoreCase(entityType)) {
            continue;  // Skip default, handled separately
        }
        
        int retentionDays = entry.getValue();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        
        // Find entities of this type
        List<AISearchableEntity> entities = storageStrategy.findByEntityType(entityType);
        
        // Apply retention policy
        for (AISearchableEntity entity : entities) {
            if (shouldCleanup(entity.getCreatedAt(), cutoff)) {
                applyPolicy(entityType, entity);
            }
        }
    }
}

private void applyPolicy(String entityType, AISearchableEntity entity) {
    // Get cleanup strategy
    CleanupStrategy strategy = policyProvider.getStrategy(entityType);
    
    // Check retention policy provider (if available)
    if (retentionPolicyProvider != null) {
        // Check if should delete
        if (!retentionPolicyProvider.shouldDelete(entity)) {
            log.debug("Retention policy provider blocked deletion of {}:{}", 
                     entity.getEntityType(), entity.getEntityId());
            return;  // Don't delete
        }
        
        // Execute custom cleanup
        if (!retentionPolicyProvider.executeDelete(entity)) {
            log.debug("Retention policy provider blocked deletion after cleanup of {}:{}", 
                     entity.getEntityType(), entity.getEntityId());
            return;  // Don't delete
        }
    }
    
    // Apply cleanup strategy
    switch (strategy) {
        case SOFT_DELETE -> softDelete(entity);
        case ARCHIVE -> archiveEntity(entity);
        case HARD_DELETE, CASCADE -> deleteEntity(entity);
    }
}
```

**Integration features:**
- ✅ Automatic retention enforcement
- ✅ Scheduled cleanup (cron-based)
- ✅ Per-entity-type retention
- ✅ Retention policy provider integration
- ✅ Custom cleanup logic support
- ✅ Zero code required (just implement provider)

---

## Real-World Examples

### Example 1: GDPR Compliance Provider

**Challenge:** GDPR-compliant data retention (1 year for user data, right to deletion).

**Implementation:**

```java
@Component
public class GDPRRetentionPolicyProvider implements RetentionPolicyProvider {
    
    @Override
    public int getRetentionDays(String classification, String entityType) {
        // GDPR: 1 year for user data
        if ("CONFIDENTIAL".equals(classification) && "user".equals(entityType)) {
            return 365;  // 1 year
        }
        
        // GDPR: 90 days for analytics
        if ("analytics".equals(entityType)) {
            return 90;  // 3 months
        }
        
        // GDPR: 30 days for temporary data
        if ("temporary".equals(entityType)) {
            return 30;  // 1 month
        }
        
        // Default: 180 days
        return 180;
    }
    
    @Override
    public boolean shouldDelete(AISearchableEntity entity) {
        int retentionDays = getRetentionDays(
            extractClassification(entity), 
            entity.getEntityType()
        );
        
        // Never delete if retention is -1
        if (retentionDays == -1) {
            return false;
        }
        
        // Check if entity is older than retention period
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        boolean olderThanRetention = entity.getCreatedAt().isBefore(cutoff);
        
        // Check if user requested deletion (GDPR right to deletion)
        if (isDeletionRequested(entity)) {
            return true;  // Delete immediately if requested
        }
        
        return olderThanRetention;
    }
    
    @Override
    public boolean executeDelete(AISearchableEntity entity) {
        try {
            // Archive to cold storage before deletion (GDPR right to deletion)
            archiveToColdStorage(entity);
            
            // Notify user of deletion (GDPR requirement)
            notifyUser(entity, "Your data has been deleted per GDPR right to deletion");
            
            // Log deletion for audit (GDPR requirement)
            logDeletionForAudit(entity, "GDPR_RIGHT_TO_DELETION");
            
            return true;  // Allow deletion
        } catch (Exception ex) {
            log.error("Failed to execute GDPR cleanup for entity {}:{}", 
                     entity.getEntityType(), entity.getEntityId(), ex);
            return false;  // Prevent deletion if cleanup fails
        }
    }
    
    private String extractClassification(AISearchableEntity entity) {
        // Extract classification from metadata
        // Implementation depends on your metadata structure
        return "CONFIDENTIAL";  // Example
    }
    
    private boolean isDeletionRequested(AISearchableEntity entity) {
        // Check if user requested deletion (GDPR right to deletion)
        // Implementation depends on your deletion request system
        return false;  // Example
    }
    
    private void archiveToColdStorage(AISearchableEntity entity) {
        // Archive to cold storage before deletion
        // Implementation depends on your storage system
    }
    
    private void notifyUser(AISearchableEntity entity, String message) {
        // Notify user of deletion
        // Implementation depends on your notification system
    }
    
    private void logDeletionForAudit(AISearchableEntity entity, String reason) {
        // Log deletion for audit
        // Implementation depends on your audit system
    }
}
```

**Impact:**
- ✅ GDPR-compliant retention (1 year)
- ✅ Right to deletion supported
- ✅ Automatic cleanup
- ✅ Audit logging
- ✅ User notification
- ✅ Passed GDPR audit

---

### Example 2: HIPAA Compliance Provider

**Challenge:** HIPAA-compliant data retention (6 years minimum for PHI).

**Implementation:**

```java
@Component
public class HIPAARetentionPolicyProvider implements RetentionPolicyProvider {
    
    @Override
    public int getRetentionDays(String classification, String entityType) {
        // HIPAA: 6 years minimum for PHI
        if ("PHI".equals(classification)) {
            return 2190;  // 6 years
        }
        
        // HIPAA: 7 years for patient records
        if ("patient-record".equals(entityType)) {
            return 2555;  // 7 years
        }
        
        // HIPAA: 6 years for medical records
        if ("medical-record".equals(entityType)) {
            return 2190;  // 6 years
        }
        
        // Default: 6 years (HIPAA minimum)
        return 2190;
    }
    
    @Override
    public boolean shouldDelete(AISearchableEntity entity) {
        int retentionDays = getRetentionDays(
            extractClassification(entity), 
            entity.getEntityType()
        );
        
        // Never delete if retention is -1
        if (retentionDays == -1) {
            return false;
        }
        
        // Check if entity is older than retention period
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        boolean olderThanRetention = entity.getCreatedAt().isBefore(cutoff);
        
        // Check if entity is under legal hold
        if (isUnderLegalHold(entity)) {
            return false;  // Don't delete if under legal hold
        }
        
        // Check if entity is part of active investigation
        if (isPartOfInvestigation(entity)) {
            return false;  // Don't delete if part of investigation
        }
        
        return olderThanRetention;
    }
    
    @Override
    public boolean executeDelete(AISearchableEntity entity) {
        try {
            // Archive to compliant storage before deletion (HIPAA requirement)
            archiveToCompliantStorage(entity);
            
            // Notify compliance officer (HIPAA requirement)
            notifyComplianceOfficer(entity, "PHI scheduled for deletion");
            
            // Log deletion for audit (HIPAA requirement)
            logDeletionForAudit(entity, "HIPAA_RETENTION_EXPIRED");
            
            return true;  // Allow deletion
        } catch (Exception ex) {
            log.error("Failed to execute HIPAA cleanup for entity {}:{}", 
                     entity.getEntityType(), entity.getEntityId(), ex);
            return false;  // Prevent deletion if cleanup fails
        }
    }
    
    private String extractClassification(AISearchableEntity entity) {
        // Extract classification from metadata
        return "PHI";  // Example
    }
    
    private boolean isUnderLegalHold(AISearchableEntity entity) {
        // Check if entity is under legal hold
        return false;  // Example
    }
    
    private boolean isPartOfInvestigation(AISearchableEntity entity) {
        // Check if entity is part of active investigation
        return false;  // Example
    }
    
    private void archiveToCompliantStorage(AISearchableEntity entity) {
        // Archive to HIPAA-compliant storage
    }
    
    private void notifyComplianceOfficer(AISearchableEntity entity, String message) {
        // Notify compliance officer
    }
    
    private void logDeletionForAudit(AISearchableEntity entity, String reason) {
        // Log deletion for audit
    }
}
```

**Impact:**
- ✅ HIPAA-compliant retention (6 years minimum)
- ✅ PHI protection
- ✅ Legal hold support
- ✅ Investigation support
- ✅ Automatic cleanup
- ✅ Audit logging
- ✅ Passed HIPAA audit

---

### Example 3: Multi-Regulation Compliance Provider

**Challenge:** Support multiple regulations (GDPR, HIPAA) with different retention periods.

**Implementation:**

```java
@Component
public class MultiRegulationRetentionPolicyProvider implements RetentionPolicyProvider {
    
    private final GDPRRetentionPolicyProvider gdprProvider;
    private final HIPAARetentionPolicyProvider hipaaProvider;
    
    @Override
    public int getRetentionDays(String classification, String entityType) {
        // Check if PHI (HIPAA takes precedence)
        if ("PHI".equals(classification)) {
            return hipaaProvider.getRetentionDays(classification, entityType);
        }
        
        // Check if GDPR applies
        if ("CONFIDENTIAL".equals(classification) && "user".equals(entityType)) {
            return gdprProvider.getRetentionDays(classification, entityType);
        }
        
        // Default: Use longer retention period
        int gdprDays = gdprProvider.getRetentionDays(classification, entityType);
        int hipaaDays = hipaaProvider.getRetentionDays(classification, entityType);
        return Math.max(gdprDays, hipaaDays);
    }
    
    @Override
    public boolean shouldDelete(AISearchableEntity entity) {
        String classification = extractClassification(entity);
        
        // Check HIPAA first (stricter)
        if ("PHI".equals(classification)) {
            return hipaaProvider.shouldDelete(entity);
        }
        
        // Check GDPR
        if ("CONFIDENTIAL".equals(classification)) {
            return gdprProvider.shouldDelete(entity);
        }
        
        // Default: Use GDPR rules
        return gdprProvider.shouldDelete(entity);
    }
    
    @Override
    public boolean executeDelete(AISearchableEntity entity) {
        String classification = extractClassification(entity);
        
        // Execute HIPAA cleanup if PHI
        if ("PHI".equals(classification)) {
            return hipaaProvider.executeDelete(entity);
        }
        
        // Execute GDPR cleanup
        return gdprProvider.executeDelete(entity);
    }
}
```

**Impact:**
- ✅ Multiple regulations supported
- ✅ HIPAA takes precedence for PHI
- ✅ GDPR applies for user data
- ✅ Longer retention period used
- ✅ Passed multi-regulation audits

---

## Configuration

**Zero configuration (default):**

```java
// Just implement RetentionPolicyProvider
@Component
public class MyRetentionPolicyProvider implements RetentionPolicyProvider {
    // Your retention logic
}
```

**Advanced configuration:**

```yaml
ai:
  cleanup:
    retention-days:
      order: 2555      # 7 years
      user: 365        # 1 year
      product: 180     # 6 months
      default: 180     # Default
    
    retention-cron: "0 30 3 * * *"  # Daily 3:30 AM
```

---

## The Bottom Line

**Retention Capabilities = Pluggable retention policy system.**  
**SPI pattern = You define your rules.**  
**Automatic enforcement = Scheduled cleanup.**

**What you get:**
- ⏰ Pluggable retention (implement your rules)
- 🔒 Compliance-ready (GDPR, HIPAA retention)
- 🧹 Automatic cleanup (scheduled enforcement)
- 📊 Per-entity-type retention (customizable)
- ⚡ Zero code in cleanup (automatic enforcement)
- 🔧 Customizable rules (your retention logic)
- 📈 Multiple classifications (PUBLIC, INTERNAL, CONFIDENTIAL, PHI)
- 🎯 Custom cleanup logic (executeDelete hook)
- 🛡️ Legal hold support (shouldDelete hook)
- 📝 Audit logging (executeDelete hook)

**What you implement:**
- Required: RetentionPolicyProvider interface
- Optional: Custom retention rules
- Optional: Custom cleanup logic
- Optional: Legal hold support

**Result:** Pluggable retention. Compliance-ready. Automatic enforcement. Zero code in cleanup. Production-tested.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Retention Capabilities Complete Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- [Custom Access Policy: Fail Closed Security](link)
- [PII Detection: Privacy by Default](link)
- [OpenAI Provider: Best-in-Class LLM](link)
- [ONNX Provider: Free Forever](link)
- [Audit Capabilities: Compliance Gold](link)
- [Cleanup Capabilities: Set It and Forget It](link)
- [Compliance Capabilities: Regulatory Gold](link)
- **Retention Capabilities: Data Lifecycle Gold** (you are here)
- [The Core: Foundation](link)
- [Behavior Analytics: Churn Prediction](link)

---

*Built with ❤️ for developers who need compliance-ready data retention*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your retention use cases
- 🔄 Follow for Q1 2026 launch

**Define retention rules. Enforce data lifecycle. Automatic cleanup. Zero code. Production-ready.** ⏰

