# Duration Configuration Standardization Plan

## 📋 Executive Summary

**Goal:** Standardize all duration-related configuration properties across the AI Infrastructure framework to use ISO-8601 Duration format, replacing inconsistent property naming patterns (`-seconds`, `-days`, `-ms` suffixes).

**Current State:** Inconsistent duration configuration
- ✅ Java code uses `Duration` type
- ❌ YAML uses mixed formats (plain numbers with unit suffixes)
- ❌ Inconsistent naming: `timeout-seconds`, `retention-days`, `ttl-seconds`

**Target State:** Consistent ISO-8601 format
- ✅ Java code continues using `Duration` type
- ✅ YAML uses ISO-8601 format (e.g., `PT24H`, `PT30S`, `PT5M`)
- ✅ Property names without unit suffixes (e.g., `timeout`, `retention`, `ttl`)

**Key Benefits:**
- ✅ Industry standard format
- ✅ Spring Boot native support
- ✅ Self-documenting (units explicit in value)
- ✅ Flexible precision (can express PT1H30M45S)
- ✅ Consistent across all modules

---

## 🎯 Why ISO-8601 Duration Format?

### Current Problems

**Problem 1: Inconsistent Suffixes**
```yaml
# Different properties use different naming
query-cache-ttl-seconds: 3600     # Uses -seconds
timeout: 60                        # Implies seconds?
connection-timeout: 20000          # Is this seconds or milliseconds?
retention-days: 7                  # Uses -days
```

**Problem 2: Property Name Tied to Unit**
```yaml
# What if we want to change 3600 seconds to 1 hour?
timeout-seconds: 3600  # Name says seconds, but 1 hour is clearer
```

**Problem 3: Ambiguity**
```yaml
timeout: 60  # Is this seconds? milliseconds? minutes?
```

### ISO-8601 Solution

**Explicit and Clear:**
```yaml
timeout: PT60S          # Clearly 60 seconds
timeout: PT1M           # Clearly 1 minute
timeout: PT1H           # Clearly 1 hour
connection-timeout: PT20S  # Clearly 20 seconds (not 20000ms!)
retention: PT7D         # Clearly 7 days
cache-ttl: PT1H         # Clearly 1 hour
```

**Flexible:**
```yaml
# Can express complex durations
processing-window: PT1H30M        # 1 hour 30 minutes
max-wait: PT2H45M30S              # 2 hours 45 minutes 30 seconds
short-delay: PT500MS              # 500 milliseconds
```

---

## 📚 ISO-8601 Duration Format Reference

### Syntax

```
PT[n]H[n]M[n]S
P[n]D

P = Period prefix
T = Time prefix (required for hours/minutes/seconds)
D = Days
H = Hours
M = Minutes (after T)
S = Seconds
```

### Common Examples

| Duration | ISO-8601 Format | Notes |
|----------|----------------|-------|
| 30 seconds | `PT30S` | |
| 1 minute | `PT1M` or `PT60S` | |
| 5 minutes | `PT5M` | |
| 1 hour | `PT1H` | |
| 24 hours | `PT24H` | Preferred over `P1D` |
| 48 hours | `PT48H` | More explicit than `P2D` |
| 7 days | `PT168H` or `P7D` | PT168H preferred for consistency |
| 1.5 hours | `PT1H30M` | Composite duration |
| 100 milliseconds | `PT0.1S` or `PT100MS` | Spring Boot supports MS |
| 2 hours 30 minutes | `PT2H30M` | Composite |

### Spring Boot Support

Spring Boot automatically converts ISO-8601 strings to `Duration` objects:

```java
// In @ConfigurationProperties class
private Duration maxAge = Duration.ofHours(24);
```

```yaml
# In application.yml
max-age: PT24H  # Auto-converted to Duration.ofHours(24)
```

---

## 🔄 Migration Strategy

### Phase 1: Identify All Duration Properties

**Current Duration-Related Properties:**

#### Core Module (AIIndexingProperties)
```java
// Current (Java)
private Duration fixedDelay = Duration.ofSeconds(1);
private Duration visibilityTimeout = Duration.ofMinutes(2);
private Duration stuckThreshold = Duration.ofMinutes(10);
private Duration sweepInterval = Duration.ofMinutes(5);
private Duration completedRetention = Duration.ofDays(7);
private Duration deadLetterRetention = Duration.ofDays(30);

// YAML - Already compatible! ✅
ai:
  indexing:
    async-worker:
      fixed-delay: PT1S      # Can use ISO-8601
    queue:
      visibility-timeout: PT2M
    cleanup:
      stuck-threshold: PT10M
      sweep-interval: PT5M
      completed-retention: PT7D
      dead-letter-retention: PT30D
```

#### Behavior Module (BehaviorProcessingProperties)
```java
// Current (Java) - Already uses Duration ✅
private Duration scheduledMaxDuration = Duration.ofMinutes(10);
private Duration apiMaxDuration = Duration.ofMinutes(30);
private Duration processingDelay = Duration.ofMillis(100);
private Duration continuousInterval = Duration.ofMinutes(5);

// YAML - Need to migrate
# OLD
ai:
  behavior:
    processing:
      scheduled-max-duration-minutes: 10  # ❌ Unit in name
      processing-delay-ms: 100            # ❌ Unit in name

# NEW (ISO-8601)
ai:
  behavior:
    processing:
      scheduled-max-duration: PT10M      # ✅ Clear
      api-max-duration: PT30M            # ✅ Clear
      processing-delay: PT100MS          # ✅ Clear
      continuous-interval: PT5M          # ✅ Clear
```

#### Relationship Query Module (RelationshipQueryProperties)
```java
// Current - Uses long/int (not Duration) ❌
private long queryCacheTtlSeconds = 3600;  
private int timeoutSeconds = 30;

// MIGRATE TO Duration
private Duration queryCacheTtl = Duration.ofHours(1);
private Duration timeout = Duration.ofSeconds(30);

// YAML Migration
# OLD
ai:
  infrastructure:
    relationship:
      query-cache-ttl-seconds: 3600  # ❌
      timeout-seconds: 30            # ❌

# NEW
ai:
  infrastructure:
    relationship:
      query-cache-ttl: PT1H    # ✅ Clear
      timeout: PT30S           # ✅ Clear
```

---

## 📦 Phase 1: Core Module Updates

### 1.1 AIIndexingProperties

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIIndexingProperties.java`

**Status:** ✅ Already uses `Duration` type correctly

**Action:** Document ISO-8601 format in JavaDoc

```java
@Data
@ConfigurationProperties(prefix = "ai.indexing")
public class AIIndexingProperties {
    
    @Data
    public static class QueueProperties {
        private int maxRetries = 5;
        
        /**
         * Visibility timeout for queue messages.
         * Format: ISO-8601 Duration (e.g., PT2M for 2 minutes)
         * Default: PT2M
         */
        private Duration visibilityTimeout = Duration.ofMinutes(2);
    }
    
    @Data
    public static class CleanupProperties {
        private boolean enabled = true;
        
        /**
         * Threshold for considering items stuck.
         * Format: ISO-8601 Duration (e.g., PT10M for 10 minutes)
         * Default: PT10M
         */
        private Duration stuckThreshold = Duration.ofMinutes(10);
        
        /**
         * Interval between cleanup sweeps.
         * Format: ISO-8601 Duration (e.g., PT5M for 5 minutes)
         * Default: PT5M
         */
        private Duration sweepInterval = Duration.ofMinutes(5);
        
        /**
         * Retention period for completed items.
         * Format: ISO-8601 Duration (e.g., PT168H for 7 days)
         * Default: PT168H (7 days)
         */
        private Duration completedRetention = Duration.ofDays(7);
        
        /**
         * Retention period for dead letter items.
         * Format: ISO-8601 Duration (e.g., PT720H for 30 days)
         * Default: PT720H (30 days)
         */
        private Duration deadLetterRetention = Duration.ofDays(30);
    }
}
```

### 1.2 IntentHistoryProperties

**Current:**
```java
private int retentionDays = 30;
```

**Migrate to:**
```java
/**
 * Retention period for intent history.
 * Format: ISO-8601 Duration (e.g., PT720H for 30 days)
 * Default: PT720H (30 days)
 */
private Duration retention = Duration.ofDays(30);
```

---

## 📦 Phase 2: Relationship Query Module Updates

### 2.1 RelationshipQueryProperties - Major Migration

**Location:** `ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/config/RelationshipQueryProperties.java`

**Current:**
```java
private long queryCacheTtlSeconds = 3600;
private int timeoutSeconds = 30;
private long ttlSeconds = 3600;  // In RegionProperties
```

**Migrate to:**
```java
@ConfigurationProperties(prefix = "ai.infrastructure.relationship")
@Data
public class RelationshipQueryProperties {
    
    // ... other properties ...
    
    /**
     * TTL for cached query plans and results.
     * Format: ISO-8601 Duration (e.g., PT1H for 1 hour)
     * Default: PT1H
     * 
     * @deprecated Use {@link #queryCacheTtl} instead. Will be removed in v3.0
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private Long queryCacheTtlSeconds;
    
    /**
     * TTL for cached query plans and results.
     * Format: ISO-8601 Duration (e.g., PT1H for 1 hour)
     * Default: PT1H
     */
    private Duration queryCacheTtl = Duration.ofHours(1);
    
    /**
     * Get cache TTL, supporting both old and new configuration.
     * @return Cache TTL duration
     */
    public Duration getQueryCacheTtl() {
        if (queryCacheTtlSeconds != null) {
            log.warn("Property 'query-cache-ttl-seconds' is deprecated. Use 'query-cache-ttl: PT1H' instead");
            return Duration.ofSeconds(queryCacheTtlSeconds);
        }
        return queryCacheTtl;
    }
    
    @Data
    public static class PlannerProperties {
        
        /**
         * @deprecated Use {@link #timeout} instead
         */
        @Deprecated(since = "2.0", forRemoval = true)
        private Integer timeoutSeconds;
        
        /**
         * LLM request timeout.
         * Format: ISO-8601 Duration (e.g., PT30S for 30 seconds)
         * Default: PT30S
         */
        private Duration timeout = Duration.ofSeconds(30);
        
        public Duration getTimeout() {
            if (timeoutSeconds != null) {
                log.warn("Property 'timeout-seconds' is deprecated. Use 'timeout: PT30S' instead");
                return Duration.ofSeconds(timeoutSeconds);
            }
            return timeout;
        }
    }
    
    @Data
    public static class RegionProperties {
        
        /**
         * @deprecated Use {@link #ttl} instead
         */
        @Deprecated(since = "2.0", forRemoval = true)
        private Long ttlSeconds;
        
        /**
         * Cache region TTL.
         * Format: ISO-8601 Duration
         * Default: PT1H
         */
        private Duration ttl = Duration.ofHours(1);
        
        private int maxEntries;
        
        public Duration getTtl() {
            if (ttlSeconds != null) {
                return Duration.ofSeconds(ttlSeconds);
            }
            return ttl;
        }
        
        public long ttlMillis() {
            return getTtl().toMillis();
        }
    }
}
```

---

## 📦 Phase 3: Behavior Module Updates

### 3.1 BehaviorProcessingProperties

**Location:** `ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/config/BehaviorProcessingProperties.java`

**Status:** ✅ Already uses `Duration` type correctly

**Action:** Document ISO-8601 format in JavaDoc

```java
@Data
@Component
@ConfigurationProperties(prefix = "ai.behavior.processing")
public class BehaviorProcessingProperties {
    
    // Scheduled processing
    private boolean scheduledEnabled = false;
    private String scheduleCron = "0 */15 * * * *";
    private int scheduledBatchSize = 100;
    
    /**
     * Maximum duration for scheduled processing batch.
     * Format: ISO-8601 Duration (e.g., PT10M for 10 minutes)
     * Default: PT10M
     */
    private Duration scheduledMaxDuration = Duration.ofMinutes(10);
    
    // API-triggered processing
    private boolean apiEnabled = true;
    private int apiMaxBatchSize = 1000;
    
    /**
     * Maximum duration for API-triggered processing.
     * Format: ISO-8601 Duration (e.g., PT30M for 30 minutes)
     * Default: PT30M
     */
    private Duration apiMaxDuration = Duration.ofMinutes(30);
    
    /**
     * Delay between processing individual items.
     * Format: ISO-8601 Duration (e.g., PT100MS for 100 milliseconds)
     * Default: PT100MS
     */
    private Duration processingDelay = Duration.ofMillis(100);
    
    // Continuous processing defaults
    private int continuousUsersPerBatch = 100;
    
    /**
     * Interval between continuous processing batches.
     * Format: ISO-8601 Duration (e.g., PT5M for 5 minutes)
     * Default: PT5M
     */
    private Duration continuousInterval = Duration.ofMinutes(5);
}
```

### 3.2 Add New BehaviorProperties (for max-insight-age)

**Location:** `ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/config/BehaviorProperties.java`

**Create New File:**

```java
package com.ai.infrastructure.behavior.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for behavior analysis and insights.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.behavior")
public class BehaviorProperties {
    
    /**
     * Enable behavior analysis module.
     * Default: true
     */
    private boolean enabled = true;
    
    /**
     * Behavior analysis mode.
     * Options: LIGHT, FULL
     * Default: FULL
     */
    private String mode = "FULL";
    
    /**
     * Maximum age of behavior insights before considered stale.
     * When insights exceed this age, they won't be included in orchestration context.
     * 
     * Format: ISO-8601 Duration
     * Examples:
     * - PT1H = 1 hour (real-time apps)
     * - PT12H = 12 hours (e-commerce)
     * - PT24H = 24 hours (standard, default)
     * - PT48H = 48 hours (B2B/enterprise)
     * - PT168H = 168 hours (7 days, long-term trends)
     * 
     * Default: PT24H (24 hours)
     */
    private Duration maxInsightAge = Duration.ofHours(24);
    
    /**
     * Whether to trigger async re-analysis when stale insights are encountered.
     * If true, encountering stale insights will automatically queue re-analysis.
     * If false, stale insights are simply ignored.
     * 
     * Default: false
     */
    private boolean triggerReanalysisOnStale = false;
    
    /**
     * Minimum confidence threshold for using behavior insights.
     * Insights with confidence below this threshold are ignored.
     * 
     * Default: 0.5 (50%)
     */
    private double minConfidenceThreshold = 0.5;
}
```

---

## 📝 YAML Configuration Examples

### Before (Old Format - Inconsistent)

```yaml
ai:
  infrastructure:
    relationship:
      query-cache-ttl-seconds: 3600        # ❌ Unit in name
      timeout-seconds: 30                  # ❌ Unit in name
      
  indexing:
    async-worker:
      fixed-delay-seconds: 1               # ❌ Inconsistent
    cleanup:
      retention-days: 7                    # ❌ Different unit suffix
      
  behavior:
    processing:
      max-duration-minutes: 10             # ❌ Yet another suffix
```

### After (New Format - Consistent)

```yaml
ai:
  infrastructure:
    relationship:
      query-cache-ttl: PT1H                # ✅ ISO-8601
      timeout: PT30S                       # ✅ ISO-8601
      
  indexing:
    async-worker:
      fixed-delay: PT1S                    # ✅ ISO-8601
    queue:
      visibility-timeout: PT2M             # ✅ ISO-8601
    cleanup:
      stuck-threshold: PT10M               # ✅ ISO-8601
      sweep-interval: PT5M                 # ✅ ISO-8601
      completed-retention: PT168H          # ✅ ISO-8601 (7 days)
      dead-letter-retention: PT720H        # ✅ ISO-8601 (30 days)
      
  behavior:
    max-insight-age: PT24H                 # ✅ ISO-8601
    processing:
      scheduled-max-duration: PT10M        # ✅ ISO-8601
      api-max-duration: PT30M              # ✅ ISO-8601
      processing-delay: PT100MS            # ✅ ISO-8601
      continuous-interval: PT5M            # ✅ ISO-8601
```

---

## 🔧 Backward Compatibility Strategy

### Approach: Support Both Formats During Transition

**Java Properties Class:**

```java
@ConfigurationProperties(prefix = "ai.infrastructure.example")
@Data
public class ExampleProperties {
    
    /**
     * @deprecated Use {@link #timeout} instead. Will be removed in v3.0.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private Integer timeoutSeconds;
    
    /**
     * Timeout for operations.
     * Format: ISO-8601 Duration (e.g., PT30S)
     * Default: PT30S
     */
    private Duration timeout = Duration.ofSeconds(30);
    
    /**
     * Get timeout, supporting both old and new configuration.
     * @return Timeout duration
     */
    public Duration getTimeout() {
        if (timeoutSeconds != null) {
            log.warn("Property 'timeout-seconds' is deprecated. Use 'timeout: PT30S' (ISO-8601) instead");
            return Duration.ofSeconds(timeoutSeconds);
        }
        return timeout;
    }
}
```

**YAML (both work):**

```yaml
# NEW (preferred) - ISO-8601
ai:
  infrastructure:
    example:
      timeout: PT30S

# OLD (deprecated but still works)
ai:
  infrastructure:
    example:
      timeout-seconds: 30
```

---

## 📊 Migration Checklist

### Core Module
- [x] AIIndexingProperties - Already uses Duration ✅
- [ ] Add ISO-8601 examples to JavaDoc
- [ ] Update example YAML configs

### Relationship Query Module
- [ ] RelationshipQueryProperties - Migrate from long/int to Duration
- [ ] Add deprecated properties with backward compatibility
- [ ] Add getter methods supporting both formats
- [ ] Update YAML examples
- [ ] Update user guide

### Behavior Module
- [x] BehaviorProcessingProperties - Already uses Duration ✅
- [ ] Create BehaviorProperties with maxInsightAge
- [ ] Add ISO-8601 examples to JavaDoc
- [ ] Update YAML examples
- [ ] Update user guide

### Documentation
- [ ] Create configuration standards guide
- [ ] Update all YAML examples to use ISO-8601
- [ ] Add migration guide for existing users
- [ ] Document backward compatibility approach

---

## 📚 Configuration Standards Guide

### For Framework Developers

**Rule 1: Use Duration Type**
```java
// ✅ Correct
private Duration timeout = Duration.ofSeconds(30);

// ❌ Incorrect
private int timeoutSeconds = 30;
private long ttlSeconds = 3600;
```

**Rule 2: No Unit Suffixes in Property Names**
```java
// ✅ Correct
private Duration timeout;
private Duration cacheTtl;
private Duration maxAge;

// ❌ Incorrect
private Duration timeoutSeconds;
private Duration cacheTtlMinutes;
private Duration maxAgeDays;
```

**Rule 3: Document ISO-8601 Format**
```java
/**
 * Timeout for operations.
 * Format: ISO-8601 Duration (e.g., PT30S for 30 seconds)
 * Default: PT30S
 */
private Duration timeout = Duration.ofSeconds(30);
```

**Rule 4: Provide Backward Compatibility**
```java
// Keep old property, mark deprecated
@Deprecated(since = "2.0", forRemoval = true)
private Integer timeoutSeconds;

// Getter supports both
public Duration getTimeout() {
    if (timeoutSeconds != null) {
        return Duration.ofSeconds(timeoutSeconds);
    }
    return timeout;
}
```

---

## 🎯 Usage Examples

### Example 1: Real-time Trading App

```yaml
ai:
  behavior:
    max-insight-age: PT1H  # Refresh hourly
    trigger-reanalysis-on-stale: true
    
  indexing:
    async-worker:
      fixed-delay: PT500MS  # Process fast
    cleanup:
      stuck-threshold: PT2M  # Quick detection
```

### Example 2: E-commerce Platform

```yaml
ai:
  behavior:
    max-insight-age: PT12H  # Twice daily
    trigger-reanalysis-on-stale: true
    processing:
      continuous-interval: PT15M  # Process every 15 minutes
```

### Example 3: B2B Enterprise SaaS

```yaml
ai:
  behavior:
    max-insight-age: PT48H  # 2 days
    trigger-reanalysis-on-stale: false
    processing:
      scheduled-max-duration: PT1H  # Longer batch windows
      continuous-interval: PT30M  # Less frequent
```

### Example 4: Content Platform

```yaml
ai:
  behavior:
    max-insight-age: PT168H  # 7 days
    trigger-reanalysis-on-stale: false
    processing:
      continuous-interval: PT6H  # Process 4x per day
```

---

## 🧪 Testing Configurations

### Development
```yaml
ai:
  behavior:
    max-insight-age: PT1H  # Short for testing
  indexing:
    cleanup:
      completed-retention: PT1H  # Clean up quickly
```

### Testing/CI
```yaml
ai:
  behavior:
    max-insight-age: PT1M  # Very short for tests
    trigger-reanalysis-on-stale: false  # Disable async in tests
  indexing:
    async-worker:
      fixed-delay: PT100MS  # Process fast
```

### Production
```yaml
ai:
  behavior:
    max-insight-age: PT24H  # Standard
  indexing:
    cleanup:
      completed-retention: PT168H  # 7 days
      dead-letter-retention: PT720H  # 30 days
```

---

## 📈 Migration Timeline

### v2.0 (Current Release)
- ✅ Add new Duration properties with ISO-8601 support
- ✅ Keep old properties (deprecated)
- ✅ Support both formats
- ✅ Log warnings when old format used
- ✅ Update documentation with migration guide

### v2.1 (Next Minor Release)
- ✅ Increase deprecation warnings
- ✅ Update all examples to use new format
- ✅ Add migration notes to changelog

### v3.0 (Next Major Release)
- ✅ Remove deprecated properties
- ✅ Only ISO-8601 format supported
- ✅ Breaking change documented

---

## 🎨 IDE Support

Modern IDEs support ISO-8601 Duration format:

**IntelliJ IDEA:**
- Autocomplete for Duration properties
- Validation for ISO-8601 format
- Quick-fix suggestions

**VS Code (with Spring Boot extension):**
- Syntax highlighting
- Format validation
- Hover documentation

---

## 📝 Documentation Updates Required

### 1. Configuration Standards Guide (New)

**Location:** `ai-infrastructure-module/docs/CONFIGURATION_STANDARDS.md`

```markdown
# AI Infrastructure Configuration Standards

## Duration Properties

All duration/timeout/age/ttl properties use ISO-8601 Duration format.

### Format Syntax

PT[n]H[n]M[n]S or P[n]D

Examples:
- PT30S = 30 seconds
- PT5M = 5 minutes
- PT1H = 1 hour
- PT24H = 24 hours
- PT168H = 7 days (168 hours)
- PT1H30M = 1 hour 30 minutes

### Java

```java
@ConfigurationProperties(prefix = "ai.example")
public class ExampleProperties {
    private Duration timeout = Duration.ofSeconds(30);
}
```

### YAML

```yaml
ai:
  example:
    timeout: PT30S
```

### Common Presets

- Real-time: PT1H
- E-commerce: PT12H
- Standard: PT24H (default for most)
- B2B: PT48H
- Long-term: PT168H (7 days)
```

### 2. Migration Guide (New)

**Location:** `ai-infrastructure-module/docs/MIGRATION_GUIDE_V2_TO_V3.md`

```markdown
# Migration Guide: v2.x to v3.0

## Breaking Change: Duration Property Format

### What Changed

All duration properties now use ISO-8601 format instead of numeric values with unit suffixes.

### OLD Format (v1.x, deprecated in v2.x)

```yaml
ai:
  infrastructure:
    relationship:
      query-cache-ttl-seconds: 3600
      timeout-seconds: 30
```

### NEW Format (v2.x+, required in v3.0)

```yaml
ai:
  infrastructure:
    relationship:
      query-cache-ttl: PT1H    # 1 hour
      timeout: PT30S           # 30 seconds
```

### Migration Steps

1. Search for properties ending in `-seconds`, `-minutes`, `-hours`, `-days`
2. Convert to ISO-8601 format
3. Remove unit suffix from property name
4. Test configuration

### Conversion Table

| Old | New | Notes |
|-----|-----|-------|
| `timeout-seconds: 30` | `timeout: PT30S` | |
| `ttl-seconds: 3600` | `ttl: PT1H` | 1 hour |
| `retention-days: 7` | `retention: PT168H` | 7 days |
| `delay-ms: 100` | `delay: PT100MS` | |

### Backward Compatibility (v2.x only)

During v2.x, both formats work:

```yaml
# Both work in v2.x
timeout: PT30S          # Preferred
timeout-seconds: 30     # Deprecated, logs warning
```

v3.0 removes old format support.
```

### 3. Update All Module User Guides

Add ISO-8601 configuration examples to:
- Core module user guide
- Relationship query user guide
- Behavior module user guide
- Each provider user guide

---

## 🔍 Affected Properties Inventory

### ai-infrastructure-core

| Current Property | Type | New Property | Format |
|-----------------|------|-------------|--------|
| `indexing.async-worker.fixed-delay` | Duration ✅ | Same | PT1S |
| `indexing.queue.visibility-timeout` | Duration ✅ | Same | PT2M |
| `indexing.cleanup.stuck-threshold` | Duration ✅ | Same | PT10M |
| `indexing.cleanup.sweep-interval` | Duration ✅ | Same | PT5M |
| `indexing.cleanup.completed-retention` | Duration ✅ | Same | PT168H |
| `indexing.cleanup.dead-letter-retention` | Duration ✅ | Same | PT720H |
| `intent-history.retention-days` | int ❌ | `retention` | PT720H |

### ai-infrastructure-relationship-query

| Current Property | Type | New Property | Format |
|-----------------|------|-------------|--------|
| `query-cache-ttl-seconds` | long ❌ | `query-cache-ttl` | PT1H |
| `planner.timeout-seconds` | int ❌ | `planner.timeout` | PT30S |
| `cache.regions.*.ttl-seconds` | long ❌ | `cache.regions.*.ttl` | PT1H |

### ai-infrastructure-behavior

| Current Property | Type | New Property | Format |
|-----------------|------|-------------|--------|
| `processing.scheduled-max-duration` | Duration ✅ | Same | PT10M |
| `processing.api-max-duration` | Duration ✅ | Same | PT30M |
| `processing.processing-delay` | Duration ✅ | Same | PT100MS |
| `processing.continuous-interval` | Duration ✅ | Same | PT5M |
| N/A (new) | - | `max-insight-age` | PT24H |

---

## 🚀 Implementation Checklist

### Phase 1: Core Module
- [x] AIIndexingProperties - Already compliant ✅
- [ ] Add ISO-8601 JavaDoc
- [ ] Migrate IntentHistoryProperties (retention-days → retention)
- [ ] Update YAML examples

### Phase 2: Relationship Query Module
- [ ] Migrate RelationshipQueryProperties to Duration type
- [ ] Add deprecated properties for backward compatibility
- [ ] Add getter methods supporting both old and new
- [ ] Update YAML examples
- [ ] Update user guide

### Phase 3: Behavior Module
- [x] BehaviorProcessingProperties - Already compliant ✅
- [ ] Create BehaviorProperties class
- [ ] Add maxInsightAge property (ISO-8601)
- [ ] Add triggerReanalysisOnStale property
- [ ] Update YAML examples
- [ ] Update user guide

### Phase 4: Documentation
- [ ] Create CONFIGURATION_STANDARDS.md
- [ ] Create MIGRATION_GUIDE_V2_TO_V3.md
- [ ] Update all user guides with ISO-8601 examples
- [ ] Update README configuration sections
- [ ] Add ISO-8601 quick reference

### Phase 5: Testing
- [ ] Test backward compatibility (old format still works)
- [ ] Test new format
- [ ] Test deprecation warnings logged
- [ ] Update all test YAML configs to new format
- [ ] Verify Spring Boot conversion works correctly

---

## 📊 Success Criteria

✅ **All duration properties use Duration type in Java**  
✅ **All YAML examples use ISO-8601 format**  
✅ **Backward compatibility maintained (v2.x)**  
✅ **Deprecation warnings logged for old format**  
✅ **Documentation updated with ISO-8601 examples**  
✅ **Migration guide created**  
✅ **Configuration standards documented**  
✅ **Tests updated and passing**  

---

## 🎯 Quick Reference: ISO-8601 Durations

### Common Patterns

```yaml
# Milliseconds
delay: PT100MS
delay: PT0.1S        # Alternative (100ms = 0.1s)

# Seconds
timeout: PT30S
timeout: PT0.5M      # Alternative (30s = 0.5m)

# Minutes
interval: PT5M
interval: PT300S     # Alternative (5m = 300s)

# Hours
ttl: PT1H
ttl: PT60M           # Alternative (1h = 60m)

# Days (use hours for consistency)
retention: PT24H     # 1 day (preferred)
retention: P1D       # 1 day (alternative)
retention: PT168H    # 7 days (preferred over P7D)

# Complex
window: PT1H30M      # 1 hour 30 minutes
max-wait: PT2H45M30S # 2 hours 45 minutes 30 seconds
```

### Conversion Table

| Plain Value | ISO-8601 | Readable |
|------------|----------|----------|
| 1 second | PT1S | 1 second |
| 30 seconds | PT30S | 30 seconds |
| 100 milliseconds | PT100MS | 100 milliseconds |
| 5 minutes | PT5M | 5 minutes |
| 1 hour | PT1H | 1 hour |
| 24 hours | PT24H | 24 hours (1 day) |
| 48 hours | PT48H | 48 hours (2 days) |
| 168 hours | PT168H | 168 hours (7 days) |
| 720 hours | PT720H | 720 hours (30 days) |

---

## 💡 Why Not Use Simple Numbers?

### Problem with Simple Numbers

```yaml
# What unit is this?
timeout: 30        # Seconds? Minutes? Milliseconds?
cache-ttl: 3600    # What is 3600? (1 hour in seconds)
retention: 7       # Days? Hours?
```

### Solution with ISO-8601

```yaml
# Crystal clear!
timeout: PT30S           # 30 seconds
cache-ttl: PT1H          # 1 hour (no math needed!)
retention: PT168H        # 168 hours = 7 days
```

---

## 🔗 References

- [Spring Boot Duration Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties.conversion.durations)
- [ISO-8601 Duration Specification](https://en.wikipedia.org/wiki/ISO_8601#Durations)
- [Java Duration Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html)

---

**Document Version:** 1.0  
**Created:** 2025-12-30  
**Status:** Ready for Implementation  
**Owner:** AI Infrastructure Team  
**Priority:** Medium (Applies to new properties immediately, migrate existing gradually)

**Key Decision:** Standardize on ISO-8601 Duration format for all duration-related configuration properties across the framework.



