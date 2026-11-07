# Support Mount Cancellation And Storage CI

## Findings And Fix Scope

The owner requested fixes after investigation. No framework/runtime behavior,
AI prompts/actions/retrieval, permissions, ticket contract or database schema
changes are included.

1. Console could unmount while `mountInstallation` awaited session/bootstrap/
   conversation reads. Its local `mounted` controller was not assigned yet, so
   cleanup removed the frame without stopping the pack's observer/store listeners.
2. Storage CI used `ubuntu-latest`, now Ubuntu 26.04. Pinned Playwright 1.58.2
   explicitly rejects that OS. Pin this workflow to supported `ubuntu-24.04`.

The generic Support mount now accepts an optional host `AbortSignal`. Aborting
disposes the pack synchronously, before Console's frame cleanup, and releases
its per-document controller slot. Late reads cannot initialize an old engine,
start a roster/bootstrap request, or call the host's selected-request callback.
Normal explicit destroy remains supported. Destroyed-frame/controller guards and
error reporting are not weakened or filtered.

## Verification

- Six deterministic mount regressions cover pre-abort, pending session/bootstrap/
  conversation reads, immediate remount and idempotent disposal.
- The delayed-session regression fails on the pre-fix `HEAD` source and passes
  with the fix. Tests exercise the actual pack/store/observer lifecycle with a
  stubbed visual shell and strict disposed-frame host, without live API writes.
- Console delegates its existing lifetime signal to the pack; identity and exact
  BFF/SRI checks remain intact.
- Pack typecheck, 27 Support unit tests (including the six lifecycle tests), 38
  focused Console/auth/Brain checks and the production UI build passed. Existing
  runtime-config script and bundle-size warnings remain unchanged.

## Original CI Correction

Run `42421128098` completed 302 backend tests with zero failures/errors and three
PostgreSQL tests skipped; all 15 UI execution/authority tests passed. Only the
browser document journeys were skipped after the browser installation failure.
Earlier wording saying all storage tests had not run was incorrect. The OS pin
does not claim to enable those separately opt-in PostgreSQL checks.

## Rollout

Pending verification and staging-first publication. The backend image also
packages the generic Support asset, so an asset-only backend refresh may be
needed; no Java source or migration differs from the current backend.
