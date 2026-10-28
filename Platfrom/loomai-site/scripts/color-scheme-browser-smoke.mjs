import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { createServer } from 'node:net'
import { once } from 'node:events'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const probe = createServer()
probe.listen(0, '127.0.0.1')
await once(probe, 'listening')
const port = probe.address().port
await new Promise(resolve => probe.close(resolve))
const origin = `http://127.0.0.1:${port}`
const server = spawn(process.execPath, ['deploy/container/server.mjs'], {
  cwd: root, env: { ...process.env, PORT: String(port), LOOMAI_SITE_DIST_DIR: fileURLToPath(new URL('../dist/', import.meta.url)) }, stdio: 'pipe',
})
let browser
const errors = [], checks = []
const key = 'loomai-site-color-scheme'
const screenshotDir = new URL('../test-results/color-scheme/', import.meta.url)
const theme = page => page.locator('html').getAttribute('data-theme')
try {
  for (let i = 0; ; i++) {
    try { if ((await fetch(origin + '/health')).ok) break } catch {}
    assert.ok(i < 100, 'Site server did not start')
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  browser = await chromium.launch({ headless: true })
  await mkdir(screenshotDir, { recursive: true })
  const context = await browser.newContext({ colorScheme: 'light', viewport: { width: 1440, height: 1000 } })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(origin)
  assert.equal(await theme(page), 'light')
  await page.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
  assert.equal(await theme(page), 'dark')
  assert.equal(await page.evaluate(key => localStorage.getItem(key), key), 'dark')
  await page.screenshot({ path: fileURLToPath(new URL('homepage-dark-viewport.png', screenshotDir)) })
  await page.screenshot({ path: fileURLToPath(new URL('homepage-dark.png', screenshotDir)), fullPage: true })
  checks.push('light-default-and-explicit-dark-toggle')
  for (const path of ['/products', '/products/loomai-platform', '/products/ai-fabric-framework', '/products/ai-fabric-chat-ui', '/experiments', '/research', '/about', '/connect']) {
    await page.goto(origin + path)
    assert.equal(await theme(page), 'dark', path)
    await page.getByRole('button', { name: 'Switch to light mode', exact: true }).waitFor()
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, path)
    for (const surface of await page.locator('.chat-capability-summary__grid article, .chat-architecture, .featured-experiment, .compact-experiment, .featured-research, .compact-research').all()) {
      assert.equal(await surface.evaluate(element => getComputedStyle(element).backgroundColor), 'rgb(29, 32, 36)', `Dark content surface at ${path}`)
    }
    if (path.startsWith('/products/')) {
      await page.screenshot({ path: fileURLToPath(new URL(path.split('/').at(-1) + '-dark.png', screenshotDir)), fullPage: true })
      await page.screenshot({ path: fileURLToPath(new URL(path.split('/').at(-1) + '-dark-viewport.png', screenshotDir)) })
    }
  }
  await page.reload()
  assert.equal(await theme(page), 'dark')
  assert.equal(await page.locator('meta[name="theme-color"]').getAttribute('content'), '#151719')
  checks.push('dark-persists-across-public-pages-and-reload')
  await page.getByRole('button', { name: 'Switch to light mode', exact: true }).click()
  await page.emulateMedia({ colorScheme: 'dark' })
  assert.equal(await theme(page), 'light')
  await page.reload()
  assert.equal(await theme(page), 'light')
  checks.push('explicit-light-overrides-dark-system-preference')
  await context.close()

  const systemContext = await browser.newContext({ colorScheme: 'dark' })
  const systemPage = await systemContext.newPage()
  await systemPage.goto(origin)
  assert.equal(await theme(systemPage), 'light')
  assert.equal(await systemPage.evaluate(key => localStorage.getItem(key), key), null)
  await systemPage.getByRole('button', { name: 'Switch to dark mode', exact: true }).waitFor()
  await systemPage.emulateMedia({ colorScheme: 'light' })
  assert.equal(await theme(systemPage), 'light')
  await systemPage.emulateMedia({ colorScheme: 'dark' })
  await systemPage.reload()
  assert.equal(await theme(systemPage), 'light')
  checks.push('new-visitors-default-light-regardless-of-system-theme')
  await systemContext.close()

  const blocked = await browser.newContext({ colorScheme: 'dark' })
  await blocked.addInitScript(() => Object.defineProperty(window, 'localStorage', {
    configurable: true, get() { throw new DOMException('Storage blocked', 'SecurityError') },
  }))
  const blockedPage = await blocked.newPage()
  blockedPage.on('pageerror', error => errors.push(error.message))
  await blockedPage.goto(origin)
  assert.equal(await theme(blockedPage), 'light')
  await blockedPage.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
  assert.equal(await theme(blockedPage), 'dark')
  checks.push('blocked-storage-does-not-break-page-or-toggle')
  await blocked.close()

  const invalid = await browser.newContext({ colorScheme: 'dark' })
  await invalid.addInitScript(key => localStorage.setItem(key, 'unknown'), key)
  const invalidPage = await invalid.newPage()
  await invalidPage.goto(origin)
  assert.equal(await theme(invalidPage), 'light')
  await invalid.close()
  checks.push('invalid-saved-theme-defaults-light')

  const mobile = await browser.newContext({ colorScheme: 'dark', viewport: { width: 390, height: 844 } })
  const mobilePage = await mobile.newPage()
  await mobilePage.goto(origin)
  assert.equal(await theme(mobilePage), 'light')
  await mobilePage.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
  await mobilePage.getByRole('button', { name: 'Switch to light mode', exact: true }).waitFor()
  assert.equal(await mobilePage.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  await mobilePage.screenshot({ path: fileURLToPath(new URL('homepage-mobile-dark.png', screenshotDir)), fullPage: true })
  await mobilePage.getByRole('button', { name: 'Open navigation', exact: true }).click()
  await mobilePage.getByRole('dialog').waitFor()
  await mobilePage.getByRole('button', { name: 'Close navigation', exact: true }).click()
  await mobilePage.getByRole('button', { name: 'Switch to light mode', exact: true }).click()
  assert.equal(await theme(mobilePage), 'light')
  await mobilePage.screenshot({ path: fileURLToPath(new URL('homepage-mobile-light.png', screenshotDir)), fullPage: true })
  await mobile.close()
  checks.push('mobile-navigation-and-theme-toggle')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ status: 'PASS', checks, screenshots: fileURLToPath(screenshotDir) }, null, 2))
} finally {
  await browser?.close()
  const stopped = once(server, 'exit')
  server.kill('SIGTERM')
  await stopped
}
