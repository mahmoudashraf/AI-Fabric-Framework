import { platformRequest } from '../api/platformApi'
import { parseExactJson, stringifyExactJson } from '../lib/losslessJson'
import type { AssistantScope, AssistantSession, AssistantStatus, ReusableSource, SourceInput, TurnInput } from './types'

const root = '/api/configuration-assistant'
const sessionPath = (id: string) => `${root}/sessions/${encodeURIComponent(id)}`
const request = <T>(path: string, init?: RequestInit) => platformRequest<T>(path, init, 'default', parseExactJson)
const post = <T>(path: string, body: unknown = {}, key?: string) => request<T>(path, {
  method: 'POST', body: stringifyExactJson(body), ...(key ? { headers: { 'Idempotency-Key': key } } : {}),
})

export const assistantApi = {
  status: () => request<AssistantStatus>(`${root}/status`),
  create: ({ resourceKind, resourceId, task }: AssistantScope) => post<AssistantSession>(`${root}/sessions`, { resourceKind, resourceId, task }),
  get: (id: string) => request<AssistantSession>(sessionPath(id)),
  source: (id: string, input: SourceInput) => post<AssistantSession>(`${sessionPath(id)}/sources`, input),
  removeSource: (id: string, sourceId: string) => request<AssistantSession>(`${sessionPath(id)}/sources/${encodeURIComponent(sourceId)}`, { method: 'DELETE' }),
  sourceContent: (id: string, sourceId: string) => request<{ id: string; title: string; content: string }>(`${sessionPath(id)}/sources/${encodeURIComponent(sourceId)}/content`),
  reusableSources: (id: string) => request<ReusableSource[]>(`${sessionPath(id)}/reusable-sources`),
  reuseSource: (id: string, sessionId: string, sourceIds: string[]) => post<AssistantSession>(`${sessionPath(id)}/sources/reuse`, { sessionId, sourceIds }),
  turn: (id: string, input: TurnInput, key: string) => post<AssistantSession>(`${sessionPath(id)}/turns`, input, key),
  cancel: (id: string, turnId?: string | null) => post<AssistantSession>(`${sessionPath(id)}/cancel`, { turnId: turnId ?? undefined }),
  proposal: (id: string, turnId: string, command: 'check' | 'apply' | 'undo') => post<AssistantSession>(`${sessionPath(id)}/proposals/${encodeURIComponent(turnId)}/${command}`),
  delete: (id: string) => request<void>(sessionPath(id), { method: 'DELETE' }),
}

export async function fileSource(file: File): Promise<SourceInput> {
  // Bound browser allocations to the server's existing 2 MiB upload contract.
  if (file.size > 2 * 1024 * 1024) throw new Error('Documentation files must be 2 MiB or smaller.')
  const mediaType = /\.ya?ml$/i.test(file.name) ? 'application/yaml' : /\.json$/i.test(file.name) ? 'application/json' : /\.pdf$/i.test(file.name) ? 'application/pdf' : /\.md$/i.test(file.name) ? 'text/markdown' : /\.txt$/i.test(file.name) ? 'text/plain' : file.type || 'application/octet-stream'
  if (mediaType === 'application/pdf' || mediaType === 'application/octet-stream') {
    const bytes = new Uint8Array(await file.arrayBuffer())
    let binary = ''
    for (let offset = 0; offset < bytes.length; offset += 8192) binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192))
    return { title: file.name, mediaType, base64: btoa(binary) }
  }
  return { title: file.name, mediaType, content: await file.text() }
}
