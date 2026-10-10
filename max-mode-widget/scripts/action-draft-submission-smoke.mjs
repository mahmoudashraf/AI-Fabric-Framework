import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

const source = readFileSync(new URL('../src/actionDraftSubmission.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
  fileName: 'actionDraftSubmission.ts',
}).outputText
const { withActionDraftSubmission } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
)

const parameters = {
  name: 'Exact Customer',
  phone: '+44 7700 900123',
}
const request = withActionDraftSubmission(
  {
    query: 'Proceed with the details I entered.',
    conversationId: 'conversation-123',
  },
  ' request_callback ',
  parameters,
)

parameters.name = 'Changed Later'

assert.deepEqual(request, {
  query: 'Proceed with the details I entered.',
  conversationId: 'conversation-123',
  actionDraftSubmission: {
    action: 'request_callback',
    parameters: {
      name: 'Exact Customer',
      phone: '+44 7700 900123',
    },
  },
})
assert.throws(
  () => withActionDraftSubmission({ query: 'continue' }, '  ', {}),
  /requires an action name/,
)

console.log('Structured action-draft request smoke passed.')
