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
const fields = ['actionsConfig','entityConfig','routingConfig','providerConfig','securityConfig','promptConfig','knowledgeSourceConfig','shellConfig','marketplaceDatasetConfig','behaviorConfig'];
const empty = () => Object.fromEntries(fields.map(key => [key, {}]));
const saved = { ...empty(), promptConfig: { tone: 'Consultative', prompt: 'HIDDEN_STORED_VALUE' } };
const active = { id:'release-12', deploymentId:'brain-1', deploymentVersionId:'version-12', targetProfileId:'target-staging', status:'APPLIED_VERIFIED', provisioningStatus:'ACTIVE', verificationStatus:'PASSED', currentStepDescription:'Release verified', verificationRunId:'verify-12', createdAt:'2026-11-06T12:00:00Z', updatedAt:'2026-11-06T12:00:00Z' };
const state = window.__updates = {
  mode, calls: [],
  draft: { id:'draft-13', deploymentId:'brain-1', revisionNumber:13, updatedAt:'2026-11-07T12:00:00Z', ...saved },
  versions: [{ id:'version-12', deploymentId:'brain-1', versionLabel:'v12', sourceCapabilityManifestRequired:true }],
  releases: [active, { ...active, id:'production-should-not-appear', targetProfileId:'target-production' }],
  manifests: { 'version-12': { deploymentId:'brain-1', versionId:'version-12', ...empty() } },
  workspace: { deployment:{name:'Northfield AI'}, access:{canEdit:mode !== 'read-only', canOperate:mode !== 'read-only'}, lifecycle:{savedDraftMatchesLatestPublished:false}, draft:{id:'draft-13',updatedAt:'2026-11-07T12:00:00Z'} },
};
if (mode === 'manifest-unavailable') delete state.manifests['version-12'];
if (mode === 'in-progress') state.releases.unshift({...active,id:'pending-release',status:'PROVISIONING',provisioningStatus:'RUNNING',verificationStatus:'NOT_RUN',createdAt:'2026-11-07T12:01:00Z'});
export const getState = () => state;
const copy = value => structuredClone(value);
export async function fetchDeploymentDraft() { return copy(state.draft) }
export async function fetchDeploymentConfigDiffCenter() { return {live:{referenceId:''},draft:{referenceId:'draft-13',updatedAt:'2026-11-07T12:00:00Z'},sections:[]} }
export async function fetchDeploymentAiBehaviourProfiles() { return {groups:[]} }
export async function fetchDeploymentVersions() { return copy(state.versions) }
export async function fetchDeploymentReleases() { return copy(state.releases) }
export async function fetchDeploymentVerificationRuns() { return [{id:'verify-12',summaryMessage:'Applied target checks passed'}] }
export async function fetchDeploymentTargetContext() { return {runtimeBaseUrl:'https://runtime.example.test'} }
export async function fetchDeploymentVersionManifest(brain, version) { if (!state.manifests[version]) throw new Error('Manifest access unavailable'); return copy(state.manifests[version]) }
export async function fetchCompatibleDeploymentSourceArtifacts() { return mode === 'no-source' ? [] : [{id:'image-1', imageTag:'runtime-reviewed', imageReference:'runtime@sha256:fixture'}] }
export async function validateDeploymentDraft(id) { state.calls.push({type:'validate',id}); return {publishReady:true,errorCount:0,warningCount:1,issues:[{severity:'WARNING',message:'Example warning retained'}]} }
export async function publishDeploymentDraft(id) {
  state.calls.push({type:'publish',id});
  const version = {id:'version-13',deploymentId:'brain-1',versionLabel:'v13',sourceCapabilityManifestRequired:true};
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
import {MemoryRouter} from 'react-router-dom';
import {ThemeProvider,CssBaseline,Box,Button,Typography,Stack} from '@mui/material';
import {createConsoleTheme} from '/src/theme/consoleTheme.ts';
import {BrainWorkspaceContext,BrainEditorContext} from '/src/brain/BrainWorkspaceContext.tsx';
import {BrainUpdatesPage} from '/src/brain/BrainUpdatesPage.tsx';
import {getState} from 'virtual:brain-updates-fixture';
const state = getState();
const queryClient = new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
function Harness() {
  const [editor,register] = useState(null);
  const dark = new URLSearchParams(location.search).get('theme') !== 'light';
  const context = {brainId:'brain-1',targetProfileId:'target-staging',targetProfile:{id:'target-staging',active:true,environmentName:'Staging',name:'London staging'},release:state.releases.find(item=>item.id==='release-12'),target:{deploymentId:'brain-1',targetProfileId:'target-staging',releaseId:'release-12'},href:section=>'/smart-brains/brain-1/'+(section||'')+'?targetProfileId=target-staging'};
  return <ThemeProvider theme={createConsoleTheme(dark?'dark':'light')}><CssBaseline/><BrainWorkspaceContext.Provider value={context}><BrainEditorContext.Provider value={register}>
    <Box sx={{height:'100dvh',display:'flex',flexDirection:'column'}}>
      <Box sx={{height:56,flexShrink:0,borderBottom:1,borderColor:'divider',display:'flex',alignItems:'center',px:3}}><Typography fontWeight={700}>LoomAI</Typography><Typography sx={{ml:4}}>Smart Brains / Northfield AI / Updates</Typography></Box>
      <Box sx={{height:64,flexShrink:0,borderBottom:1,borderColor:'divider',px:3,display:'flex',alignItems:'center'}}><Typography>Editing shared draft / Staging / Applied v12</Typography></Box>
      <Box component='main' sx={{flex:1,minHeight:0,p:3}}><BrainUpdatesPage/></Box>
      <Stack component='footer' direction={{xs:'column',sm:'row'}} gap={1} alignItems={{sm:'center'}} sx={{px:3,py:1.5,borderTop:1,borderColor:'divider',flexShrink:0}}><Typography variant='body2' sx={{flex:1}}>{editor?.message}</Typography><Button onClick={()=>editor?.discard?.()}>{editor?.secondaryLabel}</Button>{editor?.save&&<Button variant='contained' disabled={editor.busy||!editor.canSave} onClick={()=>editor.save()}>{editor.primaryLabel}</Button>}</Stack>
    </Box></BrainEditorContext.Provider></BrainWorkspaceContext.Provider></ThemeProvider>
}
createRoot(document.getElementById('root')).render(<MemoryRouter><QueryClientProvider client={queryClient}><Harness/></QueryClientProvider></MemoryRouter>);
`
const server = await createServer({ root, configFile: false, cacheDir: `${output}/vite-cache`,
  plugins: [react(), {
    name: 'brain-updates-memory-only', enforce: 'pre',
    resolveId(id, importer) {
      if (id.startsWith('virtual:brain-updates-')) return `\0${id}`
      if (!importer?.endsWith('/BrainUpdatesPage.tsx')) return
      if (id === '../api/platformApi') return '\0virtual:brain-updates-fixture'
      if (id === '../auth/PlatformAuthProvider') return '\0virtual:brain-updates-auth'
      if (id === '../workspace/DeploymentWorkspaceContext') return '\0virtual:brain-updates-workspace'
      if (id === '../pages/RevisionsPage') return '\0virtual:brain-updates-revisions'
    },
    load(id) {
      if (id === '\0virtual:brain-updates-fixture') return fixture
      if (id === '\0virtual:brain-updates-harness') return { code: harness, map: null }
      if (id === '\0virtual:brain-updates-auth') return `export const usePlatformAuth=()=>({session:{role:'CUSTOMER_ADMIN'}})`
      if (id === '\0virtual:brain-updates-workspace') return `import {getState} from 'virtual:brain-updates-fixture'; export const useDeploymentWorkspace=()=>({selectedDeploymentId:'brain-1',workspace:getState().workspace})`
      if (id === '\0virtual:brain-updates-revisions') return `export const RevisionsPage=()=>null`
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
  const open = async (query = '') => {
    await page.goto(`${url}${query}`)
    await page.getByRole('heading', {name:'Review and publish changes'}).waitFor()
    await page.waitForFunction(() => document.querySelector('[data-brain-updates] table tbody tr'))
  }
  const calls = () => page.evaluate(() => window.__updates.calls)
  const publishToReview = async () => {
    await page.getByRole('button', {name:'Publish & update Staging',exact:true}).click()
    await page.getByRole('checkbox').check()
    await page.getByRole('button', {name:'Publish & continue',exact:true}).click()
    await page.getByRole('dialog').getByText('Version: v13', {exact:false}).waitFor()
  }
  const chooseSource = async () => {
    await page.getByRole('combobox', {name:'Runtime source artifact'}).click()
    await page.getByRole('option', {name:'runtime-reviewed / image-1'}).click()
    await page.getByRole('checkbox').check()
  }

  await open()
  assert.deepEqual(await calls(), [], 'mount performs no mutations')
  await page.getByRole('button', {name:'Publish & update Staging',exact:true}).click()
  await page.getByRole('button', {name:'Cancel',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  assert.deepEqual(await calls(), [], 'review cancellation performs no mutations')
  for (const [width,height] of [[1440,900],[1366,768],[1280,800],[390,844]]) {
    await page.setViewportSize({width,height})
    await page.screenshot({path:`${output}/updates-dark-${width}.png`,fullPage:true})
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, `${width}: no horizontal overflow`)
    assert.equal(await page.locator('footer').getByRole('button', {name:'Publish & update Staging',exact:true}).isVisible(), true)
  }
  await page.setViewportSize({width:1440,height:900})
  await open('?theme=light')
  await page.screenshot({path:`${output}/updates-light-1440.png`,fullPage:true})
  await page.getByRole('button', {name:'View change details',exact:true}).click()
  assert.ok(!(await page.locator('body').innerText()).includes('HIDDEN_STORED_VALUE'))
  await page.getByRole('button', {name:'Close update details'}).click()
  await page.getByRole('tab', {name:'History',exact:true}).click()
  assert.ok(!(await page.locator('body').innerText()).includes('production-should-not-appear'))

  await open('?mode=retry')
  await publishToReview()
  await chooseSource()
  await page.getByRole('button', {name:'Update Staging',exact:true}).click()
  await page.getByRole('dialog').getByText(/v13 remains published/).waitFor()
  await page.screenshot({path:`${output}/updates-retained-version-failure.png`,fullPage:true})
  assert.equal((await calls()).filter(item=>item.type==='publish').length,1)
  await page.getByRole('button', {name:'Update Staging',exact:true}).click()
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
  assert.equal(await page.getByRole('button',{name:'Update Staging',exact:true}).isDisabled(),true)
  assert.equal((await calls()).filter(item=>item.type==='apply').length,0)

  await open()
  await page.getByRole('button',{name:'Publish version only',exact:true}).click()
  await page.getByRole('checkbox').check()
  await page.getByRole('button',{name:'Publish version',exact:true}).click()
  await page.getByRole('dialog').waitFor({state:'hidden'})
  assert.deepEqual((await calls()).map(item=>item.type),['publish'])

  await open()
  await page.getByRole('button',{name:'Publish & update Staging',exact:true}).click()
  await page.evaluate(() => { window.__updates.draft.promptConfig.tone = 'Changed by another editor' })
  await page.getByRole('checkbox').check()
  await page.getByRole('button',{name:'Publish & continue',exact:true}).click()
  await page.getByRole('dialog').getByText(/The shared draft changed/).waitFor()
  assert.deepEqual(await calls(),[],'concurrent shared-draft change blocks stale confirmation')

  await open('?mode=read-only')
  assert.equal(await page.locator('footer').getByRole('button',{name:/Publish|Update/}).count(),0)
  assert.equal(await page.getByRole('button',{name:'Publish version only',exact:true}).count(),0)
  assert.deepEqual(await calls(),[])

  await open('?mode=in-progress')
  assert.equal(await page.locator('footer').getByRole('button',{name:'Publish & update Staging',exact:true}).isDisabled(),true)
  assert.deepEqual(await calls(),[],'polling and displaying progress makes no mutations')

  await open()
  await page.getByRole('button',{name:'Check',exact:true}).click()
  await page.getByText('WARNING: Example warning retained',{exact:true}).waitFor()
  assert.deepEqual((await calls()).map(item=>item.type),['validate'])

  await open()
  await publishToReview()
  await chooseSource()
  await page.evaluate(() => {
    const previous = window.__updates.releases[0]
    window.__updates.releases.unshift({...previous,id:'another-actor-release',createdAt:'2026-11-07T14:00:00Z'})
  })
  await page.getByRole('button',{name:'Update Staging',exact:true}).click()
  await page.getByRole('dialog').getByText(/The applied target changed during review/).waitFor()
  assert.equal((await calls()).filter(item=>item.type==='apply').length,0)
  assert.equal((await calls()).filter(item=>item.type==='publish').length,1)
  assert.deepEqual(errors,[])
  console.log(JSON.stringify({result:'PASS',scenarios:['no writes on mount/cancel','dark/light desktop/mobile','no overflow','secret-safe details','target history','publish/apply retry without republish','stable idempotency key','no compatible source','version-only publish','concurrent draft change','read-only permissions','pending release is not a success','explicit readiness check with warnings','concurrent target change'],screenshots:output},null,2))
} finally {
  await browser?.close()
  await server.close()
}
