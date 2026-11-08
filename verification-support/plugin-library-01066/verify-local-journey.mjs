import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))
const { chromium, request } = require('playwright')
const ui = new URL(process.env.PLUGIN_UI_URL ?? 'http://127.0.0.1:4408')
const backend = new URL(process.env.PLUGIN_API_URL ?? 'http://127.0.0.1:18088')
for (const url of [ui, backend]) assert.equal(url.hostname, '127.0.0.1', 'This destructive fixture runner is loopback-only.')
const apiKey = process.env.PLUGIN_LOCAL_ADMIN_KEY ?? 'plugin-local-qualification'
const api = await request.newContext({ baseURL: backend.origin, extraHTTPHeaders: { 'X-PLATFORM-API-KEY': apiKey } })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const output = new URL('../../output/plugin-library-implementation/', import.meta.url)
await mkdir(output, { recursive: true })
const errors = [], commands = [], reads = []
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => { if (request.url().startsWith(backend.origin) && request.method() !== 'GET') commands.push({ method: request.method(), path: new URL(request.url()).pathname }) })
page.on('response', async response => { if (response.url().includes('/api/platform/auth/session')) { const body = await response.json().catch(() => ({})); reads.push({ url: response.url(), status: response.status(), enabled: body.enabled, apiKeyAuthEnabled: body.apiKeyAuthEnabled }) } })
try {
  const customerResponse = await api.post('/api/platform/customers', { data: { name: `Plugin UI qualification ${Date.now()}`, description: 'Disposable local-only integration fixture.' } })
  assert.equal(customerResponse.status(), 201, await customerResponse.text())
  const customer = await customerResponse.json()
  const destination = '/library/plugins/new/data?' + new URLSearchParams({ customerId: customer.id })
  await page.goto(ui.origin + '/staff/sign-in?' + new URLSearchParams({ library_return: destination }))
  await page.getByLabel('X-PLATFORM-API-KEY', { exact: true }).fill(apiKey)
  await page.getByRole('button', { name: 'Continue with API key', exact: true }).click()
  await page.getByLabel('Display name', { exact: true }).fill('Approved operations documents')
  await page.getByLabel('Purpose', { exact: true }).fill('Answer customer questions from approved operation guides.')
  const step = name => page.getByRole('navigation', { name: 'Data plugin creation steps' }).getByRole('button', { name: new RegExp(name) }).click()
  await step('Source contract')
  await page.getByRole('button', { name: 'Document storage', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Change source', exact: true }).click()
  await step('Review & version')
  await page.getByRole('button', { name: 'Save draft & check', exact: true }).click()
  await page.waitForURL('**/library/plugins/*/edit*')
  await page.getByText('Configuration checked. Provider connectivity and indexing have not been tested.', { exact: true }).waitFor()
  await step('Review & version')
  await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
  await page.getByText('Reusable version saved. No Brain or live environment was changed.', { exact: true }).waitFor()
  const listResponse = await api.get('/api/customer/plugin-library?' + new URLSearchParams({ customerId: customer.id }))
  assert.equal(listResponse.status(), 200)
  const list = await listResponse.json()
  assert.equal(list.plugins.length, 1)
  const plugin = list.plugins[0]
  assert.equal(plugin.pluginType, 'DATA'); assert.equal(plugin.visibility, 'CUSTOMER_PRIVATE')
  assert.equal(plugin.versions[0].version, '1.0.0')
  assert.equal(plugin.manifest.contributions.datasets[0].sourceConnector.connectorType, 'S3_COMPATIBLE_OBJECT_STORAGE')
  assert.equal(plugin.installs.savedCount, 0); assert.equal(plugin.installs.appliedCount, 0)
  await page.screenshot({ path: new URL('real-backend-version.png', output).pathname, fullPage: true })
  assert.equal(commands.some(item => /query|apply|releases|sync|index/.test(item.path)), false)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', customerId: customer.id, pluginId: plugin.id, revision: plugin.revision,
    workflow: 'real sign-in -> private S3 definition -> schema check -> immutable version -> persisted API readback', commands }))
} catch (error) {
  await page.screenshot({ path: new URL('real-backend-failure.png', output).pathname, fullPage: true })
  console.error({ errors, commands, reads, text: await page.locator('body').innerText() }); throw error
} finally { await api.dispose(); await browser.close() }
