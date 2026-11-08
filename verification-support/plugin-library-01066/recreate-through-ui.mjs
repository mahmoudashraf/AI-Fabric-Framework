import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const require = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))
const { chromium, request } = require('playwright')
const ui = process.env.PLUGIN_UI_ORIGIN
const base = process.env.PLUGIN_API_ORIGIN
const key = process.env.PLUGIN_STAFF_API_KEY
const customerId = process.env.PLUGIN_CUSTOMER_ID
assert.equal(process.env.PLUGIN_ALLOW_TEST_COPIES, 'true', 'Explicit permission to create and archive test copies is required')
assert.ok(ui && base && key && customerId, 'UI/API origins, staff credential and customer scope are required')
assert.ok([ui, base].every(origin => new URL(origin).protocol === 'https:'))
const label = process.env.PLUGIN_TEST_LABEL || 'hosted'
assert.match(label, /^[a-z0-9-]+$/)
const output = fileURLToPath(new URL(`../../output/plugin-form-recreation/${label}/`, import.meta.url))
await mkdir(output, { recursive: true })
const api = await request.newContext({ baseURL: base, extraHTTPHeaders: { 'X-PLATFORM-API-KEY': key } })
const call = async (method, path, data) => {
  const response = await api.fetch(path, { method, data })
  if (!response.ok()) throw new Error(`${method} ${path}: HTTP ${response.status()}`)
  return response.status() === 204 ? null : response.json()
}
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()
page.setDefaultTimeout(30000)
const errors = [], commands = [], created = new Set(), results = []
let recordCommands = false
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  if (!recordCommands || !request.url().startsWith(base) || ['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return
  commands.push({ method: request.method(), path: new URL(request.url()).pathname })
})
page.on('response', response => {
  if (response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/customer/plugin-library' && response.ok()) {
    response.json().then(value => { if (value.id?.startsWith('mkp-private-')) created.add(value.id) }).catch(() => {})
  }
})
const query = `?${new URLSearchParams({ customerId })}`
const destination = '/library/plugins/new/data' + query
const step = name => page.getByRole('navigation', { name: 'Data plugin creation steps' }).getByRole('button', { name: new RegExp(name) }).click()
const screenshot = name => page.screenshot({ path: `${output}/${name}.png`, fullPage: true })
function supported(manifest) {
  const datasets = manifest?.contributions?.datasets
  return Array.isArray(datasets) && datasets.length > 0 && datasets.every(dataset =>
    dataset.ingestionMode === 'EXTERNAL_SYNC_HTTP' && dataset.syncConnector?.connectorType === 'HTTP_JSON'
    || dataset.ingestionMode === 'EXTERNAL_DOCUMENT_STORAGE' && dataset.sourceConnector?.connectorType === 'S3_COMPATIBLE_OBJECT_STORAGE' && dataset.sourceConnector.storageOwnership === 'BOUND_CONNECTION')
}
let failure
try {
  const catalogue = (await call('GET', '/api/marketplace/plugins?includeDemo=true')).filter(plugin => plugin.pluginType === 'DATA' && plugin.visibility !== 'CUSTOMER_PRIVATE')
  const originals = []
  for (const plugin of catalogue) {
    const detail = await call('GET', `/api/marketplace/plugins/${encodeURIComponent(plugin.id)}`)
    const version = detail.versions.find(version => version.version === plugin.latestVersion) || detail.versions[0]
    assert.ok(version?.manifest)
    originals.push({ plugin, version })
  }
  await page.goto(ui + '/staff/sign-in?' + new URLSearchParams({ library_return: destination }))
  await page.getByLabel('X-PLATFORM-API-KEY', { exact: true }).fill(key)
  await page.getByRole('button', { name: 'Continue with API key', exact: true }).click()
  await page.getByLabel('Display name', { exact: true }).waitFor()
  recordCommands = true
  assert.equal(await page.getByLabel('Display name', { exact: true }).getAttribute('aria-required'), 'true')
  await step('Review & version')
  await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
  const required = page.getByRole('region', { name: 'Required fields to complete' })
  await required.waitFor()
  await screenshot('required-review')
  await required.getByRole('button', { name: 'Relative read route', exact: true }).click()
  assert.equal(await page.getByLabel('Relative read route', { exact: true }).evaluate(element => document.activeElement === element), true)
  assert.equal(commands.length, 0, 'An incomplete version attempt must not create a record')
  // This page has no changed values; the next navigation discards no customer work.
  for (const { plugin, version } of originals) {
    await page.goto(ui + destination)
    await page.getByRole('button', { name: 'Start from existing plugin', exact: true }).click()
    await page.getByRole('combobox', { name: /^Existing Data plugin/ }).click()
    await page.getByRole('option', { name: plugin.displayName, exact: true }).click()
    await page.getByRole('combobox', { name: /^Starting version/ }).click()
    await page.getByRole('option', { name: version.version, exact: true }).click()
    const use = page.getByRole('button', { name: 'Use this starting point', exact: true })
    if (!supported(version.manifest)) {
      await page.getByText(/Private definitions currently support HTTP JSON and bound S3 storage only/).waitFor()
      assert.equal(await use.isDisabled(), true)
      await page.getByRole('button', { name: 'Keep current form', exact: true }).click()
      assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), '')
      results.push({ sourcePluginId: plugin.id, name: plugin.displayName, status: 'UNAVAILABLE_BY_CONTRACT', reason: 'Existing private authoring does not support packaged, folder or SQL source authority.' })
      continue
    }
    await use.click()
    const copiedName = `UI recreation - ${plugin.displayName}`
    await page.getByLabel('Display name', { exact: true }).fill(copiedName)
    for (const name of ['Source contract', 'Records & content', 'Freshness & access', 'Setup inputs', 'Review & version']) await step(name)
    await page.getByRole('button', { name: 'Save draft & check', exact: true }).click()
    await page.waitForURL('**/library/plugins/*/edit*')
    const id = page.url().match(/\/library\/plugins\/([^/]+)\/edit/)[1]
    assert.ok(created.has(id))
    await page.getByText('Configuration checked. Provider connectivity and indexing have not been tested.', { exact: true }).waitFor()
    await step('Review & version')
    await page.getByRole('button', { name: 'Save reusable version', exact: true }).click()
    await page.getByText('Reusable version saved. No Brain or live environment was changed.', { exact: true }).waitFor()
    const saved = await call('GET', `/api/customer/plugin-library/${id}${query}`)
    const manifest = saved.versions.find(item => item.version === '1.0.0')?.manifest
    assert.ok(manifest)
    assert.notEqual(manifest.pluginId, version.manifest.pluginId)
    assert.deepEqual(manifest, { ...version.manifest, pluginId: manifest.pluginId, displayName: copiedName, version: '1.0.0' }, 'Full source contract must survive UI recreation')
    assert.equal(saved.installs.savedCount, 0)
    assert.equal(saved.installs.appliedCount, 0)
    await screenshot(plugin.id + '-version')
    await page.getByRole('link', { name: 'View reusable version', exact: true }).click()
    assert.equal(await page.getByLabel('Display name', { exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).count(), 0)
    await page.goto(ui + `/library/plugins/${id}/edit${query}`)
    await page.getByRole('button', { name: 'Archive', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Archive definition', exact: true }).click()
    await page.getByText('Definition archived. Existing pinned versions are retained.', { exact: true }).waitFor()
    results.push({ sourcePluginId: plugin.id, name: plugin.displayName, sourceVersion: version.version, copyPluginId: id, status: 'PASS', fullContractPreserved: true, cleanup: 'ARCHIVED' })
  }
  await page.goto(ui + destination)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByLabel('Display name', { exact: true }).waitFor()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await screenshot('mobile-new-plugin')
  assert.deepEqual(errors, [])
  assert.ok(commands.every(command => command.path === '/api/customer/plugin-library' || [...created].some(id => command.path.startsWith(`/api/customer/plugin-library/${id}`))), 'Only the new private copies may be changed')
  assert.ok(results.some(result => result.status === 'PASS'), 'At least one real source must be recreated')
} catch (error) {
  failure = error instanceof Error ? error.message : 'Verification failed'
  await screenshot('failure').catch(() => {})
} finally {
  const cleanupErrors = []
  for (const id of created) {
    try {
      const current = await call('GET', `/api/customer/plugin-library/${id}${query}`)
      assert.equal(current.ownerCustomerId, customerId)
      assert.equal(current.installs.savedCount, 0)
      assert.equal(current.installs.appliedCount, 0)
      if (current.status !== 'ARCHIVED') await call('POST', `/api/customer/plugin-library/${id}/archive${query}`, { expectedRevision: current.revision })
    } catch (error) { cleanupErrors.push({ id, message: error instanceof Error ? error.message : 'Cleanup failed' }) }
  }
  await context.clearCookies()
  await browser.close()
  await api.dispose()
  if (cleanupErrors.length) failure ||= 'A test copy could not be archived'
  const report = { result: failure ? 'FAIL' : 'PASS', environment: label, ui, results, createdCopies: created.size, allCopiesArchived: cleanupErrors.length === 0, cleanupErrors, commands, capturedPageErrors: errors, failure }
  await writeFile(`${output}/result.json`, JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report))
}
if (failure) throw new Error(failure)
