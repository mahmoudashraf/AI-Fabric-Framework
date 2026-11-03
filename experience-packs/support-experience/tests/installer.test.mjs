import assert from 'node:assert/strict'
import test from 'node:test'
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { chromium } from '../../../max-mode-widget/node_modules/playwright/index.mjs'

test('public installer mounts one contained engine and fails missing targets or invalid integrity without overlay', { timeout: 60000 }, async () => {
  const assets = {
    '/installer.js': await readFile(new URL('../../../max-mode-widget/dist/ai-workspace-installer.iife.js', import.meta.url)),
    '/engine.js': await readFile(new URL('../../../max-mode-widget/dist/max-mode-widget.iife.js', import.meta.url)),
    '/pack.js': await readFile(new URL('../dist/support-experience.iife.js', import.meta.url)),
  }
  const sri = value => `sha384-${createHash('sha384').update(value).digest('base64')}`
  const id = 'awi_pub_0123456789abcdef0123456789abcdef'
  let tampered = false
  const server = http.createServer((req, res) => {
    const origin = `http://${req.headers.host}`, path = new URL(req.url, origin).pathname
    if (assets[path]) { res.setHeader('Content-Type', 'text/javascript'); res.end(assets[path]); return }
    const send = value => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(value)) }
    if (path.endsWith('/manifest')) {
      send({ schemaVersion: 'loomai-ai-workspace-installation-v2', installationId: id,
        manifestRevision: `sha256:${'1'.repeat(64)}`, assignmentRevision: `sha256:${'2'.repeat(64)}`, generatedAt: '2026-01-01T10:00:00Z', cacheTtlSeconds: 60,
        delivery: { mode: 'embedded', closeAllowed: true }, authentication: { interactionPolicy: 'public', profileCode: 'none', profileVersion: '1.0.0', configurationSchemaVersion: 'none', configuration: {} },
        connection: { mode: 'backend-mediated-private-runtime', handler: 'private-backend-adapter', profileCode: 'fixture', profileVersion: '1.0.0',
          adapter: { bootstrapUrl: `${origin}/adapter`, method: 'POST', credentials: 'omit', responseSchemaVersion: 'loomai-workspace-private-adapter-v1' } },
        workspace: { asset: { version: '1.0.0', url: `${origin}/engine.js`, integrity: tampered ? `sha384-${'A'.repeat(64)}` : sri(assets['/engine.js']) },
          experiencePack: { code: 'support', version: '1.0.0', url: `${origin}/pack.js`, integrity: sri(assets['/pack.js']) },
          configuration: { schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'Fixture support' }, capabilities: { tickets: false, handover: false, currentPage: false } } } }); return
    }
    if (path === '/adapter') {
      const base = '/api/runtime'
      send({ schemaVersion: 'loomai-workspace-private-adapter-v1', chatBaseUrl: origin,
        routes: { queryUrl: `${base}/query`, suggestionsUrl: `${base}/suggestions`, authContextUrl: `${base}/auth`, shellConfigUrl: `${base}/shell`,
          conversationsUrl: `${base}/chat/me/conversations`, conversationItemUrlTemplate: `${base}/chat/me/conversations/{conversationId}` },
        features: { conversations: false }, probeAuthContextOnOpen: false, probeShellConfigOnOpen: false }); return
    }
    if (path.startsWith('/api/runtime')) { send([]); return }
    res.setHeader('Content-Type', 'text/html')
    res.end(`<!doctype html><title>Installer fixture</title><style>body{margin:0}main{margin:30px;height:800px}#support{height:100%}</style>
      <main><div id="support"></div></main><script>window.addEventListener('loomai:workspace-error',event=>window.installationError=event.detail)</script>
      <script async src="/installer.js" data-installation-id="${id}" data-presentation="page" data-container="${path === '/missing' ? '#missing' : '#support'}"></script>`)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1100, height: 960 } })
    await page.goto(origin)
    await page.waitForFunction(() => window.LoomAIWorkspace?.getController() || window.installationError)
    assert.equal(await page.evaluate(() => window.installationError?.message), undefined)
    assert.equal(await page.locator('iframe').count(), 1)
    assert.equal(await page.evaluate(() => typeof window.MaxMode), 'undefined', 'The engine must not also mount in the parent document')
    assert.equal(await page.locator('#support > [data-loomai-support]').count(), 1)
    await page.evaluate(() => { window.originalFrame=document.querySelector('iframe'); window.originalController=window.LoomAIWorkspace.getController() })
    await page.evaluate(async id => {
      const script=document.createElement('script'); script.src='/installer.js'; script.dataset.installationId=id; script.dataset.presentation='page';script.dataset.container='#support';document.body.append(script)
      await window.LoomAIWorkspace.refresh()
    }, id)
    assert.equal(await page.evaluate(() => window.originalFrame===document.querySelector('iframe')), true)
    assert.equal(await page.evaluate(() => window.originalController===window.LoomAIWorkspace.getController()), true)
    assert.equal(await page.locator('iframe').count(), 1)
    await page.evaluate(() => window.LoomAIWorkspace.getController().destroy())
    await page.goto(`${origin}/missing`)
    await page.waitForFunction(() => window.installationError)
    assert.equal(await page.locator('iframe').count(), 0)
    assert.equal(await page.locator('[data-loomai-support]').count(), 0)
    tampered = true
    await page.goto(`${origin}/tampered`)
    await page.waitForFunction(() => window.installationError)
    assert.equal(await page.locator('iframe').count(), 0)
    assert.equal(await page.locator('[data-loomai-support]').count(), 0)
  } finally { await browser.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)) }
})

test('installer preserves manifest canonical messages with tickets enabled and human handover disabled', { timeout: 45000 }, async () => {
  const assets = {
    '/installer.js': await readFile(new URL('../../../max-mode-widget/dist/ai-workspace-installer.iife.js', import.meta.url)),
    '/engine.js': await readFile(new URL('../../../max-mode-widget/dist/max-mode-widget.iife.js', import.meta.url)),
    '/pack.js': await readFile(new URL('../dist/support-experience.iife.js', import.meta.url)),
  }
  const sri = value => `sha384-${createHash('sha384').update(value).digest('base64')}`
  const installationId = 'awi_pub_0123456789abcdef0123456789abcdef'
  const runtimeToken = 'runtime-fixture-token-with-at-least-twenty-characters'
  const calls = []
  const server = http.createServer(async (req, res) => {
    const origin = `http://${req.headers.host}`, path = new URL(req.url, origin).pathname
    if (assets[path]) { res.setHeader('Content-Type', 'text/javascript'); res.end(assets[path]); return }
    let raw = ''
    for await (const chunk of req) raw += chunk
    const call = { path, method: req.method, authorization: req.headers.authorization, key: req.headers['idempotency-key'], body: raw ? JSON.parse(raw) : undefined }
    calls.push(call)
    const send = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(value)) }
    if (path.endsWith('/manifest')) {
      const base = '/api/chat/me/conversations/{conversationId}'
      send({ schemaVersion: 'loomai-ai-workspace-installation-v2', installationId,
        manifestRevision: `sha256:${'1'.repeat(64)}`, assignmentRevision: `sha256:${'2'.repeat(64)}`, generatedAt: '2026-01-01T10:00:00Z', cacheTtlSeconds: 60,
        delivery: { mode: 'embedded', closeAllowed: true },
        authentication: { interactionPolicy: 'login-required', profileCode: 'fixture', profileVersion: '1.0.0', configurationSchemaVersion: 'fixture', configuration: {} },
        connection: { mode: 'public-runtime-authenticated', handler: 'brokered-public-runtime', profileCode: 'fixture', profileVersion: '1.0.0', runtimeBaseUrl: origin,
          conversationHandover: { enabled: false },
          credentialBroker: { url: `${origin}/broker`, method: 'POST', credentials: 'omit', externalAuthorizationHeader: 'Authorization', responseSchemaVersion: 'loomai-workspace-runtime-token-v1' },
          routes: { queryUrl: '/api/chat/me/query', suggestionsUrl: '/api/chat/me/suggestions', authContextUrl: '/api/chat/me/auth-context', shellConfigUrl: '/api/chat/me/shell-config',
            conversationsUrl: '/api/chat/me/conversations', conversationItemUrlTemplate: base,
            conversationMessageUrlTemplate: `${base}/messages`, conversationControlUrlTemplate: `${base}/control` } },
        workspace: { asset: { version: '1.0.0', url: `${origin}/engine.js`, integrity: sri(assets['/engine.js']) },
          experiencePack: { code: 'support', version: '1.0.0', url: `${origin}/pack.js`, integrity: sri(assets['/pack.js']) },
          configuration: { schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'Fixture support' },
            support: { spaceId: 'fixture-space', ticketProviderBindingId: 'fixture-provider', requesterOrigin: origin, teamVisible: true },
            capabilities: { tickets: true, handover: false, currentPage: false }, copy: { starterSuggestions: [] } } } })
      return
    }
    if (path === '/broker') {
      if (call.authorization !== 'Bearer external-fixture-identity') { send({ code: 'UNAUTHORIZED' }, 401); return }
      send({ schemaVersion: 'loomai-workspace-runtime-token-v1', tokenType: 'Bearer', accessToken: runtimeToken,
        expiresAt: new Date(Date.now() + 300000).toISOString(), subjectId: 'fixture-account' }); return
    }
    if (path === '/api/desk/requester/v1/spaces/fixture-space/session') {
      const capabilities = ['CREATE', 'LIST', 'GET']
      send({ authenticated: true, requesterId: 'fixture-account', capabilities,
        space: { spaceId: 'fixture-space', providerBindingId: 'fixture-provider', humanHandoverEnabled: false, capabilities } }); return
    }
    if (path.startsWith('/api/')) {
      if (call.authorization !== `Bearer ${runtimeToken}`) { send({ code: 'UNAUTHORIZED' }, 401); return }
      if (path.endsWith('/auth-context')) { send({ subjectId: 'fixture-account', subjectType: 'END_USER', grantedScopes: ['chat:conversations', 'chat:messages', 'chat:handover:read'] }); return }
      if (path.endsWith('/shell-config')) { send({ success: true, starterPrompts: [] }); return }
      if (path === '/api/chat/me/conversations') { send([]); return }
      const message = path.match(/^\/api\/chat\/me\/conversations\/([^/]+)\/messages$/)
      if (message && req.method === 'POST') {
        send({ success: true, type: 'RAG_RESPONSE', answer: 'Synthetic canonical response.', conversationId: decodeURIComponent(message[1]) }); return
      }
      send({ code: 'ROUTE_NOT_AVAILABLE' }, 404); return
    }
    res.setHeader('Content-Type', 'text/html')
    res.end(`<!doctype html><title>Canonical message fixture</title><style>body{margin:0}main{margin:20px;height:850px}#support{height:100%}</style>
      <main><div id="support"></div></main><script>window.LoomAIWorkspaceHost={version:'1.0.0',identity:{status:'authenticated',subjectKey:'fixture-account'},getIdentityAccessToken:async()=> 'external-fixture-identity'};
      window.addEventListener('loomai:workspace-error',event=>window.installationError=event.detail)</script>
      <script async src="/installer.js" data-installation-id="${installationId}" data-presentation="page" data-container="#support"></script>`)
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1100, height: 960 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto(`http://127.0.0.1:${server.address().port}`)
    await page.waitForFunction(() => window.LoomAIWorkspace?.getController() || window.installationError)
    assert.equal(await page.evaluate(() => window.installationError?.message), undefined)
    const frame = page.frameLocator('iframe[data-loomai-workspace-frame]')
    await frame.locator('textarea').fill('A ticket-only canonical message')
    const sent = page.waitForResponse(response => response.request().method() === 'POST' && /\/conversations\/[^/]+\/messages$/.test(response.url()))
    await frame.locator('textarea').press('Enter')
    assert.equal((await sent).status(), 200)
    await frame.getByText('Synthetic canonical response.', { exact: true }).waitFor()
    const messages = calls.filter(call => call.method === 'POST' && call.path.endsWith('/messages'))
    assert.equal(messages.length, 1)
    assert.equal(messages[0].body.query, 'A ticket-only canonical message')
    assert.ok(messages[0].body.conversationId)
    assert.match(messages[0].key, /^message-/)
    assert.equal(messages[0].authorization, `Bearer ${runtimeToken}`)
    assert.equal(calls.some(call => call.path.endsWith('/query')), false)
    assert.equal(calls.some(call => /\/(handover-requests|ai-resume-requests|events|control)(\/|$)/.test(call.path)), false)
    assert.equal(await frame.getByRole('button', { name: /Talk to a person|Human support|Resume AI/ }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Request help', exact: true }).count(), 1)
    assert.deepEqual(errors, [])
    await page.evaluate(() => window.LoomAIWorkspace.getController().destroy())
    await page.close()
  } finally { await browser.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)) }
})
