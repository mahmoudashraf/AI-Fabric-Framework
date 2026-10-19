import {createHash} from "node:crypto";
import {readFileSync} from "node:fs";

const REVIEWED_TEMPLATE = JSON.parse(readFileSync(new URL(
  "../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json",
  import.meta.url), "utf8"));

export const EXTERNAL_TEMPLATE_VERSION_ID = "mkv-template-dealership-external-systems-v1-0";
export const TEMPLATE_VERSION_ID = "mkv-template-dealership-external-systems-managed-inbox-v1-0";
export const SPEC_ID = "mds-dealership-ai-managed-inbox-v1-0";

// Field order matches the dedicated backend normalizer; no framework identity is invented.
export const INBOX_CAPABILITY_MANIFEST = Object.freeze({
  schemaVersion: "loomai-managed-dependency-artifact/v1",
  managedDependencyContract: Object.freeze({
    schemaVersion: "loomai-managed-dependency-contract/v1",
    purpose: "AI_OPERATIONAL_SERVICE",
    lifecycleOwnership: "DEPLOYMENT_OWNED",
    persistence: "AI_OPERATIONAL_STATE",
    initializationContract: "inbox-callback-canary/v1",
    serviceContract: "BASIC_INBOX_KEYS_V1",
  }),
});
export const INBOX_CAPABILITY_MANIFEST_HASH = createHash("sha256")
  .update(JSON.stringify(INBOX_CAPABILITY_MANIFEST)).digest("hex");

export const DEPENDENCY_IMAGES = Object.freeze({
  inbox: Object.freeze({
    serviceName: "dealership-ai-basic-inbox",
    repository: "ghcr.io/mahmoudashraf/loomai-basic-inbox-service",
    digest: "sha256:d74871880950f09674e0558cdfb09bdadcce050e89ae7b33312dad7069ac3759",
    commit: "2035dc36082fefccbfd3a757d38e493fa8f001fc",
    capabilityManifest: INBOX_CAPABILITY_MANIFEST,
    capabilityManifestHash: INBOX_CAPABILITY_MANIFEST_HASH,
  }),
});

export function requireInboxSelection(managedInbox) {
  if (typeof managedInbox !== "boolean") throw new Error("Explicit managedInbox selection is required.");
}

export function reviewedManagedDependencyCapacity({managedInbox = false} = {}) {
  requireInboxSelection(managedInbox);
  return managedInbox
    ? {milliCpu: 250, memoryMiB: 512, diskMiB: 4096, resources: 1}
    : {milliCpu: 0, memoryMiB: 0, diskMiB: 0, resources: 0};
}

export function requireReviewedInboxArtifact(artifact) {
  const expected = DEPENDENCY_IMAGES.inbox;
  if (!artifact?.id || artifact.artifactType !== "DOCKER_IMAGE" || artifact.artifactRole !== "MANAGED_DEPENDENCY"
    || artifact.imageRepository !== expected.repository || artifact.imageDigest !== expected.digest
    || artifact.gitCommitSha !== expected.commit || !artifact.promotedAt
    || !["staging", "production"].includes(artifact.promotionChannel)
    || artifact.capabilityManifestHash !== expected.capabilityManifestHash) {
    throw new Error("The exact promoted Inbox artifact and reviewed ownership manifest hash are required.");
  }
  const actual = artifact.capabilityManifest;
  const contract = actual?.managedDependencyContract;
  if (!actual || Object.keys(actual).length !== 2 || actual.schemaVersion !== INBOX_CAPABILITY_MANIFEST.schemaVersion
    || !contract || Object.keys(contract).length !== Object.keys(INBOX_CAPABILITY_MANIFEST.managedDependencyContract).length
    || Object.entries(INBOX_CAPABILITY_MANIFEST.managedDependencyContract).some(([key, value]) => contract[key] !== value)) {
    throw new Error("The Inbox artifact ownership contract does not match the reviewed AI-only service.");
  }
  return artifact;
}

export function buildManagedDependencySpec(artifacts = {}, {managedInbox} = {}) {
  requireInboxSelection(managedInbox);
  if (Object.keys(artifacts).some(slot => slot !== "inbox")) {
    throw new Error("Business systems and external fixtures cannot be customer managed dependencies.");
  }
  // Absence of a spec is supported. An empty services spec is not a valid substitute.
  if (!managedInbox) {
    if (Object.keys(artifacts).length) throw new Error("External Inbox selection cannot include managed artifacts.");
    return null;
  }
  const artifact = requireReviewedInboxArtifact(artifacts.inbox);
  const spec = structuredClone(REVIEWED_TEMPLATE);
  spec.services[0].sourceArtifactId = artifact.id;
  return spec;
}
