import type { AssistantScope, AssistantSection, AssistantTask } from './types'

// Formal route identities only. The user's prose never selects a resource or task.
const sections: Record<string, AssistantSection> = {
  '': 'KIT', capabilities: 'ACTIONS', 'capabilities/actions': 'ACTIONS',
  'knowledge/connections': 'LIVE_DATA', knowledge: 'KNOWLEDGE',
  'knowledge/documents': 'DOCUMENTS', 'knowledge/live-data': 'LIVE_DATA',
  'knowledge/search': 'SEARCH', 'knowledge/storage': 'STORAGE',
  'knowledge/discover': 'DOCUMENTS', 'knowledge/proof': 'DOCUMENTS', 'knowledge/retention': 'DOCUMENTS',
  responses: 'BEHAVIOUR', 'responses/instructions': 'INSTRUCTIONS', 'responses/advanced': 'INSTRUCTIONS',
  models: 'MODELS', 'human-support': 'HUMAN_SUPPORT', 'action-reviews': 'ACTION_REVIEWS',
  'human-support/specialists': 'SPECIALISTS', 'advanced/providers': 'MODELS',
  'advanced/configuration': 'KIT',
}

const sectionTasks: Record<AssistantSection, AssistantTask> = {
  ACTIONS: 'ACTIONS', CONNECTIONS: 'CONNECTIONS', KNOWLEDGE: 'KNOWLEDGE', BEHAVIOUR: 'BEHAVIOUR',
  MODELS: 'MODELS', HUMAN_SUPPORT: 'HUMAN_SUPPORT', SPECIALISTS: 'SPECIALISTS', KIT: 'KIT', DATA_PLUGIN: 'DATA_PLUGIN', ENGAGE: 'ENGAGE',
  DOCUMENTS: 'KNOWLEDGE', LIVE_DATA: 'KNOWLEDGE', STORAGE: 'KNOWLEDGE', SEARCH: 'MODELS',
  INSTRUCTIONS: 'BEHAVIOUR', ACTION_REVIEWS: 'HUMAN_SUPPORT',
}

export function brainAssistantScope(pathname: string, search: string, brainId: string): AssistantScope | null {
  const root = `/smart-brains/${encodeURIComponent(brainId)}`
  if (!brainId || (pathname !== root && !pathname.startsWith(`${root}/`))) return null
  const path = pathname.slice(root.length).replace(/^\//, '').replace(/\/$/, '')
  const params = new URLSearchParams(search)
  const parts = path.split('/')
  // MCP's explicit Help configure attaches the selected owned snapshot to the same assistant.
  if (parts[0] === 'capabilities' && (params.get('view') === 'mcp' || params.has('mcp'))) return null
  let section = sections[path]
  let selectedId = ''
  if (path === 'capabilities' && params.get('view') === 'connection') section = 'CONNECTIONS'
  if (parts.length === 3 && parts[2] && parts[0] === 'knowledge' && ['connections', 'sources'].includes(parts[1])) {
    section = parts[1] === 'connections' ? 'LIVE_DATA' : 'DOCUMENTS'
    try { selectedId = decodeURIComponent(parts[2]) } catch { return null }
  }
  if (parts.length === 2 && parts[0] === 'capabilities' && !section) {
    let id = ''
    try { id = decodeURIComponent(parts[1]) } catch { return null }
    if (id.startsWith('action:') || id.startsWith('install:')) {
      section = params.get('view') === 'connection' ? 'CONNECTIONS' : 'ACTIONS'
      selectedId = id.startsWith('action:') ? id.slice('action:'.length) : id
    }
  }
  if (!section) return null
  const task = sectionTasks[section]
  return { resourceKind: 'DEPLOYMENT_DRAFT', resourceId: brainId, task,
    editorContext: { section, ...(selectedId && selectedId.length <= 256 ? { selectedDefinitionIds: [selectedId] } : {}) } }
}

export function scopeIdentity(scope: AssistantScope) { return `${scope.resourceKind}:${scope.resourceId}:${scope.task}` }

export function operation(value: unknown): { id: string; label: string; pointer: string } | null {
  if (!value || typeof value !== 'object') return null
  const row = value as Record<string, unknown>
  if (typeof row.id !== 'string' || !row.id) return null
  if (row.kind === 'MCP_TOOL') return { id: row.id,
    label: ['MCP', row.toolName, row.summary].filter(value => typeof value === 'string' && value).join(' \u00b7 '),
    pointer: typeof row.sourcePointer === 'string' ? row.sourcePointer : '' }
  return { id: row.id, label: [row.method, row.path, row.summary || row.id].filter(value => typeof value === 'string' && value).join(' '), pointer: typeof row.sourcePointer === 'string' ? row.sourcePointer : '' }
}
