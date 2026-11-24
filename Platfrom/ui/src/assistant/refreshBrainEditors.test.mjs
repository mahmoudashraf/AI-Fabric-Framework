import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { QueryClient, QueryObserver } from '@tanstack/react-query'
import ts from 'typescript'

const source = await readFile(new URL('./refreshBrainEditors.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } })
const { refreshBrainEditors } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

function fixture() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  const subscriptions = []
  const observe = (key, queryFn) => {
    const observer = new QueryObserver(client, { queryKey: key, queryFn, initialData: { revision: 1 }, staleTime: Infinity })
    subscriptions.push(observer.subscribe(() => {}))
    return observer
  }
  return { client, observe, close: () => { subscriptions.forEach(unsubscribe => unsubscribe()); client.clear() } }
}

test('failed webhook telemetry does not lock a successfully refreshed draft or conceal its own error', async () => {
  const f = fixture()
  try {
    let draftReads = 0, telemetryReads = 0, unrelatedReads = 0
    f.observe(['deployment-draft', 'brain-1'], async () => { draftReads++; return { revision: 2 } })
    const telemetry = f.observe(['deployment-webhooks-overview', 'brain-1'], async () => {
      telemetryReads++; throw new Error('Runtime webhook admin request failed with HTTP 401.')
    })
    f.observe(['deployment-draft', 'brain-2'], async () => { unrelatedReads++; return { revision: 9 } })
    await telemetry.refetch()
    assert.equal(telemetryReads, 1)
    await refreshBrainEditors(f.client, 'brain-1', 'operator-1')
    assert.equal(draftReads, 1)
    assert.equal(telemetryReads, 1)
    assert.equal(unrelatedReads, 0)
    assert.deepEqual(f.client.getQueryData(['deployment-draft', 'brain-1']), { revision: 2 })
    const state = f.client.getQueryState(['deployment-webhooks-overview', 'brain-1'])
    assert.equal(state.status, 'error')
    assert.match(state.error.message, /HTTP 401/)
    assert.equal(state.isInvalidated, true)
    assert.equal(f.client.getQueryState(['deployment-draft', 'brain-2']).isInvalidated, false)
  } finally { f.close() }
})

test('a required draft refresh failure remains visible as an invalidated read', async () => {
  const f = fixture()
  try {
    f.observe(['deployment-draft', 'brain-1'], async () => { throw new Error('Draft access denied') })
    await assert.rejects(refreshBrainEditors(f.client, 'brain-1', 'operator-1'), /Draft access denied/)
    assert.equal(f.client.getQueryState(['deployment-draft', 'brain-1']).status, 'error')
    assert.equal(f.client.getQueryState(['deployment-draft', 'brain-1']).isInvalidated, true)
  } finally { f.close() }
})

test('delegated specialist refresh remains bound to the current operator identity', async () => {
  const f = fixture()
  try {
    let ownedReads = 0, otherReads = 0
    f.observe(['customer-specialist-assistance', 'operator-1'], async () => { ownedReads++; return { revision: 2 } })
    f.observe(['customer-specialist-assistance', 'operator-2'], async () => { otherReads++; return { revision: 3 } })
    await refreshBrainEditors(f.client, 'brain-1', 'operator-1')
    assert.equal(ownedReads, 1)
    assert.equal(otherReads, 0)
  } finally { f.close() }
})
