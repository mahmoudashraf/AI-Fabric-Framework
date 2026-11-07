import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
const profiles = {}
new Function('require', 'exports', ts.transpileModule(await readFile(new URL('../src/aiBehaviourProfiles.ts', import.meta.url), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
}).outputText)(require, profiles)
const source = await readFile(new URL('../src/brain/BrainUpdatesPage.tsx', import.meta.url), 'utf8')
const revisions = await readFile(new URL('../src/pages/RevisionsPage.tsx', import.meta.url), 'utf8')
const fields = ['actionsConfig', 'entityConfig', 'routingConfig', 'providerConfig', 'securityConfig',
  'promptConfig', 'knowledgeSourceConfig', 'shellConfig', 'marketplaceDatasetConfig', 'behaviorConfig']
const draft = { id: 'draft-1', deploymentId: 'brain-1', updatedAt: '2026-11-07T12:00:00Z', revisionNumber: 13,
  ...Object.fromEntries(fields.map(key => [key, {}])), promptConfig: { tone: 'Consultative', prompt: 'DO_NOT_PROJECT_THIS_VALUE' } }
const manifest = { deploymentId: 'brain-1', versionId: 'v12', ...Object.fromEntries(fields.map(key => [key, {}])) }
const release = { id: 'release-12', deploymentId: 'brain-1', deploymentVersionId: 'v12', targetProfileId: 'target-1',
  status: 'APPLIED_VERIFIED', provisioningStatus: 'ACTIVE', verificationStatus: 'PASSED', createdAt: '2026-11-06T12:00:00Z' }
let canEdit = true
let canOperate = true
let registered
const queryData = {
  'deployment-draft': draft, 'deployment-config-diff-center': undefined,
  'deployment-versions': [{ id: 'v12', versionLabel: 'v12' }], 'deployment-releases': [release],
  'deployment-verification-runs': [], 'brain-updates-target': { runtimeBaseUrl: 'https://runtime.example.test' },
  'deployment-version-manifest': manifest,
}
const api = new Proxy({}, { get: () => () => { throw new Error('No real API is permitted in focused tests') } })
const dependencies = {
  '../api/platformApi': api,
  '../aiBehaviourProfiles': profiles,
  '../auth/PlatformAuthProvider': { usePlatformAuth: () => ({ session: { role: 'CUSTOMER_ADMIN' } }) },
  '../pages/RevisionsPage': { RevisionsPage: () => null },
  '../workspace/DeploymentWorkspaceContext': { useDeploymentWorkspace: () => ({ selectedDeploymentId: 'brain-1', workspace: {
    access: { canEdit, canOperate }, lifecycle: { savedDraftMatchesLatestPublished: false }, draft, deployment: { name: 'Test Brain' },
  } }) },
  './BrainWorkspaceContext': { useBrainWorkspace: () => ({ brainId: 'brain-1', targetProfileId: 'target-1',
    targetProfile: { id: 'target-1', active: true, environmentName: 'Staging', name: 'London staging' },
    release, target: { deploymentId: 'brain-1', targetProfileId: 'target-1', releaseId: 'release-12' }, href: section => `/smart-brains/brain-1/${section}` }), useBrainEditor: state => { registered = state } },
  './BrainCustomerReleaseTasks': { BrainCustomerReleaseTasks: () => null },
  './brainNavigation': { brainPath: (id, section, target) => `/smart-brains/${id}/${section}?targetProfileId=${target}` },
  'react-router-dom': { useNavigate: () => () => {}, useLocation: () => ({ pathname: '/smart-brains/brain-1/updates', search: '?targetProfileId=target-1' }),
    Link: ({ to, children }) => React.createElement('a', { href: to }, children) },
  '@tanstack/react-query': {
    useQuery: options => ({ data: queryData[options.queryKey[0]], isSuccess: true, isLoading: false, isError: false }),
    useQueryClient: () => ({ invalidateQueries: async () => {} }),
    useMutation: () => ({ isPending: false, isSuccess: false, isError: false }),
  },
}
const exports = {}
new Function('require', 'exports', ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
} }).outputText)(name => name in dependencies ? dependencies[name] : require(name), exports)

test('compares all ten sections with the selected target immutable manifest', () => {
  const changes = exports.buildUpdateChanges(draft, manifest, 'v12')
  assert.equal(changes.length, 10)
  assert.deepEqual(changes.filter(item => item.changed).map(item => item.key), ['promptConfig'])
  for (const key of ['knowledgeSourceConfig', 'shellConfig', 'marketplaceDatasetConfig', 'behaviorConfig']) {
    const changedDraft = { ...draft, [key]: { enabled: true } }
    assert.equal(exports.buildUpdateChanges(changedDraft, manifest, 'v12').find(item => item.key === key).changed, true)
  }
})

test('does not relabel a deployment-wide diff as a different target comparison', () => {
  const diff = { live: { referenceId: 'another-target-version' }, draft: { referenceId: draft.id, updatedAt: draft.updatedAt },
    sections: [{ key: 'prompts', draftMatchesLive: true, liveValue: 'WRONG_TARGET', draftValue: 'WRONG_DRAFT' }] }
  const changes = exports.buildUpdateChanges(draft, manifest, 'v12', diff)
  assert.equal(changes.find(item => item.key === 'promptConfig').changed, true)
  assert.ok(!JSON.stringify(changes).includes('WRONG_TARGET'))
})

test('uses canonical server summary only for matching target version and saved draft stamp', () => {
  const diff = { live: { referenceId: 'v12' }, draft: { referenceId: draft.id, updatedAt: draft.updatedAt },
    sections: [{ key: 'entities', draftMatchesLive: true, liveValue: '3 collections', draftValue: '3 collections' }] }
  const changes = exports.buildUpdateChanges(draft, { ...manifest, entityConfig: { compiled: true } }, 'v12', diff)
  assert.equal(changes.find(item => item.key === 'entityConfig').changed, false)
  assert.equal(changes.find(item => item.key === 'entityConfig').current, '3 collections')
})

test('missing or wrong-version manifests remain unknown, not an empty successful comparison', () => {
  for (const missing of [undefined, { deploymentId: 'other', versionId: 'v12' }, { ...manifest, versionId: 'v11' }]) {
    assert.ok(exports.buildUpdateChanges(draft, missing, 'v12').every(item => item.changed === null))
  }
  assert.ok(exports.buildUpdateChanges(draft, undefined, null).every(item => item.changed === true))
})

test('canonical comparison ignores object order but preserves array order and unknown fields', () => {
  assert.equal(exports.stableConfig({ b: 2, a: { z: 1, y: 3 } }), exports.stableConfig({ a: { y: 3, z: 1 }, b: 2 }))
  assert.notEqual(exports.stableConfig([1, 2]), exports.stableConfig([2, 1]))
  assert.notEqual(exports.stableConfig(null), exports.stableConfig(undefined))
  assert.notEqual(exports.draftStamp(draft), exports.draftStamp({ ...draft, shellConfig: { unknownSetting: true } }))
})

test('response choices use structured saved metadata and actual catalogue labels, not prompt text', () => {
  const catalogue = { groups: [{ key: 'tone', label: 'Tone', options: [
    { id: 'professional', label: 'Professional' }, { id: 'consultative', label: 'Consultative' },
  ] }] }
  const applied = { ...manifest, promptConfig: { aiBehaviour: { schemaVersion: 'loomai-ai-behaviour-v1', tone: { presetId: 'professional' } } } }
  const saved = { ...draft, promptConfig: { aiBehaviour: { schemaVersion: 'loomai-ai-behaviour-v1', tone: { presetId: 'consultative' } } } }
  const changes = exports.buildUpdateChanges(saved, applied, 'v12', undefined, catalogue)
  assert.equal(changes.find(item => item.key === 'promptConfig').current, 'Tone: Professional')
  assert.equal(changes.find(item => item.key === 'promptConfig').next, 'Tone: Consultative')
})

test('focused details expose field paths, never stored values or secrets', () => {
  const details = exports.configurationFieldChanges({ secretRef: 'old-secret', nested: { a: 1 }, removed: true }, { secretRef: 'new-secret', nested: { a: 2 }, added: true })
  assert.deepEqual(details.map(item => [item.path, item.change]), [['$.added', 'Added'], ['$.nested.a', 'Changed'], ['$.removed', 'Removed'], ['$.secretRef', 'Changed']])
  assert.ok(!JSON.stringify(details).includes('new-secret'))
})

test('release polling preserves pending provider confirmation and terminal failures', () => {
  assert.equal(exports.updatesReleaseInProgress(release), false)
  for (const status of ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING']) assert.equal(exports.updatesReleaseInProgress({ ...release, status }), true)
  assert.equal(exports.updatesReleaseInProgress({ ...release, status: 'PROVISIONING', provisioningStatus: 'AWAITING_CONFIRMATION' }), true)
  assert.equal(exports.updatesReleaseInProgress({ ...release, status: 'FAILED', provisioningStatus: 'FAILED', verificationStatus: 'NOT_RUN' }), false)
})

test('page mounts read-only queries, registers one shared footer and does not project raw config', () => {
  const html = renderToStaticMarkup(React.createElement(exports.BrainUpdatesPage))
  assert.match(html, /Review and publish changes/)
  assert.match(html, /Compared with Staging v12/)
  assert.ok(!html.includes('DO_NOT_PROJECT_THIS_VALUE'))
  assert.equal(registered.primaryLabel, 'Publish & update Staging')
  assert.equal(registered.secondaryLabel, 'Back to editing')
  assert.equal(registered.dirty, false)
  assert.equal(registered.canSave, true)
  assert.equal(typeof registered.save, 'function')
})

test('viewer has no publication or update command registered', () => {
  canEdit = false; canOperate = false
  const html = renderToStaticMarkup(React.createElement(exports.BrainUpdatesPage))
  assert.match(html, /Read-only access/)
  assert.equal(registered.save, undefined)
  assert.equal(registered.canSave, false)
  canEdit = true; canOperate = true
})

test('advanced existing controls remain scoped, permission checked, and preference writes disabled', () => {
  assert.match(revisions, /scope\?\.brainId \?\? legacyWorkspace.selectedDeploymentId/)
  assert.match(revisions, /enabled: !scope/)
  assert.match(revisions, /if \(scope \|\| !preferencesQuery.isSuccess/)
  assert.match(revisions, /release.targetProfileId === scope.targetProfileId/)
  assert.match(revisions, /window.confirm\(`Update/)
  for (const control of ['RESTORE_IN_PLACE', 'Publish', 'DeploymentTemplateResetButton', 'CustomerVerifiedReleaseAssignments', 'canAdmin', 'canOperate']) assert.ok(revisions.includes(control))
})
