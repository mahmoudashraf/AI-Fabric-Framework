import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {mkdtemp, readFile, rm, stat, writeFile} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {fileURLToPath} from "node:url";
import {spawnSync} from "node:child_process";
import test from "node:test";
import {hash} from "../simulated-dealership-offer.mjs";
import {executePostgresDealershipPhase, PACKAGE_PHASES, planPostgresDealershipPackage, postgresPackageTransport} from "../publish-ai-only-dealership-postgres-package.mjs";
import {fakePhaseServer, packageInput} from "./fixtures/managed-postgres-inbox-publication-fixture.mjs";

function inputFor(phase) {
  const input = packageInput(phase === "template-submit" ? null : phase === "template-validate" ? "SUBMITTED"
    : phase === "template-publish" ? "VALIDATED" : "PUBLISHED");
  if (phase === "spec") input.contracts.inboxPublication = null;
  return input;
}

test("default and every planned phase are offline, hash exact bodies, and invent no created version ID", () => {
  const original = globalThis.fetch;
  globalThis.fetch = () => { throw new Error("UNEXPECTED_NETWORK"); };
  try {
    for (const phase of PACKAGE_PHASES) {
      const input = inputFor(phase), before = structuredClone(input);
      const plan = planPostgresDealershipPackage(input, phase);
      assert.deepEqual(input, before);
      assert.equal(plan.liveReadinessProven, false);
      assert.equal(plan.request.bodySha256, hash(plan.request.body));
      assert.ok(Object.values(plan.sourceFileSha256).every(value => /^[a-f0-9]{64}$/.test(value)));
      const {planSha256, ...payload} = plan; assert.equal(planSha256, hash(payload));
      assert.equal(plan.nativeVersionId, phase === "template-submit" ? null : "mkv-33333333");
      assert.ok(plan.remainingGates.includes("ACTUAL_INBOX_POSTGRES_MIGRATION_RESTART_RESTORE_REQUIRED"));
    }
  } finally { globalThis.fetch = original; }
  const result = spawnSync(process.execPath, [new URL("../publish-ai-only-dealership-postgres-package.mjs", import.meta.url).pathname],
    {encoding: "utf8", env: {PATH: process.env.PATH}});
  assert.equal(result.status, 0); assert.match(result.stdout, /Offline: plan/);
});

test("every phase uses the exact current public API body keys without leaking tooling fields", () => {
  const expected = {"template-submit": ["pluginSlug", "releaseChannel", "manifest"], "template-validate": ["reviewNotes"],
    "template-publish": ["reviewNotes"], spec: ["specId", "spec", "reviewReference"],
    "placement-staging": ["intent"], "placement-production": ["intent"], offer: ["offerId", "definition", "reviewReference"]};
  for (const phase of PACKAGE_PHASES) assert.deepEqual(Object.keys(planPostgresDealershipPackage(inputFor(phase), phase).request.body).sort(), expected[phase].sort());
  const root = "../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/";
  const submission = readFileSync(new URL(root + "marketplace/model/CreateMarketplacePublisherSubmissionRequest.java", import.meta.url), "utf8");
  for (const declaration of ["String pluginSlug", "String releaseChannel", "JsonNode manifest"]) assert.ok(submission.includes(declaration));
  const review = readFileSync(new URL(root + "marketplace/model/ReviewMarketplacePublisherSubmissionRequest.java", import.meta.url), "utf8");
  assert.match(review, /String reviewNotes/);
  const spec = readFileSync(new URL(root + "deployment/dependency/ManagedDependencySpecPublishingService.java", import.meta.url), "utf8");
  assert.match(spec, /record PublicationRequest\(String specId, ManagedDependencySpec spec, String reviewReference\)/);
  const offers = readFileSync(new URL(root + "customerselfservice/model/CustomerOfferContracts.java", import.meta.url), "utf8");
  assert.match(offers, /record PublishOfferRequest\(@NotBlank String offerId, @NotNull @Valid Definition definition,\s*@NotBlank String reviewReference\)/);
});

for (const phase of PACKAGE_PHASES) {
  test(`${phase}: explicit reviewed execution makes only its one normal API write`, async () => {
    const plan = planPostgresDealershipPackage(inputFor(phase), phase), checkpoints = [];
    const server = fakePhaseServer(plan, (path, options) => {
      if (phase === "spec" && options.method !== "POST" && path === plan.request.path
        && server.calls.some(call => call.method === "POST")) {
        const details = structuredClone(packageInput("PUBLISHED").contracts.inboxPublication);
        details.publication.reviewReference = plan.request.body.reviewReference;
        return details;
      }
    });
    const receipt = await executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: server.request, checkpoint: async value => checkpoints.push(structuredClone(value))});
    assert.deepEqual(server.calls.filter(call => call.method === "POST").map(({path, body}) => ({path, body})),
      [{path: plan.request.path, body: plan.request.body}]);
    assert.equal(checkpoints[0].state, "PUBLICATION_ATTEMPT_STARTED");
    assert.equal(receipt.liveReadinessProven, false);
    assert.equal(receipt.secretsRecorded, false);
    assert.equal(receipt.customerBusinessSystemsProvisioned, false);
    assert.doesNotMatch(JSON.stringify(receipt), /synthetic-unit-review|sb_publishable/);
  });
}

test("missing execute/hash/admin/checkpoint and tampering fail before any write", async () => {
  const plan = planPostgresDealershipPackage(inputFor("offer"), "offer");
  const server = fakePhaseServer(plan);
  for (const extra of [{execute: false}, {reviewedPlanSha256: "0".repeat(64)}, {checkpoint: null},
    {plan: {...plan, phase: "spec"}}, {plan: {...plan, request: {...plan.request, body: {...plan.request.body, offerId: "other"}}}}])
    await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: server.request, checkpoint: async () => {}, ...extra}));
  assert.deepEqual(server.calls, []);
  const sourceDrift = structuredClone(plan); sourceDrift.sourceFileSha256["./managed-postgres-inbox-spec.mjs"] = "f".repeat(64);
  const {planSha256: ignored, ...payload} = sourceDrift; sourceDrift.planSha256 = hash(payload);
  await assert.rejects(executePostgresDealershipPhase({plan: sourceDrift, reviewedPlanSha256: sourceDrift.planSha256,
    execute: true, request: server.request, checkpoint: async () => {}}), /TAMPERED/);
  assert.deepEqual(server.calls, []);
  const nonAdmin = fakePhaseServer(plan, path => path.endsWith("/auth/session")
    ? {enabled: true, authenticated: true, role: "PLATFORM_OPERATOR", authenticationMode: "API_KEY"} : undefined);
  await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
    request: nonAdmin.request, checkpoint: async () => {}}), /ADMIN/);
  assert.equal(nonAdmin.calls.filter(call => call.method === "POST").length, 0);
});

test("source, artifact, approved version and offer configuration drift require a new reviewed plan", async () => {
  const plan = planPostgresDealershipPackage(inputFor("offer"), "offer");
  for (const row of plan.reads) {
    const server = fakePhaseServer(plan, path => path === row.path ? {syntheticDrift: true} : undefined);
    await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: server.request, checkpoint: async () => {}}), /DRIFT/);
    assert.equal(server.calls.filter(call => call.method === "POST").length, 0);
  }
});

test("exact immutable template/spec replay verifies normal readback without any second POST", async () => {
  for (const phase of ["template-submit", "template-validate", "template-publish", "spec"]) {
    const plan = planPostgresDealershipPackage(packageInput("PUBLISHED"), phase), server = fakePhaseServer(plan);
    assert.equal(plan.operation, "ALREADY_PRESENT_NO_WRITE");
    const receipt = await executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: server.request, checkpoint: async () => {}});
    assert.equal(receipt.writeAttempted, false);
    assert.equal(server.calls.filter(call => call.method === "POST").length, 0);
  }
});

test("wrong phase, duplicate artifact, missing/foreign native identity and old immutable ID are refused", () => {
  assert.throws(() => planPostgresDealershipPackage(packageInput("SUBMITTED"), "template-publish"));
  assert.throws(() => planPostgresDealershipPackage(packageInput("VALIDATED"), "spec"));
  for (const mutate of [i => { i.contracts.inboxArtifacts = []; }, i => { i.contracts.inboxArtifacts.push(structuredClone(i.contracts.inboxArtifacts[0])); },
    i => { i.selection.publisherId = "PENDING_PUBLISHER"; }, i => { i.selection.specId = "mds-dealership-ai-managed-inbox-v1-0"; },
    i => { i.contracts.publisherDetail.submissions[0].pluginVersionId = "PENDING_NATIVE_VERSION_ID"; },
    i => { i.contracts.publisherDetail.publisher.slug = "wrong-owner"; },
    i => { i.contracts.sourceTemplate.manifest.version = "changed"; },
    i => { i.contracts.inboxPublication.spec.services[0].database = null; },
    i => { i.platformBaseUrl = "https://user:synthetic-password@platform.synthetic.example"; }]) {
    const input = packageInput("PUBLISHED"); mutate(input); assert.throws(() => planPostgresDealershipPackage(input, "offer"));
  }
});

test("unknown or malformed mutation receipt is preserved and cannot be blindly replayed", async () => {
  const plan = planPostgresDealershipPackage(inputFor("offer"), "offer");
  for (const fault of ["transport", "receipt"]) {
    const checkpoints = [], server = fakePhaseServer(plan, (_path, options) => {
      if (options.method === "POST") { if (fault === "transport") throw new Error("synthetic unknown"); return {offerId: "wrong", fingerprint: "x"}; }
    });
    await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: server.request, checkpoint: async value => checkpoints.push(structuredClone(value))}), /OUTCOME_UNKNOWN/);
    assert.equal(checkpoints.at(-1).state, "PUBLICATION_OUTCOME_UNKNOWN");
    assert.equal(server.calls.filter(call => call.method === "POST").length, 1);
    const replay = fakePhaseServer(plan);
    await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: replay.request, priorReceipt: checkpoints.at(-1), checkpoint: async () => {}}), /NO_BLIND_RETRY/);
    assert.deepEqual(replay.calls, []);
  }
});

test("checkpoint failure occurs before the publication write and no auto-retry is implemented", async () => {
  const plan = planPostgresDealershipPackage(inputFor("template-submit"), "template-submit"), server = fakePhaseServer(plan);
  await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
    request: server.request, checkpoint: async () => { throw new Error("synthetic disk failure"); }}), /disk failure/);
  assert.equal(server.calls.filter(call => call.method === "POST").length, 0);
});

test("future/stale/foreign placement evidence is not accepted as a publication prerequisite receipt", async () => {
  const plan = planPostgresDealershipPackage(inputFor("placement-staging"), "placement-staging");
  for (const patch of [{profileFingerprint: "changed"}, {checkedAt: "2099-01-01T00:00:00Z"}, {expiresAt: "2000-01-01T00:00:00Z"}]) {
    const server = fakePhaseServer(plan, (_path, options) => options.method === "POST" ? {id: "placement-synthetic",
      targetProfileId: "dtp-coolify-staging-behavior", intent: "CUSTOMER_STAGING", checkedAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 60000).toISOString(), ...patch} : undefined);
    await assert.rejects(executePostgresDealershipPhase({plan, reviewedPlanSha256: plan.planSha256, execute: true,
      request: server.request, checkpoint: async () => {}}), /OUTCOME_UNKNOWN/);
  }
});

test("normal transport is injected for tests, refuses redirects, and sends auth only on the later explicit request", async () => {
  const calls = [];
  const request = postgresPackageTransport("https://platform.synthetic.example", "synthetic-unit-only", async (...args) => {
    calls.push(args); return new Response(JSON.stringify({enabled: true}), {status: 200});
  });
  assert.deepEqual(calls, []);
  assert.deepEqual(await request("/api/platform/auth/session"), {enabled: true});
  assert.equal(calls[0][1].redirect, "error");
  assert.equal(calls[0][1].headers["X-PLATFORM-API-KEY"], "synthetic-unit-only");
  await assert.rejects(request("/api/../unsafe"));
  assert.equal(calls.length, 1);
});

test("actual CLI planning needs no auth/transport, writes mode0600 and cannot overwrite a frozen plan", async () => {
  const directory = await mkdtemp(join(tmpdir(), "postgres-inbox-publication-cli-"));
  const script = fileURLToPath(new URL("../publish-ai-only-dealership-postgres-package.mjs", import.meta.url));
  const preload = fileURLToPath(new URL("./fixtures/managed-postgres-inbox-network-denied.mjs", import.meta.url));
  const inputFile = join(directory, "synthetic-input.json"), output = join(directory, "plan.json");
  const receipt = join(directory, "receipt-must-not-exist.json");
  const run = args => spawnSync(process.execPath, ["--import", preload, script, ...args],
    {encoding: "utf8", env: {PATH: process.env.PATH}});
  try {
    await writeFile(inputFile, JSON.stringify(inputFor("offer")), {mode: 0o600});
    const first = run(["plan", "--input", inputFile, "--phase", "offer", "--output", output]);
    assert.equal(first.status, 0, first.stderr);
    assert.match(first.stdout, /OFFLINE_PLANNED_NO_EFFECTS/);
    assert.equal((await stat(output)).mode & 0o777, 0o600);
    const bytes = await readFile(output, "utf8"), plan = JSON.parse(bytes);
    assert.equal(plan.request.bodySha256, hash(plan.request.body));
    assert.equal(plan.nativeVersionId, "mkv-33333333");
    assert.doesNotMatch(bytes, /X-PLATFORM-API-KEY|synthetic-raw-key|sessionCookie/);
    const again = run(["plan", "--input", inputFile, "--phase", "offer", "--output", output]);
    assert.notEqual(again.status, 0);
    assert.equal(await readFile(output, "utf8"), bytes);
    const wrongPhase = run(["execute", "--plan", output, "--reviewed-sha256", plan.planSha256,
      "--execute-reviewed-phase", "spec", "--output", receipt]);
    assert.notEqual(wrongPhase.status, 0); assert.match(wrongPhase.stderr, /EXACT_REVIEWED_PHASE/);
    await assert.rejects(stat(receipt), {code: "ENOENT"});
    const missingConsent = run(["execute", "--plan", output, "--reviewed-sha256", plan.planSha256, "--output", receipt]);
    assert.notEqual(missingConsent.status, 0);
    await assert.rejects(stat(receipt), {code: "ENOENT"});
  } finally { await rm(directory, {recursive: true, force: true}); }
});
