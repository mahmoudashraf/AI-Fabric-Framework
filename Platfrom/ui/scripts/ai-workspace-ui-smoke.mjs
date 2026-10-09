import { spawn } from 'node:child_process'
import { chromium } from 'playwright'

const port = 4391
const uiBaseUrl = `http://127.0.0.1:${port}`
const apiBaseUrl = 'http://localhost:8088'
const now = '2026-10-09T00:00:00Z'

const customers = [
  customer('customer-alpha', 'Alpha Customer', 'alpha-consumer'),
  customer('customer-beta', 'Beta Customer', 'beta-consumer'),
]
const installations = new Map([
  ['customer-alpha', [installation('awi_pub_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'customer-alpha', 'alpha-consumer', 'Alpha Workspace')]],
  ['customer-beta', [installation('awi_pub_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'customer-beta', 'beta-consumer', 'Beta Workspace')]],
])

function customer(id, name, consumerId) {
  return {
    id, name, slug: id, description: null, status: 'ACTIVE', platformManaged: false,
    tenantCount: 1, deploymentCount: 1, consumerCount: 1, createdAt: now, updatedAt: now,
    tenants: [],
    consumers: [{ consumerId, displayName: `${name} web`, status: 'ACTIVE' }],
  }
}

function installation(installationId, customerId, consumerId, displayName) {
  return {
    id: `internal-${installationId}`,
    installationId,
    customerId,
    consumerId,
    displayName,
    status: 'DRAFT',
    experiencePackCode: 'dealership',
    experiencePackVersion: '1.0.0',
    connectionMode: 'public-runtime-anonymous',
    connectionProfileCode: 'runtime-anonymous-direct',
    connectionProfileVersion: '1.0.0',
    connectionConfiguration: {},
    allowedOrigins: [`https://${customerId}.example`],
    configuration: {
      dealer: { id: customerId, assistantLabel: `${displayName} AI`, sourceMode: 'DEALERSHIP_INVENTORY' },
      page: { rootSelector: 'main', contextLabel: 'Current page' },
      capabilities: { comparison: true, testDrive: true, callback: true },
      presentation: { imageHostAllowlist: [], detailBasePath: '/vehicles/' },
      theme: { primaryColor: '#123b35' },
    },
    deploymentId: `dep-${customerId}`,
    releaseId: `rel-${customerId}`,
    assignmentRevision: `sha256:${'c'.repeat(64)}`,
    ready: true,
    readinessChecks: [{ code: 'ASSIGNMENT', status: 'PASSED', message: 'Assigned release is verified.' }],
    rowVersion: 0,
    createdAt: now,
    updatedAt: now,
    activatedAt: null,
    disabledAt: null,
  }
}

const server = spawn('npm', ['run', 'preview', '--', '--host', '127.0.0.1', '--port', String(port)], {
  cwd: process.cwd(),
  env: process.env,
  stdio: ['ignore', 'pipe', 'pipe'],
})
let serverOutput = ''
server.stdout.on('data', (chunk) => { serverOutput += chunk.toString() })
server.stderr.on('data', (chunk) => { serverOutput += chunk.toString() })

async function waitForServer() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (server.exitCode != null) throw new Error(`Platform UI preview exited early.\n${serverOutput}`)
    try {
      if ((await fetch(uiBaseUrl)).ok) return
    } catch {
      // Preview is still starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`Platform UI preview did not start.\n${serverOutput}`)
}

function json(route, value, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
}

const browser = await chromium.launch({ headless: true })
try {
  await waitForServer()
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await page.route(`${apiBaseUrl}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (path === '/api/platform/auth/session') {
      await json(route, {
        enabled: false, headerName: 'X-PLATFORM-API-KEY', authenticated: true,
        actorId: 'ui-smoke', displayName: 'UI Smoke', role: 'PLATFORM_ADMIN', authenticationMode: 'DISABLED',
        sessionAuthEnabled: false, apiKeyAuthEnabled: false, canManageUsers: true,
        canManageUserDirectory: true, canManageCustomers: true, canCreateCustomers: true,
        canManageSecrets: true, canOperateDeployments: true, customerId: null, customerName: null, customerSlug: null,
      })
      return
    }
    if (path === '/api/platform/customers') {
      await json(route, customers)
      return
    }
    if (path === '/api/platform/ai-workspaces/catalog') {
      await json(route, {
        assetsReady: true,
        assetStatus: 'READY',
        experiencePacks: [{ code: 'dealership', version: '1.0.0', name: 'Dealership', configurationSchemaVersion: 'v1', enabled: true }],
        connectionProfiles: [
          { code: 'runtime-anonymous-direct', version: '1.0.0', name: 'Direct anonymous runtime', mode: 'public-runtime-anonymous', handler: 'direct-public-runtime', configurationSchemaVersion: 'v1', enabled: true, availabilityMessage: 'Ready' },
          { code: 'runtime-authenticated-broker', version: '1.0.0', name: 'Authenticated runtime broker', mode: 'public-runtime-authenticated', handler: 'brokered-public-runtime', configurationSchemaVersion: 'v1', enabled: false, availabilityMessage: 'No reviewed broker endpoint is configured.' },
          { code: 'shopify-storefront-bridge', version: '1.0.0', name: 'Shopify storefront bridge', mode: 'backend-mediated-private-runtime', handler: 'private-backend-adapter', configurationSchemaVersion: 'v1', enabled: true, availabilityMessage: 'Ready' },
        ],
      })
      return
    }
    const collection = path.match(/^\/api\/platform\/customers\/([^/]+)\/ai-workspace-installations$/)
    if (collection && request.method() === 'GET') {
      await json(route, installations.get(collection[1]) ?? [])
      return
    }
    if (collection && request.method() === 'POST') {
      const payload = request.postDataJSON()
      const created = installation('awi_pub_cccccccccccccccccccccccccccccccc', collection[1], payload.consumerId, payload.displayName)
      installations.get(collection[1])?.push(created)
      await json(route, created, 201)
      return
    }
    const lifecycle = path.match(/^\/api\/platform\/customers\/([^/]+)\/ai-workspace-installations\/([^/]+)\/(activate|disable)$/)
    if (lifecycle && request.method() === 'POST') {
      const item = installations.get(lifecycle[1])?.find((candidate) => candidate.installationId === lifecycle[2])
      if (!item) {
        await json(route, { message: 'Not found' }, 404)
        return
      }
      item.status = lifecycle[3] === 'activate' ? 'ACTIVE' : 'DISABLED'
      item.activatedAt = lifecycle[3] === 'activate' ? now : item.activatedAt
      item.disabledAt = lifecycle[3] === 'disable' ? now : null
      item.rowVersion += 1
      await json(route, item)
      return
    }
    await json(route, { message: `Unhandled UI smoke route: ${request.method()} ${path}` }, 404)
  })

  await page.goto(`${uiBaseUrl}/ai-workspaces`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: 'AI Workspaces' }).waitFor()
  await page.getByText('Alpha Workspace', { exact: true }).waitFor()
  if (await page.getByText('Beta Workspace', { exact: true }).count()) {
    throw new Error('Customer-scoped installation list leaked a second customer row.')
  }

  const alphaRow = page.getByRole('row').filter({ hasText: 'Alpha Workspace' })
  await alphaRow.locator('button').nth(1).click()
  await alphaRow.getByText('ACTIVE', { exact: true }).waitFor()
  await alphaRow.locator('button').nth(1).click()
  await alphaRow.getByText('DISABLED', { exact: true }).waitFor()

  await page.getByLabel('Customer').click()
  await page.getByRole('option', { name: 'Beta Customer' }).click()
  await page.getByText('Beta Workspace', { exact: true }).waitFor()
  if (await page.getByText('Alpha Workspace', { exact: true }).count()) {
    throw new Error('Customer switch retained a row from the previous customer.')
  }

  await page.getByRole('button', { name: 'New workspace' }).click()
  await page.getByRole('heading', { name: 'Create AI Workspace' }).waitFor()
  await page.getByLabel('Workspace name').fill('Beta Second Workspace')
  await page.getByLabel('Allowed website origins').fill('https://beta.example')
  await page.getByLabel('Dealership ID').fill('beta-dealer')
  await page.getByRole('button', { name: 'Save draft' }).click()
  await page.getByText('Beta Second Workspace', { exact: true }).first().waitFor()
  await page.getByText('Use this exact script on an approved origin.', { exact: true }).waitFor()

  console.log('AI Workspace Platform UI smoke passed.')
} finally {
  await browser.close()
  server.kill('SIGTERM')
  await new Promise((resolve) => server.once('exit', resolve))
}
