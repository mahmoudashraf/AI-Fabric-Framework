import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const source = await readFile(new URL('../src/lib/losslessJson.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
const exact = {}
new Function('require', 'exports', compiled)(require, exact)

test('source round-trip keeps large integers, decimal precision, nulls and array order', () => {
  const input = '{"counter":12345678901234567890123456789,"ratio":0.123456789012345678901,"huge":1e400,"small":1e-400,"negativeZero":-0,"future":{"null":null,"array":[3,1,2]}}'
  const data = exact.parseExactObject(input)
  data.label = 'Edited elsewhere'
  const output = exact.stringifyExactJson(data)
  assert.equal(output, input.slice(0, -1) + ',"label":"Edited elsewhere"}')
  assert.equal(exact.stringifyExactJson(exact.cloneExactJson(data)), output)
})

test('ordinary revision counters are numbers, configuration decimals are exact', () => {
  const data = exact.parseExactObject('{"revision":42,"decimal":2.370}')
  assert.equal(data.revision, 42)
  assert.equal(exact.isExactNumber(data.decimal), true)
  assert.equal(exact.stringifyExactJson(data), '{"revision":42,"decimal":2.370}')
})

test('duplicate keys and invalid source are rejected, not silently repaired', () => {
  for (const value of ['{"id":1,"id":2}', '{', 'null', '[1,2]', '42']) assert.throws(() => exact.parseExactObject(value))
  assert.throws(() => exact.exactNumber('NaN'))
  assert.throws(() => exact.exactNumber('"2"'))
  assert.equal(exact.stringifyExactJson(exact.exactNumber('9007199254740993')), '9007199254740993')
})

test('prototype-shaped property names remain inert own data fields', () => {
  const input = '{"__proto__":{"polluted":true},"constructor":{"prototype":{"keep":1}},"toString":null}'
  const data = exact.parseExactObject(input)
  assert.equal(Object.prototype.hasOwnProperty.call(data, '__proto__'), true)
  assert.equal(Object.getPrototypeOf(data), Object.prototype)
  assert.equal({}.polluted, undefined)
  assert.equal(exact.stringifyExactJson(data), input)
})

test('plugin network paths use exact parsing and request serialization without affecting all API reads', async () => {
  const api = await readFile(new URL('../src/api/platformApi.ts', import.meta.url), 'utf8')
  for (const name of ['fetchMarketplacePlugin', 'fetchDeploymentMarketplaceInstalls', 'createDeploymentMarketplaceInstall', 'updateDeploymentMarketplaceInstall']) {
    const body = api.slice(api.indexOf(`export function ${name}(`)).split('\nexport function ')[0]
    assert.match(body, /parseExactJson/)
    if (name.startsWith('create') || name.startsWith('update')) assert.match(body, /stringifyExactJson/)
  }
})
