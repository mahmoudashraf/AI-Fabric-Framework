import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/customer/specialistAssistanceForm.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { emptySpecialistAssistance, readSpecialistAssistance, specialistAuthority, specialistAssistanceError,
  specialistBehaviorUpdate, compiledSpecialistAssistance } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)

function fixture() {
  const install = { id: 'install', deploymentId: 'dep', pluginId: 'plugin', pluginVersionId: 'version', pluginVersion: '1.0.0',
    pluginDisplayName: 'Account integration', status: 'ENABLED', entitlement: { entitledForCompilation: true } }
  const provenance = { marketplaceManaged: true, marketplaceInstallId: install.id, marketplacePluginId: install.pluginId, marketplacePluginVersion: install.pluginVersion }
  const draft = { deploymentId: 'dep', actionsConfig: { actions: [
    { name: 'get_account_status', accessMode: 'READ', ...provenance },
    { name: 'change_account_status', accessMode: 'WRITE', ...provenance },
    { name: 'manual_read', accessMode: 'READ' },
    { name: 'undeclared_read', accessMode: 'READ', ...provenance },
    { name: 'consult_specialist', accessMode: 'READ', ...provenance },
  ] }, knowledgeSourceConfig: { sources: [
    { id: 'account-guide', entityType: 'guide', displayName: 'Account guides', enabled: true, ...provenance },
    { id: 'private-guide', entityType: 'private', enabled: false, ...provenance },
    { id: 'manual-guide', entityType: 'manual', enabled: true },
  ] } }
  const version = { id: 'version', pluginId: 'plugin', version: '1.0.0', status: 'PUBLISHED', manifest: { contributions: {
    actions: [{ actionId: 'get_account_status', readOnly: true }, { actionId: 'change_account_status', accessMode: 'WRITE' },
      { actionId: 'consult_specialist', readOnly: true }],
    knowledgeSources: [{ id: 'account-guide', datasetRef: 'guides' }, { id: 'private-guide', entityType: 'private' }],
    datasets: [{ datasetId: 'guides', entityType: 'guide' }],
  } } }
  const area = { id: 'account', name: 'Account help', purpose: 'Explain the current account state.',
    instructions: 'Use approved information and state what is unknown.', vectorSpaces: [], readActions: ['get_account_status'] }
  const config = { ...emptySpecialistAssistance(), enabled: true, areas: [area] }
  return { install, draft, version, area, config }
}

test('the support capability is opt-in and reads dynamic bootstrap defaults without compiled metadata', () => {
  assert.equal(readSpecialistAssistance({ type: 'CONVERSATIONAL' }), null)
  assert.equal(readSpecialistAssistance({ specialistAssistance: { enabled: false } }).enabled, false)
  const { config } = fixture()
  const read = readSpecialistAssistance({ specialistAssistance: { ...config, compiled: { resources: [] }, contentHash: 'old' } })
  assert.deepEqual(read, config)
  assert.equal('compiled' in read, false)
  assert.equal('contentHash' in read, false)
  assert.throws(() => readSpecialistAssistance({ specialistAssistance: { enabled: true } }), /cannot be edited/)
})

test('knowledge and READ picks require owned enabled installs and exact published manifests', () => {
  const f = fixture(), authority = specialistAuthority('dep', f.draft, [f.install], [f.version])
  assert.deepEqual(authority.sources.map(item => item.id), ['guide'])
  assert.deepEqual(authority.readTools.map(item => item.id), ['get_account_status'])
  assert.deepEqual(specialistAuthority('dep', f.draft, [{ ...f.install, status: 'DISABLED' }], []), { sources: [], readTools: [] })
  assert.deepEqual(specialistAuthority('dep', f.draft, [{ ...f.install, entitlement: { entitledForCompilation: false } }], []), { sources: [], readTools: [] })
  assert.throws(() => specialistAuthority('other', f.draft, [f.install], [f.version]), /another deployment/)
  assert.throws(() => specialistAuthority('dep', f.draft, [{ ...f.install, deploymentId: 'other' }], [f.version]), /another deployment/)
  for (const patch of [{ status: 'DRAFT' }, { pluginId: 'foreign' }, { version: '2.0.0' }, { id: 'wrong-version' }]) {
    assert.throws(() => specialistAuthority('dep', f.draft, [f.install], [{ ...f.version, ...patch }]), /approved manifest/)
  }
})

test('changed provenance or dataset linkage cannot authorize a source or action', () => {
  for (const field of ['marketplaceManaged', 'marketplaceInstallId', 'marketplacePluginId', 'marketplacePluginVersion']) {
    const f = fixture()
    f.draft.actionsConfig.actions[0][field] = 'other'
    f.draft.knowledgeSourceConfig.sources[0][field] = 'other'
    const authority = specialistAuthority('dep', f.draft, [f.install], [f.version])
    assert.deepEqual(authority, { sources: [], readTools: [] })
  }
  const f = fixture()
  f.version.manifest.contributions.datasets[0].entityType = 'other'
  assert.deepEqual(specialistAuthority('dep', f.draft, [f.install], [f.version]).sources, [])
})

test('READ tools do not require a vector source or grounding-vector binding', () => {
  const f = fixture(), authority = specialistAuthority('dep', f.draft, [f.install], [f.version])
  assert.equal(specialistAssistanceError(f.config, authority), null)
  f.config.areas[0].vectorSpaces = ['uninstalled-space']
  assert.match(specialistAssistanceError(f.config, authority), /no longer available/)
  f.config.areas[0].vectorSpaces = []
  f.config.areas[0].readActions = ['change_account_status']
  assert.match(specialistAssistanceError(f.config, authority), /no longer available/)
})

test('draft updates preserve handover and unrelated behavior, and synchronize only the optional extension', () => {
  const { config } = fixture()
  const original = { type: 'CONVERSATIONAL', channelBindings: ['CHAT'], executionExtensions: ['CONVERSATION_HANDOVER'],
    conversationHandover: { enabled: true }, specialistBundles: [{ id: 'installed' }], otherOwnedSetting: 42 }
  const next = specialistBehaviorUpdate(original, config)
  assert.deepEqual(next.executionExtensions, ['CONVERSATION_HANDOVER', 'SPECIALIST_ASSISTANCE'])
  assert.deepEqual(next.conversationHandover, original.conversationHandover)
  assert.deepEqual(next.specialistBundles, original.specialistBundles)
  assert.equal(next.otherOwnedSetting, 42)
  assert.deepEqual(original.executionExtensions, ['CONVERSATION_HANDOVER'])
  const disabled = specialistBehaviorUpdate(next, { ...config, enabled: false })
  assert.deepEqual(disabled.executionExtensions, ['CONVERSATION_HANDOVER'])
  assert.throws(() => specialistBehaviorUpdate({ ...original, type: 'SMART_BRAIN' }, config), /Conversational/)
})

test('bounds and resolution references follow the compiler, not business-topic rules', () => {
  const f = fixture(), authority = specialistAuthority('dep', f.draft, [f.install], [f.version])
  f.config.resolutions = [{ id: 'investigate', name: 'Investigate', objective: 'Coordinate useful checks.', areaIds: ['account'], allowParallel: true }]
  assert.match(specialistAssistanceError(f.config, authority), /at least two/)
  f.config.resolutions[0].allowParallel = false
  assert.equal(specialistAssistanceError(f.config, authority), null)
  f.config.resolutions[0].areaIds = ['foreign']
  assert.match(specialistAssistanceError(f.config, authority), /configured support areas/)
  f.config.resolutions = []
  f.config.limits.maxParallelWorkers = 3
  assert.match(specialistAssistanceError(f.config, authority), /invocation limit/)
  f.config.enabled = false
  assert.equal(specialistAssistanceError(f.config, { sources: [], readTools: [] }), null)
})

test('immutable preview reads exact server refs, never generates them from editable area IDs', () => {
  const manifest = { deploymentId: 'dep', versionId: 'published', behaviorConfig: { specialistAssistance: { enabled: true,
    contentHash: 'sha256:server-value', compiled: { contractVersion: 'LOOMAI_SPECIALIST_ASSISTANCE_V1', resources: [],
      invocations: [{ id: 'account', name: 'Account help', targetType: 'SPECIALIST', targetRef: 'actual-server-resource@7' }] } } } }
  assert.equal(compiledSpecialistAssistance(manifest, 'dep', 'published').invocations[0].targetRef, 'actual-server-resource@7')
  assert.throws(() => compiledSpecialistAssistance(manifest, 'foreign', 'published'), /another deployment/)
  assert.throws(() => compiledSpecialistAssistance(manifest, 'dep', 'other'), /another deployment/)
  delete manifest.behaviorConfig.specialistAssistance.compiled.invocations[0].targetRef
  assert.throws(() => compiledSpecialistAssistance(manifest, 'dep', 'published'), /No references were inferred/)
})
