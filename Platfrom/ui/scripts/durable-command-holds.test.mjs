import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/components/customer/durableCommandHolds.ts', import.meta.url), 'utf8')
const output = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { createDurableCommandHolds } = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`)
function fixture() {
  const rows = new Map()
  const storage = { getItem: key => rows.get(key) ?? null, setItem: (key, value) => { rows.set(key, value) } }
  let queue = Promise.resolve()
  const exclusive = (_, work) => { const job = queue.then(work); queue = job.catch(() => {}); return job }
  return { rows, storage, fresh: () => createDurableCommandHolds({ storage: () => storage, exclusive }) }
}

test('a fresh store sees the exact durable intent after remount without persisting command contents', async () => {
  const f = fixture(), command = JSON.stringify({ reason: 'private-free-text', token: 'not-to-store', expectedRevision: 1 })
  await f.fresh().claim('customer-resource', 'original-key', command, () => true)
  const hold = f.fresh().read('customer-resource')
  assert.equal(hold.operationKey, 'original-key')
  assert.equal(hold.commandSha256, createHash('sha256').update(command).digest('hex'))
  assert.equal(hold.scope, 'customer-resource')
  assert.doesNotMatch([...f.rows.values()].join(''), /private-free-text|not-to-store|expectedRevision/)
})

test('remount, new key and another actor cannot overwrite the original held resource', async () => {
  const f = fixture()
  await f.fresh().claim('same-owned-resource', 'first-key', '{}', () => true)
  const original = [...f.rows.values()][0]
  await assert.rejects(f.fresh().claim('same-owned-resource', 'new-key', '{"actor":"different"}', () => true), /already recorded/)
  assert.equal([...f.rows.values()][0], original)
  await f.fresh().claim('different-owned-resource', 'legitimate-other-key', '{}', () => true)
  assert.equal(f.rows.size, 2)
})

test('exclusive claims across store instances admit one intent only', async () => {
  const f = fixture()
  const results = await Promise.allSettled([f.fresh().claim('shared', 'one', '{}', () => true), f.fresh().claim('shared', 'two', '{}', () => true)])
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1)
  assert.equal(results.filter(result => result.status === 'rejected').length, 1)
  assert.equal(f.rows.size, 1)
})

test('corrupt or unknown hold records fail closed without deletion or fallback', () => {
  for (const raw of ['invalid-json', '{}', JSON.stringify({ schema: 2 }), JSON.stringify({ schema: 1, scope: 's',
    operationKey: ['key'], commandSha256: 'a'.repeat(64), recordedAt: '2026-10-20T00:00:00Z' })]) {
    const f = fixture(); f.rows.set('platform.command-hold.v1:s', raw)
    assert.throws(() => f.fresh().read('s'), /protection is unavailable/)
    assert.equal([...f.rows.values()][0], raw)
  }
})

test('storage write or exact readback failure cannot permit submission', async () => {
  for (const storage of [{ getItem: () => null, setItem: () => { throw new Error('quota') } },
    { getItem: () => null, setItem: () => {} }]) {
    const holds = createDurableCommandHolds({ storage: () => storage, exclusive: async (_, work) => work() })
    await assert.rejects(holds.claim('s', 'key', '{}', () => true), /protection is unavailable/)
  }
})

test('scope changes and unavailable exclusive locking deny without writing an intent', async () => {
  const f = fixture()
  await assert.rejects(f.fresh().claim('s', 'key', '{}', () => false), /scope changed/)
  assert.equal(f.rows.size, 0)
  const unsupported = createDurableCommandHolds({ storage: () => f.storage, exclusive: async () => { throw new Error('exclusive locking unavailable') } })
  await assert.rejects(unsupported.claim('s', 'key', '{}', () => true), /exclusive locking unavailable/)
  assert.equal(f.rows.size, 0)
})
