# Developer Experience: Actions + Confirmation Interceptors (V5) — Change Plan

## Status
Implemented (core); integration tests + demos in progress

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

### 2) Public support base for confirmation resolvers (interceptors)
**New API (framework)**:
- `ConfirmationResolverSupport` (public helper base to build app resolvers without touching framework internals)

**Result**:
- App resolvers like `CancellationRetentionOfferResolver` can use a stable helper API for:
  - reading/writing pending confirmation state
  - pushing an alternative action (offer) onto the confirmation flow
  - safely reprompting the original action when the offer is rejected

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
In the app (e.g. `Real_Apps/chat-capabilities-demo`):
- Implement a resolver bean (recommended: extend `ConfirmationResolverSupport`)
- When a cancel action is confirmed, push an “offer” pending action
- When the offer is rejected, re-prompt the original cancel action explicitly

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
