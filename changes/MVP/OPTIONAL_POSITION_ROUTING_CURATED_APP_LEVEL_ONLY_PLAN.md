# Optional Position Routing in Curated Packs (App‑Level Only)

## Goal
Keep `position-routing` **available** in curated packs as an **optional convenience**, while making the **framework core fully position‑agnostic**:

- Core receives **`mode` only as a selector key** for policy resolution.
- Curated packs provide `modes.<mode>.*` capability flags and limits.
- `position` may still be sent by the UI for observability, but **core must not branch on it**.
- If an application wants “position → mode” behavior, it is implemented **outside core** (app/web layer), optionally reading curated `position-routing`.

This matches the greenfield rule: **no hidden coupling** between framework behavior and UI/domain concepts.

---

## Current State (Problem)
Today, curated packs can contain:

```yml
ai:
  orchestration:
    modes:
      navigator: { ... }
      cart_assistant: { ... }
    position-routing:
      landing: navigator
      cart: cart_assistant
```

…and core uses `ai.orchestration.position-routing` inside policy resolution to choose a mode when the request contains a `position`.

That creates coupling:
- “landing/cart/checkout” are UI concepts.
- Core becomes indirectly domain/UI aware.
- Behavior changes when a UI field is present, making debugging and portability harder.

---

## Target End State
### 1) Core: mode-only policy resolution
Core policy resolution rules become:

1. If request has `mode` → resolve that mode from the selected pack.
2. Else → use a single server default mode (pack/app configured), e.g. `ai.orchestration.default-mode=navigator`.
3. `position` is **ignored for routing**, but may be carried for debug/telemetry.

Core must not read/apply `ai.orchestration.position-routing` at runtime.

### 2) Curated packs: can still ship `position-routing` (optional)
Curated packs may keep `position-routing` as **advisory configuration** only.

- Core loads it (as part of pack YAML) but does not use it.
- Applications can optionally read it to implement “position → mode”.

### 3) App-level router (optional)
Introduce a thin, optional “router” that runs **before** orchestration:

`(position, mode?) → effectiveMode`

Rules:
- If request has `mode`, do nothing.
- If request has no `mode` and has `position`, and a routing map exists, set `mode` from the map.
- Otherwise default to the **pack/app default mode** (e.g. `ai.orchestration.default-mode=navigator`).

This router is **app/web layer**, not core.

---

## Proposed Configuration
### Curated pack schema (unchanged)
Keep the existing YAML key (for advisory routing):

```yml
ai:
  orchestration:
    position-routing:
      landing: navigator
      catalog: navigator
      search: navigator
      cart: cart_assistant
```

### Pack/app default mode (new)
Add an explicit default mode (loaded from pack or application config):

```yml
ai:
  orchestration:
    default-mode: navigator
```

Notes:
- `default` curated pack should set `default-mode: navigator` and should only ship `navigator` mode overrides.
- Domain packs (e.g., `commerce`) may also set `default-mode` (typically `navigator`).

### App-level enabling (new)
Add an explicit “use position routing” switch at the app layer:

```yml
ai:
  orchestration:
    position-routing-enabled: false # default
```

When enabled, the router can read `ai.orchestration.position-routing` and apply it **only when request.mode is blank**.

Notes:
- `default` curated pack should ship **no** `position-routing`.
- Domain packs (e.g., `commerce`) may ship a recommended map.

---

## Implementation Plan
### Step A — Remove core dependency on position routing
Update core policy resolution so it does **not** use `position-routing`.

- `OrchestrationPolicyResolutionStep`:
  - stop looking up `position-routing`
  - stop honoring `strictPositionRouting`
  - keep recording `position` in metadata (optional) for debugging
- `OrchestrationProperties`:
  - keep `positionRouting` field (for app/router consumption) or move it to a web/app module
  - but core must not branch on it either way

Acceptance:
- With `position=cart` and **no** `mode`, core still resolves to default mode (`navigator`).
- With `mode=cart_assistant`, core resolves to `cart_assistant` regardless of `position`.

### Step B — Add optional app-level router (web/app module)
Add a small component that mutates the request **before** it reaches orchestration:

- `OrchestrationRequestModeRouter` (interface)
- `PositionRoutingModeRouter` (implementation) enabled by `ai.orchestration.position-routing-enabled=true`

Integration points (choose one, app-controlled):
- In `ai-fabric-web`: `OncePerRequestFilter` / controller advice
- In Real Apps: inside the `ChatController` (or request mapping layer) before calling orchestrator

Debug metadata:
- `metadata.orchestrationPolicy.modeSource` should indicate:
  - `REQUEST_MODE` (explicit user mode)
  - `POSITION_ROUTED` (router filled mode from position)
  - `DEFAULT_MODE` (server default)

### Step C — Curated pack guidance (docs only)
Document that:
- `position-routing` is **advisory** and **app-consumed**
- core is **position-agnostic**

Update curated docs to show recommended usage patterns:
- UI sets `mode` explicitly (preferred)
- Or enable the router for “position → mode” convenience

---

## Test Plan
### Core tests
- Policy resolution ignores `position`:
  - request: `{position=cart, mode=null}` → resolves default mode
  - request: `{position=cart, mode=navigator}` → resolves navigator

### Router tests (app/web layer)
- `position-routing-enabled=false`:
  - `{position=cart, mode=null}` → mode remains null (core defaults)
- `position-routing-enabled=true`:
  - `{position=cart, mode=null}` → mode becomes `cart_assistant`
  - `{position=cart, mode=navigator}` → stays `navigator`

---

## Migration Notes
- Existing clients that relied on “position routing in core” must:
  - start sending `mode`, OR
  - enable app-level `position-routing-enabled`.

This migration is intentional: it removes hidden coupling and makes orchestration predictable.
