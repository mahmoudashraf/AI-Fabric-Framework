# Enabling Anonymous Actions (Opt-In) — Example & Policy Notes

> Default remains **blocked**. Enable only with explicit policy and handler support.

## When to Consider
- Public, low-risk actions (e.g., “save search filters locally”, “subscribe to newsletter with email only”).
- No PII/PCI/PHI in inputs; impact of abuse is minimal and rate-limited.

## Guardrails (must-have)
1) **Explicit feature flag**: e.g., `orchestrator.actions.allowAnonymous=false` by default.  
2) **Capability check**: derive `sessionCapabilities` from a trusted list (whitelist only).  
3) **Rate limiting**: keyed by `sessionId` + IP.  
4) **Scope filtering**: only allow actions tagged `allowsAnonymous=true`.  
5) **Input hygiene**: PII detection + strict schema validation per action.  
6) **No sessionId to LLM**: keep `userId` null for anonymous; never forward sessionId externally.  
7) **Audit tagging**: record `sessionId`, `ipAddress`, `userAgent`, `actionName`, `allowedAnonymous=true/false`.

## Example: Orchestrator Hook
```java
// inside RAGOrchestrator.handleAction(intent, context)
if (context.isAnonymous()) {
    if (!featureFlags.isAnonymousActionsEnabled()) {
        return OrchestrationResult.actionDenied("Authentication required for actions.");
    }

    if (!handler.supportsAnonymous()) {
        return OrchestrationResult.actionDenied("Action not available for anonymous sessions.");
    }

    if (!sessionPolicyService.isAllowed(context.getSessionId(), intent.getAction())) {
        return OrchestrationResult.actionDenied("Action not permitted for this session.");
    }
}

// proceed with handler.executeAction(params, identifierOrSession);
```

## Example: ActionHandler Contract
```java
public interface ActionHandler {
    default boolean supportsAnonymous() { return false; }
    boolean validateActionAllowed(String principal); // principal = userId or sessionId
    String getConfirmationMessage(Map<String, Object> params);
    ActionResult executeAction(Map<String, Object> params, String principal);
}
```

For anonymous-safe handlers:
```java
@Component
public class NewsletterSignupHandler implements ActionHandler {
    @Override
    public boolean supportsAnonymous() { return true; }

    @Override
    public boolean validateActionAllowed(String principal) {
        // principal may be sessionId; enforce rate limits & email format
        return rateLimiter.allow(principal) && emailGuard.isValid(principal);
    }
}
```

## Minimal Policy Flow
1) `context.isAnonymous()` → check flag `allowAnonymousActions`.  
2) `sessionPolicyService` → whitelist action & enforce rate limit.  
3) `handler.supportsAnonymous()` → must be true.  
4) Execute with `principal = context.getSessionId()`; never send to LLM.  
5) Audit entry: `{sessionId, action, ip, userAgent, allowedAnonymous: true}`.

## Testing Checklist
- ✅ Anonymous action denied when flag off.  
- ✅ Anonymous action allowed only when both flag + handler support + session policy pass.  
- ✅ PII detection runs on inputs.  
- ✅ SessionId never leaves service boundary (LLM/log redaction verified).  
- ✅ Rate limits enforced per session/IP.  
- ✅ Authenticated path unchanged.  
