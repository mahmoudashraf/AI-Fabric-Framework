import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { StaticRouter } from 'react-router-dom'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const exports = {}
  new Function('require', 'exports', ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const navigation = await load('../src/brain/brainNavigation.ts')
const redirect = await load('../src/components/customer/CustomerReleaseSetupRedirect.tsx', {
  '../../brain/brainNavigation': navigation, '../../api/platformApi': {},
})
const stage = { id: 'stage/exact', active: true, environmentName: 'staging', name: 'Staging' }
const prod = { id: 'prod/exact', active: true, environmentName: 'production', name: 'Production' }

test('legacy staging resolves only its authorized bootstrap destination', () => {
  assert.equal(redirect.customerReleaseSetupPath('brain/a', 'staging', stage.id, [stage, prod]),
    '/smart-brains/brain%2Fa/updates?targetProfileId=stage%2Fexact')
  for (const targets of [[], [prod], [{ ...stage, active: false }], [{ ...stage, id: 'other-stage' }]]) {
    assert.equal(redirect.customerReleaseSetupPath('brain/a', 'staging', stage.id, targets), null)
  }
})

test('legacy production opens explicit promotion, never a generic apply or guessed target', () => {
  assert.equal(redirect.customerReleaseSetupPath('brain', 'production', stage.id, [stage, prod]),
    '/smart-brains/brain/updates?targetProfileId=prod%2Fexact&panel=promotion')
  assert.equal(redirect.customerReleaseSetupPath('brain', 'production', stage.id, [stage, prod, { ...prod, id: 'other' }]), null)
  assert.equal(redirect.customerReleaseSetupPath('brain', 'retention', stage.id, [stage, prod]), null)
})

test('focused release tasks reuse existing ownership, storage, assignment and practical promotion contracts', async () => {
  const source = await readFile(new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url), 'utf8')
  for (const contract of ['CustomerExternalIntegrationBindings', 'CustomerAiStateStorageBinding',
    'CustomerReleaseVerificationButton', 'planDeploymentPracticalPromotion', 'requestDeploymentPracticalProductionApply',
    'activateDeploymentPracticalProductionConsumer', 'hasExactStagingAssignment', 'goLiveReadiness?.activationReady']) assert.ok(source.includes(contract), contract)
  assert.ok(source.includes('selectedDeploymentId !== releaseWorkspace.brainId'))
  assert.ok(source.includes('stagingTargetProfileId !== exactTarget.id'))
  assert.ok(source.includes("releaseWorkspace.section === 'promotion'"))
  assert.ok(source.includes('productionTargets.find(target => target.id === releaseWorkspace.targetProfileId)'))
  assert.ok(source.includes('if (releaseWorkspace || !selectedDeploymentId'))
})

test('Try your AI requires verified evidence and names the exact release; promotion remains explicit', async () => {
  const updates = await readFile(new URL('../src/brain/BrainUpdatesPage.tsx', import.meta.url), 'utf8')
  const tryPage = await readFile(new URL('../src/brain/BrainTryPage.tsx', import.meta.url), 'utf8')
  assert.ok(updates.includes("latestRelease?.status === 'APPLIED_VERIFIED' && latestRelease.verificationStatus === 'PASSED'"))
  assert.ok(updates.includes('releaseId=${encodeURIComponent(latestRelease.id)}'))
  assert.ok(updates.includes('&panel=promotion'))
  assert.ok(tryPage.includes("app.status === 'ACTIVE' && app.ready && app.releaseId === release?.id"))
  assert.ok(tryPage.includes('No active standalone Engage app is assigned to this exact release'))
  assert.ok(!updates.includes('setView('), 'tab content must follow guarded URL navigation, not discard dirty forms before navigation succeeds')
})

test('connection forms inherit the exact workspace destination and retain legacy selectors elsewhere', async () => {
  const query = { useMutation: () => ({ isPending: false }), useQuery: () => ({}), useQueryClient: () => ({}) }
  const external = await load('../src/components/customer/CustomerExternalIntegrationBindings.tsx', {
    '../../api/platformApi': {}, './customerExternalIntegration': { externalConnectionProfile: () => ({ secretFields: [] }) },
    '@tanstack/react-query': query,
  })
  const storage = await load('../src/components/customer/CustomerAiStateStorageBinding.tsx', {
    '../../api/platformApi': {}, './customerAiStateStorage': { customerAiStateSlot: () => true }, '@tanstack/react-query': query,
  })
  const props = { session: {}, actorId: 'actor', customerId: 'customer',
    offer: { id: 'offer', entitlement: {}, targets: [{ id: 'stage', capabilities: ['READ', 'BINDING_REPLACE'] }],
      bindingSlots: [{ id: 'source', kind: 'EXTERNAL_SERVICE' }], externalIntegrationProfiles: [] },
    deployment: { deploymentId: 'brain', bootstrapTargetProfileId: 'stage' }, version: { id: 'v1' } }
  const render = (component, extra) => renderToStaticMarkup(React.createElement(StaticRouter, null, React.createElement(component, { ...props, ...extra })))
  assert.match(render(external.CustomerExternalIntegrationBindings), /Connection target/)
  assert.doesNotMatch(render(external.CustomerExternalIntegrationBindings, { targetProfileId: 'stage' }), /Connection target/)
  assert.match(render(storage.CustomerAiStateStorageBinding), /AI-state target/)
  assert.doesNotMatch(render(storage.CustomerAiStateStorageBinding, { targetProfileId: 'stage' }), /AI-state target/)
  const invalid = render(storage.CustomerAiStateStorageBinding, { targetProfileId: 'unavailable' })
  assert.match(invalid, /no entitled AI-state binding target/)
  assert.doesNotMatch(invalid, /PostgreSQL password/)
})
