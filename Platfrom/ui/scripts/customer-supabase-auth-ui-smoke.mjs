import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const fixture = `<!doctype html><html><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/auth-test-entry.jsx"></script></body></html>`
const fixtureEntry = `import React from 'react';
import {createRoot} from 'react-dom/client';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {PlatformAuthProvider,usePlatformAuth,startCustomerIdentityLink} from '/src/auth/PlatformAuthProvider.tsx';
function Fixture(){const a=usePlatformAuth();return React.createElement('div',{},
React.createElement('p',{'data-testid':'identity'},a.isLoading?'loading':a.session?.authenticationMode??(a.error?'denied':'anonymous')),
React.createElement('p',{'data-testid':'actor'},a.session?.actorId??''),
React.createElement('button',{onClick:()=>a.signInCustomer('agent@example.test','TestPassword')},'Customer login'),
React.createElement('button',{onClick:()=>a.signInWithPassword('staff@example.test','TestPassword')},'Staff login'),
React.createElement('button',{onClick:()=>a.setApiKey('staff-fixture-key')},'Staff key'),
React.createElement('button',{onClick:()=>a.signOut()},'Logout'),
React.createElement('button',{onClick:()=>a.supabase.auth.refreshSession()},'Refresh'),
React.createElement('button',{onClick:async()=>{startCustomerIdentityLink();await a.supabase.auth.signInWithPassword({email:'agent@example.test',password:'TestPassword'});await a.linkCustomer()}},'Link identity'))}
createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client:new QueryClient()},React.createElement(PlatformAuthProvider,{},React.createElement(Fixture))));`
let server, browser
const cacheDir = await mkdtemp(join(tmpdir(), 'loomai-auth-smoke-'))
const results = []
const timestamp = Math.floor(Date.now() / 1000)
const jwt = suffix => `${Buffer.from(JSON.stringify({ alg: 'ES256', typ: 'JWT' })).toString('base64url')}.${Buffer.from(JSON.stringify({ sub: 'subject-agent', iss: 'https://auth.example.test/auth/v1', role: 'authenticated', aud: 'authenticated', exp: timestamp + 3600, iat: timestamp, suffix })).toString('base64url')}.fixture-signature`
const user = { id: 'subject-agent', aud: 'authenticated', role: 'authenticated', email: 'agent@example.test', email_confirmed_at: '2026-01-01T00:00:00Z', app_metadata: { provider: 'email', providers: ['email'] }, user_metadata: {}, identities: [], created_at: '2026-01-01T00:00:00Z' }
const sbSession = suffix => ({ access_token: jwt(suffix), refresh_token: 'fixture-refresh-token', token_type: 'bearer', expires_in: 3600, expires_at: timestamp + 3600, user })
const anonymous = { enabled: true, authenticated: false, headerName: 'X-PLATFORM-API-KEY', sessionAuthEnabled: true, apiKeyAuthEnabled: true }
const customer = { ...anonymous, authenticated: true, role: 'CUSTOMER_ADMIN', actorId: 'stable-owner@example.test', authenticationMode: 'SUPABASE', customerId: 'customer-fixture', customerName: 'Fixture' }
const staff = { ...anonymous, authenticated: true, role: 'PLATFORM_ADMIN', actorId: 'staff@example.test', authenticationMode: 'SESSION' }
const config = { enabled: true, projectUrl: 'https://auth.example.test', publicKey: 'sb_publishable_test_public_key', deskClients: [{ clientId: 'desk-fixture', redirectUris: ['https://desk.example.test/auth/callback'] }] }
const details = { authorization_id: 'request-one', redirect_uri: 'https://desk.example.test/auth/callback', client: { id: 'desk-fixture', name: 'LoomAI Desk' }, user: { id: user.id, email: user.email }, scope: 'openid email profile' }

async function scenario(name, action, options = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, serviceWorkers: 'block' })
  const calls = [], errors = []
  let local = anonymous, refreshed = false, linked = false, onboarded = false, enrolled = false
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  page.setDefaultTimeout(15000)
  await context.addInitScript(({ session, origin }) => {
    window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: origin }
    if (session && !sessionStorage.getItem('fixture-seeded')) {
      localStorage.setItem('loomai-console-supabase', JSON.stringify(session)); sessionStorage.setItem('fixture-seeded', '1')
    }
  }, { session: options.seed ? sbSession('initial') : null, origin: server.resolvedUrls.local[0].replace(/\/$/, '') })
  await context.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), headers = req.headers()
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (url.hostname === 'desk.example.test') {
      calls.push({ path: url.pathname, method: req.method(), deskCallback: true })
      if (options.delayedCustomerAuthorizationTransition) await new Promise(resolve => setTimeout(resolve, 1200))
      return route.fulfill({ contentType: 'text/plain', body: 'Desk callback received' })
    }
    if (url.hostname === 'auth.example.test') {
      calls.push({ path: url.pathname, method: req.method(), authorization: headers.authorization, body: req.postDataJSON(), query: url.search })
      if (url.pathname === '/auth/v1/token') { refreshed = url.searchParams.get('grant_type') === 'refresh_token'; return respond(sbSession(refreshed ? 'refreshed' : 'signed-in')) }
      if (url.pathname === '/auth/v1/signup') return respond({ user: { ...user, email_confirmed_at: null }, session: null })
      if (url.pathname === '/auth/v1/recover') return respond({})
      if (url.pathname === '/auth/v1/user') return respond(user)
      if (url.pathname === '/auth/v1/logout') return respond({})
      if (url.pathname.endsWith('/consent')) {
        if (options.approvalFailure && req.postDataJSON().action === 'approve') return respond({ message: 'Desk consent unavailable' }, 503)
        return respond({ redirect_url: `https://desk.example.test/auth/callback?${req.postDataJSON().action === 'deny' ? 'error=access_denied' : 'code=one-use'}&state=fixture` })
      }
      if (url.pathname.includes('/oauth/authorizations/')) {
        const reads = calls.filter(call => call.path === url.pathname && call.method === 'GET').length
        if (options.oneUseDetails && reads > 1) return respond({ message: 'authorization request cannot be processed' }, 400)
        return respond(options.returning ? { redirect_url: 'https://desk.example.test/auth/callback?code=already-consented&state=fixture' } : { ...details, ...options.details })
      }
      throw new Error(`Unexpected Supabase route ${url.pathname}`)
    }
    if (url.origin !== new URL(server.resolvedUrls.local[0]).origin) { errors.push(`Unmocked remote ${url.origin}`); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    calls.push({ path: url.pathname, method: req.method(), authorization: headers.authorization, cookie: headers.cookie, key: headers['x-platform-api-key'] })
    if (url.pathname.endsWith('/customer-config')) return options.backendUnavailable ? respond({ message: 'Unavailable' }, 503) : respond(config)
    if (url.pathname.endsWith('/customer-onboarding')) {
      assert.ok(headers.authorization?.startsWith('Bearer '))
      if (req.method() === 'POST') {
        assert.deepEqual(req.postDataJSON(), { workspaceName: 'My company', displayName: 'New customer' })
        onboarded = true
      }
      return respond({ status: onboarded ? 'READY' : options.linkRequired ? 'LINK_REQUIRED' : 'CREATE_WORKSPACE',
        customerId: onboarded ? customer.customerId : null, workspaceName: onboarded ? 'My company' : null })
    }
    if (url.pathname === '/api/customer-self-service/enrollment') { assert.ok(onboarded); enrolled = true; return route.fulfill({ status: 200, body: '' }) }
    if (url.pathname === '/api/customer-self-service/catalogue' && options.publicTemplate && enrolled) return respond([
      ['public-dealership-simulator-v1', 'Dealership simulator'], ['public-knowledge-workspace-v1', 'Knowledge workspace'],
    ].map(([id, name]) => ({
      id, name, templateId: id, templateVersionId: 'version', fingerprint: 'fingerprint',
      entitlement: { id: `enrollment-${id}`, offerId: id, customerId: customer.customerId, ownerUserId: 'new-owner', status: 'ACTIVE', revision: 1, expiresAt: '2099-01-01T00:00:00Z' },
      targets: [], permissions: [], settings: [], bindingSlots: [], documentAudiences: [], blockers: [], workspaceProfiles: [], externalIntegrationProfiles: [],
    })))
    if (url.pathname === '/api/deployment-operations/status') return respond({ mode: 'DISABLED', providerUsageMode: 'TEAM_AUTHORIZED', manualDeploymentsAllowed: true, serviceConfigured: false })
    if (url.pathname.endsWith('/customer-link')) { assert.equal(local.authenticationMode, 'SESSION'); assert.equal(headers.authorization, undefined); linked = true; return route.fulfill({ status: 200, body: '' }) }
    if (url.pathname.endsWith('/login')) { local = options.localCustomer ? { ...customer, authenticationMode: 'SESSION' } : staff; return respond(local) }
    if (url.pathname.endsWith('/logout')) { local = anonymous; return respond(anonymous) }
    if (url.pathname.endsWith('/session')) {
      if (options.backendUnavailable) return respond({ message: 'Unavailable' }, 503)
      if (headers.authorization) {
        assert.equal(headers['x-platform-api-key'], undefined); assert.equal(headers.cookie, undefined)
        if (options.delayedCustomerAuthorizationTransition) await new Promise(resolve => setTimeout(resolve, 400))
        return (options.deskOnly && !linked) || (options.fresh && !onboarded) ? respond({ success: false, errorCode: 'CUSTOMER_IDENTITY_NOT_LINKED', message: 'No Console membership' }, 403) : respond(customer)
      }
      return respond(headers['x-platform-api-key'] ? { ...staff, authenticationMode: 'API_KEY' } : local)
    }
    return respond([])
  })
  try { await action({ page, calls, base: server.resolvedUrls.local[0].replace(/\/$/, ''), linked: () => linked }); assert.deepEqual(errors, []); results.push(name) }
  catch (error) { console.error(JSON.stringify({ scenario: name, errors, body: (await page.locator('body').innerText()).slice(0, 1000), calls: calls.map(({ path, method }) => ({ path, method })) })); throw error }
  finally { await context.close() }
}

try {
  server = await createServer({ root, cacheDir, configFile: false, envFile: false, esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'auth-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/auth-test-entry.jsx') return '\0auth-test-entry.jsx' },
    load(id) { if (id === '\0auth-test-entry.jsx') return fixtureEntry },
    configureServer(vite) {
    vite.middlewares.use(async (req, res, next) => {
      if (req.url?.startsWith('/test-auth')) { res.setHeader('Content-Type', 'text/html'); res.end(await vite.transformIndexHtml('/test-auth', fixture)); return }
      next()
    })
  } }] })
  await server.listen()
  browser = await chromium.launch({ headless: true })
  await scenario('customer_login_refresh_and_logout', async ({ page, calls, base }) => {
    await page.goto(`${base}/test-auth`); await page.getByTestId('identity').filter({ hasText: 'anonymous' }).waitFor()
    await page.getByRole('button', { name: 'Customer login' }).click(); await page.getByTestId('identity').filter({ hasText: 'SUPABASE' }).waitFor()
    await page.getByRole('button', { name: 'Refresh', exact: true }).click()
    await page.waitForFunction(token => JSON.parse(localStorage.getItem('loomai-console-supabase') ?? 'null')?.access_token === token, jwt('refreshed'))
    await page.getByRole('button', { name: 'Logout', exact: true }).click(); await page.getByTestId('identity').filter({ hasText: 'anonymous' }).waitFor()
    assert.ok(calls.some(call => call.query === '?grant_type=refresh_token'))
    assert.ok(calls.some(call => call.authorization === `Bearer ${jwt('refreshed')}` && call.path === '/api/platform/auth/session'))
  })
  await scenario('staff_key_to_customer_credentials_do_not_mix', async ({ page, calls, base }) => {
    await page.goto(`${base}/test-auth`); await page.getByTestId('identity').filter({ hasText: 'anonymous' }).waitFor()
    await page.getByRole('button', { name: 'Staff key' }).click(); await page.getByTestId('identity').filter({ hasText: 'API_KEY' }).waitFor()
    await page.getByRole('button', { name: 'Customer login' }).click(); await page.getByTestId('identity').filter({ hasText: 'SUPABASE' }).waitFor()
    assert.ok(calls.some(call => call.key === 'staff-fixture-key'))
    assert.ok(calls.some(call => call.authorization?.startsWith('Bearer ')))
  })
  await scenario('native_customer_link_requires_existing_session_and_switches_to_supabase', async ({ page, linked, base }) => {
    await page.goto(`${base}/test-auth`); await page.getByTestId('identity').filter({ hasText: 'anonymous' }).waitFor()
    await page.getByRole('button', { name: 'Staff login' }).click(); await page.getByTestId('identity').filter({ hasText: 'SESSION' }).waitFor()
    await page.getByRole('button', { name: 'Link identity' }).click(); await page.getByTestId('identity').filter({ hasText: 'SUPABASE' }).waitFor()
    assert.equal(linked(), true)
  }, { localCustomer: true })
  await scenario('desk_only_agent_consent_reuses_supabase_without_console_membership', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/consent') && call.body.action === 'approve').length, 1)
    assert.equal(calls.filter(call => call.path === '/auth/v1/token').length, 0)
  }, { seed: true, deskOnly: true })
  await scenario('failed_oauth_is_not_retried_and_can_be_cancelled_explicitly', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('Supabase authorization failed (503). Open LoomAI Desk again to start a new request.').waitFor()
    await page.getByRole('button', { name: 'Cancel', exact: true }).click(); await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/consent') && call.body.action === 'approve').length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/consent') && call.body.action === 'deny').length, 1)
  }, { seed: true, deskOnly: true, approvalFailure: true })
  await scenario('unknown_oauth_client_is_rejected', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('This authorization request is not for a configured LoomAI Desk client.').waitFor()
    assert.equal(await page.getByRole('button', { name: 'Continue to LoomAI Desk' }).count(), 0)
    assert.equal(calls.filter(call => call.path.endsWith('/consent')).length, 0)
  }, { seed: true, details: { client: { id: 'foreign-app' } } })
  await scenario('returning_consent_automatically_uses_only_allowlisted_callback', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/consent')).length, 0)
  }, { seed: true, returning: true })
  await scenario('linked_console_owner_consumes_one_use_returning_authorization_once', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.includes('/oauth/authorizations/') && call.method === 'GET').length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/consent')).length, 0)
  }, { seed: true, returning: true, oneUseDetails: true })
  await scenario('customer_role_arrival_does_not_replay_one_use_callback_navigation', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.includes('/oauth/authorizations/') && call.method === 'GET').length, 1)
    assert.equal(calls.filter(call => call.deskCallback).length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/consent')).length, 0)
  }, { seed: true, returning: true, oneUseDetails: true, delayedCustomerAuthorizationTransition: true })
  await scenario('customer_role_arrival_does_not_replay_first_consent_callback_navigation', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.includes('/oauth/authorizations/') && call.method === 'GET').length, 1)
    assert.equal(calls.filter(call => call.deskCallback).length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/consent') && call.body.action === 'approve').length, 1)
  }, { seed: true, oneUseDetails: true, delayedCustomerAuthorizationTransition: true })
  await scenario('agent_signup_verification_uses_pkce_and_returns_to_same_central_desk_request', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByRole('button', { name: 'Create account', exact: true }).click()
    await page.getByLabel(/^Email/).fill('agent@example.test')
    await page.getByLabel(/^Password/).fill('TestPassword')
    await page.getByRole('button', { name: 'Create account', exact: true }).last().click()
    await page.getByText('Check your email to verify your account. Then return here to sign in.').waitFor()
    const signup = calls.find(call => call.path === '/auth/v1/signup')
    assert.equal(new URLSearchParams(signup.query).get('redirect_to'), `${base}/auth/callback`)
    assert.equal(signup.body.code_challenge_method, 's256')
    assert.ok(signup.body.code_challenge)
    await page.goto(`${base}/auth/callback?code=verified-email-code`)
    await page.getByText('Desk callback received').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/customer-link')).length, 0)
  }, { deskOnly: true })
  await scenario('password_recovery_uses_fixed_path_and_sdk_updates_identity_without_console_access', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByLabel(/^Email/).fill('agent@example.test')
    await page.getByRole('button', { name: 'Forgot password?' }).click()
    await page.getByText('Check your email for the password recovery link.').waitFor()
    const recovery = calls.find(call => call.path === '/auth/v1/recover')
    assert.equal(new URLSearchParams(recovery.query).get('redirect_to'), `${base}/auth/recovery`)
    await page.goto(`${base}/auth/recovery?code=recovery-code`)
    await page.getByLabel(/^New password/).fill('NewTestPassword')
    await page.getByLabel(/^Confirm new password/).fill('NewTestPassword')
    await page.getByRole('button', { name: 'Update password', exact: true }).click()
    await page.getByText('Password updated. Sign in with your new password.').waitFor()
    assert.equal(calls.filter(call => call.path === '/auth/v1/user' && call.method === 'PUT').length, 1)
  }, { deskOnly: true })
  await scenario('cancelled_login_creates_no_token_or_oauth_approval', async ({ page, calls, base }) => {
    await page.goto(`${base}/auth/desk/authorize?authorization_id=request-one`)
    await page.getByRole('link', { name: 'Cancel', exact: true }).click()
    await page.getByRole('heading', { name: 'LoomAI products', exact: true }).waitFor()
    assert.equal(calls.filter(call => call.path === '/auth/v1/token' || call.path.endsWith('/consent')).length, 0)
  })
  await scenario('existing_console_owner_registers_verifies_and_explicitly_links_both_identities', async ({ page, calls, linked, base }) => {
    await page.goto(`${base}/staff/sign-in?link=customer`)
    await page.getByLabel('Email', { exact: true }).fill('existing-owner@example.test')
    await page.getByLabel('Password', { exact: true }).fill('ExistingPlatformPassword')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await page.getByText('Connect your customer identity', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Create account', exact: true }).click()
    await page.getByLabel(/^Email/).fill('verified-owner@example.test')
    await page.getByLabel(/^Password/).fill('NewSupabasePassword')
    await page.getByRole('button', { name: 'Create account', exact: true }).last().click()
    await page.getByText('Check your email to verify your account. Then return here to sign in.').waitFor()
    await page.goto(`${base}/auth/callback?code=owner-verification-code`)
    await page.getByRole('button', { name: 'Connect verified identity' }).waitFor()
    assert.equal(linked(), false)
    await page.getByRole('button', { name: 'Connect verified identity' }).click()
    await page.waitForFunction(() => sessionStorage.getItem('loomai-console-auth-mode') === 'customer')
    assert.equal(linked(), true)
    const link = calls.find(call => call.path.endsWith('/customer-link'))
    assert.equal(link.authorization, undefined); assert.equal(link.key, undefined)
    await page.waitForURL('**/home')
  }, { deskOnly: true, localCustomer: true })
  await scenario('public_products_are_readable_without_membership_and_keep_simple_navigation', async ({ page, calls, base }) => {
    await page.goto(base)
    await page.getByRole('heading', { name: 'LoomAI products', exact: true }).waitFor()
    assert.equal(await page.locator('[data-public-products-theme]').getAttribute('data-public-products-theme'), 'dark')
    assert.equal(await page.getByRole('navigation').count(), 0)
    for (const product of ['Platform', 'Engage', 'Desk']) {
      await page.getByRole('link', { name: `Explore ${product}`, exact: true }).click()
      await page.getByRole('heading', { name: `LoomAI ${product}`, exact: true }).waitFor()
      const logo = page.getByRole('link', { name: 'LoomAI home', exact: true })
      assert.equal(await logo.getAttribute('href'), 'https://loomai.pro/')
      assert.equal(await logo.getAttribute('target'), null)
      await page.getByRole('link', { name: 'Back to products', exact: true }).click()
      assert.equal(page.url(), `${base}/products`)
    }
    await page.route('https://loomai.pro/', route => route.fulfill({ contentType: 'text/plain', body: 'LoomAI home' }))
    await page.getByRole('link', { name: 'LoomAI home', exact: true }).click()
    await page.waitForURL('https://loomai.pro/')
    assert.equal(page.context().pages().length, 1)
    assert.equal(calls.filter(call => call.method === 'POST').length, 0)
  })
  await scenario('new_verified_customer_creates_workspace_then_opens_existing_template_setup', async ({ page, calls, base }) => {
    await page.goto(`${base}/products/platform`)
    await page.getByRole('link', { name: 'Get started', exact: true }).click()
    await page.getByRole('button', { name: 'Create account', exact: true }).click()
    await page.getByLabel(/^Email/).fill('new-customer@example.test')
    await page.getByLabel(/^Password/).fill('TestPassword')
    await page.getByRole('button', { name: 'Create account', exact: true }).last().click()
    await page.getByText('Check your email to verify your account. Then return here to sign in.').waitFor()
    await page.goto(`${base}/auth/callback?code=new-customer-verified-email`)
    await page.getByRole('heading', { name: 'Create your workspace', exact: true }).waitFor()
    await page.getByLabel(/^Your name/).fill('New customer')
    await page.getByLabel(/^Workspace name/).fill('My company')
    await page.getByRole('button', { name: 'Create workspace', exact: true }).click()
    await page.getByRole('heading', { name: 'Set up your AI deployment', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Choose a deployment template', exact: true }).click()
    await page.waitForURL('**/deployments?view=setup&step=template')
    await page.getByLabel('Product offer', { exact: true }).waitFor()
    await page.getByLabel('Product offer', { exact: true }).click()
    await page.getByRole('option', { name: /Dealership simulator/ }).waitFor()
    await page.getByRole('option', { name: /Knowledge workspace/ }).waitFor()
    assert.equal(await page.getByRole('option').count(), 2)
    await page.keyboard.press('Escape')
    assert.equal(calls.filter(call => call.path.endsWith('/customer-onboarding') && call.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.path === '/api/customer-self-service/enrollment').length, 1)
    assert.equal(calls.filter(call => call.method === 'POST' && /\/(apply|staging-releases|deployments)$/.test(call.path)).length, 0)
  }, { fresh: true, publicTemplate: true })
  await scenario('existing_account_requires_explicit_link_instead_of_registration_claim', async ({ page, calls, base }) => {
    await page.goto(`${base}/start?product=desk`)
    await page.getByText(/This identity matches an existing Console account/).waitFor()
    await page.getByRole('link', { name: 'Connect existing account', exact: true }).waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/customer-onboarding') && call.method === 'POST').length, 0)
  }, { seed: true, fresh: true, linkRequired: true })
  await scenario('product_discovery_survives_backend_unavailability_without_authentication_bypass', async ({ page, calls, base }) => {
    await page.goto(base)
    await page.getByRole('heading', { name: 'LoomAI products', exact: true }).waitFor()
    await page.getByRole('link', { name: 'Explore Engage', exact: true }).click()
    await page.getByRole('heading', { name: 'LoomAI Engage', exact: true }).waitFor()
    assert.equal(calls.filter(call => call.method === 'POST').length, 0)
  }, { backendUnavailable: true })
  await scenario('empty_public_catalogue_is_explicit_and_does_not_fall_back_to_private_templates', async ({ page, calls, base }) => {
    await page.goto(`${base}/start?product=engage`)
    await page.getByLabel(/^Your name/).fill('New customer')
    await page.getByLabel(/^Workspace name/).fill('My company')
    await page.getByRole('button', { name: 'Create workspace', exact: true }).click()
    await page.getByRole('heading', { name: 'Create your Engage workspace', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Choose a deployment template', exact: true }).click()
    await page.getByText(/No self-service template is available for this workspace yet/).waitFor()
    assert.equal(calls.filter(call => call.method === 'POST' && /\/(staging-releases|deployments)$/.test(call.path)).length, 0)
  }, { seed: true, fresh: true })
  console.log(JSON.stringify({ ok: true, scope: 'offline browser; synthetic identities; no live configuration or credentials', checks: results }, null, 2))
} finally { await browser?.close(); await server?.close(); await rm(cacheDir, { recursive: true, force: true }) }
