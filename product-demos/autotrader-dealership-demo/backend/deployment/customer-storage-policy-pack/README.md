# Customer-Storage Demo Policy Pack

Version 1.1 is a deliberately fictional Northfield document set for the current
S3-backed demonstration. It is not Paul Rigby content or an Auto Trader policy.
It preserves the current canary's 90-day engine/gearbox warranty, 30-minute
test-drive duration and 25-mile standard delivery area while making the
demonstration rules and missing-fact boundaries explicit. New fictional details
include the local delivery charge and licence/age requirements.

The older sibling `document-sources/` examples are a different fixture set
(including a 45-minute test drive). Do not index both sets together. This pack
replaces the live canary's three short files. Activation was completed on
13 November 2026: independently uploaded/read back and indexed on staging and
production, with the same source registrations now active at version 2 and their
superseded indexes removed. Sixteen actual UI queries were reviewed; a production
eligibility explanation error and minor summary/wording notes remain recorded in
`verification-support/ai-behaviour-01063/2026-11-13-policy-pack-activation.md`.
This does not claim every model answer is perfect.

## Owner Upload

Upload only `delivery.txt`, `test-drive.txt` and `warranty.txt` into each existing
approved canary prefix, retaining their exact filenames:

- Bucket: `loomai-dealership-canary-978e0cdb142540e2`
- Staging: `dealership/staging/`
- Production: `dealership/production/`

Keep the bucket private, use its existing encryption/HTTPS protections and do
not upload this README. Runtime credentials stay read-only; use a separate
owner/upload identity. No new bucket, managed customer storage or infrastructure
is required. Future real dealerships must supply their own approved policies.

## Platform Acceptance

1. Qualify staging first. Use the existing owned document connection and the
   Document Knowledge screen for the active target release.
2. Inspect existing registered sources before creating anything. Replace each
   source using the normal new-first version/index reconciliation flow. Wait for
   successful indexing/activation; an accepted job alone is not completion.
3. Inspect and retire superseded registered fixture sources through supported
   source-disable/delete operations after replacement is active. Historical
   release records are audit history, not permission to mutate old releases.
   Do not wipe the vector collection or manufacture source IDs directly.
4. Check the actually retrievable source content and fingerprints. Confirm that
   duplicate old canary registrations cannot contribute conflicting facts.
5. Review answers for both stock/policy orders, GBP 49 within 25 miles, quoted
   40-mile delivery, payment/identity/insurance handover conditions, age 21 and
   12-month licence history, up-to-30-minute duration without invented notice,
   required contact details, and request-versus-confirmed-appointment wording.
6. Keep absent vehicle-specific warranty/finance facts visibly unknown. Confirm
   that information-only queries create no business requests.
7. Repeat the same source replacement and focused checks on production. Use its
   own source registrations and prefix; never copy staging vector records.

Score actual answers manually. Do not add keyword routing or lexical pass/fail
rules, automatically broaden retrieval, or change factual terms to make a failed
model answer appear correct. The earlier 30-minute interpretation failure remains
historical evidence; both current duration checks correctly distinguish duration
from minimum notice. Do not erase other retained model misses.
