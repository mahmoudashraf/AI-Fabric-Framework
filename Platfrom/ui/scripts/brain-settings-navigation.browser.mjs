import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { createServer, transformWithEsbuild } from 'vite'
import react from '@vitejs/plugin-react'
import { chromium } from 'playwright'

// Real shell, route adapters and provider editors; all API operations stay in memory.
const root = fileURLToPath(new URL('../', import.meta.url))
const cache = await mkdtemp(path.join(tmpdir(), 'brain-settings-navigation-'))
const output = process.env.BRAIN_SETTINGS_EVIDENCE_DIR ?? fileURLToPath(new URL('../../../verification-support/brain-settings-navigation/local/', import.meta.url))
const fixture = `
const copy = value => structuredClone(value);
const readonly = new URLSearchParams(location.search).has('readonly');
const providerConfig = {llmProvider:'openai',embeddingProvider:'openai',vectorStrategy:'qdrant',vectorProvisioningMode:'EXTERNAL_EXISTING',openaiModel:'saved-model',qdrantHost:'https://vector.example.test',customExtension:{keep:true}};
export const state = window.fixture = {calls:[],draft:{id:'draft',revisionNumber:4,providerConfig,entityConfig:{},promptConfig:{},actionsConfig:{actions:[{name:'read',displayName:'Read account',anonymousAllowed:false}]}},brain:{id:'brain',name:'Customer knowledge',binding:{customerId:'customer',tenantId:'tenant'}}};
export function useDeploymentWorkspace() {return {selectedDeploymentId:'brain',selectedDeploymentSummary:state.brain,workspace:{access:{canEdit:!readonly},lifecycle:{savedDraftMatchesLatestPublished:true},draft:state.draft}}}
export async function fetchDeploymentWorkspace() {return {deployment:copy(state.brain),access:{canEdit:!readonly},template:{}}}
export async function fetchDeploymentDraft() {return copy(state.draft)}
export async function fetchDeploymentTargetOptions() {return [{id:'exact-target',environmentName:'staging',name:'Customer staging',active:true}]}
export const fetchDeploymentTargetProfiles = fetchDeploymentTargetOptions;
export async function fetchDeploymentVersions() {return []}
export async function fetchDeploymentReleases() {return [{id:'exact-release',deploymentId:'brain',targetProfileId:'exact-target',status:'APPLIED_VERIFIED',provisioningStatus:'ACTIVE',verificationStatus:'PASSED',createdAt:'2026-11-08T00:00:00Z'}]}
export async function fetchDeploymentSecretUsage() {return {secrets:[]}}
export async function updateDeploymentDraft(id,payload) {state.calls.push({type:'save',id,payload:copy(payload)});state.draft={...state.draft,...copy(payload)};return copy(state.draft)}
export async function validateDeploymentDraft() {return {issues:[],publishReady:true,errorCount:0,warningCount:0}}
export async function fetchDeploymentProviderConnectivity() {return {managedVectorTargets:[],probes:[],effectiveSecretResolutions:[]}}
export async function probeDeploymentProviderConnectivity(id,payload) {state.calls.push({type:'probe',id,payload:copy(payload)});return {managedVectorTargets:[],probes:[],effectiveSecretResolutions:[]}}
export async function fetchDeploymentTargetContext() {state.calls.push({type:'knowledge-read'});return {documentDatasetIds:[],externalIntegrationConfigured:false}}
export async function fetchDocumentSources() {return []}
export async function fetchDeploymentIntegrations() {return {sources:[]}}
export async function fetchDeploymentMarketplaceInstalls() {return []}
export async function fetchMarketplacePlugins() {return []}
`
const harness = `
import React from 'react';import {createRoot} from 'react-dom/client';
import {createBrowserRouter,RouterProvider} from 'react-router-dom';import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {SmartBrainShell} from '/src/brain/SmartBrainShell.tsx';import {SmartBrainPage} from '/src/brain/SmartBrainPage.tsx';
const router=createBrowserRouter([{path:'*',element:<SmartBrainShell session={{role:'CUSTOMER_ADMIN',displayName:'Customer',authenticated:true}} onSignOut={async()=>{}}><SmartBrainPage/></SmartBrainShell>}]);
createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity},mutations:{retry:false}}})}><RouterProvider router={router}/></QueryClientProvider>);
`
const stubs = {
  '../components/customer/CustomerSupportContext': 'export const CustomerSupportProvider=({children})=>children;',
  '../components/customer/CustomerSupportAssistant': 'export const CustomerSupportAssistant=()=>null;',
  '../components/customer/SupportResourceDialog': 'export const SupportResourceDialog=()=>null;',
  '../components/DeploymentProviderOperationsPanel': 'export const DeploymentProviderOperationsPanel=()=>{throw Error("Staff operations mounted")};',
  '../components/customer/CustomerExistingVectorBinding': 'export const CustomerExistingVectorBinding=()=> <section aria-label="Existing vector binding">Existing vector binding<input aria-label="Binding draft"/></section>;',
  '../workspace/useDeploymentWorkspaceEditorState': 'export function useDeploymentWorkspaceEditorState(){throw Error("Legacy footer mounted")}',
  '../pages/DocumentKnowledgePage': 'export const DocumentKnowledgePage=()=> <section aria-label="Document controller">Document operations</section>;',
  '../pages/IntegrationsPage': 'export const IntegrationsPage=()=>null;',
  '../pages/MarketplacePage': 'export const MarketplacePage=()=>null;',
  '../pages/ActionsPage': 'export const ActionsPage=({initialTechnicalView})=> <p>Action panel: {initialTechnicalView}</p>;',
  './BrainOverviewPage': 'export const BrainOverviewPage=()=> <h1>Overview</h1>;',
  './BrainResponsesPage': 'export const BrainResponsesPage=()=> <h1>Responses</h1>;',
  './BrainUpdatesPage': 'export const BrainUpdatesPage=()=> <h1>Updates</h1>;',
  './BrainExperiencePages': 'export const BrainChannelsPage=()=>null;export const BrainHumanSupportPage=()=>null;',
  './BrainTryPage': 'export const BrainTryPage=()=>null;',
  './BrainActionReviewsPage': 'export const BrainActionReviewsPage=()=>null;',
}
const server = await createServer({ root, configFile: false, cacheDir: cache,
  plugins: [react(), {
    name: 'settings-navigation-memory-only', enforce: 'pre',
    resolveId(id) {
      if (id.startsWith('virtual:settings-')) return '\0' + id
      if (id === '../api/platformApi' || id === '../workspace/DeploymentWorkspaceContext') return '\0virtual:settings-fixture'
      if (id in stubs) return '\0virtual:settings-stub:' + id
    },
    async load(id) {
      if (id === '\0virtual:settings-fixture') return fixture
      const source = id === '\0virtual:settings-harness' ? harness : stubs[id.replace('\0virtual:settings-stub:', '')]
      if (source) return (await transformWithEsbuild(source, 'fixture.jsx', {loader:'jsx',jsx:'automatic'})).code
    },
    configureServer(vite) {vite.middlewares.use((req,res,next)=>{
      if(!req.url?.startsWith('/smart-brains/'))return next()
      res.setHeader('Content-Type','text/html')
      res.end('<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}</style></head><body><div id="root"></div><script type="module" src="/@id/__x00__virtual:settings-harness"></script></body></html>')
    })},
  }], server: {host:'127.0.0.1',port:0,hmr:false}, logLevel:'error' })
let browser
try {
  await mkdir(output,{recursive:true});await server.listen();browser=await chromium.launch({headless:true})
  const page=await browser.newPage({viewport:{width:1280,height:720}});page.setDefaultTimeout(20000)
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await page.route('**/api/**',route=>{throw Error('Real API request forbidden: '+route.request().url())})
  const origin=server.resolvedUrls.local[0].replace(/\/$/,'')
  const url=section=>origin+'/smart-brains/brain/'+section+'?targetProfileId=exact-target&releaseId=exact-release'
  const nav=()=>page.getByRole('navigation',{name:'Smart Brain navigation'})
  const advanced=()=>nav().getByRole('link',{name:'Advanced',exact:true})
  const checkScope=()=>{const current=new URL(page.url());assert.equal(current.searchParams.get('targetProfileId'),'exact-target');assert.equal(current.searchParams.get('releaseId'),'exact-release')}
  await page.goto(url('settings'));await page.getByRole('heading',{name:'Runtime connections',exact:true}).waitFor()
  await page.getByLabel('Runtime profile',{exact:true}).waitFor();checkScope()
  assert.equal(await nav().getByRole('link',{name:'Technical settings',exact:true}).count(),0)
  assert.equal(await page.getByRole('tablist',{name:'Provider technical views'}).count(),0)
  for(const [section,title,field] of [['providers','Provider parameters','OpenAI base URL'],['recovery','Recovery policy','Unknown or unavailable action'],['diagnostics','Provider checks',null],['configuration','Configuration JSON','Provider configuration JSON']]) {
    await nav().getByRole('link',{name:title,exact:true}).click()
    await page.getByRole('heading',{name:title,exact:true}).waitFor()
    if(field) await page.getByLabel(field,{exact:true}).waitFor()
    assert.equal(await nav().locator('[aria-current="page"]').count(),1);checkScope()
    assert.equal(await page.getByRole('tablist',{name:'Provider technical views'}).count(),0)
  }
  await page.getByLabel('Provider configuration JSON',{exact:true}).fill('{broken')
  await page.locator('footer').getByRole('alert').waitFor()
  assert.equal(await page.getByRole('button',{name:'Save draft',exact:true}).isDisabled(),true)
  await nav().getByRole('link',{name:'Runtime connections',exact:true}).click()
  await page.getByRole('dialog',{name:'Leave unsaved changes?'}).waitFor()
  await page.getByRole('button',{name:'Keep editing',exact:true}).click()
  assert.ok(page.url().includes('/advanced/configuration'))
  assert.equal(await page.getByLabel('Provider configuration JSON',{exact:true}).inputValue(),'{broken')
  await nav().getByRole('link',{name:'Runtime connections',exact:true}).click()
  await page.getByRole('button',{name:'Discard & leave',exact:true}).click()
  await page.getByLabel('Runtime profile',{exact:true}).waitFor()
  await nav().getByRole('link',{name:'Provider parameters',exact:true}).click()
  await page.getByLabel('OpenAI base URL',{exact:true}).fill('https://unsaved-provider.example.test')
  await nav().getByRole('link',{name:'Provider checks',exact:true}).click()
  await page.getByRole('button',{name:'Probe current edits',exact:true}).click()
  await page.waitForFunction(()=>window.fixture.calls.some(call=>call.type==='probe'))
  const probe=await page.evaluate(()=>window.fixture.calls.find(call=>call.type==='probe'))
  assert.equal(probe.payload.providerConfig.openaiBaseUrl,'https://unsaved-provider.example.test')
  assert.equal(await page.evaluate(()=>window.fixture.calls.filter(call=>call.type==='save').length),0,'Probing does not save')
  await nav().getByRole('link',{name:'AI models',exact:true}).click()
  await page.getByRole('dialog',{name:'Leave unsaved changes?'}).waitFor()
  await page.getByRole('button',{name:'Keep editing',exact:true}).click()
  await nav().getByRole('link',{name:'Provider parameters',exact:true}).click()
  assert.equal(await page.getByLabel('OpenAI base URL',{exact:true}).inputValue(),'https://unsaved-provider.example.test')
  await nav().getByRole('link',{name:'Configuration JSON',exact:true}).click()
  await page.getByRole('dialog',{name:'Leave unsaved changes?'}).waitFor()
  await page.getByRole('button',{name:'Discard & leave',exact:true}).click()
  await page.getByLabel('Provider configuration JSON',{exact:true}).waitFor()
  await nav().getByRole('link',{name:'AI models',exact:true}).click()
  await page.getByLabel('OpenAI LLM model',{exact:true}).waitFor()
  assert.equal(await page.getByRole('list',{name:'Advanced settings'}).count(),0)
  assert.equal(await page.getByRole('region',{name:'Existing vector binding'}).count(),0)
  await page.getByLabel('OpenAI LLM model',{exact:true}).fill('updated-model')
  await page.getByRole('button',{name:'Save draft',exact:true}).click()
  await page.waitForFunction(()=>window.fixture.calls.some(call=>call.type==='save'))
  assert.deepEqual(await page.evaluate(()=>window.fixture.calls.find(call=>call.type==='save').payload.providerConfig.customExtension),{keep:true})
  await page.goto(url('knowledge/search'));await page.getByLabel('Search engine',{exact:true}).waitFor();checkScope()
  await page.screenshot({path:path.join(output,'search-service-dark-1280.png')})
  assert.equal(await page.getByLabel('Embedding provider',{exact:true}).count(),0)
  assert.equal(await page.getByRole('region',{name:'Existing vector binding'}).count(),0)
  await page.getByRole('button',{name:'Embedding model',exact:true}).click();await page.getByLabel('Embedding provider',{exact:true}).waitFor()
  await page.screenshot({path:path.join(output,'embedding-dark-1280.png')})
  await page.getByRole('button',{name:'Service connection',exact:true}).click()
  assert.equal(await page.getByRole('region',{name:'Existing vector binding'}).count(),1)
  await page.getByLabel('Binding draft',{exact:true}).fill('unsaved-binding-input')
  await page.screenshot({path:path.join(output,'connection-dark-1280.png')})
  assert.equal(await page.getByRole('region',{name:'Document controller'}).count(),0)
  assert.equal(await page.evaluate(()=>window.fixture.calls.length),0,'Search settings do not fetch live sources or mutate anything')
  await page.getByRole('button',{name:'Customer separation',exact:true}).click();await page.getByLabel('Data isolation',{exact:true}).waitFor()
  await page.screenshot({path:path.join(output,'isolation-dark-1280.png')})
  assert.equal(await page.getByLabel('Search engine',{exact:true}).count(),0)
  await page.getByRole('button',{name:'Service connection',exact:true}).click()
  assert.equal(await page.getByLabel('Binding draft',{exact:true}).inputValue(),'unsaved-binding-input','Switching sections preserves the single binding editor')
  await page.getByRole('tab',{name:'Storage & uploads',exact:true}).click();await page.getByRole('region',{name:'Document controller'}).waitFor()
  assert.equal(await page.getByLabel('Embedding provider',{exact:true}).count(),0)
  await page.goto(url('settings/delivery'));await page.getByText('Action panel: webhooks',{exact:true}).waitFor();checkScope()
  await page.goto(url('settings/access'));await page.getByText('Action panel: catalogue',{exact:true}).waitFor()
  await page.goto(url('models'));await page.getByLabel('OpenAI LLM model',{exact:true}).waitFor()
  for(const mode of ['dark','light']) {
    if(mode==='light')await page.getByRole('button',{name:'Switch color theme'}).click()
    const bounds=await advanced().boundingBox();assert.ok(bounds.y>=0&&bounds.y+bounds.height<=720,'Advanced pinned at short desktop height')
    await nav().locator('[data-brain-navigation-scroll]').evaluate(element=>element.scrollTop=element.scrollHeight)
    assert.ok(await nav().getByRole('link',{name:'Updates',exact:true}).isVisible())
    await page.screenshot({path:path.join(output,`models-${mode}-1280.png`)})
  }
  await advanced().click();await page.getByLabel('Runtime profile',{exact:true}).waitFor()
  await page.screenshot({path:path.join(output,'advanced-light-1280.png')})
  assert.equal(await page.evaluate(()=>document.documentElement.scrollHeight>innerHeight+2),false)
  await page.goto(url('knowledge/search'));await page.getByLabel('Search engine',{exact:true}).waitFor()
  for(const section of ['Search service','Embedding model','Service connection','Customer separation']) {
    await page.getByRole('button',{name:section,exact:true}).click()
    assert.equal(await page.evaluate(()=>document.documentElement.scrollHeight>innerHeight+2),false,'Search setup stays inside the workspace')
    await page.screenshot({path:path.join(output,`search-${section.replaceAll(' ','-').toLowerCase()}-light-1280.png`)})
  }
  await page.setViewportSize({width:390,height:844})
  await page.getByRole('button',{name:'Embedding model',exact:true}).click();await page.getByLabel('Embedding provider',{exact:true}).waitFor()
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  await page.screenshot({path:path.join(output,'search-embedding-light-390.png'),fullPage:true})
  await page.goto(url('advanced/runtime'));await page.getByLabel('Runtime profile',{exact:true}).waitFor()
  await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'Open Smart Brain navigation'}).click()
  await advanced().waitFor();await nav().getByRole('link',{name:'Provider parameters',exact:true}).click()
  await page.getByLabel('OpenAI base URL',{exact:true}).waitFor()
  assert.equal(await page.getByRole('navigation',{name:'Smart Brain navigation'}).count(),0,'Mobile drawer closes after navigation')
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  await page.screenshot({path:path.join(output,'providers-light-390.png'),fullPage:true})
  await page.goto(url('models')+'&readonly=1');await page.getByLabel('OpenAI LLM model',{exact:true}).waitFor()
  assert.equal(await page.getByLabel('OpenAI LLM model',{exact:true}).isDisabled(),true)
  assert.deepEqual(await page.evaluate(()=>window.fixture.calls),[])
  assert.deepEqual(errors,[])
  console.log('PASS: canonical routes/legacy redirects, every advanced child, exact target/release, raw-JSON navigation guard, save preservation, storage/search separation, single vector editor, action panels, pinned short-desktop navigation, light/dark/mobile and read-only access. Screenshots: '+output)
} finally {await browser?.close();await server.close();await rm(cache,{recursive:true,force:true})}
