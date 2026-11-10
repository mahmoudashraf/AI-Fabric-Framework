import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { actionCatalogueFixture } from './actionDefinitionFixtures.mjs'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const exact = await load('../lib/losslessJson.ts')
const model = await load('./actionDefinitionModel.ts', { '../lib/losslessJson': exact })
const initial = JSON.stringify(actionCatalogueFixture, null, 2)
const parsed = value => JSON.parse(value)

test('contract enums are connector enums, not invented WRITE or source properties', () => {
  assert.deepEqual(model.ACTION_ACCESS_MODES, ['READ', 'READ_WRITE', 'WRITE_ONLY'])
  assert.deepEqual(model.ACTION_PARAM_TYPES, ['STRING', 'INTEGER', 'NUMBER', 'BOOLEAN', 'OBJECT', 'ARRAY', 'UNKNOWN'])
  assert.deepEqual(model.ACTION_PARAM_SOURCES, ['USER', 'INTERNAL'])
  assert.deepEqual(model.ACTION_RESULT_HINTS, ['DEFAULT', 'LIST', 'DETAIL', 'STATUS'])
})

test('basics, access, confirmation and grounding edits preserve all unedited definition and catalogue fields', () => {
  const changes = {
    displayName: 'Order status', description: 'Current status by order reference.', category: 'support',
    accessMode: 'READ_WRITE', anonymousAllowed: true, requiresConfirmation: true, confirmationMessage: 'Continue?',
    groundingEligible: false, groundingVectorSpaces: [], readActionResolutionEligible: false,
  }
  const result = parsed(model.patchActionDefinition(initial, 0, changes))
  assert.deepEqual(result, { ...actionCatalogueFixture, actions: [{ ...actionCatalogueFixture.actions[0], ...changes }, actionCatalogueFixture.actions[1]] })
  assert.deepEqual(parsed(initial), actionCatalogueFixture, 'the original object is unchanged')
})

test('actual params arrays round-trip INTERNAL/USER, askUser, defaults/enums, resolvers and evidence contracts', () => {
  const before = actionCatalogueFixture.actions[0].params[0]
  const patch = { name: 'orderReference', type: 'STRING', description: 'Updated trusted reference.', required: false, visibility: 'USER', askUser: true }
  const result = parsed(model.patchActionParameter(initial, 0, 0, patch))
  assert.ok(Array.isArray(result.actions[0].params))
  assert.deepEqual(result.actions[0].params, [{ ...before, ...patch }, ...actionCatalogueFixture.actions[0].params.slice(1)])
  assert.equal(Object.hasOwn(result.actions[0].params[0], 'source'), false)
  assert.deepEqual(result.actions[0].route, actionCatalogueFixture.actions[0].route)
  assert.deepEqual(result.actions[1], actionCatalogueFixture.actions[1])
})

test('real Auto Trader fixture params and rich source-projection routes survive form round-trips', async () => {
  const manifest = JSON.parse(await readFile(new URL('../../../../verification-support/external-vehicle-provider-simulator/fixtures/marketplace/autotrader-contract-actions.json', import.meta.url), 'utf8'))
  const contributed = manifest.contributions.actions.find(action => action.actionId === 'dealership_get_vehicle')
  assert.ok(Array.isArray(contributed.params))
  assert.equal(contributed.params[0].visibility, 'INTERNAL')
  const action = { name: contributed.actionId, accessMode: 'READ', params: contributed.params, route: contributed.route, llmFacts: contributed.llmFacts }
  let value = model.patchActionDefinition(JSON.stringify({ actions: [action] }), 0, { displayName: 'Vehicle details' })
  value = model.patchActionParameter(value, 0, 0, { description: 'Updated trusted provider stock ID.' })
  assert.deepEqual(parsed(value).actions[0], { ...action, displayName: 'Vehicle details',
    params: contributed.params.map((param, index) => index === 0 ? { ...param, description: 'Updated trusted provider stock ID.' } : param) })
})

test('changing ARRAY/OBJECT types does not drop nested properties, items or unknown schema fields', () => {
  const result = parsed(model.patchActionParameter(initial, 0, 2, { type: 'OBJECT', required: true }))
  assert.deepEqual(result.actions[0].params[2], { ...actionCatalogueFixture.actions[0].params[2], type: 'OBJECT', required: true })
})

test('missing optional values remain omitted when unrelated fields change', () => {
  let result = model.patchActionParameter(initial, 0, 1, { description: 'Updated limit.' })
  result = model.patchActionDefinition(result, 1, { displayName: 'Callback' })
  assert.equal(Object.hasOwn(parsed(result).actions[0].params[1], 'askUser'), false)
  assert.equal(Object.hasOwn(parsed(result).actions[0].params[1], 'visibility'), false)
  assert.equal(Object.hasOwn(parsed(result).actions[1], 'groundingVectorSpaces'), false)
})

test('Advanced JSON edits and subsequent form edits share one lossless catalogue representation', () => {
  const advanced = structuredClone(actionCatalogueFixture.actions[0])
  advanced.params[2].items.properties.count = { type: 'INTEGER', defaultValue: 2, min: 1, max: 5 }
  advanced.futureActionField.extra = { provider: ['a', 'b'] }
  let result = model.replaceActionDefinition(initial, 0, advanced)
  result = model.patchActionParameter(result, 0, 2, { description: 'Advanced filters' })
  result = model.patchActionDefinition(result, 0, { anonymousAllowed: true })
  assert.deepEqual(parsed(result), { ...actionCatalogueFixture, actions: [{ ...advanced, anonymousAllowed: true,
    params: advanced.params.map((p, i) => i === 2 ? { ...p, description: 'Advanced filters' } : p) }, actionCatalogueFixture.actions[1]] })
})

test('add and remove actions preserve all other actions and root-level dependencies', () => {
  const added = model.appendActionDefinition(initial, ' lookup_customer ')
  assert.equal(parsed(added).actions[2].name, 'lookup_customer')
  assert.equal(parsed(added).actions[2].accessMode, 'READ')
  assert.deepEqual(parsed(added).actions.slice(0, 2), actionCatalogueFixture.actions)
  assert.deepEqual(parsed(model.removeActionDefinition(added, 2)), actionCatalogueFixture)
  assert.throws(() => model.appendActionDefinition(initial, 'LOOKUP_ORDER'), /already exists/)
  assert.throws(() => model.appendActionDefinition(initial, '  '), /required/)
})

test('add/remove parameters retain rich siblings and never convert params to a map', () => {
  const added = model.appendActionParameter(initial, 0)
  const again = model.appendActionParameter(added, 0)
  assert.equal(parsed(again).actions[0].params[3].name, 'parameter_1')
  assert.equal(parsed(again).actions[0].params[4].name, 'parameter_2')
  assert.deepEqual(parsed(model.removeActionParameter(added, 0, 3)), actionCatalogueFixture)
  assert.deepEqual(parsed(model.removeActionParameter(initial, 0, 1)).actions[0].params,
    [actionCatalogueFixture.actions[0].params[0], actionCatalogueFixture.actions[0].params[2]])
})

test('action rename is explicitly blocked in form patches and Advanced JSON; dependencies remain unchanged', () => {
  assert.throws(() => model.patchActionDefinition(initial, 0, { name: 'new_name' }), /dependency renaming/)
  assert.throws(() => model.replaceActionDefinition(initial, 0, { ...actionCatalogueFixture.actions[0], name: 'new_name' }), /dependency renaming/)
  assert.throws(() => model.replaceActionDefinition(initial, 0, { accessMode: 'READ' }), /dependency renaming/)
  assert.deepEqual(parsed(initial), actionCatalogueFixture)
})

test('removal blocks external HTTP route dependencies without mutating them', () => {
  const routes = ['lookup_order']
  assert.match(model.actionRemovalBlock(initial, 0, routes), /connector route/)
  assert.throws(() => model.removeActionDefinition(initial, 0, routes), /connector route/)
  assert.equal(model.actionRemovalBlock(initial, 1, routes), null)
})

test('removal respects all three declared confirmation-interceptor action references', () => {
  for (const dependency of [
    { trigger: { pendingActions: ['LOOKUP_ORDER'] } },
    { decision: { action: 'lookup_order' } },
    { stack: { popPreviousIfActionIn: ['lookup_order'] } },
  ]) {
    const value = JSON.stringify({ ...actionCatalogueFixture, confirmationInterceptors: [dependency] })
    assert.throws(() => model.removeActionDefinition(value, 0), /confirmation interceptor/)
  }
})

test('invalid JSON and wrong container shapes never fall back to saved values or erase existing content', () => {
  for (const value of ['{', 'null', '[]', '{"actions":{}}']) {
    assert.throws(() => model.appendActionDefinition(value, 'example'))
    assert.throws(() => model.patchActionDefinition(value, 0, { description: 'example' }))
  }
  const wrongParams = JSON.stringify({ actions: [{ name: 'example', params: { existing: 'keep' } }] })
  assert.throws(() => model.appendActionParameter(wrongParams, 0), /params field must be an array/)
  const invalidSibling = JSON.stringify({ actions: [null, actionCatalogueFixture.actions[0]], other: { keep: true } })
  assert.deepEqual(parsed(model.patchActionDefinition(invalidSibling, 1, { category: 'new' })).actions[0], null)
  assert.throws(() => model.replaceActionDefinition(initial, 0, []), /must be a JSON object/)
})

test('empty catalogue objects can be authored without introducing unrelated defaults', () => {
  assert.deepEqual(parsed(model.appendActionDefinition('{"future":true}', 'first')).future, true)
  assert.deepEqual(model.readActionCatalogue('{"future":true}').actions, [])
})

test('page integrates the same editorValue and retains existing workspace save command', async () => {
  const page = await readFile(new URL('../pages/ActionsPage.tsx', import.meta.url), 'utf8')
  assert.ok(page.includes('<ActionDefinitionEditor'))
  assert.ok(page.includes('editorValue={editorValue}'))
  assert.ok(page.includes('onChange={(value) => { setEditorValue(value); setParseError(null) }}'))
  assert.ok(page.includes('const actionsConfig = parseExactJson(editorValue)'))
  assert.ok(page.includes('return updateDeploymentDraft(loadedDraft.id, {'))
  assert.ok(page.includes('Advanced catalogue JSON'))
})

const preciseCatalogue = [
  '{"actions":[',
  '{"name":"precise_lookup","accessMode":"READ","params":[',
  '{"name":"recordId","type":"INTEGER","defaultValue":9007199254740993,"max":9223372036854775807,',
  '"future":{"providerId":123456789012345678901234567890}},',
  '{"name":"payload","type":"OBJECT","defaultValue":{"id":9007199254740995,"fraction":0.12345678901234567890123456789},',
  '"properties":{"id":{"type":"INTEGER","defaultValue":9007199254740997}}}],',
  '"route":{"request":{"body":{"ownerId":9007199254740999}}},"futureAction":{"id":9007199254741001}},',
  '{"name":"untouched","accessMode":"READ","futureSibling":{"id":9007199254741003}}',
  '],"futureRoot":{"id":9007199254741005}}',
].join('')

function assertPreciseFields(value) {
  const { root, actions } = model.readActionCatalogue(value)
  assert.equal(String(actions[0].params[0].defaultValue), '9007199254740993')
  assert.equal(String(actions[0].params[0].max), '9223372036854775807')
  assert.equal(String(actions[0].params[0].future.providerId), '123456789012345678901234567890')
  assert.equal(String(actions[0].params[1].defaultValue.id), '9007199254740995')
  assert.equal(String(actions[0].params[1].defaultValue.fraction), '0.12345678901234567890123456789')
  assert.equal(String(actions[0].params[1].properties.id.defaultValue), '9007199254740997')
  assert.equal(String(actions[0].route.request.body.ownerId), '9007199254740999')
  assert.equal(String(actions[0].futureAction.id), '9007199254741001')
  assert.equal(String(actions[1].futureSibling.id), '9007199254741003')
  assert.equal(String(root.futureRoot.id), '9007199254741005')
}

test('form edits preserve numbers above 2^53 and precise defaults throughout untouched rich fields', () => {
  let value = model.patchActionDefinition(preciseCatalogue, 0, { description: 'Updated lookup' })
  value = model.patchActionParameter(value, 0, 0, { description: 'Exact provider record ID' })
  value = model.appendActionParameter(value, 0)
  value = model.removeActionParameter(value, 0, 2)
  value = model.appendActionDefinition(value, 'temporary')
  value = model.removeActionDefinition(value, 2)
  assertPreciseFields(value)
  assert.ok(!value.includes('"isLosslessNumber"'))
})

test('Advanced action JSON uses exact parsing/serialization before subsequent form edits', () => {
  const action = model.readActionCatalogue(preciseCatalogue).actions[0]
  const advanced = exact.parseExactObject(exact.stringifyExactJson(action, 2))
  advanced.futureAction.newId = exact.exactNumber('9007199254741007')
  let value = model.replaceActionDefinition(preciseCatalogue, 0, advanced)
  value = model.patchActionDefinition(value, 0, { anonymousAllowed: true })
  assertPreciseFields(value)
  assert.equal(String(model.readActionCatalogue(value).actions[0].futureAction.newId), '9007199254741007')
})

test('lossless number wrappers are scalars, never editable JSON objects', () => {
  const number = exact.exactNumber('9007199254740993')
  assert.equal(model.isJsonObject(number), false)
  assert.throws(() => model.readActionCatalogue('9007199254740993'), /must be a JSON object/)
  assert.throws(() => model.replaceActionDefinition(preciseCatalogue, 0, number), /must be a JSON object/)
  assert.throws(() => model.patchActionDefinition('{"actions":[9007199254740993]}', 0, { description: 'Do not spread a scalar' }), /must be a JSON object/)
  assert.throws(() => model.patchActionParameter('{"actions":[{"name":"test","params":[9007199254740993]}]}', 0, 0, {}), /must be a JSON object/)
})

test('focused default, enum, resolver and nested schema edits preserve rich siblings and exact numbers', () => {
  let value = model.patchActionDefinition(preciseCatalogue, 0, { resultPresentationHint: 'DETAIL' })
  const fields = {
    allowedValues: '["OPEN","CLOSED"]',
    resolveFrom: '{"source":"ATTACHMENT_METADATA","metadataKeys":["lookupValue","id"],"future":{"id":9007199254741007}}',
    items: '{"type":"OBJECT","properties":{"id":{"type":"INTEGER","defaultValue":9007199254741009}},"future":true}',
    properties: '{"stockId":{"type":"INTEGER","defaultValue":9007199254741011}}',
    requiredProperties: '["stockId"]',
  }
  for (const [field, source] of Object.entries(fields)) {
    value = model.patchActionParameter(value, 0, 0, { [field]: model.parseActionParameterJson(field, source) })
  }
  value = model.patchActionParameter(value, 0, 0, { sensitive: true, min: model.parseActionParameterInteger('-9223372036854775808') })
  assertPreciseFields(value)
  const param = model.readActionCatalogue(value).actions[0].params[0]
  assert.equal(String(param.min), '-9223372036854775808')
  assert.equal(String(param.resolveFrom.future.id), '9007199254741007')
  assert.equal(String(param.items.properties.id.defaultValue), '9007199254741009')
  assert.equal(String(param.properties.stockId.defaultValue), '9007199254741011')
  value = model.patchActionParameter(value, 0, 1, { defaultValue: model.parseActionParameterJson('defaultValue', '{"id":9007199254741013,"fraction":0.12345678901234567890123456789}') })
  assert.equal(String(model.readActionCatalogue(value).actions[0].params[1].defaultValue.id), '9007199254741013')
})

test('focused JSON controls enforce only actual connector container contracts', () => {
  for (const field of ['allowedValues', 'requiredProperties']) {
    for (const source of ['{}', '[1]', 'null', '["ok",false]']) assert.throws(() => model.parseActionParameterJson(field, source), /array of strings/)
    assert.deepEqual(model.parseActionParameterJson(field, '[]'), [])
  }
  for (const field of ['resolveFrom', 'items', 'properties']) {
    for (const source of ['[]', 'null', '9007199254740993']) assert.throws(() => model.parseActionParameterJson(field, source), /JSON object/)
  }
  assert.throws(() => model.parseActionParameterJson('properties', '{"id":1}'), /parameter definition object/)
  for (const source of ['null', 'false', '"literal"', '[]', '{}', '9007199254740993']) {
    assert.equal(exact.stringifyExactJson(model.parseActionParameterJson('defaultValue', source)), source)
  }
  assert.throws(() => model.parseActionParameterJson('defaultValue', '{'))
})

test('optional signed Long bounds and cleared focused fields never round numbers or erase unrelated fields', () => {
  for (const input of ['9007199254740993', '9223372036854775807', '-9223372036854775808', '0']) {
    assert.equal(String(model.parseActionParameterInteger(input)), input)
  }
  for (const input of ['1.5', '1e3', 'true', '"1"', '-', '9223372036854775808', '-9223372036854775809']) {
    assert.throws(() => model.parseActionParameterInteger(input), /signed 64-bit integer/)
  }
  assert.equal(model.parseActionParameterInteger('  '), undefined)
  let value = model.patchActionParameter(preciseCatalogue, 0, 0, { max: model.parseActionParameterInteger(''), allowedValues: undefined })
  const param = model.readActionCatalogue(value).actions[0].params[0]
  assert.equal(Object.hasOwn(param, 'max'), false)
  assert.equal(Object.hasOwn(param, 'allowedValues'), false)
  assert.equal(String(param.defaultValue), '9007199254740993')
  assert.equal(String(param.future.providerId), '123456789012345678901234567890')
  assert.deepEqual(model.readActionCatalogue(value).actions[1], model.readActionCatalogue(preciseCatalogue).actions[1])
})
