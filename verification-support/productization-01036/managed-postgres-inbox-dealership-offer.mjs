import {artifactFingerprint, BASE_TEMPLATE_ID, buildExternalIntegrationBindings, CUSTOMER_PLUGIN_REFS,
  CUSTOMER_PLUGIN_VERSION_IDS, hash, HOSTED_READINESS_GATES, KNOWLEDGE_INSTALL_DEFAULTS,
  MANAGED_INBOX_INSTALL_DEFAULTS, MANAGED_INBOX_TEMPLATE_PLUGIN_ID, normalizeOfferDefinition,
  OPENAI_MAX_TOKENS, profileFingerprint, PRODUCTION_TARGET_ID, requireDigest, requireKeys,
  requireTemplateRuntimeSupport, RUNTIME_FRAMEWORK_VERSION, RUNTIME_IMAGE_DIGEST, RUNTIME_SOURCE_COMMIT,
  STAGING_TARGET_ID, workspaceProfile} from "./simulated-dealership-offer.mjs";
import {requireConcreteIdentifier, requirePostgresInboxPublication} from "./managed-postgres-inbox-spec.mjs";

export const POSTGRES_PACKAGE_GATES = Object.freeze([
  "EXACT_KNOWN_NO_EFFECT_SETTLEMENT_AND_EXTERNAL_BINDING_OWNERSHIP_REQUIRED",
  "SAME_CUSTOMER_NEW_OFFER_ENTITLEMENT_AND_DEPLOYMENT_REQUIRED",
  "ACTUAL_INBOX_POSTGRES_MIGRATION_RESTART_RESTORE_REQUIRED", ...HOSTED_READINESS_GATES,
]);
const MODES = ["MANAGED_POSTGRES", "CUSTOMER_POSTGRES"];
export const check = (condition, code) => { if (!condition) throw new Error(code); };
export const same = (left, right, code) => check(hash(left) === hash(right), code);

export function text(value, max = 255) {
  check(typeof value === "string" && value.trim() === value && value.length > 0 && value.length <= max
    && !/[\u0000-\u001f\u007f]/.test(value) && !/pending/i.test(value), "EXPLICIT_REVIEWED_TEXT_REQUIRED");
  return value;
}

export function noCredentialValues(value) {
  if (!value || typeof value !== "object") return;
  for (const [name, nested] of Object.entries(value)) {
    check(!["apiKey", "sessionCookie", "authorization", "secretValue", "password", "accessToken", "privateKey",
      "serviceRoleKey", "clientSecret", "openaiApiKey", "milvusToken", "milvusUsername", "milvusPassword"].includes(name),
    "CREDENTIAL_VALUES_FORBIDDEN_IN_PUBLICATION_INPUT");
    noCredentialValues(nested);
  }
}

export function postgresDealershipTopology(mode) {
  check(MODES.includes(mode), "EXPLICIT_AI_STATE_MODE_REQUIRED");
  const managed = mode === "MANAGED_POSTGRES";
  return {replicas: 1, cpuMillis: managed ? 2250 : 2000, memoryMiB: managed ? 4096 : 3584,
    diskMiB: managed ? 6144 : 4096, databases: managed ? 2 : 1, indexes: 2, dependencies: 2, concurrentApplies: 1};
}

export function requirePublishedVersion(version) {
  requireConcreteIdentifier(version?.id, 64);
  check(version.status === "PUBLISHED" && Number.isFinite(Date.parse(version.publishedAt)), "PUBLISHED_NATIVE_VERSION_REQUIRED");
  check(version.manifest?.pluginId === version.pluginId && version.manifest.version === version.version,
    "MANIFEST_VERSION_IDENTITY_CHANGED");
  return version;
}

export function requireInboxTemplateContract(template) {
  const manifest = template?.manifest;
  const contribution = manifest?.contributions?.template;
  const provider = contribution?.providerConfig;
  check(template?.pluginId === MANAGED_INBOX_TEMPLATE_PLUGIN_ID && manifest?.pluginId === template.pluginId,
    "REVIEWED_INBOX_TEMPLATE_FAMILY_REQUIRED");
  check(provider?.openaiMaxTokens === OPENAI_MAX_TOKENS && provider.vectorStrategy === "milvus"
    && provider.vectorProvisioningMode === "EXTERNAL_EXISTING" && provider.vectorStoragePosture === "SHARED"
    && provider.milvusExternalBindingRequired === true && provider.milvusAuthenticationType === "TOKEN",
    "EXACT_NATIVE_EXTERNAL_TOKEN_VECTOR_CONTRACT_REQUIRED");
  for (const field of ["milvusHost", "milvusRuntimeTokenSecretName", "milvusRuntimeUsernameSecretName", "milvusRuntimePasswordSecretName"])
    check(!Object.hasOwn(provider, field), "EXTERNAL_VECTOR_BINDING_MUST_REMAIN_UNBOUND");
  same(contribution.requiredPluginRefs, [...CUSTOMER_PLUGIN_REFS], "EXACT_REQUIRED_PLUGINS_REQUIRED");
  same(contribution.requiredPluginInstallDefaults, {
    "mkp-data-dealership-knowledge-v1@1.0.0": KNOWLEDGE_INSTALL_DEFAULTS,
    "mkp-integration-basic-loomai-inbox@1.3.0": MANAGED_INBOX_INSTALL_DEFAULTS,
  }, "UNBOUND_BUSINESS_AND_EXPLICIT_MANAGED_INBOX_DEFAULTS_REQUIRED");
  noCredentialValues(manifest);
  return template;
}

/** Append a new version to the existing publisher-owned family; never pretend a version ID was created. */
export function buildPostgresInboxTemplateManifest(sourceTemplate, versionLabel) {
  requirePublishedVersion(sourceTemplate); requireInboxTemplateContract(sourceTemplate);
  check(sourceTemplate.version === "1.0.0" && sourceTemplate.id === "mkv-template-dealership-external-systems-managed-inbox-v1-0",
    "EXACT_ORIGINAL_TEMPLATE_SOURCE_REQUIRED");
  check(typeof versionLabel === "string" && /^\d+\.\d+\.\d+$/.test(versionLabel)
    && versionLabel !== sourceTemplate.version, "NEW_IMMUTABLE_TEMPLATE_VERSION_LABEL_REQUIRED");
  const manifest = structuredClone(sourceTemplate.manifest);
  manifest.version = versionLabel;
  manifest.displayName = "Dealership AI - PostgreSQL Managed Inbox";
  manifest.description = "AI-only operational state with a separately reviewed owned PostgreSQL Inbox. Stock and business actions remain external. PostgreSQL hosting and recovery require actual qualification.";
  return manifest;
}

function pin(version) { return {pluginId: version.pluginId, versionId: version.id, manifestSha256: hash(version.manifest)}; }

function requireRuntime(artifact) {
  requireConcreteIdentifier(artifact?.id); requireDigest(artifact.capabilityManifestHash);
  check(/^dsa-[a-f0-9]{8}$/.test(artifact.id) && artifact.serviceName === "ai-fabric-runtime"
    && artifact.artifactType === "DOCKER_IMAGE" && artifact.artifactRole === "RUNTIME"
    && artifact.imageRepository === "ghcr.io/mahmoudashraf/ai-fabric-runtime"
    && artifact.imageDigest === RUNTIME_IMAGE_DIGEST && artifact.gitCommitSha === RUNTIME_SOURCE_COMMIT
    && artifact.promotionChannel === "production" && Number.isFinite(Date.parse(artifact.promotedAt))
    && typeof artifact.buildRunId === "string" && artifact.buildRunId.trim()
    && artifact.capabilityManifest?.aiFabricVersion === RUNTIME_FRAMEWORK_VERSION, "EXACT_PRODUCTION_RUNTIME_REQUIRED");
  for (const [field, required] of Object.entries({capabilities: "deployment-local-provider-budget-v1",
    endpointClasses: "provider-budget-administration", migrationIds: "provider-budget-accounting-v1",
    verificationPackIds: "provider-budget-hard-limit-v1"}))
    check(artifact.capabilityManifest[field]?.includes(required), "RUNTIME_PROVIDER_BUDGET_CONTRACT_REQUIRED");
  check(artifact.capabilityManifest.capabilities.includes("milvus-token-authentication-v1"), "RUNTIME_NATIVE_TOKEN_REQUIRED");
}

function requireTarget(target, id, environment) {
  check(target?.id === id && target.environmentName === environment && target.active === true
    && target.providerType === "COOLIFY" && target.sourceStrategy === "IMAGE_SOURCE", "EXACT_ACTIVE_TARGET_REQUIRED");
  const expected = {runtimeLimitsCpus: "1.0", runtimeLimitsMemory: "1g", connectorLimitsCpus: "0.25",
    connectorLimitsMemory: "768m", vectorizationRunnerLimitsCpus: "0.25", vectorizationRunnerLimitsMemory: "768m",
    runtimeDatabaseMode: "COOLIFY_POSTGRES", runtimeDatabaseCpuMillicores: 250, runtimeDatabaseMemoryMiB: 512,
    runtimeDatabaseDiskMiB: 2048};
  for (const [key, value] of Object.entries(expected))
    check(String(target.resourceDefaults?.[key]) === String(value), "REVIEWED_TARGET_CAPACITY_CHANGED");
}

export function buildPostgresDealershipOffer({template, pluginVersions, artifact, targets, inboxArtifact,
  inboxSpec, specId, configuration, review, behaviorCatalog, executionExtensionCatalog}) {
  noCredentialValues({configuration, review, template, pluginVersions, artifact, targets, inboxArtifact, inboxSpec});
  requireKeys(configuration, ["aiStateStorageMode", "auth", "externalIntegrationAllowedHosts"], "PACKAGE_CONFIGURATION_INVALID");
  const mode = configuration.aiStateStorageMode;
  const topology = postgresDealershipTopology(mode);
  requireKeys(review, ["name", "reviewReference", "approvedPolicyRef", "measuredPolicyRef", "operatingPolicyRef",
    "identityPolicyRef", "storagePolicyRef", "stagingExpiryHours", "maxExtensionHours", "recoveryWindowHours"], "OFFER_REVIEW_INVALID");
  for (const field of ["name", "reviewReference", "approvedPolicyRef", "measuredPolicyRef", "operatingPolicyRef", "identityPolicyRef", "storagePolicyRef"])
    text(review[field]);
  for (const field of ["stagingExpiryHours", "maxExtensionHours", "recoveryWindowHours"])
    check(Number.isSafeInteger(review[field]) && review[field] > 0 && review[field] <= 24, "BOUNDED_REVIEWED_LIFETIME_REQUIRED");
  requirePublishedVersion(template); requireInboxTemplateContract(template);
  requirePostgresInboxPublication(inboxSpec, {templateVersionId: template.id, specId, inboxArtifact});
  check(template.version !== "1.0.0", "NEW_IMMUTABLE_TEMPLATE_REQUIRED");
  requireRuntime(artifact); requireTemplateRuntimeSupport(template, artifact, {behaviorCatalog, executionExtensionCatalog});
  check(Array.isArray(pluginVersions) && pluginVersions.length === CUSTOMER_PLUGIN_REFS.length, "EXACT_PLUGIN_PINS_REQUIRED");
  const plugins = CUSTOMER_PLUGIN_REFS.map(reference => {
    const [id, versionLabel] = reference.split("@");
    const matches = pluginVersions.filter(value => value.pluginId === id && value.version === versionLabel);
    check(matches.length === 1 && matches[0].id === CUSTOMER_PLUGIN_VERSION_IDS[id], "EXACT_PLUGIN_PINS_REQUIRED");
    return requirePublishedVersion(matches[0]);
  });
  check(Array.isArray(targets) && targets.length === 2, "EXACT_REVIEWED_TARGETS_REQUIRED");
  const staging = targets.find(value => value.id === STAGING_TARGET_ID);
  const production = targets.find(value => value.id === PRODUCTION_TARGET_ID);
  requireTarget(staging, STAGING_TARGET_ID, "staging"); requireTarget(production, PRODUCTION_TARGET_ID, "production");
  const external = buildExternalIntegrationBindings({pluginVersions: plugins,
    targetProfileIds: [staging.id, production.id], allowedHosts: configuration.externalIntegrationAllowedHosts});
  requireKeys(configuration.auth, ["projectUrl", "publishableKey", "dealerId", "imageHostAllowlist"], "PUBLIC_WORKSPACE_CONFIGURATION_REQUIRED");
  const authUrl = new URL(configuration.auth.projectUrl);
  check(authUrl.protocol === "https:" && authUrl.pathname === "/" && !authUrl.username && !authUrl.password
    && !authUrl.search && !authUrl.hash, "PUBLIC_WORKSPACE_CONFIGURATION_REQUIRED");
  check(typeof configuration.auth.publishableKey === "string" && /^sb_publishable_[A-Za-z0-9_-]+$/.test(configuration.auth.publishableKey),
    "PUBLIC_PUBLISHABLE_KEY_ONLY");
  const workspace = workspaceProfile(configuration.auth);
  const definition = {schemaVersion: "1", name: review.name, templateId: BASE_TEMPLATE_ID, template: pin(template),
    plugins: plugins.map(pin), artifacts: [artifact, inboxArtifact].map(value => ({artifactId: value.id, fingerprint: artifactFingerprint(value)})),
    targets: [{targetProfileId: staging.id, intent: "CUSTOMER_STAGING", profileFingerprint: profileFingerprint(staging)},
      {targetProfileId: production.id, intent: "CUSTOMER_PRODUCTION", profileFingerprint: profileFingerprint(production)}],
    operations: ["TEMPLATE_SELECT", "BOOTSTRAP_CONFIGURATION", "BOOTSTRAP", "READ", "VERSION_PUBLISH", "BINDING_REPLACE", "BINDING_REVOKE",
      "INBOX_OPERATOR_ENROLL", "INBOX_OPERATOR_ROTATE", "INBOX_OPERATOR_DELIVER", "INBOX_OPERATOR_REVOKE", "SOURCE_DISCOVER", "SOURCE_PREVIEW",
      "SOURCE_INDEX", "SOURCE_REFRESH", "SOURCE_REMOVE", "APP_CONFIGURE", "DOMAIN_CONFIGURE", "AUTH_CONFIGURE", "APPLY", "VERIFY",
      "PROMOTION_PLAN", "PROMOTION_APPLY", "ACTIVATE", "ROLLBACK", "STAGING_RETAIN", "STAGING_EXTEND", "TARGET_RETIRE"],
    settings: [{setting: "DEPLOYMENT_NAME", type: "TEXT", maxLength: 100, allowedValues: []},
      {setting: "AI_STATE_STORAGE_MODE", type: "CHOICE", maxLength: 32, allowedValues: [mode]},
      {setting: "APP_TITLE", type: "TEXT", maxLength: 120, allowedValues: []},
      ...[["INTERACTION_PROFILE_ID", workspace.interactionProfileId], ["THEME_PROFILE_ID", workspace.themeProfileId], ["AUTH_PROFILE_ID", workspace.authProfileId]]
        .map(([setting, value]) => ({setting, type: "CHOICE", maxLength: 100, allowedValues: [value]})),
      {setting: "APP_DOMAIN", type: "TEXT", maxLength: 255, allowedValues: []},
      {setting: "DOCUMENT_AUDIENCE", type: "CHOICE", maxLength: 32, allowedValues: ["PUBLIC"]}],
    workspaceProfiles: [{id: "dealership-standalone-mixed", label: "Dealership workspace with protected capabilities",
      interaction: workspace.interaction, theme: workspace.theme, authentication: workspace.authentication}],
    bindingSlots: [{id: "document-storage", kind: "CUSTOMER_S3", supportedProfileIds: ["reviewed-s3-read-only"],
      pluginId: "mkp-data-dealership-knowledge-v1", installConfigField: "documentStorageBindingRef"}, ...external.slots,
      ...(mode === "CUSTOMER_POSTGRES" ? [{id: "ai-state-database", kind: "CUSTOMER_AI_STATE", supportedProfileIds: [mode]}] : []),
      {id: "inbox", kind: "INBOX_OPERATOR", supportedProfileIds: ["managed-basic-inbox-keys-v1"]}],
    externalIntegrationProfiles: external.profiles, documentAudiences: ["PUBLIC"],
    admissionPrerequisites: ["APPROVED_POLICY", "IMMUTABLE_ARTIFACTS", "STORAGE", "IDENTITY", "CAPACITY", "NOTIFICATIONS", "OPERATING_POLICY"],
    limits: {measuredPolicyRef: review.measuredPolicyRef, activeStagingTargets: 1, ...topology,
      stagingExpiryHours: review.stagingExpiryHours, maxExtensionHours: review.maxExtensionHours, recoveryWindowHours: review.recoveryWindowHours},
    topology, aiStateStorageTopologies: {[mode]: structuredClone(topology)},
    approvedPolicyRef: review.approvedPolicyRef, operatingPolicyRef: review.operatingPolicyRef,
    identityPolicyRef: review.identityPolicyRef, storagePolicyRef: review.storagePolicyRef};
  return normalizeOfferDefinition(definition);
}
