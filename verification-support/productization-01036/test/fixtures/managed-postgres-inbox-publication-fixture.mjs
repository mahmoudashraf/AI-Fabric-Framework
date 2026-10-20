import {readFileSync} from "node:fs";
import {buildPostgresInboxSpec} from "../../managed-postgres-inbox-spec.mjs";
import {buildPostgresInboxTemplateManifest} from "../../managed-postgres-inbox-dealership-offer.mjs";
import {CUSTOMER_PLUGIN_REFS, CUSTOMER_PLUGIN_VERSION_IDS, EXTERNAL_BINDING_REQUIREMENTS, hash,
  KNOWLEDGE_INSTALL_DEFAULTS, MANAGED_INBOX_INSTALL_DEFAULTS, MANAGED_INBOX_TEMPLATE_PLUGIN_ID,
  profileFingerprint, RUNTIME_IMAGE_DIGEST, RUNTIME_SOURCE_COMMIT} from "../../simulated-dealership-offer.mjs";
import {DEPENDENCY_IMAGES, INBOX_CAPABILITY_MANIFEST} from "../../simulated-dealership-managed-spec.mjs";

// All IDs, approvals, metadata and hosts in this fixture are synthetic. No service is contacted.
const date = "2026-10-19T12:00:00Z";
export function inboxArtifact() {
  return {id: "dsa-11111111", serviceName: DEPENDENCY_IMAGES.inbox.serviceName, artifactType: "DOCKER_IMAGE",
    artifactRole: "MANAGED_DEPENDENCY", imageRepository: DEPENDENCY_IMAGES.inbox.repository,
    imageDigest: DEPENDENCY_IMAGES.inbox.digest, gitCommitSha: DEPENDENCY_IMAGES.inbox.commit,
    capabilityManifest: structuredClone(INBOX_CAPABILITY_MANIFEST), capabilityManifestHash: DEPENDENCY_IMAGES.inbox.capabilityManifestHash,
    promotionChannel: "production", promotedAt: date};
}

function target(id, environmentName) {
  return {id, environmentName, active: true, providerType: "COOLIFY", sourceStrategy: "IMAGE_SOURCE",
    providerConfig: {serverUuid: `synthetic-server-${environmentName}`, destinationUuid: `synthetic-destination-${environmentName}`},
    credentialRefId: `synthetic-reference-${environmentName}`, networkPolicy: {publicHttps: true}, resourceDefaults: {
      runtimeLimitsCpus: "1.0", runtimeLimitsMemory: "1g", connectorLimitsCpus: "0.25", connectorLimitsMemory: "768m",
      vectorizationRunnerLimitsCpus: "0.25", vectorizationRunnerLimitsMemory: "768m", runtimeDatabaseMode: "COOLIFY_POSTGRES",
      runtimeDatabaseCpuMillicores: 250, runtimeDatabaseMemoryMiB: 512, runtimeDatabaseDiskMiB: 2048,
    }};
}

export function packageInput(status = null, mode = "MANAGED_POSTGRES") {
  const sourceTemplate = {id: "mkv-template-dealership-external-systems-managed-inbox-v1-0",
    pluginId: MANAGED_INBOX_TEMPLATE_PLUGIN_ID, version: "1.0.0", status: "PUBLISHED", publishedAt: date, manifest: {
      schemaVersion: "marketplace-plugin/v1", pluginId: MANAGED_INBOX_TEMPLATE_PLUGIN_ID, version: "1.0.0", pluginType: "TEMPLATE",
      contributions: {template: {baseTemplateId: "dev-openai-milvus", providerConfig: {openaiMaxTokens: 2048,
        vectorStrategy: "milvus", vectorProvisioningMode: "EXTERNAL_EXISTING", vectorStoragePosture: "SHARED",
        milvusExternalBindingRequired: true, milvusAuthenticationType: "TOKEN"},
      requiredPluginRefs: [...CUSTOMER_PLUGIN_REFS], requiredPluginInstallDefaults: {
        "mkp-data-dealership-knowledge-v1@1.0.0": structuredClone(KNOWLEDGE_INSTALL_DEFAULTS),
        "mkp-integration-basic-loomai-inbox@1.3.0": structuredClone(MANAGED_INBOX_INSTALL_DEFAULTS),
      }, deploymentBehavior: {type: "CONVERSATIONAL", requiredRuntimeCapabilityIds: ["ai-fabric-core", "ai-fabric-chat-session"],
        allowedExecutionExtensions: ["CONVERSATION_HANDOVER"], defaultExecutionExtensions: ["CONVERSATION_HANDOVER"],
        allowedChannelBindings: ["BACKEND_API"], verificationPackIds: ["conversational-behavior-v1"]}}}}};
  const selection = {publisherId: "mpub-synthetic", pluginSlug: "dealership-external-systems-managed-inbox-v1",
    templateVersion: "1.1.0", specId: "mds-synthetic-postgres-inbox", offerId: "offer-synthetic-postgres-inbox"};
  const manifest = buildPostgresInboxTemplateManifest(sourceTemplate, selection.templateVersion);
  const publishedTemplate = {id: "mkv-33333333", pluginId: MANAGED_INBOX_TEMPLATE_PLUGIN_ID, version: selection.templateVersion,
    status: "PUBLISHED", publishedAt: date, manifest};
  const submission = {pluginVersionId: publishedTemplate.id, publisherId: selection.publisherId,
    pluginId: MANAGED_INBOX_TEMPLATE_PLUGIN_ID, pluginSlug: selection.pluginSlug, version: selection.templateVersion,
    releaseChannel: "EARLY_ACCESS", status, manifest: structuredClone(manifest), bundleSha256: hash(manifest)};
  const inbox = inboxArtifact();
  const plugins = CUSTOMER_PLUGIN_REFS.map(reference => {
    const [id, version] = reference.split("@");
    const requirement = EXTERNAL_BINDING_REQUIREMENTS.find(row => row.pluginId === id);
    const installForm = requirement ? [...requirement.configFields.map(field => ({id: field,
      type: requirement.endpointFields.includes(field) ? "url" : "text", required: true})),
    ...requirement.secretRefFields.map(field => ({id: field, type: "secretRef", required: true}))] : [];
    return {id: CUSTOMER_PLUGIN_VERSION_IDS[id], pluginId: id, version, status: "PUBLISHED", publishedAt: date,
      manifest: {pluginId: id, version, installForm}};
  });
  return {platformBaseUrl: "https://platform.synthetic.example", selection, review: {name: "Synthetic PG Inbox offer",
    reviewReference: "synthetic-unit-review", approvedPolicyRef: "synthetic-approved-policy", measuredPolicyRef: "synthetic-measurement",
    operatingPolicyRef: "synthetic-operating-policy", identityPolicyRef: "synthetic-identity-policy", storagePolicyRef: "synthetic-storage-policy",
    stagingExpiryHours: 24, maxExtensionHours: 24, recoveryWindowHours: 24},
  configuration: {aiStateStorageMode: mode, auth: {projectUrl: "https://synthetic.supabase.co",
    publishableKey: "sb_publishable_synthetic_unit_only", dealerId: "synthetic-dealer", imageHostAllowlist: ["media.synthetic.example"]},
  externalIntegrationAllowedHosts: {"vehicle-stock": ["stock.synthetic.example"], "business-actions": ["actions.synthetic.example"]}},
  contracts: {sourceTemplate, templateDetail: {plugin: {id: MANAGED_INBOX_TEMPLATE_PLUGIN_ID, slug: selection.pluginSlug,
    publisherSlug: "synthetic-publisher", pluginType: "TEMPLATE", status: "ACTIVE"}, versions: [structuredClone(sourceTemplate)]},
  publisherDetail: {publisher: {id: selection.publisherId, slug: "synthetic-publisher", status: "ACTIVE", verificationStatus: "VERIFIED"},
    submissions: status ? [submission] : []}, inboxArtifacts: [inbox], publishedTemplate,
  inboxPublication: {spec: buildPostgresInboxSpec({inbox}), publication: {templateVersionId: publishedTemplate.id,
    specId: selection.specId, fingerprint: "a".repeat(64), reviewReference: "synthetic-spec-review", reviewedAt: date}},
  pluginVersions: plugins, targets: [target("dtp-coolify-staging-behavior", "staging"), target("dtp-coolify-production-behavior", "production")],
  runtimeArtifacts: [{id: "dsa-22222222", serviceName: "ai-fabric-runtime", artifactType: "DOCKER_IMAGE", artifactRole: "RUNTIME",
    imageRepository: "ghcr.io/mahmoudashraf/ai-fabric-runtime", imageDigest: RUNTIME_IMAGE_DIGEST, gitCommitSha: RUNTIME_SOURCE_COMMIT,
    capabilityManifestHash: "b".repeat(64), promotionChannel: "production", promotedAt: date, buildRunId: "synthetic-build",
    capabilityManifest: JSON.parse(readFileSync(new URL("../../../../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/META-INF/loomai/runtime-capabilities.json", import.meta.url), "utf8"))}],
  behaviorCatalog: [{code: "CONVERSATIONAL", activationSources: ["AUTHENTICATED_INTERACTIVE"], channelBindings: ["BACKEND_API"],
    allowedExecutionExtensions: ["CONVERSATION_HANDOVER"], requiredRuntimeCapabilities: ["ai-fabric-core", "ai-fabric-chat-session"],
    requiredRuntimeEndpointClasses: ["chat-query", "query-once", "session-management"], requiredRuntimeMigrationIds: [],
    baselineVerificationPackIds: ["conversational-behavior-v1"], requiredSpecialistBundles: []}],
  executionExtensionCatalog: [{code: "CONVERSATION_HANDOVER", compatibleBehaviorTypes: ["CONVERSATIONAL"],
    requiredRuntimeCapabilities: ["canonical-conversation-orchestration-state"], requiredRuntimeEndpointClasses: ["conversation-channel-callback"],
    requiredRuntimeMigrationIds: ["loomai-conversation-orchestration-state-v1"], verificationPackIds: ["conversation-handover-v1"]}]}};
}

export function offerArguments(input = packageInput("PUBLISHED")) {
  const c = input.contracts;
  return {template: c.publishedTemplate, pluginVersions: c.pluginVersions, artifact: c.runtimeArtifacts[0], targets: c.targets,
    inboxArtifact: c.inboxArtifacts[0], inboxSpec: c.inboxPublication, specId: input.selection.specId, configuration: input.configuration,
    review: input.review, behaviorCatalog: c.behaviorCatalog, executionExtensionCatalog: c.executionExtensionCatalog};
}

export function fakePhaseServer(plan, override) {
  const calls = [];
  const request = async (path, options = {}) => {
    calls.push({path, ...structuredClone(options)});
    if (override) { const response = await override(path, options); if (response !== undefined) return response; }
    if (path === "/api/platform/auth/session") return {enabled: true, authenticated: true, role: "PLATFORM_ADMIN", authenticationMode: "API_KEY"};
    if (options.method !== "POST") {
      const row = plan.reads.find(value => value.path === path);
      if (row) return structuredClone(row.value);
      throw new Error(`Unexpected synthetic GET: ${path}`);
    }
    if (plan.phase.startsWith("template-")) {
      const status = {"template-submit": "SUBMITTED", "template-validate": "VALIDATED", "template-publish": "PUBLISHED"}[plan.phase];
      return {...structuredClone(packageInput(status).contracts.publisherDetail.submissions[0]),
        reviewNotes: plan.request.body.reviewNotes ?? null};
    }
    if (plan.phase === "spec") return {...packageInput("PUBLISHED").contracts.inboxPublication.publication,
      reviewReference: plan.request.body.reviewReference};
    if (plan.phase === "offer") return {offerId: plan.input.selection.offerId, fingerprint: plan.offerFingerprint};
    const target = plan.input.contracts.targets.find(value => path.includes(`/${value.id}/`));
    return {id: "placement-synthetic", targetProfileId: target.id, intent: options.body.intent,
      profileFingerprint: profileFingerprint(target), checkedAt: new Date(Date.now() - 1000).toISOString(), expiresAt: new Date(Date.now() + 60000).toISOString()};
  };
  return {request, calls};
}
