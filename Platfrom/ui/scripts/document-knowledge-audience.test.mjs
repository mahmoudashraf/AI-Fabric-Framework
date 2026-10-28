import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/pages/documentKnowledgeAudience.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText
const { hasApprovedAnonymousDocumentSource: detects } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

async function seededManifest(file, pluginId) {
  const sql = await readFile(new URL(`../../backend/src/main/resources/db/migration/${file}`, import.meta.url), 'utf8')
  for (const match of sql.matchAll(/'(\{[\s\S]*?\})'/g)) {
    const manifest = JSON.parse(match[1])
    if (manifest.pluginId === pluginId) return manifest
  }
  throw new Error(`Missing seed manifest: ${pluginId}`)
}
const dealership = await seededManifest('V152__dealership_public_document_knowledge_packages.sql', 'mkp-data-dealership-knowledge-v1')
const privateGeneric = await seededManifest('V148__document_knowledge_marketplace_products.sql', 'mkp-data-document-knowledge-s3')
const publicGeneric = structuredClone(dealership)
publicGeneric.pluginId = 'mkp-data-document-knowledge-s3'
publicGeneric.version = '1.2.0'
publicGeneric.contributions.knowledgeSources[0].sourceKey = 'generic-public-document-knowledge'

test('existing dealership public source uses its declared contract', () => {
  assert.equal(detects(dealership, 'document-knowledge'), true)
})
test('generic public source works without a dealership identity or installed consent', () => {
  assert.equal(detects(publicGeneric, 'document-knowledge'), true)
})
test('private generic document contract is not public', () => {
  assert.equal(detects(privateGeneric, 'document-knowledge'), false)
})
test('anonymous access alone does not imply approved public documents', () => {
  const value = structuredClone(publicGeneric)
  delete value.contributions.knowledgeSources[0].filters.publicationStatus
  assert.equal(detects(value, 'document-knowledge'), false)
})
test('approval filter alone does not imply anonymous access', () => {
  const value = structuredClone(publicGeneric)
  value.contributions.knowledgeSources[0].authModes = ['PUBLIC_RUNTIME_AUTHENTICATED']
  assert.equal(detects(value, 'document-knowledge'), false)
})
test('public source must refer to the selected document dataset', () => {
  assert.equal(detects(publicGeneric, 'another-dataset'), false)
  for (const field of ['datasetRef', 'filters']) {
    const value = structuredClone(publicGeneric)
    if (field === 'filters') value.contributions.knowledgeSources[0].filters.datasetId = 'another-dataset'
    else value.contributions.knowledgeSources[0][field] = 'another-dataset'
    assert.equal(detects(value, 'document-knowledge'), false)
  }
})
test('missing and malformed contracts do not classify documents as public', () => {
  for (const value of [undefined, null, {}, { contributions: { datasets: {}, knowledgeSources: [null] } }]) {
    assert.equal(detects(value, 'document-knowledge'), false)
  }
  const value = structuredClone(publicGeneric)
  value.contributions.datasets[0].ingestionMode = 'HTTP'
  assert.equal(detects(value, 'document-knowledge'), false)
})
