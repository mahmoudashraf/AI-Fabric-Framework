import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url), 'utf8')
const ast = ts.createSourceFile('Console.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const names = new Set(['recoveryText', 'recoveryRevision', 'prepareCustomerFailureRecovery', 'confirmedCustomerFailureRecovery',
  'postProvisionResumeHoldScope', 'prepareCustomerPostProvisionResume', 'confirmedCustomerPostProvisionResume', 'checkedCustomerPostProvisionResumeReceipt'])
const selected = ast.statements.filter(node => ts.isFunctionDeclaration(node) ? names.has(node.name?.text)
  : ts.isVariableStatement(node) && node.declarationList.declarations.some(item => names.has(item.name.getText(ast))))
assert.equal(selected.length, names.size)
const transpile = text => ts.transpileModule(text, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const pure = await import(`data:text/javascript;base64,${Buffer.from(transpile(selected.map(node => node.getText(ast)).join('\n'))).toString('base64')}`)
const oldWindow = globalThis.window
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://resume.invalid' } }
const api = await import(`data:text/javascript;base64,${Buffer.from(transpile(await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8'))).toString('base64')}`)
globalThis.window = oldWindow
const scope = { actorId: 'owner', customerId: 'customer', authenticationMode: 'SESSION', offerId: 'offer/one', offerFingerprint: 'pin', entitlementRevision: 7,
  deploymentId: 'dep/one', targetProfileId: 'stage', reservationId: 'reservation/one', revision: 4, releaseId: 'failed-release' }
const request = { releaseId: scope.releaseId, operationKey: 'explicit-setup-key', reason: 'Reviewed remaining setup', acceptRemainingDependencyProvisioning: true }
const plan = { command: { fence: { reservationId: scope.reservationId, revision: 4, generation: 'server-generation' }, ...request, proofFingerprint: 'b'.repeat(64) },
  boundary: 'COOLIFY_DEPENDENCY_ADMISSION_FAILED_AFTER_PROVISIONING_V1', remainingPhases: ['managed_dependencies', 'source_sync', 'post_apply_verification'],
  runtimeRedeployRequired: false, retainedAllocationIds: ['retained-allocation'] }
const receipt = { receiptId: 'resume-receipt', reservationId: scope.reservationId, generation: 'server-generation', fromRevision: 4, revision: 5,
  releaseId: scope.releaseId, status: 'APPLY_REQUESTED', financialMutationPerformed: false, runtimeRedeployRequired: false,
  retainedAllocationIds: ['retained-allocation'], authorizedAt: '2026-10-20T22:00:00Z' }

test('normal owner resume API posts consent and exact returned command without startup-retry fallback', async () => {
  const oldFetch = globalThis.fetch, calls = []
  globalThis.fetch = async (url, init) => { calls.push({ url, init }); return new Response(JSON.stringify(calls.length === 1 ? plan : receipt), { headers: { 'Content-Type': 'application/json' } }) }
  try {
    const response = await api.planCustomerPostProvisionResume(scope.offerId, scope.deploymentId, scope.reservationId, request)
    const reviewed = pure.prepareCustomerPostProvisionResume(scope, request, response)
    const command = pure.confirmedCustomerPostProvisionResume(scope, reviewed, true)
    assert.deepEqual(pure.checkedCustomerPostProvisionResumeReceipt(reviewed, await api.resumeCustomerPostProvisioning(scope.offerId, scope.deploymentId, scope.reservationId, command)), receipt)
    const base = 'https://resume.invalid/api/customer-self-service/catalogue/offer%2Fone/deployments/dep%2Fone/admission-reservations/reservation%2Fone'
    assert.deepEqual(calls.map(call => call.url), [`${base}/post-provision-resume-plan`, `${base}/resume-post-provisioning`])
    assert.equal(calls[0].init.body, JSON.stringify(request)); assert.equal(calls[1].init.body, JSON.stringify(response.command))
    for (const { init } of calls) { assert.equal(init.method, 'POST'); assert.equal(init.credentials, 'include'); assert.equal(init.headers['X-PLATFORM-API-KEY'], undefined) }
    assert.equal('fence' in request, false); assert.equal('proofFingerprint' in request, false)
  } finally { globalThis.fetch = oldFetch }
})

test('plan requires exact boundary, consent, no-runtime-redeploy flag and all ordered remaining phases', () => {
  for (const mutate of [p => { p.boundary = 'INITIAL_COOLIFY_CONNECTOR_STARTUP_FAILURE_V1' }, p => { p.boundary = '' },
    p => { p.command.acceptRemainingDependencyProvisioning = false }, p => { p.runtimeRedeployRequired = true },
    p => { delete p.runtimeRedeployRequired }, p => { p.remainingPhases = ['managed_dependencies'] },
    p => { p.remainingPhases.reverse() }, p => { p.remainingPhases.push('runtime_redeploy') }]) {
    const changed = structuredClone(plan); mutate(changed); assert.throws(() => pure.prepareCustomerPostProvisionResume(scope, request, changed))
  }
  assert.throws(() => pure.prepareCustomerPostProvisionResume(scope, { ...request, acceptRemainingDependencyProvisioning: false }, plan))
})

test('plan must retain exactly one nonblank allocation', () => {
  for (const retainedAllocationIds of [null, [], [''], ['one', 'two']]) assert.throws(() => pure.prepareCustomerPostProvisionResume(scope, request, { ...plan, retainedAllocationIds }))
})

test('confirmation binds the exact actor/offer/D/R/release/fence and unchanged command', () => {
  const reviewed = pure.prepareCustomerPostProvisionResume(scope, request, plan)
  assert.throws(() => pure.confirmedCustomerPostProvisionResume(scope, reviewed, false))
  for (const [key, value] of [['actorId', 'other'], ['authenticationMode', 'API_KEY'], ['customerId', 'foreign'], ['offerId', 'other'],
    ['offerFingerprint', 'changed'], ['entitlementRevision', 8], ['deploymentId', 'other'], ['targetProfileId', 'other'], ['reservationId', 'other'], ['revision', 5], ['releaseId', 'other']]) {
    assert.throws(() => pure.confirmedCustomerPostProvisionResume({ ...scope, [key]: value }, reviewed, true))
  }
  const changed = structuredClone(reviewed); changed.plan.command.proofFingerprint = 'c'.repeat(64)
  assert.throws(() => pure.confirmedCustomerPostProvisionResume(scope, changed, true))
  const drift = structuredClone(plan); drift.command.fence.revision++
  assert.throws(() => pure.prepareCustomerPostProvisionResume(scope, request, drift))
})

test('summary requires receipt identity, authorization status, false effect flags and exact retained allocation', () => {
  const reviewed = pure.prepareCustomerPostProvisionResume(scope, request, plan)
  for (const changed of [null, {}, { ...receipt, receiptId: '' }, { ...receipt, runtimeRedeployRequired: true },
    { ...receipt, financialMutationPerformed: true }, { ...receipt, releaseId: 'other' }, { ...receipt, generation: 'other' },
    { ...receipt, reservationId: 'other' }, { ...receipt, revision: 4 }, { ...receipt, status: 'APPLIED_VERIFIED' },
    { ...receipt, retainedAllocationIds: [] }, { ...receipt, retainedAllocationIds: ['other'] }, { ...receipt, authorizedAt: 'invalid' }]) {
    assert.throws(() => pure.checkedCustomerPostProvisionResumeReceipt(reviewed, changed), /unconfirmed/)
  }
})

test('server response is cloned, so later caller changes cannot alter review or returned command', () => {
  const response = structuredClone(plan), reviewed = pure.prepareCustomerPostProvisionResume(scope, request, response)
  response.command.proofFingerprint = 'd'.repeat(64); response.remainingPhases.length = 0
  assert.deepEqual(reviewed.plan, plan)
})

test('UNKNOWN resume intent anchor survives actor/offer/target/release/revision drift and is separate from redeploy', () => {
  const anchor = pure.postProvisionResumeHoldScope(scope)
  assert.equal(anchor, pure.postProvisionResumeHoldScope({ ...scope, actorId: 'other', offerId: 'other', targetProfileId: 'other', releaseId: 'other', revision: 5 }))
  assert.notEqual(anchor, JSON.stringify(['FAILED_APPLY_RETRY', scope.customerId, scope.reservationId]))
})

test('lost response performs one resume dispatch and no retry or recovery fallback', async () => {
  const oldFetch = globalThis.fetch, calls = []
  globalThis.fetch = async url => { calls.push(url); throw new TypeError('Synthetic lost response') }
  try { await assert.rejects(api.resumeCustomerPostProvisioning(scope.offerId, scope.deploymentId, scope.reservationId, plan.command)); assert.equal(calls.length, 1); assert.ok(calls[0].endsWith('/resume-post-provisioning')) }
  finally { globalThis.fetch = oldFetch }
})
