# Production Product Upgrade To 0.8.57

## Status

In progress under explicit owner approval to upgrade all production products.
Source preparation and local compatibility checks are complete; no hosted
mutation has happened yet. Do not treat the earlier Account Resolver rollout
as proof of private-product adoption.

## Scope

Upgrade existing active LoomAI production products: Platform publication default,
configuration assistant, Support and Northfield runtime/connector targets, the
production Console's staging-only Harbor Brain, and Desk's chat-session dependency.
Qualify staging first. Preserve Brain/app identities, credentials, models,
behaviour profiles, document/vector storage, permissions and action confirmation.
Verify Engage, Console and Desk through their connected flows. No UI redesign.

Stopped historical/test resources, databases and services without a framework
dependency are not restarted or needlessly redeployed. The unrelated public
framework example fleet is separate from the LoomAI product rollout.

## Baseline

Fresh snapshots include both hosts' applications, relevant native containers,
environment rows and each production-Console Brain's draft, published versions,
releases, consumers and Engage installations. Published source-draft comparison
shows zero changed sections for Northfield, Support and Harbor; publishing for
the framework update will not include unrelated configuration edits.

Northfield and Harbor were0.8.52; Support0.8.55. Configuration assistant and Desk
are independently pinned and must be checked by embedded libraries, not labels.
No supplied key or authenticated payload belongs in this report.

## Local Qualification

- Product modules passed, including78 budget-ledger tests with actual PostgreSQL,
  guarded transport, vectorization core and runner21.
- Platform compiler/artifact/validation:150 passed.
- Desk local150:108 passed and42 PostgreSQL-specific cases skipped. The existing
  Desk CI requires PostgreSQL and must pass before rollout; no local all-green
  persistence claim.
- Connector163 passed. Runtime818 initially had one outdated generation mock;
  red XML retained. Three-argument mock adapter corrected without weakening
  budget-denial/native-call or completed-write assertions; focused3 passed.
  Full runtime verification remains an image-pipeline gate.

## Remaining Gates

1. Required CI and immutable backend/runtime/Desk images.
2. Staging service adoption and native/public-artifact compatibility checks.
3. Normal Brain publication, staging apply and conversational checks.
4. Production service/apply/binding adoption with unchanged configuration.
5. Live customer/assistant/Engage/Desk checks and final integrity evidence.

Raw evidence and bounded helpers: `~/.codex/private/loomai-products-0857/`.
