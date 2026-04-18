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
import { fetchShell, type ShopifyBridgeShellResponse } from './api'

function badgeTone(status: string): 'success' | 'attention' | 'critical' {
  switch (status) {
    case 'READY_FOR_ONBOARDING':
      return 'success'
    case 'SETUP_REQUIRED':
      return 'attention'
    default:
      return 'critical'
  }
}

export default function App() {
  const [shell, setShell] = useState<ShopifyBridgeShellResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchShell()
      .then((payload) => {
        if (!cancelled) {
          setShell(payload)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Unknown bridge shell failure.')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <AppProvider i18n={enTranslations}>
      <Page
        title={shell?.appName ?? 'Shopify Bridge Service'}
        subtitle="Merchant-facing shell for onboarding, source readiness, sync, and storefront enablement."
        primaryAction={{ content: 'Refresh shell', onAction: () => window.location.reload() }}
      >
        <BlockStack gap="400">
          {error ? <Banner tone="critical">{error}</Banner> : null}
          {loading ? <Banner tone="info">Loading Shopify Bridge shell…</Banner> : null}

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
                This shell is intentionally opinionated. It exists to onboard merchants into one controlled product flow rather than exposing raw platform configuration.
              </Text>
            </BlockStack>
          </Card>

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
                Current shell boundaries
              </Text>
              <Text as="p" variant="bodyMd" tone="subdued">
                Merchant UI chooses per-store usage and sync controls. Product capabilities still come from the platform bundle, and infrastructure remains managed from the platform operator UI.
              </Text>
              <Divider />
              <InlineStack gap="300" align="start">
                <Button variant="primary">Continue onboarding</Button>
                <Button disabled>Open source preflight</Button>
                <Button disabled>Enable storefront widget</Button>
              </InlineStack>
            </BlockStack>
          </Card>
        </BlockStack>
      </Page>
    </AppProvider>
  )
}
