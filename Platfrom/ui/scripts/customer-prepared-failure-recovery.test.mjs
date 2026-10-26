import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'

const source = await readFile(new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url), 'utf8')
const ast = ts.createSourceFile('Console.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const names = new Set(['recoveryText', 'recoveryRevision', 'preparedFailureRecoveryHoldScope',
  'prepareCustomerFailureRecovery', 'confirmedCustomerFailureRecovery', 'checkedCustomerFailureRecoveryReceipt'])
const selected = ast.statements.filter(node => ts.isFunctionDeclaration(node) ? names.has(node.name?.text)
  : ts.isVariableStatement(node) && node.declarationList.declarations.some(item => names.has(item.name.getText(ast))))
assert.equal(selected.length, names.size)
const transpile = text => ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const pure = await import(`data:text/javascript;base64,${Buffer.from(transpile(withCustomerSessionSupport(selected.map(node => node.getText(ast)).join('\n')))).toString('base64')}`)
const apiSource = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
const previousWindow = globalThis.window
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://recovery.invalid' } }
const api = await import(`data:text/javascript;base64,${Buffer.from(transpile(apiSource)).toString('base64')}`)
globalThis.window = previousWindow
const scope = { actorId: 'owner', customerId: 'customer', authenticationMode: 'SESSION', offerId: 'offer/one', offerFingerprint: 'pin', entitlementRevision: 7,
  deploymentId: 'dep/one', targetProfileId: 'stage', reservationId: 'reservation/one', revision: 3, releaseId: 'failed-release' }
const request = { releaseId: scope.releaseId, operationKey: 'reviewed-key', reason: 'Review retained preparation' }
const plan = { command: { fence: { reservationId: scope.reservationId, revision: 3, generation: 'server-only-generation' },
  ...request, proofFingerprint: 'a'.repeat(64) }, boundary: 'SERVER_REVIEWED_BOUNDARY_V1' }
const receipt = { receiptId: 'receipt', reservationId: scope.reservationId, generation: 'server-only-generation', fromRevision: 3, revision: 4,
  releaseId: scope.releaseId, state: 'RESERVED', retainedAllocationIds: ['retained-allowance'], providerRetryPerformed: false,
  financialMutationPerformed: false, recoveredAt: '2026-10-20T20:00:00Z' }

test('actual API normal owner routes preserve exact returned command and never create funding/apply commands', async () => {
  const oldFetch = globalThis.fetch, calls = []
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init }); return new Response(JSON.stringify(calls.length === 1 ? plan : receipt), { headers: { 'Content-Type': 'application/json' } })
  }
  try {
    const response = await api.planCustomerPreparedFailureRecovery(scope.offerId, scope.deploymentId, scope.reservationId, request)
    const review = pure.prepareCustomerFailureRecovery(scope, request, response)
    const command = pure.confirmedCustomerFailureRecovery(scope, review, true)
    const result = await api.recoverCustomerPreparedFailure(scope.offerId, scope.deploymentId, scope.reservationId, command)
    assert.deepEqual(pure.checkedCustomerFailureRecoveryReceipt(review, result), receipt)
    const base = 'https://recovery.invalid/api/customer-self-service/catalogue/offer%2Fone/deployments/dep%2Fone/admission-reservations/reservation%2Fone'
    assert.deepEqual(calls.map(call => call.url), [`${base}/prepared-failure-recovery-plan`, `${base}/recover-prepared-failure`])
    assert.equal(calls[0].init.body, JSON.stringify(request)); assert.equal(calls[1].init.body, JSON.stringify(response.command))
    for (const { init } of calls) { assert.equal(init.method, 'POST'); assert.equal(init.credentials, 'include'); assert.equal(init.headers['X-PLATFORM-API-KEY'], undefined) }
    assert.equal('generation' in request, false); assert.equal('proofFingerprint' in request, false)
  } finally { globalThis.fetch = oldFetch }
})

test('scope/fence/release/key/reason/proof drift cannot become a reviewed plan', () => {
  for (const change of [p => { p.command.fence.reservationId = 'other' }, p => { p.command.fence.revision++ },
    p => { p.command.releaseId = 'other' }, p => { p.command.operationKey = 'other' }, p => { p.command.reason = 'other' },
    p => { p.command.fence.generation = '' }, p => { p.command.proofFingerprint = 'invalid' }, p => { p.boundary = '' }]) {
    const changed = structuredClone(plan); change(changed); assert.throws(() => pure.prepareCustomerFailureRecovery(scope, request, changed), /does not match/)
  }
  assert.throws(() => pure.prepareCustomerFailureRecovery({ ...scope, authenticationMode: 'API_KEY' }, request, plan))
})

test('explicit confirmation is tied to actor/offer/D/R/release and exact unchanged server command', () => {
  const review = pure.prepareCustomerFailureRecovery(scope, request, plan)
  assert.throws(() => pure.confirmedCustomerFailureRecovery(scope, review, false), /Confirm/)
  for (const [key, value] of [['actorId', 'other'], ['offerId', 'other'], ['deploymentId', 'other'], ['reservationId', 'other'],
    ['releaseId', 'other'], ['targetProfileId', 'other'], ['entitlementRevision', 8], ['revision', 4]]) {
    assert.throws(() => pure.confirmedCustomerFailureRecovery({ ...scope, [key]: value }, review, true), /Confirm/)
  }
  const changed = structuredClone(review); changed.plan.command.proofFingerprint = 'b'.repeat(64)
  assert.throws(() => pure.confirmedCustomerFailureRecovery(scope, changed, true), /Confirm/)
  assert.deepEqual(plan, review.plan, 'preparation clones rather than alters server response')
})

test('success requires actual matching receipt with RESERVED and explicit false effect flags', () => {
  const review = pure.prepareCustomerFailureRecovery(scope, request, plan)
  for (const changed of [null, {}, { ...receipt, receiptId: '' }, { ...receipt, revision: 3 }, { ...receipt, state: 'ACTIVE' },
    { ...receipt, generation: 'other' }, { ...receipt, releaseId: 'other' }, { ...receipt, providerRetryPerformed: true },
    { ...receipt, financialMutationPerformed: true }, { ...receipt, recoveredAt: 'unknown' },
    { ...receipt, retainedAllocationIds: ['duplicate', 'duplicate'] }]) {
    assert.throws(() => pure.checkedCustomerFailureRecoveryReceipt(review, changed), /unconfirmed/)
  }
})

test('durable UNKNOWN anchor cannot be escaped through actor/offer/target/revision/release changes', () => {
  const key = pure.preparedFailureRecoveryHoldScope(scope)
  assert.equal(key, pure.preparedFailureRecoveryHoldScope({ ...scope, actorId: 'other', offerId: 'other', targetProfileId: 'other', revision: 4, releaseId: 'other' }))
  assert.notEqual(key, pure.preparedFailureRecoveryHoldScope({ ...scope, reservationId: 'another-reservation' }))
})

test('transport loss is one normal dispatch; no automatic recovery or apply replay', async () => {
  const oldFetch = globalThis.fetch
  let calls = 0
  globalThis.fetch = async () => { calls++; throw new TypeError('Synthetic lost response') }
  try { await assert.rejects(api.recoverCustomerPreparedFailure(scope.offerId, scope.deploymentId, scope.reservationId, plan.command)); assert.equal(calls, 1) }
  finally { globalThis.fetch = oldFetch }
})
