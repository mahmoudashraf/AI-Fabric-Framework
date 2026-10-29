import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test, { after } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false }, appType: 'custom' })
after(() => server.close())
const load = path => server.ssrLoadModule(fileURLToPath(new URL(path, import.meta.url)))
const { buildWorkspacePreviewConfiguration: build, resolveWorkspacePackPresentation: resolve } = await load('../src/components/customer/buildWorkspacePreviewConfiguration.ts')
const { sanitizeWorkspacePreview, publicPreviewImage } = await load('../../../max-mode-widget/src/preview/configuration.ts')
const capabilities = () => ({ history: true, attachments: true, currentPage: true, sources: true,
  authentication: { signInAvailable: true }, handover: { enabled: true, runtimeConfigured: true, userCanCancelPending: true, userCanResumeAi: true } })
const configuration = () => ({ app: { assistantLabel: 'Assistant' }, capabilities: { handover: true },
  theme: { fontFamily: 'Arial, sans-serif', borderRadius: '8px' }, page: { contextLabel: 'Article', maxChars: 1200 },
  toolGroups: { initialScope: 'contextual',
    default: { label: 'Find', icon: 'search', tools: Array.from({ length: 15 }, (_, i) => ({ label: `Tool ${i}`, icon: 'details', query: `DO-NOT-FORWARD-${i}` })) },
    contextual: { label: 'Context', icon: 'documents', contextLabel: 'Selected article', availableWithoutAttachments: false, tools: [{ label: 'Explain', query: 'PRIVATE-QUERY' }] } },
  toolRail: { initiallyCollapsed: true, items: [
    { id: 'find', label: 'Find', action: 'open-tools', scope: 'default', icon: 'search', tone: 'primary' },
    { id: 'context', label: 'Context', action: 'open-tools', scope: 'contextual', icon: 'details', tone: 'teal' },
    { id: 'sources', label: 'Sources', action: 'open-documents', icon: 'documents', tone: 'violet' },
    { id: 'prompt', label: 'Prompt', action: 'prompt', query: 'SECRET-RAIL-QUERY', requiresContext: true, tone: 'amber' },
  ] } })
const preview = (config = configuration(), caps = capabilities(), policy = 'public-with-protected-capabilities') => build({ configuration: config, packCode: 'generic-workspace', interactionPolicy: policy, capabilities: caps })

test('canonical groups preserve all tools, icons, scope, context, ordering and rail presentation', () => {
  const output = preview()
  assert.equal(output.toolGroups.default.tools.length, 15)
  assert.deepEqual(output.toolGroups.default.tools.map(tool => tool.label), Array.from({ length: 15 }, (_, i) => `Tool ${i}`))
  assert.equal(output.toolGroups.initialScope, 'contextual')
  assert.equal(output.toolGroups.contextual.contextLabel, 'Selected article')
  assert.equal(output.toolGroups.contextual.availableWithoutAttachments, false)
  assert.equal(output.toolRail.initiallyCollapsed, true)
  assert.deepEqual(output.toolRail.items.map(item => item.action), ['open-tools', 'open-tools', 'open-documents', 'prompt'])
  assert.equal(output.toolRail.items[3].requiresContext, true)
  assert.equal(output.theme.fontFamily, 'Arial, sans-serif')
  assert.equal(output.theme.borderRadius, '8px')
})
test('only shared pack resolvers supply omitted defaults; explicit empty/disabled stays empty', () => {
  const expected = resolve({}, 'generic-workspace')
  const defaults = preview({})
  assert.deepEqual(defaults.toolGroups.default.tools.map(tool => tool.label), expected.toolGroups.default.tools.map(tool => tool.label))
  assert.deepEqual(defaults.copy, expected.copy)
  const explicit = preview({ toolGroups: { default: { label: 'Find', tools: [] }, contextual: { label: 'Context', tools: [] } }, toolRail: { items: [] },
    copy: { welcomeMessage: '', starterSuggestions: [] }, capabilities: { currentPage: false } })
  assert.deepEqual(explicit.toolGroups.default.tools, [])
  assert.deepEqual(explicit.toolGroups.contextual.tools, [])
  assert.deepEqual(explicit.toolRail.items, [])
  assert.equal(explicit.copy.welcomeMessage, '')
  assert.deepEqual(explicit.copy.starterSuggestions, [])
  assert.equal(explicit.currentPage.enabled, false)
})
test('legacy arrays are not accepted or re-enabled with pack defaults', () => {
  const output = preview({ toolGroups: [{ scope: 'default', tools: [{ label: 'Wrong', query: 'Wrong' }] }] })
  assert.deepEqual(output.toolGroups.default.tools, [])
  assert.match(output.limitations.join(' '), /object contract/)
})
test('transport, identity, secrets, action queries, integration config and private records never cross the boundary', () => {
  const input = { ...configuration(), auth: { accessToken: 'PRIVATE-TOKEN' }, apiConfig: { chatBaseUrl: 'https://private-endpoint.test', defaultHeaders: { Authorization: 'PRIVATE-BEARER' } },
    requestContext: { vectorSpaces: ['PRIVATE-SPACE'], customerId: 'PRIVATE-CUSTOMER' }, conversations: [{ content: 'PRIVATE-CONVERSATION' }],
    integrations: { secret: 'PRIVATE-INTEGRATION' }, presentation: { actionNames: { lookup: 'PRIVATE-ACTION' }, detailBasePath: 'https://PRIVATE-NAV.test' },
    extensions: { scriptUrl: 'https://PRIVATE-SCRIPT.test' } }
  const before = structuredClone(input)
  const output = JSON.stringify(preview(input))
  for (const marker of ['PRIVATE-', 'private-endpoint', 'DO-NOT-FORWARD', 'SECRET-RAIL', 'chatBaseUrl', 'actionNames', 'requestContext', 'accessToken', 'scriptUrl']) assert.equal(output.includes(marker), false, marker)
  assert.deepEqual(input, before)
  assert.match(output, /trusted bundled renderer/)
})
test('safe capabilities alone control visibility; missing values never enable features', () => {
  const none = preview(configuration(), {})
  for (const key of ['history', 'attachments', 'sources', 'currentPage']) assert.equal(none.capabilities[key], false)
  assert.equal(none.capabilities.handover.enabled, false)
  assert.equal(none.currentPage.enabled, false)
  assert.equal(none.capabilities.authentication.signInAvailable, false)
  const caps = capabilities(); caps.handover.runtimeConfigured = false
  assert.equal(preview(configuration(), caps).capabilities.handover.enabled, false)
})
test('config-only handover samples require explicit enablement and visibly report unknown runtime', () => {
  const caps = capabilities(); caps.handover.runtimeConfigured = null
  const output = preview(configuration(), caps)
  assert.equal(output.capabilities.handover.enabled, true)
  assert.equal(output.capabilities.handover.runtimeConfigured, null)
  assert.match(output.limitations.join(' '), /unknown.*explicit configuration/)
  assert.equal(preview({}, caps).capabilities.handover.enabled, false)
  const assigned = preview({ capabilities: { handover: false } }, capabilities())
  assert.equal(assigned.capabilities.handover.enabled, true)
  assert.match(assigned.limitations.join(' '), /assigned runtime contract/)
})
test('appearance overlay preserves intentionally empty copy rather than re-enabling default welcome', () => {
  const output = build({ configuration: { copy: { welcomeMessage: '', placeholder: '' } }, packCode: 'generic-workspace',
    interactionPolicy: 'public', capabilities: capabilities(), appearance: { assistantLabel: 'Assistant', primaryColor: '#155eef', logoUrl: '', darkMode: 'light', welcomeMessage: '', placeholder: '' } })
  assert.equal(output.copy.welcomeMessage, '')
  assert.equal(output.copy.placeholder, '')
})
test('anonymous, required-login and protected-capability policies remain distinct without auth configuration', () => {
  for (const policy of ['public', 'login-required', 'public-with-protected-capabilities']) assert.equal(preview({}, capabilities(), policy).interactionPolicy, policy)
  assert.equal(sanitizeWorkspacePreview({ interactionPolicy: 'unsupported' }).interactionPolicy, 'login-required')
})
test('handover intake contains display schema only, never default values or channel credentials', () => {
  const caps = capabilities()
  caps.handover.intake = { anonymousAllowed: false, token: 'PRIVATE-CHANNEL', fields: [{ key: 'email', label: 'Email', type: 'EMAIL', required: true, consentRequired: true, value: 'PRIVATE-EMAIL', endpoint: 'PRIVATE-ENDPOINT' }] }
  const output = preview({}, caps)
  assert.equal(output.capabilities.handover.intake.anonymousAllowed, false)
  assert.equal(output.capabilities.handover.intake.fields[0].required, true)
  assert.equal(JSON.stringify(output).includes('PRIVATE-'), false)
})
test('unknown packs and unsupported extensions produce limitations, never customer scripts', () => {
  for (const packCode of ['unknown-extension', '__proto__', 'constructor']) {
    const output = build({ configuration: configuration(), packCode, capabilities: capabilities(), interactionPolicy: 'public' })
    assert.match(output.limitations.join(' '), /no bundled preview/)
    assert.deepEqual(output.rendererIds, [])
  }
})
test('image and CSS input cannot introduce credentials, script execution or network CSS', () => {
  for (const value of ['javascript:bad', 'http://example.test/a', 'https://user:pass@example.test/a', 'https://example.test/a?token=secret']) assert.equal(publicPreviewImage(value), '')
  assert.equal(publicPreviewImage('https://example.test/logo.png?width=64'), 'https://example.test/logo.png?width=64')
  const output = preview({ theme: { fontFamily: 'url(https://bad.test/font)', borderRadius: 'expression(bad)' } })
  assert.match(output.limitations.join(' '), /radius.*typography/)
  assert.equal(JSON.stringify(output).includes('bad.test'), false)
})
test('bundled pack registrations have fixtures for every supported renderer and obey capabilities', async () => {
  const { dealershipPreviewFixtures } = await load('../../../experience-packs/dealership-experience/src/preview-fixtures.ts')
  const config = { capabilities: { comparison: false, testDrive: true, callback: false } }
  const output = build({ configuration: config, packCode: 'dealership', interactionPolicy: 'public', capabilities: capabilities() })
  const fixtures = dealershipPreviewFixtures()
  assert.ok(output.rendererIds.every(id => fixtures.presentations.some(fixture => fixture.rendererId === id)))
  assert.equal(output.rendererContext.comparisonEnabled, false)
  assert.equal(output.rendererContext.testDriveEnabled, true)
  assert.equal(output.rendererIds.includes('loomai.vehicle-comparison.v1'), false)
  const receipts = dealershipPreviewFixtures({ callbackEnabled: true, testDriveEnabled: false }).presentations.filter(fixture => fixture.sample === 'receipt')
  assert.equal(receipts.length, 1)
  assert.equal(receipts[0].context.requestKind, 'callback')
  assert.equal(dealershipPreviewFixtures({ callbackEnabled: false, testDriveEnabled: false }).receipt, undefined)
})
test('preview entry uses presentation components and local adapters, not live controller/auth/installer or storage', async () => {
  const source = await readFile(new URL('../../../max-mode-widget/src/entries/preview.tsx', import.meta.url), 'utf8')
  for (const component of ['ToolGroupTabs', 'MobileToolRail', 'Composer', 'CompanionDock', 'MessageList', 'ConversationHistoryPanel', 'DocumentsView', 'PreviewRegisteredResult']) assert.ok(source.includes(`<${component}`))
  assert.equal(/(?:fetch\(|localStorage|sessionStorage|useMaxModeController\(|from ['"].*installer|supabase)/i.test(source), false)
  assert.equal(source.includes('.slice(0, 8)'), false)
  const renderer = await readFile(new URL('../../../max-mode-widget/src/preview/PreviewRegisteredResult.tsx', import.meta.url), 'utf8')
  assert.match(renderer, /navigate: feedback/)
})
test('page attachment stays in the real companion dock, never an invented MAX toolbar', async () => {
  const preview = await readFile(new URL('../../../max-mode-widget/src/entries/preview.tsx', import.meta.url), 'utf8')
  const dock = await readFile(new URL('../../../max-mode-widget/src/components/CompanionDock.tsx', import.meta.url), 'utf8')
  const max = await readFile(new URL('../../../max-mode-widget/src/components/MaxModeView/MaxModeComposerBar.tsx', import.meta.url), 'utf8')
  assert.equal(preview.includes('<CurrentPageAttachmentControl'), false)
  assert.equal(preview.includes('Attach sample context'), false)
  assert.match(preview, /currentPageAttachmentEnabled: config\.currentPage\.enabled/)
  assert.match(dock, /<CurrentPageAttachmentControl/)
  assert.equal(max.includes('<CurrentPageAttachmentControl'), false)
})
