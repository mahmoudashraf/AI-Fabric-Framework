import assert from 'node:assert/strict'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parseArgs } from 'node:util'

const { values: args } = parseArgs({ options: Object.fromEntries(
  ['root', 'credentials', 'deployment', 'release', 'target', 'customer', 'expected-count']
    .map(key => [key, { type: 'string' }])) })
for (const key of ['root', 'credentials', 'deployment', 'release', 'target', 'customer', 'expected-count']) assert.ok(args[key], key)
const expectedCount = Number(args['expected-count'])
assert.ok(Number.isSafeInteger(expectedCount) && expectedCount > 0 && expectedCount <= 10)
process.umask(0o077)
mkdirSync(args.root, { recursive: true, mode: 0o700 })
const save = (name, value) => writeFileSync(`${args.root}/${name}.json`, JSON.stringify(value, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
assert.equal(existsSync(`${args.root}/discover.intent.json`), false, 'Consumed run: reconcile its original requests, never replay')
const api = 'https://api.loomai.pro', base = 'https://console.loomai.pro'
const path = `/api/deployments/${args.deployment}/targets/${args.target}/releases/${args.release}/document-knowledge`
const report = { startedAt: new Date().toISOString(), status: 'RUNNING', commands: [], blocked: [], errors: [], sources: [], sourceWrites: 0 }
const { chromium } = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))('playwright')
const browser = await chromium.launch()
let page, phase = 'login', currentSource
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
  await context.route('**/*', route => {
    const r = route.request(), url = new URL(r.url())
    if (['GET', 'HEAD', 'OPTIONS'].includes(r.method())) return route.continue()
    const allowed = url.origin === api && (['/api/platform/auth/login', '/api/platform/auth/logout', '/api/platform/preferences'].includes(url.pathname)
      || (r.method() === 'POST' && url.pathname === path + '/discover')
      || (r.method() === 'POST' && url.pathname === path + '/sources' && r.postDataJSON().objectReference === currentSource?.objectReference
        && r.postDataJSON().metadata?.publicationStatus === 'PUBLIC_APPROVED')
      || (r.method() === 'POST' && currentSource?.sourceId && url.pathname === `${path}/sources/${currentSource.sourceId}/index`))
    if (!allowed) { report.blocked.push({ method: r.method(), path: url.pathname }); return route.abort() }
    report.commands.push({ method: r.method(), path: url.pathname }); return route.continue()
  })
  page = await context.newPage(); page.setDefaultTimeout(45000)
  page.on('pageerror', error => report.errors.push(error.message))
  const get = async p => { const r = await context.request.get(api + p); assert.equal(r.status(), 200, p); return r.json() }
  await page.goto(base + '/home', { waitUntil: 'networkidle' })
  const owner = JSON.parse(readFileSync(args.credentials))
  await page.getByLabel('Email', { exact: true }).fill(owner.email)
  await page.getByLabel('Password', { exact: true }).fill(owner.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('heading', { name: 'Your products', exact: true }).waitFor()
  const session = await get('/api/platform/auth/session')
  assert.equal(session.customerId, args.customer); assert.equal(session.role, 'CUSTOMER_ADMIN')
  assert.equal((await get(path + '/sources')).length, 0, 'Fresh source registration only')
  await page.goto(`${base}/document-knowledge?${new URLSearchParams({ deploymentId: args.deployment, releaseId: args.release, view: 'manage' })}`, { waitUntil: 'networkidle' })
  await page.getByRole('combobox', { name: /^Target release/ }).click()
  await page.locator(`[role=option][data-value="${args.release}"]`).click()
  await page.getByText('Connector ready', { exact: true }).waitFor()
  phase = 'discover'
  save('discover.intent', { at: new Date().toISOString(), deployment: args.deployment, release: args.release, target: args.target, replay: false })
  let pending = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === path + '/discover')
  await page.getByRole('button', { name: 'Discover sources', exact: true }).click()
  let r = await pending, data = await r.json(); save('discover.response.private', { status: r.status(), body: data })
  assert.equal(r.status(), 200); assert.equal(data.sources.length, expectedCount)
  for (const [index, source] of data.sources.entries()) {
    assert.ok(source.registeredSourceId == null)
    currentSource = { ...source }; phase = `register-${index}`
    const row = page.getByRole('row').filter({ hasText: source.displayName }).filter({ has: page.getByRole('button', { name: 'Register', exact: true }) })
    await row.getByRole('button', { name: 'Register', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Register approved document source', exact: true })
    await dialog.getByLabel(/^Source category/).fill('policy')
    await dialog.getByLabel('I confirm this file is approved for anonymous public answers.', { exact: true }).check()
    save(`source-${index}-register.intent`, { at: new Date().toISOString(), objectReference: source.objectReference, publicApproved: true, replay: false })
    pending = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === path + '/sources')
    await dialog.getByRole('button', { name: 'Register source', exact: true }).click()
    r = await pending; const registered = await r.json(); save(`source-${index}-register.response.private`, { status: r.status(), body: registered })
    assert.ok([200, 201].includes(r.status())); assert.ok(registered.sourceId)
    currentSource.sourceId = registered.sourceId
    await dialog.waitFor({ state: 'hidden' })
    phase = `index-${index}`
    save(`source-${index}-index.intent`, { at: new Date().toISOString(), sourceId: registered.sourceId, replay: false })
    pending = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === `${path}/sources/${registered.sourceId}/index`)
    await page.getByRole('button', { name: 'Index', exact: true }).click()
    r = await pending; const indexed = await r.json(); save(`source-${index}-index.response.private`, { status: r.status(), body: indexed })
    assert.ok([200, 202].includes(r.status())); assert.equal(indexed.outcome, 'ACCEPTED')
    report.sources.push({ sourceId: registered.sourceId, objectReference: source.objectReference })
  }
  phase = 'observe-completed-indexing'
  const deadline = Date.now() + 180000
  let actual
  do {
    actual = await get(path + '/sources')
    if (actual.length === expectedCount && actual.every(source => source.status === 'ACTIVE' && source.activeVersion === 1 && !source.failureCode)) break
    assert.ok(Date.now() < deadline, 'Indexing did not reach ACTIVE; accepted queue work is not completion')
    assert.ok(actual.every(source => !source.failureCode), 'Indexing failure remains visible')
    await page.waitForTimeout(3000)
  } while (true)
  save('active-sources.actual', actual)
  await page.reload({ waitUntil: 'networkidle' })
  assert.deepEqual(report.blocked, []); assert.deepEqual(report.errors, [])
  await page.screenshot({ path: `${args.root}/indexed-policies.png`, fullPage: true })
  report.status = 'PASS'; report.activeSources = actual.length
} catch (error) {
  report.status = 'NOT_ACCEPTED'; report.failure = { phase, message: error.message.slice(0, 1600) }; process.exitCode = 1
  await page?.screenshot({ path: `${args.root}/failure.png`, fullPage: true }).catch(() => {})
} finally {
  await browser.close(); report.finishedAt = new Date().toISOString(); save('report.actual', report); console.log(JSON.stringify(report))
}
