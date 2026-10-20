import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

import {buildManagedDependencySpec, requireReviewedInboxArtifact, SPEC_ID as OLD_SPEC_ID,
  TEMPLATE_VERSION_ID as OLD_TEMPLATE_VERSION_ID} from "./simulated-dealership-managed-spec.mjs";
import {comparableJson} from "./publish-simulated-dealership-package.mjs";
import {hash, requireIdentifier} from "./simulated-dealership-offer.mjs";

export const INBOX_POSTGRES_IMAGE = "postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea";
export const INBOX_POSTGRES_LINEAGE = Object.freeze({
  source: "verification-support/productization-01036/run-local-budget-restore.mjs",
  javaSource: "verification-support/productization-01036/java/com/ai/fabric/runtime/budget/RuntimeBudgetExportRestoreTest.java",
  qualification: "LOCAL_BUDGET_REHEARSAL_PIN_NOT_HOSTED_INBOX_POSTGRES_APPROVAL",
});

export function requireConcreteIdentifier(value, max = 128) {
  requireIdentifier(value, max);
  if (/^(PENDING|__)/i.test(value) || /pending/i.test(value)) throw new Error("CONCRETE_REVIEWED_IDENTIFIER_REQUIRED");
  return value;
}

export function requirePostgresPinLineage() {
  for (const path of ["./run-local-budget-restore.mjs", "./java/com/ai/fabric/runtime/budget/RuntimeBudgetExportRestoreTest.java"]) {
    assert.ok(readFileSync(new URL(path, import.meta.url), "utf8").includes(INBOX_POSTGRES_IMAGE), "POSTGRES_SOURCE_PIN_DRIFT");
  }
  return INBOX_POSTGRES_IMAGE;
}

export function requireProductionInboxArtifact(value) {
  const artifact = requireReviewedInboxArtifact(value);
  requireConcreteIdentifier(artifact.id);
  assert.match(artifact.id, /^dsa-[a-f0-9]{8}$/, "NATIVE_INBOX_ARTIFACT_ID_REQUIRED");
  assert.equal(artifact.promotionChannel, "production", "PRODUCTION_INBOX_ARTIFACT_REQUIRED");
  assert.ok(Number.isFinite(Date.parse(artifact.promotedAt)), "PROMOTED_INBOX_METADATA_REQUIRED");
  return artifact;
}

/** Only BASIC_INBOX_KEYS_V1 AI operational persistence; no business-service slots. */
export function buildPostgresInboxSpec(artifacts) {
  assert.deepEqual(Object.keys(artifacts ?? {}), ["inbox"], "EXACTLY_ONE_AI_INBOX_ARTIFACT_REQUIRED");
  const inbox = requireProductionInboxArtifact(artifacts.inbox);
  requirePostgresPinLineage();
  const spec = buildManagedDependencySpec({inbox}, {managedInbox: true});
  const service = spec.services[0];
  service.secretPurposes = [...service.secretPurposes, "inbox-db"].sort();
  service.database = {image: INBOX_POSTGRES_IMAGE, passwordPurpose: "inbox-db",
    capacity: {milliCpu: 250, memoryMiB: 512, diskMiB: 4096, resources: 1}};
  service.volumes = [];
  service.environment.BASIC_INBOX_JDBC_URL = {kind: "DATABASE_JDBC"};
  service.environment.BASIC_INBOX_JDBC_USERNAME = {kind: "DATABASE_USERNAME"};
  service.environment.BASIC_INBOX_JDBC_PASSWORD = {kind: "SECRET", reference: "inbox-db"};
  return spec;
}

export function validatePostgresInboxSpec(spec, inboxArtifact) {
  if (hash(comparableJson(spec)) !== hash(comparableJson(buildPostgresInboxSpec({inbox: inboxArtifact}))))
    throw new Error("EXACT_AI_ONLY_POSTGRES_INBOX_SPEC_REQUIRED");
  return spec;
}

export function postgresInboxCapacity() {
  return {milliCpu: 500, memoryMiB: 1024, diskMiB: 4096, resources: 2, databases: 1};
}

export function requireNewInboxIdentifiers({templateVersionId, specId}) {
  requireConcreteIdentifier(templateVersionId, 64); requireConcreteIdentifier(specId, 64);
  assert.notEqual(templateVersionId, OLD_TEMPLATE_VERSION_ID, "OLD_TEMPLATE_VERSION_IS_IMMUTABLE");
  assert.notEqual(specId, OLD_SPEC_ID, "OLD_SPEC_ID_IS_IMMUTABLE");
}

export function requirePostgresInboxPublication(details, {templateVersionId, specId, inboxArtifact}) {
  requireNewInboxIdentifiers({templateVersionId, specId});
  validatePostgresInboxSpec(details?.spec, inboxArtifact);
  const publication = details?.publication;
  assert.equal(publication?.templateVersionId, templateVersionId, "SPEC_TEMPLATE_IDENTITY_CHANGED");
  assert.equal(publication?.specId, specId, "SPEC_IDENTITY_CHANGED");
  assert.match(publication?.fingerprint ?? "", /^[a-f0-9]{64}$/, "NATIVE_SPEC_FINGERPRINT_REQUIRED");
  assert.ok(typeof publication.reviewReference === "string" && publication.reviewReference.trim()
    && Number.isFinite(Date.parse(publication.reviewedAt)), "NATIVE_SPEC_REVIEW_REQUIRED");
  // The backend fingerprint is read back, not fabricated from a different local JSON serialization.
  return {specId, templateVersionId, fingerprint: publication.fingerprint, specBodySha256: hash(comparableJson(details.spec))};
}
