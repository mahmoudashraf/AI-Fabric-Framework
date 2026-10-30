import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createAcmeFixtureServer } from './server.mjs';

const key = 'unit-test-only-acme-server-key';
const observations = JSON.parse(readFileSync(new URL('../acme-read-observations.json', import.meta.url), 'utf8'));

test('requires an explicit dedicated key before starting', () => {
  assert.throws(() => createAcmeFixtureServer(), /dedicated ACME_SUPPORT_API_KEY/);
  assert.throws(() => createAcmeFixtureServer('short'), /dedicated ACME_SUPPORT_API_KEY/);
});

test('serves only authenticated read-only fictional observations and approved documents', async t => {
  const server = createAcmeFixtureServer(key);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;
  const headers = { 'X-ACME-SUPPORT-SERVICE-KEY': key };
  for (const [path, action] of [
    ['/api/support/workspace-status', 'acme_get_workspace_status'],
    ['/api/support/service-status', 'acme_get_service_status'],
  ]) {
    assert.equal((await fetch(base + path)).status, 401);
    assert.equal((await fetch(base + path, { headers: { 'X-ACME-SUPPORT-SERVICE-KEY': 'incorrect-credential' } })).status, 401);
    const response = await fetch(base + path, { headers });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('x-acme-simulation'), 'true');
    assert.equal(response.headers.get('access-control-allow-origin'), null);
    assert.deepEqual(await response.json(), { simulation: true, ...observations[action] });
    assert.equal((await fetch(base + path, { method: 'POST', headers })).status, 405);
    assert.equal((await fetch(base + path + '?workspaceId=another-customer', { headers })).status, 400);
  }
  assert.deepEqual(await (await fetch(base + '/healthz')).json(), { status: 'UP', simulation: true });
  const document = await fetch(base + '/documents/01-project-and-connector-setup.txt', { headers });
  assert.equal(document.status, 200);
  assert.match(await document.text(), /fictional/i);
  assert.equal((await fetch(base + '/documents/private-handoff.txt', { headers })).status, 404);
  assert.equal((await fetch(base + '/documents/%2e%2e%2fserver.mjs', { headers })).status, 404);
  assert.equal((await fetch(base + '/unrecognized', { headers })).status, 404);
});
