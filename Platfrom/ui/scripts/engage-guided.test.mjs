import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/engagePresentation.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { engageState, engageNextStep, engageCheckStep, engageStepPath } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
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
    assert.equal(url.searchParams.get(step === 'connection' ? 'connectInstallationId' : step === 'appearance' ? 'previewInstallationId' : 'resourceId'), 'app-a')
    if (step === 'delivery' || step === 'readiness') assert.equal(url.searchParams.get('appSection'), step)
  }
})
