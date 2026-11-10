import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('./validationFeedback.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText
const exports = {}
new Function('exports', code)(exports)
const { feedbackCeiling, feedbackPolicy, feedbackProgress } = exports
const declared = { defaultEnabled: true, maxCorrections: 2 }

test('policy uses declared defaults and always sends an explicit disabled policy without capability', () => {
  assert.deepEqual(feedbackPolicy(declared, {}), { enabled: true, maxCorrections: 2 })
  assert.deepEqual(feedbackPolicy({ ...declared, defaultEnabled: false }, {}), { enabled: false, maxCorrections: 0 })
  assert.deepEqual(feedbackPolicy(undefined, { enabled: true, maxCorrections: 2 }), { enabled: false, maxCorrections: 0 })
})

test('opt out and explanation/Kit restrictions send zero corrections without changing preferences', () => {
  assert.deepEqual(feedbackPolicy(declared, { enabled: false, maxCorrections: 2 }), { enabled: false, maxCorrections: 0 })
  const preference = { enabled: true, maxCorrections: 1 }
  assert.deepEqual(feedbackPolicy(declared, preference, false), { enabled: false, maxCorrections: 0 })
  assert.deepEqual(feedbackPolicy(declared, preference), { enabled: true, maxCorrections: 1 })
  assert.deepEqual(preference, { enabled: true, maxCorrections: 1 })
})

test('selector policy stays within the declared ceiling and approved maximum of two', () => {
  assert.equal(feedbackCeiling({ ...declared, maxCorrections: 5 }), 2)
  for (const maxCorrections of [0, -1, 1.5, NaN, Infinity]) {
    assert.equal(feedbackCeiling({ ...declared, maxCorrections }), 0)
    assert.deepEqual(feedbackPolicy({ ...declared, maxCorrections }, {}), { enabled: false, maxCorrections: 0 })
  }
  assert.deepEqual(feedbackPolicy({ ...declared, maxCorrections: 1 }, { maxCorrections: 2 }), { enabled: true, maxCorrections: 1 })
  assert.deepEqual(feedbackPolicy(declared, { maxCorrections: 0 }), { enabled: true, maxCorrections: 1 })
  assert.deepEqual(feedbackPolicy(declared, { maxCorrections: NaN }), { enabled: true, maxCorrections: 2 })
})

test('progress uses the running attempt and original plus correction budget, never array length or prose', () => {
  const turn = { status: 'RUNNING', validationFeedback: { enabled: true, maxCorrections: 2 }, attempts: [{ number: 1, status: 'VALIDATION_ERRORS' }, { number: 2, status: 'RUNNING' }], feedbackStopReason: 'CORRECTING_VALIDATION_ERRORS' }
  assert.equal(feedbackProgress(turn), 'Attempt 2 of 3')
  assert.equal(feedbackProgress({ ...turn, attempts: [{ number: 3, status: 'RUNNING' }] }), 'Attempt 3 of 3')
  assert.equal(feedbackProgress({ ...turn, validationFeedback: { enabled: false, maxCorrections: 0 }, attempts: [{ number: 1, status: 'RUNNING' }] }), 'Attempt 1 of 1')
  assert.equal(feedbackProgress({ status: 'RUNNING', message: 'Attempt 2 of 3' }), undefined)
  assert.equal(feedbackProgress({ ...turn, validationFeedback: undefined }), undefined)
})

test('completed, failed and cancelled turns cannot show stale active progress or become saved', () => {
  for (const status of ['SAVED', 'INVALID', 'FAILED', 'CANCELLED', 'PREVIEW', 'EXPLAIN']) {
    const turn = { status, validationFeedback: { enabled: true, maxCorrections: 2 }, attempts: [{ number: 2, status: 'RUNNING', findings: { valid: false } }] }
    const before = structuredClone(turn)
    assert.equal(feedbackProgress(turn), undefined)
    assert.deepEqual(turn, before)
  }
  for (const attempts of [undefined, [], [{ number: 1, status: 'INVALID_OUTPUT' }], [{ number: 4, status: 'RUNNING' }]]) {
    assert.equal(feedbackProgress({ status: 'RUNNING', validationFeedback: { enabled: true, maxCorrections: 2 }, attempts }), undefined)
  }
})
