import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

async function load(path) {
  let source = await readFile(new URL(path, import.meta.url), 'utf8')
  if (path.endsWith('/customerAuthReturn.ts')) {
    const navigation = ts.transpileModule(await readFile(new URL('../src/account/accountNavigation.ts', import.meta.url), 'utf8'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
    source = source.replace("'../account/accountNavigation'", `'data:text/javascript;base64,${Buffer.from(navigation).toString('base64')}'`)
  }
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  return import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
}
const { isCustomerSession, isCustomerAuthenticationMode } = await load('../src/auth/customerSession.ts')
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://api.example.test' } }
const { platformCredentials, platformRequest, setPlatformAccessTokenProvider } = await load('../src/api/platformApi.ts')
delete globalThis.window
const { requireDeskAuthorization, requireDeskCallback } = await load('../src/auth/deskOAuthPolicy.ts')
const { authorizeDeskOnce } = await load('../src/auth/deskAuthorizationAttempt.ts')
const { rememberCustomerAuthReturn, customerAuthReturn, consumeCustomerAuthReturn, identityEmailRedirect, identitySignInPath, productStartPath, kitSetupCode } = await load('../src/auth/customerAuthReturn.ts')
const { deskSignInUrl, deskIdentityClient, identityMethod, emailCallbackError } = await load('../src/auth/identityNavigation.ts')
const { readEmailLink, verifyEmailLinkOnce } = await load('../src/auth/emailLinkVerification.ts')
const config = { enabled: true, deskClients: [{ clientId: 'desk-prod', redirectUris: ['https://desk.example.test/auth/callback'] }] }
const details = { authorization_id: 'auth-1', client: { id: 'desk-prod' }, redirect_uri: 'https://desk.example.test/auth/callback', scope: 'openid profile email' }

test('new workspace creation and a bookmarked kit route retain only exact local kit intent', async () => {
  const path = '/kits/new/dealership-ai-experience'
  assert.equal(kitSetupCode(path), 'dealership-ai-experience')
  assert.equal(productStartPath('', path), path)
  assert.equal(productStartPath('?kit=ai-customer-experience', path), '/kits/new/ai-customer-experience')
  for (const value of ['https://other.test/kits/new/dealership-ai-experience', '//kits/new/a', '/kits/new/a/extra', '/kits/new/../a']) {
    assert.equal(kitSetupCode(value), null)
    assert.equal(productStartPath('', value), '/start')
  }
  const onboarding = await readFile(new URL('../src/pages/CustomerOnboardingPage.tsx', import.meta.url), 'utf8')
  assert.match(onboarding, /productStartPath\(location\.search, location\.pathname\)/)
  const app = await readFile(new URL('../src/App.tsx', import.meta.url), 'utf8')
  assert.match(app, /kitSetupCode\(location\.pathname\)/)
})

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

test('an already-cancelled Platform command never resolves credentials or dispatches', async () => {
  const originalFetch = globalThis.fetch
  let credentials = 0, dispatched = 0
  setPlatformAccessTokenProvider(async () => { credentials++; return 'customer-token' })
  globalThis.fetch = async () => { dispatched++; throw new Error('Unexpected network dispatch') }
  const controller = new AbortController(); controller.abort()
  try {
    await assert.rejects(platformRequest('/api/customer/staff-access', { method: 'POST', signal: controller.signal }), { name: 'AbortError' })
    assert.equal(credentials, 0); assert.equal(dispatched, 0)
  } finally { globalThis.fetch = originalFetch; setPlatformAccessTokenProvider(null) }
})

test('cancellation while credentials resolve cannot dispatch with the next customer token', async () => {
  const originalFetch = globalThis.fetch
  let release, started, dispatched = 0
  const waiting = new Promise(resolve => { release = resolve })
  const ready = new Promise(resolve => { started = resolve })
  setPlatformAccessTokenProvider(async () => { started(); await waiting; return 'next-customer-token' })
  globalThis.fetch = async () => { dispatched++; throw new Error('Unexpected network dispatch') }
  const controller = new AbortController()
  try {
    const pending = platformRequest('/api/customer/staff-access', { method: 'POST', body: '{}', signal: controller.signal })
    await ready; controller.abort(); release()
    await assert.rejects(pending, { name: 'AbortError' })
    assert.equal(dispatched, 0)
  } finally { release(); globalThis.fetch = originalFetch; setPlatformAccessTokenProvider(null) }
})

test('an uncancelled Platform request still uses its resolved credential and completes', async () => {
  const originalFetch = globalThis.fetch
  const controller = new AbortController()
  setPlatformAccessTokenProvider(async () => 'current-customer-token')
  globalThis.fetch = async (url, init) => {
    assert.equal(url, 'https://api.example.test/api/customer/staff-access')
    assert.equal(init.headers.Authorization, 'Bearer current-customer-token')
    assert.equal(init.credentials, 'omit'); assert.equal(init.signal.aborted, false)
    return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  try {
    assert.deepEqual(await platformRequest('/api/customer/staff-access', { signal: controller.signal }), [])
  } finally { globalThis.fetch = originalFetch; setPlatformAccessTokenProvider(null) }
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
    assert.equal(consumeCustomerAuthReturn(), '/staff/sign-in?link=customer')
    storage.set('loomai-console-auth-return', JSON.stringify({ purpose: 'customer', createdAt: Date.now(), next: 'https://evil.test' }))
    assert.equal(consumeCustomerAuthReturn(), '/start')
    storage.set('loomai-console-auth-return', JSON.stringify({ purpose: 'desk', createdAt: Date.now() - 3600001, authorizationId: 'expired' }))
    assert.equal(customerAuthReturn(), null)
  } finally { delete globalThis.window }
})

test('product selection survives email verification but cannot become an arbitrary redirect', () => {
  const storage = new Map()
  globalThis.window = { location: { pathname: '/sign-up', search: '?product=engage&next=https://evil.test' },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) } }
  try {
    rememberCustomerAuthReturn(false)
    assert.equal(consumeCustomerAuthReturn(), '/start?product=engage')
    window.location.search = '?product=https://evil.test'
    rememberCustomerAuthReturn(false)
    assert.equal(consumeCustomerAuthReturn(), '/start')
  } finally { delete globalThis.window }
})

test('kit selection survives same-device and email-link return without allowing arbitrary URLs', () => {
  const storage = new Map()
  globalThis.window = { location: { origin: 'https://console.example.test', pathname: '/sign-up', search: '?kit=dealership-ai-experience' },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) } }
  try {
    assert.equal(productStartPath(window.location.search), '/kits/new/dealership-ai-experience')
    rememberCustomerAuthReturn(false)
    assert.equal(consumeCustomerAuthReturn(), '/kits/new/dealership-ai-experience')
    assert.equal(identityEmailRedirect(), 'https://console.example.test/auth/callback?kit=dealership-ai-experience')
    window.location.pathname = '/auth/callback'
    assert.equal(consumeCustomerAuthReturn(), '/kits/new/dealership-ai-experience')
    window.location.search = '?kit=https://evil.test'
    assert.equal(productStartPath(window.location.search), '/start')
    assert.equal(consumeCustomerAuthReturn(), '/')
    window.location.search = '?kit=..%2Fforeign'
    assert.equal(productStartPath(window.location.search), '/start')
    assert.equal(consumeCustomerAuthReturn(), '/')
  } finally { delete globalThis.window }
})

test('account sections survive email-link return with safe Brain context but no arbitrary destination', () => {
  const storage = new Map()
  const destination = '/account/team?fromBrain=dep-a&targetProfileId=staging'
  globalThis.window = { location: { origin: 'https://console.example.test', pathname: '/sign-in', search: `?account_return=${encodeURIComponent(destination)}` },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) } }
  try {
    rememberCustomerAuthReturn(false)
    assert.equal(customerAuthReturn().accountReturn, destination)
    const redirect = new URL(identityEmailRedirect())
    assert.equal(redirect.searchParams.get('account_return'), destination)
    window.location.pathname = '/auth/callback'
    window.location.search = redirect.search
    assert.equal(consumeCustomerAuthReturn(), destination)
    assert.equal(consumeCustomerAuthReturn(), destination, 'cross-device callback uses only validated local account route')
    for (const value of ['https://evil.test/account/team', '//evil.test/account/team', '/account/team/extra']) {
      window.location.search = `?account_return=${encodeURIComponent(value)}`
      rememberCustomerAuthReturn(false)
      assert.equal(customerAuthReturn().accountReturn, undefined)
      assert.equal(consumeCustomerAuthReturn(), '/start')
    }
    storage.set('loomai-console-auth-return', JSON.stringify({ purpose: 'customer', createdAt: Date.now(), accountReturn: 'https://evil.test' }))
    assert.equal(customerAuthReturn(), null)
  } finally { delete globalThis.window }
})

test('Desk email links return to a fresh Desk entry without creating Console membership or replaying an old OAuth callback', () => {
  const storage = new Map()
  globalThis.window = { location: { origin: 'https://console.example.test', pathname: '/auth/desk/sign-in', search: '?method=email&next=https://evil.test' },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) } }
  try {
    rememberCustomerAuthReturn(false)
    assert.equal(customerAuthReturn().purpose, 'desk-entry')
    assert.equal(identityEmailRedirect(), 'https://console.example.test/auth/callback?flow=desk')
    assert.equal(identityEmailRedirect(true), 'https://console.example.test/auth/recovery?flow=desk')
    assert.equal(identitySignInPath(), '/auth/desk/sign-in')
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/sign-in')
    window.location.pathname = '/auth/desk/sign-in/desk-staging'
    rememberCustomerAuthReturn(false)
    assert.equal(identityEmailRedirect(), 'https://console.example.test/auth/callback?flow=desk&desk_client=desk-staging')
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/sign-in/desk-staging')
    window.location.pathname = '/auth/desk/sign-in'
    window.location.search = '?flow=desk&next=https://evil.test'
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/sign-in')
    window.location.search = '?flow=desk&desk_client=desk-staging'
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/sign-in/desk-staging')
    window.location.search = '?flow=desk&desk_client=https://evil.test'
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/sign-in')
    assert.equal(consumeCustomerAuthReturn(), '/auth/desk/sign-in')
    window.location.search = '?flow=evil'
    assert.equal(consumeCustomerAuthReturn(), '/')
    assert.equal(identitySignInPath(), '/sign-in')
  } finally { delete globalThis.window }
})

test('Desk entry is explicitly configured and trusted, not selected from arbitrary input or client order', () => {
  const trusted = { ...config, deskSignInUrl: 'https://desk.example.test/' }
  assert.equal(deskSignInUrl(trusted), 'https://desk.example.test/oauth2/authorization/supabase')
  assert.throws(() => deskSignInUrl(config), /not configured/)
  for (const url of ['https://evil.test/', 'https://desk.example.test/path', 'https://desk.example.test/?next=evil',
    'https://user@desk.example.test/', 'http://desk.example.test/', 'https://desk.example.test/#token']) {
    assert.throws(() => deskSignInUrl({ ...trusted, deskSignInUrl: url }))
  }
  assert.equal(identityMethod('?method=email'), 'email')
  assert.equal(identityMethod('?method=https://evil.test'), 'login')
  assert.equal(emailCallbackError('?error=access_denied', ''), 'This email link could not be verified. Request a new link.')
  assert.equal(emailCallbackError('', '#error=access_denied&error_description=Link+expired'), 'Link expired')
  assert.equal(emailCallbackError('?code=opaque', ''), null)
  const shared = { ...trusted, deskClients: [...trusted.deskClients,
    { clientId: 'desk-staging', redirectUris: ['https://stage.example.test/login/oauth2/code/supabase'] }] }
  assert.equal(deskSignInUrl(shared, 'desk-staging'), 'https://stage.example.test/oauth2/authorization/supabase')
  assert.throws(() => deskSignInUrl(shared, 'untrusted'), /not configured/)
  assert.equal(deskIdentityClient('/auth/desk/sign-in/desk-staging'), 'desk-staging')
  assert.throws(() => deskIdentityClient('/auth/desk/sign-in/https://evil.test'))
})

test('email token hashes use the provider verifier once without requiring browser-local PKCE state', async () => {
  const link = readEmailLink('?token_hash=one-use-fixture&type=email', 'email')
  let calls = 0
  const client = { auth: { verifyOtp: async input => {
    calls++; assert.deepEqual(input, { token_hash: 'one-use-fixture', type: 'email' })
    return { data: { user: { id: 'verified-user' }, session: { user: { id: 'verified-user' } } }, error: null }
  } } }
  const first = verifyEmailLinkOnce(client, 'https://auth.example.test', link)
  assert.equal(first, verifyEmailLinkOnce(client, 'https://auth.example.test', link))
  assert.equal(await first, 'verified-user'); assert.equal(calls, 1)
  assert.equal(readEmailLink('?code=pkce-legacy', 'email'), null)
  assert.throws(() => readEmailLink('?token_hash=fixture&type=recovery', 'email'))
  const denied = { auth: { verifyOtp: async () => ({ data: {}, error: new Error('Link expired') }) } }
  await assert.rejects(verifyEmailLinkOnce(denied, 'https://auth.example.test', { tokenHash: 'expired', type: 'email' }), /Link expired/)
  await assert.rejects(verifyEmailLinkOnce(client, 'https://auth.example.test', { tokenHash: 'expired', type: 'email' }), /Link expired/)
})
