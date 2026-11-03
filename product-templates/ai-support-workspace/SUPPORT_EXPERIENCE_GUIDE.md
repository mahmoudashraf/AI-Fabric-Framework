# Support Experience Setup And Operations

## Status And Ownership

These are source-only 010.57 contributions. They have not been published or
qualified live. Local schema/contract tests do not establish answer quality,
real requester authorization, staffing, provider connectivity or release readiness.
Use normal verified releases and the final customer-journey suite before enabling.

The reusable kit owns no customer S3 documents, credential values or diagnostics.
Keep the existing customer-owned document DATA connection and scoped identity.
Do not replace them while applying branding, installing ticket actions or upgrading
the template. Current disposable Console tickets require no migration engine.

## Owner Setup

1. Publish the reviewed TEMPLATE/ACTION/INTEGRATION source through the existing
   Marketplace submission/review flow. Do not overwrite any published version.
   Keep the 1.0.2/1.0.3 quality candidates unaccepted unless separately qualified.
2. Through ordinary offer authoring, expose the support TEMPLATE family with
   a `support@1.0.0` workspace profile. Use
   `fixtures/support-experience-config.json` for its editable interaction config
   and the customer's supported authentication profile. Entitlements and target
   availability remain the normal catalogue policy, not a bypass in the kit.
3. Select AI Support Experience, the Technical Support preset, the owned offer
   and staging target. Choose website, standalone or both. Set branding/topics.
   `fixtures/requests/support-kit-create.json` shows the normal kit request;
   replace its example offer/target IDs with actual catalogue choices.
4. Choose ticketing/provider separately from human support. Saved
   `ticketingEnabled`/`ticketProvider` and `humanSupportEnabled` are preferences,
   not provisioned services. Kit creation only creates canonical drafts/links.
5. Connect approved S3 knowledge using existing Knowledge/Connections editors.
   Index and verify it normally. A READ tool does not require vectors unless its
   own retrieval/grounding contract does. Install only authorized tools.
6. Publish/apply/verify through the existing release lifecycle. Use the normal
   owned support activation endpoint to register the assigned target's managed
   support space. It derives the space/provider binding; customers do not type
   space IDs or copy service credentials. Supported identity and exact allowed
   origins must match that target. Public metadata comes from this binding.
7. Human support additionally requires the actual channel and target SUPPORT
   assignments. Registering a person or setting a preference does not assign
   support access. Ticket-only support is valid with human support disabled.
8. Preview illustrative states without AI/provider calls. Explicitly activate
   the Engage installation only after readiness passes, then use its generated
   script/hosted URL. An app URL or bundle in a catalogue is not activation proof.

Templates remain editable bootstrap defaults. Normal upgrade/reset/publish/apply
flows retain the deployment/app identity and customer-owned bindings. Do not
republish or replace the customer's app to change models or prompts.

### Source-Only Offer Builder

`scripts/build-support-offer.mjs` exports `buildSupportWorkspaceProfile` and
`buildSupportOffer`. The CLI accepts a reviewed input JSON file and prints a
normal `PublishOfferRequest`; it makes no API calls or SQL changes.

`buildSupportWorkspaceProfile({authentication, loginRequired})` requires the
customer's explicit public Supabase `projectUrl`, `publishableKey` and `methods`.
The bundled profile-defaults fixture contains no project or key. The default
is direct authenticated runtime access; `loginRequired:false` permits public
AI with separately protected capabilities. Both start with unbound managed
support references and disabled ticket/human presentation preferences.

`buildSupportOffer` takes `offerId`, `reviewReference`, the actual published
`template` version summary, required published `pluginVersions`, actual immutable
`artifacts`, and `targets:[{profile,intent}]`. Supply `review` with the existing
offer's explicit reviewed name, operations, settings, binding slots, document
audiences, prerequisites, limits, topology, AI-state topologies, external profiles,
and four policy references. It derives manifest, artifact, target and workspace
pins from these inputs. It supplies no policy approvals, capacity, customer
entitlements, funding or storage credentials. Those remain normal API review
and admission requirements, not values cloned from an internal canary.

An internal Console offer may pass its separately reviewed `workspaceProfile`
explicitly; the builder never selects the private Console host by default.
Main owns that private profile and safe bootstrap projection. Publishing the
offer, enrolling the real owner and creating the fresh kit remain explicit API
operations. A generated request is neither a published offer nor live proof.

## Host Installation And Identity

Use the generated `/api/public/ai-workspace/install.js` snippet for the actual
installation ID. Page presentation uses a persistent container and the pack's
isolated-frame host; do not destroy/remount it on each host render or run a second
widget engine. Missing containers are visible errors, not a full-screen fallback.
Standalone and embedded installations may share the same deployment/consumer,
but each has normal delivery and origin/authentication settings.

Direct customer hosts use the reviewed requester origin resolved from the
assignment and supported requester identity. The Console private host uses a
thin same-origin adapter and a stable private requester subject; runtime tokens
and requester assertions are not interchangeable. Never use email as ownership,
accept browser-supplied roles, or expose server keys to the browser.

`GET /session` supplies the safe selected provider binding and effective operation
capabilities. The pack intersects those with requested UI features. Do not put
capability assertions in editable config or treat a service key as requester
identity. On logout/account changes, discard private lists, drafts and subscriptions.
Cross-origin unsent draft synchronization and anonymous identity merging are not
supported. Full navigation restores owned saved requests, not every unsent draft.

## Actions And Agents

`support-tickets.1.0.0.manifest.json` declares confirmed creation plus own-list
and own-detail READs. It does not install AI resolve/cancel operations. Managed
endpoint and secret-reference fields are supplied by trusted scoped provisioning,
never model parameters or public pack config. The existing connector generates
verified authentication and conversation headers; JSON trace/owner fields are
not authorization. Action-origin creation adopts only that verified current
conversation through the explicit registered server-trace profile.

Before activation, qualify that caller projection end to end at the Desk
integration resource. A requester-JWT route alone is not a connector caller
projection. If a reviewed server-connector profile is unavailable,
leave these actions disabled; a scoped service key, dummy bearer token or forged
requester field is not a substitute.

The Desk INTEGRATION remains the current conversation-channel primitive. It is
not a new ticket-provider plugin and does not create staffing. Its V1 anonymous
continuation policy does not grant anonymous ticket access. Unsupported provider
operations remain disabled; do not translate them into unrelated mutations.

Agents use existing SUPPORT assignments and Desk conversation controls. Ticket
status does not change conversation authority: resolve is not close/return-to-AI,
and delivered opening text is not proof that AI has completed an answer. Agents
cannot use a ticket reference to read unrelated runtime transcripts or diagnostics.

## Retention And Failure Handling

Configure the registered space's closed-ticket retention explicitly. Deleting a
ticket does not delete its canonical runtime conversation. Use authorized scoped
retention/purge APIs with expected versions and idempotency; no table edits or
cross-customer cleanup. Production and staging spaces, credentials, storage
prefixes, test requesters and ticket records remain isolated.

On provider outage, show unavailability and retain provider provenance. Never
fallback to Desk storage, dual-write, manufacture receipts or silently replay an
uncertain create. Opaque versions are strings; never parse an external ETag as an
integer. Refresh owned state after a version conflict before retrying deliberately.
Use the original idempotency key for the same command and reject changed replay.

Ticket metadata should remain accessible during runtime outage; failed transcript
or opening-message delivery remains visible. Desk outage never authorizes AI to
answer for a human-controlled conversation. Human return/close stays explicit.

## Local Checks

Run `node --test product-templates/ai-support-workspace/scripts/*.test.mjs` and
the package's offline `scripts/validate-dry-run.mjs`. The latter invokes the real
Marketplace validator and sends no API requests. Run focused Platform pack/kit
tests plus PostgreSQL migration tests through V225. The backend asset packaging
tests verify support discovery, immutable hashes and missing-output failure.

`scripts/compose-support-experience.mjs` takes a saved owned installation and
appearance/preferences JSON, then prints a normal revision-checked Engage update.
It preserves connection/authentication/domain settings and provider references,
does not make network calls, and does not activate or publish. The server remains
authoritative for validation and owned bindings. No template tuning or fixture is
accepted AI quality evidence; use the frozen reviewed quality corpus separately.
