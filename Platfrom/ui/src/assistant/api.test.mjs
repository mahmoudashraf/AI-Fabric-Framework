import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('./api.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const calls = [], exports = {}
new Function('require', 'exports', code)(name => name === '../api/platformApi' ? { platformRequest: async (...args) => { calls.push(args); return {} } } : { parseExactJson: JSON.parse, stringifyExactJson: JSON.stringify }, exports)

test('session creation projects formal identity only, never page context', async () => {
  await exports.assistantApi.create({ resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'SEARCH', editorContext: { section: 'SEARCH' } })
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'SEARCH' })
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
test('autonomous admission is explicit and does not inject a bounded correction ceiling', async () => {
  const input = { query: '', command: 'PREPARE', executionMode: 'AUTONOMOUS', previewOnly: false, sourceIds: ['doc'], operationIds: [] }
  await exports.assistantApi.turn('original session', input, 'root-key')
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), input)
  assert.equal(calls.at(-1)[0], '/api/configuration-assistant/sessions/original%20session/turns')
  assert.equal('validationFeedback' in JSON.parse(calls.at(-1)[1].body), false)
})
test('goal controls and directives retain exact goal and session identities', async () => {
  for (const command of ['PAUSE', 'RESUME', 'CANCEL']) {
    await exports.assistantApi.goalControl('s/1', 'g/1', command)
    assert.equal(calls.at(-1)[0], '/api/configuration-assistant/sessions/s%2F1/goals/g%2F1/control')
    assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { command })
  }
  await exports.assistantApi.directive('s/1', 'g/1', { query: 'Keep the original scope' }, 'same-directive-key')
  assert.equal(calls.at(-1)[0], '/api/configuration-assistant/sessions/s%2F1/goals/g%2F1/directives')
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { query: 'Keep the original scope' })
  assert.equal(calls.at(-1)[1].headers['Idempotency-Key'], 'same-directive-key')
})
test('receipt paging uses an authenticated read of the pinned session/goal with explicit offset and limit', async () => {
  const abort = new AbortController()
  await exports.assistantApi.goalOperations('original/session', 'original/goal', 100, 20, abort.signal)
  assert.equal(calls.at(-1)[0], '/api/configuration-assistant/sessions/original%2Fsession/goals/original%2Fgoal/operations?offset=100&limit=20')
  assert.equal(calls.at(-1)[1].signal, abort.signal)
  assert.equal(calls.at(-1)[1].method, undefined)
  assert.equal(calls.at(-1)[1].body, undefined)
})
