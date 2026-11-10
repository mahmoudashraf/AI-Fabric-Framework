import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'plugin-library-vite-'))
const shots = fileURLToPath(new URL('../../../output/plugin-library-implementation/', import.meta.url))
await mkdir(shots, { recursive: true })
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0, hmr: false }, plugins: [{
  name: 'plugin-library-fixture', enforce: 'pre',
  resolveId(id) { if (id === '/__library.jsx') return '\0library.jsx' },
  transform(code, id) { if (id === '\0library.jsx') return transformWithEsbuild(code, 'library.jsx', { loader: 'jsx', jsx: 'automatic' }) },
  load(id) {
    if (id !== '\0library.jsx') return
    return `import React,{useState,useCallback} from 'react';import{createRoot}from'react-dom/client';import{createBrowserRouter,RouterProvider,useLocation}from'react-router-dom';
      import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{Button,Box}from'@mui/material';
      import{CustomerConsoleTheme}from'/src/theme/CustomerConsoleTheme.tsx';import{PluginLibraryPage}from'/src/pluginLibrary/PluginLibraryPage.tsx';
      import{BrainCapabilitiesPage}from'/src/brain/BrainCapabilitiesPage.tsx';import{BrainWorkspaceContext,BrainEditorContext}from'/src/brain/BrainWorkspaceContext.tsx';
      import{setPlatformAccessTokenProvider}from'/src/api/platformApi.ts';setPlatformAccessTokenProvider(async()=> 'fixture-token');
      const client=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}});
      function Brain(){const[editor,setEditor]=useState(null);const register=useCallback(v=>setEditor(v),[]);const location=useLocation();const targetProfileId=new URLSearchParams(location.search).get('targetProfileId')??'staging';
        return <BrainWorkspaceContext.Provider value={{brainId:'brain',targetProfileId,href:p=>'/smart-brains/brain/'+p+'?targetProfileId='+targetProfileId,target:null,release:null,targetProfile:null}}><BrainEditorContext.Provider value={register}>
          <Box sx={{height:'100dvh',display:'flex',flexDirection:'column'}}><Box sx={{flex:1,minHeight:0,p:3}}><BrainCapabilitiesPage/></Box><footer><span>{editor?.message}</span>{editor?.save&&<Button disabled={editor.busy||editor.canSave===false} onClick={()=>void editor.save()}>{editor.primaryLabel}</Button>}</footer></Box>
        </BrainEditorContext.Provider></BrainWorkspaceContext.Provider>}
      const router=createBrowserRouter([{path:'/library/plugins/*',element:<PluginLibraryPage/>},{path:'/smart-brains/:id/*',element:<Brain/>}]);
      createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><CustomerConsoleTheme><RouterProvider router={router}/></CustomerConsoleTheme></QueryClientProvider>);`
  },
  configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!/^\/(library|smart-brains)\//.test(req.url)) return next()
    res.setHeader('Content-Type', 'text/html'); res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}</style></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/__library.jsx"></script></body></html>'))
  }) },
}] })
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
const source = { schemaVersion: 1, pluginId: 'source-example', pluginType: 'DATA', displayName: 'Service records', description: 'Approved service information', version: '1.0.0',
  contributions: { datasets: [{ datasetId: 'records', entityType: 'record', ingestionMode: 'EXTERNAL_SYNC_HTTP', syncConnector: { connectorType: 'HTTP_JSON', connectionProfile: { managedBaseUrlField: 'providerBaseUrl', managedAllowedHostField: 'providerHost', auth: { strategy: 'API_KEY', apiKeyHeader: 'X-API-Key', apiKeySecretRefField: 'providerKey' } }, protectedResource: { resourceIdField: 'accountId' }, httpSource: { path: '/records', trustedResourcePlacements: [{ target: 'QUERY', field: 'accountId' }], pagination: { strategy: 'NONE' }, scheduleSeconds: 900, mapping: { idJsonPointer: '/id', resourceJsonPointer: '/accountId', contentFields: { content: '/description' } } } } }], knowledgeSources: [{ datasetRef: 'records', authModes: ['PRIVATE_RUNTIME_BACKEND_MEDIATED'] }] }, installForm: [] }
const summary = (manifest = source) => ({ id: 'private-a', ownerCustomerId: 'customer', visibility: 'CUSTOMER_PRIVATE', displayName: manifest.displayName, manifest, revision: 1, status: 'DRAFT', versions: [],
  catalogueFamily: 'HTTP_JSON', adapterContract: 'HTTP_JSON', canManage: true, installs: { savedCount: 0, appliedCount: 0, saved: [], applied: [] } })
let count = 0
async function run(name, callback, options = {}) {
  const page = await browser.newPage({ viewport: options.viewport ?? { width: 1440, height: 900 } })
  page.setDefaultTimeout(15000)
  const errors = [], unexpected = [], calls = []
  let row = summary(), fail = false
  if (options.usable) { row.status = 'ACTIVE'; row.versions = [{ id: 'pv-a', version: '1.0.0', status: 'PUBLISHED', manifest: source, createdAt: '2026-11-08T00:00:00Z' }] }
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(dark => localStorage.setItem('loomai-customer-console-color-scheme', dark ? 'dark' : 'light'), options.dark !== false)
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname, method = req.method()
    const payload = req.postDataJSON(); calls.push({ path, method, payload, raw: req.postData() })
    const send = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    if (fail && method !== 'GET') { fail = false; return send({ message: 'Draft revision changed. Your edits are retained.' }, 409) }
    if (path === '/api/customer/plugin-library') {
      if (method === 'GET') return send({ customerId: 'customer', canManage: !options.readonly, plugins: options.empty ? [] : [row] })
      row = summary(payload.manifest); return send(row, 201)
    }
    if (path === '/api/customer/plugin-library/private-a') {
      if (method === 'PUT') row = { ...row, manifest: payload.manifest, displayName: payload.manifest.displayName, revision: row.revision + 1 }
      return send({ ...row, canManage: !options.readonly })
    }
    if (path.endsWith('/check')) return send({ valid: !options.checkInvalid, issues: options.checkInvalid ? [{ path: '/contributions', message: 'The source contract needs correction.' }] : [], connectionTested: false, revision: row.revision })
    if (path.endsWith('/versions') && method === 'POST') { row = { ...row, manifest: payload.manifest, status: 'ACTIVE', revision: row.revision + 1, versions: [{ id: 'pv-a', version: payload.manifest.version, status: 'PUBLISHED', manifest: payload.manifest, createdAt: '2026-11-08T00:00:00Z' }] }; return send(row, 201) }
    if (path.includes('/versions/')) return send({ id: 'pv-a', version: '1.0.0', manifest: source })
    if (path.endsWith('/archive')) { row = { ...row, status: 'ARCHIVED', revision: row.revision + 1 }; return send(row) }
    if (path === '/api/marketplace/plugins') return send([{ id: row.id, pluginType: 'DATA', displayName: row.displayName, shortDescription: 'Approved source', latestVersion: '1.0.0', visibility: 'CUSTOMER_PRIVATE' }])
    if (path.startsWith('/api/marketplace/plugins/')) return send({ plugin: { id: row.id, pluginType: 'DATA', displayName: row.displayName, publisherDisplayName: 'Private workspace' }, versions: [{ id: 'pv-a', version: '1.0.0', pricing: { requiresEntitlement: false }, installForm: [], manifest: source }] })
    if (path.endsWith('/workspace')) return send({ deployment: { name: 'Service AI', binding: { customerId: 'customer' } }, access: { canEdit: true } })
    if (path.endsWith('/draft')) return send({ actionsConfig: {} })
    if (path.endsWith('/marketplace-installs')) {
      if (method === 'GET') return send([])
      return send({ id: 'installed', pluginId: 'private-a', pluginVersion: '1.0.0', config: payload.config, secretRefs: payload.secretRefs, status: 'ENABLED', readinessStatus: 'READY', warnings: [], entitlement: {}, updatedAt: '2026-11-08T01:00:00Z' })
    }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected request' }, 404)
  })
  try {
    await page.goto(base + (options.path ?? '/library/plugins'))
    await callback({ page, calls, failNext: () => { fail = true } })
    assert.deepEqual(errors, [], 'No browser runtime errors'); assert.deepEqual(unexpected, [], 'No unrelated APIs')
    count++; console.log(`PASS ${name}`)
  } catch (error) {
    await page.screenshot({ path: join(shots, 'failure.png'), fullPage: true })
    console.error({ name, errors, unexpected, calls, text: await page.locator('body').innerText() }); throw error
  } finally { await page.close() }
}
const step = (page, name) => page.getByRole('navigation', { name: 'Data plugin creation steps' }).getByRole('button', { name: new RegExp(name) }).click()
try {
  await run('required markers and review links guide completion without preventing unfinished draft saves', async ({ page, calls }) => {
    const name = page.getByLabel('Display name', { exact: true })
    await name.waitFor()
    assert.equal(await name.getAttribute('aria-required'), 'true')
    assert.equal(await page.getByLabel('Purpose', { exact: true }).getAttribute('aria-required'), 'false')
    await step(page, 'Review & version')
    await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
    const review = page.getByRole('region', { name: 'Required fields to complete' })
    await review.waitFor()
    assert.equal(calls.filter(call => call.method !== 'GET').length, 0)
    await review.getByRole('button', { name: 'Relative read route', exact: true }).click()
    const route = page.getByLabel('Relative read route', { exact: true })
    assert.equal(await route.evaluate(element => document.activeElement === element), true)
    assert.equal(await route.getAttribute('aria-invalid'), 'true')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.waitForURL('**/private-a/edit*')
    assert.equal(calls.filter(call => call.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/versions')).length, 0)
  }, { path: '/library/plugins/new/data', empty: true })
  await run('an existing source can be recreated through the guided UI without dropping advanced contract data', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Start from existing plugin', exact: true }).click()
    await page.getByRole('combobox', { name: /^Existing Data plugin/ }).click()
    await page.getByRole('option', { name: 'Service records', exact: true }).click()
    await page.getByRole('button', { name: 'Use this starting point', exact: true }).click()
    assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'Copy of Service records')
    await page.getByRole('button', { name: 'Source', exact: true }).click()
    const copy = JSON.parse(await page.getByRole('textbox', { name: 'Complete plugin manifest' }).inputValue())
    assert.notEqual(copy.pluginId, source.pluginId)
    assert.deepEqual(copy.contributions, source.contributions)
    await page.getByRole('button', { name: 'Guided form', exact: true }).click()
    await step(page, 'Review & version')
    await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
    await page.getByText('Reusable version saved. No Brain or live environment was changed.', { exact: true }).waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/check')).length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/versions') && call.method === 'POST').length, 1)
  }, { path: '/library/plugins/new/data', empty: true })
  await run('server contract errors remain visible and prevent version creation without overwriting the draft', async ({ page, calls }) => {
    await step(page, 'Review & version')
    await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
    await page.getByText('The source contract needs correction.', { exact: false }).waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/versions') && call.method === 'POST').length, 0)
    await page.getByRole('button', { name: 'Open source', exact: true }).click()
    assert.deepEqual(JSON.parse(await page.getByRole('textbox', { name: 'Complete plugin manifest' }).inputValue()), source)
  }, { path: '/library/plugins/private-a/edit', checkInvalid: true })
  await run('Save draft and check really saves incomplete work and requests the authoritative server check', async ({ page, calls }) => {
    await step(page, 'Review & version')
    await page.getByRole('button', { name: 'Save draft & check', exact: true }).click()
    await page.waitForURL('**/private-a/edit*')
    await page.getByText('Review the configuration issues below.', { exact: true }).waitFor()
    assert.equal(calls.filter(call => call.path === '/api/customer/plugin-library' && call.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/check') && call.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/versions') && call.method === 'POST').length, 0)
    assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), '')
  }, { path: '/library/plugins/new/data', empty: true, checkInvalid: true })
  await run('guided examples explain fields without saving sample endpoints or changing existing starter defaults', async ({ page, calls }) => {
    await page.getByLabel('Display name', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('Display name', { exact: true }).getAttribute('placeholder'), 'e.g. Customer help articles')
    const purpose = page.getByLabel('Purpose', { exact: true })
    assert.ok(await purpose.getAttribute('aria-describedby'))
    await page.screenshot({ path: join(shots, 'guided-examples-name.png'), fullPage: true })
    await step(page, 'Source contract')
    const route = page.getByLabel('Relative read route', { exact: true })
    assert.equal(await route.inputValue(), '')
    assert.equal(await route.getAttribute('placeholder'), 'e.g. /records')
    await page.getByText(/Setup field names below are reusable placeholders/).waitFor()
    assert.equal(await page.getByLabel('Provider base URL input', { exact: true }).inputValue(), 'providerBaseUrl')
    await page.screenshot({ path: join(shots, 'guided-examples-http.png'), fullPage: true })
    await step(page, 'Records & content')
    await page.getByText('Example API response and field paths', { exact: true }).click()
    await page.getByText(/This is an example only; no mappings are added automatically/).waitFor()
    assert.equal(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight), true, 'Expanded mapping help must stay inside the editor scroll pane')
    await page.screenshot({ path: join(shots, 'guided-examples-mapping.png'), fullPage: true })
    assert.equal(await page.getByLabel('Stable record ID JSON pointer', { exact: true }).inputValue(), '')
    await step(page, 'Freshness & access')
    assert.equal(await page.getByLabel('Baseline sync interval (seconds)', { exact: true }).inputValue(), '900')
    await page.getByText(/900 seconds = 15 minutes/).waitFor()
    await page.getByRole('button', { name: 'Source', exact: true }).click()
    const manifest = JSON.parse(await page.getByRole('textbox', { name: 'Complete plugin manifest' }).inputValue())
    assert.equal(manifest.contributions.datasets[0].syncConnector.httpSource.path, '')
    assert.equal(manifest.contributions.datasets[0].syncConnector.httpSource.mapping.idJsonPointer, '')
    assert.deepEqual(manifest.contributions.datasets[0].syncConnector.httpSource.mapping.contentFields, {})
    assert.equal(calls.filter(call => call.method !== 'GET').length, 0)
    await page.screenshot({ path: join(shots, 'guided-examples-source.png'), fullPage: true })
  }, { path: '/library/plugins/new/data', empty: true })
  await run('document starter explains limits and installation help without filling credentials', async ({ page, calls }) => {
    await step(page, 'Source contract')
    await page.getByRole('button', { name: 'Document storage', exact: true }).click()
    await page.getByRole('button', { name: 'Change source', exact: true }).click()
    await page.getByText(/not a bucket URL or access key/).waitFor()
    assert.equal(await page.getByLabel('Maximum file bytes', { exact: true }).inputValue(), '1048576')
    await page.getByText(/1048576 bytes = 1 MiB/).waitFor()
    await step(page, 'Setup inputs')
    await page.getByText(/identifiers must match the setup field names/).waitFor()
    assert.match(await page.getByLabel('Help text', { exact: true }).inputValue(), /existing hosted or BYO S3-compatible/)
    assert.equal(await page.getByLabel('Input identifier', { exact: true }).inputValue(), 'documentStorageBindingRef')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.waitForURL('**/private-a/edit*')
    const manifest = calls.find(call => call.method === 'POST').payload.manifest
    assert.equal(Object.hasOwn(manifest.installForm[0], 'defaultValue'), false)
    assert.equal(manifest.installForm[0].type, 'text')
  }, { path: '/library/plugins/new/data', empty: true })
  await run('existing custom source values survive help rendering across every task', async ({ page, calls }) => {
    await page.getByLabel('Display name', { exact: true }).waitFor()
    for (const name of ['Source contract', 'Records & content', 'Freshness & access', 'Setup inputs', 'Review & version']) await step(page, name)
    await page.getByRole('button', { name: 'Source', exact: true }).click()
    assert.deepEqual(JSON.parse(await page.getByRole('textbox', { name: 'Complete plugin manifest' }).inputValue()), source)
    assert.equal(calls.filter(call => call.method !== 'GET').length, 0)
  }, { path: '/library/plugins/private-a/edit' })
  await run('new private definition keeps one buffer across tasks and rejects invalid source without overwriting it', async ({ page, calls }) => {
    await page.getByRole('link', { name: 'Create a data plugin', exact: true }).first().click()
    await page.getByLabel('Display name', { exact: true }).fill('Operations guide')
    await page.getByLabel('Purpose', { exact: true }).fill('Answer from approved operational information')
    await step(page, 'Source contract'); await page.getByText('Advanced source contracts', { exact: true }).click(); assert.equal(await page.getByRole('button', { name: /SQL connection/ }).isDisabled(), true)
    await step(page, 'Records & content'); await step(page, 'Name & purpose')
    assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'Operations guide')
    await page.getByRole('button', { name: 'Source', exact: true }).click()
    const editor = page.getByRole('textbox', { name: 'Complete plugin manifest' })
    const previous = await editor.inputValue()
    await editor.fill('{invalid source')
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
    await page.getByRole('button', { name: 'Guided form' }).click()
    await page.getByText(/No defaults have replaced/).waitFor()
    await page.getByRole('button', { name: 'Source', exact: true }).click(); assert.equal(await editor.inputValue(), '{invalid source')
    await editor.fill(previous)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.waitForURL('**/private-a/edit*')
    assert.equal(calls.filter(call => call.method !== 'GET').length, 1)
  }, { empty: true })
  await run('version creates through existing API with revision and retains connection scope for use in Brain', async ({ page, calls }) => {
    await step(page, 'Review & version')
    await page.getByRole('button', { name: 'Check configuration', exact: true }).click()
    await page.getByText(/Provider connectivity and indexing have not been tested/).waitFor()
    await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
    await page.getByText('Reusable version saved. No Brain or live environment was changed.', { exact: true }).waitFor()
    await page.getByRole('link', { name: 'Use in this Brain' }).click()
    assert.match(page.url(), /targetProfileId=staging/)
    assert.match(page.url(), /pluginVersion=1.0.0/)
    await page.getByRole('navigation', { name: 'Connection setup steps' }).getByRole('button', { name: '3 Review' }).click()
    await page.getByRole('button', { name: 'Add to Brain draft', exact: true }).click()
    await page.getByText('Draft updated; live configuration unchanged.', { exact: true }).first().waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/versions') && call.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/marketplace-installs') && call.method === 'POST').length, 1)
    assert.equal(calls.filter(call => call.path.includes('/releases') || call.path.includes('sync') || call.path.includes('query')).length, 0)
  }, { path: '/library/plugins/private-a/edit?returnTo=%2Fsmart-brains%2Fbrain%2Fcapabilities%2Fadd%3FtargetProfileId%3Dstaging' })
  await run('conflicting save retains changed form; incomplete numeric input survives task navigation and blocks mutation', async ({ page, calls, failNext }) => {
    await page.getByLabel('Display name', { exact: true }).fill('Changed locally')
    failNext(); await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft revision changed. Your edits are retained.', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'Changed locally')
    await step(page, 'Freshness & access')
    const number = page.getByRole('textbox', { name: /^Baseline sync interval \(seconds\)/ }); await number.fill('1e-')
    await step(page, 'Name & purpose'); await step(page, 'Freshness & access')
    assert.equal(await number.inputValue(), '1e-')
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
    await number.fill('1000')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Private draft saved. No Brain was changed.', { exact: true }).waitFor()
    assert.equal(calls.filter(call => call.method === 'PUT').at(-1).payload.manifest.contributions.datasets[0].syncConnector.httpSource.scheduleSeconds, 1000)
  }, { path: '/library/plugins/private-a/edit' })
  await run('immutable versions are read-only; library separates saved and applied counts', async ({ page, calls }) => {
    await page.getByRole('heading', { name: /immutable version/ }).waitFor()
    assert.equal(await page.getByLabel('Display name', { exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).count(), 0)
    assert.equal(calls.filter(call => call.method !== 'GET').length, 0)
  }, { path: '/library/plugins/private-a/versions/1.0.0', usable: true })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 1280, height: 720 }, { width: 390, height: 844 }]) {
    for (const dark of [false, true]) await run(`editor layout ${viewport.width} ${dark ? 'dark' : 'light'}`, async ({ page }) => {
      await page.getByLabel('Display name', { exact: true }).waitFor()
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      if (viewport.width >= 900) assert.equal(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight), true)
      await step(page, 'Records & content')
      await page.getByText('Example API response and field paths', { exact: true }).click()
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      if (viewport.width >= 900) assert.equal(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight), true)
      await page.screenshot({ path: join(shots, `creator-${viewport.width}-${dark ? 'dark' : 'light'}.png`), fullPage: true })
    }, { path: '/library/plugins/private-a/edit', viewport, dark })
  }
  console.log(`${count} plugin library browser scenarios passed. Screenshots: ${shots}`)
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
