import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const require = createRequire(new URL('../../Platfrom/ui/package.json', import.meta.url))
const { chromium } = require('playwright')
const environment = process.argv[2] ?? 'production'
assert.ok(['production', 'staging'].includes(environment))
assert.ok(process.env.DEVELOPER_LOGIN_FIXTURE, 'Provide a private verified customer credential file')
const fixture = JSON.parse(await readFile(process.env.DEVELOPER_LOGIN_FIXTURE, 'utf8'))
const origin = environment === 'production' ? 'https://console.loomai.pro' : 'https://loomai-platform-ui.46.224.145.148.sslip.io'
const api = environment === 'production' ? 'https://api.loomai.pro' : 'https://loomai-platform-backend.46.224.145.148.sslip.io'
const output = fileURLToPath(new URL('../../output/developer-entry/hosted-console/', import.meta.url))
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
page.setDefaultTimeout(30000)
const errors = [], results = []
let token
page.on('pageerror', error => errors.push(error.message))
page.on('response', async response => {
  if (response.url().includes('/auth/v1/token?grant_type=password') && response.ok()) token = (await response.json()).access_token
})
async function request(path) {
  assert.ok(token)
  const response = await context.request.get(api + path, { headers: { Authorization: 'Bearer ' + token } })
  assert.equal(response.status(), 200, 'Authenticated read failed: ' + path)
  return response.json()
}
async function minimizeSupport() {
  const button = page.getByRole('button', { name: 'Minimize support', exact: true })
  if (await button.isVisible()) await button.click()
}
async function developerCards() {
  await page.waitForFunction(() => document.querySelectorAll('[data-developer-starters] .dev-starter').length === 2)
  await page.getByRole('heading', { name: 'Customer Experience Brain', exact: true }).waitFor()
  await page.getByRole('heading', { name: 'Support Team Brain', exact: true }).waitFor()
  assert.equal(await page.locator('.ek-catalogue-card').count(), 0)
  for (const link of await page.locator('.dev-starter-button').all()) assert.equal(new URL(await link.getAttribute('href'), origin).searchParams.get('entryPoint'), 'DEVELOPER')
  await minimizeSupport()
}
try {
  if (environment === 'production') {
    await page.goto('https://developer.loomai.pro/')
    await page.getByRole('navigation', { name: 'Developer account' }).getByRole('link', { name: 'Sign in', exact: true }).click()
  } else await page.goto(origin + '/sign-in?entryPoint=DEVELOPER')
  await page.waitForURL(origin + '/sign-in?entryPoint=DEVELOPER')
  await page.getByRole('textbox', { name: 'Email', exact: true }).fill(fixture.email)
  await page.locator('input[type="password"]').fill(fixture.password)
  await page.locator('form button[type="submit"]').click()
  await page.waitForURL(origin + '/kits?entryPoint=DEVELOPER')
  await developerCards()
  const session = await request('/api/platform/auth/session')
  assert.equal(session.role, 'CUSTOMER_ADMIN')
  const before = await request('/api/customer-experience-kits')
  const beforeIds = before.map(kit => kit.id).sort()
  assert.equal(await page.locator('.ek-saved-kit').count(), before.length)
  results.push({ normalCustomerSignIn: true, developerCards: 2, existingKitsVisible: before.length })

  const nav = page.getByRole('navigation', { name: 'Customer navigation' })
  await nav.getByRole('link', { name: 'Home', exact: true }).click()
  await page.getByRole('heading', { name: 'Build your next smart app', exact: true }).waitFor()
  await developerCards()
  await page.reload()
  await developerCards()
  await page.screenshot({ path: output + environment + '-home.png', fullPage: true })
  await nav.getByRole('link', { name: 'My kits', exact: true }).click()
  await developerCards()
  await page.getByRole('link', { name: 'Explore experiences', exact: true }).click()
  await page.getByRole('heading', { name: 'Build your smart app', exact: true }).waitFor()
  await developerCards()
  results.push({ homeKitsDiscoveryAndReload: true })

  await page.getByRole('button', { name: 'Browse business kits', exact: true }).click()
  await page.locator('.ek-catalogue-card').first().waitFor()
  assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
  await page.reload()
  await page.locator('.ek-catalogue-card').first().waitFor()
  assert.equal(await page.locator('.ek-catalogue-card').count(), 3)
  await page.getByRole('button', { name: 'Browse developer starters', exact: true }).click()
  await developerCards()
  await page.goto(origin + '/kits')
  await developerCards()
  results.push({ explicitBusinessSwitchAndReturn: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await developerCards()
  for (const image of await page.locator('[data-developer-starters] img').all()) await image.evaluate(img => img.decode())
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
  await page.screenshot({ path: output + environment + '-kits-mobile.png', fullPage: true })
  await page.getByRole('link', { name: 'Build a support team', exact: true }).click()
  await page.waitForURL(origin + '/kits/new/ai-support-experience?entryPoint=DEVELOPER')
  await page.getByRole('heading', { name: 'Choose your starting point', exact: true }).waitFor()
  await page.getByText(/Developer programme · Staging only/).waitFor()
  assert.deepEqual((await request('/api/customer-experience-kits')).map(kit => kit.id).sort(), beforeIds)
  results.push({ mobileFitAndImages: true, typedStagingOnlySetup: true, resourcesCreated: 0 })

  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await page.getByRole('textbox', { name: 'Email', exact: true }).waitFor()
  await page.goto(origin + '/home')
  await page.getByRole('textbox', { name: 'Email', exact: true }).waitFor()
  assert.equal(await page.getByRole('navigation', { name: 'Customer navigation' }).count(), 0)
  results.push({ signedOutProtectedRoute: true })
  assert.deepEqual(errors, [])
  const report = { checkedAt: new Date().toISOString(), environment, results, browserErrors: errors }
  await writeFile(output + environment + '-report.json', JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report))
} catch (error) {
  await page.screenshot({ path: output + environment + '-failure.png', fullPage: true }).catch(() => {})
  console.error(JSON.stringify({ environment, path: new URL(page.url()).pathname, browserErrors: errors }))
  throw error
} finally { await context.close(); await browser.close() }
