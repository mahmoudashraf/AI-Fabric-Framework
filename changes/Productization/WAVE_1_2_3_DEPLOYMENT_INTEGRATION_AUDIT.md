# Wave 1, 2, 3 Deployment Integration Audit

Status: audit note (2026-03-31)

Purpose:

- verify which Wave 1, 2, and 3 platform features are truly driving runtime and REST connector deployments
- separate control-plane-only features from deployment-integrated features
- identify gaps where the platform looks more capable than the live Railway/runtime/connector path actually is

---

## 1) Executive Summary

The prompt-management gap was real, and it was not the only integration gap.

The current branch now has a stronger end-to-end path for:

- deployment source repository and branch selection
- published actions, entities, routing, and prompt artifacts
- runtime and connector public URL provisioning
- CORS, admin API key, and `AUTHZ_BASE_URL` propagation
- deployment verification against runtime and connector live endpoints
- governed remediation actions against live Railway/runtime/connector surfaces

However, some newer platform fields still behave more like modeled metadata or governance guidance than true deployment controls.

The provider/security deployment gap is now closed on this branch.

The most important remaining gaps are:

1. Wave 3 "verification gate" is not a true pre-apply gate yet
2. several Wave 3 "source of truth" views are based on platform-generated plan data, not Railway live read-back

---

## 2) By-Wave Assessment

### Wave 1

Wave 1 is mostly control-plane and operator-workspace work.

That includes:

- deployment workspace shell
- assignment and visibility model
- approvals and guardrails
- grid filters and operator views
- activity and audit context

These features are not supposed to change runtime or connector deployment contents directly.

Conclusion:

- no runtime/connector deployment gap here by design
- these items are control-plane foundations, not deployment payload features

### Wave 2

Wave 2 mixes control-plane and runtime-connected iteration workflows.

Confirmed integrated:

- deployment POC chat uses the real runtime path
- session-scoped hot apply reaches runtime request metadata
- prompt bundle now publishes and applies into runtime via `AI_PROMPTS_DEPLOYMENT_CONFIG_FILE`
- curated module selection now seeds deployment prompt bundle and runtime curated-pack metadata

Main evidence:

- [DeploymentService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java)
- [DeploymentArtifactService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentArtifactService.java)
- [RailwayProvisioningPlanService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java)
- [ChatRuntimeController.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java)
- [RuntimeDeploymentPromptConfigService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/config/RuntimeDeploymentPromptConfigService.java)

Conclusion:

- Wave 2 is mostly aligned after prompt deployment was fixed
- remaining Wave 2 items are mostly operator workflow, not deployment-payload gaps

### Wave 3

Wave 3 is where most remaining integration drift exists.

Some items are fully real and deployment-connected.
Some items are only partially true because they summarize generated plan data instead of the live provider state.
Some editable fields are not yet authoritative deployment knobs.

---

## 3) Confirmed Features That Really Drive Runtime / Connector Deployments

These are genuinely wired into the live path:

1. Source repository and branch overrides
   Evidence:
   [DeploymentSourceResolver.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentSourceResolver.java)
   [RailwayApiProvisioningProvider.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayApiProvisioningProvider.java)

2. Published actions, entities, routing, and prompt artifacts
   Evidence:
   [DeploymentConfigCompiler.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java)
   [DeploymentArtifactService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentArtifactService.java)
   [RailwayProvisioningPlanService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java)

3. Runtime and connector environment propagation for key live settings
   Current confirmed env propagation:
   - `AI_ACTIONS_CATALOG_PATH`
   - `AI_CONFIG_DEFAULT_FILE`
   - `AI_PROMPTS_DEPLOYMENT_CONFIG_FILE`
   - `ACTIONS_CONNECTOR_BASE_URL`
   - `AUTHZ_BASE_URL`
   - `APP_ADMIN_API_KEY`
   - `CORS_ALLOWED_ORIGINS`
   - `CORS_ALLOWED_ORIGIN_PATTERNS`
   - `CORS_ALLOW_CREDENTIALS`
   - connector runtime proxy env vars
   Evidence:
   [RailwayProvisioningPlanService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java)

4. Post-apply verification against real runtime and connector endpoints
   Evidence:
   [DeploymentReleaseExecutionService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseExecutionService.java)
   [DeploymentReleaseVerificationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseVerificationService.java)

5. Governed remediation actions against live provider/runtime/connector surfaces
   Evidence:
   [DeploymentRemediationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentRemediationService.java)
   [RailwayGraphqlClient.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayGraphqlClient.java)

---

## 4) Confirmed Gaps

### Gap A: Provider profile fields now compile into the live Railway/runtime deployment

This branch now compiles the managed provider profile into real deployment inputs:

- `llmProvider`
- `embeddingProvider`
- `vectorStrategy`
- `runtimeProfile`
- `connectorProfile`
- `qdrantHost`
- `qdrantPort`
- `qdrantGrpcPort`
- `qdrantPreferGrpc`

Live Railway/runtime behavior now receives:

- canonical provider env such as `AI_PROVIDERS_LLM_PROVIDER`, `AI_PROVIDERS_EMBEDDING_PROVIDER`, and `AI_VECTOR_DB_TYPE`
- provider-specific credentials and defaults for platform-managed OpenAI and Anthropic
- ONNX embedding enablement inside the runtime package
- Qdrant host/port/grpc/api-key wiring when `vectorStrategy=qdrant`
- runtime profile and connector profile compilation into live runtime/connector behavior

Status:

- closed for the current platform-managed support matrix on this branch
- future expansion to Azure/Cohere/Gemini/REST embeddings is still a follow-up, but the currently exposed managed matrix is authoritative

Main evidence:

- [ManagedDeploymentProfileCatalog.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/ManagedDeploymentProfileCatalog.java)
- [ProvidersPage.tsx](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/pages/ProvidersPage.tsx)
- [DeploymentServiceConfigModelService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentServiceConfigModelService.java)
- [RailwayProvisioningPlanService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java)
- [ai-fabric-runtime/pom.xml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/pom.xml)
- [application.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/resources/application.yml)

### Gap B: `authzMode` and `connectorApiKeyEnabled` now authoritatively drive deployment outputs

The security editor fields now compile into the live deployment path:

- `authzMode`
- `connectorApiKeyEnabled`

This now drives:

- compiled connector routing artifact policy for inbound API-key enforcement
- connector unauthenticated mode when connector API key enforcement is disabled
- runtime `AI_FABRIC_RUNTIME_AUTHZ_MODE`
- explicit runtime `AUTHZ_BASE_URL` fallback behavior
- connector/runtime secret requirements shown through secret usage and service config views

Status:

- closed for platform-managed runtime and connector deployments on this branch
- remaining security work is around pre-apply gating and live Railway drift read-back, not these two knobs themselves

Main evidence:

- [SecurityPage.tsx](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/pages/SecurityPage.tsx)
- [DeploymentDraftValidationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDraftValidationService.java)
- [DeploymentConfigCompiler.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java)
- [DeploymentSecretUsageService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentSecretUsageService.java)
- [DeploymentSecurityGovernanceService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentSecurityGovernanceService.java)
- [RailwayProvisioningPlanService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java)

### Gap C: Wave 3 "verification gate" is not a true gate yet

The platform now has strong verification visibility.

But apply is still:

1. queue release
2. provision
3. switch deployment live version
4. run verification after apply

There is no enforced pre-apply verification gate that blocks rollout based on readiness or previous failed checks.

So item 34 is currently:

- real post-apply verification
- real verification summary
- not yet a hard apply gate

Main evidence:

- [DeploymentReleaseExecutionService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseExecutionService.java)
- [DeploymentService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java)

### Gap D: "source of truth" and service views are mostly plan-based, not Railway read-back based

Several Wave 3 views are built from:

- deployment draft
- published version
- active version
- generated Railway plan
- stored provisioning details

They are not built from a live provider read-back of:

- current Railway service variables
- current Railway service instance settings
- current effective Dockerfile/rootDir configuration

This means provider-side manual drift can still exist while the platform shows a clean generated plan.

Main evidence:

- [DeploymentSourceOfTruthService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentSourceOfTruthService.java)
- [DeploymentServiceNavigationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentServiceNavigationService.java)
- [RailwayGraphqlClient.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayGraphqlClient.java)

### Gap E: UI/upstream "service" language is stronger than the actual provisioning scope

The Wave 3 service model includes:

- UI/browser surface
- upstream/store service

These are useful operational surfaces, but they are not provider-managed Railway services created by the platform in the same way runtime and REST connector are.

This is mainly a terminology gap, not a provisioning bug.

The UI should make it clearer that:

- `runtime` and `restConnector` are platform-provisioned services
- `uiSurface` is a client-facing integration surface
- `upstreamStore` is an external dependency surface

---

## 5) Recommended Fix Sequence

The next fixes should be:

1. make provider profile fields authoritative
   - wire `llmProvider`, `embeddingProvider`, and `vectorStrategy` into Railway env generation
   - support provider-specific secrets and enable flags
   - make `runtimeProfile` and `connectorProfile` change Dockerfile/root/service packaging behavior or remove them as editable deployment knobs

2. make security fields authoritative
   - derive connector ingress policy from one canonical model
   - derive runtime authz mode from one canonical model
   - compile that model into routing artifacts and runtime env so UI and deployment behavior cannot drift

3. turn verification gate into a true gate
   - add pre-apply readiness blocking rules
   - let privileged operators override with explicit audited reason if needed

4. add Railway live read-back
   - fetch current env vars, service instance settings, and service metadata
   - compare platform expected plan vs provider live state
   - surface real drift explicitly in source-of-truth and diagnostics

5. tighten service labeling
   - distinguish provisioned services from external/client surfaces in the UI and summary APIs

---

## 6) Conclusion

Wave 1 is mostly fine because it is control-plane work by design.

Wave 2 is now materially aligned after prompt deployment wiring was completed.

Wave 3 is valuable, but it still has a set of real "modeled vs actually deployed" gaps.

The most important principle going forward should be:

- if a field is editable in the deployment workspace and presented as a deployment control,
  it should either compile into runtime/connector/provider state or be clearly labeled as advisory metadata only
