import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {mkdtemp, readFile, writeFile, stat, rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {fileURLToPath} from "node:url";
import {spawnSync} from "node:child_process";
import test from "node:test";

import {
  artifactFingerprint,
  ATTENDED_CANARY_CONTRACT,
  buildOfferDefinition as buildPublishableOfferDefinition,
  buildOfferProposal,
  buildExternalIntegrationBindings,
  CUSTOMER_PLUGIN_REFS,
  CUSTOMER_PLUGIN_VERSION_IDS,
  EXTERNAL_BINDING_REQUIREMENTS,
  MANAGED_AI_STATE_MODE,
  CUSTOMER_AI_STATE_MODE,
  MANAGED_INBOX_TEMPLATE_PLUGIN_ID,
  MANAGED_INBOX_INSTALL_DEFAULTS,
  KNOWLEDGE_INSTALL_DEFAULTS,
  hash,
  MILVUS_TOKEN_CAPABILITY,
  OFFER_ID,
  OPENAI_MAX_TOKENS,
  profileFingerprint,
  REVIEWED_LIMITS,
  REVIEWED_TOPOLOGY,
  reviewedTopologyCapacity,
  RUNTIME_FRAMEWORK_VERSION,
  RUNTIME_IMAGE_DIGEST,
  RUNTIME_SOURCE_COMMIT,
  requireTemplateRuntimeSupport,
  normalizeOfferDefinition,
  TEMPLATE_PLUGIN_ID,
  TEMPLATE_VERSION,
  workspaceProfile,
} from "../simulated-dealership-offer.mjs";
import {platformOfferRequest, prepareSimulatedDealershipOffer, publishSimulatedDealershipOffer,
  verifySimulatedDealershipOffer} from "../publish-simulated-dealership-offer.mjs";
import {buildManagedDependencySpec, DEPENDENCY_IMAGES, SPEC_ID} from "../simulated-dealership-managed-spec.mjs";

const auth = {projectUrl: "https://example.supabase.co", publishableKey: "sb_publishable_local_fixture_not_real",
  dealerId: "reviewed-dealer-test", imageHostAllowlist: ["media.example.test"]};
const manifest = (pluginId, version) => ({pluginId, version,
  ...(EXTERNAL_BINDING_REQUIREMENTS.some(requirement => requirement.pluginId === pluginId)
    ? {installForm: EXTERNAL_BINDING_REQUIREMENTS.filter(requirement => requirement.pluginId === pluginId).flatMap(requirement => [
      ...requirement.configFields.map(id => ({id, type: requirement.endpointFields.includes(id) ? "url" : "text", required: true})),
      ...requirement.secretRefFields.map(id => ({id, type: "secretRef", required: true})),
    ])} : {}),
  contributions: [TEMPLATE_PLUGIN_ID, MANAGED_INBOX_TEMPLATE_PLUGIN_ID].includes(pluginId)
  ? {template: {providerConfig: {openaiMaxTokens: OPENAI_MAX_TOKENS, vectorStrategy: "milvus",
    vectorProvisioningMode: "EXTERNAL_EXISTING", vectorStoragePosture: "SHARED", milvusExternalBindingRequired: true,
    milvusAuthenticationType: "TOKEN"}, requiredPluginInstallDefaults: {
      "mkp-data-dealership-knowledge-v1@1.0.0": structuredClone(KNOWLEDGE_INSTALL_DEFAULTS),
    }, deploymentBehavior: {
      type: "CONVERSATIONAL", requiredRuntimeCapabilityIds: ["ai-fabric-core", "ai-fabric-chat-session"],
      allowedExecutionExtensions: ["CONVERSATION_HANDOVER"], defaultExecutionExtensions: ["CONVERSATION_HANDOVER"],
      allowedChannelBindings: ["BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT"],
      verificationPackIds: ["conversational-behavior-v1"],
    }}} : {}});
const version = (pluginId, semanticVersion, id) => ({
  pluginId, version: semanticVersion, id, manifest: manifest(pluginId, semanticVersion),
});
const target = (id, environmentName) => ({
  id, active: true, providerType: "COOLIFY", environmentName, sourceStrategy: "IMAGE_SOURCE",
  credentialRefId: `credential-${environmentName}`,
  providerConfig: {serverUuid: `server-${environmentName}`, destinationUuid: `destination-${environmentName}`},
  networkPolicy: {publicHttps: true},
  resourceDefaults: {
    sourceStrategy: "IMAGE_SOURCE",
    runtimeLimitsCpus: "1.0", runtimeLimitsMemory: "1g", runtimeLimitsMemorySwap: "1g",
    runtimeLimitsMemoryReservation: "512m", runtimeJavaOpts: "-XX:ActiveProcessorCount=1 -Xms256m -Xmx768m",
    connectorLimitsCpus: "0.25", connectorLimitsMemory: "768m", connectorLimitsMemorySwap: "768m",
    connectorLimitsMemoryReservation: "384m", connectorJavaOpts: "-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m",
    vectorizationRunnerLimitsCpus: "0.25", vectorizationRunnerLimitsMemory: "768m",
    vectorizationRunnerLimitsMemorySwap: "768m", vectorizationRunnerLimitsMemoryReservation: "384m",
    vectorizationRunnerJavaOpts: "-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m",
    runtimeDatabaseMode: "COOLIFY_POSTGRES", runtimeDatabaseCpuMillicores: 250,
    runtimeDatabaseMemoryMiB: 512, runtimeDatabaseDiskMiB: 2048,
    runtimeLimitsCpuSet: id.includes("staging") ? "7" : "3",
    connectorLimitsCpuSet: id.includes("staging") ? "6" : "2",
    vectorizationRunnerLimitsCpuSet: id.includes("staging") ? "5" : "1",
  },
});
const artifact = {
  id: "runtime-artifact", artifactRole: "RUNTIME", artifactType: "DOCKER_IMAGE",
  serviceName: "ai-fabric-runtime", imageRepository: "registry/runtime",
  imageDigest: RUNTIME_IMAGE_DIGEST, gitCommitSha: RUNTIME_SOURCE_COMMIT,
  promotionChannel: "staging", promotedAt: "2026-10-18T21:00:00Z", buildRunId: "39164527840",
  capabilityManifest: JSON.parse(readFileSync(new URL(
    "../../../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/META-INF/loomai/runtime-capabilities.json",
    import.meta.url), "utf8")),
  capabilityManifestHash: "manifest-hash",
};

const catalogs = {
  behaviorCatalog: [{
    code: "CONVERSATIONAL",
    activationSources: ["AUTHENTICATED_INTERACTIVE"],
    channelBindings: ["BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT", "QUERY_ONCE"],
    allowedExecutionExtensions: ["GOVERNED_RESOLVER", "HUMAN_REVIEW", "CONVERSATION_HANDOVER"],
    requiredRuntimeCapabilities: ["ai-fabric-core", "ai-fabric-chat-session"],
    requiredRuntimeEndpointClasses: ["chat-query", "query-once", "session-management"],
    requiredRuntimeMigrationIds: [],
    baselineVerificationPackIds: ["conversational-behavior-v1"],
    requiredSpecialistBundles: [],
  }],
  executionExtensionCatalog: [{
    code: "CONVERSATION_HANDOVER",
    compatibleBehaviorTypes: ["CONVERSATIONAL"],
    requiredRuntimeCapabilities: ["canonical-conversation-orchestration-state"],
    requiredRuntimeEndpointClasses: ["conversation-channel-callback"],
    requiredRuntimeMigrationIds: ["loomai-conversation-orchestration-state-v1"],
    verificationPackIds: ["conversation-handover-v1"],
  }],
};

function catalogInputs() {
  return structuredClone(catalogs);
}

function offerInputs() {
  return {
    ...catalogInputs(),
    template: {
      ...version(TEMPLATE_PLUGIN_ID, TEMPLATE_VERSION, "mkv-template-dealership-external-systems-v1-0"),
      contributions: {templateRequiredPluginRefs: [...CUSTOMER_PLUGIN_REFS]},
    },
    pluginVersions: CUSTOMER_PLUGIN_REFS.map(ref => {
      const [pluginId, semanticVersion] = ref.split("@");
      return version(pluginId, semanticVersion, CUSTOMER_PLUGIN_VERSION_IDS[pluginId]);
    }),
    artifact: structuredClone(artifact),
    targets: [target("dtp-coolify-staging-behavior", "staging"),
      target("dtp-coolify-production-behavior", "production")],
    auth,
    managedInbox: false,
    aiStateStorageMode: MANAGED_AI_STATE_MODE,
    externalIntegrationAllowedHosts: {"vehicle-stock": ["stock.customer.example"], "business-actions": ["actions.customer.example"]},
  };
}

// These regressions validate the proposal's candidate; they do not assert publication readiness.
function buildOfferDefinition(input) { return buildOfferProposal(input).definitionCandidate; }

test("external proposal pins new template 1.0.0 and the unchanged verified native-token image", () => {
  assert.equal(OFFER_ID, "offer-loomai-engage-external-dealership-candidate-v1");
  assert.equal(TEMPLATE_VERSION, "1.0.0");
  assert.equal(RUNTIME_FRAMEWORK_VERSION, "0.8.36");
  assert.equal(MILVUS_TOKEN_CAPABILITY, "milvus-token-authentication-v1");
  assert.equal(OPENAI_MAX_TOKENS, 2048);
  assert.match(REVIEWED_LIMITS.measuredPolicyRef, /pending/);
  assert.equal(RUNTIME_IMAGE_DIGEST, "sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140");
  assert.equal(RUNTIME_SOURCE_COMMIT, "d2b1a9792dc10ec801d3ce30c170763f1f2ab301");
  for (const promotionChannel of ["staging", "production"]) {
    const input = offerInputs();
    input.artifact.promotionChannel = promotionChannel;
    const unchanged = structuredClone(input);
    const definition = buildOfferDefinition(input);
    assert.equal(definition.template.versionId, "mkv-template-dealership-external-systems-v1-0");
    assert.match(definition.name, /Unapproved Integration Candidate/);
    assert.deepEqual(definition.artifacts, [{
      artifactId: input.artifact.id, fingerprint: artifactFingerprint(input.artifact),
    }]);
    assert.deepEqual(definition.topology, REVIEWED_TOPOLOGY);
    assert.deepEqual(definition.limits, REVIEWED_LIMITS);
    assert.deepEqual(input, unchanged);
  }
});

test("candidate offer requires the exact explicit integer output cap without changing inherited provider fields", () => {
  for (const cap of [undefined, null, 0, 1000, "2048", 2049, 4096]) {
    const input = offerInputs();
    input.template.manifest.contributions.template.providerConfig.openaiMaxTokens = cap;
    assert.throws(() => buildOfferDefinition(input), /explicit integer 2048-token output cap/);
  }
  const input = offerInputs();
  input.template.version = "1.5.0";
  assert.throws(() => buildOfferDefinition(input), /exact external-systems 1.0.0 template/);
});

test("external candidate rejects unmanaged routing and implicit endpoint or secret fallback", () => {
  for (const patch of [
    {vectorStrategy: "lucene"}, {vectorProvisioningMode: "PLATFORM_MANAGED"},
    {vectorStoragePosture: "DEDICATED"}, {milvusExternalBindingRequired: false},
    {milvusAuthenticationType: "USERNAME_PASSWORD"}, {milvusAuthenticationType: "token"},
    {milvusAuthenticationType: "TOKEN "}, {milvusAuthenticationType: null}, {milvusAuthenticationType: undefined},
    {milvusHost: "unreviewed.example"}, {milvusRuntimeUsernameSecretName: "MILVUS_USERNAME"},
    {milvusRuntimePasswordSecretName: "MILVUS_PASSWORD"}, {milvusRuntimeTokenSecretName: "MILVUS_TOKEN"},
    {milvusToken: "synthetic-token"}, {milvusUsername: "synthetic-user"}, {milvusPassword: "synthetic-password"},
  ]) {
    const input = offerInputs();
    Object.assign(input.template.manifest.contributions.template.providerConfig, patch);
    assert.throws(() => buildOfferDefinition(input), /retained external-vector binding contract/);
  }
  for (const field of ["milvusHost", "milvusToken", "milvusUsername", "milvusPassword",
    "milvusRuntimeTokenSecretName", "milvusRuntimeUsernameSecretName", "milvusRuntimePasswordSecretName"]) {
    for (const value of ["", null, false]) {
      const input = offerInputs();
      input.template.manifest.contributions.template.providerConfig[field] = value;
      assert.throws(() => buildOfferDefinition(input), /retained external-vector binding contract/);
    }
  }
});

test("external candidate rejects legacy or mixed image pins without rewriting published v10 artifact identity", () => {
  const legacyArtifact = {
    ...artifact,
    imageDigest: "sha256:d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28",
    gitCommitSha: "600f136bf0becb9a473ff1e085bbc9896b4ef696",
    capabilityManifest: {aiFabricVersion: "0.8.35"},
  };
  const legacyOffer = {
    offerId: "offer-loomai-engage-simulated-dealership-early-access-v10",
    definition: {artifacts: [{
      artifactId: legacyArtifact.id, fingerprint: artifactFingerprint(legacyArtifact),
    }]},
  };
  const legacySnapshot = structuredClone({artifact: legacyArtifact, offer: legacyOffer});
  for (const pins of [
    {imageDigest: legacyArtifact.imageDigest},
    {gitCommitSha: legacyArtifact.gitCommitSha},
    {imageDigest: legacyArtifact.imageDigest, gitCommitSha: legacyArtifact.gitCommitSha},
  ]) {
    const input = offerInputs();
    input.artifact = {...input.artifact, ...pins};
    assert.throws(() => buildOfferDefinition(input), /exact promoted AI Fabric 0.8.36 runtime artifact/);
  }
  assert.throws(() => buildOfferDefinition({...offerInputs(), artifact: legacyArtifact}),
    /exact promoted AI Fabric 0.8.36 runtime artifact/);
  const next = buildOfferDefinition(offerInputs());
  assert.notEqual(OFFER_ID, legacyOffer.offerId);
  assert.notEqual(next.artifacts[0].fingerprint, legacyOffer.definition.artifacts[0].fingerprint);
  assert.deepEqual({artifact: legacyArtifact, offer: legacyOffer}, legacySnapshot);
  assert.equal(artifactFingerprint(legacyArtifact), legacyOffer.definition.artifacts[0].fingerprint);
});

test("native-token offer rejects wrong framework or incomplete immutable artifact provenance", () => {
  for (const patch of [
    {artifactRole: "CONNECTOR"}, {artifactType: "GIT_SOURCE"},
    {promotionChannel: null}, {promotionChannel: "unreviewed"}, {promotedAt: null},
    {buildRunId: null}, {buildRunId: ""}, {buildRunId: " "},
    {capabilityManifest: {...artifact.capabilityManifest, aiFabricVersion: "0.8.35"}},
    {capabilityManifest: {...artifact.capabilityManifest, aiFabricVersion: "0.8.36 "}},
  ]) {
    const input = offerInputs();
    Object.assign(input.artifact, patch);
    assert.throws(() => buildOfferDefinition(input), /exact promoted AI Fabric 0.8.36 runtime artifact/);
  }
});

test("native-token offer requires the exact token capability in addition to every budget declaration", () => {
  for (const declaration of [undefined, null, "", "MILVUS-TOKEN-AUTHENTICATION-V1",
    "milvus-token-authentication-v1-extra", {id: MILVUS_TOKEN_CAPABILITY}, [MILVUS_TOKEN_CAPABILITY]]) {
    const input = offerInputs();
    input.artifact.capabilityManifest.capabilities = ["deployment-local-provider-budget-v1", declaration];
    assert.throws(() => buildOfferDefinition(input), /required milvus-token-authentication-v1 capability/);
  }
});

for (const [field, required] of Object.entries({
  capabilities: "deployment-local-provider-budget-v1",
  endpointClasses: "provider-budget-administration",
  migrationIds: "provider-budget-accounting-v1",
  verificationPackIds: "provider-budget-hard-limit-v1",
})) {
  test(`offer requires the exact provider-budget declaration in ${field}`, () => {
    for (const declarations of [
      undefined, null, [], required, {[required]: true}, [{id: required}],
      [`${required}-extra`], [required.toUpperCase()],
    ]) {
      const input = offerInputs();
      if (declarations === undefined) delete input.artifact.capabilityManifest[field];
      else input.artifact.capabilityManifest[field] = declarations;
      assert.throws(() => buildOfferDefinition(input), /required provider-budget guard declaration/);
    }
    const input = offerInputs();
    const retained = input.artifact.capabilityManifest[field].filter((declaration) => declaration !== required);
    input.artifact.capabilityManifest[field] = ["unrelated-capability", ...retained, required];
    assert.doesNotThrow(() => buildOfferDefinition(input));
  });
}

test("publication rejects the old unsupported resolver declaration against the actual runtime manifest", () => {
  const input = offerInputs();
  const behavior = input.template.manifest.contributions.template.deploymentBehavior;
  behavior.allowedExecutionExtensions.unshift("GOVERNED_RESOLVER");
  behavior.defaultExecutionExtensions.unshift("GOVERNED_RESOLVER");
  const before = structuredClone(input);
  assert.throws(() => requireTemplateRuntimeSupport(input.template, input.artifact, input), /allowedExecutionExtensions/);
  assert.throws(() => buildOfferDefinition(input), /allowedExecutionExtensions/);
  assert.deepEqual(input, before);
});

for (const [field, supported] of Object.entries({
  requiredRuntimeCapabilityIds: "capabilities", allowedExecutionExtensions: "supportedExecutionExtensions",
  defaultExecutionExtensions: "supportedExecutionExtensions", allowedChannelBindings: "supportedChannelBindings",
  verificationPackIds: "verificationPackIds",
})) {
  test(`template publication checks ${field} without weakening the runtime declaration`, () => {
    const input = offerInputs();
    input.template.manifest.contributions.template.deploymentBehavior[field].push("unsupported-exact-contract");
    assert.throws(() => requireTemplateRuntimeSupport(input.template, input.artifact, input), new RegExp(field));
    input.template.manifest.contributions.template.deploymentBehavior[field] = null;
    assert.throws(() => requireTemplateRuntimeSupport(input.template, input.artifact, input), new RegExp(field));
    const valid = offerInputs();
    valid.artifact.capabilityManifest[supported] = null;
    assert.throws(() => requireTemplateRuntimeSupport(valid.template, valid.artifact, valid), /template contract/);
  });
}

test("publication requires an explicit supported behavior", () => {
  const input = offerInputs();
  input.template.manifest.contributions.template.deploymentBehavior.type = "UNSUPPORTED";
  assert.throws(() => requireTemplateRuntimeSupport(input.template, input.artifact, input), /declared behavior/);
  delete input.template.manifest.contributions.template.deploymentBehavior;
  assert.throws(() => requireTemplateRuntimeSupport(input.template, input.artifact, input), /declared behavior/);
});

for (const [field, missing] of [
  ["capabilities", "canonical-conversation-orchestration-state"],
  ["endpointClasses", "conversation-channel-callback"],
  ["migrationIds", "loomai-conversation-orchestration-state-v1"],
  ["verificationPackIds", "conversation-handover-v1"],
]) {
  test(`publication rejects catalog-derived handover requirements missing from ${field}`, () => {
    const input = offerInputs();
    input.artifact.capabilityManifest[field] = input.artifact.capabilityManifest[field].filter(value => value !== missing);
    const before = structuredClone(input);
    assert.throws(() => requireTemplateRuntimeSupport(input.template, input.artifact, input), new RegExp(`${field}: ${missing}`));
    assert.throws(() => buildOfferDefinition(input), new RegExp(`${field}: ${missing}`));
    assert.deepEqual(input, before);
  });
}

for (const [catalogField, manifestField, required] of [
  ["activationSources", "supportedActivationSources", "AUTHENTICATED_INTERACTIVE"],
  ["requiredRuntimeCapabilities", "capabilities", "ai-fabric-execution"],
  ["requiredRuntimeEndpointClasses", "endpointClasses", "query-once"],
  ["requiredRuntimeMigrationIds", "migrationIds", "ai-action-proposal-receipt-v1"],
  ["baselineVerificationPackIds", "verificationPackIds", "human-review-action-proposal-v1"],
]) {
  test(`publication enforces the base behavior catalog's ${catalogField}`, () => {
    const input = offerInputs();
    input.behaviorCatalog[0][catalogField] = [required];
    assert.doesNotThrow(() => buildOfferDefinition(input));
    input.artifact.capabilityManifest[manifestField] = input.artifact.capabilityManifest[manifestField]
      .filter(value => value !== required);
    assert.throws(() => buildOfferDefinition(input), new RegExp(`CONVERSATIONAL catalog requirements in ${manifestField}: ${required}`));
  });
}

test("catalog requirements are taken from the supplied contract rather than extension-name heuristics", () => {
  const input = offerInputs();
  input.executionExtensionCatalog[0].requiredRuntimeEndpointClasses.push("catalog-only-new-endpoint");
  assert.throws(() => buildOfferDefinition(input), /endpointClasses: catalog-only-new-endpoint/);
});

test("requirements of allowed extensions are checked even when the extension is not a default", () => {
  const input = offerInputs();
  input.template.manifest.contributions.template.deploymentBehavior.allowedExecutionExtensions.push("HUMAN_REVIEW");
  input.executionExtensionCatalog.push({
    code: "HUMAN_REVIEW", compatibleBehaviorTypes: ["CONVERSATIONAL"],
    requiredRuntimeCapabilities: ["jdbc-review-state"], requiredRuntimeEndpointClasses: ["review-decision"],
    requiredRuntimeMigrationIds: ["ai-review-task-v1"], verificationPackIds: ["human-review-action-proposal-v1"],
  });
  assert.doesNotThrow(() => buildOfferDefinition(input));
  input.artifact.capabilityManifest.verificationPackIds = input.artifact.capabilityManifest.verificationPackIds
    .filter(value => value !== "human-review-action-proposal-v1");
  assert.throws(() => buildOfferDefinition(input), /HUMAN_REVIEW catalog requirements in verificationPackIds/);
});

for (const field of ["behaviorCatalog", "executionExtensionCatalog"]) {
  test(`publication requires exactly one applicable contract in ${field}`, () => {
    for (const value of [undefined, null, {}, [], [catalogs[field][0], catalogs[field][0]]]) {
      const input = offerInputs();
      input[field] = structuredClone(value);
      assert.throws(() => buildOfferDefinition(input), /backend .* catalog/);
    }
  });
}

test("template defaults, channel bindings and extension compatibility must agree with the backend catalogs", () => {
  const invalidDefault = offerInputs();
  invalidDefault.template.manifest.contributions.template.deploymentBehavior.allowedExecutionExtensions = [];
  assert.throws(() => buildOfferDefinition(invalidDefault), /default execution extensions must be allowed/);

  const invalidChannel = offerInputs();
  invalidChannel.behaviorCatalog[0].channelBindings = ["BACKEND_API"];
  assert.throws(() => buildOfferDefinition(invalidChannel), /channel bindings are outside the backend behavior catalog/);

  const invalidExtension = offerInputs();
  invalidExtension.behaviorCatalog[0].allowedExecutionExtensions = [];
  assert.throws(() => buildOfferDefinition(invalidExtension), /not compatible with the backend CONVERSATIONAL/);

  const incompatible = offerInputs();
  incompatible.executionExtensionCatalog[0].compatibleBehaviorTypes = ["SMART_BRAIN"];
  assert.throws(() => buildOfferDefinition(incompatible), /not compatible with the backend CONVERSATIONAL/);
});

test("missing or malformed catalog requirement arrays cannot silently omit requirements", () => {
  for (const [catalog, fields] of [
    ["behaviorCatalog", ["activationSources", "requiredRuntimeCapabilities", "requiredRuntimeEndpointClasses",
      "requiredRuntimeMigrationIds", "baselineVerificationPackIds", "channelBindings", "allowedExecutionExtensions"]],
    ["executionExtensionCatalog", ["compatibleBehaviorTypes", "requiredRuntimeCapabilities", "requiredRuntimeEndpointClasses",
      "requiredRuntimeMigrationIds", "verificationPackIds"]],
  ]) {
    for (const field of fields) {
      for (const value of [undefined, null, "missing", [null], [" "]]) {
        const input = offerInputs();
        input[catalog][0][field] = value;
        assert.throws(() => buildOfferDefinition(input), /Invalid runtime catalog contract/);
      }
    }
  }
});

test("base behavior specialist bundle requirements retain exact artifact identity", () => {
  const input = offerInputs();
  input.behaviorCatalog[0].requiredSpecialistBundles = [structuredClone(artifact.capabilityManifest.specialistBundles[0])];
  assert.doesNotThrow(() => buildOfferDefinition(input));
  input.artifact.capabilityManifest.specialistBundles[0].contentHash = `sha256:${"0".repeat(64)}`;
  assert.throws(() => buildOfferDefinition(input), /missing the exact catalog specialist bundle/);

  for (const value of [undefined, null, {}, [null]]) {
    const invalid = offerInputs();
    invalid.behaviorCatalog[0].requiredSpecialistBundles = value;
    assert.throws(() => buildOfferDefinition(invalid), /specialist.*[Bb]undle/);
  }
});

test("canonical hashes do not depend on object insertion order", () => {
  assert.equal(hash({b: 2, a: {d: 4, c: 3}}), hash({a: {c: 3, d: 4}, b: 2}));
});

test("target identity uses the shared admission field names and binds every field", () => {
  const staging = target("dtp-coolify-staging-behavior", "staging");
  const fingerprint = profileFingerprint(staging);
  assert.equal(fingerprint, hash({
    id: staging.id,
    provider: staging.providerType,
    environment: staging.environmentName,
    sourceStrategy: staging.sourceStrategy,
    credentialReference: staging.credentialRefId,
    providerConfig: staging.providerConfig,
    networkPolicy: staging.networkPolicy,
    resourceDefaults: staging.resourceDefaults,
  }));
  for (const [field, value] of Object.entries({
    id: "other-target", providerType: "OTHER", environmentName: "production",
    sourceStrategy: "GIT_SOURCE", credentialRefId: "other-credential",
    providerConfig: {...staging.providerConfig, serverUuid: "other-server"},
    networkPolicy: {...staging.networkPolicy, publicHttps: false},
    resourceDefaults: {...staging.resourceDefaults, runtimeLimitsMemory: "2g"},
  })) {
    assert.notEqual(profileFingerprint({...staging, [field]: value}), fingerprint, field);
  }
  assert.notEqual(fingerprint, hash({
    id: staging.id, providerType: staging.providerType, sourceStrategy: staging.sourceStrategy,
    credentialRefId: staging.credentialRefId, providerConfig: staging.providerConfig,
    networkPolicy: staging.networkPolicy, resourceDefaults: staging.resourceDefaults,
  }), "earlier offer identity is not accepted");
});

test("target identity rejects missing identity fields and non-object configurations", () => {
  const staging = target("dtp-coolify-staging-behavior", "staging");
  for (const field of ["id", "providerType", "environmentName", "sourceStrategy"]) {
    for (const value of [undefined, null, "", " \t\n", 123]) {
      assert.throws(() => profileFingerprint({...staging, [field]: value}), /TARGET_PROFILE_IDENTITY_INVALID/);
    }
  }
  for (const field of ["providerConfig", "networkPolicy", "resourceDefaults"]) {
    for (const value of [undefined, null, [], "{}", true]) {
      assert.throws(() => profileFingerprint({...staging, [field]: value}), /TARGET_PROFILE_IDENTITY_INVALID/);
    }
  }
  assert.equal(profileFingerprint({...staging, credentialRefId: null}),
    profileFingerprint({...staging, credentialRefId: ""}));
});

test("target identity matches the Java golden vector with nested configuration and exact numbers", () => {
  const golden = {
    id: "target-golden", providerType: "COOLIFY", environmentName: "customer-staging",
    sourceStrategy: "IMAGE_SOURCE", credentialRefId: "credential-golden",
    providerConfig: {
      serverUuid: "server-golden", destinationUuid: "destination-golden",
      options: {tls: true, labels: ["alpha", "beta"], limits: {retries: 2, timeout: 30}},
      callbackPath: "/api/hooks/a?x=1&y=2",
    },
    networkPolicy: {
      allowedOrigins: ["https://workspace.example", "https://dealer.example"],
      policy: {credentials: false, methods: ["GET", "POST"]},
    },
    resourceDefaults: {
      sourceStrategy: "IMAGE_SOURCE", limits: {cpuMillis: 1000, memoryMiB: 1024}, flags: {inbox: true},
    },
  };
  assert.equal(profileFingerprint(golden), "e5e3ee7463654d36663577109db633685027c5b0c51dae0c5bd7ce99c71b4d2a");
  const reordered = structuredClone(golden);
  reordered.providerConfig.options.limits = JSON.parse('{"timeout":3e1,"retries":2.0}');
  reordered.resourceDefaults.limits = JSON.parse('{"memoryMiB":1.024e3,"cpuMillis":1000.0}');
  assert.equal(profileFingerprint(reordered), profileFingerprint(golden));
  for (const number of [0.5, -0.1, 9007199254740992, -9007199254740992, Infinity, NaN]) {
    assert.throws(() => profileFingerprint({...golden, providerConfig: {nested: [{value: number}]}}),
      /TARGET_PROFILE_IDENTITY_NUMBER_UNSUPPORTED/);
  }
  assert.equal(profileFingerprint({...golden, networkPolicy: {zero: -0}}),
    profileFingerprint({...golden, networkPolicy: {zero: 0}}));
  assert.notEqual(profileFingerprint({...golden, networkPolicy: {allowedOrigins: ["https://dealer.example", "https://workspace.example"]}}),
    profileFingerprint(golden));
});

test("provider and runtime fingerprint changes are observable", () => {
  const staging = target("dtp-coolify-staging-behavior", "staging");
  assert.notEqual(profileFingerprint(staging), profileFingerprint({...staging, providerConfig: {...staging.providerConfig, serverUuid: "other"}}));
  assert.notEqual(artifactFingerprint(artifact), artifactFingerprint({...artifact, imageDigest: `sha256:${"c".repeat(64)}`}));
  assert.notEqual(artifactFingerprint(artifact), artifactFingerprint({...artifact, artifactRole: "CONNECTOR"}));
  assert.equal(artifactFingerprint(artifact), hash({
    id: artifact.id, serviceName: artifact.serviceName, artifactType: artifact.artifactType,
    artifactRole: artifact.artifactRole, imageRepository: artifact.imageRepository,
    imageDigest: artifact.imageDigest, gitCommitSha: artifact.gitCommitSha,
    capabilityManifest: artifact.capabilityManifest, capabilityManifestHash: artifact.capabilityManifestHash,
  }));
});

test("workspace profile IDs use the backend normalized enum representation", () => {
  const profile = workspaceProfile(auth);
  const configuration = {
    page: {
      kind: "auto", maxChars: 1800, maxPages: 3,
      contextLabel: "Current dealership page", rootSelector: "#main-content", maxTotalChars: 10000,
    },
    dealer: {
      id: auth.dealerId, sourceMode: "DEALERSHIP_INVENTORY", assistantLabel: "Dealership AI",
    },
    knowledge: {inventoryVectorSpace: "dealer-vehicle"},
    capabilities: {callback: true, testDrive: true, comparison: true},
    presentation: {imageHostAllowlist: auth.imageHostAllowlist},
  };
  assert.equal(profile.interactionProfileId, `workspace:${hash({
    packCode: "dealership",
    packVersion: "1.1.0",
    connectionMode: "PUBLIC_RUNTIME_ANONYMOUS",
    connectionProfileCode: "runtime-anonymous-direct",
    connectionProfileVersion: "1.0.0",
    deliveryMode: "STANDALONE",
    connectionConfiguration: {},
    configuration,
  })}`);
  assert.equal(profile.authProfileId, `auth:${hash({
    profileCode: "supabase",
    profileVersion: "1.0.0",
    interactionPolicy: "PUBLIC_WITH_PROTECTED_CAPABILITIES",
    configuration: {projectUrl: auth.projectUrl, publishableKey: auth.publishableKey, methods: ["email-password"]},
  })}`);
});

test("reviewed offer capacity covers the complete enforced topology", () => {
  const selected = {aiStateStorageMode: MANAGED_AI_STATE_MODE};
  assert.deepEqual(reviewedTopologyCapacity(selected), {
    milliCpu: 1750,
    memoryMiB: 3072,
    diskMiB: 2048,
    dependencies: 0,
    databases: 1,
    indexes: 2,
  });
  assert.equal(REVIEWED_LIMITS.cpuMillis >= reviewedTopologyCapacity(selected).milliCpu, true);
  assert.equal(REVIEWED_LIMITS.memoryMiB >= reviewedTopologyCapacity(selected).memoryMiB, true);
  assert.equal(REVIEWED_LIMITS.diskMiB, reviewedTopologyCapacity(selected).diskMiB);
  assert.equal(REVIEWED_LIMITS.dependencies, reviewedTopologyCapacity(selected).dependencies);
  assert.deepEqual(REVIEWED_TOPOLOGY, {
    replicas: 1, cpuMillis: 1750, memoryMiB: 3072, diskMiB: 2048,
    databases: 1, indexes: 2, dependencies: 0, concurrentApplies: 1,
  });
  assert.deepEqual(REVIEWED_LIMITS, {
    measuredPolicyRef: "01044-external-ai-capacity-review-pending",
    activeStagingTargets: 1, replicas: 1, cpuMillis: 4000, memoryMiB: 6144, diskMiB: 2048,
    databases: 1, indexes: 2, dependencies: 0, concurrentApplies: 1,
    stagingExpiryHours: 24, maxExtensionHours: 24, recoveryWindowHours: 24,
  });
});

test("offer pins exact template dependencies, runtime, targets and reviewed workspace profiles", () => {
  const input = offerInputs();
  const {pluginVersions} = input;
  const definition = buildOfferDefinition(input);
  assert.equal(definition.template.versionId, "mkv-template-dealership-external-systems-v1-0");
  assert.equal(definition.templateId, "dev-openai-milvus");
  assert.deepEqual(definition.plugins.map(({versionId}) => versionId), pluginVersions.map(plugin => plugin.id));
  assert.equal(definition.artifacts[0].artifactId, "runtime-artifact");
  assert.deepEqual(definition.targets.map(({intent}) => intent), ["CUSTOMER_STAGING", "CUSTOMER_PRODUCTION"]);
  assert.equal(definition.operations.includes("VERSION_PUBLISH"), true);
  assert.equal(definition.operations.includes("DRAFT_EDIT"), false);
  assert.equal(definition.operations.includes("INBOX_OPERATOR_ENROLL"), false);
  assert.equal(definition.operations.includes("INBOX_OPERATOR_DELIVER"), false);
  assert.deepEqual(definition.operations, [
    "TEMPLATE_SELECT", "BOOTSTRAP_CONFIGURATION", "BOOTSTRAP", "READ", "VERSION_PUBLISH",
    "BINDING_REPLACE", "BINDING_REVOKE", "SOURCE_DISCOVER", "SOURCE_PREVIEW", "SOURCE_INDEX",
    "SOURCE_REFRESH", "SOURCE_REMOVE", "APP_CONFIGURE", "DOMAIN_CONFIGURE", "AUTH_CONFIGURE",
    "APPLY", "VERIFY", "PROMOTION_PLAN", "PROMOTION_APPLY", "ACTIVATE", "ROLLBACK",
    "STAGING_RETAIN", "STAGING_EXTEND", "TARGET_RETIRE",
  ]);
  assert.equal(definition.bindingSlots[0].pluginId, "mkp-data-dealership-knowledge-v1");
  assert.deepEqual(definition.topology, REVIEWED_TOPOLOGY);
  assert.equal(definition.bindingSlots.length, 4, "Document, two reviewed external services and customer AI-state slots");
  assert.deepEqual(definition.bindingSlots.find(slot => slot.kind === "CUSTOMER_AI_STATE"), {
    id: "ai-state-database", kind: "CUSTOMER_AI_STATE", supportedProfileIds: ["CUSTOMER_POSTGRES"],
  });
  assert.deepEqual(definition.bindingSlots.filter(slot => slot.kind === "EXTERNAL_SERVICE").map(slot => slot.id),
    ["vehicle-stock", "business-actions"]);
  assert.equal(definition.settings.filter(({type}) => type === "CHOICE").every(({allowedValues}) => allowedValues.length > 0), true);
  assert.equal(definition.workspaceProfiles.length, 1);
  const profile = definition.workspaceProfiles[0];
  for (const [setting, prefix, payload] of [
    ["INTERACTION_PROFILE_ID", "workspace", profile.interaction],
    ["THEME_PROFILE_ID", "theme", profile.theme],
    ["AUTH_PROFILE_ID", "auth", profile.authentication],
  ]) {
    assert.deepEqual(definition.settings.find((value) => value.setting === setting).allowedValues,
      [`${prefix}:${hash(payload)}`]);
  }
});

test("offer refuses a target whose enforced worker capacity drifted", () => {
  const input = offerInputs();
  const staging = target("dtp-coolify-staging-behavior", "staging");
  staging.resourceDefaults.connectorLimitsCpus = "2.0";

  assert.throws(() => buildOfferDefinition({
    ...input, targets: [staging, target("dtp-coolify-production-behavior", "production")],
  }), /drifted capacity fields: connectorLimitsCpus/);
});

test("offer rejects incomplete required plugin pinning", () => {
  const input = offerInputs();
  const template = {
    ...input.template,
    contributions: {templateRequiredPluginRefs: ["missing@1.0.0"]},
  };
  assert.throws(() => buildOfferDefinition({
    ...input, template, pluginVersions: [],
  }), /exact required plugin/);
});

test("candidate emits exact typed V196 external profiles and unmapped slots without mutable binding values", () => {
  const input = offerInputs();
  const before = structuredClone(input);
  const definition = buildOfferDefinition(input);
  assert.equal(definition.externalIntegrationProfiles.length, 2);
  for (const profile of definition.externalIntegrationProfiles) {
    const requirement = EXTERNAL_BINDING_REQUIREMENTS.find(item => item.id === profile.bindingSlotId);
    const pinned = input.pluginVersions.find(version => version.id === requirement.pluginVersionId);
    assert.deepEqual(profile, {
      schemaVersion: "loomai-external-integration-profile/v1", id: requirement.profileId, bindingSlotId: requirement.id,
      pluginId: requirement.pluginId, pluginVersionId: requirement.pluginVersionId, manifestSha256: hash(pinned.manifest),
      targetProfileIds: ["dtp-coolify-production-behavior", "dtp-coolify-staging-behavior"],
      configFields: [...requirement.configFields].sort(), secretFields: [...requirement.secretRefFields].sort(),
      endpointFields: [...requirement.endpointFields].sort(), accountScopeFields: [...requirement.accountScopeFields].sort(),
      allowedHosts: input.externalIntegrationAllowedHosts[requirement.id],
    });
    assert.deepEqual(definition.bindingSlots.find(slot => slot.id === requirement.id), {
      id: requirement.id, kind: "EXTERNAL_SERVICE", supportedProfileIds: [requirement.profileId],
      pluginId: requirement.pluginId, installConfigField: null,
    });
    assert.deepEqual(definition.plugins.find(pin => pin.pluginId === profile.pluginId), {
      pluginId: profile.pluginId, versionId: profile.pluginVersionId, manifestSha256: profile.manifestSha256,
    });
  }
  assert.doesNotMatch(JSON.stringify(definition.externalIntegrationProfiles), /MANAGED_SIMULATOR|MANAGED_RECEIVER|https:\/\/|credentials|secretReferences/);
  assert.deepEqual(input, before);
});

test("reviewed external hosts are explicit bounded exact names, never guessed endpoints or arbitrary slot metadata", () => {
  for (const value of [undefined, null, [], {}, {"vehicle-stock": ["stock.example.com"]},
    {"vehicle-stock": ["stock.example.com"], "business-actions": ["actions.example.com"], extra: ["other.example.com"]}]) {
    assert.throws(() => buildOfferProposal({...offerInputs(), externalIntegrationAllowedHosts: value}), /Exact reviewed allowedHosts/);
  }
  for (const hosts of [undefined, null, "stock.example.com", [], [null], [""], ["https://stock.example.com"],
    ["*.example.com"], ["127.0.0.1"], ["[::1]"], ["Stock.example.com"], ["provider.internal"],
    ["provider.invalid"], ["stock.example.com", "stock.example.com"], Array.from({length: 17}, (_, i) => `host${i}.example.com`)]) {
    const input = offerInputs(); input.externalIntegrationAllowedHosts["vehicle-stock"] = hosts;
    assert.throws(() => buildOfferProposal(input), /Explicit reviewed DNS host names/);
  }
  const input = offerInputs();
  input.externalIntegrationAllowedHosts["vehicle-stock"] = ["z.provider.example", "a.provider.example"];
  const profile = buildOfferDefinition(input).externalIntegrationProfiles[0];
  assert.deepEqual(profile.allowedHosts, ["a.provider.example", "z.provider.example"]);
});

test("typed profiles reject changed V196 identity and field contracts or ambiguous versions", () => {
  for (const patch of [{id: "latest-stock-version"}, {version: "1.0.1"},
    {manifest: {pluginId: "foreign-plugin", version: "1.0.0"}}]) {
    const input = offerInputs();
    Object.assign(input.pluginVersions[0], patch);
    assert.throws(() => buildOfferProposal(input), /exact immutable V196 composition plugin pins|exact required plugin/);
  }
  for (const change of [fields => fields.slice(1), fields => [...fields, fields[0]],
    fields => fields.map((field, i) => i ? field : {...field, type: "secretRef"}),
    fields => fields.map((field, i) => i ? field : {...field, required: false}),
    fields => fields.map((field, i) => i ? field : {...field, defaultValue: "demo"})]) {
    const input = offerInputs();
    input.pluginVersions[0].manifest.installForm = change(input.pluginVersions[0].manifest.installForm);
    assert.throws(() => buildOfferProposal(input), /exact unbound V196 install field contract/);
  }
  const input = offerInputs(); input.pluginVersions.push(structuredClone(input.pluginVersions[0]));
  assert.throws(() => buildOfferProposal(input), /exact immutable V196 composition plugin pins/);
  for (const targetProfileIds of [undefined, [], ["target", "target"], [null], ["target/path"]]) {
    assert.throws(() => buildExternalIntegrationBindings({pluginVersions: offerInputs().pluginVersions,
      targetProfileIds, allowedHosts: offerInputs().externalIntegrationAllowedHosts}), /Exact reviewed external integration target IDs/);
  }
});

test("every composition pin and template identity is immutable, not just its semantic version", () => {
  assert.deepEqual(Object.values(CUSTOMER_PLUGIN_VERSION_IDS), [
    "mkv-data-dealership-external-stock-v1-0", "mkv-action-dealership-external-discovery-v1-0",
    "mkv-action-dealership-external-engagement-v1-0", "mkv-data-dealership-knowledge-v1-0",
    "mkv-action-conversation-handover-v1-1", "mkv-integration-basic-loomai-inbox-v1-3",
  ]);
  for (let index = 0; index < CUSTOMER_PLUGIN_REFS.length; index++) {
    const input = offerInputs(); input.pluginVersions[index].id = "other-immutable-version";
    assert.throws(() => buildOfferProposal(input), /exact immutable V196 composition plugin pins/);
    const foreign = offerInputs(); foreign.pluginVersions[index].manifest.pluginId = "foreign-plugin";
    assert.throws(() => buildOfferProposal(foreign), /exact immutable V196 composition plugin pins/);
  }
  const input = offerInputs(); input.template.id = "other-template-same-version";
  assert.throws(() => buildOfferProposal(input), /exact external-systems 1.0.0 template/);
});

test("binding protocol requires exact published scope, staff review and write-only customer values, not install mutation", () => {
  const proposal = buildOfferProposal(offerInputs());
  assert.deepEqual(proposal.externalBindingProtocol.scopeFields,
    ["versionId", "targetProfileId", "bindingSlotId", "selectedProfileId"]);
  assert.deepEqual(proposal.externalBindingProtocol.bindingFields,
    ["approvalId", "expectedEntitlementRevision", "operationKey", "credentials"]);
  assert.deepEqual(proposal.externalBindingProtocol.revocationFields,
    ["bindingId", "expectedBindingRevision", "expectedEntitlementRevision", "operationKey"]);
  assert.match(proposal.externalBindingProtocol.staffApprovalPath, /\/staff\/offers\/\{offerId\}\/deployments\/\{deploymentId\}\/external-integrations\/approvals$/);
  assert.match(proposal.externalBindingProtocol.customerBindingPath, /\/catalogue\/\{offerId\}\/deployments\/\{deploymentId\}\/external-integrations\/bind$/);
  assert.match(proposal.externalBindingProtocol.customerRevocationPath, /\/external-integrations\/revoke$/);
  assert.equal(proposal.externalBindingProtocol.rotation, "NEW_IMMUTABLE_VERSION_AND_TARGET_SCOPED_APPROVAL_BINDING");
  assert.match(proposal.externalBindingProtocol.credentials, /WRITE_ONLY_VALUES.*NEVER_GLOBAL_INSTALL_VALUES_OR_CLIENT_SECRET_REFERENCES/);
  assert.match(proposal.aiOperationalPersistence.customerBindingPath, /\/ai-state-storage-binding$/);
  assert.deepEqual(proposal.aiOperationalPersistence.customerBindingFields,
    ["expectedEntitlementRevision", "operationKey", "targetProfileId", "bindingSlotId", "connection"]);
  assert.equal(proposal.aiOperationalPersistence.customerBindingSlotId, "ai-state-database");
  assert.equal(proposal.aiOperationalPersistence.approvalReference, "SERVER_DERIVED_CALLER_MUST_OMIT_OR_NULL");
  assert.throws(() => buildPublishableOfferDefinition({...offerInputs(), integratedProof: true, publishable: true}), /EXTERNAL_INBOX_TARGET_BINDING_REQUIRED/);
});

test("setup-only dependency deferral is a visible blocker, not implicit source or credential readiness", () => {
  const setup = buildOfferProposal(offerInputs()).externalBindingProtocol.setupOnly;
  assert.deepEqual(setup, {
    unresolvedRequirementsPath: "behaviorConfig.marketplaceRequirements.unresolvedRequiredPluginRefs",
    sourceDependency: "DECLARED_ACTION_SOURCE_REF_TO_UNAVAILABLE_EXPLICITLY_REQUIRED_DATA_ONLY",
    ordinaryCompilation: "STRICT_NO_SETUP_DEFERRAL",
    initializer: "POST_ASSOCIATION_TRUSTED_ALIAS_ONLY_INSTALL_ENABLE_AND_COMPILER_RESYNC",
    accountBinding: "NOT_CREATED_BY_BOOTSTRAP_OR_INITIALIZER",
  });
});

test("caller success flags cannot authorize the managed canary or clear broader-mode gates", async () => {
  const priorFetch = globalThis.fetch;
  let requests = 0;
  globalThis.fetch = async () => { requests++; throw new Error("unexpected network"); };
  try {
    for (const proof of [
      {bootstrapVerified: true, postgresMigration: 199, parentBootstrapTestsPassed: 21},
      {customerBindingVerified: true, compilerVerified: true, sqlRotationVerified: true},
      {publishable: true, publicationBlockers: [], integratedProof: {status: "PASS"}},
    ]) {
      const input = {...offerInputs(), ...proof};
      const proposal = buildOfferProposal(input);
      assert.equal(proposal.publishable, false);
      assert.equal(proposal.liveReadinessProven, false);
      assert.equal(proposal.blockers.includes("ATTENDED_IMMUTABLE_OFFER_REVIEW_REQUIRED"), true);
      assert.equal(proposal.blockers.includes("CUSTOMER_AI_STATE_HOSTED_GENERATION_ACCEPTANCE_REQUIRED"), true);
      assert.equal(proposal.blockers.some(value => value.includes("REAL_V196")), false);
      assert.throws(() => buildPublishableOfferDefinition(input), /EXTERNAL_INBOX_TARGET_BINDING_REQUIRED/);
      await assert.rejects(publishSimulatedDealershipOffer(input));
    }
  } finally { globalThis.fetch = priorFetch; }
  assert.equal(requests, 0);
});

test("proposal preserves all external features but cannot masquerade as publishable authority", async () => {
  const input = offerInputs();
  const proposal = buildOfferProposal(input);
  assert.equal(proposal.publishable, false);
  assert.equal(proposal.liveReadinessProven, false);
  assert.deepEqual(proposal.blockers, ["ATTENDED_IMMUTABLE_OFFER_REVIEW_REQUIRED",
    "CUSTOMER_AI_STATE_HOSTED_GENERATION_ACCEPTANCE_REQUIRED", "EXTERNAL_INBOX_TARGET_BINDING_REQUIRED"]);
  assert.deepEqual(proposal.externalBindingRequirements.map(binding => binding.id),
    ["vehicle-stock", "business-actions", "conversation-inbox"]);
  assert.equal(proposal.externalBindingRequirements[0].secretRefFields.length, 3);
  assert.deepEqual(proposal.definitionCandidate.workspaceProfiles[0].interaction.configuration.capabilities,
    {callback: true, testDrive: true, comparison: true});
  assert.throws(() => buildPublishableOfferDefinition(input), /EXTERNAL_INBOX_TARGET_BINDING_REQUIRED/);
  const priorFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("unexpected network"); };
  try { await assert.rejects(publishSimulatedDealershipOffer(), /REVIEWED_SHA256_REQUIRED/); }
  finally { globalThis.fetch = priorFetch; }
  assert.equal(calls, 0, "No platform, placement, provider or model request before contract support");
});

test("managed Inbox is explicit, separately pinned, capacity-complete and never a CRM", () => {
  const input = offerInputs();
  input.managedInbox = true;
  input.template.pluginId = MANAGED_INBOX_TEMPLATE_PLUGIN_ID;
  input.template.id = "mkv-template-dealership-external-systems-managed-inbox-v1-0";
  input.template.manifest.pluginId = MANAGED_INBOX_TEMPLATE_PLUGIN_ID;
  input.template.manifest.contributions.template.requiredPluginInstallDefaults = {
    "mkp-data-dealership-knowledge-v1@1.0.0": structuredClone(KNOWLEDGE_INSTALL_DEFAULTS),
    "mkp-integration-basic-loomai-inbox@1.3.0": structuredClone(MANAGED_INBOX_INSTALL_DEFAULTS),
  };
  const proposal = buildOfferProposal(input);
  assert.equal(proposal.definitionCandidate.operations.includes("INBOX_OPERATOR_ENROLL"), true);
  assert.deepEqual(proposal.definitionCandidate.bindingSlots.find(slot => slot.kind === "INBOX_OPERATOR"), {
    id: "inbox", kind: "INBOX_OPERATOR", supportedProfileIds: ["managed-basic-inbox-keys-v1"],
  });
  assert.deepEqual(proposal.definitionCandidate.topology, {
    replicas: 1, cpuMillis: 2000, memoryMiB: 3584, diskMiB: 6144, databases: 1,
    indexes: 2, dependencies: 1, concurrentApplies: 1,
  });
  assert.equal(proposal.definitionCandidate.limits.dependencies, 1);
  assert.deepEqual(proposal.definitionCandidate.aiStateStorageTopologies.CUSTOMER_POSTGRES, {
    replicas: 1, cpuMillis: 1750, memoryMiB: 3072, diskMiB: 4096, databases: 0,
    indexes: 2, dependencies: 1, concurrentApplies: 1,
  });
  assert.equal(proposal.externalBindingRequirements.length, 2);
  assert.equal(proposal.managedInbox, true);
  assert.equal(proposal.blockers.includes("EXTERNAL_INBOX_TARGET_BINDING_REQUIRED"), false);
});

test("AI state durability is required and optional hosting is an explicit creation choice", () => {
  const proposal = buildOfferProposal(offerInputs());
  assert.equal(proposal.aiOperationalPersistence.required, true);
  assert.equal(proposal.aiOperationalPersistence.selectedMode, MANAGED_AI_STATE_MODE);
  assert.deepEqual(proposal.definitionCandidate.settings.find(rule => rule.setting === "AI_STATE_STORAGE_MODE"),
    {setting: "AI_STATE_STORAGE_MODE", type: "CHOICE", maxLength: 32,
      allowedValues: [MANAGED_AI_STATE_MODE, CUSTOMER_AI_STATE_MODE]});
  assert.equal(proposal.aiOperationalPersistence.modes.find(mode => mode.id === CUSTOMER_AI_STATE_MODE).localContractVerified, true);
  assert.equal(proposal.aiOperationalPersistence.modes.find(mode => mode.id === CUSTOMER_AI_STATE_MODE).hostedAcceptanceVerified, false);
  for (const aiStateStorageMode of [undefined, null, "", "managed_postgres"]) {
    assert.throws(() => buildOfferProposal({...offerInputs(), aiStateStorageMode}), /explicit reviewed AI state storage mode/);
  }
  // Local BYO contracts are proven; arithmetic is not hosted activation evidence.
  assert.deepEqual(reviewedTopologyCapacity({aiStateStorageMode: CUSTOMER_AI_STATE_MODE}), {
    milliCpu: 1500, memoryMiB: 2560, diskMiB: 0, databases: 0, dependencies: 0, indexes: 2,
  });
  assert.throws(() => reviewedTopologyCapacity(), /explicit reviewed AI state storage mode/);
  const byo = buildOfferProposal({...offerInputs(), aiStateStorageMode: CUSTOMER_AI_STATE_MODE});
  assert.equal(byo.publishable, false);
  assert.equal(byo.aiOperationalPersistence.selectedMode, CUSTOMER_AI_STATE_MODE);
  assert.deepEqual(byo.definitionCandidate.aiStateStorageTopologies, {
    MANAGED_POSTGRES: {replicas: 1, cpuMillis: 1750, memoryMiB: 3072, diskMiB: 2048, databases: 1,
      indexes: 2, dependencies: 0, concurrentApplies: 1},
    CUSTOMER_POSTGRES: {replicas: 1, cpuMillis: 1500, memoryMiB: 2560, diskMiB: 0, databases: 0,
      indexes: 2, dependencies: 0, concurrentApplies: 1},
  });
  for (const field of Object.keys(byo.definitionCandidate.topology)) {
    assert.equal(byo.definitionCandidate.topology[field], Math.max(...Object.values(byo.definitionCandidate.aiStateStorageTopologies)
      .map(demand => demand[field])));
  }
});

test("demo identity, credentials, consent and business defaults cannot leak into corrected composition", () => {
  for (const defaults of [null, [],
    {"mkp-data-dealership-external-stock-v1@1.0.0": {config: {providerBaseUrl: "https://demo.example.test"}}},
    {"mkp-action-dealership-external-engagement-v1@1.0.0": {secretRefs: {receiverApiKey: "MANAGED_RECEIVER_API_KEY"}}},
    {"mkp-data-dealership-knowledge-v1@1.0.0": {config: {publicContentApprovalConfirmed: true}}},
  ]) {
    const input = offerInputs();
    input.template.manifest.contributions.template.requiredPluginInstallDefaults = defaults;
    assert.throws(() => buildOfferProposal(input), /Business integrations must be unbound/);
  }
  for (const patch of [{dealerId: undefined}, {dealerId: ""}, {dealerId: {id: "demo"}},
    {imageHostAllowlist: undefined}, {imageHostAllowlist: ["https://media.example.test"]},
    {imageHostAllowlist: ["*.example.test"]}, {imageHostAllowlist: ["a.example.test", "a.example.test"]}]) {
    assert.throws(() => workspaceProfile({...auth, ...patch}), /Explicit reviewed dealerId/);
  }
  const config = workspaceProfile(auth).interaction.configuration;
  assert.doesNotMatch(JSON.stringify(config), /simulated-dealership|m\.atcdn\.co\.uk/);
  assert.throws(() => buildOfferProposal({...offerInputs(), managedInbox: undefined}), /Explicit managedInbox selection/);
  assert.throws(() => buildOfferProposal({...offerInputs(), managedInbox: true}), /exact external-systems/);
});

test("knowledge default is explicitly not approved and never supplies storage or indexing consent", () => {
  assert.deepEqual(KNOWLEDGE_INSTALL_DEFAULTS, {config: {publicContentApprovalConfirmed: false}});
  assert.doesNotThrow(() => buildOfferProposal(offerInputs()));
  for (const config of [{}, {publicContentApprovalConfirmed: null}, {publicContentApprovalConfirmed: true},
    {publicContentApprovalConfirmed: "false"}, {publicContentApprovalConfirmed: 0},
    {publicContentApprovalConfirmed: false, documentStorageBindingRef: "slot:document-storage"},
    {publicContentApprovalConfirmed: false, initialIndexConfirmed: true}]) {
    const input = offerInputs();
    input.template.manifest.contributions.template.requiredPluginInstallDefaults = {
      "mkp-data-dealership-knowledge-v1@1.0.0": {config},
    };
    assert.throws(() => buildOfferProposal(input), /Business integrations must be unbound/);
  }
  const missing = offerInputs();
  missing.template.manifest.contributions.template.requiredPluginInstallDefaults = {};
  assert.throws(() => buildOfferProposal(missing), /Business integrations must be unbound/);
});

function attendedInputs() {
  const input = offerInputs();
  input.managedInbox = true;
  input.aiStateStorageModes = [MANAGED_AI_STATE_MODE];
  input.template.pluginId = MANAGED_INBOX_TEMPLATE_PLUGIN_ID;
  input.template.id = "mkv-template-dealership-external-systems-managed-inbox-v1-0";
  input.template.manifest.pluginId = MANAGED_INBOX_TEMPLATE_PLUGIN_ID;
  input.template.manifest.contributions.template.requiredPluginInstallDefaults = {
    "mkp-data-dealership-knowledge-v1@1.0.0": structuredClone(KNOWLEDGE_INSTALL_DEFAULTS),
    "mkp-integration-basic-loomai-inbox@1.3.0": structuredClone(MANAGED_INBOX_INSTALL_DEFAULTS),
  };
  for (const version of [input.template, ...input.pluginVersions]) {
    version.status = "PUBLISHED"; version.publishedAt = "2026-10-18T21:00:00Z";
  }
  input.artifact.capabilityManifestHash = "a".repeat(64);
  input.inboxArtifact = {id: "reviewed-inbox-artifact", serviceName: DEPENDENCY_IMAGES.inbox.serviceName,
    artifactType: "DOCKER_IMAGE", artifactRole: "MANAGED_DEPENDENCY", imageRepository: DEPENDENCY_IMAGES.inbox.repository,
    imageDigest: DEPENDENCY_IMAGES.inbox.digest, gitCommitSha: DEPENDENCY_IMAGES.inbox.commit,
    capabilityManifest: structuredClone(DEPENDENCY_IMAGES.inbox.capabilityManifest),
    capabilityManifestHash: DEPENDENCY_IMAGES.inbox.capabilityManifestHash,
    promotedAt: "2026-10-18T21:00:00Z", promotionChannel: "staging"};
  input.inboxSpec = {publication: {specId: SPEC_ID, templateVersionId: input.template.id,
    fingerprint: "b".repeat(64), reviewReference: "local-fixture-spec-review", reviewedAt: "2026-10-18T21:00:00Z"},
  spec: buildManagedDependencySpec({inbox: input.inboxArtifact}, {managedInbox: true})};
  return input;
}

function attendedReview() {
  return {contract: ATTENDED_CANARY_CONTRACT, offerId: "offer-local-managed-ai-canary", name: "Attended dealership AI canary",
    reviewReference: "local-fixture-offer-review", approvedPolicyRef: "local-fixture-policy-review",
    measuredPolicyRef: "local-fixture-capacity-review", operatingPolicyRef: "local-fixture-lifecycle-review",
    identityPolicyRef: "local-fixture-identity-review", storagePolicyRef: "local-fixture-storage-review",
    inboxSpecFingerprint: "b".repeat(64)};
}

const baseUrl = "https://control-plane.example";
const clock = () => new Date("2026-10-19T08:00:00Z");
export function publicationFixture() {
  const input = attendedInputs();
  const review = attendedReview();
  const configuration = Object.fromEntries(["managedInbox", "aiStateStorageMode", "aiStateStorageModes", "auth",
    "externalIntegrationAllowedHosts"].map(key => [key, structuredClone(input[key])]));
  const calls = [];
  const checkpoints = [];
  let readback;
  const request = async (path, options = {}) => {
    calls.push({path, options: structuredClone(options)});
    if (path === "/api/platform/auth/session") return {enabled: true, authenticated: true, role: "PLATFORM_ADMIN", authenticationMode: "API_KEY"};
    if (path === `/api/marketplace/plugins/${MANAGED_INBOX_TEMPLATE_PLUGIN_ID}/versions/1.0.0`) return structuredClone(input.template);
    for (const version of input.pluginVersions) {
      if (path === `/api/marketplace/plugins/${version.pluginId}/versions/${version.version}`) return structuredClone(version);
    }
    if (path === "/api/deployment-provider/target-profiles") return structuredClone(input.targets);
    if (path === "/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime") return [structuredClone(input.artifact)];
    if (path === `/api/deployment-provider/source-artifacts?serviceName=${DEPENDENCY_IMAGES.inbox.serviceName}`) return [structuredClone(input.inboxArtifact)];
    if (path === `/api/customer-self-service/staff/templates/${input.template.id}/managed-dependency-spec`) return structuredClone(input.inboxSpec);
    if (path === "/api/deployment-behaviors") return structuredClone(input.behaviorCatalog);
    if (path === "/api/deployment-execution-extensions") return structuredClone(input.executionExtensionCatalog);
    for (const target of input.targets) {
      if (path === `/api/customer-self-service/staff/targets/${target.id}/placement-evidence`) return {
        id: `cpe-${target.environmentName}`, targetProfileId: target.id, intent: options.body.intent,
        profileFingerprint: profileFingerprint(target), checkedAt: "2026-10-19T07:59:59Z", expiresAt: "2026-10-19T08:04:59Z"};
    }
    if (path === "/api/customer-self-service/staff/offers") {
      assert.equal(checkpoints.at(-1)?.state, "PUBLICATION_ATTEMPT_STARTED", "Checkpoint must precede POST");
      const definition = normalizeOfferDefinition(options.body.definition);
      assert.equal(hash(definition), hash(options.body.definition));
      readback = {id: review.offerId, name: definition.name, templateId: definition.templateId,
        templateVersionId: definition.template.versionId, fingerprint: hash(definition),
        entitlement: {id: "entitlement-fixture", offerId: review.offerId, customerId: "customer-fixture", ownerUserId: "owner-fixture",
          status: "ACTIVE", revision: 2, expiresAt: "2026-10-20T00:00:00Z"},
        targets: definition.targets.map(target => ({id: target.targetProfileId, intent: target.intent, limits: definition.limits})),
        permissions: definition.operations.map(operation => ({operation})),
        ...Object.fromEntries(["settings", "bindingSlots", "documentAudiences", "workspaceProfiles", "externalIntegrationProfiles"]
          .map(key => [key, structuredClone(definition[key])]))};
      return {offerId: review.offerId, fingerprint: hash(definition)};
    }
    throw new Error(`Unexpected fixture route: ${path}`);
  };
  const prepare = (overrides = {}) => prepareSimulatedDealershipOffer({baseUrl, review, configuration, request, ...overrides});
  const publish = (prepared, overrides = {}) => publishSimulatedDealershipOffer({baseUrl, prepared,
    reviewedPreparationSha256: prepared.preparationSha256, request, checkpoint: async value => {checkpoints.push(value);}, now: clock, ...overrides});
  const customerRequest = async path => {
    calls.push({path, options: {}});
    if (path === "/api/platform/auth/session") return {enabled: true, authenticated: true, role: "CUSTOMER_ADMIN",
      authenticationMode: "SESSION", customerId: "customer-fixture"};
    if (path === `/api/customer-self-service/catalogue/${review.offerId}`) return structuredClone(readback);
    throw new Error("Unexpected customer route");
  };
  const verify = (prepared, overrides = {}) => verifySimulatedDealershipOffer({baseUrl, prepared,
    reviewedPreparationSha256: prepared.preparationSha256, customerId: "customer-fixture", ownerUserId: "owner-fixture",
    request: customerRequest, now: clock, ...overrides});
  return {input, review, configuration, calls, checkpoints, request, prepare, publish, verify, customerRequest,
    readback: () => readback, effects: () => calls.filter(call => call.options.method && call.options.method !== "GET")};
}

test("attended managed-only definition uses real immutable contracts without a BYO rotation circular gate", () => {
  const input = attendedInputs();
  const before = structuredClone(input);
  const definition = buildPublishableOfferDefinition(input, attendedReview());
  assert.deepEqual(definition.settings.find(value => value.setting === "AI_STATE_STORAGE_MODE").allowedValues, [MANAGED_AI_STATE_MODE]);
  assert.deepEqual(Object.keys(definition.aiStateStorageTopologies), [MANAGED_AI_STATE_MODE]);
  assert.equal(definition.bindingSlots.some(slot => slot.kind === "CUSTOMER_AI_STATE"), false);
  assert.deepEqual(definition.artifacts, [
    {artifactId: input.artifact.id, fingerprint: artifactFingerprint(input.artifact)},
    {artifactId: input.inboxArtifact.id, fingerprint: artifactFingerprint(input.inboxArtifact)},
  ]);
  assert.equal(definition.topology.databases, 1);
  assert.equal(definition.topology.dependencies, 1);
  assert.deepEqual(definition.targets.map(target => target.intent), ["CUSTOMER_STAGING", "CUSTOMER_PRODUCTION"]);
  for (const operation of ["PROMOTION_PLAN", "PROMOTION_APPLY"]) assert.equal(definition.operations.includes(operation), true);
  for (const operation of ["ACTIVATE", "ROLLBACK"]) assert.equal(definition.operations.includes(operation), true);
  assert.equal(definition.operations.includes("APPLY"), true);
  assert.equal(definition.approvedPolicyRef, attendedReview().approvedPolicyRef);
  assert.deepEqual(input, before);
  const proposal = buildOfferProposal(input);
  assert.deepEqual(proposal.blockers, ["ATTENDED_IMMUTABLE_OFFER_REVIEW_REQUIRED"]);
  assert.equal(proposal.liveReadinessProven, false);
  assert.ok(proposal.hostedReadinessGates.includes("PRODUCTION_PROMOTION_ACCEPTANCE_REQUIRED"));
});

test("review, unknown success flags, BYO support and Inbox identities are not publication authority", () => {
  for (const modes of [undefined, [CUSTOMER_AI_STATE_MODE], [MANAGED_AI_STATE_MODE, CUSTOMER_AI_STATE_MODE]]) {
    const input = attendedInputs(); input.aiStateStorageModes = modes;
    if (modes?.length === 1) input.aiStateStorageMode = CUSTOMER_AI_STATE_MODE;
    assert.throws(() => buildPublishableOfferDefinition(input, {...attendedReview(), sqlRotationVerified: true}));
  }
  for (const review of [undefined, null, {}, {...attendedReview(), contract: "unknown"},
    {...attendedReview(), approvedPolicyRef: ""}, {...attendedReview(), name: "x\nY"},
    {...attendedReview(), offerId: "offer/path"}, {...attendedReview(), integratedProof: true},
    {...attendedReview(), inboxSpecFingerprint: "not-a-hash"}]) {
    assert.throws(() => buildPublishableOfferDefinition(attendedInputs(), review));
  }
  for (const mutate of [input => {input.inboxArtifact.imageDigest = `sha256:${"c".repeat(64)}`;},
    input => {input.inboxSpec.publication.fingerprint = "c".repeat(64);},
    input => {input.inboxSpec.publication.templateVersionId = "other-template";},
    input => {input.inboxSpec.publication.reviewedAt = null;},
    input => {input.inboxSpec.spec.services[0].database = {image: "business-db"};},
    input => {input.inboxSpec.spec.services.push(structuredClone(input.inboxSpec.spec.services[0]));}]) {
    const input = attendedInputs(); mutate(input);
    assert.throws(() => buildPublishableOfferDefinition(input, attendedReview()));
  }
});

test("reference labels never infer approval or readiness from lexical content", () => {
  const review = attendedReview();
  for (const field of ["reviewReference", "approvedPolicyRef", "measuredPolicyRef", "operatingPolicyRef", "identityPolicyRef", "storagePolicyRef"]) {
    review[field] = "pending-unapproved-manual-evidence-identifier";
  }
  const input = attendedInputs();
  const definition = buildPublishableOfferDefinition(input, review);
  assert.equal(definition.approvedPolicyRef, review.approvedPolicyRef);
  assert.equal(definition.limits.measuredPolicyRef, review.measuredPolicyRef);
  const proposal = buildOfferProposal(input);
  assert.equal(proposal.liveReadinessProven, false);
  assert.equal(proposal.hostedReadinessGates.includes("MEASURED_CAPACITY_ADMISSION_REQUIRED"), true);
});

test("normalization follows server enum declaration ordering, lexicographic sets and null slot fields", () => {
  const input = attendedInputs();
  const definition = buildPublishableOfferDefinition(input, attendedReview());
  const reordered = structuredClone(definition);
  reordered.operations.reverse(); reordered.admissionPrerequisites.reverse();
  reordered.bindingSlots.forEach(slot => {if (slot.pluginId === null) delete slot.pluginId;
    if (slot.installConfigField === null) delete slot.installConfigField; slot.supportedProfileIds.reverse();});
  assert.deepEqual(normalizeOfferDefinition(reordered), definition);
  assert.equal(definition.operations[4], "VERSION_PUBLISH");
  assert.equal(definition.operations.indexOf("INBOX_OPERATOR_ENROLL") < definition.operations.indexOf("SOURCE_DISCOVER"), true);
  assert.deepEqual(definition.admissionPrerequisites,
    ["APPROVED_POLICY", "IMMUTABLE_ARTIFACTS", "STORAGE", "IDENTITY", "CAPACITY", "NOTIFICATIONS", "OPERATING_POLICY"]);
  for (const field of ["operations", "admissionPrerequisites", "documentAudiences"]) {
    const invalid = structuredClone(definition); invalid[field].push("UNKNOWN");
    assert.throws(() => normalizeOfferDefinition(invalid), /OFFER_ENUM_CONTRACT_INVALID/);
  }
});

test("managed canary fingerprint matches the actual Java CustomerOfferCodec golden roundtrip", async () => {
  const prepared = await publicationFixture().prepare();
  // Verified with the compiled production Definition + CustomerOfferCodec, not
  // a JS mirror of the record deserializer. No Maven target was changed.
  assert.equal(prepared.offerFingerprint, "b2d7cffbf29f994403d837421c097d69f5e056430c72bbe185b34f582b527348");
});

test("publisher routes are anchored to actual Spring mappings, not duplicated fixture guesses", async () => {
  const fixture = publicationFixture(); await fixture.prepare();
  const routes = [
    ["deployment/web/DeploymentController.java", "/api", "/deployment-behaviors"],
    ["deployment/web/DeploymentController.java", "/api", "/deployment-execution-extensions"],
    ["deployment/web/DeploymentProviderOperationsController.java", "/api/deployment-provider", "/target-profiles"],
    ["deployment/web/DeploymentProviderOperationsController.java", "/api/deployment-provider", "/source-artifacts"],
    ["marketplace/web/MarketplaceController.java", "/api", "/marketplace/plugins/{pluginId}/versions/{version}"],
  ];
  for (const [file, root, mapping] of routes) {
    const source = readFileSync(new URL(`../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/${file}`, import.meta.url), "utf8");
    assert.equal(source.includes(`@RequestMapping("${root}")`), true, file);
    assert.equal(source.includes(`@GetMapping("${mapping}")`), true, file);
    if (!mapping.includes("{")) {
      assert.equal(fixture.calls.some(call => call.path.split("?")[0] === root + mapping), true, root + mapping);
    }
  }
  assert.equal(fixture.calls.some(call => call.path.startsWith("/api/deployments/deployment-")), false);
});

test("prepare is read-only; publication writes exactly two placement receipts and one reviewed offer", async () => {
  const fixture = publicationFixture();
  const prepared = await fixture.prepare();
  assert.equal(fixture.effects().length, 0);
  assert.equal(prepared.offerFingerprint, hash(prepared.request.definition));
  const {preparationSha256, ...payload} = prepared;
  assert.equal(preparationSha256, hash(payload));
  assert.equal(JSON.stringify(prepared).includes("credential-staging"), false, "Never retain provider credential refs or raw target config");
  const receipt = await fixture.publish(prepared);
  assert.equal(receipt.state, "PUBLISHED_CUSTOMER_READBACK_PENDING");
  assert.equal(receipt.immutableReadbackVerified, false);
  assert.equal(receipt.liveReadinessProven, false);
  assert.equal(receipt.remainingGates.includes("CUSTOMER_IMMUTABLE_OFFER_READBACK_REQUIRED"), true);
  assert.deepEqual(fixture.effects().map(call => call.path), [
    "/api/customer-self-service/staff/targets/dtp-coolify-staging-behavior/placement-evidence",
    "/api/customer-self-service/staff/targets/dtp-coolify-production-behavior/placement-evidence",
    "/api/customer-self-service/staff/offers",
  ]);
  assert.deepEqual(fixture.effects().at(-1).options.body, prepared.request);
  assert.equal(fixture.effects().some(call => /apply|invitation|fleet-policies|grant|source-index|promote|source-artifacts/.test(call.path)), false);
});

test("readback uses a separately authorized actual customer API, never a nonexistent staff GET or forced entitlement", async () => {
  const fixture = publicationFixture();
  const prepared = await fixture.prepare(); await fixture.publish(prepared);
  fixture.calls.length = 0;
  const result = await fixture.verify(prepared);
  assert.equal(result.state, "IMMUTABLE_CUSTOMER_READBACK_VERIFIED");
  assert.equal(result.immutableReadbackVerified, true);
  assert.equal(result.liveReadinessProven, false);
  assert.equal(result.remainingGates.includes("PRODUCTION_PROMOTION_ACCEPTANCE_REQUIRED"), true);
  assert.equal(result.remainingGates.some(value => value.includes("ROTATION") || value.includes("BOOTSTRAP")), false);
  assert.equal(fixture.effects().length, 0);
  assert.deepEqual(fixture.calls.map(call => call.path), ["/api/platform/auth/session",
    `/api/customer-self-service/catalogue/${prepared.request.offerId}`]);
});

test("malformed or unknown prepared review cannot cause even placement effects", async () => {
  const fixture = publicationFixture(); const prepared = await fixture.prepare();
  for (const mutate of [value => {value.unknownSuccess = true;}, value => {value.preparationSha256 = "a".repeat(64);},
    value => {value.platformBaseUrl = "https://other.example";}, value => {value.request.offerId = "other-offer";},
    value => {value.request.definition.topology.databases = 99;}, value => {value.configuration.sqlRotationVerified = true;}]) {
    fixture.calls.length = 0;
    const invalid = structuredClone(prepared); mutate(invalid);
    await assert.rejects(fixture.publish(invalid));
    assert.equal(fixture.calls.length, 0);
  }
  for (const value of [undefined, null, "true", {}, [], "b".repeat(64)]) {
    fixture.calls.length = 0;
    await assert.rejects(fixture.publish(prepared, {reviewedPreparationSha256: value}));
    assert.equal(fixture.calls.length, 0);
  }
  fixture.calls.length = 0;
  await assert.rejects(fixture.publish(prepared, {checkpoint: undefined}), /DURABLE_PUBLICATION_CHECKPOINT_REQUIRED/);
  assert.equal(fixture.calls.length, 0);
});

for (const [name, mutate] of [
  ["template pin", input => {input.template.id = "unreviewed-template";}],
  ["unpublished plugin", input => {input.pluginVersions[0].status = "DRAFT";}],
  ["provider target identity", input => {input.targets[0].providerConfig.serverUuid = "another-server";}],
  ["capacity", input => {input.targets[0].resourceDefaults.connectorLimitsMemory = "2g";}],
  ["runtime CI provenance", input => {input.artifact.buildRunId = "";}],
  ["runtime identity", input => {input.artifact.imageDigest = `sha256:${"c".repeat(64)}`;}],
  ["Inbox manifest", input => {input.inboxArtifact.capabilityManifest = {};}],
  ["Inbox spec", input => {input.inboxSpec.publication.fingerprint = "c".repeat(64);}],
  ["behavior catalogue", input => {input.behaviorCatalog[0].requiredRuntimeCapabilities.push("unknown-capability");}],
]) {
  test(`server ${name} drift is rejected before placement/publication effects`, async () => {
    const fixture = publicationFixture(); const prepared = await fixture.prepare();
    mutate(fixture.input); fixture.calls.length = 0;
    await assert.rejects(fixture.publish(prepared));
    assert.equal(fixture.effects().length, 0);
  });
}

test("missing or ambiguous server records are not selected by first/latest/name inference", async () => {
  for (const [path, response] of [
    ["/api/deployment-provider/target-profiles", null],
    ["/api/deployment-provider/target-profiles", []],
    ["/api/deployment-provider/target-profiles", [target("dtp-coolify-staging-behavior", "staging"), target("dtp-coolify-staging-behavior", "staging")]],
    ["/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime", []],
    ["/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime", [attendedInputs().artifact, attendedInputs().artifact]],
  ]) {
    const fixture = publicationFixture();
    await assert.rejects(fixture.prepare({request: (requested, options) => requested === path ? response : fixture.request(requested, options)}));
    assert.equal(fixture.effects().length, 0);
  }
});

test("invalid placement and post-probe drift never reach offer publication", async () => {
  for (const patch of [{id: null}, {profileFingerprint: "unknown"}, {intent: "CUSTOMER_PRODUCTION"},
    {targetProfileId: "other-target"}, {checkedAt: "2026-10-19T08:01:00Z"}, {expiresAt: "2026-10-19T07:59:59Z"}]) {
    const fixture = publicationFixture(); const prepared = await fixture.prepare();
    await assert.rejects(fixture.publish(prepared, {request: async (path, options) => {
      const result = await fixture.request(path, options);
      return path.endsWith("placement-evidence") ? {...result, ...patch} : result;
    }}));
    assert.equal(fixture.calls.some(call => call.path.endsWith("/staff/offers")), false);
  }
  const fixture = publicationFixture(); const prepared = await fixture.prepare();
  await assert.rejects(fixture.publish(prepared, {request: async (path, options) => {
    const result = await fixture.request(path, options);
    if (path.endsWith("placement-evidence")) fixture.input.targets[0].providerConfig.serverUuid = "drift-after-probe";
    return result;
  }}));
  assert.equal(fixture.calls.some(call => call.path.endsWith("/staff/offers")), false);
});

test("failed durable checkpoint blocks publication and uncertain POST is never retried or relabeled successful", async () => {
  const fixture = publicationFixture(); const prepared = await fixture.prepare();
  await assert.rejects(fixture.publish(prepared, {checkpoint: async () => {throw new Error("Local checkpoint unavailable");}}));
  assert.equal(fixture.calls.some(call => call.path.endsWith("/staff/offers")), false);
  for (const response of [null, {}, {offerId: "other", fingerprint: prepared.offerFingerprint},
    {offerId: prepared.request.offerId, fingerprint: "c".repeat(64)}, {offerId: prepared.request.offerId,
      fingerprint: prepared.offerFingerprint, unexpected: true}]) {
    fixture.calls.length = 0; fixture.checkpoints.length = 0;
    await assert.rejects(fixture.publish(prepared, {request: async (path, options) => {
      if (path.endsWith("/staff/offers")) {
        fixture.calls.push({path, options}); return response;
      }
      return fixture.request(path, options);
    }}), /PUBLICATION_OUTCOME_UNKNOWN/);
    assert.equal(fixture.calls.filter(call => call.path.endsWith("/staff/offers")).length, 1);
    assert.equal(fixture.checkpoints.at(-1).state, "PUBLICATION_OUTCOME_UNKNOWN");
  }
  await assert.rejects(fixture.publish(prepared, {request: async (path, options) => {
    if (path.endsWith("/staff/offers")) throw new Error("raw-private-transport-error");
    return fixture.request(path, options);
  }}), error => error.message.startsWith("PUBLICATION_OUTCOME_UNKNOWN") && !error.message.includes("raw-private"));
});

test("customer readback rejects wrong authority, ownership, hash, shape and expired entitlement without writes", async () => {
  const fixture = publicationFixture(); const prepared = await fixture.prepare(); await fixture.publish(prepared);
  for (const session of [{}, {enabled: true, authenticated: true, role: "PLATFORM_ADMIN", authenticationMode: "API_KEY"},
    {enabled: true, authenticated: true, role: "CUSTOMER_ADMIN", authenticationMode: "SESSION", customerId: "other-customer"}]) {
    fixture.calls.length = 0;
    await assert.rejects(fixture.verify(prepared, {request: async path => path.endsWith("/session") ? session : fixture.customerRequest(path)}), /CUSTOMER_OWNER_SESSION_REQUIRED/);
    assert.equal(fixture.calls.length, 0);
  }
  for (const mutate of [value => {value.fingerprint = "c".repeat(64);}, value => {value.entitlement.ownerUserId = "other-owner";},
    value => {value.entitlement.expiresAt = "2026-10-18T00:00:00Z";}, value => {value.entitlement.status = "REVOKED";},
    value => {value.targets.push(value.targets[0]);}, value => {value.settings[1].allowedValues.push(CUSTOMER_AI_STATE_MODE);},
    value => {value.workspaceProfiles = [];}, value => {value.permissions.push({operation: "UNREVIEWED_OPERATION"});}]) {
    const altered = structuredClone(fixture.readback()); mutate(altered); fixture.calls.length = 0;
    await assert.rejects(fixture.verify(prepared, {request: async path => path.includes("/catalogue/") ? altered : fixture.customerRequest(path)}));
    assert.equal(fixture.effects().length, 0);
  }
});

test("default HTTP transport keeps authority on one HTTPS origin, refuses redirects and sanitizes failures", async () => {
  for (const invalid of [undefined, "http://control-plane.example", "https://user:pass@control-plane.example", "https://control-plane.example/path",
    "https://control-plane.example/?token=secret", "https://control-plane.example/#fragment"]) {
    assert.throws(() => platformOfferRequest(invalid, {apiKey: "test-key"}), /HTTPS_PLATFORM_ORIGIN_REQUIRED/);
  }
  assert.throws(() => platformOfferRequest(baseUrl, {apiKey: "a", sessionCookie: "b"}), /EXACTLY_ONE_PLATFORM_AUTHORITY_REQUIRED/);
  let calls = 0;
  const request = platformOfferRequest(baseUrl, {apiKey: "private-test-key", fetchImpl: async (url, options) => {
    calls++; assert.equal(url, `${baseUrl}/api/platform/auth/session`);
    assert.equal(options.redirect, "error");
    assert.equal(options.headers["X-PLATFORM-API-KEY"], "private-test-key");
    assert.equal(options.headers.Cookie, undefined);
    throw new Error("raw-private-test-key-error");
  }});
  await assert.rejects(request("https://evil.example/api"), /PLATFORM_PATH_INVALID/);
  assert.equal(calls, 0);
  await assert.rejects(request("/api/platform/auth/session"), error => error.message === "PLATFORM_TRANSPORT_OUTCOME_UNKNOWN");
  assert.equal(calls, 1, "No retries");
  for (const response of [new Response("private-server-error", {status: 401}), new Response("private-invalid-json"),
    new Response("{}", {headers: {"content-length": "4000001"}})]) {
    const client = platformOfferRequest(baseUrl, {sessionCookie: "private-cookie", fetchImpl: async (_url, options) => {
      assert.equal(options.headers.Cookie, "private-cookie"); assert.equal(options.headers["X-PLATFORM-API-KEY"], undefined);
      return response;
    }});
    await assert.rejects(client("/api/platform/auth/session"), error => /^PLATFORM_/.test(error.message) && !error.message.includes("private"));
  }
});

test("privileged identity keys and unknown configuration cannot enter the review file", async () => {
  for (const key of ["sb_secret_synthetic_not_real", "public-test-key", "sb_publishable_", "sb_publishable_space value",
    `eyJhbGciOiJIUzI1NiJ9.${Buffer.from(JSON.stringify({role: "service_role"})).toString("base64url")}.synthetic`]) {
    const fixture = publicationFixture(); fixture.configuration.auth.publishableKey = key;
    await assert.rejects(fixture.prepare(), /PUBLIC_WORKSPACE_AUTH_KEY_REQUIRED/);
    assert.equal(fixture.calls.length, 0);
  }
  const fixture = publicationFixture();
  fixture.configuration.auth.projectUrl += "/";
  const prepared = await fixture.prepare();
  assert.equal(prepared.configuration.auth.projectUrl, "https://example.supabase.co");
  const invalid = publicationFixture(); invalid.configuration.forceSqlReady = true;
  await assert.rejects(invalid.prepare(), /ATTENDED_OFFER_CONFIGURATION_INVALID/);
  assert.equal(invalid.calls.length, 0);
});

test("real CLI persists complete private checkpoints, verifies as customer and never overwrites an attempt", async () => {
  const directory = await mkdtemp(join(tmpdir(), "01044-offer-cli-"));
  try {
    const fixture = publicationFixture();
    const prepared = await fixture.prepare(); await fixture.publish(prepared);
    const responses = {};
    for (const path of new Set(fixture.calls.filter(call => !call.options.method).map(call => call.path))) {
      responses[path] = await fixture.request(path);
    }
    for (const permission of prepared.request.definition.targets) {
      responses[`/api/customer-self-service/staff/targets/${permission.targetProfileId}/placement-evidence`] = {
        id: `cpe-${permission.intent}`, ...permission, checkedAt: new Date(Date.now() - 1000).toISOString(),
        expiresAt: new Date(Date.now() + 300_000).toISOString()};
    }
    responses["/api/customer-self-service/staff/offers"] = {offerId: prepared.request.offerId, fingerprint: prepared.offerFingerprint};
    responses[`/api/customer-self-service/catalogue/${prepared.request.offerId}`] = fixture.readback();
    // This subprocess cannot reach a host: every fetch must match this finite map.
    const preload = `import {appendFile, readFile} from "node:fs/promises";
      const responses = ${JSON.stringify(responses)};
      globalThis.fetch = async (url, options) => {
        if (!url.startsWith(${JSON.stringify(baseUrl + "/api/")}) || options.redirect !== "error") throw new Error("Unexpected network");
        const path = url.slice(${baseUrl.length});
        if (!Object.hasOwn(responses, path)) throw new Error("Unknown route");
        await appendFile(process.env.OFFER_CLI_FETCH_LOG, JSON.stringify({path, method: options.method}) + "\\n");
        if (path.endsWith("/staff/offers")) {
          const checkpoint = JSON.parse(await readFile(process.env.OFFER_CLI_CHECKPOINT, "utf8"));
          if (checkpoint.state !== "PUBLICATION_ATTEMPT_STARTED" || checkpoint.offerFingerprint !== responses[path].fingerprint) throw new Error("Incomplete checkpoint");
        }
        let value = responses[path];
        if (path === "/api/platform/auth/session" && options.headers.Cookie) value = {enabled:true, authenticated:true,
          role:"CUSTOMER_ADMIN", authenticationMode:"SESSION", customerId:"customer-fixture"};
        if (path.includes("/catalogue/")) value.entitlement.expiresAt = new Date(Date.now() + 3600_000).toISOString();
        return new Response(JSON.stringify(value), {status:200});
      };`;
    const script = fileURLToPath(new URL("../publish-simulated-dealership-offer.mjs", import.meta.url));
    const configPath = join(directory, "config.json"), preparedPath = join(directory, "prepared.json");
    const receiptPath = join(directory, "receipt.json"), verifiedPath = join(directory, "verified.json"), logPath = join(directory, "calls.jsonl");
    await writeFile(configPath, JSON.stringify({review: fixture.review, configuration: fixture.configuration}), {mode: 0o600});
    const run = (mode, args) => spawnSync(process.execPath,
      ["--import", "data:text/javascript;base64," + Buffer.from(preload).toString("base64"), script, mode, ...args],
      {encoding: "utf8", timeout: 30_000, env: {...process.env, PLATFORM_BASE_URL: baseUrl,
        PLATFORM_ADMIN_API_KEY: mode === "verify" ? "" : "synthetic-private-staff-key",
        PLATFORM_CUSTOMER_SESSION_COOKIE: mode === "verify" ? "session=synthetic-private-cookie" : "",
        OFFER_CLI_FETCH_LOG: logPath, OFFER_CLI_CHECKPOINT: receiptPath}});
    const first = run("prepare", ["--config", configPath, "--output", preparedPath]);
    assert.equal(first.status, 0, first.stderr);
    const actualPreparation = JSON.parse(await readFile(preparedPath, "utf8"));
    assert.deepEqual(actualPreparation, prepared);
    assert.equal((await readFile(logPath, "utf8")).includes('"method":"POST"'), false);
    const publishArgs = ["--prepared", preparedPath, "--reviewed-sha256", prepared.preparationSha256, "--output", receiptPath];
    const second = run("publish", publishArgs);
    assert.equal(second.status, 0, second.stderr);
    const receipt = await readFile(receiptPath, "utf8");
    assert.equal(JSON.parse(receipt).state, "PUBLISHED_CUSTOMER_READBACK_PENDING");
    const calls = await readFile(logPath, "utf8");
    assert.equal(run("publish", publishArgs).status, 1);
    assert.equal(await readFile(logPath, "utf8"), calls, "Exclusive output check happens before any HTTP request");
    assert.equal(await readFile(receiptPath, "utf8"), receipt);
    const third = run("verify", ["--prepared", preparedPath, "--reviewed-sha256", prepared.preparationSha256,
      "--customer-id", "customer-fixture", "--owner-user-id", "owner-fixture", "--output", verifiedPath]);
    assert.equal(third.status, 0, third.stderr);
    assert.equal(JSON.parse(await readFile(verifiedPath, "utf8")).state, "IMMUTABLE_CUSTOMER_READBACK_VERIFIED");
    for (const file of [preparedPath, receiptPath, verifiedPath]) {
      assert.equal((await stat(file)).mode & 0o777, 0o600);
      assert.doesNotMatch(await readFile(file, "utf8"), /synthetic-private-staff-key|synthetic-private-cookie/);
    }
  } finally { await rm(directory, {recursive: true, force: true}); }
});
