# Gate A Canary And Tenant Evidence

Status: **PACKAGED CANARY PASSED; PLATFORM DEPLOYED; EXTERNAL RUNTIME CANARY PENDING**

Evidence date: 2026-07-30
Safe canary tenant: `ten-gate-a`
Safe canary deployment: `dep-gate-a-040`

## 1. Tenant Fixtures

The evidence fixture declares:

- entity type `document`;
- required vector metadata `tenantId` and `deploymentId`;
- optional response metadata `source` and `sourceVersion`;
- one private-runtime search source restricted to `ten-gate-a` and
  `dep-gate-a-040`.

The fixture paths are:

```text
fixtures/gate-a-ai-entity-config.yml
fixtures/gate-a-knowledge-sources.json
```

The entity fixture SHA-256 is:

```text
0ead66b1c6c17b8356adf59018a63f00213a24a256a17847313603102fa4fa2f
```

## 2. Positive Retrieval

Request:

```text
What release checks are required for Project Aurora before production launch?
```

Result:

```text
providerRequestId: rag-e9bbc5e3-4021-430b-b62b-c55b6f43d9b2
type: INFORMATION_PROVIDED
response path: RAG_ANSWER
sources: 1
source ID: gate-a-aurora
source tenant: ten-gate-a
score: 0.8209589719772339
```

The generated answer correctly required a penetration test and security-owner
approval.

## 3. Cross-Tenant Probe

A Tenant A assertion asked specifically for Tenant B Project Borealis.

```text
providerRequestId: rag-eae7a5c8-3a7e-4d42-922d-b5f4158d8da5
Tenant B marker leaked: false
Tenant B source attached: false
```

The only attached candidate belonged to `ten-gate-a`; the model explicitly
said Tenant B information was unavailable. The private source's native Lucene
filter and Platform's fail-closed post-filter therefore agree.

## 4. Projection And Auth Negatives

| Negative | Result |
| --- | --- |
| Missing required tenant/deployment metadata | HTTP 400, `PROJECTION_REJECTED / REQUIRED_CONTEXT_MISSING` |
| Missing Data Sync backend key | HTTP 401 |
| Missing chat assertion | HTTP 401 |
| Wrong backend key | HTTP 401 |
| Invalid assertion signature | HTTP 401 |
| Wrong assertion audience | HTTP 401 |
| Missing required chat scope | HTTP 403 |

The verified chat request carried tenant/deployment identity in the signed
assertion. The browser/request body did not own those values.

## 5. Gate Decision

The packaged tenant canary is green, and the Platform backend/UI now run the
Gate A control-plane code on isolated staging. Gate A still requires the same
tenant/auth/retrieval checks against an externally deployed runtime after the
canonical V04 cutover and fresh Platform release gate pass. Specialist
execution remains disabled.
