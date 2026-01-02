# RAG Orchestrator User Guide

## What It Does
- Orchestrates retrieval + generation with security, access control, PII, and compliance gates.
- Supports authenticated and anonymous users via `OrchestrationContext`.
- Enriches prompts with behavior insights when a provider is present (SPI, optional).

## Core Entry Point
```java
OrchestrationResult orchestrate(String query, OrchestrationContext context)
```

### OrchestrationContext Checklist
- `userId` for authenticated users, `sessionId` for anonymous (one required).
- Optional: `ipAddress`, `userAgent`, `locale`, `metadata` (tier, device, referrer, etc.).
- `requestId` auto-generates if absent; call `context.validate()` to enforce identifiers.
- Factory helpers: `forUser(userId)`, `forSession(sessionId)`, `anonymous()`, `forTest()`.

### Behavior Integration (Optional)
- Core defines SPI `BehaviorContextProvider`; behavior module or custom apps can implement it.
- If present, behavior context is added to `SystemContext` and prompt (tone/recommendations).
- `userId` is opaque; non-UUID IDs are supported.

## Execution Flow (simplified)
1) Security: `AISecurityService.analyzeRequest` (uses userId + sessionId + metadata).
2) Access Control: `AIAccessControlService.checkAccess` (uses both identifiers).
3) PII: optional detect/redact on input/output.
4) Compliance: `AIComplianceService.checkCompliance`.
5) Intent extraction: `IntentQueryExtractor.extract(query, context)`.
6) Intent handling:
   - `ACTION`: requires authenticated user; routed to action handler registry.
   - `INFORMATION`: runs RAG (search) and optional generation when flagged.
   - `COMPOUND`: processes multiple intents.
   - `OUT_OF_SCOPE`: returns guidance.
7) Smart suggestions: next-step recommendations trigger secondary RAG with context metadata.
8) History: `IntentHistoryService.recordIntent` (skips if userId missing).

## Key Behaviors
- Anonymous actions are blocked; information queries allowed with session tracking.
- Security/AC metadata include authentication flag, sessionId, IP, UA, and passthrough metadata.
- Deprecated signatures:
  - `orchestrate(String, String)` delegates to context and logs warning.
  - `IntentQueryExtractor.extract(String, String)` delegates to context.
  - `SystemContextBuilder.buildContext(String)` and `EnrichedPromptBuilder.buildSystemPrompt(String)` are deprecated; prefer context variants.

## Example Usage
```java
// Authenticated
OrchestrationContext ctx = OrchestrationContext.builder()
    .userId("user-123")
    .sessionId("sess-abc")           // optional
    .ipAddress("203.0.113.10")
    .userAgent("Mozilla/5.0")
    .locale(Locale.US)
    .metadata(Map.of("tier", "gold"))
    .build();
OrchestrationResult result = orchestrator.orchestrate("Cancel my plan", ctx);

// Anonymous (information-only)
OrchestrationContext anon = OrchestrationContext.forSession("sess-xyz");
OrchestrationResult info = orchestrator.orchestrate("Show refund policy", anon);
```

## When to Extend
- Add new action handlers via `ActionHandlerRegistry`.
- Implement `BehaviorContextProvider` in your module to enrich prompts.
- Adjust smart-suggestion thresholds in `SmartSuggestionsProperties`.

## Testing Tips
- Use `OrchestrationContext.forTest()` for isolated unit tests.
- For behavior-aware tests, mock `BehaviorContextProvider` and provide context.
- Integration tests should call context-based signatures to avoid overload ambiguity.
