import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const { ThemeProvider, createTheme } = require('@mui/material/styles')
const { QueryClient, QueryClientProvider } = require('@tanstack/react-query')
const source = await readFile(new URL('../src/components/AiBehaviourEditor.tsx', import.meta.url), 'utf8')
const helperSource = await readFile(new URL('../src/aiBehaviourProfiles.ts', import.meta.url), 'utf8')
function compile(source, resolve = require) {
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText
  const exports = {}
  new Function('exports', 'require', compiled)(exports, resolve)
  return exports
}
const helpers = compile(helperSource)
const apiCalls = []
const { AiBehaviourEditor } = compile(source, name => name === '../aiBehaviourProfiles' ? helpers
  : name === '../api/platformApi' ? {
    fetchDeploymentAiBehaviourProfiles: () => { apiCalls.push('catalogue'); throw new Error('Unexpected request during presentation test') },
    composeDeploymentAiBehaviour: () => { apiCalls.push('compose'); throw new Error('Unexpected composition during presentation test') },
  } : require(name))

const groups = [
  { key: 'tone', label: 'Tone', ids: ['professional', 'warm', 'consultative', 'technical'] },
  { key: 'answerBreadth', label: 'Answer breadth', ids: ['grounded', 'advisory', 'exploratory'] },
  { key: 'answerLength', label: 'Answer length', ids: ['brief', 'balanced', 'detailed'] },
  { key: 'nextSteps', label: 'Next steps', ids: ['off', 'contextual'] },
].map(group => ({ ...group, options: group.ids.map(id => ({ id, label: `${group.label}: ${id}`,
  description: `${group.label} ${id} description`, instructions: `${group.key} ${id} instructions`,
  examples: [`${group.label} ${id} example`],
})) }))
const catalogue = { schemaVersion: 'loomai-ai-behaviour-v1', revision: 1,
  defaults: { tone: 'professional', answerBreadth: 'grounded', answerLength: 'balanced', nextSteps: 'off' }, groups }
const metadata = { schemaVersion: catalogue.schemaVersion, revision: 1, ...Object.fromEntries(groups.map(group => {
  const presetId = group.key === 'tone' ? 'warm' : catalogue.defaults[group.key]
  return [group.key, { presetId, revision: 1, instructions: `${group.key} retained instructions` }]
})) }

function render({ mode = 'light', promptConfig = { aiBehaviour: metadata }, canEdit = true, deploymentId = 'dep-ui-test' } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } } })
  client.setQueryData(['deployment-ai-behaviour-profiles'], catalogue)
  const effects = []
  const html = renderToStaticMarkup(React.createElement(ThemeProvider, { theme: createTheme({ palette: { mode } }) },
    React.createElement(QueryClientProvider, { client }, React.createElement(AiBehaviourEditor, {
      deploymentId, promptConfig, canEdit,
      onApply: value => effects.push(['apply', value]), onPendingChange: value => effects.push(['pending', value]),
    }))))
  client.clear()
  return { html, effects }
}

function comboboxes(html) { return html.match(/<[^>]+role="combobox"[^>]*>/g) ?? [] }

test('the unframed editor has a compact icon heading and all four accessible profile fields', () => {
  const { html } = render()
  assert.match(html, /<section[^>]+aria-label="AI behaviour"/)
  assert.match(html, /<h2[^>]*>AI behaviour<\/h2>/)
  for (const icon of ['TuneRounded', 'RecordVoiceOverRounded', 'OpenInFullRounded', 'SubjectRounded', 'AltRouteRounded']) {
    assert.ok(html.includes(`data-testid="${icon}Icon"`), `Missing ${icon} icon`)
  }
  const controls = comboboxes(html)
  assert.equal(controls.length, 4)
  for (const group of groups) assert.equal(controls.filter(control => control.includes(`aria-label="${group.label}"`)).length, 1)
  assert.ok(!source.includes('<Card') && !source.includes('<Paper'), 'Profile fields must not become nested cards')
})

test('saved selections, descriptions, examples and instruction labels remain catalogue-driven', () => {
  const { html } = render()
  for (const group of groups) {
    const option = group.options.find(option => option.id === metadata[group.key].presetId)
    assert.ok(html.includes(option.label))
    assert.ok(html.includes(option.description))
    assert.ok(html.includes(option.examples[0]))
    assert.ok(html.includes(`${group.label} instructions`))
    assert.ok(html.includes(metadata[group.key].instructions))
  }
  assert.match(html, /aria-expanded="false"/)
  assert.ok(html.includes('Advanced profile instructions'))
  assert.ok(html.includes('Review changes') && html.includes('Choose defaults'))
})

test('legacy and custom configurations retain their existing presentation without applying defaults', () => {
  const legacy = { systemPrompt: 'Keep this exact base prompt', extra: { enabled: true } }
  const before = JSON.stringify(legacy)
  const result = render({ promptConfig: legacy })
  assert.ok(result.html.includes('Current instructions / not yet configured'))
  assert.ok(result.html.includes('Existing instructions stay unchanged until you choose a profile.'))
  assert.ok(result.html.includes('Your base prompts remain unchanged.'))
  assert.equal(JSON.stringify(legacy), before)
  assert.deepEqual(result.effects, [])
  const custom = render({ promptConfig: { aiBehaviour: { ...metadata,
    tone: { presetId: 'custom', revision: 1, instructions: 'Exact custom guidance' },
  } } })
  assert.ok(custom.html.includes('Custom instructions'))
  assert.ok(custom.html.includes('Exact custom guidance'))
  assert.deepEqual(custom.effects, [])
})

test('read-only and no-deployment states keep their existing disabled controls', () => {
  for (const props of [{ canEdit: false }, { deploymentId: '' }]) {
    const { html } = render(props)
    assert.equal(comboboxes(html).filter(control => control.includes('aria-disabled="true"')).length, 4)
    assert.match(html, /<button[^>]+disabled=""[^>]*>Review changes<\/button>/)
  }
})

test('dark and light layouts use theme tokens and stack from one to two columns', () => {
  for (const mode of ['light', 'dark']) {
    const { html } = render({ mode })
    assert.equal(comboboxes(html).length, 4)
    assert.ok(html.includes('grid-template-columns:minmax(0, 1fr)'))
    assert.ok(html.includes('grid-template-columns:repeat(2, minmax(0, 1fr))'))
    assert.ok(html.includes('white-space:normal') && html.includes('overflow-wrap:anywhere'))
  }
  assert.doesNotMatch(source, /#[\da-f]{3,8}\b/i)
  assert.doesNotMatch(source, /letterSpacing\s*:\s*['"]?-/)
  assert.ok(source.includes('theme.palette.primary.main') && source.includes('theme.palette[color].main'))
  assert.ok(!source.includes("lg: 'repeat(4"))
})

test('presentation rendering makes no API calls or configuration writes', () => {
  const promptConfig = { aiBehaviour: metadata, unknownSetting: ['preserve'], providerConfig: { model: 'unchanged' } }
  const before = JSON.stringify(promptConfig)
  const { effects } = render({ promptConfig })
  assert.equal(JSON.stringify(promptConfig), before)
  assert.deepEqual(effects, [])
  assert.deepEqual(apiCalls, [])
})
