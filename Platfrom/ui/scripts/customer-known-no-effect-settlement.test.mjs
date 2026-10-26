import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import test from 'node:test'
import ts from 'typescript'
import { withCustomerSessionSupport } from './customer-auth-test-support.mjs'

const apiSource = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
const panelSource = withCustomerSessionSupport(await readFile(new URL('../src/components/customer/KnownNoEffectSettlementPanel.tsx', import.meta.url), 'utf8'))
const transpile = (source, module) => ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2022, module, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true,
} }).outputText
const originalWindow = globalThis.window
globalThis.window = { __PLATFORM_RUNTIME_CONFIG__: { apiBaseUrl: 'https://settlement.invalid' } }
const api = await import(`data:text/javascript;base64,${Buffer.from(transpile(apiSource, ts.ModuleKind.ESNext)).toString('base64')}`)
if (originalWindow === undefined) delete globalThis.window
else globalThis.window = originalWindow
const require = createRequire(new URL('../package.json', import.meta.url))
const holdModule = { exports: {} }
const holdSource = await readFile(new URL('../src/components/customer/durableCommandHolds.ts', import.meta.url), 'utf8')
new Function('module', 'exports', transpile(holdSource, ts.ModuleKind.CommonJS))(holdModule, holdModule.exports)
const module = { exports: {} }
new Function('require', 'module', 'exports', transpile(panelSource, ts.ModuleKind.CommonJS))(
  (name) => name === '../../api/platformApi' ? api : name === './durableCommandHolds' ? holdModule.exports : require(name), module, module.exports,
)
const { KnownNoEffectSettlementPanel, prepareKnownNoEffectSettlement, claimKnownNoEffectSettlement,
  checkedKnownNoEffectSettlementReceipt } = module.exports

const scope = { authority: { actorId: 'owner', customerId: 'customer', authenticationMode: 'SESSION' },
  offerId: 'offer/one', deploymentId: 'deployment/one', reservationId: 'reservation/one',
  targetProfileId: 'target/one', revision: 1 }
const request = { operationKey: 'settlement-stable-key', reason: 'Close the reviewed failed release' }
const draft = { installationEntityId: 'draft-workspace', expectedRowVersion: 0 }
const plan = { proofFingerprint: 'server-proof', command: {
  fence: { reservationId: scope.reservationId, revision: 1, generation: 'server-generation' },
  ...request, manifestFingerprint: 'server-manifest', versionId: 'server-version', versionConfigHash: 'server-config',
  bindingFingerprint: 'server-binding', failedReleases: [{ releaseId: 'failed-release',
    authorizationOperationKey: 'server-auth-key', authorizationRequestFingerprint: 'server-auth-fingerprint' }],
  consumerDetachments: [{ consumerEntityId: 'consumer', expectedBindingRevision: 1, draftWorkspaces: [draft] }],
} }
const receipt = { id: 'actual-server-receipt', reservationId: scope.reservationId, generation: 'server-generation',
  fromRevision: 1, revision: 2, deploymentId: scope.deploymentId, targetProfileId: scope.targetProfileId,
  outcome: 'SETTLED_KNOWN_NO_EFFECT', reclamationPerformed: false, settledAt: '2026-10-20T15:13:45.342086Z',
  failedReleaseIds: ['failed-release'], consumerDetachments: [{ consumerEntityId: 'consumer', fromBindingRevision: 1,
    toBindingRevision: 2, historyId: 'server-history', operationKey: 'server-detachment-key', preservedDraftWorkspaces: [draft] }] }

test('actual API uses only encoded customer routes and posts the unchanged returned command once', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init })
    return new Response(JSON.stringify(calls.length === 1 ? plan : receipt), { headers: { 'Content-Type': 'application/json' } })
  }
  try {
    const response = await api.planCustomerKnownNoEffectSettlement(scope.offerId, scope.deploymentId, scope.reservationId, request)
    const prepared = prepareKnownNoEffectSettlement(scope, request, response)
    const command = claimKnownNoEffectSettlement(scope, prepared, true, new Set())
    const result = await api.settleCustomerKnownNoEffectReservation(scope.offerId, scope.deploymentId, scope.reservationId, command)
    assert.deepEqual(checkedKnownNoEffectSettlementReceipt(prepared, result), receipt)
    const base = 'https://settlement.invalid/api/customer-self-service/catalogue/offer%2Fone/deployments/deployment%2Fone/admission-reservations/reservation%2Fone'
    assert.deepEqual(calls.map((call) => call.url), [`${base}/known-no-effect-plan`, `${base}/settle-known-no-effect`])
    assert.equal(calls[0].init.body, JSON.stringify(request))
    assert.equal(calls[1].init.body, JSON.stringify(response.command))
    for (const call of calls) {
      assert.equal(call.init.method, 'POST')
      assert.equal(call.init.credentials, 'include')
      assert.equal(call.init.headers['Content-Type'], 'application/json')
      assert.equal(call.init.headers['X-PLATFORM-API-KEY'], undefined)
      assert.equal(call.init.headers['Idempotency-Key'], undefined)
    }
    assert.deepEqual(request, { operationKey: 'settlement-stable-key', reason: 'Close the reviewed failed release' })
  } finally { globalThis.fetch = originalFetch }
})

test('lost response is one POST, stays claimed, and cannot become a receipt or a retry', async () => {
  const originalFetch = globalThis.fetch
  const attempted = new Set()
  const prepared = prepareKnownNoEffectSettlement(scope, request, plan)
  const command = claimKnownNoEffectSettlement(scope, prepared, true, attempted)
  let calls = 0
  globalThis.fetch = async () => { calls += 1; throw new TypeError('Synthetic lost response') }
  try {
    await assert.rejects(api.settleCustomerKnownNoEffectReservation(scope.offerId, scope.deploymentId, scope.reservationId, command), /lost response/)
    assert.equal(calls, 1)
    assert.throws(() => claimKnownNoEffectSettlement(scope, prepared, true, attempted), /already submitted/)
    assert.throws(() => claimKnownNoEffectSettlement({ ...scope, revision: 2 },
      prepareKnownNoEffectSettlement({ ...scope, revision: 2 }, request,
        { ...plan, command: { ...plan.command, fence: { ...plan.command.fence, revision: 2 } } }), true, attempted), /already submitted/)
    assert.throws(() => checkedKnownNoEffectSettlementReceipt(prepared, undefined), /unconfirmed/)
  } finally { globalThis.fetch = originalFetch }
})

test('preview requires the server fence, unchanged request, and complete unique release and DRAFT expectations', () => {
  const prepared = prepareKnownNoEffectSettlement(scope, request, plan)
  assert.deepEqual(prepared.plan, plan)
  assert.notEqual(prepared.plan, plan)
  const invalid = [
    (p) => { delete p.command.fence.generation },
    (p) => { p.command.fence.revision = 2 },
    (p) => { p.command.fence.reservationId = 'another-reservation' },
    (p) => { p.command.reason = 'changed reason' },
    (p) => { p.command.operationKey = 'another-operation' },
    (p) => { p.command.failedReleases.push(p.command.failedReleases[0]) },
    (p) => { p.command.consumerDetachments[0].draftWorkspaces = null },
  ]
  for (const change of invalid) {
    const response = structuredClone(plan); change(response)
    assert.throws(() => prepareKnownNoEffectSettlement(scope, request, response), /does not match/)
  }
})

test('confirmation, exact scope and unchanged preview are mandatory before a single claim', () => {
  const prepared = prepareKnownNoEffectSettlement(scope, request, plan)
  const attempted = new Set()
  assert.throws(() => claimKnownNoEffectSettlement(scope, prepared, false, attempted), /Confirm/)
  for (const [name, value] of [['offerId', 'other-offer'], ['deploymentId', 'other-deployment'],
    ['reservationId', 'other-reservation'], ['targetProfileId', 'other-target'], ['revision', 2]]) {
    assert.throws(() => claimKnownNoEffectSettlement({ ...scope, [name]: value }, prepared, true, attempted), /Confirm/)
  }
  assert.equal(attempted.size, 0)
  const changed = prepareKnownNoEffectSettlement(scope, request, plan)
  changed.plan.command.consumerDetachments = []
  assert.throws(() => claimKnownNoEffectSettlement(scope, changed, true, attempted), /Confirm/)
  assert.equal(claimKnownNoEffectSettlement(scope, prepared, true, attempted), prepared.plan.command)
  assert.throws(() => claimKnownNoEffectSettlement(scope, prepared, true, attempted), /already submitted/)
})

test('only the matching complete server receipt permits success', () => {
  const prepared = prepareKnownNoEffectSettlement(scope, request, plan)
  assert.equal(checkedKnownNoEffectSettlementReceipt(prepared, receipt), receipt)
  const invalid = [
    {}, { ...receipt, id: '' }, { ...receipt, outcome: 'OUTCOME_UNKNOWN' }, { ...receipt, reclamationPerformed: true },
    { ...receipt, targetProfileId: 'other-target' }, { ...receipt, generation: 'other-generation' },
    { ...receipt, revision: 3 }, { ...receipt, failedReleaseIds: [] },
    { ...receipt, consumerDetachments: [{ ...receipt.consumerDetachments[0], toBindingRevision: 3 }] },
    { ...receipt, consumerDetachments: [{ ...receipt.consumerDetachments[0], preservedDraftWorkspaces: [] }] },
  ]
  for (const response of invalid) assert.throws(() => checkedKnownNoEffectSettlementReceipt(prepared, response), /unconfirmed/)
})

test('standalone panel is inert until explicit preview and retains captured old-scope invalidation', () => {
  const { createElement } = require('react')
  const { renderToStaticMarkup } = require('react-dom/server')
  const { QueryClient, QueryClientProvider } = require('@tanstack/react-query')
  const props = { authority: scope.authority, targetProfileId: scope.targetProfileId,
    offerId: scope.offerId, deploymentId: scope.deploymentId, candidateFailed: true, onSettled: () => {},
    reservation: { id: scope.reservationId, deploymentId: scope.deploymentId, targetProfileId: scope.targetProfileId,
      revision: 1, state: 'RESERVED', intent: 'CUSTOMER_STAGING', expiryState: 'CURRENT', expiresAt: null, keepUntil: null, operation: 'APPLY' } }
  const originalFetch = globalThis.fetch
  let calls = 0
  globalThis.fetch = () => { calls += 1; throw new Error('Unexpected render request') }
  const client = new QueryClient()
  try {
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(KnownNoEffectSettlementPanel, props)))
    assert.match(html, /Failed release settlement/)
    assert.match(html, /Preview settlement/)
    assert.doesNotMatch(html, /severity="success"/)
    assert.equal(renderToStaticMarkup(createElement(KnownNoEffectSettlementPanel, { ...props, candidateFailed: false })), '')
    assert.equal(calls, 0)
    assert.match(panelSource, /SettlementReview key=\{scopeKey\}/)
    assert.match(panelSource, /mounted\.current && isCurrent\(\)/)
    assert.match(panelSource, /queryKey: \['customer-admission-reservations', scope\.offerId,\s*scope\.deploymentId, scope\.targetProfileId\], exact: true/)
    assert.match(panelSource, /await onSettled\(accepted, structuredClone\(scope\)\)/)
    assert.doesNotMatch(panelSource, /useMutation|retry:|setTimeout|setInterval|useQuery\(/)
  } finally { client.clear(); globalThis.fetch = originalFetch }
})

test('review confirmation cannot cross actor, customer or authentication authority changes', () => {
  const prepared = prepareKnownNoEffectSettlement(scope, request, plan)
  for (const authority of [{ ...scope.authority, actorId: 'other-owner' },
    { ...scope.authority, customerId: 'other-customer' }, { ...scope.authority, authenticationMode: 'API_KEY' }]) {
    assert.throws(() => claimKnownNoEffectSettlement({ ...scope, authority }, prepared, true, new Set()), /Confirm/)
  }
  assert.throws(() => prepareKnownNoEffectSettlement({ ...scope, authority: { ...scope.authority, authenticationMode: null } }, request, plan), /does not match/)
})
