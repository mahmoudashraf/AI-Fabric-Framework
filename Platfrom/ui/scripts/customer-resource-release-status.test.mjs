import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withDeploymentTerminology } from './deployment-terminology-test-support.mjs'

const source = await readFile(new URL('../src/components/customer/customerResourceModels.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(withDeploymentTerminology(source), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { deploymentResource, appResource, resourceReleaseSelection, releaseIssues } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const deployment = { deploymentId: 'dep-owned', name: 'Owned deployment', status: 'DRAFT', environmentName: 'Staging' }
const app = (releaseId, overrides = {}) => ({ installationId: 'app-owned', deploymentId: deployment.deploymentId, status: 'ACTIVE', releaseId, ...overrides })
const release = (id, overrides = {}) => ({ id, deploymentId: deployment.deploymentId, status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', ...overrides })

test('configuration state is not presented as the state of a connected app or as verified runtime health', () => {
  const model = deploymentResource(deployment, undefined, [app('current')])
  assert.equal(model.status, 'App connected')
  assert.equal(model.severity, 'info')
  assert.ok(model.facts.some(([key, value]) => key === 'Configuration state' && value === 'DRAFT'))
})
test('disabled, unassigned and foreign apps do not imply a live deployment assignment', () => {
  for (const candidate of [app('current', { status: 'DISABLED' }), app(null), app('current', { deploymentId: 'foreign' })]) {
    assert.equal(deploymentResource(deployment, undefined, [candidate]).status, 'Draft configuration')
  }
})
test('both assigned targets are visible while old failed releases stay in history', () => {
  const current = release('production'), staging = release('staging')
  const old = release('old', { verificationStatus: 'FAILED', errorMessage: 'Old failure' })
  const selection = resourceReleaseSelection(deploymentResource(deployment), [old, current, staging], [app('production'), app('staging', { installationId: 'stage-app' })], null)
  assert.deepEqual(selection.visible.map(item => item.id), ['production', 'staging'])
  assert.deepEqual(selection.history.map(item => item.id), ['old'])
  assert.deepEqual(selection.visible.flatMap(releaseIssues), [])
  assert.equal(selection.history.flatMap(releaseIssues).length, 2)
})
test('unavailable assigned release never falls back to a different healthy release', () => {
  const selection = resourceReleaseSelection(deploymentResource(deployment), [release('unrelated')], [app('missing')], null)
  assert.deepEqual(selection.visible, [])
  assert.deepEqual(selection.missingAssignmentIds, ['missing'])
})
test('explicit selection retains a failed historical release and rejects a foreign one', () => {
  const resource = deploymentResource(deployment)
  const releases = [release('current'), release('old', { verificationStatus: 'FAILED' }), release('foreign', { deploymentId: 'other' })]
  assert.deepEqual(resourceReleaseSelection(resource, releases, [app('current')], 'old').visible.map(item => item.id), ['old'])
  assert.deepEqual(resourceReleaseSelection(resource, releases, [app('current')], 'foreign').visible, [])
})
test('app inspector uses only that exact app assignment, not another app on the deployment', () => {
  const resource = { kind: 'app', id: 'app-owned', deploymentId: deployment.deploymentId }
  const selection = resourceReleaseSelection(resource, [release('current'), release('other-app')], [app('current'), app('other-app', { installationId: 'another' })], null)
  assert.deepEqual(selection.visible.map(item => item.id), ['current'])
})
test('setup without any active assignment retains target-release visibility', () => {
  const selection = resourceReleaseSelection(deploymentResource(deployment), [release('first')], [], null)
  assert.deepEqual(selection.visible.map(item => item.id), ['first'])
})
test('Engage details retain allowed websites for both delivery modes and raw connection references', () => {
  for (const deliveryMode of ['standalone', 'embedded']) {
    const result = appResource({ ...app('current'), consumerId: 'consumer-owned', displayName: 'Customer workspace', deliveryMode,
      domains: [], readinessChecks: [], allowedOrigins: ['https://company.test'], appUrl: 'https://ai.company.test',
      connectionMode: 'public-runtime-authenticated', connectionProfileCode: 'authenticated', connectionProfileVersion: '1.0', interactionPolicy: 'login-required' })
    assert.ok(result.facts.some(([key, value]) => key === 'Allowed websites' && value === 'https://company.test'))
    assert.ok(result.facts.some(([key, value]) => key === 'Connection' && value === 'public-runtime-authenticated'))
    assert.ok(result.facts.some(([key, value]) => key === 'Visitor access' && value === 'login-required'))
    assert.ok(result.advancedFacts.some(([key, value]) => key === 'Connection ID' && value === 'consumer-owned'))
    assert.ok(result.advancedFacts.some(([key, value]) => key === 'Assigned release' && value === 'current'))
    if (deliveryMode === 'standalone') assert.ok(result.facts.some(([key, value]) => key === 'App address' && value === 'https://ai.company.test'))
  }
})
