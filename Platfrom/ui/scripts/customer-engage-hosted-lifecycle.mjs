import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { parseArgs } from 'node:util'
import { chromium } from 'playwright'

const { values } = parseArgs({ options: {
  console: { type: 'string' }, api: { type: 'string' }, customer: { type: 'string' },
  'primary-app': { type: 'string' }, credentials: { type: 'string' }, output: { type: 'string' },
  'allow-draft-writes': { type: 'boolean', default: false },
} })
for (const key of ['console', 'api', 'customer', 'primary-app', 'credentials', 'output']) assert.ok(values[key], `Missing --${key}`)
assert.ok(values['allow-draft-writes'], 'Explicit --allow-draft-writes is required')
const consoleUrl = new URL(values.console).origin, api = new URL(values.api).origin
assert.equal(new URL(consoleUrl).protocol, 'https:')
assert.equal(new URL(api).protocol, 'https:')
process.umask(0o077)
mkdirSync(values.output, { mode: 0o700, recursive: true })
const output = name => `${values.output}/${name}`
const report = { startedAt: new Date().toISOString(), status: 'RUNNING', checks: [], commands: [], blocked: [], errors: [] }
const checkpoint = () => writeFileSync(output('report.actual.json'), JSON.stringify(report, null, 2) + '\n', { mode: 0o600 })
const name = `Engage UI qualification ${Date.now()}`
const editedName = `${name} edited`
const slug = `ui-check-${Date.now()}`
let createdId, consumerId, offerId, targetId, page, context, original
const collection = `/api/platform/customers/${values.customer}/ai-workspace-installations`
const primaryPath = `${collection}/${values['primary-app']}`
const browser = await chromium.launch()
const settle = () => page.evaluate(() => Promise.all(document.getAnimations().map(animation => animation.finished.catch(() => {}))))
try {
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
  await context.route('**/*', route => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    if (['GET', 'HEAD', 'OPTIONS'].includes(method)) return route.continue()
    let allowed = url.origin === api && ['/api/platform/auth/login', '/api/platform/auth/logout', '/api/platform/preferences'].includes(url.pathname)
    if (url.origin === api && method === 'POST' && offerId && targetId
      && url.pathname === `/api/customer-self-service/catalogue/${offerId}/targets/${targetId}/placement-evidence`) allowed = true
    if (url.origin === api && ((method === 'POST' && url.pathname === collection)
      || (createdId && method === 'PUT' && url.pathname === `${collection}/${createdId}`))) {
      const body = request.postDataJSON()
      allowed = body.consumerId === consumerId && [name, editedName].includes(body.displayName)
        && body.appSlug === slug && body.customDomains?.length === 0
    }
    if (url.origin === api && createdId && method === 'DELETE' && url.pathname === `${collection}/${createdId}`) allowed = true
    if (!allowed) {
      report.blocked.push({ method, path: url.pathname }); checkpoint()
      return route.abort('blockedbyclient')
    }
    return route.continue()
  })
  page = await context.newPage()
  page.setDefaultTimeout(30000)
  page.on('pageerror', error => report.errors.push(error.message))
  const get = async path => {
    const response = await context.request.get(api + path)
    assert.equal(response.status(), 200, path)
    return response.json()
  }
  const command = async (method, path, click) => {
    const responsePromise = page.waitForResponse(response => response.request().method() === method && response.url() === api + path)
    await click()
    const response = await responsePromise
    let body
    if (response.status() !== 204) body = await response.json()
    if (method === 'POST' && path === collection && response.ok()) createdId = body.installationId
    report.commands.push({ method, path, status: response.status(), installationId: createdId,
      ...(response.ok() ? {} : { code: body?.code ?? body?.errorCode, message: body?.message ?? body?.detail }) })
    report.cleanupRequired = createdId ?? null
    checkpoint()
    assert.ok(response.ok(), `UI command ${method} ${path} failed: ${response.status()} ${body?.code ?? body?.errorCode ?? body?.message ?? ''}`)
    return body
  }
  await page.goto(`${consoleUrl}/home`, { waitUntil: 'networkidle' })
  const credentials = JSON.parse(readFileSync(values.credentials, 'utf8'))
  await page.getByLabel('Email', { exact: true }).fill(credentials.email)
  await page.getByLabel('Password', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('heading', { name: 'Your products', exact: true }).waitFor()
  const session = await get('/api/platform/auth/session')
  assert.equal(session.role, 'CUSTOMER_ADMIN'); assert.equal(session.customerId, values.customer)
  original = await get(primaryPath)
  assert.equal(original.status, 'ACTIVE')
  consumerId = original.consumerId
  const portfolio = await get('/api/customer-self-service/deployments')
  const deployment = portfolio.find(item => item.deploymentId === original.deploymentId)
  assert.ok(deployment)
  offerId = deployment.offerId
  const appBefore = await get(collection)
  const customerSnapshot = async () => (await get('/api/platform/customers')).find(customer => customer.id === values.customer)
  const consumerBefore = await customerSnapshot()
  targetId = consumerBefore.consumers.find(consumer => consumer.consumerId === consumerId)?.boundTargetProfileId
  assert.ok(targetId)
  await page.goto(`${consoleUrl}/ai-workspaces?view=manage&consumerId=${encodeURIComponent(consumerId)}`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: 'Create AI App', exact: true }).click()
  let dialog = page.getByRole('dialog', { name: 'Create AI App', exact: true })
  await dialog.getByLabel('Workspace name', { exact: true }).fill(name)
  await dialog.getByLabel('App slug', { exact: true }).fill(slug)
  await dialog.getByRole('button', { name: 'Save draft', exact: true }).waitFor({ state: 'visible' })
  const created = await command('POST', collection, () => dialog.getByRole('button', { name: 'Save draft', exact: true }).click())
  assert.equal(created.status, 'DRAFT'); assert.equal(created.consumerId, consumerId)
  await dialog.waitFor({ state: 'hidden' })
  await page.reload({ waitUntil: 'networkidle' })
  let row = page.getByRole('row').filter({ has: page.getByText(name, { exact: true }) })
  await row.getByRole('button', { name: 'Edit app', exact: true }).click()
  dialog = page.getByRole('dialog', { name: 'Edit AI App', exact: true })
  assert.equal(await dialog.getByLabel('Workspace name', { exact: true }).inputValue(), name)
  assert.equal(await dialog.getByLabel('App slug', { exact: true }).inputValue(), slug)
  await dialog.getByLabel('Workspace name', { exact: true }).fill(editedName)
  const updated = await command('PUT', `${collection}/${createdId}`, () => dialog.getByRole('button', { name: 'Save draft', exact: true }).click())
  assert.equal(updated.displayName, editedName); assert.equal(updated.status, 'DRAFT')
  assert.ok(updated.rowVersion > created.rowVersion)
  for (const key of ['consumerId', 'experiencePackCode', 'configuration', 'authConfiguration', 'interactionPolicy', 'appSlug']) {
    assert.deepEqual(updated[key], created[key], `The edit changed ${key}`)
  }
  await dialog.waitFor({ state: 'hidden' })
  await page.reload({ waitUntil: 'networkidle' })
  row = page.getByRole('row').filter({ has: page.getByText(editedName, { exact: true }) })
  await row.waitFor()
  await settle(); await page.screenshot({ path: output('saved-draft.png'), fullPage: true })
  report.checks.push({ name: 'REAL_CUSTOMER_CREATE_RELOAD_EDIT_RELOAD', status: 'PASS', primaryAppChanged: false })
  await row.getByRole('button', { name: 'Delete draft app', exact: true }).click()
  const confirmation = page.getByRole('dialog', { name: 'Delete draft app?', exact: true })
  await confirmation.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.equal((await get(`${collection}/${createdId}`)).status, 'DRAFT')
  await row.getByRole('button', { name: 'Delete draft app', exact: true }).click()
  await command('DELETE', `${collection}/${createdId}`, () => confirmation.getByRole('button', { name: 'Confirm', exact: true }).click())
  await row.waitFor({ state: 'hidden' })
  const appAfter = await get(collection)
  assert.deepEqual(appAfter.map(app => app.installationId).sort(), appBefore.map(app => app.installationId).sort())
  const after = await get(primaryPath)
  for (const key of ['installationId', 'displayName', 'status', 'rowVersion', 'configuration', 'authConfiguration',
    'consumerId', 'deploymentId', 'releaseId', 'assignmentRevision', 'appUrl', 'allowedOrigins']) assert.deepEqual(after[key], original[key], key)
  assert.deepEqual(await customerSnapshot(), consumerBefore)
  report.cleanupRequired = null
  report.checks.push({ name: 'DELETE_CANCEL_CONFIRM_OWN_DRAFT_ONLY', status: 'PASS' },
    { name: 'PRIMARY_APP_ASSIGNMENT_AND_ALL_EXISTING_APPS_PRESERVED', status: 'PASS' })
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await page.getByLabel('Email', { exact: true }).waitFor()
  assert.deepEqual(report.blocked, []); assert.deepEqual(report.errors, [])
  report.status = 'PASS'
} catch (error) {
  report.status = 'FAIL'; report.error = error.message; process.exitCode = 1
  await page?.screenshot({ path: output('failure.png'), fullPage: true }).catch(() => {})
} finally {
  await browser.close(); report.finishedAt = new Date().toISOString(); checkpoint()
  console.log(JSON.stringify({ ...report, evidenceRoot: values.output }))
}
