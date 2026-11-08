import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'staff-kits-vite-'))
const screenshots = await mkdtemp(join(tmpdir(), 'staff-kits-screenshots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'staff-kits-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__kits-entry.jsx') return '\0kits-entry.jsx'
      if (id === '/__kits-auth' || id.endsWith('/auth/PlatformAuthProvider')) return '\0kits-auth.js'
    },
    transform(code, id) {
      if (id === '\0kits-entry.jsx') return transformWithEsbuild(code, 'kits-entry.jsx', { loader: 'jsx', jsx: 'automatic' })
    },
    load(id) {
      if (id === '\0kits-auth.js') return `import { createContext, useContext } from 'react';
        export const Auth = createContext(null); export const usePlatformAuth = () => useContext(Auth);`
      if (id === '\0kits-entry.jsx') return `
        import React, { useState } from 'react'; import { createRoot } from 'react-dom/client';
        import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
        import { MemoryRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
        import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
        import { Auth } from '/__kits-auth';
        import { StaffExperienceKitsPage } from '/src/pages/StaffExperienceKitsPage.tsx';
        import { ExperienceKitSetup } from '/src/components/kits/ExperienceKitSetup.tsx';
        const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } });
        window.__queryClient = client;
        function App() {
          const navigate = useNavigate(), current = useLocation();
          const [session, setSession] = useState({ authenticated: true, role: 'PLATFORM_ADMIN', actorId: 'staff-a' });
          window.__navigate = navigate; window.__session = patch => setSession(value => ({ ...value, ...patch }));
          window.__path = current.pathname;
          return <Auth.Provider value={{ session, isLoading: false }}><Routes>
            <Route path="/customers/:customerId/kits" element={<StaffExperienceKitsPage />} />
            <Route path="/customers/:customerId/kits/new/:code" element={<StaffExperienceKitsPage />} />
            <Route path="/customers/:customerId/kits/:id" element={<StaffExperienceKitsPage />} />
            <Route path="/customer-setup" element={<ExperienceKitSetup customerId="customer-a" actorId="customer-admin-a" code="ai-customer-experience" />} />
            <Route path="/kits/:id" element={<h1>Customer kit created</h1>} />
            <Route path="/users" element={<h1>Existing user directory</h1>} />
          </Routes></Auth.Provider>;
        }
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[new URLSearchParams(location.search).get('route') || '/customers/customer-a/kits/new/ai-customer-experience']}>
          <ThemeProvider theme={createTheme()}><CssBaseline /><main style={{ maxWidth: 1280, padding: 16, margin: 'auto' }}><App /></main></ThemeProvider>
        </MemoryRouter></QueryClientProvider>);
      `
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (new URL(req.url, 'http://fixture').pathname !== '/__kits') return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__kits', `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div>
        <script>window.__PLATFORM_RUNTIME_CONFIG__ = { apiBaseUrl: location.origin };</script><script type="module" src="/__kits-entry.jsx"></script></body></html>`))
    }) },
  }],
})

const catalogue = (customerId, code = 'ai-customer-experience') => [{ definition: { code, version: '1.0.0', name: code === 'ai-support-experience' ? 'AI Support Experience' : 'AI Customer Experience', summary: 'Customer knowledge and enquiries',
  description: 'Approved business knowledge', experiencePackCode: code === 'ai-support-experience' ? 'support' : 'generic-workspace', defaultWorkspaceName: 'Company AI', features: [], demoUrl: null },
  offers: [{ id: `offer-${customerId}`, name: `Template for ${customerId}`, entitlement: { customerId, status: 'ACTIVE', revision: 1 }, settings: [],
    targets: [{ id: `stage-${customerId}`, intent: 'CUSTOMER_STAGING', blockers: [] }, { id: `production-${customerId}`, intent: 'CUSTOMER_PRODUCTION', blockers: [] }], blockers: [], bindingSlots: [],
    workspaceProfiles: [{ id: `profile-${customerId}`, label: 'Company workspace', theme: {}, interaction: { configuration: {} } }] }], ticketProviders: [] }]
const kit = (customerId, values = {}) => ({ id: 'kit-one', kitCode: 'ai-customer-experience', kitVersion: '1.0.0', name: 'Saved kit',
  deploymentId: `dep-${customerId}`, consumerId: 'consumer-one', installationId: 'app-one', embeddedInstallationId: null,
  offerId: `offer-${customerId}`, targetProfileId: `stage-${customerId}`, revision: 3, humanSupportEnabled: false,
  ticketingEnabled: false, ticketProvider: null, deliveryMode: 'standalone',
  workspace: { customerId, installationId: 'app-one', rowVersion: 8, displayName: 'Saved AI', appSlug: 'saved-ai',
    configuration: { theme: { primaryColor: '#28bfa6' } }, experiencePackCode: 'generic-workspace', allowedOrigins: [], status: 'DRAFT', ready: false, releaseId: null },
  ...values })

await server.listen()
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, test, options = {}) {
  const page = await browser.newPage({ viewport: options.mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 } })
  page.setDefaultTimeout(12000)
  const requests = [], unexpected = [], errors = [], gates = new Map(), releases = []
  let prepareFailure = options.prepareFailure, accessReady = !options.requireAccess, saved = kit('customer-a')
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    if (new URL(route.request().url()).origin === baseUrl) return route.continue()
    unexpected.push(route.request().url()); return route.abort()
  })
  await page.route(`${baseUrl}/api/**`, async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    const body = request.postData() ? JSON.parse(request.postData()) : null
    requests.push({ path, method, body })
    const send = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) }).catch(() => {})
    const gate = gates.get(`${method} ${path}`)
    if (gate) { gates.delete(`${method} ${path}`); gate.started(); await gate.wait }
    if (path === '/api/platform/customers' && method === 'GET') return send(['a', 'b'].map(suffix => ({ id: `customer-${suffix}`, name: `Company ${suffix.toUpperCase()}`, slug: `company-${suffix}`, status: 'ACTIVE' })))
    const staff = path.match(/^\/api\/staff\/customers\/([^/]+)\/experience-kits(.*)$/)
    const customerId = staff?.[1] ?? 'customer-a', suffix = staff?.[2]
    const access = path.match(/^\/api\/staff\/customers\/([^/]+)\/access$/)
    if (access && method === 'POST') {
      accessReady = true
      return send({ customerId: access[1], userId: `admin-${access[1]}`, email: body.email,
        displayName: body.displayName, identityLinked: false, created: true })
    }
    if ((staff && suffix === '/prepare') || path === '/api/customer-self-service/enrollment') {
      if (prepareFailure || !accessReady) { prepareFailure = false; return send({ message: 'An active customer administrator is required.' }, 409) }
      return send(null)
    }
    if ((staff && suffix === '/catalogue') || path === '/api/customer-experience-kits/catalogue') return send(accessReady ? catalogue(customerId, options.kitCode) : [])
    if ((staff && suffix === '') || path === '/api/customer-experience-kits') {
      if (method === 'GET') return send([])
      if (method === 'POST') {
        saved = kit(customerId, { kitCode: body.kitCode, name: body.name, deliveryMode: body.deliveryMode, humanSupportEnabled: body.humanSupportEnabled,
          ...(body.entryPoint === 'DEVELOPER' ? { deploymentPolicy: { allowedEnvironments: ['staging'], tags: ['staging-only'] } } : {}),
          workspace: { ...kit(customerId).workspace, displayName: body.workspaceName, appSlug: body.appSlug, configuration: { theme: { primaryColor: body.brandColor } } },
          ...(body.deliveryMode === 'both' ? { embeddedInstallationId: 'app-embed', embeddedWorkspace: { ...kit(customerId).workspace, installationId: 'app-embed', rowVersion: 9, allowedOrigins: [body.websiteOrigin] } } : {}) })
        return send(saved)
      }
    }
    if (staff && suffix === '/kit-one') {
      if (method === 'PUT') saved = { ...saved, name: body.name, revision: body.expectedRevision + 1,
        workspace: { ...saved.workspace, displayName: body.workspaceName, rowVersion: body.expectedWorkspaceRevision + 1 } }
      return send(saved)
    }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected request' }, 404)
  })
  const hold = (method, path) => {
    let started, release
    const result = { started: new Promise(resolve => { started = resolve }), release: () => release() }
    gates.set(`${method} ${path}`, { started, wait: new Promise(resolve => { release = resolve }) })
    releases.push(result.release)
    return result
  }
  const navigate = path => page.evaluate(path => window.__navigate(path), path)
  const chooseCustomer = async label => {
    await page.getByRole('combobox', { name: 'Customer / company' }).click()
    await page.getByRole('option', { name: label, exact: true }).click()
  }
  const complete = async (name = 'Company A kit', slug = 'company-a-ai', staff = true) => {
    await page.getByRole('textbox', { name: 'Kit name', exact: true }).fill(name)
    await page.getByRole('button', { name: 'Continue setup', exact: true }).click()
    await page.getByRole('textbox', { name: 'Workspace name', exact: true }).fill(`${name} AI`)
    await page.getByRole('textbox', { name: 'App address name', exact: true }).fill(slug)
    await page.getByRole('button', { name: 'Continue setup', exact: true }).click()
    await page.getByRole('button', { name: staff ? 'Create customer workspace' : 'Create my workspace', exact: true }).click()
  }
  const initialHold = options.hold ? hold(...options.hold) : null
  try {
    await page.goto(`${baseUrl}/__kits?${new URLSearchParams(options.route ? { route: options.route } : {})}`)
    await test({ page, requests, navigate, chooseCustomer, complete, hold, initialHold })
    assert.deepEqual(errors, [], 'No browser runtime errors')
    assert.deepEqual(unexpected, [], 'All HTTP traffic stays on local fixture contracts')
    passed++; console.log(`PASS ${name}`)
  } catch (error) {
    console.error({ name, errors, unexpected, requests, body: await page.locator('body').innerText() })
    throw error
  } finally { for (const release of releases) release(); await page.close() }
}

try {
  await run('staff shared form creates and updates only the selected customer configuration', async t => {
    await t.complete()
    await t.page.getByRole('heading', { name: 'Company A kit', exact: true }).waitFor()
    const create = t.requests.find(item => item.method === 'POST' && item.path.endsWith('/experience-kits'))
    assert.equal(create.path, '/api/staff/customers/customer-a/experience-kits')
    assert.deepEqual({ ...create.body, operationKey: 'opaque' }, { kitCode: 'ai-customer-experience', name: 'Company A kit',
      offerId: 'offer-customer-a', targetProfileId: 'stage-customer-a', workspaceProfileId: 'profile-customer-a', workspaceName: 'Company A kit AI',
      brandColor: '#28bfa6', deliveryMode: 'standalone', appSlug: 'company-a-ai', humanSupportEnabled: false,
      ticketingEnabled: false, ticketProvider: null, operationKey: 'opaque' })
    assert.ok(create.body.operationKey)
    await t.page.getByRole('textbox', { name: 'Workspace name', exact: true }).fill('Revised company AI')
    await t.page.getByRole('button', { name: 'Save kit settings', exact: true }).click()
    await t.page.waitForFunction(() => window.__queryClient.getMutationCache().getAll().every(item => item.state.status !== 'pending'))
    const update = t.requests.find(item => item.method === 'PUT')
    assert.equal(update.path, '/api/staff/customers/customer-a/experience-kits/kit-one')
    assert.equal(update.body.expectedRevision, 3); assert.equal(update.body.expectedWorkspaceRevision, 8)
    assert.equal(update.body.expectedEmbeddedWorkspaceRevision, null); assert.equal(update.body.workspaceName, 'Revised company AI')
    assert.equal(await t.page.getByRole('link', { name: 'Customer accounts', exact: true }).getAttribute('href'), '/users')
    assert.equal(await t.page.getByRole('link', { name: 'Open Brain editor' }).getAttribute('href'), '/overview?deploymentId=dep-customer-a')
    assert.equal(await t.page.getByRole('link', { name: 'Open Engage editor', exact: true }).getAttribute('href'),
      '/ai-workspaces?customerId=customer-a&installationId=app-one')
    assert.ok(t.requests.every(item => !item.path.startsWith('/api/customer-')))
    await t.page.screenshot({ path: join(screenshots, 'staff-overview-desktop.png'), fullPage: true })
  })

  await run('target switch clears unsaved setup and a stale create cannot redirect the new target', async t => {
    const pending = t.hold('POST', '/api/staff/customers/customer-a/experience-kits')
    await t.complete('Original company kit')
    await pending.started
    await t.chooseCustomer('Company B (company-b)')
    await t.page.getByRole('link', { name: 'Set up kit', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).waitFor()
    assert.equal(await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).inputValue(), 'AI Customer Experience')
    pending.release()
    await t.page.waitForFunction(() => window.__queryClient.getMutationCache().getAll().every(item => item.state.status !== 'pending'))
    assert.equal(await t.page.evaluate(() => window.__path), '/customers/customer-b/kits/new/ai-customer-experience')
    await t.complete('Company B kit', 'company-b-ai')
    await t.page.getByRole('heading', { name: 'Company B kit', exact: true }).waitFor()
    const creates = t.requests.filter(item => item.method === 'POST' && item.path.endsWith('/experience-kits'))
    assert.equal(creates.length, 2)
    assert.equal(creates[1].path, '/api/staff/customers/customer-b/experience-kits')
    assert.equal(creates[1].body.offerId, 'offer-customer-b')
    assert.notEqual(creates[0].body.operationKey, creates[1].body.operationKey)
    const keys = await t.page.evaluate(() => window.__queryClient.getQueryCache().getAll().map(query => query.queryKey))
    assert.ok(keys.some(key => key[0] === 'staff-experience-kits' && key[2] === 'customer-a'))
    assert.ok(keys.some(key => key[0] === 'staff-experience-kits' && key[2] === 'customer-b'))
  })

  await run('stale preparation never loads the previous customer catalogue into the new setup', async t => {
    const pending = t.initialHold
    await pending.started
    await t.chooseCustomer('Company B (company-b)')
    await t.page.getByRole('link', { name: 'Set up kit', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).waitFor()
    pending.release()
    assert.ok(!t.requests.some(item => item.path === '/api/staff/customers/customer-a/experience-kits/catalogue'))
    assert.equal(await t.page.getByRole('combobox', { name: 'Starting experience' }).innerText(), 'Template for customer-b')
  }, { hold: ['POST', '/api/staff/customers/customer-a/experience-kits/prepare'] })

  await run('prepare failure keeps account prerequisite visible and retries without a payload', async t => {
    await t.page.getByText('An active customer administrator is required.', { exact: false }).last().waitFor()
    assert.equal(t.requests.filter(item => item.path.endsWith('/catalogue')).length, 0)
    await t.page.getByRole('button', { name: 'Retry', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).waitFor()
    const prepares = t.requests.filter(item => item.path.endsWith('/prepare'))
    assert.equal(prepares.length, 2); assert.ok(prepares.every(item => item.method === 'POST' && item.body === null))
  }, { prepareFailure: true })

  await run('customer access retries blocked setup while retaining manual handover instructions', async t => {
    await t.page.getByText('An active customer administrator is required.', { exact: false }).last().waitFor()
    assert.equal(t.requests.filter(item => item.path.endsWith('/catalogue')).length, 0)
    await t.page.getByRole('button', { name: 'Give customer access', exact: true }).click()
    const dialog = t.page.getByRole('dialog', { name: 'Prepare customer access' })
    await dialog.getByRole('textbox', { name: 'Customer email', exact: true }).fill('owner@company.example')
    await dialog.getByRole('textbox', { name: 'Customer name', exact: true }).fill('Company owner')
    const retry = t.page.waitForResponse(response => new URL(response.url()).pathname === '/api/staff/customers/customer-a/experience-kits/prepare' && response.status() === 200)
    await dialog.getByRole('button', { name: 'Prepare access', exact: true }).click()
    await retry
    await dialog.getByText('No email has been sent.', { exact: false }).waitFor()
    assert.equal(await dialog.getByRole('textbox', { name: 'Connect existing workspace' }).inputValue(), `${baseUrl}/recover-account`)
    assert.equal(await dialog.getByRole('textbox', { name: 'Create sign-in account' }).inputValue(), `${baseUrl}/sign-up`)
    const accesses = t.requests.filter(item => item.path.endsWith('/access'))
    assert.deepEqual(accesses, [{ path: '/api/staff/customers/customer-a/access', method: 'POST', body: { email: 'owner@company.example', displayName: 'Company owner' } }])
    assert.equal(t.requests.filter(item => item.path.endsWith('/prepare')).length, 2)
    await dialog.getByRole('button', { name: 'Done', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).waitFor()
    assert.equal(await t.page.evaluate(() => window.__path), '/customers/customer-a/kits/new/ai-customer-experience')
    assert.ok(t.requests.every(item => !item.path.startsWith('/api/customer-')))
  }, { requireAccess: true })

  await run('access refreshes the selected customer catalogue and clears a failed list preparation', async t => {
    await t.page.getByRole('button', { name: 'Prepare available kits', exact: true }).click()
    await t.page.getByRole('alert').filter({ hasText: 'An active customer administrator is required.' }).waitFor()
    await t.page.getByRole('button', { name: 'Give customer access', exact: true }).click()
    const dialog = t.page.getByRole('dialog', { name: 'Prepare customer access' })
    await dialog.getByRole('textbox', { name: 'Customer email', exact: true }).fill('owner@company.example')
    await dialog.getByRole('textbox', { name: 'Customer name', exact: true }).fill('Company owner')
    await dialog.getByRole('button', { name: 'Prepare access', exact: true }).click()
    await dialog.getByRole('button', { name: 'Done', exact: true }).click()
    await t.page.getByRole('link', { name: 'Set up kit', exact: true }).waitFor()
    assert.equal(await t.page.getByRole('alert').filter({ hasText: 'An active customer administrator is required.' }).count(), 0)
    const retry = t.page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/experience-kits/prepare') && response.status() === 200)
    await t.page.getByRole('button', { name: 'Prepare available kits', exact: true }).click()
    await retry
    const catalogues = t.requests.filter(item => item.path.endsWith('/catalogue'))
    assert.ok(catalogues.length >= 2)
    assert.ok(catalogues.every(item => item.path === '/api/staff/customers/customer-a/experience-kits/catalogue'))
  }, { route: '/customers/customer-a/kits', requireAccess: true })

  await run('preparing customer access preserves the current kit draft', async t => {
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).fill('Keep this kit draft')
    await t.page.getByRole('button', { name: 'Continue setup', exact: true }).click()
    await t.page.getByRole('textbox', { name: 'Workspace name', exact: true }).fill('Keep this workspace name')
    await t.page.getByRole('textbox', { name: 'App address name', exact: true }).fill('keep-this-address')
    await t.page.getByRole('button', { name: 'Give customer access', exact: true }).click()
    const dialog = t.page.getByRole('dialog', { name: 'Prepare customer access' })
    await dialog.getByRole('textbox', { name: 'Customer email', exact: true }).fill('owner@company.example')
    await dialog.getByRole('textbox', { name: 'Customer name', exact: true }).fill('Company owner')
    const retry = t.page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/experience-kits/prepare') && response.status() === 200)
    await dialog.getByRole('button', { name: 'Prepare access', exact: true }).click()
    await retry
    await dialog.getByRole('button', { name: 'Done', exact: true }).click()
    assert.equal(await t.page.getByRole('textbox', { name: 'Workspace name', exact: true }).inputValue(), 'Keep this workspace name')
    assert.equal(await t.page.getByRole('textbox', { name: 'App address name', exact: true }).inputValue(), 'keep-this-address')
    await t.page.getByRole('button', { name: 'Continue setup', exact: true }).click()
    await t.page.getByText('Keep this kit draft', { exact: true }).waitFor()
  })

  await run('switching customer closes access dialog and ignores a late preparation response', async t => {
    await t.page.getByRole('heading', { name: 'Kits for Company A' }).waitFor()
    await t.page.getByRole('button', { name: 'Give customer access', exact: true }).click()
    const dialog = t.page.getByRole('dialog', { name: 'Prepare customer access' })
    await dialog.getByRole('textbox', { name: 'Customer email', exact: true }).fill('owner@company.example')
    await dialog.getByRole('textbox', { name: 'Customer name', exact: true }).fill('Company owner')
    const pending = t.hold('POST', '/api/staff/customers/customer-a/access')
    await dialog.getByRole('button', { name: 'Prepare access', exact: true }).click()
    await pending.started
    await t.navigate('/customers/customer-b/kits')
    await t.page.getByRole('heading', { name: 'Kits for Company B' }).waitFor()
    pending.release()
    assert.equal(await t.page.getByRole('dialog').count(), 0)
    assert.ok(!t.requests.some(item => item.path === '/api/staff/customers/customer-b/experience-kits/prepare'))
    await t.page.getByRole('button', { name: 'Give customer access', exact: true }).click()
    const next = t.page.getByRole('dialog', { name: 'Prepare customer access' })
    assert.equal(await next.getByRole('textbox', { name: 'Customer email', exact: true }).inputValue(), '')
    await next.getByText('Company B', { exact: true }).waitFor()
  }, { route: '/customers/customer-a/kits' })

  await run('non-staff roles cannot load staff customer or kit data', async t => {
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).waitFor()
    const count = t.requests.length
    await t.page.evaluate(() => window.__session({ role: 'DEPLOYMENT_ADMIN' }))
    await t.page.getByText('Staff access is required to set up a customer kit.').waitFor()
    await t.navigate('/customers/customer-b/kits')
    assert.equal(t.requests.length, count)
    await t.page.evaluate(() => window.__session({ role: 'PLATFORM_ADMIN', actorId: 'staff-b' }))
    await t.page.getByRole('heading', { name: 'Kits for Company B' }).waitFor()
    const keys = await t.page.evaluate(() => window.__queryClient.getQueryCache().getAll().map(query => query.queryKey))
    assert.ok(keys.some(key => key[0] === 'staff-experience-kits' && key[1] === 'staff-b' && key[2] === 'customer-b'))
  })

  await run('customer-granted operators use kit editors without customer identity controls', async t => {
    await t.page.getByRole('heading', { name: 'Kits for Company A' }).waitFor()
    await t.page.evaluate(() => window.__session({ role: 'PLATFORM_OPERATOR', actorId: 'integration-engineer' }))
    await t.page.getByRole('heading', { name: 'Kits for Company A' }).waitFor()
    assert.equal(await t.page.getByRole('button', { name: 'Give customer access', exact: true }).count(), 0)
    assert.equal(await t.page.getByRole('link', { name: 'Customer accounts', exact: true }).count(), 0)
    assert.equal(await t.page.getByRole('link', { name: 'Customer workspaces', exact: true }).getAttribute('href'), '/customer-workspaces')
    await t.navigate('/customers/customer-a/kits/kit-one')
    await t.page.getByRole('heading', { name: 'Saved kit', exact: true }).waitFor()
    assert.equal(await t.page.getByRole('link', { name: 'Open Engage editor', exact: true }).getAttribute('href'), '/ai-workspaces?customerId=customer-a&installationId=app-one')
  }, { route: '/customers/customer-a/kits' })

  await run('customer-only setup retains enrollment, payload and navigation', async t => {
    await t.complete('Customer self setup', 'self-setup', false)
    await t.page.getByRole('heading', { name: 'Customer kit created', exact: true }).waitFor()
    assert.ok(t.requests.some(item => item.path === '/api/customer-self-service/enrollment' && item.body === null))
    const create = t.requests.find(item => item.path === '/api/customer-experience-kits' && item.method === 'POST')
    assert.equal(create.body.offerId, 'offer-customer-a')
    assert.equal(create.body.workspaceName, 'Customer self setup AI')
    assert.equal(Object.hasOwn(create.body, 'entryPoint'), false, 'ordinary customer setup has no developer marker')
    assert.ok(t.requests.every(item => !item.path.startsWith('/api/staff/')))
  }, { route: '/customer-setup' })

  for (const code of ['ai-customer-experience', 'ai-support-experience']) {
    await run(`developer ${code} uses the shared staff setup with staging-only copy and explicit payload`, async t => {
      const banner = t.page.getByRole('alert').filter({ hasText: 'Developer programme · Staging only.' })
      await banner.waitFor()
      await banner.getByText(/cannot be deployed or promoted to production/).waitFor()
      await t.page.getByText('Developer environment', { exact: true }).waitFor()
      assert.equal(await t.page.getByText('Staging only', { exact: true }).count(), 1)
      assert.equal(await t.page.getByRole('combobox', { name: 'Test environment', exact: true }).count(), 0)
      assert.equal(await t.page.getByText('Developer staging only', { exact: true }).count(), 0)
      const name = code === 'ai-support-experience' ? 'Developer support team' : 'Developer customer experience'
      await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).fill(name)
      await t.page.getByRole('button', { name: 'Continue setup', exact: true }).click()
      await t.page.getByRole('textbox', { name: 'Workspace name', exact: true }).fill(`${name} AI`)
      await t.page.getByRole('textbox', { name: 'App address name', exact: true }).fill(`dev-${code}`)
      await t.page.getByRole('button', { name: 'Continue setup', exact: true }).click()
      await t.page.getByText('Developer staging only', { exact: true }).waitFor()
      await t.page.getByText('Next: connect your information and APIs, test your Brain in staging, and integrate it into your development app.', { exact: true }).waitFor()
      assert.equal(t.requests.filter(item => item.method === 'POST' && item.path.endsWith('/experience-kits')).length, 0)
      await t.page.getByRole('button', { name: 'Create customer workspace', exact: true }).click()
      await t.page.getByRole('heading', { name, exact: true }).waitFor()
      const creates = t.requests.filter(item => item.method === 'POST' && item.path.endsWith('/experience-kits'))
      assert.equal(creates.length, 1)
      assert.equal(creates[0].path, '/api/staff/customers/customer-a/experience-kits')
      assert.deepEqual({ ...creates[0].body, operationKey: 'opaque' }, { kitCode: code, name, offerId: 'offer-customer-a',
        targetProfileId: 'stage-customer-a', workspaceProfileId: 'profile-customer-a', entryPoint: 'DEVELOPER', workspaceName: `${name} AI`,
        brandColor: '#28bfa6', deliveryMode: 'standalone', appSlug: `dev-${code}`, humanSupportEnabled: false,
        ticketingEnabled: false, ticketProvider: null, operationKey: 'opaque' })
      assert.ok(creates[0].body.operationKey)
      assert.ok(t.requests.every(item => !item.path.startsWith('/api/customer-')), 'staff developer setup never changes transport or auth scope')
      await t.page.screenshot({ path: join(screenshots, `${code}-developer-overview.png`), fullPage: true })
    }, { route: `/customers/customer-a/kits/new/${code}?entryPoint=DEVELOPER`, kitCode: code })
  }

  await run('mobile assisted setup keeps customer and form controls within viewport', async t => {
    await t.page.getByRole('textbox', { name: 'Kit name', exact: true }).waitFor()
    assert.ok(await t.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await t.page.screenshot({ path: join(screenshots, 'staff-setup-mobile.png'), fullPage: true })
    await t.complete('Mobile company kit', 'mobile-company-ai')
    await t.page.getByRole('heading', { name: 'Mobile company kit', exact: true }).waitFor()
    assert.ok(await t.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  }, { mobile: true })
  console.log(JSON.stringify({ passed, screenshots }))
} finally {
  await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true })
}
