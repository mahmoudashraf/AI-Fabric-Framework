# Behavior Analysis Module - User Guide

## Overview

The Behavior Analysis Module is an AI-powered system that analyzes user behavior patterns to generate actionable insights. It processes user events, tracks sentiment trends, predicts churn risk, and provides personalized recommendations using Large Language Models (LLMs).

### What This Module Does

- **Event-Driven Analysis**: Processes user events from external sources to generate behavioral insights
- **Sentiment Tracking**: Monitors user sentiment over time with 6-level classification (DELIGHTED → CHURNING)
- **Churn Prediction**: Calculates churn risk scores with explanatory reasons
- **Trend Detection**: Identifies behavioral trends (RAPIDLY_IMPROVING → RAPIDLY_DECLINING)
- **Smart Indexing**: Optional vector-based search and discovery (FULL mode)
- **Evolutionary Analysis**: Compares current behavior against historical baselines

### Target Audience

Developers integrating behavioral intelligence into applications, AI orchestrations, or customer success platforms.

---

## Quick Start

### 1. Enable the Module

Add to your `application.yml`:

```yaml
ai:
  behavior:
    enabled: true
    mode: LIGHT  # or FULL for advanced search capabilities
```

### 2. Implement Event Provider (Required)

Create a bean that implements `ExternalEventProvider`:

```java
@Component
public class MyEventProvider implements ExternalEventProvider {
    
    @Override
    public List<ExternalEvent> getEventsForUser(UUID userId, LocalDateTime since, LocalDateTime until) {
        // Fetch user events from your database/analytics system
        // Examples: login, page_view, feature_usage, error_encountered, support_ticket
        return myEventService.getEventsForUser(userId, since, until);
    }
    
    @Override
    public UserEventBatch getNextUserEvents() {
        // Return next user pending analysis (for batch/discovery processing)
        // Can return null when no users are pending
        User nextUser = myUserQueue.getNextPendingUser();
        if (nextUser == null) return null;
        
        return UserEventBatch.builder()
            .userId(nextUser.getId())
            .events(myEventService.getRecentEvents(nextUser.getId()))
            .totalEventCount(events.size())
            .userContext(Map.of("tier", nextUser.getTier(), "region", nextUser.getRegion()))
            .build();
    }
}
```

### 3. Run Analysis

```bash
# Analyze a specific user
POST /api/behavior/processing/users/{userId}

# Process batch of users
POST /api/behavior/processing/batch

# Start continuous background processing
POST /api/behavior/processing/continuous
```

### 4. Consume Insights

```java
@Autowired
private BehaviorInsightsRepository repository;

public void checkUserHealth(UUID userId) {
    Optional<BehaviorInsights> insights = repository.findByUserId(userId);
    
    insights.ifPresent(insight -> {
        if (insight.requiresImmediateAction()) {
            alertCustomerSuccess(userId, insight);
        }
    });
}
```

---

## Core Concepts

### Modes: LIGHT vs FULL

| Feature | LIGHT | FULL |
|---------|-------|------|
| Behavior Analysis | ✅ | ✅ |
| JPA Persistence | ✅ | ✅ |
| Auto-Embedding | ❌ | ✅ |
| Vector Search | ❌ | ✅ |
| Relationship Query | ❌ | ✅ |
| Resource Usage | Minimal | Higher |

**LIGHT Mode**: Production-ready, minimal footprint. Use when you only need analysis and REST API access.

**FULL Mode**: Enables AI-powered search across insights. Use when building discovery features or complex queries.

### Data Model

#### BehaviorInsights Entity

The core entity stored for each user:

```java
@Entity
@Table(name = "ai_behavior_insights")
public class BehaviorInsights {
    UUID id;                    // Primary key
    UUID userId;                // Unique per user
    
    // AI-Generated Insights
    String segment;             // User segment (e.g., "Power User", "At-Risk")
    List<String> patterns;      // Detected behavior patterns
    List<String> recommendations; // Action recommendations
    Map<String, Object> insights; // Additional structured insights
    
    // Sentiment Analysis
    Double sentimentScore;      // -1.0 (negative) to 1.0 (positive)
    SentimentLabel sentimentLabel; // DELIGHTED | SATISFIED | NEUTRAL | CONFUSED | FRUSTRATED | CHURNING
    
    // Churn Prediction
    Double churnRisk;           // 0.0 (safe) to 1.0 (churning)
    String churnReason;         // Explanation for churn risk
    
    // Trend Tracking
    Double previousSentimentScore;
    Double previousChurnRisk;
    BehaviorTrend trend;        // Overall trajectory
    
    // Metadata
    LocalDateTime analyzedAt;
    Double confidence;          // 0.0 to 1.0
    String aiModelUsed;         // e.g., "gpt-4o"
    String modelPromptVersion;
    Long processingTimeMs;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

#### SentimentLabel Enum

6-level sentiment classification:

```java
public enum SentimentLabel {
    DELIGHTED("Extremely positive engagement"),
    SATISFIED("Positive experience"),
    NEUTRAL("No strong sentiment"),
    CONFUSED("Help-seeking behavior"),
    FRUSTRATED("Friction detected"),
    CHURNING("Imminent departure signals");
}
```

#### BehaviorTrend Enum

Directional trend indicators:

```java
public enum BehaviorTrend {
    RAPIDLY_IMPROVING("Major positive shift"),
    IMPROVING("Positive shift"),
    STABLE("No significant change"),
    DECLINING("Negative shift"),
    RAPIDLY_DECLINING("Major negative shift - ALERT"),
    NEW_USER("Baseline analysis");
}
```

Trends are computed from sentiment and churn deltas:
- **RAPIDLY_DECLINING**: sentimentDelta < -0.4 OR churnDelta > 0.4
- **DECLINING**: sentimentDelta < -0.2 OR churnDelta > 0.2
- **RAPIDLY_IMPROVING**: sentimentDelta > 0.4 OR churnDelta < -0.4
- **IMPROVING**: sentimentDelta > 0.2 OR churnDelta < -0.2
- **STABLE**: All other cases

#### ExternalEvent Model

Input format for user events:

```java
@Data
@Builder
public class ExternalEvent {
    String eventType;              // e.g., "page_view", "feature_used", "error"
    Map<String, Object> eventData; // Event-specific data
    LocalDateTime timestamp;
    String source;                 // Event source identifier
}
```

---

## Configuration Reference

### Core Settings

```yaml
ai:
  behavior:
    enabled: true              # Enable/disable module (default: false)
    mode: LIGHT                # LIGHT or FULL (default: LIGHT)
    
    processing:
      # API Processing
      api-enabled: true        # Enable processing REST API (default: true)
      api-max-batch-size: 1000 # Max users per batch request (default: 1000)
      api-max-duration: PT30M  # Max duration for batch (default: 30 minutes)
      
      # Scheduled Background Processing
      scheduled-enabled: false # Enable cron-based worker (default: false)
      schedule-cron: "0 */15 * * * *"  # Every 15 minutes (default)
      scheduled-batch-size: 100        # Users per scheduled run (default: 100)
      scheduled-max-duration: PT10M    # Max time per scheduled run (default: 10 min)
      
      # Throttling
      processing-delay: PT0.1S # Delay between user analyses (default: 100ms)
      
      # Continuous Processing
      continuous-users-per-batch: 100  # Users per continuous batch (default: 100)
      continuous-interval: PT5M        # Interval between batches (default: 5 min)
```

### Preset Files

Located in `behavior-presets/`:

**behavior-ai-light.yml**:
```yaml
ai-entities:
  behavior-insight:
    auto-embedding: false
    indexable: false
    features: ["analysis"]
    crud-operations:
      create:
        generate-embedding: false
        index-for-search: false
```

**behavior-ai-full.yml**:
```yaml
ai-entities:
  behavior-insight:
    auto-embedding: true
    indexable: true
    features: ["embedding", "search", "analysis"]
    searchable-fields:
      - name: segment
        weight: 2.0
      - name: patterns
        weight: 1.5
    metadata-fields:
      - name: segment
        type: string
        include-in-search: true
      - name: confidence
        type: double
        include-in-search: true
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
```

---

## Processing APIs

### 1. Targeted User Analysis

Analyze a specific user on-demand.

**Endpoint**: `POST /api/behavior/processing/users/{userId}`

**Request**: No body required

**Response**: 
```json
{
  "id": "uuid",
  "userId": "uuid",
  "segment": "Power User",
  "patterns": [
    "Daily active user",
    "Uses advanced features",
    "Low error rate"
  ],
  "sentimentScore": 0.85,
  "sentimentLabel": "SATISFIED",
  "churnRisk": 0.15,
  "churnReason": "Low engagement with new features",
  "trend": "STABLE",
  "recommendations": [
    "Introduce to beta features",
    "Invite to user community"
  ],
  "confidence": 0.92,
  "analyzedAt": "2025-12-28T10:30:00"
}
```

**HTTP Status**:
- `200 OK`: Analysis successful
- `204 No Content`: No events available for user

### 2. Batch Processing

Process multiple users in a single request.

**Endpoint**: `POST /api/behavior/processing/batch`

**Request Body** (all fields optional):
```json
{
  "maxUsers": 50,
  "maxDuration": "PT5M"
}
```

**Response**:
```json
{
  "processedCount": 47,
  "failedCount": 3,
  "duration": "PT4M32S",
  "insights": [
    { /* BehaviorInsights objects */ }
  ]
}
```

### 3. Continuous Processing

Start a background job that continuously processes users.

**Endpoint**: `POST /api/behavior/processing/continuous`

**Request Body**:
```json
{
  "usersPerBatch": 100,
  "interval": "PT5M",
  "maxDuration": "PT1H"
}
```

**Response**:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "startedAt": "2025-12-28T10:00:00",
  "processedCount": 0
}
```

**Cancel Continuous Job**: `POST /api/behavior/processing/continuous/{jobId}/cancel`

### 4. Scheduled Processing Control

Control the scheduled background worker.

**Pause**: `POST /api/behavior/processing/scheduled/pause`

**Resume**: `POST /api/behavior/processing/scheduled/resume`

**Response**:
```json
{
  "status": "PAUSED",
  "message": "Scheduled processing paused"
}
```

---

## Analytics APIs

### 1. Rapid Decline Alerts

Get users requiring immediate attention.

**Endpoint**: `GET /api/behavior/analytics/rapid-decline`

**Response**:
```json
[
  {
    "userId": "uuid",
    "sentiment": "FRUSTRATED",
    "churnRisk": 0.87,
    "churnReason": "Multiple errors in critical workflow",
    "trend": "RAPIDLY_DECLINING",
    "recommendations": [
      "Immediate customer success intervention",
      "Technical support outreach"
    ],
    "analyzedAt": "2025-12-28T09:15:00"
  }
]
```

### 2. Trend Distribution

Get count of users by trend category.

**Endpoint**: `GET /api/behavior/analytics/trend-distribution`

**Response**:
```json
{
  "RAPIDLY_IMPROVING": 45,
  "IMPROVING": 132,
  "STABLE": 1567,
  "DECLINING": 78,
  "RAPIDLY_DECLINING": 23,
  "NEW_USER": 156
}
```

### 3. Sentiment Distribution

Get count of users by sentiment label.

**Endpoint**: `GET /api/behavior/analytics/sentiment-distribution`

**Response**:
```json
{
  "DELIGHTED": 234,
  "SATISFIED": 890,
  "NEUTRAL": 456,
  "CONFUSED": 123,
  "FRUSTRATED": 67,
  "CHURNING": 31
}
```

### 4. User Trend Details

Get detailed trend analysis for a specific user.

**Endpoint**: `GET /api/behavior/analytics/users/{userId}/trend`

**Response**:
```json
{
  "userId": "uuid",
  "currentSentiment": 0.65,
  "previousSentiment": 0.82,
  "sentimentDelta": -0.17,
  "currentChurnRisk": 0.45,
  "previousChurnRisk": 0.28,
  "churnDelta": 0.17,
  "trend": "DECLINING",
  "churnReason": "Reduced feature usage",
  "recommendations": [
    "Re-engagement campaign",
    "Feature adoption guidance"
  ],
  "analyzedAt": "2025-12-28T08:00:00"
}
```

**HTTP Status**:
- `200 OK`: User found
- `404 Not Found`: No insights for user

---

## Repository Access

Direct database access via Spring Data JPA.

```java
@Autowired
private BehaviorInsightsRepository repository;

// Find by user ID
Optional<BehaviorInsights> insights = repository.findByUserId(userId);

// Find by trend
List<BehaviorInsights> declining = repository.findByTrend(BehaviorTrend.DECLINING);

// Find by sentiment
List<BehaviorInsights> frustrated = repository.findBySentimentLabel(SentimentLabel.FRUSTRATED);

// Get rapid decline alerts
List<BehaviorInsights> alerts = repository.findRapidlyDecliningUsers();

// Standard JPA operations
repository.findAll();
repository.save(insight);
repository.deleteByUserId(userId);
```

---

## Advanced Features

### Custom Storage Implementation

Override default JPA storage with custom implementation:

```java
@Component
public class RedisInsightStore implements BehaviorInsightStore {
    
    @Override
    public void save(BehaviorInsights insight) {
        // Store in Redis, MongoDB, or external system
    }
    
    @Override
    public Optional<BehaviorInsights> findByUserId(UUID userId) {
        // Retrieve from custom storage
    }
    
    @Override
    public void deleteByUserId(UUID userId) {
        // Delete from custom storage
    }
}
```

When this bean exists, it becomes the single source of truth for all read/write operations.

### Computed Properties

`BehaviorInsights` provides transient methods for analysis:

```java
// Calculate deltas
Double sentimentDelta = insight.getSentimentDelta();  // current - previous
Double churnDelta = insight.getChurnDelta();

// Quick checks
boolean improving = insight.isSentimentImproving();        // delta > 0.2
boolean risky = insight.isChurnRiskIncreasing();          // delta > 0.2
boolean urgent = insight.requiresImmediateAction();       // RAPIDLY_DECLINING or churn > 0.8

// Get searchable content (for FULL mode)
String content = insight.getSearchableContent();
```

### Scheduled Background Worker

Enable automatic periodic processing:

```yaml
ai:
  behavior:
    processing:
      scheduled-enabled: true
      schedule-cron: "0 0 */6 * * *"  # Every 6 hours
      scheduled-batch-size: 500
      scheduled-max-duration: PT30M
```

Worker implementation (`BehaviorAnalysisWorker`):
- Runs on schedule if not paused
- Processes users via `ExternalEventProvider.getNextUserEvents()`
- Respects batch size and max duration limits
- Can be paused/resumed via API

### Observability

**Logging**:
- `INFO`: New user analysis, trend changes
- `WARN`: Negative trend detection, invalid LLM outputs
- `ERROR`: Analysis failures, LLM errors

**Metrics** (if Micrometer present):
- `ai.behavior.processing.processed`: Counter of successful analyses
- `ai.behavior.processing.errors`: Counter of failures

**Trend Alerts**:
```
WARN: User 123e4567-e89b-12d3-a456-426614174000 trend worsening: STABLE -> DECLINING
```

---

## AI Integration Details

### LLM Analysis Process

1. **Context Building**: Combines previous insights + new events + user context
2. **Prompt Generation**: Evolutionary prompt asking for trend detection
3. **LLM Call**: Via `AICoreService` with temperature=0.2, maxTokens=1200
4. **Response Parsing**: Extracts JSON and validates fields
5. **Fallback Handling**: Returns existing insights or safe defaults on failure

### System Prompt

```
You are an expert Behavioral Psychologist specializing in TREND DETECTION.

Analyze user behavior and detect CHANGES over time.

Output Dimensions:
1. Segment
2. Patterns
3. Sentiment {score: -1..1, label: DELIGHTED|SATISFIED|NEUTRAL|CONFUSED|FRUSTRATED|CHURNING}
4. Churn {risk: 0..1, reason: string}
5. Trend {RAPIDLY_IMPROVING|IMPROVING|STABLE|DECLINING|RAPIDLY_DECLINING|NEW_USER}
6. Recommendations
7. Insights
8. Confidence (0..1)

Respond with valid JSON.
```

### Validation & Defaults

- **Sentiment Score**: Clamped to [-1.0, 1.0]
- **Churn Risk**: Clamped to [0.0, 1.0]
- **Invalid Labels**: Default to NEUTRAL
- **Invalid Trends**: Recomputed from deltas
- **Missing Churn Reason**: Auto-filled if risk > 0.5
- **Confidence**: Defaults to 0.5

### Error Handling

**LLM Failure**:
1. Returns existing insight if available
2. Creates fallback insight with confidence=0.0, trend=STABLE

**No Events**:
1. Returns existing insight if available
2. Returns `null` for new users

---

## Database Schema

### Table: `ai_behavior_insights`

```sql
CREATE TABLE ai_behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    
    -- AI Insights
    segment VARCHAR(100),
    patterns JSONB,
    recommendations JSONB,
    insights JSONB,
    
    -- Sentiment
    sentiment_score DOUBLE PRECISION,
    sentiment_label VARCHAR(50),
    
    -- Churn
    churn_risk DOUBLE PRECISION,
    churn_reason TEXT,
    
    -- Trend Tracking
    previous_sentiment_score DOUBLE PRECISION,
    previous_churn_risk DOUBLE PRECISION,
    trend VARCHAR(50),
    
    -- Metadata
    analyzed_at TIMESTAMP NOT NULL,
    confidence DOUBLE PRECISION,
    ai_model_used VARCHAR(100),
    model_prompt_version VARCHAR(20),
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Indexes
CREATE INDEX idx_insights_user ON ai_behavior_insights(user_id);
CREATE INDEX idx_insights_segment ON ai_behavior_insights(segment);
CREATE INDEX idx_insights_sentiment ON ai_behavior_insights(sentiment_label);
CREATE INDEX idx_insights_churn ON ai_behavior_insights(churn_risk);
CREATE INDEX idx_insights_trend ON ai_behavior_insights(trend);
```

---

## Best Practices

### Event Provider Implementation

✅ **DO**:
- Return deterministic results in tests
- Include relevant metadata (timestamps, event types)
- Implement efficient pagination for `getNextUserEvents()`
- Handle null `since`/`until` gracefully (use sensible defaults)

❌ **DON'T**:
- Store raw events in the Behavior Module (it's event-agnostic)
- Return millions of events (use time windows)
- Block for long periods (implement timeouts)

### Performance Optimization

```yaml
# For high-volume systems
ai:
  behavior:
    processing:
      processing-delay: PT0.05S  # 50ms delay (faster)
      api-max-batch-size: 5000   # Larger batches
      scheduled-batch-size: 1000
```

### Testing

```java
@Test
public void testBehaviorAnalysis() {
    // Use LIGHT mode for tests
    // Mock ExternalEventProvider
    // Compare deltas with tolerance (not exact equality)
    
    assertThat(insight.getSentimentDelta()).isCloseTo(-0.15, within(0.01));
}
```

### Monitoring

```java
// Check for users needing intervention
@Scheduled(fixedRate = 300000) // Every 5 minutes
public void checkHighRiskUsers() {
    List<BehaviorInsights> alerts = repository.findRapidlyDecliningUsers();
    
    alerts.stream()
        .filter(BehaviorInsights::requiresImmediateAction)
        .forEach(insight -> {
            notifyCustomerSuccess(insight.getUserId(), insight);
        });
}
```

### Integration with AI Orchestration

```java
// Use insights in LLM prompts
public String buildContextualPrompt(UUID userId, String userQuery) {
    Optional<BehaviorInsights> insights = repository.findByUserId(userId);
    
    return insights.map(i -> String.format(
        "User Context:\n" +
        "- Segment: %s\n" +
        "- Sentiment: %s\n" +
        "- Patterns: %s\n" +
        "- Recommendations: %s\n\n" +
        "User Query: %s",
        i.getSegment(),
        i.getSentimentLabel(),
        String.join(", ", i.getPatterns()),
        String.join(", ", i.getRecommendations()),
        userQuery
    )).orElse(userQuery);
}
```

---

## Troubleshooting

### Module Not Starting

**Issue**: No behavior beans detected

**Solution**: Ensure `ai.behavior.enabled=true` and `ExternalEventProvider` bean exists

### No Insights Generated

**Issue**: Analysis runs but returns null

**Solution**: 
1. Verify `ExternalEventProvider` returns events
2. Check logs for LLM errors
3. Confirm AI Core Service is configured

### Inaccurate Trends

**Issue**: Trends don't match actual behavior

**Solution**:
1. Review event data quality
2. Check if sentiment/churn deltas are being tracked
3. Verify previous values are persisted correctly

### Performance Issues

**Issue**: Batch processing is slow

**Solution**:
1. Reduce `processing-delay` for faster throughput
2. Increase `api-max-batch-size`
3. Optimize `ExternalEventProvider` queries
4. Consider async/parallel processing in provider

### FULL Mode Search Not Working

**Issue**: Vector search returns no results

**Solution**:
1. Verify embedding provider is configured
2. Check `behavior-ai-full.yml` is loaded (check logs)
3. Ensure entities are being indexed (check `@AIProcess` execution)

---

## Example Use Cases

### 1. Proactive Customer Success

```java
@Component
public class ChurnPreventionService {
    
    @Autowired
    private BehaviorInsightsRepository repository;
    
    @Scheduled(cron = "0 0 9 * * MON")  // Every Monday 9am
    public void identifyAtRiskUsers() {
        List<BehaviorInsights> atRisk = repository.findAll().stream()
            .filter(i -> i.getChurnRisk() > 0.7)
            .filter(i -> i.getTrend().isNegative())
            .toList();
        
        atRisk.forEach(insight -> {
            createCustomerSuccessTask(
                insight.getUserId(),
                insight.getChurnReason(),
                insight.getRecommendations()
            );
        });
    }
}
```

### 2. Dynamic Feature Recommendations

```java
public List<String> getPersonalizedFeatures(UUID userId) {
    return repository.findByUserId(userId)
        .map(insight -> {
            if (insight.getSentimentLabel() == SentimentLabel.CONFUSED) {
                return List.of("guided-tour", "help-center", "onboarding-refresh");
            } else if (insight.getSegment().equals("Power User")) {
                return List.of("beta-features", "api-access", "advanced-analytics");
            }
            return insight.getRecommendations();
        })
        .orElse(Collections.emptyList());
}
```

### 3. Sentiment Dashboard

```java
@GetMapping("/dashboard/user-health")
public DashboardDTO getUserHealthDashboard() {
    Map<String, Long> sentimentDist = analyticsController
        .getSentimentDistribution()
        .getBody();
    
    Map<String, Long> trendDist = analyticsController
        .getTrendDistribution()
        .getBody();
    
    List<TrendAlertDTO> alerts = analyticsController
        .getRapidDeclineAlerts()
        .getBody();
    
    return DashboardDTO.builder()
        .sentimentBreakdown(sentimentDist)
        .trendBreakdown(trendDist)
        .urgentAlerts(alerts)
        .healthScore(calculateHealthScore(sentimentDist, trendDist))
        .build();
}
```

---

## Migration Guide

### From No Behavior Tracking

1. Enable module in configuration
2. Implement `ExternalEventProvider` for your event source
3. Run initial batch: `POST /api/behavior/processing/batch?maxUsers=10000`
4. Enable scheduled processing for ongoing updates

### Adding Custom Storage

1. Implement `BehaviorInsightStore`
2. Migrate existing data from `ai_behavior_insights` table
3. Register as Spring bean
4. Remove old JPA repository usage

---

## FAQ

**Q: How often should I run analysis?**
A: Depends on event volume. High-activity apps: every 15-60 minutes. Low-activity: daily/weekly.

**Q: Can I customize the LLM prompt?**
A: Currently system prompt is fixed. Consider extending `BehaviorAnalysisService` for custom prompts.

**Q: What's the cost per user analysis?**
A: Depends on LLM provider. ~500-1000 tokens per analysis. Use LIGHT mode for cost efficiency.

**Q: How do I handle GDPR deletion?**
A: Call `repository.deleteByUserId(userId)` or implement in custom `BehaviorInsightStore`.

**Q: Can I analyze users without events?**
A: No. The module requires events to generate insights. Returns existing insights or null.

**Q: What models are supported?**
A: Any model configured in AI Core Service (OpenAI, Anthropic, Azure, etc.).

---

## Version Information

- **Module Version**: 3.1.0
- **Prompt Version**: 3.1.0
- **Minimum Java**: 17
- **Spring Boot**: 3.x
- **Dependencies**: ai-infrastructure-core, ai-infrastructure-relationship-query (FULL mode)

---

## Support & Resources

- **Source Code**: `com.ai.infrastructure.behavior`
- **Entity**: `BehaviorInsights.java`
- **Configuration**: `BehaviorProcessingProperties.java`
- **Integration Tests**: `behavior-integration-tests/`
- **Technical Spec**: See `BEHAVIOR_REALAPI_INTEGRATION_TEST_SPECIFICATION.md`

---

*This guide reflects the actual implementation in the codebase. For framework-wide features (embeddings, search, orchestration), refer to the main AI Infrastructure documentation.*

