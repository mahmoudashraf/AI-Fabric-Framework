import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import ts from 'typescript'

const require = createRequire(import.meta.url)
async function load(relative, dependencies = {}) {
  const source = (await readFile(new URL(relative, import.meta.url), 'utf8')).replaceAll('import.meta.env.BASE_URL', "'/'")
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } })
  const exports = {}
  new Function('require', 'exports', compiled.outputText)(name => {
    if (name in dependencies) return dependencies[name]
    if (name.endsWith('.css')) return {}
    if (name === 'react' || name === 'react/jsx-runtime' || name === 'react-router-dom' || name.startsWith('@mui/')) return require(name)
    throw new Error(`Unexpected catalogue dependency ${name}`)
  }, exports)
  return exports
}
const integrationConfig = await load('../src/integrationTeam.ts')
const integrationCall = await load('../src/components/IntegrationCallButton.tsx', { '../integrationTeam': integrationConfig })
const identity = await load('../src/components/kits/KitIdentity.tsx', {
  '../../theme/CustomerConsoleTheme': { useCustomerColorPreference: () => ({ setPreference() {} }) },
  '../IntegrationCallButton': integrationCall,
  '../../integrationTeam': integrationConfig,
})
const content = await load('../src/components/kits/kitPreviewContent.ts')
const scene = await load('../src/components/kits/KitCardScene.tsx', { './KitIdentity': identity, './kitPreviewContent': content })
const commercial = await load('../src/components/kits/KitCommercialPresentation.tsx')
const chooser = await load('../src/components/kits/KitChooser.tsx', {
  './KitIdentity': identity, './KitCardScene': scene, './KitCommercialPresentation': commercial,
  './KitCapabilityPreview': { KitExperiencePreview: ({ kind }) => React.createElement('button', null, `Preview ${kind}`) },
  './kitModels': { kitPreviewKind: code => code === 'dealership-ai-experience' ? 'dealership' : code === 'ai-support-experience' ? 'support' : 'adaptive',
    safeKitExternalUrl: value => value?.startsWith('https://') ? value : null },
})
const definitions = [
  { code: 'ai-customer-experience', name: 'AI Customer Experience', demoUrl: null },
  { code: 'dealership-ai-experience', name: 'Dealership AI Experience', demoUrl: 'https://loomai.pro/demos/dealership-ai' },
  { code: 'ai-support-experience', name: 'AI Support Experience', demoUrl: null },
]
const render = (component, props) => renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(component, props)))

test('catalogue keeps three distinct kits and existing detail destinations', () => {
  const html = render(chooser.KitChooser, { definitions })
  for (const definition of definitions) assert.ok(html.includes(`href="/experiences/${definition.code}"`))
  for (const name of ['AI Customer Experience', 'Dealership AI', 'AI Support']) assert.ok(html.includes(name))
  assert.equal((html.match(/class="ek-catalogue-card"/g) ?? []).length, 3)
  assert.equal((html.match(/class="ek-card-products"/g) ?? []).length, 3)
  for (const label of ['Website widget', 'Max workspace', 'Standalone app']) assert.ok(html.includes(label))
})

test('commercial offer is dealership-only, not billing or an entitlement change', () => {
  const html = render(chooser.KitChooser, { definitions })
  assert.equal((html.match(/aria-label="Dealership offer"/g) ?? []).length, 1)
  assert.ok(html.includes(commercial.dealershipOffer.price))
  assert.ok(html.includes('Only pay when value starts'))
  assert.ok(html.includes('First 3 months free'))
  assert.ok(html.includes('Free integration'))
  for (const definition of [definitions[0], definitions[2]]) {
    assert.doesNotMatch(render(chooser.KitChooser, { definitions: [definition] }), /\u00a3500|Free integration|aria-label="Dealership offer"/)
  }
  assert.doesNotMatch(html, /checkout|stripe|Coming soon/)
})

test('Desk channel icons sit above the headset-wearing agent portrait and remain optional everywhere', async () => {
  const html = render(identity.KitDeskMark)
  assert.match(html, /ek-desk-channels/)
  assert.match(html, /WhatsAppIcon/)
  assert.match(html, /SmartphoneOutlinedIcon/)
  assert.match(html, /demo-support-agent-headset.webp/)
  assert.doesNotMatch(html, /HeadsetMicOutlinedIcon/)
  const catalogue = render(chooser.KitChooser, { definitions })
  assert.equal((catalogue.match(/Human support optional with Desk/g) ?? []).length, 3)
  const css = await readFile(new URL('../src/components/kits/KitCatalogueCards.css', import.meta.url), 'utf8')
  assert.match(css, /\.ek-desk-channels \{ position: absolute; top: 0;/)
  assert.match(css, /\.ek-desk-channels > svg \{ width: 11px; height: 11px;/)
})

test('card examples show the correct assets without collecting messages or executing mock actions', () => {
  for (const kind of ['adaptive', 'dealership', 'support']) {
    const html = render(scene.KitCardScene, { kind })
    assert.match(html, /Fictional example, not live AI or an assigned agent/)
    assert.doesNotMatch(html, /<button|<input|<form/)
  }
  assert.match(render(scene.KitCardScene, { kind: 'adaptive' }), /example-forma-chair.webp/)
  assert.match(render(scene.KitCardScene, { kind: 'dealership' }), /example-electric-suv.webp/)
  assert.match(render(scene.KitCardScene, { kind: 'support' }), /demo-support-agent.webp/)
})

test('signed-in chooser still bootstraps through the same kit setup routes', () => {
  const html = render(chooser.KitChooser, { definitions, authenticated: true, detailLinks: false })
  for (const definition of definitions) assert.ok(html.includes(`href="/kits/new/${definition.code}"`))
  assert.equal((html.match(/Install kit/g) ?? []).length, 3)
})

test('integration calls use the approved real scheduling page without account context', () => {
  const html = render(integrationCall.IntegrationCallButton)
  assert.ok(html.includes(`href="${integrationConfig.integrationTeam.bookingUrl}"`))
  assert.match(html, /target="_blank"/)
  assert.match(html, /rel="noopener noreferrer"/)
  assert.match(html, /Book integration call/)
  assert.doesNotMatch(html, /\?email|customerId|deploymentId|iframe|<script/)
})
