import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const config = moduleUrl('export function getWidgetConfig(){return {}}')
const compiled = ts.transpileModule(readFileSync(new URL('../src/chatResult.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText.replaceAll('"@/config"', JSON.stringify(config))
const { canonicalChatResult } = await import(moduleUrl(compiled))
const base = { success: true, type: 'INFORMATION_PROVIDED', answer: 'Current approved answer.' }
const suggestion = { query: 'Compare the selected options', intent: 'INFORMATION', rationale: 'Review their differences.', confidence: 0.8 }
const result = canonicalChatResult({ ...base, suggestions: [suggestion], result: { nextSteps: [{ query: 'must not read raw results' }] } })
assert.deepEqual(result.nextSteps, [suggestion])
assert.equal(result.sanitizedPayload.answer, base.answer)
assert.deepEqual(canonicalChatResult(base).nextSteps, [])
assert.deepEqual(canonicalChatResult({ ...base, success: false, suggestions: [suggestion] }).nextSteps, [])
for (const type of ['CONFIRMATION_REQUIRED', 'CLARIFICATION_REQUIRED']) {
  assert.deepEqual(canonicalChatResult({ ...base, type, suggestions: [suggestion] }).nextSteps, [])
  assert.deepEqual(canonicalChatResult({ ...base, type: 'COMPOUND_HANDLED', results: [{ type, success: true }], suggestions: [suggestion] }).nextSteps, [])
}
assert.deepEqual(canonicalChatResult({ ...base, suggestions: [null, {}, 'not a structured recommendation', { query: ' ' }] }).nextSteps, [])
assert.deepEqual(canonicalChatResult({ ...base, suggestions: [suggestion, suggestion, suggestion] }).nextSteps, [suggestion, suggestion])
assert.deepEqual(canonicalChatResult({ ...base, suggestions: [{ query: 'Explain the approved policy' }] }).nextSteps,
  [{ query: 'Explain the approved policy', intent: 'INFORMATION', rationale: '', confidence: 0 }])
console.log('AI behaviour next steps: canonical structured suggestions retained, bounded, no raw-result access, no invented recommendations or action execution.')
