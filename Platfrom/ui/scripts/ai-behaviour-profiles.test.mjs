import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

function evaluate(source, names, bindings = {}) {
  const compiled = ts.transpileModule(`${source}\nObject.assign(exports, { ${names.join(',')} });`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText
  const result = {}
  new Function('exports', ...Object.keys(bindings), compiled)(result, ...Object.values(bindings))
  return result
}
const helperSource = await readFile(new URL('../src/aiBehaviourProfiles.ts', import.meta.url), 'utf8')
const helpers = evaluate(helperSource, [])
const pageSource = await readFile(new URL('../src/pages/PromptsPage.tsx', import.meta.url), 'utf8')
const pageAst = ts.createSourceFile('PromptsPage.tsx', pageSource, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const functions = ['defaultPromptState', 'isRecord', 'normalizePromptState', 'parseRagSimilarityThreshold', 'parsePositiveInteger', 'createPromptConfigPayload']
const selected = pageAst.statements.filter(node =>
  ts.isFunctionDeclaration(node) && functions.includes(node.name?.text) ||
  ts.isVariableStatement(node) && node.declarationList.declarations.some(item => item.name.getText(pageAst) === 'PROMPT_FIELDS'))
const page = evaluate(selected.map(node => node.getText(pageAst)).join('\n'), functions)
const source = {
  systemPrompt: '  Preserve exact whitespace.\n', intentExtractionPrompt: 'Intent', actionSelectionPrompt: 'Actions',
  clarificationPrompt: 'Clarify', answerGenerationPrompt: 'Domain-specific guidance', retrievalPrompt: 'Source rules', assistantUiPrompt: 'UI',
  ragSimilarityThreshold: 0.2, ragMaxDocumentsUsedForContext: 12, ragMaxContextChars: 8000,
  responseGenerationMaxTokensConcise: 600, responseGenerationMaxTokensStandard: 1500, responseGenerationMaxTokensDeep: 2500,
  smartSuggestionsEnabled: true, unknownSetting: { enabled: false, nested: ['one', 'two'] },
}
const metadata = Object.fromEntries(helpers.AI_BEHAVIOUR_GROUP_KEYS.map(key => [key, { presetId: key === 'tone' ? 'warm' : 'grounded', revision: 1, instructions: `${key} guidance` }]))
metadata.schemaVersion = 'loomai-ai-behaviour-v1'; metadata.revision = 1

test('legacy configuration does not invent or save behaviour defaults', () => {
  assert.equal(helpers.readAiBehaviour(source), null)
  assert.deepEqual(helpers.aiBehaviourPresetSelections(source), { tone: '', answerBreadth: '', answerLength: '', nextSteps: '' })
  assert.deepEqual(helpers.aiBehaviourReviewInput(source, {}, {}), { promptConfig: source })
})

test('the real page payload preserves all seven base prompts, whitespace, unknown configuration and metadata', () => {
  const value = { ...source, aiBehaviour: metadata }
  const form = page.normalizePromptState(value)
  assert.deepEqual(page.createPromptConfigPayload(form, value), value)
  const changed = page.createPromptConfigPayload({ ...form, systemPrompt: '', ragMaxContextChars: '', smartSuggestionsMode: 'disabled' }, value)
  assert.equal(changed.systemPrompt, '')
  assert.equal(changed.smartSuggestionsEnabled, false)
  assert.equal('ragMaxContextChars' in changed, false)
  assert.deepEqual(changed.unknownSetting, source.unknownSetting)
  assert.deepEqual(changed.aiBehaviour, metadata)
})

test('advanced profile edits change only the owned instructions; the server decides custom identity', () => {
  const value = { ...source, aiBehaviour: metadata }
  const result = helpers.aiBehaviourReviewInput(value, { answerLength: 'brief' }, { tone: 'A custom tone.\n' })
  assert.equal(result.promptConfig.aiBehaviour.tone.presetId, 'warm')
  assert.equal(result.promptConfig.aiBehaviour.tone.instructions, 'A custom tone.\n')
  assert.deepEqual(result.selections, { answerLength: 'brief' })
  for (const key of helpers.AI_BEHAVIOUR_PROMPT_KEYS) assert.equal(result.promptConfig[key], value[key])
  assert.equal(value.aiBehaviour.tone.instructions, 'tone guidance')
})

test('custom profiles are presented as custom without interpreting their text', () => {
  assert.equal(helpers.aiBehaviourPresetSelections({ aiBehaviour: { ...metadata, tone: { ...metadata.tone, presetId: 'custom' } } }).tone, '')
  assert.equal(helpers.aiBehaviourPresetSelections({ aiBehaviour: metadata }).tone, 'warm')
})

test('published source/expanded provenance survives owned instruction edits until canonical API normalization', () => {
  const value = { ...source, aiBehaviour: { ...metadata, sourcePrompts: { answerGenerationPrompt: 'Original domain prompt' },
    expandedPrompts: { answerGenerationPrompt: 'Published overlay' } } }
  const result = helpers.aiBehaviourReviewInput(value, {}, { tone: 'Changed profile instructions' })
  assert.deepEqual(result.promptConfig.aiBehaviour.sourcePrompts, value.aiBehaviour.sourcePrompts)
  assert.deepEqual(result.promptConfig.aiBehaviour.expandedPrompts, value.aiBehaviour.expandedPrompts)
  assert.equal(result.promptConfig.answerGenerationPrompt, source.answerGenerationPrompt)
})

test('prompt-only previews pass only the seven server-compiled strings, never metadata or runtime knobs', () => {
  const compiled = { ...source, answerGenerationPrompt: 'Compiled profile overlay', aiBehaviour: metadata,
    smartSuggestionsEnabled: false, providerConfig: { model: 'must not be sent' } }
  const preview = helpers.effectiveAiBehaviourPreview(compiled)
  assert.deepEqual(Object.keys(preview), helpers.AI_BEHAVIOUR_PROMPT_KEYS)
  assert.equal(preview.answerGenerationPrompt, 'Compiled profile overlay')
  assert.equal(preview.systemPrompt, source.systemPrompt)
})

test('configuration identity is stable across object key order, exact across content and array order', () => {
  assert.equal(helpers.promptConfigIdentity({ a: 1, b: { x: false, y: null } }), helpers.promptConfigIdentity({ b: { y: null, x: false }, a: 1 }))
  assert.notEqual(helpers.promptConfigIdentity('Answer '), helpers.promptConfigIdentity('Answer'))
  assert.notEqual(helpers.promptConfigIdentity(['a', 'b']), helpers.promptConfigIdentity(['b', 'a']))
})

test('appended API adapters use the canonical catalogue and encoded deployment composition paths', async () => {
  const apiSource = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
  const ast = ts.createSourceFile('platformApi.ts', apiSource, ts.ScriptTarget.Latest, true)
  const names = ['fetchDeploymentAiBehaviourProfiles', 'composeDeploymentAiBehaviour']
  const calls = []
  const api = evaluate(ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.includes(node.name?.text))
    .map(node => node.getText(ast)).join('\n'), names, { request: async (...args) => { calls.push(args); return { okay: true } } })
  await api.fetchDeploymentAiBehaviourProfiles()
  await api.composeDeploymentAiBehaviour('dep/a b', { promptConfig: source, selections: { tone: 'technical' } })
  assert.equal(calls[0][0], '/api/deployment-ai-behaviour-profiles')
  assert.equal(calls[1][0], '/api/deployments/dep%2Fa%20b/ai-behaviour/compose')
  assert.deepEqual(JSON.parse(calls[1][1].body), { promptConfig: source, selections: { tone: 'technical' } })
})
