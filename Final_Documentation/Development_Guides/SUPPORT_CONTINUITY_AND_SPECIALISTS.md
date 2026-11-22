# Support Continuity And Specialists

## Ownership And History

Console Support uses the installed Support experience pack and its canonical
runtime conversation. Platform stores the requester/conversation reference,
selection time, selected diagnostic resources, consent and revision. It does not
store a second transcript. Opening Support resumes the last selected ordinary
conversation; New chat explicitly creates an empty scope. History is requester-
scoped, including customer, user, issuer and subject, not shared with every member
of the workspace. Membership is checked again before reads.

Ticket-linked conversations use the ticket's authoritative episode and lifecycle.
Adopting ordinary chat into a ticket removes it from ordinary history/resume.
An old ordinary route cannot bypass closure, cancellation or a newer episode.
Selection/adoption and consented diagnostic reads use the same requester/scope
locks. V244 adds metadata to the existing conversation-scope table only.

## Diagnostic Consent

A ticket is not required to inspect a selected owned resource. Configure
diagnostics in ordinary chat or a request, save the selected resources, then
grant permission. Changing resources revokes permission. Revocation stops later
private reads, not observations already shared in history. Merely visiting a
Brain, typing an ID or attaching an object does not authorize a resource lookup.

All four compound diagnostic tools retain the dedicated service-key authentication,
verified original requester, active membership, ownership, explicit resource
selection and consent. The exact connector routes are registered individually;
there is no wildcard anonymous diagnostics API. A normal customer token cannot
call the internal connector route directly. Child specialists preserve original
authority and cannot invent target IDs. Ticket reads also require the current
open authoritative episode.

- `support_get_case_summary`: current linked request metadata; ordinary chat can
  legitimately have no request.
- `support_get_deployment_status`: recorded states and bounded release records,
  including target, environment, version and verification. Exact document index
  observations require a selected connected app. `integrationConfiguration`
  contains bounded saved-draft capability metadata, not endpoints, credentials,
  raw configuration or business payloads. Neither projection executes business
  tools or proves live health/connectivity. Check setup remains the canonical
  compound connectivity workflow.
- `support_get_engage_readiness`: bounded local configuration checks. Not a
  delivery, provider connectivity or HTTPS probe. `handoverConfiguration` reads
  selected app visibility and its exact assigned verified release, not the draft.
  Agent presence, message delivery and ticket-provider health remain unprobed.
- `support_get_connection_sync_status`: stored synchronization observations, not
  a new upstream source check.

Missing selection/consent is returned as a safe structured requirement with no
resource observations. The model should explain the corresponding UI step, not
present it as a successful resource check. Source authorization, approved document
filters and release-specific runtime authority remain separate checks.

## Specialists And Plans

The internal LoomAI overlay defines Workspace and channels, Brain and release
health, Knowledge and search, Tools and integrations, and Support and human
handover. Its five plans cover workspace/data investigation, knowledge freshness,
API/MCP setup, launch readiness and safe next-step guidance.

The reusable company Support template defines five company-neutral areas and
five corresponding plans. Its default READ-tool lists are empty and contain no
LoomAI diagnostic service bindings. Customers attach their own approved knowledge
and explicitly installed READ tools through the existing Support capabilities
editor. The LoomAI private overlay is an installation configuration, not a new
framework behavior type.
Fresh templates preconfigure the five areas and plans with assistance off until
the required sources/tools are connected. This preserves configuration-only
bootstrap when an owned document-storage binding does not exist yet. LoomAI's
already-connected production installation enables them through its normal draft
and release flow. The two areas of another company's custom installation are not
silently replaced by the new generic defaults.
The reusable template also retains its company-neutral system prompt: internal
Console diagnostic controls and Build with AI navigation are not assumed to
exist in another company's end-user support experience.

Plans use native SpecialistChain resources. The LLM manager chooses relevant
workers, order, sequential/independent parallel checks, clarification and
completion. Limits are six manager decisions, four worker invocations, two
parallel workers and 75 seconds. Each target can run once. Requested parallel
results remain all-required; failures and incomplete findings stay visible.
Simple questions may use direct approved retrieval without a specialist.

Worker writes are disabled. Parent conversation ticket/handover controls retain
their actual authority and confirmation requirements. Draft configuration edits
use canonical Build with AI; there is no second Support draft writer. Publish,
apply, activation and destructive operations remain explicit existing workflows.

V245/V246/V247 create additive catalogue snapshots. V246 corrects the distinction
between runtime extensions and template allowed/default extensions. Two unused
invalid generated snapshots retain their payloads and hashes as rejected audit
records; they cannot bootstrap a deployment. The corrective migration refuses
to reject either if an installation or offer references it. Startup excludes
unreviewed/rejected authoring payloads, while published definitions still require
the actual manifest parser. Explicit draft-template configuration bootstrap keeps
its existing parser and ownership checks; creating a draft is not deployment or
permission to execute its actions. No extra publication gate is added to that
authoring flow. The rejected malformed seed still fails parsing if selected.
V247 preserves the original diagnostic endpoint/credential field contract by
adding the new observations to the existing resource checks. It also makes fresh
template activation dependency-aware; it does not auto-enable unconnected sources.
Existing customer drafts,
immutable published versions, offer pins and app assignments are not silently
repinned. A newly reviewed offer can select the new template revision; the
presence of a catalogue revision is not proof that every existing starter offer
has adopted it. Shared model/provider/security defaults are preserved.

## Source And Verification

Source profiles are under `product-templates/ai-support-workspace/overlays`.
`scripts/build-specialist-defaults.mjs` regenerates diagnostic1.2.0 and the v2
bundled defaults. The original bundle used by applied V245/V246 is frozen; do not
rewrite historical migration inputs or the unqualified six-endpoint1.1.0 candidate.
Node consistency tests
prevent drift; compiler tests verify both profiles within the existing 65536-byte
runtime bundle limit. No framework limit increase follows from this feature.

Compiled bundles above 16384 UTF-8 bytes use a signed, version-specific
`specialist-assistance.json` artifact instead of nested base64 in provider shell
commands. This protects the provider's OS argument-size boundary. Apply requires
the selected runtime image's `signed-specialist-bundle-bootstrap-v1` capability
before provisioning; old images remain usable for smaller inline bundles.
The private runtime entrypoint downloads over HTTPS, enforces the existing
65536-byte limit, verifies the published SHA-256 digest and exports the existing
base64 setting locally. The unchanged framework loader verifies it again.
Download/hash failures stop startup without falling back to stale configuration.
Disabled specialists explicitly clear both transports and their hash. Signed
artifact access retains existing version ownership and signature rules; it is
not an unauthenticated configuration catalogue or a new AI execution engine.

Approved Support guides 10 and 12 describe the customer workflow. Source edits
are not live indexing evidence: upload, approval, preview and indexing must be
observed through the existing owned document workflow before retiring an older
indexed guide. Never delete upstream objects as a substitute for index removal.

Use the dated verification record and `LLM-guides/CODEX_WORKING_CONTEXT.md` for
actual deployed commits, assignments, query quality and incomplete checks. Keep
funding limits real; do not increase a Build with AI allowance merely to make a
regression run green. Retain failed attempts and distinguish model quality,
provider allowance exhaustion and application failures.
