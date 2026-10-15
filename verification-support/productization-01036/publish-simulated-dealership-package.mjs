#!/usr/bin/env node

import assert from "node:assert/strict";
import {mkdir, writeFile} from "node:fs/promises";
import {dirname, resolve} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";

import {
  buildManagedDependencySpec,
  DEPENDENCY_IMAGES,
  SPEC_ID,
  TEMPLATE_VERSION_ID,
} from "./simulated-dealership-managed-spec.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const REQUIRED_RUNTIME_BINDINGS = new Set([
  "MANAGED_SIMULATOR_BASE_URL",
  "MANAGED_SIMULATOR_HOST",
  "MANAGED_SIMULATOR_ACCOUNT_ID",
  "MANAGED_SIMULATOR_PROVIDER_KEY",
  "MANAGED_SIMULATOR_PROVIDER_SECRET",
  "MANAGED_SIMULATOR_NOTIFICATION_SECRET",
  "MANAGED_RECEIVER_ACTION_URL",
  "MANAGED_RECEIVER_API_KEY",
  "MANAGED_INBOX_BASE_URL",
  "MANAGED_INBOX_BINDING_ID",
  "MANAGED_INBOX_CONSOLE_URL",
  "MANAGED_INBOX_API_KEY",
  "MANAGED_INBOX_CALLBACK_SIGNING_SECRET",
  "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET",
]);

export function validateManagedDependencySpec(spec) {
  assert.equal(spec.schemaVersion, "managed-dependencies/v1");
  assert.deepEqual(spec.services.map(({slot}) => slot), ["simulator", "inbox", "receiver"]);
  assert.equal(spec.services.every(({image}) => /@sha256:[a-f0-9]{64}$/.test(image)), true);
  assert.equal(spec.services.every(({sourceCommit}) => /^[a-f0-9]{40}$/.test(sourceCommit)), true);
  assert.deepEqual(new Set(spec.runtimeEnvironment.map(({name}) => name)), REQUIRED_RUNTIME_BINDINGS);
  assert.equal(spec.runtimeEnvironment.every(({target}) => target === "CONNECTOR"), true);

  const callbackAuthority = spec.runtimeEnvironment.find(
    ({name}) => name === "MANAGED_INBOX_CALLBACK_SIGNING_SECRET",
  ).value;
  const providerWebhook = spec.runtimeEnvironment.find(
    ({name}) => name === "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET",
  ).value;
  assert.equal(callbackAuthority.kind, "TARGET_SECRET");
  assert.equal(callbackAuthority.reference, "conversation-handover-callback-signing");
  assert.equal(providerWebhook.kind, "SERVICE_SECRET");
  assert.equal(providerWebhook.serviceSlot, "inbox");
  assert.notEqual(providerWebhook.reference, callbackAuthority.reference);
  const inboxBinding = spec.runtimeEnvironment.find(
    ({name}) => name === "MANAGED_INBOX_BINDING_ID",
  ).value;
  assert.equal(inboxBinding.kind, "BINDING_ID");
  assert.equal(inboxBinding.serviceSlot, "inbox");
  return spec;
}

function normalizedBaseUrl(value) {
  const url = new URL(value);
  if (url.protocol !== "https:") throw new Error("PLATFORM_BASE_URL must use HTTPS.");
  return url.toString().replace(/\/$/, "");
}

function requiredEnvironment(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

async function platformRequest(baseUrl, apiKey, path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers: {
      Accept: "application/json",
      "X-PLATFORM-API-KEY": apiKey,
      ...(options.body ? {"Content-Type": "application/json"} : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: AbortSignal.timeout(30_000),
  });
  if (options.allowNotFound && response.status === 404) return null;
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = {message: "Non-JSON response"};
    }
  }
  if (!response.ok) {
    const message = typeof payload?.message === "string" ? payload.message : `HTTP ${response.status}`;
    throw new Error(`${options.operation ?? path} failed: ${message}`);
  }
  return payload;
}

function exactArtifact(candidate, expected) {
  return candidate.artifactRole === "MANAGED_DEPENDENCY"
    && candidate.artifactType === "DOCKER_IMAGE"
    && candidate.imageRepository === expected.repository
    && candidate.imageDigest === expected.digest
    && candidate.gitCommitSha === expected.commit;
}

async function ensureArtifact(baseUrl, apiKey, expected) {
  const query = new URLSearchParams({serviceName: expected.serviceName});
  const artifacts = await platformRequest(
    baseUrl,
    apiKey,
    `/api/deployment-provider/source-artifacts?${query}`,
    {operation: `list ${expected.serviceName} artifacts`},
  );
  let artifact = artifacts.find((candidate) => exactArtifact(candidate, expected));
  if (!artifact) {
    artifact = await platformRequest(baseUrl, apiKey, "/api/deployment-provider/source-artifacts", {
      method: "POST",
      operation: `register ${expected.serviceName} artifact`,
      body: {
        serviceName: expected.serviceName,
        artifactType: "DOCKER_IMAGE",
        artifactRole: "MANAGED_DEPENDENCY",
        imageRepository: expected.repository,
        imageTag: expected.commit.slice(0, 12),
        imageDigest: expected.digest,
        gitCommitSha: expected.commit,
        buildRunId: null,
        sbomRef: null,
        promotionChannel: null,
        capabilityManifest: null,
      },
    });
  }
  if (artifact.promotionChannel !== "production" || !artifact.promotedAt) {
    artifact = await platformRequest(
      baseUrl,
      apiKey,
      `/api/deployment-provider/source-artifacts/${encodeURIComponent(artifact.id)}/promote`,
      {
        method: "POST",
        operation: `promote ${expected.serviceName} artifact`,
        body: {promotionChannel: "production"},
      },
    );
  }
  if (!exactArtifact(artifact, expected) || artifact.promotionChannel !== "production") {
    throw new Error(`${expected.serviceName} artifact readback does not match the reviewed provenance.`);
  }
  return artifact;
}

function artifactInput(artifact) {
  return {
    id: artifact.id,
    image: `${artifact.imageRepository}@${artifact.imageDigest}`,
    commit: artifact.gitCommitSha,
  };
}

export async function publishSimulatedDealershipPackage({baseUrl, apiKey, reviewReference, output}) {
  const reviewed = {};
  for (const [slot, expected] of Object.entries(DEPENDENCY_IMAGES)) {
    reviewed[slot] = await ensureArtifact(baseUrl, apiKey, expected);
  }
  const spec = validateManagedDependencySpec(buildManagedDependencySpec({
    simulator: artifactInput(reviewed.simulator),
    receiver: artifactInput(reviewed.receiver),
    inbox: artifactInput(reviewed.inbox),
  }));

  const path = `/api/customer-self-service/staff/templates/${encodeURIComponent(TEMPLATE_VERSION_ID)}/managed-dependency-spec`;
  let current = await platformRequest(baseUrl, apiKey, path, {
    allowNotFound: true,
    operation: "read managed dependency specification",
  });
  let created = false;
  if (!current) {
    await platformRequest(baseUrl, apiKey, path, {
      method: "POST",
      operation: "publish managed dependency specification",
      body: {specId: SPEC_ID, spec, reviewReference},
    });
    current = await platformRequest(baseUrl, apiKey, path, {
      operation: "read back managed dependency specification",
    });
    created = true;
  }
  assert.deepEqual(current.spec, spec, "The active immutable topology differs from the reviewed package.");
  assert.equal(current.publication.specId, SPEC_ID);

  const evidence = {
    schemaVersion: "loomai-simulated-dealership-package-publication-evidence-v1",
    checkedAt: new Date().toISOString(),
    platformBaseUrl: baseUrl,
    templateVersionId: TEMPLATE_VERSION_ID,
    specId: current.publication.specId,
    specFingerprint: current.publication.fingerprint,
    reviewReference: current.publication.reviewReference,
    publicationCreated: created,
    artifactPromotionChannel: "production",
    artifacts: Object.fromEntries(Object.entries(reviewed).map(([slot, artifact]) => [slot, {
      id: artifact.id,
      imageRepository: artifact.imageRepository,
      imageDigest: artifact.imageDigest,
      gitCommitSha: artifact.gitCommitSha,
      artifactRole: artifact.artifactRole,
      promotionChannel: artifact.promotionChannel,
      promotedAt: artifact.promotedAt,
    }])),
    runtimeBindingNames: [...REQUIRED_RUNTIME_BINDINGS].sort(),
    secretsRecorded: false,
  };
  await mkdir(dirname(output), {recursive: true});
  await writeFile(output, `${JSON.stringify(evidence, null, 2)}\n`, {mode: 0o644});
  return evidence;
}

async function main() {
  const baseUrl = normalizedBaseUrl(requiredEnvironment("PLATFORM_BASE_URL"));
  const apiKey = requiredEnvironment("PLATFORM_ADMIN_API_KEY");
  const reviewReference = requiredEnvironment("MANAGED_DEPENDENCY_REVIEW_REFERENCE");
  const timestamp = new Date().toISOString().replaceAll(":", "-").replace(".", "-");
  const output = resolve(process.env.EVIDENCE_OUTPUT?.trim()
    || `${HERE}/evidence/managed-package-publication-${timestamp}.json`);
  const evidence = await publishSimulatedDealershipPackage({baseUrl, apiKey, reviewReference, output});
  process.stdout.write(`${JSON.stringify({
    status: "PASS",
    templateVersionId: evidence.templateVersionId,
    specFingerprint: evidence.specFingerprint,
    publicationCreated: evidence.publicationCreated,
    output,
  })}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
