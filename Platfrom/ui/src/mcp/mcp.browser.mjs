import assert from 'node:assert/strict'
import { mkdtemp, mkdir, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer, transformWithEsbuild } from 'vite'
import { chromium } from 'playwright'
import { LosslessNumber, stringify } from 'lossless-json'

const root = fileURLToPath(new URL('../..', import.meta.url))
const cacheDir = await mkdtemp(join(tmpdir(), 'mcp-ui-cache-'))
const evidence = process.env.MCP_UI_EVIDENCE_DIR || await mkdtemp(join(tmpdir(), 'mcp-ui-evidence-'))
await mkdir(evidence, { recursive: true })
const entry = `
import React from 'react';import{createRoot}from'react-dom/client';
import{BrowserRouter,Link,useLocation,useParams}from'react-router-dom';import{QueryClient,QueryClientProvider}from'@tanstack/react-query';
import{Box,Stack,Typography}from'@mui/material';import{CustomerConsoleTheme}from'/src/theme/CustomerConsoleTheme.tsx';
import{BrainWorkspaceContext}from'/src/brain/BrainWorkspaceContext.tsx';import{BrainCapabilitiesPage}from'/src/brain/BrainCapabilitiesPage.tsx';
import{setPlatformAccessTokenProvider}from'/src/api/platformApi.ts';setPlatformAccessTokenProvider(async()=> 'fixture-console-token');
function App(){const location=useLocation();const brainId='brain-a';const targetProfileId=new URLSearchParams(location.search).get('targetProfileId')??'stage';
 const href=(section='')=>'/smart-brains/'+brainId+'/'+section+'?'+new URLSearchParams({targetProfileId});
 return <BrainWorkspaceContext.Provider value={{brainId,targetProfileId,href,targetProfile:null,release:null,target:null}}><Box sx={{height:'100dvh',display:'flex',flexDirection:'column',minWidth:0}}>
 <Stack direction="row" gap={2} sx={{px:3,py:2,borderBottom:1,borderColor:'divider',flexWrap:'wrap'}}><Typography fontWeight={650}>Example Smart Brain</Typography><Link to="/smart-brains/brain-a/capabilities?view=mcp&targetProfileId=stage">Staging</Link><Link to="/smart-brains/brain-a/capabilities?view=mcp&targetProfileId=prod">Production</Link></Stack>
 <Box sx={{p:{xs:2,lg:3},display:'flex',flex:1,minWidth:0,minHeight:0}}><BrainCapabilitiesPage/></Box></Box></BrainWorkspaceContext.Provider>}
createRoot(document.getElementById('root')).render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}})}><BrowserRouter><CustomerConsoleTheme><App/></CustomerConsoleTheme></BrowserRouter></QueryClientProvider>);
`
const server = await createServer({ root, cacheDir, configFile: false, envFile: false, resolve: { dedupe: ['react', 'react-dom'] }, logLevel: 'error', server: { host: '127.0.0.1', port: 0, hmr: false }, plugins: [{
  name: 'mcp-browser-fixture', enforce: 'pre',
  resolveId(id) {
    if (id === '/__mcp.jsx') return '\0mcp.jsx'
    if (id.endsWith('/pages/ActionsPage')) return '\0mcp-actions.js'
    if (id.endsWith('/pages/MarketplacePage')) return '\0mcp-marketplace.js'
  },
  async load(id) {
    if (id === '\0mcp.jsx') return (await transformWithEsbuild(entry, 'mcp.jsx', { loader: 'jsx', jsx: 'automatic' })).code
    if (id === '\0mcp-actions.js') return 'export const ActionsPage=()=>null;'
    if (id === '\0mcp-marketplace.js') return 'export const MarketplacePage=()=>null;'
  },
  configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!req.url.startsWith('/smart-brains/')) return next()
    res.setHeader('Content-Type', 'text/html')
    res.end(await vite.transformIndexHtml('/', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body,#root{margin:0;width:100%;height:100%}</style></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/__mcp.jsx"></script></body></html>'))
  }) },
}] })
const rootPath = '/api/deployments/brain-a/mcp-connections'
const connection = (patch = {}) => ({ id: 'conn-a', deploymentId: 'brain-a', customerId: 'customer-a', targetProfileId: 'stage', allowedTargetProfileIds: [patch.targetProfileId ?? 'stage'], serverRef: 'mcp-conn-a', name: 'Company tools', endpointUrl: 'https://service.example/mcp', connectionModel: 'PUBLIC', transport: 'AUTO', auth: { mode: 'NONE' }, revision: 1, status: 'ACTIVE', authStatus: 'NOT_REQUIRED', lastDiscoveryId: null, createdAt: '2026-11-11T10:00:00Z', updatedAt: '2026-11-11T10:00:00Z', ...patch })
const tools = () => [
  { name: 'search_docs', title: 'Search documentation', description: 'Search the approved documentation.', inputSchema: { type: 'object', properties: { query: { type: 'string' }, recordId: { type: 'integer', maximum: new LosslessNumber('9223372036854775807') } }, required: ['query'] }, outputSchema: { type: 'object' }, schemaHash: 'hash-search', compatible: true, findings: [], annotations: { readOnlyHint: true } },
  { name: 'cancel_subscription', title: 'Cancel subscription', description: 'Cancel an authorized subscription.', inputSchema: { type: 'object', properties: { subscriptionId: { type: 'string' } } }, schemaHash: 'hash-cancel', compatible: true, findings: [] },
  { name: 'async_task', title: 'Required background task', inputSchema: { type: 'object' }, schemaHash: 'hash-async', compatible: false, findings: ['Requires MCP task execution, which is not supported by synchronous actions.'] },
]
let browser, count = 0
await server.listen()
const base = `http://127.0.0.1:${server.httpServer.address().port}`

async function run(name, work, options = {}) {
  if (process.env.MCP_UI_CASE_FILTER && !name.includes(process.env.MCP_UI_CASE_FILTER)) return
  const context = await browser.newContext({ viewport: options.viewport ?? { width: 1440, height: 1000 } })
  const page = await context.newPage()
  page.setDefaultTimeout(12000)
  const calls = [], errors = [], unexpected = []
  const state = { connections: options.connections ?? [], actions: options.actions ?? [], beforeImport: null, undoFailure: false, draftFailure: false, discoveryFailure: false, importFailure: false, complete: true, failList: false, bindingFailure: false, assistant: null }
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(dark => localStorage.setItem('loomai-customer-console-color-scheme', dark ? 'dark' : 'light'), options.dark !== false)
  await page.route('**/*', route => new URL(route.request().url()).origin === base ? route.continue() : (unexpected.push(route.request().url()), route.abort()))
  await page.route(`${base}/api/**`, async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname, method = req.method(), input = req.postDataJSON()
    calls.push({ path, method, input, target: url.searchParams.get('targetProfileId'), auth: req.headers().authorization, cookie: req.headers().cookie ?? '' })
    const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: stringify(data) })
    if (path === '/api/platform/auth/session') return send({ actorId: 'actor-a', customerId: 'customer-a', role: 'CUSTOMER_ADMIN', authenticated: true })
    if (path === '/api/configuration-assistant/status') return send({ enabled: true, ready: true, documentFetchEnabled: false, validationFeedback: { defaultEnabled: true, maxCorrections: 2 } })
    if (path === '/api/configuration-assistant/sessions' && method === 'POST') {
      state.assistant = { ...input, id: 'session-a', status: 'READY', displayName: 'Example Smart Brain', editable: true, activeTurnId: null, expiresAt: new Date(Date.now() + 3600000).toISOString(), sources: [], turns: [] }
      return send(state.assistant)
    }
    if (path === '/api/configuration-assistant/sessions/session-a' && method === 'GET') return send(state.assistant)
    if (path === '/api/configuration-assistant/sessions/session-a/sources/mcp') {
      state.assistant.sources.push({ id: 'source-a', title: 'Selected MCP tools', mediaType: 'application/vnd.loomai.mcp+json', operations: input.toolNames.map(toolName => ({ id: `op-${toolName}`, kind: 'MCP_TOOL', toolName, summary: 'Tool from the owned catalogue', sourcePointer: `/tools/${toolName}` })), characters: 500, warnings: [] })
      return send(state.assistant)
    }
    if (path.endsWith('/marketplace-installs')) return send([])
    if (path.endsWith('/workspace')) return send({ deployment: { id: 'brain-a', binding: { customerId: 'customer-a' } }, access: { canEdit: !options.readOnly, canAdmin: !options.readOnly } })
    if (path.endsWith('/draft')) return state.draftFailure ? send({ message: 'Saved draft unavailable.' }, 503) : send({ id: 'draft-a', actionsConfig: { actions: state.actions } })
    if (path === '/api/deployments/brain-a/secrets') return send({ deploymentId: 'brain-a', secrets: [{ name: 'CUSTOMER_A_COMPANY_KEY', label: 'Company service key', purpose: 'INTEGRATION_CREDENTIAL', present: true, bindingCount: 0 }], purposes: [] })
    if (path === rootPath && method === 'GET') return state.failList ? send({ message: 'Connection list temporarily unavailable.' }, 503) : send({ deploymentId: 'brain-a', connections: state.connections })
    if (path === rootPath && method === 'POST') {
      const saved = connection({ ...input, authStatus: input.connectionModel === 'PUBLIC' ? 'NOT_REQUIRED' : input.auth.mode === 'OAUTH_AUTHORIZATION_CODE' ? 'AUTHORIZATION_REQUIRED' : 'CONFIGURED' })
      state.connections.push(saved); return send(saved)
    }
    if (path === `${rootPath}/conn-a` && method === 'PATCH') {
      const saved = connection({ ...state.connections[0], ...input, revision: state.connections[0].revision + 1 })
      state.connections[0] = saved; return send(saved)
    }
    if (path === `${rootPath}/conn-a/discover`) {
      if (state.discoveryFailure) return send({ message: 'The service requires company authorization.' }, 401)
      state.connections[0].lastDiscoveryId = 'snapshot-a'
      return send({ id: 'snapshot-a', connectionId: 'conn-a', connectionRevision: state.connections[0].revision, observedAt: '2026-11-11T10:00:00Z', complete: state.complete, protocolVersion: '2025-11-25', tools: tools(), message: state.complete ? 'Tool discovery completed.' : 'Tool traversal reached its configured bound.' })
    }
    if (path === `${rootPath}/conn-a/snapshots/snapshot-a` && method === 'GET') return send({ id: 'snapshot-a', connectionId: 'conn-a', connectionRevision: state.connections[0].revision, observedAt: '2026-11-11T10:00:00Z', complete: state.complete, protocolVersion: '2025-11-25', tools: tools(), message: 'Saved metadata snapshot.' })
    if (path === `${rootPath}/conn-a/import`) {
      if (state.importFailure) return send({ message: 'The snapshot is stale. Discover tools again.' }, 409)
      state.beforeImport = state.actions
      state.actions = input.tools.map(tool => ({ name: `mcp_${tool.toolName}`, displayName: tool.toolName, description: 'Imported capability', adapterType: 'mcp-tool', accessMode: tool.accessMode ?? 'READ_WRITE', requiresConfirmation: tool.requiresConfirmation ?? true, anonymousAllowed: tool.allowAnonymous ?? false, enabled: tool.enabled ?? true, inputSchema: tools().find(candidate => candidate.name === tool.toolName).inputSchema, params: [], execution: { mcp: { connectionId: 'conn-a', serverRef: 'mcp-conn-a', toolName: tool.toolName, toolSchemaHash: tools().find(candidate => candidate.name === tool.toolName).schemaHash } } }))
      const projection = action => Object.fromEntries(Object.entries(action).filter(([key]) => ['name', 'displayName', 'description', 'accessMode', 'requiresConfirmation', 'anonymousAllowed', 'groundingEligible', 'enabled', 'inputSchema', 'outputSchema'].includes(key)))
      return send({ id: 'receipt-a', connectionId: 'conn-a', snapshotId: input.snapshotId, draftId: 'draft-a', draftRevision: 4, createdCount: input.tools.length, updatedCount: 0, actionNames: state.actions.map(action => action.name), createdAt: '2026-11-11T10:00:00Z', changes: state.actions.map(action => ({ actionName: action.name, toolName: action.execution.mcp.toolName, changeType: 'CREATED', before: null, after: projection(action) })) })
    }
    if (path === `${rootPath}/conn-a/imports/receipt-a/undo`) {
      if (state.undoFailure) return send({ message: 'Action settings changed after import. Review the current draft before undoing.' }, 409)
      state.actions = state.beforeImport ?? []
      return send({ importReceiptId: 'receipt-a', draftId: 'draft-a', draftRevision: 5, restoredAt: '2026-11-11T11:00:00Z' })
    }
    if (path === `${rootPath}/conn-a/authorize`) return route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Set-Cookie': 'mcp-browser-binding=fixture-binding; HttpOnly; SameSite=Lax; Path=/' }, body: stringify({ connectionId: 'conn-a', authorizationUrl: 'https://provider.example/authorize?state=server-owned', expiresAt: new Date(Date.now() + 120000).toISOString() }) })
    if (path === `${rootPath}/conn-a/disconnect`) { state.connections[0] = { ...state.connections[0], status: 'DISCONNECTED', authStatus: 'DISCONNECTED' }; return send(state.connections[0]) }
    if (path === `${rootPath}/conn-a/target-bindings`) {
      if (state.bindingFailure) return send({ message: 'This key is not authorized for the selected environment.' }, 403)
      state.connections[0].allowedTargetProfileIds = input.enabled ? [...new Set([...state.connections[0].allowedTargetProfileIds, input.targetProfileId])] : state.connections[0].allowedTargetProfileIds.filter(id => id !== input.targetProfileId)
      return send(state.connections[0])
    }
    return send({ message: `Unexpected API: ${method} ${path}` }, 404)
  })
  const goto = async (query = '?targetProfileId=stage&view=mcp') => { await page.goto(`${base}/smart-brains/brain-a/capabilities${query}`); await page.getByRole('heading', { name: /Connected tools|What your AI can do/ }).waitFor() }
  try {
    await work({ page, calls, state, goto })
    assert.deepEqual(errors, [], 'no browser runtime errors')
    assert.deepEqual(unexpected, [], 'no browser calls to providers')
    assert.ok(calls.every(call => call.auth === 'Bearer fixture-console-token'), 'requests use existing Console authentication')
    count++; process.stdout.write(`PASS ${name}\n`)
  } catch (error) { await page.screenshot({ path: join(evidence, `FAILED-${name.replace(/[^a-z0-9]+/gi, '-')}.png`), fullPage: true }); process.stdout.write(`Failure evidence: ${evidence}\n`); throw error }
  finally { await context.close() }
}
const choose = async (page, label, option) => { await page.getByRole('combobox', { name: label }).click(); await page.getByRole('option', { name: option, exact: true }).click() }

try {
  browser = await chromium.launch({ headless: true })
  await run('public onboarding, compatibility, policies and actual draft receipt', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage')
    await page.getByRole('link', { name: 'Connect MCP', exact: true }).click()
    await page.getByLabel('Connection name').fill('Public documentation')
    await page.getByLabel('MCP server address').fill('https://service.example/mcp')
    assert.equal(await page.getByRole('combobox', { name: 'Company authentication' }).count(), 0)
    assert.equal(calls.filter(call => call.method !== 'GET').length, 0)
    assert.equal(calls.filter(call => call.path.endsWith('/secrets')).length, 0)
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Public documentation', exact: true }).waitFor()
    const create = calls.find(call => call.method === 'POST' && call.path === rootPath)
    assert.equal(create.input.connectionModel, 'PUBLIC'); assert.deepEqual(create.input.auth, { mode: 'NONE' }); assert.equal(create.input.targetProfileId, 'stage')
    assert.equal(calls.some(call => call.path.endsWith('/discover')), false)
    await page.getByRole('button', { name: 'Discover / refresh tools' }).click()
    await page.getByText('3 discovered', { exact: true }).waitFor()
    assert.equal(await page.getByRole('checkbox', { name: 'Select async_task' }).isDisabled(), true)
    await page.getByRole('button', { name: 'Details for search_docs' }).click()
    await page.getByRole('tab', { name: 'Input schema' }).click()
    await page.getByLabel('search_docs input schema').waitFor()
    assert.match(await page.getByLabel('search_docs input schema').innerText(), /9223372036854775807/)
    await page.getByRole('tab', { name: 'Access & confirmation' }).click()
    assert.equal(await page.getByRole('checkbox', { name: 'Ask the customer to confirm before running' }).isChecked(), true)
    assert.equal(await page.getByRole('checkbox', { name: 'Allow visitors who are not signed in' }).isChecked(), false)
    await choose(page, 'Tool effect', 'Verified read-only')
    await page.getByRole('checkbox', { name: 'Ask the customer to confirm before running' }).uncheck()
    await page.getByRole('checkbox', { name: 'Allow visitors who are not signed in' }).check()
    await page.getByRole('button', { name: 'Select all compatible', exact: true }).click()
    await page.getByRole('button', { name: 'Add 2 tools to draft', exact: true }).click()
    await page.getByText('Draft import completed', { exact: true }).waitFor()
    const imported = calls.find(call => call.path.endsWith('/import')).input
    assert.equal(imported.snapshotId, 'snapshot-a'); assert.equal(imported.connectionRevision, 1)
    assert.deepEqual(imported.tools.find(tool => tool.toolName === 'cancel_subscription'), { toolName: 'cancel_subscription' }, 'unmodified policies must not overwrite existing definitions')
    assert.deepEqual(imported.tools.find(tool => tool.toolName === 'search_docs'), { toolName: 'search_docs', accessMode: 'READ', requiresConfirmation: false, allowAnonymous: true })
    assert.ok(calls.filter(call => call.path.startsWith(rootPath) && call.method !== 'GET').every(call => call.target === 'stage'))
    assert.equal(calls.some(call => /publish|apply|tools\/call|turns/.test(call.path)), false)
    await page.getByRole('link', { name: 'Review changes in Updates' }).waitFor()
    await page.screenshot({ path: join(evidence, 'desktop-dark-import.png'), fullPage: true })
  })
  await run('business key onboarding uses references only', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=connect')
    await page.getByLabel('Connection name').fill('Company service')
    await page.getByLabel('MCP server address').fill('https://service.example/mcp')
    await page.getByRole('radio', { name: /Business-authorized/ }).check()
    await choose(page, 'Saved key', 'Company service key / CUSTOMER_A_COMPANY_KEY')
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Company service', exact: true }).waitFor()
    const create = calls.find(call => call.method === 'POST' && call.path === rootPath)
    assert.deepEqual(create.input.auth, { mode: 'BEARER_TOKEN_SECRET_REF', secretRef: 'CUSTOMER_A_COMPANY_KEY' })
    assert.equal(calls.some(call => call.path.endsWith('/authorize')), false)
    assert.equal(await page.locator('input[type=password]').count(), 0)
  })
  await run('machine OAuth configuration', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=connect')
    await page.getByLabel('Connection name').fill('Company machine account')
    await page.getByLabel('MCP server address').fill('https://service.example/mcp')
    await page.getByRole('radio', { name: /Business-authorized/ }).check()
    await choose(page, 'Company authentication', 'OAuth service credentials')
    const issuer = page.getByLabel('Authorization server URL')
    await issuer.waitFor()
    assert.equal(await issuer.evaluate(input => input.required), true)
    await page.getByLabel('Service client ID').fill('company-client')
    await choose(page, 'Saved client secret', 'Company service key / CUSTOMER_A_COMPANY_KEY')
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    assert.equal(calls.some(call => call.method === 'POST' && call.path === rootPath), false, 'missing required machine issuer must not reach the backend')
    await issuer.fill('https://issuer.example')
    await page.getByRole('button', { name: 'Technical connection settings', exact: true }).click()
    assert.equal(await page.getByLabel('Authorization server URL').count(), 1, 'machine issuer must not be duplicated in Advanced')
    await page.getByLabel('Requested scopes', { exact: true }).pressSequentially('support:read support:write')
    assert.equal(await page.getByLabel('Requested scopes', { exact: true }).inputValue(), 'support:read support:write')
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Company machine account', exact: true }).waitFor()
    assert.equal(calls.find(call => call.method === 'POST' && call.path === rootPath).input.auth.oauth.clientSecretRef, 'CUSTOMER_A_COMPANY_KEY')
    assert.equal(calls.find(call => call.method === 'POST' && call.path === rootPath).input.auth.oauth.authorizationServerUrl, 'https://issuer.example')
    assert.equal(calls.some(call => call.path.endsWith('/authorize')), false)
  })
  await run('API-key and multiple-header forms keep credential values out of connection payloads', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=connect')
    await page.getByLabel('Connection name').fill('Company headers')
    await page.getByLabel('MCP server address').fill('https://service.example/mcp')
    await page.getByRole('radio', { name: /Business-authorized/ }).check()
    await choose(page, 'Company authentication', 'API-key header')
    assert.equal(await page.getByLabel('API-key header name').inputValue(), 'X-API-Key')
    await choose(page, 'Saved key', 'Company service key / CUSTOMER_A_COMPANY_KEY')
    await choose(page, 'Company authentication', 'Multiple credential headers')
    await page.getByLabel('Header 1 name').fill('X-API-Key')
    await choose(page, 'Header 1 saved key', 'Company service key / CUSTOMER_A_COMPANY_KEY')
    await page.getByRole('button', { name: 'Add credential header', exact: true }).click()
    await page.getByLabel('Header 2 name').fill('X-Tenant-Key')
    await choose(page, 'Header 2 saved key', 'Company service key / CUSTOMER_A_COMPANY_KEY')
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Company headers', exact: true }).waitFor()
    assert.deepEqual(calls.find(call => call.method === 'POST' && call.path === rootPath).input.auth, { mode: 'MULTIPLE_HEADERS', headers: [{ name: 'X-API-Key', secretRef: 'CUSTOMER_A_COMPANY_KEY' }, { name: 'X-Tenant-Key', secretRef: 'CUSTOMER_A_COMPANY_KEY' }] })
    await page.getByRole('button', { name: 'Edit connection', exact: true }).click()
    await page.getByRole('radio', { name: /Public \/ no authentication/ }).check()
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Company headers', exact: true }).waitFor()
    assert.deepEqual(calls.find(call => call.method === 'PATCH').input.auth, { mode: 'NONE' })
  })
  await run('discovery failure and stale import are visible, not success', async ({ page, calls, state, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    state.discoveryFailure = true
    await page.getByRole('button', { name: 'Discover / refresh tools' }).click()
    await page.getByText('The service requires company authorization.', { exact: true }).waitFor()
    assert.equal(await page.getByText('3 discovered', { exact: true }).count(), 0)
    assert.equal(state.connections[0].connectionModel, 'PUBLIC')
    state.discoveryFailure = false; state.importFailure = true; state.complete = false
    await page.getByRole('button', { name: 'Discover / refresh tools' }).click()
    await page.getByText(/Discovery is incomplete/).waitFor()
    await page.getByRole('checkbox', { name: 'Select search_docs', exact: true }).check()
    assert.equal(await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).isDisabled(), true)
    state.complete = true
    await page.getByRole('button', { name: 'Discover / refresh tools' }).click()
    await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).click()
    await page.getByText('The snapshot is stale. Discover tools again.', { exact: true }).waitFor()
    assert.equal(await page.getByText('Draft import completed', { exact: true }).count(), 0)
    assert.equal(calls.filter(call => call.path.endsWith('/import')).length, 1)
  }, { connections: [connection()] })
  await run('company OAuth popup blocked and callback cannot fabricate a grant', async ({ page, calls, goto }) => {
    await page.addInitScript(() => { window.open = () => null })
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    assert.equal(await page.getByRole('button', { name: 'Discover / refresh tools' }).isDisabled(), true)
    await page.getByRole('button', { name: 'Connect for this Smart Brain' }).click()
    await page.getByRole('button', { name: 'Continue in this tab' }).waitFor()
    const authorization = calls.find(call => call.path.endsWith('/authorize'))
    assert.equal(authorization.input.expectedRevision, 1)
    assert.match(authorization.input.returnPath, /^\/smart-brains\/brain-a\/capabilities\?targetProfileId=stage&view=mcp&mcp=conn-a$/)
    const binding = (await page.context().cookies(base)).find(cookie => cookie.name === 'mcp-browser-binding')
    assert.equal(binding?.httpOnly, true, 'Bearer-authenticated authorize must accept its HttpOnly browser-binding cookie')
    const refreshed = page.waitForResponse(response => new URL(response.url()).pathname === rootPath && response.request().method() === 'GET')
    await page.getByRole('button', { name: 'Refresh connection status', exact: true }).click()
    await refreshed
    assert.equal(calls.filter(call => call.path === rootPath && call.method === 'GET').at(-1).cookie, '', 'ordinary API requests must keep credentials omit with Bearer auth')
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a&mcpOAuth=connected')
    await page.getByText(/Returned from company authorization/).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Discover / refresh tools' }).isDisabled(), true, 'untrusted callback parameter cannot grant access')
    assert.equal(calls.some(call => call.path.endsWith('/discover')), false)
  }, { connections: [connection({ connectionModel: 'BUSINESS', auth: { mode: 'OAUTH_AUTHORIZATION_CODE', oauth: { registrationMode: 'AUTO', scopes: ['support:write'] } }, authStatus: 'AUTHORIZATION_REQUIRED' })] })
  await run('company OAuth popup closed refreshes server state and disconnect is explicit', async ({ page, calls, state, goto }) => {
    await page.addInitScript(() => { window.open = () => { window.__popup = { closed: false, document: { title: '', body: { textContent: '' } }, location: { replace: value => { window.__authorizationUrl = value } }, close: () => { window.__popup.closed = true } }; return window.__popup } })
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByRole('button', { name: 'Connect for this Smart Brain' }).click()
    await page.waitForFunction(() => window.__authorizationUrl)
    state.connections[0].authStatus = 'CONNECTED'
    await page.evaluate(() => { window.__popup.closed = true })
    await page.getByRole('button', { name: 'Reconnect company account' }).waitFor()
    assert.equal(await page.getByRole('button', { name: 'Discover / refresh tools' }).isDisabled(), false)
    await page.getByRole('button', { name: 'Disconnect', exact: true }).click()
    assert.equal(calls.some(call => call.path.endsWith('/disconnect')), false)
    await page.getByRole('dialog').getByRole('button', { name: 'Disconnect', exact: true }).click()
    await page.getByText(/Connection disconnected\. Existing action/).waitFor()
    assert.equal(calls.filter(call => call.path.endsWith('/disconnect')).length, 1)
  }, { connections: [connection({ connectionModel: 'BUSINESS', auth: { mode: 'OAUTH_AUTHORIZATION_CODE', oauth: { registrationMode: 'AUTO', scopes: [] } }, authStatus: 'AUTHORIZATION_REQUIRED' })] })
  await run('read-only workspace cannot configure or discover', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    assert.equal(await page.getByRole('link', { name: 'Connect MCP', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Edit connection', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('switch', { name: 'Use this connection in this environment', exact: true }).isDisabled(), true)
    assert.equal(await page.getByRole('button', { name: 'Discover / refresh tools' }).isDisabled(), true)
    assert.equal(calls.some(call => call.method !== 'GET'), false)
  }, { connections: [connection()], readOnly: true })
  await run('saved metadata and existing policies load without upstream calls', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByText('3 discovered', { exact: true }).waitFor()
    assert.equal(calls.some(call => call.method !== 'GET'), false)
    await page.getByRole('button', { name: 'Details for search_docs' }).click()
    assert.equal(await page.getByRole('checkbox', { name: 'Ask the customer to confirm before running' }).isChecked(), false)
    assert.equal(await page.getByRole('checkbox', { name: 'Allow visitors who are not signed in' }).isChecked(), true)
    await page.getByRole('checkbox', { name: 'Ask the customer to confirm before running' }).check()
    await page.getByRole('checkbox', { name: 'Select search_docs', exact: true }).check()
    await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).click()
    await page.getByText('Draft import completed', { exact: true }).waitFor()
    assert.deepEqual(calls.find(call => call.path.endsWith('/import')).input.tools, [{ toolName: 'search_docs', requiresConfirmation: true }])
  }, { connections: [connection({ lastDiscoveryId: 'snapshot-a' })], actions: [{ name: 'existing_search', accessMode: 'READ', anonymousAllowed: true, requiresConfirmation: false, groundingEligible: true, execution: { mcp: { connectionId: 'conn-a', toolName: 'search_docs' } } }] })
  await run('refresh shows new changed and not-returned tools without mutating the draft', async ({ page, calls, state, goto }) => {
    const before = JSON.stringify(state.actions)
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    const comparison = page.getByRole('region', { name: 'Tool refresh comparison', exact: true })
    await comparison.getByText('2 new / not in draft', { exact: true }).waitFor()
    await comparison.getByText('1 schema change', { exact: true }).waitFor()
    await comparison.getByText('1 not returned', { exact: true }).waitFor()
    await page.getByText('Schema changed', { exact: true }).waitFor()
    await page.getByText('Names not returned', { exact: true }).click()
    await page.getByText('retired_tool', { exact: true }).waitFor()
    assert.equal(JSON.stringify(state.actions), before)
    assert.equal(calls.some(call => call.method !== 'GET'), false)
  }, { connections: [connection({ lastDiscoveryId: 'snapshot-a' })], actions: [
    { name: 'imported_search', execution: { mcp: { connectionId: 'conn-a', toolName: 'search_docs', toolSchemaHash: 'older-hash' } } },
    { name: 'retained_tool', execution: { mcp: { connectionId: 'conn-a', toolName: 'retired_tool', toolSchemaHash: 'retired-hash' } } },
  ] })
  await run('missing draft baseline is not misreported as all new tools', async ({ page, calls, state, goto }) => {
    state.draftFailure = true
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByText('The saved draft is unavailable for comparison. No new, removed or changed tool state is assumed.', { exact: true }).waitFor()
    assert.equal(await page.getByRole('region', { name: 'Tool refresh comparison', exact: true }).count(), 0)
    assert.equal(calls.some(call => call.method !== 'GET'), false)
  }, { connections: [connection({ lastDiscoveryId: 'snapshot-a' })] })
  await run('environment switch clears unsaved setup and uses current target only', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=connect')
    await page.getByLabel('Connection name').fill('Unsaved staging name')
    await page.getByRole('link', { name: 'Production', exact: true }).click()
    await page.getByRole('link', { name: 'Connect MCP', exact: true }).click()
    assert.equal(await page.getByLabel('Connection name').inputValue(), '')
    await page.getByLabel('Connection name').fill('Production documentation')
    await page.getByLabel('MCP server address').fill('https://service.example/mcp')
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Production documentation', exact: true }).waitFor()
    assert.equal(calls.find(call => call.method === 'POST' && call.path === rootPath).input.targetProfileId, 'prod')
  })
  await run('reuse and revoke same owned connection in production without duplicate actions or credentials', async ({ page, calls, state, goto }) => {
    await goto('?targetProfileId=prod&view=mcp&mcp=conn-a')
    const toggle = page.getByRole('switch', { name: 'Use this connection in this environment', exact: true })
    await toggle.waitFor()
    assert.equal(await toggle.isChecked(), false)
    assert.equal(calls.some(call => call.method !== 'GET'), false)
    await toggle.click()
    assert.equal(calls.some(call => call.path.endsWith('/target-bindings')), false)
    await page.getByRole('dialog').getByRole('button', { name: 'Enable connection here', exact: true }).click()
    await page.getByText(/Connection enabled in this environment\./).waitFor()
    assert.equal(await toggle.isChecked(), true)
    assert.deepEqual(calls.find(call => call.path.endsWith('/target-bindings')).input, { expectedRevision: 1, targetProfileId: 'prod', enabled: true })
    assert.equal(state.connections[0].targetProfileId, 'stage'); assert.equal(state.connections[0].revision, 1)
    assert.deepEqual(state.connections[0].allowedTargetProfileIds, ['stage', 'prod'])
    assert.equal(state.actions.length, 1)
    assert.equal(calls.some(call => call.path.endsWith('/import') || call.path.endsWith('/authorize') || call.path.endsWith('/discover')), false)
    await toggle.click()
    await page.getByRole('dialog').getByRole('button', { name: 'Disable connection here', exact: true }).click()
    await page.getByText(/Connection disabled in this environment\./).waitFor()
    assert.equal(await toggle.isChecked(), false)
    assert.deepEqual(state.connections[0].allowedTargetProfileIds, ['stage'])
    await page.getByRole('button', { name: 'Edit connection', exact: true }).click()
    await page.getByLabel('Connection name').fill('Updated shared connection')
    await page.getByRole('button', { name: 'Save connection', exact: true }).click()
    await page.getByRole('heading', { name: 'Updated shared connection', exact: true }).waitFor()
    assert.equal(calls.find(call => call.method === 'PATCH').input.targetProfileId, 'stage', 'editing in production must not retarget the staging-owned connection')
    assert.equal(state.connections.length, 1); assert.equal(state.actions.length, 1)
  }, { connections: [connection()], actions: [{ name: 'existing_tool', execution: { mcp: { connectionId: 'conn-a', toolName: 'search_docs' } } }] })
  await run('environment binding rejection stays visible and does not enable or replace credentials', async ({ page, calls, state, goto }) => {
    state.bindingFailure = true
    await goto('?targetProfileId=prod&view=mcp&mcp=conn-a')
    const toggle = page.getByRole('switch', { name: 'Use this connection in this environment', exact: true })
    await toggle.click()
    await page.getByRole('dialog').getByRole('button', { name: 'Enable connection here', exact: true }).click()
    await page.getByRole('dialog').getByText('This key is not authorized for the selected environment.', { exact: true }).waitFor()
    assert.equal(await page.locator('input[role="switch"]').isChecked(), false)
    assert.deepEqual(state.connections[0].allowedTargetProfileIds, ['stage'])
    assert.equal(calls.filter(call => call.method !== 'GET').length, 1)
  }, { connections: [connection()] })
  await run('saved import diff and actual undo use the current receipt only', async ({ page, calls, state, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByRole('checkbox', { name: 'Select search_docs', exact: true }).check()
    await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).click()
    await page.getByText('Draft import completed', { exact: true }).waitFor()
    await page.getByText('Saved changes (1)', { exact: true }).click()
    await page.getByText('Created: search_docs', { exact: true }).click()
    const settings = page.getByRole('region', { name: 'search_docs saved settings', exact: true })
    await settings.getByText('Customer confirmation', { exact: true }).waitFor()
    await settings.getByText('Confirmation required', { exact: true }).waitFor()
    await settings.getByText('Sign-in required', { exact: true }).waitFor()
    await page.getByText('Advanced snapshots', { exact: true }).click()
    assert.equal(await page.getByLabel('search_docs before', { exact: true }).innerText(), 'null')
    assert.match(await page.getByLabel('search_docs after', { exact: true }).innerText(), /9223372036854775807/)
    assert.doesNotMatch(await page.getByLabel('search_docs after', { exact: true }).innerText(), /execution|secretRef/)
    await page.getByRole('button', { name: 'Undo this import', exact: true }).click()
    await page.getByText(/Import undone\. Draft revision 5/).waitFor()
    assert.equal(await page.getByText('Draft import completed', { exact: true }).count(), 0)
    assert.equal(state.actions.length, 0)
    assert.equal(calls.filter(call => call.path.endsWith('/imports/receipt-a/undo')).length, 1)
    assert.equal(calls.some(call => /publish|apply|tools\/call/.test(call.path)), false)
  }, { connections: [connection({ lastDiscoveryId: 'snapshot-a' })] })
  await run('undo rejection keeps saved receipt and current draft intact', async ({ page, state, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByRole('checkbox', { name: 'Select search_docs', exact: true }).check()
    await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).click()
    await page.getByText('Draft import completed', { exact: true }).waitFor()
    state.undoFailure = true
    await page.getByRole('button', { name: 'Undo this import', exact: true }).click()
    await page.getByText('Action settings changed after import. Review the current draft before undoing.', { exact: true }).waitFor()
    assert.equal(state.actions.length, 1)
    assert.equal(await page.getByText('Draft import completed', { exact: true }).count(), 1)
    assert.equal(await page.getByText(/Import undone\. Draft revision/).count(), 0)
  }, { connections: [connection({ lastDiscoveryId: 'snapshot-a' })] })
  await run('optional assistant reuses existing panel with owned sanitized snapshot, without inference on open', async ({ page, calls, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByRole('button', { name: 'Discover / refresh tools' }).click()
    await page.getByRole('checkbox', { name: 'Select search_docs', exact: true }).check()
    await page.getByRole('button', { name: 'Help configure', exact: true }).click()
    const panel = page.getByRole('complementary', { name: 'Build with AI' })
    await panel.getByRole('button', { name: 'Selected MCP tools', exact: true }).waitFor()
    const attach = calls.find(call => call.path.endsWith('/sources/mcp'))
    assert.deepEqual(attach.input, { connectionId: 'conn-a', snapshotId: 'snapshot-a', toolNames: ['search_docs'] })
    assert.equal(calls.some(call => call.path.endsWith('/turns')), false)
    await panel.locator('summary').filter({ hasText: /^Sources/ }).click()
    await panel.getByRole('checkbox', { name: 'MCP \u00b7 search_docs \u00b7 Tool from the owned catalogue', exact: true }).waitFor()
    await panel.getByRole('button', { name: 'Close assistant', exact: true }).click()
    assert.equal(await page.getByRole('button', { name: 'Add 1 tool to draft', exact: true }).isEnabled(), true)
  }, { connections: [connection()] })
  for (const [width, dark] of [[1440, false], [390, true], [320, false]]) await run(`layout ${width} ${dark ? 'dark' : 'light'}`, async ({ page, goto }) => {
    await goto('?targetProfileId=stage&view=mcp&mcp=conn-a')
    await page.getByRole('button', { name: 'Discover / refresh tools' }).click()
    await page.getByRole('button', { name: 'Details for search_docs' }).click()
    await page.getByRole('tab', { name: 'Input schema' }).click()
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true, 'no horizontal page overflow')
    const schema = page.getByLabel('search_docs input schema')
    await schema.focus(); assert.equal(await schema.evaluate(node => node === document.activeElement), true)
    await page.screenshot({ path: join(evidence, `layout-${width}-${dark ? 'dark' : 'light'}.png`), fullPage: true })
  }, { connections: [connection()], viewport: { width, height: 900 }, dark })
  assert.ok(count > 0, 'at least one requested browser scenario must run')
  process.stdout.write(`\n${count} browser scenarios passed. Mocked API/UI verification only, not live provider qualification. Evidence: ${evidence}\n`)
} finally { await browser?.close(); await server.close(); await rm(cacheDir, { recursive: true, force: true }) }
