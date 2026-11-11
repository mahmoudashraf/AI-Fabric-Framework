import { platformRequest } from '../api/platformApi'
import { parseExactJson, stringifyExactJson } from '../lib/losslessJson'
import type { AssistantSession } from '../assistant/types'
import type { McpAuthorization, McpConnection, McpConnectionInput, McpConnectionList, McpDiscovery, McpImportInput, McpImportReceipt, McpUndoReceipt } from './types'

const root = (brainId: string) => `/api/deployments/${encodeURIComponent(brainId)}/mcp-connections`
const path = (brainId: string, targetProfileId: string, suffix = '') => `${root(brainId)}${suffix}?${new URLSearchParams({ targetProfileId })}`
const request = <T>(url: string, init?: RequestInit) => platformRequest<T>(url, init, 'default', parseExactJson)
const post = <T>(url: string, body: unknown = {}) => request<T>(url, { method: 'POST', body: stringifyExactJson(body) })
const connectionPath = (id: string) => `/${encodeURIComponent(id)}`

export const mcpApi = {
  list: (brainId: string, target?: string) => request<McpConnectionList>(target ? path(brainId, target) : root(brainId)),
  create: (brainId: string, target: string, input: McpConnectionInput) => post<McpConnection>(path(brainId, target), { ...input, targetProfileId: target }),
  update: (brainId: string, target: string, id: string, revision: number, input: McpConnectionInput, sourceTargetProfileId = target) => request<McpConnection>(path(brainId, target, connectionPath(id)), {
    method: 'PATCH', body: stringifyExactJson({ ...input, expectedRevision: revision, targetProfileId: sourceTargetProfileId }),
  }),
  bindTarget: (brainId: string, target: string, id: string, revision: number, enabled: boolean) => post<McpConnection>(path(brainId, target, `${connectionPath(id)}/target-bindings`), { expectedRevision: revision, targetProfileId: target, enabled }),
  discover: (brainId: string, target: string, id: string) => platformRequest<McpDiscovery>(path(brainId, target, `${connectionPath(id)}/discover`), {
    method: 'POST', body: stringifyExactJson({}),
  }, 'default', parseExactJson, { timeoutMs: 75_000 }),
  snapshot: (brainId: string, target: string, id: string, snapshotId: string) => request<McpDiscovery>(path(brainId, target, `${connectionPath(id)}/snapshots/${encodeURIComponent(snapshotId)}`)),
  import: (brainId: string, target: string, id: string, input: McpImportInput) => post<McpImportReceipt>(path(brainId, target, `${connectionPath(id)}/import`), input),
  undoImport: (brainId: string, target: string, id: string, receiptId: string) => post<McpUndoReceipt>(path(brainId, target, `${connectionPath(id)}/imports/${encodeURIComponent(receiptId)}/undo`)),
  authorize: (brainId: string, target: string, id: string, revision: number, returnPath: string) => platformRequest<McpAuthorization>(path(brainId, target, `${connectionPath(id)}/authorize`), {
    method: 'POST', body: stringifyExactJson({ expectedRevision: revision, returnPath }),
  }, 'browser-bound', parseExactJson),
  disconnect: (brainId: string, target: string, id: string) => post<McpConnection>(path(brainId, target, `${connectionPath(id)}/disconnect`)),
  assistantSource: (sessionId: string, connectionId: string, snapshotId: string, toolNames: string[]) => post<AssistantSession>(`/api/configuration-assistant/sessions/${encodeURIComponent(sessionId)}/sources/mcp`, { connectionId, snapshotId, toolNames }),
}
