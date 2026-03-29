import { chromium } from 'playwright'

const baseUrl = process.env.PLATFORM_UI_BASE_URL ?? 'http://localhost:5173'

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
  if (!email || !password) {
    throw new Error('Set PLATFORM_UI_EMAIL and PLATFORM_UI_PASSWORD to run the Phase 20 UI smoke.')
  }

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.getByText('Configurable AI Enablement Platform').waitFor()
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })

try {
  const deploymentName = `Phase20 Smoke ${Date.now()}`

  await page.goto(`${baseUrl}/deployments`, { waitUntil: 'networkidle' })
  await maybeLogin(page)

  await page.getByRole('heading', { name: 'Create deployment' }).waitFor()
  await page.getByText('Dev / OpenAI / Lucene').first().click()
  await page.getByLabel('Deployment name').fill(deploymentName)
  await page.getByLabel('Environment').fill('dev')
  await page.getByRole('button', { name: '3. Create deployment' }).click()

  const deploymentCard = page.locator('[data-testid^="deployment-card-"]').filter({ hasText: deploymentName })
  await deploymentCard.first().waitFor()

  const cardText = await deploymentCard.first().innerText()
  assertIncludes(cardText, 'DRAFT', 'new deployment draft status')
  assertIncludes(cardText, 'Manage releases', 'manage releases button')
  assertIncludes(cardText, 'View diagnostics', 'diagnostics button')

  await deploymentCard.getByRole('button', { name: 'Manage releases' }).click()
  await page.waitForURL(/\/revisions\?deploymentId=/)
  const revisionsText = await page.locator('body').innerText()
  assertIncludes(revisionsText, 'Draft, publish, apply', 'revisions page heading')

  await page.goto(`${baseUrl}/deployments`, { waitUntil: 'networkidle' })
  const deploymentCardAgain = page.locator('[data-testid^="deployment-card-"]').filter({ hasText: deploymentName })
  await deploymentCardAgain.first().waitFor()
  await deploymentCardAgain.getByRole('button', { name: 'Archive' }).click()

  await page.getByText('Archive deployment').waitFor()
  await page.getByTestId('archive-confirmation-input').fill(deploymentName)
  await page.getByRole('button', { name: 'Confirm archive' }).click()

  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(750)
  if ((await deploymentCardAgain.count()) > 0 && (await deploymentCardAgain.first().isVisible().catch(() => false))) {
    throw new Error('Archived deployment card still visible in active deployments.')
  }

  await page.getByRole('checkbox', { name: 'Show archived' }).check()
  await page.getByRole('heading', { name: 'Archived deployments' }).waitFor()
  await page.waitForTimeout(1000)
  await page.locator(`text=${deploymentName}`).last().waitFor()
  const archivedText = await page.locator('body').innerText()
  assertIncludes(archivedText, deploymentName, 'archived deployment')
  assertIncludes(archivedText, 'ARCHIVED', 'archived status chip')

  console.log(JSON.stringify({
    ok: true,
    baseUrl,
    deploymentName,
    checks: [
      'customer_deployment_create',
      'deployment_lifecycle_navigation',
      'explicit_archive_confirmation',
      'archived_hidden_from_active_list',
      'archived_visible_in_archive_section',
    ],
  }, null, 2))
} finally {
  await browser.close()
}
