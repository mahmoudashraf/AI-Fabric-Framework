import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { withPluginJsonSupport } from './plugin-json-test-support.mjs'

const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
const url = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const documentUrl = url(compile(withPluginJsonSupport(await readFile(new URL('../src/pluginLibrary/creatorDocument.ts', import.meta.url), 'utf8'))))
const document = await import(documentUrl)
const requirements = await import(url(compile((await readFile(new URL('../src/pluginLibrary/creatorRequirements.ts', import.meta.url), 'utf8')).replace("'./creatorDocument'", JSON.stringify(documentUrl)))))

test('new HTTP source names the existing required inputs without requiring optional purpose or root-array paths', () => {
  const source = document.newDefinition('private-test')
  const parsed = document.parseDocument(source).document
  assert.deepEqual(requirements.missingCreatorRequirements(parsed).map(item => item.label), [
    'Display name', 'Relative read route', 'Stable record ID JSON pointer', 'Account ownership JSON pointer', 'Searchable content',
  ])
  assert.equal(requirements.requirementAt(parsed, ['description']), false)
  assert.equal(requirements.requirementAt(parsed, ['contributions', 'datasets', 0, 'syncConnector', 'httpSource', 'mapping', 'recordsJsonPointer']), false)
  assert.equal(document.newDefinition('private-test'), source, 'Presentation must not mutate the source')
})

test('required document boolean false is a supplied value, not a forced true setting', () => {
  const parsed = document.parseDocument(document.newDefinition('private-files', 'S3_COMPATIBLE_OBJECT_STORAGE')).document
  assert.deepEqual(requirements.missingCreatorRequirements(parsed).map(item => item.label), ['Display name'])
  assert.equal(parsed.contributions.datasets[0].documentPolicy.trustedAutoIndexingAllowed, false)
  const requirement = requirements.creatorRequirements(parsed).find(item => item.path.at(-1) === 'trustedAutoIndexingAllowed')
  assert.ok(requirement)
})

test('token expiry and credential fields reflect the selected structured contract', () => {
  let source = document.newDefinition('private-token')
  const auth = ['contributions', 'datasets', 0, 'syncConnector', 'connectionProfile', 'auth']
  source = document.patchDocument(source, [{ path: auth, value: { strategy: 'FORM_TOKEN_EXCHANGE', tokenPath: '/authenticate', tokenJsonPointer: '/access_token', absoluteExpiryJsonPointer: '/expires_at', credentialFields: [{ name: 'key', secretRefField: '' }] } }])
  const parsed = document.parseDocument(source).document
  assert.equal(requirements.requirementAt(parsed, [...auth, 'relativeExpiryJsonPointer']), false)
  const missing = requirements.missingCreatorRequirements(parsed)
  assert.ok(missing.some(item => item.path.at(-1) === 'secretRefField'))
  assert.ok(!missing.some(item => item.path.at(-1) === 'absoluteExpiryJsonPointer' || item.path.at(-1) === 'relativeExpiryJsonPointer'))
})

test('required mapping rows use exact escaped JSON paths and array indices for review navigation', () => {
  const input = document.patchDocument(document.newDefinition('private-map'), [{ path: ['contributions', 'datasets', 0, 'syncConnector', 'httpSource', 'mapping', 'contentFields', 'a/b~c'], value: '' }])
  const missing = requirements.missingCreatorRequirements(document.parseDocument(input).document)
  const mapping = missing.find(item => item.path.at(-1) === 'a/b~c')
  assert.equal(requirements.pointerPath(mapping.path), '/contributions/datasets/0/syncConnector/httpSource/mapping/contentFields/a~1b~0c')
  assert.equal(mapping.step, 2)
  assert.equal(mapping.dataset, 0)
})
