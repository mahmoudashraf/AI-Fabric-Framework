import assert from 'node:assert/strict'
import { build } from 'esbuild'

const moduleFrom = async name => {
  const result = await build({ entryPoints: [new URL(`../src/${name}.ts`, import.meta.url).pathname], bundle: true,
    write: false, platform: 'node', format: 'esm', logLevel: 'silent' })
  return import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`)
}
const { WORKSPACE_STYLE_PRESETS, resolveWorkspaceStylePreset } = await moduleFrom('stylePresets')
const { workspaceStyleVariables, applyThemeToElement, bindWidgetColorScheme } = await moduleFrom('theme')
assert.equal(WORKSPACE_STYLE_PRESETS.length, 13)
assert.equal(resolveWorkspaceStylePreset().id, 'current')
assert.equal(resolveWorkspaceStylePreset('unknown').id, 'current')
assert.deepEqual(workspaceStyleVariables(undefined, false), {})
assert.deepEqual(workspaceStyleVariables({ stylePreset: 'current' }, true), {})
const luminance = hex => hex.slice(1).match(/../g).map(x => parseInt(x, 16) / 255)
  .map(x => x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4)
  .reduce((sum, x, i) => sum + x * [.2126, .7152, .0722][i], 0)
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05)
for (const preset of WORKSPACE_STYLE_PRESETS.slice(1)) {
  for (const dark of [false, true]) {
    const palette = dark ? preset.dark : preset.light
    const vars = workspaceStyleVariables({ stylePreset: preset.id }, dark)
    assert.equal(vars['--mxw-style-surface'], palette.surface)
    assert.equal(vars['--mxw-style-font'], preset.font)
    assert.equal(vars['--mxw-style-display-font'], preset.displayFont)
    for (const surface of [palette.surface, palette.canvas, palette.user, palette.human])
      assert(contrast(palette.text, surface) >= 4.5, `${preset.id}/${dark} text contrast`)
    assert(contrast(vars['--mxw-style-on-accent'], palette.accent) >= 4.5, `${preset.id}/${dark} button contrast`)
  }
  assert.equal(workspaceStyleVariables({ stylePreset: preset.id, primaryColor: '#72421f', fontFamily: 'Georgia', borderRadius: '6px' }, true)['--mxw-style-accent'], '#72421f')
}
const properties = new Map(), classes = new Set(), media = new EventTarget()
media.matches = false
const element = { dataset: {}, ownerDocument: { defaultView: { matchMedia: () => media } },
  classList: { contains: name => classes.has(name), toggle: (name, active) => active ? classes.add(name) : classes.delete(name) },
  style: { setProperty: (name, value) => properties.set(name, value), removeProperty: name => properties.delete(name) } }
applyThemeToElement(element, { primaryColor: '#123b35', fontFamily: 'Inter', borderRadius: '.5rem' })
const baseline = [...properties]
for (const preset of WORKSPACE_STYLE_PRESETS.slice(1)) applyThemeToElement(element, { stylePreset: preset.id })
applyThemeToElement(element, { primaryColor: '#123b35', fontFamily: 'Inter', borderRadius: '.5rem' })
assert.deepEqual([...properties], baseline, 'Returning to Current restores the exact legacy properties')
assert.equal(element.dataset.mxwStyle, 'current')
const release = bindWidgetColorScheme(element, 'auto', { stylePreset: 'botanical' })
assert.equal(properties.get('--mxw-style-surface'), resolveWorkspaceStylePreset('botanical').light.surface)
media.matches = true; media.dispatchEvent(new Event('change'))
assert.equal(properties.get('--mxw-style-surface'), resolveWorkspaceStylePreset('botanical').dark.surface)
release()
console.log('13-style theme contract passed: defaults, 24 palettes, contrast, overrides, exact Current restoration and automatic colour scheme.')
