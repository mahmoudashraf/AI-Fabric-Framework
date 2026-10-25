import { spawn } from 'node:child_process'
import { chromium } from 'playwright'
import assert from 'node:assert/strict'

const port = 4392
const uiBaseUrl = `http://127.0.0.1:${port}`
const apiBaseUrl = 'http://localhost:8088'
const customerLayout = process.argv.includes('--customer-layout')

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
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  let staffTargetProfileRequests = 0
  let placementProbeRequests = 0
  let lifecycle = false
  let productionReady = false
  const commandKeys = new Map()
  const lifecycleCalls = []
  const releases = []
  const consumers = []
  const deploymentPath = '/api/customer-self-service/catalogue/dealership-offer/deployments/deployment-one'
  const version = { id: 'version-one', deploymentId: 'deployment-one', versionLabel: 'v1', status: 'PUBLISHED' }
  const release = (id, targetProfileId) => ({
    id, deploymentId: 'deployment-one', deploymentVersionId: version.id, targetProfileId,
    status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE',
    createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
  })
  const promotion = () => ({
    status: 'READY', message: 'Production candidate reviewed.', deploymentId: 'deployment-one', versionId: version.id,
    stagingTargetProfileId: 'staging-target', productionTargetProfileId: 'production-target',
    stagingReleaseId: 'staging-release', candidateFingerprint: 'candidate-fingerprint', resources: [], consumer: consumers[0],
  })
  const requireCommand = (request, operation) => {
    const body = request.postDataJSON()
    assert.equal(typeof body.operationKey, 'string')
    assert.ok(body.operationKey.length > 0)
    if (commandKeys.has(operation)) assert.equal(body.operationKey, commandKeys.get(operation))
    commandKeys.set(operation, body.operationKey)
    lifecycleCalls.push(operation)
    return body
  }
  const unhandledPaths = new Set()
  await page.route(`${apiBaseUrl}/**`, async (route) => {
    const path = new URL(route.request().url()).pathname
    const request = route.request()
    if (path === '/api/deployment-operations/status') {
      await json(route, { mode: 'DISABLED', manualDeploymentsAllowed: true, serviceConfigured: false,
        providerUsageMode: 'TEAM_AUTHORIZED', decisionSource: 'EXTERNAL_SERVICE' })
      return
    }
    if (/\/(placement-evidence|admission-reservations|retirements)$/.test(path)) {
      placementProbeRequests += 1
      unhandledPaths.add(`${request.method()} ${path}`)
      await json(route, { message: 'Legacy placement/admission must not be called' }, 500)
      return
    }
    if (lifecycle && path === '/api/deployments/deployment-one/apply/version-one') {
      assert.ok(request.headers()['idempotency-key'])
      assert.equal(new URL(request.url()).searchParams.get('targetProfileId'), 'staging-target')
      assert.equal(new URL(request.url()).searchParams.get('sourceArtifactId'), 'runtime-current')
      lifecycleCalls.push('APPLY')
      releases.push(release('staging-release', 'staging-target'))
      await json(route, releases[0])
      return
    }
    if (lifecycle && path === `${deploymentPath}/staging-consumer-assignments`) {
      const body = request.postDataJSON()
      if (body.releaseId) requireCommand(request, 'ASSIGN_VERIFIED')
      else {
        assert.equal('allocationId' in body, false, 'A configuration-only assignment must not manufacture allocation authority')
        lifecycleCalls.push('APP_CONFIGURE')
      }
      consumers.push({ consumerId: body.consumerId, displayName: body.displayName, bindingRevision: 1,
        customerId: 'customer-one', status: 'ACTIVE',
        boundDeploymentId: 'deployment-one', boundReleaseId: body.releaseId, boundTargetProfileId: 'staging-target' })
      await json(route, consumers[0])
      return
    }
    if (lifecycle && path === `${deploymentPath}/staging-consumer-activations`) {
      const body = requireCommand(request, 'ACTIVATE_STAGING')
      assert.equal(body.expectedBindingRevision, consumers[0].bindingRevision)
      Object.assign(consumers[0], { boundReleaseId: body.releaseId, bindingRevision: consumers[0].bindingRevision + 1 })
      await json(route, consumers[0])
      return
    }
    if (lifecycle && path === '/api/deployments/deployment-one/practical-promotion/plan') {
      requireCommand(request, 'PROMOTION_PLAN')
      await json(route, promotion())
      return
    }
    if (lifecycle && path === '/api/deployments/deployment-one/practical-promotion/production-apply') {
      requireCommand(request, 'PROMOTION_APPLY')
      releases.unshift(release('production-release', 'production-target'))
      await json(route, { ...promotion(), productionReleaseId: 'production-release', message: 'Production apply queued.' })
      return
    }
    if (lifecycle && path === `${deploymentPath}/go-live-readiness`) {
      await json(route, { deploymentId: 'deployment-one', offerId: 'dealership-offer', versionId: version.id,
        releaseId: 'production-release', targetProfileId: 'production-target', consumerId: consumers[0]?.consumerId,
        expectedBindingRevision: consumers[0]?.bindingRevision, activationReady: productionReady, postActivationComplete: false, items: [],
        blockers: productionReady ? [] : ['Production connection proof expired.'] })
      return
    }
    if (path === '/api/deployments/deployment-one/human-reviews') {
      await json(route, { message: 'Action review is unavailable in this fixture.' }, 400)
      return
    }
    if (lifecycle && path === '/api/deployments/deployment-one/practical-promotion/activate-production-consumer') {
      const body = requireCommand(request, 'ACTIVATE_PRODUCTION')
      assert.equal(body.expectedBindingRevision, consumers[0].bindingRevision)
      Object.assign(consumers[0], { boundReleaseId: 'production-release', boundTargetProfileId: 'production-target',
        bindingRevision: consumers[0].bindingRevision + 1 })
      await json(route, { ...promotion(), message: 'Production consumer activated.' })
      return
    }
    if (path === '/api/platform/auth/session') {
      await json(route, {
        enabled: true,
        headerName: 'X-PLATFORM-API-KEY',
        authenticated: true,
        actorId: 'customer-owner',
        displayName: 'Customer Owner',
        role: 'CUSTOMER_ADMIN',
        authenticationMode: 'SESSION',
        sessionAuthEnabled: true,
        apiKeyAuthEnabled: false,
        canManageUsers: false,
        canManageUserDirectory: true,
        canManageCustomers: false,
        canCreateCustomers: false,
        canManageSecrets: false,
        canOperateDeployments: true,
        customerId: 'customer-one',
        customerName: 'Customer One',
        customerSlug: 'customer-one',
      })
      return
    }
    if (path === '/api/platform/users/me/preferences' || path === '/api/platform/preferences') {
      await json(route, {})
      return
    }
    if (path === '/api/customer-self-service/invitations'
      || path === '/api/platform/customers/customer-one/ai-workspace-installations'
      || (path === '/api/deployments/deployment-one/versions' && !lifecycle)
      ) {
      await json(route, [])
      return
    }
    if (path === '/api/platform/customers/customer-one/consumers') {
      await json(route, consumers)
      return
    }
    if (lifecycle && path === '/api/deployments/deployment-one/versions') {
      await json(route, [version])
      return
    }
    if (path === '/api/deployments/deployment-one/versions/version-one/compatible-source-artifacts') {
      await json(route, [{ id: 'runtime-current', serviceName: 'Runtime', imageTag: 'current' }])
      return
    }
    if (path === '/api/customer-self-service/catalogue') {
      const limits = {
        measuredPolicyRef: 'policy', activeStagingTargets: 1, replicas: 1, cpuMillis: 500,
        memoryMiB: 512, diskMiB: 1024, databases: 1, indexes: 1, dependencies: 1,
        concurrentApplies: 1, stagingExpiryHours: 24, maxExtensionHours: 24, recoveryWindowHours: 24,
      }
      const dealershipOffer = {
        id: 'dealership-offer', name: 'Dealership Experience', templateId: 'dealership-template',
        templateVersionId: 'version', fingerprint: 'fingerprint',
        entitlement: {
          id: 'entitlement', offerId: 'dealership-offer', customerId: 'customer-one',
          ownerUserId: 'customer-owner', status: 'ACTIVE', revision: 1, expiresAt: '2026-12-01T00:00:00Z',
        },
        targets: [
          { id: 'staging-target', intent: 'CUSTOMER_STAGING', readiness: 'REVIEWED_TARGET_READY', capabilities: [], limits, blockers: [] },
          { id: 'production-target', intent: 'CUSTOMER_PRODUCTION', readiness: 'REVIEWED_TARGET_READY', capabilities: [], limits, blockers: [] },
        ],
        permissions: [], settings: [], bindingSlots: [{
          id: 'document-storage', kind: 'CUSTOMER_S3', supportedProfileIds: ['aws-s3-read-only'],
          pluginId: 'mkp-data-dealership-knowledge-v1', installConfigField: 'documentStorageBindingRef',
        }], documentAudiences: ['PUBLIC'], blockers: [],
      }
      await json(route, [dealershipOffer, {
        ...dealershipOffer,
        id: 'support-offer',
        name: 'Support Experience',
        templateId: 'support-template',
        entitlement: { ...dealershipOffer.entitlement, id: 'support-entitlement', offerId: 'support-offer' },
        targets: dealershipOffer.targets.map((target) => ({
          ...target,
          id: target.intent === 'CUSTOMER_STAGING' ? 'support-staging' : 'support-production',
        })),
      }])
      return
    }
    if (path === '/api/customer-self-service/deployments') {
      await json(route, [{
        deploymentId: 'deployment-one', name: 'Northfield workspace', environmentName: 'customer-staging',
        status: 'ACTIVE', tenantId: 'tenant-one', offerId: 'dealership-offer',
        bootstrapTargetProfileId: 'staging-target', offerFingerprint: 'fingerprint',
        authorizationEntitlementRevision: 1, activeVersionId: 'version-one', createdAt: '2026-10-16T00:00:00Z',
      }])
      return
    }
    if (path === '/api/deployments') {
      await json(route, [{
        id: 'deployment-one', name: 'Northfield workspace', environment: 'customer-staging',
        templateId: 'dealership-template', behaviorType: 'CONVERSATIONAL', binding: null,
        source: {}, status: 'ACTIVE', activeVersion: 'version-one', runtimeBaseUrl: null,
        connectorProvisioned: true, approvalRequiredForApply: false, approvalRequiredForDelete: false,
        createdAt: '2026-10-16T00:00:00Z',
      }])
      return
    }
    if (path === '/api/deployments/deployment-one/workspace') {
      await json(route, {
        deployment: {
          id: 'deployment-one', name: 'Northfield workspace', environment: 'customer-staging',
          templateId: 'dealership-template', behaviorType: 'CONVERSATIONAL', binding: null, source: {},
          access: { assignmentRole: 'ADMIN', canEdit: true, canOperate: true, canAdmin: true },
          status: 'ACTIVE', activeVersion: 'version-one', healthStatus: 'HEALTHY', healthSummary: 'Ready',
          runtimeBaseUrl: null, connectorProvisioned: true, approvalRequiredForApply: false,
          approvalRequiredForDelete: false, latestRelease: null, latestVerification: null, deletion: null,
          archivedAt: null, createdAt: '2026-10-16T00:00:00Z', updatedAt: '2026-10-16T00:00:00Z',
        },
        template: { id: 'dealership-template', name: 'Dealership Experience' },
        access: { assignmentRole: 'ADMIN', canEdit: true, canOperate: true, canAdmin: true },
        draft: { id: 'draft-one', revisionNumber: 1, status: 'DRAFT', updatedAt: '2026-10-16T00:00:00Z' },
        lifecycle: {
          savedDraftState: 'CURRENT', liveState: 'CURRENT', hasPublishedVersion: true, hasLiveVersion: false,
          savedDraftMatchesLatestPublished: true, liveMatchesLatestPublished: false,
          latestPublishedVersionId: 'version-one', latestPublishedVersionLabel: 'Version one',
          latestPublishedAt: '2026-10-16T00:00:00Z', liveVersionId: null, liveVersionLabel: null,
          liveAppliedAt: null, summaryMessage: 'Customer staging is ready.',
        },
        latestVersion: null, latestRelease: null, latestVerificationRun: null,
        versionCount: 1, releaseCount: 0, verificationRunCount: 0,
        documentKnowledgeConfigured: true, documentKnowledgeLive: false,
        externalIntegrationConfigured: true, externalIntegrationLive: false,
        conversationHandoverConfigured: true, conversationHandoverLive: false,
      })
      return
    }
    if (path === '/api/deployments/deployment-one/draft') {
      await json(route, { marketplaceDatasetConfig: { datasets: [] } })
      return
    }
    if (path === '/api/deployments/deployment-one/releases') {
      await json(route, releases)
      return
    }
    if (path === '/api/deployments/deployment-one/marketplace-installs'
      || path === '/api/deployments/deployment-one/document-storage-bindings') {
      await json(route, [])
      return
    }
    if (path === '/api/deployment-provider/target-profiles') {
      staffTargetProfileRequests += 1
      await json(route, { code: 'FORBIDDEN' }, 403)
      return
    }
    unhandledPaths.add(`${route.request().method()} ${path}`)
    await json(route, [])
  })

  await page.goto(`${uiBaseUrl}/${customerLayout ? 'runtime-artifacts' : 'deployments'}`, { waitUntil: 'networkidle' })
  await page.waitForURL(url => url.pathname === '/deployments')
  await page.goto(`${uiBaseUrl}/deployments?view=manage`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: 'My deployments' }).waitFor()

  await page.getByRole('combobox', { name: 'Customer staging target' }).click()
  const stagingOption = page.getByRole('option', { name: /staging-target/ })
  await stagingOption.waitFor()
  if ((await stagingOption.getAttribute('aria-disabled')) === 'true') {
    throw new Error('Placement-verified staging target was incorrectly disabled before admission.')
  }
  await page.keyboard.press('Escape')
  assert.equal(await page.getByRole('button', { name: 'Verify placement' }).count(), 0)
  await page.getByText('DISABLED · Manual team', { exact: true }).waitFor()
  await page.getByRole('textbox', { name: 'Deployment name' }).fill('Northfield second workspace')
  if (await page.getByRole('button', { name: 'Create staging deployment' }).isDisabled()) {
    throw new Error('Reviewed target and effective policy did not enable customer staging creation.')
  }
  await page.getByRole('combobox', { name: 'Product offer' }).click()
  await page.getByRole('option', { name: 'Support Experience' }).click()
  await page.getByRole('combobox', { name: 'Customer staging target' }).click()
  await page.getByRole('option', { name: /support-staging/ }).waitFor()
  if (await page.getByRole('option', { name: /staging-target/ }).count()) {
    throw new Error('Creation target remained coupled to the deployment selected for management.')
  }
  await page.keyboard.press('Escape')

  if (customerLayout) {
  const sidebar = page.getByRole('navigation', { name: 'Customer navigation' })
  for (const label of ['Deployments', 'Engage apps', 'Knowledge & data', 'Connections', 'Action reviews', 'Activity & alerts', 'Account & team']) {
    await sidebar.getByText(label, { exact: true }).waitFor()
  }
  for (const forbidden of ['Providers', 'Runtime Artifacts', 'Customers', 'Marketplace']) {
    if (await sidebar.getByText(forbidden, { exact: true }).count()) {
      throw new Error(`Customer navigation exposed staff area: ${forbidden}`)
    }
  }

  await page.goto(`${uiBaseUrl}/runtime-artifacts`, { waitUntil: 'networkidle' })
  await page.waitForURL(url => url.pathname === '/deployments')
  if (await page.getByRole('heading', { name: 'Runtime Artifacts' }).count()) {
    throw new Error('Direct staff route rendered inside the customer workspace.')
  }
  }

  await page.goto(`${uiBaseUrl}/document-knowledge?view=manage&deploymentId=deployment-one`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: 'Document Knowledge' }).waitFor()
  await page.getByRole('combobox', { name: 'Target profile' }).click()
  await page.getByRole('option', { name: /Customer staging.*Reviewed offer/ }).waitFor()
  await page.keyboard.press('Escape')
  if (staffTargetProfileRequests !== 0) {
    throw new Error('Customer knowledge page called the staff-only target-profile catalogue.')
  }
  for (const staffControl of customerLayout ? ['Runtime Swagger', 'Runtime auth', 'Diagnostics'] : []) {
    if (await page.getByText(staffControl, { exact: true }).count()) {
      throw new Error(`Customer workspace exposed a staff deployment control: ${staffControl}`)
    }
  }
  lifecycle = true
  await page.goto(`${uiBaseUrl}/deployments?view=manage`, { waitUntil: 'networkidle' })
  await page.getByRole('textbox', { name: 'Assignment ID', exact: true }).fill('customer-workspace')
  await page.getByRole('textbox', { name: 'Display name', exact: true }).fill('Customer workspace')
  await page.getByRole('button', { name: 'Create staging assignment', exact: true }).click()
  await page.getByText('App assignment configured. Its staging release is awaiting activation.').waitFor()
  assert.equal(releases.length, 0, 'The first app assignment must be configurable without a deployed runtime')
  await page.getByRole('combobox', { name: 'Staging runtime image', exact: true }).click()
  await page.getByRole('option', { name: 'Runtime / current / runtime-current', exact: true }).click()
  await page.getByRole('button', { name: 'Deploy to customer staging', exact: true }).click()
  await page.getByText(/Staging release staging-release is queued/).waitFor()
  await page.getByRole('button', { name: 'Use latest verified staging release', exact: true }).click()
  await page.getByText('App assignment now resolves to the latest verified staging release.').waitFor()
  await page.getByRole('button', { name: 'Review production candidate', exact: true }).click()
  await page.getByText('Production candidate reviewed.', { exact: true }).first().waitFor()
  await page.getByRole('button', { name: 'Deploy production candidate', exact: true }).click()
  await page.getByText('Production apply queued.', { exact: true }).first().waitFor()
  assert.equal(await page.getByRole('button', { name: 'Activate production', exact: true }).isDisabled(), true)
  const bindingBeforeRefresh = structuredClone(consumers[0])
  const operationsBeforeRefresh = [...lifecycleCalls]
  productionReady = true
  await page.getByRole('button', { name: 'Refresh production readiness', exact: true }).click()
  await page.getByText('Production readiness refreshed. No app assignment was changed.', { exact: true }).waitFor()
  assert.deepEqual(consumers[0], bindingBeforeRefresh, 'Readiness refresh must not activate or rebind an app')
  assert.deepEqual(lifecycleCalls, operationsBeforeRefresh, 'Readiness refresh must not dispatch a deployment operation')
  await page.getByRole('button', { name: 'Activate production', exact: true }).click()
  await page.getByText('Production consumer activated.', { exact: true }).first().waitFor()
  assert.deepEqual(lifecycleCalls, ['APP_CONFIGURE', 'APPLY', 'ACTIVATE_STAGING', 'PROMOTION_PLAN', 'PROMOTION_APPLY', 'ACTIVATE_PRODUCTION'])
  assert.equal(placementProbeRequests, 0, 'No legacy probe, admission or retirement request may run')
  await page.setViewportSize({ width: 390, height: 844 })
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true,
    'The complete production lifecycle dashboard must fit the mobile viewport')
  const steps = await page.locator('.MuiStepper-root').evaluate(element => element.getBoundingClientRect().right)
  assert.ok(steps <= 391, 'The lifecycle indicator must not overflow the page')
  await page.goto(`${uiBaseUrl}/human-review?deploymentId=deployment-one`, { waitUntil: 'networkidle' })
  await page.getByText('Action review is unavailable in this fixture.', { exact: true }).waitFor()
  assert.equal(await page.getByText('Inbox clear', { exact: true }).count(), 0,
    'An unavailable review inbox must never appear successfully empty')
  if (unhandledPaths.size > 0) {
    throw new Error(`Customer smoke received unhandled API calls: ${[...unhandledPaths].sort().join(', ')}`)
  }

  console.log('Customer navigation, route boundaries, and server-assessed staging/promotion UI flow passed without legacy admission.')
} finally {
  await browser.close()
  server.kill('SIGTERM')
  await new Promise((resolve) => server.once('exit', resolve))
}
