import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

function load(file, dependencies) {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
  } }).outputText
  const exports = {}
  new Function('require', 'exports', compiled)(name => {
    assert.ok(name in dependencies, `Unexpected dependency: ${name}`)
    return dependencies[name]
  }, exports)
  return exports
}
const noop = () => {}
const config = { getWidgetConfig: () => ({}), emitEvent: noop }
const canonical = load('../src/chatResult.ts', { '@/config': config })
let messages = [], pending
const { useChatFlow } = load('../src/hooks/useChatFlow.ts', {
  react: { useCallback: fn => fn },
  '@/api/chat': { createConversationId: () => 'conversation', usesCanonicalConversationMessages: () => false,
    resolvedChatMessageUrl: () => '/api/chat/me/query', postChatQuery: () => new Promise(resolve => { pending = resolve }) },
  '@/api/client': { isPublicRuntimeSessionInvalidatedError: () => false },
  '@/attachments': { toRuntimeAttachments: () => [] },
  '@/config': config,
  '@/utils': { hasShopifyRequestContext: () => false, normalizeMessageContent: value => value,
    withRequestContext: (payload, context) => ({ ...payload, context }) },
  '@/chatResult': canonical,
  '@/authenticationRequirement': { extractWorkspaceAuthenticationRequirement: () => undefined,
    dispatchWorkspaceAuthenticationRequirement: noop },
  '@/requestSemantics': { resolveChatRequestSemantics: ({ query }) => ({ query, mode: 'executor', position: 'landing' }) },
  '../workspaceHost': { updateWorkspaceHost: noop },
})
const flow = useChatFlow({ chatQuery: '', setChatQuery: noop,
  setChatMessages: update => { messages = typeof update === 'function' ? update(messages) : update },
  attachedItems: [], searchCategory: null, currentConversationId: null, setCurrentConversationId: noop,
  setIsLoading: noop, setSuggestions: noop, setCurrentPosition: noop, setCurrentMode: noop,
  setLastRequestData: noop, setLastResponseData: noop, setSelectedDebugMessage: noop,
  currentPosition: 'landing', currentMode: 'executor',
})
const response = { data: { success: true, type: 'INFORMATION_PROVIDED', answer: 'Completed answer.',
  suggestions: [{ query: 'A useful next question' }] }, status: 200, durationMs: 1 }
for (const state of ['current', 'reset', 'newer-turn']) {
  messages = []
  const turn = flow.handleChatQuery('A normal read-only question')
  assert.equal(messages.length, 1)
  if (state === 'reset') messages = []
  if (state === 'newer-turn') messages.push({ id: 'newer-user-turn', type: 'user', content: 'A different question' })
  pending(response)
  await turn
  const answer = messages.at(-1)
  assert.equal(answer.content, response.data.answer)
  assert.equal(answer.result.nextSteps.length, state === 'current' ? 1 : 0)
}
console.log('Chat suggestion race passed: normal recommendations survive; reset and newer-turn completions cannot reactivate stale choices.')
