# Mode-Driven Orchestration Policy (Profile + Request Mode + Position Routing) — Change Plan

## Status
Proposed

## Executive summary
Different UI “positions” (pages/modules) have genuinely different optimal orchestration behavior:
- Landing/search pages want **navigator** behavior (retrieval-first, broader RAG, stable grounding).
- Cart/checkout pages want **action-first** behavior (cart/order actions, strict target resolution).
- Support pages want **resolution** behavior (read/write actions, higher safety gates, richer history).

Today, we approximate these behaviors using global flags and prompts. That leads to:
- drift across turns (wide retrieval when we should be scoped),
- wrong tool selection (actions chosen from unrelated context),
- poor UX (“compare both” uses unrelated items, “buy it” picks the wrong SKU),
- accidental cost spikes (deterministic fan-out when vectorSpace is missing).

This plan introduces a **server-authoritative OrchestrationPolicy** that can be selected per request via a **mode**, optionally routed from a UI “position” signal.

The system remains contract-based, deterministic, and greenfield:
- Clients can *request* a mode/position, but the **server decides** the effective policy via an allowlist.
- Policies are defined in YAML, easy for framework users to customize.
- A mode is a coherent bundle, not a pile of micro-flags.

---

## Why we need this (problems it solves)

### 1) Eliminates “context guessing” on pages with strong intent
When the user is on the **cart page**, their intent is rarely “broad search”.
It is usually:
- add/remove/update cart items
- apply discount / calculate totals
- create or cancel an order

A cart-specific mode can:
- bias intent extraction toward cart actions,
- constrain retrieval to the cart/order vector spaces,
- require strict target resolution from UI attachments (selected items).

Result: “add to cart” never picks an unrelated SKU from older retrieval.

### 2) Prevents retrieval drift (phone vs sneakers)
On landing/search pages, broader RAG is fine. On cart/support pages, broad RAG is harmful.
Modes allow per-position:
- different vectorSpace constraints,
- different fan-out limits,
- different working-set reuse rules.

### 3) Makes deterministic RAG safe and cost-bounded
`DETERMINISTIC_RAG_GENERATE` is great for catalog demos, but can be expensive if it fan-outs across many spaces.
In a “navigator” mode we can enable deterministic answering while still:
- constraining vector spaces when attachments exist,
- bounding fan-out limits,
- enabling working-set reuse for follow-ups.

### 4) Improves developer experience
Instead of telling app developers to learn and coordinate 10 flags, they define:
- a few modes in YAML
- optional page→mode routing rules

This is simpler, safer, and more maintainable for an open-source framework.

---

## Core concept: server-authoritative OrchestrationPolicy

### The policy object
Define a single internal object used by pipeline steps:
`OrchestrationPolicy`

It contains all knobs that materially change orchestration behavior, e.g.:
- `informationMode`: `LLM_DRIVEN | DETERMINISTIC_RAG_GENERATE`
- `promptMode`: `FULL_CONTRACT | MINIMAL_FOR_RAG`
- `attachments.enabled`
- `attachments.constrainVectorSpaces`
- `workingSet.enabled`
- `history.windowSize`, `history.maxChars`
- `rag.thresholds`, `rag.fanOutMaxSpaces`, `rag.topKPerSpace`
- `actions.allowed` (allowlist by action name/category/accessMode)
- `actions.confirmationPolicy` (e.g., confirm write actions, never confirm read actions)

Important: pipeline code should not read scattered booleans; it reads the resolved policy.

### How policy is selected
Inputs:
1) Global `profile` (baseline defaults)
2) Optional request `mode` (server-allowlisted)
3) Optional request `position` (mapped to a mode server-side)

Resolution:
`effectivePolicy = merge(profileDefaults, modeOverrides)`

Client can request mode/position, but server may:
- accept,
- downgrade to a safe mode,
- or reject (fail-closed) depending on access control.

---

## API changes (request-level signals)

### Extend `ChatQueryRequest` (or OrchestrationContext)
Add optional fields:
- `mode`: string (e.g., `navigator`, `cart_assistant`, `support_agent`)
- `position`: string (e.g., `landing`, `cart`, `support`)
- `attachments[]` and `activeAttachmentIds[]` (from the attachments plan)

Rules:
- `mode` is optional and only effective if server allows it.
- `position` is optional and is mapped to mode server-side (preferred; lower spoof risk).

---

## Configuration (YAML-defined modes)

### 1) Define modes under a single root
Example (illustrative):

```yaml
ai:
  orchestration:
    profile: DEFAULT
    modes:
      navigator:
        informationMode: DETERMINISTIC_RAG_GENERATE
        promptMode: MINIMAL_FOR_RAG
        attachments:
          enabled: true
          constrainVectorSpaces: true
        workingSet:
          enabled: true
        rag:
          fanOutMaxSpaces: 5
          topKPerSpace: 5
          threshold: 0.25

      cart_assistant:
        informationMode: LLM_DRIVEN
        promptMode: FULL_CONTRACT
        attachments:
          enabled: true
          constrainVectorSpaces: true
        actions:
          allowCategories: ["commerce", "cart", "orders"]
          confirmWriteActions: true
          confirmReadActions: false

      chatty:
        informationMode: LLM_DRIVEN
        promptMode: FULL_CONTRACT
        attachments:
          enabled: true
          constrainVectorSpaces: true
        workingSet:
          enabled: true
        history:
          windowSize: 12
          maxChars: 8000
```

Notes:
- Keep mode schema **bounded** and domain-agnostic.
- Mode definitions are explicit; no implicit guessing.

### 2) Position routing (server-side)
Add an allowlisted map:

```yaml
ai:
  orchestration:
    positionRouting:
      landing: navigator
      cart: cart_assistant
      support: support_agent
```

This makes it easy for UI to send `position=cart` while server selects the correct mode.

---

## Security model (fail-closed)

### Why the client cannot fully control the mode
If the client can choose modes freely, a public landing page could request a support mode and access privileged actions.

Therefore:
- The server must **allowlist** modes per environment/app.
- Access control can optionally restrict which user roles can use which modes.
- Unknown modes → ignore or reject (configurable), defaulting to `profile` behavior.

Optional extensions:
- `modeAccessPolicy`: allow modes by authentication state, roles, tenant, etc.

---

## How this integrates with existing features

### Deterministic RAG (`DETERMINISTIC_RAG_GENERATE`)
Best placed inside a `navigator` or `demo_catalog` mode. The mode can also set:
- fan-out max spaces
- thresholds
- promptMode = `MINIMAL_FOR_RAG`

Critical integration:
- If attachments exist, `constrainVectorSpaces=true` must take precedence, preventing expensive fan-out.

### Prompt mode switching
Already exists (`ai.intent-extraction.prompt-mode`). Under policy-based orchestration:
- prompt mode is determined by the effective policy.

### Attachments and working set
These are mode-tunable:
- navigator: enabled + constrained retrieval
- cart_assistant: enabled + strict target resolution
- support_agent: enabled + richer history + higher safety/confirmation defaults

---

## Implementation steps (comprehensive)

### Phase 1 — Data model and configuration
1) Add `OrchestrationPolicy` (immutable record or class).
2) Add YAML config model:
   - `ai.orchestration.profile`
   - `ai.orchestration.modes.*`
   - `ai.orchestration.positionRouting.*`
3) Add validation:
   - unknown `informationMode` or `promptMode` values fail startup
   - mode names must be non-empty and match a safe regex

### Phase 2 — Request wiring
4) Extend the request DTO (`ChatQueryRequest`) to accept:
   - `position` and/or `mode`
   - (and attachments fields from the attachments plan)
5) Put these into `OrchestrationContext` (or `PipelineContext.metadata`) as raw “requested signals”.

### Phase 3 — Policy resolution step (single source of truth)
6) Add a new pipeline step early (before intent extraction):
   `OrchestrationPolicyResolutionStep`
   - resolves `effectiveMode`:
     - if `position` provided and mapped → mode
     - else if `mode` provided and allowlisted → mode
     - else → profile default
   - produces `context.policy` (effective) and debug metadata:
     - `policy.profile`, `policy.mode`, `policy.effectiveInformationMode`, `policy.effectivePromptMode`
7) Fail-closed option:
   - if `position` is unknown and `strictPositionRouting=true` → return ERROR/CLARIFICATION
   - default: ignore unknown, use profile

### Phase 4 — Consume policy in core steps
8) Update key steps to read from policy (not raw properties):
   - `EnrichedPromptBuilder` (prompt mode)
   - `VectorSpaceResolutionStep` (deterministic fan-out behavior)
   - `IntentHandlingStep` (deterministic information mode; retrieval/generation)
   - confirmation/action eligibility filtering (actions allowlist)
   - history limits / memory strategy (if mode overrides)

### Phase 5 — Action eligibility and safety
9) Add action allowlist filtering to `SystemContextBuilder` (AVAILABLE ACTIONS list):
   - only include actions permitted by policy for this mode
10) Confirmation defaults per mode:
   - read actions may skip confirmation
   - write actions require confirmation (policy-controlled)

### Phase 6 — Documentation + examples
11) Add a new developer guide:
   - “Mode-driven orchestration policies”
   - examples for landing/cart/support
   - security guidance (server-authoritative; avoid client spoofing)

---

## Testing strategy

### Unit tests
- policy resolution precedence:
  - position routing beats mode
  - mode beats profile
  - unknown mode ignored / strict behavior when enabled
- policy consumption:
  - prompt mode chosen by policy
  - deterministic info mode toggled by policy

### Integration tests
- cart mode:
  - only cart actions appear in AVAILABLE ACTIONS
  - “add to cart” uses attachments as target
- navigator mode:
  - deterministic RAG+generate for INFORMATION
  - vector spaces constrained by attachments

---

## Benefits (what framework users get)
- **One mental model:** “Pick a mode” instead of coordinating many flags.
- **Position-aware assistants:** page context becomes an explicit orchestration constraint.
- **Safety:** privileged actions cannot be unlocked by spoofing client flags.
- **Performance/cost control:** deterministic behavior is bounded by mode-specific limits.
- **Extensibility:** apps can define their own modes in YAML without forking core logic.

