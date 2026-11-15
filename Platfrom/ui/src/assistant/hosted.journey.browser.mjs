import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { chromium } from 'playwright'

const { CONSOLE_URL, CONSOLE_EMAIL, CONSOLE_PASSWORD, BRAIN_ID, ASSISTANT_SPEC_FILE, ASSISTANT_EVIDENCE_DIR } = process.env
assert.equal(process.env.ALLOW_ASSISTANT_DRAFT_WRITE, '1', 'This test starts paid inference and saves only the explicitly supplied fixture draft')
assert.ok(CONSOLE_URL && CONSOLE_EMAIL && CONSOLE_PASSWORD && BRAIN_ID && ASSISTANT_SPEC_FILE && ASSISTANT_EVIDENCE_DIR)
const origin = new URL(CONSOLE_URL).origin
const libraryBrainId = process.env.LIBRARY_BRAIN_ID || BRAIN_ID
const spec = JSON.parse(await readFile(ASSISTANT_SPEC_FILE, 'utf8'))
assert.ok(Object.values(spec.paths).every(path => Object.keys(path).every(method => method === 'get')), 'Use a public read-only fixture specification')
await mkdir(ASSISTANT_EVIDENCE_DIR, { recursive: true })
const save = (name, value) => writeFile(join(ASSISTANT_EVIDENCE_DIR, name), JSON.stringify(value, null, 2), { mode: 0o600 })
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1600, height: 1060 } })
const page = await context.newPage()
const errors = [], forbidden = [], sessions = new Set(), receipts = [], turns = []
let latestDraft
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  if (new URL(request.url()).pathname.endsWith('/turns') && request.method() === 'POST') turns.push({ path: new URL(request.url()).pathname, input: request.postDataJSON() })
})
page.on('response', async response => {
  const path = new URL(response.url()).pathname
  if (!response.ok()) return
  try {
    if (path.startsWith('/api/configuration-assistant/sessions')) {
      const value = await response.json()
      if (value.resourceId === BRAIN_ID && value.id && Array.isArray(value.turns)) {
        sessions.add(value.id)
        for (const turn of value.turns) if (turn.status === 'SAVED' && turn.receipt) receipts.push(turn.receipt)
      }
    }
    if (path === `/api/deployments/${BRAIN_ID}/draft` && response.request().method() === 'GET') latestDraft = await response.json()
  } catch { /* Non-JSON responses are not evidence of a save. */ }
})
await page.route('**/api/**', route => {
  const request = route.request(), path = new URL(request.url()).pathname
  if (request.method() !== 'GET' && /\/(?:publish|apply|index|provision)(?:\/|$)/.test(path)) {
    forbidden.push({ path, method: request.method() })
    return route.abort('blockedbyclient')
  }
  return route.continue()
})
const panel = () => page.getByRole('complementary', { name: 'Build with AI' })
async function terminalTurn() {
  await page.waitForFunction(() => {
    const status = [...document.querySelectorAll('[data-assistant-turn-status]')].at(-1)?.getAttribute('data-assistant-turn-status')
    return status && !['RUNNING', 'READY'].includes(status)
  }, undefined, { timeout: 240000 })
  return panel().locator('[data-assistant-turn-status]').last().getAttribute('data-assistant-turn-status')
}
try {
  await page.goto(`${origin}/sign-in`)
  await page.getByRole('textbox', { name: /^Email/ }).fill(CONSOLE_EMAIL)
  await page.getByLabel(/^Password/).fill(CONSOLE_PASSWORD)
  await page.locator('form').filter({ has: page.getByLabel(/^Password/) }).locator('button[type="submit"]').click()
  await page.waitForURL(url => !url.pathname.startsWith('/sign-in'), { timeout: 60000 })
  await page.goto(`${origin}/deployments?${new URLSearchParams({ resourceKind: 'deployment', resourceId: libraryBrainId, deploymentId: libraryBrainId })}`)
  const details = page.getByRole('complementary').filter({ has: page.getByRole('button', { name: 'Build with AI', exact: true }) })
  await details.getByRole('button', { name: 'Build with AI', exact: true }).waitFor({ timeout: 60000 })
  await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'library.png'), animations: 'disabled' })
  await details.getByRole('button', { name: 'Build with AI', exact: true }).click()
  if (libraryBrainId !== BRAIN_ID) {
    await panel().waitFor()
    await page.goto(`${origin}/smart-brains/${encodeURIComponent(BRAIN_ID)}`)
  }
  await panel().getByRole('navigation', { name: 'AI setup steps' }).getByRole('link', { name: 'Connect your tools', exact: true }).click()
  await page.getByRole('heading', { name: 'What should your AI help customers do?', exact: true }).waitFor()
  await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]')?.disabled)
  assert.equal(latestDraft?.deploymentId, BRAIN_ID)
  assert.equal(latestDraft.actionsConfig?.actions?.length ?? 0, 0, 'Never run this proof on an existing configured Brain')
  await panel().getByRole('button', { name: 'Add source', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Add documentation' })
  await dialog.locator('input[type=file]').setInputFiles(ASSISTANT_SPEC_FILE)
  await dialog.getByRole('button', { name: 'Share source', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.equal(turns.length, 0, 'Navigation and source import start no inference')
  await panel().getByRole('button', { name: 'Prepare capabilities', exact: true }).click()
  let status = await terminalTurn()
  const firstStatus = status
  if (status === 'NEEDS_INPUT') {
    await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'clarification.png') })
    await panel().getByRole('textbox', { name: 'Message', exact: true }).fill('Use all three public read operations in the reference.')
    await panel().getByRole('button', { name: 'Send', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('[data-assistant-turn-status="RUNNING"]') || document.querySelectorAll('[data-assistant-turn-status]').length >= 2)
    status = await terminalTurn()
  }
  assert.equal(status, 'SAVED', 'Inspect the real failure or clarification; never replace it with a canned result')
  await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset')?.disabled)
  await panel().getByText('Saved to draft · Not published', { exact: true }).last().waitFor()
  const deadline = Date.now() + 30000
  while ((!latestDraft?.actionsConfig?.actions?.length || !receipts.length) && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100))
  const actions = latestDraft.actionsConfig.actions
  assert.ok(actions.length > 0)
  const receipt = receipts.at(-1)
  assert.equal(receipt.resourceId, BRAIN_ID)
  assert.equal(receipt.draftId, latestDraft.id)
  assert.equal(receipt.published, false)
  const table = page.getByRole('table', { name: 'Your capabilities', exact: true })
  for (const action of actions) {
    assert.equal(action.accessMode, 'READ', 'This fixture exposes read operations only')
    assert.ok(action.displayName || action.name)
    await table.getByRole('link', { name: action.displayName || action.name.replace(/[_-]/g, ' '), exact: true }).waitFor()
  }
  await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'saved-capabilities.png'), animations: 'disabled' })
  const changes = panel().getByRole('region', { name: 'Saved changes', exact: true }).last()
  await changes.locator('summary').filter({ hasText: 'See what changed' }).click()
  assert.ok(await changes.getByRole('listitem').count() > 0)
  await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'actual-diff.png'), animations: 'disabled' })
  const turnCount = turns.length
  await panel().getByRole('button', { name: 'Close assistant', exact: true }).click()
  await panel().waitFor({ state: 'hidden' })
  await page.getByRole('button', { name: 'Build with AI', exact: true }).click()
  await panel().getByText('Saved to draft · Not published', { exact: true }).last().waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  await panel().getByRole('button', { name: 'Close assistant', exact: true }).waitFor()
  await page.waitForFunction(() => {
    const bounds = document.querySelector('#configuration-assistant-panel button[aria-label="Close assistant"]')?.getBoundingClientRect()
    return bounds && bounds.x >= 0 && bounds.right <= innerWidth + 1
  })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), true, 'The narrow-screen drawer must not overflow the viewport')
  await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'mobile-assistant.png'), animations: 'disabled' })
  await panel().getByRole('button', { name: 'Close assistant', exact: true }).click()
  await panel().waitFor({ state: 'hidden' })
  await page.waitForFunction(() => ![...document.querySelectorAll('.MuiDrawer-root')].some(element =>
    element.querySelector('#configuration-assistant-panel') && getComputedStyle(element).visibility !== 'hidden'))
  await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'mobile-capabilities.png'), animations: 'disabled' })
  await page.setViewportSize({ width: 1600, height: 1060 })
  await page.getByRole('button', { name: 'Build with AI', exact: true }).click()
  await panel().getByText('Saved to draft · Not published', { exact: true }).last().waitFor()
  assert.equal(turns.length, turnCount, 'Reopening and changing viewport do not replay inference')
  await panel().getByRole('link', { name: 'Review draft', exact: true }).last().click()
  await page.waitForURL(url => url.pathname.endsWith('/updates'))
  assert.deepEqual(forbidden, [])
  assert.deepEqual(errors, [])
  const report = { status: 'PASS', origin, brainId: BRAIN_ID, libraryBrainId, singleBrainJourney: libraryBrainId === BRAIN_ID, firstStatus, finalStatus: status, turns: turns.length, capabilities: actions.map(action => ({ name: action.name, displayName: action.displayName, accessMode: action.accessMode })), receipt, closeReopen: true, narrowScreen: true, liveChanged: false, provisioned: false, pageErrors: errors, at: new Date().toISOString() }
  await save('journey-result.private.json', report)
  console.log(JSON.stringify(report))
} catch (error) {
  await page.screenshot({ path: join(ASSISTANT_EVIDENCE_DIR, 'failure.png') }).catch(() => {})
  await save('failure.private.json', { url: page.url(), message: error.message, errors, forbidden, turns, receipts, latestDraft, at: new Date().toISOString() })
  throw error
} finally {
  await save('browser-sessions.private.json', [...sessions])
  await context.clearCookies()
  await browser.close()
}
