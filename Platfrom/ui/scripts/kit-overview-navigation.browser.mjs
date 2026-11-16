import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'kit-navigation-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'kit-navigation-screenshots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'kit-overview-navigation-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__kit-entry.jsx') return '\0kit-entry.jsx'
      if (id === '/__kit-auth' || id.endsWith('/auth/PlatformAuthProvider')) return '\0kit-auth.js'
    },
    transform(code, id) {
      if (id === '\0kit-entry.jsx') return transformWithEsbuild(code, 'kit-entry.jsx', { loader: 'jsx', jsx: 'automatic' })
    },
    load(id) {
      if (id === '\0kit-auth.js') return `export const usePlatformAuth = () => ({ session: {
        authenticated: true, role: 'CUSTOMER_ADMIN', actorId: 'actor-a', customerId: 'customer-a', authenticationMode: 'SESSION'
      } });`
      if (id === '\0kit-entry.jsx') return `
        import React from 'react'; import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
        import { MemoryRouter, Routes, Route, useLocation, useNavigate } from 'react-router-dom';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { ExperienceKitOverview } from '/src/components/kits/ExperienceKitOverview.tsx';
        import '/src/pages/ExperienceKits.css';
        const mode = new URLSearchParams(location.search).get('theme') || 'light';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } });
        window.__kitClient = client;
        function App() {
          const current = useLocation(); window.__route = current.pathname + current.search;
          window.__kitNavigate = useNavigate();
          useQuery({ queryKey: ['experience-kits', 'customer-a', 'list'], queryFn: () => fetch('/api/customer-experience-kits').then(r => r.json()) });
          useQuery({ queryKey: ['ai-workspace-installations', 'customer-a'], queryFn: () => fetch('/api/kit-fixture-installations').then(r => r.json()) });
          return <main className="ek-page" data-kit-theme={mode} style={{ padding: 20, background: mode === 'light' ? '#f7f9fa' : '#111619', minHeight: '100vh' }}>
            <Routes><Route path="/kits/kit-a" element={<ExperienceKitOverview customerId="customer-a" id="kit-a" />} />
              <Route path="/kits" element={<h1>My kits</h1>} />
              <Route path="/smart-brains/:id" element={<h1>Existing Brain destination</h1>} />
              <Route path="/ai-workspaces" element={<h1>Existing workspace editor destination</h1>} /></Routes>
          </main>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}>
          <MemoryRouter initialEntries={['/kits/kit-a']}><ThemeProvider theme={createTheme({ palette: { mode } })}><CssBaseline /><App /></ThemeProvider></MemoryRouter>
        </QueryClientProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__kit') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__kit', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__kit-entry.jsx"></script></body></html>`))
    }) },
  }],
})

const workspace = (id, deliveryMode) => ({ installationId: id, customerId: 'customer-a', displayName: deliveryMode === 'embedded' ? 'Website AI' : 'Your company AI',
  deploymentId: 'dep-a', releaseId: null, status: 'DRAFT', ready: false, deliveryMode, appSlug: 'example-ai',
  appUrl: 'https://example-ai.apps.loomai.pro', allowedOrigins: ['https://example.test'], domains: [], readinessChecks: [
    { code: 'ASSIGNMENT', status: 'BLOCKED', message: 'A verified release binding is required.' },
  ], configuration: { theme: { primaryColor: '#28bfa6' } }, experiencePackCode: 'generic-workspace', rowVersion: 1 })
const makeKit = (deliveryMode, missing = false) => ({ id: 'kit-a', kitCode: 'ai-customer-experience', kitVersion: '1.0.0', name: 'AI Customer Experience',
  deploymentId: 'dep-a', offerId: 'offer-a', targetProfileId: 'dtp-coolify-staging-behavior', installationId: missing ? null : 'awi-a', embeddedInstallationId: deliveryMode === 'both' ? 'awi-embed' : null,
  deliveryMode, revision: 1, humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null,
  updatedAt: '2026-11-15T12:00:00Z', workspace: missing ? null : workspace('awi-a', deliveryMode === 'embedded' ? 'embedded' : 'standalone'),
  embeddedWorkspace: deliveryMode === 'both' ? workspace('awi-embed', 'embedded') : null })

await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
function gate() {
  let release
  const promise = new Promise(resolve => { release = resolve })
  return { promise, release }
}
async function run(name, check, { theme = 'light', width = 1440, deliveryMode = 'standalone', missing = false,
  kitOverrides = {}, releaseRecords = [], allowWrites = false } = {}) {
  const page = await browser.newPage({ viewport: { width, height: width < 600 ? 844 : 1000 } })
  page.setDefaultTimeout(15000)
  const errors = [], unexpected = [], requests = []
  const state = { kit: { ...makeKit(deliveryMode, missing), ...kitOverrides }, saveStatus: 200, saveGate: null, refreshGate: null, saved: false }
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const method = route.request().method(), path = new URL(route.request().url()).pathname
    const body = route.request().postDataJSON()
    requests.push({ method, path, body })
    const send = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    if (method === 'GET' && path === '/api/customer-experience-kits/kit-a') return send(state.kit)
    if (method === 'PUT' && path === '/api/customer-experience-kits/kit-a' && allowWrites) {
      if (state.saveGate) await state.saveGate.promise
      if (state.saveStatus !== 200) return route.fulfill({ status: state.saveStatus, json: { message: 'Kit configuration changed. Review before saving.' } })
      const updateApp = app => app && ({ ...app, displayName: body.workspaceName, rowVersion: app.rowVersion + 1,
        configuration: { ...app.configuration, theme: { primaryColor: body.brandColor } } })
      state.kit = { ...state.kit, name: body.name, revision: state.kit.revision + 1,
        workspace: updateApp(state.kit.workspace), embeddedWorkspace: updateApp(state.kit.embeddedWorkspace) }
      state.saved = true
      return send(state.kit)
    }
    if (method === 'GET' && ['/api/customer-experience-kits', '/api/kit-fixture-installations'].includes(path)) {
      if (state.saved && state.refreshGate) await state.refreshGate.promise
      return send(path === '/api/customer-experience-kits' ? [state.kit] : [state.kit.workspace, state.kit.embeddedWorkspace].filter(Boolean))
    }
    if (method === 'GET' && path === '/api/customer-self-service/deployments') return send([{ deploymentId: 'dep-a', name: 'Your AI assistant', status: 'DRAFT', offerId: 'offer-a' }])
    if (method === 'GET' && path === '/api/deployments/dep-a/releases') return send(releaseRecords)
    if (method === 'GET' && path === '/api/deployments/dep-a/desk') return send({ status: 'DISABLED' })
    if (method === 'GET' && path === '/api/customer-experience-kits/catalogue') return send([{ definition: { code: 'ai-support-experience' }, ticketProviders: [] }])
    if (method === 'GET' && path === '/api/customer-self-service/catalogue/offer-a') return send({ id: 'offer-a', targets: [] })
    if (method === 'GET' && path === '/api/deployments/dep-a/document-storage-bindings') return send([])
    if (method === 'GET' && path === '/api/deployments/dep-a/document-storage/connections') return send({ connections: [], hostedAvailable: false, supportedExtensions: ['txt'], maxUploadBytes: 1048576 })
    unexpected.push(`${method} ${path}`)
    return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Unexpected request' }) })
  })
  try {
    await page.goto(`${baseUrl}/__kit?theme=${theme}`)
    await page.getByRole('heading', { name: 'AI Customer Experience', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Kit AI guide', exact: true }).waitFor()
    const panel = page.getByRole('complementary', { name: 'Selected product details' })
    await check({ page, panel, state, requests })
    assert.deepEqual(errors, [], 'No browser runtime errors')
    assert.deepEqual(unexpected, [], 'All requests are controlled fixture reads')
    assert.ok(requests.every(request => request.method === 'GET' || (allowWrites && request.method === 'PUT'
      && request.path === '/api/customer-experience-kits/kit-a')), 'Only explicit Kit edits may write; never publish, activate, reassign or invoke AI')
    passed++; console.log(`PASS ${name}`)
  } catch (error) {
    console.error({ name, errors, unexpected, requests, body: await page.locator('body').innerText() })
    await page.screenshot({ path: join(screenshots, `failure-${passed}.png`), fullPage: true })
    throw error
  } finally { state.saveGate?.release(); state.refreshGate?.release(); await page.close() }
}

async function focusedPanel(panel) {
  await panel.evaluate(element => new Promise((resolve, reject) => {
    const started = performance.now()
    function check() {
      const rect = element.getBoundingClientRect()
      if (document.activeElement === element && rect.top >= 0 && rect.top + 80 <= innerHeight && element.scrollTop === 0) return resolve()
      if (performance.now() - started > 5000) return reject(new Error(`Inspector was not focused, reset and revealed: ${JSON.stringify({ focused: document.activeElement === element, top: rect.top, scrollTop: element.scrollTop, viewportHeight: innerHeight })}`))
      requestAnimationFrame(check)
    }
    check()
  }))
}

try {
  for (const theme of ['light', 'dark']) for (const width of [1440, 390]) {
    await run(`${theme} ${width}: every product header reveals details, including repeated selection and reopening`, async ({ page, panel }) => {
      assert.equal(await panel.evaluate(element => document.activeElement === element), false, 'Default panel does not steal focus on entry')
      const engage = page.getByRole('button', { name: 'Inspect Engage experience', exact: true })
      for (let i = 0; i < 2; i++) {
        if (i) await panel.evaluate(element => { element.scrollTop = element.scrollHeight })
        await engage.click(); await focusedPanel(panel)
        await panel.getByRole('heading', { name: 'Your company AI', exact: true }).waitFor()
        assert.equal(await engage.getAttribute('aria-expanded'), 'true')
      }
      await page.screenshot({ path: join(screenshots, `${theme}-${width}-workspace.png`) })
      await page.getByRole('button', { name: /Inspect AI / }).click(); await focusedPanel(panel)
      await panel.getByRole('heading', { name: 'Your AI assistant', exact: true }).waitFor()
      await page.getByRole('button', { name: 'Inspect human support', exact: true }).click(); await focusedPanel(panel)
      await panel.getByRole('heading', { name: 'Human support', exact: true }).waitFor()
      await page.getByRole('button', { name: 'Close kit product details', exact: true }).click()
      assert.equal(await panel.count(), 0)
      assert.equal(await page.getByRole('button', { name: 'Inspect human support', exact: true }).evaluate(element => document.activeElement === element), true)
      await engage.click(); await focusedPanel(panel)
      assert.equal(await page.getByRole('button', { name: 'View details', exact: true }).count(), 0)
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'No horizontal overflow')
    }, { theme, width })
  }
  for (const deliveryMode of ['standalone', 'embedded', 'both']) {
    await run(`${deliveryMode}: Configure workspace opens the canonical editor with Kit origin`, async ({ page }) => {
      const configure = page.getByRole('link', { name: 'Configure workspace', exact: true })
      const target = new URL(await configure.getAttribute('href'), baseUrl)
      assert.equal(target.pathname, '/ai-workspaces')
      assert.equal(target.searchParams.get('resourceId'), 'awi-a')
      assert.equal(target.searchParams.get('deploymentId'), 'dep-a')
      assert.equal(target.searchParams.get('editor'), 'configuration')
      assert.equal(target.searchParams.get('kitId'), 'kit-a')
      await configure.click()
      await page.getByRole('heading', { name: 'Existing workspace editor destination', exact: true }).waitFor()
      assert.equal(await page.evaluate(() => window.__route), `${target.pathname}${target.search}`)
    }, { deliveryMode })
  }
  await run('both: preview and each delivery editor retain their own installation and Kit origin', async ({ page, panel }) => {
    for (const [name, parameter, installationId] of [
      ['Preview workspace', 'resourceId', 'awi-a'],
      ['Configure standalone app', 'resourceId', 'awi-a'],
      ['Configure website embed', 'resourceId', 'awi-embed'],
      ['See your customer experience', 'resourceId', 'awi-a'],
    ]) {
      const link = (name === 'Preview workspace' ? page : panel).getByRole('link', { name, exact: true })
      const target = new URL(await link.getAttribute('href'), baseUrl)
      assert.equal(target.searchParams.get(parameter), installationId)
      assert.equal(target.searchParams.get('kitId'), 'kit-a')
    }
  }, { deliveryMode: 'both' })
  await run('missing workspace retains setup warning and navigable app-list fallback', async ({ page }) => {
    await page.getByRole('alert').filter({ hasText: 'An Engage app is missing.' }).waitFor()
    assert.equal(await page.getByRole('link', { name: 'Configure workspace', exact: true }).count(), 0)
    const link = page.locator('.ek-product-resource').nth(1).getByRole('link', { name: 'Open Engage apps', exact: true })
    assert.equal(await link.getAttribute('href'), '/ai-workspaces')
  }, { missing: true })
  for (const deliveryMode of ['standalone', 'embedded', 'both']) {
    await run(`${deliveryMode}: first Brain entry retains the Kit-selected behavior target`, async ({ page, panel }) => {
      for (const link of [page.getByRole('link', { name: 'Set up your assistant', exact: true }), panel.locator('.ek-connected-deployment')]) {
        const target = new URL(await link.getAttribute('href'), baseUrl)
        assert.equal(target.searchParams.get('targetProfileId'), 'dtp-coolify-staging-behavior')
        assert.equal(target.searchParams.has('releaseId'), false)
      }
      await page.getByRole('button', { name: /Inspect AI / }).click()
      const configure = panel.getByRole('link', { name: /^Configure (?:smart )?brain$/i })
      assert.equal(new URL(await configure.getAttribute('href'), baseUrl).searchParams.get('targetProfileId'), 'dtp-coolify-staging-behavior')
      await configure.click()
      await page.getByRole('heading', { name: 'Existing Brain destination', exact: true }).waitFor()
      assert.equal(new URL(await page.evaluate(() => window.__route), baseUrl).searchParams.get('targetProfileId'), 'dtp-coolify-staging-behavior')
    }, { deliveryMode })
  }
  await run('Brain entry retains the exact assigned production release instead of a newer attempt or Kit bootstrap', async ({ page, panel }) => {
    await panel.getByText('Connected version', { exact: true }).waitFor()
    const target = new URL(await panel.locator('.ek-connected-deployment').getAttribute('href'), baseUrl)
    assert.equal(target.searchParams.get('targetProfileId'), 'production-exact')
    assert.equal(target.searchParams.get('releaseId'), 'assigned')
    const setup = new URL(await page.getByRole('link', { name: 'Set up your assistant', exact: true }).getAttribute('href'), baseUrl)
    assert.equal(setup.search, target.search)
  }, { kitOverrides: { workspace: { ...workspace('awi-a', 'standalone'), releaseId: 'assigned' } }, releaseRecords: [
    { id: 'assigned', deploymentId: 'dep-a', targetProfileId: 'production-exact', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', createdAt: '2026-11-14T10:00:00Z' },
    { id: 'newer', deploymentId: 'dep-a', targetProfileId: 'dtp-coolify-staging', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', createdAt: '2026-11-15T10:00:00Z' },
  ] })
  for (const active of [false, true]) for (const deliveryMode of ['embedded', 'both']) {
    const app = { ...workspace(deliveryMode === 'both' ? 'awi-embed' : 'awi-a', 'embedded'), status: active ? 'ACTIVE' : 'DRAFT' }
    await run(`${deliveryMode}: website snippet reflects recorded ${app.status} state without claiming health`, async ({ panel }) => {
      const snippet = panel.locator('.ek-install-snippet')
      assert.ok((await snippet.innerText()).includes(active ? 'This website app is active.' : 'Activate this website app when setup is complete.'))
      if (active) assert.ok(!(await snippet.innerText()).includes('Activate this website app'))
    }, { deliveryMode, kitOverrides: deliveryMode === 'both' ? { embeddedWorkspace: app } : { workspace: app } })
  }
  for (const deliveryMode of ['standalone', 'embedded', 'both']) {
    await run(`${deliveryMode}: HTTP 200 branding save closes before delayed cache refresh and reopens with saved edits`, async ({ page, state, requests }) => {
      state.saveGate = gate(); state.refreshGate = gate()
      await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
      const edit = page.getByRole('dialog')
      await edit.getByLabel(/^Workspace name/).fill('Manually branded workspace')
      await edit.getByRole('button', { name: 'Brand color #409aff', exact: true }).click()
      await page.screenshot({ path: join(screenshots, `edit-${deliveryMode}.png`) })
      assert.equal(requests.filter(request => request.method !== 'GET').length, 0, 'Opening and manual editing do not save')
      await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
      await edit.getByRole('button', { name: 'Saving...', exact: true }).waitFor()
      assert.equal(await edit.getByRole('button', { name: 'Cancel', exact: true }).isDisabled(), true)
      assert.equal(await edit.getByLabel(/^Workspace name/).isDisabled(), true)
      await page.keyboard.press('Escape'); assert.equal(await edit.isVisible(), true)
      await page.mouse.click(5, 5); assert.equal(await edit.isVisible(), true)
      await edit.locator('form').evaluate(form => form.requestSubmit())
      state.saveGate.release()
      await edit.waitFor({ state: 'hidden' })
      assert.equal(state.saved, true)
      const writes = requests.filter(request => request.method === 'PUT')
      assert.equal(writes.length, 1, 'Busy submit cannot duplicate the save')
      assert.deepEqual(writes[0].body, {
        name: 'AI Customer Experience', workspaceName: 'Manually branded workspace', brandColor: '#409aff', deliveryMode,
        ...(deliveryMode !== 'embedded' ? { appSlug: 'example-ai' } : {}),
        ...(deliveryMode !== 'standalone' ? { websiteOrigin: 'https://example.test' } : {}),
        humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null,
        expectedRevision: 1, expectedWorkspaceRevision: 1, expectedEmbeddedWorkspaceRevision: deliveryMode === 'both' ? 17 : null,
      })
      await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
      await edit.waitFor()
      assert.equal(await edit.getByLabel(/^Workspace name/).inputValue(), 'Manually branded workspace')
      assert.equal(await edit.getByRole('button', { name: 'Brand color #409aff', exact: true }).getAttribute('aria-pressed'), 'true')
      await edit.getByLabel(/^Workspace name/).fill('Unsaved manual buffer')
      await edit.getByRole('button', { name: 'Cancel', exact: true }).click()
      await edit.waitFor({ state: 'hidden' })
      state.refreshGate.release()
      await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
      assert.equal(await edit.getByLabel(/^Workspace name/).inputValue(), 'Manually branded workspace')
      await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
      await edit.waitFor({ state: 'hidden' })
      const second = requests.filter(request => request.method === 'PUT')[1].body
      assert.equal(second.expectedRevision, 2)
      assert.equal(second.expectedWorkspaceRevision, 2)
      assert.equal(second.expectedEmbeddedWorkspaceRevision, deliveryMode === 'both' ? 18 : null)
      assert.equal(state.kit.workspace.status, 'DRAFT')
      if (state.kit.embeddedWorkspace) assert.equal(state.kit.embeddedWorkspace.status, 'DRAFT')
      await page.screenshot({ path: join(screenshots, `saved-${deliveryMode}.png`) })
    }, { deliveryMode, allowWrites: true, theme: deliveryMode === 'both' ? 'dark' : 'light', width: deliveryMode === 'embedded' ? 390 : 1440,
      kitOverrides: deliveryMode === 'both' ? { embeddedWorkspace: { ...workspace('awi-embed', 'embedded'), rowVersion: 17 } } : {} })
  }
  await run('409 conflict retains manual edits, both canonical editor links and original concurrency revisions', async ({ page, state, requests }) => {
    state.saveStatus = 409
    await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
    const edit = page.getByRole('dialog')
    await edit.getByLabel(/^Workspace name/).fill('Keep this manual edit')
    await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
    await edit.getByRole('alert').filter({ hasText: 'Review the latest app configuration in its editor before saving.' }).waitFor()
    assert.equal(await edit.getByLabel(/^Workspace name/).inputValue(), 'Keep this manual edit')
    for (const [name, id] of [['Open app editor', 'awi-a'], ['Open website editor', 'awi-embed']]) {
      const target = new URL(await edit.getByRole('link', { name, exact: true }).getAttribute('href'), baseUrl)
      assert.equal(target.searchParams.get('resourceId'), id)
      assert.equal(target.searchParams.get('kitId'), 'kit-a')
    }
    assert.equal(state.kit.revision, 1)
    state.saveStatus = 200
    await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
    await edit.waitFor({ state: 'hidden' })
    const writes = requests.filter(request => request.method === 'PUT')
    assert.equal(writes.length, 2)
    assert.deepEqual(writes[0].body, writes[1].body, 'No silent revision rebase or lost manual fields')
  }, { deliveryMode: 'both', allowWrites: true })
  await run('late success from an unmounted form cannot close a newly opened editor', async ({ page, state }) => {
    state.saveGate = gate()
    await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
    const edit = page.getByRole('dialog')
    await edit.getByLabel(/^Workspace name/).fill('Old form')
    await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
    await edit.getByRole('button', { name: 'Saving...', exact: true }).waitFor()
    await page.evaluate(() => window.__kitNavigate('/kits'))
    await page.getByRole('heading', { name: 'My kits', exact: true }).waitFor()
    await page.evaluate(() => window.__kitNavigate('/kits/kit-a'))
    await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
    await edit.getByLabel(/^Workspace name/).fill('New form manual buffer')
    const response = page.waitForResponse(response => response.request().method() === 'PUT')
    state.saveGate.release(); await response
    await page.waitForFunction(() => window.__kitClient.isMutating() === 0)
    assert.equal(await edit.isVisible(), true)
    assert.equal(await edit.getByLabel(/^Workspace name/).inputValue(), 'New form manual buffer')
    assert.equal(await page.evaluate(() => window.__kitClient.getQueryData(['experience-kits', 'customer-a', 'detail', 'kit-a']).revision), 1)
    await edit.getByRole('button', { name: 'Cancel', exact: true }).click()
  }, { allowWrites: true })
  await run('foreign-customer installations retain the ownership boundary and cannot expose an editor', async ({ page, state, requests }) => {
    const foreign = { ...state.kit, embeddedWorkspace: { ...state.kit.embeddedWorkspace, customerId: 'foreign-customer' } }
    await page.evaluate(value => window.__kitClient.setQueryData(['experience-kits', 'customer-a', 'detail', 'kit-a'], value), foreign)
    await page.getByRole('alert').filter({ hasText: 'The returned installation does not match this customer account.' }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Edit your experience', exact: true }).count(), 0)
    assert.ok(requests.every(request => request.method === 'GET'))
  }, { deliveryMode: 'both' })
  console.log(JSON.stringify({ passed, screenshots }, null, 2))
} finally {
  await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true })
}
