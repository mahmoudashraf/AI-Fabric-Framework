import { readFile, mkdir, writeFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const origin = process.env.PLATFORM_PREVIEW_URL ?? 'http://127.0.0.1:4398'
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(origin).hostname), 'Run this review against a local UI only.')
const credentialFile = process.env.PLATFORM_TEST_CREDENTIALS_FILE
if (!credentialFile) throw new Error('Provide a private credential registry through PLATFORM_TEST_CREDENTIALS_FILE.')
const registry = JSON.parse(await readFile(credentialFile, 'utf8'))
const customer = process.env.PLATFORM_TEST_CUSTOMER === 'true'
const user = process.env.PLATFORM_TEST_IDENTITY_KEY ? registry.users?.[process.env.PLATFORM_TEST_IDENTITY_KEY]
  : registry.credentials.find(item => item.email === process.env.PLATFORM_TEST_EMAIL)
if (!user) throw new Error('The requested test identity was not found in the private registry.')
const output = new URL('../../../verification-support/smart-brain-workspace-01064/local/', import.meta.url)
await mkdir(output, { recursive: true })
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()
page.setDefaultTimeout(45000)
const errors = [], writes = [], checks = []
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  const path = new URL(request.url()).pathname
  if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && path.startsWith('/api/')
    && !path.startsWith('/api/platform/auth/') && path !== '/api/platform/preferences'
    && !path.endsWith('/ai-behaviour/compose')) writes.push(`${request.method()} ${path}`)
})
try {
  await page.goto(`${origin}${customer ? '/sign-in' : '/staff/sign-in'}`)
  await page.getByLabel(/^Email/).fill(user.email)
  await page.getByLabel(/^Password/).fill(user.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).and(page.locator('[type=submit]')).click()
  await page.waitForURL(url => !url.pathname.includes('sign-in'))
  const deployments = await page.evaluate(async () => {
    const api = await import('/src/api/platformApi.ts')
    return api.fetchDeployments()
  })
  const brain = process.env.PLATFORM_PREVIEW_BRAIN_ID
    ? deployments.find(item => item.id === process.env.PLATFORM_PREVIEW_BRAIN_ID)
    : deployments.find(item => item.connectorProvisioned && item.status !== 'DRAFT')
  assert.ok(brain, 'The exact requested Brain must be accessible.')
  const root = `/smart-brains/${encodeURIComponent(brain.id)}`
  await page.goto(origin + root + '/responses')
  await page.getByRole('heading', { name: 'How your AI answers', exact: true }).waitFor()
  await page.getByRole('combobox', { name: 'Tone', exact: true }).waitFor()
  await page.waitForFunction(() => !document.body.textContent.includes('Loading response settings...'))
  if (process.env.PLATFORM_PREVIEW_REQUIRE_TARGET === 'true') {
    await page.waitForURL(url => url.searchParams.has('targetProfileId'))
  }
  await page.waitForTimeout(800)
  const target = new URL(page.url()).searchParams.get('targetProfileId')
  const sections = ['', 'responses', 'capabilities', 'knowledge', 'channels', 'human-support', 'try', 'updates', 'settings']
  if (process.env.PLATFORM_PREVIEW_DEEP === 'true') sections.push('responses/instructions', 'responses/advanced',
    'capabilities/actions', 'capabilities/add', 'knowledge/storage', 'knowledge/discover', 'knowledge/proof',
    'knowledge/retention', 'knowledge/connections', 'settings/models', 'settings/storage', 'settings/technical-files', 'settings/access', 'settings/delivery')
  for (const section of sections) {
    await page.goto(`${origin}${root}${section ? `/${section}` : ''}${target ? `?targetProfileId=${encodeURIComponent(target)}` : ''}`)
    await page.locator('[data-brain-workspace]').waitFor()
    await page.getByRole('heading', { level: 1 }).waitFor()
    await page.waitForTimeout(650)
    const layout = await page.evaluate(() => {
      const footer = document.querySelector('footer')?.getBoundingClientRect()
      return { pageOverflow: document.documentElement.scrollHeight - innerHeight,
        horizontalOverflow: document.documentElement.scrollWidth - innerWidth,
        footerVisible: Boolean(footer && footer.bottom <= innerHeight + 2) }
    })
    assert.ok(layout.pageOverflow <= 2, `${section || 'overview'} page-level scroll: ${layout.pageOverflow}`)
    assert.ok(layout.horizontalOverflow <= 2, `${section || 'overview'} horizontal overflow`)
    assert.ok(layout.footerVisible, `${section || 'overview'} footer visible`)
    await page.screenshot({ path: new URL(`${customer ? 'customer-' : ''}${section.replaceAll('/', '-') || 'overview'}-dark.png`, output).pathname })
    checks.push({ section: section || 'overview', ...layout })
  }
  if (process.env.PLATFORM_PREVIEW_DEEP === 'true') {
    await page.goto(`${origin}${root}/knowledge/storage${target ? `?targetProfileId=${encodeURIComponent(target)}` : ''}`)
    const bucket = page.getByRole('textbox', { name: 'Bucket', exact: true })
    if (await bucket.count() && await bucket.isEnabled()) {
      await bucket.fill('local-review-unsaved-example')
      await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByText('Responses', { exact: true }).click()
      await page.getByRole('dialog', { name: 'Leave unsaved changes?' }).waitFor()
      await page.getByRole('button', { name: 'Discard & leave', exact: true }).click()
      checks.push({ storageNavigation: 'guarded-and-discarded' })
    }
  }
  await page.goto(`${origin}${root}/responses${target ? `?targetProfileId=${encodeURIComponent(target)}` : ''}`)
  const tone = page.getByRole('combobox', { name: 'Tone', exact: true })
  await tone.waitFor()
  await page.waitForFunction(() => !document.body.textContent.includes('Loading response settings...'))
  if (await tone.isEnabled()) {
    await tone.click()
    await page.getByRole('option', { name: 'Professional', exact: true }).click()
    if (await page.getByRole('button', { name: 'Save draft', exact: true }).isEnabled()) {
      await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByText('Knowledge', { exact: true }).click()
      await page.getByRole('dialog', { name: 'Leave unsaved changes?' }).waitFor()
      await page.getByRole('button', { name: 'Keep editing', exact: true }).click()
      await page.getByRole('button', { name: 'Discard', exact: true }).click()
      assert.ok(new URL(page.url()).pathname.endsWith('/responses'))
      checks.push({ unsavedNavigation: 'guarded-and-discarded' })
    }
  }
  await page.getByRole('button', { name: 'Switch color theme', exact: true }).click()
  await page.screenshot({ path: new URL('responses-light.png', output).pathname })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: 'Open Smart Brain navigation', exact: true }).click()
  await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByText('AI capabilities', { exact: true }).click()
  await page.getByRole('heading', { name: 'What your AI can do' }).waitFor()
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), 'Mobile horizontal fit')
  await page.screenshot({ path: new URL('capabilities-mobile.png', output).pathname })
  assert.deepEqual(writes, [], 'No live resource writes or provider calls during visual verification')
  assert.deepEqual(errors, [], 'No browser page errors')
  const report = { at: new Date().toISOString(), localUiOnly: true, apiReadback: 'existing authorized Platform API',
    brainId: brain.id, targetProfileId: target, checks, resourceWrites: writes, pageErrors: errors }
  await writeFile(new URL(`${customer ? 'customer-' : ''}read-only-browser-report.json`, output), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))
} catch (error) {
  console.error(JSON.stringify({ pageErrors: errors, url: page.url(), visibleText: (await page.locator('body').innerText()).slice(0, 500) }))
  await page.screenshot({ path: new URL('failure.png', output).pathname }).catch(() => {})
  throw error
} finally {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: 'Sign out', exact: true }).click({ timeout: 1500 }).catch(() => {})
  await browser.close()
}
