import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

const source = readFileSync(new URL('../src/utils.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
  fileName: 'utils.ts',
}).outputText
const { sanitizeRequestContext, withRequestContext } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
)

const unsafeContext = {
  dealershipId: 'dealer-demo-001',
  pageType: 'vehicle-detail',
  preferredVectorSpaces: ['dealer-vehicle', 'document'],
  preferred_vector_spaces: ['document'],
  retrievalVectorSpaces: ['document'],
  vectorSpace: 'dealer-vehicle',
  vector_space: 'document',
  entityType: 'dealer-vehicle',
  entity_type: 'document',
}

assert.deepEqual(sanitizeRequestContext(unsafeContext), {
  dealershipId: 'dealer-demo-001',
  pageType: 'vehicle-detail',
})
assert.deepEqual(withRequestContext({ query: 'delivery policy' }, unsafeContext), {
  query: 'delivery policy',
  context: {
    dealershipId: 'dealer-demo-001',
    pageType: 'vehicle-detail',
  },
})

console.log('Request context routing-boundary smoke passed.')
