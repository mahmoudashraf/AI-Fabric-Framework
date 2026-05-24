# 010.6 Private Runtime Asymmetric Assertion Auth Plan

Date: 2026-05-20

Status: proposed, ready for implementation.

Parent plans:

- `010_5_LOOMAI_CANONICAL_RUNTIME_BRIDGE_CONTRACT_STANDARDIZATION_PLAN.md`
- `010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md`
- `009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md`

Related guides:

- `Final_Documentation/Development_Guides/PRIVATE_RUNTIME_CUSTOMER_INTEGRATION_GUIDE.md`
- `Final_Documentation/Development_Guides/RUNTIME_AUTHORIZATION_AND_ACCESS_CONTROL_GUIDE.md`
- `Final_Documentation/Development_Guides/PRODUS_LOOMAI_STAGING_DEPLOYMENT_DEV_GUIDE.md`
- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`

## 1. Goal

Add production-grade asymmetric private-runtime assertions for external customer backends such as ProdUS.

The current staging path uses a shared HMAC signing secret:

- ProdUS backend signs `rpa1` assertions with a shared secret.
- LoomAI runtime verifies the same shared secret.
- This is live verified and acceptable for staging.

The target production posture is asymmetric:

- Customer backend owns the private key.
- LoomAI stores only the public key or trusted JWKS reference.
- Runtime verifies the assertion without being able to mint customer assertions.
- Key registration, rotation, revocation, and evidence are Platform-managed.

## 2. Non-Goals

- Do not change the canonical chat request/response contract from Plan 010.5.
- Do not expose runtime keys, private keys, HMAC secrets, Platform API keys, or provider credentials to browsers.
- Do not make Shopify, ProdUS, or future products parse answer text for auth or action failures.
- Do not require a new product-specific bridge contract.
- Do not remove the currently live HMAC path until each deployment is explicitly migrated.
- Do not store customer-owned private keys in LoomAI.

## 3. Current State

Runtime code currently supports HMAC-only private assertions:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimePrivateAssertionService.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/config/RuntimeAuthProperties.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/resources/application.yml`

Platform code currently signs HMAC private assertions for Platform-mediated calls:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/RuntimePrivateAssertionSigningService.java`

Current private assertion format:

```http
X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer rpa1.<base64url-json-payload>.<base64url-hmac-sha256-signature>
```

Current limitations:

- no `kid`.
- no algorithm header.
- no per-issuer public key registry.
- no JWKS support.
- runtime and caller both know the same signing secret.
- rotation requires coordinated secret replacement on both sides.

## 4. Target Contract

Keep the same HTTP header:

```http
X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <private-runtime-assertion>
```

Add an asymmetric token format:

```http
Bearer rpa2.<base64url-json-header>.<base64url-json-payload>.<base64url-signature>
```

Header:

```json
{
  "typ": "rpa",
  "alg": "ES256",
  "kid": "produs-staging-2026-05"
}
```

Payload keeps the existing private runtime claim shape:

```json
{
  "sub": "produs-user-123",
  "subjectType": "END_USER",
  "authMode": "PRIVATE_RUNTIME_BACKEND_MEDIATED",
  "callerType": "TRUSTED_BACKEND",
  "sessionId": "produs-session-123",
  "deploymentId": "dep-7706fafb",
  "customerId": "produs-staging",
  "tenantId": "produs-tenant-1",
  "iss": "produs-staging-backend",
  "aud": "dep-7706fafb",
  "exp": "2026-05-20T12:00:00Z",
  "scopes": ["chat:query", "chat:suggestions", "chat:conversations"]
}
```

Signing input:

```text
base64url(header) + "." + base64url(payload)
```

Supported algorithms:

- P0: `ES256`
- P0 alternate: `RS256`
- P1 optional: `EdDSA`, only if the runtime JDK and deployment crypto posture support it consistently

Rules:

- `alg=none` is always rejected.
- Header-provided `jwk`, `jku`, `x5u`, or remote key material is always ignored or rejected.
- `kid` is required for asymmetric assertions.
- key lookup is by deployment id, issuer, audience, and `kid`.
- payload `iss` and `aud` must still pass runtime accepted issuer/audience checks.
- `exp` remains an ISO-8601 UTC instant string.
- clock skew should be configurable, default `60s`.

## 5. Runtime Architecture

Introduce a verifier abstraction in `ai-fabric-runtime`:

```java
interface RuntimePrivateAssertionVerifier {
    boolean supports(RuntimePrivateAssertionToken token);
    RuntimeAuthContext verify(RuntimePrivateAssertionToken token);
}
```

Implementations:

- `RuntimeHmacPrivateAssertionVerifier`
  - supports current `rpa1`.
  - uses existing `AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY`.
- `RuntimeAsymmetricPrivateAssertionVerifier`
  - supports `rpa2`.
  - resolves public key by issuer/audience/kid.
  - verifies `ES256` and `RS256` signatures.
- `RuntimePrivateAssertionTokenParser`
  - parses `rpa1` and `rpa2`.
  - rejects malformed segment counts, oversized headers/payloads, unknown prefixes, and invalid base64url.

`RuntimePrivateAssertionService` becomes orchestration:

- parse token.
- select verifier.
- verify signature.
- parse payload into `RuntimeAuthContext`.
- run existing context validation.
- enforce scope and subject rules exactly as today.

## 6. Runtime Key Configuration

P0 supports static deployment configuration because managed runtime deployments already receive Coolify env from Platform.

Add one JSON config env:

```text
AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_PUBLIC_KEYS_JSON=[
  {
    "issuer": "produs-staging-backend",
    "audience": "dep-7706fafb",
    "kid": "produs-staging-2026-05",
    "algorithm": "ES256",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----...",
    "status": "ACTIVE",
    "notBefore": "2026-05-20T00:00:00Z",
    "notAfter": "2026-08-20T00:00:00Z"
  }
]
```

Optional P1 JWKS config:

```text
AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_JWKS_JSON=[
  {
    "issuer": "produs-production-backend",
    "audience": "dep-prod...",
    "jwksUri": "https://api.produs.example/.well-known/jwks.json",
    "allowedAlgorithms": ["ES256"],
    "cacheSeconds": 300
  }
]
```

Security requirements:

- Public keys are not secrets, but updates are privileged and audited.
- Private keys are never stored in LoomAI runtime or Platform for external customers.
- JWKS URLs, if supported, must be HTTPS, SSRF-validated, timeout bounded, redirect disabled, and cached.
- Runtime startup must fail closed when asymmetric auth is required but no active key matches required issuer/audience.
- Header names remain static and not caller-configured from browser input.

## 7. Platform Management Architecture

Platform owns registration and lifecycle, not raw runtime env editing.

Add Platform-managed registry records:

```text
runtime_private_assertion_key
- id
- deployment_id
- customer_id
- issuer
- audience
- key_id
- algorithm
- public_key_pem
- jwks_uri
- status: DRAFT | ACTIVE | RETIRING | DISABLED | EXPIRED
- not_before
- not_after
- allowed_scopes
- created_by
- created_at
- updated_by
- updated_at
- disabled_reason
```

Platform operations:

- register public key for deployment.
- activate key after syntax and smoke verification.
- mark old key `RETIRING` during overlap.
- disable compromised key immediately.
- emit audit event for every key lifecycle mutation.
- render the runtime env payload deterministically for Coolify-managed runtime services.
- force-recreate/redeploy runtime after key config changes.
- verify the runtime active key set through `/api/chat/me/auth-context` with a signed test token supplied by the customer or generated only for Platform-owned keys.

## 8. Customer Key Ownership Flow

For external customers such as ProdUS:

1. Customer generates keypair in their environment.
2. Customer sends LoomAI only:
   - issuer.
   - audience/deployment id.
   - algorithm.
   - `kid`.
   - public key PEM or JWKS URL.
   - desired activation window.
3. LoomAI registers the public material in Platform.
4. Platform updates runtime env and redeploys/recreates the runtime.
5. Customer signs `rpa2` assertions with their private key.
6. LoomAI verifies auth-context/query/suggestions live.

Example ProdUS backend env:

```text
LOOMAI_ENABLED=true
LOOMAI_INTEGRATION_MODE=BACKEND_MEDIATED_PRIVATE_RUNTIME
LOOMAI_AUTH_MODE=PRIVATE_RUNTIME_ASSERTION
LOOMAI_ASSERTION_FORMAT=rpa2
LOOMAI_ASSERTION_ALGORITHM=ES256
LOOMAI_ASSERTION_ISSUER=produs-staging-backend
LOOMAI_ASSERTION_AUDIENCE=dep-7706fafb
LOOMAI_ASSERTION_KEY_ID=produs-staging-2026-05
LOOMAI_ASSERTION_PRIVATE_KEY_PATH=/run/secrets/produs-loomai-private-key.pem
```

## 9. Shopify Safety And Migration

This plan must not break Shopify Companion.

Rules:

- Keep current Shopify runtime/bridge chat payload contract from Plan 010.5.
- Keep current HMAC `rpa1` verification enabled for deployments that have not opted into asymmetric-only auth.
- Add deployment-level auth policy:
  - `HMAC_ONLY`
  - `ASYMMETRIC_ONLY`
  - `HMAC_AND_ASYMMETRIC_DURING_ROTATION`
- Shopify staging remains on current verified path until a controlled migration is scheduled.
- Shopify production should eventually use either Platform-owned asymmetric signing or a product-service-owned keypair, but this is separate from ProdUS staging enablement.

## 10. Release Gates

P0 unit gates:

- valid `rpa2` `ES256` assertion passes.
- valid `rpa2` `RS256` assertion passes if `RS256` is enabled.
- wrong signature fails `401`.
- wrong issuer fails `401`.
- wrong audience fails `401`.
- unknown `kid` fails `401`.
- disabled key fails `401`.
- expired key fails `401`.
- expired assertion fails `401`.
- future `notBefore` key fails `401`.
- missing required `kid` fails `401`.
- `alg=none` fails `401`.
- header `jku`/inline `jwk` does not trigger remote or inline key trust.
- missing `chat:query` still fails query with `403`.
- public `Authorization` plus private runtime authorization still fails with `400`.
- existing `rpa1` HMAC tests continue passing for HMAC-enabled deployments.

P0 Platform gates:

- register public key through Platform service/API.
- duplicate active `issuer/audience/kid` is rejected.
- key rotation supports active plus retiring overlap.
- disabled key is no longer emitted into runtime config.
- Coolify env render includes public-key config without private-key material.
- release verification reports configured asymmetric key ids without printing PEM if policy requires redaction.

P0 live gates:

- ProdUS signs `rpa2` from its backend or a customer-owned smoke tool.
- runtime `/api/chat/me/auth-context` accepts `rpa2`.
- runtime `/api/chat/me/query` accepts `rpa2`.
- runtime `/api/chat/me/suggestions` accepts `rpa2`.
- wrong `kid` and wrong issuer fail live.
- current Shopify staging query still works after runtime deploy.

## 11. Implementation Slices

### Slice 1: Runtime verifier abstraction

Files:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimePrivateAssertionService.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/config/RuntimeAuthProperties.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/resources/application.yml`
- runtime auth tests under `ai-infrastructure-module/ai-fabric-runtime/src/test/java/...`

Deliverable:

- `rpa1` behavior unchanged.
- parser/verifier split added.
- `rpa2` asymmetric verification unit-tested.

### Slice 2: Platform key registry

Files:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/...`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/secret/...`
- `Platfrom/backend/src/main/resources/db/migration/...`
- deployment provider tests.

Deliverable:

- Platform can register public keys and emit runtime config.
- Coolify provider can update runtime env and trigger redeploy/recreate.

### Slice 3: Verification and operational scripts

Files:

- `scripts/run-platform-deployment-verification.sh`
- `scripts/verify-vector-deployment.sh`
- new focused script if needed: `scripts/verify-private-runtime-asymmetric-auth.sh`

Deliverable:

- local and hosted verification can generate or accept a customer-signed `rpa2`.
- output redacts all private material.

### Slice 4: Documentation and handoff

Files:

- `Final_Documentation/Development_Guides/PRIVATE_RUNTIME_CUSTOMER_INTEGRATION_GUIDE.md`
- `Final_Documentation/Development_Guides/RUNTIME_AUTHORIZATION_AND_ACCESS_CONTROL_GUIDE.md`
- `Final_Documentation/Development_Guides/PRODUS_LOOMAI_STAGING_DEPLOYMENT_DEV_GUIDE.md`
- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`

Deliverable:

- HMAC documented as staging/internal.
- asymmetric documented as production/external standard.
- private keys stay customer-owned.
- public key registration steps are explicit.

## 12. Verification Commands

Minimum expected commands after implementation:

```bash
mvn -f ai-infrastructure-module/ai-fabric-runtime/pom.xml -q -Dtest='*PrivateAssertion*Test,*Runtime*Auth*Test' test
mvn -f ai-infrastructure-module/ai-fabric-runtime/pom.xml -q test
mvn -f Platfrom/backend/pom.xml -q -Dtest='*RuntimePrivateAssertion*,*Deployment*Verification*,*Coolify*Provider*' test
mvn -f Platfrom/backend/pom.xml -q test
bash -n scripts/run-platform-deployment-verification.sh
bash -n scripts/verify-vector-deployment.sh
bash -n scripts/verify-private-runtime-asymmetric-auth.sh
```

Live verification:

```bash
curl -fsS "${RUNTIME_BASE_URL}/actuator/health"
curl -fsS \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${RUNTIME_TRUSTED_BACKEND_API_KEY}" \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${RPA2_ASSERTION}" \
  "${RUNTIME_BASE_URL}/api/chat/me/auth-context"
```

## 13. Done Definition

The plan is done when:

- Runtime verifies customer-owned `rpa2` asymmetric assertions.
- Platform can register, rotate, disable, and audit public keys.
- Coolify-managed runtimes receive reproducible public-key config from Platform.
- ProdUS staging can run live chat using customer-owned asymmetric signing.
- HMAC staging remains functional for deployments not yet migrated.
- Shopify Companion staging still passes smoke and quality checks after the runtime change.
- Guides explain exactly when to use HMAC versus asymmetric auth.
- No raw private keys or shared secrets are committed, printed, or exposed in browser/API responses.

## 14. Open Decisions

1. Choose the default production algorithm: `ES256` is smaller and efficient; `RS256` is more universally familiar. Recommendation: support both, default to `ES256`.
2. Decide whether P0 uses static public key JSON only or includes JWKS URI support. Recommendation: static public key JSON for P0, JWKS in P1 after SSRF/cache hardening.
3. Decide whether Platform UI exposes public key registration in the first implementation or keeps it operator/API-only. Recommendation: operator/API-only for P0, UI in a later partner/admin hardening slice.
4. Decide Shopify migration timing separately from ProdUS. Recommendation: do not migrate Shopify until ProdUS asymmetric auth is live-proven and the Shopify release gate can be rerun.
