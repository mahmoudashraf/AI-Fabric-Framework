import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const server = await createServer({ root, logLevel: 'error', server: { host: '127.0.0.1', port: 0 } })
let browser
let page
const errors = [], unexpected = [], calls = []
let resetDenied = false
let draftRevision = 1
const observedAt = '2026-10-25T16:00:00Z'
const deployment = {
  id: 'deployment-one', name: 'Customer workspace', environment: 'production', templateId: 'original-template',
  behaviorType: 'CONVERSATIONAL', status: 'ACTIVE', activeVersion: 'v1', runtimeBaseUrl: null,
  source: { repository: 'fixture/repository', branch: 'main' }, binding: null,
  approvalRequiredForApply: true, approvalRequiredForDelete: true,
}
const portfolio = [{ deploymentId: deployment.id, name: deployment.name, environmentName: 'production',
  status: 'ACTIVE', tenantId: 'tenant-one', offerId: 'original-offer-no-longer-listed',
  bootstrapTargetProfileId: 'staging-target', activeVersionId: 'version-one', createdAt: observedAt }]
const versions = [2, 1].map(number => ({ id: `version-${number}`, deploymentId: deployment.id,
  versionLabel: `v${number}`, status: 'PUBLISHED', configHash: String(number).repeat(64), reindexRequired: false,
  publishedAt: observedAt, sourceCapabilityManifestRequired: false }))
const release = (id, versionId) => ({ id, deploymentId: deployment.id, deploymentVersionId: versionId,
  targetProfileId: 'production-target', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED',
  provisioningStatus: 'ACTIVE', currentStepDescription: 'Release verified.', updatedAt: observedAt, createdAt: observedAt })
const releases = [release('release-old', 'version-1')]
const consumers = [{ customerId: 'customer-one', consumerId: 'existing-consumer', displayName: 'Existing app assignment',
  boundDeploymentId: deployment.id, boundTargetProfileId: 'production-target', boundReleaseId: 'release-old', bindingRevision: 7 }]
const apps = [{ installationId: 'existing-app', customerId: 'customer-one', consumerId: 'existing-consumer', displayName: 'Customer AI',
  deploymentId: deployment.id, appUrl: 'https://existing-app.apps.loomai.pro', readinessChecks: [], domains: [],
  status: 'PUBLISHED', ready: true, experiencePackCode: 'generic', deliveryMode: 'STANDALONE',
  connectionMode: 'ANONYMOUS', interactionPolicy: 'PUBLIC', releaseId: 'release-old' }]
const draft = () => ({ id: 'draft-one', deploymentId: deployment.id, revisionNumber: draftRevision,
  status: 'DRAFT', updatedAt: observedAt, actionsConfig: { actions: [] }, entityConfig: { 'ai-entities': {} },
  routingConfig: { actions: {} }, providerConfig: { milvusExternalBindingRequired: true,
    aiStateStorageMode: 'MANAGED_POSTGRES', openaiTemperature: '0' }, promptConfig: {}, securityConfig: {},
  marketplaceDatasetConfig: { datasets: [] }, knowledgeSourceConfig: {} })
const installs = []
const pricing = { pricingModel: 'FREE', requiresEntitlement: false }
const contributions = { actionIds: [], knowledgeSourceIds: [], shellModuleIds: [], shellCardIds: [],
  automationWorkflowIds: [], inferenceProfileIds: [], inferenceEndpointProfileRefs: [], specialistBundleIds: [], specialistBundleRefs: [] }
const plugin = { id: 'plugin-one', slug: 'plugin-one', displayName: 'Customer plugin', pluginType: 'INFERENCE_PROFILE',
  publisherSlug: 'loomai', publisherDisplayName: 'LoomAI', shortDescription: 'A reusable deployment plugin.',
  status: 'PUBLISHED', latestVersion: '1.0.0', pricing, categories: [], contributions, updatedAt: observedAt }
const mutations = () => calls.filter(call => call.method !== 'GET' && call.path !== '/api/platform/preferences'
  && !call.path.endsWith('/validate'))

try {
  await server.listen()
  const base = server.resolvedUrls.local[0]
  browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' })
  page = await context.newPage()
  page.setDefaultTimeout(30000)
  page.on('pageerror', error => errors.push(error.message))
  await context.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.origin !== new URL(base).origin) {
      unexpected.push(`${request.method()} ${url.origin}${url.pathname}`)
      return route.abort()
    }
    if (url.pathname === '/runtime-config.js') return route.fulfill({ contentType: 'application/javascript',
      body: `window.__PLATFORM_RUNTIME_CONFIG__ = ${JSON.stringify({ apiBaseUrl: base.replace(/\/$/, '') })}` })
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname, method = request.method()
    calls.push({ path, method, query: url.search, body: request.postData(), headers: request.headers() })
    const json = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/api/platform/auth/customer-config') return json({ enabled: false })
    if (path === '/api/deployment-operations/status') return json({ mode: 'DISABLED', manualDeploymentsAllowed: true,
      serviceConfigured: false, providerUsageMode: 'TEAM_AUTHORIZED', decisionSource: 'CONFIGURATION' })
    if (path === '/api/platform/auth/session') return json({ enabled: true, authenticated: true,
      role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION', actorId: 'owner-one', customerId: 'customer-one',
      customerName: 'Customer One', displayName: 'Customer Owner' })
    if (path === '/api/platform/preferences') return json({})
    if (path === '/api/customer-self-service/catalogue' || path === '/api/customer-self-service/invitations') return json([])
    if (path === '/api/customer-self-service/deployments') return json(portfolio)
    if (path === '/api/platform/customers/customer-one/ai-workspace-installations') return json(apps)
    if (path === '/api/platform/customers/customer-one/consumers') return json(consumers)
    if (path === '/api/platform/customers/customer-one/consumers/existing-consumer/binding' && method === 'PUT') {
      const body = request.postDataJSON()
      assert.equal(releases[0].status, 'APPLIED_VERIFIED')
      assert.equal(body.expectedBindingRevision, consumers[0].bindingRevision)
      assert.equal(body.deploymentId, deployment.id)
      assert.equal(body.targetProfileId, 'production-target')
      assert.equal(body.releaseId, 'release-upgraded')
      Object.assign(consumers[0], { boundReleaseId: body.releaseId, boundTargetProfileId: body.targetProfileId,
        bindingRevision: consumers[0].bindingRevision + 1 })
      apps[0].releaseId = body.releaseId
      return json(consumers[0])
    }
    if (path === '/api/deployments') return json([deployment])
    if (path === '/api/deployments/deployment-one/target-profiles') return json([
      { id: 'production-target', name: 'Current production', active: true, providerType: 'COOLIFY', environmentName: 'production' },
      { id: 'staging-target', name: 'Customer staging', active: true, providerType: 'COOLIFY', environmentName: 'staging' },
    ])
    if (path === '/api/deployment-curated-modules') return json([])
    if (path === '/api/marketplace/plugins') return json([plugin])
    if (path === '/api/marketplace/categories') return json([])
    if (path === '/api/marketplace/plugins/plugin-one') return json({ plugin, versions: [{
      id: 'plugin-version-one', pluginId: plugin.id, version: '1.0.0', status: 'PUBLISHED', releaseChannel: 'STABLE',
      pricing, contributions, installForm: [], permissions: {}, recommendedPluginIds: [],
      compatibility: { requiredCapabilities: [], supportedDeploymentTargets: [], supportedAuthModes: [], supportedProviderModes: [] },
    }] })
    if (path === '/api/deployment-drafts/draft-one/validate') return json({ publishReady: true, issues: [] })
    if (path === '/api/deployment-drafts/draft-one/publish') {
      versions.unshift({ ...versions[0], id: 'version-3', versionLabel: 'v3' })
      return json(versions[0], 201)
    }
    if (path === '/api/deployment-drafts/draft-one' && method === 'PUT') {
      draftRevision++
      return json(draft())
    }
    const scoped = path.match(/^\/api\/deployments\/deployment-one\/(.+)$/)
    if (scoped) {
      const operation = scoped[1]
      if (operation === 'workspace') return json({ deployment, template: { id: 'original-template', name: 'Original template' },
        access: { assignmentRole: 'DEPLOYMENT_ADMIN', canEdit: true, canOperate: true, canAdmin: true },
        lifecycle: { liveVersionId: '' }, documentKnowledgeConfigured: false, conversationHandoverConfigured: false })
      if (operation === 'draft') return json(draft())
      if (operation === 'versions') return json(versions)
      if (operation === 'releases') return json(releases)
      if (operation === 'config-diff-center' || operation === 'integration-summary' || operation.endsWith('/provisioning-plan')) return json(null)
      if (operation.endsWith('/compatible-source-artifacts')) return json([{ id: 'image-optimized', serviceName: 'AI runtime', imageTag: 'optimized-current' }])
      if (operation === 'template-reset') {
        if (resetDenied) return json({ message: 'Deployment access denied.' }, 403)
        draftRevision++
        return json(draft())
      }
      if (operation.startsWith('apply/')) {
        const result = { ...release('release-upgraded', operation.split('/')[1]), status: 'APPLY_REQUESTED',
          verificationStatus: 'PENDING', provisioningStatus: 'QUEUED', currentStepDescription: 'Apply queued.' }
        releases.unshift(result)
        return json(result, 201)
      }
      if (operation === 'prompt-revisions') return json([])
      if (operation === 'desk') return json(null)
      if (operation === 'prompt-baseline' || operation === 'poc/prompt-session') return json(null)
      if (operation === 'secret-usage') return json({ secrets: [], items: [] })
      if (operation === 'webhooks/overview') return json({ deliveries: [], subscriptions: [], recentDeliveries: [] })
      if (operation === 'marketplace-installs') {
        if (method === 'POST') {
          const body = request.postDataJSON()
          assert.equal(body.pluginId, plugin.id)
          assert.equal(body.pluginVersion, '1.0.0')
          installs.push({ id: 'install-one', deploymentId: deployment.id, pluginId: plugin.id, pluginDisplayName: plugin.displayName,
            pluginType: plugin.pluginType, pluginVersion: '1.0.0', pluginVersionId: 'plugin-version-one', status: 'ENABLED',
            config: {}, secretRefs: {}, contributions, warnings: [], liveState: 'DRAFT_ONLY', readinessStatus: 'READY',
            entitlement: { ...pricing, status: 'ACTIVE', entitledForCompilation: true }, createdAt: observedAt, updatedAt: observedAt })
          return json(installs[0], 201)
        }
        return json(installs)
      }
      if (operation === 'marketplace-impact') return json({ deploymentId: deployment.id, totalInstalls: installs.length,
        ...contributions, installs: [], warnings: [], recommendedPluginIds: [] })
    }
    unexpected.push(`${method} ${path}`)
    return json({ message: 'Unexpected fixture request.' }, 404)
  })

  await page.goto(`${base}deployments?resourceKind=deployment&resourceId=deployment-one&deploymentId=deployment-one&releaseId=release-old`)
  const details = page.getByRole('complementary', { name: 'Customer workspace details' })
  const versionLink = details.getByRole('link', { name: 'Versions & updates', exact: true })
  assert((await versionLink.getAttribute('href')).includes('/revisions?deploymentId=deployment-one'))
  await details.getByRole('button', { name: 'Configure', exact: true }).click()
  await page.getByRole('heading', { name: 'Draft, publish, apply', exact: true }).waitFor()
  assert.equal(new URL(page.url()).searchParams.get('targetProfileId'), 'production-target')
  assert.equal(await page.getByRole('heading', { name: 'Deployment source', exact: true }).count(), 0)
  assert.equal(await page.getByRole('heading', { name: 'Deployment backup and restore', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Request approval', exact: true }).count(), 0)
  const currentRow = page.getByRole('row').filter({ has: page.getByText('v2', { exact: true }) })
  const apply = currentRow.getByRole('button', { name: 'Apply', exact: true })
  assert.equal(await apply.isDisabled(), true, 'An explicit compatible image is required')
  await page.getByRole('combobox', { name: 'Runtime source artifact', exact: true }).click()
  await page.getByRole('option', { name: 'AI runtime · optimized-current · image-optimized', exact: true }).click()
  await apply.click()
  await page.getByRole('alert').filter({ hasText: 'Release release-upgraded:' }).waitFor()
  const applyCall = mutations().find(call => call.path.includes('/apply/'))
  assert.equal(applyCall.path, '/api/deployments/deployment-one/apply/version-2')
  assert.equal(new URLSearchParams(applyCall.query).get('targetProfileId'), 'production-target')
  assert.equal(new URLSearchParams(applyCall.query).get('sourceArtifactId'), 'image-optimized')
  assert.equal(new URLSearchParams(applyCall.query).has('approvalId'), false)
  assert.ok(applyCall.headers['idempotency-key'])
  const assignments = page.getByRole('region', { name: 'Existing app release assignment' })
  const useVerified = assignments.getByRole('button', { name: 'Use verified release', exact: true })
  assert.equal(await useVerified.isDisabled(), true, 'Applying must not activate an unverified release')
  assert.equal(mutations().filter(call => call.path.endsWith('/binding')).length, 0)
  const existingAppUrl = await assignments.getByRole('link', { name: 'https://existing-app.apps.loomai.pro/' }).getAttribute('href')
  releases[0] = release('release-upgraded', 'version-2')
  await useVerified.click()
  await assignments.getByRole('alert').filter({ hasText: 'App assignment now uses verified release release-upgraded.' }).waitFor()
  const bindingCall = mutations().find(call => call.path.endsWith('/binding'))
  assert.equal(bindingCall.method, 'PUT')
  assert.equal(bindingCall.path, '/api/platform/customers/customer-one/consumers/existing-consumer/binding')
  const binding = JSON.parse(bindingCall.body)
  assert.equal(binding.expectedBindingRevision, 7)
  assert.equal(binding.releaseId, 'release-upgraded')
  assert.equal(binding.deploymentId, 'deployment-one')
  assert.equal(binding.targetProfileId, 'production-target')
  assert.ok(binding.operationKey)
  assert.equal(consumers[0].bindingRevision, 8)
  assert.equal(consumers[0].boundReleaseId, 'release-upgraded')
  assert.equal(await assignments.getByRole('link', { name: 'https://existing-app.apps.loomai.pro/' }).getAttribute('href'), existingAppUrl)

  const beforeCancel = mutations().length
  await page.getByRole('button', { name: 'Reset to template', exact: true }).click()
  await page.getByRole('dialog', { name: 'Reset to template?', exact: true }).getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.equal(mutations().length, beforeCancel)
  const beforeResetReads = calls.filter(call => call.path.endsWith('/draft')).length
  await page.getByRole('button', { name: 'Reset to template', exact: true }).click()
  await page.getByRole('button', { name: 'Reset draft settings', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'Template settings restored to the draft.' }).waitFor()
  const resetCall = mutations().at(-1)
  assert.equal(resetCall.path, '/api/deployments/deployment-one/template-reset')
  assert.equal(resetCall.method, 'POST')
  assert.equal(resetCall.body, null, 'The server resolves the original template; no reset body is allowed')
  assert(calls.filter(call => call.path.endsWith('/draft')).length > beforeResetReads)
  assert.equal(mutations().filter(call => call.path.includes('/apply/')).length, 1, 'Reset must not apply')
  assert.equal(mutations().filter(call => call.path.endsWith('/publish')).length, 0, 'Reset must not publish')

  resetDenied = true
  await page.getByRole('button', { name: 'Reset to template', exact: true }).click()
  await page.getByRole('button', { name: 'Reset draft settings', exact: true }).click()
  await page.getByRole('dialog').getByRole('alert').waitFor()
  assert.equal(await page.getByRole('button', { name: 'Reset draft settings', exact: true }).isEnabled(), true)
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  const deniedAttempts = mutations().filter(call => call.path.endsWith('/template-reset')).length
  assert.equal(deniedAttempts, 2, 'A rejected reset is not automatically retried')

  await page.getByRole('button', { name: 'Publish draft', exact: true }).click()
  await page.getByRole('row').filter({ has: page.getByText('v3', { exact: true }) }).waitFor()
  assert(mutations().some(call => call.path === '/api/deployment-drafts/draft-one/publish'))
  assert.equal(mutations().some(call => call.path.includes('/customer-self-service/')), false)
  for (const [label, heading] of [['Prompts', 'Prompt posture'], ['Providers', 'Provider profile editor'], ['Actions', 'Action catalog and connector routing']]) {
    await page.getByRole('navigation', { name: 'Deployment advanced configuration' }).getByRole('link', { name: label, exact: true }).click()
    await page.waitForURL(url => url.pathname === `/${label.toLowerCase()}`)
    await page.getByRole('navigation', { name: 'Deployment advanced configuration' }).waitFor()
    await page.getByRole('heading', { name: heading, exact: true }).waitFor()
    assert.equal(new URL(page.url()).searchParams.get('deploymentId'), 'deployment-one')
    if (label === 'Prompts') {
      await page.getByRole('spinbutton', { name: 'Max context characters', exact: true }).fill('8000')
      await page.getByRole('button', { name: 'Save prompt draft', exact: true }).click()
      await page.getByRole('alert').filter({ hasText: 'Prompt draft saved.' }).waitFor()
      assert.ok(JSON.parse(mutations().at(-1).body).promptConfig)
    }
    if (label === 'Providers') {
      await page.getByRole('textbox', { name: 'Orchestration model override', exact: true }).fill('quality-model')
      await page.getByRole('button', { name: 'Save provider config', exact: true }).click()
      await page.getByRole('alert').filter({ hasText: 'Provider config draft saved.' }).waitFor()
      const savedProvider = JSON.parse(mutations().at(-1).body).providerConfig
      assert.equal(savedProvider.orchestrationModel, 'quality-model')
      assert.equal(savedProvider.openaiTemperature, '0', 'Zero is a configured value, not an unset field')
      assert.equal(savedProvider.enableFallback, false)
      assert.equal(savedProvider.milvusExternalBindingRequired, true)
      assert.equal(savedProvider.aiStateStorageMode, 'MANAGED_POSTGRES')
      assert.equal(Object.values(savedProvider).some(value => typeof value === 'string' && !value.trim()), false)
    }
    if (label === 'Actions') {
      await page.getByRole('button', { name: 'Save actions config', exact: true }).click()
      await page.getByRole('alert').filter({ hasText: 'Actions draft saved.' }).waitFor()
      assert.ok(JSON.parse(mutations().at(-1).body).actionsConfig)
    }
  }
  await page.getByRole('navigation', { name: 'Deployment advanced configuration' }).getByRole('link', { name: 'Plugins', exact: true }).click()
  await page.getByRole('heading', { name: 'Marketplace', exact: true }).waitFor()
  assert.equal(await page.getByRole('combobox', { name: /^Target deployment/ }).isDisabled(), true)
  assert.equal(await page.getByRole('heading', { name: 'Publisher workspace', exact: true }).count(), 0)
  assert.equal(await page.getByRole('heading', { name: 'Managed inference services', exact: true }).count(), 0)
  await page.getByRole('button', { name: 'Install plugin', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: 'Customer plugin is now installed for deployment-one.' }).waitFor()
  assert(mutations().some(call => call.method === 'POST' && call.path === '/api/deployments/deployment-one/marketplace-installs'))
  assert.equal(calls.some(call => /\/marketplace\/(publishers|inference-services)/.test(call.path)), false)
  assert.equal(calls.some(call => call.path === '/api/deployment-templates'), false)
  assert.equal(errors.length, 0, errors.join('\n'))
  Object.assign(consumers[0], { boundTargetProfileId: 'staging-target', boundReleaseId: 'staging-release', bindingRevision: 9 })
  consumers.push({ customerId: 'foreign-customer', consumerId: 'foreign-consumer', displayName: 'Foreign customer app',
    boundDeploymentId: deployment.id, boundTargetProfileId: 'staging-target', boundReleaseId: 'foreign-release', bindingRevision: 1 },
  { customerId: 'customer-one', consumerId: 'other-deployment-consumer', displayName: 'Other deployment app',
    boundDeploymentId: 'other-deployment', boundTargetProfileId: 'staging-target', boundReleaseId: 'other-release', bindingRevision: 1 })
  await page.goto(`${base}revisions?deploymentId=deployment-one&targetProfileId=production-target`)
  const crossTarget = page.getByRole('region', { name: 'Existing app release assignment' })
  await crossTarget.getByText('Current target: staging-target / staging-release', { exact: true }).waitFor()
  await crossTarget.getByText('Selected target: production-target', { exact: true }).waitFor()
  await crossTarget.getByRole('alert').filter({ hasText: 'switches the app to the selected environment' }).waitFor()
  const beforeSwitch = mutations().filter(call => call.path.endsWith('/binding')).length
  assert.equal(beforeSwitch, 1, 'Reading another target must not switch the app')
  await crossTarget.getByRole('combobox', { name: /^App assignment/ }).click()
  assert.equal(await page.getByRole('option', { name: 'Foreign customer app', exact: true }).count(), 0)
  assert.equal(await page.getByRole('option', { name: 'Other deployment app', exact: true }).count(), 0)
  await page.getByRole('option', { name: 'Existing app assignment', exact: true }).click()
  await crossTarget.getByRole('button', { name: 'Use verified release', exact: true }).click()
  await crossTarget.getByText('This app assignment already uses the verified release.', { exact: true }).waitFor()
  const crossBinding = mutations().filter(call => call.path.endsWith('/binding'))
  assert.equal(crossBinding.length, beforeSwitch + 1)
  const switched = JSON.parse(crossBinding.at(-1).body)
  assert.equal(switched.expectedBindingRevision, 9)
  assert.equal(switched.targetProfileId, 'production-target')
  assert.equal(switched.releaseId, 'release-upgraded')
  assert.notEqual(switched.operationKey, binding.operationKey)
  assert.equal(consumers[0].boundTargetProfileId, 'production-target')
  assert.equal(consumers[0].bindingRevision, 10)
  assert.equal(await crossTarget.getByRole('link', { name: 'https://existing-app.apps.loomai.pro/' }).getAttribute('href'), existingAppUrl)
  await page.goto(`${base}revisions?deploymentId=foreign-deployment`)
  await page.getByRole('heading', { name: 'The requested deployment is not available', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Reset to template', exact: true }).count(), 0)
  assert.equal(calls.some(call => call.path.startsWith('/api/deployments/foreign-deployment/')), false)
  assert.deepEqual(unexpected, [])
  assert.deepEqual(errors, [])
  const security = await readFile(new URL('../src/pages/SecurityPage.tsx', import.meta.url), 'utf8')
  const verification = await readFile(new URL('../src/pages/VerificationPage.tsx', import.meta.url), 'utf8')
  const revisions = await readFile(new URL('../src/pages/RevisionsPage.tsx', import.meta.url), 'utf8')
  assert.equal(security.includes('Require approval before operators can apply versions'), false)
  assert(security.includes('Require approval before operators can delete deployments'))
  assert(security.includes('approvalRequiredForApply: false'))
  assert.equal(verification.includes('approvalRequiredForApply'), false)
  assert.equal(revisions.includes('approvalRequiredForApply'), false)
  assert(verification.includes('Direct apply'))
  console.log('PASS: shared editors/plugins, missing-offer access, direct production apply, explicit cross-target verified assignment with revision/idempotency and scope filtering, draft publish/reset and unchanged app URL.')
} catch (error) {
  console.error(JSON.stringify({ errors, unexpected, body: await page?.locator('body').innerText().catch(() => '') }))
  throw error
} finally {
  await browser?.close()
  await server.close()
}
