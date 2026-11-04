# 010.57 Support Experience Kit Execution Record

Started: 3 November 2026
Status: Native implementation deployed; core hosted journeys qualified, with explicit quality and notification limitations
Source baseline: `0ea836cc85ec68efc0e33a86b9658f7fd6872648`

## Owner Decisions

- Implement 010.57 fully, including real LoomAI Console adoption, production
  verification, S3 knowledge preservation and legacy ticketing retirement.
- Greenfield: current LoomAI tickets are disposable. No old-ticket import,
  compatibility readers or historical transcript migration is required.
- Preserve actual approved S3 documents/connections, environment-scoped secrets,
  current qualified behavior and real trust boundaries.
- TicketProvider is generic; Desk is the first implementation. External vendor
  adapters remain deferred, but replaceability must have contract-test evidence.

## Streams and Ownership

- Main: shared integration contracts, Console thin adapter/context, provisioning,
  S3/configuration snapshots, actual rollout, quality and customer-journey proof.
- Desk backend: TicketProvider, native persistence, requester/agent/integration
  routes, security and focused persistence/contract tests.
- Support pack: generic support experience and contained installer/host behavior.
- Catalogue: registry/schema/assets, kit bootstrap and template contributions.
- Desk UI: ticket view in the existing managed service UI.

Parallel streams have disjoint write sets. Main reviews and integrates their
work; no parallel task independently deploys or changes customer resources.

## Phase State

- S0: COMPLETE. Philosophy/contracts reviewed; actual pre-change snapshots and
  nine-turn production baseline retained. Its two old deployments had13 active
  sources and framework0.8.44; this is historical baseline, not current fleet.
- S1: COMPLETE_NATIVE. Shared Desk provider/persistence, scoped requester/agent
  APIs and contract tests implemented. Optional email delivery is not implemented
  as a qualified integration; native in-app updates are the released channel.
- S2: COMPLETE_NATIVE. Registration, separate identity proofs, canonical transport
  and confirmed CREATE/authorized READ tested through both real identity modes.
- S3: COMPLETE_NATIVE. Shared page/widget/standalone pack, installer and Desk UI
  implemented. Hosted desktop proof passed; actual live phone is not claimed.
- S4: COMPLETE_NATIVE. Catalogue, configuration/preview, support-space setup and
  fresh normal kit installations in LoomAI and neutral Acme are working.
- S5: QUALIFIED_NATIVE_BASELINE. Core hosted lifecycle/security/action/human
  journeys passed. Known semantic follow-up failures and the wider unexecuted
  edge-case matrix are not represented as all-green quality acceptance.
- S6: COMPLETE_NATIVE_CUTOVER. Console uses the real kit on production; scoped S3
  sources preserved, obsolete dedicated services and old Console ticket store
  retired, guides and dated evidence published. Limitations stay listed below.

## Evidence Rules

Code or a health probe is not hosted customer-journey proof. Record actual test
counts/skips, source/asset/image identities, exact deployment jobs and first AI
outcomes. Retain failures and known limitations. Never publish secrets or private
customer transcripts. Source-only quality candidates are not accepted defaults.

No completion claim is made by this initial record.

## Integration Progress, 3 November

- Baseline: all nine production quality turns completed. The existing wrong-subject
  compound follow-up was reproduced; it is not counted as a successful answer.
- Catalogue slice: 141 Java tests, 22 Node tests, a PostgreSQL migration and real
  asset packaging passed locally. Managed support binding activation is being
  integrated next.
- Desk ticket UI: 87 full UI tests and production build passed locally; no hosted
  claim yet.

## First Hosted Findings And Corrections

- First requester preflight failed at team read while the Desk binding's local
  projection still said APPLYING. The normal owner Desk-status operation
  reconciled the actual verified release to ENABLED. The explicit preparation
  sequence now includes this supported readiness step; session/team/list then
  passed. No database status override or weakened readiness check was used.
- First lifecycle and quality attempts saved and delivered their opening
  messages, but canonical `completedTurns` omitted the successful answer. An
  authorized read-only inspection confirmed an accepted INFORMATION_PROVIDED
  result: its stored size was 303,352 characters, including duplicate RAG
  documents/context. A 256,000-character history budget was incorrectly applied
  before public projection. Commit `fa7e1530e` applies the budget after removing
  diagnostics and redundant context. Oversized optional detail now retains the
  actual canonical answer with an explicit limited-presentation marker. No
  opening message was replayed and the qualification assertion was not relaxed.
- That commit also supplies the registered API origin to the isolated Console
  host. Otherwise the generic pack correctly rejects a different runtime origin
  because it compares against the frontend origin when no BFF origin is given.
- Focused runtime tests: 24 passed, zero skipped. Widget canonical-history smoke,
  typecheck, seven Console host tests and Console production build passed. The
  first added Java assertion had a missing assertion-helper reference; it was
  corrected and the successful run retained separately.
- Opening-mode observation: Desk posted the form's opening query without a
  mode, causing the deployment default THINKER (actions disabled), while the
  support chat pack uses EXECUTOR. A bounded, explicit server-registered opening
  mode is being added to the Desk space contract, with runtime-owned extensible
  mode identifiers. This is configuration consistency, not semantic rerouting.
- Console host: provider-delegated request routes, separate private requester
  assertions, runtime transport and minimal consent/resource context added.
  Conversation-first support does not automatically create a ticket.
- The old Console case service/store/controller and duplicate ticket-operation
  store were removed from production source. V227 retires their four tables at
  rollout under the owner's greenfield authorization; no data migration is added.
- Platform backend compilation/package passed with tests skipped while the
  obsolete lifecycle tests are being replaced. This is compilation evidence only.
- An existing conversation may be adopted by a ticket only with independently
  verified runtime ownership and a host-signed principal/conversation binding.
  Model parameters cannot choose requester identity. No transcript copying.

Private evidence directory:
`~/.codex/private/loomai-support-kit-01057/`.
Original S3 reader scopes remain unchanged. The deliberate guide-12 replacement
and its conditional upload/private backup are recorded below.

## Integration and First Rollout

- Commits: `476f8e48d` (plan), `adf6fa5fa` (Desk provider), `994d7323f`
  (Desk ticket UI). Pushed to the current branch. Desk image workflow
  `41683636532` passed at exact source
  `994d7323f9a91a652b6acfd256d039a927161784`.
- Staging Desk deployment `s6kbhjawvx08tnjn4jovnesz` completed. Native container
  image revision and healthy status match that source. This does not qualify
  ticket creation, canonical chat or human handover end to end.
- Found a Platform-runtime coupling: canonical saved-conversation APIs were
  enabled only by the human-handover switch. A ticket-enabled, human-disabled
  kit therefore lacked its conversation transport. Separate opt-in canonical
  conversation control is being added to Platform runtime and normal generation.
  Human transition endpoints still require actual handover enablement. No
  AI Fabric framework change or release is needed.
- Focused Platform generation/identity/manifest/secret tests: 93 passed after
  correcting the handling of an absent provider configuration. The first run's
  six configuration-reader errors were retained, not hidden.
- Console support suite: 145 executed, 144 passed and one template-schema error
  for the new canonical flag. The schema and required artifact capability are
  being integrated before the next run. No hosted completion claim.
- Private Console identity remains distinct from direct Supabase requester
  identity. Ordinary AI bootstrap uses a verified registration receipt rather
  than a live Desk call; ticket-provider outages must not disable unrelated AI.

## Source Freeze and Runtime Verification

- Runtime commit `5eb1dbc84`: standalone canonical conversation opt-in, secret
  projection and real human-off denials. Runtime `mvn verify`: 599 passed,
  zero failures/errors/skips, including PostgreSQL. The packaged JAR advertises
  `standalone-conversation-control`. Runtime image workflow `41688534188` is
  still running; no deployment uses that image yet.
- Backend/catalogue commit `70646e425`: reusable support kit, scoped registration,
  private Console adapter and removal of duplicate Console ticket storage.
  Integrated Support/kit/provider/artifact suite: 226 passed, zero failures or
  skips. The earlier template-schema error is resolved. Template/asset helper
  tests: 17 passed. Existing apply/generation/readiness regressions also passed.
- Customer kit UI commit `ad0179c67`; pack/engine/installer/qualification-runner
  commit `e034f6a0d`; Console host adoption commit `3493d2a58`. Shared core images
  are being built from the pushed source. Source changes do not by themselves
  prove a working external customer installation.
- Production Desk deployment `raaodwh6r8d6edr1a6a88b9w` is healthy at the same
  verified `994d7323f` source as staging.
- Read-only historical request episodes and attachment-suggestion transport are
  explicitly supported by the Console adapter. Earlier episodes cannot accept
  new messages; a provider-owned current episode is required for writes.
- LoomAI-specific ticket commands will use the existing external integration
  binding primitive with separate target-scoped credentials, exactly like its
  diagnostic actions. The reusable public kit retains its direct managed Desk
  command route. No Console-specific callback is added to generic customers.

## Fresh Staging Kit Qualification

- Shared Desk is deployed in both environments at `994d7323f`; staging Console
  is at `3493d2a58` and staging backend at `0d2699990`. Their exact Coolify jobs
  completed and native image revisions/health were checked. Production Console
  and backend have not yet been switched.
- Runtime image `5eb1dbc84` completed CI and is registered with its immutable
  digest. A real normal customer-kit creation under the existing LoomAI owner
  created `kit-f03664cc-02f1-4a28-8896-cc2a92ed1b12`, deployment
  `dep-a9d6d646`, and support installation
  `awi_pub_7964cf7dcbde0b0163be77554c5c5cfe`. This is not a relabelled old
  support deployment.
- Internal support template `1.1.0` composes the reviewed document, ticket,
  diagnostics and handover plugins. Commit `3eab95897` supplies only logical
  target-managed Desk channel references needed before first apply; actual
  credentials stay target-scoped. The reusable external template has no Console
  diagnostic dependency. Focused compiler/bootstrap tests passed.
- Fresh staging release `rel-f7db58e9` finished provisioning. Its first
  verification failed only at `document_active_sources_ready`, before documents
  had been registered. All 13 approved S3 guides were then indexed explicitly;
  retrieval returned five correctly scoped evidence items, and normal
  post-apply verification was requested. No vectors were transferred.
- Existing source storage/read identities are retained. Guide 12 was deliberately
  refreshed to describe the new request/diagnostics journey, using a scoped
  uploader, conditional object replacement and a private original-content
  backup. The other twelve sources were preserved.
- Current remaining work: hosted native ticket/agent/AI qualification, neutral
  external-kit proof, production fresh-kit promotion and Console cutover,
  followed by exact old support-resource retirement. No end-to-end completion
  claim yet.

## Production Adoption And Live Qualification

- Console/backend source `4befbbae917bc816e3ca47327bcf853e2ef134e8` is healthy
  in both environments. Desk source `d990b202b6aa50a770a4b96bd14d07b1202156f5`
  is healthy in both environments. This includes the bounded opaque runtime
  issuer correction described below. The final retirement fix is not deployed
  yet; do not infer hosted adoption from source HEAD.
- Runtime source `fa7e1530e57a4279681b64d4795a7116b60dd7ac`, immutable digest
  `sha256:efe38e7b4db81c4fc732700dbae6d39ff6ccc2c368ec0228d482ccc0963fee0f`,
  remains AI Fabric **0.8.45**. There was no framework change or release in this
  implementation. Runtime build `41705333508` and core build `41705333501`
  succeeded. Artifacts are `dsa-d7aa2347` (staging) and `dsa-a2b7e7aa`
  (production), both attested against the actual OCI revision/digest.
- Actual LoomAI-owner developer-staging kit:
  `kit-f03664cc-02f1-4a28-8896-cc2a92ed1b12`, deployment `dep-a9d6d646`,
  installation `awi_pub_7964cf7dcbde0b0163be77554c5c5cfe`. Its original
  verified version was `ver-008d3864` / release `rel-08257cf6` after the
  canonical-history correction. The subsequent settings-only canary is
  `ver-230f5a96` / `rel-d36e16a2`; it is not a new recommended template default.
- Actual fresh production-control-plane kit:
  `kit-ca617a82-bfb6-411e-89fe-94ccabf8713e`, deployment `dep-79af290c`,
  installation `awi_pub_fc4f82f55cb6b83355cd807f0a21adfa`. The normal kit flow
  created these resources, rather than renaming the old support deployment.
  Version `ver-35b051a2` is verified on customer staging (`rel-e9693e16`)
  and production (`rel-94fd2fcc`). Production assignment, support registration,
  app readiness and explicit activation passed. The Console now uses the
  production target, not the staging runtime.
- The first promotion apply `rel-06c3f05f` was interrupted by the simultaneous
  backend rollout. A normal restart recorded FAILED; no database status override
  or automatic replay was used. Explicit new cold apply produced `rel-94fd2fcc`.
  Because that new apply has no frozen promotion-candidate metadata, its normal
  verified consumer-binding operation was used instead of mislabelling the
  interrupted promotion as successful. The installation was deliberately disabled
  during the target change, prepared against the assigned production target,
  and explicitly reactivated. This is an operational interruption, not proof of
  uninterrupted promotion recovery.
- All 13 approved guides were independently indexed in each new target using
  the existing scoped S3 bindings. Sources are ACTIVE; retrieval proof returned
  five scoped evidence items. No vector, old ticket or conversation was copied.
  Only guide 12 was updated deliberately for the new request/consent lifecycle;
  original content and object identity checks are retained privately.
- Native requester preflight and lifecycle suites pass in staging and production:
  saved receipt, canonical opening/readback, own list/detail, seen/activity,
  resolve/reopen/cancel and chat-first link without a second intake message.
  Initial failures and first wire responses remain in the private evidence.
- Real staging UI qualification under an independent existing customer verified
  form save, restored AI answer, My Requests, resolve/cancel/reopen, human request,
  assigned-agent claim/reply, asynchronous requester receipt, visitor reply,
  explicit return to AI, repeated handover/close and an empty second episode.
  Ticket status and conversation control remain separate. Production UI created
  a fresh request and displayed a grounded answer explaining that separation.

## Bounded Quality Results, Not A Universal Guarantee

- Original production baseline: nine turns, including the known wrong-subject
  compound follow-up. Full raw results were retained before replacement.
- New staging: nine primary turns completed; manual review found six PASS,
  two PARTIAL and one FAIL. The 18-question document library found nine PASS,
  seven PARTIAL and two FAIL. HTTP success is not semantic acceptance.
- Read-only investigation showed the original 8,000-character generation limit
  omitted the actual reset/indexing procedure despite its presence in retrieved
  sources. Stored retrieval context was not mistaken for the exact provider
  prompt: the bound was reconstructed from the immutable framework algorithm,
  source order and actual selected chunks.
- One settings-only canary raised that limit to 32,000, keeping the model,
  prompts, source catalogue, provider order and permissions unchanged. Four
  unchanged questions produced three PASS and one FAIL. Template reset now
  correctly distinguishes draft/live/credentials; document replacement still
  incorrectly substitutes a deployment-release workflow. This candidate is not
  published as a global quality improvement or silently promoted to production.
- New production's nine primary turns completed. Installation and index-state
  questions are useful; wrong-subject compound follow-up and ticket/handover
  terminology remain known limitations. Documentation and real status checks
  are distinguished, and denied diagnostics are not scored as successful reads.
- No keyword/regex semantic routing, lexical document ranking, search-all
  fallback, confirmation bypass or fabricated successful receipt was introduced.

## Final Bounded Integration Corrections

- `9291b2334`: Desk now uses explicitly registered opening mode/optional position.
  Mode identifiers are bounded strings, not a closed Desk enum; actual runtime
  catalogue acceptance remains runtime-owned. 109 focused tests passed.
- `71389aeb0`: same-user Supabase focus/token events no longer clear the entire
  query cache and remount support. Real sign-out/account changes still reset.
  Three continuity tests, seven host tests and the Console build passed.
- `4befbbae9`: no background refresh while intake is being edited; focus no
  longer replaces live chat controls. Diagnostics preserve actual check codes
  and explicitly distinguish local configuration from provider/HTTPS probes.
- `028c96e1b` (included in deployed Desk `d990b202b`): a server-registered runtime
  issuer is identity metadata, not an outbound URL. Keep its bounded exact value;
  retain exact trace equality and all actual destination/JWKS/SSRF checks.
  The first neutral configure attempts failed visibly with HTTP 400; no alternate
  provider or private-host identity was substituted.
- `d990b202b`: starter prompts no longer cover a restored nonempty conversation.
  Typecheck and the eight browser/installer groups passed. Two earlier complete
  browser runs exposed an intermittent continuation wait, while the focused
  continuation run and final complete run passed; diagnostic state is now added
  to that timeout instead of hiding it or relaxing the assertion.
- `e97b4381b`: deletion now detaches the authorized deployment's circular
  release/snapshot references before removing authority snapshots and target
  authorities. Nine focused tests pass, zero skipped, including real PostgreSQL,
  another-deployment preservation and rollback after a later failure. Initial
  test compilation ambiguity was corrected before the passing run.

## Historical Pending Checkpoint (Superseded By 4 November Closure)

- Neutral direct-Supabase ticket-only installation: live support-space
  registration, activation, real successful ticket action, UI and isolation proof
  after the Desk issuer fix. Its three approved Acme files are indexed, its
  runtime is verified, and its vector fixture is isolated from other customers.
- Retire the exact superseded support runtimes/connectors/AI-state databases,
  complete normal metadata deletion after the authority-cleanup build, and
  retain the failures and actual retirement receipts.
- Final live visual readback for the starter-prompt fix and dated acceptance.
- Email notifications are not configured or claimed as delivered. The released
  interface says Updates in My Requests. Specific external ticket adapters,
  notification delivery integrations and unmeasured AI-quality improvements must
  not be advertised as qualified by these tests.

## 4 November: Native Release And Retirement Closed

Current authoritative acceptance:
`verification-support/support-kit-01057/2026-11-04-production-acceptance.md`.
Earlier entries retain their original intermediate results, not current status.

- Runtime/connector source83fd17427, framework0.8.45, digest
  `sha256:0ad2e34df4d9b5b9f218e96cbaa4e0b5b89196a5ca74c60e0f46ab28e5aaad08`.
  All eight containers across four targets are native-source verified and healthy.
- Production kit `dep-79af290c` uses version `ver-35b051a2` unchanged on customer
  staging `rel-2f6b9f87` and production `rel-09c92f90`. Both verified; production
  assignment/preparation/readiness/explicit activation passed. Its13 approved
  sources remain ACTIVE. Developer32k candidate remains separately identified.
- Neutral Acme `dep-b9a09c0a` / `rel-6f17b45e` verified and both installations
  active. Three separate fictional guides, direct Supabase identity, real-ticket
  isolation, distinct runtime token and human-disabled denials passed. Its
  confirmed CREATE and fresh installed own READ now both have actual receipts;
  no old confirmation or intake was replayed.
- Backends source021a17472 are healthy in both environments. Desk source28c8625c3
  is healthy in both. Console remainsd990b202b. Exact CI/Coolify identifiers and
  native receipts are in the dated acceptance and private rollout directory.
- Ordinary REST idempotency forwarding passed51 focused tests. Both ticket
  facades now require replay keys only for actual mutations:12 Platform tests
  and47 Desk tests passed, zero skipped, including real HTTP security and PG.
  No generated READ key, framework change or authorization weakening.
- Production confirmed CREATE returned`SUP-3503620B-EEC`; fresh own READ returned
  that actual receipt/status without changing its version. An earlier deliberately
  supplied unconfigured topic was rejected visibly and retained as a negative
  case; no semantic remapping was added. The helper's pending-confirmation
  `success` assertion was corrected without re-proposing the action.
- Production's independent customer UI completed real assigned-agent claim,
  asynchronous reply, visitor reply and explicit return to AI. Staging additionally
  proved close/new empty episode and retained old-history reads. Saved requests
  and AI/human control remain independent.
- Real public installer page/widget and standalone checks passed on a registered
  external origin. Embedded protected requests use the website identity hook;
  standalone supplies sign-in. Temporary nonsecret test assets were removed.
  Local responsive fixtures passed; the live viewport bridge ignored390px and
  reported1280px, so that live screenshot is explicitly desktop, not phone proof.
- Old`dep-a2706601` and`dep-757bc5bc` are deleted: normal receipts`del-65cd24a4`
  and`del-84c79f2e` SUCCEEDED, independent database counts0; six native resources
  absent. Owned volumes needed explicit vendor-admin retirement before normal
  metadata retry. S3/shared services/unrelated products preserved. Release-owned
  evidence deletion now passes15 focused tests including real PG rollback and
  another deployment's preservation. No old Console case repository/writer remains.
- Eight qualification-runner tests passed. Full runtime/image, core-image,
  deletion and Desk CI passed for the exact adopted sources. Earlier failed
  test-count assertions and first wire failures remain recorded.

Not claimed: universal helpdesk adapters, email delivery, perfect AI synthesis,
an exhaustive live expiry/outage/phone matrix, or interrupted-promotion recovery.
Measured wrong-subject follow-up and incomplete synthesis remain explicit quality
follow-ups; the32k candidate is not a global recommended default. These are not
hidden by a successful health probe, receipt or repeated question.
