import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'engage-guided-vite-'))
const shots = await mkdtemp(join(tmpdir(), 'engage-guided-shots-'))
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, resolve: { dedupe: ['react', 'react-dom'] },
  server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'engage-guided-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/guided-entry.tsx') return '\0guided-entry.tsx' },
    async load(id) { if (id === '\0guided-entry.tsx') return transformWithEsbuild(`
      import React from 'react'; import { createRoot } from 'react-dom/client';
      import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
      import { createBrowserRouter, RouterProvider } from 'react-router-dom';
      import { ThemeProvider, CssBaseline, Box } from '@mui/material';
      import { PlatformAuthProvider } from '/src/auth/PlatformAuthProvider.tsx';
      import { createConsoleTheme } from '/src/theme/consoleTheme.ts';
      import { CustomerResourcesPage } from '/src/pages/CustomerResourcesPage.tsx';
      import { ExperienceKitOverview } from '/src/components/kits/ExperienceKitOverview.tsx';
      import '/src/pages/ExperienceKits.css';
      const theme = new URLSearchParams(location.search).get('theme') === 'dark' ? 'dark' : 'light';
      function Frame({children}) {return <Box sx={{p:{xs:2,md:3.5},pt:'85px',minHeight:'100vh',bgcolor:'background.default'}}>{children}</Box>}
      createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}})}><PlatformAuthProvider>
        <ThemeProvider theme={createConsoleTheme(theme)}><CssBaseline/><RouterProvider router={createBrowserRouter([
          {path:'/ai-workspaces',element:<Frame><CustomerResourcesPage mode="apps"/></Frame>},
          {path:'/deployments',element:<Frame><CustomerResourcesPage mode="deployments"/></Frame>},
          {path:'/kits/kit-a',element:<Frame><div className="ek-page" data-kit-theme={theme}><ExperienceKitOverview customerId="customer" id="kit-a"/></div></Frame>},
        ])}/></ThemeProvider></PlatformAuthProvider></QueryClientProvider>);
    `, 'guided-entry.tsx', { loader: 'tsx', jsx: 'automatic' }) },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!/^\/(ai-workspaces|deployments|kits\/kit-a)(\?|$)/.test(req.url)) return next()
      res.setHeader('Content-Type', 'text/html'); res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin,environment:"staging"}</script><script type="module" src="/guided-entry.tsx"></script></body></html>'))
    }) },
  }] })
const draft = { id: 'a', installationId: 'a', customerId: 'customer', consumerId: 'consumer-a', displayName: 'Your company AI', status: 'DRAFT',
  experiencePackCode: 'generic-workspace', experiencePackVersion: '1.0.0', connectionMode: 'public-runtime-anonymous', connectionProfileCode: 'runtime-anonymous-direct', connectionProfileVersion: '1.0.0',
  connectionConfiguration: {}, configuration: { app: { assistantLabel: 'Company AI' }, theme: { primaryColor: '#176d61' } }, allowedOrigins: ['https://company.example.test'],
  deliveryMode: 'standalone', appSlug: 'company', appUrl: 'https://company.apps.example.test', interactionPolicy: 'public', authProfileCode: 'none', authProfileVersion: '1.0.0', authConfiguration: {},
  domains: [{ hostname: 'company.apps.example.test', url: 'https://company.apps.example.test', type: 'PLATFORM', status: 'PENDING', dnsRecordName: '', dnsRecordValue: '', routingRecordName: null, routingRecordValue: null, lastVerificationError: null }],
  deploymentId: 'brain-a', releaseId: null, ready: false, rowVersion: 1, readinessChecks: [
    { code: 'ASSIGNMENT', status: 'BLOCKED', message: 'A verified release binding is required.' },
    { code: 'APP_DOMAIN', status: 'BLOCKED', message: 'No standalone app domain is verified.' },
    { code: 'APP_DELIVERY', status: 'BLOCKED', message: 'The standalone frontend route is currently unavailable.' },
  ], updatedAt: '2026-11-15T12:00:00Z' }
const active = { ...draft, id: 'b', installationId: 'b', consumerId: 'consumer-b', displayName: 'Website assistant', status: 'ACTIVE', deliveryMode: 'embedded', appUrl: null,
  domains: [], ready: true, releaseId: 'rel-stage', readinessChecks: [{ code: 'ASSIGNMENT', status: 'PASSED', message: 'Verified assignment.' }] }
const brain = { deploymentId: 'brain-a', name: 'Company Brain', status: 'DRAFT', environmentName: 'staging', offerId: 'offer-a', offerFingerprint: 'fp', bootstrapTargetProfileId: 'stage', createdAt: '2026-11-15T10:00:00Z' }
const release = { id: 'rel-stage', deploymentId: 'brain-a', targetProfileId: 'stage', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE', updatedAt: '2026-11-15T10:00:00Z' }
const offer = { id: 'offer-a', name: 'Company Experience', fingerprint: 'fp', entitlement: { status: 'ACTIVE', customerId: 'customer', revision: 1 }, settings: [], targets: [], bindingSlots: [], workspaceProfiles: [] }
const kit = { id: 'kit-a', kitCode: 'ai-customer-experience', kitVersion: '1.0.0', name: 'Company Experience Kit', offerId: 'offer-a', deploymentId: 'brain-a', targetProfileId: 'stage',
  installationId: 'a', deliveryMode: 'standalone', workspace: draft, embeddedWorkspace: null, embeddedInstallationId: null, revision: 1, humanSupportEnabled: false, updatedAt: '2026-11-15T10:00:00Z' }
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`, browser = await chromium.launch({ headless: true })
let count = 0
async function run(name, fn, { width = 1440, theme = 'light', ready = false, invalidKit = false, unknownCheck = false } = {}) {
  const page = await browser.newPage({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' })
  page.setDefaultTimeout(20000)
  const errors = [], writes = [], unexpected = []
  const current = ready ? { ...draft, releaseId: 'rel-stage', ready: true, readinessChecks: [{ code: 'ASSIGNMENT', status: 'PASSED', message: 'Verified.' }], domains: [] }
    : unknownCheck ? { ...draft, readinessChecks: [...draft.readinessChecks, { code: 'FUTURE_CHECK', status: 'WARNING', message: 'Provider needs review, not an assumed success.' }] } : draft
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname
    if (url.origin !== base) { unexpected.push('External request'); return route.abort() }
    if (!path.startsWith('/api/')) return route.continue()
    if (req.method() !== 'GET') { writes.push(path); return route.fulfill({ status: 500, json: { message: 'This navigation test never executes writes' } }) }
    const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/platform/auth/session') return json({ enabled: true, authenticated: true, actorId: 'owner', role: 'CUSTOMER_ADMIN', customerId: 'customer', authenticationMode: 'SESSION' })
    if (path === '/api/platform/auth/customer-config') return json({ enabled: false })
    if (path === '/api/customer-self-service/deployments') return json([brain])
    if (path === '/api/customer-self-service/catalogue') return json([offer])
    if (path === '/api/customer-self-service/catalogue/offer-a') return json(offer)
    if (path === '/api/platform/customers') return json([{ id: 'customer', name: 'Company', consumers: [current, active].map(app => ({ consumerId: app.consumerId, customerId: 'customer', status: 'ACTIVE', displayName: app.displayName, boundDeploymentId: 'brain-a', boundTargetProfileId: 'stage', boundReleaseId: app.releaseId, bindingRevision: 1 })) }])
    if (path === '/api/platform/customers/customer/ai-workspace-installations') return json([current, active])
    if (path === '/api/platform/ai-workspaces/catalog') return json({ assetsReady: true, experiencePacks: [{ code: 'generic-workspace', version: '1.0.0', name: 'Generic', enabled: true }], connectionProfiles: [{ code: 'runtime-anonymous-direct', version: '1.0.0', mode: 'public-runtime-anonymous', name: 'Anonymous', enabled: true }], authenticationProfiles: [] })
    if (/\/preview-capabilities$/.test(path)) return json({ runtimeConfigured: false, handover: { enabled: false, userCanCancelPending: false, userCanResumeAi: false } })
    if (path === '/api/deployments/brain-a/releases') return json([release])
    if (path === '/api/deployments/brain-a/target-profiles') return json([{ id: 'stage', name: 'Staging', environmentName: 'staging', active: true }])
    if (path === '/api/deployments/brain-a/document-storage-bindings') return json([])
    if (path === '/api/deployments/brain-a/document-storage/connections') return json({ connections: [], hostedAvailable: false, supportedExtensions: ['txt'], maxUploadBytes: 1048576 })
    if (path === '/api/customer-experience-kits/kit-a') return json({ ...kit, deploymentId: invalidKit ? 'other-brain' : 'brain-a', workspace: current })
    if (/\/desk$/.test(path)) return json({ status: 'DISABLED' })
    unexpected.push(path); return route.fulfill({ status: 500, json: { message: 'Unhandled read' } })
  })
  try {
    await fn(page, theme)
    assert.deepEqual(errors, []); assert.deepEqual(writes, []); assert.deepEqual(unexpected, [])
    console.log(`PASS ${name}`); count++
  } catch (error) {
    await page.screenshot({ path: join(shots, `failure-${count}.png`), animations: 'disabled' }).catch(() => {})
    console.error(JSON.stringify({ errors, writes, unexpected, url: page.url(), text: (await page.locator('body').innerText()).slice(0, 3500) }))
    throw error
  } finally { await page.close() }
}
const appPath = '/ai-workspaces?resourceKind=app&resourceId=a&deploymentId=brain-a'
async function closeConfiguration(page) {
  await page.getByRole('button', { name: 'Close configuration', exact: true }).click()
  await page.getByRole('dialog', { name: 'Close configuration?', exact: true }).getByRole('button', { name: 'Close panel', exact: true }).click()
}
try {
  for (const [width, theme] of [[1440, 'light'], [1440, 'dark'], [390, 'dark'], [320, 'light']]) {
    await run(`${width} ${theme}: compact apps, guided steps, real blockers and preserved technical controls`, async page => {
      await page.goto(`${base}${appPath}&theme=${theme}`)
      const panel = page.getByRole('complementary')
      await panel.getByRole('link', { name: 'Connect your Brain', exact: true }).waitFor()
      for (const check of draft.readinessChecks) await panel.getByRole('navigation', { name: 'Engage setup steps' }).getByText(check.message, { exact: true }).waitFor()
      assert.equal(await panel.getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link').count(), 4)
      assert.equal(await panel.getByRole('link', { name: 'Open app', exact: true }).count(), 0, 'Draft app is not advertised as open/live')
      if (width < 900) await page.waitForFunction(() => Math.abs(document.querySelector('.MuiDrawer-paper').getBoundingClientRect().left) < 1)
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      await page.screenshot({ path: join(shots, `${theme}-${width}-guided.png`), animations: 'disabled' })
      await panel.locator('summary').filter({ hasText: 'Technical details' }).click()
      await panel.getByRole('link', { name: 'Appearance & preview', exact: true }).waitFor()
      await panel.getByRole('button', { name: 'Close resource details', exact: true }).click()
      await page.getByRole('button', { name: 'Active', exact: true }).click()
      assert.equal(await page.getByRole('list', { name: 'Engage apps' }).getByRole('button').count(), 1)
      await page.getByRole('button', { name: 'All', exact: true }).click()
      await page.getByRole('textbox', { name: 'Search Engage apps' }).fill('company')
      assert.equal(await page.getByRole('list', { name: 'Engage apps' }).getByRole('button').count(), 1)
    }, { width, theme })
  }
  await run('Kit Configure workspace opens actual editor; return is visible and keeps close confirmation', async page => {
    await page.goto(`${base}/kits/kit-a`)
    await page.getByRole('link', { name: 'Configure workspace', exact: true }).click()
    await page.getByRole('button', { name: 'Close configuration', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Activate app', exact: true }).waitFor()
    const back = page.getByRole('link', { name: 'Back to Kit', exact: true }).filter({ visible: true }).last()
    await back.click()
    await page.getByRole('dialog', { name: 'Close configuration?', exact: true }).getByRole('button', { name: 'Close panel', exact: true }).click()
    await page.waitForURL('**/kits/kit-a')
    await page.getByRole('heading', { name: 'Company Experience Kit', exact: true }).waitFor()
  })
  for (const step of ['delivery', 'readiness']) await run(`${step} opens and focuses its canonical configuration section with Kit return`, async page => {
    await page.goto(`${base}${appPath}&kitId=kit-a`)
    const name = step === 'delivery' ? /3\. Set up your app address/ : /4\. Check and activate/
    await page.getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link', { name }).click()
    const target = page.locator(`[aria-label="${step === 'delivery' ? 'Website and app address setup' : 'App launch checks'}"]`)
    await target.waitFor()
    assert.ok(await target.evaluate(node => document.activeElement === node))
    await page.getByRole('link', { name: 'Back to Kit', exact: true }).filter({ visible: true }).last().waitFor()
    await closeConfiguration(page)
  })
  await run('appearance uses the actual branded preview editor and retains Back to Kit without inference', async page => {
    await page.goto(`${base}${appPath}&kitId=kit-a`)
    await page.getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link', { name: /2\. Brand your workspace/ }).click()
    const dialog = page.getByRole('dialog', { name: 'Appearance & preview', exact: true })
    await dialog.getByRole('textbox', { name: 'Workspace name', exact: true }).waitFor()
    await dialog.getByRole('link', { name: 'Back to Kit', exact: true }).waitFor()
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  })
  await run('connect step reuses the existing selected-app connection panel', async page => {
    await page.goto(base + appPath)
    await page.getByRole('complementary').getByRole('link', { name: 'Connect your Brain', exact: true }).click()
    await page.getByRole('dialog', { name: 'Connect smart brain', exact: true }).waitFor()
  })
  await run('unrelated Kit origin is warned about, never offered as a valid return', async page => {
    await page.goto(`${base}${appPath}&kitId=kit-a`)
    await page.getByText('The original Kit is unavailable for this app.', { exact: false }).waitFor()
    assert.equal(await page.getByRole('link', { name: 'Back to Kit', exact: true }).count(), 0)
  }, { invalidKit: true })
  await run('unknown readiness messages stay visible and route to launch checks', async page => {
    await page.goto(base + appPath)
    await page.getByRole('navigation', { name: 'Engage setup steps' }).getByText('Provider needs review, not an assumed success.', { exact: true }).waitFor()
  }, { unknownCheck: true })
  await run('ready app keeps activation explicit in the existing editor', async page => {
    await page.goto(base + appPath)
    await page.getByRole('complementary').getByRole('link', { name: 'Review launch checks', exact: true }).click()
    const activate = page.getByRole('button', { name: 'Activate app', exact: true })
    await activate.waitFor(); assert.equal(await activate.isEnabled(), true)
  }, { ready: true })
  console.log(JSON.stringify({ passed: count, screenshots: shots, inference: 0, configurationWrites: 0 }))
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
