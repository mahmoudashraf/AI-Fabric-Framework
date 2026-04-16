import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded'
import HubRoundedIcon from '@mui/icons-material/HubRounded'
import KeyRoundedIcon from '@mui/icons-material/KeyRounded'
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
import MemoryRoundedIcon from '@mui/icons-material/MemoryRounded'
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
  fetchMarketplaceInferenceService,
  fetchMarketplaceInferenceServiceActivity,
  fetchMarketplaceInferenceServiceDependents,
  fetchMarketplaceInferenceServiceHealth,
  fetchMarketplaceInferenceServices,
  forceRecreateMarketplaceInferenceService,
  reconcileMarketplaceInferenceService,
  restartMarketplaceInferenceService,
  rotateMarketplaceInferenceServiceSecret,
  scaleMarketplaceInferenceService,
  type PlatformAuditEventSummary,
  type PlatformManagedInferenceDependentDeploymentSummary,
  type PlatformManagedInferenceProbeSummary,
  type PlatformManagedInferenceServiceSummary,
} from '../api/platformApi'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function chipColor(value: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch ((value ?? '').toUpperCase()) {
    case 'ACTIVE':
    case 'READY':
    case 'NO_DRIFT':
      return 'success'
    case 'FAILED':
    case 'SECRET_DRIFT':
    case 'RAILWAY_RESOURCE_DRIFT':
    case 'ENDPOINT_DRIFT':
    case 'BLOCKED':
      return 'error'
    case 'CREATED':
    case 'PROVISIONING':
    case 'DEGRADED':
    case 'SKIPPED':
    case 'SCALE_DRIFT':
      return 'warning'
    default:
      return 'default'
  }
}

function normalizeText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function detailValue(value: string | number | null | undefined): string {
  if (value == null) {
    return '—'
  }
  const stringValue = `${value}`.trim()
  return stringValue.length > 0 ? stringValue : '—'
}

function statusMatches(service: PlatformManagedInferenceServiceSummary, selectedStatus: string): boolean {
  return selectedStatus === 'ALL' || service.status === selectedStatus
}

function kindMatches(service: PlatformManagedInferenceServiceSummary, selectedKind: string): boolean {
  return selectedKind === 'ALL' || service.serviceKind === selectedKind
}

function providerMatches(service: PlatformManagedInferenceServiceSummary, selectedProvider: string): boolean {
  return selectedProvider === 'ALL' || service.providerType === selectedProvider
}

function environmentMatches(service: PlatformManagedInferenceServiceSummary, selectedEnvironment: string): boolean {
  return selectedEnvironment === 'ALL' || (service.environmentScope ?? 'UNSCOPED') === selectedEnvironment
}

function serviceSearchMatches(service: PlatformManagedInferenceServiceSummary, searchText: string): boolean {
  const search = normalizeText(searchText)
  if (search.length === 0) {
    return true
  }
  return [
    service.displayName,
    service.serviceRef,
    service.serviceKind,
    service.providerType,
    service.modelId,
    service.environmentScope,
  ]
    .map(normalizeText)
    .some((value) => value.includes(search))
}

function activeDependentWarning(service: PlatformManagedInferenceServiceSummary | null): string | null {
  if (!service || service.dependentActiveDeploymentsCount <= 0) {
    return null
  }
  return `${service.dependentActiveDeploymentsCount} active deployment${service.dependentActiveDeploymentsCount === 1 ? '' : 's'} currently depend on this service.`
}

function probeCard(probe: PlatformManagedInferenceProbeSummary | null | undefined, title: string) {
  if (!probe) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1}>
            <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
            <Typography color="text.secondary">No probe data available.</Typography>
          </Stack>
        </CardContent>
      </Card>
    )
  }
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
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
    const pieces = ['status', 'driftStatus', 'secretName', 'deploymentId', 'message']
      .map((key) => (typeof record[key] === 'string' ? `${key}=${record[key] as string}` : null))
      .filter((value): value is string => value != null)
    if (pieces.length > 0) {
      return pieces.join(' · ')
    }
  }
  return 'No extra details.'
}

export function InferenceServicesPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [kindFilter, setKindFilter] = useState('ALL')
  const [providerFilter, setProviderFilter] = useState('ALL')
  const [environmentFilter, setEnvironmentFilter] = useState('ALL')
  const [searchText, setSearchText] = useState('')
  const [scaleValue, setScaleValue] = useState('1')
  const [rotateDialogOpen, setRotateDialogOpen] = useState(false)
  const [rotateSecretValue, setRotateSecretValue] = useState('')
  const [forceRecreateDialogOpen, setForceRecreateDialogOpen] = useState(false)
  const [forceRecreateConfirmation, setForceRecreateConfirmation] = useState('')
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const servicesQuery = useQuery({
    queryKey: ['marketplace', 'inference-services'],
    queryFn: fetchMarketplaceInferenceServices,
  })

  const filteredServices = useMemo(
    () =>
      (servicesQuery.data ?? []).filter(
        (service) =>
          statusMatches(service, statusFilter)
          && kindMatches(service, kindFilter)
          && providerMatches(service, providerFilter)
          && environmentMatches(service, environmentFilter)
          && serviceSearchMatches(service, searchText),
      ),
    [environmentFilter, kindFilter, providerFilter, searchText, servicesQuery.data, statusFilter],
  )

  const selectedServiceRef = searchParams.get('service') ?? ''

  useEffect(() => {
    if (filteredServices.length === 0) {
      return
    }
    const selectedStillVisible = filteredServices.some((service) => service.serviceRef === selectedServiceRef)
    if (!selectedStillVisible) {
      setSearchParams({ service: filteredServices[0].serviceRef }, { replace: true })
    }
  }, [filteredServices, selectedServiceRef, setSearchParams])

  const selectedServiceSummary = useMemo(
    () => filteredServices.find((service) => service.serviceRef === selectedServiceRef) ?? null,
    [filteredServices, selectedServiceRef],
  )

  const selectedServiceQuery = useQuery({
    queryKey: ['marketplace', 'inference-services', selectedServiceRef],
    queryFn: () => fetchMarketplaceInferenceService(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const selectedService = selectedServiceQuery.data ?? selectedServiceSummary

  useEffect(() => {
    if (selectedService) {
      setScaleValue(`${selectedService.desiredReplicas ?? 1}`)
    }
  }, [selectedService?.desiredReplicas, selectedService?.serviceRef])

  const dependentsQuery = useQuery({
    queryKey: ['marketplace', 'inference-services', selectedServiceRef, 'dependents'],
    queryFn: () => fetchMarketplaceInferenceServiceDependents(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const activityQuery = useQuery({
    queryKey: ['marketplace', 'inference-services', selectedServiceRef, 'activity'],
    queryFn: () => fetchMarketplaceInferenceServiceActivity(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const healthQuery = useQuery({
    queryKey: ['marketplace', 'inference-services', selectedServiceRef, 'health'],
    queryFn: () => fetchMarketplaceInferenceServiceHealth(selectedServiceRef),
    enabled: selectedServiceRef.length > 0,
  })

  const refreshServiceState = async (serviceRef: string) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', serviceRef] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', serviceRef, 'dependents'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', serviceRef, 'activity'] }),
      queryClient.invalidateQueries({ queryKey: ['marketplace', 'inference-services', serviceRef, 'health'] }),
    ])
  }

  const reconcileMutation = useMutation({
    mutationFn: (serviceRef: string) => reconcileMarketplaceInferenceService(serviceRef),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Reconciled ${service.displayName}.` })
      await refreshServiceState(service.serviceRef)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to reconcile service.' })
    },
  })

  const restartMutation = useMutation({
    mutationFn: (serviceRef: string) => restartMarketplaceInferenceService(serviceRef),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Restarted ${service.displayName}.` })
      await refreshServiceState(service.serviceRef)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to restart service.' })
    },
  })

  const scaleMutation = useMutation({
    mutationFn: ({ serviceRef, desiredReplicas }: { serviceRef: string; desiredReplicas: number }) =>
      scaleMarketplaceInferenceService(serviceRef, { desiredReplicas }),
    onSuccess: async (service) => {
      setMessage({ type: 'success', text: `Updated replica target for ${service.displayName} to ${service.desiredReplicas ?? 1}.` })
      await refreshServiceState(service.serviceRef)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to scale service.' })
    },
  })

  const rotateSecretMutation = useMutation({
    mutationFn: ({ serviceRef, value }: { serviceRef: string; value: string }) =>
      rotateMarketplaceInferenceServiceSecret(serviceRef, { value }),
    onSuccess: async (service) => {
      setRotateDialogOpen(false)
      setRotateSecretValue('')
      setMessage({ type: 'success', text: `Rotated secret and reconciled ${service.displayName}.` })
      await refreshServiceState(service.serviceRef)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to rotate managed secret.' })
    },
  })

  const forceRecreateMutation = useMutation({
    mutationFn: (serviceRef: string) => forceRecreateMarketplaceInferenceService(serviceRef),
    onSuccess: async (service) => {
      setForceRecreateDialogOpen(false)
      setForceRecreateConfirmation('')
      setMessage({ type: 'success', text: `Force recreated ${service.displayName}.` })
      await refreshServiceState(service.serviceRef)
    },
    onError: (error) => {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : 'Failed to force recreate service.' })
    },
  })

  const uniqueStatuses = useMemo(
    () => Array.from(new Set((servicesQuery.data ?? []).map((service) => service.status))).sort(),
    [servicesQuery.data],
  )
  const uniqueKinds = useMemo(
    () => Array.from(new Set((servicesQuery.data ?? []).map((service) => service.serviceKind))).sort(),
    [servicesQuery.data],
  )
  const uniqueProviders = useMemo(
    () => Array.from(new Set((servicesQuery.data ?? []).map((service) => service.providerType))).sort(),
    [servicesQuery.data],
  )
  const uniqueEnvironments = useMemo(
    () =>
      Array.from(new Set((servicesQuery.data ?? []).map((service) => service.environmentScope ?? 'UNSCOPED'))).sort(),
    [servicesQuery.data],
  )

  const canRotateSecret = Boolean(selectedService?.secretName)
  const forceRecreateRequiresConfirmation = (selectedService?.dependentActiveDeploymentsCount ?? 0) > 0
  const forceRecreateEnabled = selectedService != null
    && (!forceRecreateRequiresConfirmation || forceRecreateConfirmation.trim() === selectedService.serviceRef)

  return (
    <Stack spacing={3}>
      <Stack spacing={1}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Inference Services
        </Typography>
        <Typography color="text.secondary">
          Production operations for platform-managed inference services used by marketplace inference-profile installs.
        </Typography>
      </Stack>

      {message ? <Alert severity={message.type}>{message.text}</Alert> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} lg={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap" useFlexGap>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Services
                  </Typography>
                  <Chip label={`${filteredServices.length} visible`} variant="outlined" />
                </Stack>

                <Grid container spacing={1.5}>
                  <Grid item xs={12}>
                    <TextField
                      fullWidth
                      label="Search"
                      value={searchText}
                      onChange={(event) => setSearchText(event.target.value)}
                      placeholder="Name, ref, provider, model"
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <TextField select fullWidth label="Status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                      <MenuItem value="ALL">All</MenuItem>
                      {uniqueStatuses.map((status) => (
                        <MenuItem key={status} value={status}>
                          {status}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={6}>
                    <TextField select fullWidth label="Kind" value={kindFilter} onChange={(event) => setKindFilter(event.target.value)}>
                      <MenuItem value="ALL">All</MenuItem>
                      {uniqueKinds.map((kind) => (
                        <MenuItem key={kind} value={kind}>
                          {kind}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={6}>
                    <TextField select fullWidth label="Provider" value={providerFilter} onChange={(event) => setProviderFilter(event.target.value)}>
                      <MenuItem value="ALL">All</MenuItem>
                      {uniqueProviders.map((provider) => (
                        <MenuItem key={provider} value={provider}>
                          {provider}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid item xs={6}>
                    <TextField
                      select
                      fullWidth
                      label="Environment"
                      value={environmentFilter}
                      onChange={(event) => setEnvironmentFilter(event.target.value)}
                    >
                      <MenuItem value="ALL">All</MenuItem>
                      {uniqueEnvironments.map((environment) => (
                        <MenuItem key={environment} value={environment}>
                          {environment}
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                </Grid>

                <Divider />

                {servicesQuery.isLoading ? (
                  <Typography color="text.secondary">Loading managed inference services…</Typography>
                ) : filteredServices.length === 0 ? (
                  <Alert severity="info">No inference services match the current filters.</Alert>
                ) : (
                  <List sx={{ p: 0 }}>
                    {filteredServices.map((service) => {
                      const selected = service.serviceRef === selectedServiceRef
                      return (
                        <ListItemButton
                          key={service.id}
                          selected={selected}
                          onClick={() => setSearchParams({ service: service.serviceRef }, { replace: true })}
                          sx={{ borderRadius: 2, mb: 1, border: '1px solid', borderColor: selected ? 'primary.main' : 'divider' }}
                        >
                          <ListItemText
                            primary={service.displayName}
                            secondary={
                              <Stack spacing={0.75} sx={{ mt: 0.75 }}>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Chip size="small" label={service.status} color={chipColor(service.status)} />
                                  <Chip size="small" label={service.serviceKind} variant="outlined" />
                                </Stack>
                                <Typography variant="caption" color="text.secondary">
                                  {service.serviceRef} · {service.providerType} · replicas {service.actualReplicas ?? 0}/{service.desiredReplicas ?? 1}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  Dependents {service.dependentDeploymentsCount} · Last healthy {formatTimestamp(service.lastHealthyAt)}
                                </Typography>
                              </Stack>
                            }
                          />
                        </ListItemButton>
                      )
                    })}
                  </List>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          {selectedService == null ? (
            <Card>
              <CardContent>
                <Typography color="text.secondary">Select an inference service to inspect its production state.</Typography>
              </CardContent>
            </Card>
          ) : (
            <Stack spacing={3}>
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
                      <Box>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="h5" sx={{ fontWeight: 800 }}>
                            {selectedService.displayName}
                          </Typography>
                          <Chip label={selectedService.status} color={chipColor(selectedService.status)} />
                          <Chip label={selectedService.serviceKind} variant="outlined" />
                        </Stack>
                        <Typography color="text.secondary" sx={{ mt: 1 }}>
                          {selectedService.serviceRef} · {selectedService.providerType} · {selectedService.deploymentMode}
                        </Typography>
                      </Box>
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
                        <Button
                          variant="outlined"
                          startIcon={<RefreshRoundedIcon />}
                          onClick={() => {
                            setMessage(null)
                            void healthQuery.refetch()
                          }}
                          disabled={healthQuery.isFetching}
                        >
                          Run health check
                        </Button>
                        <Button
                          variant="outlined"
                          startIcon={<AutorenewRoundedIcon />}
                          onClick={() => {
                            setMessage(null)
                            reconcileMutation.mutate(selectedService.serviceRef)
                          }}
                          disabled={reconcileMutation.isPending}
                        >
                          Reconcile
                        </Button>
                        <Button
                          variant="outlined"
                          startIcon={<RestartAltRoundedIcon />}
                          onClick={() => {
                            setMessage(null)
                            restartMutation.mutate(selectedService.serviceRef)
                          }}
                          disabled={restartMutation.isPending}
                        >
                          Restart
                        </Button>
                        <Button
                          variant="outlined"
                          startIcon={<KeyRoundedIcon />}
                          onClick={() => {
                            setMessage(null)
                            setRotateDialogOpen(true)
                          }}
                          disabled={!canRotateSecret}
                        >
                          Rotate secret
                        </Button>
                        <Button
                          variant="contained"
                          color="warning"
                          startIcon={<WarningAmberRoundedIcon />}
                          onClick={() => {
                            setMessage(null)
                            setForceRecreateDialogOpen(true)
                          }}
                          disabled={forceRecreateMutation.isPending}
                        >
                          Force recreate
                        </Button>
                        {selectedService.baseUrl ? (
                          <Button
                            variant="text"
                            endIcon={<LaunchRoundedIcon />}
                            onClick={() => window.open(selectedService.baseUrl ?? '', '_blank', 'noopener,noreferrer')}
                          >
                            Open service
                          </Button>
                        ) : null}
                      </Stack>
                    </Stack>

                    {activeDependentWarning(selectedService) ? (
                      <Alert severity="warning">{activeDependentWarning(selectedService)}</Alert>
                    ) : null}
                    {selectedService.driftStatus && selectedService.driftStatus !== 'NO_DRIFT' ? (
                      <Alert severity="warning">{selectedService.driftMessage ?? 'Service drift detected.'}</Alert>
                    ) : null}
                    {!selectedService.secretConfigured && selectedService.secretName ? (
                      <Alert severity="error">Expected secret `{selectedService.secretName}` is not currently configured.</Alert>
                    ) : null}

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={3}>
                        <Card variant="outlined">
                          <CardContent>
                            <Stack spacing={0.75}>
                              <Typography variant="body2" color="text.secondary">Replicas</Typography>
                              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                                {selectedService.actualReplicas ?? 0}/{selectedService.desiredReplicas ?? 1}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                Autoscaling {detailValue(selectedService.autoscalingMode)}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <Card variant="outlined">
                          <CardContent>
                            <Stack spacing={0.75}>
                              <Typography variant="body2" color="text.secondary">Last healthy</Typography>
                              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                                {formatTimestamp(selectedService.lastHealthyAt)}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                Probe {detailValue(selectedService.lastProbeStatus)}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <Card variant="outlined">
                          <CardContent>
                            <Stack spacing={0.75}>
                              <Typography variant="body2" color="text.secondary">Dependents</Typography>
                              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                                {selectedService.dependentDeploymentsCount}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                Active {selectedService.dependentActiveDeploymentsCount}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <Card variant="outlined">
                          <CardContent>
                            <Stack spacing={0.75}>
                              <Typography variant="body2" color="text.secondary">Drift</Typography>
                              <Chip size="small" label={detailValue(selectedService.driftStatus)} color={chipColor(selectedService.driftStatus)} />
                              <Typography variant="caption" color="text.secondary">
                                Secret {selectedService.secretConfigured ? 'configured' : 'missing'}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                  </Stack>
                </CardContent>
              </Card>

              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                  <Card sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={1.5}>
                        <Typography variant="h6" sx={{ fontWeight: 700 }}>
                          Identity
                        </Typography>
                        <Typography variant="body2"><strong>Service ref:</strong> {detailValue(selectedService.serviceRef)}</Typography>
                        <Typography variant="body2"><strong>Provider:</strong> {detailValue(selectedService.providerType)}</Typography>
                        <Typography variant="body2"><strong>Protocol:</strong> {detailValue(selectedService.protocolType)}</Typography>
                        <Typography variant="body2"><strong>Model:</strong> {detailValue(selectedService.modelId)}</Typography>
                        <Typography variant="body2"><strong>Environment:</strong> {detailValue(selectedService.environmentScope)}</Typography>
                        <Typography variant="body2"><strong>Tier:</strong> {detailValue(selectedService.tierScope)}</Typography>
                        <Typography variant="body2"><strong>Deployment:</strong> {detailValue(selectedService.deploymentId)}</Typography>
                        <Typography variant="body2"><strong>Last deployment id:</strong> {detailValue(selectedService.lastDeploymentId)}</Typography>
                        <Typography variant="body2"><strong>Last reconciled:</strong> {formatTimestamp(selectedService.lastReconciledAt)}</Typography>
                        <Typography variant="body2"><strong>Reconcile status:</strong> {detailValue(selectedService.lastReconcileStatus)}</Typography>
                        <Typography variant="body2" color="text.secondary">{detailValue(selectedService.lastReconcileMessage)}</Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Card sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={1.5}>
                        <Typography variant="h6" sx={{ fontWeight: 700 }}>
                          Runtime and linkage
                        </Typography>
                        <Typography variant="body2"><strong>Base URL:</strong> {detailValue(selectedService.baseUrl)}</Typography>
                        <Typography variant="body2"><strong>Private URL:</strong> {detailValue(selectedService.privateNetworkUrl)}</Typography>
                        <Typography variant="body2"><strong>Health path:</strong> {detailValue(selectedService.healthPath)}</Typography>
                        <Typography variant="body2"><strong>Railway project:</strong> {detailValue(selectedService.railwayProjectId)}</Typography>
                        <Typography variant="body2"><strong>Railway environment:</strong> {detailValue(selectedService.railwayEnvironmentId)}</Typography>
                        <Typography variant="body2"><strong>Railway service:</strong> {detailValue(selectedService.railwayServiceId)}</Typography>
                        <Typography variant="body2"><strong>Secret name:</strong> {detailValue(selectedService.secretName)}</Typography>
                        <Typography variant="body2"><strong>Secret configured:</strong> {selectedService.secretConfigured ? 'Yes' : 'No'}</Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>

              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                  {probeCard(healthQuery.data?.healthProbe, 'Service health probe')}
                </Grid>
                <Grid item xs={12} md={6}>
                  {probeCard(healthQuery.data?.inferenceProbe, 'Authenticated inference probe')}
                </Grid>
              </Grid>

              <Grid container spacing={3}>
                <Grid item xs={12} md={5}>
                  <Card sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <HubRoundedIcon color="primary" />
                          <Typography variant="h6" sx={{ fontWeight: 700 }}>
                            Endpoints
                          </Typography>
                        </Stack>
                        {selectedService.endpoints.length === 0 ? (
                          <Typography color="text.secondary">No endpoint profiles are currently attached to this service.</Typography>
                        ) : (
                          <List sx={{ p: 0 }}>
                            {selectedService.endpoints.map((endpoint) => (
                              <ListItemButton key={endpoint.id} sx={{ borderRadius: 2, mb: 1, border: '1px solid', borderColor: 'divider' }}>
                                <ListItemText
                                  primary={`${endpoint.endpointPurpose ?? 'UNKNOWN'} · ${endpoint.profileRef}`}
                                  secondary={
                                    <Stack spacing={0.5} sx={{ mt: 0.75 }}>
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                        <Chip size="small" label={endpoint.status} color={chipColor(endpoint.status)} />
                                        <Chip size="small" label={endpoint.providerType} variant="outlined" />
                                      </Stack>
                                      <Typography variant="caption" color="text.secondary">
                                        {detailValue(endpoint.baseUrl)}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Secret {detailValue(endpoint.secretName)} · API {detailValue(endpoint.apiVersion)}
                                      </Typography>
                                    </Stack>
                                  }
                                />
                              </ListItemButton>
                            ))}
                          </List>
                        )}
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={12} md={7}>
                  <Card sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <MemoryRoundedIcon color="primary" />
                          <Typography variant="h6" sx={{ fontWeight: 700 }}>
                            Dependent deployments
                          </Typography>
                        </Stack>
                        {dependentsQuery.isLoading ? (
                          <Typography color="text.secondary">Loading dependent deployments…</Typography>
                        ) : (dependentsQuery.data ?? []).length === 0 ? (
                          <Typography color="text.secondary">No deployments currently reference this service.</Typography>
                        ) : (
                          <List sx={{ p: 0 }}>
                            {(dependentsQuery.data ?? []).map((dependent: PlatformManagedInferenceDependentDeploymentSummary) => (
                              <ListItemButton key={dependent.deploymentId} sx={{ borderRadius: 2, mb: 1, border: '1px solid', borderColor: 'divider' }}>
                                <ListItemText
                                  primary={`${dependent.deploymentName} (${dependent.environmentName})`}
                                  secondary={
                                    <Stack spacing={0.5} sx={{ mt: 0.75 }}>
                                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                        <Chip size="small" label={dependent.deploymentStatus} color={chipColor(dependent.deploymentStatus)} />
                                        {dependent.runtimeActive ? <Chip size="small" label="Runtime active" color="success" variant="outlined" /> : null}
                                        {dependent.currentDraftUsesService ? <Chip size="small" label="Draft uses service" variant="outlined" /> : null}
                                        {dependent.activeVersionUsesService ? <Chip size="small" label="Active version uses service" variant="outlined" /> : null}
                                      </Stack>
                                      <Typography variant="caption" color="text.secondary">
                                        {dependent.deploymentId} · version {detailValue(dependent.activeVersionId)}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Usages: {dependent.usages.join(', ')}
                                      </Typography>
                                    </Stack>
                                  }
                                />
                              </ListItemButton>
                            ))}
                          </List>
                        )}
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>

              <Card>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Recent activity
                    </Typography>
                    {activityQuery.isLoading ? (
                      <Typography color="text.secondary">Loading service activity…</Typography>
                    ) : (activityQuery.data ?? []).length === 0 ? (
                      <Typography color="text.secondary">No recent activity is recorded for this service.</Typography>
                    ) : (
                      <List sx={{ p: 0 }}>
                        {(activityQuery.data ?? []).map((event: PlatformAuditEventSummary) => (
                          <ListItemButton key={event.id} sx={{ borderRadius: 2, mb: 1, border: '1px solid', borderColor: 'divider' }}>
                            <ListItemText
                              primary={`${event.action} · ${event.actorId}`}
                              secondary={
                                <Stack spacing={0.5} sx={{ mt: 0.75 }}>
                                  <Typography variant="caption" color="text.secondary">
                                    {event.actorRole} · {formatTimestamp(event.createdAt)}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {activitySummary(event)}
                                  </Typography>
                                </Stack>
                              }
                            />
                          </ListItemButton>
                        ))}
                      </List>
                    )}
                  </Stack>
                </CardContent>
              </Card>

              <Card>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Scale
                    </Typography>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', sm: 'center' }}>
                      <TextField
                        label="Desired replicas"
                        type="number"
                        value={scaleValue}
                        onChange={(event) => setScaleValue(event.target.value)}
                        sx={{ maxWidth: 220 }}
                        inputProps={{ min: 1 }}
                      />
                      <Typography color="text.secondary" variant="body2">
                        Allowed range {detailValue(selectedService.minReplicas ?? 1)} to {detailValue(selectedService.maxReplicas ?? Math.max(selectedService.minReplicas ?? 1, 1))}
                      </Typography>
                      <Button
                        variant="contained"
                        onClick={() => {
                          const desiredReplicas = Number(scaleValue)
                          if (!Number.isInteger(desiredReplicas) || desiredReplicas <= 0) {
                            setMessage({ type: 'error', text: 'Desired replicas must be a positive integer.' })
                            return
                          }
                          setMessage(null)
                          scaleMutation.mutate({ serviceRef: selectedService.serviceRef, desiredReplicas })
                        }}
                        disabled={scaleMutation.isPending}
                      >
                        Apply scale
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          )}
        </Grid>
      </Grid>

      <Dialog open={rotateDialogOpen} onClose={() => setRotateDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Rotate managed service secret</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning">
              This updates the platform-managed secret and immediately reconciles the selected service.
            </Alert>
            <TextField
              fullWidth
              label="New secret value"
              type="password"
              value={rotateSecretValue}
              onChange={(event) => setRotateSecretValue(event.target.value)}
              autoFocus
            />
            <Typography variant="body2" color="text.secondary">
              Secret: {detailValue(selectedService?.secretName)}
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRotateDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              if (!selectedService) {
                return
              }
              if (rotateSecretValue.trim().length === 0) {
                setMessage({ type: 'error', text: 'Secret value is required.' })
                return
              }
              setMessage(null)
              rotateSecretMutation.mutate({ serviceRef: selectedService.serviceRef, value: rotateSecretValue.trim() })
            }}
            disabled={rotateSecretMutation.isPending}
          >
            Rotate and reconcile
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={forceRecreateDialogOpen} onClose={() => setForceRecreateDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Force recreate service</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="error">
              Force recreate clears the stored Railway binding and provisions the service again from scratch.
            </Alert>
            {forceRecreateRequiresConfirmation ? (
              <>
                <Alert severity="warning">
                  {activeDependentWarning(selectedService)}
                </Alert>
                <Typography variant="body2" color="text.secondary">
                  Type <strong>{selectedService?.serviceRef}</strong> to confirm.
                </Typography>
                <TextField
                  fullWidth
                  label="Confirmation"
                  value={forceRecreateConfirmation}
                  onChange={(event) => setForceRecreateConfirmation(event.target.value)}
                />
              </>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setForceRecreateDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="warning"
            onClick={() => {
              if (!selectedService) {
                return
              }
              setMessage(null)
              forceRecreateMutation.mutate(selectedService.serviceRef)
            }}
            disabled={!forceRecreateEnabled || forceRecreateMutation.isPending}
          >
            Force recreate
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
