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
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { ExperienceKitOverview } from '/src/components/kits/ExperienceKitOverview.tsx';
        import '/src/pages/ExperienceKits.css';
        const mode = new URLSearchParams(location.search).get('theme') || 'light';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } });
        function App() {
          const current = useLocation(); window.__route = current.pathname + current.search;
          return <main className="ek-page" data-kit-theme={mode} style={{ padding: 20, background: mode === 'light' ? '#f7f9fa' : '#111619', minHeight: '100vh' }}>
            <Routes><Route path="/kits/kit-a" element={<ExperienceKitOverview customerId="customer-a" id="kit-a" />} />
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
  deploymentId: 'dep-a', offerId: 'offer-a', targetProfileId: 'stage-a', installationId: missing ? null : 'awi-a', embeddedInstallationId: deliveryMode === 'both' ? 'awi-embed' : null,
  deliveryMode, revision: 1, humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null,
  updatedAt: '2026-11-15T12:00:00Z', workspace: missing ? null : workspace('awi-a', deliveryMode === 'embedded' ? 'embedded' : 'standalone'),
  embeddedWorkspace: deliveryMode === 'both' ? workspace('awi-embed', 'embedded') : null })

await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, check, { theme = 'light', width = 1440, deliveryMode = 'standalone', missing = false } = {}) {
  const page = await browser.newPage({ viewport: { width, height: width < 600 ? 844 : 1000 } })
  page.setDefaultTimeout(15000)
  const errors = [], unexpected = [], requests = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const method = route.request().method(), path = new URL(route.request().url()).pathname
    requests.push({ method, path })
    const send = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    if (method === 'GET' && path === '/api/customer-experience-kits/kit-a') return send(makeKit(deliveryMode, missing))
    if (method === 'GET' && path === '/api/customer-self-service/deployments') return send([{ deploymentId: 'dep-a', name: 'Your AI assistant', status: 'DRAFT', offerId: 'offer-a' }])
    if (method === 'GET' && path === '/api/deployments/dep-a/releases') return send([])
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
    await check({ page, panel })
    assert.deepEqual(errors, [], 'No browser runtime errors')
    assert.deepEqual(unexpected, [], 'All requests are controlled fixture reads')
    assert.ok(requests.every(request => request.method === 'GET'), 'Navigation never mutates a resource, creates an assistant session or invokes AI')
    passed++; console.log(`PASS ${name}`)
  } catch (error) {
    console.error({ name, errors, unexpected, requests, body: await page.locator('body').innerText() })
    await page.screenshot({ path: join(screenshots, `failure-${passed}.png`), fullPage: true })
    throw error
  } finally { await page.close() }
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
      ['Preview workspace', 'previewInstallationId', 'awi-a'],
      ['Configure standalone app', 'resourceId', 'awi-a'],
      ['Configure website embed', 'resourceId', 'awi-embed'],
      ['See your customer experience', 'previewInstallationId', 'awi-a'],
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
  console.log(JSON.stringify({ passed, screenshots }, null, 2))
} finally {
  await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true })
}
