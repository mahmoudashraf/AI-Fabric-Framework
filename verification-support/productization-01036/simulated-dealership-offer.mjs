import {createHash} from "node:crypto";

import {buildManagedDependencySpec, requireReviewedInboxArtifact,
  reviewedManagedDependencyCapacity, requireInboxSelection, SPEC_ID} from "./simulated-dealership-managed-spec.mjs";
import {comparableJson, validateManagedDependencySpec} from "./publish-simulated-dealership-package.mjs";

export const OFFER_ID = "offer-loomai-engage-external-dealership-candidate-v1";
export const TEMPLATE_PLUGIN_ID = "mkp-template-dealership-external-systems-v1";
export const MANAGED_INBOX_TEMPLATE_PLUGIN_ID = "mkp-template-dealership-external-systems-managed-inbox-v1";
export const TEMPLATE_VERSION = "1.0.0";
export const CUSTOMER_PLUGIN_REFS = Object.freeze([
  "mkp-data-dealership-external-stock-v1@1.0.0",
  "mkp-action-dealership-external-discovery-v1@1.0.0",
  "mkp-action-dealership-external-engagement-v1@1.0.0",
  "mkp-data-dealership-knowledge-v1@1.0.0",
  "mkp-action-conversation-handover@1.1.0",
  "mkp-integration-basic-loomai-inbox@1.3.0",
]);
export const CUSTOMER_PLUGIN_VERSION_IDS = Object.freeze({
  "mkp-data-dealership-external-stock-v1": "mkv-data-dealership-external-stock-v1-0",
  "mkp-action-dealership-external-discovery-v1": "mkv-action-dealership-external-discovery-v1-0",
  "mkp-action-dealership-external-engagement-v1": "mkv-action-dealership-external-engagement-v1-0",
  "mkp-data-dealership-knowledge-v1": "mkv-data-dealership-knowledge-v1-0",
  "mkp-action-conversation-handover": "mkv-action-conversation-handover-v1-1",
  "mkp-integration-basic-loomai-inbox": "mkv-integration-basic-loomai-inbox-v1-3",
});
export const MANAGED_INBOX_INSTALL_DEFAULTS = Object.freeze({
  config: Object.freeze({providerBaseUrl: "${MANAGED_INBOX_BASE_URL}", operatorConsoleUrl: "${MANAGED_INBOX_CONSOLE_URL}",
    bindingId: "${MANAGED_INBOX_BINDING_ID}"}),
  secretRefs: Object.freeze({providerApiKeySecretRef: "MANAGED_INBOX_API_KEY",
    runtimeCallbackSigningSecretRef: "MANAGED_INBOX_CALLBACK_SIGNING_SECRET",
    providerWebhookSigningSecretRef: "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET"}),
});
export const KNOWLEDGE_INSTALL_DEFAULTS = Object.freeze({
  config: Object.freeze({publicContentApprovalConfirmed: false}),
});
export const EXTERNAL_INTEGRATION_PROFILE_SCHEMA = "loomai-external-integration-profile/v1";
// Exact V196 contracts. Hosts are supplied by review, never inferred from a plugin or demo.
export const EXTERNAL_BINDING_REQUIREMENTS = Object.freeze([
  Object.freeze({id: "vehicle-stock", pluginId: "mkp-data-dealership-external-stock-v1",
    profileId: "dealership-external-stock-v1", pluginVersionId: "mkv-data-dealership-external-stock-v1-0",
    configFields: Object.freeze(["providerBaseUrl", "providerHost", "advertiserId"]),
    secretRefFields: Object.freeze(["providerKey", "providerSecret", "notificationSecret"]),
    endpointFields: Object.freeze(["providerBaseUrl"]), accountScopeFields: Object.freeze(["advertiserId"])}),
  Object.freeze({id: "business-actions", pluginId: "mkp-action-dealership-external-engagement-v1",
    profileId: "dealership-external-engagement-v1", pluginVersionId: "mkv-action-dealership-external-engagement-v1-0",
    configFields: Object.freeze(["receiverActionUrl"]), secretRefFields: Object.freeze(["receiverApiKey"]),
    endpointFields: Object.freeze(["receiverActionUrl"]), accountScopeFields: Object.freeze([])}),
]);
export const MANAGED_AI_STATE_MODE = "MANAGED_POSTGRES";
export const CUSTOMER_AI_STATE_MODE = "CUSTOMER_POSTGRES";
export const ATTENDED_CANARY_CONTRACT = "loomai-attended-managed-dealership-canary/v1";
export const HOSTED_READINESS_GATES = Object.freeze([
  "CUSTOMER_SCOPED_BINDINGS_AND_PROVIDER_AUTH_REQUIRED", "MEASURED_CAPACITY_ADMISSION_REQUIRED",
  "HOSTED_APPLY_AND_AI_STATE_PERSISTENCE_REQUIRED", "DOCUMENT_AND_STOCK_INDEXING_REQUIRED",
  "READ_ACTION_CONFIRMATION_IDENTITY_AND_HANDOVER_REQUIRED", "PRODUCTION_PROMOTION_ACCEPTANCE_REQUIRED",
]);

export function requireCustomerOfferPublicationSupport({managedInbox, aiStateStorageModes} = {}) {
  requireInboxSelection(managedInbox);
  if (!managedInbox) throw new Error("EXTERNAL_INBOX_TARGET_BINDING_REQUIRED");
  if (!Array.isArray(aiStateStorageModes) || hash(aiStateStorageModes) !== hash([MANAGED_AI_STATE_MODE])) {
    throw new Error("CUSTOMER_AI_STATE_HOSTED_GENERATION_ACCEPTANCE_REQUIRED: publish only the explicitly selected MANAGED_POSTGRES canary; BYO remains a separate gate.");
  }
}
export const OPENAI_MAX_TOKENS = 2048;
export const BASE_TEMPLATE_ID = "dev-openai-milvus";
// Verified CI receipt: run 39164527840, job 117785013595. Registration and promotion are separate prerequisites.
export const RUNTIME_IMAGE_DIGEST = "sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140";
export const RUNTIME_SOURCE_COMMIT = "d2b1a9792dc10ec801d3ce30c170763f1f2ab301";
export const RUNTIME_FRAMEWORK_VERSION = "0.8.36";
export const MILVUS_TOKEN_CAPABILITY = "milvus-token-authentication-v1";
export const STAGING_TARGET_ID = "dtp-coolify-staging-behavior";
export const PRODUCTION_TARGET_ID = "dtp-coolify-production-behavior";

export const REVIEWED_PLATFORM_CAPACITY = Object.freeze({
  runtime: Object.freeze({milliCpu: 1000, memoryMiB: 1024, diskMiB: 0}),
  connector: Object.freeze({milliCpu: 250, memoryMiB: 768, diskMiB: 0}),
  vectorizationRunner: Object.freeze({milliCpu: 250, memoryMiB: 768, diskMiB: 0}),
});
export const MANAGED_AI_STATE_CAPACITY = Object.freeze({milliCpu: 250, memoryMiB: 512, diskMiB: 2048});

function sumCapacity(capacities) {
  return capacities.reduce((total, capacity) => ({
    milliCpu: total.milliCpu + capacity.milliCpu,
    memoryMiB: total.memoryMiB + capacity.memoryMiB,
    diskMiB: total.diskMiB + capacity.diskMiB,
  }), {milliCpu: 0, memoryMiB: 0, diskMiB: 0});
}

export function reviewedTopologyForMode({managedInbox, aiStateStorageMode}) {
  const capacity = reviewedTopologyCapacity({managedInbox, aiStateStorageMode});
  return {replicas: 1, cpuMillis: capacity.milliCpu, memoryMiB: capacity.memoryMiB,
    diskMiB: capacity.diskMiB, databases: capacity.databases, indexes: capacity.indexes,
    dependencies: capacity.dependencies, concurrentApplies: 1};
}

export function reviewedTopologyCapacity({managedInbox = false, aiStateStorageMode} = {}) {
  if (![MANAGED_AI_STATE_MODE, CUSTOMER_AI_STATE_MODE].includes(aiStateStorageMode)) {
    throw new Error("An explicit reviewed AI state storage mode is required.");
  }
  const managed = reviewedManagedDependencyCapacity({managedInbox});
  const hosted = aiStateStorageMode === MANAGED_AI_STATE_MODE;
  const platform = sumCapacity([...Object.values(REVIEWED_PLATFORM_CAPACITY),
    ...(hosted ? [MANAGED_AI_STATE_CAPACITY] : [])]);
  return Object.freeze({
    milliCpu: platform.milliCpu + managed.milliCpu,
    memoryMiB: platform.memoryMiB + managed.memoryMiB,
    diskMiB: platform.diskMiB + managed.diskMiB,
    dependencies: managed.resources,
    databases: hosted ? 1 : 0,
    indexes: 2,
  });
}

const REVIEWED_CAPACITY = reviewedTopologyCapacity({aiStateStorageMode: MANAGED_AI_STATE_MODE});

export const REVIEWED_TOPOLOGY = Object.freeze({
  replicas: 1,
  cpuMillis: REVIEWED_CAPACITY.milliCpu,
  memoryMiB: REVIEWED_CAPACITY.memoryMiB,
  diskMiB: REVIEWED_CAPACITY.diskMiB,
  databases: REVIEWED_CAPACITY.databases,
  indexes: REVIEWED_CAPACITY.indexes,
  dependencies: REVIEWED_CAPACITY.dependencies,
  concurrentApplies: 1,
});

// Proposed envelope only, not admission approval. Optional AI-state hosting is
// owner-approved; exact target fit and both typed storage modes still need verification.
export const REVIEWED_LIMITS = Object.freeze({
  measuredPolicyRef: "01044-external-ai-capacity-review-pending",
  activeStagingTargets: 1,
  replicas: 1,
  cpuMillis: 4000,
  memoryMiB: 6144,
  diskMiB: REVIEWED_CAPACITY.diskMiB,
  databases: REVIEWED_CAPACITY.databases,
  indexes: REVIEWED_CAPACITY.indexes,
  dependencies: REVIEWED_CAPACITY.dependencies,
  concurrentApplies: 1,
  stagingExpiryHours: 24,
  maxExtensionHours: 24,
  recoveryWindowHours: 24,
});

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
  }
  return value;
}

export function hash(value) {
  return createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");
}

export function profileFingerprint(profile) {
  function validateNumbers(value) {
    if (typeof value === "number" && !Number.isSafeInteger(value)) {
      throw new Error("TARGET_PROFILE_IDENTITY_NUMBER_UNSUPPORTED");
    }
    if (value && typeof value === "object") Object.values(value).forEach(validateNumbers);
  }
  for (const field of ["id", "providerType", "environmentName", "sourceStrategy"]) {
    if (typeof profile?.[field] !== "string" || !profile[field].trim()) {
      throw new Error("TARGET_PROFILE_IDENTITY_INVALID");
    }
  }
  if (profile.credentialRefId != null && typeof profile.credentialRefId !== "string") {
    throw new Error("TARGET_PROFILE_IDENTITY_INVALID");
  }
  for (const field of ["providerConfig", "networkPolicy", "resourceDefaults"]) {
    if (!profile[field] || typeof profile[field] !== "object" || Array.isArray(profile[field])) {
      throw new Error("TARGET_PROFILE_IDENTITY_INVALID");
    }
    validateNumbers(profile[field]);
  }
  // Match DeploymentTargetProfileIdentity, including the target environment.
  return hash({
    id: profile.id,
    provider: profile.providerType,
    environment: profile.environmentName,
    sourceStrategy: profile.sourceStrategy,
    credentialReference: profile.credentialRefId ?? "",
    providerConfig: profile.providerConfig,
    networkPolicy: profile.networkPolicy,
    resourceDefaults: profile.resourceDefaults,
  });
}

export function artifactFingerprint(artifact) {
  return hash({
    id: artifact.id,
    serviceName: artifact.serviceName,
    artifactType: artifact.artifactType,
    artifactRole: artifact.artifactRole ?? "",
    imageRepository: artifact.imageRepository ?? "",
    imageDigest: artifact.imageDigest ?? "",
    gitCommitSha: artifact.gitCommitSha ?? "",
    capabilityManifest: artifact.capabilityManifest,
    capabilityManifestHash: artifact.capabilityManifestHash ?? "",
  });
}

export function workspaceProfile({projectUrl, publishableKey, dealerId, imageHostAllowlist}) {
  if (!projectUrl || !publishableKey) throw new Error("The reviewed Supabase public configuration is required.");
  if (typeof dealerId !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(dealerId)
    || !Array.isArray(imageHostAllowlist) || imageHostAllowlist.length > 32
    || imageHostAllowlist.some(host => typeof host !== "string"
      || !/^(?=.{1,253}$)[a-z0-9]+(?:[.-][a-z0-9]+)*\.[a-z]{2,63}$/.test(host))
    || new Set(imageHostAllowlist).size !== imageHostAllowlist.length) {
    throw new Error("Explicit reviewed dealerId and imageHostAllowlist are required; no demo defaults.");
  }
  const configuration = {
    page: {
      kind: "auto",
      maxChars: 1800,
      maxPages: 3,
      contextLabel: "Current dealership page",
      rootSelector: "#main-content",
      maxTotalChars: 10000,
    },
    dealer: {
      id: dealerId,
      sourceMode: "DEALERSHIP_INVENTORY",
      assistantLabel: "Dealership AI",
    },
    knowledge: {inventoryVectorSpace: "dealer-vehicle"},
    capabilities: {callback: true, testDrive: true, comparison: true},
    presentation: {imageHostAllowlist: [...imageHostAllowlist]},
  };
  const theme = {
    darkMode: false,
    fontFamily: "Inter, system-ui, sans-serif",
    borderRadius: "0.5rem",
    primaryColor: "#123b35",
  };
  const interaction = {
    packCode: "dealership",
    packVersion: "1.1.0",
    // Profile IDs pin the backend's validated representation, not display values.
    connectionMode: "PUBLIC_RUNTIME_ANONYMOUS",
    connectionProfileCode: "runtime-anonymous-direct",
    connectionProfileVersion: "1.0.0",
    deliveryMode: "STANDALONE",
    connectionConfiguration: {},
    configuration,
  };
  const authentication = {
    profileCode: "supabase",
    profileVersion: "1.0.0",
    interactionPolicy: "PUBLIC_WITH_PROTECTED_CAPABILITIES",
    configuration: {projectUrl, publishableKey, methods: ["email-password"]},
  };
  return {
    interaction,
    theme,
    authentication,
    configuration: {...configuration, theme},
    authConfiguration: authentication.configuration,
    interactionPolicy: authentication.interactionPolicy,
    interactionProfileId: `workspace:${hash(interaction)}`,
    themeProfileId: `theme:${hash(theme)}`,
    authProfileId: `auth:${hash(authentication)}`,
  };
}

function pluginPin(version) {
  if (!version?.pluginId || !version?.id || !version?.manifest) {
    throw new Error("A published plugin version with its manifest is required.");
  }
  return {pluginId: version.pluginId, versionId: version.id, manifestSha256: hash(version.manifest)};
}

export function buildExternalIntegrationBindings({pluginVersions, targetProfileIds, allowedHosts}) {
  const slots = EXTERNAL_BINDING_REQUIREMENTS.map(requirement => requirement.id);
  if (!allowedHosts || typeof allowedHosts !== "object" || Array.isArray(allowedHosts)
    || hash(Object.keys(allowedHosts).sort()) !== hash([...slots].sort())) {
    throw new Error("Exact reviewed allowedHosts are required for vehicle-stock and business-actions.");
  }
  if (!Array.isArray(targetProfileIds) || targetProfileIds.length === 0 || targetProfileIds.length > 16
    || new Set(targetProfileIds).size !== targetProfileIds.length
    || targetProfileIds.some(id => typeof id !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(id))) {
    throw new Error("Exact reviewed external integration target IDs are required.");
  }
  const profiles = EXTERNAL_BINDING_REQUIREMENTS.map(requirement => {
    const matches = pluginVersions.filter(version => version?.pluginId === requirement.pluginId);
    const version = matches.length === 1 ? matches[0] : null;
    if (version?.id !== requirement.pluginVersionId || version.version !== "1.0.0"
      || version.manifest?.pluginId !== requirement.pluginId || version.manifest.version !== "1.0.0") {
      throw new Error(`The exact V196 published plugin pin is required for ${requirement.id}.`);
    }
    const fields = version.manifest.installForm;
    const expectedTypes = Object.fromEntries([
      ...requirement.configFields.map(field => [field, requirement.endpointFields.includes(field) ? "url" : "text"]),
      ...requirement.secretRefFields.map(field => [field, "secretRef"]),
    ]);
    if (!Array.isArray(fields) || fields.length !== Object.keys(expectedTypes).length
      || new Set(fields.map(field => field?.id)).size !== fields.length
      || fields.some(field => !field || expectedTypes[field.id] !== field.type || field.required !== true
        || Object.hasOwn(field, "defaultValue"))) {
      throw new Error(`The exact unbound V196 install field contract is required for ${requirement.id}.`);
    }
    const hosts = allowedHosts[requirement.id];
    if (!Array.isArray(hosts) || hosts.length === 0 || hosts.length > 16 || new Set(hosts).size !== hosts.length
      || hosts.some(host => typeof host !== "string" || host.length > 253
        || !/^[a-z][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*)*\.[a-z]{2,63}$/.test(host)
        || ["localhost", "local", "internal", "lan", "home", "invalid"].includes(host.split(".").at(-1)))) {
      throw new Error(`Explicit reviewed DNS host names, not URLs/IPs/wildcards, are required for ${requirement.id}.`);
    }
    return {
      schemaVersion: EXTERNAL_INTEGRATION_PROFILE_SCHEMA,
      id: requirement.profileId, bindingSlotId: requirement.id,
      pluginId: requirement.pluginId, pluginVersionId: version.id, manifestSha256: pluginPin(version).manifestSha256,
      targetProfileIds: [...targetProfileIds].sort(), configFields: [...requirement.configFields].sort(),
      secretFields: [...requirement.secretRefFields].sort(), endpointFields: [...requirement.endpointFields].sort(),
      accountScopeFields: [...requirement.accountScopeFields].sort(), allowedHosts: [...hosts].sort(),
    };
  });
  return {profiles, slots: profiles.map(profile => ({id: profile.bindingSlotId, kind: "EXTERNAL_SERVICE",
    supportedProfileIds: [profile.id], pluginId: profile.pluginId, installConfigField: null}))};
}

const REVIEWED_RESOURCE_DEFAULTS = Object.freeze({
  runtimeLimitsCpus: "1.0",
  runtimeLimitsMemory: "1g",
  runtimeLimitsMemorySwap: "1g",
  runtimeLimitsMemoryReservation: "512m",
  runtimeJavaOpts: "-XX:ActiveProcessorCount=1 -Xms256m -Xmx768m",
  connectorLimitsCpus: "0.25",
  connectorLimitsMemory: "768m",
  connectorLimitsMemorySwap: "768m",
  connectorLimitsMemoryReservation: "384m",
  connectorJavaOpts: "-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m",
  vectorizationRunnerLimitsCpus: "0.25",
  vectorizationRunnerLimitsMemory: "768m",
  vectorizationRunnerLimitsMemorySwap: "768m",
  vectorizationRunnerLimitsMemoryReservation: "384m",
  vectorizationRunnerJavaOpts: "-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m",
  runtimeDatabaseMode: "COOLIFY_POSTGRES",
  runtimeDatabaseCpuMillicores: 250,
  runtimeDatabaseMemoryMiB: 512,
  runtimeDatabaseDiskMiB: 2048,
});

const REVIEWED_CPUSETS = Object.freeze({
  [STAGING_TARGET_ID]: Object.freeze({runtimeLimitsCpuSet: "7", connectorLimitsCpuSet: "6", vectorizationRunnerLimitsCpuSet: "5"}),
  [PRODUCTION_TARGET_ID]: Object.freeze({runtimeLimitsCpuSet: "3", connectorLimitsCpuSet: "2", vectorizationRunnerLimitsCpuSet: "1"}),
});

function requireReviewedTarget(target, targetId) {
  if (!target?.active || target.providerType !== "COOLIFY" || target.sourceStrategy !== "IMAGE_SOURCE") {
    throw new Error(`Reviewed target ${targetId} is not an active immutable Coolify image target.`);
  }
  const required = {...REVIEWED_RESOURCE_DEFAULTS, ...REVIEWED_CPUSETS[targetId]};
  const actual = target.resourceDefaults ?? {};
  const drifted = Object.entries(required).filter(([key, value]) => String(actual[key]) !== String(value));
  if (drifted.length > 0) {
    throw new Error(`Reviewed target ${targetId} has missing or drifted capacity fields: ${drifted.map(([key]) => key).join(", ")}.`);
  }
}

function requireReviewedArtifact(artifact) {
  if (!artifact?.id || artifact.artifactRole !== "RUNTIME" || artifact.artifactType !== "DOCKER_IMAGE"
    || artifact.imageDigest !== RUNTIME_IMAGE_DIGEST || artifact.gitCommitSha !== RUNTIME_SOURCE_COMMIT
    || !["staging", "production"].includes(artifact.promotionChannel)
    || !artifact.promotedAt || typeof artifact.buildRunId !== "string" || !artifact.buildRunId.trim()
    || artifact.capabilityManifest?.aiFabricVersion !== RUNTIME_FRAMEWORK_VERSION) {
    throw new Error("The exact promoted AI Fabric 0.8.36 runtime artifact with CI build provenance is required.");
  }
  for (const [field, required] of Object.entries({
    capabilities: "deployment-local-provider-budget-v1",
    endpointClasses: "provider-budget-administration",
    migrationIds: "provider-budget-accounting-v1",
    verificationPackIds: "provider-budget-hard-limit-v1",
  })) {
    const declarations = artifact.capabilityManifest[field];
    if (!Array.isArray(declarations) || !declarations.includes(required)) {
      throw new Error(`The reviewed runtime artifact is missing a required provider-budget guard declaration: ${field}.`);
    }
  }
  if (!artifact.capabilityManifest.capabilities.includes(MILVUS_TOKEN_CAPABILITY)) {
    throw new Error("The reviewed runtime artifact is missing the required milvus-token-authentication-v1 capability.");
  }
}

function buildDefinitionCandidate({template, pluginVersions, artifact, targets, auth, managedInbox, aiStateStorageMode,
  aiStateStorageModes = [MANAGED_AI_STATE_MODE, CUSTOMER_AI_STATE_MODE],
  externalIntegrationAllowedHosts, behaviorCatalog, executionExtensionCatalog}) {
  requireInboxSelection(managedInbox);
  reviewedTopologyCapacity({managedInbox, aiStateStorageMode});
  if (!Array.isArray(aiStateStorageModes) || aiStateStorageModes.length === 0
    || new Set(aiStateStorageModes).size !== aiStateStorageModes.length
    || aiStateStorageModes.some(mode => ![MANAGED_AI_STATE_MODE, CUSTOMER_AI_STATE_MODE].includes(mode))
    || !aiStateStorageModes.includes(aiStateStorageMode)) {
    throw new Error("Explicit supported AI state storage modes must contain the selected mode without unknown or duplicate choices.");
  }
  const expectedTemplate = managedInbox ? MANAGED_INBOX_TEMPLATE_PLUGIN_ID : TEMPLATE_PLUGIN_ID;
  const expectedTemplateVersionId = managedInbox ? "mkv-template-dealership-external-systems-managed-inbox-v1-0"
    : "mkv-template-dealership-external-systems-v1-0";
  if (template?.pluginId !== expectedTemplate || template?.version !== TEMPLATE_VERSION
    || template.id !== expectedTemplateVersionId
    || template.manifest?.contributions?.template?.providerConfig?.openaiMaxTokens !== OPENAI_MAX_TOKENS) {
    throw new Error("The exact external-systems 1.0.0 template with an explicit integer 2048-token output cap is required.");
  }
  const provider = template.manifest.contributions.template.providerConfig;
  const credentialFields = ["milvusToken", "milvusUsername", "milvusPassword", "milvusHost",
    "milvusRuntimeTokenSecretName", "milvusRuntimeUsernameSecretName", "milvusRuntimePasswordSecretName"];
  if (provider.vectorStrategy !== "milvus" || provider.vectorProvisioningMode !== "EXTERNAL_EXISTING"
    || provider.vectorStoragePosture !== "SHARED" || provider.milvusExternalBindingRequired !== true
    || provider.milvusAuthenticationType !== "TOKEN" || credentialFields.some((field) => Object.hasOwn(provider, field))) {
    throw new Error("The exact native TOKEN retained external-vector binding contract is required; no implicit managed, endpoint or credential fallback.");
  }
  const requiredRefs = template?.contributions?.templateRequiredPluginRefs ?? [];
  const byRef = new Map(pluginVersions.map((version) => [`${version.pluginId}@${version.version}`, version]));
  if (requiredRefs.length !== CUSTOMER_PLUGIN_REFS.length || new Set(requiredRefs).size !== requiredRefs.length
    || CUSTOMER_PLUGIN_REFS.some(ref => !requiredRefs.includes(ref))
    || requiredRefs.some((reference) => !byRef.has(reference))) {
    throw new Error("The template's exact required plugin versions were not supplied.");
  }
  for (const reference of requiredRefs) {
    const version = byRef.get(reference);
    if (version.id !== CUSTOMER_PLUGIN_VERSION_IDS[version.pluginId] || version.manifest?.pluginId !== version.pluginId
      || version.manifest.version !== version.version
      || pluginVersions.filter(candidate => candidate.pluginId === version.pluginId).length !== 1) {
      throw new Error("The exact immutable V196 composition plugin pins are required.");
    }
  }
  const defaults = template.manifest.contributions.template.requiredPluginInstallDefaults;
  if (!defaults || typeof defaults !== "object" || Array.isArray(defaults)
    || hash(defaults) !== hash({"mkp-data-dealership-knowledge-v1@1.0.0": KNOWLEDGE_INSTALL_DEFAULTS,
      ...(managedInbox ? {"mkp-integration-basic-loomai-inbox@1.3.0": MANAGED_INBOX_INSTALL_DEFAULTS} : {})})
    || /MANAGED_SIMULATOR|MANAGED_RECEIVER/.test(JSON.stringify(template.manifest))) {
    throw new Error("Business integrations must be unbound; managed business defaults and pre-approved document consent are forbidden.");
  }
  requireReviewedArtifact(artifact);
  requireTemplateRuntimeSupport(template, artifact, {behaviorCatalog, executionExtensionCatalog});
  reviewedTopologyCapacity({managedInbox, aiStateStorageMode});
  const targetById = new Map(targets.map((target) => [target.id, target]));
  if (targets.length !== 2 || targetById.size !== 2) throw new Error("Exactly two distinct reviewed targets are required.");
  const staging = targetById.get(STAGING_TARGET_ID);
  const production = targetById.get(PRODUCTION_TARGET_ID);
  requireReviewedTarget(staging, STAGING_TARGET_ID);
  requireReviewedTarget(production, PRODUCTION_TARGET_ID);
  const externalBindings = buildExternalIntegrationBindings({pluginVersions,
    targetProfileIds: [staging.id, production.id], allowedHosts: externalIntegrationAllowedHosts});
  const workspace = workspaceProfile(auth);
  const aiStateStorageTopologies = Object.fromEntries(aiStateStorageModes
    .map(mode => [mode, reviewedTopologyForMode({managedInbox, aiStateStorageMode: mode})]));
  const topology = Object.fromEntries(Object.keys(REVIEWED_TOPOLOGY)
    .map(key => [key, Math.max(...Object.values(aiStateStorageTopologies).map(demand => demand[key]))]));
  const limits = {...REVIEWED_LIMITS, diskMiB: topology.diskMiB, dependencies: topology.dependencies};
  return {
    schemaVersion: "1",
    name: "LoomAI Engage External Dealership (Unapproved Integration Candidate)",
    templateId: BASE_TEMPLATE_ID,
    template: pluginPin(template),
    plugins: requiredRefs.map((reference) => pluginPin(byRef.get(reference))),
    artifacts: [{artifactId: artifact.id, fingerprint: artifactFingerprint(artifact)}],
    targets: [
      {targetProfileId: staging.id, intent: "CUSTOMER_STAGING", profileFingerprint: profileFingerprint(staging)},
      {targetProfileId: production.id, intent: "CUSTOMER_PRODUCTION", profileFingerprint: profileFingerprint(production)},
    ],
    operations: [
      "TEMPLATE_SELECT", "BOOTSTRAP_CONFIGURATION", "BOOTSTRAP", "READ", "VERSION_PUBLISH",
      "BINDING_REPLACE", "BINDING_REVOKE", "SOURCE_DISCOVER", "SOURCE_PREVIEW", "SOURCE_INDEX",
      ...(managedInbox ? ["INBOX_OPERATOR_ENROLL", "INBOX_OPERATOR_ROTATE", "INBOX_OPERATOR_DELIVER", "INBOX_OPERATOR_REVOKE"] : []),
      "SOURCE_REFRESH", "SOURCE_REMOVE", "APP_CONFIGURE", "DOMAIN_CONFIGURE", "AUTH_CONFIGURE",
      "APPLY", "VERIFY", "PROMOTION_PLAN", "PROMOTION_APPLY", "ACTIVATE", "ROLLBACK",
      "STAGING_RETAIN", "STAGING_EXTEND", "TARGET_RETIRE",
    ],
    settings: [
      {setting: "DEPLOYMENT_NAME", type: "TEXT", maxLength: 100, allowedValues: []},
      {setting: "AI_STATE_STORAGE_MODE", type: "CHOICE", maxLength: 32,
        allowedValues: [...aiStateStorageModes]},
      {setting: "APP_TITLE", type: "TEXT", maxLength: 120, allowedValues: []},
      {setting: "INTERACTION_PROFILE_ID", type: "CHOICE", maxLength: 100,
        allowedValues: [workspace.interactionProfileId]},
      {setting: "THEME_PROFILE_ID", type: "CHOICE", maxLength: 100,
        allowedValues: [workspace.themeProfileId]},
      {setting: "AUTH_PROFILE_ID", type: "CHOICE", maxLength: 100,
        allowedValues: [workspace.authProfileId]},
      {setting: "APP_DOMAIN", type: "TEXT", maxLength: 255, allowedValues: []},
      {setting: "DOCUMENT_AUDIENCE", type: "CHOICE", maxLength: 32, allowedValues: ["PUBLIC"]},
    ],
    workspaceProfiles: [{
      id: "dealership-standalone-mixed",
      label: "Dealership workspace with public browsing and protected capabilities",
      interaction: workspace.interaction,
      theme: workspace.theme,
      authentication: workspace.authentication,
    }],
    bindingSlots: [
      {
        id: "document-storage",
        kind: "CUSTOMER_S3",
        supportedProfileIds: ["reviewed-s3-read-only"],
        pluginId: "mkp-data-dealership-knowledge-v1",
        installConfigField: "documentStorageBindingRef",
      },
      ...externalBindings.slots,
      ...(aiStateStorageModes.includes(CUSTOMER_AI_STATE_MODE)
        ? [{id: "ai-state-database", kind: "CUSTOMER_AI_STATE", supportedProfileIds: [CUSTOMER_AI_STATE_MODE]}] : []),
      ...(managedInbox ? [{
        id: "inbox",
        kind: "INBOX_OPERATOR",
        supportedProfileIds: ["managed-basic-inbox-keys-v1"],
      }] : []),
    ],
    externalIntegrationProfiles: externalBindings.profiles,
    documentAudiences: ["PUBLIC"],
    admissionPrerequisites: [
      "APPROVED_POLICY", "IMMUTABLE_ARTIFACTS", "STORAGE", "IDENTITY", "CAPACITY",
      "NOTIFICATIONS", "OPERATING_POLICY",
    ],
    limits,
    topology,
    aiStateStorageTopologies,
    approvedPolicyRef: "01044-external-dealership-policy-review-pending",
    operatingPolicyRef: "01036-customer-staging-lifecycle-v1",
    identityPolicyRef: "01036-supabase-mixed-auth-v1",
    storagePolicyRef: "01036-s3-read-only-text-json-v1",
  };
}

export function buildOfferProposal(input) {
  const definitionCandidate = buildDefinitionCandidate(input);
  const modes = input.aiStateStorageModes ?? [MANAGED_AI_STATE_MODE, CUSTOMER_AI_STATE_MODE];
  return {
    schemaVersion: "loomai-external-dealership-offer-proposal/v1",
    publishable: false,
    blockers: ["ATTENDED_IMMUTABLE_OFFER_REVIEW_REQUIRED",
      ...(modes.includes(CUSTOMER_AI_STATE_MODE) ? ["CUSTOMER_AI_STATE_HOSTED_GENERATION_ACCEPTANCE_REQUIRED"] : []),
      ...(!input.managedInbox ? ["EXTERNAL_INBOX_TARGET_BINDING_REQUIRED"] : [])],
    definitionCandidate,
    externalBindingRequirements: [
      ...structuredClone(EXTERNAL_BINDING_REQUIREMENTS),
      ...(!input.managedInbox ? [{id: "conversation-inbox", pluginId: "mkp-integration-basic-loomai-inbox",
        configFields: ["providerBaseUrl", "operatorConsoleUrl", "bindingId"],
        secretRefFields: ["providerApiKeySecretRef", "runtimeCallbackSigningSecretRef", "providerWebhookSigningSecretRef"],
        operatorLifecycle: "EXTERNAL_OWNER_MANAGED_NOT_PLATFORM_ENROLLMENT",
      }] : []),
    ],
    externalBindingProtocol: {
      staffApprovalPath: "/api/customer-self-service/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals",
      customerBindingPath: "/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/bind",
      customerSummaryPath: "/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations",
      customerRevocationPath: "/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations/revoke",
      scopeFields: ["versionId", "targetProfileId", "bindingSlotId", "selectedProfileId"],
      approvalFields: ["config", "accountScope", "reviewReference", "operationKey"],
      bindingFields: ["approvalId", "expectedEntitlementRevision", "operationKey", "credentials"],
      revocationFields: ["bindingId", "expectedBindingRevision", "expectedEntitlementRevision", "operationKey"],
      credentials: "WRITE_ONLY_VALUES_AT_CUSTOMER_API; NEVER_GLOBAL_INSTALL_VALUES_OR_CLIENT_SECRET_REFERENCES",
      rotation: "NEW_IMMUTABLE_VERSION_AND_TARGET_SCOPED_APPROVAL_BINDING",
      readiness: "LOCAL_CONFIGURATION_ONLY_NOT_PROVIDER_ACCESS_OR_HEALTH",
      setupOnly: {
        unresolvedRequirementsPath: "behaviorConfig.marketplaceRequirements.unresolvedRequiredPluginRefs",
        sourceDependency: "DECLARED_ACTION_SOURCE_REF_TO_UNAVAILABLE_EXPLICITLY_REQUIRED_DATA_ONLY",
        ordinaryCompilation: "STRICT_NO_SETUP_DEFERRAL",
        initializer: "POST_ASSOCIATION_TRUSTED_ALIAS_ONLY_INSTALL_ENABLE_AND_COMPILER_RESYNC",
        accountBinding: "NOT_CREATED_BY_BOOTSTRAP_OR_INITIALIZER",
      },
    },
    aiOperationalPersistence: {
      required: true, selectedMode: input.aiStateStorageMode,
      hostingDecision: "OPTIONAL_LOOMAI_HOSTING_OWNER_APPROVED_EXPLICIT_SELECTION_REQUIRED",
      modes: modes.map(id => ({id, topologyDeclared: true, localContractVerified: true, hostedAcceptanceVerified: false,
        ...(id === CUSTOMER_AI_STATE_MODE ? {blocker: "CUSTOMER_AI_STATE_HOSTED_GENERATION_ACCEPTANCE_REQUIRED"} : {})})),
      customerBindingPath: "/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/ai-state-storage-binding",
      customerBindingFields: ["expectedEntitlementRevision", "operationKey", "targetProfileId", "bindingSlotId", "connection"],
      customerBindingSlotId: "ai-state-database",
      approvalReference: "SERVER_DERIVED_CALLER_MUST_OMIT_OR_NULL",
      createSettingIntegration: "AI_STATE_STORAGE_MODE; bootstrap request aiStateStorageMode; draft.providerConfig.aiStateStorageMode.",
      capacityBasis: "Hosted AI-state database counted only for the explicitly selected managed profile; no business database.",
    },
    managedInbox: input.managedInbox,
    hostedReadinessGates: [...HOSTED_READINESS_GATES],
    externalSystemsLifecycle: "RETAIN_EXTERNAL_NO_CREATE_SEED_RESET_DELETE",
    liveReadinessProven: false,
  };
}

export function buildOfferDefinition(input, review) {
  const proposal = buildOfferProposal(input);
  requireCustomerOfferPublicationSupport(input);
  requireAttendedOfferReview(review);
  const inbox = requireReviewedInboxArtifact(input.inboxArtifact);
  const current = input.inboxSpec;
  requireKeys(current, ["publication", "spec"], "REVIEWED_INBOX_SPEC_REQUIRED");
  const publication = current.publication;
  if (publication?.templateVersionId !== input.template.id || publication.specId !== SPEC_ID
    || publication.fingerprint !== review.inboxSpecFingerprint
    || typeof publication.reviewReference !== "string" || !publication.reviewReference.trim()
    || !Number.isFinite(Date.parse(publication.reviewedAt))) throw new Error("REVIEWED_INBOX_SPEC_REQUIRED");
  try { validateManagedDependencySpec(current.spec); }
  catch { throw new Error("REVIEWED_INBOX_SPEC_CHANGED"); }
  if (hash(comparableJson(current.spec)) !== hash(comparableJson(buildManagedDependencySpec({inbox}, {managedInbox: true})))) {
    throw new Error("REVIEWED_INBOX_SPEC_CHANGED");
  }
  const definition = structuredClone(proposal.definitionCandidate);
  definition.name = review.name;
  for (const field of ["approvedPolicyRef", "operatingPolicyRef", "identityPolicyRef", "storagePolicyRef"]) definition[field] = review[field];
  definition.limits.measuredPolicyRef = review.measuredPolicyRef;
  definition.artifacts.push({artifactId: inbox.id, fingerprint: artifactFingerprint(inbox)});
  // Declared promotion permissions do not bypass target bindings, admission,
  // financial authorization or readiness. Preserve the same-customer journey.
  return normalizeOfferDefinition(definition);
}

export function requireAttendedOfferReview(review) {
  requireKeys(review, ["contract", "offerId", "name", "reviewReference", "approvedPolicyRef", "measuredPolicyRef",
    "operatingPolicyRef", "identityPolicyRef", "storagePolicyRef", "inboxSpecFingerprint"], "ATTENDED_OFFER_REVIEW_INVALID");
  if (review.contract !== ATTENDED_CANARY_CONTRACT) throw new Error("ATTENDED_OFFER_REVIEW_INVALID");
  requireIdentifier(review.offerId, 64);
  for (const field of ["name", "reviewReference", "approvedPolicyRef", "measuredPolicyRef", "operatingPolicyRef",
    "identityPolicyRef", "storagePolicyRef"]) {
    if (typeof review[field] !== "string" || !review[field].trim() || review[field] !== review[field].trim()
      || review[field].length > 255 || /[\u0000-\u001f\u007f]/.test(review[field])) throw new Error("ATTENDED_OFFER_REVIEW_INVALID");
  }
  requireDigest(review.inboxSpecFingerprint);
}

export function requireKeys(value, keys, code) {
  if (!value || typeof value !== "object" || Array.isArray(value)
    || hash(Object.keys(value).sort()) !== hash([...keys].sort())) throw new Error(code);
}

export function requireIdentifier(value, maxLength = 128) {
  if (typeof value !== "string" || value.length > maxLength || !/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(value)) {
    throw new Error("REVIEWED_IDENTIFIER_REQUIRED");
  }
  return value;
}

export function requireDigest(value) {
  if (typeof value !== "string" || !/^[a-f0-9]{64}$/.test(value)) throw new Error("REVIEWED_SHA256_REQUIRED");
  return value;
}

// TreeSet<Operation/Prerequisite> uses declaration order, not alphabetical order.
const OPERATION_ORDER = ["TEMPLATE_SELECT", "BOOTSTRAP_CONFIGURATION", "BOOTSTRAP", "READ", "DRAFT_EDIT", "VERSION_PUBLISH",
  "BINDING_REPLACE", "BINDING_REVOKE", "INBOX_OPERATOR_ENROLL", "INBOX_OPERATOR_ROTATE", "INBOX_OPERATOR_DELIVER",
  "INBOX_OPERATOR_REVOKE", "SOURCE_DISCOVER", "SOURCE_PREVIEW", "SOURCE_INDEX", "SOURCE_REFRESH", "SOURCE_REMOVE",
  "APP_CONFIGURE", "DOMAIN_CONFIGURE", "AUTH_CONFIGURE", "APPLY", "VERIFY", "PROMOTION_PLAN", "PROMOTION_APPLY",
  "ACTIVATE", "ROLLBACK", "STAGING_RETAIN", "STAGING_EXTEND", "TARGET_RETIRE"];
const PREREQUISITE_ORDER = ["APPROVED_POLICY", "IMMUTABLE_ARTIFACTS", "STORAGE", "IDENTITY", "CAPACITY", "NOTIFICATIONS", "OPERATING_POLICY"];

export function normalizeOfferDefinition(definition) {
  const result = structuredClone(definition);
  function ordered(values, declaration) {
    if (!Array.isArray(values) || new Set(values).size !== values.length
      || values.some(value => !declaration.includes(value))) throw new Error("OFFER_ENUM_CONTRACT_INVALID");
    return declaration.filter(value => values.includes(value));
  }
  result.operations = ordered(result.operations, OPERATION_ORDER);
  result.admissionPrerequisites = ordered(result.admissionPrerequisites, PREREQUISITE_ORDER);
  result.documentAudiences = ordered(result.documentAudiences, ["PUBLIC", "AUTHENTICATED"]);
  result.settings.forEach(rule => { rule.allowedValues.sort(); });
  result.bindingSlots.forEach(slot => {
    slot.supportedProfileIds.sort();
    slot.pluginId ??= null;
    slot.installConfigField ??= null;
  });
  result.externalIntegrationProfiles.forEach(profile => {
    for (const field of ["targetProfileIds", "configFields", "secretFields", "endpointFields", "accountScopeFields", "allowedHosts"]) profile[field].sort();
  });
  return result;
}

export function requireTemplateRuntimeSupport(template, artifact,
  {behaviorCatalog, executionExtensionCatalog} = {}) {
  const behavior = template?.manifest?.contributions?.template?.deploymentBehavior;
  const manifest = artifact?.capabilityManifest;
  if (!behavior || typeof behavior.type !== "string" || !behavior.type.trim()
    || !manifest || !Array.isArray(manifest.supportedBehaviorTypes)
    || !manifest.supportedBehaviorTypes.includes(behavior.type)) {
    throw new Error("The reviewed runtime artifact must support the template's declared behavior.");
  }
  for (const [field, supportedField] of Object.entries({
    requiredRuntimeCapabilityIds: "capabilities",
    allowedExecutionExtensions: "supportedExecutionExtensions",
    defaultExecutionExtensions: "supportedExecutionExtensions",
    allowedChannelBindings: "supportedChannelBindings",
    verificationPackIds: "verificationPackIds",
  })) {
    const required = behavior[field];
    const supported = manifest[supportedField];
    if (!Array.isArray(required) || required.some(value => typeof value !== "string" || !value.trim())
      || !Array.isArray(supported) || required.some(value => !supported.includes(value))) {
      throw new Error(`The reviewed runtime artifact does not support the template contract: ${field}.`);
    }
  }

  function strings(value, field) {
    if (!Array.isArray(value) || value.some(item => typeof item !== "string" || !item.trim())) {
      throw new Error(`Invalid runtime catalog contract: ${field}.`);
    }
    return value;
  }

  function catalogEntry(catalog, code, name) {
    if (!Array.isArray(catalog)) throw new Error(`The backend ${name} catalog is required.`);
    const matches = catalog.filter(entry => entry?.code === code);
    if (matches.length !== 1) throw new Error(`The backend ${name} catalog must contain exactly one ${code} contract.`);
    return matches[0];
  }

  function requireDeclarations(required, field, origin) {
    const supported = strings(manifest[field], `manifest.${field}`);
    const missing = required.filter(value => !supported.includes(value));
    if (missing.length > 0) {
      throw new Error(`The reviewed runtime artifact is missing ${origin} catalog requirements in ${field}: ${missing.join(", ")}.`);
    }
  }

  function requireCatalogFields(contract, fields) {
    for (const [field, supportedField] of Object.entries(fields)) {
      requireDeclarations(strings(contract[field], `${contract.code}.${field}`), supportedField, contract.code);
    }
  }

  const base = catalogEntry(behaviorCatalog, behavior.type, "behavior");
  requireCatalogFields(base, {
    activationSources: "supportedActivationSources",
    requiredRuntimeCapabilities: "capabilities",
    requiredRuntimeEndpointClasses: "endpointClasses",
    requiredRuntimeMigrationIds: "migrationIds",
    baselineVerificationPackIds: "verificationPackIds",
  });
  const allowedChannels = strings(base.channelBindings, `${base.code}.channelBindings`);
  if (behavior.allowedChannelBindings.some(channel => !allowedChannels.includes(channel))) {
    throw new Error("The template channel bindings are outside the backend behavior catalog contract.");
  }
  const allowedExtensions = strings(base.allowedExecutionExtensions, `${base.code}.allowedExecutionExtensions`);
  if (behavior.defaultExecutionExtensions.some(code => !behavior.allowedExecutionExtensions.includes(code))) {
    throw new Error("The template default execution extensions must be allowed by its contract.");
  }
  const selectedExtensions = new Set([...behavior.allowedExecutionExtensions, ...behavior.defaultExecutionExtensions]);
  if (!Array.isArray(executionExtensionCatalog)) throw new Error("The backend execution-extension catalog is required.");
  for (const code of selectedExtensions) {
    const extension = catalogEntry(executionExtensionCatalog, code, "execution-extension");
    if (!allowedExtensions.includes(code)
      || !strings(extension.compatibleBehaviorTypes, `${code}.compatibleBehaviorTypes`).includes(behavior.type)) {
      throw new Error(`Execution extension ${code} is not compatible with the backend ${behavior.type} behavior contract.`);
    }
    requireCatalogFields(extension, {
      requiredRuntimeCapabilities: "capabilities",
      requiredRuntimeEndpointClasses: "endpointClasses",
      requiredRuntimeMigrationIds: "migrationIds",
      verificationPackIds: "verificationPackIds",
    });
  }

  if (!Array.isArray(base.requiredSpecialistBundles) || !Array.isArray(manifest.specialistBundles)) {
    throw new Error("The runtime catalog and artifact must declare specialistBundles.");
  }
  for (const required of base.requiredSpecialistBundles) {
    if (!required || typeof required.bundleId !== "string" || !required.bundleId.trim()) {
      throw new Error("Invalid runtime catalog specialist bundle contract.");
    }
    const matches = manifest.specialistBundles.filter(bundle => bundle?.bundleId === required.bundleId);
    const actual = matches.length === 1 ? matches[0] : null;
    const scalarFields = ["contractVersion", "contentHash"];
    const arrayFields = ["behaviorTypes", "specialistRefs", "chainRefs", "resourceLocations"];
    const sameScalars = scalarFields.every(field => typeof required[field] === "string"
      && required[field].trim() && actual?.[field] === required[field]);
    const sameArrays = arrayFields.every(field => {
      const expected = strings(required[field], `${required.bundleId}.${field}`);
      const values = actual?.[field];
      return Array.isArray(values) && values.length === new Set(values).size
        && new Set(expected).size === values.length && expected.every(value => values.includes(value));
    });
    if (!sameScalars || !sameArrays) {
      throw new Error(`The reviewed runtime artifact is missing the exact catalog specialist bundle: ${required.bundleId}.`);
    }
  }
}
