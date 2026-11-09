import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'developer-landing-vite-'))
const shots = fileURLToPath(new URL('../../../output/developer-entry/', import.meta.url))
await mkdir(shots, { recursive: true })
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, server: { host: '127.0.0.1', port: 0, hmr: false }, plugins: [{
  name: 'developer-entry-fixture',
  configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!/^\/(?:developers\/?|sign-in|kits\/new\/[^?]+)?(?:\?|$)/.test(req.url)) return next()
    res.setHeader('Content-Type', 'text/html')
    res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin,environment:"staging"}</script><script type="module" src="/src/main.tsx"></script></body></html>'))
  }) },
}] })
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
const browser = await chromium.launch({ headless: true })
const codes = ['ai-customer-experience', 'ai-support-experience']
let count = 0
async function run(name, fn, { viewport = { width: 1440, height: 1000 }, origin = base } = {}) {
  const page = await browser.newPage({ viewport })
  const errors = [], commands = []
  page.setDefaultTimeout(20000)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== origin) return route.abort()
    if (url.pathname.startsWith('/api/')) {
      if (request.method() !== 'GET') commands.push(url.pathname)
      const json = url.pathname === '/api/platform/auth/customer-config' ? { enabled: false, projectUrl: null, publicKey: null, deskClients: [] }
        : url.pathname === '/api/platform/auth/session' ? { enabled: true, authenticated: false, sessionAuthEnabled: true, apiKeyAuthEnabled: true }
          : url.pathname === '/api/public/experience-kits' ? codes.map(code => ({ code, version: '1.0.0', name: code === codes[0] ? 'AI Customer Experience' : 'AI Support Experience', summary: 'A connected AI experience', features: [], description: 'Example', experiencePackCode: code === codes[0] ? 'generic' : 'support', defaultWorkspaceName: 'My project', demoUrl: null })) : null
      return route.fulfill({ status: json ? 200 : 404, contentType: 'application/json', body: JSON.stringify(json ?? {}) })
    }
    return origin === base ? route.continue() : route.fulfill({ response: await route.fetch({ url: base + url.pathname + url.search }) })
  })
  try {
    await fn(page)
    assert.deepEqual(errors, [])
    assert.deepEqual(commands, [], 'Landing-page navigation must not create resources')
    console.log(`PASS ${name}`); count++
  } catch (error) {
    await page.screenshot({ path: join(shots, 'failure.png'), fullPage: true })
    throw error
  } finally { await page.close() }
}
try {
  await run('approved dark layout, real media and both existing Kit entry links', async page => {
    await page.goto(base + '/developers')
    await page.getByRole('heading', { name: 'Smart Brain for your app.' }).waitFor()
    assert.equal(await page.locator('[data-developer-landing]').evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(12, 18, 20)')
    assert.equal(await page.locator('.dev-capability').count(), 8)
    assert.equal(await page.locator('span.dev-brand').count(), 2)
    assert.equal(await page.locator('.dev-brand[href], a:has(.dev-brand), button:has(.dev-brand)').count(), 0)
    for (const image of await page.locator('.dev-landing img').all()) assert.equal(await image.evaluate(img => img.complete && img.naturalWidth > 0), true)
    await page.getByText('Free for 1 year', { exact: true }).waitFor()
    await page.getByText('Training by LoomAI included', { exact: true }).waitFor()
    const links = await page.locator('.dev-starter-button').evaluateAll(els => els.map(el => el.getAttribute('href')))
    assert.deepEqual(links, codes.map(code => `/kits/new/${code}?entryPoint=DEVELOPER`))
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.screenshot({ path: join(shots, 'desktop.png'), fullPage: true })
  })
  await run('all capability nodes open honest dismissible previews', async page => {
    await page.goto(base + '/developers')
    for (let i = 0; i < 8; i++) {
      await page.locator('.dev-capability').nth(i).click()
      await page.getByRole('dialog').waitFor()
      assert.equal(await page.getByRole('dialog').getByRole('link', { name: 'Choose your starting point' }).evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(99, 239, 207)', 'Portal CTA must retain readable theme tokens')
      await page.getByRole('button', { name: 'Close capability preview' }).click()
      await page.getByRole('dialog').waitFor({ state: 'hidden' })
    }
    await page.getByRole('button', { name: /Specialist teams/ }).click()
    await page.getByText(/not automatically enabled by a starter Kit/).waitFor()
    await page.getByRole('dialog').getByRole('link', { name: 'Choose your starting point' }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    assert.equal(new URL(page.url()).hash, '#developer-brains')
  })
  await run('model and retrieval options have visible names and locally loaded icons', async page => {
    await page.goto(base + '/developers')
    await page.getByRole('button', { name: /AI models Your choice/ }).click()
    let dialog = page.getByRole('dialog')
    for (const name of ['OpenAI', 'Anthropic', 'Google Gemini', 'Azure OpenAI', 'Cohere']) await dialog.getByText(name, { exact: true }).waitFor()
    assert.equal(await dialog.locator('.dev-option-grid li').count(), 5)
    for (const img of await dialog.locator('img').all()) { await img.evaluate(image => image.decode()); assert.equal(await img.evaluate(image => image.naturalWidth > 0), true) }
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.MuiDialog-container')).opacity === '1')
    await dialog.screenshot({ path: join(shots, 'models-dialog.png'), animations: 'disabled' })
    await dialog.getByRole('button', { name: 'Close capability preview' }).click()
    await dialog.waitFor({ state: 'hidden' })
    await page.getByRole('button', { name: /Retrieval Vector search/ }).click()
    dialog = page.getByRole('dialog')
    for (const name of ['Milvus', 'Qdrant', 'Pinecone', 'Weaviate', 'Lucene', 'Memory', 'ONNX']) await dialog.getByText(name, { exact: true }).waitFor()
    assert.equal(await dialog.locator('.dev-option-grid li').count(), 11)
    for (const img of await dialog.locator('img').all()) await img.evaluate(image => image.decode())
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.MuiDialog-container')).opacity === '1')
    await dialog.screenshot({ path: join(shots, 'retrieval-dialog.png'), animations: 'disabled' })
  })
  await run('web, mobile and backend previews expose real integration contracts without execution', async page => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
    await page.goto(base + '/developers')
    for (const name of ['Web app', 'Mobile app', 'Backend']) {
      await page.locator('.dev-app-targets').getByRole('button', { name, exact: true }).click()
      const dialog = page.getByRole('dialog')
      await dialog.getByRole('group', { name: 'Integration channel' }).waitFor()
      const code = await dialog.locator('pre code').innerText()
      if (name === 'Web app') { assert.ok(code.includes('data-installation-id="YOUR_INSTALLATION_ID"')); assert.ok(code.includes('/api/public/ai-workspace/install.js')) }
      if (name === 'Mobile app') { assert.equal(code, 'https://your-project.apps.loomai.pro'); await dialog.getByText('Your native interface', { exact: true }).waitFor() }
      if (name === 'Backend') {
        assert.ok(code.includes('/api/chat/me/conversations/$CONVERSATION_ID/messages'))
        assert.ok(code.includes('Idempotency-Key: $MESSAGE_ID'))
        assert.ok(code.includes('Authorization: Bearer $RUNTIME_TOKEN'))
        await dialog.getByRole('button', { name: 'Copy integration example' }).click()
        await dialog.getByRole('status').filter({ hasText: 'Copied' }).waitFor()
        assert.equal(await page.evaluate(() => navigator.clipboard.readText()), code)
        await page.waitForFunction(() => getComputedStyle(document.querySelector('.MuiDialog-container')).opacity === '1')
        await dialog.screenshot({ path: join(shots, 'backend-dialog.png'), animations: 'disabled' })
      }
      await dialog.getByRole('button', { name: 'Close capability preview' }).click()
      await dialog.waitFor({ state: 'hidden' })
    }
  })
  await run('all four setup steps open useful previews and templates retain their real entry links', async page => {
    await page.goto(base + '/developers')
    for (const name of ['Choose a template', 'Connect your tools', 'Test your Brain', 'Use it in your app']) {
      await page.locator('.dev-build-steps').getByRole('button', { name: new RegExp(name) }).click()
      const dialog = page.getByRole('dialog')
      if (name === 'Choose a template') {
        assert.equal(await dialog.locator('.dev-template-choices article').count(), 2)
        assert.deepEqual(await dialog.getByRole('link', { name: 'Use this starter' }).evaluateAll(els => els.map(el => el.getAttribute('href'))), codes.map(code => `/kits/new/${code}?entryPoint=DEVELOPER`))
        await page.waitForFunction(() => getComputedStyle(document.querySelector('.MuiDialog-container')).opacity === '1')
        await dialog.screenshot({ path: join(shots, 'templates-dialog.png'), animations: 'disabled' })
      }
      if (name === 'Connect your tools') for (const option of ['S3-compatible storage', 'HTTP JSON APIs', 'Action endpoints', 'LoomAI Desk']) await dialog.getByText(option, { exact: true }).waitFor()
      if (name === 'Test your Brain') { await dialog.getByLabel('Illustrative Brain test').waitFor(); await dialog.getByText(/This preview sends no AI requests/).waitFor() }
      if (name === 'Use it in your app') {
        await dialog.getByRole('button', { name: 'Backend', exact: true }).click()
        assert.ok((await dialog.locator('pre code').innerText()).includes('Idempotency-Key'))
      }
      await dialog.getByRole('button', { name: 'Close capability preview' }).click()
      await dialog.waitFor({ state: 'hidden' })
    }
  })
  await run('all explorer panels fit mobile without clipping names or examples', async page => {
    await page.goto(base + '/developers')
    const triggers = page.locator('.dev-capability, .dev-app-targets > button, .dev-step-button')
    assert.equal(await triggers.count(), 15)
    for (let i = 0; i < await triggers.count(); i++) {
      await triggers.nth(i).click()
      const dialog = page.getByRole('dialog')
      await dialog.waitFor()
      assert.equal(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1), true)
      for (const item of await dialog.locator('.dev-option-grid li, .dev-template-choices article, .dev-code-example').all()) assert.equal(await item.evaluate(el => el.scrollWidth <= el.clientWidth + 1), true)
      if (i === 4) {
        await page.waitForFunction(() => getComputedStyle(document.querySelector('.MuiDialog-container')).opacity === '1')
        await dialog.screenshot({ path: join(shots, 'models-dialog-mobile.png'), animations: 'disabled' })
      }
      await dialog.getByRole('button', { name: 'Close capability preview' }).click()
      await dialog.waitFor({ state: 'hidden' })
    }
  }, { viewport: { width: 390, height: 844 } })
  await run('developer intent survives the existing unauthenticated Kit route', async page => {
    await page.goto(base + '/developers')
    await page.getByRole('link', { name: 'Build a customer experience' }).click()
    await page.waitForURL('**/sign-in?kit=ai-customer-experience&entryPoint=DEVELOPER')
    await page.getByText(/staging-only Smart Brain for your app/).waitFor()
    assert.equal(await page.getByRole('link', { name: 'Back to developers' }).getAttribute('href'), '/developers')
  })
  await run('ordinary Console root retains the business entry page', async page => {
    await page.goto(base + '/')
    await page.locator('.ek-public-page').first().waitFor()
    assert.equal(await page.locator('[data-developer-landing]').count(), 0)
  })
  await run('developer hostname shows only the new entry and uses canonical Console auth', async page => {
    await page.goto('http://developer.loomai.pro/')
    await page.getByRole('heading', { name: 'Smart Brain for your app.' }).waitFor()
    assert.equal(await page.getByRole('link', { name: 'Build a support team' }).getAttribute('href'), 'https://console.loomai.pro/kits/new/ai-support-experience?entryPoint=DEVELOPER')
    assert.equal(await page.getByRole('link', { name: 'Open Console' }).getAttribute('href'), 'https://console.loomai.pro/sign-in?entryPoint=DEVELOPER')
  }, { origin: 'http://developer.loomai.pro' })
  for (const width of [390, 768, 1920]) await run(`layout fits ${width}px viewport`, async page => {
    await page.goto(base + '/developers')
    await page.getByRole('heading', { name: 'Support Team Brain' }).waitFor()
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    for (const selector of ['.dev-capability', '.dev-starter', '.dev-starter-button', '.dev-offer']) {
      for (const el of await page.locator(selector).all()) assert.equal(await el.evaluate(node => node.scrollWidth <= node.clientWidth + 1), true, selector)
    }
    await page.screenshot({ path: join(shots, `layout-${width}.png`), fullPage: true })
  }, { viewport: { width, height: 900 } })
  console.log(`Developer landing: ${count} scenarios passed`)
} finally { await browser.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
