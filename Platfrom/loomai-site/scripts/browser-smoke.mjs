import AxeBuilder from '@axe-core/playwright'
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
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
let anonymousRenewalCount = 0

const mockVehicles = [
  {
    id: 'veh-aster-e1', stockId: 'DEMO-1001', slug: 'aster-e1-motion', make: 'Aster', model: 'E1',
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
      appliedFilters: Object.fromEntries(
        ['make', 'fuelType', 'bodyType', 'maxPriceGbp', 'maxMileage']
          .map((key) => [key, url.searchParams.get(key)])
          .filter(([, value]) => value),
      ),
      dataNotice: 'Fictional demonstration inventory. No live Auto Trader data is used.',
    })
    return
  }

  if (url.pathname.startsWith('/api/public/vehicles/') && request.method === 'GET') {
    const slug = decodeURIComponent(url.pathname.slice('/api/public/vehicles/'.length))
    const vehicle = mockVehicles.find((item) => item.slug === slug)
    if (!vehicle) {
      writeMockJson(response, 404, { success: false, message: 'Vehicle was not found or is no longer active.' })
      return
    }
    writeMockJson(response, 200, {
      success: true,
      vehicle,
      source: { label: 'Demonstration inventory', refreshedAt: '2026-09-29T19:30:00Z' },
      dataNotice: 'Fictional demonstration inventory. Confirm current availability with the dealership.',
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
        renewUrl: '/api/public/chat/session/renew',
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
      expiresAt: new Date(Date.now() + 5_000).toISOString(),
    })
    return
  }

  if (url.pathname === '/api/public/chat/session/renew' && request.method === 'POST') {
    anonymousRenewalCount += 1
    writeMockJson(response, 200, {
      token: 'browser-smoke-renewed-token',
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
    if (payload.query === 'Show current electric vehicles under GBP 40,000.') {
      writeMockJson(response, 200, {
        success: true,
        type: 'ACTION_EXECUTED',
        conversationId: 'conversation-browser-smoke',
        answer: 'I found current electric vehicles under GBP 40,000.',
        actions: [{
          action: 'dealership_search_inventory',
          actionResult: {
            success: true,
            data: {
              _items: mockVehicles.slice(0, 2),
              _count: 2,
              total: 2,
              appliedFilters: { fuelType: 'Electric', maxPriceGbp: 40000, sort: 'recommended' },
              source: { label: 'Demonstration inventory', refreshedAt: '2026-09-29T19:30:00Z' },
              dataNotice: 'Fictional demonstration inventory. No live Auto Trader data is used.',
            },
          },
        }],
      })
      return
    }
    if (payload.query === 'Tell me about 2025 Aster E1 using its current dealership facts.') {
      writeMockJson(response, 200, {
        success: true,
        type: 'ACTION_EXECUTED',
        conversationId: 'conversation-browser-smoke',
        answer: 'Current details loaded for the 2025 Aster E1.',
        actions: [{
          action: 'dealership_get_vehicle',
          actionResult: {
            success: true,
            data: {
              vehicle: mockVehicles[0],
              source: { label: 'Demonstration inventory', refreshedAt: '2026-09-29T19:30:00Z' },
              dataNotice: 'Fictional demonstration inventory. Confirm current availability with the dealership.',
            },
          },
        }],
      })
      return
    }
    if (payload.query?.startsWith('Compare these selected current vehicles using dealership facts:')) {
      writeMockJson(response, 200, {
        success: true,
        type: 'ACTION_EXECUTED',
        conversationId: 'conversation-browser-smoke',
        answer: 'I compared the selected current vehicles.',
        actions: [{
          action: 'dealership_compare_vehicles',
          actionResult: {
            success: true,
            data: {
              _items: mockVehicles.slice(0, 2),
              _count: 2,
              source: { label: 'Demonstration inventory', refreshedAt: '2026-09-29T19:30:00Z' },
            },
          },
        }],
      })
      return
    }
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
    if (payload.query === 'I would like to request a test drive for 2025 Aster E1.') {
      writeMockJson(response, 200, {
        success: false,
        type: 'CONFIRMATION_REQUIRED',
        conversationId: 'conversation-browser-smoke',
        answer: 'Confirm the selected test-drive request?',
        actions: [{
          action: 'dealership_request_test_drive',
          confirmationRequired: true,
          confirmationMessage: 'Confirm the selected test-drive request?',
        }],
      })
      return
    }
    if (payload.query === 'I would like the dealership to call me about 2025 Aster E1.') {
      writeMockJson(response, 200, {
        success: true,
        type: 'INFORMATION_PROVIDED',
        conversationId: 'conversation-browser-smoke',
        answer: 'I can start a callback request for the selected vehicle.',
      })
      return
    }
    if (payload.query === 'No, cancel') {
      writeMockJson(response, 200, {
        success: true,
        type: 'INFORMATION_PROVIDED',
        conversationId: 'conversation-browser-smoke',
        answer: 'The request was cancelled.',
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

  const widgetManifestResponse = await fetch(`${origin}/vendor/max-mode-widget-manifest.json`)
  if (widgetManifestResponse.headers.get('cache-control') !== 'no-store') {
    throw new Error(`Widget manifest is cacheable: ${widgetManifestResponse.headers.get('cache-control')}`)
  }
  const widgetManifest = await widgetManifestResponse.json()
  if (widgetManifest.schemaVersion !== 'loomai-widget-bundle-v1' ||
      !/^max-mode-widget\.[a-f0-9]{16}\.iife\.js$/.test(widgetManifest.file || '')) {
    throw new Error('Widget manifest did not expose a valid content-hashed bundle')
  }
  const widgetBundleResponse = await fetch(`${origin}/vendor/${widgetManifest.file}`)
  if (widgetBundleResponse.headers.get('cache-control') !== 'public, max-age=31536000, immutable') {
    throw new Error(`Content-hashed widget is not immutable: ${widgetBundleResponse.headers.get('cache-control')}`)
  }
  const widgetBundleBytes = Buffer.from(await widgetBundleResponse.arrayBuffer())
  const widgetBundleSha256 = createHash('sha256').update(widgetBundleBytes).digest('hex')
  if (widgetBundleSha256 !== widgetManifest.sha256) {
    throw new Error('Widget manifest did not match the served bundle SHA-256')
  }
  const legacyWidgetResponse = await fetch(`${origin}/vendor/max-mode-widget.iife.js`)
  if (!legacyWidgetResponse.ok || legacyWidgetResponse.headers.get('cache-control') !== 'public, max-age=0, must-revalidate') {
    throw new Error('Legacy widget compatibility URL is missing or remains long-lived')
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
    '/demos/dealership-ai/vehicles/aster-e1-motion',
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
  await page.waitForFunction(() => ['ready', 'unavailable'].includes(
    document.querySelector('[data-runtime-state]')?.getAttribute('data-state') || '',
  ))
  const listingRuntimeState = await page.locator('[data-runtime-state]').getAttribute('data-state')
  if (listingRuntimeState !== 'ready') {
    const detail = await page.locator('[data-runtime-state-detail]').textContent()
    throw new Error(`Dealership assistant did not become ready: ${detail}`)
  }
  const loadedWidgetScript = page.locator('script[data-max-mode-bundle]')
  const loadedWidgetSource = await loadedWidgetScript.getAttribute('src')
  const loadedWidgetSha256 = await loadedWidgetScript.getAttribute('data-max-mode-bundle-sha256')
  if (loadedWidgetSource !== `/vendor/${widgetManifest.file}` || loadedWidgetSha256 !== widgetManifest.sha256) {
    throw new Error('Dealership loaded a stable or mismatched widget bundle instead of the manifest version')
  }
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

  const currentPageChips = page.locator('[data-max-mode-current-page-chip]')
  const listingAttachButton = page.getByRole('button', { name: 'Attach current page' })
  const listingAttachInsideInputShell = await listingAttachButton.evaluate((element) =>
    Boolean(element.closest('[data-max-mode-companion-input-shell]')),
  )
  if (listingAttachInsideInputShell) {
    throw new Error('Attach-current-page control is still inside the Companion input shell')
  }
  await listingAttachButton.click()
  await currentPageChips.first().waitFor()
  if ((await currentPageChips.count()) !== 1) {
    throw new Error('Inventory page attachment did not create exactly one page entry')
  }

  const firstVehicleDetailUrl = await page.locator('[data-card-details]').first().getAttribute('href')
  if (!firstVehicleDetailUrl) {
    throw new Error('Dealership vehicle card did not expose its detail route')
  }
  await page.goto(`${origin}${firstVehicleDetailUrl}`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => document.querySelector('[data-vehicle-evidence]')?.getAttribute('aria-busy') === 'false')
  await page.waitForFunction(() => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready')
  if ((await page.getByRole('heading', { level: 1 }).textContent()) !== '2025 Aster E1') {
    throw new Error('Dealership vehicle detail route did not render the live-backed vehicle title')
  }

  const attachCurrentPageButton = page.getByRole('button', { name: 'Attach current page' })
  const attachInsideInputShell = await attachCurrentPageButton.evaluate((element) =>
    Boolean(element.closest('[data-max-mode-companion-input-shell]')),
  )
  if (attachInsideInputShell) {
    throw new Error('Attach-current-page control is still inside the Companion input shell')
  }
  await attachCurrentPageButton.click()
  await currentPageChips.nth(1).waitFor()
  if (!(await currentPageChips.getByText('2025 Aster E1', { exact: false }).count())) {
    throw new Error('Current-page attachment did not expose the page title')
  }
  await page.getByRole('button', { name: 'Refresh current page' }).click()
  if ((await currentPageChips.count()) !== 2) {
    throw new Error('Refreshing the current page created a duplicate attachment')
  }
  await page.locator('section[aria-label="Northfield AI"]').screenshot({
    path: path.join(screenshotDir, 'dealership-vehicle-current-page-attachment.png'),
    animations: 'disabled',
  })

  await page.getByTitle('Open Max Mode').click()
  const maxModeInputShell = page.locator('[data-max-mode-composer-input-shell]')
  await maxModeInputShell.waitFor()
  const maxModeAttachButton = page.getByRole('button', { name: 'Refresh current page' })
  await maxModeAttachButton.waitFor()
  const attachInsideMaxModeInputShell = await maxModeAttachButton.evaluate((element) =>
    Boolean(element.closest('[data-max-mode-composer-input-shell]')),
  )
  if (attachInsideMaxModeInputShell) {
    throw new Error('Attach-current-page control is still inside the Max Mode input shell')
  }
  await page.getByRole('button', { name: 'Close MAX Mode' }).click()
  await page.locator('section[aria-label="Northfield AI"]').waitFor()

  await page.goto(`${origin}/demos/dealership-ai/vehicles/aster-e2-sport`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => document.querySelector('[data-vehicle-evidence]')?.getAttribute('aria-busy') === 'false')
  await page.waitForFunction(() => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready')
  await currentPageChips.nth(1).waitFor()
  await page.getByRole('button', { name: 'Attach current page' }).click()
  await currentPageChips.nth(2).waitFor()
  if (!(await currentPageChips.getByText('2025 Aster E2', { exact: false }).count())) {
    throw new Error('Second vehicle page was not retained as a separate attachment')
  }

  await page.goto(`${origin}/demos/dealership-ai/vehicles/northstar-s4-touring`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => document.querySelector('[data-vehicle-evidence]')?.getAttribute('aria-busy') === 'false')
  await page.waitForFunction(() => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready')
  await currentPageChips.nth(2).waitFor()
  const pageLimitButton = page.getByRole('button', { name: 'Page attachment limit reached' })
  await pageLimitButton.waitFor()
  if (!(await pageLimitButton.isDisabled())) {
    throw new Error('Page attachment limit did not disable collection of a fourth page')
  }
  await page.getByRole('button', { name: /Remove attached page: Northfield Motor House demo/i }).click()
  await currentPageChips.nth(2).waitFor({ state: 'detached' })
  await page.getByRole('button', { name: 'Attach current page' }).click()
  await currentPageChips.nth(2).waitFor()
  if (!(await currentPageChips.getByText('2024 Northstar S4', { exact: false }).count())) {
    throw new Error('A page could not be attached after removing one collection entry')
  }

  await page.setViewportSize({ width: 390, height: 844 })
  const mobilePageToolbar = page.locator('[data-max-mode-current-page-toolbar]')
  const mobileCompanionInputShell = page.locator('[data-max-mode-companion-input-shell]')
  const mobileToolbarMetrics = await mobilePageToolbar.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    return { bottom: rect.bottom, clientWidth: element.clientWidth, scrollWidth: element.scrollWidth }
  })
  const mobileInputTop = await mobileCompanionInputShell.evaluate(
    (element) => element.getBoundingClientRect().top,
  )
  if (mobileToolbarMetrics.scrollWidth - mobileToolbarMetrics.clientWidth > 1) {
    throw new Error('Multi-page attachment toolbar overflows horizontally on mobile')
  }
  if (mobileToolbarMetrics.bottom > mobileInputTop + 1) {
    throw new Error('Multi-page attachment toolbar overlaps the mobile Companion input shell')
  }
  await page.locator('section[aria-label="Northfield AI"]').screenshot({
    path: path.join(screenshotDir, 'dealership-multi-page-attachments-mobile.png'),
    animations: 'disabled',
  })
  await page.setViewportSize({ width: 1440, height: 1000 })

  const pageContextRequestPromise = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Compare the three attached vehicle detail pages.'
  })
  const companionInput = page.getByRole('textbox', { name: 'Ask Northfield AI' })
  await companionInput.fill('Compare the three attached vehicle detail pages.')
  await page.getByTitle('Send message').click()
  const pageContextPayload = (await pageContextRequestPromise).postDataJSON()
  const pageContextAttachments = pageContextPayload.attachments?.filter(
    (attachment) => attachment.source === 'current-page',
  ) || []
  if (pageContextAttachments.length !== 3) {
    throw new Error(`Expected three current-page attachments, found ${pageContextAttachments.length}`)
  }
  if (pageContextAttachments.some((attachment) => Object.hasOwn(attachment, 'vectorSpace'))) {
    throw new Error('Current-page attachment was incorrectly assigned a vector space')
  }
  const pageContextText = pageContextAttachments.map((attachment) => attachment.contentText || '').join('\n')
  if (!pageContextText.includes('2025 Aster E1') ||
      !pageContextText.includes('Adaptive cruise control') ||
      !pageContextText.includes('2025 Aster E2') ||
      !pageContextText.includes('Dual-motor all-wheel drive') ||
      !pageContextText.includes('2024 Northstar S4') ||
      !pageContextText.includes('Blind-spot monitoring')) {
    throw new Error('Multi-page attachments did not preserve each vehicle detail snapshot')
  }
  if (pageContextAttachments.some((attachment) => attachment.metadata?.capturedCharacters > 4000)) {
    throw new Error('A current-page attachment exceeded the configured per-page text limit')
  }
  const totalPageCharacters = pageContextAttachments.reduce(
    (total, attachment) => total + Number(attachment.metadata?.capturedCharacters || 0),
    0,
  )
  if (totalPageCharacters > 10000) {
    throw new Error('Current-page attachments exceeded the configured aggregate text limit')
  }
  if (pageContextAttachments.some((attachment) => attachment.url?.includes('?') || attachment.url?.includes('#'))) {
    throw new Error('Current-page attachment leaked URL query or fragment data')
  }
  if (new Set(pageContextAttachments.map((attachment) => attachment.id)).size !== 3 ||
      new Set(pageContextAttachments.map((attachment) => attachment.url)).size !== 3) {
    throw new Error('Multi-page attachments did not retain distinct page identities')
  }
  if (pageContextPayload.mode !== 'executor' || pageContextPayload.position !== 'landing') {
    throw new Error('Current-page attachment unexpectedly changed chat routing')
  }
  await page.getByText(
    'The selected Aster is electric, has low mileage and is shown with current fictional dealership facts.',
    { exact: true },
  ).first().waitFor()

  await page.getByRole('button', { name: /Remove attached page: 2025 Aster E2/i }).click()
  await currentPageChips.nth(2).waitFor({ state: 'detached' })
  if (!(await currentPageChips.getByText('2025 Aster E1', { exact: false }).count()) ||
      !(await currentPageChips.getByText('2024 Northstar S4', { exact: false }).count())) {
    throw new Error('Removing one page attachment removed another retained page')
  }
  await page.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.vehicle-card')
  await page.waitForFunction(() => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready')

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
    throw new Error('Dealership vehicle question reached chat without its expected vehicle context attachment')
  }
  if (!cardAskPayload.attachments?.some((attachment) => attachment.vectorSpace === 'dealer-vehicle')) {
    throw new Error('Dealership vehicle question used the wrong attachment vector space')
  }
  const expectedHostTools = [
    'Search stock',
    'Electric cars',
    'Family options',
    'Compare cars',
    'Vehicle details',
    'Test drive',
    'Request callback',
    'Showroom location',
  ]
  const renderedHostTools = await page.locator('[data-max-mode-view] [data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if (renderedHostTools.length !== expectedHostTools.length ||
      expectedHostTools.some((label) => !renderedHostTools.includes(label))) {
    throw new Error(`Max Mode did not expose the complete host-owned tool set: ${JSON.stringify(renderedHostTools)}`)
  }

  const inventoryPresentationRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Show current electric vehicles under GBP 40,000.'
  })
  await page.evaluate(() => {
    window.MaxMode.sendMessage('Show current electric vehicles under GBP 40,000.', {
      mode: 'executor',
      position: 'search',
      open: true,
    })
  })
  await inventoryPresentationRequest
  const inventoryPresentation = page.locator('loomai-dealership-inventory').last()
  await inventoryPresentation.waitFor()
  if ((await inventoryPresentation.locator('.vehicle-card').count()) !== 2) {
    throw new Error('The injected inventory presentation did not render its two bounded vehicle records')
  }
  if (!(await inventoryPresentation.getByText('Fuel: Electric', { exact: true }).count()) ||
      !(await inventoryPresentation.getByText('Up to £40,000', { exact: true }).count())) {
    throw new Error('The injected inventory presentation did not render applied filters')
  }
  const detailPresentationRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Tell me about 2025 Aster E1 using its current dealership facts.'
  })
  const askAboutVehicleButton = inventoryPresentation.getByRole('button', { name: 'Ask about this' }).first()
  if (!(await inventoryPresentation.isVisible()) || !(await askAboutVehicleButton.isVisible())) {
    throw new Error('The injected inventory workspace or its primary ask control is not visible in Max Mode')
  }
  await askAboutVehicleButton.click()
  const detailPresentationPayload = (await detailPresentationRequest).postDataJSON()
  const selectedDetailAttachments = detailPresentationPayload.attachments?.filter(
    (attachment) => attachment.source === 'action-result-context',
  ) || []
  if (selectedDetailAttachments.length !== 1 ||
      selectedDetailAttachments[0].vectorSpace !== undefined ||
      selectedDetailAttachments[0].metadata?.actionEligible !== false ||
      selectedDetailAttachments[0].metadata?.trust !== 'REQUIRES_SERVER_RESOLUTION' ||
      selectedDetailAttachments[0].metadata?.sourceActionName !== 'dealership_search_inventory') {
    throw new Error('Selected result context did not preserve its bounded non-authoritative provenance')
  }
  const detailPresentation = page.locator('loomai-dealership-vehicle-detail').last()
  await detailPresentation.waitFor()
  await detailPresentation.getByText('Vehicle details', { exact: true }).waitFor()
  const detailSuitabilityQuery = 'Is 2025 Aster E1 suitable for everyday driving? Explain using current facts and identify unknowns.'
  const detailSuitabilityRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === detailSuitabilityQuery
  })
  await detailPresentation.getByRole('button', { name: 'Everyday suitability' }).click()
  await detailSuitabilityRequest
  await detailPresentation.getByRole('button', { name: 'Keep in context' }).click()
  await detailPresentation.getByRole('button', { name: 'Remove context' }).waitFor()
  await detailPresentation.getByRole('button', { name: 'Remove context' }).click()
  await detailPresentation.getByRole('button', { name: 'Keep in context' }).waitFor()

  const presentationTestDriveRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'I would like to request a test drive for 2025 Aster E1.'
  })
  await inventoryPresentation.getByRole('button', { name: 'Request test drive' }).first().click()
  const presentationTestDrivePayload = (await presentationTestDriveRequest).postDataJSON()
  if (presentationTestDrivePayload.attachments?.filter(
    (attachment) => attachment.source === 'action-result-context',
  ).length !== 1) {
    throw new Error('The injected test-drive control did not preserve exactly one selected result reference')
  }
  const presentationRejectRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'No, cancel'
  })
  await page.getByRole('button', { name: 'Reject', exact: true }).last().click()
  await presentationRejectRequest
  await page.getByText('The request was cancelled.', { exact: true }).last().waitFor()

  const presentationCallbackRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'I would like the dealership to call me about 2025 Aster E1.'
  })
  await inventoryPresentation.getByRole('button', { name: 'Request callback' }).first().click()
  await presentationCallbackRequest

  await inventoryPresentation.getByRole('checkbox').nth(0).click()
  await inventoryPresentation.getByRole('checkbox').nth(1).click()
  const comparisonPresentationRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query?.startsWith('Compare these selected current vehicles using dealership facts:')
  })
  await inventoryPresentation.getByRole('button', { name: 'Compare selected' }).click()
  const comparisonPresentationPayload = (await comparisonPresentationRequest).postDataJSON()
  const selectedComparisonAttachments = comparisonPresentationPayload.attachments?.filter(
    (attachment) => attachment.source === 'action-result-context',
  ) || []
  if (selectedComparisonAttachments.length !== 2 ||
      selectedComparisonAttachments.some((attachment) => attachment.vectorSpace !== undefined || attachment.metadata?.actionEligible !== false)) {
    throw new Error('Comparison did not send exactly two bounded non-action-eligible result references')
  }
  const comparisonPresentation = page.locator('loomai-dealership-vehicle-comparison').last()
  await comparisonPresentation.waitFor()
  await comparisonPresentation.getByText('Vehicle comparison', { exact: true }).waitFor()
  const firstComparisonSelection = comparisonPresentation.getByRole('button', { name: 'Select vehicle' }).first()
  await firstComparisonSelection.click()
  await comparisonPresentation.getByRole('button', { name: 'Selected' }).first().waitFor()
  await comparisonPresentation.getByRole('button', { name: 'Selected' }).first().click()
  await comparisonPresentation.getByRole('button', { name: 'Select vehicle' }).first().waitFor()
  const compareValueRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Compare the value trade-offs between these current vehicles. Ask for my priorities before naming a best option.'
  })
  await comparisonPresentation.getByRole('button', { name: 'Compare value' }).click()
  await compareValueRequest

  const removeAttachment = page.getByRole('button', { name: /Remove attachment|Remove attached page:/ }).first()
  for (let attempt = 0; attempt < 8 && await removeAttachment.isVisible().catch(() => false); attempt += 1) {
    await removeAttachment.click()
  }
  const dismissSuggestions = page.getByRole('button', { name: 'Dismiss suggestions' }).last()
  if (await dismissSuggestions.isVisible().catch(() => false)) {
    await dismissSuggestions.click()
  }
  const comparisonSurface = page.locator('[data-max-mode-action-presentation="loomai.vehicle-comparison.v1"]').last()
  await comparisonSurface.evaluate((element) => element.scrollIntoView({ block: 'start' }))
  await page.screenshot({
    path: path.join(screenshotDir, 'dealership-injected-comparison-desktop.png'),
    animations: 'disabled',
  })
  await page.setViewportSize({ width: 390, height: 844 })
  if (!(await comparisonPresentation.locator('.comparison-mobile').isVisible())) {
    throw new Error('Injected comparison did not switch to the stacked mobile presentation')
  }
  const mobileComparisonBox = await comparisonSurface.boundingBox()
  if (!mobileComparisonBox || mobileComparisonBox.width > 390) {
    throw new Error(`Injected comparison exceeded the mobile viewport: ${JSON.stringify(mobileComparisonBox)}`)
  }
  await comparisonSurface.evaluate((element) => element.scrollIntoView({ block: 'start' }))
  await page.screenshot({
    path: path.join(screenshotDir, 'dealership-injected-comparison-mobile.png'),
    animations: 'disabled',
  })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: 'Close MAX Mode' }).click()
  await page.locator('section[aria-label="Northfield AI"]').waitFor()

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
  if (anonymousRenewalCount < 1) {
    throw new Error('The anonymous browser session did not renew before expiry')
  }

  await context.close()

  const mobileConfirmationContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
    reducedMotion: 'reduce',
  })
  const mobileConfirmationPage = await mobileConfirmationContext.newPage()
  await mobileConfirmationPage.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await mobileConfirmationPage.waitForSelector('.vehicle-card')
  await mobileConfirmationPage.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )
  const mobileVehicleResponse = mobileConfirmationPage.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query?.startsWith('Tell me whether the 2025 Aster E1')
  })
  await mobileConfirmationPage.locator('[data-card-ask]').first().click()
  await mobileVehicleResponse
  const mobileActionResponse = mobileConfirmationPage.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === 'Request a test drive for the Aster E1'
  })
  await mobileConfirmationPage.evaluate(() => {
    window.MaxMode.sendMessage('Request a test drive for the Aster E1', {
      mode: 'executor',
      open: true,
    })
  })
  await mobileActionResponse
  const mobileConfirmButton = mobileConfirmationPage.getByRole('button', { name: 'Confirm', exact: true })
  await mobileConfirmButton.waitFor()
  const mobileConfirmationResponse = mobileConfirmationPage.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === 'Yes, confirm'
  })
  await mobileConfirmButton.click()
  await mobileConfirmationResponse
  await mobileConfirmationPage.getByText('Test-drive request created.', { exact: true }).first().waitFor()
  await mobileConfirmationContext.close()

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
    { name: 'dealership-vehicle-detail', path: '/demos/dealership-ai/vehicles/aster-e1-motion' },
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
