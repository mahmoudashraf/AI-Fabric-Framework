import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

async function load(path) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  return import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
}
const { isCustomerSession, isCustomerAuthenticationMode } = await load('../src/auth/customerSession.ts')
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://api.example.test' } }
const { platformCredentials, setPlatformAccessTokenProvider } = await load('../src/api/platformApi.ts')
delete globalThis.window
const { requireDeskAuthorization, requireDeskCallback } = await load('../src/auth/deskOAuthPolicy.ts')
const { authorizeDeskOnce } = await load('../src/auth/deskAuthorizationAttempt.ts')
const { rememberCustomerAuthReturn, customerAuthReturn, consumeCustomerAuthReturn } = await load('../src/auth/customerAuthReturn.ts')
const config = { enabled: true, deskClients: [{ clientId: 'desk-prod', redirectUris: ['https://desk.example.test/auth/callback'] }] }
const details = { authorization_id: 'auth-1', client: { id: 'desk-prod' }, redirect_uri: 'https://desk.example.test/auth/callback', scope: 'openid profile email' }

test('customer predicate accepts verified session modes without admitting API keys, staff or unscoped users', () => {
  const customer = { authenticated: true, actorId: 'owner', customerId: 'customer', role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE' }
  assert.equal(isCustomerSession(customer), true)
  assert.equal(isCustomerSession({ ...customer, authenticationMode: 'SESSION' }), true)
  for (const override of [{ role: 'PLATFORM_ADMIN' }, { customerId: null }, { actorId: null }, { authenticated: false }, { authenticationMode: 'API_KEY' }]) {
    assert.equal(isCustomerSession({ ...customer, ...override }), false)
  }
  assert.equal(isCustomerAuthenticationMode('SUPABASE'), true)
  assert.equal(isCustomerAuthenticationMode('API_KEY'), false)
})

test('token authentication cannot mix staff keys/cookies and refreshes from its SDK provider on each request', async () => {
  let token = 'current-token'
  setPlatformAccessTokenProvider(async () => token)
  assert.deepEqual(await platformCredentials('/api/deployments', 'staff-key'), { credentials: 'omit', headers: { Authorization: 'Bearer current-token' } })
  token = 'refreshed-token'
  assert.equal((await platformCredentials('/api/platform/auth/session', 'staff-key')).headers.Authorization, 'Bearer refreshed-token')
  await assert.rejects(platformCredentials('https://external.test/api', 'staff-key'))
  await assert.rejects(platformCredentials('//external.test/api', 'staff-key'))
  assert.deepEqual(await platformCredentials('/api/platform/auth/customer-config', 'staff-key', 'none'), { credentials: 'omit', headers: {} })
  assert.deepEqual(await platformCredentials('/api/platform/auth/customer-link', 'staff-key', 'local'), { credentials: 'include', headers: {} })
  setPlatformAccessTokenProvider(null)
  assert.deepEqual(await platformCredentials('/api/deployments', 'staff-key'), { credentials: 'include', headers: { 'X-PLATFORM-API-KEY': 'staff-key' } })
})

test('an expired customer SDK session must not fall back to a staff credential', async () => {
  setPlatformAccessTokenProvider(async () => { throw new Error('Session expired') })
  await assert.rejects(platformCredentials('/api/deployments', 'staff-key'), /Session expired/)
  setPlatformAccessTokenProvider(async () => null)
  await assert.rejects(platformCredentials('/api/deployments', 'staff-key'), /Customer session expired/)
  setPlatformAccessTokenProvider(null)
})

test('Desk authorization uses exact client, request and callback bindings and only identity scopes', () => {
  requireDeskAuthorization(config, details, 'auth-1')
  for (const changed of [{ client: { id: 'unconfigured' } }, { authorization_id: 'other' },
    { redirect_uri: 'https://desk.example.test/auth/callback/extra' }, { redirect_uri: 'https://evil.test/auth/callback' },
    { scope: 'openid email write:deployments' }]) {
    assert.throws(() => requireDeskAuthorization(config, { ...details, ...changed }, 'auth-1'))
  }
  assert.throws(() => requireDeskAuthorization({ ...config, enabled: false }, details, 'auth-1'))
})

test('returning consent and approve/deny responses only redirect to configured exact callbacks', () => {
  assert.equal(requireDeskCallback(config, 'https://desk.example.test/auth/callback?code=one-use&state=pkce'), 'https://desk.example.test/auth/callback?code=one-use&state=pkce')
  for (const redirect of ['https://evil.test/auth/callback?code=x', 'https://desk.example.test.evil.test/auth/callback',
    'https://desk.example.test/auth/callback/extra', 'https://user@desk.example.test/auth/callback',
    'javascript:alert(1)', 'https://desk.example.test/auth/callback#token']) assert.throws(() => requireDeskCallback(config, redirect))
})

test('first-party auto-approval runs once across remounts and never retries a failed or reloaded attempt', async () => {
  const storage = new Map()
  globalThis.sessionStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) }
  try {
    let calls = 0
    const approve = async () => { calls++; return 'https://desk.example.test/auth/callback?code=one-use' }
    const first = authorizeDeskOnce('strict-mode', 'issuer:user:desk', approve)
    const remount = authorizeDeskOnce('strict-mode', 'issuer:user:desk', approve)
    assert.equal(first, remount)
    await Promise.all([first, remount]); assert.equal(calls, 1)
    await assert.rejects(authorizeDeskOnce('strict-mode', 'issuer:other:desk', approve), /identity changed/)
    const fail = async () => { calls++; throw new Error('Provider failed') }
    await assert.rejects(authorizeDeskOnce('failed', 'issuer:user:desk', fail), /Provider failed/)
    await assert.rejects(authorizeDeskOnce('failed', 'issuer:user:desk', fail), /Provider failed/)
    assert.equal(calls, 2)
    storage.set('loomai-desk-authorization-attempt:reloaded', 'ATTEMPTED')
    await assert.rejects(authorizeDeskOnce('reloaded', 'issuer:user:desk', approve), /already attempted/)
    assert.equal(calls, 2)
  } finally { delete globalThis.sessionStorage }
})

test('verification returns only to the local linking or central Desk path, never an arbitrary next URL', () => {
  const storage = new Map()
  globalThis.window = { location: { pathname: '/auth/desk/authorize', search: '?authorization_id=opaque-request' },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) } }
  try {
    rememberCustomerAuthReturn(false)
    assert.equal(customerAuthReturn().purpose, 'desk')
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/authorize?authorization_id=opaque-request')
    rememberCustomerAuthReturn(true)
    assert.equal(customerAuthReturn().purpose, 'link')
    assert.equal(consumeCustomerAuthReturn(), '/')
    storage.set('loomai-console-auth-return', JSON.stringify({ purpose: 'customer', createdAt: Date.now(), next: 'https://evil.test' }))
    assert.equal(consumeCustomerAuthReturn(), '/')
    storage.set('loomai-console-auth-return', JSON.stringify({ purpose: 'desk', createdAt: Date.now() - 3600001, authorizationId: 'expired' }))
    assert.equal(customerAuthReturn(), null)
  } finally { delete globalThis.window }
})
