import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/customer/customerAdmissionCommands.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const apiSource = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
const apiCompiled = ts.transpileModule(apiSource, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const apiUrl = `data:text/javascript;base64,${Buffer.from(apiCompiled).toString('base64')}`
const originalWindow = globalThis.window
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://operations.test' } }
let commandsModule
try {
  const resolved = compiled.replace('../../api/platformApi', apiUrl)
  assert.notEqual(resolved, compiled)
  commandsModule = await import(`data:text/javascript;base64,${Buffer.from(resolved).toString('base64')}`)
} finally {
  globalThis.window = originalWindow
}
const { canApplyReviewedVersion, CustomerAdmissionCommands, releaseNeedsProgressPolling,
  admissionNeedsProgressPolling, currentCustomerAdmission } = commandsModule

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

const policyStatus = (mode = 'DISABLED', serviceConfigured = false, providerUsageMode = 'TEAM_AUTHORIZED') => ({
  mode, manualDeploymentsAllowed: true, serviceConfigured, providerUsageMode, decisionSource: 'EXTERNAL_SERVICE',
})
const applyRequest = () => ({
  targetProfileId: 'staging', versionId: 'version', operation: 'APPLY', operationKey: 'apply',
})

function fixture(status = policyStatus()) {
  const state = { status, statuses: 0, failure: null, legacyCalls: [] }
  const legacy = name => async () => {
    state.legacyCalls.push(name)
    throw new Error(`Legacy ${name} must not be called`)
  }
  const commands = new CustomerAdmissionCommands({
    status: async () => {
      state.statuses += 1
      if (state.failure) throw state.failure
      return state.status
    },
    probe: legacy('probe'), list: legacy('list'), reserve: legacy('reserve'),
  })
  return { state, commands }
}

for (const mode of ['DISABLED', 'OBSERVE', 'ENFORCE']) {
  for (const serviceConfigured of [false, true]) {
    for (const providerUsageMode of ['FUNDED', 'TEAM_AUTHORIZED', 'CUSTOMER_KEY']) {
      test(`${mode}/${serviceConfigured}/${providerUsageMode} leaves admission assessment to core apply`, async () => {
        const { state, commands } = fixture(policyStatus(mode, serviceConfigured, providerUsageMode))
        const request = applyRequest()
        assert.equal(await commands.authorize('offer', 'deployment', request), null)
        assert.deepEqual(request, applyRequest())
        assert.equal(state.statuses, 1)
        assert.deepEqual(state.legacyCalls, [])
      })
    }
  }
}

test('promotion, activation and rollback require no invented allocation or ACTIVE evidence', async () => {
  const { state, commands } = fixture(policyStatus('ENFORCE', true))
  for (const operation of ['PROMOTION_PLAN', 'PROMOTION_APPLY', 'ACTIVATE', 'ROLLBACK']) {
    const request = { ...applyRequest(), targetProfileId: 'production', operation, operationKey: operation }
    if (['ACTIVATE', 'ROLLBACK'].includes(operation)) request.releaseId = 'verified-release'
    assert.equal(await commands.authorize('offer', 'deployment', request), null)
    assert.equal('allocationId' in request, false)
    commands.complete(request.operationKey)
  }
  assert.equal(state.statuses, 4)
  assert.deepEqual(state.legacyCalls, [])
})

test('retries refresh server status without legacy admission or mutating a command', async () => {
  const { state, commands } = fixture()
  const request = applyRequest()
  assert.equal(await commands.authorize('offer', 'deployment', request), null)
  state.status = policyStatus('ENFORCE', true, 'FUNDED')
  assert.equal(await commands.authorize('offer', 'deployment', request), null)
  assert.equal(state.statuses, 2)
  assert.deepEqual(request, applyRequest())
  assert.deepEqual(state.legacyCalls, [])
})

test('changed scope or command cannot reuse an operation key', async () => {
  const { state, commands } = fixture()
  const request = applyRequest()
  await commands.authorize('offer', 'deployment', request)
  await assert.rejects(commands.authorize('foreign-offer', 'deployment', request), /command changed/)
  await assert.rejects(commands.authorize('offer', 'foreign-deployment', request), /command changed/)
  for (const changes of [
    { versionId: 'other-version' }, { targetProfileId: 'production' }, { operation: 'ROLLBACK' }, { releaseId: 'other-release' },
  ]) {
    await assert.rejects(commands.authorize('offer', 'deployment', { ...request, ...changes }), /command changed/)
  }
  assert.equal(state.statuses, 1)
  commands.complete(request.operationKey)
  assert.equal(await commands.authorize('offer', 'other-deployment', request), null)
  assert.equal(state.statuses, 2)
  assert.deepEqual(state.legacyCalls, [])
})

test('concurrent commands retain the original scope before status resolves', async () => {
  let resolveStatus
  const status = new Promise(resolve => { resolveStatus = resolve })
  const commands = new CustomerAdmissionCommands({ status: () => status })
  const request = applyRequest()
  const first = commands.authorize('offer', 'deployment', request)
  await assert.rejects(commands.authorize('offer', 'other-deployment', request), /command changed/)
  resolveStatus(policyStatus())
  assert.equal(await first, null)
})

test('ordinary status errors remain visible and never fall back to legacy admission', async () => {
  const { state, commands } = fixture()
  const request = applyRequest()
  for (const message of ['Unauthorized', 'Forbidden', 'Status service unavailable', 'Connection lost']) {
    state.failure = new Error(message)
    await assert.rejects(commands.authorize('offer', 'deployment', request), error => error === state.failure)
  }
  state.failure = null
  assert.equal(await commands.authorize('offer', 'deployment', request), null)
  assert.equal(state.statuses, 5)
  assert.deepEqual(state.legacyCalls, [])
})

test('missing or malformed policy status cannot become client-side permission', async () => {
  const { state, commands } = fixture()
  for (const status of [undefined, null, {},
    { ...policyStatus(), mode: 'UNKNOWN' },
    { ...policyStatus(), manualDeploymentsAllowed: false },
    { ...policyStatus(), manualDeploymentsAllowed: 'true' },
    { ...policyStatus(), serviceConfigured: 'false' },
    { ...policyStatus(), providerUsageMode: 'UNBOUNDED' },
    { ...policyStatus(), decisionSource: 'LEGACY_ADMISSION' },
    { ...policyStatus(), decisionSource: undefined },
  ]) {
    state.status = status
    await assert.rejects(commands.authorize('offer', 'deployment', applyRequest()), /policy status is invalid/)
  }
  assert.deepEqual(state.legacyCalls, [])
})

test('existing constructors use the authenticated status route without running injected legacy methods', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  const legacyCalls = []
  const legacy = name => async () => { legacyCalls.push(name); throw new Error(`Unexpected ${name}`) }
  const commands = new CustomerAdmissionCommands({ probe: legacy('probe'), list: legacy('list'), reserve: legacy('reserve') })
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init })
    return new Response(JSON.stringify(policyStatus('ENFORCE', true)), { headers: { 'Content-Type': 'application/json' } })
  }
  try {
    assert.equal(await commands.authorize('offer', 'deployment', applyRequest()), null)
    assert.equal(calls.length, 1)
    assert.equal(calls[0].url, 'https://operations.test/api/deployment-operations/status')
    assert.equal(calls[0].init.credentials, 'include')
    assert.deepEqual(legacyCalls, [])
  } finally {
    globalThis.fetch = originalFetch
  }
})
