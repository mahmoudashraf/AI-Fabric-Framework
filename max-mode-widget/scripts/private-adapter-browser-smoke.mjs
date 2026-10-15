import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import http from 'node:http'
import { chromium } from 'playwright'

const installationId = 'awi_pub_0123456789abcdef0123456789abcdef'
const installer = await readFile(new URL('../dist/ai-workspace-installer.iife.js', import.meta.url), 'utf8')
const maxModeAsset = `(() => {
  window.MaxMode = {
    init(config) { window.__workspaceWidgetConfig = config; },
    open() {},
    destroy() {},
  };
})();\n`
const packAsset = `(() => {
  window.LoomAIWorkspace.registerExperiencePack({
    code: 'shopify-shopping',
    version: '1.0.0',
    mount(context) {
      context.maxMode.init({ ...context.widgetConfig, experience: 'shopify-shopping-workspace' });
      const config = window.__workspaceWidgetConfig;
      return {
        async query() {
          const response = await fetch(config.apiConfig.runtimeRoutes.chatQueryUrl, {
            method: 'POST',
            credentials: config.apiConfig.fetchCredentials,
            headers: { 'Content-Type': 'application/json', ...config.apiConfig.defaultHeaders },
            body: JSON.stringify({ query: 'Show products in the approved catalog.' }),
          });
          return { status: response.status, body: await response.json() };
        },
      };
    },
  });
})();\n`

const integrity = (value) => `sha384-${createHash('sha384').update(value).digest('base64')}`
const observed = { adapterRequests: [], queryRequests: [] }
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

  const result = await page.evaluate(() => window.LoomAIWorkspace.getController().query())
  assert.equal(result.status, 200)
  assert.equal(result.body.success, true)
  assert.equal(observed.adapterRequests.length, 1)
  assert.deepEqual(observed.adapterRequests[0].body, {
    schemaVersion: 'loomai-workspace-private-adapter-request-v1',
    installationId,
    assignmentRevision: revision('assignment'),
  })
  assert.match(observed.adapterRequests[0].cookie, /shopify-session=opaque-browser-session/)
  assert.equal(observed.queryRequests.length, 1)
  assert.match(observed.queryRequests[0].cookie, /shopify-session=opaque-browser-session/)
  assert.equal(observed.queryRequests[0].shopifySession, 'adapter-session-reference')
  assert.deepEqual(observed.queryRequests[0].body, {
    query: 'Show products in the approved catalog.',
  })
  assert.deepEqual(await page.evaluate(() => window.__workspaceWidgetConfig.apiConfig.runtimeAuth), {
    probeAuthContextOnOpen: false,
  })
  assert.equal(await page.evaluate(() => window.__workspaceWidgetConfig.apiConfig.defaultHeaders.Authorization), undefined)
  assert.equal(await page.evaluate(() => window.__workspaceWidgetConfig.integrationMode), 'backend-mediated-private-runtime')
  assert.deepEqual(errors, [])
  console.log('AI Workspace private-adapter browser smoke passed.')
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
    response.end(`<!doctype html><html><body><script
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
  if (request.url === '/api/shopify/private-adapter/bootstrap' && request.method === 'POST') {
    observed.adapterRequests.push({
      body: await body(request),
      cookie: request.headers.cookie || '',
    })
    return json(response, 200, {
      schemaVersion: 'loomai-workspace-private-adapter-v1',
      chatBaseUrl: origin,
      defaultHeaders: { 'X-Shopify-Session': 'adapter-session-reference' },
      fetchCredentials: 'include',
      routes: routes(),
      features: { conversations: true },
      probeShellConfigOnOpen: false,
      probeAuthContextOnOpen: false,
    })
  }
  if (request.url === '/api/private/chat/query' && request.method === 'POST') {
    observed.queryRequests.push({
      body: await body(request),
      cookie: request.headers.cookie || '',
      shopifySession: request.headers['x-shopify-session'],
    })
    return json(response, 200, { success: true, answer: 'Approved catalog result.' })
  }
  return json(response, 404, { message: 'Not found' })
}

function manifest() {
  return {
    schemaVersion: 'loomai-ai-workspace-installation-v2',
    installationId,
    manifestRevision: revision('manifest'),
    assignmentRevision: revision('assignment'),
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
        bootstrapUrl: `${origin}/api/shopify/private-adapter/bootstrap`,
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
