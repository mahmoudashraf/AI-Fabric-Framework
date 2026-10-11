import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

const source = readFileSync(new URL('../src/requestSemantics.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
  fileName: 'requestSemantics.ts',
}).outputText
const {
  explicitAttachmentVectorSpace,
  resolveChatRequestSemantics,
} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

const query = 'Compare the attached options and explain the policy.'
const scoped = resolveChatRequestSemantics({
  query,
  currentPosition: 'landing',
  currentMode: 'executor',
  searchCategory: 'Electric vehicles',
})
assert.equal(scoped.query, query)
assert.equal(scoped.position, 'search')
assert.equal(scoped.mode, 'navigator')
assert.equal(scoped.searchCategory, 'Electric vehicles')

const explicitTool = resolveChatRequestSemantics({
  query,
  currentPosition: 'landing',
  currentMode: 'navigator',
  actionPosition: 'cart',
  actionMode: 'executor',
})
assert.equal(explicitTool.query, query)
assert.equal(explicitTool.position, 'cart')
assert.equal(explicitTool.mode, 'executor')

assert.equal(explicitAttachmentVectorSpace({
  type: 'order',
  metadata: { category: 'order' },
}), undefined)
assert.equal(explicitAttachmentVectorSpace({
  vectorSpace: ' dealer-vehicle ',
  metadata: { entityType: 'document' },
}), 'dealer-vehicle')
assert.equal(explicitAttachmentVectorSpace({
  metadata: { entityType: 'document' },
}), 'document')

const attachmentControllerSource = readFileSync(
  new URL('../src/hooks/useAttachmentsController.ts', import.meta.url),
  'utf8',
)
assert.doesNotMatch(attachmentControllerSource, /setCurrent(?:Mode|Position)/)
assert.doesNotMatch(attachmentControllerSource, /applyAttachmentFocus/)

console.log('AI Workspace semantic-boundary smoke passed.')
