import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

function loadSource(path, dependencies = {}) {
  const compiled = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: path,
  }).outputText
  const exports = {}
  new Function('require', 'exports', compiled)((id) => {
    assert.ok(Object.hasOwn(dependencies, id), `Unexpected runtime dependency: ${id}`)
    return dependencies[id]
  }, exports)
  return exports
}

function deepFreeze(value) {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value
  for (const child of Object.values(value)) deepFreeze(child)
  return Object.freeze(value)
}

const config = { getWidgetConfig: () => ({}), emitEvent: () => {} }
const chatResult = loadSource('../src/chatResult.ts', { '@/config': config })
const { canonicalChatResult } = chatResult

function documentsFor(payload) {
  const before = structuredClone(payload)
  deepFreeze(payload)
  const documents = canonicalChatResult(payload).sanitizedPayload.data.documents || []
  assert.deepEqual(payload, before, 'Representation must not mutate the response')
  return documents
}

const handbook = {
  id: 'chunk-handbook',
  content: 'Laboratory handbook: label every sample before storage.',
  score: 0.82,
  similarity: 0.79,
  metadata: {
    knowledgeSourceId: 'laboratory-library',
    _aiDocumentSourceId: 'uploaded-handbook',
    _aiDocumentSourceName: 'sample-handbook.txt',
    tenantId: 'tenant-laboratory',
    customerId: 'customer-laboratory',
    deploymentId: 'deployment-laboratory',
    releaseId: 'release-laboratory',
    vectorSpace: 'document',
  },
}
const schedule = {
  id: 'chunk-schedule',
  content: 'Equipment maintenance is scheduled every Friday.',
  score: 0.74,
  metadata: { ...handbook.metadata, _aiDocumentSourceId: 'uploaded-schedule', _aiDocumentSourceName: 'maintenance.txt' },
}
const unreferenced = {
  id: 'chunk-unreferenced',
  content: 'This retrieved document was not cited.',
  metadata: { knowledgeSourceId: 'laboratory-library' },
}
const generic = {
  type: 'COMPOUND_HANDLED',
  answer: 'The handbook and maintenance schedule apply.',
  sources: [
    { sourceId: 'laboratory-library', documentId: schedule.id, vectorSpace: 'document', score: 0, metadata: { citationIndex: 1 } },
    { sourceId: 'laboratory-library', documentId: handbook.id, vectorSpace: 'document' },
  ],
  ragResponse: { entityType: 'document', documents: [handbook, schedule, unreferenced] },
}
const genericDocuments = documentsFor(generic)
assert.deepEqual(genericDocuments.map((doc) => doc.id), [schedule.id, handbook.id])
assert.deepEqual(genericDocuments.map((doc) => doc.title), ['maintenance.txt', 'sample-handbook.txt'])
assert.deepEqual(genericDocuments.map((doc) => doc.content), [schedule.content, handbook.content])
assert.equal(genericDocuments[0].score, 0, 'Citation score takes precedence, including zero')
assert.equal(genericDocuments[1].score, handbook.score)
assert.equal(genericDocuments[1].similarity, handbook.similarity)
assert.deepEqual(genericDocuments[1].metadata, handbook.metadata)
assert.equal(genericDocuments[0].metadata.citationIndex, 1)
assert.equal(genericDocuments[0].sourceId, 'laboratory-library')
assert.equal(genericDocuments[0].documentId, schedule.id)
assert.notEqual(genericDocuments[1].metadata, handbook.metadata)

const reference = { sourceId: 'laboratory-library', documentId: handbook.id, vectorSpace: 'document' }
for (const source of [
  { ...reference, sourceId: 'another-library' },
  { ...reference, knowledgeSourceId: 'another-library' },
  { ...reference, metadata: { knowledgeSourceId: 'another-library' } },
]) {
  const [document] = documentsFor({ sources: [source], ragResponse: { documents: [handbook, unreferenced] } })
  assert.equal(document.id, handbook.id)
  assert.equal(document.content, 'No content available', 'Conflicting knowledge sources must not join')
  assert.equal(document.metadata.tenantId, undefined)
}
const [conflictingDocument] = documentsFor({
  sources: [reference],
  ragResponse: { documents: [{ ...handbook, knowledgeSourceId: 'another-library' }] },
})
assert.equal(conflictingDocument.content, 'No content available')

for (const duplicate of [handbook, { ...handbook, content: 'Ambiguous duplicate content.' }, {
  ...handbook, metadata: { ...handbook.metadata, knowledgeSourceId: 'another-library' },
}]) {
  const [document] = documentsFor({ sources: [reference], ragResponse: { documents: [handbook, duplicate] } })
  assert.equal(document.content, 'No content available', 'Duplicate document IDs must not be joined')
  assert.equal(document.metadata.tenantId, undefined)
}

for (const source of [
  { ...reference, documentId: 'missing-chunk', title: 'sample-handbook.txt' },
  { ...reference, documentId: handbook.id.toUpperCase() },
  { ...reference, documentId: ` ${handbook.id} ` },
  { sourceId: handbook.id },
  { ...reference, documentId: handbook.metadata._aiDocumentSourceId },
]) {
  const documents = documentsFor({ sources: [source], ragResponse: { documents: [handbook, unreferenced] } })
  assert.equal(documents.length, 1)
  assert.equal(documents[0].content, 'No content available', 'Only exact document IDs may enrich a reference')
}
assert.equal(documentsFor({ sources: [{ documentId: handbook.id }], ragResponse: { documents: [handbook] } })[0].content, handbook.content)
assert.equal(documentsFor({ sources: [{ id: handbook.id, sourceId: 'laboratory-library' }], ragResponse: { documents: [handbook] } })[0].content, handbook.content)

const repeated = documentsFor({ sources: [reference, reference], ragResponse: { documents: [handbook] } })
assert.deepEqual(repeated.map((doc) => doc.id), [handbook.id, handbook.id], 'Do not deduplicate provider citations')

const richSource = {
  sourceId: 'rich-source',
  title: 'Museum access guide',
  contentText: 'The museum opens at ten.',
  type: 'document',
  score: 0.95,
  metadata: { tenantId: 'tenant-museum', originalFilename: 'visitor-guide.txt' },
}
const richDocuments = documentsFor({ sources: [richSource, handbook], ragResponse: { documents: [unreferenced] } })
assert.deepEqual(richDocuments.map((doc) => doc.id), ['rich-source', handbook.id])
assert.equal(richDocuments[0].title, richSource.title)
assert.equal(richDocuments[0].content, richSource.contentText)
assert.equal(richDocuments[0].score, richSource.score)
assert.deepEqual(richDocuments[0].metadata, richSource.metadata)
assert.equal(richDocuments[1].content, handbook.content)
assert.equal(documentsFor({ sources: [{ content: 'Generic content.', metadata: { originalFilename: 'research-notes.md' } }] })[0].title, 'research-notes.md')

const product = {
  id: 'item-notebook', title: 'Notebook', content: 'A ruled notebook.', type: 'product',
  imageUrl: 'https://example.test/notebook.png', url: 'https://example.test/notebook',
  product_variant_id: 'variant-notebook', priceRange: '12.00', metadata: { tenantId: 'tenant-stationery' },
}
const [normalizedProduct] = documentsFor({ sources: [product] })
for (const key of ['id', 'title', 'content', 'type', 'imageUrl', 'url', 'product_variant_id', 'priceRange']) {
  assert.equal(normalizedProduct[key], product[key])
}
assert.equal(product.metadata.imageUrl, undefined, 'Normalization must not write through to source metadata')

const emptySources = { sources: [], ragResponse: { entityType: 'research-note', documents: [schedule, handbook] } }
assert.deepEqual(documentsFor(emptySources).map((doc) => doc.id), [schedule.id, handbook.id])
for (const payload of [
  { ragResponse: { documents: [handbook] } },
  { sources: [], ragResponse: { documents: [] }, documents: [handbook] },
  { sources: [], result: { data: { documents: [handbook] } } },
  { sources: [], result: { data: { ragResponse: { documents: [handbook] } } } },
  { sources: [], actions: [{ documents: [handbook] }] },
]) assert.equal(documentsFor(payload)[0].content, handbook.content)
assert.deepEqual(documentsFor({ sources: [], ragResponse: { documents: [] } }), [])
assert.equal(documentsFor({ sources: [], ragResponse: { entityType: 'research-note', documents: [{ id: 'note', content: 'A note.' }] } })[0].type, 'research-note')

// Exercise the hook with explicit local mocks. No transport or provider module is loaded.
let runtimeResponse
const requests = []
const { useChatFlow } = loadSource('../src/hooks/useChatFlow.ts', {
  react: { useCallback: (callback) => callback },
  '@/api/chat': {
    createConversationId: () => 'fixture-conversation',
    postChatQuery: async (payload) => {
      requests.push(payload)
      return { data: runtimeResponse, status: 200, durationMs: 1 }
    },
    resolvedChatMessageUrl: () => '/fixture/chat',
    usesCanonicalConversationMessages: () => false,
  },
  '@/api/client': { isPublicRuntimeSessionInvalidatedError: () => false },
  '@/attachments': { toRuntimeAttachments: (attachments) => attachments },
  '@/config': config,
  '@/utils': {
    hasShopifyRequestContext: () => false,
    normalizeMessageContent: (content) => content,
    withRequestContext: (payload) => payload,
  },
  '@/shopifyMcpResults': { summarizeShopifyMcpCatalogResult: () => undefined },
  '@/chatResult': chatResult,
  '@/authenticationRequirement': {
    extractWorkspaceAuthenticationRequirement: () => undefined,
    dispatchWorkspaceAuthenticationRequirement: () => {},
  },
  '@/requestSemantics': loadSource('../src/requestSemantics.ts'),
})

async function assertHookDocuments(payload) {
  const expected = documentsFor(payload)
  runtimeResponse = payload
  let messages = []
  const noop = () => {}
  const { handleChatQuery } = useChatFlow({
    chatQuery: '', setChatQuery: noop,
    setChatMessages: (update) => { messages = typeof update === 'function' ? update(messages) : update },
    attachedItems: [], searchCategory: null,
    currentConversationId: 'fixture-conversation', setCurrentConversationId: noop,
    setIsLoading: noop, setSuggestions: noop, setCurrentPosition: noop, setCurrentMode: noop,
    setLastRequestData: noop, setLastResponseData: noop, setSelectedDebugMessage: noop,
    currentPosition: 'landing', currentMode: 'navigator',
  })
  const requestCount = requests.length
  await handleChatQuery('Explain the referenced material.')
  assert.equal(requests.length, requestCount + 1)
  assert.equal(messages.length, 2)
  const message = messages[1]
  assert.equal(message.type, 'ai')
  assert.deepEqual(message.result.sanitizedPayload.data.documents, expected)
  assert.deepEqual(message.documents.map(({ messageId, ...doc }) => {
    assert.equal(messageId, message.id)
    return doc
  }), expected, 'The hook and canonical result must use the same representation')
}

await assertHookDocuments(generic)
await assertHookDocuments({ ...emptySources, type: 'INFORMATION_PROVIDED' })
await assertHookDocuments({ type: 'INFORMATION_PROVIDED', sources: [richSource] })
await assertHookDocuments({ type: 'INFORMATION_PROVIDED', sources: [reference], ragResponse: { documents: [handbook, handbook] } })

// Optional local response fixture: node scripts/source-document-resolution-smoke.mjs /path/to/response.json
if (process.argv[2]) {
  const fixture = JSON.parse(readFileSync(process.argv[2], 'utf8')).body
  assert.ok(fixture.sources.length > 0)
  const documents = documentsFor(fixture)
  assert.equal(documents.length, fixture.sources.length)
  assert.deepEqual(documents.map((doc) => doc.id), fixture.sources.map((source) => source.documentId))
  assert.equal(new Set(documents.map((doc) => doc.id)).size, documents.length)
  for (const [index, reference] of fixture.sources.entries()) {
    const matches = fixture.ragResponse.documents.filter((doc) => doc.id === reference.documentId)
    assert.equal(matches.length, 1)
    const rich = matches[0]
    assert.equal(documents[index].content, rich.content.trim())
    assert.equal(documents[index].title, rich.metadata._aiDocumentSourceName || rich.metadata.originalFilename)
    assert.equal(documents[index].score, reference.score ?? rich.score)
    assert.equal(documents[index].similarity, reference.similarity ?? rich.similarity)
    assert.deepEqual(documents[index].metadata, rich.metadata)
  }
  await assertHookDocuments(fixture)
  console.log(`Deployed response fixture passed: ${documents.length} referenced documents with content, titles and ownership metadata.`)
}

console.log('Source document resolution smoke passed: exact IDs, order, metadata, mismatch/ambiguity guards, rich/empty sources, immutability and hook parity.')
