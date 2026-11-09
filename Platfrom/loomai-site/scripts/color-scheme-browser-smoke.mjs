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
const origin = process.env.LOOMAI_SITE_SMOKE_ORIGIN || `http://127.0.0.1:${port}`
const server = process.env.LOOMAI_SITE_SMOKE_ORIGIN ? null : spawn(process.execPath, ['deploy/container/server.mjs'], {
  cwd: root, env: { ...process.env, PORT: String(port), LOOMAI_SITE_DIST_DIR: fileURLToPath(new URL('../dist/', import.meta.url)) }, stdio: 'pipe',
})
let browser
const errors = [], checks = []
const key = 'loomai-site-color-scheme'
const screenshotDir = new URL(process.env.LOOMAI_SITE_SMOKE_OUTPUT_DIR || '../test-results/color-scheme/', import.meta.url)
const theme = page => page.locator('html').getAttribute('data-theme')
async function checkEntryCards(page) {
  const cards = page.locator('.business-hero__actions')
  const founder = cards.getByRole('link', { name: 'Talk to Mo', exact: true })
  const developer = cards.getByRole('link', { name: /Developer\?/ })
  assert.equal(await developer.getAttribute('href'), 'https://developer.loomai.pro/')
  await developer.getByText('Configure your Smart Brain', { exact: true }).waitFor()
  await founder.getByText('AI Enablement Engineer', { exact: true }).waitFor()
  for (const image of await cards.locator('img').all()) {
    await image.evaluate(element => element.decode())
    assert.ok(await image.evaluate(element => element.naturalWidth > 0 && element.complete))
  }
  const founderBox = await founder.boundingBox()
  const developerBox = await developer.boundingBox()
  assert.ok(founderBox && developerBox)
  assert.equal(founderBox.y, developerBox.y)
  assert.equal(founderBox.height, developerBox.height)
  assert.ok(founderBox.x + founderBox.width < developerBox.x)
  assert.ok(founderBox.height <= 72, 'Invitations must remain compact, including narrow screens')
  if (page.viewportSize().width > 672) assert.ok(founderBox.width <= 240 && developerBox.width <= 260, 'Desktop invitations must not expand into full-width cards')
  assert.equal(await cards.evaluate(element => [...element.querySelectorAll('a, strong, small, img')].some(child => {
    const box = child.getBoundingClientRect()
    const parent = child.closest('a').getBoundingClientRect()
    return box.left < parent.left || box.right > parent.right || box.top < parent.top || box.bottom > parent.bottom
  })), false, 'Invitation card content must stay inside its card')
  await founder.click()
  await page.locator('[data-booking-panel]').waitFor({ state: 'visible' })
  await page.getByRole('button', { name: 'Close integration panel', exact: true }).click()
  await page.locator('[data-booking-panel]').waitFor({ state: 'hidden' })
}
try {
  for (let i = 0; ; i++) {
    try { if ((await fetch(origin + '/health')).ok) break } catch {}
    assert.ok(i < 100, 'Site server did not start')
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  browser = await chromium.launch({ headless: true })
  await mkdir(screenshotDir, { recursive: true })
  const context = await browser.newContext({ colorScheme: 'light', reducedMotion: 'reduce', viewport: { width: 1440, height: 1000 } })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(origin)
  const developerLink = page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: 'Developers', exact: true })
  assert.equal(await developerLink.getAttribute('href'), 'https://developer.loomai.pro/')
  for (const width of [1440, 1024, 960]) {
    await page.setViewportSize({ width, height: 1000 })
    await developerLink.waitFor({ state: 'visible' })
    const brand = await page.locator('.site-header__brand').boundingBox()
    const navigation = await page.locator('.site-header__nav').boundingBox()
    const actions = await page.locator('.site-header__actions').boundingBox()
    assert.ok(brand && navigation && actions && brand.x + brand.width <= navigation.x && navigation.x + navigation.width <= actions.x, `Header overlap at ${width}px`)
    await checkEntryCards(page)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  checks.push('homepage-developer-link-and-desktop-header-fit')
  checks.push('paired-invitation-cards-loaded-media-fit-and-founder-dialog')
  assert.equal(await theme(page), 'light')
  await page.screenshot({ path: fileURLToPath(new URL('homepage-light-viewport.png', screenshotDir)) })
  await page.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
  assert.equal(await theme(page), 'dark')
  await checkEntryCards(page)
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

  const mobile = await browser.newContext({ colorScheme: 'dark', reducedMotion: 'reduce', viewport: { width: 390, height: 844 } })
  const mobilePage = await mobile.newPage()
  await mobilePage.goto(origin)
  assert.equal(await theme(mobilePage), 'light')
  for (const width of [320, 390, 768]) {
    await mobilePage.setViewportSize({ width, height: 844 })
    await checkEntryCards(mobilePage)
    assert.equal(await mobilePage.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `Card layout at ${width}px`)
  }
  await mobilePage.setViewportSize({ width: 390, height: 844 })
  await mobilePage.getByRole('button', { name: 'Switch to dark mode', exact: true }).click()
  await mobilePage.getByRole('button', { name: 'Switch to light mode', exact: true }).waitFor()
  await checkEntryCards(mobilePage)
  assert.equal(await mobilePage.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  await mobilePage.screenshot({ path: fileURLToPath(new URL('homepage-mobile-dark.png', screenshotDir)), fullPage: true })
  await mobilePage.screenshot({ path: fileURLToPath(new URL('homepage-mobile-dark-viewport.png', screenshotDir)) })
  await mobilePage.getByRole('button', { name: 'Open navigation', exact: true }).click()
  await mobilePage.getByRole('dialog').waitFor()
  const mobileDeveloper = mobilePage.getByRole('navigation', { name: 'Mobile navigation' }).getByRole('link', { name: 'Developers', exact: true })
  await mobileDeveloper.waitFor({ state: 'visible' })
  assert.equal(await mobileDeveloper.getAttribute('href'), 'https://developer.loomai.pro/')
  await mobilePage.getByRole('button', { name: 'Close navigation', exact: true }).click()
  await mobilePage.getByRole('button', { name: 'Switch to light mode', exact: true }).click()
  assert.equal(await theme(mobilePage), 'light')
  await mobilePage.screenshot({ path: fileURLToPath(new URL('homepage-mobile-light.png', screenshotDir)), fullPage: true })
  await mobilePage.screenshot({ path: fileURLToPath(new URL('homepage-mobile-light-viewport.png', screenshotDir)) })
  await mobile.close()
  checks.push('mobile-navigation-and-theme-toggle')
  if (process.env.LOOMAI_SITE_SMOKE_ORIGIN) {
    const referralContext = await browser.newContext()
    const referral = await referralContext.newPage()
    referral.on('pageerror', error => errors.push(error.message))
    await referral.goto(origin)
    await referral.locator('.developer-entry').click()
    await referral.waitForURL('https://developer.loomai.pro/')
    await referral.getByRole('heading', { level: 1 }).waitFor()
    await referral.goto(origin)
    await referral.waitForURL('https://developer.loomai.pro/')
    await referralContext.close()
    checks.push('live-developer-card-navigation-and-remembered-entry')
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ status: 'PASS', checks, screenshots: fileURLToPath(screenshotDir) }, null, 2))
} finally {
  await browser?.close()
  if (server) {
    const stopped = once(server, 'exit')
    server.kill('SIGTERM')
    await stopped
  }
}
