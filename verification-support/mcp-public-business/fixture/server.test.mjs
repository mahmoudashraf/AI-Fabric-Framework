import assert from 'node:assert/strict'
import { fork } from 'node:child_process'
import { createHash } from 'node:crypto'
import { once } from 'node:events'
import { after, before, test } from 'node:test'
import { fileURLToPath } from 'node:url'

let child, base
before(async () => {
  child = fork(fileURLToPath(new URL('server.mjs', import.meta.url)), { silent: true, env: { ...process.env,
    PORT: '0', FIXTURE_ORIGIN: 'https://fixture.example.test', FIXTURE_OAUTH_CALLBACK: 'https://console.example.test/api/mcp/oauth/callback',
    FIXTURE_ADMIN_KEY: 'fixture-admin', FIXTURE_BUSINESS_KEY: 'business-key',
    FIXTURE_CLIENT_ID: 'fixture-client', FIXTURE_CLIENT_SECRET: 'fixture-client-secret' } })
  const [message] = await once(child, 'message')
  base = `http://127.0.0.1:${message.port}`
})
after(async () => { if (child) { child.kill(); await once(child, 'exit') } })
const json = async (path, body, headers = {}) => {
  const response = await fetch(base + path, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...headers }, body: body ? JSON.stringify(body) : undefined })
  return { status: response.status, value: await response.json() }
}
test('public tools require no credentials; private tools issue an authorization challenge', async () => {
  const request = { jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} }
  const publicResult = await json('/public/mcp', request)
  assert.equal(publicResult.status, 200); assert.equal(publicResult.value.result.tools[0].name, 'search_guides')
  assert.equal((await json('/business/mcp', request)).status, 401)
})
test('business catalogue pagination and receiver ownership are enforced', async () => {
  const headers = { Authorization: 'Bearer business-key' }
  const first = await json('/business/mcp', { jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} }, headers)
  const second = await json('/business/mcp', { jsonrpc: '2.0', id: 2, method: 'tools/list', params: { cursor: first.value.result.nextCursor } }, headers)
  assert.equal(first.value.result.tools.length + second.value.result.tools.length, 3)
  const denied = await json('/business/mcp', { jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'cancel_subscription', arguments: { actorId: 'fixture-a', subscriptionId: 'sub-b' } } }, headers)
  assert.equal(denied.value.result.isError, true)
  const allowed = await json('/business/mcp', { jsonrpc: '2.0', id: 4, method: 'tools/call', params: { name: 'cancel_subscription', arguments: { actorId: 'fixture-a', subscriptionId: 'sub-a' } } }, headers)
  assert.equal(allowed.value.result.structuredContent.status, 'CANCELLATION_SCHEDULED')
  const evidence = await json('/admin/evidence', null, { 'X-Fixture-Admin-Key': 'fixture-admin' })
  assert.equal(evidence.value.counts.cancellations, 1); assert.equal(evidence.value.counts.deniedMutations, 1)
})
test('business OAuth requires PKCE and resource binding and rotates refresh tokens', async () => {
  const callback = 'https://console.example.test/api/mcp/oauth/callback'
  const registration = await json('/oauth/register', { redirect_uris: [callback], token_endpoint_auth_method: 'none' })
  const verifier = 'v'.repeat(64), challenge = createHash('sha256').update(verifier).digest('base64url')
  const params = new URLSearchParams({ response_type: 'code', client_id: registration.value.client_id, redirect_uri: callback,
    state: 'fixture-state', code_challenge: challenge, code_challenge_method: 'S256', resource: 'https://fixture.example.test/business/mcp', approve: 'yes' })
  const authorization = await fetch(base + '/oauth/authorize?' + params, { redirect: 'manual' })
  assert.equal(authorization.status, 302)
  const code = new URL(authorization.headers.get('location')).searchParams.get('code')
  const token = await json('/oauth/token', { grant_type: 'authorization_code', code, code_verifier: verifier,
    client_id: registration.value.client_id, redirect_uri: callback, resource: 'https://fixture.example.test/business/mcp' })
  assert.equal(token.status, 200); assert.ok(token.value.access_token)
  assert.equal((await json('/business/mcp', { jsonrpc: '2.0', id: 8, method: 'tools/list' }, { Authorization: `Bearer ${token.value.access_token}` })).status, 200)
  const renewed = await json('/oauth/token', { grant_type: 'refresh_token', refresh_token: token.value.refresh_token, resource: 'https://fixture.example.test/business/mcp' })
  assert.equal(renewed.status, 200); assert.notEqual(renewed.value.refresh_token, token.value.refresh_token)
  assert.equal((await json('/oauth/token', { grant_type: 'refresh_token', refresh_token: token.value.refresh_token, resource: 'https://fixture.example.test/business/mcp' })).status, 400)
})
test('machine OAuth authenticates the company client without a visitor account', async () => {
  const token = await json('/oauth/token', { grant_type: 'client_credentials', client_id: 'fixture-client',
    client_secret: 'fixture-client-secret', resource: 'https://fixture.example.test/business/mcp' })
  assert.equal(token.status, 200); assert.ok(token.value.access_token); assert.equal(token.value.refresh_token, undefined)
  const denied = await json('/oauth/token', { grant_type: 'client_credentials', client_id: 'fixture-client',
    client_secret: 'wrong-secret', resource: 'https://fixture.example.test/business/mcp' })
  assert.equal(denied.status, 401)
})
