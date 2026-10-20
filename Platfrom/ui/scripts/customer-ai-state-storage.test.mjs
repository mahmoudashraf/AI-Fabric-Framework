import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const compile = async path => {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const result = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
  return import(`data:text/javascript;base64,${Buffer.from(result.outputText).toString('base64')}`)
}
const helper = await compile('../src/components/customer/customerAiStateStorage.ts')
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://platform.test' } }
const api = await compile('../src/api/platformApi.ts')
const offer = () => ({ id: 'offer/one', fingerprint: 'offer-pin', entitlement: { customerId: 'owner', status: 'ACTIVE', revision: 4 },
  settings: [{ setting: 'AI_STATE_STORAGE_MODE', type: 'CHOICE', allowedValues: ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES'] }],
  targets: [{ id: 'staging/one', intent: 'CUSTOMER_STAGING', capabilities: ['READ', 'BINDING_REPLACE'] }],
  bindingSlots: [{ id: 'ai-state', kind: 'CUSTOMER_AI_STATE', supportedProfileIds: ['CUSTOMER_POSTGRES'] }] })
const deployment = () => ({ deploymentId: 'deployment/one', offerId: 'offer/one', offerFingerprint: 'offer-pin', tenantId: 'tenant', bootstrapTargetProfileId: 'staging/one' })
const draft = mode => ({ id: 'draft-one', deploymentId: 'deployment/one', revisionNumber: 3, updatedAt: '2026-10-20T12:00:00Z', providerConfig: { aiStateStorageMode: mode, preserved: 'same' } })
const scope = () => ({ customerId: 'owner', offer: offer(), deployment: deployment(), targetProfileId: 'staging/one',
  version: { id: 'version/one', deploymentId: 'deployment/one', status: 'PUBLISHED', configHash: 'version-pin' } })
const fields = () => ({ host: 'db.example.test', port: 5432, databaseName: 'ai_state', runtimeSchema: 'runtime_ai',
  connectorSchema: 'connector_ai', username: 'ai_user', password: 'synthetic-private-value', migrationAuthorized: true })

test('both entitled choices reach the actual bootstrap API body explicitly and retain owner-scoped encoded routes', async () => {
  const calls = [], original = globalThis.fetch
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response('{}') }
  try {
    for (const mode of ['MANAGED_POSTGRES', 'CUSTOMER_POSTGRES']) {
      await api.bootstrapCustomerDeployment(offer().id, helper.customerStorageBootstrapRequest(offer(), 'owner', 'staging/one', ' My app ', mode, 'key-'+mode))
      const call = calls.at(-1), body = JSON.parse(call.init.body)
      assert.equal(call.url, 'https://platform.test/api/customer-self-service/catalogue/offer%2Fone/deployments')
      assert.equal(call.init.credentials, 'include'); assert.equal(call.init.method, 'POST')
      assert.deepEqual(body, { targetProfileId: 'staging/one', name: 'My app', expectedEntitlementRevision: 4, operationKey: 'key-'+mode, aiStateStorageMode: mode })
    }
  } finally { globalThis.fetch = original }
})
test('missing, unknown, or no-longer-entitled choices cannot become a bootstrap request', () => {
  for (const mode of ['', 'UNKNOWN', 'customer_postgres']) assert.throws(() => helper.customerStorageBootstrapRequest(offer(), 'owner', 'staging/one', 'App', mode, 'key'))
  const changed = offer(); changed.settings[0].allowedValues = ['MANAGED_POSTGRES']
  assert.throws(() => helper.customerStorageBootstrapRequest(changed, 'owner', 'staging/one', 'App', 'CUSTOMER_POSTGRES', 'key'))
})
test('foreign/inactive owner and foreign/production target scopes are denied locally', () => {
  assert.throws(() => helper.customerStorageBootstrapRequest(offer(), 'foreign', 'staging/one', 'App', 'MANAGED_POSTGRES', 'key'))
  const inactive = offer(); inactive.entitlement.status = 'REVOKED'
  assert.throws(() => helper.customerStorageBootstrapRequest(inactive, 'owner', 'staging/one', 'App', 'MANAGED_POSTGRES', 'key'))
  assert.throws(() => helper.customerStorageBootstrapRequest(offer(), 'owner', 'foreign-target', 'App', 'MANAGED_POSTGRES', 'key'))
  const production = offer(); production.targets[0].intent = 'CUSTOMER_PRODUCTION'
  assert.throws(() => helper.customerStorageBootstrapRequest(production, 'owner', 'staging/one', 'App', 'MANAGED_POSTGRES', 'key'))
})
test('legacy offers without a storage-choice setting preserve the old body and never manufacture a mode', () => {
  const legacy = offer(); legacy.settings = []
  assert.equal('aiStateStorageMode' in helper.customerStorageBootstrapRequest(legacy, 'owner', 'staging/one', 'App', '', 'key'), false)
  assert.throws(() => helper.customerStorageBootstrapRequest(legacy, 'owner', 'staging/one', 'App', 'MANAGED_POSTGRES', 'key'))
})
test('malformed and duplicate offer rules fail closed', () => {
  for (const edit of [o => o.settings.push({ ...o.settings[0] }), o => o.settings[0].allowedValues.push('UNKNOWN'),
    o => o.settings[0].allowedValues = [], o => o.settings[0].type = 'TEXT']) {
    const o = offer(); edit(o); assert.throws(() => helper.aiStateStorageModes(o))
  }
})
test('publishing preserves stored mode and sends only the actual PublishRequest fields, without rewriting the draft', async () => {
  const o = offer(), d = draft('CUSTOMER_POSTGRES'), before = structuredClone(d), calls = [], original = globalThis.fetch
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response('' + JSON.stringify({ versionId: 'new' })) }
  try {
    await helper.publishStoredAiStateStorage({ draft: async () => structuredClone(d), publish: api.publishCustomerDeploymentVersion }, o, 'owner', deployment(), d, 'publish-key', () => true)
    assert.deepEqual(d, before)
    assert.deepEqual(JSON.parse(calls[0].init.body), { targetProfileId: 'staging/one', expectedEntitlementRevision: 4, operationKey: 'publish-key' })
    assert.equal(calls[0].url, 'https://platform.test/api/customer-self-service/catalogue/offer%2Fone/deployments/deployment%2Fone/versions')
    assert.equal(calls[0].init.credentials, 'include')
  } finally { globalThis.fetch = original }
})
test('changed draft mode, draft revision or deployment cannot be published from stale UI state', async () => {
  for (const edit of [d => d.providerConfig.aiStateStorageMode = 'MANAGED_POSTGRES', d => d.revisionNumber++, d => d.deploymentId = 'foreign']) {
    const old = draft('CUSTOMER_POSTGRES'), current = structuredClone(old); edit(current); let posts = 0
    await assert.rejects(helper.publishStoredAiStateStorage({ draft: async () => current, publish: async () => { posts++ } }, offer(), 'owner', deployment(), old, 'key', () => true))
    assert.equal(posts, 0)
  }
})
test('scope change while the publish preflight is pending prevents the subsequent POST', async () => {
  let complete, active = true, posts = 0
  const current = draft('CUSTOMER_POSTGRES'), pending = new Promise(resolve => { complete = resolve })
  const operation = helper.publishStoredAiStateStorage({ draft: () => pending, publish: async () => { posts++ } }, offer(), 'owner', deployment(), current, 'key', () => active)
  active = false; complete(current); await assert.rejects(operation); assert.equal(posts, 0)
})
test('existing modes are read from their stored configuration, not any creation selector choice', () => {
  assert.equal(helper.storedAiStateStorageMode(offer(), draft('CUSTOMER_POSTGRES')), 'CUSTOMER_POSTGRES')
  assert.equal(helper.storedAiStateStorageMode(offer(), draft('MANAGED_POSTGRES')), 'MANAGED_POSTGRES')
  assert.throws(() => helper.storedAiStateStorageMode(offer(), draft(undefined)))
  assert.throws(() => helper.storedAiStateStorageMode(offer(), draft('UNKNOWN')))
})
test('BYO credentials reach only the actual typed POST body, with explicit version/target/slot and migration consent', async () => {
  const calls = [], original = globalThis.fetch, s = scope(), form = fields()
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response('{}') }
  try {
    const body = helper.customerAiStateBindingRequest(s, form, 'bind-key')
    await api.bindCustomerAiStateStorage(s.offer.id, s.deployment.deploymentId, body)
    await api.fetchCustomerAiStateStorage(s.offer.id, s.deployment.deploymentId, helper.customerAiStateBindingScope(s))
    assert.deepEqual(JSON.parse(calls[0].init.body), { targetProfileId: 'staging/one', bindingSlotId: 'ai-state', expectedEntitlementRevision: 4,
      operationKey: 'bind-key', connection: { ...form, versionId: 'version/one' } })
    assert.equal(calls[0].init.credentials, 'include'); assert.equal(calls[0].init.method, 'POST')
    for (const call of calls) assert.equal(call.url.includes(form.password), false)
    assert.equal(calls[1].init.body, undefined)
    const query = new URL(calls[1].url).searchParams
    assert.deepEqual(Object.fromEntries(query), { versionId: 'version/one', targetProfileId: 'staging/one', bindingSlotId: 'ai-state' })
  } finally { globalThis.fetch = original }
})
test('BYO scope, migration consent and distinct dedicated schemas are required', () => {
  for (const edit of [s => s.customerId = 'foreign', s => s.version.deploymentId = 'foreign', s => s.targetProfileId = 'foreign',
    s => s.offer.bindingSlots = [], s => s.offer.targets[0].capabilities = ['READ']]) {
    const s = scope(); edit(s); assert.throws(() => helper.customerAiStateBindingRequest(s, fields(), 'key'))
  }
  for (const edit of [f => f.migrationAuthorized = false, f => f.runtimeSchema = 'public', f => f.connectorSchema = f.runtimeSchema,
    f => f.port = 0, f => f.host = 'https://db.example.test', f => f.password = '']) {
    const f = fields(); edit(f); assert.throws(() => helper.customerAiStateBindingRequest(scope(), f, 'key'))
  }
})
test('foreign/stale BYO readbacks are not accepted as readiness for the current target and version', () => {
  const s = scope(), binding = { customerId: 'owner', tenantId: 'tenant', deploymentId: 'deployment/one', targetProfileId: 'staging/one',
    versionId: 'version/one', configHash: 'version-pin', mode: 'CUSTOMER_POSTGRES', lifecycle: 'RETAIN_EXTERNAL', migrationAuthorized: true }
  assert.equal(helper.checkedAiStateBinding(s, binding), binding)
  for (const field of ['customerId', 'tenantId', 'deploymentId', 'targetProfileId', 'versionId', 'configHash']) {
    assert.throws(() => helper.checkedAiStateBinding(s, { ...binding, [field]: 'foreign' }))
  }
})
