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
  const code = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const terminology = await load('../src/deploymentTerminology.ts')
const navigation = await load('../src/brain/brainNavigation.ts')
const presentation = await load('../src/components/customer/CustomerBrainSetupPresentation.tsx', {
  '../../deploymentTerminology': terminology, '../../brain/brainNavigation': navigation,
})
const offer = { id: 'offer-1', name: 'Approved knowledge workspace', bindingSlots: [{ kind: 'CUSTOMER_S3' }], workspaceProfiles: [{}] }
const deployment = { deploymentId: 'dep/a & b', name: 'Customer support', bootstrapTargetProfileId: 'staging/a', status: 'DRAFT' }
const props = {
  deployment: null, offer, offers: [offer], selectedOfferId: offer.id,
  onOffer() {}, onContinue() {}, onCreateAnother() {}, onCreate() {},
  creationFields: React.createElement('input', { 'aria-label': 'Creation-only field' }),
  creationNotices: null, creationDetails: React.createElement('span', null, 'Exact template reference'),
  canCreate: false, creating: false,
}
const render = (Component, values) => renderToStaticMarkup(React.createElement(StaticRouter, null, React.createElement(Component, values)))

test('an existing Brain continues setup without rendering another creation form', () => {
  const html = render(presentation.CustomerBrainStartingPoint, { ...props, deployment })
  assert.match(html, /Your starting point is saved/)
  assert.match(html, /Customer support/)
  assert.match(html, /Continue setup/)
  assert.match(html, /Create another/)
  assert.doesNotMatch(html, /Creation-only field|Create my setup|Available starting templates/)
  assert.match(html, /\/smart-brains\/dep%2Fa%20%26%20b\?targetProfileId=staging%2Fa/)
  assert.doesNotMatch(html, /Running v|Live draft/)
})

test('a new Brain has one deliberate creation command and keeps its disabled state', () => {
  const html = render(presentation.CustomerBrainStartingPoint, props)
  assert.match(html, /Choose your starting point/)
  assert.match(html, /Creation-only field/)
  assert.match(html, /aria-pressed="true"/)
  assert.match(html, /disabled=""[^>]*>Create my setup/)
  assert.match(html, /Nothing switches your live app automatically/)
  assert.doesNotMatch(html, /Continue setup|Open smart brain/)
  const enabled = render(presentation.CustomerBrainStartingPoint, { ...props, canCreate: true })
  assert.doesNotMatch(enabled, /disabled=""[^>]*>Create my setup/)
})

test('outcomes follow declared template capabilities, not names or domain keywords', () => {
  assert.deepEqual(presentation.setupTemplateOutcomes(offer).map(item => item.id), ['documents', 'workspace'])
  assert.deepEqual(presentation.setupTemplateOutcomes({ name: 'Dealership with documents', bindingSlots: [], workspaceProfiles: [] }), [])
  assert.deepEqual(presentation.setupTemplateOutcomes({ bindingSlots: [{ kind: 'EXTERNAL_SERVICE' }], workspaceProfiles: [] }).map(item => item.id), ['systems'])
  assert.deepEqual(presentation.setupTemplateOutcomes(null), [])
})

test('missing template details never claim installed capabilities or hide the existing Brain', () => {
  const html = render(presentation.CustomerBrainStartingPoint, { ...props, offer: null, deployment })
  assert.match(html, /Starting template details are unavailable/)
  assert.match(html, /Continue setup/)
  assert.doesNotMatch(html, /Answers from your knowledge|Connected to your business/)
})

test('setup stages preserve existing URL IDs, and future steps require a created Brain', () => {
  assert.deepEqual(presentation.customerSetupStages.map(item => item.id), ['template', 'configure', 'staging', 'production', 'retention'])
  const html = render(presentation.CustomerBrainSetupHeader, { step: 'template', hasDeployment: false, onStep() {} })
  assert.match(html, /aria-current="step"/)
  assert.equal((html.match(/disabled=""/g) ?? []).length, 4)
  assert.match(html, /Connect your business/)
  assert.match(html, /Prepare staging/)
  assert.match(html, /Go live/)
})

test('setup uses one depth disclosure without changing other customer editors', async () => {
  const sections = await readFile(new URL('../src/components/customer/CustomerDetailSections.tsx', import.meta.url), 'utf8')
  const resourcePage = await readFile(new URL('../src/pages/CustomerResourcesPage.tsx', import.meta.url), 'utf8')
  const consoleSource = await readFile(new URL('../src/components/customer/CustomerDeploymentConsole.tsx', import.meta.url), 'utf8')
  assert.ok(sections.includes('showDepthSwitch = true'))
  assert.ok(resourcePage.includes('showDepthSwitch={false}'))
  assert.ok(consoleSource.includes('Technical setup & resource limits'))
  assert.ok(consoleSource.includes("step === 'template' && (!requestedDeploymentId || selectedDeployment)"))
  assert.ok(consoleSource.includes('bootstrapCustomerDeployment(offer.id, customerStorageBootstrapRequest'))
  assert.ok(consoleSource.includes('creationGuard.capture()'))
  assert.ok(consoleSource.includes('setSelectedOfferId(requestedOfferId)'))
  assert.ok(consoleSource.includes('setSelectedOfferId(selectedOffer?.id'))
})
