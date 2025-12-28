# Behavior Module User Guide

Audience: Developers integrating the Behavior Module to generate and use behavior insights (sentiment, churn, trend) in their apps and AI orchestrations.

## What the module does
- Pulls user events (via your `ExternalEventProvider` SPI) and produces `BehaviorInsights`.
- Supports LIGHT/FULL modes; FULL indexes insights (`@AIProcess`) for discovery/search.
- Provides APIs to run analysis (processing) and to query aggregates/details (analytics).

## Quickstart
1) **Enable module**  
   - Set `ai.behavior.enabled=true` in your app.  
   - Pick a preset: `ai.behavior.mode=LIGHT` or `FULL` (YAML presets under `behavior-presets/`).

2) **Provide events**  
   - Implement `ExternalEventProvider` to return events for `getEventsForUser` and `getNextUserEvents`.
   - Optional: implement `BehaviorInsightStore` if you want custom persistence; otherwise JPA is used.

3) **Run analysis**  
   - Targeted: `POST /api/behavior/processing/users/{userId}`  
   - Batch/discovery: `POST /api/behavior/processing/batch`  
   - Continuous: `POST /api/behavior/processing/continuous` (start) and `/cancel`  
   - Scheduled worker (opt-in): set `ai.behavior.processing.scheduled-enabled=true`

4) **Consume insights**  
   - Repository: `BehaviorInsightsRepository.findByUserId(userId)`  
   - Analytics APIs:  
     - `GET /api/behavior/analytics/rapid-decline` (alerts)  
     - `GET /api/behavior/analytics/trend-distribution`  
     - `GET /api/behavior/analytics/sentiment-distribution`  
     - `GET /api/behavior/analytics/users/{userId}/trend`
   - Orchestrator prompt: inject segment, sentiment, churn, trend, recommendations (see orchestration guide).

## Key configuration
`ai.behavior.*`:
- `enabled`: true|false
- `mode`: LIGHT|FULL (drives preset load)
- `processing.api-enabled`: true|false (enable processing API)
- `processing.scheduled-enabled`: true|false (enable worker)
- `processing.schedule-cron`: cron for worker (default `0 */15 * * * *`)
- `processing.api-max-batch-size`, `scheduled-batch-size`, `processing-delay`, `scheduled-max-duration`

`ai.behavior.processing.*` binds to `BehaviorProcessingProperties`.

## Entities and enums
- `BehaviorInsights`: sentimentScore/Label, churnRisk/Reason, trend, deltas, patterns, recommendations, insights map, confidence, timestamps.
- `SentimentLabel`: DELIGHTED, SATISFIED, NEUTRAL, CONFUSED, FRUSTRATED, CHURNING.
- `BehaviorTrend`: NEW_USER, RAPIDLY_IMPROVING, IMPROVING, STABLE, DECLINING, RAPIDLY_DECLINING.

## APIs (processing)
- `POST /api/behavior/processing/users/{userId}` → runs analysis for one user.
- `POST /api/behavior/processing/batch` → processes next users (honors maxUsers/duration/delay).
- `POST /api/behavior/processing/continuous` → starts background job; `/cancel` to stop.
- `POST /api/behavior/processing/scheduled/pause|resume` → toggle scheduled worker.

## APIs (analytics)
- `GET /api/behavior/analytics/rapid-decline` → critical trend alerts.
- `GET /api/behavior/analytics/trend-distribution`
- `GET /api/behavior/analytics/sentiment-distribution`
- `GET /api/behavior/analytics/users/{userId}/trend` → deltas + current state.

## Scheduling & background
- `BehaviorAnalysisWorker` runs on cron when `processing.scheduled-enabled=true`.
- `BehaviorProcessingManager` handles batch/continuous jobs; exposes pause/resume.
- `BehaviorProcessingState` stores pause flag.

## Observability
- Logs trend changes (warn on negative trends).
- Micrometer counters: `ai.behavior.processing.processed`, `ai.behavior.processing.errors` (if MeterRegistry present).

## Error handling & defaults
- Invalid/missing LLM fields are clamped and defaulted (sentiment → NEUTRAL, churn → 0.0, trend recomputed from deltas).
- If events are missing, existing insight is returned; new users default to STABLE/unknown.
- LLM failures fall back to a safe, low-confidence STABLE insight.

## Development tips
- Keep `ExternalEventProvider` deterministic in tests.
- For floating deltas, compare with tolerance (not exact equality).
- Use LIGHT preset for minimal footprint; FULL to index/search via Relationship Query.
- If you need real-API integration tests, run via the manual workflow/runner (realapi suites are excluded from default verify).
