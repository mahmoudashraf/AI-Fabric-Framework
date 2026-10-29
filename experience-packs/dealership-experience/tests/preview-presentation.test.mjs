import assert from 'node:assert/strict'
import test from 'node:test'

globalThis.window = { location: { pathname: '/' }, dispatchEvent() {} }
globalThis.document = { querySelector() { return null } }
globalThis.CustomEvent ??= class { constructor(type, options) { this.type = type; this.detail = options?.detail } }
const { mountInstallation, resolveDealershipPresentation } = await import('../dist/dealership-experience.esm.js')
const configuration = () => ({ dealer: { id: 'sample', assistantLabel: 'Assistant' }, page: { kind: 'inventory', rootSelector: 'main', contextLabel: 'Current page' } })

test('live mount and preview use identical resolved tools, rail, copy and theme', async () => {
  const config = configuration(), expected = resolveDealershipPresentation(config)
  let mounted
  const controller = await mountInstallation({ configuration: config, deliveryMode: 'embedded', widgetConfig: { apiConfig: {} },
    maxMode: { init(value) { mounted = value }, open() {}, destroy() {} }, refreshAssignment: async () => {} })
  assert.deepEqual(mounted.host.toolGroups, expected.toolGroups)
  assert.deepEqual(mounted.host.toolRail, expected.toolRail)
  assert.deepEqual(mounted.theme, expected.theme)
  assert.equal(mounted.host.welcomeMessage, expected.copy.welcomeMessage)
  assert.deepEqual(mounted.host.starterSuggestions, expected.copy.starterSuggestions)
  controller.destroy()
})
test('explicit empty tools, rail, copy and suggestions remain empty through live mounting', async () => {
  const config = { ...configuration(), toolGroups: { initialScope: 'default', default: { label: 'Browse', tools: [] }, contextual: { label: 'Context', tools: [] } },
    toolRail: { items: [], initiallyCollapsed: true }, copy: { welcomeMessage: '', starterSuggestions: [], placeholder: '' } }
  let mounted
  const controller = await mountInstallation({ configuration: config, deliveryMode: 'embedded', widgetConfig: { apiConfig: {} },
    maxMode: { init(value) { mounted = value }, open() {}, destroy() {} }, refreshAssignment: async () => {} })
  assert.deepEqual(mounted.host.toolGroups, config.toolGroups)
  assert.deepEqual(mounted.host.toolRail.items, [])
  assert.equal(mounted.host.welcomeMessage, '')
  assert.equal(mounted.host.companionPlaceholder, '')
  assert.deepEqual(mounted.host.starterSuggestions, [])
  controller.destroy()
})
test('detail-context defaults and supported result kinds follow configured capabilities', () => {
  const config = configuration(); config.page.kind = 'vehicle-detail'; config.page.subjectLabel = 'Sample subject'
  config.capabilities = { comparison: false, callback: true }
  const output = resolveDealershipPresentation(config)
  assert.equal(output.toolGroups.initialScope, 'contextual')
  assert.equal(output.toolGroups.contextual.availableWithoutAttachments, true)
  assert.equal(output.toolGroups.contextual.contextLabel, 'Sample subject')
  assert.equal(output.rendererIds.includes('loomai.vehicle-comparison.v1'), false)
  assert.equal(output.rendererIds.includes('loomai.dealership-request-receipt.v1'), true)
})
