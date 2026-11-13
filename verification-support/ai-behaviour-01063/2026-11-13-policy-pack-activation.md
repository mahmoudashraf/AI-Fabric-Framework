# Northfield Policy Pack Activation

Date: 13 November 2026.

## Result And Scope

The approved fictional Northfield policy pack 1.1 is uploaded and independently
indexed on customer staging and production. The existing three registered source
IDs per environment are retained, each with candidate/active version 2. All six
superseded version-1 index manifests are `DELETED` through normal reconciliation.

This completes the policy-data activation requested alongside the separately
published dealership bootstrap defaults. It is not a claim of perfect model
answers or completion of every deferred 010.63 integration.

Brain `dep-3c4dbe4f` remains on saved version `ver-fcd3ee7b` (v15), staging
`rel-3618a1fb` and production `rel-712024cd`. No draft, prompt, release, framework
or runtime change was needed for this data activation. The runtime remains
AI Fabric 0.8.52. Existing app addresses and assignments remain in use:

- Staging: <https://northfield-staging.apps.loomai.pro>.
- Production: <https://northfield-console-live.apps.loomai.pro>.

## Storage And Indexing

The owner approved and completed renewal of the existing AWS operator sign-in.
Its identity and bucket owner were verified; the separate managed-storage
provisioner was not substituted. No new key, IAM identity, permission, bucket
policy or runtime-reader credential was created or changed.

Only `delivery.txt`, `test-drive.txt` and `warranty.txt` were replaced under the
existing bucket's `dealership/staging/` and `dealership/production/` prefixes.
All six original objects were backed up before writing. Each conditional upload
used its previous ETag, the expected bucket owner and a SHA-256 checksum. All six
returned HTTP 200 and passed independent downloaded-byte SHA-256 and encrypted
object-metadata verification. Private access, SSE-S3 and HTTPS remain in place.

The same file bytes are used independently in each prefix:

- `delivery.txt`: 1,691 bytes,
  `a482a6f473f768faa4cdcbc1ef2a3c0c569f1f47570dd172a51473185d0b1c42`.
- `test-drive.txt`: 1,632 bytes,
  `b78b98b62eb136157c426ee6923b3a71fb18f5d9db3b55e2a0e1cbba222daf9f`.
- `warranty.txt`: 1,118 bytes,
  `bff38eb01bef2cd85a3242bed571924a191401bd7590ba455323cafa260ac379`.

Staging used the existing refresh, preview, reviewed index and reconciliation
workflow. Actual staging answers were inspected before production's conditional
uploads and independent indexing. Each document produces one complete indexed
chunk. The preview's bounded display excerpt is not the complete indexed text.
The existing five `METADATA_KEY_DROPPED` notices concern reader metadata outside
the configured whitelist, not missing policy content.

There was no vector copying, collection wipe, duplicate source registration,
website-seeded policy fallback, inventory database or new infrastructure.

## Actual Answer Qualification

Sixteen real UI query turns were captured and reviewed: eight on each environment.
Staging used seven initial turns plus one explicit age/licence check; production
used all eight scenarios in one run. These were not retries selected for greener
answers. All sixteen returned HTTP 200/success, preserved session continuity,
contained current version-2 approved policy evidence and produced no captured
page errors, blocked turns or successful business writes.

The reusable cases are in `policy-pack-1.1.scenarios.json`. Actual query/debug
responses and screenshots were inspected against the source documents and the
authorized stock READ results, rather than scored by keyword matching:

1. Stock followed by delivery: both parts answered, correct two eligible cars.
2. Second-option test-drive follow-up: Morrow C2 resolved without asking the
   customer to choose an internal knowledge domain.
3. Delivery followed by stock: both parts answered in the reverse clause order.
4. Forty-mile delivery: individually quoted, not the GBP 49 local fee; insurance
   effective from handover cannot be waived.
5. Test-drive duration and request details: up to 30 minutes is duration, not
   minimum notice; required contact details and unconfirmed-request status correct.
6. Warranty: engine/gearbox coverage and exclusions; battery cover and monetary
   claim limit remain unverified rather than invented.
7. Handover: cleared payment, identity, preparation and insurance requirements
   retained, with collection/delivery checks.
8. Eligibility: both answers refuse the age-20, licence-11-month request and state
   the actual age-21/licence-12-month thresholds. See the reasoning defect below.

### Retained Model Limitations

Production is **qualified with limitations**, not eight perfect semantic passes.
Its eligibility answer correctly refused the request and quoted both thresholds,
but then incorrectly said the 11-month licence met the 12-month requirement.
That explanation is a real partial semantic failure. No appointment or other
business action was executed. Staging's explicit eligibility answer was correct.

Production's warranty summary omitted the available 90-day duration. Staging's
handover answer opened with an ambiguous "Yes" before immediately stating that
collection/delivery must wait for cleared funds. These omissions/wording notes
are retained. Some answers are verbose or end with generic optional offers.

No answer was rewritten or retried to conceal a miss. No phrase stripping,
lexical routing/scoring, hidden fallback or framework patch was added. These are
ordinary answer-quality follow-ups, not an unresolved storage/index activation.

## Final Integrity Check

At 22:14 UTC, both releases remained `APPLIED_VERIFIED` on the existing saved
version. All ten saved draft sections, draft identity/timestamp, selected runtime
addresses and app assignments matched their pre-activation snapshots. The three
existing Engage installations remained `ACTIVE`. All six Northfield runtime,
connector and AI-state database services were natively healthy. Unauthenticated
Console draft access still returned 401.

Live readback again confirmed exactly three sources per environment, with version
2 active and version 1 deleted. The template builder's 16 tests also passed again.
This final data/test/documentation change requires no additional product image
build or framework release; the earlier backend publication is recorded in the
separate bootstrap report.

## Evidence And Boundaries

The runner suppresses only synthetic visit-counter writes, including the current
Supabase visit RPC, so qualification does not inflate analytics. It does not mock
chat/model/source responses. It additionally verifies the expected runtime URL
when supplied in the environment scope. Native source/target/publication IDs are
checked separately from manual answer-quality review.

Raw responses, screenshots, original object backups and operational receipts
remain in the ignored private verification directory, not in this repository.
No auth token, signed URL or credential is included here. This fixture is not
real Paul Rigby, Auto Trader or another dealership's policy.

The fresh customer Kit inheritance/preview proof and publication details remain
in [bootstrap-default adoption](2026-11-13-template-default-adoption.md). That
configuration-only fixture was cleaned up normally; this policy qualification
uses the existing live Northfield apps, not a newly provisioned customer runtime.
