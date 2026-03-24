# Verification Playbook (Connector Demo + Runtime)

This document is a repeatable checklist to verify the runnable 2-service demo:

- **Customer Connector** (domain APIs + `POST /actions/execute`)
- **AI Fabric Runtime** (chat orchestration, action execution, and vector indexing)

It focuses on the two failure classes we hit most often:

- Runtime actions not loading (so everything becomes `OUT_OF_SCOPE`)
- Indexing not happening (vectors stay at `0`)

## 0) Fill These In

Set the two base URLs you are verifying:

```bash
export CONNECTOR_BASE_URL="https://<connector>.up.railway.app"
export RUNTIME_BASE_URL="https://<runtime>.up.railway.app"
```

Optional: if you enabled runtime admin auth (`app.admin.api-key`), set:

```bash
export RUNTIME_ADMIN_API_KEY_HEADER="X-ADMIN-API-KEY"
export RUNTIME_ADMIN_API_KEY="<secret>"
```

Optional: if you enabled connector admin auth (`connector.auth.api-key`), set:

```bash
export CONNECTOR_ADMIN_API_KEY_HEADER="X-AIFABRIC-API-KEY"
export CONNECTOR_ADMIN_API_KEY="<secret>"
```

If you want to disable admin protection on the connector even when `/actions/execute` is protected:

```bash
export CONNECTOR_ADMIN_AUTH_ENABLED="false"
```

## 1) Health Checks

```bash
curl -sS "${RUNTIME_BASE_URL}/actuator/health"
curl -sS "${CONNECTOR_BASE_URL}/actuator/health"
```

Expected: `{"status":"UP"}` for both.

## 2) Verify Runtime “Vector Spaces” (Entity Config Loaded)

Runtime should expose the Data Sync API and list configured vector spaces:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/ai/data-sync/vector-spaces"
```

Expected (connector demo config): includes `product`, `review`, `policy`.

If this is `404`:

- Ensure `ai.data-sync.enabled=true`
- Ensure embeddings are enabled (in the demo config this is tied to `OPENAI_ENABLED=true`)

## 3) Verify Index Counts (Is Indexing Happening?)

Check the runtime’s vector index counts:

Without admin auth:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/overview"
```

With admin auth:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/overview" \
  -H "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}"
```

Expected: JSON with `countsByEntityType` and `totalVectors`.

## 4) Connector → Runtime Indexing (Product)

This verifies the event-based indexing flow: connector writes a product, then the runtime gets an upsert.

1) Create a product in the connector:

```bash
curl -sS -X POST "${CONNECTOR_BASE_URL}/api/products" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "SKU_VERIFY_1",
    "name": "Verification Laptop",
    "description": "Created by verification playbook to test indexing.",
    "category": "gaming",
    "tags": "laptop,gaming",
    "imageUrl": "https://example.com/image",
    "price": 1299.00,
    "currency": "USD",
    "inStockQty": 5
  }'
```

2) Wait briefly, then re-check runtime counts:

Without admin auth:

```bash
sleep 1
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/overview"
```

With admin auth:

```bash
sleep 1
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/overview" \
  -H "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}"
```

3) Inspect indexed vectors (paged):

Without admin auth:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/vectors?entityType=product&offset=0&limit=50"
```

With admin auth:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/vectors?entityType=product&offset=0&limit=50" \
  -H "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}"
```

Expected: a vector with `entityId: "SKU_VERIFY_1"`.

If the product is created in the connector but runtime counts do not change:

- Ensure connector indexing is enabled:
  `CONNECTOR_INDEXING_ENABLED=true`

- Ensure the connector points at the runtime with an absolute URL (must include scheme):
  `CONNECTOR_INDEXING_RUNTIME_BASE_URL=https://...`

- Confirm runtime Data Sync endpoint is reachable from the connector (connector logs will show failures).

## 5) Connector → Runtime Indexing (Review)

Create a review in the connector:

```bash
curl -sS -X POST "${CONNECTOR_BASE_URL}/api/reviews" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "u1",
    "sku": "SKU_VERIFY_1",
    "rating": 5,
    "text": "Great performance and build quality."
  }'
```

Then verify `review` count increments:

Without admin auth:

```bash
sleep 1
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/overview"
```

With admin auth:

```bash
sleep 1
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/overview" \
  -H "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}"
```

And inspect review vectors:

Without admin auth:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/vectors?entityType=review&offset=0&limit=50"
```

With admin auth:

```bash
curl -sS "${RUNTIME_BASE_URL}/api/admin/indexing/vectors?entityType=review&offset=0&limit=50" \
  -H "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}"
```

## 6) Reset / Clear (For Repeatable Testing)

### 6.1 Clear runtime vectors (runtime endpoint)

Without admin auth:

```bash
curl -sS -X POST "${RUNTIME_BASE_URL}/api/admin/migration/clear?confirm=true"
```

With admin auth:

```bash
curl -sS -X POST "${RUNTIME_BASE_URL}/api/admin/migration/clear?confirm=true" \
  -H "${RUNTIME_ADMIN_API_KEY_HEADER}: ${RUNTIME_ADMIN_API_KEY}"
```

### 6.2 Reset connector demo (connector endpoint, clears connector DB and can clear runtime vectors)

This endpoint is protected when:

- `connector.auth.api-key` is set, and
- `connector.admin.auth.enabled=true` (default)

To disable protection for demo resets only, set:

- `CONNECTOR_ADMIN_AUTH_ENABLED=false`

```bash
curl -sS -X POST "${CONNECTOR_BASE_URL}/api/admin/demo/reset" \
  -H "Content-Type: application/json" \
  -H "${CONNECTOR_ADMIN_API_KEY_HEADER}: ${CONNECTOR_ADMIN_API_KEY}" \
  -d '{ "confirm": true, "clearConnectorData": true, "clearRuntimeVectors": true }'
```

Backwards-compatible alias:

```bash
curl -sS -X POST "${CONNECTOR_BASE_URL}/api/admin/migration/clear" \
  -H "Content-Type: application/json" \
  -H "${CONNECTOR_ADMIN_API_KEY_HEADER}: ${CONNECTOR_ADMIN_API_KEY}" \
  -d '{ "confirm": true, "clearConnectorData": true, "clearRuntimeVectors": true }'
```

## 7) Action Wiring (Runtime → Connector) Quick Verification

There is no single “list actions” API by default. Two practical checks:

1) **Runtime logs**: the intent-extraction prompt includes an `ALLOWED ACTIONS` list.
   - If you only see `remove_vector` / `clear_vector_index`, your `ai-actions.yml` did not load.

2) **Connector reachability**:
   - Ensure `ACTIONS_CONNECTOR_BASE_URL` is absolute and includes scheme.
   - Good: `https://ai-fabric-framework-production-a247.up.railway.app`
   - Bad: `ai-fabric-framework-production-a247.up.railway.app`

Common symptom in runtime logs:

- `URI is not absolute` (base URL missing `https://` / `http://`)
- `Connector service is unavailable` (wrong URL, connector down, or network failure)
