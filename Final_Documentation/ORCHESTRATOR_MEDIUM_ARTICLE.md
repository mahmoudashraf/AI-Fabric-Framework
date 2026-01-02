# Designing a Trustworthy Behaviour Orchestrator: Principles Over Hacks

*By the AI Infrastructure Team*

## The Problem We Chose to Solve
LLM features fail in production not because models hallucinate, but because context, identity, safety, and policy are afterthoughts. We built the Behaviour Orchestrator to make those concerns first-class: every request carries who/what is asking, why it’s allowed, and how it’s kept safe.

## Core Principles
1) **Context as contract** — A single `OrchestrationContext` (userId or sessionId required) is the API. No hidden globals, no side channels.  
2) **Safety first, always** — Security, access control, compliance, and PII detection/redaction run before intent handling. If blocked, we stop.  
3) **Least privilege by default** — Anonymous gets search/info; actions are denied unless explicitly allowed. Authenticated users are still policy-gated.  
4) **Do not leak** — Session IDs never leave the service boundary. Only authenticated userId is sent to providers.  
5) **Progressive enhancement** — Start with identity + locale + metadata; later add behaviour insights or personalization without breaking callers.  
6) **Auditability is a feature** — Every decision is traceable via requestId; sanitization is explicit and testable.

## The Flow (At a Glance)
1) Ingest `OrchestrationContext`; validate userId or sessionId; generate requestId.  
2) Safety gates: Security → Access Control → Compliance → PII detection/redaction.  
3) Intent extraction with sanitized query + safe system context (no sessionId to LLM).  
4) Intent handling: actions (auth only by default), information (RAG), out-of-scope, compound.  
5) Response hygiene: sanitization, PII annotations, smart suggestions.  
6) Audit/history: persist with requestId, userId/sessionId, redacted query, intents, outcome.

## Why Single-Entry Matters
One orchestrate call means one place to enforce policy, log, test, and reason about regressions. Deprecating the old `orchestrate(query, userId)` in favor of `orchestrate(query, OrchestrationContext)` removes ambiguity and keeps teams honest about identity and session handling.

## Anonymous vs Authenticated: Our Posture
- **Authenticated**: richer personalization, action surface unlocked, full policy/audit.  
- **Anonymous**: info/search only, session required, rate-limited, zero action by default.  
- **Opt-in future**: Anonymous actions require explicit policy, handler opt-in, and rate limits (see `ANONYMOUS_ACTIONS_POLICY_EXAMPLE.md`).

## Extensibility Horizons
- Behaviour insights (opt-in) to enrich prompts without changing the contract.  
- Pluggable policies per tenant/surface.  
- Adaptive safety: dynamic throttling by risk signals (session + IP + content).

## How to Adopt
1) Use `OrchestrationContext` factories (`forUser`, `forSession`, `anonymous`, `forTest`).  
2) Pass context into orchestrate; remove legacy userId-only calls.  
3) Verify provider payloads contain no sessionId; keep userId only when authenticated.  
4) Add tests for: auth action success, anonymous action denial, info flows, safety/compliance blocks, sanitization paths.

## The Payoff
A trustworthy orchestrator is the spine of an AI product: it aligns identity, policy, safety, and context so teams can ship faster without eroding trust. By putting context and safety first, we gain velocity without inviting surprises later.
