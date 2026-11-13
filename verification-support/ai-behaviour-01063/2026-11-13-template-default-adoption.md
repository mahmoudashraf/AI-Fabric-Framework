# Dealership Bootstrap Default Adoption

Date: 13 November 2026.

## Result

Reusable dealership defaults are published and selected for new customer setup
on both control planes. Existing Northfield configuration and live resources are
unchanged. The separate richer S3 policy activation is **not complete**.

Published TEMPLATE revisions:

- Simulator: `mkp-template-simulated-dealership-experience-v1@1.15.0`.
- External inventory: `mkp-template-dealership-external-systems-v1@1.5.0`.
- External inventory with human support:
  `mkp-template-dealership-external-systems-managed-inbox-v1@1.11.0`.
- Required policy DATA plugin: `mkp-data-dealership-knowledge-v1@1.2.0`.

The legacy managed-inbox identifier is retained; its reviewed production template
composition is preserved. Staging previously had only version1.3.0 of this
template; the new revision uses the explicitly reviewed production1.10.0 input,
not an assumed staging1.10.0 or a customer draft.

Defaults: exact model `gpt-5.4-mini-2026-03-17`, similarity threshold0.4, eight
evidence documents and8,000 context characters. Managed profiles are
Consultative / Advisory / Balanced / Contextual. READ planning is iterative,
maximum2 iterations and10 evidence items, with the existing bounded two-space
missing-selection recovery policy. No experimental follow-up prompt was adopted.
Contextual next steps retain their existing additional AI-call behavior.

DATA1.2.0 adds `PLATFORM_PROXY_SESSION` to the existing policy source and matching
compatibility list. All other source fields, approval requirements, filters,
storage rules and permissions match the reviewed1.1.0 manifest exactly. Adding a
transport does not grant another document audience or bypass ownership.

## Platform Changes And Delivery

- `4d857ff5d`: source builder/defaults and new-install catalogue discovery.
  Retired self-enrolled public starters are hidden from new-install discovery;
  direct historical grants/resources and private invitations remain valid.
- `1a1a1e19b`: permit existing threshold/managed-profile fields in TEMPLATE
  defaults, delegating their validation to existing deployment contracts.
  No runtime reasoning, framework, UI or permission change.
- Backend-only image CI43462333219 and storage CI43462333188 passed.
- Both backends natively verified healthy on full source
  `1a1a1e19b3ec54f7e7befb556cb117f4331c10a7`, digest
  `sha256:81fe716925a68f8f2db593ade38227396ed81dc5306788dc8bfe7b876e20c6cf`.
- Normal submit/validate/publish APIs published all four revisions. New starter
  `public-dealership-quality-20261113` uses the exact required plugin versions
  and registered0.8.52 runtime artifact. Existing generic/support starters are
  preserved. Native process configuration confirms selection on both planes.

Templates remain bootstrap defaults, not restrictions on later customer upgrades.
No customer credentials, canary storage, live bindings or funding were bundled.

## Verification

-16 source-builder tests passed;173 expanded backend tests passed. An initial
  wider run found one stale test expecting four retired inbox permissions;
  its assertion was corrected without changing production permissions.
- Live submission initially rejected `ragSimilarityThreshold`, exposing the
  manifest contract gap. The rejected submission was retained and retried only
  after the tested Platform correction; there was no duplicate publication.
- Actual customer sign-in and UI Kit creation passed. Exactly one Kit was
  submitted, creating Brain `dep-3c263d72` and both standalone/embedded Engage
  configurations. Readback confirms the new defaults and DATA1.2.0, with public
  document approval stillfalse and no copied canary connection.
- Brain response controls display Consultative / Helpful guidance / Balanced /
  When useful. The actual branded Engage visual preview renders. Screenshots
  inspected; zero captured page errors and zero AI queries.
- This is **configuration/bootstrap qualification**, not a newly provisioned
  runtime, activated customer domain, new source-indexing run or fresh AI-answer
  benchmark. The preview is explicitly synthetic. Existing live Northfield
  qualification remains separate; ordinary semantic-quality limits remain.
- The docked support composer covered a Continue button; its visible Minimize
  support control was used. No force-click, hidden overlay removal or UI change.
- Test Kit, Brain, consumer and both draft apps were removed through existing
  APIs; deletion `del-4725f1f1` SUCCEEDED. No infrastructure was provisioned.
- Northfield's ten saved draft sections and draft identity/timestamp are equal
  to the pre-task snapshot. Version `ver-fcd3ee7b`, both verified releases and
  all three ACTIVE installations remain. Six runtime/connector/AI-state services
  are natively healthy. Unauthenticated draft access remains401.

## Policy Activation Still Pending

The richer fictional policy pack exists at
`product-demos/autotrader-dealership-demo/backend/deployment/customer-storage-policy-pack/`.
The bucket still serves the original three ACTIVE sources per environment.
No upload, refresh/index, vector transfer or richer-pack answer test occurred.

The existing `aws-toolkit` session is expired. Required for production holds the
owner confirmation request for renewing that sign-in, then verifying bucket-owner
account522783510785. No login, IAM mutation, new persistent uploader or cross-account
provisioner substitution was performed.

Resume: verify access/owner, back up the exact six existing objects, conditionally
replace staging's three approved files, refresh/review/index through existing
source workflows, inspect real policy/compound answers, then independently repeat
for production. Never copy vectors or silently use the website's seeded policies.
