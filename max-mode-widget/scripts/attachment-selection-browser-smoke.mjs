import assert from 'node:assert/strict'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { build, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const source = fileURLToPath(new URL('../src', import.meta.url))
const fixture = `
import React, { useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { setWidgetConfig } from '@/config';
import { useAttachmentsController } from '@/hooks/useAttachmentsController';
import { useChatFlow } from '@/hooks/useChatFlow';
import { ActionResultRenderer } from '@/components/ActionResultRenderer';
import { AttachmentsRow } from '@/components/Chat/Composer/AttachmentsRow';
import '@/styles/index.css';
setWidgetConfig({features:{cart:false},storageNamespace:'attachment-test'});
window.requests = [];
function Fixture() {
 const [items,setItems] = useState([]), [messages,setMessages] = useState([]);
 const [actionName,setActionName] = useState('catalogue');
 const [results,setResults] = useState([{id:'desk',name:'Standing desk',priceGbp:499,stock:8,adjustableHeight:true,
   features:{height:{adjustable:true},discount:0,nextDelivery:null},apiKey:'CREDENTIAL_CANARY'},
   {id:'chair',name:'Chair',priceGbp:129,stock:0,adjustableHeight:false}]);
 const ref = useRef(null), noop = () => {};
 const controller = useAttachmentsController({attachedItems:items,setAttachedItems:setItems,
   setCollectingItem:noop,chatInputRef:ref,toast:noop});
 const flow = useChatFlow({chatQuery:'',setChatQuery:noop,setChatMessages:setMessages,attachedItems:items,
   searchCategory:null,currentConversationId:'conversation',setCurrentConversationId:noop,setIsLoading:noop,
   setSuggestions:noop,setCurrentPosition:noop,setCurrentMode:noop,setLastRequestData:noop,
   setLastResponseData:noop,setSelectedDebugMessage:noop,currentPosition:'landing',currentMode:'navigator'});
 window.state = {items,messages};
 window.idless = () => {setItems([]);setResults([{name:'First anonymous item'},{name:'Second anonymous item'}])};
 window.otherSource = () => {setActionName('other_catalogue');setResults([{id:'desk',name:'Different source desk',priceGbp:1}])};
 return <main style={{maxWidth:650,padding:16}}>
   <ActionResultRenderer data={results} messageId="message-1" actionName={actionName} expandedCount={6}
     onExpand={noop} onAttach={controller.handleAttachActionResultItem} isAttached={controller.isItemAttached}/>
   <AttachmentsRow items={items} showAttachments onRemoveAttachment={controller.removeNonAiAttachmentByIndex}
     onDismissAttachments={noop} onShowAttachments={noop}/>
   <button onClick={() => flow.handleChatQuery('Compare these items')}>Send comparison</button>
   <button onClick={() => items[0] && controller.handleReattachItem(items[0])}>Reattach first</button>
 </main>;
}
createRoot(document.getElementById('root')).render(<Fixture/>);
`
const output = await build({ configFile:false, root:fileURLToPath(new URL('..',import.meta.url)),
  publicDir:false, logLevel:'error', resolve:{alias:{'@':source}},
  define:{'process.env.NODE_ENV':JSON.stringify('production')},
  plugins:[{name:'attachment-fixture',enforce:'pre',resolveId(id){
    if(id==='attachment-fixture'||id.endsWith('/attachment-fixture')) return '\0attachment-fixture.jsx'
    if(id==='@/api/chat'||id===source+'/api/chat') return '\0attachment-api'
  },async load(id){
    if(id==='\0attachment-fixture.jsx') return (await transformWithEsbuild(fixture,'fixture.jsx',{loader:'jsx',jsx:'automatic'})).code
    if(id==='\0attachment-api') return `export const createConversationId=()=> 'conversation';
      export const resolvedChatMessageUrl=()=> '/fixture/query';
      export const usesCanonicalConversationMessages=()=> false;
      export async function postChatQuery(payload){window.requests.push(payload);
        return {data:{type:'INFORMATION_PROVIDED',success:true,answer:'Fixture answer.'},status:200,durationMs:1}}`
  }}],build:{write:false,minify:false,lib:{entry:'attachment-fixture',name:'AttachmentFixture',formats:['iife']}}})
const files = (Array.isArray(output)?output[0]:output).output
const bundle = files.find(file=>file.type==='chunk').code
const css = files.filter(file=>file.type==='asset'&&file.fileName.endsWith('.css')).map(file=>file.source).join('\n')
const server = http.createServer((request,response)=>{
  const js = request.url==='/fixture.js', styles = request.url==='/fixture.css'
  response.setHeader('Content-Type',js?'text/javascript':styles?'text/css':'text/html')
  response.end(js?bundle:styles?css:'<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/fixture.css"></head><body><div id="root"></div><script src="/fixture.js"></script></body></html>')
})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const browser = await chromium.launch({headless:true})
try {
  for(const width of [1280,390]) {
    const page = await browser.newPage({viewport:{width,height:1000}}), errors=[]
    page.on('pageerror',error=>errors.push(error.message))
    await page.goto('http://127.0.0.1:'+server.address().port)
    await page.getByTitle('Attach to Chat',{exact:true}).first().click()
    await page.getByTitle('Attach to Chat',{exact:true}).first().click()
    assert.equal(await page.getByTitle('Already in Chat',{exact:true}).count(),2)
    assert.equal(await page.locator('[data-mxw-attachment-chip]').count(),2)
    await page.getByRole('button',{name:'Reattach first',exact:true}).click()
    assert.equal(await page.locator('[data-mxw-attachment-chip]').count(),2)
    await page.getByRole('button',{name:'Send comparison',exact:true}).click()
    await page.waitForFunction(()=>window.requests.length===1)
    const request = await page.evaluate(()=>window.requests[0])
    assert.equal(request.attachments.length,2)
    const desk = JSON.parse(request.attachments[0].contentText),chair = JSON.parse(request.attachments[1].contentText)
    assert.equal(desk.priceGbp,499);assert.equal(desk.stock,8);assert.equal(desk.adjustableHeight,true)
    assert.deepEqual(desk.features,{height:{adjustable:true},discount:0,nextDelivery:null})
    assert.equal(chair.stock,0);assert.equal(chair.adjustableHeight,false)
    assert.equal(JSON.stringify(request).includes('CREDENTIAL_CANARY'),false)
    assert.equal(request.attachments[0].metadata.actionEligible,false)
    await page.getByRole('button',{name:'Remove attachment',exact:true}).first().click()
    assert.equal(await page.getByTitle('Already in Chat',{exact:true}).count(),1)
    await page.getByTitle('Attach to Chat',{exact:true}).click()
    assert.equal(await page.getByTitle('Already in Chat',{exact:true}).count(),2)
    await page.evaluate(()=>window.otherSource())
    await page.getByTitle('Attach to Chat',{exact:true}).click()
    assert.equal(await page.locator('[data-mxw-attachment-chip]').count(),3,'same ID in another source stays independent')
    await page.evaluate(()=>window.idless())
    await page.getByTitle('Attach to Chat',{exact:true}).first().click()
    await page.getByTitle('Attach to Chat',{exact:true}).first().click()
    assert.equal(await page.getByTitle('Already in Chat',{exact:true}).count(),2)
    assert.equal(await page.locator('[data-mxw-attachment-chip]').count(),2)
    assert.deepEqual(errors,[])
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false)
    await page.close()
  }
  console.log('Attachment browser smoke passed at desktop/phone widths: actual cards, hook, reattachment and chat request retain full items and distinct ID-less selections; no live services.')
} finally {
  await browser.close()
  await new Promise(resolve=>server.close(resolve))
}
