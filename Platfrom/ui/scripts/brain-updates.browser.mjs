import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import react from '@vitejs/plugin-react'
import { chromium } from 'playwright'

// Every operation in this harness mutates only browser memory. No backend module is loaded.
const root = fileURLToPath(new URL('../', import.meta.url))
const output = `${tmpdir()}/brain-updates-${process.pid}`
await mkdir(output, { recursive: true })
const fixture = `
const mode = new URLSearchParams(location.search).get('mode') || 'normal';
const deploymentPolicy = mode === 'developer' ? {allowedEnvironments:['staging'],tags:['staging-only']} : null;
const fields = ['actionsConfig','entityConfig','routingConfig','providerConfig','securityConfig','promptConfig','knowledgeSourceConfig','shellConfig','marketplaceDatasetConfig','behaviorConfig'];
const empty = () => Object.fromEntries(fields.map(key => [key, {}]));
const saved = { ...empty(), promptConfig: { tone: 'Consultative', prompt: 'HIDDEN_STORED_VALUE' } };
const active = { id:'release-12', deploymentId:'brain-1', deploymentVersionId:'version-12', targetProfileId:'target-staging', status:'APPLIED_VERIFIED', provisioningStatus:'ACTIVE', verificationStatus:'PASSED', currentStepDescription:'Release verified', verificationRunId:'verify-12', createdAt:'2026-11-06T12:00:00Z', updatedAt:'2026-11-06T12:00:00Z' };
const state = window.__updates = {
  mode, calls: [],
  summary: {id:'brain-1',name:'Northfield AI',deploymentPolicy},
  draft: { id:'draft-13', deploymentId:'brain-1', revisionNumber:13, updatedAt:'2026-11-07T12:00:00Z', ...saved },
  versions: [{ id:'version-12', deploymentId:'brain-1', versionLabel:'v12', status:'PUBLISHED', publishedAt:active.createdAt, sourceCapabilityManifestRequired:true },
    { id:'version-11', deploymentId:'brain-1', versionLabel:'v11', status:'PUBLISHED', publishedAt:'2026-11-05T12:00:00Z', sourceCapabilityManifestRequired:true }],
  releases: [active, { ...active, id:'production-should-not-appear', targetProfileId:'target-production' }],
  manifests: { 'version-12': { deploymentId:'brain-1', versionId:'version-12', ...empty() }, 'version-11':{deploymentId:'brain-1',versionId:'version-11',...empty(),actionsConfig:{actions:[{displayName:'Find stock'}]}} },
  workspace: { deployment:{name:'Northfield AI'}, access:{canEdit:mode !== 'read-only', canOperate:mode !== 'read-only'}, lifecycle:{savedDraftMatchesLatestPublished:false}, draft:{id:'draft-13',updatedAt:'2026-11-07T12:00:00Z'} },
};
if (mode === 'manifest-unavailable') delete state.manifests['version-12'];
if (mode === 'first-deployment') state.releases = [];
if (mode === 'past-verified') state.releases.push({...active,id:'old-version-release',deploymentVersionId:'version-11',provisioningStatus:'DELETED',createdAt:'2026-11-05T10:00:00Z'});
if (mode === 'in-progress') state.releases.unshift({...active,id:'pending-release',status:'PROVISIONING',provisioningStatus:'RUNNING',verificationStatus:'NOT_RUN',createdAt:'2026-11-07T12:01:00Z'});
if (mode === 'preview' || mode === 'preview-unavailable') {
  state.manifests['version-12'].promptConfig = {aiBehaviour:{schemaVersion:'loomai-ai-behaviour-v1',tone:{presetId:'professional',instructions:'Use concise, professional language.'}}};
  state.draft.promptConfig = {aiBehaviour:{schemaVersion:'loomai-ai-behaviour-v1',tone:{presetId:'consultative',instructions:'Use a warm, consultative tone. Ask one useful follow-up question when it helps.'}},secretRef:'HIDDEN_CREDENTIAL_VALUE'};
  state.draft.actionsConfig = {actions:[{name:'request_callback',displayName:'Request a callback',requiresConfirmation:true,accessMode:'WRITE_ONLY'}]};
  state.draft.knowledgeSourceConfig = {sources:[{id:'delivery',attributionLabel:'Delivery & collection guide',accessKey:'HIDDEN_ACCESS_KEY'}]};
  if (mode === 'preview-unavailable') delete state.manifests['version-12'];
}
export const getState = () => state;
const copy = value => structuredClone(value);
export async function fetchDeploymentDraft() { return copy(state.draft) }
export async function fetchDeploymentConfigDiffCenter() { return {live:{referenceId:''},draft:{referenceId:'draft-13',updatedAt:'2026-11-07T12:00:00Z'},sections:[]} }
export async function fetchDeploymentAiBehaviourProfiles() { return {groups:[{key:'tone',label:'Tone',options:[{id:'professional',label:'Professional'},{id:'consultative',label:'Warm & consultative'}]}]} }
export async function fetchDeploymentVersions() { return copy(state.versions) }
export async function fetchDeploymentTargetOptions() { return [{id:'target-staging',active:true,environmentName:'staging',name:'London staging'},{id:'target-production',active:true,environmentName:'production',name:'London production'}] }
export const fetchDeploymentTargetProfiles = fetchDeploymentTargetOptions;
export async function fetchDeploymentReleases() { return copy(state.releases) }
export async function fetchDeploymentVerificationRuns() { return [{id:'verify-12',summaryMessage:'Applied target checks passed'}] }
export async function fetchDeploymentTargetContext() { return {runtimeBaseUrl:'https://runtime.example.test'} }
export async function fetchDeploymentVersionManifest(brain, version) { if (!state.manifests[version]) throw new Error('Manifest access unavailable'); return copy(state.manifests[version]) }
export async function fetchCompatibleDeploymentSourceArtifacts() { return mode === 'no-source' ? [] : [{id:'image-1', imageTag:'runtime-reviewed', imageReference:'runtime@sha256:fixture'}] }
export async function validateDeploymentDraft(id) { state.calls.push({type:'validate',id}); return {publishReady:true,errorCount:0,warningCount:1,issues:[{severity:'WARNING',message:'Example warning retained'}]} }
export async function publishDeploymentDraft(id) {
  state.calls.push({type:'publish',id});
  const version = {id:'version-13',deploymentId:'brain-1',versionLabel:'v13',status:'PUBLISHED',publishedAt:'2026-11-07T13:00:00Z',sourceCapabilityManifestRequired:true};
  state.manifests[version.id] = {deploymentId:'brain-1',versionId:version.id,...copy(saved)};
  state.versions.unshift(version);
  state.draft = {...state.draft,id:'draft-14',revisionNumber:14,updatedAt:'2026-11-07T13:00:00Z'};
  state.workspace.draft = {id:state.draft.id,updatedAt:state.draft.updatedAt};
  state.workspace.lifecycle = {savedDraftMatchesLatestPublished:true,latestPublishedVersionId:version.id};
  return copy(version);
}
export async function applyDeploymentVersion(brain, version, target, source, key) {
  state.calls.push({type:'apply',brain,version,target,source,key});
  if (mode === 'retry' && state.calls.filter(item => item.type === 'apply').length === 1) throw new Error('Fixture provider unavailable');
  const result = {...active,id:'release-13',deploymentVersionId:version,targetProfileId:target,sourceArtifactId:source,createdAt:'2026-11-07T13:01:00Z',updatedAt:'2026-11-07T13:01:00Z'};
  state.releases.unshift(result); return copy(result);
}
export async function rerunDeploymentVerification(brain, id) { state.calls.push({type:'verify',brain,id}); return {summaryMessage:'Verification requested'} }
export async function reconcileDeploymentRelease() { state.calls.push({type:'reconcile'}); return copy(active) }
`
const harness = `
import React, {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,createMemoryRouter,RouterProvider} from 'react-router-dom';
import {ThemeProvider,CssBaseline,Box,Button,Typography,Stack} from '@mui/material';
import {createConsoleTheme} from '/src/theme/consoleTheme.ts';
import {BrainWorkspaceContext,BrainEditorContext} from '/src/brain/BrainWorkspaceContext.tsx';
import {BrainUpdatesPage} from '/src/brain/BrainUpdatesPage.tsx';
import {SmartBrainShell} from '/src/brain/SmartBrainShell.tsx';
import {getState} from 'virtual:brain-updates-fixture';
const state = getState();
const queryClient = new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
const params = new URLSearchParams(location.search);
const selectedTarget = params.get('target') || 'target-staging';
const initialRoute = '/smart-brains/brain-1/updates?targetProfileId='+selectedTarget+(params.has('panel')?'&panel='+params.get('panel'):'');
function Harness() {
  const [editor,register] = useState(null);
  const dark = new URLSearchParams(location.search).get('theme') !== 'light';
  const release = state.releases.find(item=>item.targetProfileId===selectedTarget);
  const context = {brainId:'brain-1',targetProfileId:selectedTarget,targetProfile:{id:selectedTarget,active:true,environmentName:selectedTarget==='target-production'?'production':'staging',name:selectedTarget==='target-production'?'London production':'London staging'},release,target:release?{deploymentId:'brain-1',targetProfileId:selectedTarget,releaseId:release.id}:null,href:section=>'/smart-brains/brain-1/'+(section||'')+'?targetProfileId='+selectedTarget};
  return <ThemeProvider theme={createConsoleTheme(dark?'dark':'light')}><CssBaseline/><BrainWorkspaceContext.Provider value={context}><BrainEditorContext.Provider value={register}>
    <Box sx={{height:'100dvh',display:'flex',flexDirection:'column'}}>
      <Box sx={{height:56,flexShrink:0,borderBottom:1,borderColor:'divider',display:'flex',alignItems:'center',px:3}}><Typography fontWeight={700}>LoomAI</Typography><Typography sx={{ml:4}}>Smart Brains / Northfield AI / Updates</Typography></Box>
      <Box sx={{height:64,flexShrink:0,borderBottom:1,borderColor:'divider',px:3,display:'flex',alignItems:'center'}}><Typography>Editing shared draft / Staging / Applied v12</Typography></Box>
      <Box component='main' sx={{flex:1,minHeight:0,p:3}}><BrainUpdatesPage/></Box>
      <Stack component='footer' direction={{xs:'column',sm:'row'}} gap={1} alignItems={{sm:'center'}} sx={{px:3,py:1.5,borderTop:1,borderColor:'divider',flexShrink:0}}><Typography variant='body2' sx={{flex:1}}>{editor?.message}</Typography><Button onClick={()=>editor?.discard?.()}>{editor?.secondaryLabel}</Button>{editor?.save&&<Button variant='contained' disabled={editor.busy||!editor.canSave} onClick={()=>editor.save()}>{editor.primaryLabel}</Button>}</Stack>
    </Box></BrainEditorContext.Provider></BrainWorkspaceContext.Provider></ThemeProvider>
}
const shellRouter = params.has('shell') ? createMemoryRouter([{path:'/smart-brains/:brainId/*',element:<SmartBrainShell session={{authenticated:true,role:'CUSTOMER_ADMIN',displayName:'Fixture customer'}} onSignOut={async()=>{}}><BrainUpdatesPage/></SmartBrainShell>}],{initialEntries:[initialRoute]}) : null;
createRoot(document.getElementById('root')).render(<QueryClientProvider client={queryClient}>{shellRouter?<RouterProvider router={shellRouter}/>:<MemoryRouter initialEntries={[initialRoute]}><Harness/></MemoryRouter>}</QueryClientProvider>);
`
const server = await createServer({ root, configFile: false, cacheDir: `${output}/vite-cache`,
  plugins: [react(), {
    name: 'brain-updates-memory-only', enforce: 'pre',
    resolveId(id, importer) {
      if (id.startsWith('virtual:brain-updates-')) return `\0${id}`
      if (!importer?.endsWith('/BrainUpdatesPage.tsx') && !importer?.endsWith('/SmartBrainShell.tsx')) return
      if (id === '../api/platformApi') return '\0virtual:brain-updates-fixture'
      if (id === '../auth/PlatformAuthProvider') return '\0virtual:brain-updates-auth'
      if (id === '../workspace/DeploymentWorkspaceContext') return '\0virtual:brain-updates-workspace'
      if (id === '../pages/RevisionsPage') return '\0virtual:brain-updates-revisions'
      if (id === './BrainCustomerReleaseTasks') return '\0virtual:brain-updates-release-tasks'
      if (id === '../components/customer/CustomerSupportContext') return '\0virtual:brain-updates-support-context'
      if (id === '../components/customer/CustomerSupportAssistant') return '\0virtual:brain-updates-support-assistant'
      if (id === '../components/customer/SupportResourceDialog') return '\0virtual:brain-updates-support-dialog'
    },
    load(id) {
      if (id === '\0virtual:brain-updates-fixture') return fixture
      if (id === '\0virtual:brain-updates-harness') return { code: harness, map: null }
      if (id === '\0virtual:brain-updates-auth') return `export const usePlatformAuth=()=>({session:{role:'CUSTOMER_ADMIN'}})`
      if (id === '\0virtual:brain-updates-workspace') return `import {getState} from 'virtual:brain-updates-fixture'; export const useDeploymentWorkspace=()=>({selectedDeploymentId:'brain-1',selectedDeploymentSummary:getState().summary,workspace:getState().workspace,deploymentsLoading:false})`
      if (id === '\0virtual:brain-updates-revisions') return `export const RevisionsPage=()=>null`
      if (id === '\0virtual:brain-updates-release-tasks') return `import {createElement} from 'react'; export const BrainCustomerReleaseTasks=({section})=>createElement('div',{'data-release-task':section},'Fixture release task: '+section)`
      if (id === '\0virtual:brain-updates-support-context') return `export const CustomerSupportProvider=({children})=>children`
      if (id === '\0virtual:brain-updates-support-assistant') return `export const CustomerSupportAssistant=()=>null`
      if (id === '\0virtual:brain-updates-support-dialog') return `export const SupportResourceDialog=()=>null`
    },
    transform(code, id) {
      if (id === '\0virtual:brain-updates-harness') return import('esbuild').then(({transform}) => transform(code, {loader:'jsx',jsx:'automatic',format:'esm'}))
    },
    configureServer(vite) {
      vite.middlewares.use('/__brain_updates_test', async (req, res) => {
        res.setHeader('Content-Type', 'text/html')
        res.end(await vite.transformIndexHtml(req.url, '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script type="module" src="/@id/__x00__virtual:brain-updates-harness"></script></body></html>'))
      })
    },
  }], server: { host: '127.0.0.1', port: 0 }, logLevel: 'error' })
let browser
try {
  await server.listen()
  const address = server.httpServer.address()
  const url = `http://127.0.0.1:${address.port}/__brain_updates_test`
  browser = await chromium.launch({headless:true})
  const page = await browser.newPage({viewport:{width:1440,height:900}})
  const errors = []
  page.on('pageerror', error => { errors.push(error.message); console.error(error.message) })
  page.on('console', message => { if (message.type() === 'error') console.error(message.text()) })
  await page.route('**/api/**', route => { throw new Error(`Real API request forbidden: ${route.request().url()}`) })
  const open = async (query = '', heading = 'Updates') => {
    await page.goto(`${url}${query}`)
    await page.getByRole('heading', {name:heading,exact:true}).waitFor()
    if (heading === 'Updates') await page.waitForFunction(() => document.querySelector('[data-brain-updates] table tbody tr'))
  }
  const calls = () => page.evaluate(() => window.__updates.calls)
  const publishToReview = async (environment = 'Staging') => {
    await page.getByRole('button', {name:'Review & publish version',exact:true}).click()
    await page.getByRole('checkbox').check()
    await page.getByRole('button', {name:'Publish version',exact:true}).click()
    await page.getByRole('dialog').getByRole('heading',{name:`Update ${environment}`,exact:true}).waitFor()
  }
  const chooseSource = async () => {
    await page.getByRole('combobox', {name:'Runtime package'}).click()
    await page.getByRole('option', {name:'runtime-reviewed image-1'}).click()
    await page.getByRole('checkbox').check()
  }

  await open()
  assert.deepEqual(await calls(), [], 'mount performs no mutations')
  await page.getByRole('button', {name:'Review & publish version',exact:true}).click()
  await page.getByRole('button', {name:'Cancel',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  assert.deepEqual(await calls(), [], 'review cancellation performs no mutations')
  for (const [width,height] of [[1440,900],[1366,768],[1280,800],[390,844]]) {
    await page.setViewportSize({width,height})
    await page.screenshot({path:`${output}/updates-dark-${width}.png`,fullPage:true})
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${width}: no horizontal overflow`)
    assert.equal(await page.locator('footer').getByRole('button', {name:'Review & publish version',exact:true}).isVisible(), true)
  }
  await page.setViewportSize({width:1440,height:900})
  await open('?theme=light')
  await page.screenshot({path:`${output}/updates-light-1440.png`,fullPage:true})
  await page.getByRole('button', {name:'View change details',exact:true}).click()
  assert.ok(!(await page.locator('body').innerText()).includes('HIDDEN_STORED_VALUE'))
  await page.getByRole('button', {name:'Close update details'}).click()
  await page.getByRole('button', {name:'Activity',exact:true}).click()
  assert.ok(!(await page.locator('body').innerText()).includes('production-should-not-appear'))

  await open('?mode=preview')
  await page.getByText('Professional → Warm & consultative',{exact:true}).waitFor()
  await page.screenshot({path:`${output}/preview-overview-dark.png`,fullPage:true})
  await page.getByRole('button',{name:'Preview How your AI replies',exact:true}).click()
  await page.getByRole('dialog').getByRole('heading',{name:'How your AI replies',exact:true}).waitFor()
  await page.getByRole('dialog').getByText('Use concise, professional language.',{exact:true}).waitFor()
  await page.getByRole('dialog').getByText('Use a warm, consultative tone. Ask one useful follow-up question when it helps.',{exact:true}).waitFor()
  assert.equal(await page.getByRole('dialog').getByRole('table',{name:'Changed configuration fields'}).isVisible(),false,'technical fields start collapsed')
  assert.ok(!(await page.locator('body').innerText()).includes('HIDDEN_CREDENTIAL_VALUE'))
  await page.screenshot({path:`${output}/preview-comparison-dark.png`,fullPage:true})
  await page.getByRole('dialog').getByRole('button',{name:'Technical details',exact:true}).click()
  assert.ok(!(await page.locator('body').innerText()).includes('HIDDEN_CREDENTIAL_VALUE'))
  await page.getByRole('dialog').getByRole('button',{name:'Back to changes',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  await page.getByRole('button',{name:'Preview What your AI can do',exact:true}).click()
  await page.getByRole('dialog').getByRole('heading',{name:'Request a callback',exact:true}).waitFor()
  await page.getByRole('dialog').getByRole('button',{name:'Back to changes',exact:true}).click()
  await page.getByRole('button',{name:'Preview What your AI knows',exact:true}).click()
  await page.getByRole('dialog').getByRole('heading',{name:'Delivery & collection guide',exact:true}).waitFor()
  assert.ok(!(await page.locator('body').innerText()).includes('HIDDEN_ACCESS_KEY'))
  await page.getByRole('dialog').getByRole('button',{name:'Back to changes',exact:true}).click()
  assert.deepEqual(await calls(),[],'all preview interactions must be read-only')
  await open('?mode=preview&theme=light')
  await page.getByRole('button',{name:'Preview How your AI replies',exact:true}).click()
  await page.getByRole('dialog').getByRole('heading',{name:'How your AI replies',exact:true}).waitFor()
  await page.screenshot({path:`${output}/preview-comparison-light.png`,fullPage:true})
  for (const [width,height] of [[1280,800],[390,844]]) {
    await page.setViewportSize({width,height})
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth > innerWidth),false)
    assert.equal(await page.getByRole('dialog').getByRole('button',{name:'Back to changes',exact:true}).isVisible(),true)
    await page.screenshot({path:`${output}/preview-comparison-${width}.png`,fullPage:true})
  }
  assert.match(await page.getByRole('dialog').getByRole('link',{name:'Edit Responses',exact:true}).getAttribute('href'), /targetProfileId=target-staging/)
  await page.getByRole('dialog').getByRole('button',{name:'Back to changes',exact:true}).click()
  await page.setViewportSize({width:1440,height:900})
  await open('?mode=preview-unavailable')
  await page.getByRole('button',{name:'Preview How your AI replies',exact:true}).click()
  await page.getByRole('dialog').getByText(/Only saved settings are shown/).waitFor()
  assert.equal(await page.getByRole('dialog').getByText('Use concise, professional language.',{exact:true}).count(),0)
  assert.deepEqual(await calls(),[])

  await open('?mode=retry')
  await publishToReview()
  await chooseSource()
  await page.getByRole('button', {name:'Apply v13 to Staging',exact:true}).click()
  await page.getByRole('dialog').getByText(/v13 remains published/).waitFor()
  await page.screenshot({path:`${output}/updates-retained-version-failure.png`,fullPage:true})
  assert.equal((await calls()).filter(item=>item.type==='publish').length,1)
  await page.getByRole('button', {name:'Apply v13 to Staging',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  const retried = await calls()
  const applies = retried.filter(item=>item.type==='apply')
  assert.equal(retried.filter(item=>item.type==='publish').length,1)
  assert.equal(applies.length,2)
  assert.equal(applies[0].key,applies[1].key,'ambiguous retry reuses the idempotency key')
  for (const item of applies) assert.deepEqual([item.brain,item.version,item.target,item.source],['brain-1','version-13','target-staging','image-1'])

  await open('?mode=no-source')
  await publishToReview()
  await page.getByRole('dialog').getByText(/No compatible runtime image/).waitFor()
  assert.equal(await page.getByRole('button',{name:'Apply v13 to Staging',exact:true}).isDisabled(),true)
  assert.equal((await calls()).filter(item=>item.type==='apply').length,0)

  await open()
  await publishToReview()
  await page.getByRole('button',{name:'Cancel',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  assert.deepEqual((await calls()).map(item=>item.type),['publish'])

  await open()
  await page.getByRole('button',{name:'Review & publish version',exact:true}).click()
  await page.evaluate(() => { window.__updates.draft.promptConfig.tone = 'Changed by another editor' })
  await page.getByRole('checkbox').check()
  await page.getByRole('button',{name:'Publish version',exact:true}).click()
  await page.getByRole('dialog').getByText(/The shared draft changed/).waitFor()
  assert.deepEqual(await calls(),[],'concurrent shared-draft change blocks stale confirmation')

  await open('?mode=read-only')
  assert.equal(await page.locator('footer').getByRole('button',{name:/publish|apply/i}).count(),0)
  assert.equal(await page.getByRole('button',{name:'Review & apply',exact:true}).count(),0)
  assert.deepEqual(await calls(),[])

  await open('?mode=in-progress')
  assert.equal(await page.locator('footer').getByRole('button',{name:'Review & publish version',exact:true}).isDisabled(),true)
  assert.deepEqual(await calls(),[],'polling and displaying progress makes no mutations')

  await open()
  await page.getByRole('button',{name:'Check draft',exact:true}).click()
  await page.getByText('WARNING: Example warning retained',{exact:true}).waitFor()
  assert.deepEqual((await calls()).map(item=>item.type),['validate'])

  await open()
  await publishToReview()
  await chooseSource()
  await page.evaluate(() => {
    const previous = window.__updates.releases[0]
    window.__updates.releases.unshift({...previous,id:'another-actor-release',createdAt:'2026-11-07T14:00:00Z'})
  })
  await page.getByRole('button',{name:'Apply v13 to Staging',exact:true}).click()
  await page.getByRole('dialog').getByText(/The applied target changed during review/).waitFor()
  assert.equal((await calls()).filter(item=>item.type==='apply').length,0)
  assert.equal((await calls()).filter(item=>item.type==='publish').length,1)

  await open()
  await page.getByRole('button',{name:'Review & apply',exact:true}).click()
  await page.getByRole('dialog').getByRole('heading',{name:'Update Staging',exact:true}).waitFor()
  await chooseSource()
  await page.getByRole('button',{name:'Apply v11 to Staging',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  assert.deepEqual((await calls()).map(item=>item.type),['apply'],'applying an older published version must not publish the draft')

  await open()
  await page.getByRole('button',{name:'View v11 details',exact:true}).click()
  await page.getByRole('heading',{name:'Version v11',exact:true}).waitFor()
  await page.getByRole('button',{name:'Close update details',exact:true}).click()
  assert.equal(await page.locator('footer').getByRole('button',{name:'Review & publish version',exact:true}).isEnabled(),true,'inspection must not replace publication with historical apply')
  assert.deepEqual(await calls(),[])
  await open('?mode=past-verified')
  await page.getByRole('button',{name:'View v11 details',exact:true}).click()
  await page.getByRole('button',{name:'Close update details',exact:true}).click()
  assert.equal(await page.locator('[aria-label="Update sequence"] .Mui-completed').count(),0,'historical verification must not complete the current draft journey')

  await open()
  await page.getByRole('button',{name:'All versions',exact:true}).click()
  await page.getByRole('textbox',{name:'Find a version'}).fill('v11')
  assert.equal(await page.getByRole('button',{name:'v12',exact:true}).count(),0)
  await page.getByRole('button',{name:'View v11 details',exact:true}).click()
  await page.getByRole('dialog').getByRole('heading',{name:'Version v11',exact:true}).waitFor()
  await page.getByRole('button',{name:'Close update details'}).click()
  assert.deepEqual(await calls(),[])

  await open('?mode=first-deployment')
  await page.getByText('New setup for Staging',{exact:true}).waitFor()
  await page.getByRole('button',{name:'Preview How your AI replies',exact:true}).click()
  await page.getByRole('dialog').getByText(/New setup. No version has been applied/).waitFor()
  await page.getByRole('button',{name:'Back to changes',exact:true}).click()
  assert.deepEqual(await calls(),[])

  await open('?shell=1')
  await page.getByRole('button',{name:'View Staging',exact:true}).waitFor()
  assert.equal(await page.getByRole('button',{name:'View Production',exact:true}).isEnabled(),true,'ordinary Brains retain production selection')
  assert.equal(await page.getByRole('columnheader',{name:'Production',exact:true}).count(),1)
  assert.match(await page.getByRole('link',{name:'Review go-live',exact:true}).getAttribute('href'),/targetProfileId=target-production&panel=promotion/)
  assert.equal(await page.getByText('Developer · Staging only',{exact:true}).count(),0)
  await page.getByRole('button',{name:'View Production',exact:true}).click()
  await page.getByText('Compared with Production v12',{exact:true}).waitFor()
  assert.equal(await page.getByRole('button',{name:'View Production',exact:true}).getAttribute('aria-pressed'),'true')
  assert.equal(await page.locator('footer').getByRole('button',{name:'Review & publish version',exact:true}).isEnabled(),true)
  await page.getByRole('button',{name:'Promote tested staging',exact:true}).click()
  await page.locator('[data-release-task="promotion"]').waitFor()
  assert.deepEqual(await calls(),[],'ordinary production selection and promotion review remain read-only')
  await page.screenshot({path:`${output}/ordinary-production-promotion.png`,fullPage:true})
  await page.getByRole('button',{name:'Back to update review',exact:true}).click()
  await page.getByRole('heading',{name:'Updates',exact:true}).waitFor()
  await publishToReview('Production')
  await chooseSource()
  await page.getByRole('button',{name:'Apply v13 to Production',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  const productionCalls = await calls()
  assert.deepEqual(productionCalls.map(item=>item.type),['publish','apply'])
  assert.equal(productionCalls[1].target,'target-production','ordinary production apply retains its selected destination')

  await open('?mode=developer&shell=1')
  await page.getByText('Developer · Staging only',{exact:true}).waitFor()
  assert.deepEqual(await page.evaluate(()=>window.__updates.summary.deploymentPolicy),{allowedEnvironments:['staging'],tags:['staging-only']})
  assert.equal(await page.getByRole('button',{name:'View Staging',exact:true}).isEnabled(),true)
  assert.equal(await page.getByRole('button',{name:'View Production',exact:true}).count(),0)
  assert.equal(await page.getByRole('columnheader',{name:'Production',exact:true}).count(),0)
  assert.equal(await page.getByRole('link',{name:'Review go-live',exact:true}).count(),0)
  assert.equal(await page.getByRole('button',{name:/^(Promote tested staging|Production workspace)$/}).count(),0)
  assert.equal(await page.getByRole('button',{name:'Required connections',exact:true}).isEnabled(),true)
  assert.equal(await page.getByRole('button',{name:'Connect Engage app',exact:true}).isEnabled(),true)
  assert.equal(await page.locator('footer').getByRole('button',{name:'Review & publish version',exact:true}).isEnabled(),true)
  await page.screenshot({path:`${output}/developer-staging-updates.png`,fullPage:true})
  await publishToReview()
  await chooseSource()
  await page.getByRole('button',{name:'Apply v13 to Staging',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  const developerCalls = await calls()
  assert.deepEqual(developerCalls.map(item=>item.type),['publish','apply'])
  assert.deepEqual(developerCalls[1],{type:'apply',brain:'brain-1',version:'version-13',target:'target-staging',source:'image-1',key:developerCalls[1].key})
  assert.ok(developerCalls[1].key,'staging update retains the existing operation key')

  await page.goto(`${url}?mode=developer&shell=1&target=target-production`)
  await page.getByText('The requested environment is not available. No alternative environment has been selected.',{exact:true}).waitFor()
  assert.equal(await page.getByRole('heading',{name:'Updates',exact:true}).count(),0)
  assert.equal(await page.getByRole('button',{name:'View Production',exact:true}).count(),0)
  assert.equal(await page.getByRole('button',{name:'View Staging',exact:true}).getAttribute('aria-pressed'),'false','a disallowed URL does not silently change targets')
  assert.equal(await page.locator('footer').getByRole('button',{name:/publish|apply/i}).count(),0)
  assert.deepEqual(await calls(),[])
  await page.getByRole('button',{name:'View Staging',exact:true}).click()
  await page.getByRole('heading',{name:'Updates',exact:true}).waitFor()
  assert.equal(await page.locator('footer').getByRole('button',{name:'Review & publish version',exact:true}).isEnabled(),true)
  assert.deepEqual(await calls(),[],'returning explicitly to staging starts no operation')

  await open('?mode=developer&shell=1&panel=promotion')
  await page.getByText('The requested release task belongs to a different environment. Select the matching environment above; no release operation was started.',{exact:true}).waitFor()
  assert.equal(await page.locator('[data-release-task="promotion"]').count(),0,'developer promotion panel never mounts')
  assert.equal(await page.getByRole('link',{name:'Review go-live',exact:true}).count(),0)
  assert.deepEqual(await calls(),[])
  assert.deepEqual(errors,[])
  console.log(JSON.stringify({result:'PASS',scenarios:['no writes on mount/cancel','dark/light desktop/mobile','no overflow','secret-safe details','target history','publish/apply retry without republish','stable idempotency key','no compatible source','version-only publish','concurrent draft change','read-only permissions','pending release is not a success','explicit readiness check with warnings','concurrent target change','existing version apply without publication','historical inspection preserves publication','past verification is not current completion','all-version search and details','first deployment','read-only clickable before-after previews','action and source preview','collapsed technical details hide unknown values','preview light/mobile and scoped edit link','unknown baseline preview','first setup preview','ordinary production selection and promotion review retained','developer staging-only shell and update apply','developer production URL blocked without silent fallback','developer promotion panel does not mount'],screenshots:output},null,2))
} finally {
  await browser?.close()
  await server.close()
}
