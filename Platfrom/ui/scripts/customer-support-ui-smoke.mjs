import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const widget = await readFile(new URL('../../../max-mode-widget/dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const pack = await readFile(new URL('../../../experience-packs/generic-workspace-experience/dist/generic-workspace-experience.iife.js', import.meta.url), 'utf8')
const integrity = value => 'sha384-' + createHash('sha384').update(value).digest('base64')
const calls = [], errors = [], cases = new Map(), createdKeys = new Map()
const controls = new Map()
const scenarios = []
const layoutMeasurements = {}
const teamReference = 'team-' + 'a'.repeat(64)
const frameSelector = 'iframe[title="LoomAI integration support workspace"]'
const evidence = process.env.SUPPORT_UI_OUTPUT || '/private/tmp/loomai-support-ui-01054'
await mkdir(evidence, { recursive: true })
let startFails = false, staffAllowed = false, token = 'fixture-console-token-one'
const authModule = '\0support-auth.js', entry = '\0support-fixture.jsx'
const session = { enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE',
  actorId: 'requester-one', customerId: 'customer-one', customerName: 'Acme Labs', displayName: 'Alex' }
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
  cacheDir: `${evidence}/vite-cache-${process.pid}`, resolve: { dedupe: ['react', 'react-dom'] },
  server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'support-customer-fixture', enforce: 'pre',
    resolveId(id) { if (id === '/support-fixture.jsx') return entry; if (id.endsWith('/auth/PlatformAuthProvider')) return authModule },
    load(id) {
      if (id === authModule) return `export function usePlatformAuth(){ return { session:${JSON.stringify(session)},
        supabaseSession:{user:{id:'supabase-subject-one'}},
        supabase:{auth:{getSession:async()=>{window.__tokenReads=(window.__tokenReads||0)+1;return {data:{session:{access_token:window.__fixtureToken,user:{id:'supabase-subject-one'}}}}}}} } }`
      if (id === entry) return `
        import React from 'react';import{createRoot}from'react-dom/client';
        import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{BrowserRouter,Routes,Route}from'react-router-dom';
        import{CustomerSupportPage}from'/src/pages/CustomerSupportPage.tsx';
        import{CustomerShellLayout}from'/src/layout/CustomerConsoleShell.tsx';
        import{AppShell}from'/src/layout/AppShell.tsx';
        import{DeploymentWorkspaceProvider}from'/src/workspace/DeploymentWorkspaceContext.tsx';
        import{setPlatformAccessTokenProvider}from'/src/api/platformApi.ts';
        window.__fixtureToken ??= ${JSON.stringify(token)};setPlatformAccessTokenProvider(async()=>window.__fixtureToken);
        const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
        const staff=location.pathname==='/staff-fixture';
        const routes=React.createElement(Routes,null,
          React.createElement(Route,{path:'/home',element:React.createElement('h1',null,'Customer home')}),
          React.createElement(Route,{path:'/support',element:React.createElement(CustomerSupportPage)}),
          React.createElement(Route,{path:'/support/requests',element:React.createElement(CustomerSupportPage,{view:'requests'})}),
          React.createElement(Route,{path:'/support/:caseId',element:React.createElement(CustomerSupportPage)}));
        const shell=staff?React.createElement(DeploymentWorkspaceProvider,null,
          React.createElement(AppShell,{session:{...${JSON.stringify(session)},role:'PLATFORM_OPERATOR',customerId:null},onSignOut:async()=>{}},
          React.createElement('h1',null,'Staff shell fixture')))
          :React.createElement(CustomerShellLayout,{session:${JSON.stringify(session)},onSignOut:async()=>{},buildWorkspacePath:p=>p,expandedNavigation:false,supportEnabled:true},routes);
        createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},
          React.createElement(BrowserRouter,null,shell)));
      `
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      const pathname = new URL(req.url, 'http://fixture.local').pathname
      if (pathname !== '/home' && pathname !== '/staff-fixture' && pathname !== '/support' && !pathname.startsWith('/support/')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1" /></head><body style="margin:0"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/support-fixture.jsx"></script></body></html>')
    }) },
  }],
})
await server.listen()
const base = server.resolvedUrls.local[0]
const browser = await chromium.launch({ headless: true })
let page
try {
  page = await browser.newPage({ viewport: { width: 1500, height: 1050 } })
  page.setDefaultTimeout(15_000)
  page.on('pageerror', error => errors.push(error.stack || error.message))
  // Synthetic JPEG fixture only: no live agent profile or image is read or changed.
  const photo = Buffer.from(await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = 8
    const context = canvas.getContext('2d'); context.fillStyle = '#387c83'; context.fillRect(0, 0, 8, 8)
    return canvas.toDataURL('image/jpeg').split(',')[1]
  }), 'base64')
  await page.addInitScript(() => {
    window.__photoObjectUrls = []; window.__revokedPhotoObjectUrls = []
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = blob => {
      const url = create(blob)
      if (blob.type === 'image/jpeg') window.__photoObjectUrls.push(url)
      return url
    }
    URL.revokeObjectURL = url => { window.__revokedPhotoObjectUrls.push(url); revoke(url) }
  })
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    if (!path.startsWith('/api/')) return route.continue()
    if (path === '/api/public/ai-workspace/assets/widget.js' || path === '/api/public/ai-workspace/assets/pack.js') {
      return route.fulfill({ contentType: 'text/javascript', body: path.endsWith('widget.js') ? widget : pack })
    }
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method, body, key: request.headers()['idempotency-key'], authorization: request.headers().authorization })
    const json = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    if (request.headers().authorization !== `Bearer ${token}`) return json({ message: 'Console authentication required' }, 401)
    if (path === '/api/support/team') return json({ agents: [
      { agentReference: teamReference, displayName: 'Sam Support', photoAvailable: true },
      { agentReference: 'team-' + 'b'.repeat(64), displayName: 'Terry Support', photoAvailable: false },
    ] })
    if (path === `/api/support/team/${teamReference}/photo`) return route.fulfill({
      contentType: 'image/jpeg', headers: { 'cache-control': 'private, no-store', 'x-content-type-options': 'nosniff' }, body: photo,
    })
    if (path === '/api/support/staff/intake') return staffAllowed ? json([]) : json({ message: 'SUPPORT permission required' }, 403)
    if (path === '/api/customer-self-service/deployments') return json([])
    if (path === '/api/platform/customers/customer-one/ai-workspace-installations') return json([])
    if (path === '/api/support/cases') {
      if (method === 'GET') return json([...cases.values()])
      const operationKey = request.headers()['idempotency-key']
      if (createdKeys.has(operationKey)) return json(cases.get(createdKeys.get(operationKey)))
      const number = cases.size + 1, id = `case-${number}`, at = '2026-10-30T12:00:00Z'
      const value = { id, reference: `SUP-2026-${number}`, summary: body.summary, details: body.details, topic: body.topic,
        resources: body.resources, status: 'OPEN', consent: { granted: body.diagnosticConsent, version: 0, changedAt: at, revokedAt: null },
        revision: 0, initialMessageDelivery: 'PENDING', deliveryObservedAt: null,
        episodes: [{ id: `episode-${number}`, number: 1, conversationId: `conversation-${number}`, terminal: false, createdAt: at }],
        createdAt: at, updatedAt: at, closedAt: null }
      cases.set(id, value); createdKeys.set(operationKey, id)
      return json(value)
    }
    const match = path.match(/^\/api\/support\/cases\/([^/]+)(.*)$/)
    if (match) {
      const [, id, operation] = match, supportCase = cases.get(id)
      if (!supportCase) return json({ message: 'Request not available to this requester' }, 403)
      const conversationId = supportCase.episodes.at(-1).conversationId
      if (!operation && method === 'GET') return json(supportCase)
      if (operation === '/consent') {
        assert.equal(body.expectedVersion, supportCase.consent.version)
        supportCase.consent.granted = body.granted; supportCase.consent.version++
        return json(supportCase)
      }
      if (['/resolve', '/cancel', '/reopen'].includes(operation)) {
        assert.equal(body.expectedRevision, supportCase.revision)
        if (operation === '/reopen' && supportCase.episodes.at(-1).terminal) {
          const number = supportCase.episodes.length + 1
          supportCase.episodes.push({ id: `${id}-episode-${number}`, number, conversationId: `${id}-conversation-${number}`, terminal: false, createdAt: supportCase.updatedAt })
          controls.delete(id)
        }
        supportCase.status = operation === '/resolve' ? 'RESOLVED' : operation === '/cancel' ? 'CANCELLED' : 'OPEN'
        supportCase.revision++
        return json(supportCase)
      }
      if (operation === '/start') {
        supportCase.initialMessageDelivery = startFails ? 'UNKNOWN' : 'COMPLETED'
        if (startFails) return json({ message: 'Synthetic runtime outage; case remains saved' }, 503)
        return json({ supportCase, runtimeStatus: 200, response: { success: true } })
      }
      if (operation === '/workspace') return json({
        conversationId, principalNamespace: `support:${id}:episode-1`, apiBaseUrl: `/api/support/cases/${id}/runtime/api`,
        installationId: 'awi-real-fixture', assignmentRevision: 'verified-assignment-one',
        workspaceAssetUrl: '/api/public/ai-workspace/assets/widget.js', workspaceIntegrity: integrity(widget),
        experienceModuleUrl: '/api/public/ai-workspace/assets/pack.js', experienceIntegrity: integrity(pack),
        reference: supportCase.reference, status: supportCase.status, initialMessageDelivery: supportCase.initialMessageDelivery,
        configuration: { app: { assistantLabel: 'LoomAI Support' }, copy: { starterSuggestions: [] }, capabilities: { currentPage: false }, toolRail: { items: [] } },
        conversationHandover: { enabled: true, userCanCancelPending: true, userCanResumeAi: true },
      })
      if (operation.startsWith('/runtime/api/chat/me/conversations/')) {
        const item = `/runtime/api/chat/me/conversations/${conversationId}`
        const control = controls.get(id) || { conversationId, controlState: 'AI_ACTIVE', controlVersion: 1, messages: [] }
        controls.set(id, control)
        if (operation === item + '/control' && method === 'GET') {
          if (control.controlState === 'CLOSED') supportCase.episodes.at(-1).terminal = true
          return json(control)
        }
        if (operation === item + '/events' && method === 'GET') return route.fulfill({ contentType: 'text/event-stream', body: ': connected\n\n' })
        if (operation === item + '/handover-requests' && method === 'POST') {
          control.controlState = 'HUMAN_ACTIVE'; control.controlVersion++; control.handoverId = 'handover-one'
          return json({ outcome: 'HANDOVER_REQUESTED', conversation: control })
        }
        if (operation === item + '/messages' && method === 'POST') {
          assert.equal(control.controlState, 'HUMAN_ACTIVE', 'The explicit follow-up belongs to the human channel')
          return json({ success: true, type: 'HUMAN_MESSAGE_ACCEPTED', conversationId }, 202)
        }
        if (operation === item + '/ai-resume-requests' && method === 'POST') {
          control.controlState = 'AI_ACTIVE'; control.controlVersion++
          return json(control)
        }
        assert.equal(method, 'GET', 'Only configured explicit conversation operations may write')
        assert.equal(operation, item)
        if (supportCase.episodes.length > 1) return json({ id: conversationId, status: 'ACTIVE', controlState: control.controlState,
          controlVersion: control.controlVersion, participantMessages: [], completedTurns: [] })
        return json({ id: conversationId, status: 'ACTIVE', controlState: control.controlState, controlVersion: control.controlVersion,
          participantMessages: [
            { messageId: `${id}-user`, role: 'USER', sequence: 1, content: `Integration support request: ${supportCase.summary}`, createdAt: supportCase.createdAt },
            { messageId: `${id}-ai`, role: 'AI', sequence: 2, content: `Canonical guidance for ${supportCase.reference}.`, createdAt: supportCase.createdAt },
          ], completedTurns: [{ userMessageId: `${id}-user`, aiMessageId: `${id}-ai`, completedAt: supportCase.createdAt,
            response: { type: 'INFORMATION_PROVIDED', success: true, answer: `Canonical guidance for ${supportCase.reference}.`,
              sources: [{ id: 'guide', title: 'Engage integration guide', content: 'Approved setup documentation.', type: 'document' }] } }],
        })
      }
    }
    errors.push(`Unhandled ${method} ${path}`)
    return json({ message: 'Unknown fixture API' }, 500)
  })

  const writes = () => calls.filter(call => !['GET', 'HEAD', 'OPTIONS'].includes(call.method))
  const starts = id => calls.filter(call => call.path === `/api/support/cases/${id}/start`)
  const creates = () => calls.filter(call => call.method === 'POST' && call.path === '/api/support/cases')
  const passed = name => { scenarios.push(name); console.log(`PASS ${name}`) }
  const reopenDock = async () => {
    const launcher = page.getByRole('button', { name: 'Open LoomAI support', exact: true })
    const pointerReachable = await launcher.evaluate(node => {
      const box = node.getBoundingClientRect()
      const target = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
      return node === target || node.contains(target)
    })
    assert.equal(pointerReachable, true, 'Minimized support must be pointer-accessible, not covered by the navigation drawer')
    await launcher.click()
  }
  const assistant = page.getByTestId('customer-support-assistant')
  const frame = page.frameLocator(frameSelector)
  const rotateToken = async value => {
    token = value
    await page.addInitScript(value => { window.__fixtureToken = value }, token)
    await page.evaluate(value => { window.__fixtureToken = value }, token)
  }
  const waitForGuidance = async number => {
    await frame.getByText(`Canonical guidance for SUP-2026-${number}.`, { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Refresh support conversation', exact: true }).waitFor({ state: 'visible' })
    await page.waitForFunction(() => !document.querySelector('button[aria-label="Refresh support conversation"]')?.disabled)
  }
  const openDetails = async () => {
    await page.getByRole('button', { name: 'Request details', exact: true }).click()
    await page.getByRole('heading', { name: 'Request details', exact: true }).waitFor()
  }
  const closeDetails = async () => {
    await page.getByRole('button', { name: 'Close request details', exact: true }).click()
    await page.getByRole('heading', { name: 'Request details', exact: true }).waitFor({ state: 'hidden' })
  }
  const requestForm = async () => {
    await page.getByRole('button', { name: 'Request integration help', exact: true }).click()
    const form = page.getByRole('dialog')
    await form.getByRole('heading', { name: 'Request integration help', exact: true }).waitFor()
    return form
  }

  await page.goto(base + 'support')
  await page.getByRole('heading', { name: 'Integration support', exact: true }).waitFor()
  await page.getByRole('link', { name: 'Help & requests' }).waitFor()
  await page.getByText('Sam Support', { exact: true }).waitFor()
  await page.getByText('Terry Support', { exact: true }).waitFor()
  await page.waitForFunction(() => {
    const image = document.querySelector('img[alt="Sam Support"]')
    return image?.src.startsWith('blob:') && image.complete && image.naturalWidth === 8
  })
  const firstPhotoUrl = await page.getByRole('img', { name: 'Sam Support', exact: true }).getAttribute('src')
  assert.equal(calls.filter(call => call.path.endsWith('/photo')).length, 1, 'No-photo agents must not trigger a photo read')
  assert.equal(calls.find(call => call.path.endsWith('/photo')).authorization, `Bearer ${token}`)
  assert.equal(await page.getByText('Online', { exact: true }).count(), 0, 'The roster must not invent online status')
  assert.equal(await page.getByRole('link', { name: 'Team intake', exact: true }).count(), 0)
  await page.getByRole('textbox', { name: 'Ask LoomAI support', exact: true }).waitFor()
  assert.equal(await page.getByRole('textbox', { name: 'Ask LoomAI support', exact: true }).inputValue(), '')
  assert.equal(await page.getByRole('button', { name: 'Send support question', exact: true }).isDisabled(), true)
  assert.equal(await page.locator(frameSelector).count(), 0, 'Empty support must not create a runtime conversation on mount')
  assert.equal(writes().length, 0, 'Entering Support must perform no writes')
  await page.screenshot({ path: `${evidence}/01-empty-support-team.png`, fullPage: true })
  passed('empty support is ready, assigned roster and protected test-only JPEG render, no mount writes')

  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await page.getByRole('heading', { name: 'Customer home', exact: true }).waitFor()
  assert.equal(await assistant.getAttribute('data-presentation'), 'dock')
  await page.waitForFunction(url => window.__revokedPhotoObjectUrls.includes(url), firstPhotoUrl)
  assert.equal(writes().length, 0, 'Entering Home must not create or start a support request')
  await rotateToken('fixture-console-token-two')
  await page.getByRole('link', { name: 'Help & requests' }).click()
  await page.getByRole('img', { name: 'Sam Support', exact: true }).waitFor()
  await page.waitForFunction(() => document.querySelector('img[alt="Sam Support"]')?.naturalWidth === 8)
  const rosterRefreshed = page.waitForResponse(response => new URL(response.url()).pathname === '/api/support/team')
  await page.getByRole('button', { name: 'Refresh support team', exact: true }).click()
  await rosterRefreshed
  assert.equal(calls.filter(call => call.path === '/api/support/team').at(-1).authorization, `Bearer ${token}`)
  assert.equal(calls.filter(call => call.path.endsWith('/photo')).at(-1).authorization, `Bearer ${token}`, 'Protected photo fetch must obtain the current bearer')
  assert.equal(writes().length, 0)
  passed('Support/Home navigation is read-only; photo URLs revoke and roster/photo reads use fresh bearer')

  await page.getByRole('textbox', { name: 'Ask LoomAI support', exact: true }).fill('How do I connect my website to Engage?')
  await page.getByRole('button', { name: 'Send support question', exact: true }).click()
  await page.waitForURL('**/support/case-1')
  await waitForGuidance(1)
  assert.equal(creates().length, 1)
  assert.equal(starts('case-1').length, 1)
  assert.equal(creates()[0].body.summary, 'How do I connect my website to Engage?')
  assert.deepEqual(creates()[0].body.resources, [])
  assert.equal(creates()[0].body.diagnosticConsent, false)
  assert.ok(creates()[0].key)
  assert.equal(await frame.getByText('Integration support request: How do I connect my website to Engage?', { exact: true }).count(), 1)
  passed('first explicit chat message creates one durable request and starts its initial turn once')

  const iframe = await page.locator(frameSelector).elementHandle()
  const documentMarker = 'synthetic-document-' + crypto.randomUUID()
  await iframe.evaluate((node, marker) => { node.contentDocument.__supportSmokeIdentity = marker }, documentMarker)
  const draft = 'Keep this unsent integration question while I inspect my deployment.'
  await frame.locator('textarea').fill(draft)
  const sameDocumentAndDraft = async label => {
    assert.equal(await page.locator(frameSelector).count(), 1, `${label}: one iframe only`)
    assert.equal(await iframe.evaluate((node, marker) => node.isConnected && node.contentDocument.__supportSmokeIdentity === marker, documentMarker), true,
      `${label}: the original iframe node and document must survive`)
    assert.equal(await frame.locator('textarea').inputValue(), draft, `${label}: unsent text must survive`)
  }
  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await page.getByRole('heading', { name: 'Customer home', exact: true }).waitFor()
  await sameDocumentAndDraft('Support to Home')
  const rightDock = await assistant.boundingBox()
  assert.ok(rightDock.x > 750 && rightDock.width <= 440, 'The default dock must fit on the right')
  await page.getByRole('button', { name: 'Move support to the left', exact: true }).click()
  assert.ok((await assistant.boundingBox()).x < 750)
  await sameDocumentAndDraft('move dock left')
  await page.getByRole('button', { name: 'Minimize support', exact: true }).click()
  await assistant.waitFor({ state: 'hidden' })
  assert.equal(await page.locator(frameSelector).count(), 1, 'Minimize must hide, not destroy, the conversation')
  assert.ok((await page.getByRole('button', { name: 'Open LoomAI support', exact: true }).boundingBox()).x < 750)
  await reopenDock()
  await sameDocumentAndDraft('minimize and reopen')
  await page.getByRole('button', { name: 'Move support to the right', exact: true }).click()
  assert.ok((await assistant.boundingBox()).x > 750)
  await page.getByRole('button', { name: 'Move support to the left', exact: true }).click()
  await page.getByRole('link', { name: 'Help & requests' }).click()
  await page.getByRole('heading', { name: 'Integration support', exact: true }).waitFor()
  await sameDocumentAndDraft('Home back to Support')
  assert.equal(await assistant.getAttribute('data-presentation'), 'page')
  assert.equal(starts('case-1').length, 1)
  passed('same iframe document and draft survive Support/Home/Support, minimize/reopen and both dock sides')

  await page.getByRole('link', { name: 'Home', exact: true }).click()
  await page.getByRole('button', { name: 'Minimize support', exact: true }).click()
  await page.reload()
  await page.getByRole('heading', { name: 'Customer home', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Open LoomAI support', exact: true }).waitFor()
  assert.ok((await page.getByRole('button', { name: 'Open LoomAI support', exact: true }).boundingBox()).x < 750)
  await assistant.waitFor({ state: 'hidden' })
  await reopenDock()
  await waitForGuidance(1)
  assert.ok((await assistant.boundingBox()).x < 750)
  await page.getByRole('link', { name: 'Help & requests' }).click()
  await waitForGuidance(1)
  assert.equal(starts('case-1').length, 1, 'Reload must restore preferences without replaying the initial turn')
  passed('dock side/minimized preferences persist after reload without intake replay')

  const form = await requestForm()
  assert.equal(await page.locator(frameSelector).count(), 1, 'The request form overlays rather than replaces the chat')
  await form.getByRole('textbox', { name: 'Summary', exact: true }).fill('Help with my embedded workspace')
  const consent = form.getByRole('checkbox', { name: /Allow AI and the integration team/ })
  assert.equal(await consent.isChecked(), false, 'Status-check consent must be unchecked by default')
  await form.getByRole('textbox', { name: 'Details (optional)', exact: true }).fill('I need to connect the workspace on my website.')
  await page.screenshot({ path: `${evidence}/02-request-help-modal.png`, fullPage: true })
  await form.getByRole('button', { name: 'Submit request', exact: true }).click()
  await page.waitForURL('**/support/case-2')
  await waitForGuidance(2)
  assert.equal(creates().length, 2)
  assert.equal(starts('case-2').length, 1)
  assert.equal(creates()[1].body.diagnosticConsent, false)
  assert.ok(creates()[1].key)
  assert.equal(await frame.getByText('Integration support request: Help with my embedded workspace', { exact: true }).count(), 1)
  assert.notEqual(await frame.getByText('Canonical guidance for SUP-2026-2.', { exact: true }).evaluate(node => getComputedStyle(node).color), 'rgb(0, 0, 0)', 'The dark iframe must retain readable theme text')
  assert.equal(await page.locator('body').innerText().then(text => text.includes(token)), false, 'Console token must not appear in visible UI')
  const box = await page.locator(frameSelector).boundingBox(), container = await assistant.boundingBox()
  assert.ok(box.width <= container.width && box.height > 250 && box.x >= 205, 'The inline conversation must fit its customer content column')
  await frame.getByRole('button', { name: 'Hide', exact: true }).click()
  await frame.getByText('Context Panel', { exact: true }).waitFor({ state: 'hidden' })
  await page.evaluate(() => window.scrollTo(0, 0))
  const composer = await frame.locator('[data-max-mode-composer-input-shell]').boundingBox()
  const input = await frame.locator('textarea').boundingBox()
  const viewport = page.viewportSize()
  assert.ok(composer && input, 'Selected-case composer and input must render')
  layoutMeasurements.selectedCase = { viewportHeight: viewport.height, composerBottom: composer.y + composer.height, inputBottom: input.y + input.height }
  assert.ok(composer.y >= 0 && composer.y + composer.height <= viewport.height + 2,
    `Selected-case composer must fit the desktop viewport without page scrolling: ${JSON.stringify(layoutMeasurements.selectedCase)}`)
  assert.ok(input.y >= 0 && input.y + input.height <= viewport.height + 2, 'The customer message input must remain within the desktop viewport')
  await page.screenshot({ path: `${evidence}/03-inline-request-workspace.png`, fullPage: true })
  await page.screenshot({ path: `${evidence}/05-desktop-composer-viewport.png` })
  passed('modal request create/save/start once, default denied diagnostics and canonical readable iframe history')
  passed('selected-case composer and message input fit the desktop viewport at scroll top')

  const tokenReads = await page.evaluate(() => window.__tokenReads)
  await page.evaluate(() => window.postMessage({ type: 'loomai:support:token-request', requestId: 'spoof', nonce: 'spoof' }, location.origin))
  await page.waitForTimeout(50)
  assert.equal(await page.evaluate(() => window.__tokenReads), tokenReads, 'Wrong window messages must not obtain identity')
  await rotateToken('fixture-console-token-three')
  const refreshed = page.waitForResponse(response => response.url().includes('/runtime/api/chat/me/conversations/'))
  await page.getByRole('button', { name: 'Refresh support conversation', exact: true }).click()
  await refreshed
  assert.ok(calls.some(call => call.path.includes('/runtime/') && call.authorization === `Bearer ${token}`), 'Each conversation request must use a fresh Console token')
  await page.waitForFunction(() => !document.querySelector('button[aria-label="Refresh support conversation"]')?.disabled)
  passed('iframe bearer bridge obtains fresh identity and rejects wrong-source messages')

  const connect = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/handover-requests'))
  await frame.getByRole('button', { name: 'Talk to a person', exact: true }).click()
  await connect
  await assistant.getByText(/SUP-2026-2.*Human support active/).waitFor()
  await frame.locator('textarea').fill('Please help me confirm the website setup.')
  const humanMessage = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/messages'))
  await frame.locator('textarea').press('Enter')
  assert.equal((await humanMessage).status(), 202)
  await frame.getByRole('button', { name: 'Human support', exact: true }).click()
  await frame.getByRole('dialog').getByRole('button', { name: 'Continue with AI', exact: true }).click()
  await assistant.getByText(/SUP-2026-2.*AI assisting/).waitFor()
  passed('explicit human handover, human-channel message and authorized return to AI')

  await openDetails()
  await page.getByRole('checkbox', { name: /Allow AI and the integration team/ }).click()
  await page.waitForFunction(() => document.querySelector('input[type="checkbox"]')?.checked)
  await page.getByRole('checkbox', { name: /Allow AI and the integration team/ }).click()
  await page.waitForFunction(() => !document.querySelector('input[type="checkbox"]')?.checked)
  assert.equal(cases.get('case-2').consent.version, 2)
  assert.equal(cases.get('case-2').consent.granted, false)
  await closeDetails()
  await page.getByRole('button', { name: 'Resolve', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Resolve request', exact: true }).click()
  await page.getByRole('button', { name: 'Reopen request', exact: true }).waitFor()
  await frame.locator('textarea').waitFor()
  assert.equal(await frame.locator('textarea').isDisabled(), true)
  await assistant.getByText('SUP-2026-2 · Resolved request', { exact: true }).waitFor()
  assert.equal(controls.get('case-2').controlState, 'AI_ACTIVE', 'Resolved caption must override the runtime AI state')
  await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
  await page.getByRole('button', { name: 'Resolve', exact: true }).waitFor()
  await openDetails()
  await page.getByRole('button', { name: 'Cancel request', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel request', exact: true }).click()
  await page.getByRole('button', { name: 'Close request details', exact: true }).click()
  await assistant.getByText('SUP-2026-2 · Cancelled request', { exact: true }).waitFor()
  assert.equal(controls.get('case-2').controlState, 'AI_ACTIVE', 'Cancelled caption must override the runtime AI state')
  await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
  await page.reload()
  await waitForGuidance(2)
  assert.equal(starts('case-2').length, 1, 'Reload/reopen must not resend initial intake')
  passed('Request details consent grant/revoke, resolve/cancel/reopen and no replay on reload')

  controls.get('case-2').controlState = 'CLOSED'
  await page.getByRole('button', { name: 'Refresh support conversation', exact: true }).click()
  await page.getByRole('button', { name: 'Start new conversation', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Start new conversation', exact: true }).click()
  await frame.locator('textarea:not([disabled])').waitFor()
  assert.equal(cases.get('case-2').episodes.length, 2)
  assert.equal(await frame.getByText('Canonical guidance for SUP-2026-2.', { exact: true }).count(), 0)
  assert.equal(starts('case-2').length, 1, 'New episode must not replay the original form')
  passed('terminal conversation creates a fresh empty episode without intake replay')

  startFails = true
  const outageForm = await requestForm()
  await outageForm.getByRole('textbox', { name: 'Summary', exact: true }).fill('A saved request with runtime unavailable')
  await outageForm.getByRole('button', { name: 'Submit request', exact: true }).click()
  await page.waitForURL('**/support/case-3')
  await page.getByRole('alert').filter({ hasText: 'Initial delivery is unconfirmed' }).waitFor()
  await page.getByText('SUP-2026-3', { exact: true }).waitFor()
  assert.equal(cases.size, 3, 'AI outage must not erase durable intake')
  assert.equal(starts('case-3').length, 1)
  await page.reload()
  await page.getByText('SUP-2026-3', { exact: true }).waitFor()
  await page.getByRole('alert').filter({ hasText: 'Initial delivery is unconfirmed' }).waitFor()
  assert.equal(starts('case-3').length, 1, 'Uncertain delivery must not retry on mount')
  passed('runtime outage retains the case and uncertain delivery never retries on mount')

  await page.getByRole('link', { name: 'My requests', exact: true }).click()
  await page.waitForURL('**/support/requests')
  await page.getByRole('heading', { name: 'My requests', exact: true }).waitFor()
  await page.getByRole('link', { name: /A saved request with runtime unavailable/ }).waitFor()
  await page.getByRole('link', { name: /A saved request with runtime unavailable/ }).click()
  await page.waitForURL('**/support/case-3')
  assert.equal(creates().length, 3)
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 3)
  passed('My requests uses /support/requests and reopens the selected request without writes')

  await page.setViewportSize({ width: 430, height: 900 })
  await page.getByRole('button', { name: 'Open customer navigation', exact: true }).waitFor()
  await page.screenshot({ path: `${evidence}/04-basic-narrow-navigation.png`, fullPage: true })
  staffAllowed = true
  await page.goto(base + 'support')
  await page.getByRole('link', { name: 'Team intake', exact: true }).waitFor()
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 3, 'Staff access discovery must not start any conversation')
  passed('basic narrow navigation and separate authorized team-intake discovery remain available')

  const staffPage = await browser.newPage({ viewport: { width: 1500, height: 1050 } })
  const staffCalls = []
  try {
    staffPage.setDefaultTimeout(15_000)
    staffPage.on('pageerror', error => errors.push(error.message))
    await staffPage.route('**/*', route => {
      const path = new URL(route.request().url()).pathname
      if (!path.startsWith('/api/')) return route.continue()
      staffCalls.push(path)
      return route.fulfill({ contentType: 'application/json', body: '[]' })
    })
    await staffPage.goto(base + 'staff-fixture')
    await staffPage.getByRole('heading', { name: 'Staff shell fixture', exact: true }).waitFor()
    assert.equal(await staffPage.getByTestId('customer-support-assistant').count(), 0)
    assert.equal(await staffPage.locator(frameSelector).count(), 0)
    assert.equal(await staffPage.getByRole('button', { name: 'Open LoomAI support', exact: true }).count(), 0)
    assert.equal(staffCalls.filter(path => path.startsWith('/api/support/')).length, 0, 'Staff shell must not fetch customer support data')
  } finally { await staffPage.close() }
  passed('real staff shell exposes neither customer assistant nor support API traffic')
  assert.deepEqual(errors, [])
  await writeFile(`${evidence}/results.json`, JSON.stringify({ synthetic: true, scenarios, layoutMeasurements, counts: { created: creates().length,
    started: calls.filter(call => call.path.endsWith('/start')).length, privatePhotoReads: calls.filter(call => call.path.endsWith('/photo')).length }, errors }, null, 2))
  console.log(`Customer support UI smoke passed ${scenarios.length} scenario groups using real UI and explicitly synthetic API/JPEG fixtures. Screenshots: ${evidence}`)
} catch (error) {
  await page?.screenshot({ path: `${evidence}/failure.png`, fullPage: true }).catch(() => {})
  await writeFile(`${evidence}/failure.json`, JSON.stringify({ synthetic: true, passed: scenarios, errors, calls, failure: error.stack }, null, 2)).catch(() => {})
  console.error(JSON.stringify({ passed: scenarios, errors, recentCalls: calls.slice(-12), failure: error.stack }, null, 2))
  throw error
} finally {
  await browser.close()
  await server.close()
}
