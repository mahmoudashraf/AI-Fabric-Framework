import AddRoundedIcon from '@mui/icons-material/AddRounded'
import HubRoundedIcon from '@mui/icons-material/HubRounded'
import KeyRoundedIcon from '@mui/icons-material/KeyRounded'
import PrecisionManufacturingRoundedIcon from '@mui/icons-material/PrecisionManufacturingRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  createProductService,
  decommissionProductService,
  fetchDeploymentTargetProfiles,
  fetchProductServiceDeploymentHistory,
  fetchProductService,
  fetchProductServiceActivity,
  fetchProductServiceDependents,
  fetchProductServiceHealth,
  fetchProductServiceOverview,
  fetchProductServiceRailwayLogs,
  fetchProductServiceStoreBinding,
  fetchProductServiceStoreBillingSummary,
  fetchProductServiceWebhookSubscriptions,
  fetchProductServices,
  forceRecreateProductService,
  reconcileProductService,
  restartProductService,
  rotateProductServiceSecret,
  scaleProductService,
  updateProductServiceShopifyBillingConfig,
  type CreatePlatformManagedProductServiceRequest,
  type DeploymentTargetProfileSummary,
  type PlatformAuditEventSummary,
  type PlatformManagedProductServiceBillingSummary,
  type PlatformManagedProductServiceHealthSummary,
  type PlatformManagedProductServiceOverviewSummary,
  type PlatformManagedProductServiceProbeSummary,
  type PlatformManagedProductServiceShopifyBillingConfig,
  type PlatformManagedProductServiceSummary,
  type RailwayLogEntrySummary,
  type ShopifyStoreConnectionSummary,
} from '../api/platformApi'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function chipColor(value: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch ((value ?? '').toUpperCase()) {
    case 'ACTIVE':
    case 'READY':
    case 'NO_DRIFT':
    case 'INSTALLED':
    case 'SYNCED':
    case 'LIVE':
    case 'PREFLIGHT_READY':
    case 'APPLIED_VERIFIED':
    case 'PASSED':
      return 'success'
    case 'FAILED':
    case 'BLOCKED':
    case 'RAILWAY_LINKAGE_MISSING':
    case 'SECRET_DRIFT':
    case 'BASE_URL_MISSING':
      return 'error'
    case 'PROVISIONING':
    case 'CREATED':
    case 'DEGRADED':
    case 'NOT_SYNCED':
    case 'NOT_RUN':
    case 'NOT_STARTED':
    case 'INSTALL_IDENTITY_READY':
    case 'PLATFORM_BOOTSTRAPPED':
    case 'GO_LIVE_REQUESTED':
    case 'APPLY_REQUESTED':
    case 'PENDING':
    case 'QUEUED':
      return 'warning'
    default:
      return 'default'
  }
}

function detailValue(value: string | number | null | undefined): string {
  if (value == null) {
    return '—'
  }
  const stringValue = `${value}`.trim()
  return stringValue.length > 0 ? stringValue : '—'
}

function billingSeverity(billing: PlatformManagedProductServiceBillingSummary | null | undefined): 'info' | 'warning' | 'error' {
  if (!billing) {
    return 'info'
  }
  if (billing.launchBlocked) {
    return billing.status?.toUpperCase() === 'PAYMENT_ISSUE' ? 'error' : 'warning'
  }
  return 'info'
}

function targetProfileLabel(profile: DeploymentTargetProfileSummary): string {
  const flags = [
    profile.defaultForRestartableServices ? 'restartable default' : null,
    profile.defaultForRuntime ? 'runtime default' : null,
  ].filter(Boolean)
  return `${profile.name} (${profile.id}, ${profile.environmentName})${flags.length > 0 ? ` - ${flags.join(', ')}` : ''}`
}

function usageBreakdownLabel(eventType: string): string {
  const normalized = eventType.trim().toUpperCase()
  switch (normalized) {
    case 'MERCHANT_CONNECT':
      return 'Merchant connect'
    case 'MERCHANT_SOURCE_PREFLIGHT':
      return 'Source preflight'
    case 'MERCHANT_BOOTSTRAP':
      return 'Platform bootstrap'
    case 'MERCHANT_SYNC_NOW':
      return 'Sync now'
    case 'MERCHANT_GO_LIVE':
      return 'Go live'
    case 'MERCHANT_WIDGET_SETTINGS_UPDATED':
      return 'Widget settings update'
    case 'MERCHANT_SOURCE_SETTINGS_UPDATED':
      return 'Source settings update'
    case 'MERCHANT_PLAYGROUND_QUERY':
      return 'Merchant playground query'
    case 'MERCHANT_PLAYGROUND_SUGGESTIONS':
      return 'Merchant playground suggestions'
    case 'STOREFRONT_WIDGET_OPENED_HOME_PAGE':
      return 'Storefront widget opened (home)'
    case 'STOREFRONT_WIDGET_OPENED_PRODUCT_PAGE':
      return 'Storefront widget opened (product)'
    case 'STOREFRONT_WIDGET_OPENED_COLLECTION_PAGE':
      return 'Storefront widget opened (collection)'
    case 'STOREFRONT_WIDGET_OPENED_CONTENT_PAGE':
      return 'Storefront widget opened (content)'
    case 'STOREFRONT_WIDGET_OPENED_GENERIC_PAGE':
      return 'Storefront widget opened (generic)'
    case 'STOREFRONT_SUGGESTION_CLICKED':
      return 'Storefront suggestion clicked'
    case 'STOREFRONT_CHAT_RESET':
      return 'Storefront chat reset'
    default:
      return normalized
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ')
  }
}

function probeCard(probe: PlatformManagedProductServiceProbeSummary | null | undefined) {
  if (!probe) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Typography color="text.secondary">No probe data available.</Typography>
        </CardContent>
      </Card>
    )
  }
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography sx={{ fontWeight: 700 }}>Health probe</Typography>
            <Chip size="small" label={probe.status} color={chipColor(probe.status)} />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {probe.method} {detailValue(probe.endpoint)}
          </Typography>
          <Typography variant="body2">{probe.message}</Typography>
          <Typography variant="caption" color="text.secondary">
            Checked {formatTimestamp(probe.checkedAt)}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  )
}

function activitySummary(event: PlatformAuditEventSummary): string {
  const details = event.details
  if (details && typeof details === 'object' && !Array.isArray(details)) {
    const record = details as Record<string, unknown>
    const pieces = ['status', 'serviceRef', 'deploymentId', 'secretName', 'error']
      .map((key) => (typeof record[key] === 'string' && record[key] ? `${key}=${record[key] as string}` : null))
      .filter((value): value is string => value != null)
    if (pieces.length > 0) {
      return pieces.join(' · ')
    }
  }
  return 'No extra details.'
}

function railwaySeverityColor(entry: RailwayLogEntrySummary): 'default' | 'error' | 'warning' | 'info' | 'success' {
  const severity = (entry.severity ?? '').toUpperCase()
  if (severity.includes('ERROR')) {
    return 'error'
  }
  if (severity.includes('WARN')) {
    return 'warning'
  }
  if (severity.includes('INFO')) {
    return 'info'
  }
  if (severity.includes('DEBUG') || severity.includes('TRACE')) {
    return 'default'
  }
  return 'success'
}

type ProductServiceFormState = CreatePlatformManagedProductServiceRequest

const defaultShopifyBillingConfig: PlatformManagedProductServiceShopifyBillingConfig = {
  mode: 'FREE',
  starterEnabled: false,
  starterPlanName: 'Loom Companion Starter',
  starterPlanHandle: 'loom-companion-starter',
  starterAmount: '29.00',
  starterCurrencyCode: 'USD',
  starterInterval: 'EVERY_30_DAYS',
  starterTrialDays: 7,
  starterTest: true,
  eliteEnabled: false,
  elitePlanName: 'Loom Companion Elite',
  elitePlanHandle: 'loom-companion-elite',
  eliteAmount: '179.00',
  eliteCurrencyCode: 'USD',
  eliteTrialDays: 0,
  eliteInterval: 'EVERY_30_DAYS',
  eliteTest: true,
}

const emptyForm: ProductServiceFormState = {
  serviceRef: '',
  displayName: '',
  productFamily: 'SHOPIFY',
  serviceKind: 'SHOPIFY_BRIDGE_SERVICE',
  deploymentMode: 'SHARED_PLATFORM_SERVICE',
  tenantMode: 'MULTI_TENANT_SHARED',
  environmentScope: 'dev',
  deploymentId: null,
  desiredReplicas: 1,
  minReplicas: 1,
  maxReplicas: 3,
  baseUrl: null,
  healthPath: '/actuator/health',
  serviceRoot: 'product-services/shopify-bridge-service',
  dockerfilePath: 'product-services/shopify-bridge-service/deploy/railway/Dockerfile',
  secretName: null,
  targetProfileId: null,
  shopifyBillingConfig: defaultShopifyBillingConfig,
}

const productServicePresets: Record<string, Partial<ProductServiceFormState>> = {
  SHOPIFY_BRIDGE_SERVICE: {
    productFamily: 'SHOPIFY',
    serviceKind: 'SHOPIFY_BRIDGE_SERVICE',
    serviceRoot: 'product-services/shopify-bridge-service',
    dockerfilePath: 'product-services/shopify-bridge-service/deploy/railway/Dockerfile',
    healthPath: '/actuator/health',
    shopifyBillingConfig: defaultShopifyBillingConfig,
  },
  MCP_EXECUTION_GATEWAY_SERVICE: {
    serviceRef: 'mcp-execution-gateway',
    displayName: 'MCP Execution Gateway',
    productFamily: 'MCP',
    serviceKind: 'MCP_EXECUTION_GATEWAY_SERVICE',
    serviceRoot: 'product-services/mcp-execution-gateway-service',
    dockerfilePath: 'product-services/mcp-execution-gateway-service/deploy/railway/Dockerfile',
    healthPath: '/actuator/health',
    shopifyBillingConfig: null,
  },
}

function billingConfigOrDefault(
  value: PlatformManagedProductServiceShopifyBillingConfig | null | undefined,
): PlatformManagedProductServiceShopifyBillingConfig {
  return {
    ...defaultShopifyBillingConfig,
    ...(value ?? {}),
  }
}

function shopifyBillingConfigFields(
  config: PlatformManagedProductServiceShopifyBillingConfig,
  onChange: (patch: Partial<PlatformManagedProductServiceShopifyBillingConfig>) => void,
) {
  return (
    <Stack spacing={2}>
      <TextField select label="Billing mode" value={config.mode} onChange={(event) => onChange({ mode: event.target.value })} fullWidth>
        <MenuItem value="FREE">Free-only</MenuItem>
        <MenuItem value="SHOPIFY_APP_SUBSCRIPTION">Shopify app subscriptions</MenuItem>
      </TextField>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Starter"
            value={config.starterEnabled ? 'true' : 'false'}
            onChange={(event) => onChange({ starterEnabled: event.target.value === 'true' })}
            fullWidth
          >
            <MenuItem value="false">Disabled</MenuItem>
            <MenuItem value="true">Enabled</MenuItem>
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Elite"
            value={config.eliteEnabled ? 'true' : 'false'}
            onChange={(event) => onChange({ eliteEnabled: event.target.value === 'true' })}
            fullWidth
          >
            <MenuItem value="false">Disabled</MenuItem>
            <MenuItem value="true">Enabled</MenuItem>
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Test billing"
            value={config.starterTest && config.eliteTest ? 'true' : 'false'}
            onChange={(event) => {
              const enabled = event.target.value === 'true'
              onChange({ starterTest: enabled, eliteTest: enabled })
            }}
            fullWidth
          >
            <MenuItem value="true">Test subscriptions</MenuItem>
            <MenuItem value="false">Live subscriptions</MenuItem>
          </TextField>
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            label="Starter plan"
            value={config.starterPlanName}
            onChange={(event) => onChange({ starterPlanName: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Starter handle"
            value={config.starterPlanHandle}
            onChange={(event) => onChange({ starterPlanHandle: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <TextField
            label="Starter amount"
            value={config.starterAmount}
            onChange={(event) => onChange({ starterAmount: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <TextField
            select
            label="Currency"
            value={config.starterCurrencyCode}
            onChange={(event) => onChange({ starterCurrencyCode: event.target.value })}
            fullWidth
          >
            <MenuItem value="USD">USD</MenuItem>
            <MenuItem value="GBP">GBP</MenuItem>
            <MenuItem value="EUR">EUR</MenuItem>
          </TextField>
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Starter interval"
            value={config.starterInterval}
            onChange={(event) => onChange({ starterInterval: event.target.value })}
            fullWidth
          >
            <MenuItem value="EVERY_30_DAYS">Every 30 days</MenuItem>
            <MenuItem value="ANNUAL">Annual</MenuItem>
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Starter trial days"
            type="number"
            value={config.starterTrialDays}
            onChange={(event) => onChange({ starterTrialDays: Number(event.target.value) })}
            fullWidth
          />
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            label="Elite plan"
            value={config.elitePlanName}
            onChange={(event) => onChange({ elitePlanName: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Elite handle"
            value={config.elitePlanHandle}
            onChange={(event) => onChange({ elitePlanHandle: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <TextField
            label="Elite amount"
            value={config.eliteAmount}
            onChange={(event) => onChange({ eliteAmount: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <TextField
            select
            label="Currency"
            value={config.eliteCurrencyCode}
            onChange={(event) => onChange({ eliteCurrencyCode: event.target.value })}
            fullWidth
          >
            <MenuItem value="USD">USD</MenuItem>
            <MenuItem value="GBP">GBP</MenuItem>
            <MenuItem value="EUR">EUR</MenuItem>
          </TextField>
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Elite interval"
            value={config.eliteInterval}
            onChange={(event) => onChange({ eliteInterval: event.target.value })}
            fullWidth
          >
            <MenuItem value="EVERY_30_DAYS">Every 30 days</MenuItem>
            <MenuItem value="ANNUAL">Annual</MenuItem>
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Elite trial days"
            type="number"
            value={config.eliteTrialDays}
            onChange={(event) => onChange({ eliteTrialDays: Number(event.target.value) })}
            fullWidth
          />
        </Grid>
      </Grid>
    </Stack>
  )
}

export function ProductServicesPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [rotateDialogOpen, setRotateDialogOpen] = useState(false)
  const [shopifyBillingDialogOpen, setShopifyBillingDialogOpen] = useState(false)
  const [forceRecreateDialogOpen, setForceRecreateDialogOpen] = useState(false)
  const [decommissionDialogOpen, setDecommissionDialogOpen] = useState(false)
  const [webhookDialogStore, setWebhookDialogStore] = useState<ShopifyStoreConnectionSummary | null>(null)
  const [billingDialogStore, setBillingDialogStore] = useState<ShopifyStoreConnectionSummary | null>(null)
  const [bindingDialogStore, setBindingDialogStore] = useState<ShopifyStoreConnectionSummary | null>(null)
  const [deploymentHistoryDialogOpen, setDeploymentHistoryDialogOpen] = useState(false)
  const [selectedRailwayDeploymentId, setSelectedRailwayDeploymentId] = useState<string | null>(null)
  const [rotateSecretValue, setRotateSecretValue] = useState('')
  const [forceRecreateConfirmation, setForceRecreateConfirmation] = useState('')
  const [decommissionConfirmation, setDecommissionConfirmation] = useState('')
  const [scaleValue, setScaleValue] = useState('1')
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [form, setForm] = useState<ProductServiceFormState>(emptyForm)
  const [shopifyBillingDraft, setShopifyBillingDraft] =
    useState<PlatformManagedProductServiceShopifyBillingConfig>(defaultShopifyBillingConfig)

  const servicesQuery = useQuery({
    queryKey: ['product-services'],
    queryFn: fetchProductServices,
  })

  const targetProfilesQuery = useQuery({
    queryKey: ['deployment-target-profiles', 'COOLIFY', 'product-services'],
    queryFn: () => fetchDeploymentTargetProfiles('COOLIFY'),
  })

  const managedServiceTargetProfiles = useMemo(
    () => (targetProfilesQuery.data ?? []).filter((profile) => profile.active && profile.platformServicesAllowed),
    [targetProfilesQuery.data],
  )

  const selectedServiceRef = searchParams.get('service') ?? ''

  useEffect(() => {
    const services = servicesQuery.data ?? []
    if (services.length === 0) {
      return
    }
    const selectedStillVisible = services.some((service) => service.serviceRef === selectedServiceRef)
    if (!selectedStillVisible) {
      setSearchParams({ service: services[0].serviceRef }, { replace: true })
    }
  }, [selectedServiceRef, servicesQuery.data, setSearchParams])

  const selectedSummary = useMemo(
    () => (servicesQuery.data ?? []).find((service) => service.serviceRef === selectedServiceRef) ?? null,
    [selectedServiceRef, servicesQuery.data],
  )

  const selectedServiceQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef],
    queryFn: () => fetchProductService(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const selectedService = selectedServiceQuery.data ?? selectedSummary
  const decommissionBlocked = (selectedService?.dependentStoresCount ?? 0) > 0

  useEffect(() => {
    if (selectedService) {
      setScaleValue(`${selectedService.desiredReplicas ?? 1}`)
      setShopifyBillingDraft(billingConfigOrDefault(selectedService.shopifyBillingConfig))
    }
  }, [selectedService?.desiredReplicas, selectedService?.serviceRef, selectedService?.shopifyBillingConfig])

  const dependentsQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'dependents'],
    queryFn: () => fetchProductServiceDependents(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const activityQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'activity'],
    queryFn: () => fetchProductServiceActivity(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const healthQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'health'],
    queryFn: () => fetchProductServiceHealth(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const overviewQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'overview'],
    queryFn: () => fetchProductServiceOverview(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const webhookSubscriptionsQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'webhook-subscriptions', webhookDialogStore?.shopDomain ?? ''],
    queryFn: () => fetchProductServiceWebhookSubscriptions(selectedServiceRef, webhookDialogStore?.shopDomain ?? ''),
    enabled: selectedServiceRef.length > 0 && webhookDialogStore != null,
  })

  const storeBillingSummaryQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'store-billing-summary', billingDialogStore?.shopDomain ?? ''],
    queryFn: () => fetchProductServiceStoreBillingSummary(selectedServiceRef, billingDialogStore?.shopDomain ?? ''),
    enabled: selectedServiceRef.length > 0 && billingDialogStore != null,
  })

  const storeBindingQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'store-binding', bindingDialogStore?.shopDomain ?? ''],
    queryFn: () => fetchProductServiceStoreBinding(selectedServiceRef, bindingDialogStore?.shopDomain ?? ''),
    enabled: selectedServiceRef.length > 0 && bindingDialogStore != null,
  })

  const deploymentHistoryQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'deployment-history'],
    queryFn: () => fetchProductServiceDeploymentHistory(selectedServiceRef),
    enabled: selectedServiceRef.length > 0 && deploymentHistoryDialogOpen,
  })

  const railwayLogsQuery = useQuery({
    queryKey: ['product-services', selectedServiceRef, 'railway-logs', selectedRailwayDeploymentId ?? 'latest'],
    queryFn: () =>
      fetchProductServiceRailwayLogs({
        serviceRef: selectedServiceRef,
        source: 'deployment',
        deploymentId: selectedRailwayDeploymentId ?? undefined,
        limit: 100,
      }),
    enabled: selectedServiceRef.length > 0 && deploymentHistoryDialogOpen,
  })

  useEffect(() => {
    if (!deploymentHistoryDialogOpen) {
      return
    }
    const deployments = deploymentHistoryQuery.data?.deployments ?? []
    if (deployments.length === 0) {
      setSelectedRailwayDeploymentId(null)
      return
    }
    if (!selectedRailwayDeploymentId || !deployments.some((deployment) => deployment.id === selectedRailwayDeploymentId)) {
      setSelectedRailwayDeploymentId(deployments[0].id)
    }
  }, [deploymentHistoryDialogOpen, deploymentHistoryQuery.data?.deployments, selectedRailwayDeploymentId])

  const refreshSelected = async (serviceRef: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'dependents'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'activity'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'health'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'overview'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'webhook-subscriptions'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'store-billing-summary'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'store-binding'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'deployment-history'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'railway-logs'] }),
      queryClient.invalidateQueries({ queryKey: ['shopify-stores'] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: createProductService,
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Created product service ${service.displayName}.` })
      setCreateDialogOpen(false)
      setForm(emptyForm)
      setSearchParams({ service: service.serviceRef }, { replace: true })
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to create product service.' })
    },
  })

  const reconcileMutation = useMutation({
    mutationFn: (serviceRef: string) => reconcileProductService(serviceRef),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Reconciled ${service.displayName}.` })
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to reconcile product service.' }),
  })

  const scaleMutation = useMutation({
    mutationFn: ({ serviceRef, desiredReplicas }: { serviceRef: string; desiredReplicas: number }) =>
      scaleProductService(serviceRef, { desiredReplicas }),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Updated replica target for ${service.displayName}.` })
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to scale product service.' }),
  })

  const updateShopifyBillingMutation = useMutation({
    mutationFn: ({ serviceRef, payload }: { serviceRef: string; payload: PlatformManagedProductServiceShopifyBillingConfig }) =>
      updateProductServiceShopifyBillingConfig(serviceRef, payload),
    onSuccess: async (service) => {
      setMessage({
        type: 'success',
        text: `Updated Shopify billing configuration for ${service.displayName}. Reconcile to apply it to Railway.`,
      })
      setShopifyBillingDialogOpen(false)
      await refreshSelected(service.serviceRef)
    },
    onError: (error) =>
      setMessage({
        type: 'error',
        text: error instanceof Error ? error.message : 'Failed to update Shopify billing configuration.',
      }),
  })

  const restartMutation = useMutation({
    mutationFn: (serviceRef: string) => restartProductService(serviceRef),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Restart requested for ${service.displayName}.` })
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to restart product service.' }),
  })

  const rotateMutation = useMutation({
    mutationFn: ({ serviceRef, value }: { serviceRef: string; value: string }) =>
      rotateProductServiceSecret(serviceRef, { value }),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Rotated secret for ${service.displayName}.` })
      setRotateDialogOpen(false)
      setRotateSecretValue('')
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to rotate secret.' }),
  })

  const forceRecreateMutation = useMutation({
    mutationFn: (serviceRef: string) => forceRecreateProductService(serviceRef),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Cleared linkage for ${service.displayName}.` })
      setForceRecreateDialogOpen(false)
      setForceRecreateConfirmation('')
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to force recreate product service.' }),
  })

  const decommissionMutation = useMutation({
    mutationFn: (serviceRef: string) => decommissionProductService(serviceRef),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Decommissioned ${service.displayName}.` })
      setDecommissionDialogOpen(false)
      setDecommissionConfirmation('')
      await refreshSelected(service.serviceRef)
    },
    onError: (error) => setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to decommission product service.' }),
  })

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Product Services
          </Typography>
          <Typography color="text.secondary">
            Operator lifecycle for shared product backends such as the Shopify Bridge Service.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreateDialogOpen(true)}>
          Register service
        </Button>
      </Stack>

      {message ? <Alert severity={message.type}>{message.text}</Alert> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography sx={{ fontWeight: 700 }}>Managed services</Typography>
                {(servicesQuery.data ?? []).length === 0 ? (
                  <Alert severity="info">No product services registered yet.</Alert>
                ) : (
                  <List disablePadding>
                    {(servicesQuery.data ?? []).map((service) => (
                      <ListItemButton
                        key={service.serviceRef}
                        selected={service.serviceRef === selectedServiceRef}
                        onClick={() => setSearchParams({ service: service.serviceRef }, { replace: true })}
                        sx={{ borderRadius: 2, mb: 0.5 }}
                      >
                        <ListItemText
                          primary={service.displayName}
                          secondary={`${service.serviceKind} · ${service.environmentScope ?? 'unscoped'}`}
                        />
                        <Chip size="small" label={service.status} color={chipColor(service.status)} />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          {!selectedService ? (
            <Alert severity="info">Select a managed product service to inspect its state.</Alert>
          ) : (
            <Stack spacing={2}>
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
                      <Box>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <PrecisionManufacturingRoundedIcon color="primary" />
                          <Typography variant="h5" sx={{ fontWeight: 700 }}>
                            {selectedService.displayName}
                          </Typography>
                          <Chip size="small" label={selectedService.status} color={chipColor(selectedService.status)} />
                        </Stack>
                        <Typography color="text.secondary">
                          {selectedService.serviceRef} · {selectedService.productFamily} · {selectedService.serviceKind}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Button
                          variant="outlined"
                          startIcon={<RefreshRoundedIcon />}
                          onClick={() => reconcileMutation.mutate(selectedService.serviceRef)}
                          disabled={reconcileMutation.isPending}
                        >
                          Reconcile
                        </Button>
                        <Button variant="outlined" onClick={() => setDeploymentHistoryDialogOpen(true)}>
                          Inspect provider deployments
                        </Button>
                        <Button
                          variant="outlined"
                          startIcon={<RestartAltRoundedIcon />}
                          onClick={() => restartMutation.mutate(selectedService.serviceRef)}
                          disabled={restartMutation.isPending}
                        >
                          Restart
                        </Button>
                        <Button
                          variant="outlined"
                          color="warning"
                          startIcon={<KeyRoundedIcon />}
                          onClick={() => setRotateDialogOpen(true)}
                          disabled={!selectedService.secretName}
                        >
                          Rotate secret
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          startIcon={<WarningAmberRoundedIcon />}
                          onClick={() => setForceRecreateDialogOpen(true)}
                        >
                          Force recreate
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          onClick={() => setDecommissionDialogOpen(true)}
                          disabled={decommissionBlocked}
                        >
                          Decommission
                        </Button>
                      </Stack>
                    </Stack>

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <TextField
                          label="Desired replicas"
                          value={scaleValue}
                          onChange={(event) => setScaleValue(event.target.value)}
                          size="small"
                          fullWidth
                        />
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Button
                          variant="contained"
                          fullWidth
                          sx={{ height: '100%' }}
                          onClick={() => scaleMutation.mutate({ serviceRef: selectedService.serviceRef, desiredReplicas: Number(scaleValue) })}
                          disabled={scaleMutation.isPending}
                        >
                          Update scale
                        </Button>
                      </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                      {[
                        ['Environment', selectedService.environmentScope],
                        ['Deployment mode', selectedService.deploymentMode],
                        ['Tenant mode', selectedService.tenantMode],
                        ['Base URL', selectedService.baseUrl],
                        ['Health path', selectedService.healthPath],
                        ['Service root', selectedService.serviceRoot],
                        ['Dockerfile', selectedService.dockerfilePath],
                        ['Provider service', selectedService.railwayServiceId],
                        ['Dependents', `${selectedService.dependentStoresCount} stores`],
                      ].map(([label, value]) => (
                        <Grid item xs={12} md={4} key={label}>
                          <Typography variant="caption" color="text.secondary">
                            {label}
                          </Typography>
                          <Typography variant="body2">{detailValue(value)}</Typography>
                        </Grid>
                      ))}
                    </Grid>
                    {decommissionBlocked ? (
                      <Alert severity="warning">
                        Decommission is blocked while {selectedService?.dependentStoresCount ?? 0} Shopify store mapping(s) still depend on this
                        service. Remove the mappings first.
                      </Alert>
                    ) : null}
                    {selectedService.shopifyBillingConfig ? (
                      <Alert
                        severity={selectedService.shopifyBillingConfig.mode === 'SHOPIFY_APP_SUBSCRIPTION' ? 'info' : 'warning'}
                        action={
                          <Button
                            color="inherit"
                            size="small"
                            onClick={() => {
                              setShopifyBillingDraft(billingConfigOrDefault(selectedService.shopifyBillingConfig))
                              setShopifyBillingDialogOpen(true)
                            }}
                          >
                            Configure
                          </Button>
                        }
                      >
                        Shopify billing config {selectedService.shopifyBillingConfig.mode} · Starter{' '}
                        {selectedService.shopifyBillingConfig.starterEnabled ? 'enabled' : 'off'} · Elite{' '}
                        {selectedService.shopifyBillingConfig.eliteEnabled ? 'enabled' : 'off'}. Reconcile applies these values to the
                        provider-managed Bridge service on create or recreate.
                      </Alert>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>

              <Grid container spacing={2}>
                <Grid item xs={12} md={6}>
                  {healthQuery.data ? probeCard(healthQuery.data.healthProbe) : <Alert severity="info">Health data will load when selected.</Alert>}
                </Grid>
                <Grid item xs={12} md={6}>
                  <Card variant="outlined">
                    <CardContent>
                      <Stack spacing={1.25}>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <HubRoundedIcon color="primary" />
                          <Typography sx={{ fontWeight: 700 }}>Health summary</Typography>
                          <Chip size="small" label={healthQuery.data?.status ?? 'PENDING'} color={chipColor(healthQuery.data?.status)} />
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          Drift: {detailValue(healthQuery.data?.driftStatus)}
                        </Typography>
                        <Typography variant="body2">{detailValue(healthQuery.data?.driftMessage)}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          Last healthy {formatTimestamp(healthQuery.data?.lastHealthyAt)}
                        </Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>

              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={1.5}>
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                      <Typography sx={{ fontWeight: 700 }}>Bridge overview</Typography>
                      <Chip size="small" label={overviewQuery.data?.status ?? 'PENDING'} color={chipColor(overviewQuery.data?.status)} />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={overviewQuery.data?.stores.platformAccessStatus ?? 'UNKNOWN'}
                        color={chipColor(overviewQuery.data?.stores.platformAccessStatus)}
                      />
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      {detailValue(overviewQuery.data?.summaryMessage)}
                    </Typography>
                    <Grid container spacing={2}>
                      {[
                        ['App name', overviewQuery.data?.appName],
                        ['Platform base URL', overviewQuery.data?.platformBaseUrl],
                        ['Public base URL', overviewQuery.data?.publicBaseUrl],
                        ['Started', formatTimestamp(overviewQuery.data?.serverStartedAt)],
                        ['Install records', `${overviewQuery.data?.installs.totalCount ?? 0}`],
                        ['Installed', `${overviewQuery.data?.installs.installedCount ?? 0}`],
                        ['Credentials ready', `${overviewQuery.data?.installs.credentialReadyCount ?? 0}`],
                        ['Mapped stores', `${overviewQuery.data?.stores.totalCount ?? 0}`],
                        ['Go-live ready', `${overviewQuery.data?.stores.readyForGoLiveCount ?? 0}`],
                        ['Storefront ready', `${overviewQuery.data?.stores.storefrontReadyCount ?? 0}`],
                        ['Live stores', `${overviewQuery.data?.stores.liveCount ?? 0}`],
                        ['Blocked stores', `${overviewQuery.data?.stores.blockedCount ?? 0}`],
                      ].map(([label, value]) => (
                        <Grid item xs={12} md={3} key={label}>
                          <Typography variant="caption" color="text.secondary">
                            {label}
                          </Typography>
                          <Typography variant="body2">{detailValue(value)}</Typography>
                        </Grid>
                      ))}
                    </Grid>
                    <Typography variant="body2" color="text.secondary">
                      Platform store access: {detailValue(overviewQuery.data?.stores.platformAccessMessage)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Last authenticated {formatTimestamp(overviewQuery.data?.installs.lastAuthenticatedAt)} · Last uninstall{' '}
                      {formatTimestamp(overviewQuery.data?.installs.lastUninstalledAt)} · Last webhook {formatTimestamp(overviewQuery.data?.stores.lastWebhookAt)}
                    </Typography>
                    {overviewQuery.data?.webhookSubscriptions ? (
                      <Alert severity={overviewQuery.data.webhookSubscriptions.status === 'READY' ? 'info' : 'warning'}>
                        Webhook subscriptions {detailValue(overviewQuery.data.webhookSubscriptions.status)} · expected{' '}
                        {overviewQuery.data.webhookSubscriptions.expectedCount}. {detailValue(overviewQuery.data.webhookSubscriptions.message)}
                        {overviewQuery.data.webhookSubscriptions.webhookUri
                          ? ` Endpoint ${overviewQuery.data.webhookSubscriptions.webhookUri}.`
                          : ''}
                      </Alert>
                    ) : null}
                    {overviewQuery.data?.billing ? (
                      <Alert severity={overviewQuery.data.billing.launchBlocked ? 'warning' : 'info'}>
                        Billing {detailValue(overviewQuery.data.billing.mode)} · tier {detailValue(overviewQuery.data.billing.tierKey)} ·{' '}
                        {detailValue(overviewQuery.data.billing.status)} · plan {detailValue(overviewQuery.data.billing.planName)}.{' '}
                        {detailValue(overviewQuery.data.billing.message)}
                      </Alert>
                    ) : null}
                    {overviewQuery.data?.billing ? (
                      <Typography variant="body2" color="text.secondary">
                        Surfaces {overviewQuery.data.billing.allowedSurfaces.length ? overviewQuery.data.billing.allowedSurfaces.join(' · ') : '—'} ·
                        {' '}Product cap {detailValue(overviewQuery.data.billing.catalogProductCap ?? 'unlimited')} ·
                        {' '}Sync cadence {detailValue(overviewQuery.data.billing.syncCadence)} ·
                        {' '}Badge {overviewQuery.data.billing.poweredByBadgeRequired ? 'required' : 'optional'} ·
                        {' '}Chat fallback {overviewQuery.data.billing.chatFallbackEnabled ? 'enabled' : 'disabled'} ·
                        {' '}Actions {overviewQuery.data.billing.actionCapable ? 'enabled' : 'read-only'}
                      </Typography>
                    ) : null}
                    {overviewQuery.data?.capabilities?.length ? (
                      <Typography variant="body2" color="text.secondary">
                        Capabilities: {overviewQuery.data.capabilities.join(' · ')}
                      </Typography>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>

              {overviewQuery.data?.usage ? (
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                        <Typography sx={{ fontWeight: 700 }}>Bridge usage</Typography>
                        <Chip size="small" label={`${overviewQuery.data.usage.totalToday} today`} color={chipColor(overviewQuery.data.usage.totalToday > 0 ? 'READY' : 'PENDING')} />
                        <Chip size="small" variant="outlined" label={`${overviewQuery.data.usage.totalLast7Days} last 7d`} />
                      </Stack>
                      <Grid container spacing={2}>
                        {[
                          ['Active shops today', `${overviewQuery.data.usage.activeShopsToday}`],
                          ['Active shops last 7d', `${overviewQuery.data.usage.activeShopsLast7Days}`],
                          ['Events today', `${overviewQuery.data.usage.totalToday}`],
                          ['Events last 7d', `${overviewQuery.data.usage.totalLast7Days}`],
                        ].map(([label, value]) => (
                          <Grid item xs={12} md={3} key={label}>
                            <Typography variant="caption" color="text.secondary">
                              {label}
                            </Typography>
                            <Typography variant="body2">{detailValue(value)}</Typography>
                          </Grid>
                        ))}
                      </Grid>
                      <Typography variant="body2" color="text.secondary">
                        Generated {formatTimestamp(overviewQuery.data.usage.generatedAt)} · Last activity {formatTimestamp(overviewQuery.data.usage.lastActivityAt)}
                      </Typography>
                      {overviewQuery.data.usage.todayBreakdown.length ? (
                        <Typography variant="body2" color="text.secondary">
                          Today: {overviewQuery.data.usage.todayBreakdown.map((entry) => `${usageBreakdownLabel(entry.eventType)} ${entry.count}`).join(' · ')}
                        </Typography>
                      ) : null}
                      {overviewQuery.data.usage.last7DayBreakdown.length ? (
                        <Typography variant="body2" color="text.secondary">
                          Last 7 days: {overviewQuery.data.usage.last7DayBreakdown.map((entry) => `${usageBreakdownLabel(entry.eventType)} ${entry.count}`).join(' · ')}
                        </Typography>
                      ) : null}
                    </Stack>
                  </CardContent>
                </Card>
              ) : null}

              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={2}>
                    <Typography sx={{ fontWeight: 700 }}>Dependent Shopify stores</Typography>
                    {(dependentsQuery.data ?? []).length === 0 ? (
                      <Alert severity="info">No Shopify stores are currently mapped to this service.</Alert>
                    ) : (
                      <Stack spacing={1.5}>
                        {(dependentsQuery.data ?? []).map((store: ShopifyStoreConnectionSummary) => (
                          <Card key={store.id} variant="outlined">
                            <CardContent>
                              <Stack spacing={1.25}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Typography sx={{ fontWeight: 700 }}>{store.shopDomain}</Typography>
                                  {store.readiness ? (
                                    <Chip size="small" label={store.readiness.overallStatus} color={chipColor(store.readiness.overallStatus)} />
                                  ) : null}
                                  <Chip size="small" label={store.onboardingStatus} color={chipColor(store.onboardingStatus)} />
                                  <Chip size="small" label={store.installStatus} color={chipColor(store.installStatus)} />
                                  <Chip size="small" label={store.syncStatus} color={chipColor(store.syncStatus)} />
                                  <Chip size="small" label={store.sourceReadinessStatus} color={chipColor(store.sourceReadinessStatus)} />
                                  <Chip size="small" label={store.widgetStatus} color={chipColor(store.widgetStatus)} />
                                </Stack>
                                <Typography variant="body2" color="text.secondary">
                                  Customer {detailValue(store.customerName)} · Deployment {detailValue(store.deploymentName)} · Consumer {detailValue(store.consumerId)}
                                </Typography>
                                {store.latestRelease ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Release {detailValue(store.latestVersion?.versionLabel)} · {store.latestRelease.status.toLowerCase()} · verification{' '}
                                    {detailValue(store.latestRelease.verificationStatus).toLowerCase()} · provisioning {detailValue(store.latestRelease.provisioningStatus).toLowerCase()}
                                  </Typography>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">
                                    Release not requested yet.
                                  </Typography>
                                )}
                                <Typography variant="body2" color="text.secondary">
                                  Sources: products {store.productsEnabled ? 'on' : 'off'} · collections {store.collectionsEnabled ? 'on' : 'off'} · pages {store.pagesEnabled ? 'on' : 'off'} · policies {store.policiesEnabled ? 'on' : 'off'} · articles {store.articlesEnabled ? 'on' : 'off'}
                                </Typography>
                                {store.credentials ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Credentials {store.credentials.status.toLowerCase()} · access {store.credentials.accessTokenPresent ? 'ready' : 'missing'} · refresh {store.credentials.refreshTokenPresent ? 'ready' : 'missing'} · scope {detailValue(store.credentials.scopesText)}
                                  </Typography>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">
                                    Credentials missing.
                                  </Typography>
                                )}
                                {store.sourcePreflight ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Preflight {store.sourcePreflight.overallStatus.toLowerCase()} ·{' '}
                                    {store.sourcePreflight.categories
                                      .map((category) => `${category.category} ${category.status.toLowerCase()} (${category.itemCount})`)
                                      .join(' · ')}
                                  </Typography>
                                ) : null}
                                {store.syncDetail ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Sync {store.syncDetail.status.toLowerCase()} · mode {detailValue(store.syncDetail.mode)} · documents {store.syncDetail.documentCount}
                                  </Typography>
                                ) : null}
                                {store.widgetDetail?.settings?.enabledSurfaces?.length ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Configured widget surfaces {store.widgetDetail.settings.enabledSurfaces.join(' · ')}
                                  </Typography>
                                ) : null}
                                {store.capabilities ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Deployment capabilities {store.capabilities.actionCount} actions · {store.capabilities.knowledgeSourceCount} knowledge sources · {store.capabilities.marketplaceDatasetCount} datasets · {store.capabilities.shellModuleCount} shell modules
                                  </Typography>
                                ) : null}
                                {store.webhookDetail ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Webhook {detailValue(store.webhookDetail.topic)} · event {detailValue(store.webhookDetail.eventType)} · source {detailValue(store.webhookDetail.sourceCategory)}
                                    {store.webhookDetail.invalidateSync ? ' · sync invalidated' : ''}
                                  </Typography>
                                ) : null}
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Button size="small" onClick={() => setBindingDialogStore(store)}>
                                    Inspect platform bindings
                                  </Button>
                                  <Button size="small" onClick={() => setBillingDialogStore(store)}>
                                    Inspect billing posture
                                  </Button>
                                  <Button size="small" onClick={() => setWebhookDialogStore(store)}>
                                    Inspect webhook subscriptions
                                  </Button>
                                </Stack>
                                {store.widgetDetail ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Widget {store.widgetDetail.status.toLowerCase()} · channel {detailValue(store.widgetDetail.channel)}
                                    {store.widgetDetail.settings?.launcherLabel ? ` · launcher ${store.widgetDetail.settings.launcherLabel}` : ''}
                                    {store.widgetDetail.settings ? ` · debug ${store.widgetDetail.settings.debugEnabled ? 'enabled' : 'disabled'}` : ''}
                                  </Typography>
                                ) : null}
                                {store.readiness?.nextActions?.length ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Next: {store.readiness.nextActions.join(' · ')}
                                  </Typography>
                                ) : null}
                                <Typography variant="caption" color="text.secondary">
                                  Last preflight {formatTimestamp(store.lastSourcePreflightAt)} · Last sync {formatTimestamp(store.lastSyncAt)} · Last webhook {formatTimestamp(store.lastWebhookAt)}
                                </Typography>
                              </Stack>
                            </CardContent>
                          </Card>
                        ))}
                      </Stack>
                    )}
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography sx={{ fontWeight: 700 }}>Recent activity</Typography>
                    {(activityQuery.data ?? []).length === 0 ? (
                      <Typography color="text.secondary">No audit events recorded yet.</Typography>
                    ) : (
                      <Stack spacing={1}>
                        {(activityQuery.data ?? []).map((event) => (
                          <Box key={event.id}>
                            <Typography sx={{ fontWeight: 600 }}>{event.action}</Typography>
                            <Typography variant="body2" color="text.secondary">
                              {activitySummary(event)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {formatTimestamp(event.createdAt)}
                            </Typography>
                            <Divider sx={{ mt: 1.25 }} />
                          </Box>
                        ))}
                      </Stack>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          )}
        </Grid>
      </Grid>

      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Register Product Service</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField label="Service ref" value={form.serviceRef} onChange={(event) => setForm((current) => ({ ...current, serviceRef: event.target.value }))} fullWidth />
            <TextField label="Display name" value={form.displayName} onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))} fullWidth />
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField select label="Product family" value={form.productFamily} onChange={(event) => setForm((current) => ({ ...current, productFamily: event.target.value }))} fullWidth>
                <MenuItem value="SHOPIFY">SHOPIFY</MenuItem>
                <MenuItem value="MCP">MCP</MenuItem>
                <MenuItem value="WOOCOMMERCE">WOOCOMMERCE</MenuItem>
              </TextField>
              <TextField
                select
                label="Service kind"
                value={form.serviceKind}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    ...(productServicePresets[event.target.value] ?? {}),
                    serviceKind: event.target.value,
                  }))
                }
                fullWidth
              >
                <MenuItem value="SHOPIFY_BRIDGE_SERVICE">SHOPIFY_BRIDGE_SERVICE</MenuItem>
                <MenuItem value="MCP_EXECUTION_GATEWAY_SERVICE">MCP_EXECUTION_GATEWAY_SERVICE</MenuItem>
              </TextField>
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="Deployment mode" value={form.deploymentMode} onChange={(event) => setForm((current) => ({ ...current, deploymentMode: event.target.value }))} fullWidth />
              <TextField label="Tenant mode" value={form.tenantMode} onChange={(event) => setForm((current) => ({ ...current, tenantMode: event.target.value }))} fullWidth />
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="Environment scope" value={form.environmentScope ?? ''} onChange={(event) => setForm((current) => ({ ...current, environmentScope: event.target.value || null }))} fullWidth />
              <TextField label="Health path" value={form.healthPath ?? ''} onChange={(event) => setForm((current) => ({ ...current, healthPath: event.target.value || null }))} fullWidth />
            </Stack>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="Desired replicas" type="number" value={form.desiredReplicas ?? 1} onChange={(event) => setForm((current) => ({ ...current, desiredReplicas: Number(event.target.value) }))} fullWidth />
              <TextField label="Min replicas" type="number" value={form.minReplicas ?? 1} onChange={(event) => setForm((current) => ({ ...current, minReplicas: Number(event.target.value) }))} fullWidth />
              <TextField label="Max replicas" type="number" value={form.maxReplicas ?? 3} onChange={(event) => setForm((current) => ({ ...current, maxReplicas: Number(event.target.value) }))} fullWidth />
            </Stack>
            <TextField label="Base URL (optional)" value={form.baseUrl ?? ''} onChange={(event) => setForm((current) => ({ ...current, baseUrl: event.target.value || null }))} fullWidth />
            <TextField label="Service root" value={form.serviceRoot ?? ''} onChange={(event) => setForm((current) => ({ ...current, serviceRoot: event.target.value || null }))} fullWidth />
            <TextField label="Dockerfile path" value={form.dockerfilePath ?? ''} onChange={(event) => setForm((current) => ({ ...current, dockerfilePath: event.target.value || null }))} fullWidth />
            <TextField label="Secret name (optional)" value={form.secretName ?? ''} onChange={(event) => setForm((current) => ({ ...current, secretName: event.target.value || null }))} fullWidth />
            <TextField
              select
              label="Coolify target profile"
              value={form.targetProfileId ?? ''}
              onChange={(event) => setForm((current) => ({ ...current, targetProfileId: event.target.value || null }))}
              helperText={
                targetProfilesQuery.isError
                  ? 'Target profiles failed to load; leave blank to use the managed-service default or enter one through the API.'
                  : 'Leave blank for the restartable-services default. Select production only for an intentional production rollout.'
              }
              disabled={targetProfilesQuery.isLoading}
              fullWidth
            >
              <MenuItem value="">Default managed-service profile</MenuItem>
              {managedServiceTargetProfiles.map((profile) => (
                <MenuItem key={profile.id} value={profile.id}>
                  {targetProfileLabel(profile)}
                </MenuItem>
              ))}
              {form.targetProfileId && !managedServiceTargetProfiles.some((profile) => profile.id === form.targetProfileId) ? (
                <MenuItem value={form.targetProfileId}>{form.targetProfileId}</MenuItem>
              ) : null}
            </TextField>
            {form.serviceKind === 'SHOPIFY_BRIDGE_SERVICE' ? (
              <>
                <Divider />
                <Stack spacing={1}>
                  <Typography sx={{ fontWeight: 700 }}>Shopify billing</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Stored on the Platform service record and applied to the managed provider on reconcile, create, and recreate.
                  </Typography>
                </Stack>
                {shopifyBillingConfigFields(billingConfigOrDefault(form.shopifyBillingConfig), (patch) =>
                  setForm((current) => ({
                    ...current,
                    shopifyBillingConfig: {
                      ...billingConfigOrDefault(current.shopifyBillingConfig),
                      ...patch,
                    },
                  })),
                )}
              </>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => createMutation.mutate(form)} disabled={createMutation.isPending}>
            Register
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={shopifyBillingDialogOpen} onClose={() => setShopifyBillingDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Configure Shopify Billing</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="info">
              This changes the Platform-owned Bridge billing configuration. Run Reconcile after saving to update the managed provider service
              environment and redeploy the Bridge.
            </Alert>
            {shopifyBillingConfigFields(shopifyBillingDraft, (patch) =>
              setShopifyBillingDraft((current) => ({
                ...current,
                ...patch,
              })),
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShopifyBillingDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() =>
              selectedService
                ? updateShopifyBillingMutation.mutate({
                    serviceRef: selectedService.serviceRef,
                    payload: shopifyBillingDraft,
                  })
                : undefined
            }
            disabled={!selectedService || updateShopifyBillingMutation.isPending}
          >
            Save billing config
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={webhookDialogStore != null} onClose={() => setWebhookDialogStore(null)} fullWidth maxWidth="md">
        <DialogTitle>Webhook Subscription Diagnostics</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Store {detailValue(webhookDialogStore?.shopDomain)} · Service {detailValue(selectedServiceRef)}
            </Typography>
            {webhookSubscriptionsQuery.isLoading ? (
              <Alert severity="info">Loading webhook subscription diagnostics…</Alert>
            ) : webhookSubscriptionsQuery.isError ? (
              <Alert severity="error">
                {webhookSubscriptionsQuery.error instanceof Error
                  ? webhookSubscriptionsQuery.error.message
                  : 'Failed to load webhook subscription diagnostics.'}
              </Alert>
            ) : webhookSubscriptionsQuery.data ? (
              <Stack spacing={1.5}>
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                  <Chip label={webhookSubscriptionsQuery.data.status} color={chipColor(webhookSubscriptionsQuery.data.status)} />
                  <Chip label={`${webhookSubscriptionsQuery.data.readyCount}/${webhookSubscriptionsQuery.data.expectedCount} ready`} variant="outlined" />
                  <Chip label={`${webhookSubscriptionsQuery.data.missingCount} missing`} color={chipColor(webhookSubscriptionsQuery.data.missingCount > 0 ? 'DEGRADED' : 'READY')} />
                  <Chip label={`${webhookSubscriptionsQuery.data.driftedCount} drifted`} color={chipColor(webhookSubscriptionsQuery.data.driftedCount > 0 ? 'DEGRADED' : 'READY')} />
                </Stack>
                <Typography variant="body2">{detailValue(webhookSubscriptionsQuery.data.message)}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Webhook URI {detailValue(webhookSubscriptionsQuery.data.webhookUri)} · Checked {formatTimestamp(webhookSubscriptionsQuery.data.checkedAt)}
                </Typography>
                <Stack spacing={1}>
                  {webhookSubscriptionsQuery.data.topics.map((topic) => (
                    <Card key={`${topic.topic ?? 'topic'}-${topic.expectedName ?? 'expected'}`} variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography sx={{ fontWeight: 700 }}>{detailValue(topic.topic)}</Typography>
                            <Chip size="small" label={topic.status} color={chipColor(topic.status)} />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            Expected name {detailValue(topic.expectedName)} · subscription {detailValue(topic.subscriptionName)} · URI {detailValue(topic.subscriptionUri)}
                          </Typography>
                          {topic.message ? (
                            <Typography variant="body2" color="text.secondary">
                              {topic.message}
                            </Typography>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>
              </Stack>
            ) : (
              <Alert severity="info">Webhook subscription diagnostics have not been loaded yet.</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setWebhookDialogStore(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={billingDialogStore != null} onClose={() => setBillingDialogStore(null)} fullWidth maxWidth="sm">
        <DialogTitle>Store Billing Posture</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Store {detailValue(billingDialogStore?.shopDomain)} · Service {detailValue(selectedServiceRef)}
            </Typography>
            {storeBillingSummaryQuery.isLoading ? (
              <Alert severity="info">Loading billing posture…</Alert>
            ) : storeBillingSummaryQuery.isError ? (
              <Alert severity="error">
                {storeBillingSummaryQuery.error instanceof Error
                  ? storeBillingSummaryQuery.error.message
                  : 'Failed to load store billing posture.'}
              </Alert>
            ) : storeBillingSummaryQuery.data ? (
              <Stack spacing={1.5}>
                <Alert severity={billingSeverity(storeBillingSummaryQuery.data)}>
                  Billing {detailValue(storeBillingSummaryQuery.data.mode)} · tier {detailValue(storeBillingSummaryQuery.data.tierKey)} ·{' '}
                  {detailValue(storeBillingSummaryQuery.data.status)} · plan {detailValue(storeBillingSummaryQuery.data.planName)}.{' '}
                  {detailValue(storeBillingSummaryQuery.data.message)}
                </Alert>
                <Grid container spacing={2}>
                  {[
                    ['Mode', storeBillingSummaryQuery.data.mode],
                    ['Tier', storeBillingSummaryQuery.data.tierKey],
                    ['Plan', storeBillingSummaryQuery.data.planName],
                    ['Status', storeBillingSummaryQuery.data.status],
                    ['Merchant approval', storeBillingSummaryQuery.data.merchantApprovalRequired ? 'required' : 'not required'],
                    ['Go-live blocking', storeBillingSummaryQuery.data.launchBlocked ? 'yes' : 'no'],
                    ['Paid tier', storeBillingSummaryQuery.data.paidTier ? 'yes' : 'no'],
                    ['Action capable', storeBillingSummaryQuery.data.actionCapable ? 'yes' : 'no'],
                    ['Product cap', storeBillingSummaryQuery.data.catalogProductCap ?? 'unlimited'],
                    ['Sync cadence', storeBillingSummaryQuery.data.syncCadence],
                    ['Powered-by badge', storeBillingSummaryQuery.data.poweredByBadgeRequired ? 'required' : 'optional'],
                    ['Chat fallback', storeBillingSummaryQuery.data.chatFallbackEnabled ? 'enabled' : 'disabled'],
                    ['Explicit confirmation', storeBillingSummaryQuery.data.requiresExplicitConfirmation ? 'required' : 'not required'],
                    ['Audit trail', storeBillingSummaryQuery.data.auditTrailAvailable ? 'available' : 'not applicable'],
                  ].map(([label, value]) => (
                    <Grid item xs={12} sm={6} key={label}>
                      <Typography variant="caption" color="text.secondary">
                        {label}
                      </Typography>
                      <Typography variant="body2">{detailValue(value)}</Typography>
                    </Grid>
                  ))}
                </Grid>
                <Typography variant="body2" color="text.secondary">
                  Allowed surfaces {storeBillingSummaryQuery.data.allowedSurfaces.length ? storeBillingSummaryQuery.data.allowedSurfaces.join(' · ') : '—'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Action packages {storeBillingSummaryQuery.data.actionPackages.length ? storeBillingSummaryQuery.data.actionPackages.join(' · ') : '—'}
                </Typography>
                {storeBillingSummaryQuery.data.availablePlans.length ? (
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      Tier ladder
                    </Typography>
                    {storeBillingSummaryQuery.data.availablePlans.map((plan) => (
                      <Card key={`${plan.tierKey ?? 'unknown'}-${plan.planName ?? 'plan'}`} variant="outlined">
                        <CardContent sx={{ py: 1.5 }}>
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                              <Typography variant="subtitle2">{detailValue(plan.planName)}</Typography>
                              <Stack direction="row" spacing={1}>
                                <Chip
                                  size="small"
                                  label={plan.active ? 'Current tier' : detailValue(plan.tierKey)}
                                  color={plan.active ? 'success' : 'default'}
                                  variant={plan.active ? 'filled' : 'outlined'}
                                />
                                <Chip
                                  size="small"
                                  label={plan.chatFallbackEnabled ? 'Chat fallback' : 'Embedded-only'}
                                  color={plan.chatFallbackEnabled ? 'success' : 'warning'}
                                  variant="outlined"
                                />
                              </Stack>
                            </Stack>
                            <Typography variant="body2" color="text.secondary">
                              {plan.amount && plan.currencyCode && plan.interval
                                ? `${plan.amount} ${plan.currencyCode} / ${plan.interval}`
                                : plan.tierKey === 'FREE'
                                  ? 'Free'
                                  : 'Pricing unavailable'} · Surfaces{' '}
                              {plan.allowedSurfaces.length ? plan.allowedSurfaces.join(' · ') : '—'} · Product cap{' '}
                              {detailValue(plan.catalogProductCap ?? 'unlimited')} · Sync cadence {detailValue(plan.syncCadence)}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {plan.actionCapable ? 'Read + governed actions' : 'Read-only shopper intelligence'} · Merchant approval{' '}
                              {plan.merchantApprovalSupported ? 'supported' : 'not required'} · Badge{' '}
                              {plan.poweredByBadgeRequired ? 'required' : 'optional'}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              Confirmation {plan.requiresExplicitConfirmation ? 'required' : 'not required'} · Audit{' '}
                              {plan.auditTrailAvailable ? 'available' : 'not applicable'} · Action packages{' '}
                              {plan.actionPackages.length ? plan.actionPackages.join(' · ') : '—'}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {detailValue(plan.message)}
                            </Typography>
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                ) : null}
              </Stack>
            ) : (
              <Alert severity="info">Billing posture is not available for this store yet.</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBillingDialogStore(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={bindingDialogStore != null} onClose={() => setBindingDialogStore(null)} fullWidth maxWidth="md">
        <DialogTitle>Platform Binding Inspection</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Store {detailValue(bindingDialogStore?.shopDomain)} · Service {detailValue(selectedServiceRef)}
            </Typography>
            {storeBindingQuery.isLoading ? (
              <Alert severity="info">Loading platform binding inspection…</Alert>
            ) : storeBindingQuery.isError ? (
              <Alert severity="error">
                {storeBindingQuery.error instanceof Error ? storeBindingQuery.error.message : 'Failed to load platform binding inspection.'}
              </Alert>
            ) : storeBindingQuery.data ? (
              <Stack spacing={1.5}>
                {storeBindingQuery.data.warnings.length > 0 ? (
                  <Alert severity="warning">
                    <strong>Binding warnings</strong>
                    <List dense>
                      {storeBindingQuery.data.warnings.map((warning) => (
                        <li key={warning}>{warning}</li>
                      ))}
                    </List>
                  </Alert>
                ) : (
                  <Alert severity="success">Customer, deployment, and consumer bindings are aligned.</Alert>
                )}
                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Customer</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {detailValue(storeBindingQuery.data.customer?.name)} · {detailValue(storeBindingQuery.data.customer?.id)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Slug {detailValue(storeBindingQuery.data.customer?.slug)} · status {detailValue(storeBindingQuery.data.customer?.status)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Platform managed {storeBindingQuery.data.customer?.platformManaged ? 'yes' : 'no'}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Created {formatTimestamp(storeBindingQuery.data.customer?.createdAt)} · Updated {formatTimestamp(storeBindingQuery.data.customer?.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Deployment</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {detailValue(storeBindingQuery.data.deployment?.name)} · {detailValue(storeBindingQuery.data.deployment?.id)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Env {detailValue(storeBindingQuery.data.deployment?.environment)} · status {detailValue(storeBindingQuery.data.deployment?.status)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Active version {detailValue(storeBindingQuery.data.deployment?.activeVersionId)} · template {detailValue(storeBindingQuery.data.deployment?.templateId)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Runtime {detailValue(storeBindingQuery.data.deployment?.runtimeBaseUrl)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Connector {detailValue(storeBindingQuery.data.deployment?.connectorBaseUrl)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Created {formatTimestamp(storeBindingQuery.data.deployment?.createdAt)} · Updated {formatTimestamp(storeBindingQuery.data.deployment?.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Typography sx={{ fontWeight: 700 }}>Consumer</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {detailValue(storeBindingQuery.data.consumer?.displayName)} · {detailValue(storeBindingQuery.data.consumer?.consumerId)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Status {detailValue(storeBindingQuery.data.consumer?.status)} · customer {detailValue(storeBindingQuery.data.consumer?.customerId)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Bound deployment {detailValue(storeBindingQuery.data.consumer?.boundDeploymentName)} · {detailValue(storeBindingQuery.data.consumer?.boundDeploymentId)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Last bound {formatTimestamp(storeBindingQuery.data.consumer?.lastBoundAt)} · Updated {formatTimestamp(storeBindingQuery.data.consumer?.updatedAt)}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>
                <Typography variant="body2" color="text.secondary">
                  Latest version {detailValue(storeBindingQuery.data.latestVersion?.versionLabel)} · status {detailValue(storeBindingQuery.data.latestVersion?.status)} · published{' '}
                  {formatTimestamp(storeBindingQuery.data.latestVersion?.publishedAt)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Latest release {detailValue(storeBindingQuery.data.latestRelease?.status)} · verification {detailValue(storeBindingQuery.data.latestRelease?.verificationStatus)} · provisioning{' '}
                  {detailValue(storeBindingQuery.data.latestRelease?.provisioningStatus)}
                </Typography>
              </Stack>
            ) : (
              <Alert severity="info">Platform binding inspection is not available for this store yet.</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBindingDialogStore(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deploymentHistoryDialogOpen} onClose={() => setDeploymentHistoryDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Provider Deployment History</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Service {detailValue(selectedServiceRef)}
            </Typography>
            {deploymentHistoryQuery.isLoading ? (
              <Alert severity="info">Loading provider deployment history…</Alert>
            ) : deploymentHistoryQuery.isError ? (
              <Alert severity="error">
                {deploymentHistoryQuery.error instanceof Error ? deploymentHistoryQuery.error.message : 'Failed to load provider deployment history.'}
              </Alert>
            ) : deploymentHistoryQuery.data ? (
              <Stack spacing={1.5}>
                <Alert severity={deploymentHistoryQuery.data.available ? 'info' : 'warning'}>
                  {deploymentHistoryQuery.data.message}
                </Alert>
                <Typography variant="body2" color="text.secondary">
                  Project {detailValue(deploymentHistoryQuery.data.railwayProjectId)} · Environment {detailValue(deploymentHistoryQuery.data.railwayEnvironmentId)} · Service{' '}
                  {detailValue(deploymentHistoryQuery.data.railwayServiceId)} · Generated {formatTimestamp(deploymentHistoryQuery.data.generatedAt)}
                </Typography>
                {deploymentHistoryQuery.data.deployments.length > 0 ? (
                  <Stack spacing={1}>
                    {deploymentHistoryQuery.data.deployments.map((deployment) => (
                      <Card key={deployment.id} variant="outlined">
                        <CardContent>
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography sx={{ fontWeight: 700 }}>{detailValue(deployment.id)}</Typography>
                              <Chip size="small" label={detailValue(deployment.status)} color={chipColor(deployment.status)} />
                              <Button size="small" variant={selectedRailwayDeploymentId === deployment.id ? 'contained' : 'outlined'} onClick={() => setSelectedRailwayDeploymentId(deployment.id)}>
                                View logs
                              </Button>
                            </Stack>
                            <Typography variant="body2" color="text.secondary">
                              URL {detailValue(deployment.url)} · Static {detailValue(deployment.staticUrl)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Created {formatTimestamp(deployment.createdAt)}
                            </Typography>
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                ) : null}
                <Divider />
                <Stack spacing={1.5}>
                  <Typography sx={{ fontWeight: 700 }}>Deployment logs</Typography>
                  {railwayLogsQuery.isLoading ? (
                    <Alert severity="info">Loading provider deployment logs…</Alert>
                  ) : railwayLogsQuery.isError ? (
                    <Alert severity="error">
                      {railwayLogsQuery.error instanceof Error ? railwayLogsQuery.error.message : 'Failed to load provider deployment logs.'}
                    </Alert>
                  ) : railwayLogsQuery.data ? (
                    <>
                      <Alert severity={railwayLogsQuery.data.available ? 'info' : 'warning'}>{railwayLogsQuery.data.message}</Alert>
                      <Typography variant="body2" color="text.secondary">
                        Deployment {detailValue(railwayLogsQuery.data.railwayDeploymentId)} · Source {detailValue(railwayLogsQuery.data.source)} · Queried{' '}
                        {formatTimestamp(railwayLogsQuery.data.queriedAt)}
                      </Typography>
                      {railwayLogsQuery.data.entries.length > 0 ? (
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Time</TableCell>
                              <TableCell>Severity</TableCell>
                              <TableCell>Message</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {railwayLogsQuery.data.entries.map((entry, index) => (
                              <TableRow key={`${entry.timestamp ?? 'na'}-${index}`} hover>
                                <TableCell>{formatTimestamp(entry.timestamp)}</TableCell>
                                <TableCell>
                                  <Chip size="small" label={entry.severity ?? 'UNKNOWN'} color={railwaySeverityColor(entry)} variant="outlined" />
                                </TableCell>
                                <TableCell sx={{ maxWidth: 900, whiteSpace: 'pre-wrap' }}>{entry.message ?? '—'}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      ) : (
                        <Alert severity="info">No log entries were returned for the current query.</Alert>
                      )}
                    </>
                  ) : (
                    <Alert severity="info">Provider logs are not available for this service yet.</Alert>
                  )}
                </Stack>
              </Stack>
            ) : (
              <Alert severity="info">Provider deployment history is not available for this service yet.</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeploymentHistoryDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={rotateDialogOpen} onClose={() => setRotateDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Rotate service secret</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="New secret value"
            value={rotateSecretValue}
            onChange={(event) => setRotateSecretValue(event.target.value)}
            fullWidth
            type="password"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRotateDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => selectedService && rotateMutation.mutate({ serviceRef: selectedService.serviceRef, value: rotateSecretValue })}
            disabled={!selectedService || rotateSecretValue.trim().length === 0 || rotateMutation.isPending}
          >
            Rotate
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={forceRecreateDialogOpen} onClose={() => setForceRecreateDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Force recreate linkage</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Type the service ref to clear provider linkage and base URLs.
          </Typography>
          <TextField
            autoFocus
            margin="dense"
            label="Service ref"
            value={forceRecreateConfirmation}
            onChange={(event) => setForceRecreateConfirmation(event.target.value)}
            fullWidth
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setForceRecreateDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={() => selectedService && forceRecreateMutation.mutate(selectedService.serviceRef)}
            disabled={!selectedService || selectedService.serviceRef !== forceRecreateConfirmation.trim() || forceRecreateMutation.isPending}
          >
            Clear linkage
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={decommissionDialogOpen} onClose={() => setDecommissionDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Decommission service</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Type the service ref to delete the managed provider linkage and clear the managed secret. Dependent Shopify store mappings must be removed first.
          </Typography>
          {decommissionBlocked ? (
            <Alert severity="warning" sx={{ mb: 2 }}>
              This service still has {selectedService?.dependentStoresCount ?? 0} dependent Shopify store mapping(s). Decommission is blocked until those
              mappings are removed.
            </Alert>
          ) : null}
          <TextField
            autoFocus
            margin="dense"
            label="Service ref"
            value={decommissionConfirmation}
            onChange={(event) => setDecommissionConfirmation(event.target.value)}
            fullWidth
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecommissionDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={() => selectedService && decommissionMutation.mutate(selectedService.serviceRef)}
            disabled={
              !selectedService ||
              decommissionBlocked ||
              selectedService.serviceRef !== decommissionConfirmation.trim() ||
              decommissionMutation.isPending
            }
          >
            Decommission
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
