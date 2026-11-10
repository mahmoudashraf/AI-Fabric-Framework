import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('../../..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'hosted-document-storage-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'hosted-document-storage-screenshots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  resolve: { dedupe: ['react', 'react-dom'] },
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'hosted-document-storage-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__storage-entry.jsx') return '\0storage-entry.jsx'
      if (id === '/__storage-auth' || id.endsWith('/auth/PlatformAuthProvider')) return '\0storage-auth.js'
      if (id.endsWith('/workspace/DeploymentWorkspaceContext')) return '\0storage-workspace.js'
    },
    transform(code, id) {
      if (id === '\0storage-entry.jsx') return transformWithEsbuild(code, 'storage-entry.jsx', { loader: 'jsx', jsx: 'automatic' })
    },
    load(id) {
      if (id === '\0storage-auth.js') return `import { createContext, useContext } from 'react';
        export const Auth = createContext(null); export const usePlatformAuth = () => useContext(Auth);`
      if (id === '\0storage-workspace.js') return `export function useDeploymentWorkspace() { return {
        selectedDeploymentId: 'dep-a', selectedDeploymentSummary: { id: 'dep-a', binding: { customerId: 'customer-a', tenantId: 'tenant-a' } },
        workspace: { documentKnowledgeConfigured: false, access: { canAdmin: true, canOperate: true } }
      }; }`
      if (id === '\0storage-entry.jsx') return `
        import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query'; import { MemoryRouter } from 'react-router-dom';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { Auth } from '/__storage-auth';
        import { HostedDocumentStorageSetup } from '/src/components/documents/HostedDocumentStorageSetup.tsx';
        import { DocumentKnowledgePage } from '/src/pages/DocumentKnowledgePage.tsx';
        import { ExperienceKitOverview } from '/src/components/kits/ExperienceKitOverview.tsx';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } });
        window.__queryClient = client; window.__invalidations = [];
        const invalidate = client.invalidateQueries.bind(client);
        client.invalidateQueries = filters => { window.__invalidations.push(filters.queryKey); return invalidate(filters); };
        function App() {
          const [state, setState] = useState({ customerId: 'customer-a', actorId: 'actor-a', authenticationMode: 'SESSION', deploymentId: 'dep-a', live: false, canManage: true, releaseId: 'release-a' });
          window.__scope = patch => setState(current => ({ ...current, ...patch }));
          const view = new URLSearchParams(location.search).get('view');
          return <Auth.Provider value={{ session: { authenticated: true, role: 'CUSTOMER_ADMIN', ...state } }}>
            {view === 'documents' ? <DocumentKnowledgePage /> : view === 'kit' ? <ExperienceKitOverview customerId={state.customerId} id="kit-a" /> : <HostedDocumentStorageSetup customerId={state.customerId} deploymentId={state.deploymentId}
              targetProfiles={[{ id: 'stage', name: 'Customer staging' }, { id: 'prod', name: 'Customer production' }]}
              initialTargetProfileId="stage" canManage={state.canManage} indexingHref="/document-knowledge"
              runtime={state.live ? { targetProfileId: 'stage', releaseId: state.releaseId, datasetId: 'actual-dataset' } : undefined}
              byoStorage={<div>Original BYO form fixture</div>} />}
          </Auth.Provider>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><MemoryRouter>
          <ThemeProvider theme={createTheme()}><CssBaseline /><div style={{ maxWidth: 780, padding: 16, margin: 'auto' }}><App /></div></ThemeProvider>
        </MemoryRouter></QueryClientProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__storage') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__storage', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__storage-entry.jsx"></script></body></html>`))
    }) },
  }],
})

const connection = (status = 'READY') => ({ id: 'connection-a', name: 'Shared knowledge', mode: 'LOOMAI_HOSTED_S3',
  bucket: status === 'READY' ? 'customer-knowledge' : '', region: 'eu-west-2', rootPrefix: 'knowledge/', status, lastError: null,
  createdAt: '2026-11-04T10:00:00Z', updatedAt: '2026-11-04T10:00:00Z' })
const upload = (id = 'upload-a', filename = 'guide.txt') => ({ id, connectionId: 'connection-a', filename,
  objectKey: `knowledge/public/${id}/${filename}`, prefix: 'public/', sizeBytes: 12, contentType: 'text/plain', sha256: 'fixture-checksum',
  status: 'UPLOADED', lastError: null, createdAt: '2026-11-04T10:00:00Z' })

async function fixture(browser, baseUrl, test, options = {}) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } })
  page.setDefaultTimeout(10000)
  const errors = [], unexpected = [], requests = [], failures = new Map(), gates = new Map()
  let connections = options.empty ? [] : [connection(options.status)]
  const uploads = options.uploads ?? []
  const receipts = new Map()
  let selectionCalls = 0
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    const raw = request.postDataBuffer()
    const body = request.headers()['content-type'] === 'application/octet-stream' ? raw : raw ? JSON.parse(raw.toString()) : null
    requests.push({ path, method, body, query: Object.fromEntries(url.searchParams), headers: request.headers() })
    const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) }).catch(() => {})
    const key = `${method} ${path}`, gate = gates.get(key)
    if (gate) { gates.delete(key); gate.started(); await gate.wait }
    if (failures.get(key)) { failures.delete(key); return send({ message: 'Temporary storage failure' }, 503) }
    if (method === 'GET' && path === '/api/customer-experience-kits/kit-a') return send({ id: 'kit-a', kitCode: 'ai-customer-experience', kitVersion: '1.0.0', name: 'Example kit',
      deploymentId: 'dep-a', offerId: 'offer-a', targetProfileId: 'stage', workspace: null, embeddedWorkspace: null, deliveryMode: 'standalone',
      installationId: null, embeddedInstallationId: null, humanSupportEnabled: false, ticketingEnabled: false, updatedAt: '2026-11-04T10:00:00Z' })
    if (method === 'GET' && path === '/api/customer-self-service/deployments') return send([{ deploymentId: 'dep-a', tenantId: 'tenant-a', name: 'Example assistant', offerId: 'offer-a', offerFingerprint: 'offer-pin' }])
    if (method === 'GET' && ['/api/customer-self-service/catalogue', '/api/customer-self-service/catalogue/offer-a'].includes(path)) {
      const offer = { id: 'offer-a', fingerprint: 'offer-pin', entitlement: { customerId: 'customer-a', status: 'ACTIVE', revision: 1 },
        targets: [{ id: 'stage', intent: 'CUSTOMER_STAGING', capabilities: ['BINDING_REPLACE', 'BINDING_REVOKE'] }, { id: 'prod', intent: 'CUSTOMER_PRODUCTION', capabilities: ['BINDING_REPLACE'] }],
        bindingSlots: [{ id: 'document-storage', kind: 'CUSTOMER_S3', pluginId: 'mkp-data-ai-support-documents', supportedProfileIds: ['reviewed-s3'] }] }
      return send(path.endsWith('/offer-a') ? offer : [offer])
    }
    if (method === 'GET' && path === '/api/deployments/dep-a/releases') return send(options.view === 'documents' ? [{
      id: 'release-a', deploymentId: 'dep-a', targetProfileId: 'stage', status: 'APPLIED_VERIFIED', provisioningStatus: 'ACTIVE', verificationStatus: 'PASSED',
    }] : [])
    if (method === 'GET' && path === '/api/deployments/dep-a/draft') return send({ marketplaceDatasetConfig: { datasets: [{
      datasetId: 'support-documents', ingestionMode: 'EXTERNAL_DOCUMENT_STORAGE', marketplaceInstallId: 'doc-install', marketplacePluginId: 'mkp-data-ai-support-documents',
    }] } })
    if (method === 'GET' && path.endsWith('/marketplace-installs')) return send([{ id: 'doc-install', pluginId: 'mkp-data-ai-support-documents',
      pluginType: 'DATA', pluginVersion: '2.0.0', pluginVersionId: 'document-v2', config: { documentStorageBindingRef: 'slot:document-storage' } }])
    if (method === 'GET' && path === '/api/marketplace/plugins/mkp-data-ai-support-documents/versions/2.0.0') return send({ id: 'document-v2', manifest: {
      contributions: { knowledgeSources: [{ datasetRef: 'support-documents', sourceType: 'deployment-private-vector',
        authModes: ['PUBLIC_RUNTIME_ANONYMOUS'], filters: { datasetId: 'support-documents', publicationStatus: 'PUBLIC_APPROVED' } }] },
    } })
    if (method === 'GET' && path === '/api/deployments/dep-a/document-storage-bindings') return send([])
    const targetPath = '/api/deployments/dep-a/targets/stage/releases/release-a'
    if (method === 'GET' && path === `${targetPath}/context`) return send({ documentDatasetIds: ['support-documents'] })
    if (method === 'GET' && path === `${targetPath}/document-knowledge/connector`) return send({ ready: true, connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE' })
    if (method === 'GET' && path === `${targetPath}/document-knowledge/sources`) return send([])
    if (method === 'GET' && path === `${targetPath}/document-knowledge/retention`) return send({ evidenceRetention: 'P30D', commandRetention: 'P30D', batchSize: 100 })
    if (method === 'GET' && path.endsWith('/connections')) return send({ hostedAvailable: options.available !== false,
      maxUploadBytes: 1048576, supportedExtensions: ['txt', 'json'], connections: path.includes('/dep-b/') ? [] : connections })
    if (method === 'POST' && path.endsWith('/connections')) { connections = [connection('CONFIGURED')]; return send(connections[0]) }
    if (method === 'POST' && path.endsWith('/prepare')) { connections = [connection()]; return send(connections[0]) }
    if (method === 'GET' && path.endsWith('/uploads')) return send(uploads)
    if (method === 'GET' && path.endsWith('/selections')) return send(receipts.get(`${url.searchParams.get('targetProfileId')}/${url.searchParams.get('releaseId')}`) ?? [])
    if (method === 'POST' && path.endsWith('/uploads')) { const receipt = upload(`upload-${uploads.length}`, url.searchParams.get('filename')); uploads.push(receipt); return send(receipt) }
    if (method === 'POST' && path.endsWith('/bind')) return send({ connection: connections[0], binding: {
      bindingRef: `binding-${body.targetProfileId}`, deploymentId: 'dep-a', targetProfileId: body.targetProfileId,
      credentialsPresent: true, connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE', status: 'CONFIGURED',
      safeMetadata: { storageConnectionId: 'connection-a', prefix: `knowledge/${body.prefix}` }, updatedAt: '2026-11-04T10:00:00Z',
    } })
    if (method === 'POST' && path.endsWith('/prepare-selection')) {
      selectionCalls++
      const items = body.uploadIds.map(uploadId => ({ uploadId, sourceId: `source-${uploadId}`,
        status: selectionCalls > 1 ? 'ACTIVE' : 'INDEXING', workId: `work-${uploadId}` }))
      receipts.set(`${body.targetProfileId}/${body.releaseId}`, items.map(item => ({ ...item, workId: null })))
      return send({ complete: selectionCalls > 1, items })
    }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected request' }, 404)
  })
  const choose = async (label, name) => { await page.getByRole('combobox', { name: label, exact: true }).click(); await page.getByRole('option', { name, exact: true }).click() }
  const select = () => choose('Hosted storage connection', `Shared knowledge (${connections[0].status})`)
  const settled = () => page.waitForFunction(() => window.__queryClient.getMutationCache().getAll().every(item => item.state.status !== 'pending'))
  const scope = patch => page.evaluate(patch => window.__scope(patch), patch)
  const fail = (method, suffix) => failures.set(`${method} /api/deployments/dep-a/document-storage/connections${suffix}`, true)
  const hold = (method, suffix) => {
    let started, release
    const gate = { started: new Promise(resolve => { started = resolve }), release: () => release() }
    gates.set(`${method} /api/deployments/dep-a/document-storage/connections${suffix}`, { started, wait: new Promise(resolve => { release = resolve }) })
    return gate
  }
  try {
    await page.goto(`${baseUrl}/__storage${options.view ? `?view=${options.view}` : ''}`)
    if (options.view === 'documents') {
      await page.getByRole('heading', { name: 'Document Knowledge', exact: true }).waitFor()
      await page.getByRole('button', { name: 'Use LoomAI storage', exact: true }).click()
    }
    await page.getByRole('combobox', { name: 'Hosted storage connection' }).waitFor()
    await test({ page, requests, choose, select, settled, scope, fail, hold })
    assert.deepEqual(errors, [], 'No browser runtime errors')
    assert.deepEqual(unexpected, [], 'All HTTP traffic uses local fixtures')
  } catch (error) {
    console.error({ errors, unexpected, requests, body: await page.locator('body').innerText() })
    throw error
  } finally { for (const gate of gates.values()) gate.started(); await page.close() }
}

await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, test, options) { await fixture(browser, baseUrl, test, options); passed++; console.log(`PASS ${name}`) }
try {
  await run('pre-runtime configuration, explicit preparation, raw upload and shared stage/prod binding', async t => {
    t.fail('POST', '')
    for (let attempt = 0; attempt < 2; attempt++) { await t.page.getByRole('button', { name: 'Create connection', exact: true }).click(); await t.settled() }
    const creates = t.requests.filter(item => item.method === 'POST')
    assert.equal(creates.length, 2); assert.deepEqual(creates[0].body, creates[1].body)
    assert.deepEqual(Object.keys(creates[0].body).sort(), ['name', 'operationKey'])
    t.fail('POST', '/connection-a/prepare')
    for (let attempt = 0; attempt < 2; attempt++) { await t.page.getByRole('button', { name: 'Prepare storage', exact: true }).click(); await t.settled() }
    const preparations = t.requests.filter(item => item.path.endsWith('/prepare'))
    assert.deepEqual(preparations[0].body, preparations[1].body)
    await t.page.getByText('Storage ready', { exact: true }).waitFor()
    await t.page.getByLabel('Document file', { exact: true }).setInputFiles({ name: 'policy & prices.txt', mimeType: 'text/plain', buffer: Buffer.from('Exact source bytes') })
    t.fail('POST', '/connection-a/uploads')
    for (let attempt = 0; attempt < 2; attempt++) { await t.page.getByRole('button', { name: 'Upload file', exact: true }).click(); await t.settled() }
    await t.page.getByText('policy & prices.txt', { exact: true }).waitFor()
    assert.ok(await t.page.getByText('Uploaded; view target indexing status', { exact: false }).count() > 0)
    const uploads = t.requests.filter(item => item.method === 'POST' && item.path.endsWith('/uploads'))
    assert.deepEqual(uploads[0].query, uploads[1].query)
    assert.equal(uploads[0].headers['content-type'], 'application/octet-stream')
    assert.equal(uploads[0].body.toString(), 'Exact source bytes')
    assert.equal(uploads[0].query.filename, 'policy & prices.txt')
    assert.equal(await t.page.getByRole('button', { name: 'Index selected files', exact: true }).count(), 0)
    for (const target of ['Customer staging', 'Customer production']) {
      await t.choose('Storage target', target)
      await t.page.getByRole('textbox', { name: 'Purpose prefix' }).fill('public')
      t.fail('POST', '/connection-a/bind')
      for (let attempt = 0; attempt < 2; attempt++) { await t.page.getByRole('button', { name: 'Bind storage', exact: true }).click(); await t.settled() }
      await t.page.getByText(`${target}: CONFIGURED`, { exact: false }).waitFor()
    }
    const binds = t.requests.filter(item => item.path.endsWith('/bind'))
    assert.deepEqual(binds.map(item => item.body.targetProfileId), ['stage', 'stage', 'prod', 'prod'])
    assert.deepEqual(binds[0].body, binds[1].body); assert.deepEqual(binds[2].body, binds[3].body)
    assert.ok(binds.every(item => item.body.prefix === 'public/' && Object.keys(item.body).length === 3))
    await t.page.getByRole('button', { name: 'Connect my storage', exact: true }).click()
    await t.page.getByText('Original BYO form fixture', { exact: true }).waitFor()
  }, { empty: true })

  await run('unsupported, oversized and traversing uploads never submit; availability is explicit', async t => {
    await t.select()
    for (const [name, buffer, error] of [['guide.pdf', Buffer.from('pdf'), 'Supported files:'], ['guide.txt', Buffer.alloc(1048577), 'exceeds']]) {
      await t.page.getByLabel('Document file', { exact: true }).setInputFiles({ name, mimeType: 'application/octet-stream', buffer })
      await t.page.getByText(error, { exact: false }).waitFor()
      assert.equal(await t.page.getByRole('button', { name: 'Upload file', exact: true }).isDisabled(), true)
    }
    await t.page.getByRole('textbox', { name: 'Purpose prefix' }).fill('../private/')
    assert.equal(await t.page.getByRole('button', { name: 'Bind storage', exact: true }).isDisabled(), true)
    assert.ok(t.requests.every(item => item.method === 'GET'))
  })
  await run('provider unavailable retains BYO and connection list without claiming ready', async t => {
    assert.equal(await t.page.getByRole('button', { name: 'Create connection', exact: true }).isDisabled(), true)
    await t.select()
    assert.equal(await t.page.getByRole('button', { name: 'Prepare storage', exact: true }).isDisabled(), true)
    await t.page.getByText('Hosted storage changes are unavailable.', { exact: false }).waitFor()
    assert.equal(await t.page.getByText('Storage ready', { exact: true }).count(), 0)
  }, { available: false, status: 'CONFIGURED' })

  await run('real Kit overview exposes storage before a runtime exists and uses actual allowed target choices', async t => {
    await t.select()
    assert.equal(await t.page.getByText('Uploaded; no indexing receipt', { exact: false }).count(), 0)
    await t.choose('Storage target', 'Customer production')
    assert.equal(await t.page.getByRole('button', { name: 'Bind storage', exact: true }).isEnabled(), true)
    assert.equal(await t.page.getByRole('button', { name: 'Index selected files', exact: true }).count(), 0)
    assert.equal(t.requests.some(item => item.path === '/api/customer-self-service/catalogue/offer-a'), true)
    assert.equal(t.requests.some(item => item.path.includes('/deployment-provider/')), false)
    assert.ok(t.requests.every(item => item.method === 'GET'))
  }, { view: 'kit' })

  await run('real Document Knowledge page detects Support compiled DATA, enables exact-file selection after release and sends the runtime dataset', async t => {
    await t.select()
    assert.equal(await t.page.getByRole('checkbox', { name: /^guide.txt/ }).isDisabled(), true)
    await t.choose('Target release', 'stage \u00b7 release-a \u00b7 APPLIED_VERIFIED')
    await t.page.getByRole('checkbox', { name: /^guide.txt/ }).check()
    assert.equal(await t.page.getByRole('checkbox', { name: 'I approve the selected files for anonymous public answers.' }).isChecked(), false)
    await t.page.getByRole('button', { name: 'Index selected files', exact: true }).click(); await t.settled()
    const call = t.requests.find(item => item.path.endsWith('/prepare-selection'))
    assert.deepEqual(call.body.uploadIds, ['upload-a'])
    assert.equal(call.body.datasetId, 'support-documents')
    assert.equal(call.body.releaseId, 'release-a')
    assert.equal(call.body.targetProfileId, 'stage')
    assert.equal(call.body.publicApproved, false)
    assert.deepEqual(Object.keys(call.body).sort(), ['datasetId', 'operationKey', 'publicApproved', 'releaseId', 'targetProfileId', 'uploadIds'])
    await t.page.getByRole('heading', { name: 'Connector and discovery', exact: true }).waitFor()
  }, { view: 'documents', uploads: [upload(), upload('upload-b', 'not-selected.json')] })

  await run('exact selected files, default approval off, stable failed retry and fresh explicit reconciliation on the real runtime target', async t => {
    await t.select(); await t.scope({ live: true })
    const publicApproval = t.page.getByRole('checkbox', { name: 'I approve the selected files for anonymous public answers.' })
    assert.equal(await publicApproval.isChecked(), false)
    await t.page.getByRole('checkbox', { name: /^guide.txt/ }).check()
    t.fail('POST', '/connection-a/prepare-selection')
    for (let attempt = 0; attempt < 2; attempt++) { await t.page.getByRole('button', { name: 'Index selected files', exact: true }).click(); await t.settled() }
    await t.page.getByText('guide.txt: INDEXING', { exact: false }).waitFor()
    await t.page.getByRole('button', { name: 'Reconcile selected files', exact: true }).click(); await t.settled()
    await t.page.getByText('guide.txt: ACTIVE', { exact: false }).waitFor()
    const calls = t.requests.filter(item => item.path.endsWith('/prepare-selection'))
    assert.equal(calls.length, 3); assert.deepEqual(calls[0].body, calls[1].body)
    assert.notEqual(calls[1].body.operationKey, calls[2].body.operationKey)
    assert.deepEqual({ ...calls[1].body, operationKey: 'opaque' }, { ...calls[2].body, operationKey: 'opaque' })
    assert.deepEqual({ ...calls[0].body, operationKey: 'opaque' }, { targetProfileId: 'stage', releaseId: 'release-a', datasetId: 'actual-dataset', uploadIds: ['upload-a'], publicApproved: false, operationKey: 'opaque' })
    await t.page.reload()
    await t.page.getByRole('combobox', { name: 'Hosted storage connection' }).waitFor()
    await t.select(); await t.scope({ live: true })
    await t.page.getByText('Active on Customer staging', { exact: false }).waitFor()
    assert.equal(await t.page.getByText('guide.txt: ACTIVE', { exact: false }).count(), 0, 'Persisted row status, not a previous mutation result')
    assert.equal(t.requests.some(item => item.path.endsWith('/selections') && item.query.targetProfileId === 'stage' && item.query.releaseId === 'release-a'), true)
    await t.page.getByRole('checkbox', { name: /^guide.txt/ }).check()
    await publicApproval.check(); await t.scope({ releaseId: 'release-b' })
    await t.page.waitForFunction(() => [...document.querySelectorAll('input[type=checkbox]')].every(input => !input.checked))
    assert.equal(await publicApproval.isChecked(), false)
    assert.equal(await t.page.getByText('guide.txt: ACTIVE', { exact: false }).count(), 0)
    await t.page.screenshot({ path: join(screenshots, 'desktop.png'), fullPage: true })
    for (const width of [390, 360]) {
      await t.page.setViewportSize({ width, height: 844 })
      assert.equal(await t.page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'No horizontal page overflow')
      await t.page.screenshot({ path: join(screenshots, `mobile-${width}.png`), fullPage: true })
    }
  }, { uploads: [upload(), upload('upload-b', 'internal-not-selected.json')] })

  await run('read-only permission leaves storage visible and every mutation disabled', async t => {
    await t.select(); await t.scope({ canManage: false, live: true })
    for (const name of ['Create connection', 'Prepare storage', 'Bind storage', 'Upload file', 'Index selected files']) {
      assert.equal(await t.page.getByRole('button', { name, exact: true }).isDisabled(), true)
    }
    assert.equal(await t.page.getByLabel('Document file', { exact: true }).isDisabled(), true)
    assert.ok(t.requests.every(item => item.method === 'GET'))
  }, { uploads: [upload()] })

  await run('late selected-file receipt stays on its original release and cannot grant public approval on the next', async t => {
    await t.select(); await t.scope({ live: true })
    await t.page.getByRole('checkbox', { name: /^guide.txt/ }).check()
    await t.page.getByRole('checkbox', { name: 'I approve the selected files for anonymous public answers.' }).check()
    const gate = t.hold('POST', '/connection-a/prepare-selection')
    await t.page.getByRole('button', { name: 'Index selected files', exact: true }).click(); await gate.started
    await t.scope({ releaseId: 'release-b' }); gate.release(); await t.settled()
    assert.equal(await t.page.getByText('guide.txt: INDEXING', { exact: false }).count(), 0)
    assert.equal(await t.page.getByRole('checkbox', { name: 'I approve the selected files for anonymous public answers.' }).isChecked(), false)
    const keys = await t.page.evaluate(() => window.__invalidations.filter(key => ['document-sources', 'document-source-detail', 'document-connector-status'].includes(key[0])))
    assert.equal(keys.length, 3)
    assert.ok(keys.every(key => key.slice(1).join('/') === 'dep-a/stage/release-a'))
  }, { uploads: [upload()] })

  for (const patch of [{ deploymentId: 'dep-b' }, { customerId: 'customer-b', actorId: 'actor-b' }, { authenticationMode: 'SUPABASE' }]) {
    await run(`late mutation cannot select a connection or invalidate another scope: ${JSON.stringify(patch)}`, async t => {
      const gate = t.hold('POST', '')
      await t.page.getByRole('button', { name: 'Create connection', exact: true }).click(); await gate.started
      await t.scope(patch); gate.release(); await t.settled()
      await t.page.getByRole('button', { name: 'Create connection', exact: true }).waitFor()
      assert.equal(await t.page.getByRole('combobox', { name: 'Hosted storage connection' }).innerText(), '\u200b')
      assert.deepEqual(await t.page.evaluate(() => window.__invalidations), [])
      assert.equal(await t.page.getByText('Configuration saved.', { exact: false }).count(), 0)
    }, { empty: true })
  }
  console.log(`Hosted document storage browser: ${passed} scenarios passed. Local mocks only. Screenshots: ${screenshots}`)
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
