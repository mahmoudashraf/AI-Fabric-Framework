# Acme Simulated External Support API

Temporary, stateless integration fixture for the second non-LoomAI hosted proof.
It has no framework dependency, business database, persistence or write endpoint.
It is not a production service or a new managed-service commitment.

Build from `product-templates/ai-support-workspace`:

```sh
docker build -f fixtures/service/Dockerfile -t acme-support-fixture:01054 .
docker run --rm -p 8118:8118 -e ACME_SUPPORT_API_KEY acme-support-fixture:01054
```

Set a dedicated random `ACME_SUPPORT_API_KEY` in the server environment, at least
16 characters. No key is supplied in source, build arguments or browser config.
Keep the same value in the owned deployment's `ACME_SUPPORT_ADAPTER_SERVICE_KEY`
secret. Neither the image nor `/healthz` reveals it. `PORT` defaults to `8118`.
Use the normal staging TLS route; do not expose this fixture over plaintext to a
remote connector.

Authenticated endpoints use header `X-ACME-SUPPORT-SERVICE-KEY`:

- `GET /api/support/workspace-status` -> `acme_get_workspace_status` observation.
- `GET /api/support/service-status` -> `acme_get_service_status` observation.
- `GET /documents/{approvedFilename}.txt` -> one of the three Acme policy files.
- `GET /healthz` is unauthenticated, returning only `UP` and `simulation: true`.

Responses preserve the frozen timestamps and status shape from
`../acme-read-observations.json`, adding `simulation: true`. Do not describe the
fixture timestamp as a fresh production observation. A delayed fictional import
and degraded fictional service are intentional so the specialist can distinguish
documentation from observations. Every response carries `X-Acme-Simulation: true`.

Bind the existing deployed HTTPS endpoints through the normal external profile:
`workspaceStatusUrl=https://<fixture-host>/api/support/workspace-status` and
`serviceStatusUrl=https://<fixture-host>/api/support/service-status`. Include the
exact host in the reviewed profile's `allowedHosts`. The Acme ACTION `1.0.1`
and its mounted mechanics TEMPLATE use normal offer bootstrap, endpoint approval
and credential binding; the initializer generates the hashed environment aliases.
Do not set custom Coolify environment variables or submit the offline compiler
fixture as a projection mechanism. See `../../MOUNTED_DEMO_QUALIFICATION.md`.
No generated routing patch or browser-side secret is needed. Approved text files may be copied into the
customer-owned document source; serving them here does not itself index them.

The simulator authenticates the dedicated connector, not real Acme end users.
One fictional workspace is served for this isolated proof. It accepts no target
IDs or query parameters and enables no CORS/browser access. Production adapters
must independently validate the verified caller and resource ownership; this
fixture is not evidence that such a customer adapter exists.

Run local endpoint checks without Docker:

```sh
node --test fixtures/service/server.test.mjs
```
