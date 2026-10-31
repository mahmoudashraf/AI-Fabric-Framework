import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const script = resolve(dirname(fileURLToPath(import.meta.url)), 'prepare-support-library.mjs')
const run = args => spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' })

test('all real support guides have matching source review and non-fictional library metadata', () => {
  const result = run([])
  assert.equal(result.status, 0, result.stderr)
  const report = JSON.parse(result.stdout)
  assert.equal(report.documentCount, 13)
  assert.equal(report.indexed, false)
  assert.equal(report.uploaded, false)
  assert.equal(report.manifest.libraryId, 'loomai-customer-support')
  assert.equal(report.manifest.sourceDeletionAuthorized, false)
  assert.equal(new Set(report.manifest.documents.map(document => document.id)).size, 13)
})

test('delivery includes only guide bytes under the indexing prefix and a separate checksum manifest', () => {
  const parent = mkdtempSync(join(tmpdir(), 'loomai-support-library-'))
  try {
    const output = join(parent, 'prepared')
    const result = run(['--output-dir', output])
    assert.equal(result.status, 0, result.stderr)
    assert.deepEqual(readdirSync(output).sort(), ['delivery-manifest.json', 'guides'])
    const manifest = JSON.parse(readFileSync(join(output, 'delivery-manifest.json'), 'utf8'))
    assert.equal(readdirSync(join(output, 'guides')).length, 13)
    for (const document of manifest.documents) {
      const bytes = readFileSync(join(output, document.objectKey))
      assert.equal(bytes.length, document.bytes)
      assert.equal(createHash('sha256').update(bytes).digest('hex'), document.sha256)
      assert.equal(document.metadata.publicationStatus, 'PUBLIC_APPROVED')
      assert.equal(document.objectKey.endsWith('.txt'), true)
    }
    const repeated = run(['--output-dir', output])
    assert.notEqual(repeated.status, 0)
    assert.match(repeated.stderr, /Use a new output directory/)
  } finally { rmSync(parent, { recursive: true, force: true }) }
})

test('delivery cannot write inside the indexed source library', () => {
  const output = resolve(dirname(script), '../documents/loomai-support/unsafe-output')
  const result = run(['--output-dir', output])
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /Output cannot enter an indexed source directory/)
})
