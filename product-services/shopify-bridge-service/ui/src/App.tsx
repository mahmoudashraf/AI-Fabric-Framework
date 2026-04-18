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
  List,
  Page,
  Text,
  TextField,
} from '@shopify/polaris'
import enTranslations from '@shopify/polaris/locales/en.json'
import {
  bootstrapStore,
  fetchBillingSummary,
  connectStore,
  fetchUsageSummary,
  fetchStorefrontPreview,
  fetchSession,
  fetchShell,
  goLiveStore,
  queryMerchantPlayground,
  runSourcePreflight,
  suggestMerchantPlayground,
  syncNowStore,
  updateSourceSettings,
  updateWidgetSettings,
  type ShopifyBridgeMerchantSessionResponse,
  type ShopifyBridgeBillingSummary,
  type ShopifyBridgeShellResponse,
  type ShopifyBridgeStoreBootstrapResponse,
  type ShopifyBridgeStoreSummary,
  type ShopifyBridgeUsageSummary,
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
  loading: boolean
  error: string | null
}

type PlaygroundMessage = {
  role: 'assistant' | 'user'
  content: string
}

export default function App() {
  const [state, setState] = useState<LoadState>({
    shell: null,
    session: null,
    storefrontPreview: null,
    usageSummary: null,
    billingSummary: null,
    loading: true,
    error: null,
  })
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [busyAction, setBusyAction] = useState<'connect' | 'preflight' | 'bootstrap' | 'sync' | 'go-live' | 'source-settings' | null>(null)
  const [widgetSettings, setWidgetSettings] = useState({
    launcherLabel: 'Ask the store assistant',
    welcomeMessage: 'Store assistant is ready. Ask about products, policies, or collections.',
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

  async function refresh() {
    setState((current) => ({ ...current, loading: true, error: null }))
    try {
      const shell = await fetchShell()
      let session: ShopifyBridgeMerchantSessionResponse | null = null
      let storefrontPreview: ShopifyStorefrontPreviewResponse | null = null
      let usageSummary: ShopifyBridgeUsageSummary | null = null
      let billingSummary: ShopifyBridgeBillingSummary | null = null
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
          loading: false,
          error: sessionError instanceof Error ? sessionError.message : 'Failed to resolve merchant session.',
        })
        return
      }
      setState({
        shell,
        session,
        storefrontPreview,
        usageSummary,
        billingSummary,
        loading: false,
        error: null,
      })
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
        })
      }
    } catch (error) {
      setState({
        shell: null,
        session: null,
        storefrontPreview: null,
        usageSummary: null,
        billingSummary: null,
        loading: false,
        error: error instanceof Error ? error.message : 'Unknown bridge shell failure.',
      })
    }
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
      setActionMessage(`Updated source categories for ${store.shopDomain}. Run source preflight again before go-live.`)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to update source settings.')
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
      setPlaygroundMessages((current) => [...current, { role: 'assistant', content: assistantMessage }])
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
  const supportBundleText = buildSupportBundle(shell, session, storefrontPreview, usageSummary, billingSummary)
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
        'Store assistant is ready. Ask about products, policies, or collections.') !== widgetSettings.welcomeMessage)

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
                    <Text as="p" variant="bodySm" tone={billingSummary.launchBlocked ? 'critical' : 'subdued'}>
                      Billing {billingSummary.mode} / {billingSummary.status} · {billingSummary.message}
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
                    Companion owns bounded launcher content. Theme settings still enable the embed, but the launcher label and first assistant message come from this app.
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
                          <Text as="p" variant="bodyMd">
                            <strong>{message.role === 'assistant' ? 'Assistant' : 'You'}:</strong> {message.content}
                          </Text>
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
                  {billingSummary?.message ?? 'Billing setup is incomplete. Shopify Companion go-live is blocked until billing is configured.'}
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
          {store.widgetDetail.settings.welcomeMessage ?? 'Store assistant is ready. Ask about products, policies, or collections.'}
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
  billingSummary: ShopifyBridgeBillingSummary | null
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
