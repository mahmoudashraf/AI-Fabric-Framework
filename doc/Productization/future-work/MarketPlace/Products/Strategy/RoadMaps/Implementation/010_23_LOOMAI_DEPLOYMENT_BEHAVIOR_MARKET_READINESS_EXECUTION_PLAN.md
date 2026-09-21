# 010.23 LoomAI Deployment Behavior Market Readiness Execution Plan

- **Status:** Execution in progress; staging immutable control-plane canary complete, production capacity decision required
- **Date:** 2026-09-20
- **Active AI Fabric baseline:** `0.7.1`
- **Depends on:** `010.21` architecture and `010.22` productization implementation
- **Current maturity:** exact hosted canaries are `HOSTED_PROVEN`; reusable releases are not yet `MARKET_READY`

## 1. Purpose

This plan turns the hosted-proven LoomAI deployment behaviors into repeatable,
supportable ready-deployment templates that Platform customers can select and
operate without LoomAI engineering intervention.

The launch units are Platform behaviors, not LoomAI-owned customer-domain
products:

1. `CONVERSATIONAL`
2. `AGENTIC_SPECIALIST_TEAM`
3. `SMART_BRAIN`
4. Human Review as an optional compatible execution extension

Market readiness applies to an exact immutable template and composition
version. It never applies automatically to every possible deployment using the
same behavior type.

## 2. Executive Decision

The existing source and hosted canaries prove that the three behavior contracts
can run. The next work is not another behavior abstraction or deployment
lifecycle. It is:

1. publish exact reusable Marketplace templates through the existing V04 path;
2. prove clean-room creation with no manual environment repair;
3. complete lifecycle, failure, recovery, isolation, and security matrices;
4. add production operations, limits, support, and customer documentation;
5. launch exact compositions through controlled cohorts;
6. mark each composition `MARKET_READY` independently only after its evidence
   is complete.

The recommended launch order is:

```text
Generic Conversational template
  -> Conversational Human Review extension
  -> Bounded Agentic Specialist Team template
  -> Event-Driven Smart Brain template
  -> broader capability packs and partner self-service
```

Work may run in parallel after the common template/lifecycle foundation exists,
but no later behavior may bypass its own gates.

## 3. Non-Negotiable Boundaries

### 3.1 One control plane

- Reuse Marketplace publish/install, deployment draft, V04 version, apply,
  release, assignment, verification, export/import, promotion, rollback,
  draining, and decommission.
- Do not create a second product profile, workflow, release, or deployment
  aggregate.
- Released deployments must originate from published exact template versions,
  not `custom-start-from-scratch` or production-only manual configuration.

### 3.2 Deterministic Platform authority

- AI may operate only inside a reviewed runtime contract.
- Code and immutable configuration decide behavior type, specialists, tools,
  triggers, review policy, callbacks, quotas, and authority.
- Models may not invent topology, reviewer identity, target authority, callback
  URLs, plugins, or write permissions.

### 3.3 Customer application authority

- The customer application remains authoritative for identity, authorization,
  validation, transactions, and system-of-record writes.
- Entitlements constrain what may be provisioned; they do not grant runtime
  action authority.
- Agentic workers remain read-only in the first release.
- Smart Brain remains read-only and cannot call Resolver automatically.
- A write may occur only through a separately configured governed action and
  Human Review/Resolver boundary with current authorization revalidation.

### 3.4 Deployment-local data plane

- Chat, Agentic, Smart Brain, review, knowledge, and status traffic goes to the
  assigned deployment-local endpoint.
- The central Platform manages lifecycle and assignment but is not the runtime
  traffic proxy.
- Smart Brain first release uses CloudEvents `1.0` structured HTTPS ingress,
  direct polling, and optional deployment-owned signed webhook delivery.
- Broker/adaptor products are outside the first market-ready release unless a
  separate bounded contribution contract is approved.

### 3.5 No fake readiness

- No dummy, stub, hardcoded, text-matched, UI-only, or silent fallback path may
  satisfy an acceptance gate.
- No skipped normal tests or local-only framework artifacts may be used.
- A failed aggregate gate remains failed even when an owner-approved exception
  permits an unrelated exact template to progress.

## 4. Current Baseline

| Capability | Current proof | Current maturity | Primary gap |
| --- | --- | --- | --- |
| Conversational | Staging `dep-5c249fac`; production `dep-18e74807`; normal turn passed | `HOSTED_PROVEN` exact canary | Released reusable template and complete lifecycle/regression proof |
| Agentic Specialist Team | Staging `dep-17befd6c`; production `dep-f56d32a9`; durable two-worker chain passed | `HOSTED_PROVEN` exact canary | Released specialist/template origin and full failure/recovery matrix |
| Smart Brain | Staging mechanics `dep-10c99bd4`; production `dep-e8a29c2b`; typed durable operation passed | `HOSTED_PROVEN` exact canary | Released template and full event/delivery/lifecycle matrix |
| Human Review | Backend/runtime/UI source implemented and locally verified | `SOURCE_COMPLETE` | Hosted governed-action proof and full authorization/recovery matrix |
| Common Platform | Runtime `174/174`, backend `757/757`, PostgreSQL `V1..V133`, UI build | Source and migration proven | Clean-room reusable release and operational/commercial completion |

The production behavior runtimes are live evidence fixtures. They are not a
substitute for newly created template-backed deployments.

## 5. Market-Ready Definition

An exact template version may be marked `MARKET_READY` only when all of these
are true:

1. **Published origin:** a reviewed immutable Marketplace template and exact
   dependency versions are published.
2. **Deterministic composition:** installation compiles into V04 with stable
   composition, capability, prompt, schema, specialist, and source hashes.
3. **Clean-room deployment:** a new tenant can create and release it with no
   hidden local state or manual environment repair.
4. **Runtime parity:** live capability readback exactly matches the source
   artifact and released composition.
5. **Behavior proof:** the complete behavior-specific matrix passes against a
   real provider and real durable storage where claimed.
6. **Security proof:** two-tenant/two-deployment isolation and missing-boundary
   fail-closed tests pass.
7. **Lifecycle proof:** export/import, promotion, rollback, restart, recovery,
   draining, and decommission pass.
8. **Operational proof:** health, metrics, safe traces, alerts, backup,
   recovery, retention, deletion, and offboarding are documented and tested.
9. **Cost control:** quotas, concurrency, model/vector usage, storage,
   schedules, and callback limits are enforced and metered.
10. **Customer UX:** selection, prerequisites, endpoint integration,
    operations, failures, and recovery are understandable without raw JSON.
11. **Support readiness:** severity, escalation, incident, rollback, and data
    recovery runbooks have named owners.
12. **Commercial readiness:** entitlement, packaging, billing, privacy,
    support, documentation, and claims match the exact verified composition.
13. **Controlled production:** at least one approved design-partner deployment
    passes the same release gate under realistic use.

One successful canary, a green health endpoint, or a visually complete
catalogue page cannot independently satisfy this definition.

## 6. Initial Ready-Deployment Templates

### 6.1 Generic Grounded Conversational Runtime

Behavior: `CONVERSATIONAL`

Initial included capabilities:

- authenticated chat and query-once;
- bounded multi-turn session behavior;
- grounded retrieval and citations when selected;
- grounding-eligible read actions with post-action generation;
- structured result contracts;
- docked composer, Max Mode, and inline UI bindings;
- optional compatible Human Review extension.

Initial exclusions:

- arbitrary autonomous workflows;
- unreviewed write actions;
- customer-specific Shopify or ProdUS logic in the generic template.

Launch target: first general-availability candidate.

### 6.2 Bounded Specialist Team Runtime

Behavior: `AGENTIC_SPECIALIST_TEAM`

Initial included capabilities:

- one reviewed manager;
- at least two genuinely distinct read-only specialists;
- official declarative `SpecialistChain` resources;
- durable PostgreSQL execution;
- submit, status, result, cancel, and replay;
- safe worker attribution and lineage;
- Platform operations UI.

Initial exclusions:

- model-created workers or topology;
- dynamic arbitrary tool installation;
- write-capable workers;
- hidden conversational fallback.

Launch target: controlled beta, then general availability after the full
recovery matrix and design-partner proof.

### 6.3 Event-Driven Smart Brain Runtime

Behavior: `SMART_BRAIN`

Initial included capabilities:

- deployment-local CloudEvents ingress;
- reviewed trigger registry and schemas;
- durable typed operations and results;
- idempotency, replay, cancellation, and status polling;
- deployment-local Quartz/PostgreSQL schedules;
- signed allowlisted webhook delivery with retry/dead-letter handling;
- Platform operations UI.

Initial exclusions:

- browser-held service credentials;
- event-selected callback destinations;
- central Platform traffic proxying;
- direct domain writes or automatic Resolver execution;
- broker-specific adapters in the first release.

Launch target: private preview, then controlled beta after delivery, outage,
draining, and recovery proof.

### 6.4 Governed Human Review Extension

This is not a fourth behavior type. It is a separately selectable extension.

The first released contract accepts only exact `ACTION_PROPOSAL` sources and
supports:

- safe inbox and detail projections;
- customer-owned reviewer authorization and separation of duty;
- approve, reject, and expiry;
- current policy/action/preflight revalidation;
- at-most-one known domain outcome through Resolver/action coordination;
- durable dispatch, decision, receipt, replay, and recovery.

Arbitrary answers, Agentic results, or Smart Brain results may not be coerced
into action review. A separate non-action result-review contract is required
before Smart Brain review can be marketed.

## 7. Workstreams

### MR0: Freeze release truth and evidence model

**Status:** `READY`

Tasks:

- add a readiness record keyed by behavior, template version, plugin versions,
  V04 composition hash, source artifact, image digest, framework version, and
  verification pack version;
- define the allowed maturity transitions:
  `FRAMEWORK_AVAILABLE -> RUNTIME_PACKAGED -> PLATFORM_SELECTABLE -> HOSTED_PROVEN -> MARKET_READY`;
- prevent UI/API claims from exceeding the stored maturity;
- define evidence expiration and revalidation triggers;
- keep owner-approved exceptions explicit and scoped.

Exit:

- every customer-visible readiness claim resolves to immutable evidence;
- changing any material hash invalidates the old readiness decision.

### MR1: Publish governed reusable Marketplace templates

**Status:** `BLOCKING_COMMON_PATH`

Tasks:

- publish exact versions for the three initial templates;
- publish reviewed specialist bundles through the governed `SPECIALIST`
  contribution path without executable marketplace code;
- freeze required and optional plugin versions and compatibility rules;
- include exact prompts, schemas, chains, trigger definitions, endpoint classes,
  resource requirements, secret names, and verification packs;
- prove deterministic install-to-draft and draft-to-V04 compilation;
- reject missing, mutable, incompatible, or unattested contributions;
- expose only curated/private `SPECIALIST` contributions until the public
  contribution contract and operating context are formally updated.

Exit:

- each template compiles to the same hashes from the same inputs;
- no release requires `custom-start-from-scratch` or direct production edits.

### MR2: Clean-room provisioning and release

**Status:** `PENDING_MR1`

Tasks:

- use a new staging tenant and no reused canary deployment records;
- install each template through the supported UI/API;
- validate prerequisites before publish;
- provision exact PostgreSQL, vector/provider, secret, callback, and runtime
  resources required by the selected composition;
- publish, apply, release, and poll to terminal state;
- verify assignment and every advertised deployment-local endpoint;
- compare source capability manifest, V04 composition, runtime readback, image,
  migration set, and framework version;
- prove creation through both operator UI and public supported API paths.

Exit:

- a clean tenant reaches a healthy verified deployment without shell repair,
  database patching, or manual environment injection.

### MR3: Full lifecycle certification

**Status:** `PENDING_MR2`

For every exact template:

1. export a sealed configuration bundle;
2. import to a different target scope;
3. release the imported deployment;
4. verify no source-owned identity, secret, or target authority leaked;
5. promote staging composition to production through the supported flow;
6. exercise immutable update and rollback;
7. restart runtime and attached durable services;
8. rotate supported secrets without corrupting in-flight work;
9. drain old work while routing new work to the promoted release;
10. decommission and verify resource/data handling;
11. restore from the supported configuration and data backup boundary.

Exit:

- all lifecycle operations have terminal evidence and actionable failure states;
- rollback does not require reconstructing undocumented manual configuration.

### MR4: Behavior certification

**Status:** `PENDING_MR2`

Conversational minimum:

- new query, continuation, reset, expiry, and owner isolation;
- clarification, no-evidence, known-answer RAG, and citation correctness;
- read-action grounding and final answer generation;
- structured result validation and UI rendering;
- real provider failure, vector failure, MCP failure, and safe output handling;
- Shopify and ProdUS non-regression packs without weakening expectations.

Agentic minimum:

- manager-only, one-worker, sequential two-worker, and approved parallel paths;
- exact supporting-result attribution;
- target/specialist invention denial and independent worker authorization;
- provider, worker, projector, deadline, and required-branch failure;
- cancellation, exact replay, changed-payload conflict, and definition drift;
- restart, lease recovery, changed-authorization resume denial, and safe lineage;
- cross-owner, tenant, deployment, scope, evidence, and target isolation.

Smart Brain minimum:

- valid/invalid CloudEvents, unknown triggers, payload/schema ceilings;
- service identity and tenant/deployment isolation;
- exact idempotent replay and changed-facts conflict;
- durable restart, cancellation, status/result authorization, and retention;
- schedule activation through the same operation contract;
- signed callback delivery, retry, exhaustion, dead letter, and operator replay;
- Platform outage after cached assignment resolution;
- promotion draining and decommission;
- proof that no event or schedule can perform a domain write.

Exit:

- each released template passes its complete deterministic and real-service
  verification pack in staging and controlled production.

### MR5: Hosted Human Review certification

**Status:** `SOURCE_COMPLETE_HOSTED_PENDING`

Tasks:

- select one low-risk Conversational governed action;
- release exact review policy, scopes, separation-of-duty rule, TTL, dispatcher,
  migrations, secrets, and verification pack through V04;
- prove safe inbox/detail and unauthorized non-disclosure;
- prove approve, reject, expiry, replay, conflict, and restart behavior;
- revalidate current reviewer and domain authority at decision time;
- prove at-most-one known action outcome and visible `OUTCOME_UNKNOWN` handling;
- prove dispatch retry/exhaustion, secret rotation, retention, rollback, and
  draining;
- verify managed and customer-owned review UIs use the same deployment-local
  contract.

Exit:

- one template-backed deployment has hosted-proven review without widening
  customer authority or exposing protected action/receipt state.

### MR6: Security, reliability, and operations

**Status:** `PENDING_TEMPLATE_RELEASE`

Tasks:

- run two-tenant and two-deployment isolation for every data-plane API;
- run missing principal, tenant, deployment, scope, capability, secret, target,
  specialist, trigger, vector-space, and callback fail-closed tests;
- complete dependency/container and secret-exposure scans;
- define approved availability, latency, queue age, delivery, recovery, and
  data-loss objectives before launch;
- add metrics, alerts, safe traces, dashboards, and runbooks for each objective;
- test provider, database, vector, webhook, network, and Platform outages;
- define backup ownership and run restore rehearsals;
- enforce retention, deletion, regional, privacy, and offboarding policy;
- define incident severity, escalation, rollback, and customer communication.

Exit:

- operations can detect, explain, contain, recover, and communicate every
  supported failure without inspecting private raw payloads.

### MR7: Entitlement, quota, billing, support, and documentation

**Status:** `NOT_STARTED`

Tasks:

- define which templates and extensions are available per offering;
- enforce model, embedding, vector, storage, specialist, concurrency, schedule,
  event, operation, callback, and retention ceilings;
- meter usage and cost by customer/deployment/template version;
- alert before budget exhaustion and fail predictably at hard limits;
- publish prerequisites, API integration, UI integration, security, privacy,
  operations, troubleshooting, limits, and offboarding guides;
- define support scope, response targets, exclusions, and escalation;
- create claims only from exact verification evidence;
- give partners guided template choices without exposing raw framework modules.

Exit:

- what the customer buys, operates, is billed for, and receives support for is
  the same exact composition that was verified.

### MR8: Controlled launch and maturity promotion

**Status:** `NOT_STARTED`

Tasks:

- select one design partner per behavior before broad launch;
- provision through the released template path;
- capture baseline, load, cost, failure, recovery, usability, and support data;
- fix defects through immutable versions and repeat affected gates;
- hold an explicit go/no-go review with engineering, operations, security,
  privacy, support, and commercial owners;
- promote only the approved exact version to `MARKET_READY`;
- retain older and experimental compositions at their honest maturity.

Exit:

- the approved version has controlled-production evidence and named operational
  and commercial owners;
- the public catalogue cannot select an unapproved composition as market-ready.

## 8. Release Evidence Bundle

Every market-ready candidate must produce one evidence bundle containing:

- behavior contract and template/version IDs;
- exact Marketplace contribution versions and hashes;
- V04 composition/provenance hash;
- private source commit and immutable image digest;
- AI Fabric version and Central-only dependency proof;
- runtime capability readback and migration inventory;
- staging and production deployment/release IDs;
- deterministic, real-provider, isolation, security, lifecycle, and UI results;
- export/import, promotion, rollback, restart, restore, draining, and
  decommission evidence;
- load/cost observations and approved ceilings;
- SLO, alert, runbook, retention, privacy, support, and offboarding approvals;
- design-partner sign-off and final market claim approval;
- all open exceptions, owners, expiry dates, and affected claims.

Evidence must be machine-readable where the Platform makes an automated
readiness decision and human-readable where an operator must approve risk.

## 9. Environment Progression

### 9.1 Local

- targeted tests, full affected reactors, UI build, migration test, container
  boot, and clean diff;
- Maven Central-only framework resolution;
- no skipped normal tests.

### 9.2 Clean staging

- new tenant and template-backed deployment;
- real provider/storage where claimed;
- full behavior, isolation, lifecycle, and failure verification;
- configuration export before promotion.

### 9.3 Controlled production

- immutable promotion or supported target-scoped recreation;
- direct endpoint, assignment, runtime identity, and capability verification;
- production canary and design-partner workload;
- rollback and incident readiness retained throughout observation.

### 9.4 General availability

- only after MR0 through MR8 applicable gates pass;
- enable published catalogue visibility and entitlement;
- monitor the exact released version and freeze claims to verified behavior.

## 10. Go/No-Go Rules

### Go

- every mandatory gate for the exact template is green;
- no critical/high unresolved security, isolation, durability, or authority
  defect exists;
- operations and support accept the runbooks and objectives;
- commercial claims match evidence;
- rollback and decommission remain available.

### No-Go

- template still depends on manual production configuration;
- runtime readback differs from released composition;
- tenant/deployment isolation is unproven;
- durable work cannot recover or reconcile;
- hidden fallback changes semantics;
- a required behavior-specific test is skipped or waived without removing the
  affected market claim;
- a canary is being used as evidence for a materially different composition;
- customer or model input can widen authority.

The existing protected Shopify exception remains visible in aggregate release
truth. It must not be relabeled green. It does not automatically approve or
reject an unrelated exact behavior template; the affected template's own
regression scope and claims determine whether it is blocking.

## 11. Rollback And Withdrawal

For every released template version:

1. stop new assignments or new work while preserving authorized reads;
2. drain or cancel work according to the behavior contract;
3. preserve durable state and safe evidence;
4. roll back through an immutable known-good V04 release;
5. verify runtime identity, assignment, data integrity, and behavior;
6. reconcile callbacks/actions before declaring recovery;
7. withdraw the affected template version from new selection;
8. notify affected customers according to the incident plan;
9. release a new immutable version after the failed gates are rerun.

Do not overwrite tags, mutate published template versions, or repair only the
production environment.

## 12. Execution Waves

### Wave 1: Common path and Conversational GA candidate

1. MR0 evidence/maturity contract.
2. MR1 generic Conversational template publication.
3. MR2 clean staging creation.
4. MR3 complete lifecycle certification.
5. Conversational portion of MR4.
6. MR6 operations/security baseline.
7. MR7 packaging and documentation.
8. MR8 controlled production and GA decision.

### Wave 2: Conversational Human Review and Agentic beta

1. MR5 low-risk governed-action review.
2. MR1 Agentic template and curated specialist bundle.
3. MR2/MR3 Agentic clean-room and lifecycle proof.
4. Agentic portion of MR4.
5. MR6/MR7 Agentic operations, limits, and support.
6. MR8 design-partner beta and GA decision.

### Wave 3: Smart Brain private preview and GA candidate

1. MR1 Smart Brain template.
2. MR2 clean deployment with direct event endpoint.
3. MR3 lifecycle and draining proof.
4. Smart Brain portion of MR4.
5. MR6 delivery/outage/retention operations.
6. MR7 event, schedule, callback, and storage quotas.
7. MR8 private preview, controlled beta, and GA decision.

## 13. Immediate Next Implementation Batch

The next coding/operational batch should be limited to the common bottleneck and
the first launch candidate:

1. freeze the market-readiness evidence schema and maturity transition guard;
2. assign immutable IDs/versions to the generic Conversational template and
   its required contributions;
3. remove any remaining `custom-start-from-scratch` assumption from its
   released path;
4. add deterministic template-install/V04 compilation tests;
5. expose prerequisites and compatibility in the existing deployment UI;
6. create a new staging tenant and deploy only from the published template;
7. run Conversational behavior, isolation, provider-failure, and UI packs;
8. run export/import, promotion, rollback, restart, draining, and decommission;
9. produce the first complete evidence bundle;
10. review the result before starting Agentic and Smart Brain reusable releases.

This batch must not modify ProdUS or Shopify assignments merely to manufacture
generic template evidence.

## 14. Owner Decisions And Inputs

Engineering can complete MR0 through the staging parts of MR6 without pricing
decisions. Before controlled production or public claims, the owner must approve:

- customer-facing names and initial catalogue visibility;
- design partner for each behavior;
- availability/support objectives and escalation ownership;
- included quotas and commercial tiers;
- provider/vector cost policy;
- privacy, retention, deletion, and regional posture;
- whether each release is private preview, beta, or general availability;
- the exact claims permitted on the public site and Partner Portal.

Lack of a commercial decision must block public launch, not technical staging
verification.

## 15. Definition Of Done

This plan is complete when:

1. each of the three behavior types has at least one published reusable
   Marketplace template deployed through V04 from a clean tenant;
2. each exact template independently passes its full behavior, security,
   lifecycle, recovery, operations, cost, support, and controlled-production
   gates;
3. Human Review has one hosted-proven low-risk governed-action composition;
4. customer and partner UIs expose only real prerequisites, endpoints,
   operations, limits, and supported choices;
5. every market claim points to a current immutable evidence bundle;
6. only approved exact versions are marked `MARKET_READY`;
7. the customer application remains the final authority for identity,
   authorization, validation, transactions, and system-of-record writes.

## 16. 2026-09-21 Execution Checkpoint

### Completed evidence

- Production exact-template Agentic certification passed for
  `mkp-template-agentic-specialist-team@1.0.4`: deployment `dep-650df1e0`,
  version `ver-959204de`, release `rel-d2e65013`, runtime
  `dprh-475f26b9`, database `dprh-0e98054a`, suite `vsr-51202473`, and
  stage `vss-21cd0164`. Candidate `brr-76153ef2-076` remains
  `HOSTED_PROVEN`, not `MARKET_READY`.
- Governed Coolify target placement is implemented in `0a2f0b6e3`; legacy
  Coolify destination discovery is fixed in `567282757`. The complete
  Platform backend suite passed `788` tests, including PostgreSQL migrations.
- Staging source-backed Platform backend and UI reached terminal Coolify
  success on `567282757`. Live `dtp-coolify-staging` preflight is `PASSED`
  through the version-tolerant `RESOURCE_INVENTORY` destination proof.
- Immutable Platform core images are published by
  `.github/workflows/publish-platform-core-images.yml`. Exact source
  `154f6552d98fb6b32f2ed3874534da65dedc18df` produced backend digest
  `sha256:243cad95815e1a2dd9a3b44eae174c0b8936616d53bcf67fa6c186ba56bf4f66`
  and UI digest
  `sha256:64453037efe7c11a276c6fe16c8a15bc082ee8210fe46ded989027b3cba4d9bc`.
- The staging image canaries are application `awocqyg4kxeyw3ol2nfjhmrp`
  (backend, deployment `l3kne30vhiorqe72muyahs7o`) and application
  `u67gepsowf7oo90dwjdx018r` (UI, deployment
  `r9o1xrgog5lwdhpz28vxao1u`). Both deployments finished; both resources are
  healthy on the exact source tag. Backend liveness, readiness, aggregate
  health, authenticated target preflight, UI health, runtime API config, and
  placement UI contract passed.
- The canary found and fixed a real release-image defect: Coolify health
  checks require `curl` or `wget` inside Dockerfile/image deployments. Both
  Platform UI runtime Dockerfiles now include `curl`; the published GHCR image
  was independently pulled and inspected before the successful hosted retry.

### Production boundary

- Production image applications were created with exact copied environment
  values and remain non-canonical: backend `sbdv2pkkqrbsy9m59034hb5j` and UI
  `a6fz2ncuzzulg6lrzzwrjshu`. Environment comparison passed `84/84` for the
  backend and `4/4` for the UI without logging secret values.
- The production UI canary has not been started. The backend canary was
  stopped after two guarded attempts. Deployment `jmds7ookbdrihfpifsitjjlb`
  was cancelled; deployment `yyfsh0q2jp4r6qs7ztyizj8r` was safety-stopped.
  Both deployment records are terminal `cancelled-by-user`.
- The current four-core production host cannot keep the canonical Platform
  backend responsive while a second backend JVM starts. Pinning the canary to
  isolated CPU `3` did not remove the failure: canonical liveness returned
  `502` twice, the guard stopped the canary, and all canonical probes then
  recovered to HTTP `200` / `UP`.
- Do not retry blue/green backend startup on the same host. Production rollout
  now requires an owner-approved choice between a short maintenance cutover
  with the old backend stopped first, or additional/expanded production
  capacity. Creating or resizing paid Hetzner capacity remains an explicit
  approval boundary.
- Production placement code and the new Platform UI are therefore not yet
  canonical live evidence. Do not infer deployment from the configured Git
  pin or from the successful staging image canaries.

### Next execution order

1. Obtain the production capacity or maintenance-window decision.
2. Finish the immutable production backend/UI cutover with rollback domains
   and exact Coolify terminal evidence.
3. Update Platform core-service ownership to the final image application UUIDs.
4. Provision the isolated production behavior worker only after explicit paid
   infrastructure approval.
5. Run the Smart Brain exact-template proof, remaining lifecycle matrix, and
   full release gates without weakening the deferred Shopify truth.
