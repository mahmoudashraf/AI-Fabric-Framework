import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import test from 'node:test'

const ts = createRequire(new URL('../generic-workspace-experience/package.json', import.meta.url))('typescript')
const metadataSource = await readFile(new URL('../../max-mode-widget/src/stylePresets.ts', import.meta.url), 'utf8')
const metadata = ts.transpileModule(metadataSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { WORKSPACE_STYLE_IDS } = await import(`data:text/javascript;base64,${Buffer.from(metadata).toString('base64')}`)
const generic = await import('../generic-workspace-experience/dist/generic-workspace-experience.esm.js')
const dealership = await import('../dealership-experience/dist/dealership-experience.esm.js')
const support = await import('../support-experience/dist/support-experience.esm.js')

test('all public pack theme types match the shared 13-style contract', async () => {
  for (const pack of ['generic-workspace', 'dealership', 'support']) {
    const source = await readFile(new URL(`../${pack}-experience/src/types.ts`, import.meta.url), 'utf8')
    const tree = ts.createSourceFile('types.ts', source, ts.ScriptTarget.Latest, true)
    const presets = []
    const visit = node => {
      if (ts.isPropertySignature(node) && node.name.getText(tree) === 'stylePreset') {
        assert.ok(node.questionToken, `${pack} style selection must remain optional`)
        assert.ok(ts.isUnionTypeNode(node.type))
        for (const type of node.type.types) {
          assert.ok(ts.isLiteralTypeNode(type) && ts.isStringLiteral(type.literal))
          presets.push(type.literal.text)
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(tree)
    assert.deepEqual(presets, WORKSPACE_STYLE_IDS, `${pack} theme contract drifted from widget metadata`)
  }
})

test('generic and dealership keep exact Current defaults but let enhanced presets supply their own visual defaults', () => {
  for (const [resolve, primaryColor] of [
    [generic.resolveGenericWorkspacePresentation, '#155eef'],
    [dealership.resolveDealershipPresentation, '#123b35'],
  ]) {
    const baseline = resolve({})
    assert.deepEqual(baseline.theme, { primaryColor, logoUrl: undefined, borderRadius: '0.5rem', fontFamily: 'Inter, system-ui, sans-serif', darkMode: false })
    assert.deepEqual(resolve({ theme: { stylePreset: 'current' } }).theme, { ...baseline.theme, stylePreset: 'current' })
    for (const stylePreset of WORKSPACE_STYLE_IDS.slice(1)) {
      const themed = resolve({ theme: { stylePreset } })
      assert.equal(themed.theme.stylePreset, stylePreset)
      assert.equal(themed.theme.primaryColor, undefined)
      assert.equal(themed.theme.fontFamily, undefined)
      assert.equal(themed.theme.borderRadius, undefined)
      assert.deepEqual({ ...themed, theme: undefined }, { ...baseline, theme: undefined })
    }
  }
})

test('resolvers preserve explicit branding and spread presentation fields, without mutating configuration', () => {
  for (const stylePreset of WORKSPACE_STYLE_IDS) {
    const theme = Object.freeze({ stylePreset, primaryColor: '#72421f', logoUrl: 'https://brand.example/logo.png',
      fontFamily: 'Georgia, serif', borderRadius: '3px', darkMode: 'auto', futurePresentationField: 'retained' })
    for (const resolve of [generic.resolveGenericWorkspacePresentation, dealership.resolveDealershipPresentation, support.resolveSupportPresentation]) {
      const config = { app: { assistantLabel: 'Support' }, theme }
      assert.deepEqual(resolve(config).theme, theme)
    }
  }
})

test('generic and dealership live mounts propagate every style in embedded and standalone without changing operational configuration', async () => {
  globalThis.window = { location: { pathname: '/' }, dispatchEvent() {} }
  globalThis.document = { querySelector() { return null } }
  globalThis.CustomEvent ??= class { constructor(type, options) { this.type = type; this.detail = options?.detail } }
  for (const [mount, configuration] of [
    [generic.mountInstallation, { app: { assistantLabel: 'Example' } }],
    [dealership.mountInstallation, { dealer: { id: 'example', assistantLabel: 'Example' }, page: { kind: 'inventory', rootSelector: 'main', contextLabel: 'Page' }, capabilities: { comparison: true, testDrive: true, callback: true } }],
  ]) {
    for (const deliveryMode of ['embedded', 'standalone']) {
      let mounted, opens = 0
      const context = { installationId: 'same-installation', assignmentRevision: 'same-assignment', deliveryMode,
        widgetConfig: { apiConfig: { chatBaseUrl: 'https://runtime.example' },
          host: { authentication: { enabled: true }, readOnly: true }, features: { attachments: true } },
        maxMode: { init(value) { mounted = value }, open() { opens++ }, destroy() {} }, refreshAssignment: async () => {} }
      const initial = await mount({ ...context, configuration })
      const baseline = mounted
      initial.destroy()
      for (const stylePreset of WORKSPACE_STYLE_IDS) {
        const controller = await mount({ ...context, configuration: { ...configuration, theme: { stylePreset } } })
        assert.equal(mounted.theme.stylePreset, stylePreset)
        const { onEvent: _ignored, theme: _theme, ...actual } = mounted
        const { onEvent: _baselineEvent, theme: _baselineTheme, ...expected } = baseline
        assert.deepEqual(actual, expected, `${stylePreset}/${deliveryMode} changed behaviour`)
        assert.equal(mounted.apiConfig, context.widgetConfig.apiConfig)
        controller.destroy()
      }
      assert.equal(opens, deliveryMode === 'standalone' ? WORKSPACE_STYLE_IDS.length + 1 : 0)
    }
  }
})
