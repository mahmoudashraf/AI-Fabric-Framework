import {createHash} from "node:crypto";

export const OFFER_ID = "offer-loomai-engage-simulated-dealership-early-access-v3";
export const TEMPLATE_PLUGIN_ID = "mkp-template-simulated-dealership-experience-v1";
export const TEMPLATE_VERSION = "1.3.0";
export const RUNTIME_IMAGE_DIGEST = "sha256:c15853a59ad2f7d7b7b42fc9ba0a8e77c7c5b6007419ac755be2b5e2ac11af0f";
export const RUNTIME_SOURCE_COMMIT = "980fc9d75bfea761d07808f86af9e0386e90ab66";
export const STAGING_TARGET_ID = "dtp-coolify-staging-behavior";
export const PRODUCTION_TARGET_ID = "dtp-coolify-production-behavior";

export const REVIEWED_LIMITS = Object.freeze({
  measuredPolicyRef: "01036-dealership-capacity-2026-10-15",
  activeStagingTargets: 1,
  replicas: 1,
  cpuMillis: 3000,
  memoryMiB: 4096,
  diskMiB: 16384,
  databases: 2,
  indexes: 2,
  dependencies: 3,
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
  return hash({
    id: profile.id,
    providerType: profile.providerType,
    sourceStrategy: profile.sourceStrategy,
    credentialRefId: profile.credentialRefId ?? "",
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

export function buildOfferDefinition({template, pluginVersions, artifact, targets, auth}) {
  const requiredRefs = template?.contributions?.templateRequiredPluginRefs ?? [];
  const byRef = new Map(pluginVersions.map((version) => [`${version.pluginId}@${version.version}`, version]));
  if (requiredRefs.length === 0 || requiredRefs.some((reference) => !byRef.has(reference))) {
    throw new Error("The template's exact required plugin versions were not supplied.");
  }
  if (!artifact?.id || artifact.artifactRole !== "RUNTIME"
    || artifact.imageDigest !== RUNTIME_IMAGE_DIGEST || artifact.gitCommitSha !== RUNTIME_SOURCE_COMMIT
    || !["staging", "production"].includes(artifact.promotionChannel)
    || artifact.capabilityManifest?.aiFabricVersion !== "0.8.33") {
    throw new Error("The exact promoted AI Fabric 0.8.33 runtime artifact is required.");
  }
  const targetById = new Map(targets.map((target) => [target.id, target]));
  const staging = targetById.get(STAGING_TARGET_ID);
  const production = targetById.get(PRODUCTION_TARGET_ID);
  if (!staging?.active || !production?.active) throw new Error("Both reviewed target profiles must be active.");
  const workspace = workspaceProfile(auth);
  return {
    schemaVersion: "1",
    name: "LoomAI Engage Dealership Experience (Simulated Auto Trader)",
    templateId: "custom-start-from-scratch",
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
    approvedPolicyRef: "01036-simulated-dealership-offer-review-2026-10-15",
    operatingPolicyRef: "01036-customer-staging-lifecycle-v1",
    identityPolicyRef: "01036-supabase-mixed-auth-v1",
    storagePolicyRef: "01036-s3-read-only-text-json-v1",
  };
}
