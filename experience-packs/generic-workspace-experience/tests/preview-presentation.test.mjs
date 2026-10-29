import assert from 'node:assert/strict'
import test from 'node:test'
const { mountInstallation, resolveGenericWorkspacePresentation } = await import('../dist/generic-workspace-experience.esm.js')

test('live mounting and preview share defaults without loading customer context', async () => {
  const config = { app: { assistantLabel: 'Assistant' } }, expected = resolveGenericWorkspacePresentation(config)
  let mounted
  const controller = await mountInstallation({ configuration: config, deliveryMode: 'standalone', widgetConfig: { apiConfig: {} },
    maxMode: { init(value) { mounted = value }, open() {}, destroy() {} }, refreshAssignment: async () => {} })
  assert.deepEqual(mounted.host.toolGroups, expected.toolGroups)
  assert.deepEqual(mounted.host.toolRail, expected.toolRail)
  assert.deepEqual(mounted.theme, expected.theme)
  assert.equal(mounted.host.welcomeMessage, expected.copy.welcomeMessage)
  assert.deepEqual(mounted.host.starterSuggestions, expected.copy.starterSuggestions)
  controller.destroy()
})
test('explicit empty/disabled configuration never restores pack defaults', async () => {
  const config = { app: { assistantLabel: 'Assistant' }, capabilities: { currentPage: false },
    toolGroups: { default: { label: 'Explore', tools: [] }, contextual: { label: 'Context', tools: [] } }, toolRail: { items: [] }, copy: { welcomeMessage: '', placeholder: '', starterSuggestions: [] } }
  let mounted
  const controller = await mountInstallation({ configuration: config, deliveryMode: 'embedded', widgetConfig: { apiConfig: {} },
    maxMode: { init(value) { mounted = value }, open() {}, destroy() {} }, refreshAssignment: async () => {} })
  assert.deepEqual(mounted.host.toolGroups.default.tools, [])
  assert.deepEqual(mounted.host.toolRail.items, [])
  assert.equal(mounted.host.welcomeMessage, '')
  assert.equal(mounted.host.companionPlaceholder, '')
  assert.deepEqual(mounted.host.starterSuggestions, [])
  assert.equal(mounted.host.currentPageAttachment, undefined)
  controller.destroy()
})
