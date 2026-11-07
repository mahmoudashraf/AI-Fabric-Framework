import { accountSignInReturn } from '../account/accountNavigation'

const returnKey = 'loomai-console-auth-return'
type AuthReturn = { purpose: 'link' | 'desk' | 'desk-entry' | 'customer'; authorizationId?: string; deskClientId?: string; product?: string; kit?: string; accountReturn?: string; createdAt: number }

function currentAccountReturn() {
  return accountSignInReturn(`${window.location.pathname}${window.location.search}`)
    ?? accountSignInReturn(new URLSearchParams(window.location.search).get('account_return'))
}

export function selectedKit(search: string): string | null {
  const kit = new URLSearchParams(search).get('kit')
  return kit && /^[a-z][a-z0-9-]{0,63}$/.test(kit) ? kit : null
}

export function selectedProduct(search: string): 'platform' | 'engage' | 'desk' | null {
  const product = new URLSearchParams(search).get('product')
  return product === 'platform' || product === 'engage' || product === 'desk' ? product : null
}

export function kitSetupCode(pathname: string): string | null {
  return pathname.match(/^\/kits\/new\/([a-z][a-z0-9-]{0,63})\/?$/)?.[1] ?? null
}

export function productStartPath(search: string, pathname = '') {
  const kit = selectedKit(search) ?? kitSetupCode(pathname)
  if (kit) return `/kits/new/${kit}`
  const product = selectedProduct(search)
  return product ? `/start?product=${product}` : '/start'
}

export function rememberCustomerAuthReturn(linking: boolean) {
  const authorizationId = window.location.pathname === '/auth/desk/authorize'
    ? new URLSearchParams(window.location.search).get('authorization_id') : null
  const intent: AuthReturn = { purpose: linking ? 'link' : authorizationId ? 'desk'
    : window.location.pathname.startsWith('/auth/desk/sign-in') ? 'desk-entry' : 'customer',
    ...(window.location.pathname.startsWith('/auth/desk/sign-in/') ? { deskClientId: window.location.pathname.slice('/auth/desk/sign-in/'.length) } : {}),
    ...(selectedProduct(window.location.search) ? { product: selectedProduct(window.location.search)! } : {}),
    ...(selectedKit(window.location.search) ? { kit: selectedKit(window.location.search)! } : {}),
    ...(currentAccountReturn() ? { accountReturn: currentAccountReturn()! } : {}),
    ...(authorizationId ? { authorizationId } : {}), createdAt: Date.now() }
  window.localStorage.setItem(returnKey, JSON.stringify(intent))
}

export function customerAuthReturn(): AuthReturn | null {
  try {
    const intent = JSON.parse(window.localStorage.getItem(returnKey) ?? 'null')
    if (!intent || !['link', 'desk', 'desk-entry', 'customer'].includes(intent.purpose) || typeof intent.createdAt !== 'number'
      || intent.createdAt > Date.now() || Date.now() - intent.createdAt > 3600000
      || (intent.purpose === 'desk' && (typeof intent.authorizationId !== 'string'
        || intent.authorizationId.length === 0 || intent.authorizationId.length > 512))
      || (intent.deskClientId !== undefined && (typeof intent.deskClientId !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(intent.deskClientId)))) return null
    if (intent.kit !== undefined && (typeof intent.kit !== 'string' || !/^[a-z][a-z0-9-]{0,63}$/.test(intent.kit))) return null
    if (intent.accountReturn !== undefined && !accountSignInReturn(intent.accountReturn)) return null
    return intent
  } catch { return null }
}

export function consumeCustomerAuthReturn() {
  const intent = customerAuthReturn()
  window.localStorage.removeItem(returnKey)
  if (intent?.purpose === 'desk') return `/auth/desk/authorize?authorization_id=${encodeURIComponent(intent.authorizationId!)}`
  if (intent?.purpose === 'link') return '/staff/sign-in?link=customer'
  if (intent?.purpose === 'desk-entry') return '/auth/desk/sign-in' + (intent.deskClientId ? '/' + intent.deskClientId : '')
  if ((!intent || intent.purpose === 'customer') && new URLSearchParams(window.location.search).get('flow') === 'desk') return deskEntryFromEmail()
  if (!intent || intent.purpose === 'customer') {
    const account = accountSignInReturn(intent?.accountReturn) ?? currentAccountReturn()
    if (account) return account
    const kit = intent?.kit ?? selectedKit(window.location.search)
    if (kit) return `/kits/new/${kit}`
  }
  return intent?.purpose === 'customer'
    ? ['platform', 'engage', 'desk'].includes(intent.product ?? '') ? `/start?product=${intent!.product}` : '/start'
    : '/'
}

export function identityEmailRedirect(recovery = false) {
  const desk = window.location.pathname.startsWith('/auth/desk/')
    || new URLSearchParams(window.location.search).get('flow') === 'desk'
  const clientId = window.location.pathname.startsWith('/auth/desk/sign-in/')
    ? window.location.pathname.slice('/auth/desk/sign-in/'.length) : new URLSearchParams(window.location.search).get('desk_client')
  const client = clientId && /^[a-zA-Z0-9_-]{1,128}$/.test(clientId) ? `&desk_client=${encodeURIComponent(clientId)}` : ''
  const kit = selectedKit(window.location.search)
  const account = currentAccountReturn()
  return `${window.location.origin}/auth/${recovery ? 'recovery' : 'callback'}${desk ? '?flow=desk' + client : account ? '?account_return=' + encodeURIComponent(account) : kit ? '?kit=' + encodeURIComponent(kit) : ''}`
}

function deskEntryFromEmail() {
  const client = new URLSearchParams(window.location.search).get('desk_client')
  return '/auth/desk/sign-in' + (client && /^[a-zA-Z0-9_-]{1,128}$/.test(client) ? '/' + client : '')
}

export function identitySignInPath() {
  const intent = customerAuthReturn()
  return new URLSearchParams(window.location.search).get('flow') === 'desk'
    || intent?.purpose === 'desk' || intent?.purpose === 'desk-entry'
    ? intent?.purpose === 'desk-entry' && intent.deskClientId ? '/auth/desk/sign-in/' + intent.deskClientId : deskEntryFromEmail() : '/sign-in'
}
