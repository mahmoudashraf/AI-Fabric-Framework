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
  Text,
  TextField,
} from '@shopify/polaris'
import enTranslations from '@shopify/polaris/locales/en.json'
import {
  bootstrapStore,
  connectStore,
  fetchBillingSummary,
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
  retryLastFailedVectorizationAutoRunStore,
  runSourcePreflight,
  suggestMerchantPlayground,
  syncNowStore,
  updateSourceSettings,
  updateVectorizationPolicyStore,
  updateWidgetSettings,
  vectorizeNowStore,
  type ShopifyBridgeBillingApprovalResponse,
  type ShopifyBridgeBillingSummary,
  type ShopifyBridgeMerchantSessionResponse,
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
      return 'success'
    case 'PLATFORM_BOOTSTRAPPED':
    case 'CONNECTED':
    case 'NOT_SYNCED':
    case 'NOT_RUN':
    case 'NOT_ENABLED':
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
  billingSummary: ShopifyBridgeBillingSummary | null
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null
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

const UPDATE_TRIGGER_OPTIONS = [
  { label: 'Disabled', value: 'NONE' },
  { label: 'Any update', value: 'ANY_UPDATE' },
  { label: 'Indexed fields only', value: 'INDEXED_FIELDS_ONLY' },
  { label: 'Selected indexed fields', value: 'SELECTED_INDEXED_FIELDS' },
]

const DEFAULT_WIDGET_SURFACES = [
  'ai-search',
  'contextual-pill',
  'product-insight',
  'policy-strip',
  'product-faq',
  'comparison',
]

const WIDGET_SURFACE_OPTIONS = [
  { label: 'AI search dock', value: 'ai-search' },
  { label: 'Contextual pill', value: 'contextual-pill' },
  { label: 'Product insight card', value: 'product-insight' },
  { label: 'Policy strip', value: 'policy-strip' },
  { label: 'FAQ prompts', value: 'product-faq' },
  { label: 'Comparison prompts', value: 'comparison' },
]

const SHELL_MODE_PROFILE_OPTIONS = [
  { label: 'Shopify Companion', value: 'SHOPIFY_COMPANION' },
  { label: 'Guided Commerce', value: 'GUIDED_COMMERCE' },
  { label: 'Guided Support', value: 'GUIDED_SUPPORT' },
]

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
    billingSummary: null,
    webhookSubscriptions: null,
    vectorizationSummary: null,
    loading: true,
    error: null,
  })
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
  const [widgetSettings, setWidgetSettings] = useState({
    launcherLabel: 'Ask the store assistant',
    welcomeMessage: 'Store assistant is ready. Ask about products, policies, or collections.',
    shellModeProfile: 'SHOPIFY_COMPANION',
    enabledSurfaces: DEFAULT_WIDGET_SURFACES,
  })
  const [busyWidgetSettings, setBusyWidgetSettings] = useState(false)
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
  })
  const [selectedReindexEntityTypes, setSelectedReindexEntityTypes] = useState<string[]>([])
  const [vectorizationPolicyDraft, setVectorizationPolicyDraft] = useState<VectorizationPolicyDraft | null>(null)

  useEffect(() => {
    void refresh()
  }, [])

  useEffect(() => {
    if (!isReleaseInProgress(state.session?.store?.latestRelease?.status)) {
      return undefined
    }
    const timer = window.setTimeout(() => {
      void refresh()
    }, 5000)
    return () => window.clearTimeout(timer)
  }, [state.session?.store?.latestRelease?.status])

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
    setState((current) => ({ ...current, loading: true, error: null }))
    try {
      const shell = await fetchShell()
      let session: ShopifyBridgeMerchantSessionResponse | null = null
      let storefrontPreview: ShopifyStorefrontPreviewResponse | null = null
      let usageSummary: ShopifyBridgeUsageSummary | null = null
      let billingSummary: ShopifyBridgeBillingSummary | null = null
      let webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null = null
      let vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null = null
      try {
        session = await fetchSession()
        storefrontPreview = await fetchStorefrontPreview()
        usageSummary = await fetchUsageSummary()
        billingSummary = await fetchBillingSummary()
      } catch (sessionError) {
        session = null
        setState({
          shell,
          session: null,
          storefrontPreview: null,
          usageSummary: null,
          billingSummary: null,
          webhookSubscriptions: null,
          vectorizationSummary: null,
          loading: false,
          error: sessionError instanceof Error ? sessionError.message : 'Failed to resolve merchant session.',
        })
        return
      }
      try {
        webhookSubscriptions = await fetchWebhookSubscriptions()
      } catch {
        webhookSubscriptions = null
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
        billingSummary,
        webhookSubscriptions,
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
        })
        setWidgetSettings({
          launcherLabel: session.store.widgetDetail?.settings?.launcherLabel ?? 'Ask the store assistant',
          welcomeMessage:
            session.store.widgetDetail?.settings?.welcomeMessage ??
            'Store assistant is ready. Ask about products, policies, or collections.',
          shellModeProfile: session.store.widgetDetail?.settings?.shellModeProfile ?? 'SHOPIFY_COMPANION',
          enabledSurfaces:
            session.store.widgetDetail?.settings?.enabledSurfaces?.length
              ? [...session.store.widgetDetail.settings.enabledSurfaces]
              : [...DEFAULT_WIDGET_SURFACES],
        })
      }
    } catch (error) {
      setState({
        shell: null,
        session: null,
        storefrontPreview: null,
        usageSummary: null,
        billingSummary: null,
        webhookSubscriptions: null,
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

  async function handleBillingApproval() {
    setBusyAction('billing-approval')
    setActionError(null)
    setActionMessage(null)
    try {
      const response = await requestBillingApproval()
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
      setActionMessage(`Synced enabled Shopify content into the platform runtime for ${store.shopDomain}.`)
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
      setActionMessage(`Updated source categories for ${store.shopDomain}. Deployment vectorization support has been reconciled for the current selection.`)
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
      setActionMessage(`Reconciled deployment vectorization support for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to reconcile deployment vectorization support.')
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
      setActionMessage(`Queued deployment vectorization for ${vectorizationSummary.shopDomain}.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to trigger Shopify vectorization.')
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
  const billingSummary = state.billingSummary
  const billingApprovalRequired = Boolean(
    billingSummary?.merchantApprovalRequired && billingSummary?.status === 'READY_FOR_APPROVAL',
  )
  const webhookSubscriptions = state.webhookSubscriptions
  const vectorizationSummary = state.vectorizationSummary
  const supportBundleText = buildSupportBundle(
    shell,
    session,
    storefrontPreview,
    usageSummary,
    billingSummary,
    webhookSubscriptions,
    vectorizationSummary,
  )
  const installRecoveryRequired = Boolean(session?.installRecoveryRequired)
  const installRecoveryUrl = session?.installRecoveryUrl ?? null
  const billingLaunchBlocked = Boolean(billingSummary?.launchBlocked)
  const canGoLive =
    Boolean(session) &&
    Boolean(store) &&
    !installRecoveryRequired &&
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
      store.policiesEnabled !== sourceSettings.policiesEnabled)
  const widgetSettingsDirty =
    !!store &&
    ((store.widgetDetail?.settings?.launcherLabel ?? 'Ask the store assistant') !== widgetSettings.launcherLabel ||
      (store.widgetDetail?.settings?.welcomeMessage ??
        'Store assistant is ready. Ask about products, policies, or collections.') !== widgetSettings.welcomeMessage ||
      (store.widgetDetail?.settings?.shellModeProfile ?? 'SHOPIFY_COMPANION') !== widgetSettings.shellModeProfile ||
      JSON.stringify(store.widgetDetail?.settings?.enabledSurfaces ?? DEFAULT_WIDGET_SURFACES) !==
        JSON.stringify(widgetSettings.enabledSurfaces))

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

  return (
    <AppProvider i18n={enTranslations}>
      <Page
        title={shell?.appName ?? 'Shopify Bridge Service'}
        subtitle="Merchant-facing shell for onboarding, source readiness, sync, and storefront enablement."
        primaryAction={{ content: 'Refresh', onAction: () => void refresh() }}
      >
        <BlockStack gap="400">
          {state.error ? <Banner tone="critical">{state.error}</Banner> : null}
          {actionError ? <Banner tone="critical">{actionError}</Banner> : null}
          {actionMessage ? <Banner tone="success">{actionMessage}</Banner> : null}
          {state.loading ? <Banner tone="info">Loading Shopify Bridge shell…</Banner> : null}

          <Card>
            <BlockStack gap="300">
              <InlineStack align="space-between">
                <Text as="h2" variant="headingMd">
                  Bridge status
                </Text>
                <Badge tone={badgeTone(shell?.status ?? 'UNKNOWN')}>{shell?.status ?? 'UNKNOWN'}</Badge>
              </InlineStack>
              <Text as="p" variant="bodyMd" tone="subdued">
                Service ref: {shell?.serviceRef ?? '—'} · Environment: {shell?.environmentScope ?? '—'}
              </Text>
              <Text as="p" variant="bodyMd">
                Merchant traffic now depends on Shopify session tokens. This shell only drives one bounded store lifecycle instead of exposing raw platform composition.
              </Text>
              <Text as="p" variant="bodyMd" tone={shell?.merchantSessionAuthConfigured ? 'success' : 'critical'}>
                Merchant session auth configured: {shell?.merchantSessionAuthConfigured ? 'yes' : 'no'}
              </Text>
            </BlockStack>
          </Card>

          <InlineStack gap="400" blockAlign="start" align="start">
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
                        <List.Item>User: {session.userId}</List.Item>
                        <List.Item>Expires: {new Date(session.expiresAt).toLocaleString()}</List.Item>
                        <List.Item>Install record: {session.installRecord?.status ?? 'MISSING'}</List.Item>
                        <List.Item>Credential refs: {session.installRecord?.accessTokenSecretRef ? 'present' : 'missing'}</List.Item>
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
                    </BlockStack>
                  ) : (
                    <Text as="p" variant="bodyMd" tone="subdued">
                      No authenticated Shopify merchant session is available yet.
                    </Text>
                  )}
                </BlockStack>
              </Card>
            </Box>

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
                      <List.Item>Billing status: {billingSummary?.status ?? 'UNKNOWN'}</List.Item>
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
                        Billing {billingSummary.mode} / {billingSummary.status} · {billingSummary.message}
                      </Text>
                      {billingApprovalRequired ? (
                        <InlineStack gap="200">
                          <Button
                            variant="primary"
                            onClick={() => void handleBillingApproval()}
                            loading={busyAction === 'billing-approval'}
                          >
                            Approve Shopify billing
                          </Button>
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
                    </BlockStack>
                  ) : null}
                  <TextField
                    label="Support bundle"
                    autoComplete="off"
                    multiline={12}
                    value={supportBundleText}
                    readOnly
                  />
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
          </InlineStack>

          <InlineStack gap="400" blockAlign="start" align="start">
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
                    helpText="This controls how the storefront surfaces phrase prompts and shopper guidance."
                  />
                  <BlockStack gap="150">
                    <Text as="p" variant="bodySm" tone="subdued">
                      Embedded surfaces
                    </Text>
                    {WIDGET_SURFACE_OPTIONS.map((surface) => (
                      <Checkbox
                        key={surface.value}
                        label={surface.label}
                        checked={widgetSettings.enabledSurfaces.includes(surface.value)}
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

            <Box minWidth="360px">
              <Card>
                <BlockStack gap="300">
                  <Text as="h2" variant="headingMd">
                    Merchant playground
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    Test the live Companion behavior from the embedded app using the same bridge-backed runtime contract as the storefront widget.
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
          </InlineStack>

          <Card>
            <BlockStack gap="300">
              <Text as="h2" variant="headingMd">
                Next actions
              </Text>
              <Text as="p" variant="bodyMd" tone="subdued">
                Connect the current shop first. Run source preflight before bootstrapping so the platform can gate apply-time sync on real Shopify source reachability. After bootstrap, use sync now to push enabled Shopify content into the bound deployment runtime. Go live only after the source readiness checks are clean.
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
              {billingLaunchBlocked ? (
                <Banner tone="critical">
                  <BlockStack gap="200">
                    <Text as="p" variant="bodyMd">
                      {billingSummary?.message ?? 'Billing setup is incomplete. Shopify Companion go-live is blocked until billing is configured.'}
                    </Text>
                    {billingApprovalRequired ? (
                      <InlineStack gap="200">
                        <Button
                          variant="primary"
                          onClick={() => void handleBillingApproval()}
                          loading={busyAction === 'billing-approval'}
                        >
                          Approve Shopify billing
                        </Button>
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
        <List.Item>Credentials: {store.credentials?.status ?? 'MISSING'}</List.Item>
        <List.Item>Deployment: {store.deploymentName ?? '—'} ({store.deploymentStatus ?? '—'})</List.Item>
        <List.Item>Latest version: {store.latestVersion?.versionLabel ?? '—'} ({store.latestVersion?.status ?? '—'})</List.Item>
        <List.Item>Latest release: {store.latestRelease?.status ?? '—'} / verification {store.latestRelease?.verificationStatus ?? '—'}</List.Item>
        <List.Item>Consumer: {store.consumerId ?? '—'}</List.Item>
      </List>
      {store.credentials ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Token refs {store.credentials.accessTokenPresent ? 'ready' : 'missing'} / {store.credentials.refreshTokenPresent ? 'refresh ready' : 'refresh missing'} · Scope {store.credentials.scopesText ?? '—'}
        </Text>
      ) : null}
      {store.sourcePreflight ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Preflight {store.sourcePreflight.overallStatus} ·{' '}
          {store.sourcePreflight.categories
            .map((category) => `${category.category} ${category.status.toLowerCase()} (${category.itemCount})`)
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
      {store.capabilities ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Capabilities: {store.capabilities.actionCount} actions, {store.capabilities.knowledgeSourceCount} knowledge sources, {store.capabilities.marketplaceDatasetCount} datasets, {store.capabilities.shellModuleCount} shell modules
        </Text>
      ) : null}
      {store.syncDetail ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Sync {store.syncDetail.status} · mode {store.syncDetail.mode ?? '—'} · documents {store.syncDetail.documentCount}
          {store.syncDetail.message ? ` · ${store.syncDetail.message}` : ''}
        </Text>
      ) : null}
      {store.webhookDetail ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Last webhook {store.webhookDetail.topic ?? '—'} · event {store.webhookDetail.eventType ?? '—'} · source {store.webhookDetail.sourceCategory ?? '—'} · received{' '}
          {store.webhookDetail.receivedAt ? new Date(store.webhookDetail.receivedAt).toLocaleString() : '—'}
          {store.webhookDetail.invalidateSync ? ' · sync invalidated' : ''}
          {store.webhookDetail.message ? ` · ${store.webhookDetail.message}` : ''}
        </Text>
      ) : null}
      {store.latestRelease ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Release step {store.latestRelease.currentStepKey ?? '—'} · provisioning {store.latestRelease.provisioningStatus} · updated{' '}
          {store.latestRelease.updatedAt ? new Date(store.latestRelease.updatedAt).toLocaleString() : '—'}
          {store.latestRelease.errorMessage ? ` · ${store.latestRelease.errorMessage}` : ''}
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
        Sources: products {store.productsEnabled ? 'on' : 'off'}, collections {store.collectionsEnabled ? 'on' : 'off'}, pages {store.pagesEnabled ? 'on' : 'off'}, policies {store.policiesEnabled ? 'on' : 'off'}
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

function buildSupportBundle(
  shell: ShopifyBridgeShellResponse | null,
  session: ShopifyBridgeMerchantSessionResponse | null,
  storefrontPreview: ShopifyStorefrontPreviewResponse | null,
  usageSummary: ShopifyBridgeUsageSummary | null,
  billingSummary: ShopifyBridgeBillingSummary | null,
  webhookSubscriptions: ShopifyWebhookSubscriptionStatusSummary | null,
  vectorizationSummary: ShopifyBridgeStoreVectorizationSummary | null
): string {
  const store = session?.store ?? null
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
            activationSteps: storefrontPreview.activationSteps,
            blockingReasons: storefrontPreview.blockingReasons,
          }
        : null,
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

function describeUsageEvent(eventType: string): string {
  const normalized = (eventType || '').trim().toUpperCase()
  switch (normalized) {
    case 'MERCHANT_CONNECT':
      return 'merchant connect'
    case 'MERCHANT_SOURCE_PREFLIGHT':
      return 'merchant source preflight'
    case 'MERCHANT_BOOTSTRAP':
      return 'merchant bootstrap'
    case 'MERCHANT_GO_LIVE':
      return 'merchant go-live'
    case 'MERCHANT_SYNC_NOW':
      return 'merchant sync'
    case 'MERCHANT_WIDGET_SETTINGS_UPDATED':
      return 'widget settings updates'
    case 'MERCHANT_SOURCE_SETTINGS_UPDATED':
      return 'source setting updates'
    case 'MERCHANT_VECTORIZATION_INDEX_ALL':
      return 'index all requests'
    case 'MERCHANT_VECTORIZATION_REINDEX_ALL':
      return 'reindex all requests'
    case 'MERCHANT_VECTORIZATION_REINDEX_SELECTED':
      return 'reindex selected requests'
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
