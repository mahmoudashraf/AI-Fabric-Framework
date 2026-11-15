import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'brain-library-vite-'))
const shots = await mkdtemp(join(tmpdir(), 'brain-library-screenshots-'))
const ownedBrainIds = ['brain-customer', 'brain-support', 'brain-dealer']
const actualProfiles = [{ id: 'stage', environmentName: 'staging', active: true }, { id: 'prod', environmentName: 'production', active: true }]
const releaseFixture = { id: 'release-prod', deploymentId: 'brain-support', deploymentVersionId: 'v1', targetProfileId: 'prod', status: 'APPLIED_VERIFIED',
  verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE', errorMessage: null, createdAt: '2026-11-15T11:00:00Z', updatedAt: '2026-11-15T11:00:00Z' }
const entry = `
import React from 'react'
import { createRoot } from 'react-dom/client'
import { Box, CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, RouterProvider, useLocation } from 'react-router-dom'
import { PlatformAuthProvider } from '/src/auth/PlatformAuthProvider.tsx'
import { createConsoleTheme } from '/src/theme/consoleTheme.ts'
import { CustomerResourceSurface } from '/src/components/customer/CustomerResourceSurface.tsx'
const deployments = [
  { deploymentId: 'brain-customer', name: 'AI Customer Experience', status: 'DRAFT' },
  { deploymentId: 'brain-support', name: 'Customer Support Brain', status: 'VERSION_PUBLISHED', activeVersionId: 'v1' },
  { deploymentId: 'brain-dealer', name: 'Dealership AI Experience', status: 'CONFIGURATION_ONLY' },
].map(item => ({ environmentName: 'staging', offerId: 'offer', bootstrapTargetProfileId: 'stage', createdAt: '2026-11-15T10:00:00Z', ...item }))
const offer = { id: 'offer', name: 'Customer Experience', entitlement: { status: 'ACTIVE' }, product: { code: 'support', name: 'Support' },
  targets: [{ id: 'stage', intent: 'CUSTOMER_STAGING' }, { id: 'prod', intent: 'CUSTOMER_PRODUCTION' }], bindingSlots: [], workspaceProfiles: [] }
const app = { id: 'app-support', installationId: 'app-support', customerId: 'customer', displayName: 'Support app', status: 'ACTIVE', ready: true,
  deploymentId: 'brain-support', releaseId: 'release-prod', experiencePackCode: 'support', experiencePackVersion: '1.0', connectionMode: 'public-runtime-anonymous',
  connectionProfileCode: 'profile', connectionProfileVersion: '1.0', configuration: {}, deliveryMode: 'standalone', appUrl: 'https://support.example.com',
  allowedOrigins: [], interactionPolicy: 'public', domains: [], readinessChecks: [], updatedAt: '2026-11-15T10:00:00Z' }
const release = ${JSON.stringify(releaseFixture)}
function Fixture() {
  const location = useLocation(), params = new URLSearchParams(location.search), scenario = params.get('case') || 'default'
  const mode = location.pathname === '/home' ? 'home' : location.pathname === '/ai-workspaces' ? 'apps' : 'deployments'
  const selectedId = params.get('resourceKind') === 'app' ? 'brain-support' : params.get('resourceId')
  const connected = ['connected', 'missing', 'failed', 'error', 'loading', 'long', 'embedded', 'resources-error', 'inactive-app', 'assigned-failed', 'no-staging'].includes(scenario) || mode === 'apps'
  const apps = connected ? [{ ...app, ...(scenario === 'embedded' ? { deliveryMode: 'embedded', allowedOrigins: ['https://www.example.com'] } : {}),
    ...(scenario === 'inactive-app' ? { status: 'DISABLED' } : {}), ...(scenario === 'assigned-failed' ? { releaseId: 'release-old-failed' } : {}) }] : []
  const releases = selectedId === 'brain-support' && (connected || scenario === 'api') && scenario !== 'missing' ? [release,
    { ...release, id: 'release-stage', targetProfileId: 'stage' },
    { ...release, id: 'release-old-failed', status: 'FAILED', verificationStatus: 'FAILED', errorMessage: 'Historical failure', createdAt: '2026-11-15T10:00:00Z' },
  ].map(item => scenario === 'failed' && item.id === release.id ? { ...item, status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED', errorMessage: 'Recorded verification failure' } : item) : []
  const destination = location.pathname.startsWith('/smart-brains/') || params.get('view') === 'manage'
  return <ThemeProvider theme={createConsoleTheme(params.get('theme') === 'dark' ? 'dark' : 'light')}><CssBaseline />
    {destination ? <Box role="status" sx={{ p: 3 }}>Canonical destination: {location.pathname + location.search}</Box> :
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh', pt: '62px', pl: { xs: 0, md: '218px' } }}>
      <Box sx={{ position: 'fixed', top: 0, left: 0, right: 0, height: 62, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }} />
      <Box sx={{ position: 'fixed', top: 62, left: 0, bottom: 0, width: 218, borderRight: 1, borderColor: 'divider', display: { xs: 'none', md: 'block' } }} />
      <Box sx={{ p: { xs: 2, md: 3.5 } }}><CustomerResourceSurface mode={mode}
        deployments={deployments.map(item => scenario === 'long' && item.deploymentId === 'brain-support' ? { ...item, name: 'InternationalCustomerExperienceAndSupportBrainForAllBusinessTeams' } : item)}
        apps={apps} offers={scenario === 'api' ? [] : [offer]} releases={releases} loading={false} releaseLoading={scenario === 'loading'}
        releaseError={scenario === 'error' ? 'Release service unavailable' : null} errors={scenario === 'resources-error' ? ['App data unavailable'] : []}
        dataObservedAt={null} configuration={<div>Existing app configuration</div>} /></Box>
    </Box>}
  </ThemeProvider>
}
createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <PlatformAuthProvider><RouterProvider router={createBrowserRouter([{ path: '*', element: <Fixture /> }])} /></PlatformAuthProvider>
</QueryClientProvider>)
`
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 5196, hmr: false }, plugins: [{
    name: 'brain-library-fixture',
    resolveId(id) { if (id === '/brain-library-fixture.tsx') return '\0brain-library-fixture.tsx' },
    async load(id) { if (id === '\0brain-library-fixture.tsx') return transformWithEsbuild(entry, 'brain-library-fixture.tsx', { loader: 'tsx', jsx: 'automatic' }) },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      if (!/^\/(?:deployments|ai-workspaces|home|smart-brains(?:\/[^?]*)?)(?:\?|$)/.test(req.url)) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin,environment:"staging"}</script><script type="module" src="/brain-library-fixture.tsx"></script></body></html>'))
    }) },
  }] })
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
let passed = 0
async function run(name, fn, viewport = { width: 1568, height: 1003 }) {
  const page = await browser.newPage({ viewport, reducedMotion: 'reduce' })
  const errors = [], commands = [], apiReads = []
  let scenario = 'default'
  page.setDefaultTimeout(15000)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url())
    if (url.origin !== base) return route.abort()
    if (!url.pathname.startsWith('/api/')) {
      if (req.isNavigationRequest()) scenario = url.searchParams.get('case') || 'default'
      return route.continue()
    }
    if (req.method() !== 'GET') commands.push(url.pathname)
    else apiReads.push(url.pathname)
    let body = []
    if (url.pathname === '/api/platform/auth/customer-config') body = { enabled: false }
    else if (url.pathname === '/api/platform/auth/session') body = { enabled: true, authenticated: true, actorId: 'owner', role: 'CUSTOMER_ADMIN', customerId: 'customer', authenticationMode: 'SESSION', canOperateDeployments: true }
    else if (url.pathname === '/api/customer-self-service/infrastructure-quota') body = { customerId: 'customer', used: 1, limit: 2, available: 1,
      environments: [{ deploymentId: 'brain-support', deploymentName: 'Customer Support Brain', targetProfileId: 'prod', environment: 'production', ownedResourcesPresent: true }] }
    else if (url.pathname.includes('/desk')) body = { status: 'ENABLED', portalUrl: 'https://desk.example.com' }
    else if (/^\/api\/deployments\/[^/]+\/(releases|target-profiles)$/.test(url.pathname)) {
      const [, , , brainId, kind] = url.pathname.split('/')
      assert.ok(ownedBrainIds.includes(brainId), `Read must be restricted to an owned Brain: ${url.pathname}`)
      if (scenario === 'api' || scenario === 'live-loading') await new Promise(resolve => setTimeout(resolve, 250))
      if (scenario === 'live-error' && brainId === 'brain-support' && kind === 'releases'
        || scenario === 'profiles-error' && brainId === 'brain-support' && kind === 'target-profiles') {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Fixture evidence unavailable' }) })
      }
      body = kind === 'target-profiles' ? scenario === 'unknown-profile' ? [] : scenario === 'no-staging' ? [actualProfiles[1]] : actualProfiles
        : brainId === 'brain-support' && ['api', 'connected', 'live-loading', 'unknown-profile', 'profiles-error'].includes(scenario) ? [releaseFixture] : []
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  try {
    await fn(page, apiReads)
    assert.deepEqual(errors, [], 'No browser errors')
    assert.deepEqual(commands, [], 'Browsing must never run inference or write configuration')
    assert.ok(apiReads.every(path => /auth|infrastructure-quota|desk|experience-kits/.test(path)
      || /^\/api\/deployments\/(brain-customer|brain-support|brain-dealer)\/(releases|target-profiles)$/.test(path)), `Unexpected API read: ${apiReads}`)
    console.log(`PASS ${name}`); passed++
  } catch (error) {
    await page.screenshot({ path: join(shots, `failure-${passed}.png`), fullPage: true })
    throw error
  } finally { await page.close() }
}
const inspect = (id, extra = '') => `/deployments?resourceKind=deployment&resourceId=${id}${extra}`
async function noOverflow(page) {
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Page fits viewport')
  const aside = page.getByRole('complementary')
  if (await aside.count()) {
    if (page.viewportSize().width < 900) await page.waitForFunction(() => {
      const bounds = document.querySelector('.MuiDrawer-paper')?.getBoundingClientRect()
      return bounds && Math.abs(bounds.left) < 0.5 && Math.abs(bounds.right - innerWidth) < 0.5
    })
    assert.equal(await aside.evaluate(node => node.scrollWidth <= node.clientWidth), true, 'Inspector fits viewport')
  }
}
try {
  await run('search and All / Drafts / Live are real filters, saved is not live', async page => {
    await page.goto(base + '/deployments')
    const rows = page.getByRole('list', { name: 'Smart Brains' })
    await rows.getByRole('button').first().waitFor()
    assert.equal(await rows.getByRole('button').count(), 3)
    await page.getByRole('button', { name: 'Drafts', exact: true }).click()
    assert.equal(await rows.getByRole('button').count(), 2)
    await page.getByRole('button', { name: 'Live', exact: true }).click()
    await page.getByText('No confirmed live Brains in the available release details.').waitFor()
    assert.equal(await rows.getByRole('button').count(), 0)
    await page.getByRole('button', { name: 'All', exact: true }).click()
    await page.getByRole('textbox', { name: 'Search Smart Brains' }).fill('  SUPPORT ')
    assert.equal(await rows.getByRole('button').count(), 1)
    await rows.getByText('Version saved', { exact: true }).waitFor()
    await page.getByRole('textbox', { name: 'Search Smart Brains' }).fill('no match')
    await page.getByText('No Smart Brains match your search.').waitFor()
  })
  await run('Live lazily reads owned Brains with no selection, recognizes an API Brain without its offer, and reuses cached reads', async (page, reads) => {
    await page.goto(base + '/deployments?case=api')
    const rows = page.getByRole('list', { name: 'Smart Brains' })
    await rows.getByRole('button').first().waitFor()
    assert.equal(reads.filter(path => /\/(releases|target-profiles)$/.test(path)).length, 0, 'All must not eagerly read every Brain')
    await page.getByRole('button', { name: 'Live', exact: true }).click()
    await page.getByText('Loading live status...', { exact: true }).waitFor()
    await rows.getByText('Production verified', { exact: true }).waitFor()
    assert.equal(await rows.getByRole('button').count(), 1)
    await rows.getByText('Engage not connected', { exact: true }).waitFor()
    assert.equal(new URL(page.url()).searchParams.has('resourceId'), false)
    for (const id of ownedBrainIds) for (const kind of ['releases', 'target-profiles']) {
      assert.equal(reads.filter(path => path === `/api/deployments/${id}/${kind}`).length, 1, `${id} ${kind} read once`)
    }
    await page.getByRole('button', { name: 'All', exact: true }).click()
    await page.getByRole('button', { name: 'Live', exact: true }).click()
    await rows.getByRole('button', { name: 'Inspect Customer Support Brain' }).click()
    const aside = page.getByRole('complementary')
    await aside.getByText('Production verified', { exact: true }).waitFor()
    await aside.getByText('Keep improving your AI', { exact: true }).waitFor()
    await aside.getByText('What would you like to improve?', { exact: true }).waitFor()
    assert.equal(await aside.getByText('Give your AI something to know.', { exact: true }).count(), 0)
    await aside.getByRole('button', { name: 'Close resource details' }).click()
    await aside.waitFor({ state: 'detached' })
    await rows.getByText('Production verified', { exact: true }).waitFor()
    for (const id of ownedBrainIds) for (const kind of ['releases', 'target-profiles']) {
      assert.equal(reads.filter(path => path === `/api/deployments/${id}/${kind}`).length, 1, 'Selection and filters must reuse the actual query cache')
    }
  })
  for (const [scenario, message] of [['live-error', 'Live status could not be loaded for some Brains.'],
    ['profiles-error', 'Live status could not be loaded for some Brains.'], ['unknown-profile', 'Live status is unavailable for some Brains.']]) {
    await run(`${scenario} is distinct from a successful empty Live filter`, async page => {
      await page.goto(base + `/deployments?case=${scenario}`)
      await page.getByRole('button', { name: 'Live', exact: true }).click()
      await page.getByText(message, { exact: true }).waitFor()
      assert.equal(await page.getByRole('list', { name: 'Smart Brains' }).getByText('Production verified').count(), 0)
    })
  }
  await run('ready app with a failed exact assignment stays not live while a separate production release is verified', async page => {
    await page.goto(base + inspect('brain-support', '&case=assigned-failed'))
    const aside = page.getByRole('complementary')
    await aside.getByText('Production verified', { exact: true }).waitFor()
    await aside.getByText('Experience not live · Production · Release needs attention', { exact: true }).waitFor()
    assert.equal(await aside.getByText('Live experience', { exact: false }).count(), 0)
    await aside.locator('summary').filter({ hasText: 'Technical details' }).click()
    await aside.getByText('FAILED / FAILED', { exact: true }).waitFor()
  })
  await run('inactive Engage does not become live merely because its Brain production release is verified', async page => {
    await page.goto(base + inspect('brain-support', '&case=inactive-app'))
    const aside = page.getByRole('complementary')
    await aside.getByText('Production verified', { exact: true }).waitFor()
    await aside.getByText('Disabled app · Production · Verified release', { exact: true }).waitFor()
    assert.equal(await aside.getByText('Live experience', { exact: false }).count(), 0)
  })
  await run('missing actual staging metadata never falls back to production or original offer pins', async page => {
    await page.goto(base + inspect('brain-support', '&case=no-staging&releaseId=release-prod'))
    const aside = page.getByRole('complementary')
    await aside.getByText('Production verified', { exact: true }).waitFor()
    await aside.getByText('Keep improving your AI', { exact: true }).waitFor()
    assert.equal(await aside.getByRole('link', { name: 'Try in staging' }).getAttribute('href'), '/deployments?view=setup&deploymentId=brain-support&step=staging')
  })
  for (const theme of ['light', 'dark']) {
    await run(`${theme} desktop guide, compact quota and canonical setup links`, async (page, reads) => {
      await page.goto(base + inspect('brain-customer', `&theme=${theme}`))
      const aside = page.getByRole('complementary')
      await aside.getByRole('button', { name: 'Build with AI', exact: true }).waitFor()
      await page.getByText('1 / 2 used', { exact: true }).waitFor()
      assert.equal(await aside.getByRole('tab').count(), 0)
      assert.equal(await aside.getByText('brain-customer', { exact: true }).isVisible(), false)
      for (const [name, path] of [['Add your documents', 'knowledge/documents'], ['Connect your tools', 'capabilities'], ['Shape its answers', 'responses']]) {
        const href = await aside.getByRole('link', { name: new RegExp(name) }).getAttribute('href')
        assert.equal(href, `/smart-brains/brain-customer/${path}`)
      }
      await noOverflow(page)
      const rowHeights = await page.getByRole('list', { name: 'Smart Brains' }).getByRole('button').evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().height))
      assert.ok(rowHeights.every(height => height <= 110), `Compact desktop rows: ${rowHeights}`)
      await page.screenshot({ path: join(shots, `${theme}-desktop.png`), fullPage: true })
      const before = reads.filter(path => path.endsWith('infrastructure-quota')).length
      await page.getByRole('button', { name: 'Refresh infrastructure allowance' }).click()
      await page.waitForFunction(() => !document.querySelector('[aria-label="Refresh infrastructure allowance"]')?.disabled)
      assert.ok(reads.filter(path => path.endsWith('infrastructure-quota')).length > before)
      await page.locator('summary').filter({ hasText: 'Environments' }).click()
      await page.getByText('Stopped infrastructure counts until removed.', { exact: false }).waitFor()
      assert.equal(await page.getByRole('link', { name: /Customer Support Brain.*production/ }).getAttribute('href'), '/revisions?deploymentId=brain-support&targetProfileId=prod')
      await aside.getByRole('button', { name: 'Build with AI', exact: true }).click()
      await page.waitForURL('**/smart-brains/brain-customer')
    })
    await run(`${theme} 390px list, long name, technical disclosure and close`, async page => {
      await page.goto(base + `/deployments?case=long&theme=${theme}`)
      await page.getByRole('button', { name: /Inspect International/ }).waitFor()
      await noOverflow(page)
      await page.screenshot({ path: join(shots, `${theme}-mobile-list.png`), fullPage: true })
      await page.getByRole('button', { name: /Inspect International/ }).click()
      const aside = page.getByRole('complementary')
      await aside.getByRole('button', { name: 'Build with AI', exact: true }).waitFor()
      await noOverflow(page)
      await page.screenshot({ path: join(shots, `${theme}-mobile-inspector.png`) })
      await aside.locator('summary').filter({ hasText: 'Technical details' }).click()
      await aside.getByText('brain-support', { exact: true }).waitFor()
      await noOverflow(page)
      await aside.getByRole('button', { name: 'Close resource details' }).click()
      await aside.waitFor({ state: 'detached' })
      await noOverflow(page)
    }, { width: 390, height: 844 })
  }
  await run('assigned live evidence, selected release, all technical controls and historical failures survive', async page => {
    await page.goto(base + inspect('brain-support', '&case=connected&releaseId=release-prod'))
    const aside = page.getByRole('complementary')
    await aside.getByText('Production verified', { exact: true }).waitFor()
    await aside.getByText('Live experience · Production · Verified release', { exact: true }).waitFor()
    assert.equal(await aside.getByText('release-prod', { exact: true }).isVisible(), false)
    assert.equal(await aside.getByRole('link', { name: 'Try in staging' }).getAttribute('href'), '/smart-brains/brain-support/try?targetProfileId=stage')
    await aside.locator('summary').filter({ hasText: 'Technical details' }).click()
    for (const label of ['Configuration state', 'Published version', 'Offer ID', 'Release references']) await aside.getByText(label, { exact: true }).waitFor()
    await aside.getByRole('link', { name: 'Open advanced settings' }).count().then(count => assert.equal(count, 0))
    await aside.getByRole('button', { name: 'Open advanced settings' }).waitFor()
    await aside.getByRole('link', { name: 'Open LoomAI Desk' }).waitFor()
    await aside.getByRole('combobox', { name: 'Working environment' }).waitFor()
    await aside.locator('summary').filter({ hasText: 'Other release history' }).click()
    await aside.getByText('release-old-failed', { exact: true }).waitFor()
    await aside.getByText('Historical failure', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Live', exact: true }).click()
    assert.equal(await page.getByRole('list', { name: 'Smart Brains' }).getByRole('button').count(), 1)
    await aside.getByRole('button', { name: 'Support app', exact: true }).first().click()
    await page.waitForURL('**/deployments?**resourceKind=app**')
    await page.getByRole('complementary').getByRole('link', { name: 'Appearance & preview' }).waitFor()
  })
  for (const [scenario, label] of [['missing', "An app's assigned release details are unavailable."], ['error', 'Release details could not be loaded: Release service unavailable'], ['loading', 'Loading release details...'], ['failed', 'Recorded verification failure']]) {
    await run(`${scenario} release evidence stays visible and is never reported live`, async page => {
      await page.goto(base + inspect('brain-support', `&case=${scenario}`))
      const aside = page.getByRole('complementary')
      await aside.getByText(label, { exact: false }).first().waitFor()
      assert.equal(await aside.getByText('Production verified', { exact: true }).count(), 0)
      if (scenario === 'missing') await aside.getByText('Assigned release unavailable', { exact: false }).waitFor()
    })
  }
  await run('explicit unavailable release is not replaced by the healthy assignment', async page => {
    await page.goto(base + inspect('brain-support', '&case=connected&releaseId=foreign-release'))
    await page.getByText('The requested release is not available for this resource.', { exact: false }).waitFor()
    await page.getByRole('complementary').locator('summary').filter({ hasText: 'Technical details' }).click()
    assert.equal(await page.getByText('Production / release-prod', { exact: true }).count(), 0)
  })
  await run('unavailable app summaries do not erase production evidence or claim a live app', async page => {
    await page.goto(base + inspect('brain-support', '&case=resources-error'))
    const aside = page.getByRole('complementary')
    await aside.getByText('App details unavailable.', { exact: true }).waitFor()
    await aside.getByText('Production verified', { exact: true }).waitFor()
    assert.equal(await aside.getByText('Live experience', { exact: false }).count(), 0)
    await page.getByRole('list', { name: 'Smart Brains' }).getByText('Engage unavailable', { exact: true }).first().waitFor()
  })
  await run('an unavailable requested resource never selects another Brain', async page => {
    await page.goto(base + inspect('foreign-brain'))
    await page.getByText('The requested resource is not available in this account.', { exact: false }).waitFor()
    assert.equal(await page.getByRole('complementary').count(), 0)
  })
  await run('Engage apps keep existing details, appearance, open, install and alternate-delivery actions', async (page, reads) => {
    await page.goto(base + '/ai-workspaces?case=embedded&resourceKind=app&resourceId=app-support')
    const aside = page.getByRole('complementary')
    await aside.getByRole('button', { name: 'Configure', exact: true }).waitFor()
    assert.equal(await page.getByRole('group', { name: 'Filter Smart Brains' }).count(), 0)
    assert.equal(await aside.getByText('Your next step').count(), 0)
    assert.equal(await aside.getByRole('link', { name: 'Appearance & preview' }).getAttribute('href'), '/ai-workspaces?view=manage&previewInstallationId=app-support')
    assert.equal(await aside.getByRole('link', { name: 'Open app', exact: true }).getAttribute('href'), 'https://support.example.com/')
    assert.equal(await aside.getByRole('link', { name: 'Add standalone app' }).getAttribute('href'), '/ai-workspaces?view=manage&sourceInstallationId=app-support&delivery=standalone')
    await aside.getByRole('button', { name: 'Installation script' }).click()
    await page.getByText('Existing app configuration', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Close configuration', exact: true }).click()
    await page.getByRole('button', { name: 'Close panel', exact: true }).click()
    await page.getByText('Existing app configuration', { exact: true }).waitFor({ state: 'detached' })
    await aside.getByRole('tab', { name: 'Advanced', exact: true }).click()
    await aside.getByText('Assigned release', { exact: true }).waitFor()
    assert.equal(reads.filter(path => /\/(releases|target-profiles)$/.test(path)).length, 0, 'App presentation must not start library evidence reads')
  })
  await run('standalone app retains alternate embed, reassignment and preview destinations', async page => {
    await page.goto(base + '/ai-workspaces?resourceKind=app&resourceId=app-support')
    const aside = page.getByRole('complementary')
    await aside.getByRole('link', { name: 'Add website embed', exact: true }).waitFor()
    assert.equal(await aside.getByRole('link', { name: 'Add website embed' }).getAttribute('href'), '/ai-workspaces?view=manage&sourceInstallationId=app-support&delivery=embedded')
    assert.equal(await aside.getByRole('link', { name: 'Change smart brain' }).getAttribute('href'), '/ai-workspaces?view=manage&connectInstallationId=app-support')
    await aside.getByRole('link', { name: 'Appearance & preview' }).click()
    await page.waitForURL('**/ai-workspaces?view=manage&previewInstallationId=app-support')
  })
  await run('home retains the full quota and existing resource view', async (page, reads) => {
    await page.goto(base + '/home')
    await page.getByText('Infrastructure environments', { exact: true }).waitFor()
    await page.getByRole('heading', { name: 'Your AI experiences', exact: true }).waitFor()
    assert.equal(await page.getByRole('group', { name: 'Filter Smart Brains' }).count(), 0)
    assert.equal(reads.filter(path => /\/(releases|target-profiles)$/.test(path)).length, 0, 'Home must not start library evidence reads')
  })
  console.log(`${passed} Brain library browser scenarios passed; screenshots: ${shots}`)
} finally {
  await browser.close()
  await server.close()
  await rm(cacheDir, { recursive: true, force: true })
}
