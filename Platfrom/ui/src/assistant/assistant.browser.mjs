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
import{AssistantWorkspace,useAssistantSource}from'/src/assistant/AssistantWorkspace.tsx';import{brainAssistantScope,brainPageAssistantScope}from'/src/assistant/scope.ts';
import{KitAssistantGuide}from'/src/assistant/KitAssistantGuide.tsx';
import{PluginLibraryPage}from'/src/pluginLibrary/PluginLibraryPage.tsx';
import{SmartBrainShell}from'/src/brain/SmartBrainShell.tsx';import{BrainModelsPage}from'/src/brain/BrainSettingsPage.tsx';
import{BrainCapabilitiesPage}from'/src/brain/BrainCapabilitiesPage.tsx';
import{BrainHumanSupportPage}from'/src/brain/BrainExperiencePages.tsx';import{BrainActionReviewsPage}from'/src/brain/BrainActionReviewsPage.tsx';
import{ExperienceKitOverview}from'/src/components/kits/ExperienceKitOverview.tsx';import'/src/pages/ExperienceKits.css';
import{CustomerWorkflowAssistance}from'/src/components/customer/CustomerWorkflowAssistance.tsx';
import{setPlatformAccessTokenProvider,platformRequest}from'/src/api/platformApi.ts';setPlatformAccessTokenProvider(async()=> 'assistant-fixture-token');
function Editor(){const[value,setValue]=useState('Saved manual value');const[other,setOther]=useState('Keep this manual value');const[real]=useState(()=>new URLSearchParams(window.location.search).has('real'));
 const location=useLocation();const{brainId}=useParams();const params=new URLSearchParams(location.search);const kind=params.get('kind');
 if(params.has('workflowForm'))return <Box sx={{p:2}}><CustomerWorkflowAssistance draft={window.__workflowDraft} canEdit={true}/></Box>;
 if(real)return <SmartBrainShell session={{role:'CUSTOMER_ADMIN',actorId:'actor-37',customerId:'customer-37',authenticationMode:'SUPABASE',displayName:'Customer',authenticated:true}} onSignOut={async()=>{}}>{location.pathname.includes('/action-reviews')?<BrainActionReviewsPage/>:location.pathname.includes('/human-support')?<BrainHumanSupportPage/>:location.pathname.includes('/capabilities')?<BrainCapabilitiesPage/>:<BrainModelsPage/>}</SmartBrainShell>;
 const defaultOpen=params.has('defaultOpen');
 const scope=kind==='ENGAGE_APP'?{resourceKind:kind,resourceId:'app-37',task:'ENGAGE'}:kind==='DOCUMENT_SOURCE'?{resourceKind:kind,resourceId:'brain-37|target-stage|release-37|source-37',task:'DOCUMENT_PREPARATION'}:(defaultOpen?brainPageAssistantScope:brainAssistantScope)(location.pathname,location.search,brainId??'brain-37');
 return <Box sx={{height:'100dvh',display:'flex'}}><AssistantWorkspace scope={scope} initiallyOpen={defaultOpen} visibilityKey={defaultOpen?brainId:undefined} readOnlyReason={params.has('active')?'Active Engage apps can only be previewed.':undefined}
 onSaved={async()=>{const saved=await platformRequest('/api/editor');setValue(saved.value)}}>
 <Box sx={{p:3,display:'flex',gap:2,flexDirection:'column'}}><h1>Selected editor</h1><TextField label="Manual value" value={value} onChange={e=>setValue(e.target.value)}/><TextField label="Unrelated value" value={other} onChange={e=>setOther(e.target.value)}/>{params.has('sourceShortcut')&&<SourceShortcut/>}
 {params.has('scopeTest')&&<><Link to="/smart-brains/brain-37/models?scopeTest">Select Models task</Link><Link to="/smart-brains/brain-38/models?scopeTest">Select another Brain</Link></>}
 {defaultOpen&&<><Link to="/smart-brains/brain-37/models?defaultOpen">Select Models task</Link><Link to="/smart-brains/brain-37/updates?defaultOpen">Select Updates guidance</Link><Link to="/smart-brains/brain-38/models?defaultOpen">Select another Brain</Link></>}
 {params.has('contextTest')&&[['models','Models context'],['knowledge/search','Search context'],['responses','Behaviour context'],['responses/instructions','Instructions context'],['knowledge/documents','Documents context'],['knowledge/connections/source-1','Live data context'],['knowledge/storage','Storage context'],['capabilities/action%3Aread','Selected action context']].map(([path,label])=><Link key={path} to={'/smart-brains/brain-37/'+path+'?contextTest'}>{label}</Link>)}</Box>
 </AssistantWorkspace></Box>}
function SourceShortcut(){const request=useAssistantSource();return <button onClick={()=>request({notice:'Selected authorized document sample',prepare:true,load:id=>platformRequest('/api/configuration-assistant/sessions/'+id+'/sources',{method:'POST',body:JSON.stringify({title:'Document sample',mediaType:'text/plain',content:'Authorized sample for preparation.'})})})}>Optimize selected sample</button>}
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
  const sessions = new Map()
  let session, currentPlugin = plugin(), complete = false, cancelled = false, loseResponse = options.loseResponse, editorValue = 'AI saved value', sequence = 0, reloadError = options.reloadError, model = 'saved-model'
  let loseDirective = options.loseDirective, loseControl = options.loseControl
  let autonomousEnabled = options.autonomousEnabled ?? true, autonomousUnavailableReason = options.autonomousUnavailableReason ?? ''
  let heldPoll, releasePoll, didHoldPoll = false
  let heldAdmission, releaseAdmission, didHoldAdmission = false
  let heldReceipts, releaseReceipts, didHoldReceipts = false, receiptFailures = options.receiptFailures ?? 1
  const directives = new Set()
  let workflowDraft = options.workflowDraft ? structuredClone(options.workflowDraft) : undefined
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(dark => localStorage.setItem('loomai-customer-console-color-scheme', dark ? 'dark' : 'light'), options.dark !== false)
  if (workflowDraft) await page.addInitScript(draft => { window.__workflowDraft = draft }, workflowDraft)
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const req = route.request(), path = new URL(req.url()).pathname, method = req.method(), input = req.postDataJSON()
    calls.push({ path, search: new URL(req.url()).search, method, input, key: req.headers()['idempotency-key'], auth: req.headers().authorization })
    const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    const requestedSession = path.match(/\/sessions\/(session-\d+)/)?.[1]
    if (requestedSession) session = sessions.get(requestedSession)
    if (path === '/api/platform/auth/session') return send({ actorId: 'actor-37', customerId: 'customer-37', role: 'CUSTOMER_ADMIN', authenticationMode: 'SUPABASE', authenticated: true })
    if (workflowDraft && path === '/api/deployments/brain-37/workflow-actions') return send({ deploymentId: 'brain-37', readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'] })
    if (workflowDraft && path === '/api/deployments/brain-37/draft') return send(workflowDraft)
    if (workflowDraft && path === '/api/deployment-drafts/draft-same' && method === 'PUT') { workflowDraft = { ...workflowDraft, ...input }; return send(workflowDraft) }
    if (path === '/api/configuration-assistant/status') return send({ enabled: !options.unavailable, ready: !options.unavailable, engine: 'CONFIGURED_BRAIN', documentFetchEnabled: Boolean(options.url), unavailableReason: options.unavailable ? 'Authoring provider is not configured.' : '', ...(options.missingAutonomousAvailability ? {} : { autonomousEnabled, autonomousUnavailableReason }), validationFeedback: options.validationFeedback === null ? undefined : options.validationFeedback ?? { defaultEnabled: true, maxCorrections: 2 } })
    if (path === '/api/editor') return reloadError ? send({ message: 'Saved editor could not be read.' }, 503) : send({ value: editorValue })
    if (path === '/api/customer-self-service/deployments') return send([{ deploymentId: 'brain-37', name: 'Customer knowledge', status: 'DRAFT' }])
    if (path === '/api/customer-experience-kits/kit-37') return send({ id: 'kit-37', name: 'Customer experience kit', kitCode: 'ai-customer-experience', deploymentId: 'brain-37', offerId: 'offer-37', deliveryMode: 'standalone', workspace: null, humanSupportEnabled: false, updatedAt: '2026-11-10T12:00:00Z' })
    if (path === '/api/customer-self-service/catalogue/offer-37') return send({ targets: [] })
    if (path.endsWith('/document-storage-bindings')) return send([])
    if (path.endsWith('/target-profiles')) return send([{ id: 'target-stage', name: 'Customer staging', environmentName: 'staging', active: true }])
    if (path.endsWith('/releases') || path.endsWith('/versions')) return send([])
    if (path === '/api/deployments/brain-37/marketplace-installs') return send([])
    if (path === '/api/deployments/brain-37/workspace') return send({ deployment: { id: 'brain-37', name: 'Customer knowledge', behaviorType: 'CONVERSATIONAL', binding: { customerId: 'customer-37' } }, access: { canEdit: true }, template: {} })
    if (path === '/api/deployments/brain-37/draft') return send({ id: 'draft-same', deploymentId: 'brain-37', revisionNumber: 1, entityConfig: {}, actionsConfig: options.aiWorkspace ? { actions: complete && session?.turns.at(-1)?.status === 'SAVED' ? options.aiWorkspace : [] } : undefined, providerConfig: { llmProvider: 'openai', embeddingProvider: 'openai', openaiModel: model }, behaviorConfig: { type: 'CONVERSATIONAL', executionExtensions: [], specialistAssistance: { enabled: false, areas: [], resolutions: [], limits: { maxDecisions: 4, maxWorkerInvocations: 2, maxParallelWorkers: 2, deadlineSeconds: 75 } } } })
    if (path.endsWith('/secret-usage')) return send({ secrets: [] })
    if (path.endsWith('/provider-connectivity')) return send({ managedVectorTargets: [], probes: [], effectiveSecretResolutions: [] })
    if (path === '/api/customer/plugin-library') return send({ customerId: 'customer-37', canManage: true, plugins: [currentPlugin] })
    if (path === '/api/customer/plugin-library/plugin-37') return send(currentPlugin)
    if (path === '/api/customer/plugin-library/plugin-37/versions/1.0.0') return send({ version: '1.0.0', manifest: currentPlugin.manifest })
    if (path === '/api/configuration-assistant/sessions' && method === 'POST') {
      session = { ...input, id: `session-${++sequence}`, displayName: 'Customer knowledge', status: 'IDLE', editable: !options.active, readOnlyReason: options.active ? 'Active app preview only.' : '', activeTurnId: null, expiresAt: '2026-11-11T00:00:00Z', sources: [], turns: [] }
      session.engine = options.legacy && sequence === 1 ? 'LEGACY' : 'CONFIGURED_BRAIN'
      if (session.engine === 'LEGACY') Object.assign(session, { editable: false, readOnlyReason: 'Earlier assistant history is read-only. Start a new Build with AI session.', turns: [{ id: 'old-turn', query: 'Earlier request', status: 'SAVED', message: 'Earlier preserved answer', receipt: { published: false }, attempts: [] }] })
      sessions.set(session.id, session)
      return send(session)
    }
    if (path.match(/\/sessions\/session-\d+$/)) {
      if (method === 'DELETE') { sessions.delete(session.id); session = undefined; return route.fulfill({ status: 204 }) }
      if (complete && session?.activeTurnId) {
        const turn = session.turns.at(-1)
        turn.status = options.results?.[session.turns.indexOf(turn)] ?? options.result ?? (turn.command && ['KIT', 'ACTION_REVIEWS'].includes(turn.editorContext.section) ? 'EXPLAIN' : options.active || turn.preview ? 'PREVIEW' : 'SAVED'); turn.message = 'Fixture response: requested configuration processed.'
        turn.diff = options.diff ?? [{ path: '/promptConfig/tone', before: 'manual', after: 'concise' }]; turn.findings = structuredClone(options.findings ?? { uncertainty: ['Pagination is not documented.'], sourceRefs: [{ sourceId: 'source-1', sourcePointer: '/paths/~1catalogue/get' }] })
        turn.questions = [{ id: 'q1', question: 'Which account owns these records?', field: '/accountId' }]
        turn.receipt = turn.status === 'SAVED' ? { revision: 2, published: false } : null
        turn.attempts = structuredClone(options.finalAttempts ?? [{ number: 1, status: 'CHECKED', completedAt: '2026-11-10T12:01:00Z' }])
        turn.feedbackStopReason = options.feedbackStopReason
        turn.runtime = { requestId: 'runtime-request-37', modelCalls: 1, accountingReceipt: { recorded: true }, usage: { tokens: 123 } }
        if (options.workflow) {
          turn.workflow = structuredClone(options.workflow); turn.operations = structuredClone(options.operations ?? []);
          turn.attempts = []; turn.questions = []; turn.receipt = null;
        }
        session.status = 'IDLE'; session.activeTurnId = null
        if (session.goal) { turn.questions = []; Object.assign(session.goal, { state: 'COMPLETE', canPause: false, canResume: false, canCancel: false, progress: { summary: 'Requested draft configuration prepared.', remaining: [], coverage: { completed: 5, total: 5 } } }) }
        model = 'ai-model'
        currentPlugin = { ...currentPlugin, manifest: { ...currentPlugin.manifest, displayName: 'AI private source' }, displayName: 'AI private source', revision: 2 }
      }
      if (session?.goal && options.holdPoll && !didHoldPoll) {
        didHoldPoll = true
        const snapshot = structuredClone(session)
        heldPoll?.()
        await new Promise(resolve => { releasePoll = resolve })
        return send(snapshot)
      }
      return session ? send(session) : send({ message: 'Session expired.' }, 410)
    }
    if (path.endsWith('/operations') && path.includes('/goals/')) {
      const parameters = new URL(req.url()).searchParams, offset = Number(parameters.get('offset')), limit = Number(parameters.get('limit'))
      const goalId = decodeURIComponent(path.split('/').at(-2))
      if (goalId !== session.goal.id) return send({ message: 'Goal access denied.' }, 403)
      if (options.holdReceiptOffset === offset && !didHoldReceipts) {
        didHoldReceipts = true; heldReceipts?.(); await new Promise(resolve => { releaseReceipts = resolve })
      }
      if (options.receiptErrorOffset === offset && receiptFailures > 0) { receiptFailures--; return send({ message: 'Operation receipts are temporarily unavailable.' }, 503) }
      const ledger = options.receiptOperations ?? session.goal.operations
      const operations = structuredClone(ledger.slice(offset, offset + limit))
      if (options.duplicateReceiptOffset === offset && operations.length > 1) operations[1] = structuredClone(operations[0])
      const hasMore = offset + operations.length < ledger.length
      return send({ goalId: options.foreignReceiptOffset === offset ? 'foreign-goal' : goalId, operations, offset, limit, total: ledger.length, hasMore, ...(hasMore ? { nextOffset: offset + limit } : {}) })
    }
    if (path.endsWith('/control')) {
      if (options.controlError) return send({ message: 'Goal control is temporarily unavailable.' }, 503)
      const goal = session.goal
      if (input.command === 'PAUSE') Object.assign(goal, { state: 'PAUSED', reasonCode: 'USER_PAUSED', canPause: false, canResume: true })
      if (input.command === 'RESUME') Object.assign(goal, { state: 'RUNNING', reasonCode: null, canPause: true, canResume: false })
      if (input.command === 'CANCEL') { Object.assign(goal, { state: 'CANCELLED', canPause: false, canResume: false, canCancel: false }); session.activeTurnId = null; session.status = 'IDLE'; session.turns.at(-1).status = 'CANCELLED' }
      if (loseControl) { loseControl = false; return route.abort('failed') }
      return send(session)
    }
    if (path.endsWith('/directives')) {
      if (options.directiveContextLimit) return send({ message: 'AUTHORING_DIRECTIVE_CONTEXT_LIMIT' }, 409)
      const key = req.headers()['idempotency-key']
      if (!directives.has(key)) {
        directives.add(key); session.goal.requestRevision++
        Object.assign(session.goal, { state: 'RUNNING', canPause: true, canResume: false, latestRequestUpdate: { query: input.query, requestRevision: session.goal.requestRevision, createdAt: '2026-11-24T20:00:00Z' } })
      }
      if (loseDirective) { loseDirective = false; return route.abort('failed') }
      return send(session)
    }
    if (path.endsWith('/reusable-sources')) return send([
      { sessionId: options.existingReuse ? session.id : 'old-session', sourceId: options.existingReuse ? 'source-1' : 'old-source', title: 'Previously imported API', task: 'ACTIONS', mediaType: 'application/json', resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37' },
      { sessionId: 'other-session', sourceId: 'other-source', title: 'Other Brain API', task: 'ACTIONS', mediaType: 'application/json', resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-38' },
      { sessionId: 'plugin-session', sourceId: 'plugin-source', title: 'Plugin API', task: 'DATA_PLUGIN', mediaType: 'application/json', resourceKind: 'PRIVATE_DATA_PLUGIN', resourceId: 'brain-37' },
    ])
    if (path.endsWith('/sources/reuse')) {
      if (options.existingReuse) return send(session)
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
      if (options.holdAdmission && !didHoldAdmission) {
        didHoldAdmission = true; heldAdmission?.()
        await new Promise(resolve => { releaseAdmission = resolve })
      }
      const existing = session.turns.find(turn => turn.key === req.headers()['idempotency-key'])
      if (!existing) {
        session.turns.push({ id: `turn-${session.turns.length + 1}`, key: req.headers()['idempotency-key'], query: input.command ? 'Registered server intent, not transcript copy.' : input.query, hint: input.command ? input.query : undefined, command: input.command, editorContext: input.editorContext, status: 'RUNNING', message: '', preview: input.previewOnly, createdAt: '2026-11-10T12:00:00Z', usage: { tokens: 123 }, validationFeedback: input.validationFeedback, attempts: structuredClone(options.runningAttempts ?? [{ number: 1, status: 'RUNNING', startedAt: '2026-11-10T12:00:00Z' }]), feedbackStopReason: options.runningStopReason })
        if (options.turnProviderDiagnostics) session.turns.at(-1).providerDiagnostics = structuredClone(options.turnProviderDiagnostics)
        session.activeTurnId = session.turns.at(-1).id; session.status = 'RUNNING'
        if (options.goal && input.executionMode === 'AUTONOMOUS') session.goal = {
          id: 'goal-37', turnId: session.activeTurnId, executionMode: 'AUTONOMOUS', originalRequest: input.query || 'Prepare all five selected API operations',
          resourceKind: session.resourceKind, resourceId: session.resourceId, task: session.task, profileId: 'api', releaseId: 'release-pinned', bundleHash: 'bundle-pinned',
          sourceIds: input.sourceIds, operationIds: input.operationIds,
          state: 'RUNNING', requestRevision: 1, segment: 4, canPause: true, canResume: false, canCancel: true,
          progress: { summary: 'Two operations covered. Three remain.', coverage: { completed: 2, total: 5 }, remaining: ['Product details', 'Enquiry WRITE', 'Enquiry receipt READ'] },
          usage: { modelCalls: 22, corrections: 6, writes: 4 }, operations: [
            { operationId: 'goal-37:step:4', action: 'authoring_update_draft', status: 'EXECUTED', published: false, receipt: { draftId: 'draft-same', published: false } },
            { operationId: 'goal-37:step:5', action: 'authoring_update_draft', status: 'DISPATCHING', published: false },
          ], ...structuredClone(options.goal === true ? {} : options.goal),
        }
        if (session.goal?.terminal) {
          session.turns.at(-1).status = session.goal.state
          session.activeTurnId = null; session.status = 'IDLE'
        }
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
    await work({ page, calls, finish: () => { complete = true }, session: () => session, cancelled: () => cancelled, allowReload: () => { reloadError = false },
      waitForHeldPoll: () => new Promise(resolve => { if (didHoldPoll) resolve(); else heldPoll = resolve }), releasePoll: () => releasePoll?.(),
      waitForAdmission: () => new Promise(resolve => { if (didHoldAdmission) resolve(); else heldAdmission = resolve }), releaseAdmission: () => releaseAdmission?.(),
      waitForReceipts: () => new Promise(resolve => { if (didHoldReceipts) resolve(); else heldReceipts = resolve }), releaseReceipts: () => releaseReceipts?.(),
      setAvailability: (enabled, reason = '') => { autonomousEnabled = enabled; autonomousUnavailableReason = reason } })
    assert.deepEqual(errors, [], 'No browser runtime errors'); assert.deepEqual(unexpected, [], 'No unrelated requests')
    assert.ok(calls.every(call => call.auth === 'Bearer assistant-fixture-token'), 'All Console requests reuse Platform bearer authentication')
    count++; console.log(`PASS ${name}`)
  } catch (error) { await page.screenshot({ path: join(evidence, 'failure.png'), fullPage: true }); console.error(name, errors, unexpected, await page.locator('body').innerText()); throw error }
  finally { await page.close() }
}
async function turnOptions(page, visible = true) { const toggle = panel(page).getByRole('button', { name: 'Turn options', exact: true }); if (!visible && !await toggle.count()) return; if (await toggle.getAttribute('aria-expanded') !== String(visible)) await toggle.click() }
async function open(page, quickEditOptions = true) {
  const button = page.getByRole('button', { name: 'Build with AI', exact: true })
  if (await button.getAttribute('aria-expanded') !== 'true') await button.click()
  await panel(page).getByRole('textbox', { name: 'Message', exact: true }).waitFor()
  await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
  if (quickEditOptions && await panel(page).getByRole('button', { name: 'Quick edit', exact: true }).count())
    await panel(page).getByRole('button', { name: 'Quick edit', exact: true }).click()
  await turnOptions(page, quickEditOptions)
}
async function send(page, text = 'Use a concise response style') { await panel(page).getByRole('textbox', { name: 'Message', exact: true }).fill(text); await panel(page).getByRole('button', { name: 'Send', exact: true }).click() }
async function review(page) { await panel(page).getByRole('region', { name: 'Selected documents', exact: true }).getByRole('button').last().click(); const dialog = page.getByRole('dialog', { name: /^Review / }); await dialog.getByText('Redacted imported documentation.', { exact: false }).waitFor(); assert.equal(await page.evaluate(() => window.sourceExecuted), undefined); await dialog.getByRole('button', { name: 'Close source review' }).click() }
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
  await turnOptions(page, false)
  const status = panel(page).getByRole('article', { name: 'Assistant', exact: true }).last().locator('[data-assistant-turn-status="SAVED"]')
  await status.waitFor(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled); await status.scrollIntoViewIfNeeded()
  assert.equal(await status.evaluate(node => {
    const rect = node.getBoundingClientRect(), log = node.closest('[role="log"]').getBoundingClientRect()
    return rect.top >= Math.max(0, log.top) - 1 && rect.bottom <= Math.min(innerHeight, log.bottom) + 1
  }), true, 'Latest authoritative SAVED terminal is inside the visible conversation viewport')
}
async function importDocumentation(page, mode = 'paste', title = 'API reference') {
  await panel(page).getByRole('button', { name: 'Add source', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Add documentation' })
  if (mode === 'file') await dialog.locator('input[type=file]').setInputFiles({ name: 'API reference.json', mimeType: 'application/json', buffer: Buffer.from('{"openapi":"3.1.0"}') })
  else if (mode === 'url') { await dialog.getByRole('tab', { name: 'URL', exact: true }).click(); await dialog.getByLabel('Source title').fill(title); await dialog.getByLabel('Public documentation URL').fill('https://docs.example.test/openapi.json') }
  else { await dialog.getByRole('tab', { name: 'Paste', exact: true }).click(); await dialog.getByLabel('Source title').fill(title); await dialog.getByLabel('Documentation', { exact: true }).fill('openapi: 3.1.0') }
  await dialog.getByRole('button', { name: 'Share source', exact: true }).click(); await dialog.waitFor({ state: 'hidden' })
  assert.equal(await page.getByRole('dialog', { name: /^Review / }).count(), 0, 'Import never forces the review dialog')
}
try {
  browser = await chromium.launch({ headless: true })
  for (const path of ['', '/models', '/responses', '/knowledge/search', '/knowledge/documents', '/human-support/specialists', '/capabilities', '/capabilities?view=connection']) {
    await run(`available assistant defaults to a native autonomous request on ${path || 'Brain setup'}`, async ({ page, calls }) => {
      await open(page, false)
      assert.equal(await panel(page).getByRole('button', { name: 'Autonomous', exact: true }).getAttribute('aria-pressed'), 'true')
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0, 'Opening never invokes a model')
      await send(page, 'Prepare the requested draft configuration')
      await panel(page).getByRole('region', { name: 'Autonomous goal', exact: true }).waitFor()
      const turns = calls.filter(call => call.path.endsWith('/turns'))
      assert.equal(turns.length, 1)
      assert.equal(turns[0].input.executionMode, 'AUTONOMOUS')
      assert.equal('validationFeedback' in turns[0].input, false)
    }, { goal: true, path: '/smart-brains/brain-37' + path })
  }
  const startGoal = async page => {
    await open(page, false)
    await panel(page).getByRole('button', { name: 'Autonomous', exact: true }).click()
    await send(page, 'Prepare all five API operations')
    await panel(page).getByRole('region', { name: 'Autonomous goal', exact: true }).waitFor()
  }
  const goalLayout = async page => {
    await page.waitForFunction(() => {
      const panel = document.getElementById('configuration-assistant-panel'), box = panel?.getBoundingClientRect()
      return box && box.left >= -1 && box.right <= innerWidth + 1
    })
    const layout = await panel(page).evaluate(node => {
      const goal = node.querySelector('[aria-label="Autonomous goal"]').getBoundingClientRect()
      const composer = node.querySelector('textarea').getBoundingClientRect()
      const log = node.querySelector('[role="log"]').getBoundingClientRect()
      const close = node.querySelector('[aria-label="Close assistant"]').getBoundingClientRect()
      return { fits: document.documentElement.scrollWidth <= innerWidth && node.scrollWidth <= node.clientWidth + 1, visible: close.top >= 0 && close.bottom <= innerHeight && composer.top >= goal.bottom && composer.bottom < innerHeight, logHeight: log.height }
    })
    assert.equal(layout.fits, true, 'No horizontal overflow')
    assert.equal(layout.visible, true, 'Goal, close control and composer do not overlap or leave viewport')
    assert.ok(layout.logHeight > 30, 'Conversation remains nonblank and visible')
    const png = await page.screenshot()
    assert.ok(png.length > 5000, 'Rendered screenshot is not blank')
  }
  const operationLedger = (count = 125) => Array.from({ length: count }, (_, index) => ({ operationId: `goal-37:step:${index + 1}`, action: 'authoring_update_draft', status: 'EXECUTED', published: false, receipt: { draftId: 'draft-same', published: false, sequence: index + 1 } }))
  const receiptRegion = page => panel(page).getByRole('region', { name: 'Goal operation receipts', exact: true })
  const openActivity = async page => { await panel(page).getByText('Activity · Segment 4', { exact: true }).click() }
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    const ledger = operationLedger()
    await run(`activity receipt paging reaches later saves with dedupe errors and bounded layout at ${viewport.width}px`, async ({ page, calls, session }) => {
      await startGoal(page)
      await panel(page).getByRole('region', { name: 'Saved operations', exact: true }).getByText('Saved operation receipts: 125', { exact: true }).waitFor()
      assert.equal(calls.filter(call => call.path.endsWith('/operations')).length, 0, 'Collapsed activity makes no journal request')
      const before = await panel(page).boundingBox()
      await openActivity(page); const receipts = receiptRegion(page)
      await receipts.getByText('Receipts 1-20 of 125', { exact: true }).waitFor()
      assert.equal(await receipts.locator('[data-operation-id]').count(), 20)
      await receipts.getByRole('button', { name: 'Next receipts' }).click()
      await receipts.getByText('Operation receipts are temporarily unavailable.', { exact: true }).waitFor()
      assert.equal(await receipts.locator('[data-operation-id]').count(), 20, 'A failed page retains the last confirmed page')
      await receipts.getByRole('button', { name: 'Retry receipts' }).click()
      await receipts.getByText('Receipts 21-40 of 125', { exact: true }).waitFor()
      const ids = await receipts.locator('[data-operation-id]').evaluateAll(nodes => nodes.map(node => node.dataset.operationId))
      assert.equal(ids.length, 19); assert.equal(new Set(ids).size, ids.length, 'Duplicate receipt identity is not rendered twice')
      for (const offset of [40, 60, 80, 100, 120]) {
        await receipts.getByRole('button', { name: 'Next receipts' }).click()
        await receipts.getByText(`Receipts ${offset + 1}-${Math.min(offset + 20, 125)} of 125`, { exact: true }).waitFor()
        assert.ok(await receipts.locator('[data-operation-id]').count() <= 20, 'Only one receipt page is mounted')
      }
      await receipts.locator('[data-operation-id="goal-37:step:125"]').waitFor()
      assert.equal(await receipts.getByRole('button', { name: 'Next receipts' }).isDisabled(), true)
      const readsBefore = calls.filter(call => call.path === '/api/editor').length
      ledger.push(...operationLedger(130).slice(125)); session().goal.operationCount = 130
      await receipts.getByText('Receipts 121-130 of 130', { exact: true }).waitFor()
      await page.waitForFunction(() => [...document.querySelectorAll('input')].some(input => input.value === 'AI saved value'))
      assert.ok(calls.filter(call => call.path === '/api/editor').length > readsBefore, 'A confirmed save after the first 100 reloads the editor')
      assert.equal(calls.filter(call => call.path.endsWith('/operations') && call.search.includes('offset=120&')).length, 2, 'New receipts refresh the tail once without a read loop')
      await receipts.getByRole('button', { name: 'Previous receipts' }).click()
      await receipts.getByText('Receipts 101-120 of 130', { exact: true }).waitFor()
      assert.equal(await receipts.locator('[data-operation-id="goal-37:step:125"]').count(), 0)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      assert.ok(calls.filter(call => call.path.endsWith('/operations')).every(call => call.method === 'GET' && call.path.includes('/sessions/session-1/goals/goal-37/')))
      await goalLayout(page)
      assert.equal((await panel(page).boundingBox()).height, before.height, 'The primary panel does not grow with the journal')
      await page.screenshot({ path: join(evidence, `activity-receipts-${viewport.width}.png`) })
    }, { goal: { operationCount: 125, operations: ledger.slice(0, 100) }, receiptOperations: ledger, receiptErrorOffset: 20, duplicateReceiptOffset: 20, viewport })
  }
  {
    const ledger = operationLedger()
    await run('activity receipt paging stays pinned after task and Brain navigation', async ({ page, calls }) => {
      await startGoal(page)
      await panel(page).getByRole('button', { name: 'Close assistant' }).click()
      await page.getByRole('link', { name: 'Select Models task', exact: true }).click(); await open(page, false)
      await panel(page).getByRole('button', { name: 'Close assistant' }).click()
      await page.getByRole('link', { name: 'Select another Brain', exact: true }).click(); await open(page, false)
      await panel(page).locator('[data-goal-target]').filter({ hasText: 'brain-37' }).waitFor()
      await openActivity(page)
      await receiptRegion(page).getByText('Receipts 1-20 of 125', { exact: true }).waitFor()
      await receiptRegion(page).getByRole('button', { name: 'Next receipts' }).click()
      await receiptRegion(page).getByText('Receipts 21-40 of 125', { exact: true }).waitFor()
      assert.ok(calls.filter(call => call.path.endsWith('/operations')).every(call => call.path === '/api/configuration-assistant/sessions/session-1/goals/goal-37/operations'))
      assert.equal(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').length, 1)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    }, { goal: { operationCount: 125, operations: ledger.slice(0, 100) }, receiptOperations: ledger, path: '/smart-brains/brain-37/responses?scopeTest' })
    await run('activity foreign receipt projection is rejected without changing the original goal', async ({ page, calls }) => {
      await startGoal(page); await openActivity(page)
      await receiptRegion(page).getByText('The receipt page could not be matched to this goal. Refresh the receipts to try again.', { exact: true }).waitFor()
      await panel(page).locator('[data-goal-target]').filter({ hasText: 'brain-37' }).waitFor()
      assert.equal(await panel(page).getByText('foreign-goal', { exact: false }).count(), 0)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
    }, { goal: { operationCount: 125, operations: ledger.slice(0, 100) }, receiptOperations: ledger, foreignReceiptOffset: 0 })
    await run('activity pending receipt read leaves the real manual editor and save footer enabled', async ({ page, calls, waitForReceipts, releaseReceipts }) => {
      await startGoal(page); await openActivity(page)
      await receiptRegion(page).getByText('Receipts 1-20 of 125', { exact: true }).waitFor()
      await receiptRegion(page).getByRole('button', { name: 'Next receipts' }).click(); await waitForReceipts()
      const field = page.getByLabel('OpenAI LLM model', { exact: true })
      assert.equal(await field.isEnabled(), true); await field.fill('manual-during-receipt-read')
      assert.equal(await page.locator('footer button').filter({ hasText: 'Save draft' }).isEnabled(), true)
      releaseReceipts()
      await receiptRegion(page).getByText('Receipts 21-40 of 125', { exact: true }).waitFor()
      assert.equal(await field.inputValue(), 'manual-during-receipt-read', 'Reading a receipt page does not refresh or reset the manual buffer')
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    }, { goal: { operationCount: 125, operations: ledger.slice(0, 100) }, receiptOperations: ledger, holdReceiptOffset: 20, path: '/smart-brains/brain-37/models?real&targetProfileId=target-stage' })
  }
  {
    const ledger = operationLedger().map(operation => ({ ...operation, receipt: { ...operation.receipt, protectedSource: 'SOURCE_ERASURE_UI_CANARY' } }))
    await run('activity source erasure forgets paged bodies without replaying saves or resetting manual input', async ({ page, calls, session }) => {
      await startGoal(page); await openActivity(page)
      await receiptRegion(page).getByText('Receipts 1-20 of 125', { exact: true }).waitFor()
      assert.equal(await page.evaluate(() => document.body.textContent.includes('SOURCE_ERASURE_UI_CANARY')), true)
      await page.getByLabel('Manual value', { exact: true }).fill('Manual value retained after source removal')
      const readsBefore = calls.filter(call => call.path === '/api/editor').length
      const retained = ledger.map(({ operationId, action, status, published }) => ({ operationId, action, status, published, protectedBodyErased: true }))
      ledger.splice(0, ledger.length, ...retained)
      Object.assign(session().goal, { state: 'CANCELLED', terminal: true, cancellationEpoch: 1, reasonCode: 'SOURCE_WITHDRAWN', originalRequest: null, latestRequestUpdate: undefined, sourceIds: [], operations: retained.slice(0, 100), canPause: false, canResume: false, canCancel: false })
      session().turns = []; session().sources = []; session().activeTurnId = null; session().status = 'IDLE'
      await panel(page).locator('[data-goal-state="CANCELLED"]').waitFor()
      assert.equal(await page.evaluate(() => document.body.textContent.includes('SOURCE_ERASURE_UI_CANARY')), false)
      assert.equal(calls.filter(call => call.path === '/api/editor').length, readsBefore, 'Changing receipt retention is not a new draft save')
      assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Manual value retained after source removal')
      await openActivity(page)
      await receiptRegion(page).getByText('Receipts 1-20 of 125', { exact: true }).waitFor()
      await receiptRegion(page).locator('[data-operation-id]').first().locator('summary').click()
      await receiptRegion(page).getByText('Receipt details removed.', { exact: true }).first().waitFor()
      assert.equal(await page.evaluate(() => document.body.textContent.includes('SOURCE_ERASURE_UI_CANARY')), false)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    }, { goal: { operationCount: 125, operations: ledger.slice(0, 100) }, receiptOperations: ledger })
  }
  const diagnostics = { observations: [
    { operation: 'CHAT_COMPLETION', model: 'configured-model', effectiveOutputCap: 8192, finishReason: 'length', providerRequestId: 'provider-request-safe', inputTokens: 120, outputTokens: 30, reasoningTokens: 12, contentLength: 0, contentEmpty: true, rawResponse: 'NEVER_RENDER_RAW', source: 'NEVER_RENDER_SOURCE', reasoningText: 'NEVER_RENDER_REASONING', headers: { authorization: 'NEVER_RENDER_AUTH' } },
    { operation: 'CHAT_COMPLETION', responseModel: 'response-model', finishReason: null, inputTokens: null, outputTokens: null, reasoningTokens: null },
  ], droppedObservations: 7, request: 'NEVER_RENDER_REQUEST' }
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await run(`activity provider diagnostics remain collapsed bounded metadata with no inference at ${viewport.width}px`, async ({ page, calls }) => {
      await startGoal(page); await openActivity(page)
      const summary = panel(page).getByText('Provider diagnostics (2 observations)', { exact: true })
      assert.equal(await summary.evaluate(node => node.parentElement.open), false)
      await summary.click()
      await panel(page).getByText('provider-request-safe', { exact: true }).waitFor()
      await panel(page).getByText('Earlier observations omitted: 7', { exact: true }).waitFor()
      assert.ok(await panel(page).getByText('Unavailable', { exact: true }).count() > 0)
      assert.equal((await panel(page).innerText()).includes('NEVER_RENDER'), false)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      assert.equal(calls.filter(call => /\/control$|\/directives$|\/operations$/.test(call.path)).length, 0)
      await goalLayout(page)
      await page.screenshot({ path: join(evidence, `activity-provider-diagnostics-${viewport.width}.png`) })
    }, { goal: { state: 'PAUSED', reasonCode: 'PROVIDER_UNAVAILABLE', canPause: false, canResume: true, providerDiagnostics: diagnostics }, viewport })
  }
  await run('activity provider metadata is inspectable in bounded turn history without unsafe fields', async ({ page, calls, finish }) => {
    await open(page, false); await send(page, 'Make a quick change'); finish()
    await panel(page).locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await panel(page).getByText('Advanced', { exact: true }).click()
    await panel(page).getByText('Provider diagnostics (2 observations)', { exact: true }).click()
    await panel(page).getByText('provider-request-safe', { exact: true }).waitFor()
    assert.equal((await panel(page).innerText()).includes('NEVER_RENDER'), false)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { turnProviderDiagnostics: diagnostics })
  await run('autonomous controls restore pinned goal across refresh close navigation and another Brain', async ({ page, calls, session }) => {
    await startGoal(page)
    const p = panel(page), goal = p.getByRole('region', { name: 'Autonomous goal' })
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.executionMode, 'AUTONOMOUS')
    assert.equal('validationFeedback' in calls.find(call => call.path.endsWith('/turns')).input, false)
    await goal.getByText('Preparing draft · 2 of 5 covered', { exact: true }).waitFor()
    await goal.getByText('1 executed draft operation · Not published', { exact: true }).waitFor()
    await goal.getByRole('region', { name: 'Remaining work' }).getByText('Enquiry WRITE', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
    await page.getByLabel('Manual value', { exact: true }).fill('Manual while autonomous')
    await goal.getByRole('button', { name: 'Pause goal' }).click()
    await p.locator('[data-goal-state="PAUSED"]').waitFor()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).isEnabled(), true)
    await p.getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('link', { name: 'Select Models task', exact: true }).click()
    await open(page, false)
    await p.locator('[data-goal-state="PAUSED"]').waitFor()
    await goal.locator('[data-goal-target]').filter({ hasText: 'brain-37 · AI responses' }).waitFor()
    await page.reload(); await open(page, false); await p.locator('[data-goal-state="PAUSED"]').waitFor()
    await p.getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('link', { name: 'Select another Brain', exact: true }).click(); await open(page, false)
    await goal.locator('[data-goal-target]').filter({ hasText: 'brain-37' }).waitFor()
    assert.equal(session().goal.id, 'goal-37')
    assert.equal(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').length, 1, 'No retargeted session or inference during navigation')
    await goal.getByRole('button', { name: 'Resume goal' }).click(); await p.locator('[data-goal-state="RUNNING"]').waitFor()
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Keep optional search filters')
    await p.getByRole('button', { name: 'Update goal', exact: true }).click()
    assert.equal(calls.filter(call => call.path.endsWith('/directives')).length, 1)
    assert.equal(session().goal.requestRevision, 2)
    await goal.getByRole('button', { name: 'Cancel goal' }).click(); await p.locator('[data-goal-state="CANCELLED"]').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    assert.equal(calls.filter(call => /\/publish|\/apply$|\/index|\/secrets|funding/.test(call.path)).length, 0)
    assert.deepEqual(calls.filter(call => call.path.endsWith('/control')).map(call => call.input.command), ['PAUSE', 'RESUME', 'CANCEL'])
    await goalLayout(page)
    await page.screenshot({ path: join(evidence, 'autonomous-pinned-cancelled-desktop.png') })
  }, { goal: true, path: '/smart-brains/brain-37/responses?scopeTest' })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }, { width: 320, height: 740 }]) {
    await run(`autonomous yielded progress beyond two corrections remains usable at ${viewport.width}px`, async ({ page, calls }) => {
      await startGoal(page)
      await panel(page).locator('[data-goal-state="YIELDED"]').waitFor()
      await panel(page).getByText('Continuing preparation · 2 of 5 covered', { exact: true }).waitFor()
      assert.equal(await panel(page).getByText(/Work stopped:/).count(), 0)
      await panel(page).getByText('Activity · Segment 4', { exact: true }).click()
      await panel(page).getByText('Cumulative: 22 model calls · 6 corrections', { exact: true }).waitFor()
      await goalLayout(page)
      await page.screenshot({ path: join(evidence, `autonomous-yielded-${viewport.width}.png`) })
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      await panel(page).getByRole('button', { name: 'Close assistant' }).click()
      assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
    }, { goal: { state: 'YIELDED', reasonCode: 'WORKFLOW_CORRECTION_LIMIT' }, viewport, dark: viewport.width !== 320 })
  }
  for (const reasonCode of ['BUDGET_EXHAUSTED', 'AUTHORITY_EXPIRED', 'PROVIDER_UNAVAILABLE']) {
    await run(`autonomous operational pause ${reasonCode} preserves receipts and explicit resume`, async ({ page, calls }) => {
      await startGoal(page); const p = panel(page)
      await p.locator('[data-goal-state="PAUSED"]').waitFor()
      await p.getByText(reasonCode, { exact: true }).waitFor()
      assert.equal(await p.getByText('Needs your input', { exact: true }).count(), 0)
      await p.getByRole('button', { name: 'Resume goal' }).click(); await p.locator('[data-goal-state="RUNNING"]').waitFor()
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    }, { goal: { state: 'PAUSED', reasonCode, canPause: false, canResume: true } })
  }
  await run('autonomous needs input answers same goal and completion retains review and Undo', async ({ page, calls, finish }) => {
    await startGoal(page); const p = panel(page)
    await p.getByText('Needs your input · 2 of 5 covered', { exact: true }).waitFor()
    assert.equal(await p.getByRole('button', { name: 'Resume goal' }).count(), 0)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Use the existing owned credential reference')
    await p.getByRole('button', { name: 'Answer goal', exact: true }).click()
    finish(); await p.locator('[data-goal-state="COMPLETE"]').waitFor()
    await p.getByText('Draft preparation complete · 5 of 5 covered', { exact: true }).waitFor()
    assert.equal(await p.getByRole('button', { name: 'Pause goal' }).count(), 0)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    await p.getByRole('button', { name: 'Undo saved changes', exact: true }).click()
    await p.locator('[data-assistant-turn-status="UNDONE"]').waitFor()
    await p.getByText('Draft changes undone', { exact: true }).waitFor()
    assert.equal(await p.getByText('Draft preparation complete · 5 of 5 covered', { exact: true }).count(), 0, 'Undo cannot leave a current completion claim')
    assert.equal(calls.filter(call => call.path.endsWith('/undo')).length, 1)
    await goalLayout(page)
    await page.screenshot({ path: join(evidence, 'autonomous-completed-390.png') })
  }, { goal: { state: 'WAITING_FOR_INPUT', canPause: false, canResume: false }, viewport: { width: 390, height: 844 } })
  await run('autonomous lost directive response retries same revision key without a second user turn', async ({ page, calls, session }) => {
    await startGoal(page); const p = panel(page)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Keep original confirmation policy')
    await p.getByRole('button', { name: 'Update goal', exact: true }).click()
    await p.getByRole('button', { name: 'Retry same turn', exact: true }).click()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    const sent = calls.filter(call => call.path.endsWith('/directives'))
    assert.equal(sent.length, 2); assert.equal(sent[0].key, sent[1].key); assert.equal(session().goal.requestRevision, 2)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { goal: true, loseDirective: true })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await run(`autonomous latest request update survives refresh without another transcript turn at ${viewport.width}px`, async ({ page, calls, session }) => {
      await startGoal(page); const p = panel(page), update = 'Keep the optional category filter. <script>window.updateExecuted=true</script>'
      assert.equal(await p.getByRole('region', { name: 'Latest goal update', exact: true }).count(), 0)
      await p.getByRole('textbox', { name: 'Message', exact: true }).fill(update)
      await p.getByRole('button', { name: 'Update goal', exact: true }).click()
      await p.getByRole('region', { name: 'Latest goal update', exact: true }).getByText(update, { exact: true }).waitFor()
      assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), '')
      await page.reload(); await open(page, false)
      const latest = p.getByRole('region', { name: 'Latest goal update', exact: true })
      await latest.getByText('Your latest update · Revision 2', { exact: true }).waitFor()
      await latest.getByText(update, { exact: true }).waitFor()
      await p.getByRole('region', { name: 'Autonomous goal', exact: true }).getByText('Prepare all five API operations', { exact: true }).waitFor()
      await p.locator('[data-goal-target]').filter({ hasText: 'brain-37' }).waitFor()
      assert.equal(await page.evaluate(() => window.updateExecuted), undefined)
      assert.equal(session().turns.length, 1); assert.equal(await p.getByRole('article', { name: 'You', exact: true }).count(), 1)
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      assert.equal(calls.filter(call => call.path.endsWith('/directives')).length, 1)
      await goalLayout(page)
      await page.screenshot({ path: join(evidence, `goal-latest-update-${viewport.width}.png`) })
    }, { goal: true, viewport })
  }
  await run('autonomous control failure is visible and leaves manual editor usable', async ({ page }) => {
    await startGoal(page); await panel(page).getByRole('button', { name: 'Pause goal' }).click()
    await panel(page).getByText('Goal control is temporarily unavailable.', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
  }, { goal: true, controlError: true })
  await run('autonomous directive context limit retains the unaccepted text and previous accepted revision', async ({ page, calls, session }) => {
    await startGoal(page); const p = panel(page), rejected = 'A new update that exceeds the remaining request context.'
    await p.getByRole('region', { name: 'Latest goal update', exact: true }).getByText('Keep the previously accepted filters.', { exact: true }).waitFor()
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill(rejected)
    await p.getByRole('button', { name: 'Update goal', exact: true }).click()
    await p.getByText('AUTHORING_DIRECTIVE_CONTEXT_LIMIT', { exact: true }).waitFor()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), rejected)
    await p.getByRole('region', { name: 'Latest goal update', exact: true }).getByText('Your latest update · Revision 33', { exact: true }).waitFor()
    assert.equal(session().goal.requestRevision, 33)
    assert.equal(session().goal.latestRequestUpdate.query, 'Keep the previously accepted filters.')
    assert.equal(session().turns.length, 1)
    assert.equal(await p.getByRole('button', { name: 'Retry same turn', exact: true }).count(), 0)
    await p.getByRole('button', { name: 'Assistant session options' }).click()
    await p.getByRole('button', { name: 'Refresh assistant session' }).click()
    await p.locator('[data-goal-state="RUNNING"]').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/directives')).length, 1, 'A context limit is not a transport or semantic retry')
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { directiveContextLimit: true, goal: { requestRevision: 33, latestRequestUpdate: { query: 'Keep the previously accepted filters.', requestRevision: 33, createdAt: '2026-11-24T20:00:00Z' } } })
  await run('autonomous stale running poll cannot overwrite a newer explicit pause', async ({ page, waitForHeldPoll, releasePoll }) => {
    await startGoal(page); await waitForHeldPoll()
    await panel(page).getByRole('button', { name: 'Pause goal' }).click()
    await panel(page).locator('[data-goal-state="PAUSED"]').waitFor()
    const response = page.waitForResponse(response => response.request().method() === 'GET' && response.url().endsWith('/sessions/session-1'))
    releasePoll(); await response
    await panel(page).locator('[data-goal-state="PAUSED"]').waitFor()
    assert.equal(await panel(page).getByRole('button', { name: 'Resume goal' }).count(), 1)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
  }, { goal: true, holdPoll: true })
  await run('autonomous navigation before admission acknowledgement retains the original pending goal', async ({ page, calls, waitForAdmission, releaseAdmission }) => {
    await open(page, false)
    await panel(page).getByRole('button', { name: 'Autonomous', exact: true }).click()
    await send(page, 'Prepare the original Brain only'); await waitForAdmission()
    await page.getByRole('link', { name: 'Select Models task', exact: true }).click()
    await page.getByRole('button', { name: 'Build with AI', exact: true }).click()
    await panel(page).getByText('Confirming the goal for brain-37. No new request is needed.', { exact: true }).waitFor()
    releaseAdmission()
    await panel(page).getByRole('region', { name: 'Autonomous goal', exact: true }).waitFor()
    await panel(page).locator('[data-goal-target]').filter({ hasText: 'AI responses' }).waitFor()
    await page.reload(); await open(page, false)
    await panel(page).getByRole('button', { name: 'Pause goal' }).click(); await panel(page).locator('[data-goal-state="PAUSED"]').waitFor()
    await panel(page).getByRole('button', { name: 'Resume goal' }).click(); await panel(page).locator('[data-goal-state="RUNNING"]').waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    assert.equal(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').length, 1)
  }, { goal: true, holdAdmission: true, path: '/smart-brains/brain-37/responses?scopeTest' })
  await run('autonomous real manual editor and save footer remain enabled while running', async ({ page, calls }) => {
    await startGoal(page)
    const field = page.getByLabel('OpenAI LLM model', { exact: true })
    assert.equal(await field.isEnabled(), true)
    await field.fill('manual-autonomous-model')
    assert.equal(await page.locator('footer button').filter({ hasText: 'Save draft' }).isEnabled(), true)
    await panel(page).getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByRole('link', { name: 'Updates', exact: true }).click()
    await page.getByRole('dialog', { name: 'Leave unsaved changes?' }).getByRole('button', { name: 'Keep editing' }).click()
    assert.equal(await field.inputValue(), 'manual-autonomous-model')
    assert.equal(calls.filter(call => /\/publish|\/apply$/.test(call.path)).length, 0)
  }, { goal: true, path: '/smart-brains/brain-37/models?real&targetProfileId=target-stage' })
  for (const state of ['BLOCKED', 'FAILED']) {
    await run(`native terminal ${state} retains partial work and permits a fresh preparation`, async ({ page, calls }) => {
      await startGoal(page); const p = panel(page)
      await p.getByText(state === 'BLOCKED' ? 'Remaining work blocked · 2 of 5 covered' : 'Work stopped · 2 of 5 covered', { exact: true }).waitFor()
      await p.getByText('Review the recorded result and remaining work.', { exact: true }).waitFor()
      await p.getByRole('region', { name: 'Remaining work', exact: true }).getByText('Enquiry WRITE', { exact: true }).waitFor()
      for (const name of ['Pause goal', 'Resume goal', 'Cancel goal']) assert.equal(await p.getByRole('button', { name, exact: true }).count(), 0)
      assert.equal(await p.getByRole('button', { name: 'New preparation', exact: true }).isEnabled(), true)
      assert.equal(await p.getByRole('button', { name: 'Send', exact: true }).isDisabled(), true, 'Empty composer is not a new request')
      await p.getByRole('button', { name: 'New preparation', exact: true }).click()
      await p.getByRole('button', { name: 'Prepare responses', exact: true }).waitFor()
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1, 'Opening a new preparation does not infer')
      assert.equal(calls.filter(call => call.path.endsWith('/directives') || call.path.endsWith('/control')).length, 0)
      await goalLayout(page)
      await page.screenshot({ path: join(evidence, `native-terminal-${state.toLowerCase()}-390.png`) })
    }, { goal: { state, terminal: true, canPause: false, canResume: false, canCancel: false,
      reasonCode: state === 'BLOCKED' ? 'UNSUPPORTED_WORK' : 'GOAL_DEADLINE_EXCEEDED', reasonMessage: 'The remaining operation cannot continue under this work contract.', nextAction: 'VIEW_RESULT' }, viewport: { width: 390, height: 844 } })
  }
  await run('quick edit explicitly retains bounded Prepare and its existing correction policy', async ({ page, calls, finish }) => {
    await open(page, false); const p = panel(page)
    assert.equal(await p.getByRole('button', { name: 'Autonomous', exact: true }).getAttribute('aria-pressed'), 'true')
    await p.getByRole('button', { name: 'Quick edit', exact: true }).click()
    await turnOptions(page)
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true)
    await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).click()
    const input = calls.find(call => call.path.endsWith('/turns')).input
    assert.equal(input.executionMode, 'BOUNDED'); assert.deepEqual(input.validationFeedback, { enabled: true, maxCorrections: 2 })
    finish(); await p.locator('[data-assistant-turn-status="SAVED"]').waitFor()
    assert.equal(await p.getByRole('region', { name: 'Autonomous goal' }).count(), 0)
    assert.equal(calls.filter(call => /\/goals\//.test(call.path)).length, 0)
    await p.getByRole('button', { name: 'Undo saved changes' }).click()
    await p.locator('[data-assistant-turn-status="UNDONE"]').waitFor()
  }, { path: '/smart-brains/brain-37/capabilities' })
  await run('quick edit after a completed goal polls its own turn without replaying the old goal', async ({ page, calls, finish }) => {
    await startGoal(page); finish()
    await panel(page).locator('[data-goal-state="COMPLETE"]').waitFor()
    await panel(page).getByRole('button', { name: 'Quick edit', exact: true }).click()
    await send(page, 'Make one small follow-up change')
    await panel(page).getByRole('article', { name: 'Assistant', exact: true }).last().locator('[data-assistant-turn-status="SAVED"]').waitFor()
    const turns = calls.filter(call => call.path.endsWith('/turns'))
    assert.equal(turns.length, 2); assert.equal(turns[1].input.executionMode, 'BOUNDED')
    assert.deepEqual(turns[1].input.validationFeedback, { enabled: true, maxCorrections: 2 })
    assert.equal(calls.filter(call => call.path.endsWith('/control') || call.path.endsWith('/directives')).length, 0)
  }, { goal: true })
  for (const missing of [false, true]) for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await run(`published autonomy availability ${missing ? 'missing' : 'disabled'} keeps Quick edit usable at ${viewport.width}px`, async ({ page, calls, finish }) => {
      await open(page, false); const p = panel(page)
      assert.equal(await p.getByRole('button', { name: 'Quick edit', exact: true }).getAttribute('aria-pressed'), 'true')
      assert.equal(await p.getByRole('button', { name: 'Autonomous', exact: true }).isDisabled(), true)
      await p.getByText(missing ? 'Autonomous availability is not reported by this server. Quick edit remains available.' : 'Autonomous is disabled in the published authoring Brain.', { exact: true }).waitFor()
      await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).click()
      const turns = calls.filter(call => call.path.endsWith('/turns'))
      assert.equal(turns.length, 1); assert.equal(turns[0].input.executionMode, 'BOUNDED')
      assert.deepEqual(turns[0].input.validationFeedback, { enabled: true, maxCorrections: 2 })
      finish(); await p.locator('[data-assistant-turn-status="SAVED"]').waitFor()
      assert.equal(await p.getByRole('region', { name: 'Autonomous goal' }).count(), 0)
      assert.equal(calls.filter(call => /\/goals\/|\/publish|\/apply$/.test(call.path) || ['PUT', 'PATCH'].includes(call.method)).length, 0, 'No goal admission, semantic retry or configuration enablement')
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
      const layout = await p.getByRole('textbox', { name: 'Message', exact: true }).boundingBox()
      assert.ok(layout.y >= 0 && layout.y + layout.height < viewport.height)
      await page.screenshot({ path: join(evidence, `autonomous-availability-${missing ? 'missing' : 'disabled'}-${viewport.width}.png`) })
    }, { path: '/smart-brains/brain-37/capabilities', autonomousEnabled: false, autonomousUnavailableReason: 'Autonomous is disabled in the published authoring Brain.', missingAutonomousAvailability: missing, viewport })
  }
  await run('published autonomy rollback invalidates an earlier Autonomous preference before admission', async ({ page, calls, setAvailability, finish }) => {
    await open(page, false); const p = panel(page)
    await p.getByRole('button', { name: 'Autonomous', exact: true }).click()
    setAvailability(false, 'The current published goal policy is disabled.')
    await p.getByRole('button', { name: 'Assistant session options' }).click()
    await p.getByRole('button', { name: 'Refresh assistant session' }).click()
    await p.getByText('The current published goal policy is disabled.', { exact: true }).waitFor()
    assert.equal(await p.getByRole('button', { name: 'Autonomous', exact: true }).isDisabled(), true)
    assert.equal(await p.getByRole('button', { name: 'Quick edit', exact: true }).getAttribute('aria-pressed'), 'true')
    await p.getByRole('button', { name: 'Prepare my Brain', exact: true }).click()
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.executionMode, 'BOUNDED')
    finish(); await p.locator('[data-assistant-turn-status="SAVED"]').waitFor()
  }, { path: '/smart-brains/brain-37' })
  const preparedCapabilities = [
    { name: 'read_catalogue', displayName: 'Find products', description: 'Search the catalogue.', accessMode: 'READ', anonymousAllowed: true, requiresConfirmation: false },
    { name: 'submit_enquiry', displayName: 'Request support', description: 'Submit an authorized enquiry.', accessMode: 'WRITE_ONLY', anonymousAllowed: false, requiresConfirmation: true },
  ]
  await run('AI-first journey keeps task guidance, actual saved capabilities and review in one workspace', async ({ page, calls, finish }) => {
    await open(page, false)
    await panel(page).getByRole('navigation', { name: 'AI setup steps' }).getByRole('link', { name: 'Connect your tools', exact: true }).click()
    await page.getByRole('heading', { name: 'What should your AI help customers do?', exact: true }).waitFor()
    await open(page, false)
    await importDocumentation(page, 'paste', 'Customer API.yaml')
    const p = panel(page)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
    await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).click(); finish()
    await p.locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await p.getByText('Saved to draft · Not published', { exact: true }).waitFor()
    const table = page.getByRole('table', { name: 'Your capabilities', exact: true })
    await table.getByRole('link', { name: 'Find products', exact: true }).waitFor()
    await table.getByRole('link', { name: 'Request support', exact: true }).waitFor()
    const review = p.getByRole('link', { name: 'Review draft', exact: true })
    assert.equal(new URL(await review.getAttribute('href'), base).searchParams.get('targetProfileId'), 'target-stage')
    const changes = p.getByRole('region', { name: 'Saved changes', exact: true })
    assert.equal(await changes.locator('details').first().getAttribute('open'), null)
    await changes.locator('summary').filter({ hasText: 'See what changed' }).click()
    assert.ok(await changes.getByRole('listitem').count() > 0)
    await changes.locator('summary').filter({ hasText: 'See what changed' }).click()
    await page.screenshot({ path: join(evidence, 'ai-first-capabilities-workspace.png'), fullPage: true })
    await review.click()
    await page.waitForURL(url => url.pathname.endsWith('/updates'))
    assert.equal(calls.filter(call => /\/publish|\/apply$/.test(call.path)).length, 0, 'Review never publishes or deploys')
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { path: '/smart-brains/brain-37/?real&targetProfileId=target-stage', aiWorkspace: preparedCapabilities,
    diff: [{ path: '/actionsConfig/actions', before: [], after: preparedCapabilities }], findings: { valid: true, connectionTested: false } })
  await run('Build with AI is prominent with bounded motion and a reduced-motion alternative', async ({ page, calls }) => {
    await open(page, false)
    const button = page.getByRole('button', { name: 'Build with AI', exact: true })
    const style = await button.evaluate(node => {
      const button = getComputedStyle(node), icon = getComputedStyle(node.querySelector('.MuiButton-startIcon'))
      return { height: node.getBoundingClientRect().height, weight: Number(button.fontWeight), background: button.backgroundColor, animation: button.animationName, iterations: button.animationIterationCount, iconAnimation: icon.animationName }
    })
    assert.ok(style.height >= 38 && style.weight >= 700)
    assert.notEqual(style.background, 'rgba(0, 0, 0, 0)')
    assert.equal(style.animation, 'buildAiInvite'); assert.equal(style.iterations, '3'); assert.equal(style.iconAnimation, 'buildAiSpark')
    await page.screenshot({ path: join(evidence, 'build-with-ai-prominent.png') })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const reduced = await button.evaluate(node => [getComputedStyle(node).animationName, getComputedStyle(node.querySelector('.MuiButton-startIcon')).animationName])
    assert.deepEqual(reduced, ['none', 'none'])
    await panel(page).getByRole('button', { name: 'Close assistant' }).click()
    await button.waitFor({ state: 'visible' })
    await button.click(); await panel(page).getByRole('textbox', { name: 'Message', exact: true }).waitFor()
    assert.equal(calls.filter(call => /\/turns|\/apply|\/publish/.test(call.path)).length, 0)
  }, { path: '/smart-brains/brain-37/models?real' })
  await run('Brain entry opens assistance; dismissal survives page changes and another Brain opens afresh', async ({ page, calls }) => {
    await open(page, false)
    assert.equal(await panel(page).isVisible(), true)
    await page.getByLabel('Manual value', { exact: true }).fill('Retained manual edit')
    await panel(page).getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('link', { name: 'Select Models task' }).click()
    assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).getAttribute('aria-expanded'), 'false')
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'Retained manual edit')
    await open(page, false)
    await page.getByRole('link', { name: 'Select Updates guidance' }).click()
    await panel(page).getByText('Guide only.', { exact: false }).waitFor()
    await panel(page).getByRole('button', { name: 'Explain setup', exact: true }).waitFor()
    await panel(page).getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('link', { name: 'Select another Brain' }).click()
    await panel(page).getByRole('textbox', { name: 'Message', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).getAttribute('aria-expanded'), 'true')
    await page.getByRole('link', { name: 'Select Models task' }).click()
    await page.waitForURL(url => url.pathname === '/smart-brains/brain-37/models')
    await panel(page).getByRole('textbox', { name: 'Message', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Build with AI', exact: true }).getAttribute('aria-expanded'), 'true', 'Returning to a previously dismissed Brain is a fresh visit')
    assert.equal(calls.filter(call => /\/turns|\/apply|\/publish|\/sources/.test(call.path)).length, 0, 'Opening and navigation perform no inference, source sharing or saves')
  }, { path: '/smart-brains/brain-37/responses?defaultOpen' })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    await run(`Brain shell opens its closable panel at ${viewport.width}px`, async ({ page, calls }) => {
      await panel(page).getByRole('textbox', { name: 'Message', exact: true }).waitFor()
      assert.equal(await panel(page).isVisible(), true, 'Actual Brain shell opens without clicking the entry button')
      assert.equal(await page.locator('[id="configuration-assistant-panel"]').count(), 1)
      await page.screenshot({ path: join(evidence, `brain-assistant-${viewport.width}.png`), fullPage: true })
      await panel(page).getByRole('button', { name: 'Close assistant' }).click()
      await panel(page).waitFor({ state: 'hidden' })
      await open(page, false)
      assert.equal(await panel(page).isVisible(), true)
      assert.equal(calls.filter(call => /\/turns|\/apply|\/publish/.test(call.path)).length, 0)
    }, { path: '/smart-brains/brain-37/models?real', viewport, dark: viewport.width === 1440 })
  }
  for (const [area, target, task] of [['documents', '/smart-brains/brain-37/knowledge/documents?sourceShortcut&kind=DOCUMENT_SOURCE', 'DOCUMENT_PREPARATION'], ['search', '/smart-brains/brain-37/knowledge/search?sourceShortcut', 'SEARCH']]) {
    for (const choice of ['default', 'quick', 'unavailable']) {
      await run(`external preparation ${area} respects ${choice} execution mode`, async ({ page, calls, finish }) => {
        await open(page, false)
        if (choice === 'quick') await panel(page).getByRole('button', { name: 'Quick edit', exact: true }).click()
        assert.equal(calls.filter(c => c.path.endsWith('/turns')).length, 0)
        const sent = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/turns'))
        await page.getByRole('button', { name: 'Optimize selected sample', exact: true }).click()
        await sent
        const turns = calls.filter(c => c.path.endsWith('/turns'))
        assert.equal(turns.length, 1)
        assert.equal(calls.find(c => c.path.endsWith('/sessions') && c.method === 'POST').input.task, task)
        const input = turns[0].input
        assert.equal(input.executionMode, choice === 'default' ? 'AUTONOMOUS' : 'BOUNDED')
        assert.equal(input.command, 'PREPARE'); assert.equal(input.previewOnly, false)
        assert.deepEqual(input.sourceIds, ['source-1'])
        if (choice === 'default') assert.equal(input.validationFeedback, undefined)
        else assert.deepEqual(input.validationFeedback, { enabled: true, maxCorrections: 2 })
        finish(); await latestSaved(page)
      }, { path: target, autonomousEnabled: choice !== 'unavailable' })
    }
  }
  for (const mode of ['paste', 'file', 'url']) {
    await run(`page aware ${mode} documentation prepares actions with no hint and no inference on import`, async ({ page, calls, finish }) => {
      await open(page, false); const p = panel(page)
      await p.getByText('Saves draft only. Review or undo after. Never publishes.', { exact: true }).waitFor()
      await p.getByRole('region', { name: 'Page guidance', exact: true }).getByText('Share API documentation', { exact: false }).waitFor()
      assert.equal(await p.getByRole('button', { name: 'Turn options', exact: true }).getAttribute('aria-expanded'), 'false')
      await importDocumentation(page, mode)
      assert.ok((await p.getByRole('region', { name: 'Selected documents', exact: true }).innerText()).includes('API reference'))
      assert.equal(calls.filter(call => call.path.endsWith('/content')).length, 0, 'Source review is an explicit separate action')
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0, 'Open and import do not invoke AI')
      assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
      await page.screenshot({ path: join(evidence, `page-aware-${mode}-ready.png`) })
      await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).click()
      await p.locator('[data-assistant-turn-status="RUNNING"]').waitFor()
      const input = calls.find(call => call.path.endsWith('/turns')).input
      assert.deepEqual(input, { query: '', command: 'PREPARE', editorContext: { section: 'ACTIONS' }, previewOnly: false, sourceIds: ['source-1'], operationIds: [], executionMode: 'AUTONOMOUS' })
      assert.equal((await p.innerText()).includes('Registered server intent'), false)
      assert.equal(await p.getByRole('article', { name: 'You', exact: true }).getByText('Prepare capabilities', { exact: true }).count(), 1)
      finish(); await latestSaved(page)
      assert.equal(await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).count(), 0, 'Initial preparation collapses after the first turn')
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
      assert.equal(calls.filter(call => /publish|activate|\/apply$|index/.test(call.path)).length, 0)
      await page.screenshot({ path: join(evidence, `page-aware-${mode}-saved.png`) })
    }, { path: '/smart-brains/brain-37/capabilities/actions', url: mode === 'url', findings: { valid: true }, ...(mode === 'file' ? { viewport: { width: 390, height: 844 }, dark: false } : {}) })
  }
  await run('page aware short hint and selected operations clarify through normal Send, not another preparation', async ({ page, calls, finish }) => {
    await open(page, false); await importDocumentation(page)
    const p = panel(page)
    await p.locator('summary').filter({ hasText: 'Sources (' }).click(); await p.getByRole('checkbox', { name: 'GET /catalogue List catalogue' }).check()
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Catalogue reads only')
    await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).click(); finish()
    await p.locator('[data-assistant-turn-status="NEEDS_INPUT"]').waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).count(), 0)
    await p.getByText('Answer the question, then Send.', { exact: true }).waitFor()
    assert.equal(await p.getByRole('region', { name: 'Page guidance', exact: true }).getByText('Share API documentation', { exact: false }).count(), 0)
    const first = calls.find(call => call.path.endsWith('/turns')).input
    assert.equal(first.query, 'Catalogue reads only'); assert.deepEqual(first.operationIds, ['catalogue'])
    assert.ok((await p.getByRole('article', { name: 'You', exact: true }).innerText()).includes('Prepare capabilities\nCatalogue reads only'))
    await send(page, 'Use the accountId field'); await latestSaved(page)
    const second = calls.filter(call => call.path.endsWith('/turns'))[1].input
    assert.equal(second.query, 'Use the accountId field'); assert.equal(second.command, undefined)
    assert.deepEqual(second.editorContext, { section: 'ACTIONS' }); assert.deepEqual(second.sourceIds, ['source-1'])
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 2)
    await page.screenshot({ path: join(evidence, 'page-aware-clarification-follow-up.png') })
  }, { path: '/smart-brains/brain-37/capabilities/actions', results: ['NEEDS_INPUT', 'SAVED'], findings: { valid: true } })
  await run('page aware models-search navigation retains separate task sessions without automatic inference', async ({ page, calls, finish }) => {
    await open(page, false); const p = panel(page)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Keep this optional hint')
    await page.getByRole('link', { name: 'Search context', exact: true }).click()
    await p.waitFor({ state: 'hidden' })
    await open(page, false)
    await p.getByRole('button', { name: 'Prepare search settings', exact: true }).waitFor()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).inputValue(), '')
    assert.deepEqual(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').map(call => call.input.task), ['MODELS', 'SEARCH'])
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('')
    await p.getByRole('button', { name: 'Prepare search settings', exact: true }).click(); finish(); await latestSaved(page)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.editorContext, { section: 'SEARCH' })
    await page.getByRole('link', { name: 'Models context', exact: true }).click()
    await p.waitFor({ state: 'hidden' })
    await open(page, false)
    await send(page, 'Use the response model I selected'); await latestSaved(page)
    assert.deepEqual(calls.filter(call => call.path.endsWith('/turns'))[1].input.editorContext, { section: 'MODELS' })
    assert.equal(calls.filter(call => call.path.endsWith('/sessions') && call.method === 'POST').length, 2)
    await page.screenshot({ path: join(evidence, 'page-aware-models-search-context.png') })
  }, { path: '/smart-brains/brain-37/models?contextTest', findings: { valid: true } })
  await run('page aware registered subpages show refreshed guidance and exact definition hints', async ({ page, calls, finish }) => {
    await open(page, false); const p = panel(page)
    let task = 'BEHAVIOUR'
    for (const [link, command, nextTask] of [['Instructions context', 'Prepare instructions', 'BEHAVIOUR'], ['Documents context', 'Prepare document sources', 'KNOWLEDGE'], ['Live data context', 'Prepare live data', 'KNOWLEDGE'], ['Storage context', 'Prepare storage', 'KNOWLEDGE'], ['Selected action context', 'Prepare capabilities', 'ACTIONS']]) {
      await page.getByRole('link', { name: link, exact: true }).click()
      if (task !== nextTask) { await page.waitForFunction(() => document.querySelector('[data-assistant-workspace]')?.getAttribute('data-assistant-open') === 'false'); await open(page, false) }
      task = nextTask
      await p.getByRole('button', { name: command, exact: true }).waitFor()
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
    }
    await p.getByRole('button', { name: 'Assistant session options' }).click()
    await p.getByText('Selected: read', { exact: true }).waitFor()
    await p.getByRole('button', { name: 'Assistant session options' }).click()
    await p.getByRole('button', { name: 'Prepare capabilities', exact: true }).click(); finish(); await latestSaved(page)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.editorContext, { section: 'ACTIONS', selectedDefinitionIds: ['read'] })
    assert.ok(calls.some(call => call.path.endsWith('/sessions') && call.input?.task === 'KNOWLEDGE'))
  }, { path: '/smart-brains/brain-37/responses?contextTest' })
  await run('page aware same-Brain reuse is offered explicitly and reselects only the chosen attached source', async ({ page, calls }) => {
    await open(page, false); await importDocumentation(page, 'paste', 'First reference'); await importDocumentation(page, 'paste', 'Second reference')
    const p = panel(page), summary = p.locator('summary').filter({ hasText: 'Sources (' })
    await summary.click(); await p.getByRole('checkbox', { name: 'First reference', exact: true }).uncheck(); await p.getByRole('checkbox', { name: 'Second reference', exact: true }).uncheck()
    assert.equal(calls.filter(call => call.path.endsWith('/reusable-sources')).length, 0)
    await p.getByRole('button', { name: 'Brain documents', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Add documentation' })
    await dialog.getByRole('combobox', { name: 'Available source' }).click()
    assert.deepEqual(await page.getByRole('option').allTextContents(), ['Previously imported API / ACTIONS / application/json'])
    await page.getByRole('option', { name: 'Previously imported API / ACTIONS / application/json', exact: true }).click()
    assert.equal(calls.filter(call => call.path.endsWith('/sources/reuse')).length, 0, 'Listing reuse candidates does not attach them')
    await dialog.getByRole('button', { name: 'Reuse source', exact: true }).click(); await dialog.waitFor({ state: 'hidden' })
    assert.equal(await p.getByRole('checkbox', { name: 'First reference', exact: true }).isChecked(), true)
    assert.equal(await p.getByRole('checkbox', { name: 'Second reference', exact: true }).isChecked(), false)
    assert.equal((await p.getByRole('region', { name: 'Selected documents', exact: true }).innerText()).trim(), 'First reference')
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
    assert.equal(calls.filter(call => call.path.endsWith('/content')).length, 0)
  }, { existingReuse: true, path: '/smart-brains/brain-37/capabilities/actions' })
  for (const [path, command, section] of [['/smart-brains/brain-37/action-reviews', 'Explain action reviews', 'ACTION_REVIEWS'], ['/smart-brains/brain-37/advanced/configuration', 'Explain setup', 'KIT']]) {
    await run(`page aware ${section} command is guidance only without a hint`, async ({ page, calls, finish }) => {
      await open(page, false); const p = panel(page)
      await p.getByText('Guidance only. No draft changes.', { exact: true }).waitFor()
      await p.getByRole('button', { name: command, exact: true }).click(); finish(); await p.locator('[data-assistant-turn-status="EXPLAIN"]').waitFor()
      const input = calls.find(call => call.path.endsWith('/turns')).input
      assert.equal(input.query, ''); assert.equal(input.previewOnly, true)
      assert.deepEqual(input.editorContext, { section }); assert.deepEqual(input.validationFeedback, { enabled: true, maxCorrections: 2 })
      assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    }, { path })
  }
  await run('page aware active Engage prepares preview only with no draft save', async ({ page, calls, finish }) => {
    await open(page, false); const p = panel(page)
    await p.getByRole('button', { name: 'Prepare appearance (preview)', exact: true }).click(); finish(); await p.locator('[data-assistant-turn-status="PREVIEW"]').waitFor()
    const input = calls.find(call => call.path.endsWith('/turns')).input
    assert.equal(input.command, 'PREPARE'); assert.equal(input.previewOnly, true); assert.deepEqual(input.editorContext, { section: 'ENGAGE' })
    assert.equal(await p.getByRole('button', { name: 'Save preview to draft' }).isDisabled(), true)
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    await page.screenshot({ path: join(evidence, 'page-aware-active-engage-preview.png') })
  }, { path: '/smart-brains/brain-37/channels?kind=ENGAGE_APP&active', active: true, viewport: { width: 390, height: 844 } })
  await run('page aware preview and Explain only retain their existing nonmutating contracts', async ({ page, calls, finish }) => {
    await open(page); const p = panel(page)
    await p.getByRole('checkbox', { name: 'Preview only' }).check(); await p.getByRole('button', { name: 'Prepare capabilities (preview)', exact: true }).click(); finish(); await p.locator('[data-assistant-turn-status="PREVIEW"]').waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    await p.getByRole('checkbox', { name: 'Explain only' }).check(); await p.getByRole('button', { name: 'New preparation', exact: true }).click()
    assert.equal(await p.getByRole('button', { name: 'Prepare capabilities (preview)', exact: true }).isDisabled(), true)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Explain the current source mapping'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    await p.getByRole('article', { name: 'Assistant', exact: true }).nth(1).locator('[data-assistant-turn-status="PREVIEW"]').waitFor()
    const second = calls.filter(call => call.path.endsWith('/turns'))[1].input
    assert.equal(second.command, undefined); assert.equal(second.previewOnly, true); assert.ok(second.query.startsWith('Explain only;'))
    assert.deepEqual(second.validationFeedback, { enabled: true, maxCorrections: 2 })
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
  }, { path: '/smart-brains/brain-37/capabilities/actions' })
  await run('page aware preparation hint bound does not reduce normal typed request capacity', async ({ page, calls }) => {
    await open(page, false); const p = panel(page), message = p.getByRole('textbox', { name: 'Message', exact: true }), prepare = p.getByRole('button', { name: 'Prepare capabilities', exact: true })
    await message.fill('x'.repeat(6000)); assert.equal(await prepare.isEnabled(), true)
    await message.fill('x'.repeat(6001)); assert.equal(await prepare.isDisabled(), true)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
    await message.fill('x'.repeat(7000)); await p.getByRole('button', { name: 'Send', exact: true }).click(); await p.locator('[data-assistant-turn-status="RUNNING"]').waitFor()
    const input = calls.find(call => call.path.endsWith('/turns')).input
    assert.equal(input.query.length, 7000); assert.equal(input.command, undefined)
  }, { path: '/smart-brains/brain-37/capabilities/actions' })
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
    await p.getByRole('button', { name: 'Cancel', exact: true }).click(); await p.locator('[data-assistant-turn-status="CANCELLED"]').waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
    await send(page); await p.locator('[data-assistant-turn-status="RUNNING"]').waitFor()
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
    assert.equal(await article.locator('[data-assistant-turn-status="SAVED"]').count(), 0)
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
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
      await article.locator('[data-assistant-turn-status="' + result + '"]').waitFor()
      await article.getByText(result === 'INVALID' ? 'Correction limit reached. Review the remaining findings or continue manually.' : 'Correction stopped. Review the failure and retained attempt history.', { exact: true }).waitFor()
      await article.getByText('Validation feedback (2 attempts)', { exact: true }).click()
      await article.getByText('Attempt 1 / VALIDATION_ERRORS', { exact: true }).waitFor(); await article.getByText('Attempt 2 / RUNNING', { exact: true }).waitFor(); await article.getByText('Not completed', { exact: true }).waitFor()
      await article.getByText('Attempt findings', { exact: true }).click(); await article.getByText('Missing source mapping.', { exact: false }).waitFor()
      assert.equal(await article.getByText('Attempt 2 of 3', { exact: true }).count(), 0, 'Terminal turns do not show stale active progress')
      assert.equal(await article.locator('[data-assistant-turn-status="SAVED"]').count(), 0)
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
    await article.locator('[data-assistant-turn-status="INVALID"]').waitFor()
    await article.getByText('Correction limit reached. Review the remaining findings or continue manually.', { exact: true }).waitFor()
    await article.getByText('Validation feedback (3 attempts)', { exact: true }).click()
    for (const number of [1, 2, 3]) await article.getByText(`Attempt ${number} / VALIDATION_ERRORS`, { exact: true }).waitFor()
    assert.equal(await article.getByText('Not completed', { exact: true }).count(), 0)
    assert.equal(await article.locator('[data-assistant-turn-status="SAVED"]').count(), 0)
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
  }, { result: 'INVALID', finalAttempts: [1, 2, 3].map(number => ({ ...invalidAttempt, number })), feedbackStopReason: 'CORRECTION_LIMIT_REACHED', findings: { valid: false } })
  await run('validation feedback saved invalid final candidate remains needs attention, not corrected', async ({ page, calls, finish }) => {
    await open(page); await send(page); finish(); const p = panel(page), article = p.getByRole('article', { name: 'Assistant', exact: true })
    await article.getByText('Draft saved. Needs attention.', { exact: true }).waitFor()
    await article.getByText('Correction limit reached. Review the remaining findings or continue manually.', { exact: true }).waitFor()
    assert.equal(await article.getByText('Draft saved. Configuration validation passed.', { exact: true }).count(), 0)
    assert.equal(await article.locator('[data-assistant-turn-status="SAVED"]').evaluate(node => node.closest('.MuiChip-root').classList.contains('MuiChip-colorSuccess')), false)
    await article.getByText('Validation feedback (2 attempts)', { exact: true }).click()
    await article.getByText('Attempt 1 / INVALID_OUTPUT', { exact: true }).waitFor()
    await article.getByText('Structured output rejected.', { exact: true }).waitFor()
    await article.getByText('Attempt 2 / VALIDATION_ERRORS', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
    assert.equal(await first.locator('[data-assistant-turn-status="SAVED"]').evaluate(node => node.closest('.MuiChip-root').classList.contains('MuiChip-colorSuccess')), false)
    assert.equal(await first.locator('details[open]').count(), 0, 'Validation is visible without expanding raw JSON')
    await page.screenshot({ path: join(evidence, 'saved-invalid-validation-desktop.png') })
    dataValidation.valid = true; dataValidation.revision = 3; dataValidation.issues = []
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
    await summary.locator('summary').filter({ hasText: 'See what changed' }).click()
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
  await run('saved turn leaves manual input available, refreshes requested values and exposes receipts and undo', async ({ page, calls, finish }) => {
    await page.getByLabel('Manual value', { exact: true }).fill('Unsaved requested manual value')
    await page.getByLabel('Unrelated value', { exact: true }).fill('Unrelated unsaved value')
    await open(page); await send(page)
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
    assert.equal(await page.getByLabel('Unrelated value', { exact: true }).isEnabled(), true)
    assert.equal(await page.locator('[data-assistant-workspace] [inert]').count(), 0)
    await page.getByLabel('Manual value', { exact: true }).fill('Manual input remains available during AI work')
    const creation = calls.find(call => call.path.endsWith('/sessions') && call.method === 'POST')
    assert.deepEqual(creation.input, { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'BEHAVIOUR' })
    assert.ok(calls.find(call => call.path.endsWith('/turns')).key)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.sourceIds, [], 'No implicit source sharing')
    finish(); await panel(page).locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
    await panel(page).getByRole('button', { name: 'Undo saved change' }).click(); await panel(page).locator('[data-assistant-turn-status="UNDONE"]').waitFor()
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
    await send(page); finish(); await panel(page).locator('[data-assistant-turn-status="SAVED"]').waitFor()
    const turn = calls.find(call => call.path.endsWith('/turns'))
    assert.deepEqual(turn.input.sourceIds, ['source-1']); assert.deepEqual(turn.input.operationIds, ['catalogue'])
    assert.deepEqual(turn.input.validationFeedback, { enabled: true, maxCorrections: 2 }, 'Feedback preserves explicit source and operation choices')
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Change the workspace title'); await p.getByRole('button', { name: 'Preview', exact: true }).click(); finish(); await p.locator('[data-assistant-turn-status="PREVIEW"]').waitFor()
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
    await p.getByRole('checkbox', { name: 'Preview only' }).check(); await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Suggest a model'); await p.getByRole('button', { name: 'Preview', exact: true }).click(); finish(); await p.locator('[data-assistant-turn-status="PREVIEW"]').waitFor()
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0)
    await p.getByRole('button', { name: 'Save preview to draft' }).click(); await p.locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    await p.getByRole('checkbox', { name: 'Explain only' }).check()
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isDisabled(), false)
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true)
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Explain this model choice'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).at(-1).input.previewOnly, true)
    assert.deepEqual(calls.filter(call => call.path.endsWith('/turns')).at(-1).input.validationFeedback, { enabled: true, maxCorrections: 2 })
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    await p.getByRole('checkbox', { name: 'Explain only' }).uncheck()
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true, 'Explain does not discard correction preference')
  }, { path: '/smart-brains/brain-37/models' })
  await run('lost turn response retries the same idempotency key and cancellation unlocks the editor', async ({ page, calls, cancelled }) => {
    await open(page); await send(page); await panel(page).getByRole('button', { name: 'Retry same turn' }).click()
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    const attempts = calls.filter(call => call.path.endsWith('/turns')); assert.equal(attempts.length, 2); assert.equal(attempts[0].key, attempts[1].key)
    assert.deepEqual(attempts[0].input.validationFeedback, attempts[1].input.validationFeedback, 'Explicit transport retry retains the original turn policy')
    await panel(page).getByRole('button', { name: 'Cancel', exact: true }).click(); await panel(page).locator('[data-assistant-turn-status="CANCELLED"]').waitFor()
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
  await run('legacy history starts the configured assistant without deletion and remains accessible after reload', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Build with AI', exact: true }).click()
    const p = panel(page)
    await p.getByText('Earlier preserved answer', { exact: true }).waitFor()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).isDisabled(), true)
    await p.getByRole('button', { name: 'Start Build with AI', exact: true }).click()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    await page.reload(); await open(page, false)
    await p.getByRole('button', { name: 'Assistant session options' }).click()
    await p.getByRole('button', { name: 'View earlier conversation', exact: true }).click()
    await p.getByText('Earlier preserved answer', { exact: true }).waitFor()
    assert.equal(await p.getByRole('textbox', { name: 'Message', exact: true }).isDisabled(), true)
    assert.equal(calls.filter(call => call.method === 'DELETE' || call.path.endsWith('/turns')).length, 0)
    assert.equal(calls.filter(call => call.path === '/api/editor').length, 0, 'Viewing earlier saved history must not reload the manual buffer')
    assert.ok(calls.some(call => call.method === 'GET' && call.path.endsWith('/sessions/session-1')))
  }, { legacy: true })
  await run('save reload failures remain visible without blocking manual editing or follow-ups', async ({ page, finish, allowReload }) => {
    await open(page); await send(page); finish()
    await panel(page).getByRole('alert').filter({ hasText: 'Draft changes were saved, but the editor could not be reloaded.' }).waitFor()
    assert.equal(await page.getByLabel('Manual value', { exact: true }).isEnabled(), true)
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    await page.getByLabel('Manual value', { exact: true }).fill('Continue manual editing')
    allowReload(); await panel(page).getByRole('button', { name: 'Assistant session options' }).click(); await panel(page).getByRole('button', { name: 'Refresh assistant session' }).click()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(await page.getByLabel('Manual value', { exact: true }).inputValue(), 'AI saved value')
  }, { reloadError: true })
  await run('saved private plugin reloads its real editor without publishing a version', async ({ page, calls, finish }) => {
    await page.getByLabel('Display name', { exact: true }).fill('Manual plugin value'); await open(page); await send(page, 'Name this private source'); finish(); await panel(page).locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
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
    assert.deepEqual(turns[0].input, { query, previewOnly: false, sourceIds: ['source-1'], operationIds: ['catalogue'], validationFeedback: { enabled: false, maxCorrections: 0 }, editorContext: { section: 'BEHAVIOUR' }, executionMode: 'BOUNDED' })
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
      await panel(page).waitFor({ state: 'hidden' })
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
    const openKit = async () => { await page.getByRole('button', { name: 'Kit AI guide', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled); await turnOptions(page) }
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
    await p.getByRole('textbox', { name: 'Message', exact: true }).press('Enter'); finish(); await p.locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await resizeWorkspace(page, { width: 320, height: 740 }); assert.ok((await p.boundingBox()).width <= 320)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await latestSaved(page)
    await page.screenshot({ path: join(evidence, 'assistant-light-320.png') })
  }, { dark: false })
  await run('real Smart Brain editor replaces requested unsaved values even with the same draft ID and revision', async ({ page, calls, finish }) => {
    const field = page.getByLabel('OpenAI LLM model', { exact: true }); await field.fill('manual-unsaved-model')
    await open(page); await send(page, 'Use the selected AI model')
    await page.waitForFunction(() => document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(await field.isEnabled(), true)
    assert.equal(await page.locator('footer button').filter({ hasText: 'Save draft' }).isEnabled(), true)
    await page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByRole('link', { name: 'Updates', exact: true }).click()
    const blocked = page.getByRole('dialog', { name: 'Leave unsaved changes?' }); await blocked.waitFor()
    assert.equal(await blocked.getByRole('button', { name: 'Discard & leave' }).isEnabled(), true)
    await blocked.getByRole('button', { name: 'Keep editing' }).click()
    assert.equal(calls.filter(call => /publish|\/apply$/.test(call.path)).length, 0)
    finish(); await panel(page).locator('[data-assistant-turn-status="SAVED"]').waitFor()
    await page.waitForFunction(() => [...document.querySelectorAll('input')].some(input => input.value === 'ai-model'))
    assert.equal(await field.inputValue(), 'ai-model'); assert.equal(await page.getByRole('dialog').count(), 0, 'No conflict approval')
    assert.ok(calls.filter(call => call.path === '/api/deployments/brain-37/draft').length >= 2)
    await latestSaved(page)
    await page.screenshot({ path: join(evidence, 'real-brain-same-revision-dark.png') })
  }, { path: '/smart-brains/brain-37/models?real&targetProfileId=target-stage' })
  await run('real Brain discard-and-leave preserves consent and changes pages without stale blocker callbacks', async ({ page, calls }) => {
    await open(page, false)
    const field = page.getByLabel('OpenAI LLM model', { exact: true })
    await field.fill('unsaved-manual-model')
    const knowledge = page.getByRole('navigation', { name: 'Smart Brain navigation' }).getByRole('link', { name: 'Knowledge', exact: true })
    await knowledge.click()
    const confirmation = page.getByRole('dialog', { name: 'Leave unsaved changes?' })
    await confirmation.getByRole('button', { name: 'Keep editing', exact: true }).click()
    assert.equal(new URL(page.url()).pathname, '/smart-brains/brain-37/models')
    assert.equal(await field.inputValue(), 'unsaved-manual-model')
    await confirmation.waitFor({ state: 'detached' })
    await knowledge.click()
    await confirmation.getByRole('button', { name: 'Discard & leave', exact: true }).click()
    await page.waitForURL(url => url.pathname === '/smart-brains/brain-37/knowledge')
    await confirmation.waitFor({ state: 'detached' })
    assert.equal(await field.inputValue(), 'saved-model')
    assert.equal(calls.filter(call => /\/turns|\/apply|\/publish/.test(call.path)).length, 0)
  }, { path: '/smart-brains/brain-37/models?real&targetProfileId=target-stage' })
  await run('actual Human support links bind Specialists and reviews with their own draft/guide scopes', async ({ page, calls }) => {
    await open(page, false)
    assert.equal(calls.find(call => call.path.endsWith('/sessions')).input.task, 'HUMAN_SUPPORT')
    await page.getByRole('navigation', { name: 'Human support tasks' }).getByRole('link', { name: 'Specialists', exact: true }).click()
    await page.getByRole('heading', { name: 'Specialists', exact: true }).waitFor(); await page.getByRole('checkbox', { name: 'Specialist assistance' }).waitFor()
    await open(page); assert.equal(calls.filter(call => call.path.endsWith('/sessions')).at(-1).input.task, 'SPECIALISTS')
    await panel(page).getByRole('button', { name: 'Close assistant' }).click()
    await page.getByRole('navigation', { name: 'Human support tasks' }).getByRole('link', { name: 'Action reviews', exact: true }).click(); await open(page)
    await panel(page).getByText('Guide only.', { exact: false }).waitFor()
    await panel(page).getByRole('textbox', { name: 'Message', exact: true }).fill('Explain review setup'); await panel(page).getByRole('button', { name: 'Explain', exact: true }).click()
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.previewOnly, true)
    assert.equal(calls.filter(call => call.path.endsWith('/sessions')).at(-1).input.task, 'KIT', 'Reviews use the published read-only guide, not a handover writer')
    assert.equal(calls.find(call => call.path.endsWith('/turns')).path, '/api/configuration-assistant/sessions/session-3/turns')
  }, { path: '/smart-brains/brain-37/human-support?real&targetProfileId=target-stage' })
  await run('whole Brain setup is explicit and page navigation restores narrower authoring scopes', async ({ page, calls }) => {
    await open(page, false)
    assert.equal(calls.find(call => call.path.endsWith('/sessions')).input.task, 'MODELS')
    await panel(page).getByRole('link', { name: 'Whole Brain setup', exact: true }).click()
    await page.waitForURL(url => url.pathname === '/smart-brains/brain-37')
    await panel(page).getByText('Tell me what your app should do, or share its API documentation.', { exact: false }).waitFor()
    await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    assert.equal(calls.filter(call => call.path.endsWith('/sessions')).at(-1).input.task, 'BRAIN_SETUP')
    await panel(page).getByRole('textbox', { name: 'Message', exact: true }).fill('Set up my app from its documentation')
    await panel(page).getByRole('button', { name: 'Prepare my Brain', exact: true }).click()
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1)
    assert.equal(calls.filter(call => /publish|\/apply$/.test(call.path)).length, 0)
  }, { path: '/smart-brains/brain-37/models?real&targetProfileId=target-stage' })
  for (const status of ['FAILED', 'CANCELLED']) {
    await run(`configured workflow preserves actual saves and undo after ${status.toLowerCase()} narration`, async ({ page, calls, finish }) => {
      await open(page); await send(page, 'Update only the greeting'); finish();
      const p = panel(page);
      await p.locator(`[data-assistant-turn-status="${status}"]`).waitFor();
      await page.waitForFunction(() => [...document.querySelectorAll('input')].some(input => input.value === 'AI saved value'));
      assert.equal(await p.getByRole('region', { name: 'Saved operations' }).count(), 1);
      await p.getByText('4 tool steps · 1 write attempt · 5 model calls · 0 corrections', { exact: true }).waitFor();
      await p.getByText('Work stopped: WORKFLOW_MODEL_CALL_LIMIT. Review any saved operations before continuing.', { exact: true }).waitFor();
      assert.equal(await p.getByText('Validation feedback (0 attempts)', { exact: true }).count(), 0);
      await p.getByRole('button', { name: 'Undo saved changes', exact: true }).click();
      await p.locator('[data-assistant-turn-status="UNDONE"]').waitFor();
      assert.equal(calls.filter(call => call.path.endsWith('/undo')).length, 1);
      assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 1);
      await page.screenshot({ path: join(evidence, `configured-partial-${status.toLowerCase()}-390.png`) });
    }, { result: status, viewport: { width: 390, height: 844 }, dark: status === 'FAILED',
      workflow: { state: 'STOPPED', stopCode: 'WORKFLOW_MODEL_CALL_LIMIT', usage: { actions: 4, writes: 1, modelCalls: 5, corrections: 0 } },
      operations: [{ operationId: 'turn-1:step:3', action: 'authoring_update_draft', status: 'EXECUTED', published: false,
        receipt: { draftId: 'draft-same', published: false } }] })
  }
  await run('real Kit details guide binds the saved Kit Brain and links to real resource editors', async ({ page, calls }) => {
    await page.getByRole('button', { name: 'Kit AI guide', exact: true }).click(); const p = panel(page)
    await p.getByRole('textbox', { name: 'Message', exact: true }).waitFor(); await page.waitForFunction(() => !document.querySelector('textarea[aria-label="Message"]').disabled)
    await turnOptions(page)
    assert.deepEqual(calls.find(call => call.path.endsWith('/sessions')).input, { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'KIT' })
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isDisabled(), false)
    assert.equal(await p.getByRole('checkbox', { name: 'Let AI fix validation errors' }).isChecked(), true)
    await p.getByText('Open resource editor', { exact: true }).click()
    assert.equal(await p.getByRole('link', { name: 'Specialists', exact: true }).getAttribute('href'), '/smart-brains/brain-37/human-support/specialists')
    assert.equal(await p.getByRole('link', { name: 'Actions', exact: true }).getAttribute('href'), '/smart-brains/brain-37/capabilities/actions')
    await p.getByRole('textbox', { name: 'Message', exact: true }).fill('Help me configure this Kit'); await p.getByRole('button', { name: 'Explain', exact: true }).click()
    assert.equal(calls.find(call => call.path.endsWith('/turns')).input.previewOnly, true)
    assert.deepEqual(calls.find(call => call.path.endsWith('/turns')).input.validationFeedback, { enabled: true, maxCorrections: 2 })
    assert.equal(calls.filter(call => /\/apply$|publish|activate|disable/.test(call.path)).length, 0)
    await page.screenshot({ path: join(evidence, 'kit-guide-real-details.png') })
  }, { path: '/kits/kit-37' })
  await run('workflow editor saves prompts, bounded limits and tool switches without dropping unknown configuration', async ({ page, calls }) => {
    const section = page.getByRole('region', { name: 'Configuration workflows', exact: true })
    await section.getByLabel('Instructions', { exact: true }).fill('Use the actual receipts and explain the saved draft changes.')
    await section.getByLabel('Write attempts', { exact: true }).fill('2')
    const writer = section.getByRole('checkbox', { name: /authoring_update_draft/ })
    await writer.uncheck(); await writer.check()
    await section.getByRole('button', { name: 'Save workflow draft', exact: true }).click()
    await section.getByText('Workflow draft saved. Publish and apply to update the assistant.', { exact: true }).waitFor()
    const saved = calls.find(call => call.path === '/api/deployment-drafts/draft-same' && call.method === 'PUT').input
    assert.deepEqual(saved.behaviorConfig.futureBehavior, { keep: true })
    const workflow = saved.behaviorConfig.workflowAssistance
    assert.deepEqual(workflow.futureWorkflow, { keep: true })
    assert.deepEqual(workflow.profiles[0].futureProfile, { keep: true })
    assert.equal(workflow.policy.maxWriteSteps, 2)
    assert.equal(workflow.profiles[0].instructions, 'Use the actual receipts and explain the saved draft changes.')
    assert.deepEqual(workflow.profiles[0].writeActions, ['authoring_update_draft'])
    assert.equal('compiled' in workflow, false); assert.equal('contentHash' in workflow, false)
    assert.equal('goal' in workflow, false, 'Editing a bounded workflow does not invent a goal override')
    assert.equal(calls.filter(call => /publish|\/apply$|\/turns$/.test(call.path)).length, 0)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.screenshot({ path: join(evidence, 'workflow-editor-390.png'), fullPage: true })
  }, { path: '/smart-brains/brain-37/responses?workflowForm', viewport: { width: 390, height: 844 }, workflowDraft: {
    id: 'draft-same', deploymentId: 'brain-37', behaviorConfig: { type: 'CONVERSATIONAL', futureBehavior: { keep: true }, workflowAssistance: {
      enabled: true, defaultProfileId: 'api', profiles: [{ id: 'api', name: 'API capabilities', purpose: 'Prepare requested capabilities.', instructions: 'Read the selected API documentation.',
        readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'], futureProfile: { keep: true } }],
      taskBindings: [{ task: 'ACTIONS', profileId: 'api' }], policy: { maxActionSteps: 8, maxWriteSteps: 3, maxModelCalls: 16, maxCorrections: 2, deadlineSeconds: 120 },
      futureWorkflow: { keep: true }, compiled: { generated: true }, contentHash: 'server-owned' } } } })
  await run('workflow goal policy and delegation save losslessly beyond the bounded correction ceiling', async ({ page, calls }) => {
    const section = page.getByRole('region', { name: 'Configuration workflows', exact: true })
    assert.equal(await section.getByRole('checkbox', { name: 'Enable autonomous goals' }).isChecked(), true)
    assert.equal(await section.getByLabel('Automatic corrections', { exact: true }).inputValue(), '4')
    assert.equal(await section.getByLabel('Automatic corrections', { exact: true }).getAttribute('max'), '8')
    await section.getByLabel('Automatic corrections', { exact: true }).fill('6')
    await section.getByLabel('Model calls', { exact: true }).fill('32')
    await section.getByLabel('Goal lifetime (seconds)', { exact: true }).fill('172800')
    await section.getByLabel('Continuation delay (seconds)', { exact: true }).fill('3')
    await section.getByLabel('No-progress pause threshold', { exact: true }).fill('12')
    await section.getByLabel('Planner-failure pause threshold', { exact: true }).fill('6')
    await section.getByRole('combobox', { name: /Delegated profiles/ }).click()
    await page.getByRole('option', { name: 'Response specialist', exact: true }).click(); await page.keyboard.press('Escape')
    await section.getByRole('button', { name: 'Save workflow draft', exact: true }).click()
    await section.getByText('Workflow draft saved. Publish and apply to update the assistant.', { exact: true }).waitFor()
    const saved = calls.find(call => call.path === '/api/deployment-drafts/draft-same' && call.method === 'PUT').input
    const workflow = saved.behaviorConfig.workflowAssistance
    assert.deepEqual(workflow.goal, { enabled: true, maxElapsedSeconds: 172800, continuationDelaySeconds: 3, maxConsecutiveNoProgress: 12, maxConsecutivePlannerFailures: 6, futureGoal: { keep: true } })
    assert.equal(workflow.policy.maxCorrections, 6); assert.equal(workflow.policy.maxModelCalls, 32)
    assert.deepEqual(workflow.profiles[0].delegatedProfiles, ['responses'])
    assert.deepEqual(workflow.profiles[0].futureProfile, { keep: true })
    assert.deepEqual(saved.behaviorConfig.futureBehavior, { keep: true })
    assert.equal(calls.filter(call => /publish|\/apply$|\/turns$/.test(call.path)).length, 0)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.screenshot({ path: join(evidence, 'workflow-goal-policy-390.png'), fullPage: true })
  }, { path: '/smart-brains/brain-37/responses?workflowForm', viewport: { width: 390, height: 844 }, workflowDraft: {
    id: 'draft-same', deploymentId: 'brain-37', behaviorConfig: { type: 'CONVERSATIONAL', futureBehavior: { keep: true }, workflowAssistance: {
      enabled: true, defaultProfileId: 'api', profiles: [
        { id: 'api', name: 'API capabilities', purpose: 'Prepare requested capabilities.', instructions: 'Read the selected API documentation.', readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'], futureProfile: { keep: true } },
        { id: 'responses', name: 'Response specialist', purpose: 'Prepare response settings.', instructions: 'Use the requested tone.', readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'] },
      ], taskBindings: [{ task: 'ACTIONS', profileId: 'api' }], policy: { maxActionSteps: 8, maxWriteSteps: 3, maxModelCalls: 16, maxCorrections: 4, deadlineSeconds: 120 },
      goal: { enabled: true, maxElapsedSeconds: 86400, continuationDelaySeconds: 1, maxConsecutiveNoProgress: 8, maxConsecutivePlannerFailures: 4, futureGoal: { keep: true } },
    } } } })
  await run('workflow absent goal policy is explicit and enabling autonomy preserves current segment settings', async ({ page, calls }) => {
    const section = page.getByRole('region', { name: 'Configuration workflows', exact: true })
    await section.getByText('Not configured', { exact: true }).waitFor()
    assert.equal(await section.getByLabel('Goal lifetime (seconds)', { exact: true }).count(), 0)
    assert.equal(calls.filter(call => call.method === 'PUT').length, 0)
    await section.getByRole('checkbox', { name: 'Enable autonomous goals' }).check()
    assert.equal(await section.getByLabel('Goal lifetime (seconds)', { exact: true }).inputValue(), '86400')
    assert.equal(await section.getByLabel('Automatic corrections', { exact: true }).inputValue(), '2', 'Enabling goal mode does not change current segment values')
    assert.equal(await section.getByLabel('Automatic corrections', { exact: true }).getAttribute('max'), '8')
    await section.getByLabel('Automatic corrections', { exact: true }).fill('4')
    await section.getByRole('button', { name: 'Save workflow draft', exact: true }).click()
    await section.getByText('Workflow draft saved. Publish and apply to update the assistant.', { exact: true }).waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/turns')).length, 0)
  }, { path: '/smart-brains/brain-37/responses?workflowForm', workflowDraft: {
    id: 'draft-same', deploymentId: 'brain-37', behaviorConfig: { type: 'CONVERSATIONAL', workflowAssistance: {
      enabled: true, defaultProfileId: 'api', profiles: [{ id: 'api', name: 'API capabilities', purpose: 'Prepare requested capabilities.', instructions: 'Read the selected API documentation.', readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'] }],
      taskBindings: [{ task: 'ACTIONS', profileId: 'api' }], policy: { maxActionSteps: 8, maxWriteSteps: 3, maxModelCalls: 16, maxCorrections: 2, deadlineSeconds: 120 },
    } } } })
  await run('workflow goal off preserves raw segment limits and delegates while showing narrowed Quick edit limits', async ({ page, calls }) => {
    const section = page.getByRole('region', { name: 'Configuration workflows', exact: true })
    await section.getByRole('checkbox', { name: 'Enable autonomous goals' }).uncheck()
    assert.equal(await section.getByLabel('Automatic corrections', { exact: true }).inputValue(), '4')
    assert.equal(await section.getByLabel('Automatic corrections', { exact: true }).getAttribute('aria-invalid'), 'false')
    await section.getByText('Stored segment limits', { exact: true }).waitFor()
    const effective = section.getByRole('region', { name: 'Effective Quick edit limits', exact: true })
    await effective.getByText('2', { exact: true }).waitFor(); await effective.getByText('16', { exact: true }).waitFor()
    assert.equal(await section.getByRole('combobox', { name: /Stored delegated profiles/ }).innerText(), 'Response specialist')
    assert.equal(await section.getByRole('button', { name: 'Save workflow draft', exact: true }).isEnabled(), true)
    assert.equal(calls.filter(call => call.method === 'PUT').length, 0)
    await section.getByRole('button', { name: 'Save workflow draft', exact: true }).click()
    await section.getByText('Workflow draft saved. Publish and apply to update the assistant.', { exact: true }).waitFor()
    const workflow = calls.find(call => call.path === '/api/deployment-drafts/draft-same' && call.method === 'PUT').input.behaviorConfig.workflowAssistance
    assert.equal(workflow.goal.enabled, false)
    assert.deepEqual(workflow.policy, { maxActionSteps: 64, maxWriteSteps: 32, maxModelCalls: 64, maxCorrections: 4, deadlineSeconds: 140 })
    assert.deepEqual(workflow.profiles[0].delegatedProfiles, ['responses'])
    assert.equal(calls.filter(call => /publish|\/apply$|\/turns$/.test(call.path)).length, 0)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.screenshot({ path: join(evidence, 'workflow-goal-off-preserved-390.png'), fullPage: true })
  }, { path: '/smart-brains/brain-37/responses?workflowForm', viewport: { width: 390, height: 844 }, workflowDraft: {
    id: 'draft-same', deploymentId: 'brain-37', behaviorConfig: { type: 'CONVERSATIONAL', workflowAssistance: {
      enabled: true, defaultProfileId: 'api', profiles: [
        { id: 'api', name: 'API capabilities', purpose: 'Prepare requested capabilities.', instructions: 'Read the selected API documentation.', readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'], delegatedProfiles: ['responses'] },
        { id: 'responses', name: 'Response specialist', purpose: 'Prepare response settings.', instructions: 'Use the requested tone.', readActions: ['authoring_get_context'], writeActions: ['authoring_update_draft'] },
      ], taskBindings: [{ task: 'ACTIONS', profileId: 'api' }], policy: { maxActionSteps: 64, maxWriteSteps: 32, maxModelCalls: 64, maxCorrections: 4, deadlineSeconds: 140 },
      goal: { enabled: true, maxElapsedSeconds: 86400, continuationDelaySeconds: 1, maxConsecutiveNoProgress: 8, maxConsecutivePlannerFailures: 4 },
    } } } })
  console.log(`PASS ${count} browser groups. Fixture-only evidence: ${evidence}`)
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
