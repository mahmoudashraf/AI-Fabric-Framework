import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'
const source = await readFile(new URL('../src/components/customer/customerExternalIntegration.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(withCustomerSessionSupport(source), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { externalConnectionProfile: profile, requireFreshExternalAuthority: fresh, checkedExternalApproval: option, checkedExternalBinding: binding } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
function fixture() {
  const session = { authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION', actorId: 'owner', customerId: 'customer' }
  const offer = { id: 'offer', fingerprint: 'pin', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 },
    bindingSlots: [{ id: 'slot', kind: 'EXTERNAL_SERVICE', pluginId: 'plugin', supportedProfileIds: ['profile'] }],
    externalIntegrationProfiles: [{ id: 'profile', bindingSlotId: 'slot', pluginId: 'plugin', targetProfileIds: ['stage'] }],
    targets: [{ id: 'stage', capabilities: ['READ','BINDING_REPLACE','BINDING_REVOKE'] }] }
  const deployment = { deploymentId: 'dep', tenantId: 'tenant', offerId: 'offer', offerFingerprint: 'pin' }
  const version = { id: 'version', deploymentId: 'dep', status: 'PUBLISHED', configHash: 'hash' }
  const scope = { versionId: 'version', targetProfileId: 'stage', bindingSlotId: 'slot', selectedProfileId: 'profile' }
  const authority = { session, offer, deployment, version, scope }
  const api = { session: async () => session, offer: async () => offer, deployments: async () => [deployment], versions: async () => [version] }
  return { authority, api }
}
test('only current customer-owned published reviewed profiles are editable', () => {
  assert.equal(profile(fixture().authority).id, 'profile')
  for (const change of [a => { a.session.role = 'ADMIN' }, a => { a.session.authenticationMode = 'API_KEY' },
    a => { a.offer.entitlement.customerId = 'foreign' }, a => { a.version.deploymentId = 'foreign' },
    a => { a.version.status = 'DRAFT' }, a => { a.deployment.offerFingerprint = 'stale' },
    a => { a.scope.targetProfileId = 'unreviewed' }, a => { a.offer.externalIntegrationProfiles.push(a.offer.externalIntegrationProfiles[0]) },
    a => { a.offer.bindingSlots[0].pluginId = 'other' }]) {
    const { authority } = fixture(); change(authority); assert.throws(() => profile(authority))
  }
})
test('Supabase owners retain scoped external-integration access', async () => {
  const f = fixture(); f.authority.session.authenticationMode = 'SUPABASE'
  assert.equal(profile(f.authority).id, 'profile')
  await fresh(f.authority, f.api, () => true, 'BINDING_REPLACE')
})
test('fresh authority retains exact actor, entitlement, tenant, version and operation', async () => {
  const good = fixture(); await fresh(good.authority, good.api, () => true, 'BINDING_REPLACE')
  for (const change of [f => { f.api.session = async () => ({ ...f.authority.session, actorId: 'other' }) },
    f => { f.api.offer = async () => ({ ...f.authority.offer, entitlement: { ...f.authority.offer.entitlement, revision: 5 } }) },
    f => { f.api.deployments = async () => [{ ...f.authority.deployment, tenantId: 'other' }] },
    f => { f.api.versions = async () => [{ ...f.authority.version, configHash: 'different' }] },
    f => { f.api.offer = async () => ({ ...f.authority.offer, targets: [{ id: 'stage', capabilities: ['READ'] }] }) }]) {
    const f = fixture(); change(f); await assert.rejects(fresh(f.authority, f.api, () => true, 'BINDING_REPLACE'))
  }
})
test('unmounted or changed selection stops before a write', async () => {
  const f = fixture(); await assert.rejects(fresh(f.authority, f.api, () => false, 'BINDING_REPLACE'), /authority changed/)
})
test('foreign approval or binding projections cannot become success', () => {
  const { scope } = fixture().authority
  const approved = { ...scope, id: 'approved' }, bound = { ...scope, id: 'binding', deploymentId: 'dep', revision: 0 }
  assert.equal(option(scope, approved), approved); assert.equal(binding('dep', scope, bound), bound)
  for (const field of ['versionId','targetProfileId','bindingSlotId','selectedProfileId']) {
    assert.throws(() => option(scope, { ...approved, [field]: 'foreign' }))
    assert.throws(() => binding('dep', scope, { ...bound, [field]: 'foreign' }))
  }
  assert.throws(() => binding('other-dep', scope, bound))
  assert.throws(() => binding('dep', scope, { ...bound, revision: -1 }))
})
