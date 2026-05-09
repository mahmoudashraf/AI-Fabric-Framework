import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const rootDir = path.resolve(__dirname, '..')
const repoRoot = path.resolve(rootDir, '..', '..')
const requireFromPlatformUi = createRequire(path.join(repoRoot, 'Platfrom/ui/package.json'))
const { chromium } = requireFromPlatformUi('playwright')
const port = Number(process.env.BROWSER_SMOKE_PORT || 4191)
const baseUrl = `http://127.0.0.1:${port}`
const screenshotDir = process.env.BROWSER_SMOKE_SCREENSHOT_DIR || '/tmp/loomai-landing-site'

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

async function waitForServer(processHandle) {
  const deadline = Date.now() + 8000
  while (Date.now() < deadline) {
    if (processHandle.exitCode !== null) {
      throw new Error(`server exited early with code ${processHandle.exitCode}`)
    }
    try {
      const response = await fetch(`${baseUrl}/health`)
      if (response.ok) {
        return
      }
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 200))
    }
  }
  throw new Error('server did not become healthy')
}

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    bodyScrollWidth: document.body.scrollWidth,
    bodyClientWidth: document.body.clientWidth
  }))
  assert(
    overflow.scrollWidth <= overflow.clientWidth + 2 && overflow.bodyScrollWidth <= overflow.bodyClientWidth + 2,
    `${label} has horizontal overflow: ${JSON.stringify(overflow)}`
  )
}

async function assertPage(page, label, expectedHeading) {
  await page.waitForLoadState('networkidle')
  await page.getByRole('heading', { name: expectedHeading }).first().waitFor({ state: 'visible' })
  await assertNoHorizontalOverflow(page, label)
  const visibleButtons = await page.locator('.button').evaluateAll((buttons) =>
    buttons.map((button) => {
      const rect = button.getBoundingClientRect()
      return {
        text: button.textContent?.trim(),
        width: rect.width,
        height: rect.height
      }
    })
  )
  for (const button of visibleButtons) {
    assert(button.width >= 40 && button.height >= 36, `${label} has collapsed button: ${button.text}`)
  }
}

async function runBrowserChecks() {
  const browser = await chromium.launch()
  try {
    await mkdir(screenshotDir, { recursive: true })
    const desktop = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true })

    const merchantDesktop = await desktop.newPage()
    await merchantDesktop.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' })
    await assertPage(merchantDesktop, 'merchant desktop', 'Loom Companion for Shopify stores.')
    await merchantDesktop.screenshot({ path: path.join(screenshotDir, 'merchant-desktop.png'), fullPage: true })

    const merchantMobile = await mobile.newPage()
    await merchantMobile.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' })
    await assertPage(merchantMobile, 'merchant mobile', 'Loom Companion for Shopify stores.')
    await merchantMobile.screenshot({ path: path.join(screenshotDir, 'merchant-mobile.png'), fullPage: true })

    const partnerDesktop = await desktop.newPage()
    await partnerDesktop.setExtraHTTPHeaders({ 'X-Forwarded-Host': 'partners.localhost' })
    await partnerDesktop.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' })
    await assertPage(partnerDesktop, 'partner desktop', 'This is what you help clients launch.')
    await partnerDesktop.screenshot({ path: path.join(screenshotDir, 'partner-desktop.png'), fullPage: true })

    const partnerMobile = await mobile.newPage()
    await partnerMobile.setExtraHTTPHeaders({ 'X-Forwarded-Host': 'partners.localhost' })
    await partnerMobile.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' })
    await assertPage(partnerMobile, 'partner mobile', 'This is what you help clients launch.')
    await partnerMobile.screenshot({ path: path.join(screenshotDir, 'partner-mobile.png'), fullPage: true })
  } finally {
    await browser.close()
  }
}

const server = spawn(process.execPath, ['server.mjs'], {
  cwd: rootDir,
  env: {
    ...process.env,
    PORT: String(port),
    LOOMAI_LEADS_FILE: 'disabled'
  },
  stdio: ['ignore', 'pipe', 'pipe']
})

const logs = []
server.stdout.on('data', (chunk) => logs.push(chunk.toString('utf8')))
server.stderr.on('data', (chunk) => logs.push(chunk.toString('utf8')))

try {
  await waitForServer(server)
  await runBrowserChecks()
  console.log(`LoomAI landing browser smoke checks passed. Screenshots: ${screenshotDir}`)
} finally {
  server.kill('SIGTERM')
  await new Promise((resolve) => server.once('exit', resolve))
  if (server.exitCode && server.exitCode !== 0 && server.exitCode !== null) {
    throw new Error(`server exited with ${server.exitCode}: ${logs.join('')}`)
  }
}
