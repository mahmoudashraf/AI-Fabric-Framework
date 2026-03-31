# Multi-Cloud Provisioning Expansion Plan

Status: planning document (2026-03-30)

This document describes how to evolve the platform from its current **Railway-first provisioning model** into a **multi-cloud deployment platform** where the operator can choose a deployment target such as:

- Railway
- AWS
- Azure
- later: GCP and other providers

The goal is not only to support AWS as an alternative to Railway, but to do it in a way that remains extendable rather than adding one-off cloud-specific logic into the platform.

---

## 1) Executive Summary

### 1.1 Short answer

Yes, this can be made reasonably easy for the user.

The right user experience is:

- platform admin configures target profiles once
- operator creates a deployment and selects a target profile
- platform applies the same published version to the selected target

For the operator, the choice should feel like:

- `Railway Dev`
- `AWS App Runner Dev`
- `Azure Container Apps Dev`

not:

- “manually set 14 AWS parameters”
- “paste arbitrary branch names”
- “write cloud infrastructure configuration in the UI”

### 1.2 Engineering reality

This is **medium complexity**, not trivial, because the current implementation still assumes:

- one global provisioning mode:
  - `platform.provisioning.mode`
- one global deployment source:
  - repository
  - branch
- one provider-specific operational surface:
  - Railway

The platform already has a strong starting point:

- `DeploymentProvisioningProvider`
- provider selection at runtime
- Railway plan generation
- release progress tracking
- verification and diagnostics

But to support AWS and Azure correctly, we should first refactor from:

- `global platform provisioning mode`

to:

- `per-deployment target profile`

---

## 2) Current State

Current provisioning model in the platform:

- provider interface exists:
  - [DeploymentProvisioningProvider.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentProvisioningProvider.java)
- provider selection service exists:
  - [DeploymentProvisioningService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentProvisioningService.java)
- current properties are still global:
  - [PlatformProvisioningProperties.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/PlatformProvisioningProperties.java)
- current implemented providers:
  - `RAILWAY_STUB`
  - `RAILWAY_API`

This is a good foundation, but it is still optimized around one active target for the whole platform process.

---

## 3) Product Goal

The platform should support:

- one control plane
- many customer deployments
- each deployment choosing its own infrastructure target

Examples:

- deployment A -> Railway
- deployment B -> AWS
- deployment C -> Azure

That choice should be:

- versioned at the deployment level
- visible in the UI
- supported by verification and logs
- overridable by template/environment/profile

---

## 4) Recommended Design Direction

### 4.1 Do not model this as “mode”

The current `platform.provisioning.mode` is useful for the current implementation, but it is the wrong long-term abstraction for multi-cloud.

Instead, introduce:

- `deployment target profile`

Each deployment target profile should define:

- provider type
- environment name
- region
- source strategy
- credentials reference
- network/public URL policy
- service sizing defaults
- optional provider-specific settings

Example target profiles:

- `railway-dev`
- `aws-apprunner-dev-eu`
- `azure-container-apps-dev-uksouth`
- `aws-ecs-prod-eu`

### 4.2 Keep deployment config and deployment target separate

The deployment draft/version already owns:

- actions
- entity config
- routing
- providers
- security

Do not mix infrastructure target details into that same config blob.

Instead:

- deployment version = app/runtime config
- deployment target profile = infrastructure execution target

This keeps the product model clean.

### 4.3 Standardize around a provider-neutral service deployment contract

The platform should compile one generic deployment request into:

- `runtime service spec`
- `rest connector service spec`
- signed artifact URLs
- env var maps
- health expectations

The provider adapter translates that into:

- Railway project/service/env
- AWS App Runner service pair
- Azure Container Apps pair

---

## 5) Easiest AWS Path

### 5.1 Recommended first AWS target: App Runner

The easiest AWS target to add first is:

- **AWS App Runner**

Why:

- simple public web service model
- deploys containers from registry
- straightforward env vars
- public HTTPS URLs
- lower operational overhead than ECS/EKS
- good fit for the current platform model of:
  - one runtime service
  - one rest-connector service

Why not start with ECS/EKS:

- ECS Fargate is more flexible, but more infrastructure work
- EKS is much heavier operationally
- both are better later for enterprise/private networking, not for first optional AWS support

### 5.2 Future AWS path

After App Runner, add:

- **AWS ECS Fargate** for enterprise-style deployments

This should be a second AWS provider type, not a replacement for the first.

Recommended AWS provider sequence:

1. `AWS_APP_RUNNER`
2. `AWS_ECS_FARGATE`
3. optionally later: `AWS_EKS`

---

## 6) Best Matching Azure Path

Recommended first Azure target:

- **Azure Container Apps**

Why:

- very similar mental model to App Runner / Cloud Run
- container-based
- env-var friendly
- public URL by default
- lower overhead than AKS

Recommended Azure provider sequence:

1. `AZURE_CONTAINER_APPS`
2. optionally later: `AKS`

---

## 7) Best Matching GCP Path

Recommended first GCP target:

- **Cloud Run**

This gives a clear multi-cloud “managed container service” family:

- Railway
- AWS App Runner
- Azure Container Apps
- GCP Cloud Run

That is the best first extendable shape.

---

## 8) Key Architecture Change Required First

### 8.1 Move from global provider mode to deployment target profiles

Current:

- one platform process decides provider through:
  - `platform.provisioning.mode`

Target:

- each deployment points to a target profile:
  - `deployment.targetProfileId`

This is the most important change.

Without it, adding AWS or Azure only gives you:

- “run the entire platform in AWS mode”

instead of:

- “choose AWS for this deployment and Railway for that one”

### 8.2 Keep a platform default target

The platform should still have:

- one global default target profile

so operators can move fast.

But deployments must be able to override it.

---

## 9) Source Strategy: Git-Based vs Image-Based

### 9.1 Current state

Current Railway provisioning is GitHub + Dockerfile based.

That works for Railway, but it is not the best cross-provider abstraction.

### 9.2 Recommended target state

Introduce a provider-neutral source model:

- `GIT_SOURCE`
- `IMAGE_SOURCE`

### 9.3 Recommendation

For multi-cloud, standardize on:

- **OCI image deployment**

Meaning:

- CI builds runtime image
- CI builds rest-connector image
- platform provider adapters deploy image refs

Why:

- AWS App Runner wants container images
- Azure Container Apps wants container images
- GCP Cloud Run wants container images
- this is more portable than provider-specific Git-based build behavior

### 9.4 Transitional approach

Use two phases:

1. keep Railway on current Git-based flow
2. introduce image-based deployment source for multi-cloud
3. optionally move Railway to image-based too later

---

## 10) Proposed Data Model Changes

Add these platform concepts.

### 10.1 Provider type enum

Examples:

- `RAILWAY_API`
- `AWS_APP_RUNNER`
- `AWS_ECS_FARGATE`
- `AZURE_CONTAINER_APPS`
- `GCP_CLOUD_RUN`

### 10.2 Deployment target profile

Fields:

- `id`
- `name`
- `providerType`
- `environmentName`
- `region`
- `sourceStrategy`
- `credentialsRefId`
- `publicExposureMode`
- `runtimeSizingProfile`
- `connectorSizingProfile`
- `providerConfigJson`
- `active`

### 10.3 Deployment target selection

On deployment:

- `targetProfileId`

On template:

- optional default `targetProfileId`

### 10.4 Source reference

Introduce a reusable source model:

- `repository`
- `branch`
- or `runtimeImage`
- `connectorImage`
- optional `imageTag`
- optional `imageDigest`

### 10.5 Credentials reference

Provider credentials should not live in deployment draft config.

Create provider credential refs like:

- `railway-workspace-prod`
- `aws-platform-dev`
- `azure-platform-prod`

---

## 11) Backend Refactor Plan

### 11.1 Replace `supports(mode)` with provider type dispatch

Current provider selection is:

- `supports(String mode)`

Move toward:

- `supports(ProviderType type)`

or:

- direct registry by provider type

### 11.2 Add target-aware provisioning request model

Introduce a provider-neutral model such as:

- `DeploymentTargetContext`
- `ServiceDeploymentSpec`
- `DeploymentSourceSpec`
- `ProviderCredentialsContext`

The provider should receive a fully compiled plan, not re-derive everything from raw global properties.

### 11.3 Move provider settings out of global properties

Global properties should remain only for:

- platform defaults
- backend runtime behavior

Provider-specific deployment settings should move into:

- target profile config

### 11.4 Keep Railway preflight but generalize it

Current:

- Railway preflight is Railway-specific

Target:

- provider-specific preflight endpoints behind one UI surface:
  - Railway preflight
  - AWS preflight
  - Azure preflight

---

## 12) UI Plan

### 12.1 Platform admin screens

Add:

- `Target Profiles`
- `Provider Credentials`
- `Deployment Sources`

These are admin-only.

### 12.2 Operator flow

During deployment create/edit:

- choose target profile

In Revisions/Diagnostics:

- show provider type
- show region
- show deployment ids/URLs
- show provider-specific status

### 12.3 Keep user UX simple

Operators should choose:

- target profile

not:

- 20 raw cloud fields

---

## 13) Logging And Verification

Multi-cloud is only usable if diagnostics stay strong.

Current Railway-specific capabilities:

- Railway logs
- Railway preflight
- Railway provisioning details

Target:

- generic logs tab
- provider-specific log fetcher behind the same UI
- generic verification results
- provider-specific deployment identifiers

Provider contract should include:

- fetch service logs
- fetch deployment metadata
- fetch external base URLs

---

## 14) Security Model

### 14.1 Provider credentials

Cloud credentials must be stored as platform-managed secrets/credential refs.

Never in:

- deployment draft JSON
- provider config JSON
- versioned artifacts

### 14.2 Runtime/connector secrets remain platform-managed

These do not change by cloud provider:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`
- `PLATFORM_ARTIFACT_SIGNING_KEY`

### 14.3 Cloud identity model

Recommended:

- one platform-managed cloud credential per target profile set
- not customer-entered arbitrary credentials in V1

Later:

- support customer-owned cloud credentials if your business model requires BYOC deployment

---

## 15) Phased Implementation Plan

### Phase A: Provider-Neutral Target Model

Goal:

- remove the “single platform provisioning mode” limitation

Work:

- add `deployment target profile` model
- add `providerType`
- add `targetProfileId` to deployments/templates
- refactor provider dispatch away from global mode
- keep current Railway provider working through a Railway target profile

Done when:

- one deployment can target Railway profile A
- another deployment can target Railway profile B
- provider selection is no longer a global singleton decision

### Phase B: Provider-Neutral Source Model

Goal:

- support image-based deploys across providers

Work:

- add `GIT_SOURCE` and `IMAGE_SOURCE`
- add source profile model
- allow target profiles to choose supported source strategy
- keep Railway on `GIT_SOURCE` initially

Done when:

- provider adapters consume a normalized source spec

### Phase C: AWS App Runner Provider

Goal:

- make AWS the first real optional alternative to Railway

Work:

- add `AWS_APP_RUNNER` provider adapter
- add AWS preflight
- add AWS target profile config
  - region
  - runtime image
  - connector image
  - environment name
  - IAM/credential ref
- support two App Runner services:
  - runtime
  - rest connector
- support env injection
- support URL capture
- support logs surface

Done when:

- operator can create a deployment and choose `AWS App Runner Dev`
- publish/apply provisions both services
- platform verification passes

### Phase D: Admin UI For Target Profiles

Goal:

- make multi-cloud manageable from the UI

Work:

- target profiles page
- provider credentials page
- source profiles page
- deployment target selector

Done when:

- admin can add/edit target profiles without DB/manual config hacks

### Phase E: Azure Container Apps Provider

Goal:

- prove the abstraction is not AWS-only

Work:

- add `AZURE_CONTAINER_APPS` provider
- add Azure preflight
- add logs/status integration

Done when:

- same deployment model can target Azure with minimal product changes

### Phase F: Advanced AWS Target

Goal:

- support customers needing stronger AWS networking/control

Work:

- add `AWS_ECS_FARGATE` provider

This is intentionally later.

---

## 16) Recommended Order Of Investment

Recommended sequence:

1. target profiles
2. source model
3. AWS App Runner
4. admin UI for target management
5. Azure Container Apps
6. ECS Fargate

Do not start with:

- EKS
- AKS
- arbitrary raw Terraform in UI
- customer-editable cloud credentials

---

## 17) What Makes This Easy For Users

If implemented correctly, the user workflow is simple:

1. create deployment
2. choose target profile:
   - Railway Dev
   - AWS App Runner Dev
   - Azure Container Apps Dev
3. edit actions/entities/providers/security
4. publish
5. apply

That is the right abstraction boundary.

Cloud choice should feel like:

- environment selection

not:

- infrastructure engineering

---

## 18) Practical Recommendation

### 18.1 Short-term

Build **AWS App Runner** first as the optional AWS target.

### 18.2 Before building AWS provider code

Do this first:

- refactor current platform to use deployment target profiles

That work is mandatory for a clean multi-cloud future.

### 18.3 Long-term

Standardize providers around:

- image-based deployment
- target profiles
- provider-specific adapters
- common verification/logging surface

---

## 19) Definition Of Done

This multi-cloud expansion is successful when:

- Railway still works through the new target-profile model
- one deployment can target Railway
- another deployment can target AWS
- operator does not need provider-specific low-level config for normal usage
- logs and verification remain available
- cloud credentials are managed by the platform, not embedded in deployment config

---

## 20) Recommended Next Document

After approval of this plan, the next concrete execution doc should be:

- `changes/Productization/AWS_APP_RUNNER_PROVIDER_EXECUTION_PLAN.md`

That follow-up doc should specify:

- backend schema changes
- target profile API design
- target selector UI
- AWS App Runner provisioning workflow
- logging/preflight/verification details
