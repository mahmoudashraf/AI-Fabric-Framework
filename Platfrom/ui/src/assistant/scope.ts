import type { AssistantScope, AssistantTask } from './types'

// Formal route identities only. The user's prose never selects a resource or task.
const tasks: Record<string, AssistantTask> = {
  '': 'KIT', capabilities: 'ACTIONS', 'capabilities/actions': 'ACTIONS',
  'knowledge/connections': 'CONNECTIONS', knowledge: 'KNOWLEDGE',
  'knowledge/documents': 'KNOWLEDGE', 'knowledge/live-data': 'KNOWLEDGE',
  'knowledge/search': 'MODELS', 'knowledge/storage': 'KNOWLEDGE',
  'knowledge/discover': 'KNOWLEDGE', 'knowledge/proof': 'KNOWLEDGE', 'knowledge/retention': 'KNOWLEDGE',
  responses: 'BEHAVIOUR', 'responses/instructions': 'BEHAVIOUR', 'responses/advanced': 'BEHAVIOUR',
  models: 'MODELS', 'human-support': 'HUMAN_SUPPORT', 'action-reviews': 'HUMAN_SUPPORT',
  'human-support/specialists': 'SPECIALISTS', 'advanced/providers': 'MODELS',
  'advanced/configuration': 'KIT',
}

export function brainAssistantScope(pathname: string, search: string, brainId: string): AssistantScope | null {
  const root = `/smart-brains/${encodeURIComponent(brainId)}`
  if (!brainId || (pathname !== root && !pathname.startsWith(`${root}/`))) return null
  const path = pathname.slice(root.length).replace(/^\//, '').replace(/\/$/, '')
  const params = new URLSearchParams(search)
  const parts = path.split('/')
  let task = tasks[path]
  if (path === 'capabilities' && params.get('view') === 'connection') task = 'CONNECTIONS'
  if (parts.length === 3 && parts[2] && parts[0] === 'knowledge' && parts[1] === 'connections') task = 'CONNECTIONS'
  if (parts.length === 3 && parts[2] && parts[0] === 'knowledge' && parts[1] === 'sources') task = 'KNOWLEDGE'
  if (parts.length === 2 && parts[0] === 'capabilities' && !task) {
    let id = ''
    try { id = decodeURIComponent(parts[1]) } catch { return null }
    if (id.startsWith('action:') || id.startsWith('install:')) task = params.get('view') === 'connection' ? 'CONNECTIONS' : 'ACTIONS'
  }
  return task ? { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: brainId, task } : null
}

export function scopeIdentity(scope: AssistantScope) { return `${scope.resourceKind}:${scope.resourceId}:${scope.task}` }

export function operation(value: unknown): { id: string; label: string; pointer: string } | null {
  if (!value || typeof value !== 'object') return null
  const row = value as Record<string, unknown>
  if (typeof row.id !== 'string' || !row.id) return null
  return { id: row.id, label: [row.method, row.path, row.summary || row.id].filter(value => typeof value === 'string' && value).join(' '), pointer: typeof row.sourcePointer === 'string' ? row.sourcePointer : '' }
}
