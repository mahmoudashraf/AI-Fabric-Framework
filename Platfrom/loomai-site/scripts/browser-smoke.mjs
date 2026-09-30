import AxeBuilder from '@axe-core/playwright'
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { createServer } from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')
const screenshotDir = path.join(root, 'test-results/screenshots')
const port = 4387
const origin = `http://127.0.0.1:${port}`
const mockPort = 4388
const mockOrigin = `http://127.0.0.1:${mockPort}`

const mockVehicles = [
  {
    id: 'veh-aster-e1', stockId: 'DEMO-1001', slug: 'aster-e1-range', make: 'Aster', model: 'E1',
    derivative: 'Long Range', registrationYear: 2025, priceGbp: 31950, priceFormatted: '£31,950.00', currency: 'GBP', mileage: 4120,
    fuelType: 'Electric', transmission: 'Automatic', bodyType: 'SUV', exteriorColour: 'Ocean blue', doors: 5,
    seats: 5, electricRangeMiles: 312, location: 'Northfield Central', lifecycleState: 'ACTIVE',
    summary: 'A quiet long-range electric SUV with a spacious cabin and straightforward everyday technology.',
    features: ['Adaptive cruise control', 'Heat pump', '360-degree camera', 'Wireless phone charging'],
    imagePath: '/assets/demos/dealership/vehicle-01.webp', sourceLabel: 'Demonstration inventory',
    sourceUpdatedAt: '2026-09-29T19:30:00Z', sourceVersion: 1,
  },
  {
    id: 'veh-aster-e2', stockId: 'DEMO-1006', slug: 'aster-e2-sport', make: 'Aster', model: 'E2',
    derivative: 'Sport Dual Motor', registrationYear: 2025, priceGbp: 39250, priceFormatted: '£39,250.00', currency: 'GBP', mileage: 3760,
    fuelType: 'Electric', transmission: 'Automatic', bodyType: 'Crossover', exteriorColour: 'Silver', doors: 5,
    seats: 5, electricRangeMiles: 276, location: 'Northfield Central', lifecycleState: 'ACTIVE',
    summary: 'A responsive dual-motor crossover with useful range and a versatile cabin.',
    features: ['Dual-motor all-wheel drive', 'Vehicle-to-load power', 'Head-up display'],
    imagePath: '/assets/demos/dealership/vehicle-04.webp', sourceLabel: 'Demonstration inventory',
    sourceUpdatedAt: '2026-09-29T19:28:00Z', sourceVersion: 1,
  },
  {
    id: 'veh-northstar-s4', stockId: 'DEMO-1002', slug: 'northstar-s4-touring', make: 'Northstar', model: 'S4',
    derivative: 'Touring Hybrid', registrationYear: 2024, priceGbp: 27400, priceFormatted: '£27,400.00', currency: 'GBP', mileage: 8920,
    fuelType: 'Hybrid', transmission: 'Automatic', bodyType: 'Estate', exteriorColour: 'Graphite', doors: 5,
    seats: 5, electricRangeMiles: null, location: 'Northfield Central', lifecycleState: 'ACTIVE',
    summary: 'A practical hybrid estate for longer journeys, luggage and low-speed electric driving around town.',
    features: ['Blind-spot monitoring', 'Heated front seats', 'Powered tailgate'],
    imagePath: '/assets/demos/dealership/vehicle-02.webp', sourceLabel: 'Demonstration inventory',
    sourceUpdatedAt: '2026-09-29T19:26:00Z', sourceVersion: 1,
  },
]

function writeMockJson(response, status, body) {
  response.writeHead(status, {
    'Access-Control-Allow-Credentials': 'true',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type, Accept, X-XSRF-TOKEN',
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
    'Access-Control-Allow-Origin': origin,
    'Cache-Control': 'no-store',
    'Content-Type': 'application/json; charset=utf-8',
  })
  response.end(JSON.stringify(body))
}

async function readMockJson(request) {
  const chunks = []
  for await (const chunk of request) {
    chunks.push(chunk)
  }
  if (chunks.length === 0) return {}
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

const mockServer = createServer(async (request, response) => {
  const url = new URL(request.url || '/', mockOrigin)
  if (request.method === 'OPTIONS') {
    writeMockJson(response, 204, {})
    return
  }

  if (url.pathname === '/api/public/vehicles') {
    let items = [...mockVehicles]
    const make = url.searchParams.get('make')
    const fuel = url.searchParams.get('fuelType')
    if (make) items = items.filter((vehicle) => vehicle.make === make)
    if (fuel) items = items.filter((vehicle) => vehicle.fuelType === fuel)
    writeMockJson(response, 200, {
      success: true,
      dealership: { id: 'dealer-demo-001', name: 'Northfield Motor House' },
      items,
      total: items.length,
      facets: {
        makes: [{ value: 'Aster', count: 2 }, { value: 'Northstar', count: 1 }],
        fuelTypes: [{ value: 'Electric', count: 2 }, { value: 'Hybrid', count: 1 }],
        bodyTypes: [{ value: 'SUV', count: 1 }, { value: 'Crossover', count: 1 }, { value: 'Estate', count: 1 }],
      },
      source: { label: 'Demonstration inventory', refreshedAt: '2026-09-29T19:30:00Z' },
      dataNotice: 'Fictional demonstration inventory. No live Auto Trader data is used.',
    })
    return
  }

  if (url.pathname === '/api/public/runtime-descriptor') {
    writeMockJson(response, 200, {
      success: true,
      ready: true,
      integrationMode: 'public-runtime-anonymous',
      chatBaseUrl: mockOrigin,
      runtimeRoutes: {
        bootstrapUrl: '/api/public/chat/session',
        queryUrl: '/api/chat/me/query',
        suggestionsUrl: '/api/chat/me/suggestions',
        authContextUrl: '/api/chat/me/auth-context',
        shellConfigUrl: '/api/chat/me/shell-config',
        conversationsUrl: '/api/chat/me/conversations',
        conversationItemUrlTemplate: '/api/chat/me/conversations/{conversationId}',
      },
      vectorSpace: 'dealer-vehicle',
    })
    return
  }

  if (url.pathname === '/api/public/chat/session' && request.method === 'POST') {
    writeMockJson(response, 200, {
      token: 'browser-smoke-token',
      tokenType: 'Bearer',
      authMode: 'PUBLIC_RUNTIME_ANONYMOUS',
      subjectType: 'ANONYMOUS_SESSION',
      sessionId: 'browser-smoke-session',
      expiresAt: '2099-01-01T00:00:00Z',
    })
    return
  }

  if (url.pathname === '/api/chat/me/auth-context') {
    writeMockJson(response, 200, {
      subjectId: 'browser-smoke-session',
      subjectType: 'ANONYMOUS_SESSION',
      authMode: 'PUBLIC_RUNTIME_ANONYMOUS',
      callerType: 'PUBLIC_BROWSER',
      sessionId: 'browser-smoke-session',
      deploymentId: 'dep-dealership-smoke',
      customerId: 'customer-dealership-smoke',
      tenantId: 'tenant-dealership-smoke',
      issuer: 'runtime-public-bootstrap',
      grantedScopes: ['chat:query'],
    })
    return
  }

  if (url.pathname === '/api/chat/me/shell-config') {
    writeMockJson(response, 200, {
      success: true,
      contractVersion: 'RUNTIME_SHELL_CONFIG_V1',
      greetingTitle: 'Northfield AI',
      greetingMessage: 'Ask about current dealership inventory.',
      defaultConversationMode: 'executor',
      allowedConversationModes: ['executor'],
      starterPrompts: [],
    })
    return
  }

  if (url.pathname === '/api/chat/me/suggestions') {
    writeMockJson(response, 200, { success: true, suggestions: ['Compare the selected vehicles'] })
    return
  }

  if (url.pathname === '/api/chat/me/query' && request.method === 'POST') {
    const payload = await readMockJson(request)
    if (payload.query === 'Request a test drive for the Aster E1') {
      writeMockJson(response, 200, {
        success: false,
        type: 'CONFIRMATION_REQUIRED',
        conversationId: 'conversation-browser-smoke',
        answer: 'Confirm the test-drive request?',
        actions: [{
          action: 'dealership_request_test_drive',
          confirmationRequired: true,
          confirmationMessage: 'Confirm the test-drive request?',
        }],
      })
      return
    }
    if (payload.query === 'Yes, confirm') {
      writeMockJson(response, 200, {
        success: true,
        type: 'ACTION_EXECUTED',
        conversationId: 'conversation-browser-smoke',
        answer: 'Test-drive request created.',
        actions: [{
          action: 'dealership_request_test_drive',
          executed: true,
        }],
      })
      return
    }
    writeMockJson(response, 200, {
      success: true,
      type: 'INFORMATION_PROVIDED',
      conversationId: 'conversation-browser-smoke',
      answer: 'The selected Aster is electric, has low mileage and is shown with current fictional dealership facts.',
      sources: [{ id: 'veh-aster-e1', title: '2025 Aster E1', entityType: 'dealer-vehicle', content: 'Electric SUV with 4,120 miles.' }],
    })
    return
  }

  if (url.pathname === '/api/chat/me/conversations' && request.method === 'GET') {
    writeMockJson(response, 200, [])
    return
  }

  if (url.pathname === '/api/public/status') {
    writeMockJson(response, 200, { success: true, inventoryCount: 3, runtimeConfigured: true })
    return
  }

  if (url.pathname === '/api/staff/session') {
    writeMockJson(response, 401, { success: false, errorCode: 'STAFF_AUTH_REQUIRED', message: 'Staff authentication is required.' })
    return
  }

  writeMockJson(response, 404, { success: false, message: 'Mock route not found.' })
})

await new Promise((resolve, reject) => {
  mockServer.once('error', reject)
  mockServer.listen(mockPort, '127.0.0.1', resolve)
})

await mkdir(screenshotDir, { recursive: true })

const server = spawn(process.execPath, ['deploy/container/server.mjs'], {
  cwd: root,
  env: {
    ...process.env,
    NODE_ENV: 'production',
    PORT: String(port),
    LOOMAI_SITE_DIST_DIR: path.join(root, 'dist'),
    DEALERSHIP_DEMO_API_BASE_URL: mockOrigin,
    DEALERSHIP_DEMO_RUNTIME_BASE_URL: mockOrigin,
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
    '/experiments/dealership-ai-experience',
    '/research',
    '/about',
    '/connect',
    '/demos/dealership-ai',
    '/demos/dealership-ai/staff',
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

  await page.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.vehicle-card')
  const initialVehicleCount = await page.locator('.vehicle-card').count()
  if (initialVehicleCount !== 3) {
    throw new Error(`Expected three dealership smoke vehicles, found ${initialVehicleCount}`)
  }
  await page.waitForFunction(() => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready')
  await page.locator('select[name="make"]').selectOption('Aster')
  await page.waitForFunction(() => document.querySelectorAll('.vehicle-card').length === 2)
  await page.locator('[data-clear-filters]').click()
  await page.waitForFunction(() => document.querySelectorAll('.vehicle-card').length === 3)
  await page.locator('.compare-check').nth(0).click()
  await page.locator('.compare-check').nth(1).click()
  if (!(await page.locator('[data-card-compare]:checked').count() === 2)) {
    throw new Error('Dealership comparison controls did not retain two selected vehicles')
  }
  await page.locator('[data-open-comparison]').click()
  if (!(await page.locator('[data-compare-dialog]').evaluate((dialog) => dialog.open))) {
    throw new Error('Dealership comparison dialog did not open')
  }
  await page.locator('[data-close-comparison]').click()
  await page.locator('[data-card-details]').first().click()
  if (!(await page.locator('[data-vehicle-dialog]').evaluate((dialog) => dialog.open))) {
    throw new Error('Dealership vehicle detail dialog did not open')
  }
  await page.locator('[data-close-vehicle-dialog]').click()
  const cardAskRequestPromise = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query?.startsWith('Tell me whether the 2025 Aster E1')
  })
  await page.locator('[data-card-ask]').first().click()
  await page.waitForFunction(() => {
    const host = document.querySelector('#max-mode-widget-shadow-host')
    return Boolean(host?.shadowRoot?.querySelector('.max-mode-widget-root'))
  })
  const cardAskPayload = (await cardAskRequestPromise).postDataJSON()
  if (cardAskPayload.mode !== 'executor') {
    throw new Error('Dealership vehicle question did not use executor mode')
  }
  if (!cardAskPayload.attachments?.some((attachment) => attachment.id === 'veh-aster-e1')) {
    throw new Error('Dealership vehicle question reached chat without its trusted vehicle attachment')
  }

  const actionRequestPromise = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Request a test drive for the Aster E1'
  })
  await page.evaluate(() => {
    window.MaxMode.sendMessage('Request a test drive for the Aster E1', {
      mode: 'executor',
      open: true,
    })
  })
  const actionRequest = await actionRequestPromise
  if (actionRequest.postDataJSON()?.mode !== 'executor') {
    throw new Error('Initial governed action request did not preserve executor mode')
  }

  const confirmButton = page.getByRole('button', { name: 'Confirm', exact: true })
  await confirmButton.waitFor()
  const confirmationRequestPromise = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Yes, confirm'
  })
  await confirmButton.click()
  const confirmationRequest = await confirmationRequestPromise
  if (confirmationRequest.postDataJSON()?.mode !== 'executor') {
    throw new Error('Confirmation follow-up did not preserve executor mode')
  }
  await page.getByText('Test-drive request created.', { exact: true }).first().waitFor()

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
    { name: 'dealership-experiment-detail', path: '/experiments/dealership-ai-experience' },
    { name: 'research', path: '/research' },
    {
      name: 'research-detail',
      path: '/research/application-data-ai-evidence-alignment',
    },
    { name: 'about', path: '/about' },
    { name: 'connect', path: '/connect' },
    { name: 'dealership-demo', path: '/demos/dealership-ai' },
    { name: 'dealership-staff', path: '/demos/dealership-ai/staff' },
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
          `${route.path} has mobile/desktop accessibility violations at ${viewport.width}px:\n${blocking
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
  await new Promise((resolve) => mockServer.close(resolve))
}
