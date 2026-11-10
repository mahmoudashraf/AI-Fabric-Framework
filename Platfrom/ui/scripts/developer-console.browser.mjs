import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'developer-console-vite-'))
const shots = fileURLToPath(new URL('../../../output/developer-entry/console/', import.meta.url))
await mkdir(shots, { recursive: true })
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0, hmr: false }, plugins: [{
  name: 'developer-console-fixture',
  configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!/^\/(?:developers|sign-in|home|kits(?:\/[^?]*)?|experiences(?:\/[^?]*)?|deployments)(?:\?|$)/.test(req.url) && req.url !== '/') return next()
    res.setHeader('Content-Type', 'text/html')
    res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin,environment:"staging"}</script><script type="module" src="/src/main.tsx"></script></body></html>'))
  }) },
}] })
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
const codes = ['ai-customer-experience', 'dealership-ai-experience', 'ai-support-experience']
const definitions = codes.map(code => ({ code, version: '1.0.0', name: code === codes[0] ? 'AI Customer Experience' : code === codes[1] ? 'Dealership AI' : 'AI Support',
  summary: 'A connected AI experience', description: 'Example', features: [], demoUrl: null,
  experiencePackCode: code === codes[2] ? 'support' : code === codes[1] ? 'dealership' : 'generic', defaultWorkspaceName: 'My app' }))
let count = 0

async function run(name, fn, viewport = { width: 1440, height: 1100 }) {
  const page = await browser.newPage({ viewport })
  let identity = 'owner-a'
  const errors = [], commands = []
  page.setDefaultTimeout(20000)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== base) return route.abort()
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (request.method() !== 'GET') commands.push(url.pathname)
    let body = []
    if (url.pathname === '/api/platform/auth/customer-config') body = { enabled: false, projectUrl: null, publicKey: null, deskClients: [] }
    else if (url.pathname === '/api/platform/auth/session') body = { enabled: true, authenticated: Boolean(identity), sessionAuthEnabled: true, apiKeyAuthEnabled: false,
      ...(identity ? { actorId: identity, role: 'CUSTOMER_ADMIN', customerId: 'workspace-one', customerName: 'My project workspace', authenticationMode: 'SESSION', canOperateDeployments: true } : {}) }
    else if (url.pathname === '/api/customer-experience-kits/catalogue') body = definitions.map(definition => ({ definition, offers: [], ticketProviders: [] }))
    else if (url.pathname === '/api/public/experience-kits') body = definitions
    else if (url.pathname === '/api/customer-self-service/infrastructure-quota') body = { customerId: 'workspace-one', used: 0, available: 2, limit: 2, environments: [] }
    else if (url.pathname === '/api/platform/preferences') body = {}
    else if (url.pathname.includes('support')) return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Support not configured in this fixture' }) })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  try {
    await fn(page, value => { identity = value })
    assert.deepEqual(errors, [])
    assert.deepEqual(commands, [], 'Changing presentation and browsing must not mutate resources')
    console.log(`PASS ${name}`); count++
  } catch (error) {
    await page.screenshot({ path: join(shots, 'failure.png'), fullPage: true })
    throw error
  } finally { await page.close() }
}

async function developerCards(page) {
  await page.waitForFunction(() => document.querySelectorAll('[data-developer-starters] .dev-starter').length === 2)
  await page.getByRole('heading', { name: 'Customer Experience Brain', exact: true }).waitFor()
  await page.getByRole('heading', { name: 'Support Team Brain', exact: true }).waitFor()
  assert.equal(await page.locator('[data-developer-starters] .dev-starter').count(), 2)
  assert.equal(await page.locator('.ek-catalogue-card').count(), 0)
  for (const link of await page.locator('.dev-starter-button').all()) assert.equal(new URL(await link.getAttribute('href'), base).searchParams.get('entryPoint'), 'DEVELOPER')
  const minimize = page.getByRole('button', { name: 'Minimize support', exact: true })
  if (await minimize.isVisible()) await minimize.click()
}

try {
  await run('general developer Sign in retains intent and reaches developer Console cards', async (page, setIdentity) => {
    setIdentity(null)
    await page.goto(base + '/developers')
    await page.getByRole('navigation', { name: 'Developer account' }).getByRole('link', { name: 'Sign in', exact: true }).click()
    await page.waitForURL('**/sign-in?entryPoint=DEVELOPER')
    await page.getByText('Use your existing account, or create one, to build a staging-only Smart Brain for your app.').waitFor()
    // Simulate the successful identity exchange; no real credentials or provider calls.
    setIdentity('owner-a')
    await page.reload()
    await page.waitForURL('**/kits?entryPoint=DEVELOPER')
    await developerCards(page)
  })
  await run('developer view survives Home, My kits, discovery and reload', async page => {
    await page.goto(base + '/kits?entryPoint=DEVELOPER')
    await developerCards(page)
    await page.getByRole('navigation', { name: 'Customer navigation' }).getByRole('link', { name: 'Home', exact: true }).click()
    await page.waitForURL('**/home')
    await page.getByRole('heading', { name: 'Build your next smart app', exact: true }).waitFor()
    await developerCards(page)
    await page.getByRole('button', { name: 'Browse business kits', exact: true }).waitFor()
    await page.getByRole('navigation', { name: 'Customer navigation' }).getByRole('link', { name: 'My kits', exact: true }).click()
    await page.waitForURL('**/kits')
    await page.getByRole('heading', { name: 'My kits', exact: true }).waitFor()
    await developerCards(page)
    await page.getByRole('link', { name: 'Explore experiences', exact: true }).click()
    await page.getByRole('heading', { name: 'Build your smart app', exact: true }).waitFor()
    await developerCards(page)
    await page.reload()
    await developerCards(page)
    await page.screenshot({ path: join(shots, 'discovery-desktop.png'), fullPage: true })
    await page.goto(base + '/home')
    await developerCards(page)
    await page.screenshot({ path: join(shots, 'home-desktop.png'), fullPage: true })
  })
  await run('switching to business restores all existing Kit cards and is remembered', async page => {
    await page.goto(base + '/kits?entryPoint=DEVELOPER')
    await developerCards(page)
    await page.getByRole('button', { name: 'Browse business kits', exact: true }).click()
    await page.waitForURL('**/kits?entryPoint=CUSTOMER')
    await page.locator('.ek-catalogue-card').first().waitFor()
    assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
    await page.goto(base + '/experiences')
    await page.getByRole('heading', { name: 'Choose your AI experience', exact: true }).waitFor()
    await page.locator('.ek-catalogue-card').first().waitFor()
    assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
    await page.getByRole('button', { name: 'Browse developer starters', exact: true }).click()
    await developerCards(page)
  })
  await run('remembered presentation cannot leak to a different signed-in user', async (page, setIdentity) => {
    await page.goto(base + '/kits?entryPoint=DEVELOPER')
    await developerCards(page)
    setIdentity('owner-b')
    await page.goto(base + '/kits')
    await page.locator('.ek-catalogue-card').first().waitFor()
    assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
    assert.equal(await page.locator('[data-developer-starters]').count(), 0)
    setIdentity('owner-a')
    await page.reload()
    await developerCards(page)
  })
  await run('ordinary customers and public business visitors retain business discovery', async page => {
    await page.goto(base + '/kits')
    await page.locator('.ek-catalogue-card').first().waitFor()
    assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
    await page.goto(base + '/')
    await page.getByRole('heading', { name: 'Choose your AI experience', exact: true }).waitFor()
    await page.locator('.ek-catalogue-card').first().waitFor()
    assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
  })
  await run('signed-out Console discovery respects the shared developer entry but not another account', async (page, setIdentity) => {
    setIdentity(null)
    await page.context().addCookies([{ name: 'loomai-website-experience', value: 'DEVELOPER', url: base }])
    await page.goto(base + '/')
    await developerCards(page)
    setIdentity('ordinary-owner')
    await page.goto(base + '/kits')
    await page.locator('.ek-catalogue-card').first().waitFor()
    assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
  })
  await run('empty Brain resource screen offers developer starters without business template cards', async page => {
    await page.goto(base + '/deployments?entryPoint=DEVELOPER')
    await developerCards(page)
    assert.equal(await page.getByRole('link', { name: 'Create smart brain', exact: false }).getAttribute('href'), '/kits')
  })
  await run('developer cards fit mobile and keep staging-only creation links', async page => {
    await page.goto(base + '/kits?entryPoint=DEVELOPER')
    await developerCards(page)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    for (const img of await page.locator('[data-developer-starters] img').all()) await img.evaluate(image => image.decode())
    await page.screenshot({ path: join(shots, 'kits-mobile.png'), fullPage: true })
    await page.getByRole('link', { name: 'Build a support team', exact: true }).click()
    await page.waitForURL('**/kits/new/ai-support-experience?entryPoint=DEVELOPER')
  }, { width: 390, height: 844 })
  console.log(`${count} developer Console browser scenarios passed`)
} finally {
  await browser.close()
  await server.close()
  await rm(cacheDir, { recursive: true, force: true })
}
