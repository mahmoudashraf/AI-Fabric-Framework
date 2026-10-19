#!/usr/bin/env node

import {lstat, open, readFile} from "node:fs/promises";
import {resolve} from "node:path";
import {pathToFileURL} from "node:url";
import {buildOfferDefinition, CUSTOMER_PLUGIN_REFS, hash, HOSTED_READINESS_GATES, MANAGED_INBOX_TEMPLATE_PLUGIN_ID,
  normalizeOfferDefinition, requireAttendedOfferReview, requireCustomerOfferPublicationSupport, requireDigest,
  requireIdentifier, requireKeys, RUNTIME_IMAGE_DIGEST, RUNTIME_SOURCE_COMMIT, TEMPLATE_VERSION, workspaceProfile,
} from "./simulated-dealership-offer.mjs";
import {DEPENDENCY_IMAGES, TEMPLATE_VERSION_ID as MANAGED_INBOX_TEMPLATE_VERSION_ID} from "./simulated-dealership-managed-spec.mjs";

const PREPARATION_SCHEMA = "loomai-attended-offer-preparation/v1";
const RECEIPT_SCHEMA = "loomai-attended-offer-publication/v1";
const CONFIG_FIELDS = ["managedInbox", "aiStateStorageMode", "aiStateStorageModes", "auth", "externalIntegrationAllowedHosts"];
const PREPARATION_FIELDS = ["schemaVersion", "platformBaseUrl", "review", "configuration", "request", "offerFingerprint",
  "catalogFingerprints", "inboxPublication", "preparationSha256"];
const fail = code => { throw new Error(code); };
const check = (condition, code) => { if (!condition) fail(code); };
const same = (left, right, code) => check(hash(left) === hash(right), code);

function origin(baseUrl) {
  let url;
  try { url = new URL(baseUrl); } catch { fail("HTTPS_PLATFORM_ORIGIN_REQUIRED"); }
  check(url.protocol === "https:" && !url.username && !url.password && !url.search && !url.hash
    && url.pathname === "/", "HTTPS_PLATFORM_ORIGIN_REQUIRED");
  return url.origin;
}

function configuration(value) {
  requireKeys(value, CONFIG_FIELDS, "ATTENDED_OFFER_CONFIGURATION_INVALID");
  requireCustomerOfferPublicationSupport(value);
  check(value.aiStateStorageMode === "MANAGED_POSTGRES", "EXPLICIT_MANAGED_AI_STATE_CANARY_REQUIRED");
  requireKeys(value.auth, ["projectUrl", "publishableKey", "dealerId", "imageHostAllowlist"], "WORKSPACE_REVIEW_INVALID");
  let authUrl;
  try { authUrl = new URL(value.auth.projectUrl); } catch { fail("WORKSPACE_REVIEW_INVALID"); }
  check(authUrl.protocol === "https:" && !authUrl.username && !authUrl.password && !authUrl.search && !authUrl.hash
    && authUrl.pathname === "/", "WORKSPACE_REVIEW_INVALID");
  const key = value.auth.publishableKey;
  check(typeof key === "string" && key.trim() && key.length <= 2048 && !/\s/.test(key), "PUBLIC_WORKSPACE_AUTH_KEY_REQUIRED");
  let publicKey = key.startsWith("sb_publishable_") && key.length > "sb_publishable_".length;
  if (!publicKey && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(key)) {
    try { publicKey = JSON.parse(Buffer.from(key.split(".")[1], "base64url").toString("utf8"))?.role === "anon"; }
    catch { /* Invalid JWT metadata cannot enter browser configuration. */ }
  }
  check(publicKey, "PUBLIC_WORKSPACE_AUTH_KEY_REQUIRED");
  const result = structuredClone(value);
  result.auth.projectUrl = authUrl.origin;
  workspaceProfile(result.auth);
  return result;
}

export function platformOfferRequest(baseUrl, {apiKey, sessionCookie, fetchImpl = fetch} = {}) {
  const base = origin(baseUrl);
  check(Boolean(apiKey) !== Boolean(sessionCookie), "EXACTLY_ONE_PLATFORM_AUTHORITY_REQUIRED");
  const credential = apiKey ?? sessionCookie;
  check(typeof credential === "string" && credential.trim() && !/[\r\n]/.test(credential), "PLATFORM_AUTHORITY_INVALID");
  return async (path, {method = "GET", body} = {}) => {
    check(typeof path === "string" && path.startsWith("/api/") && !path.includes("..")
      && !path.includes("#") && !/[\r\n]/.test(path), "PLATFORM_PATH_INVALID");
    let response;
    try {
      response = await fetchImpl(base + path, {method, redirect: "error", signal: AbortSignal.timeout(30_000),
        headers: {Accept: "application/json", ...(apiKey ? {"X-PLATFORM-API-KEY": apiKey} : {Cookie: sessionCookie}),
          ...(body === undefined ? {} : {"Content-Type": "application/json"})},
        ...(body === undefined ? {} : {body: JSON.stringify(body)})});
    } catch { fail("PLATFORM_TRANSPORT_OUTCOME_UNKNOWN"); }
    check(response.ok, `PLATFORM_HTTP_${response.status}`);
    const length = Number(response.headers.get("content-length"));
    check(!length || length <= 4_000_000, "PLATFORM_RESPONSE_TOO_LARGE");
    const chunks = [];
    let bytes = 0;
    const reader = response.body?.getReader();
    check(reader, "PLATFORM_JSON_REQUIRED");
    try {
      for (;;) {
        const {done, value} = await reader.read();
        if (done) break;
        bytes += value.byteLength;
        if (bytes > 4_000_000) {
          await reader.cancel();
          fail("PLATFORM_RESPONSE_TOO_LARGE");
        }
        chunks.push(value);
      }
    } catch (error) {
      fail(error.message === "PLATFORM_RESPONSE_TOO_LARGE" ? error.message : "PLATFORM_RESPONSE_UNREADABLE");
    } finally { reader.releaseLock(); }
    let text;
    try { text = new TextDecoder("utf-8", {fatal: true}).decode(Buffer.concat(chunks)); }
    catch { fail("PLATFORM_JSON_REQUIRED"); }
    try { return JSON.parse(text); } catch { fail("PLATFORM_JSON_REQUIRED"); }
  };
}

async function staff(request) {
  const session = await request("/api/platform/auth/session");
  check(session?.enabled === true && session.authenticated === true && session.role === "PLATFORM_ADMIN"
    && ["API_KEY", "SESSION"].includes(session.authenticationMode), "STAFF_ADMIN_REQUIRED");
}

async function contracts(request, config, review) {
  const template = await request(`/api/marketplace/plugins/${MANAGED_INBOX_TEMPLATE_PLUGIN_ID}/versions/${TEMPLATE_VERSION}`);
  check(template?.id === MANAGED_INBOX_TEMPLATE_VERSION_ID && template.pluginId === MANAGED_INBOX_TEMPLATE_PLUGIN_ID
    && template.version === TEMPLATE_VERSION, "REVIEWED_TEMPLATE_IDENTITY_REQUIRED");
  const pluginVersions = [];
  for (const reference of CUSTOMER_PLUGIN_REFS) {
    const [plugin, version] = reference.split("@");
    pluginVersions.push(await request(`/api/marketplace/plugins/${plugin}/versions/${version}`));
  }
  for (const version of [template, ...pluginVersions]) {
    check(version?.status === "PUBLISHED" && Number.isFinite(Date.parse(version.publishedAt)), "PUBLISHED_PLUGIN_CONTRACT_REQUIRED");
  }
  const targets = await request("/api/deployment-provider/target-profiles");
  check(Array.isArray(targets), "TARGET_CONTRACT_LIST_REQUIRED");
  const selected = ["dtp-coolify-staging-behavior", "dtp-coolify-production-behavior"].map(id => {
    const matches = targets.filter(target => target?.id === id);
    check(matches.length === 1, "TARGET_IDENTITY_MISSING_OR_AMBIGUOUS");
    return matches[0];
  });
  const runtimeArtifacts = await request("/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime");
  check(Array.isArray(runtimeArtifacts), "ARTIFACT_CONTRACT_LIST_REQUIRED");
  const matchingRuntime = runtimeArtifacts.filter(value => value?.imageDigest === RUNTIME_IMAGE_DIGEST
    && value.gitCommitSha === RUNTIME_SOURCE_COMMIT && value.artifactRole === "RUNTIME");
  check(matchingRuntime.length === 1, "RUNTIME_IDENTITY_MISSING_OR_AMBIGUOUS");
  const artifact = matchingRuntime[0];
  requireIdentifier(artifact.id);
  check(artifact.serviceName === "ai-fabric-runtime" && typeof artifact.imageRepository === "string"
    && artifact.imageRepository.trim() && Number.isFinite(Date.parse(artifact.promotedAt)), "RUNTIME_PROVENANCE_REQUIRED");
  requireDigest(artifact.capabilityManifestHash);
  const inboxArtifacts = await request(`/api/deployment-provider/source-artifacts?serviceName=${DEPENDENCY_IMAGES.inbox.serviceName}`);
  check(Array.isArray(inboxArtifacts), "ARTIFACT_CONTRACT_LIST_REQUIRED");
  const matchingInbox = inboxArtifacts.filter(value => value?.imageRepository === DEPENDENCY_IMAGES.inbox.repository
    && value.imageDigest === DEPENDENCY_IMAGES.inbox.digest && value.gitCommitSha === DEPENDENCY_IMAGES.inbox.commit);
  check(matchingInbox.length === 1, "INBOX_IDENTITY_MISSING_OR_AMBIGUOUS");
  const inboxArtifact = matchingInbox[0];
  requireIdentifier(inboxArtifact.id);
  const inboxSpec = await request(`/api/customer-self-service/staff/templates/${template.id}/managed-dependency-spec`);
  const behaviorCatalog = await request("/api/deployment-behaviors");
  const executionExtensionCatalog = await request("/api/deployment-execution-extensions");
  const definition = buildOfferDefinition({...config, template, pluginVersions, artifact, targets: selected,
    inboxArtifact, inboxSpec, behaviorCatalog, executionExtensionCatalog}, review);
  return {definition, catalogFingerprints: {behavior: hash(behaviorCatalog), executionExtensions: hash(executionExtensionCatalog)},
    inboxPublication: {specId: inboxSpec.publication.specId, templateVersionId: inboxSpec.publication.templateVersionId,
      fingerprint: inboxSpec.publication.fingerprint, reviewedAt: inboxSpec.publication.reviewedAt,
      specSha256: hash(inboxSpec.spec)}};
}

export async function prepareSimulatedDealershipOffer({baseUrl, apiKey, review, configuration: config, request} = {}) {
  const platformBaseUrl = origin(baseUrl);
  requireAttendedOfferReview(review);
  config = configuration(config);
  request ??= platformOfferRequest(platformBaseUrl, {apiKey});
  await staff(request);
  const current = await contracts(request, config, review);
  const prepared = {schemaVersion: PREPARATION_SCHEMA, platformBaseUrl, review: structuredClone(review), configuration: config,
    request: {offerId: review.offerId, definition: current.definition, reviewReference: review.reviewReference},
    offerFingerprint: hash(current.definition), catalogFingerprints: current.catalogFingerprints,
    inboxPublication: current.inboxPublication};
  return {...prepared, preparationSha256: hash(prepared)};
}

function preparation(value, reviewedSha256, baseUrl) {
  requireDigest(reviewedSha256);
  requireKeys(value, PREPARATION_FIELDS, "OFFER_PREPARATION_INVALID");
  const {preparationSha256, ...payload} = value;
  check(preparationSha256 === reviewedSha256 && hash(payload) === reviewedSha256, "OFFER_PREPARATION_REVIEW_MISMATCH");
  check(value.schemaVersion === PREPARATION_SCHEMA && value.platformBaseUrl === origin(baseUrl), "OFFER_PREPARATION_SCOPE_MISMATCH");
  requireAttendedOfferReview(value.review);
  configuration(value.configuration);
  requireKeys(value.request, ["offerId", "definition", "reviewReference"], "OFFER_PREPARATION_INVALID");
  check(value.request.offerId === value.review.offerId && value.request.reviewReference === value.review.reviewReference,
    "OFFER_PREPARATION_SCOPE_MISMATCH");
  same(value.request.definition, normalizeOfferDefinition(value.request.definition), "OFFER_NORMALIZED_CONTRACT_REQUIRED");
  check(hash(value.request.definition) === value.offerFingerprint, "OFFER_FINGERPRINT_MISMATCH");
  return structuredClone(value);
}

/** Publication metadata only. No invite, entitlement, policy, apply or resource is created. */
export async function publishSimulatedDealershipOffer({baseUrl, apiKey, prepared, reviewedPreparationSha256,
  request, checkpoint, now = () => new Date()} = {}) {
  prepared = preparation(prepared, reviewedPreparationSha256, baseUrl);
  check(typeof checkpoint === "function", "DURABLE_PUBLICATION_CHECKPOINT_REQUIRED");
  request ??= platformOfferRequest(baseUrl, {apiKey});
  await staff(request);
  const current = await contracts(request, prepared.configuration, prepared.review);
  same(current.definition, prepared.request.definition, "REVIEWED_SERVER_CONTRACT_CHANGED");
  same(current.catalogFingerprints, prepared.catalogFingerprints, "REVIEWED_SERVER_CATALOG_CHANGED");
  same(current.inboxPublication, prepared.inboxPublication, "REVIEWED_INBOX_PUBLICATION_CHANGED");
  const placements = [];
  for (const permission of prepared.request.definition.targets) {
    const value = await request(`/api/customer-self-service/staff/targets/${permission.targetProfileId}/placement-evidence`,
      {method: "POST", body: {intent: permission.intent}});
    requireIdentifier(value?.id);
    check(value.targetProfileId === permission.targetProfileId && value.intent === permission.intent
      && value.profileFingerprint === permission.profileFingerprint && Number.isFinite(Date.parse(value.checkedAt))
      && Date.parse(value.checkedAt) <= now().getTime() && Date.parse(value.expiresAt) > now().getTime(), "PLACEMENT_EVIDENCE_INVALID");
    placements.push({id: value.id, ...permission, checkedAt: value.checkedAt, expiresAt: value.expiresAt});
  }
  // Recheck the committed definitions after probes. The publication API also
  // revalidates pins and fresh placement in its own transaction.
  const final = await contracts(request, prepared.configuration, prepared.review);
  same(final, current, "REVIEWED_SERVER_CONTRACT_CHANGED");
  const receipt = {schemaVersion: RECEIPT_SCHEMA, platformBaseUrl: prepared.platformBaseUrl,
    offerId: prepared.request.offerId, offerFingerprint: prepared.offerFingerprint,
    preparationSha256: prepared.preparationSha256, state: "PUBLICATION_ATTEMPT_STARTED",
    placementEvidence: placements, immutableReadbackVerified: false, liveReadinessProven: false,
    remainingGates: ["CUSTOMER_IMMUTABLE_OFFER_READBACK_REQUIRED", ...HOSTED_READINESS_GATES],
    customerBusinessSystemsProvisioned: false, secretsRecorded: false};
  await checkpoint(structuredClone(receipt));
  try {
    const result = await request("/api/customer-self-service/staff/offers", {method: "POST", body: prepared.request});
    requireKeys(result, ["offerId", "fingerprint"], "OFFER_PUBLICATION_RECEIPT_INVALID");
    check(result.offerId === receipt.offerId && result.fingerprint === receipt.offerFingerprint, "OFFER_PUBLICATION_RECEIPT_MISMATCH");
  } catch {
    receipt.state = "PUBLICATION_OUTCOME_UNKNOWN";
    await checkpoint(structuredClone(receipt));
    fail("PUBLICATION_OUTCOME_UNKNOWN: do not retry the POST or change the offer ID; reconcile through authorized customer readback.");
  }
  receipt.state = "PUBLISHED_CUSTOMER_READBACK_PENDING";
  await checkpoint(structuredClone(receipt));
  return receipt;
}

/** The existing read API needs a separately invited/accepted CUSTOMER_ADMIN session. */
export async function verifySimulatedDealershipOffer({baseUrl, sessionCookie, prepared, reviewedPreparationSha256,
  customerId, ownerUserId, request, now = () => new Date()} = {}) {
  prepared = preparation(prepared, reviewedPreparationSha256, baseUrl);
  requireIdentifier(customerId, 64); requireIdentifier(ownerUserId, 64);
  request ??= platformOfferRequest(baseUrl, {sessionCookie});
  const session = await request("/api/platform/auth/session");
  check(session?.enabled === true && session.authenticated === true && session.role === "CUSTOMER_ADMIN"
    && session.authenticationMode === "SESSION" && session.customerId === customerId, "CUSTOMER_OWNER_SESSION_REQUIRED");
  const actual = await request(`/api/customer-self-service/catalogue/${prepared.request.offerId}`);
  const expected = prepared.request.definition;
  check(actual?.id === prepared.request.offerId && actual.fingerprint === prepared.offerFingerprint
    && actual.name === expected.name && actual.templateId === expected.templateId
    && actual.templateVersionId === expected.template.versionId, "IMMUTABLE_OFFER_READBACK_MISMATCH");
  const entitlement = actual.entitlement;
  requireIdentifier(entitlement?.id, 64);
  check(entitlement.status === "ACTIVE" && entitlement.offerId === actual.id && entitlement.customerId === customerId
    && entitlement.ownerUserId === ownerUserId && Number.isSafeInteger(entitlement.revision) && entitlement.revision > 0
    && Date.parse(entitlement.expiresAt) > now().getTime(), "ACTIVE_EXACT_CUSTOMER_ENTITLEMENT_REQUIRED");
  for (const field of ["settings", "bindingSlots", "documentAudiences", "workspaceProfiles", "externalIntegrationProfiles"]) {
    same(actual[field], expected[field], "IMMUTABLE_OFFER_READBACK_MISMATCH");
  }
  check(Array.isArray(actual.targets) && actual.targets.length === expected.targets.length
    && new Set(actual.targets.map(target => target?.id)).size === expected.targets.length, "IMMUTABLE_TARGET_READBACK_MISMATCH");
  for (const target of actual.targets) {
    const permission = expected.targets.find(value => value.targetProfileId === target.id);
    check(permission?.intent === target.intent, "IMMUTABLE_TARGET_READBACK_MISMATCH");
    same(target.limits, expected.limits, "IMMUTABLE_TARGET_READBACK_MISMATCH");
  }
  check(Array.isArray(actual.permissions) && new Set(actual.permissions.map(row => row?.operation)).size === expected.operations.length,
    "IMMUTABLE_PERMISSION_READBACK_MISMATCH");
  same(actual.permissions.map(row => row.operation).sort(), [...expected.operations].sort(), "IMMUTABLE_PERMISSION_READBACK_MISMATCH");
  return {schemaVersion: RECEIPT_SCHEMA, platformBaseUrl: prepared.platformBaseUrl, offerId: actual.id,
    offerFingerprint: actual.fingerprint, preparationSha256: prepared.preparationSha256,
    state: "IMMUTABLE_CUSTOMER_READBACK_VERIFIED", immutableReadbackVerified: true, liveReadinessProven: false,
    customerId, entitlementId: entitlement.id, entitlementRevision: entitlement.revision,
    remainingGates: [...HOSTED_READINESS_GATES], customerBusinessSystemsProvisioned: false, secretsRecorded: false};
}

async function readJson(path) {
  const file = resolve(path);
  const stat = await lstat(file);
  check(stat.isFile() && !stat.isSymbolicLink() && stat.size <= 4_000_000, "REGULAR_BOUNDED_JSON_FILE_REQUIRED");
  try { return JSON.parse(await readFile(file, "utf8")); } catch { fail("LOCAL_JSON_INVALID"); }
}

async function main() {
  const args = process.argv.slice(2);
  const mode = args.shift();
  check(["prepare", "publish", "verify"].includes(mode), "MODE_REQUIRED: prepare, publish or verify");
  const allowed = mode === "prepare" ? ["--config", "--output"] : mode === "publish"
    ? ["--prepared", "--reviewed-sha256", "--output"] : ["--prepared", "--reviewed-sha256", "--customer-id", "--owner-user-id", "--output"];
  const flags = {};
  for (let i = 0; i < args.length; i += 2) {
    check(allowed.includes(args[i]) && !Object.hasOwn(flags, args[i]) && args[i + 1] && !args[i + 1].startsWith("--"), "UNKNOWN_OR_INCOMPLETE_ARGUMENT");
    flags[args[i]] = args[i + 1];
  }
  check(allowed.every(key => flags[key]), "ALL_MODE_ARGUMENTS_REQUIRED");
  const baseUrl = origin(process.env.PLATFORM_BASE_URL);
  const output = await open(resolve(flags["--output"]), "wx", 0o600);
  const checkpoint = async value => {
    const bytes = Buffer.from(JSON.stringify(value, null, 2) + "\n");
    await output.truncate(0);
    let offset = 0;
    while (offset < bytes.length) {
      const {bytesWritten} = await output.write(bytes, offset, bytes.length - offset, offset);
      check(bytesWritten > 0, "LOCAL_CHECKPOINT_WRITE_INCOMPLETE");
      offset += bytesWritten;
    }
    await output.sync();
  };
  try {
    let result;
    if (mode === "prepare") {
      const input = await readJson(flags["--config"]);
      requireKeys(input, ["review", "configuration"], "PUBLICATION_CONFIG_INVALID");
      result = await prepareSimulatedDealershipOffer({baseUrl, apiKey: process.env.PLATFORM_ADMIN_API_KEY, ...input});
    } else {
      const prepared = await readJson(flags["--prepared"]);
      const shared = {baseUrl, prepared, reviewedPreparationSha256: flags["--reviewed-sha256"]};
      result = mode === "publish" ? await publishSimulatedDealershipOffer({...shared, apiKey: process.env.PLATFORM_ADMIN_API_KEY, checkpoint})
        : await verifySimulatedDealershipOffer({...shared, sessionCookie: process.env.PLATFORM_CUSTOMER_SESSION_COOKIE,
          customerId: flags["--customer-id"], ownerUserId: flags["--owner-user-id"]});
    }
    await checkpoint(result);
    process.stdout.write(`${JSON.stringify({state: result.state ?? "PREPARED_NOT_PUBLISHED", output: resolve(flags["--output"]),
      preparationSha256: result.preparationSha256, offerFingerprint: result.offerFingerprint})}\n`);
  } finally { await output.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
