import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('./pagePresentation.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } })
const { assistantPage, assistantPages, editorContext, sameResourceSources, userTurnText } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
const scope = { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', task: 'MODELS' }

test('every registered section has product guidance and an explicit command', () => {
  const sections = ['ACTIONS', 'CONNECTIONS', 'KNOWLEDGE', 'DOCUMENTS', 'LIVE_DATA', 'STORAGE', 'SEARCH', 'BEHAVIOUR', 'INSTRUCTIONS', 'MODELS', 'HUMAN_SUPPORT', 'ACTION_REVIEWS', 'SPECIALISTS', 'KIT', 'DATA_PLUGIN', 'ENGAGE']
  assert.deepEqual(Object.keys(assistantPages).sort(), sections.sort())
  for (const section of sections) {
    const page = assistantPage({ ...scope, editorContext: { section } })
    assert.ok(page.title && page.welcome && page.command)
    assert.ok(page.command.startsWith('Prepare ') || page.command.startsWith('Explain '))
  }
})
test('same task can present response models and embedding/search without mutating scope', () => {
  const search = { ...scope, editorContext: { section: 'SEARCH', selectedDefinitionIds: ['exact-id'] } }
  const before = structuredClone(search)
  assert.equal(assistantPage(scope).command, 'Prepare model settings')
  assert.equal(assistantPage(search).command, 'Prepare search settings')
  assert.deepEqual(editorContext(search), before.editorContext); assert.deepEqual(search, before)
  assert.deepEqual(editorContext(scope), { section: 'MODELS' })
})
test('PREPARE transcript shows registered command plus redacted hint, not server intent', () => {
  const turn = { command: 'PREPARE', query: 'Server-owned long instructions must not appear', hint: 'Only catalogue reads', editorContext: { section: 'ACTIONS' } }
  assert.equal(userTurnText(turn, scope), 'Prepare actions\nOnly catalogue reads')
  assert.equal(userTurnText({ ...turn, hint: '' }, scope), 'Prepare actions')
  assert.equal(userTurnText({ ...turn, hint: undefined }, scope), 'Prepare actions')
})
test('ordinary user text is never interpreted or rewritten for display', () => {
  const query = '  PREPARE actions?\nKeep all punctuation.  '
  assert.equal(userTurnText({ query }, scope), query)
})
test('reuse offers same resource only, never attaches or mutates authorized results', () => {
  const rows = [
    { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-37', sourceId: 'same', task: 'ACTIONS' },
    { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: 'brain-38', sourceId: 'other' },
    { resourceKind: 'PRIVATE_DATA_PLUGIN', resourceId: 'brain-37', sourceId: 'different-kind' },
    { sourceId: 'unknown-metadata' },
  ]
  const before = structuredClone(rows)
  assert.deepEqual(sameResourceSources(rows, scope), [rows[0]])
  assert.deepEqual(rows, before)
})
