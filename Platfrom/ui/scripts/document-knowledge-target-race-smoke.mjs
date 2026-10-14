import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer } from 'vite'

// Mount the real page and API client; only the workspace provider and HTTP responses are fixtures.
const root = fileURLToPath(new URL('..', import.meta.url))
const entryId = '\0document-knowledge-race-entry.jsx'
const workspaceId = '\0document-knowledge-race-workspace'
const server = await createServer({
  root,
  configFile: false,
  envFile: false,
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{
    name: 'document-knowledge-race-fixture',
    enforce: 'pre',
    resolveId(id, importer) {
      if (id === '/__race-entry.jsx') return entryId
      if (id.endsWith('/workspace/DeploymentWorkspaceContext')
        && importer?.endsWith('/pages/DocumentKnowledgePage.tsx')) return workspaceId
    },
    load(id) {
      if (id === workspaceId || id.endsWith('/workspace/DeploymentWorkspaceContext.tsx')) return `
        import { useSyncExternalStore } from 'react';
        let deploymentId = 'dep-a';
        const listeners = new Set();
        window.__switchDeployment = (next) => { deploymentId = next; listeners.forEach(fn => fn()); };
        export function useDeploymentWorkspace() {
          const id = useSyncExternalStore(fn => { listeners.add(fn); return () => listeners.delete(fn); }, () => deploymentId);
          return { selectedDeploymentId: id, selectedDeploymentSummary: { environment: 'production' },
            workspace: { documentKnowledgeConfigured: true, access: { canAdmin: true, canOperate: true } } };
        }
      `
      if (id === entryId) return `
        import React from 'react';
        import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { MemoryRouter } from 'react-router-dom';
        import { DocumentKnowledgePage } from '/src/pages/DocumentKnowledgePage.tsx';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
        window.__queryClient = client;
        window.__invalidations = [];
        const invalidate = client.invalidateQueries.bind(client);
        client.invalidateQueries = (filters, ...rest) => { window.__invalidations.push(filters.queryKey); return invalidate(filters, ...rest); };
        createRoot(document.getElementById('root')).render(
          React.createElement(QueryClientProvider, { client }, React.createElement(MemoryRouter, null, React.createElement(DocumentKnowledgePage)))
        );
      `
    },
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        if (req.url !== '/__race') return next()
        res.setHeader('Content-Type', 'text/html')
        res.end(await vite.transformIndexHtml('/__race', `<!doctype html><html><body><div id="root"></div>
          <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script>
          <script type="module" src="/__race-entry.jsx"></script></body></html>`))
      })
    },
  }],
})

function source(release, id = 'source-one') {
  return {
    sourceId: id, datasetId: `dataset-${release}`, displayName: `${release}-${id}.txt`,
    mediaType: 'text/plain', contentLength: 128, status: 'READY', candidateVersion: 1,
    activeVersion: 1, providerRevisionFingerprint: 'fixture-revision', updatedAt: '2026-10-14T00:00:00Z',
  }
}

function targetPath(deployment = 'dep-a', release = 'release-a') {
  return `/api/deployments/${deployment}/targets/target-${release.at(-1)}/releases/${release}`
}

function documentsPath(deployment = 'dep-a', release = 'release-a') {
  return `${targetPath(deployment, release)}/document-knowledge`
}

function binding(deployment, profile = 'target-a') {
  return { bindingRef: `binding-${deployment}-${profile}`, targetProfileId: profile,
    connectorType: 'MOUNTED_FOLDER', credentialsPresent: false, status: 'ACTIVE' }
}

async function session(browser, baseUrl, test, { configuredSlot = false } = {}) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  page.setDefaultTimeout(10_000)
  const requests = []
  const unexpected = []
  const pageErrors = []
  const gates = new Map()
  const releases = []
  const registrations = new Map()
  const installs = new Map(['dep-a', 'dep-b'].map(deployment => [deployment, {
    id: `install-${deployment}`, pluginId: 'mkp-data-dealership-knowledge-v1',
    config: { preservedSetting: deployment, ...(configuredSlot ? { documentStorageBindingRef: 'slot:document-storage' } : {}) },
  }]))
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('dialog', dialog => dialog.accept())
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(`External request: ${route.request().url()}`)
    return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    const body = request.postData() ? request.postDataJSON() : null
    requests.push({ method, path, body })
    assert.equal(request.headers()['x-platform-api-key'], undefined)
    assert.equal(request.headers().authorization, undefined)
    const json = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    const gate = gates.get(`${method} ${path}`)
    if (gate) {
      gates.delete(`${method} ${path}`)
      gate.arrived()
      const response = await gate.response
      if (response) return json(response.body, response.status)
    }
    if (path === '/api/deployment-provider/target-profiles') return json([
      { id: 'target-a', name: 'Production profile', providerType: 'COOLIFY', active: true, environmentName: 'production' },
      { id: 'target-b', name: 'Staging profile', providerType: 'COOLIFY', active: true, environmentName: 'staging' },
    ])
    const deploymentMatch = path.match(/^\/api\/deployments\/(dep-[ab])\/(.*)$/)
    if (!deploymentMatch) {
      unexpected.push(`${method} ${path}`)
      return json({ message: 'Unexpected fixture route' }, 404)
    }
    const [, deployment, suffix] = deploymentMatch
    if (suffix === 'draft') return json({ marketplaceDatasetConfig: { datasets: [{
      ingestionMode: 'EXTERNAL_DOCUMENT_STORAGE', datasetId: `draft-${deployment}`, marketplaceInstallId: `install-${deployment}`,
    }] } })
    if (suffix === 'marketplace-installs') return json([installs.get(deployment)])
    if (suffix === 'releases') return json(['a', 'b'].map(letter => ({
      id: `release-${letter}`, targetProfileId: `target-${letter}`, provisioningStatus: 'ACTIVE', status: 'APPLIED_VERIFIED',
    })))
    if (suffix === 'document-storage-bindings') return json(method === 'POST'
      ? binding(deployment, body.targetProfileId) : [binding(deployment)])
    if (suffix.startsWith('document-storage-bindings/') && method === 'DELETE') return route.fulfill({ status: 204 })
    if (suffix === `marketplace-installs/install-${deployment}` && method === 'PUT') {
      installs.get(deployment).config = body.config
      return json(installs.get(deployment))
    }
    if (suffix === `marketplace-installs/install-${deployment}/resolve`) return json({})
    const targetMatch = suffix.match(/^targets\/(target-[ab])\/releases\/(release-[ab])\/(.*)$/)
    if (targetMatch) {
      const [, profile, release, operation] = targetMatch
      assert.equal(profile.at(-1), release.at(-1), 'Exact target profile and release must agree')
      const sources = [source(release), source(release, 'source-two'), ...(registrations.get(`${deployment}/${release}`) ?? [])]
      if (operation === 'context') return json({ deploymentId: deployment, targetProfileId: profile, releaseId: release,
        documentDatasetIds: [`dataset-${deployment}-${release}`] })
      if (operation === 'document-knowledge/connector') return json({ ready: true, connectorType: 'MOUNTED_FOLDER' })
      if (operation === 'document-knowledge/discover') return json({ nextCursor: `cursor-${deployment}-${release}`, sources: [{
        objectReference: `object-${deployment}-${release}`, displayName: `discovered-${release}.txt`,
        mediaType: 'text/plain', contentLength: 128, registeredSourceId: null,
      }] })
      if (operation === 'document-knowledge/sources') {
        if (method === 'GET') return json(sources)
        const registered = source(release, 'registered-source')
        registrations.set(`${deployment}/${release}`, [registered])
        return json(registered)
      }
      if (operation === 'document-knowledge/retrieval-proof') return json({
        query: body.query, evidenceCount: 1, processingTimeMs: 7, evidence: [{
          entityId: 'evidence', sourceName: 'fixture', sourceVersion: 1, chunkIndex: 0, content: `proof-${deployment}-${release}`,
        }],
      })
      if (operation === 'document-knowledge/retention') return json({ evidenceRetention: 'P7D', commandRetention: 'P7D', batchSize: 50 })
      if (operation === 'document-knowledge/retention/cleanup') return json({ manifestsDeleted: 17, workDeleted: 18, commandsDeleted: 19 })
      const sourceMatch = operation.match(/^document-knowledge\/sources\/(source-one|source-two|registered-source)(?:\/(preview|index|refresh|reconcile))?$/)
      if (sourceMatch) {
        const [, id, command] = sourceMatch
        if (command === 'preview') return json({ chunkCount: 1, totalContentLength: 128, chunks: [{
          chunkId: 'chunk', chunkIndex: 0, chunkCount: 1, contentPreview: `preview-${deployment}-${release}-${id}`,
        }] })
        if (command || method === 'DELETE') return json({ outcome: `outcome-${command ?? 'remove'}-${deployment}-${release}`, source: source(release, id) })
        return json({ source: source(release, id), manifests: [] })
      }
    }
    unexpected.push(`${method} ${path}`)
    return json({ message: 'Unexpected fixture route' }, 404)
  })
  const hold = (method, path) => {
    let arrived, release
    const started = new Promise(resolve => { arrived = resolve })
    const response = new Promise(resolve => { release = resolve })
    releases.push(release)
    gates.set(`${method} ${path}`, { arrived, response })
    const waitForStart = async () => {
      let timeout
      try {
        await Promise.race([started, new Promise((_, reject) => {
          timeout = setTimeout(() => reject(new Error(`Expected fixture request: ${method} ${path}`)), 10_000)
        })])
      } finally {
        clearTimeout(timeout)
      }
    }
    return { get started() { return waitForStart() }, release, fail: message => release({ status: 409, body: { message } }) }
  }
  const choose = async (label, option) => {
    await page.getByRole('combobox', { name: label }).click()
    await page.getByRole('option').filter({ hasText: option }).click()
  }
  const selectRelease = async (release = 'release-a') => {
    await choose('Target release', release)
    await page.getByText(`${release}-source-one.txt`, { exact: true }).waitFor()
    await page.getByText('Connector ready', { exact: true }).waitFor()
  }
  const selectSource = async (release = 'release-a', id = 'source-one') => {
    await page.getByRole('cell', { name: `${release}-${id}.txt`, exact: true }).click()
    await page.getByRole('button', { name: 'Preview', exact: true }).waitFor()
  }
  const switchDeployment = async () => {
    await page.evaluate(() => window.__switchDeployment('dep-b'))
    await page.waitForFunction(() => window.__queryClient.getQueryState(['deployment-draft', 'dep-b'])?.status === 'success')
    await page.getByRole('combobox', { name: 'Target release' }).waitFor()
    await selectRelease()
  }
  const settled = () => page.waitForFunction(() => window.__queryClient.getMutationCache().getAll().every(mutation => mutation.state.status !== 'pending'))
  const invalidations = () => page.evaluate(() => window.__invalidations)
  const clearInvalidations = () => page.evaluate(() => { window.__invalidations = [] })
  const assertHidden = async text => assert.equal(await page.getByText(text, { exact: false }).count(), 0, `Hidden outside originating scope: ${text}`)
  try {
    await page.goto(`${baseUrl}/__race`)
    await page.getByRole('heading', { name: 'Document Knowledge', exact: true }).waitFor()
    await selectRelease()
    await clearInvalidations()
    await test({ page, requests, hold, choose, selectRelease, selectSource, switchDeployment, settled, invalidations, clearInvalidations, assertHidden })
    assert.deepEqual(unexpected, [], 'All requests must be local fixtures')
    assert.deepEqual(pageErrors, [], 'No browser runtime errors')
  } catch (error) {
    console.error({ pageErrors, unexpected, requests, body: await page.locator('body').innerText() })
    throw error
  } finally {
    // Release any deferred fixture responses before closing the isolated browser page.
    for (const release of releases) release()
    await page.close()
  }
}

let browser
let passed = 0
async function run(name, test, options) {
  await session(browser, baseUrl, test, options)
  passed += 1
  console.log(`PASS ${name}`)
}

await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
try {
  browser = await chromium.launch({ headless: true })
  await run('late release-scoped successes stay hidden on B and remain available on A', async t => {
    const operations = [
      ['POST', 'discover', 'Discover sources'], ['GET', 'sources/source-one/preview', 'Preview'],
      ['POST', 'retrieval-proof', 'Verify'], ['POST', 'sources/source-one/index', 'Index'],
      ['POST', 'retention/cleanup', 'Run cleanup'],
    ].map(([method, suffix, button]) => ({ gate: t.hold(method, `${documentsPath()}/${suffix}`), button }))
    await t.selectSource()
    for (const { gate, button } of operations) {
      await t.page.getByRole('button', { name: button, exact: true }).click()
      await gate.started
    }
    await t.selectRelease('release-b')
    await t.selectSource('release-b')
    for (const { button } of operations) assert.equal(await t.page.getByRole('button', { name: button, exact: true }).isDisabled(), true)
    for (const { gate } of operations) gate.release()
    await t.settled()
    for (const text of ['discovered-release-a.txt', 'Next page', 'preview-dep-a-release-a', 'proof-dep-a-release-a', 'outcome-index-dep-a-release-a', 'Removed 17']) await t.assertHidden(text)
    assert.deepEqual((await t.invalidations()).map(key => JSON.stringify(key)).sort(), [
      ['document-sources', 'dep-a', 'target-a', 'release-a'],
      ['document-source-detail', 'dep-a', 'target-a', 'release-a'],
      ['document-connector-status', 'dep-a', 'target-a', 'release-a'],
      ['document-retention-status', 'dep-a', 'target-a', 'release-a'],
    ].map(key => JSON.stringify(key)).sort())
    await t.selectRelease()
    await t.selectSource()
    for (const text of ['discovered-release-a.txt', 'Next page', 'preview-dep-a-release-a-source-one', 'proof-dep-a-release-a', 'Operation: outcome-index-dep-a-release-a', 'Removed 17']) await t.page.getByText(text, { exact: false }).waitFor()
    await t.selectSource('release-a', 'source-two')
    await t.assertHidden('preview-dep-a-release-a-source-one')
    await t.assertHidden('Operation: outcome-index-dep-a-release-a')
    await t.selectSource()
    await t.page.getByText('preview-dep-a-release-a-source-one', { exact: true }).waitFor()
    await t.page.getByRole('button', { name: 'Next page', exact: true }).click()
    await t.settled()
    const discoveries = t.requests.filter(request => request.path.endsWith('/discover'))
    assert.equal(discoveries[0].body.datasetId, 'dataset-dep-a-release-a')
    assert.equal(discoveries[1].body.cursor, 'cursor-dep-a-release-a')
    assert.equal(discoveries[1].path, `${documentsPath()}/discover`)
  })

  await run('late errors are deployment-scoped and do not discard pending requests', async t => {
    await t.selectSource()
    const operations = [
      ['POST', 'discover', 'Discover sources'], ['GET', 'sources/source-one/preview', 'Preview'],
      ['POST', 'retrieval-proof', 'Verify'], ['POST', 'sources/source-one/index', 'Index'],
      ['POST', 'retention/cleanup', 'Run cleanup'],
    ]
    const held = []
    for (const [method, suffix, button] of operations) {
      const gate = t.hold(method, `${documentsPath()}/${suffix}`)
      await t.page.getByRole('button', { name: button, exact: true }).click()
      await gate.started
      held.push({ gate, suffix })
    }
    await t.switchDeployment()
    await t.selectSource()
    for (const { gate, suffix } of held) gate.fail(`old-target-error-${suffix}`)
    await t.settled()
    await t.assertHidden('old-target-error')
    assert.deepEqual(await t.invalidations(), [])
    await t.page.evaluate(() => window.__switchDeployment('dep-a'))
    await t.selectRelease()
    await t.selectSource()
    for (const { suffix } of held) await t.page.getByText(`old-target-error-${suffix}`, { exact: true }).waitFor()
  })

  await run('registration captures route, runtime dataset and audience without selecting a source on another deployment', async t => {
    await t.page.getByRole('button', { name: 'Discover sources', exact: true }).click()
    await t.page.getByRole('button', { name: 'Register', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Source category', exact: true }).fill('policy')
    await t.page.getByRole('checkbox', { name: 'I confirm this file is approved for anonymous public answers.' }).check()
    const gate = t.hold('POST', `${documentsPath()}/sources`)
    await t.page.getByRole('button', { name: 'Register source', exact: true }).click()
    await gate.started
    await t.switchDeployment()
    assert.equal(await t.page.getByRole('dialog').count(), 0)
    gate.release()
    await t.settled()
    assert.equal(await t.page.locator('tr.Mui-selected').count(), 0)
    assert.equal(t.requests.some(request => request.path.includes('/dep-b/') && request.path.endsWith('/registered-source')), false)
    assert.deepEqual(t.requests.find(request => request.method === 'POST' && request.path === `${documentsPath()}/sources`).body, {
      datasetId: 'dataset-dep-a-release-a', objectReference: 'object-dep-a-release-a', visibility: 'tenant',
      metadata: { sourceCategory: 'policy', publicationStatus: 'PUBLIC_APPROVED' },
    })
    assert.deepEqual(await t.invalidations(), [
      ['document-sources', 'dep-a', 'target-a', 'release-a'],
      ['document-source-detail', 'dep-a', 'target-a', 'release-a'],
      ['document-connector-status', 'dep-a', 'target-a', 'release-a'],
    ])
    await t.page.evaluate(() => window.__switchDeployment('dep-a'))
    await t.selectRelease()
    await t.page.getByText('release-a-registered-source.txt', { exact: true }).waitFor()
  })

  await run('remove completion invalidates only its originating target', async t => {
    await t.selectSource()
    await t.page.getByRole('button', { name: 'Remove index', exact: true }).click()
    const gate = t.hold('DELETE', `${documentsPath()}/sources/source-one`)
    await t.page.getByRole('dialog').getByRole('button', { name: 'Remove index', exact: true }).click()
    await gate.started
    await t.switchDeployment()
    assert.equal(await t.page.getByRole('dialog').count(), 0)
    gate.release()
    await t.settled()
    assert.deepEqual(await t.invalidations(), [
      ['document-sources', 'dep-a', 'target-a', 'release-a'],
      ['document-source-detail', 'dep-a', 'target-a', 'release-a'],
      ['document-connector-status', 'dep-a', 'target-a', 'release-a'],
    ])
  })

  for (const [command, button] of [['refresh', 'Refresh revision'], ['reconcile', 'Reconcile']]) {
    await run(`${command} keeps captured route and success scoped to the originating release`, async t => {
      await t.selectSource()
      const gate = t.hold('POST', `${documentsPath()}/sources/source-one/${command}`)
      await t.page.getByRole('button', { name: button, exact: true }).click()
      await gate.started
      await t.selectRelease('release-b')
      await t.selectSource('release-b')
      gate.release()
      await t.settled()
      await t.assertHidden(`outcome-${command}`)
      assert.equal((await t.invalidations()).every(key => key.slice(1).join('/') === 'dep-a/target-a/release-a'), true)
      await t.selectRelease()
      await t.selectSource()
      await t.page.getByText(`Operation: outcome-${command}-dep-a-release-a`, { exact: true }).waitFor()
    })
  }

  for (const slot of [false, true]) {
    await run(`binding save captures deployment, profile and ${slot ? 'opt-in slot' : 'default direct binding'} across chained requests`, async t => {
      const checkbox = t.page.getByRole('checkbox', { name: 'Use target-scoped document storage slot' })
      assert.equal(await checkbox.isChecked(), false)
      if (slot) await checkbox.check()
      await t.page.getByRole('button', { name: 'Mounted demo', exact: true }).click()
      await t.choose('Target profile', 'Staging profile')
      const gate = t.hold('POST', '/api/deployments/dep-a/document-storage-bindings')
      await t.page.getByRole('button', { name: 'Save binding', exact: true }).click()
      await gate.started
      await t.switchDeployment()
      await t.choose('Target profile', 'Production profile')
      gate.release()
      await t.settled()
      await t.assertHidden('Storage binding saved.')
      const writes = t.requests.filter(request => request.method !== 'GET')
      assert.deepEqual(writes.map(({ method, path }) => [method, path]), [
        ['POST', '/api/deployments/dep-a/document-storage-bindings'],
        ['PUT', '/api/deployments/dep-a/marketplace-installs/install-dep-a'],
        ['POST', '/api/deployments/dep-a/marketplace-installs/install-dep-a/resolve'],
      ])
      assert.deepEqual(writes[0].body, { targetProfileId: 'target-b', connectorType: 'MOUNTED_FOLDER', mountedRoot: '/app/document-sources' })
      assert.deepEqual(writes[1].body.config, { preservedSetting: 'dep-a', documentStorageBindingRef: slot ? 'slot:document-storage' : 'binding-dep-a-target-b' })
      assert.deepEqual(await t.invalidations(), [
        ['document-storage-bindings', 'dep-a'], ['deployment-marketplace-installs', 'dep-a'],
        ['deployment-draft', 'dep-a'], ['deployment-workspace', 'dep-a'],
      ])
    })
  }

  await run('an existing opt-in slot does not repin composition when saving a binding', async t => {
    assert.equal(await t.page.getByRole('checkbox', { name: 'Use target-scoped document storage slot' }).isChecked(), true)
    await t.page.getByRole('button', { name: 'Mounted demo', exact: true }).click()
    await t.choose('Target profile', 'Staging profile')
    await t.page.getByRole('button', { name: 'Save binding', exact: true }).click()
    await t.settled()
    assert.deepEqual(t.requests.filter(request => request.method !== 'GET').map(request => request.path), ['/api/deployments/dep-a/document-storage-bindings'])
  }, { configuredSlot: true })

  await run('delete binding invalidates only the captured deployment', async t => {
    const gate = t.hold('DELETE', '/api/deployments/dep-a/document-storage-bindings/binding-dep-a-target-a')
    await t.page.getByRole('button', { name: 'Remove document storage binding', exact: true }).click()
    await gate.started
    await t.switchDeployment()
    gate.release()
    await t.settled()
    assert.deepEqual(await t.invalidations(), [['document-storage-bindings', 'dep-a']])
  })
  console.log(`Document Knowledge target race smoke: ${passed} scenarios passed; local mocks only, no credentials.`)
} finally {
  await browser?.close()
  await server.close()
}
