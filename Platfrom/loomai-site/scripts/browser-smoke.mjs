import AxeBuilder from '@axe-core/playwright'
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
const screenshotDir = path.join(root, 'test-results/screenshots')
const port = 4387
const origin = `http://127.0.0.1:${port}`

await mkdir(screenshotDir, { recursive: true })

const server = spawn(process.execPath, ['deploy/container/server.mjs'], {
  cwd: root,
  env: {
    ...process.env,
    NODE_ENV: 'production',
    PORT: String(port),
    LOOMAI_SITE_DIST_DIR: path.join(root, 'dist'),
  },
  stdio: ['ignore', 'pipe', 'pipe'],
})

let serverOutput = ''
server.stdout.on('data', (chunk) => {
  serverOutput += chunk.toString()
})
server.stderr.on('data', (chunk) => {
  serverOutput += chunk.toString()
})

const waitForServer = async () => {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`${origin}/health`)
      if (response.ok) return
    } catch {
      // Server is still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`Static server did not start.\n${serverOutput}`)
}

const browser = await chromium.launch({ headless: true })

try {
  await waitForServer()

  const feedResponse = await fetch(`${origin}/research/feed.xml`)
  if (!feedResponse.headers.get('content-type')?.startsWith('application/rss+xml')) {
    throw new Error(
      `Research feed has incorrect content type: ${feedResponse.headers.get('content-type')}`,
    )
  }

  const routes = [
    '/',
    '/products',
    '/products/ai-fabric-framework',
    '/products/ai-fabric-chat-ui',
    '/experiments',
    '/research',
    '/about',
    '/connect',
  ]

  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: 'reduce',
  })
  const page = await context.newPage()

  for (const route of routes) {
    const response = await page.goto(`${origin}${route}`, { waitUntil: 'networkidle' })
    if (!response?.ok()) {
      throw new Error(`${route} returned ${response?.status()}`)
    }
    if ((await page.locator('h1').count()) !== 1) {
      throw new Error(`${route} does not have exactly one h1`)
    }
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    )
    if (overflow > 1) {
      throw new Error(`${route} has ${overflow}px horizontal overflow`)
    }
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze()
    const blocking = results.violations.filter((violation) =>
      ['critical', 'serious'].includes(violation.impact || ''),
    )
    if (blocking.length > 0) {
      throw new Error(
        `${route} has blocking accessibility violations:\n${blocking
          .map((item) => {
            const targets = item.nodes
              .slice(0, 6)
              .map((node) => `${node.target.join(' ')} (${node.failureSummary || 'no summary'})`)
              .join('\n  ')
            return `${item.id}: ${item.help}\n  ${targets}`
          })
          .join('\n')}`,
      )
    }
  }

  await page.goto(`${origin}/experiments`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: 'Governed actions' }).click()
  if (!page.url().includes('category=governed-actions')) {
    throw new Error('Experiment filter did not update the shareable URL')
  }
  const visibleExperiments = await page.locator('[data-filter-item]:visible').count()
  if (visibleExperiments !== 1) {
    throw new Error(`Expected one governed-actions experiment, found ${visibleExperiments}`)
  }

  await context.close()

  const viewports = [
    { name: '390', width: 390, height: 844 },
    { name: '768', width: 768, height: 1024 },
    { name: '1024', width: 1024, height: 900 },
    { name: '1440', width: 1440, height: 1000 },
    { name: '1536', width: 1536, height: 1024 },
  ]

  for (const viewport of viewports) {
    const responsiveContext = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
      reducedMotion: 'reduce',
    })
    const responsivePage = await responsiveContext.newPage()
    await responsivePage.goto(origin, { waitUntil: 'networkidle' })
    await responsivePage.evaluate(async () => {
      const distance = Math.max(400, Math.floor(window.innerHeight * 0.8))
      for (let y = 0; y < document.documentElement.scrollHeight; y += distance) {
        window.scrollTo(0, y)
        await new Promise((resolve) => setTimeout(resolve, 35))
      }
      window.scrollTo(0, 0)
    })
    await responsivePage.waitForTimeout(200)
    await responsivePage.screenshot({
      path: path.join(screenshotDir, `home-${viewport.name}.png`),
      fullPage: true,
      animations: 'disabled',
    })

    const firstSectionTop = await responsivePage.locator('main > section').nth(1).evaluate(
      (element) => element.getBoundingClientRect().top,
    )
    if (firstSectionTop >= viewport.height + 80) {
      throw new Error(`Homepage at ${viewport.name}px does not hint at the next section`)
    }

    if (viewport.width === 390) {
      await responsivePage.getByRole('button', { name: 'Open navigation' }).click()
      const dialog = responsivePage.getByRole('dialog')
      if (!(await dialog.isVisible())) {
        throw new Error('Mobile navigation dialog did not open')
      }
      await responsivePage.getByRole('button', { name: 'Close navigation' }).click()
    }

    await responsiveContext.close()
  }

  const visualRoutes = [
    { name: 'products', path: '/products' },
    { name: 'chat-ui', path: '/products/ai-fabric-chat-ui' },
    { name: 'experiments', path: '/experiments' },
    { name: 'experiment-detail', path: '/experiments/live-data-sync' },
    { name: 'research', path: '/research' },
    {
      name: 'research-detail',
      path: '/research/application-data-ai-evidence-alignment',
    },
    { name: 'about', path: '/about' },
    { name: 'connect', path: '/connect' },
  ]

  for (const viewport of [
    { name: 'mobile', width: 390, height: 844 },
    { name: 'desktop', width: 1440, height: 1000 },
  ]) {
    const visualContext = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
      reducedMotion: 'reduce',
    })
    const visualPage = await visualContext.newPage()
    for (const route of visualRoutes) {
      await visualPage.goto(`${origin}${route.path}`, { waitUntil: 'networkidle' })
      const overflow = await visualPage.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      if (overflow > 1) {
        throw new Error(`${route.path} has ${overflow}px horizontal overflow at ${viewport.width}px`)
      }
      const results = await new AxeBuilder({ page: visualPage })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze()
      const blocking = results.violations.filter((violation) =>
        ['critical', 'serious'].includes(violation.impact || ''),
      )
      if (blocking.length > 0) {
        throw new Error(
          `${route.path} has mobile/desktop accessibility violations at ${viewport.width}px: ${blocking
            .map((item) => item.id)
            .join(', ')}`,
        )
      }
      await visualPage.evaluate(async () => {
        const distance = Math.max(400, Math.floor(window.innerHeight * 0.8))
        for (let y = 0; y < document.documentElement.scrollHeight; y += distance) {
          window.scrollTo(0, y)
          await new Promise((resolve) => setTimeout(resolve, 25))
        }
        window.scrollTo(0, 0)
      })
      await visualPage.waitForTimeout(150)
      await visualPage.screenshot({
        path: path.join(screenshotDir, `${route.name}-${viewport.name}.png`),
        fullPage: true,
        animations: 'disabled',
      })
    }
    await visualContext.close()
  }

  console.log(`Browser smoke passed; screenshots saved to ${screenshotDir}`)
} finally {
  await browser.close()
  server.kill('SIGTERM')
}
