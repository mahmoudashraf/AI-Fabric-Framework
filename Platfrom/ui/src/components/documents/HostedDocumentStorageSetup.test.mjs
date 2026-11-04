import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'

const require = createRequire(import.meta.url)
async function load(path, dependencies) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const calls = []
const api = await load('../../api/documentStorageApi.ts', {
  './platformApi': { platformRequest: async (...args) => { calls.push(args); return args } },
})
const models = await load('./HostedDocumentStorageSetup.tsx', {
  '../../auth/PlatformAuthProvider': {}, '../../api/documentStorageApi': api,
})

test('reads use deployment-scoped, encoded paths and preserve cancellation signals', async () => {
  calls.length = 0
  const signal = new AbortController().signal
  await api.fetchHostedDocumentStorage('dep/a', signal)
  await api.fetchHostedDocumentUploads('dep/a', 'connection/b', signal)
  await api.fetchHostedDocumentSelections('dep/a', 'connection/b', 'target/a', 'release/b', signal)
  assert.deepEqual(calls.map(([path]) => path), [
    '/api/deployments/dep%2Fa/document-storage/connections',
    '/api/deployments/dep%2Fa/document-storage/connections/connection%2Fb/uploads',
    '/api/deployments/dep%2Fa/document-storage/connections/connection%2Fb/selections?targetProfileId=target%2Fa&releaseId=release%2Fb',
  ])
  assert.ok(calls.every(([, init]) => init.signal === signal && !init.method))
})

test('create is configuration-only; prepare, bind and exact selection have separate contracts', async () => {
  calls.length = 0
  const base = '/api/deployments/dep/document-storage/connections'
  const create = { name: 'Business documents', operationKey: 'create-key' }
  const prepare = { operationKey: 'prepare-key' }
  const bind = { targetProfileId: 'production', prefix: 'public/', operationKey: 'bind-key' }
  const selection = { targetProfileId: 'production', releaseId: 'release-one', uploadIds: ['upload-two'],
    datasetId: 'configured-dataset', publicApproved: false, operationKey: 'selection-key' }
  await api.createHostedDocumentStorage('dep', create)
  await api.prepareHostedDocumentStorage('dep', 'connection', prepare)
  await api.bindHostedDocumentStorage('dep', 'connection', bind)
  await api.prepareHostedDocumentSelection('dep', 'connection', selection)
  assert.deepEqual(calls.map(([path]) => path), [base, `${base}/connection/prepare`, `${base}/connection/bind`, `${base}/connection/prepare-selection`])
  assert.deepEqual(calls.map(([, init]) => JSON.parse(init.body)), [create, prepare, bind, selection])
  assert.ok(calls.every(([, init]) => init.method === 'POST'))
})

test('upload is raw File bytes, never multipart or JSON; filename, prefix and retry key are encoded', async () => {
  calls.length = 0
  const file = new File(['a useful guide'], 'price & policy.txt', { type: 'text/plain' })
  const input = { file, prefix: 'public/policies/', operationKey: 'retry key' }
  await api.uploadHostedDocument('dep', 'connection', input)
  await api.uploadHostedDocument('dep', 'connection', input)
  assert.equal(calls[0][0], '/api/deployments/dep/document-storage/connections/connection/uploads?filename=price+%26+policy.txt&prefix=public%2Fpolicies%2F&operationKey=retry+key')
  for (const [, init] of calls) {
    assert.equal(init.body, file)
    assert.equal(init.headers['Content-Type'], 'application/octet-stream')
  }
  assert.equal(calls[0][0], calls[1][0])
})

test('query identities isolate authority, customer, deployment and connection', () => {
  const key = api.documentStorageKeys.connections('actor/customer/session', 'dep')
  assert.notDeepEqual(key, api.documentStorageKeys.connections('actor/other/session', 'dep'))
  assert.notDeepEqual(key, api.documentStorageKeys.connections('actor/customer/supabase', 'dep'))
  assert.notDeepEqual(key, api.documentStorageKeys.connections('actor/customer/session', 'other'))
  assert.notDeepEqual(api.documentStorageKeys.uploads('scope', 'dep', 'a'), api.documentStorageKeys.uploads('scope', 'dep', 'b'))
  assert.notDeepEqual(api.documentStorageKeys.selections('scope', 'dep', 'a', 'stage', 'release-a'), api.documentStorageKeys.selections('scope', 'dep', 'a', 'prod', 'release-a'))
  assert.notDeepEqual(api.documentStorageKeys.selections('scope', 'dep', 'a', 'stage', 'release-a'), api.documentStorageKeys.selections('scope', 'dep', 'a', 'stage', 'release-b'))
})

test('upload checks only bounded supported file execution requirements; server checks actual content', () => {
  const error = file => models.hostedUploadError(file, 1048576, ['txt', 'json'])
  assert.equal(error(new File(['hello'], 'guide.TXT')), null)
  assert.equal(error(new File(['{}'], 'guide.json')), null)
  assert.equal(error(new File([new Uint8Array(1048576)], 'guide.txt')), null)
  assert.match(error(new File([new Uint8Array(1048577)], 'guide.txt')), /exceeds/)
  assert.match(error(new File([], 'guide.txt')), /empty/)
  assert.match(error(new File(['x'], 'guide.pdf')), /Supported files/)
  assert.match(error(new File(['x'], 'no-extension')), /Supported files/)
  assert.match(error(null), /Choose a file/)
})

test('purpose prefixes are relative and cannot traverse or encode another source scope', () => {
  for (const prefix of ['public/', 'customer-help/policies/', 'staff-only/runbooks/']) assert.equal(models.hostedPrefixError(prefix), null)
  for (const prefix of ['', '/public/', '../private/', 'public/../private/', './public/', 'public\\private', '%2e%2e/private/', 'public/\u0000']) {
    assert.match(models.hostedPrefixError(prefix), /relative purpose prefix/, prefix)
  }
})

test('both integrations use shared setup without replacing the original BYO or live editor', async () => {
  const page = await readFile(new URL('../../pages/DocumentKnowledgePage.tsx', import.meta.url), 'utf8')
  const kit = await readFile(new URL('../kits/ExperienceKitOverview.tsx', import.meta.url), 'utf8')
  assert.match(page, /<HostedDocumentStorageSetup deploymentId=\{selectedDeploymentId\} targetProfiles=\{matchingProfiles\}/)
  assert.match(page, /defaultMode="byo"/)
  for (const text of ['Save binding', 'Connector and discovery', 'Discover sources', 'Register source']) assert.ok(page.includes(text))
  assert.match(kit, /<KitDocumentStorageSetup customerId=\{customerId\}/)
  assert.match(kit, /fetchCustomerSelfServiceOffer\(kit.offerId\)/)
  assert.ok(kit.indexOf('<KitDocumentStorageSetup customerId=') < kit.indexOf('id="ek-before-launch-title"'))
})

test('Support and future document installs resolve from compiled provenance and the DATA binding contract', async () => {
  const source = await readFile(new URL('../../pages/DocumentKnowledgePage.tsx', import.meta.url), 'utf8')
  const parsed = ts.createSourceFile('page.tsx', source, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX)
  const names = new Set(['isRecord', 'asRecord', 'documentDataset', 'documentInstall'])
  const helpers = parsed.statements.filter(statement => names.has(statement.name?.text)
    || (ts.isVariableStatement(statement) && statement.declarationList.declarations.some(item => item.name.getText(parsed) === 'DOCUMENT_PLUGIN_IDS')))
    .map(statement => statement.getText(parsed)).join('\n')
  const compiled = ts.transpileModule(`${helpers}\nexport { documentDataset, documentInstall };`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText
  const exports = {}; new Function('exports', compiled)(exports)
  const support = { id: 'support-install', pluginId: 'mkp-data-ai-support-documents', pluginType: 'DATA', config: {} }
  const future = { id: 'future-install', pluginId: 'future-documents', pluginType: 'DATA', config: { documentStorageBindingRef: 'slot:document-storage' } }
  assert.equal(exports.documentInstall([support], null), support)
  const dataset = exports.documentDataset({ datasets: [{ datasetId: 'support-documents', ingestionMode: 'EXTERNAL_DOCUMENT_STORAGE', marketplaceInstallId: 'future-install' }] })
  assert.equal(exports.documentInstall([support, future], dataset), future)
  assert.equal(exports.documentInstall([support, future], { ...dataset, marketplaceInstallId: undefined, marketplacePluginId: 'future-documents' }), future)
  assert.equal(exports.documentInstall([support, future], { ...dataset, marketplaceInstallId: undefined }), future)
  assert.equal(exports.documentDataset({ datasets: [{ ingestionMode: 'SYNCHRONIZED_DATA' }] }), null)
  assert.match(source, /const documentConfigured = dataset != null \|\| workspace\?\.documentKnowledgeConfigured === true/)
})
