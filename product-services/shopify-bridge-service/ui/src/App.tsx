import { useEffect, useState } from 'react'
import {
  AppProvider,
  Badge,
  Banner,
  BlockStack,
  Box,
  Button,
  Card,
  Checkbox,
  Divider,
  InlineStack,
  Link,
  List,
  Page,
  Select,
  Tabs,
  Text,
  TextField,
} from '@shopify/polaris'
import enTranslations from '@shopify/polaris/locales/en.json'
import {
  bootstrapStore,
  connectStore,
  approvePartnerAccessRequest,
  denyPartnerAccessRequest,
  fetchBillingSummary,
  fetchPartnerAccessRequests,
  fetchProvisioningStatus,
  fetchRecentGovernedActions,
  fetchSession,
  fetchShell,
  fetchStorefrontPreview,
  fetchUsageSummary,
  fetchVectorizationSummary,
  fetchWebhookSubscriptions,
  goLiveStore,
  indexAllStore,
  queryMerchantPlayground,
  reindexAllStore,
  reindexSelectedStore,
  replayVectorizationEventStore,
  reconcileVectorization,
  requestBillingApproval,
  revokePartnerAccessRequest,
  retryLastFailedVectorizationAutoRunStore,
  runSourcePreflight,
  suggestMerchantPlayground,
  syncNowStore,
  updateSourceSettings,
  updateSupportProfile,
  updateVectorizationPolicyStore,
  updateWidgetSettings,
  vectorizeNowStore,
  type ShopifyBridgeBillingApprovalResponse,
  type ShopifyBridgeBillingSummary,
  type ShopifyBridgeGovernedActionAuditSummary,
  type ShopifyBridgeMerchantSessionResponse,
  type ShopifyBridgePartnerAccessRequestSummary,
  type ShopifyBridgeProvisioningStatusSummary,
  type ShopifyBridgeShellResponse,
  type ShopifyBridgeStoreBootstrapResponse,
  type ShopifyBridgeStoreSummary,
  type ShopifyBridgeStoreVectorizationIndexedFieldSummary,
  type ShopifyBridgeStoreVectorizationSourcePolicyInput,
  type ShopifyBridgeStoreVectorizationSourcePolicySummary,
  type ShopifyBridgeStoreVectorizationSummary,
  type ShopifyBridgeUsageSummary,
  type ShopifyWebhookSubscriptionStatusSummary,
  type ShopifyStorefrontPreviewResponse,
} from './api'

function badgeTone(status: string): 'success' | 'attention' | 'critical' {
  switch (status) {
    case 'READY_FOR_ONBOARDING':
    case 'LIVE':
    case 'READY':
    case 'SYNCED':
    case 'ENABLED':
    case 'ACTIVE':
    case 'APPROVED':
      return 'success'
    case 'PLATFORM_BOOTSTRAPPED':
    case 'CONNECTED':
    case 'NOT_SYNCED':
    case 'NOT_RUN':
    case 'NOT_ENABLED':
    case 'WAITING_ON_MERCHANT':
    case 'QUEUED':
    case 'RUNNING':
      return 'attention'
    default:
      return 'critical'
  }
}

function isReleaseInProgress(status: string | null | undefined): boolean {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes((status ?? '').toUpperCase())
}

type LoadState = {
  shell: ShopifyBridgeShellResponse | null
  session: ShopifyBridgeMerchantSessionResponse | null
  storefrontPreview: ShopifyStorefrontPreviewResponse | null
  usageSummary: ShopifyBridgeUsageSummary | null
  recentGovernedActions: ShopifyBridgeGovernedActionAuditSummary[]
  partnerAccessRequests: ShopifyBridgePartnerAccessRequestSummary[]
  partnerAccessError: string | null
  billingSummary: ShopifyBridgeBillingSummary | null
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null
  provisioningStatus: ShopifyBridgeProvisioningStatusSummary | null
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null
  loading: boolean
  error: string | null
}

type PlaygroundMessage = {
  role: 'assistant' | 'user'
  content: string
  products?: PlaygroundProductCard[]
  sources?: PlaygroundSourceCard[]
}

type PlaygroundProductCard = {
  title: string
  subtitle: string | null
  detail: string | null
  url: string | null
}

type PlaygroundSourceCard = {
  label: string
  excerpt: string | null
  url: string | null
}

type VectorizationPolicyDraft = {
  policyVersion: number | null
  sourcePolicies: ShopifyBridgeStoreVectorizationSourcePolicyInput[]
}

type WidgetSettingsState = {
  launcherLabel: string
  welcomeMessage: string
  shellModeProfile: string
  defaultConversationMode: string
  allowedConversationModes: string[]
  pageModeMappings: Record<string, string>
  enabledSurfaces: string[]
}

const UPDATE_TRIGGER_OPTIONS = [
  { label: 'Disabled', value: 'NONE' },
  { label: 'Any update', value: 'ANY_UPDATE' },
  { label: 'Indexed fields only', value: 'INDEXED_FIELDS_ONLY' },
  { label: 'Selected indexed fields', value: 'SELECTED_INDEXED_FIELDS' },
]

const DEFAULT_WIDGET_SURFACES = [
  'ai-search',
]

const WIDGET_SURFACE_OPTIONS = [
  { label: 'AI search dock', value: 'ai-search' },
  { label: 'Contextual pill', value: 'contextual-pill' },
  { label: 'Product insight card', value: 'product-insight' },
  { label: 'Policy strip', value: 'policy-strip' },
  { label: 'FAQ prompts', value: 'product-faq' },
  { label: 'Comparison block', value: 'comparison' },
  { label: 'Elite order lookup block', value: 'order-lookup' },
]

const STARTER_SURFACE_IDS = [
  'ai-search',
  'contextual-pill',
  'product-insight',
  'policy-strip',
  'product-faq',
  'comparison',
]

const SHELL_MODE_PROFILE_OPTIONS = [
  { label: 'Shopify Companion', value: 'SHOPIFY_COMPANION' },
  { label: 'Guided Commerce', value: 'GUIDED_COMMERCE' },
  { label: 'Guided Support', value: 'GUIDED_SUPPORT' },
]

const CONVERSATION_MODE_OPTIONS = [
  { label: 'Navigator', value: 'navigator' },
  { label: 'Deep', value: 'navigator_deep' },
  { label: 'Assistant', value: 'cart_assistant' },
  { label: 'Resolver', value: 'executor' },
]

const PAGE_MODE_OPTIONS = [
  { label: 'Landing pages', value: 'landing', helpText: 'Home pages and generic non-commerce entry points.' },
  { label: 'Product pages', value: 'product', helpText: 'Individual product detail pages.' },
  { label: 'Collection pages', value: 'collection', helpText: 'Collections and list-collections pages.' },
  { label: 'Search pages', value: 'search', helpText: 'Search results and search-led discovery flows.' },
  { label: 'Cart pages', value: 'cart', helpText: 'Cart review and checkout-adjacent pages.' },
  { label: 'Account pages', value: 'account', helpText: 'Customer account, orders, and login/register flows.' },
  { label: 'Content pages', value: 'content', helpText: 'Articles, blogs, and CMS pages.' },
]

const ADMIN_TABS = [
  { id: 'home', content: 'Home' },
  { id: 'setup', content: 'Setup' },
  { id: 'insights', content: 'Insights' },
  { id: 'billing', content: 'Billing' },
  { id: 'support', content: 'Support' },
  { id: 'partners', content: 'Partners' },
  { id: 'launch', content: 'Go live' },
  { id: 'advanced', content: 'Support tools' },
]

function defaultConversationModeForShellProfile(shellModeProfile?: string | null): string {
  return shellModeProfile === 'GUIDED_SUPPORT' ? 'navigator_deep' : 'navigator'
}

function normalizeAllowedConversationModes(values: string[] | null | undefined, fallback: string): string[] {
  const normalized = (values ?? [])
    .filter((value): value is string => Boolean(value && value.trim()))
    .map((value) => value.trim())
    .filter((value, index, array) => array.indexOf(value) === index)
  if (!normalized.includes(fallback)) {
    normalized.push(fallback)
  }
  return normalized.length ? normalized : [fallback]
}

function normalizePageModeMappings(values: Record<string, string> | null | undefined, allowedModes: string[]): Record<string, string> {
  if (!values) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(values).filter(([pageKey, mode]) => Boolean(pageKey) && Boolean(mode) && allowedModes.includes(mode))
  )
}

function buildWidgetSettingsState(snapshot?: WidgetSettingsSnapshot | null): WidgetSettingsState {
  const shellModeProfile = snapshot?.shellModeProfile ?? 'SHOPIFY_COMPANION'
  const defaultConversationMode =
    snapshot?.defaultConversationMode ?? defaultConversationModeForShellProfile(shellModeProfile)
  const allowedConversationModes = normalizeAllowedConversationModes(
    snapshot?.allowedConversationModes,
    defaultConversationMode
  )
  return {
    launcherLabel: snapshot?.launcherLabel ?? 'Ask the store assistant',
    welcomeMessage:
      snapshot?.welcomeMessage ?? 'Store assistant is ready. Ask about products, policies, or collections.',
    shellModeProfile,
    defaultConversationMode,
    allowedConversationModes,
    pageModeMappings: normalizePageModeMappings(snapshot?.pageModeMappings, allowedConversationModes),
    enabledSurfaces: snapshot?.enabledSurfaces?.length ? [...snapshot.enabledSurfaces] : [...DEFAULT_WIDGET_SURFACES],
  }
}

function buildVectorizationPolicyDraft(summary: ShopifyBridgeStoreVectorizationSummary | null): VectorizationPolicyDraft | null {
  if (!summary?.policy) {
    return null
  }
  return {
    policyVersion: summary.policy.policyVersion ?? null,
    sourcePolicies: (summary.policy.sourcePolicies ?? []).map((policy) => ({
      sourceCategory: policy.sourceCategory,
      autoIndexingEnabled: policy.autoIndexingEnabled,
      createTriggerEnabled: policy.createTriggerEnabled,
      deleteTriggerEnabled: policy.deleteTriggerEnabled,
      updateTriggerMode: policy.updateTriggerMode,
      selectedIndexedFields: [...(policy.selectedIndexedFields ?? [])],
      debounceWindowSeconds: policy.debounceWindowSeconds,
      minimumRunIntervalSeconds: policy.minimumRunIntervalSeconds,
    })),
  }
}

function sourcePolicyFieldOptions(
  summary: ShopifyBridgeStoreVectorizationSummary | null,
  sourceCategory: string
): ShopifyBridgeStoreVectorizationIndexedFieldSummary[] {
  return (summary?.effectiveIndexedFields ?? []).filter(
    (field) => field.sourceCategory === sourceCategory && field.selectableForTriggerPolicy
  )
}

export default function App() {
  const [state, setState] = useState<LoadState>({
    shell: null,
    session: null,
    storefrontPreview: null,
    usageSummary: null,
    recentGovernedActions: [],
    partnerAccessRequests: [],
    partnerAccessError: null,
    billingSummary: null,
    webhookSubscriptions: null,
    provisioningStatus: null,
    vectorizationSummary: null,
    loading: true,
    error: null,
  })
  const [selectedTab, setSelectedTab] = useState(0)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [pendingBillingReturn, setPendingBillingReturn] = useState(() => hasBillingReturnQueryParam())
  const [busyAction, setBusyAction] = useState<
    | 'connect'
    | 'preflight'
    | 'bootstrap'
    | 'sync'
    | 'go-live'
    | 'source-settings'
    | 'billing-approval'
    | 'partner-access-approve'
    | 'partner-access-deny'
    | 'partner-access-revoke'
    | 'vectorization-reconcile'
    | 'vectorize-now'
    | 'vectorization-index-all'
    | 'vectorization-reindex-all'
    | 'vectorization-reindex-selected'
    | 'vectorization-policy-save'
    | 'vectorization-event-replay'
    | 'vectorization-auto-retry'
    | null
  >(null)
  const [widgetSettings, setWidgetSettings] = useState<WidgetSettingsState>(buildWidgetSettingsState())
  const [busyWidgetSettings, setBusyWidgetSettings] = useState(false)
  const [supportProfileSettings, setSupportProfileSettings] = useState({
    contactEmail: '',
    contactUrl: '',
    helpCenterUrl: '',
    orderLookupPageUrl: '',
    supportPolicyNote: '',
  })
  const [busySupportProfile, setBusySupportProfile] = useState(false)
  const [playgroundConversationId, setPlaygroundConversationId] = useState<string | null>(null)
  const [playgroundMessages, setPlaygroundMessages] = useState<PlaygroundMessage[]>([
    {
      role: 'assistant',
      content: 'Store assistant is ready. Ask about products, policies, or collections.',
    },
  ])
  const [playgroundInput, setPlaygroundInput] = useState('')
  const [playgroundSuggestions, setPlaygroundSuggestions] = useState<string[]>([])
  const [playgroundLoading, setPlaygroundLoading] = useState(false)
  const [sourceSettings, setSourceSettings] = useState({
    productsEnabled: true,
    collectionsEnabled: true,
    pagesEnabled: true,
    policiesEnabled: true,
    articlesEnabled: true,
    metaobjectsEnabled: false,
  })
  const [selectedReindexEntityTypes, setSelectedReindexEntityTypes] = useState<string[]>([])
  const [vectorizationPolicyDraft, setVectorizationPolicyDraft] = useState<VectorizationPolicyDraft | null>(null)

  useEffect(() => {
    void refresh()
  }, [])

  useEffect(() => {
    const provisioningActive = ['QUEUED', 'RUNNING'].includes((state.provisioningStatus?.status ?? '').toUpperCase())
    if (!isReleaseInProgress(state.session?.store?.latestRelease?.status) && !provisioningActive) {
      return undefined
    }
    const timer = window.setTimeout(() => {
      void refresh()
    }, 5000)
    return () => window.clearTimeout(timer)
  }, [state.provisioningStatus?.status, state.session?.store?.latestRelease?.status])

  useEffect(() => {
    if (!pendingBillingReturn || state.loading) {
      return
    }
    if (state.error) {
      setActionError(`Returned from Shopify billing approval, but the bridge could not refresh the store state: ${state.error}`)
      clearBillingReturnQueryParam()
      setPendingBillingReturn(false)
      return
    }
    setActionMessage(describeBillingReturn(state.billingSummary))
    clearBillingReturnQueryParam()
    setPendingBillingReturn(false)
  }, [pendingBillingReturn, state.billingSummary, state.error, state.loading])

  async function refresh() {
    setState((current) => ({ ...current, loading: true, error: null, partnerAccessError: null }))
    try {
      const shell = await fetchShell()
      let session: ShopifyBridgeMerchantSessionResponse | null = null
      let storefrontPreview: ShopifyStorefrontPreviewResponse | null = null
      let usageSummary: ShopifyBridgeUsageSummary | null = null
      let recentGovernedActions: ShopifyBridgeGovernedActionAuditSummary[] = []
      let partnerAccessRequests: ShopifyBridgePartnerAccessRequestSummary[] = []
      let partnerAccessError: string | null = null
      let billingSummary: ShopifyBridgeBillingSummary | null = null
      let webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null = null
      let provisioningStatus: ShopifyBridgeProvisioningStatusSummary | null = null
      let vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null = null
      try {
        session = await fetchSession()
        storefrontPreview = await fetchStorefrontPreview()
        usageSummary = await fetchUsageSummary()
        recentGovernedActions = await fetchRecentGovernedActions()
        billingSummary = await fetchBillingSummary()
      } catch (sessionError) {
        session = null
        setState({
          shell,
          session: null,
          storefrontPreview: null,
          usageSummary: null,
          recentGovernedActions: [],
          partnerAccessRequests: [],
          partnerAccessError: null,
          billingSummary: null,
          webhookSubscriptions: null,
          provisioningStatus: null,
          vectorizationSummary: null,
          loading: false,
          error: sessionError instanceof Error ? sessionError.message : 'Failed to resolve merchant session.',
        })
        return
      }
      try {
        partnerAccessRequests = await fetchPartnerAccessRequests()
      } catch (partnerAccessLoadError) {
        partnerAccessRequests = []
        partnerAccessError = partnerAccessLoadError instanceof Error
          ? partnerAccessLoadError.message
          : 'Partner access requests could not be loaded.'
      }
      try {
        webhookSubscriptions = await fetchWebhookSubscriptions()
      } catch {
        webhookSubscriptions = null
      }
      try {
        if (session?.store) {
          provisioningStatus = await fetchProvisioningStatus()
        }
      } catch {
        provisioningStatus = null
      }
      try {
        if (session?.store?.deploymentId) {
          vectorizationSummary = await fetchVectorizationSummary()
        }
      } catch {
        vectorizationSummary = null
      }
      setState({
        shell,
        session,
        storefrontPreview,
        usageSummary,
        recentGovernedActions,
        partnerAccessRequests,
        partnerAccessError,
        billingSummary,
        webhookSubscriptions,
        provisioningStatus,
        vectorizationSummary,
        loading: false,
        error: null,
      })
      setVectorizationPolicyDraft(buildVectorizationPolicyDraft(vectorizationSummary))
      setSelectedReindexEntityTypes(vectorizationSummary?.selectedEntityTypes ?? [])
      if (session?.store) {
        setSourceSettings({
          productsEnabled: session.store.productsEnabled,
          collectionsEnabled: session.store.collectionsEnabled,
          pagesEnabled: session.store.pagesEnabled,
          policiesEnabled: session.store.policiesEnabled,
          articlesEnabled: session.store.articlesEnabled,
          metaobjectsEnabled: session.store.metaobjectsEnabled,
        })
        setWidgetSettings(buildWidgetSettingsState(session.store.widgetDetail?.settings))
        setSupportProfileSettings({
          contactEmail: session.supportReadiness?.supportProfile?.contactEmail ?? '',
          contactUrl: session.supportReadiness?.supportProfile?.contactUrl ?? '',
          helpCenterUrl: session.supportReadiness?.supportProfile?.helpCenterUrl ?? '',
          orderLookupPageUrl: session.supportReadiness?.supportProfile?.orderLookupPageUrl ?? '',
          supportPolicyNote: session.supportReadiness?.supportProfile?.supportPolicyNote ?? '',
        })
      }
    } catch (error) {
      setState({
        shell: null,
        session: null,
        storefrontPreview: null,
        usageSummary: null,
        recentGovernedActions: [],
        partnerAccessRequests: [],
        partnerAccessError: null,
        billingSummary: null,
        webhookSubscriptions: null,
        provisioningStatus: null,
        vectorizationSummary: null,
        loading: false,
        error: error instanceof Error ? error.message : 'Unknown bridge shell failure.',
      })
      setVectorizationPolicyDraft(null)
      setSelectedReindexEntityTypes([])
    }
  }

  function applyVectorizationSummary(summary: ShopifyBridgeStoreVectorizationSummary) {
    setState((current) => ({ ...current, vectorizationSummary: summary }))
    setVectorizationPolicyDraft(buildVectorizationPolicyDraft(summary))
    setSelectedReindexEntityTypes((current) => current.filter((value) => summary.selectedEntityTypes.includes(value)))
  }

  function updatePolicyDraftSource(
    sourceCategory: string,
    updater: (current: ShopifyBridgeStoreVectorizationSourcePolicyInput) => ShopifyBridgeStoreVectorizationSourcePolicyInput
  ) {
    setVectorizationPolicyDraft((current) => {
      if (!current) {
        return current
      }
      return {
        ...current,
        sourcePolicies: current.sourcePolicies.map((policy) =>
          policy.sourceCategory === sourceCategory ? updater(policy) : policy
        ),
      }
    })
  }

  async function handleConnect() {
    setBusyAction('connect')
    setActionError(null)
    setActionMessage(null)
    try {
      const store = await connectStore()
      setState((current) => ({
        ...current,
        session: current.session ? { ...current.session, store } : current.session,
      }))
      setActionMessage(`Connected ${store.shopDomain} to ${store.displayName}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to connect store.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleBootstrap() {
    setBusyAction('bootstrap')
    setActionError(null)
    setActionMessage(null)
    try {
      const result = await bootstrapStore()
      applyBootstrapResult(result)
      await refresh()
      setActionMessage(`Bootstrapped deployment ${result.deploymentId ?? '—'} for ${result.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to bootstrap store.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleSourcePreflight() {
    setBusyAction('preflight')
    setActionError(null)
    setActionMessage(null)
    try {
      const store = await runSourcePreflight()
      setState((current) => ({
        ...current,
        session: current.session ? { ...current.session, store } : current.session,
      }))
      setActionMessage(`Recorded Shopify source preflight for ${store.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to run Shopify source preflight.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleGoLive() {
    setBusyAction('go-live')
    setActionError(null)
    setActionMessage(null)
    try {
      const store = await goLiveStore()
      setState((current) => ({
        ...current,
        session: current.session ? { ...current.session, store } : current.session,
      }))
      setActionMessage(`Requested publish/apply for ${store.shopDomain}. Track the latest release below before enabling the storefront widget.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to request Shopify Companion go-live.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleBillingApproval(tierKey = 'STARTER') {
    setBusyAction('billing-approval')
    setActionError(null)
    setActionMessage(null)
    try {
      const response = await requestBillingApproval(tierKey)
      if (response.confirmationUrl) {
        redirectTopLevel(response.confirmationUrl)
        return
      }
      setActionMessage(response.message)
      await refresh()
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to request Shopify billing approval.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handlePartnerAccessApprove(requestId: string) {
    setBusyAction('partner-access-approve')
    setActionError(null)
    setActionMessage(null)
    try {
      const summary = await approvePartnerAccessRequest(requestId, {
        approverName: session?.userId ? `Shopify admin ${session.userId}` : 'Shopify admin',
        approvedScope: 'FULL_STORE_ACCESS',
      })
      await refresh()
      setActionMessage(`Approved partner access for ${summary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to approve partner access.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handlePartnerAccessDeny(requestId: string) {
    setBusyAction('partner-access-deny')
    setActionError(null)
    setActionMessage(null)
    try {
      const summary = await denyPartnerAccessRequest(requestId, {
        approverName: session?.userId ? `Shopify admin ${session.userId}` : 'Shopify admin',
        approvedScope: 'FULL_STORE_ACCESS',
        decisionReason: 'Merchant denied from Shopify admin.',
      })
      await refresh()
      setActionMessage(`Denied partner access for ${summary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to deny partner access.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handlePartnerAccessRevoke(requestId: string) {
    setBusyAction('partner-access-revoke')
    setActionError(null)
    setActionMessage(null)
    try {
      const summary = await revokePartnerAccessRequest(requestId, {
        approverName: session?.userId ? `Shopify admin ${session.userId}` : 'Shopify admin',
        approvedScope: 'FULL_STORE_ACCESS',
        decisionReason: 'Merchant revoked active partner access from Shopify admin.',
      })
      await refresh()
      setActionMessage(`Revoked partner access for ${summary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to revoke partner access.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleSyncNow() {
    setBusyAction('sync')
    setActionError(null)
    setActionMessage(null)
    try {
      const store = await syncNowStore()
      setState((current) => ({
        ...current,
        session: current.session ? { ...current.session, store } : current.session,
      }))
      setActionMessage(`Knowledge Sync refreshed enabled Shopify content for ${store.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to sync Shopify store content.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleSourceSettingsSave() {
    setBusyAction('source-settings')
    setActionError(null)
    setActionMessage(null)
    try {
      const store = await updateSourceSettings(sourceSettings)
      setState((current) => ({
        ...current,
        session: current.session ? { ...current.session, store } : current.session,
      }))
      await refresh()
      setActionMessage(`Updated source categories for ${store.shopDomain}. Knowledge Sync has been refreshed for the current selection.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update source settings.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleVectorizationReconcile() {
    setBusyAction('vectorization-reconcile')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await reconcileVectorization()
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Refreshed Knowledge Sync support for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to refresh Knowledge Sync support.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleVectorizeNow() {
    setBusyAction('vectorize-now')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await vectorizeNowStore()
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Queued Knowledge Sync for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to trigger Knowledge Sync.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleIndexAll() {
    setBusyAction('vectorization-index-all')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await indexAllStore()
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Queued indexing for all enabled Shopify data in ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to queue indexing for the current Shopify scope.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleReindexAll() {
    setBusyAction('vectorization-reindex-all')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await reindexAllStore()
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Queued a full Shopify reindex for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to queue Shopify reindex.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleReindexSelected() {
    if (!selectedReindexEntityTypes.length) {
      setActionError('Select at least one enabled entity family before requesting a bounded Shopify reindex.')
      return
    }
    setBusyAction('vectorization-reindex-selected')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await reindexSelectedStore({ entityTypes: selectedReindexEntityTypes })
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Queued a bounded Shopify reindex for ${selectedReindexEntityTypes.join(', ')}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to queue bounded Shopify reindex.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleSaveVectorizationPolicy() {
    if (!vectorizationPolicyDraft) {
      return
    }
    setBusyAction('vectorization-policy-save')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await updateVectorizationPolicyStore(vectorizationPolicyDraft)
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Saved live update policy for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to save the Shopify live update policy.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleReplayVectorizationEvent(eventId: string) {
    setBusyAction('vectorization-event-replay')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await replayVectorizationEventStore(eventId)
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Requeued Shopify live update event ${eventId}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to replay the Shopify live update event.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleRetryFailedAutoRun() {
    setBusyAction('vectorization-auto-retry')
    setActionError(null)
    setActionMessage(null)
    try {
      const vectorizationSummary = await retryLastFailedVectorizationAutoRunStore()
      applyVectorizationSummary(vectorizationSummary)
      setActionMessage(`Requeued the last failed Shopify live auto indexing run for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to retry the last failed Shopify live auto indexing run.')
    } finally {
      setBusyAction(null)
    }
  }

  async function handleWidgetSettingsSave() {
    setBusyWidgetSettings(true)
    setActionError(null)
    setActionMessage(null)
    try {
      const store = await updateWidgetSettings(widgetSettings)
      await refresh()
      setActionMessage(`Updated storefront widget settings for ${store.shopDomain}. Reopen the storefront or theme editor to use the latest launcher content.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update storefront widget settings.')
    } finally {
      setBusyWidgetSettings(false)
    }
  }

  async function handleSupportProfileSave() {
    setBusySupportProfile(true)
    setActionError(null)
    setActionMessage(null)
    try {
      const readiness = await updateSupportProfile(supportProfileSettings)
      setState((current) => ({
        ...current,
        session: current.session ? { ...current.session, supportReadiness: readiness } : current.session,
      }))
      setActionMessage(
        readiness.merchantHandoffConfigured
          ? 'Updated the merchant support handoff profile and refreshed support readiness.'
          : 'Saved the merchant support profile. Companion still needs a merchant handoff channel before launch.'
      )
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update the merchant support profile.')
    } finally {
      setBusySupportProfile(false)
    }
  }

  async function handlePlaygroundSend(content: string) {
    const query = content.trim()
    if (!query || playgroundLoading || !store) {
      return
    }

    setPlaygroundLoading(true)
    setActionError(null)
    setPlaygroundInput('')
    setPlaygroundMessages((current) => [...current, { role: 'user', content: query }])

    try {
      const payload = await queryMerchantPlayground({
        query,
        conversationId: playgroundConversationId,
      })
      const assistantMessage = extractAssistantMessage(payload)
      setPlaygroundConversationId(extractConversationId(payload) ?? playgroundConversationId)
      setPlaygroundMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content: assistantMessage,
          products: extractProductCards(payload),
          sources: extractSourceCards(payload),
        },
      ])
      const nextSuggestions = extractSuggestions(payload)
      if (nextSuggestions.length > 0) {
        setPlaygroundSuggestions(nextSuggestions)
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Merchant playground query failed.')
      setPlaygroundMessages((current) => [
        ...current,
        { role: 'assistant', content: error instanceof Error ? error.message : 'Merchant playground query failed.' },
      ])
    } finally {
      setPlaygroundLoading(false)
    }
  }

  async function handlePlaygroundReset() {
    setPlaygroundConversationId(null)
    setPlaygroundMessages([
      {
        role: 'assistant',
        content: widgetSettings.welcomeMessage,
      },
    ])
    setPlaygroundSuggestions([])
    if (!store) {
      return
    }
    try {
      const payload = await suggestMerchantPlayground({ content: '', maxSuggestions: 4 })
      const nextSuggestions = extractSuggestions(payload)
      setPlaygroundSuggestions(nextSuggestions)
    } catch {
      setPlaygroundSuggestions([])
    }
  }

  function applyBootstrapResult(result: ShopifyBridgeStoreBootstrapResponse) {
    setState((current) => ({
      ...current,
      session: current.session ? { ...current.session, store: result.store } : current.session,
    }))
  }

  const shell = state.shell
  const session = state.session
  const store = session?.store ?? null
  const storefrontPreview = state.storefrontPreview
  const usageSummary = state.usageSummary
  const recentGovernedActions = state.recentGovernedActions
  const partnerAccessRequests = state.partnerAccessRequests
  const partnerAccessError = state.partnerAccessError
  const billingSummary = state.billingSummary
  const billingApprovalRequired = Boolean(
    billingSummary?.availablePlans?.some(
      (plan) => !plan.active && plan.merchantApprovalSupported && plan.commerciallyAvailable,
    ),
  )
  const billingAllowedSurfaces =
    billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const storefrontSurfacePlacements = storefrontPreview?.surfacePlacements ?? []
  const billingPaidPlanOptions =
    billingSummary?.availablePlans?.filter((plan) => plan.tierKey !== 'FREE' && !plan.active) ?? []
  const webhookSubscriptions = state.webhookSubscriptions
  const supportReadiness = session?.supportReadiness ?? null
  const thinkerHealth = session?.thinkerHealth ?? null
  const provisioningStatus = state.provisioningStatus
  const vectorizationSummary = state.vectorizationSummary
  const shopperSurfaceUsage = usageSummary?.last7DaySurfaceUsage ?? []
  const topShopperQuestions = usageSummary?.topQuestionsLast7Days ?? []
  const unansweredShopperQuestions = usageSummary?.unansweredQuestionsLast7Days ?? []
  const actionIntentQuestions = usageSummary?.actionIntentQuestionsLast7Days ?? []
  const shopperSurfaceJourneys = usageSummary?.last7DaySurfaceJourneys ?? []
  const roiSummary = usageSummary?.roiSummary ?? null
  const sourceCoverageSignals = buildSourceCoverageSignals(store)
  const intelligenceReadiness = buildStoreIntelligenceReadiness(
    store,
    storefrontPreview,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    store?.widgetDetail?.settings ?? null,
  )
  const launchReadiness = buildLaunchReadiness(
    store,
    storefrontPreview,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    usageSummary,
    store?.widgetDetail?.settings ?? null,
    supportReadiness,
  )
  const launchPacket = buildLaunchPacket(
    store,
    storefrontPreview,
    billingSummary,
    usageSummary,
    store?.widgetDetail?.settings ?? null,
    supportReadiness,
  )
  const goLiveChecklist = buildGoLiveChecklist(
    session,
    store,
    storefrontPreview,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    store?.widgetDetail?.settings ?? null,
    supportReadiness,
  )
  const supportBundleText = buildSupportBundle(
    shell,
    session,
    storefrontPreview,
    usageSummary,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    supportReadiness,
  )
  const launchDossierText = buildLaunchDossier(
    shell,
    session,
    store,
    storefrontPreview,
    usageSummary,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    goLiveChecklist,
    launchPacket,
    supportReadiness,
  )
  const appStoreListingPackageText = buildAppStoreListingPackage(
    store,
    storefrontPreview,
    billingSummary,
    launchPacket,
    usageSummary,
    supportReadiness,
  )
  const designPartnerRolloutText = buildDesignPartnerRolloutPacket(
    shell,
    session,
    store,
    storefrontPreview,
    billingSummary,
    usageSummary,
    goLiveChecklist,
    launchPacket,
    supportReadiness,
  )
  const appReviewGuideText = buildAppReviewGuide(
    shell,
    session,
    store,
    storefrontPreview,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    goLiveChecklist,
    launchPacket,
    supportReadiness,
  )
  const reviewScreencastScriptText = buildReviewScreencastScript(
    shell,
    session,
    store,
    storefrontPreview,
    billingSummary,
    launchPacket,
    supportReadiness,
  )
  const supportRunbookText = buildSupportRunbook(
    session,
    store,
    storefrontPreview,
    usageSummary,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    goLiveChecklist,
    supportReadiness,
  )
  const lifecycleSubscriptionPacketText = buildLifecycleSubscriptionPacket(
    session,
    store,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
    supportReadiness,
  )
  const installRecoveryRequired = Boolean(session?.installRecoveryRequired)
  const installRecoveryUrl = session?.installRecoveryUrl ?? null
  const orderLookupTierAllowed = billingAllowedSurfaces.includes('order-lookup')
  const scopeGrantRequired = Boolean(orderLookupTierAllowed && supportReadiness?.scopeGrantRequired)
  const scopeGrantUrl = supportReadiness?.scopeGrantUrl ?? null
  const supportReadinessLaunchBlocked =
    orderLookupTierAllowed && supportReadiness?.status != null && supportReadiness.status !== 'READY'
  const billingLaunchBlocked = Boolean(billingSummary?.launchBlocked)
  const canGoLive =
    Boolean(session) &&
    Boolean(store) &&
    !installRecoveryRequired &&
    !supportReadinessLaunchBlocked &&
    !billingLaunchBlocked &&
    Boolean(store?.readiness?.goLiveEligible) &&
    !isReleaseInProgress(store?.latestRelease?.status)
  const canSyncNow =
    Boolean(session) &&
    Boolean(store?.deploymentId) &&
    !installRecoveryRequired &&
    !isReleaseInProgress(store?.latestRelease?.status)
  const canReconcileVectorization =
    Boolean(session) &&
    Boolean(store?.deploymentId) &&
    !installRecoveryRequired
  const canVectorizeNow =
    Boolean(session) &&
    Boolean(vectorizationSummary?.readyToRun) &&
    !installRecoveryRequired
  const vectorizationBusy =
    busyAction === 'vectorization-reconcile' ||
    busyAction === 'vectorize-now' ||
    busyAction === 'vectorization-index-all' ||
    busyAction === 'vectorization-reindex-all' ||
    busyAction === 'vectorization-reindex-selected' ||
    busyAction === 'vectorization-policy-save' ||
    busyAction === 'vectorization-event-replay' ||
    busyAction === 'vectorization-auto-retry'
  const sourceSettingsDirty =
    !!store &&
    (store.productsEnabled !== sourceSettings.productsEnabled ||
      store.collectionsEnabled !== sourceSettings.collectionsEnabled ||
      store.pagesEnabled !== sourceSettings.pagesEnabled ||
      store.policiesEnabled !== sourceSettings.policiesEnabled ||
      store.articlesEnabled !== sourceSettings.articlesEnabled ||
      store.metaobjectsEnabled !== sourceSettings.metaobjectsEnabled)
  const widgetSettingsDirty =
    !!store &&
    (() => {
      const persisted = buildWidgetSettingsState(store.widgetDetail?.settings)
      return (
        persisted.launcherLabel !== widgetSettings.launcherLabel ||
        persisted.welcomeMessage !== widgetSettings.welcomeMessage ||
        persisted.shellModeProfile !== widgetSettings.shellModeProfile ||
        persisted.defaultConversationMode !== widgetSettings.defaultConversationMode ||
        JSON.stringify(persisted.allowedConversationModes) !== JSON.stringify(widgetSettings.allowedConversationModes) ||
        JSON.stringify(persisted.pageModeMappings) !== JSON.stringify(widgetSettings.pageModeMappings) ||
        JSON.stringify(persisted.enabledSurfaces) !== JSON.stringify(widgetSettings.enabledSurfaces)
      )
    })()
  const supportProfileDirty =
    (supportReadiness?.supportProfile?.contactEmail ?? '') !== supportProfileSettings.contactEmail ||
    (supportReadiness?.supportProfile?.contactUrl ?? '') !== supportProfileSettings.contactUrl ||
    (supportReadiness?.supportProfile?.helpCenterUrl ?? '') !== supportProfileSettings.helpCenterUrl ||
    (supportReadiness?.supportProfile?.orderLookupPageUrl ?? '') !== supportProfileSettings.orderLookupPageUrl ||
    (supportReadiness?.supportProfile?.supportPolicyNote ?? '') !== supportProfileSettings.supportPolicyNote
  const selectedSection = ADMIN_TABS[selectedTab]?.id ?? 'home'

  useEffect(() => {
    if (!store) {
      return
    }
    setPlaygroundMessages([
      {
        role: 'assistant',
        content: store.widgetDetail?.settings?.welcomeMessage ?? 'Store assistant is ready. Ask about products, policies, or collections.',
      },
    ])
    setPlaygroundConversationId(null)
    setPlaygroundSuggestions([])
    void suggestMerchantPlayground({ content: '', maxSuggestions: 4 })
      .then((payload) => {
        setPlaygroundSuggestions(extractSuggestions(payload))
      })
      .catch(() => {
        setPlaygroundSuggestions([])
      })
  }, [store?.shopDomain])

  async function handleCopySupportBundle() {
    try {
      await navigator.clipboard.writeText(supportBundleText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion support bundle to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the support bundle.')
    }
  }

  function handleDownloadSupportBundle() {
    try {
      const blob = new Blob([supportBundleText], { type: 'text/plain;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-support-bundle-${safeShopDomain}.txt`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion support bundle.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the support bundle.')
    }
  }

  async function handleCopyLaunchDossier() {
    try {
      await navigator.clipboard.writeText(launchDossierText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion launch dossier to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the launch dossier.')
    }
  }

  function handleDownloadLaunchDossier() {
    try {
      const blob = new Blob([launchDossierText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-launch-dossier-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion launch dossier.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the launch dossier.')
    }
  }

  async function handleCopyAppStoreListingPackage() {
    try {
      await navigator.clipboard.writeText(appStoreListingPackageText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion App Store listing package to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the App Store listing package.')
    }
  }

  function handleDownloadAppStoreListingPackage() {
    try {
      const blob = new Blob([appStoreListingPackageText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-app-store-package-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion App Store listing package.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the App Store listing package.')
    }
  }

  async function handleCopyDesignPartnerRollout() {
    try {
      await navigator.clipboard.writeText(designPartnerRolloutText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion design-partner rollout packet to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the design-partner rollout packet.')
    }
  }

  function handleDownloadDesignPartnerRollout() {
    try {
      const blob = new Blob([designPartnerRolloutText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-design-partner-rollout-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion design-partner rollout packet.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the design-partner rollout packet.')
    }
  }

  async function handleCopyAppReviewGuide() {
    try {
      await navigator.clipboard.writeText(appReviewGuideText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion App Review guide to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the App Review guide.')
    }
  }

  function handleDownloadAppReviewGuide() {
    try {
      const blob = new Blob([appReviewGuideText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-app-review-guide-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion App Review guide.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the App Review guide.')
    }
  }

  async function handleCopyReviewScreencastScript() {
    try {
      await navigator.clipboard.writeText(reviewScreencastScriptText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion review screencast script to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the review screencast script.')
    }
  }

  function handleDownloadReviewScreencastScript() {
    try {
      const blob = new Blob([reviewScreencastScriptText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-review-screencast-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion review screencast script.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the review screencast script.')
    }
  }

  async function handleCopySupportRunbook() {
    try {
      await navigator.clipboard.writeText(supportRunbookText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion support runbook to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the support runbook.')
    }
  }

  function handleDownloadSupportRunbook() {
    try {
      const blob = new Blob([supportRunbookText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-support-runbook-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion support runbook.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the support runbook.')
    }
  }

  async function handleCopyLifecycleSubscriptionPacket() {
    try {
      await navigator.clipboard.writeText(lifecycleSubscriptionPacketText)
      setActionError(null)
      setActionMessage('Copied Shopify Companion lifecycle and subscription packet to the clipboard.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to copy the lifecycle and subscription packet.')
    }
  }

  function handleDownloadLifecycleSubscriptionPacket() {
    try {
      const blob = new Blob([lifecycleSubscriptionPacketText], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      const safeShopDomain = session?.shopDomain?.replace(/[^a-z0-9.-]+/gi, '-').toLowerCase() || 'shopify-store'
      anchor.href = url
      anchor.download = `shopify-companion-lifecycle-subscription-${safeShopDomain}.md`
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
      setActionError(null)
      setActionMessage('Downloaded Shopify Companion lifecycle and subscription packet.')
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to download the lifecycle and subscription packet.')
    }
  }

  return (
    <AppProvider i18n={enTranslations}>
      <Page
        title="Loom Companion"
        subtitle="Store setup, storefront surfaces, shopper insights, billing, and support."
        primaryAction={{ content: 'Refresh', onAction: () => void refresh() }}
      >
        <BlockStack gap="400">
          {state.error ? <Banner tone="critical">{state.error}</Banner> : null}
          {actionError ? <Banner tone="critical">{actionError}</Banner> : null}
          {actionMessage ? <Banner tone="success">{actionMessage}</Banner> : null}
          {state.loading ? <Banner tone="info">Loading Shopify Bridge shell…</Banner> : null}
          <Tabs tabs={ADMIN_TABS} selected={selectedTab} onSelect={setSelectedTab} />

          {selectedSection === 'home' ? (
          <Card>
            <BlockStack gap="300">
              <InlineStack align="space-between">
                <Text as="h2" variant="headingMd">
                  App status
                </Text>
                <Badge tone={badgeTone(shell?.status ?? 'UNKNOWN')}>{shell?.status ?? 'UNKNOWN'}</Badge>
              </InlineStack>
              <Text as="p" variant="bodyMd" tone="subdued">
                Manage one Shopify store, keep shopper-facing surfaces aligned with the active plan, and close only the actions that are blocking launch.
              </Text>
              <Text as="p" variant="bodyMd" tone={shell?.merchantSessionAuthConfigured ? 'success' : 'critical'}>
                Merchant session auth configured: {shell?.merchantSessionAuthConfigured ? 'yes' : 'no'}
              </Text>
            </BlockStack>
          </Card>
          ) : null}

          <InlineStack gap="400" blockAlign="start" align="start">
            {selectedSection === 'home' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Merchant session
                  </Text>
                  {session ? (
                    <BlockStack gap="200">
                        <List type="bullet">
                          <List.Item>Shop: {session.shopDomain}</List.Item>
                        <List.Item>Session active until: {new Date(session.expiresAt).toLocaleString()}</List.Item>
                        <List.Item>Install record: {session.installRecord?.status ?? 'MISSING'}</List.Item>
                      </List>
                      {installRecoveryRequired ? (
                        <Banner tone="warning">
                          <BlockStack gap="200">
                            <Text as="p" variant="bodyMd">
                              {session.installRecoveryMessage ?? 'This shop must complete the Shopify install flow again before Companion can continue onboarding.'}
                            </Text>
                            {installRecoveryUrl ? (
                              <InlineStack gap="200">
                                <Button url={installRecoveryUrl} target="_top" variant="primary">
                                  Reconnect Shopify app
                                </Button>
                              </InlineStack>
                            ) : null}
                          </BlockStack>
                        </Banner>
                      ) : null}
                      {!installRecoveryRequired && scopeGrantRequired ? (
                        <Banner tone="warning">
                          <BlockStack gap="200">
                            <Text as="p" variant="bodyMd">
                              {supportReadiness?.message ?? 'Approve Shopify order-read scope before claiming customer-safe order lookup.'}
                            </Text>
                            {scopeGrantUrl ? (
                              <InlineStack gap="200">
                                <Button url={scopeGrantUrl} target="_top" variant="primary">
                                  Approve order-read scope
                                </Button>
                              </InlineStack>
                            ) : null}
                          </BlockStack>
                        </Banner>
                      ) : null}
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      No authenticated Shopify merchant session is available yet.
                    </Text>
                  )}
                </BlockStack>
              </Card>
              <Box paddingBlockStart="400">
                <Card>
                  <BlockStack gap="300">
                    <InlineStack gap="200" align="space-between" blockAlign="center">
                      <Text as="h2" variant="headingMd">
                        Thinker deep diagnosis
                      </Text>
                      <Badge tone={badgeTone(thinkerHealth?.status ?? 'DISABLED')}>
                        {thinkerHealth?.status ?? 'DISABLED'}
                      </Badge>
                    </InlineStack>
                    <Text as="p" variant="bodyMd" tone="subdued">
                      Merchant-safe visibility into whether deep diagnostic sessions can be captured for this store. Partner access still requires an active merchant-approved assignment.
                    </Text>
                    {thinkerHealth ? (
                      <BlockStack gap="200">
                        <List type="bullet">
                          <List.Item>Enabled: {thinkerHealth.enabled ? 'yes' : 'no'}</List.Item>
                          <List.Item>Recent sessions: {thinkerHealth.recentSessionCount}</List.Item>
                          <List.Item>Blocked sessions: {thinkerHealth.blockedSessionCount}</List.Item>
                          <List.Item>Linked deployment: {thinkerHealth.deploymentId ?? 'not linked'}</List.Item>
                        </List>
                        <Text as="p" variant="bodySm" tone="subdued">
                          {thinkerHealth.message}
                        </Text>
                        {thinkerHealth.nextActions.length ? (
                          <Banner tone={thinkerHealth.status === 'READY' ? 'success' : 'warning'}>
                            <List type="bullet">
                              {thinkerHealth.nextActions.map((action) => (
                                <List.Item key={action}>{action}</List.Item>
                              ))}
                            </List>
                          </Banner>
                        ) : null}
                      </BlockStack>
                    ) : (
                      <Text as="p" variant="bodyMd" tone="subdued">
                        Thinker health has not been returned in the current merchant session.
                      </Text>
                    )}
                  </BlockStack>
                </Card>
              </Box>
            </Box>
            ) : null}

            {selectedSection === 'setup' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Source categories
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Choose the bounded Shopify source categories that should flow into Companion knowledge. Changing these toggles resets source readiness and requires a fresh preflight and apply-time sync.
                  </Text>
                  <Checkbox
                    label="Products"
                    checked={sourceSettings.productsEnabled}
                    onChange={(checked) => setSourceSettings((current) => ({ ...current, productsEnabled: checked }))}
                  />
                  <Checkbox
                    label="Collections"
                    checked={sourceSettings.collectionsEnabled}
                    onChange={(checked) => setSourceSettings((current) => ({ ...current, collectionsEnabled: checked }))}
                  />
                  <Checkbox
                    label="Pages"
                    checked={sourceSettings.pagesEnabled}
                    onChange={(checked) => setSourceSettings((current) => ({ ...current, pagesEnabled: checked }))}
                  />
                  <Checkbox
                    label="Policies"
                    checked={sourceSettings.policiesEnabled}
                    onChange={(checked) => setSourceSettings((current) => ({ ...current, policiesEnabled: checked }))}
                  />
                  <Checkbox
                    label="Articles"
                    checked={sourceSettings.articlesEnabled}
                    onChange={(checked) => setSourceSettings((current) => ({ ...current, articlesEnabled: checked }))}
                  />
                  <Checkbox
                    label="Metaobjects"
                    checked={sourceSettings.metaobjectsEnabled}
                    onChange={(checked) => setSourceSettings((current) => ({ ...current, metaobjectsEnabled: checked }))}
                  />
                  <InlineStack gap="200">
                    <Button
                      onClick={() => void handleSourceSettingsSave()}
                      loading={busyAction === 'source-settings'}
                      disabled={!session || installRecoveryRequired || !sourceSettingsDirty}
                    >
                      Save source settings
                    </Button>
                  </InlineStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'home' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Store lifecycle
                  </Text>
                  {store ? <StoreSummary store={store} /> : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      This merchant has not connected the current store to the platform yet.
                    </Text>
                  )}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'home' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <InlineStack gap="200" align="space-between" blockAlign="center">
                    <Text as="h2" variant="headingMd">
                      Product package
                    </Text>
                    <InlineStack gap="150">
                      <Badge tone={badgeTone(provisioningStatus?.status ?? 'NOT_STARTED')}>
                        {provisioningStatus?.status ?? 'Not started'}
                      </Badge>
                      <Badge tone={badgeTone(provisioningStatus?.phase ?? 'NOT_STARTED')}>
                        {provisioningStatus?.phase ?? 'No phase'}
                      </Badge>
                    </InlineStack>
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    {provisioningStatus?.summaryMessage ?? 'Provisioning begins after app install or plan/source changes.'}
                  </Text>
                  {provisioningStatus?.effectiveProfile ? (
                    <List type="bullet">
                      <List.Item>Package: {provisioningStatus.effectiveProfile.packageKey} · tier {provisioningStatus.effectiveProfile.tierKey}</List.Item>
                      <List.Item>Runtime profile: {provisioningStatus.effectiveProfile.runtimeProfileKey}</List.Item>
                      <List.Item>Vector profile: {provisioningStatus.effectiveProfile.vectorProfileKey}</List.Item>
                      <List.Item>Verification pack: {provisioningStatus.effectiveProfile.verificationPackId ?? 'platform default'}</List.Item>
                    </List>
                  ) : null}
                  {provisioningStatus?.latestJob ? (
                    <Text as="p" variant="bodySm" tone={provisioningStatus.latestJob.lastErrorMessage ? 'critical' : 'subdued'}>
                      Latest job {provisioningStatus.latestJob.jobType} · {provisioningStatus.latestJob.status}/{provisioningStatus.latestJob.phase} · updated{' '}
                      {formatTimestamp(provisioningStatus.latestJob.updatedAt)}
                      {provisioningStatus.latestJob.vectorReindexRequired ? ' · sync required' : ''}
                      {provisioningStatus.latestJob.lastErrorMessage ? ` · ${provisioningStatus.latestJob.lastErrorMessage}` : ''}
                    </Text>
                  ) : null}
                  {provisioningStatus?.nextAction ? (
                    <Text as="p" variant="bodySm" tone="subdued">
                      {provisioningStatus.nextAction}
                    </Text>
                  ) : null}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'billing' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <InlineStack gap="200" align="space-between" blockAlign="center">
                    <Text as="h2" variant="headingMd">
                      Billing and plan
                    </Text>
                    <Badge tone={billingSummary?.launchBlocked ? 'critical' : 'success'}>
                      {billingSummary?.tierKey ?? 'UNKNOWN'}
                    </Badge>
                  </InlineStack>
                  {billingSummary ? (
                    <BlockStack gap="200">
                      <Text as="p" variant="bodyMd" tone={billingSummary.launchBlocked ? 'critical' : 'subdued'}>
                        {billingSummary.message}
                      </Text>
                      <List type="bullet">
                        <List.Item>Current plan: {billingSummary.planName}</List.Item>
                        <List.Item>Allowed surfaces: {billingAllowedSurfaces.join(' · ') || '—'}</List.Item>
                        <List.Item>Product cap: {billingSummary.catalogProductCap ?? 'unlimited'}</List.Item>
                        <List.Item>Sync cadence: {billingSummary.syncCadence ?? 'platform default'}</List.Item>
                        <List.Item>Powered-by badge: {billingSummary.poweredByBadgeRequired ? 'required' : 'optional'}</List.Item>
                        <List.Item>Chat fallback: {billingSummary.chatFallbackEnabled ? 'enabled' : 'disabled'}</List.Item>
                      </List>
                      {billingSummary.availablePlans?.length ? (
                        <BlockStack gap="200">
                          <Text as="h3" variant="headingSm">
                            Available plans
                          </Text>
                          {billingSummary.availablePlans.map((plan) => {
                            const canActivate = !plan.active && plan.merchantApprovalSupported && plan.commerciallyAvailable
                            return (
                              <Box key={plan.tierKey} padding="200" borderWidth="025" borderColor="border" borderRadius="200">
                                <BlockStack gap="150">
                                  <InlineStack gap="200" align="space-between" blockAlign="center">
                                    <Text as="p" variant="bodyMd" fontWeight="semibold">
                                      {plan.planName}
                                    </Text>
                                    <InlineStack gap="150">
                                      <Badge tone={plan.active ? 'success' : 'info'}>
                                        {plan.active ? 'Current' : plan.tierKey}
                                      </Badge>
                                      <Badge tone={plan.commerciallyAvailable ? 'success' : 'attention'}>
                                        {plan.commerciallyAvailable ? 'Available' : 'Not available yet'}
                                      </Badge>
                                    </InlineStack>
                                  </InlineStack>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {formatPlanPrice(plan)} · {plan.allowedSurfaces.join(' · ') || 'No storefront surfaces'}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {plan.actionCapable ? 'Read + governed actions' : 'Read-only shopper intelligence'} · sync {plan.syncCadence ?? 'platform default'}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {plan.message}
                                  </Text>
                                  {canActivate ? (
                                    <InlineStack gap="200">
                                      <Button
                                        variant="primary"
                                        onClick={() => void handleBillingApproval(plan.tierKey)}
                                        loading={busyAction === 'billing-approval'}
                                      >
                                        Activate {plan.planName}
                                      </Button>
                                    </InlineStack>
                                  ) : null}
                                </BlockStack>
                              </Box>
                            )
                          })}
                        </BlockStack>
                      ) : null}
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      Billing appears after the current merchant session resolves.
                    </Text>
                  )}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <InlineStack gap="200" align="space-between" blockAlign="center">
                    <Text as="h2" variant="headingMd">
                      Support tools
                    </Text>
                    <Badge tone="info">Internal</Badge>
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    This area is for support, App Review, and operator handoff. Merchants do not need it for daily setup.
                  </Text>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Deployment vectorization
                  </Text>
                  {vectorizationSummary ? (
                    <BlockStack gap="200">
                      <InlineStack gap="200" align="start">
                        <Badge tone={vectorizationSummary.readyToRun ? 'success' : 'attention'}>
                          {vectorizationSummary.readyToRun ? 'Ready to run' : 'Needs reconcile'}
                        </Badge>
                        {vectorizationSummary.syncState ? (
                          <Badge tone={badgeTone(vectorizationSummary.syncState)}>{vectorizationSummary.syncState}</Badge>
                        ) : null}
                        {vectorizationSummary.automation ? (
                          <Badge tone={vectorizationSummary.automation.autoIndexingHealthy ? 'success' : 'attention'}>
                            {vectorizationSummary.automation.autoIndexingHealthy ? 'Live updates healthy' : 'Live updates degraded'}
                          </Badge>
                        ) : null}
                      </InlineStack>
                      <Text as="p" variant="bodyMd" tone="subdued">
                        Shopify source selection now drives deployment plugin installs and the deployment vectorization plan. Use reconcile to align the deployment with the current store scope, then use bounded Index, Reindex, and Live updates controls for the enabled data.
                      </Text>
                      <List type="bullet">
                        <List.Item>Selected categories: {vectorizationSummary.selectedCategories.join(', ') || 'None selected'}</List.Item>
                        <List.Item>Selected entity types: {vectorizationSummary.selectedEntityTypes.join(', ') || 'None selected'}</List.Item>
                        <List.Item>Required plugins: {vectorizationSummary.requiredPluginIds.join(', ') || 'None required'}</List.Item>
                        <List.Item>Installed plugins: {vectorizationSummary.installedPluginIds.join(', ') || 'None installed'}</List.Item>
                        <List.Item>Missing plugins: {vectorizationSummary.missingPluginIds.join(', ') || 'None'}</List.Item>
                        <List.Item>Disabled plugins: {vectorizationSummary.disabledPluginIds.join(', ') || 'None'}</List.Item>
                        <List.Item>Source connection: {vectorizationSummary.sourceConnectionStatus ?? 'Not configured'}</List.Item>
                        <List.Item>Runner: {vectorizationSummary.runnerConfigured ? (vectorizationSummary.runnerRegistrationStatus ?? 'Configured') : 'Not provisioned'}</List.Item>
                        <List.Item>Deployment apply: {vectorizationSummary.deploymentApplyInProgress ? (vectorizationSummary.deploymentApplyStatus ?? 'IN_PROGRESS') : (vectorizationSummary.deploymentApplyStatus ?? 'Idle')}</List.Item>
                        <List.Item>Plan runner: {vectorizationSummary.runnerMode ?? 'Not configured'}</List.Item>
                        <List.Item>Policy version: {vectorizationSummary.policy?.policyVersion ?? '—'}</List.Item>
                        <List.Item>
                          Live updates backlog:
                          {' '}queued {vectorizationSummary.automation?.queuedEvents ?? 0},
                          {' '}failed {vectorizationSummary.automation?.failedEvents ?? 0},
                          {' '}dead-lettered {vectorizationSummary.automation?.deadLetteredEvents ?? 0}
                        </List.Item>
                      </List>
                      {vectorizationSummary.lastRun ? (
                        <Text as="p" variant="bodySm" tone="subdued">
                          Last run: {vectorizationSummary.lastRun.reason} · {vectorizationSummary.lastRun.status} · {formatTimestamp(vectorizationSummary.lastRun.createdAt)}
                        </Text>
                      ) : (
                        <Text as="p" variant="bodySm" tone="subdued">
                          No vectorization run has been queued for this deployment yet.
                        </Text>
                      )}
                      {vectorizationSummary.blockingReasons.length ? (
                        <Banner tone="warning">
                          <List type="bullet">
                            {vectorizationSummary.blockingReasons.map((reason) => (
                              <List.Item key={reason}>{reason}</List.Item>
                            ))}
                          </List>
                        </Banner>
                      ) : null}
                      {vectorizationSummary.automation?.degradedReasons?.length ? (
                        <Banner tone="warning">
                          <List type="bullet">
                            {vectorizationSummary.automation.degradedReasons.map((reason) => (
                              <List.Item key={reason}>{reason}</List.Item>
                            ))}
                          </List>
                        </Banner>
                      ) : null}
                      <InlineStack gap="200">
                        <Button
                          onClick={() => void handleVectorizationReconcile()}
                          loading={busyAction === 'vectorization-reconcile'}
                          disabled={!canReconcileVectorization}
                        >
                          Reconcile deployment support
                        </Button>
                        <Button
                          variant="primary"
                          onClick={() => void handleIndexAll()}
                          loading={busyAction === 'vectorization-index-all'}
                          disabled={!canVectorizeNow}
                        >
                          Index all enabled data
                        </Button>
                        <Button
                          onClick={() => void handleReindexAll()}
                          loading={busyAction === 'vectorization-reindex-all'}
                          disabled={!canVectorizeNow}
                        >
                          Reindex all enabled data
                        </Button>
                      </InlineStack>

                      <BlockStack gap="200">
                        <Text as="p" variant="bodySm" tone="subdued">
                          Bounded reindex selection
                        </Text>
                        <InlineStack gap="200">
                          {vectorizationSummary.selectedEntityTypes.map((entityType) => (
                            <Checkbox
                              key={entityType}
                              label={entityType}
                              checked={selectedReindexEntityTypes.includes(entityType)}
                              onChange={(checked) => {
                                setSelectedReindexEntityTypes((current) =>
                                  checked
                                    ? Array.from(new Set([...current, entityType]))
                                    : current.filter((value) => value !== entityType)
                                )
                              }}
                            />
                          ))}
                        </InlineStack>
                        <InlineStack gap="200">
                          <Button
                            onClick={() => void handleReindexSelected()}
                            loading={busyAction === 'vectorization-reindex-selected'}
                            disabled={!canVectorizeNow || !selectedReindexEntityTypes.length}
                          >
                            Reindex selected types
                          </Button>
                          <Button
                            onClick={() => void handleRetryFailedAutoRun()}
                            loading={busyAction === 'vectorization-auto-retry'}
                            disabled={vectorizationBusy || !vectorizationSummary.automation?.lastFailedAutoIndexAt}
                          >
                            Retry failed live updates
                          </Button>
                        </InlineStack>
                      </BlockStack>

                      <BlockStack gap="150">
                        <Text as="p" variant="bodySm" tone="subdued">
                          Effective indexed fields
                        </Text>
                        {vectorizationSummary.effectiveIndexedFields.length ? (
                          <List type="bullet">
                            {vectorizationSummary.effectiveIndexedFields.map((field) => (
                              <List.Item key={field.fieldKey}>
                                {field.label} · {field.sourceCategory} · {field.entityType}
                              </List.Item>
                            ))}
                          </List>
                        ) : (
                          <Text as="p" variant="bodySm" tone="subdued">
                            Effective indexed fields appear after the deployment vectorization plan is active.
                          </Text>
                        )}
                      </BlockStack>

                      {vectorizationPolicyDraft ? (
                        <BlockStack gap="200">
                          <Text as="h3" variant="headingSm">
                            Live update policy
                          </Text>
                          {vectorizationSummary.policy?.sourcePolicies.map((policy) => {
                            const draftPolicy =
                              vectorizationPolicyDraft.sourcePolicies.find((current) => current.sourceCategory === policy.sourceCategory) ?? policy
                            const selectableFields = sourcePolicyFieldOptions(vectorizationSummary, policy.sourceCategory)
                            return (
                              <Box key={policy.sourceCategory} padding="200" borderWidth="025" borderColor="border" borderRadius="200">
                                <BlockStack gap="200">
                                  <Text as="p" variant="bodyMd" fontWeight="semibold">
                                    {policy.sourceCategory}
                                  </Text>
                                  <InlineStack gap="200">
                                    <Checkbox
                                      label="Live updates"
                                      checked={Boolean(draftPolicy.autoIndexingEnabled)}
                                      disabled={!policy.enabled}
                                      onChange={(checked) =>
                                        updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                          ...current,
                                          autoIndexingEnabled: checked,
                                        }))
                                      }
                                    />
                                    <Checkbox
                                      label="Create"
                                      checked={Boolean(draftPolicy.createTriggerEnabled)}
                                      disabled={!policy.enabled}
                                      onChange={(checked) =>
                                        updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                          ...current,
                                          createTriggerEnabled: checked,
                                        }))
                                      }
                                    />
                                    <Checkbox
                                      label="Delete"
                                      checked={Boolean(draftPolicy.deleteTriggerEnabled)}
                                      disabled={!policy.enabled}
                                      onChange={(checked) =>
                                        updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                          ...current,
                                          deleteTriggerEnabled: checked,
                                        }))
                                      }
                                    />
                                  </InlineStack>
                                  <Select
                                    label="Update trigger mode"
                                    options={UPDATE_TRIGGER_OPTIONS}
                                    value={draftPolicy.updateTriggerMode ?? 'NONE'}
                                    disabled={!policy.enabled}
                                    onChange={(value) =>
                                      updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                        ...current,
                                        updateTriggerMode: value,
                                        selectedIndexedFields:
                                          value === 'SELECTED_INDEXED_FIELDS' ? current.selectedIndexedFields ?? [] : [],
                                      }))
                                    }
                                  />
                                  {draftPolicy.updateTriggerMode === 'SELECTED_INDEXED_FIELDS' ? (
                                    <BlockStack gap="100">
                                      <Text as="p" variant="bodySm" tone="subdued">
                                        Selected indexed fields
                                      </Text>
                                      {selectableFields.length ? (
                                        <InlineStack gap="200">
                                          {selectableFields.map((field) => (
                                            <Checkbox
                                              key={field.fieldKey}
                                              label={field.label}
                                              checked={(draftPolicy.selectedIndexedFields ?? []).includes(field.fieldKey)}
                                              onChange={(checked) =>
                                                updatePolicyDraftSource(policy.sourceCategory, (current) => ({
                                                  ...current,
                                                  selectedIndexedFields: checked
                                                    ? Array.from(new Set([...(current.selectedIndexedFields ?? []), field.fieldKey]))
                                                    : (current.selectedIndexedFields ?? []).filter((value) => value !== field.fieldKey),
                                                }))
                                              }
                                            />
                                          ))}
                                        </InlineStack>
                                      ) : (
                                        <Text as="p" variant="bodySm" tone="subdued">
                                          No selectable indexed fields are available for this Shopify source family yet.
                                        </Text>
                                      )}
                                    </BlockStack>
                                  ) : null}
                                </BlockStack>
                              </Box>
                            )
                          })}
                          <InlineStack gap="200">
                            <Button
                              onClick={() => void handleSaveVectorizationPolicy()}
                              loading={busyAction === 'vectorization-policy-save'}
                              disabled={vectorizationBusy}
                            >
                              Save live update policy
                            </Button>
                          </InlineStack>
                        </BlockStack>
                      ) : null}

                      <BlockStack gap="150">
                        <Text as="h3" variant="headingSm">
                          Recent live update events
                        </Text>
                        {vectorizationSummary.recentEvents.length ? (
                          <BlockStack gap="150">
                            {vectorizationSummary.recentEvents.map((event) => (
                              <Box key={event.id} padding="200" borderWidth="025" borderColor="border" borderRadius="200">
                                <BlockStack gap="100">
                                  <Text as="p" variant="bodySm">
                                    {event.sourceCategory} · {event.operation ?? 'UNKNOWN'} · {event.status}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {event.entityType}
                                    {event.sourceObjectId ? ` · ${event.sourceObjectId}` : ''}
                                    {event.failureCode ? ` · ${event.failureCode}` : ''}
                                    {event.coalescedRunId ? ` · ${event.coalescedRunId}` : ''}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    Occurred {formatTimestamp(event.occurredAt)} · Last attempt {formatTimestamp(event.lastAttemptAt)}
                                  </Text>
                                  <InlineStack gap="200">
                                    <Button
                                      size="micro"
                                      onClick={() => void handleReplayVectorizationEvent(event.id)}
                                      loading={busyAction === 'vectorization-event-replay'}
                                      disabled={vectorizationBusy}
                                    >
                                      Replay event
                                    </Button>
                                  </InlineStack>
                                </BlockStack>
                              </Box>
                            ))}
                          </BlockStack>
                        ) : (
                          <Text as="p" variant="bodySm" tone="subdued">
                            No recent live update events have been recorded yet.
                          </Text>
                        )}
                      </BlockStack>
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      Vectorization details appear after the store is bootstrapped to a deployment.
                    </Text>
                  )}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'setup' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Storefront activation
                  </Text>
                  {storefrontPreview ? (
                    <BlockStack gap="200">
                      <InlineStack gap="200" align="start">
                        <Badge tone={storefrontPreview.ready ? 'success' : 'attention'}>
                          {storefrontPreview.ready ? 'Theme embed ready' : 'Blocked'}
                        </Badge>
                        <Badge tone={badgeTone(storefrontPreview.widgetStatus)}>{storefrontPreview.widgetStatus}</Badge>
                      </InlineStack>
                      <Text as="p" variant="bodyMd" tone="subdued">
                        {storefrontPreview.message}
                      </Text>
                      <List type="bullet">
                        <List.Item>Extension handle: {storefrontPreview.extensionHandle}</List.Item>
                        <List.Item>Bridge base URL: {storefrontPreview.bridgeBaseUrl ?? 'Not configured'}</List.Item>
                        <List.Item>Launcher label: {storefrontPreview.launcherLabelDefault}</List.Item>
                        <List.Item>Welcome message: {storefrontPreview.welcomeMessageDefault}</List.Item>
                        <List.Item>Shell profile: {store?.widgetDetail?.settings?.shellModeProfile ?? 'SHOPIFY_COMPANION'}</List.Item>
                        <List.Item>
                          Embedded surfaces:{' '}
                          {(store?.widgetDetail?.settings?.enabledSurfaces?.length
                            ? store.widgetDetail.settings.enabledSurfaces
                            : DEFAULT_WIDGET_SURFACES
                          ).join(', ')}
                        </List.Item>
                        <List.Item>Storefront base URL: {storefrontPreview.storefrontBaseUrl ?? '—'}</List.Item>
                        <List.Item>Merchant-placeable blocks: {storefrontSurfacePlacements.length}</List.Item>
                        <List.Item>
                          Grounding signals:{' '}
                          {storefrontPreview.groundingSignals.length ? storefrontPreview.groundingSignals.join(' · ') : 'Core catalog only'}
                        </List.Item>
                        <List.Item>
                          Review providers:{' '}
                          {storefrontPreview.supportedReviewProviders.length
                            ? storefrontPreview.supportedReviewProviders.join(' · ')
                            : 'Review-aware grounding is not enabled for this store'}
                        </List.Item>
                      </List>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Activation steps
                      </Text>
                      <List type="number">
                        {storefrontPreview.activationSteps.map((step) => (
                          <List.Item key={step}>{step}</List.Item>
                        ))}
                      </List>
                      {storefrontPreview.blockingReasons.length ? (
                        <Banner tone="warning">
                          <List type="bullet">
                            {storefrontPreview.blockingReasons.map((reason) => (
                              <List.Item key={reason}>{reason}</List.Item>
                            ))}
                          </List>
                        </Banner>
                      ) : null}
                      {storefrontSurfacePlacements.length ? (
                        <BlockStack gap="200">
                          <Text as="p" variant="bodySm" tone="subdued">
                            Suggested theme block placements
                          </Text>
                          {storefrontSurfacePlacements.map((placement) => {
                            const tierAllowed = billingAllowedSurfaces.includes(placement.surfaceId)
                            const widgetEnabled = widgetSettings.enabledSurfaces.includes(placement.surfaceId)
                            return (
                              <Card key={placement.blockHandle}>
                                <BlockStack gap="150">
                                  <InlineStack gap="200" align="space-between" blockAlign="center">
                                    <Text as="h3" variant="headingSm">
                                      {placement.label}
                                    </Text>
                                    <InlineStack gap="150">
                                      <Badge tone="info">{`${placement.requiredTierKey} tier`}</Badge>
                                      <Badge tone={tierAllowed ? 'success' : 'attention'}>
                                        {tierAllowed ? 'Tier allowed' : 'Requires higher tier'}
                                      </Badge>
                                      <Badge tone={widgetEnabled ? 'success' : 'attention'}>
                                        {widgetEnabled ? 'Enabled in widget settings' : 'Disabled in widget settings'}
                                      </Badge>
                                    </InlineStack>
                                  </InlineStack>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {placement.guidance}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {placement.template} template · {placement.target} · {placement.blockHandle} · {placement.requiredTierKey}
                                  </Text>
                                  <InlineStack gap="200">
                                    <Button
                                      url={placement.themeEditorUrl ?? undefined}
                                      target="_blank"
                                      disabled={!placement.themeEditorUrl || !tierAllowed}
                                    >
                                      Open placement in theme editor
                                    </Button>
                                  </InlineStack>
                                </BlockStack>
                              </Card>
                            )
                          })}
                        </BlockStack>
                      ) : null}
                      {storefrontPreview.themeEditorActivationUrl || storefrontPreview.storefrontBaseUrl ? (
                        <InlineStack gap="200">
                          {storefrontPreview.themeEditorActivationUrl ? (
                            <Button url={storefrontPreview.themeEditorActivationUrl} target="_blank" variant="primary">
                              Open theme editor
                            </Button>
                          ) : null}
                          {storefrontPreview.storefrontBaseUrl ? (
                            <Button url={storefrontPreview.storefrontBaseUrl} target="_blank">
                              Open storefront
                            </Button>
                          ) : null}
                        </InlineStack>
                      ) : null}
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      Storefront activation preview is unavailable until the merchant session resolves.
                    </Text>
                  )}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'partners' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <InlineStack gap="200" align="space-between" blockAlign="center">
                    <Text as="h2" variant="headingMd">
                      Partner access requests
                    </Text>
                    <Badge tone={partnerAccessRequests.some((request) => request.status === 'WAITING_ON_MERCHANT') ? 'attention' : 'success'}>
                      {`${partnerAccessRequests.filter((request) => request.status === 'WAITING_ON_MERCHANT').length} pending`}
                    </Badge>
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Review implementation support access for the installed Shopify store. Approval creates scoped partner visibility; denial keeps the store private.
                  </Text>
                  {partnerAccessError ? (
                    <Banner tone="critical">
                      Partner access requests could not be loaded: {partnerAccessError}
                    </Banner>
                  ) : null}
                  {partnerAccessRequests.length ? (
                    <BlockStack gap="300">
                      {partnerAccessRequests.map((request) => {
                        const waiting = request.status === 'WAITING_ON_MERCHANT'
                        const active = request.status === 'APPROVED' || request.status === 'ACTIVE'
                        return (
                          <Box key={request.requestId} padding="300" borderWidth="025" borderColor="border" borderRadius="200">
                            <BlockStack gap="200">
                              <InlineStack gap="200" align="space-between" blockAlign="center">
                                <BlockStack gap="050">
                                  <Text as="p" variant="bodyMd" fontWeight="semibold">
                                    {request.partnerName}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {request.clientName} · Merchant controls tier
                                  </Text>
                                </BlockStack>
                                <Badge tone={badgeTone(request.status)}>{request.status}</Badge>
                              </InlineStack>
                              <List type="bullet">
                                <List.Item>Requested scope: {request.requestedScope}</List.Item>
                                <List.Item>Store configured surfaces: {request.requestedSurfaces.join(' · ') || 'No configured surfaces'}</List.Item>
                                <List.Item>Requested: {formatTimestamp(request.createdAt)}</List.Item>
                                <List.Item>Expires: {formatTimestamp(request.expiresAt)}</List.Item>
                                {request.approvedAt ? <List.Item>Approved: {formatTimestamp(request.approvedAt)}</List.Item> : null}
                                {request.revokedAt ? <List.Item>Revoked: {formatTimestamp(request.revokedAt)}</List.Item> : null}
                              </List>
                              {request.notes ? (
                                <Text as="p" variant="bodySm" tone="subdued">
                                  {request.notes}
                                </Text>
                              ) : null}
                              {waiting ? (
                                <InlineStack gap="200">
                                  <Button
                                    variant="primary"
                                    onClick={() => void handlePartnerAccessApprove(request.requestId)}
                                    loading={busyAction === 'partner-access-approve'}
                                  >
                                    Approve
                                  </Button>
                                  <Button
                                    tone="critical"
                                    onClick={() => void handlePartnerAccessDeny(request.requestId)}
                                    loading={busyAction === 'partner-access-deny'}
                                  >
                                    Deny
                                  </Button>
                                </InlineStack>
                              ) : null}
                              {active ? (
                                <InlineStack gap="200">
                                  <Button
                                    tone="critical"
                                    onClick={() => void handlePartnerAccessRevoke(request.requestId)}
                                    loading={busyAction === 'partner-access-revoke'}
                                  >
                                    Revoke access
                                  </Button>
                                </InlineStack>
                              ) : null}
                            </BlockStack>
                          </Box>
                        )
                      })}
                    </BlockStack>
                  ) : !partnerAccessError ? (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      No partner access requests are waiting for this store.
                    </Text>
                  ) : null}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'insights' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Store intelligence health
                  </Text>
                  <InlineStack gap="200" align="start">
                    <Badge tone={intelligenceReadiness.tone}>{intelligenceReadiness.status}</Badge>
                    {intelligenceReadiness.freshnessLabel ? (
                      <Badge tone={intelligenceReadiness.freshnessTone}>{intelligenceReadiness.freshnessLabel}</Badge>
                    ) : null}
                    {intelligenceReadiness.liveUpdatesLabel ? (
                      <Badge tone={intelligenceReadiness.liveUpdatesTone}>{intelligenceReadiness.liveUpdatesLabel}</Badge>
                    ) : null}
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    {intelligenceReadiness.message}
                  </Text>
                  <List type="bullet">
                    <List.Item>
                      Shopper-ready surfaces: {intelligenceReadiness.enabledTierReadySurfaces}/{intelligenceReadiness.allowedTierSurfaces}
                    </List.Item>
                    <List.Item>Last successful sync: {formatTimestamp(intelligenceReadiness.lastSuccessfulSyncAt)}</List.Item>
                    <List.Item>Last webhook event: {formatTimestamp(store?.lastWebhookAt ?? null)}</List.Item>
                    <List.Item>Last successful live update: {formatTimestamp(intelligenceReadiness.lastSuccessfulAutoIndexAt)}</List.Item>
                    <List.Item>Last failed live update: {formatTimestamp(intelligenceReadiness.lastFailedAutoIndexAt)}</List.Item>
                    <List.Item>
                      Live update backlog: queued {vectorizationSummary?.automation?.queuedEvents ?? 0}, failed {vectorizationSummary?.automation?.failedEvents ?? 0},
                      {' '}dead-lettered {vectorizationSummary?.automation?.deadLetteredEvents ?? 0}
                    </List.Item>
                  </List>
                  {intelligenceReadiness.issues.length ? (
                    <Banner tone={intelligenceReadiness.tone === 'critical' ? 'critical' : 'warning'}>
                      <List type="bullet">
                        {intelligenceReadiness.issues.map((issue) => (
                          <List.Item key={issue}>{issue}</List.Item>
                        ))}
                      </List>
                    </Banner>
                  ) : null}
                  {shopperSurfaceUsage.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Shopper surface usage, last 7 days
                      </Text>
                      <List type="bullet">
                        {shopperSurfaceUsage.slice(0, 5).map((surface) => (
                          <List.Item key={surface.surfaceId}>
                            {surface.label} · {surface.count}
                          </List.Item>
                        ))}
                      </List>
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodySm" tone="subdued">
                      Shopper surface usage appears after real storefront queries run through the live bridge.
                    </Text>
                  )}
                  {shopperSurfaceJourneys.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Shopper journeys by surface, last 7 days
                      </Text>
                      {shopperSurfaceJourneys.slice(0, 4).map((journey) => (
                        <Box key={journey.surfaceId} padding="200" borderWidth="025" borderColor="border" borderRadius="200">
                          <BlockStack gap="100">
                            <Text as="p" variant="bodySm">
                              {journey.label}
                            </Text>
                            <Text as="p" variant="bodySm" tone="subdued">
                              {formatSurfaceJourneySummary(journey)}
                            </Text>
                          </BlockStack>
                        </Box>
                      ))}
                    </BlockStack>
                  ) : null}
                  {roiSummary ? (
                    <BlockStack gap="150">
                      <InlineStack gap="200" align="start">
                        <Text as="p" variant="bodySm" tone="subdued">
                          Merchant value evidence
                        </Text>
                        <Badge tone={roiTone(roiSummary.status)}>{formatRoiStatus(roiSummary.status)}</Badge>
                      </InlineStack>
                      <Text as="p" variant="bodySm" tone="subdued">
                        {roiSummary.message}
                      </Text>
                      <List type="bullet">
                        <List.Item>Shopper assist signals: {roiSummary.shopperAssistSignals}</List.Item>
                        <List.Item>Decision-support signals: {roiSummary.decisionSupportSignals}</List.Item>
                        <List.Item>Elite governed commerce completions: {roiSummary.governedActionCompletions}</List.Item>
                        <List.Item>Future Elite demand signals: {actionIntentQuestions.reduce((sum, question) => sum + question.count, 0)}</List.Item>
                        <List.Item>Active shopper surfaces: {roiSummary.activeSurfaceCount}</List.Item>
                        <List.Item>Strongest surfaces: {roiSummary.strongestSurfaceLabels.join(' · ') || 'None yet'}</List.Item>
                      </List>
                      {roiSummary.recommendations.length ? (
                        <Banner tone={roiTone(roiSummary.status) === 'success' ? 'success' : 'info'}>
                          <List type="bullet">
                            {roiSummary.recommendations.map((entry) => (
                              <List.Item key={entry}>{entry}</List.Item>
                            ))}
                          </List>
                        </Banner>
                      ) : null}
                    </BlockStack>
                  ) : null}
                  {sourceCoverageSignals.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Detected source depth
                      </Text>
                      {sourceCoverageSignals.map((entry) => (
                        <Text key={entry.category} as="p" variant="bodySm" tone="subdued">
                          {entry.label} · {entry.signals.join(' · ')}
                        </Text>
                      ))}
                    </BlockStack>
                  ) : null}
                  {topShopperQuestions.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Top shopper questions, last 7 days
                      </Text>
                      {topShopperQuestions.map((question) => (
                        <Text key={`${question.surfaceId}-${question.queryText}`} as="p" variant="bodySm" tone="subdued">
                          {question.label} · {question.queryText} · {question.count} · {formatTimestamp(question.lastAskedAt)}
                        </Text>
                      ))}
                    </BlockStack>
                  ) : null}
                  {unansweredShopperQuestions.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Unanswered/source-gap candidates, last 7 days
                      </Text>
                      {unansweredShopperQuestions.map((question) => (
                        <Text key={`gap-${question.surfaceId}-${question.queryText}`} as="p" variant="bodySm" tone="subdued">
                          {question.label} · {question.queryText} · {question.count} · {formatTimestamp(question.lastAskedAt)}
                        </Text>
                      ))}
                    </BlockStack>
                  ) : null}
                  {actionIntentQuestions.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Action-intent questions, last 7 days
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Treat these as future Elite demand signals. Starter remains read-only and hands off order-specific or account-specific cases.
                      </Text>
                      {actionIntentQuestions.map((question) => (
                        <Text key={`intent-${question.surfaceId}-${question.queryText}`} as="p" variant="bodySm" tone="subdued">
                          {question.label} · {question.queryText} · {question.count} · {formatTimestamp(question.lastAskedAt)}
                        </Text>
                      ))}
                    </BlockStack>
                  ) : null}
                  {billingSummary?.actionCapable && recentGovernedActions.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Recent governed commerce actions
                      </Text>
                      {recentGovernedActions.slice(0, 5).map((action) => (
                        <Text key={action.id} as="p" variant="bodySm" tone="subdued">
                          {action.actionType} · {action.surfaceId} · {action.productTitle ?? action.productHandle ?? 'current product'} ·
                          {' '}status {action.status} · shopper {action.shopperSessionRef ?? 'n/a'} · {formatTimestamp(action.createdAt)}
                        </Text>
                      ))}
                    </BlockStack>
                  ) : billingSummary?.actionCapable ? (
                    <Text as="p" variant="bodySm" tone="subdued">
                      Governed commerce actions will appear here after real Elite storefront actions run through the live bridge.
                    </Text>
                  ) : null}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'launch' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Go-live readiness
                  </Text>
                  <InlineStack gap="200" align="start">
                    <Badge tone={launchReadiness.tone}>{launchReadiness.status}</Badge>
                    <Badge tone={launchReadiness.productTone}>{launchReadiness.productLabel}</Badge>
                    <Badge tone={launchReadiness.commercialTone}>{launchReadiness.commercialLabel}</Badge>
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    {launchReadiness.message}
                  </Text>
                  <BlockStack gap="150">
                    {launchReadiness.items.map((item) => (
                      <Box key={item.label} padding="200" borderWidth="025" borderColor="border" borderRadius="200">
                        <BlockStack gap="100">
                          <InlineStack gap="200" align="space-between" blockAlign="center">
                            <Text as="p" variant="bodyMd" fontWeight="semibold">
                              {item.label}
                            </Text>
                            <Badge tone={item.tone}>{item.status}</Badge>
                          </InlineStack>
                          <Text as="p" variant="bodySm" tone="subdued">
                            {item.detail}
                          </Text>
                        </BlockStack>
                      </Box>
                    ))}
                  </BlockStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Launch packet and safe claims
                  </Text>
                  <InlineStack gap="200" align="start">
                    <Badge tone={launchPacket.tone}>{launchPacket.status}</Badge>
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    {launchPacket.message}
                  </Text>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Claim-safe product highlights
                    </Text>
                    <List type="bullet">
                      {launchPacket.safeClaims.map((claim) => (
                        <List.Item key={claim}>{claim}</List.Item>
                      ))}
                    </List>
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Commercial packaging
                    </Text>
                    <List type="bullet">
                      {launchPacket.commercialNotes.map((note) => (
                        <List.Item key={note}>{note}</List.Item>
                      ))}
                    </List>
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      App Review and launch notes
                    </Text>
                    <List type="bullet">
                      {launchPacket.reviewNotes.map((note) => (
                        <List.Item key={note}>{note}</List.Item>
                      ))}
                    </List>
                  </BlockStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'launch' || selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <InlineStack gap="200" align="space-between" blockAlign="center">
                    <Text as="h2" variant="headingMd">
                      {selectedSection === 'advanced' ? 'Go-live checklist and dossier' : 'Go-live checklist'}
                    </Text>
                    <Badge tone={goLiveChecklist.tone}>{goLiveChecklist.status}</Badge>
                  </InlineStack>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    {goLiveChecklist.message}
                  </Text>
                  <BlockStack gap="150">
                    {goLiveChecklist.items.map((item) => {
                      const action = item.action
                      return (
                        <Box key={item.label} padding="200" borderWidth="025" borderColor="border" borderRadius="200">
                          <BlockStack gap="150">
                            <InlineStack gap="200" align="space-between" blockAlign="center">
                              <Text as="p" variant="bodyMd" fontWeight="semibold">
                                {item.label}
                              </Text>
                              <Badge tone={item.tone}>{item.status}</Badge>
                            </InlineStack>
                            <Text as="p" variant="bodySm" tone="subdued">
                              {item.detail}
                            </Text>
                            {action ? (
                              <InlineStack gap="200">
                                {action.kind === 'open-url' && action.url ? (
                                  <Button url={action.url} target="_blank">
                                    {action.label}
                                  </Button>
                                ) : null}
                                {action.kind === 'run-go-live' ? (
                                  <Button variant="primary" onClick={() => void handleGoLive()} loading={busyAction === 'go-live'} disabled={!canGoLive}>
                                    {action.label}
                                  </Button>
                                ) : null}
                                {action.kind === 'run-sync' ? (
                                  <Button onClick={() => void handleSyncNow()} loading={busyAction === 'sync'} disabled={!canSyncNow}>
                                    {action.label}
                                  </Button>
                                ) : null}
                                {action.kind === 'run-reconcile' ? (
                                  <Button
                                    onClick={() => void handleVectorizationReconcile()}
                                    loading={busyAction === 'vectorization-reconcile'}
                                    disabled={!canReconcileVectorization}
                                  >
                                    {action.label}
                                  </Button>
                                ) : null}
                                {action.kind === 'activate-plan' ? (
                                  <Button
                                    variant="primary"
                                    onClick={() => void handleBillingApproval(action.tierKey)}
                                    loading={busyAction === 'billing-approval'}
                                  >
                                    {action.label}
                                  </Button>
                                ) : null}
                                {action.kind === 'copy-launch-dossier' ? (
                                  <Button onClick={() => void handleCopyLaunchDossier()}>
                                    {action.label}
                                  </Button>
                                ) : null}
                              </InlineStack>
                            ) : null}
                          </BlockStack>
                        </Box>
                      )
                    })}
                  </BlockStack>
                  {selectedSection === 'advanced' ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Export the full launch dossier only when support or App Review needs the current live-store evidence.
                      </Text>
                      <InlineStack gap="200">
                        <Button onClick={() => void handleCopyLaunchDossier()} disabled={!session}>
                          Copy launch dossier
                        </Button>
                        <Button onClick={handleDownloadLaunchDossier} disabled={!session}>
                          Download launch dossier
                        </Button>
                      </InlineStack>
                    </BlockStack>
                  ) : null}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    App Store and design-partner package
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Generate claim-safe App Store copy and a repeatable design-partner rollout packet from the current live store posture.
                  </Text>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      App Store listing package
                    </Text>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Claim-safe listing copy, screenshot plan, and disallowed claims generated from the current store posture.
                    </Text>
                    <InlineStack gap="200">
                      <Button onClick={() => void handleCopyAppStoreListingPackage()} disabled={!session}>
                        Copy App Store package
                      </Button>
                      <Button onClick={handleDownloadAppStoreListingPackage} disabled={!session}>
                        Download App Store package
                      </Button>
                    </InlineStack>
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Design-partner rollout packet
                    </Text>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Repeatable beta rollout steps and evidence checklist for partner stores.
                    </Text>
                    <InlineStack gap="200">
                      <Button onClick={() => void handleCopyDesignPartnerRollout()} disabled={!session}>
                        Copy design-partner packet
                      </Button>
                      <Button onClick={handleDownloadDesignPartnerRollout} disabled={!session}>
                        Download design-partner packet
                      </Button>
                    </InlineStack>
                  </BlockStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Webhook subscriptions
                  </Text>
                  {webhookSubscriptions ? (
                    <BlockStack gap="200">
                      <InlineStack gap="200" align="start">
                        <Badge tone={badgeTone(webhookSubscriptions.status)}>{webhookSubscriptions.status}</Badge>
                        <Badge tone={webhookSubscriptions.missingCount > 0 || webhookSubscriptions.driftedCount > 0 ? 'attention' : 'success'}>
                          {`${webhookSubscriptions.readyCount}/${webhookSubscriptions.expectedCount} ready`}
                        </Badge>
                      </InlineStack>
                      <Text as="p" variant="bodyMd" tone="subdued">
                        {webhookSubscriptions.message}
                      </Text>
                      <List type="bullet">
                        <List.Item>Webhook URI: {webhookSubscriptions.webhookUri ?? 'Not configured'}</List.Item>
                        <List.Item>Missing topics: {webhookSubscriptions.missingCount}</List.Item>
                        <List.Item>Drifted topics: {webhookSubscriptions.driftedCount}</List.Item>
                        <List.Item>Checked: {formatTimestamp(webhookSubscriptions.checkedAt)}</List.Item>
                      </List>
                      {webhookSubscriptions.topics.length ? (
                        <BlockStack gap="100">
                          {webhookSubscriptions.topics.map((topic) => (
                            <Text key={topic.topic} as="p" variant="bodySm" tone={topic.status === 'READY' ? 'subdued' : 'critical'}>
                              {topic.topic} · {topic.status}
                              {topic.subscriptionName ? ` · ${topic.subscriptionName}` : ''}
                              {topic.message ? ` · ${topic.message}` : ''}
                            </Text>
                          ))}
                        </BlockStack>
                      ) : null}
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      Webhook subscription diagnostics are unavailable until the merchant session resolves.
                    </Text>
                  )}
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Review and support playbooks
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Generate reviewer-safe and support-safe playbooks from the current live store posture so launch, App Review, and support handoff stay aligned with the shipped surface set.
                  </Text>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      App Review guide
                    </Text>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Reviewer-safe walkthrough, scope posture, and claims to avoid.
                    </Text>
                    <InlineStack gap="200">
                      <Button onClick={() => void handleCopyAppReviewGuide()} disabled={!session}>
                        Copy App Review guide
                      </Button>
                      <Button onClick={handleDownloadAppReviewGuide} disabled={!session}>
                        Download App Review guide
                      </Button>
                    </InlineStack>
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Review screencast script
                    </Text>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Short recording script for the current verified storefront story.
                    </Text>
                    <InlineStack gap="200">
                      <Button onClick={() => void handleCopyReviewScreencastScript()} disabled={!session}>
                        Copy screencast script
                      </Button>
                      <Button onClick={handleDownloadReviewScreencastScript} disabled={!session}>
                        Download screencast script
                      </Button>
                    </InlineStack>
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Support runbook
                    </Text>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Support-safe triage order and merchant handoff guidance.
                    </Text>
                    <InlineStack gap="200">
                      <Button onClick={() => void handleCopySupportRunbook()} disabled={!session}>
                        Copy support runbook
                      </Button>
                      <Button onClick={handleDownloadSupportRunbook} disabled={!session}>
                        Download support runbook
                      </Button>
                    </InlineStack>
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Lifecycle and subscription packet
                    </Text>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Install, subscription, scope, and support lifecycle evidence.
                    </Text>
                    <InlineStack gap="200">
                      <Button onClick={() => void handleCopyLifecycleSubscriptionPacket()} disabled={!session}>
                        Copy lifecycle packet
                      </Button>
                      <Button onClick={handleDownloadLifecycleSubscriptionPacket} disabled={!session}>
                        Download lifecycle packet
                      </Button>
                    </InlineStack>
                  </BlockStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Diagnostics and support bundle
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    This is the bounded merchant-facing diagnostic view. It exposes store readiness and deployment capability status without exposing secret material.
                  </Text>
                  {store ? (
                    <List type="bullet">
                      <List.Item>Deployment health: {store.deploymentStatus ?? 'UNBOUND'}</List.Item>
                      <List.Item>Sync status: {store.syncDetail?.status ?? store.syncStatus}</List.Item>
                      <List.Item>Last successful sync: {formatTimestamp(store.lastSyncAt)}</List.Item>
                      <List.Item>Widget status: {store.widgetDetail?.status ?? store.widgetStatus}</List.Item>
                      <List.Item>Billing mode: {billingSummary?.mode ?? 'UNKNOWN'}</List.Item>
                      <List.Item>Billing tier: {billingSummary?.tierKey ?? 'UNKNOWN'}</List.Item>
                      <List.Item>Billing status: {billingSummary?.status ?? 'UNKNOWN'}</List.Item>
                      <List.Item>Tier surfaces: {billingAllowedSurfaces.join(' · ') || '—'}</List.Item>
                      <List.Item>Product cap: {billingSummary?.catalogProductCap ?? 'unlimited'}</List.Item>
                      <List.Item>Webhook subscriptions: {webhookSubscriptions?.status ?? 'UNKNOWN'}</List.Item>
                      <List.Item>Actions: {store.capabilities?.actionCount ?? 0}</List.Item>
                      <List.Item>Knowledge sources: {store.capabilities?.knowledgeSourceCount ?? 0}</List.Item>
                      <List.Item>Datasets: {store.capabilities?.marketplaceDatasetCount ?? 0}</List.Item>
                      <List.Item>Shell modules: {store.capabilities?.shellModuleCount ?? 0}</List.Item>
                    </List>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      Support diagnostics appear after the current merchant session resolves and the store is connected.
                    </Text>
                  )}
                  {store?.capabilities ? (
                    <Text as="p" variant="bodySm" tone="subdued">
                      Actions {store.capabilities.actionNames.join(' · ') || '—'} · Knowledge {store.capabilities.knowledgeSourceIds.join(' · ') || '—'}
                    </Text>
                  ) : null}
                  {billingSummary ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone={billingSummary.launchBlocked ? 'critical' : 'subdued'}>
                        Billing {billingSummary.mode} / {billingSummary.tierKey} / {billingSummary.status} · {billingSummary.message}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Surfaces {billingAllowedSurfaces.join(' · ')} · Product cap {billingSummary.catalogProductCap ?? 'unlimited'} ·
                        {' '}Sync {billingSummary.syncCadence ?? 'platform default'} · Badge {billingSummary.poweredByBadgeRequired ? 'required' : 'optional'}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Governance {billingSummary.requiresExplicitConfirmation ? 'explicit confirmation required' : 'read-only'} ·
                        {' '}Audit {billingSummary.auditTrailAvailable ? 'available' : 'not applicable'} ·
                        {' '}Action packages {billingSummary.actionPackages.length ? billingSummary.actionPackages.join(' · ') : '—'}
                      </Text>
                      {billingSummary.availablePlans?.length ? (
                        <BlockStack gap="150">
                          <Text as="p" variant="bodySm" tone="subdued">
                            Tier ladder
                          </Text>
                          {billingSummary.availablePlans.map((plan) => {
                            const canActivate = !plan.active && plan.merchantApprovalSupported && plan.commerciallyAvailable
                            return (
                              <Card key={plan.tierKey}>
                                <BlockStack gap="150">
                                  <InlineStack gap="200" align="space-between" blockAlign="center">
                                    <Text as="h3" variant="headingSm">
                                      {plan.planName}
                                    </Text>
                                    <InlineStack gap="150">
                                      <Badge tone={plan.active ? 'success' : 'info'}>
                                        {plan.active ? 'Current tier' : plan.tierKey}
                                      </Badge>
                                      <Badge tone={plan.chatFallbackEnabled ? 'success' : 'attention'}>
                                        {plan.chatFallbackEnabled ? 'Chat fallback' : 'Embedded-only'}
                                      </Badge>
                                    </InlineStack>
                                  </InlineStack>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {formatPlanPrice(plan)} · Surfaces {plan.allowedSurfaces.join(' · ') || '—'} · Product cap{' '}
                                    {plan.catalogProductCap ?? 'unlimited'} · Sync {plan.syncCadence ?? 'platform default'}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {plan.actionCapable ? 'Read + governed actions' : 'Read-only shopper intelligence'} · Badge{' '}
                                    {plan.poweredByBadgeRequired ? 'required' : 'optional'}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    Governance {plan.requiresExplicitConfirmation ? 'explicit confirmation required' : 'read-only'} ·
                                    {' '}Audit {plan.auditTrailAvailable ? 'available' : 'not applicable'} ·
                                    {' '}Action packages {plan.actionPackages.length ? plan.actionPackages.join(' · ') : '—'}
                                  </Text>
                                  <Text as="p" variant="bodySm" tone="subdued">
                                    {plan.message}
                                  </Text>
                                  {canActivate ? (
                                    <InlineStack gap="200">
                                      <Button
                                        variant="primary"
                                        onClick={() => void handleBillingApproval(plan.tierKey)}
                                        loading={busyAction === 'billing-approval'}
                                      >
                                        Activate {plan.planName}
                                      </Button>
                                    </InlineStack>
                                  ) : null}
                                </BlockStack>
                              </Card>
                            )
                          })}
                        </BlockStack>
                      ) : null}
                      {billingApprovalRequired ? (
                        <InlineStack gap="200">
                          {billingPaidPlanOptions.map((plan) => (
                            <Button
                              key={plan.tierKey}
                              variant="primary"
                              onClick={() => void handleBillingApproval(plan.tierKey)}
                              loading={busyAction === 'billing-approval'}
                              disabled={!plan.merchantApprovalSupported || !plan.commerciallyAvailable}
                            >
                              Activate {plan.planName}
                            </Button>
                          ))}
                        </InlineStack>
                      ) : null}
                    </BlockStack>
                  ) : null}
                  {webhookSubscriptions ? (
                    <Text as="p" variant="bodySm" tone={webhookSubscriptions.status === 'READY' ? 'subdued' : 'critical'}>
                      Webhooks {webhookSubscriptions.status} · ready {webhookSubscriptions.readyCount}/{webhookSubscriptions.expectedCount} ·
                      {' '}{webhookSubscriptions.message}
                    </Text>
                  ) : null}
                  {usageSummary ? (
                    <BlockStack gap="100">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Usage today {usageSummary.totalToday} · last 7 days {usageSummary.totalLast7Days} · last activity {formatTimestamp(usageSummary.lastActivityAt)}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Today {formatUsageBreakdown(usageSummary.todayBreakdown)}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Last 7 days {formatUsageBreakdown(usageSummary.last7DayBreakdown)}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Shopper surfaces {formatSurfaceUsageBreakdown(usageSummary.last7DaySurfaceUsage)}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Shopper journeys {(usageSummary.last7DaySurfaceJourneys ?? []).slice(0, 3).map((entry) => `${entry.label}: ${formatSurfaceJourneySummary(entry)}`).join(' | ') || 'No surface journeys yet'}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        ROI posture {formatRoiStatus(usageSummary.roiSummary.status)} · {formatRoiSummary(usageSummary.roiSummary)}
                      </Text>
                    </BlockStack>
                  ) : null}
                  <Text as="p" variant="bodySm" tone="subdued">
                    Export the raw support bundle only for engineering/support escalation. It includes IDs and operational state.
                  </Text>
                  <InlineStack gap="200">
                    <Button onClick={() => void handleCopySupportBundle()} disabled={!session}>
                      Copy support bundle
                    </Button>
                    <Button onClick={handleDownloadSupportBundle} disabled={!session}>
                      Download support bundle
                    </Button>
                  </InlineStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}
          </InlineStack>

          <InlineStack gap="400" blockAlign="start" align="start">
            {selectedSection === 'setup' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Widget settings
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Companion owns bounded launcher content, shell persona, and the embedded intelligence surfaces that appear before chat fallback.
                  </Text>
                  <TextField
                    label="Launcher label"
                    autoComplete="off"
                    value={widgetSettings.launcherLabel}
                    maxLength={60}
                    onChange={(value) => setWidgetSettings((current) => ({ ...current, launcherLabel: value }))}
                  />
                  <TextField
                    label="Welcome message"
                    autoComplete="off"
                    multiline={4}
                    value={widgetSettings.welcomeMessage}
                    maxLength={320}
                    onChange={(value) => setWidgetSettings((current) => ({ ...current, welcomeMessage: value }))}
                    helpText="This becomes the first assistant message when the storefront launcher opens."
                  />
                  <Select
                    label="Shell profile"
                    options={SHELL_MODE_PROFILE_OPTIONS}
                    value={widgetSettings.shellModeProfile}
                    onChange={(value) => setWidgetSettings((current) => ({ ...current, shellModeProfile: value }))}
                    helpText={
                      billingSummary?.chatFallbackEnabled === false
                        ? 'Current tier is embedded-surface only. Chat fallback is disabled until a paid tier is active.'
                        : 'This controls how the storefront surfaces phrase prompts and shopper guidance.'
                    }
                    disabled={billingSummary?.chatFallbackEnabled === false}
                  />
                  <Select
                    label="Default mode"
                    options={CONVERSATION_MODE_OPTIONS}
                    value={widgetSettings.defaultConversationMode}
                    onChange={(value) =>
                      setWidgetSettings((current) => ({
                        ...current,
                        defaultConversationMode: value,
                        allowedConversationModes: normalizeAllowedConversationModes(
                          current.allowedConversationModes,
                          value
                        ),
                      }))
                    }
                    helpText="This is the default Max widget mode before page-specific routing or shopper selection changes it."
                  />
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Intentional advanced modes
                    </Text>
                    {CONVERSATION_MODE_OPTIONS.map((mode) => (
                      <Checkbox
                        key={mode.value}
                        label={mode.label}
                        checked={widgetSettings.allowedConversationModes.includes(mode.value)}
                        disabled={mode.value === widgetSettings.defaultConversationMode}
                        onChange={(checked) =>
                          setWidgetSettings((current) => {
                            const nextAllowed = checked
                              ? normalizeAllowedConversationModes(
                                  [...current.allowedConversationModes, mode.value],
                                  current.defaultConversationMode
                                )
                              : current.allowedConversationModes.filter((value) => value !== mode.value)
                            return {
                              ...current,
                              allowedConversationModes: nextAllowed,
                              pageModeMappings: normalizePageModeMappings(current.pageModeMappings, nextAllowed),
                            }
                          })
                        }
                      />
                    ))}
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Page-aware mode routing
                    </Text>
                    {PAGE_MODE_OPTIONS.map((pageOption) => (
                      <Select
                        key={pageOption.value}
                        label={pageOption.label}
                        options={[
                          { label: 'Use default mode', value: '' },
                          ...CONVERSATION_MODE_OPTIONS.filter((mode) =>
                            widgetSettings.allowedConversationModes.includes(mode.value)
                          ),
                        ]}
                        value={widgetSettings.pageModeMappings[pageOption.value] ?? ''}
                        onChange={(value) =>
                          setWidgetSettings((current) => {
                            const nextMappings = { ...current.pageModeMappings }
                            if (!value) {
                              delete nextMappings[pageOption.value]
                            } else {
                              nextMappings[pageOption.value] = value
                            }
                            return {
                              ...current,
                              pageModeMappings: nextMappings,
                            }
                          })
                        }
                        helpText={pageOption.helpText}
                      />
                    ))}
                  </BlockStack>
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Embedded surfaces · current tier allows {billingAllowedSurfaces.join(' · ')}
                    </Text>
                    {WIDGET_SURFACE_OPTIONS.map((surface) => (
                      <Checkbox
                        key={surface.value}
                        label={surface.label}
                        checked={widgetSettings.enabledSurfaces.includes(surface.value)}
                        disabled={!billingAllowedSurfaces.includes(surface.value)}
                        onChange={(checked) =>
                          setWidgetSettings((current) => ({
                            ...current,
                            enabledSurfaces: checked
                              ? [...current.enabledSurfaces, surface.value].filter(
                                  (value, index, values) => values.indexOf(value) === index,
                                )
                              : current.enabledSurfaces.filter((value) => value !== surface.value),
                          }))
                        }
                      />
                    ))}
                  </BlockStack>
                  {storefrontSurfacePlacements.length ? (
                    <BlockStack gap="150">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Placement guidance
                      </Text>
                      {storefrontSurfacePlacements.map((placement) => {
                        const tierAllowed = billingAllowedSurfaces.includes(placement.surfaceId)
                        const widgetEnabled = widgetSettings.enabledSurfaces.includes(placement.surfaceId)
                        return (
                          <Text
                            key={placement.blockHandle}
                            as="p"
                            variant="bodySm"
                            tone={tierAllowed && widgetEnabled ? 'subdued' : 'critical'}
                          >
                            {placement.label} · {placement.requiredTierKey} tier · {tierAllowed ? 'tier ready' : 'upgrade required'} ·{' '}
                            {widgetEnabled ? 'enabled' : 'disabled'} · {placement.template}/{placement.target}
                          </Text>
                        )
                      })}
                    </BlockStack>
                  ) : null}
                  <InlineStack gap="200">
                    <Button
                      onClick={() => void handleWidgetSettingsSave()}
                      loading={busyWidgetSettings}
                      disabled={!session || installRecoveryRequired || !widgetSettingsDirty}
                    >
                      Save widget settings
                    </Button>
                  </InlineStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'support' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Support handoff profile
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Configure the real merchant handoff path used when Companion reaches the read-only boundary for refunds, edits, account changes, or unsupported order cases.
                  </Text>
                  <Text as="p" variant="bodySm" tone={supportReadiness?.merchantHandoffConfigured ? 'success' : 'critical'}>
                    {supportReadiness?.merchantHandoffMessage ??
                      'No merchant support handoff has been configured yet.'}
                  </Text>
                  {supportReadiness?.nextActions?.length ? (
                    <Text as="p" variant="bodySm" tone="subdued">
                      Next actions: {supportReadiness.nextActions.join(' | ')}
                    </Text>
                  ) : null}
                  <TextField
                    label="Support email"
                    autoComplete="off"
                    value={supportProfileSettings.contactEmail}
                    onChange={(value) => setSupportProfileSettings((current) => ({ ...current, contactEmail: value }))}
                    helpText="Used for merchant handoff when Companion reaches the read-only support boundary."
                  />
                  <TextField
                    label="Contact URL"
                    autoComplete="off"
                    value={supportProfileSettings.contactUrl}
                    onChange={(value) => setSupportProfileSettings((current) => ({ ...current, contactUrl: value }))}
                    helpText="Use a full URL or a storefront-relative path like /pages/contact."
                  />
                  <TextField
                    label="Help center URL"
                    autoComplete="off"
                    value={supportProfileSettings.helpCenterUrl}
                    onChange={(value) => setSupportProfileSettings((current) => ({ ...current, helpCenterUrl: value }))}
                  />
                  {orderLookupTierAllowed ? (
                    <TextField
                      label="Elite order lookup page URL"
                      autoComplete="off"
                      value={supportProfileSettings.orderLookupPageUrl}
                      onChange={(value) => setSupportProfileSettings((current) => ({ ...current, orderLookupPageUrl: value }))}
                      helpText="Set the support or contact page where the governed Elite order lookup block lives."
                    />
                  ) : null}
                  <TextField
                    label="Support policy note"
                    autoComplete="off"
                    multiline={4}
                    value={supportProfileSettings.supportPolicyNote}
                    maxLength={600}
                    onChange={(value) => setSupportProfileSettings((current) => ({ ...current, supportPolicyNote: value }))}
                    helpText="This note feeds the support packet and keeps merchant handoff language aligned with the live store posture."
                  />
                  {supportReadiness?.activeSubscriptions?.length ? (
                    <BlockStack gap="100">
                      <Text as="p" variant="bodySm" tone="subdued">
                        Active subscriptions
                      </Text>
                      {supportReadiness.activeSubscriptions.map((subscription) => (
                        <Text key={subscription.subscriptionId ?? `${subscription.name}-${subscription.status}`} as="p" variant="bodySm" tone="subdued">
                          {(subscription.name ?? 'Unnamed subscription')} · {subscription.status} · {subscription.tierKey}
                          {subscription.active ? ' · active' : ''}
                        </Text>
                      ))}
                    </BlockStack>
                  ) : null}
                  <InlineStack gap="200">
                    <Button
                      onClick={() => void handleSupportProfileSave()}
                      loading={busySupportProfile}
                      disabled={!session || installRecoveryRequired || !supportProfileDirty}
                    >
                      Save support profile
                    </Button>
                  </InlineStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'insights' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Merchant playground
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Test the live Companion behavior from the embedded app using the same store knowledge as the storefront widget.
                  </Text>
                  {!store?.readiness?.storefrontReady ? (
                    <Banner tone="warning">
                      Complete publish/apply/verify and storefront readiness before using the merchant playground.
                    </Banner>
                  ) : null}
                  <Box
                    padding="300"
                    borderColor="border"
                    borderWidth="025"
                    borderRadius="200"
                    background="bg-surface-secondary"
                  >
                    <BlockStack gap="200">
                      {playgroundMessages.map((message, index) => (
                        <Box
                          key={`${message.role}-${index}`}
                          padding="200"
                          borderRadius="200"
                          background={message.role === 'assistant' ? 'bg-surface' : 'bg-fill-brand-selected'}
                        >
                          <BlockStack gap="200">
                            <Text as="p" variant="bodyMd">
                              <strong>{message.role === 'assistant' ? 'Assistant' : 'You'}:</strong> {message.content}
                            </Text>
                            {message.role === 'assistant' && message.products?.length ? (
                              <BlockStack gap="150">
                                <Text as="p" variant="bodySm" tone="subdued">
                                  Matched products
                                </Text>
                                {message.products.map((product) => (
                                  <Box
                                    key={`${product.title}-${product.url ?? product.detail ?? 'product'}`}
                                    padding="200"
                                    borderRadius="200"
                                    background="bg-surface-secondary"
                                  >
                                    <BlockStack gap="100">
                                      <Text as="p" variant="bodyMd" fontWeight="semibold">
                                        {product.title}
                                      </Text>
                                      {product.subtitle ? (
                                        <Text as="p" variant="bodySm" tone="subdued">
                                          {product.subtitle}
                                        </Text>
                                      ) : null}
                                      {product.detail ? (
                                        <Text as="p" variant="bodySm" tone="subdued">
                                          {product.detail}
                                        </Text>
                                      ) : null}
                                      {product.url ? (
                                        <Link url={product.url} target="_blank" removeUnderline>
                                          Open product
                                        </Link>
                                      ) : null}
                                    </BlockStack>
                                  </Box>
                                ))}
                              </BlockStack>
                            ) : null}
                            {message.role === 'assistant' && message.sources?.length ? (
                              <BlockStack gap="150">
                                <Text as="p" variant="bodySm" tone="subdued">
                                  Grounding sources
                                </Text>
                                {message.sources.map((source) => (
                                  <Box
                                    key={`${source.label}-${source.url ?? source.excerpt ?? 'source'}`}
                                    padding="200"
                                    borderRadius="200"
                                    background="bg-surface-secondary"
                                  >
                                    <BlockStack gap="100">
                                      <Text as="p" variant="bodyMd" fontWeight="semibold">
                                        {source.label}
                                      </Text>
                                      {source.excerpt ? (
                                        <Text as="p" variant="bodySm" tone="subdued">
                                          {source.excerpt}
                                        </Text>
                                      ) : null}
                                      {source.url ? (
                                        <Link url={source.url} target="_blank" removeUnderline>
                                          Open source
                                        </Link>
                                      ) : null}
                                    </BlockStack>
                                  </Box>
                                ))}
                              </BlockStack>
                            ) : null}
                          </BlockStack>
                        </Box>
                      ))}
                    </BlockStack>
                  </Box>
                  {playgroundSuggestions.length ? (
                    <InlineStack gap="200" wrap>
                      {playgroundSuggestions.map((suggestion) => (
                        <Button
                          key={suggestion}
                          onClick={() => void handlePlaygroundSend(suggestion)}
                          disabled={playgroundLoading || !store?.readiness?.storefrontReady}
                        >
                          {suggestion}
                        </Button>
                      ))}
                    </InlineStack>
                  ) : null}
                  <TextField
                    label="Playground prompt"
                    autoComplete="off"
                    multiline={3}
                    value={playgroundInput}
                    onChange={setPlaygroundInput}
                    placeholder="Ask about products, collections, shipping, or policies"
                  />
                  <InlineStack gap="200">
                    <Button
                      variant="primary"
                      onClick={() => void handlePlaygroundSend(playgroundInput)}
                      loading={playgroundLoading}
                      disabled={!store?.readiness?.storefrontReady || !playgroundInput.trim()}
                    >
                      Send prompt
                    </Button>
                    <Button onClick={() => void handlePlaygroundReset()} disabled={playgroundLoading || !store}>
                      Reset playground
                    </Button>
                  </InlineStack>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Onboarding sequence
                  </Text>
                  <List type="number">
                    {(shell?.onboardingPhases ?? []).map((phase) => (
                      <List.Item key={phase}>{phase}</List.Item>
                    ))}
                  </List>
                </BlockStack>
              </Card>
            </Box>
            ) : null}

            {selectedSection === 'advanced' ? (
            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Launch capabilities
                  </Text>
                  <List type="bullet">
                    {(shell?.launchCapabilities ?? []).map((capability) => (
                      <List.Item key={capability}>{capability}</List.Item>
                    ))}
                  </List>
                </BlockStack>
              </Card>
            </Box>
            ) : null}
          </InlineStack>

          {selectedSection === 'home' || selectedSection === 'launch' ? (
          <Card>
            <BlockStack gap="300">
              <Text as="h2" variant="headingMd">
                Next actions
              </Text>
              <Text as="p" variant="bodyMd" tone="subdued">
                Connect the current shop first. Check Shopify source reachability before bootstrapping. After bootstrap, use Knowledge Sync to refresh enabled Shopify content. Go live only after the source readiness checks are clean.
              </Text>
              {installRecoveryRequired ? (
                <Banner tone="warning">
                  <BlockStack gap="200">
                    <Text as="p" variant="bodyMd">
                      Shopify marked this installation as uninstalled. Re-run the Shopify install flow before using connect, preflight, sync, or go-live actions.
                    </Text>
                    {installRecoveryUrl ? (
                      <InlineStack gap="200">
                        <Button url={installRecoveryUrl} target="_top" variant="primary">
                          Reconnect Shopify app
                        </Button>
                      </InlineStack>
                    ) : null}
                  </BlockStack>
                </Banner>
              ) : null}
              {!installRecoveryRequired && scopeGrantRequired ? (
                <Banner tone="warning">
                  <BlockStack gap="200">
                    <Text as="p" variant="bodyMd">
                      {supportReadiness?.message ?? 'Approve Shopify order-read scope before enabling Elite order lookup.'}
                    </Text>
                    {scopeGrantUrl ? (
                      <InlineStack gap="200">
                        <Button url={scopeGrantUrl} target="_top" variant="primary">
                          Approve order-read scope
                        </Button>
                      </InlineStack>
                    ) : null}
                  </BlockStack>
                </Banner>
              ) : null}
              {billingLaunchBlocked ? (
                <Banner tone="critical">
                  <BlockStack gap="200">
                    <Text as="p" variant="bodyMd">
                      {billingSummary?.message ?? 'Billing setup is incomplete. Shopify Companion go-live is blocked until billing is configured.'}
                    </Text>
                    {billingApprovalRequired && billingPaidPlanOptions.length > 0 ? (
                      <InlineStack gap="200">
                        {billingPaidPlanOptions.map((plan) => (
                          <Button
                            key={plan.tierKey}
                            variant="primary"
                            onClick={() => void handleBillingApproval(plan.tierKey)}
                            loading={busyAction === 'billing-approval'}
                            disabled={!plan.merchantApprovalSupported || !plan.commerciallyAvailable}
                          >
                            Activate {plan.planName}
                          </Button>
                        ))}
                      </InlineStack>
                    ) : null}
                  </BlockStack>
                </Banner>
              ) : null}
              {store?.readiness ? (
                <List type="bullet">
                  <List.Item>Readiness: {store.readiness.overallStatus}</List.Item>
                  <List.Item>Go-live eligible: {store.readiness.goLiveEligible ? 'yes' : 'no'}</List.Item>
                  <List.Item>Storefront ready: {store.readiness.storefrontReady ? 'yes' : 'no'}</List.Item>
                </List>
              ) : null}
              <Divider />
              {store?.latestRelease ? (
                <Text as="p" variant="bodySm" tone="subdued">
                  Latest release {store.latestRelease.status} / verification {store.latestRelease.verificationStatus}. The page will auto-refresh while go-live is still running.
                </Text>
              ) : null}
              {store?.readiness?.nextActions?.length ? (
                <Banner tone="info">
                  <List type="bullet">
                    {store.readiness.nextActions.map((action) => (
                      <List.Item key={action}>{action}</List.Item>
                    ))}
                  </List>
                </Banner>
              ) : null}
              <InlineStack gap="300" align="start">
                <Button
                  variant="primary"
                  onClick={() => void handleConnect()}
                  loading={busyAction === 'connect'}
                  disabled={!session || installRecoveryRequired}
                >
                  Connect current shop
                </Button>
                <Button
                  onClick={() => void handleSourcePreflight()}
                  loading={busyAction === 'preflight'}
                  disabled={!session || installRecoveryRequired}
                >
                  Run source preflight
                </Button>
                <Button
                  onClick={() => void handleBootstrap()}
                  loading={busyAction === 'bootstrap'}
                  disabled={!session || installRecoveryRequired}
                >
                  Bootstrap deployment
                </Button>
                <Button
                  onClick={() => void handleSyncNow()}
                  loading={busyAction === 'sync'}
                  disabled={!canSyncNow}
                >
                  Sync now
                </Button>
                <Button
                  onClick={() => void handleGoLive()}
                  loading={busyAction === 'go-live'}
                  disabled={!canGoLive}
                >
                  Publish and apply
                </Button>
              </InlineStack>
            </BlockStack>
          </Card>
          ) : null}
        </BlockStack>
      </Page>
    </AppProvider>
  )
}

function StoreSummary({ store }: { store: ShopifyBridgeStoreSummary }) {
  return (
    <BlockStack gap="200">
      <InlineStack align="space-between">
        <Text as="p" variant="bodyMd">
          {store.displayName}
        </Text>
        <Badge tone={badgeTone(store.onboardingStatus)}>{store.onboardingStatus}</Badge>
      </InlineStack>
      {store.readiness ? (
        <InlineStack gap="200" align="start">
          <Badge tone={badgeTone(store.readiness.overallStatus)}>{store.readiness.overallStatus}</Badge>
          <Badge tone={store.readiness.goLiveEligible ? 'success' : 'attention'}>{`Go-live ${store.readiness.goLiveEligible ? 'ready' : 'blocked'}`}</Badge>
          <Badge tone={store.readiness.storefrontReady ? 'success' : 'attention'}>{`Storefront ${store.readiness.storefrontReady ? 'ready' : 'not ready'}`}</Badge>
        </InlineStack>
      ) : null}
      <List type="bullet">
        <List.Item>Install: {store.installStatus}</List.Item>
        <List.Item>Data sync: {store.syncStatus}</List.Item>
        <List.Item>Source readiness: {store.sourceReadinessStatus}</List.Item>
        <List.Item>Widget: {store.widgetStatus}</List.Item>
        <List.Item>Last successful sync: {formatTimestamp(store.lastSyncAt)}</List.Item>
        <List.Item>Last Shopify update: {formatTimestamp(store.lastWebhookAt)}</List.Item>
      </List>
      {store.sourcePreflight ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Source check {store.sourcePreflight.overallStatus} ·{' '}
          {store.sourcePreflight.categories
            .filter((category) => category.enabled)
            .map((category) => {
              return `${category.category} ${category.status.toLowerCase()} (${category.itemCount})`
            })
            .join(' · ')}
        </Text>
      ) : null}
      {store.widgetDetail?.settings ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Launcher “{store.widgetDetail.settings.launcherLabel ?? 'Ask the store assistant'}” · welcome message{' '}
          {store.widgetDetail.settings.welcomeMessage ?? 'Store assistant is ready. Ask about products, policies, or collections.'} · profile{' '}
          {store.widgetDetail.settings.shellModeProfile ?? 'SHOPIFY_COMPANION'} · surfaces{' '}
          {(store.widgetDetail.settings.enabledSurfaces?.length
            ? store.widgetDetail.settings.enabledSurfaces
            : DEFAULT_WIDGET_SURFACES
          ).join(', ')}
        </Text>
      ) : null}
      {store.syncDetail ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Synced documents: {store.syncDetail.documentCount}
          {store.syncDetail.message ? ` · ${store.syncDetail.message}` : ''}
        </Text>
      ) : null}
      {store.readiness?.goLiveBlockingReasons?.length ? (
        <Banner tone="warning">
          <List type="bullet">
            {store.readiness.goLiveBlockingReasons.map((reason) => (
              <List.Item key={reason}>{reason}</List.Item>
            ))}
          </List>
        </Banner>
      ) : null}
      {store.readiness?.storefrontBlockingReasons?.length ? (
        <Banner tone="critical">
          <List type="bullet">
            {store.readiness.storefrontBlockingReasons.map((reason) => (
              <List.Item key={reason}>{reason}</List.Item>
            ))}
          </List>
        </Banner>
      ) : null}
      {store.readiness?.nextActions?.length ? (
        <Banner tone="info">
          <List type="bullet">
            {store.readiness.nextActions.map((action) => (
              <List.Item key={action}>{action}</List.Item>
            ))}
          </List>
        </Banner>
      ) : null}
      <Text as="p" variant="bodySm" tone="subdued">
        Sources: products {store.productsEnabled ? 'on' : 'off'}, collections {store.collectionsEnabled ? 'on' : 'off'}, pages {store.pagesEnabled ? 'on' : 'off'}, policies {store.policiesEnabled ? 'on' : 'off'}, articles {store.articlesEnabled ? 'on' : 'off'}, metaobjects {store.metaobjectsEnabled ? 'on' : 'off'}
      </Text>
    </BlockStack>
  )
}

function extractAssistantMessage(payload: unknown): string {
  if (!payload || typeof payload !== 'object') {
    return 'I could not process that request.'
  }
  const result = (payload as { result?: { sanitizedPayload?: { message?: unknown }; message?: unknown } }).result
  if (typeof result?.sanitizedPayload?.message === 'string' && result.sanitizedPayload.message.trim()) {
    return result.sanitizedPayload.message.trim()
  }
  if (typeof result?.message === 'string' && result.message.trim()) {
    return result.message.trim()
  }
  const response = (payload as { response?: unknown }).response
  if (typeof response === 'string' && response.trim()) {
    return response.trim()
  }
  const message = (payload as { message?: unknown }).message
  if (typeof message === 'string' && message.trim()) {
    return message.trim()
  }
  return 'I processed your request.'
}

function extractConversationId(payload: unknown): string | null {
  if (!payload || typeof payload !== 'object') {
    return null
  }
  const value = (payload as { conversationId?: unknown }).conversationId
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function extractSuggestions(payload: unknown): string[] {
  if (!payload || typeof payload !== 'object') {
    return []
  }
  const rootSuggestions = (payload as { suggestions?: unknown }).suggestions
  const nestedSuggestions = (payload as { result?: { sanitizedPayload?: { suggestions?: unknown } } }).result?.sanitizedPayload?.suggestions
  const candidates = Array.isArray(rootSuggestions) ? rootSuggestions : Array.isArray(nestedSuggestions) ? nestedSuggestions : []
  return candidates
    .map((value) => {
      if (typeof value === 'string') {
        return value.trim()
      }
      if (value && typeof value === 'object' && 'text' in value && typeof (value as { text?: unknown }).text === 'string') {
        return (value as { text: string }).text.trim()
      }
      if (value && typeof value === 'object' && 'label' in value && typeof (value as { label?: unknown }).label === 'string') {
        return (value as { label: string }).label.trim()
      }
      return ''
    })
    .filter((value, index, all) => value && all.indexOf(value) === index)
    .slice(0, 4)
}

function extractProductCards(payload: unknown): PlaygroundProductCard[] {
  const candidates =
    firstArray(
      readPath(payload, 'result', 'sanitizedPayload', 'products'),
      readPath(payload, 'result', 'products'),
      readPath(payload, 'products'),
      readPath(payload, 'result', 'sanitizedPayload', 'items')
    ) ?? []
  return candidates.map(normalizeProductCard).filter((value): value is PlaygroundProductCard => value != null).slice(0, 4)
}

function extractSourceCards(payload: unknown): PlaygroundSourceCard[] {
  const candidates =
    firstArray(
      readPath(payload, 'result', 'sanitizedPayload', 'sources'),
      readPath(payload, 'result', 'sources'),
      readPath(payload, 'sources')
    ) ?? []
  return candidates.map(normalizeSourceCard).filter((value): value is PlaygroundSourceCard => value != null).slice(0, 4)
}

function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString()
}

function formatSupportChannels(supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null): string {
  const supportProfile = supportReadiness?.supportProfile
  if (!supportProfile) {
    return 'No merchant handoff configured'
  }
  const values = [
    supportProfile.contactEmail ? `email ${supportProfile.contactEmail}` : null,
    supportProfile.contactUrl ? `contact ${supportProfile.contactUrl}` : null,
    supportProfile.helpCenterUrl ? `help ${supportProfile.helpCenterUrl}` : null,
    supportProfile.orderLookupPageUrl ? `order lookup ${supportProfile.orderLookupPageUrl}` : null,
  ].filter(Boolean)
  return values.length ? values.join(' · ') : 'No merchant handoff configured'
}

function formatSupportSubscriptions(supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null): string {
  const subscriptions = supportReadiness?.activeSubscriptions ?? []
  if (!subscriptions.length) {
    return 'None detected'
  }
  return subscriptions
    .map((subscription) =>
      [(subscription.name ?? 'Unnamed subscription'), subscription.status, subscription.tierKey, subscription.active ? 'active' : null]
        .filter(Boolean)
        .join(' · ')
    )
    .join(' | ')
}

function surfaceLabelFor(surfaceId: string): string {
  return WIDGET_SURFACE_OPTIONS.find((surface) => surface.value === surfaceId)?.label ?? surfaceId
}

function starterSurfaceLabels(): string[] {
  return STARTER_SURFACE_IDS.map(surfaceLabelFor)
}

function buildSupportBundle(
  shell: ShopifyBridgeShellResponse | null,
  session: ShopifyBridgeMerchantSessionResponse | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null
): string {
  const store = session?.store ?? null
  const policyGroundingAvailable = Boolean(
    store?.policiesEnabled ||
      (storefrontPreview?.groundingSignals ?? []).includes('Policy grounding'),
  )
  const orderLookupClaimReady = Boolean(
    billingSummary?.allowedSurfaces?.includes('order-lookup') && supportReadiness?.orderLookupSupported
  )
  return JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      app: shell
        ? {
            appName: shell.appName,
            serviceRef: shell.serviceRef,
            environmentScope: shell.environmentScope,
            status: shell.status,
            merchantSessionAuthConfigured: shell.merchantSessionAuthConfigured,
          }
        : null,
      merchantSession: session
        ? {
            shopDomain: session.shopDomain,
            userId: session.userId,
            expiresAt: session.expiresAt,
            installRecoveryRequired: session.installRecoveryRequired,
            installRecoveryMessage: session.installRecoveryMessage,
            installRecoveryUrl: session.installRecoveryUrl,
            installRecordStatus: session.installRecord?.status ?? null,
            accessTokenConfigured: Boolean(session.installRecord?.accessTokenSecretRef),
            refreshTokenConfigured: Boolean(session.installRecord?.refreshTokenSecretRef),
            scopesText: session.installRecord?.scopesText ?? null,
            lastAuthenticatedAt: session.installRecord?.lastAuthenticatedAt ?? null,
            lastUninstalledAt: session.installRecord?.lastUninstalledAt ?? null,
          }
        : null,
      store: store
        ? {
            shopDomain: store.shopDomain,
            displayName: store.displayName,
            deploymentId: store.deploymentId,
            deploymentName: store.deploymentName,
            deploymentStatus: store.deploymentStatus,
            consumerId: store.consumerId,
            installStatus: store.installStatus,
            onboardingStatus: store.onboardingStatus,
            syncStatus: store.syncStatus,
            sourceReadinessStatus: store.sourceReadinessStatus,
            widgetStatus: store.widgetStatus,
            documentCount: store.syncDetail?.documentCount ?? 0,
            lastSourcePreflightAt: store.sourcePreflight?.checkedAt ?? null,
            lastSyncAt: store.syncDetail?.checkedAt ?? store.lastSyncAt,
            lastWebhookAt: store.webhookDetail?.receivedAt ?? store.lastWebhookAt,
            sourceCategories: {
              productsEnabled: store.productsEnabled,
              collectionsEnabled: store.collectionsEnabled,
              pagesEnabled: store.pagesEnabled,
              policiesEnabled: store.policiesEnabled,
              articlesEnabled: store.articlesEnabled,
              metaobjectsEnabled: store.metaobjectsEnabled,
            },
            capabilities: store.capabilities,
            readiness: store.readiness,
            latestVersion: store.latestVersion,
            latestRelease: store.latestRelease,
          }
        : null,
      billingSummary,
      webhookSubscriptions,
      vectorizationSummary,
      storefrontPreview: storefrontPreview
        ? {
            ready: storefrontPreview.ready,
            widgetStatus: storefrontPreview.widgetStatus,
            onboardingStatus: storefrontPreview.onboardingStatus,
            extensionHandle: storefrontPreview.extensionHandle,
            storefrontBaseUrl: storefrontPreview.storefrontBaseUrl,
            bridgeBaseUrl: storefrontPreview.bridgeBaseUrl,
            themeEditorActivationUrl: storefrontPreview.themeEditorActivationUrl,
            groundingSignals: storefrontPreview.groundingSignals,
            supportedReviewProviders: storefrontPreview.supportedReviewProviders,
            surfacePlacements: storefrontPreview.surfacePlacements,
            activationSteps: storefrontPreview.activationSteps,
            blockingReasons: storefrontPreview.blockingReasons,
          }
        : null,
      storeIntelligenceReadiness: buildStoreIntelligenceReadiness(
        store,
        storefrontPreview,
        billingSummary,
        webhookSubscriptions,
        vectorizationSummary,
        store?.widgetDetail?.settings ?? null
      ),
      launchReadiness: buildLaunchReadiness(
        store,
        storefrontPreview,
        billingSummary,
        webhookSubscriptions,
        vectorizationSummary,
        usageSummary,
        store?.widgetDetail?.settings ?? null,
        supportReadiness
      ),
      goLiveChecklist: buildGoLiveChecklist(
        session,
        store,
        storefrontPreview,
        billingSummary,
        webhookSubscriptions,
        vectorizationSummary,
        store?.widgetDetail?.settings ?? null,
        supportReadiness
      ),
      launchPacket: buildLaunchPacket(
        store,
        storefrontPreview,
        billingSummary,
        usageSummary,
        store?.widgetDetail?.settings ?? null,
        supportReadiness
      ),
      supportGuidance: {
        customerSafeOrderLookupSupported: orderLookupClaimReady,
        policyGroundingAvailable,
        returnGuidanceMode: policyGroundingAvailable ? 'POLICY_GROUNDED_ONLY' : 'HANDOFF_ONLY',
        orderSpecificPostPurchaseMode: orderLookupClaimReady ? 'LOOKUP_PLUS_HANDOFF' : 'MERCHANT_HANDOFF_REQUIRED',
        boundedHandoffText: orderLookupClaimReady
          ? `${supportReadiness?.message ?? 'Customer-safe order lookup is verified for this store.'} Refunds, cancellations, address changes, and account-specific actions still require merchant support handoff.`
          : policyGroundingAvailable
            ? 'Use published policy grounding for general return and refund guidance, but hand off order-specific decisions, tracking, cancellations, and account changes to the merchant support channel.'
            : 'Do not answer return, refund, tracking, or order-status questions as if the assistant has order access. Hand off those cases to the merchant support channel.',
        lifecycleStage: supportReadiness?.lifecycleStage ?? 'UNKNOWN',
        merchantHandoffConfigured: Boolean(supportReadiness?.merchantHandoffConfigured),
        merchantHandoffMessage: supportReadiness?.merchantHandoffMessage ?? null,
        merchantHandoffChannels: formatSupportChannels(supportReadiness),
        merchantSupportProfile: supportReadiness?.supportProfile ?? null,
        nextActions: supportReadiness?.nextActions ?? [],
        activeSubscriptions: supportReadiness?.activeSubscriptions ?? [],
      },
      reviewPackage: {
        appReviewGuideAvailable: true,
        reviewScreencastScriptAvailable: true,
        supportRunbookAvailable: true,
      },
      supportReadiness,
      usageSummary,
    },
    null,
    2
  )
}

function redirectTopLevel(url: string) {
  if (window.top && window.top !== window) {
    window.top.location.assign(url)
    return
  }
  window.location.assign(url)
}

function hasBillingReturnQueryParam(): boolean {
  return new URLSearchParams(window.location.search).get('billing') === 'return'
}

function clearBillingReturnQueryParam() {
  const url = new URL(window.location.href)
  url.searchParams.delete('billing')
  window.history.replaceState({}, document.title, url.toString())
}

function describeBillingReturn(billingSummary: ShopifyBridgeBillingSummary | null): string {
  if (!billingSummary) {
    return 'Returned from Shopify billing approval. Refresh the page if the store billing status does not update.'
  }
  if (!billingSummary.launchBlocked && billingSummary.status === 'ACTIVE') {
    return 'Shopify billing is active for this store. You can continue with go-live.'
  }
  return `Returned from Shopify billing approval, but billing is still ${billingSummary.status ?? 'UNKNOWN'}. ${billingSummary.message ?? 'Review the billing section before continuing.'}`
}

function formatUsageBreakdown(entries: Array<{ eventType: string; count: number }>): string {
  if (!entries.length) {
    return 'No events recorded'
  }
  return entries.map((entry) => `${describeUsageEvent(entry.eventType)} ${entry.count}`).join(' · ')
}

function formatSurfaceUsageBreakdown(entries: Array<{ surfaceId: string; label: string; count: number }>): string {
  if (!entries.length) {
    return 'No shopper surface queries yet'
  }
  return entries.map((entry) => `${entry.label} ${entry.count}`).join(' · ')
}

function formatSurfaceJourneySummary(entry: ShopifyBridgeUsageSummary['last7DaySurfaceJourneys'][number]): string {
  const parts = [`Questions ${entry.shopperQuestions}`, `Interactions ${entry.shopperInteractions}`, `Read actions ${entry.readActions}`]
  if (entry.governedActionGrants || entry.governedActionCompletions || entry.governedActionFailures) {
    parts.push(
      `Grants ${entry.governedActionGrants}`,
      `Completed ${entry.governedActionCompletions}`,
      `Failed ${entry.governedActionFailures}`,
    )
  }
  return parts.join(' · ')
}

function formatRoiStatus(status: string): string {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIONABLE':
      return 'Actionable'
    case 'PROVING_VALUE':
      return 'Proving value'
    case 'EARLY_SIGNAL':
      return 'Early signal'
    case 'NO_SIGNAL':
      return 'No signal'
    default:
      return status || 'Unknown'
  }
}

function roiTone(status: string): 'success' | 'attention' | 'critical' {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIONABLE':
    case 'PROVING_VALUE':
      return 'success'
    case 'EARLY_SIGNAL':
      return 'attention'
    case 'NO_SIGNAL':
      return 'critical'
    default:
      return 'attention'
  }
}

function formatRoiSummary(summary: ShopifyBridgeUsageSummary['roiSummary']): string {
  const parts = [
    `assist ${summary.shopperAssistSignals}`,
    `decision ${summary.decisionSupportSignals}`,
    `Elite completions ${summary.governedActionCompletions}`,
    `surfaces ${summary.activeSurfaceCount}`,
  ]
  if (summary.strongestSurfaceLabels.length) {
    parts.push(`top ${summary.strongestSurfaceLabels.join(', ')}`)
  }
  return parts.join(' · ')
}

function formatPlanPrice(plan: ShopifyBridgeBillingSummary['availablePlans'][number]): string {
  if (!plan.amount || !plan.currencyCode || !plan.interval) {
    return plan.tierKey === 'FREE' ? 'Free' : 'Pricing unavailable'
  }
  return `${plan.amount} ${plan.currencyCode} / ${plan.interval}`
}

type WidgetSettingsSnapshot = {
  launcherLabel?: string | null
  welcomeMessage?: string | null
  shellModeProfile?: string | null
  defaultConversationMode?: string | null
  allowedConversationModes?: string[]
  pageModeMappings?: Record<string, string>
  enabledSurfaces?: string[]
}

function describeUsageEvent(eventType: string): string {
  const normalized = (eventType || '').trim().toUpperCase()
  switch (normalized) {
    case 'MERCHANT_CONNECT':
      return 'merchant connect'
    case 'MERCHANT_SOURCE_PREFLIGHT':
      return 'source reachability checks'
    case 'MERCHANT_BOOTSTRAP':
      return 'merchant bootstrap'
    case 'MERCHANT_GO_LIVE':
      return 'merchant go-live'
    case 'MERCHANT_SYNC_NOW':
      return 'Knowledge Sync runs'
    case 'MERCHANT_WIDGET_SETTINGS_UPDATED':
      return 'widget settings updates'
    case 'MERCHANT_SOURCE_SETTINGS_UPDATED':
      return 'source setting updates'
    case 'MERCHANT_VECTORIZATION_INDEX_ALL':
      return 'Knowledge Sync all-source requests'
    case 'MERCHANT_VECTORIZATION_REINDEX_ALL':
      return 'Knowledge Sync refresh-all requests'
    case 'MERCHANT_VECTORIZATION_REINDEX_SELECTED':
      return 'Knowledge Sync selected-source requests'
    case 'MERCHANT_VECTORIZATION_POLICY_UPDATED':
      return 'live update policy saves'
    case 'MERCHANT_VECTORIZATION_EVENT_REPLAYED':
      return 'live update event replays'
    case 'MERCHANT_VECTORIZATION_AUTO_RETRY':
      return 'failed live update retries'
    case 'MERCHANT_PLAYGROUND_QUERY':
      return 'merchant playground queries'
    case 'MERCHANT_PLAYGROUND_SUGGESTIONS':
      return 'merchant playground suggestions'
    case 'STOREFRONT_BOOTSTRAP':
      return 'storefront bootstraps'
    case 'STOREFRONT_QUERY':
      return 'shopper queries'
    case 'STOREFRONT_SUGGESTIONS':
      return 'shopper suggestions'
    case 'STOREFRONT_SUGGESTION_CLICKED':
      return 'shopper suggestion clicks'
    case 'STOREFRONT_CHAT_RESET':
      return 'shopper chat resets'
    case 'STOREFRONT_WIDGET_OPENED_PRODUCT_PAGE':
      return 'widget opens on product pages'
    case 'STOREFRONT_WIDGET_OPENED_COLLECTION_PAGE':
      return 'widget opens on collection pages'
    case 'STOREFRONT_WIDGET_OPENED_HOME_PAGE':
      return 'widget opens on home page'
    case 'STOREFRONT_WIDGET_OPENED_CONTENT_PAGE':
      return 'widget opens on content pages'
    case 'STOREFRONT_WIDGET_OPENED_GENERIC_PAGE':
      return 'widget opens on other pages'
    default:
      return normalized.toLowerCase().replace(/_/g, ' ')
  }
}

function buildStoreIntelligenceReadiness(
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  widgetSettings: WidgetSettingsSnapshot | null
): {
  status: string
  tone: 'success' | 'attention' | 'critical'
  message: string
  freshnessLabel: string | null
  freshnessTone: 'success' | 'attention' | 'critical'
  liveUpdatesLabel: string | null
  liveUpdatesTone: 'success' | 'attention' | 'critical'
  allowedTierSurfaces: number
  enabledTierReadySurfaces: number
  lastSuccessfulSyncAt: string | null
  lastSuccessfulAutoIndexAt: string | null
  lastFailedAutoIndexAt: string | null
  issues: string[]
} {
  const issues: string[] = []
  if (!store) {
    return {
      status: 'Not connected',
      tone: 'attention',
      message: 'Connect the store before Companion can prove storefront readiness, sync health, or live update health.',
      freshnessLabel: null,
      freshnessTone: 'attention',
      liveUpdatesLabel: null,
      liveUpdatesTone: 'attention',
      allowedTierSurfaces: 0,
      enabledTierReadySurfaces: 0,
      lastSuccessfulSyncAt: null,
      lastSuccessfulAutoIndexAt: null,
      lastFailedAutoIndexAt: null,
      issues: ['Store is not connected to the platform yet.'],
    }
  }

  if (billingSummary?.launchBlocked) {
    issues.push(`Billing is blocking launch: ${billingSummary.message}`)
  }
  if (!storefrontPreview?.ready) {
    issues.push('Theme activation is still blocked or incomplete.')
  }
  if (webhookSubscriptions && webhookSubscriptions.status !== 'READY') {
    issues.push(`Webhook subscriptions are ${webhookSubscriptions.status.toLowerCase()}.`)
  }
  if (vectorizationSummary && !vectorizationSummary.readyToRun) {
    issues.push('Knowledge Sync still needs refresh before enabled Shopify content can stay current.')
  }
  if (vectorizationSummary?.automation && !vectorizationSummary.automation.autoIndexingHealthy) {
    issues.push('Live updates are degraded and need attention before relying on freshness.')
  }
  if (store.syncDetail && store.syncDetail.status !== 'SYNCED') {
    issues.push(`Store sync is ${store.syncDetail.status.toLowerCase()}.`)
  }

  const allowedTierSurfaces = billingSummary?.allowedSurfaces?.length
    ? billingSummary.allowedSurfaces.length
    : DEFAULT_WIDGET_SURFACES.length
  const enabledSurfaceValues = widgetSettings?.enabledSurfaces ?? DEFAULT_WIDGET_SURFACES
  const enabledTierReadySurfaces = enabledSurfaceValues.filter((surfaceId: string) =>
    (billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES).includes(surfaceId)
  ).length

  const freshnessBlocked = Boolean(store.syncDetail && store.syncDetail.status !== 'SYNCED') ||
    Boolean(vectorizationSummary?.syncState && !['CURRENT', 'READY', 'IN_SYNC'].includes(vectorizationSummary.syncState))
  const liveUpdatesHealthy = vectorizationSummary?.automation?.autoIndexingHealthy !== false

  return {
    status: issues.length ? (issues.some((issue) => issue.startsWith('Billing')) ? 'Blocked' : 'Needs attention') : 'Healthy',
    tone: issues.length ? (issues.some((issue) => issue.startsWith('Billing')) ? 'critical' : 'attention') : 'success',
    message: issues.length
      ? 'Companion is installed, but one or more shipping gates still need work before the product is fully legible and fresh for shoppers.'
      : 'Storefront surfaces, sync, billing, and live updates are aligned closely enough to treat the store as shopper-ready.',
    freshnessLabel: freshnessBlocked ? 'Freshness needs attention' : 'Freshness healthy',
    freshnessTone: freshnessBlocked ? 'attention' : 'success',
    liveUpdatesLabel: liveUpdatesHealthy ? 'Live updates healthy' : 'Live updates degraded',
    liveUpdatesTone: liveUpdatesHealthy ? 'success' : 'critical',
    allowedTierSurfaces,
    enabledTierReadySurfaces,
    lastSuccessfulSyncAt: store.syncDetail?.checkedAt ?? store.lastSyncAt,
    lastSuccessfulAutoIndexAt: vectorizationSummary?.automation?.lastSuccessfulAutoIndexAt ?? null,
    lastFailedAutoIndexAt: vectorizationSummary?.automation?.lastFailedAutoIndexAt ?? null,
    issues,
  }
}

function buildLaunchReadiness(
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  widgetSettings: WidgetSettingsSnapshot | null,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null
): {
  status: string
  tone: 'success' | 'attention' | 'critical'
  message: string
  productLabel: string
  productTone: 'success' | 'attention' | 'critical'
  commercialLabel: string
  commercialTone: 'success' | 'attention' | 'critical'
  items: Array<{
    label: string
    status: string
    tone: 'success' | 'attention' | 'critical'
    detail: string
  }>
} {
  const configuredSurfaces = widgetSettings?.enabledSurfaces?.length ? widgetSettings.enabledSurfaces : DEFAULT_WIDGET_SURFACES
  const placementIds = new Set((storefrontPreview?.surfacePlacements ?? []).map((placement) => placement.surfaceId))
  const requiredProductSurfaces = WIDGET_SURFACE_OPTIONS
    .filter((surface) => surface.value !== 'order-lookup')
    .map((surface) => surface.value)
  const productSurfaceReady = requiredProductSurfaces.every((surfaceId) => placementIds.has(surfaceId))
  const supportSurfaceReady = placementIds.has('order-lookup')
  const tierKeys = new Set((billingSummary?.availablePlans ?? []).map((plan) => plan.tierKey))
  const starterPlan = billingSummary?.availablePlans?.find((plan) => plan.tierKey === 'STARTER') ?? null
  const elitePlan = billingSummary?.availablePlans?.find((plan) => plan.tierKey === 'ELITE') ?? null
  const starterCommercialReady = Boolean(starterPlan?.commerciallyAvailable)
  const tierLadderReady = tierKeys.has('FREE') && tierKeys.has('STARTER') && tierKeys.has('ELITE') && starterCommercialReady
  const eliteGovernanceReady = Boolean(
    elitePlan?.actionCapable &&
      elitePlan?.requiresExplicitConfirmation &&
      elitePlan?.auditTrailAvailable &&
      (elitePlan?.actionPackages?.length ?? 0) > 0
  )
  const shopperSignalsReady = Boolean(
    (usageSummary?.last7DaySurfaceUsage?.length ?? 0) > 0 ||
      (usageSummary?.topQuestionsLast7Days?.length ?? 0) > 0 ||
      (usageSummary?.last7DaySurfaceJourneys?.length ?? 0) > 0,
  )
  const webhookReady = !webhookSubscriptions || webhookSubscriptions.status === 'READY'
  const storefrontReady = Boolean(storefrontPreview?.ready)
  const goLiveReady = Boolean(store?.readiness?.goLiveEligible)
  const syncReady = !store?.syncDetail || store.syncDetail.status === 'SYNCED'
  const liveUpdatesReady = !vectorizationSummary?.automation || vectorizationSummary.automation.autoIndexingHealthy !== false
  const productTierReady = configuredSurfaces
    .filter((surfaceId) => requiredProductSurfaces.includes(surfaceId))
    .every((surfaceId) => (billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES).includes(surfaceId))
  const orderLookupTierAllowed = Boolean(billingSummary?.allowedSurfaces?.includes('order-lookup'))
  const orderLookupReady = Boolean(
    orderLookupTierAllowed &&
      supportReadiness?.orderLookupSupported &&
      supportReadiness.orderLookupScopeGranted &&
      supportReadiness.appScopesUpdateWebhookReady &&
      supportSurfaceReady
  )
  const launchGateReady = storefrontReady && goLiveReady && webhookReady && syncReady && liveUpdatesReady
  const orderLookupReadinessItem: {
    label: string
    status: string
    tone: 'success' | 'attention' | 'critical'
    detail: string
  } | null = orderLookupTierAllowed ? {
    label: 'Elite customer-safe order lookup',
    status: orderLookupReady
      ? 'Ready'
      : supportReadiness?.status === 'PENDING_SCOPE_GRANT'
        ? 'Waiting for scope'
        : 'Needs attention',
    tone: orderLookupReady
      ? 'success'
      : supportReadiness?.status === 'INSTALL_RECOVERY_REQUIRED'
        ? 'critical'
        : 'attention',
    detail: orderLookupReady
      ? 'A merchant-placeable order lookup block is available with exact order number plus checkout email verification.'
      : supportReadiness?.message ?? 'Order lookup still needs scope, install, or support-surface work before it is launch-safe.',
  } : null

  const items: Array<{
    label: string
    status: string
    tone: 'success' | 'attention' | 'critical'
    detail: string
  }> = [
    {
      label: 'Embedded product surface set',
      status: productSurfaceReady ? 'Ready' : 'Incomplete',
      tone: productSurfaceReady ? 'success' : 'attention',
      detail: productSurfaceReady
        ? 'AI search, contextual pill, product insight, policy strip, product FAQ, and comparison all have merchant-placeable theme blocks.'
        : 'One or more required Companion surfaces are still missing from the storefront placement contract.',
    },
    {
      label: 'Commercial tier ladder',
      status: tierLadderReady ? 'Ready' : 'Needs attention',
      tone: tierLadderReady ? 'success' : 'attention',
      detail: tierLadderReady
        ? eliteGovernanceReady
          ? `Free, Starter, and Elite are visible to merchants, and Elite is packaged as the governed action tier for ${elitePlan?.actionPackages?.join(' and ') ?? 'merchant-approved actions'}.`
          : 'Free, Starter, and Elite are visible to merchants. Starter is commercially available, while Elite action claims stay gated until the live contract is verified.'
        : 'The merchant tier ladder is not fully legible yet or Starter is not commercially available in the live billing contract.',
    },
    {
      label: 'Launch gate',
      status: launchGateReady ? 'Ready' : 'Blocked',
      tone: launchGateReady ? 'success' : 'critical',
      detail: launchGateReady
        ? 'Storefront activation, go-live posture, sync, webhooks, and live updates are aligned for a clean launch story.'
        : 'One or more operational launch gates are still not clean enough for launch, App Review, or merchant-safe support claims.',
    },
    {
      label: 'Review signal ingestion',
      status: store?.productsEnabled ? 'Supported' : 'Not enabled',
      tone: store?.productsEnabled ? 'success' : 'attention',
      detail: store?.productsEnabled
        ? 'Companion now ingests Judge.me-compatible review and rating metafields from Shopify products when they are present.'
        : 'Product ingestion is disabled, so review and rating signals cannot flow into Companion yet.',
    },
    ...(orderLookupReadinessItem ? [orderLookupReadinessItem] : []),
    {
      label: 'Merchant legibility',
      status: shopperSignalsReady ? 'Observed' : 'Awaiting traffic',
      tone: shopperSignalsReady ? 'success' : 'attention',
      detail: shopperSignalsReady
        ? 'Real shopper surface usage and top-question summaries are available for merchant support and launch QA.'
        : 'The merchant app is ready to surface shopper analytics, but real storefront traffic has not produced enough signal yet.',
    },
    {
      label: 'Tier-to-surface alignment',
      status: productTierReady ? 'Aligned' : 'Needs attention',
      tone: productTierReady ? 'success' : 'attention',
      detail: productTierReady
        ? 'Configured storefront surfaces fit inside the current billing tier posture.'
        : 'The widget configuration currently asks for surfaces outside the active tier allowance.',
    },
  ]

  const hasCritical = items.some((item) => item.tone === 'critical')
  const hasAttention = items.some((item) => item.tone === 'attention')
  return {
    status: hasCritical ? 'Blocked' : hasAttention ? 'Needs attention' : 'Ready',
    tone: hasCritical ? 'critical' : hasAttention ? 'attention' : 'success',
    message: hasCritical
      ? 'Companion is close, but the launch story still has at least one operational blocker.'
      : hasAttention
        ? 'Companion is materially launchable, but a few productization details still need tightening.'
        : 'Companion now looks coherent enough to present as a real Shopify product, not just a technical integration.',
    productLabel: productSurfaceReady ? 'Product shape ready' : 'Product shape incomplete',
    productTone: productSurfaceReady ? 'success' : 'attention',
    commercialLabel: tierLadderReady ? 'Tier ladder ready' : 'Tier ladder incomplete',
    commercialTone: tierLadderReady ? 'success' : 'attention',
    items,
  }
}

function buildLaunchPacket(
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  widgetSettings: WidgetSettingsSnapshot | null,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null
): {
  status: string
  tone: 'success' | 'attention' | 'critical'
  message: string
  safeClaims: string[]
  commercialNotes: string[]
  reviewNotes: string[]
} {
  const configuredSurfaces = widgetSettings?.enabledSurfaces?.length ? widgetSettings.enabledSurfaces : DEFAULT_WIDGET_SURFACES
  const tierSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const activeSurfaceIds = configuredSurfaces.filter((surfaceId) => tierSurfaces.includes(surfaceId))
  const activeSurfaceLabels = activeSurfaceIds
    .map(surfaceLabelFor)
  const reviewProviders = buildDetectedReviewProviders(store)
  const groundingSignals = storefrontPreview?.groundingSignals ?? []
  const actionIntentCount = (usageSummary?.actionIntentQuestionsLast7Days ?? [])
    .reduce((sum, question) => sum + question.count, 0)
  const unansweredCount = (usageSummary?.unansweredQuestionsLast7Days ?? [])
    .reduce((sum, question) => sum + question.count, 0)
  const hasEliteGovernance = Boolean(
    billingSummary?.actionCapable &&
      billingSummary.requiresExplicitConfirmation &&
      billingSummary.auditTrailAvailable &&
      billingSummary.actionPackages.length,
  )
  const starterSurfaceReady = STARTER_SURFACE_IDS.every((surfaceId) => activeSurfaceIds.includes(surfaceId))
  const orderLookupClaimReady = Boolean(activeSurfaceIds.includes('order-lookup') && supportReadiness?.orderLookupSupported)

  const safeClaims: string[] = []
  if (activeSurfaceIds.includes('ai-search')) {
    safeClaims.push('Free-tier shoppers can use a real AI search block without opening the launcher shell.')
  }
  if (orderLookupClaimReady) {
    safeClaims.push('Customer-safe order lookup is available with the exact order number and checkout email, and stays read-only inside the bridge.')
  }
  if (
    activeSurfaceIds.includes('contextual-pill') &&
    activeSurfaceIds.includes('product-insight') &&
    activeSurfaceIds.includes('policy-strip') &&
    activeSurfaceIds.includes('product-faq') &&
    activeSurfaceIds.includes('comparison')
  ) {
    safeClaims.push('Starter can be described honestly as embedded product intelligence across insight, policy, FAQ, and comparison surfaces.')
  } else if (activeSurfaceLabels.length) {
    safeClaims.push(`Current storefront surfaces are ${activeSurfaceLabels.join(', ')}.`)
  }
  if (groundingSignals.includes('Published article grounding') || groundingSignals.includes('Metaobject grounding')) {
    safeClaims.push('Answers can draw on published articles and structured metaobject content when those sources are enabled.')
  }
  if (reviewProviders.length) {
    safeClaims.push(`Review-aware grounding supports ${reviewProviders.join(', ')} compatible Shopify metafields.`)
  }
  if (hasEliteGovernance) {
    safeClaims.push('Elite guided commerce is governed with shopper confirmation, signed bridge grants, and audit history.')
  }
  if (usageSummary && (actionIntentCount > 0 || unansweredCount > 0)) {
    safeClaims.push(`Merchant analytics expose ${unansweredCount} source-gap candidate signals and ${actionIntentCount} action-intent signals without making Starter an action tier.`)
  }
  if (!safeClaims.length) {
    safeClaims.push('Core catalog and policy grounding are live, but the surface set still needs more packaging before broad launch claims.')
  }

  const commercialNotes = [
    'Free: AI search only, powered-by posture enforced when required.',
    'Starter: full read-only store intelligence with embedded shopper guidance surfaces.',
    hasEliteGovernance
      ? `Elite: governed action packaging for ${billingSummary?.actionPackages.join(' and ') ?? 'bounded commerce'} is technically real.`
      : 'Elite: do not market governed actions beyond what the live billing contract currently exposes.',
  ]

  const reviewNotes = [
    storefrontPreview?.ready
      ? 'Theme extension, placement guidance, and storefront activation links are already present for merchant setup.'
      : 'Do not claim storefront readiness until the theme extension blockers are resolved.',
    billingSummary?.poweredByBadgeRequired
      ? 'Powered by Loom Companion must remain visible in the tiers that require it.'
      : 'Powered-by badge is optional in the current billing posture.',
    store?.readiness?.goLiveEligible
      ? 'Go-live posture is already clean enough to use for launch and App Review walk-throughs.'
      : 'Go-live posture still has at least one blocker; keep launch messaging conservative until it is green.',
    orderLookupClaimReady
      ? supportReadiness?.allOrdersScopeGranted
        ? 'Order lookup can be shown as a live recent-and-historical support surface.'
        : 'Order lookup can be shown for recent orders, but avoid promising older-order coverage until broader Shopify order access is granted.'
      : 'Order lookup is not part of the Free or Starter launch package; keep post-purchase support handoff-based unless Elite order lookup is entitled and verified.',
  ]

  const hasCritical = !storefrontPreview?.ready || Boolean(billingSummary?.launchBlocked)
  const hasAttention = !hasCritical && !starterSurfaceReady

  return {
    status: hasCritical ? 'Blocked' : hasAttention ? 'Needs attention' : 'Ready',
    tone: hasCritical ? 'critical' : hasAttention ? 'attention' : 'success',
    message: hasCritical
      ? 'The launch packet still has at least one blocker between storefront activation and commercial posture.'
      : hasAttention
        ? 'The launch packet is credible, but a few claims still need careful positioning.'
        : 'The launch packet now reads like a real product package instead of a feature inventory.',
    safeClaims,
    commercialNotes,
    reviewNotes,
  }
}

function buildGoLiveChecklist(
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  widgetSettings: WidgetSettingsSnapshot | null,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): {
  status: string
  tone: 'success' | 'attention' | 'critical'
  message: string
  items: Array<{
    label: string
    status: string
    tone: 'success' | 'attention' | 'critical'
    detail: string
    action?:
      | { kind: 'open-url'; label: string; url: string }
      | { kind: 'run-go-live'; label: string }
      | { kind: 'run-sync'; label: string }
      | { kind: 'run-reconcile'; label: string }
      | { kind: 'activate-plan'; label: string; tierKey: string }
      | { kind: 'copy-launch-dossier'; label: string }
  }>
} {
  const configuredSurfaces = widgetSettings?.enabledSurfaces?.length ? widgetSettings.enabledSurfaces : DEFAULT_WIDGET_SURFACES
  const allowedSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const needsPaidTier = configuredSurfaces.some((surfaceId) => !allowedSurfaces.includes(surfaceId))
  const orderLookupTierAllowed = allowedSurfaces.includes('order-lookup')
  const storefrontReady = Boolean(storefrontPreview?.ready)
  const vectorizationReady = Boolean(vectorizationSummary?.readyToRun)
  const liveUpdatesHealthy = !vectorizationSummary?.automation || vectorizationSummary.automation.autoIndexingHealthy !== false
  const webhooksReady = !webhookSubscriptions || webhookSubscriptions.status === 'READY'
  const goLiveEligible = Boolean(store?.readiness?.goLiveEligible)
  const installRecoveryRequired = Boolean(session?.installRecoveryRequired)
  const scopeGrantRequired = Boolean(orderLookupTierAllowed && supportReadiness?.scopeGrantRequired)
  const scopeGrantUrl = supportReadiness?.scopeGrantUrl ?? null
  const activeStarterPlan = billingSummary?.availablePlans?.find((plan) => plan.tierKey === 'STARTER') ?? null
  const sourceDepthReady = hasLaunchSafeSourceDepth(store)
  const supportSurfaceReady = Boolean(
    supportReadiness?.merchantHandoffConfigured &&
      (supportReadiness.supportProfile?.contactEmail ||
        supportReadiness.supportProfile?.contactUrl ||
        supportReadiness.supportProfile?.helpCenterUrl),
  )
  const orderLookupReady = Boolean(
    orderLookupTierAllowed &&
      supportReadiness?.orderLookupSupported &&
      supportReadiness.orderLookupScopeGranted &&
      supportReadiness.appScopesUpdateWebhookReady &&
      supportSurfaceReady
  )

  const items: Array<{
    label: string
    status: string
    tone: 'success' | 'attention' | 'critical'
    detail: string
    action?:
      | { kind: 'open-url'; label: string; url: string }
      | { kind: 'run-go-live'; label: string }
      | { kind: 'run-sync'; label: string }
      | { kind: 'run-reconcile'; label: string }
      | { kind: 'activate-plan'; label: string; tierKey: string }
      | { kind: 'copy-launch-dossier'; label: string }
  }> = [
    {
      label: 'Merchant install and session',
      status: installRecoveryRequired ? 'Blocked' : scopeGrantRequired ? 'Needs action' : 'Ready',
      tone: installRecoveryRequired ? 'critical' : scopeGrantRequired ? 'attention' : 'success',
      detail: installRecoveryRequired
        ? session?.installRecoveryMessage ?? 'Merchant install recovery is required before Companion can be presented safely.'
        : scopeGrantRequired
          ? supportReadiness?.message ?? 'Merchant session is healthy, but Shopify order-read scope still needs approval.'
          : 'Merchant install, scoped auth, and embedded session recovery are clean enough for onboarding.',
      action: installRecoveryRequired && session?.installRecoveryUrl
        ? { kind: 'open-url', label: 'Recover install', url: session.installRecoveryUrl }
        : scopeGrantRequired && scopeGrantUrl
          ? { kind: 'open-url', label: 'Approve order scope', url: scopeGrantUrl }
        : undefined,
    },
    {
      label: 'Theme activation and storefront placement',
      status: storefrontReady ? 'Ready' : 'Needs action',
      tone: storefrontReady ? 'success' : 'attention',
      detail: storefrontReady
        ? 'Theme embed and merchant-placeable surfaces are active with live placement guidance.'
        : storefrontPreview?.blockingReasons?.join(' ') || 'Theme activation still needs a merchant action in Shopify Theme Editor.',
      action: !storefrontReady && storefrontPreview?.themeEditorActivationUrl
        ? { kind: 'open-url', label: 'Open Theme Editor', url: storefrontPreview.themeEditorActivationUrl }
        : undefined,
    },
    {
      label: 'Source grounding depth',
      status: sourceDepthReady ? 'Ready' : 'Needs action',
      tone: sourceDepthReady ? 'success' : 'attention',
      detail: sourceDepthReady
        ? 'Catalog, policy, and richer structured content are available for launch-safe grounding.'
        : 'Enable richer store sources so launch claims can go beyond core catalog-only answers.',
    },
    {
      label: 'Knowledge Sync and live updates',
      status: vectorizationReady && liveUpdatesHealthy && webhooksReady ? 'Ready' : 'Needs action',
      tone: vectorizationReady && liveUpdatesHealthy && webhooksReady ? 'success' : 'attention',
      detail: vectorizationReady && liveUpdatesHealthy && webhooksReady
        ? 'Knowledge Sync, webhooks, and live updates are aligned for a clean launch story.'
        : !vectorizationReady
          ? 'Knowledge Sync still needs refresh before enabled Shopify content can stay current.'
          : !liveUpdatesHealthy
            ? 'Live updates are currently degraded and should be stabilized before launch.'
            : 'Webhook subscriptions still need attention before launch.',
      action: !vectorizationReady
        ? { kind: 'run-reconcile', label: 'Refresh Knowledge Sync' }
        : !store?.syncDetail || store.syncDetail.status !== 'SYNCED'
          ? { kind: 'run-sync', label: 'Run sync now' }
          : undefined,
    },
    {
      label: 'Tier posture and billing story',
      status: !billingSummary?.launchBlocked && !needsPaidTier ? 'Ready' : 'Needs action',
      tone: !billingSummary?.launchBlocked && !needsPaidTier ? 'success' : 'attention',
      detail: !billingSummary?.launchBlocked && !needsPaidTier
        ? `Current ${billingSummary?.tierKey ?? 'UNKNOWN'} posture matches the enabled surface set.`
        : billingSummary?.launchBlocked
          ? billingSummary.message ?? 'Billing is still blocking launch.'
          : 'Configured storefront surfaces exceed the current billing tier allowance.',
      action: needsPaidTier && activeStarterPlan?.commerciallyAvailable
        ? { kind: 'activate-plan', label: `Activate ${activeStarterPlan.planName}`, tierKey: activeStarterPlan.tierKey }
        : undefined,
    },
    {
      label: 'Go-live and App Review packet',
      status: storefrontReady && sourceDepthReady && (!orderLookupTierAllowed || orderLookupReady) ? 'Ready' : 'Needs attention',
      tone: storefrontReady && sourceDepthReady && (!orderLookupTierAllowed || orderLookupReady) ? 'success' : 'attention',
      detail: goLiveEligible && (!orderLookupTierAllowed || orderLookupReady)
        ? 'The current store posture is strong enough to generate a merchant-facing launch dossier and run go-live.'
        : orderLookupTierAllowed
          ? 'Generate the launch dossier now, then close the remaining Elite order lookup blockers before go-live.'
          : 'Generate the launch dossier now, then use it to close the remaining launch blockers before go-live.',
      action: goLiveEligible && (!orderLookupTierAllowed || orderLookupReady)
        ? { kind: 'run-go-live', label: 'Run go-live' }
        : { kind: 'copy-launch-dossier', label: 'Copy launch dossier' },
    },
  ]

  const hasCritical = items.some((item) => item.tone === 'critical')
  const hasAttention = items.some((item) => item.tone === 'attention')
  return {
    status: hasCritical ? 'Blocked' : hasAttention ? 'Needs attention' : 'Ready',
    tone: hasCritical ? 'critical' : hasAttention ? 'attention' : 'success',
    message: hasCritical
      ? 'Companion still has at least one hard blocker before go-live.'
      : hasAttention
        ? 'Companion has a credible launch path, but a few operator steps still need to be closed.'
        : 'Companion now has a repeatable merchant go-live flow instead of an operator-only checklist.',
    items,
  }
}

function buildLaunchDossier(
  shell: ShopifyBridgeShellResponse | null,
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  goLiveChecklist: ReturnType<typeof buildGoLiveChecklist>,
  launchPacket: ReturnType<typeof buildLaunchPacket>,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const shopDomain = session?.shopDomain ?? storefrontPreview?.shopDomain ?? 'shopify-store'
  const reviewProviders = buildDetectedReviewProviders(store)
  const sourceCoverageSignals = buildSourceCoverageSignals(store)
  const orderLookupClaimReady = Boolean(
    billingSummary?.allowedSurfaces?.includes('order-lookup') && supportReadiness?.orderLookupSupported
  )
  return [
    '# Shopify Companion Launch Dossier',
    '',
    `Generated: ${new Date().toISOString()}`,
    `Shop: ${shopDomain}`,
    `App: ${shell?.appName ?? 'Loom Companion'}`,
    '',
    '## Current posture',
    `- Billing tier: ${billingSummary?.tierKey ?? 'UNKNOWN'} (${billingSummary?.status ?? 'UNKNOWN'})`,
    `- Billing mode: ${billingSummary?.mode ?? 'UNKNOWN'}`,
    `- Storefront ready: ${storefrontPreview?.ready ? 'yes' : 'no'}`,
    `- Widget status: ${storefrontPreview?.widgetStatus ?? 'UNKNOWN'}`,
    `- Live updates healthy: ${vectorizationSummary?.automation?.autoIndexingHealthy === false ? 'no' : 'yes'}`,
    `- Webhooks ready: ${webhookSubscriptions?.status ?? 'UNKNOWN'}`,
    `- Support posture: ${supportReadiness?.status ?? 'UNKNOWN'}${supportReadiness?.message ? ` (${supportReadiness.message})` : ''}`,
    '',
    '## Safe product claims',
    ...launchPacket.safeClaims.map((claim) => `- ${claim}`),
    '',
    '## Commercial packaging',
    ...launchPacket.commercialNotes.map((note) => `- ${note}`),
    '',
    '## App Review and launch notes',
    ...launchPacket.reviewNotes.map((note) => `- ${note}`),
    '',
    '## Grounding signals',
    `- ${(storefrontPreview?.groundingSignals ?? []).join(' · ') || 'Core catalog only'}`,
    `- Review providers: ${reviewProviders.join(' · ') || 'None detected'}`,
    ...sourceCoverageSignals.map((entry) => `- ${entry.label}: ${entry.signals.join(' · ')}`),
    '',
    '## Support and order lookup posture',
    `- Customer-safe order lookup: ${orderLookupClaimReady ? 'yes' : 'no'}`,
    `- Support lifecycle stage: ${supportReadiness?.lifecycleStage ?? 'UNKNOWN'}`,
    `- Granted scopes: ${supportReadiness?.grantedScopes?.join(' · ') || 'None detected'}`,
    `- App scopes webhook ready: ${supportReadiness?.appScopesUpdateWebhookReady ? 'yes' : 'no'}`,
    `- Older-order coverage: ${orderLookupClaimReady ? (supportReadiness?.allOrdersScopeGranted ? 'recent and historical order access available' : 'recent orders only until broader Shopify order access is granted') : 'not in current tier posture'}`,
    `- Active support subscriptions: ${formatSupportSubscriptions(supportReadiness)}`,
    `- Merchant handoff configured: ${supportReadiness?.merchantHandoffConfigured ? 'yes' : 'no'}`,
    `- Merchant handoff channels: ${formatSupportChannels(supportReadiness)}`,
    `- Merchant handoff note: ${supportReadiness?.supportProfile?.supportPolicyNote ?? supportReadiness?.merchantHandoffMessage ?? 'Not configured'}`,
    `- Next support actions: ${supportReadiness?.nextActions?.join(' | ') || 'None'}`,
    '',
    '## Merchant signal',
    `- Last 7 days total events: ${usageSummary?.totalLast7Days ?? 0}`,
    `- Top shopper questions: ${(usageSummary?.topQuestionsLast7Days ?? []).slice(0, 5).map((item) => `${item.label}: ${item.queryText}`).join(' | ') || 'None yet'}`,
    `- Unanswered/source-gap candidates: ${(usageSummary?.unansweredQuestionsLast7Days ?? []).slice(0, 5).map((item) => `${item.label}: ${item.queryText}`).join(' | ') || 'None yet'}`,
    `- Action-intent questions: ${(usageSummary?.actionIntentQuestionsLast7Days ?? []).slice(0, 5).map((item) => `${item.label}: ${item.queryText}`).join(' | ') || 'None yet'}${(usageSummary?.actionIntentQuestionsLast7Days ?? []).length ? ' (future Elite demand only; Starter remains read-only)' : ''}`,
    `- Surface journeys: ${(usageSummary?.last7DaySurfaceJourneys ?? []).slice(0, 5).map((item) => `${item.label}: ${formatSurfaceJourneySummary(item)}`).join(' | ') || 'None yet'}`,
    `- ROI posture: ${usageSummary?.roiSummary ? `${formatRoiStatus(usageSummary.roiSummary.status)} (${formatRoiSummary(usageSummary.roiSummary)})` : 'No ROI signal yet'}`,
    `- ROI recommendations: ${usageSummary?.roiSummary?.recommendations?.join(' | ') || 'None yet'}`,
    '',
    '## Go-live checklist',
    ...goLiveChecklist.items.map((item) => `- ${item.label}: ${item.status}. ${item.detail}`),
    '',
    '## Next operator notes',
    `- Storefront base URL: ${storefrontPreview?.storefrontBaseUrl ?? 'Not configured'}`,
    `- Theme editor activation URL: ${storefrontPreview?.themeEditorActivationUrl ?? 'Not configured'}`,
    `- Allowed surfaces: ${(billingSummary?.allowedSurfaces ?? []).join(' · ') || 'None detected'}`,
    `- Action packages: ${(billingSummary?.actionPackages ?? []).join(' · ') || 'None detected'}`,
    `- Install recovery URL: ${supportReadiness?.installRecoveryUrl ?? session?.installRecoveryUrl ?? 'Not required'}`,
    `- Scope grant URL: ${supportReadiness?.scopeGrantUrl ?? 'Not required'}`,
  ].join('\n')
}

function buildAppReviewGuide(
  shell: ShopifyBridgeShellResponse | null,
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  goLiveChecklist: ReturnType<typeof buildGoLiveChecklist>,
  launchPacket: ReturnType<typeof buildLaunchPacket>,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const shopDomain = session?.shopDomain ?? storefrontPreview?.shopDomain ?? 'shopify-store'
  const configuredSurfaces = store?.widgetDetail?.settings?.enabledSurfaces?.length
    ? store.widgetDetail.settings.enabledSurfaces
    : DEFAULT_WIDGET_SURFACES
  const allowedSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const activeSurfaceIds = configuredSurfaces.filter((surfaceId) => allowedSurfaces.includes(surfaceId))
  const activeSurfaceLabels = activeSurfaceIds
    .map(surfaceLabelFor)
  const reviewProviders = buildDetectedReviewProviders(store)
  const orderLookupClaimReady = Boolean(activeSurfaceIds.includes('order-lookup') && supportReadiness?.orderLookupSupported)
  const scopesText = session?.installRecord?.scopesText ?? 'Not captured in the current merchant session'
  const verificationLines = [
    `Storefront ready: ${storefrontPreview?.ready ? 'yes' : 'no'}`,
    `Go-live eligible: ${store?.readiness?.goLiveEligible ? 'yes' : 'no'}`,
    `Webhooks ready: ${webhookSubscriptions?.status ?? 'UNKNOWN'}`,
    `Live updates healthy: ${vectorizationSummary?.automation?.autoIndexingHealthy === false ? 'no' : 'yes'}`,
    `Billing posture: ${billingSummary?.tierKey ?? 'UNKNOWN'} (${billingSummary?.status ?? 'UNKNOWN'})`,
  ]

  return [
    '# Shopify Companion App Review Guide',
    '',
    `Generated: ${new Date().toISOString()}`,
    `App: ${shell?.appName ?? 'Loom Companion'}`,
    `Shop: ${shopDomain}`,
    '',
    '## Reviewer-facing product posture',
    '- Shopify Companion is an embedded, read-first storefront intelligence product.',
    `- Current billing tier for the review store: ${billingSummary?.tierKey ?? 'UNKNOWN'}.`,
    `- Current reviewer-safe surface set: ${activeSurfaceLabels.join(' · ') || 'AI search only'}.`,
    `- Grounding signals: ${(storefrontPreview?.groundingSignals ?? []).join(' · ') || 'Core catalog only'}.`,
    `- Review-aware provider signals: ${reviewProviders.join(' · ') || 'None detected'}.`,
    '',
    '## Explicit non-goals for this review package',
    '- Do not present autonomous checkout, arbitrary merchant automation, or unsupported order/customer writes.',
    orderLookupClaimReady
      ? '- Do not imply refunds, cancellations, address changes, payment detail access, or customer profile access. Order lookup stays read-only and verification-bound.'
      : '- Do not imply customer-safe order lookup for Free or Starter review stores.',
    '- Only mention Elite governed actions if the current review store is intentionally configured for that commercial posture.',
    '',
    '## Requested scope posture',
    `- Current install scopes: ${scopesText}`,
    `- Support readiness: ${supportReadiness?.status ?? 'UNKNOWN'}${supportReadiness?.message ? ` · ${supportReadiness.message}` : ''}`,
    '- The current launch posture stays read-first and does not require transactional write scopes for the default reviewer story.',
    '',
    '## Reviewer flow for the current store',
    '1. Open the embedded admin UI and confirm merchant session resolution.',
    '2. Confirm launch and App Review readiness in the merchant app.',
    '3. Review the tier ladder and governance posture for the current store.',
    '4. Review storefront preview and theme activation guidance.',
    '5. Run one shopper discovery flow, one policy flow, and one comparison flow on the live storefront.',
    '6. Confirm grounded answers and visible source cards.',
    orderLookupClaimReady
      ? '7. Demonstrate the support order lookup with an exact order number and checkout email, then state clearly that refunds, edits, and account changes still require merchant handoff.'
      : '7. Review support bundle, App Store package, support runbook, and screencast script exports from the merchant app.',
    billingSummary?.actionCapable
      ? '8. If Elite is intentionally enabled for this store, show the governed action posture as a separate appendix with confirmation and audit language.'
      : '8. Keep the walkthrough read-first; do not imply governed commerce for this review store.',
    '',
    '## Evidence required before submission',
    ...verificationLines.map((line) => `- ${line}`),
    ...goLiveChecklist.items.map((item) => `- ${item.label}: ${item.status}. ${item.detail}`),
    '',
    '## Allowed claims',
    ...launchPacket.safeClaims.map((claim) => `- ${claim}`),
    '',
    '## Operator reminder',
    '- Run `scripts/verify-shopify-companion.sh` before handing the package to reviewers.',
    '- Use the support runbook and screencast script generated from the same store posture so the package does not drift from live reality.',
  ].join('\n')
}

function buildReviewScreencastScript(
  shell: ShopifyBridgeShellResponse | null,
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  launchPacket: ReturnType<typeof buildLaunchPacket>,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const shopDomain = session?.shopDomain ?? storefrontPreview?.shopDomain ?? 'shopify-store'
  const configuredSurfaces = store?.widgetDetail?.settings?.enabledSurfaces?.length
    ? store.widgetDetail.settings.enabledSurfaces
    : DEFAULT_WIDGET_SURFACES
  const allowedSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const activeSurfaceIds = configuredSurfaces.filter((surfaceId) => allowedSurfaces.includes(surfaceId))
  const activeSurfaceLabels = activeSurfaceIds
    .map(surfaceLabelFor)
  const orderLookupClaimReady = Boolean(activeSurfaceIds.includes('order-lookup') && supportReadiness?.orderLookupSupported)

  return [
    '# Shopify Companion Review Screencast Script',
    '',
    `Generated: ${new Date().toISOString()}`,
    `App: ${shell?.appName ?? 'Loom Companion'}`,
    `Shop: ${shopDomain}`,
    '',
    '## Recording goal',
    '- Show the bounded merchant-to-storefront loop without implying unsupported order access or autonomous checkout.',
    `- Keep the story centered on the current live surface set: ${activeSurfaceLabels.join(' · ') || 'AI search'}.`,
    '',
    '## Segment 1 — Merchant posture',
    '- Open the merchant app home.',
    `- State the current billing/tier posture: ${billingSummary?.tierKey ?? 'UNKNOWN'} (${billingSummary?.status ?? 'UNKNOWN'}).`,
    '- Show launch readiness and store intelligence health before any storefront demo.',
    '',
    '## Segment 2 — Source readiness and lifecycle',
    '- Show source readiness, webhook posture, and live update health.',
    '- State that the app binds one Shopify store to one Companion launch path.',
    '',
    '## Segment 3 — Storefront activation',
    `- Open storefront preview${storefrontPreview?.themeEditorActivationUrl ? ' and show the theme activation link.' : '.'}`,
    `- Confirm the current shopper-facing surfaces: ${activeSurfaceLabels.join(' · ') || 'AI search'}.`,
    '',
    '## Segment 4 — Shopper walkthrough',
    '- Demonstrate one discovery question and one policy or comparison question.',
    '- Keep the narration focused on grounded answers, source cards, and embedded intelligence before chat depth.',
    orderLookupClaimReady
      ? '- Show the order lookup block on a support page with an exact order number plus checkout email, then explain that the flow is read-only and still hands off refunds or edits to the merchant.'
      : '- State that post-purchase support remains policy-grounded and handoff-based unless Elite order lookup is entitled and verified.',
    '',
    '## Segment 5 — Launch and support exports',
    '- Show the App Store listing package, App Review guide, support runbook, support bundle, and design-partner packet exports.',
    '- State that launch/support collateral is generated from the same live store posture shown in the demo.',
    '',
    billingSummary?.actionCapable
      ? '## Optional appendix — Elite governed action posture\n- Only include this appendix if the review store is intentionally configured for Elite.\n- Show explicit confirmation and audit posture, and state that this is governed commerce rather than autonomous checkout.'
      : '## No Elite appendix\n- Do not show governed actions for this store because the current commercial posture is read-first.',
    '',
    '## Claims to avoid',
    orderLookupClaimReady
      ? supportReadiness?.allOrdersScopeGranted
        ? '- Do not imply refunds, cancellations, address changes, payment-detail access, or customer account changes.'
        : '- Do not imply refunds, cancellations, address changes, payment-detail access, customer account changes, or guaranteed older-order coverage.'
      : '- Do not imply customer-safe order lookup or order-status reads for Free or Starter.',
    '- Do not imply broad support desk replacement.',
    ...launchPacket.reviewNotes.map((note) => `- ${note}`),
  ].join('\n')
}

function buildAppStoreListingPackage(
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  launchPacket: ReturnType<typeof buildLaunchPacket>,
  usageSummary: ShopifyBridgeUsageSummary | null,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const configuredSurfaces = store?.widgetDetail?.settings?.enabledSurfaces?.length
    ? store.widgetDetail.settings.enabledSurfaces
    : DEFAULT_WIDGET_SURFACES
  const allowedSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const activeSurfaceIds = configuredSurfaces.filter((surfaceId) => allowedSurfaces.includes(surfaceId))
  const activeSurfaceLabels = activeSurfaceIds
    .map(surfaceLabelFor)
  const starterLabels = starterSurfaceLabels()
  const reviewProviders = buildDetectedReviewProviders(store)
  const sourceCoverageSignals = buildSourceCoverageSignals(store)
  const actionIntentCount = (usageSummary?.actionIntentQuestionsLast7Days ?? [])
    .reduce((sum, question) => sum + question.count, 0)
  const unansweredCount = (usageSummary?.unansweredQuestionsLast7Days ?? [])
    .reduce((sum, question) => sum + question.count, 0)
  const hasEliteGovernance = Boolean(
    billingSummary?.actionCapable &&
      billingSummary.requiresExplicitConfirmation &&
      billingSummary.auditTrailAvailable &&
      billingSummary.actionPackages.length,
  )
  const orderLookupClaimReady = Boolean(activeSurfaceIds.includes('order-lookup') && supportReadiness?.orderLookupSupported)
  const subtitle = 'AI search, product insights, and policy answers for Shopify'
  const oneLineDescription = 'Add AI search, product insights, FAQs, comparison help, and grounded policy answers to your Shopify storefront.'
  const fullDescription = [
    'Loom Companion brings embedded AI store intelligence to Shopify.',
    activeSurfaceLabels.length
      ? `Shoppers can use ${activeSurfaceLabels.join(', ')} powered by live store data.`
      : 'Shoppers can use AI search and grounded storefront guidance powered by live store data.',
    orderLookupClaimReady
      ? supportReadiness?.allOrdersScopeGranted
        ? 'Support teams can also verify orders through a read-only order lookup block using the exact order number and checkout email.'
        : 'Support teams can also verify recent orders through a read-only order lookup block using the exact order number and checkout email.'
      : 'Post-purchase support remains policy-grounded and merchant-handoff based unless Elite order lookup is entitled and verified.',
    sourceCoverageSignals.length
      ? `Grounding currently draws on ${sourceCoverageSignals.map((entry) => `${entry.label.toLowerCase()} (${entry.signals.join(', ')})`).join('; ')}.`
      : 'Grounding currently draws on live catalog, content, and policy data.',
    hasEliteGovernance
      ? `Elite guided commerce is live with ${billingSummary?.actionPackages.join(' and ') ?? 'governed actions'}, explicit confirmation, and audit history.`
      : 'The default launch posture stays read-first, with governed actions only marketed when the Elite rollout is commercially active.',
  ].join(' ')
  const screenshotLines = [
    'Merchant app home showing store intelligence health, launch readiness, and the tier ladder.',
    'Merchant storefront preview showing AI search, contextual pill, product insight, policy strip, product FAQ, and comparison placements.',
    'Storefront AI search result state with grounded product and source cards.',
    activeSurfaceIds.includes('product-insight') ? 'Product page with product insight and policy strip visible together.' : null,
    activeSurfaceIds.includes('contextual-pill') ? 'Collection or product page with the contextual pill block active.' : null,
    activeSurfaceIds.includes('product-faq') ? 'Product FAQ block in use on a real product page.' : null,
    activeSurfaceIds.includes('comparison') ? 'Comparison block in use on a real product page.' : null,
    orderLookupClaimReady ? 'Support page with the read-only order lookup block in use.' : null,
    hasEliteGovernance ? 'Optional Elite screenshot showing governed action history or explicit confirmation UI.' : null,
  ].filter(Boolean) as string[]
  const commerciallyAvailablePlans = (billingSummary?.availablePlans ?? [])
    .filter((plan) => plan.commerciallyAvailable)
    .map((plan) => `${plan.tierKey}: ${plan.planName}`)

  return [
    '# Shopify Companion App Store Listing Package',
    '',
    `Generated: ${new Date().toISOString()}`,
    `Storefront ready: ${storefrontPreview?.ready ? 'yes' : 'no'}`,
    `Current billing posture: ${billingSummary?.tierKey ?? 'UNKNOWN'} (${billingSummary?.status ?? 'UNKNOWN'})`,
    '',
    '## Listing posture',
    '- Position Shopify Companion as embedded storefront intelligence with chat-assisted depth when needed.',
    '- Keep the launch story read-first unless Elite guided commerce is commercially active and verified.',
    '',
    '## Primary listing copy',
    '- App name: Loom Companion',
    `- Short subtitle: ${subtitle}`,
    `- One-sentence description: ${oneLineDescription}`,
    `- Full description: ${fullDescription}`,
    '',
    '## Tier-safe product truth',
    `- Free: AI search for the storefront with ${billingSummary?.poweredByBadgeRequired ? 'a required powered-by posture.' : 'a launch-safe entry posture.'}`,
    `- Starter: ${starterLabels.join(', ')}, read-only Companion chat depth, and shopper analytics. Starter does not include order lookup or governed actions.`,
    hasEliteGovernance
      ? `- Elite: Governed commerce actions for ${(billingSummary?.actionPackages ?? []).join(' and ')} with explicit confirmation and audit trail.`
      : '- Elite: Do not market governed actions until the commercial rollout is active for the target store.',
    `- Value proof: ${usageSummary ? `${usageSummary.totalLast7Days} shopper/merchant signals, ${unansweredCount} source-gap candidates, and ${actionIntentCount} future Elite demand signals in the last 7 days.` : 'Use live usage, source-gap, and action-intent analytics after traffic starts.'}`,
    '',
    '## Source-depth proof points',
    `- Review providers: ${reviewProviders.join(' · ') || 'None detected yet'}`,
    ...sourceCoverageSignals.map((entry) => `- ${entry.label}: ${entry.signals.join(' · ')}`),
    '',
    '## Screenshot shot list',
    ...screenshotLines.map((line, index) => `${index + 1}. ${line}`),
    '',
    '## Allowed claims',
    ...launchPacket.safeClaims.map((claim) => `- ${claim}`),
    '',
    '## Disallowed claims',
    '- Do not claim autonomous purchasing or checkout automation.',
    '- Do not claim full support desk replacement or broad workflow automation.',
    orderLookupClaimReady
      ? '- Do not claim refunds, cancellations, address changes, or customer account updates from the order lookup surface.'
      : '- Do not claim customer-safe order lookup for Free or Starter stores.',
    '- Do not claim all review providers are supported.',
    '- Do not claim Elite actions when the current commercial rollout is not active.',
    '',
    '## Pricing and commercial notes',
    `- Current active tier: ${billingSummary?.tierKey ?? 'UNKNOWN'}`,
    `- Commercially available plans: ${commerciallyAvailablePlans.join(' · ') || 'Free only'}`,
    ...launchPacket.commercialNotes.map((note) => `- ${note}`),
    '',
    '## Launch asset checklist',
    '- Final subtitle chosen from the generated package above.',
    '- Final long description reviewed against the current live surface and source-depth posture.',
    '- Screenshot set captured from the live product, not mocks.',
    '- Review screencast, support runbook, and design-partner packet all match this package.',
  ].join('\n')
}

function buildDesignPartnerRolloutPacket(
  shell: ShopifyBridgeShellResponse | null,
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  goLiveChecklist: ReturnType<typeof buildGoLiveChecklist>,
  launchPacket: ReturnType<typeof buildLaunchPacket>,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const shopDomain = session?.shopDomain ?? store?.shopDomain ?? 'shopify-store'
  const configuredSurfaceIds = store?.widgetDetail?.settings?.enabledSurfaces?.length
    ? store.widgetDetail.settings.enabledSurfaces
    : DEFAULT_WIDGET_SURFACES
  const allowedSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const activeSurfaceIds = configuredSurfaceIds.filter((surfaceId) => allowedSurfaces.includes(surfaceId))
  const activeSurfaceLabels = activeSurfaceIds
    .map(surfaceLabelFor)
  const reviewProviders = buildDetectedReviewProviders(store)
  const orderLookupClaimReady = Boolean(activeSurfaceIds.includes('order-lookup') && supportReadiness?.orderLookupSupported)

  return [
    '# Shopify Companion Design-Partner Rollout Packet',
    '',
    `Generated: ${new Date().toISOString()}`,
    `App: ${shell?.appName ?? 'Loom Companion'}`,
    `Shop: ${shopDomain}`,
    '',
    '## Preconditions',
    '- Live Shopify verification passed on 2026-04-23 for the current environment baseline.',
    `- Merchant session resolved: ${session ? 'yes' : 'no'}`,
    `- Storefront ready: ${storefrontPreview?.ready ? 'yes' : 'no'}`,
    `- Go-live posture: ${store?.readiness?.goLiveEligible ? 'ready' : 'blocked'}`,
    '',
    '## Merchant store requirements',
    '- Use a real development or partner-approved merchant store.',
    '- Prefer a safe preview theme before touching a production theme.',
    `- Confirm enough source depth for the rollout story: ${reviewProviders.join(' · ') || 'catalog/policy only so far'}.`,
    orderLookupClaimReady
      ? '- Place the order lookup block on a support or contact page before the partner walkthrough.'
      : '- Keep post-purchase support handoff-based unless Elite order lookup is entitled and support readiness is green.',
    '',
    '## Rollout sequence',
    ...goLiveChecklist.items.map((item) => `- ${item.label}: ${item.status}. ${item.detail}`),
    '',
    '## Intended storefront surface set',
    `- ${(activeSurfaceLabels.length ? activeSurfaceLabels.join(' · ') : 'AI search').trim()}`,
    '',
    '## Required evidence to capture',
    '- Store intelligence health screenshot or export.',
    '- Launch and App Review readiness screenshot.',
    '- Tier ladder screenshot.',
    '- Storefront preview screenshot with the intended surfaces visible.',
    orderLookupClaimReady ? '- Order lookup screenshot or clip showing exact order number plus checkout email verification.' : null,
    '- Support bundle export.',
    '- Launch dossier export.',
    '- App Store listing package export.',
    '',
    '## Merchant value prompts',
    `- Run at least one product discovery question, one policy question, and one comparison flow${billingSummary?.actionCapable ? ', then one governed commerce flow if Elite is active.' : '.'}`,
    `- Capture top shopper questions and surface usage after traffic: ${(usageSummary?.topQuestionsLast7Days ?? []).slice(0, 3).map((item) => item.queryText).join(' | ') || 'no traffic yet'}`,
    `- Capture unanswered/source-gap candidates after traffic: ${(usageSummary?.unansweredQuestionsLast7Days ?? []).slice(0, 3).map((item) => item.queryText).join(' | ') || 'no source-gap signal yet'}`,
    `- Capture action-intent questions as future Elite demand only: ${(usageSummary?.actionIntentQuestionsLast7Days ?? []).slice(0, 3).map((item) => item.queryText).join(' | ') || 'no action-intent signal yet'}`,
    `- Capture surface journey evidence after traffic: ${(usageSummary?.last7DaySurfaceJourneys ?? []).slice(0, 3).map((item) => `${item.label} (${formatSurfaceJourneySummary(item)})`).join(' | ') || 'no journey signal yet'}`,
    `- Capture ROI posture after traffic: ${usageSummary?.roiSummary ? `${formatRoiStatus(usageSummary.roiSummary.status)} (${formatRoiSummary(usageSummary.roiSummary)})` : 'no ROI signal yet'}`,
    `- Follow ROI recommendations: ${usageSummary?.roiSummary?.recommendations?.join(' | ') || 'none yet'}`,
    '',
    '## Sign-off criteria',
    `- Launch packet posture: ${launchPacket.status}`,
    `- Theme activation URL: ${storefrontPreview?.themeEditorActivationUrl ?? 'not available'}`,
    `- Storefront base URL: ${storefrontPreview?.storefrontBaseUrl ?? 'not available'}`,
    `- Billing posture: ${billingSummary?.tierKey ?? 'UNKNOWN'} (${billingSummary?.status ?? 'UNKNOWN'})`,
    '- Do not mark the partner complete until the intended surfaces are visible and the rollout evidence above is captured.',
  ].join('\n')
}

function buildSupportRunbook(
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  goLiveChecklist: ReturnType<typeof buildGoLiveChecklist>,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const shopDomain = session?.shopDomain ?? store?.shopDomain ?? 'shopify-store'
  const configuredSurfaces = store?.widgetDetail?.settings?.enabledSurfaces?.length
    ? store.widgetDetail.settings.enabledSurfaces
    : DEFAULT_WIDGET_SURFACES
  const allowedSurfaces = billingSummary?.allowedSurfaces?.length ? billingSummary.allowedSurfaces : DEFAULT_WIDGET_SURFACES
  const activeSurfaceLabels = configuredSurfaces
    .filter((surfaceId) => allowedSurfaces.includes(surfaceId))
    .map(surfaceLabelFor)
  const orderLookupClaimReady = Boolean(allowedSurfaces.includes('order-lookup') && supportReadiness?.orderLookupSupported)
  const reviewProviders = buildDetectedReviewProviders(store)
  const subscriptionWebhook = webhookSubscriptions?.topics.find((topic) => topic.topic === 'APP_SUBSCRIPTIONS_UPDATE') ?? null
  const policyGroundingAvailable = Boolean(
    store?.policiesEnabled ||
      (storefrontPreview?.groundingSignals ?? []).includes('Policy grounding'),
  )
  const lifecycleSignals = [
    `Install status: ${store?.installStatus ?? 'UNKNOWN'}`,
    `Onboarding status: ${store?.onboardingStatus ?? 'UNKNOWN'}`,
    `Billing posture: ${billingSummary?.tierKey ?? 'UNKNOWN'} (${billingSummary?.status ?? 'UNKNOWN'})`,
    `Billing webhook posture: ${subscriptionWebhook?.status ?? 'UNKNOWN'}`,
    `Storefront ready: ${storefrontPreview?.ready ? 'yes' : 'no'}`,
    `Live updates healthy: ${vectorizationSummary?.automation?.autoIndexingHealthy === false ? 'no' : 'yes'}`,
  ]

  return [
    '# Shopify Companion Support Runbook',
    '',
    `Generated: ${new Date().toISOString()}`,
    `Shop: ${shopDomain}`,
    '',
    '## Current live posture',
    ...lifecycleSignals.map((line) => `- ${line}`),
    `- Support readiness: ${supportReadiness?.status ?? 'UNKNOWN'}${supportReadiness?.message ? ` · ${supportReadiness.message}` : ''}`,
    `- Support lifecycle stage: ${supportReadiness?.lifecycleStage ?? 'UNKNOWN'}`,
    `- Merchant handoff channels: ${formatSupportChannels(supportReadiness)}`,
    `- Active shopper surfaces: ${activeSurfaceLabels.join(' · ') || 'AI search only'}`,
    `- Review-aware provider signals: ${reviewProviders.join(' · ') || 'None detected'}`,
    `- Last webhook: ${store?.webhookDetail?.topic ?? '—'} (${formatTimestamp(store?.webhookDetail?.receivedAt)})`,
    '',
    '## Support-safe scope',
    '- Product discovery, comparison, policy grounding, and storefront activation guidance are in scope.',
    orderLookupClaimReady
      ? supportReadiness?.allOrdersScopeGranted
        ? '- Customer-safe order lookup is in scope with exact order number plus checkout email verification.'
        : '- Customer-safe order lookup is in scope for recent orders only, with exact order number plus checkout email verification.'
      : '- Keep order-specific support in merchant handoff mode unless Elite order lookup is entitled and verified.',
    billingSummary?.actionCapable
      ? '- Elite governed commerce is in scope only when the current store is entitled and the flow uses explicit confirmation plus audit history.'
      : '- Keep the support posture read-first for this store. Do not imply guided commerce unless the live billing tier changes.',
    '',
    '## Out of scope',
    orderLookupClaimReady
      ? '- Refunds, cancellations, address changes, payment-detail access, and customer account actions stay out of scope for Companion.'
      : '- Customer-safe order lookup is not currently supported.',
    '- Do not promise refund approval, cancellation, tracking, or account changes from Companion.',
    '- Do not widen scopes or permissions during incident handling.',
    '',
    '## Return and post-purchase guidance',
    policyGroundingAvailable
      ? '- Use published store policy grounding for general return or refund guidance, but keep it policy-grounded and non-transactional.'
      : '- The store does not currently expose enough policy grounding for safe return guidance. Hand off return/refund questions directly.',
    orderLookupClaimReady
      ? '- Order-specific status and tracking lookups may use the governed order lookup block, but any refund, cancellation, address change, or account-specific action must still be handed off to the merchant support channel.'
      : '- Any order-specific return, refund, tracking, or order-status question must be handed off to the merchant support channel.',
    orderLookupClaimReady
      ? '- Use a support handoff like: “I can verify your order status with your order number and checkout email, but refunds, changes, and account-specific help still go through the merchant support team.”'
      : '- Use a support handoff like: “I can explain the store’s published policy, but I cannot inspect or change your order. Please continue with the merchant support channel for order-specific help.”',
    `- Current merchant handoff note: ${supportReadiness?.supportProfile?.supportPolicyNote ?? supportReadiness?.merchantHandoffMessage ?? 'Not configured'}`,
    '',
    '## Triage order',
    ...goLiveChecklist.items.map((item, index) => `${index + 1}. ${item.label}: ${item.status}. ${item.detail}`),
    '',
    '## Next support actions',
    ...(supportReadiness?.nextActions?.length ? supportReadiness.nextActions.map((item) => `- ${item}`) : ['- None recorded']),
    '',
    '## Merchant signal to review before escalation',
    `- ROI posture: ${usageSummary?.roiSummary ? `${formatRoiStatus(usageSummary.roiSummary.status)} (${formatRoiSummary(usageSummary.roiSummary)})` : 'No ROI signal yet'}`,
    `- Top shopper questions: ${(usageSummary?.topQuestionsLast7Days ?? []).slice(0, 3).map((item) => item.queryText).join(' | ') || 'None yet'}`,
    `- Unanswered/source-gap candidates: ${(usageSummary?.unansweredQuestionsLast7Days ?? []).slice(0, 3).map((item) => item.queryText).join(' | ') || 'None yet'}`,
    `- Action-intent questions: ${(usageSummary?.actionIntentQuestionsLast7Days ?? []).slice(0, 3).map((item) => item.queryText).join(' | ') || 'None yet'}${(usageSummary?.actionIntentQuestionsLast7Days ?? []).length ? ' (do not present Starter as an action tier)' : ''}`,
    `- Surface journeys: ${(usageSummary?.last7DaySurfaceJourneys ?? []).slice(0, 3).map((item) => `${item.label}: ${formatSurfaceJourneySummary(item)}`).join(' | ') || 'None yet'}`,
    '',
    '## Operator reminders',
    '- Use the support bundle first for diagnostics, then this runbook for bounded support posture.',
    '- Re-run `scripts/verify-shopify-companion.sh` before escalating a launch or review incident.',
    '- Keep support guidance aligned with the App Review guide and screencast script generated from the same store posture.',
  ].join('\n')
}

function buildLifecycleSubscriptionPacket(
  session: ShopifyBridgeMerchantSessionResponse | null,
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null,
  supportReadiness: ShopifyBridgeMerchantSessionResponse['supportReadiness'] | null,
): string {
  const shopDomain = session?.shopDomain ?? store?.shopDomain ?? 'shopify-store'
  const subscriptionWebhook = webhookSubscriptions?.topics.find((topic) => topic.topic === 'APP_SUBSCRIPTIONS_UPDATE') ?? null
  const scopesWebhook = webhookSubscriptions?.topics.find((topic) => topic.topic === 'APP_SCOPES_UPDATE') ?? null
  const orderLookupClaimReady = Boolean(
    billingSummary?.allowedSurfaces?.includes('order-lookup') && supportReadiness?.orderLookupSupported
  )
  const availablePlans = (billingSummary?.availablePlans ?? []).map((plan) => {
    const posture = [plan.tierKey, plan.planName]
    if (plan.active) {
      posture.push('current')
    }
    if (!plan.commerciallyAvailable) {
      posture.push('not commercial')
    }
    return posture.join(' · ')
  })

  return [
    '# Shopify Companion Lifecycle And Subscription Packet',
    '',
    `Generated: ${new Date().toISOString()}`,
    `Shop: ${shopDomain}`,
    '',
    '## Lifecycle timeline',
    `- Installed at: ${formatTimestamp(session?.installRecord?.installedAt)}`,
    `- Last authenticated: ${formatTimestamp(session?.installRecord?.lastAuthenticatedAt)}`,
    `- Last uninstall: ${formatTimestamp(session?.installRecord?.lastUninstalledAt)}`,
    `- Last source preflight: ${formatTimestamp(store?.lastSourcePreflightAt)}`,
    `- Last sync: ${formatTimestamp(store?.lastSyncAt)}`,
    `- Last webhook: ${formatTimestamp(store?.lastWebhookAt)}${store?.webhookDetail?.topic ? ` (${store.webhookDetail.topic})` : ''}`,
    `- Latest release applied: ${formatTimestamp(store?.latestRelease?.appliedAt)}`,
    '',
    '## Current lifecycle status',
    `- Install status: ${store?.installStatus ?? 'UNKNOWN'}`,
    `- Onboarding status: ${store?.onboardingStatus ?? 'UNKNOWN'}`,
    `- Deployment status: ${store?.deploymentStatus ?? 'UNBOUND'}`,
    `- Sync status: ${store?.syncDetail?.status ?? store?.syncStatus ?? 'UNKNOWN'}`,
    `- Storefront ready: ${store?.readiness?.storefrontReady ? 'yes' : 'no'}`,
    `- Live updates healthy: ${vectorizationSummary?.automation?.autoIndexingHealthy === false ? 'no' : 'yes'}`,
    `- Support readiness: ${supportReadiness?.status ?? 'UNKNOWN'}${supportReadiness?.message ? ` · ${supportReadiness.message}` : ''}`,
    `- Lifecycle stage: ${supportReadiness?.lifecycleStage ?? 'UNKNOWN'}`,
    '',
    '## Subscription posture',
    `- Billing mode: ${billingSummary?.mode ?? 'UNKNOWN'}`,
    `- Billing tier: ${billingSummary?.tierKey ?? 'UNKNOWN'}`,
    `- Billing status: ${billingSummary?.status ?? 'UNKNOWN'}`,
    `- Launch blocked: ${billingSummary?.launchBlocked ? 'yes' : 'no'}`,
    `- Allowed surfaces: ${(billingSummary?.allowedSurfaces ?? []).join(' · ') || 'None detected'}`,
    `- Action packages: ${(billingSummary?.actionPackages ?? []).join(' · ') || 'None detected'}`,
    `- Subscription webhook: ${subscriptionWebhook?.status ?? 'UNKNOWN'}${subscriptionWebhook?.message ? ` · ${subscriptionWebhook.message}` : ''}`,
    `- App scopes webhook: ${scopesWebhook?.status ?? 'UNKNOWN'}${scopesWebhook?.message ? ` · ${scopesWebhook.message}` : ''}`,
    `- Available plans: ${availablePlans.join(' | ') || 'None detected'}`,
    `- Active subscriptions: ${formatSupportSubscriptions(supportReadiness)}`,
    '',
    '## Support and order scope posture',
    `- Order lookup supported: ${orderLookupClaimReady ? 'yes' : 'no'}`,
    `- Order scope granted: ${supportReadiness?.orderLookupScopeGranted ? 'yes' : 'no'}`,
    `- Historical order scope granted: ${supportReadiness?.allOrdersScopeGranted ? 'yes' : 'no'}`,
    `- Granted scopes: ${supportReadiness?.grantedScopes?.join(' · ') || 'None detected'}`,
    `- Missing scopes: ${supportReadiness?.missingScopes?.join(' · ') || 'None'}`,
    `- Merchant handoff configured: ${supportReadiness?.merchantHandoffConfigured ? 'yes' : 'no'}`,
    `- Merchant handoff channels: ${formatSupportChannels(supportReadiness)}`,
    `- Merchant handoff note: ${supportReadiness?.supportProfile?.supportPolicyNote ?? supportReadiness?.merchantHandoffMessage ?? 'Not configured'}`,
    `- Active support subscriptions: ${formatSupportSubscriptions(supportReadiness)}`,
    `- Next actions: ${supportReadiness?.nextActions?.join(' | ') || 'None'}`,
    '',
    '## Operator notes',
    billingSummary?.launchBlocked
      ? `- Billing is currently blocking go-live: ${billingSummary.message ?? 'Review the active plan and merchant approval flow.'}`
      : '- Billing is not currently blocking go-live for this store.',
    session?.installRecoveryRequired
      ? `- Merchant install recovery is required: ${session.installRecoveryMessage ?? 'Reinstall before continuing.'}`
      : '- Merchant install recovery is not currently required.',
    webhookSubscriptions?.status === 'READY'
      ? '- Webhook posture is ready for install, lifecycle, and subscription tracking.'
      : `- Webhook posture still needs attention: ${webhookSubscriptions?.message ?? 'Review webhook subscriptions before launch.'}`,
  ].join('\n')
}

function readPath(root: unknown, ...segments: string[]): unknown {
  let current: unknown = root
  for (const segment of segments) {
    if (!current || typeof current !== 'object' || !(segment in current)) {
      return undefined
    }
    current = (current as Record<string, unknown>)[segment]
  }
  return current
}

function firstArray(...values: unknown[]): unknown[] | null {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value
    }
  }
  return null
}

function buildSourceCoverageSignals(
  store: ShopifyBridgeMerchantSessionResponse['store'] | null,
): Array<{ category: string; label: string; signals: string[] }> {
  if (!store?.sourcePreflight?.categories?.length) {
    return []
  }
  return store.sourcePreflight.categories
    .filter((category) => category.enabled && category.status === 'READY' && category.signals.length)
    .map((category) => ({
      category: category.category,
      label: categoryLabel(category.category),
      signals: category.signals,
    }))
}

function buildDetectedReviewProviders(store: ShopifyBridgeMerchantSessionResponse['store'] | null): string[] {
  const productsCategory = store?.sourcePreflight?.categories?.find((category) => category.category === 'products')
  if (!productsCategory?.signals?.length) {
    return []
  }
  return productsCategory.signals.filter((signal) => signal !== 'Review metafields detected')
}

function hasLaunchSafeSourceDepth(store: ShopifyBridgeMerchantSessionResponse['store'] | null): boolean {
  if (!store?.productsEnabled || !store?.policiesEnabled) {
    return false
  }
  const articlesReady = Boolean(
    store.sourcePreflight?.categories?.some(
      (category) => category.category === 'articles' && category.enabled && category.status === 'READY' && category.itemCount > 0,
    ),
  )
  const metaobjectsReady = Boolean(
    store.sourcePreflight?.categories?.some(
      (category) => category.category === 'metaobjects' && category.enabled && category.status === 'READY' && category.itemCount > 0,
    ),
  )
  const reviewProvidersReady = buildDetectedReviewProviders(store).length > 0
  return articlesReady || metaobjectsReady || reviewProvidersReady
}

function categoryLabel(category: string): string {
  switch (category) {
    case 'products':
      return 'Product review signals'
    case 'metaobjects':
      return 'Metaobject coverage'
    case 'articles':
      return 'Article coverage'
    default:
      return category.charAt(0).toUpperCase() + category.slice(1)
  }
}

function normalizeProductCard(candidate: unknown): PlaygroundProductCard | null {
  if (!candidate || typeof candidate !== 'object') {
    return null
  }
  const record = candidate as Record<string, unknown>
  const title = firstText(record.title, record.name, record.label)
  if (!title) {
    return null
  }
  return {
    title,
    subtitle: joinText(firstText(record.vendor, record.brand), firstText(record.type, record.subtitle)),
    detail: joinText(
      firstText(record.formattedPrice, record.priceText, scalarText(record.price)),
      firstText(record.availability, record.status)
    ),
    url: firstText(record.url, record.href),
  }
}

function normalizeSourceCard(candidate: unknown): PlaygroundSourceCard | null {
  if (typeof candidate === 'string' && candidate.trim()) {
    return { label: candidate.trim(), excerpt: null, url: null }
  }
  if (!candidate || typeof candidate !== 'object') {
    return null
  }
  const record = candidate as Record<string, unknown>
  const label = firstText(record.title, record.label, record.name, record.id)
  if (!label) {
    return null
  }
  return {
    label,
    excerpt: truncateText(firstText(record.snippet, record.summary, record.excerpt, record.text, record.content), 220),
    url: firstText(record.url, record.href),
  }
}

function firstText(...values: unknown[]): string | null {
  for (const value of values) {
    const normalized = scalarText(value)
    if (normalized) {
      return normalized
    }
  }
  return null
}

function scalarText(value: unknown): string | null {
  if (typeof value === 'string' && value.trim()) {
    return value.trim()
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return null
}

function joinText(...values: Array<string | null | undefined>): string | null {
  const parts = values.map((value) => (value ?? '').trim()).filter(Boolean)
  return parts.length ? parts.join(' · ') : null
}

function truncateText(value: string | null, maxLength: number): string | null {
  if (!value) {
    return null
  }
  return value.length <= maxLength ? value : `${value.slice(0, maxLength - 1).trimEnd()}…`
}
