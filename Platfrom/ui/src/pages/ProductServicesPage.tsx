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
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  createProductService,
  decommissionProductService,
  fetchProductService,
  fetchProductServiceActivity,
  fetchProductServiceDependents,
  fetchProductServiceHealth,
  fetchProductServiceOverview,
  fetchProductServices,
  forceRecreateProductService,
  reconcileProductService,
  restartProductService,
  rotateProductServiceSecret,
  scaleProductService,
  type CreatePlatformManagedProductServiceRequest,
  type PlatformAuditEventSummary,
  type PlatformManagedProductServiceHealthSummary,
  type PlatformManagedProductServiceOverviewSummary,
  type PlatformManagedProductServiceProbeSummary,
  type PlatformManagedProductServiceSummary,
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

type ProductServiceFormState = CreatePlatformManagedProductServiceRequest

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
}

export function ProductServicesPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [rotateDialogOpen, setRotateDialogOpen] = useState(false)
  const [forceRecreateDialogOpen, setForceRecreateDialogOpen] = useState(false)
  const [decommissionDialogOpen, setDecommissionDialogOpen] = useState(false)
  const [rotateSecretValue, setRotateSecretValue] = useState('')
  const [forceRecreateConfirmation, setForceRecreateConfirmation] = useState('')
  const [decommissionConfirmation, setDecommissionConfirmation] = useState('')
  const [scaleValue, setScaleValue] = useState('1')
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [form, setForm] = useState<ProductServiceFormState>(emptyForm)

  const servicesQuery = useQuery({
    queryKey: ['product-services'],
    queryFn: fetchProductServices,
  })

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

  useEffect(() => {
    if (selectedService) {
      setScaleValue(`${selectedService.desiredReplicas ?? 1}`)
    }
  }, [selectedService?.desiredReplicas, selectedService?.serviceRef])

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

  const refreshSelected = async (serviceRef: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['product-services'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'dependents'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'activity'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'health'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services', serviceRef, 'overview'] }),
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
                        ['Railway service', selectedService.railwayServiceId],
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
                    {overviewQuery.data?.capabilities?.length ? (
                      <Typography variant="body2" color="text.secondary">
                        Capabilities: {overviewQuery.data.capabilities.join(' · ')}
                      </Typography>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>

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
                                  Sources: products {store.productsEnabled ? 'on' : 'off'} · collections {store.collectionsEnabled ? 'on' : 'off'} · pages {store.pagesEnabled ? 'on' : 'off'} · policies {store.policiesEnabled ? 'on' : 'off'}
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
                                {store.widgetDetail ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Widget {store.widgetDetail.status.toLowerCase()} · channel {detailValue(store.widgetDetail.channel)}
                                  </Typography>
                                ) : null}
                                {store.readiness?.nextActions?.length ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Next: {store.readiness.nextActions.join(' · ')}
                                  </Typography>
                                ) : null}
                                <Typography variant="caption" color="text.secondary">
                                  Last preflight {formatTimestamp(store.lastSourcePreflightAt)} · Last sync {formatTimestamp(store.lastSyncAt)}
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
                <MenuItem value="WOOCOMMERCE">WOOCOMMERCE</MenuItem>
              </TextField>
              <TextField label="Service kind" value={form.serviceKind} onChange={(event) => setForm((current) => ({ ...current, serviceKind: event.target.value }))} fullWidth />
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
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => createMutation.mutate(form)} disabled={createMutation.isPending}>
            Register
          </Button>
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
            Type the service ref to clear Railway linkage and base URLs.
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
            Type the service ref to delete the managed Railway linkage and clear the managed secret. Dependent Shopify store mappings must be removed first.
          </Typography>
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
            disabled={!selectedService || selectedService.serviceRef !== decommissionConfirmation.trim() || decommissionMutation.isPending}
          >
            Decommission
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
