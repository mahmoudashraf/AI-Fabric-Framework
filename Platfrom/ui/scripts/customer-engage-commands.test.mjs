import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'
const source = await readFile(new URL('../src/components/customer/customerEngageCommands.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(withCustomerSessionSupport(source), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { prepareCustomerEngageCommand } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const fixture = () => ({ actorId: 'owner', customerId: 'customer',
  consumers: [{ consumerId: 'chosen', customerId: 'customer', status: 'ACTIVE', boundDeploymentId: 'deployment',
    boundTargetProfileId: 'production', boundReleaseId: 'release', bindingRevision: 3 }],
  deployments: [{ deploymentId: 'deployment', offerId: 'offer', offerFingerprint: 'reviewed' }],
  offers: [{ id: 'offer', fingerprint: 'reviewed', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 },
    targets: [{ id: 'staging', capabilities: ['APP_CONFIGURE'] }, { id: 'production', capabilities: ['APP_CONFIGURE'] }] }],
})
const account = () => ({ authenticated: true, actorId: 'owner', customerId: 'customer', role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION' })
test('verified Supabase owners can configure the same owned app', async () => {
  await prepareCustomerEngageCommand(fixture, 'chosen', async () => ({ ...account(), authenticationMode: 'SUPABASE' }))
})
test('checks the current account and exact assigned target without placement or reservation calls', async () => {
  const state = fixture(); let reads = 0
  await prepareCustomerEngageCommand(() => state, 'chosen', async () => { reads++; return account() })
  assert.equal(reads, 1)
  state.consumers[0].boundTargetProfileId = null
  await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => assert.fail('Unexpected account read')))
})
test('missing, foreign and inactive assignments stop before an account read', async () => {
  for (const change of [state => { state.consumers = [] }, state => { state.consumers[0].customerId = 'foreign' },
    state => { state.consumers[0].status = 'DISABLED' }]) {
    const state = fixture(); change(state)
    await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => assert.fail('Unexpected account read')))
  }
})
test('bootstrap offer provenance does not restrict later owned-app commands', async () => {
  for (const change of [state => { state.offers[0].entitlement.status = 'REVOKED' },
    state => { state.offers[0].fingerprint = 'changed' }, state => { state.offers[0].targets[1].capabilities = ['READ'] },
    state => { state.offers = [] }]) {
    const state = fixture(); change(state)
    await prepareCustomerEngageCommand(() => state, 'chosen', async () => account())
  }
})
test('missing deployment or incomplete assignment stops before an account read', async () => {
  for (const change of [state => { state.deployments = [] },
    state => { state.consumers[0].boundDeploymentId = 'foreign' },
    state => { state.consumers[0].boundReleaseId = null }]) {
    const state = fixture(); change(state)
    await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => assert.fail('Unexpected account read')))
  }
})
test('a failed authenticated account read propagates without a retry or write', async () => {
  let calls = 0
  await assert.rejects(prepareCustomerEngageCommand(fixture, 'chosen', async () => { calls++; throw new Error('Denied') }), /Denied/)
  assert.equal(calls, 1)
})
test('assignment and actor changes during the check stop the command', async () => {
  for (const change of [state => { state.actorId = 'different' }, state => { state.customerId = 'foreign' },
    state => { state.consumers[0].bindingRevision++ }, state => { state.consumers[0].boundReleaseId = 'other' },
    state => { state.consumers[0].boundTargetProfileId = 'staging' }]) {
    const state = fixture()
    await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => { change(state); return account() }))
  }
})
test('closing the view while checking prevents the command from proceeding', async () => {
  let state = fixture()
  await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => { state = null; return account() }), /changed/)
  await assert.rejects(prepareCustomerEngageCommand(() => null, 'chosen', async () => assert.fail()), /no longer active/)
})
test('reassignment validates both assignments with one account read and deduplicates repeated IDs', async () => {
  const state = fixture(); let reads = 0
  state.consumers.push({ ...state.consumers[0], consumerId: 'second', boundTargetProfileId: 'staging' })
  await prepareCustomerEngageCommand(() => state, ['chosen', 'second', 'chosen'], async () => { reads++; return account() })
  assert.equal(reads, 1)
  state.consumers[1].boundTargetProfileId = 'production'
  await prepareCustomerEngageCommand(() => state, ['chosen', 'second'], async () => { reads++; return account() })
  assert.equal(reads, 2)
  state.consumers[1].customerId = 'foreign'
  await assert.rejects(prepareCustomerEngageCommand(() => state, ['chosen', 'second'], async () => assert.fail('Unexpected account read')))
})
test('revoked, foreign or non-customer authenticated sessions cannot authorize app commands', async () => {
  for (const changes of [{ authenticated: false }, { actorId: 'other' }, { customerId: 'foreign' },
    { authenticationMode: 'API_KEY' }, { role: 'PLATFORM_ADMIN' }]) {
    await assert.rejects(prepareCustomerEngageCommand(fixture, 'chosen', async () => ({ ...account(), ...changes })), /account changed/)
  }
})
