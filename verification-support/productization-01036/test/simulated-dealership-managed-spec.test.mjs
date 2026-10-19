import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {buildManagedDependencySpec, DEPENDENCY_IMAGES, EXTERNAL_TEMPLATE_VERSION_ID,
  INBOX_CAPABILITY_MANIFEST, INBOX_CAPABILITY_MANIFEST_HASH, reviewedManagedDependencyCapacity,
  SPEC_ID, TEMPLATE_VERSION_ID} from "../simulated-dealership-managed-spec.mjs";
import {comparableJson, inboxArtifactRegistration, publishSimulatedDealershipPackage,
  validateManagedDependencySpec} from "../publish-simulated-dealership-package.mjs";

function artifact() {
  const pin = DEPENDENCY_IMAGES.inbox;
  return {id: "inbox-artifact", artifactRole: "MANAGED_DEPENDENCY", artifactType: "DOCKER_IMAGE",
    imageRepository: pin.repository, imageDigest: pin.digest, gitCommitSha: pin.commit,
    capabilityManifest: structuredClone(INBOX_CAPABILITY_MANIFEST), capabilityManifestHash: INBOX_CAPABILITY_MANIFEST_HASH,
    promotionChannel: "staging", promotedAt: "2026-10-19T00:00:00Z"};
}

test("external composition has no spec, not an invalid empty-services spec or implicit Inbox", () => {
  assert.equal(buildManagedDependencySpec({}, {managedInbox: false}), null);
  assert.throws(() => buildManagedDependencySpec(), /Explicit managedInbox selection/);
  for (const managedInbox of [null, "false", 0, 1]) {
    assert.throws(() => buildManagedDependencySpec({}, {managedInbox}), /Explicit managedInbox selection/);
  }
  assert.notEqual(EXTERNAL_TEMPLATE_VERSION_ID, TEMPLATE_VERSION_ID);
  assert.equal(TEMPLATE_VERSION_ID, "mkv-template-dealership-external-systems-managed-inbox-v1-0");
  assert.equal(SPEC_ID, "mds-dealership-ai-managed-inbox-v1-0");
});

test("explicit optional Inbox is AI-only, has no business database and retains scoped callbacks", () => {
  const spec = validateManagedDependencySpec(buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true}));
  assert.deepEqual(spec.services.map(service => service.slot), ["inbox"]);
  assert.equal(spec.services[0].database, null);
  assert.equal(spec.services[0].initializationContract, "inbox-callback-canary/v1");
  assert.equal(spec.services[0].environment.AI_ENABLED.reference, "false");
  assert.deepEqual(spec.services[0].environment.BASIC_INBOX_OPERATORS, {kind: "OPERATORS"});
  assert.doesNotMatch(JSON.stringify(spec), /SIMULATOR|RECEIVER|seed-sync|dealership-demo-backend|postgres@/);
  assert.deepEqual(spec.runtimeEnvironment.find(item => item.name === "MANAGED_INBOX_CALLBACK_SIGNING_SECRET").value,
    {kind: "TARGET_SECRET", reference: "conversation-handover-callback-signing"});
  assert.deepEqual(spec.runtimeEnvironment.find(item => item.name === "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET").value,
    {kind: "SERVICE_SECRET", reference: "callback", serviceSlot: "inbox"});
});

test("new AI-only fixture is immutable and separate from historical business graph", () => {
  const fixture = JSON.parse(readFileSync(new URL(
    "../../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json",
    import.meta.url), "utf8"));
  const expected = structuredClone(fixture);
  expected.services[0].sourceArtifactId = "inbox-artifact";
  assert.deepEqual(buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true}), expected);
  const old = JSON.parse(readFileSync(new URL(
    "../../../Platfrom/backend/src/test/resources/managed-dependency/simulated-dealership-experience-v1-6.template.json",
    import.meta.url), "utf8"));
  assert.equal(old.services.some(service => service.slot === "receiver" && service.database), true);
  assert.throws(() => validateManagedDependencySpec(old));
  expected.services[0].volumes[0].capacity.diskMiB = 1;
  assert.equal(buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true}).services[0].volumes[0].capacity.diskMiB, 4096);
});

test("complete dependency capacity is counted only for explicit Inbox selection", () => {
  assert.deepEqual(reviewedManagedDependencyCapacity(), {milliCpu: 0, memoryMiB: 0, diskMiB: 0, resources: 0});
  const spec = buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true});
  const capacities = spec.services.flatMap(service => [service.capacity, ...service.volumes.map(volume => volume.capacity)]);
  const sum = capacities.reduce((total, value) => Object.fromEntries(Object.keys(total)
    .map(key => [key, total[key] + value[key]])), {milliCpu: 0, memoryMiB: 0, diskMiB: 0, resources: 0});
  assert.deepEqual(reviewedManagedDependencyCapacity({managedInbox: true}), sum);
});

test("business systems and fixture artifacts are rejected, not ignored", () => {
  for (const slot of ["simulator", "receiver", "inventory", "crm"]) {
    assert.throws(() => buildManagedDependencySpec({inbox: artifact(), [slot]: artifact()}, {managedInbox: true}), /cannot be customer managed/);
  }
  assert.throws(() => buildManagedDependencySpec({inbox: artifact()}, {managedInbox: false}), /cannot include managed artifacts/);
});

test("artifact registration declares dedicated ownership schema without a fake framework version", () => {
  const registration = inboxArtifactRegistration();
  assert.deepEqual(registration.capabilityManifest, INBOX_CAPABILITY_MANIFEST);
  assert.equal(INBOX_CAPABILITY_MANIFEST_HASH, "534d1b349fea12deec5374ce8114f3b5a2c47b1ba1791fe581db16d1bf0be403");
  assert.equal(registration.capabilityManifest.aiFabricVersion, undefined);
  assert.deepEqual(registration.capabilityManifest.managedDependencyContract, {
    schemaVersion: "loomai-managed-dependency-contract/v1", purpose: "AI_OPERATIONAL_SERVICE",
    lifecycleOwnership: "DEPLOYMENT_OWNED", persistence: "AI_OPERATIONAL_STATE",
    initializationContract: "inbox-callback-canary/v1", serviceContract: "BASIC_INBOX_KEYS_V1",
  });
  registration.capabilityManifest.managedDependencyContract.purpose = "CUSTOMER_BUSINESS_SYSTEM";
  assert.equal(inboxArtifactRegistration().capabilityManifest.managedDependencyContract.purpose, "AI_OPERATIONAL_SERVICE");
});

for (const [field, value] of Object.entries({
  imageDigest: `sha256:${"0".repeat(64)}`, gitCommitSha: "0".repeat(40), capabilityManifestHash: "0".repeat(64),
  artifactRole: "RUNTIME", artifactType: "GIT_SOURCE", promotedAt: null, promotionChannel: null,
})) {
  test(`exact provenance rejects ${field} drift`, () => {
    assert.throws(() => buildManagedDependencySpec({inbox: {...artifact(), [field]: value}}, {managedInbox: true}), /exact promoted/);
  });
}

for (const [field, value] of Object.entries({purpose: "CUSTOMER_BUSINESS_SYSTEM", lifecycleOwnership: "EXTERNAL_RETAINED",
  persistence: "CUSTOMER_BUSINESS_DATA", serviceContract: "HTTP_SERVICE", initializationContract: "scoped-seed-sync-canary/v1"})) {
  test(`ownership contract rejects ${field} drift even with copied expected hash`, () => {
    const input = artifact();
    input.capabilityManifest.managedDependencyContract[field] = value;
    assert.throws(() => buildManagedDependencySpec({inbox: input}, {managedInbox: true}), /ownership contract/);
  });
}

test("strict spec validation rejects hidden business effects and callback authority drift", () => {
  for (const mutate of [
    spec => spec.services.push({...spec.services[0], slot: "receiver"}),
    spec => { spec.services[0].database = {name: "business"}; },
    spec => { spec.services[0].initializationContract = "scoped-seed-sync-canary/v1"; },
    spec => { spec.runtimeEnvironment[0].name = "MANAGED_RECEIVER_API_KEY"; },
    spec => { spec.runtimeEnvironment.find(item => item.name === "MANAGED_INBOX_CALLBACK_SIGNING_SECRET").value.kind = "SERVICE_SECRET"; },
  ]) {
    const spec = buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true});
    mutate(spec);
    assert.throws(() => validateManagedDependencySpec(spec));
  }
});

test("external-only package selection makes no platform requests", async () => {
  const result = await publishSimulatedDealershipPackage({managedInbox: false,
    request: () => { throw new Error("unexpected request"); }});
  assert.equal(result.specId, null);
  assert.equal(result.templateVersionId, EXTERNAL_TEMPLATE_VERSION_ID);
});

test("publisher pins exact promoted artifact and spec, never registers or promotes implicitly", async () => {
  const calls = [];
  let current = null;
  const result = await publishSimulatedDealershipPackage({managedInbox: true, reviewReference: "synthetic-review",
    request: async (path, options = {}) => {
      calls.push({path, options});
      if (path.includes("source-artifacts?")) return [artifact()];
      if (options.method === "POST") {
        current = {spec: options.body.spec, publication: {specId: SPEC_ID, fingerprint: "synthetic-fingerprint", reviewReference: "synthetic-review"}};
        return current;
      }
      return current;
    }});
  assert.equal(result.publicationCreated, true);
  assert.equal(result.providerHealthProven, false);
  assert.equal(result.artifact.capabilityManifestHash, INBOX_CAPABILITY_MANIFEST_HASH);
  assert.deepEqual(calls.filter(call => call.options.method === "POST").map(call => call.path),
    [`/api/customer-self-service/staff/templates/${TEMPLATE_VERSION_ID}/managed-dependency-spec`]);
  assert.doesNotMatch(JSON.stringify(result), /synthetic-raw-secret/);
});

test("missing, ambiguous, unpromoted or ownership-less artifacts fail before publication", async () => {
  for (const artifacts of [[], [artifact(), artifact()], [{...artifact(), promotedAt: null}],
    [{...artifact(), capabilityManifestHash: null}], [{...artifact(), capabilityManifest: null}]]) {
    const writes = [];
    await assert.rejects(publishSimulatedDealershipPackage({managedInbox: true, reviewReference: "synthetic-review",
      request: async (_path, options = {}) => {
        if (options.method === "POST") writes.push(options);
        return artifacts;
      }}));
    assert.deepEqual(writes, []);
  }
});

test("exact publication replay reuses immutable receipt without a second write", async () => {
  const spec = buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true});
  let writes = 0;
  const result = await publishSimulatedDealershipPackage({managedInbox: true, reviewReference: "synthetic-review",
    request: async (path, options = {}) => {
      if (options.method === "POST") writes++;
      return path.includes("source-artifacts?") ? [artifact()] : {
        spec, publication: {specId: SPEC_ID, fingerprint: "stable-fingerprint", reviewReference: "original-review"},
      };
    }});
  assert.equal(writes, 0);
  assert.equal(result.publicationCreated, false);
  assert.equal(result.specFingerprint, "stable-fingerprint");
  assert.equal(result.reviewReference, "original-review");
});

test("immutable spec drift is rejected without replacement; optional null fields compare consistently", async () => {
  const spec = buildManagedDependencySpec({inbox: artifact()}, {managedInbox: true});
  spec.services[0].port = 9999;
  await assert.rejects(publishSimulatedDealershipPackage({managedInbox: true, reviewReference: "synthetic-review",
    request: async (path, options = {}) => {
      assert.notEqual(options.method, "POST");
      return path.includes("source-artifacts?") ? [artifact()] : {spec};
    }}), /immutable spec cannot be replaced/);
  assert.deepEqual(comparableJson({a: null, b: [{c: null, d: false}]}), {b: [{d: false}]});
});
