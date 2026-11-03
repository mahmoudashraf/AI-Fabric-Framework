import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, stat } from 'node:fs/promises'
import { createRequire } from 'node:module'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const imports = []
async function load(relative, dependencies = {}) {
  const source = await readFile(new URL(relative, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } })
  const exports = {}
  new Function('require', 'exports', compiled.outputText)(name => {
    imports.push(name)
    if (name in dependencies) return dependencies[name]
    if (name === 'react' || name === 'react/jsx-runtime' || name.startsWith('@mui/')) return require(name)
    throw new Error(`Unexpected preview dependency: ${name}`)
  }, exports)
  return exports
}
const content = await load('../src/components/kits/kitPreviewContent.ts')
const supportConfig = await load('../../../experience-packs/support-experience/src/config.ts')
const supportFixtures = await load('../../../experience-packs/support-experience/src/preview-fixtures.ts', { './config': supportConfig })
const identity = {
  KitMark: ({ product }) => React.createElement('span', { 'data-product': product }),
  kitAsset: name => `/products/${name}`,
}
const support = await load('../src/components/kits/SupportKitPreview.tsx', {
  '../../../../../experience-packs/support-experience/src/preview-fixtures': supportFixtures,
  './KitIdentity': identity, './kitPreviewContent': content,
})
const preview = await load('../src/components/kits/KitCapabilityPreview.tsx', {
  './KitIdentity': identity,
  './kitPreviewContent': content,
  './KitCapabilityPreview.css': {},
  './SupportKitPreview': support,
})
const render = (component, props) => renderToStaticMarkup(React.createElement(component, props))

test('preview formats are visual states, not new installation delivery modes', () => {
  assert.deepEqual(content.kitPreviewFormats.map(format => format.id), ['website', 'max', 'standalone'])
  assert.deepEqual(content.kitPreviewFormats.map(format => format.label), ['Website AI chat', 'Max AI workspace', 'Standalone AI space'])
  assert.ok(content.kitPreviewFormats.every(format => format.description.length > 20))
})

test('experience scope distinguishes adaptive from industry-specific without changing kit definitions', () => {
  assert.match(render(preview.KitScope, { kind: 'adaptive' }), /Adapts to your business/)
  assert.match(render(preview.KitScope, { kind: 'adaptive' }), /Your products\. Your services\. Your knowledge\./)
  assert.match(render(preview.KitScope, { kind: 'dealership' }), /Built for dealerships/)
  assert.equal(content.kitPreviewContent('adaptive').dealership, false)
  assert.equal(content.kitPreviewContent('dealership').dealership, true)
})

test('both examples show real image assets, actions and explicitly optional human support', () => {
  for (const kind of ['adaptive', 'dealership']) {
    const html = render(preview.KitCapabilityIllustration, { kind })
    const data = content.kitPreviewContent(kind)
    assert.match(html, /role="img"/)
    assert.match(html, /Fictional example, not live AI or an assigned agent/)
    assert.match(html, /Optional with LoomAI Desk/)
    assert.match(html, /Compare/)
    assert.match(html, /demo-support-agent\.webp/)
    for (const product of data.products) assert.ok(html.includes(`/products/${product.image}`))
    assert.ok(html.includes(data.nextAction))
    assert.ok(!html.includes('<button'), 'Inline illustration is not a dead interactive workspace')
    assert.ok(!html.includes('<input'), 'Illustration must not collect a message')
  }
})

test('product composition consistently includes Platform and Engage, with Desk optional', () => {
  const html = render(preview.KitIncludedProducts)
  for (const name of ['LoomAI Platform', 'LoomAI Engage', 'LoomAI Desk']) assert.ok(html.includes(name))
  assert.match(html, /LoomAI Desk <em>Optional<\/em>/)
  assert.match(html, /Connect your business/)
  assert.match(html, /Brand the experience/)
  assert.match(html, /Bring your team/)
})

test('every format has its own accessible preview command and a miniature screen', () => {
  for (const kind of ['adaptive', 'dealership']) {
    const html = render(preview.KitExperiencePreview, { kind, name: 'Example kit' })
    for (const format of content.kitPreviewFormats) {
      assert.ok(html.includes(`aria-label="Preview ${format.label} for Example kit"`))
      assert.ok(html.includes(`ek-format-${format.id}`))
    }
    assert.ok(html.includes('Preview experience'))
    assert.ok(!html.includes('role="dialog"'), 'Dismissible preview starts closed')
    assert.ok(!html.includes('Create my workspace'), 'Preview does not create an installation')
  }
})

test('marketing preview imports no backend, auth, widget, runtime or AI client', () => {
  assert.ok(imports.length > 5)
  assert.ok(imports.every(name => !/platformApi|experienceKitsApi|auth\/|max-mode-widget|supabase/.test(name)))
})

test('dismissal, format switching and product interactions are explicit local state only', async () => {
  const source = await readFile(new URL('../src/components/kits/KitCapabilityPreview.tsx', import.meta.url), 'utf8')
  assert.ok(source.includes('onClose={() => setOpen(false)}'))
  assert.ok(source.includes('aria-label="Close preview"'))
  assert.ok(source.includes('if (value) setFormat(value)'))
  assert.ok(source.includes('setContext(\'compare\')'))
  assert.ok(source.includes('setHuman(value => !value)'))
  assert.ok(!/fetch\(|localStorage|sessionStorage|platformRequest\(|createExperienceKit\(/.test(source))
})

test('all referenced product photos exist, without relying on generated-image cache paths', async () => {
  for (const kind of ['adaptive', 'dealership']) {
    for (const product of content.kitPreviewContent(kind).products) {
      const info = await stat(new URL(`../public/products/${product.image}`, import.meta.url))
      assert.ok(info.size > 1000)
    }
  }
})

test('public-site and Console illustrations use the same example products and prices', async () => {
  const site = await load('../../loomai-site/src/components/ExperienceCatalogueContent.ts')
  for (const [kind, variant] of [['adaptive', 'generic'], ['dealership', 'dealership']]) {
    const consoleData = content.kitPreviewContent(kind)
    const siteData = site.catalogueExperiences[variant]
    assert.equal(consoleData.query, siteData.question)
    assert.equal(consoleData.badge, siteData.badge)
    assert.deepEqual(consoleData.products.map(item => ({ name: item.name, price: item.price })),
      siteData.products.map(item => ({ name: item.name, price: item.price })))
  }
})

test('Support uses canonical pack fixtures and never shopping products, comparisons or delivery', () => {
  const html = render(preview.KitCapabilityIllustration, { kind: 'support' })
  const fixtures = support.supportKitPreviewFixtures()
  assert.ok(html.includes(fixtures.userMessage))
  assert.ok(html.includes(fixtures.support.requests[0].subject))
  assert.match(html, /SAMPLE-001/)
  assert.match(html, /Synthetic preview/)
  assert.doesNotMatch(html, /Chair|GBP|Test drive|Compare|Delivery|<button|<input/)
  assert.match(render(preview.KitScope, { kind: 'support' }), /Built for support/)
})

test('Support preview honors separate ticket/human flags without forwarding private instance configuration', () => {
  const options = { ticketingEnabled: false, humanSupportEnabled: true, configuration: {
    support: { spaceId: 'PRIVATE-SPACE', requesterOrigin: 'https://private.test' }, requestContext: { token: 'PRIVATE-TOKEN' },
  } }
  const fixture = support.supportKitPreviewFixtures(options)
  assert.deepEqual(fixture.support.requests, [])
  assert.equal(fixture.support.presentation.handover, true)
  assert.ok(!JSON.stringify(fixture).includes('PRIVATE-'))
  const html = render(support.SupportKitIllustration, options)
  assert.doesNotMatch(html, /SAMPLE-001/)
  assert.match(html, /Optional human support/)
})
