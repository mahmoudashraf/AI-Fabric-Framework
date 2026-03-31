import { chromium } from 'playwright'

const baseUrl = process.env.PLATFORM_UI_BASE_URL ?? 'http://localhost:5173'
const screenshotDir = process.env.PLATFORM_UI_SMOKE_SCREENSHOT_DIR

function assertIncludes(haystack, needle, label) {
  if (!haystack.includes(needle)) {
    throw new Error(`Missing ${label}: expected to find "${needle}"`)
  }
}

async function maybeLogin(page) {
  const loginHeading = page.getByText('Platform sign-in')
  if (!(await loginHeading.isVisible().catch(() => false))) {
    return
  }

  const email = process.env.PLATFORM_UI_EMAIL
  const password = process.env.PLATFORM_UI_PASSWORD
  const apiKey = process.env.PLATFORM_UI_API_KEY

  const emailField = page.getByLabel('Email')
  if (email && password && (await emailField.isVisible().catch(() => false))) {
    await emailField.fill(email)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.getByText('Configurable AI Enablement Platform').waitFor()
    return
  }

  if (!apiKey) {
    throw new Error('Platform UI requires authentication. Set PLATFORM_UI_EMAIL/PLATFORM_UI_PASSWORD or PLATFORM_UI_API_KEY.')
  }

  await page.getByLabel('Platform API key').fill(apiKey)
  await page.getByRole('button', { name: 'Continue with API key' }).click()
  await page.getByText('Configurable AI Enablement Platform').waitFor()
}

async function captureScreenshot(page, name) {
  if (!screenshotDir) {
    return
  }
  await page.screenshot({ path: `${screenshotDir}/${name}.png`, fullPage: true })
}

async function assertRevisionsPage(page) {
  await page.goto(`${baseUrl}/revisions`, { waitUntil: 'networkidle' })
  await maybeLogin(page)
  await page.getByText('Railway plan preview').waitFor()
  await page.getByText('Artifact bundle').waitFor()

  const body = await page.locator('body').innerText()
  assertIncludes(body, 'Railway plan preview', 'revisions heading')
  assertIncludes(body, 'Artifact bundle', 'artifact bundle section')
  assertIncludes(body, 'expires=', 'signed artifact expiry parameter')
  assertIncludes(body, 'sig=', 'signed artifact signature parameter')
  assertIncludes(body, 'AI_ACTIONS_CATALOG_PATH', 'runtime artifact env binding')

  await captureScreenshot(page, 'phase18-revisions')
}

async function assertDiagnosticsPage(page) {
  await page.goto(`${baseUrl}/diagnostics`, { waitUntil: 'networkidle' })
  await page.getByText('Latest provisioning evidence').waitFor()

  const body = await page.locator('body').innerText()
  assertIncludes(body, 'Artifact strategy: SIGNED_REMOTE_CONFIG_BUNDLES', 'signed artifact strategy')
  assertIncludes(body, 'Artifact URLs', 'artifact urls section')
  assertIncludes(body, 'sig=', 'signed artifact signature parameter in diagnostics')
  assertIncludes(body, 'run_verification', 'recorded release progress step')

  await captureScreenshot(page, 'phase18-diagnostics')
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })

try {
  await assertRevisionsPage(page)
  await assertDiagnosticsPage(page)
  console.log(JSON.stringify({
    ok: true,
    baseUrl,
    checks: [
      'revisions_signed_artifact_bundle',
      'diagnostics_signed_artifact_strategy',
      'diagnostics_release_progress',
    ],
  }, null, 2))
} finally {
  await browser.close()
}
