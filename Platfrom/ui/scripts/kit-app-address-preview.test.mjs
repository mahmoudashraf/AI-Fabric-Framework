import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ts from 'typescript'
import { withDeploymentTerminology } from './deployment-terminology-test-support.mjs'

const require = createRequire(import.meta.url)
async function load(relative, dependencies) {
  const source = await readFile(new URL(relative, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(withDeploymentTerminology(source), { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } })
  const exports = {}
  new Function('require', 'exports', compiled.outputText)(name => {
    if (name in dependencies) return dependencies[name]
    if (name === 'react/jsx-runtime' || name.startsWith('@mui/')) return require(name)
    throw new Error(`Unexpected address-preview dependency ${name}`)
  }, exports)
  return exports
}
const models = await load('../src/components/kits/kitModels.ts', {
  '../../pages/aiWorkspaceAppearance': { readWorkspaceAppearance: () => null },
  '../customer/customerEngageConnection': { connectableRelease: () => false },
})
const fields = await load('../src/components/kits/KitExperienceFields.tsx', {
  './kitModels': models,
  'react-router-dom': { Link: ({ to, children }) => React.createElement('a', { href: to }, children) },
})
const form = overrides => ({ name: 'Address check', workspaceName: 'Example AI', brandColor: '#409aff',
  standalone: true, embedded: false, appSlug: '', websiteOrigin: '', humanSupportEnabled: false, ...overrides })
const render = value => renderToStaticMarkup(React.createElement(fields.KitExperienceFields, { value, onChange() {} }))

test('valid names produce the complete expected app link without changing the stored name', () => {
  for (const name of ['northfield-ai', 'dealership2', 'a', 'a'.repeat(63)]) {
    assert.equal(models.kitAppAddressPreview(name), `https://${name}.apps.loomai.pro`)
    assert.deepEqual(models.kitExperienceErrors(form({ appSlug: name })), [])
    assert.equal(models.kitUpdateRequest(form({ appSlug: name }), 1, 1).appSlug, name)
  }
})

test('invalid or incomplete labels never become a plausible app link', () => {
  for (const name of ['', '-demo', 'demo-', 'demo--ai', 'Demo', 'demo ai', ' demo ', 'demo_ai',
    'https://demo.apps.loomai.pro', 'demo.apps.loomai.pro', 'a'.repeat(64), '<script>']) {
    assert.equal(models.kitAppAddressPreview(name), null, name)
    assert.ok(models.kitExperienceErrors(form({ appSlug: name })).some(message => message.includes('not a full URL')), name)
  }
})

test('field shows an example before typing and a non-clickable full link as the value changes', () => {
  const empty = render(form())
  assert.match(empty, /Example app link/)
  assert.match(empty, /https:\/\/your-company-ai\.apps\.loomai\.pro/)
  for (const appSlug of ['northfield', 'northfield-ai']) {
    const html = render(form({ appSlug }))
    assert.match(html, /aria-label="App link preview"/)
    assert.match(html, /aria-live="polite"/)
    assert.ok(html.includes(`https://${appSlug}.apps.loomai.pro`))
    assert.ok(!html.includes(`href="https://${appSlug}.apps.loomai.pro"`))
    assert.match(html, /Available after setup, address verification and activation/)
  }
})

test('invalid text has inline guidance and does not retain the prior link', () => {
  const html = render(form({ appSlug: 'https://northfield.apps.loomai.pro' }))
  assert.match(html, /aria-invalid="true"/)
  assert.match(html, /without https:\/\/ or a domain/)
  assert.match(html, /Example app link/)
  assert.ok(!html.includes('https://https://'))
})

test('embedded-only configurations have no app-name field or standalone-link preview', () => {
  const html = render(form({ standalone: false, embedded: true, websiteOrigin: 'https://example.com' }))
  assert.ok(!html.includes('App address name'))
  assert.ok(!html.includes('App link preview'))
  assert.match(html, /Website address/)
})

test('setup passes the same validated URL to the canvas and final review without creating a live link', async () => {
  const setup = await readFile(new URL('../src/components/kits/ExperienceKitSetup.tsx', import.meta.url), 'utf8')
  const preview = await readFile(new URL('../src/components/kits/KitWorkspacePreview.tsx', import.meta.url), 'utf8')
  assert.ok(setup.includes('appUrl={form.standalone ? kitAppAddressPreview(form.appSlug) : null}'))
  assert.ok(setup.includes('<dt>App link</dt><dd>{kitAppAddressPreview(form.appSlug)}</dd>'))
  assert.ok(preview.includes("layout === 'standalone' && appUrl"))
  assert.ok(!preview.includes('href={appUrl}'))
})
