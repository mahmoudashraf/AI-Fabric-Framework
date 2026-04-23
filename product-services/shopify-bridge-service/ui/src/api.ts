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
  articlesEnabled: boolean
  metaobjectsEnabled: boolean
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
      shellModeProfile: string | null
      enabledSurfaces: string[]
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
  groundingSignals: string[]
  supportedReviewProviders: string[]
  surfacePlacements: Array<{
    surfaceId: string
    label: string
    placementType: string
    blockHandle: string
    template: string
    target: string
    themeEditorUrl: string | null
    requiredTierKey: string
    guidance: string
  }>
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
  todaySurfaceUsage: Array<{
    surfaceId: string
    label: string
    count: number
  }>
  last7DaySurfaceUsage: Array<{
    surfaceId: string
    label: string
    count: number
  }>
  topQuestionsLast7Days: Array<{
    surfaceId: string
    label: string
    queryText: string
    count: number
    lastAskedAt: string
  }>
}

export type ShopifyBridgeGovernedActionAuditSummary = {
  id: string
  actionType: string
  actionPackage: string
  surfaceId: string
  pageType: string
  productHandle: string | null
  productTitle: string | null
  variantId: string | null
  requestedQuantity: number | null
  targetQuantity: number | null
  resultingQuantity: number | null
  confirmationRequired: boolean
  confirmationAccepted: boolean
  shopperSessionRef: string | null
  status: string
  message: string | null
  createdAt: string
  expiresAt: string | null
  completedAt: string | null
}

export type ShopifyBridgeBillingSummary = {
  mode: string
  tierKey: string
  planName: string
  status: string
  merchantApprovalRequired: boolean
  launchBlocked: boolean
  paidTier: boolean
  actionCapable: boolean
  catalogProductCap: number | null
  syncCadence: string | null
  poweredByBadgeRequired: boolean
  chatFallbackEnabled: boolean
  requiresExplicitConfirmation: boolean
  auditTrailAvailable: boolean
  actionPackages: string[]
  allowedSurfaces: string[]
  availablePlans: Array<{
    tierKey: string
    planName: string
    amount: string | null
    currencyCode: string | null
    interval: string | null
    active: boolean
    commerciallyAvailable: boolean
    merchantApprovalSupported: boolean
    actionCapable: boolean
    catalogProductCap: number | null
    syncCadence: string | null
    poweredByBadgeRequired: boolean
    chatFallbackEnabled: boolean
    requiresExplicitConfirmation: boolean
    auditTrailAvailable: boolean
    actionPackages: string[]
    allowedSurfaces: string[]
    message: string
  }>
  message: string
}

export type ShopifyBridgeBillingApprovalResponse = {
  status: string
  confirmationUrl: string | null
  message: string
}

export type ShopifyBridgeStoreVectorizationRunSummary = {
  id: string
  reason: string
  status: string
  requestedStatus: string
  entityScope: string[]
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string
}

export type ShopifyBridgeStoreVectorizationSourcePolicySummary = {
  sourceCategory: string
  enabled: boolean
  manualIndexAllowed: boolean
  manualReindexAllowed: boolean
  autoIndexingEnabled: boolean
  createTriggerEnabled: boolean
  deleteTriggerEnabled: boolean
  updateTriggerMode: string
  selectedIndexedFields: string[]
  debounceWindowSeconds: number
  minimumRunIntervalSeconds: number
}

export type ShopifyBridgeStoreVectorizationPolicySummary = {
  policyVersion: number
  autoIndexingDefault: boolean
  sourcePolicies: ShopifyBridgeStoreVectorizationSourcePolicySummary[]
  updatedBy: string | null
  updatedAt: string | null
}

export type ShopifyBridgeStoreVectorizationIndexedFieldSummary = {
  fieldKey: string
  sourceCategory: string
  entityType: string
  sourceField: string
  label: string
  selectableForTriggerPolicy: boolean
}

export type ShopifyBridgeStoreVectorizationAutomationSummary = {
  autoIndexingHealthy: boolean
  queuedEvents: number
  leasedEvents: number
  dispatchedEvents: number
  skippedEvents: number
  failedEvents: number
  deadLetteredEvents: number
  lastAutoEventAt: string | null
  lastSuccessfulAutoIndexAt: string | null
  lastFailedAutoIndexAt: string | null
  lastAutoRunId: string | null
  degradedReasons: string[]
}

export type ShopifyBridgeStoreVectorizationEventSummary = {
  id: string
  sourceCategory: string
  entityType: string
  sourceObjectId: string | null
  shopifyTopic: string | null
  operation: string | null
  status: string
  triggerReason: string | null
  failureCode: string | null
  coalescedRunId: string | null
  shopifyWebhookId: string | null
  occurredAt: string | null
  queuedAt: string | null
  lastAttemptAt: string | null
  completedAt: string | null
  notes: string | null
}

export type ShopifyBridgeStoreVectorizationSummary = {
  shopDomain: string
  deploymentId: string | null
  bootstrapped: boolean
  selectedCategories: string[]
  selectedEntityTypes: string[]
  requiredPluginIds: string[]
  installedPluginIds: string[]
  missingPluginIds: string[]
  disabledPluginIds: string[]
  reconciliationRequired: boolean
  connectionConfigured: boolean
  sourceConnectionId: string | null
  sourceConnectionStatus: string | null
  sourceAdapterType: string | null
  planConfigured: boolean
  planId: string | null
  planStatus: string | null
  runnerConfigured: boolean
  runnerRegistrationId: string | null
  runnerRegistrationStatus: string | null
  deploymentApplyInProgress: boolean
  deploymentApplyStatus: string | null
  runnerMode: string | null
  syncState: string | null
  readyToRun: boolean
  blockingReasons: string[]
  lastRun: ShopifyBridgeStoreVectorizationRunSummary | null
  policy: ShopifyBridgeStoreVectorizationPolicySummary | null
  effectiveIndexedFields: ShopifyBridgeStoreVectorizationIndexedFieldSummary[]
  automation: ShopifyBridgeStoreVectorizationAutomationSummary | null
  recentEvents: ShopifyBridgeStoreVectorizationEventSummary[]
}

export type ShopifyBridgeStoreVectorizationSourcePolicyInput = {
  sourceCategory: string
  autoIndexingEnabled?: boolean | null
  createTriggerEnabled?: boolean | null
  deleteTriggerEnabled?: boolean | null
  updateTriggerMode?: string | null
  selectedIndexedFields?: string[]
  debounceWindowSeconds?: number | null
  minimumRunIntervalSeconds?: number | null
}

export type ShopifyBridgeUpdateStoreVectorizationPolicyRequest = {
  policyVersion: number | null
  sourcePolicies: ShopifyBridgeStoreVectorizationSourcePolicyInput[]
}

export type ShopifyBridgeStoreVectorizationSelectedEntitiesRequest = {
  entityTypes: string[]
}

export type ShopifyWebhookSubscriptionStatusSummary = {
  shopDomain: string
  status: string
  message: string
  webhookUri: string | null
  expectedCount: number
  readyCount: number
  missingCount: number
  driftedCount: number
  checkedAt: string | null
  topics: Array<{
    topic: string
    expectedName: string | null
    status: string
    subscriptionId: string | null
    subscriptionName: string | null
    subscriptionUri: string | null
    message: string | null
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

export async function fetchRecentGovernedActions(limit = 10): Promise<ShopifyBridgeGovernedActionAuditSummary[]> {
  return authenticatedFetchJson(`/api/app/store/actions/recent?limit=${limit}`, { method: 'GET' })
}

export async function fetchVectorizationSummary(): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization', { method: 'GET' })
}

export async function reconcileVectorization(): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/reconcile', { method: 'POST' })
}

export async function vectorizeNowStore(): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/vectorize-now', { method: 'POST' })
}

export async function indexAllStore(): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/index-all', { method: 'POST' })
}

export async function reindexAllStore(): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/reindex-all', { method: 'POST' })
}

export async function reindexSelectedStore(request: ShopifyBridgeStoreVectorizationSelectedEntitiesRequest): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/reindex-selected', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

export async function updateVectorizationPolicyStore(request: ShopifyBridgeUpdateStoreVectorizationPolicyRequest): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/policy', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
}

export async function fetchVectorizationEventsStore(limit = 10): Promise<ShopifyBridgeStoreVectorizationEventSummary[]> {
  return authenticatedFetchJson(`/api/app/store/vectorization/events?limit=${limit}`, { method: 'GET' })
}

export async function replayVectorizationEventStore(eventId: string): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson(`/api/app/store/vectorization/events/${encodeURIComponent(eventId)}/replay`, { method: 'POST' })
}

export async function retryLastFailedVectorizationAutoRunStore(): Promise<ShopifyBridgeStoreVectorizationSummary> {
  return authenticatedFetchJson('/api/app/store/vectorization/retry-last-failed-auto-run', { method: 'POST' })
}

export async function fetchBillingSummary(): Promise<ShopifyBridgeBillingSummary> {
  return authenticatedFetchJson('/api/app/store/billing-summary', { method: 'GET' })
}

export async function requestBillingApproval(tierKey?: string): Promise<ShopifyBridgeBillingApprovalResponse> {
  return authenticatedFetchJson('/api/app/store/billing/approval', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ tierKey: tierKey ?? 'STARTER' }),
  })
}

export async function fetchWebhookSubscriptions(): Promise<ShopifyWebhookSubscriptionStatusSummary> {
  return authenticatedFetchJson('/api/app/store/webhook-subscriptions', { method: 'GET' })
}

export async function updateSourceSettings(settings: {
  productsEnabled: boolean
  collectionsEnabled: boolean
  pagesEnabled: boolean
  policiesEnabled: boolean
  articlesEnabled: boolean
  metaobjectsEnabled: boolean
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
  shellModeProfile: string
  enabledSurfaces: string[]
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
