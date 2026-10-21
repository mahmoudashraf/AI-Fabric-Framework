import {artifactFingerprint, buildExternalIntegrationBindings, CUSTOMER_PLUGIN_REFS, CUSTOMER_PLUGIN_VERSION_IDS,
  hash, MANAGED_INBOX_TEMPLATE_PLUGIN_ID, normalizeOfferDefinition, OPENAI_MAX_TOKENS, profileFingerprint, requireDigest, requireKeys,
  requireTemplateRuntimeSupport, STAGING_TARGET_ID, workspaceProfile} from "./simulated-dealership-offer.mjs";
import {check, noCredentialValues, postgresDealershipTopology, requireInboxTemplateContract,
  requirePublishedVersion, same, text} from "./managed-postgres-inbox-dealership-offer.mjs";
import {buildPostgresInboxSpec, requireConcreteIdentifier, requireNewInboxIdentifiers,
  requirePostgresInboxPublication} from "./managed-postgres-inbox-spec.mjs";

export const QDRANT_TEMPLATE_PLUGIN_ID = MANAGED_INBOX_TEMPLATE_PLUGIN_ID;
export const QDRANT_TEMPLATE_SLUG = "dealership-external-systems-managed-inbox-v1";
export const QDRANT_TEMPLATE_VERSION = "1.2.0";
export const QDRANT_BASE_TEMPLATE_ID = "dev-openai-qdrant";
export const QDRANT_INBOX_ARTIFACT_ID = "dsa-1dc83268";
export const QDRANT_RUNTIME = Object.freeze({id: "dsa-6776d0bf", frameworkVersion: "0.8.36",
  imageDigest: "sha256:a32574d477a1330550d3bb8da3fb4a1a5edddcf13117538736bcbc9c0dace677",
  gitCommitSha: "d8743ee3b66307b4bb26645023b11f0dec64127f"});
export const QDRANT_STAGING_GATES = Object.freeze([
  "MAIN_FREE_ONLY_ACCOUNT_QUOTA_AND_SERVING_KEY_REVIEW_REQUIRED",
  "NEW_CUSTOMER_ENTITLEMENT_DEPLOYMENT_AND_TARGET_SCOPE_REQUIRED",
  "FRESH_CAPACITY_AND_FINANCIAL_AUTHORIZATION_REQUIRED",
  "ACTUAL_TARGET_PREFIX_READBACK_INDEXING_AND_RETRIEVAL_REQUIRED",
  "HOSTED_PG_INBOX_AND_OWNER_WORKSPACE_ACCEPTANCE_REQUIRED",
]);

function publicInput(value) {
  noCredentialValues(value);
  if (!value || typeof value !== "object") return;
  for (const [key, nested] of Object.entries(value)) {
    check(!["qdrantapikey", "providerkey", "providersecret", "receiverapikey", "credentialvalue"]
      .includes(key.toLowerCase()), "CREDENTIAL_VALUES_FORBIDDEN_IN_PUBLICATION_INPUT");
    publicInput(nested);
  }
}

/** QDRANT_API_KEY is an existing resolver purpose, not a new public secret-reference field. */
export function requireReviewedQdrantVector(vector) {
  requireKeys(vector, ["host", "port", "grpcPort", "preferGrpc", "apiKeySecretName"], "EXACT_REVIEWED_VECTOR_REQUIRED");
  publicInput(vector);
  check(typeof vector.host === "string" && vector.host === vector.host.trim() && vector.host.length <= 255,
    "EXACT_REVIEWED_QDRANT_HTTPS_ENDPOINT_REQUIRED");
  let endpoint;
  try { endpoint = new URL(vector.host); } catch { throw new Error("EXACT_REVIEWED_QDRANT_HTTPS_ENDPOINT_REQUIRED"); }
  check(endpoint.protocol === "https:" && endpoint.hostname && !endpoint.username && !endpoint.password
    && endpoint.pathname === "/" && !endpoint.search && !endpoint.hash,
  "EXACT_REVIEWED_QDRANT_HTTPS_ENDPOINT_REQUIRED");
  for (const port of [vector.port, vector.grpcPort])
    check(Number.isSafeInteger(port) && port >= 1 && port <= 65535, "EXACT_REVIEWED_QDRANT_PORTS_REQUIRED");
  // The REST adapter uses the complete HTTPS host URL, not its separate port property.
  check(Number(endpoint.port || 443) === vector.port, "QDRANT_URL_AND_REST_PORT_MUST_MATCH");
  check(vector.preferGrpc === false, "REVIEWED_QDRANT_REST_TRANSPORT_REQUIRED");
  check(vector.apiKeySecretName === "QDRANT_API_KEY", "EXISTING_QDRANT_SERVING_SECRET_PURPOSE_REQUIRED");
  return {...vector, host: endpoint.origin};
}

export function buildFreshQdrantStagingTemplateManifest(sourceTemplate, reviewedVector) {
  publicInput(sourceTemplate);
  requirePublishedVersion(sourceTemplate); requireInboxTemplateContract(sourceTemplate);
  check(sourceTemplate.version === "1.0.0"
    && sourceTemplate.id === "mkv-template-dealership-external-systems-managed-inbox-v1-0",
  "EXACT_ORIGINAL_TEMPLATE_SOURCE_REQUIRED");
  const vector = requireReviewedQdrantVector(reviewedVector);
  const manifest = structuredClone(sourceTemplate.manifest);
  manifest.pluginId = QDRANT_TEMPLATE_PLUGIN_ID;
  manifest.version = QDRANT_TEMPLATE_VERSION;
  manifest.displayName = "Dealership AI - Qdrant Staging / PostgreSQL Inbox";
  manifest.description = "Reviewed staging-only external Qdrant collections with PostgreSQL AI state and Inbox. No migration or production approval.";
  const template = manifest.contributions.template;
  delete template.baseTemplateId;
  template.templateId = QDRANT_BASE_TEMPLATE_ID;
  template.providerConfig = {openaiMaxTokens: OPENAI_MAX_TOKENS, vectorStrategy: "qdrant",
    vectorProvisioningMode: "EXTERNAL_EXISTING", vectorStoragePosture: "SHARED",
    qdrantManagedCollectionsEnabled: false, qdrantHost: vector.host, qdrantPort: vector.port,
    qdrantGrpcPort: vector.grpcPort, qdrantPreferGrpc: vector.preferGrpc};
  if (template.launchProfile) template.launchProfile = {...template.launchProfile, environment: "staging",
    targetProfileId: STAGING_TARGET_ID, requiresVerificationBeforeProduction: true};
  const compatibility = manifest.compatibility ?? {};
  const modes = compatibility.supportedProviderModes ?? [];
  check(Array.isArray(modes) && modes.every(mode => typeof mode === "string"), "SOURCE_PROVIDER_COMPATIBILITY_INVALID");
  manifest.compatibility = {...compatibility, supportedDeploymentTargets: [QDRANT_BASE_TEMPLATE_ID],
    supportedProviderModes: [...modes.filter(mode => !mode.startsWith("vector:")), "vector:qdrant"]};
  return manifest;
}

function requireFreshTemplate({sourceTemplate, reviewedVector, template}) {
  requirePublishedVersion(template);
  check(/^mkv-[a-f0-9]{8}$/.test(template.id) && template.id !== sourceTemplate.id
    && template.pluginId === QDRANT_TEMPLATE_PLUGIN_ID && template.version === QDRANT_TEMPLATE_VERSION,
  "ACTUAL_NEW_QDRANT_TEMPLATE_VERSION_REQUIRED");
  same(template.manifest, buildFreshQdrantStagingTemplateManifest(sourceTemplate, reviewedVector), "QDRANT_TEMPLATE_SOURCE_DRIFT");
  return template;
}

function plan(request, extra = {}) {
  const payload = {schemaVersion: "loomai-fresh-qdrant-staging-plan/v1", ...extra,
    request: {...request, method: "POST", bodySha256: hash(request.body)},
    liveReadinessProven: false, freeQuotaVerified: false, remainingGates: [...QDRANT_STAGING_GATES]};
  return {...payload, planSha256: hash(payload)};
}

/** Descriptors only. No transport, execution, automatic phase advancement or retry. */
export function planFreshQdrantTemplatePublication(input, phase) {
  requireKeys(input, ["sourceTemplate", "reviewedVector", "publisher", "submission", "reviewReference"], "TEMPLATE_PLAN_INPUT_INVALID");
  publicInput(input);
  check(["template-submit", "template-validate", "template-publish"].includes(phase), "EXPLICIT_TEMPLATE_PHASE_REQUIRED");
  const {publisher, submission} = input;
  requireConcreteIdentifier(publisher?.id, 64);
  check(publisher.status === "ACTIVE" && publisher.verificationStatus === "VERIFIED", "VERIFIED_PUBLISHER_REQUIRED");
  const manifest = buildFreshQdrantStagingTemplateManifest(input.sourceTemplate, input.reviewedVector);
  text(input.reviewReference);
  let request;
  if (phase === "template-submit") {
    check(submission === null, "EXISTING_SUBMISSION_REQUIRES_READ_ONLY_RECONCILIATION");
    request = {path: `/api/marketplace/publishers/${publisher.id}/submissions`, body: {
      pluginSlug: QDRANT_TEMPLATE_SLUG, releaseChannel: "EARLY_ACCESS", manifest}};
  } else {
    check(/^mkv-[a-f0-9]{8}$/.test(submission?.pluginVersionId) && submission.publisherId === publisher.id
      && submission.pluginId === QDRANT_TEMPLATE_PLUGIN_ID && submission.pluginSlug === QDRANT_TEMPLATE_SLUG
      && submission.version === QDRANT_TEMPLATE_VERSION && submission.releaseChannel === "EARLY_ACCESS",
    "ACTUAL_QDRANT_SUBMISSION_REQUIRED");
    same(submission.manifest, manifest, "QDRANT_SUBMISSION_SOURCE_DRIFT");
    requireDigest(submission.bundleSha256);
    check(submission.status === (phase === "template-validate" ? "SUBMITTED" : "VALIDATED"), "EXACT_SUBMISSION_PHASE_REQUIRED");
    request = {path: `/api/marketplace/submissions/${submission.pluginVersionId}/${phase === "template-validate" ? "validate" : "publish"}`,
      body: {reviewNotes: input.reviewReference}};
  }
  return plan(request, {phase, nativeVersionId: submission?.pluginVersionId ?? null});
}

export function planFreshQdrantStagingSpec(input) {
  requireKeys(input, ["sourceTemplate", "reviewedVector", "template", "inboxArtifact", "specId", "reviewReference"], "SPEC_PLAN_INPUT_INVALID");
  publicInput(input);
  check(input.inboxArtifact?.id === QDRANT_INBOX_ARTIFACT_ID, "EXACT_REVIEWED_INBOX_ARTIFACT_REQUIRED");
  const template = requireFreshTemplate(input);
  requireNewInboxIdentifiers({templateVersionId: template.id, specId: input.specId});
  text(input.reviewReference);
  return plan({path: `/api/customer-self-service/staff/templates/${template.id}/managed-dependency-spec`,
    body: {specId: input.specId, spec: buildPostgresInboxSpec({inbox: input.inboxArtifact}), reviewReference: input.reviewReference}},
  {phase: "spec", nativeVersionId: template.id});
}

function requireRuntime(artifact, template, catalogs) {
  check(artifact?.id === QDRANT_RUNTIME.id && artifact.serviceName === "ai-fabric-runtime"
    && artifact.artifactType === "DOCKER_IMAGE" && artifact.artifactRole === "RUNTIME"
    && artifact.imageRepository === "ghcr.io/mahmoudashraf/ai-fabric-runtime"
    && artifact.imageDigest === QDRANT_RUNTIME.imageDigest && artifact.gitCommitSha === QDRANT_RUNTIME.gitCommitSha
    && artifact.promotionChannel === "staging" && Number.isFinite(Date.parse(artifact.promotedAt))
    && typeof artifact.buildRunId === "string" && artifact.buildRunId.trim()
    && artifact.capabilityManifest?.aiFabricVersion === QDRANT_RUNTIME.frameworkVersion, "EXACT_LATEST_STAGING_RUNTIME_REQUIRED");
  requireDigest(artifact.capabilityManifestHash);
  for (const [field, required] of Object.entries({capabilities: "deployment-local-provider-budget-v1",
    endpointClasses: "provider-budget-administration", migrationIds: "provider-budget-accounting-v1",
    verificationPackIds: "provider-budget-hard-limit-v1"}))
    check(artifact.capabilityManifest[field]?.includes(required), "RUNTIME_PROVIDER_BUDGET_CONTRACT_REQUIRED");
  requireTemplateRuntimeSupport(template, artifact, catalogs);
}

function requireStagingTarget(target) {
  check(target?.id === STAGING_TARGET_ID && target.environmentName === "staging" && target.active === true
    && target.providerType === "COOLIFY" && target.sourceStrategy === "IMAGE_SOURCE", "EXACT_ACTIVE_STAGING_TARGET_REQUIRED");
  const expected = {runtimeLimitsCpus: "1.0", runtimeLimitsMemory: "1g", connectorLimitsCpus: "0.25",
    connectorLimitsMemory: "768m", vectorizationRunnerLimitsCpus: "0.25", vectorizationRunnerLimitsMemory: "768m",
    runtimeDatabaseMode: "COOLIFY_POSTGRES", runtimeDatabaseCpuMillicores: 250, runtimeDatabaseMemoryMiB: 512,
    runtimeDatabaseDiskMiB: 2048};
  for (const [key, value] of Object.entries(expected))
    check(String(target.resourceDefaults?.[key]) === String(value), "REVIEWED_STAGING_CAPACITY_CHANGED");
}

export function buildFreshQdrantStagingOffer(input) {
  requireKeys(input, ["sourceTemplate", "reviewedVector", "template", "pluginVersions", "artifact", "target",
    "inboxArtifact", "inboxSpec", "specId", "configuration", "review", "behaviorCatalog", "executionExtensionCatalog"], "OFFER_INPUT_INVALID");
  publicInput(input);
  const {configuration, review, target, artifact, inboxArtifact} = input;
  check(inboxArtifact?.id === QDRANT_INBOX_ARTIFACT_ID, "EXACT_REVIEWED_INBOX_ARTIFACT_REQUIRED");
  requireKeys(configuration, ["aiStateStorageMode", "auth", "externalIntegrationAllowedHosts"], "PACKAGE_CONFIGURATION_INVALID");
  check(configuration.aiStateStorageMode === "MANAGED_POSTGRES", "MANAGED_POSTGRES_STAGING_PILOT_ONLY");
  requireKeys(review, ["name", "reviewReference", "approvedPolicyRef", "measuredPolicyRef", "operatingPolicyRef",
    "identityPolicyRef", "storagePolicyRef", "stagingExpiryHours", "maxExtensionHours", "recoveryWindowHours"], "OFFER_REVIEW_INVALID");
  for (const field of ["name", "reviewReference", "approvedPolicyRef", "measuredPolicyRef", "operatingPolicyRef", "identityPolicyRef", "storagePolicyRef"])
    text(review[field]);
  for (const field of ["stagingExpiryHours", "maxExtensionHours", "recoveryWindowHours"])
    check(Number.isSafeInteger(review[field]) && review[field] > 0 && review[field] <= 24, "BOUNDED_REVIEWED_LIFETIME_REQUIRED");
  const template = requireFreshTemplate(input);
  requireRuntime(artifact, template, input);
  requireStagingTarget(target);
  requirePostgresInboxPublication(input.inboxSpec, {templateVersionId: template.id, specId: input.specId, inboxArtifact});
  check(Array.isArray(input.pluginVersions) && input.pluginVersions.length === CUSTOMER_PLUGIN_REFS.length, "EXACT_PLUGIN_PINS_REQUIRED");
  const plugins = CUSTOMER_PLUGIN_REFS.map(reference => {
    const [id, version] = reference.split("@");
    const matches = input.pluginVersions.filter(row => row.pluginId === id && row.version === version);
    check(matches.length === 1 && matches[0].id === CUSTOMER_PLUGIN_VERSION_IDS[id], "EXACT_PLUGIN_PINS_REQUIRED");
    return requirePublishedVersion(matches[0]);
  });
  const external = buildExternalIntegrationBindings({pluginVersions: plugins, targetProfileIds: [target.id],
    allowedHosts: configuration.externalIntegrationAllowedHosts});
  requireKeys(configuration.auth, ["projectUrl", "publishableKey", "dealerId", "imageHostAllowlist"], "PUBLIC_WORKSPACE_CONFIGURATION_REQUIRED");
  const authUrl = new URL(configuration.auth.projectUrl);
  check(authUrl.protocol === "https:" && authUrl.pathname === "/" && !authUrl.username && !authUrl.password
    && !authUrl.search && !authUrl.hash, "PUBLIC_WORKSPACE_CONFIGURATION_REQUIRED");
  check(typeof configuration.auth.publishableKey === "string" && /^sb_publishable_[A-Za-z0-9_-]+$/.test(configuration.auth.publishableKey),
    "PUBLIC_PUBLISHABLE_KEY_ONLY");
  const workspace = workspaceProfile(configuration.auth);
  const topology = postgresDealershipTopology("MANAGED_POSTGRES");
  const pin = row => ({pluginId: row.pluginId, versionId: row.id, manifestSha256: hash(row.manifest)});
  return normalizeOfferDefinition({schemaVersion: "1", name: review.name, templateId: QDRANT_BASE_TEMPLATE_ID,
    template: pin(template), plugins: plugins.map(pin),
    artifacts: [artifact, inboxArtifact].map(row => ({artifactId: row.id, fingerprint: artifactFingerprint(row)})),
    targets: [{targetProfileId: target.id, intent: "CUSTOMER_STAGING", profileFingerprint: profileFingerprint(target)}],
    operations: ["TEMPLATE_SELECT", "BOOTSTRAP_CONFIGURATION", "BOOTSTRAP", "READ", "VERSION_PUBLISH", "BINDING_REPLACE", "BINDING_REVOKE",
      "INBOX_OPERATOR_ENROLL", "INBOX_OPERATOR_ROTATE", "INBOX_OPERATOR_DELIVER", "INBOX_OPERATOR_REVOKE", "SOURCE_DISCOVER", "SOURCE_PREVIEW",
      "SOURCE_INDEX", "SOURCE_REFRESH", "SOURCE_REMOVE", "APP_CONFIGURE", "DOMAIN_CONFIGURE", "AUTH_CONFIGURE", "APPLY", "VERIFY",
      "ACTIVATE", "ROLLBACK", "STAGING_RETAIN", "STAGING_EXTEND", "TARGET_RETIRE"],
    settings: [{setting: "DEPLOYMENT_NAME", type: "TEXT", maxLength: 100, allowedValues: []},
      {setting: "AI_STATE_STORAGE_MODE", type: "CHOICE", maxLength: 32, allowedValues: ["MANAGED_POSTGRES"]},
      {setting: "APP_TITLE", type: "TEXT", maxLength: 120, allowedValues: []},
      ...[["INTERACTION_PROFILE_ID", workspace.interactionProfileId], ["THEME_PROFILE_ID", workspace.themeProfileId], ["AUTH_PROFILE_ID", workspace.authProfileId]]
        .map(([setting, value]) => ({setting, type: "CHOICE", maxLength: 100, allowedValues: [value]})),
      {setting: "APP_DOMAIN", type: "TEXT", maxLength: 255, allowedValues: []},
      {setting: "DOCUMENT_AUDIENCE", type: "CHOICE", maxLength: 32, allowedValues: ["PUBLIC"]}],
    workspaceProfiles: [{id: "dealership-standalone-mixed", label: "Dealership workspace with protected capabilities",
      interaction: workspace.interaction, theme: workspace.theme, authentication: workspace.authentication}],
    bindingSlots: [{id: "document-storage", kind: "CUSTOMER_S3", supportedProfileIds: ["reviewed-s3-read-only"],
      pluginId: "mkp-data-dealership-knowledge-v1", installConfigField: "documentStorageBindingRef"}, ...external.slots,
      {id: "inbox", kind: "INBOX_OPERATOR", supportedProfileIds: ["managed-basic-inbox-keys-v1"]}],
    externalIntegrationProfiles: external.profiles, documentAudiences: ["PUBLIC"],
    admissionPrerequisites: ["APPROVED_POLICY", "IMMUTABLE_ARTIFACTS", "STORAGE", "IDENTITY", "CAPACITY", "NOTIFICATIONS", "OPERATING_POLICY"],
    limits: {measuredPolicyRef: review.measuredPolicyRef, activeStagingTargets: 1, ...topology,
      stagingExpiryHours: review.stagingExpiryHours, maxExtensionHours: review.maxExtensionHours, recoveryWindowHours: review.recoveryWindowHours},
    topology, aiStateStorageTopologies: {MANAGED_POSTGRES: structuredClone(topology)},
    approvedPolicyRef: review.approvedPolicyRef, operatingPolicyRef: review.operatingPolicyRef,
    identityPolicyRef: review.identityPolicyRef, storagePolicyRef: review.storagePolicyRef});
}

export function planFreshQdrantStagingOffer({offerId, ...input}) {
  requireConcreteIdentifier(offerId, 64);
  check(offerId.startsWith("offer-") && /qdrant/.test(offerId), "NEW_QDRANT_OFFER_ID_REQUIRED");
  const definition = buildFreshQdrantStagingOffer(input);
  return plan({path: "/api/customer-self-service/staff/offers", body: {offerId, definition, reviewReference: input.review.reviewReference}},
    {phase: "offer", nativeVersionId: input.template.id, offerFingerprint: hash(definition)});
}
