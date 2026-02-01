# Curated Modes + Profiles Enablement Guide (Server-Authoritative)

## Goal
Enable predictable, production-friendly orchestration behavior using a **single configuration surface**:
- `profile` (baseline defaults)
- `modes.*` (server allowlist + per-mode overrides)
- `position-routing.*` (server-authoritative routing: UI position → mode)

This intentionally avoids “global override” flags that can silently bypass mode/profile behavior.

---

## Core concepts (what to configure)

### 1) `profile` (baseline)
`profile` is a small set of coherent defaults (no per-request guesswork):

```yaml
ai:
  orchestration:
    profile: DEFAULT | PRODUCTION_CHAT | PRODUCTION_NAVIGATOR
```

### 2) `modes` (server allowlist + overrides)
Clients may send `mode`, but the server only accepts modes defined under:

```yaml
ai:
  orchestration:
    modes:
      navigator:
        information-mode: DETERMINISTIC_RAG_GENERATE
      copilot:
        information-mode: LLM_DRIVEN
```

If a mode is not defined here, it is treated as unsupported (ignored or rejected depending on strictness).

### 3) `position-routing` (preferred)
Instead of letting the UI pick an arbitrary mode, the UI sends a low-spoof-risk `position`
and the server routes it to a mode:

```yaml
ai:
  orchestration:
    position-routing:
      landing: navigator
      checkout: copilot
```

### 4) Strictness
```yaml
ai:
  orchestration:
    strict-position-routing: false
    strict-mode-routing: false
```

- `strict-position-routing=true`: unknown `position` terminates the request.
- `strict-mode-routing=true`: unknown requested `mode` terminates the request.

Recommended in production:
- prefer strict *position* routing when your UI always sends a known position.
- keep strict *mode* routing off unless you fully control clients.

---

## Quickstart (opt-in curated defaults)

The framework ships an **opt-in** curated defaults config file:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/curated/ai-curated-default.yml`

To enable it in your app:

```yaml
spring:
  config:
    import: "optional:classpath:curated/ai-curated-default.yml"
```

Then override only what your app needs in `application.yml`.

---

## Request fields (what the UI sends)
For chat-style endpoints, the request can include:
- `position` (recommended)
- `mode` (optional; only effective if allowlisted)

The server will expose the effective policy in the response metadata (debug):
`result.metadata.orchestrationPolicy`

Key fields:
- `profile`
- `mode`
- `position`
- `informationModeEffective`
- `modeSource` (`POSITION` vs `REQUEST_MODE`)

---

## Example: Chat Capabilities Demo (navigator-only)
The demo app is temporarily configured to accept **only** `navigator` mode and route all
known positions to it:
- `Real_Apps/chat-capabilities-demo/src/main/resources/application.yml`

This is intentionally strict on behavior (predictable demo) while remaining fail-open on unknown mode/position
signals so the external UI doesn’t break.

---

## Why we do not support a global `information-mode` override
Global “override” flags are powerful but create a second source of truth that can silently
invalidate profile/mode testing. For a greenfield system focused on reproducibility and UX:

- `profile` sets defaults.
- `mode` refines behavior (allowlisted).
- `position` routes to a mode (server-authoritative).

If you need emergency operational controls later, add them as *explicit* “ops-only” layers
with clear precedence and strong observability.

