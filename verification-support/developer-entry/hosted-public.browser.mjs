import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const require = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))
const { chromium } = require('playwright')
const output = fileURLToPath(new URL('../../output/developer-entry/hosted/', import.meta.url))
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const results = []
try {
  for (const width of [1440, 390, 768]) {
    const page = await browser.newPage({ viewport: { width, height: 1000 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const response = await page.goto('https://developer.loomai.pro/')
    assert.equal(response.status(), 200)
    await page.getByRole('heading', { name: 'Smart Brain for your app.', exact: true }).waitFor()
    assert.equal(await page.locator('.dev-capability').count(), 8)
    assert.equal(await page.locator('.dev-starter').count(), 2)
    assert.equal(await page.locator('span.dev-brand').count(), 2)
    assert.equal(await page.locator('.dev-brand[href], a:has(.dev-brand), button:has(.dev-brand)').count(), 0)
    assert.equal(await page.getByRole('navigation', { name: 'Developer account' }).getByRole('link', { name: 'Sign in', exact: true, includeHidden: true }).getAttribute('href'),
      'https://console.loomai.pro/sign-in?entryPoint=DEVELOPER')
    assert.equal(await page.getByRole('navigation', { name: 'Developer account' }).getByRole('link', { name: 'Open Console', exact: true }).getAttribute('href'),
      'https://console.loomai.pro/sign-in?entryPoint=DEVELOPER')
    for (const image of await page.locator('.dev-landing img').all()) {
      await image.scrollIntoViewIfNeeded()
      await image.evaluate(img => img.decode())
      assert.equal(await image.evaluate(img => img.complete && img.naturalWidth > 0), true)
    }
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    assert.equal(await page.getByRole('link', { name: 'Build a customer experience', exact: true }).getAttribute('href'),
      'https://console.loomai.pro/kits/new/ai-customer-experience?entryPoint=DEVELOPER')
    assert.equal(await page.getByRole('link', { name: 'Build a support team', exact: true }).getAttribute('href'),
      'https://console.loomai.pro/kits/new/ai-support-experience?entryPoint=DEVELOPER')
    for (const button of await page.locator('.dev-capability').all()) {
      await button.click()
      await page.getByRole('dialog').waitFor()
      await page.getByRole('button', { name: 'Close capability preview', exact: true }).click()
      await page.getByRole('dialog').waitFor({ state: 'hidden' })
    }
    for (const button of await page.locator('.dev-app-targets > button, .dev-step-button').all()) {
      await button.click()
      const dialog = page.getByRole('dialog')
      await dialog.waitFor()
      assert.equal(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1), true)
      const cta = dialog.getByRole('link', { name: 'Choose your starting point' })
      assert.equal(await cta.evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(99, 239, 207)')
      await dialog.getByRole('button', { name: 'Close capability preview' }).click()
      await dialog.waitFor({ state: 'hidden' })
    }
    await page.getByRole('button', { name: /AI models Your choice/ }).click()
    const models = page.getByRole('dialog')
    assert.equal(await models.locator('.dev-option-grid li').count(), 5)
    for (const img of await models.locator('img').all()) await img.evaluate(image => image.decode())
    await page.waitForFunction(() => getComputedStyle(document.querySelector('.MuiDialog-container')).opacity === '1')
    await models.screenshot({ path: output + `models-${width}.png`, animations: 'disabled' })
    await models.getByRole('button', { name: 'Close capability preview' }).click()
    await models.waitFor({ state: 'hidden' })
    await page.locator('.dev-app-targets').getByRole('button', { name: 'Backend', exact: true }).click()
    const api = page.getByRole('dialog')
    assert.ok((await api.locator('pre code').innerText()).includes('/api/chat/me/conversations/$CONVERSATION_ID/messages'))
    await api.getByRole('button', { name: 'Close capability preview' }).click()
    await api.waitFor({ state: 'hidden' })
    await page.screenshot({ path: output + `developer-${width}.png`, fullPage: true })
    assert.deepEqual(errors, [])
    results.push({ host: 'developer.loomai.pro', width, https: true, capabilityPreviews: 8, integrationPreviews: 3, setupPreviews: 4, modelProviderIcons: 5, backendExample: true, starters: 2, imagesLoaded: true, overflow: false, browserErrors: errors })
    await page.close()
  }
  for (const [name, origin] of [
    ['production', 'https://console.loomai.pro'],
    ['staging', 'https://loomai-platform-ui.46.224.145.148.sslip.io'],
  ]) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    await page.goto(origin + '/')
    await page.locator('.ek-public-page').waitFor()
    assert.equal(await page.locator('[data-developer-landing]').count(), 0)
    const config = await page.evaluate(async () => (await fetch('/runtime-config.js')).text())
    assert.ok(!config.includes('uiProfile'))
    assert.ok(config.includes(`"environment":"${name}"`))
    results.push({ host: new URL(origin).host, ordinaryBusinessEntry: true, obsoleteProfileAbsent: true })
    await page.close()
  }
  const response = await fetch('https://developer.loomai.pro/sign-in?kit=ai-support-experience&entryPoint=DEVELOPER', { redirect: 'manual' })
  assert.equal(response.status, 302)
  assert.equal(response.headers.get('location'), 'https://console.loomai.pro/sign-in?kit=ai-support-experience&entryPoint=DEVELOPER')
  results.push({ canonicalIdentityRedirect: true, selectedKitRetained: true })
  const general = await fetch('https://developer.loomai.pro/sign-in?entryPoint=DEVELOPER', { redirect: 'manual' })
  assert.equal(general.status, 302)
  assert.equal(general.headers.get('location'), 'https://console.loomai.pro/sign-in?entryPoint=DEVELOPER')
  results.push({ generalDeveloperIdentityRedirect: true })
  await writeFile(output + 'report.json', JSON.stringify({ checkedAt: new Date().toISOString(), results }, null, 2))
  console.log(JSON.stringify({ passed: results.length, results }))
} finally { await browser.close() }
