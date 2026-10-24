import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('..', import.meta.url))
const probeModule = '\0scope-token-probe.js'
const server = await createServer({
  root, logLevel: 'error', server: { host: '127.0.0.1', port: 4396, strictPort: true },
  plugins: [{
    name: 'test-only-scope-token-probe',
    resolveId(id) { if (id === '/scope-token-probe.js') return probeModule },
    load(id) {
      if (id !== probeModule) return
      return `
        import React, { useRef, useState } from 'react';
        import { createRoot } from 'react-dom/client';
        import { useScopedOneTimeValue } from '/src/hooks/useScopedOneTimeValue.ts';
        function Probe() {
          const [scope, setScope] = useState('A');
          const [result, setResult] = useState('');
          const guard = useScopedOneTimeValue(scope);
          const retained = useRef(null);
          const pending = useRef(null);
          const button = (label, onClick) => React.createElement('button', { onClick }, label);
          return React.createElement('main', null,
            React.createElement('output', { 'data-testid': 'scope' }, scope),
            React.createElement('output', { 'data-testid': 'result' }, result),
            React.createElement('output', { 'data-testid': 'value' }, guard.value ?? ''),
            button('Retain handler', () => {
              retained.current = () => {
                const captured = guard.capture();
                pending.current = () => setResult(guard.publish(captured, 'stale-value') ? 'published' : 'discarded');
              };
            }),
            button('Start retained request', () => retained.current()),
            button('Complete retained request', () => pending.current()),
            button('Switch scope', () => setScope(value => value === 'A' ? 'B' : 'A')),
            button('Publish current', () => guard.publish(guard.capture(), 'current-value')));
        }
        createRoot(document.getElementById('root')).render(React.createElement(React.StrictMode, null, React.createElement(Probe)));
      `
    },
    configureServer(vite) {
      vite.middlewares.use((request, response, next) => {
        if (request.url !== '/scope-token-probe') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end('<!doctype html><html><body><div id="root"></div><script type="module" src="/scope-token-probe.js"></script></body></html>')
      })
    },
  }],
})
await server.listen()
const base = server.resolvedUrls.local[0]
const browser = await chromium.launch({ headless: true })
let passed = 0

const deployments = ['a', 'b'].map(id => ({
  deploymentId: `deployment-${id}`, name: `Workspace ${id.toUpperCase()}`, environmentName: 'customer-staging',
  status: 'ACTIVE', tenantId: `tenant-${id}`, offerId: 'support-offer', bootstrapTargetProfileId: 'staging',
  offerFingerprint: 'reviewed', authorizationEntitlementRevision: 1, activeVersionId: null,
  createdAt: '2026-10-18T00:00:00Z',
}))
const release = (deploymentId, targetProfileId, suffix) => ({
  id: `${deploymentId}-${suffix}`, deploymentId, targetProfileId, provisioningStatus: 'ACTIVE',
  status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED',
})
const releases = id => [release(id, 'staging', 's1'), release(id, 'staging', 's2'), release(id, 'production', 'p1')]

async function fixture({ initial = 'deployments?view=manage&deploymentId=deployment-b', preferred = 'deployment-a', operationsMode = 'DISABLED',
  failSelectionSave = false, dependencyBlocked = false } = {}) {
  const context = await browser.newContext()
  const page = await context.newPage()
  page.setDefaultTimeout(15_000)
  const errors = []
  const calls = []
  const pending = []
  let resolveDelivery
  const deliveryRequested = new Promise(resolve => { resolveDelivery = resolve })
  let actor = 'owner-one'
  let delayed = false
  let preferences = { deploymentWorkspace: { lastDeploymentId: preferred, lastSection: '/integrations' } }
  let catalogueReads = 0
  const json = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  page.on('pageerror', error => errors.push(error.message))
  await page.route(`${base}runtime-config.js`, route => route.fulfill({ contentType: 'application/javascript',
    body: `window.__PLATFORM_RUNTIME_CONFIG__ = ${JSON.stringify({ apiBaseUrl: base.replace(/\/$/, '') })}` }))
  await page.route(`${base}api/**`, async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    const body = request.postDataJSON()
    calls.push({ path, method, body })
    if (path === '/api/deployment-operations/status') return json(route, {
      mode: operationsMode, manualDeploymentsAllowed: true, serviceConfigured: operationsMode !== 'DISABLED',
      providerUsageMode: 'TEAM_AUTHORIZED', decisionSource: 'EXTERNAL_SERVICE',
    })
    if (path === '/api/platform/auth/session') return json(route, {
      enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION',
      actorId: actor, displayName: actor, customerId: 'customer-one', customerName: 'Customer One',
      canManageUsers: false, canManageCustomers: true, canManageUserDirectory: true, canOperateDeployments: true,
    })
    if (path === '/api/platform/preferences') {
      if (method === 'PUT' && failSelectionSave) {
        failSelectionSave = false
        return json(route, { message: 'Synthetic selection save failure' }, 503)
      }
      if (method === 'PUT') preferences = { ...preferences, ...body }
      return json(route, preferences)
    }
    if (path === '/api/customer-self-service/invitations') return json(route, [])
    if (path === '/api/platform/customers/customer-one/consumers') return json(route, [])
    if (path === '/api/platform/customers/customer-one/ai-workspace-installations') return json(route, [])
    if (path === '/api/customer-self-service/deployments') return json(route, deployments)
    if (path === '/api/customer-self-service/catalogue' || path === '/api/customer-self-service/catalogue/support-offer') {
      catalogueReads += 1
      const offer = {
        id: 'support-offer', name: 'Support experience', fingerprint: 'reviewed', templateId: 'support-template',
        entitlement: { customerId: 'customer-one', status: 'ACTIVE', revision: 1 },
        targets: ['staging', 'production'].map(target => ({
          id: target, intent: target === 'staging' ? 'CUSTOMER_STAGING' : 'CUSTOMER_PRODUCTION',
          readiness: 'REVIEWED_TARGET_READY',
          blockers: [], capabilities: ['INBOX_OPERATOR_ENROLL', 'INBOX_OPERATOR_ROTATE', 'INBOX_OPERATOR_DELIVER'], limits: { stagingExpiryHours: 24 },
        })), settings: [], bindingSlots: [], blockers: [],
      }
      return json(route, path.endsWith('/support-offer') ? offer : [offer])
    }
    if (path === '/api/deployments') return json(route, deployments.map(value => ({
      ...value, id: value.deploymentId, environment: value.environmentName, behaviorType: 'CONVERSATIONAL',
    })))
    const deployment = path.match(/^\/api\/deployments\/(deployment-[ab])\/(.+)$/)
    if (deployment) {
      const [, id, operation] = deployment
      if (operation === 'workspace') return json(route, {
        deployment: { ...deployments.find(value => value.deploymentId === id), id },
        access: { assignmentRole: 'DEPLOYMENT_ADMIN', canAdmin: true, canEdit: true, canOperate: true },
        documentKnowledgeConfigured: false, externalIntegrationConfigured: false, conversationHandoverConfigured: true,
      })
      if (operation === 'versions') return json(route, [])
      if (operation === 'releases') return json(route, releases(id))
      if (operation === 'draft') return json(route, { marketplaceDatasetConfig: { datasets: [] } })
      if (operation === 'marketplace/installs') return json(route, [])
      if (/^targets\/[^/]+\/releases\/[^/]+\/context$/.test(operation)) return json(route, {
        deploymentId: id, targetProfileId: operation.split('/')[1], releaseId: operation.split('/')[3],
        externalIntegrationConfigured: false, conversationHandoverConfigured: true,
      })
      if (operation.endsWith('/conversation-handover')) return json(route, {
        runtime: { channelBindingId: 'support-inbox', handoverCounts: {}, recentDispatches: [] },
        connector: { reachable: true, status: { recentCallbacks: [] } },
      })
      if (operation.endsWith('/managed-dependencies')) return json(route, {
        status: dependencyBlocked ? 'BLOCKED' : 'READY', configurationRevision: dependencyBlocked ? 2 : 1,
        effectiveConfigurationRevision: 1, failureCode: dependencyBlocked ? 'DEPENDENCY_ADMISSION_INVALID' : null,
        services: [{ slot: 'support-inbox' }],
        operators: [{ id: 'operator-one', displayName: 'Fixture operator', slot: 'support-inbox', status: 'ENROLLED',
          credentialRevision: 1, delivered: false, effectiveAt: '2026-10-18T00:00:00Z' }],
      })
      if (operation.endsWith('/operators/operator-one/credential-delivery')) {
        if (delayed) {
          await new Promise(resolve => {
            pending.push(async () => {
              try { await json(route, { apiKey: 'SYNTHETIC-OPERATOR-CREDENTIAL' }) } finally { resolve() }
            })
            resolveDelivery()
          })
          return
        }
        return json(route, { apiKey: 'SYNTHETIC-OPERATOR-CREDENTIAL' })
      }
    }
    if (/^\/api\/customer-self-service\/catalogue\/support-offer\/deployments\/deployment-[ab]\/(admission-reservations|retirements)$/.test(path)) return json(route, [])
    errors.push(`Unhandled ${method} ${path}`)
    return json(route, { message: 'Unexpected fixture API' }, 404)
  })
  await page.goto(`${base}${initial}`, { waitUntil: 'networkidle' })
  return {
    page, context, errors, calls, pending, deliveryRequested,
    delay: () => { delayed = true },
    setActor: value => { actor = value },
    get catalogueReads() { return catalogueReads },
    async close() {
      for (const complete of pending.splice(0)) await complete()
      await context.close()
      assert.deepEqual(errors, [])
      assert.equal(calls.some(call => /placement-evidence|admission-reservations|\/probe(?:\/|$)/.test(call.path)), false,
        'Customer navigation and operator commands must not depend on legacy admission or placement')
    },
  }
}

async function selectedConsole(page, id) {
  await page.waitForURL(url => url.pathname === '/deployments' && url.searchParams.get('deploymentId') === `deployment-${id}`)
  await page.getByRole('heading', { name: `Workspace ${id.toUpperCase()}`, exact: true, level: 5 }).waitFor()
}

async function selectRelease(page, id) {
  try { await page.getByRole('combobox', { name: /^Target release/ }).click() }
  catch (error) { throw new Error(`${error.message}\nRendered body: ${await page.locator('body').innerText()}`) }
  await page.getByRole('option').filter({ hasText: id }).click()
  await page.getByRole('heading', { name: 'Inbox operators' }).waitFor()
}

async function spaNavigate(page, path) {
  await page.evaluate(path => {
    window.history.pushState(null, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
}

async function hasNoCredential(page) {
  await page.waitForFunction(() => !document.body.textContent.includes('SYNTHETIC-OPERATOR-CREDENTIAL'))
  assert.equal(await page.getByText(/One-time operator credential:/).count(), 0)
}

try {
  for (const transition of ['cross-scope', 'ABA', 'delayed-ABA']) {
    const page = await browser.newPage()
    try {
      await page.goto(`${base}scope-token-probe`)
      await page.getByTestId('scope').filter({ hasText: 'A' }).waitFor()
      await page.getByRole('button', { name: 'Retain handler', exact: true }).click()
      if (transition === 'delayed-ABA') await page.getByRole('button', { name: 'Start retained request', exact: true }).click()
      await page.getByRole('button', { name: 'Switch scope', exact: true }).click()
      await page.getByTestId('scope').filter({ hasText: 'B' }).waitFor()
      if (transition !== 'cross-scope') {
        await page.getByRole('button', { name: 'Switch scope', exact: true }).click()
        await page.getByTestId('scope').filter({ hasText: 'A' }).waitFor()
      }
      if (transition !== 'delayed-ABA') await page.getByRole('button', { name: 'Start retained request', exact: true }).click()
      await page.getByRole('button', { name: 'Complete retained request', exact: true }).click()
      await page.getByTestId('result').filter({ hasText: 'discarded' }).waitFor()
      assert.equal(await page.getByTestId('value').innerText(), '')
      await page.getByRole('button', { name: 'Publish current', exact: true }).click()
      await page.getByTestId('value').filter({ hasText: 'current-value' }).waitFor()
      passed += 1
      console.log(`Passed: retained render handler cannot obtain a newer token (${transition})`)
    } finally { await page.close() }
  }

  const navigation = await fixture()
  try {
    const { page } = navigation
    await selectedConsole(page, 'b')
    await page.waitForFunction(() => document.querySelector('a[href="/integrations?deploymentId=deployment-b"]'))
    const sidebar = page.getByRole('navigation', { name: 'Customer navigation' })
    assert.equal(await sidebar.getByRole('link', { name: 'Knowledge & data', exact: true }).getAttribute('href'), '/document-knowledge?deploymentId=deployment-b')
    assert.equal(await sidebar.getByRole('link', { name: 'Connections', exact: true }).getAttribute('href'), '/integrations?deploymentId=deployment-b')
    await page.getByRole('heading', { name: 'Workspace A', exact: true, level: 6 }).locator('..').locator('..').locator('..')
      .getByRole('button', { name: 'Manage deployment' }).click()
    await selectedConsole(page, 'a')
    assert.equal(await sidebar.getByRole('link', { name: 'Knowledge & data', exact: true }).getAttribute('href'), '/document-knowledge?deploymentId=deployment-a')
    await page.goBack()
    await selectedConsole(page, 'b')
    await page.goForward()
    await selectedConsole(page, 'a')
    await page.reload({ waitUntil: 'networkidle' })
    await selectedConsole(page, 'a')
    await sidebar.getByRole('link', { name: 'Connections', exact: true }).click()
    await page.getByRole('heading', { name: 'Connections', exact: true }).waitFor()
    await page.goBack()
    await selectedConsole(page, 'a')
    assert.ok(navigation.calls.some(call => call.path === '/api/platform/preferences' && call.method === 'PUT'
      && call.body.deploymentWorkspace.lastDeploymentId === 'deployment-a'))
    passed += 1
    console.log('Passed: deep link, shared sidebar, selection persistence, reload, Back/Forward and return navigation')
  } finally { await navigation.close() }

  const preferred = await fixture({ initial: 'deployments?view=manage', preferred: 'deployment-b' })
  try { await selectedConsole(preferred.page, 'b'); passed += 1; console.log('Passed: saved workspace preference seeds a missing URL selection') }
  finally { await preferred.close() }

  const retrySelection = await fixture({ failSelectionSave: true })
  try {
    await selectedConsole(retrySelection.page, 'b')
    await retrySelection.page.getByRole('button', { name: 'Retry selection save' }).click()
    await retrySelection.page.waitForFunction(() => !document.body.textContent.includes('Deployment selection could not be persisted.'))
    const writes = retrySelection.calls.filter(call => call.path === '/api/platform/preferences' && call.method === 'PUT')
    assert.equal(writes.length, 2)
    assert.deepEqual(writes[0].body, writes[1].body)
    passed += 1
    console.log('Passed: selection persistence failure is visible and retryable without changing resource scope')
  } finally { await retrySelection.close() }

  const missing = await fixture({ initial: 'deployments?view=manage&deploymentId=foreign-or-missing' })
  try {
    await missing.page.getByText(/requested deployment is unavailable/).waitFor()
    assert.equal(await missing.page.getByRole('heading', { level: 5 }).count(), 0)
    assert.equal(new URL(missing.page.url()).searchParams.get('deploymentId'), 'foreign-or-missing')
    assert.equal(missing.calls.some(call => call.path.includes('/foreign-or-missing/')), false)
    assert.equal(missing.calls.some(call => call.path.includes('/deployment-a/versions')), false)
    passed += 1
    console.log('Passed: explicit unknown deployment does not silently select or operate another resource')
  } finally { await missing.close() }

  for (const operationsMode of ['DISABLED', 'OBSERVE', 'ENFORCE']) {
    const placement = await fixture({ operationsMode })
    try {
      const { page } = placement
      await page.getByRole('region', { name: 'Operations policy', exact: true }).waitFor()
      await page.getByRole('textbox', { name: 'Deployment name' }).fill('Generic support workspace')
      assert.equal(await page.getByRole('button', { name: 'Verify placement', exact: true }).count(), 0)
      assert.equal(await page.getByRole('button', { name: 'Create staging deployment', exact: true }).isDisabled(), false)
      passed += 1
      console.log(`Passed: ${operationsMode} leaves policy assessment to core without legacy placement gates`)
    } finally { await placement.close() }
  }

  const blockedInbox = await fixture({ initial: 'integrations?view=manage&deploymentId=deployment-a', dependencyBlocked: true })
  try {
    await selectRelease(blockedInbox.page, 'deployment-a-s1')
    await blockedInbox.page.getByRole('alert').filter({ hasText: 'Inbox configuration: DEPENDENCY_ADMISSION_INVALID' }).waitFor()
    assert.equal(await blockedInbox.page.getByRole('button', { name: 'Deliver once', exact: true }).isDisabled(), true)
    assert.equal(blockedInbox.calls.some(call => call.path.endsWith('/credential-delivery')), false)
    passed += 1
    console.log('Passed: ineffective inbox configuration warns and prevents credential delivery')
  } finally { await blockedInbox.close() }

  for (const change of ['release', 'target', 'deployment', 'actor']) {
    const current = await fixture({ initial: 'integrations?view=manage&deploymentId=deployment-a' })
    try {
      const { page } = current
      await selectRelease(page, 'deployment-a-s1')
      await page.getByRole('button', { name: 'Deliver once', exact: true }).click()
      await page.getByText(/One-time operator credential:.*SYNTHETIC-OPERATOR-CREDENTIAL/).waitFor()
      assert.deepEqual(current.errors, [], 'No render or API error may accompany credential delivery')
      if (change === 'release' || change === 'target') await selectRelease(page, change === 'release' ? 'deployment-a-s2' : 'deployment-a-p1')
      else if (change === 'deployment') {
        await spaNavigate(page, '/integrations?view=manage&deploymentId=deployment-b')
        await selectRelease(page, 'deployment-b-s1')
      } else {
        current.setActor('owner-two')
        await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')))
        await page.getByText('owner-two', { exact: true }).waitFor()
        await selectRelease(page, 'deployment-a-s1')
      }
      await hasNoCredential(page)
      passed += 1
      console.log(`Passed: delivered credential clears on ${change} change`)
    } finally { await current.close() }
  }

  for (const change of ['release', 'target', 'deployment', 'actor', 'round-trip']) {
    const current = await fixture({ initial: 'integrations?view=manage&deploymentId=deployment-a' })
    try {
      const { page } = current
      await selectRelease(page, 'deployment-a-s1')
      current.delay()
      await page.getByRole('button', { name: 'Deliver once', exact: true }).click()
      await current.deliveryRequested
      assert.equal(current.pending.length, 1, 'The response must still be pending at scope transition')
      if (change === 'deployment') {
        await spaNavigate(page, '/integrations?view=manage&deploymentId=deployment-b')
        await selectRelease(page, 'deployment-b-s1')
      } else if (change === 'actor') {
        current.setActor('owner-two')
        await page.evaluate(() => window.dispatchEvent(new Event('visibilitychange')))
        await page.getByText('owner-two', { exact: true }).waitFor()
        await selectRelease(page, 'deployment-a-s1')
      } else {
        await selectRelease(page, change === 'release' ? 'deployment-a-s2' : 'deployment-a-p1')
        if (change === 'round-trip') await selectRelease(page, 'deployment-a-s1')
      }
      const response = page.waitForResponse(response => response.url().includes('/credential-delivery'))
      await current.pending.shift()()
      await response
      await page.getByRole('button', { name: 'Deliver once', exact: true }).waitFor({ state: 'visible' })
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.textContent === 'Deliver once' && !button.disabled))
      await hasNoCredential(page)
      passed += 1
      console.log(`Passed: delayed completion is discarded after ${change}`)
    } finally { await current.close() }
  }
  console.log(`Customer UI scope regressions: ${passed} scenarios passed. Local mocked APIs only.`)
} finally {
  await browser.close()
  await server.close()
}
