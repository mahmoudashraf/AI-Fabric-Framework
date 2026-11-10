import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import * as lossless from 'lossless-json'

const jsonExports = {}
const jsonSource = await readFile(new URL('../src/lib/losslessJson.ts', import.meta.url), 'utf8')
new Function('require', 'exports', ts.transpileModule(jsonSource, { compilerOptions: {
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
} }).outputText)(() => lossless, jsonExports)

async function load(path, extra = '') {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText
  const exports = {}
  new Function('require', 'exports', `${compiled}\n${extra}`)(id => id === '../lib/losslessJson' ? jsonExports : {}, exports)
  return exports
}
const page = await load('../src/brain/BrainCapabilitiesPage.tsx')
const marketplace = await load('../src/pages/MarketplacePage.tsx', 'exports.updateField = updateInstallFieldValue; exports.readField = readInstallFieldValue;')
const actions = await load('../src/pages/ActionsPage.tsx', 'exports.toggleAnonymous = updateActionAnonymousAllowed; exports.routes = resolvedRouteRecordForAction;')
const install = (overrides = {}) => ({ id: 'install/one', pluginId: 'support', pluginDisplayName: 'Service desk', pluginType: 'ACTION', contributions: { actionIds: ['open_ticket', 'read_ticket'] }, ...overrides })

test('contributed tasks retain stable installation/action identities when labels change', () => {
  const draft = { actions: [{ name: 'open_ticket', displayName: 'Create a support request', description: 'Send an approved request', requiresConfirmation: true }, { name: 'read_ticket' }] }
  const rows = page.capabilityRows([install()], draft)
  assert.equal(rows.length, 2)
  assert.equal(rows[0].key, 'install:install/one:action:open_ticket')
  assert.equal(rows[0].label, 'Create a support request')
  assert.equal(rows[1].install.id, rows[0].install.id)
  draft.actions[0].displayName = 'Contact support'
  assert.equal(page.capabilityRows([install()], draft)[0].key, rows[0].key)
})

test('draft-only READ actions appear without any vector or plugin prerequisite', () => {
  const rows = page.capabilityRows([], { actions: [{ name: 'lookup_order', accessMode: 'READ', anonymousAllowed: false }] })
  assert.equal(rows.length, 1)
  assert.equal(rows[0].key, 'action:lookup_order')
  assert.equal(rows[0].install, null)
})

test('knowledge and integration connections link to their owning installation, without inventing unsupported builders', () => {
  const rows = page.capabilityRows([
    install({ pluginType: 'DATA', contributions: { actionIds: [], knowledgeSourceIds: ['docs'] } }),
    install({ id: 'channel', pluginType: 'INTEGRATION', contributions: { actionIds: [], conversationChannel: {} } }),
    install({ id: 'model', pluginType: 'INFERENCE_PROFILE', contributions: { actionIds: [] } }),
    install({ id: 'specialist', pluginType: 'SPECIALIST', contributions: { actionIds: [] } }),
    install({ id: 'automation', pluginType: 'AUTOMATION', contributions: { actionIds: [] } }),
    install({ id: 'template', pluginType: 'TEMPLATE', contributions: { actionIds: [] } }),
  ], {})
  assert.deepEqual(rows.map(row => row.kind), ['knowledge', 'integration'])
})

test('unavailable contributed definitions are explicit, not synthesized as executable actions', () => {
  const rows = page.capabilityRows([install()], {})
  assert.equal(rows[0].action, null)
  assert.match(rows[0].purpose, /unavailable/)
})

test('available catalogue excludes templates, model profiles, unsupported specialists and automations', () => {
  const types = ['TEMPLATE', 'ACTION', 'DATA', 'INTEGRATION', 'SPECIALIST', 'AUTOMATION', 'INFERENCE_PROFILE']
  assert.deepEqual(page.availableCapabilities(types.map(pluginType => ({ pluginType }))).map(row => row.pluginType), ['ACTION', 'DATA', 'INTEGRATION'])
})

test('customer input projection retains declared types and requiredness without writing to the schema', () => {
  const action = { params: [{ name: 'email', type: 'STRING', required: true, unknown: ['retained'] }, { name: 'message', type: 'STRING' }], requiredParameters: ['accountId'] }
  const before = structuredClone(action)
  assert.deepEqual(page.capabilityInputs(action), [
    { name: 'email', type: 'STRING', description: '', required: true },
    { name: 'message', type: 'STRING', description: '', required: false },
    { name: 'accountId', type: '', description: '', required: true },
  ])
  assert.deepEqual(action, before)
})

test('typed install field changes preserve unknown configuration and reference fields', () => {
  const config = { endpoint: 'https://example.test', opaque: { future: ['one', 2] } }
  const refs = { token: 'customer-token', extra: 'retained-ref' }
  const next = marketplace.updateField({ id: 'endpoint', type: 'url' }, 'https://new.example.test', config, refs)
  assert.deepEqual(next.config.opaque, config.opaque)
  assert.deepEqual(next.secretRefs, refs)
  assert.equal(config.endpoint, 'https://example.test')
  const bool = marketplace.updateField({ id: 'enabled', type: 'boolean' }, 'false', next.config, refs)
  assert.equal(bool.config.enabled, false)
  assert.equal(marketplace.readField({ id: 'enabled', type: 'boolean' }, bool.config, refs), 'false')
})

test('anonymous toggle and route resolution preserve full unknown action and route fields', () => {
  const config = { unknownRoot: { keep: true }, actions: [{ name: 'open_ticket', anonymousAllowed: false, requiresConfirmation: true, future: [1, 2], route: { path: '/tickets', request: { body: { account: '{{accountId}}', future: true } } } }], webhookTargets: [{ id: 'hook', signingSecretRef: 'signing' }] }
  const changed = actions.toggleAnonymous(config, 'open_ticket', true)
  assert.deepEqual(changed, { ...config, actions: [{ ...config.actions[0], anonymousAllowed: true }] })
  assert.equal(config.actions[0].anonymousAllowed, false)
  const route = actions.routes({ actions: { open_ticket: { method: 'PUT', request: { query: { exact: 'value' } } } } }, changed, 'open_ticket')
  assert.deepEqual(route.request, { body: { account: '{{accountId}}', future: true }, query: { exact: 'value' } })
  assert.equal(route.method, 'PUT')
})
