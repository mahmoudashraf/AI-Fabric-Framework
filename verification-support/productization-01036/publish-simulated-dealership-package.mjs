#!/usr/bin/env node

import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {mkdir, writeFile} from "node:fs/promises";
import {dirname, resolve} from "node:path";
import {pathToFileURL} from "node:url";

import {buildManagedDependencySpec, DEPENDENCY_IMAGES, EXTERNAL_TEMPLATE_VERSION_ID,
  requireInboxSelection, requireReviewedInboxArtifact, SPEC_ID, TEMPLATE_VERSION_ID,
} from "./simulated-dealership-managed-spec.mjs";

const TEMPLATE = JSON.parse(readFileSync(new URL(
  "../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json",
  import.meta.url), "utf8"));

export function comparableJson(value) {
  if (Array.isArray(value)) return value.map(comparableJson);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value)
    .filter(([, nested]) => nested !== null).map(([key, nested]) => [key, comparableJson(nested)]));
  return value;
}

export function validateManagedDependencySpec(spec) {
  const expected = structuredClone(TEMPLATE);
  const id = spec?.services?.[0]?.sourceArtifactId;
  assert.equal(typeof id, "string");
  assert.match(id, /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/);
  assert.notEqual(id, "__INBOX_SOURCE_ARTIFACT_ID__");
  expected.services[0].sourceArtifactId = id;
  assert.deepEqual(comparableJson(spec), comparableJson(expected), "Only the reviewed AI Inbox topology is allowed.");
  return spec;
}

// Registration and staff promotion are separate reviewed actions, never implicit publisher side effects.
export function inboxArtifactRegistration() {
  const expected = DEPENDENCY_IMAGES.inbox;
  return {
    serviceName: expected.serviceName, artifactType: "DOCKER_IMAGE", artifactRole: "MANAGED_DEPENDENCY",
    imageRepository: expected.repository, imageTag: expected.commit.slice(0, 12),
    imageDigest: expected.digest, gitCommitSha: expected.commit,
    capabilityManifest: structuredClone(expected.capabilityManifest),
  };
}

async function platformRequest(baseUrl, apiKey, path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers: {Accept: "application/json", "X-PLATFORM-API-KEY": apiKey,
      ...(options.body ? {"Content-Type": "application/json"} : {})},
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: AbortSignal.timeout(30_000),
  });
  if (options.allowNotFound && response.status === 404) return null;
  if (!response.ok) throw new Error(`Platform package request failed (HTTP ${response.status}).`);
  return response.json();
}

export async function publishSimulatedDealershipPackage({baseUrl, apiKey, reviewReference, output,
  managedInbox, request = (path, options) => platformRequest(baseUrl, apiKey, path, options)}) {
  requireInboxSelection(managedInbox);
  if (!managedInbox) return {
    templateVersionId: EXTERNAL_TEMPLATE_VERSION_ID, specId: null, publicationCreated: false,
    managedInbox: false, note: "No managed dependency specification; externally operated Inbox still requires binding.",
  };
  if (typeof reviewReference !== "string" || !reviewReference.trim()) throw new Error("A review reference is required.");
  const artifacts = await request(`/api/deployment-provider/source-artifacts?${new URLSearchParams({
    serviceName: DEPENDENCY_IMAGES.inbox.serviceName,
  })}`);
  const matching = artifacts.filter(candidate => candidate.imageRepository === DEPENDENCY_IMAGES.inbox.repository
    && candidate.imageDigest === DEPENDENCY_IMAGES.inbox.digest
    && candidate.gitCommitSha === DEPENDENCY_IMAGES.inbox.commit
    && candidate.capabilityManifestHash === DEPENDENCY_IMAGES.inbox.capabilityManifestHash);
  if (matching.length !== 1) throw new Error("Exactly one reviewed Inbox artifact must be registered and staff-promoted first.");
  const artifact = requireReviewedInboxArtifact(matching[0]);
  const spec = validateManagedDependencySpec(buildManagedDependencySpec({inbox: artifact}, {managedInbox: true}));
  const path = `/api/customer-self-service/staff/templates/${TEMPLATE_VERSION_ID}/managed-dependency-spec`;
  let current = await request(path, {allowNotFound: true});
  const created = !current;
  if (current) {
    assert.deepEqual(comparableJson(current.spec), comparableJson(spec), "An existing immutable spec cannot be replaced.");
  } else {
    await request(path, {method: "POST", body: {specId: SPEC_ID, spec, reviewReference}});
    current = await request(path);
  }
  assert.deepEqual(comparableJson(current.spec), comparableJson(spec));
  assert.equal(current.publication.specId, SPEC_ID);
  const evidence = {
    schemaVersion: "loomai-ai-inbox-package-publication-evidence-v1", checkedAt: new Date().toISOString(),
    templateVersionId: TEMPLATE_VERSION_ID, specId: SPEC_ID, specFingerprint: current.publication.fingerprint,
    reviewReference: current.publication.reviewReference, publicationCreated: created, managedInbox: true,
    artifact: {id: artifact.id, imageDigest: artifact.imageDigest, gitCommitSha: artifact.gitCommitSha,
      capabilityManifestHash: artifact.capabilityManifestHash, promotionChannel: artifact.promotionChannel},
    runtimeBindingNames: spec.runtimeEnvironment.map(binding => binding.name).sort(),
    customerBusinessSystemsProvisioned: false, providerHealthProven: false, secretsRecorded: false,
  };
  if (output) {
    await mkdir(dirname(output), {recursive: true});
    await writeFile(output, `${JSON.stringify(evidence, null, 2)}\n`, {mode: 0o600});
  }
  return evidence;
}

async function main() {
  const selection = process.env.MANAGED_INBOX;
  if (!["true", "false"].includes(selection)) throw new Error("MANAGED_INBOX must explicitly be true or false.");
  const baseUrl = process.env.PLATFORM_BASE_URL?.replace(/\/$/, "");
  if (!baseUrl || new URL(baseUrl).protocol !== "https:") throw new Error("PLATFORM_BASE_URL must use HTTPS.");
  if (!process.env.PLATFORM_ADMIN_API_KEY) throw new Error("PLATFORM_ADMIN_API_KEY is required.");
  const evidence = await publishSimulatedDealershipPackage({baseUrl, apiKey: process.env.PLATFORM_ADMIN_API_KEY,
    reviewReference: process.env.MANAGED_DEPENDENCY_REVIEW_REFERENCE, managedInbox: selection === "true",
    output: process.env.EVIDENCE_OUTPUT ? resolve(process.env.EVIDENCE_OUTPUT) : undefined});
  process.stdout.write(`${JSON.stringify(evidence)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
}
