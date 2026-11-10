import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

assert.equal(process.argv[2], '--run', 'Explicit --run required: creates a disposable Brain and fictional test keys.')
const environment = process.argv[3], output = resolve(process.argv[4] ?? '')
assert.ok(['staging', 'production'].includes(environment) && process.argv[4] && process.env.CUSTOMER_SECRETS_AUTH_REGISTRY)
await mkdir(output, { mode: 0o700 })
const credential = JSON.parse(await readFile(process.env.CUSTOMER_SECRETS_AUTH_REGISTRY, 'utf8')).credentials[Number(process.env.CUSTOMER_SECRETS_AUTH_INDEX ?? 1)]
const ui = environment === 'production' ? 'https://console.loomai.pro' : 'https://loomai-platform-ui.46.224.145.148.sslip.io'
const api = environment === 'production' ? 'https://api.loomai.pro' : 'https://loomai-platform-backend.46.224.145.148.sslip.io'
const { chromium } = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))('playwright')
const browser = await chromium.launch(), context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
const page = await context.newPage(); page.setDefaultTimeout(25000)
let token, brain
const errors = [], mutations = [], cleanup = []
const report = { environment, startedAt: new Date().toISOString(), status: 'IN_PROGRESS', cases: [], errors, cleanup }
const save = () => writeFile(resolve(output, 'report.private.json'), JSON.stringify(report, null, 2), { mode: 0o600 })
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/') && request.method() !== 'GET') mutations.push({ method: request.method(), path: new URL(request.url()).pathname }) })
async function request(path, { method = 'GET', body, statuses = [200] } = {}) {
  const response = await fetch(api + path, { method, headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000) })
  assert.ok(statuses.includes(response.status), `${method} ${path}: ${response.status}`)
  const text = await response.text(); return text ? JSON.parse(text) : null
}
const dialog = () => page.getByRole('dialog')
try {
  await page.goto(ui + '/sign-in', { waitUntil: 'domcontentloaded' })
  await page.locator('input[type=email]').fill(credential.email); await page.locator('input[type=password]').fill(credential.password)
  await page.locator('button[type=submit]').click(); await page.waitForURL(url => !url.pathname.includes('sign-in'), { timeout: 45000 })
  token = await page.evaluate(() => JSON.parse(localStorage.getItem('loomai-console-supabase')).access_token)
  assert.equal((await request('/api/platform/auth/session')).role, 'CUSTOMER_ADMIN')
  brain = await request('/api/deployments', { method: 'POST', statuses: [201], body: {
    name: `Customer keys qualification ${environment}`, environment: 'staging', templateId: 'custom-start-from-scratch', behaviorType: 'CONVERSATIONAL', vectorProvisioningMode: 'LOCAL_MANAGED' } })
  report.brainId = brain.id; await save()
  const path = `/api/deployments/${brain.id}/secrets`
  await page.goto(`${ui}/smart-brains/${brain.id}/secrets`, { waitUntil: 'domcontentloaded' })
  await page.getByText('No customer keys yet', { exact: true }).waitFor()
  await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByRole('link', { name: 'Keys & secrets', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).count(), 0)
  await page.getByRole('button', { name: 'Add key', exact: true }).click()
  await dialog().getByLabel('Key name', { exact: false }).fill('UPSTREAM_TEST')
  await dialog().getByLabel('Key value', { exact: false }).fill('fictional-ui-credential-not-a-real-key')
  await dialog().getByRole('button', { name: 'Save key', exact: true }).click()
  await page.getByText('UPSTREAM_TEST', { exact: true }).waitFor()
  let catalogue = await request(path)
  assert.equal(catalogue.secrets.length, 1)
  assert.ok(!JSON.stringify(catalogue).includes('fictional-ui-credential-not-a-real-key'))
  assert.ok(!(await page.locator('body').innerText()).includes('fictional-ui-credential-not-a-real-key'))
  report.cases.push('customer-create-safe-metadata')
  await page.getByRole('button', { name: 'Replace UPSTREAM_TEST', exact: true }).click()
  assert.equal(await dialog().getByLabel('Key value', { exact: false }).inputValue(), '')
  await dialog().getByLabel('Key value', { exact: false }).fill('fictional-replacement-not-a-real-key')
  await dialog().getByRole('button', { name: 'Replace value', exact: true }).click(); await dialog().waitFor({ state: 'hidden' })
  assert.ok(!JSON.stringify(await request(path)).includes('fictional-replacement-not-a-real-key'))
  report.cases.push('write-only-ui-rotation')
  await request(path + '/OPENAI_API_KEY', { method: 'PUT', statuses: [404], body: { value: 'must-not-change-global-key' } })
  await request('/api/platform/secrets', { statuses: [403] })
  report.cases.push('global-secret-api-and-global-key-mutation-denied')
  await page.getByRole('button', { name: 'Add key', exact: true }).click()
  await dialog().getByRole('combobox', { name: 'Used for' }).click(); await page.getByRole('option', { name: 'OpenAI API key', exact: true }).click()
  await dialog().getByLabel('Key name', { exact: false }).fill('PROVIDER_TEST')
  await dialog().getByLabel('Key value', { exact: false }).fill('fictional-provider-not-used-for-inference')
  await dialog().getByRole('button', { name: 'Save & use key', exact: true }).click()
  await page.getByText('Provider selected', { exact: true }).waitFor()
  catalogue = await request(path)
  const provider = catalogue.secrets.find(secret => secret.label === 'PROVIDER_TEST')
  const bindings = await request(`/api/deployments/${brain.id}/provider-secret-bindings`)
  assert.equal(bindings.bindings.find(binding => binding.secretPurpose === 'OPENAI_API_KEY').secretName, provider.name)
  assert.equal(bindings.bindings.find(binding => binding.secretPurpose === 'OPENAI_API_KEY').bindingMode, 'REQUIRE_OVERRIDE')
  assert.equal(await page.getByRole('button', { name: 'Delete PROVIDER_TEST', exact: true }).isDisabled(), true)
  await request(path + '/' + provider.name, { method: 'DELETE', statuses: [409] })
  report.cases.push('existing-provider-binding-and-bound-deletion-protection')
  await page.screenshot({ path: resolve(output, 'keys-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await page.screenshot({ path: resolve(output, 'keys-mobile.png'), fullPage: true })
  await page.getByRole('button', { name: 'Add key', exact: true }).click()
  await dialog().getByLabel('Key name', { exact: false }).fill('UNSAVED_TEST')
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await dialog().evaluate(node => Promise.all(node.getAnimations({ subtree: true }).filter(a => a.playState === 'running').map(a => a.finished.catch(() => {}))))
  await page.screenshot({ path: resolve(output, 'keys-mobile-form.png'), fullPage: true })
  await dialog().getByRole('button', { name: 'Cancel', exact: true }).click()
  report.cases.push('mobile-and-cancelled-form')
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.getByRole('button', { name: 'Delete UPSTREAM_TEST', exact: true }).click()
  await dialog().getByRole('button', { name: 'Delete key', exact: true }).click(); await dialog().waitFor({ state: 'hidden' })
  assert.equal((await request(path)).secrets.some(secret => secret.label === 'UPSTREAM_TEST'), false)
  report.cases.push('confirmed-ui-deletion')
  assert.equal((await request(`/api/deployments/${brain.id}/versions`)).length, 0)
  assert.equal((await request(`/api/deployments/${brain.id}/releases`)).length, 0)
  assert.equal(mutations.filter(request => request.path.includes('/configuration-assistant/')).length, 0)
  assert.equal(errors.length, 0)
  report.status = 'PASS'
} catch (error) {
  report.status = 'FAIL'; report.failure = error.message
  await page.screenshot({ path: resolve(output, 'failure.png'), fullPage: true }).catch(() => {})
} finally {
  if (brain) {
    try {
      const path = `/api/deployments/${brain.id}/secrets`
      const bindings = await request(`/api/deployments/${brain.id}/provider-secret-bindings`)
      for (const binding of bindings.bindings) await request(`/api/deployments/${brain.id}/provider-secret-bindings/${binding.secretPurpose}`, { method: 'DELETE', statuses: [204] })
      for (const secret of (await request(path)).secrets) await request(path + '/' + secret.name, { method: 'DELETE', statuses: [204] })
      assert.equal((await request(path)).secrets.length, 0); cleanup.push('all-canary-keys-and-bindings-deleted')
      await request(`/api/deployments/${brain.id}/archive`, { method: 'POST' }); cleanup.push('configuration-only-brain-archived')
    } catch (error) { cleanup.push(error.message) }
  }
  try {
    await page.goto(ui + '/home', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: 'Sign out', exact: true }).click()
    await page.waitForURL(url => url.pathname === '/' || url.pathname.includes('sign-in')); cleanup.push('customer-signed-out')
  } catch { cleanup.push('sign-out-not-confirmed') }
  report.finishedAt = new Date().toISOString(); await save(); await context.close(); await browser.close()
  console.log(JSON.stringify({ status: report.status, environment, output, cases: report.cases, cleanup, errors }))
  if (report.status !== 'PASS' || cleanup.length !== 3) process.exitCode = 1
}
