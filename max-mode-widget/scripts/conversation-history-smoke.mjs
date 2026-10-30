import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'

const result = await build({
  configFile: false, logLevel: 'error', publicDir: false,
  resolve: { alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) } },
  build: { write: false, minify: false, lib: {
    entry: fileURLToPath(new URL('../src/conversationHistory.ts', import.meta.url)), formats: ['es'],
  } },
})
const code = (Array.isArray(result) ? result[0] : result).output.find(item => item.type === 'chunk').code
const { toChatMessages } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const originalFetch = globalThis.fetch
let networkRequests = 0
globalThis.fetch = () => {
  networkRequests += 1
  throw new Error('Canonical history restoration must not fetch or replay a query')
}
const message = (id, sequence, role, content) => ({ messageId: id, sequence, role, content, createdAt: '2026-10-30T12:00:00Z' })
const receipt = (userMessageId, aiMessageId, overrides = {}) => ({ userMessageId, aiMessageId, completedAt: '2026-10-30T12:00:01Z', response: {
  type: 'INFORMATION_PROVIDED', success: true, conversationId: 'one', answer: 'Wrong retained answer',
  sources: [{ id: 'guide', title: 'Public guide', content: 'Approved excerpt', type: 'document' }],
  metadata: { secret: 'not-for-widget' }, result: { debug: 'not-for-widget' },
  authenticationRequirement: { code: 'not-for-widget' }, suggestions: ['not-for-widget'],
  ...overrides,
} })
const participantMessages = [message('ai2', 4, 'AI', 'Second canonical answer'), message('u1', 1, 'USER', 'First question'),
  message('human', 5, 'HUMAN', 'Human reply'), message('ai1', 2, 'AI', 'First canonical answer'), message('u2', 3, 'USER', 'Second question')]
const restored = toChatMessages({ id: 'one', participantMessages, completedTurns: [receipt('u1', 'ai1')] }, 'one')
assert.deepEqual(restored.map(m => m.id), ['u1', 'ai1', 'u2', 'ai2', 'human'])
assert.equal(restored[1].content, 'First canonical answer')
assert.equal(restored[1].result.sanitizedPayload.data.answer, 'First canonical answer')
assert.equal(restored[1].documents[0].messageId, 'ai1')
assert.equal(restored[1].historyPresentationUnavailable, undefined, 'Safe restored receipt needs no unavailable notice')
assert.equal(restored[3].result, undefined, 'Expired/missing second receipt stays text-only')
assert.equal(restored[3].historyPresentationUnavailable, true, 'Missing retained details are disclosed without inventing a reason')
assert.equal(restored[0].historyPresentationUnavailable, undefined, 'USER text never gets a presentation notice')
assert.equal(restored[4].result, undefined, 'No retained AI presentation on a HUMAN message')
assert.equal(restored[4].participantRole, 'human')
assert.equal(restored[4].historyPresentationUnavailable, undefined, 'HUMAN text never gets an AI presentation notice')
assert.equal(JSON.stringify(restored).includes('not-for-widget'), false, 'No raw debug/auth/result restore')
for (const [label, completedTurns] of [
  ['empty completed-turn history', []],
  ['missing response', [{ ...receipt('u1', 'ai1'), response: undefined }]],
  ['non-object response', [{ ...receipt('u1', 'ai1'), response: 'invalid' }]],
  ['array response', [{ ...receipt('u1', 'ai1'), response: [] }]],
  ['foreign conversation', [receipt('u1', 'ai1', { conversationId: 'foreign' })]],
  ['unknown user ID', [receipt('foreign-user', 'ai1')]],
  ['unknown AI ID', [receipt('u1', 'foreign-ai')]],
  ['missing AI ID', [receipt('u1', undefined)]],
  ['human message ID', [receipt('u1', 'human')]],
  ['confirmation proposal', [receipt('u1', 'ai1', { type: 'CONFIRMATION_REQUIRED' })]],
  ['clarification proposal', [receipt('u1', 'ai1', { type: 'CLARIFICATION_REQUIRED' })]],
  ['unknown result type', [receipt('u1', 'ai1', { type: 'UNKNOWN_RESULT' })]],
  ['wrong result type', [receipt('u1', 'ai1', { type: 42 })]],
]) {
  const messages = toChatMessages({ id: 'one', participantMessages, completedTurns }, 'one')
  assert.equal(messages[1].result, undefined, label)
  assert.equal(messages[1].content, 'First canonical answer', label)
  assert.equal(messages[1].historyPresentationUnavailable, true, label)
}
const legacyParticipants = toChatMessages({ id: 'one', participantMessages }, 'one')
assert.equal(legacyParticipants[1].result, undefined)
assert.equal(legacyParticipants[1].historyPresentationUnavailable, undefined, 'An absent completedTurns contract proves nothing about retained presentations')
const interleaved = toChatMessages({ id: 'one', participantMessages: [
  message('u1', 1, 'USER', 'First concurrent question'), message('u2', 2, 'USER', 'Second concurrent question'),
  message('ai1', 3, 'AI', 'First concurrent answer'), message('ai2', 4, 'AI', 'Second concurrent answer'),
], completedTurns: [
  receipt('u2', 'ai2', { sources: [{ id: 'second-guide', title: 'Second guide', content: 'Second evidence', type: 'document' }] }),
  receipt('u1', 'ai1', { sources: [{ id: 'first-guide', title: 'First guide', content: 'First evidence', type: 'document' }] }),
] }, 'one')
assert.equal(interleaved[2].documents[0].id, 'first-guide', 'AI1 must not inherit USER2 evidence when two tabs interleave')
assert.equal(interleaved[2].documents[0].messageId, 'ai1')
assert.equal(interleaved[3].documents[0].id, 'second-guide')
assert.equal(interleaved[3].documents[0].messageId, 'ai2')
assert.equal(interleaved[2].historyPresentationUnavailable, undefined)
assert.equal(interleaved[3].historyPresentationUnavailable, undefined)
const legacy = toChatMessages({ turns: [{ userQuery: 'Old question', aiResponse: 'Old answer', timestamp: 'now' }] }, 'old')
assert.deepEqual(legacy.map(m => m.content), ['Old question', 'Old answer'])
assert.equal(legacy[1].historyPresentationUnavailable, undefined)
assert.equal(networkRequests, 0, 'Unavailable presentations must never cause a query replay')
globalThis.fetch = originalFetch
console.log('Conversation history smoke passed: exact USER/AI IDs, canonical text, unavailable-detail disclosure, legacy compatibility, isolation and no query replay.')
