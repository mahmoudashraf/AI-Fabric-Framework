import assert from 'node:assert/strict'
import { mkdtemp, writeFile } from 'node:fs/promises'
import { homedir, tmpdir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { chromium } from 'playwright'

const CONSOLE = 'https://console.loomai.pro'
const BRAIN = 'dep-3623c3f2'
const FIXTURE = 'https://mcp-01069.46.224.145.148.sslip.io'
const ROOT = `/api/deployments/${BRAIN}/mcp-connections`
const helper = join(homedir(), '.codex/private/loomai-mcp-01069/api.mjs')

// Nothing above this gate reads private auth material, starts a browser or makes a request.
if (!process.argv.includes('--run-live')) {
  process.stdout.write(`Dry run only. No authentication, network requests or live changes performed.\nAfter MAIN confirms rollout: node src/mcp/hosted.browser.mjs --run-live\nConsole: ${CONSOLE}\nCanary: ${BRAIN}\nSynthetic services: ${FIXTURE}/public/mcp and /business/mcp\nScope: connection metadata, discovery, draft import/review/undo, target binding and disconnect. No infrastructure, publish/apply, inference or tool execution.\n`)
  process.exit(0)
}

const evidence = await mkdtemp(join(tmpdir(), 'loomai-mcp-hosted-ui-'))
const result = { deploymentId: BRAIN, startedAt: new Date().toISOString(), checks: [], api: [], createdConnectionIds: [], cleanupIssues: [], blockedMutations: [], browserErrorCount: 0 }
const created = [], pendingImports = []
let auth, browser, context, page, originalActions, phase = 'customer authentication', failed = false
const commandPath = path => path === ROOT || new RegExp(`^${ROOT}/[^/]+(?:/(?:discover|import|authorize|disconnect|target-bindings)|/imports/[^/]+/undo)?$`).test(path)
const safePhase = () => phase.replace(/[^a-z0-9-]+/gi, '-').slice(0, 80)
const check = name => { result.checks.push(name); process.stdout.write(`PASS ${name}\n`) }
const getCatalogue = () => auth.request(ROOT)
const connection = async id => (await getCatalogue()).connections.find(value => value.id === id)
const href = (target, id = 'connect') => `${CONSOLE}/smart-brains/${BRAIN}/capabilities?${new URLSearchParams({ targetProfileId: target, view: 'mcp', mcp: id })}`
const choose = async (label, option) => { await page.getByRole('combobox', { name: label }).click(); await page.getByRole('option', { name: option, exact: true }).click() }
const apiResponse = (path, method = 'POST', timeout = 95000) => page.waitForResponse(response => new URL(response.url()).pathname === path && response.request().method() === method, { timeout }).catch(() => null)
async function actualResponse(response) {
  assert.ok(response, 'The expected hosted command response was not observed')
  assert.ok(response.ok(), `Hosted command returned HTTP ${response.status()}`)
  return response.json()
}
async function waitForGrant(id) {
  const deadline = Date.now() + 120000
  while (Date.now() < deadline) {
    const value = await connection(id)
    if (value?.authStatus === 'CONNECTED') return value
    await new Promise(resolve => setTimeout(resolve, 1200))
  }
  throw new Error('The synthetic company authorization did not produce a connected grant.')
}
async function createConnection(target, mode) {
  phase = `${mode.toLowerCase()} connection onboarding`
  const name = `010.69 hosted UI ${mode.toLowerCase()} ${Date.now()}`
  const endpointUrl = `${FIXTURE}/${mode === 'PUBLIC' ? 'public' : 'business'}/mcp`
  await page.goto(href(target))
  await page.getByLabel('Connection name').fill(name)
  await page.getByLabel('MCP server address').fill(endpointUrl)
  if (mode === 'BUSINESS') {
    await page.getByRole('radio', { name: /Business-authorized/ }).check()
    await choose('Company authentication', 'Company-owner OAuth sign-in')
  }
  const saved = apiResponse(ROOT)
  await page.getByRole('button', { name: 'Save connection', exact: true }).click()
  const value = await actualResponse(await saved)
  assert.equal(value.endpointUrl, endpointUrl); assert.equal(value.connectionModel, mode)
  created.push({ id: value.id, endpointUrl }); result.createdConnectionIds.push(value.id)
  await page.getByRole('heading', { name, exact: true }).waitFor()
  assert.ok(value.allowedTargetProfileIds.includes(target))
  check(`${mode}: customer UI saved actual connection`)
  return value
}
async function reviewAndUndo(target, value) {
  phase = `${value.connectionModel.toLowerCase()} discovery and import`
  await page.goto(href(target, value.id))
  const discoveryResponse = apiResponse(`${ROOT}/${value.id}/discover`)
  await page.getByRole('button', { name: 'Discover / refresh tools', exact: true }).click()
  const snapshot = await actualResponse(await discoveryResponse)
  assert.equal(snapshot.complete, true)
  const tool = snapshot.tools.find(candidate => candidate.compatible === true)
  assert.ok(tool, 'The synthetic service must have a compatible tool')
  await page.getByRole('checkbox', { name: `Select ${tool.name}`, exact: true }).check()
  const importResponse = apiResponse(`${ROOT}/${value.id}/import`)
  await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).click()
  const receipt = await actualResponse(await importResponse)
  pendingImports.push({ connectionId: value.id, id: receipt.id })
  assert.equal(receipt.createdCount + receipt.updatedCount, 1)
  assert.equal(receipt.changes.length, 1); assert.equal(receipt.changes[0].toolName, tool.name)
  await page.getByText('Draft import completed', { exact: true }).waitFor()
  await page.getByText('Saved changes (1)', { exact: true }).click()
  await page.getByText(`${receipt.changes[0].changeType === 'CREATED' ? 'Created' : 'Updated'}: ${tool.name}`, { exact: true }).click()
  await page.getByRole('region', { name: `${tool.name} saved settings`, exact: true }).waitFor()
  await page.screenshot({ path: join(evidence, `${value.connectionModel.toLowerCase()}-saved-review.png`), fullPage: true })
  check(`${value.connectionModel}: complete discovery and real draft review`)
  phase = `${value.connectionModel.toLowerCase()} guarded undo`
  const undoResponse = apiResponse(`${ROOT}/${value.id}/imports/${receipt.id}/undo`)
  await page.getByRole('button', { name: 'Undo this import', exact: true }).click()
  const undo = await actualResponse(await undoResponse)
  assert.equal(undo.importReceiptId, receipt.id)
  pendingImports.splice(pendingImports.findIndex(item => item.id === receipt.id), 1)
  await page.getByText(/Import undone\. Draft revision/).waitFor()
  check(`${value.connectionModel}: actual import receipt undone through UI`)
}
async function setTargetBinding(target, id, enabled) {
  await page.goto(href(target, id))
  const toggle = page.getByRole('switch', { name: 'Use this connection in this environment', exact: true })
  await toggle.waitFor()
  if (await toggle.isChecked() === enabled) return
  await toggle.click()
  const bindingResponse = apiResponse(`${ROOT}/${id}/target-bindings`)
  await page.getByRole('dialog').getByRole('button', { name: enabled ? 'Enable connection here' : 'Disable connection here', exact: true }).click()
  const bound = await actualResponse(await bindingResponse)
  assert.equal(bound.allowedTargetProfileIds.includes(target), enabled)
  await page.getByText(enabled ? /Connection enabled in this environment\./ : /Connection disabled in this environment\./).waitFor()
  return bound
}

try {
  const { customer } = await import(pathToFileURL(helper).href)
  auth = await customer('production')
  assert.ok(auth.login?.access_token && auth.login?.refresh_token)
  const workspace = await auth.request(`/api/deployments/${BRAIN}/workspace`)
  assert.equal(workspace.deployment.id, BRAIN)
  assert.equal(workspace.access.canAdmin, true)
  const profiles = await auth.request(`/api/deployments/${BRAIN}/target-profiles`)
  const profile = environment => {
    const matches = profiles.filter(value => value.active && value.environmentName === environment)
    assert.equal(matches.length, 1, `Expected one canonical ${environment} target for the customer canary`)
    return matches[0].id
  }
  const stage = profile('staging'), production = profile('production')
  originalActions = (await auth.request(`/api/deployments/${BRAIN}/draft`)).actionsConfig
  const session = { ...auth.login }
  if (!session.expires_at) {
    assert.ok(typeof session.expires_in === 'number' && session.expires_in > 0)
    session.expires_at = Math.floor(Date.now() / 1000) + session.expires_in
  }
  browser = await chromium.launch({ headless: !process.argv.includes('--headed') })
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addInitScript(({ session, origin }) => {
    if (location.origin !== origin) return
    localStorage.setItem('loomai-console-supabase', JSON.stringify(session))
    localStorage.setItem('loomai-customer-console-color-scheme', 'dark')
  }, { session, origin: CONSOLE })
  const apiOrigin = new URL(auth.base).origin
  await context.route('**/*', route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin === apiOrigin && url.pathname.startsWith('/api/') && !['GET', 'HEAD', 'OPTIONS'].includes(request.method())
      && !commandPath(url.pathname) && !(url.pathname === '/api/platform/preferences' && request.method() === 'PUT')) {
      result.blockedMutations.push({ method: request.method(), path: url.pathname })
      return route.abort('blockedbyclient')
    }
    return route.continue()
  })
  page = await context.newPage(); page.setDefaultTimeout(30000)
  page.on('pageerror', () => result.browserErrorCount++)
  context.on('response', response => {
    const url = new URL(response.url())
    if (url.origin === apiOrigin && url.pathname.startsWith('/api/')) result.api.push({ method: response.request().method(), path: url.pathname, status: response.status() })
  })
  const publicConnection = await createConnection(stage, 'PUBLIC')
  await reviewAndUndo(stage, publicConnection)
  phase = 'explicit production reuse and revoke'
  const reused = await setTargetBinding(production, publicConnection.id, true)
  assert.equal(reused.revision, publicConnection.revision)
  assert.equal(reused.targetProfileId, stage)
  await page.screenshot({ path: join(evidence, 'production-reuse.png'), fullPage: true })
  await setTargetBinding(production, publicConnection.id, false)
  check('Same connection explicitly enabled and revoked in production without reimport')
  const businessConnection = await createConnection(stage, 'BUSINESS')
  phase = 'company OAuth browser binding and provider callback'
  const authorizeResponse = apiResponse(`${ROOT}/${businessConnection.id}/authorize`)
  const popupPromise = page.waitForEvent('popup').catch(() => null)
  await page.getByRole('button', { name: 'Connect for this Smart Brain', exact: true }).click()
  const popup = await popupPromise
  assert.ok(popup, 'The synthetic company authorization popup did not open')
  await actualResponse(await authorizeResponse)
  const cookies = await context.cookies(`${apiOrigin}/api/mcp/oauth/callback`)
  assert.ok(cookies.some(cookie => cookie.httpOnly && cookie.path.startsWith('/api/mcp/oauth')), 'Actual authorize must set its HttpOnly callback binding')
  await waitForGrant(businessConnection.id)
  if (!popup.isClosed()) await popup.close()
  check('Company OAuth completed with actual backend grant and browser-binding cookie')
  await reviewAndUndo(stage, businessConnection)
  phase = 'basic phone navigation'
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(href(stage, publicConnection.id))
  await page.getByRole('heading', { name: publicConnection.name, exact: true }).waitFor()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await page.screenshot({ path: join(evidence, 'phone-connections.png'), fullPage: true })
  check('Hosted basic phone layout has no horizontal overflow')
  assert.deepEqual(result.blockedMutations, [])
  assert.equal(result.browserErrorCount, 0)
} catch {
  failed = true; result.failedPhase = phase
  if (page && !page.isClosed()) await page.screenshot({ path: join(evidence, `failed-${safePhase()}.png`), fullPage: true }).catch(() => {})
  process.stderr.write(`Hosted UI verification failed during: ${phase}. No credentials or raw provider responses logged.\n`)
} finally {
  for (const item of pendingImports.reverse()) {
    try { await auth.request(`${ROOT}/${item.connectionId}/imports/${item.id}/undo`, { method: 'POST' }) }
    catch { result.cleanupIssues.push(`Guarded import undo incomplete for ${item.id}; no forced draft replacement attempted.`) }
  }
  for (const item of created) {
    try {
      const current = await connection(item.id)
      assert.equal(current?.endpointUrl, item.endpointUrl)
      if (current.status !== 'DISCONNECTED') await auth.request(`${ROOT}/${item.id}/disconnect`, { method: 'POST' })
    } catch { result.cleanupIssues.push(`Could not disconnect this run's synthetic connection ${item.id}.`) }
  }
  if (auth && originalActions !== undefined) {
    try { assert.deepEqual((await auth.request(`/api/deployments/${BRAIN}/draft`)).actionsConfig, originalActions) }
    catch { result.cleanupIssues.push('The action draft does not match the pre-run API projection; no forced restoration attempted.') }
  }
  await context?.close().catch(() => result.cleanupIssues.push('Isolated browser context did not close cleanly.'))
  await browser?.close().catch(() => result.cleanupIssues.push('Qualification browser did not close cleanly.'))
  if (auth) try { await auth.logout() } catch { result.cleanupIssues.push('Fresh qualification session logout did not complete.') }
  result.completedAt = new Date().toISOString(); result.passed = !failed && result.cleanupIssues.length === 0
  await writeFile(join(evidence, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(`Evidence: ${evidence}\n${result.passed ? 'PASS' : 'NOT GREEN'} hosted UI qualification. Synthetic connection records are disconnected, not deleted.\n`)
  if (!result.passed) process.exitCode = 1
}
