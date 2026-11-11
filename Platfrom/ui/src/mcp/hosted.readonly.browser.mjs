import assert from 'node:assert/strict'
import { mkdtemp, writeFile } from 'node:fs/promises'
import { homedir, tmpdir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { chromium } from 'playwright'
import { hostedRuntimeTarget } from './hostedTargets.mjs'

const CONSOLE = 'https://console.loomai.pro'
const BRAIN = 'dep-3623c3f2'
const ROOT = `/api/deployments/${BRAIN}/mcp-connections`
if (!process.argv.includes('--run-read-only')) {
  process.stdout.write('Dry run only. Use --run-read-only after production UI health is confirmed. No private auth, browser or network requests started.\n')
  process.exit(0)
}

const evidence = await mkdtemp(join(tmpdir(), 'loomai-mcp-production-readonly-'))
const result = { deploymentId: BRAIN, checks: [], skipped: [], api: [], blockedWrites: [], preferenceWrites: 0, browserErrors: 0, startedAt: new Date().toISOString() }
let auth, browser, context, page, phase = 'customer authentication', failed = false, beforeConnections, beforeDraft
const check = name => { result.checks.push(name); process.stdout.write(`PASS ${name}\n`) }
const choose = async (label, option) => {
  await page.getByRole('combobox', { name: label }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
  await page.getByRole('listbox').waitFor({ state: 'hidden' })
}
async function layout(label) {
  phase = `layout ${label}`
  await page.evaluate(async () => {
    await document.fonts.ready
    await Promise.all(document.getAnimations().filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {})))
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${label}: horizontal overflow`)
  const heading = await page.getByRole('heading', { name: 'Connected tools', exact: true }).boundingBox()
  const action = await page.getByRole('link', { name: 'Connect MCP', exact: true }).boundingBox()
  if (heading && action) assert.ok(Math.min(heading.x + heading.width, action.x + action.width) - Math.max(heading.x, action.x) < 2
    || Math.min(heading.y + heading.height, action.y + action.height) - Math.max(heading.y, action.y) < 2, `${label}: heading/action overlap`)
  const fields = await page.locator('form .MuiTextField-root').evaluateAll(nodes => nodes.map(node => {
    const rect = node.getBoundingClientRect(), style = getComputedStyle(node)
    return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, visible: rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none' }
  }).filter(value => value.visible))
  for (let i = 0; i < fields.length; i++) for (let j = i + 1; j < fields.length; j++) {
    const a = fields[i], b = fields[j]
    assert.ok(Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x) < 2
      || Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y) < 2, `${label}: input-container overlap`)
  }
  await page.screenshot({ path: join(evidence, `${label}.png`), fullPage: true, animations: 'disabled' })
  check(`${label}: no horizontal overflow or input/heading overlap`)
}

try {
  const { customer } = await import(pathToFileURL(join(homedir(), '.codex/private/loomai-mcp-01069/api.mjs')).href)
  auth = await customer('production')
  const profiles = await auth.request(`/api/deployments/${BRAIN}/target-profiles`)
  const target = hostedRuntimeTarget(profiles, 'production')
  beforeConnections = await auth.request(ROOT)
  beforeDraft = await auth.request(`/api/deployments/${BRAIN}/draft`)
  const session = { ...auth.login }
  assert.ok(session.access_token && session.refresh_token)
  if (!session.expires_at) {
    assert.ok(typeof session.expires_in === 'number' && session.expires_in > 0)
    session.expires_at = Math.floor(Date.now() / 1000) + session.expires_in
  }
  browser = await chromium.launch({ headless: true })
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addInitScript(({ origin, session }) => {
    if (location.origin !== origin) return
    localStorage.setItem('loomai-console-supabase', JSON.stringify(session))
    localStorage.setItem('loomai-customer-console-color-scheme', 'dark')
  }, { origin: CONSOLE, session })
  const apiOrigin = new URL(auth.base).origin
  await context.route('**/*', route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin === apiOrigin && url.pathname.startsWith('/api/') && !['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
      if (request.method() === 'PUT' && url.pathname === '/api/platform/preferences') result.preferenceWrites++
      else { result.blockedWrites.push({ method: request.method(), path: url.pathname }); return route.abort('blockedbyclient') }
    }
    return route.continue()
  })
  context.on('response', response => {
    const url = new URL(response.url())
    if (url.origin === apiOrigin && url.pathname.startsWith('/api/')) result.api.push({ path: url.pathname, method: response.request().method(), status: response.status() })
  })
  page = await context.newPage(); page.setDefaultTimeout(30000)
  page.on('pageerror', () => result.browserErrors++)
  const capabilities = `${CONSOLE}/smart-brains/${BRAIN}/capabilities?targetProfileId=${encodeURIComponent(target)}`
  phase = 'MCP capabilities entry'
  await page.goto(capabilities)
  await page.getByRole('heading', { name: 'What your AI can do', exact: true }).waitFor()
  await page.getByRole('tab', { name: 'MCP connections', exact: true }).click()
  await page.getByRole('heading', { name: 'Connected tools', exact: true }).waitFor()
  await layout('mcp-list-desktop-dark')
  phase = 'public and business connection fields'
  await page.getByRole('link', { name: 'Connect MCP', exact: true }).click()
  await page.getByLabel('Connection name').waitFor()
  assert.equal(await page.getByRole('radio', { name: /Public \/ no authentication/ }).isChecked(), true)
  assert.equal(await page.getByRole('combobox', { name: 'Company authentication' }).count(), 0)
  await layout('public-form-desktop-dark')
  await page.getByRole('radio', { name: /Business-authorized/ }).check()
  await choose('Company authentication', 'Saved bearer token')
  await page.getByRole('combobox', { name: 'Saved key' }).waitFor()
  assert.equal(await page.locator('input[type="password"]').count(), 0)
  await choose('Company authentication', 'API-key header')
  assert.equal(await page.getByLabel('API-key header name').inputValue(), 'X-API-Key')
  await choose('Company authentication', 'Multiple credential headers')
  await page.getByLabel('Header 1 name').waitFor()
  await choose('Company authentication', 'OAuth service credentials')
  assert.equal(await page.getByLabel('Authorization server URL').evaluate(input => input.required), true)
  await layout('machine-oauth-desktop-dark')
  await choose('Company authentication', 'Company-owner OAuth sign-in')
  assert.equal(await page.getByLabel('Authorization server URL').count(), 0)
  await page.getByRole('button', { name: 'Technical connection settings', exact: true }).click()
  assert.equal(await page.getByLabel('Authorization server URL').evaluate(input => input.required), false)
  await layout('company-oauth-desktop-dark')
  phase = 'light theme'
  await page.getByRole('button', { name: 'Switch color theme', exact: true }).click()
  assert.equal(await page.getByRole('heading', { name: 'Connected tools', exact: true }).evaluate(node => node.closest('[data-customer-theme]')?.getAttribute('data-customer-theme')), 'light')
  await layout('company-oauth-desktop-light')
  await page.getByRole('radio', { name: /Public \/ no authentication/ }).check()
  await layout('public-form-desktop-light')
  phase = 'basic phone layout'
  await page.setViewportSize({ width: 390, height: 844 })
  await layout('public-form-phone-light')
  await page.getByRole('radio', { name: /Business-authorized/ }).check()
  await choose('Company authentication', 'OAuth service credentials')
  await layout('machine-oauth-phone-light')
  await page.getByRole('button', { name: 'Switch color theme', exact: true }).click()
  await layout('machine-oauth-phone-dark')
  phase = 'existing native MCP action editor'
  const ownedIds = new Set(beforeConnections.connections.map(value => value.id))
  const native = (beforeDraft.actionsConfig?.actions ?? []).find(action => (action.adapterType === 'mcp-tool' || action.execution?.adapterType === 'mcp-tool') && ownedIds.has(action.execution?.mcp?.connectionId))
  if (native) {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(`${CONSOLE}/smart-brains/${BRAIN}/capabilities/${encodeURIComponent(`action:${native.name}`)}?targetProfileId=${encodeURIComponent(target)}&view=technical`)
    const destination = page.getByRole('region', { name: 'MCP destination', exact: true })
    await destination.getByText(native.execution.mcp.toolName, { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: /^HTTP route for/ }).count(), 0)
    await page.getByRole('button', { name: 'Native MCP schemas (read-only)', exact: true }).click()
    if (native.inputSchema != null) await page.getByLabel('MCP native input schema', { exact: true }).waitFor()
    await page.screenshot({ path: join(evidence, 'existing-mcp-action-editor.png'), fullPage: true })
    check('Existing MCP action editor uses exact destination and read-only schema, not an HTTP route')
  } else { result.skipped.push('No owned imported MCP action exists in this canary; native editor hosted check not performed.'); process.stdout.write('SKIP native editor: no owned imported MCP action exists\n') }
  phase = 'read-only verification'
  const afterConnections = await auth.request(ROOT)
  const afterDraft = await auth.request(`/api/deployments/${BRAIN}/draft`)
  assert.deepEqual(afterConnections, beforeConnections)
  assert.deepEqual(afterDraft.actionsConfig, beforeDraft.actionsConfig)
  assert.deepEqual(result.blockedWrites, [])
  assert.equal(result.browserErrors, 0)
  assert.equal(result.api.filter(value => value.path.startsWith(ROOT) && value.method !== 'GET').length, 0)
  check('Connection catalogue and action draft unchanged; zero MCP writes and browser runtime errors')
} catch {
  failed = true; result.failedPhase = phase
  if (page && !page.isClosed()) await page.screenshot({ path: join(evidence, 'failed.png'), fullPage: true }).catch(() => {})
  process.stderr.write(`Read-only hosted UI verification failed during: ${phase}. Credentials and raw responses are not logged.\n`)
} finally {
  await context?.close().catch(() => {})
  await browser?.close().catch(() => {})
  if (auth) try { await auth.logout() } catch { result.logoutIncomplete = true; failed = true }
  result.passed = !failed; result.completedAt = new Date().toISOString()
  await writeFile(join(evidence, 'result.json'), JSON.stringify(result, null, 2))
  process.stdout.write(`Evidence: ${evidence}\n${result.passed ? 'PASS' : 'NOT GREEN'} actual production read-only UI verification.\n`)
  if (failed) process.exitCode = 1
}
