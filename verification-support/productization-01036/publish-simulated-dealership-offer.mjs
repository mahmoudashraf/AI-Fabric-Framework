#!/usr/bin/env node

import {mkdir, writeFile} from "node:fs/promises";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

import {
  buildOfferDefinition,
  OFFER_ID,
  PRODUCTION_TARGET_ID,
  RUNTIME_IMAGE_DIGEST,
  RUNTIME_SOURCE_COMMIT,
  STAGING_TARGET_ID,
  TEMPLATE_PLUGIN_ID,
  TEMPLATE_VERSION,
  workspaceProfile,
} from "./simulated-dealership-offer.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

async function request(baseUrl, apiKey, path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers: {
      Accept: "application/json",
      "X-PLATFORM-API-KEY": apiKey,
      ...(options.body ? {"Content-Type": "application/json"} : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: AbortSignal.timeout(130_000),
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(`${options.operation ?? path} failed (${response.status}): ${payload?.errorCode ?? payload?.message ?? "unknown"}`);
  return payload;
}

async function main() {
  const baseUrl = required("PLATFORM_BASE_URL").replace(/\/$/, "");
  if (!baseUrl.startsWith("https://")) throw new Error("PLATFORM_BASE_URL must use HTTPS.");
  const apiKey = required("PLATFORM_ADMIN_API_KEY");
  const auth = {
    projectUrl: required("ENGAGE_SUPABASE_PROJECT_URL"),
    publishableKey: required("ENGAGE_SUPABASE_PUBLISHABLE_KEY"),
  };
  const template = await request(baseUrl, apiKey,
    `/api/marketplace/plugins/${TEMPLATE_PLUGIN_ID}/versions/${TEMPLATE_VERSION}`);
  const refs = template.contributions?.templateRequiredPluginRefs ?? [];
  const pluginVersions = await Promise.all(refs.map((reference) => {
    const separator = reference.lastIndexOf("@");
    return request(baseUrl, apiKey,
      `/api/marketplace/plugins/${encodeURIComponent(reference.slice(0, separator))}/versions/${encodeURIComponent(reference.slice(separator + 1))}`);
  }));
  const targets = await request(baseUrl, apiKey, "/api/deployment-provider/target-profiles");
  const artifacts = await request(baseUrl, apiKey, "/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime");
  const behaviorCatalog = await request(baseUrl, apiKey, "/api/deployments/deployment-behaviors");
  const executionExtensionCatalog = await request(baseUrl, apiKey, "/api/deployments/deployment-execution-extensions");
  const artifact = artifacts.find((candidate) => candidate.imageDigest === RUNTIME_IMAGE_DIGEST
    && candidate.gitCommitSha === RUNTIME_SOURCE_COMMIT && candidate.artifactRole === "RUNTIME");
  const selectedTargets = targets.filter(({id}) => id === STAGING_TARGET_ID || id === PRODUCTION_TARGET_ID);
  const definition = buildOfferDefinition({template, pluginVersions, artifact, targets: selectedTargets, auth,
    behaviorCatalog, executionExtensionCatalog});

  const placement = [];
  for (const [targetProfileId, intent] of [
    [STAGING_TARGET_ID, "CUSTOMER_STAGING"], [PRODUCTION_TARGET_ID, "CUSTOMER_PRODUCTION"],
  ]) {
    const evidence = await request(baseUrl, apiKey,
      `/api/customer-self-service/staff/targets/${targetProfileId}/placement-evidence`, {
        method: "POST", body: {intent}, operation: `probe ${intent} placement`,
      });
    const permission = definition.targets.find((target) => target.targetProfileId === targetProfileId);
    if (evidence.targetProfileId !== targetProfileId || evidence.intent !== intent
      || evidence.profileFingerprint !== permission.profileFingerprint) {
      throw new Error(`Placement identity for ${targetProfileId} differs from the reviewed target pin.`);
    }
    placement.push(evidence);
  }

  const reviewReference = required("CUSTOMER_OFFER_REVIEW_REFERENCE");
  const published = await request(baseUrl, apiKey, "/api/customer-self-service/staff/offers", {
    method: "POST",
    body: {offerId: OFFER_ID, definition, reviewReference},
    operation: "publish reviewed customer offer",
  });
  const profiles = workspaceProfile(auth);
  const evidence = {
    schemaVersion: "loomai-customer-offer-publication-evidence-v1",
    checkedAt: new Date().toISOString(),
    platformBaseUrl: baseUrl,
    offerId: published.offerId,
    offerFingerprint: published.fingerprint,
    reviewReference,
    templateVersionId: template.id,
    pluginVersionIds: pluginVersions.map(({id}) => id).sort(),
    runtimeArtifact: {
      id: artifact.id,
      imageDigest: artifact.imageDigest,
      gitCommitSha: artifact.gitCommitSha,
      aiFabricVersion: artifact.capabilityManifest?.aiFabricVersion,
      promotionChannel: artifact.promotionChannel,
    },
    targets: selectedTargets.map(({id, providerType, environmentName, sourceStrategy}) => ({
      id, providerType, environmentName, sourceStrategy,
    })).sort((left, right) => left.id.localeCompare(right.id)),
    placementEvidence: placement.map(({id, targetProfileId, intent, profileFingerprint, checkedAt, expiresAt}) => ({
      id, targetProfileId, intent, profileFingerprint, checkedAt, expiresAt,
    })),
    workspaceProfileIds: {
      interactionProfileId: profiles.interactionProfileId,
      themeProfileId: profiles.themeProfileId,
      authProfileId: profiles.authProfileId,
    },
    secretsRecorded: false,
  };
  const timestamp = new Date().toISOString().replaceAll(":", "-").replace(".", "-");
  const output = resolve(process.env.EVIDENCE_OUTPUT?.trim()
    || `${HERE}/evidence/customer-offer-publication-${timestamp}.json`);
  await mkdir(dirname(output), {recursive: true});
  await writeFile(output, `${JSON.stringify(evidence, null, 2)}\n`);
  process.stdout.write(`${JSON.stringify({status: "PASS", offerId: published.offerId,
    fingerprint: published.fingerprint, output})}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
