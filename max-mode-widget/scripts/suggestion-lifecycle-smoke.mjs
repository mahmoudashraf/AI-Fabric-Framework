import assert from 'node:assert/strict'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('..', import.meta.url))
const fixture = `
import React, { useState, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { useSuggestionsController } from './src/hooks/useSuggestionsController';
import { SuggestionsPanel } from './src/components/Chat/Composer/SuggestionsPanel';
import { MessageList } from './src/components/Chat/MessageList';
const noop = () => {};
const initial = [{ id:'answer-1', type:'ai', content:'The approved answer.', timestamp:'2026-11-09T12:00:00Z',
  resultType:'INFORMATION_PROVIDED', success:true, result:{type:'INFORMATION_PROVIDED',success:true,
  sanitizedPayload:{data:{}},nextSteps:[{query:'Explain the approved delivery options',intent:'INFORMATION',rationale:'Review next steps.',confidence:0.8}]}}];
function Fixture() {
 const [messages,setMessages] = useState(initial), [busy,setBusy] = useState(false), [human,setHuman] = useState(false);
 const [starters,setStarters] = useState(['Explore the catalogue']);
 const [hasMessages,setHasMessages] = useState(false), [context,setContext] = useState(0);
 const controller = useSuggestionsController({starterSuggestions:starters,hasConversationMessages:hasMessages});
 const ref = useRef(null);
 window.changeContext=()=>setContext(n=>n+1);
 window.changeStarters=()=>setStarters(['Read the help guide']);
 window.beginTurn=()=>{setHasMessages(true);setBusy(true);setMessages(prev=>[...prev,{id:'user-2',type:'user',content:'A follow-up',timestamp:'2026-11-09T12:01:00Z'}]);};
 window.finishTurn=()=>{setBusy(false);setMessages(prev=>[...prev,{...initial[0],id:'answer-2',result:{...initial[0].result,nextSteps:[{query:'Compare the approved options',intent:'INFORMATION',rationale:'',confidence:0.5}]}}]);};
 window.humanControl=value=>setHuman(value);
 window.reset=()=>{setMessages(initial);setHasMessages(false);setBusy(false);setHuman(false);};
 window.selectedQueries=[];
 return <><output>{JSON.stringify({context,loading:controller.isLoadingSuggestions,suggestions:controller.suggestions})}</output>
 <SuggestionsPanel {...controller} onDismissSuggestions={()=>controller.setShowSuggestions(false)} onShowSuggestions={()=>controller.setShowSuggestions(true)} onSuggestionSelect={query=>window.selectedQueries.push(query)}/>
 <MessageList containerClassName="messages" messages={messages} latestMessageRef={ref} messagesEndRef={ref} isLoading={busy}
 nextStepsEnabled={!human} getAiStyles={()=>({bg:'',border:'',text:'',iconColor:'',label:'Information',hideBadge:true})}
 isPanelVisible={false} attachedItems={[]} confirmationStatus={{}} expandedActions={{}} debugEnabled={false}
 onOpenDebug={noop} onResendAction={noop} onReattachItem={noop} onOpenSourcesMobile={noop} onOpenSourcesDesktop={noop}
 onConfirm={noop} onExpandActionResults={noop} isItemAttached={()=>false} onAttachActionResultItem={noop}
 onNextStepClick={query=>window.selectedQueries.push(query)} onPresentationAsk={noop} onAttachPresentationResult={noop}
 onDetachPresentationResult={noop} isPresentationResultAttached={()=>false} onCustomerAccountConnect={noop}/></>;
}
createRoot(document.getElementById('root')).render(<Fixture/>);
`
const output = await build({ configFile:false,root,publicDir:false,logLevel:'error',
 resolve:{alias:{'@':fileURLToPath(new URL('../src',import.meta.url))}},
 define:{'process.env.NODE_ENV':JSON.stringify('production')},
 plugins:[{name:'suggestion-lifecycle-fixture',enforce:'pre',resolveId(id){if(id==='fixture'||id.endsWith('/fixture'))return root+'/fixture.jsx'},load(id){if(id===root+'/fixture.jsx')return fixture}}],
 build:{write:false,minify:false,lib:{entry:'fixture',name:'SuggestionFixture',formats:['iife']}} })
const bundle = (Array.isArray(output)?output[0]:output).output.find(item=>item.type==='chunk').code
const server = http.createServer((req,res)=>{res.setHeader('Content-Type',req.url==='/fixture.js'?'text/javascript':'text/html');res.end(req.url==='/fixture.js'?bundle:'<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script src="/fixture.js"></script></body></html>')})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const browser=await chromium.launch({headless:true})
try {
 const page=await browser.newPage(),errors=[],requests=[]
 page.setDefaultTimeout(10000)
 page.on('pageerror',error=>errors.push(error.message))
 page.on('request',request=>{if(request.url().includes('/suggestions'))requests.push(request.url())})
 await page.clock.install()
 await page.goto('http://127.0.0.1:'+server.address().port)
 await page.getByText('Try asking',{exact:true}).waitFor()
 await page.getByText('Suggested next steps',{exact:true}).waitFor()
 await page.getByRole('button',{name:'Explain the approved delivery options'}).click()
 assert.deepEqual(await page.evaluate(()=>window.selectedQueries),['Explain the approved delivery options'])
 await page.evaluate(()=>window.changeContext())
 await page.clock.fastForward(12000)
 assert.deepEqual(requests,[],'Attaching/navigating must not start background suggestion requests')
 assert.equal(await page.getByText('AI Suggestions',{exact:true}).count(),0)
 await page.evaluate(()=>window.beginTurn())
 await page.waitForFunction(()=>JSON.parse(document.querySelector('output').textContent).suggestions.length===0)
 assert.equal(await page.getByText('Suggested next steps',{exact:true}).count(),0)
 await page.evaluate(()=>window.finishTurn())
 await page.getByRole('button',{name:'Compare the approved options'}).waitFor()
 assert.equal(await page.getByRole('button',{name:'Explain the approved delivery options'}).count(),0)
 await page.evaluate(()=>window.humanControl(true))
 await page.waitForFunction(()=>!document.body.textContent.includes('Suggested next steps'))
 await page.evaluate(()=>window.humanControl(false))
 await page.getByRole('button',{name:'Compare the approved options'}).waitFor()
 await page.evaluate(()=>{window.reset();window.changeStarters()})
 await page.getByRole('button',{name:'Read the help guide',exact:true}).waitFor()
 await page.clock.fastForward(12000)
 assert.deepEqual(requests,[])
 assert.deepEqual(errors,[])
 console.log('Suggestion lifecycle passed: honest starters, no hidden/late attachment requests, latest-turn controls, human-control suppression and normal query submission.')
} finally {await browser.close();await new Promise(resolve=>server.close(resolve))}
