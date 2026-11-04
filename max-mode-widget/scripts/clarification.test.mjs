import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'

const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const config = moduleUrl('export function getWidgetConfig() { return {}; }')
async function load(name) {
  const source = readFileSync(new URL(`../src/${name}.ts`, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText.replaceAll('"@/config"', JSON.stringify(config))
  return import(moduleUrl(compiled))
}
const { clarificationParameters, clarificationValuePresent } = await load('clarificationForm')
const { clarificationStates } = await load('clarificationState')
const { canonicalChatResult } = await load('chatResult')
const { withActionDraftSubmission } = await load('actionDraftSubmission')

const action = 'dealership_request_callback'
const provided = { message: 'I would like the dealership to call me about 2025 Aster E1.', consent: true }
const message = (id, response) => ({ id, type: 'ai', content: response.answer,
  timestamp: '2026-11-04T17:20:00Z', resultType: response.type, success: response.success,
  result: canonicalChatResult(response) })
const clarification = (id, name = action) => message(id, {
  type: 'CLARIFICATION_REQUIRED', success: false, answer: 'To proceed, please provide: name, phone.',
  actions: [{ action: name, missingRequiredParameters: ['name', 'phone'], providedParameters: provided }],
})
const cancellation = (id, name = action, overrides = {}) => message(id, {
  type: 'INFORMATION_PROVIDED', success: true, answer: 'Synthetic acknowledgement.',
  actions: [{ action: name, actionDraftCancelled: true }], ...overrides,
})

test('current callback form preserves boolean consent and the phone leading zero through structured submission', () => {
  const parameters = clarificationParameters(['name', 'phone', 'message', 'consent'], provided, {
    name: 'UI Journey Check', phone: '07700900900',
  })
  const request = withActionDraftSubmission({ conversationId: 'conversation-one', query: 'Proceed.' }, action, parameters)
  assert.deepEqual(JSON.parse(JSON.stringify(request)).actionDraftSubmission, { action, parameters: {
    name: 'UI Journey Check', phone: '07700900900', ...provided,
  } })
  assert.equal(typeof parameters.consent, 'boolean')
  assert.equal(typeof parameters.phone, 'string')
  assert.deepEqual(provided, { message: provided.message, consent: true })
})

test('all untouched JSON values retain their types without text-based coercion', () => {
  const values = { enabled: false, count: 0, ratio: 1.25, text: 'true', identifier: '00123',
    optional: null, settings: { enabled: false, values: [1, '2', null] }, tags: ['a', 'b'] }
  assert.deepEqual(clarificationParameters(Object.keys(values), values, {}), values)
  assert.deepEqual(clarificationParameters(['phone', 'note'], {}, { phone: '07700900900', note: 'false' }),
    { phone: '07700900900', note: 'false' })
  for (const value of [false, 0, true, {}, []]) assert.equal(clarificationValuePresent(value), true)
  for (const value of [null, undefined, '', '  ']) assert.equal(clarificationValuePresent(value), false)
})

test('edits follow provided types and invalid typed edits are not silently submitted as strings', () => {
  const values = { enabled: true, count: 2, settings: { old: true }, tags: ['old'] }
  assert.deepEqual(clarificationParameters(Object.keys(values), values, {
    enabled: false, count: '0', settings: '{"new":false}', tags: '["new",0]',
  }), { enabled: false, count: 0, settings: { new: false }, tags: ['new', 0] })
  for (const [key, value] of [['enabled', 'false'], ['count', ''], ['count', 'NaN'],
    ['count', 'Infinity'], ['settings', '[]'], ['settings', 'null'], ['tags', '{}'], ['tags', '[broken']]) {
    assert.throws(() => clarificationParameters([key], values, { [key]: value }), new RegExp(key))
  }
})

test('structured successful cancellation retires the preceding active clarification, and a fresh draft reopens', () => {
  const messages = [clarification('first'), clarification('repeat'), cancellation('cancel')]
  assert.deepEqual([...clarificationStates(messages)], [['first', 'superseded'], ['repeat', 'cancelled']])
  assert.deepEqual([...clarificationStates([...messages, clarification('fresh')])],
    [['first', 'superseded'], ['repeat', 'cancelled'], ['fresh', 'active']])
  assert.equal(messages[1].resultType, 'CLARIFICATION_REQUIRED', 'Presentation history remains immutable')
})

test('cancellation uses exact action IDs and only the current conversation history', () => {
  assert.equal(clarificationStates([clarification('one'), cancellation('other', 'other_action')]).get('one'), 'active')
  const firstConversation = [clarification('one'), cancellation('cancel')]
  const secondConversation = [clarification('two')]
  assert.equal(clarificationStates(firstConversation).get('one'), 'cancelled')
  assert.equal(clarificationStates(secondConversation).get('two'), 'active')
  assert.equal(clarificationStates([cancellation('before'), clarification('later')]).get('later'), 'active')
})

test('prose, failed results, human messages and string markers never acknowledge cancellation', () => {
  const ignored = [
    cancellation('failed', action, { success: false }),
    cancellation('error', action, { type: 'ERROR' }),
    cancellation('text', action, { actions: [], answer: 'The incomplete request was cancelled without submitting it.' }),
    cancellation('string', action, { actions: [{ action, actionDraftCancelled: 'true' }] }),
    cancellation('false', action, { actions: [{ action, actionDraftCancelled: false }] }),
    { ...cancellation('human'), participantRole: 'human' },
    { ...cancellation('user'), type: 'user' },
  ]
  for (const ignoredMessage of ignored) {
    assert.equal(clarificationStates([clarification('active'), ignoredMessage]).get('active'), 'active', ignoredMessage.id)
  }
})

test('canonical multi-action cancellation markers stay scoped to their action', () => {
  const cancel = cancellation('multi', action, { actions: [
    { action: 'other_action', actionDraftCancelled: true }, { action, actionDraftCancelled: true },
  ] })
  assert.equal(clarificationStates([clarification('active'), cancel]).get('active'), 'cancelled')
})

test('canonical confirmation supersedes matching intake even with the normal success:false contract', () => {
  const confirmation = message('confirmation', { type: 'CONFIRMATION_REQUIRED', success: false,
    actions: [{ action, confirmationRequired: true, providedParameters: { name: 'UI Journey Check' } }] })
  const history = JSON.parse(JSON.stringify([clarification('old'), confirmation]))
  assert.equal(clarificationStates(history).get('old'), 'superseded')
  assert.equal(clarificationStates([...history, clarification('fresh')]).get('fresh'), 'active')
  for (const actions of [[], [{ confirmationRequired: true }], [{ action }],
    [{ action, confirmationRequired: 'true' }], [{ action: 'other_action', confirmationRequired: true }]]) {
    const unrelated = message('unrelated', { type: 'CONFIRMATION_REQUIRED', success: false, actions })
    assert.equal(clarificationStates([clarification('active'), unrelated]).get('active'), 'active')
  }
})

test('successful canonical execution supersedes matching intake across serialized history', () => {
  const execution = message('receipt', { type: 'ACTION_EXECUTED', success: true,
    actions: [{ action, actionExecutionId: 'synthetic-execution', actionResult: { success: true } }] })
  const history = JSON.parse(JSON.stringify([clarification('old'), execution]))
  assert.equal(clarificationStates(history).get('old'), 'superseded')
  assert.equal(clarificationStates([...history, clarification('fresh')]).get('fresh'), 'active')
  assert.equal(clarificationStates([execution, clarification('later')]).get('later'), 'active')
})

test('failed, unidentified, unrelated or prose-only executions do not retire clarification', () => {
  for (const response of [
    { type: 'ACTION_EXECUTED', success: false, actions: [{ action, actionResult: { success: false } }] },
    { type: 'ACTION_EXECUTED', success: true, actions: [{ action, actionResult: { success: false } }] },
    { type: 'ACTION_EXECUTED', success: true, actions: [] },
    { type: 'ACTION_EXECUTED', success: true, actions: [{ action: 'other_action', actionResult: { success: true } }] },
    { type: 'INFORMATION_PROVIDED', success: true, actions: [{ action }], answer: 'Action executed successfully.' },
  ]) {
    assert.equal(clarificationStates([clarification('active'), message('outcome', response)]).get('active'), 'active')
  }
})
