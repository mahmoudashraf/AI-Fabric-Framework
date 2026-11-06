import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const previewBundle = await readFile(new URL('../../../max-mode-widget/dist/engage-preview.iife.js', import.meta.url), 'utf8')
const output = process.env.SUPPORT_KIT_UI_OUTPUT || '/private/tmp/loomai-support-kit-ui'
await mkdir(output, { recursive: true })
const session = { enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE',
  actorId: 'owner-one', customerId: 'customer-one', customerName: 'Example Support', displayName: 'Alex' }
const definition = { code: 'ai-support-experience', version: '1.0.0', name: 'AI Support Experience', summary: 'Help customers solve problems.',
  description: 'Approved knowledge, linked requests and optional human support.', experiencePackCode: 'support', defaultWorkspaceName: 'Your support AI',
  features: ['Approved support knowledge', 'Tracked requests', 'Optional human handover'], demoUrl: null }
const configuration = { schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'Your support AI' },
  capabilities: { tickets: false, handover: false, currentPage: false, attachments: true }, theme: { primaryColor: '#409aff' } }
const offer = { id: 'offer-support', name: 'Technical support', entitlement: { customerId: 'customer-one', status: 'ACTIVE' }, blockers: [], bindingSlots: [], settings: [],
  targets: [{ id: 'stage', intent: 'CUSTOMER_STAGING', blockers: [], capabilities: [] }],
  workspaceProfiles: [{ id: 'support-style', label: 'Support workspace', interaction: { configuration }, theme: { primaryColor: '#409aff' } }] }
const catalogue = [{ definition, offers: [offer], ticketProviders: [{ code: 'desk', name: 'Desk-managed tickets', declaredOperations: ['createRequest', 'getOwnRequest'] }] }]
const releases = [
  { id: 'verified-one', deploymentId: 'dep-support', targetProfileId: 'stage', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE', createdAt: '2026-11-03T10:00:00Z' },
  { id: 'verified-two', deploymentId: 'dep-support', targetProfileId: 'stage-two', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE', createdAt: '2026-11-03T11:00:00Z' },
  { id: 'foreign', deploymentId: 'dep-foreign', targetProfileId: 'foreign', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE', createdAt: '2026-11-03T12:00:00Z' },
  { id: 'retired', deploymentId: 'dep-support', targetProfileId: 'old', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'RETIRED', createdAt: '2026-11-03T12:00:00Z' },
]
const app = (body, installationId = 'awi-support') => ({ installationId, customerId: 'customer-one', displayName: body.workspaceName,
  deploymentId: 'dep-support', releaseId: null, rowVersion: 7, status: 'DRAFT', ready: false, experiencePackCode: 'support', experiencePackVersion: '1.0.0',
  configuration: { ...configuration, app: { assistantLabel: body.workspaceName }, theme: { primaryColor: body.brandColor },
    capabilities: { tickets: body.ticketingEnabled, handover: body.humanSupportEnabled, currentPage: false, attachments: true } },
  deliveryMode: body.deliveryMode === 'embedded' ? 'embedded' : 'standalone', appSlug: body.appSlug ?? '', appUrl: null,
  allowedOrigins: body.websiteOrigin ? [body.websiteOrigin] : [], readinessChecks: [{ code: 'SUPPORT_SPACE', status: 'BLOCKED', message: 'Configure managed support.' }], domains: [] })
let kit = null, createError = 0, setupError = 0, statusError = 0, humanEnabled = false
const spaces = new Map(), calls = [], errors = [], groups = []
const authId = '\0kit-owner-auth', entry = '\0kit-support-fixture'
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
  cacheDir: `${output}/vite-cache-${process.pid}`, resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'support-kit-owner-fixture', enforce: 'pre', resolveId(id) {
    if (id.endsWith('/auth/PlatformAuthProvider')) return authId
    if (id === '/support-kit-fixture.jsx') return entry
  }, load(id) {
    if (id === authId) return `export function usePlatformAuth(){return {session:${JSON.stringify(session)},isLoading:false}}`
    if (id === entry) return `import React from'react';import{createRoot}from'react-dom/client';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{BrowserRouter,Routes,Route}from'react-router-dom';
      import{ExperienceKitsPage}from'/src/pages/ExperienceKitsPage.tsx';import{ExperienceKitsPublicPage}from'/src/pages/ExperienceKitsPublicPage.tsx';
      import{CustomerShellLayout}from'/src/layout/CustomerConsoleShell.tsx';import{setPlatformAccessTokenProvider}from'/src/api/platformApi.ts';
      import{EngageAppearancePreview}from'/src/components/customer/EngageAppearancePreview.tsx';
      setPlatformAccessTokenProvider(async()=>'fixture-owner-token');
      const customer=element=>React.createElement(CustomerShellLayout,{session:${JSON.stringify(session)},onSignOut:async()=>{},buildWorkspacePath:p=>p,expandedNavigation:false,supportEnabled:false},element);
      const appearance=React.createElement(EngageAppearancePreview,{appearance:{assistantLabel:'Support preview',primaryColor:'#409aff',darkMode:'light',logoUrl:'',welcomeMessage:'Approved support guidance',placeholder:'Ask support'},
        appName:'Support preview',deliveryMode:'standalone',showBrandingError:false,packCode:'support',interactionPolicy:'public',
        configuration:{...${JSON.stringify(configuration)},capabilities:{tickets:new URLSearchParams(location.search).get('tickets')!=='off',handover:false}},
        capabilities:{history:true,attachments:true,currentPage:false,sources:true,authentication:{signInAvailable:false},handover:{enabled:false,runtimeConfigured:false,userCanCancelPending:false,userCanResumeAi:false}}});
      createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client:new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})},React.createElement(BrowserRouter,null,React.createElement(Routes,null,
        React.createElement(Route,{path:'/appearance-fixture',element:customer(appearance)}),
        React.createElement(Route,{path:'/experiences',element:React.createElement(ExperienceKitsPublicPage,{authenticated:true})}),
        React.createElement(Route,{path:'/experiences/ai-support-experience',element:React.createElement(ExperienceKitsPublicPage,{code:'ai-support-experience',authenticated:true})}),
        ...['/kits','/kits/new/:code','/kits/:id'].map(path=>React.createElement(Route,{key:path,path,element:customer(React.createElement(ExperienceKitsPage))}))
      ))));`
  }, configureServer(vite) { vite.middlewares.use((req, res, next) => {
    if (!/^\/(?:kits|experiences|appearance-fixture)(?:\/|$)/.test(new URL(req.url, 'http://fixture').pathname)) return next()
    res.setHeader('Content-Type', 'text/html')
    res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/support-kit-fixture.jsx"></script></body></html>')
  }) } }],
})
await server.listen()
const base = server.resolvedUrls.local[0]
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
page.setDefaultTimeout(20_000)
await page.addInitScript(() => { if (window === window.top && !localStorage.getItem('loomai-customer-console-color-scheme')) localStorage.setItem('loomai-customer-console-color-scheme', 'light') })
page.on('pageerror', error => errors.push(error.stack || error.message))
const passed = message => { groups.push(message); console.log(`PASS ${message}`) }
try {
  await page.route('**/engage-preview.iife.js', route => route.fulfill({ contentType: 'text/javascript', body: previewBundle }))
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    if (!path.startsWith('/api/')) return route.continue()
    const body = request.postDataJSON()
    calls.push({ path, method, body, query: url.search, authorization: request.headers().authorization })
    const json = (value, status = 200) => route.fulfill({ json: value, status })
    if (path === '/api/public/experience-kits') return json([definition])
    if (path === '/api/customer-experience-kits/catalogue') return json(catalogue)
    if (path === '/api/customer-self-service/enrollment') return json({})
    if (path === '/api/customer-experience-kits' && method === 'GET') return json(kit ? [kit] : [])
    if (path === '/api/customer-experience-kits' && method === 'POST') {
      if (createError) return json({ message: 'Synthetic unconfirmed create.' }, createError)
      kit = { ...body, id: 'kit-support', kitVersion: '1.0.0', deploymentId: 'dep-support', consumerId: 'consumer-support',
        installationId: 'awi-support', embeddedInstallationId: body.deliveryMode === 'both' ? 'awi-embed' : null,
        workspace: app(body), embeddedWorkspace: body.deliveryMode === 'both' ? app(body, 'awi-embed') : null,
        revision: 1, createdAt: '2026-11-03T10:00:00Z', updatedAt: '2026-11-03T10:00:00Z' }
      return json(kit)
    }
    if (path === '/api/customer-experience-kits/kit-support') {
      if (method === 'PUT') {
        assert.equal(body.expectedRevision, kit.revision)
        assert.equal(body.expectedWorkspaceRevision, kit.workspace.rowVersion)
        kit = { ...kit, ...body, revision: kit.revision + 1, workspace: { ...app(body), rowVersion: kit.workspace.rowVersion + 1 } }
      }
      return json(kit)
    }
    if (path === '/api/customer-self-service/deployments') return json([{ deploymentId: 'dep-support', name: 'Support assistant', status: 'DRAFT' }])
    if (path === '/api/customer-self-service/catalogue/offer-support') return json(offer)
    if (path === '/api/deployments/dep-support/document-storage/connections') return json({ hostedAvailable: false, unavailableReason: 'Storage is not enabled in this support fixture.', maxUploadBytes: 1048576, supportedExtensions: ['txt'], connections: [] })
    if (path === '/api/deployments/dep-support/document-storage-bindings') return json([])
    if (path === '/api/deployments/dep-support/releases') return json(releases)
    if (path.endsWith('/desk')) return json({ status: humanEnabled ? 'ENABLED' : 'DISABLED', portalUrl: null })
    if (path.endsWith('/support-space')) {
      if (method === 'GET' && statusError) return json({ message: 'The selected runtime artifact does not support canonical conversations.' }, statusError)
      const installationId = path.split('/').at(-2)
      const target = method === 'PUT' ? body.targetProfileId : url.searchParams.get('targetProfileId')
      const scope = `${installationId}:${target}`
      if (method === 'PUT') {
        assert.equal(body.expectedVersion, spaces.get(scope)?.version ?? null)
        if (setupError) return json({ message: 'PRIVATE-UPSTREAM-SECRET must not be rendered.' }, setupError)
        spaces.set(scope, { spaceId: `space-${target}`, providerId: 'desk', providerBindingId: `binding-${target}`, version: `W/"space:${spaces.size + 1}"`,
          enabled: body.enabled, humanHandoverEnabled: body.humanHandoverEnabled, closedRetentionDays: 30, capabilities: ['CREATE', 'GET', 'LIST'] })
      }
      return json({ status: spaces.has(scope) ? 'CONFIGURED' : 'NOT_CONFIGURED', assignmentRevision: 'assignment:opaque-current', space: spaces.get(scope) ?? null })
    }
    errors.push(`Unhandled ${method} ${path}`)
    return json({ message: 'Unknown fixture API' }, 500)
  })
  await page.goto(base + 'experiences')
  await page.getByRole('heading', { name: 'AI Support', exact: true }).waitFor()
  const choice = page.locator('[data-kind="support"] article')
  await choice.getByText('Request #1042', { exact: true }).waitFor()
  assert.doesNotMatch(await choice.innerText(), /Chair|GBP|Compare cars|Delivery/)
  await choice.getByRole('button', { name: 'Preview support', exact: true }).click()
  const preview = page.getByRole('dialog')
  await preview.getByRole('checkbox', { name: 'Request tracking sample' }).uncheck()
  await preview.getByRole('checkbox', { name: 'Human support sample' }).check()
  assert.equal(await preview.getByText('SAMPLE-001', { exact: false }).count(), 0)
  await preview.getByText('Example support team', { exact: true }).waitFor()
  assert.equal(calls.filter(call => call.method !== 'GET').length, 0)
  await page.getByRole('button', { name: 'Close preview', exact: true }).click()
  passed('Support catalogue and fixture preview are explicit, non-shopping and network-read-only')
  await page.goto(base + 'experiences/ai-support-experience')
  await page.getByText('Turn your support page into an AI-enabled help workspace.', { exact: true }).waitFor()
  await page.screenshot({ path: `${output}/01-support-public-light.png`, fullPage: true })
  await page.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  const mobileBounds = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
    overflowing: [...document.querySelectorAll('body *')].map(element => ({ element: element.tagName, className: element.className, right: element.getBoundingClientRect().right }))
      .filter(value => value.right > innerWidth + 1).slice(0, 12) }))
  assert.ok(mobileBounds.scroll <= mobileBounds.width, JSON.stringify(mobileBounds))
  await page.screenshot({ path: `${output}/02-support-public-mobile-dark.png`, fullPage: true })
  passed('Support public detail retains approved kit layout, dark/light and mobile bounds')
  await page.setViewportSize({ width: 1440, height: 1100 })
  assert.equal(await page.getByRole('link', { name: 'Talk to Mo', exact: true }).first().getAttribute('href'), 'https://calendly.com/mahmoud-elgammal-loomai/loomai-integration-customer-success')
  await page.getByRole('link', { name: 'Install kit', exact: true }).first().click()
  await page.getByRole('heading', { name: 'Choose your starting point', exact: true }).waitFor()
  assert.equal(await page.getByRole('link', { name: 'Talk to Mo', exact: true }).getAttribute('href'), 'https://calendly.com/mahmoud-elgammal-loomai/loomai-integration-customer-success')
  assert.equal(await page.getByRole('link', { name: 'LoomAI website', exact: true }).getAttribute('href'), 'https://loomai.pro/')
  await page.getByRole('button', { name: 'Continue setup', exact: true }).click()
  await page.getByLabel(/^App address name/).fill('example-support')
  const tracking = page.getByRole('checkbox', { name: 'Include request tracking', exact: true })
  const human = page.getByRole('checkbox', { name: 'Include human support setup', exact: true })
  assert.equal(await tracking.isChecked(), false); assert.equal(await human.isChecked(), false)
  await tracking.check()
  await page.getByRole('button', { name: 'Continue setup', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'Choose an available ticket provider' }).waitFor()
  await page.getByRole('combobox', { name: /^Ticket provider/ }).click()
  await page.getByRole('option', { name: 'Desk-managed tickets', exact: true }).click()
  assert.equal(await human.isChecked(), false)
  await page.screenshot({ path: `${output}/03-support-setup.png`, fullPage: true })
  await page.getByRole('button', { name: 'Continue setup', exact: true }).click()
  createError = 503
  await page.getByRole('button', { name: 'Create my workspace', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'Synthetic unconfirmed create' }).waitFor()
  createError = 0
  await page.getByRole('button', { name: 'Create my workspace', exact: true }).click()
  await page.waitForURL('**/kits/kit-support')
  await page.getByRole('heading', { name: 'AI Support Experience', exact: true }).waitFor()
  const creates = calls.filter(call => call.path === '/api/customer-experience-kits' && call.method === 'POST')
  assert.equal(creates.length, 2); assert.equal(creates[0].body.operationKey, creates[1].body.operationKey)
  assert.deepEqual(creates[0].body, creates[1].body)
  assert.equal(creates[1].body.ticketingEnabled, true); assert.equal(creates[1].body.humanSupportEnabled, false); assert.equal(creates[1].body.ticketProvider, 'desk')
  assert.equal(calls.filter(call => call.path.endsWith('/support-space') && call.method === 'PUT').length, 0)
  passed('setup uses explicit catalogue provider and independent flags; uncertain create retains one operation identity with no activation')
  await page.getByRole('button', { name: 'Configure support', exact: true }).click()
  const releaseSelect = page.getByRole('combobox', { name: 'Verified support release', exact: true })
  await releaseSelect.click()
  assert.equal(await page.getByRole('option', { name: /foreign|retired/ }).count(), 0)
  await page.getByRole('option', { name: 'stage \u00b7 verified-one', exact: true }).click()
  const configure = page.getByRole('button', { name: 'Configure support service', exact: true })
  await configure.waitFor()
  setupError = 409
  await configure.click()
  await page.getByRole('alert').filter({ hasText: 'Setup changed or a prerequisite is incomplete' }).waitFor()
  assert.equal((await page.locator('body').innerText()).includes('PRIVATE-UPSTREAM-SECRET'), false)
  setupError = 0
  await page.getByRole('button', { name: 'Refresh support setup', exact: true }).click()
  await configure.click()
  await page.getByText('Configured for these settings', { exact: true }).waitFor()
  const setups = calls.filter(call => call.path.endsWith('/support-space') && call.method === 'PUT')
  assert.equal(setups[0].body.operationKey, setups[1].body.operationKey)
  assert.deepEqual(setups[1].body, { installationRevision: 7, assignmentRevision: 'assignment:opaque-current', expectedVersion: null,
    enabled: true, humanHandoverEnabled: false, ticketProvider: 'desk', closedRetentionDays: null, targetProfileId: 'stage', releaseId: 'verified-one', operationKey: setups[0].body.operationKey })
  assert.equal(kit.workspace.releaseId, null)
  await page.screenshot({ path: `${output}/04-support-overview.png`, fullPage: true })
  passed('owner setup uses exact verified target, installation revision, assignment and opaque version without app rebinding or human authority')
  await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
  const edit = page.getByRole('dialog')
  await edit.getByRole('checkbox', { name: 'Include request tracking', exact: true }).uncheck()
  await edit.getByRole('checkbox', { name: 'Include human support setup', exact: true }).check()
  await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
  await edit.waitFor({ state: 'hidden' })
  assert.equal(kit.ticketingEnabled, false); assert.equal(kit.humanSupportEnabled, true)
  await page.getByRole('combobox', { name: 'Verified support release', exact: true }).click()
  await page.getByRole('option', { name: 'stage \u00b7 verified-one', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: "Connect this target's human support channel" }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Update support setup', exact: true }).isDisabled(), true)
  humanEnabled = true
  await page.reload()
  await page.getByRole('button', { name: 'Configure support', exact: true }).click()
  await page.getByRole('combobox', { name: 'Verified support release', exact: true }).click()
  await page.getByRole('option', { name: 'stage \u00b7 verified-one', exact: true }).click()
  await page.getByRole('button', { name: 'Update support setup', exact: true }).click()
  await page.getByText('Configured for these settings', { exact: true }).waitFor()
  const humanSetup = calls.filter(call => call.path.endsWith('/support-space') && call.method === 'PUT').at(-1)
  assert.equal(humanSetup.body.expectedVersion, setups[1] ? 'W/"space:1"' : null)
  assert.equal(humanSetup.body.installationRevision, 8)
  assert.equal(humanSetup.body.enabled, false); assert.equal(humanSetup.body.humanHandoverEnabled, true)
  passed('edit preserves independent flags and exact revisions; human-only setup requires the same target Desk channel first')
  kit.ticketProvider = null
  await page.reload()
  await page.getByRole('button', { name: 'Configure support', exact: true }).click()
  await page.getByText('Choose a ticket provider in Edit your experience before configuring Desk.', { exact: true }).waitFor()
  await page.getByRole('button', { name: 'Edit your experience', exact: true }).click()
  await page.getByRole('dialog').getByRole('combobox', { name: /^Ticket provider/ }).click()
  await page.getByRole('option', { name: 'Desk-managed tickets', exact: true }).click()
  assert.equal(await page.getByRole('dialog').getByRole('checkbox', { name: 'Include request tracking', exact: true }).isChecked(), false)
  await page.getByRole('dialog').getByRole('button', { name: 'Save changes', exact: true }).click()
  await page.getByRole('dialog').waitFor({ state: 'hidden' })
  assert.equal(kit.ticketProvider, 'desk'); assert.equal(kit.ticketingEnabled, false)
  passed('human-only kits with no provider have an explicit catalogue choice without silently enabling tickets')
  kit.deliveryMode = 'both'; kit.workspace.releaseId = 'verified-one'
  kit.embeddedInstallationId = 'awi-embed'
  kit.embeddedWorkspace = { ...app({ ...kit, workspaceName: 'Website support AI' }, 'awi-embed'), rowVersion: 17, releaseId: 'verified-two', deliveryMode: 'embedded' }
  await page.reload()
  await page.getByRole('button', { name: 'Configure support', exact: true }).click()
  const embedded = page.getByRole('region', { name: 'Support setup for Website support AI', exact: true })
  await embedded.getByRole('button', { name: 'Configure support service', exact: true }).click()
  await embedded.getByText('Configured for these settings', { exact: true }).waitFor()
  const embeddedSetup = calls.filter(call => call.path.endsWith('/awi-embed/support-space') && call.method === 'PUT').at(-1)
  assert.equal(embeddedSetup.body.installationRevision, 17)
  assert.equal(embeddedSetup.body.releaseId, 'verified-two'); assert.equal(embeddedSetup.body.targetProfileId, 'stage-two')
  assert.equal(embeddedSetup.body.expectedVersion, null)
  passed('both delivery channels configure their own installation revision and exact assigned target independently')
  const beforeBlockedRead = calls.filter(call => call.path.endsWith('/support-space') && call.method === 'PUT').length
  const runtimeBlocker = 'Deploy the current runtime artifact with canonical conversation control before activating support.'
  kit.workspace.ready = false
  kit.workspace.configuration.providerConfig = { canonicalConversationsEnabled: true }
  kit.workspace.readinessChecks = [{ code: 'CANONICAL_CONVERSATIONS', status: 'BLOCKED', message: runtimeBlocker }]
  statusError = 409
  await page.reload()
  await page.getByRole('button', { name: 'Configure support', exact: true }).click()
  await page.getByText(runtimeBlocker, { exact: true }).first().waitFor()
  await page.getByRole('alert').filter({ hasText: 'Setup changed or a prerequisite is incomplete' }).first().waitFor()
  const blockedSetup = page.getByRole('button', { name: 'Configure support service', exact: true })
  assert.equal(await blockedSetup.count(), 2)
  for (const button of await blockedSetup.all()) assert.equal(await button.isDisabled(), true)
  assert.equal(await page.getByText('Ready to go live', { exact: true }).count(), 0)
  assert.equal(calls.filter(call => call.path.endsWith('/support-space') && call.method === 'PUT').length, beforeBlockedRead)
  statusError = 0
  passed('runtime readiness blocks survive template/human preferences and a failed owner read cannot configure or activate support')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForFunction(() => document.documentElement.scrollWidth <= innerWidth, undefined, { timeout: 2000 }).catch(() => {})
  const overviewBounds = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
    overflowing: [...document.querySelectorAll('body *')].map(element => ({ element: element.tagName, className: element.getAttribute('class'), right: element.getBoundingClientRect().right,
      parent: element.parentElement?.outerHTML.slice(0, 900) }))
      .filter(value => value.right > innerWidth + 1).slice(0, 12) }))
  assert.ok(overviewBounds.scroll <= overviewBounds.width, JSON.stringify(overviewBounds))
  await page.screenshot({ path: `${output}/05-support-overview-mobile.png`, fullPage: true })
  const beforePreview = calls.length
  await page.goto(base + 'appearance-fixture')
  const appearanceFrame = page.frameLocator('iframe[title="Engage customer appearance preview"]')
  await appearanceFrame.getByText('Approved support guidance', { exact: true }).waitFor()
  await page.getByRole('combobox', { name: /^Sample screen/ }).click()
  await page.getByRole('option', { name: 'Conversation', exact: true }).click()
  await appearanceFrame.getByText('Can you help me understand the approved guidance?', { exact: true }).waitFor()
  await page.getByRole('combobox', { name: /^Sample screen/ }).click()
  await page.getByRole('option', { name: 'Receipt', exact: true }).click()
  await page.getByRole('listbox').waitFor({ state: 'hidden' })
  await appearanceFrame.getByText('SAMPLE-001', { exact: false }).waitFor()
  await page.screenshot({ path: `${output}/06-support-engage-preview-mobile.png`, fullPage: true })
  await page.goto(base + 'appearance-fixture?tickets=off')
  await appearanceFrame.getByText('Approved support guidance', { exact: true }).waitFor()
  await page.getByRole('combobox', { name: /^Sample screen/ }).click()
  await page.getByRole('option', { name: 'Receipt', exact: true }).click()
  await appearanceFrame.getByText('Can you help me understand the approved guidance?', { exact: true }).waitFor()
  assert.equal(await appearanceFrame.getByText('SAMPLE-001', { exact: false }).count(), 0)
  assert.equal(calls.length, beforePreview, 'Shared preview has no BFF, requester or runtime calls')
  passed('common Engage preview loads real support fixtures and respects ticket capabilities without live requests')
  assert.ok(calls.every(call => !/(?:publish|activate|apply|provision|tickets|runtime\/api)/.test(call.path)))
  assert.ok(calls.filter(call => call.path.startsWith('/api/customer-experience-kits') || call.path.endsWith('/support-space')).every(call => call.authorization === 'Bearer fixture-owner-token'))
  assert.deepEqual(errors, [])
  await writeFile(`${output}/results.json`, JSON.stringify({ synthetic: true, groups, errors, calls }, null, 2))
  console.log(`Support Kit UI passed ${groups.length} scenario groups. Synthetic owner BFF only. Evidence: ${output}`)
} catch (error) {
  await page.screenshot({ path: `${output}/failure.png`, fullPage: true }).catch(() => {})
  await writeFile(`${output}/failure.json`, JSON.stringify({ synthetic: true, groups, errors, calls, failure: error.stack }, null, 2)).catch(() => {})
  console.error(JSON.stringify({ errors, recentCalls: calls.slice(-12), failure: error.stack }, null, 2))
  throw error
} finally { await browser.close(); await server.close() }
