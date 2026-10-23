import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.CUSTOMER_CONNECTION_UI_OUTPUT ?? `/private/tmp/01045-m4-connections-${Date.now()}`
await mkdir(output, { recursive: true })
const entry = '\0connection-entry.jsx'
const server = await createServer({ root, configFile: false, envFile: false, cacheDir: output+'/vite-cache',
  esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'connection-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/connection-entry.jsx') return entry },
    load(id) { if (id === entry) return `import React,{useState} from 'react';import{createRoot}from'react-dom/client';
      import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
      import{CustomerExternalIntegrationBindings}from'/src/components/customer/CustomerExternalIntegrationBindings.tsx';
      function App(){const[props,setProps]=useState(window.__fixture);window.__changeProps=setProps;
      return React.createElement(QueryClientProvider,{client:new QueryClient({defaultOptions:{mutations:{retry:false},queries:{retry:false}}})},
        React.createElement(CustomerExternalIntegrationBindings,props))}
      createRoot(document.getElementById('root')).render(React.createElement(App));` },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      if (req.url !== '/connection-fixture') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/connection-entry.jsx"></script>')
    }) },
  }] })
await server.listen()
const base = server.resolvedUrls.local[0], browser = await chromium.launch()
const session = { authenticated: true, actorId: 'owner', customerId: 'customer', role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION' }
const deployment = { deploymentId: 'dep', tenantId: 'tenant', name: 'Support assistant', offerId: 'offer', offerFingerprint: 'pin', bootstrapTargetProfileId: 'stage' }
const version = { id: 'version', deploymentId: 'dep', configHash: 'hash', status: 'PUBLISHED', versionLabel: 'v1' }
const profile = { id: 'provider', bindingSlotId: 'source', pluginId: 'data-plugin', targetProfileIds: ['stage', 'prod'], secretFields: ['apiKey'] }
const offer = { id: 'offer', fingerprint: 'pin', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 3 },
  bindingSlots: [{ id: 'source', kind: 'EXTERNAL_SERVICE', pluginId: 'data-plugin', supportedProfileIds: ['provider'] }],
  externalIntegrationProfiles: [profile], targets: ['stage', 'prod'].map(id => ({ id, intent: id === 'stage' ? 'CUSTOMER_STAGING' : 'CUSTOMER_PRODUCTION', capabilities: ['READ','BINDING_REPLACE','BINDING_REVOKE'] })) }
const props = { session, deployment, offer, version }
let passed = 0
async function fixture({ denied = false, foreign = false, empty = false } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1000 } }), page = await context.newPage()
  await context.addInitScript(value => { window.__fixture = value }, props)
  const state = { calls: [], errors: [], blocked: [], binding: null, holdOffer: false, denyBind: false, pending: [] }
  page.on('pageerror', error => state.errors.push(error.message))
  const json = (route, body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    if (url.origin !== new URL(base).origin) { state.blocked.push(url.href); return route.abort() }
    if (!path.startsWith('/api/')) return route.continue()
    const body = request.postDataJSON(); state.calls.push({ path, method, body, query: Object.fromEntries(url.searchParams) })
    if (path === '/api/platform/auth/session') return json(route, session)
    if (path === '/api/customer-self-service/deployments') return json(route, [deployment])
    if (path === '/api/customer-self-service/catalogue/offer') {
      if (state.holdOffer) return new Promise(resolve => state.pending.push(async () => { await json(route, offer); resolve() }))
      return json(route, offer)
    }
    if (path === '/api/deployments/dep/versions') return json(route, [version])
    if (path.endsWith('/placement-evidence')) return denied ? json(route, { message: 'Placement rejected', errorCode: 'PLACEMENT_REJECTED' }, 409) : json(route, { id: 'placement' })
    const scope = { versionId: 'version', targetProfileId: url.searchParams.get('targetProfileId') ?? body?.targetProfileId ?? 'stage', bindingSlotId: 'source', selectedProfileId: 'provider' }
    if (path.endsWith('/external-integrations/approvals')) return json(route, empty ? [] : [{ ...scope, id: 'approval', reviewReference: 'review-ticket', config: { endpoint: 'https://example.invalid/api' }, accountScope: { account: 'approved-account' } }])
    if (path.endsWith('/external-integrations') && method === 'GET') return state.binding ? json(route, { ...state.binding, ...scope, deploymentId: foreign ? 'foreign' : 'dep' }) : json(route, { message: 'No binding' }, 404)
    if (path.endsWith('/external-integrations/bind')) {
      if (state.denyBind) return json(route, { message: 'Connection credentials rejected', errorCode: 'CREDENTIALS_REJECTED' }, 409)
      state.binding = { ...scope, deploymentId: 'dep', id: 'binding', approvalId: 'approval', status: 'CONFIGURED_LOCAL_CREDENTIALS', revision: 0 }
      return json(route, state.binding)
    }
    if (path.endsWith('/external-integrations/revoke')) {
      state.binding = { ...state.binding, status: 'REVOKED_FUTURE_RESOLUTION_EXTERNAL_RETAINED', revision: 1 }
      return json(route, state.binding)
    }
    state.errors.push(method+' '+path); return json(route, { message: 'Unhandled route' }, 500)
  })
  await page.goto(base+'connection-fixture', { waitUntil: 'networkidle' })
  return { page, state, async close() { await context.close(); assert.deepEqual(state.errors, []); assert.deepEqual(state.blocked, []) } }
}
async function review(f) { await f.page.getByRole('button', { name: 'Review approved connection', exact: true }).click() }
async function fill(f) {
  await review(f); await f.page.getByRole('combobox', { name: 'Approved connection', exact: true }).click()
  await f.page.getByRole('option', { name: 'review-ticket / approval', exact: true }).click()
  await f.page.getByLabel('apiKey', { exact: true }).fill('synthetic-fixture-key')
}
try {
  const f = await fixture()
  try {
    assert.equal(f.state.calls.length, 0)
    await fill(f)
    assert.equal(await f.page.getByLabel('apiKey', { exact: true }).getAttribute('type'), 'password')
    await f.page.getByRole('button', { name: 'Bind approved connection', exact: true }).click()
    await f.page.getByText('Connection configured_local_credentials / binding', { exact: true }).waitFor()
    const bind = f.state.calls.find(call => call.path.endsWith('/bind'))
    assert.deepEqual(bind.body.credentials, { apiKey: 'synthetic-fixture-key' }); assert.equal(bind.body.approvalId, 'approval')
    assert.equal(bind.body.targetProfileId, 'stage'); assert.equal(bind.body.expectedEntitlementRevision, 3)
    assert.equal(await f.page.getByLabel('apiKey', { exact: true }).count(), 0)
    assert.equal(await f.page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }).includes('synthetic-fixture-key')), false)
    await f.page.getByRole('button', { name: 'Revoke connection', exact: true }).click()
    await f.page.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.equal(f.state.calls.some(call => call.path.endsWith('/revoke')), false)
    await f.page.getByRole('button', { name: 'Revoke connection', exact: true }).click()
    await f.page.getByRole('button', { name: 'Confirm revoke', exact: true }).click()
    await f.page.getByText('This version already has an immutable binding record.', { exact: false }).waitFor()
    assert.equal(f.state.calls.find(call => call.path.endsWith('/revoke')).body.expectedBindingRevision, 0)
    await f.page.getByRole('combobox', { name: /^Connection target/ }).click(); await f.page.getByRole('option', { name: 'Customer production / prod', exact: true }).click()
    assert.equal(await f.page.getByText('Connection configured_local_credentials / binding', { exact: true }).count(), 0)
    assert.equal(await f.page.getByLabel('apiKey', { exact: true }).count(), 0)
    await f.page.screenshot({ path: output+'/production-clean.png', fullPage: true }); passed++
  } finally { await f.close() }
  for (const config of [{ denied: true }, { empty: true }]) {
    const f = await fixture(config)
    try {
      await review(f)
      await f.page.getByText(config.denied ? 'Placement rejected' : 'No approved connection is available', { exact: false }).waitFor()
      assert.equal(f.state.calls.some(call => /\/(bind|revoke)$/.test(call.path)), false); passed++
    } finally { await f.close() }
  }
  const stale = await fixture()
  try {
    await fill(stale); stale.state.holdOffer = true
    await stale.page.getByRole('button', { name: 'Bind approved connection', exact: true }).click()
    while (!stale.state.pending.length) await new Promise(resolve => setTimeout(resolve, 10))
    await stale.page.getByRole('combobox', { name: /^Connection target/ }).click(); await stale.page.getByRole('option', { name: 'Customer production / prod', exact: true }).click()
    const response = stale.page.waitForResponse(r => r.url().endsWith('/catalogue/offer'))
    await stale.state.pending.shift()(); await response
    assert.equal(stale.state.calls.some(call => call.path.endsWith('/bind')), false); passed++
  } finally { await stale.close() }
  const retry = await fixture()
  try {
    await fill(retry); retry.state.denyBind = true
    await retry.page.getByRole('button', { name: 'Bind approved connection', exact: true }).click()
    await retry.page.getByText('Connection credentials rejected', { exact: true }).waitFor()
    assert.equal(await retry.page.getByLabel('apiKey', { exact: true }).inputValue(), 'synthetic-fixture-key')
    assert.equal(retry.state.calls.filter(call => call.path.endsWith('/bind')).length, 1)
    retry.state.denyBind = false
    await retry.page.getByRole('button', { name: 'Bind approved connection', exact: true }).click()
    await retry.page.getByText('Connection configured_local_credentials / binding', { exact: true }).waitFor()
    const commands = retry.state.calls.filter(call => call.path.endsWith('/bind'))
    assert.equal(commands.length, 2); assert.equal(commands[0].body.operationKey, commands[1].body.operationKey); passed++
  } finally { await retry.close() }
  const foreign = await fixture({ foreign: true })
  try {
    foreign.state.binding = { id: 'foreign-binding', revision: 0, status: 'CONFIGURED' }
    await review(foreign); await foreign.page.getByText('The connection binding does not match', { exact: false }).waitFor()
    assert.equal(await foreign.page.getByRole('button', { name: 'Bind approved connection', exact: true }).count(), 0); passed++
  } finally { await foreign.close() }
  const report = { status: 'PASS', scenarios: passed, hosted: false, output, secretMaterial: 'synthetic-only' }
  await writeFile(output+'/report.actual.json', JSON.stringify(report, null, 2)+'\n'); console.log(JSON.stringify(report))
} finally { await browser.close(); await server.close() }
