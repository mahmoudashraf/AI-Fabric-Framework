import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdir, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url)), moduleId = '\0existing-vector-fixture.jsx'
const evidence = process.env.CUSTOMER_VECTOR_UI_EVIDENCE_DIR
let server, browser, cache, base
before(async () => {
  cache = await mkdtemp(join(tmpdir(), 'customer-existing-vector-'))
  if (evidence) await mkdir(evidence, { recursive: true })
  server = await createServer({ root, configFile: false, envFile: false, cacheDir: cache, logLevel: 'error',
    esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'vector-fixture',
      resolveId(id) { if (id === '/existing-vector-fixture.jsx') return moduleId },
      async load(id) {
        if (id !== moduleId) return
        return (await transformWithEsbuild(`
          import React from 'react'; import { createRoot } from 'react-dom/client';
          import { createMemoryRouter, RouterProvider, useLocation, useNavigate } from 'react-router-dom';
          import { QueryClient, QueryClientProvider } from '@tanstack/react-query'; import { Box, Button } from '@mui/material';
          import { PlatformAuthProvider } from '/src/auth/PlatformAuthProvider.tsx';
          import { CustomerConsoleTheme } from '/src/theme/CustomerConsoleTheme.tsx';
          import { CustomerConfigurationPanel } from '/src/components/customer/CustomerConfigurationPanel.tsx';
          import { CustomerKnowledgeConnectionsPage } from '/src/pages/CustomerKnowledgeConnectionsPage.tsx';
          const options = new URLSearchParams(window.location.search), panel = options.get('panel');
          function Content() {
            const location = useLocation(), navigate = useNavigate(), dep = new URLSearchParams(location.search).get('deploymentId');
            return <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1100, mx: 'auto' }}>
              {panel ? <CustomerConfigurationPanel open title={panel + ' / Configuration'} scopeKey={dep} deploymentId={dep} onClose={() => {}}>
                <p>{panel} editor</p></CustomerConfigurationPanel> : <CustomerKnowledgeConnectionsPage mode="connections" />}
              <Button onClick={() => navigate({ search: new URLSearchParams({ deploymentId: dep === 'dep-a' ? 'dep-b' : 'dep-a' }).toString() })}>Switch fixture deployment</Button>
            </Box>
          }
          function Fixture() { return <PlatformAuthProvider><CustomerConsoleTheme><Content /></CustomerConsoleTheme></PlatformAuthProvider> }
          const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); window.fixtureClient = client;
          const router = createMemoryRouter([{ path: '/', element: <Fixture/> }], { initialEntries: ['/?deploymentId=' + (options.get('deploymentId') || 'dep-a')] });
          createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
        `, 'existing-vector-fixture.jsx', { loader: 'jsx', jsx: 'automatic' })).code
      },
      configureServer(vite) { vite.middlewares.use((req, res, next) => {
        if (new URL(req.url, 'http://localhost').pathname !== '/vector-fixture') return next()
        res.setHeader('Content-Type', 'text/html'); res.end('<!doctype html><html><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin};</script><script type="module" src="/existing-vector-fixture.jsx"></script></body></html>')
      }) },
    }] })
  await server.listen(); base = server.resolvedUrls.local[0]; browser = await chromium.launch({ headless: true })
})
after(async () => { await browser?.close(); await server?.close(); if (cache) await rm(cache, { recursive: true, force: true }) })

async function fixture(t, options = {}) {
  const state = { calls: [], errors: [], bindings: {}, customerId: 'customer-a', probeError: false, readError: false, ...options }
  const context = await browser.newContext({ viewport: options.viewport ?? { width: 1440, height: 1000 } })
  t.after(() => context.close())
  const page = await context.newPage(); page.setDefaultTimeout(15000)
  page.on('pageerror', error => state.errors.push(error.message))
  const json = (route, value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
  await page.route(`${base}api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    state.calls.push({ path, method, body: request.postDataJSON() })
    if (path === '/api/platform/auth/customer-config') return json(route, { enabled: false })
    if (path === '/api/platform/auth/session') return json(route, { enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN',
      actorId: 'owner', customerId: state.customerId, authenticationMode: 'SESSION' })
    if (path === '/api/customer-self-service/deployments') return json(route, ['dep-a', 'dep-b'].map(deploymentId => ({ deploymentId, name: deploymentId })))
    const match = path.match(/^\/api\/deployments\/(dep-[ab])\/(.*)$/)
    if (match) {
      const [, dep, resource] = match
      if (resource === 'releases') return json(route, [])
      if (resource === 'workspace') {
        if (state.holdWorkspace) await state.holdWorkspace
        return json(route, { deployment: { id: dep, behaviorType: 'SMART_BRAIN', binding: { customerId: 'customer-a' } }, access: { canAdmin: !state.readOnly } })
      }
      if (resource === 'target-profiles') return json(route, [{ id: 'staging', name: 'Staging', environmentName: 'staging', active: true }, { id: 'production', name: 'Production', environmentName: 'production', active: true }])
      if (resource === 'versions') return json(route, ['v1', 'v2'].map(id => ({ id, deploymentId: dep, status: 'PUBLISHED', versionLabel: id, configHash: 'hash-'+id })))
      if (resource.startsWith('versions/')) {
        const id = resource.split('/')[1]
        return json(route, { deploymentId: dep, versionId: id, providerConfig: { vectorStrategy: 'milvus', vectorProvisioningMode: 'EXTERNAL_EXISTING',
          vectorStoragePosture: 'SHARED', milvusExternalBindingRequired: !state.notRequired, milvusAuthenticationType: id === 'v1' ? 'TOKEN' : 'USERNAME_PASSWORD' } })
      }
    }
    const binding = path.match(/^\/api\/customer-self-service\/deployments\/(dep-[ab])\/targets\/(staging|production)\/existing-vector-binding$/)
    if (binding) {
      const [, dep, target] = binding, key = dep+':'+target
      if (method === 'GET') return state.readError ? json(route, { message: 'Binding read unavailable' }, 503)
        : state.foreignReadback ? json(route, { ...summary(dep, target), targetProfileId: 'foreign' })
        : state.bindings[key] ? json(route, state.bindings[key]) : json(route, { message: 'No binding' }, 404)
      if (state.probeError) return json(route, { message: 'EXTERNAL_VECTOR_SERVING_VERIFICATION_FAILED' }, 412)
      if (state.holdBind) await state.holdBind
      const body = request.postDataJSON(); state.bindings[key] = { ...summary(dep, target), versionId: body.versionId, authType: body.authType, clusterId: body.clusterId }
      return json(route, state.bindings[key])
    }
    state.errors.push('Unexpected API: '+path); return json(route, { message: 'Unexpected fixture API' }, 500)
  })
  await page.goto(`${base}vector-fixture?${new URLSearchParams({ ...(options.panel ? { panel: options.panel } : {}), ...(options.deploymentId ? { deploymentId: options.deploymentId } : {}) })}`)
  if (options.panel) { await page.getByText(`${options.panel} editor`, { exact: true }).waitFor(); return { page, state } }
  await page.getByRole('heading', { name: 'Connections', exact: true }).waitFor()
  if (options.deploymentId === 'foreign') return { page, state }
  await page.getByRole('button', { name: 'Existing vector service', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Existing vector service', exact: true }).count(), 1)
  await page.getByRole('button', { name: 'Existing vector service', exact: true }).click()
  await select(page, 'Vector target', 'Staging / staging'); await select(page, 'Published vector version', options.version ?? 'v1')
  if (state.bindings['dep-a:staging']) await page.getByText('Stored vector binding:', { exact: false }).waitFor()
  else await page.getByLabel('Zilliz cluster ID', { exact: true }).waitFor()
  return { page, state }
}
function summary(dep, target) { return { bindingId: 'binding-'+target, deploymentId: dep, targetProfileId: target, versionId: 'v1',
  clusterId: 'cluster', deploymentOption: 'Free', endpointHost: 'cluster.zilliz.com', databaseName: 'default', collectionPrefix: 'scoped_'+target,
  lifecycle: 'RETAIN_EXTERNAL', fingerprint: 'fingerprint', checkedAt: '2026-11-02T12:00:00Z', authType: 'TOKEN' } }
async function select(page, label, option) {
  await page.getByRole('combobox', { name: new RegExp(`^${label}(?: |$)`) }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
}
async function until(predicate, message) {
  const deadline = Date.now() + 10000
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error(message)
    await new Promise(resolve => setTimeout(resolve, 10))
  }
}
async function fill(page, auth = 'TOKEN') {
  for (const [label, value] of [['Zilliz cluster ID', 'cluster'], ['Zilliz project ID', 'project'], ['Zilliz region ID', 'region']]) await page.getByLabel(label, { exact: true }).fill(value)
  if (auth === 'TOKEN') await page.getByLabel('Milvus serving token', { exact: true }).fill('synthetic-private-token')
  else { await page.getByLabel('Milvus username', { exact: true }).fill('serving-user'); await page.getByLabel('Milvus password', { exact: true }).fill('synthetic-private-password') }
}
const bindButton = page => page.getByRole('button', { name: 'Check and bind existing vector service', exact: true })

for (const auth of ['TOKEN', 'USERNAME_PASSWORD']) test(`${auth} uses the shared panel and leaves credentials out of caches and readback`, async t => {
  const { page, state } = await fixture(t, { version: auth === 'TOKEN' ? 'v1' : 'v2' })
  await fill(page, auth); await bindButton(page).click()
  await page.getByText('Stored vector binding:', { exact: false }).waitFor()
  const writes = state.calls.filter(call => call.method === 'POST')
  assert.equal(writes.length, 1); assert.equal(writes[0].path.includes('/targets/staging/'), true)
  assert.equal(writes[0].body.databaseName, 'default'); assert.equal(writes[0].body.authType, auth)
  assert.equal('approvalReference' in writes[0].body, false)
  assert.equal('token' in writes[0].body, auth === 'TOKEN')
  const cached = await page.evaluate(() => JSON.stringify({ queries: window.fixtureClient.getQueryCache().getAll().map(q => q.state),
    mutations: window.fixtureClient.getMutationCache().getAll().map(m => m.state) }))
  assert.equal(cached.includes('synthetic-private'), false)
  assert.equal(await page.getByLabel(auth === 'TOKEN' ? 'Milvus serving token' : 'Milvus password', { exact: true }).count(), 0)
  await page.getByText('This readback is not a new provider probe.', { exact: false }).waitFor()
  assert.deepEqual(state.errors, [])
})

test('target and version switches clear credentials and never reuse staging success for production', async t => {
  const { page, state } = await fixture(t)
  await fill(page); await select(page, 'Vector target', 'Production / production')
  assert.equal(await page.getByLabel('Milvus serving token', { exact: true }).inputValue(), '')
  await fill(page); await select(page, 'Published vector version', 'v2')
  assert.equal(await page.getByLabel('Milvus password', { exact: true }).inputValue(), '')
  assert.equal(state.calls.some(call => call.method === 'POST'), false)
})

test('scope change during bind preflight prevents a subsequent POST', async t => {
  const { page, state } = await fixture(t); await fill(page)
  let release; state.holdWorkspace = new Promise(resolve => { release = resolve })
  const before = state.calls.length; await bindButton(page).click()
  await page.waitForFunction(() => document.querySelector('button') !== null)
  await until(() => state.calls.slice(before).some(call => call.path.endsWith('/workspace')), 'Binding preflight did not recheck workspace ownership')
  await page.getByRole('button', { name: 'Switch fixture deployment', exact: true }).click()
  await page.getByRole('combobox', { name: 'Deployment dep-b', exact: true }).waitFor()
  release(); state.holdWorkspace = null
  await page.getByRole('button', { name: 'Existing vector service', exact: true }).waitFor()
  await page.waitForTimeout(100)
  assert.equal(state.calls.some(call => call.method === 'POST'), false)
  assert.equal(await page.getByText('Stored vector binding:', { exact: false }).count(), 0)
})

test('late staging bind response cannot mark the production target bound', async t => {
  const { page, state } = await fixture(t); await fill(page)
  let release; state.holdBind = new Promise(resolve => { release = resolve })
  await bindButton(page).click()
  await until(() => state.calls.some(call => call.method === 'POST'), 'Bind did not reach the selected target')
  await select(page, 'Vector target', 'Production / production')
  release(); state.holdBind = null
  await until(() => Boolean(state.bindings['dep-a:staging']), 'Staging response did not complete')
  await page.getByText('No vector binding is recorded for this target.', { exact: true }).waitFor()
  assert.equal(await page.getByLabel('Milvus serving token', { exact: true }).inputValue(), '')
  assert.equal(await page.getByText('Stored vector binding:', { exact: false }).count(), 0)
  assert.equal(state.bindings['dep-a:production'], undefined)
})

test('later published versions can inspect a stored binding approved against an earlier version', async t => {
  const { page, state } = await fixture(t, { version: 'v2', bindings: { 'dep-a:staging': summary('dep-a', 'staging') } })
  await page.getByText('Approved version: v1.', { exact: false }).waitFor()
  assert.equal(await bindButton(page).count(), 0)
  assert.equal(state.calls.some(call => call.method === 'POST'), false)
})

test('changed customer identity prevents credential submission', async t => {
  const { page, state } = await fixture(t); await fill(page); state.customerId = 'foreign-customer'
  await bindButton(page).click(); await page.getByText('The customer, target or published version changed.', { exact: false }).waitFor()
  assert.equal(state.calls.some(call => call.method === 'POST'), false)
})

test('failed provider probe never becomes a recorded binding', async t => {
  const { page, state } = await fixture(t, { probeError: true }); await fill(page); await bindButton(page).click()
  await page.getByText('EXTERNAL_VECTOR_SERVING_VERIFICATION_FAILED', { exact: true }).waitFor()
  assert.equal(await page.getByText('Stored vector binding:', { exact: false }).count(), 0)
  assert.deepEqual(state.bindings, {})
})

for (const option of ['readError', 'foreignReadback', 'readOnly']) test(`${option} cannot submit a bind`, async t => {
  const { page, state } = await fixture(t, { [option]: true })
  assert.equal(await bindButton(page).isDisabled(), true)
  assert.equal(state.calls.some(call => call.method === 'POST'), false)
})

for (const panel of ['Prompts', 'Actions']) test(`${panel} configuration has no vector binding control or provider reads`, async t => {
  const { page, state } = await fixture(t, { panel })
  assert.equal(await page.getByRole('button', { name: 'Existing vector service', exact: true }).count(), 0)
  assert.equal(state.calls.some(call => call.path.includes('/existing-vector-binding') || call.path.endsWith('/target-profiles')), false)
  assert.deepEqual(state.errors, [])
})

test('Connections cannot open a vector binding for a deployment outside the owned portfolio', async t => {
  const { page, state } = await fixture(t, { deploymentId: 'foreign' })
  await page.getByText('The requested deployment is not available in this account.', { exact: false }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Existing vector service', exact: true }).count(), 0)
  assert.equal(state.calls.some(call => call.path.includes('/existing-vector-binding')), false)
})

for (const width of [1440, 390]) test(`shared binding panel fits viewport ${width}`, async t => {
  const { page, state } = await fixture(t, { viewport: { width, height: 1000 } })
  await page.screenshot({ path: join(evidence ?? cache, `existing-vector-${width}.png`), fullPage: true })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
  for (const label of ['Zilliz cluster ID', 'Zilliz project ID', 'Milvus serving token']) {
    const box = await page.getByLabel(label, { exact: true }).boundingBox(); assert.ok(box && box.x >= 0 && box.x + box.width <= width)
  }
  assert.deepEqual(state.errors, [])
})
