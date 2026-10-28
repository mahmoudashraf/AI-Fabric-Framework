const returnKey = 'loomai-console-auth-return'
type AuthReturn = { purpose: 'link' | 'desk' | 'customer'; authorizationId?: string; product?: string; createdAt: number }

export function selectedProduct(search: string): 'platform' | 'engage' | 'desk' | null {
  const product = new URLSearchParams(search).get('product')
  return product === 'platform' || product === 'engage' || product === 'desk' ? product : null
}

export function productStartPath(search: string) {
  const product = selectedProduct(search)
  return product ? `/start?product=${product}` : '/start'
}

export function rememberCustomerAuthReturn(linking: boolean) {
  const authorizationId = window.location.pathname === '/auth/desk/authorize'
    ? new URLSearchParams(window.location.search).get('authorization_id') : null
  const intent: AuthReturn = { purpose: linking ? 'link' : authorizationId ? 'desk' : 'customer',
    ...(selectedProduct(window.location.search) ? { product: selectedProduct(window.location.search)! } : {}),
    ...(authorizationId ? { authorizationId } : {}), createdAt: Date.now() }
  window.localStorage.setItem(returnKey, JSON.stringify(intent))
}

export function customerAuthReturn(): AuthReturn | null {
  try {
    const intent = JSON.parse(window.localStorage.getItem(returnKey) ?? 'null')
    if (!intent || !['link', 'desk', 'customer'].includes(intent.purpose) || typeof intent.createdAt !== 'number'
      || intent.createdAt > Date.now() || Date.now() - intent.createdAt > 3600000
      || (intent.purpose === 'desk' && (typeof intent.authorizationId !== 'string'
        || intent.authorizationId.length === 0 || intent.authorizationId.length > 512))) return null
    return intent
  } catch { return null }
}

export function consumeCustomerAuthReturn() {
  const intent = customerAuthReturn()
  window.localStorage.removeItem(returnKey)
  if (intent?.purpose === 'desk') return `/auth/desk/authorize?authorization_id=${encodeURIComponent(intent.authorizationId!)}`
  if (intent?.purpose === 'link') return '/staff/sign-in?link=customer'
  return intent?.purpose === 'customer'
    ? ['platform', 'engage', 'desk'].includes(intent.product ?? '') ? `/start?product=${intent!.product}` : '/start'
    : '/'
}
