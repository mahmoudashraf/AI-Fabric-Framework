import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'brain-capabilities-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'brain-capabilities-screenshots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, server: { host: '127.0.0.1', port: 0, hmr: false },
  plugins: [{ name: 'brain-capabilities-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/__capabilities-entry.jsx') return '\0capabilities-entry.jsx' },
    transform(code, id) { if (id === '\0capabilities-entry.jsx') return transformWithEsbuild(code, 'capabilities-entry.jsx', { loader: 'jsx', jsx: 'automatic' }) },
    load(id) {
      if (id !== '\0capabilities-entry.jsx') return
      return `import React, { useCallback, useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { BrowserRouter, Routes, Route, useLocation, useParams } from 'react-router-dom';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { ThemeProvider, CssBaseline, Box, Button } from '@mui/material';
        import { createConsoleTheme } from '/src/theme/consoleTheme.ts';
        import { setPlatformAccessTokenProvider } from '/src/api/platformApi.ts';
        import { BrainWorkspaceContext, BrainEditorContext } from '/src/brain/BrainWorkspaceContext.tsx';
        import { BrainCapabilitiesPage } from '/src/brain/BrainCapabilitiesPage.tsx';
        setPlatformAccessTokenProvider(async () => 'local-fixture');
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } }); window.__client = client;
        function App() {
          const [editor, setEditor] = useState(null); const register = useCallback(value => setEditor(value), []);
          const location = useLocation(); const { brainId } = useParams();
          const targetProfileId = new URLSearchParams(location.search).get('targetProfileId') ?? '';
          const href = section => '/smart-brains/' + brainId + (section ? '/' + section : '') + '?targetProfileId=' + encodeURIComponent(targetProfileId);
          return <BrainWorkspaceContext.Provider value={{ brainId, targetProfileId, href, target: null, release: null, targetProfile: null }}><BrainEditorContext.Provider value={register}>
            <Box sx={{ height: { lg: '100dvh' }, minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
              <Box component='main' sx={{ flex: 1, minHeight: 0, p: 3, overflow: { lg: 'hidden' } }}><BrainCapabilitiesPage /></Box>
              <Box component='footer' sx={{ minHeight: 64, borderTop: 1, borderColor: 'divider', p: 2, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
                <span data-testid='editor-state'>{editor?.dirty ? 'Unsaved changes' : editor?.message ?? 'Shared draft'}</span>
                {editor?.error && <span role='alert'>{editor.error}</span>}
                {editor?.discard && <Button onClick={editor.discard}>Discard</Button>}
                {editor?.save && <Button variant='contained' disabled={editor.busy || editor.canSave === false} onClick={() => void editor.save()}>{editor.primaryLabel ?? 'Save draft'}</Button>}
              </Box>
            </Box>
          </BrainEditorContext.Provider></BrainWorkspaceContext.Provider>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><ThemeProvider theme={createConsoleTheme(new URLSearchParams(location.search).get('theme') === 'dark' ? 'dark' : 'light')}><CssBaseline /><BrowserRouter><Routes><Route path='/smart-brains/:brainId/*' element={<App />} /></Routes></BrowserRouter></ThemeProvider></QueryClientProvider>);`
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!new URL(req.url, 'http://fixture').pathname.startsWith('/smart-brains/')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__capabilities', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__capabilities-entry.jsx"></script></body></html>`))
    }) },
  }],
})

const contributions = { actionIds: ['open_ticket', 'read_ticket'], knowledgeSourceIds: [], conversationChannel: null }
const pricing = { pricingModel: 'FREE', requiresEntitlement: false }
const plugin = (id = 'support') => ({ id, displayName: id === 'support' ? 'Customer service' : 'Shipping API', shortDescription: 'Connect your service team', publisherDisplayName: 'Example publisher', pluginType: 'ACTION', pricing, contributions })
const installed = (extra = {}) => ({ id: 'install-a', deploymentId: 'brain-a', pluginId: 'support', pluginDisplayName: 'Customer service', pluginType: 'ACTION', pluginVersion: '1.0.0', status: 'ENABLED', config: { endpoint: 'https://service.example.test', opaque: { preserved: [1, 2] } }, secretRefs: { token: 'service-key', opaqueRef: 'preserve' }, contributions, readinessStatus: 'READY', liveState: 'DRAFT_ONLY', warnings: [], entitlement: { status: 'ACTIVE', requiresEntitlement: false }, updatedAt: '2026-11-07T00:00:00Z', ...extra })
const draft = () => ({ id: 'draft-a', deploymentId: 'brain-a', revisionNumber: 3, status: 'DRAFT',
  actionsConfig: { unknownRoot: { retained: true }, actions: [
    { name: 'open_ticket', displayName: 'Open a support request', description: 'Send a customer request to your service team', category: 'support', accessMode: 'WRITE_ONLY', requiresConfirmation: true, anonymousAllowed: false, params: [{ name: 'email', type: 'STRING', required: true }, { name: 'message', type: 'STRING', required: false }], future: { retained: [1, 2] }, route: { path: '/tickets', request: { body: { email: '{{email}}' } } } },
    { name: 'read_ticket', displayName: 'Track a request', description: 'Read the current request status', category: 'support', accessMode: 'READ', requiresConfirmation: false, anonymousAllowed: true },
    { name: 'lookup_order', description: 'Read an order', accessMode: 'READ' },
  ], webhookTargets: [{ id: 'receipt', urlSecretRef: 'receipt-url', signingSecretRef: 'receipt-signing', future: 'retained' }] },
  routingConfig: { connector: { upstream: { 'base-url': 'https://service.example.test' } }, actions: { open_ticket: { method: 'POST', path: '/tickets', future: { retained: true } }, future_action: { url: 'https://future.example.test', unknown: { keep: true } } }, futureRoot: ['retained'] }, providerConfig: {} })

await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, callback, options = {}) {
  const page = await browser.newPage({ viewport: options.viewport ?? { width: 1440, height: 900 } })
  page.setDefaultTimeout(15000)
  const requests = [], errors = [], unexpected = []
  let state = draft(), installs = options.empty ? [] : [installed()], failSave = false
  if (options.longLabels) state.actionsConfig.actions[0].displayName = 'AnExtremelyLongCapabilityNameWithoutSpacesThatMustRemainReadable'
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => new URL(route.request().url()).origin === baseUrl ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    const body = request.postDataJSON()
    requests.push({ path, method, body })
    const send = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    if (path === '/api/marketplace/plugins') return send([plugin(), plugin('shipping'), { ...plugin('model'), pluginType: 'INFERENCE_PROFILE', displayName: 'Model profile' }])
    if (path.startsWith('/api/marketplace/plugins/')) {
      const id = path.split('/').pop()
      return send({ plugin: plugin(id), versions: [{ id: `version-${id}`, version: '1.0.0', pricing,
        installForm: [{ id: 'endpoint', label: 'Service endpoint', type: 'url', required: true }, { id: 'token', label: 'API key reference', type: 'secretRef', required: false }], contributions }] })
    }
    if (path === '/api/deployments/brain-a/workspace') return send({ deployment: { name: 'Service AI', runtimeBaseUrl: options.runtime ? 'https://runtime.example.test' : null }, access: { canEdit: !options.readonly, canOperate: !options.readonly }, template: { name: 'Support' } })
    if (path === '/api/deployments/brain-a/draft') return send(state)
    if (path === '/api/deployments/brain-a/webhooks/overview') return send({ counts: { FAILED: 1 }, retryEligibleCount: 1, recentDeliveries: [{ id: 'delivery-a', eventType: 'request.created', actionName: 'open_ticket', targetRef: 'receipt', status: 'FAILED', attemptCount: 1, maxAttempts: 3, retryEligible: true }] })
    if (path === '/api/deployments/brain-a/webhooks/deliveries/delivery-a/retry') return send({ summaryMessage: 'Retry requested.' })
    if (path === '/api/deployment-drafts/draft-a' && method === 'PUT') {
      if (failSave) { failSave = false; return send({ message: 'Draft revision changed on the server.' }, 409) }
      state = { ...state, ...body, revisionNumber: state.revisionNumber + 1 }; return send(state)
    }
    if (path === '/api/deployments/brain-a/marketplace-installs') {
      if (method === 'GET') return send(installs)
      if (failSave) { failSave = false; return send({ message: 'Connection update failed.' }, 409) }
      const row = installed({ id: 'install-shipping', pluginId: body.pluginId, config: body.config, secretRefs: body.secretRefs, updatedAt: '2026-11-07T01:00:00Z' })
      installs.push(row); return send(row)
    }
    if (path === '/api/deployments/brain-a/marketplace-installs/install-a' && method === 'PUT') {
      if (failSave) { failSave = false; return send({ message: 'Connection update failed.' }, 409) }
      installs[0] = { ...installs[0], ...body, updatedAt: '2026-11-07T01:00:00Z' }; return send(installs[0])
    }
    if (path === '/api/deployments/brain-a/marketplace-installs/install-a/resolve') return send({ installId: 'install-a', status: 'RESOLVED' })
    if (path === '/api/deployments/brain-a/marketplace-installs/install-a' && method === 'DELETE') { installs = []; return send(null) }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected fixture request' }, 404)
  })
  const path = options.path ?? 'capabilities'
  try {
    await page.goto(`${baseUrl}/smart-brains/brain-a/${path}${path.includes('?') ? '&' : '?'}targetProfileId=target-exact${options.dark ? '&theme=dark' : ''}`)
    await callback({ page, requests, failNextSave: () => { failSave = true }, conflict: () => { state.revisionNumber += 1 },
      connectionConflict: () => { installs[0] = { ...installs[0], config: { ...installs[0].config, endpoint: 'https://current.example.test' }, updatedAt: '2026-11-07T02:00:00Z' } }, state: () => state })
    assert.deepEqual(errors, [], 'No browser errors')
    assert.deepEqual(unexpected, [], 'No unrelated/global API or external requests')
    passed++; console.log(`PASS ${name}`)
  } catch (error) {
    await page.screenshot({ path: join(screenshots, 'failure.png'), fullPage: true })
    console.error({ name, errors, unexpected, requests, screenshots,
      overflow: await page.evaluate(() => [...document.querySelectorAll('body *')].map(el => ({ tag: el.tagName, className: el.className?.baseVal ?? el.className, right: el.getBoundingClientRect().right, width: el.getBoundingClientRect().width })).filter(el => el.right > innerWidth + 1)),
      text: await page.locator('body').innerText() })
    throw error
  } finally { await page.close() }
}
const technicalView = async (page, name) => {
  await page.getByRole('combobox', { name: 'Technical view' }).click()
  await page.getByRole('option', { name, exact: true }).click()
}
const noOverflow = page => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)

try {
  await run('generic master/detail, stable deep links, search and close', async ({ page, requests }) => {
    await page.getByRole('heading', { name: 'Open a support request' }).waitFor()
    await page.getByText('Required before execution.', { exact: true }).waitFor()
    await page.getByText('email (required)', { exact: true }).waitFor()
    await page.getByRole('link', { name: /Track a request Read/ }).click()
    assert.match(page.url(), /install%3Ainstall-a%3Aaction%3Aread_ticket/)
    assert.match(page.url(), /targetProfileId=target-exact/)
    await page.reload(); await page.getByRole('heading', { name: 'Track a request' }).waitFor()
    await page.getByRole('link', { name: 'Close details' }).click()
    await page.getByRole('region', { name: 'Capability details' }).waitFor({ state: 'hidden' })
    assert.equal(await page.getByRole('region', { name: 'Capability details' }).count(), 0)
    await page.getByRole('textbox', { name: 'Find a capability' }).fill('lookup')
    assert.equal(await page.getByRole('list', { name: 'Connected capabilities' }).getByRole('link').count(), 1)
    assert.equal(requests.filter(item => item.method !== 'GET').length, 0)
  })
  await run('new install saves once through existing server command, not a fake second draft save', async ({ page, requests }) => {
    await page.getByRole('tab', { name: 'Available', exact: true }).click()
    await page.getByRole('link', { name: /Shipping API Connect/ }).click()
    await page.getByRole('heading', { name: 'Shipping API', exact: true }).waitFor()
    await page.getByRole('textbox', { name: 'Service endpoint' }).fill('https://shipping.example.test')
    await page.getByRole('button', { name: 'Add capability', exact: true }).click()
    await page.getByText('Draft updated; live configuration unchanged.', { exact: true }).waitFor()
    assert.equal(requests.filter(item => item.method === 'POST').length, 1)
    assert.equal(requests.filter(item => item.path.startsWith('/api/deployment-drafts/') && item.method !== 'GET').length, 0)
    await page.getByRole('button', { name: 'Save connection', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Save connection', exact: true }).isDisabled(), true)
    assert.equal(await page.getByText('Model profile', { exact: true }).count(), 0)
  })
  await run('connection update failure retains inputs, retry round-trips unknown fields and refs', async ({ page, requests, failNextSave }) => {
    await page.getByRole('link', { name: 'View connection' }).click()
    await page.getByRole('textbox', { name: 'Service endpoint' }).fill('https://new.example.test')
    failNextSave(); await page.getByRole('button', { name: 'Save connection' }).click()
    await page.getByText('Connection update failed.', { exact: true }).first().waitFor()
    assert.equal(await page.getByRole('textbox', { name: 'Service endpoint' }).inputValue(), 'https://new.example.test')
    await page.getByRole('button', { name: 'Save connection' }).click()
    await page.getByText('Draft updated; live configuration unchanged.', { exact: true }).waitFor()
    const saved = requests.filter(item => item.method === 'PUT').at(-1).body
    assert.deepEqual(saved.config.opaque, { preserved: [1, 2] })
    assert.deepEqual(saved.secretRefs, { token: 'service-key', opaqueRef: 'preserve' })
  })
  await run('full technical action/anonymous/route/webhook edits save together without loss', async ({ page, requests, state }) => {
    const editor = page.getByRole('textbox', { name: 'Actions JSON configuration' })
    await editor.waitFor()
    await page.getByTestId('editor-state').filter({ hasText: /^Shared draft$/ }).waitFor()
    const original = JSON.parse(await editor.inputValue()); original.futureRoot = { nested: ['retained'] }
    await editor.fill(JSON.stringify(original))
    await page.getByRole('checkbox').first().check()
    await technicalView(page, 'HTTP routes & connector')
    await page.getByRole('textbox', { name: 'Relative path', exact: true }).fill('/requests')
    await technicalView(page, 'Webhook targets & policies')
    await page.getByRole('textbox', { name: 'Signing secret ref' }).fill('new-signing-ref')
    assert.equal(await page.getByRole('button', { name: 'Save webhook changes' }).count(), 0)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByTestId('editor-state').filter({ hasText: 'Draft saved; live configuration unchanged.' }).waitFor()
    await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled().then(value => assert.equal(value, true))
    assert.deepEqual(state().actionsConfig.futureRoot, { nested: ['retained'] })
    assert.deepEqual(state().actionsConfig.actions[0].future, { retained: [1, 2] })
    assert.equal(state().actionsConfig.actions[0].requiresConfirmation, true)
    assert.equal(state().actionsConfig.actions[0].anonymousAllowed, true)
    assert.equal(state().actionsConfig.webhookTargets[0].signingSecretRef, 'new-signing-ref')
    assert.equal(state().routingConfig.actions.open_ticket.path, '/requests')
    assert.deepEqual(state().routingConfig.futureRoot, ['retained'])
    assert.deepEqual(state().routingConfig.actions.future_action, { url: 'https://future.example.test', unknown: { keep: true } })
    assert.equal(requests.filter(item => item.method === 'PUT').length, 1)
  }, { path: 'capabilities/actions' })
  await run('stale draft revision retains the local buffer and does not write', async ({ page, requests, conflict }) => {
    const editor = page.getByRole('textbox', { name: 'Actions JSON configuration' }); await editor.waitFor()
    const modified = JSON.parse(await editor.inputValue()); modified.custom = 'retained'
    await editor.fill(JSON.stringify(modified)); conflict()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText(/The saved draft changed/).first().waitFor()
    assert.equal(JSON.parse(await editor.inputValue()).custom, 'retained')
    assert.equal(requests.filter(item => item.method === 'PUT').length, 0)
    await page.getByRole('button', { name: 'Discard', exact: true }).click()
    assert.equal(JSON.parse(await editor.inputValue()).custom, undefined)
  }, { path: 'capabilities/actions' })
  await run('read-only connection has disabled inputs and no save command', async ({ page, requests }) => {
    await page.getByRole('link', { name: 'View connection' }).click()
    await page.getByRole('textbox', { name: 'Service endpoint' }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: 'Service endpoint' }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Save connection' }).count(), 0)
    assert.equal(requests.filter(item => item.method !== 'GET').length, 0)
  }, { readonly: true })
  await run('read-only technical editor retains navigation but disables all configuration inputs', async ({ page }) => {
    await page.getByRole('textbox', { name: 'Actions JSON configuration' }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: 'Actions JSON configuration' }).isDisabled(), true)
    await technicalView(page, 'HTTP routes & connector')
    assert.equal(await page.getByRole('textbox', { name: 'Relative path', exact: true }).isDisabled(), true)
    await technicalView(page, 'Webhook targets & policies')
    assert.equal(await page.getByRole('textbox', { name: 'URL secret ref' }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).count(), 0)
  }, { readonly: true, path: 'capabilities/actions' })
  await run('invalid action JSON is visible and blocks route edits without losing the buffer', async ({ page, requests }) => {
    const editor = page.getByRole('textbox', { name: 'Actions JSON configuration' }); await editor.waitFor()
    await editor.fill('{invalid JSON')
    await page.getByText(/Action JSON is invalid/).waitFor()
    await technicalView(page, 'HTTP routes & connector')
    assert.equal(await page.getByRole('textbox', { name: 'Relative path', exact: true }).isDisabled(), true)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: /JSON/ }).last().waitFor()
    await technicalView(page, 'Action JSON & anonymous access')
    assert.equal(await editor.inputValue(), '{invalid JSON')
    assert.equal(requests.filter(item => item.method === 'PUT').length, 0)
  }, { path: 'capabilities/actions' })
  await run('connection conflict retains edits and discard loads the current server version', async ({ page, requests, connectionConflict }) => {
    await page.getByRole('link', { name: 'View connection' }).click()
    const input = page.getByRole('textbox', { name: 'Service endpoint' }); await input.fill('https://local.example.test')
    connectionConflict()
    await page.getByRole('button', { name: 'Save connection' }).click()
    await page.getByText(/This connection changed/).first().waitFor()
    assert.equal(await input.inputValue(), 'https://local.example.test')
    assert.equal(requests.filter(item => item.method === 'PUT').length, 0)
    await page.getByRole('button', { name: 'Discard', exact: true }).click()
    assert.equal(await input.inputValue(), 'https://current.example.test')
  })
  await run('connection lifecycle retains existing enable, recompile and confirmed removal commands', async ({ page, requests }) => {
    await page.getByRole('link', { name: 'View connection' }).click()
    await page.getByRole('button', { name: 'Technical connection settings' }).click()
    await page.getByRole('button', { name: 'Disable', exact: true }).click()
    await page.getByRole('button', { name: 'Enable', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Recompile draft' }).click()
    await page.waitForFunction(() => ![...document.querySelectorAll('button')].find(button => button.textContent === 'Recompile draft')?.disabled)
    await page.getByRole('button', { name: 'Remove connection', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    assert.equal(requests.filter(item => item.method === 'DELETE').length, 0)
    await page.getByRole('button', { name: 'Remove connection', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Remove connection', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    assert.equal(requests.filter(item => item.method === 'DELETE').length, 1)
    assert.equal(requests.filter(item => item.path.endsWith('/resolve')).length, 1)
  })
  await run('live webhook deliveries retain the existing operator retry operation', async ({ page, requests }) => {
    await page.getByRole('combobox', { name: 'Technical view' }).waitFor()
    await technicalView(page, 'Live webhook deliveries')
    await page.getByRole('button', { name: 'Retry', exact: true }).click()
    await page.getByText('Retry requested.', { exact: true }).waitFor()
    assert.equal(requests.filter(item => item.path.endsWith('/delivery-a/retry') && item.method === 'POST').length, 1)
  }, { path: 'capabilities/actions', runtime: true })
  await run('unknown capability never silently selects another task', async ({ page }) => {
    await page.getByText(/This capability is not available in this Brain/).waitFor()
    assert.equal(await page.getByRole('heading', { name: 'Open a support request' }).count(), 0)
  }, { path: 'capabilities/unknown-id' })
  await run('mobile detail deep link uses a full-width task and contains long metadata labels', async ({ page }) => {
    await page.getByRole('heading', { name: /AnExtremelyLongCapabilityName/ }).waitFor()
    assert.equal(await noOverflow(page), true)
    assert.equal(await page.getByRole('list', { name: 'Connected capabilities' }).isVisible(), false)
    await page.screenshot({ path: join(screenshots, '320-detail.png'), fullPage: true })
  }, { viewport: { width: 320, height: 844 }, path: 'capabilities/install%3Ainstall-a%3Aaction%3Aopen_ticket', longLabels: true })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 1366, height: 768 }, { width: 1280, height: 800 }, { width: 390, height: 844 }]) {
    for (const dark of [false, true]) await run(`layout ${viewport.width} ${dark ? 'dark' : 'light'}`, async ({ page }) => {
      await page.getByRole('heading', { name: 'Open a support request' }).waitFor()
      assert.equal(await noOverflow(page), true)
      if (viewport.width >= 1200) assert.equal(await page.evaluate(() => document.documentElement.scrollHeight <= innerHeight), true)
      await page.screenshot({ path: join(screenshots, `${viewport.width}-${dark ? 'dark' : 'light'}.png`), fullPage: true })
    }, { viewport, dark })
  }
  console.log(`${passed} browser scenarios passed. Screenshots: ${screenshots}`)
} finally {
  await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true })
}
