import { platformRequest, type DocumentStorageBindingSummary } from './platformApi'

export type HostedDocumentStorageConnection = {
  id: string
  name: string
  mode: 'LOOMAI_HOSTED_S3'
  bucket: string
  region: string
  rootPrefix: string
  status: string
  lastError: string | null
  createdAt: string
  updatedAt: string
}

export type HostedDocumentUpload = {
  id: string
  connectionId: string
  filename: string
  objectKey: string
  prefix: string
  sizeBytes: number
  contentType: string
  sha256: string
  status: string
  lastError: string | null
  createdAt: string
}

export type HostedDocumentStorageCatalogue = {
  hostedAvailable: boolean
  maxUploadBytes: number
  supportedExtensions: string[]
  connections: HostedDocumentStorageConnection[]
}

export type PrepareDocumentSelectionRequest = {
  targetProfileId: string
  releaseId: string
  uploadIds: string[]
  datasetId: string
  publicApproved: boolean
  operationKey: string
}

export type DocumentStorageSelectionItem = { uploadId: string; sourceId: string | null; status: string; workId?: string | null; message?: string | null }

export type PreparedDocumentSelection = {
  items: DocumentStorageSelectionItem[]
  complete: boolean
}

export const documentStorageKeys = {
  connections: (scope: string, deploymentId: string) => ['hosted-document-storage', scope, deploymentId, 'connections'] as const,
  uploads: (scope: string, deploymentId: string, connectionId: string) => ['hosted-document-storage', scope, deploymentId, 'uploads', connectionId] as const,
  selections: (scope: string, deploymentId: string, connectionId: string, targetProfileId: string, releaseId: string) =>
    ['hosted-document-storage', scope, deploymentId, 'selections', connectionId, targetProfileId, releaseId] as const,
}

const collection = (deploymentId: string) => `/api/deployments/${encodeURIComponent(deploymentId)}/document-storage/connections`
const connectionPath = (deploymentId: string, connectionId: string) => `${collection(deploymentId)}/${encodeURIComponent(connectionId)}`

export const fetchHostedDocumentStorage = (deploymentId: string, signal?: AbortSignal) =>
  platformRequest<HostedDocumentStorageCatalogue>(collection(deploymentId), { signal })

export const createHostedDocumentStorage = (deploymentId: string, body: { name: string; operationKey: string }, signal?: AbortSignal) =>
  platformRequest<HostedDocumentStorageConnection>(collection(deploymentId), { method: 'POST', body: JSON.stringify(body), signal })

export const prepareHostedDocumentStorage = (deploymentId: string, connectionId: string, body: { operationKey: string }, signal?: AbortSignal) =>
  platformRequest<HostedDocumentStorageConnection>(`${connectionPath(deploymentId, connectionId)}/prepare`, { method: 'POST', body: JSON.stringify(body), signal })

export const fetchHostedDocumentUploads = (deploymentId: string, connectionId: string, signal?: AbortSignal) =>
  platformRequest<HostedDocumentUpload[]>(`${connectionPath(deploymentId, connectionId)}/uploads`, { signal })

export const fetchHostedDocumentSelections = (deploymentId: string, connectionId: string, targetProfileId: string, releaseId: string, signal?: AbortSignal) =>
  platformRequest<DocumentStorageSelectionItem[]>(`${connectionPath(deploymentId, connectionId)}/selections?${new URLSearchParams({ targetProfileId, releaseId })}`, { signal })

export const uploadHostedDocument = (deploymentId: string, connectionId: string,
  input: { file: File; prefix: string; operationKey: string }, signal?: AbortSignal) =>
  platformRequest<HostedDocumentUpload>(`${connectionPath(deploymentId, connectionId)}/uploads?${new URLSearchParams({
    filename: input.file.name, prefix: input.prefix, operationKey: input.operationKey,
  })}`, { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: input.file, signal })

export const bindHostedDocumentStorage = (deploymentId: string, connectionId: string,
  body: { targetProfileId: string; prefix: string; operationKey: string }, signal?: AbortSignal) =>
  platformRequest<{ connection: HostedDocumentStorageConnection; binding: DocumentStorageBindingSummary }>(
    `${connectionPath(deploymentId, connectionId)}/bind`, { method: 'POST', body: JSON.stringify(body), signal })

export const prepareHostedDocumentSelection = (deploymentId: string, connectionId: string, body: PrepareDocumentSelectionRequest, signal?: AbortSignal) =>
  platformRequest<PreparedDocumentSelection>(`${connectionPath(deploymentId, connectionId)}/prepare-selection`, {
    method: 'POST', body: JSON.stringify(body), signal,
  })
