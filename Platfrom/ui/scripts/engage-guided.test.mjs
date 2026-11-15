import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/engagePresentation.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { engageState, engageNextStep, engageCheckStep, engageStepPath, normalizeLegacyEngageRoute, closeEngageToolRoute, engageConnectionInfo, engageReadinessInfo } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const app = overrides => ({ installationId: 'app-a', deploymentId: 'brain-a', releaseId: 'release-a', status: 'DRAFT', ready: false, readinessChecks: [], domains: [], ...overrides })
test('saved, active and ready are distinct presentation states', () => {
  assert.equal(engageState(app()).label, 'Setup needed')
  assert.equal(engageState(app({ ready: true })).label, 'Ready to activate')
  assert.equal(engageState(app({ status: 'ACTIVE', ready: true })).label, 'Active')
  assert.equal(engageState(app({ status: 'DISABLED', ready: true })).label, 'Disabled')
  assert.equal(engageState(app({ status: 'UNKNOWN' })).label, 'UNKNOWN')
})
test('a stale ready field cannot hide actual failed checks or domain errors', () => {
  for (const fields of [{ readinessChecks: [{ code: 'APP_DOMAIN', status: 'BLOCKED', message: 'No domain' }] }, { domains: [{ lastVerificationError: 'DNS failed' }] }]) {
    assert.equal(engageState(app({ ready: true, status: 'ACTIVE', ...fields })).label, 'Needs attention')
  }
})
test('connection, delivery and remaining readiness steps use check codes, never message interpretation', () => {
  for (const code of ['ASSIGNMENT', 'CONSUMER', 'CONNECTION_PROFILE']) assert.equal(engageCheckStep({ code, message: 'Domain failed' }), 'connection')
  for (const code of ['APP_DOMAIN', 'APP_DELIVERY', 'ORIGINS']) assert.equal(engageCheckStep({ code, message: 'Assignment failed' }), 'delivery')
  assert.equal(engageCheckStep({ code: 'NEW_PROVIDER_CHECK', message: 'Connect your Brain' }), 'readiness')
})
test('next-step guidance respects real assignment and remaining checks without executing anything', () => {
  assert.equal(engageNextStep(app({ releaseId: null })), 'connection')
  assert.equal(engageNextStep(app({ readinessChecks: [{ code: 'APP_DOMAIN', status: 'BLOCKED' }] })), 'delivery')
  assert.equal(engageNextStep(app({ readinessChecks: [{ code: 'ASSETS', status: 'BLOCKED' }] })), 'readiness')
  assert.equal(engageNextStep(app({ ready: true })), 'readiness')
  assert.equal(engageNextStep(app({ status: 'ACTIVE', ready: true })), 'appearance')
})
test('step links reuse existing editors and retain only supported origin identifiers', () => {
  const context = new URLSearchParams({ kitId: 'kit-a', fromBrainId: 'brain-a', fromBrainReleaseId: 'release-a', returnUrl: 'https://evil.test', customerId: 'foreign' })
  for (const step of ['connection', 'appearance', 'delivery', 'readiness']) {
    const url = new URL(engageStepPath(app(), step, context), 'https://console.test')
    assert.equal(url.pathname, '/ai-workspaces'); assert.equal(url.searchParams.get('kitId'), 'kit-a')
    assert.equal(url.searchParams.get('fromBrainId'), 'brain-a'); assert.equal(url.searchParams.has('returnUrl'), false)
    assert.equal(url.searchParams.has('customerId'), false)
    assert.equal(url.searchParams.get('resourceId'), 'app-a')
    assert.equal(url.searchParams.has('view'), false)
    if (step === 'connection' || step === 'appearance') assert.equal(url.searchParams.get('appTool'), step)
    if (step === 'delivery' || step === 'readiness') assert.equal(url.searchParams.get('appSection'), step)
  }
})
test('legacy customer setup URLs select a tool on the canonical app surface, not another management page', () => {
  for (const [query, action, id] of [
    ['view=manage&previewInstallationId=a&kitId=k', 'appearance', 'a'],
    ['view=manage&connectInstallationId=b', 'connection', 'b'],
    ['view=setup&sourceInstallationId=c&delivery=embedded', 'create', 'c'],
    ['view=manage', 'create', null],
    ['view=manage&connectDeploymentId=brain-a', 'connection', null],
  ]) {
    const route = normalizeLegacyEngageRoute(new URLSearchParams(query))
    assert.equal(route.has('view'), false); assert.equal(route.get('appTool'), action)
    assert.equal(route.get('resourceId'), id)
    assert.equal(route.has('previewInstallationId'), false); assert.equal(route.has('connectInstallationId'), false)
  }
  assert.equal(normalizeLegacyEngageRoute(new URLSearchParams('resourceKind=app&resourceId=a&appTool=appearance')), null)
})
test('closing setup preserves the exact selection/context and removes transient tool parameters', () => {
  const params = new URLSearchParams('resourceKind=app&resourceId=app-a&appTool=appearance&kitId=k&fromBrainId=brain-a&fromBrainReleaseId=release-a')
  const closed = closeEngageToolRoute(params)
  assert.equal(closed.get('resourceId'), 'app-a'); assert.equal(closed.get('kitId'), 'k'); assert.equal(closed.get('fromBrainReleaseId'), 'release-a')
  assert.equal(closed.has('appTool'), false)
  const created = closeEngageToolRoute(params, app({ installationId: 'new-app', deploymentId: 'other-brain' }))
  assert.equal(created.get('resourceId'), 'new-app'); assert.equal(created.has('kitId'), false); assert.equal(created.has('fromBrainId'), false)
})
const connectedApp = app({ customerId: 'customer-a', consumerId: 'consumer-a' })
const brains = [{ deploymentId: 'brain-a', name: 'Customer service Brain', environmentName: 'staging' }]
const connection = { customerId: 'customer-a', consumerId: 'consumer-a', boundDeploymentId: 'brain-a', boundReleaseId: 'release-a', boundDeploymentEnvironment: 'production' }
test('app environment comes from its exact assigned connection, not the Brain bootstrap or another app', () => {
  assert.deepEqual(engageConnectionInfo(connectedApp, brains, [connection], 'ready'), { brainLabel: 'Customer service Brain', environmentLabel: 'Production' })
  assert.equal(engageConnectionInfo(connectedApp, brains, [{ ...connection, boundDeploymentEnvironment: 'staging' }], 'ready').environmentLabel, 'Staging')
  for (const changed of [{ customerId: 'foreign' }, { consumerId: 'other' }, { boundDeploymentId: 'other' }, { boundReleaseId: 'newer-release' }, { boundDeploymentEnvironment: 'unknown' }]) {
    assert.equal(engageConnectionInfo(connectedApp, brains, [{ ...connection, ...changed }], 'ready').environmentLabel, 'Unavailable')
  }
})
test('draft, missing and failed connection reads stay distinct from a production assignment', () => {
  assert.equal(engageConnectionInfo({ ...connectedApp, releaseId: null }, brains, [connection], 'ready').environmentLabel, 'Not assigned')
  assert.equal(engageConnectionInfo({ ...connectedApp, deploymentId: null }, brains).brainLabel, 'Not connected')
  assert.equal(engageConnectionInfo(connectedApp, []).brainLabel, 'Brain unavailable')
  for (const evidence of ['loading', 'error', 'unavailable']) {
    assert.equal(engageConnectionInfo(connectedApp, brains, [connection], evidence).environmentLabel, evidence === 'loading' ? 'Loading...' : 'Unavailable')
  }
})
test('row readiness preserves the actual blocking reason and remaining issue count', () => {
  const result = engageReadinessInfo(app({ ready: true, readinessChecks: [
    { code: 'WARNING', status: 'WARNING', message: 'Review authentication' },
    { code: 'ASSIGNMENT', status: 'BLOCKED', message: 'Connect a verified release' },
  ], domains: [{ hostname: 'ai.example.test', lastVerificationError: 'HTTPS pending' }] }))
  assert.deepEqual(result, { label: 'Blocked', color: 'warning', reason: 'Connect a verified release', additional: 2 })
  assert.equal(engageReadinessInfo(app({ readinessChecks: [{ code: 'NEW', status: 'WARNING', message: 'New provider condition' }] })).reason, 'New provider condition')
})
test('domain errors and missing readiness are never presented as ready', () => {
  assert.equal(engageReadinessInfo(app({ ready: true, domains: [{ hostname: 'ai.test', lastVerificationError: 'Pending HTTPS' }] })).reason, 'ai.test: Pending HTTPS')
  assert.equal(engageReadinessInfo(app()).label, 'Not ready')
  assert.equal(engageReadinessInfo(app({ ready: true })).label, 'Ready')
})
