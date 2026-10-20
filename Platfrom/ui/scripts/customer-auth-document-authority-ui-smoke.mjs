import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.AUTH_DOCUMENT_SMOKE_OUTPUT
  ?? '/private/tmp/01044-auth-document-authority-ui-20261020/browser-r1'
assert.match(output, /^\/private\/tmp\/01044-auth-document-authority-ui-[a-zA-Z0-9-]+\/browser-r[0-9]+$/)
await mkdir(output)
const save = (name, value) => writeFile(`${output}/${name}`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' })
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const paths = ['src/auth/PlatformAuthProvider.tsx', 'src/api/platformApi.ts', 'src/pages/DocumentKnowledgePage.tsx',
  'src/components/customer/customerDocumentStorageScope.ts', 'scripts/customer-auth-document-authority-ui-smoke.mjs']
const sourcePins = await Promise.all(paths.map(async path => ({ path, sha256: hash(await readFile(`${root}/${path}`)) })))
await save('source.before.json', sourcePins)
const startedAt = new Date().toISOString(), results = [], journals = []
const anonymous = { authenticated: false, actorId: null, customerId: null, role: null, authenticationMode: 'SESSION' }
const owner = { authenticated: true, actorId: 'owner', customerId: 'customer', role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION' }
const offer = () => ({ id: 'offer', fingerprint: 'pin', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 7 },
  targets: [{ id: 'stage', intent: 'CUSTOMER_STAGING', capabilities: ['BINDING_REPLACE', 'BINDING_REVOKE'] }],
  bindingSlots: [{ id: 'document-storage', kind: 'CUSTOMER_S3', supportedProfileIds: ['reviewed-storage'] }] })
const portfolio = [{ deploymentId: 'dep', tenantId: 'tenant', offerId: 'offer', offerFingerprint: 'pin', authorizationEntitlementRevision: 2 }]
const binding = { bindingRef: 'slot:document-storage', targetProfileId: 'stage', connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE', status: 'ACTIVE', credentialsPresent: true }
let server, browser, base, failure = null, contexts = 0
const cleanup = { browserClosed: false, serverClosed: false, contexts: null }
async function until(check, label) {
  const end = Date.now() + 10_000
  while (!(await check())) {
    if (Date.now() > end) throw new Error(`Timed out: ${label}`)
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}
async function scenario(name, documents, action) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1000 }, serviceWorkers: 'block' }); contexts++
  const page = await context.newPage()
  page.setDefaultTimeout(10_000)
  const state = { session: documents ? structuredClone(owner) : structuredClone(anonymous), offer: offer(), calls: [], escaped: [], errors: [],
    holdOffer: false, offerReplies: [], holdSession: false, sessionReplies: [], bindings: [binding], rejectWrite: false }
  journals.push(state)
  page.on('pageerror', error => state.errors.push(error.stack))
  page.on('dialog', dialog => dialog.accept())
  await context.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname, method = route.request().method(), body = route.request().postDataJSON()
    state.calls.push({ path, method, body })
    const json = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    if (path === '/api/platform/auth/session') {
      const reply = route.request().headers()['x-platform-api-key'] ? { ...owner, actorId: 'key-owner', authenticationMode: 'API_KEY' } : structuredClone(state.session)
      if (state.holdSession) { state.holdSession = false; state.sessionReplies.push(() => json(reply)); return }
      return json(reply)
    }
    if (path === '/api/platform/auth/login') { state.session = structuredClone(owner); return json(state.session) }
    if (path === '/api/platform/auth/logout') { state.session = structuredClone(anonymous); return json(state.session) }
    if (path === '/api/customer-self-service/catalogue') return json([state.offer])
    if (path === '/api/customer-self-service/catalogue/offer') {
      if (state.holdOffer) { state.holdOffer = false; state.offerReplies.push(() => json(state.offer)); return }
      return json(state.offer)
    }
    if (path === '/api/customer-self-service/deployments') return json(portfolio)
    if (path === '/api/deployments/dep/draft') return json({ marketplaceDatasetConfig: { datasets: [{ ingestionMode: 'EXTERNAL_DOCUMENT_STORAGE', datasetId: 'docs' }] } })
    if (path.endsWith('/marketplace-installs')) return json([{ id: 'install', pluginId: 'mkp-data-document-knowledge-s3', config: { documentStorageBindingRef: 'slot:document-storage' } }])
    if (path === '/api/deployments/dep/releases') return json([])
    if (path === '/api/deployments/dep/document-storage-bindings') return json(state.bindings)
    if (path.endsWith('/document-storage-binding') || path.endsWith('/document-storage-binding/revoke')) {
      assert.equal(method, 'POST'); assert.equal(body.expectedEntitlementRevision, 7)
      assert.equal(body.targetProfileId, 'stage'); assert.equal(body.bindingSlotId, 'document-storage')
      assert.equal('authorizationEntitlementRevision' in body, false)
      if (state.rejectWrite) return json({ success: false, errorCode: 'ENTITLEMENT_CHANGED', message: 'Synthetic current revision conflict' }, 409)
      if (path.endsWith('/revoke')) { state.bindings = []; return json({}) }
      return json({ binding })
    }
    state.errors.push(`Unexpected ${method} ${path}`)
    return json({ message: 'Unmocked API; not forwarded' }, 500)
  })
  try {
    await page.goto(`${base}authority-fixture${documents ? '?documents=1' : ''}`, { waitUntil: 'networkidle' })
    await until(async () => await page.getByTestId('auth').innerText() === (documents ? 'owner:SESSION' : 'anonymous:SESSION'), 'initial actual auth observer')
    await action({ page, state, writes: () => state.calls.filter(call => call.method === 'POST' && call.path.includes('/document-storage-binding')) })
    assert.deepEqual(state.escaped, []); assert.deepEqual(state.errors, [])
    results.push({ name, status: 'PASS', documentWrites: state.calls.filter(call => call.path.includes('/document-storage-binding') && call.method === 'POST').length })
    console.log(`PASS ${name}`)
  } catch (error) {
    await page.screenshot({ path: `${output}/${name}.failure.png`, fullPage: true }).catch(() => {})
    await writeFile(`${output}/${name}.failure.txt`, `${error.stack}\n${await page.locator('body').innerText()}`, { flag: 'wx' })
    throw error
  } finally {
    await save(`${name}.actual.json`, { ...state, offerReplies: state.offerReplies.length, sessionReplies: state.sessionReplies.length })
    await context.close(); contexts--
  }
}
const saveButton = page => page.getByRole('button', { name: 'Save binding', exact: true })
async function prepareBinding(page) {
  await page.getByRole('combobox', { name: 'Target profile', exact: true }).click()
  await page.getByRole('option', { name: 'Customer staging · Reviewed offer', exact: true }).click()
  await until(async () => await saveButton(page).isEnabled(), 'current reviewed offer binding scope')
  await page.getByRole('textbox', { name: 'Bucket', exact: true }).fill('synthetic-owned')
}
try {
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: `${output}/vite-cache`, esbuild: { jsx: 'automatic' },
    server: { host: '127.0.0.1', port: 41797, strictPort: true }, plugins: [{ name: 'actual-auth-document-fixture', enforce: 'pre',
      resolveId(id, importer) {
        if (id === '/authority-entry.jsx') return '\0authority-entry.jsx'
        if (id.endsWith('/workspace/DeploymentWorkspaceContext') && importer?.endsWith('/DocumentKnowledgePage.tsx')) return '\0authority-workspace.js'
      },
      load(id) {
        if (id === '\0authority-workspace.js') return `export function useDeploymentWorkspace(){return {selectedDeploymentId:'dep',
          selectedDeploymentSummary:{id:'dep',binding:{customerId:'customer',tenantId:'tenant'}},workspace:{documentKnowledgeConfigured:true,access:{canAdmin:true,canOperate:true}}};}`
        if (id === '\0authority-entry.jsx') return `import React from 'react';import {createRoot} from 'react-dom/client';
          import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {MemoryRouter} from 'react-router-dom';
          import {PlatformAuthProvider,usePlatformAuth} from '/src/auth/PlatformAuthProvider.tsx';import {DocumentKnowledgePage} from '/src/pages/DocumentKnowledgePage.tsx';
          const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});window.__client=client;
          function Harness(){const auth=usePlatformAuth();window.__auth=auth;return React.createElement(React.Fragment,null,
            React.createElement('div',{'data-testid':'auth'},(auth.session?.actorId??'anonymous')+':'+(auth.session?.authenticationMode??'loading')),
            new URLSearchParams(location.search).has('documents')&&auth.session?.authenticated?React.createElement(DocumentKnowledgePage):null);}
          createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},React.createElement(PlatformAuthProvider,null,React.createElement(MemoryRouter,null,React.createElement(Harness)))));`
      }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
        if (!req.url.startsWith('/authority-fixture')) return next()
        res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:16px"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/authority-entry.jsx"></script></body></html>')
      }) },
    }] })
  await server.listen(); base = server.resolvedUrls.local[0]; assert.equal(new URL(base).hostname, '127.0.0.1')
  browser = await chromium.launch({ headless: true })
  await scenario('same-empty-key-login-logout-live-observer', false, async ({ page }) => {
    await page.evaluate(() => window.__client.setQueryData(['private-old-principal'], 'old-secret'))
    await page.evaluate(() => {
      window.__client.getMutationCache().build(window.__client, { mutationFn: async () => null })
      window.__client.fetchQuery({ queryKey: ['private-in-flight'], queryFn: () => new Promise(resolve => { window.__completeOld = resolve }) }).catch(() => {})
    })
    await page.evaluate(() => window.__auth.signInWithPassword('synthetic@invalid.test', 'synthetic-not-real'))
    await until(async () => await page.getByTestId('auth').innerText() === 'owner:SESSION', 'login same empty key observer')
    assert.equal(await page.evaluate(() => window.__client.getQueryData(['private-old-principal'])), undefined)
    await page.evaluate(() => window.__completeOld('late-old-secret'))
    assert.equal(await page.evaluate(() => window.__client.getQueryData(['private-in-flight'])), undefined)
    assert.equal(await page.evaluate(() => window.__client.getMutationCache().getAll().length), 0)
    assert.equal(await page.evaluate(() => window.__client.getQueryCache().findAll().filter(q => q.queryKey[0] === 'platform-auth-session' && q.getObserversCount() > 0).length), 1)
    await page.evaluate(() => window.__client.setQueryData(['private-owner'], 'owner-secret'))
    await page.evaluate(() => window.__auth.signOut())
    await until(async () => await page.getByTestId('auth').innerText() === 'anonymous:SESSION', 'logout same empty key observer')
    assert.equal(await page.evaluate(() => window.__client.getQueryData(['private-owner'])), undefined)
    assert.equal(await page.evaluate(() => window.__client.getQueryCache().findAll().filter(q => q.getObserversCount() > 0).length), 1)
  })
  await scenario('key-session-transition-and-delayed-old-session', false, async ({ page, state }) => {
    state.holdSession = true
    const oldRead = page.evaluate(() => window.__auth.refreshSession())
    await until(() => state.sessionReplies.length === 1, 'delayed old auth read')
    await page.evaluate(() => window.__auth.setApiKey('synthetic-key'))
    await until(async () => await page.getByTestId('auth').innerText() === 'key-owner:API_KEY', 'API key observer')
    await state.sessionReplies.shift()(); await oldRead
    assert.equal(await page.getByTestId('auth').innerText(), 'key-owner:API_KEY')
    await page.evaluate(() => window.__auth.signInWithPassword('synthetic@invalid.test', 'synthetic-not-real'))
    await until(async () => await page.getByTestId('auth').innerText() === 'owner:SESSION', 'key back to session')
  })
  await scenario('document-bind-revoke-current-seven-not-bootstrap-two', true, async ({ page, writes }) => {
    await prepareBinding(page); assert.equal(writes().length, 0)
    await page.screenshot({ path: `${output}/desktop.png`, animations: 'disabled' })
    await page.setViewportSize({ width: 390, height: 844 })
    await saveButton(page).scrollIntoViewIfNeeded(); await page.screenshot({ path: `${output}/mobile.png`, animations: 'disabled' })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
    await saveButton(page).click(); await page.getByText('Storage binding saved.', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Remove document storage binding', exact: true }).click()
    await until(() => writes().length === 2, 'current revision revoke')
    assert.deepEqual(writes().map(call => call.body.expectedEntitlementRevision), [7, 7])
  })
  await scenario('fresh-entitlement-conflict-blocks-before-post', true, async ({ page, state, writes }) => {
    await prepareBinding(page); state.offer.entitlement.revision = 8
    await saveButton(page).click()
    await page.getByText('Document-storage authority changed. Refresh the current reviewed offer before submitting.', { exact: true }).waitFor()
    assert.equal(writes().length, 0)
  })
  await scenario('actor-change-during-readback-cannot-fallback-to-staff', true, async ({ page, state, writes }) => {
    await prepareBinding(page); state.holdOffer = true
    await saveButton(page).click(); await until(() => state.offerReplies.length === 1, 'held offer readback')
    state.session = { ...owner, actorId: 'different-owner' }
    await page.evaluate(() => window.__auth.refreshSession())
    await until(async () => await page.getByTestId('auth').innerText() === 'different-owner:SESSION', 'authority switch')
    await state.offerReplies.shift()()
    await until(async () => await saveButton(page).isEnabled(), 'stale request finished')
    assert.equal(writes().length, 0)
  })
  await scenario('backend-current-revision-rejection-not-retried', true, async ({ page, state, writes }) => {
    await prepareBinding(page); state.rejectWrite = true
    await saveButton(page).click()
    await page.getByText('Synthetic current revision conflict', { exact: true }).waitFor()
    await page.waitForTimeout(250)
    assert.equal(writes().length, 1)
  })
  for (const pin of sourcePins) assert.equal(hash(await readFile(`${root}/${pin.path}`)), pin.sha256, `Source drift: ${pin.path}`)
} catch (error) { failure = { message: error.message, stack: error.stack }; process.exitCode = 1; console.error(error.stack) }
finally {
  if (browser) { await browser.close(); cleanup.browserClosed = !browser.isConnected() }
  if (server) { await server.close(); cleanup.serverClosed = !server.httpServer?.listening }
  cleanup.contexts = contexts
  const proof = { phase: 'ACTUAL_AUTH_AND_DOCUMENT_AUTHORITY_UI', status: failure ? 'FAIL' : 'PASS', startedAt,
    finishedAt: new Date().toISOString(), plannedScenarios: 6, results, failure, cleanup, sourcePins,
    nonLoopbackAttempts: journals.flatMap(item => item.escaped), allApiRequestsIntercepted: true, noHostedActions: true,
    limits: 'Actual UI/provider/API with synthetic loopback normal owner endpoints; not hosted/backend/P5/P6 acceptance.' }
  if (!cleanup.browserClosed || !cleanup.serverClosed || contexts !== 0) { proof.status = 'FAIL'; process.exitCode = 1 }
  await save('qualification.actual.json', proof); console.log(JSON.stringify({ status: proof.status, scenarios: results.length, cleanup, output }))
}
