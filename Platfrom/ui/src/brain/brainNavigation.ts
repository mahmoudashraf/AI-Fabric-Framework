export const brainSections = [
  { path: '', label: 'Overview', group: 'Build' },
  { path: 'knowledge', label: 'Knowledge', group: 'Build' },
  { path: 'capabilities', label: 'AI capabilities', group: 'Build' },
  { path: 'responses', label: 'Responses', group: 'Build' },
  { path: 'channels', label: 'Channels', group: 'Experience' },
  { path: 'human-support', label: 'Human support', group: 'Experience' },
  { path: 'action-reviews', label: 'Action reviews', group: 'Operations' },
  { path: 'try', label: 'Try it', group: 'Release' },
  { path: 'updates', label: 'Updates', group: 'Release' },
  { path: 'settings', label: 'Technical settings', group: 'Settings' },
] as const

export function brainIdFromPath(pathname: string): string {
  const match = pathname.match(/^\/smart-brains\/([^/]+)(?:\/|$)/)
  try { return match ? decodeURIComponent(match[1]) : '' } catch { return '' }
}

export function brainPath(brainId: string, section = '', targetProfileId?: string | null): string {
  const path = `/smart-brains/${encodeURIComponent(brainId)}${section ? `/${section.replace(/^\/+/, '')}` : ''}`
  return targetProfileId ? `${path}?${new URLSearchParams({ targetProfileId })}` : path
}

export const legacyBrainSections: Record<string, string> = {
  '/overview': '', '/prompts': 'responses', '/plugins': 'capabilities',
  '/actions': 'capabilities/actions', '/providers': 'settings/models', '/revisions': 'updates',
}

export const scopedBrainTasks: Record<string, string> = {
  '/document-knowledge': 'knowledge/documents', '/integrations': 'knowledge/connections',
  '/human-support': 'human-support', '/human-review': 'action-reviews',
}

// Preserve only supported task identifiers; selectors are resolved by the Brain shell.
export function scopedBrainTaskPath(brainId: string, pathname: string, search: string): string {
  const params = new URLSearchParams(search)
  let section = scopedBrainTasks[pathname] ?? ''
  const sourceId = params.get('sourceId') ?? (pathname === '/integrations' ? params.get('connectionId') : null)
  if (pathname === '/document-knowledge') {
    const task = params.get('task') ?? params.get('view')
    if (task && ['storage', 'discover', 'proof', 'retention'].includes(task)) section = `knowledge/${task}`
    else if (sourceId) section = `knowledge/sources/${encodeURIComponent(sourceId)}`
  } else if (pathname === '/integrations') {
    if (params.get('view') === 'human' || params.get('handoverOnly') === 'true') section = 'human-support'
    else if (sourceId) section += `/${encodeURIComponent(sourceId)}`
  }
  const [path, query] = brainPath(brainId, section, params.get('targetProfileId')).split('?')
  const next = new URLSearchParams(query)
  for (const key of ['releaseId', ...(pathname === '/human-review' ? ['taskId'] : [])]) {
    if (params.get(key)) next.set(key, params.get(key)!)
  }
  return `${path}${next.size ? `?${next}` : ''}`
}

export function currentBrainSection(pathname: string): string {
  return pathname.split('/')[3] ?? ''
}

export function brainSignInReturn(value: unknown): string | null {
  return typeof value === 'string' && value.length <= 2048 && !/[\r\n#]/.test(value)
    && brainIdFromPath(value.split('?')[0]) ? value : null
}

export function isUsableRelease(release: { provisioningStatus: string; status: string }): boolean {
  return release.provisioningStatus === 'ACTIVE'
    && ['VERIFYING', 'APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(release.status)
}

export function retainEditingRelease<T extends { id: string }>(
  previous: { scope: string; release: T | null } | null, scope: string, release: T | null, locked: boolean,
) {
  return previous?.scope === scope && locked && previous.release ? previous : { scope, release }
}
