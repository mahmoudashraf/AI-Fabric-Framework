import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import react from '@vitejs/plugin-react'
import { chromium } from 'playwright'

// All commands below operate on this in-memory fixture, never a hosted deployment.
const root = fileURLToPath(new URL('../', import.meta.url))
const cache = await mkdtemp(`${tmpdir()}/brain-knowledge-`)
const fixture = `
const copy = value => structuredClone(value);
export const state = window.fixture = {calls:[], mode:new URLSearchParams(location.search).get('mode'),
  source:{sourceId:'inventory',entityType:'item',vectorSpace:'catalogue',scheduleSeconds:120,connectionProfileRef:'provider',protectedResourceBindingRef:'account',freshnessState:'CURRENT',preflightState:'READY',
    state:{status:'ACTIVE',counts:{sourceCount:6,indexedCount:6,failedWorkCount:0},lastSuccessAt:'2026-11-08T12:00:00Z'}},
  document:{sourceId:'policy',displayName:'Delivery policy',status:'ACTIVE',updatedAt:'2026-11-08T12:00:00Z',activeVersion:1}};
export async function fetchDeploymentTargetContext() {return {externalIntegrationConfigured:true,documentDatasetIds:['documents'],conversationHandoverConfigured:true}}
export async function fetchDocumentSources() {return [copy(state.document)]}
export async function fetchDeploymentReleases() {return [{id:'r1',deploymentId:'brain',targetProfileId:'staging',provisioningStatus:'ACTIVE',status:'APPLIED_VERIFIED'}]}
export async function fetchDeploymentIntegrations() {return {success:true,sources:[copy(state.source)],webhooks:[{sourceId:'stock-events',reconcileDataSourceRef:'inventory',registrationState:'READY',method:'POST',signatureHeader:'X-Signature',publicUrl:'https://fixture.invalid/events',counts:{received:2,rejected:0,duplicate:0,deadLetter:0},manualReplayEnabled:true,recentEvents:[]}],persistence:{enabled:true,schema:'connector'},runtimeDataSync:{enabled:true,serviceCredentialConfigured:true},tokenPosture:{},connectionProfiles:[],protectedResources:[]}}
export async function reconcileDeploymentIntegrationSource(target,sourceId,key) {state.calls.push({type:'sync',target,sourceId,key});return {success:true}}
export async function replayDeploymentIntegrationWebhook() {state.calls.push({type:'replay'});return {success:true}}
export async function fetchConversationHandoverOperations() {state.calls.push({type:'handover-read'});throw Error('Data view must not fetch handover')}
export async function expireConversationHandover() {state.calls.push({type:'expire'})}
export async function retryConversationHandoverDispatch() {state.calls.push({type:'retry'})}
const task={taskId:'review-1',title:'Approve refund',summary:'A customer asks for a refund.',status:'PENDING',type:'ACTION_REVIEW',version:1,policyId:{name:'refund',version:1},createdAt:'2026-11-08T12:00:00Z',expiresAt:'2099-11-09T12:00:00Z',allowedDecisions:['APPROVE','REJECT']};
export async function fetchDeploymentHumanReviewInbox() {return [copy(task)]}
export async function fetchDeploymentHumanReviewDetail(brain,target,id) {if(id!==task.taskId)throw Error('Review task unavailable');return {task:copy(task),message:'Review before deciding'}}
export async function decideDeploymentHumanReview(brain,target,id,input) {state.calls.push({type:'decide',brain,target,id,input});task.status='APPROVED';task.version++;return {task:copy(task),outcome:null}}
export class PlatformApiError extends Error {}
export function usePlatformAuth() {return {session:{authenticated:true,role:'CUSTOMER_ADMIN',authenticationMode:'SUPABASE',actorId:'owner',customerId:'customer'}}}
export function useDeploymentWorkspace() {return {selectedDeploymentId:'brain',workspace:{access:{canOperate:state.mode!=='read-only'}}}}
`
const harness = `
import React from 'react';import {createRoot} from 'react-dom/client';import {createBrowserRouter,RouterProvider} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {ThemeProvider,CssBaseline,Box} from '@mui/material';
import {createConsoleTheme} from '/src/theme/consoleTheme.ts';import {BrainWorkspaceContext} from '/src/brain/BrainWorkspaceContext.tsx';
import {BrainKnowledgePage} from '/src/brain/BrainKnowledgePage.tsx';
import {BrainActionReviewsPage} from '/src/brain/BrainActionReviewsPage.tsx';
const target={deploymentId:'brain',targetProfileId:'staging',releaseId:'r1'};
const router=createBrowserRouter([{path:'/smart-brains/brain/action-reviews',element:<BrainActionReviewsPage/>},{path:'*',element:<BrainKnowledgePage/>}]);
const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
createRoot(document.getElementById('root')).render(<ThemeProvider theme={createConsoleTheme('dark')}><CssBaseline/><QueryClientProvider client={client}>
<BrainWorkspaceContext.Provider value={{brainId:'brain',targetProfileId:'staging',targetProfile:{id:'staging',active:true},target,release:{id:'r1',deploymentId:'brain',targetProfileId:'staging',provisioningStatus:'ACTIVE',status:'APPLIED_VERIFIED',verificationStatus:'PASSED'},href:path=>'/smart-brains/brain/'+path+'?targetProfileId=staging'}}>
<Box sx={{height:'100dvh',p:2,display:'flex',flexDirection:'column'}}><RouterProvider router={router}/></Box></BrainWorkspaceContext.Provider></QueryClientProvider></ThemeProvider>);
`
const server = await createServer({root,configFile:false,cacheDir:cache,resolve:{dedupe:['react','react-dom']},optimizeDeps:{entries:[],include:['react','react-dom/client','react/jsx-runtime','react-router-dom','@tanstack/react-query','@mui/material','@mui/material/styles',...['DescriptionOutlined','SyncOutlined','AddRounded','CloudUploadOutlined','ArrowBackRounded','FactCheckOutlined','CableRounded','ContentCopyRounded','ReplayRounded','StorefrontRounded','SyncRounded','ExpandMoreRounded','CheckCircleRounded','FactCheckRounded','RefreshRounded','TaskAltRounded','CancelRounded'].map(name=>'@mui/icons-material/'+name)]},plugins:[react(),{
  name:'knowledge-memory-only',enforce:'pre',resolveId(id, importer) {
    if(id.startsWith('virtual:knowledge-') || id.startsWith('/virtual:knowledge-')) return '\0'+id.replace(/^\//,'');
    if(!importer?.endsWith('/BrainKnowledgePage.tsx') && !importer?.endsWith('/IntegrationsPage.tsx') && !importer?.endsWith('/HumanReviewPage.tsx')) return;
    if(['../api/platformApi','../auth/PlatformAuthProvider','../workspace/DeploymentWorkspaceContext'].includes(id)) return '\0virtual:knowledge-fixture';
    if(id==='../pages/DocumentKnowledgePage') return '\0virtual:knowledge-document';
    if(id==='../pages/ProvidersPage') return '\0virtual:knowledge-provider';
  },async load(id) {
    if(id==='\0virtual:knowledge-fixture') return fixture;
    if(id==='\0virtual:knowledge-provider') return 'export const ProvidersPage=()=>null;';
    if(id==='\0virtual:knowledge-harness') return (await transformWithEsbuild(harness,'harness.jsx',{loader:'jsx',jsx:'automatic'})).code;
    if(id==='\0virtual:knowledge-document') return (await transformWithEsbuild('import React from "react";export function DocumentKnowledgePage(props){return <section aria-label="Document controller">{props.workspaceView} / {props.initialSourceId} / {props.initialReleaseId}</section>}','document.jsx',{loader:'jsx',jsx:'automatic'})).code;
  },
  configureServer(vite) {vite.middlewares.use((req,res,next)=>{if(!req.url?.startsWith('/smart-brains/'))return next();res.setHeader('Content-Type','text/html');res.end('<html><body><div id="root"></div><script type="module" src="/virtual:knowledge-harness"></script></body></html>')})},
}],server:{host:'127.0.0.1',port:0,hmr:false}})
let browser
try {
  await server.listen();browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:900}})
  page.setDefaultTimeout(15000);const errors=[];page.on('pageerror',error=>errors.push(error.stack ?? error.message));const base=server.resolvedUrls.local[0];
  const open=async path=>{await page.goto(base+path);try{await page.getByRole('heading',{name:'What your AI knows'}).waitFor()}catch(error){console.error('Fixture errors:',errors);throw error}};
  await open('smart-brains/brain/knowledge');await page.getByRole('link',{name:/Delivery policy Document/}).waitFor();assert.equal(await page.getByRole('list',{name:'Knowledge sources'}).getByRole('link').count(),2);
  await page.getByRole('tab',{name:'Live data',exact:true}).click();assert.equal(await page.getByRole('list',{name:'Knowledge sources'}).getByRole('link').count(),1);
  await page.getByRole('link',{name:/inventory Live data/}).click();await page.getByRole('heading',{name:'inventory',exact:true}).waitFor();
  assert.deepEqual(await page.evaluate(()=>window.fixture.calls),[]);await page.getByRole('button',{name:'Sync now',exact:true}).click();
  await page.getByText('Sync requested.',{exact:false}).waitFor();const calls=await page.evaluate(()=>window.fixture.calls);assert.equal(calls.length,1);assert.equal(calls[0].sourceId,'inventory');assert.deepEqual(calls[0].target,{deploymentId:'brain',targetProfileId:'staging',releaseId:'r1'});assert.ok(calls[0].key);
  await page.getByRole('button',{name:'Sync activity & inbound events'}).click();await page.getByText('https://fixture.invalid/events',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Connection diagnostics & authority'}).click();await page.getByText('Connection profile: provider',{exact:true}).waitFor();
  await open('smart-brains/brain/knowledge/connections/foreign');await page.getByRole('alert').filter({hasText:'requested source is not available'}).waitFor();assert.equal(await page.getByRole('button',{name:'Sync now'}).count(),0);
  await open('smart-brains/brain/knowledge/connections/inventory?mode=read-only');await page.getByRole('button',{name:'Sync now'}).waitFor();assert.equal(await page.getByRole('button',{name:'Sync now'}).isEnabled(),false);
  await open('smart-brains/brain/knowledge/sources/policy');await page.getByRole('region',{name:'Document controller'}).waitFor();assert.match(await page.getByRole('region',{name:'Document controller'}).innerText(),/source \/ policy \/ r1/);
  await page.setViewportSize({width:390,height:844});await page.getByRole('link',{name:'Back to sources'}).waitFor();assert.equal(await page.getByRole('list',{name:'Knowledge sources'}).count(),0);await page.getByRole('link',{name:'Back to sources'}).click();await page.getByRole('list',{name:'Knowledge sources'}).waitFor();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.setViewportSize({width:1440,height:900});await page.goto(base+'smart-brains/brain/action-reviews');await page.getByRole('button',{name:/Approve refund/}).waitFor();
  assert.deepEqual(await page.evaluate(()=>window.fixture.calls),[]);await page.getByRole('button',{name:/Approve refund/}).click();
  await page.getByRole('button',{name:'Approve',exact:true}).click();await page.getByRole('dialog').waitFor();assert.deepEqual(await page.evaluate(()=>window.fixture.calls),[]);
  await page.getByRole('dialog').getByRole('button',{name:'Cancel',exact:true}).click();assert.deepEqual(await page.evaluate(()=>window.fixture.calls),[]);
  await page.getByRole('button',{name:'Approve',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Approve',exact:true}).click();
  await page.getByText('Decision recorded. Execution is reported separately.',{exact:true}).waitFor();const decisions=await page.evaluate(()=>window.fixture.calls);assert.equal(decisions.length,1);assert.equal(decisions[0].id,'review-1');assert.equal(decisions[0].target,'staging');assert.equal(decisions[0].input.expectedVersion,1);
  await page.goto(base+'smart-brains/brain/action-reviews?taskId=foreign');await page.getByRole('alert').filter({hasText:'Review task unavailable'}).waitFor();assert.equal(await page.getByRole('button',{name:'Approve',exact:true}).count(),0);
  assert.deepEqual(errors,[]);console.log('PASS: 13 scenarios for source filtering/scoped sync/no handover reads/diagnostics/events/missing source/permissions/reused document controller/mobile navigation/review selection/confirmation/cancel/exact decision/missing review.');
} finally {await browser?.close();await server.close();await rm(cache,{recursive:true,force:true})}
