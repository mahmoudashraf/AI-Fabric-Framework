import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const code = ts.transpileModule(await readFile(new URL('./providerDiagnostics.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const exports = {}
new Function('exports', code)(exports)
const { providerDiagnostics } = exports

test('only published exchange metadata is rendered, never raw model/source/reasoning or nested payloads', () => {
  const safe = providerDiagnostics({ observations: [{ operation: 'CHAT_COMPLETION', model: 'configured-model', finishReason: 'length', effectiveOutputCap: 8192, inputTokens: 120, outputTokens: 30, reasoningTokens: 12, contentEmpty: true,
    rawResponse: 'NEVER_RENDER_RAW', source: 'NEVER_RENDER_SOURCE', reasoning: 'NEVER_RENDER_REASONING', headers: { authorization: 'NEVER_RENDER_AUTH' }, responseModel: { body: 'nested-private' } }], droppedObservations: 4, request: 'NEVER_RENDER_REQUEST' })
  assert.deepEqual(safe, { observations: [{ operation: 'CHAT_COMPLETION', model: 'configured-model', effectiveOutputCap: 8192, finishReason: 'length', inputTokens: 120, outputTokens: 30, reasoningTokens: 12, contentEmpty: true }], droppedObservations: 4 })
  assert.equal(JSON.stringify(safe).includes('NEVER_RENDER'), false)
})
test('unknown values remain null or absent rather than fabricated zeroes and planner purposes', () => {
  assert.equal(providerDiagnostics(undefined), null)
  assert.equal(providerDiagnostics({ rawResponse: 'private' }), null)
  assert.deepEqual(providerDiagnostics({ observations: [{ finishReason: null, outputTokens: null, operation: 'PLANNING', contentLength: -1, inputTokens: '123', effectiveOutputCap: Infinity }] }), { observations: [{ finishReason: null, outputTokens: null }] })
})
test('provider metadata remains bounded to the latest 32 observations and 256-character strings', () => {
  const result = providerDiagnostics({ observations: Array.from({ length: 40 }, (_, id) => ({ attemptId: String(id), model: 'm'.repeat(257) })), droppedObservations: 9 })
  assert.equal(result.observations.length, 32); assert.equal(result.observations[0].attemptId, '8')
  assert.equal(result.observations.at(-1).attemptId, '39'); assert.equal(result.droppedObservations, 9)
  assert.ok(result.observations.every(row => !('model' in row)))
})
