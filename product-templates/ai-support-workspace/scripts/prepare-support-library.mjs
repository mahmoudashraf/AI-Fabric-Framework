#!/usr/bin/env node
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repo = resolve(root, '../..')
const library = JSON.parse(readFileSync(resolve(root, 'review/support-library.json'), 'utf8'))
const args = process.argv.slice(2)
assert.ok(args.length === 0 || args.length === 2 && args[0] === '--output-dir',
  'Usage: node prepare-support-library.mjs [--output-dir <new-or-existing-output-directory>]')
const output = args.length ? resolve(args[1]) : null
const contains = (parent, child) => {
  const path = relative(parent, child)
  return path !== '..' && !path.startsWith('..' + sep) && !path.startsWith(sep)
}
if (output) {
  assert.ok(!contains(resolve(root, 'documents'), output), 'Output cannot enter an indexed source directory')
  assert.ok(!existsSync(output), 'Use a new output directory so stale files cannot enter the delivery package')
}

const evidence = new Map()
for (const file of library.provenanceFiles) {
  const source = resolve(root, file)
  assert.ok(contains(resolve(root, 'review'), source))
  for (const document of JSON.parse(readFileSync(source, 'utf8')).documents) {
    assert.ok(!evidence.has(document.id), 'Duplicate review identity')
    assert.ok(document.sourceFiles.length > 0 && document.claims.length > 0)
    for (const path of document.sourceFiles) {
      const reference = resolve(repo, path)
      assert.ok(contains(repo, reference) && !path.includes('PRIVATE') && !path.includes('.codex/'), 'Unsafe review reference')
      readFileSync(reference)
    }
    evidence.set(document.id, document)
  }
}

const identities = new Set()
const documents = library.documents.map(document => {
  assert.ok(!identities.has(document.id), 'Duplicate document identity')
  identities.add(document.id)
  const source = resolve(root, document.path)
  assert.ok(contains(resolve(root, 'documents/loomai-support'), source) && source.endsWith('.txt'))
  const review = evidence.get(document.id)
  assert.equal(review?.path, document.path, 'Every guide needs its own matching source review')
  const bytes = readFileSync(source)
  const text = bytes.toString('utf8')
  assert.ok(text.length > 1500 && !text.includes('\uFFFD'), 'Guide must be substantive UTF-8 text')
  assert.ok(text.startsWith('Title: ') && text.includes(`Document ID: ${document.id}\n`))
  assert.ok(text.includes('Reviewed: ' + library.reviewedAt))
  const title = text.slice('Title: '.length, text.indexOf('\n')).trim()
  const objectKey = 'guides/' + relative(resolve(root, 'documents/loomai-support'), source).split(sep).join('/')
  if (output) {
    const target = resolve(output, objectKey)
    assert.ok(contains(output, target))
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, bytes)
  }
  return { id: document.id, title, objectKey, bytes: bytes.length,
    sha256: createHash('sha256').update(bytes).digest('hex'),
    metadata: { sourceCategory: document.sourceCategory, libraryId: library.libraryId,
      contentVersion: library.contentVersion, publicationStatus: 'PUBLIC_APPROVED', language: library.language } }
})
assert.equal(evidence.size, documents.length, 'Review set must equal the indexed guide set')
const manifest = { schemaVersion: 1, libraryId: library.libraryId, contentVersion: library.contentVersion,
  contentStatus: library.contentStatus, deploymentStatus: library.deploymentStatus,
  indexingPrefix: 'guides/', sourceDeletionAuthorized: false, documents }
if (output) {
  mkdirSync(output, { recursive: true })
  writeFileSync(resolve(output, 'delivery-manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
}
console.log(JSON.stringify({ status: 'PASS', libraryId: library.libraryId, contentVersion: library.contentVersion,
  documentCount: documents.length, bytes: documents.reduce((n, d) => n + d.bytes, 0),
  outputDirectory: output, indexed: false, uploaded: false, ...(output ? {} : { manifest }) }, null, 2))
