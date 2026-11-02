import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

async function compile(path) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const result = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
  return import(`data:text/javascript;base64,${Buffer.from(result.outputText).toString('base64')}`)
}
const helper = await compile('../src/components/customer/existingVectorBindingForm.ts')
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://platform.test' } }
const api = await compile('../src/api/platformApi.ts')
const manifest = () => ({ deploymentId: 'dep/one', versionId: 'ver/one', providerConfig: { vectorStrategy: 'milvus',
  vectorProvisioningMode: 'EXTERNAL_EXISTING', vectorStoragePosture: 'SHARED', milvusExternalBindingRequired: true, milvusAuthenticationType: 'TOKEN' } })
const form = () => ({ clusterId: 'cluster', projectId: 'project', regionId: 'region', expectedDeploymentOption: 'Free',
  token: 'synthetic-private-token', username: 'serving-user', password: 'synthetic-private-password' })

test('only Providers and owned Connections mount one shared binding control, never the generic editor shell', async () => {
  const read = path => readFile(new URL(path, import.meta.url), 'utf8')
  const shell = await read('../src/components/customer/CustomerConfigurationPanel.tsx')
  assert.equal(shell.includes('CustomerExistingVectorBinding'), false)
  const providers = await read('../src/pages/ProvidersPage.tsx')
  const connections = await read('../src/pages/CustomerKnowledgeConnectionsPage.tsx')
  assert.equal([...providers.matchAll(/<CustomerExistingVectorBinding\b/g)].length, 1)
  assert.equal([...connections.matchAll(/<CustomerExistingVectorBinding\b/g)].length, 1)
  assert.match(connections, /mode === 'connections' && deployment \? <CustomerExistingVectorBinding/)
  const vector = await read('../src/components/customer/CustomerExistingVectorBinding.tsx')
  assert.match(vector, /import \{ connectionEnvironments, connectionEnvironmentSelection \} from '\.\/customerEngageConnection'/)
  assert.equal([...vector.matchAll(/connectionEnvironmentSelection\(/g)].length, 2)
  assert.doesNotMatch(vector, /\{value\.name\} \/ \{value\.environmentName\}/)
})

test('the published typed mode chooses only supported auth and does not rewrite configuration', () => {
  const input = manifest(), before = structuredClone(input)
  assert.equal(helper.existingVectorAuth(input, 'dep/one', 'ver/one'), 'TOKEN')
  assert.deepEqual(input, before)
  delete input.providerConfig.milvusAuthenticationType
  delete input.providerConfig.vectorProvisioningMode
  assert.equal(helper.existingVectorAuth(input, 'dep/one', 'ver/one'), 'USERNAME_PASSWORD')
  for (const change of [v => v.deploymentId = 'foreign', v => v.versionId = 'foreign', v => v.providerConfig.milvusAuthenticationType = 'unknown']) {
    const value = manifest(); change(value); assert.throws(() => helper.existingVectorAuth(value, 'dep/one', 'ver/one'))
  }
  for (const auth of ['', null, 'token', ' TOKEN ']) {
    const value = manifest(); value.providerConfig.milvusAuthenticationType = auth
    assert.throws(() => helper.existingVectorAuth(value, 'dep/one', 'ver/one'))
  }
  for (const change of [v => v.vectorStrategy = 'qdrant', v => v.vectorProvisioningMode = 'PLATFORM_MANAGED',
    v => v.vectorStoragePosture = 'DEDICATED', v => v.milvusExternalBindingRequired = false]) {
    const value = manifest(); change(value.providerConfig); assert.equal(helper.existingVectorAuth(value, 'dep/one', 'ver/one'), null)
  }
})

test('encoded customer routes carry exact typed credentials only in POST bodies, never management keys or caller approvals', async () => {
  const calls = [], original = globalThis.fetch
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response('{}') }
  try {
    for (const auth of ['TOKEN', 'USERNAME_PASSWORD']) {
      const payload = helper.existingVectorRequest(form(), 'ver/one', auth)
      await api.bindCustomerExistingVector('dep/one', 'target/one', payload)
      const call = calls.at(-1), body = JSON.parse(call.init.body)
      assert.equal(call.url, 'https://platform.test/api/customer-self-service/deployments/dep%2Fone/targets/target%2Fone/existing-vector-binding')
      assert.equal(call.init.credentials, 'include'); assert.equal(call.init.method, 'POST')
      assert.equal(body.databaseName, 'default'); assert.equal(body.versionId, 'ver/one'); assert.equal(body.authType, auth)
      assert.equal('approvalReference' in body, false)
      assert.equal('token' in body, auth === 'TOKEN'); assert.equal('password' in body, auth !== 'TOKEN')
      assert.equal(call.url.includes('synthetic-private'), false)
    }
    await api.fetchCustomerExistingVectorBinding('dep/one', 'target/one')
    assert.equal(calls.at(-1).init.body, undefined)
  } finally { globalThis.fetch = original }
})

test('missing inputs cannot produce a bind command', () => {
  for (const key of ['clusterId', 'projectId', 'regionId', 'token']) {
    const value = form(); value[key] = ''; assert.throws(() => helper.existingVectorRequest(value, 'version', 'TOKEN'))
  }
  assert.throws(() => helper.existingVectorRequest(form(), '', 'TOKEN'))
})

test('stored readback is target-scoped, permits original approval versions, and is not a live provider probe', () => {
  const value = { bindingId: 'binding', deploymentId: 'dep/one', targetProfileId: 'target/one', versionId: 'older-approved-version',
    lifecycle: 'RETAIN_EXTERNAL', databaseName: 'default' }
  assert.equal(helper.checkedExistingVectorBinding(value, 'dep/one', 'target/one'), value)
  for (const change of [v => v.deploymentId = 'foreign', v => v.targetProfileId = 'foreign', v => v.versionId = '',
    v => v.lifecycle = 'PLATFORM_MANAGED', v => v.databaseName = 'foreign']) {
    const changed = { ...value }; change(changed); assert.throws(() => helper.checkedExistingVectorBinding(changed, 'dep/one', 'target/one'))
  }
})
