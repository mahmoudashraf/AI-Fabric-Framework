import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source = await readFile(new URL('../src/components/customer/customerEngageSelection.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } })
const { customerEngageSelection } = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const assignment = (consumerId, customerId = 'owner', status = 'ACTIVE') => ({ consumerId, customerId, status })
test('multiple active customer assignments require an explicit choice', () => {
  assert.equal(customerEngageSelection([assignment('first'), assignment('second')], 'owner').consumerId, '')
})
test('an explicit owned assignment is retained rather than replaced by the first', () => {
  assert.deepEqual(customerEngageSelection([assignment('first'), assignment('second')], 'owner', 'second'), { consumerId: 'second', error: null })
})
test('foreign, disabled or unknown requested assignments fail visibly without selecting another', () => {
  for (const requested of ['foreign', 'disabled', 'missing']) {
    const result = customerEngageSelection([assignment('mine'), assignment('foreign', 'other'), assignment('disabled', 'owner', 'DISABLED')], 'owner', requested)
    assert.equal(result.consumerId, ''); assert.ok(result.error)
  }
})
test('only the sole active owned assignment can be a default', () => {
  assert.equal(customerEngageSelection([assignment('mine'), assignment('foreign', 'other'), assignment('disabled', 'owner', 'DISABLED')], 'owner').consumerId, 'mine')
  assert.equal(customerEngageSelection([], 'owner').consumerId, '')
})
