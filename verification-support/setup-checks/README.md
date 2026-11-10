# Combined Smart Brain Setup Checks

## Contract

One customer/staff Updates action combines normal draft validation with
connection results. Saving a plugin installation, action/routing configuration
or model/search configuration starts CHANGED checks. No per-operation test
buttons, new AI fallback, framework release, runtime upgrade, database migration
or publication gate is introduced.

- `GET /api/deployments/{id}/setup-checks`: authorized metadata inspection; no
  external network, DNS or provider call.
- `POST` to the same path: editor-authorized `ALL`, `CHANGED` or `FAILED` mode.
- Results: VERIFIED / FAILED / NOT_TESTED with safe message, timestamp, HTTP
  status and using-capability names. No endpoint, header values, response body
  or raw upstream exception is returned.
- Five-minute in-memory evidence, bounded to 256 Brains; restart discards it.
  Current resolved endpoint/auth material determines fingerprint freshness.
  This is advisory saved-draft evidence, not a new release or health database.
- At most two concurrent combined runs, 40 service groups and a 25-second
  scheduling budget. A final in-flight HTTP check has its own four-second
  deadline. Remaining checks are explicitly untested, not silently retried.

## Probe Boundaries

Generic API tests require an explicit safe GET path and the same configured
headers. Credentials must be owned by the selected Brain, with no target/global
fallback. Provider secret resolution is reused; shared provider credentials may
only be sent to fixed official vendor hosts. Public DNS is validated and pinned;
TLS hostname validation remains active. No redirects, cookies, proxy routing,
token exchange, write operation or inference is performed. Upstream bodies are
not read or retained. Authentication is only as strong as the selected check
endpoint; a public health route proves reachability, not credential enforcement.

Current adapters: OpenAI model metadata and Pinecone/Qdrant/Weaviate read-only
metadata, explicit action/API-key HTTP profile paths. Milvus and target-bound
connections, managed inference refs, unsupported providers, token exchange and
document-source access remain visibly untested here and use existing binding,
preview, runtime or apply verification. No vector copying or source indexing.

Plugin installer `config.setupCheckPath` compiles to action `setup-check-path`
or HTTP profile `setupCheckPath`. Direct routes and the connector upstream expose
their optional path in the existing editor. This metadata is ignored by the
runtime connector's existing unknown-property contract; business routes, methods,
authorization and request templates are unchanged.

## Verification

The focused service tests exercise shared connections, changed-only caching,
credential/endpoint invalidation, failed-only retry, missing safe paths,
Brain/target/global credential isolation, editor permission rechecks, safe
serialization, no token exchange and metadata-only provider checks. HTTP client
tests exercise public DNS pinning, request/header bounds, private/special-address
denial, sanitization, status handling and transport isolation. Controller tests
cover Console roles versus machine identities and explicit run modes.

Compiler regression proves installer check metadata reaches the resolved HTTP
profile; existing compiler, secret and release UI regression suites remain in the
verification selection. Final local and hosted outcomes are recorded below after
execution, not inferred from this source checklist.

### Local Qualification - 10 November 2026

- 242 focused backend tests across seven classes passed, zero failures/skips:
  21 setup service, 152 transport, two controller, 20 configuration compiler,
  23 plugin compiler, 12 provider ownership and 12 customer-secret tests.
- 57 existing UI unit/integration checks passed, including ten real-browser
  settings scenarios. Seven new setup-check browser scenarios, 29 existing
  Updates journey groups and 33 capability/installation browser scenarios passed.
- Production UI build and `git diff --check` passed. Known existing large-bundle
  and runtime-config script warnings remain; no claim of a framework-wide run.
- Initial harness failures were missing auth/API fixtures, changed command
  expectations, and obsolete collapsed/renamed editor selectors. Fixtures now
  assert the extra CHANGED/ALL check explicitly, without weakening the one-save,
  no-write-on-failure or publication/target checks. One shell timing run failed;
  the instrumented full rerun passed without a production navigation change.
- The stale-save regression exposed an actual existing editor issue: the 409
  handler replaced query data and thereby reset the claimed retained buffer.
  It now retains that candidate separately until explicit Discard, reusing the
  same draft/version contract. Requested assistant refresh still adopts data.

Desktop/light/dark/mobile captures are private temporary output, not committed
assets. `qualify-ui.mjs` uses a normal customer, creates an empty Brain and a
fictional key, checks a public LoomAI health GET and a protected read denial,
then verifies auto-save checks, failed-only retry and credential invalidation.
The public health GET proves reachability only, not credential enforcement.
It additionally uses the test customer's short-lived bearer, sent only to the
same Platform hostname, to prove actual authenticated read success, then rotates
to an invalid value and back. This temporary scoped key is deleted in cleanup;
no identity value, body or provider credential enters the report or Git.
Hosted screenshots exposed inline-detail clipping inside the Updates pane.
Details now open in the existing right-drawer pattern with a bounded scrollable
list and fixed footer/close control. The seven focused browser scenarios and
production build were repeated for this presentation-only correction. Initial
captures remain private historical evidence, not proof of the final layout.
Its cleanup deletes fictional keys, archives the empty Brain and signs out.
No live result is implied until that hosted runner has completed.

### Hosted Closeout - 10 November 2026

Both native environments are healthy and qualified on these exact images:

- Backend source `6e1021210b5471bf4a08a3d91d70237dd781b926`, digest
  `sha256:54b3ffd893579a7411f9ba915def324e959fb0ba5d0e46329a3586b065529de3`.
- Console source `ba602061afe71d27c7408b54631946deaf2eb3ba`, digest
  `sha256:19c45bc9700058b22c63c0f4c7806481f5e4c109a6952f9b1ee36dd15cce0d70`.
- CI `42834435905`: both initial images passed. CI `42836223842`: final
  Console image passed, backend correctly skipped for the UI-only correction.
- Exact completed Coolify jobs: staging backend `i3f01fh4m7u8uw2jmpex0oga`,
  staging final Console `gg1ugp6y3ypqw96nup09ro2u`, production backend
  `np30u9g76r7o6yqm0j25ng0p`, production Console `kmf08yiffmarncutaizs8r04`.

Final ordinary-customer hosted runs passed all five groups on each environment:
one real GET shared by read/write capabilities with no probe on navigation;
real connection-editor save -> CHANGED check and protected-endpoint rejection;
failed-only retry and authenticated read success; actual key rotation invalidating
cached success; mobile layout with no publication/provisioning/inference.
Zero page errors. A temporary copy of the fixture customer's short-lived bearer
was confined to its own Brain and sent only to that same Platform's protected
read endpoint, then deleted. No upstream business action was called.

Final raw reports/screenshots:

- `~/.codex/private/setup-checks-20261110-staging-final/`
- `~/.codex/private/setup-checks-20261110-production-final/`

Final desktop/mobile drawer captures were inspected after animation completion.
Initial two staging runs passed behavior but predated the drawer correction;
their captures are historical and retained privately. All four canary Brains
(`dep-e8f2bdad`, `dep-9dc32ed2`, `dep-2b8a5a6f`, `dep-7e442271`) were archived,
all test keys deleted, and all fixture browser sessions signed out. No runtime,
vector service, customer data store, version/release or provider allowance was
created or reset. Anonymous access to the new hosted API returned HTTP 401.

Exact native image/source/health receipts remain in
`~/.codex/private/loomai-feedback-loop-20261110/`; the reused helper's
`page-aware-deploy` filename is historical. Framework remains 0.8.46, and private
authoring/customer AI runtime images are unchanged. Later documentation commits
are not new serving-image sources. Unrelated dirty files remain untouched.
