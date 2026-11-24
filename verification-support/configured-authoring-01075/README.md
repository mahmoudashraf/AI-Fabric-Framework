# Configured Build with AI Migration - Implementation Record

Implementation and live qualification in progress, 24 November 2026. Staging
has a verified native release, but production migration and full acceptance are
not complete. The owner approved the full010.75migration and framework work.
No infrastructure/capacity or spending-cap change has been performed.

## Worktrees and Baseline

- Product source: Platform-V11, baseline 136134b05d9157ed959937e4fc1c9708765d478c.
- Framework source: /private/tmp/loomai-framework-01075,
  codex/configured-authoring-01075, based on released f933227e.
- Framework candidate: 4f9673a5231189c06864a3e07ededb15705720ff, public
  main/tag ai-fabric-framework-v0.8.68. Signed Central publication45377408953
  completed successfully. Nine official Central JARs downloaded and matched
  their repository checksums; SHA256proof in/private/tmp/loomai-01075-central/proof.json.
  Product sources target0.8.68; staging authoring now uses it natively.
- Live Support and production authoring remain on0.8.67.
- Unrelated pre-existing dirty files, screenshots and .gitignore changes are
  preserved. Public framework and private feature/adoption commits are pushed;
  subsequent qualification findings are recorded below.

## Implemented Locally

- Opt-in native bounded workflow planning over governed READ and WRITE actions.
  Authorized draft writers retain their existing no-confirmation policy; other
  writers pause on their actual confirmation contract. Ordinary chat stays off.
- One root model/action/write/correction allowance, encrypted checkpoints on the
  existing leased execution record, stable operation IDs and receipt readback.
  Unknown effects do not blindly replay. Model stop decisions always stop.
- Transactional Platform draft operations with original actor/resource scope,
  fresh user/delegation checks, frozen per-profile tools and encrypted receipts.
- Published workflow compiler, signed artifact bootstrap and nine editable
  page/task-selected native profiles. Generic first-party bootstrap template.
  The whole-Brain profile combines supported changes only inside the selected
  Brain draft. Kit/navigation guidance has no WRITE tools. Human-support setup
  keeps its existing narrower draft paths under the channel specialist.
- Canonical conversation transport and Runtime-to-SDK bridge reuse one existing
  conversation lease and snapshot; no second interactive history owner.
- Feature-gated Platform facade plus UI operation receipts, partial-save
  refresh and group undo. The custom generation loop, private generation API,
  separate inference client and Java task-instruction catalogue are removed
  locally. No replacement custom inference loop or live cutover is claimed.
- Existing draft row locks serialize manual/AI writes and refresh the latest
  saved state. Each work item retains its original draft identity. No new
  revision framework or second authoring approval flow was introduced.
- Workflow instructions, tool switches and finite policy fields in the existing
  specialist Advanced surface. Only already-configured workflows are shown.
- Actual selected profile, published release/hash, consumed allowances and stop
  reasons travel with native workflow results, rather than inferred UI labels.
- Cancellation/erasure: INTERRUPT_AI advances the active epoch
  without closing history. Authoring content deletion keeps closed ownership
  tombstones and separate budget/accounting records.

## Verification So Far (Local, Not Live)

- Framework: 31 focused workflow/pipeline tests and 63 selected native execution,
  compiler and checkpoint checks passed. The default 38-module `test` reactor
  passed. A subsequent `install` also invoked opt-in Failsafe integration tests:
  that stage failed with absent ONNX assets and fixture-dependent RAG failures.
  It is retained as a failed integration qualification, not called a green build.
  The immutable publication gate excludes those integration-test artifacts and
  still runs the release modules' tests.
- Platform: 124 focused migration/regression checks passed. A further 20 checks
  passed for actual two-thread database locking, frozen-release selection,
  per-tool authentication and preservation of the original provider credential
  and budget. A concurrent manual edit survives an AI change, and an old work
  item cannot silently target a newly selected draft.
- Runtime: all eight official profiles and governed native handlers loaded
  through the normal application. A canonical request executed a governed
  draft write, stored encrypted state and replayed without a second write.
  These tests use explicit provider/receiver fixtures, not a live model.
- UI: 71 assistant/search tests passed; the complete production build passed,
  with the existing bundle-size warning. Browser/live qualification remains.
- Complete Runtime regression: 802 checks, zero failures/errors, five opt-in
  skips. Includes PostgreSQL migration/accounting preservation and ordinary
  Support/Tasks plus configured-authoring startup/governed-write tests. These
  are deterministic fixtures, not live model-quality evidence.
- Broader Platform migration/provider/authority regression: 245 passed. The
  complete backend suite and expanded browser journeys are still running.
- The public Framework Build for 4f9673a passed, including vector container
  contracts and real consumer smoke boots (run45377157484). Central publication
  run45377408953 remains processing; no duplicate upload has been started.
- Packaged native startup at the existing 384MiB heap passed real HTTP health,
  status serialization, unchanged uninitialized funding and removal of the
  private generation endpoint. Observed RSS450MiB was below the existing
  640MiB service limit. The final nine-profile package also passed these real
  HTTP checks; observed RSS485MiB, same384MiBheap/640MiBservice limit. No model
  call or initialized funding was used to manufacture that startup proof.
- Final nine-profile Runtime regression: 802 checks passed, zero failures/errors,
  five explicit skips. Clean isolated Platform assistant suite:123passed, plus
  43passed for final guide-task/turn handling. UI71unit checks and production
  build passed;51browser groups passed, followed by the narrowed Action reviews
  check and workflow-editor save/tool-reenable/unknown-field checks at390px.
- Preflight found that both existing services use the private Docker alias
  `http://loomai-authoring-runtime:8097`, no public FQDN and existing PostgreSQL
  datasources. Adoption now preserves that operator-configured private route;
  it checks the exact service identity and original budget, does not add domains
  and rejects public HTTP, loopback, userinfo and non-base paths. Eight lifecycle
  tests include these address restrictions.
- The broad backend run began with cached0.8.67application resources while the
  source capability manifest was updated to0.8.68. Three version-consistency
  failures are retained; they require a fresh consistent-version rerun and are
  not called a green whole-backend result. No production behavior was changed
  to bypass version checks.

## Still Required Before Completion

Staging adoption is in progress, not qualified. The core UI/backend feature
commitf54e2b2a0 is healthy on staging; runtime artifactdsa-a2537ec2 is attested
against nine official CentralJARs. The managed Brain is installed and version
ver-ff755b1a published. Its first apply was rejected before creating a release:
copied Git source defaults did not describe the actual existing image service.
The backend-only metadata correction passed22checks; deploy it and repeat the
authorized installation normalization before applying this same version.
Production and both live authoring runtimes remain0.8.67. No paid calls yet.

1. Finish full Runtime regressions, exact published tool-disable behavior,
   cancellation, source removal and session-erasure qualification.
2. Establish ordinary managed Brain/draft/version/release records while reusing
   the existing native assistant capacity/database and original budget identity.
   No fresh allocation, provider/container capacity or funding reset is allowed.
3. Qualify published prompt/capability changes, exact immutable bindings and
   normal Support/Task regressions. Complete source capability/health evidence.
4. Verify the locally retired private generation endpoint stays absent after
   deployment. Legacy sessions remain read-only history.
5. Publish a new immutable framework release only after complete checks, adopt
   it in products, deploy staging, run the 010.75 live acceptance cases and UI
   desktop/mobile checks, then release/verify production.
6. Update this evidence and the working context with actual commit/image/release
   identifiers and honest quality results. No live migration is claimed yet.

## Current Financial Bounds

The owner approved 185 -> 210 turns and 2,000,000 -> 4,000,000 tokens per
environment in reply call_4197bbcbfc5348f29d4d8b892ea14dfc. Both increases were
applied through the existing top-up API and read back. USD5 caps, original
approval/store/fingerprint, expiry and accumulated usage remain unchanged.
No model calls were made by this maintenance. Native service adoption must use
these actual preserved limits, not create a second allocation.

## Staging Authority Qualification, 24 November 2026

- Adoption correction676ecb35a passed CI and is healthy on staging. The existing
  image service now retains its real PostgreSQL/port defaults. The original
  private route,640MiB/1CPU capacity, provider key and budget remain unchanged.
- The operator TEAM_AUTHORIZED target list now includes the exact existing
  `configuration-authoring-staging` target, retaining all previous entries.
- First queued release rel-68aff6e7 stopped PRE_APPLY_BLOCKED/FAILED before any
  Runtime change. Its frozen authority snapshot was absent: normal release
  freezing included private Tasks but omitted enabled private workflows.
- The normal freeze predicate now includes enabled workflow assistance. No
  authorization bypass or synthetic snapshot is used. Focused authority,
  lifecycle, compiler, apply-guard and funding-mode regression:82passed.
  Verification exposes safe failure phase/type, not exception/secret contents.
- Deploy this backend correction and create a new normal apply for the same
  published version. Preserve the failed release. Runtime adoption and paid
  model qualification are still pending; production remains unchanged.

- Authority correction6ad6bf6dd passed all three release gates and is healthy
  on staging (backend910ca1c6). New release rel-73298aad passed preflight but
  failed before the environment/image update: the generic plan's conversation
  fallback names retained unfrozen legacy secret references. Frozen authority
  now supplies both existing property names for each actual Runtime key.
- The first-party provider-preservation helper also covers embedding aliases,
  which are disabled but must not resolve an unrelated global account. The
  existing paid key remains authoritative.106focused authority, lifecycle and
  accounting checks passed. No paid model calls or native service update yet.

## Native Live Qualification In Progress

- Staging rel-6f33b144/ver-ff755b1a is APPLIED_VERIFIED/PASSED, with nine native
  profiles, original ledger/PG/provider credential and640MiBcapacity preserved.
  The old generation POST returns404. Console reports CONFIGURED_BRAIN/ready.
- First prose and OpenAPI cases exposed two model-planning mistakes without
  writes: interpreting an empty parsed-operation list as absent prose sources,
  and interpreting a successful candidate check as a saved draft. Both failed
  cases and their complete cleanup remain recorded, not replaced by a retry.
- Shared instructions now explicitly distinguish selected documents from
  parsed operations, and validation from an actual draft-write receipt. This
  was published/applied as ordinary Brain configuration, ver-fb756de5/
  rel-a506e44d, without a new Runtime build or permission/budget change.
- The next OpenAPI case passed: get_context -> check_candidate -> update_draft,
  one actual write and four model calls, two READ capabilities and exact routes.
  The prose UI case stopped before inference with missing SDK failure details;
  it is not counted as a quality pass or ignored. Safe failure reason/status
  preservation is now covered by8focused Runtime startup/diagnostic checks.
- Database-backed selected-prose regression12passed; compiler/binding checks23
  passed. Further live operation-selection/missing-input tests are running.
  Production remains unchanged. Full acceptance is still pending.

- Further scope tests passed narrow endpoint preparation and missing-credential
  clarification, but two requests stopped before inference. The deadline suffix
  correlated exactly with PostgreSQL rounding in all six inspected cases: every
  down-rounded deadline failed, every up-rounded deadline proceeded. The SDK
  checkpoint guard correctly rejected the original nanosecond ceiling when it
  exceeded the persisted microsecond ceiling. The805-check diagnostic Runtime
  CI also exposed this in its ordinary JDBC startup test (not a transient CI error).
- Runtime now rereads its durable execution and takes the earlier of the
  requested and persisted deadlines before creating the workflow request. No
  tolerance, wider authority or weakened checkpoint comparison is introduced.
  Real PostgreSQL reproduces both directions;7focused precision/startup/diagnostic
  checks passed. The first draft of that test fixture had an assertion-order and
  duplicate test-idempotency issue; corrected final run is green. Hosted Runtime
  update and complete live rerun remain required.
