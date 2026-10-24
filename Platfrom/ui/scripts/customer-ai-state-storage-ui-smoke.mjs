import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.CUSTOMER_STORAGE_UI_OUTPUT ?? `/private/tmp/01045-publish-storage-ui-${Date.now()}`
await mkdir(output, { recursive: true })
const workspaceId = '\0storage-workspace.js', entryId = '\0storage-entry.jsx'
const server = await createServer({ root, configFile: false, envFile: false, cacheDir: output+'/vite-cache',
  esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'customer-storage-fixture', enforce: 'pre',
    resolveId(id, importer) {
      if (id === '/storage-entry.jsx') return entryId
      if (id.endsWith('/workspace/DeploymentWorkspaceContext') && importer?.endsWith('/CustomerDeploymentConsole.tsx')) return workspaceId
    },
    load(id) {
      if (id === workspaceId) return `import {useState} from 'react';
        export function useDeploymentWorkspace(){const [id,setId]=useState('dep-a');window.__selectDeployment=setId;
        return {requestedDeploymentId:id,setSelectedDeploymentId:setId};}`
      if (id === entryId) return `import React,{useState} from 'react';import {createRoot} from 'react-dom/client';
        import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {MemoryRouter} from 'react-router-dom';
        import {CustomerDeploymentConsole} from '/src/components/customer/CustomerDeploymentConsole.tsx';
        const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
        function Harness(){const [actor,setActor]=useState('owner');window.__actor=setActor;
        return React.createElement(QueryClientProvider,{client},React.createElement(MemoryRouter,null,
          React.createElement(CustomerDeploymentConsole,{session:{customerId:'customer',actorId:actor,role:'CUSTOMER_ADMIN',authenticationMode:'SESSION'}})));
        }createRoot(document.getElementById('root')).render(React.createElement(Harness));`
    },
    configureServer(vite) { vite.middlewares.use((request, response, next) => {
      if (request.url !== '/storage-fixture') return next()
      response.setHeader('Content-Type', 'text/html')
      response.end('<html><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/storage-entry.jsx"></script></body></html>')
    }) },
  }],
})
await server.listen()
const base = server.resolvedUrls.local[0], browser = await chromium.launch({ headless: true })
let passed = 0
const offer = (id, modes) => ({ id, name: id, fingerprint: 'pin-'+id, templateId: 'ai-only', blockers: [],
  entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 },
  settings: [{ setting: 'AI_STATE_STORAGE_MODE', type: 'CHOICE', allowedValues: modes }],
  targets: ['staging', 'production'].map(id => ({ id, intent: id === 'staging' ? 'CUSTOMER_STAGING' : 'CUSTOMER_PRODUCTION',
    readiness: 'READY', capabilities: ['READ', 'BINDING_REPLACE'], limits: { stagingExpiryHours: 24 }, blockers: [] })),
  bindingSlots: [{ id: 'ai-state', kind: 'CUSTOMER_AI_STATE', supportedProfileIds: ['CUSTOMER_POSTGRES'] }],
})
const version = id => ({ id: 'v-'+id, deploymentId: id, versionLabel: 'v1', status: 'PUBLISHED', configHash: 'hash-'+id })

async function fixture({ invalidMode = false, publishDenied = false } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1100 } }), page = await context.newPage()
  const state = { calls: [], errors: [], holdBootstrap: false, holdBinding: false, holdDraft: false, pending: [], escaped: [] }
  const offers = [offer('offer-a', ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES']), offer('offer-b', ['MANAGED_POSTGRES'])]
  const deployments = ['dep-a', 'dep-b'].map(id => ({ deploymentId: id, name: id, environmentName: 'customer-staging',
    status: 'DRAFT', tenantId: 'tenant-'+id, offerId: 'offer-a', offerFingerprint: 'pin-offer-a', bootstrapTargetProfileId: 'staging',
    authorizationEntitlementRevision: 4, activeVersionId: null, createdAt: '2026-10-20T00:00:00Z' }))
  const json = (route, value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const request = route.request(), path = url.pathname, method = request.method(), body = request.postDataJSON()
    state.calls.push({ path, method, body })
    if (path === '/api/deployment-operations/status') return json(route, { mode: 'DISABLED', manualDeploymentsAllowed: true,
      serviceConfigured: false, providerUsageMode: 'TEAM_AUTHORIZED', decisionSource: 'EXTERNAL_SERVICE' })
    if (path === '/api/customer-self-service/catalogue') return json(route, offers)
    if (path === '/api/customer-self-service/invitations') return json(route, [])
    if (path === '/api/customer-self-service/deployments') return json(route, deployments)
    if (path === '/api/platform/customers/customer/consumers') return json(route, [])
    if (path === '/api/platform/preferences') return json(route, body ?? {})
    const draft = /^\/api\/deployments\/(dep-[ab])\/draft$/.exec(path)
    if (draft) {
      const reply = () => json(route, { id: 'draft-'+draft[1], deploymentId: draft[1], revisionNumber: 1,
        updatedAt: '2026-10-20T00:00:00Z', providerConfig: { aiStateStorageMode: invalidMode ? 'UNKNOWN' : 'CUSTOMER_POSTGRES' } })
      if (state.holdDraft && draft[1] === 'dep-a') { state.pending.push(reply); return }
      return reply()
    }
    const versions = /^\/api\/deployments\/(dep-[ab])\/versions$/.exec(path)
    if (versions) return json(route, [version(versions[1])])
    if (/\/api\/deployments\/dep-[ab]\/releases$/.test(path) || /\/(admission-reservations|retirements)$/.test(path)) return json(route, [])
    if (/\/catalogue\/offer-[ab]\/deployments$/.test(path) && method === 'POST') {
      const reply = () => json(route, { deploymentId: 'created-late', aiStateStorageMode: body.aiStateStorageMode })
      if (state.holdBootstrap) { state.pending.push(reply); return }
      return reply()
    }
    if (path.endsWith('/ai-state-storage-binding')) {
      if (method === 'GET') return json(route, { message: 'Not configured' }, 404)
      const id = path.includes('dep-a') ? 'dep-a' : 'dep-b'
      const reply = () => json(route, { binding: { bindingId: 'bound-'+body.targetProfileId, customerId: 'customer', tenantId: 'tenant-'+id,
        deploymentId: id, targetProfileId: body.targetProfileId, versionId: body.connection.versionId, configHash: 'hash-'+id,
        mode: 'CUSTOMER_POSTGRES', lifecycle: 'RETAIN_EXTERNAL', migrationAuthorized: true } })
      if (state.holdBinding) { state.pending.push(reply); return }
      return reply()
    }
    if (path.endsWith('/versions') && method === 'POST') return publishDenied
      ? json(route, { message: 'Source or credential review denied publication' }, 403)
      : json(route, { deploymentId: 'dep-a', versionLabel: 'v2' })
    state.errors.push('Unhandled '+method+' '+path)
    return json(route, { message: 'Unhandled fixture route' }, 500)
  })
  await page.goto(base+'storage-fixture', { waitUntil: 'networkidle' })
  try { await page.getByRole('heading', { name: 'My deployments' }).waitFor() }
  catch (error) {
    await page.screenshot({ path: output+'/failure.png', fullPage: true })
    throw new Error(`${error.message}\nPage errors: ${JSON.stringify(state.errors)}\nBody: ${await page.locator('body').innerText()}`)
  }
  return { ...state, state, page, async close() { await context.close(); assert.deepEqual(state.errors, []); assert.deepEqual(state.escaped, []) } }
}
async function choose(page, label, option) {
  await page.getByRole('combobox', { name: new RegExp('^'+label) }).click({ timeout: 10000 })
  await page.getByRole('option', { name: option, exact: true }).click()
}
async function readyCreation(page) {
  await page.getByRole('textbox', { name: 'Deployment name', exact: true }).fill('My AI workspace')
  assert.equal(await page.getByRole('button', { name: 'Verify placement', exact: true }).count(), 0)
}
async function waitPending(f) { await f.page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.disabled && b.textContent.includes('Create staging deployment'))); while (!f.state.pending.length) await new Promise(resolve => setTimeout(resolve, 10)) }

try {
  const create = await fixture()
  try {
    await readyCreation(create.page)
    assert.equal(await create.page.getByRole('button', { name: 'Create staging deployment', exact: true }).isDisabled(), true)
    await choose(create.page, 'AI-state storage', 'Managed AI-state PostgreSQL')
    create.state.holdBootstrap = true
    await create.page.getByRole('button', { name: 'Create staging deployment', exact: true }).click(); await waitPending(create)
    const request = create.state.calls.find(c => c.method === 'POST' && c.path.endsWith('/deployments'))
    assert.equal(request.body.aiStateStorageMode, 'MANAGED_POSTGRES'); assert.equal(request.body.expectedEntitlementRevision, 4)
    await choose(create.page, 'Product offer', 'offer-b')
    assert.equal(await create.page.getByRole('combobox', { name: /^AI-state storage/ })
      .evaluate(element => element.parentElement.querySelector('input').value), '')
    await choose(create.page, 'AI-state storage', 'Managed AI-state PostgreSQL')
    const response = create.page.waitForResponse(r => r.url().endsWith('/deployments') && r.request().method() === 'POST')
    await create.state.pending.shift()(); await response
    await create.page.getByRole('heading', { name: 'dep-a', exact: true, level: 5 }).waitFor()
    assert.equal(await create.page.getByText('The requested deployment is unavailable', { exact: false }).count(), 0)
    assert.equal(await create.page.getByRole('textbox', { name: 'Deployment name', exact: true }).inputValue(), 'My AI workspace')
    assert.equal(await create.page.getByRole('combobox', { name: /^AI-state storage/ })
      .evaluate(element => element.parentElement.querySelector('input').value), 'MANAGED_POSTGRES')
    passed += 1
    console.log('Passed: explicit creation mode, offer reset and late bootstrap response isolation')
  } finally { await create.close() }

  const publish = await fixture()
  try {
    await choose(publish.page, 'AI-state storage', 'Managed AI-state PostgreSQL')
    await publish.page.getByRole('button', { name: 'Publish current configuration', exact: true }).click()
    await publish.page.getByText('Immutable version v2 published.', { exact: true }).waitFor()
    const request = publish.state.calls.find(c => c.method === 'POST' && c.path.endsWith('/versions'))
    assert.deepEqual(Object.keys(request.body).sort(), ['expectedEntitlementRevision', 'operationKey', 'targetProfileId'])
    assert.equal(request.body.targetProfileId, 'staging')
    assert.equal(publish.state.calls.some(c => c.path.endsWith('/placement-evidence')), false)
    await publish.page.getByText('Customer AI-state PostgreSQL', { exact: true }).waitFor()
    await publish.page.screenshot({ path: output+'/desktop.png', fullPage: true })
    await publish.page.setViewportSize({ width: 390, height: 844 })
    await publish.page.screenshot({ path: output+'/mobile.png', fullPage: true })
    passed += 1
    console.log('Passed: saved BYO mode survives unrelated creation choice and exact publish body')
  } finally { await publish.close() }

  const denied = await fixture({ publishDenied: true })
  try {
    assert.equal(denied.state.calls.some(c => c.method === 'POST' && c.path.endsWith('/placement-evidence')), false)
    await denied.page.getByRole('button', { name: 'Publish current configuration', exact: true }).click()
    await denied.page.getByRole('alert').filter({ hasText: 'Source or credential review denied publication' }).waitFor()
    assert.equal(denied.state.calls.filter(c => c.method === 'POST' && c.path.endsWith('/versions')).length, 1)
    assert.equal(denied.state.calls.some(c => c.path.endsWith('/placement-evidence')), false)
    passed += 1
    console.log('Passed: source/credential rejection remains visible without a legacy placement gate or retry')
  } finally { await denied.close() }

  const bind = await fixture()
  try {
    for (const [label, value] of [['PostgreSQL host', 'db.example.test'], ['AI-state database', 'ai_state'],
      ['Runtime AI schema', 'runtime_ai'], ['Connector AI schema', 'connector_ai'], ['PostgreSQL username', 'ai_user'], ['PostgreSQL password', 'private-fixture-value']]) {
      await bind.page.getByLabel(label, { exact: true }).fill(value)
    }
    assert.equal(await bind.page.getByRole('button', { name: 'Bind AI-state PostgreSQL', exact: true }).isDisabled(), true)
    await bind.page.getByLabel('Authorize migrations to dedicated AI schemas', { exact: true }).check()
    bind.state.holdBinding = true
    await bind.page.getByRole('button', { name: 'Bind AI-state PostgreSQL', exact: true }).click()
    while (!bind.state.pending.length) await new Promise(resolve => setTimeout(resolve, 10))
    const request = bind.state.calls.find(c => c.method === 'POST' && c.path.endsWith('/ai-state-storage-binding'))
    assert.equal(request.body.connection.versionId, 'v-dep-a'); assert.equal(request.body.connection.password, 'private-fixture-value')
    assert.equal(request.body.bindingSlotId, 'ai-state'); assert.equal(request.body.targetProfileId, 'staging')
    await choose(bind.page, 'AI-state target', 'production · CUSTOMER_PRODUCTION')
    assert.equal(await bind.page.getByLabel('PostgreSQL password', { exact: true }).inputValue(), '')
    await bind.state.pending.shift()()
    await bind.page.getByLabel('PostgreSQL password', { exact: true }).fill('new-unsent-secret')
    await bind.page.evaluate(() => window.__actor('owner-two'))
    await bind.page.waitForFunction(() => document.querySelector('input[type="password"]')?.value === '')
    assert.equal(await bind.page.getByText(/PostgreSQL binding verified/).count(), 0)
    passed += 1
    console.log('Passed: scoped BYO credentials, consent, target/actor clearing and delayed completion isolation')
  } finally { await bind.close() }

  const stale = await fixture()
  try {
    stale.state.holdDraft = true
    await stale.page.getByRole('button', { name: 'Publish current configuration', exact: true }).click()
    while (!stale.state.pending.length) await new Promise(resolve => setTimeout(resolve, 10))
    await stale.page.evaluate(() => window.__selectDeployment('dep-b'))
    await stale.page.getByRole('heading', { name: 'dep-b', exact: true, level: 5 }).waitFor()
    await stale.state.pending.shift()()
    await stale.page.waitForFunction(() => [...document.querySelectorAll('button')]
      .some(button => button.textContent.includes('Publish current configuration') && !button.disabled))
    assert.equal(await stale.page.getByText('Configuration changed. Refresh before publishing.', { exact: true }).count(), 0)
    assert.equal(stale.state.calls.some(c => c.method === 'POST' && c.path.endsWith('/versions')), false)
    passed += 1
    console.log('Passed: changed deployment scope prevents a stale publish POST')
  } finally { await stale.close() }

  const invalid = await fixture({ invalidMode: true })
  try {
    await invalid.page.getByText('The stored AI-state storage choice is not permitted by this offer.', { exact: true }).waitFor()
    assert.equal(await invalid.page.getByRole('button', { name: 'Publish current configuration', exact: true }).isDisabled(), true)
    assert.equal(invalid.state.calls.some(c => c.method === 'POST' && c.path.endsWith('/versions')), false)
    passed += 1
    console.log('Passed: invalid stored mode blocks publication')
  } finally { await invalid.close() }
  console.log(`Customer AI-state storage UI: ${passed} focused source/API scenarios passed; loopback mocked APIs only.`)
} finally { await browser.close(); await server.close() }
