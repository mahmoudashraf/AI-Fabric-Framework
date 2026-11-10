import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

// Real page, shell, draft hooks and API clients. All APIs below are in-memory fixtures.
const root = fileURLToPath(new URL('..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'managed-tasks-vite-'))
const output = await mkdtemp(join(tmpdir(), 'managed-tasks-evidence-'))
const task = { code: 'assess-request', name: 'Assess a request', description: 'Route a service request to the right team.',
  instructions: 'Use the supplied request. Return your recommendation and explain any missing information.',
  inputSchema: { type: 'object', properties: { message: { type: 'string', minLength: 2 } }, required: ['message'], additionalProperties: false },
  outputMode: 'JSON', outputSchema: { type: 'object', properties: { answer: { type: 'string' } }, required: ['answer'] },
  vectorSpaces: [], readActions: [], writeActions: [], confirmationMode: 'CALLER', deadlineSeconds: 45, maxOutputTokens: 1200 }
const resources = {
  reviewAvailable: true,
  knowledgeCollections: [{ id: 'document', name: 'Approved guide' }],
  actions: [{ id: 'read_account', name: 'Read account', accessMode: 'READ', confirmationRequired: false },
    { id: 'update_account', name: 'Update account', accessMode: 'WRITE', confirmationRequired: false },
    { id: 'send_message', name: 'Send message', accessMode: 'WRITE', confirmationRequired: true }],
  specialists: [{ ref: 'service-specialist', name: 'Service specialist', description: 'Existing service expertise',
    inputSchema: task.inputSchema, outputSchema: task.outputSchema, outputMode: 'JSON', vectorSpaces: ['document'],
    readActions: ['read_account'], writeActions: ['send_message'], deadlineSeconds: 90, maxOutputTokens: 2000 }],
}
const install = { id: 'install', deploymentId: 'brain', pluginId: 'plugin', pluginVersionId: 'plugin-v1', pluginVersion: '1.0.0',
  pluginDisplayName: 'Support tools', status: 'ENABLED', entitlement: { entitledForCompilation: true } }
const provenance = { marketplaceManaged: true, marketplaceInstallId: 'install', marketplacePluginId: 'plugin', marketplacePluginVersion: '1.0.0' }
const manifest = { id: 'plugin-v1', pluginId: 'plugin', version: '1.0.0', status: 'PUBLISHED', manifest: { contributions: {
  actions: [{ actionId: 'read_account', readOnly: true }, { actionId: 'update_account', accessMode: 'WRITE_ONLY' }],
  knowledgeSources: [{ id: 'guide', entityType: 'document' }],
} } }
function fixture(options = {}) {
  const saved = { id: 'draft', deploymentId: 'brain', revisionNumber: 3, status: 'DRAFT',
    behaviorConfig: { type: 'CONVERSATIONAL', executionExtensions: ['CONVERSATION_HANDOVER'], conversationHandover: { enabled: true },
      managedTasks: { enabled: !options.empty && !options.disabledDraft, tasks: options.empty ? [] : [structuredClone(task)], compiled: { serverOwned: true }, contentHash: 'server-owned' } },
    actionsConfig: { actions: [{ name: 'read_account', displayName: 'Read account', accessMode: 'READ', ...provenance },
      { name: 'manual_read', accessMode: 'READ' }, { name: 'update_account', accessMode: 'WRITE_ONLY', ...provenance }] },
    entityConfig: { 'ai-entities': { unrelated: {} } }, knowledgeSourceConfig: { sources: [{ id: 'guide', displayName: 'Approved guide', entityType: 'document', enabled: true, ...provenance }] } }
  if (options.pendingTaskConfig) Object.assign(saved.behaviorConfig.managedTasks.tasks[0], {
    instructions: 'DRAFT_ONLY_INSTRUCTIONS', inputSchema: { type: 'object', properties: { draftOnly: { type: 'boolean' } }, required: ['draftOnly'] },
  })
  if (options.unknownResources) Object.assign(saved.behaviorConfig.managedTasks.tasks[0], {
    vectorSpaces: ['missing-space'], readActions: ['missing-read'], writeActions: ['missing-write'],
  })
  if (options.specialistRef) saved.behaviorConfig.managedTasks.tasks[0] = {
    code: task.code, name: task.name, description: task.description, deadlineSeconds: 45, specialistRef: options.specialistRef,
  }
  if (options.executedWrite || options.unknownWrite) saved.behaviorConfig.managedTasks.tasks[0].writeActions = ['update_account']
  if (options.humanReview) Object.assign(saved.behaviorConfig.managedTasks.tasks[0], { writeActions: ['update_account'], confirmationMode: 'HUMAN_REVIEW' })
  if (options.twoTasks) saved.behaviorConfig.managedTasks.tasks.push({ ...structuredClone(task), code: 'other-task', name: 'Other task' })
  return { saved, options, failSave: false, changed: false, failExecute: false, canonicalFailure: false,
    appliedVersion: 'v1', appliedHash: 'tasks-hash', metadataReads: 0, resources: { ...structuredClone(resources), reviewAvailable: !options.noReview } }
}
function workspace(state) {
  return { access: { canEdit: !state.options.readonly, canOperate: !state.options.noOperate, canAdmin: true },
    deployment: { id: 'brain', name: 'Service intelligence', binding: { customerId: 'customer' }, deploymentPolicy: null },
    draft: { id: 'draft', revisionNumber: state.saved.revisionNumber },
    lifecycle: { savedDraftMatchesLatestPublished: !state.changed && !state.options.newer,
      latestPublishedVersionId: state.options.latestPublishedVersionId ?? 'v1' }, latestVersion: { id: state.options.latestPublishedVersionId ?? 'v1' } }
}
function result(state) {
  const pending = state.options.pendingProposal
  return { runId: 'run-fixture', taskCode: 'assess-request', status: state.options.resultStatus ?? (pending ? 'CONFIRMATION_REQUIRED' : state.canonicalFailure ? 'FAILED' : 'SUCCEEDED'),
    configurationVersion: 'v1', taskConfigurationHash: 'tasks-hash', outputSchemaVersion: '1',
    output: state.canonicalFailure ? null : { answer: 'Route to the integration team; the API authorisation details are missing.' },
    failure: state.canonicalFailure ? { code: 'TASK_OUTPUT_INVALID', message: 'The generated output did not match the task schema.' } : null,
    evidence: [{ evidenceId: 'guide-1', source: 'Approved guide', vectorSpace: 'document' }],
    sideEffectsExecuted: Boolean(state.options.executedWrite),
    sideEffectsStatus: state.options.unknownWrite ? 'OUTCOME_UNKNOWN' : state.options.executedWrite ? 'EXECUTED' : 'NONE',
    actionExecutions: state.options.executedWrite || state.options.unknownWrite ? [{ executionId: 'write-1', actionName: 'update_account',
      status: state.options.unknownWrite ? 'OUTCOME_UNKNOWN' : 'SUCCEEDED', outcome: { receipt: 'actual-write-receipt' },
      startedAt: '2026-11-10T12:00:00Z', ...(state.options.executedWrite ? { completedAt: '2026-11-10T12:00:01Z' } : {}) }] : [],
    actionProposal: pending ? { receiptId: 'proposal-1', actionName: 'send_message', confirmationMessage: 'Send this service message?',
      status: state.options.proposalStatus ?? 'PROPOSED', createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + (state.options.expired ? -60000 : state.options.proposalLifetimeMs ?? 600000)).toISOString() } : null,
    ...(state.options.resultExpiryMs !== undefined ? { expiresAt: new Date(Date.now() + state.options.resultExpiryMs).toISOString() } : {}),
    review: state.options.resultStatus === 'REVIEW_REQUIRED' ? { taskId: 'review-existing-1', policyId: { name: 'existing-policy', version: '1' },
      type: 'ACTION_CONFIRMATION', title: 'Review service message', summary: 'Actual pending review from the review service.',
      status: 'PENDING', allowedDecisions: ['APPROVE', 'REJECT'], createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 600000).toISOString(), version: 0 } : null,
    inputRequirement: state.options.resultStatus === 'INPUT_REQUIRED' ? { message: 'Which account should receive the message?' } : null,
    startedAt: '2026-11-10T12:00:00Z', completedAt: '2026-11-10T12:00:01Z' }
}
function handle(state, method, url, body) {
  const path = url.pathname
  const send = (body, status = 200) => ({ body, status })
  if (path === '/api/deployments/brain/draft') return send(state.saved)
  if (path === '/api/deployments/brain/workspace') return send(workspace(state))
  if (path === '/api/deployment-provider/target-profiles' || path === '/api/deployments/brain/target-profiles') return send([
    { id: 'target', name: 'Customer staging', environmentName: 'staging', active: true, defaultForRuntime: true },
    ...(state.options.twoTargets ? [{ id: 'target2', name: 'Customer production', environmentName: 'production', active: true }] : []),
  ])
  if (path === '/api/support/workspace') return send({ message: 'Support disabled in this isolated local fixture.' }, 503)
  if (path === '/api/deployments/brain/releases') return send(state.options.noRelease ? [] : [
    { id: 'release', deploymentVersionId: 'v1', targetProfileId: 'target', createdAt: '2026-11-10', status: 'APPLIED_VERIFIED', provisioningStatus: 'ACTIVE' },
    ...(state.options.twoTargets ? [{ id: 'release2', deploymentVersionId: 'v1', targetProfileId: 'target2', createdAt: '2026-11-10', status: 'APPLIED_VERIFIED', provisioningStatus: 'ACTIVE' }] : []),
  ])
  if (path === '/api/deployments/brain/versions') return send([{ id: 'v1', versionLabel: 'v1', publishedAt: '2026-11-10' }])
  if (path === '/api/deployment-drafts/draft' && method === 'PUT') {
    if (state.failSave) { state.failSave = false; return send({ message: 'Draft save rejected.', errorCode: 'CONFLICT' }, 409) }
    // Production keeps this revision identity across saves; it is not an edit ETag.
    Object.assign(state.saved, body); state.changed = true
    return send(state.saved)
  }
  if (path === '/api/deployments/brain/tasks/applied') {
    assert.ok(['target', ...(state.options.twoTargets ? ['target2'] : [])].includes(url.searchParams.get('targetProfileId')))
    state.metadataReads++
    if (state.options.metadataError) return send({ message: 'Applied configuration unavailable.' }, 503)
    return send({ configurationVersion: state.appliedVersion, taskConfigurationHash: state.appliedHash, runtimeBaseUrl: 'https://runtime.example.test', tasks: state.options.missingTask ? [] : [task] })
  }
  if (path === '/api/deployments/brain/tasks/resources') {
    assert.equal(url.search, '')
    return state.options.resourceError ? send({ message: 'Resource catalogue unavailable.' }, 503) : send(state.resources)
  }
  if (path === '/api/deployments/brain/tasks/assess-request/action-proposals/proposal-1/decisions' && method === 'POST') {
    assert.equal(url.searchParams.get('targetProfileId'), 'target')
    assert.equal(url.searchParams.get('expectedVersion'), state.appliedVersion)
    assert.ok(['CONFIRM', 'REJECT'].includes(body.decision)); assert.deepEqual(Object.keys(body), ['decision'])
    if (state.options.decisionDenied) return send({ message: 'Action decision denied.' }, 403)
    if (state.options.decisionStaleDenial) return send({ message: 'Action decision is stale.' }, 409)
    const status = body.decision === 'REJECT' ? 'REJECTED' : 'decisionStatus' in state.options ? state.options.decisionStatus : 'SUCCEEDED'
    return send({ taskCode: 'assess-request', configurationVersion: 'v1', taskConfigurationHash: 'tasks-hash',
      status: state.options.decisionResponseStatus ?? status ?? 'FAILED',
      failure: state.options.decisionResponseFailure ?? null, actionExecutions: state.options.decisionExecutions ?? [],
      ...state.options.decisionIdentity,
      decision: state.options.noDecision ? null : { receiptId: 'proposal-1', status,
        ...(state.options.decisionFailure ? { failure: state.options.decisionFailure }
          : body.decision === 'CONFIRM' ? { outcome: { receipt: 'confirmed-write-receipt' } } : {}) } }, state.options.decisionHttpStatus ?? 200)
  }
  if (path === '/api/deployments/brain/tasks/assess-request/execute' && method === 'POST') {
    assert.equal(url.searchParams.get('targetProfileId'), 'target')
    assert.equal(url.searchParams.get('expectedVersion'), state.appliedVersion)
    if (state.failExecute) return send({ message: 'Execution access denied.' }, 403)
    return send(result(state), state.options.executionHttpStatus ?? (state.canonicalFailure ? 502 : 200))
  }
  if (path === '/api/deployments/brain/marketplace-installs') return send([install])
  if (path === '/api/marketplace/plugins/plugin/versions/1.0.0') return send(manifest)
  return send({ message: `Unexpected fixture request: ${method} ${path}` }, 404)
}
const previewState = fixture()
const server = await createServer({ root, cacheDir, configFile: false, envFile: false,
  resolve: { dedupe: ['react', 'react-dom'] }, server: { host: '127.0.0.1', port: 0, hmr: false }, logLevel: 'error',
  plugins: [{ name: 'managed-task-memory-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__tasks-entry.jsx') return '\0tasks-entry.jsx'
      if (id.endsWith('/workspace/DeploymentWorkspaceContext')) return '\0tasks-workspace.js'
      if (id.endsWith('/auth/PlatformAuthProvider')) return '\0tasks-auth.js'
    },
    async load(id) {
      if (id === '\0tasks-workspace.js') return `import {useQuery} from '@tanstack/react-query';import {fetchDeploymentWorkspace} from '/src/api/platformApi.ts';
        export function useDeploymentWorkspace(){const q=useQuery({queryKey:['deployment-workspace','brain'],queryFn:()=>fetchDeploymentWorkspace('brain')});return {workspace:q.data,selectedDeploymentId:'brain',selectedDeploymentSummary:q.data?.deployment,deploymentsLoading:q.isPending}}`
      if (id === '\0tasks-auth.js') return `export function usePlatformAuth(){const session={user:{id:'fixture-subject'},access_token:'memory-fixture'};return {session:window.__taskSession,supabaseSession:session,supabase:{auth:{getSession:async()=>({data:{session},error:null})}}}};export function PlatformAuthProvider({children}){return children}`
      if (id !== '\0tasks-entry.jsx') return
      return (await transformWithEsbuild(`
        import React from 'react';import {createRoot} from 'react-dom/client';
        import {createBrowserRouter,RouterProvider} from 'react-router-dom';
        import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
        import {setPlatformAccessTokenProvider} from '/src/api/platformApi.ts';
        import {SmartBrainShell} from '/src/brain/SmartBrainShell.tsx';import {BrainTasksPage} from '/src/brain/BrainTasksPage.tsx';
        const params=new URLSearchParams(location.search);localStorage.setItem('loomai-customer-console-color-scheme',params.get('theme')||'dark');
        setPlatformAccessTokenProvider(async()=> 'memory-fixture');
        const client=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}});
        const session={role:params.has('customer')?'CUSTOMER_ADMIN':'OPERATOR',authenticated:true,authenticationMode:'SUPABASE',displayName:'Local reviewer',actorId:'fixture',customerId:'customer'};window.__taskSession=session;
        const router=createBrowserRouter([{path:'*',element:<SmartBrainShell session={session} onSignOut={async()=>{}}><BrainTasksPage/></SmartBrainShell>}]);
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider>);
      `, 'tasks-entry.jsx', { loader: 'jsx', jsx: 'automatic' })).code
    },
    configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
      const url = new URL(req.url, 'http://fixture')
      if (url.pathname.startsWith('/api/')) {
        let raw = ''; for await (const chunk of req) raw += chunk
        const response = handle(previewState, req.method, url, raw ? JSON.parse(raw) : null)
        res.statusCode = response.status; res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(response.body)); return
      }
      if (!url.pathname.startsWith('/smart-brains/')) return next()
      res.setHeader('Content-Type', 'text/html')
      res.end(await vite.transformIndexHtml('/__tasks', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin};</script><script type="module" src="/__tasks-entry.jsx"></script></body></html>'))
    }) },
  }],
})
await server.listen()
const base = server.resolvedUrls.local[0].replace(/\/$/, '')
let browser, passed = 0
async function run(name, verify, options = {}) {
  if (process.env.MANAGED_TASKS_BROWSER_FILTER && !name.includes(process.env.MANAGED_TASKS_BROWSER_FILTER)) return
  const context = await browser.newContext({ viewport: options.viewport ?? { width: 1440, height: 1000 } })
  const page = await context.newPage()
  if (options.clock) await page.clock.install()
  page.setDefaultTimeout(20000)
  const state = fixture(options), calls = [], errors = [], unexpected = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method(), body = request.postDataJSON()
    calls.push({ path: url.pathname, method, body, search: url.search })
    const response = handle(state, method, url, body)
    if (response.status === 404) unexpected.push(response.body.message)
    await route.fulfill({ status: response.status, contentType: 'application/json', body: JSON.stringify(response.body) })
  })
  try {
    await page.goto(`${base}/smart-brains/brain/tasks${options.empty ? '' : '/assess-request'}?targetProfileId=target&theme=${options.theme ?? 'dark'}${options.customer ? '&customer=1' : ''}`)
    await page.getByRole('heading', { name: 'Managed Tasks', exact: true }).waitFor()
    await page.getByRole('switch', { name: 'Enable Managed Tasks' }).waitFor()
    await verify({ page, state, calls })
    assert.deepEqual(errors, []); assert.deepEqual(unexpected, [])
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
    await page.evaluate(() => Promise.all(document.getAnimations().filter(animation => animation.playState === 'running'
      && Number.isFinite(animation.effect?.getTiming().iterations)).map(animation => animation.finished.catch(() => {}))))
    await page.screenshot({ path: join(output, `${++passed}-${name.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.png`), fullPage: true })
    console.log(`PASS ${name}`)
  } catch (error) {
    await page.screenshot({ path: join(output, 'failure.png'), fullPage: true }); console.error({ name, errors, unexpected, output }); throw error
  } finally { await context.close() }
}
const saves = calls => calls.filter(call => call.method === 'PUT')
const executions = calls => calls.filter(call => call.path.endsWith('/execute'))
const appliedReads = calls => calls.filter(call => call.path.endsWith('/applied'))
const decisions = calls => calls.filter(call => call.path.endsWith('/decisions'))
async function fillTask(page) {
  await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('Summarise request')
  await page.getByRole('textbox', { name: 'Task code', exact: false }).fill('summarise-request')
  await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('Prepare a concise service summary.')
  await page.getByRole('textbox', { name: 'Instructions', exact: false }).fill('Summarise the provided input and state what is missing.')
}
try {
  browser = await chromium.launch()
  await run('create save and discard with the real Brain shell', async ({ page, calls, state }) => {
    assert.equal(saves(calls).length, 0); assert.equal(appliedReads(calls).length, 0); assert.equal(executions(calls).length, 0)
    await page.getByRole('switch', { name: 'Enable Managed Tasks' }).check()
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
    await page.getByRole('button', { name: 'Add task', exact: true }).click()
    await fillTask(page)
    await page.getByRole('textbox', { name: 'Input field name', exact: true }).fill('message')
    await page.getByRole('region', { name: 'Input fields', exact: true }).getByRole('button', { name: 'Add field' }).click()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    const body = saves(calls)[0].body
    assert.deepEqual(Object.keys(body), ['behaviorConfig'])
    assert.deepEqual(body.behaviorConfig.conversationHandover, { enabled: true })
    assert.deepEqual(Object.keys(body.behaviorConfig.managedTasks).sort(), ['enabled', 'tasks'])
    assert.equal(body.behaviorConfig.managedTasks.tasks[0].code, 'summarise-request')
    assert.equal(body.behaviorConfig.managedTasks.tasks[0].inputSchema.properties.message.type, 'string')
    await page.getByRole('button', { name: 'Summarise request summarise-request', exact: true }).click()
    await page.waitForURL('**/tasks/summarise-request?**')
    await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('Unsaved title')
    await page.getByRole('button', { name: 'Discard', exact: true }).click()
    await page.getByRole('textbox', { name: 'Task name', exact: false }).filter({ visible: true }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).inputValue(), state.saved.behaviorConfig.managedTasks.tasks[0].name)
    assert.equal(await page.getByRole('dialog').count(), 0)
    assert.equal(appliedReads(calls).length, 0); assert.equal(executions(calls).length, 0)
  }, { empty: true })
  await run('schema editing retains constraints and TEXT omits output schema', async ({ page, calls }) => {
    const input = page.getByRole('region', { name: 'Input fields', exact: true })
    await input.getByRole('switch', { name: 'Advanced JSON' }).check()
    const schema = { type: 'object', properties: { message: { type: 'string', minLength: 8, enum: ['one', 'two'] }, detail: { $ref: '#/$defs/detail' } }, $defs: { detail: { type: 'string' } }, additionalProperties: false }
    await input.getByLabel('Input schema JSON').fill(JSON.stringify(schema))
    await input.getByRole('switch', { name: 'Advanced JSON' }).uncheck()
    await input.getByRole('checkbox', { name: 'Required', exact: true }).first().check()
    await input.getByRole('switch', { name: 'Advanced JSON' }).check()
    const savedSchema = JSON.parse(await input.getByLabel('Input schema JSON').inputValue())
    assert.deepEqual(savedSchema.$defs, schema.$defs); assert.deepEqual(savedSchema.properties.message, schema.properties.message)
    const output = page.getByRole('region', { name: 'Output fields', exact: true })
    await output.getByRole('switch', { name: 'Advanced JSON' }).check()
    await output.getByLabel('Output schema JSON').fill('{unfinished')
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).isDisabled(), true)
    await page.getByRole('combobox', { name: 'Return an answer as' }).click(); await page.getByRole('option', { name: 'Text', exact: true }).click()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    const task = saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0]
    assert.equal(task.outputMode, 'TEXT'); assert.equal('outputSchema' in task, false); assert.deepEqual(task.inputSchema, savedSchema)
  })
  await run('rejected save and same-revision task conflict retain local edits', async ({ page, state, calls }) => {
    await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('Local edit')
    state.failSave = true
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft save rejected.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).inputValue(), 'Local edit')
    state.saved.behaviorConfig.managedTasks.tasks[0].name = 'Other editor'
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Your local edits are retained. Discard loads the newer saved draft.', { exact: true }).waitFor()
    assert.equal(saves(calls).length, 1)
    assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).inputValue(), 'Local edit')
    await page.getByRole('button', { name: 'Discard', exact: true }).click()
    await page.waitForFunction(() => document.querySelector('input[value="Other editor"]'))
  })
  await run('same-revision unrelated behavior edits are preserved when tasks save', async ({ page, state, calls }) => {
    await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('Save my task purpose.')
    state.saved.behaviorConfig.conversationHandover = { enabled: true, contactPolicy: 'NEWER_UNRELATED_SETTING' }
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.equal(state.saved.revisionNumber, 3)
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.conversationHandover, { enabled: true, contactPolicy: 'NEWER_UNRELATED_SETTING' })
    assert.equal(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].description, 'Save my task purpose.')
  })
  await run('changing a task code keeps the editor and its saved route remains selectable', async ({ page, state }) => {
    await page.getByRole('textbox', { name: 'Task code', exact: false }).fill('assess-updated-request')
    assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).inputValue(), 'Assess a request')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Assess a request assess-updated-request', exact: true }).click()
    await page.waitForURL('**/tasks/assess-updated-request?**')
    assert.equal(await page.getByRole('textbox', { name: 'Task code', exact: false }).inputValue(), 'assess-updated-request')
    assert.equal(state.saved.revisionNumber, 3)
    assert.equal(await page.getByRole('dialog').count(), 0)
  })
  await run('task removal is confirmed and only saved through the shared draft', async ({ page, state, calls }) => {
    await page.getByRole('button', { name: 'Remove task', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
    await page.locator('[role="dialog"]').waitFor({ state: 'detached' })
    assert.equal(saves(calls).length, 0)
    await page.getByRole('button', { name: 'Remove task', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Remove task', exact: true }).click()
    await page.getByText('No tasks yet. Add one to define what your service needs.', { exact: true }).waitFor()
    await page.locator('[role="dialog"]').waitFor({ state: 'detached' })
    assert.equal(saves(calls).length, 0)
    await page.getByRole('switch', { name: 'Enable Managed Tasks' }).uncheck()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.deepEqual(state.saved.behaviorConfig.managedTasks, { enabled: false, tasks: [] })
  })
  await run('applied task tests are explicit and return canonical results and failures', async ({ page, state, calls }) => {
    assert.equal(appliedReads(calls).length, 0)
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    assert.equal(appliedReads(calls).length, 1); assert.equal(executions(calls).length, 0)
    await page.getByLabel('Task input JSON').fill('{"message":"Help configure the account"}')
    await page.getByLabel('Correlation ID (optional)').fill('review-123')
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).getByText('SUCCEEDED', { exact: true }).waitFor()
    assert.deepEqual(executions(calls)[0].body, { input: { message: 'Help configure the account' }, correlationId: 'review-123' })
    assert.equal(executions(calls).length, 1)
    state.canonicalFailure = true
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByText('TASK_OUTPUT_INVALID: The generated output did not match the task schema.', { exact: true }).waitFor()
    await page.getByRole('region', { name: 'Task result' }).getByText('FAILED', { exact: true }).waitFor()
    state.failExecute = true
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByText('Execution access denied.', { exact: true }).waitFor()
    assert.equal(executions(calls).length, 3)
  })
  await run('customer pending global draft runs the applied task without using draft settings', async ({ page, state, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByText('This view uses the applied version. Pending changes are not used.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isEnabled(), true)
    await page.getByText('Applied input schema', { exact: true }).click()
    const schema = await page.locator('details').filter({ hasText: 'Applied input schema' }).locator('pre').innerText()
    assert.deepEqual(JSON.parse(schema), task.inputSchema)
    await page.getByLabel('Task input JSON').fill('{"message":"Use only the applied task inputs"}')
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).getByText('SUCCEEDED', { exact: true }).waitFor()
    assert.equal(executions(calls).length, 1)
    assert.deepEqual(executions(calls)[0].body, { input: { message: 'Use only the applied task inputs' } })
    assert.equal(state.saved.behaviorConfig.managedTasks.enabled, false)
    assert.equal(state.saved.behaviorConfig.managedTasks.tasks[0].instructions, 'DRAFT_ONLY_INSTRUCTIONS')
    assert.equal(saves(calls).length, 0)
    await page.getByRole('tab', { name: 'Use in your app', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Copy request', exact: true }).isEnabled(), true)
    assert.equal(executions(calls).length, 1)
  }, { customer: true, newer: true, latestPublishedVersionId: 'v2', disabledDraft: true, pendingTaskConfig: true })
  await run('local unsaved task edits still block testing until saved or discarded', async ({ page, calls }) => {
    await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('Unsaved task title')
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByText('Save or discard your local task edits before testing the applied version.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isDisabled(), true)
    assert.equal(executions(calls).length, 0); assert.equal(saves(calls).length, 0)
  })
  for (const mode of ['appliedVersion', 'appliedHash', 'missingTask', 'permission', 'draftId', 'draftRevision', 'draftTask']) {
    await run('command boundary rejects changed ' + mode, async ({ page, state, calls }) => {
      await page.getByRole('tab', { name: 'Try', exact: true }).click()
      await page.getByText('Applied version: v1', { exact: true }).waitFor()
      await page.getByLabel('Task input JSON').fill('{"message":"Check the current applied context"}')
      if (mode === 'appliedVersion') state.appliedVersion = 'v2'
      if (mode === 'appliedHash') state.appliedHash = 'changed-hash'
      if (mode === 'missingTask') state.options.missingTask = true
      if (mode === 'permission') state.options.noOperate = true
      if (mode === 'draftId') state.saved.id = 'new-draft'
      if (mode === 'draftRevision') state.saved.revisionNumber++
      if (mode === 'draftTask') state.saved.behaviorConfig.managedTasks.tasks[0].description = 'Another editor changed this task.'
      await page.getByRole('button', { name: 'Run task', exact: true }).click()
      const message = mode.startsWith('draft') ? 'The saved task configuration changed. Reload it before testing the applied version.'
        : mode === 'missingTask' ? 'This task is not available in the applied version. Apply an update first.'
          : 'The applied configuration or your access changed. Refresh the applied tasks before running again.'
      await page.getByText(message, { exact: true }).waitFor()
      assert.equal(executions(calls).length, 0); assert.equal(saves(calls).length, 0)
      assert.equal(await page.getByRole('region', { name: 'Task result' }).count(), 0)
    })
  }
  for (const mode of ['missingTask', 'noRelease']) await run('applied gate ' + mode, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    if (mode !== 'noRelease') await page.getByText('Applied version: v1', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isDisabled(), true)
    assert.equal(executions(calls).length, 0)
    if (mode === 'noRelease') assert.equal(appliedReads(calls).length, 0)
  }, { [mode]: true })
  await run('resource API supplies existing knowledge READ and WRITE policies', async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByRole('checkbox', { name: 'Read account', exact: true }).waitFor()
    assert.equal(await page.getByText('manual_read', { exact: true }).count(), 0)
    assert.equal(await page.getByText('unrelated', { exact: true }).count(), 0)
    await page.getByRole('checkbox', { name: 'Read account', exact: true }).check()
    await page.getByRole('checkbox', { name: 'Approved guide', exact: true }).check()
    await page.getByRole('checkbox', { name: 'Update account · No confirmation required', exact: true }).check()
    await page.getByRole('checkbox', { name: 'Send message · Confirmation required', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].readActions, ['read_account'])
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].vectorSpaces, ['document'])
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].writeActions, ['update_account'])
    assert.ok(calls.some(call => call.path.endsWith('/tasks/resources') && call.search === ''))
    assert.equal(calls.some(call => call.path.includes('marketplace')), false)
    assert.equal(executions(calls).length, 0)
  })
  for (const resourceError of [false, true]) await run('unavailable resources stay visible removable and preserved ' + resourceError, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    if (resourceError) await page.getByText(/Resource catalogue unavailable.*Saved selections/).waitFor()
    const missingRead = page.getByRole('checkbox', { name: 'missing-read (not in saved catalogue)', exact: true })
    await missingRead.waitFor()
    assert.equal(await missingRead.isChecked(), true)
    assert.equal(await missingRead.isEnabled(), true)
    await missingRead.click()
    assert.equal(await missingRead.count(), 0)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    const saved = saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0]
    assert.deepEqual(saved.readActions, []); assert.deepEqual(saved.writeActions, ['missing-write']); assert.deepEqual(saved.vectorSpaces, ['missing-space'])
    assert.equal(executions(calls).length, 0)
  }, { resourceError, unknownResources: true })
  await run('specialist inherits read-only contracts resources and preserves manual buffer until saved', async ({ page, calls }) => {
    await page.getByRole('textbox', { name: 'Instructions', exact: false }).fill('Manual instructions retained while switching.')
    await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('My specialist Task')
    await page.getByRole('combobox', { name: 'Existing specialist (optional)' }).click()
    await page.getByRole('option', { name: 'Service specialist', exact: true }).click()
    const inherited = page.getByRole('region', { name: 'Inherited specialist configuration' })
    await inherited.waitFor()
    assert.deepEqual(JSON.parse(await inherited.locator('pre').innerText()).inputSchema, task.inputSchema)
    assert.equal(await page.getByRole('textbox', { name: 'Instructions', exact: false }).count(), 0)
    assert.equal(await page.getByRole('region', { name: 'Input fields', exact: true }).count(), 0)
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    const write = page.getByRole('checkbox', { name: 'Send message · Confirmation required', exact: true })
    assert.equal(await write.isChecked(), true); assert.equal(await write.isDisabled(), true)
    await page.getByRole('tab', { name: 'Outcome', exact: true }).click()
    await page.getByRole('combobox', { name: 'Existing specialist (optional)' }).click()
    await page.getByRole('option', { name: 'Task-authored instructions and contracts', exact: true }).click()
    assert.equal(await page.getByRole('textbox', { name: 'Instructions', exact: false }).inputValue(), 'Manual instructions retained while switching.')
    await page.getByRole('combobox', { name: 'Existing specialist (optional)' }).click()
    await page.getByRole('option', { name: 'Service specialist', exact: true }).click()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0], {
      code: task.code, name: 'My specialist Task', description: task.description, deadlineSeconds: 45, specialistRef: 'service-specialist', confirmationMode: 'CALLER',
    })
    assert.equal(executions(calls).length, 0)
  })
  for (const specialistRef of ['service-specialist', 'missing-specialist']) await run('load reference-only Task ' + specialistRef, async ({ page, calls }) => {
    if (specialistRef === 'service-specialist') await page.getByRole('region', { name: 'Inherited specialist configuration' }).waitFor()
    else {
      await page.getByText(/Specialist context is unavailable/).waitFor()
      assert.equal(await page.getByRole('combobox', { name: 'Existing specialist (optional)' }).innerText(), 'missing-specialist (unavailable)')
    }
    await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('New Task-owned purpose')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.equal(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].specialistRef, specialistRef)
    assert.equal('inputSchema' in saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0], false)
    assert.equal(executions(calls).length, 0)
  }, { specialistRef })
  for (const unknownWrite of [false, true]) await run('no-confirm write receipts survive AI failure ' + unknownWrite, async ({ page, state, calls }) => {
    state.canonicalFailure = true
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByText(/Run task may execute selected write actions immediately/).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    const outcomes = page.getByRole('region', { name: 'Action outcomes' })
    await outcomes.getByText(unknownWrite ? 'Side effects: OUTCOME_UNKNOWN' : 'Side effects: EXECUTED', { exact: true }).waitFor()
    await outcomes.getByText(unknownWrite ? 'update_account: OUTCOME_UNKNOWN' : 'update_account: SUCCEEDED', { exact: true }).waitFor()
    assert.ok((await outcomes.innerText()).includes('actual-write-receipt'))
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(await page.getByRole('dialog').count(), 0)
    assert.equal(executions(calls).length, 1); assert.equal(decisions(calls).length, 0)
  }, { executedWrite: !unknownWrite, unknownWrite })
  for (const decision of ['CONFIRM', 'REJECT']) await run('pending proposal requires explicit ' + decision, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).getByText('CONFIRMATION_REQUIRED', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).waitFor()
    assert.equal(decisions(calls).length, 0)
    await page.getByRole('button', { name: decision === 'CONFIRM' ? 'Confirm action' : 'Reject action', exact: true }).click()
    const receipt = page.getByRole('region', { name: 'Action decision result' })
    await receipt.getByText('Decision status: ' + (decision === 'CONFIRM' ? 'SUCCEEDED' : 'REJECTED'), { exact: true }).waitFor()
    assert.equal(decisions(calls).length, 1); assert.deepEqual(decisions(calls)[0].body, { decision })
    assert.equal(executions(calls).length, 1)
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    if (decision === 'CONFIRM') assert.ok((await receipt.innerText()).includes('confirmed-write-receipt'))
  }, { pendingProposal: true, executionHttpStatus: 409 })
  for (const mode of ['appliedVersion', 'appliedHash', 'permission', 'draftTask', 'missingTask']) await run('decision boundary rejects changed ' + mode, async ({ page, state, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).waitFor()
    if (mode === 'appliedVersion') state.appliedVersion = 'v2'
    if (mode === 'appliedHash') state.appliedHash = 'changed'
    if (mode === 'permission') state.options.noOperate = true
    if (mode === 'draftTask') state.saved.behaviorConfig.managedTasks.tasks[0].description = 'changed'
    if (mode === 'missingTask') state.options.missingTask = true
    await page.getByRole('button', { name: 'Confirm action', exact: true }).click()
    await page.getByText(/No decision was sent. Run again only after reviewing/).waitFor()
    assert.equal(decisions(calls).length, 0); assert.equal(executions(calls).length, 1)
    assert.equal(await page.getByRole('region', { name: 'Task result' }).count(), 0)
  }, { pendingProposal: true })
  for (const mode of ['denied', 'identity', 'unknown']) await run('decision receipt handles ' + mode, async ({ page, state, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).waitFor()
    if (mode === 'denied') state.options.decisionDenied = true
    if (mode === 'identity') state.options.decisionIdentity = { taskConfigurationHash: 'foreign' }
    if (mode === 'unknown') state.options.decisionStatus = 'OUTCOME_UNKNOWN'
    await page.getByRole('button', { name: 'Confirm action', exact: true }).click()
    if (mode === 'unknown') await page.getByRole('region', { name: 'Action decision result' }).getByText('Decision status: OUTCOME_UNKNOWN', { exact: true }).waitFor()
    else await page.getByText(mode === 'denied' ? /Action decision denied/ : /action decision response did not match/).waitFor()
    assert.equal(decisions(calls).length, 1); assert.equal(executions(calls).length, 1)
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
  }, { pendingProposal: true })
  for (const status of ['FAILED', null]) await run('safe action failure receipt ' + status, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).click()
    const receipt = page.getByRole('region', { name: 'Action decision result' })
    await receipt.getByText('ACCESS_DENIED: Current action policy denied the decision.', { exact: true }).waitFor()
    await receipt.getByText('Decision status: ' + (status ?? 'FAILED'), { exact: true }).waitFor()
    assert.equal(decisions(calls).length, 1)
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
  }, { pendingProposal: true, decisionStatus: status, decisionFailure: { reason: 'ACCESS_DENIED', publicMessage: 'Current action policy denied the decision.', retryable: true } })
  for (const unknown of [false, true]) await run('decision transport preserves non-2xx receipts ' + unknown, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).click()
    const receipt = page.getByRole('region', { name: 'Action decision result' })
    await receipt.getByText('Decision status: ' + (unknown ? 'OUTCOME_UNKNOWN' : 'FAILED'), { exact: true }).waitFor()
    await receipt.getByText('send_message: SUCCEEDED', { exact: true }).waitFor()
    if (!unknown) await receipt.getByText('Receipt status: SUCCEEDED', { exact: true }).waitFor()
    assert.ok((await receipt.innerText()).includes('actual-decision-write'))
    await receipt.getByText('TASK_DECISION_FAILED: Stopped waiting for coordinator completion.', { exact: true }).waitFor()
    if (unknown) {
      await receipt.getByText('No decision receipt was returned. Do not assume the action was rejected or had no effect.', { exact: true }).waitFor()
      await receipt.getByText(/The action outcome is unknown. Check the connected system/).waitFor()
    }
    await page.getByText('Run side effects (before decision): NONE', { exact: true }).waitFor()
    assert.equal(await page.getByText('Side effects: NONE', { exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Reject action', exact: true }).count(), 0)
    assert.equal(decisions(calls).length, 1); assert.equal(executions(calls).length, 1)
    await receipt.scrollIntoViewIfNeeded()
  }, { pendingProposal: true, decisionHttpStatus: unknown ? 504 : 409, decisionStatus: unknown ? 'FAILED' : 'SUCCEEDED', noDecision: unknown,
    decisionResponseStatus: unknown ? 'OUTCOME_UNKNOWN' : 'FAILED',
    decisionResponseFailure: { code: 'TASK_DECISION_FAILED', message: 'Stopped waiting for coordinator completion.' },
    decisionExecutions: [{ executionId: 'actual-decision-write', actionName: 'send_message', status: 'SUCCEEDED',
      outcome: { message: 'Actual business write completed.' }, startedAt: '2026-11-10T00:00:00Z', completedAt: '2026-11-10T00:00:01Z' }],
    viewport: unknown ? { width: 390, height: 844 } : undefined })
  await run('decision transport rejects ordinary stale denial', async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).click()
    await page.getByText(/Action decision is stale/).waitFor()
    assert.equal(await page.getByRole('region', { name: 'Action decision result' }).count(), 0)
    assert.equal(decisions(calls).length, 1); assert.equal(executions(calls).length, 1)
  }, { pendingProposal: true, decisionStaleDenial: true })
  for (const resultExpiry of [false, true]) await run('decision transport expiry updates without new inference ' + resultExpiry, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).waitFor()
    await page.clock.fastForward(6000)
    await page.getByText('This proposal has expired. No decision was sent.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Reject action', exact: true }).count(), 0)
    assert.equal(decisions(calls).length, 0); assert.equal(executions(calls).length, 1)
  }, { pendingProposal: true, clock: true, ...(resultExpiry ? { resultExpiryMs: 5000 } : { proposalLifetimeMs: 5000 }) })
  for (const mode of ['expired', 'rejected']) await run('terminal proposals expose no decisions ' + mode, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Reject action', exact: true }).count(), 0)
    assert.equal(decisions(calls).length, 0)
  }, { pendingProposal: true, expired: mode === 'expired', proposalStatus: mode === 'rejected' ? 'REJECTED' : 'PROPOSED' })
  for (const mode of ['config', 'task', 'refresh', 'target']) await run('pending proposals clear on ' + mode, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('button', { name: 'Confirm action', exact: true }).waitFor()
    if (mode === 'refresh') await page.getByRole('button', { name: 'Refresh applied tasks', exact: true }).click()
    if (mode === 'task') await page.getByRole('button', { name: 'Other task other-task', exact: true }).click()
    if (mode === 'target') {
      await page.getByRole('button', { name: 'View Production', exact: true }).click()
      await page.waitForURL('**targetProfileId=target2**')
    }
    if (mode === 'config') {
      await page.getByRole('tab', { name: 'Outcome', exact: true }).click()
      await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('Changed')
      await page.getByRole('tab', { name: 'Try', exact: true }).click()
    }
    await page.getByRole('region', { name: 'Task result' }).waitFor({ state: 'detached' })
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(decisions(calls).length, 0)
  }, { pendingProposal: true, twoTasks: true, twoTargets: true })
  await run('Task confirmation handling changes no existing action flags', async ({ page, state, calls }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByRole('checkbox', { name: 'Update account · No confirmation required', exact: true }).waitFor()
    assert.equal(await page.getByRole('combobox', { name: 'Confirmation handling' }).count(), 0)
    await page.getByRole('checkbox', { name: 'Update account · No confirmation required', exact: true }).check()
    await page.getByRole('combobox', { name: 'Confirmation handling' }).click()
    await page.getByRole('option', { name: 'Human reviewers', exact: true }).click()
    await page.getByText(/Writes without confirmation still execute directly/).waitFor()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    const config = saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0]
    assert.equal(config.confirmationMode, 'HUMAN_REVIEW'); assert.deepEqual(config.writeActions, ['update_account'])
    assert.deepEqual(state.resources.actions, resources.actions)
    assert.deepEqual(Object.keys(saves(calls)[0].body), ['behaviorConfig'])
  })
  await run('Human reviewers choice follows actual review availability', async ({ page }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByRole('checkbox', { name: 'Update account · No confirmation required', exact: true }).check()
    await page.getByRole('combobox', { name: 'Confirmation handling' }).click()
    await page.getByRole('option', { name: 'Your backend', exact: true }).waitFor()
    assert.equal(await page.getByRole('option', { name: 'Human reviewers', exact: true }).count(), 0)
    await page.keyboard.press('Escape')
  }, { noReview: true })
  await run('Unavailable saved human review handling is retained until deliberately changed', async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByText('Human review is unavailable in the current resource catalogue. Your saved selection is retained.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('combobox', { name: 'Confirmation handling' }).innerText(), 'Human reviewers (unavailable)')
    await page.getByRole('tab', { name: 'Outcome', exact: true }).click()
    await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('Preserve review selection')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.equal(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].confirmationMode, 'HUMAN_REVIEW')
  }, { noReview: true, humanReview: true })
  await run('Specialist reference retains Task-owned confirmation handling', async ({ page, calls }) => {
    await page.getByRole('region', { name: 'Inherited specialist configuration' }).waitFor()
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByRole('combobox', { name: 'Confirmation handling' }).click()
    await page.getByRole('option', { name: 'Human reviewers', exact: true }).click()
    assert.equal(await page.getByRole('checkbox', { name: 'Send message · Confirmation required', exact: true }).isDisabled(), true)
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0], {
      code: task.code, name: task.name, description: task.description, deadlineSeconds: 45, specialistRef: 'service-specialist', confirmationMode: 'HUMAN_REVIEW',
    })
  }, { specialistRef: 'service-specialist' })
  await run('Human review setting does not add a gate to no-confirm writes', async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).getByText('SUCCEEDED', { exact: true }).waitFor()
    await page.getByRole('region', { name: 'Action outcomes' }).getByText('Side effects: EXECUTED', { exact: true }).waitFor()
    assert.equal(await page.getByRole('region', { name: 'Pending human review' }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(executions(calls).length, 1); assert.equal(decisions(calls).length, 0)
  }, { humanReview: true, executedWrite: true })
  for (const status of ['REVIEW_REQUIRED', 'INPUT_REQUIRED']) await run('Actual pending state ' + status, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).getByText(status, { exact: true }).waitFor()
    if (status === 'REVIEW_REQUIRED') {
      const review = page.getByRole('region', { name: 'Pending human review' })
      await review.getByText('Review service message', { exact: true }).waitFor()
      assert.ok((await review.innerText()).includes('review-existing-1'))
      assert.ok((await review.getByRole('link', { name: 'Open Action reviews' }).getAttribute('href')).includes('/action-reviews?targetProfileId=target'))
    } else await page.getByText('Missing input: Which account should receive the message?', { exact: true }).waitFor()
    await page.getByRole('region', { name: 'Action outcomes' }).getByText('Side effects: NONE', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: 'Reject action', exact: true }).count(), 0)
    assert.equal(executions(calls).length, 1); assert.equal(decisions(calls).length, 0)
  }, { resultStatus: status, pendingProposal: true, executionHttpStatus: 409 })
  await run('read-only access has no editor writes or inference authority', async ({ page, calls }) => {
    assert.equal(await page.getByRole('button', { name: 'Add task', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Save draft', exact: true }).count(), 0)
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isDisabled(), true)
    assert.equal(saves(calls).length, 0); assert.equal(executions(calls).length, 0)
  }, { readonly: true, noOperate: true })
  await run('same-section navigation keeps buffer and leaving asks before discard', async ({ page }) => {
    await page.getByRole('textbox', { name: 'Task name', exact: false }).fill('Unsaved title')
    await page.getByRole('link', { name: 'Responses', exact: true }).click()
    await page.getByRole('dialog', { name: 'Leave unsaved changes?' }).waitFor()
    await page.getByRole('button', { name: 'Keep editing', exact: true }).click()
    assert.equal(await page.getByRole('textbox', { name: 'Task name', exact: false }).inputValue(), 'Unsaved title')
  })
  await run('runtime integration example uses private auth placeholders', async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Use in your app', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    const text = await page.locator('pre').innerText()
    assert.ok(text.includes('/api/tasks/v1/tasks/assess-request/execute'))
    assert.ok(text.includes('X-AIFABRIC-RUNTIME-API-KEY'))
    assert.ok(text.includes('X-AIFABRIC-RUNTIME-AUTHORIZATION'))
    assert.equal(text.includes('\n+'), false)
    assert.equal(appliedReads(calls).length, 1); assert.equal(executions(calls).length, 0)
  })
  await run('customer admin uses the same shell draft and applied task journey', async ({ page, calls }) => {
    assert.ok(calls.some(call => call.path === '/api/deployments/brain/target-profiles'))
    assert.equal(calls.some(call => call.path === '/api/deployment-provider/target-profiles'), false)
    assert.equal(appliedReads(calls).length, 0); assert.equal(executions(calls).length, 0)
    await page.getByRole('button', { name: 'Open LoomAI support', exact: true }).waitFor()
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByLabel('Task input JSON').fill('{"message":"Prepare an account summary"}')
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    await page.getByRole('region', { name: 'Task result' }).getByText('SUCCEEDED', { exact: true }).waitFor()
    assert.equal(executions(calls).length, 1)
    await page.getByRole('tab', { name: 'Outcome', exact: true }).click()
    await page.getByRole('textbox', { name: 'Purpose', exact: false }).fill('Prepare a summary for the customer service team.')
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.equal(saves(calls).length, 1)
    assert.equal(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].description, 'Prepare a summary for the customer service team.')
  }, { customer: true })
  for (const theme of ['light', 'dark']) await run('mobile navigation and fields ' + theme, async ({ page }) => {
    await page.getByRole('textbox', { name: 'Task code', exact: false }).waitFor()
    await page.getByRole('button', { name: 'Open Smart Brain navigation' }).click()
    await page.getByRole('link', { name: 'Managed Tasks', exact: true }).waitFor()
    await page.keyboard.press('Escape')
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).waitFor({ state: 'visible' })
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.textContent === 'Run task' && !button.disabled))
  }, { theme, viewport: { width: 390, height: 844 } })
  for (const theme of ['light', 'dark']) await run('mobile write confirmation and human review ' + theme, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByRole('combobox', { name: 'Confirmation handling' }).waitFor()
    assert.equal(await page.getByRole('combobox', { name: 'Confirmation handling' }).innerText(), 'Human reviewers')
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    await page.getByText('Applied version: v1', { exact: true }).waitFor()
    await page.getByRole('button', { name: 'Run task', exact: true }).click()
    if (theme === 'light') {
      await page.getByRole('button', { name: 'Confirm action', exact: true }).waitFor()
      await page.getByRole('button', { name: 'Reject action', exact: true }).click()
      await page.getByRole('region', { name: 'Action decision result' }).getByText('Decision status: REJECTED', { exact: true }).waitFor()
      await page.getByRole('region', { name: 'Action decision result' }).scrollIntoViewIfNeeded()
      assert.equal(decisions(calls).length, 1)
    } else {
      await page.getByRole('region', { name: 'Pending human review' }).scrollIntoViewIfNeeded()
      assert.equal(await page.getByRole('button', { name: 'Confirm action', exact: true }).count(), 0)
      assert.equal(decisions(calls).length, 0)
    }
    assert.equal(executions(calls).length, 1)
  }, { theme, humanReview: true, pendingProposal: true, resultStatus: theme === 'dark' ? 'REVIEW_REQUIRED' : undefined, viewport: { width: 390, height: 844 } })
  console.log(JSON.stringify({ passed, output, realInferenceCalls: 0, realDraftWrites: 0 }))
  await browser.close(); browser = null
  if (process.argv.includes('--serve')) {
    console.log(`LOCAL_REVIEW_URL=${base}/smart-brains/brain/tasks/assess-request?targetProfileId=target&customer=1`)
    console.log('Local in-memory preview only. Changes are disposable; no hosted APIs or inference are used.')
    await new Promise(resolve => { process.once('SIGINT', resolve); process.once('SIGTERM', resolve) })
  }
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
