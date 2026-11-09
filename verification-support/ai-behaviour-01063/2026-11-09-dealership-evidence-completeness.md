# Dealership Evidence Completeness - 2026-11-09

Scope: implement 010.63 section 6.2 evidence improvements only. No suggestion,
prompt, framework, account-ownership or customer UI redesign changes.

## Execution

- [x] Confirm current Northfield releases through the production control plane:
  staging `rel-c7415db7`, production `rel-b329b925`, version `ver-5557856a`;
  both report `APPLIED_VERIFIED` / `PASSED`.
- [x] Trace authoritative simulator stock fields through indexing, read actions
  and presentation; preserve genuinely unknown values.
- [x] Inspect active policy registrations: exactly three original short sources
  per target, all active version 1. No competing six-file fixture registration.
- [ ] Inspect stored objects and back up existing
  objects privately before replacing an unversioned object.
- [ ] Qualify the single fictional Northfield policy pack on staging, then
  production, using normal source refresh/index reconciliation.
- [ ] Run focused tests and live evidence checks. Record failures honestly.
- [ ] Update canonical implementation/context records with actual outcomes.

## Boundaries

- Independent website seed data is not an AI evidence source.
- Simulator facts are fictional, not Auto Trader sandbox or Paul Rigby facts.
- Do not invent an official provider field or infer an absent feature.
- Keep trust, tenant, advertiser and source ownership checks intact.
- Do not copy vector records between environments, wipe collections, patch
  indexing tables directly, or add semantic text matching.
- Retire superseded evidence only after its replacement is active.
- Runtime storage credentials remain read-only. Missing upload permission is
  reported rather than bypassed with a broader runtime identity.

Private credentials, object backups, raw API receipts and query transcripts
remain outside Git under `~/.codex/private/loomai-evidence-20261109/`.

## Findings And Local Implementation

- Added optional simulator-authored seats, numeric range in miles, equipment,
  advertiser town and forecourt date. One vehicle deliberately has no optional
  evidence. No field is copied from website seed data.
- The checked public provider contract does not define a range test-basis field.
  Neither simulator nor UI invents WLTP/NEDC certification.
- Added immutable DATA/READ/template revisions through migration 239. Existing
  installations, releases, permissions and historical manifests remain untouched
  until the normal installation update/publish/apply workflow is used.
- Fixed generic connector array loss in trusted target content and null values
  represented as literal searchable facts. Structured fields remain structured;
  no domain-specific semantic normalizer or text matching is introduced.
- Focused PostgreSQL migration test passed, including historical row preservation,
  native manifest validation and a repeat-migration check.
- The first browser baseline completed both queries but its broad transport guard
  rejected the site's existing visit analytics requests. That runner result is
  **not** an accepted UI gate. Retained exchanges separately show missing vehicle
  fields and no retrieved delivery policy evidence.

## External Prerequisite

The original local staging S3 reader key returns `InvalidAccessKeyId`. This does
not mean the live connector is unavailable: its scoped source preview succeeds.
The separate managed-storage provisioner authenticates but has no read access to
this existing canary bucket. Required for production confirmed that no approved
current uploader is recorded. Owner upload or a scoped uploader is requested;
runtime read permissions will not be broadened or substituted across buckets.

The prepared three-file fictional policy pack remains pending upload and indexing.
Do not report policy qualification complete from source registrations alone.
