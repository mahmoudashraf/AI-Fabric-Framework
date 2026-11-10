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
  vectorSpaces: [], readActions: [], deadlineSeconds: 45, maxOutputTokens: 1200 }
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
      managedTasks: { enabled: !options.empty, tasks: options.empty ? [] : [structuredClone(task)], compiled: { serverOwned: true }, contentHash: 'server-owned' } },
    actionsConfig: { actions: [{ name: 'read_account', displayName: 'Read account', accessMode: 'READ', ...provenance },
      { name: 'manual_read', accessMode: 'READ' }, { name: 'update_account', accessMode: 'WRITE_ONLY', ...provenance }] },
    entityConfig: { 'ai-entities': { unrelated: {} } }, knowledgeSourceConfig: { sources: [{ id: 'guide', displayName: 'Approved guide', entityType: 'document', enabled: true, ...provenance }] } }
  return { saved, options, failSave: false, changed: false, failExecute: false, canonicalFailure: false, appliedHash: 'tasks-hash', metadataReads: 0 }
}
function workspace(state) {
  return { access: { canEdit: !state.options.readonly, canOperate: !state.options.noOperate, canAdmin: true },
    deployment: { id: 'brain', name: 'Service intelligence', binding: { customerId: 'customer' }, deploymentPolicy: null },
    draft: { id: 'draft', revisionNumber: state.saved.revisionNumber },
    lifecycle: { savedDraftMatchesLatestPublished: !state.changed && !state.options.newer, latestPublishedVersionId: 'v1' }, latestVersion: { id: 'v1' } }
}
function result(state) {
  return { runId: 'run-fixture', taskCode: 'assess-request', status: state.canonicalFailure ? 'FAILED' : 'SUCCEEDED',
    configurationVersion: 'v1', taskConfigurationHash: 'tasks-hash', outputSchemaVersion: '1',
    output: state.canonicalFailure ? null : { answer: 'Route to the integration team; the API authorisation details are missing.' },
    failure: state.canonicalFailure ? { code: 'TASK_OUTPUT_INVALID', message: 'The generated output did not match the task schema.' } : null,
    evidence: [{ evidenceId: 'guide-1', source: 'Approved guide', vectorSpace: 'document' }], sideEffectsExecuted: false,
    startedAt: '2026-11-10T12:00:00Z', completedAt: '2026-11-10T12:00:01Z' }
}
function handle(state, method, url, body) {
  const path = url.pathname
  const send = (body, status = 200) => ({ body, status })
  if (path === '/api/deployments/brain/draft') return send(state.saved)
  if (path === '/api/deployments/brain/workspace') return send(workspace(state))
  if (path === '/api/deployment-provider/target-profiles' || path === '/api/deployments/brain/target-profiles') return send([{ id: 'target', name: 'Customer staging', environmentName: 'staging', active: true, defaultForRuntime: true }])
  if (path === '/api/support/workspace') return send({ message: 'Support disabled in this isolated local fixture.' }, 503)
  if (path === '/api/deployments/brain/releases') return send(state.options.noRelease ? [] : [{ id: 'release', deploymentVersionId: 'v1', targetProfileId: 'target', createdAt: '2026-11-10', status: 'APPLIED_VERIFIED', provisioningStatus: 'ACTIVE' }])
  if (path === '/api/deployments/brain/versions') return send([{ id: 'v1', versionLabel: 'v1', publishedAt: '2026-11-10' }])
  if (path === '/api/deployment-drafts/draft' && method === 'PUT') {
    if (state.failSave) { state.failSave = false; return send({ message: 'Draft save rejected.', errorCode: 'CONFLICT' }, 409) }
    // Production keeps this revision identity across saves; it is not an edit ETag.
    Object.assign(state.saved, body); state.changed = true
    return send(state.saved)
  }
  if (path === '/api/deployments/brain/tasks/applied') {
    assert.equal(url.searchParams.get('targetProfileId'), 'target')
    state.metadataReads++
    if (state.options.metadataError) return send({ message: 'Applied configuration unavailable.' }, 503)
    return send({ configurationVersion: 'v1', taskConfigurationHash: state.appliedHash, runtimeBaseUrl: 'https://runtime.example.test', tasks: state.options.missingTask ? [] : [task] })
  }
  if (path === '/api/deployments/brain/tasks/assess-request/execute' && method === 'POST') {
    assert.equal(url.searchParams.get('targetProfileId'), 'target')
    if (state.failExecute) return send({ message: 'Execution access denied.' }, 403)
    return send(result(state), state.canonicalFailure ? 502 : 200)
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
  const context = await browser.newContext({ viewport: options.viewport ?? { width: 1440, height: 1000 } })
  const page = await context.newPage()
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
    assert.equal(saves(calls).length, 0)
    await page.getByRole('button', { name: 'Remove task', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Remove task', exact: true }).click()
    await page.getByText('No tasks yet. Add one to define what your service needs.', { exact: true }).waitFor()
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
  for (const mode of ['newer', 'missingTask', 'noRelease']) await run('applied gate ' + mode, async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Try', exact: true }).click()
    if (mode !== 'noRelease') await page.getByText('Applied version: v1', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Run task', exact: true }).isDisabled(), true)
    assert.equal(executions(calls).length, 0)
    if (mode === 'noRelease') assert.equal(appliedReads(calls).length, 0)
  }, { [mode]: true })
  await run('resource choices reuse installed source and READ authority', async ({ page, calls }) => {
    await page.getByRole('tab', { name: 'Resources', exact: true }).click()
    await page.getByRole('checkbox', { name: 'Read account · Support tools' }).waitFor()
    assert.equal(await page.getByText('manual_read', { exact: true }).count(), 0)
    assert.equal(await page.getByText('update_account', { exact: true }).count(), 0)
    assert.equal(await page.getByText('unrelated', { exact: true }).count(), 0)
    await page.getByRole('checkbox', { name: 'Read account · Support tools' }).check()
    await page.getByRole('button', { name: 'Save draft', exact: true }).click()
    await page.getByText('Draft saved; apply an update to use it', { exact: true }).waitFor()
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].readActions, ['read_account'])
    assert.deepEqual(saves(calls)[0].body.behaviorConfig.managedTasks.tasks[0].vectorSpaces, [])
    assert.equal(executions(calls).length, 0)
  })
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
  console.log(JSON.stringify({ passed, output, realInferenceCalls: 0, realDraftWrites: 0 }))
  await browser.close(); browser = null
  if (process.argv.includes('--serve')) {
    console.log(`LOCAL_REVIEW_URL=${base}/smart-brains/brain/tasks/assess-request?targetProfileId=target&customer=1`)
    console.log('Local in-memory preview only. Changes are disposable; no hosted APIs or inference are used.')
    await new Promise(resolve => { process.once('SIGINT', resolve); process.once('SIGTERM', resolve) })
  }
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
