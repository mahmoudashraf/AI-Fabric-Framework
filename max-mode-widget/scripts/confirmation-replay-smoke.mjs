import assert from 'node:assert/strict'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { build, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const source = fileURLToPath(new URL('../src', import.meta.url))
const fixture = `
import React, { useCallback, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { MaxModeProvider } from '@/context';
import { setWidgetConfig } from '@/config';
import { useMaxModePersistence } from '@/hooks/useMaxModePersistence';
import { useConfirmationFlow } from '@/hooks/useConfirmationFlow';
import { MessageList } from '@/components/Chat/MessageList';
setWidgetConfig({ storageNamespace: 'confirmation-replay-test', features: { cart: false } });
window.requests = [];
function Fixture() {
 const [messages, setMessages] = useState([]), [id, setId] = useState('conversation-one');
 const [status, setStatus] = useState({}), [loading, setLoading] = useState(false);
 const [attached, setAttached] = useState([]), [documents, setDocuments] = useState([]);
 const [position, setPosition] = useState('landing'), [mode, setMode] = useState('executor');
 const [enabled, setEnabled] = useState(true), ref = useRef(null), noop = useCallback(() => {}, []);
 const hydrated = useMaxModePersistence({chatMessages:messages,setChatMessages:setMessages,
  attachedItems:attached,setAttachedItems:setAttached,currentPosition:position,setCurrentPosition:setPosition,
  currentMode:mode,setCurrentMode:setMode,currentConversationId:id,setCurrentConversationId:setId,
  contextDocuments:documents,setContextDocuments:setDocuments,hostInitialAttachments:[]});
 const { handleConfirmation } = useConfirmationFlow({chatMessages:messages,confirmationStatus:status,
  isLoading:loading,enabled,attachedItems:attached,currentConversationId:id,currentMode:mode,
  setConfirmationStatus:setStatus,setChatMessages:setMessages,setContextDocuments:setDocuments,
  setCurrentConversationId:setId,setIsLoading:setLoading,toast:noop});
 window.setFixture = (next, conversation = 'conversation-one') => { setMessages(next); setId(conversation); setStatus({}); };
 window.confirmMessage = handleConfirmation;
 window.setBusy = setLoading; window.setEnabled = setEnabled;
 window.fixtureState = { messages, id, status, loading, hydrated };
 return <MessageList containerClassName="messages" messages={messages} latestMessageRef={ref} messagesEndRef={ref}
  isLoading={loading} getAiStyles={() => ({bg:'',border:'',text:'',iconColor:'',label:''})}
  isPanelVisible={false} attachedItems={[]} confirmationStatus={status} expandedActions={{}} debugEnabled={false}
  onConfirm={handleConfirmation} onOpenDebug={noop} onResendAction={noop} onReattachItem={noop}
  onOpenSourcesMobile={noop} onOpenSourcesDesktop={noop} onExpandActionResults={noop} isItemAttached={() => false}
  onAttachActionResultItem={noop} onNextStepClick={noop} onPresentationAsk={noop} onAttachPresentationResult={noop}
  onDetachPresentationResult={noop} isPresentationResultAttached={() => false} onCustomerAccountConnect={noop} />;
}
createRoot(document.getElementById('root')).render(<MaxModeProvider><Fixture /></MaxModeProvider>);
`
const modules = {
  '\0confirmation-fixture.jsx': fixture,
  '\0confirmation-api': `export async function postChatQuery(payload, options) {
    window.requests.push({payload, options});
    if (window.holdConfirmation) await new Promise(resolve => window.finishConfirmation = resolve);
    return { data: window.confirmationResponse || {type:'ACTION_EXECUTED',success:true,
      conversationId:payload.conversationId,answer:'Synthetic receipt: completed once.'} };
  }`,
}
const output = await build({ configFile: false, root: fileURLToPath(new URL('..', import.meta.url)),
  publicDir: false, logLevel: 'error', resolve: { alias: { '@': source } },
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  plugins: [{ name: 'confirmation-replay-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === 'confirmation-fixture' || id.endsWith('/confirmation-fixture')) return '\0confirmation-fixture.jsx'
      if (id === '@/api/chat' || id === source + '/api/chat') return '\0confirmation-api'
    }, async load(id) {
      if (id === '\0confirmation-fixture.jsx') return (await transformWithEsbuild(fixture, 'fixture.jsx', { loader: 'jsx', jsx: 'automatic' })).code
      return modules[id]
    },
  }],
  build: { write: false, minify: false, lib: { entry: 'confirmation-fixture', name: 'ConfirmationFixture', formats: ['iife'] } },
})
const bundle = (Array.isArray(output) ? output[0] : output).output.find(item => item.type === 'chunk').code
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', req.url === '/fixture.js' ? 'text/javascript' : 'text/html')
  res.end(req.url === '/fixture.js' ? bundle : '<!doctype html><html><body><div id="root"></div><script src="/fixture.js"></script></body></html>')
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const browser = await chromium.launch({ headless: true })
const prompt = id => ({ id, type: 'ai', resultType: 'CONFIRMATION_REQUIRED', success: true,
  content: 'Synthetic action awaits confirmation.', timestamp: '2026-11-02T12:00:00Z' })
const receipt = { id: 'receipt', type: 'ai', resultType: 'ACTION_EXECUTED', success: true,
  content: 'Synthetic receipt: completed once.', timestamp: '2026-11-02T12:00:01Z' }
try {
  const page = await browser.newPage(), errors = [], unexpectedRequests = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    const url = new URL(route.request().url())
    if (url.hostname !== '127.0.0.1') { unexpectedRequests.push(url.origin); return route.abort() }
    return route.continue()
  })
  const origin = 'http://127.0.0.1:' + server.address().port
  await page.goto(origin)
  await page.waitForFunction(() => window.fixtureState?.hydrated)
  await page.evaluate(value => window.setFixture([value]), prompt('old-confirmation'))
  await page.getByRole('button', { name: 'Confirm', exact: true }).click()
  await page.getByText(receipt.content, { exact: true }).first().waitFor()
  assert.equal(await page.evaluate(() => window.requests.length), 1)
  await page.waitForFunction(() => JSON.parse(sessionStorage.getItem('maxmode_widget_state:confirmation-replay-test')).chatMessages.some(m => m.resultType === 'ACTION_EXECUTED'))
  await page.reload()
  await page.waitForFunction(() => window.fixtureState?.hydrated)
  await page.getByText(receipt.content, { exact: true }).first().waitFor()
  assert.equal(await page.getByRole('button', { name: 'Confirm', exact: true }).count(), 0, 'Reload reactivated a historical confirmation')
  assert.equal(await page.getByRole('button', { name: 'Reject', exact: true }).count(), 0)
  await page.evaluate(() => window.confirmMessage('old-confirmation', true))
  assert.equal(await page.evaluate(() => window.requests.length), 0, 'Direct historical callback must not issue a request')

  await page.evaluate(value => window.setFixture([value]), prompt('pending-before-reload'))
  await page.waitForFunction(() => JSON.parse(sessionStorage.getItem('maxmode_widget_state:confirmation-replay-test')).chatMessages[0]?.id === 'pending-before-reload')
  await page.reload()
  await page.waitForFunction(() => window.fixtureState?.hydrated)
  assert.equal(await page.getByRole('button', { name: 'Confirm', exact: true }).count(), 0, 'A restored proposal has no fresh server authority')
  await page.getByText('Historical confirmation. Ask again for a current confirmation.', { exact: true }).waitFor()

  await page.evaluate(values => window.setFixture(values), [prompt('superseded'), prompt('current')])
  assert.equal(await page.getByRole('button', { name: 'Confirm', exact: true }).count(), 1)
  await page.evaluate(() => window.confirmMessage('superseded', true))
  assert.equal(await page.evaluate(() => window.requests.length), 0)
  await page.evaluate(value => { window.staleConfirmation = window.confirmMessage; window.setFixture([value]) }, prompt('replacement'))
  await page.waitForFunction(() => window.fixtureState.messages.at(-1)?.id === 'replacement')
  await page.evaluate(() => window.staleConfirmation('current', true))
  assert.equal(await page.evaluate(() => window.requests.length), 0, 'A retained callback must check the current prompt, not its old render')
  await page.evaluate(value => window.setFixture([value]), prompt('current'))
  await page.waitForFunction(() => window.fixtureState.messages.at(-1)?.id === 'current')
  await page.evaluate(() => { window.setBusy(true) })
  await page.evaluate(() => window.confirmMessage('current', true))
  assert.equal(await page.evaluate(() => window.requests.length), 0)
  await page.evaluate(() => { window.setBusy(false); window.setEnabled(false) })
  await page.evaluate(() => window.confirmMessage('current', true))
  assert.equal(await page.evaluate(() => window.requests.length), 0)
  await page.evaluate(() => { window.setEnabled(true); window.holdConfirmation = true })
  await page.evaluate(() => { void window.confirmMessage('current', true); void window.confirmMessage('current', true) })
  await page.waitForFunction(() => typeof window.finishConfirmation === 'function')
  assert.equal(await page.evaluate(() => window.requests.length), 1, 'Duplicate callbacks must not duplicate confirmation requests')
  await page.evaluate(() => window.finishConfirmation())
  await page.getByText(receipt.content, { exact: true }).first().waitFor()
  const request = await page.evaluate(() => window.requests[0])
  assert.equal(request.payload.conversationId, 'conversation-one')
  assert.equal(request.payload.query, 'Yes, confirm')
  assert.match(request.options.idempotencyKey, /^confirmation-/)
  assert.equal('proposalId' in request.payload, false, 'Do not invent an unsupported proposal-ID protocol')
  await page.evaluate(value => {
    window.holdConfirmation = false;
    window.confirmationResponse = {type:'INFORMATION_PROVIDED',success:true,conversationId:'conversation-one',answer:'Synthetic rejection acknowledged.'};
    window.setFixture([value]);
  }, prompt('reject-current'))
  await page.locator('[data-message-id="reject-current"]').getByRole('button', { name: 'Reject', exact: true }).click()
  await page.getByText('Synthetic rejection acknowledged.', { exact: true }).first().waitFor()
  assert.equal(await page.evaluate(() => window.requests.length), 2)
  assert.equal(await page.evaluate(() => window.requests[1].payload.query), 'No, cancel')
  await page.reload()
  await page.waitForFunction(() => window.fixtureState?.hydrated)
  await page.getByText('Synthetic rejection acknowledged.', { exact: true }).first().waitFor()
  assert.equal(await page.getByRole('button', { name: 'Confirm', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Reject', exact: true }).count(), 0)
  assert.equal(await page.evaluate(() => window.requests.length), 0)
  await page.evaluate(value => {
    window.setFixture([value]); window.holdConfirmation = true;
  }, prompt('late-response'))
  await page.getByRole('button', { name: 'Confirm', exact: true }).waitFor()
  await page.evaluate(() => { window.pendingConfirmation = window.confirmMessage('late-response', true) })
  await page.waitForFunction(() => typeof window.finishConfirmation === 'function')
  await page.evaluate(() => window.setFixture([], 'conversation-two'))
  await page.waitForFunction(() => window.fixtureState.id === 'conversation-two')
  await page.evaluate(async () => { window.finishConfirmation(); await window.pendingConfirmation })
  assert.equal(await page.evaluate(() => window.fixtureState.id), 'conversation-two')
  assert.deepEqual(await page.evaluate(() => window.fixtureState.messages), [], 'A late response must not enter another conversation')
  assert.deepEqual(unexpectedRequests, [])
  assert.deepEqual(errors, [])
  console.log('Confirmation replay smoke passed: actual persistence reload, read-only history, current-only controls, guarded callbacks and unchanged request contract; no live writes.')
} finally {
  await browser.close()
  await new Promise(resolve => server.close(resolve))
}
