import assert from "node:assert/strict";
import test from "node:test";

import {
  buildManagedDependencySpec,
  DEPENDENCY_IMAGES,
  POSTGRES_IMAGE,
  reviewedManagedDependencyCapacity,
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
    milliCpu: 1500,
    memoryMiB: 2816,
    diskMiB: 14336,
    resources: 4,
  });
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
