import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('./scope.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } })
const { brainAssistantScope, operation, scopeIdentity } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
const root = '/smart-brains/brain-37'
const expected = {
  '': 'KIT', '/': 'KIT', '/capabilities': 'ACTIONS', '/capabilities/actions': 'ACTIONS',
  '/capabilities/action%3Aread': 'ACTIONS', '/capabilities/install%3Aplugin%3Aaction%3Aread': 'ACTIONS',
  '/knowledge': 'KNOWLEDGE', '/knowledge/documents': 'KNOWLEDGE', '/knowledge/sources/source-1': 'KNOWLEDGE',
  '/knowledge/storage': 'KNOWLEDGE', '/knowledge/discover': 'KNOWLEDGE', '/knowledge/proof': 'KNOWLEDGE', '/knowledge/retention': 'KNOWLEDGE',
  '/knowledge/connections': 'CONNECTIONS', '/knowledge/connections/source-1': 'CONNECTIONS', '/knowledge/search': 'MODELS',
  '/responses': 'BEHAVIOUR', '/responses/instructions': 'BEHAVIOUR', '/responses/advanced': 'BEHAVIOUR',
  '/models': 'MODELS', '/human-support': 'HUMAN_SUPPORT', '/action-reviews': 'HUMAN_SUPPORT',
  '/human-support/specialists': 'SPECIALISTS', '/advanced/providers': 'MODELS', '/advanced/configuration': 'KIT',
}
for (const [path, task] of Object.entries(expected)) test(`route ${path || '/'} binds ${task}`, () => {
  assert.deepEqual(brainAssistantScope(root + path, '?targetProfileId=production&query=change+models', 'brain-37'), { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task })
})
test('formal connection and specialist selectors bind their tasks', () => {
  assert.equal(brainAssistantScope(root + '/capabilities', '?view=connection', 'brain-37').task, 'CONNECTIONS')
  assert.equal(brainAssistantScope(root + '/capabilities/action%3Aread', '?view=connection', 'brain-37').task, 'CONNECTIONS')
  assert.equal(brainAssistantScope(root + '/human-support/specialists', '', 'brain-37').task, 'SPECIALISTS')
  assert.equal(brainAssistantScope(root + '/advanced/configuration', '?panel=specialists', 'brain-37').task, 'KIT', 'No invented specialist selector on provider JSON')
})
test('unrelated, unavailable and operational routes have no assistant', () => {
  for (const path of ['/try', '/channels', '/updates', '/advanced/runtime', '/advanced/recovery', '/advanced/diagnostics', '/capabilities/add', '/capabilities/unknown', '/capabilities/action%3Aread/extra', '/knowledge/sources/id/extra', '/unknown', '/responses/unknown']) assert.equal(brainAssistantScope(root + path, '', 'brain-37'), null, path)
  assert.equal(brainAssistantScope('/smart-brains/other/models', '', 'brain-37'), null)
  assert.equal(brainAssistantScope('/smart-brains/brain-37-suffix/models', '', 'brain-37'), null)
  assert.equal(brainAssistantScope('/smart-brains//models', '', ''), null)
})
test('task and resource identity are unaffected by natural language parameters', () => {
  assert.equal(brainAssistantScope(root + '/responses', '?query=configure+models&task=MODELS', 'brain-37').task, 'BEHAVIOUR')
  assert.equal(scopeIdentity({ resourceKind: 'PRIVATE_DATA_PLUGIN', resourceId: 'plugin-1', task: 'DATA_PLUGIN' }), 'PRIVATE_DATA_PLUGIN:plugin-1:DATA_PLUGIN')
})
test('operations require server-issued IDs and preserve source pointers', () => {
  assert.equal(operation({ method: 'GET', path: '/items' }), null)
  assert.deepEqual(operation({ id: 'read', method: 'GET', path: '/items', summary: 'Read items', sourcePointer: '/paths/~1items/get' }), { id: 'read', label: 'GET /items Read items', pointer: '/paths/~1items/get' })
})
