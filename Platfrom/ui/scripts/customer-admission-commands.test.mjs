import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/customer/customerAdmissionCommands.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { canApplyReviewedVersion, CustomerAdmissionCommands, releaseNeedsProgressPolling,
  admissionNeedsProgressPolling, currentCustomerAdmission } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('customer progress observes actual release and allocation states until settled', () => {
  for (const status of ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING']) assert.equal(releaseNeedsProgressPolling(status), true)
  for (const status of ['APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED', 'FAILED']) assert.equal(releaseNeedsProgressPolling(status), false)
  for (const state of ['RESERVED', 'PROVISIONING', 'OUTCOME_UNKNOWN']) assert.equal(admissionNeedsProgressPolling(state), true)
  for (const state of ['ACTIVE', 'RELEASED', 'CLEANUP_FAILED']) assert.equal(admissionNeedsProgressPolling(state), false)
})

test('fresh same-scope allocation readback replaces retained command state without crossing resources', () => {
  const retained = { id: 'allocation', deploymentId: 'deployment', targetProfileId: 'staging', revision: 1, state: 'PROVISIONING' }
  const fresh = { ...retained, revision: 3, state: 'ACTIVE' }
  assert.equal(currentCustomerAdmission(retained, [fresh], 'deployment', 'staging'), fresh)
  assert.equal(currentCustomerAdmission(fresh, [retained], 'deployment', 'staging'), fresh)
  assert.equal(currentCustomerAdmission(retained, [{ ...fresh, deploymentId: 'other' }], 'deployment', 'staging'), retained)
  assert.equal(currentCustomerAdmission(retained, [fresh], 'other', 'staging'), null)
  assert.equal(currentCustomerAdmission(null, [fresh], 'deployment', 'production'), null)
  assert.equal(currentCustomerAdmission(null, [fresh, { ...fresh, id: 'ambiguous' }], 'deployment', 'staging'), null)
})

test('reviewed new versions may replace a settled release but not duplicate or interrupt an apply', () => {
  const release = { deploymentVersionId: 'version-one', status: 'APPLIED_VERIFIED',
    verificationStatus: 'PASSED', provisioningStatus: 'ACTIVE' }
  assert.equal(canApplyReviewedVersion('version-two', release), true)
  assert.equal(canApplyReviewedVersion('version-one', release), false)
  assert.equal(canApplyReviewedVersion(undefined, release), false)
  assert.equal(canApplyReviewedVersion('version-one', null), true)
  for (const status of ['QUEUED', 'RUNNING', 'VERIFYING', 'APPLYING', 'UNKNOWN']) {
    assert.equal(canApplyReviewedVersion('version-two', { ...release, status }), false)
  }
  for (const status of ['FAILED', 'PRE_APPLY_BLOCKED']) {
    assert.equal(canApplyReviewedVersion('version-one', { ...release, status }), true)
  }
  assert.equal(canApplyReviewedVersion('version-two', { ...release, verificationStatus: 'FAILED' }), false)
  assert.equal(canApplyReviewedVersion('version-one', { ...release, status: 'APPLIED_VERIFICATION_FAILED', verificationStatus: 'FAILED' }), true)
  assert.equal(canApplyReviewedVersion('version-one', { ...release, status: 'APPLIED_VERIFICATION_FAILED', provisioningStatus: 'RUNNING' }), false)
})

function fixture() {
  const state = { allocations: [], reservations: [], probes: [], loseResponse: false, lists: 0 }
  const commands = new CustomerAdmissionCommands({
    probe: async (...args) => { state.probes.push(args) },
    list: async () => { state.lists += 1; return state.allocations },
    reserve: async (offerId, deploymentId, request) => {
      state.reservations.push({ offerId, deploymentId, request })
      state.allocations = [{ id: 'allocation', targetProfileId: request.targetProfileId }]
      if (state.loseResponse) throw new Error('Connection lost after acceptance')
      return state.allocations[0]
    },
  })
  return { state, commands }
}

test('a lost response retries the exact reservation without changing its allocation choice', async () => {
  const { state, commands } = fixture()
  const request = { targetProfileId: 'staging', versionId: 'version', operation: 'APPLY', operationKey: 'apply' }
  state.loseResponse = true
  await assert.rejects(commands.authorize('offer', 'deployment', request), /Connection lost/)
  state.loseResponse = false
  await commands.authorize('offer', 'deployment', request)
  assert.deepEqual(state.reservations[0], state.reservations[1])
  assert.equal(state.reservations[1].request.operationKey, 'apply')
  assert.equal(state.reservations[1].request.allocationId, undefined)
})

test('promotion stages reuse one allocation with distinct operation receipts', async () => {
  const { state, commands } = fixture()
  for (const operation of ['PROMOTION_PLAN', 'PROMOTION_APPLY', 'ACTIVATE']) {
    const request = { targetProfileId: 'production', versionId: 'version', operation, operationKey: operation }
    if (operation === 'ACTIVATE') request.releaseId = 'production-release'
    await commands.authorize('offer', 'deployment', request)
    commands.complete(request.operationKey)
  }
  assert.equal(state.reservations[0].request.allocationId, undefined)
  assert.equal(state.reservations[1].request.allocationId, 'allocation')
  assert.equal(state.reservations[2].request.allocationId, 'allocation')
  assert.equal(state.reservations[2].request.releaseId, 'production-release')
  assert.equal(state.probes.length, 3)
})

test('concurrent submissions freeze one allocation choice for their shared operation key', async () => {
  const { state, commands } = fixture()
  const request = { targetProfileId: 'staging', versionId: 'version', operation: 'APPLY', operationKey: 'apply' }
  await Promise.all([
    commands.authorize('offer', 'deployment', request),
    commands.authorize('offer', 'deployment', request),
  ])
  assert.equal(state.lists, 1)
  assert.deepEqual(state.reservations[0], state.reservations[1])
})

test('changed commands, ambiguous allocations, and unallocated releases fail before reserve', async () => {
  const { state, commands } = fixture()
  const request = { targetProfileId: 'staging', versionId: 'version', operation: 'APPLY', operationKey: 'apply' }
  await commands.authorize('offer', 'deployment', request)
  await assert.rejects(commands.authorize('foreign-offer', 'deployment', request), /command changed/)
  await assert.rejects(commands.authorize('offer', 'deployment', { ...request, versionId: 'other-version' }), /command changed/)
  state.allocations = [{ id: 'one' }, { id: 'two' }]
  await assert.rejects(commands.authorize('offer', 'deployment', { ...request, operationKey: 'ambiguous' }), /reconciliation/)
  state.allocations = []
  await assert.rejects(commands.authorize('offer', 'deployment', {
    ...request, operation: 'ROLLBACK', operationKey: 'rollback', releaseId: 'staging-release',
  }), /no current capacity/)
  assert.equal(state.reservations.length, 1)
})

test('placement is refreshed before the allocation list that requires current evidence', async () => {
  const calls = []
  let current = false
  const commands = new CustomerAdmissionCommands({
    probe: async () => { calls.push('probe'); current = true },
    list: async () => { calls.push('list'); assert.equal(current, true); return [] },
    reserve: async () => { calls.push('reserve'); return { id: 'allocation' } },
  })
  await commands.authorize('offer', 'deployment', {
    targetProfileId: 'production', versionId: 'version', operation: 'PROMOTION_PLAN', operationKey: 'plan',
  })
  assert.deepEqual(calls, ['probe', 'list', 'reserve'])
})

test('a failed placement probe cannot list or reserve and the original operation can be reviewed again', async () => {
  let fail = true
  const calls = []
  const commands = new CustomerAdmissionCommands({
    probe: async () => { calls.push('probe'); if (fail) throw new Error('Placement unavailable') },
    list: async () => { calls.push('list'); return [] },
    reserve: async () => { calls.push('reserve'); return { id: 'allocation' } },
  })
  const request = { targetProfileId: 'production', versionId: 'version', operation: 'PROMOTION_PLAN', operationKey: 'plan' }
  await assert.rejects(commands.authorize('offer', 'deployment', request), /Placement unavailable/)
  assert.deepEqual(calls, ['probe'])
  fail = false
  await commands.authorize('offer', 'deployment', request)
  assert.deepEqual(calls, ['probe', 'probe', 'list', 'reserve'])
})

test('lost response reprobes placement without relisting or changing the frozen allocation', async () => {
  const { state, commands } = fixture()
  state.loseResponse = true
  const request = { targetProfileId: 'production', versionId: 'version', operation: 'PROMOTION_APPLY', operationKey: 'apply' }
  await assert.rejects(commands.authorize('offer', 'deployment', request), /Connection lost/)
  state.loseResponse = false
  await commands.authorize('offer', 'deployment', request)
  assert.equal(state.probes.length, 2)
  assert.equal(state.lists, 1)
  assert.deepEqual(state.reservations[0], state.reservations[1])
})
