# Behaviour Orchestrator – Practical User Guide

## What It Does
The Behaviour Orchestrator takes a user query plus rich context, runs safety and policy checks, extracts intent, and executes the right action or information flow. It is the single entry point for conversational experiences across authenticated and anonymous users.

## The API You Should Use
```java
OrchestrationResult orchestrate(String query, OrchestrationContext context);
```
**Deprecated:** `orchestrate(String query, String userId)` (wraps `forUser`, remove in future).

## Building Context
- Authenticated: `OrchestrationContext.forUser(userId)`
- Anonymous: `OrchestrationContext.forSession(sessionId)` or `OrchestrationContext.anonymous()`
- Rich context:
  ```java
  OrchestrationContext ctx = OrchestrationContext.builder()
      .userId(userId)                // optional for anonymous
      .sessionId(sessionId)          // required if no userId
      .ipAddress(clientIp)
      .userAgent(userAgent)
      .locale(requestLocale)
      .metadata(Map.of("tier", "pro", "device", "mobile"))
      .build();
  ```
- Validation: `context.validate()` enforces userId or sessionId.

## Core Use Cases
1) **Authenticated SaaS user (actions + info)**  
   ```java
   OrchestrationResult r = orchestrator.orchestrate("cancel my subscription",
       OrchestrationContext.forUser(currentUser.getId()));
   ```
2) **Anonymous browsing (info/search only)**  
   ```java
   OrchestrationResult r = orchestrator.orchestrate("return policy for electronics",
       OrchestrationContext.forSession(httpSession.getId()));
   ```
3) **Personalized, policy-aware flow**  
   Pass IP/UA/locale/metadata to strengthen security, access, and prompt context; sessionId stays internal and is never sent to the LLM.
4) **System/test harness**  
   `OrchestrationContext.forTest()` gives synthetic user + session IDs for integration tests.

## Behaviour & Safety
- Actions require authentication; anonymous actions are denied by default.
- Security → Access Control → Compliance → PII detection run before intent handling.
- Session IDs are never sent to LLM/providers; only userId is forwarded when authenticated.
- Sanitization runs on responses; PII annotations are attached when detected.

## Migration Checklist (from userId-only)
1) Swap `orchestrate(query, userId)` → `orchestrate(query, OrchestrationContext.forUser(userId))`.
2) Update intent extraction callers to pass `OrchestrationContext`.
3) For anonymous flows, supply a sessionId via `forSession(...)`.
4) Keep handlers expecting userId unchanged; anonymous actions remain blocked unless you explicitly implement a safe path.

## Testing Scenarios
- Authenticated action: expect `ACTION_EXECUTED` when handler allows.
- Anonymous action: expect `ACTION_DENIED`.
- Anonymous info: expect `INFORMATION_PROVIDED`.
- Compliance/security block: expect error result with message.
- Telemetry: verify sessionId absent from LLM/provider payloads.
