#!/usr/bin/env node

import {lstat, open, readFile} from "node:fs/promises";
import {readFileSync} from "node:fs";
import {createHash} from "node:crypto";
import {resolve} from "node:path";
import {pathToFileURL} from "node:url";

import {CUSTOMER_PLUGIN_REFS, hash, MANAGED_INBOX_TEMPLATE_PLUGIN_ID, profileFingerprint, requireDigest, requireKeys} from "./simulated-dealership-offer.mjs";
import {DEPENDENCY_IMAGES} from "./simulated-dealership-managed-spec.mjs";
import {buildPostgresInboxSpec, INBOX_POSTGRES_IMAGE, INBOX_POSTGRES_LINEAGE, requireConcreteIdentifier,
  requireNewInboxIdentifiers, requirePostgresInboxPublication, requireProductionInboxArtifact} from "./managed-postgres-inbox-spec.mjs";
import {buildPostgresDealershipOffer, buildPostgresInboxTemplateManifest, check, noCredentialValues,
  POSTGRES_PACKAGE_GATES, requirePublishedVersion, same, text} from "./managed-postgres-inbox-dealership-offer.mjs";

export const PACKAGE_PHASES = Object.freeze(["template-submit", "template-validate", "template-publish", "spec",
  "placement-staging", "placement-production", "offer"]);
const SCHEMA = "loomai-ai-only-postgres-inbox-publication-plan/v1";

function sourcePins() {
  return Object.fromEntries(["./managed-postgres-inbox-spec.mjs", "./managed-postgres-inbox-dealership-offer.mjs",
    "./publish-ai-only-dealership-postgres-package.mjs", "./simulated-dealership-managed-spec.mjs",
    "./simulated-dealership-offer.mjs", "./publish-simulated-dealership-package.mjs", "./run-local-budget-restore.mjs",
    "./java/com/ai/fabric/runtime/budget/RuntimeBudgetExportRestoreTest.java",
    "../../product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json"]
    .map(path => [path, createHash("sha256").update(readFileSync(new URL(path, import.meta.url))).digest("hex")]));
}

function origin(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error("EXACT_HTTPS_PLATFORM_ORIGIN_REQUIRED"); }
  check(url.protocol === "https:" && url.pathname === "/" && !url.username && !url.password && !url.search && !url.hash,
    "EXACT_HTTPS_PLATFORM_ORIGIN_REQUIRED");
  return url.origin;
}

function nativeSubmission(row, selection, manifest, publisher) {
  requireConcreteIdentifier(row?.pluginVersionId, 64);
  check(/^mkv-[a-f0-9]{8}$/.test(row.pluginVersionId), "ACTUAL_SUBMISSION_VERSION_ID_REQUIRED");
  check(row.publisherId === publisher.id && row.pluginId === MANAGED_INBOX_TEMPLATE_PLUGIN_ID
    && row.pluginSlug === selection.pluginSlug && row.version === selection.templateVersion
    && row.releaseChannel === "EARLY_ACCESS", "SUBMISSION_SCOPE_CHANGED");
  same(row.manifest, manifest, "SUBMISSION_MANIFEST_CHANGED");
  requireDigest(row.bundleSha256);
  return row;
}

/** Pure offline plan from explicitly supplied normal API readbacks. No request function or ambient auth. */
export function planPostgresDealershipPackage(input, phase) {
  requireKeys(input, ["platformBaseUrl", "selection", "review", "configuration", "contracts"], "PACKAGE_INPUT_INVALID");
  check(PACKAGE_PHASES.includes(phase), "EXPLICIT_PUBLICATION_PHASE_REQUIRED");
  noCredentialValues(input);
  const platformBaseUrl = origin(input.platformBaseUrl);
  const {selection, contracts, review} = input;
  requireKeys(selection, ["publisherId", "pluginSlug", "templateVersion", "specId", "offerId"], "PACKAGE_SELECTION_INVALID");
  for (const field of ["publisherId", "specId", "offerId"]) requireConcreteIdentifier(selection[field], 64);
  check(/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(selection.pluginSlug), "EXACT_EXISTING_PLUGIN_SLUG_REQUIRED");
  check(selection.specId !== "mds-dealership-ai-managed-inbox-v1-0"
    && !["offer-loomai-engage-external-dealership-candidate-v1", "offer-loomai-engage-external-dealership-managed-inbox-v1"].includes(selection.offerId),
    "OLD_IMMUTABLE_PACKAGE_IDS_FORBIDDEN");
  text(review?.reviewReference);
  const reads = [];
  const capture = (path, value, allowNotFound = false) => {
    check(value !== undefined, "NORMAL_API_READBACK_REQUIRED");
    reads.push({path, value: structuredClone(value), allowNotFound});
    return value;
  };
  const publisherPath = `/api/marketplace/publishers/${selection.publisherId}`;
  const publisherDetail = capture(publisherPath, contracts.publisherDetail);
  const publisher = publisherDetail?.publisher;
  check(publisher?.id === selection.publisherId && publisher.status === "ACTIVE"
    && publisher.verificationStatus === "VERIFIED" && Array.isArray(publisherDetail.submissions), "EXISTING_VERIFIED_PUBLISHER_REQUIRED");
  const templateDetail = capture(`/api/marketplace/plugins/${MANAGED_INBOX_TEMPLATE_PLUGIN_ID}`, contracts.templateDetail);
  check(templateDetail?.plugin?.id === MANAGED_INBOX_TEMPLATE_PLUGIN_ID && templateDetail.plugin.slug === selection.pluginSlug
    && templateDetail.plugin.pluginType === "TEMPLATE" && templateDetail.plugin.status === "ACTIVE"
    && templateDetail.plugin.publisherSlug === publisher.slug, "EXISTING_TEMPLATE_PUBLISHER_OWNERSHIP_REQUIRED");
  const source = capture(`/api/marketplace/plugins/${MANAGED_INBOX_TEMPLATE_PLUGIN_ID}/versions/1.0.0`, contracts.sourceTemplate);
  const manifest = buildPostgresInboxTemplateManifest(source, selection.templateVersion);
  const inboxes = capture(`/api/deployment-provider/source-artifacts?serviceName=${DEPENDENCY_IMAGES.inbox.serviceName}`, contracts.inboxArtifacts);
  check(Array.isArray(inboxes), "NORMAL_ARTIFACT_LIST_REQUIRED");
  const matches = inboxes.filter(value => value?.imageRepository === DEPENDENCY_IMAGES.inbox.repository
    && value.imageDigest === DEPENDENCY_IMAGES.inbox.digest && value.gitCommitSha === DEPENDENCY_IMAGES.inbox.commit);
  check(matches.length === 1, "EXACTLY_ONE_PROMOTED_INBOX_ARTIFACT_REQUIRED");
  const inbox = requireProductionInboxArtifact(matches[0]);
  const spec = buildPostgresInboxSpec({inbox});
  const submissions = publisherDetail.submissions.filter(row => row.pluginId === MANAGED_INBOX_TEMPLATE_PLUGIN_ID
    && row.version === selection.templateVersion);
  check(submissions.length <= 1, "NATIVE_VERSION_AMBIGUOUS");
  const current = submissions[0] ? nativeSubmission(submissions[0], selection, manifest, publisher) : null;
  let request, operation = "CREATE", offerFingerprint = null;
  let nativeVersionId = current?.pluginVersionId ?? null;
  const reviewBody = {reviewNotes: review.reviewReference};
  if (phase === "template-submit") {
    if (current) {
      check(["SUBMITTED", "VALIDATED", "PUBLISHED"].includes(current.status), "SUBMISSION_STATE_NOT_REPLAYABLE");
      operation = "ALREADY_PRESENT_NO_WRITE";
    }
    request = {path: `/api/marketplace/publishers/${publisher.id}/submissions`, body: {
      pluginSlug: selection.pluginSlug, releaseChannel: "EARLY_ACCESS", manifest}};
  } else if (phase.startsWith("template-")) {
    check(current, "ACTUAL_NEW_SUBMISSION_READBACK_REQUIRED");
    const validate = phase === "template-validate";
    const expected = validate ? "SUBMITTED" : "VALIDATED";
    if (current.status !== expected) {
      check((validate && ["VALIDATED", "PUBLISHED"].includes(current.status)) || (!validate && current.status === "PUBLISHED"),
        "EXACT_SUBMISSION_PHASE_REQUIRED");
      operation = "ALREADY_PRESENT_NO_WRITE";
    }
    request = {path: `/api/marketplace/submissions/${nativeVersionId}/${validate ? "validate" : "publish"}`, body: reviewBody};
  } else {
    check(current?.status === "PUBLISHED", "NEW_PUBLISHED_TEMPLATE_READBACK_REQUIRED");
    const template = capture(`/api/marketplace/plugins/${MANAGED_INBOX_TEMPLATE_PLUGIN_ID}/versions/${selection.templateVersion}`,
      contracts.publishedTemplate);
    requirePublishedVersion(template);
    check(template.id === nativeVersionId && template.version === selection.templateVersion
      && template.pluginId === MANAGED_INBOX_TEMPLATE_PLUGIN_ID, "ACTUAL_NEW_TEMPLATE_ID_REQUIRED");
    same(template.manifest, manifest, "NEW_TEMPLATE_SOURCE_DRIFT");
    requireNewInboxIdentifiers({templateVersionId: template.id, specId: selection.specId});
    const specPath = `/api/customer-self-service/staff/templates/${template.id}/managed-dependency-spec`;
    const details = capture(specPath, contracts.inboxPublication, true);
    if (phase === "spec") {
      request = {path: specPath, body: {specId: selection.specId, spec, reviewReference: review.reviewReference}};
      if (details !== null) {
        requirePostgresInboxPublication(details, {templateVersionId: template.id, specId: selection.specId, inboxArtifact: inbox});
        operation = "ALREADY_PRESENT_NO_WRITE";
      }
    } else {
      requirePostgresInboxPublication(details, {templateVersionId: template.id, specId: selection.specId, inboxArtifact: inbox});
      const plugins = [];
      for (const reference of CUSTOMER_PLUGIN_REFS) {
        const [id, version] = reference.split("@");
        const rows = contracts.pluginVersions?.filter(row => row.pluginId === id && row.version === version);
        check(rows?.length === 1, "EXACT_PLUGIN_READBACK_REQUIRED");
        plugins.push(capture(`/api/marketplace/plugins/${id}/versions/${version}`, rows[0]));
      }
      const targets = capture("/api/deployment-provider/target-profiles", contracts.targets);
      const runtimeArtifacts = capture("/api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime", contracts.runtimeArtifacts);
      check(Array.isArray(runtimeArtifacts), "NORMAL_ARTIFACT_LIST_REQUIRED");
      const runtime = runtimeArtifacts.filter(row => row.imageDigest === "sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140"
        && row.gitCommitSha === "d2b1a9792dc10ec801d3ce30c170763f1f2ab301");
      check(runtime.length === 1, "EXACTLY_ONE_RUNTIME_ARTIFACT_REQUIRED");
      const behaviorCatalog = capture("/api/deployment-behaviors", contracts.behaviorCatalog);
      const executionExtensionCatalog = capture("/api/deployment-execution-extensions", contracts.executionExtensionCatalog);
      const definition = buildPostgresDealershipOffer({template, pluginVersions: plugins, artifact: runtime[0], targets,
        inboxArtifact: inbox, inboxSpec: details, specId: selection.specId, configuration: input.configuration,
        review, behaviorCatalog, executionExtensionCatalog});
      offerFingerprint = hash(definition);
      if (phase === "offer") request = {path: "/api/customer-self-service/staff/offers",
        body: {offerId: selection.offerId, definition, reviewReference: review.reviewReference}};
      else {
        const intent = phase === "placement-staging" ? "CUSTOMER_STAGING" : "CUSTOMER_PRODUCTION";
        const target = definition.targets.find(row => row.intent === intent);
        request = {path: `/api/customer-self-service/staff/targets/${target.targetProfileId}/placement-evidence`, body: {intent}};
      }
    }
  }
  request = {method: "POST", ...request, bodySha256: hash(request.body)};
  const payload = {schemaVersion: SCHEMA, platformBaseUrl, phase, operation, nativeVersionId, request,
    input: structuredClone(input), reads, sourceFileSha256: sourcePins(), offerFingerprint, postgresImage: INBOX_POSTGRES_IMAGE,
    postgresPinLineage: INBOX_POSTGRES_LINEAGE, liveReadinessProven: false, remainingGates: [...POSTGRES_PACKAGE_GATES]};
  return {...payload, planSha256: hash(payload)};
}

function checkedPlan(plan, reviewedPlanSha256) {
  requireDigest(reviewedPlanSha256);
  check(plan?.schemaVersion === SCHEMA && plan.planSha256 === reviewedPlanSha256, "EXACT_REVIEWED_PLAN_REQUIRED");
  same(plan, planPostgresDealershipPackage(plan.input, plan.phase), "PLAN_OR_BODY_TAMPERED");
  return structuredClone(plan);
}

async function readbacks(plan, request) {
  for (const row of plan.reads) same(await request(row.path, {allowNotFound: row.allowNotFound}), row.value, "SOURCE_READBACK_DRIFT_REPLAN_REQUIRED");
}

/** One reviewed normal API mutation only. Never registers/promotes artifacts, invites, applies, or retries writes. */
export async function executePostgresDealershipPhase({plan, reviewedPlanSha256, execute = false, request, checkpoint, priorReceipt} = {}) {
  check(execute === true, "EXPLICIT_REVIEWED_EXECUTION_REQUIRED");
  plan = checkedPlan(plan, reviewedPlanSha256);
  check(typeof request === "function" && typeof checkpoint === "function", "EXPLICIT_REQUEST_AND_DURABLE_CHECKPOINT_REQUIRED");
  check(!priorReceipt || priorReceipt.writeAttempted !== true, "PRIOR_WRITE_MUST_BE_RECONCILED_NO_BLIND_RETRY");
  const session = await request("/api/platform/auth/session");
  check(session?.enabled === true && session.authenticated === true && session.role === "PLATFORM_ADMIN"
    && ["API_KEY", "SESSION"].includes(session.authenticationMode), "NORMAL_PLATFORM_ADMIN_REQUIRED");
  await readbacks(plan, request);
  const receipt = {schemaVersion: "loomai-ai-only-postgres-inbox-publication-receipt/v1", phase: plan.phase,
    planSha256: plan.planSha256, requestBodySha256: plan.request.bodySha256, nativeVersionId: plan.nativeVersionId,
    writeAttempted: false, state: "ALREADY_PRESENT_NO_WRITE", liveReadinessProven: false, secretsRecorded: false,
    customerBusinessSystemsProvisioned: false, remainingGates: [...POSTGRES_PACKAGE_GATES]};
  if (plan.operation === "ALREADY_PRESENT_NO_WRITE") { await checkpoint(receipt); return receipt; }
  receipt.state = "PUBLICATION_ATTEMPT_STARTED"; receipt.writeAttempted = true;
  await checkpoint(structuredClone(receipt));
  try {
    const result = await request(plan.request.path, {method: "POST", body: plan.request.body});
    if (plan.phase.startsWith("template-")) {
      const manifest = buildPostgresInboxTemplateManifest(plan.input.contracts.sourceTemplate, plan.input.selection.templateVersion);
      nativeSubmission(result, plan.input.selection, manifest, plan.input.contracts.publisherDetail.publisher);
      const expected = {"template-submit": "SUBMITTED", "template-validate": "VALIDATED", "template-publish": "PUBLISHED"}[plan.phase];
      check(result.status === expected && (plan.nativeVersionId === null || result.pluginVersionId === plan.nativeVersionId), "NATIVE_SUBMISSION_RECEIPT_CHANGED");
      if (plan.phase !== "template-submit") check(result.reviewNotes === plan.request.body.reviewNotes, "NATIVE_REVIEW_RECEIPT_CHANGED");
      receipt.nativeVersionId = result.pluginVersionId;
    } else if (plan.phase === "spec") {
      const details = await request(plan.request.path);
      const inbox = plan.input.contracts.inboxArtifacts.find(row => row.id === plan.request.body.spec.services[0].sourceArtifactId);
      const native = requirePostgresInboxPublication(details, {templateVersionId: plan.nativeVersionId,
        specId: plan.input.selection.specId, inboxArtifact: inbox});
      check(result.specId === native.specId && result.templateVersionId === native.templateVersionId
        && result.fingerprint === native.fingerprint && result.reviewReference === plan.request.body.reviewReference
        && details.publication.reviewReference === plan.request.body.reviewReference, "NATIVE_SPEC_RECEIPT_CHANGED");
      receipt.specFingerprint = native.fingerprint;
    } else if (plan.phase === "offer") {
      requireKeys(result, ["offerId", "fingerprint"], "NATIVE_OFFER_RECEIPT_INVALID");
      check(result.offerId === plan.input.selection.offerId && result.fingerprint === plan.offerFingerprint, "NATIVE_OFFER_RECEIPT_CHANGED");
      receipt.offerId = result.offerId; receipt.offerFingerprint = result.fingerprint;
    } else {
      requireConcreteIdentifier(result?.id, 64);
      const target = plan.input.contracts.targets.find(row => row.id === result.targetProfileId);
      check(target && result.intent === plan.request.body.intent && plan.request.path.includes(`/${result.targetProfileId}/`)
        && result.profileFingerprint === profileFingerprint(target) && Number.isFinite(Date.parse(result.checkedAt))
        && Date.parse(result.checkedAt) <= Date.now() && Date.parse(result.expiresAt) > Date.now(), "PLACEMENT_RECEIPT_INVALID");
      receipt.placementEvidenceId = result.id;
    }
    receipt.state = plan.phase === "offer" ? "PUBLISHED_CUSTOMER_READBACK_PENDING" : "PHASE_CONFIRMED_REPLAN_NEXT_PHASE";
    await checkpoint(structuredClone(receipt));
    return receipt;
  } catch {
    receipt.state = "PUBLICATION_OUTCOME_UNKNOWN";
    await checkpoint(structuredClone(receipt));
    throw new Error("PUBLICATION_OUTCOME_UNKNOWN: preserve receipt; reconcile native metadata before any further write; never rotate IDs to retry.");
  }
}

export function postgresPackageTransport(baseUrl, apiKey, fetchImpl = fetch) {
  const base = origin(baseUrl);
  check(typeof apiKey === "string" && apiKey.trim() && !/[\r\n]/.test(apiKey), "ADMIN_AUTHORITY_REQUIRED_ONLY_FOR_EXPLICIT_EXECUTION");
  return async (path, {method = "GET", body, allowNotFound = false} = {}) => {
    check(path.startsWith("/api/") && !path.includes("..") && !/[\r\n#]/.test(path), "NORMAL_API_PATH_REQUIRED");
    let response;
    try { response = await fetchImpl(base + path, {method, redirect: "error", signal: AbortSignal.timeout(30_000),
      headers: {Accept: "application/json", "X-PLATFORM-API-KEY": apiKey,
        ...(body === undefined ? {} : {"Content-Type": "application/json"})},
      ...(body === undefined ? {} : {body: JSON.stringify(body)})}); }
    catch { throw new Error("PLATFORM_TRANSPORT_OUTCOME_UNKNOWN"); }
    if (allowNotFound && method === "GET" && response.status === 404) return null;
    check(response.ok, `PLATFORM_HTTP_${response.status}`);
    const chunks = []; let bytes = 0;
    const reader = response.body?.getReader(); check(reader, "BOUNDED_PLATFORM_JSON_REQUIRED");
    try {
      for (;;) {
        const {done, value} = await reader.read(); if (done) break;
        bytes += value.byteLength;
        if (bytes > 4_000_000) { await reader.cancel(); throw new Error("PLATFORM_JSON_TOO_LARGE"); }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    try { return JSON.parse(new TextDecoder("utf-8", {fatal: true}).decode(Buffer.concat(chunks))); }
    catch { throw new Error("PLATFORM_JSON_INVALID"); }
  };
}

async function jsonFile(path) {
  const file = resolve(path); const info = await lstat(file);
  check(info.isFile() && !info.isSymbolicLink() && info.size <= 4_000_000, "REGULAR_BOUNDED_LOCAL_JSON_REQUIRED");
  try { return JSON.parse(await readFile(file, "utf8")); }
  catch { throw new Error("LOCAL_PUBLICATION_JSON_INVALID"); }
}

async function main() {
  const args = process.argv.slice(2), mode = args.shift();
  if (!mode || mode === "--help") {
    process.stdout.write("Offline: plan --input FILE --phase PHASE --output NEW_FILE\nLater reviewed execution: execute --plan FILE --reviewed-sha256 SHA --execute-reviewed-phase PHASE --output NEW_RECEIPT [--prior-receipt FILE]\n");
    return;
  }
  check(["plan", "execute"].includes(mode), "EXPLICIT_PLAN_OR_EXECUTE_MODE_REQUIRED");
  const allowed = mode === "plan" ? ["--input", "--phase", "--output"]
    : ["--plan", "--reviewed-sha256", "--execute-reviewed-phase", "--output", "--prior-receipt"];
  const flags = {};
  for (let i = 0; i < args.length; i += 2) {
    check(allowed.includes(args[i]) && !Object.hasOwn(flags, args[i]) && args[i + 1] && !args[i + 1].startsWith("--"), "EXACT_MODE_ARGUMENTS_REQUIRED");
    flags[args[i]] = args[i + 1];
  }
  check(allowed.filter(key => key !== "--prior-receipt").every(key => flags[key]), "ALL_MODE_ARGUMENTS_REQUIRED");
  const planned = mode === "plan" ? planPostgresDealershipPackage(await jsonFile(flags["--input"]), flags["--phase"])
    : checkedPlan(await jsonFile(flags["--plan"]), flags["--reviewed-sha256"]);
  if (mode === "execute") check(planned.phase === flags["--execute-reviewed-phase"], "EXACT_REVIEWED_PHASE_REQUIRED");
  const output = await open(resolve(flags["--output"]), "wx", 0o600);
  const checkpoint = async value => {
    const bytes = Buffer.from(JSON.stringify(value, null, 2) + "\n");
    await output.truncate(0); let offset = 0;
    while (offset < bytes.length) {
      const {bytesWritten} = await output.write(bytes, offset, bytes.length - offset, offset);
      check(bytesWritten > 0, "LOCAL_CHECKPOINT_WRITE_INCOMPLETE"); offset += bytesWritten;
    }
    await output.sync();
  };
  try {
    const result = mode === "plan" ? planned : await executePostgresDealershipPhase({plan: planned,
      reviewedPlanSha256: flags["--reviewed-sha256"], execute: true, checkpoint,
      priorReceipt: flags["--prior-receipt"] ? await jsonFile(flags["--prior-receipt"]) : undefined,
      request: postgresPackageTransport(planned.platformBaseUrl, process.env.PLATFORM_ADMIN_API_KEY)});
    await checkpoint(result);
    process.stdout.write(JSON.stringify({state: result.state ?? "OFFLINE_PLANNED_NO_EFFECTS", phase: planned.phase,
      planSha256: planned.planSha256, requestBodySha256: planned.request.bodySha256, output: resolve(flags["--output"])}) + "\n");
  } finally { await output.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  main().catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
