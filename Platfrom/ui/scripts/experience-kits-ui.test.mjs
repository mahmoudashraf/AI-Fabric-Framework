import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withDeploymentTerminology } from './deployment-terminology-test-support.mjs'

async function loadTypescript(relative, dependencies) {
  const source = await readFile(new URL(relative, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(withDeploymentTerminology(source), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } })
  const exports = {}
  new Function('require', 'exports', compiled.outputText)(name => {
    if (!(name in dependencies)) throw new Error(`Unexpected test dependency ${name}`)
    return dependencies[name]
  }, exports)
  return exports
}

const calls = []
const api = await loadTypescript('../src/api/experienceKitsApi.ts', {
  './platformApi': { platformRequest: async (...args) => { calls.push(args); return args } },
})
const connectionModels = await loadTypescript('../src/components/customer/customerEngageConnection.ts', {})
const models = await loadTypescript('../src/components/kits/kitModels.ts', {
  '../../pages/aiWorkspaceAppearance': { readWorkspaceAppearance: configuration => ({ primaryColor: configuration.theme?.primaryColor ?? '#155eef' }) },
  '../customer/customerEngageConnection': connectionModels,
})
const form = (overrides = {}) => ({ name: 'Customer experience', workspaceName: 'Example AI', brandColor: '#28bfa6',
  standalone: true, embedded: false, appSlug: 'example-ai', websiteOrigin: '', humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null, ...overrides })
const workspace = (overrides = {}) => ({ installationId: 'awi-one', displayName: 'Example AI', configuration: { theme: { primaryColor: '#409aff' }, toolGroups: { default: { tools: [] } } },
  experiencePackCode: 'generic-workspace', deliveryMode: 'standalone', allowedOrigins: ['https://example-ai.apps.loomai.pro'], appSlug: 'example-ai',
  deploymentId: 'dep-one', releaseId: null, rowVersion: 7, ...overrides })
const kit = (overrides = {}) => ({ id: 'kit-one', kitCode: 'ai-customer-experience', name: 'Example', deploymentId: 'dep-one', targetProfileId: 'stage-one',
  installationId: 'awi-one', embeddedInstallationId: null, workspace: workspace(), embeddedWorkspace: null, deliveryMode: 'standalone', revision: 3,
  humanSupportEnabled: false, ticketingEnabled: false, ticketProvider: null, ...overrides })
const release = (id, overrides = {}) => ({ id, deploymentId: 'dep-one', targetProfileId: 'stage-one', createdAt: '2026-11-02T10:00:00Z',
  status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', ...overrides })

test('public catalogue is anonymous and read-only; customer reads use canonical endpoints', async () => {
  calls.length = 0
  await api.fetchPublicExperienceKits()
  await api.fetchExperienceKitCatalogue()
  await api.fetchExperienceKits()
  await api.fetchExperienceKit('kit/one&two')
  assert.equal(calls[0][0], '/api/public/experience-kits')
  assert.equal(calls[0][2], 'none')
  assert.deepEqual(calls.slice(1).map(call => call[0]), ['/api/customer-experience-kits/catalogue', '/api/customer-experience-kits', '/api/customer-experience-kits/kit%2Fone%26two'])
  assert.ok(calls.every(call => !call[1]?.method))
})

test('create posts the supplied operation key without generating another key or deploying products', async () => {
  const body = { kitCode: 'ai-customer-experience', operationKey: 'retry-the-same-key', deliveryMode: 'both' }
  calls.length = 0
  await api.createExperienceKit(body)
  await api.createExperienceKit(body)
  assert.equal(calls.length, 2)
  for (const [path, init] of calls) {
    assert.equal(path, '/api/customer-experience-kits')
    assert.equal(init.method, 'POST')
    assert.deepEqual(JSON.parse(init.body), body)
  }
})

test('update preserves kit and canonical installation concurrency revisions', async () => {
  const body = models.kitUpdateRequest(form({ embedded: true, websiteOrigin: 'https://www.example.com/' }), 3, 7, 12)
  assert.equal(body.expectedRevision, 3)
  assert.equal(body.expectedWorkspaceRevision, 7)
  assert.equal(body.expectedEmbeddedWorkspaceRevision, 12)
  assert.equal(body.deliveryMode, 'both')
  assert.equal(body.websiteOrigin, 'https://www.example.com')
  calls.length = 0
  await api.updateExperienceKit('kit/one', body)
  assert.equal(calls[0][0], '/api/customer-experience-kits/kit%2Fone')
  assert.equal(calls[0][1].method, 'PUT')
  assert.deepEqual(JSON.parse(calls[0][1].body), body)
})

test('all authenticated kit query identities include the current customer', () => {
  for (const key of ['customer', 'catalogue', 'list', 'detail', 'releases']) {
    const first = api.experienceKitKeys[key]('customer-a', 'resource-one')
    const second = api.experienceKitKeys[key]('customer-b', 'resource-one')
    assert.ok(first.includes('customer-a'))
    assert.notDeepEqual(first, second)
  }
})

test('delivery supports both canonical channels without inventing a widget delivery mode', () => {
  assert.equal(models.kitDeliveryMode(form()), 'standalone')
  assert.equal(models.kitDeliveryMode(form({ standalone: false, embedded: true })), 'embedded')
  assert.equal(models.kitDeliveryMode(form({ embedded: true })), 'both')
  assert.equal(models.kitDeliveryMode(form({ standalone: false })), null)
})

test('existing standalone allowed origins do not imply an embedded installation', () => {
  const value = models.kitFormFromSummary(kit())
  assert.equal(value.standalone, true)
  assert.equal(value.embedded, false)
})

test('both-channel form reads the secondary embedded installation origin', () => {
  const value = models.kitFormFromSummary(kit({ deliveryMode: 'both', embeddedInstallationId: 'awi-embed',
    embeddedWorkspace: workspace({ installationId: 'awi-embed', deliveryMode: 'embedded', allowedOrigins: ['https://www.example.com'] }) }))
  assert.equal(value.standalone, true)
  assert.equal(value.embedded, true)
  assert.equal(value.websiteOrigin, 'https://www.example.com')
})

test('deleted canonical installations remain missing rather than becoming fake ready products', () => {
  const value = models.kitFormFromSummary(kit({ workspace: null, installationId: null }))
  assert.equal(value.workspaceName, '')
  assert.equal(value.appSlug, '')
  assert.equal(value.websiteOrigin, '')
  assert.throws(() => models.kitEditorPath(kit({ installationId: null })), /not available/)
})

test('form requires a delivery channel, valid brand color and required address fields', () => {
  assert.deepEqual(models.kitExperienceErrors(form()), [])
  assert.ok(models.kitExperienceErrors(form({ embedded: true })).length)
  assert.ok(models.kitExperienceErrors(form({ standalone: false })).length)
  assert.ok(models.kitExperienceErrors(form({ brandColor: 'not-a-color' })).length)
  assert.ok(models.kitExperienceErrors(form({ appSlug: 'Bad Address' })).length)
  assert.ok(models.kitExperienceErrors(form({ name: ' ', workspaceName: ' ' })).length >= 2)
})

test('website configuration accepts only an HTTPS origin, never a URL containing credentials, queries or page paths', () => {
  for (const websiteOrigin of ['http://example.com', 'https://user:password@example.com', 'https://example.com/path', 'https://example.com?q=x', 'https://example.com/#x']) {
    assert.ok(models.kitExperienceErrors(form({ embedded: true, websiteOrigin })).length, websiteOrigin)
  }
  assert.deepEqual(models.kitExperienceErrors(form({ embedded: true, websiteOrigin: 'https://www.example.com/' })), [])
})

test('updates contain presentation and desired support settings only, not pack configuration or lifecycle commands', () => {
  const value = models.kitUpdateRequest(form({ humanSupportEnabled: true }), 3, 7)
  assert.deepEqual(Object.keys(value).sort(), ['name', 'workspaceName', 'brandColor', 'deliveryMode', 'appSlug', 'humanSupportEnabled',
    'expectedRevision', 'expectedWorkspaceRevision', 'expectedEmbeddedWorkspaceRevision', 'ticketingEnabled', 'ticketProvider'].sort())
  assert.equal(value.expectedEmbeddedWorkspaceRevision, null)
  assert.equal(value.humanSupportEnabled, true)
})

test('only an exact app assignment resolves the current release, never the newest successful attempt', () => {
  const records = [release('assigned'), release('newer', { createdAt: '2026-11-02T11:00:00Z' })]
  assert.equal(models.kitRelease(kit({ workspace: workspace({ releaseId: 'assigned' }) }), records).id, 'assigned')
  assert.equal(models.kitRelease(kit(), records), null)
  assert.equal(models.kitRelease(kit({ workspace: workspace({ releaseId: 'missing' }) }), records), null)
  assert.equal(models.kitRelease(kit({ workspace: workspace({ releaseId: 'assigned' }) }), [release('assigned', { deploymentId: 'other' })]), null)
})

test('latest attempt is separately labelled data and constrained to the exact deployment and target', () => {
  const records = [release('old'), release('latest', { createdAt: '2026-11-02T11:00:00Z' }),
    release('other-target', { targetProfileId: 'production', createdAt: '2026-11-02T12:00:00Z' })]
  assert.equal(models.kitLatestAttempt(kit(), records).id, 'latest')
  assert.equal(models.kitRelease(kit(), records), null)
})

test('business status says Live only for a ready app on its exact verified assigned release', () => {
  const app = workspace({ status: 'ACTIVE', ready: true, releaseId: 'assigned' })
  assert.deepEqual(models.kitAppPresentation(app, 'dep-one', [release('assigned')]), { label: 'Live', ready: true, active: true })
  assert.equal(models.kitAppPresentation(app, 'dep-one', [release('newer')]).label, 'Not live yet')
  assert.equal(models.kitAppPresentation(app, 'dep-one', [release('assigned', { verificationStatus: 'FAILED' })]).label, 'Not live yet')
  assert.equal(models.kitAppPresentation(app, 'dep-one', [release('assigned', { deploymentId: 'other' })]).label, 'Not live yet')
  assert.equal(models.kitAppPresentation({ ...app, deploymentId: 'other' }, 'dep-one', [release('assigned')]).label, 'Not live yet')
})

test('template canonical-control and human preferences never override blocked runtime readiness', () => {
  for (const humanSupportEnabled of [false, true]) {
    const value = kit({ kitCode: 'ai-support-experience', ticketingEnabled: true, humanSupportEnabled, ticketProvider: 'desk',
      workspace: workspace({ status: 'ACTIVE', ready: false, releaseId: 'assigned', experiencePackCode: 'support',
        configuration: { providerConfig: { canonicalConversationsEnabled: true }, capabilities: { tickets: true, handover: humanSupportEnabled } } }) })
    assert.deepEqual(models.kitAppPresentation(value.workspace, value.deploymentId, [release('assigned')]),
      { label: 'Setup needed', ready: false, active: false })
  }
})

test('new runtime capability readiness blockers remain visible without a frontend artifact guess', () => {
  const check = { code: 'CANONICAL_CONVERSATIONS', status: 'BLOCKED', message: 'Deploy the current runtime artifact with canonical conversation control before activating support.' }
  assert.equal(models.kitReadinessText(check), check.message)
})

test('draft, disabled and missing products have concise truthful business statuses', () => {
  const verified = [release('assigned')]
  assert.deepEqual(models.kitAppPresentation(workspace({ status: 'DRAFT', ready: true, releaseId: 'assigned' }), 'dep-one', verified),
    { label: 'Ready to go live', ready: true, active: false })
  assert.equal(models.kitAppPresentation(workspace({ status: 'DRAFT', ready: false, releaseId: 'assigned' }), 'dep-one', verified).label, 'Setup needed')
  assert.equal(models.kitAppPresentation(workspace({ status: 'ACTIVE', ready: true }), 'dep-one', verified).label, 'Not live yet')
  assert.equal(models.kitAppPresentation(workspace({ status: 'DISABLED', ready: true, releaseId: 'assigned' }), 'dep-one', verified).label, 'Paused')
  assert.deepEqual(models.kitAppPresentation(null, 'dep-one', verified), { label: 'Setup needed', ready: false, active: false })
})

test('editor and preview links use the correct canonical delivery channel', () => {
  const value = kit({ deliveryMode: 'both', embeddedInstallationId: 'awi-embed' })
  const editor = new URL(models.kitEditorPath(value, false, true), 'https://console.test')
  assert.equal(editor.searchParams.get('resourceId'), 'awi-embed')
  assert.equal(editor.searchParams.get('editor'), 'configuration')
  assert.equal(editor.searchParams.get('view'), null, 'Existing resource inspector selects the correct app editor')
  assert.equal(editor.searchParams.get('kitId'), value.id)
  const preview = new URL(models.kitEditorPath(value, true), 'https://console.test')
  assert.equal(preview.searchParams.get('resourceId'), 'awi-one')
  assert.equal(preview.searchParams.get('appTool'), 'appearance')
  assert.equal(preview.searchParams.has('view'), false)
  assert.equal(preview.searchParams.get('kitId'), value.id)
})

test('all editor and preview links retain the exact Kit origin, including embedded-only and encoded IDs', () => {
  const value = kit({ id: 'kit/a&b', deploymentId: 'dep/a&b', installationId: 'awi/a&b', embeddedInstallationId: 'embed/a&b', deliveryMode: 'both' })
  for (const preview of [false, true]) for (const embedded of [false, true]) {
    const path = new URL(models.kitEditorPath(value, preview, embedded), 'https://console.test')
    assert.equal(path.pathname, '/ai-workspaces')
    assert.equal(path.searchParams.get('kitId'), value.id)
    assert.equal(path.searchParams.get('resourceId'), embedded ? value.embeddedInstallationId : value.installationId)
    if (!preview) assert.equal(path.searchParams.get('deploymentId'), value.deploymentId)
  }
  const embeddedOnly = kit({ deliveryMode: 'embedded', embeddedInstallationId: null })
  const path = new URL(models.kitEditorPath(embeddedOnly), 'https://console.test')
  assert.equal(path.searchParams.get('resourceId'), embeddedOnly.installationId)
  assert.equal(path.searchParams.get('kitId'), embeddedOnly.id)
  assert.throws(() => models.kitEditorPath(embeddedOnly, true, true), /not available/)
})

test('first Brain entry retains the fresh Kit target instead of the runtime default', () => {
  for (const deliveryMode of ['standalone', 'embedded', 'both']) {
    const value = kit({ deliveryMode, targetProfileId: 'dtp-coolify-staging-behavior' })
    const path = new URL(models.kitDeploymentPath(value), 'https://console.test')
    assert.equal(path.pathname, '/smart-brains/dep-one')
    assert.equal(path.searchParams.get('targetProfileId'), value.targetProfileId)
    assert.equal(path.searchParams.has('releaseId'), false)
  }
})

test('Brain entry prefers the exact app-assigned release and target, not newer attempts or bootstrap defaults', () => {
  const value = kit({ workspace: workspace({ releaseId: 'assigned' }) })
  const records = [release('assigned', { targetProfileId: 'production' }),
    release('newer', { createdAt: '2026-11-02T11:00:00Z' })]
  const path = new URL(models.kitDeploymentPath(value, records), 'https://console.test')
  assert.equal(path.searchParams.get('targetProfileId'), 'production')
  assert.equal(path.searchParams.get('releaseId'), 'assigned')
  assert.equal(value.targetProfileId, 'stage-one', 'Navigation does not change the Kit or assignment')
})

test('missing, foreign or targetless assignments never substitute another release for Brain entry', () => {
  const assignedKit = kit({ workspace: workspace({ releaseId: 'assigned' }) })
  for (const [value, records] of [
    [kit(), [release('newer', { targetProfileId: 'production' })]],
    [assignedKit, []],
    [assignedKit, [release('other', { targetProfileId: 'production' })]],
    [assignedKit, [release('assigned', { deploymentId: 'foreign', targetProfileId: 'production' })]],
    [assignedKit, [release('assigned', { targetProfileId: null })]],
    [kit({ workspace: workspace({ releaseId: 'assigned', deploymentId: 'foreign' }) }), [release('assigned')]],
    [kit({ workspace: workspace({ releaseId: 'assigned', installationId: 'foreign' }) }), [release('assigned')]],
  ]) {
    const path = new URL(models.kitDeploymentPath(value, records), 'https://console.test')
    assert.equal(path.searchParams.get('targetProfileId'), value.targetProfileId)
    assert.equal(path.searchParams.has('releaseId'), false)
  }
})

test('both-channel Brain entry follows the primary app assignment without borrowing the secondary app release', () => {
  const value = kit({ deliveryMode: 'both', embeddedInstallationId: 'awi-embed',
    workspace: workspace({ releaseId: 'primary' }),
    embeddedWorkspace: workspace({ installationId: 'awi-embed', releaseId: 'secondary', deliveryMode: 'embedded' }) })
  const records = [release('primary', { targetProfileId: 'primary-target' }), release('secondary', { targetProfileId: 'secondary-target' })]
  const path = new URL(models.kitDeploymentPath(value, records), 'https://console.test')
  assert.equal(path.searchParams.get('targetProfileId'), 'primary-target')
  assert.equal(path.searchParams.get('releaseId'), 'primary')
})

test('Brain entry safely encodes identifiers and retains an assigned failing release for inspection', () => {
  const value = kit({ deploymentId: 'dep/a&b', workspace: workspace({ deploymentId: 'dep/a&b', releaseId: 'release/a&b' }) })
  const path = new URL(models.kitDeploymentPath(value, [release('release/a&b', {
    deploymentId: value.deploymentId, targetProfileId: 'target/a&b', verificationStatus: 'FAILED',
  })]), 'https://console.test')
  assert.equal(path.pathname, '/smart-brains/dep%2Fa%26b')
  assert.equal(path.searchParams.get('targetProfileId'), 'target/a&b')
  assert.equal(path.searchParams.get('releaseId'), 'release/a&b')
})

test('outbound app and demo links reject unsafe protocols and embedded credentials', () => {
  assert.equal(models.safeKitExternalUrl('javascript:alert(1)'), null)
  assert.equal(models.safeKitExternalUrl('https://name:secret@example.com'), null)
  assert.equal(models.safeKitExternalUrl('http://example.com'), null)
  assert.equal(models.safeKitExternalUrl('https://example.com/demo'), 'https://example.com/demo')
})

test('only customer-staging targets are offered during kit bootstrap', () => {
  assert.deepEqual(models.kitStagingTargets({ targets: [{ id: 'stage', intent: 'CUSTOMER_STAGING' }, { id: 'prod', intent: 'CUSTOMER_PRODUCTION' }] }).map(item => item.id), ['stage'])
})

test('readiness wording uses stable check codes, without changing status or the original diagnostic', () => {
  const check = { code: 'ASSIGNMENT', status: 'BLOCKED', message: 'A verified release binding is required.' }
  const before = structuredClone(check)
  assert.equal(models.kitReadinessText(check), 'Test and connect your AI assistant before this workspace goes live.')
  assert.deepEqual(check, before)
  assert.equal(models.kitReadinessText({ ...check, status: 'PASSED' }), 'Your AI service connection is verified.')
})

test('unknown checks and warning statuses retain their exact backend message', () => {
  for (const code of ['NEW_CHECK', 'constructor', '__proto__']) {
    assert.equal(models.kitReadinessText({ code, status: 'BLOCKED', message: 'Specific new failure.' }), 'Specific new failure.')
  }
  assert.equal(models.kitReadinessText({ code: 'APP_DOMAIN', status: 'WARNING', message: 'Check external sign-in redirects.' }), 'Check external sign-in redirects.')
})

test('plain-language readiness retains original codes, statuses and messages in technical details', async () => {
  const source = await readFile(new URL('../src/components/kits/ExperienceKitOverview.tsx', import.meta.url), 'utf8')
  assert.ok(source.includes('<summary>Technical details</summary>'))
  assert.ok(source.includes('{check.code} ({check.status})'))
  assert.ok(source.includes('<p>{check.message}</p>'))
  assert.ok(source.includes('kitReadinessText(check)'))
})

test('public page is prop-driven outside Routes, retaining selected-kit sign-up links', async () => {
  const source = await readFile(new URL('../src/pages/ExperienceKitsPublicPage.tsx', import.meta.url), 'utf8')
  assert.ok(!source.includes('useParams'))
  assert.ok(source.includes('consolePath?: string'))
  const chooser = await readFile(new URL('../src/components/kits/KitChooser.tsx', import.meta.url), 'utf8')
  assert.ok(source.includes('<KitChooser'))
  assert.ok(chooser.includes('/sign-up?kit=${definition.code}'))
})

test('setup prepares approved access once, previews nested pack configuration and retains retry identity', async () => {
  const source = await readFile(new URL('../src/components/kits/ExperienceKitSetup.tsx', import.meta.url), 'utf8')
  assert.ok(source.includes('if (!enrollment.current) enrollment.current = enrollPublicCustomerTemplates()'))
  assert.ok(source.includes('enabled: prepared'))
  assert.ok(source.includes('workspaceProfile?.interaction.configuration'))
  assert.ok(source.includes('sessionStorage.getItem(operationStorageKey)'))
  assert.ok(source.includes('operationKey: operation.current.key'))
  assert.ok(!/enableDeploymentDesk|activateAIWorkspace|publishCustomer|applyCustomer/.test(source))
})

test('Support is an explicit kit kind, never the adaptive shopping fallback', () => {
  assert.equal(models.kitPreviewKind('ai-support-experience'), 'support')
  assert.equal(models.kitDisplayName('ai-support-experience'), 'AI Support Experience')
})

test('ticket tracking and human support are independent, with explicit catalogue providers', () => {
  const providers = [{ code: 'desk', name: 'Desk-managed tickets', declaredOperations: ['createRequest'] }]
  const tracking = form({ ticketingEnabled: true, ticketProvider: 'desk' })
  assert.deepEqual(models.kitExperienceErrors(tracking, providers), [])
  assert.equal(models.kitUpdateRequest(tracking, 1, 2).humanSupportEnabled, false)
  assert.equal(models.kitUpdateRequest(tracking, 1, 2).ticketingEnabled, true)
  assert.ok(models.kitExperienceErrors(form({ ticketingEnabled: true }), providers).some(error => error.includes('provider')))
  assert.ok(models.kitExperienceErrors(form({ ticketingEnabled: true, ticketProvider: 'invented' }), providers).some(error => error.includes('unavailable')))
  assert.ok(models.kitExperienceErrors(tracking, []).length)
  const humanOnly = form({ humanSupportEnabled: true, ticketingEnabled: false, ticketProvider: 'desk' })
  assert.deepEqual(models.kitExperienceErrors(humanOnly, providers), [])
  assert.equal(models.kitUpdateRequest(humanOnly, 1, 2).ticketingEnabled, false)
})

test('support setup sends exact customer/install/target scope and opaque provider version', async () => {
  calls.length = 0
  await api.fetchKitSupportSpace('customer/a', 'installation/a', 'target/a', 'release/a')
  const body = { installationRevision: 7, assignmentRevision: 'assignment:opaque', expectedVersion: 'W/"opaque"',
    enabled: true, humanHandoverEnabled: false, ticketProvider: 'desk', closedRetentionDays: null, operationKey: 'same-key', targetProfileId: 'target/a', releaseId: 'release/a' }
  await api.configureKitSupportSpace('customer/a', 'installation/a', body)
  assert.equal(calls[0][0], '/api/platform/customers/customer%2Fa/ai-workspace-installations/installation%2Fa/support-space?targetProfileId=target%2Fa&releaseId=release%2Fa')
  assert.equal(calls[1][0], '/api/platform/customers/customer%2Fa/ai-workspace-installations/installation%2Fa/support-space')
  assert.equal(calls[1][1].method, 'PUT')
  assert.deepEqual(JSON.parse(calls[1][1].body), body)
  assert.notDeepEqual(api.experienceKitKeys.supportSpace('a', 'i', 't', 'r'), api.experienceKitKeys.supportSpace('b', 'i', 't', 'r'))
})
