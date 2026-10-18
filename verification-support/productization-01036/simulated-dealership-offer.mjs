import {createHash} from "node:crypto";

import {reviewedManagedDependencyCapacity} from "./simulated-dealership-managed-spec.mjs";

export const OFFER_ID = "offer-loomai-engage-simulated-dealership-early-access-v7";
export const TEMPLATE_PLUGIN_ID = "mkp-template-simulated-dealership-experience-v1";
export const TEMPLATE_VERSION = "1.5.0";
export const BASE_TEMPLATE_ID = "dev-openai-milvus";
export const RUNTIME_IMAGE_DIGEST = "sha256:d7cc5b3a7d72f65da0f77c02ae50a27d836b8599908566b109b5a07bd5e6f16f";
export const RUNTIME_SOURCE_COMMIT = "bf72080847c960c331d3ec6a2441e8d5f38ca56a";
export const STAGING_TARGET_ID = "dtp-coolify-staging-behavior";
export const PRODUCTION_TARGET_ID = "dtp-coolify-production-behavior";

export const REVIEWED_PLATFORM_CAPACITY = Object.freeze({
  runtime: Object.freeze({milliCpu: 1000, memoryMiB: 1024, diskMiB: 0}),
  connector: Object.freeze({milliCpu: 500, memoryMiB: 768, diskMiB: 0}),
  vectorizationRunner: Object.freeze({milliCpu: 500, memoryMiB: 768, diskMiB: 0}),
  runtimeDatabase: Object.freeze({milliCpu: 250, memoryMiB: 512, diskMiB: 2048}),
});

function sumCapacity(capacities) {
  return capacities.reduce((total, capacity) => ({
    milliCpu: total.milliCpu + capacity.milliCpu,
    memoryMiB: total.memoryMiB + capacity.memoryMiB,
    diskMiB: total.diskMiB + capacity.diskMiB,
  }), {milliCpu: 0, memoryMiB: 0, diskMiB: 0});
}

export function reviewedTopologyCapacity() {
  const managed = reviewedManagedDependencyCapacity();
  const platform = sumCapacity(Object.values(REVIEWED_PLATFORM_CAPACITY));
  return Object.freeze({
    milliCpu: platform.milliCpu + managed.milliCpu,
    memoryMiB: platform.memoryMiB + managed.memoryMiB,
    diskMiB: platform.diskMiB + managed.diskMiB,
    dependencies: managed.resources,
    databases: 2,
    indexes: 2,
  });
}

const REVIEWED_CAPACITY = reviewedTopologyCapacity();

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

export const REVIEWED_LIMITS = Object.freeze({
  measuredPolicyRef: "01036-dealership-capacity-2026-10-15-v2",
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

export function workspaceProfile({projectUrl, publishableKey}) {
  if (!projectUrl || !publishableKey) throw new Error("The reviewed Supabase public configuration is required.");
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
      id: "simulated-dealership",
      sourceMode: "DEALERSHIP_INVENTORY",
      assistantLabel: "Dealership AI",
    },
    knowledge: {inventoryVectorSpace: "dealer-vehicle"},
    capabilities: {callback: true, testDrive: true, comparison: true},
    presentation: {imageHostAllowlist: ["m.atcdn.co.uk"]},
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

const REVIEWED_RESOURCE_DEFAULTS = Object.freeze({
  runtimeLimitsCpus: "1.0",
  runtimeLimitsMemory: "1g",
  runtimeLimitsMemorySwap: "1g",
  runtimeLimitsMemoryReservation: "512m",
  runtimeJavaOpts: "-XX:ActiveProcessorCount=1 -Xms256m -Xmx768m",
  connectorLimitsCpus: "0.5",
  connectorLimitsMemory: "768m",
  connectorLimitsMemorySwap: "768m",
  connectorLimitsMemoryReservation: "384m",
  connectorJavaOpts: "-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m",
  vectorizationRunnerLimitsCpus: "0.5",
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

export function buildOfferDefinition({template, pluginVersions, artifact, targets, auth}) {
  const requiredRefs = template?.contributions?.templateRequiredPluginRefs ?? [];
  const byRef = new Map(pluginVersions.map((version) => [`${version.pluginId}@${version.version}`, version]));
  if (requiredRefs.length === 0 || requiredRefs.some((reference) => !byRef.has(reference))) {
    throw new Error("The template's exact required plugin versions were not supplied.");
  }
  if (!artifact?.id || artifact.artifactRole !== "RUNTIME"
    || artifact.imageDigest !== RUNTIME_IMAGE_DIGEST || artifact.gitCommitSha !== RUNTIME_SOURCE_COMMIT
    || !["staging", "production"].includes(artifact.promotionChannel)
    || artifact.capabilityManifest?.aiFabricVersion !== "0.8.35") {
    throw new Error("The exact promoted AI Fabric 0.8.35 runtime artifact is required.");
  }
  const targetById = new Map(targets.map((target) => [target.id, target]));
  const staging = targetById.get(STAGING_TARGET_ID);
  const production = targetById.get(PRODUCTION_TARGET_ID);
  requireReviewedTarget(staging, STAGING_TARGET_ID);
  requireReviewedTarget(production, PRODUCTION_TARGET_ID);
  const workspace = workspaceProfile(auth);
  return {
    schemaVersion: "1",
    name: "LoomAI Engage Dealership Experience (Simulated Auto Trader)",
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
      "INBOX_OPERATOR_ENROLL", "INBOX_OPERATOR_ROTATE", "INBOX_OPERATOR_DELIVER", "INBOX_OPERATOR_REVOKE",
      "SOURCE_REFRESH", "SOURCE_REMOVE", "APP_CONFIGURE", "DOMAIN_CONFIGURE", "AUTH_CONFIGURE",
      "APPLY", "VERIFY", "PROMOTION_PLAN", "PROMOTION_APPLY", "ACTIVATE", "ROLLBACK",
      "STAGING_RETAIN", "STAGING_EXTEND", "TARGET_RETIRE",
    ],
    settings: [
      {setting: "DEPLOYMENT_NAME", type: "TEXT", maxLength: 100, allowedValues: []},
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
      {
        id: "inbox",
        kind: "INBOX_OPERATOR",
        supportedProfileIds: ["managed-basic-inbox-keys-v1"],
      },
    ],
    documentAudiences: ["PUBLIC"],
    admissionPrerequisites: [
      "APPROVED_POLICY", "IMMUTABLE_ARTIFACTS", "STORAGE", "IDENTITY", "CAPACITY",
      "NOTIFICATIONS", "OPERATING_POLICY",
    ],
    limits: REVIEWED_LIMITS,
    topology: REVIEWED_TOPOLOGY,
    approvedPolicyRef: "01036-simulated-dealership-offer-review-2026-10-15",
    operatingPolicyRef: "01036-customer-staging-lifecycle-v1",
    identityPolicyRef: "01036-supabase-mixed-auth-v1",
    storagePolicyRef: "01036-s3-read-only-text-json-v1",
  };
}
