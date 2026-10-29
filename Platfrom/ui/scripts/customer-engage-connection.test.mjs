import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/customerEngageConnection.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { matchingEngageConnections, connectableRelease, initialConnectionRelease, initialConnectionTarget,
  connectionEnvironments, connectionEnvironmentSelection } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const connection = { consumerId: 'one', customerId: 'customer', status: 'ACTIVE', boundDeploymentId: 'deployment', boundTargetProfileId: 'staging', boundReleaseId: null }
const release = { id: 'release', deploymentId: 'deployment', targetProfileId: 'staging', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', createdAt: '2026-10-28' }
test('target selection preserves current intent, then explicit bootstrap intent, never another environment', () => {
  const targets = [{ id: 'staging', active: true }, { id: 'production', active: true }]
  assert.equal(initialConnectionTarget(targets, 'production', 'staging'), 'production')
  assert.equal(initialConnectionTarget(targets, null, 'staging'), 'staging')
  assert.equal(initialConnectionTarget(targets), '')
  assert.equal(initialConnectionTarget([targets[0]], 'production', 'staging'), '')
  assert.equal(initialConnectionTarget([targets[0]], null, 'missing'), '')
  assert.equal(initialConnectionTarget([targets[0]]), 'staging')
  assert.equal(initialConnectionTarget([{ id: 'staging', active: false }], null, 'staging'), '')
})
test('connection reuse is exact, owned and active; aliases remain explicit', () => {
  const all = [connection, { ...connection, consumerId: 'two' }, { ...connection, customerId: 'foreign' },
    { ...connection, boundDeploymentId: 'other' }, { ...connection, boundTargetProfileId: 'production' },
    { ...connection, boundReleaseId: 'release' }, { ...connection, status: 'DISABLED' }]
  assert.deepEqual(matchingEngageConnections(all, 'customer', 'deployment', 'staging', null).map(item => item.consumerId), ['one', 'two'])
  assert.equal(matchingEngageConnections(all, 'customer', 'deployment', 'production', null).length, 1)
  assert.equal(all[0].boundReleaseId, null)
})
test('only applied and verified releases connect; newer failures are not hidden by an older release', () => {
  assert.equal(connectableRelease(release), true)
  assert.equal(connectableRelease({ ...release, verificationStatus: 'FAILED' }), false)
  assert.equal(initialConnectionRelease([release], 'staging'), 'release')
  assert.equal(initialConnectionRelease([release, { ...release, id: 'new', createdAt: '2026-10-29', status: 'FAILED' }], 'staging'), '')
  assert.equal(initialConnectionRelease([release], 'production'), '')
})
test('current exact release is retained, including visible unverified state, never replaced silently', () => {
  const current = { ...connection, boundReleaseId: 'current' }
  assert.equal(initialConnectionRelease([release], 'staging', current), '')
  assert.equal(initialConnectionRelease([release, { ...release, id: 'current', status: 'FAILED' }], 'staging', current), 'current')
})

const target = (id, environmentName, sourceStrategy = 'IMAGE_SOURCE', extra = {}) => ({
  id, environmentName, sourceStrategy, providerType: 'COOLIFY', active: true, defaultForRuntime: false, ...extra,
})
const profiles = [target('stage-git', 'staging', 'GIT_SOURCE'), target('stage-image', 'staging'),
  target('prod-git', 'production', 'GIT_SOURCE'), target('prod-image', 'production')]
test('environment choices group technical profiles and exclude inactive targets', () => {
  assert.deepEqual(connectionEnvironments([...profiles, target('old', 'retired', 'GIT_SOURCE', { active: false })]),
    [{ name: 'production', label: 'Production' }, { name: 'staging', label: 'Staging' }])
  assert.deepEqual(connectionEnvironments([]), [])
  assert.deepEqual(connectionEnvironments([target('custom', 'test')]), [{ name: 'test', label: 'test' }])
})
test('environment draft selection uses the exact bootstrap or its compatible target, independent of profile order', () => {
  for (const targets of [profiles, [...profiles].reverse()]) {
    assert.deepEqual(connectionEnvironmentSelection('staging', targets, [], undefined, 'stage-image'),
      { targetId: 'stage-image', releaseId: '' })
    assert.deepEqual(connectionEnvironmentSelection('production', targets, [], undefined, 'stage-image'),
      { targetId: 'prod-image', releaseId: '' })
    assert.deepEqual(connectionEnvironmentSelection('production', targets, [], undefined, 'stage-git'),
      { targetId: 'prod-git', releaseId: '' })
  }
})
test('selecting an environment preserves the existing exact connection and release', () => {
  const current = { ...connection, boundTargetProfileId: 'prod-git', boundReleaseId: 'original' }
  const all = [{ ...release, id: 'original', targetProfileId: 'prod-git' },
    { ...release, id: 'new-image', targetProfileId: 'prod-image', createdAt: '2026-10-29' }]
  assert.deepEqual(connectionEnvironmentSelection('production', profiles, all, current, 'stage-image'),
    { targetId: 'prod-git', releaseId: 'original' })
})
test('environment releases determine their exact target, without hiding a newer failure behind an older success', () => {
  const all = [{ ...release, targetProfileId: 'prod-git' },
    { ...release, id: 'new-image', targetProfileId: 'prod-image', createdAt: '2026-10-29' }]
  assert.deepEqual(connectionEnvironmentSelection('production', profiles, all, undefined, 'stage-git'),
    { targetId: 'prod-image', releaseId: 'new-image' })
  all[1].status = 'FAILED'
  assert.deepEqual(connectionEnvironmentSelection('production', profiles, all, undefined, 'stage-git'),
    { targetId: 'prod-image', releaseId: '' })
})
test('unauthorized, inactive and other-environment releases cannot supply a connection target', () => {
  const targets = [...profiles.filter(item => item.id !== 'prod-image'),
    target('prod-image', 'production', 'IMAGE_SOURCE', { active: false })]
  const all = [{ ...release, targetProfileId: 'prod-image' }, { ...release, targetProfileId: 'foreign' },
    { ...release, targetProfileId: 'stage-image' }]
  assert.deepEqual(connectionEnvironmentSelection('production', targets, all, undefined, 'stage-image'),
    { targetId: '', releaseId: '' })
  assert.deepEqual(connectionEnvironmentSelection('missing', profiles, all), { targetId: '', releaseId: '' })
})
test('ambiguous drafts require configuration, never the first target in the list', () => {
  assert.deepEqual(connectionEnvironmentSelection('production', profiles, []), { targetId: '', releaseId: '' })
  const ambiguous = [...profiles, target('prod-image-2', 'production')]
  assert.deepEqual(connectionEnvironmentSelection('production', ambiguous, [], undefined, 'stage-image'),
    { targetId: '', releaseId: '' })
  ambiguous[4].defaultForRuntime = true
  assert.deepEqual(connectionEnvironmentSelection('production', ambiguous, [], undefined, 'stage-image'),
    { targetId: 'prod-image-2', releaseId: '' })
})
