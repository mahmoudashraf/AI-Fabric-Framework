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
Its cleanup deletes fictional keys, archives the empty Brain and signs out.
No live result is implied until that hosted runner has completed.
