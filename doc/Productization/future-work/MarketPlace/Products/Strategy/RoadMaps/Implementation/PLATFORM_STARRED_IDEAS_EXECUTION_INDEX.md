# Platform Starred Ideas Execution Index

- Created: 2026-10-13.
- Status: detailed plans prepared; new implementation has not started.
- Source: [Platform Notion page](https://app.notion.com/p/332f30af56ae802a8cccf903b71916bd).
- Latest preceding plan: `010.34`, completed Conversation Handover.
- Code baseline: private source `db58acd9958b717666de712f44ab2882cfc99b8f`; public framework source `561659dc0a4e8872f83654ef8549ead3cc4acc32`.
- Current source dependency: AI Fabric `0.8.32`.

## Purpose

Turn the seven starred ideas into independent, executable plans using the existing Platform primitives. This sequence is a product-design decision, not a pricing, sales, or market-fit exercise. New delivery forms and optional capabilities remain parts of the existing framework, AI Workspace, and Platform product layers.

The source snapshot and completed release records were checked while preparing the plans. Existing code is identified separately from proposed contracts. Source support does not establish a new hosted capability as ready.

Code-path shorthand in the plans: `Platfrom/backend/.../` denotes
`Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/`;
`ai-infrastructure-module/ai-fabric-runtime/.../` denotes that module's
`src/main/java/com/ai/fabric/runtime/` tree. A subsequent `.../` in an evidence
list retains the relevant module/package prefix. Public module paths are
relative to the public framework's `ai-infrastructure-module/` directory.

## Execution Order

| Priority | Canonical plan | Product boundary | Verdict |
| --- | --- | --- | --- |
| 1 | [010.35 AI Fabric Coding Assistant Skill](010_35_AI_FABRIC_CODING_ASSISTANT_SKILL_IMPLEMENTATION_PLAN.md) | Public framework developer guidance | Implement a focused, maintained skill. |
| 2 | [010.36 LoomAI Engage](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md) | AI Workspace technical delivery form and Platform installation | Reuse the workspace; add a generic pack and standalone lifecycle. |
| 3 | [010.37 Platform Deployment Configuration Assistant](010_37_PLATFORM_DEPLOYMENT_CONFIGURATION_ASSISTANT_IMPLEMENTATION_PLAN.md) | Assisted control-plane authoring | Generate reviewable, validated drafts through scoped tools. |
| 4 | [010.38 Workspace Startup Context](010_38_DEPLOYMENT_OWNED_WORKSPACE_STARTUP_CONTEXT_IMPLEMENTATION_PLAN.md) | Deployment-owned read capability and workspace presentation | Add explicit, fresh initialization evidence. |
| 5 | [010.39 Outbound Event Integrations](010_39_DEPLOYMENT_LOCAL_OUTBOUND_EVENT_INTEGRATIONS_IMPLEMENTATION_PLAN.md) | Integration plugins and deployment-local delivery | Extend existing delivery foundations with a notification contract. |
| 6 | [010.40 Small Persistent Lucene Deployment Profile](010_40_SMALL_PERSISTENT_LUCENE_DEPLOYMENT_PROFILE_IMPLEMENTATION_PLAN.md) | Managed deployment efficiency profile | Measure and productize the existing embedded storage path. |
| 7 | [010.41 Customer Hosted Deployment Bundle](010_41_CUSTOMER_HOSTED_DEPLOYMENT_BUNDLE_IMPLEMENTATION_PLAN.md) | Portable customer deployment delivery | Package immutable, runnable deployment services. |

Run the `010.40` measurement phase in parallel with `010.35` and `010.36`. The sixth position applies to production profile adoption, not to waiting before collecting measurements.

The order is a recommended allocation of work. It is not a dependency chain: the skill is not required to run a standalone workspace; the configuration assistant is not required to implement startup context or integrations.

## Completed Human In The Loop Baseline

Human Review and Conversation Handover are implemented. `010.34` explicitly records H0-H7 as complete and hosted-verified. Its closure includes Basic LoomAI Inbox, participant messages, durable control, owner-bound routes, SSE replay, channel dispatch, recovery, and acknowledged return to AI.

Reuse:

- `max-mode-widget/src/api/handover.ts` and `src/hooks/useConversationHandover.ts`;
- `max-mode-widget/src/installer/types.ts` handover routes;
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/handover/`;
- `product-services/basic-loomai-inbox-service/`;
- the existing `INTEGRATION` / `conversationChannel` contribution and compiler;
- the independent action `HUMAN_REVIEW` extension.

These plans do not rebuild those capabilities. Relevant plans repeat regression checks only when they change workspace lifecycle, plugin compilation, persistence, authentication, or packaging. Conversation Handover remains scoped to `CONVERSATIONAL`; these plans do not authorize a Smart Brain or specialist-team handover extension.

## Source Findings That Change The Plans

1. `AIWorkspaceExperiencePackRegistry` currently resolves only `dealership@1.1.0`; `AIWorkspaceConfigurationValidator` validates dealership fields. Generic standalone hosting therefore needs an actual generic pack/schema/catalog entry.
2. The installer already models anonymous direct runtime, authenticated broker, and private backend adapter connections. Broker support is conditional on a reviewed endpoint being configured; schema support alone is not authenticated-product readiness.
3. Draft update accepts structured sections but has no caller-supplied edit fingerprint in `UpdateDeploymentDraftRequest`. AI-assisted edits need an atomic stale-draft guard, not a new use of `revisionNumber` as if every save increments it.
4. `SmartBrainDeliveryService` already persists delivery records, sends signed CloudEvents, includes idempotency keys, and retries to `DEAD_LETTER`. A general notification plugin is missing; outbound transport is not wholly new work.
5. Runtime defaults to file-backed Lucene and H2. Coolify reconciles persistent `/app/data` storage for Lucene. Production capacity and a small profile still need measurements and restart/recovery proof.
6. Existing exports contain configuration and optional sealed secrets; artifact delivery uses versioned compiled files and signed URLs. A runnable on-prem bundle needs locally materialized artifacts, selected service images, storage, identity, installation assets, and update procedures.

Standalone authentication refinement: `010.36` now includes public,
login-required, and mixed public/protected policies, with a generic reviewed
provider/broker integration and server-side enforcement. Successful login
starts a new authenticated conversation. Anonymous history, attachments,
pending actions, and handover are not transferred; the user requests the
protected operation again after sign-in.

Standalone delivery decisions confirmed, 2026-10-13: the user approved all
twelve recommendations in `010.36`. First release uses one shared frontend
service on production Coolify, separate app installations/domains and assigned
customer runtimes, default `<app-name>.apps.loomai.pro` addresses, optional
verified customer domains, and **Create AI App** through existing workspace/
template/assignment primitives. Public templates default to mixed interaction;
internal templates require login. Prove customer-owned Supabase Auth with a
deployment-local adapter first; OIDC follows, while existing customer-session
and Shopify paths remain supported. Frontend, installation, and runtime
releases/readiness are separate. There is no unmeasured 100-app ceiling.
These are confirmed design choices; all implementation phase tasks remain
pending, and no new hosted capability is claimed.

Product naming confirmed, 2026-10-13: **LoomAI Engage** is the commercial
name; **AI Workspace** remains the technical reference. Existing filenames,
routes, contracts, and **Create AI App** terminology remain unchanged.
`010.36` also records a code-reviewed mobile portability recommendation:
web-first, optional installable PWA, then Capacitor using the existing web UI.
Native bootstrap/auth, secure storage, lifecycle, and distribution need
explicit later evidence; zero-change conversion and automatic store approval
are not promised. Mobile-ready host boundaries are first-build constraints,
not a requirement to implement a native app before the web release.

Future mobile direction confirmed, 2026-10-14: `010.36` now selects a single
LoomAI Engage host with a common AI Workspace renderer, approved bundled
generic/domain components, remote business configuration, business selection,
and verified links. Web implementation must preserve this target through
reusable UI/configuration contracts and isolated host/state boundaries.
Native catalogue/search, platform integration, packaging, links, and store
publication remain deferred execution work. Dedicated business binaries are
a later option; hosted-page WebViews are not the primary selected path.
No mobile build or store proof is required to close the current web release.

## Shared Implementation Rules

- Latest-only consumption; retain immutable historical evidence without adding simultaneous supported runtime/framework versions.
- Templates, ACTION/DATA/INTEGRATION contributions, curated modules, profiles, installations, assignment, and existing release workflows are the composition model.
- Natural-language decisions use validated structured model output. Deterministic schema parsing, exact identifiers, authorization, integrity checks, and state machines remain allowed.
- No semantic keyword matching, hidden generated prompts posing as user requests, fake success, or silent model-decision recovery. Named configured recovery must disclose the original and effective decision.
- Configuration validation, publication, assignment, and operational truth remain Platform-owned. Customer chat, indexing, effects, and event traffic stay deployment-local.
- Source documents remain customer-owned. Derived index and operational state persistence are different from offering managed customer object storage.
- A public framework release is required only when an evidenced reusable contract gap belongs there. Product HTTP routes, adapters, inboxes, UI, and deployment packaging remain private.
- Critical isolation, authorization, effect, idempotency, and persistence checks must pass. Record unrelated broader-suite warnings without declaring the aggregate gate green.

## Tracking And Completion

Each plan includes numbered implementation phases, file targets, proposed contracts, verification, and exit criteria. Keep phase tasks unchecked until their actual deliverables are implemented and verified. A code review completed during planning is not an implementation phase passed.

When implementing, record source commit, framework artifact version when changed, release/apply identity, named checks, evidence files, live results, and remaining limitations in the relevant plan. Append concise operational facts to `CODEX_WORKING_CONTEXT.md`; append product-boundary changes to `Codex_Strategic_Context.md`.

No product code, framework dependencies, live configuration, or completed `010.34` status is changed by creating these plans.

Planning validation passed for the seven numbered documents, pending phase
checklists, local cross-links, source evidence paths, and Markdown code fences.
Implementation tests and live checks remain the work of the individual plans.
