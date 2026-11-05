import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const widget = await readFile(new URL('../../../max-mode-widget/dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const integrity = value => 'sha384-' + createHash('sha384').update(value).digest('base64')
const calls = [], errors = [], requests = new Map(), contexts = new Map(), controls = new Map(), createdKeys = new Map(), scenarios = []
const evidence = process.env.SUPPORT_UI_OUTPUT || '/private/tmp/loomai-support-ui-01057'
await mkdir(evidence, { recursive: true })
const teamReference = 'team-' + 'a'.repeat(64)
const capabilities = ['CREATE', 'LIST', 'GET', 'RESOLVE', 'CANCEL', 'REOPEN', 'SEEN', 'LINK_CONVERSATION']
const at = '2026-11-03T12:00:00Z'
let token = 'fixture-console-one', createError = 0, startError = 0, contextError = 0, sessionError = 0
const authModule = '\0support-auth.js', entry = '\0support-fixture.jsx'
const session = { enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE',
  actorId: 'requester-one', customerId: 'customer-one', customerName: 'Acme Labs', displayName: 'Alex' }
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
  cacheDir: `${evidence}/vite-cache-${process.pid}`, resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'support-kit-customer-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/support-fixture.jsx') return entry; if (id.endsWith('/auth/PlatformAuthProvider')) return authModule },
    load(id) {
      if (id === authModule) return `import{useEffect,useState}from'react';export function usePlatformAuth(){const[,render]=useState(0);useEffect(()=>{const fn=()=>render(n=>n+1);addEventListener('fixture-auth',fn);return()=>removeEventListener('fixture-auth',fn)},[]);return {
        session:window.__signedOut?null:${JSON.stringify(session)},supabaseSession:window.__signedOut?null:{user:{id:'supabase-subject-one'}},
        supabase:{auth:{getSession:async()=>{window.__tokenReads=(window.__tokenReads||0)+1;return {data:{session:window.__signedOut?null:{access_token:window.__fixtureToken,user:{id:'supabase-subject-one'}}}}}}}
      }}`
      if (id === entry) return `import React from'react';import{createRoot}from'react-dom/client';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{BrowserRouter,Routes,Route}from'react-router-dom';
        import{CustomerSupportPage}from'/src/pages/CustomerSupportPage.tsx';import{CustomerShellLayout}from'/src/layout/CustomerConsoleShell.tsx';
        import{AppShell}from'/src/layout/AppShell.tsx';import{DeploymentWorkspaceProvider}from'/src/workspace/DeploymentWorkspaceContext.tsx';
        import{setPlatformAccessTokenProvider}from'/src/api/platformApi.ts';
        window.__fixtureToken??=${JSON.stringify(token)};setPlatformAccessTokenProvider(async()=>window.__signedOut?null:window.__fixtureToken);
        const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});const staff=location.pathname==='/staff-fixture';
        const routes=React.createElement(Routes,null,React.createElement(Route,{path:'/home',element:React.createElement('h1',null,'Customer home')}),
          React.createElement(Route,{path:'/support',element:React.createElement(CustomerSupportPage)}),
          React.createElement(Route,{path:'/support/requests',element:React.createElement(CustomerSupportPage,{view:'requests'})}),
          React.createElement(Route,{path:'/support/:caseId',element:React.createElement(CustomerSupportPage)}));
        const shell=staff?React.createElement(DeploymentWorkspaceProvider,null,React.createElement(AppShell,{session:{...${JSON.stringify(session)},role:'PLATFORM_OPERATOR',customerId:null},onSignOut:async()=>{}},React.createElement('h1',null,'Staff shell fixture')))
          :React.createElement(CustomerShellLayout,{session:${JSON.stringify(session)},onSignOut:async()=>{},buildWorkspacePath:p=>p,expandedNavigation:false,supportEnabled:true},routes);
        createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},React.createElement(BrowserRouter,null,shell)));`
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      const path = new URL(req.url, 'http://fixture.local').pathname
      if (!['/home', '/staff-fixture', '/support'].includes(path) && !path.startsWith('/support/')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/support-fixture.jsx"></script></body></html>')
    }) },
  }],
})
await server.listen()
const base = server.resolvedUrls.local[0]
const browser = await chromium.launch({ headless: true })
let page
const passed = label => { scenarios.push(label); console.log(`PASS ${label}`) }
const requestCreates = () => calls.filter(call => call.method === 'POST' && call.path === '/api/support/requests')
const requestStarts = () => calls.filter(call => call.method === 'POST' && call.path.endsWith('/start'))
const bootstrap = id => ({ conversationId: id ? requests.get(id).conversation.conversationId : 'conversation-first', principalNamespace: id ? `support:${id}:episode-1` : 'support:conversation-first',
  apiBaseUrl: id ? `/api/support/requests/${id}/runtime/api` : '/api/support/conversations/conversation-first/runtime/api', installationId: 'awi-real-support-fixture', assignmentRevision: 'verified-support-assignment',
  workspaceAssetUrl: '/api/public/ai-workspace/assets/widget.js', workspaceIntegrity: integrity(widget),
  experienceModuleUrl: '/api/public/ai-workspace/assets/support.js', experienceIntegrity: integrity('pack bundled in Console'),
  configuration: { schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'LoomAI Support', title: 'Integration support' },
    support: { spaceId: 'space-one', ticketProviderBindingId: 'provider-one', requestLabel: 'My Requests', teamVisible: true, topics: [{ code: 'guidance', label: 'Guidance' }] },
    capabilities: { tickets: true, handover: true, attachments: true, currentPage: false }, copy: { starterSuggestions: [] }, toolRail: { items: [] } },
  conversationHandover: { enabled: true, userCanCancelPending: true, userCanResumeAi: true } })
try {
  page = await browser.newPage({ viewport: { width: 1500, height: 1050 } })
  page.setDefaultTimeout(20_000)
  page.on('pageerror', error => errors.push(error.stack || error.message))
  const photo = Buffer.from(await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = 8
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#387c83'; ctx.fillRect(0, 0, 8, 8)
    return canvas.toDataURL('image/jpeg').split(',')[1]
  }), 'base64')
  await page.addInitScript(() => {
    window.__photoObjectUrls = []; window.__revokedPhotoObjectUrls = []
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = blob => { const url = create(blob); if (blob.type === 'image/jpeg') window.__photoObjectUrls.push(url); return url }
    URL.revokeObjectURL = url => { window.__revokedPhotoObjectUrls.push(url); revoke(url) }
  })
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/public/ai-workspace/assets/widget.js') return route.fulfill({ contentType: 'text/javascript', body: widget })
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method, body, key: request.headers()['idempotency-key'], authorization: request.headers().authorization })
    const json = (value, status = 200) => route.fulfill({ status, json: value })
    if (request.headers().authorization !== `Bearer ${token}`) return json({ message: 'Console authentication required' }, 401)
    if (path === '/api/support/workspace') return json(bootstrap())
    if (path === '/api/support/notifications') return json({ available: true, enabled: false, deliveryStatus: 'NONE' })
    if (path === '/api/support/session') return sessionError ? json({ message: 'Synthetic session failure' }, sessionError)
      : json({ authenticated: true, requesterId: 'verified-requester-one', space: { spaceId: 'space-one', providerBindingId: 'provider-one', humanHandoverEnabled: true, capabilities }, capabilities })
    if (path === '/api/support/team') return json({ available: true, agents: [{ agentReference: teamReference, displayName: 'Sam Support', photoAvailable: true }, { agentReference: 'team-' + 'b'.repeat(64), displayName: 'Terry Support', photoAvailable: false }] })
    if (path === `/api/support/team/${teamReference}/photo`) return route.fulfill({ contentType: 'image/jpeg', body: photo })
    if (path === '/api/customer-self-service/deployments') return json([])
    if (path === '/api/deployments/overview') return json([{ id: 'current-deployment', name: 'Current deployment', archivedAt: null }, { id: 'archived-deployment', name: 'Archived deployment', archivedAt: at }])
    if (path === '/api/platform/customers/customer-one/ai-workspace-installations') return json([
      { installationId: 'current-app', customerId: 'customer-one', displayName: 'Current app', deploymentId: 'current-deployment' },
      { installationId: 'foreign-app', customerId: 'another-customer', displayName: 'Foreign app', deploymentId: 'current-deployment' },
      { installationId: 'obsolete-app', customerId: 'customer-one', displayName: 'App on archived deployment', deploymentId: 'archived-deployment' }])
    if (path === '/api/support/requests') {
      if (method === 'GET') return json({ items: [...requests.values()].filter(value => !url.searchParams.get('status') || value.status === url.searchParams.get('status')), nextCursor: null })
      assert.deepEqual(Object.keys(body).sort(), ['details', 'subject', 'topic'], 'No Console resource/consent fields in generic creation')
      if (createError) return json({ message: 'Synthetic uncertain create' }, createError)
      if (createdKeys.has(request.headers()['idempotency-key'])) return json(requests.get(createdKeys.get(request.headers()['idempotency-key'])))
      const number = requests.size + 1, id = `request-${number}`, conversation = { episodeId: `episode-${number}`, conversationId: `conversation-${number}`, delivery: 'PENDING' }
      const value = { id, reference: `SUP-2026-${number}`, providerBindingId: 'provider-one', subject: body.subject, details: body.details, topic: body.topic,
        status: 'OPEN', version: 'W/"version-0"', createdAt: at, updatedAt: at, unread: false, openingMessageRequired: true, conversation,
        episodes: [{ ...conversation, number: 1, openingMessageRequired: true, createdAt: at }], events: [{ type: 'CREATED', createdAt: at }], capabilities }
      requests.set(id, value); createdKeys.set(request.headers()['idempotency-key'], id)
      contexts.set(id, { ticketId: id, resources: [], consent: { granted: false, version: 0, changedAt: at, revokedAt: null }, revision: 0 })
      return json(value, 201)
    }
    const match = path.match(/^\/api\/support\/requests\/([^/]+)(.*)$/)
    if (match) {
      const [, id, operation] = match, ticket = requests.get(id)
      if (!ticket) return json({ message: 'Request unavailable' }, 404)
      if (!operation) return json(ticket)
      if (operation === '/workspace') return json(bootstrap(id))
      if (operation === '/context') {
        const context = contexts.get(id)
        if (method === 'PUT') {
          if (contextError) return json({ message: 'Context changed' }, contextError)
          assert.equal(body.expectedRevision, context.revision)
          context.resources = body.resources; context.revision++; context.consent.granted = false; context.consent.version++
        }
        return json(context)
      }
      if (operation === '/context/consent') {
        const context = contexts.get(id)
        assert.equal(body.expectedVersion, context.consent.version)
        context.consent.granted = body.granted; context.consent.version++; context.revision++
        return json(context)
      }
      if (operation === '/seen') {
        assert.equal(body.expectedVersion, ticket.version)
        assert.ok(request.headers()['idempotency-key'])
        ticket.unread = false; ticket.version += ':seen'
        return json(ticket)
      }
      if (['/start', '/resolve', '/cancel', '/reopen'].includes(operation)) {
        assert.equal(body.expectedVersion, ticket.version)
        assert.ok(request.headers()['idempotency-key'])
        if (operation === '/start') {
          if (startError) return json({ message: 'Runtime delivery uncertain' }, startError)
          ticket.conversation.delivery = ticket.episodes[0].delivery = 'DELIVERED'
        } else { ticket.status = operation === '/resolve' ? 'RESOLVED' : operation === '/cancel' ? 'CANCELLED' : 'OPEN'; ticket.version += ':next' }
        return json(ticket)
      }
    }
    if (/^\/api\/support\/(requests|conversations)\/[^/]+\/runtime\/api\/chat\/me\/suggestions$/.test(path)) return json({ success: true, suggestions: [] })
    const runtime = path.match(/^\/api\/support\/(requests|conversations)\/([^/]+)\/runtime\/api\/chat\/me\/conversations\/([^/]+)(.*)$/)
    if (runtime) {
      const [, kind, scopeId, conversationId, operation] = runtime, id = kind === 'requests' ? scopeId : null, ticket = id ? requests.get(id) : null
      if (kind === 'conversations') assert.equal(scopeId, conversationId)
      const control = controls.get(conversationId) || { conversationId, controlState: 'AI_ACTIVE', controlVersion: 1, messages: [] }
      controls.set(conversationId, control)
      if (operation === '/control') return json(control)
      if (operation === '/events') return route.fulfill({ contentType: 'text/event-stream', body: ': connected\n\n' })
      if (operation === '/handover-requests') { control.controlState = 'HUMAN_ACTIVE'; control.controlVersion++; control.handoverId = 'handover-one'; return json({ outcome: 'HANDOVER_REQUESTED', conversation: control }) }
      if (operation === '/ai-resume-requests') { control.controlState = 'AI_ACTIVE'; control.controlVersion++; return json(control) }
      if (operation === '/messages' && method === 'POST') {
        if (control.controlState === 'HUMAN_ACTIVE') return json({ success: true, type: 'HUMAN_MESSAGE_ACCEPTED', conversationId }, 202)
        return json({ success: true, type: 'INFORMATION_PROVIDED', answer: 'A chat-first answer without creating a ticket.', conversationId })
      }
      if (!operation) return json({ id: conversationId, status: 'ACTIVE', controlState: control.controlState, controlVersion: control.controlVersion,
        participantMessages: ticket && ticket.conversation.delivery === 'DELIVERED' ? [
          { messageId: `${id}-user`, role: 'USER', sequence: 1, content: ticket.details, createdAt: at },
          { messageId: `${id}-ai`, role: 'AI', sequence: 2, content: `Canonical guidance for ${ticket.reference}.`, createdAt: at },
        ] : [], completedTurns: [] })
    }
    errors.push(`Unhandled ${method} ${path}`)
    return json({ message: 'Unknown fixture API' }, 500)
  })
  const assistant = page.getByTestId('customer-support-assistant')
  const frame = page.frameLocator('iframe[data-loomai-workspace-frame]')
  await page.goto(base + 'support')
  await frame.locator('textarea').waitFor({ timeout: 60_000 })
  await page.getByText('Sam Support', { exact: true }).waitFor()
  await page.getByText('Terry Support', { exact: true }).waitFor()
  assert.equal(await page.locator('[data-loomai-support]').count(), 1)
  assert.equal(await page.locator('iframe[data-loomai-workspace-frame]').count(), 1)
  assert.equal(requestCreates().length, 0)
  assert.equal(requestStarts().length, 0)
  assert.equal(calls.some(call => call.path.includes('/cases')), false)
  assert.equal(calls.find(call => call.path.endsWith('/photo')).authorization, `Bearer ${token}`)
  await page.screenshot({ path: `${evidence}/01-support-page.png`, fullPage: true })
  passed('real reusable support pack and protected roster, conversation-first bootstrap with no ticket or start')

  await frame.locator('textarea').fill('How do I connect my website?')
  await frame.locator('textarea').press('Enter')
  await frame.getByText('A chat-first answer without creating a ticket.', { exact: true }).waitFor()
  assert.equal(requestCreates().length, 0)
  passed('chat-first canonical message path does not create a tracked request')

  const iframe = await page.locator('iframe[data-loomai-workspace-frame]').elementHandle()
  const marker = crypto.randomUUID(), draft = 'Keep this unsent question while I inspect my deployment.'
  await iframe.evaluate((node, value) => { node.contentDocument.__fixtureMarker = value; node.contentWindow.MaxMode.attachItem({ type: 'document', data: { id: 'retained-context', title: 'Retained support context', content: 'Synthetic attached context.' } }) }, marker)
  await frame.locator('textarea').fill(draft)
  const retained = async label => {
    assert.equal(await iframe.evaluate((node, value) => node.isConnected && node.contentDocument.__fixtureMarker === value, marker), true, `${label}: original iframe survives`)
    assert.equal(await page.locator('iframe[data-loomai-workspace-frame]').count(), 1)
    assert.equal(await frame.locator('textarea').inputValue(), draft)
    if (await frame.locator('[data-mxw-open-max]').isVisible()) await frame.locator('[data-mxw-open-max]').click()
    await frame.getByText('Retained support context', { exact: false }).first().waitFor()
  }
  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await page.getByRole('heading', { name: 'Customer home', exact: true }).waitFor()
  await frame.locator('.max-mode-companion-dock').waitFor()
  await page.getByRole('heading', { name: 'Customer home', exact: true }).click()
  await page.screenshot({ path: `${evidence}/05-native-support-dock.png`, fullPage: true })
  await retained('Support to Home')
  assert.equal(await assistant.getAttribute('data-presentation'), 'dock')
  await page.getByRole('button', { name: 'Move support to the left', exact: true }).click()
  assert.ok((await page.getByTestId('support-dock-controls').boundingBox()).x < 750)
  await page.getByRole('button', { name: 'Minimize support', exact: true }).click()
  await assistant.waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: 'Open LoomAI support', exact: true }).click()
  await retained('dock move and minimize')
  await page.getByRole('combobox', { name: 'Customer color theme' }).click()
  await page.getByRole('option', { name: 'Dark', exact: true }).click()
  await retained('dark theme')
  await page.getByRole('link', { name: 'Help & requests' }).click()
  await retained('Home to Support')
  await page.screenshot({ path: `${evidence}/02-support-dark.png`, fullPage: true })
  passed('one iframe, unsent draft and attachment survive navigation, left dock, minimize and in-place theme changes')

  await page.getByRole('button', { name: 'Request help', exact: true }).click()
  await page.getByLabel('Subject', { exact: true }).fill('Help with my embedded workspace')
  await page.getByLabel('Details', { exact: true }).fill('The guide is connected but needs a readiness check.')
  await page.getByLabel('Topic', { exact: true }).selectOption('guidance')
  await page.getByRole('button', { name: 'Submit request', exact: true }).click()
  await frame.getByText('Canonical guidance for SUP-2026-1.', { exact: true }).waitFor()
  assert.equal(await frame.getByText('Retained support context', { exact: false }).count(), 0, 'Explicit ticket scope switch clears prior attachments')
  await page.getByRole('button', { name: 'Configure diagnostics', exact: true }).waitFor()
  assert.equal(requestCreates().length, 1); assert.equal(requestStarts().length, 1)
  assert.equal(requestStarts()[0].body.expectedVersion, 'W/"version-0"')
  assert.equal(contexts.get('request-1').consent.granted, false)
  passed('pack form saves and starts once with opaque version and no diagnostic permission in generic intake')

  await page.getByRole('button', { name: 'Configure diagnostics', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Configure diagnostics' })
  const consent = dialog.getByRole('checkbox', { name: /Allow AI and the integration team/ })
  assert.equal(await consent.isChecked(), false)
  await dialog.getByRole('combobox', { name: 'Affected resources (optional)', exact: true }).click()
  await page.getByRole('option', { name: 'Current deployment', exact: true }).waitFor()
  assert.equal(await page.getByRole('option', { name: 'Foreign app', exact: true }).count(), 0)
  assert.equal(await page.getByRole('option', { name: 'Archived deployment', exact: true }).count(), 0)
  assert.equal(await page.getByRole('option', { name: 'App on archived deployment', exact: true }).count(), 0)
  await page.getByRole('option', { name: 'Current deployment', exact: true }).click()
  assert.equal(await consent.isDisabled(), true)
  await dialog.getByRole('button', { name: 'Save resources', exact: true }).click()
  await consent.waitFor()
  await page.waitForFunction(() => !document.querySelector('[role="dialog"] input[type="checkbox"]')?.disabled)
  assert.equal(contexts.get('request-1').consent.granted, false)
  await consent.click()
  await page.waitForFunction(() => document.querySelector('[role="dialog"] input[type="checkbox"]')?.checked)
  await consent.click()
  await page.waitForFunction(() => !document.querySelector('[role="dialog"] input[type="checkbox"]')?.checked)
  assert.equal(contexts.get('request-1').consent.granted, false)
  assert.equal(calls.filter(call => call.path.endsWith('/context/consent')).length, 2)
  await dialog.getByRole('combobox', { name: 'Affected resources (optional)', exact: true }).click()
  await page.getByRole('option', { name: 'Current app', exact: true }).click()
  contextError = 409
  await dialog.getByRole('button', { name: 'Save resources', exact: true }).click()
  await dialog.getByRole('alert').filter({ hasText: 'context changed' }).waitFor()
  assert.equal(calls.filter(call => call.path.endsWith('/context/consent')).length, 2, 'A conflict never retries or regrants permission')
  contextError = 0
  await dialog.getByRole('button', { name: 'Save resources', exact: true }).click()
  await page.waitForFunction(() => !document.querySelector('[role="dialog"] input[type="checkbox"]')?.disabled)
  assert.equal(contexts.get('request-1').consent.granted, false)
  await page.screenshot({ path: `${evidence}/03-resource-permission.png`, fullPage: true })
  await dialog.getByRole('button', { name: 'Close diagnostics', exact: true }).click()
  assert.equal(await iframe.evaluate((node, value) => node.contentDocument.__fixtureMarker === value, marker), true)
  passed('owned resource picker and independent explicit consent grant/revoke preserve pack/frame and never change ticket lifecycle')

  requests.get('request-1').unread = true
  await page.getByRole('button', { name: 'My Requests', exact: true }).click()
  await page.getByRole('button', { name: /SUP-2026-1.*Help with my embedded workspace/ }).click()
  await page.waitForFunction(() => {
    const buttons = document.querySelector('[data-loomai-support]')?.shadowRoot?.querySelectorAll('button') ?? []
    return [...buttons].some(button => button.textContent === 'Resolve request' && !button.disabled)
  })
  const version = requests.get('request-1').version
  await page.getByRole('button', { name: 'Resolve request', exact: true }).click()
  await page.getByRole('button', { name: 'Reopen request', exact: true }).waitFor()
  const resolve = calls.find(call => call.path.endsWith('/resolve'))
  assert.equal(resolve.body.expectedVersion, version)
  assert.equal(controls.get('conversation-1').controlState, 'AI_ACTIVE')
  await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
  await page.getByRole('button', { name: 'Resolve request', exact: true }).waitFor()
  assert.equal(requestStarts().length, 1)
  passed('the pack alone renders My Requests and versioned transitions without replay or changing human authority')

  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await page.getByRole('link', { name: 'Open support page', exact: true }).click()
  await page.waitForURL('**/support/request-1')
  await page.getByRole('button', { name: 'All requests', exact: true }).waitFor()
  await page.reload()
  await page.getByRole('button', { name: 'All requests', exact: true }).waitFor()
  assert.equal(requestStarts().length, 1)
  passed('deep links and reload select pack-owned detail with no start replay')

  token = 'fixture-console-rotated'
  await page.evaluate(value => { window.__fixtureToken = value }, token)
  await page.getByRole('button', { name: 'Configure diagnostics', exact: true }).click()
  await page.getByRole('dialog').getByRole('checkbox').waitFor()
  assert.equal(calls.filter(call => call.path.endsWith('/context')).at(-1).authorization, `Bearer ${token}`)
  await page.getByRole('button', { name: 'Close diagnostics', exact: true }).click()
  await page.getByRole('button', { name: 'Request help', exact: true }).click()
  await page.getByLabel('Subject', { exact: true }).fill('An unconfirmed submission')
  await page.getByLabel('Details', { exact: true }).fill('Retain the same request and command identity.')
  await page.getByLabel('Topic', { exact: true }).selectOption('guidance')
  createError = 503
  await page.getByRole('button', { name: 'Submit request', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'No success has been confirmed.' }).waitFor()
  const uncertain = requestCreates().at(-1)
  createError = 0
  await page.getByRole('button', { name: 'Submit request', exact: true }).click()
  await frame.getByText('Canonical guidance for SUP-2026-2.', { exact: true }).waitFor()
  assert.equal(requestCreates().at(-1).key, uncertain.key)
  assert.deepEqual(requestCreates().at(-1).body, uncertain.body)
  passed('current Console auth and pack-owned uncertain-create idempotency are reused without a host retry engine')

  for (const size of [{ width: 1024, height: 800 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(size)
    await page.getByRole('button', { name: 'Chat', exact: true }).click()
    await frame.locator('textarea').waitFor()
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    const rect = await page.locator('iframe[data-loomai-workspace-frame]').boundingBox()
    assert.ok(rect.width > 100 && rect.x >= 0 && rect.x + rect.width <= size.width + 2)
    assert.ok(rect.y >= 0 && rect.y + rect.height <= size.height + 2, `${size.width}px chat frame fits vertically`)
    const composer = await frame.locator('textarea').boundingBox()
    assert.ok(composer.height > 0 && composer.y >= rect.y && composer.y + composer.height <= size.height + 2, `${size.width}px composer stays inside the viewport`)
    await page.screenshot({ path: `${evidence}/04-support-${size.width}.png`, fullPage: true })
  }
  passed('compact desktop and mobile keep real pack/chat within the Console content bounds')

  await page.getByRole('button', { name: 'Open customer navigation', exact: true }).click()
  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await frame.locator('.max-mode-companion-dock').waitFor()
  await page.locator('#customer-navigation').waitFor({ state: 'hidden' })
  await frame.locator('textarea').focus()
  await frame.locator('[data-mxw-dock-conversation]').waitFor()
  await page.screenshot({ path: `${evidence}/06-native-support-dock-mobile.png`, fullPage: true })
  const mobileInput = await frame.locator('textarea').boundingBox()
  assert.ok(mobileInput.x >= 0 && mobileInput.x + mobileInput.width <= 391)

  const photos = await page.evaluate(() => window.__photoObjectUrls)
  await page.evaluate(() => { window.__signedOut = true; dispatchEvent(new Event('fixture-auth')) })
  await page.locator('iframe[data-loomai-workspace-frame]').waitFor({ state: 'detached' })
  assert.equal(await page.locator('[data-loomai-support]').count(), 0)
  for (const url of photos) assert.equal(await page.evaluate(value => window.__revokedPhotoObjectUrls.includes(value), url), true)
  passed('identity loss destroys the old controller/frame and revokes protected portrait object URLs')

  const staff = await browser.newPage()
  const staffCalls = []
  await staff.route('**/api/**', route => { const path = new URL(route.request().url()).pathname; if (!path.startsWith('/api/')) return route.continue(); staffCalls.push(path); return route.fulfill({ json: [] }) })
  await staff.goto(base + 'staff-fixture')
  await staff.getByRole('heading', { name: 'Staff shell fixture', exact: true }).waitFor()
  assert.equal(await staff.getByTestId('customer-support-assistant').count(), 0)
  assert.equal(staffCalls.some(path => path.startsWith('/api/support/')), false)
  await staff.close()
  passed('staff shell remains separate and makes no customer support calls')
  assert.deepEqual(errors, [])
  await writeFile(`${evidence}/results.json`, JSON.stringify({ synthetic: true, scenarios, counts: { requests: requests.size, creates: requestCreates().length, starts: requestStarts().length }, errors }, null, 2))
  console.log(`Customer Support pack host passed ${scenarios.length} scenario groups. Synthetic BFF/portraits, real pack and engine. Evidence: ${evidence}`)
} catch (error) {
  await page?.screenshot({ path: `${evidence}/failure.png`, fullPage: true }).catch(() => {})
  await writeFile(`${evidence}/failure.json`, JSON.stringify({ synthetic: true, scenarios, errors, calls, failure: error.stack }, null, 2)).catch(() => {})
  console.error(JSON.stringify({ scenarios, errors, recentCalls: calls.slice(-14), failure: error.stack }, null, 2))
  throw error
} finally { await browser.close(); await server.close() }
