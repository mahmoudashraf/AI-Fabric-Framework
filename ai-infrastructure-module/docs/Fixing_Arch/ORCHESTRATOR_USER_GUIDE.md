# Behaviour Orchestrator – User Guide (Context-Based API)

## TL;DR
- Use `orchestrate(String query, OrchestrationContext context)` (new).
- Provide `userId` for authenticated flows, `sessionId` for anonymous; one is required.
- Actions are blocked for anonymous by default; info/search works.
- Never send `sessionId` to the LLM; only `userId` when authenticated.
- Prefer factory helpers: `OrchestrationContext.forUser(...)`, `forSession(...)`, `anonymous()`.

## Core Use Cases
1) **Authenticated SaaS user**
   ```java
   OrchestrationContext ctx = OrchestrationContext.forUser(currentUser.getId());
   OrchestrationResult result = orchestrator.orchestrate("show my invoices", ctx);
   ```
   - Full access to actions and information.
   - Security/Access/Compliance checks run with `userId`.

2) **Anonymous browsing / public docs**
   ```java
   OrchestrationContext ctx = OrchestrationContext.forSession(httpSession.getId());
   OrchestrationResult result = orchestrator.orchestrate("return policy for electronics", ctx);
   ```
   - Information/search allowed.
   - Actions are denied by default for anonymous.

3) **Rich context for personalization**
   ```java
   OrchestrationContext ctx = OrchestrationContext.builder()
       .userId(currentUser.getId())
       .sessionId(request.getSession().getId())
       .ipAddress(request.getRemoteAddr())
       .userAgent(request.getHeader("User-Agent"))
       .locale(request.getLocale())
       .metadata(Map.of("subscriptionTier", "pro", "device", "mobile"))
       .build();
   OrchestrationResult result = orchestrator.orchestrate("optimize my workspace", ctx);
   ```
   - Security/access gets IP/UA for decisions.
   - Prompt builder gets locale/metadata (not sessionId).

4) **System test**
   ```java
   OrchestrationContext ctx = OrchestrationContext.forTest();
   OrchestrationResult result = orchestrator.orchestrate("ping", ctx);
   assertNotNull(result);
   ```

## API Surface
- **New**: `orchestrate(String query, OrchestrationContext context)`
- **Deprecated**: `orchestrate(String query, String userId)` → wraps `forUser(userId)`
- Helpers: `forUser(...)`, `forSession(...)`, `anonymous()`, `forTest()`
- Validation: `context.validate()` ensures userId or sessionId is present.

## Behaviour & Security Notes
- Actions require authentication (anonymous → `ACTION_DENIED`).
- Session IDs stay internal: not sent to LLM or external providers.
- PII detection runs on input; output sanitization remains in place.
- Access/Compliance/Security requests carry `userId`/`sessionId`, IP, UA, metadata.

## Migration Checklist (from userId-only)
1) Replace `orchestrate(query, userId)` → `orchestrate(query, OrchestrationContext.forUser(userId))`.
2) Update intent extraction calls to pass `OrchestrationContext`.
3) For anonymous flows, supply a `sessionId` via `forSession(...)`.
4) Ensure handlers that need anonymous support declare it explicitly (default deny).

## Testing Tips
- Authenticated action: expect `ACTION_EXECUTED` when handler allows.
- Anonymous action: expect `ACTION_DENIED` (default).
- Anonymous information: expect `INFORMATION_PROVIDED`.
- Verify sessionId never appears in provider payloads/log lines to LLMs.
- Integration tests: cover both `userId` and `sessionId` paths.
