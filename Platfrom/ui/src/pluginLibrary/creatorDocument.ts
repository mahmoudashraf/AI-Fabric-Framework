import { exactNumber, isExactNumber, parseExactObject, stringifyExactJson } from '../lib/losslessJson'

export type DocumentPath = (string | number)[]
export type SourceKind = 'HTTP_JSON' | 'S3_COMPATIBLE_OBJECT_STORAGE' | 'SQL_QUERY'
export type ExactObject = Record<string, unknown>
export const sourceNames: Record<SourceKind, string> = {
  HTTP_JSON: 'HTTP JSON', S3_COMPATIBLE_OBJECT_STORAGE: 'Document storage', SQL_QUERY: 'SQL connection',
}
export function objectValue(value: unknown): value is ExactObject {
  return value !== null && typeof value === 'object' && !Array.isArray(value) && !isExactNumber(value)
}
export function at(value: unknown, path: DocumentPath): unknown {
  return path.reduce<unknown>((node, key) => {
    if ((!objectValue(node) && !Array.isArray(node)) || !Object.prototype.hasOwnProperty.call(node, key)) return undefined
    return (node as Record<string | number, unknown>)[key]
  }, value)
}
export function textAt(value: unknown, path: DocumentPath): string {
  const field = at(value, path)
  return typeof field === 'string' ? field : typeof field === 'number' || isExactNumber(field) ? String(field) : ''
}
export function parseDocument(source: string): { document: ExactObject; error: null } | { document: null; error: string } {
  try { return { document: parseExactObject(source, 'Plugin definition'), error: null } }
  catch (error) { return { document: null, error: error instanceof Error ? error.message : 'Invalid definition source.' } }
}

// Only explicitly edited paths change. Never rebuild a manifest from a form projection.
export function patchDocument(source: string, edits: { path: DocumentPath; value: unknown; remove?: boolean }[]): string {
  const document = parseExactObject(source, 'Plugin definition')
  for (const { path, value, remove } of edits) {
    if (!path.length) throw new Error('A field path is required.')
    let parent: unknown = document
    for (let index = 0; index < path.length - 1; index++) {
      const key = path[index]
      if (!objectValue(parent) && !Array.isArray(parent)) throw new Error(`Cannot edit ${path.join('.')}: an existing parent is not an object. Open Source to correct it.`)
      if (!Object.prototype.hasOwnProperty.call(parent, key)) Object.defineProperty(parent, key, {
        value: typeof path[index + 1] === 'number' ? [] : {}, enumerable: true, writable: true, configurable: true,
      })
      parent = (parent as Record<string | number, unknown>)[key]
    }
    if (!objectValue(parent) && !Array.isArray(parent)) throw new Error(`Cannot edit ${path.join('.')}: its parent is not an object. Open Source to correct it.`)
    const key = path[path.length - 1]
    if (remove) {
      if (Array.isArray(parent) && typeof key === 'number') parent.splice(key, 1)
      else delete (parent as ExactObject)[String(key)]
    } else Object.defineProperty(parent, key, { value, enumerable: true, writable: true, configurable: true })
  }
  return stringifyExactJson(document, 2)
}
export function numericValue(value: string): unknown { return value.trim() === '' ? null : exactNumber(value) }
export function datasetPath(index: number): DocumentPath { return ['contributions', 'datasets', index] }
export function sourceKind(document: unknown, index = 0): string {
  const path = datasetPath(index)
  return textAt(document, [...path, 'sourceConnector', 'connectorType']) || textAt(document, [...path, 'syncConnector', 'connectorType'])
}
export function datasetList(document: unknown): unknown[] {
  const datasets = at(document, ['contributions', 'datasets'])
  return Array.isArray(datasets) ? datasets : []
}
export function knowledgeIndex(document: unknown, datasetIndex: number): number {
  const sources = at(document, ['contributions', 'knowledgeSources'])
  const id = textAt(document, [...datasetPath(datasetIndex), 'datasetId'])
  return Array.isArray(sources) ? sources.findIndex(source => textAt(source, ['datasetRef']) === id) : -1
}
export function audience(document: unknown, index: number): string {
  const modes = at(document, ['contributions', 'knowledgeSources', index, 'authModes'])
  if (!Array.isArray(modes)) return 'custom'
  const publicModes = ['PUBLIC_RUNTIME_ANONYMOUS', 'PUBLIC_RUNTIME_AUTHENTICATED', 'PRIVATE_RUNTIME_BACKEND_MEDIATED']
  const signedModes = publicModes.slice(1)
  const staffModes = ['PRIVATE_RUNTIME_BACKEND_MEDIATED']
  const matches = (values: string[]) => values.length === modes.length && values.every(value => modes.includes(value))
  return matches(publicModes) ? 'public' : matches(signedModes) ? 'signed' : matches(staffModes) ? 'staff' : 'custom'
}
export function audienceModes(value: string): string[] {
  return value === 'public' ? ['PRIVATE_RUNTIME_BACKEND_MEDIATED', 'PUBLIC_RUNTIME_AUTHENTICATED', 'PUBLIC_RUNTIME_ANONYMOUS']
    : value === 'signed' ? ['PRIVATE_RUNTIME_BACKEND_MEDIATED', 'PUBLIC_RUNTIME_AUTHENTICATED'] : ['PRIVATE_RUNTIME_BACKEND_MEDIATED']
}

function sourceContract(kind: SourceKind, identity = 'source'): ExactObject {
  if (kind === 'S3_COMPATIBLE_OBJECT_STORAGE') return {
    ingestionMode: 'EXTERNAL_DOCUMENT_STORAGE', updateStrategy: 'VERSIONED_REPLACE', storageScope: 'CUSTOMER_SCOPED', sharingScope: 'DEPLOYMENT_ONLY',
    sourceConnector: { connectorType: kind, storageOwnership: 'BOUND_CONNECTION', bindingRefField: 'documentStorageBindingRef', allowedOperations: ['LIST', 'READ', 'HEAD'], deleteSourceOnRemoval: false },
    documentPolicy: {
      allowedMediaTypes: ['text/plain', 'application/json'], allowedExtensions: ['.txt', '.json'], jsonContentKeys: ['content', 'text', 'body'],
      maxSourceBytes: 1048576, maxSources: 100, maxTotalIndexedBytes: 104857600, maxChunksPerSource: 100, maxChunkCharacters: 8000,
      maxTotalCharacters: 250000, previewMaxChunks: 10, previewMaxCharactersPerChunk: 500, evidenceRetentionDays: 30, commandRetentionDays: 30,
      retentionBatchSize: 100, allowedMetadataKeys: ['originalFilename', 'locale', 'sourceCategory', 'publicationStatus'],
      initialIndexRequiresConfirmation: true, trustedAutoIndexingAllowed: false,
    },
  }
  if (kind === 'SQL_QUERY') return {
    ingestionMode: 'EXTERNAL_SYNC_SQL', updateStrategy: 'UPSERT_BY_ID', storageScope: 'CUSTOMER_MANAGED', sharingScope: 'DEPLOYMENT_ONLY',
    syncConnector: { connectorType: kind, connectionRefField: 'connectionRef', query: '' },
  }
  return {
    ingestionMode: 'EXTERNAL_SYNC_HTTP', updateStrategy: 'UPSERT_BY_ID', storageScope: 'CUSTOMER_MANAGED', sharingScope: 'DEPLOYMENT_ONLY',
    syncConnector: {
      connectorType: kind,
      connectionProfile: { profileId: `${identity}-provider`, environment: 'bound', managedBaseUrlField: 'providerBaseUrl', managedAllowedHostField: 'providerHost',
        auth: { strategy: 'API_KEY', apiKeyHeader: 'X-API-Key', apiKeySecretRefField: 'providerKey' }, capabilityGrants: ['records:read'] },
      protectedResource: { bindingId: `${identity}-account`, environment: 'bound', resourceType: 'account', resourceIdField: 'accountId', capabilityGrants: ['records:read'] },
      httpSource: { sourceId: identity, path: '', method: 'GET', trustedResourcePlacements: [{ target: 'QUERY', field: 'accountId' }], requiredCapabilityGrants: ['records:read'],
        pagination: { strategy: 'NONE', maxPages: 100 }, mapping: { recordsJsonPointer: '', idJsonPointer: '', resourceJsonPointer: '', contentFields: {}, entityFields: {}, metadataFields: {} },
        tombstonePolicy: { strategy: 'NONE' }, scheduleSeconds: 900 },
    },
  }
}
function installFields(kind: SourceKind): ExactObject[] {
  if (kind === 'S3_COMPATIBLE_OBJECT_STORAGE') return [{ id: 'documentStorageBindingRef', label: 'Document storage connection', type: 'text', required: true, description: 'Choose an existing hosted or BYO S3-compatible connection for this environment. The plugin reads its approved folder; no access key belongs in this field.' }]
  if (kind === 'SQL_QUERY') return [{ id: 'connectionRef', label: 'SQL connection reference', type: 'text', required: true }]
  return [
    { id: 'providerBaseUrl', label: 'Provider base URL', type: 'url', required: true, description: 'Your API base URL, for example https://api.example.com. The relative read route is defined by this plugin.' },
    { id: 'providerHost', label: 'Allowed provider host', type: 'text', required: true, description: 'Approved hostname matching the API URL, for example api.example.com. Do not include https:// or a path.' },
    { id: 'providerKey', label: 'Provider credential reference', type: 'secretRef', required: true, description: 'Reference to the API credential already stored in Platform. Never paste the API key itself.' },
    { id: 'accountId', label: 'Authorized account', type: 'text', required: true, description: 'The source account ID you are authorized to read, for example account-7. The connector restricts requests and returned records to this account.' },
  ]
}
export function newDefinition(identifier: string, kind: SourceKind = 'HTTP_JSON'): string {
  const entity = kind === 'S3_COMPATIBLE_OBJECT_STORAGE' ? 'document' : 'record'
  const datasetId = `${identifier}-records`
  return stringifyExactJson({
    schemaVersion: 1, pluginId: identifier, version: '1.0.0', pluginType: 'DATA', displayName: '', description: '',
    compatibility: { requiredCapabilities: ['knowledgeSources'] }, pricing: { pricingModel: 'FREE' },
    permissions: { contributesKnowledgeSources: true, requiresSharedDatasetAccess: kind === 'S3_COMPATIBLE_OBJECT_STORAGE' },
    presentation: { catalogueFamily: kind === 'S3_COMPATIBLE_OBJECT_STORAGE' ? 'DOCUMENT_STORAGE' : kind, catalogueAudience: 'CUSTOMER', adapterContract: kind },
    installForm: installFields(kind),
    contributions: {
      entityConfig: { 'ai-entities': { [entity]: { indexing: { enabled: true, 'max-characters': 8000 },
        'searchable-fields': [{ name: 'content', destinations: ['SEMANTIC_SEARCH', 'RAG_CONTEXT'], required: true, priority: 100 }],
        'metadata-fields': ['tenantId', 'customerId', 'deploymentId', 'datasetId', 'knowledgeSourceHandleRef'].map(name => ({ name, 'data-type': 'ID', destinations: ['VECTOR_METADATA'], required: true })),
      } } },
      datasets: [{ datasetId, entityType: entity, ...sourceContract(kind, datasetId) }],
      knowledgeSources: [{ sourceKey: datasetId, sourceType: 'deployment-private-vector', adapterType: 'deployment-private-vector', datasetRef: datasetId, entityType: entity,
        attributionLabel: '', authModes: audienceModes('staff') }],
    },
  }, 2)
}
export function replaceSourceContract(source: string, index: number, kind: SourceKind): string {
  const document = parseExactObject(source)
  const current = at(document, datasetPath(index))
  if (!objectValue(current)) throw new Error('The selected dataset must be an object. Correct it in Source.')
  const replacement = { ...current }
  for (const key of ['syncConnector', 'sourceConnector', 'documentPolicy']) delete replacement[key]
  Object.assign(replacement, sourceContract(kind, textAt(document, [...datasetPath(index), 'datasetId']) || 'source'))
  const oldKind = sourceKind(document, index) as SourceKind
  const oldDefaults = Object.prototype.hasOwnProperty.call(sourceNames, oldKind) ? installFields(oldKind) : []
  const existing = at(document, ['installForm'])
  if (!Array.isArray(existing)) throw new Error('Setup inputs are not an array. Correct them in Source before changing contracts.')
  const oldIds = new Set(oldDefaults.map(field => field.id))
  const newFields = installFields(kind)
  const kept = existing.filter(field => !oldIds.has(textAt(field, ['id'])))
  const keptIds = new Set(kept.map(field => textAt(field, ['id'])))
  const updatedFields = [...kept, ...newFields.filter(field => !keptIds.has(String(field.id)))]
  const edits: { path: DocumentPath; value: unknown }[] = [
    { path: datasetPath(index), value: replacement }, { path: ['installForm'], value: updatedFields },
    { path: ['permissions', 'requiresSharedDatasetAccess'], value: kind === 'S3_COMPATIBLE_OBJECT_STORAGE' },
    { path: ['presentation', 'catalogueFamily'], value: kind === 'S3_COMPATIBLE_OBJECT_STORAGE' ? 'DOCUMENT_STORAGE' : kind },
    { path: ['presentation', 'adapterContract'], value: kind },
  ]
  if (kind === 'S3_COMPATIBLE_OBJECT_STORAGE') {
    const entityPath: DocumentPath = ['contributions', 'entityConfig', 'ai-entities']
    const documentEntity = at(parseExactObject(newDefinition('seed', kind)), [...entityPath, 'document'])
    if (at(document, [...entityPath, 'document']) === undefined) edits.push({ path: [...entityPath, 'document'], value: documentEntity })
    edits.push({ path: [...datasetPath(index), 'entityType'], value: 'document' })
    const sourceIndex = knowledgeIndex(document, index)
    if (sourceIndex >= 0) edits.push({ path: ['contributions', 'knowledgeSources', sourceIndex, 'entityType'], value: 'document' })
  }
  return patchDocument(source, edits)
}
export function safeBrainReturn(search: string): string | null {
  const raw = new URLSearchParams(search).get('returnTo')
  if (!raw || raw.length > 4096 || /[\r\n\\]/.test(raw) || !raw.startsWith('/smart-brains/')) return null
  try {
    const url = new URL(raw, 'https://console.invalid')
    return url.origin === 'https://console.invalid' && /^\/smart-brains\/[^/]+(?:\/|$)/.test(url.pathname) ? `${url.pathname}${url.search}${url.hash}` : null
  } catch { return null }
}
export function libraryPath(path: string, search: string): string {
  const params = new URLSearchParams(search)
  const next = new URLSearchParams()
  const back = safeBrainReturn(search)
  if (back) next.set('returnTo', back)
  if (params.get('customerId')) next.set('customerId', params.get('customerId')!)
  return `${path}${next.size ? `?${next}` : ''}`
}
export function installationPath(returnTo: string, pluginId: string, version: string): string {
  const url = new URL(returnTo, 'https://console.invalid')
  const match = url.pathname.match(/^\/smart-brains\/([^/]+)(?:\/|$)/)
  if (url.origin !== 'https://console.invalid' || !match) throw new Error('Choose a Smart Brain before configuring this plugin.')
  url.pathname = `/smart-brains/${match[1]}/capabilities/add`
  url.searchParams.set('pluginId', pluginId)
  url.searchParams.set('pluginVersion', version)
  url.searchParams.set('view', 'setup')
  url.searchParams.set('type', 'DATA')
  return `${url.pathname}${url.search}`
}
