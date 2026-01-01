# Building a Trustworthy Behaviour Orchestrator: Philosophy, Patterns, and Practicalities

*By the AI Infrastructure Team*

## Why an Orchestrator?
Generative AI systems thrive on context, but context is messy: identity, session, policy, security, compliance, PII, and intent all converge at the same moment. The Behaviour Orchestrator exists to make that convergence deliberate, observable, and safe—so teams can ship faster without trading away trust.

## First Principles
1) **Context is the contract**  
   We accept a single rich `OrchestrationContext` (userId or sessionId required). This keeps the API honest about who (or what session) is acting, and gives us a single place to attach metadata (locale, device, tier, IP, UA).

2) **Safety before cleverness**  
   Security, access control, compliance, and PII detection run *before* intent handling. If a request is blocked, we stop immediately and return a clear reason.

3) **Least privilege by default**  
   Anonymous users get information; actions are denied unless explicitly allowed by policy and handler capability. Auth paths remain first-class but still gated by policy.

4) **No secrets to the LLM**  
   We never send session identifiers to providers. Only `userId` (when authenticated) is forwarded. Session IDs stay internal for rate limiting, auditing, and correlation.

5) **Progressive enhancement**  
   Start simple: user/session, locale, and minimal metadata. Grow into behaviour insights, personalization, or A/B knobs without breaking the contract.

6) **Auditability as a feature**  
   Every decision point (security, access, compliance, PII) is traceable by requestId. Sanitization is explicit and observable.

## The Shape of the Flow
1) **Context ingress** — validate `OrchestrationContext` (userId or sessionId). Generate `requestId`.  
2) **Safety gates** — Security → Access Control → Compliance → PII detection/redaction.  
3) **Intent extraction** — Pass sanitized query + system context to LLM; never include sessionId.  
4) **Intent handling** — Actions (authenticated only by default), Information (RAG + generation toggle), Out-of-scope, Compound.  
5) **Response hygiene** — Sanitization, PII annotations, smart suggestions, metadata packaging.  
6) **Audit & history** — Persist with requestId, userId/sessionId, redacted query, intents, and outcome.

## Design Decisions That Matter
- **Single entry point**: `orchestrate(query, OrchestrationContext)` keeps platform consistency and makes policy enforcement testable.  
- **Deprecation path**: Old `orchestrate(query, userId)` delegates to the new signature to ease migration.  
- **Anonymous posture**: Block actions; allow search/info; retain session for rate limits and correlation.  
- **Metadata hygiene**: IP/UA used internally for policy; sessionId never leaves the boundary; userId to LLM only when authenticated.  
- **Composable builders**: `SystemContextBuilder` and `EnrichedPromptBuilder` transform orchestration context into prompt-safe context without leaking sensitive tokens.  

## Handling Anonymous vs Authenticated
- **Authenticated**: richer personalization, full action surface (still policy-checked), clearer auditing.  
- **Anonymous**: minimal surface (info/search), mandatory sessionId, strict rate limits, zero action by default.  
- **Future opt-in**: If teams want anonymous actions, they must ship explicit policy, handler opt-in, and rate limits (see `ANONYMOUS_ACTIONS_POLICY_EXAMPLE.md`).

## Extensibility Horizons
- **Behaviour insights**: attach optional behavioural signals to `SystemContext` when available—never required for correctness.  
- **Policy plugins**: allow custom access rules per tenant/product surface without changing the orchestrator core.  
- **Adaptive safety**: dynamic throttling based on risk scores (session + IP + content signals).  

## How to Adopt
1) Migrate to `OrchestrationContext` factories: `forUser(...)`, `forSession(...)`, `anonymous()`.  
2) Update callers of intent extraction to pass context, not bare userId.  
3) Verify logs/telemetry never carry raw session IDs beyond the service boundary.  
4) Add tests for: authenticated action, anonymous info, anonymous action (denied), PII redaction, compliance/access blocks.  

## Testing Ethos
- Unit: context validation, factory helpers, action denial for anonymous.  
- Integration: full pipeline with security/access/compliance mocks; verify metadata and sanitization.  
- Regression: deprecated signature still works and routes through the new path.  

## The Payoff
By treating context as a first-class citizen and safety as a default, the Behaviour Orchestrator lets product teams move quickly while staying inside well-lit guardrails. It is the “spine” that keeps identity, policy, and AI behaviour aligned—so you can evolve capabilities without eroding trust.
