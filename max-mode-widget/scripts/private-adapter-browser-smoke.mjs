import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import http from 'node:http'
import { chromium } from 'playwright'

const installationId = 'awi_pub_0123456789abcdef0123456789abcdef'
const installer = await readFile(new URL('../dist/ai-workspace-installer.iife.js', import.meta.url), 'utf8')
const maxModeAsset = await readFile(new URL('../dist/max-mode-widget.iife.js', import.meta.url), 'utf8')
const packAsset = `(() => {
  window.LoomAIWorkspace.registerExperiencePack({
    code: 'shopify-shopping',
    version: '1.0.0',
    mount(context) {
      const config = {
        ...context.widgetConfig,
        launcher: false,
        features: { cart: false, conversations: false, quickActions: false },
        host: { ...context.widgetConfig.host, welcomeMessage: 'Approved store assistant.' },
      };
      window.__workspaceWidgetConfig = config;
      context.maxMode.init(config);
      context.maxMode.open();
      return { destroy() { context.maxMode.destroy(); } };
    },
  });
})();\n`

const integrity = (value) => `sha384-${createHash('sha384').update(value).digest('base64')}`
const observed = { adapterRequests: [], queryRequests: [] }
const conversationOwners = new Map()
let assignmentRevision = revision('assignment')
let hostIdentity = { status: 'anonymous' }
let adapterPath = '/api/shopify/private-adapter/bootstrap'
const server = http.createServer(handler)
await listen(server)
const address = server.address()
const origin = `http://127.0.0.1:${address.port}`
const browser = await chromium.launch({ headless: true })

try {
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto(origin)
  await page.waitForFunction(() => Boolean(window.LoomAIWorkspace?.getController()))

  const result = await send(page, 'Show products in the approved catalog.')
  assert.equal(result.success, true)
  assert.equal(observed.adapterRequests.length, 1)
  const browserSessionId = observed.adapterRequests[0].body.browserSessionId
  assert.match(browserSessionId, /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/)
  assert.deepEqual(observed.adapterRequests[0].body, {
    schemaVersion: 'loomai-workspace-private-adapter-request-v1',
    installationId,
    assignmentRevision,
    browserSessionId,
  })
  assert.match(observed.adapterRequests[0].cookie, /shopify-session=opaque-browser-session/)
  assert.equal(observed.queryRequests.length, 1)
  assert.match(observed.queryRequests[0].cookie, /shopify-session=opaque-browser-session/)
  assert.equal(observed.queryRequests[0].shopifySession, `workspace-${browserSessionId}`)
  assert.equal(observed.queryRequests[0].body.query, 'Show products in the approved catalog.')
  assert.deepEqual(await page.evaluate(() => window.__workspaceWidgetConfig.apiConfig.runtimeAuth), {
    probeAuthContextOnOpen: false,
  })
  assert.equal(await page.evaluate(() => window.__workspaceWidgetConfig.apiConfig.defaultHeaders.Authorization), undefined)
  assert.equal(await page.evaluate(() => window.__workspaceWidgetConfig.integrationMode), 'backend-mediated-private-runtime')

  const originalNamespace = await page.evaluate(() => window.__workspaceWidgetConfig.storageNamespace)
  await page.reload()
  await page.getByText('Approved catalog result.', { exact: true }).waitFor()
  assert.equal(observed.queryRequests.length, 1, 'Reload replayed a query')
  const followUp = await send(page, 'Compare those products.')
  assert.equal(followUp.success, true, 'A restored conversation lost its owner after reload')
  assert.equal(followUp.conversationId, result.conversationId)
  assert.equal(observed.adapterRequests[1].body.browserSessionId, browserSessionId)
  assert.equal(observed.queryRequests[1].shopifySession, observed.queryRequests[0].shopifySession)
  assert.equal(observed.queryRequests[1].body.conversationId, result.conversationId)
  assert.equal(await page.evaluate(() => window.__workspaceWidgetConfig.storageNamespace), originalNamespace)

  assignmentRevision = revision('new-assignment')
  await page.reload()
  await page.locator('textarea').waitFor()
  const reassigned = await send(page, 'Show the current catalog.')
  assert.notEqual(observed.adapterRequests[2].body.browserSessionId, browserSessionId)
  assert.notEqual(reassigned.conversationId, result.conversationId)
  const reassignedNamespace = await page.evaluate(() => window.__workspaceWidgetConfig.storageNamespace)
  assert.notEqual(reassignedNamespace, originalNamespace)

  hostIdentity = { status: 'authenticated', subjectKey: 'verified-other-owner' }
  await page.reload()
  await page.locator('textarea').waitFor()
  const newOwner = await send(page, 'Show my approved catalog.')
  assert.notEqual(observed.adapterRequests[3].body.browserSessionId, observed.adapterRequests[2].body.browserSessionId)
  assert.notEqual(newOwner.conversationId, reassigned.conversationId)
  assert.notEqual(await page.evaluate(() => window.__workspaceWidgetConfig.storageNamespace), reassignedNamespace)

  const currentOwnerSession = observed.adapterRequests[3].body.browserSessionId
  adapterPath = '/api/reviewed-private-adapter/bootstrap'
  await page.reload()
  await page.locator('textarea').waitFor()
  const newAdapter = await send(page, 'Show the approved data from this adapter.')
  assert.notEqual(observed.adapterRequests[4].body.browserSessionId, currentOwnerSession)
  assert.notEqual(newAdapter.conversationId, newOwner.conversationId)

  const secondBrowser = await browser.newPage()
  await secondBrowser.goto(origin)
  await secondBrowser.locator('textarea').waitFor()
  assert.notEqual(observed.adapterRequests[5].body.browserSessionId, observed.adapterRequests[4].body.browserSessionId)
  await secondBrowser.close()

  const changedDuringBootstrap = await browser.newPage()
  await changedDuringBootstrap.addInitScript(() => {
    window.addEventListener('loomai:workspace-error', (event) => { window.__installationError = event.detail })
  })
  await changedDuringBootstrap.route(`**${adapterPath}`, async (route) => {
    await changedDuringBootstrap.evaluate(() => { window.LoomAIWorkspaceHost.identity.subjectKey = 'changed-during-bootstrap' })
    await route.continue()
  })
  const queryCountBeforeChange = observed.queryRequests.length
  await changedDuringBootstrap.goto(origin)
  await changedDuringBootstrap.waitForFunction(() => window.__installationError?.code === 'INSTALLATION_FAILED')
  assert.equal(await changedDuringBootstrap.locator('textarea').count(), 0)
  assert.equal(observed.queryRequests.length, queryCountBeforeChange, 'Identity change mounted the previous owner')
  await changedDuringBootstrap.close()

  const adapterRequestsBeforeCorruption = observed.adapterRequests.length
  await page.evaluate(() => {
    for (const key of Object.keys(sessionStorage)) {
      if (key.startsWith('loomai_workspace_private_adapter_session_v1:')) sessionStorage.setItem(key, 'invalid-session')
    }
  })
  await page.addInitScript(() => {
    window.addEventListener('loomai:workspace-error', (event) => { window.__installationError = event.detail })
  })
  await page.reload()
  await page.waitForFunction(() => window.__installationError?.code === 'INSTALLATION_FAILED')
  assert.equal(observed.adapterRequests.length, adapterRequestsBeforeCorruption, 'Invalid session silently created another owner')
  assert.equal(await page.locator('textarea').count(), 0)

  const unavailableStorage = await browser.newPage()
  await unavailableStorage.addInitScript(() => {
    Storage.prototype.setItem = () => { throw new Error('Storage unavailable') }
    window.addEventListener('loomai:workspace-error', (event) => { window.__installationError = event.detail })
  })
  await unavailableStorage.goto(origin)
  await unavailableStorage.waitForFunction(() => window.__installationError?.code === 'INSTALLATION_FAILED')
  assert.equal(observed.adapterRequests.length, adapterRequestsBeforeCorruption, 'Unavailable storage allowed an unpreserved owner')
  await unavailableStorage.close()
  assert.deepEqual(errors, [])
  console.log('AI Workspace private-adapter browser smoke passed: query/reload/follow-up, assignment/owner/adapter/browser isolation, in-flight identity fencing, corrupt/unavailable storage denial.')
} finally {
  await browser.close()
  await new Promise((resolve) => server.close(resolve))
}

async function handler(request, response) {
  if (request.url === '/' && request.method === 'GET') {
    response.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Set-Cookie': 'shopify-session=opaque-browser-session; HttpOnly; SameSite=Lax; Path=/',
    })
    response.end(`<!doctype html><html><body><script>window.LoomAIWorkspaceHost = { version: '1.0.0', identity: ${JSON.stringify(hostIdentity)} };</script><script
      src="${origin}/ai-workspace-installer.iife.js"
      integrity="${integrity(installer)}"
      crossorigin="anonymous"
      data-installation-id="${installationId}"></script></body></html>`)
    return
  }
  if (request.url === '/ai-workspace-installer.iife.js') return javascript(response, installer)
  if (request.url === '/max-mode.js') return javascript(response, maxModeAsset)
  if (request.url === '/shopify-pack.js') return javascript(response, packAsset)
  if (request.url === `/api/public/ai-workspace/installations/${installationId}/manifest`) {
    return json(response, 200, manifest())
  }
  if (request.url === adapterPath && request.method === 'POST') {
    const payload = await body(request)
    observed.adapterRequests.push({
      body: payload,
      cookie: request.headers.cookie || '',
    })
    return json(response, 200, {
      schemaVersion: 'loomai-workspace-private-adapter-v1',
      chatBaseUrl: origin,
      defaultHeaders: { 'X-Shopify-Session': `workspace-${payload.browserSessionId}` },
      fetchCredentials: 'include',
      routes: routes(),
      features: { conversations: true },
      probeShellConfigOnOpen: false,
      probeAuthContextOnOpen: false,
    })
  }
  if (request.url === '/api/private/chat/query' && request.method === 'POST') {
    const payload = await body(request)
    const owner = request.headers['x-shopify-session']
    const conversationId = payload.conversationId || `chat-fixture-${conversationOwners.size + 1}`
    observed.queryRequests.push({
      body: payload,
      cookie: request.headers.cookie || '',
      shopifySession: request.headers['x-shopify-session'],
    })
    const previousOwner = conversationOwners.get(conversationId)
    if (previousOwner && previousOwner !== owner) {
      return json(response, 200, { success: false, type: 'ERROR', fallbackReason: 'ACCESS_DENIED', conversationId, answer: 'Access denied to conversation.' })
    }
    conversationOwners.set(conversationId, owner)
    return json(response, 200, { success: true, type: 'INFORMATION_PROVIDED', conversationId, answer: 'Approved catalog result.', sources: [], actions: [], suggestions: [] })
  }
  if (request.url === '/api/private/chat/suggestions') return json(response, 200, { success: true, suggestions: [] })
  return json(response, 404, { message: 'Not found' })
}

function manifest() {
  return {
    schemaVersion: 'loomai-ai-workspace-installation-v2',
    installationId,
    manifestRevision: revision('manifest'),
    assignmentRevision,
    generatedAt: new Date().toISOString(),
    cacheTtlSeconds: 30,
    delivery: { mode: 'embedded', closeAllowed: true },
    authentication: {
      interactionPolicy: 'public',
      profileCode: 'shopify-customer-session',
      profileVersion: '1.0.0',
      configurationSchemaVersion: '1.0.0',
      configuration: {},
    },
    connection: {
      mode: 'backend-mediated-private-runtime',
      profileCode: 'shopify-private-adapter',
      profileVersion: '1.0.0',
      handler: 'private-backend-adapter',
      adapter: {
        bootstrapUrl: `${origin}${adapterPath}`,
        method: 'POST',
        credentials: 'include',
        responseSchemaVersion: 'loomai-workspace-private-adapter-v1',
      },
    },
    workspace: {
      asset: { version: '1.1.0', url: `${origin}/max-mode.js`, integrity: integrity(maxModeAsset) },
      experiencePack: {
        code: 'shopify-shopping',
        version: '1.0.0',
        url: `${origin}/shopify-pack.js`,
        integrity: integrity(packAsset),
      },
      configuration: {},
    },
  }
}

async function send(page, query) {
  const received = page.waitForResponse((response) => response.url() === `${origin}/api/private/chat/query` && response.request().method() === 'POST')
  await page.locator('textarea').fill(query)
  await page.locator('textarea').press('Enter')
  const response = await received
  assert.equal(response.status(), 200)
  const result = await response.json()
  await page.getByText(result.answer, { exact: true }).last().waitFor()
  return result
}

function routes() {
  return {
    queryUrl: '/api/private/chat/query',
    suggestionsUrl: '/api/private/chat/suggestions',
    authContextUrl: '/api/private/chat/auth-context',
    shellConfigUrl: '/api/private/chat/shell-config',
    conversationsUrl: '/api/private/chat/conversations',
    conversationItemUrlTemplate: '/api/private/chat/conversations/{conversationId}',
  }
}

function revision(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`
}

async function body(request) {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

function javascript(response, value) {
  response.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8' })
  response.end(value)
}

function json(response, status, value) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(value))
}

function listen(target) {
  return new Promise((resolve, reject) => {
    target.once('error', reject)
    target.listen(0, '127.0.0.1', resolve)
  })
}
