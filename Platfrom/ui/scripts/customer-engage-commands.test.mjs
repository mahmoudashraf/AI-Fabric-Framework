import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/customerEngageCommands.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { prepareCustomerEngageCommand } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const fixture = () => ({ actorId: 'owner', customerId: 'customer',
  consumers: [{ consumerId: 'chosen', customerId: 'customer', status: 'ACTIVE', boundDeploymentId: 'deployment',
    boundTargetProfileId: 'production', boundReleaseId: 'release', bindingRevision: 3 }],
  deployments: [{ deploymentId: 'deployment', offerId: 'offer', offerFingerprint: 'reviewed' }],
  offers: [{ id: 'offer', fingerprint: 'reviewed', entitlement: { customerId: 'customer', status: 'ACTIVE', revision: 4 },
    targets: [{ id: 'staging', capabilities: ['APP_CONFIGURE'] }, { id: 'production', capabilities: ['APP_CONFIGURE'] }] }],
})
test('refreshes the exact assigned target, not the bootstrap or first target', async () => {
  const state = fixture(), calls = []
  await prepareCustomerEngageCommand(() => state, 'chosen', async (...args) => calls.push(args))
  assert.deepEqual(calls, [['offer', 'production']])
})
test('missing, foreign and inactive assignments never probe', async () => {
  for (const change of [state => { state.consumers = [] }, state => { state.consumers[0].customerId = 'foreign' },
    state => { state.consumers[0].status = 'DISABLED' }]) {
    const state = fixture(); change(state)
    await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => assert.fail('Unexpected probe')))
  }
})
test('revoked entitlement, changed offer and forbidden target never probe', async () => {
  for (const change of [state => { state.offers[0].entitlement.status = 'REVOKED' },
    state => { state.offers[0].fingerprint = 'changed' }, state => { state.offers[0].targets[1].capabilities = ['READ'] }]) {
    const state = fixture(); change(state)
    await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => assert.fail('Unexpected probe')))
  }
})
test('a failed placement check propagates, without a retry or write', async () => {
  let calls = 0
  await assert.rejects(prepareCustomerEngageCommand(fixture, 'chosen', async () => { calls++; throw new Error('Denied') }), /Denied/)
  assert.equal(calls, 1)
})
test('assignment, actor and entitlement changes during the check stop the command', async () => {
  for (const change of [state => { state.actorId = 'different' }, state => { state.customerId = 'foreign' },
    state => { state.consumers[0].bindingRevision++ }, state => { state.consumers[0].boundReleaseId = 'other' },
    state => { state.offers[0].entitlement.revision++ }]) {
    const state = fixture()
    await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => change(state)))
  }
})
test('closing the view while checking prevents the command from proceeding', async () => {
  let state = fixture()
  await assert.rejects(prepareCustomerEngageCommand(() => state, 'chosen', async () => { state = null }), /changed/)
  await assert.rejects(prepareCustomerEngageCommand(() => null, 'chosen', async () => assert.fail()), /no longer active/)
})
test('reassignment checks both old and new targets and deduplicates the same target', async () => {
  const state = fixture(), calls = []
  state.consumers.push({ ...state.consumers[0], consumerId: 'second', boundTargetProfileId: 'staging' })
  await prepareCustomerEngageCommand(() => state, ['chosen', 'second', 'chosen'], async (...args) => calls.push(args))
  assert.deepEqual(calls, [['offer', 'production'], ['offer', 'staging']])
  calls.length = 0; state.consumers[1].boundTargetProfileId = 'production'
  await prepareCustomerEngageCommand(() => state, ['chosen', 'second'], async (...args) => calls.push(args))
  assert.deepEqual(calls, [['offer', 'production']])
})
