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
import{createBrowserRouter,RouterProvider,Link,useLocation,useParams}from'react-router-dom';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
import{Box,TextField}from'@mui/material';import{CustomerConsoleTheme}from'/src/theme/CustomerConsoleTheme.tsx';
import{AssistantWorkspace}from'/src/assistant/AssistantWorkspace.tsx';import{brainAssistantScope}from'/src/assistant/scope.ts';
import{KitAssistantGuide}from'/src/assistant/KitAssistantGuide.tsx';
import{PluginLibraryPage}from'/src/pluginLibrary/PluginLibraryPage.tsx';
import{SmartBrainShell}from'/src/brain/SmartBrainShell.tsx';import{BrainModelsPage}from'/src/brain/BrainSettingsPage.tsx';
import{BrainHumanSupportPage}from'/src/brain/BrainExperiencePages.tsx';import{BrainActionReviewsPage}from'/src/brain/BrainActionReviewsPage.tsx';
import{ExperienceKitOverview}from'/src/components/kits/ExperienceKitOverview.tsx';import'/src/pages/ExperienceKits.css';
import{setPlatformAccessTokenProvider,platformRequest}from'/src/api/platformApi.ts';setPlatformAccessTokenProvider(async()=> 'assistant-fixture-token');
function Editor(){const[value,setValue]=useState('Saved manual value');const[other,setOther]=useState('Keep this manual value');const[real]=useState(()=>new URLSearchParams(window.location.search).has('real'));
 const location=useLocation();const{brainId}=useParams();const params=new URLSearchParams(location.search);const kind=params.get('kind');
 if(real)return <SmartBrainShell session={{role:'CUSTOMER_ADMIN',actorId:'actor-37',customerId:'customer-37',authenticationMode:'SUPABASE',displayName:'Customer',authenticated:true}} onSignOut={async()=>{}}>{location.pathname.includes('/action-reviews')?<BrainActionReviewsPage/>:location.pathname.includes('/human-support')?<BrainHumanSupportPage/>:<BrainModelsPage/>}</SmartBrainShell>;
 const scope=kind==='ENGAGE_APP'?{resourceKind:kind,resourceId:'app-37',task:'ENGAGE'}:brainAssistantScope(location.pathname,location.search,brainId??'brain-37');
 return <Box sx={{height:'100dvh',display:'flex'}}><AssistantWorkspace scope={scope} readOnlyReason={params.has('active')?'Active Engage apps can only be previewed.':undefined}
 onSaved={async()=>{const saved=await platformRequest('/api/editor');setValue(saved.value)}}>
 <Box sx={{p:3,display:'flex',gap:2,flexDirection:'column'}}><h1>Selected editor</h1><TextField label="Manual value" value={value} onChange={e=>setValue(e.target.value)}/><TextField label="Unrelated value" value={other} onChange={e=>setOther(e.target.value)}/>
 {params.has('scopeTest')&&<><Link to="/smart-brains/brain-37/models?scopeTest">Select Models task</Link><Link to="/smart-brains/brain-38/models?scopeTest">Select another Brain</Link></>}</Box>
 </AssistantWorkspace></Box>}
function KitGuideFixture(){const[brainId,setBrainId]=useState('brain-37');return <><button onClick={()=>setBrainId('brain-38')}>Select another Kit Brain</button><KitAssistantGuide brainId={brainId}/></>}
const router=createBrowserRouter([{path:'/library/plugins/*',element:<PluginLibraryPage/>},{path:'/kits/scope-fixture',element:<KitGuideFixture/>},{path:'/kits/:id',element:<ExperienceKitOverview customerId="customer-37" id="kit-37"/>},{path:'/smart-brains/:brainId/*',element:<Editor/>}]);
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
  if (process.env.ASSISTANT_CASE_FILTER && !name.includes(process.env.ASSISTANT_CASE_FILTER)) return
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
    if (path === '/api/configuration-assistant/status') return send({ enabled: !options.unavailable, ready: !options.unavailable, documentFetchEnabled: Boolean(options.url), unavailableReason: options.unavailable ? 'Authoring provider is not configured.' : '', validationFeedback: options.validationFeedback === null ? undefined : options.validationFeedback ?? { defaultEnabled: true, maxCorrections: 2 } })
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
        turn.diff = options.diff ?? [{ path: '/promptConfig/tone', before: 'manual', after: 'concise' }]; turn.findings = structuredClone(options.findings ?? { uncertainty: ['Pagination is not documented.'], sourceRefs: [{ sourceId: 'source-1', sourcePointer: '/paths/~1catalogue/get' }] })
        turn.questions = [{ id: 'q1', question: 'Which account owns these records?', field: '/accountId' }]
        turn.receipt = turn.status === 'SAVED' ? { revision: 2, published: false } : null
        turn.attempts = structuredClone(options.finalAttempts ?? [{ number: 1, status: 'CHECKED', completedAt: '2026-11-10T12:01:00Z' }])
        turn.feedbackStopReason = options.feedbackStopReason
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
        session.turns.push({ id: `turn-${session.turns.length + 1}`, key: req.headers()['idempotency-key'], query: input.query, status: 'RUNNING', message: '', preview: input.previewOnly, createdAt: '2026-11-10T12:00:00Z', usage: { tokens: 123 }, validationFeedback: input.validationFeedback, attempts: structuredClone(options.runningAttempts ?? [{ number: 1, status: 'RUNNING', startedAt: '2026-11-10T12:00:00Z' }]), feedbackStopReason: options.runningStopReason })
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
async function addResizeSource(page) {
  await panel(page).getByRole('button', { name: 'Add source' }).click()
  const dialog = page.getByRole('dialog', { name: 'Add documentation' })
  await dialog.getByRole('tab', { name: 'Paste' }).click(); await dialog.getByLabel('Source title').fill('Resize API'); await dialog.getByLabel('Documentation', { exact: true }).fill('openapi: 3.1.0')
  await dialog.getByRole('button', { name: 'Share source' }).click(); await dialog.waitFor({ state: 'hidden' }); await review(page)
  await panel(page).locator('summary').filter({ hasText: 'Sources (' }).click()
  await panel(page).getByRole('checkbox', { name: 'GET /catalogue List catalogue' }).check()
}
async function enterPanelInputs(page, query) {
  await addResizeSource(page)
  const p = panel(page)
  await p.getByRole('combobox', { name: 'Max corrections' }).click(); await page.getByRole('option', { name: '1', exact: true }).click()
  await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).uncheck()
  await p.getByRole('checkbox', { name: 'Preview only' }).check(); await p.getByRole('checkbox', { name: 'Explain only' }).check()
  await p.getByRole('textbox', { name: 'Message', exact: true }).fill(query)
}
async function assertPanelInputs(page, query) {
  const p = panel(page)
  assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), query)
  assert.equal(await p.getByRole('checkbox', { name: 'Preview only' }).isChecked(), true)
  assert.equal(await p.getByRole('checkbox', { name: 'Explain only' }).isChecked(), true)
  assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), false)
  assert.equal(await p.getByRole('combobox', { name: 'Max corrections' }).innerText(), '1')
  const sources = p.locator('summary').filter({ hasText: 'Sources (' })
  assert.equal(await sources.innerText(), 'Sources (1 selected) / 1 selected operations')
  if (!await sources.evaluate(node => node.parentElement.open)) await sources.click()
  assert.equal(await p.getByRole('checkbox', { name: 'Resize API', exact: true }).isChecked(), true)
  assert.equal(await p.getByRole('checkbox', { name: 'GET /catalogue List catalogue' }).isChecked(), true)
  assert.equal(await p.getByRole('checkbox', { name: 'POST /enquiries Submit enquiry' }).isChecked(), false)
}
async function resizeWorkspace(page, viewport) {
  await page.setViewportSize(viewport)
  await page.waitForFunction(() => {
    const node = document.getElementById('configuration-assistant-panel'), rect = node?.getBoundingClientRect()
    return rect && Boolean(node.closest('.MuiDrawer-paper')) === (innerWidth < 1200) && rect.left >= -1 && rect.right <= innerWidth + 1
  })
}
async function latestSaved(page) {
  const status = panel(page).getByRole('article', { name: 'Assistant', exact: true }).last().getByText('SAVED', { exact: true })
  await status.waitFor(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled); await status.scrollIntoViewIfNeeded()
  assert.equal(await status.evaluate(node => {
    const rect = node.getBoundingClientRect(), log = node.closest('[role="log"]').getBoundingClientRect()
    return rect.top >= Math.max(0, log.top) - 1 && rect.bottom <= Math.min(innerHeight, log.bottom) + 1
  }), true, 'Latest authoritative SAVED terminal is inside the visible conversation viewport')
}
try {
  browser = await chromium.launch({ headless: true })
  const invalidAttempt = { number: 1, status: 'VALIDATION_ERRORS', startedAt: '2026-11-10T12:00:00Z', completedAt: '2026-11-10T12:00:10Z', message: 'Candidate requires correction.', findings: { valid: false, issues: [{ path: '/manifest', message: 'Missing source mapping.' }] }, changes: { proposed: 'intermediate-only' }, runtime: { requestId: 'attempt-runtime-1' } }
  const runningCorrection = { number: 2, status: 'RUNNING', startedAt: '2026-11-10T12:00:11Z' }
  await run('validation feedback defaults, bounded selection, opt out and busy controls send explicit policy', async ({ page, calls }) => {
    await open(page); const p = panel(page), toggle = p.getByRole('checkbox', { name: 'Let AI fix validation errors' }), limit = p.getByRole('combobox', { name: 'Max corrections' })
    assert.equal(await toggle.isChecked(), true); assert.equal(await limit.innerText(), '2')
    await limit.click(); assert.deepEqual(await page.getByRole('option').allTextContents(), ['1', '2']); await page.getByRole('option', { name: '1', exact: true }).click()
    await send(page)
    await p.getByText('Attempt 1 of 2', { exact: true }).waitFor()
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.validationFeedback, { enabled: true, maxCorrections: 1 })
    assert.equal(await toggle.isDisabled(), true); assert.equal(await limit.isDisabled(), true)
    await p.getByRole('button', { name: 'Cancel', exact: true }).click(); await p.getByText('CANCELLED', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    await toggle.uncheck(); assert.equal(await limit.isDisabled(), true)
    await send(page, 'Make this change without corrections')
    await p.getByText('Attempt 1 of 1', { exact: true }).waitFor()
    const turns = calls.filter(call => call.path.endsWith('/turns'))
    assert.equal(turns.length, 2); assert.deepEqual(turns[1].input.validationFeedback, { enabled: false, maxCorrections: 0 })
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0, 'No intermediate save reload')
  })
  await run('validation feedback honors disabled server default and the lower server ceiling', async ({ page, calls }) => {
    await open(page); const p = panel(page), toggle = p.getByRole('checkbox', { name: 'Let AI fix validation errors' }), limit = p.getByRole('combobox', { name: 'Max corrections' })
    assert.equal(await toggle.isChecked(), false); assert.equal(await limit.isDisabled(), true)
    await toggle.check(); await limit.click(); assert.deepEqual(await page.getByRole('option').allTextContents(), ['1']); await page.getByRole('option', { name: '1', exact: true }).click()
    await send(page); await p.getByText('Attempt 1 of 2', { exact: true }).waitFor()
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.validationFeedback, { enabled: true, maxCorrections: 1 })
  }, { validationFeedback: { defaultEnabled: false, maxCorrections: 1 } })
  await run('validation feedback absent capability disables correction and sends zero', async ({ page, calls }) => {
    await open(page); const p = panel(page), toggle = p.getByRole('checkbox', { name: 'Let AI fix validation errors' })
    assert.equal(await toggle.isChecked(), false); assert.equal(await toggle.isDisabled(), true)
    await send(page); await p.getByText('RUNNING', { exact: true }).waitFor()
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.validationFeedback, { enabled: false, maxCorrections: 0 })
  }, { validationFeedback: null })
  await run('validation feedback shows running correction history without saving intermediates', async ({ page, calls, finish }) => {
    await page.getByLabel('Manual value', { exact: true }).fill('Unsaved manual buffer')
    await page.getByLabel('Unrelated value', { exact: true }).fill('Unrelated retained buffer')
    await open(page); await send(page)
    const p = panel(page), article = p.getByRole('article', { name: 'Assistant', exact: true })
    await article.getByText('Attempt 2 of 3', { exact: true }).waitFor()
    await article.getByText('AI is correcting the reported validation errors.', { exact: true }).waitFor()
    await page.screenshot({ path: join(evidence, 'validation-feedback-running-desktop.png') })
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    assert.equal(await article.getByText('SAVED', { exact: true }).count(), 0)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Unsaved manual buffer')
    await article.getByText('Validation feedback (2 attempts)', { exact: true }).click()
    await article.getByText('Attempt 1 / VALIDATION_ERRORS', { exact: true }).waitFor()
    await article.getByText('Attempt 2 / RUNNING', { exact: true }).waitFor()
    await article.getByText('Attempt findings', { exact: true }).click(); await article.getByText('Missing source mapping.', { exact: false }).waitFor()
    await article.getByText('Attempt changes', { exact: true }).click(); await article.getByText('intermediate-only', { exact: false }).waitFor()
    await article.getByText('Attempt runtime', { exact: true }).click(); await article.getByText('attempt-runtime-1', { exact: false }).waitFor()
    await page.screenshot({ path: join(evidence, 'validation-feedback-history-desktop.png') })
    finish(); await article.getByText('Draft saved. Configuration validation passed.', { exact: true }).waitFor()
    assert.equal(await article.getByText('Attempt 2 of 3', { exact: true }).count(), 0)
    await article.getByText('Attempt 1 / VALIDATION_ERRORS', { exact: true }).waitFor()
    await article.getByText('Attempt 2 / CHECKED', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 1, 'Only final authoritative save reloads editor')
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1, 'UI never posts correction turns')
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'AI saved value')
    assert.equal(await page.getByLabel('Unrelated value', { exact: true }).inputValue(), 'Unrelated retained buffer')
    await latestSaved(page); await page.screenshot({ path: join(evidence, 'validation-feedback-final-saved-desktop.png') })
  }, { runningAttempts: [invalidAttempt, runningCorrection], runningStopReason: 'CORRECTING_VALIDATION_ERRORS', finalAttempts: [invalidAttempt, { ...runningCorrection, status: 'CHECKED', completedAt: '2026-11-10T12:01:00Z' }], feedbackStopReason: 'VALIDATION_PASSED', findings: { valid: true } })
  for (const result of ['INVALID', 'FAILED', 'CANCELLED']) {
    await run(`validation feedback ${result} retains unfinished findings without a saved claim at 320px`, async ({ page, calls, finish }) => {
      await page.getByLabel('Manual value', { exact: true }).fill('Manual buffer survives failure')
      await open(page); await send(page); const p = panel(page), article = p.getByRole('article', { name: 'Assistant', exact: true })
      await article.getByText('Attempt 2 of 3', { exact: true }).waitFor(); finish()
      await article.getByText(result, { exact: true }).waitFor()
      await article.getByText(result === 'INVALID' ? 'Correction limit reached. Review the remaining findings or continue manually.' : 'Correction stopped. Review the failure and retained attempt history.', { exact: true }).waitFor()
      await article.getByText('Validation feedback (2 attempts)', { exact: true }).click()
      await article.getByText('Attempt 1 / VALIDATION_ERRORS', { exact: true }).waitFor(); await article.getByText('Attempt 2 / RUNNING', { exact: true }).waitFor(); await article.getByText('Not completed', { exact: true }).waitFor()
      await article.getByText('Attempt findings', { exact: true }).click(); await article.getByText('Missing source mapping.', { exact: false }).waitFor()
      assert.equal(await article.getByText('Attempt 2 of 3', { exact: true }).count(), 0, 'Terminal turns do not show stale active progress')
      assert.equal(await article.getByText('SAVED', { exact: true }).count(), 0)
      assert.equal(await article.getByRole('region', { name: 'Saved changes', exact: true }).count(), 0)
      assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      await p.getByRole('button', { name: 'Close assistant' }).click()
      assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Manual buffer survives failure')
      assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
      await page.getByRole('button', { name: 'Build with AI', exact: true }).click()
      await page.waitForFunction(() => { const rect = document.getElementById('configuration-assistant-panel').getBoundingClientRect(); return rect.left >= -1 && rect.right <= innerWidth + 1 })
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth && [...document.querySelectorAll('#configuration-assistant-panel pre')].every(node => node.scrollWidth <= node.clientWidth)), true)
      await page.screenshot({ path: join(evidence, `validation-feedback-${result.toLowerCase()}-320.png`) })
    }, { result, runningAttempts: [invalidAttempt, runningCorrection], finalAttempts: [invalidAttempt, runningCorrection], feedbackStopReason: result === 'INVALID' ? 'CORRECTION_LIMIT_REACHED' : 'FAILED', findings: { valid: false }, dark: false, viewport: { width: 320, height: 740 } })
  }
  await run('validation feedback exhausted invalid attempts stay unsaved and inspectable', async ({ page, calls, finish }) => {
    await open(page); await send(page); finish(); const p = panel(page), article = p.getByRole('article', { name: 'Assistant', exact: true })
    await article.getByText('INVALID', { exact: true }).waitFor()
    await article.getByText('Correction limit reached. Review the remaining findings or continue manually.', { exact: true }).waitFor()
    await article.getByText('Validation feedback (3 attempts)', { exact: true }).click()
    for (const number of [1, 2, 3]) await article.getByText(`Attempt ${number} / VALIDATION_ERRORS`, { exact: true }).waitFor()
    assert.equal(await article.getByText('Not completed', { exact: true }).count(), 0)
    assert.equal(await article.getByText('SAVED', { exact: true }).count(), 0)
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { result: 'INVALID', finalAttempts: [1, 2, 3].map(number => ({ ...invalidAttempt, number })), feedbackStopReason: 'CORRECTION_LIMIT_REACHED', findings: { valid: false } })
  await run('validation feedback saved invalid final candidate remains needs attention, not corrected', async ({ page, calls, finish }) => {
    await open(page); await send(page); finish(); const p = panel(page), article = p.getByRole('article', { name: 'Assistant', exact: true })
    await article.getByText('Draft saved. Needs attention.', { exact: true }).waitFor()
    await article.getByText('Correction limit reached. Review the remaining findings or continue manually.', { exact: true }).waitFor()
    assert.equal(await article.getByText('Draft saved. Configuration validation passed.', { exact: true }).count(), 0)
    assert.equal(await article.getByText('SAVED', { exact: true }).evaluate(node => node.closest('.MuiChip-root').classList.contains('MuiChip-colorSuccess')), false)
    await article.getByText('Validation feedback (2 attempts)', { exact: true }).click()
    await article.getByText('Attempt 1 / INVALID_OUTPUT', { exact: true }).waitFor()
    await article.getByText('Structured output rejected.', { exact: true }).waitFor()
    await article.getByText('Attempt 2 / VALIDATION_ERRORS', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 1, 'Actual final saved candidate alone reloads editor')
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { finalAttempts: [{ number: 1, status: 'INVALID_OUTPUT', message: 'Structured output rejected.', completedAt: '2026-11-10T12:00:10Z' }, { ...invalidAttempt, number: 2 }], feedbackStopReason: 'CORRECTION_LIMIT_REACHED', findings: { valid: false, issues: [{ message: 'Final mapping still needs attention.' }] } })
  const dataValidation = { valid: false, revision: 2, issues: [{ path: '/contributions', code: 'MANIFEST_CONTRACT_INVALID', message: 'The saved manifest requires a source mapping.' }], connectionTested: false }
  await run('validation immediately distinguishes invalid saved Data from a valid follow-up', async ({ page, calls, finish }) => {
    await open(page); await send(page); finish()
    const p = panel(page), first = p.getByRole('article', { name: 'Assistant', exact: true }).first()
    await first.getByText('Draft saved. Needs attention.', { exact: true }).waitFor()
    await first.getByText('The saved manifest requires a source mapping.', { exact: true }).waitFor()
    await first.getByText('Connection not tested.', { exact: true }).waitFor()
    assert.equal(await first.getByText('SAVED', { exact: true }).evaluate(node => node.closest('.MuiChip-root').classList.contains('MuiChip-colorSuccess')), false)
    assert.equal(await first.locator('details[open]').count(), 0, 'Validation is visible without expanding raw JSON')
    await page.screenshot({ path: join(evidence, 'saved-invalid-validation-desktop.png') })
    dataValidation.valid = true; dataValidation.revision = 3; dataValidation.issues = []
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    await send(page, 'Correct the source mapping')
    const second = p.getByRole('article', { name: 'Assistant', exact: true }).nth(1)
    await second.getByText('Draft saved. Configuration validation passed.', { exact: true }).waitFor()
    await second.getByText('Connection not tested.', { exact: true }).waitFor()
    assert.equal(await first.getByText('Draft saved. Needs attention.', { exact: true }).count(), 1, 'History retains its actual invalid result')
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 2)
    await latestSaved(page); await page.screenshot({ path: join(evidence, 'latest-follow-up-saved-desktop.png') })
  }, { findings: dataValidation })
  const longPath = '/actionsConfig/actions/route/' + 'long-field-name-'.repeat(8)
  await run('validation and structural diff remain readable at 320px without unchanged object fields', async ({ page, finish }) => {
    await open(page); await send(page); finish()
    const p = panel(page), article = p.getByRole('article', { name: 'Assistant', exact: true })
    await article.getByText('Draft saved. Configuration validation passed.', { exact: true }).waitFor()
    await article.getByText('0 validation errors; 4 warnings', { exact: true }).waitFor()
    const summary = article.getByRole('region', { name: 'Saved changes', exact: true })
    await summary.getByText(longPath, { exact: true }).waitFor()
    assert.equal(await summary.getByRole('listitem').count(), 1)
    assert.equal((await summary.innerText()).includes('unchanged-description'), false)
    assert.equal((await summary.innerText()).includes('unchanged-endpoint'), false)
    assert.equal(await article.getByText('Connection not tested.', { exact: true }).count(), 0, 'Absent connectivity metadata is not invented')
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    assert.equal(await summary.evaluate(node => node.scrollWidth > node.clientWidth + 1), false)
    await summary.scrollIntoViewIfNeeded()
    await page.screenshot({ path: join(evidence, 'saved-valid-field-diff-light-320.png') })
    await article.getByText('Advanced', { exact: true }).click()
    await article.getByText('Saved diff', { exact: true }).click()
    await article.getByText('unchanged-description', { exact: false }).waitFor()
    await article.getByText('Findings, uncertainty and source references', { exact: true }).click()
    await article.getByText('"warningCount": 4', { exact: false }).waitFor()
  }, { viewport: { width: 320, height: 740 }, dark: false,
    findings: { publishReady: true, errorCount: 0, warningCount: 4, issues: [] },
    diff: [{ path: '/actionsConfig/actions', operation: 'UPSERT_ITEM', key: 'read', existed: true,
      before: { name: 'read', description: 'unchanged-description', route: { endpoint: 'unchanged-endpoint', ['long-field-name-'.repeat(8)]: 'before-' + 'a'.repeat(100) } },
      after: { description: 'unchanged-description', route: { ['long-field-name-'.repeat(8)]: 'after-' + 'b'.repeat(100), endpoint: 'unchanged-endpoint' }, name: 'read' } }] })
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
    await panel(page).getByText('Advanced', { exact: true }).click()
    await panel(page).getByText('Save receipt', { exact: true }).click(); await panel(page).getByText('Findings, uncertainty and source references', { exact: true }).click()
    await panel(page).getByText('Which account owns these records?', { exact: false }).waitFor()
    assert.ok((await panel(page).innerText()).includes('Pagination is not documented.'))
    await panel(page).getByText('Runtime, usage and accounting', { exact: true }).click(); await panel(page).getByText('runtime-request-37', { exact: false }).waitFor()
    assert.equal(calls.filter(call => /publish|activate|disable|index/.test(call.path)).length, 0)
    await latestSaved(page)
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
    assert.deepEqual(turn.input.validationFeedback, { enabled: true, maxCorrections: 2 }, 'Feedback preserves explicit source and operation choices')
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
    await p.getByRole('checkbox', { name: 'Explain only' }).check()
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isDisabled(), true)
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), false)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Explain this model choice'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).at(-1).input.previewOnly, true)
    assert.deepEqual(calls.filter(call => call.path.endsWith('/turns')).at(-1).input.validationFeedback, { enabled: false, maxCorrections: 0 })
    await page.waitForFunction(() => !document.querySelector('[data-assistant-workspace] fieldset').disabled)
    await p.getByRole('checkbox', { name: 'Explain only' }).uncheck()
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true, 'Explain does not discard correction preference')
  }, { path: '/smart-brains/brain-37/models' })
  await run('lost turn response retries the same idempotency key and cancellation unlocks the editor', async ({ page, calls, cancelled }) => {
    await open(page); await send(page); await panel(page).getByRole('button', { name: 'Retry same turn' }).click()
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    const attempts = calls.filter(call => call.path.endsWith('/turns')); assert.equal(attempts.length, 2); assert.equal(attempts[0].key, attempts[1].key)
    assert.deepEqual(attempts[0].input.validationFeedback, attempts[1].input.validationFeedback, 'Explicit transport retry retains the original turn policy')
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
  await run('panel inputs survive desktop-mobile-desktop resize and dismissal without model turns', async ({ page, calls, finish }) => {
    await open(page); const query = 'Keep this unsent configuration request', p = panel(page)
    await enterPanelInputs(page, query); await assertPanelInputs(page, query)
    await resizeWorkspace(page, { width: 390, height: 844 }); await assertPanelInputs(page, query)
    await p.getByRole('button', { name: 'Close assistant' }).click(); await open(page); await assertPanelInputs(page, query)
    await resizeWorkspace(page, { width: 1440, height: 900 }); await assertPanelInputs(page, query)
    await p.getByRole('button', { name: 'Close assistant' }).click(); await open(page); await assertPanelInputs(page, query)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0, 'Resize and dismissal never trigger inference')
    assert.equal(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').length, 1, 'Resize does not recreate the scoped controller/session')
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    await p.getByRole('checkbox', { name: 'Explain only' }).uncheck()
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), false, 'Correction off was retained independently of Explain only')
    await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).check()
    assert.equal(await p.getByRole('combobox', { name: 'Max corrections' }).innerText(), '1', 'Correction limit remains one when re-enabled')
    await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).uncheck(); await p.getByRole('checkbox', { name: 'Preview only' }).uncheck()
    await p.getByRole('button', { name: 'Send', exact: true }).click(); finish(); await latestSaved(page)
    const turns = calls.filter(call => call.path.endsWith('/turns'))
    assert.equal(turns.length, 1)
    assert.deepEqual(turns[0].input, { query, previewOnly: false, sourceIds: ['source-1'], operationIds: ['catalogue'], validationFeedback: { enabled: false, maxCorrections: 0 } })
    await page.screenshot({ path: join(evidence, 'resize-retained-inputs-saved-desktop.png') })
    await resizeWorkspace(page, { width: 390, height: 844 }); await latestSaved(page)
    await page.screenshot({ path: join(evidence, 'resize-retained-inputs-saved-mobile.png') })
    await resizeWorkspace(page, { width: 1440, height: 900 }); await latestSaved(page)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1, 'Resizing a terminal saved turn never replays it')
  })
  await run('panel inputs reset on formal task and resource scope changes', async ({ page, calls }) => {
    await page.getByLabel('Manual value', { exact: true }).fill('Retain the manual editor buffer')
    await open(page); await enterPanelInputs(page, 'Old task query')
    const assertReset = async () => {
      await open(page); const p = panel(page)
      assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), '')
      assert.equal(await p.getByRole('checkbox', { name: 'Preview only' }).isChecked(), false)
      assert.equal(await p.getByRole('checkbox', { name: 'Explain only' }).isChecked(), false)
      assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true)
      assert.equal(await p.getByRole('combobox', { name: 'Max corrections' }).innerText(), '2')
      assert.equal(await p.locator('summary').filter({ hasText: 'Sources (' }).count(), 0)
      assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Retain the manual editor buffer')
    }
    await page.getByRole('link', { name: 'Select Models task', exact: true }).click(); await assertReset()
    await enterPanelInputs(page, 'Old resource query')
    await page.getByRole('link', { name: 'Select another Brain', exact: true }).click(); await assertReset()
    assert.deepEqual(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').map(call => [call.input.resourceId, call.input.task]), [['brain-37', 'BEHAVIOUR'], ['brain-37', 'MODELS'], ['brain-38', 'MODELS']])
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
  }, { path: '/smart-brains/brain-37/responses?scopeTest' })
  await run('Kit panel inputs survive dismissal and resize but reset for another Brain', async ({ page, calls }) => {
    const openKit = async () => { await page.getByRole('button', { name: 'Kit AI guide', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled) }
    await openKit(); const p = panel(page)
    await addResizeSource(page); await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Retained Kit question')
    await p.getByRole('button', { name: 'Close assistant' }).click(); await page.setViewportSize({ width: 390, height: 844 }); await openKit()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Retained Kit question')
    assert.equal(await p.getByRole('checkbox', { name: 'Resize API', exact: true }).isChecked(), true)
    assert.equal(await p.getByRole('checkbox', { name: 'GET /catalogue List catalogue' }).isChecked(), true)
    await p.getByRole('button', { name: 'Close assistant' }).click(); await page.setViewportSize({ width: 1440, height: 900 }); await openKit()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Retained Kit question')
    await p.getByRole('button', { name: 'Close assistant' }).click(); await page.getByRole('button', { name: 'Select another Kit Brain', exact: true }).click(); await openKit()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), '')
    assert.equal(await p.locator('summary').filter({ hasText: 'Sources (' }).count(), 0)
    assert.deepEqual(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').map(call => call.input.resourceId), ['brain-37', 'brain-38'])
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
  }, { path: '/kits/scope-fixture' })
  await run('desktop resize, dismissal retains input, narrow light layout fits and Enter submits', async ({ page, finish }) => {
    await open(page); const p = panel(page); await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Retained question')
    const before = await p.boundingBox(); await page.getByRole('separator', { name: 'Resize assistant' }).focus(); await page.keyboard.press('ArrowLeft'); const after = await p.boundingBox(); assert.ok(after.width > before.width)
    await p.getByRole('button', { name: 'Close assistant' }).click(); await page.getByRole('button', { name: 'Build with AI', exact: true }).click(); assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Retained question')
    await p.getByRole('textbox', { name: 'Message', exact: true }).press('Shift+Enter'); assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), 'Retained question\n')
    await p.getByRole('textbox', { name: 'Message', exact: true }).press('Enter'); finish(); await p.getByText('SAVED', { exact: true }).waitFor()
    await resizeWorkspace(page, { width: 320, height: 740 }); assert.ok((await p.boundingBox()).width <= 320)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await latestSaved(page)
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
    await latestSaved(page)
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
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isDisabled(), true)
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), false)
    await p.getByText('Open resource editor', { exact: true }).click()
    assert.equal(await p.getByRole('link', { name: 'Specialists', exact: true }).getAttribute('href'), '/smart-brains/brain-37/human-support/specialists')
    assert.equal(await p.getByRole('link', { name: 'Actions', exact: true }).getAttribute('href'), '/smart-brains/brain-37/capabilities/actions')
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Help me configure this Kit'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.previewOnly, true)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.validationFeedback, { enabled: false, maxCorrections: 0 })
    assert.equal(calls.filter(call => /\/apply$|publish|activate|disable/.test(call.path)).length, 0)
    await page.screenshot({ path: join(evidence, 'kit-guide-real-details.png') })
  }, { path: '/kits/kit-37' })
  console.log(`PASS ${count} browser groups. Fixture-only evidence: ${evidence}`)
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
