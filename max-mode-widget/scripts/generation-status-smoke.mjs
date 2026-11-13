import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

// Response-only qualification: history does not restore this metadata. Do not infer it from stored prose or receipts.
const root = fileURLToPath(new URL('..', import.meta.url))
const result = await build({
  stdin: { contents: `
    export { canonicalChatResult, extractChatResultMessage } from './src/chatResult';
    export { MessageBubble } from './src/components/Chat/MessageBubble';
    export { toChatMessages } from './src/conversationHistory';
  `, resolveDir: root, loader: 'ts' },
  bundle: true, write: false, platform: 'node', format: 'cjs',
  alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) },
  external: ['react', 'react/*', 'react-dom', 'react-dom/*'], logLevel: 'silent',
})
const module = { exports: {} }
new Function('require', 'module', 'exports', result.outputFiles[0].text)(
  createRequire(new URL('../package.json', import.meta.url)), module, module.exports)
const { canonicalChatResult, extractChatResultMessage, MessageBubble, toChatMessages } = module.exports
const notice = 'Action completed. The AI explanation is unavailable.'
const marker = 'data-mxw-generation-status'
const unexpectedEffect = () => { throw new Error('Rendering must not execute or replay an action or make a network request') }
const originalFetch = globalThis.fetch
globalThis.fetch = unexpectedEffect

const base = {
  type: 'ACTION_EXECUTED', success: true, answer: '',
  actions: [{ action: 'book_visit', actionResult: { success: true, data: {
    receiptId: 'receipt-generation-status-1', status: 'confirmed',
  } } }],
}
const failure = reason => ({ used: false, skippedReason: reason, errorCode: 'GENERATION_FAILED' })
const withStatus = (status, overrides = {}) => ({ ...structuredClone(base), ...overrides,
  metadata: { postActionGeneration: status } })

function toMessage(payload) {
  const content = extractChatResultMessage(payload, '')
  const canonical = canonicalChatResult(payload, content)
  return { id: 'generation-status', type: 'ai', content, timestamp: '2026-11-13T12:00:00Z',
    result: canonical, resultType: canonical.type, success: canonical.success }
}

function render(message) {
  return renderToStaticMarkup(React.createElement(MessageBubble, {
    message, isLatest: true, clarificationBusy: false, latestMessageRef: { current: null },
    aiStyles: { bg: '', border: '', text: '', iconColor: '', label: '' },
    isPanelVisible: false, attachedItems: [], confirmationStatus: {}, expandedCount: 4, debugEnabled: false,
    onOpenDebug: unexpectedEffect, onResendAction: unexpectedEffect, onReattachItem: unexpectedEffect,
    onOpenSourcesMobile: unexpectedEffect, onOpenSourcesDesktop: unexpectedEffect, onConfirm: unexpectedEffect,
    onExpandActionResults: unexpectedEffect, isItemAttached: () => false,
    onAttachActionResultItem: unexpectedEffect, onNextStepClick: unexpectedEffect,
    onPresentationAsk: unexpectedEffect, onAttachPresentationResult: unexpectedEffect,
    onDetachPresentationResult: unexpectedEffect, isPresentationResultAttached: () => false,
    onCustomerAccountConnect: unexpectedEffect,
  }))
}

try {
  for (const reason of ['generation_failed', 'empty_generation_response']) {
    const payload = withStatus({ ...failure(reason), rawException: 'PRIVATE_EXCEPTION_DO_NOT_PROJECT',
      providerPayload: { credential: 'PRIVATE_PROVIDER_PAYLOAD_DO_NOT_PROJECT' }, message: 'PRIVATE_MESSAGE_DO_NOT_PROJECT' })
    payload.metadata.otherInternalData = 'PRIVATE_METADATA_DO_NOT_PROJECT'
    const original = structuredClone(payload)
    const message = toMessage(payload)
    assert.equal(extractChatResultMessage(payload, 'I processed your confirmation.'), '')
    assert.equal(canonicalChatResult(payload).sanitizedPayload.answer, '')
    assert.deepEqual(message.result.sanitizedPayload.metadata, { postActionGeneration: failure(reason) })
    assert.equal(message.content, '')
    assert.equal(message.result.sanitizedPayload.answer, '')
    assert.equal(message.success, true)
    assert.equal(message.result.type, 'ACTION_EXECUTED')
    assert.deepEqual(message.result.sanitizedPayload.data.actionResult, base.actions[0].actionResult)
    for (const value of ['PRIVATE_EXCEPTION_DO_NOT_PROJECT', 'PRIVATE_PROVIDER_PAYLOAD_DO_NOT_PROJECT',
      'PRIVATE_MESSAGE_DO_NOT_PROJECT', 'PRIVATE_METADATA_DO_NOT_PROJECT']) {
      assert.equal(JSON.stringify(message.result).includes(value), false)
    }
    const markup = render(message)
    assert.equal(markup.split(marker).length - 1, 1)
    assert.ok(markup.includes('role="status"'))
    assert.ok(markup.includes(notice))
    assert.ok(markup.includes(base.actions[0].actionResult.data.receiptId))
    assert.ok(markup.indexOf(base.actions[0].actionResult.data.receiptId) < markup.indexOf(notice))
    assert.equal(markup.includes('I processed your query successfully.'), false)
    assert.equal(markup.includes('>Confirm<'), false)
    assert.equal(markup.includes('>Reject<'), false)
    assert.deepEqual(payload, original)
  }

  const authored = 'Your request was completed. Keep the supplied receipt.'
  const preserved = toMessage(withStatus(failure('generation_failed'), { answer: authored }))
  assert.equal(preserved.content, authored)
  assert.equal(preserved.result.sanitizedPayload.answer, authored)
  assert.ok(render(preserved).includes(authored))
  assert.ok(render(preserved).includes(notice))

  const excluded = [
    ['no status', structuredClone(base)],
    ['generation succeeded', withStatus({ used: true, skippedReason: 'generation_failed', errorCode: 'GENERATION_FAILED' })],
    ['generation disabled', withStatus({ used: false, skippedReason: 'disabled', errorCode: 'GENERATION_FAILED' })],
    ['grounding insufficient', withStatus({ used: false, skippedReason: 'grounding_insufficient', errorCode: 'GENERATION_FAILED' })],
    ['no attempt', withStatus({ used: false, skippedReason: 'no_successful_action', errorCode: 'GENERATION_FAILED' })],
    ['missing error code', withStatus({ used: false, skippedReason: 'generation_failed' })],
    ['different error code', withStatus({ ...failure('generation_failed'), errorCode: 'OTHER_FAILURE' })],
    ['different code casing', withStatus({ ...failure('generation_failed'), errorCode: 'generation_failed' })],
    ['string false', withStatus({ ...failure('generation_failed'), used: 'false' })],
    ['unknown reason', withStatus({ ...failure('generation_failed'), skippedReason: 'unavailable' })],
    ['array status', withStatus([failure('generation_failed')])],
    ['null status', withStatus(null)],
    ['failed execution', withStatus(failure('generation_failed'), { success: false })],
    ['unconfirmed execution success', withStatus(failure('generation_failed'), { success: undefined })],
    ...['ERROR', 'ACTION_DENIED', 'INFORMATION_PROVIDED', 'CONFIRMATION_REQUIRED', 'CLARIFICATION_REQUIRED']
      .map(type => [type, withStatus(failure('generation_failed'), { type })]),
    ['plain error response', { type: 'ERROR', success: false, answer: 'A supplied error stays visible.' }],
    ['read without generation', { type: 'INFORMATION_PROVIDED', success: true, answer: 'Read result.' }],
    ['prose only', { ...structuredClone(base), answer: notice,
      metadata: { message: 'generation_failed', errorCode: 'GENERATION_FAILED' } }],
    ['unrelated metadata', { ...structuredClone(base), metadata: { providerError: 'GENERATION_FAILED' } }],
    ['compound status not supported', withStatus(failure('generation_failed'), { type: 'COMPOUND_HANDLED',
      results: [{ type: 'ACTION_EXECUTED', success: true, answer: 'Completed.', metadata: { postActionGeneration: failure('generation_failed') } }] })],
  ]
  for (const [label, payload] of excluded) {
    const original = structuredClone(payload)
    const message = toMessage(payload)
    assert.equal(message.result.sanitizedPayload.metadata, undefined, label)
    assert.equal(render(message).includes(marker), false, label)
    assert.equal(message.content, payload.answer || '', label)
    assert.deepEqual(payload, original, label)
  }

  const safe = toMessage(withStatus(failure('generation_failed')))
  for (const mutate of [
    message => { message.type = 'user' }, message => { message.participantRole = 'human' },
    message => { message.resultType = 'ERROR' }, message => { message.success = false },
    message => { message.result.success = false }, message => { message.result.sanitizedPayload.success = false },
    message => { message.result.sanitizedPayload.type = 'INFORMATION_PROVIDED' },
  ]) {
    const message = structuredClone(safe)
    mutate(message)
    assert.equal(render(message).includes(marker), false)
  }

  const confirmation = toMessage({ type: 'CONFIRMATION_REQUIRED', success: true, answer: 'Confirm this booking.',
    actions: [{ action: 'book_visit', confirmationRequired: true }],
    metadata: { postActionGeneration: failure('generation_failed') } })
  const confirmationMarkup = render(confirmation)
  assert.ok(confirmationMarkup.includes('>Confirm<'))
  assert.ok(confirmationMarkup.includes('>Reject<'))
  assert.equal(confirmationMarkup.includes(marker), false)

  const restored = toChatMessages({ id: 'history-one', participantMessages: [
    { messageId: 'user-one', sequence: 1, role: 'USER', content: 'Book a visit.', createdAt: '2026-11-13T12:00:00Z' },
    { messageId: 'ai-one', sequence: 2, role: 'AI', content: '', createdAt: '2026-11-13T12:00:01Z' },
  ], completedTurns: [{ userMessageId: 'user-one', aiMessageId: 'ai-one', response: withStatus(failure('generation_failed')) }] }, 'history-one')
  assert.equal(restored[1].result.sanitizedPayload.metadata, undefined)
  assert.equal(render(restored[1]).includes(marker), false, 'Do not promise a status absent from the supported history projection')

  console.log(`Generation status smoke passed: two safe failure reasons, receipt/narrative preservation, ${excluded.length} excluded cases, guarded rendering, confirmation unchanged and unsupported history explicit.`)
} finally { globalThis.fetch = originalFetch }
