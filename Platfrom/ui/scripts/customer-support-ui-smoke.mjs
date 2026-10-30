import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile, mkdir } from 'node:fs/promises'
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const widget = await readFile(new URL('../../../max-mode-widget/dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const pack = await readFile(new URL('../../../experience-packs/generic-workspace-experience/dist/generic-workspace-experience.iife.js', import.meta.url), 'utf8')
const integrity = value => 'sha384-' + createHash('sha384').update(value).digest('base64')
const calls = [], errors = [], cases = new Map(), createdKeys = new Map()
const controls = new Map()
const evidence = process.env.SUPPORT_UI_OUTPUT || '/private/tmp/loomai-support-ui-01054'
await mkdir(evidence, { recursive: true })
let startFails = false, staffAllowed = false, token = 'fixture-console-token-one'
const authModule = '\0support-auth.js', entry = '\0support-fixture.jsx'
const session = { enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE',
  actorId: 'requester-one', customerId: 'customer-one', customerName: 'Acme Labs', displayName: 'Alex' }
const server = await createServer({ root, configFile: false, envFile: false, logLevel: 'error', esbuild: { jsx: 'automatic' },
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
        import{setPlatformAccessTokenProvider}from'/src/api/platformApi.ts';
        window.__fixtureToken ??= ${JSON.stringify(token)};setPlatformAccessTokenProvider(async()=>window.__fixtureToken);
        const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
        createRoot(document.getElementById('root')).render(React.createElement(QueryClientProvider,{client},
          React.createElement(BrowserRouter,null,React.createElement(CustomerShellLayout,{session:${JSON.stringify(session)},onSignOut:async()=>{},buildWorkspacePath:p=>p,expandedNavigation:false},
          React.createElement(Routes,null,...['/support','/support/:caseId'].map(path=>React.createElement(Route,{key:path,path,element:React.createElement(CustomerSupportPage)})))))));
      `
    },
    configureServer(vite) { vite.middlewares.use((req, res, next) => {
      const pathname = new URL(req.url, 'http://fixture.local').pathname
      if (pathname !== '/support' && !pathname.startsWith('/support/')) return next()
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
  page.on('pageerror', error => errors.push(error.message))
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

  await page.goto(base + 'support')
  await page.getByRole('heading', { name: 'Integration support', exact: true }).waitFor()
  await page.getByRole('link', { name: 'Help & requests' }).waitFor()
  assert.equal(await page.getByRole('link', { name: 'Team intake', exact: true }).count(), 0)
  await page.getByRole('button', { name: 'New request', exact: true }).click()
  const summary = page.getByRole('textbox', { name: 'Summary', exact: true })
  await summary.fill('Help with my embedded workspace')
  const consent = page.getByRole('checkbox', { name: /Allow AI and the integration team/ })
  assert.equal(await consent.isChecked(), false, 'Status-check consent must be unchecked by default')
  await page.getByRole('textbox', { name: 'Details (optional)', exact: true }).fill('I need to connect the workspace on my website.')
  await page.screenshot({ path: `${evidence}/01-request-form.png`, fullPage: true })
  await page.getByRole('button', { name: 'Submit request', exact: true }).click()
  await page.waitForURL('**/support/case-1')
  const frame = page.frameLocator('iframe[title="LoomAI integration support workspace"]')
  await frame.getByText('Canonical guidance for SUP-2026-1.', { exact: true }).waitFor()
  assert.notEqual(await frame.getByText('Canonical guidance for SUP-2026-1.', { exact: true }).evaluate(node => getComputedStyle(node).color), 'rgb(0, 0, 0)', 'The dark iframe must retain readable theme text')
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 1)
  const created = calls.find(call => call.method === 'POST' && call.path === '/api/support/cases')
  assert.equal(created.body.diagnosticConsent, false)
  assert.ok(created.key)
  assert.equal(await frame.getByText('Integration support request: Help with my embedded workspace', { exact: true }).count(), 1)
  assert.equal(await page.locator('body').innerText().then(text => text.includes(token)), false, 'Console token must not appear in visible UI')
  const box = await page.locator('iframe').boundingBox()
  assert.ok(box.width < 1100 && box.height >= 460 && box.x > 205, 'The widget must remain inside the case column')
  await frame.getByRole('button', { name: 'Hide', exact: true }).waitFor()
  await frame.getByRole('button', { name: 'Hide', exact: true }).click()
  await frame.getByText('Context Panel', { exact: true }).waitFor({ state: 'hidden' })
  await page.screenshot({ path: `${evidence}/02-case-workspace.png`, fullPage: true })

  const tokenReads = await page.evaluate(() => window.__tokenReads)
  await page.evaluate(() => window.postMessage({ type: 'loomai:support:token-request', requestId: 'spoof', nonce: 'spoof' }, location.origin))
  assert.equal(await page.evaluate(() => window.__tokenReads), tokenReads, 'Wrong window messages must not obtain identity')
  token = 'fixture-console-token-two'
  await page.addInitScript(value => { window.__fixtureToken = value }, token)
  await page.evaluate(value => { window.__fixtureToken = value }, token)
  const refreshed = page.waitForResponse(response => response.url().includes('/runtime/api/chat/me/conversations/'))
  await page.getByRole('button', { name: 'Refresh support conversation', exact: true }).click()
  await refreshed
  assert.ok(calls.some(call => call.path.includes('/runtime/') && call.authorization === `Bearer ${token}`), 'Each request must use a fresh Console token')
  await page.waitForFunction(() => !document.querySelector('button[aria-label="Refresh support conversation"]')?.disabled)
  const connect = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/handover-requests'))
  await frame.getByRole('button', { name: 'Talk to a person', exact: true }).click()
  await connect
  await page.getByText('Human support active', { exact: true }).waitFor()
  await frame.locator('textarea').fill('Please help me confirm the website setup.')
  const humanMessage = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/messages'))
  await frame.locator('textarea').press('Enter')
  assert.equal((await humanMessage).status(), 202)
  await frame.getByRole('button', { name: 'Human support', exact: true }).click()
  await frame.getByRole('dialog').getByRole('button', { name: 'Continue with AI', exact: true }).click()
  await page.getByText('AI assisting', { exact: true }).waitFor()

  await page.getByRole('checkbox', { name: /Allow AI and the integration team/ }).click()
  await page.waitForFunction(() => document.querySelector('input[type="checkbox"]')?.checked)
  await page.getByRole('checkbox', { name: /Allow AI and the integration team/ }).click()
  await page.waitForFunction(() => !document.querySelector('input[type="checkbox"]')?.checked)
  await page.getByRole('button', { name: 'Resolve', exact: true }).click()
  await page.getByRole('button', { name: 'Resolve request', exact: true }).click()
  await page.getByRole('button', { name: 'Reopen request', exact: true }).waitFor()
  await frame.locator('textarea').waitFor()
  assert.equal(await frame.locator('textarea').isDisabled(), true)
  await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
  await page.getByRole('button', { name: 'Resolve', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Cancel request', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel request', exact: true }).click()
  await page.getByRole('button', { name: 'Reopen request', exact: true }).click()
  await page.reload()
  await frame.getByText('Canonical guidance for SUP-2026-1.', { exact: true }).waitFor()
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 1, 'Reload/reopen must not resend initial intake')

  controls.get('case-1').controlState = 'CLOSED'
  await page.getByRole('button', { name: 'Refresh support conversation', exact: true }).click()
  await page.getByRole('button', { name: 'Start new conversation', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Start new conversation', exact: true }).click()
  await frame.locator('textarea:not([disabled])').waitFor()
  assert.equal(cases.get('case-1').episodes.length, 2)
  assert.equal(await frame.getByText('Canonical guidance for SUP-2026-1.', { exact: true }).count(), 0)
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 1, 'New episode must not replay the original form')

  startFails = true
  await page.getByRole('button', { name: 'New support request', exact: true }).click()
  await summary.fill('A second request with runtime unavailable')
  await page.getByRole('button', { name: 'Submit request', exact: true }).click()
  await page.waitForURL('**/support/case-2')
  await page.getByRole('alert').filter({ hasText: 'Initial conversation delivery returned' }).waitFor()
  await page.getByText('SUP-2026-2', { exact: true }).waitFor()
  assert.equal(cases.size, 2, 'AI outage must not erase durable intake')
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 2)
  await page.reload()
  await page.getByText('SUP-2026-2', { exact: true }).waitFor()
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 2, 'Uncertain delivery must not retry on mount')
  await page.setViewportSize({ width: 430, height: 900 })
  await page.screenshot({ path: `${evidence}/03-basic-narrow-navigation.png`, fullPage: true })
  staffAllowed = true
  await page.goto(base + 'support')
  await page.getByRole('link', { name: 'Team intake', exact: true }).waitFor()
  assert.equal(calls.filter(call => call.path.endsWith('/start')).length, 2, 'Staff access discovery must not start any conversation')
  assert.deepEqual(errors, [])
  console.log(`Customer support UI smoke passed: create/save/start, canonical iframe, fresh bearer bridge, human handover/message/AI return, consent, resolve/cancel/reopen, no mount replay and outage retention. Screenshots: ${evidence}`)
} catch (error) {
  await page?.screenshot({ path: `${evidence}/failure.png`, fullPage: true }).catch(() => {})
  console.error(JSON.stringify({ errors, calls }, null, 2))
  throw error
} finally {
  await browser.close()
  await server.close()
}
