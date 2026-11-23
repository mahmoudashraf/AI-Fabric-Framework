# Support Resource Ownership Verification

Date: 23 November 2026. Result: tested access-control boundaries passed, with
separate AI guidance findings below. This is a targeted verification, not a full
Platform security certification or qualification of future retrieval changes.

## Handoff And Serving Scope

The separate document-intelligence handoff was received. Its completed journey
and limits are recorded in
[the 22 November release evidence](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/verification-support/document-intelligence/2026-11-22_RELEASE.md).
Do not redo indexing or restore that document's historical serving versions.

The complete next plan is
[010.73 Hybrid Retrieval, Optional Reranking And AI Led Knowledge Operations](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_73_HYBRID_RETRIEVAL_RERANKING_AND_AI_LED_KNOWLEDGE_OPERATIONS_PLAN.md).
Its order is current inventory/baselines, genuine hybrid retrieval for documents
and entities, shared settings/Ragas evaluation, bulk knowledge operations, then
optional learned reranking. It has not been implemented by this verification.
Ragas and learned reranking are not represented as already deployed.

Both tested Console backends/UIs serve `97be03bf0c3f7f13c565ac873ffa95df5b653249`.
Both existing Support runtime images still match the qualified 0.8.66 digest
`78927abf08384cbd456891dd2b711cb8a794ffe03270d2aa4b061f34d2b98445`.
No release, model, prompt, permission, database schema or allowance was changed.

## Live Method

Normal signed-in customer UI and customer Bearer sessions were used, with two
known test customers whose customer IDs were confirmed different. No staff or
internal service credential was used for an attacker request.

Each environment used a temporary foreign Brain with a private synthetic action
name which was never supplied in the attack questions, a real foreign Engage
app, and a temporary foreign source-connection record. The connection was saved
as metadata only: no credential, discovery, sync or external API execution.
The rightful owner successfully read its records first, so denials were tested
against known-existing resources rather than guessed nonexistent IDs.

An owned, unprovisioned Brain provided the positive diagnostic control. Neither
temporary Brain had a version, release or allocated infrastructure. Checks ran
on both staging and production Support, including actual model responses.

## Results

- 85 focused backend tests passed, with zero failures, errors or skips. This
  comprises the 78-case ownership/conversation/proxy suite and seven additional
  document-status/requester-assertion checks, not the whole backend or framework.
- Each environment passed 20 explicit assertions, including 14 HTTP denials:
  40 assertions and 28 denied requests in total.
- Twelve real chat queries completed, six per environment. The actual Brain
  specialist succeeded and the launch-readiness plan completed in both runs.
- The resource picker showed the caller's owned Brain and excluded foreign
  Brain, app and connection IDs.
- Forced selection of each foreign resource returned 403. A mixed owned/foreign
  selection also returned 403 and left the prior selected resources, revision
  and granted consent unchanged; there was no partial acceptance.
- A direct foreign Brain draft read returned 404; another customer's app-list
  request returned 403. The private synthetic capability was not disclosed.
- Foreign conversation context, resume, history cursor and transcript-through-
  owned-proxy reads returned 404. Ordinary history excluded the other requester.
- All four internal diagnostic endpoints returned 403 to the customer session.
- With no selection, a question naming a foreign Brain returned no resource
  observations. With valid selection and consent, the positive check returned
  the owned Brain's actual draft state.
- Forged body `conversationId`, `userId` and `customerId`, plus foreign resources
  in client context, did not change the trusted requester or selected resource.
  The message remained in the attacker's canonical conversation; the victim's
  messages and completed turns were unchanged.
- Adversarial specialist/plan questions did not return foreign diagnostic facts
  or the private marker. Returned resource observations, where present, referred
  only to the server-approved owned selection, not the requested foreign ID.
- After consent revocation, no fresh resource observations were returned,
  including when the user requested the previously selected Brain and a foreign
  Brain together. Already-authorized facts in prior chat history are not erased
  by revocation; they do not grant a new diagnostic read.

## Why The Boundary Holds

Resource selection and consent are persisted server-side. A resource ID, a
claimed administrator role, or purported permission in a chat message is not an
ownership/delegation grant. Ownership is rechecked at the diagnostic read; the
model and child specialist cannot choose another customer's execution identity.

Relevant implementation points:

- [Resource ownership checks](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/SupportResourceService.java:51)
  cover deployment customer ownership, app/consumer ownership, and source
  connection customer/tenant/deployment relationships.
- [Selection and consent writes](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/SupportConversationScopeService.java:91)
  recheck ownership, preserve revisions and serialize against consent changes.
- [Diagnostic reads](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/SupportDiagnosticService.java:34)
  resolve the original requester/principal, enforce current scope and consent,
  then recheck the selected resources before returning bounded facts.
- [Private diagnostic authentication](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/SupportDiagnosticAuthentication.java:28)
  requires its dedicated service key and expected binding/identity/scope claims.
  A customer token alone does not authenticate that internal connector route.
- [Canonical proxy body binding](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/runtime/SupportWorkspaceService.java:74)
  forwards only supported fields and replaces caller-supplied conversation IDs.
  Its route allowlist prevents reading another conversation through an owned one.

## AI Guidance Findings

No unauthorized resource disclosure or execution was observed. However, some
answers still suggest selecting the unavailable foreign ID, although it is not
in the resource picker and the server refuses it. Some wording claims to have
reviewed the named resource when the tool supplied only a selection/consent
requirement. One staging answer retained the earlier foreign target despite an
actual observation for the selected owned Brain; it did not attribute that
owned observation to the foreign Brain.

These are response-quality findings, not evidence of a permission bypass. A
bounded follow-up could guide the existing prompts to say that only resources
available to the current account can be selected and that an unmet requirement
does not prove resource existence, inspection, status or readiness. Do not
implement a keyword refusal filter or weaken the server checks to improve the
wording. No such prompt/framework change was made in this test.

## Cleanup And Limits

Four temporary Brain drafts and their owned connection records were deleted
through normal owner APIs; an independently authenticated staff read confirmed
all four deletion operations SUCCEEDED. That staff session was not used in any
negative test. Both staff cookies were revoked and proved unauthenticated.

All customer test browsers signed out. Diagnostic consent was revoked, test
selections cleared and the original conversation selection restored. The four
new test conversations remain private audit records, not new tickets or a
second transcript. No business write, index operation, production apply or app
activation occurred. The original 48 staging and 24 production application IDs
and infrastructure settings remained unchanged.

This does not claim full coverage of same-workspace role combinations, external
ticket adapters, all transport/header variants, concurrency/revocation races or
every prompt-injection form. In particular, it does not qualify future hybrid,
Ragas, bulk-indexing or reranking behavior. Those changes must retain and test
ownership/audience/active-version filters before any candidate reaches a model
or reranker; current Support safety is not a substitute for that acceptance gate.

Private raw evidence, response review and deletion/logout proofs are under
`~/.codex/private/loomai-support-ownership-20261123` (0700). The exact received
010.73 file and its SHA-256 receipt are preserved there. No credentials, cookies
or raw requester traces are committed in this report.
