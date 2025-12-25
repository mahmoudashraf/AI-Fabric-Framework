# AI Behavior Module v2 - Comprehensive Implementation Specification

**Version:** 2.1  
**Status:** Architecture Finalized  
**Strategic Alignment:** AI Fabric Framework Native Addon

---

## 📋 1. Executive Summary

The AI Behavior Module v2 is designed as a **Temporal-to-Semantic** pipeline that transforms raw user events into high-level AI insights. It operates in two distinct modes—**LIGHT (Context)** and **FULL (Discovery)**—allowing developers to balance between low-cost personalization and high-power behavioral search and statistics.

---

## 🏗️ 2. Architectural Principles

### 2.1 Mode-Based Toggling
- **LIGHT Mode (Default):** Focuses on single-user personalization. Insights are stored in the relational database. No indexing or vector embeddings are generated. Use case: Providing context to an LLM during a chat.
- **FULL Mode:** Enables cross-user analytics. Insights are automatically indexed into `AISearchableEntity` and the Vector Database via the Framework's `@AIProcess` mechanism. Use case: "Find all users at risk of churn."

### 2.2 Temporal-to-Semantic Flow
1.  **Ingestion:** Raw events are captured in high-speed temporary storage with a **24-hour TTL**.
2.  **Analysis:** A stateful background worker aggregates events and generates structured insights via LLM.
3.  **Persistence:** Insights are stored permanently (Relational) and optionally indexed (Semantic).

---

## 🗄️ 3. Data Layer Design

### 3.1 Temporary Event Storage (`ai_behavior_events_temp`)
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `user_id` | UUID | Indexed. The target user. |
| `event_type` | String | Simple string (e.g., "purchase", "click"). |
| `event_data` | JSONB | Flexible payload for event attributes. |
| `processed` | Boolean | Flag for the analysis worker. |
| `expires_at` | Timestamp | TTL marker (NOW + 24h). |

### 3.2 Persistent AI Insights (`ai_behavior_insights`)
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `user_id` | UUID | Unique Index. |
| `segment` | String | AI-generated category (e.g., "Power User"). |
| `patterns` | JSON (Array) | List of detected behavioral patterns. |
| `recommendations` | JSON (Array) | AI-suggested next best actions. |
| `analyzed_at` | Timestamp | Last successful LLM analysis. |
| `confidence` | Double | AI confidence score (0.0-1.0). |

---

## ⚙️ 4. Addon Configuration (Zero-Config Approach)

The module ships with internal presets that the user can toggle via `application.yml`.

### 4.1 Internal Presets (`behavior-ai-*.yml`)
The addon includes internal YAML files that are registered programmatically with the `AIEntityConfigurationLoader` only if the user hasn't provided a custom override for the `behavior-insight` entity.

### 4.2 User Configuration (`application.yml`)
```yaml
ai:
  behavior:
    enabled: true
    mode: LIGHT # or FULL
    analysis:
      cooldown-hours: 12
      min-event-threshold: 5
    cleanup:
      cron: "0 0 * * * *" # Hourly
```

---

## 🔄 5. Core Services

### 5.1 Ingestion Service
- **Responsibility:** Non-blocking capture of events.
- **Target Latency:** < 5ms.
- **Implementation:** Direct repository save to the `temp` table.

### 5.2 Analysis Worker (The "Weaver")
- **Lazy Analysis Logic:**
  ```java
  if (Insight.AnalyzedAt + Cooldown < NOW && NewEvents.Count >= Threshold) {
      triggerLLMAnalysis(userId);
  }
  ```
- **Analysis Execution:** Calls the LLM to process the raw event window into a `BehaviorInsights` object.

### 5.3 Framework Integration (`@AIProcess`)
The service method responsible for saving insights is annotated to trigger the framework's automatic indexing.

```java
@AIProcess(type = "behavior-insight", processType = "create")
public BehaviorInsights save(BehaviorInsights insights) {
    return repository.save(insights);
}
```
*In **FULL Mode**, this annotation triggers vector generation and `AISearchableEntity` creation.*

---

## 🔍 6. Query Orchestration

The module integrates with the `RelationshipQueryPlanner` to provide a unified interface.

### 6.1 Personalization Query (Mode 1)
- Fetch via `behaviorInsightsRepository.findByUserId(userId)`.
- Injected into LLM system prompts as: *"User Context: {{behavior_insights}}"*.

### 6.2 Statistics & Discovery (Mode 2)
- Queries are sent to the `RelationshipQueryService`.
- **Example Natural Language:** "How many users in the 'Loyal' segment are at risk of churning?"
- **Execution:** The planner generates a plan that joins `UserEntity` with the behavior metadata stored in `AISearchableEntity`.

---

## 📅 7. Implementation Roadmap

| Phase | Milestone | Key Deliverable |
| :--- | :--- | :--- |
| **Phase 1** | Foundation | DB Schemas + `BehaviorInsights` entity with `@AICapable`. |
| **Phase 2** | Addon Logic | `BehaviorAIAutoConfiguration` with LIGHT/FULL toggles. |
| **Phase 3** | Ingestion | `BehaviorEventIngestionService` + Cleanup Job. |
| **Phase 4** | Worker | `BehaviorAnalysisWorker` with Lazy Analysis + `@AIProcess`. |
| **Phase 5** | Integration | Linking `User` to `BehaviorInsights` in `RelationshipSchemaProvider`. |
| **Phase 6** | QA | Validation of Mode toggling and Hybrid query execution. |

---

## ⚠️ 8. Critical Design Notes
- **User-First Override:** The `AIEntityConfigurationLoader` must check `hasEntityConfig("behavior-insight")` before applying addon presets.
- **Privacy:** In `LIGHT` mode, no data ever leaves the relational database for indexing purposes.
- **Scalability:** The background worker handles heavy LLM calls asynchronously, ensuring ingestion remains fast under load.

