import { at, datasetList, datasetPath, objectValue, sourceKind, textAt, type DocumentPath, type ExactObject } from './creatorDocument'

export type CreatorRequirement = { path: DocumentPath; label: string; step: number; dataset?: number }
export function pointerPath(path: DocumentPath): string {
  return '/' + path.map(key => String(key).replace(/~/g, '~0').replace(/\//g, '~1')).join('/')
}

// Presentation of existing manifest requirements, not a replacement for server validation.
export function creatorRequirements(document: ExactObject): CreatorRequirement[] {
  const result: CreatorRequirement[] = []
  const add = (path: DocumentPath, label: string, step: number, dataset?: number) => result.push({ path, label, step, dataset })
  add(['displayName'], 'Display name', 0)
  add(['pluginId'], 'Plugin identifier', 0)
  add(['version'], 'Reusable version', 5)
  datasetList(document).forEach((_, index) => {
    const d = datasetPath(index), c = [...d, 'syncConnector'], p = [...c, 'connectionProfile'], h = [...c, 'httpSource'], a = [...p, 'auth']
    const field = (path: DocumentPath, label: string, step: number) => add(path, label, step, index)
    const rows = (path: DocumentPath, label: string, keys: string[], step: number, requireCollection = false) => {
      if (requireCollection) field(path, label, step)
      const value = at(document, path)
      if (Array.isArray(value)) value.forEach((_, row) => keys.forEach(key => field([...path, row, key], `${label} ${row + 1}: ${key}`, step)))
    }
    field([...d, 'datasetId'], 'Dataset identifier', 0)
    field([...d, 'entityType'], 'Record type', 0)
    if (sourceKind(document, index) === 'HTTP_JSON') {
      if (!textAt(document, [...p, 'baseUrl'])) field([...p, 'managedBaseUrlField'], 'Provider base URL input', 1)
      if (!Array.isArray(at(document, [...p, 'allowedHosts']))) field([...p, 'managedAllowedHostField'], 'Allowed host input', 1)
      field([...a, 'strategy'], 'Authentication', 1)
      if (textAt(document, [...a, 'strategy']) === 'API_KEY') {
        field([...a, 'apiKeyHeader'], 'API key header', 1)
        field([...a, 'apiKeySecretRefField'], 'Credential reference input', 1)
      } else if (textAt(document, [...a, 'strategy']) === 'FORM_TOKEN_EXCHANGE') {
        field([...a, 'tokenPath'], 'Token route', 1)
        field([...a, 'tokenJsonPointer'], 'Token JSON pointer', 1)
        if (!textAt(document, [...a, 'absoluteExpiryJsonPointer'])) field([...a, 'relativeExpiryJsonPointer'], 'Relative expiry JSON pointer', 1)
        rows([...a, 'credentialFields'], 'Token credentials', ['name', 'secretRefField'], 1, true)
      }
      field([...h, 'path'], 'Relative read route', 1)
      field([...c, 'protectedResource', 'resourceIdField'], 'Authorized account input', 1)
      rows([...h, 'trustedResourcePlacements'], 'Authorized account placement', ['target', 'field'], 1, true)
      const placements = at(document, [...h, 'trustedResourcePlacements'])
      if (Array.isArray(placements)) placements.forEach((row, position) => {
        if (textAt(row, ['target']) === 'BODY') field([...h, 'trustedResourcePlacements', position, 'jsonPointer'], 'Account body JSON pointer', 1)
      })
      if (textAt(document, [...h, 'pagination', 'strategy']) === 'CURSOR') field([...h, 'pagination', 'nextCursorJsonPointer'], 'Next cursor JSON pointer', 1)
      field([...h, 'mapping', 'idJsonPointer'], 'Stable record ID JSON pointer', 2)
      field([...h, 'mapping', 'resourceJsonPointer'], 'Account ownership JSON pointer', 2)
      for (const [key, label] of [['contentFields', 'Searchable content'], ['entityFields', 'Record fields'], ['metadataFields', 'Metadata, citations and media']]) {
        const path = [...h, 'mapping', key]
        if (key === 'contentFields') field(path, label, 2)
        const mapping = at(document, path)
        if (objectValue(mapping)) Object.keys(mapping).forEach(name => field([...path, name], `${label}: ${name}`, 2))
      }
      if (['FIELD_VALUE', 'ABSENT_OR_FIELD_VALUE'].includes(textAt(document, [...h, 'tombstonePolicy', 'strategy']))) {
        field([...h, 'tombstonePolicy', 'operationJsonPointer'], 'Deletion field JSON pointer', 3)
        field([...h, 'tombstonePolicy', 'deleteValues'], 'Deletion values', 3)
      }
    } else if (sourceKind(document, index) === 'S3_COMPATIBLE_OBJECT_STORAGE') {
      field([...d, 'sourceConnector', 'bindingRefField'], 'Document storage connection input', 1)
      for (const [key, label, step] of [
        ['allowedMediaTypes', 'Allowed media types', 1], ['allowedExtensions', 'Allowed file extensions', 1], ['maxSourceBytes', 'Maximum file bytes', 1],
        ['jsonContentKeys', 'JSON content fields', 2], ['maxChunkCharacters', 'Maximum characters per chunk', 2],
        ['initialIndexRequiresConfirmation', 'Require approval before initial indexing', 3], ['trustedAutoIndexingAllowed', 'Allow trusted automatic indexing', 3],
        ['evidenceRetentionDays', 'Evidence retention (days)', 3], ['commandRetentionDays', 'Command retention (days)', 3],
      ] as const) field([...d, 'documentPolicy', key], label, step)
    }
  })
  const inputs = at(document, ['installForm'])
  if (Array.isArray(inputs)) inputs.forEach((row, index) => {
    for (const [key, label] of [['id', 'Input identifier'], ['type', 'Type']]) add(['installForm', index, key], `${label} (${index + 1})`, 4)
    if (textAt(row, ['type']) === 'select') add(['installForm', index, 'options'], `Options (${index + 1})`, 4)
  })
  return result
}
export function missingCreatorRequirements(document: ExactObject): CreatorRequirement[] {
  return creatorRequirements(document).filter(({ path }) => {
    const value = at(document, path)
    return value === undefined || value === null || typeof value === 'string' && !value.trim()
      || Array.isArray(value) && value.length === 0 || objectValue(value) && Object.keys(value).length === 0
  })
}
export function requirementAt(document: ExactObject, path: DocumentPath): boolean {
  return creatorRequirements(document).some(required => pointerPath(required.path) === pointerPath(path))
}
