import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import ts from 'typescript'

const source = readFileSync(new URL('../src/components/Chat/AIThinkingAnimation.tsx', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  fileName: 'AIThinkingAnimation.tsx',
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
    jsx: ts.JsxEmit.ReactJSX,
  },
}).outputText
const module = { exports: {} }
new Function('require', 'module', 'exports', compiled)(createRequire(import.meta.url), module, module.exports)
const { AIThinkingAnimation } = module.exports
const render = props => renderToStaticMarkup(React.createElement(AIThinkingAnimation, props))
const message = (id, type, content, extra = {}) => ({ id, type, content, timestamp: '2026-10-30T12:00:00Z', ...extra })
const document = id => ({ id, title: `Guide ${id}`, type: 'document', content: 'Existing context', similarity: 0.99 })

const requests = [
  ['support', 'Show my support case status'],
  ['support', 'Track my deployment issue and review the logs'],
  ['cars', 'Show available cars'],
  ['cars', 'Compare these vehicles'],
  ['shopping', 'Show laptops and computers'],
  ['shopping', 'Buy this phone'],
  ['shopping', 'Browse Apple products and headphones'],
  ['shopping', 'Track my order and shipment'],
  ['generic', 'Hello'],
]
const forbiddenClaims = /\b(database|inventory|products?|pricing|stock|shipment|searching|querying|retrieving|executing|executed|checking|verifying|filtering|calculating|comparing|analyzing|reviewing|generating|processing)\b/i
const contexts = [
  { history: [], documents: [], attachments: 0, expected: [] },
  { history: [message('ai', 'ai', 'Earlier reply')], documents: [document('one')], attachments: 1,
    expected: ['1 attached item', '1 other message in this conversation', '1 document in this conversation'] },
  { history: [message('u1', 'user', 'Earlier question'), message('a1', 'ai', 'Earlier answer'),
    message('u2', 'user', 'Another question'), message('a2', 'ai', 'Another answer')],
    documents: ['one', 'two', 'three', 'four', 'five', 'one'].map(document), attachments: 2,
    expected: ['2 attached items', '4 other messages in this conversation', '5 documents in this conversation',
      'Conversation documents (5)', '+2 more documents'] },
]

for (const context of contexts) {
  let baseline
  for (const [domain, query] of requests) {
    const props = {
      messages: [...context.history.map(item => ({ ...item,
        result: { sanitizedPayload: { data: { products: [{ sku: domain }] } } },
      })), message('current', 'user', query, {
        searchCategory: domain,
        documents: context.documents,
      })],
      attachedItems: Array.from({ length: context.attachments }, () => ({ type: domain, data: { name: domain, sku: domain } })),
    }
    const before = JSON.stringify(props)
    const html = render(props)
    assert.equal(JSON.stringify(props), before, 'Pending presentation must not mutate request/context')
    const escapedQuery = renderToStaticMarkup(React.createElement(React.Fragment, null, query))
    assert.ok(html.includes(escapedQuery), 'The actual request remains visible')
    const pending = html.replace(escapedQuery, '__REQUEST__')
    baseline ??= pending
    assert.equal(pending, baseline, `${domain}: identical context must render identical pending UI`)
    assert.ok(pending.includes('Response pending...'))
    assert.ok(pending.includes('Waiting for a response'))
    assert.doesNotMatch(pending, forbiddenClaims, 'No inferred domain or execution claims')
    assert.doesNotMatch(pending, /% match|Knowledge base/, 'Historical context must not imply current retrieval')
    for (const count of context.expected) assert.ok(pending.includes(count), count)
    if (context.documents.length > 3) {
      assert.equal((pending.match(/Guide /g) || []).length, 3, 'Keep the three-document display limit')
    }
    if (!context.attachments) assert.doesNotMatch(pending, /attached item/)
    if (!context.history.length) assert.doesNotMatch(pending, /other message/)
    if (!context.documents.length) assert.doesNotMatch(pending, /Conversation documents|document in this conversation/)
  }
}

assert.doesNotThrow(() => render({ messages: [] }), 'Empty context and omitted attachments remain supported')
const duplicateDocuments = render({ messages: [
  message('a', 'ai', 'Earlier reply', { documents: [document('same')] }),
  message('u', 'user', 'Next question', { documents: [document('same')] }),
] })
assert.ok(duplicateDocuments.includes('1 document in this conversation'), 'Deduplicate documents across messages')
assert.ok(duplicateDocuments.includes('Conversation documents (1)'))

console.log('AI pending animation smoke passed: domain-independent UI, actual context counts, document deduplication, no inferred execution claims, and unchanged inputs.')
