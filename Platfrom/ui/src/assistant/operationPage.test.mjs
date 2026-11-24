import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const code = ts.transpileModule(await readFile(new URL('./operationPage.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
const exports = {}
new Function('exports', code)(exports)
const { readOperationPage, uniqueOperations, operationPageSize } = exports
const operation = (id, rest = {}) => ({ operationId: id, action: 'authoring_update_draft', status: 'EXECUTED', published: false, ...rest })
const page = rest => ({ goalId: 'original-goal', operations: [operation('step:101')], offset: 100, limit: 20, total: 105, hasMore: false, ...rest })

test('receipt pages beyond the first hundred retain their exact goal, offset and server total', () => {
  assert.equal(operationPageSize, 20)
  const result = readOperationPage(page(), 'original-goal', 100, 20)
  assert.equal(result.operations[0].operationId, 'step:101'); assert.equal(result.total, 105)
  assert.equal(result.offset, 100); assert.equal(result.recordCount, 1)
})
test('receipt identities deduplicate within a page without accumulating another page or inventing saves', () => {
  const updated = operation('same', { receipt: { actual: 'latest' } })
  const result = readOperationPage(page({ operations: [operation('same'), updated, operation('unknown', { status: 'DISPATCHING' })] }), 'original-goal', 100, 20)
  assert.deepEqual(result.operations, [updated]); assert.equal(result.recordCount, 3)
  assert.deepEqual(uniqueOperations([operation('one'), operation('one')]), [operation('one')])
})
test('foreign goal responses and malformed or non-advancing paging cursors stay visible errors', () => {
  for (const patch of [{ goalId: 'foreign' }, { offset: 0 }, { limit: 100 }, { total: -1 }, { total: Infinity }, { hasMore: true }, { hasMore: true, nextOffset: 100 }, { hasMore: true, nextOffset: 101 }, { operations: Array.from({ length: 21 }, (_, id) => operation(String(id))) }]) {
    assert.throws(() => readOperationPage(page(patch), 'original-goal', 100, 20), /matched to this goal/)
  }
  assert.equal(readOperationPage(page({ hasMore: true, nextOffset: 120, total: 125 }), 'original-goal', 100, 20).nextOffset, 120)
})
