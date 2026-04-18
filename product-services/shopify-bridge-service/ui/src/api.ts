export type ShopifyBridgeShellResponse = {
  appName: string
  serviceRef: string
  environmentScope: string
  status: string
  merchantSessionAuthConfigured: boolean
  onboardingPhases: string[]
  launchCapabilities: string[]
}

export type ShopifyBridgeStoreSummary = {
  shopDomain: string
  displayName: string
  deploymentId: string | null
  deploymentName: string | null
  deploymentStatus: string | null
  consumerId: string | null
  installStatus: string
  syncStatus: string
  sourceReadinessStatus: string
  widgetStatus: string
  onboardingStatus: string
  productsEnabled: boolean
  collectionsEnabled: boolean
  pagesEnabled: boolean
  policiesEnabled: boolean
  credentials: {
    status: string
    accessTokenPresent: boolean
    refreshTokenPresent: boolean
    accessTokenSecretRef: string | null
    refreshTokenSecretRef: string | null
    checkedAt: string | null
    accessTokenExpiresAt: string | null
    refreshTokenExpiresAt: string | null
    scopesText: string | null
    expiring: boolean
  } | null
}

export type ShopifyBridgeMerchantSessionResponse = {
  shopDomain: string
  destination: string
  userId: string
  expiresAt: string
  installRecord: {
    status: string
    accessTokenSecretRef: string | null
    refreshTokenSecretRef: string | null
    scopesText: string | null
    accessTokenExpiresAt: string | null
    refreshTokenExpiresAt: string | null
    installedAt: string | null
    lastAuthenticatedAt: string | null
    lastUninstalledAt: string | null
    appBridgeHost: string | null
  } | null
  store: ShopifyBridgeStoreSummary | null
}

export type ShopifyBridgeStoreBootstrapResponse = {
  shopDomain: string
  customerId: string | null
  deploymentId: string | null
  consumerId: string | null
  createdCustomer: boolean
  createdDeployment: boolean
  createdConsumer: boolean
  installedPluginIds: string[]
  store: ShopifyBridgeStoreSummary
}

export async function fetchShell(): Promise<ShopifyBridgeShellResponse> {
  return fetchJson('/api/app/shell')
}

export async function fetchSession(): Promise<ShopifyBridgeMerchantSessionResponse> {
  return authenticatedFetchJson('/api/app/session', {
    method: 'GET',
    headers: hostHeader(),
  })
}

export async function connectStore(): Promise<ShopifyBridgeStoreSummary> {
  return authenticatedFetchJson('/api/app/store/connect', { method: 'POST' })
}

export async function bootstrapStore(): Promise<ShopifyBridgeStoreBootstrapResponse> {
  return authenticatedFetchJson('/api/app/store/bootstrap', { method: 'POST' })
}

async function authenticatedFetchJson<T>(input: string, init: RequestInit): Promise<T> {
  const token = await resolveSessionToken()
  return fetchJson<T>(input, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      ...(init.headers ?? {}),
    },
  })
}

async function fetchJson<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, init)
  if (!response.ok) {
    const errorText = await safeReadText(response)
    throw new Error(errorText || `Request failed: HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

async function resolveSessionToken(): Promise<string> {
  if (typeof window !== 'undefined' && typeof window.shopify?.idToken === 'function') {
    return window.shopify.idToken()
  }

  if (import.meta.env.DEV) {
    const params = new URLSearchParams(window.location.search)
    const devToken = params.get('dev_session_token') ?? params.get('session_token')
    if (devToken && devToken.trim()) {
      return devToken.trim()
    }
  }

  throw new Error('Shopify session token is unavailable. Open the embedded app inside Shopify admin or provide a dev_session_token query parameter in local development.')
}

async function safeReadText(response: Response): Promise<string> {
  try {
    return (await response.text()).trim()
  } catch {
    return ''
  }
}

function hostHeader(): Record<string, string> {
  const host = new URLSearchParams(window.location.search).get('host')
  return host && host.trim() ? { 'X-Shopify-Embedded-Host': host.trim() } : {}
}
