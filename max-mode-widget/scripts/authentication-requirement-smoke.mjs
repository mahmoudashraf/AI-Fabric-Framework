import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

const source = readFileSync(new URL('../src/authenticationRequirement.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
  fileName: 'authenticationRequirement.ts',
}).outputText
const {
  extractWorkspaceAuthenticationRequirement,
  WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
  dispatchWorkspaceAuthenticationRequirement,
} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

assert.deepEqual(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
    reasonCode: 'AUTHENTICATION_REQUIRED',
    retryPolicy: 'EXPLICIT_NEW_REQUEST',
    authenticationProfile: 'customer-supabase',
    capability: 'request-account-change',
  },
}), {
  schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
  reasonCode: 'AUTHENTICATION_REQUIRED',
  retryPolicy: 'EXPLICIT_NEW_REQUEST',
  authenticationProfile: 'customer-supabase',
  capability: 'request-account-change',
  message: undefined,
})

assert.equal(extractWorkspaceAuthenticationRequirement({ fallbackReason: 'AUTHENTICATION_REQUIRED' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  actions: [{ actionResult: { errorCode: 'AUTHENTICATION_REQUIRED' } }],
}), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {},
  metadata: { authenticationRequirement: {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
    reasonCode: 'AUTHENTICATION_REQUIRED',
    retryPolicy: 'EXPLICIT_NEW_REQUEST',
  } },
}), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({ result: { data: { authenticationRequirement: {
  schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
  reasonCode: 'AUTHENTICATION_REQUIRED',
  retryPolicy: 'EXPLICIT_NEW_REQUEST',
} } } }), undefined)

assert.equal(extractWorkspaceAuthenticationRequirement({ fallbackReason: 'ACCESS_DENIED' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({ errorCode: 'CAPABILITY_DENIED' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({ answer: 'Please sign in to continue.' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {
    schemaVersion: 'unknown-contract',
    reasonCode: 'AUTHENTICATION_REQUIRED',
  },
}), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
    reasonCode: 'AUTHENTICATION_REQUIRED',
    retryPolicy: 'AUTOMATIC_REPLAY',
  },
}), undefined)

for (const invalid of [
  { reasonCode: 'authentication_required', retryPolicy: 'EXPLICIT_NEW_REQUEST' },
  { reasonCode: 'AUTHENTICATION_REQUIRED' },
  { reasonCode: 'ACCESS_DENIED', retryPolicy: 'EXPLICIT_NEW_REQUEST' },
]) {
  assert.equal(extractWorkspaceAuthenticationRequirement({ authenticationRequirement: {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA, ...invalid,
  } }), undefined)
}
const requirement = extractWorkspaceAuthenticationRequirement({ authenticationRequirement: {
  schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
  reasonCode: 'AUTHENTICATION_REQUIRED', retryPolicy: 'EXPLICIT_NEW_REQUEST',
} })
let signIns = 0
let events = []
const host = {
  interactionPolicy: 'public-with-protected-capabilities', status: 'anonymous', signInAvailable: true,
  onSignIn: async () => { signIns++ },
}
await dispatchWorkspaceAuthenticationRequirement(requirement, host, 'anonymous-conversation', (type, data) => events.push({ type, data }))
assert.equal(signIns, 1)
assert.equal(events[0].type, 'authentication:required')
assert.equal(events[0].data.retryPolicy, 'EXPLICIT_NEW_REQUEST')
for (const unavailable of [
  undefined, { ...host, status: 'authenticated' }, { ...host, interactionPolicy: 'public' },
  { ...host, interactionPolicy: 'login-required' }, { ...host, signInAvailable: false }, { ...host, onSignIn: undefined },
]) await dispatchWorkspaceAuthenticationRequirement(requirement, unavailable, undefined, () => {})
assert.equal(signIns, 1)
for (const onSignIn of [() => { throw new Error('secret diagnostic') }, async () => { throw new Error('secret diagnostic') }]) {
  events = []
  await dispatchWorkspaceAuthenticationRequirement(requirement, { ...host, onSignIn }, undefined, (type, data) => events.push({ type, data }))
  assert.equal(events[1].type, 'error')
  assert.equal(JSON.stringify(events).includes('secret diagnostic'), false)
}

const chatFlowSource = readFileSync(new URL('../src/hooks/useChatFlow.ts', import.meta.url), 'utf8')
assert.match(chatFlowSource, /extractWorkspaceAuthenticationRequirement\(data\)/)
assert.match(chatFlowSource, /dispatchWorkspaceAuthenticationRequirement\(authenticationRequirement/)
assert.doesNotMatch(chatFlowSource, /authenticationRequirement[\s\S]{0,900}(?:resendChatQuery|handleChatQuery\()/)

// Exercise the real transport: a typed 401 reaches sign-in without invalidating the guest owner or retrying.
const moduleUrl = (source) => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const configUrl = moduleUrl(`export function getWidgetConfig() { return globalThis.fixtureConfig; }
  export function getWidgetStorageKey(key) { return 'fixture_' + key; }`)
let clientSource = ts.transpileModule(readFileSync(new URL('../src/api/client.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText
clientSource = clientSource.replaceAll('"@/config"', JSON.stringify(configUrl))
  .replaceAll('"@/authenticationRequirement"', JSON.stringify(moduleUrl(compiled)))
globalThis.fixtureConfig = { integrationMode: 'public-runtime-anonymous', apiConfig: { chatBaseUrl: 'https://runtime.example.test' } }
const storage = new Map([['fixture_maxmode_public_runtime_session_binding_v1', JSON.stringify({ runtimeKey: 'target-one', sessionId: 'anonymous-owner' })]])
globalThis.sessionStorage = { getItem: (key) => storage.get(key) || null, removeItem: (key) => storage.delete(key) }
const { apiFetchResponse, isPublicRuntimeSessionInvalidatedError } = await import(moduleUrl(clientSource))
let transportRequests = 0
let responseBody = { authenticationRequirement: requirement }
globalThis.fetch = async () => { transportRequests++; return new Response(JSON.stringify(responseBody), { status: 401 }) }
const request = { method: 'POST', headers: { Authorization: 'Bearer test-anonymous-owner-proof' }, body: JSON.stringify({ query: 'fixture request' }) }
const response = await apiFetchResponse('/query', request)
assert.equal(transportRequests, 1)
assert.ok(storage.has('fixture_maxmode_public_runtime_session_binding_v1'))
await dispatchWorkspaceAuthenticationRequirement(extractWorkspaceAuthenticationRequirement(await response.json()), host, 'anonymous-owner', () => {})
assert.equal(signIns, 2)
assert.equal(transportRequests, 1)
responseBody = { message: 'Please sign in', errorCode: 'AUTHENTICATION_REQUIRED' }
await assert.rejects(apiFetchResponse('/query', request), isPublicRuntimeSessionInvalidatedError)
assert.equal(transportRequests, 2)
assert.equal(signIns, 2)
assert.equal(storage.has('fixture_maxmode_public_runtime_session_binding_v1'), false)

console.log('AI Workspace authentication-requirement smoke passed.')
