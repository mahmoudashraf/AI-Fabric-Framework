import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/brain/BrainActionReviewsPage.tsx', import.meta.url), 'utf8')
const ast = ts.createSourceFile('BrainActionReviewsPage.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const helper = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'isReviewEnvironmentEligible')
assert.ok(helper)
const compiled = ts.transpileModule(`${helper.getText(ast)}\nexports.eligible = isReviewEnvironmentEligible;`, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText
const helpers = {}
new Function('exports', compiled)(helpers)
const { eligible } = helpers

const profile = { id: 'staging-a', active: true }
const release = { id: 'release-a', deploymentId: 'brain-a', targetProfileId: profile.id, provisioningStatus: 'ACTIVE', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED' }
const target = { deploymentId: 'brain-a', targetProfileId: profile.id, releaseId: release.id }
const check = (p = profile, r = release, t = target, requested = null) => eligible('brain-a', profile.id, p, r, t, requested)

test('review eligibility retains the active target plus ACTIVE/APPLIED_VERIFIED/PASSED release contract', () => {
  assert.equal(check(), true)
  assert.equal(check(profile, release, target, release.id), true)
  assert.equal(check({ ...profile, active: false }), false)
  for (const provisioningStatus of ['PROVISIONING', 'STOPPED', 'DELETED']) {
    assert.equal(check(profile, { ...release, provisioningStatus }), false)
  }
  for (const status of ['VERIFYING', 'DRAFT', 'APPLIED_VERIFICATION_FAILED']) {
    assert.equal(check(profile, { ...release, status }), false)
  }
  for (const verificationStatus of ['PENDING', 'FAILED', 'UNKNOWN']) {
    assert.equal(check(profile, { ...release, verificationStatus }), false)
  }
})

test('every identity is exact; missing/foreign Brain, profile, target and release cannot substitute', () => {
  for (const p of [null, { ...profile, id: 'production' }]) assert.equal(check(p), false)
  for (const r of [null, { ...release, deploymentId: 'brain-b' }, { ...release, targetProfileId: 'production' }, { ...release, id: 'release-b' }]) {
    assert.equal(check(profile, r), false)
  }
  for (const t of [null, { ...target, deploymentId: 'brain-b' }, { ...target, targetProfileId: 'production' }, { ...target, releaseId: 'release-b' }]) {
    assert.equal(check(profile, release, t), false)
  }
  assert.equal(eligible('', profile.id, profile, release, target, null), false)
  assert.equal(eligible('brain-a', '', profile, release, target, null), false)
})

test('an explicit release link is not silently moved to the current release', () => {
  assert.equal(check(profile, release, target, 'historical-release'), false)
  assert.equal(check(profile, release, target, ''), false)
  assert.equal(check(profile, release, target, release.id), true)
})

test('wrapper uses the shared Brain context, one header and the existing review controller', () => {
  assert.ok(source.includes('useBrainWorkspace()'))
  assert.ok(source.includes('<BrainPageHeader title="Action reviews"'))
  assert.ok(source.includes('deploymentId={brainId}'))
  assert.ok(source.includes('targetProfileId={targetProfileId} releaseId={release!.id} workspaceView'))
  assert.ok(source.includes('key={`${brainId}:${targetProfileId}:${release!.id}`}'))
  assert.ok(source.includes("new URLSearchParams(location.search).get('releaseId')"))
  assert.ok(!source.includes('useQuery'))
  assert.ok(!source.includes('fetch'))
  assert.ok(!source.includes('<TextField'))
})

test('live operations register busy shell guards, without draft or publishing actions', () => {
  assert.ok(source.includes('onBusyChange={setBusy}'))
  assert.ok(source.includes("useBrainEditor({ dirty: false, busy, message: 'Live action reviews for this environment.' })"))
  assert.ok(!source.includes('save:'))
  assert.ok(!source.includes('discard:'))
  assert.ok(!source.includes('Save draft'))
  assert.ok(!source.includes('Publish'))
})
