import assert from 'node:assert/strict'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium } from 'playwright'

const origin = process.env.WORKSPACE_PRIVATE_ORIGIN
const otherRuntime = process.env.WORKSPACE_OTHER_RUNTIME
const otherOrigin = process.env.WORKSPACE_OTHER_ORIGIN
assert(origin && otherRuntime && otherOrigin && process.env.WORKSPACE_AUTH_CREDENTIAL_FILE, 'Set reviewed private app, other runtime, its allowed origin, and synthetic identity file.')
const credentials = JSON.parse(await readFile(process.env.WORKSPACE_AUTH_CREDENTIAL_FILE, 'utf8'))
const output = resolve(process.env.WORKSPACE_PRIVATE_OUTPUT || '/private/tmp/loomai-engage-private-live-gate')
await mkdir(output, { recursive: true })
const report = { checkedAt: new Date().toISOString(), origin, checks: {}, errors: [] }
const browser = await chromium.launch()
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(120_000)
  let brokerToken
  let runtimeOrigin
  let messagePosts = 0
  page.on('request', request => {
    if (request.method() === 'POST' && /\/conversations\/[^/]+\/messages$/.test(new URL(request.url()).pathname)) messagePosts++
  })
  await page.goto(origin)
  const dialog = page.getByRole('dialog')
  await dialog.waitFor({ state: 'visible' })
  assert.equal(await page.locator('textarea').count(), 0)
  assert.equal(messagePosts, 0)
  report.checks.privateEntryRequiresSignIn = true
  await dialog.getByLabel('Email').fill(credentials.email)
  await dialog.getByLabel('Password').fill('invalid-password')
  await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.locator('#auth-error').waitFor({ state: 'visible' })
  assert.equal(await page.locator('textarea').count(), 0)
  report.checks.failedLoginDidNotMountWorkspace = true
  const brokerResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/public/auth/runtime-token')
  await dialog.getByLabel('Password').fill(credentials.password)
  await dialog.getByRole('button', { name: 'Sign in', exact: true }).click()
  const broker = await brokerResponse
  assert.equal(broker.status(), 200)
  const identity = await broker.json()
  brokerToken = identity.accessToken
  runtimeOrigin = new URL(broker.url()).origin
  assert.equal(identity.subjectId, credentials.userId)
  assert(identity.grantedScopes.includes('chat:messages'))
  assert(identity.grantedScopes.every(scope => scope.startsWith('chat:') && !scope.includes('operator') && !scope.includes('admin')))
  await page.locator('textarea').waitFor({ state: 'visible' })
  assert.equal(messagePosts, 0)
  report.checks.realProviderMountedPrivateWorkspaceWithoutReplay = true
  const headers = { Origin: origin, Authorization: `Bearer ${brokerToken}` }
  const auth = await page.request.get(`${runtimeOrigin}/api/chat/me/auth-context`, { headers })
  assert.equal(auth.status(), 200)
  const bootstrap = await page.request.post(`${runtimeOrigin}/api/public/chat/session`, { headers: { Origin: origin }, data: {} })
  assert([401, 403, 404].includes(bootstrap.status()))
  const query = await page.request.post(`${runtimeOrigin}/api/chat/me/conversations/private-entry-denial-probe/messages`, {
    headers: { Origin: origin, 'Idempotency-Key': 'private-entry-denial-probe' },
    data: { query: 'Private entry authorization probe' },
  })
  report.anonymousMessageStatus = query.status()
  assert([401, 403].includes(query.status()), `Canonical anonymous message returned HTTP ${query.status()}`)
  report.checks.anonymousBootstrapAndQueryDenied = true
  const foreign = await page.request.get(`${otherRuntime}/api/chat/me/auth-context`, {
    headers: { Origin: otherOrigin, Authorization: `Bearer ${brokerToken}` },
  })
  assert([401, 403].includes(foreign.status()))
  report.checks.otherDeploymentRejectedToken = true
  await page.screenshot({ path: resolve(output, 'private-desktop.png') })
  await page.evaluate(() => window.LoomAIWorkspaceHost.signOut())
  await dialog.waitFor({ state: 'visible' })
  assert.equal(await page.locator('textarea').count(), 0)
  assert.equal(await page.evaluate(() => Object.keys(sessionStorage).some(key => key.startsWith('loomai_engage_identity:'))), false)
  report.checks.logoutRestoredEntryGate = true
  report.runtimeOrigin = runtimeOrigin
} catch (error) {
  report.errors.push(error.message)
  process.exitCode = 1
} finally {
  await browser.close()
  await writeFile(resolve(output, 'report.json'), `${JSON.stringify(report, null, 2)}\n`)
  console.log(JSON.stringify(report, null, 2))
}
