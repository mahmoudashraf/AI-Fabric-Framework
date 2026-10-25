import assert from 'node:assert/strict';
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {resolve, join} from 'node:path';
import {setTimeout as sleep} from 'node:timers/promises';

process.umask(0o077);
const env = process.env;
const base = new URL(env.LOOMAI_PLATFORM_URL);
assert.equal(base.protocol, 'https:', 'Use the live HTTPS control-plane route.');
assert.ok(!base.username && !base.password && !base.search && !base.hash);
const scope = [env.LIFECYCLE_DEPLOYMENT_ID, env.LIFECYCLE_TARGET_ID, env.LIFECYCLE_RELEASE_ID];
for (const id of [...scope, env.LIFECYCLE_OPERATION_ID]) assert.match(id ?? '', /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/);
assert.equal(env.LIFECYCLE_SCOPE_ACK, scope.join(':'), 'Explicitly acknowledge the exact disposable fixture scope.');
assert.ok(env.LOOMAI_PLATFORM_API_KEY && !/[\r\n]/.test(env.LOOMAI_PLATFORM_API_KEY));
const goal = env.LIFECYCLE_GOAL ?? 'RECONCILE';
const mode = env.LIFECYCLE_MODE ?? 'COLD';
assert.ok(['STOP', 'RETIRE', 'RECOVER', 'RECONCILE'].includes(goal));
assert.ok(['COLD', 'GRACEFUL'].includes(mode));
const reclaim = env.LIFECYCLE_RECLAIM_OWNED_DATA === 'true';
if (reclaim) assert.equal(goal, 'RETIRE');
const directory = resolve(env.LIFECYCLE_EVIDENCE_DIRECTORY ?? `/tmp/loomai-lifecycle-${env.LIFECYCLE_OPERATION_ID}`);
mkdirSync(directory, {recursive: true, mode: 0o700});
const intentPath = join(directory, 'intent.json');
const request = existsSync(intentPath) ? JSON.parse(readFileSync(intentPath, 'utf8')) : {
  operationId: env.LIFECYCLE_OPERATION_ID, releaseId: scope[2], goal, mode,
  policyRevision: env.LIFECYCLE_POLICY_REVISION ?? 'explicit-disposable-fixture-v1',
  dueAt: env.LIFECYCLE_DUE_AT ?? new Date().toISOString(),
  deadlineAt: env.LIFECYCLE_DEADLINE_AT ?? new Date(Date.now() + 30 * 60_000).toISOString(),
  reclaimOwnedData: reclaim,
};
assert.equal(request.operationId, env.LIFECYCLE_OPERATION_ID);
assert.equal(request.releaseId, scope[2]); assert.equal(request.goal, goal);
assert.equal(request.mode, mode); assert.equal(request.reclaimOwnedData, reclaim);
if (!existsSync(intentPath)) writeFileSync(intentPath, JSON.stringify(request, null, 2), {flag: 'wx', mode: 0o600});

async function api(path, body) {
  const response = await fetch(new URL(path, base), {
    method: body === undefined ? 'GET' : 'POST',
    headers: {[env.LOOMAI_PLATFORM_API_KEY_HEADER ?? 'X-PLATFORM-API-KEY']: env.LOOMAI_PLATFORM_API_KEY,
      Accept: 'application/json', ...(body === undefined ? {} : {'Content-Type': 'application/json'})},
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(180_000), redirect: 'error',
  });
  const value = await response.json();
  assert.ok(response.ok, `Platform HTTP ${response.status}: ${value.code ?? value.errorCode ?? 'request rejected'}`);
  return value;
}

const operationPath = `/api/deployment-operations/jobs/${request.operationId}`;
const created = await api(`/api/deployment-operations/targets/${scope[0]}/${scope[1]}/jobs`, request);
assert.equal(created.authorization.deploymentId, scope[0]);
assert.equal(created.authorization.targetProfileId, scope[1]);
assert.equal(created.authorization.releaseId, scope[2]);
const started = Date.now(); let last;
while (Date.now() - started < 35 * 60_000) {
  const state = await api(operationPath);
  assert.equal(state.request.operationId, request.operationId);
  assert.equal(state.request.grantId, created.authorization.grantId);
  const progress = {state: state.state, phase: state.phase, code: state.code};
  if (JSON.stringify(progress) !== last) { console.log(JSON.stringify(progress)); last = JSON.stringify(progress); }
  writeFileSync(join(directory, 'latest.json'), JSON.stringify(state, null, 2), {mode: 0o600});
  if (['SUCCEEDED', 'CANCELLED', 'BLOCKED', 'FAILED'].includes(state.state)) {
    const native = await api(`${operationPath}/native`);
    const events = await api(`${operationPath}/events`);
    writeFileSync(join(directory, 'native-receipts.json'), JSON.stringify(native, null, 2), {mode: 0o600});
    writeFileSync(join(directory, 'events.json'), JSON.stringify(events, null, 2), {mode: 0o600});
    assert.equal(state.state, 'SUCCEEDED', `Workflow stopped honestly at ${state.code}. Inspect the retained receipts; do not bypass it.`);
    assert.equal(native.claimed, false);
    assert.equal(native.state, 'CLOSED');
    assert.ok(native.commands.every(command => command.status === 'SUCCEEDED'));
    console.log(JSON.stringify({result: 'PASS', operationId: request.operationId, goal, mode, evidenceDirectory: directory}));
    break;
  }
  await api(`${operationPath}/advance`, {});
  await sleep(3000);
}
assert.ok(last && JSON.parse(last).state === 'SUCCEEDED', 'Live workflow timed out; its durable identity and receipts are retained.');
