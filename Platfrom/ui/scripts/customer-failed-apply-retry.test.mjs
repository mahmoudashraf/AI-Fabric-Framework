import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'

const source = await readFile(new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url), 'utf8')
const ast = ts.createSourceFile('Console.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const names = new Set(['recoveryText', 'recoveryRevision', 'prepareCustomerFailureRecovery', 'confirmedCustomerFailureRecovery',
  'failedApplyRetryHoldScope', 'prepareCustomerFailedApplyRetry', 'confirmedCustomerFailedApplyRetry', 'checkedCustomerFailedApplyRetryReceipt'])
const selected = ast.statements.filter(node => ts.isFunctionDeclaration(node) ? names.has(node.name?.text)
  : ts.isVariableStatement(node) && node.declarationList.declarations.some(item => names.has(item.name.getText(ast))))
assert.equal(selected.length, names.size)
const transpile = text => ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const pure = await import(`data:text/javascript;base64,${Buffer.from(transpile(withCustomerSessionSupport(selected.map(node => node.getText(ast)).join('\n')))).toString('base64')}`)
const oldWindow = globalThis.window
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://retry.invalid' } }
const api = await import(`data:text/javascript;base64,${Buffer.from(transpile(await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8'))).toString('base64')}`)
globalThis.window = oldWindow
const scope = { actorId: 'owner', customerId: 'customer', authenticationMode: 'SESSION', offerId: 'offer/one', offerFingerprint: 'pin', entitlementRevision: 7,
  deploymentId: 'dep/one', targetProfileId: 'stage', reservationId: 'reservation/one', revision: 3, releaseId: 'failed-release' }
const request = { releaseId: scope.releaseId, operationKey: 'explicit-redeploy-key', reason: 'Reviewed owned service redeploy', acceptOwnedServiceRedeploy: true }
const plan = { command: { fence: { reservationId: scope.reservationId, revision: 3, generation: 'server-generation' }, ...request, proofFingerprint: 'a'.repeat(64) },
  boundary: 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1', redeployedServiceRoles: ['runtime', 'connector'] }
const receipt = { receiptId: 'authorization-receipt', reservationId: scope.reservationId, generation: 'server-generation', fromRevision: 3,
  revision: 4, releaseId: scope.releaseId, status: 'APPLY_REQUESTED', financialMutationPerformed: false, retainedAllocationIds: ['same-allocation'], authorizedAt: '2026-10-20T21:00:00Z' }

test('normal encoded owner API sends consent and exact returned command without recovery/apply fallback', async () => {
  const oldFetch = globalThis.fetch, calls = []
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response(JSON.stringify(calls.length === 1 ? plan : receipt), { headers: { 'Content-Type': 'application/json' } }) }
  try {
    const response = await api.planCustomerFailedApplyRetry(scope.offerId, scope.deploymentId, scope.reservationId, request)
    const prepared = pure.prepareCustomerFailedApplyRetry(scope, request, response)
    const command = pure.confirmedCustomerFailedApplyRetry(scope, prepared, true)
    assert.deepEqual(pure.checkedCustomerFailedApplyRetryReceipt(prepared,
      await api.retryCustomerFailedApply(scope.offerId, scope.deploymentId, scope.reservationId, command)), receipt)
    const base = 'https://retry.invalid/api/customer-self-service/catalogue/offer%2Fone/deployments/dep%2Fone/admission-reservations/reservation%2Fone'
    assert.deepEqual(calls.map(call => call.url), [`${base}/failed-apply-retry-plan`, `${base}/retry-failed-apply`])
    assert.equal(calls[0].init.body, JSON.stringify(request)); assert.equal(calls[1].init.body, JSON.stringify(response.command))
    for (const { init } of calls) { assert.equal(init.method, 'POST'); assert.equal(init.credentials, 'include'); assert.equal(init.headers['X-PLATFORM-API-KEY'], undefined) }
    assert.equal('fence' in request, false); assert.equal('proofFingerprint' in request, false)
  } finally { globalThis.fetch = oldFetch }
})

test('preview requires actual consent and exactly both runtime and connector roles', () => {
  assert.throws(() => pure.prepareCustomerFailedApplyRetry(scope, { ...request, acceptOwnedServiceRedeploy: false }, plan))
  for (const roles of [[], ['connector'], ['runtime'], ['runtime', 'connector', 'database'], ['runtime', 'runtime'], null]) {
    assert.throws(() => pure.prepareCustomerFailedApplyRetry(scope, request, { ...plan, redeployedServiceRoles: roles }), /both owned/)
  }
  assert.throws(() => pure.prepareCustomerFailedApplyRetry(scope, request, { ...plan, command: { ...plan.command, acceptOwnedServiceRedeploy: false } }))
})

test('preview accepts only the exact frozen initial connector-startup boundary', () => {
  for (const boundary of ['', 'OTHER_BOUNDARY_V1', 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V2', null, undefined]) {
    assert.throws(() => pure.prepareCustomerFailedApplyRetry(scope, request, { ...plan, boundary }), /Unsupported/)
  }
  assert.equal(pure.prepareCustomerFailedApplyRetry(scope, request, plan).plan.boundary, 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1')
})

test('actor/offer/D/R/release/revision changes, missing confirmation and changed returned command deny', () => {
  const review = pure.prepareCustomerFailedApplyRetry(scope, request, plan)
  assert.throws(() => pure.confirmedCustomerFailedApplyRetry(scope, review, false))
  for (const [key, value] of [['actorId', 'other'], ['customerId', 'foreign'], ['offerId', 'other'], ['offerFingerprint', 'other'], ['entitlementRevision', 8],
    ['deploymentId', 'other'], ['targetProfileId', 'other'], ['reservationId', 'other'], ['revision', 4], ['releaseId', 'other']]) {
    assert.throws(() => pure.confirmedCustomerFailedApplyRetry({ ...scope, [key]: value }, review, true))
  }
  const changed = structuredClone(review); changed.plan.command.acceptOwnedServiceRedeploy = false
  assert.throws(() => pure.confirmedCustomerFailedApplyRetry(scope, changed, true))
  const drift = structuredClone(plan); drift.command.fence.revision++
  assert.throws(() => pure.prepareCustomerFailedApplyRetry(scope, request, drift))
  assert.deepEqual(review.plan, plan)
})

test('receipt proves authorization only: exact identity/APPLY_REQUESTED/no financial mutation, not effect-free success', () => {
  const review = pure.prepareCustomerFailedApplyRetry(scope, request, plan)
  for (const changed of [null, {}, { ...receipt, receiptId: '' }, { ...receipt, status: 'APPLIED_VERIFIED' }, { ...receipt, status: 'RESERVED' },
    { ...receipt, revision: 3 }, { ...receipt, releaseId: 'other' }, { ...receipt, reservationId: 'other' }, { ...receipt, generation: 'other' },
    { ...receipt, financialMutationPerformed: true }, { ...receipt, authorizedAt: 'unknown' }, { ...receipt, retainedAllocationIds: ['dup', 'dup'] }]) {
    assert.throws(() => pure.checkedCustomerFailedApplyRetryReceipt(review, changed), /unconfirmed/)
  }
  assert.equal(pure.checkedCustomerFailedApplyRetryReceipt(review, receipt).status, 'APPLY_REQUESTED')
})

test('initial-boundary receipt must retain exactly one allocation, never none', () => {
  const review = pure.prepareCustomerFailedApplyRetry(scope, request, plan)
  for (const retainedAllocationIds of [[], ['allocation-one', 'allocation-two']]) {
    assert.throws(() => pure.checkedCustomerFailedApplyRetryReceipt(review, { ...receipt, retainedAllocationIds }), /unconfirmed/)
  }
  assert.deepEqual(pure.checkedCustomerFailedApplyRetryReceipt(review, receipt).retainedAllocationIds, ['same-allocation'])
})

test('UNKNOWN hold stays anchored across actor/offer/target/release/revision changes and differs from prepared recovery', () => {
  const anchor = pure.failedApplyRetryHoldScope(scope)
  assert.equal(anchor, pure.failedApplyRetryHoldScope({ ...scope, actorId: 'other', offerId: 'other', targetProfileId: 'other', releaseId: 'other', revision: 4 }))
  assert.notEqual(anchor, JSON.stringify(['PREPARED_FAILURE_RECOVERY', scope.customerId, scope.reservationId]))
})

test('transport loss is one retry dispatch and never invokes either recovery or funding/apply API', async () => {
  const oldFetch = globalThis.fetch, calls = []
  globalThis.fetch = async url => { calls.push(url); throw new TypeError('Synthetic lost response') }
  try { await assert.rejects(api.retryCustomerFailedApply(scope.offerId, scope.deploymentId, scope.reservationId, plan.command)); assert.equal(calls.length, 1); assert.ok(calls[0].endsWith('/retry-failed-apply')) }
  finally { globalThis.fetch = oldFetch }
})
