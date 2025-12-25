# AI Behavior Module v2 - Comprehensive Implementation Specification

**Version:** 2.3  
**Status:** Architecture Finalized  
**Strategic Alignment:** AI Fabric Framework Native Addon

---

## 📋 1. Executive Summary

The AI Behavior Module v2 is a **stateless behavioral processor** that transforms application-side user interactions into high-level AI insights. This module focuses exclusively on **App Interactions** (clicks, transactions, usage patterns) and purposefully ignores AI internal system events and conversation history. It provides the framework with deep "User Intelligence" for personalization and global statistics.

---

## 🏗️ 2. Architectural Principles

### 2.1 Mode-Based Toggling
- **LIGHT Mode (Default):** Personalization focus. Insights stored in relational DB for single-user context injection.
- **FULL Mode:** Discovery focus. Insights automatically indexed into `AISearchableEntity` via the Framework's `@AIProcess` for cross-user search and statistics.

### 2.2 Input: Pull-Based App Interactions
The module **does not receive or store raw events**. It pulls them via an SPI whenever an analysis is triggered.
- **Scope:** Strictly limited to user interactions on the application (e.g., e-commerce actions, feature usage).
- **Excluded:** AI system logs (PII, errors) and AI conversation history are handled by separate modules.

### 2.3 Output: Persistent Behavior Insights
The module's primary output is a `BehaviorInsights` entity containing segments, patterns, and recommendations.

---

## 🔌 3. Provider Interfaces (SPI)

The module relies on user-defined beans to bridge the gap between the App and the AI:

### 3.1 `ExternalEventProvider` (The Input)
Users implement this to provide the raw behavioral "raw material" for a user.
- `getEventsForUser(UUID userId)`: Targeted fetch for a specific user.
- `getNextUserEvents()`: Discovery fetch for the next user needing analysis.

### 3.2 `BehaviorInsightStore` (The Output/Sink)
Users can provide a custom implementation to store the **resulting intelligence**.
- `storeInsight(BehaviorInsights insight)`: Called whenever an analysis is complete. Defaults to a JPA implementation if not provided.

---

## 🗄️ 4. Data Layer Design

### 4.1 Persistent AI Insights (`ai_behavior_insights`)
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

## 🔄 5. Core Services & Logic

### 5.1 Analysis Service (The "Weaver")
- **Stateful Evolution:** Before analysis, the service fetches the **most recent** `BehaviorInsights` for the user.
- **Pull Logic:** Fetches new app interaction data from the `ExternalEventProvider`.
- **Evolutionary Analysis:** Uses the framework's `AICoreService` to process the **New Events** in the context of the **Previous Insight**. This allows the AI to detect shifts (e.g., "User is moving from 'Steady' to 'Power User'") rather than just static snapshots.
- **Persistence:** Saves the updated insight via the `BehaviorInsightStore`.
- **Annotation:** Uses `@AIProcess` to trigger the framework's automatic indexing (if in FULL mode).

---

## 🔍 6. Query Orchestration

1.  **Personalization:** Fetches a specific user's insight to provide context for AI responses.
2.  **Discovery (Relationship Query):** Enables the framework to answer questions like: *"Show me the distribution of churn risk across all power users"* by querying the `AISearchableEntity` metadata.

---

## 📅 7. Implementation Roadmap

| Phase | Milestone | Key Deliverable |
| :--- | :--- | :--- |
| **Phase 1** | Foundation | Relational schema for `ai_behavior_insights`. |
| **Phase 2** | Addon Config | `BehaviorAIAutoConfiguration` (LIGHT/FULL toggles). |
| **Phase 3** | Input SPI | `ExternalEventProvider` implementation. |
| **Phase 4** | Output SPI | `BehaviorInsightStore` implementation. |
| **Phase 5** | Processor | `BehaviorAnalysisService` with `@AIProcess` integration. |
| **Phase 6** | Integration | `RelationshipQuery` support for behavior metadata. |

---

## ⚠️ 8. Critical Design Notes
- **Zero Raw Persistence:** The framework never stores raw app events.
- **Isolation:** Behavioral logic is decoupled from AI system monitoring and conversation logs.
- **Privacy:** Analysis happens on a per-user "window" of data that is discarded immediately after the insight is generated.
