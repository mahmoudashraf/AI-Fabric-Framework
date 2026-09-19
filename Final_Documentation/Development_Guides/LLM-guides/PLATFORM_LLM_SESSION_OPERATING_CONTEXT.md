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
The current private product source target is AI Fabric `0.7.0` through:

- `io.github.loom-ai-labs:ai-fabric-bom:0.7.0`
- Git tag `ai-fabric-framework-v0.7.0`

The immutable tag, GitHub release, framework CI, signed publication workflow,
and Maven Central artifacts exist and match release commit
`5b075b66384dc5b756b3b3dd12efaf896ce9a50b`. Framework guidance is at
documentation commit `7ac32985`. The release retains the existing security and
execution behavior and adds official immutable `ai.fabric/v1`
`SpecialistChain` YAML/JSON resources, offline validation, a shared
Java/manifest registry and gateway, source-aware runtime status and hashes,
and fail-closed declarative-chain loading.

Source target and hosted-fleet truth are separate. AI Fabric `0.7.0` Gate A is
now complete at private commit
`2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264`: the supported staging and
production runtime families were published and applied through V04, live
readback reports `0.7.0`, and specialist chains remain disabled. Never infer a
future live version from the source POM or relabel the fleet before readback
evidence exists. The canonical Gate A record is
`Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_0_7_0_PLATFORM_MIGRATION/README.md`.

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
Gate A evidence is recorded; Gate B and Gate C remain unstarted and require a
new reviewed decision before implementation.

The private runtime indexing admin facade uses `IndexingWorkQuery` and
`IndexingWorkStatus` for durable per-work Data Sync reconciliation. Keep the
private HTTP route, admin authorization, tenant/deployment checks, polling
policy, and response projection in LoomAI. Aggregate queue diagnostics still
use the framework's internal queue repository contract because `0.7.0` does
not expose a public
queue-summary contract; preserve that diagnostic behavior and raise a
framework contract request before removing or duplicating it.

Before promotion, rebuild from an empty Central-only Maven cache, verify the
packaged execution JAR is `0.7.0`, and repeat the hosted two-tenant,
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
