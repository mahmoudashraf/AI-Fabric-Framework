import assert from "node:assert/strict";
import test from "node:test";

import {
  artifactFingerprint,
  buildOfferDefinition,
  hash,
  profileFingerprint,
  REVIEWED_LIMITS,
  REVIEWED_TOPOLOGY,
  reviewedTopologyCapacity,
  RUNTIME_IMAGE_DIGEST,
  RUNTIME_SOURCE_COMMIT,
  workspaceProfile,
} from "../simulated-dealership-offer.mjs";

const auth = {projectUrl: "https://example.supabase.co", publishableKey: "public-test-key"};
const manifest = (pluginId, version) => ({pluginId, version, contributions: {}});
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
    connectorLimitsCpus: "0.5", connectorLimitsMemory: "768m", connectorLimitsMemorySwap: "768m",
    connectorLimitsMemoryReservation: "384m", connectorJavaOpts: "-XX:ActiveProcessorCount=1 -Xms128m -Xmx512m",
    vectorizationRunnerLimitsCpus: "0.5", vectorizationRunnerLimitsMemory: "768m",
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
  promotionChannel: "staging", capabilityManifest: {aiFabricVersion: "0.8.33"},
  capabilityManifestHash: "manifest-hash",
};

test("canonical hashes do not depend on object insertion order", () => {
  assert.equal(hash({b: 2, a: {d: 4, c: 3}}), hash({a: {c: 3, d: 4}, b: 2}));
});

test("provider and runtime fingerprint changes are observable", () => {
  const staging = target("dtp-coolify-staging-behavior", "staging");
  assert.notEqual(profileFingerprint(staging), profileFingerprint({...staging, providerConfig: {...staging.providerConfig, serverUuid: "other"}}));
  assert.notEqual(artifactFingerprint(artifact), artifactFingerprint({...artifact, imageDigest: `sha256:${"c".repeat(64)}`}));
});

test("workspace profile IDs use the backend normalized enum representation", () => {
  const profile = workspaceProfile(auth);
  const configuration = {
    page: {
      kind: "auto", maxChars: 1800, maxPages: 3,
      contextLabel: "Current dealership page", rootSelector: "#main-content", maxTotalChars: 10000,
    },
    dealer: {
      id: "simulated-dealership", sourceMode: "DEALERSHIP_INVENTORY", assistantLabel: "Dealership AI",
    },
    knowledge: {inventoryVectorSpace: "dealer-vehicle"},
    capabilities: {callback: true, testDrive: true, comparison: true},
    presentation: {imageHostAllowlist: ["m.atcdn.co.uk"]},
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
  assert.deepEqual(reviewedTopologyCapacity(), {
    milliCpu: 3750,
    memoryMiB: 5888,
    diskMiB: 16384,
    dependencies: 4,
    databases: 2,
    indexes: 2,
  });
  assert.equal(REVIEWED_LIMITS.cpuMillis >= reviewedTopologyCapacity().milliCpu, true);
  assert.equal(REVIEWED_LIMITS.memoryMiB >= reviewedTopologyCapacity().memoryMiB, true);
  assert.equal(REVIEWED_LIMITS.diskMiB, reviewedTopologyCapacity().diskMiB);
  assert.equal(REVIEWED_LIMITS.dependencies, reviewedTopologyCapacity().dependencies);
  assert.deepEqual(REVIEWED_TOPOLOGY, {
    replicas: 1, cpuMillis: 3750, memoryMiB: 5888, diskMiB: 16384,
    databases: 2, indexes: 2, dependencies: 4, concurrentApplies: 1,
  });
});

test("offer pins exact template dependencies, runtime, targets and reviewed workspace profiles", () => {
  const pluginVersions = [
    version("data", "1.0.0", "data-v1"),
    version("action", "1.0.0", "action-v1"),
  ];
  const template = {
    ...version("mkp-template-simulated-dealership-experience-v1", "1.4.0", "template-v1-4"),
    contributions: {templateRequiredPluginRefs: ["data@1.0.0", "action@1.0.0"]},
  };
  const definition = buildOfferDefinition({
    template, pluginVersions, artifact,
    targets: [target("dtp-coolify-staging-behavior", "staging"),
      target("dtp-coolify-production-behavior", "production")],
    auth,
  });
  assert.equal(definition.template.versionId, "template-v1-4");
  assert.equal(definition.templateId, "dev-openai-milvus");
  assert.deepEqual(definition.plugins.map(({versionId}) => versionId), ["data-v1", "action-v1"]);
  assert.equal(definition.artifacts[0].artifactId, "runtime-artifact");
  assert.deepEqual(definition.targets.map(({intent}) => intent), ["CUSTOMER_STAGING", "CUSTOMER_PRODUCTION"]);
  assert.equal(definition.operations.includes("VERSION_PUBLISH"), true);
  assert.equal(definition.operations.includes("DRAFT_EDIT"), false);
  assert.equal(definition.operations.includes("INBOX_OPERATOR_ENROLL"), true);
  assert.equal(definition.operations.includes("INBOX_OPERATOR_DELIVER"), true);
  assert.equal(definition.bindingSlots[0].pluginId, "mkp-data-dealership-knowledge-v1");
  assert.deepEqual(definition.topology, REVIEWED_TOPOLOGY);
  assert.deepEqual(definition.bindingSlots[1], {
    id: "inbox", kind: "INBOX_OPERATOR", supportedProfileIds: ["managed-basic-inbox-keys-v1"],
  });
  assert.equal(definition.settings.filter(({type}) => type === "CHOICE").every(({allowedValues}) => allowedValues.length > 0), true);
});

test("offer refuses a target whose enforced worker capacity drifted", () => {
  const pluginVersions = [version("data", "1.0.0", "data-v1")];
  const template = {
    ...version("mkp-template-simulated-dealership-experience-v1", "1.4.0", "template-v1-4"),
    contributions: {templateRequiredPluginRefs: ["data@1.0.0"]},
  };
  const staging = target("dtp-coolify-staging-behavior", "staging");
  staging.resourceDefaults.connectorLimitsCpus = "2.0";

  assert.throws(() => buildOfferDefinition({
    template, pluginVersions, artifact,
    targets: [staging, target("dtp-coolify-production-behavior", "production")], auth,
  }), /drifted capacity fields: connectorLimitsCpus/);
});

test("offer rejects incomplete required plugin pinning", () => {
  const template = {
    ...version("mkp-template-simulated-dealership-experience-v1", "1.4.0", "template-v1-4"),
    contributions: {templateRequiredPluginRefs: ["missing@1.0.0"]},
  };
  assert.throws(() => buildOfferDefinition({
    template, pluginVersions: [], artifact,
    targets: [target("dtp-coolify-staging-behavior", "staging"),
      target("dtp-coolify-production-behavior", "production")], auth,
  }), /exact required plugin/);
});
