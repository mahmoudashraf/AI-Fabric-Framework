# 010.37 Staging Import HTTP Evidence

## Actual Result

- Environment: staging, `https://loomai-platform-backend.46.224.145.148.sslip.io/api/configuration-assistant`.
- Final run: `2026-11-10T01-59-12-341Z-6f2d0bfd`.
- Started: **2026-11-10T01:59:12.343Z**. Finished: **2026-11-10T01:59:30.710Z** (UTC).
- Result: **PASS_IMPORT_ONLY: 26 passed, 0 failed, 0 blocked**; 133 backend HTTP requests.
- Readiness returned HTTP 200 with `enabled:true`, `ready:true`, `documentFetchEnabled:true`.
- Zero model calls, draft-setting writes, publish/apply, indexing, or infrastructure-provisioning calls during qualification.
- Two disposable sessions were deleted and subsequent reads returned 404; no cleanup failures.

## Isolation

Qualification used a separate configuration-only DRAFT Brain and tenant, created at
2026-11-10T01:58:13.200Z (HTTP 201). No release or runtime was provisioned. Only
MODELS and CONNECTIONS assistant tasks were used. The disposable Brain was later
archived as recorded below; its IDs are private. Main's recorded sessions were distinct and were not
cancelled or deleted. The isolated draft's before/after response hashes matched.

## Fixture Cleanup

At **2026-11-10T02:51:19.531Z**, a read-only preflight confirmed the isolated staging
fixture was DRAFT with zero versions, releases, and verification runs, no runtime
or connector, and a different identity from both main staging and production
Brains. Both own MODELS/CONNECTIONS sessions still returned 404, and the draft
response hash still matched its initial value before archive.

The ordinary archive API returned HTTP 200 and archived only this canary at
**2026-11-10T02:51:29.301096800Z**. A subsequent workspace read confirmed persisted
ARCHIVED state; cleanup finished successfully at **2026-11-10T02:51:29.529Z**.
Zero versions/releases remained. This was one own-canary metadata archive write,
separate from the import qualification's zero draft-setting writes. There were
no model calls, hard deletes, manual SQL, restores, or main-resource mutations.
Archived configuration/tenant metadata is retained.

The post-archive draft GET returned the expected HTTP 400 archived-resource
access denial. No post-archive draft hash comparison is claimed. The raw archive
intent, response, and successful cleanup record remain in private evidence.

## Passed Checks

1. OpenAPI 3.0.3 JSON: 2 normalized operations; inherited parameters/security, request body, local schema refs, explicit credential/header redaction, and benign fields preserved.
2. OpenAPI 3.1.0 JSON: 2 operations; nullable union and boolean schema preserved, with the same redaction checks.
3. Swagger 2.0 JSON: 1 operation; formal request-body/response conversion and visible conversion warning.
4. OpenAPI 3.0.3 YAML: 1 operation.
5. OpenAPI 3.1.0 YAML: 1 operation.
6. JSON sample: credential value redacted, business counter preserved, no invented operations.
7. Markdown/text: line anchors and declared Authorization redaction; ordinary prose unchanged.
8. cURL sample: header/cookie/user/OAuth credential values redacted; inert text, no operations executed.
9. Text-bearing PDF: actual extracted text, page anchor and credential redaction; no invented operations.
10. External refs: 4 network/file refs visibly unresolved in warnings and normalized projection.
11. Duplicate/absent operation IDs: unique source/method/path identities and duplicate-ID warning.
12. Source reuse/removal: catalogue IDs and content preserved, duplicate reuse deduplicated, removing the copy retained the original, and explicit selection IDs remained valid.
13. Malformed JSON rejected with HTTP 400 and no source persisted.
14. YAML alias rejected with HTTP 400 and no source persisted.
15. PDF without text rejected with HTTP 400 and no source persisted.
16. File of 2 MiB + 1 byte rejected with HTTP 400 and no source persisted.
17. Public HTTPS fetch of the [official fictional Swagger sample](https://petstore3.swagger.io/api/v3/openapi.json): 19 operations, 17,116 retained characters, 1 visible warning. No sample operation was invoked.
18. HTTP IPv4 loopback URL rejected with HTTP 400.
19. HTTPS IPv4 loopback URL rejected with HTTP 400.
20. HTTPS localhost URL rejected with HTTP 400.
21. HTTPS link-local metadata-address URL rejected with HTTP 400.
22. HTTPS IPv6 loopback URL rejected with HTTP 400.
23. HTTPS CGNAT-address URL rejected with HTTP 400.
24. HTTPS IPv6 ULA-address URL rejected with HTTP 400.
25. HTTPS port 444 URL rejected with HTTP 400.
26. Isolated Brain draft unchanged, verified by exact before/after response hashes.

## Preserved Failures

- **2026-11-10T01:54:31.330Z to 01:54:48.669Z:** initial shared-fixture run recorded 12 passes and 14 failures. After 11 import cases, writes returned HTTP 503 while reads still worked. Main confirmed old-disabled/new-enabled containers overlapped until rollout completion at 01:55:03Z. This is not counted as importer success or an established importer defect. Its own disposable session was cleaned up; its ID differed from main's subsequently recorded sessions.
- A later local preflight refused a fixture snapshot containing existing sessions (`FIXTURE_HAS_PREEXISTING_SESSIONS`) before any HTTP request. The original fixture was not overwritten.
- **2026-11-10T01:57:28.285Z:** isolated setup returned HTTP 409 because the requested tenant was already bound. No resource was created by that attempt; the existing binding was not changed. A separate tenant was used for the subsequent successful setup.
- **2026-11-10T01:58:20.952Z to 01:58:39.787Z:** first isolated run recorded 25 passes and 1 harness failure. A repeated fixture upload collided with an exclusive private evidence filename before source reuse was requested. Both sessions were cleaned up. Only verification-script filename uniqueness was fixed before the final run; no backend fix was made for this failure.

## Scope And Provenance

Provider operation selection is **NOT_EXERCISED**: explicit catalogue IDs and
selection payloads were checked without submitting a turn. External-ref warnings
and unresolved projections are HTTP evidence, not packet-level proof of no egress.
The no-text PDF case is not an OCR/image-extraction qualification. This record
qualifies the staging service at the times above, not later serving images or UI.

Import-run runner: [import-hosted.mjs](import-hosted.mjs), SHA-256 at qualification time
`0d55fea1e7266cc76446724cc9d902a6f9aa3794f7ccdb1c93271262015abeff`.
Raw reports/responses and disposable resource/session IDs remain in private
operator evidence, outside the repository. No credentials or fixture IDs are
included here. Earlier failed reports were retained, not replaced by the pass.

Production was **not run as part of this staging record**. Its later, separately
authorized run is recorded in [production import evidence](2026-11-10-production-import-evidence.md).
