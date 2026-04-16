# Marketplace Shared Inference Service Platform Plan

Status: implementation-baseline plan (2026-04-16)

This document defines how Loom AI should support shared `Ollama` and shared embedding services for marketplace inference offers while staying inside the existing marketplace boundary:

- public marketplace type remains `INFERENCE_PROFILE`
- installs still compile into deployment `providerConfig`
- runtime still consumes normal provider configuration only
- no arbitrary model-server plugin loading in runtime

This plan covers the missing service-lifecycle layer behind inference-profile plugins:

- shared platform-managed inference services
- optional dedicated deployment-scoped inference services
- Railway replicas and platform-controlled scaling
- verification and rollout strategy

Related docs:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_INFERENCE_PROFILE_PRODUCTIZATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/FREE_LLM_AND_EMBEDDING_DEPLOYMENT_STRATEGY.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_DEFAULT_STARTER_CATALOG_PLAN.md`

---

## 1) Executive Summary

The marketplace and runtime contract for inference profiles is already in place.

What exists now:

- `INFERENCE_PROFILE` is a real public plugin type
- installs compile into deployment `providerConfig`
- deployments can reference managed endpoint profiles such as:
  - `openai-cloud-default`
  - `openai-cloud-orchestration`
  - `onnx-bundled`
- runtime provisioning exports purpose-specific endpoint-profile fields
- provider connectivity and release verification already validate those compiled outcomes

What is missing:

- the platform does not yet create, scale, reconcile, or retire shared inference services
- managed endpoint profiles are currently registry entries, not lifecycle-managed service instances

Required product outcome:

- operators can install marketplace inference offers that bind deployments to:
  - shared Loom-managed Ollama services
  - shared Loom-managed embedding services
  - optional deployment-dedicated embedding services
- platform manages those services on Railway using normal control-plane lifecycle and verification

---

## 2) Boundary And Naming

Public marketplace taxonomy should stay simple.

Do:

- keep the public plugin type as `INFERENCE_PROFILE`

Do not:

- add public plugin types like `OLLAMA`, `ONNX`, `MODEL_SERVER`, or `EMBEDDING_SERVICE`

Internal platform concepts may still exist for service lifecycle:

- `SHARED_OLLAMA_SERVICE`
- `SHARED_EMBEDDING_SERVICE`
- `DEDICATED_EMBEDDING_SERVICE`
- `OPENAI_COMPATIBLE_LLM_SERVICE`

These are platform service kinds, not public marketplace plugin types.

---

## 3) Current State

### 3.1 Already implemented

- inference-profile plugin compilation into deployment `providerConfig`
- purpose-specific endpoint profile fields:
  - `orchestrationEndpointProfile`
  - `generationEndpointProfile`
  - `embeddingEndpointProfile`
- managed endpoint registry and validation
- provider connectivity probes
- runtime provisioning export of purpose-specific provider fields
- live marketplace install flow for inference profiles

### 3.2 What this means in practice

Today the platform can do:

- bundled ONNX embeddings in runtime
- customer BYOK OpenAI-compatible endpoints
- first-party cloud endpoint profiles

Today the platform cannot do:

- deploy a new shared Ollama service because a plugin was installed
- create a shared embedding service and bind multiple deployments to it automatically
- ask Railway for more replicas for a shared inference service
- treat a shared inference worker as a first-class managed platform resource

---

## 4) Target Service Modes

The platform should support three inference deployment modes.

### 4.1 `BUNDLED_RUNTIME`

Use case:

- low-cost default ONNX embeddings
- no extra Railway service

Characteristics:

- model assets are bundled into runtime
- no independent scaling
- best for small ONNX models and low-throughput defaults

Current example:

- `onnx-bundled`

### 4.2 `SHARED_PLATFORM_SERVICE`

Use case:

- one managed service used by many deployments
- best fit for shared Ollama orchestration and shared embedding services

Characteristics:

- deployed once per environment or tier
- multiple deployments reference the same endpoint profile
- platform manages service shape, replicas, health, and secrets

Examples:

- shared `Ollama` orchestration service
- shared `TEI` embedding service
- shared OpenAI-compatible inference gateway

### 4.3 `DEPLOYMENT_DEDICATED_SERVICE`

Use case:

- stronger isolation
- deployment-specific throughput or model tuning
- dedicated ONNX or embedding worker for one deployment

Characteristics:

- deployed as an extra service inside the deployment project
- lifecycle follows the deployment release
- platform provisions it alongside:
  - runtime
  - connector
  - vectorization runner

This mode is especially reasonable for ONNX or TEI embedding workers when a deployment needs more throughput than bundled ONNX but does not want to use a shared global service.

---

## 5) Railway Assumptions

Assumption for this plan:

- Railway service replicas and built-in balancing are sufficient for the first production slice

Implications:

- do not introduce an external gateway or service mesh in the first slice
- rely on one Railway service per shared inference worker
- use Railway replicas for horizontal scale
- use one stable service domain per managed endpoint profile

Known Railway constraints accepted in this plan:

- no sticky sessions
- no advanced weighted routing
- no queue-aware balancing
- no custom inference-aware traffic policy

This is acceptable because shared inference requests are stateless request/response traffic.

---

## 6) Recommended Initial Product Shape

### 6.1 First shared services to ship

1. shared embedding service
   - preferred first implementation
   - easiest operationally
   - lowest risk

2. shared Ollama orchestration service
   - second implementation
   - orchestration only at first
   - keep final answer generation on cloud or BYOK paths initially

### 6.2 Recommended ONNX handling

Do not make “raw ONNX” the public marketplace surface.

Instead support ONNX through three operational options:

1. bundled ONNX in runtime
   - existing free baseline

2. deployment-dedicated ONNX/TEI embedding service
   - extra Railway service in the deployment project
   - managed by platform

3. shared managed embedding service
   - preferred scalable marketplace-backed embedding service

### 6.3 Recommended Ollama handling

Use Ollama as an internal service kind behind an OpenAI-compatible provider surface.

This keeps the runtime and plugin contract unchanged:

- provider stays `openai`
- endpoint profile points to a managed Ollama-backed service
- plugin still compiles into normal `providerConfig`

---

## 7) Platform Control-Plane Additions

### 7.1 New managed service model

Add a first-class platform entity for service lifecycle.

Recommended entity:

- `PlatformManagedInferenceService`

Recommended fields:

- `id`
- `serviceRef`
- `displayName`
- `serviceKind`
- `deploymentMode`
  - `SHARED_PLATFORM_SERVICE`
  - `DEPLOYMENT_DEDICATED_SERVICE`
  - `BUNDLED_RUNTIME`
- `providerType`
- `protocolType`
  - `OPENAI_COMPATIBLE`
- `modelId`
- `embeddingDimensions`
- `environmentScope`
- `tierScope`
- `railwayProjectId`
- `railwayEnvironmentId`
- `railwayServiceId`
- `railwayReplicaTarget`
- `railwayReplicaMin`
- `railwayReplicaMax`
- `baseUrl`
- `privateNetworkUrl`
- `healthPath`
- `secretName`
- `status`
- `detailsJson`

### 7.2 Endpoint profile relationship

Keep endpoint profiles, but stop treating them as the lifecycle root.

Recommended relationship:

- `PlatformManagedInferenceService`
  - owns the real service lifecycle
- `PlatformManagedInferenceEndpoint`
  - becomes the resolved endpoint identity bound to a service

One service may expose one or more endpoint profiles, for example:

- orchestration profile
- generation profile
- embedding profile

### 7.3 Deployment-dedicated service relationship

For dedicated mode, add a deployment link:

- `deploymentId`
- `versionId` or applied-release linkage

Dedicated inference services should be created and reconciled as part of the same apply lifecycle as runtime, connector, and vectorization runner.

---

## 8) Marketplace And Install Model

### 8.1 Public plugin contract stays the same

Inference offers should still compile into deployment `providerConfig`.

The public manifest should express:

- purpose
- provider
- endpoint profile ref or managed service binding ref
- model defaults
- install-field bindings

### 8.2 New internal resolution layer

The compiler should be able to resolve:

- direct `endpointProfileRef`
- or an internal managed service ref that resolves to an endpoint profile

Recommended resolved pattern:

- plugin version declares intended managed service class
- install compiler resolves that to an active endpoint profile in the target environment
- deployment receives only resolved `providerConfig`

### 8.3 New first-party inference offers

Recommended first-party additions:

- `mkp-inference-shared-embeddings-standard`
- `mkp-inference-shared-embeddings-premium`
- `mkp-inference-shared-ollama-orchestration`
- `mkp-inference-dedicated-embedding-worker`

These remain `INFERENCE_PROFILE` plugins.

---

## 9) Railway Provisioning Model

### 9.1 Shared service topology

Shared services should live in their own Railway projects or controlled shared-infra projects per environment.

Recommended services:

- `shared-embedding-<environment>`
- `shared-ollama-orch-<environment>`

Each service gets:

- stable public domain when needed
- private networking target when possible
- service-level health endpoint
- replica target

### 9.2 Dedicated service topology

Dedicated embedding services should be provisioned as optional extra services inside the deployment project.

Recommended deployment topology:

- runtime
- rest connector
- vectorization runner
- optional dedicated embedding worker

The connector does not need to call the embedding worker directly.
It remains part of the deployment project because it shares the same operational lifecycle and verification window.

### 9.3 Runtime wiring

For shared or dedicated services, runtime should still be configured only through provider env and endpoint-profile fields.

Do not add marketplace-specific runtime behavior.

---

## 10) Railway Scaling In Platform

### 10.1 Why platform scaling is required

If shared inference services are first-class managed resources, the platform must be able to ask Railway for more replicas.

Without this:

- shared services will become a manual ops bottleneck
- marketplace subscriptions cannot be mapped cleanly to service capacity
- hosted verification cannot prove the platform can scale with usage

### 10.2 Required platform feature

Add Railway replica management to the platform control plane.

Recommended capability:

- get current replica target for a managed service
- set desired replica target
- enforce min/max bounds
- record last scaling action and operator

Recommended fields on managed service:

- `desiredReplicas`
- `actualReplicas`
- `minReplicas`
- `maxReplicas`
- `autoscalingMode`
  - `MANUAL`
  - `POLICY_DRIVEN`

### 10.3 First slice

Do not build autoscaling policy first.

Implement:

- manual replica management
- platform API to update replica count
- provisioning reconcile that applies desired replica count to Railway

That is enough for the initial production slice.

---

## 11) Connectivity, Auth, And Secret Model

### 11.1 Shared Ollama

Recommended protocol:

- OpenAI-compatible HTTP

Recommended auth posture:

- private networking when the caller is platform-managed runtime
- optional bearer token or shared secret when public exposure is unavoidable

### 11.2 Shared embedding service

Recommended protocols:

- OpenAI-compatible embeddings API

Preferred first option:

- OpenAI-compatible embeddings API

This reduces special-case behavior and fits the existing provider stack more cleanly.

### 11.3 Secret handling

Managed service credentials should stay in platform-managed secrets and resolve into deployment provider config the same way current managed endpoint profiles do.

Do not expose:

- raw service admin tokens in plugin manifests
- raw Railway service secrets in deployment UI

---

## 12) Verification Model

### 12.1 Compile-time verification

Draft validation must verify:

- referenced managed service or endpoint profile exists
- service status is active
- provider type matches the inference section
- protocol is valid for the selected provider type

### 12.2 Provider connectivity verification

Connectivity must verify:

- service health endpoint
- reachable base URL
- required secret binding
- intended model readiness where feasible

### 12.3 Release verification

Release verification must compare:

- expected resolved endpoint profile refs
- expected managed inference metadata
- expected provider fields
- runtime admin overview values

### 12.4 Hosted verification

Hosted verification should add:

- shared embedding smoke query
- shared Ollama orchestration smoke query
- degraded replica or unavailable service case
- dedicated embedding worker proof for one rollout

---

## 13) Operational Model

### 13.1 Shared service ownership

Shared inference services are platform infrastructure.

They should be:

- created once per environment or tier
- reused by many deployments
- versioned and rolled carefully

### 13.2 Dedicated service ownership

Dedicated embedding services are deployment infrastructure.

They should:

- follow deployment apply lifecycle
- be versioned with the deployment
- be removed when the deployment is archived or the dedicated mode is removed

### 13.3 Failure posture

Fail closed:

- if a shared endpoint profile points to no active service, draft validation should fail
- if service provisioning is incomplete, apply should not mark release verified
- if bundled ONNX is selected, no external probe is required

---

## 14) Recommended Wave Sequence

### Wave 0: service model and registry split

Add:

- `PlatformManagedInferenceService`
- endpoint-to-service relationship
- migration of current managed endpoint rows to the new model

### Wave 1: shared embedding service

Implement:

- shared embedding service provisioning on Railway
- one managed service kind:
  - `SHARED_EMBEDDING_SERVICE`
- endpoint profile binding
- connectivity and release verification

### Wave 2: Railway replica management

Implement:

- desired replica count in platform
- Railway reconcile for replicas
- admin UI/API to change replicas

### Wave 3: shared Ollama orchestration service

Implement:

- shared OpenAI-compatible Ollama service
- orchestration endpoint profile binding
- hosted verification for orchestration route

### Wave 4: deployment-dedicated embedding worker

Implement:

- optional dedicated embedding service in the deployment project
- provisioning alongside runtime, connector, and vectorization runner
- release verification and cleanup

### Wave 5: policy-driven scaling and tier mapping

Implement:

- tier to managed service mapping
- capacity guidance
- optional policy-driven replica targets

---

## 15) Acceptance Criteria

This plan is complete when:

- inference-profile plugins can bind deployments to shared managed services, not only static endpoint rows
- the platform can provision a shared embedding service on Railway
- the platform can provision a shared Ollama orchestration service on Railway
- the platform can set Railway replica targets for managed inference services
- one deployment can use bundled ONNX, another can use shared embeddings, and another can use a dedicated embedding worker
- hosted verification proves those modes live
- runtime still consumes only resolved `providerConfig`
- no new arbitrary runtime plugin system is introduced

---

## 16) Recommended First Production Slice

Build this first:

1. `PlatformManagedInferenceService`
2. shared embedding service on Railway
3. manual Railway replica control
4. one new `INFERENCE_PROFILE` offer bound to the shared embedding service
5. release and hosted verification for that service

Then build:

6. shared Ollama orchestration service
7. one `INFERENCE_PROFILE` offer bound to that shared service

Then build:

8. deployment-dedicated embedding worker mode

This gives the fastest path to real shared inference value without overloading the first slice with too many service types at once.
