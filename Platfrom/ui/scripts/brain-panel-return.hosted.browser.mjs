import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { chromium } from 'playwright'

const { CONSOLE_URL, CONSOLE_EMAIL, CONSOLE_PASSWORD, BRAIN_ID, BRAIN_NAME, APP_ID, APP_NAME, RELEASE_ID, EVIDENCE_DIR } = process.env
assert.ok(CONSOLE_URL && CONSOLE_EMAIL && CONSOLE_PASSWORD && BRAIN_ID && BRAIN_NAME && APP_ID && APP_NAME && RELEASE_ID && EVIDENCE_DIR)
await mkdir(EVIDENCE_DIR, { recursive: true })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' })
const errors = [], writes = []
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && /\/(?:draft|publish|apply|turns|sources|provision)(?:\/|$)/.test(new URL(request.url()).pathname)) writes.push(new URL(request.url()).pathname)
})
const panel = () => page.getByRole('complementary')
const back = () => panel().getByRole('button', { name: 'Back to Smart Brain', exact: true })
async function openApp() {
  await panel().getByRole('button', { name: APP_NAME, exact: true }).first().click()
  await page.waitForURL(url => url.searchParams.get('resourceKind') === 'app' && url.searchParams.get('resourceId') === APP_ID)
  await back().waitFor()
}
async function returnToBrain() {
  await back().click()
  await page.waitForURL(url => url.searchParams.get('resourceKind') === 'deployment' && url.searchParams.get('resourceId') === BRAIN_ID)
  assert.equal(new URL(page.url()).searchParams.get('releaseId'), RELEASE_ID)
  await panel().getByRole('button', { name: 'Build with AI', exact: true }).waitFor()
}
try {
  await page.goto(`${CONSOLE_URL}/sign-in`)
  await page.getByRole('textbox', { name: /^Email/ }).fill(CONSOLE_EMAIL)
  await page.getByLabel(/^Password/).fill(CONSOLE_PASSWORD)
  await page.locator('form').filter({ has: page.getByLabel(/^Password/) }).locator('button[type="submit"]').click()
  await page.waitForURL(url => !url.pathname.startsWith('/sign-in'), { timeout: 60000 })
  await page.goto(`${CONSOLE_URL}/deployments?${new URLSearchParams({ resourceKind: 'deployment', resourceId: BRAIN_ID, deploymentId: BRAIN_ID, releaseId: RELEASE_ID })}`)
  await panel().getByRole('button', { name: 'Build with AI', exact: true }).waitFor({ timeout: 60000 })
  await page.getByRole('textbox', { name: 'Search Smart Brains', exact: true }).fill(BRAIN_NAME)
  await openApp()
  await page.screenshot({ path: join(EVIDENCE_DIR, 'desktop-app-back.png'), animations: 'disabled' })
  await returnToBrain()
  assert.equal(await page.getByRole('textbox', { name: 'Search Smart Brains', exact: true }).inputValue(), BRAIN_NAME)
  await openApp()
  await page.reload()
  await back().waitFor()
  await returnToBrain()
  await page.setViewportSize({ width: 390, height: 844 })
  await openApp()
  await page.waitForFunction(() => {
    const bounds = document.querySelector('.MuiDrawer-paper')?.getBoundingClientRect()
    return bounds && Math.abs(bounds.left) < 1 && Math.abs(bounds.right - innerWidth) < 1
  })
  await page.screenshot({ path: join(EVIDENCE_DIR, 'mobile-app-back.png'), animations: 'disabled' })
  await returnToBrain()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
  assert.deepEqual(writes, [])
  assert.deepEqual(errors, [])
  const result = { status: 'PASS', origin: new URL(CONSOLE_URL).origin, brainId: BRAIN_ID, appId: APP_ID, releaseId: RELEASE_ID,
    desktop: true, mobile: true, searchPreserved: true, returnAfterReload: true, configurationWrites: 0, inferenceCalls: 0, pageErrors: errors, at: new Date().toISOString() }
  await writeFile(join(EVIDENCE_DIR, 'result.private.json'), JSON.stringify(result, null, 2), { mode: 0o600 })
  console.log(JSON.stringify(result))
} catch (error) {
  await page.screenshot({ path: join(EVIDENCE_DIR, 'failure.png'), animations: 'disabled' }).catch(() => {})
  throw error
} finally { await browser.close() }
