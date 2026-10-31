import type { CustomerAuthConfig } from '../api/platformApi'

export type IdentityMethod = 'login' | 'register' | 'email' | 'recovery' | 'verify'
export function identityMethod(search: string): IdentityMethod {
  const method = new URLSearchParams(search).get('method')
  return method === 'register' || method === 'email' || method === 'recovery' || method === 'verify' ? method : 'login'
}

export function deskSignInUrl(config: CustomerAuthConfig | null) {
  if (!config?.enabled || !config.deskSignInUrl) throw new Error('Desk sign-in is not configured. Contact LoomAI support.')
  const entry = new URL(config.deskSignInUrl)
  const secure = entry.protocol === 'https:' || (entry.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(entry.hostname))
  if (!secure || entry.username || entry.password || entry.search || entry.hash || entry.pathname !== '/'
    || !config.deskClients?.some(client => client.redirectUris.some(uri => new URL(uri).origin === entry.origin))) {
    throw new Error('The configured Desk sign-in address is not trusted.')
  }
  return entry.href
}

export function emailCallbackError(search: string, hash: string) {
  const query = new URLSearchParams(search), fragment = new URLSearchParams(hash.replace(/^#/, ''))
  const error = query.get('error_description') || fragment.get('error_description')
  return error || (query.has('error') || fragment.has('error') ? 'This email link could not be verified. Request a new link.' : null)
}
