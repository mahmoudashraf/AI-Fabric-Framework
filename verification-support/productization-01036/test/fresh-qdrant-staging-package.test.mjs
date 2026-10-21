import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import {buildFreshQdrantStagingOffer, buildFreshQdrantStagingTemplateManifest, planFreshQdrantStagingOffer,
  planFreshQdrantStagingSpec, planFreshQdrantTemplatePublication, QDRANT_BASE_TEMPLATE_ID,
  QDRANT_INBOX_ARTIFACT_ID, QDRANT_RUNTIME, QDRANT_TEMPLATE_PLUGIN_ID, QDRANT_TEMPLATE_SLUG,
  QDRANT_TEMPLATE_VERSION, requireReviewedQdrantVector} from "../fresh-qdrant-staging-package.mjs";
import {buildPostgresInboxSpec} from "../managed-postgres-inbox-spec.mjs";
import {hash} from "../simulated-dealership-offer.mjs";
import {packageInput} from "./fixtures/managed-postgres-inbox-publication-fixture.mjs";

// Synthetic normal API metadata only. No provider, auth file, SQL, HTTP or hosted authority.
function fixture() {
  const base = packageInput("PUBLISHED");
  const c = base.contracts;
  const reviewedVector = {host: "https://synthetic-qdrant.example:6333", port: 6333, grpcPort: 6334,
    preferGrpc: false, apiKeySecretName: "QDRANT_API_KEY"};
  const template = {id: "mkv-77777777", pluginId: QDRANT_TEMPLATE_PLUGIN_ID, version: QDRANT_TEMPLATE_VERSION,
    status: "PUBLISHED", publishedAt: "2026-10-21T12:00:00Z",
    manifest: buildFreshQdrantStagingTemplateManifest(c.sourceTemplate, reviewedVector)};
  const artifact = {...structuredClone(c.runtimeArtifacts[0]), id: QDRANT_RUNTIME.id,
    imageDigest: QDRANT_RUNTIME.imageDigest, gitCommitSha: QDRANT_RUNTIME.gitCommitSha, promotionChannel: "staging"};
  const inboxArtifact = {...c.inboxArtifacts[0], id: QDRANT_INBOX_ARTIFACT_ID};
  const inboxSpec = structuredClone(c.inboxPublication);
  inboxSpec.spec = buildPostgresInboxSpec({inbox: inboxArtifact});
  inboxSpec.publication.templateVersionId = template.id;
  inboxSpec.publication.specId = "mds-synthetic-qdrant-pg-inbox";
  const pluginVersions = c.pluginVersions.map(row => ({...row, manifest: {...row.manifest,
    compatibility: {supportedDeploymentTargets: ["custom-start-from-scratch", "dev-openai-milvus"]}}}));
  return {sourceTemplate: c.sourceTemplate, reviewedVector, template, pluginVersions, artifact,
    target: c.targets[0], inboxArtifact, inboxSpec, specId: inboxSpec.publication.specId,
    configuration: base.configuration, review: base.review, behaviorCatalog: c.behaviorCatalog,
    executionExtensionCatalog: c.executionExtensionCatalog};
}

function publicationInput() {
  const f = fixture();
  return {sourceTemplate: f.sourceTemplate, reviewedVector: f.reviewedVector,
    publisher: {id: "mpub-synthetic", slug: "synthetic-publisher", status: "ACTIVE", verificationStatus: "VERIFIED"},
    submission: null, reviewReference: "synthetic-qdrant-staging-review"};
}

function submission(input, status) {
  const manifest = buildFreshQdrantStagingTemplateManifest(input.sourceTemplate, input.reviewedVector);
  return {pluginVersionId: "mkv-77777777", publisherId: input.publisher.id, pluginId: QDRANT_TEMPLATE_PLUGIN_ID,
    pluginSlug: QDRANT_TEMPLATE_SLUG, version: QDRANT_TEMPLATE_VERSION, releaseChannel: "EARLY_ACCESS", status,
    manifest, bundleSha256: hash(manifest)};
}

test("new immutable 1.3.0 preserves original 1.0.0 source and plugin defaults on a compatible neutral base with pinned Qdrant", () => {
  const f = fixture(), before = structuredClone(f);
  const manifest = buildFreshQdrantStagingTemplateManifest(f.sourceTemplate, f.reviewedVector);
  const original = f.sourceTemplate.manifest.contributions.template;
  const current = manifest.contributions.template;
  assert.equal(manifest.pluginId, f.sourceTemplate.pluginId);
  assert.equal(manifest.version, "1.3.0");
  assert.equal(f.sourceTemplate.version, "1.0.0");
  assert.equal(current.templateId, QDRANT_BASE_TEMPLATE_ID);
  assert.equal(current.templateId, "custom-start-from-scratch");
  for (const key of ["requiredPluginRefs", "requiredPluginInstallDefaults", "deploymentBehavior"])
    assert.deepEqual(current[key], original[key]);
  assert.deepEqual(current.providerConfig, {openaiMaxTokens: 2048, vectorStrategy: "qdrant",
    vectorProvisioningMode: "EXTERNAL_EXISTING", vectorStoragePosture: "SHARED", qdrantManagedCollectionsEnabled: false,
    qdrantHost: f.reviewedVector.host, qdrantPort: 6333, qdrantGrpcPort: 6334, qdrantPreferGrpc: false});
  assert.deepEqual(manifest.compatibility.supportedDeploymentTargets, [QDRANT_BASE_TEMPLATE_ID]);
  assert.deepEqual(manifest.compatibility.supportedProviderModes, ["vector:lucene", "vector:qdrant"]);
  assert.doesNotMatch(JSON.stringify(current.providerConfig), /milvus|zilliz|CollectionPrefix|RuntimeApiKeySecretName|apiKeySecretName/);
  assert.deepEqual(f, before);
});

test("reviewed endpoint is explicit HTTPS with matching URL port and only the existing serving-key purpose", () => {
  const f = fixture();
  assert.equal(requireReviewedQdrantVector({...f.reviewedVector, host: "https://synthetic-qdrant.example/", port: 443}).host,
    "https://synthetic-qdrant.example");
  for (const patch of [{host: "http://synthetic-qdrant.example:6333"}, {host: "https://user:pass@synthetic-qdrant.example:6333"},
    {host: "https://synthetic-qdrant.example:6333/collections"}, {host: "https://synthetic-qdrant.example:6333?apiKey=value"},
    {host: "https://synthetic-qdrant.example:6333#fragment"}, {port: 443}, {grpcPort: 0}, {port: "6333"},
    {preferGrpc: true}, {apiKeySecretName: "QDRANT_CLOUD_MANAGEMENT_API_KEY"}, {apiKeySecretName: "OTHER_KEY"},
    {qdrantApiKey: "synthetic-forbidden"}, {collectionPrefix: "foreign"}])
    assert.throws(() => requireReviewedQdrantVector({...f.reviewedVector, ...patch}));
});

test("source template is the exact published original and cannot smuggle changed defaults or credentials", () => {
  for (const mutate of [f => { f.sourceTemplate.status = "SUBMITTED"; }, f => { f.sourceTemplate.version = "1.1.0"; },
    f => { f.sourceTemplate.id = "mkv-ffffffff"; },
    f => { f.sourceTemplate.manifest.contributions.template.requiredPluginRefs.pop(); },
    f => { f.sourceTemplate.manifest.contributions.template.requiredPluginInstallDefaults.extra = {}; },
    f => { f.sourceTemplate.manifest.qdrantApiKey = "synthetic-forbidden"; }]) {
    const f = fixture(); mutate(f);
    assert.throws(() => buildFreshQdrantStagingTemplateManifest(f.sourceTemplate, f.reviewedVector));
  }
});

test("publisher descriptors use real routes, no invented version ID, and exact native phase readback", () => {
  const input = publicationInput(), before = structuredClone(input);
  const submit = planFreshQdrantTemplatePublication(input, "template-submit");
  assert.equal(submit.nativeVersionId, null);
  assert.equal(submit.request.path, "/api/marketplace/publishers/mpub-synthetic/submissions");
  assert.equal(submit.request.body.pluginSlug, QDRANT_TEMPLATE_SLUG);
  assert.equal(submit.request.body.manifest.pluginId, QDRANT_TEMPLATE_PLUGIN_ID);
  for (const [phase, status, suffix] of [["template-validate", "SUBMITTED", "validate"], ["template-publish", "VALIDATED", "publish"]]) {
    const plan = planFreshQdrantTemplatePublication({...input, submission: submission(input, status)}, phase);
    assert.equal(plan.request.path, `/api/marketplace/submissions/mkv-77777777/${suffix}`);
    assert.deepEqual(plan.request.body, {reviewNotes: input.reviewReference});
  }
  assert.equal(submit.request.bodySha256, hash(submit.request.body));
  assert.deepEqual(input, before);
  assert.throws(() => planFreshQdrantTemplatePublication({...input, submission: submission(input, "SUBMITTED")}, "template-submit"));
  assert.throws(() => planFreshQdrantTemplatePublication(input, "template-validate"));
  const native = submission(input, "PUBLISHED");
  assert.throws(() => planFreshQdrantTemplatePublication({...input, submission: native}, "template-publish"));
  native.status = "SUBMITTED"; native.publisherId = "mpub-foreign";
  assert.throws(() => planFreshQdrantTemplatePublication({...input, submission: native}, "template-validate"));
  const oldNative = submission(input, "SUBMITTED");
  oldNative.version = "1.2.0";
  assert.throws(() => planFreshQdrantTemplatePublication({...input, submission: oldNative}, "template-validate"));
});

test("PG Inbox spec is reused unchanged and linked only to the actual newly published template ID", () => {
  const f = fixture();
  const input = {sourceTemplate: f.sourceTemplate, reviewedVector: f.reviewedVector, template: f.template,
    inboxArtifact: f.inboxArtifact, specId: f.specId, reviewReference: f.review.reviewReference};
  const before = structuredClone(input);
  const plan = planFreshQdrantStagingSpec(input);
  assert.equal(plan.request.path, `/api/customer-self-service/staff/templates/${f.template.id}/managed-dependency-spec`);
  assert.deepEqual(plan.request.body, {specId: f.specId, spec: buildPostgresInboxSpec({inbox: f.inboxArtifact}),
    reviewReference: f.review.reviewReference});
  assert.equal(plan.request.body.spec.services[0].contract, "BASIC_INBOX_KEYS_V1");
  assert.equal(plan.request.body.spec.services[0].volumes.length, 0);
  assert.ok(plan.request.body.spec.services[0].database);
  assert.deepEqual(input, before);
  assert.throws(() => planFreshQdrantStagingSpec({...input, specId: "mds-dealership-ai-managed-inbox-v1-0"}));
  assert.throws(() => planFreshQdrantStagingSpec({...input, template: {...f.template, status: "VALIDATED"}}));
  assert.throws(() => planFreshQdrantStagingSpec({...input, template: {...f.template, id: "PENDING"}}));
});

test("offer pins latest staging artifact, one staging target and unchanged whole PG/Inbox topology, never production/promotion", () => {
  const f = fixture(), before = structuredClone(f);
  const offer = buildFreshQdrantStagingOffer(f);
  assert.equal(offer.templateId, "custom-start-from-scratch");
  assert.ok(f.pluginVersions.every(row => row.manifest.compatibility.supportedDeploymentTargets.includes(offer.templateId)));
  assert.equal(offer.template.versionId, f.template.id);
  assert.deepEqual(offer.artifacts.map(row => row.artifactId), [QDRANT_RUNTIME.id, f.inboxArtifact.id]);
  assert.deepEqual(offer.targets.map(row => [row.targetProfileId, row.intent]), [[f.target.id, "CUSTOMER_STAGING"]]);
  assert.deepEqual(offer.topology, {replicas: 1, cpuMillis: 2250, memoryMiB: 4096, diskMiB: 6144,
    databases: 2, indexes: 2, dependencies: 2, concurrentApplies: 1});
  assert.deepEqual(offer.aiStateStorageTopologies, {MANAGED_POSTGRES: offer.topology});
  assert.equal(offer.limits.activeStagingTargets, 1);
  assert.ok(offer.operations.includes("APPLY") && offer.operations.includes("SOURCE_INDEX") && offer.operations.includes("INBOX_OPERATOR_ENROLL"));
  assert.ok(!offer.operations.some(value => value.startsWith("PROMOTION_")));
  assert.ok(!offer.settings.some(row => /QDRANT|VECTOR/.test(row.setting)));
  assert.ok(!offer.bindingSlots.some(row => row.kind === "CUSTOMER_AI_STATE"));
  assert.ok(offer.externalIntegrationProfiles.every(row => row.targetProfileIds.length === 1 && row.targetProfileIds[0] === f.target.id));
  const plan = planFreshQdrantStagingOffer({offerId: "offer-synthetic-qdrant-stage", ...f});
  assert.equal(plan.request.path, "/api/customer-self-service/staff/offers");
  assert.deepEqual(plan.request.body, {offerId: "offer-synthetic-qdrant-stage", definition: offer, reviewReference: f.review.reviewReference});
  assert.equal(plan.offerFingerprint, hash(offer));
  assert.equal(plan.liveReadinessProven, false);
  assert.equal(plan.freeQuotaVerified, false);
  assert.deepEqual(f, before);
});

test("offer rejects artifact/channel/framework, scope, spec, endpoint, plugin and capacity drift", () => {
  for (const mutate of [f => { f.artifact.id = "dsa-395fa10f"; }, f => { f.artifact.imageDigest = "sha256:" + "0".repeat(64); },
    f => { f.artifact.gitCommitSha = "0".repeat(40); }, f => { f.artifact.promotionChannel = "production"; },
    f => { f.artifact.capabilityManifest.aiFabricVersion = "0.8.37"; }, f => { f.artifact.capabilityManifest.capabilities = []; },
    f => { f.target.environmentName = "production"; }, f => { f.target.id = "dtp-coolify-production-behavior"; },
    f => { f.target.resourceDefaults.runtimeLimitsMemory = "2g"; }, f => { f.configuration.aiStateStorageMode = "CUSTOMER_POSTGRES"; },
    f => { f.inboxSpec.publication.templateVersionId = "mkv-foreign"; }, f => { f.inboxSpec.spec.services[0].database = null; },
    f => { f.template.manifest.contributions.template.providerConfig.qdrantManagedCollectionsEnabled = true; },
    f => { f.template.manifest.contributions.template.providerConfig.qdrantHost = "https://foreign.example:6333"; },
    f => { f.template.version = "1.2.0"; },
    f => { f.pluginVersions[0].id = "mkv-ffffffff"; }, f => { f.review.stagingExpiryHours = 25; },
    f => { f.configuration.auth.publishableKey = "sb_secret_forbidden"; }]) {
    const f = fixture(); mutate(f); assert.throws(() => buildFreshQdrantStagingOffer(f));
  }
  for (let index = 0; index < fixture().pluginVersions.length; index++) {
    const f = fixture();
    f.pluginVersions[index].manifest.compatibility.supportedDeploymentTargets = ["dev-openai-milvus"];
    assert.throws(() => planFreshQdrantStagingOffer({offerId: "offer-synthetic-qdrant-stage", ...f}),
      /REQUIRED_PLUGIN_BASE_TEMPLATE_UNSUPPORTED/);
  }
  for (const supportedTargets of ["custom-start-from-scratch", [null], [" custom-start-from-scratch "]]) {
    const f = fixture();
    f.pluginVersions[0].manifest.compatibility.supportedDeploymentTargets = supportedTargets;
    assert.throws(() => buildFreshQdrantStagingOffer(f), /REQUIRED_PLUGIN_TARGET_COMPATIBILITY_INVALID/);
  }
  const unrestricted = fixture();
  delete unrestricted.pluginVersions[0].manifest.compatibility;
  unrestricted.pluginVersions[1].manifest.compatibility.supportedDeploymentTargets = [];
  assert.doesNotThrow(() => buildFreshQdrantStagingOffer(unrestricted));
});

test("native bootstrap and template schema support pinned configuration without a new customer provider setting", () => {
  const backend = new URL("../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/", import.meta.url);
  const read = path => readFileSync(new URL(path, backend), "utf8");
  const schema = read("marketplace/service/MarketplaceManifestService.java");
  for (const key of Object.keys(fixture().template.manifest.contributions.template.providerConfig))
    assert.ok(schema.includes(`"${key}"`), `native template field: ${key}`);
  assert.ok(!schema.includes('"qdrantRuntimeApiKeySecretName"'));
  assert.match(read("customerselfservice/service/CustomerDeploymentBootstrapService.java"), /templates\.bootstrapConfiguration\(/);
  assert.match(read("deployment/service/DeploymentService.java"), /createDeploymentConfiguration\(CreateDeploymentRequest request\)\s*\{\s*return createDeployment\(request, false\);/);
  assert.match(read("marketplace/service/MarketplaceTemplateBootstrapService.java"), /compileTemplateProviderBaseline\(/);
  assert.match(read("deployment/service/DeploymentService.java"), /Reviewed-offer deployments must use the scoped customer self-service configuration/);
  const runtimePlan = read("deployment/service/RailwayProvisioningPlanService.java");
  assert.ok(runtimePlan.includes("targetScopedProviderConfig(deployment, providerConfig, targetProfileId)"));
  assert.ok(runtimePlan.includes('"QDRANT_API_KEY", runtimeSecretName, "AI_PROVIDERS_QDRANT_API_KEY"'));
});
