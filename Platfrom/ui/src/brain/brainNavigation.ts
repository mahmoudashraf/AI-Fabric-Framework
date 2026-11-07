export const brainSections = [
  { path: '', label: 'Overview', group: 'Build' },
  { path: 'knowledge', label: 'Knowledge', group: 'Build' },
  { path: 'capabilities', label: 'AI capabilities', group: 'Build' },
  { path: 'responses', label: 'Responses', group: 'Build' },
  { path: 'channels', label: 'Channels', group: 'Experience' },
  { path: 'human-support', label: 'Human support', group: 'Experience' },
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
