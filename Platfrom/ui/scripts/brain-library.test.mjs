import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const result = {}
  new Function('require', 'exports', ts.transpileModule(await readFile(new URL(path, import.meta.url), 'utf8'), { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText)(name => name in dependencies ? dependencies[name] : require(name), result)
  return result
}
const terminology = { deploymentTerm: { singular: 'Smart Brain', lowercase: 'smart brain', plural: 'Smart Brains' } }
const model = await load('../src/components/customer/customerResourceModels.ts', { '../../deploymentTerminology': terminology })
const nav = await load('../src/brain/brainNavigation.ts')
const build = await load('../src/assistant/BuildWithAIButton.tsx')
const router = { Link: React.forwardRef(function TestLink({ to, ...props }, ref) { return React.createElement('a', { ...props, href: to, ref }) }) }
const { BrainLibrary } = await load('../src/components/customer/BrainLibrary.tsx', { './customerResourceModels': model })
const { BrainLibraryInspector } = await load('../src/components/customer/BrainLibraryInspector.tsx', {
  './customerResourceModels': model, '../../brain/brainNavigation': nav, '../../assistant/BuildWithAIButton': build, 'react-router-dom': router,
})
const deployment = { deploymentId: 'brain-owned', name: 'Customer Support Brain', status: 'DRAFT', environmentName: 'staging', offerId: 'offer', activeVersionId: null }
const profiles = [{ id: 'stage', environmentName: 'staging', active: true }, { id: 'prod', environmentName: 'production', active: true }]
const app = { installationId: 'app-owned', deploymentId: deployment.deploymentId, displayName: 'Customer app', status: 'ACTIVE', releaseId: 'release-prod', ready: true }
const release = { id: 'release-prod', deploymentId: deployment.deploymentId, targetProfileId: 'prod', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE', errorMessage: null, createdAt: '2026-11-15T10:00:00Z' }
const state = (changes = {}, apps = [], releases = [], evidence = 'ready', selectedProfiles = profiles, appsAvailable = true) =>
  model.brainLibraryState({ ...deployment, ...changes }, apps, releases, selectedProfiles, evidence, appsAvailable)

test('release chronology handles timestamps with and without fractional seconds', () => {
  const later = { ...release, id: 'later', createdAt: '2026-11-15T10:00:00.500Z' }
  assert.equal(model.brainLibraryLatestRelease([release, later], deployment.deploymentId, 'prod').id, 'later')
})

test('configuration-only and draft are drafts; saved versions are not live or drafts', () => {
  for (const status of ['DRAFT', 'CONFIGURATION_ONLY']) {
    assert.equal(state({ status }).label, 'Draft')
    assert.equal(state({ status }).draft, true)
  }
  const saved = state({ status: 'VERSION_PUBLISHED', activeVersionId: 'v1' })
  assert.equal(saved.label, 'Version saved')
  assert.equal(saved.live, false)
  assert.equal(saved.draft, false)
  assert.equal(saved.liveLabel, 'Not live')
})

test('a verified active production release is live for standalone/API Brains without an Engage app or original offer', () => {
  assert.equal(state({ status: 'ACTIVE', offerId: 'offer-no-longer-available' }, [], [release]).live, true)
  assert.equal(state({ status: 'ACTIVE' }, [app], [release]).live, true)
  assert.equal(state({ status: 'ACTIVE' }, [app], [release]).liveLabel, 'Production verified')
  for (const changed of [{ status: 'DISABLED' }, { ready: false }, { releaseId: null }, { deploymentId: 'foreign' }]) {
    assert.equal(state({}, [{ ...app, ...changed }], [release]).live, true, 'App state must not hide a verified Brain runtime')
    assert.equal(model.brainLibraryAppState({ ...app, ...changed }, [release], profiles, 'ready', 'ready').live, false)
  }
  for (const changed of [{ deploymentId: 'foreign' }, { targetProfileId: 'stage' },
    { provisioningStatus: 'DELETED' }, { status: 'FAILED' }, { verificationStatus: 'FAILED' }, { verificationStatus: 'NOT_RUN' }, { errorMessage: 'Failed' }]) {
    assert.equal(state({}, [app], [{ ...release, ...changed }]).live, false, JSON.stringify(changed))
  }
  assert.equal(state({}, [], [release], 'ready', profiles, false).live, true, 'Unavailable app summaries must not erase runtime evidence')
})

test('a ready app assigned a failed or missing release is not a live experience, even when another production release is healthy', () => {
  const failed = { ...release, id: 'assigned-failed', status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED', createdAt: '2026-11-15T09:00:00Z' }
  const assigned = { ...app, releaseId: failed.id }
  assert.equal(state({}, [assigned], [release, failed]).live, true)
  assert.deepEqual(model.brainLibraryAppState(assigned, [release, failed], profiles, 'ready', 'ready'), { live: false, label: 'Experience not live' })
  assert.deepEqual(model.brainLibraryAppState(assigned, [release], profiles, 'ready', 'ready'), { live: false, label: 'Experience status unavailable' })
  assert.equal(model.brainLibraryAppState(app, [{ ...release, status: 'UNKNOWN' }], profiles, 'ready', 'ready').label, 'Experience status unavailable')
  assert.equal(model.brainLibraryAppState(app, [release], profiles, 'ready', 'ready').live, true)
})

test('latest production record is selected before checking verification or activity, never an older passing record', () => {
  for (const changed of [
    { status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED' },
    { provisioningStatus: 'DELETED' }, { provisioningStatus: 'STOPPED' }, { provisioningStatus: 'INACTIVE' },
    { status: 'FAILED' }, { status: 'PROVISIONING' }, { status: 'UNKNOWN' },
  ]) {
    const newer = { ...release, id: 'newer', createdAt: '2026-11-15T11:00:00Z', ...changed }
    for (const records of [[release, newer], [newer, release]]) {
      const original = [...records]
      assert.equal(model.brainLibraryLatestRelease(records, deployment.deploymentId, 'prod'), newer)
      assert.equal(state({ status: 'ACTIVE' }, [], records).live, false, JSON.stringify(changed))
      assert.notEqual(state({ status: 'ACTIVE' }, [], records).label, 'Production verified')
      assert.deepEqual(records, original, 'Selection must not reorder caller-owned history')
    }
  }
})

test('latest-record selection is isolated to the exact Brain and actual production target', () => {
  const inactive = { ...release, id: 'inactive', createdAt: '2026-11-15T11:00:00Z', provisioningStatus: 'DELETED' }
  const otherTarget = { ...release, id: 'other-target', targetProfileId: 'prod-two', createdAt: '2026-11-15T09:00:00Z' }
  const staging = { ...release, id: 'staging', targetProfileId: 'stage', createdAt: '2026-11-15T12:00:00Z' }
  const foreign = { ...inactive, id: 'foreign', deploymentId: 'foreign', createdAt: '2026-11-15T13:00:00Z' }
  const records = [foreign, otherTarget, release, staging, inactive]
  assert.equal(model.brainLibraryLatestRelease(records, deployment.deploymentId, 'prod'), inactive)
  assert.equal(model.brainLibraryLatestRelease(records, deployment.deploymentId, 'missing'), null)
  assert.equal(state({}, [], records).live, false)
  const actualProfiles = [...profiles, { id: 'prod-two', environmentName: 'production', active: true }]
  assert.equal(state({}, [], records, 'ready', actualProfiles).label, 'Production verified')
})

test('Engage continues to use its exact assigned record rather than latest-target selection', () => {
  const newer = { ...release, id: 'newer-failed', status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED', createdAt: '2026-11-15T11:00:00Z' }
  assert.equal(state({}, [app], [release, newer]).live, false)
  assert.equal(model.brainLibraryAppState(app, [release, newer], profiles, 'ready', 'ready').live, true)
  const assigned = { ...app, releaseId: newer.id }
  const laterPass = { ...release, id: 'later-pass', createdAt: '2026-11-15T12:00:00Z' }
  assert.equal(state({}, [assigned], [newer, laterPass]).live, true)
  assert.equal(model.brainLibraryAppState(assigned, [newer, laterPass], profiles, 'ready', 'ready').live, false)
  assert.equal(model.brainLibraryAppState(assigned, [laterPass], profiles, 'ready', 'ready').label, 'Experience status unavailable')
})

test('unknown release, target and failed/loading reads stay unavailable, never empty or successful', () => {
  for (const evidence of ['loading', 'error', 'unavailable']) {
    const result = state({}, [app], [release], evidence)
    assert.equal(result.live, false)
    assert.equal(result.liveLabel, evidence === 'loading' ? 'Loading live status' : evidence === 'error' ? 'Live status could not be loaded' : 'Live status unavailable')
    assert.equal(result.liveEvidence, evidence)
  }
  assert.equal(state({}, [app], []).liveLabel, 'Not live', 'An absent Brain release is separate from an unavailable app assignment')
  assert.equal(model.brainLibraryAppState(app, [], profiles, 'ready', 'ready').label, 'Experience status unavailable')
  assert.equal(model.brainLibraryState(deployment, [app], [release], undefined, 'ready').live, false)
  assert.equal(state({}, [app], [release], 'ready', []).liveLabel, 'Live status unavailable')
  assert.equal(state({}, [app], [{ ...release, status: 'UNKNOWN' }]).liveLabel, 'Live status unavailable')
  assert.equal(model.brainLibraryState(deployment, [app], [release], profiles, 'ready', true, 'error').liveLabel, 'Live status could not be loaded')
})

test('query evidence distinguishes disabled unknown, initial loading, failed reads and successful empty reads', () => {
  assert.equal(model.brainLibraryQueryEvidence(undefined), 'unavailable')
  assert.equal(model.brainLibraryQueryEvidence({ isError: false, isFetching: false }), 'unavailable')
  assert.equal(model.brainLibraryQueryEvidence({ isError: false, isFetching: true }), 'loading')
  assert.equal(model.brainLibraryQueryEvidence({ isError: true, isFetching: false, data: [release] }), 'error')
  assert.equal(model.brainLibraryQueryEvidence({ isError: false, isFetching: false, data: [] }), 'ready')
})

test('connection labels distinguish unavailable, inactive, unassigned and assigned apps', () => {
  assert.equal(state().connectionLabel, 'Engage not connected')
  assert.equal(state({}, [{ ...app, status: 'DISABLED' }]).connectionLabel, 'Engage inactive')
  assert.equal(state({}, [{ ...app, releaseId: null }]).connectionLabel, 'Engage not assigned')
  assert.equal(state({}, [app]).connectionLabel, 'Engage connected')
  assert.equal(state({}, [], [], 'ready', profiles, false).connectionLabel, 'Engage unavailable')
})

test('filters combine name search with actual draft and live state, not publication or assignment alone', () => {
  const rows = [
    { resource: model.deploymentResource(deployment), state: state() },
    { resource: { ...model.deploymentResource(deployment), id: 'saved', name: 'Saved Brain' }, state: state({ status: 'VERSION_PUBLISHED' }) },
    { resource: { ...model.deploymentResource(deployment), id: 'live', name: 'Live Support' }, state: state({}, [app], [release]) },
    { resource: { ...model.deploymentResource(deployment), id: 'unknown', name: 'Unknown Support' }, state: state({ status: 'ACTIVE' }, [app], [], 'unavailable') },
  ]
  assert.equal(model.filterBrainLibrary(rows, '', 'all').length, 4)
  assert.deepEqual(model.filterBrainLibrary(rows, '', 'drafts').map(row => row.resource.id), ['brain-owned'])
  assert.deepEqual(model.filterBrainLibrary(rows, '  SUPPORT ', 'live').map(row => row.resource.id), ['live'])
  assert.equal(model.filterBrainLibrary(rows, 'unknown', 'live').length, 0)
})

test('release evidence uses human labels while keeping unrecognized states unavailable', () => {
  assert.equal(model.brainLibraryReleaseLabel(release), 'Verified release')
  assert.equal(model.brainLibraryReleaseLabel({ ...release, verificationStatus: 'FAILED' }), 'Verification failed')
  assert.equal(model.brainLibraryReleaseLabel({ ...release, provisioningStatus: 'DELETED' }), 'Infrastructure inactive')
  assert.equal(model.brainLibraryReleaseLabel({ ...release, status: 'FUTURE_STATE' }), 'Release status unavailable')
})

const render = component => renderToStaticMarkup(component)
test('compact rows expose real filters, selection and unavailable live evidence without technical IDs', () => {
  const html = render(React.createElement(BrainLibrary, {
    rows: [{ resource: model.deploymentResource(deployment), state: state({}, [], [], 'unavailable') }],
    search: '', onSearch() {}, filter: 'all', onFilter() {}, selectedId: deployment.deploymentId, onInspect() {},
  }))
  for (const label of ['Search Smart Brains', 'All', 'Drafts', 'Live', 'Knowledge', 'Capabilities', 'Engage not connected', 'aria-pressed="true"']) assert.ok(html.includes(label), label)
  assert.ok(!html.includes('brain-owned'))
  const live = render(React.createElement(BrainLibrary, { rows: [{ resource: model.deploymentResource(deployment), state: state({}, [], [], 'unavailable') }],
    search: '', onSearch() {}, filter: 'live', onFilter() {}, onInspect() {} }))
  assert.ok(live.includes('Live status is unavailable'))
  assert.ok(live.includes('No confirmed live Brains'))
})

test('guide uses canonical scoped routes, one shared AI button and collapsed technical facts', () => {
  const html = render(React.createElement(BrainLibraryInspector, {
    resource: model.deploymentResource(deployment), state: state(), targetProfileId: 'prod', apps: [], releases: [], profiles,
    evidence: 'ready', profileEvidence: 'ready', resourcesAvailable: true, selectedReleaseId: null, notices: null,
    technicalDetails: React.createElement('div', null, 'private technical facts'), onBuild() {}, onClose() {}, onInspectApp() {},
  }))
  for (const label of ['Build with AI', 'Your next step', 'Add your documents', 'Connect your tools', 'Shape its answers', 'From setup to customers', 'Technical details', 'Versions &amp; updates']) assert.ok(html.includes(label), label)
  for (const path of ['knowledge/documents?targetProfileId=prod', 'capabilities?targetProfileId=prod', 'responses?targetProfileId=prod', 'try?targetProfileId=stage', 'updates?targetProfileId=prod']) assert.ok(html.includes(`/smart-brains/brain-owned/${path}`), path)
  assert.ok(html.includes('connectDeploymentId=brain-owned'))
  assert.equal((html.match(/Build with AI/g) || []).length, 1)
  assert.ok(/<details[^>]*>[\s\S]*<summary[^>]*>[\s\S]*Technical details[\s\S]*private technical facts/.test(html), 'Technical facts are inside the disclosure')
  assert.ok(!/<details[^>]* open/.test(html))
  assert.ok(!html.includes('role="tab"'))
})

test('guide never displays raw release IDs or substitutes an unavailable assigned release', () => {
  const html = render(React.createElement(BrainLibraryInspector, {
    resource: model.deploymentResource(deployment), state: state({}, [app], []), apps: [app], releases: [{ ...release, id: 'different' }], profiles,
    evidence: 'ready', profileEvidence: 'ready', resourcesAvailable: true, selectedReleaseId: null, notices: null, technicalDetails: null,
    onBuild() {}, onClose() {}, onInspectApp() {},
  }))
  assert.ok(html.includes('Assigned release unavailable'))
  assert.ok(!html.includes('Verified release'))
  assert.ok(!html.includes('release-prod'))
})

test('non-draft guidance never assumes missing knowledge, and an absent staging profile uses the normal staging setup route', () => {
  const saved = { ...deployment, status: 'VERSION_PUBLISHED' }
  const props = { resource: model.deploymentResource(saved), state: state({ status: saved.status }), targetProfileId: 'prod',
    apps: [], releases: [], profiles: [profiles[1]], evidence: 'ready', profileEvidence: 'ready', resourcesAvailable: true,
    selectedReleaseId: null, notices: null, technicalDetails: null, onBuild() {}, onClose() {}, onInspectApp() {} }
  const html = render(React.createElement(BrainLibraryInspector, props))
  assert.ok(html.includes('Keep improving your AI'))
  assert.ok(html.includes('What would you like to improve?'))
  assert.ok(!html.includes('Give your AI something to know.'))
  assert.ok(html.includes('/deployments?view=setup&amp;deploymentId=brain-owned&amp;step=staging'))
  assert.ok(!html.includes('/try?targetProfileId=prod'))
  for (const label of ['Add your documents', 'Connect your tools', 'Shape its answers']) assert.ok(html.includes(label), label)
})

test('quota compact rendering is opt-in and retains the original quota data, links and disclosure copy', async () => {
  const quota = { customerId: 'customer', used: 1, limit: 2, available: 1, environments: [{ deploymentId: 'brain', targetProfileId: 'prod', deploymentName: 'Support', environment: 'production', ownedResourcesPresent: true }] }
  const { CustomerEnvironmentQuota } = await load('../src/components/customer/CustomerEnvironmentQuota.tsx', {
    '../../deploymentTerminology': terminology, '../../api/platformApi': { fetchCustomerEnvironmentQuota() {} },
    '../../auth/PlatformAuthProvider': { usePlatformAuth: () => ({ session: { customerId: 'customer' } }) },
    'react-router-dom': router,
    '@tanstack/react-query': { useQuery: () => ({ data: quota, isFetching: false, refetch() {} }) },
  })
  const normal = render(React.createElement(CustomerEnvironmentQuota))
  const compact = render(React.createElement(CustomerEnvironmentQuota, { compact: true }))
  assert.ok(normal.includes('Infrastructure environments'))
  assert.ok(normal.includes('View environments in use'))
  assert.ok(compact.includes('1 / 2'))
  assert.ok(!compact.includes('Infrastructure environments'))
  for (const html of [normal, compact]) {
    assert.ok(html.includes('Stopped infrastructure counts until removed.'))
    assert.ok(html.includes('Refresh infrastructure allowance'))
    assert.ok(html.includes('/revisions?deploymentId=brain&amp;targetProfileId=prod'))
  }
})
