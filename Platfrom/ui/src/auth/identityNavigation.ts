import type { CustomerAuthConfig } from '../api/platformApi'

export type IdentityMethod = 'login' | 'register' | 'email' | 'recovery' | 'verify'
export function identityMethod(search: string): IdentityMethod {
  const method = new URLSearchParams(search).get('method')
  return method === 'register' || method === 'email' || method === 'recovery' || method === 'verify' ? method : 'login'
}

export function deskSignInUrl(config: CustomerAuthConfig | null, clientId?: string | null) {
  if (!config?.enabled || !config.deskSignInUrl) throw new Error('Desk sign-in is not configured. Contact LoomAI support.')
  const entry = new URL(config.deskSignInUrl)
  const secure = entry.protocol === 'https:' || (entry.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(entry.hostname))
  if (!secure || entry.username || entry.password || entry.search || entry.hash || entry.pathname !== '/'
    || !config.deskClients?.some(client => client.redirectUris.some(uri => new URL(uri).origin === entry.origin))) {
    throw new Error('The configured Desk sign-in address is not trusted.')
  }
  if (clientId) {
    const client = config.deskClients?.find(client => client.clientId === clientId)
    if (!client) throw new Error('This Desk sign-in client is not configured.')
    const origins = [...new Set(client.redirectUris.map(uri => new URL(uri).origin))]
    const origin = origins.includes(entry.origin) ? entry.origin : origins.length === 1 ? origins[0] : null
    if (!origin || !origin.startsWith('https://')) throw new Error('This Desk sign-in destination is ambiguous or insecure.')
    return new URL('/oauth2/authorization/supabase', origin).href
  }
  return new URL('/oauth2/authorization/supabase', entry).href
}

export function deskIdentityClient(pathname: string) {
  const prefix = '/auth/desk/sign-in/'
  if (!pathname.startsWith(prefix)) return null
  const client = pathname.slice(prefix.length)
  if (!/^[a-zA-Z0-9_-]{1,128}$/.test(client)) throw new Error('The Desk sign-in client is invalid.')
  return client
}

export function emailCallbackError(search: string, hash: string) {
  const query = new URLSearchParams(search), fragment = new URLSearchParams(hash.replace(/^#/, ''))
  const error = query.get('error_description') || fragment.get('error_description')
  return error || (query.has('error') || fragment.has('error') ? 'This email link could not be verified. Request a new link.' : null)
}
