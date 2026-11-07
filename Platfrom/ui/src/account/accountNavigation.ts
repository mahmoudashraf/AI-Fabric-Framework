export const accountSections = [
  { path: 'profile', label: 'My account' },
  { path: 'team', label: 'Your team' },
  { path: 'loomai-access', label: 'LoomAI collaboration' },
  { path: 'security', label: 'Sign-in & security' },
] as const

export function isAccountPath(path: string) {
  return path === '/users' || path === '/account' || path.startsWith('/account/')
}

export function accountSection(path: string) {
  return accountSections.find(item => path === `/account/${item.path}`)?.path ?? 'team'
}

export function accountPath(section: string, search = '') {
  const source = new URLSearchParams(search)
  const params = new URLSearchParams()
  const brain = source.get('fromBrain') ?? source.get('deploymentId')
  if (brain && /^dep-[a-zA-Z0-9-]{1,100}$/.test(brain)) {
    params.set('fromBrain', brain)
    const target = source.get('targetProfileId')
    if (target && /^[a-zA-Z0-9_-]{1,160}$/.test(target)) params.set('targetProfileId', target)
  }
  return `/account/${accountSections.some(item => item.path === section) ? section : 'team'}${params.size ? `?${params}` : ''}`
}

export function accountSignInReturn(value: unknown): string | null {
  if (typeof value !== 'string' || value.length > 2048 || /[\r\n#]/.test(value)) return null
  const [path, search = ''] = value.split('?')
  if (!accountSections.some(item => path === `/account/${item.path}`) && path !== '/users' && path !== '/account') return null
  return accountPath(accountSection(path), search)
}
