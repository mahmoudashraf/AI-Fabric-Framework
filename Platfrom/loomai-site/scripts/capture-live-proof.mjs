import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const outputDir = path.resolve(__dirname, '../public/assets/experiments')

const targets = [
  {
    slug: 'ai-shopping-experience',
    url: 'https://ai-fabric.dev/demos/ai-fabric-framework',
  },
  {
    slug: 'account-resolver',
    url: 'https://ai-fabric.dev/demos/ai-fabric-account-resolver',
  },
  {
    slug: 'behavior-signals',
    url: 'https://ai-fabric.dev/demos/ai-fabric-behavior-signals',
  },
  {
    slug: 'tenant-guard',
    url: 'https://ai-fabric.dev/demos/ai-fabric-tenant-guard',
  },
  {
    slug: 'privacy-shield',
    url: 'https://ai-fabric.dev/demos/ai-fabric-privacy-shield',
  },
  {
    slug: 'live-data-sync',
    url: 'https://ai-fabric.dev/demos/ai-fabric-live-data-sync',
  },
]

await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({
  ignoreHTTPSErrors: true,
  reducedMotion: 'reduce',
  viewport: { width: 1280, height: 800 },
})

try {
  for (const target of targets) {
    const page = await context.newPage()
    await page.goto(target.url, {
      timeout: 60_000,
      waitUntil: 'domcontentloaded',
    })
    await page.waitForTimeout(2_500)
    const bodyText = (await page.locator('body').innerText()).trim()
    if (bodyText.length < 20) {
      throw new Error(`${target.slug} did not render meaningful page content`)
    }
    await page.screenshot({
      path: path.join(outputDir, `${target.slug}.png`),
      animations: 'disabled',
      fullPage: false,
    })
    console.log(`Captured ${target.slug} from ${target.url}`)
    await page.close()
  }
} finally {
  await browser.close()
}
