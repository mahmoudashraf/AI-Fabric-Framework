import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'
const source = await readFile(new URL('../src/components/customer/customerOperatorPlacement.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(withCustomerSessionSupport(source), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { prepareCustomerOperatorPlacement: prepare } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
function fixture() {
  const session = { authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION', actorId: 'owner', customerId: 'customer' }
  const target = { deploymentId: 'dep', targetProfileId: 'prod', releaseId: 'release' }
  const calls = []
  const api = { session: async () => { calls.push('session'); return session },
    deployments: async () => [{ deploymentId: 'dep', tenantId: 'tenant', offerId: 'offer', offerFingerprint: 'pin' }],
    offer: async () => ({ id: 'offer', fingerprint: 'pin', entitlement: { customerId: 'customer', status: 'ACTIVE' },
      targets: [{ id: 'prod', capabilities: ['INBOX_OPERATOR_ENROLL','INBOX_OPERATOR_ROTATE','INBOX_OPERATOR_DELIVER'] }] }),
    target: async () => ({ ...target, conversationHandoverConfigured: true }) }
  return { session, target, calls, api }
}
test('operator grants prepare only the explicitly owned offer and target', async () => {
  for (const operation of ['INBOX_OPERATOR_ENROLL','INBOX_OPERATOR_ROTATE','INBOX_OPERATOR_DELIVER']) {
    const f = fixture(); await prepare(f.target, f.session, operation, f.api, () => true)
    assert.deepEqual(f.calls, ['session', 'session'])
  }
})
test('foreign actor, target, stale offer and revoked capability stop before an operator command', async () => {
  for (const change of [f => { f.api.session = async () => ({ ...f.session, actorId: 'foreign' }) },
    f => { f.api.deployments = async () => [] },
    f => { f.api.target = async () => ({ ...f.target, releaseId: 'other', conversationHandoverConfigured: true }) },
    f => { f.api.offer = async () => ({ fingerprint: 'changed', entitlement: { customerId: 'customer', status: 'ACTIVE' }, targets: [] }) }]) {
    const f = fixture(); change(f)
    await assert.rejects(prepare(f.target, f.session, 'INBOX_OPERATOR_ENROLL', f.api, () => true))
    assert.ok(f.calls.length < 2)
  }
})
test('failed account read or selection change never proceeds to grant', async () => {
  const f = fixture(); f.api.session = async () => { throw new Error('unreachable') }
  await assert.rejects(prepare(f.target, f.session, 'INBOX_OPERATOR_ENROLL', f.api, () => true), /unreachable/)
  const g = fixture(); let current = true
  const target = g.api.target
  g.api.target = async input => { const value = await target(input); current = false; return value }
  await assert.rejects(prepare(g.target, g.session, 'INBOX_OPERATOR_ENROLL', g.api, () => current), /authorize|changed/)
})
test('account revocation after target read is observed before the operator command', async () => {
  const f = fixture(); let reads = 0
  f.api.session = async () => { reads++; return { ...f.session, authenticated: reads === 1 } }
  await assert.rejects(prepare(f.target, f.session, 'INBOX_OPERATOR_DELIVER', f.api, () => true), /changed/)
  assert.equal(reads, 2)
})
