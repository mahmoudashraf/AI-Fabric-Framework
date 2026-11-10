import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'

const root = fileURLToPath(new URL('../..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'assistant-vite-'))
const evidence = process.env.ASSISTANT_EVIDENCE_DIR || await mkdtemp(join(tmpdir(), 'assistant-evidence-'))
await mkdir(evidence, { recursive: true })
const harness = `
import React,{useState} from 'react';import{createRoot}from'react-dom/client';
import{createBrowserRouter,RouterProvider}from'react-router-dom';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
import{Box,TextField}from'@mui/material';import{CustomerConsoleTheme}from'/src/theme/CustomerConsoleTheme.tsx';
import{AssistantWorkspace}from'/src/assistant/AssistantWorkspace.tsx';import{brainAssistantScope}from'/src/assistant/scope.ts';
import{PluginLibraryPage}from'/src/pluginLibrary/PluginLibraryPage.tsx';
import{SmartBrainShell}from'/src/brain/SmartBrainShell.tsx';import{BrainModelsPage}from'/src/brain/BrainSettingsPage.tsx';
import{BrainHumanSupportPage}from'/src/brain/BrainExperiencePages.tsx';import{BrainActionReviewsPage}from'/src/brain/BrainActionReviewsPage.tsx';
import{ExperienceKitOverview}from'/src/components/kits/ExperienceKitOverview.tsx';import'/src/pages/ExperienceKits.css';
import{setPlatformAccessTokenProvider,platformRequest}from'/src/api/platformApi.ts';setPlatformAccessTokenProvider(async()=> 'assistant-fixture-token');
function Editor(){const[value,setValue]=useState('Saved manual value');const[other,setOther]=useState('Keep this manual value');
 const params=new URLSearchParams(location.search);const kind=params.get('kind');
 if(params.has('real'))return <SmartBrainShell session={{role:'CUSTOMER_ADMIN',actorId:'actor-37',customerId:'customer-37',authenticationMode:'SUPABASE',displayName:'Customer',authenticated:true}} onSignOut={async()=>{}}>{location.pathname.includes('/action-reviews')?<BrainActionReviewsPage/>:location.pathname.includes('/human-support')?<BrainHumanSupportPage/>:<BrainModelsPage/>}</SmartBrainShell>;
 const scope=kind==='ENGAGE_APP'?{resourceKind:kind,resourceId:'app-37',task:'ENGAGE'}:brainAssistantScope(location.pathname,location.search,'brain-37');
 return <Box sx={{height:'100dvh',display:'flex'}}><AssistantWorkspace scope={scope} readOnlyReason={params.has('active')?'Active Engage apps can only be previewed.':undefined}
 onSaved={async()=>{const saved=await platformRequest('/api/editor');setValue(saved.value)}}>
 <Box sx={{p:3,display:'flex',gap:2,flexDirection:'column'}}><h1>Selected editor</h1><TextField label="Manual value" value={value} onChange={e=>setValue(e.target.value)}/><TextField label="Unrelated value" value={other} onChange={e=>setOther(e.target.value)}/></Box>
 </AssistantWorkspace></Box>}
const router=createBrowserRouter([{path:'/library/plugins/*',element:<PluginLibraryPage/>},{path:'/kits/:id',element:<ExperienceKitOverview customerId="customer-37" id="kit-37"/>},{path:'/smart-brains/:brainId/*',element:<Editor/>}]);
createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}})}><CustomerConsoleTheme><RouterProvider router={router}/></CustomerConsoleTheme></QueryClientProvider>);
`
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, resolve: { dedupe: ['react', 'react-dom'] }, logLevel: 'error', server: { host: '127.0.0.1', port: 0, hmr: false }, plugins: [{
  name: 'assistant-browser-fixture', enforce: 'pre',
  resolveId(id) {
    if (id === '/__assistant.jsx') return '\0assistant.jsx'
    if (id.endsWith('/workspace/DeploymentWorkspaceContext')) return '\0assistant-workspace.js'
    if (id.endsWith('/auth/PlatformAuthProvider')) return '\0assistant-auth.js'
    if (['CustomerSupportContext', 'CustomerSupportAssistant', 'SupportResourceDialog', 'HostedDocumentStorageSetup'].some(name => id.endsWith('/' + name))) return '\0assistant-stub-' + id.split('/').at(-1)
  },
  async load(id) {
    if (id === '\0assistant.jsx') return (await transformWithEsbuild(harness, 'assistant.jsx', { loader: 'jsx', jsx: 'automatic' })).code
    if (id === '\0assistant-workspace.js') return `export function useDeploymentWorkspace(){return {selectedDeploymentId:'brain-37',selectedDeploymentSummary:{id:'brain-37',name:'Customer knowledge',binding:{customerId:'customer-37'}},workspace:{access:{canEdit:true},lifecycle:{savedDraftMatchesLatestPublished:true},draft:{id:'draft-same',revisionNumber:1}},setEditorBufferState:()=>{}}}`
    if (id === '\0assistant-auth.js') return `export function usePlatformAuth(){return {session:{role:'CUSTOMER_ADMIN',actorId:'actor-37',customerId:'customer-37',authenticationMode:'SUPABASE',authenticated:true},isLoading:false}}`
    if (id.startsWith('\0assistant-stub-')) return `export const CustomerSupportProvider=({children})=>children;export const CustomerSupportAssistant=()=>null;export const SupportResourceDialog=()=>null;export const HostedDocumentStorageSetup=()=>null;`
  },
  configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!/^\/(smart-brains|library|kits)\//.test(req.url)) return next()
    res.setHeader('Content-Type', 'text/html')
    res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}</style></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/__assistant.jsx"></script></body></html>'))
  }) },
}] })
let browser, count = 0
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`
const panel = page => page.getByRole('complementary', { name: 'Build with AI' })
const plugin = () => ({ id: 'plugin-37', ownerCustomerId: 'customer-37', visibility: 'CUSTOMER_PRIVATE', displayName: 'Private source', status: 'DRAFT', revision: 1, manifest: { schemaVersion: 1, pluginId: 'plugin-37', pluginType: 'DATA', displayName: 'Private source', version: '1.0.0', contributions: { datasets: [] }, installForm: [] }, versions: [], canManage: true, installs: { savedCount: 0, appliedCount: 0, saved: [], applied: [] } })
async function run(name, work, options = {}) {
  const page = await browser.newPage({ viewport: options.viewport ?? { width: 1440, height: 900 } })
  page.setDefaultTimeout(20000)
  const calls = [], errors = [], unexpected = []
  let session, currentPlugin = plugin(), complete = false, cancelled = false, loseResponse = options.loseResponse, editorValue = 'AI saved value', sequence = 0, reloadError = options.reloadError, model = 'saved-model'
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(dark => localStorage.setItem('loomai-customer-console-color-scheme', dark ? 'dark' : 'light'), options.dark !== false)
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method(), input = req.postDataJSON()
    calls.push({ path, method, input, key: req.headers()['idempotency-key'], auth: req.headers().authorization })
    const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    if (path === '/api/platform/auth/session') return send({ actorId: 'actor-37', customerId: 'customer-37', role: 'CUSTOMER_ADMIN', authenticated: true })
    if (path === '/api/configuration-assistant/status') return send({ enabled: !options.unavailable, ready: !options.unavailable, documentFetchEnabled: Boolean(options.url), unavailableReason: options.unavailable ? 'Authoring provider is not configured.' : '' })
    if (path === '/api/editor') return reloadError ? send({ message: 'Saved editor could not be read.' }, 503) : send({ value: editorValue })
    if (path === '/api/customer-self-service/deployments') return send([{ deploymentId: 'brain-37', name: 'Customer knowledge', status: 'DRAFT' }])
    if (path === '/api/customer-experience-kits/kit-37') return send({ id: 'kit-37', name: 'Customer experience kit', kitCode: 'ai-customer-experience', deploymentId: 'brain-37', offerId: 'offer-37', deliveryMode: 'standalone', workspace: null, humanSupportEnabled: false, updatedAt: '2026-11-10T12:00:00Z' })
    if (path === '/api/customer-self-service/catalogue/offer-37') return send({ targets: [] })
    if (path.endsWith('/document-storage-bindings')) return send([])
    if (path.endsWith('/target-profiles')) return send([{ id: 'target-stage', name: 'Customer staging', environmentName: 'staging', active: true }])
    if (path.endsWith('/releases') || path.endsWith('/versions')) return send([])
    if (path === '/api/deployments/brain-37/workspace') return send({ deployment: { id: 'brain-37', name: 'Customer knowledge', behaviorType: 'CONVERSATIONAL', binding: { customerId: 'customer-37' } }, access: { canEdit: true }, template: {} })
    if (path === '/api/deployments/brain-37/draft') return send({ id: 'draft-same', deploymentId: 'brain-37', revisionNumber: 1, entityConfig: {}, providerConfig: { llmProvider: 'openai', embeddingProvider: 'openai', openaiModel: model }, behaviorConfig: { type: 'CONVERSATIONAL', executionExtensions: [], specialistAssistance: { enabled: false, areas: [], resolutions: [], limits: { maxDecisions: 4, maxWorkerInvocations: 2, maxParallelWorkers: 2, deadlineSeconds: 75 } } } })
    if (path.endsWith('/secret-usage')) return send({ secrets: [] })
    if (path.endsWith('/provider-connectivity')) return send({ managedVectorTargets: [], probes: [], effectiveSecretResolutions: [] })
    if (path === '/api/customer/plugin-library') return send({ customerId: 'customer-37', canManage: true, plugins: [currentPlugin] })
    if (path === '/api/customer/plugin-library/plugin-37') return send(currentPlugin)
    if (path === '/api/customer/plugin-library/plugin-37/versions/1.0.0') return send({ version: '1.0.0', manifest: currentPlugin.manifest })
    if (path === '/api/configuration-assistant/sessions' && method === 'POST') {
      session = { ...input, id: `session-${++sequence}`, displayName: 'Customer knowledge', status: 'IDLE', editable: !options.active, readOnlyReason: options.active ? 'Active app preview only.' : '', activeTurnId: null, expiresAt: '2026-11-11T00:00:00Z', sources: [], turns: [] }
      return send(session)
    }
    if (path.match(/\/sessions\/session-\d+$/)) {
      if (method === 'DELETE') { session = undefined; return route.fulfill({ status: 204 }) }
      if (complete && session?.activeTurnId) {
        const turn = session.turns.at(-1)
        turn.status = options.result ?? (options.active || turn.preview ? 'PREVIEW' : 'SAVED'); turn.message = 'Fixture response: requested configuration processed.'
        turn.diff = [{ path: '/promptConfig/tone', before: 'manual', after: 'concise' }]; turn.findings = { uncertainty: ['Pagination is not documented.'], sourceRefs: [{ sourceId: 'source-1', sourcePointer: '/paths/~1catalogue/get' }] }
        turn.questions = [{ id: 'q1', question: 'Which account owns these records?', field: '/accountId' }]
        turn.receipt = turn.status === 'SAVED' ? { revision: 2, published: false } : null
        turn.runtime = { requestId: 'runtime-request-37', modelCalls: 1, accountingReceipt: { recorded: true }, usage: { tokens: 123 } }
        session.status = 'IDLE'; session.activeTurnId = null
        model = 'ai-model'
        currentPlugin = { ...currentPlugin, manifest: { ...currentPlugin.manifest, displayName: 'AI private source' }, displayName: 'AI private source', revision: 2 }
      }
      return session ? send(session) : send({ message: 'Session expired.' }, 410)
    }
    if (path.endsWith('/reusable-sources')) return send([{ sessionId: 'old-session', sourceId: 'old-source', title: 'Previously imported API', task: 'ACTIONS', mediaType: 'application/json' }])
    if (path.endsWith('/sources/reuse')) {
      session.sources.push({ id: 'source-reused', title: 'Previously imported API', mediaType: 'application/json', characters: 123, warnings: [], operations: [] }); return send(session)
    }
    if (path.includes('/sources/') && path.endsWith('/content')) return send({ id: path.split('/').at(-2), title: 'Imported source', content: 'Redacted imported documentation. <script>window.sourceExecuted=true</script>' })
    if (path.endsWith('/sources') && method === 'POST') {
      session.sources.push({ id: `source-${session.sources.length + 1}`, title: input.title, mediaType: input.mediaType, characters: 123, warnings: ['Imported documentation is untrusted evidence.'], operations: [{ id: 'catalogue', method: 'GET', path: '/catalogue', summary: 'List catalogue', sourcePointer: '/paths/~1catalogue/get' }, { id: 'enquiry', method: 'POST', path: '/enquiries', summary: 'Submit enquiry', sourcePointer: '/paths/~1enquiries/post' }] })
      return send(session)
    }
    if (path.includes('/sources/') && method === 'DELETE') { session.sources = session.sources.filter(source => source.id !== path.split('/').at(-1)); session.turns = []; return send(session) }
    if (path.endsWith('/turns')) {
      if (options.turnError) return send({ message: 'The requested task is not supported.' }, 422)
      const existing = session.turns.find(turn => turn.key === req.headers()['idempotency-key'])
      if (!existing) {
        session.turns.push({ id: `turn-${session.turns.length + 1}`, key: req.headers()['idempotency-key'], query: input.query, status: 'RUNNING', message: '', preview: input.previewOnly, createdAt: '2026-11-10T12:00:00Z', usage: { tokens: 123 } })
        session.activeTurnId = session.turns.at(-1).id; session.status = 'RUNNING'
      }
      if (loseResponse) { loseResponse = false; return route.abort('failed') }
      return send(session)
    }
    if (path.endsWith('/cancel')) {
      cancelled = true; session.turns.forEach(turn => { if (turn.status === 'RUNNING') turn.status = 'CANCELLED' }); session.status = 'IDLE'; session.activeTurnId = null; return send(session)
    }
    if (path.includes('/proposals/')) {
      const turn = session.turns.find(turn => turn.id === path.split('/').at(-2))
      if (path.endsWith('/undo')) { turn.status = 'UNDONE'; turn.undoReceipt = { revision: 3, restored: true }; editorValue = 'Restored manual value' }
      if (path.endsWith('/apply')) { turn.status = 'SAVED'; turn.receipt = { revision: 2, published: false } }
      if (path.endsWith('/check')) turn.findings = { valid: true, connectionTested: false }
      return send(session)
    }
    unexpected.push(`${method} ${path}`); return send({ message: 'Unexpected fixture request' }, 404)
  })
  try {
    await page.goto(base + (options.path ?? '/smart-brains/brain-37/responses'))
    await work({ page, calls, finish: () => { complete = true }, session: () => session, cancelled: () => cancelled, allowReload: () => { reloadError = false } })
    assert.deepEqual(errors, [], 'No browser runtime errors'); assert.deepEqual(unexpected, [], 'No unrelated requests')
    assert.ok(calls.every(call => call.auth === 'Bearer assistant-fixture-token'), 'All Console requests reuse Platform bearer authentication')
    count++; console.log(`PASS ${name}`)
  } catch (error) { await page.screenshot({ path: join(evidence, 'failure.png'), fullPage: true }); console.error(name, errors, unexpected, await page.locator('body').innerText()); throw error }
  finally { await page.close() }
}
async function open(page) { await page.getByRole('button', { name: 'Build with AI', exact: true }).click(); await panel(page).getByRole('textbox', { name: 'Message', exact: true }).waitFor(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled) }
async function send(page, text = 'Use a concise response style') { await panel(page).getByRole('textbox', { name: 'Message', exact: true }).fill(text); await panel(page).getByRole('button', { name: 'Send', exact: true }).click() }
async function review(page) { const dialog = page.getByRole('dialog', { name: /^Review / }); await dialog.getByText('Redacted imported documentation.', { exact: false }).waitFor(); assert.equal(await page.evaluate(() => window.sourceExecuted), undefined); await dialog.getByRole('button', { name: 'Close source review' }).click() }
try {
  browser = await chromium.launch({ headless: true })
  await run('saved turn pauses manual input, reloads requested values and exposes real receipts and undo', async ({ page, calls, finish }) => {
    await page.getByLabel('Manual value', { exact: true }).fill('Unsaved requested manual value')
    await page.getByLabel('Unrelated value', { exact: true }).fill('Unrelated unsaved value')
    await open(page); await send(page)
    await page.waitForFunction(() => document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isDisabled(), true)
    const creation = calls.find(call => call.path.endsWith('/sessions') && call.method === 'POST')
    assert.deepEqual(creation.input, { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'BEHAVIOUR' })
    assert.ok(calls.find(call => call.path.endsWith('/turns')).key)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.sourceIds, [], 'No implicit source sharing')
    finish(); await panel(page).getByText('SAVED', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'AI saved value')
    assert.equal(await page.getByLabel('Unrelated value', { exact: true }).inputValue(), 'Unrelated unsaved value')
    await panel(page).getByText('Save receipt', { exact: true }).click(); await panel(page).getByText('Findings, uncertainty and source references', { exact: true }).click()
    await panel(page).getByText('Which account owns these records?', { exact: false }).waitFor()
    assert.ok((await panel(page).innerText()).includes('Pagination is not documented.'))
    await panel(page).getByText('Runtime, usage and accounting', { exact: true }).click(); await panel(page).getByText('runtime-request-37', { exact: false }).waitFor()
    assert.equal(calls.filter(call => /publish|activate|disable|index/.test(call.path)).length, 0)
    await page.screenshot({ path: join(evidence, 'saved-dark-desktop.png') })
    await panel(page).getByRole('button', { name: 'Undo saved change' }).click(); await panel(page).getByText('UNDONE', { exact: true }).waitFor()
    await page.waitForFunction(() => document.querySelector('input[aria-label]')?.value !== 'AI saved value')
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Restored manual value')
    await panel(page).getByText('Undo receipt', { exact: true }).click()
    await panel(page).getByText('"restored": true', { exact: false }).waitFor()
    await page.reload(); await open(page)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1, 'Reload never replays inference')
    assert.equal(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').length, 1, 'Reload restores the scoped session')
  })
  await run('sources support paste, selected operations, binary files, URL gating and removal', async ({ page, calls, finish }) => {
    await open(page); await panel(page).getByRole('button', { name: 'Add source' }).click()
    const dialog = page.getByRole('dialog', { name: 'Add documentation' })
    assert.equal(await dialog.getByRole('tab', { name: 'URL', exact: true }).count(), 0)
    await dialog.getByRole('tab', { name: 'Paste' }).click(); await dialog.getByLabel('Source title').fill('API guide'); await dialog.getByLabel('Documentation', { exact: true }).fill('openapi: 3.1.0')
    await dialog.getByRole('combobox', { name: 'Documentation format' }).click(); await page.getByRole('option', { name: 'YAML / OpenAPI / Swagger', exact: true }).click()
    await dialog.getByRole('button', { name: 'Share source' }).click(); await dialog.waitFor({ state: 'hidden' })
    await review(page); assert.equal(calls.find(call => call.path.endsWith('/sources')).input.mediaType, 'application/yaml')
    await panel(page).locator('summary').filter({ hasText: 'Sources (' }).click()
    await panel(page).getByRole('checkbox', { name: 'API guide', exact: true }).check(); await panel(page).getByRole('checkbox', { name: 'GET /catalogue List catalogue' }).check()
    await send(page); finish(); await panel(page).getByText('SAVED', { exact: true }).waitFor()
    const turn = calls.find(call => call.path.endsWith('/turns'))
    assert.deepEqual(turn.input.sourceIds, ['source-1']); assert.deepEqual(turn.input.operationIds, ['catalogue'])
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    await panel(page).getByRole('button', { name: 'Add source' }).click()
    await dialog.getByRole('tab', { name: 'File' }).click(); await dialog.locator('input[type=file]').setInputFiles({ name: 'guide.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nbinary') })
    await dialog.getByRole('button', { name: 'Share source' }).click(); await dialog.waitFor({ state: 'hidden' })
    await review(page)
    assert.ok(calls.find(call => call.input?.mediaType === 'application/pdf').input.base64.startsWith('JVBER'))
    await panel(page).getByRole('button', { name: 'Remove API guide' }).click()
    await page.getByRole('dialog', { name: 'Remove API guide?' }).getByText('Removing this source also clears the assistant conversation; saved draft changes remain.').waitFor()
    await page.getByRole('button', { name: 'Remove source', exact: true }).click()
    await page.waitForFunction(() => !document.body.innerText.includes('Remove API guide'))
    assert.ok(calls.some(call => call.method === 'DELETE' && call.path.endsWith('/sources/source-1')))
  })
  await run('URL import is available only with the server flag', async ({ page, calls }) => {
    await open(page); await panel(page).getByRole('button', { name: 'Add source' }).click()
    const dialog = page.getByRole('dialog', { name: 'Add documentation' })
    await dialog.getByRole('tab', { name: 'URL', exact: true }).click(); await dialog.getByLabel('Public documentation URL').fill('https://docs.example.test/openapi.json'); await dialog.getByRole('button', { name: 'Share source' }).click(); await dialog.waitFor({ state: 'hidden' })
    await review(page)
    assert.equal(calls.find(call => call.path.endsWith('/sources')).input.url, 'https://docs.example.test/openapi.json')
  }, { url: true })
  await run('explicit source reuse copies one selected source and reviews it before provider use', async ({ page, calls }) => {
    await open(page); await panel(page).getByRole('button', { name: 'Add source' }).click()
    const dialog = page.getByRole('dialog', { name: 'Add documentation' }); await dialog.getByRole('tab', { name: 'Reuse', exact: true }).click()
    await dialog.getByRole('combobox', { name: 'Available source' }).click(); await page.getByRole('option', { name: 'Previously imported API / ACTIONS / application/json' }).click()
    await dialog.getByRole('button', { name: 'Reuse source', exact: true }).click(); await review(page)
    assert.deepEqual(calls.find(call => call.path.endsWith('/sources/reuse')).input, { sessionId: 'old-session', sourceIds: ['old-source'] })
    await send(page)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.sourceIds, ['source-reused'])
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.operationIds, [])
  })
  await run('active Engage has preview authority only, with check but no apply or lifecycle calls', async ({ page, calls, finish }) => {
    await open(page); const p = panel(page)
    assert.equal(await p.getByRole('checkbox', { name: 'Preview only' }).isChecked(), true)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Change the workspace title'); await p.getByRole('button', { name: 'Preview', exact: true }).click(); finish(); await p.getByText('PREVIEW', { exact: true }).waitFor()
    await p.getByRole('button', { name: 'Check preview' }).click()
    assert.equal(await p.getByRole('button', { name: 'Save preview to draft' }).isDisabled(), true)
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.previewOnly, true)
    assert.equal(calls.filter(call => /\/apply$|disable|activate/.test(call.path)).length, 0)
    await page.screenshot({ path: join(evidence, 'engage-light-mobile.png') })
    await page.waitForFunction(() => ![...document.querySelectorAll('button')].find(button => button.textContent === 'Check preview')?.disabled)
    await p.getByRole('button', { name: 'Check preview' }).focus(); await page.keyboard.press('Escape'); await panel(page).waitFor({ state: 'hidden' })
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Saved manual value')
  }, { active: true, path: '/smart-brains/brain-37/channels?kind=ENGAGE_APP&active', dark: false, viewport: { width: 390, height: 844 } })
  await run('preview remains unsaved until an explicit apply, and explain always sends previewOnly', async ({ page, calls, finish }) => {
    await open(page); const p = panel(page)
    await p.getByRole('checkbox', { name: 'Preview only' }).check(); await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Suggest a model'); await p.getByRole('button', { name: 'Preview', exact: true }).click(); finish(); await p.getByText('PREVIEW', { exact: true }).waitFor()
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    await p.getByRole('button', { name: 'Save preview to draft' }).click(); await p.getByText('SAVED', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    await p.getByRole('checkbox', { name: 'Explain only' }).check(); await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Explain this model choice'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).at(-1).input.previewOnly, true)
  }, { path: '/smart-brains/brain-37/models' })
  await run('lost turn response retries the same idempotency key and cancellation unlocks the editor', async ({ page, calls, cancelled }) => {
    await open(page); await send(page); await panel(page).getByRole('button', { name: 'Retry same turn' }).click()
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    const attempts = calls.filter(call => call.path.endsWith('/turns')); assert.equal(attempts.length, 2); assert.equal(attempts[0].key, attempts[1].key)
    await panel(page).getByRole('button', { name: 'Cancel', exact: true }).click(); await panel(page).getByText('CANCELLED', { exact: true }).waitFor()
    assert.equal(cancelled(), true); assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
  }, { loseResponse: true })
  await run('unavailable and rejected turns show actual errors without fabricated replies', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Build with AI', exact: true }).click(); await panel(page).getByText('Authoring provider is not configured.').waitFor()
    assert.equal(calls.filter(call => call.method === 'POST').length, 0)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
  }, { unavailable: true })
  await run('rejected turn unlocks manual editing and retains the query', async ({ page }) => {
    await open(page); await send(page); await panel(page).getByRole('alert').filter({ hasText: 'The requested task is not supported.' }).waitFor()
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
    assert.equal(await panel(page).getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Use a concise response style')
  }, { turnError: true })
  await run('save reload failures retain the pause until an explicit successful refresh', async ({ page, finish, allowReload }) => {
    await open(page); await send(page); finish()
    await panel(page).getByRole('alert').filter({ hasText: 'Draft changes were saved, but the editor could not be reloaded.' }).waitFor()
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isDisabled(), true)
    allowReload(); await panel(page).getByRole('button', { name: 'Refresh assistant session' }).click()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'AI saved value')
  }, { reloadError: true })
  await run('saved private plugin reloads its real editor without publishing a version', async ({ page, calls, finish }) => {
    await page.getByLabel('Display name', { exact: true }).fill('Manual plugin value'); await open(page); await send(page, 'Name this private source'); finish(); await panel(page).getByText('SAVED', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(await page.getByLabel('Display name', { exact: true }).inputValue(), 'AI private source')
    assert.deepEqual(calls.find(call => call.path.endsWith('/sessions')).input, { resourceKind: 'PRIVATE_DATA_PLUGIN', resourceId: 'plugin-37', task: 'DATA_PLUGIN' })
    assert.equal(calls.filter(call => call.path.endsWith('/versions') && call.method !== 'GET').length, 0)
  }, { path: '/library/plugins/plugin-37/edit' })
  await run('immutable plugin versions and unsaved new plugins have no mutating assistant entry', async ({ page, calls }) => {
    await page.getByLabel('Display name', { exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).count(), 0)
    assert.equal(calls.filter(call => call.path.startsWith('/api/configuration-assistant')).length, 0)
    await page.goto(base + '/library/plugins/new/data'); await page.getByLabel('Display name', { exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).count(), 0)
  }, { path: '/library/plugins/plugin-37/versions/1.0.0' })
  await run('desktop resize, dismissal retains input, narrow light layout fits and Enter submits', async ({ page, finish }) => {
    await open(page); const p = panel(page); await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Retained question')
    const before = await p.boundingBox(); await page.getByRole('separator', { name: 'Resize assistant' }).focus(); await page.keyboard.press('ArrowLeft'); const after = await p.boundingBox(); assert.ok(after.width > before.width)
    await p.getByRole('button', { name: 'Close assistant' }).click(); await page.getByRole('button', { name: 'Build with AI', exact: true }).click(); assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Retained question')
    await p.getByRole('textbox', { name: 'Message', exact: true }).press('Shift+Enter'); assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Retained question\n')
    await p.getByRole('textbox', { name: 'Message', exact: true }).press('Enter'); finish(); await p.getByText('SAVED', { exact: true }).waitFor()
    await page.setViewportSize({ width: 320, height: 740 }); await p.waitFor(); assert.ok((await p.boundingBox()).width <= 320)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.screenshot({ path: join(evidence, 'assistant-light-320.png') })
  }, { dark: false })
  await run('real Smart Brain editor replaces requested unsaved values even with the same draft ID and revision', async ({ page, calls, finish }) => {
    const field = page.getByLabel('OpenAI LLM model', { exact: true }); await field.fill('manual-unsaved-model')
    await open(page); await send(page, 'Use the selected AI model')
    await page.waitForFunction(() => document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(await page.locator('footer button').filter({ hasText: 'Working...' }).isDisabled(), true)
    await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByRole('link', { name: 'Updates', exact: true }).click()
    const blocked = page.getByRole('dialog', { name: 'An operation is in progress' }); await blocked.waitFor()
    assert.equal(await blocked.getByRole('button', { name: 'Discard & leave' }).count(), 0)
    await blocked.getByRole('button', { name: 'Keep editing' }).click()
    assert.equal(calls.filter(call => /publish|\/apply$/.test(call.path)).length, 0)
    finish(); await panel(page).getByText('SAVED', { exact: true }).waitFor()
    await page.waitForFunction(() => [...document.querySelectorAll('input')].some(input => input.value === 'ai-model'))
    assert.equal(await field.inputValue(), 'ai-model'); assert.equal(await page.getByRole('dialog').count(), 0, 'No conflict approval')
    assert.ok(calls.filter(call => call.path === '/api/deployments/brain-37/draft').length >= 2)
    await page.screenshot({ path: join(evidence, 'real-brain-same-revision-dark.png') })
  }, { path: '/smart-brains/brain-37/models?real&targetProfileId=target-stage' })
  await run('actual Human support links bind Specialists and reviews with their own draft/guide scopes', async ({ page, calls }) => {
    await page.getByRole('navigation', { name: 'Human support tasks' }).getByRole('link', { name: 'Specialists', exact: true }).click()
    await page.getByRole('heading', { name: 'Specialists', exact: true }).waitFor(); await page.getByRole('checkbox', { name: 'Specialist assistance' }).waitFor()
    await open(page); assert.equal(calls.find(call => call.path.endsWith('/sessions')).input.task, 'SPECIALISTS')
    await panel(page).getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('navigation', { name: 'Human support tasks' }).getByRole('link', { name: 'Action reviews', exact: true }).click(); await open(page)
    assert.equal(calls.filter(call => call.path.endsWith('/sessions')).at(-1).input.task, 'HUMAN_SUPPORT')
    await panel(page).getByRole('textbox', { name: 'Message', exact: true }).fill('Explain review setup'); await panel(page).getByRole('button', { name: 'Explain', exact: true }).click()
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.previewOnly, true)
  }, { path: '/smart-brains/brain-37/human-support?real&targetProfileId=target-stage' })
  await run('real Kit details guide binds the saved Kit Brain and links to real resource editors', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Kit AI guide', exact: true }).click(); const p = panel(page)
    await p.getByRole('textbox', { name: 'Message', exact: true }).waitFor(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.deepEqual(calls.find(call => call.path.endsWith('/sessions')).input, { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'KIT' })
    await p.getByText('Open resource editor', { exact: true }).click()
    assert.equal(await p.getByRole('link', { name: 'Specialists', exact: true }).getAttribute('href'), '/smart-brains/brain-37/human-support/specialists')
    assert.equal(await p.getByRole('link', { name: 'Actions', exact: true }).getAttribute('href'), '/smart-brains/brain-37/capabilities/actions')
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Help me configure this Kit'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.previewOnly, true)
    assert.equal(calls.filter(call => /\/apply$|publish|activate|disable/.test(call.path)).length, 0)
    await page.screenshot({ path: join(evidence, 'kit-guide-real-details.png') })
  }, { path: '/kits/kit-37' })
  console.log(`PASS ${count} browser groups. Fixture-only evidence: ${evidence}`)
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
