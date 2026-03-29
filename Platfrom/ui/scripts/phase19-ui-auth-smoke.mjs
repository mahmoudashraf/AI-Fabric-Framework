import { chromium } from 'playwright'

const baseUrl = process.env.PLATFORM_UI_BASE_URL ?? 'http://localhost:5173'
const email = process.env.PLATFORM_UI_EMAIL
const password = process.env.PLATFORM_UI_PASSWORD

if (!email || !password) {
  throw new Error('Set PLATFORM_UI_EMAIL and PLATFORM_UI_PASSWORD to run the Phase 19 auth smoke.')
}

function assertIncludes(haystack, needle, label) {
  if (!haystack.includes(needle)) {
    throw new Error(`Missing ${label}: expected "${needle}"`)
  }
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })

try {
  await page.goto(`${baseUrl}/deployments`, { waitUntil: 'networkidle' })
  await page.getByLabel('Email').waitFor()
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()

  await page.getByText('Configurable AI Enablement Platform').waitFor()
  const signedInBody = await page.locator('body').innerText()
  assertIncludes(signedInBody, 'Deployments', 'deployments screen')
  assertIncludes(signedInBody, 'PLATFORM_ADMIN', 'role chip')
  assertIncludes(signedInBody, 'SESSION', 'session auth mode chip')

  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.getByLabel('Email').waitFor()

  const signedOutBody = await page.locator('body').innerText()
  assertIncludes(signedOutBody, 'Platform sign-in', 'sign-in screen after logout')

  console.log(JSON.stringify({
    ok: true,
    baseUrl,
    checks: [
      'session_login',
      'session_authenticated_ui',
      'session_logout',
    ],
  }, null, 2))
} finally {
  await browser.close()
}
