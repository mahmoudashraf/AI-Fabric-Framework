import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test, { after } from 'node:test'
import ts from 'typescript'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

const server = await createServer({ configFile: false, optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false }, appType: 'custom' })
after(() => server.close())

async function moduleAt(path) {
  return server.ssrLoadModule(fileURLToPath(new URL(path, import.meta.url)))
}
const { readWorkspaceAppearance, applyWorkspaceAppearance, workspaceAppearanceError, workspacePreviewTools } = await moduleAt('../src/pages/aiWorkspaceAppearance.ts')
const { engagePreviewDocument } = await moduleAt('../src/components/customer/engagePreviewDocument.ts')
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
  assert.equal((html.match(/<\/script>/gi) ?? []).length, 1)
  assert.equal(html.includes('allow-same-origin'), false)
})

test('widget colour scheme is local, tracks system mode and releases its listener', async () => {
  const source = await readFile(new URL('../../../max-mode-widget/src/theme.ts', import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const { bindWidgetColorScheme } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
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
