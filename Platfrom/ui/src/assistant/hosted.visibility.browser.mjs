import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { chromium } from 'playwright'

const { CONSOLE_URL, CONSOLE_EMAIL, CONSOLE_PASSWORD, BRAIN_ID } = process.env
assert.ok(CONSOLE_URL && CONSOLE_EMAIL && CONSOLE_PASSWORD && BRAIN_ID, 'Provide an existing test account and Brain through environment variables')
const origin = new URL(CONSOLE_URL).origin
const checkScope = process.env.ASSISTANT_CHECK_SCOPE || 'full'
assert.ok(['full', 'entry'].includes(checkScope))
const evidence = process.env.ASSISTANT_EVIDENCE_DIR || join(tmpdir(), `brain-assistant-live-${Date.now()}`)
await mkdir(evidence, { recursive: true })
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
const errors = [], assistantCalls = [], writes = [], navigationConfirmations = []
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  const { pathname } = new URL(request.url())
  if (pathname.startsWith('/api/configuration-assistant/')) assistantCalls.push({ path: pathname, method: request.method() })
  if (!['GET', 'OPTIONS', 'HEAD'].includes(request.method()) && /\/draft(?:\/|$)|\/publish(?:\/|$)|\/apply(?:\/|$)/.test(pathname)) writes.push(pathname)
})
const panel = () => page.getByRole('complementary', { name: 'Build with AI' })
const button = () => page.getByRole('button', { name: 'Build with AI', exact: true })
try {
  await page.goto(`${origin}/sign-in`)
  await page.getByRole('textbox', { name: /^Email/ }).fill(CONSOLE_EMAIL)
  await page.getByLabel(/^Password/).fill(CONSOLE_PASSWORD)
  await page.locator('form').filter({ has: page.getByLabel(/^Password/) }).locator('button[type="submit"]').click()
  await page.waitForURL(url => !url.pathname.startsWith('/sign-in'), { timeout: 60000 })
  await page.goto(`${origin}/deployments?${new URLSearchParams({ resourceKind: 'deployment', resourceId: BRAIN_ID, deploymentId: BRAIN_ID })}`)
  const details = page.getByRole('complementary').filter({ has: page.getByRole('button', { name: 'Build with AI', exact: true }) })
  const entry = details.getByRole('button', { name: 'Build with AI', exact: true })
  await entry.waitFor({ timeout: 60000 })
  assert.ok(await entry.evaluate(node => node.getBoundingClientRect().height >= 38 && getComputedStyle(node).backgroundColor !== 'rgba(0, 0, 0, 0)'))
  await page.screenshot({ path: join(evidence, 'resource-build-with-ai.png') })
  await entry.click()
  await page.waitForURL(url => url.pathname === `/smart-brains/${encodeURIComponent(BRAIN_ID)}`)
  await panel().getByRole('textbox', { name: 'Message', exact: true }).waitFor({ timeout: 60000 })
  await page.goto(`${origin}/smart-brains/${encodeURIComponent(BRAIN_ID)}/models`)
  await panel().getByRole('textbox', { name: 'Message', exact: true }).waitFor({ timeout: 60000 })
  await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]')?.disabled && !document.querySelector('[data-assistant-workspace] fieldset')?.disabled)
  assert.equal(await button().getAttribute('aria-expanded'), 'true')
  assert.equal(await page.locator('[id="configuration-assistant-panel"]').count(), 1)
  await page.screenshot({ path: join(evidence, 'desktop-open.png') })
  await panel().getByRole('button', { name: 'Close assistant' }).click()
  await panel().waitFor({ state: 'hidden' })
  for (const [label, section] of checkScope === 'full' ? [['Knowledge', 'knowledge'], ['AI capabilities', 'capabilities'], ['Responses', 'responses'], ['Updates', 'updates'], ['Keys & secrets', 'secrets']] : []) {
    await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByRole('link', { name: label, exact: true }).click()
    await page.waitForFunction(expected => location.pathname === expected || [...document.querySelectorAll('[role="dialog"]')].some(node => node.textContent.includes('Leave unsaved changes?')), `/smart-brains/${encodeURIComponent(BRAIN_ID)}/${section}`)
    const discard = page.getByRole('dialog', { name: 'Leave unsaved changes?', includeHidden: true })
    if (await discard.isVisible()) {
      navigationConfirmations.push({ destination: section, reason: 'Editor reported unsaved state without test edits; explicitly discarded only its local buffer.' })
      await discard.getByRole('button', { name: 'Discard & leave', exact: true }).click()
      await discard.waitFor({ state: 'detached' })
    }
    await page.waitForURL(url => url.pathname === `/smart-brains/${encodeURIComponent(BRAIN_ID)}/${section}`)
    await page.waitForFunction(expected => document.querySelector('nav[aria-label="Smart Brain navigation"] a[aria-current="page"]')?.pathname === expected, `/smart-brains/${encodeURIComponent(BRAIN_ID)}/${section}`)
    await page.waitForFunction(() => document.querySelector('[data-assistant-workspace]')?.getAttribute('data-assistant-open') === 'false')
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset')?.disabled)
    await button().waitFor({ state: 'visible' })
    assert.equal(await button().isVisible(), true, `${label} retains the reopen control`)
  }
  if (checkScope === 'entry') {
    await page.goto(`${origin}/smart-brains/${encodeURIComponent(BRAIN_ID)}`)
    await panel().getByRole('textbox', { name: 'Message', exact: true }).waitFor({ timeout: 60000 })
  } else await button().click()
  await panel().getByText('Guide only.', { exact: false }).waitFor()
  await panel().getByRole('button', { name: 'Close assistant' }).click()
  await page.reload()
  await panel().getByRole('textbox', { name: 'Message', exact: true }).waitFor({ timeout: 60000 })
  await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]')?.disabled && !document.querySelector('[data-assistant-workspace] fieldset')?.disabled)
  assert.equal(await button().getAttribute('aria-expanded'), 'true', 'A fresh visit opens assistance again')
  await page.setViewportSize({ width: 390, height: 844 })
  await panel().waitFor()
  await page.screenshot({ path: join(evidence, 'mobile-open.png') })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
  await panel().getByRole('button', { name: 'Close assistant' }).click()
  await panel().waitFor({ state: 'hidden' })
  await button().click()
  await panel().waitFor()
  assert.deepEqual(errors, [])
  assert.deepEqual(writes, [], 'Visibility checks never save or release a draft')
  assert.equal(assistantCalls.filter(call => /\/turns|\/sources/.test(call.path)).length, 0, 'No inference or document sharing on open/navigation')
  const report = { status: 'PASS', checkScope, crossPageNavigation: checkScope === 'full', origin, brainId: BRAIN_ID, desktop: true, mobile: true, guidanceOnly: true, dismissAndReopen: true, inferenceCalls: 0, draftWrites: 0, pageErrors: errors, navigationConfirmations, at: new Date().toISOString() }
  await writeFile(join(evidence, 'result.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ ...report, evidence }))
} catch (error) {
  await page.screenshot({ path: join(evidence, 'failure.png') }).catch(() => {})
  console.error(JSON.stringify({ status: 'FAIL', origin, url: page.url(), pageErrors: errors, evidence }))
  throw error
} finally { await context.clearCookies(); await browser.close() }
