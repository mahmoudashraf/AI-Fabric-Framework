import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/pages/PromptsPage.tsx', import.meta.url), 'utf8')
const ast = ts.createSourceFile('PromptsPage.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const names = ['defaultPromptState', 'isRecord', 'normalizePromptState', 'parseRagSimilarityThreshold', 'parsePositiveInteger', 'createPromptConfigPayload']
const selected = ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.includes(node.name?.text) ||
  ts.isVariableStatement(node) && node.declarationList.declarations.some(item => item.name.getText(ast) === 'PROMPT_FIELDS'))
const compiled = ts.transpileModule(`${selected.map(node => node.getText(ast)).join('\n')}\nObject.assign(exports, { ${names.join(',')} });`, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText
const helpers = {}
new Function('exports', compiled)(helpers)

test('workspace round trip preserves absent strings, numeric representations, unknown fields and profile metadata', () => {
  const value = { systemPrompt: '  exact\n', ragSimilarityThreshold: '0.20', ragMaxDocumentsUsedForContext: '012',
    unknown: { deeply: ['retained', false] }, aiBehaviour: { sourcePrompts: { systemPrompt: 'source' }, future: { revision: 9 } } }
  const form = helpers.normalizePromptState(value)
  assert.deepEqual(helpers.createPromptConfigPayload(form, value, true), value)
  assert.deepEqual(helpers.createPromptConfigPayload({ ...form, systemPrompt: 'Changed\n' }, value, true), { ...value, systemPrompt: 'Changed\n' })
})

test('workspace clearing an override deletes only that override', () => {
  const value = { systemPrompt: 'exact', ragSimilarityThreshold: 0.2, ragMaxContextChars: 9000, smartSuggestionsEnabled: true, future: ['keep'] }
  const form = helpers.normalizePromptState(value)
  const changed = helpers.createPromptConfigPayload({ ...form, ragMaxContextChars: '', smartSuggestionsMode: 'inherit' }, value, true)
  assert.deepEqual(changed, { systemPrompt: 'exact', ragSimilarityThreshold: 0.2, future: ['keep'] })
})

test('workspace numeric validation still rejects impossible execution settings', () => {
  assert.equal(helpers.parseRagSimilarityThreshold('1.01'), null)
  assert.equal(helpers.parsePositiveInteger('-1'), null)
  assert.equal(helpers.parsePositiveInteger('1.5'), null)
  assert.equal(helpers.parsePositiveInteger('500'), 500)
})
