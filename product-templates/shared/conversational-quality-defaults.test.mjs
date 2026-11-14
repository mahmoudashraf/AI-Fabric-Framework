import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { readConversationalQualityDefaults } from './conversational-quality-defaults.mjs'

const bootstrap = new URL('../../Platfrom/backend/src/main/resources/bootstrap/', import.meta.url)
const read = path => JSON.parse(readFileSync(new URL(path, bootstrap), 'utf8'))

test('shared defaults use the canonical bootstrap resource without a parallel policy', () => {
  const { intentExtractionPrompt, ...policy } = readConversationalQualityDefaults()
  assert.deepEqual(policy, read('brain-quality-defaults.json'))
  assert.equal(policy.schemaVersion, 'loomai-conversational-quality-defaults-v1')
  assert.equal(policy.openaiModel, 'gpt-5.4-mini-2026-03-17')
  assert.equal(policy.openaiMaxTokens, 2048)
  assert.deepEqual(policy.behaviourSelections, {
    tone: 'consultative', answerBreadth: 'grounded', answerLength: 'balanced', nextSteps: 'contextual',
  })
  assert.equal(policy.readActionResolution.maxIterations, 2)
  assert.equal(Object.hasOwn(policy, 'ragSimilarityThreshold'), false)
  assert.equal(Object.hasOwn(policy, 'answerGenerationPrompt'), false)
  assert.equal(typeof intentExtractionPrompt, 'string')
})

test('generic intent is exactly the bootstrap intent prompt plus canonical guidance, with no other prompt copied', () => {
  const policy = read('brain-quality-defaults.json')
  const prompts = read('prompts/default-prompt-config.json')
  const result = readConversationalQualityDefaults()
  assert.equal(result.intentExtractionPrompt, `${prompts.intentExtractionPrompt}\n\n${policy.intentGuidance}`)
  assert.deepEqual(Object.keys(result).sort(), [...Object.keys(policy), 'intentExtractionPrompt'].sort())
  for (const key of Object.keys(prompts).filter(key => key !== 'intentExtractionPrompt')) {
    assert.equal(Object.hasOwn(result, key), false, key)
  }
})

test('callers cannot mutate subsequent resource reads', () => {
  const result = readConversationalQualityDefaults()
  result.behaviourSelections.answerBreadth = 'custom'
  result.promptBudgets.ragMaxContextChars = 1
  result.readActionResolution.maxIterations = 99
  result.intentExtractionPrompt = 'CALLER ONLY'
  const { intentExtractionPrompt, ...reloaded } = readConversationalQualityDefaults()
  assert.deepEqual(reloaded, read('brain-quality-defaults.json'))
  assert.notEqual(intentExtractionPrompt, 'CALLER ONLY')
})
