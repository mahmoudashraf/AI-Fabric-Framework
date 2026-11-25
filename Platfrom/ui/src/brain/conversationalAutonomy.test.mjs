import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const code = ts.transpileModule(await readFile(new URL('./conversationalAutonomy.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText
const exports = {}
new Function('exports', code)(exports)
const { readConversationalAutonomy, mergeConversationalAutonomy, conversationalAutonomyError } = exports

test('missing policy stays Standard without inventing saved fields', () => {
  assert.deepEqual(readConversationalAutonomy({}), {})
  assert.equal(conversationalAutonomyError({}), null)
})
test('focused changes retain all existing model, search, future and policy values', () => {
  const original = { openaiModel: 'existing-model', custom: { values: [false, 42] }, conversationalAutonomy: {
    enabled: false, maxWritesPerSegment: 0, futurePolicy: { retain: true }, instructions: 'Exact instructions.\n',
  } }
  const before = structuredClone(original)
  const updated = mergeConversationalAutonomy(original, { enabled: true, maxElapsedSeconds: 90 })
  assert.deepEqual(original, before)
  assert.deepEqual(updated.custom, original.custom)
  assert.deepEqual(updated.conversationalAutonomy, { ...original.conversationalAutonomy, enabled: true, maxElapsedSeconds: 90 })
})
test('disabling preserves limits and explicit empty/zero/false settings', () => {
  const policy = { enabled: true, maxWritesPerSegment: 0, defaultEnabled: false, instructions: '', maxElapsedSeconds: 90 }
  assert.deepEqual(mergeConversationalAutonomy({ conversationalAutonomy: policy }, { enabled: false }).conversationalAutonomy,
    { ...policy, enabled: false })
})
test('clearing one optional field restores inheritance without dropping other keys', () => {
  const updated = mergeConversationalAutonomy({ conversationalAutonomy: { enabled: true, maxElapsedSeconds: 60, future: 'kept' } }, { maxElapsedSeconds: null })
  assert.deepEqual(updated.conversationalAutonomy, { enabled: true, future: 'kept' })
})
test('unsupported stored policy is not silently replaced', () => {
  for (const policy of ['future-policy-format', [], 42, false]) assert.throws(() => mergeConversationalAutonomy({ conversationalAutonomy: policy }, { enabled: true }))
})
test('finite execution ceilings and typed flags are enforced without interpreting text', () => {
  for (const patch of [{ maxElapsedSeconds: Infinity }, { segmentSeconds: 0 }, { maxActionsPerSegment: 1.5 },
    { maxWritesPerSegment: 4, maxActionsPerSegment: 3 }, { enabled: 'true' }, { maxModelCallsPerSegment: -1 },
    { instructions: 'x'.repeat(8001) }]) assert.equal(typeof conversationalAutonomyError(patch), 'string')
  assert.equal(conversationalAutonomyError({ enabled: true, maxWritesPerSegment: 0, maxElapsedSeconds: 300 }), null)
})
