import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/customerEngageConnection.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { matchingEngageConnections, connectableRelease, initialConnectionRelease } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const connection = { consumerId: 'one', customerId: 'customer', status: 'ACTIVE', boundDeploymentId: 'deployment', boundTargetProfileId: 'staging', boundReleaseId: null }
const release = { id: 'release', deploymentId: 'deployment', targetProfileId: 'staging', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', createdAt: '2026-10-28' }
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
