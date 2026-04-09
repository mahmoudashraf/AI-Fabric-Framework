# Platform Verification and Auth Troubleshooting Guide

Use this guide when a future LLM session needs to debug platform verification failures, hosted verification failures, runtime admin/auth failures, POC chat regressions, or vectorization drift after the new auth model rollout.

This guide is implementation-oriented. It is not a design plan.

Related references:

- `doc/Productization/future-work/Auth/AUTH_IMPLEMENTED_FLOW_GUIDE.md`
- `doc/Productization/future-work/Auth/AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

---

## 1. First Rule: Identify Which Layer Is Actually Failing

Do not start by assuming the problem is in the runtime, connector, or auth layer. Separate the failure surface first.

There are four common failure surfaces:

1. release verification evidence stored on the platform
2. platform-hosted verification runner execution
3. direct live verification from the repo scripts
4. the actual runtime, connector, or vectorization deployment state

These surfaces can disagree.

Example:

- a `POST_APPLY` verification run can fail because it observed the runtime while it was still serving the previous version
- a later `MANUAL_RERUN` can pass for the exact same release
- the deployment is then healthy, but the older failed evidence still appears in history

---

## 2. Fast Triage Order

Use this sequence.

1. Check the latest platform release state.
2. Check release verification runs.
3. Check hosted verification runs separately.
4. Run the verification script directly from the repo.
5. Inspect live runtime admin endpoints.
6. Inspect auth posture and auth-context alignment.
7. Only then decide whether the issue is release drift, hosted-runner drift, auth misalignment, runtime stale rollout, connector stale rollout, or vectorization sync failure.

---

## 3. What To Check First

### 3.1 Release state

Check:

- `GET /api/deployments/{deploymentId}/releases`

You are looking for:

- latest release id
- latest deployment version id
- `status`
- `verificationStatus`
- `provisioningStatus`

Good sign:

- latest release is `APPLIED_VERIFIED`

Warning sign:

- latest release is `APPLIED_VERIFICATION_FAILED`
- latest release points at version `A`, but runtime later appears to serve version `B`

### 3.2 Release verification runs

Check:

- `GET /api/deployments/{deploymentId}/verification-runs`

You are looking for:

- latest `POST_APPLY`
- latest `MANUAL_RERUN`
- whether both refer to the same `releaseId` and `deploymentVersionId`

Important interpretation:

- if `POST_APPLY` failed but `MANUAL_RERUN` later passed for the same release and version, the earlier failure is stale evidence, not an active runtime bug

### 3.3 Hosted verification runs

Check:

- `GET /api/deployments/{deploymentId}/hosted-verifications`

Hosted verification is a different lane.

Important interpretation:

- if direct verification passes but hosted verification fails, the likely issue is in the platform-hosted runner path, script bundle, or hosted context
- do not misdiagnose that as a deployment/runtime failure until you prove both lanes fail the same way

### 3.4 Direct live verification

Run from the repo:

```bash
PLATFORM_BASE_URL='https://ai-fabric-framework-production-324f.up.railway.app' \
PLATFORM_LOGIN_EMAIL='admin@gmail.com' \
PLATFORM_LOGIN_PASSWORD='admin' \
PLATFORM_DEPLOYMENT_ID='dep-xxxxxxxx' \
VERIFICATION_PROFILE='ecommerce' \
VERIFY_WRITE='false' \
APP_ADMIN_API_KEY='test' \
bash scripts/run-platform-deployment-verification.sh
```

If this passes while hosted verification fails, the platform-hosted runner is stale or misconfigured.

---

## 4. Common Failure Patterns

### 4.1 `runtime_config_matches_expected` or `runtime_prompt_config_matches_expected` failed

Meaning:

- the platform expected version `X`
- runtime admin overview still reported config artifact URLs from version `Y`

Check:

- latest release in `/api/deployments/{id}/releases`
- latest verification runs in `/api/deployments/{id}/verification-runs`
- runtime admin overview directly:
  - `GET {runtimeBaseUrl}/api/admin/overview`

Likely causes:

- early post-apply verification snapshot
- rollout still serving the previous runtime revision for a short time
- real stale runtime rollout

What to do:

1. compare failed `POST_APPLY` against latest `MANUAL_RERUN`
2. if runtime now serves the expected artifact URLs, trigger:
   - `POST /api/deployments/{deploymentId}/verification-runs/recheck`
3. if runtime still serves the wrong version, redeploy the active version

Where to look in code:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseVerificationService.java`
- `scripts/verify-ecommerce-deployment.sh`
- `scripts/verify-vector-deployment.sh`

### 4.2 Hosted verification failed, but direct repo verification passed

Meaning:

- deployment is probably fine
- hosted verification path is broken

Check:

- latest hosted run log output
- whether the hosted failure occurs before or after real checks
- whether the hosted log shows script-level errors such as:
  - `Argument list too long`
  - `command not found`
  - missing script/context/auth material

Likely causes:

- platform service is still running an older script bundle
- hosted verification context is missing required env or secret material
- platform-hosted runner hit a shell/Python execution issue

What to do:

1. compare hosted run output against direct script output
2. inspect the hosted execution log line that actually failed
3. fix the repo script or hosted context if needed
4. redeploy the platform service if the fix is already pushed but hosted logs still show old line numbers or old behavior

Where to look in code:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationExecutionService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationContextService.java`
- `scripts/run-platform-deployment-verification.sh`
- `scripts/verify-ecommerce-deployment.sh`
- `scripts/verify-vector-deployment.sh`

### 4.3 Runtime admin or connector admin proxy returns `401`

Meaning:

- runtime private auth material is missing, stale, or not accepted

Check:

- `GET {runtimeBaseUrl}/api/admin/auth/overview`
- deployment security configuration on the platform
- hosted verification context or POC runtime headers

Specifically verify:

- ingress mode is `VERIFIED_CONTEXT_REQUIRED`
- trusted backend API key is configured
- private assertion validation is configured
- issuer is accepted
- audience matches deployment id

Likely causes:

- missing `X-AIFABRIC-RUNTIME-API-KEY`
- missing `X-AIFABRIC-RUNTIME-AUTHORIZATION`
- wrong issuer or audience in private assertion
- runtime release was not reapplied after auth changes

Where to look in code:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimeRequestAuthResolver.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/admin/RuntimeConnectorAdminProxyController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationContextService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java`

### 4.4 POC or private-runtime chat returns `Access denied by policy.`

Meaning:

- auth passed into runtime
- but orchestration or remote authz still treated the caller as anonymous or unauthorized

Check:

- `result.metadata.authenticated`
- runtime auth context route:
  - `GET /api/chat/me/auth-context`
- remote authz behavior

Likely causes:

- verified subject was not mapped into orchestration identity
- remote authz contract still expected compatibility aliases or older fields
- secure routes were called correctly, but policy still saw no authenticated subject

Where to look in code:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/authz/RemoteHttpEntityAccessPolicy.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/AccessControlStep.java`

### 4.5 Runtime action executed, but connector returned `400`

Meaning:

- runtime/policy allowed the action
- but downstream connector routing did not receive the expected auth context or parameter shape

Example symptom:

- `view_cart` succeeds as an action selection
- connector call to `/api/carts/active` returns `400`

Likely causes:

- connector templates depend on `trace.authContext.subjectId`
- action executor only forwarded older trace fields

Where to look in code:

- `ai-infrastructure-module/ai-infrastructure-actions-connector/src/main/java/com/ai/infrastructure/intent/action/connector/ActionConnectorExecutor.java`
- `ai-infrastructure-module/ai-infrastructure-actions-connector/src/main/java/com/ai/infrastructure/intent/action/connector/ActionConnectorProtocol.java`

### 4.6 POC conversation lookup returns `404 Conversation not found`

Meaning:

- the live query returned a `conversationId`
- but runtime did not persist a stored transcript for that result

This is common for some immediate error/denial paths.

Do not misdiagnose this as:

- route missing
- connector missing
- auth route missing

Where to look in code:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPocChatService.java`
- `Platfrom/ui/src/pages/PocPage.tsx`
- `ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/pipeline/ConversationRecordingStep.java`

### 4.7 Vectorization shows `BOOTSTRAP_REQUIRED`, `RUNNING` forever, or zero indexed rows

Meaning:

- vectorization control plane may be configured, but ingestion may not have actually completed

Check:

- platform vectorization summary:
  - `GET /api/deployments/{deploymentId}/vectorization`
- runtime indexing overview:
  - `GET {runtimeBaseUrl}/api/admin/indexing/overview`
- latest vectorization run state

Likely causes:

- runner still writes to the wrong operational surface
- runtime-backed data-sync write path is broken
- sync timeout is too low for current batch size
- bootstrap or reindex never actually completed

Where to look in code:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/service/VectorizationRunnerService.java`
- `ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/service/ConnectorDataSyncTargetWriter.java`
- `ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/config/VectorizationRunnerProperties.java`

---

## 5. Auth New Model Misalignment Checklist

Yes, new auth model misalignment is often the hidden base cause.

When the failure involves `401`, `403`, policy denial, missing conversation ownership, missing connector subject data, or runtime admin proxy failures, run this checklist.

### 5.1 Runtime auth posture

Check `GET {runtimeBaseUrl}/api/admin/auth/overview`.

Verify:

- `ingressMode = VERIFIED_CONTEXT_REQUIRED` for private-runtime deployments
- `trustedBackendConfigured = true`
- `privateAssertionValidationConfigured = true`
- accepted issuers include the expected platform or POC issuer
- accepted audiences include the deployment id

### 5.2 Correct route family

Verified/private chat should use:

- `/api/chat/me/*`

Do not treat old caller-supplied identity routes as authoritative.

If a caller is still depending on raw `userId`, `ownerId`, or `sessionId` as identity input, that is a red flag.

### 5.3 POC and platform proxy paths

POC, hosted verification, and platform operational reads are now private-runtime callers.

They must send:

- `X-AIFABRIC-RUNTIME-API-KEY`
- `X-AIFABRIC-RUNTIME-AUTHORIZATION`

If either is missing or stale, runtime admin routes and secured private-runtime chat will fail.

### 5.4 Remote authz compatibility

Even when runtime auth is correct, remote authz can still be out of alignment.

Check whether:

- verified subject is being mapped into the orchestration context
- compatibility alias fields expected by the current remote authz service are still being emitted

### 5.5 Connector-private assumption

If the connector is private, external operational reads should go through runtime.

If a flow still expects direct connector access for:

- health
- overview
- config
- action catalog
- operational summaries

that can look like auth breakage even when the real issue is surface mismatch.

---

## 6. Minimum Live Commands To Use

Login:

```bash
curl -sS -c /tmp/platform.cookies \
  -H 'Content-Type: application/json' \
  -X POST 'https://ai-fabric-framework-production-324f.up.railway.app/api/platform/auth/login' \
  --data '{"email":"admin@gmail.com","password":"admin"}'
```

Latest releases:

```bash
curl -sS -b /tmp/platform.cookies \
  'https://ai-fabric-framework-production-324f.up.railway.app/api/deployments/dep-xxxxxxxx/releases?limit=5' | jq .
```

Release verification runs:

```bash
curl -sS -b /tmp/platform.cookies \
  'https://ai-fabric-framework-production-324f.up.railway.app/api/deployments/dep-xxxxxxxx/verification-runs?limit=5' | jq .
```

Hosted verification runs:

```bash
curl -sS -b /tmp/platform.cookies \
  'https://ai-fabric-framework-production-324f.up.railway.app/api/deployments/dep-xxxxxxxx/hosted-verifications' | jq .
```

Rerun release verification:

```bash
curl -sS -b /tmp/platform.cookies \
  -X POST \
  'https://ai-fabric-framework-production-324f.up.railway.app/api/deployments/dep-xxxxxxxx/verification-runs/recheck' | jq .
```

Run direct verification from the repo:

```bash
PLATFORM_BASE_URL='https://ai-fabric-framework-production-324f.up.railway.app' \
PLATFORM_LOGIN_EMAIL='admin@gmail.com' \
PLATFORM_LOGIN_PASSWORD='admin' \
PLATFORM_DEPLOYMENT_ID='dep-xxxxxxxx' \
VERIFICATION_PROFILE='ecommerce' \
VERIFY_WRITE='false' \
APP_ADMIN_API_KEY='test' \
bash scripts/run-platform-deployment-verification.sh
```

---

## 7. Where To Look In Code By Problem Type

### 7.1 Platform release and verification

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseVerificationService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentController.java`

### 7.2 Hosted verification execution

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationExecutionService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationContextService.java`
- `scripts/run-platform-deployment-verification.sh`
- `scripts/verify-ecommerce-deployment.sh`
- `scripts/verify-vector-deployment.sh`

### 7.3 Runtime auth and admin routes

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimeRequestAuthResolver.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/admin/RuntimeConnectorAdminProxyController.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/authz/RemoteHttpEntityAccessPolicy.java`

### 7.4 Platform private-runtime callers

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPocChatService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPocImportService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentHostedVerificationContextService.java`

### 7.5 Connector action execution

- `ai-infrastructure-module/ai-infrastructure-actions-connector/src/main/java/com/ai/infrastructure/intent/action/connector/ActionConnectorExecutor.java`
- `ai-infrastructure-module/ai-infrastructure-actions-connector/src/main/java/com/ai/infrastructure/intent/action/connector/ActionConnectorProtocol.java`

### 7.6 Vectorization

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/service/VectorizationRunnerService.java`
- `ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/service/ConnectorDataSyncTargetWriter.java`
- `ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/config/VectorizationRunnerProperties.java`

---

## 8. Safe Debugging Sequence

When taking action, prefer this order:

1. inspect release state
2. inspect verification runs
3. inspect hosted runs
4. run direct repo verification
5. inspect runtime auth overview
6. inspect runtime admin overview
7. inspect source-of-truth and vectorization state
8. only then rerun verification, redeploy active version, or hard reset rollout

Use destructive rollout reset only after proving the issue is not:

- stale verification evidence
- hosted-runner drift
- auth-model misalignment
- script-bundle drift on the platform service

---

## 9. Practical Interpretation Rules

- `POST_APPLY failed` plus `MANUAL_RERUN passed` for the same release usually means stale release evidence, not an active deployment failure.
- direct repo verification passing is stronger evidence of deployment health than a stale hosted run failure.
- hosted verification `exit 126` usually means a script execution problem, not a failed business or auth check.
- if a hosted run log shows old line numbers after a fix was pushed, the platform service likely has not redeployed yet.
- if runtime admin routes fail with `401`, auth new model misalignment is a top suspect.
- if runtime chat says authenticated but policy denies, check orchestration identity mapping and remote authz compatibility.
- if runtime action succeeds but connector returns `400`, inspect auth-context forwarding into connector routing/templates.

---

## 10. When To Suspect Auth New Model Misalignment First

Suspect auth-model misalignment immediately when the symptom is one of:

- runtime admin `401`
- connector admin proxy `401`
- secure chat policy denial
- POC works partially but action or conversation ownership breaks
- hosted verification can hit health but fails secured runtime-admin surfaces
- runtime and connector are up, but private-runtime behavior looks anonymous

If the symptom is instead:

- hosted runner `exit 126`
- stale `POST_APPLY` evidence with later passing rerun
- vectorization long-running sync with no auth failures

then auth may not be the primary problem.

Start with the lane-specific evidence first.
