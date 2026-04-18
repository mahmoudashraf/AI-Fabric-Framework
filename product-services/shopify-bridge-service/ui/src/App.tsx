import { useEffect, useState } from 'react'
import {
  AppProvider,
  Badge,
  Banner,
  BlockStack,
  Box,
  Button,
  Card,
  Divider,
  InlineStack,
  List,
  Page,
  Text,
} from '@shopify/polaris'
import enTranslations from '@shopify/polaris/locales/en.json'
import {
  bootstrapStore,
  connectStore,
  fetchSession,
  fetchShell,
  type ShopifyBridgeMerchantSessionResponse,
  type ShopifyBridgeShellResponse,
  type ShopifyBridgeStoreBootstrapResponse,
  type ShopifyBridgeStoreSummary,
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

type LoadState = {
  shell: ShopifyBridgeShellResponse | null
  session: ShopifyBridgeMerchantSessionResponse | null
  loading: boolean
  error: string | null
}

export default function App() {
  const [state, setState] = useState<LoadState>({
    shell: null,
    session: null,
    loading: true,
    error: null,
  })
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [busyAction, setBusyAction] = useState<'connect' | 'bootstrap' | null>(null)

  useEffect(() => {
    void refresh()
  }, [])

  async function refresh() {
    setState((current) => ({ ...current, loading: true, error: null }))
    try {
      const shell = await fetchShell()
      let session: ShopifyBridgeMerchantSessionResponse | null = null
      try {
        session = await fetchSession()
      } catch (sessionError) {
        session = null
        setState({
          shell,
          session: null,
          loading: false,
          error: sessionError instanceof Error ? sessionError.message : 'Failed to resolve merchant session.',
        })
        return
      }
      setState({
        shell,
        session,
        loading: false,
        error: null,
      })
    } catch (error) {
      setState({
        shell: null,
        session: null,
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

  function applyBootstrapResult(result: ShopifyBridgeStoreBootstrapResponse) {
    setState((current) => ({
      ...current,
      session: current.session ? { ...current.session, store: result.store } : current.session,
    }))
  }

  const shell = state.shell
  const session = state.session
  const store = session?.store ?? null

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
                    <List type="bullet">
                      <List.Item>Shop: {session.shopDomain}</List.Item>
                      <List.Item>User: {session.userId}</List.Item>
                      <List.Item>Expires: {new Date(session.expiresAt).toLocaleString()}</List.Item>
                      <List.Item>Install record: {session.installRecord?.status ?? 'MISSING'}</List.Item>
                      <List.Item>Credential refs: {session.installRecord?.accessTokenSecretRef ? 'present' : 'missing'}</List.Item>
                    </List>
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
          </InlineStack>

          <InlineStack gap="400" blockAlign="start" align="start">
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
                Connect the current shop first. Bootstrap creates the platform customer, deployment, and consumer binding for this merchant.
              </Text>
              <Divider />
              <InlineStack gap="300" align="start">
                <Button
                  variant="primary"
                  onClick={() => void handleConnect()}
                  loading={busyAction === 'connect'}
                  disabled={!session}
                >
                  Connect current shop
                </Button>
                <Button
                  onClick={() => void handleBootstrap()}
                  loading={busyAction === 'bootstrap'}
                  disabled={!session}
                >
                  Bootstrap deployment
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
      <List type="bullet">
        <List.Item>Install: {store.installStatus}</List.Item>
        <List.Item>Data sync: {store.syncStatus}</List.Item>
        <List.Item>Source readiness: {store.sourceReadinessStatus}</List.Item>
        <List.Item>Widget: {store.widgetStatus}</List.Item>
        <List.Item>Credentials: {store.credentials?.status ?? 'MISSING'}</List.Item>
        <List.Item>Deployment: {store.deploymentName ?? '—'} ({store.deploymentStatus ?? '—'})</List.Item>
        <List.Item>Consumer: {store.consumerId ?? '—'}</List.Item>
      </List>
      {store.credentials ? (
        <Text as="p" variant="bodySm" tone="subdued">
          Token refs {store.credentials.accessTokenPresent ? 'ready' : 'missing'} / {store.credentials.refreshTokenPresent ? 'refresh ready' : 'refresh missing'} · Scope {store.credentials.scopesText ?? '—'}
        </Text>
      ) : null}
      <Text as="p" variant="bodySm" tone="subdued">
        Sources: products {store.productsEnabled ? 'on' : 'off'}, collections {store.collectionsEnabled ? 'on' : 'off'}, pages {store.pagesEnabled ? 'on' : 'off'}, policies {store.policiesEnabled ? 'on' : 'off'}
      </Text>
    </BlockStack>
  )
}
