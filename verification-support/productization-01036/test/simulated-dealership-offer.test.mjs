import assert from "node:assert/strict";
import test from "node:test";

import {
  artifactFingerprint,
  buildOfferDefinition,
  hash,
  OFFER_ID,
  OPENAI_MAX_TOKENS,
  profileFingerprint,
  REVIEWED_LIMITS,
  REVIEWED_TOPOLOGY,
  reviewedTopologyCapacity,
  RUNTIME_IMAGE_DIGEST,
  RUNTIME_SOURCE_COMMIT,
  TEMPLATE_PLUGIN_ID,
  TEMPLATE_VERSION,
  workspaceProfile,
} from "../simulated-dealership-offer.mjs";

const auth = {projectUrl: "https://example.supabase.co", publishableKey: "public-test-key"};
const manifest = (pluginId, version) => ({pluginId, version, contributions: pluginId === TEMPLATE_PLUGIN_ID
  ? {template: {providerConfig: {openaiMaxTokens: OPENAI_MAX_TOKENS}}} : {}});
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
  promotionChannel: "staging",
  capabilityManifest: {
    aiFabricVersion: "0.8.35",
    capabilities: ["deployment-local-provider-budget-v1"],
    endpointClasses: ["provider-budget-administration"],
    migrationIds: ["provider-budget-accounting-v1"],
    verificationPackIds: ["provider-budget-hard-limit-v1"],
  },
  capabilityManifestHash: "manifest-hash",
};

function offerInputs() {
  return {
    template: {
      ...version(TEMPLATE_PLUGIN_ID, TEMPLATE_VERSION, "template-v1-6"),
      contributions: {templateRequiredPluginRefs: ["data@1.0.0"]},
    },
    pluginVersions: [version("data", "1.0.0", "data-v1")],
    artifact: structuredClone(artifact),
    targets: [target("dtp-coolify-staging-behavior", "staging"),
      target("dtp-coolify-production-behavior", "production")],
    auth,
  };
}

test("v9 pins template 1.6.0 and the same guarded image as an unmeasured staging candidate", () => {
  assert.equal(OFFER_ID, "offer-loomai-engage-simulated-dealership-early-access-v9");
  assert.equal(TEMPLATE_VERSION, "1.6.0");
  assert.equal(OPENAI_MAX_TOKENS, 2048);
  assert.match(REVIEWED_LIMITS.measuredPolicyRef, /candidate/);
  assert.equal(RUNTIME_IMAGE_DIGEST, "sha256:d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28");
  assert.equal(RUNTIME_SOURCE_COMMIT, "600f136bf0becb9a473ff1e085bbc9896b4ef696");
  for (const promotionChannel of ["staging", "production"]) {
    const input = offerInputs();
    input.artifact.promotionChannel = promotionChannel;
    const definition = buildOfferDefinition(input);
    assert.equal(definition.template.versionId, "template-v1-6");
    assert.match(definition.name, /Staging Measurement Candidate/);
    assert.deepEqual(definition.artifacts, [{
      artifactId: input.artifact.id, fingerprint: artifactFingerprint(input.artifact),
    }]);
    assert.deepEqual(definition.topology, REVIEWED_TOPOLOGY);
    assert.deepEqual(definition.limits, REVIEWED_LIMITS);
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
  assert.throws(() => buildOfferDefinition(input), /exact 1.6.0 template/);
});

test("v9 rejects legacy or mixed image pins without rewriting v7 artifact identity", () => {
  const legacyArtifact = {
    ...artifact,
    imageDigest: "sha256:d7cc5b3a7d72f65da0f77c02ae50a27d836b8599908566b109b5a07bd5e6f16f",
    gitCommitSha: "bf72080847c960c331d3ec6a2441e8d5f38ca56a",
    capabilityManifest: {aiFabricVersion: "0.8.35"},
  };
  const legacyOffer = {
    offerId: "offer-loomai-engage-simulated-dealership-early-access-v7",
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
    assert.throws(() => buildOfferDefinition(input), /exact promoted AI Fabric 0.8.35 runtime artifact/);
  }
  assert.throws(() => buildOfferDefinition({...offerInputs(), artifact: legacyArtifact}),
    /exact promoted AI Fabric 0.8.35 runtime artifact/);
  const next = buildOfferDefinition(offerInputs());
  assert.notEqual(OFFER_ID, legacyOffer.offerId);
  assert.notEqual(next.artifacts[0].fingerprint, legacyOffer.definition.artifacts[0].fingerprint);
  assert.deepEqual({artifact: legacyArtifact, offer: legacyOffer}, legacySnapshot);
  assert.equal(artifactFingerprint(legacyArtifact), legacyOffer.definition.artifacts[0].fingerprint);
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
    input.artifact.capabilityManifest[field] = ["unrelated-capability", required];
    assert.doesNotThrow(() => buildOfferDefinition(input));
  });
}

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
    milliCpu: 2750,
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
    replicas: 1, cpuMillis: 2750, memoryMiB: 5888, diskMiB: 16384,
    databases: 2, indexes: 2, dependencies: 4, concurrentApplies: 1,
  });
});

test("offer pins exact template dependencies, runtime, targets and reviewed workspace profiles", () => {
  const pluginVersions = [
    version("data", "1.0.0", "data-v1"),
    version("action", "1.0.0", "action-v1"),
  ];
  const template = {
    ...version(TEMPLATE_PLUGIN_ID, TEMPLATE_VERSION, "template-v1-6"),
    contributions: {templateRequiredPluginRefs: ["data@1.0.0", "action@1.0.0"]},
  };
  const definition = buildOfferDefinition({
    template, pluginVersions, artifact,
    targets: [target("dtp-coolify-staging-behavior", "staging"),
      target("dtp-coolify-production-behavior", "production")],
    auth,
  });
  assert.equal(definition.template.versionId, "template-v1-6");
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
  const pluginVersions = [version("data", "1.0.0", "data-v1")];
  const template = {
    ...version(TEMPLATE_PLUGIN_ID, TEMPLATE_VERSION, "template-v1-6"),
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
    ...version(TEMPLATE_PLUGIN_ID, TEMPLATE_VERSION, "template-v1-6"),
    contributions: {templateRequiredPluginRefs: ["missing@1.0.0"]},
  };
  assert.throws(() => buildOfferDefinition({
    template, pluginVersions: [], artifact,
    targets: [target("dtp-coolify-staging-behavior", "staging"),
      target("dtp-coolify-production-behavior", "production")], auth,
  }), /exact required plugin/);
});
