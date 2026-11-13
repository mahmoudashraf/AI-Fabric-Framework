import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import React from 'react'
import * as jsxRuntime from 'react/jsx-runtime'
import { renderToStaticMarkup } from 'react-dom/server'
import * as icons from 'lucide-react'
import ts from 'typescript'

function load(file, dependencies = {}) {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX,
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
const chatResult = load('../src/chatResult.ts', { '@/config': config })
const shopify = load('../src/shopifyMcpResults.ts')
let response, messages, requests
const { useChatFlow } = load('../src/hooks/useChatFlow.ts', {
  react: { useCallback: fn => fn },
  '@/api/chat': {
    usesCanonicalConversationMessages: () => false,
    resolvedChatMessageUrl: () => '/fixture/query',
    postChatQuery: async () => {
      requests += 1
      return { data: response, status: 200, durationMs: 1 }
    },
  },
  '@/api/client': { isPublicRuntimeSessionInvalidatedError: () => false },
  '@/attachments': { toRuntimeAttachments: () => [] },
  '@/config': config,
  '@/utils': {
    hasShopifyRequestContext: () => true,
    normalizeMessageContent: value => value,
    withRequestContext: (payload, context) => ({ ...payload, context }),
  },
  '@/chatResult': chatResult,
  '@/authenticationRequirement': {
    extractWorkspaceAuthenticationRequirement: () => undefined,
    dispatchWorkspaceAuthenticationRequirement: noop,
  },
  '@/requestSemantics': { resolveChatRequestSemantics: ({ query }) => ({ query, mode: 'navigator', position: 'search' }) },
  '../workspaceHost': { updateWorkspaceHost: noop },
})
const flow = useChatFlow({
  chatQuery: '', setChatQuery: noop,
  setChatMessages: update => { messages = typeof update === 'function' ? update(messages) : update },
  attachedItems: [], searchCategory: null, currentConversationId: 'fixture-conversation',
  setCurrentConversationId: noop, setIsLoading: noop, setSuggestions: noop,
  setCurrentPosition: noop, setCurrentMode: noop, setLastRequestData: noop,
  setLastResponseData: noop, setSelectedDebugMessage: noop,
  currentPosition: 'search', currentMode: 'navigator',
})

const toolResult = { content: [{ type: 'text', text: JSON.stringify({ products: [{
  id: 'product-1', title: 'Harbor Desk', available: true,
  price_range: { min: { amount: 49.5, currency: 'GBP' } },
  media: [{ url: 'https://example.test/desk.jpg' }],
}] }) }] }
const action = { action: 'search_catalog', success: true, actionResult: { data: { toolResult } } }
const expectedProducts = shopify.normalizeShopifyMcpCatalogResult(action)
assert.equal(expectedProducts.products[0].title, 'Harbor Desk')

async function submit(payload, expectedAnswer) {
  response = structuredClone(payload)
  const original = structuredClone(response)
  messages = []
  requests = 0
  await flow.handleChatQuery('Compare the options and explain any limitations.')
  assert.equal(requests, 1, 'Displaying a result must not cause a retry or another provider request')
  assert.equal(messages.length, 2)
  const message = messages.at(-1)
  assert.equal(message.type, 'ai')
  assert.equal(message.content, expectedAnswer)
  assert.equal(message.result.sanitizedPayload.answer, expectedAnswer)
  assert.equal(message.result.sanitizedPayload.message, expectedAnswer)
  assert.equal(message.result.sanitizedPayload.safeSummary, expectedAnswer)
  assert.equal(message.result.sanitizedPayload.data.answer, expectedAnswer || undefined)
  assert.equal(message.success, payload.success)
  assert.equal(message.resultType, payload.type)
  assert.deepEqual(response, original, 'Displaying a result must not mutate the runtime response')
  assert.deepEqual(message.debugData.response.data, original)
  return message
}

const base = { type: 'INFORMATION_PROVIDED', success: true, actions: [action] }
const authoredAnswer = 'The desk is available, but I cannot confirm delivery before Friday.\n\n**Please check delivery first.**'
const message = await submit({ ...base, answer: authoredAnswer }, authoredAnswer)
assert.deepEqual(shopify.normalizeShopifyMcpCatalogResult(message.result.sanitizedPayload.data), expectedProducts)
await submit({ ...base, response: authoredAnswer }, authoredAnswer)
await submit({ ...base, safeSummary: 'Approved safe answer.', answer: authoredAnswer }, 'Approved safe answer.')
await submit({ ...base, answer: authoredAnswer, result: { data: { toolResult } } }, authoredAnswer)
const missingAnswer = await submit(base, '')
assert.deepEqual(shopify.normalizeShopifyMcpCatalogResult(missingAnswer.result.sanitizedPayload.data), expectedProducts)
await submit({ ...base, answer: '  ', response: null }, '')
await submit({ ...base, type: 'ERROR', success: false, answer: 'The catalogue was read, but answer generation failed.' },
  'The catalogue was read, but answer generation failed.')
await submit({ ...base, type: 'ERROR', success: false }, '')
await submit({ ...base, type: 'COMPOUND_HANDLED', answer: authoredAnswer, results: [
  { type: 'ERROR', success: false, answer: 'Delivery information was unavailable.' },
] }, authoredAnswer)
await submit({ type: 'INFORMATION_PROVIDED', success: true, answer: 'A normal non-catalogue answer.' },
  'A normal non-catalogue answer.')

// Exercise the unchanged product renderer separately from the authored narrative.
const element = tag => ({ children, ...props }) => React.createElement(tag, props, children)
const { ActionResultRenderer } = load('../src/components/ActionResultRenderer.tsx', {
  'react/jsx-runtime': jsxRuntime,
  '@/ui/button': { Button: element('button') },
  '@/ui/card': { Card: element('div'), CardContent: element('div') },
  'lucide-react': icons,
  '@/utils': { formatFieldName: value => value, formatFieldValue: value => String(value) },
  '@/shopifyMcpResults': shopify,
  '@/actionPresentation': { resolveActionPresentation: () => null },
  '@/config': config,
  './ActionPresentationSurface': {},
})
const markup = renderToStaticMarkup(React.createElement(ActionResultRenderer, {
  data: message.result.sanitizedPayload.data, messageId: message.id,
  expandedCount: 4, onExpand: noop,
}))
assert.ok(markup.includes('Harbor Desk'))
assert.ok(markup.includes('£49.50'))
assert.ok(markup.includes('https://example.test/desk.jpg'))
assert.ok(markup.includes('Available'))
assert.equal('summarizeShopifyMcpCatalogResult' in shopify, false)

console.log('Chat narrative preservation passed: 10 hook cases, unchanged structured Shopify cards, no fabricated success or extra requests.')
