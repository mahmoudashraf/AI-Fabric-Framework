import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

assert.equal(process.argv[2], '--run', 'Explicit --run creates a configuration-only Brain and fictional key.')
const environment = process.argv[3], output = resolve(process.argv[4] ?? '')
assert.ok(['staging', 'production'].includes(environment) && process.argv[4] && process.env.SETUP_CHECK_AUTH_REGISTRY)
await mkdir(output, { mode: 0o700 })
const credential = JSON.parse(await readFile(process.env.SETUP_CHECK_AUTH_REGISTRY, 'utf8')).credentials[1]
const ui = environment === 'production' ? 'https://console.loomai.pro' : 'https://loomai-platform-ui.46.224.145.148.sslip.io'
const api = environment === 'production' ? 'https://api.loomai.pro' : 'https://loomai-platform-backend.46.224.145.148.sslip.io'
const { chromium } = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))('playwright')
const browser = await chromium.launch(), context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
const page = await context.newPage(); page.setDefaultTimeout(25000)
let token, brain
const errors = [], calls = [], cleanup = []
const report = { environment, status: 'IN_PROGRESS', cases: [], errors, cleanup }
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  if (new URL(request.url()).pathname.endsWith('/setup-checks')) calls.push({ method: request.method(), body: request.postDataJSON() })
})
const save = () => writeFile(resolve(output, 'report.private.json'), JSON.stringify(report, null, 2), { mode: 0o600 })
async function request(path, { method = 'GET', body, statuses = [200] } = {}) {
  const response = await fetch(api + path, { method, headers: { Authorization: `Bearer ${token}`, Accept: 'application/json',
    ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(45000) })
  assert.ok(statuses.includes(response.status), `${method} ${path}: ${response.status}`)
  const text = await response.text(); return text ? JSON.parse(text) : null
}
try {
  await page.goto(ui + '/sign-in', { waitUntil: 'domcontentloaded' })
  await page.locator('input[type=email]').fill(credential.email); await page.locator('input[type=password]').fill(credential.password)
  await page.locator('button[type=submit]').click(); await page.waitForURL(url => !url.pathname.includes('sign-in'), { timeout: 45000 })
  token = await page.evaluate(() => JSON.parse(localStorage.getItem('loomai-console-supabase')).access_token)
  assert.equal((await request('/api/platform/auth/session')).role, 'CUSTOMER_ADMIN')
  brain = await request('/api/deployments', { method: 'POST', statuses: [201], body: {
    name: `Setup checks qualification ${environment}`, environment: 'staging', templateId: 'custom-start-from-scratch',
    behaviorType: 'CONVERSATIONAL', vectorProvisioningMode: 'LOCAL_MANAGED' } })
  report.brainId = brain.id; await save()
  const keys = `/api/deployments/${brain.id}/secrets`
  const catalogue = await request(keys, { method: 'POST', statuses: [201], body: { name: 'SAFE_CHECK', purpose: 'INTEGRATION_CREDENTIAL', value: 'fictional-check-key-no-business-access' } })
  const key = catalogue.find(item => item.label === 'SAFE_CHECK').name
  let draft = await request(`/api/deployments/${brain.id}/draft`)
  draft = await request(`/api/deployment-drafts/${draft.id}`, { method: 'PUT', body: {
    providerConfig: { ...draft.providerConfig, llmProvider: 'anthropic', orchestrationLlmProvider: 'anthropic', generationLlmProvider: 'anthropic', embeddingProvider: 'onnx', vectorStrategy: 'lucene' },
    actionsConfig: { actions: [{ name: 'read_example', displayName: 'Read example', description: 'Fictional read operation; not executed by this test.', accessMode: 'READ', params: [] },
      { name: 'create_example', displayName: 'Create example', description: 'Fictional confirmed write; never executed.', accessMode: 'WRITE_ONLY', requiresConfirmation: true, params: [] }] },
    routingConfig: { connector: { upstream: { 'base-url': api, 'setup-check-path': '/actuator/health', auth: { type: 'API_KEY', header: 'X-Setup-Test', value: '${' + key + '}' } } },
      actions: { read_example: { method: 'GET', path: '/never-call-business-read' }, create_example: { method: 'POST', path: '/never-call-business-write' } } },
  } })
  const checksPath = `/api/deployments/${brain.id}/setup-checks`
  assert.equal((await request(checksPath)).verifiedCount, 0)
  const brainUrl = `${ui}/smart-brains/${brain.id}`
  await page.goto(brainUrl + '/updates?targetProfileId=dtp-coolify-staging', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Check setup', exact: true }).waitFor()
  assert.equal(calls.filter(call => call.method === 'POST').length, 0)
  await page.getByRole('button', { name: 'Check setup', exact: true }).click()
  await page.getByRole('status').filter({ hasText: /Connections: 1 verified/ }).waitFor()
  let result = await request(checksPath)
  assert.equal(result.verifiedCount, 1)
  assert.deepEqual(result.services.find(item => item.status === 'VERIFIED').usedBy, ['Read example', 'Create example'])
  assert.ok(!JSON.stringify(result).includes('fictional-check-key'))
  await page.getByRole('button', { name: 'Details', exact: true }).click()
  await page.getByText('Action connection', { exact: true }).waitFor()
  await page.screenshot({ path: resolve(output, 'updates-desktop.png'), fullPage: true })
  report.cases.push('one-real-safe-GET-shared-by-read-and-write-actions; no-on-mount-probe')

  await page.goto(brainUrl + '/capabilities/actions?panel=routing&targetProfileId=dtp-coolify-staging', { waitUntil: 'domcontentloaded' })
  const field = page.getByRole('textbox', { name: 'Safe connection check path (optional)', exact: true }).first()
  await field.waitFor(); await field.fill('/api/deployments')
  await page.locator('footer').getByRole('button', { name: 'Save draft', exact: true }).click()
  await page.getByRole('status').filter({ hasText: /Connections: 0 verified · 1 failed/ }).waitFor()
  result = await request(checksPath)
  assert.equal(result.failedCount, 1)
  assert.ok([401, 403].includes(result.services.find(item => item.status === 'FAILED').httpStatus))
  assert.ok(calls.some(call => call.body?.mode === 'CHANGED'))
  report.cases.push('real-editor-save-auto-check; protected-endpoint-rejects-fictional-auth')
  await Promise.all([page.waitForResponse(response => response.url().endsWith('/setup-checks') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'Retry failed checks', exact: true }).click()])
  await page.getByRole('status').filter({ hasText: /Connections: 0 verified · 1 failed/ }).waitFor()
  assert.ok(calls.some(call => call.body?.mode === 'FAILED'))
  // Short-lived qualification identity, only sent back to this same Platform host; removed in finally.
  await request(keys + '/' + key, { method: 'PUT', body: { value: `Bearer ${token}` } })
  await page.getByRole('textbox', { name: 'Upstream auth header', exact: true }).fill('Authorization')
  await page.locator('footer').getByRole('button', { name: 'Save draft', exact: true }).click()
  await page.getByRole('status').filter({ hasText: /Connections: 1 verified/ }).waitFor()
  result = await request(checksPath)
  assert.equal(result.services.find(item => item.status === 'VERIFIED').httpStatus, 200)
  report.cases.push('failed-only-retry-and-real-authenticated-read-success-on-same-host')
  await request(keys + '/' + key, { method: 'PUT', body: { value: 'fictional-rotated-check-key' } })
  assert.equal((await request(checksPath)).verifiedCount, 0)
  report.cases.push('real-credential-rotation-invalidates-cached-success')
  await page.goto(brainUrl + '/updates?targetProfileId=dtp-coolify-staging', { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: 'Check setup', exact: true }).click()
  await page.getByRole('status').filter({ hasText: /Connections: 0 verified · 1 failed/ }).waitFor()
  await request(keys + '/' + key, { method: 'PUT', body: { value: `Bearer ${token}` } })
  await Promise.all([page.waitForResponse(response => response.url().endsWith('/setup-checks') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'Retry failed checks', exact: true }).click()])
  await page.getByRole('status').filter({ hasText: /Connections: 1 verified/ }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: 'Details', exact: true }).click()
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.screenshot({ path: resolve(output, 'updates-mobile.png'), fullPage: true })
  assert.equal((await request(`/api/deployments/${brain.id}/versions`)).length, 0)
  assert.equal((await request(`/api/deployments/${brain.id}/releases`)).length, 0)
  assert.equal(errors.length, 0)
  report.cases.push('mobile-no-overflow; no-publication-provisioning-or-inference')
  report.status = 'PASS'
} catch (error) {
  report.status = 'FAIL'; report.failure = error.message
  await page.screenshot({ path: resolve(output, 'failure.png'), fullPage: true }).catch(() => {})
} finally {
  if (brain) {
    try {
      const keys = `/api/deployments/${brain.id}/secrets`
      for (const key of (await request(keys)).secrets) await request(keys + '/' + key.name, { method: 'DELETE', statuses: [204] })
      await request(`/api/deployments/${brain.id}/archive`, { method: 'POST' }); cleanup.push('fictional-keys-deleted-and-empty-brain-archived')
    } catch (error) { cleanup.push(error.message) }
  }
  try { await page.goto(ui + '/home'); await page.getByRole('button', { name: 'Sign out', exact: true }).click(); cleanup.push('signed-out') } catch { cleanup.push('browser-context-closed') }
  await context.close(); await browser.close(); await save()
}
console.log(JSON.stringify(report, null, 2))
if (report.status !== 'PASS') process.exitCode = 1
