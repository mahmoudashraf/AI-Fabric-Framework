import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const code = ts.transpileModule(await readFile(new URL('./workflowGoalPolicy.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const exports = {}
new Function('exports', code)(exports)
const { effectiveQuickLimits, segmentPolicyFields, validGoalPolicy, newGoalPolicy } = exports

test('published autonomous segment ceilings do not retain the quick-edit two-correction limit', () => {
  assert.equal(segmentPolicyFields(false).find(([id]) => id === 'maxCorrections')[3], 2)
  assert.equal(segmentPolicyFields(true).find(([id]) => id === 'maxCorrections')[3], 8)
  assert.equal(segmentPolicyFields(true).find(([id]) => id === 'maxModelCalls')[3], 64)
})
test('missing or disabled policy stays unconfigured and enabling defaults matches the native policy', () => {
  assert.equal(validGoalPolicy(undefined), true)
  assert.equal(validGoalPolicy(null), true)
  assert.equal(validGoalPolicy({ enabled: false }), true)
  assert.equal(validGoalPolicy(newGoalPolicy), true)
  assert.equal(validGoalPolicy({ ...newGoalPolicy, maxElapsedSeconds: 2592000, maxConsecutiveNoProgress: 1000, maxConsecutivePlannerFailures: 100 }), true)
})
test('goal fields respect the published execution contract without accepting infinite or malformed allowances', () => {
  for (const patch of [{ maxElapsedSeconds: Infinity }, { continuationDelaySeconds: -1 }, { maxConsecutivePlannerFailures: 0 }, { maxConsecutiveNoProgress: 1.5 }, { maxElapsedSeconds: undefined }]) assert.equal(validGoalPolicy({ ...newGoalPolicy, ...patch }), false)
})
test('disabled goals preserve stored segment values while Quick edit uses the bounded minimums', () => {
  const stored = { maxActionSteps: 64, maxWriteSteps: 32, maxModelCalls: 64, maxCorrections: 4, deadlineSeconds: 140, future: 'preserved' }
  const original = structuredClone(stored)
  assert.deepEqual(effectiveQuickLimits(stored), { maxActionSteps: 8, maxWriteSteps: 3, maxModelCalls: 16, maxCorrections: 2, deadlineSeconds: 120 })
  assert.deepEqual(stored, original)
  assert.equal(effectiveQuickLimits({ ...stored, maxCorrections: 1 }).maxCorrections, 1)
  assert.equal(effectiveQuickLimits({ ...stored, maxCorrections: Infinity }).maxCorrections, undefined)
})
