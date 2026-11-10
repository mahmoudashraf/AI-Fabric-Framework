# 010.37 Production Import HTTP Evidence

## Actual Result

- Environment: production, `https://api.loomai.pro/api/configuration-assistant`.
- Run: `2026-11-10T02-45-12-331Z-1c99e7d0`.
- Started: **2026-11-10T02:45:12.332Z**. Finished: **2026-11-10T02:45:31.229Z** (UTC).
- **PASS_IMPORT_ONLY: 26 passed, 0 failed, 0 blocked**; 133 backend HTTP requests.
- Readiness: HTTP 200, `enabled:true`, `ready:true`, `documentFetchEnabled:true`.
- Zero model calls or existing-resource writes. No publish/apply, indexing, runtime, connector, or infrastructure-provisioning requests.
- Both disposable sessions were deleted; subsequent reads returned 404. No session/source cleanup failures.

## Isolation And Archive

One separate configuration-only DRAFT Brain with a fresh unbound tenant was created
at **2026-11-10T02:45:09.552Z** (HTTP 201). Only MODELS and CONNECTIONS assistant
tasks on that Brain were allowed. Main's production Brain and ACTIONS/BEHAVIOUR
sessions were not used, cancelled, or deleted. Fixture IDs and the genuine
customer bearer token remained private.

The draft response hash matched before/after all import checks and again before
archive. Safety checks confirmed an unused DRAFT with zero versions, releases,
or verification runs and no runtime, connector, or pending deletion.

The ordinary archive API returned HTTP 200. The canary was archived at
**2026-11-10T02:45:39.760759559Z**; a read-only workspace check reconfirmed persisted
ARCHIVED state at **2026-11-10T02:47:09.441Z**, still with zero versions/releases
and no runtime/connector. Cleanup performed one own-canary metadata archive write,
not an infrastructure teardown. The archived configuration/tenant metadata is
retained; no restore or hard deletion was attempted.

## Passed Checks

1. OpenAPI 3.0.3 JSON: 2 operations; parameters/security/body/local refs and declared credential/header redaction.
2. OpenAPI 3.1.0 JSON: 2 operations; nullable union and boolean schema preserved.
3. Swagger 2.0 JSON: 1 operation; formal body/response conversion with visible warning.
4. OpenAPI 3.0.3 YAML: 1 operation.
5. OpenAPI 3.1.0 YAML: 1 operation.
6. JSON sample: credential redacted, business counter preserved, no invented operations.
7. Text/Markdown: line anchors, declared header redaction, ordinary prose preserved.
8. cURL: header/cookie/user/OAuth credential redaction; inert text, not execution.
9. Text PDF: extracted text, page anchor and credential redaction.
10. Four external network/file refs visibly unresolved in warnings and projection.
11. Duplicate/absent operation IDs normalized to unique source/method/path identities.
12. Source reuse/removal: IDs/content preserved, repeat reuse deduplicated, original survives copy removal, explicit catalogue selection IDs valid.
13. Malformed JSON rejected with HTTP 400 and no source persisted.
14. YAML alias rejected with HTTP 400 and no source persisted.
15. PDF without text rejected with HTTP 400 and no source persisted.
16. File of 2 MiB + 1 byte rejected with HTTP 400 and no source persisted.
17. [Official fictional Swagger sample](https://petstore3.swagger.io/api/v3/openapi.json) fetched over HTTPS: 19 operations, 17,116 retained characters, 1 visible warning. No sample operation invoked.
18. HTTP IPv4 loopback URL rejected with HTTP 400.
19. HTTPS IPv4 loopback URL rejected with HTTP 400.
20. HTTPS localhost URL rejected with HTTP 400.
21. HTTPS link-local metadata-address URL rejected with HTTP 400.
22. HTTPS IPv6 loopback URL rejected with HTTP 400.
23. HTTPS CGNAT-address URL rejected with HTTP 400.
24. HTTPS IPv6 ULA-address URL rejected with HTTP 400.
25. HTTPS port 444 URL rejected with HTTP 400.
26. Isolated Brain draft unchanged throughout import qualification by exact response hashes.

## Failure And Limits

There were **no failed production import checks or setup attempts**. The first
archive command exited 1 with the harness code `ARCHIVE_CHANGED_DRAFT_SETTINGS`:
its final check incorrectly required a readable draft after confirmed archive.
Read-only diagnosis at **2026-11-10T02:46:08.760Z** returned HTTP 400,
`BAD_REQUEST`, `Deployment is archived and cannot load draft.` This is intentional
archived-resource access denial, not a returned changed draft. The archive was not
retried or undone. Only the verification postcheck was corrected; the original
nonzero result and actual archive/read responses remain in private evidence.
No post-archive draft hash comparison is claimed because that read is denied.

Provider operation selection remains **NOT_EXERCISED**: catalogue IDs/payloads were
checked without a turn. Ref warnings/projections are not packet-level egress proof.
No-text PDF rejection is not OCR qualification. This is API-only, time-bounded
evidence, not UI, model quality, runtime allocation, or serving-image identity proof.

Runner: [import-hosted.mjs](import-hosted.mjs). After the archive-postcheck correction,
its SHA-256 is `65e6674e80f236ea9f65256a6ba124e3f49f0a4f9ad43c144e3873165e9f0fef`.
Syntax and offline guard checks passed, including 15 forbidden requests blocked
before transport and archive safety/access-denial tests. The 26 hosted import
checks were not changed or rerun by that postcheck correction. Raw evidence stays
outside the repository; no tokens or private resource/session IDs appear here.
Do not replay against the archived fixture or main's resources; repeat runs need
fresh, explicitly isolated disposable configuration.

Related: [26-check staging evidence](2026-11-10-staging-import-evidence.md).
