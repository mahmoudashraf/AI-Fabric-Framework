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
const simulatorMediaOrigin = 'https://external-vehicle-provider-simulator.46.224.145.148.sslip.io'
let anonymousRenewalCount = 0
let anonymousBootstrapCount = 0
let anonymousSessionId = 'browser-smoke-session'
let chatQueryCount = 0
let staleConversationAccessRequestCount = 0
let exposeRecentConversationForNavigation = false
let recentConversationListCount = 0
let externalDealershipBundle = null

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

const mockActionVehicles = mockVehicles.map((vehicle, index) => ({
  stockId: vehicle.stockId,
  make: vehicle.make,
  model: vehicle.model,
  derivative: vehicle.derivative,
  year: vehicle.registrationYear,
  priceGbp: vehicle.priceGbp,
  mileage: vehicle.mileage,
  fuelType: vehicle.fuelType,
  transmission: vehicle.transmission,
  bodyType: vehicle.bodyType,
  availability: vehicle.lifecycleState,
  advertStatus: 'LIVE',
  features: vehicle.features,
  imageId: `simulator-vehicle-0${index + 1}`,
  imageUrl: `${simulatorMediaOrigin}/media/w720h540/simulator-vehicle-0${index + 1}.webp`,
}))

const expectedBrowseTools = [
  'Search stock',
  'Electric cars',
  'Family options',
  'Compare cars',
]

const expectedContextualTools = [
  'Live details',
  'Everyday use',
  'Trade-offs',
  'Location',
  'Test drive',
  'Callback',
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

function writeMockHtml(response, status, body) {
  response.writeHead(status, {
    'Cache-Control': 'no-store',
    'Content-Type': 'text/html; charset=utf-8',
  })
  response.end(body)
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

  if (url.pathname === '/external-dealership-host') {
    if (!externalDealershipBundle) {
      writeMockHtml(response, 503, '<!doctype html><title>Bundle unavailable</title>')
      return
    }
    writeMockHtml(response, 200, `<!doctype html>
      <html lang="en">
        <head><meta charset="utf-8"><title>Harbour Motors</title></head>
        <body>
          <main id="dealer-content"><h1>Harbour Motors inventory</h1><p>Independent dealership host.</p></main>
          <script
            src="${origin}/vendor/${externalDealershipBundle.file}"
            data-bootstrap-url="${mockOrigin}/external-dealership-config.json"
            crossorigin="anonymous"
            integrity="sha256-${externalDealershipBundle.integrity}"
          ></script>
        </body>
      </html>`)
    return
  }

  if (url.pathname === '/external-dealership-config.json') {
    writeMockJson(response, 200, {
      backendBaseUrl: mockOrigin,
      widget: { manifestUrl: `${origin}/vendor/max-mode-widget-manifest.json` },
      dealer: {
        id: 'dealer-harbour-smoke',
        assistantLabel: 'Harbour AI',
        sourceMode: 'DEALERSHIP_INVENTORY',
      },
      page: {
        kind: 'inventory',
        rootSelector: '#dealer-content',
        contextLabel: 'Harbour Motors inventory',
      },
      capabilities: {
        comparison: false,
        testDrive: false,
        callback: false,
      },
      presentation: {
        detailBasePath: '/vehicles/',
        imageHostAllowlist: [],
      },
    })
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
      inventoryVectorSpace: 'dealer-vehicle',
      retrievalVectorSpaces: ['dealer-vehicle', 'document'],
    })
    return
  }

  if (url.pathname === '/api/public/chat/session' && request.method === 'POST') {
    anonymousBootstrapCount += 1
    writeMockJson(response, 200, {
      token: 'browser-smoke-token',
      tokenType: 'Bearer',
      authMode: 'PUBLIC_RUNTIME_ANONYMOUS',
      subjectType: 'ANONYMOUS_SESSION',
      sessionId: anonymousSessionId,
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
      sessionId: anonymousSessionId,
      expiresAt: '2099-01-01T00:00:00Z',
    })
    return
  }

  if (url.pathname === '/api/chat/me/auth-context') {
    writeMockJson(response, 200, {
      subjectId: anonymousSessionId,
      subjectType: 'ANONYMOUS_SESSION',
      authMode: 'PUBLIC_RUNTIME_ANONYMOUS',
      callerType: 'PUBLIC_BROWSER',
      sessionId: anonymousSessionId,
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
    chatQueryCount += 1
    const payload = await readMockJson(request)
    if (payload.query === 'Verify stale conversation recovery without replay.') {
      staleConversationAccessRequestCount += 1
      writeMockJson(response, 200, {
        success: false,
        type: 'ERROR',
        conversationId: payload.conversationId,
        answer: 'Access denied to conversation',
        safeSummary: 'Access denied to conversation',
        fallbackReason: 'ACCESS_DENIED',
        actions: [],
        sources: [],
      })
      return
    }
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
              _items: mockActionVehicles.slice(0, 2),
              _count: 2,
              results: mockActionVehicles.slice(0, 2),
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
    if (payload.query === 'Load the current live stock record for 2025 Aster E1, then summarize its dealership facts.') {
      writeMockJson(response, 200, {
        success: true,
        type: 'INFORMATION_PROVIDED',
        conversationId: 'conversation-browser-smoke',
        answer: 'Current details loaded for the 2025 Aster E1.',
        actions: [{
          action: 'dealership_get_vehicle',
          actionResult: {
            success: true,
            data: {
              vehicleRecord: mockActionVehicles[0],
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
    if (payload.query === 'Show a generic action result.') {
      writeMockJson(response, 200, {
        success: true,
        type: 'ACTION_EXECUTED',
        conversationId: 'conversation-browser-smoke',
        answer: 'Generic operation completed.',
        actions: [{
          action: 'demo_generic_operation',
          actionResult: {
            success: true,
            data: {
              data: {
                referenceCode: 'GENERIC-001',
                status: 'READY',
                summary: 'The generic result remains readable without a host-specific renderer.',
                details: {
                  owner: 'Demo team',
                  nextStep: 'Review the structured result.',
                },
              },
              message: 'The generic operation completed successfully.',
              success: true,
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
          actionResult: {
            success: true,
            data: {
              data: {
                receiptCode: 'NFM-DEMO-RECEIPT-001',
                actionType: 'dealership_request_test_drive',
                status: 'NEW',
                createdAt: '2026-10-05T10:35:14.429Z',
                vehicle: '2025 Aster E1',
                message: 'Your request is in the dealership review inbox. A team member will use the contact details you confirmed.',
              },
              message: 'Your request is in the dealership review inbox. A team member will use the contact details you confirmed.',
              success: true,
            },
          },
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
    recentConversationListCount += 1
    writeMockJson(response, 200, exposeRecentConversationForNavigation ? [{
      id: 'conversation-browser-smoke',
      title: 'Recent conversation',
      status: 'ACTIVE',
      createdAt: '2026-10-03T12:00:00Z',
      lastInteractionAt: '2026-10-03T12:01:00Z',
      turnsCount: 1,
    }] : [])
    return
  }

  if (url.pathname === '/api/chat/me/conversations/conversation-browser-smoke' && request.method === 'GET') {
    writeMockJson(response, 200, {
      id: 'conversation-browser-smoke',
      title: 'Recent conversation',
      status: 'ACTIVE',
      createdAt: '2026-10-03T12:00:00Z',
      lastInteractionAt: '2026-10-03T12:01:00Z',
      turnsCount: 1,
      turns: [{
        timestamp: '2026-10-03T12:01:00Z',
        userQuery: 'Show current electric vehicles under GBP 40,000.',
        aiResponse: 'I found current electric vehicles under GBP 40,000.',
      }],
    })
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
    PUBLIC_IMAGE_ORIGINS: simulatorMediaOrigin,
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
    if (server.exitCode !== null) {
      throw new Error(`Static server exited before becoming ready.\n${serverOutput}`)
    }
    try {
      const response = await fetch(`${origin}/health`)
      if (response.ok && serverOutput.includes(`public site listening on port ${port}`)) return
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

  const dealershipManifestResponse = await fetch(`${origin}/vendor/dealership-experience-manifest.json`)
  if (dealershipManifestResponse.headers.get('cache-control') !== 'no-store') {
    throw new Error(`Dealership experience manifest is cacheable: ${dealershipManifestResponse.headers.get('cache-control')}`)
  }
  if (dealershipManifestResponse.headers.get('access-control-allow-origin') !== '*' ||
      dealershipManifestResponse.headers.get('cross-origin-resource-policy') !== 'cross-origin') {
    throw new Error('Dealership experience manifest is not available to reviewed external hosts')
  }
  const dealershipManifest = await dealershipManifestResponse.json()
  if (dealershipManifest.schemaVersion !== 'loomai-dealership-experience-bundle-v1' ||
      !/^dealership-experience\.[a-f0-9]{16}\.iife\.js$/.test(dealershipManifest.file || '')) {
    throw new Error('Dealership experience manifest did not expose a valid content-hashed bundle')
  }
  const dealershipBundleResponse = await fetch(`${origin}/vendor/${dealershipManifest.file}`)
  if (dealershipBundleResponse.headers.get('cache-control') !== 'public, max-age=31536000, immutable' ||
      dealershipBundleResponse.headers.get('access-control-allow-origin') !== '*' ||
      dealershipBundleResponse.headers.get('cross-origin-resource-policy') !== 'cross-origin') {
    throw new Error('Content-hashed dealership experience is not immutable and cross-origin installable')
  }
  const dealershipBundleBytes = Buffer.from(await dealershipBundleResponse.arrayBuffer())
  const dealershipBundleSha256 = createHash('sha256').update(dealershipBundleBytes).digest('hex')
  if (dealershipBundleSha256 !== dealershipManifest.sha256) {
    throw new Error('Dealership experience manifest did not match the served bundle SHA-256')
  }
  externalDealershipBundle = {
    file: dealershipManifest.file,
    integrity: Buffer.from(dealershipBundleSha256, 'hex').toString('base64'),
  }

  const externalInstallContext = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    reducedMotion: 'reduce',
  })
  const externalInstallPage = await externalInstallContext.newPage()
  const externalHostResponse = await externalInstallPage.goto(
    `${mockOrigin}/external-dealership-host`,
    { waitUntil: 'networkidle' },
  )
  if (!externalHostResponse?.ok()) {
    throw new Error(`External dealership host returned ${externalHostResponse?.status()}`)
  }
  await externalInstallPage.waitForFunction(() => Boolean(
    window.LoomAIDealershipExperience?.mount && window.MaxMode?.open,
  ))
  await externalInstallPage.evaluate(() => window.MaxMode.open())
  const externalMaxMode = externalInstallPage.locator('[data-max-mode-view]')
  await externalMaxMode.waitFor()
  await externalMaxMode.getByText('Harbour AI', { exact: true }).first().waitFor()
  const externalBrowseTools = await externalMaxMode.locator('[data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if (JSON.stringify(externalBrowseTools) !== JSON.stringify([
    'Search stock',
    'Electric cars',
    'Family options',
  ])) {
    throw new Error(`External dealer did not receive its bounded capability set: ${JSON.stringify(externalBrowseTools)}`)
  }
  if (!(await externalMaxMode.locator('[data-max-mode-tool-scope="contextual"]').isDisabled())) {
    throw new Error('External inventory host unexpectedly enabled contextual tools without context')
  }
  const externalPackScript = externalInstallPage.locator('script[data-bootstrap-url]')
  if (new URL(await externalPackScript.getAttribute('src')).origin !== origin ||
      await externalPackScript.getAttribute('integrity') !== `sha256-${externalDealershipBundle.integrity}`) {
    throw new Error('External dealer did not install the reviewed content-hashed experience pack')
  }
  await externalInstallContext.close()

  const routes = [
    '/',
    '/products',
    '/products/loomai-platform',
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
  const mediaNetworkEvents = []
  page.on('response', (response) => {
    if (!response.url().startsWith(`${simulatorMediaOrigin}/media/`)) return
    mediaNetworkEvents.push({
      type: 'response',
      url: response.url(),
      status: response.status(),
      contentType: response.headers()['content-type'] || '',
    })
  })
  page.on('requestfailed', (request) => {
    if (!request.url().startsWith(`${simulatorMediaOrigin}/media/`)) return
    mediaNetworkEvents.push({
      type: 'requestfailed',
      url: request.url(),
      error: request.failure()?.errorText || 'unknown',
    })
  })
  await page.route(`${simulatorMediaOrigin}/media/**`, async (route) => {
    const match = route.request().url().match(/simulator-vehicle-(0[1-5])\.webp$/)
    const image = match?.[1] || '01'
    await route.fulfill({
      path: path.join(root, 'public', 'assets', 'demos', 'dealership', `vehicle-${image}.webp`),
      contentType: 'image/webp',
      headers: { 'Cache-Control': 'public, max-age=31536000, immutable' },
    })
  })

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

  await page.goto(`${origin}/`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: 'LoomAI Platform' }).waitFor()
  if ((await page.locator('.platform-system').count()) !== 1) {
    throw new Error('Homepage does not expose the animated LoomAI Platform system visual')
  }
  await page.getByRole('link', { name: 'Dealership live demo' }).waitFor()

  await page.goto(`${origin}/products/loomai-platform`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { level: 1, name: 'LoomAI Platform' }).waitFor()
  await page.getByRole('heading', { name: 'See a customer application use its own LoomAI deployment.' }).waitFor()
  await page.getByRole('heading', { name: 'Dealership AI Experience' }).waitFor()
  const platformLifecycle = await page.locator('.platform-system__lifecycle li').allTextContents()
  if (JSON.stringify(platformLifecycle) !== JSON.stringify(['Validate', 'Publish', 'Apply', 'Verify'])) {
    throw new Error(`Platform visual exposes the wrong lifecycle: ${JSON.stringify(platformLifecycle)}`)
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
  const loadedDealershipScript = page.locator('script[data-dealership-experience-bundle]')
  const loadedDealershipSource = await loadedDealershipScript.getAttribute('src')
  const loadedDealershipSha256 = await loadedDealershipScript.getAttribute('data-dealership-experience-bundle-sha256')
  const loadedDealershipIntegrity = await loadedDealershipScript.getAttribute('integrity')
  if (loadedDealershipSource !== `/vendor/${dealershipManifest.file}` ||
      loadedDealershipSha256 !== dealershipManifest.sha256 ||
      loadedDealershipIntegrity !== `sha256-${Buffer.from(dealershipBundleSha256, 'hex').toString('base64')}`) {
    throw new Error('Dealership demo did not load the packaged experience manifest version')
  }
  const packApiReady = await page.evaluate(() => Boolean(window.LoomAIDealershipExperience?.mount))
  if (!packApiReady) {
    throw new Error('Dealership demo did not initialize through the packaged browser API')
  }
  const loadedWidgetScript = page.locator('script[data-max-mode-bundle]')
  const loadedWidgetSource = await loadedWidgetScript.getAttribute('src')
  const loadedWidgetSha256 = await loadedWidgetScript.getAttribute('data-max-mode-bundle-sha256')
  if (new URL(loadedWidgetSource, origin).pathname !== `/vendor/${widgetManifest.file}` || loadedWidgetSha256 !== widgetManifest.sha256) {
    throw new Error('Dealership loaded a stable or mismatched widget bundle instead of the manifest version')
  }
  await page.evaluate(() => window.MaxMode.open())
  const listingMaxMode = page.locator('[data-max-mode-view]')
  await listingMaxMode.waitFor()
  const initialBrowseScope = listingMaxMode.locator('[data-max-mode-tool-scope="default"]')
  const initialContextualScope = listingMaxMode.locator('[data-max-mode-tool-scope="contextual"]')
  await initialBrowseScope.waitFor()
  if ((await listingMaxMode.locator('[data-max-mode-tool-scope]').count()) !== 2 ||
      (await initialBrowseScope.getAttribute('aria-selected')) !== 'true' ||
      !(await initialContextualScope.isDisabled())) {
    throw new Error('Listing tool groups did not start in Browse stock with unavailable contextual tools')
  }
  const initialBrowseTools = await listingMaxMode.locator('[data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if (JSON.stringify(initialBrowseTools) !== JSON.stringify(expectedBrowseTools)) {
    throw new Error(`Listing did not expose the expected Browse stock tools: ${JSON.stringify(initialBrowseTools)}`)
  }
  await page.getByRole('button', { name: 'Close MAX Mode' }).click()
  const listingCompanion = page.locator('section[aria-label="Northfield AI"]')
  await listingCompanion.waitFor()
  await listingCompanion.getByRole('textbox', { name: 'Ask Northfield AI' }).focus()
  if ((await listingCompanion.locator('[data-max-mode-tool-scope]').count()) !== 0 ||
      (await listingCompanion.locator('[data-max-mode-quick-action]').count()) !== 0) {
    throw new Error('Companion dock exposed tools reserved for Max Mode')
  }
  await listingCompanion.getByRole('button', { name: 'Minimize assistant' }).click()
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
  if ((await listingCompanion.getByRole('button', { name: 'Minimize assistant' }).count()) !== 0) {
    throw new Error('Attaching the inventory page unexpectedly opened the Companion chat')
  }
  const navigationSessionBefore = await page.evaluate(() => {
    const binding = JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}')
    return binding.sessionId
  })
  const bootstrapsBeforeNavigation = anonymousBootstrapCount

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
  await page.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.attachedItems?.some((item) => item.type === 'current-page') === true
  })
  const navigationSessionAfter = await page.evaluate(() => {
    const binding = JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}')
    return binding.sessionId
  })
  if (navigationSessionAfter !== navigationSessionBefore) {
    throw new Error('Full-page navigation changed the runtime-owned anonymous session')
  }
  if (anonymousBootstrapCount !== bootstrapsBeforeNavigation) {
    throw new Error('Full-page navigation bootstrapped a new anonymous runtime identity')
  }

  const attachCurrentPageButton = page.getByRole('button', { name: 'Attach current page' })
  const attachInsideInputShell = await attachCurrentPageButton.evaluate((element) =>
    Boolean(element.closest('[data-max-mode-companion-input-shell]')),
  )
  if (attachInsideInputShell) {
    throw new Error('Attach-current-page control is still inside the Companion input shell')
  }
  await currentPageChips.first().waitFor()
  if ((await currentPageChips.count()) !== 1 ||
      !(await currentPageChips.getByText('Northfield Motor House demo', { exact: false }).count())) {
    throw new Error('Navigation-persistent page attachment disappeared during full-page navigation')
  }
  await attachCurrentPageButton.click()
  await currentPageChips.nth(1).waitFor()
  if (!(await currentPageChips.getByText('2025 Aster E1', { exact: false }).count())) {
    throw new Error('Current-page attachment did not expose the page title')
  }
  if ((await page.locator('section[aria-label="Northfield AI"]').getByRole('button', { name: 'Minimize assistant' }).count()) !== 0) {
    throw new Error('Attaching a vehicle page unexpectedly opened the Companion chat')
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
  if ((await page.locator('[data-max-mode-view] [data-max-mode-current-page-action]').count()) !== 0) {
    throw new Error('Attach-current-page control is visible in Max Mode')
  }
  if ((await page.locator('[data-max-mode-view] [data-max-mode-current-page-chip]').count()) !== 2) {
    throw new Error('Max Mode did not retain the attached page context after hiding its attach control')
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
    throw new Error(`Full-page navigation or current-page attachment changed the preserved chat routing: ${JSON.stringify({ mode: pageContextPayload.mode, position: pageContextPayload.position })}`)
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
  const maxModeView = page.locator('[data-max-mode-view]')
  const browseScope = maxModeView.locator('[data-max-mode-tool-scope="default"]')
  const contextualScope = maxModeView.locator('[data-max-mode-tool-scope="contextual"]')
  await contextualScope.waitFor()
  if ((await contextualScope.getAttribute('aria-selected')) !== 'true' || await contextualScope.isDisabled()) {
    throw new Error('Attaching a vehicle did not automatically open its contextual tool group')
  }
  const contextLabel = await maxModeView.locator('[data-max-mode-active-context-label]').getAttribute(
    'data-max-mode-active-context-label',
  )
  if (!contextLabel?.includes('2025 Aster E1')) {
    throw new Error(`Attached vehicle context label was not clear: ${contextLabel}`)
  }
  const renderedHostTools = await page.locator('[data-max-mode-view] [data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if (JSON.stringify(renderedHostTools) !== JSON.stringify(expectedContextualTools)) {
    throw new Error(`Max Mode did not expose the host-owned contextual tools: ${JSON.stringify(renderedHostTools)}`)
  }
  await browseScope.click()
  const browseToolsWithContext = await maxModeView.locator('[data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  const retainedVehicleAttachment = await page.evaluate(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.attachedItems?.some((item) => item.data?.id === 'veh-aster-e1') === true
  })
  if (JSON.stringify(browseToolsWithContext) !== JSON.stringify(expectedBrowseTools) || !retainedVehicleAttachment) {
    throw new Error('Switching back to Browse stock removed or obscured the attached vehicle context')
  }
  await contextualScope.click()

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
  const inventoryImages = inventoryPresentation.locator('.vehicle-image')
  await inventoryImages.nth(1).waitFor()
  if ((await inventoryImages.count()) !== 2) {
    throw new Error('The injected inventory presentation did not render provider media for every vehicle')
  }
  for (let index = 0; index < await inventoryImages.count(); index += 1) {
    const image = inventoryImages.nth(index)
    await image.scrollIntoViewIfNeeded()
    const rendered = await image.evaluate(async (element) => {
      try {
        await element.decode()
        return {
          src: element.src,
          width: element.naturalWidth,
          height: element.naturalHeight,
          error: '',
        }
      } catch (error) {
        return {
          src: element.src,
          width: element.naturalWidth,
          height: element.naturalHeight,
          error: String(error),
        }
      }
    })
    if (rendered.error) {
      throw new Error(
        `Provider media could not be decoded: ${JSON.stringify({ rendered, mediaNetworkEvents })}`,
      )
    }
    if (!rendered.src.startsWith(`${simulatorMediaOrigin}/media/`) || rendered.width < 1 || rendered.height < 1) {
      throw new Error(`Provider media did not render in the inventory presentation: ${JSON.stringify(rendered)}`)
    }
  }
  if (!(await inventoryPresentation.getByText('Fuel: Electric', { exact: true }).count()) ||
      !(await inventoryPresentation.getByText('Up to £40,000', { exact: true }).count())) {
    throw new Error('The injected inventory presentation did not render applied filters')
  }
  const detailPresentationRequest = page.waitForRequest((request) => {
    if (!request.url().endsWith('/api/chat/me/query')) return false
    return request.postDataJSON()?.query === 'Load the current live stock record for 2025 Aster E1, then summarize its dealership facts.'
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
  const detailImage = detailPresentation.locator('.vehicle-image')
  await detailImage.scrollIntoViewIfNeeded()
  const detailImageDecodeError = await detailImage.evaluate(async (element) => {
    try {
      await element.decode()
      return ''
    } catch (error) {
      return String(error)
    }
  })
  if (detailImageDecodeError) {
    throw new Error(`Provider detail media could not be decoded: ${detailImageDecodeError}`)
  }
  if (!(await detailImage.getAttribute('src'))?.startsWith(`${simulatorMediaOrigin}/media/`) ||
      await detailImage.evaluate((element) => element.naturalWidth) < 1) {
    throw new Error('The injected vehicle detail presentation did not render provider media')
  }
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
  await page.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return !state.attachedItems?.some((item) => item.type !== 'ai-search')
  })
  if ((await browseScope.getAttribute('aria-selected')) !== 'true' ||
      !(await contextualScope.isDisabled())) {
    throw new Error('Removing the final listing attachment did not return to Browse stock and disable contextual tools')
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
  const desktopRequestReceipt = page.locator(
    '[data-max-mode-action-presentation="loomai.dealership-request-receipt.v1"]',
  ).last()
  await desktopRequestReceipt.waitFor()
  const desktopRequestReceiptContent = desktopRequestReceipt
    .locator('loomai-dealership-request-receipt')
    .locator('.workspace')
  await desktopRequestReceiptContent.waitFor()
  const desktopRequestReceiptText = await desktopRequestReceiptContent.textContent()
  if (!desktopRequestReceiptText?.includes('Request received') ||
      !desktopRequestReceiptText.includes('NFM-DEMO-RECEIPT-001') ||
      !desktopRequestReceiptText.includes('2025 Aster E1') ||
      await desktopRequestReceipt.locator('[data-max-mode-generic-action-result]').count() !== 0) {
    throw new Error(`Confirmed test-drive result did not use the rich receipt presentation: ${desktopRequestReceiptText}`)
  }
  if (anonymousRenewalCount < 1) {
    throw new Error('The anonymous browser session did not renew before expiry')
  }

  const staleConversationResponse = page.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === 'Verify stale conversation recovery without replay.'
  })
  await page.evaluate(() => {
    window.MaxMode.sendMessage('Verify stale conversation recovery without replay.', {
      mode: 'executor',
      position: 'search',
      open: true,
    })
  })
  await staleConversationResponse
  await page.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.conversationId === null && Array.isArray(state.chatMessages) && state.chatMessages.length === 0
  })
  await page.waitForTimeout(150)
  if (staleConversationAccessRequestCount !== 1) {
    throw new Error(`Stale conversation request was unexpectedly replayed ${staleConversationAccessRequestCount} times`)
  }
  if (await page.getByText('Access denied to conversation', { exact: true }).count()) {
    throw new Error('Raw conversation access denial was rendered as an assistant answer')
  }

  const recoveredRequestPromise = page.waitForRequest((request) => {
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
  const recoveredRequest = await recoveredRequestPromise
  if (recoveredRequest.postDataJSON()?.conversationId) {
    throw new Error('The first request after stale-conversation recovery reused the denied conversation')
  }
  await page.getByText('I found current electric vehicles under GBP 40,000.', { exact: true }).first().waitFor()
  await page.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.conversationId === 'conversation-browser-smoke'
  })

  const requestsBeforeIdentityRotation = chatQueryCount
  anonymousSessionId = 'browser-smoke-session-rotated'
  await page.evaluate(() => {
    sessionStorage.removeItem('maxmode_public_runtime_session_credential_v2')
  })
  await page.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await page.waitForFunction(
    () => ['ready', 'unavailable'].includes(
      document.querySelector('[data-runtime-state]')?.getAttribute('data-state') || '',
    ),
  )
  const rotatedRuntimeState = await page.locator('[data-runtime-state]').getAttribute('data-state')
  if (rotatedRuntimeState !== 'ready') {
    const detail = await page.locator('[data-runtime-state-detail]').textContent()
    throw new Error(`Dealership assistant did not remain ready after identity rotation: ${detail}`)
  }
  await page.evaluate(() => window.MaxMode.open({ position: 'search', mode: 'executor' }))
  try {
    await page.waitForFunction(() => {
      const binding = JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}')
      const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
      return binding.sessionId === 'browser-smoke-session-rotated'
        && state.conversationId === null
        && Array.isArray(state.chatMessages)
        && state.chatMessages.every((message) => message.id === 'welcome')
        && Array.isArray(state.attachedItems)
        && state.attachedItems.length === 0
    }, undefined, { timeout: 5_000 })
  } catch {
    const storageState = await page.evaluate(() => ({
      binding: JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}'),
      widget: JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}'),
    }))
    throw new Error(`Anonymous identity rotation did not reset all conversation-bound state: ${JSON.stringify(storageState)}`)
  }
  await page.waitForTimeout(150)
  if (chatQueryCount !== requestsBeforeIdentityRotation) {
    throw new Error('Anonymous identity rotation replayed a persisted chat request')
  }

  const rotatedIdentityRequestPromise = page.waitForRequest((request) => {
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
  const rotatedIdentityRequest = await rotatedIdentityRequestPromise
  if (rotatedIdentityRequest.postDataJSON()?.conversationId) {
    throw new Error('Anonymous identity rotation retained the previous identity conversation')
  }
  await page.getByText('I found current electric vehicles under GBP 40,000.', { exact: true }).first().waitFor()

  await context.close()

  const navigationContinuityContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: 'reduce',
  })
  const navigationContinuityPage = await navigationContinuityContext.newPage()
  await navigationContinuityPage.goto(`${origin}/demos/dealership-ai`, { waitUntil: 'networkidle' })
  await navigationContinuityPage.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )
  await navigationContinuityPage.getByRole('button', { name: 'Attach current page' }).click()
  await navigationContinuityPage.evaluate(() => {
    window.MaxMode.attachItem({
      type: 'action-result-context',
      data: {
        id: 'navigation-reference',
        title: 'Saved comparison context',
        content: 'A bounded non-page result reference.',
      },
    })
  })
  await navigationContinuityPage.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.attachedItems?.some((item) => item.type === 'current-page') === true
      && state.attachedItems?.some((item) => item.data?.id === 'navigation-reference') === true
  })
  const navigationContinuityResponse = navigationContinuityPage.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === 'Show current electric vehicles under GBP 40,000.'
  })
  await navigationContinuityPage.evaluate(() => {
    window.MaxMode.sendMessage('Show current electric vehicles under GBP 40,000.', {
      mode: 'executor',
      position: 'search',
      open: true,
    })
  })
  await navigationContinuityResponse
  await navigationContinuityPage.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.conversationId === 'conversation-browser-smoke'
      && state.chatMessages?.some((message) => message.content === 'I found current electric vehicles under GBP 40,000.') === true
  })
  const navigationContinuityBefore = await navigationContinuityPage.evaluate(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return {
      conversationId: state.conversationId,
      messages: state.chatMessages?.map(({ id, type, content }) => ({ id, type, content })),
      attachments: state.attachedItems?.map((item) => ({ type: item.type, id: item.data?.id, title: item.data?.title })),
      currentMode: state.currentMode,
      currentPosition: state.currentPosition,
      sessionId: JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}').sessionId,
      detailUrl: document.querySelector('[data-card-details]')?.getAttribute('href'),
    }
  })
  if (!navigationContinuityBefore.sessionId || !navigationContinuityBefore.detailUrl) {
    throw new Error('Navigation continuity setup did not expose a runtime session and vehicle detail route')
  }
  const continuityBootstrapsBeforeNavigation = anonymousBootstrapCount
  const recentConversationListsBeforeNavigation = recentConversationListCount
  exposeRecentConversationForNavigation = true
  await navigationContinuityPage.goto(`${origin}${navigationContinuityBefore.detailUrl}`, { waitUntil: 'networkidle' })
  await navigationContinuityPage.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )
  try {
    await navigationContinuityPage.waitForFunction(() => {
      const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
      return state.conversationId === 'conversation-browser-smoke'
        && state.chatMessages?.some((message) => message.content === 'I found current electric vehicles under GBP 40,000.') === true
        && state.attachedItems?.some((item) => item.type === 'current-page') === true
        && state.attachedItems?.some((item) => item.data?.id === 'navigation-reference') === true
    })
  } catch {
    const state = await navigationContinuityPage.evaluate(() => {
      const widget = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
      const binding = JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}')
      return {
        conversationId: widget.conversationId,
        messages: widget.chatMessages?.map((message) => ({ type: message.type, content: message.content })),
        attachments: widget.attachedItems?.map((item) => ({ type: item.type, id: item.data?.id })),
        sessionId: binding.sessionId,
      }
    })
    throw new Error(`Full-page navigation did not preserve conversation state: ${JSON.stringify(state)}`)
  }
  const navigationContinuitySessionAfter = await navigationContinuityPage.evaluate(
    () => JSON.parse(sessionStorage.getItem('maxmode_public_runtime_session_binding_v1') || '{}').sessionId,
  )
  const navigationContinuityAfter = await navigationContinuityPage.evaluate(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return {
      conversationId: state.conversationId,
      messages: state.chatMessages?.map(({ id, type, content }) => ({ id, type, content })),
      attachments: state.attachedItems?.map((item) => ({ type: item.type, id: item.data?.id, title: item.data?.title })),
      currentMode: state.currentMode,
      currentPosition: state.currentPosition,
    }
  })
  if (navigationContinuitySessionAfter !== navigationContinuityBefore.sessionId) {
    throw new Error('Full-page navigation changed the runtime-owned anonymous session')
  }
  for (const key of ['conversationId', 'currentMode', 'currentPosition']) {
    if (navigationContinuityAfter[key] !== navigationContinuityBefore[key]) {
      throw new Error(`Full-page navigation changed persisted ${key}`)
    }
  }
  if (JSON.stringify(navigationContinuityAfter.messages) !== JSON.stringify(navigationContinuityBefore.messages)) {
    throw new Error('Full-page navigation changed the persisted message sequence')
  }
  if (JSON.stringify(navigationContinuityAfter.attachments) !== JSON.stringify(navigationContinuityBefore.attachments)) {
    throw new Error('Full-page navigation changed the persisted attachment sequence')
  }
  if (anonymousBootstrapCount !== continuityBootstrapsBeforeNavigation) {
    throw new Error('Full-page navigation bootstrapped a new anonymous runtime identity')
  }
  if (recentConversationListCount !== recentConversationListsBeforeNavigation) {
    throw new Error('Full-page navigation replaced local state through an unnecessary recent-conversation load')
  }
  exposeRecentConversationForNavigation = false
  await navigationContinuityContext.close()

  const detailToolsContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: 'reduce',
  })
  const detailToolsPage = await detailToolsContext.newPage()
  await detailToolsPage.goto(`${origin}/demos/dealership-ai/vehicles/aster-e1-motion`, { waitUntil: 'networkidle' })
  await detailToolsPage.waitForFunction(
    () => document.querySelector('[data-runtime-state]')?.getAttribute('data-state') === 'ready',
  )
  await detailToolsPage.evaluate(() => window.MaxMode.open())
  const detailMaxMode = detailToolsPage.locator('[data-max-mode-view]')
  const detailBrowseScope = detailMaxMode.locator('[data-max-mode-tool-scope="default"]')
  const detailContextualScope = detailMaxMode.locator('[data-max-mode-tool-scope="contextual"]')
  await detailContextualScope.waitFor()
  if (await detailContextualScope.isDisabled() ||
      (await detailContextualScope.getAttribute('aria-selected')) !== 'true') {
    throw new Error('Vehicle detail page did not expose and initially select its page-provided contextual tools')
  }
  const detailContextLabel = await detailMaxMode.locator('[data-max-mode-active-context-label]').getAttribute(
    'data-max-mode-active-context-label',
  )
  if (detailContextLabel !== '2025 Aster E1') {
    throw new Error(`Vehicle detail page exposed the wrong active context label: ${detailContextLabel}`)
  }
  const detailContextualTools = await detailMaxMode.locator('[data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if (JSON.stringify(detailContextualTools) !== JSON.stringify(expectedContextualTools)) {
    throw new Error(`Vehicle detail page exposed the wrong contextual tools: ${JSON.stringify(detailContextualTools)}`)
  }
  await detailBrowseScope.click()
  const detailBrowseTools = await detailMaxMode.locator('[data-max-mode-quick-action]').evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if (JSON.stringify(detailBrowseTools) !== JSON.stringify(expectedBrowseTools) ||
      await detailContextualScope.isDisabled()) {
    throw new Error('Vehicle detail page hid Current context or failed to expose Browse stock')
  }
  await detailToolsPage.getByRole('button', { name: 'Close MAX Mode' }).click()
  const detailCompanion = detailToolsPage.locator('section[aria-label="Northfield AI"]')
  await detailCompanion.getByRole('button', { name: 'Attach current page' }).click()
  await detailToolsPage.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    return state.attachedItems?.some((item) => item.type === 'current-page') === true
  })
  if ((await detailCompanion.getByRole('button', { name: 'Minimize assistant' }).count()) !== 0) {
    throw new Error('Attaching detail context unexpectedly opened the Companion chat')
  }
  await detailToolsPage.getByTitle('Open Max Mode').click()
  await detailMaxMode.waitFor()
  await detailToolsPage.waitForFunction(() => {
    const selected = document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot
      ?.querySelector('[data-max-mode-tool-scope="contextual"]')
    return selected?.getAttribute('aria-selected') === 'true'
  })
  const attachedDetailLabel = await detailMaxMode.locator('[data-max-mode-active-context-label]').getAttribute(
    'data-max-mode-active-context-label',
  )
  if (!attachedDetailLabel?.includes('2025 Aster E1')) {
    throw new Error(`Detail attachment did not retain a clear context label: ${attachedDetailLabel}`)
  }
  await detailToolsPage.getByRole('button', { name: /Remove attached page:/ }).first().click()
  await detailToolsPage.waitForFunction(() => {
    const state = JSON.parse(sessionStorage.getItem('maxmode_widget_state') || '{}')
    const selected = document.querySelector('#max-mode-widget-shadow-host')?.shadowRoot
      ?.querySelector('[data-max-mode-tool-scope="default"]')
    return state.attachedItems?.length === 0 && selected?.getAttribute('aria-selected') === 'true'
  })
  if (await detailContextualScope.isDisabled()) {
    throw new Error('Removing a detail-page attachment incorrectly disabled page-provided contextual tools')
  }
  await detailMaxMode.screenshot({
    path: path.join(screenshotDir, 'dealership-context-tool-groups-desktop.png'),
    animations: 'disabled',
  })
  await detailToolsContext.close()

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
  const mobileToolRail = mobileConfirmationPage.locator('[data-max-mode-tool-rail]')
  await mobileToolRail.waitFor()
  await mobileToolRail.locator('[data-max-mode-tool-rail-item="sources"]').waitFor()
  const mobileToolRailItems = await mobileToolRail.locator('[data-max-mode-tool-rail-item]').evaluateAll(
    (elements) => elements.map((element) => ({
      id: element.getAttribute('data-max-mode-tool-rail-item'),
      label: element.getAttribute('aria-label'),
    })),
  )
  if (JSON.stringify(mobileToolRailItems.map((item) => item.id)) !== JSON.stringify([
    'browse-stock',
    'current-vehicle',
    'sources',
  ])) {
    throw new Error(`Mobile Max Mode exposed the wrong dealership tool rail: ${JSON.stringify(mobileToolRailItems)}`)
  }
  if (mobileToolRailItems.some((item) => /cart|product/i.test(`${item.id} ${item.label}`))) {
    throw new Error(`Generic mobile tool rail leaked a commerce control: ${JSON.stringify(mobileToolRailItems)}`)
  }
  await mobileToolRail.locator('[data-max-mode-tool-rail-item="browse-stock"]').click()
  const mobileBrowseScope = mobileConfirmationPage.locator(
    '[data-max-mode-tool-scope="default"]:visible',
  )
  await mobileBrowseScope.waitFor()
  const mobileBrowseTools = await mobileConfirmationPage.locator(
    '[data-max-mode-quick-action]:visible',
  ).evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if ((await mobileBrowseScope.getAttribute('aria-selected')) !== 'true' ||
      JSON.stringify(mobileBrowseTools) !== JSON.stringify(expectedBrowseTools)) {
    throw new Error(`Mobile Stock rail command did not open Browse stock tools: ${JSON.stringify(mobileBrowseTools)}`)
  }
  await mobileConfirmationPage.getByRole('button', { name: 'Close tools' }).click()
  await mobileToolRail.locator('[data-max-mode-tool-rail-item="current-vehicle"]').click()
  const mobileContextualScope = mobileConfirmationPage.locator(
    '[data-max-mode-tool-scope="contextual"]:visible',
  )
  await mobileContextualScope.waitFor()
  const mobileContextualTools = await mobileConfirmationPage.locator(
    '[data-max-mode-quick-action]:visible',
  ).evaluateAll(
    (elements) => elements.map((element) => element.getAttribute('data-max-mode-quick-action')),
  )
  if ((await mobileContextualScope.getAttribute('aria-selected')) !== 'true' ||
      JSON.stringify(mobileContextualTools) !== JSON.stringify(expectedContextualTools)) {
    throw new Error(`Mobile Max Mode did not expose the selected contextual tool group: ${JSON.stringify(mobileContextualTools)}`)
  }
  await mobileConfirmationPage.getByRole('button', { name: 'Close tools' }).click()
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
  const mobileRequestReceipt = mobileConfirmationPage.locator(
    '[data-max-mode-action-presentation="loomai.dealership-request-receipt.v1"]',
  ).last()
  await mobileRequestReceipt.waitFor()
  const mobileRequestReceiptBox = await mobileRequestReceipt.boundingBox()
  const mobileRequestReceiptContent = mobileRequestReceipt
    .locator('loomai-dealership-request-receipt')
    .locator('.workspace')
  await mobileRequestReceiptContent.waitFor()
  const mobileRequestReceiptText = await mobileRequestReceiptContent.textContent()
  if (!mobileRequestReceiptBox || mobileRequestReceiptBox.x < 0 ||
      mobileRequestReceiptBox.x + mobileRequestReceiptBox.width > 390 ||
      !mobileRequestReceiptText?.includes('Request received') ||
      !mobileRequestReceiptText.includes('NFM-DEMO-RECEIPT-001')) {
    throw new Error(`Mobile confirmed-action receipt is missing or overflows: ${JSON.stringify({ mobileRequestReceiptBox, mobileRequestReceiptText })}`)
  }
  await mobileRequestReceipt.evaluate((element) => element.scrollIntoView({ block: 'center' }))
  await mobileConfirmationPage.screenshot({
    path: path.join(screenshotDir, 'dealership-request-receipt-mobile.png'),
    animations: 'disabled',
  })
  const mobileGenericResultResponse = mobileConfirmationPage.waitForResponse((response) => {
    if (!response.url().endsWith('/api/chat/me/query')) return false
    return response.request().postDataJSON()?.query === 'Show a generic action result.'
  })
  await mobileConfirmationPage.evaluate(() => {
    window.MaxMode.sendMessage('Show a generic action result.', {
      mode: 'executor',
      position: 'search',
      open: true,
    })
  })
  await mobileGenericResultResponse
  const mobileGenericResult = mobileConfirmationPage.locator('[data-max-mode-generic-action-result]').last()
  await mobileGenericResult.waitFor()
  const mobileGenericResultBox = await mobileGenericResult.boundingBox()
  const mobileGenericResultText = await mobileGenericResult.textContent()
  if (!mobileGenericResultBox || mobileGenericResultBox.x < 0 ||
      mobileGenericResultBox.x + mobileGenericResultBox.width > 390 ||
      !mobileGenericResultText?.includes('Reference Code') ||
      !mobileGenericResultText.includes('GENERIC-001') ||
      !mobileGenericResultText.includes('Demo team')) {
    throw new Error(`Generic mobile action result is unreadable or overflows: ${JSON.stringify({ mobileGenericResultBox, mobileGenericResultText })}`)
  }
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
    { name: 'loomai-platform', path: '/products/loomai-platform' },
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
