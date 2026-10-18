import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {
  buildManagedDependencySpec,
  DEPENDENCY_IMAGES,
  POSTGRES_IMAGE,
  reviewedManagedDependencyCapacity,
  SPEC_ID,
  TEMPLATE_VERSION_ID,
} from "../simulated-dealership-managed-spec.mjs";
import {
  comparableJson,
  validateManagedDependencySpec,
} from "../publish-simulated-dealership-package.mjs";

const artifacts = Object.fromEntries(Object.entries(DEPENDENCY_IMAGES).map(([slot, artifact]) => [slot, {
  id: `artifact-${slot}`,
  image: `${artifact.repository}@${artifact.digest}`,
  commit: artifact.commit,
}]));

test("builds the exact three-service reviewed topology", () => {
  const spec = validateManagedDependencySpec(buildManagedDependencySpec(artifacts));

  assert.deepEqual(spec.services.map(({slot}) => slot), ["simulator", "inbox", "receiver"]);
  assert.equal(spec.services[0].volumes[0].mountPath, "/app/data");
  assert.equal(spec.services[1].volumes[0].mountPath, "/app/data");
  assert.equal(spec.services[1].contract, "BASIC_INBOX_KEYS_V1");
  assert.equal(spec.services[2].database.image, POSTGRES_IMAGE);
});

test("reviewed capacity includes services, database and persistent volumes", () => {
  assert.deepEqual(reviewedManagedDependencyCapacity(), {
    milliCpu: 1000,
    memoryMiB: 2816,
    diskMiB: 14336,
    resources: 4,
  });
});

test("candidate spec 1.6 changes only receiver and database CPU, retaining the immutable old fixture", () => {
  assert.equal(TEMPLATE_VERSION_ID, "mkv-template-simulated-dealership-experience-v1-6");
  assert.equal(SPEC_ID, "mds-simulated-dealership-experience-v1-6");
  const base = "../../../Platfrom/backend/src/test/resources/managed-dependency/";
  const old = JSON.parse(readFileSync(new URL(`${base}simulated-dealership-experience-v1.template.json`, import.meta.url), "utf8"));
  const candidate = JSON.parse(readFileSync(new URL(`${base}simulated-dealership-experience-v1-6.template.json`, import.meta.url), "utf8"));
  const legacyReceiver = old.services.find(({slot}) => slot === "receiver");
  const receiver = candidate.services.find(({slot}) => slot === "receiver");
  assert.equal(legacyReceiver.capacity.milliCpu, 500);
  assert.equal(legacyReceiver.database.capacity.milliCpu, 500);
  assert.equal(receiver.capacity.milliCpu, 250);
  assert.equal(receiver.database.capacity.milliCpu, 250);
  const restored = structuredClone(candidate);
  restored.services.find(({slot}) => slot === "receiver").capacity.milliCpu = 500;
  restored.services.find(({slot}) => slot === "receiver").database.capacity.milliCpu = 500;
  assert.deepEqual(restored, old);
});

test("keeps all managed template placeholders server-owned and distinct", () => {
  const spec = buildManagedDependencySpec(artifacts);
  const bindings = Object.fromEntries(spec.runtimeEnvironment.map((binding) => [binding.name, binding.value]));

  assert.equal(Object.keys(bindings).length, 14);
  assert.deepEqual(bindings.MANAGED_RECEIVER_ACTION_URL, {
    kind: "SERVICE_URL_PATH",
    reference: "/api/internal/actions/execute",
    serviceSlot: "receiver",
  });
  assert.deepEqual(bindings.MANAGED_INBOX_CALLBACK_SIGNING_SECRET, {
    kind: "TARGET_SECRET",
    reference: "conversation-handover-callback-signing",
  });
  assert.deepEqual(bindings.MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET, {
    kind: "SERVICE_SECRET",
    reference: "callback",
    serviceSlot: "inbox",
  });
  assert.deepEqual(bindings.MANAGED_INBOX_BINDING_ID, {
    kind: "BINDING_ID",
    serviceSlot: "inbox",
  });
});

test("pins every service and database to immutable image digests", () => {
  const spec = buildManagedDependencySpec(artifacts);

  for (const service of spec.services) {
    assert.match(service.image, /@sha256:[a-f0-9]{64}$/);
    assert.match(service.sourceCommit, /^[a-f0-9]{40}$/);
    if (service.database) assert.match(service.database.image, /@sha256:[a-f0-9]{64}$/);
  }
});

test("ignores only server-materialized optional null fields during topology readback", () => {
  const reviewed = {
    value: {kind: "SERVICE_URL", reference: "inbox"},
    enabled: true,
  };
  const readback = {
    value: {kind: "SERVICE_URL", reference: "inbox", serviceSlot: null},
    enabled: true,
  };

  assert.deepEqual(comparableJson(readback), comparableJson(reviewed));
  assert.notDeepEqual(
    comparableJson({...readback, enabled: false}),
    comparableJson(reviewed),
  );
});
