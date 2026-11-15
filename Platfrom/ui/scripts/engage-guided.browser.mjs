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
      import { AIWorkspacesPage } from '/src/pages/AIWorkspacesPage.tsx';
      import { ExperienceKitOverview } from '/src/components/kits/ExperienceKitOverview.tsx';
      import '/src/pages/ExperienceKits.css';
      const theme = new URLSearchParams(location.search).get('theme') === 'dark' ? 'dark' : 'light';
      function Frame({children}) {return <Box sx={{p:{xs:2,md:3.5},pt:'85px',minHeight:'100vh',bgcolor:'background.default'}}>{children}</Box>}
      createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}})}><PlatformAuthProvider>
        <ThemeProvider theme={createConsoleTheme(theme)}><CssBaseline/><RouterProvider router={createBrowserRouter([
          {path:'/ai-workspaces',element:<Frame><CustomerResourcesPage mode="apps"/></Frame>},
          {path:'/deployments',element:<Frame><CustomerResourcesPage mode="deployments"/></Frame>},
          {path:'/staff-workspaces',element:<Frame><AIWorkspacesPage initialCustomerId="customer"/></Frame>},
          {path:'/kits/kit-a',element:<Frame><div className="ek-page" data-kit-theme={theme}><ExperienceKitOverview customerId="customer" id="kit-a"/></div></Frame>},
        ])}/></ThemeProvider></PlatformAuthProvider></QueryClientProvider>);
    `, 'guided-entry.tsx', { loader: 'tsx', jsx: 'automatic' }) },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!/^\/(ai-workspaces|staff-workspaces|deployments|kits\/kit-a)(\?|$)/.test(req.url)) return next()
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
let count = 0, fixtureCreates = 0
async function run(name, fn, { width = 1440, theme = 'light', ready = false, invalidKit = false, unknownCheck = false, staff = false, createApp = false, connectionState = 'ready', longName = false } = {}) {
  const page = await browser.newPage({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' })
  page.setDefaultTimeout(20000)
  const errors = [], writes = [], unexpected = []
  let created = null
  const current = ready ? { ...draft, releaseId: 'rel-stage', ready: true, readinessChecks: [{ code: 'ASSIGNMENT', status: 'PASSED', message: 'Verified.' }], domains: [] }
    : unknownCheck ? { ...draft, readinessChecks: [...draft.readinessChecks, { code: 'FUTURE_CHECK', status: 'WARNING', message: 'Provider needs review, not an assumed success.' }] } : draft
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname
    if (url.origin !== base) { unexpected.push('External request'); return route.abort() }
    if (!path.startsWith('/api/')) return route.continue()
    if (createApp && path === '/api/platform/customers/customer/ai-workspace-installations' && req.method() === 'POST') {
      assert.ok(req.headers()['idempotency-key'])
      fixtureCreates++
      created = { ...draft, ...req.postDataJSON(), installationId: 'created', id: 'created' }
      return route.fulfill({ status: 201, json: created })
    }
    if (req.method() !== 'GET') { writes.push(path); return route.fulfill({ status: 500, json: { message: 'This navigation test never executes writes' } }) }
    const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/platform/auth/session') return json({ enabled: true, authenticated: true, actorId: 'owner', role: staff ? 'PLATFORM_ADMIN' : 'CUSTOMER_ADMIN', customerId: staff ? null : 'customer', authenticationMode: 'SESSION' })
    if (path === '/api/platform/auth/customer-config') return json({ enabled: false })
    if (path === '/api/customer-self-service/deployments') return json([{ ...brain, ...(longName ? { name: 'InternationalCustomerSupportBrainForEveryBranchAndSalesTeam' } : {}) }])
    if (path === '/api/customer-self-service/catalogue') return json([offer])
    if (path === '/api/customer-self-service/catalogue/offer-a') return json(offer)
    if (path === '/api/platform/customers') return connectionState === 'error'
      ? route.fulfill({ status: 503, json: { message: 'Connection catalogue unavailable' } })
      : json([{ id: 'customer', name: 'Company', consumers: [current, active].map(app => ({ consumerId: app.consumerId, customerId: 'customer', status: 'ACTIVE', displayName: app.displayName,
        boundDeploymentId: 'brain-a', boundTargetProfileId: 'stage', boundReleaseId: connectionState === 'mismatch' ? 'other-release' : app.releaseId,
        boundDeploymentEnvironment: connectionState === 'production' ? 'production' : 'staging', bindingRevision: 1 })) }])
    if (path === '/api/platform/customers/customer/ai-workspace-installations') return json([current, active, ...(created ? [created] : [])])
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
    if (createApp) assert.equal(created?.displayName, 'Fresh customer workspace')
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
async function unifiedPageMounted(page) {
  assert.equal(await page.locator('[aria-label="Engage apps"]').count(), 1, 'The compact customer list stays mounted under the tool')
  assert.equal(await page.locator('table').count(), 0, 'No legacy app table is mounted for customer setup')
}
async function selectedAppRestored(page, id) {
  await page.waitForURL(url => !url.searchParams.has('appTool') && url.searchParams.get('resourceId') === id)
  await page.getByRole('complementary').getByRole('navigation', { name: 'Engage setup steps' }).waitFor()
  await unifiedPageMounted(page)
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
  for (const [width, theme] of [[1600, 'light'], [1440, 'dark'], [390, 'dark'], [320, 'light']]) {
    await run(`${width} ${theme}: rows retain Brain, exact environment, delivery, status and blocker`, async page => {
      await page.goto(`${base}/ai-workspaces?theme=${theme}`)
      const list = page.getByRole('list', { name: 'Engage apps' })
      const draftRow = list.getByRole('button', { name: 'Inspect Your company AI', exact: true })
      const activeRow = list.getByRole('button', { name: 'Inspect Website assistant', exact: true })
      await activeRow.getByText('Staging', { exact: true }).waitFor()
      for (const row of [draftRow, activeRow]) await row.getByText('Company Brain', { exact: true }).waitFor()
      await draftRow.getByText('Not assigned', { exact: true }).waitFor()
      await draftRow.getByText('A verified release binding is required.', { exact: true }).waitFor()
      await draftRow.getByText('+2 more to review', { exact: true }).waitFor()
      await activeRow.getByText('Recorded checks passed', { exact: true }).waitFor()
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
      for (const row of [draftRow, activeRow]) assert.equal(await row.evaluate(node => node.scrollWidth <= node.clientWidth + 1), true)
      await page.screenshot({ path: join(shots, `${theme}-${width}-record-details.png`), animations: 'disabled' })
    }, { width, theme })
  }
  await run('production assignment is not labelled with the Brain bootstrap staging environment', async page => {
    await page.goto(`${base}/ai-workspaces`)
    await page.getByRole('button', { name: 'Inspect Website assistant', exact: true }).getByText('Production', { exact: true }).waitFor()
  }, { connectionState: 'production' })
  for (const connectionState of ['error', 'mismatch']) await run(`${connectionState}: environment stays unavailable rather than guessing`, async page => {
    await page.goto(`${base}/ai-workspaces`)
    await page.getByRole('button', { name: 'Inspect Website assistant', exact: true }).getByText('Unavailable', { exact: true }).waitFor()
  }, { connectionState })
  await run('long Brain names wrap without removing metadata or widening a phone page', async page => {
    await page.goto(`${base}/ai-workspaces`)
    await page.getByRole('list', { name: 'Engage apps' }).getByText('InternationalCustomerSupportBrainForEveryBranchAndSalesTeam', { exact: true }).first().waitFor()
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
  }, { width: 320, longName: true })
  for (const [id, command, confirmation] of [['a', 'Delete draft app', 'Delete draft app?'], ['b', 'Disable app', 'Disable app?']]) {
    await run(`${command}: existing controls, AI editing and confirmation remain reachable without writes`, async page => {
      await page.goto(`${base}/ai-workspaces?resourceKind=app&resourceId=${id}&deploymentId=brain-a`)
      await page.getByRole('complementary').getByRole('link', { name: 'App controls', exact: true }).click()
      const aiButton = page.getByRole('button', { name: 'Build app with AI', exact: true })
      await aiButton.waitFor()
      const bounds = await aiButton.boundingBox()
      assert.ok(bounds && bounds.y >= 0 && bounds.y + bounds.height <= page.viewportSize().height, 'App controls open at the controls, not a scrolled-away section')
      await page.getByRole('button', { name: 'Edit app', exact: true }).waitFor()
      await page.getByRole('button', { name: command, exact: true }).click()
      const confirm = page.getByRole('dialog', { name: confirmation, exact: true })
      await confirm.getByRole('button', { name: 'Cancel', exact: true }).click()
      await closeConfiguration(page)
    })
  }
  await run('Kit Configure workspace opens actual editor; return is visible and keeps close confirmation', async page => {
    await page.goto(`${base}/kits/kit-a`)
    await page.getByRole('link', { name: 'Configure workspace', exact: true }).click()
    await page.getByRole('button', { name: 'Close configuration', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Activate app', exact: true }).waitFor()
    await unifiedPageMounted(page)
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
    await unifiedPageMounted(page)
    await page.getByRole('dialog', { name: 'Connect smart brain', exact: true }).getByRole('button', { name: 'Cancel', exact: true }).click()
    await selectedAppRestored(page, 'a')
  })
  await run('legacy connection URL opens over one customer list and cancels back to the exact app', async page => {
    await page.goto(`${base}/ai-workspaces?view=manage&connectInstallationId=b`)
    const dialog = page.getByRole('dialog', { name: 'Connect smart brain', exact: true })
    await dialog.waitFor(); await unifiedPageMounted(page)
    assert.equal(new URL(page.url()).searchParams.has('view'), false)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await selectedAppRestored(page, 'b')
    await page.getByRole('complementary').getByRole('heading', { name: 'Website assistant', exact: true }).waitFor()
  })
  await run('legacy appearance URL cancels to its original app without the management table', async page => {
    await page.goto(`${base}/ai-workspaces?view=manage&previewInstallationId=a`)
    const dialog = page.getByRole('dialog', { name: 'Appearance & preview', exact: true })
    await dialog.getByRole('textbox', { name: 'Workspace name', exact: true }).waitFor()
    await unifiedPageMounted(page)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await selectedAppRestored(page, 'a')
  })
  await run('create opens in context and cancel restores the filtered library', async page => {
    await page.goto(`${base}/ai-workspaces`)
    await page.getByRole('textbox', { name: 'Search Engage apps' }).fill('company')
    await page.getByRole('link', { name: 'Create Engage app', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Create Engage app', exact: true })
    await dialog.waitFor(); await unifiedPageMounted(page)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await page.waitForURL(url => !url.searchParams.has('appTool'))
    assert.equal(await page.getByRole('textbox', { name: 'Search Engage apps' }).inputValue(), 'company')
    assert.equal(await page.getByRole('list', { name: 'Engage apps' }).getByRole('button').count(), 1)
  })
  await run('alternate delivery opens the native create form and cancels back to its source app', async page => {
    await page.goto(`${base}/ai-workspaces?view=manage&sourceInstallationId=b&delivery=standalone`)
    const dialog = page.getByRole('dialog', { name: 'Create Engage app', exact: true })
    await dialog.getByRole('textbox', { name: 'Workspace name', exact: true }).waitFor()
    await unifiedPageMounted(page)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await selectedAppRestored(page, 'b')
  })
  await run('nested connection cancellation preserves the outer manual form', async page => {
    await page.goto(`${base}${appPath}&appTool=appearance`)
    const dialog = page.getByRole('dialog', { name: 'Appearance & preview', exact: true })
    const name = dialog.getByRole('textbox', { name: 'Workspace name', exact: true })
    await name.fill('Unsaved brand name')
    await dialog.getByRole('button', { name: /Change.*brain/i }).click()
    const connection = page.getByRole('dialog', { name: 'Connect smart brain', exact: true })
    await connection.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.equal(await name.inputValue(), 'Unsaved brand name')
    assert.equal(new URL(page.url()).searchParams.get('appTool'), 'appearance')
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await selectedAppRestored(page, 'a')
  })
  await run('explicit create saves through the canonical API and selects the new app in the unified list', async page => {
    await page.goto(`${base}/ai-workspaces?appTool=create&consumerId=consumer-a`)
    const dialog = page.getByRole('dialog', { name: 'Create Engage app', exact: true })
    await dialog.getByRole('textbox', { name: 'Workspace name', exact: true }).fill('Fresh customer workspace')
    await dialog.getByRole('textbox', { name: 'App slug', exact: true }).fill('fresh-workspace')
    await unifiedPageMounted(page)
    await dialog.getByRole('button', { name: 'Save draft', exact: true }).click()
    await selectedAppRestored(page, 'created')
    await page.getByRole('complementary').getByRole('heading', { name: 'Fresh customer workspace', exact: true }).waitFor()
  }, { createApp: true })
  await run('Brain-origin connection picker stays in the unified app page', async page => {
    await page.goto(`${base}/ai-workspaces?view=manage&connectDeploymentId=brain-a`)
    const dialog = page.getByRole('dialog', { name: 'Connect smart brain', exact: true })
    await dialog.getByRole('combobox', { name: /^Engage app/ }).waitFor()
    await unifiedPageMounted(page)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await page.waitForURL(url => !url.searchParams.has('appTool'))
    await unifiedPageMounted(page)
  })
  await run('unavailable legacy app never substitutes another owned app', async page => {
    await page.goto(`${base}/ai-workspaces?view=manage&previewInstallationId=foreign-app`)
    await page.getByRole('list', { name: 'Engage apps' }).waitFor()
    assert.equal(await page.getByRole('dialog').count(), 0)
    assert.equal(new URL(page.url()).searchParams.get('resourceId'), 'foreign-app')
    await unifiedPageMounted(page)
  })
  await run('staff management retains its existing table', async page => {
    await page.goto(`${base}/staff-workspaces`)
    await page.getByRole('cell').filter({ has: page.getByText('Your company AI', { exact: true }) }).waitFor()
    assert.equal(await page.getByRole('table').count(), 1)
  }, { staff: true })
  await run('direct Engage setup returns to the selected app without requiring Kit or Brain origin', async page => {
    await page.goto(base + appPath)
    await page.getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link', { name: /2\. Brand your workspace/ }).click()
    await page.getByRole('dialog', { name: 'Appearance & preview', exact: true }).getByRole('link', { name: 'Back to Engage apps', exact: true }).click()
    await page.getByRole('complementary').getByRole('navigation', { name: 'Engage setup steps' }).waitFor()
    assert.equal(new URL(page.url()).searchParams.get('resourceId'), 'a')
    assert.equal(new URL(page.url()).searchParams.has('view'), false)
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
  console.log(JSON.stringify({ passed: count, screenshots: shots, inference: 0, fixtureCreateWrites: fixtureCreates, unexpectedWrites: 0 }))
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
