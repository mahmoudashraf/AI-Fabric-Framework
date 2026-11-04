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
import { useClarificationFlow } from '@/hooks/useClarificationFlow';
import { canonicalChatResult } from '@/chatResult';
import { MessageList } from '@/components/Chat/MessageList';
setWidgetConfig({ storageNamespace: 'clarification-flow-test', features: { cart: false } });
window.requests = [];
function toMessage(id, response) {
 return {id,type:'ai',content:response.answer,timestamp:'2026-11-04T17:20:00Z',
  resultType:response.type,success:response.success,result:canonicalChatResult(response)};
}
function Fixture() {
 const [messages, setMessages] = useState(() => JSON.parse(sessionStorage.getItem('clarification-history') || '[]'));
 const [id, setId] = useState('conversation-one');
 const [loading, setLoading] = useState(false), ref = useRef(null), noop = useCallback(() => {}, []);
 const { handleClarificationSubmit } = useClarificationFlow({attachedItems:[],currentConversationId:id,
  currentMode:'executor',setChatMessages:setMessages,setContextDocuments:noop,setCurrentConversationId:setId,
  setIsLoading:setLoading,toast:noop});
 window.setFixture = (items, conversation = 'conversation-one') => {
  setMessages(items.map(({id,response}) => toMessage(id,response))); setId(conversation);
 };
 window.appendResponse = (id,response) => setMessages(prev => [...prev,toMessage(id,response)]);
 window.fixtureState = {messages,id,loading};
 return <MessageList containerClassName="messages" messages={messages} latestMessageRef={ref} messagesEndRef={ref}
  isLoading={loading} getAiStyles={() => ({bg:'',border:'',text:'',iconColor:'',label:''})}
  isPanelVisible={false} attachedItems={[]} confirmationStatus={{}} expandedActions={{}} debugEnabled={false}
  onClarificationSubmit={handleClarificationSubmit} onConfirm={noop} onOpenDebug={noop} onResendAction={noop}
  onReattachItem={noop} onOpenSourcesMobile={noop} onOpenSourcesDesktop={noop} onExpandActionResults={noop}
  isItemAttached={() => false} onAttachActionResultItem={noop} onNextStepClick={noop} onPresentationAsk={noop}
  onAttachPresentationResult={noop} onDetachPresentationResult={noop} isPresentationResultAttached={() => false}
  onCustomerAccountConnect={noop} />;
}
createRoot(document.getElementById('root')).render(<MaxModeProvider><Fixture /></MaxModeProvider>);
`
const modules = {
  '\0clarification-api': `export async function postChatQuery(payload, options) {
   window.requests.push({payload:structuredClone(payload),options});
   const params = payload.actionDraftSubmission?.parameters;
   return {data: params && typeof params.consent === 'boolean' && typeof params.phone === 'string'
    ? {type:'CONFIRMATION_REQUIRED',success:false,conversationId:payload.conversationId,answer:'Synthetic final confirmation.',
      actions:[{action:payload.actionDraftSubmission.action,confirmationRequired:true,providedParameters:params}]}
    : window.repeatedClarification};
  }`,
}
const output = await build({ configFile: false, root: fileURLToPath(new URL('..', import.meta.url)),
  publicDir: false, logLevel: 'error', resolve: { alias: { '@': source } },
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  plugins: [{ name: 'clarification-flow-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === 'clarification-fixture' || id.endsWith('/clarification-fixture')) return '\0clarification-fixture.jsx'
      if (id === '@/api/chat' || id === source + '/api/chat') return '\0clarification-api'
    }, async load(id) {
      if (id === '\0clarification-fixture.jsx') return (await transformWithEsbuild(fixture, 'fixture.jsx', { loader: 'jsx', jsx: 'automatic' })).code
      return modules[id]
    },
  }],
  build: { write: false, minify: false, lib: { entry: 'clarification-fixture', name: 'ClarificationFixture', formats: ['iife'] } },
})
const bundle = (Array.isArray(output) ? output[0] : output).output.find(item => item.type === 'chunk').code
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', req.url === '/fixture.js' ? 'text/javascript' : 'text/html')
  res.end(req.url === '/fixture.js' ? bundle : '<!doctype html><html><body><div id="root"></div><script src="/fixture.js"></script></body></html>')
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const browser = await chromium.launch({ headless: true })
const action = 'dealership_request_callback'
const response = { type: 'CLARIFICATION_REQUIRED', success: false, answer: 'To proceed, please provide: name, phone.',
  actions: [{ action, missingRequiredParameters: ['name', 'phone'], providedParameters: {
    message: 'I would like the dealership to call me about 2025 Aster E1.', consent: true,
  } }] }
const cancellation = { type: 'INFORMATION_PROVIDED', success: true, answer: 'Synthetic acknowledgement.',
  actions: [{ action, actionDraftCancelled: true }],
  metadata: { actionDraftSemanticContinuation: { evaluated: true, cancelled: true } } }
try {
  const page = await browser.newPage(), errors = [], unexpectedRequests = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    const url = new URL(route.request().url())
    if (url.hostname !== '127.0.0.1') { unexpectedRequests.push(url.origin); return route.abort() }
    return route.continue()
  })
  await page.goto('http://127.0.0.1:' + server.address().port)
  await page.waitForFunction(() => typeof window.setFixture === 'function')
  await page.evaluate(response => {
    window.repeatedClarification = response;
    window.setFixture([{id:'callback',response}]);
  }, response)
  await page.getByLabel('Name', { exact: true }).fill('UI Journey Check')
  await page.getByLabel('Phone', { exact: true }).fill('07700900900')
  assert.equal(await page.getByRole('checkbox', { name: 'Consent', exact: true }).isChecked(), true)
  await page.getByRole('button', { name: 'Submit & Proceed', exact: true }).click()
  await page.getByText('Synthetic final confirmation.', { exact: true }).first().waitFor()
  let requests = await page.evaluate(() => window.requests)
  assert.equal(requests.length, 1)
  assert.deepEqual(requests[0].payload.actionDraftSubmission, {action,parameters:{name:'UI Journey Check',
    phone:'07700900900',...response.actions[0].providedParameters}})
  assert.equal(requests[0].payload.conversationId, 'conversation-one')
  assert.match(requests[0].options.idempotencyKey, /^clarification-/)
  assert.equal(await page.getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 0)

  await page.evaluate(response => window.setFixture([{id:'first',response},{id:'repeat',response}]), response)
  const repeated = page.locator('[data-message-id="repeat"]')
  await repeated.getByLabel('Name', { exact: true }).waitFor()
  assert.equal(await page.locator('[data-message-id="first"]').getByRole('textbox').count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 1)
  await repeated.getByLabel('Name', { exact: true }).fill('UI Journey Check')
  await page.evaluate(value => window.appendResponse('cancel',value), cancellation)
  await repeated.getByText('Request cancelled.', { exact: true }).waitFor()
  assert.equal(await repeated.getByRole('textbox').count(), 0)
  assert.equal(await repeated.getByRole('checkbox').count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 0)
  assert.equal(await page.evaluate(() => window.requests.length), 1, 'Cancellation presentation must not submit anything')

  await page.evaluate(value => window.appendResponse('fresh',value), response)
  const fresh = page.locator('[data-message-id="fresh"]')
  await fresh.getByLabel('Name', { exact: true }).fill('UI Journey Check')
  await fresh.getByLabel('Phone', { exact: true }).fill('07700900900')
  await fresh.getByRole('checkbox', { name: 'Consent', exact: true }).uncheck()
  await fresh.getByRole('button', { name: 'Submit & Proceed', exact: true }).click()
  await page.waitForFunction(() => window.requests.length === 2 && !window.fixtureState.loading)
  requests = await page.evaluate(() => window.requests)
  assert.equal(requests[1].payload.actionDraftSubmission.parameters.consent, false)
  assert.equal(requests[1].payload.actionDraftSubmission.parameters.phone, '07700900900')

  for (const [id, ignored] of [
    ['prose',{...cancellation,actions:[],answer:'The incomplete request was cancelled without submitting it.'}],
    ['failed',{...cancellation,success:false}],
    ['other',{...cancellation,actions:[{action:'other_action',actionDraftCancelled:true}]}],
    ['string',{...cancellation,actions:[{action,actionDraftCancelled:'true'}]}],
  ]) {
    await page.evaluate(({id,response,ignored}) => window.setFixture([{id:'active-'+id,response},{id,response:ignored}]),
      {id,response,ignored})
    const active = page.locator(`[data-message-id="active-${id}"]`)
    await active.getByRole('checkbox').waitFor()
    assert.equal(await active.getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 1)
  }
  await page.evaluate(response => window.setFixture([{id:'other-conversation',response}], 'conversation-two'), response)
  await page.locator('[data-message-id="other-conversation"]').getByRole('checkbox').waitFor()
  assert.equal(await page.locator('[data-message-id="other-conversation"]')
    .getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 1)
  assert.equal(await page.evaluate(() => window.requests.length), 2)

  for (const terminal of [
    {type:'CONFIRMATION_REQUIRED',success:false,answer:'Synthetic confirmation awaits approval.',
      actions:[{action,confirmationRequired:true}]},
    {type:'ACTION_EXECUTED',success:true,answer:'Synthetic completed receipt.',
      actions:[{action,actionExecutionId:'synthetic-execution',actionResult:{success:true}}]},
  ]) {
    await page.evaluate(({response,terminal}) => window.setFixture([
      {id:'restored-clarification',response},{id:'structured-outcome',response:terminal},
    ]), {response,terminal})
    await page.waitForFunction(type => window.fixtureState.messages.at(-1)?.resultType === type, terminal.type)
    await page.evaluate(() => sessionStorage.setItem('clarification-history',JSON.stringify(window.fixtureState.messages)))
    await page.reload()
    const restored = page.locator('[data-message-id="restored-clarification"]')
    await restored.getByText('Clarification no longer active.', { exact: true }).waitFor()
    assert.equal(await restored.getByRole('textbox').count(), 0, 'Reload must not reopen completed intake')
    assert.equal(await page.getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 0)
    assert.equal(await page.evaluate(() => window.requests.length), 0, 'History restoration must never resubmit')
    await page.evaluate(response => window.appendResponse('fresh-after-reload',response), response)
    await page.locator('[data-message-id="fresh-after-reload"]').getByRole('checkbox').waitFor()
    assert.equal(await page.getByRole('button', { name: 'Submit & Proceed', exact: true }).count(), 1)
  }
  assert.deepEqual(unexpectedRequests, [])
  assert.deepEqual(errors, [])
  console.log('Clarification flow smoke passed: typed callback submission, boolean controls, leading-zero phone, scoped cancellation, confirmation/execution reload retirement and fresh drafts; no live calls.')
} finally {
  await browser.close()
  await new Promise(resolve => server.close(resolve))
}
