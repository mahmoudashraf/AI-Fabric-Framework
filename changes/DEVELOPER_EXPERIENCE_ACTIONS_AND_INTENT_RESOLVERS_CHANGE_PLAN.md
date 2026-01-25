# Developer Experience: Actions + Confirmation Interceptors (V5) — Change Plan

## Status
Implemented (core); demos + integration tests in progress

## Source
Based on: `ai-infrastructure-module/docs/DEVELOPER_EXPERIENCE_IMPROVEMENT_PLAN.md`

## Scope
This plan focuses on **developer ergonomics** for:
- Implementing actions (reducing boilerplate, typed params).
- Implementing confirmation “interceptors” (resolvers) in apps (e.g. retention offers).

**Greenfield rule**: no backwards compatibility for the legacy `ActionHandler` API.

---

## What We Implemented

### 1) Annotation-driven actions (DX Plan “Solution 1”)
**New API (framework)**:
- `@AIAction` (class-level; `requiresConfirmation` is required; no default)
- `@ActionExecute` (required execution method)
- `@ActionConfirmation` (optional confirmation message method)
- `@ActionFacts` (optional “facts for LLM” method)
- `@ActionAllowed` (optional access-control guard)
- `@Param` (param metadata + required flag)

**Runtime support (framework)**:
- `AIActionRegistry` (discovers `@AIAction` Spring beans and exposes metadata + handlers)
- `AIActionHandler` (runtime contract used by the orchestration pipeline)
- `AnnotatedAIActionHandler` + `ActionMethodArgumentBinder` (typed binding + invocation)
- `RegisteredActionsAIActionProvider` (feeds “registered actions” into prompts/LLM selection)

**Result**:
- Action authors no longer implement metadata builders or `Map<String,Object>` casting helpers.
- Actions can explicitly opt into **no confirmation** with `requiresConfirmation = false`.

### 2) Annotation-driven confirmation interceptors (DX for interception flows)
This complements the DX plan’s “interceptors” section by removing most of the boilerplate around writing a full
`IntentResolver` class for common confirmation interception patterns (retention offers, upsells, etc).

**New API (framework)** (in `ai-infrastructure-chat-session`):
- `@AIConfirmationInterceptors` (class-level Spring stereotype)
- `@OnPendingActionConfirmation(...)` (method-level rule declaration)
- `ConfirmationInterceptionContext` (small helper context: pending stack ops + decision helpers)
- `InterceptionDecision` (what intents to replace with + which actions are already confirmed)
- `AnnotatedConfirmationInterceptorsResolver` (framework resolver that discovers and runs these handlers)

**Key behaviors**:
- Runs early (priority `8`) before the default confirmation resolvers.
- Stores loop-guards in **pending action params** via `onceParam` (portable across stores).
- Lets apps rewrite a confirmation turn into:
  - a new ACTION intent (prompt or execute)
  - an INFORMATION intent (short direct reply)

### 3) Greenfield cleanup
Removed from `ai-infrastructure-core`:
- legacy `ActionHandler`
- legacy `ActionHandlerRegistry`
- legacy action provider `ActionHandlersAIActionProvider`

---

## App Developer Experience (How To Use)

### Implementing an action
1. Create a Spring bean annotated with `@AIAction(name=..., requiresConfirmation=...)`.
2. Add one `@ActionExecute` method with typed args annotated by `@Param`.
3. Optional: `@ActionConfirmation` / `@ActionFacts` / `@ActionAllowed`.

### Implementing a confirmation “interceptor” (retention offer)
Recommended in apps: use the annotation API instead of writing a full `IntentResolver`.

In the app (e.g. `Real_Apps/chat-capabilities-demo`):
- Create a bean annotated with `@AIConfirmationInterceptors`
- Add methods annotated with `@OnPendingActionConfirmation(...)`
- Return `InterceptionDecision` (use `ConfirmationInterceptionContext` helpers):
  - `promptAction(action, params)` to route to another action (often with its own confirmation)
  - `executeAction(action, params)` to execute immediately (marks action as confirmed for this request)
  - `reply(text)` for short no-RAG/no-generation acknowledgements

The demo retention flow was migrated from a hand-written resolver to this annotation API:
- `Real_Apps/chat-capabilities-demo/.../CancellationRetentionOfferResolver.java`

---

## Follow-ups (Next DX Iterations)

### Validation (DX Plan “Phase 2”)
Add validation support in `ActionMethodArgumentBinder`:
- `@Param.pattern`
- `@Param.allowedValues`
- `@Param.min` / `@Param.max`

Add a “custom validator” SPI:
- `ParamValidator<T extends Annotation>`
- registry of validators discovered as Spring beans

### Parameter groups
Support record/POJO param groups as a single argument (optional, but high DX for large actions).

### Templates + docs
Add a short guide + examples in `Final_Documentation/Development_Guides` for:
- “Your first @AIAction”
- “Retention offer interceptor (resolver) template”

---

## Acceptance Criteria
- New actions are implementable with a single class and typed method signatures (no manual map extraction).
- App confirmation interceptors can be built with minimal boilerplate and without relying on framework internals.
- `mvn -f ai-infrastructure-module/pom.xml verify` passes.
