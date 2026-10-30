import assert from 'node:assert/strict'
import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'
import { chromium } from 'playwright'

const hook = fileURLToPath(new URL('../src/hooks/useConversationsController.ts', import.meta.url))
const fixture = `
import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { useConversationsController } from ${JSON.stringify(hook)};
const root = createRoot(document.getElementById('root'));
let mount = 0;
function Fixture() {
 const [id, setId] = useState(null), [messages, setMessages] = useState([]);
 const noop = React.useCallback(() => {}, []);
 useConversationsController({enabled:true,isPersistenceHydrated:true,isOpen:true,
  chatMessagesLength:messages.length,currentConversationId:id,setCurrentConversationId:setId,
  setChatMessages:setMessages,setIsLoading:noop,setAttachedItems:noop,setSuggestions:noop,
  setContextDocuments:noop,toast:noop});
 window.startTurn = () => { setId('current-turn'); setMessages([{id:'new',type:'user',content:'My new question'}]); };
 window.selectConversation = () => setId('explicit-selection');
 return React.createElement('output', null, JSON.stringify({id,messages}));
}
window.mountFixture = () => root.render(React.createElement(Fixture, {key:++mount}));
window.mountFixture();
`
const modules = {
 '\0fixture.jsx': fixture,
 '\0conversation-api': `
 export const listConversations = async () => [{id:'previous',createdAt:'2026-10-22T12:00:00Z',status:'ACTIVE'}];
 export const getConversation = () => new Promise(resolve => { window.finishHistory = () => resolve({turns:[{userQuery:'Previous question',aiResponse:'Previous answer'}]}); });
 export const deleteConversation = async () => {};
 `,
 '\0client-api': 'export const isPublicRuntimeSessionInvalidatedError = () => false;'
}
const output = await build({ configFile:false, root:fileURLToPath(new URL('..',import.meta.url)),publicDir:false,logLevel:'error',
 resolve:{alias:{'@/config':fileURLToPath(new URL('../src/config.ts',import.meta.url))}},
 define:{'process.env.NODE_ENV':JSON.stringify('production')},
 plugins:[{name:'conversation-race-fixture',enforce:'pre',resolveId(id){
  if(id==='fixture'||id.endsWith('/fixture'))return '\0fixture.jsx';
  if(id==='@/api/conversations')return '\0conversation-api';
  if(id==='@/api/client')return '\0client-api';
 },load(id){return modules[id]}}],
 build:{write:false,minify:false,lib:{entry:'fixture',name:'ConversationRaceFixture',formats:['iife']}} })
const bundle=(Array.isArray(output)?output[0]:output).output.find(item=>item.type==='chunk').code
const server=http.createServer((req,res)=>{res.setHeader('Content-Type',req.url==='/fixture.js'?'text/javascript':'text/html');
 res.end(req.url==='/fixture.js'?bundle:'<!doctype html><div id="root"></div><script src="/fixture.js"></script>')})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const browser=await chromium.launch({headless:true})
try {
 const page=await browser.newPage(),errors=[]
 page.on('pageerror',error=>errors.push(error.message))
 await page.goto('http://127.0.0.1:'+server.address().port)
 await page.waitForFunction(()=>typeof window.finishHistory==='function')
 await page.evaluate(()=>window.startTurn())
 await page.waitForFunction(()=>JSON.parse(document.querySelector('output').textContent).id==='current-turn')
 await page.evaluate(()=>window.finishHistory())
 await page.waitForTimeout(100)
 assert.deepEqual(await page.locator('output').evaluate(node=>JSON.parse(node.textContent)),
  {id:'current-turn',messages:[{id:'new',type:'user',content:'My new question'}]})
 await page.reload()
 await page.waitForFunction(()=>typeof window.finishHistory==='function')
 await page.evaluate(()=>window.selectConversation())
 await page.waitForFunction(()=>JSON.parse(document.querySelector('output').textContent).id==='explicit-selection')
 await page.evaluate(()=>window.finishHistory())
 await page.waitForTimeout(100)
 assert.equal(await page.locator('output').evaluate(node=>JSON.parse(node.textContent).id),'explicit-selection')
 await page.reload()
 await page.waitForFunction(()=>typeof window.finishHistory==='function')
 await page.evaluate(()=>window.finishHistory())
 await page.waitForFunction(()=>JSON.parse(document.querySelector('output').textContent).id==='previous')
 assert.equal(await page.locator('output').evaluate(node=>JSON.parse(node.textContent).messages.length),2)
 assert.deepEqual(errors,[])
 console.log('Conversation restoration race smoke passed: new turn, explicit selection and idle restoration.')
} finally {await browser.close();await new Promise(resolve=>server.close(resolve))}
