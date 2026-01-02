# The Orchestrator: Context First, Behavior Ready

Modern AI systems fail when they assume every request comes from a logged-in, well-behaved user. Our orchestrator is built for the real world: anonymous shoppers, authenticated SaaS users, and everything in between—while keeping security, compliance, and personalization in the loop.

## Why a Context Object?
We moved from `orchestrate(query, userId)` to `orchestrate(query, OrchestrationContext)` for one reason: extensibility without API churn. The context carries userId or sessionId, requestId, IP, UA, locale, and arbitrary metadata. That lets us:
- Serve anonymous users safely (sessionId required) and authenticated users richly.
- Pass the full picture to security and access control—no more overloading userId with session data.
- Add fields later (e.g., device, referrer, locale) without breaking clients.

## Behavior Without Coupling
Behavior insights should be optional, not a hard dependency. We use an SPI: core defines `BehaviorContextProvider`; behavior module (or any app) implements it. If present, prompts get enriched with sentiment, churn, and segment info; if not, the orchestrator still runs. No circular dependencies, no forced analytics stack.

## Guardrails by Default
1) Security first: requests are analyzed with the full context (userId + sessionId + IP/UA/metadata).
2) Access control: policies see both identifiers; anonymous users aren’t mis-labeled as “userId.”
3) PII and compliance: configurable detection/redaction and compliance checks before intent handling.
4) Anonymous actions blocked: info is allowed for anonymous sessions; actions require auth.

## Smart Suggestions, Not Guesswork
Next-step recommendations from the LLM trigger targeted RAG lookups with the same context (user/session, vectorSpace, confidence). Metadata is null-safe, so tests and prod behave consistently. Suggestions remain secondary—no suggestion, no extra RAG.

## Testing Philosophy
- Prefer context-based signatures in tests to avoid overload ambiguity.
- Mock behavior providers when you want enrichment; omit them when you don’t.
- Use factory helpers (`forUser`, `forSession`, `anonymous`, `forTest`) to keep setup concise.

## Takeaways
- Context-first API keeps us future-proof and safe.
- Behavior is plug-in, not mandatory.
- Anonymous support is deliberate, with action guardrails.
- Security/AC/PII/compliance run with the full picture, not just a userId string.

That’s the orchestrator philosophy: practical, extensible, and ready for real-world traffic.***
