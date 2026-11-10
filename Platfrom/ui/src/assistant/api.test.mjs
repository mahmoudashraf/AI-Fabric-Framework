import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('./api.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const calls = [], exports = {}
new Function('require', 'exports', code)(name => name === '../api/platformApi' ? { platformRequest: async (...args) => { calls.push(args); return {} } } : { parseExactJson: JSON.parse, stringifyExactJson: JSON.stringify }, exports)

test('session creation projects formal identity only, never page context', async () => {
  await exports.assistantApi.create({ resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'MODELS', editorContext: { section: 'SEARCH' } })
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'MODELS' })
})
test('PREPARE sends empty or exact hint plus per-turn context with existing idempotency', async () => {
  for (const query of ['', '  Focus on read endpoints\n']) {
    const input = { command: 'PREPARE', query, previewOnly: false, sourceIds: ['doc'], operationIds: ['read'], editorContext: { section: 'ACTIONS', selectedDefinitionIds: ['exact-id'] } }
    await exports.assistantApi.turn('session-37', input, 'same-key')
    assert.deepEqual(JSON.parse(calls.at(-1)[1].body), input)
    assert.equal(calls.at(-1)[1].headers['Idempotency-Key'], 'same-key')
  }
})
test('ordinary typed requests carry fresh editor context with no generated intent or command', async () => {
  const input = { query: '  Use this model, exactly.\n', previewOnly: false, sourceIds: [], operationIds: [], editorContext: { section: 'SEARCH' } }
  await exports.assistantApi.turn('session-37', input, 'key')
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), input)
})
