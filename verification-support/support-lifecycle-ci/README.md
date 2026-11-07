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
- Local read-only browser checks passed repeated Home / Brain shell transitions
  with no captured console errors, followed by opening the real Support composer.
  No AI query or ticket/configuration mutation was sent. Screenshot in `local/`.
- Independent focused review reported no findings in the lifecycle changes.

## Original CI Correction

Run `42421128098` completed 302 backend tests with zero failures/errors and three
PostgreSQL tests skipped; all 15 UI execution/authority tests passed. Only the
browser document journeys were skipped after the browser installation failure.
Earlier wording saying all storage tests had not run was incorrect. The OS pin
was then verified by rerunning the complete workflow, including PostgreSQL checks.

## Rollout

Source `c311cdd0344c102c09c7623b636df95cb3e23595` is pushed on `Platform-V11`.
[Core image CI 42423903778](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42423903778)
passed for both images. The backend image also packages the generic Support
asset; no Java source or migration differs from the previous backend.

[Storage CI 42423903772](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42423903772)
is green on Ubuntu 24.04: **302 backend tests passed with none skipped**, 15 UI
execution/authority checks passed, and all 11 mocked document-browser journeys
passed. The previously skipped PostgreSQL checks ran successfully on this runner.

Pre-rollout database backups completed on both hosts (staging approximately
9.7 MB, production approximately 11.1 MB).

- Staging backend job `wkx549utrabsed5myatnpw48` and UI job
  `eo1bnsa1uoqlxb09ii4dktdn` finished. Both exact-source containers are healthy;
  backend migration state remains `235:true`.
- Production backend job `z9v0dexrlc5um7en496toexb` and UI job
  `tj0vkv949pglkg0bve7kyawx` finished. Both exact-source containers are healthy;
  backend migration state remains `235:true`.
- Hosted staging and production passed repeated Home / Brain shell transitions,
  dock opening, dedicated Support page initialization and sign-out with **zero
  captured console errors**. Production included four repeated navigation cycles.
- On each host, the initialization indicator disappeared and the iframe composer
  was enabled. Merely finding an early-rendered textarea was not counted as ready.
- Browser verification was read-only: no AI query, request submission, customer
  configuration, app assignment or infrastructure mutation. Test sessions were
  observed signed out and temporary tabs closed. Screenshots are in `live/`.

Both reported issues are closed. No framework release, runtime behavior change,
schema migration or log/error suppression was needed.
