import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const config = moduleUrl('export function getWidgetConfig() { return {}; }')
const compiled = ts.transpileModule(readFileSync(new URL('../src/chatResult.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText.replaceAll('"@/config"', JSON.stringify(config))
const { canonicalChatResult } = await import(moduleUrl(compiled))

const payload = {
  type: 'COMPOUND_HANDLED', success: true, answer: 'Some items need clarification.',
  results: [
    { type: 'INFORMATION_PROVIDED', success: true, answer: 'The approved policy requires photo ID.',
      data: { secret: 'must-not-be-projected' } },
    { type: 'CLARIFICATION_REQUIRED', success: false, answer: 'Which item do you mean?',
      fallbackReason: 'TARGET_REQUIRED', actions: [{ action: 'must-not-be-replayed' }] },
  ],
}
const result = canonicalChatResult(payload)
assert.equal(result.success, true)
assert.equal(result.sanitizedPayload.answer, payload.answer)
assert.deepEqual(result.sanitizedPayload.data.results, [
  { type: 'INFORMATION_PROVIDED', success: true, message: payload.results[0].answer, errorCode: undefined },
  { type: 'CLARIFICATION_REQUIRED', success: false, message: payload.results[1].answer, errorCode: 'TARGET_REQUIRED' },
])
assert.equal(JSON.stringify(result).includes('must-not-be-projected'), false)
assert.equal(JSON.stringify(result).includes('must-not-be-replayed'), false)
assert.equal(payload.results[1].success, false)

for (const value of [undefined, null, {}, 'invalid']) {
  assert.equal(canonicalChatResult({ ...payload, results: value }).sanitizedPayload.data.results, undefined)
}
assert.deepEqual(canonicalChatResult({ ...payload, results: [null, 1, [], {},
  { type: 'ERROR', success: 'false' }, { type: 'ERROR', success: false, answer: 'Not completed.' },
] }).sanitizedPayload.data.results, [
  { type: 'ERROR', success: false, message: 'Not completed.', errorCode: undefined },
])
assert.equal(canonicalChatResult({ ...payload, type: 'INFORMATION_PROVIDED' }).sanitizedPayload.data.results, undefined)
assert.equal(canonicalChatResult({ type: 'ERROR', success: false, answer: 'Denied.' }).success, false)

console.log('Compound result projection smoke passed: sanitized display fields only, statuses preserved, no action replay.')
