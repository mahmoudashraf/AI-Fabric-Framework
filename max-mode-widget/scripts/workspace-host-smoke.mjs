import assert from 'node:assert/strict'
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { chromium } from 'playwright'

const widget = await readFile(new URL('../dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const pack = await readFile(new URL('../../experience-packs/generic-workspace-experience/dist/generic-workspace-experience.iife.js', import.meta.url), 'utf8')
const requests = []
const waiting = new Map()
let controlState = 'AI_ACTIVE'
let controlVersion = 1
let acceptExplicitMessage = false
const timestamp = '2026-10-30T12:00:00Z'
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://fixture.local')
  if (url.pathname === '/widget.js' || url.pathname === '/pack.js') {
    res.setHeader('Content-Type', 'text/javascript')
    res.end(url.pathname === '/widget.js' ? widget : pack)
    return
  }
  if (!url.pathname.startsWith('/api/')) {
    res.setHeader('Content-Type', 'text/html')
    res.end(`<!doctype html><title>Workspace host contract fixture</title>
      <script>window.LoomAIWorkspace={registerExperiencePack:p=>window.pack=p}</script>
      <script src="/widget.js"></script><script src="/pack.js"></script>`)
    return
  }
  const observed = { path: url.pathname, method: req.method, account: req.headers['x-fixture-account'] }
  requests.push(observed)
  const id = url.pathname.split('/')[5]
  if (url.pathname.endsWith('/events')) {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' })
    res.write(': connected\n\n')
    return
  }
  res.setHeader('Content-Type', 'application/json')
  if (acceptExplicitMessage && req.method === 'POST' && url.pathname.endsWith('/messages')) {
    let body = ''
    req.on('data', chunk => { body += chunk })
    req.on('end', () => {
      observed.body = JSON.parse(body)
      res.writeHead(202).end(JSON.stringify({ success: true, type: 'HUMAN_MESSAGE_ACCEPTED', conversationId: id }))
    })
    return
  }
  if (req.method !== 'GET') {
    res.writeHead(500).end(JSON.stringify({ message: 'Host history operations must not write.' }))
    return
  }
  if (url.pathname.endsWith('/control')) {
    res.end(JSON.stringify({ conversationId: id, controlState, controlVersion, messages: [] }))
    return
  }
  if (id === 'forbidden') {
    res.writeHead(403).end(JSON.stringify({ message: 'Not authorized' }))
    return
  }
  const finish = () => res.end(JSON.stringify(history(id)))
  if (id === 'slow') waiting.set(id, finish)
  else finish()
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  page.setDefaultTimeout(8_000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(origin)
  const mount = async (accountKey = 'account-a', namespace = 'thread-a') => page.evaluate(async ({ accountKey, namespace }) => {
    const base = '/api/chat/me/conversations/{conversationId}'
    const apiConfig = {
      chatBaseUrl: location.origin + '/api',
      defaultHeaders: { 'X-Fixture-Account': accountKey },
      probeShellConfigOnOpen: false,
      runtimeAuth: { probeAuthContextOnOpen: false },
      conversationHandover: { enabled: true },
      runtimeRoutes: {
        conversationMessageUrlTemplate: base + '/messages',
        handoverRequestUrlTemplate: base + '/handover',
        handoverCancelUrlTemplate: base + '/handover/{handoverId}/cancel',
        handoverRetryUrlTemplate: base + '/handover/{handoverId}/retry',
        conversationControlUrlTemplate: base + '/control',
        conversationEventsUrlTemplate: base + '/events',
        aiResumeRequestUrlTemplate: base + '/resume',
      },
    }
    window.previousController = window.controller
    window.controller = await window.pack.mount({
      installationId: 'fixture-installation', assignmentRevision: 'revision-a',
      connectionMode: 'backend-mediated-private-runtime', deliveryMode: 'embedded',
      hostSession: { namespace, accountKey },
      configuration: { app: { assistantLabel: 'Example assistant' }, capabilities: { currentPage: false }, copy: { starterSuggestions: [] } },
      widgetConfig: { apiConfig, storageNamespace: 'fixture-installation' },
      maxMode: window.MaxMode, refreshAssignment: async () => {},
    })
    return window.controller.whenReady()
  }, { accountKey, namespace })

  const initial = await mount()
  assert.equal(initial.ready, true)
  assert.equal(initial.conversationId, null)
  assert.equal(requests.length, 0, 'A host mount must not list, select, create or replay a conversation')
  await assert.rejects(page.evaluate(() => window.controller.refreshConversation()), /Open an existing conversation/)

  await page.evaluate(() => {
    window.observedStates = []
    window.unsubscribe = window.controller.onStateChange(state => window.observedStates.push(state))
  })
  const loaded = await page.evaluate(async () => {
    const opened = await window.controller.openConversation('alpha', { open: false })
    if (opened.isOpen) throw new Error('open:false unexpectedly opened the workspace')
    await window.controller.refreshConversation()
    window.MaxMode.open()
    return window.controller.getState()
  })
  assert.equal(loaded.conversationId, 'alpha')
  assert.equal(loaded.controlState, 'AI_ACTIVE')
  await page.getByText('Canonical alpha answer.', { exact: true }).waitFor()
  await page.getByText('Ready', { exact: true }).waitFor()
  assert.equal(await page.getByText('Receipt answer must not replace canonical text.', { exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Yes, confirm', exact: true }).count(), 0)
  const firstGets = requests.filter(req => req.path === '/api/chat/me/conversations/alpha').length
  await page.evaluate(() => window.controller.refreshConversation())
  assert.equal(requests.filter(req => req.path === '/api/chat/me/conversations/alpha').length, firstGets + 1)
  assert.equal(await page.getByText('Canonical alpha answer.', { exact: true }).count(), 1, 'Refresh must replace canonical messages, not duplicate them')
  assert.ok((await page.evaluate(() => window.observedStates)).some(state => state.status === 'loading'))
  const countAtUnsubscribe = await page.evaluate(() => { window.unsubscribe(); return window.observedStates.length })

  controlState = 'HUMAN_ACTIVE'; controlVersion++
  const human = await page.evaluate(() => window.controller.refreshConversation())
  assert.equal(human.controlState, 'HUMAN_ACTIVE')
  assert.equal(await page.evaluate(() => window.observedStates.length), countAtUnsubscribe)
  assert.equal(await page.evaluate(() => { try { window.MaxMode.sendMessage('Do not resend intake'); return false } catch { return true } }), true)
  assert.equal(requests.filter(req => req.method !== 'GET').length, 0, 'Restoring history must never query/replay/write')

  await page.evaluate(() => window.controller.openConversation('expired'))
  await page.getByText('Canonical expired answer.', { exact: true }).waitFor()
  await page.getByText('Canonical alpha answer.', { exact: true }).waitFor({ state: 'hidden' })
  assert.equal(await page.getByText('Canonical alpha answer.', { exact: true }).count(), 0)
  assert.equal(await page.getByText('Ready', { exact: true }).count(), 0, 'Expired presentation must not reuse another turn\'s card')
  await assert.rejects(page.evaluate(() => window.controller.openConversation('forbidden')), /403/)
  await page.waitForFunction(() => window.controller.getState().status === 'error')
  await page.getByText('Canonical expired answer.', { exact: true }).waitFor({ state: 'hidden' })
  assert.equal(await page.evaluate(() => window.controller.getState().conversationId), null)
  assert.equal(await page.getByText('Canonical expired answer.', { exact: true }).count(), 0, 'Denied selection must not leave previous messages visible')

  // A retired account's delayed GET cannot populate the replacement account or revive its controller.
  await page.evaluate(() => { window.pendingOpen = window.controller.openConversation('slow').then(() => 'resolved', () => 'rejected') })
  await page.waitForFunction(() => window.controller.getState().status === 'loading')
  await assert.rejects(page.evaluate(() => window.controller.refreshConversation()), /current workspace operation/)
  const deadline = Date.now() + 8_000
  while (!waiting.has('slow') && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 10))
  assert.ok(waiting.has('slow'), 'Expected the delayed history request')
  await mount('account-b', 'thread-b')
  waiting.get('slow')()
  assert.equal(await page.evaluate(() => window.pendingOpen), 'rejected')
  assert.equal(await page.evaluate(() => window.controller.getState().conversationId), null)
  await assert.rejects(page.evaluate(() => window.previousController.openConversation('alpha')), /no longer active/)
  await page.evaluate(() => window.previousController.destroy())
  assert.equal(await page.evaluate(() => window.controller.getState().ready), true, 'Destroying an old pack controller must not unmount the new account')
  await page.evaluate(() => window.controller.openConversation('beta'))
  await page.getByText('Canonical beta answer.', { exact: true }).waitFor()
  assert.equal(await page.getByText('Canonical slow answer.', { exact: true }).count(), 0)
  assert.ok(requests.some(req => req.path === '/api/chat/me/conversations/beta' && req.account === 'account-b'))
  assert.equal(await page.evaluate(() => Object.keys(sessionStorage).some(key => key.startsWith('maxmode_widget_state'))), false,
    'Host-controlled canonical transcripts must not be restored/persisted as browser authority')

  await page.reload()
  await mount('account-b', 'thread-b')
  assert.equal(await page.evaluate(() => window.controller.getState().conversationId), null, 'Reload must wait for an explicit authorized conversation')
  await page.evaluate(() => window.controller.openConversation('beta'))
  assert.equal(requests.filter(req => req.method !== 'GET').length, 0)
  acceptExplicitMessage = true
  await page.locator('textarea').fill('A new message for the human, not initial intake.')
  const sent = page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/beta/messages'))
  await page.locator('textarea').press('Enter')
  assert.equal((await sent).status(), 202)
  const writes = requests.filter(req => req.method === 'POST')
  assert.equal(writes.length, 1, 'Only the explicit composer message may write')
  assert.equal(writes[0].body.conversationId, 'beta')
  assert.equal(writes[0].body.query, 'A new message for the human, not initial intake.')
  await page.evaluate(() => window.controller.destroy())
  assert.deepEqual(errors, [])
  console.log('Workspace host smoke passed: GET-only open/refresh, retained cards, canonical text, control state, denial, reload, account replacement, stale completion and pack lifecycle.')
} finally {
  await browser.close()
  server.closeAllConnections()
  await new Promise(resolve => server.close(resolve))
}

function history(id) {
  return {
    id, status: 'ACTIVE', createdAt: timestamp, controlState, controlVersion,
    participantMessages: [
      { messageId: `${id}-user`, role: 'USER', sequence: 1, content: `Already delivered ${id} intake.`, createdAt: timestamp },
      { messageId: `${id}-ai`, role: 'AI', sequence: 2, content: `Canonical ${id} answer.`, createdAt: timestamp },
    ],
    completedTurns: id === 'expired' ? [] : [{
      userMessageId: `${id}-user`, aiMessageId: `${id}-ai`, completedAt: timestamp,
      response: {
        type: 'ACTION_EXECUTED', success: true, conversationId: id,
        answer: 'Receipt answer must not replace canonical text.',
        sources: [{ id: 'guide', title: 'Setup guide', content: 'Public configuration guidance', type: 'document' }],
        actions: [{ action: 'example_read_status', actionResult: { data: { status: 'Ready', observedAt: timestamp } } }],
      },
    }],
  }
}
