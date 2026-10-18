import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdtemp, readFile, writeFile } from 'node:fs/promises'
import http from 'node:http'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { dirname, extname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

// Dedicated browser gate, intentionally outside test/*.test.mjs and image watch paths.
// From the repo root: node --test verification-support/productization-01036/auth-acceptance-browser.test.mjs
// Requires existing workspace npm dependencies and widget Playwright Chromium; never installs them.
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const widgetRoot = resolve(repoRoot, 'max-mode-widget')
const { chromium } = createRequire(resolve(widgetRoot, 'package.json'))('playwright')
const output = await mkdtemp(resolve(tmpdir(), '01036-p4-auth-'))
const installationId = 'awi_pub_01036010360103601036010360103601'
const assignmentRevision = revision('p4-auth-fixture-assignment')
const namespace = (identity) => `${installationId}:public-runtime-anonymous:${assignmentRevision}:${identity}`
const providerKey = `loomai_engage_identity:127.0.0.1:${installationId}:supabase`
const requirement = {
  schemaVersion: 'loomai-workspace-authentication-requirement-v1',
  reasonCode: 'AUTHENTICATION_REQUIRED',
  retryPolicy: 'EXPLICIT_NEW_REQUEST',
  authenticationProfile: 'supabase',
  capability: 'fixture-private-record',
}
const report = {
  schemaVersion: '01036-p4-offline-auth-acceptance-v1',
  output,
  evidenceScope: 'Real local shell, installer, generic pack, widget, storage and HTTP transport; fixture provider/broker/runtime only.',
  liveGaps: [
    'Real runtime protected-capability selection, authorization, confirmation/review and effects.',
    'Real provider cancellation, credential issuance, expiry, refresh and measured bearer revocation latency.',
    'Hosted exact-origin/TLS, private-context ownership and retained handover lifecycle.',
  ],
  builds: [],
  scenarios: [],
}
let server
let browser
let origin
let installer
let widget
let pack
let fixture

test.before(async () => {
  console.log(`P4 offline auth evidence: ${output}`)
  for (const [name, root, config] of [
    ['widget', widgetRoot, 'vite.config.iife.ts'],
    ['installer', widgetRoot, 'vite.config.installer.ts'],
    ['pack', resolve(repoRoot, 'experience-packs/generic-workspace-experience'), 'vite.config.iife.ts'],
    ['shell', resolve(repoRoot, 'workspace-apps/standalone-ai-workspace'), 'vite.config.ts'],
  ]) {
    const outDir = resolve(output, name)
    const result = spawnSync(process.execPath, [
      resolve(widgetRoot, 'node_modules/vite/bin/vite.js'), 'build', '--config', config,
      '--outDir', outDir, '--emptyOutDir',
    ], { cwd: root, encoding: 'utf8', timeout: 180_000, maxBuffer: 8 * 1024 * 1024 })
    const logPath = resolve(output, `${name}-build.log`)
    await writeFile(logPath, `${result.stdout || ''}\n${result.stderr || ''}`)
    report.builds.push({ name, outDir, status: result.status, logPath })
    assert.equal(result.status, 0, `${name} build failed; diagnostics: ${logPath}`)
  }
  installer = await readFile(resolve(output, 'installer/ai-workspace-installer.iife.js'), 'utf8')
  widget = await readFile(resolve(output, 'widget/max-mode-widget.iife.js'), 'utf8')
  pack = await readFile(resolve(output, 'pack/generic-workspace-experience.iife.js'), 'utf8')
  report.assetHashes = { installer: revision(installer), widget: revision(widget), pack: revision(pack) }
  report.untouchedToastSourceHash = revision(await readFile(resolve(widgetRoot, 'src/ui/toast-container.tsx')))
  server = http.createServer((request, response) => {
    void handler(request, response).catch((error) => {
      fixture?.serverErrors.push(error.stack)
      json(response, 500, { message: 'Fixture handler failed' })
    })
  })
  await new Promise((done, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', done)
  })
  origin = `http://127.0.0.1:${server.address().port}`
  browser = await chromium.launch({ headless: true })
}, { timeout: 240_000 })

test.after(async () => {
  await browser?.close()
  if (server) await new Promise((done) => server.close(done))
  report.status = report.scenarios.length && report.scenarios.every((entry) => entry.status === 'PASS') ? 'PASS' : 'FAIL'
  await writeFile(resolve(output, 'report.json'), JSON.stringify(report, null, 2))
  console.log(`P4 offline auth report: ${resolve(output, 'report.json')}`)
})

test('typed protected 401, cancellation and failed login preserve the actual permitted anonymous conversation', async () => {
  await scenario('challenge-cancel-failure', {}, async (page) => {
    const first = await send(page, 'public-before-challenge')
    await page.evaluate(() => window.MaxMode.attachItem({ type: 'document', data: { id: 'anonymous-evidence', title: 'Anonymous evidence' } }))
    const challenge = await send(page, 'protected-anonymous-context', 401)
    assert.equal(challenge.conversationId, first.conversationId)
    assert.deepEqual(challenge.authenticationRequirement, requirement)
    await page.locator('#auth-dialog[open]').waitFor()
    assert.equal(fixture.effects.length, 0)
    assert.equal(fixture.queries.at(-1).owner, first.owner)
    assert.match(JSON.stringify(fixture.queries.at(-1).body.attachments), /anonymous-evidence/)
    const beforeCancel = await identityState(page, 'anonymous')
    const queryCount = fixture.queries.length
    await page.locator('#auth-close').click()
    await page.locator('#auth-dialog[open]').waitFor({ state: 'hidden' })
    assert.deepEqual(await identityState(page, 'anonymous'), beforeCancel)
    assert.equal(await page.evaluate(() => window.LoomAIWorkspaceHost.identity.status), 'anonymous')
    assert.equal(fixture.passwordRequests, 0)
    await assertNoReplay(page, queryCount)

    await send(page, 'protected-again', 401)
    await page.locator('#auth-dialog[open]').waitFor()
    const beforeFailure = await identityState(page, 'anonymous')
    await submitLogin(page, 'owner-a@example.test', 'incorrect-fixture-password')
    await page.locator('#auth-error:not(.hidden)').waitFor()
    assert.deepEqual(await identityState(page, 'anonymous'), beforeFailure)
    assert.equal(await page.evaluate((key) => sessionStorage.getItem(key), providerKey), null)
    assert.equal(fixture.effects.length, 0)
    await assertNoReplay(page, queryCount + 1)
    await page.locator('#auth-close').click()
    const followUp = await send(page, 'public-after-failure')
    assert.equal(followUp.conversationId, first.conversationId)
    assert.equal(followUp.owner, first.owner)
    assert.equal(fixture.sessions, 1, 'Cancellation/failure replaced the anonymous owner')
  })
})

test('successful challenge login discards anonymous authority and queues; only a new request reaches a fresh conversation', async () => {
  await scenario('login-new-conversation-no-replay', {}, async (page) => {
    await send(page, 'public-before-login')
    await page.evaluate(() => window.MaxMode.attachItem({ type: 'document', data: { id: 'anonymous-evidence', title: 'Anonymous evidence' } }))
    const challenge = await send(page, 'protected-anonymous-request', 401)
    await page.locator('#auth-dialog[open]').waitFor()
    await seedPendingAuthority(page, 'anonymous')
    const count = fixture.queries.length
    await login(page)
    await assertNoReplay(page, count)
    assert.deepEqual(await identityState(page, 'anonymous'), {})
    assert.equal(await page.getByText('public-before-login', { exact: true }).count(), 0)
    await assertOtherInstallation(page)
    await page.reload()
    await workspaceReady(page, 'owner-a')
    await assertNoReplay(page, count)
    const authorized = await send(page, 'protected-explicit-new-request')
    assert.notEqual(authorized.conversationId, challenge.conversationId)
    assert.equal(authorized.owner, 'owner-a')
    const payload = fixture.queries.at(-1).body
    assert.equal(payload.attachments, undefined)
    assert.doesNotMatch(JSON.stringify(payload), /anonymous-evidence|stale-target|stale-receipt|queued-protected-request/)
    assert.equal(fixture.effects.length, 1)
    assert.equal(fixture.effects[0].query, 'protected-explicit-new-request')
    assert.equal(fixture.effects[0].owner, 'owner-a')
  })
})

test('cancelling an in-flight provider login fences a delayed success without replacing the anonymous owner', async () => {
  await scenario('in-flight-cancel-no-late-login', {}, async (page) => {
    const previous = await send(page, 'public-before-in-flight-login')
    await send(page, 'protected-before-in-flight-login', 401)
    await page.locator('#auth-dialog[open]').waitFor()
    const before = await identityState(page, 'anonymous')
    let release
    const gate = new Promise((done) => { release = done })
    fixture.passwordGate = gate
    const requested = page.waitForRequest((request) => request.url().includes('grant_type=password') && request.method() === 'POST')
    try {
      await submitLogin(page, 'owner-a@example.test', 'valid-fixture-password')
      await requested
      await page.locator('#auth-close').click()
      release()
      await assertNoReplay(page, 2)
      assert.deepEqual(await identityState(page, 'anonymous'), before)
      assert.equal(await page.evaluate(() => window.LoomAIWorkspaceHost.identity.status), 'anonymous')
      assert.equal(await page.evaluate((key) => sessionStorage.getItem(key), providerKey), null)
      assert.equal(await page.locator('#auth-password').inputValue(), '')
      assert.equal(fixture.brokerRequests, 0)
      assert.equal(fixture.effects.length, 0)
      const followUp = await send(page, 'public-after-in-flight-cancel')
      assert.equal(followUp.conversationId, previous.conversationId)
      assert.equal(followUp.owner, previous.owner)
      assert.equal(fixture.sessions, 1)
    } finally {
      release()
    }
  })
})

test('account switch and logout remove actual private state without replay or cross-owner conversation reuse', async () => {
  await scenario('account-switch-logout', {}, async (page) => {
    const anonymous = await send(page, 'public-original-owner')
    await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
    await login(page)
    const first = await send(page, 'protected-owner-a')
    await seedPendingAuthority(page, 'authenticated:owner-a')
    await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
    const beforeSwitch = fixture.queries.length
    await login(page, 'owner-b')
    await assertNoReplay(page, beforeSwitch)
    assert.deepEqual(await identityState(page, 'authenticated:owner-a'), {})
    assert.equal(await page.getByText('protected-owner-a', { exact: true }).count(), 0)
    const second = await send(page, 'protected-owner-b')
    assert.equal(second.owner, 'owner-b')
    assert.notEqual(second.conversationId, first.conversationId)
    assert.equal(fixture.queries.at(-1).body.attachments, undefined)
    await page.reload()
    await workspaceReady(page, 'owner-b')
    await assertNoReplay(page, beforeSwitch + 1)
    const sameOwner = await send(page, 'public-owner-b-follow-up')
    assert.equal(sameOwner.conversationId, second.conversationId)
    await page.evaluate(() => { void window.LoomAIWorkspaceHost.signOut() })
    await workspaceReady(page, 'anonymous')
    await assertNoReplay(page, beforeSwitch + 2)
    assert.deepEqual(await identityState(page, 'authenticated:owner-a'), {})
    assert.deepEqual(await identityState(page, 'authenticated:owner-b'), {})
    assert.equal(await page.evaluate((key) => sessionStorage.getItem(key), providerKey), null)
    assert.equal(await page.getByText('protected-owner-b', { exact: true }).count(), 0)
    await assertOtherInstallation(page)
    const afterLogout = await send(page, 'public-after-logout')
    assert.notEqual(afterLogout.conversationId, first.conversationId)
    assert.notEqual(afterLogout.conversationId, second.conversationId)
    assert.notEqual(afterLogout.owner, anonymous.owner)
    assert.match(afterLogout.owner, /^anonymous-/)
    assert.equal(fixture.logoutRequests, 1)
  })
})

test('same-owner provider and broker renewal retain the real conversation and do not resend its earlier operation', async () => {
  await scenario('same-owner-renewal', {}, async (page) => {
    await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
    await login(page)
    const first = await send(page, 'protected-before-renewal')
    const brokerCount = fixture.brokerRequests
    await page.clock.setSystemTime(new Date(Date.now() + 280_000))
    assert.deepEqual(await page.evaluate(() => Promise.all([
      window.LoomAIWorkspaceHost.getIdentityAccessToken(),
      window.LoomAIWorkspaceHost.getIdentityAccessToken(),
    ])), ['provider-owner-a-refreshed', 'provider-owner-a-refreshed'])
    assert.equal(fixture.refreshRequests, 1)
    await assertNoReplay(page, 1)
    const second = await send(page, 'public-after-renewal')
    assert.equal(second.conversationId, first.conversationId)
    assert.equal(second.owner, 'owner-a')
    assert.equal(fixture.brokerRequests, brokerCount + 1)
    assert.equal(fixture.effects.length, 1)
  })
})

for (const [policy, invalidation] of [
  ['public-with-protected-capabilities', 'missing-refresh'],
  ['public-with-protected-capabilities', 'fixture-revoked-refresh'],
  ['login-required', 'fixture-revoked-refresh'],
]) {
  test(`${policy}: ${invalidation} at expiry cannot restore or send the previous private conversation`, async () => {
    await scenario(`expiry-${policy}-${invalidation}`, { policy }, async (page) => {
      if (policy !== 'login-required') await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
      await login(page)
      const previous = await send(page, 'protected-before-expiry')
      await seedPendingAuthority(page, 'authenticated:owner-a')
      await page.evaluate(({ key, invalidation }) => {
        const session = JSON.parse(sessionStorage.getItem(key))
        session.expires_at = Math.floor(Date.now() / 1000) - 60
        if (invalidation === 'missing-refresh') delete session.refresh_token
        else session.refresh_token = 'fixture-revoked-refresh'
        sessionStorage.setItem(key, JSON.stringify(session))
      }, { key: providerKey, invalidation })
      const before = fixture.queries.length
      await page.reload()
      if (policy === 'login-required') {
        await page.locator('#auth-dialog[open]').waitFor()
        assert.equal(await page.locator('textarea').count(), 0)
        assert.equal(await page.evaluate(() => typeof window.LoomAIWorkspaceHost), 'undefined')
      } else {
        await workspaceReady(page, 'anonymous')
      }
      await assertNoReplay(page, before)
      assert.deepEqual(await identityState(page, 'authenticated:owner-a'), {})
      assert.equal(await page.evaluate((key) => sessionStorage.getItem(key), providerKey), null)
      assert.equal(fixture.refreshRequests, invalidation === 'missing-refresh' ? 0 : 1)
      assert.equal(fixture.effects.length, 1, 'An invalidated owner executed another protected effect')
      await assertOtherInstallation(page)
      if (policy !== 'login-required') {
        const fresh = await send(page, 'public-after-expiry')
        assert.notEqual(fresh.conversationId, previous.conversationId)
        assert.match(fresh.owner, /^anonymous-/)
        assert.equal(fixture.queries.at(-1).body.attachments, undefined)
      }
    })
  })
}

test('broker denial after runtime-token expiry sends no private request and makes no automatic retry', async () => {
  await scenario('broker-denial-no-private-send', {}, async (page) => {
    await page.evaluate(() => window.LoomAIWorkspaceHost.requestSignIn())
    await login(page)
    await send(page, 'protected-before-broker-denial')
    fixture.denyBroker = true
    const brokers = fixture.brokerRequests
    await page.clock.setSystemTime(new Date(Date.now() + 280_000))
    await page.locator('textarea').fill('protected-must-not-reach-runtime')
    await page.locator('textarea').press('Enter')
    await page.getByText('Sorry, I encountered an error processing your request.', { exact: true }).waitFor()
    await assertNoReplay(page, 1)
    assert.equal(fixture.brokerRequests, brokers + 1)
    assert.equal(fixture.effects.length, 1)
    // A broker 401 is not proof the still-valid provider bearer was revoked or the transcript erased.
    assert.equal(await page.evaluate(() => window.LoomAIWorkspaceHost.identity.subjectKey), 'owner-a')
  })
})

async function scenario(name, options, run) {
  fixture = {
    policy: options.policy || 'public-with-protected-capabilities',
    queries: [], effects: [], sessions: 0, passwordRequests: 0, refreshRequests: 0,
    brokerRequests: 0, logoutRequests: 0, denyBroker: false,
    owners: new Map(), credentials: new Map(), unexpected: [], serverErrors: [],
  }
  const entry = { name, status: 'RUNNING' }
  report.scenarios.push(entry)
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce', serviceWorkers: 'block' })
  const pageErrors = []
  let page
  try {
    await context.route('**/*', (route) => {
      if (new URL(route.request().url()).origin === origin) return route.continue()
      fixture.unexpected.push(route.request().url())
      return route.abort('blockedbyclient')
    })
    await context.addInitScript(() => {
      window.__p4InstallerEvents = []
      for (const type of ['loading', 'ready', 'error', 'authentication-required']) {
        window.addEventListener(`loomai:workspace-${type}`, (event) => window.__p4InstallerEvents.push({ type, detail: event.detail }))
      }
    })
    page = await context.newPage()
    page.setDefaultTimeout(10_000)
    page.on('pageerror', (error) => pageErrors.push(error.message))
    await page.goto(origin)
    if (fixture.policy === 'login-required') await page.locator('#auth-dialog[open]').waitFor()
    else await workspaceReady(page, 'anonymous')
    await run(page)
    assert.deepEqual(pageErrors, [])
    assert.deepEqual(fixture.serverErrors, [])
    assert.deepEqual(fixture.unexpected, [], 'Unexpected/off-loopback browser traffic')
    await page.screenshot({ path: resolve(output, `${name}.png`) })
    entry.status = 'PASS'
  } catch (error) {
    entry.status = 'FAIL'
    entry.error = error.stack
    if (page) {
      entry.pageState = await page.evaluate(() => ({
        text: document.body.innerText,
        events: window.__p4InstallerEvents,
        identity: window.LoomAIWorkspaceHost?.identity,
      })).catch(() => null)
      await page.screenshot({ path: resolve(output, `${name}-failed.png`) }).catch(() => {})
    }
    await writeFile(resolve(output, `${name}-failure.json`), JSON.stringify(entry, null, 2))
    throw error
  } finally {
    Object.assign(entry, {
      queries: fixture.queries, effects: fixture.effects, anonymousSessions: fixture.sessions,
      passwordRequests: fixture.passwordRequests, refreshRequests: fixture.refreshRequests,
      brokerRequests: fixture.brokerRequests, logoutRequests: fixture.logoutRequests,
      pageErrors, unexpectedRequests: fixture.unexpected, serverErrors: fixture.serverErrors,
    })
    await context.close()
  }
}

async function workspaceReady(page, subject) {
  await page.waitForFunction((subject) => {
    const identity = window.LoomAIWorkspaceHost?.identity
    return window.__p4InstallerEvents.some((event) => event.type === 'error') || (window.LoomAIWorkspace?.getController()
      && (subject === 'anonymous' ? identity?.status === 'anonymous' : identity?.subjectKey === subject)
    )
  }, subject)
  assert.deepEqual(await page.evaluate(() => window.__p4InstallerEvents.filter((event) => event.type === 'error')), [], 'Installer failed')
  await page.locator('textarea').waitFor()
}

async function send(page, query, status = 200) {
  const received = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/runtime/query' && response.request().method() === 'POST')
  await page.locator('textarea').fill(query)
  await page.locator('textarea').press('Enter')
  const response = await received
  assert.equal(response.status(), status)
  const result = await response.json()
  await page.getByText(result.answer, { exact: true }).last().waitFor()
  await page.waitForFunction(({ prefix, id }) => Object.keys(sessionStorage).some((key) => {
    if (!key.startsWith(prefix)) return false
    return JSON.parse(sessionStorage.getItem(key)).conversationId === id
  }), { prefix: `maxmode_widget_state:${installationId}:`, id: result.conversationId })
  return result
}

async function submitLogin(page, email, password) {
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.locator('#password-sign-in').click()
}

async function login(page, subject = 'owner-a') {
  await submitLogin(page, `${subject}@example.test`, 'valid-fixture-password')
  await workspaceReady(page, subject)
}

async function identityState(page, identity) {
  return page.evaluate(({ installationId, identity }) => Object.fromEntries(Object.keys(sessionStorage)
    .filter((key) => key.includes(`:${installationId}:`) && key.endsWith(`:${identity}`))
    .sort().map((key) => [key, sessionStorage.getItem(key)])), { installationId, identity })
}

async function seedPendingAuthority(page, identity) {
  await page.evaluate(({ namespace }) => {
    sessionStorage.setItem(`maxmode_widget_pending_prompts:${namespace}`, JSON.stringify([{
      id: 'stale-prompt', message: 'queued-protected-request', open: true,
      requestContext: { targetHandle: 'stale-target', confirmationReceipt: 'stale-receipt' },
    }]))
    sessionStorage.setItem(`maxmode_widget_pending_attachments:${namespace}`, JSON.stringify([
      { type: 'document', data: { id: 'stale-target', title: 'Stale private evidence' } },
    ]))
    sessionStorage.setItem('maxmode_widget_state:awi_pub_other_installation:authenticated:other-owner', 'preserve-unrelated-installation')
  }, { namespace: namespace(identity) })
}

async function assertOtherInstallation(page) {
  assert.equal(await page.evaluate(() => sessionStorage.getItem('maxmode_widget_state:awi_pub_other_installation:authenticated:other-owner')), 'preserve-unrelated-installation')
}

async function assertNoReplay(page, expectedCount) {
  // Bound the observation after a completed mount/reload; this is not an infinite no-replay claim.
  await page.waitForTimeout(350)
  assert.equal(fixture.queries.length, expectedCount, 'A request was replayed without a new user submission')
}

async function handler(request, response) {
  const url = new URL(request.url, origin)
  const path = url.pathname
  if (path === '/runtime-config.js') return source(response, `window.__LOOMAI_ENGAGE_CONFIG__ = { platformApiBaseUrl: ${JSON.stringify(origin)} };`)
  if (path === '/install.js') return source(response, installer)
  if (path === '/widget.js') return source(response, widget)
  if (path === '/pack.js') return source(response, pack)
  if (path === '/api/public/ai-workspace/apps/bootstrap') return json(response, 200, {
    schemaVersion: 'loomai-ai-workspace-app-bootstrap-v1', installationId, displayName: 'P4 Auth Fixture',
    interactionPolicy: fixture.policy,
    delivery: { mode: 'standalone', closeAllowed: false, appUrl: origin },
    authentication: { profileCode: 'supabase', profileVersion: '1.0.0', configuration: {
      projectUrl: origin, publishableKey: 'public-fixture-key', methods: ['email-password'],
    } },
    installer: { url: `${origin}/install.js`, integrity: integrity(installer), version: '1.0.0' },
  })
  if (path === `/api/public/ai-workspace/installations/${installationId}/manifest`) return json(response, 200, manifest())
  if (path === '/auth/v1/token' && url.searchParams.get('grant_type') === 'password' && request.method === 'POST') {
    fixture.passwordRequests++
    const body = await bodyJson(request)
    if (body.password !== 'valid-fixture-password') return json(response, 401, { message: 'Fixture sign-in denied.' })
    const subject = body.email === 'owner-b@example.test' ? 'owner-b' : 'owner-a'
    if (fixture.passwordGate) await fixture.passwordGate
    return json(response, 200, providerSession(subject))
  }
  if (path === '/auth/v1/token' && url.searchParams.get('grant_type') === 'refresh_token' && request.method === 'POST') {
    fixture.refreshRequests++
    const body = await bodyJson(request)
    if (body.refresh_token === 'fixture-revoked-refresh') return json(response, 401, { message: 'Fixture refresh denied.' })
    assert.match(body.refresh_token, /^refresh-owner-[ab]$/)
    return json(response, 200, providerSession(body.refresh_token.slice('refresh-'.length), true))
  }
  if (path === '/auth/v1/logout' && request.method === 'POST') {
    fixture.logoutRequests++
    return response.writeHead(204).end()
  }
  if (path === '/broker' && request.method === 'POST') {
    fixture.brokerRequests++
    if (fixture.denyBroker) return json(response, 401, { message: 'Fixture credential denied.' })
    const body = await bodyJson(request)
    assert.deepEqual(body, { schemaVersion: 'loomai-workspace-runtime-token-request-v1' })
    const match = /^Bearer provider-(owner-[ab])(?:-refreshed)?$/.exec(request.headers.authorization || '')
    if (!match) return json(response, 401, { message: 'Fixture provider identity missing.' })
    const token = `runtime-fixture-${match[1]}-${fixture.brokerRequests}`
    fixture.credentials.set(token, match[1])
    return json(response, 200, {
      schemaVersion: 'loomai-workspace-runtime-token-v1', tokenType: 'Bearer', accessToken: token,
      subjectId: match[1], expiresAt: new Date(Date.now() + (fixture.brokerRequests === 1 ? 300_000 : 900_000)).toISOString(),
    })
  }
  if (path === '/api/runtime/session' && request.method === 'POST') {
    const owner = `anonymous-${++fixture.sessions}`
    const token = `fixture-owner-proof-${owner}`
    fixture.credentials.set(token, owner)
    return json(response, 200, { token, sessionId: owner, expiresAt: new Date(Date.now() + 900_000).toISOString() })
  }
  if (path.startsWith('/api/runtime/')) {
    const token = (request.headers.authorization || '').replace(/^Bearer /, '')
    const owner = fixture.credentials.get(token)
    if (!owner) return json(response, 401, { message: 'Fixture runtime credential denied.' })
    if (path === '/api/runtime/auth-context') return json(response, 200, {
      subjectId: owner, subjectType: owner.startsWith('anonymous-') ? 'ANONYMOUS_SESSION' : 'USER',
      authMode: 'PUBLIC_RUNTIME_ANONYMOUS', grantedScopes: [],
    })
    if (path === '/api/runtime/shell-config') return json(response, 200, { success: true, allowedConversationModes: ['executor'], starterPrompts: [] })
    if (path === '/api/runtime/suggestions') return json(response, 200, { success: true, suggestions: [] })
    if (path === '/api/runtime/conversations') return json(response, 200, { conversations: [], total: 0 })
    if (path === '/api/runtime/query' && request.method === 'POST') {
      const body = await bodyJson(request)
      const conversationId = body.conversationId || `fixture-conversation-${fixture.owners.size + 1}`
      fixture.queries.push({ owner, body })
      const previous = fixture.owners.get(conversationId)
      if (previous && previous !== owner) return json(response, 403, { message: 'Fixture conversation owner mismatch' })
      fixture.owners.set(conversationId, owner)
      if (body.query.startsWith('protected') && owner.startsWith('anonymous-')) return json(response, 401, {
        success: false, type: 'ERROR', answer: 'Protected fixture capability blocked.', conversationId, owner,
        authenticationRequirement: requirement,
      })
      if (body.query.startsWith('protected')) fixture.effects.push({ owner, query: body.query, conversationId })
      return json(response, 200, {
        success: true, type: 'INFORMATION_PROVIDED', answer: `Fixture reply: ${body.query}`,
        conversationId, owner, sources: [], actions: [], suggestions: [],
      })
    }
    fixture.unexpected.push(`${request.method} ${path}`)
    return json(response, 404, { message: 'Unknown fixture runtime route' })
  }
  if (path === '/favicon.ico') return response.writeHead(204).end()
  const file = path === '/' ? 'index.html' : path.replace(/^\//, '')
  if (file === 'index.html' || /^assets\/[a-zA-Z0-9_.-]+$/.test(file)) {
    const data = await readFile(resolve(output, 'shell', file))
    response.writeHead(200, { 'Content-Type': ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' })[extname(file)] })
    return response.end(data)
  }
  fixture.unexpected.push(`${request.method} ${path}`)
  return json(response, 404, { message: 'Unknown fixture route' })
}

function manifest() {
  return {
    schemaVersion: 'loomai-ai-workspace-installation-v2', installationId,
    manifestRevision: revision('p4-manifest'), assignmentRevision, generatedAt: new Date().toISOString(), cacheTtlSeconds: 30,
    delivery: { mode: 'standalone', closeAllowed: false },
    authentication: { interactionPolicy: fixture.policy, profileCode: 'supabase', profileVersion: '1.0.0', configurationSchemaVersion: '1.0.0', configuration: {} },
    connection: {
      mode: 'public-runtime-anonymous', handler: 'direct-public-runtime', profileCode: 'fixture-mixed', profileVersion: '1.0.0', runtimeBaseUrl: origin,
      anonymousBootstrap: { url: '/api/runtime/session', renewUrl: '/api/runtime/session/renew', authorizationHeader: 'Authorization', tokenScheme: 'Bearer' },
      credentialBroker: { url: `${origin}/broker`, method: 'POST', credentials: 'omit', externalAuthorizationHeader: 'Authorization', responseSchemaVersion: 'loomai-workspace-runtime-token-v1' },
      routes: { queryUrl: '/api/runtime/query', suggestionsUrl: '/api/runtime/suggestions', authContextUrl: '/api/runtime/auth-context', shellConfigUrl: '/api/runtime/shell-config', conversationsUrl: '/api/runtime/conversations', conversationItemUrlTemplate: '/api/runtime/conversations/{conversationId}' },
    },
    workspace: {
      asset: { version: '1.1.0', url: `${origin}/widget.js`, integrity: integrity(widget) },
      experiencePack: { code: 'generic-workspace', version: '1.0.0', url: `${origin}/pack.js`, integrity: integrity(pack) },
      configuration: { app: { assistantLabel: 'P4 Fixture' }, capabilities: { currentPage: false }, copy: { starterSuggestions: [] } },
    },
  }
}

function providerSession(subject, refreshed = false) {
  return { access_token: `provider-${subject}${refreshed ? '-refreshed' : ''}`, refresh_token: `refresh-${subject}`, expires_in: 300, user: { id: subject, email: `${subject}@example.test` } }
}

async function bodyJson(request) {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

function revision(value) { return `sha256:${createHash('sha256').update(value).digest('hex')}` }
function integrity(value) { return `sha384-${createHash('sha384').update(value).digest('base64')}` }
function source(response, body) { response.writeHead(200, { 'Content-Type': 'text/javascript' }).end(body) }
function json(response, status, body) { response.writeHead(status, { 'Content-Type': 'application/json' }).end(JSON.stringify(body)) }
