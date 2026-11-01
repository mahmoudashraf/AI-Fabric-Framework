import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const targets = {
  staging: {
    origin: 'https://northfield-staging.apps.loomai.pro',
    installation: 'awi_pub_2a877305f6066b698c0ba3bf1dbd7455',
    runtime: 'https://dep-3c4dbe4f.46.224.145.148.sslip.io',
  },
  production: {
    origin: 'https://northfield-console-live.apps.loomai.pro',
    installation: 'awi_pub_d33c69a074da480628c2f81358a03144',
    runtime: 'https://dep-3c4dbe4f.46.225.162.106.sslip.io',
  },
};

const cases = [
  { id: 'stock-then-delivery', session: 'forward', query: 'What electric cars do you have under GBP 35,000, and what is your delivery policy?' },
  { id: 'delivery-then-stock', session: 'reverse', query: 'What is your delivery policy, and which electric cars do you have under GBP 35,000?' },
  { id: 'delivery-cost-and-handover', session: 'delivery', query: 'What does local delivery cost and what must be ready before handover?' },
  { id: 'delivery-charge-missing-fact', session: 'delivery', query: 'Can you give me the exact delivery charge for a home 40 miles away? Do not guess or submit a request.' },
  { id: 'test-drive-requirements', session: 'test-drive', query: 'What age and driving licence history does Northfield require for a test drive? Do not book one.' },
  { id: 'test-drive-duration', session: 'test-drive', query: 'How long is the test drive, and how far in advance must I book? Distinguish what the policy states from anything it does not specify. Do not book anything.' },
  { id: 'test-drive-required-inputs', session: 'test-drive', query: 'What details would you need from me to request a test drive for the Aster E1? Explain only; do not create or submit anything.' },
  { id: 'vehicle-with-warranty-and-delivery', session: 'mixed', query: 'Give the Aster E1 current price and mileage, then explain the dealership warranty and delivery policies.' },
];

const targetName = process.argv[2];
assert.ok(Object.hasOwn(targets, targetName), 'Usage: node run-buyer-readiness.mjs staging|production /absolute/new/output-directory');
const output = process.argv[3];
assert.ok(output && path.isAbsolute(output), 'Provide an absolute, new output directory.');
process.umask(0o077);
await fs.mkdir(output, { mode: 0o700 });
const target = targets[targetName];
const manifest = await jsonRequest(`https://api.loomai.pro/api/public/ai-workspace/installations/${target.installation}/manifest`, {
  headers: { Origin: target.origin },
});
assert.equal(manifest.installationId, target.installation);
assert.equal(manifest.connection.runtimeBaseUrl, target.runtime, 'Unexpected assignment; review before sending questions.');
const manifestFile = { installationId: manifest.installationId, assignmentRevision: manifest.assignmentRevision, runtime: target.runtime };
await save('assignment', manifestFile);
const results = [];
for (const session of [...new Set(cases.map(item => item.session))]) {
  const identity = await jsonRequest(new URL(manifest.connection.anonymousBootstrap.url, target.runtime), {
    method: 'POST', headers: { Origin: target.origin, 'Content-Type': 'application/json' }, body: '{}',
  });
  assert.equal(identity.deploymentId, 'dep-3c4dbe4f');
  assert.ok(identity.token);
  const conversationId = `chat-${crypto.randomUUID()}`;
  for (const scenario of cases.filter(item => item.session === session)) {
    const request = { query: scenario.query, mode: 'executor', position: 'landing', conversationId,
      context: { dealershipId: 'dealer-demo-001', sourceMode: 'DEALERSHIP_INVENTORY',
        workspaceInstallationId: manifest.installationId, assignmentRevision: manifest.assignmentRevision } };
    const route = manifest.connection.routes.conversationMessageUrlTemplate.replace('{conversationId}', encodeURIComponent(conversationId));
    const started = Date.now();
    const response = await jsonRequest(new URL(route, target.runtime), {
      method: 'POST', headers: { Origin: target.origin, 'Content-Type': 'application/json',
        Authorization: `Bearer ${identity.token}`, 'Idempotency-Key': `buyer-readiness-${crypto.randomUUID()}` },
      body: JSON.stringify(request),
    });
    assert.equal(response.conversationId, conversationId);
    await save(scenario.id, { at: new Date().toISOString(), request, response });
    assert.ok(!String(response.fallbackReason || '').startsWith('PROVIDER_BUDGET'), 'Provider allowance interrupted; stop.');
    const executed = (response.actions || []).filter(action => action.actionResult?.success === true);
    assert.ok(executed.every(action => ['dealership_search_inventory', 'dealership_get_vehicle', 'dealership_compare_vehicles'].includes(action.action)), 'Unexpected executed action; stop for review.');
    const row = { ...scenario, type: response.type, success: response.success, answer: response.answer,
      sourceCount: (response.sources || []).length, durationMs: Date.now() - started, verdict: 'PENDING_MANUAL_REVIEW' };
    results.push(row);
    await save('progress', { targetName, ...manifestFile, results });
    console.log(JSON.stringify(row));
  }
}
await save('complete', { completedAt: new Date().toISOString(), targetName, ...manifestFile, results });
console.log(JSON.stringify({ completed: results.length, semanticReview: 'REQUIRED', output }));

async function jsonRequest(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(180_000) });
  assert.equal(response.status, 200, `${new URL(url).pathname}: HTTP ${response.status}`);
  return response.json();
}

async function save(name, value) {
  await fs.writeFile(path.join(output, `${name}.json`), JSON.stringify(value, null, 2), { mode: 0o600 });
}
