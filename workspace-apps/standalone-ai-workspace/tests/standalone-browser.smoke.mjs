import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { spawn } from 'node:child_process'
import http from 'node:http'
import test from 'node:test'
import { chromium } from 'playwright'

const installerSource = `(() => {
  const installationId = document.currentScript?.dataset.installationId || 'missing';
  const marker = document.createElement('div');
  marker.id = 'installed-workspace';
  marker.dataset.installationId = installationId;
  marker.dataset.identity = window.LoomAIWorkspaceHost?.identity?.status || 'missing';
  marker.textContent = installationId;
  document.body.appendChild(marker);
  window.dispatchEvent(new CustomEvent('loomai:workspace-ready'));
})();\n`
const installerIntegrity = `sha384-${createHash('sha384').update(installerSource).digest('base64')}`

const installations = new Map([
  ['app-one.localhost', app('awi_pub_app_one', 'App One', 'public', 'none')],
  ['app-two.localhost', app('awi_pub_app_two', 'App Two', 'public', 'none')],
  ['secure.localhost', app('awi_pub_secure', 'Secure App', 'login-required', 'supabase')],
  ['mixed.localhost', app('awi_pub_mixed', 'Mixed App', 'public-with-protected-capabilities', 'supabase')],
])

let platformServer
let shellProcess
let browser
let platformPort
let shellPort
const requests = []

test.before(async () => {
  platformPort = await freePort()
  shellPort = await freePort()
  platformServer = http.createServer(platformHandler)
  await listen(platformServer, platformPort)
  shellProcess = spawn(process.execPath, ['server.mjs'], {
    cwd: new URL('..', import.meta.url),
    env: {
      ...process.env,
      PORT: String(shellPort),
      PLATFORM_API_BASE_URL: `http://127.0.0.1:${platformPort}`,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  await waitForHttp(`http://127.0.0.1:${shellPort}/health`)
  browser = await chromium.launch({ headless: true })
})

test.after(async () => {
  await browser?.close()
  shellProcess?.kill('SIGTERM')
  await new Promise((resolve) => platformServer?.close(resolve))
})

test('one shared shell resolves exact hosts to isolated installations', async () => {
  const context = await browser.newContext()
  const first = await context.newPage()
  const second = await context.newPage()
  const firstNavigation = await first.goto(appUrl('app-one.localhost'))
  await second.goto(appUrl('app-two.localhost'))
  await first.locator('#installed-workspace').waitFor()
  await second.locator('#installed-workspace').waitFor()
  assert.equal(await first.locator('#installed-workspace').getAttribute('data-installation-id'), 'awi_pub_app_one')
  assert.equal(await second.locator('#installed-workspace').getAttribute('data-installation-id'), 'awi_pub_app_two')
  assert.equal(await first.title(), 'App One')
  assert.equal(await second.title(), 'App Two')
  assert.equal(await first.evaluate(() => typeof window.LoomAIWorkspaceHost?.requestSignIn), 'undefined')
  assert.equal(firstNavigation?.headers()['cross-origin-opener-policy'], 'same-origin')
  assert.equal(firstNavigation?.headers()['referrer-policy'], 'no-referrer')
  assert.equal(firstNavigation?.headers()['x-content-type-options'], 'nosniff')
  assert.equal(firstNavigation?.headers()['x-frame-options'], 'DENY')
  await context.close()
})

test('an unknown host fails closed without loading the installer', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('unknown.localhost'))
  await page.getByRole('heading', { name: 'This AI app is unavailable' }).waitFor()
  assert.equal(await page.locator('#installed-workspace').count(), 0)
  assert.match(await page.locator('#status-copy').textContent(), /unavailable/i)
  await page.close()
})

test('login-required policy blocks installation until Supabase identity succeeds', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.getByRole('heading', { name: 'Sign in to continue' }).waitFor()
  assert.equal(await page.locator('#installed-workspace').count(), 0)
  assert.equal(await page.locator('#auth-close').isVisible(), false)
  await page.getByLabel('Email').fill('operator@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#installed-workspace').waitFor()
  assert.equal(await page.locator('#installed-workspace').getAttribute('data-identity'), 'authenticated')
  await page.close()
})

test('mixed-mode sign-in clears only anonymous installation state and never replays a request', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  await page.evaluate(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_mixed:conversation:anonymous', 'remove-me')
    sessionStorage.setItem('loomai_workspace:awi_pub_app_two:conversation:anonymous', 'keep-me')
    window.dispatchEvent(new CustomEvent('loomai:workspace-authentication-required'))
  })
  await page.getByRole('heading', { name: 'Sign in to continue' }).waitFor()
  const requestCountBeforeLogin = requests.filter((entry) => entry.path.startsWith('/api/chat')).length
  await page.getByLabel('Email').fill('member@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  const state = await page.evaluate(() => ({
    anonymous: sessionStorage.getItem('loomai_workspace:awi_pub_mixed:conversation:anonymous'),
    otherInstallation: sessionStorage.getItem('loomai_workspace:awi_pub_app_two:conversation:anonymous'),
  }))
  assert.equal(state.anonymous, null)
  assert.equal(state.otherInstallation, 'keep-me')
  assert.equal(requests.filter((entry) => entry.path.startsWith('/api/chat')).length, requestCountBeforeLogin)
  await page.close()
})

test('cancelling mixed-mode sign-in preserves the anonymous workspace and executes no request', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  await page.evaluate(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_mixed:conversation:anonymous', 'anonymous-conversation')
    window.dispatchEvent(new CustomEvent('loomai:workspace-authentication-required'))
  })
  await page.getByRole('heading', { name: 'Sign in to continue' }).waitFor()
  const providerRequestsBeforeCancel = requests.filter((entry) => entry.path.startsWith('/auth/v1/')).length
  await page.locator('#auth-close').click()
  await page.waitForFunction(() => !document.querySelector('#auth-dialog')?.hasAttribute('open'))
  const state = await page.evaluate(() => ({
    conversation: sessionStorage.getItem('loomai_workspace:awi_pub_mixed:conversation:anonymous'),
    installed: Boolean(document.getElementById('installed-workspace')),
    identity: window.LoomAIWorkspaceHost.identity.status,
  }))
  assert.equal(state.conversation, 'anonymous-conversation')
  assert.equal(state.installed, true)
  assert.equal(state.identity, 'anonymous')
  assert.equal(requests.filter((entry) => entry.path.startsWith('/auth/v1/')).length, providerRequestsBeforeCancel)
  await page.close()
})

test('optional identity lookup preserves anonymous mixed-mode workspace without reloading', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  const lookup = await page.evaluate(async () => ({
    token: await window.LoomAIWorkspaceHost.getIdentityAccessToken(),
    installed: Boolean(document.getElementById('installed-workspace')),
    identity: window.LoomAIWorkspaceHost.identity.status,
  }))
  assert.equal(lookup.token, null)
  assert.equal(lookup.installed, true)
  assert.equal(lookup.identity, 'anonymous')
  await page.close()
})

test('magic-link callback clears anonymous state before mounting authenticated workspace', async () => {
  const context = await browser.newContext()
  await context.addInitScript(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_mixed:conversation:anonymous', 'remove-me')
    sessionStorage.setItem('loomai_workspace:awi_pub_app_two:conversation:anonymous', 'keep-me')
  })
  const page = await context.newPage()
  await page.goto(`${appUrl('mixed.localhost')}#access_token=magic-access-token&refresh_token=magic-refresh-token&expires_in=3600`)
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  const state = await page.evaluate(() => ({
    anonymous: sessionStorage.getItem('loomai_workspace:awi_pub_mixed:conversation:anonymous'),
    otherInstallation: sessionStorage.getItem('loomai_workspace:awi_pub_app_two:conversation:anonymous'),
    fragment: location.hash,
  }))
  assert.equal(state.anonymous, null)
  assert.equal(state.otherInstallation, 'keep-me')
  assert.equal(state.fragment, '')
  await context.close()
})

test('logout removes authenticated and stale anonymous state before returning to the entry policy', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.getByLabel('Email').fill('operator@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  await page.evaluate(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:anonymous', 'remove-anonymous')
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1', 'remove-authenticated')
  })
  await page.evaluate(() => window.LoomAIWorkspaceHost?.signOut())
  await page.getByRole('heading', { name: 'Sign in to continue' }).waitFor()
  const state = await page.evaluate(() => ({
    anonymous: sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:anonymous'),
    authenticated: sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1'),
  }))
  assert.equal(state.anonymous, null)
  assert.equal(state.authenticated, null)
  await page.close()
})

test('a revoked expired provider session clears private state and restores the required login gate', async () => {
  const context = await browser.newContext()
  await context.addInitScript(() => {
    sessionStorage.setItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase', JSON.stringify({
      access_token: 'expired-access-token',
      refresh_token: 'revoked-refresh-token',
      expires_at: Math.floor(Date.now() / 1000) - 60,
      user: { id: 'revoked-user', email: 'revoked@example.test' },
    }))
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:revoked-user', 'private-conversation')
  })
  const page = await context.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.getByRole('heading', { name: 'Sign in to continue' }).waitFor()
  assert.equal(await page.locator('#installed-workspace').count(), 0)
  const state = await page.evaluate(() => ({
    provider: sessionStorage.getItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase'),
    privateConversation: sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:revoked-user'),
  }))
  assert.equal(state.provider, null)
  assert.equal(state.privateConversation, null)
  await context.close()
})

function app(installationId, displayName, interactionPolicy, profileCode) {
  return { installationId, displayName, interactionPolicy, profileCode }
}

function appUrl(hostname) {
  return `http://${hostname}:${shellPort}/`
}

function platformHandler(request, response) {
  const origin = request.headers.origin || ''
  const hostname = origin ? new URL(origin).hostname : ''
  requests.push({ method: request.method, path: request.url || '', origin })
  response.setHeader('Access-Control-Allow-Origin', origin || '*')
  response.setHeader('Access-Control-Allow-Headers', 'Content-Type, Accept, apikey, Authorization')
  response.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
  if (request.method === 'OPTIONS') {
    response.writeHead(204).end()
    return
  }
  if (request.url === '/api/public/ai-workspace/apps/bootstrap') {
    const binding = installations.get(hostname)
    if (!binding) {
      json(response, 404, { message: 'Unknown app host' })
      return
    }
    json(response, 200, {
      schemaVersion: 'loomai-ai-workspace-app-bootstrap-v1',
      installationId: binding.installationId,
      displayName: binding.displayName,
      interactionPolicy: binding.interactionPolicy,
      delivery: { mode: 'standalone', closeAllowed: false, appUrl: origin },
      authentication: {
        profileCode: binding.profileCode,
        profileVersion: '1.0.0',
        configuration: binding.profileCode === 'supabase'
          ? {
              projectUrl: `http://127.0.0.1:${platformPort}`,
              publishableKey: 'public-test-key',
              methods: ['email-password'],
            }
          : {},
      },
      installer: {
        url: `http://127.0.0.1:${platformPort}/install.js`,
        integrity: installerIntegrity,
        version: 'test',
      },
    })
    return
  }
  if (request.url === '/install.js') {
    response.writeHead(200, { 'Content-Type': 'application/javascript' })
    response.end(installerSource)
    return
  }
  if (request.url === '/auth/v1/token?grant_type=password' && request.method === 'POST') {
    json(response, 200, {
      access_token: 'verified-access-token',
      refresh_token: 'verified-refresh-token',
      expires_in: 3600,
      user: { id: 'user-verified-1', email: 'member@example.test' },
    })
    return
  }
  if (request.url === '/auth/v1/token?grant_type=refresh_token' && request.method === 'POST') {
    json(response, 401, { message: 'Refresh token revoked' })
    return
  }
  if (request.url === '/auth/v1/user') {
    json(response, 200, { id: 'user-verified-1', email: 'member@example.test' })
    return
  }
  if (request.url === '/auth/v1/logout' && request.method === 'POST') {
    response.writeHead(204).end()
    return
  }
  json(response, 404, { message: 'Not found' })
}

function json(response, status, value) {
  response.writeHead(status, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify(value))
}

async function freePort() {
  const server = http.createServer()
  await listen(server, 0)
  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : 0
  await new Promise((resolve) => server.close(resolve))
  return port
}

function listen(server, port) {
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, '127.0.0.1', resolve)
  })
}

async function waitForHttp(url) {
  const deadline = Date.now() + 10_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100))
  }
  throw new Error(`Timed out waiting for ${url}`)
}
