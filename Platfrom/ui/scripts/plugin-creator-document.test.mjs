import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withPluginJsonSupport } from './plugin-json-test-support.mjs'

const source = await readFile(new URL('../src/pluginLibrary/creatorDocument.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(withPluginJsonSupport(source), {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext },
}).outputText
const document = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('unrelated form edits retain unknown fields, nulls, array order and exact numeric lexemes', () => {
  const input = '{"displayName":"Before","unknown":{"integer":9223372036854775807123,"decimal":0.123456789012345678901234567890,"negativeZero":-0,"exponent":1.234567890123456789e+200,"nil":null,"array":[null,3,1,2]}}'
  const output = document.patchDocument(input, [{ path: ['displayName'], value: 'After' }])
  for (const number of ['9223372036854775807123', '0.123456789012345678901234567890', '-0', '1.234567890123456789e+200']) {
    assert.ok(output.includes(number), `${number} must remain exact`)
  }
  const result = document.parseDocument(output)
  assert.equal(result.error, null)
  assert.equal(result.document.displayName, 'After')
  assert.equal(result.document.unknown.nil, null)
  assert.deepEqual(result.document.unknown.array, [null, 3, 1, 2])
})

test('invalid source is reported and never replaced with a generated definition', () => {
  const input = '{"displayName":'
  const result = document.parseDocument(input)
  assert.equal(result.document, null)
  assert.equal(typeof result.error, 'string')
  assert.throws(() => document.patchDocument(input, [{ path: ['displayName'], value: 'New' }]))
  assert.equal(input, '{"displayName":')
})

test('editing through null or scalar ancestors refuses destructive replacement', () => {
  assert.throws(() => document.patchDocument('{"contributions":null}', [
    { path: ['contributions', 'datasets', 0, 'datasetId'], value: 'changed' },
  ]), /parent is not an object/)
  assert.throws(() => document.patchDocument('{"contributions":"keep"}', [
    { path: ['contributions', 'datasets'], value: [] },
  ]), /parent is not an object/)
})

test('explicit array removal preserves the order and values of remaining entries', () => {
  const output = document.patchDocument('{"values":["first",null,"last"]}', [
    { path: ['values', 1], value: undefined, remove: true },
  ])
  assert.deepEqual(document.parseDocument(output).document.values, ['first', 'last'])
})

test('prototype-like mapping keys survive round trips as own properties without prototype mutation', () => {
  const input = '{"__proto__":{"retained":true},"constructor":null,"prototype":{"field":"/source"}}'
  const parsed = document.parseDocument(input).document
  assert.equal(Object.prototype.hasOwnProperty.call(parsed, '__proto__'), true)
  assert.equal(Object.getPrototypeOf(parsed), Object.prototype)
  assert.equal(document.at(parsed, ['__proto__', 'retained']), true)

  const output = document.patchDocument(input, [
    { path: ['__proto__', 'edited'], value: true },
    { path: ['displayName'], value: 'Changed elsewhere' },
  ])
  const result = document.parseDocument(output).document
  assert.equal(Object.prototype.hasOwnProperty.call(result, '__proto__'), true)
  assert.equal(Object.getPrototypeOf(result), Object.prototype)
  assert.equal(document.at(result, ['__proto__', 'retained']), true)
  assert.equal(document.at(result, ['__proto__', 'edited']), true)
  assert.equal(document.at(result, ['constructor']), null)
  assert.equal(document.at(result, ['prototype', 'field']), '/source')
  assert.equal(Object.prototype.retained, undefined)
  assert.equal(Object.prototype.edited, undefined)
})

test('one-time plugin identities produce distinct datasets and knowledge keys with matching references', () => {
  const first = document.parseDocument(document.newDefinition('private-first')).document
  const second = document.parseDocument(document.newDefinition('private-second')).document
  assert.notEqual(first.contributions.datasets[0].datasetId, second.contributions.datasets[0].datasetId)
  assert.notEqual(first.contributions.knowledgeSources[0].sourceKey, second.contributions.knowledgeSources[0].sourceKey)
  assert.equal(first.contributions.knowledgeSources[0].datasetRef, first.contributions.datasets[0].datasetId)
  assert.equal(document.sourceKind(first), 'HTTP_JSON')
  assert.equal(first.installForm.find(field => field.id === 'providerKey').type, 'secretRef')
  for (const path of [
    ['connectionProfile', 'profileId'], ['protectedResource', 'bindingId'], ['httpSource', 'sourceId'],
  ]) {
    assert.notEqual(document.at(first.contributions.datasets[0].syncConnector, path),
      document.at(second.contributions.datasets[0].syncConnector, path))
  }
  assert.equal(first.presentation.catalogueFamily, 'HTTP_JSON')
  assert.equal(first.presentation.catalogueAudience, 'CUSTOMER')

  const renamed = document.parseDocument(document.patchDocument(document.newDefinition('private-first'), [
    { path: ['displayName'], value: 'A different name' },
  ])).document
  assert.equal(renamed.pluginId, first.pluginId)
  assert.equal(renamed.contributions.datasets[0].datasetId, first.contributions.datasets[0].datasetId)
})

test('explicit contract switches replace generated inputs and retain unrelated custom fields', () => {
  let input = document.newDefinition('private-switch')
  input = document.patchDocument(input, [
    { path: ['installForm', 4], value: { id: 'custom', type: 'text', label: 'Custom', extension: null } },
    { path: ['contributions', 'datasets', 0, 'extension'], value: null },
  ])
  const result = document.parseDocument(document.replaceSourceContract(input, 0, 'S3_COMPATIBLE_OBJECT_STORAGE')).document
  assert.equal(document.sourceKind(result), 'S3_COMPATIBLE_OBJECT_STORAGE')
  assert.deepEqual(result.installForm.map(field => field.id), ['custom', 'documentStorageBindingRef'])
  assert.equal(result.installForm[0].extension, null)
  assert.equal(result.contributions.datasets[0].extension, null)
  assert.equal(result.contributions.datasets[0].entityType, 'document')
  assert.equal(result.contributions.knowledgeSources[0].entityType, 'document')
  assert.equal(result.contributions.datasets[0].syncConnector, undefined)
  assert.equal(result.presentation.catalogueFamily, 'DOCUMENT_STORAGE')
  assert.equal(result.presentation.adapterContract, 'S3_COMPATIBLE_OBJECT_STORAGE')
})

test('starter definitions explain installation inputs without inventing customer connection values', () => {
  for (const kind of ['HTTP_JSON', 'S3_COMPATIBLE_OBJECT_STORAGE']) {
    const manifest = document.parseDocument(document.newDefinition('private-guidance', kind)).document
    for (const field of manifest.installForm) {
      assert.ok(field.description.length > 30, `Missing installer help for ${field.id}`)
      assert.equal(Object.hasOwn(field, 'defaultValue'), false, 'Example URLs and account IDs must not become connection values')
    }
    const dataset = manifest.contributions.datasets[0]
    if (kind === 'HTTP_JSON') {
      assert.equal(dataset.syncConnector.httpSource.path, '')
      assert.equal(dataset.syncConnector.httpSource.mapping.idJsonPointer, '')
      assert.equal(dataset.syncConnector.httpSource.scheduleSeconds, 900)
      assert.deepEqual(dataset.syncConnector.httpSource.mapping.contentFields, {})
    } else {
      assert.equal(dataset.documentPolicy.maxSourceBytes, 1048576)
      assert.equal(dataset.documentPolicy.trustedAutoIndexingAllowed, false)
    }
  }
})

test('copying an existing Data definition preserves its full contract and exact values, but gives it a new identity', () => {
  const original = document.parseDocument(document.newDefinition('public-original', 'S3_COMPATIBLE_OBJECT_STORAGE')).document
  original.displayName = 'Approved support documents'
  const withExtension = document.parseDocument(document.patchDocument(JSON.stringify(original), [
    { path: ['custom'], value: { retained: [null, 'first', 'second'] } },
  ])).document
  const copy = document.parseDocument(document.copyDefinition(withExtension, 'private-copy')).document
  assert.equal(copy.pluginId, 'private-copy')
  assert.equal(copy.displayName, 'Copy of Approved support documents')
  assert.equal(copy.version, '1.0.0')
  assert.deepEqual(copy.contributions, original.contributions)
  assert.deepEqual(copy.installForm, original.installForm)
  assert.deepEqual(copy.custom, { retained: [null, 'first', 'second'] })
  assert.equal(withExtension.pluginId, 'public-original')
  const exact = document.parseDocument('{"pluginType":"DATA","version":"3.0.0","custom":900719925474099312345,"contributions":{"datasets":[{"ingestionMode":"EXTERNAL_SYNC_HTTP","syncConnector":{"connectorType":"HTTP_JSON"}}]}}').document
  assert.ok(document.copyDefinition(exact, 'private-exact').includes('900719925474099312345'))
})

test('copying does not convert unsupported adapters or plugin types into private executable definitions', () => {
  for (const kind of ['SQL_QUERY', 'FILE_FOLDER', 'PACKAGED']) assert.throws(() => document.copyDefinition({ pluginType: 'DATA', contributions: { datasets: [{ syncConnector: { connectorType: kind } }] } }, 'private-copy'), /Private definitions currently support/)
  assert.throws(() => document.copyDefinition({ pluginType: 'ACTION' }, 'private-copy'), /Data plugin/)
  assert.throws(() => document.copyDefinition({ pluginType: 'DATA', contributions: { datasets: [{ sourceConnector: { connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE', storageOwnership: 'PLATFORM_FILESYSTEM' } }] } }, 'private-copy'), /Private definitions currently support/)
})

test('audience labels follow exact declared access modes without guessing custom policy', () => {
  const input = document.parseDocument(document.newDefinition('private-access')).document
  assert.equal(document.audience(input, 0), 'staff')
  input.contributions.knowledgeSources[0].authModes.push('PLATFORM_PROXY_SESSION')
  assert.equal(document.audience(input, 0), 'custom')
  for (const audience of ['staff', 'signed', 'public']) {
    input.contributions.knowledgeSources[0].authModes = document.audienceModes(audience)
    assert.equal(document.audience(input, 0), audience)
  }
})

test('numeric parsing rejects incomplete values and preserves large integers and decimals exactly', () => {
  for (const input of ['1e-', '-', '1.']) assert.throws(() => document.numericValue(input))
  assert.equal(String(document.numericValue('1234567890123456789012345')), '1234567890123456789012345')
  assert.equal(String(document.numericValue('0.1000000000000000000000001')), '0.1000000000000000000000001')
  assert.equal(document.numericValue(''), null)
})

test('library and installer navigation retain assigned customer, Brain target, release and exact version', () => {
  const returnTo = '/smart-brains/brain%2Fid/knowledge?targetProfileId=production&releaseId=release-1'
  const search = `?${new URLSearchParams({ returnTo, customerId: 'customer-2', unrelated: 'omit' })}`
  const library = new URL(document.libraryPath('/library/plugins/new/data', search), 'https://console.invalid')
  assert.equal(library.searchParams.get('returnTo'), returnTo)
  assert.equal(library.searchParams.get('customerId'), 'customer-2')
  assert.equal(library.searchParams.get('unrelated'), null)

  const installation = new URL(document.installationPath(returnTo, 'private-1', '1.2.0'), 'https://console.invalid')
  assert.equal(installation.pathname, '/smart-brains/brain%2Fid/capabilities/add')
  for (const [key, value] of Object.entries({
    targetProfileId: 'production', releaseId: 'release-1', pluginId: 'private-1', pluginVersion: '1.2.0', view: 'setup', type: 'DATA',
  })) assert.equal(installation.searchParams.get(key), value)
})

test('external and malformed Brain return destinations are rejected', () => {
  for (const returnTo of [
    'https://attacker.invalid/smart-brains/1', '//attacker.invalid/smart-brains/1',
    '/smart-brains/../home', '/smart-brains/1\\evil', '/home',
  ]) assert.equal(document.safeBrainReturn(`?${new URLSearchParams({ returnTo })}`), null, returnTo)
})
