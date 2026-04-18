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
  latestVersion: {
    id: string
    versionLabel: string
    status: string
    publishedAt: string | null
  } | null
  latestRelease: {
    id: string
    deploymentVersionId: string
    status: string
    verificationStatus: string
    provisioningStatus: string
    currentStepKey: string | null
    currentStepDescription: string | null
    errorMessage: string | null
    createdAt: string
    appliedAt: string | null
    updatedAt: string
  } | null
  consumerId: string | null
  installStatus: string
  syncStatus: string
  sourceReadinessStatus: string
  widgetStatus: string
  onboardingStatus: string
  lastSourcePreflightAt: string | null
  lastSyncAt: string | null
  lastWebhookAt: string | null
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
  sourcePreflight: {
    overallStatus: string
    checkedAt: string | null
    categories: Array<{
      category: string
      enabled: boolean
      status: string
      itemCount: number
      message: string | null
    }>
  } | null
  capabilities: {
    actionCount: number
    knowledgeSourceCount: number
    shellModuleCount: number
    marketplaceDatasetCount: number
    actionNames: string[]
    knowledgeSourceIds: string[]
    shellModuleIds: string[]
    marketplaceDatasetIds: string[]
  } | null
  readiness: {
    overallStatus: string
    goLiveEligible: boolean
    storefrontReady: boolean
    goLiveBlockingReasons: string[]
    storefrontBlockingReasons: string[]
    nextActions: string[]
  } | null
  syncDetail: {
    status: string
    checkedAt: string | null
    mode: string | null
    documentCount: number
    message: string | null
  } | null
  webhookDetail: {
    topic: string | null
    eventType: string | null
    sourceCategory: string | null
    receivedAt: string | null
    invalidateSync: boolean
    message: string | null
  } | null
  widgetDetail: {
    status: string
    checkedAt: string | null
    channel: string | null
    message: string | null
    settings: {
      launcherLabel: string | null
      welcomeMessage: string | null
    } | null
  } | null
  createdAt: string
  updatedAt: string
}

export type ShopifyBridgeMerchantSessionResponse = {
  shopDomain: string
  destination: string
  userId: string
  expiresAt: string
  installRecoveryRequired: boolean
  installRecoveryMessage: string | null
  installRecoveryUrl: string | null
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

export type ShopifyStorefrontPreviewResponse = {
  ready: boolean
  shopDomain: string
  storefrontBaseUrl: string | null
  bridgeBaseUrl: string | null
  widgetStatus: string
  onboardingStatus: string
  consumerId: string | null
  deploymentId: string | null
  extensionHandle: string
  launcherLabelDefault: string
  welcomeMessageDefault: string
  themeEditorActivationUrl: string | null
  activationSteps: string[]
  blockingReasons: string[]
  message: string
}

export type ShopifyBridgeUsageSummary = {
  shopDomain: string
  generatedAt: string
  lastActivityAt: string | null
  totalToday: number
  totalLast7Days: number
  todayBreakdown: Array<{
    eventType: string
    count: number
  }>
  last7DayBreakdown: Array<{
    eventType: string
    count: number
  }>
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

export async function runSourcePreflight(): Promise<ShopifyBridgeStoreSummary> {
  return authenticatedFetchJson('/api/app/store/source-preflight', { method: 'POST' })
}

export async function bootstrapStore(): Promise<ShopifyBridgeStoreBootstrapResponse> {
  return authenticatedFetchJson('/api/app/store/bootstrap', { method: 'POST' })
}

export async function goLiveStore(): Promise<ShopifyBridgeStoreSummary> {
  return authenticatedFetchJson('/api/app/store/go-live', { method: 'POST' })
}

export async function syncNowStore(): Promise<ShopifyBridgeStoreSummary> {
  return authenticatedFetchJson('/api/app/store/sync-now', { method: 'POST' })
}

export async function fetchStorefrontPreview(): Promise<ShopifyStorefrontPreviewResponse> {
  return authenticatedFetchJson('/api/app/store/storefront-preview', { method: 'GET' })
}

export async function fetchUsageSummary(): Promise<ShopifyBridgeUsageSummary> {
  return authenticatedFetchJson('/api/app/store/usage-summary', { method: 'GET' })
}

export async function updateSourceSettings(settings: {
  productsEnabled: boolean
  collectionsEnabled: boolean
  pagesEnabled: boolean
  policiesEnabled: boolean
}): Promise<ShopifyBridgeStoreSummary> {
  return authenticatedFetchJson('/api/app/store/source-settings', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(settings),
  })
}

export async function updateWidgetSettings(settings: {
  launcherLabel: string
  welcomeMessage: string
}): Promise<ShopifyBridgeStoreSummary> {
  return authenticatedFetchJson('/api/app/store/widget-settings', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(settings),
  })
}

export async function queryMerchantPlayground(request: {
  query: string
  conversationId?: string | null
}): Promise<unknown> {
  return authenticatedFetchJson('/api/app/store/playground/query', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

export async function suggestMerchantPlayground(request: {
  content?: string
  maxSuggestions?: number
}): Promise<unknown> {
  return authenticatedFetchJson('/api/app/store/playground/suggestions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
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
