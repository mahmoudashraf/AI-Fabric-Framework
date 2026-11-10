import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import test from 'node:test'
import ts from 'typescript'

const require = createRequire(import.meta.url)
async function load(path, dependencies = {}) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText
  const exports = {}
  new Function('require', 'exports', code)(name => name in dependencies ? dependencies[name] : require(name), exports)
  return exports
}
const exact = await load('../lib/losslessJson.ts')
const { changedFields, validationSummary } = await load('./turnPresentation.ts', { '../lib/losslessJson': exact })
const diff = (before, after, rest = {}) => [{ path: '/configuration', before, after, ...rest }]

test('invalid saved result is attention regardless of successful message or status', () => {
  const result = validationSummary({ status: 'SAVED', message: 'All valid and connected', findings: { valid: false, connectionTested: false, issues: [{ path: '/manifest', code: 'INVALID', message: 'Missing source mapping' }] } })
  assert.equal(result.outcome, 'attention'); assert.equal(result.connectionTested, false)
  assert.deepEqual(result.issues, [{ path: '/manifest', code: 'INVALID', message: 'Missing source mapping' }])
})
test('positive error count overrides valid true and nested zero count', () => {
  const result = validationSummary({ findings: { valid: true, errorCount: 2, validation: { valid: true, errorCount: 0 } } })
  assert.equal(result.outcome, 'attention'); assert.equal(result.errorCount, 2)
})
test('nested explicit validation false and publishReady false require attention', () => {
  assert.equal(validationSummary({ findings: { validation: { valid: false } } }).outcome, 'attention')
  assert.equal(validationSummary({ findings: { publishReady: false, errorCount: 0 } }).outcome, 'attention')
})
test('valid result is separate from warning counts and does not invent a connection test', () => {
  const result = validationSummary({ findings: { valid: true, warningCount: 1 } })
  assert.equal(result.outcome, 'passed'); assert.equal(result.warningCount, 1); assert.equal(result.connectionTested, undefined)
})
test('message text, missing validation, malformed booleans and string counts never imply passed', () => {
  for (const turn of [{ message: 'Validation passed' }, { findings: { valid: 'true', errorCount: '0' } }, { findings: { configurationChecked: false } }]) assert.equal(validationSummary(turn).outcome, 'unknown')
})
test('whole item diff yields changed leaf fields only, keeping server key separate', () => {
  const result = changedFields(diff({ name: 'read', route: { url: 'old', method: 'GET' }, enabled: false }, { enabled: false, route: { method: 'GET', url: 'new' }, name: 'read' }, { key: 'read' }))
  assert.deepEqual(result, [{ path: '/configuration', key: 'read', field: '/route/url', before: 'old', after: 'new' }])
})
test('object ordering is irrelevant, JSON types and exact large numbers are preserved', () => {
  assert.deepEqual(changedFields(diff({ b: 2, a: 1 }, { a: 1, b: 2 })), [])
  assert.equal(changedFields(diff('1', 1)).length, 1)
  const result = changedFields(exact.parseExactJson('[{"path":"/configuration","before":{"n":9007199254740992},"after":{"n":9007199254740993}}]'))
  assert.equal(result.length, 1); assert.equal(exact.stringifyExactJson(result[0].after), '9007199254740993')
})
test('arrays compare by position without inferring identity fields', () => {
  const result = changedFields(diff([{ id: 'a', value: 1 }, { id: 'b', value: 2 }], [{ id: 'b', value: 2 }, { id: 'a', value: 1 }]))
  assert.deepEqual(result.map(change => change.field), ['/0/id', '/0/value', '/1/id', '/1/value'])
})
test('missing, null, additions and explicit removals stay distinct', () => {
  assert.deepEqual(changedFields(diff({}, { field: null })), [{ path: '/configuration', field: '/field', before: undefined, after: null }])
  assert.equal(changedFields(diff(null, null, { existed: false }))[0].before, undefined)
  const removed = changedFields(diff({ field: null }, null, { operation: 'REMOVE' }))
  assert.equal(removed[0].before, null); assert.equal(removed[0].after, undefined)
  assert.equal(changedFields(diff({ value: 1 }, null, { operation: 'REMOVE_ITEM', key: 'a' }))[0].field, '/value')
})
test('pointer escaping and own object fields are formal, not label guesses', () => {
  const result = changedFields(diff({}, JSON.parse('{"a/b~c":true,"__proto__":"changed"}')))
  assert.deepEqual(result.map(change => change.field), ['/a~1b~0c', '/__proto__'])
  assert.ok(result.every(change => change.before === undefined))
})
test('no summary is fabricated from requested changes or malformed diff', () => {
  assert.deepEqual(changedFields({ changes: [{ path: '/configuration', value: 'proposed' }] }), [])
  assert.deepEqual(changedFields([{ path: '/configuration', after: 'missing before' }, null]), [])
})
