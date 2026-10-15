# Platform LLM Session Operating Context

Use this document to orient the next LLM session to how this codebase is developed and operated.

This is not a task handoff.
This is the baseline operating context for platform work.

Use it together with:

- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md`
- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`
- `Final_Documentation/Development_Guides/LLM-guides/USER_GLOBAL_LLM_WORKING_RULES.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_RESTART_GUIDE.md`

## 1. What We Are Building

We are building two related but different things:

### Framework

The framework now lives in the public sibling repository:

- `/Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework`
- `https://github.com/Loom-AI-Labs/ai-fabric-framework`

The framework source is no longer kept inside this private product repository. The old private-repo paths were removed:

- `Real_Apps`
- reusable framework library modules formerly under `ai-infrastructure-module`

Framework responsibilities still include:

- provider modules
- orchestration, retrieval, action connector libraries, auth primitives, vector modules, curated packs, and provider integrations

Deployable runtime and generic REST connector services are not public framework deliverables. They are private LoomAI product services and live in this private repo at:

- `ai-infrastructure-module/ai-fabric-runtime`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector`

Private products consume the framework through published Maven artifacts.
The current private product source target is AI Fabric `0.8.33` through:

- `io.github.loom-ai-labs:ai-fabric-bom:0.8.33`
- Git tag `ai-fabric-framework-v0.8.33`

The immutable tag, GitHub release, framework CI, Maven Central publication,
and empty-cache external-consumer proof are recorded for release commit
`b1a0ac2c`. The release adds the provider-neutral structured authentication
requirement consumed by LoomAI Engage; it does not add identity UI or automatic
request replay. Exact hosted image and assignment evidence belongs in
`CODEX_WORKING_CONTEXT.md` and the private handoff; do not infer it from HEAD.

Source target and hosted-fleet truth remain separate. Never infer a future live
version from a source POM or relabel the fleet before immutable image,
release/apply, and live readback evidence exists. The current dealership
closure is recorded in `CODEX_WORKING_CONTEXT.md` and its dated verification evidence;
older migration records remain historical evidence only.

The private runtime and embedding worker must each resolve one AI Fabric
version. Docker/CI builds consume released Maven artifacts and must not clone
mutable framework source. A local framework install is allowed only for
explicit unreleased-framework development and is not publication or release
evidence.

Platform uses the V04 entity lifecycle contract. The migration is greenfield
and one-way: do not add dual readers, compatibility shims, or version
fallbacks. Preserve immutable historical deployment records as evidence.
Keep `ai-fabric-execution` in the private runtime. The completed `0.7.0` Gate A
rollout preserves existing behavior with
`ai.execution.output-finalization.max-attempts=1` and
`ai.execution.specialist-chains.enabled=false`. Do not add chain tables,
secrets, endpoints, or product claims retroactively to that dependency upgrade.
Gate B schema preparation, Gate C one-worker declarative mechanics canary, and
Gate D real multi-specialist product adoption are separate reviewed decisions.
Gate A evidence is historical. Current behavior/extension readiness comes from
the published deployment configuration, source capability manifest, and dated
handoff evidence, not from that earlier migration checkpoint.

The private runtime indexing admin facade uses `IndexingWorkQuery` and
`IndexingWorkStatus` for durable per-work Data Sync reconciliation. Keep the
private HTTP route, admin authorization, tenant/deployment checks, polling
policy, and response projection in LoomAI. Aggregate queue diagnostics still
use the framework's internal queue repository contract because `0.7.0` does
not expose a public
queue-summary contract; preserve that diagnostic behavior and raise a
framework contract request before removing or duplicating it.

Before promotion, rebuild from an empty Central-only Maven cache, verify the
packaged execution JAR matches the current BOM (`0.8.33`), and repeat the hosted two-tenant,
two-deployment, and missing-boundary specialist canaries. The full release gate
must run only after those checks pass.

For the completed `0.7.0` rollout, canonical staging and production suites,
Partner, Thinker, direct specialist, isolation, and ProdUS grounded retrieval
passed. The aggregate full suites remain `FAILED` only at the explicitly
owner-deferred Shopify first-product answer-quality stage. That accepted
upgrade exception does not make the aggregate release gate green.

For Coolify applies, a healthy pre-existing application is not deployment
completion evidence. When Coolify returns a deployment UUID, poll that exact
deployment to terminal success and then refresh application readiness before
post-apply verification. Late-success release recovery may use only
`POST_APPLY` or `MANUAL_RERUN` verification; never use `PRE_APPLY` evidence.

Framework debugging and contract escalation:

- Use `/Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework` as the
  authoritative local framework checkout when debugging framework behavior.
- Before adding a product-side workaround, inspect the framework source,
  public contract, tests, and release notes to determine which layer owns the
  behavior.
- When a required endpoint or contract is genuinely a framework
  responsibility but the framework does not expose it, raise that absence as
  an explicit blocker. Record the expected contract, caller/use case, owning
  framework module, evidence that it is missing, and release impact.
- Do not hide a missing framework contract behind a private duplicate
  endpoint, dummy implementation, stub, text-matched special case, or silent
  product fallback.
- Do not declare a framework blocker merely because a consumer assumed an
  endpoint should exist. Confirm ownership and the intended public contract
  from framework evidence first.

Resolved specialist boundary and current release posture:

- The historical trusted-retrieval blocker is resolved in the immutable
  release lineage consumed by `0.7.0`; keep LoomAI's native provider filter
  and fail-closed post-filter as defense in depth.
- `0.7.0` retains exact MCP server binding, nested backend-owned read
  parameters, Lucene reader leases, and specialist grounding from approved
  read actions. Do not add product-side compatibility workarounds for the old
  behavior.
- Manifest identity now includes resolved prompt, input schema, and output
  schema. Drain or cancel non-terminal manifest-backed work before replacing a
  runtime; never rewrite stored hashes to make old work resume under changed
  behavior.
- Multi-specialist chains remain disabled during Gate A. Gate B adds only the
  application-owned chain migration. Gate C may package one reviewed one-worker
  `SpecialistChain` mechanics canary using the existing deployment-knowledge
  specialist. Gate D requires a genuinely distinct second read-only worker;
  do not invent one for a demo. Every enabled chain still requires durable
  state, stable distinct private secrets, exact-version closed topology,
  trusted-context/security/restart/replay/cancellation/drift canaries, and
  explicit owner approval.
- LoomAI must use the official `SpecialistChain` resource contract when its
  bounded JSON mapping/projection is sufficient. A reviewed Java chain is for
  genuine application-owned invariants only. Never add a LoomAI-only chain DSL
  or a second chain engine.
- AI Fabric `0.7.0` durable Human Review is specifically bound to
  `ReviewSourceType.ACTION_PROPOSAL`. Reuse its `ReviewDecisionGateway`, policy,
  persistence, dispatch, replay, and recovery contracts; do not duplicate them
  or present arbitrary answers/analysis/chain/Smart Brain results as action
  review. LoomAI owns V04/runtime/API/UI productization, while the customer
  application remains authoritative for reviewer identity, roles/scopes,
  separation of duty, domain authorization, and system-of-record effects.

Framework responsibilities:

- reusable primitives
- runtime correctness
- generic contracts
- trusted extensibility
- security and performance at the primitive level

### Platform / Product

The product lives primarily under:

- `Platfrom/backend`
- `Platfrom/ui`
- rollout orchestration, verification, marketplace control plane, managed profiles, and admin/operator UX

Platform responsibilities:

- managed workflows
- deployment creation, draft, publish, apply
- verification and operational truth
- rollout management
- product-facing templates and guided defaults
- admin and operator usability

### Platform-hosted AI Workspace installation

The current browser integration contract is a Platform-owned **AI Workspace
installation**, not a customer-authored runtime descriptor. A customer page
loads the stable Platform script
`/api/public/ai-workspace/install.js` with one opaque public installation ID.
The Platform resolves that installation through its bound consumer to the
consumer's explicitly assigned, verified deployment and returns a safe,
origin-scoped manifest plus immutable workspace/experience assets.

Keep these boundaries intact:

- The Platform is the control plane and asset source. It is not the per-message
  chat, retrieval, action, document, or provider proxy.
- `public-runtime-anonymous` bootstraps at and then talks directly to the
  assigned deployment.
- `public-runtime-authenticated` obtains a short-lived deployment token from a
  reviewed host-identity broker, then talks directly to the deployment.
- `backend-mediated-private-runtime` talks to a reviewed trusted adapter such
  as the Shopify Bridge; runtime assignment credentials and private assertions
  never reach the browser.
- The generic installer and widget know only typed connection handlers and
  pack registration. Dealership, Shopify, vehicle, product, and provider logic
  belongs in experience packs or trusted adapters.
- Rebinding a consumer affects new sessions. Never silently move an active
  conversation to another deployment or replay its history there.
- Do not restore the retired dealership `/api/public/runtime-descriptor` or
  `data-bootstrap-url` path. Explicit programmatic widget initialization
  remains supported for integrations that do not use Platform installation.
- Do not enable the authenticated profile until a real reviewed broker is
  configured and its identity, scope, origin, expiry, revocation, and runtime
  validation are proved end to end.

The canonical design, implementation status, and release evidence are tracked
in `010_31_PLATFORM_HOSTED_AI_WORKSPACE_INSTALLATION_AND_ASSIGNED_RUNTIME_DISCOVERY_PLAN.md`.

Production self-service productization is approved planning scope in `010.36`
section 10, not a hosted completion claim. Production control-plane ownership,
runtime target placement, and Engage frontend delivery are independent. A
production-owned app may initially bind a verified customer-staging release,
then explicitly activate a verified production-target release through existing
consumer guards. Normal promotion is not cross-control-plane export/import or
an in-place provider move. Customer staging is not disposable developer staging;
cleanup must check customer ownership/bindings and retain protected resources.
Simulator data/accounts, target credentials/indexes/inboxes, and token authority
must be isolated, with simulation explicitly labelled and never substituted for
failed real provider access. Preserve pinned active sessions; assignment change
is not permission to migrate conversations, attachments, pending effects, or
human queues. Keep all outstanding Engage acceptance in `010.36` section 6.2
open until actual verification closes it.

The 010.36 hosting policy is stricter than the general placement alternatives:
customer staging is staging Coolify only (`46.224.145.148`); production Coolify
(`46.225.162.106`) hosts production-target deployments and shared production
services. A production-owned app bound to staging does not provision its
runtime on production. Validate actual target/server placement and reject
staging spillover, including hidden production dependencies. Exhausted staging
capacity is an explicit admission failure, not permission to use production.
Measured quotas, update/retry reuse, failed-resource reconciliation, notified
expiry, authorized retention/extension, post-promotion staging retirement, and
database/index/volume reclamation are required before final UI extraction.
Developer cleanup may not delete valid customer staging, but an authorized
customer lifecycle policy may retire it after ownership, assignment, active
session/human-work, and backup/retention checks. Preserve historical evidence
and source objects; history does not require an indefinitely running fleet.
Immediate staging rollback ends when that target is retired. Plan edits alone
authorize no existing resource cleanup.

010.36 customer self-service reuses `CUSTOMER_ADMIN`, scoped to its customer,
and the existing Platform UI/configuration APIs. Customer and staff navigation
compose shared editors; do not build duplicate portal configuration/persistence
or a second frontend project. Customer document knowledge and connections are
normal supported operations under installed capability/audience policy, not
unrestricted source upload or global secret administration. AWS Console and
Cloudscape inspire operational layout and resource hierarchy; adopting a new
UI library or AWS infrastructure is not approved by that reference.

The user requires UI extraction last: complete and verify P0-P5 functional and
non-extraction work through existing views/APIs before P6 shared-editor
extraction/customer-shell changes. Record an independently verified baseline.
Keep P6 in UI-only commits without schema/API/configuration mutations or
provider/assignment changes. Reverting that UI must preserve all completed
backend work, current configuration, deployments, and indexed knowledge.
Pause extraction for any discovered backend gap and verify it separately.
P7 certifies the final customer UI after visual/workflow review; a UI rollback
reopens that presentation acceptance, not independently verified functionality.

Preserve staff capabilities through 010.36 extraction using
`010_36_ADMIN_OPERATOR_UI_CAPABILITY_BASELINE_2026_10_14.md` and the frozen
source snapshot under `verification-support/platform-ui-capability-baseline`.
It is source-only evidence, not a live parity certification. Record P0-P5's
separate verified baseline and map every retained/moved staff feature, field,
action, role restriction, and operational view to post-extraction browser proof.
Customer navigation reduction must not remove authorized admin/operator tools.

Promotion exposes the section 10.5.1 customer Production Readiness & Go-Live
checklist using existing lifecycle evidence, not another workflow engine.
Bind mandatory checks to the exact immutable candidate, production target,
configuration and expected consumer revision. Missing/stale/failed mandatory
proof blocks activation; a user checkbox or readiness average cannot bypass it.
Production activation and staging-retirement closure are separate. Show
outstanding drain/cleanup, explicit retained staging and expiry, actual provider
reconciliation, and loss of staging rollback after retirement. Cleanup must
protect the active production service, shared dependencies and customer source
objects. All functional gates precede final UI extraction/presentation.

The code-reviewed implementation contract for 010.36 is section 10.12 R1-R7.
P0 must close actual schema/API/permission decisions, Platform/canary
prerequisites and measured limits before P1; document review is not P0 closure.
Deployment-level environment/latest runtime/active version are projections,
not authority for target-specific administration. Resolve the explicit owned
target/release, including pre-activation indexing under reviewed operator
permissions, and bind index/token/secret authority separately per target.
Consumer activation requires a frozen bound staging candidate, database-backed
binding revision, idempotency and atomic revalidation; all rebind paths retain
the appropriate readiness/ownership guard. Retirement operates only on owned
target resources and retains lineage/history, never through whole-deployment
deletion or orphan metadata marking alone.

The promotable first template uses customer-owned S3-compatible text/JSON
sources in staging and production. Preserve the existing production mounted
source denial; mounted developer demos are not this customer-storage contract.
Dedicated simulator/command receiver/Basic Inbox instances per customer/target
are the first offer; shared tenancy needs a separate proven contract. Provision
their immutable artifacts, accounts, secrets, callbacks, storage and unique
operator credentials through owned managed-service lifecycles with no shell
repair. Customer scope must fail closed when absent/invalid, not become global
staff scope. Staff catalogue/admin rights do not transfer to customer actors.
The first offer is invite-based, explicitly simulated and capacity-bounded;
no automatic real provider, public signup, PDF/Word, enterprise SSO or HA claim.

Hosted status as of 2026-10-09:

- Current Platform/backend/UI/public-site release identity is
  `bbe647d2706884cb100618048e4358bf9cd08370`. Both staging and production
  Platform backends are ready; the production backend image is explicitly
  pinned to that immutable tag.
- Northfield installation `awi_pub_456ec9f67744f4d7e231a939e835ac05`
  is active and resolves consumer `northfield-dealership-demo` to verified
  deployment `dep-f023c863`. All ten installation readiness checks pass.
- The canonical production-site dealership live gate and strict `12/12`
  quality corpus pass. Browser traffic goes directly to the assigned runtime;
  exact-origin denial, manifest ETag handling, immutable asset integrity,
  page attachments, grounded reads, and confirmed write receipts are proved.
- The existing production Shopify Bridge remains healthy as
  `backend-mediated-private-runtime`. Live adapter bootstrap exposes no private
  runtime material; grounded retrieval and read-only `shopify_search_catalog`
  execution pass through deployment `dep-8c3e7259`.
- `public-runtime-authenticated` is code-complete and deterministically tested,
  but remains disabled because no real reviewed host-identity broker has been
  registered. Do not describe that mode as live-proven or bypass the broker
  requirement.
- The temporary production Coolify access window used for this release is
  closed; the operator CIDR is absent from both Hetzner firewalls and direct
  local access returns HTTP `000` while public readiness remains healthy.

Do not collapse these layers mentally.
The framework is not the product.
The product should not leak raw framework complexity without reason.

## 2. Core Philosophy We Follow

### Greenfield discipline

This codebase is treated as greenfield.

That means:

- prefer the right shape over compatibility clutter
- delete obsolete paths instead of preserving them
- avoid parallel control surfaces that express the same thing twice
- do not keep deprecated behavior “just in case”

Greenfield does not mean reckless flexibility.
It means clean, intentional design.

### Deterministic control plane

Critical control-plane flows must stay deterministic:

- create
- validate
- publish
- apply
- verify
- approve
- assign
- scale
- reconcile

Important decisions in these flows should be made by explicit code and explicit state, not hidden AI behavior.

### Natural-language semantic decisions

For deployment-local AI behavior, production code must not infer the meaning
of a user query or model prompt with keywords, substrings, regular expressions,
token overlap, punctuation, query length, or phrase order. This applies in the
framework, private runtime, connectors, Platform adapters, generic AI
Workspace, and experience packs.

Intent, action, parameter, target, vector-space, knowledge-source, retrieval-
mode, and evidence decisions must come from validated
structured LLM output, trusted structured input, or a declared provider
contract. Deterministic code validates structure, authorization, allowlists,
provenance, and bounds; it must not guess a semantic replacement.

Presentation-only matching is allowed for labels, icons, renderers, and
illustrative progress when it cannot change the submitted request, backend
semantic decision, authority, diagnostics, or audit truth.

LLM output may be imperfect. Improve its prompt, schema, available catalog,
examples, and model, and measure the result over varied wording. A bounded LLM
repair is permitted when configured. Otherwise clarify or fail visibly rather
than adding text matching.

Any recovery that changes model-selected behavior is denied unless a named,
server-owned allow-policy permits it. Allowed recovery must expose the original
and effective decisions and may never broaden authority. A mechanism is not
"diagnostic" if it mutates intent, routing, evidence, authority, or the
semantic result. UI must not present illustrative state as proof of backend
execution.

Canonical remediation and audit inventory:

- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_33_LLM_DRIVEN_SEMANTIC_DECISIONS_AND_EXPLICIT_RECOVERY_POLICY_PLAN.md`

### Verification is part of the product

A deployment is not operationally complete because it exists.
It is operationally complete when it can be verified.

Verification is a product requirement, not an afterthought.

### Fail closed

If auth, config, resolution, or verification is uncertain:

- deny
- block
- fail the operation
- surface a clear reason

Do not silently degrade security or correctness.

### Productize only runtime-backed contracts

The platform should only expose product features that have a real runtime/framework contract behind them.

This is especially important for:

- marketplace features
- shell extensions
- inference profiles
- data plugins
- admin actions

If the runtime cannot enforce or verify it, the platform should not pretend it is a finished feature.

## 3. How We Design Features

### Start from the managed workflow

The first question is not:

- “What config knobs can we expose?”

The first question is:

- “What is the clearest managed workflow for the operator?”

Preferred pattern:

1. define the product workflow
2. define the explicit state transitions
3. define the runtime-backed contract
4. add validation
5. add verification
6. then add UI and docs

### Prefer composition through deployment config

Most platform features should resolve into deployment artifacts or deployment config.

Examples:

- actions config
- shell config
- knowledge source config
- provider config
- marketplace-installed contributions

The live runtime should consume resolved config, not marketplace metadata or UI state.

### Keep platform awareness out of runtime when possible

The runtime should not know catalog, billing, publisher workflow, or UI concepts.

The platform compiles higher-level concepts into deployment-time artifacts.
The runtime consumes those artifacts.

## 4. How We Implement Changes

### Typical sequence

Normal implementation sequence:

1. inspect existing code and current contracts
2. identify the true control-plane and runtime boundaries
3. implement the smallest coherent slice
4. add or update tests
5. verify locally
6. verify live if the feature affects deployed behavior
7. commit and push

### What “done” means here

A feature is not done when the code compiles.

It is done when:

- the contracts are coherent
- validation exists
- verification exists
- tests exist
- docs do not materially contradict implementation
- the operational path is understandable

### What to avoid

- UI-only fixes for missing backend contracts
- product flows that bypass publish and apply
- hidden fallback behavior that changes live semantics
- duplicated settings in multiple places
- open-ended plugin surfaces without bounded contracts

## 5. How We Verify Work

We distinguish several verification layers.

### 5.1 Local code verification

Always do the smallest relevant verification first:

- targeted tests for the changed modules
- broader test suite if the change crosses module boundaries
- `git diff --check`
- frontend build if UI changed

### 5.2 Release verification

Release verification proves that the live runtime or connector has loaded the expected published version state.

This is parity proof.

Typical checks include:

- actions config parity
- prompt config parity
- shell config parity
- knowledge source parity
- provider config parity
- confirmation or policy parity

### 5.3 Hosted verification

Hosted verification proves the product-managed live behavior.

This is behavior proof.

Typical checks include:

- runtime query path
- auth posture
- action execution
- retrieval behavior
- rollout-specific scenarios

### 5.4 Direct live verification

If there is doubt about hosted verification, run the repo scripts directly against live services.

This separates:

- real deployment/runtime issues
- hosted runner issues
- stale platform deployment issues

### 5.5 Canonical rollout deployments

Canonical rollout deployments are used as live fixtures.

They are important because they let us prove:

- old features still work
- new features work in realistic environments
- provider and runtime drift is visible

If a feature affects live deployment behavior, live rollout validation matters.

## 6. How We Decide Something Is Production-Ready

Production-ready in this codebase means more than “works once.”

Minimum standard:

- clear contract
- bounded scope
- validation before publish or apply
- explicit failure modes
- release verification
- useful operational visibility
- security reviewed at the contract level
- no hidden dependency on local-only behavior

For user-facing product surfaces, also require:

- understandable UX
- actionable errors
- minimal confusion about where values must be set
- clear prerequisite handling

For runtime-affecting features, also require:

- live proof on a realistic deployment
- evidence that old behavior did not regress

## 7. Security And Auth Standards

### Security posture

We prefer:

- fail-closed auth
- explicit trusted-backend boundaries
- secret refs instead of plaintext config
- deterministic authorization decisions
- auditable denial behavior

We reject:

- silent partial access
- ambiguous trust boundaries
- secret values committed into normal docs
- runtime behavior that depends on undeclared trust assumptions

### Auth model expectations

When working on auth-sensitive features:

- separate public, private, and platform-trusted flows clearly
- verify issuer and audience semantics
- verify ingress mode assumptions
- confirm runtime auth overview surfaces match expectations

Do not treat “request succeeded once” as auth proof.

## 8. Marketplace-Specific Operating Rules

Current public marketplace plugin types are:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

Marketplace is a control-plane composition layer.

Implications:

- installs compile into deployment drafts
- publish and apply remain mandatory
- runtime does not load marketplace code dynamically
- platform should not expose plugin ideas that have no runtime-backed contract

Marketplace work should be judged by:

- install flow correctness
- deployment config compilation correctness
- publish/apply correctness
- release verification
- live behavior proof

Not by:

- catalog appearance alone
- UI-only install success
- draft-only state

## 9. UI Expectations

UI is not decoration.
It is the operator surface for a managed platform.

UI work should:

- expose the real workflow
- reveal prerequisites early
- reduce raw JSON editing when a typed form is possible
- surface actionable errors
- avoid fake control that the backend cannot honor

When users repeatedly do not know where to set a value, treat that as a product defect, not user error.

## 10. How The Next LLM Session Should Behave

When starting a new task:

1. identify whether it is framework or platform work
2. identify the real runtime/control-plane boundary
3. check whether the feature already has a runtime-backed contract
4. if not, build the contract first
5. keep scope coherent and bounded
6. verify locally
7. verify live if the behavior is deployment-facing
8. commit and push when the slice is complete

When debugging:

1. identify the failing layer first
2. do not assume the runtime is wrong just because hosted verification failed
3. compare release verification, hosted verification, and direct live verification
4. inspect admin or overview endpoints before inventing theories

When making product decisions:

- optimize for operator clarity
- optimize for deterministic workflows
- optimize for explicit operational truth
- reject flexibility that adds ambiguity without value

## 11. What To Read Before Major Changes

For platform/product work:

- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`

For framework/runtime work:

- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`

For marketplace work:

- `doc/Productization/future-work/MarketPlace/README.md`

## 12. Final Rule

Do not confuse activity with progress.

In this codebase, high-quality progress means:

- clearer contracts
- less ambiguity
- stronger verification
- safer operational behavior
- better operator usability
- simpler, more intentional system shape

## 13. Current-Only Version Policy

LoomAI is a green-field platform. Support exactly one current framework,
runtime, and Platform contract at a time.

- Do not add compatibility branches, parallel old/new execution, legacy mode
  aliases, or multi-version deployment matrices unless they are required for
  one bounded migration into the current state.
- Historical tags, stopped applications, exports, and configuration snapshots
  are recovery evidence. They are not supported customer/runtime versions.
- Upgrade the canonical deployment in place. A planned maintenance window is
  acceptable when duplicate capacity would be unsafe or wasteful.
- Reject retired selectors and stale contracts after migration. Translate only
  current UI vocabulary into the current runtime contract at an explicit
  boundary.
- Verification and product claims must target the current immutable release;
  a pass on an older release does not count as current evidence.

As of 2026-10-15, the only supported source and next-rollout AI Fabric baseline
is `0.8.33`. Exact hosted image, rollout and live product evidence remain
separate facts recorded in `CODEX_WORKING_CONTEXT.md`, the private session
handoff and the applicable execution plan. A Central-only consumer pass or a
focused product canary does not prove that the hosted fleet has been upgraded.
