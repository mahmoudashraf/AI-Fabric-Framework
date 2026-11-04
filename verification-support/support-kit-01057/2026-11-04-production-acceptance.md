# Support Experience Kit: Production Acceptance

Qualification: 3-4 November 2026. Scope: implementation plan 010.57.
Status: native implementation deployed and core journeys qualified; measured
semantic-quality and delivery limitations remain explicit below.

## Delivered Product

- A normal AI Support Experience kit, existing support templates/plugins,
  reusable `support@1.0.0` pack and shared native Desk TicketProvider.
- Contained support page, floating website widget and standalone Engage app.
  The same deployment can support website and standalone installations.
- Form-first and chat-first requests, real provider receipts, My Requests,
  detail/history, activity/seen and explicit resolve/cancel/reopen.
- Canonical AI conversations and empty continuation episodes; no copied
  transcript or synthetic intake replay. Ticket status is not AI/human control.
- Optional human handover and safe assigned-agent profiles; native Desk Tickets
  UI alongside the existing conversation and review views.
- Console consumes that exact pack/provider. Its only private adapter duties
  are existing private runtime identity, owned resource selection and consent.

No AI Fabric framework change or release was made. These runtimes contain
AI Fabric **0.8.45**. No semantic text matching, lexical ranking, automatic
search-all, confirmation bypass or provider-switch fallback was introduced.

## Current Deployment Evidence

- Production Console Support: kit `kit-ca617a82-bfb6-411e-89fe-94ccabf8713e`,
  deployment `dep-79af290c`, installation
  `awi_pub_fc4f82f55cb6b83355cd807f0a21adfa`.
- Matched customer-staging/production configuration `ver-35b051a2`:
  staging release `rel-2f6b9f87`, production release `rel-09c92f90`.
  Both are APPLIED_VERIFIED/PASSED. Production assignment and explicit app
  activation passed. The production Console talks to the production target.
- Developer tuning kit: deployment `dep-a9d6d646`, version `ver-230f5a96`,
  release `rel-c1158395`, verified and active. Its 32k context setting is a
  separately identified candidate, not the production/default configuration.
- Neutral Acme kit: `kit-ab9a999c-80d0-45d0-804a-3cef4932f165`, deployment
  `dep-b9a09c0a`, version `ver-48854447`, release `rel-6f17b45e`, verified.
  Standalone and embedded installations are ACTIVE, with human support OFF.
- All four target releases use registered runtime/connector source
  `83fd17427b28814fb737093571f46615a598e2c0`, immutable image digest
  `sha256:0ad2e34df4d9b5b9f218e96cbaa4e0b5b89196a5ca74c60e0f46ab28e5aaad08`.
  Runtime workflow `41736107377` passed. Artifacts `dsa-02cba709` (staging)
  and `dsa-d3f03019` (production) were attested and registered normally.
- Platform backends: source `021a17472f67751d41b4b8531fa84ab1fdff2afd`,
  healthy after exact staging job `qt0k4oa16z4fzpab5krruhxg` and production job
  `fosu55v9awmk81ub00ynp11j`. Core workflow `41743162532` passed.
- Console frontend: `d990b202b6aa50a770a4b96bd14d07b1202156f5`, healthy in
  both environments. This includes suppression of starter prompts after a
  restored user conversation.
- Desk source `28c8625c34a47584e300896d095085ff92d7bd77` passed workflow
  `41743911089`; staging job `qle4j7hx6i2wss8k0bdeb25v` and production job
  `tdwiwms40bekre5huieum77p` finished. Both native source labels and healthy
  state match. Eight runtime/connector containers across all four targets were
  independently inspected at source83, all healthy.

Native image labels, health and exact Coolify job receipts are private. A
healthy old container was never used as evidence that a new deployment finished.

## Knowledge And Storage

Each new LoomAI target independently indexed the same 13 approved S3 guides
using the original scoped readers. All sources are ACTIVE; fresh retrieval
proof returned five owned evidence items. No vector or ticket transfer occurred.
Only guide 12 was deliberately updated for the new support-request lifecycle;
its original bytes/object identity and conditional upload evidence were retained.
The other twelve guides were unchanged.

Acme independently uses three fictional approved Acme guides and an isolated
vector fixture. It does not use internal Console documents, diagnostics or a
private-host fallback. Original Supabase identity authorizes Desk; a distinct
brokered token authorizes its runtime.

Native Desk stores tickets in its shared multi-customer database. The runtime
owns canonical conversation state. Source files stay in customer storage and
derived evidence stays in the selected vector service. No per-deployment
business-ticket database was added.

## Actual Journey Checks

- Normal customer APIs created fresh LoomAI and Acme kits, rather than
  renaming old deployments or inserting ready states directly in a database.
- Native requester preflight/lifecycle passed in staging and production,
  including a repeat after the final image upgrade. Receipt, opening delivery,
  canonical answer restoration, list/detail, seen/activity, resolve/reopen/cancel
  and chat-first linking without a second intake message were exercised.
- Neutral actual-receipt isolation: own GET200, foreign GET404, foreign list
  excludes it, foreign resolve404 without status/version change, foreign
  canonical-history404, and runtime-token-as-Desk-requester401.
- Human-disabled Acme rejected a real handover with403 and left AI_ACTIVE
  unchanged. It showed no human availability or SLA promise.
- Actual Console staging UI: form/request/answer, lifecycle, assigned-agent
  claim/reply, asynchronous visitor receipt, visitor reply, return to AI,
  repeated handover/close and an empty second episode all passed.
- Actual production UI: an independent customer requester restored its saved
  grounded answer, requested a human, and reached the new production Desk
  binding. Assigned agent claim, agent reply, asynchronous visitor receipt,
  visitor reply and confirmed return to AI passed. No extra AI query was
  required for either human message. Handover
  `handover-e449e37b7995d06a57dae5b5867c1690` ended RETURNED_TO_AI.
- Standalone Acme browser: normal sign-in, own list, restored resolved read-only
  answer, new form submission and grounded approved-document answer passed.
- The actual public installer also mounted contained page and floating widget
  on a registered HTTPS origin. A normal Supabase website identity hook supplied
  the host login, not a Console backend or injected token. Protected My Requests,
  saved history, minimize/reopen and navigation were observed. Temporary static
  test assets were removed afterward.

Live browser evidence is desktop. The browser viewport bridge ignored the
requested phone override and still reported1280x720; its screenshot is labelled
desktop, not passed off as mobile. Local browser fixtures cover responsive
layouts. A fresh live phone qualification is not claimed.

## Governed Actions And Corrections

The original failures remain recorded; a successful later observation does not
erase them.

1. Ordinary REST connector routes omitted their configured idempotency header.
   `a6c6f4c8c` forwards only an existing configured key and retains verified
   trace precedence. 51 focused tests passed, including11 new wire cases.
   No synthetic READ key or framework change was introduced.
2. The Desk registered issuer was incorrectly validated as an outbound URL.
   `028c96e1b` preserves the bounded opaque issuer and exact trace equality.
   Actual HTTP destinations retain HTTPS/network restrictions.
3. Both command facades demanded a mutation key for GET/LIST. Framework READ
   planning correctly supplies no mutation key. `021a17472` and `28c8625c3`
   make the envelope header optional for actual reads while CREATE/LINK retain
   valid-key enforcement before effects. Platform's12 focused security/HTTP
   tests and Desk's47 focused tests passed, zero skips, including PostgreSQL.
4. A production test first supplied unconfigured topic `installation`; the
   space declares `setup` and `service`. The rejected command created no ticket.
   No silent taxonomy remapping was added. A new valid-topic proposal required
   confirmation and returned real receipt `SUP-3503620B-EEC`. Its fresh own READ
   after the Platform correction succeeded with unchanged ticket status/version.
   The original create/confirmation/failed read were not replayed.
5. Neutral confirmed CREATE returned `SUP-EEAED0AC-3F2`, linked the actual owned
   conversation with delivery NOT_REQUIRED and no intake replay. Its original
   READ failure and final corrected READ observation are retained separately.
   The fresh corrected `support_get_own_request` succeeded, matched the actual
   native record and left OPEN/version0 unchanged. Its text explained the record
   accurately but omitted the reference; the actual receipt data contains it.
   This is a successful read contract, not a claim of perfect answer completeness.

One test-helper assertion initially treated pending confirmation's `success=false`
as failure. The actual contract was CONFIRMATION_REQUIRED with no execution.
The assertion was corrected and that same pending proposal continued, without
re-proposing it. No product behavior was changed to satisfy the helper.

## Retirement And Release Interruptions

- Old installations disabled and consumers unbound using revisioned owned APIs.
- Obsolete deployments `dep-a2706601` and `dep-757bc5bc` deleted. Normal deletion
  receipts `del-65cd24a4` and `del-84c79f2e` are SUCCEEDED; independent read-only
  database counts are zero.
- Their six old native runtime/connector/AI-state resources are absent. Owned
  volumes required explicit vendor-admin retirement under the owner's greenfield
  authorization before normal metadata retry. This is not a claim that the
  storage guard was bypassed by self-service deletion.
- `83fd17427` orders owned authority/integration/Desk/lifecycle evidence before
  releases/versions. Fifteen focused tests passed, zero skips, including real
  PostgreSQL, other-deployment preservation and rollback after later failure.
- V227 removed the four obsolete Console case/ticket-operation tables. Source
  search finds no old ticket repositories or writers. Minimal resource/consent
  context remains; historical shared Desk projections are not a second service.
- Approved S3 objects, scoped readers, shared Desk/vector services and unrelated
  products were preserved.
- An earlier promotion apply was interrupted by a backend restart. A fresh
  explicit cold apply and normal verified consumer binding completed the move;
  it was not relabelled as successful interrupted-promotion recovery. Later
  concurrent same-deployment applies were rejected409 and serialized normally.

## Measured AI Quality And Deliberate Limits

- Original baseline: nine turns, including the existing wrong-subject follow-up.
- New staging primary:6 PASS/2 PARTIAL/1 FAIL; document library:9 PASS/7 PARTIAL/
  2 FAIL across18 questions.
- The isolated32k context canary improved three of four unchanged questions but
  still confused document replacement with deployment replacement. It is not
  adopted as a production/global template default.
- New production primary:6 PASS/2 PARTIAL/1 FAIL. Compound follow-up subject
  retention and ticket/handover terminology remain imperfect. Useful direct
  installation/indexing answers and real diagnostic observations are not a
  claim of a universally complete investigation.
- Neutral Acme five-turn primary:4 PASS/1 PARTIAL/0 FAIL; the partial answer
  safely refused an unproven credit but omitted a documented condition.
- Email delivery is not configured/qualified. The shipped UI says Updates in
  My Requests. Specific external helpdesk adapters, SLAs, omnichannel routing,
  autonomous repair and organization-wide requester access remain deferred.
- New offers must select the qualified registered image explicitly. Existing
  immutable bootstrap history is not a permanent upgrade restriction; normal
  compatible-image selection and release/apply were used for this upgrade.

Security/execution mechanics and semantic answer quality are separate results.
No HTTP200, later retry, truncated evidence or synthetic fixture turns a wrong
answer into a pass.

## Guides And Screenshots

- [Owner, installation, identity, agent, storage and outage guide](../../experience-packs/support-experience/docs/INSTALLATION_AND_OPERATIONS.md).
- [Private host contract](../../experience-packs/support-experience/docs/CONSOLE_HOST_ADAPTER.md).
- [Full execution record](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_57_SUPPORT_EXPERIENCE_KIT_EXECUTION_RECORD.md).
- [Embedded page](live-ui-2026-11-03/embedded-page.jpg).
- [Floating widget](live-ui-2026-11-03/embedded-widget.jpg).
- [Floating request list, desktop](live-ui-2026-11-03/embedded-widget-requests-desktop.jpg).
- [Production Desk human reply](live-ui-2026-11-03/production-desk-human-reply.jpg).
- [Production requester after human handover](live-ui-2026-11-03/production-support-after-human.jpg).

Private first responses, operation journals, state snapshots and native rollout
receipts: `~/.codex/private/loomai-support-kit-01057/` and
`~/.codex/private/loomai-support-01054/rollouts/`. They are not published.
