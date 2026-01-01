# Behavior Module ↔ Orchestrator Integration Guide

**Goal:** Show how to surface `BehaviorInsights` into orchestrated LLM calls so user state (sentiment, churn, trend, recommendations) is available in context.

## What This Covers
- Where to plug behavior insights in an orchestrated flow.
- Data flow: fetch, sanitize, inject into prompt/context.
- API and service touchpoints (processing + analytics).
- Guardrails (freshness, fallbacks, errors).
- Example payload shape for orchestrators.

## High-Level Flow
1) **Ensure insights exist**  
   - Run behavior analysis (targeted or batch) via processing API:  
     - `POST /api/behavior/processing/users/{userId}`  
     - `POST /api/behavior/processing/batch`  
   - Or run scheduled/continuous processing out-of-band.

2) **Fetch insights for orchestration**  
   - Via JPA/Repository: `BehaviorInsightsRepository.findByUserId(userId)`  
   - Or via analytics API for trend view: `GET /api/behavior/analytics/users/{userId}/trend`

3) **Normalize for LLM context**  
   - Include: `segment, sentiment {score,label}, churn {risk,reason}, trend, patterns, recommendations, insights (map), analyzedAt, confidence, deltas`.
   - Strip/limit PII; cap list sizes to avoid token bloat.

4) **Inject into orchestrator prompt/context**  
   - Prepend a **Behavior Context** block before user turns or system prompt.
   - Example snippet:
     ```
     [BEHAVIOR CONTEXT]
     segment: Pro | sentiment: SATISFIED (0.70) | churn: 0.10 (reason: good ux)
     trend: IMPROVING | confidence: 0.80
     patterns: login, upgrade
     recommendations: nps, reward
     insights: {"device":"ios","tier":"gold"}
     analyzedAt: 2025-12-28T00:21:28Z
     ```

5) **Handle stale/missing data**  
   - If no insight or `analyzedAt` is too old, inject a fallback block:  
     `behavior: unavailable or stale; proceed with default onboarding tone.`

## Integration Points
- **Services:**  
  - `BehaviorAnalysisService` (programmatic use)  
  - `BehaviorProcessingController` (HTTP for analyze/batch/continuous)  
  - `BehaviorAnalyticsController` (HTTP for trend/alerts/distribution)
- **Repositories:**  
  - `BehaviorInsightsRepository.findByUserId(userId)`  
  - `findRapidlyDecliningUsers()` for triage queues
- **DTO shape for orchestration (suggested)**
  ```json
  {
    "userId": "...",
    "segment": "Pro",
    "sentiment": { "score": 0.7, "label": "SATISFIED" },
    "churn": { "risk": 0.1, "reason": "good ux" },
    "trend": "IMPROVING",
    "patterns": ["login","upgrade"],
    "recommendations": ["nps","reward"],
    "insights": {"device":"ios","tier":"gold"},
    "confidence": 0.8,
    "analyzedAt": "2025-12-28T00:21:28Z",
    "previousSentimentScore": 0.1,
    "previousChurnRisk": 0.2,
    "sentimentDelta": 0.6,
    "churnDelta": -0.1
  }
  ```

## Guardrails & Best Practices
- **Freshness:** define max age (e.g., 24h) before treating insights as stale.
- **Token budget:** truncate `patterns` / `recommendations`; summarize `insights` map.
- **PII:** redact/omit sensitive fields before prompt injection.
- **LLM safety:** if any field is null/invalid, fall back to defaults (`segment: unknown`, `trend: STABLE`, `sentiment: NEUTRAL`, `churn: 0.0`).
- **Error handling:** on fetch failure, proceed with a minimal context note instead of blocking the call.

## Example Orchestrator Hook (conceptual)
1) Fetch `BehaviorInsights` by `userId`.
2) If missing or stale, enqueue `analyzeUser(userId)` asynchronously; continue with fallback context.
3) Render a Behavior Context block and prepend to the LLM system prompt or conversation state.
4) Optionally log which insight version (`analyzedAt`, `modelPromptVersion`) was used for traceability.

## When to Re-run Analysis
- Before high-stakes actions (renewal, save, upsell).
- On key events: downgrade, cancel_attempt, multiple failures.
- On a schedule for high-value cohorts (e.g., nightly).

## Verification Checklist
- [ ] Behavior processing reachable (analyze/batch APIs or service call).  
- [ ] Insight fetch wired in orchestrator (repo or analytics API).  
- [ ] Prompt injection includes sentiment/churn/trend and recommendations.  
- [ ] Fallback path when insight missing/stale.  
- [ ] PII guarded; lists truncated to avoid token bloat.  
- [ ] Logs/metrics capture which insight version was injected.  
