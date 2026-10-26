import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'

const source = await readFile(new URL('../src/components/customer/customerDocumentStorageScope.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(withCustomerSessionSupport(source), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { customerDocumentStorageScope: scope, requireFreshCustomerDocumentStorageScope: fresh, hasReviewedDocumentStorageInstall } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
function fixture() {
  const session = { authenticated: true, actorId: 'owner', customerId: 'customer', role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION' }
  const deployment = { deploymentId: 'dep', tenantId: 'tenant', offerId: 'offer', offerFingerprint: 'pin', authorizationEntitlementRevision: 2 }
  const selectedDeployment = { id: 'dep', binding: { customerId: 'customer', tenantId: 'tenant' } }
  const offer = { id: 'offer', fingerprint: 'pin', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 7 },
    targets: [{ id: 'stage', capabilities: ['BINDING_REPLACE', 'BINDING_REVOKE'] }],
    bindingSlots: [{ id: 'slot', kind: 'CUSTOMER_S3', supportedProfileIds: ['reviewed'] }] }
  const input = { session, deployment, selectedDeployment, selectedDeploymentId: 'dep', offer, targetProfileId: 'stage',
    bindingSlotId: 'slot', operation: 'BINDING_REPLACE', selectedProfileId: 'reviewed' }
  return { input, api: { session: async () => session, deployments: async () => [deployment], offer: async () => offer } }
}

test('reviewed optional document install may be configured before its first binding enables it', () => {
  const offer = { bindingSlots: [{ kind: 'CUSTOMER_S3', pluginId: 'documents', supportedProfileIds: ['s3'] }] }
  assert.equal(hasReviewedDocumentStorageInstall(offer, [{ pluginId: 'documents', status: 'DISABLED' }]), true)
  assert.equal(hasReviewedDocumentStorageInstall(offer, [{ pluginId: 'unreviewed', status: 'ENABLED' }]), false)
  assert.equal(hasReviewedDocumentStorageInstall(null, [{ pluginId: 'documents' }]), false)
  assert.equal(hasReviewedDocumentStorageInstall({ bindingSlots: [{ ...offer.bindingSlots[0], supportedProfileIds: [] }] }, [{ pluginId: 'documents' }]), false)
})

test('bind and revoke capture current reviewed entitlement, never historical bootstrap revision', async () => {
  const f = fixture()
  for (const operation of ['BINDING_REPLACE', 'BINDING_REVOKE']) {
    const captured = scope({ ...f.input, operation })
    assert.equal(captured.entitlementRevision, 7)
    await fresh(captured, f.api, () => true)
  }
})

test('Supabase customer document scope preserves its real authentication mode and ownership checks', async () => {
  const f = fixture(); f.input.session.authenticationMode = 'SUPABASE'
  const captured = scope(f.input)
  assert.equal(captured.authenticationMode, 'SUPABASE')
  await fresh(captured, f.api, () => true)
  f.input.selectedDeployment.binding.customerId = 'foreign'
  assert.throws(() => scope(f.input))
})

test('missing, foreign, stale-offer and unsupported target/profile scopes fail closed', () => {
  for (const change of [f => { f.session = null }, f => { f.offer = null }, f => { f.selectedDeployment.binding.customerId = 'foreign' },
    f => { f.selectedDeployment.binding.tenantId = 'other' }, f => { f.deployment.offerFingerprint = 'old' },
    f => { f.offer.entitlement.status = 'REVOKED' }, f => { f.offer.entitlement.customerId = 'foreign' },
    f => { f.targetProfileId = 'other' }, f => { f.selectedProfileId = 'unreviewed' },
    f => { f.offer.bindingSlots.push(structuredClone(f.offer.bindingSlots[0])) }]) {
    const { input } = fixture(); change(input); assert.throws(() => scope(input))
  }
})

test('fresh owner readbacks reject current revision/authority/resource drift before any mutation', async () => {
  for (const change of [f => { f.input.offer.entitlement.revision++ }, f => { f.input.session.actorId = 'other' },
    f => { f.input.session.authenticationMode = 'API_KEY' }, f => { f.input.deployment.tenantId = 'other' },
    f => { f.input.offer.fingerprint = 'changed' }, f => { f.api.deployments = async () => [] },
    f => { f.api.deployments = async () => [f.input.deployment, f.input.deployment] },
    f => { f.input.offer.targets[0].capabilities = [] }]) {
    const f = fixture(), captured = scope(f.input); change(f)
    await assert.rejects(fresh(captured, f.api, () => true), /authority changed/)
  }
})

test('in-flight readback cannot submit after selected actor/target scope changes', async () => {
  const f = fixture(), captured = scope(f.input)
  let current = true, release
  f.api.offer = () => new Promise(resolve => { release = resolve })
  const read = fresh(captured, f.api, () => current)
  current = false; release(f.input.offer)
  await assert.rejects(read, /authority changed/)
})

test('API errors preserve only structured code/rejection evidence, never infer it from text', async () => {
  const apiSource = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
  const compiled = ts.transpileModule(apiSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const oldWindow = globalThis.window, oldFetch = globalThis.fetch
  globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'http://loopback.invalid' } }
  try {
    const api = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
    for (const [payload, code, rejected] of [
      [{ success: false, errorCode: 'SETTING_FORBIDDEN', message: 'arbitrary text' }, 'SETTING_FORBIDDEN', true],
      [{ message: 'SETTING_FORBIDDEN' }, null, false],
      [{ success: true, errorCode: 'SETTING_FORBIDDEN' }, 'SETTING_FORBIDDEN', false],
      [{ success: false, errorCode: 'setting forbidden' }, null, true],
    ]) {
      globalThis.fetch = async () => new Response(JSON.stringify(payload), { status: 403, headers: { 'Content-Type': 'application/json' } })
      await assert.rejects(api.fetchCustomerSelfServiceOffer('offer'), error => {
        assert.equal(error.status, 403); assert.equal(error.errorCode, code); assert.equal(error.responseRejected, rejected); return true
      })
    }
    globalThis.fetch = async () => new Response('SETTING_FORBIDDEN', { status: 403 })
    await assert.rejects(api.fetchCustomerSelfServiceOffer('offer'), error => error.errorCode === null && !error.responseRejected)
  } finally { globalThis.window = oldWindow; globalThis.fetch = oldFetch }
})
