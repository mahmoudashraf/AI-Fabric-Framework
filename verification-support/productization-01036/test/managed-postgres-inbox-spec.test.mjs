import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {buildPostgresInboxSpec, INBOX_POSTGRES_IMAGE, postgresInboxCapacity, requireNewInboxIdentifiers,
  requirePostgresPinLineage, requirePostgresInboxPublication, validatePostgresInboxSpec} from "../managed-postgres-inbox-spec.mjs";
import {buildManagedDependencySpec} from "../simulated-dealership-managed-spec.mjs";
import {inboxArtifact, packageInput} from "./fixtures/managed-postgres-inbox-publication-fixture.mjs";

test("new PG spec changes only AI Inbox persistence and leaves the immutable H2 builder/template intact", () => {
  const artifact = inboxArtifact(), before = structuredClone(artifact);
  const old = buildManagedDependencySpec({inbox: artifact}, {managedInbox: true});
  const originalBytes = readFileSync(new URL("../../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json", import.meta.url), "utf8");
  const spec = buildPostgresInboxSpec({inbox: artifact});
  assert.deepEqual(spec.services.map(value => value.slot), ["inbox"]);
  assert.equal(spec.services[0].contract, "BASIC_INBOX_KEYS_V1");
  assert.equal(spec.services[0].environment.AI_ENABLED.reference, "false");
  assert.deepEqual(spec.services[0].volumes, []);
  assert.equal(spec.services[0].database.image, INBOX_POSTGRES_IMAGE);
  assert.deepEqual(spec.services[0].environment.BASIC_INBOX_JDBC_URL, {kind: "DATABASE_JDBC"});
  assert.deepEqual(spec.services[0].environment.BASIC_INBOX_JDBC_USERNAME, {kind: "DATABASE_USERNAME"});
  assert.deepEqual(spec.services[0].environment.BASIC_INBOX_JDBC_PASSWORD, {kind: "SECRET", reference: "inbox-db"});
  assert.deepEqual(spec.runtimeEnvironment, old.runtimeEnvironment);
  assert.deepEqual(artifact, before);
  assert.deepEqual(buildManagedDependencySpec({inbox: artifact}, {managedInbox: true}), old);
  assert.equal(old.services[0].database, null);
  assert.equal(readFileSync(new URL("../../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json", import.meta.url), "utf8"), originalBytes);
});

test("PG source pin lineage and exact complete two-resource dependency capacity", () => {
  assert.equal(requirePostgresPinLineage(), INBOX_POSTGRES_IMAGE);
  const service = buildPostgresInboxSpec({inbox: inboxArtifact()}).services[0];
  assert.deepEqual(postgresInboxCapacity(), {milliCpu: service.capacity.milliCpu + service.database.capacity.milliCpu,
    memoryMiB: service.capacity.memoryMiB + service.database.capacity.memoryMiB,
    diskMiB: service.database.capacity.diskMiB, resources: 2, databases: 1});
});

for (const [field, value] of Object.entries({id: "PENDING_REVIEWED_INBOX_ARTIFACT_ID", artifactRole: "RUNTIME", artifactType: "GIT_SOURCE",
  imageDigest: "sha256:" + "0".repeat(64), gitCommitSha: "0".repeat(40), capabilityManifestHash: "0".repeat(64),
  promotionChannel: "staging", promotedAt: "not-a-date"})) {
  test(`unsafe/missing Inbox metadata rejects ${field}`, () => {
    assert.throws(() => buildPostgresInboxSpec({inbox: {...inboxArtifact(), [field]: value}}));
  });
}

test("no artifact registration, guessed shorthand, business service or copied capability hash", () => {
  assert.throws(() => buildPostgresInboxSpec());
  assert.throws(() => buildPostgresInboxSpec({inbox: {...inboxArtifact(), id: "dsa1dc"}}));
  for (const name of ["simulator", "receiver", "crm", "inventory"])
    assert.throws(() => buildPostgresInboxSpec({inbox: inboxArtifact(), [name]: inboxArtifact()}));
  const artifact = inboxArtifact(); artifact.capabilityManifest.managedDependencyContract.persistence = "CUSTOMER_BUSINESS_DATA";
  assert.throws(() => buildPostgresInboxSpec({inbox: artifact}));
});

test("new spec remains strict, independent and compatible with null-omitting normal API readback", () => {
  const artifact = inboxArtifact();
  for (const mutate of [spec => { spec.services[0].database.image = "postgres:16"; },
    spec => { spec.services[0].environment.BASIC_INBOX_JDBC_PASSWORD = {kind: "LITERAL", reference: "forbidden-value"}; },
    spec => { spec.services[0].capacity.memoryMiB = 1; }, spec => { spec.services.push(structuredClone(spec.services[0])); }]) {
    const spec = buildPostgresInboxSpec({inbox: artifact}); mutate(spec);
    assert.throws(() => validatePostgresInboxSpec(spec, artifact));
  }
  const spec = buildPostgresInboxSpec({inbox: artifact});
  spec.services[0].environment.BASIC_INBOX_JDBC_URL.reference = null;
  assert.equal(validatePostgresInboxSpec(spec, artifact), spec);
});

test("publication pins real returned fingerprint, new native version and distinct requested spec ID", () => {
  const input = packageInput("PUBLISHED"), c = input.contracts;
  const args = {templateVersionId: c.publishedTemplate.id, specId: input.selection.specId, inboxArtifact: c.inboxArtifacts[0]};
  assert.equal(requirePostgresInboxPublication(c.inboxPublication, args).fingerprint, "a".repeat(64));
  assert.throws(() => requireNewInboxIdentifiers({...args, templateVersionId: "mkv-template-dealership-external-systems-managed-inbox-v1-0"}));
  assert.throws(() => requireNewInboxIdentifiers({...args, specId: "mds-dealership-ai-managed-inbox-v1-0"}));
  c.inboxPublication.publication.fingerprint = "PENDING_NATIVE_READBACK";
  assert.throws(() => requirePostgresInboxPublication(c.inboxPublication, args));
});
