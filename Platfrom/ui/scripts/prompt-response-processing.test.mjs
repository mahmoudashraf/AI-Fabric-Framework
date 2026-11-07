import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'

const source = fs.readFileSync(new URL('../src/pages/PromptsPage.tsx', import.meta.url), 'utf8')
const tree = ts.createSourceFile('PromptsPage.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const selected = new Set(['defaultPromptState', 'isRecord', 'normalizePromptState', 'statesEqual',
  'createPromptPreviewPayload', 'parseRagSimilarityThreshold', 'parsePositiveInteger', 'createPromptConfigPayload'])
const declarations = tree.statements.filter(node => ts.isFunctionDeclaration(node) && selected.has(node.name.text))
assert.equal(declarations.length, selected.size)
const promptFields = tree.statements.find(node => ts.isVariableStatement(node) && node.declarationList.declarations.some(value => value.name.getText(tree) === 'PROMPT_FIELDS'))
const compiled = ts.transpileModule([promptFields, ...declarations].map(node => node.getText(tree)).join('\n'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText
const api = new Function(compiled + '\nreturn {defaultPromptState, normalizePromptState, statesEqual, createPromptConfigPayload, createPromptPreviewPayload};')()

test('missing/default choice preserves authored wording and saves an explicit false', () => {
  for (const input of [undefined, {}, { removeGenericAssistanceClosers: false }]) {
    const state = api.normalizePromptState(input)
    assert.equal(state.removeGenericAssistanceClosers, false)
    assert.equal(api.createPromptConfigPayload(state).removeGenericAssistanceClosers, false)
  }
})

test('explicit choice survives save/reload, toggles dirty state and preserves prompt instructions', () => {
  const saved = api.normalizePromptState({ systemPrompt: 'Respect ownership.', answerGenerationPrompt: 'Explain useful trade-offs.' })
  const enabled = { ...saved, removeGenericAssistanceClosers: true }
  assert.equal(api.statesEqual(saved, enabled), false)
  const payload = api.createPromptConfigPayload(enabled)
  assert.equal(payload.systemPrompt, 'Respect ownership.')
  assert.equal(payload.answerGenerationPrompt, 'Explain useful trade-offs.')
  assert.equal(api.normalizePromptState(payload).removeGenericAssistanceClosers, true)
  assert.equal(api.statesEqual(enabled, api.normalizePromptState(payload)), true)
  assert.equal(api.createPromptConfigPayload({ ...enabled, removeGenericAssistanceClosers: false }).removeGenericAssistanceClosers, false)
})

test('non-boolean values never opt into rewriting and prompt-only previews omit the runtime switch', () => {
  for (const value of ['true', 'false', 1, [], {}]) {
    assert.equal(api.normalizePromptState({ removeGenericAssistanceClosers: value }).removeGenericAssistanceClosers, false)
  }
  assert.equal('removeGenericAssistanceClosers' in api.createPromptPreviewPayload({ ...api.defaultPromptState(), removeGenericAssistanceClosers: true }), false)
})
