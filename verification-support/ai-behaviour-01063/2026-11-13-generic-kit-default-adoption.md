# AI Customer Experience Default Adoption

Date: 13 November 2026.

## Result

The four owner-approved generic quality areas are published and selected for new
AI Customer Experience Kits on both control planes:

- Consultative tone and Balanced answer length.
- Contextual next steps, shown as "When useful" in the customer UI.
- Iterative READ planning, maximum two iterations, and
  `RAG_IF_ACTIONS_INSUFFICIENT` cooperation.
- Eight retrieval documents/8,000 context characters, READ planner context12,000
  characters, per-action evidence8,000 characters and10 evidence items. Already
  larger evidence limits are not reduced by the source builder.

Grounded answer breadth is retained (UI: "Your information only"). The model,
provider output-token limit, search threshold, permission/approval/confirmation
rules, recovery/fanout policy and other modes are not overridden. Contextual next
steps retain their existing additional generation/accounting behavior; no web
search, text rewriting or hidden semantic fallback was introduced.

## Publication

Source `6a72a38eb` adds the source-only builder and a shared catalogue-driven
profile resolver. All three previously published dealership outputs remain
identical with that resolver; there was no dealership republishing or rollout.

Published `mkp-template-document-knowledge-assistant@1.4.0` derives from the
current published generic1.3.0 manifest, not a customer draft. Managed instructions
come from the actual catalogue. Its unrelated fields and generic DATA1.3.0
dependency remain unchanged. The baseline without managed profiles inherits the
catalogue's Grounded breadth; an explicit/custom breadth is preserved exactly.

Version IDs: staging `mkv-618ca102`, production `mkv-a7e0b188`. New starter
`public-knowledge-quality-20261113` uses those exact versions and the existing
qualified0.8.52 runtime. The old generic starter referenced TEMPLATE1.2.0,
DATA1.2.0 and runtime0.8.41; the new starter uses current published dependencies
rather than bootstrapping an obsolete runtime. No framework change/release or
new image was required. Normal composition publication refreshes the artifact,
dependency and actual target fingerprints, preserving original funding/model
limits, storage choices, workspace profiles, permissions and binding slots.

Only the generic starter ID changed in native onboarding configuration.
`public-dealership-quality-20261113` and `public-support-profile-20261108` remain
selected. Both backends reloaded this setting on their same1a1a1e19b image, native
digest81fe7169..., and are healthy. Staging deployment `xax8qobxvae9x0srm9buog8k`
qualified before production deployment `drszr0l0azh1nbq6uzu7j5vm`.

Historical customer grants and existing drafts/releases were not migrated or
overwritten. Templates remain editable bootstrap settings, not lifetime pins.

## Actual Customer Proof

A real customer signed in and entered normal AI Customer Experience Kit setup.
The existing setup enrollment operation registers access to the current public
starter before loading choices. A catalogue GET by itself intentionally does not
grant new starter access. No privileged grant write or catalogue mutation was
added to make the check pass.

Exactly one Kit, `kit-4ccc229f-46f4-4855-8eb7-4bdeed99ccc7`, was created with
Brain `dep-d65a97e0`, a standalone Engage app and an embedded installation. Saved
readback matched the requested profiles, all evidence/planning fields and the
current generic DATA version. Public document approval stayed false; no canary
credential, dealer inventory, policy pack or customer data was copied.

The Responses page displays Consultative / Your information only / Balanced /
When useful. Actual selected preset IDs were checked rather than assuming API
technical labels were customer-visible labels. The branded Engage preview
renders the chosen workspace name. Screenshots were inspected; zero page errors.

This is **configuration inheritance and view qualification**, not a new active
runtime or an AI-answer benchmark. There were zero model queries, published
releases, activated customer domains or provisioned infrastructure. Preview
content is explicitly illustrative. Synthetic visit-counter events were
suppressed without mocking authentication, configuration or model responses.

The fixture's two draft apps, consumer, Brain and Kit were removed through normal
customer APIs. Deletion `del-3d5ba08e` SUCCEEDED; no infrastructure was removed
because none was created. Other customer resources are not cleanup targets.

## Verification

Nine generic builder tests and16 existing dealership tests pass. They cover exact
change scope, catalogue identities/instructions, input/output independence,
preserved breadth/model/threshold/source/authority, larger budgets, invalid saved
contracts and CLI metadata handling. No Java, UI or framework code changed.

Raw captures, customer auth state and screenshots remain private under the task's
ignored operational directory. They contain no committed invitation, provider key
or customer credential. Remaining ordinary AI semantic-quality limits from the
earlier qualification are not reclassified as perfect results by this adoption.

At23:22 UTC, final live readback confirms identical1.4.0 manifest hashes on both
planes, unchanged Northfield draft identity/timestamp/all saved sections and
verified releases, all three existing apps ACTIVE, six serving services healthy
and both sets of policy sources still ACTIVE at version2. Unauthenticated draft
access remains401. The test customer's Kit/Brain inventory matches its pre-test
snapshot after cleanup. No unrelated resource or data cleanup was performed.
