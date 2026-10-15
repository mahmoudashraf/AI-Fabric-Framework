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
    window.LoomAIWorkspaceHost.requestSignIn()
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
    window.LoomAIWorkspaceHost.requestSignIn()
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

test('authentication challenge consumes only the canonical top-level requirement projection', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('loomai:workspace-authentication-required', { detail: {
      installationId: 'awi_pub_mixed',
      interactionPolicy: 'public-with-protected-capabilities',
      authenticationRequirement: {},
      metadata: { authenticationRequirement: {
        schemaVersion: 'loomai-workspace-authentication-requirement-v1',
        reasonCode: 'AUTHENTICATION_REQUIRED',
        retryPolicy: 'EXPLICIT_NEW_REQUEST',
      } },
    } }))
  })
  await page.waitForTimeout(50)
  assert.equal(await page.locator('#auth-dialog').getAttribute('open'), null)
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent('loomai:workspace-authentication-required', { detail: {
      authenticationRequirement: {
        schemaVersion: 'loomai-workspace-authentication-requirement-v1',
        reasonCode: 'AUTHENTICATION_REQUIRED',
        retryPolicy: 'EXPLICIT_NEW_REQUEST',
      },
    } }))
  })
  await page.locator('#auth-dialog[open]').waitFor()
  await page.close()
})

test('magic-link callback clears anonymous state before mounting authenticated workspace', async () => {
  const context = await browser.newContext()
  await context.addInitScript(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_mixed:conversation:anonymous', 'remove-me')
    sessionStorage.setItem('loomai_workspace:awi_pub_app_two:conversation:anonymous', 'keep-me')
    localStorage.setItem('loomai_engage_magic_link:mixed.localhost:awi_pub_mixed:supabase', JSON.stringify({
      attemptId: 'verified-attempt',
      createdAt: Date.now(),
    }))
  })
  const page = await context.newPage()
  await page.goto(`${appUrl('mixed.localhost')}?loomai_auth_attempt=verified-attempt#access_token=magic-access-token&refresh_token=magic-refresh-token&expires_in=3600`)
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  const state = await page.evaluate(() => ({
    anonymous: sessionStorage.getItem('loomai_workspace:awi_pub_mixed:conversation:anonymous'),
    otherInstallation: sessionStorage.getItem('loomai_workspace:awi_pub_app_two:conversation:anonymous'),
    fragment: location.hash,
    search: location.search,
    pendingAttempt: localStorage.getItem('loomai_engage_magic_link:mixed.localhost:awi_pub_mixed:supabase'),
  }))
  assert.equal(state.anonymous, null)
  assert.equal(state.otherInstallation, 'keep-me')
  assert.equal(state.fragment, '')
  assert.equal(state.search, '')
  assert.equal(state.pendingAttempt, null)
  await context.close()
})

test('unsolicited magic-link token callback is rejected without creating an authenticated workspace', async () => {
  const page = await browser.newPage()
  await page.goto(`${appUrl('mixed.localhost')}?loomai_auth_attempt=unsolicited#access_token=magic-access-token&refresh_token=magic-refresh-token&expires_in=3600`)
  await page.getByRole('heading', { name: 'Sign in to continue' }).waitFor()
  const state = await page.evaluate(() => ({
    identity: window.LoomAIWorkspaceHost?.identity.status,
    fragment: location.hash,
    search: location.search,
    providerSession: sessionStorage.getItem('loomai_engage_identity:mixed.localhost:awi_pub_mixed:supabase'),
  }))
  assert.equal(state.identity, 'anonymous')
  assert.equal(state.fragment, '')
  assert.equal(state.search, '')
  assert.equal(state.providerSession, null)
  await page.close()
})

test('a consumed magic-link callback nonce cannot be replayed', async () => {
  const context = await browser.newContext()
  const first = await context.newPage()
  await first.goto(appUrl('mixed.localhost'))
  await first.evaluate(() => {
    localStorage.setItem('loomai_engage_magic_link:mixed.localhost:awi_pub_mixed:supabase', JSON.stringify({
      attemptId: 'single-use-attempt',
      createdAt: Date.now(),
    }))
  })
  const callback = `${appUrl('mixed.localhost')}?loomai_auth_attempt=single-use-attempt#access_token=magic-access-token&refresh_token=magic-refresh-token&expires_in=3600`
  await first.goto(callback)
  await first.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  await first.close()

  const replay = await context.newPage()
  await replay.goto(callback)
  await replay.locator('#auth-dialog[open]').waitFor()
  assert.equal(await replay.locator('#installed-workspace').getAttribute('data-identity'), 'anonymous')
  assert.equal(await replay.evaluate(() => sessionStorage.getItem('loomai_engage_identity:mixed.localhost:awi_pub_mixed:supabase')), null)
  assert.equal(await replay.evaluate(() => location.hash), '')
  assert.equal(await replay.evaluate(() => location.search), '')
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

test('failed mixed-mode login retains anonymous state and never sends a chat request', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  await page.evaluate(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_mixed:conversation:anonymous', 'anonymous-state')
    window.LoomAIWorkspaceHost.requestSignIn()
  })
  const before = requests.filter((entry) => entry.path.startsWith('/api/chat')).length
  await page.getByLabel('Email').fill('member@example.test')
  await page.getByLabel('Password').fill('invalid-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#auth-error:not(.hidden)').waitFor()
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_workspace:awi_pub_mixed:conversation:anonymous')), 'anonymous-state')
  assert.equal(await page.locator('#installed-workspace').getAttribute('data-identity'), 'anonymous')
  assert.equal(requests.filter((entry) => entry.path.startsWith('/api/chat')).length, before)
  await page.close()
})

test('cancelling an in-flight login fences its delayed successful response', async () => {
  const page = await browser.newPage()
  let release
  let received = false
  const deferred = new Promise((resolve) => { release = resolve })
  await page.route('**/auth/v1/token?grant_type=password', async (route) => {
    const headers = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type, apikey, Authorization', 'Access-Control-Allow-Methods': 'POST, OPTIONS' }
    if (route.request().method() === 'OPTIONS') { await route.fulfill({ status: 204, headers }); return }
    received = true
    await deferred
    await route.fulfill({ status: 200, contentType: 'application/json', headers, body: JSON.stringify({
      access_token: 'late-token', refresh_token: 'late-refresh', expires_in: 300,
      user: { id: 'late-user', email: 'late@example.test' },
    }) }).catch(() => {})
  })
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
  await page.getByLabel('Email').fill('member@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  const deadline = Date.now() + 5_000
  while (!received && Date.now() < deadline) await new Promise((resolve) => setTimeout(resolve, 10))
  assert.equal(received, true)
  await page.locator('#auth-close').click()
  release()
  await page.waitForTimeout(100)
  assert.equal(await page.evaluate(() => window.LoomAIWorkspaceHost.identity.status), 'anonymous')
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_engage_identity:mixed.localhost:awi_pub_mixed:supabase')), null)
  assert.equal(await page.locator('#auth-password').inputValue(), '')
  await page.close()
})

test('same-owner refresh rotates provider tokens without losing private workspace state', async () => {
  const context = await browser.newContext()
  await context.addInitScript(() => {
    sessionStorage.setItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase', JSON.stringify({
      access_token: 'expired-access-token', refresh_token: 'verified-refresh-token', expires_at: Math.floor(Date.now() / 1000) - 1,
      user: { id: 'user-verified-1' },
    }))
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1', 'owned-conversation-confirmation-attachments')
  })
  const page = await context.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  const result = await page.evaluate(async () => ({
    token: await window.LoomAIWorkspaceHost.getIdentityAccessToken(),
    state: sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1'),
  }))
  assert.equal(result.token, 'refreshed-access-token')
  assert.equal(result.state, 'owned-conversation-confirmation-attachments')
  await context.close()
})

test('expiry without refresh proof clears private state and never reuses the old credential', async () => {
  const context = await browser.newContext()
  await context.addInitScript(() => {
    sessionStorage.setItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase', JSON.stringify({
      access_token: 'expired-access-token', expires_at: Math.floor(Date.now() / 1000) - 1, user: { id: 'expired-user' },
    }))
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:expired-user', 'private-state')
  })
  const page = await context.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.locator('#auth-dialog[open]').waitFor()
  assert.equal(await page.locator('#installed-workspace').count(), 0)
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:expired-user')), null)
  await context.close()
})

test('a refreshed credential for another owner is denied instead of inheriting the conversation', async () => {
  const context = await browser.newContext()
  await context.addInitScript(() => {
    sessionStorage.setItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase', JSON.stringify({
      access_token: 'expired-access-token', refresh_token: 'other-account-refresh', expires_at: Math.floor(Date.now() / 1000) - 1,
      user: { id: 'user-verified-1' },
    }))
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1', 'owner-one-state')
  })
  const page = await context.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.locator('#auth-dialog[open]').waitFor()
  assert.equal(await page.locator('#installed-workspace').count(), 0)
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1')), null)
  await context.close()
})

test('an explicit account switch clears previous-owner state and does not replay the request', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('mixed.localhost'))
  await page.locator('#installed-workspace').waitFor()
  await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
  await page.getByLabel('Email').fill('member@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  await page.evaluate(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_mixed:conversation:authenticated:user-verified-1', 'old-owner-state')
    sessionStorage.setItem('loomai_workspace:awi_pub_app_two:conversation:authenticated:user-verified-1', 'other-installation-state')
    window.LoomAIWorkspaceHost.requestSignIn()
  })
  const before = requests.filter((entry) => entry.path.startsWith('/api/chat')).length
  await page.getByLabel('Email').fill('other@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.waitForFunction(() => window.LoomAIWorkspaceHost?.identity?.subjectKey === 'user-other')
  const result = await page.evaluate(() => ({
    previous: sessionStorage.getItem('loomai_workspace:awi_pub_mixed:conversation:authenticated:user-verified-1'),
    otherInstallation: sessionStorage.getItem('loomai_workspace:awi_pub_app_two:conversation:authenticated:user-verified-1'),
  }))
  assert.equal(result.previous, null)
  assert.equal(result.otherInstallation, 'other-installation-state')
  assert.equal(requests.filter((entry) => entry.path.startsWith('/api/chat')).length, before)
  await page.close()
})

test('failed provider logout clears local authority but reports revocation as unconfirmed', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.getByLabel('Email').fill('member@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  await page.route('**/auth/v1/logout?scope=local', (route) => route.fulfill({
    status: route.request().method() === 'OPTIONS' ? 204 : 503,
    headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type, apikey, Authorization', 'Access-Control-Allow-Methods': 'POST, OPTIONS' },
  }))
  const failure = await page.evaluate(async () => {
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1', 'private-state')
    try { await window.LoomAIWorkspaceHost.signOut(); return null } catch (error) { return error.message }
  })
  assert.match(failure, /provider logout could not be confirmed/)
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase')), null)
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1')), null)
  assert.equal(await page.evaluate(() => typeof window.LoomAIWorkspaceHost), 'undefined')
  await page.close()
})

test('slow provider logout revokes local authority immediately and fails within the bounded timeout', async () => {
  const page = await browser.newPage()
  await page.goto(appUrl('secure.localhost'))
  await page.getByLabel('Email').fill('member@example.test')
  await page.getByLabel('Password').fill('verified-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#installed-workspace[data-identity="authenticated"]').waitFor()
  await page.route('**/auth/v1/logout?scope=local', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 10_000))
    try {
      await route.fulfill({
        status: 204,
        headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type, apikey, Authorization', 'Access-Control-Allow-Methods': 'POST, OPTIONS' },
      })
    } catch {
      // The browser is expected to abort the provider request first.
    }
  })
  const immediate = await page.evaluate(() => {
    sessionStorage.setItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1', 'private-state')
    window.__widgetDestroyed = false
    window.MaxMode = { destroy() { window.__widgetDestroyed = true } }
    window.__boundedLogoutFailure = null
    window.LoomAIWorkspaceHost.signOut().catch((error) => { window.__boundedLogoutFailure = error.message })
    return {
      widgetDestroyed: window.__widgetDestroyed,
      hostRemoved: typeof window.LoomAIWorkspaceHost === 'undefined',
      providerCredentialRemoved: sessionStorage.getItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase') === null,
      privateStateRemoved: sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1') === null,
    }
  })
  assert.deepEqual(immediate, {
    widgetDestroyed: true,
    hostRemoved: true,
    providerCredentialRemoved: true,
    privateStateRemoved: true,
  })
  await page.waitForFunction(() => typeof window.LoomAIWorkspaceHost === 'undefined')
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_engage_identity:secure.localhost:awi_pub_secure:supabase')), null)
  assert.equal(await page.evaluate(() => sessionStorage.getItem('loomai_workspace:awi_pub_secure:conversation:authenticated:user-verified-1')), null)
  await page.waitForFunction(() => typeof window.__boundedLogoutFailure === 'string', undefined, { timeout: 7_000 })
  assert.match(await page.evaluate(() => window.__boundedLogoutFailure), /provider logout could not be confirmed/)
  await page.close()
})

function app(installationId, displayName, interactionPolicy, profileCode) {
  return { installationId, displayName, interactionPolicy, profileCode }
}

function appUrl(hostname) {
  return `http://${hostname}:${shellPort}/`
}

async function platformHandler(request, response) {
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
    const body = await bodyJson(request)
    if (body.password === 'invalid-password') {
      json(response, 401, { message: 'Sign in failed.' })
      return
    }
    const switching = body.email === 'other@example.test'
    json(response, 200, {
      access_token: switching ? 'other-access-token' : 'verified-access-token',
      refresh_token: switching ? 'other-refresh-token' : 'verified-refresh-token',
      expires_in: 300,
      user: { id: switching ? 'user-other' : 'user-verified-1', email: switching ? 'other@example.test' : 'member@example.test' },
    })
    return
  }
  if (request.url === '/auth/v1/token?grant_type=refresh_token' && request.method === 'POST') {
    const body = await bodyJson(request)
    if (body.refresh_token === 'verified-refresh-token' || body.refresh_token === 'other-account-refresh') {
      json(response, 200, {
        access_token: 'refreshed-access-token', refresh_token: 'rotated-refresh-token', expires_in: 300,
        user: { id: body.refresh_token === 'other-account-refresh' ? 'user-other' : 'user-verified-1', email: 'member@example.test' },
      })
      return
    }
    json(response, 401, { message: 'Refresh token revoked' })
    return
  }
  if (request.url === '/auth/v1/user') {
    json(response, 200, { id: 'user-verified-1', email: 'member@example.test' })
    return
  }
  if (request.url === '/auth/v1/logout?scope=local' && request.method === 'POST') {
    response.writeHead(204).end()
    return
  }
  json(response, 404, { message: 'Not found' })
}

async function bodyJson(request) {
  let body = ''
  for await (const chunk of request) body += chunk
  return JSON.parse(body || '{}')
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
