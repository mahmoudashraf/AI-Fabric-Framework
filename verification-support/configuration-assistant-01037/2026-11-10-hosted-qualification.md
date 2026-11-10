# 010.37 Hosted Configuration Assistant Qualification

Completed on 10 November 2026, including final desktop/phone UI readback and
fixture cleanup. The results below are actual checks, not an assertion that
every model reply passed.

## Serving Components

- Platform backend on staging and production: source
  `5d94e3e59f1c3e64d739f4f0d75ed22a75617bba`.
- Final Console presentation: source
  `669250d6224373b8deed777264357ee6196b189d`, serving digest
  `sha256:ba6390957c0c8ae9a7268cedbca02205e8814115ea37f19353003942b0483c8b`.
- Shared private authoring runtime on each host: source
  `1db306265558042b3566a485b84014345c075f22`, actual serving digest
  `sha256:532927ca9a685a924736c76eec1e9339b082453313c90e8b5f5d4b8957cd3ced`.
- Released AI Fabric remains **0.8.46**. No framework source or release was
  changed. Existing customer-serving Northfield, Support and ProdUS runtime
  images were not replaced by this authoring-service rollout.
- Native Docker readback confirmed one healthy final container per component
  on each host. Both backend images have digest
  `sha256:50677bf2da478b73a9c1dbbb27af08df25bb44c326bb96124e6db4056abf3565`.
- Both fixed private services retain their original internal allocation/store
  identity, with liveness and readiness UP after upgrade. They use isolated
  schemas on the existing Platform PostgreSQL services, not customer business
  databases or one assistant service per customer.

## Actual Customer And Staff Results

1. Genuine Supabase customer sign-in and authenticated API access worked on both
   control planes. Production UI showed the correct customer's configuration.
2. [Staging imports](2026-11-10-staging-import-evidence.md): **26 passed**.
   [Production imports](2026-11-10-production-import-evidence.md): **26 passed**.
   OpenAPI/Swagger JSON/YAML, samples, cURL, PDF, source review/reuse/deletion,
   public HTTPS and denied internal URLs were exercised without model calls or
   operation execution. Original failed harness/rollout runs remain recorded.
3. Actual models created two action definitions and their routes while preserving
   existing actions and unknown fields. Production's ordinary import request
   produced a compatible catalogue on its first qualified attempt.
4. Untouched actual staging and production generated catalogues passed the
   released 0.8.46 action loader. Each generated READ route then passed one
   temporary localhost connector/mock invocation. Only the upstream base URL was
   explicitly overridden for the mock. No generated write action or real
   external business API was executed. The earlier invalid-hint response remains
   the negative control; it was not normalized to make the probe pass.
5. Actual production behaviour-profile generation saved Warm / Advisory / Brief /
   Contextual with the registered revision/instructions, preserving the existing
   general system prompt. A staging model-setting edit saved only provider config.
6. Neutral HTTP_JSON and bound-S3 Data definitions reached `valid:true` through
   existing checks. A failed correction required another explicit user turn to
   remove five invented mappings. Trusted server metadata declarations remained.
   No reusable version, connection test or indexing success is inferred.
7. An AI-edited Engage draft displayed its real branding, welcome message and
   tools in the hosted Max-mode preview. Sample preview content was not reported
   as inference or a working app assignment.
8. An active production Support Engage app remained PREVIEW with `editable:false`,
   even when a direct apply request was made. The endpoint returned the explicit
   edit-lifecycle notice; the app's complete response hash was unchanged and its
   state remained ACTIVE. It was never silently disabled or saved live.
9. Existing Support specialist configuration qualified a preview-only limit
   change with zero validation errors and an unchanged draft hash. Empty worker
   authority and a missing Desk binding returned NEEDS_INPUT rather than invented
   workers/agents/connections. Existing dealership knowledge guidance returned
   EXPLAIN with zero changes and an unchanged draft hash; its limited projection
   was not presented as a live indexing/connection probe.
10. Real staging UI replaced a manually saved label with the requested AI label,
    paused editing during the turn, and reloaded the actual saved value. Undo
    restored the previous manual label and retained `futureField.untouched:true`.
    Production UI also saved its requested label and kept other fields intact.
    Final production UI at 1440x960 resumed the manual editor/composer after
    restoration. At 390x844, document width was exactly 390, the composer stayed
    inside the viewport and its centre was unobstructed. Phone undo restored the
    original label, and closing the panel restored the floating Support launcher.
    The real staging Data history showed two saved-invalid warnings separately
    from its subsequent valid result; no extra model call was used to stage that UI.
11. Preview followed by explicit apply, same-key submission deduplication,
    cancellation without late draft changes, actor/resource isolation and source
    deletion were exercised. Other-actor session access and foreign-resource
    Brain creation returned 404; anonymous access returned 401. Foreign Engage
    access returned the existing ownership guard's 403 with no resource content.
    The first test expected 404 for Engage too; that harness assumption was
    corrected after inspecting the actual denial, with the original nonzero run
    retained. No authorization/product code was changed. Database session bodies
    were verified sealed rather than plaintext.

## Tests And CI

- Focused Platform assistant suite: **182 passed**, zero failures/errors/skips.
- Runtime [CI 42743386211](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42743386211):
  **748 passed**, zero failures/errors/skips; immutable image publication passed.
- Final UI: full preview/TypeScript/Vite production build passed; **40** focused
  unit/route tests and **18** controlled browser groups passed. These local browser
  fixtures do not substitute for the separate hosted customer evidence above.
- Final UI image [CI 42746886520](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42746886520)
  passed. Backend image [CI 42743305051](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42743305051)
  passed. Storage regression `42740706173` passed after the React harness fix.
- Final composition check caught the real floating Support launcher covering the
  authoring composer. The launcher is now hidden only while authoring is open;
  Help navigation and existing Support state are untouched. **13** real-Support
  fixture groups and the **18** assistant groups passed, including desktop/phone
  restoration and retained Support iframe/unsent draft. This is not a new global
  customer-page redesign.
- The unrelated initial quota CI had one Docker/Testcontainers environment error,
  not an assertion failure. It is not relabelled green. No framework-wide release
  gate or capacity/drain redesign was required for this product-only change.

## Provider Usage And Operations

Real OpenAI `gpt-5.4-mini-2026-03-17`, one model call per submitted turn, no hidden
generation retry. Final read-only ledger snapshots on 10 November:

- Staging at 02:56:26 UTC: **23** ingress/provider attempts, **127,477** tokens,
  exact ledger cost **USD 0.122037750**.
- Production at 02:57:02 UTC: **5** ingress/provider attempts, **41,488** tokens,
  exact ledger cost **USD 0.038012250**.
- Hosted total: **28 attempts**, **168,965 tokens**, **USD 0.160050000**. This is a
  usage total, not 28 semantic passes. Earlier local 20-call qualification is a
  separate record, not included or double-counted here.

Each service's finite initial internal allowance is USD5 / 100 ingress attempts /
2M tokens, expiring 10 December 2026 at 01:45:27 UTC. Renew through the existing
audited accounting lifecycle before exhaustion/expiry. Never reset usage or
initialize again on startup/upgrade. These are shared internal authoring limits,
not customer chat funding. Manual editors remain usable when generation is disabled.

## Accepted Limits

Model quality is not perfect: one prior request was answered instead of the latest
one, one correction retained invalid mappings, and model prose can disagree with
the actual save result. Current-query clarity, real validation findings, explicit
follow-up and authoritative UI state are used; no narrative rewriting, semantic
matcher, automatic repair or hidden retry conceals these attempts.

Assistant imports are not document indexing. The existing bound-S3 contract here
supports TXT/JSON; OCR/private authenticated crawling/new executable protocols are
not added. Real connection tests, source approval/indexing, publication, deployment
and business execution remain explicit existing workflows. See the
[customer guide](../../Final_Documentation/External_User_Guides/CONFIGURE_SMART_BRAINS_WITH_AI_ASSISTANT.md).

## Cleanup And Evidence

- Main qualification: 10 staging and 2 production customer sessions are absent
  with 404 readback; 3 staff preview/guidance sessions were deleted separately.
  Import-run session deletion is recorded in the two import reports.
- Deleted the one unused draft Engage app and its resource-free consumer.
  Archived three uninstalled private definitions and both main configuration-only
  Brains. The two separate import Brains were also archived. Archive metadata and
  audit history are intentionally retained; no hard deletion is claimed.
- No fixture runtime, connector, vector service or external storage was ever
  provisioned. Existing products, active Support app and customer accounts were
  not removed or reconfigured.
- Cleanup guards caught two operator-script assumptions: the archive request
  needs `expectedRevision`, and the production fixture stores its original owner
  on the plugin rather than at its root. Correct requests were used after owner
  verification; no product check, approval or authorization was bypassed.
- Both browser test accounts were signed out, the temporary production tab was
  closed, viewport overrides were reset, and the separate CLI auth session was
  revoked with its private tokens removed. Existing passwords were not changed.
- Actual screenshots and raw receipts remain outside Git under the operator's
  private `loomai-authoring-01037` evidence directory. Useful screenshot names:
  `production-assistant-qualified-settled-desktop.jpg`,
  `production-assistant-qualified-phone.jpg`, and
  `staging-actual-validation-warning.jpg`. Earlier captures, including the
  launcher overlap and initial restoration-busy view, remain retained.
