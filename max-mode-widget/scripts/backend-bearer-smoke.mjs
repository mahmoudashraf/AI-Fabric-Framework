import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const compile = path => ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText
const configUrl = moduleUrl(`export function getWidgetConfig(){return globalThis.fixtureConfig}
  export function getWidgetStorageKey(key){return 'fixture_'+key}`)
const authUrl = moduleUrl(compile('../src/authenticationRequirement.ts'))
const clientUrl = moduleUrl(compile('../src/api/client.ts')
  .replaceAll('"@/config"', JSON.stringify(configUrl))
  .replaceAll('"@/authenticationRequirement"', JSON.stringify(authUrl)))
const { apiFetchJson } = await import(clientUrl)
const { openConversationEvents } = await import(moduleUrl(compile('../src/api/handover.ts')
  .replaceAll('"@/config"', JSON.stringify(configUrl)).replaceAll('"./client"', JSON.stringify(clientUrl))))

let currentToken = 'browser-token-one', tokenReads = 0, loggedOut = false
globalThis.fixtureConfig = { integrationMode: 'backend-mediated-private-runtime', apiConfig: {
  chatBaseUrl: 'https://reviewed-adapter.example.test/api', fetchCredentials: 'include',
  defaultHeaders: { 'X-Reviewed-Adapter': 'browser-session-reference' },
  runtimeAuth: { getBearerToken: async () => { tokenReads++; if (loggedOut) throw new Error('Sign in required'); return currentToken } },
  runtimeRoutes: { conversationEventsUrlTemplate: '/chat/me/conversations/{conversationId}/events' },
} }
const calls = []
globalThis.fetch = async (url, init) => {
  calls.push({ url, init })
  return new Response(JSON.stringify({ success: true }), { status: 200 })
}

await apiFetchJson('/chat/me/conversations/one')
assert.equal(calls[0].init.headers.Authorization, 'Bearer browser-token-one')
assert.equal(calls[0].init.headers['X-Reviewed-Adapter'], 'browser-session-reference')
assert.equal(calls[0].init.credentials, 'include', 'Private-adapter cookie policy must remain unchanged')

currentToken = 'browser-token-two'
await openConversationEvents('one', null, new AbortController().signal)
assert.equal(calls[1].init.headers.Authorization, 'Bearer browser-token-two')
assert.equal(calls[1].init.headers.Accept, 'text/event-stream')
currentToken = 'browser-token-three'
await openConversationEvents('one', 'last-event', new AbortController().signal)
assert.equal(calls[2].init.headers.Authorization, 'Bearer browser-token-three', 'An SSE reconnect must resolve identity again')
assert.equal(calls[2].init.headers['Last-Event-ID'], 'last-event')
assert.equal(tokenReads, 3)

loggedOut = true
await assert.rejects(apiFetchJson('/chat/me/conversations/one'), /Sign in required/)
await assert.rejects(openConversationEvents('one', null, new AbortController().signal), /Sign in required/)
assert.equal(calls.length, 3, 'No stale bearer request may be sent after the host rejects identity')

loggedOut = false
const readsBeforeExplicitHeader = tokenReads
await apiFetchJson('/chat/me/conversations/one', { headers: { authorization: 'Bearer explicit-request-proof' } })
assert.equal(calls.at(-1).init.headers.authorization, 'Bearer explicit-request-proof')
assert.equal(tokenReads, readsBeforeExplicitHeader, 'Explicit authorization must retain existing precedence')
globalThis.fixtureConfig.apiConfig.defaultHeaders.Authorization = 'Bearer configured-proof'
await apiFetchJson('/chat/me/conversations/one')
assert.equal(calls.at(-1).init.headers.Authorization, 'Bearer configured-proof')
assert.equal(tokenReads, readsBeforeExplicitHeader)

delete globalThis.fixtureConfig.apiConfig.defaultHeaders.Authorization
currentToken = null
await apiFetchJson('/chat/me/conversations/one')
assert.equal(calls.at(-1).init.headers.Authorization, undefined)
assert.equal(calls.at(-1).init.credentials, 'include', 'A cookie-backed host can intentionally return no bearer')
console.log('Backend-mediated bearer smoke passed: fresh GET/SSE identity, logout denial, header precedence and cookie compatibility.')
