import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const output = process.env.CUSTOMER_OPERATIONS_UI_OUTPUT ?? `/private/tmp/01046-operations-ui-${Date.now()}`
await mkdir(output, { recursive: true })
const workspaceId = '\0operations-workspace.js', entryId = '\0operations-entry.jsx'
const server = await createServer({ root, configFile: false, envFile: false, cacheDir: output+'/vite-cache',
  esbuild: { jsx: 'automatic' }, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'customer-operations-fixture', enforce: 'pre',
    resolveId(id, importer) {
      if (id === '/operations-entry.jsx') return entryId
      if (id.endsWith('/workspace/DeploymentWorkspaceContext') && importer?.endsWith('/CustomerDeploymentConsole.tsx')) return workspaceId
    },
    load(id) {
      if (id === workspaceId) return `import {useState} from 'react';
        export function useDeploymentWorkspace(){const [id,setId]=useState('');window.__selectDeployment=setId;
        return {requestedDeploymentId:id,setSelectedDeploymentId:setId};}`
      if (id === entryId) return `import React,{useState} from 'react';import {createRoot} from 'react-dom/client';
        import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {MemoryRouter} from 'react-router-dom';
        import {ThemeProvider,createTheme,CssBaseline} from '@mui/material';
        import {CustomerDeploymentConsole} from '/src/components/customer/CustomerDeploymentConsole.tsx';
        const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
        window.__refresh=()=>client.invalidateQueries();
        function Harness(){const [mode,setMode]=useState('light');window.__theme=setMode;
        return React.createElement(ThemeProvider,{theme:createTheme({palette:{mode}})},React.createElement(CssBaseline),
          React.createElement(QueryClientProvider,{client},React.createElement(MemoryRouter,null,
            React.createElement(CustomerDeploymentConsole,{session:{customerId:'customer',actorId:'owner',role:'CUSTOMER_ADMIN',authenticationMode:'SESSION'}}))));
        }createRoot(document.getElementById('root')).render(React.createElement(Harness));`
    },
    configureServer(vite) { vite.middlewares.use((request, response, next) => {
      if (request.url !== '/operations-fixture') return next()
      response.setHeader('Content-Type', 'text/html')
      response.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>#root{padding:24px;min-width:0}</style></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/operations-entry.jsx"></script></body></html>')
    }) },
  }],
})
await server.listen()
const base = server.resolvedUrls.local[0], browser = await chromium.launch({ headless: true })
let passed = 0
const offerPath = '/api/customer-self-service/catalogue/generic-offer'
const deploymentPath = `${offerPath}/deployments/deployment-one`
const policy = mode => ({ mode, manualDeploymentsAllowed: true, serviceConfigured: mode !== 'DISABLED',
  providerUsageMode: 'TEAM_AUTHORIZED', decisionSource: 'EXTERNAL_SERVICE' })
const scopedCommand = body => {
  assert.equal(typeof body.operationKey, 'string'); assert.ok(body.operationKey.length > 0)
  assert.equal('allocationId' in body, false)
}

async function fixture({ mode = 'DISABLED', statusFailure = null, publishFailure = null, applyFailure = null,
  policyServiceDown = false, targetReadiness = 'REVIEWED_TARGET_READY' } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 1000 } }), page = await context.newPage()
  page.setDefaultTimeout(15000)
  const state = { calls: [], errors: [], escaped: [], status: policy(mode), statusFailure, publishFailure,
    applyFailure, deployments: [], versions: [], releases: [], consumers: [], readinessReady: false }
  const offer = { id: 'generic-offer', name: 'Support workspace', templateId: 'generic-template', fingerprint: 'offer-hash',
    entitlement: { customerId: 'customer', ownerUserId: 'owner', status: 'ACTIVE', revision: 3 },
    targets: ['staging', 'production'].map(id => ({ id, intent: id === 'staging' ? 'CUSTOMER_STAGING' : 'CUSTOMER_PRODUCTION',
      readiness: targetReadiness, capabilities: [], limits: { stagingExpiryHours: 24 }, blockers: [] })),
    settings: [], permissions: [], bindingSlots: [], blockers: [], workspaceProfiles: [] }
  const assessment = () => ({ mode, status: mode === 'DISABLED' || policyServiceDown ? 'BYPASSED' : mode === 'OBSERVE' ? 'OBSERVED' : 'ALLOWED',
    reason: policyServiceDown ? 'OPERATIONS_UNAVAILABLE' : mode === 'DISABLED' ? 'DISABLED_MANUAL_TEAM' : 'POLICY_ACCEPTED',
    assessmentId: mode === 'DISABLED' || policyServiceDown ? null : 'assessment-one',
    requestFingerprint: 'a'.repeat(64), policyRevision: 'policy-one', observedAt: '2026-10-24T15:00:00Z' })
  const release = target => ({ id: `${target}-release`, deploymentId: 'deployment-one', deploymentVersionId: 'version-one', targetProfileId: target,
    status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED', provisioningStatus: 'ACTIVE',
    currentStepDescription: 'Source and runtime checks need attention.', errorMessage: null,
    provisioningDetails: { deploymentOperations: assessment() }, createdAt: '2026-10-24T15:00:00Z' })
  const promotion = message => ({ deploymentId: 'deployment-one', versionId: 'version-one', status: 'READY', message,
    stagingTargetProfileId: 'staging', productionTargetProfileId: 'production', stagingReleaseId: 'staging-release',
    candidateFingerprint: 'candidate-one', resources: [], consumer: state.consumers[0] })
  const json = (route, value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    if (url.origin !== new URL(base).origin) { state.escaped.push(url.href); return route.abort() }
    if (!path.startsWith('/api/')) return route.continue()
    const body = request.postDataJSON()
    state.calls.push({ path, method, body })
    if (/\/(placement-evidence|admission-reservations|retirements)$/.test(path)) {
      state.errors.push('Legacy operations request: '+method+' '+path)
      return json(route, { message: 'Legacy operations are prohibited in this fixture' }, 500)
    }
    if (path === '/api/deployment-operations/status') return state.statusFailure
      ? json(route, { message: state.statusFailure.message }, state.statusFailure.status) : json(route, state.status)
    if (path === '/api/customer-self-service/catalogue') return json(route, [offer])
    if (path === '/api/customer-self-service/invitations') return json(route, [])
    if (path === '/api/customer-self-service/deployments') return json(route, state.deployments)
    if (path === '/api/platform/customers/customer/consumers') return json(route, state.consumers)
    if (path === '/api/platform/preferences') return json(route, body ?? {})
    if (path === `${offerPath}/deployments` && method === 'POST') {
      scopedCommand(body); assert.equal(body.expectedEntitlementRevision, 3); assert.equal(body.targetProfileId, 'staging')
      const deployment = { deploymentId: 'deployment-one', name: body.name, environmentName: 'customer-staging', status: 'DRAFT',
        tenantId: 'tenant-one', offerId: offer.id, offerFingerprint: offer.fingerprint,
        bootstrapTargetProfileId: 'staging', authorizationEntitlementRevision: 3, activeVersionId: null, createdAt: '2026-10-24T15:00:00Z' }
      state.deployments.push(deployment); return json(route, deployment)
    }
    if (path === '/api/deployments/deployment-one/versions') return json(route, state.versions)
    if (path === '/api/deployments/deployment-one/releases') return json(route, state.releases)
    if (path === '/api/deployments/deployment-one/verification-runs/recheck' && method === 'POST') {
      const selectedRelease = state.releases.find(item => item.id === url.searchParams.get('releaseId'))
      assert.ok(selectedRelease, 'Recheck must target an existing release in this deployment')
      assert.equal(selectedRelease.provisioningStatus, 'ACTIVE')
      Object.assign(selectedRelease, { status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED',
        currentStepDescription: 'Source and runtime verification passed.' })
      return json(route, { id: `verification-${selectedRelease.id}`, deploymentId: 'deployment-one',
        releaseId: selectedRelease.id, deploymentVersionId: selectedRelease.deploymentVersionId, status: 'PASSED' })
    }
    if (path === `${deploymentPath}/versions` && method === 'POST') {
      scopedCommand(body); assert.equal(body.expectedEntitlementRevision, 3); assert.equal(body.targetProfileId, 'staging')
      if (state.publishFailure) return json(route, { message: state.publishFailure.message }, state.publishFailure.status)
      const version = { id: 'version-one', deploymentId: 'deployment-one', versionLabel: 'v1', status: 'PUBLISHED' }
      state.versions = [version]; return json(route, version)
    }
    if (path === `${deploymentPath}/staging-consumer-assignments` && method === 'POST') {
      scopedCommand(body); assert.equal(body.expectedEntitlementRevision, 3)
      const consumer = { id: 'consumer-entity', consumerId: body.consumerId, displayName: body.displayName, customerId: 'customer',
        status: 'ACTIVE', bindingRevision: 0, boundDeploymentId: null, boundReleaseId: null, boundTargetProfileId: null }
      state.consumers.push(consumer); return json(route, consumer)
    }
    if (path === `${deploymentPath}/staging-releases` && method === 'POST') {
      scopedCommand(body); assert.equal(body.versionId, 'version-one'); assert.equal(body.expectedEntitlementRevision, 3)
      assert.equal('targetProfileId' in body, false)
      if (state.applyFailure) return json(route, { message: state.applyFailure.message }, state.applyFailure.status)
      state.releases = [release('staging')]; return json(route, state.releases[0])
    }
    if (path === `${deploymentPath}/staging-consumer-activations` && method === 'POST') {
      scopedCommand(body); assert.equal(body.releaseId, 'staging-release'); assert.equal(body.expectedEntitlementRevision, 3)
      assert.equal(state.releases.find(r => r.id === body.releaseId)?.verificationStatus, 'PASSED')
      assert.equal(body.expectedBindingRevision, state.consumers[0].bindingRevision)
      Object.assign(state.consumers[0], { boundDeploymentId: 'deployment-one', boundReleaseId: body.releaseId,
        boundTargetProfileId: 'staging', bindingRevision: state.consumers[0].bindingRevision + 1 })
      return json(route, state.consumers[0])
    }
    if (path === '/api/deployments/deployment-one/practical-promotion/plan') {
      scopedCommand(body); assert.equal(body.stagingReleaseId, 'staging-release'); assert.equal(body.productionTargetProfileId, 'production')
      assert.equal(body.expectedBindingRevision, state.consumers[0].bindingRevision)
      return json(route, promotion('Production candidate reviewed.'))
    }
    if (path === '/api/deployments/deployment-one/practical-promotion/production-apply') {
      scopedCommand(body); assert.equal(body.candidateFingerprint, 'candidate-one')
      state.releases.unshift(release('production')); return json(route, promotion('Production apply queued.'))
    }
    if (path === `${deploymentPath}/go-live-readiness`) return json(route, {
      offerId: offer.id, deploymentId: 'deployment-one', versionId: 'version-one', releaseId: 'production-release', targetProfileId: 'production',
      consumerId: state.consumers[0]?.consumerId, expectedBindingRevision: state.consumers[0]?.bindingRevision,
      activationReady: state.readinessReady, postActivationComplete: false, items: [], blockers: state.readinessReady ? [] : ['Runtime evidence incomplete'],
    })
    if (path === '/api/deployments/deployment-one/practical-promotion/activate-production-consumer') {
      scopedCommand(body); assert.equal(body.expectedBindingRevision, state.consumers[0].bindingRevision)
      assert.equal(body.productionReleaseId, 'production-release'); assert.equal(state.readinessReady, true)
      Object.assign(state.consumers[0], { boundReleaseId: 'production-release', boundTargetProfileId: 'production',
        bindingRevision: state.consumers[0].bindingRevision + 1 })
      return json(route, promotion('Production assignment activated.'))
    }
    state.errors.push('Unhandled '+method+' '+path)
    return json(route, { message: 'Unhandled fixture route' }, 500)
  })
  await page.goto(base+'operations-fixture', { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: 'My deployments' }).waitFor()
  return { state, page, async close() {
    if (state.errors.length) await page.screenshot({ path: output+'/failure.png', fullPage: true })
    await context.close(); assert.deepEqual(state.errors, []); assert.deepEqual(state.escaped, [])
  } }
}

async function createDeployment(f) {
  await f.page.getByRole('textbox', { name: 'Deployment name', exact: true }).fill('Acme support workspace')
  await f.page.getByRole('button', { name: 'Create staging deployment', exact: true }).click()
  await f.page.getByText('Customer staging configuration created from the reviewed product offer.', { exact: true }).waitFor()
}
async function publish(f) {
  await f.page.getByRole('button', { name: 'Publish current configuration', exact: true }).click()
  await f.page.getByText('Immutable version v1 published.', { exact: true }).waitFor()
}
async function verified(f, target) {
  const response = f.page.waitForResponse(result => {
    const url = new URL(result.url())
    return url.pathname.endsWith('/verification-runs/recheck') && url.searchParams.get('releaseId') === `${target}-release`
  })
  await f.page.getByRole('button', { name: `Verify ${target} release`, exact: true }).click()
  await response
  await f.page.getByText('Release verification passed.', { exact: true }).last().waitFor()
}

try {
  for (const mode of ['DISABLED', 'OBSERVE', 'ENFORCE']) {
    const f = await fixture({ mode })
    try {
      const panel = f.page.getByRole('region', { name: 'Operations policy', exact: true })
      await panel.getByText(mode === 'DISABLED' ? 'DISABLED · Manual team' : mode === 'OBSERVE' ? 'OBSERVE · Nonblocking' : 'ENFORCE · Server assessed', { exact: true }).waitFor()
      await panel.getByText('Default provider policy: team authorized', { exact: true }).waitFor()
      await panel.getByText('Existing funded allocations retain their limits.', { exact: true }).waitFor()
      assert.equal(await panel.getByText(/^Provider usage:/).count(), 0)
      assert.equal(await panel.getByRole('combobox').count(), 0, 'Customers cannot switch policy mode')
      assert.equal(await f.page.getByRole('button', { name: 'Verify placement', exact: true }).count(), 0)
      await createDeployment(f)
      await f.page.getByRole('textbox', { name: 'Assignment ID', exact: true }).fill('acme-workspace')
      await f.page.getByRole('textbox', { name: 'Display name', exact: true }).fill('Acme workspace')
      await f.page.getByRole('button', { name: 'Create staging assignment', exact: true }).click()
      await f.page.getByText('App assignment configured. Its staging release is awaiting activation.', { exact: true }).waitFor()
      assert.equal(f.state.releases.length, 0)
      await publish(f)
      await f.page.getByRole('button', { name: 'Deploy to customer staging', exact: true }).click()
      await f.page.getByText(/Staging release staging-release is queued/).waitFor()
      assert.equal(await f.page.getByRole('button', { name: 'Use latest verified staging release', exact: true }).count(), 0)
      assert.equal(await f.page.getByRole('button', { name: 'Review production candidate', exact: true }).isDisabled(), true)
      await verified(f, 'staging')
      await f.page.getByRole('button', { name: 'Use latest verified staging release', exact: true }).click()
      await f.page.getByText('App assignment now resolves to the latest verified staging release.', { exact: true }).waitFor()
      await f.page.getByRole('button', { name: 'Review production candidate', exact: true }).click()
      await f.page.getByText('Production candidate reviewed.', { exact: true }).first().waitFor()
      await f.page.getByRole('button', { name: 'Deploy production candidate', exact: true }).click()
      await f.page.getByText('Production apply queued.', { exact: true }).first().waitFor()
      assert.equal(await f.page.getByRole('button', { name: 'Activate production', exact: true }).isDisabled(), true)
      await verified(f, 'production')
      assert.equal(await f.page.getByRole('button', { name: 'Activate production', exact: true }).isDisabled(), true,
        'A verified runtime alone cannot replace candidate-bound go-live readiness')
      const before = structuredClone(f.state.consumers[0])
      f.state.readinessReady = true
      await f.page.getByRole('button', { name: 'Refresh production readiness', exact: true }).click()
      await f.page.getByText('Production readiness refreshed. No app assignment was changed.', { exact: true }).waitFor()
      assert.deepEqual(f.state.consumers[0], before)
      await f.page.getByRole('button', { name: 'Activate production', exact: true }).click()
      await f.page.getByText('Production assignment activated.', { exact: true }).first().waitFor()
      await f.page.getByText('Staging closure and retention are operator-managed. Automatic expiry and recovery are not configured in this console.', { exact: true }).waitFor()
      assert.equal(await f.page.getByRole('button', { name: /Retain staging|Extend staging|Preview.*recovery/i }).count(), 0)
      assert.equal(f.state.calls.some(c => /\/(placement-evidence|admission-reservations|retirements)$/.test(c.path)), false)
      if (mode === 'DISABLED') await panel.screenshot({ path: output+'/disabled-desktop.png' })
      if (mode === 'ENFORCE') {
        await f.page.evaluate(() => window.__theme('dark'))
        await panel.screenshot({ path: output+'/enforce-dark.png' })
        await f.page.setViewportSize({ width: 390, height: 844 })
        await panel.screenshot({ path: output+'/enforce-mobile.png' })
        assert.equal(await f.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true)
      }
      passed += 1; console.log(`Passed: ${mode} generic customer bootstrap, publish, assignment, verified promotion and operator-managed lifecycle`)
    } finally { await f.close() }
  }

  for (const failure of [{ status: 401, message: 'Authentication required' }, { status: 403, message: 'Customer scope denied' },
    { status: 503, message: 'Policy status could not be loaded' }]) {
    const f = await fixture({ statusFailure: failure })
    try {
      await f.page.getByRole('region', { name: 'Operations policy' }).getByText(failure.message, { exact: true }).waitFor()
      await f.page.getByRole('textbox', { name: 'Deployment name', exact: true }).fill('Acme support workspace')
      assert.equal(await f.page.getByRole('button', { name: 'Create staging deployment', exact: true }).isDisabled(), true)
      assert.equal(f.state.calls.some(c => c.method === 'POST' && c.path === `${offerPath}/deployments`), false)
      f.state.statusFailure = null
      await f.page.getByRole('button', { name: 'Refresh operations policy', exact: true }).click()
      await f.page.getByText('DISABLED · Manual team', { exact: true }).waitFor()
      assert.equal(await f.page.getByRole('button', { name: 'Create staging deployment', exact: true }).isDisabled(), false)
      passed += 1; console.log(`Passed: ${failure.status} status failure is visible and recovers only after an authenticated read`)
    } finally { await f.close() }
  }

  const denied = await fixture({ mode: 'ENFORCE', applyFailure: { status: 412, message: 'DEPLOYMENT_OPERATIONS_DENIED: CAPACITY_LIMIT' } })
  try {
    await createDeployment(denied); await publish(denied)
    await denied.page.getByRole('button', { name: 'Deploy to customer staging', exact: true }).click()
    await denied.page.getByText('DEPLOYMENT_OPERATIONS_DENIED: CAPACITY_LIMIT', { exact: true }).waitFor()
    assert.equal(denied.state.releases.length, 0)
    const original = denied.state.calls.find(c => c.method === 'POST' && c.path.endsWith('/staging-releases'))
    denied.state.applyFailure = null
    await denied.page.getByRole('button', { name: 'Deploy to customer staging', exact: true }).click()
    await denied.page.getByText(/Staging release staging-release is queued/).waitFor()
    const attempts = denied.state.calls.filter(c => c.method === 'POST' && c.path.endsWith('/staging-releases'))
    assert.equal(attempts.length, 2); assert.deepEqual(attempts[1].body, original.body)
    passed += 1; console.log('Passed: healthy ENFORCE denial remains visible; explicit retry retains the original command key')
  } finally { await denied.close() }

  const unavailable = await fixture({ mode: 'ENFORCE', policyServiceDown: true })
  try {
    await createDeployment(unavailable); await publish(unavailable)
    await unavailable.page.getByRole('button', { name: 'Deploy to customer staging', exact: true }).click()
    await unavailable.page.getByText('Operations assessment: BYPASSED · OPERATIONS_UNAVAILABLE', { exact: true }).waitFor()
    await unavailable.page.getByText('Optional policy assessment was bypassed for this release. Core authorization and release verification still apply.', { exact: true }).waitFor()
    assert.equal(await unavailable.page.getByRole('button', { name: 'Review production candidate', exact: true }).isDisabled(), true)
    passed += 1; console.log('Passed: recorded policy outage is a visible bypass, not invented release verification')
  } finally { await unavailable.close() }

  const forbidden = await fixture({ publishFailure: { status: 403, message: 'Credential ownership invalid' } })
  try {
    await createDeployment(forbidden)
    await forbidden.page.getByRole('button', { name: 'Publish current configuration', exact: true }).click()
    await forbidden.page.getByText('Credential ownership invalid', { exact: true }).waitFor()
    assert.equal(forbidden.state.versions.length, 0)
    assert.equal(await forbidden.page.getByRole('button', { name: 'Deploy to customer staging', exact: true }).isDisabled(), true)
    await forbidden.page.evaluate(() => window.__selectDeployment('foreign-deployment'))
    await forbidden.page.getByText('The requested deployment is unavailable in your customer portfolio. Select an owned deployment to continue.', { exact: true }).waitFor()
    assert.equal(await forbidden.page.getByRole('button', { name: 'Publish current configuration', exact: true }).count(), 0)
    passed += 1; console.log('Passed: credential errors and foreign deployment scope never gain apply access')
  } finally { await forbidden.close() }
  console.log(`Customer operations policy UI: ${passed} focused browser scenarios passed; local mocked APIs only. Screenshots: ${output}`)
} finally { await browser.close(); await server.close() }
