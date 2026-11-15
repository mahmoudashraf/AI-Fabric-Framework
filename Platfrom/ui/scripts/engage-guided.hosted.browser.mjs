import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { chromium } from 'playwright'
const { CONSOLE_URL, CONSOLE_EMAIL, CONSOLE_PASSWORD, KIT_ID, KIT_APP_ID, APP_ID, APP_NAME, BRAIN_ID, RELEASE_ID, EVIDENCE_DIR } = process.env
assert.ok(CONSOLE_URL && CONSOLE_EMAIL && CONSOLE_PASSWORD && KIT_ID && KIT_APP_ID && APP_ID && APP_NAME && BRAIN_ID && RELEASE_ID && EVIDENCE_DIR)
await mkdir(EVIDENCE_DIR, { recursive: true })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1050 }, reducedMotion: 'reduce' })
const errors = [], writes = [], reads = new Map()
page.on('pageerror', error => errors.push(error.message))
page.on('response', async response => {
  const path = new URL(response.url()).pathname
  if (response.ok() && (path === '/api/platform/customers' || path === '/api/customer-self-service/deployments'
    || /^\/api\/platform\/customers\/[^/]+\/ai-workspace-installations$/.test(path))) {
    reads.set(path, await response.json())
  }
})
page.on('request', request => {
  if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && /\/api\/(?:deployments|customer-experience-kits|platform\/customers\/[^/]+\/ai-workspace-installations|configuration-assistant\/.*\/(?:turns|sources))/.test(new URL(request.url()).pathname)) writes.push(new URL(request.url()).pathname)
})
const panel = () => page.getByRole('complementary')
async function unifiedEngage() {
  assert.equal(await page.locator('[aria-label="Engage apps"]').count(), 1, 'The customer library stays mounted under setup')
  assert.equal(await page.locator('table').count(), 0, 'Customer setup never mounts the legacy management table')
}
async function appRecordContext() {
  const apps = [...reads.entries()].find(([path]) => /^\/api\/platform\/customers\/[^/]+\/ai-workspace-installations$/.test(path))?.[1]
  const app = apps?.find(item => item.installationId === APP_ID)
  const brain = reads.get('/api/customer-self-service/deployments')?.find(item => item.deploymentId === BRAIN_ID)
  const connection = reads.get('/api/platform/customers')?.find(item => item.id === app?.customerId)?.consumers
    .find(item => item.consumerId === app.consumerId && item.boundDeploymentId === app.deploymentId && item.boundReleaseId === app.releaseId)
  assert.ok(app && brain)
  const environment = !app.releaseId ? 'Not assigned' : connection?.boundDeploymentEnvironment === 'staging' ? 'Staging'
    : connection?.boundDeploymentEnvironment === 'production' ? 'Production' : 'Unavailable'
  const row = page.getByRole('list', { name: 'Engage apps' }).getByRole('button', { pressed: true })
  await row.getByText(brain.name, { exact: true }).waitFor()
  await row.getByText(environment, { exact: true }).waitFor()
  await row.getByText('Readiness', { exact: true }).waitFor()
  const blocked = app.readinessChecks.find(item => item.status === 'BLOCKED') ?? app.readinessChecks.find(item => item.status !== 'PASSED')
  if (blocked) await row.getByText(blocked.message, { exact: true }).waitFor()
  else if (app.ready && !app.domains.some(item => item.lastVerificationError)) await row.getByText('Recorded checks passed', { exact: true }).waitFor()
  await panel().getByRole('link', { name: 'App controls', exact: true }).waitFor()
}
async function backToKit() {
  await page.getByRole('link', { name: 'Back to Kit', exact: true }).last().click()
  const confirm = page.getByRole('dialog', { name: 'Close configuration?', exact: true })
  await Promise.race([page.waitForURL(url => url.pathname === `/kits/${KIT_ID}`), confirm.waitFor()])
  if (await confirm.isVisible()) await confirm.getByRole('button', { name: 'Close panel', exact: true }).click()
  await page.waitForURL(url => url.pathname === `/kits/${KIT_ID}`)
}
try {
  await page.goto(`${CONSOLE_URL}/sign-in`)
  await page.getByRole('textbox', { name: /^Email/ }).fill(CONSOLE_EMAIL)
  await page.getByLabel(/^Password/).fill(CONSOLE_PASSWORD)
  await page.locator('form').filter({ has: page.getByLabel(/^Password/) }).locator('button[type="submit"]').click()
  await page.waitForURL(url => !url.pathname.startsWith('/sign-in'), { timeout: 60000 })
  await page.goto(`${CONSOLE_URL}/kits/${KIT_ID}`)
  await page.getByRole('button', { name: 'Inspect Engage experience', exact: true }).click()
  const kitPanel = page.getByRole('complementary', { name: 'Selected product details' })
  await page.waitForFunction(() => document.activeElement?.matches('.ek-inspector'))
  await page.getByRole('link', { name: 'Configure workspace', exact: true }).click()
  await page.getByRole('button', { name: 'Close configuration', exact: true }).waitFor()
  await unifiedEngage()
  assert.equal(new URL(page.url()).searchParams.get('resourceId'), KIT_APP_ID)
  assert.equal(new URL(page.url()).searchParams.get('kitId'), KIT_ID)
  await page.screenshot({ path: join(EVIDENCE_DIR, 'kit-configure-return.png'), animations: 'disabled' })
  await backToKit()
  await page.getByRole('link', { name: 'Preview workspace', exact: true }).click()
  const preview = page.getByRole('dialog', { name: 'Appearance & preview', exact: true })
  await preview.getByRole('link', { name: 'Back to Kit', exact: true }).waitFor()
  await preview.getByRole('textbox', { name: 'Workspace name', exact: true }).waitFor()
  await unifiedEngage()
  await page.screenshot({ path: join(EVIDENCE_DIR, 'kit-brand-preview.png'), animations: 'disabled' })
  await backToKit()
  await page.goto(`${CONSOLE_URL}/ai-workspaces?${new URLSearchParams({ resourceKind: 'app', resourceId: APP_ID, deploymentId: BRAIN_ID })}`)
  await panel().getByRole('navigation', { name: 'Engage setup steps' }).waitFor()
  await appRecordContext()
  await page.screenshot({ path: join(EVIDENCE_DIR, 'engage-desktop.png'), animations: 'disabled' })
  await panel().getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link', { name: /4\. Check and activate/ }).click()
  const checks = page.locator('[aria-label="App launch checks"]')
  await checks.waitFor()
  await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === 'App launch checks')
  await page.getByRole('button', { name: 'Close configuration', exact: true }).click()
  await page.getByRole('dialog', { name: 'Close configuration?', exact: true }).getByRole('button', { name: 'Close panel', exact: true }).click()
  await panel().getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link', { name: /2\. Brand your workspace/ }).click()
  await page.getByRole('dialog', { name: 'Appearance & preview', exact: true }).getByRole('link', { name: 'Back to Engage apps', exact: true }).click()
  await panel().getByRole('navigation', { name: 'Engage setup steps' }).waitFor()
  assert.equal(new URL(page.url()).searchParams.get('resourceId'), APP_ID)
  for (const [key, title] of [['connectInstallationId', 'Connect smart brain'], ['previewInstallationId', 'Appearance & preview']]) {
    await page.goto(`${CONSOLE_URL}/ai-workspaces?${new URLSearchParams({ view: 'manage', [key]: APP_ID })}`)
    const tool = page.getByRole('dialog', { name: title, exact: true })
    await tool.waitFor()
    await unifiedEngage()
    assert.equal(new URL(page.url()).searchParams.has('view'), false)
    await tool.getByRole('button', { name: /^(Cancel|Close preview)$/ }).click()
    await page.waitForURL(url => !url.searchParams.has('appTool') && url.searchParams.get('resourceId') === APP_ID)
    await panel().getByRole('navigation', { name: 'Engage setup steps' }).waitFor()
    await unifiedEngage()
  }
  await page.screenshot({ path: join(EVIDENCE_DIR, 'unified-engage-legacy-return.png'), animations: 'disabled' })
  await panel().getByRole('link', { name: 'App controls', exact: true }).click()
  await page.getByRole('button', { name: 'Build app with AI', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Edit app', exact: true }).waitFor()
  await page.getByRole('button', { name: 'Close configuration', exact: true }).click()
  await page.getByRole('dialog', { name: 'Close configuration?', exact: true }).getByRole('button', { name: 'Close panel', exact: true }).click()
  await page.goto(`${CONSOLE_URL}/deployments?${new URLSearchParams({ resourceKind: 'deployment', resourceId: BRAIN_ID, deploymentId: BRAIN_ID, releaseId: RELEASE_ID })}`)
  await panel().getByRole('button', { name: APP_NAME, exact: true }).first().click()
  await page.waitForURL(url => url.searchParams.get('resourceId') === APP_ID && url.searchParams.get('resourceKind') === 'app')
  await panel().getByRole('navigation', { name: 'Engage setup steps' }).getByRole('link', { name: /2\. Brand your workspace/ }).click()
  await page.getByRole('dialog', { name: 'Appearance & preview', exact: true }).getByRole('link', { name: 'Back to Smart Brain', exact: true }).click()
  await page.waitForURL(url => url.searchParams.get('resourceKind') === 'deployment' && url.searchParams.get('resourceId') === BRAIN_ID)
  assert.equal(new URL(page.url()).searchParams.get('releaseId'), RELEASE_ID)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${CONSOLE_URL}/kits/${KIT_ID}`)
  await page.getByRole('button', { name: 'Inspect Engage experience', exact: true }).click()
  await page.waitForFunction(() => {
    const element = document.querySelector('.ek-inspector'), bounds = element?.getBoundingClientRect()
    return element === document.activeElement && bounds.top >= 0 && bounds.top < innerHeight
  })
  await page.screenshot({ path: join(EVIDENCE_DIR, 'kit-mobile-inspector.png'), animations: 'disabled' })
  await page.goto(`${CONSOLE_URL}/ai-workspaces?${new URLSearchParams({ resourceKind: 'app', resourceId: APP_ID, deploymentId: BRAIN_ID })}`)
  await panel().getByRole('navigation', { name: 'Engage setup steps' }).waitFor()
  await page.waitForFunction(() => Math.abs(document.querySelector('.MuiDrawer-paper').getBoundingClientRect().left) < 1)
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
  await page.screenshot({ path: join(EVIDENCE_DIR, 'engage-mobile.png'), animations: 'disabled' })
  await panel().getByRole('button', { name: 'Close resource details', exact: true }).click()
  const mobileRow = page.getByRole('list', { name: 'Engage apps' }).getByRole('button').first()
  await mobileRow.getByText('Readiness', { exact: true }).waitFor()
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
  await page.screenshot({ path: join(EVIDENCE_DIR, 'engage-mobile-records.png'), animations: 'disabled' })
  assert.deepEqual(errors, []); assert.deepEqual(writes, [])
  const result = { status: 'PASS', origin: new URL(CONSOLE_URL).origin, kitId: KIT_ID, kitAppId: KIT_APP_ID, appId: APP_ID, brainId: BRAIN_ID,
    desktop: true, mobile: true, kitConfigureReturn: true, kitPreviewReturn: true, directEngageReturn: true, launchChecksFocus: true, brainPreviewReturn: true, headerFocus: true,
    unifiedCustomerPage: true, legacyLinkCancelReturn: true, legacyTableMounted: false, recordContextMatchesApi: true, appControlsReachable: true,
    configurationWrites: 0, inferenceCalls: 0, pageErrors: errors, at: new Date().toISOString() }
  await writeFile(join(EVIDENCE_DIR, 'result.private.json'), JSON.stringify(result, null, 2), { mode: 0o600 })
  console.log(JSON.stringify(result))
} catch (error) {
  await page.screenshot({ path: join(EVIDENCE_DIR, 'failure.png'), animations: 'disabled' }).catch(() => {})
  await writeFile(join(EVIDENCE_DIR, 'failure.private.json'), JSON.stringify({ url: page.url(), errors, writes, message: error.message }, null, 2), { mode: 0o600 })
  throw error
} finally { await browser.close() }
