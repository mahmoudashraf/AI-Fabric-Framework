import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPublicActionReference } from './public-action-reference.mjs'

const action = {
  name: 'request_help', anonymousAllowed: true, description: 'Request customer assistance',
  accessMode: 'WRITE_ONLY', requiresConfirmation: true, route: { secret: 'must-not-be-projected' },
  params: [
    { name: 'email', type: 'STRING', required: true, sensitive: true, defaultValue: 'never-project-values' },
    { name: 'notes', type: 'STRING', required: false },
    { name: 'record', type: 'STRING', visibility: 'INTERNAL' },
    { name: 'token', type: 'STRING', visibility: 'SECRET' },
    { name: 'system', type: 'STRING', visibility: 'SYSTEM' },
    { name: 'session', type: 'STRING', askUser: false },
  ],
}

test('projects public schema facts, not protected values or routes', () => {
  const result = buildPublicActionReference({ actions: [action] })
  assert.deepEqual(result.actions[0].inputs, [
    { name: 'email', description: undefined, type: 'STRING', required: true },
    { name: 'notes', description: undefined, type: 'STRING', required: false },
  ])
  assert.equal(result.actions[0].requiresConfirmation, true)
  assert.equal(result.actions[0].route, undefined)
  assert.equal(result.actions[0].inputs[0].defaultValue, undefined)
})

test('excludes private and unspecified visibility actions', () => {
  const result = buildPublicActionReference({ actions: [action, { ...action, anonymousAllowed: false }, { name: 'private_without_flag' }] })
  assert.equal(result.actions.length, 1)
})

test('explicit public workflow documentation preserves its authentication requirement', () => {
  const protectedAction = { ...action, name: 'public_signed_in_workflow', anonymousAllowed: false }
  const result = buildPublicActionReference({ actions: [protectedAction] }, { publicWorkflowNames: [protectedAction.name] })
  assert.equal(result.actions[0].authenticationRequired, true)
  assert.equal(result.actions[0].requiresConfirmation, true)
  assert.equal(protectedAction.anonymousAllowed, false)
  assert.throws(() => buildPublicActionReference({ actions: [] }, { publicWorkflowNames: ['unknown'] }))
})

test('does not invent unknown required or confirmation flags', () => {
  const result = buildPublicActionReference({ actions: [{ name: 'explain', anonymousAllowed: true, params: [{ name: 'choice', type: 'STRING' }] }] })
  assert.equal(Object.hasOwn(result.actions[0], 'requiresConfirmation'), false)
  assert.equal(Object.hasOwn(result.actions[0].inputs[0], 'required'), false)
})

test('retains complete JSON items within explicit size and count bounds', () => {
  const actions = Array.from({ length: 8 }, (_, i) => ({ ...action, name: 'request_' + i }))
  const result = buildPublicActionReference({ actions }, { maxActions: 2, maxChars: 1800 })
  assert.equal(result.actions.length, 2)
  assert.equal(result.omittedActions, 6)
  assert.ok(JSON.stringify(result).length <= 1800)
  assert.deepEqual(JSON.parse(JSON.stringify(result)).actions.map(x => x.name), ['request_0', 'request_1'])
})

test('a changed source contract changes the reference identity', () => {
  assert.notEqual(buildPublicActionReference({ actions: [action] }).sourceConfigurationSha256,
    buildPublicActionReference({ actions: [{ ...action, requiresConfirmation: false }] }).sourceConfigurationSha256)
})
