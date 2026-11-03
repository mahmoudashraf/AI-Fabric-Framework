# Support Experience Composition Contract

Source-only implementation contract for 010.57. No publication, deployment or
answer-quality acceptance is implied by these files.

- Pack: `support@1.0.0`; configuration: `loomai-support-experience-config-v1`.
- Bundle: `experience-packs/support-experience/dist/support-experience.iife.js`.
- Kit: `ai-support-experience@1.0.0`; preset: Technical Support.
- Public template family: `mkp-template-ai-support-workspace`; new revision `1.1.0`.
- Internal instance family: `mkp-template-loomai-support-workspace@1.1.0`.
- Platform migrations: `V225` and the additive instance mapping `V229`. Console
  provisioning and retirement are separate.

The pack uses the section 13 configuration: `app`, `support`, `capabilities`,
plus existing `page`, `copy`, `theme`, `toolGroups`, `toolRail`, `requestContext`.
`support` contains safe `ticketProviderBindingId`/`spaceId` references, optional
`requestLabel`, `teamVisible`, and at most twenty `{code,label}` topics.
`support.requesterOrigin` is an optional exact HTTPS origin for the reviewed
Desk requester facade, never an arbitrary endpoint path. The private Console
passes its same-origin facade through its trusted host context instead.
There are no browser-configured credential, requester or operator authority fields.
Drafts allow missing/empty managed references, including when ticketing is
requested. They are not customer-entered provisioning fields. The active public
manifest resolves them from the assigned verified target's managed support
binding. Its deterministic support-space ID is the target Desk binding ID.
Activation/readiness requires the actual enabled, owned, authenticated service;
neither reference existence nor a capability preference establishes authority.

Kit `ticketingEnabled` and `ticketProvider` are saved setup preferences separate
from `humanSupportEnabled`. Creation uses the ordinary catalogue/bootstrap,
consumer and Engage draft services. It never provisions Desk, creates requests,
publishes, applies, verifies or activates. Managed target registration/readiness
and safe projection are described below. A preference grants no authority.

The initial provider choice is `desk`, matching `DeskTicketProvider.id()`.
Effective provider binding/capability metadata comes from authenticated
`GET /session`, not editable pack configuration or the static kit catalogue.
External provider implementations
need a registered adapter and their own qualification; no vendor is claimed.
The existing `mkp-integration-loomai-desk` INTEGRATION contributes the human
conversation channel only. It is not relabelled as a ticket-storage contract.
Ticket ACTIONs use managed URL/secret-reference fields and trusted provisioning;
they do not introduce a new Marketplace contribution or plugin type.
The new template requires `mkp-action-support-tickets@1.0.0` through the existing
TEMPLATE `requiredPluginRefs` and supplies only managed URL/secret-reference
install defaults. Normal bootstrap installs the confirmed create and own-read
tools into the editable draft alongside the existing document dependency. This
does not publish, activate ticketing or grant requester authority; a disabled
ticket provider still refuses ticket operations even if the tools are installed.

Reusable defaults have no LoomAI diagnostics, S3 locations, credentials or
Console role assumptions. Existing document connections and scoped identity
remain instance-owned. Templates are editable bootstrap defaults; upgrades,
reset, publication, apply and verification remain the normal lifecycle.

The new `1.1.0` template includes only the currently allowed nonsecret model, executor and prompt
quality leaves from the 2026-11-03 production draft readback: GPT-5.4-mini,
iterative two-pass READ planning, planner 12000 characters, action evidence
8000 characters / ten items, knowledge overview, single-space bounded RAG
fanout, and eight-document / 8000-character RAG context. It retains the
ticket-specific confirmation, receipt and independent-handover guidance.
No sources, identity settings, credentials or diagnostics were copied. This
is a source candidate awaiting fresh live quality proof, not an accepted new
default for every template. Historical published versions are not rewritten.
The current TEMPLATE schema also accepts `answerGenerationPrompt`,
`retrievalPrompt` and `assistantUiPrompt`; the new candidate includes the reviewed
nonsecret baseline for all three. `vectorSpaceSelectionRequired` remains a
draft-only field and is not smuggled through other fields. Source prompt parity
is not live quality qualification.

The new template also sets `providerConfig.canonicalConversationsEnabled:true`
for owned conversation initialization, messages and control independently of
human handover. This is a Platform runtime feature, not a new Framework release
or permission to enable a human channel. Normal apply must map it to
`LOOMAI_CONVERSATION_CONTROL_ENABLED:true`, retain `chat:messages`,
`chat:conversations` and `chat:handover:read` for authenticated direct access,
and leave human transitions disabled when handover is off. The selected runtime
artifact and explicit activation/readiness must support that contract; a
template flag or an older verified release alone is not proof of availability.
Its existing `requiredRuntimeCapabilityIds` includes the distinct
`standalone-conversation-control` capability, derived from the enabled provider
preference during TEMPLATE validation. The source offer builder rejects offered
runtime artifacts lacking it. External support-space GET/PUT and live/cached
readiness use the exact release artifact through
`StandaloneConversationRuntimeArtifactGate`, including immutable provenance,
normalized manifest hash and published framework-version checks. Old
`conversation-control` or orchestration-state metadata cannot substitute for
the new marker. A missing gate is reported as `CANONICAL_CONVERSATIONS` readiness.
Existing published template versions remain unchanged.

## Internal LoomAI Instance

`manifests/loomai-support-workspace.1.1.0.manifest.json` is a separate, source-only
instance composition, not a public customer default. Its provider and prompt
defaults match the new generic `1.1.0`, including the canonical conversation
requirement and ticket confirmation/receipt guidance. This LoomAI instance also
requests the human extension once its managed channel and action are configured.
It requires
exactly:

- `mkp-data-ai-support-documents@1.0.0`
- `mkp-action-support-tickets@1.0.0`
- `mkp-action-loomai-support-diagnostics@1.0.3`
- `mkp-integration-loomai-desk@1.0.1`
- `mkp-action-conversation-handover@1.1.0`

Only the Desk channel receives target-independent managed references in
`requiredPluginInstallDefaults`. No actual URL, credential or binding ID is
embedded. Native Desk ticket aliases must not precede the Console ticket ACTION's reviewed
`EXTERNAL_SERVICE` profile aliases. The existing offer/bootstrap initializer
configures ticket and diagnostics fields through those two profiles. Endpoint
approval and target-scoped credential binding remain normal API operations;
neither endpoint values nor keys are template defaults. Other empty required
inputs use the ordinary deferred-input path. The managed channel is registered
by normal target provisioning; an agent assignment and an explicit conversation
handover are still required before a human joins. Customer-owned S3 and identity
remain unchanged.

`V229` adds the internal template family alongside the generic family in the
existing `ai-support-experience` definition. It creates no offer, installation,
source binding, release or authority. The pure offer builder accepts either
reviewed `1.1.0` family and enforces the exact required plugin set and actual
published version metadata. The internal family additionally requires an explicit
reviewed `BACKEND_MEDIATED_PRIVATE_RUNTIME` / `platform-support-case-host` workspace
profile; it cannot inherit the direct Supabase profile by default. The backend
still enforces the configured Console owner and private-host registration.

## Managed External Activation

`GET` / `PUT`
`/api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/support-space`
uses an active Supabase customer owner and current owned installation/assignment.
PUT accepts `installationRevision`, `assignmentRevision`, opaque
`expectedVersion`, `enabled`, `humanHandoverEnabled`, `ticketProvider: "desk"`,
optional `closedRetentionDays`, and `operationKey`. It accepts no space/runtime/
identity URL or credential. A stale revision/assignment fails before provisioning.
Optional paired `targetProfileId`/`releaseId` select a same-deployment verified
candidate for pre-registration before normal consumer promotion. They never
change the assignment or choose an arbitrary URL. GET supports the same optional
query selectors; omit both to inspect the current assignment.

The service reuses `DeskBindingProvisioner.prepareSupportBinding`. Ticket-only
registration records `SUPPORT_ONLY`, not a successful runtime handover activation.
It neither publishes a release nor changes app/document configuration. Both
presentations on one target share the binding-derived space and their registered
exact origins. Supabase issuer/JWKS/audience must match the published runtime.
Prepared credentials remain stable after an uncertain remote registration; use
status readback and the returned opaque version for a deliberate conditional PUT.

Public support configuration replaces all editable space/provider/origin fields
with the verified assignment's persisted safe registration receipt (V228).
Successful configuration stores only binding/owner/target, registered runtime
and requester origin, safe identity and feature metadata, never credentials.
Public delivery validates these local references without a Desk RPC. Provider
outage therefore does not prevent ordinary AI from loading; the pack's requester
session reports unavailable ticket operations using the original provider refs.
The existing runtime conversation control still prevents AI resuming while HUMAN
is active. This receipt is not a claim of current provider availability.
Explicit activation/readiness checks the live registered provider, identity,
origins and requested operations. Human support
also requires the actual published Desk extension and assigned SUPPORT team.
Local draft evaluation does not call Desk. Ticket ACTION caller qualification is
a separate gate from form/requester readiness.

## Connector Caller Qualification

Live end-to-end qualification remains a release gate; source/schema tests alone
do not prove the deployed connector's trust boundary.
`TraceContextSupport.forwardHeaders` in the existing generic connector overwrites
`X-AIFABRIC-AUTH-*` headers from its verified runtime trace. Direct authenticated
callers have `END_USER`, `PUBLIC_RUNTIME_AUTHENTICATED`, `PUBLIC_BROWSER`, the
original Supabase subject, and the runtime's public issuer/audience. They do not
carry the original Supabase JWT. The ACTION's JSON `trace` is not proof.

The bounded path stays in the existing connector and Desk services:

1. The explicit Desk `server-ticket-commands` route authenticates its distinct
   managed connector credential and rejects browser Origin/cookie/role fallback.
   Its admin-registered `serverConnector` profile validates single generated
   headers against the frozen target's PUBLIC runtime issuer/audience:
   subject type/mode/caller, customer/tenant/deployment, runtime issuer/audience,
   expiry and operation scopes. A key alone or model actor field does not qualify.
2. Use the original verified Supabase subject, never email or a model parameter.
   The registered profile requires `chat:conversations` and `chat:messages`;
   it grants no internal diagnostic scopes.
3. For action-origin create, the typed verified runtime trace attests only the
   current owner-authorized conversation. Desk uses the immutable generated
   conversation header, ignoring JSON conversation/owner/trace fields. The
   runtime must have authorized that turn and the connector must overwrite
   forwarded headers from its verified runtime context. The dedicated per-space
   secret is restricted to that approved server connector, not browser code.
4. Direct requester start/link retain original Supabase requester JWTs and the
   original runtime bearer with independent canonical ownership reads. The
   server trace is a separate explicit profile, never a JWT fallback. No raw
   user token forwarding, CP ticket proxy or credential-minting callback is used.
5. Qualify missing/forged/expired/wrong-target headers, service-key-only calls,
   wrong conversation ownership, confirmed create replay and own-list/detail
   isolation before activating the ACTION contribution. No separate bridge
   deployment, new ticket store, token fallback or guessed plugin type is needed.

Desk/Gauss owns the server-trace entry contract and its hard authentication/
ownership tests. Platform registers its exact frozen public identity metadata.
Console's private-host adapter is separately owned by main.

The normal ACTION install request is in
`fixtures/requests/support-ticket-action-install.json`. Existing compilation
requires the managed URL alias, not a concrete URL. Normal provisioning must
prepare the owned target credential through `DeskBindingProvisioner.prepareForApply`,
which also handles ticket-only actions without enabling a human channel.
`RailwayProvisioningPlanService` now adds the connector-only map from
`DeskSupportTicketConfiguration.connectorEnvironment` when
`requested(actions,routing)` is true. The URL and secret reference derive from
the selected target; published versions contain no staging IDs or key values.
The normal Coolify apply caller includes managed ticket actions as well as the
optional human channel before target secret materialization. Compatible-artifact
selection and normal apply enforce the distinct capability for
`canonicalConversationsEnabled` releases. Main owns the separate private Console
support adapter, publication, actual registration and live qualification.
