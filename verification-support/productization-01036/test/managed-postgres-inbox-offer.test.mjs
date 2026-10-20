import assert from "node:assert/strict";
import test from "node:test";
import {buildPostgresDealershipOffer, buildPostgresInboxTemplateManifest, postgresDealershipTopology} from "../managed-postgres-inbox-dealership-offer.mjs";
import {offerArguments, packageInput} from "./fixtures/managed-postgres-inbox-publication-fixture.mjs";

test("new version clones the exact published source without overwriting any old version or required plugin", () => {
  const input = packageInput(), source = input.contracts.sourceTemplate, before = structuredClone(source);
  const manifest = buildPostgresInboxTemplateManifest(source, "1.1.0");
  assert.deepEqual(source, before);
  assert.equal(manifest.version, "1.1.0");
  assert.equal(manifest.pluginId, source.pluginId);
  assert.deepEqual(manifest.contributions, source.manifest.contributions);
  assert.throws(() => buildPostgresInboxTemplateManifest(source, "1.0.0"));
  assert.throws(() => buildPostgresInboxTemplateManifest(source, "PENDING"));
});

for (const mode of ["MANAGED_POSTGRES", "CUSTOMER_POSTGRES"]) {
  test(`${mode} offer has exact whole-topology accounting with only AI-state DBs and two derived indexes`, () => {
    const args = offerArguments(packageInput("PUBLISHED", mode)), before = structuredClone(args);
    const definition = buildPostgresDealershipOffer(args);
    assert.deepEqual(definition.topology, postgresDealershipTopology(mode));
    assert.deepEqual(definition.aiStateStorageTopologies, {[mode]: postgresDealershipTopology(mode)});
    const {replicas, cpuMillis, memoryMiB, diskMiB, databases, dependencies, indexes, concurrentApplies} = definition.limits;
    assert.deepEqual({replicas, cpuMillis, memoryMiB, diskMiB, databases, dependencies, indexes, concurrentApplies}, definition.topology);
    assert.deepEqual(definition.artifacts.map(row => row.artifactId), ["dsa-22222222", "dsa-11111111"]);
    assert.deepEqual(definition.settings.find(row => row.setting === "AI_STATE_STORAGE_MODE").allowedValues, [mode]);
    assert.equal(definition.bindingSlots.some(row => row.kind === "CUSTOMER_AI_STATE"), mode === "CUSTOMER_POSTGRES");
    assert.equal(definition.topology.databases, mode === "MANAGED_POSTGRES" ? 2 : 1);
    assert.equal(definition.topology.dependencies, 2);
    assert.equal(definition.topology.indexes, 2);
    assert.equal(definition.template.versionId, "mkv-33333333");
    assert.deepEqual(args, before);
    assert.equal(definition.externalIntegrationProfiles.length, 2);
    assert.doesNotMatch(JSON.stringify(definition), /MANAGED_SIMULATOR|MANAGED_RECEIVER|seed-sync|business-database/);
  });
}

test("no fallback AI-state mode or silent capacity/template/artifact/profile drift", () => {
  for (const mutate of [a => { a.configuration.aiStateStorageMode = "H2"; }, a => { a.configuration.aiStateStorageMode = null; },
    a => { a.template.id = "mkv-template-dealership-external-systems-managed-inbox-v1-0"; },
    a => { a.artifact.artifactRole = "MANAGED_DEPENDENCY"; }, a => { a.artifact.promotionChannel = "staging"; },
    a => { a.artifact.gitCommitSha = "0".repeat(40); }, a => { a.artifact.imageDigest = "sha256:" + "0".repeat(64); },
    a => { a.artifact.capabilityManifest.capabilities = []; }, a => { a.targets[0].resourceDefaults.runtimeLimitsMemory = "2g"; },
    a => { a.targets[1].environmentName = "staging"; }, a => { a.pluginVersions[0].id = "mkv-ffffffff"; },
    a => { a.template.manifest.contributions.template.providerConfig.vectorProvisioningMode = "PLATFORM_MANAGED"; },
    a => { a.inboxSpec.publication.specId = "mds-dealership-ai-managed-inbox-v1-0"; },
    a => { a.review.measuredPolicyRef = "PENDING_MEASUREMENT"; }, a => { a.review.stagingExpiryHours = 48; }]) {
    const args = offerArguments(); mutate(args);
    assert.throws(() => buildPostgresDealershipOffer(args));
  }
});

test("public workspace config is allowed, credentials and business defaults are not", () => {
  for (const mutate of [a => { a.configuration.auth.publishableKey = "sb_secret_synthetic_forbidden"; },
    a => { a.configuration.auth.projectUrl = "https://user:password@synthetic.example"; },
    a => { a.artifact.secretValue = "synthetic-raw-key"; },
    a => { a.template.manifest.contributions.template.requiredPluginInstallDefaults.receiver = {password: "synthetic-value"}; },
    a => { a.configuration.auth.serviceRoleKey = "synthetic-value"; }]) {
    const args = offerArguments(); mutate(args); assert.throws(() => buildPostgresDealershipOffer(args));
  }
});
