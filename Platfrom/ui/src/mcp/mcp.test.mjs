import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

async function load(path, dependencies = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => dependencies[name] ?? {}, exports)
  return exports
}
const model = await load('./model.ts')
const calls = []
const { mcpApi } = await load('./api.ts', { '../api/platformApi': { platformRequest: async (...args) => { calls.push(args); return {} } }, '../lib/losslessJson': { parseExactJson: JSON.parse, stringifyExactJson: JSON.stringify } })
const { operation, brainAssistantScope } = await load('../assistant/scope.ts')
const exact = await load('../lib/losslessJson.ts', { 'lossless-json': await import('lossless-json') })
const actionModel = await load('../actions/actionDefinitionModel.ts', { '../lib/losslessJson': exact })
const actionConfiguration = await load('./actionConfiguration.ts', { '../actions/actionDefinitionModel': actionModel, '../lib/losslessJson': exact })
const turnPresentation = await load('../assistant/turnPresentation.ts', { '../lib/losslessJson': exact })
const review = await load('./importReview.ts', { '../assistant/turnPresentation': turnPresentation, '../actions/actionDefinitionModel': actionModel, '../lib/losslessJson': exact })
const publicConnection = () => ({ ...model.emptyConnection(), name: 'Documentation', endpointUrl: 'https://example.com/mcp' })

test('new public connections contain no upstream credential or personal-account mode', () => {
  const input = publicConnection()
  assert.equal(model.connectionError(input), null)
  assert.deepEqual(input.auth, { mode: 'NONE' })
  assert.equal(input.connectionModel, 'PUBLIC')
  assert.deepEqual(model.connectionPayload({ ...input, auth: { mode: 'BEARER_TOKEN_SECRET_REF', secretRef: 'STALE_REF' } }).auth, { mode: 'NONE' })
})

test('HTTPS validation protects credential/URL boundaries without classifying prose', () => {
  for (const value of ['', 'service.example.com', 'http://example.com/mcp', 'https://user:password@example.com/mcp', 'https://example.com/mcp#token']) assert.ok(model.httpsEndpointError(value))
  for (const value of ['https://example.com/mcp', 'https://example.com:8443/service?version=1']) assert.equal(model.httpsEndpointError(value), null)
})

test('business auth uses key references and validates necessary execution fields', () => {
  const input = { ...publicConnection(), connectionModel: 'BUSINESS', auth: { mode: 'BEARER_TOKEN_SECRET_REF' } }
  assert.match(model.connectionError(input), /saved key/)
  assert.equal(model.connectionError({ ...input, auth: { mode: 'BEARER_TOKEN_SECRET_REF', secretRef: 'CUSTOMER_ABC_API_KEY' } }), null)
  assert.match(model.connectionError({ ...input, auth: { mode: 'NONE' } }), /authorizes/)
  assert.match(model.connectionError({ ...input, auth: { mode: 'API_KEY_HEADER_SECRET', secretRef: 'KEY', headerName: 'Host' } }), /header/)
  assert.equal(model.connectionError({ ...input, auth: { mode: 'API_KEY_HEADER_SECRET', secretRef: 'KEY', headerName: 'X-API-Key' } }), null)
})

test('multiple-header credential references reject protocol overrides and duplicate names', () => {
  const input = { ...publicConnection(), connectionModel: 'BUSINESS', auth: { mode: 'MULTIPLE_HEADERS', headers: [{ name: 'X-Key', secretRef: 'KEY' }, { name: 'x-key', secretRef: 'KEY2' }] } }
  assert.match(model.connectionError(input), /once/)
  assert.match(model.connectionError({ ...input, auth: { ...input.auth, headers: [{ name: 'X-Key', secretRef: '' }] } }), /saved key/)
  for (const name of ['Host', 'Cookie', 'Content-Length', 'Mcp-Session-Id', 'Mcp-Protocol-Version', 'X\nInjected']) assert.equal(model.validHeader(name), false)
  assert.equal(model.connectionError({ ...input, auth: { ...input.auth, headers: [{ name: 'X-Key', secretRef: 'KEY' }, { name: 'X-Tenant', secretRef: 'TENANT' }] } }), null)
})

test('company-owner OAuth is allowed independently of deferred personal-account linking', () => {
  const input = { ...publicConnection(), connectionModel: 'BUSINESS', auth: { mode: 'OAUTH_AUTHORIZATION_CODE', oauth: { registrationMode: 'AUTO', scopes: [] } } }
  assert.equal(model.connectionError(input), null)
  assert.match(model.connectionError({ ...input, auth: { ...input.auth, oauth: { ...input.auth.oauth, registrationMode: 'PREREGISTERED' } } }), /client ID/)
  assert.equal(model.connectionError({ ...input, auth: { ...input.auth, oauth: { ...input.auth.oauth, registrationMode: 'PREREGISTERED', clientId: 'company-client' } } }), null)
})

test('machine OAuth requires configured client credentials, not a visitor account', () => {
  const input = { ...publicConnection(), connectionModel: 'BUSINESS', auth: { mode: 'OAUTH2_CLIENT_CREDENTIALS', oauth: { registrationMode: 'PREREGISTERED', scopes: [] } } }
  assert.match(model.connectionError(input), /client ID/)
  const configured = { ...input, auth: { ...input.auth, oauth: { ...input.auth.oauth, clientId: 'client', clientSecretRef: 'SECRET' } } }
  assert.match(model.connectionError(configured), /authorization server URL/)
  assert.match(model.connectionError({ ...configured, auth: { ...configured.auth, oauth: { ...configured.auth.oauth, authorizationServerUrl: '   ' } } }), /authorization server URL/)
  assert.match(model.connectionError({ ...configured, auth: { ...configured.auth, oauth: { ...configured.auth.oauth, authorizationServerUrl: 'http://issuer.example' } } }), /HTTPS/)
  assert.equal(model.connectionError({ ...configured, auth: { ...configured.auth, oauth: { ...configured.auth.oauth, authorizationServerUrl: 'https://issuer.example' } } }), null)
})

test('default policies do not infer read authority or anonymous access from provider hints', () => {
  assert.deepEqual(model.defaultToolPolicy(), { accessMode: 'READ_WRITE', requiresConfirmation: true, allowAnonymous: false, readGrounding: false, enabled: true })
  const read = model.updateToolPolicy(model.defaultToolPolicy(), { accessMode: 'READ', readGrounding: true, requiresConfirmation: false, allowAnonymous: true })
  assert.equal(read.readGrounding, true)
  assert.equal(model.updateToolPolicy(read, { accessMode: 'WRITE_ONLY' }).readGrounding, false)
  assert.equal(read.readGrounding, true, 'policy edits never mutate the previous state')
})

test('per-tool policy overrides retain unedited values and exact connection/tool identity', () => {
  assert.deepEqual(model.updateToolPolicy({}, { requiresConfirmation: false }), { requiresConfirmation: false })
  assert.deepEqual(model.updateToolPolicy({}, { accessMode: 'READ' }), { accessMode: 'READ' })
  assert.deepEqual(model.updateToolPolicy({ readGrounding: true }, { accessMode: 'WRITE_ONLY' }), { accessMode: 'WRITE_ONLY', readGrounding: false })
  const action = (connectionId, toolName) => ({ accessMode: 'READ', anonymousAllowed: true, requiresConfirmation: false, groundingEligible: true, enabled: true, execution: { mcp: { connectionId, toolName } } })
  const existing = model.existingToolPolicies({ actions: [action('connection-a', 'ExactName'), action('connection-b', 'foreign'), action('connection-a', '__proto__')] }, 'connection-a')
  assert.deepEqual(existing.ExactName, { accessMode: 'READ', requiresConfirmation: false, allowAnonymous: true, readGrounding: true, enabled: true })
  assert.equal(existing.foreign, undefined)
  assert.equal(existing.exactname, undefined)
  assert.equal(existing.__proto__.accessMode, 'READ')
  assert.equal(Object.getPrototypeOf(existing), null)
})

test('refresh compares exact names/hashes against only this connection, without changing the draft', () => {
  const action = (toolName, hash, connectionId = 'owned') => ({ execution: { mcp: { connectionId, toolName, toolSchemaHash: hash } } })
  const config = { actions: [action('same', 'a'), action('changed', 'old'), action('missing', 'b'), action('noHash', undefined), action('new', 'foreign', 'other'), action('__proto__', 'p')] }
  const before = JSON.stringify(config)
  const snapshot = { connectionId: 'owned', tools: [{ name: 'same', schemaHash: 'a' }, { name: 'changed', schemaHash: 'new' }, { name: 'new', schemaHash: 'n' }, { name: 'noHash', schemaHash: 'x' }, { name: '__proto__', schemaHash: 'p' }] }
  const comparison = model.compareMcpTools(snapshot, config)
  assert.deepEqual(comparison.added, ['new'])
  assert.deepEqual(comparison.updated, ['changed'])
  assert.deepEqual(comparison.unchanged, ['same', '__proto__'])
  assert.deepEqual(comparison.unverified, ['noHash'])
  assert.deepEqual(comparison.notReturned, ['missing'])
  assert.equal(JSON.stringify(config), before)
  assert.equal(comparison.states.get('changed'), 'SCHEMA_CHANGED')
})

test('refresh does not infer unchanged from missing/conflicting hashes or normalize tool names', () => {
  const config = { actions: [
    { execution: { mcp: { connectionId: 'owned', toolName: 'Exact', toolSchemaHash: 'a' } } },
    { execution: { mcp: { connectionId: 'owned', toolName: 'duplicate', toolSchemaHash: 'a' } } },
    { execution: { mcp: { connectionId: 'owned', toolName: 'duplicate', toolSchemaHash: 'b' } } },
  ] }
  const snapshot = { connectionId: 'owned', complete: false, tools: [{ name: 'exact', schemaHash: 'a' }, { name: 'duplicate', schemaHash: 'a' }] }
  const comparison = model.compareMcpTools(snapshot, config)
  assert.deepEqual(comparison.added, ['exact'])
  assert.deepEqual(comparison.notReturned, ['Exact'])
  assert.deepEqual(comparison.unverified, ['duplicate'])
})

test('unsafe OAuth navigation cannot become script execution or credential-embedded URLs', () => {
  for (const value of ['javascript:alert(1)', 'http://example.com', 'https://user:password@example.com', '/oauth']) assert.throws(() => model.safeAuthorizationUrl(value))
  assert.equal(model.safeAuthorizationUrl('https://provider.example/authorize?state=opaque'), 'https://provider.example/authorize?state=opaque')
})

test('every connection API binds the selected target and uses existing authenticated client', async () => {
  await mcpApi.list('brain/one', 'target space')
  assert.equal(calls.at(-1)[0], '/api/deployments/brain%2Fone/mcp-connections?targetProfileId=target+space')
  await mcpApi.create('brain', 'stage', publicConnection())
  assert.equal(JSON.parse(calls.at(-1)[1].body).targetProfileId, 'stage')
  await mcpApi.update('brain', 'stage', 'connection/id', 7, publicConnection())
  assert.equal(calls.at(-1)[1].method, 'PATCH')
  assert.equal(JSON.parse(calls.at(-1)[1].body).expectedRevision, 7)
  assert.match(calls.at(-1)[0], /connection%2Fid\?targetProfileId=stage$/)
  await mcpApi.discover('brain', 'stage', 'connection')
  assert.match(calls.at(-1)[0], /\/connection\/discover\?targetProfileId=stage$/)
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), {})
  assert.equal(calls.at(-1)[2], 'default')
  assert.equal(calls.at(-1)[3], JSON.parse)
  assert.deepEqual(calls.at(-1)[4], { timeoutMs: 75_000 })
})

test('all owned connections remain discoverable for reuse, and explicit binding does not retarget the connection', async () => {
  await mcpApi.list('brain')
  assert.equal(calls.at(-1)[0], '/api/deployments/brain/mcp-connections')
  await mcpApi.bindTarget('brain', 'prod', 'connection', 3, true)
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { expectedRevision: 3, targetProfileId: 'prod', enabled: true })
  assert.match(calls.at(-1)[0], /\/connection\/target-bindings\?targetProfileId=prod$/)
  assert.equal(calls.at(-1)[2], 'default')
  await mcpApi.update('brain', 'prod', 'connection', 3, publicConnection(), 'stage')
  assert.equal(JSON.parse(calls.at(-1)[1].body).targetProfileId, 'stage')
  assert.match(calls.at(-1)[0], /targetProfileId=prod$/)
  const owned = { targetProfileId: 'stage', allowedTargetProfileIds: ['stage'] }
  assert.equal(model.connectionEnabledInTarget(owned, 'stage'), true)
  assert.equal(model.connectionEnabledInTarget(owned, 'prod'), false)
  assert.equal(model.connectionEnabledInTarget({ ...owned, allowedTargetProfileIds: [] }, 'stage'), false)
  assert.equal(model.connectionEnabledInTarget({ targetProfileId: 'stage' }, 'stage'), false, 'creation target is not inferred authorization')
})

test('import projects the reviewed snapshot and only requested policy overrides', async () => {
  const input = { snapshotId: 'snapshot', connectionRevision: 7, tools: [{ toolName: 'ReadExact' }, { toolName: 'cancel', requiresConfirmation: false }] }
  await mcpApi.import('brain', 'prod', 'connection', input)
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), input)
  assert.match(calls.at(-1)[0], /targetProfileId=prod$/)
  assert.equal(calls.at(-1)[1].method, 'POST')
})

test('undo uses the owned import receipt without replaying discovery or import', async () => {
  await mcpApi.undoImport('brain', 'stage', 'connection', 'receipt/id')
  assert.equal(calls.at(-1)[0], '/api/deployments/brain/mcp-connections/connection/imports/receipt%2Fid/undo?targetProfileId=stage')
  assert.equal(calls.at(-1)[1].method, 'POST')
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), {})
})

test('business OAuth and disconnect use backend-owned state, never supplied tokens', async () => {
  await mcpApi.authorize('brain', 'stage', 'connection', 4, '/smart-brains/brain/capabilities?view=mcp')
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { expectedRevision: 4, returnPath: '/smart-brains/brain/capabilities?view=mcp' })
  assert.equal(calls.at(-1)[2], 'browser-bound')
  assert.equal(calls.at(-1)[4], undefined)
  await mcpApi.disconnect('brain', 'stage', 'connection')
  assert.match(calls.at(-1)[0], /\/disconnect\?targetProfileId=stage$/)
  assert.equal(calls.at(-1)[2], 'default')
})

test('assistant only attaches a server-owned snapshot handle, not credentials or browser-built tool JSON', async () => {
  await mcpApi.assistantSource('session/id', 'connection', 'snapshot', ['tool/one'])
  assert.equal(calls.at(-1)[0], '/api/configuration-assistant/sessions/session%2Fid/sources/mcp')
  assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { connectionId: 'connection', snapshotId: 'snapshot', toolNames: ['tool/one'] })
})

test('MCP operations use an explicit MCP label without changing HTTP operations', () => {
  assert.deepEqual(operation({ id: 'op', kind: 'MCP_TOOL', toolName: 'SearchDocs', summary: 'Search documentation', sourcePointer: '/tools/0' }), { id: 'op', label: 'MCP \u00b7 SearchDocs \u00b7 Search documentation', pointer: '/tools/0' })
  assert.deepEqual(operation({ id: 'http', method: 'GET', path: '/orders', summary: 'Read orders' }), { id: 'http', label: 'GET /orders Read orders', pointer: '' })
  assert.equal(operation({ kind: 'MCP_TOOL', toolName: 'no-server-id' }), null)
})

test('the explicit snapshot-aware Help configure owns the MCP assistant, without a duplicate outer panel', () => {
  for (const search of ['?view=mcp', '?mcp=connect', '?view=mcp&mcp=connection-a']) assert.equal(brainAssistantScope('/smart-brains/brain-a/capabilities', search, 'brain-a'), null)
  assert.equal(brainAssistantScope('/smart-brains/brain-a/capabilities', '', 'brain-a').task, 'ACTIONS')
})

const mcpCatalogue = () => exact.parseExactJson(`{"actions":[{"name":"mcp_exact","displayName":"Read account","adapterType":"mcp-tool","inputSchema":{"type":"object","properties":{"account":{"type":"integer","maximum":9223372036854775807},"options":{"type":"object"}},"required":["account"]},"outputSchema":{"type":"object"},"execution":{"adapterType":"mcp-tool","retry":{"maxAttempts":1},"mcp":{"toolName":"Exact.Tool","connectionId":"owned-connection","serverRef":"owned-server","toolSchemaHash":"schema-hash"}},"params":[{"name":"account","type":"INTEGER","required":true,"defaultValue":9007199254740993,"askUser":true,"future":{"id":9007199254740995}},{"name":"options","type":"OBJECT","required":false,"properties":{"count":{"type":"INTEGER"}},"defaultValue":{"count":2}}]},{"name":"http_sibling","route":{"path":"/keep"}}],"mcpServers":[{"serverRef":"owned-server","auth":{"mode":"BEARER_TOKEN_SECRET_REF","secretRef":"CUSTOMER_KEY"}}],"future":{"id":9007199254740997}}`)

test('MCP destination detection uses exact adapter metadata, never action name or guessed server', () => {
  const action = mcpCatalogue().actions[0]
  assert.equal(actionConfiguration.mcpActionDestination(action).toolName, 'Exact.Tool')
  assert.equal(actionConfiguration.mcpActionDestination({ ...action, adapterType: undefined }).connectionId, 'owned-connection')
  assert.equal(actionConfiguration.mcpActionDestination({ name: 'mcp_looks_like_one', adapterType: 'connector-http', execution: { mcp: { toolName: 'not-a-binding' } } }), null)
  assert.deepEqual(actionConfiguration.mcpActionDestination({ adapterType: 'mcp-tool' }), {})
})

test('local advanced MCP settings exclude native schemas, execution/auth and routes', () => {
  const action = mcpCatalogue().actions[0]
  const local = actionConfiguration.mcpLocalSettings(action)
  assert.equal(local.inputSchema, undefined); assert.equal(local.outputSchema, undefined)
  assert.equal(local.execution, undefined); assert.equal(local.adapterType, undefined)
  assert.equal(local.name, action.name)
  assert.equal(String(local.params[0].defaultValue), '9007199254740993')
})

test('MCP local settings retain typed scalar/complex defaults, native contract and unrelated configuration exactly', () => {
  const catalogue = mcpCatalogue(), original = exact.stringifyExactJson(catalogue)
  const local = actionConfiguration.mcpLocalSettings(catalogue.actions[0])
  local.description = 'Ask for the account then look up its details.'
  local.params[0] = { ...local.params[0], description: 'Account from the signed-in context.', askUser: false, visibility: 'INTERNAL', resolveFrom: { source: 'USER_ID' } }
  local.params[1] = { ...local.params[1], defaultValue: exact.parseExactJson('{"id":9007199254740999,"enabled":false,"options":[1,"two"]}') }
  const after = exact.parseExactJson(actionConfiguration.replaceMcpLocalSettings(original, 0, local))
  assert.deepEqual(after.actions[0].inputSchema, catalogue.actions[0].inputSchema)
  assert.deepEqual(after.actions[0].execution, catalogue.actions[0].execution)
  assert.deepEqual(after.actions[1], catalogue.actions[1]); assert.deepEqual(after.mcpServers, catalogue.mcpServers)
  assert.equal(String(after.future.id), '9007199254740997')
  assert.equal(String(after.actions[0].params[0].defaultValue), '9007199254740993')
  assert.equal(String(after.actions[0].params[1].defaultValue.id), '9007199254740999')
  assert.equal(after.actions[0].params[1].defaultValue.enabled, false)
})

test('MCP advanced local JSON cannot replace the upstream execution contract', () => {
  const catalogue = mcpCatalogue(), original = exact.stringifyExactJson(catalogue)
  for (const field of ['adapterType', 'execution', 'inputSchema', 'outputSchema', 'route']) {
    const local = actionConfiguration.mcpLocalSettings(catalogue.actions[0]); local[field] = {}
    assert.throws(() => actionConfiguration.replaceMcpLocalSettings(original, 0, local), /read-only/)
  }
  for (const patch of [{ name: 'new_argument' }, { type: 'STRING' }, { required: false }, { max: 1 }]) {
    const local = actionConfiguration.mcpLocalSettings(mcpCatalogue().actions[0]); local.params[0] = { ...local.params[0], ...patch }
    assert.throws(() => actionConfiguration.replaceMcpLocalSettings(original, 0, local), /parameter names, types, requirements and schemas are read-only/)
  }
  const local = actionConfiguration.mcpLocalSettings(mcpCatalogue().actions[0]); local.params.pop()
  assert.throws(() => actionConfiguration.replaceMcpLocalSettings(original, 0, local), /read-only/)
})

test('MCP local edits permit harmless parameter/key reordering and keep existing dependency protection', () => {
  const catalogue = mcpCatalogue(), original = exact.stringifyExactJson(catalogue)
  const local = actionConfiguration.mcpLocalSettings(catalogue.actions[0]); local.params.reverse()
  local.params = local.params.map(param => Object.fromEntries(Object.entries(param).reverse()))
  assert.equal(exact.parseExactJson(actionConfiguration.replaceMcpLocalSettings(original, 0, local)).actions[0].params[0].name, 'options')
  local.name = 'renamed'
  assert.throws(() => actionConfiguration.replaceMcpLocalSettings(original, 0, local), /cannot be changed/)
})

test('saved import review reports actual changed policies in readable language without inferred authority', () => {
  const rows = review.importReviewRows({ changeType: 'UPDATED', before: { displayName: 'Look up account', accessMode: 'READ_WRITE', requiresConfirmation: true, anonymousAllowed: false }, after: { displayName: 'Look up account', accessMode: 'READ', requiresConfirmation: false, anonymousAllowed: true } })
  assert.deepEqual(rows, [
    { key: 'accessMode', label: 'Tool effect', before: 'May read and change data', after: 'Read-only' },
    { key: 'requiresConfirmation', label: 'Customer confirmation', before: 'Confirmation required', after: 'No confirmation' },
    { key: 'anonymousAllowed', label: 'Who can use it', before: 'Sign-in required', after: 'Anonymous visitors allowed' },
  ])
  assert.deepEqual(review.importReviewRows({ before: null, after: { annotations: { readOnlyHint: true } } }), [])
})

test('created import review distinguishes absent values and changed native schema, preserving exact comparisons', () => {
  const rows = review.importReviewRows({ before: null, after: { displayName: 'Find a guide', requiresConfirmation: true, enabled: false, inputSchema: { type: 'object', properties: { query: { type: 'string' } } } } })
  assert.equal(rows.find(row => row.key === 'requiresConfirmation').before, 'Not present')
  assert.equal(rows.find(row => row.key === 'enabled').after, 'Disabled')
  assert.equal(rows.find(row => row.key === 'inputSchema').after, 'Saved schema (1 field)')
  const before = exact.parseExactJson('{"inputSchema":{"type":"integer","maximum":9007199254740993}}')
  const after = exact.parseExactJson('{"inputSchema":{"maximum":9007199254740993,"type":"integer"}}')
  assert.deepEqual(review.importReviewRows({ before, after }), [])
  after.inputSchema.maximum = exact.exactNumber('9007199254740995')
  assert.equal(review.importReviewRows({ before, after })[0].label, 'Expected inputs')
})
