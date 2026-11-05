import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test, { after } from 'node:test'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

const server = await createServer({ configFile: false, optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false }, appType: 'custom' })
after(() => server.close())

async function moduleAt(path) {
  return server.ssrLoadModule(fileURLToPath(new URL(path, import.meta.url)))
}
const { readWorkspaceAppearance, selectWorkspaceAppearanceStyle, applyWorkspaceAppearance, workspaceAppearanceError, workspacePreviewTools } = await moduleAt('../src/pages/aiWorkspaceAppearance.ts')
const { engagePreviewDocument } = await moduleAt('../src/components/customer/engagePreviewDocument.ts')
const { WORKSPACE_STYLE_PRESETS } = await moduleAt('../../../max-mode-widget/src/stylePresets.ts')
const configuration = () => ({ app: { assistantLabel: 'Support' },
  theme: { primaryColor: '#155eef', borderRadius: '8px', fontFamily: 'Inter', darkMode: false },
  copy: { welcomeMessage: 'Welcome', starterSuggestions: ['Help me'], companionModeLabel: 'Assistant' },
  toolGroups: { initialScope: 'default', default: { label: 'Browse', tools: [{ label: 'Search', query: 'Original query' }] }, contextual: { label: 'Context', tools: [{ label: 'Explain', query: 'Original context query' }] } },
  requestContext: { vectorSpaces: ['approved'] }, capabilities: { handover: true },
})

test('generic appearance reads the customer-facing name, not installation metadata', () => {
  assert.equal(readWorkspaceAppearance(configuration(), 'generic-workspace').assistantLabel, 'Support')
})
test('dealership identity keeps its provider fields while sharing the same appearance contract', () => {
  const config = { ...configuration(), dealer: { id: 'dealer-owned', assistantLabel: 'Dealer AI', sourceMode: 'LIVE' } }
  delete config.app
  const draft = readWorkspaceAppearance(config, 'dealership')
  const output = applyWorkspaceAppearance(config, 'dealership', { ...draft, assistantLabel: 'New name' })
  assert.deepEqual(output.dealer, { ...config.dealer, assistantLabel: 'New name' })
  assert.equal('app' in output, false)
})
test('appearance changes preserve tools, context, capabilities, typography and suggestions', () => {
  const input = configuration(); const before = structuredClone(input)
  const draft = { ...readWorkspaceAppearance(input, 'generic-workspace'), assistantLabel: '  My assistant  ',
    primaryColor: '#147d64', logoUrl: 'https://example.test/logo.png', darkMode: 'dark', welcomeMessage: 'Hello', placeholder: 'Ask us...' }
  const output = applyWorkspaceAppearance(input, 'generic-workspace', draft)
  assert.equal(output.app.assistantLabel, 'My assistant')
  assert.deepEqual(output.theme, { ...input.theme, logoUrl: draft.logoUrl, primaryColor: draft.primaryColor, darkMode: true })
  assert.deepEqual(output.copy, { ...input.copy, welcomeMessage: 'Hello', placeholder: 'Ask us...' })
  for (const key of ['toolGroups', 'requestContext', 'capabilities']) assert.deepEqual(output[key], input[key])
  output.toolGroups.default.tools[0].label = 'Changed copy'
  assert.deepEqual(input, before)
})
test('clearing logo and optional copy really removes overrides without losing sibling values', () => {
  const input = configuration(); input.theme.logoUrl = 'https://example.test/old.png'; input.copy.placeholder = 'Old'
  const output = applyWorkspaceAppearance(input, 'generic-workspace', { ...readWorkspaceAppearance(input, 'generic-workspace'), logoUrl: '', welcomeMessage: '', placeholder: '' })
  assert.equal('logoUrl' in output.theme, false)
  assert.equal('welcomeMessage' in output.copy, false)
  assert.equal('placeholder' in output.copy, false)
  assert.deepEqual(output.copy.starterSuggestions, ['Help me'])
})
test('system appearance is preserved as an explicit configuration choice', () => {
  const input = configuration(); input.theme.darkMode = 'auto'
  const draft = readWorkspaceAppearance(input, 'generic-workspace')
  assert.equal(draft.darkMode, 'auto')
  assert.equal(applyWorkspaceAppearance(input, 'generic-workspace', draft).theme.darkMode, 'auto')
})
test('opting into a style replaces only inherited legacy typography/shape, and Current restores them', () => {
  const input = configuration()
  input.theme = { primaryColor: '#123b35', fontFamily: 'Inter, system-ui, sans-serif', borderRadius: '0.5rem', darkMode: false }
  const baseline = structuredClone(input)
  const current = readWorkspaceAppearance(input, 'generic-workspace')
  const selected = applyWorkspaceAppearance(input, 'generic-workspace', { ...current, stylePreset: 'editorial' })
  assert.deepEqual(selected.theme, { primaryColor: '#123b35', darkMode: false, stylePreset: 'editorial' })
  assert.deepEqual(applyWorkspaceAppearance(input, 'generic-workspace', current), baseline)
  const savedCustom = { ...input, theme: { ...input.theme, stylePreset: 'pop' } }
  assert.equal(applyWorkspaceAppearance(savedCustom, 'generic-workspace', { ...current, stylePreset: 'editorial' }).theme.fontFamily, 'Inter, system-ui, sans-serif')
  assert.deepEqual(input, baseline)
})
test('missing or unknown saved styles keep Current without adding a default override', () => {
  for (const stylePreset of [undefined, null, '', 'future-style', { id: 'pop' }]) {
    const input = configuration()
    if (stylePreset !== undefined) input.theme.stylePreset = stylePreset
    const draft = readWorkspaceAppearance(input, 'generic-workspace')
    assert.equal(draft.stylePreset, 'current')
    const output = applyWorkspaceAppearance(input, 'generic-workspace', draft)
    assert.equal(Object.hasOwn(output.theme, 'stylePreset'), false)
    assert.deepEqual(output.theme, configuration().theme)
  }
})
test('every canonical style round trips independently of brand, scheme and capabilities', () => {
  assert.equal(WORKSPACE_STYLE_PRESETS.length, 13)
  assert.equal(WORKSPACE_STYLE_PRESETS[0].id, 'current')
  for (const packCode of ['generic-workspace', 'dealership', 'support']) {
    const input = { ...configuration(), dealer: { assistantLabel: 'Dealer AI', accountId: 'owned-account' },
      auth: { policy: 'protected' }, apiConfig: { chatBaseUrl: 'https://runtime.example.test' },
      actionPresentation: { renderer: 'registered' }, theme: { ...configuration().theme, darkMode: 'auto' } }
    const before = structuredClone(input)
    for (const preset of WORKSPACE_STYLE_PRESETS) {
      const draft = { ...readWorkspaceAppearance(input, packCode), stylePreset: preset.id }
      const output = applyWorkspaceAppearance(input, packCode, draft)
      assert.equal(output.theme.stylePreset, preset.id === 'current' ? undefined : preset.id)
      assert.equal(readWorkspaceAppearance(output, packCode).stylePreset, preset.id)
      const expected = { ...before }
      if (preset.id !== 'current') expected.theme = { ...before.theme, stylePreset: preset.id }
      assert.deepEqual(output, expected, `${packCode}:${preset.id}`)
      assert.deepEqual(input, before)
    }
  }
})
test('returning to Current removes only the preset and preserves the previous custom appearance', () => {
  const input = configuration()
  input.theme = { ...input.theme, logoUrl: 'https://example.test/logo.png', fontFamily: 'Custom font', borderRadius: '17px', darkMode: true }
  const styled = applyWorkspaceAppearance(input, 'generic-workspace', { ...readWorkspaceAppearance(input, 'generic-workspace'), stylePreset: 'obsidian' })
  const restored = applyWorkspaceAppearance(styled, 'generic-workspace', { ...readWorkspaceAppearance(styled, 'generic-workspace'), stylePreset: 'current' })
  assert.deepEqual(restored, input)
})
test('unspecified branding follows preset metadata without persisting defaults and Current restores the pack colour', () => {
  for (const packCode of ['generic-workspace', 'dealership', 'support']) {
    const input = { app: { assistantLabel: 'Assistant' }, dealer: { assistantLabel: 'Dealer AI' }, theme: { darkMode: 'auto' } }
    const original = readWorkspaceAppearance(input, packCode)
    assert.equal(original.primaryColorSource, 'default')
    for (const preset of WORKSPACE_STYLE_PRESETS) {
      const draft = selectWorkspaceAppearanceStyle(original, preset.id)
      assert.equal(draft.primaryColor, preset.id === 'current' ? original.primaryColor : preset.light.accent)
      assert.equal(draft.darkMode, 'auto')
      const saved = applyWorkspaceAppearance(input, packCode, draft)
      assert.equal(Object.hasOwn(saved.theme, 'primaryColor'), false)
      assert.equal(Object.hasOwn(saved.theme, 'fontFamily'), false)
      assert.equal(Object.hasOwn(saved.theme, 'borderRadius'), false)
      assert.equal(Object.hasOwn(saved.theme, 'primaryColorSource'), false)
      assert.equal(Object.hasOwn(saved.theme, 'currentPrimaryColor'), false)
      const restored = selectWorkspaceAppearanceStyle(draft, 'current')
      assert.equal(restored.primaryColor, original.primaryColor)
      assert.deepEqual(applyWorkspaceAppearance(input, packCode, restored), input)
    }
  }
})
test('preset selection leaves a saved or newly edited brand colour and colour scheme unchanged', () => {
  const input = configuration()
  const original = readWorkspaceAppearance(input, 'generic-workspace')
  for (const preset of WORKSPACE_STYLE_PRESETS) {
    const draft = selectWorkspaceAppearanceStyle(original, preset.id)
    assert.equal(draft.primaryColor, input.theme.primaryColor)
    assert.equal(draft.primaryColorSource, 'configured')
    assert.equal(draft.darkMode, original.darkMode)
    assert.equal(applyWorkspaceAppearance(input, 'generic-workspace', draft).theme.primaryColor, input.theme.primaryColor)
  }
})
test('invalid execution fields and unsafe image locations cannot be saved', () => {
  const draft = readWorkspaceAppearance(configuration(), 'generic-workspace')
  for (const logoUrl of ['javascript:alert(1)', 'data:image/svg+xml,a', 'http://example.test/logo', 'https://user:password@example.test/logo',
    'https://example.test/logo#fragment', 'https:example.test', 'https://example.test/a b.png', 'https://example.test:65536/logo']) {
    assert.match(workspaceAppearanceError({ ...draft, logoUrl }), /Logo/)
  }
  assert.match(workspaceAppearanceError({ ...draft, assistantLabel: 'x'.repeat(129) }), /Assistant name/)
  assert.match(workspaceAppearanceError({ ...draft, primaryColor: 'url(javascript:bad)' }), /colour/)
  assert.throws(() => applyWorkspaceAppearance({}, 'unsupported', draft), /does not expose/)
  assert.equal(workspaceAppearanceError({ ...draft, logoUrl: 'https://images.example.test/logo.png?width=64' }), null)
})
test('preview tools derive only from configured display labels, never queries or credentials', () => {
  assert.deepEqual(workspacePreviewTools(configuration()), ['Search', 'Explain'])
  assert.deepEqual(workspacePreviewTools({ toolGroups: [{ scope: 'default', tools: [{ query: 'No label' }] }] }), [])
})
test('canonical object groups retain every configured default/contextual label in order', () => {
  const tools = Array.from({ length: 14 }, (_, index) => ({ label: `Tool ${index}`, query: `Query ${index}` }))
  const config = { toolGroups: { initialScope: 'contextual', default: { tools }, contextual: { tools: [{ label: 'Context' }] } } }
  assert.deepEqual(workspacePreviewTools(config), [...tools.map(tool => tool.label), 'Context'])
  assert.deepEqual(workspacePreviewTools({ toolGroups: { default: { tools: [] }, contextual: { tools: [] } } }), [])
})
test('preview document forbids network, forms and embedding active script data', () => {
  const html = engagePreviewDocument('const example = "</script>";')
  assert.match(html, /connect-src 'none'/)
  assert.match(html, /form-action 'none'/)
  assert.match(html, /default-src 'none'/)
  assert.match(html, /font-src data:;/)
  assert.equal((html.match(/<\/script>/gi) ?? []).length, 1)
  assert.equal(html.includes('allow-same-origin'), false)
})

test('widget colour scheme is local, tracks system mode and releases its listener', async () => {
  const { bindWidgetColorScheme } = await moduleAt('../../../max-mode-widget/src/theme.ts')
  const classes = new Set(); const listeners = new Set()
  const media = { matches: false, addEventListener: (_, listener) => listeners.add(listener), removeEventListener: (_, listener) => listeners.delete(listener) }
  const element = { ownerDocument: { defaultView: { matchMedia: () => media } }, classList: {
    contains: name => classes.has(name), toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name),
  } }
  const release = bindWidgetColorScheme(element, 'auto')
  assert.equal(classes.has('dark'), false)
  media.matches = true; listeners.forEach(listener => listener())
  assert.equal(classes.has('dark'), true)
  release()
  assert.equal(classes.has('dark'), false)
  assert.equal(listeners.size, 0)
  const releaseDark = bindWidgetColorScheme(element, true)
  assert.equal(classes.has('dark'), true)
  releaseDark()
  assert.equal(classes.has('dark'), false)
})
