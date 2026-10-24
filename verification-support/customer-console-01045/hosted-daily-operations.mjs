import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parseArgs } from 'node:util'

const { values: args } = parseArgs({ options: Object.fromEntries(
  ['mode', 'root', 'credentials', 'deployment', 'release', 'target', 'customer', 'app', 'offer'].map(key => [key, { type: 'string' }])) })
for (const key of ['mode', 'root', 'credentials', 'deployment', 'release', 'target', 'customer', 'app', 'offer']) assert.ok(args[key], key)
assert.ok(['documents', 'synchronization', 'operator'].includes(args.mode))
process.umask(0o077)
mkdirSync(args.root, { recursive: true, mode: 0o700 })
const save = (name, value) => writeFileSync(`${args.root}/${name}.json`, JSON.stringify(value, null, 2), { flag: 'wx', mode: 0o600 })
assert.equal(existsSync(`${args.root}/${args.mode}.intent.json`), false, 'Consumed command must not be replayed')
const api = 'https://api.loomai.pro', base = 'https://console.loomai.pro'
const targetPath = `/api/deployments/${args.deployment}/targets/${args.target}`
const releasePath = `${targetPath}/releases/${args.release}`
const appPath = `/api/platform/customers/${args.customer}/ai-workspace-installations/${args.app}`
const { chromium } = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))('playwright')
const browser = await chromium.launch()
const report = { at: new Date().toISOString(), mode: args.mode, status: 'RUNNING', commands: [], blocked: [], errors: [] }
let page, phase = 'login', selectedSource, operatorId
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
  const allowed = path => ['/api/platform/auth/login', '/api/platform/auth/logout', '/api/platform/preferences'].includes(path)
    || (args.mode === 'operator' && path === `/api/customer-self-service/catalogue/${args.offer}/targets/${args.target}/placement-evidence`)
    || (args.mode === 'documents' && path === `${releasePath}/document-knowledge/sources/${encodeURIComponent(selectedSource)}/refresh`)
    || (args.mode === 'synchronization' && path === `${releasePath}/integrations/sources/${encodeURIComponent(selectedSource)}/reconcile`)
    || (args.mode === 'operator' && (path === `${targetPath}/managed-dependencies/operators`
      || (operatorId && ['credential-delivery', 'revoke'].some(command => path === `${targetPath}/managed-dependencies/operators/${operatorId}/${command}`))))
  await context.route('**/*', route => {
    const r = route.request(), url = new URL(r.url())
    if (['GET', 'HEAD', 'OPTIONS'].includes(r.method())) return route.continue()
    if (url.origin === api && allowed(url.pathname)) {
      report.commands.push({ method: r.method(), path: url.pathname })
      return route.continue()
    }
    report.blocked.push({ method: r.method(), path: url.pathname }); return route.abort()
  })
  page = await context.newPage(); page.setDefaultTimeout(45000)
  page.on('pageerror', error => report.errors.push(error.message))
  const get = async path => { const r = await context.request.get(api + path); assert.equal(r.status(), 200, path); return r.json() }
  const waitCommand = path => page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === path)
  await page.goto(base + '/home', { waitUntil: 'networkidle' })
  const owner = JSON.parse(readFileSync(args.credentials))
  await page.getByLabel('Email', { exact: true }).fill(owner.email)
  await page.getByLabel('Password', { exact: true }).fill(owner.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('heading', { name: 'Your products', exact: true }).waitFor()
  const session = await get('/api/platform/auth/session')
  assert.equal(session.customerId, args.customer); assert.equal(session.role, 'CUSTOMER_ADMIN')
  const before = await get(appPath)
  assert.equal(before.deploymentId, args.deployment); assert.equal(before.releaseId, args.release)
  const query = new URLSearchParams({ deploymentId: args.deployment, releaseId: args.release })
  const route = args.mode === 'documents' ? 'document-knowledge' : args.mode === 'operator' ? 'human-support' : 'integrations'
  phase = 'open-shared-editor'
  await page.goto(`${base}/${route}?${query}`, { waitUntil: 'networkidle' })
  if (args.mode === 'documents') {
    const sources = await get(releasePath + '/document-knowledge/sources')
    const source = sources.find(item => item.status === 'ACTIVE'); assert.ok(source)
    selectedSource = source.sourceId
    await page.getByRole('button').filter({ hasText: source.displayName }).first().click()
    await page.getByRole('complementary', { name: 'Document details' }).getByRole('tab', { name: 'Configuration', exact: true }).click()
    await page.getByRole('button', { name: 'Manage document', exact: true }).click()
    await page.getByRole('button', { name: 'Refresh revision', exact: true }).waitFor()
    save(`${args.mode}.intent`, { at: new Date().toISOString(), sourceId: selectedSource, releaseId: args.release, replay: false })
    phase = 'refresh-document'
    const pending = waitCommand(`${releasePath}/document-knowledge/sources/${encodeURIComponent(selectedSource)}/refresh`)
    await page.getByRole('button', { name: 'Refresh revision', exact: true }).click()
    const response = await pending; report.commandStatus = response.status()
    report.result = await response.json(); assert.equal(response.status(), 200)
    await page.getByRole('alert').filter({ hasText: `Operation: ${report.result.outcome}` }).waitFor()
    const after = await get(releasePath + '/document-knowledge/sources')
    assert.equal(after.find(item => item.sourceId === selectedSource)?.status, 'ACTIVE')
    report.sourceRemainsActive = true
  } else if (args.mode === 'synchronization') {
    const sources = await get(releasePath + '/integrations')
    const source = sources.sources.find(item => item.state.counts.sourceCount > 0); assert.ok(source)
    selectedSource = source.sourceId
    await page.getByRole('button', { name: 'Manage', exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: selectedSource }).filter({ has: page.locator('svg[data-testid="SyncRoundedIcon"]') })
    await row.waitFor()
    save(`${args.mode}.intent`, { at: new Date().toISOString(), sourceId: selectedSource, releaseId: args.release, replay: false })
    phase = 'reconcile-inventory'
    const pending = waitCommand(`${releasePath}/integrations/sources/${encodeURIComponent(selectedSource)}/reconcile`)
    await row.getByRole('button').click()
    const response = await pending; report.commandStatus = response.status()
    report.result = await response.json(); assert.equal(response.status(), 200)
    const after = await get(releasePath + '/integrations')
    const current = after.sources.find(item => item.sourceId === selectedSource)
    assert.ok(current.state.counts.indexedCount > 0); assert.equal(current.state.counts.failedWorkCount, 0)
    report.indexedCount = current.state.counts.indexedCount
  } else {
    await page.getByRole('button', { name: 'Manage', exact: true }).click()
    await page.getByRole('heading', { name: 'Inbox operators', exact: true }).waitFor()
    const name = `UI qualification ${Date.now()}`
    await page.getByLabel('Operator display name', { exact: true }).fill(name)
    const slot = page.getByRole('combobox', { name: /^Inbox slot/ })
    if (!(await slot.innerText()).trim()) { await slot.click(); await page.getByRole('option').first().click() }
    save(`${args.mode}.intent`, { at: new Date().toISOString(), displayName: name, target: args.target, replay: false })
    phase = 'enrol-temporary-operator'
    let pending = waitCommand(`${targetPath}/managed-dependencies/operators`)
    await page.getByRole('button', { name: 'Enrol operator', exact: true }).click()
    let response = await pending
    let data = await response.json()
    if (response.status() !== 200) report.rejection = data
    assert.equal(response.status(), 200)
    const operator = data.operators.find(item => item.displayName === name); assert.ok(operator)
    operatorId = operator.id; report.operatorId = operatorId
    const row = page.getByRole('row').filter({ hasText: name })
    phase = 'deliver-once'
    await row.getByRole('button', { name: 'Deliver once', exact: true }).click({ trial: true, timeout: 180000 })
    pending = waitCommand(`${targetPath}/managed-dependencies/operators/${operatorId}/credential-delivery`)
    await row.getByRole('button', { name: 'Deliver once', exact: true }).click()
    response = await pending
    if (response.status() !== 200) report.rejection = await response.json()
    assert.equal(response.status(), 200)
    data = await response.json(); assert.ok(data.apiKey?.length > 15)
    report.oneTimeCredentialReceived = true
    await page.getByRole('alert').filter({ hasText: 'One-time operator credential:' }).getByRole('button').click()
    data = null
    phase = 'revoke-temporary-operator'
    pending = waitCommand(`${targetPath}/managed-dependencies/operators/${operatorId}/revoke`)
    await row.getByRole('button', { name: 'Revoke', exact: true }).click()
    response = await pending; assert.equal(response.status(), 200)
    data = await response.json(); assert.equal(data.operators.find(item => item.id === operatorId)?.status, 'REVOKED')
    await row.getByText('REVOKED', { exact: true }).waitFor()
    const deadline = Date.now() + 180000
    do {
      data = await get(`${targetPath}/managed-dependencies`)
      if (data.operators.find(item => item.id === operatorId)?.effectiveAt && data.configurationRevision === data.effectiveConfigurationRevision) break
      assert.ok(Date.now() < deadline, 'Operator revocation has not become effective')
      assert.notEqual(data.status, 'BLOCKED', data.failureCode)
      await page.waitForTimeout(5000)
    } while (true)
    report.temporaryOperatorRevoked = true
  }
  const after = await get(appPath)
  for (const key of ['releaseId', 'assignmentRevision', 'rowVersion', 'status', 'appUrl']) assert.equal(after[key], before[key], key)
  report.appAssignmentUnchanged = true
  assert.deepEqual(report.blocked, []); assert.deepEqual(report.errors, [])
  await page.screenshot({ path: `${args.root}/${args.mode}.png`, fullPage: true })
  report.status = 'PASS'
} catch (error) {
  report.status = 'NOT_ACCEPTED'; report.failure = { phase, message: error.message.slice(0, 1500) }; process.exitCode = 1
} finally {
  await browser.close(); report.finishedAt = new Date().toISOString()
  save(`${args.mode}-${Date.now()}.actual`, report)
  console.log(JSON.stringify(report))
}
