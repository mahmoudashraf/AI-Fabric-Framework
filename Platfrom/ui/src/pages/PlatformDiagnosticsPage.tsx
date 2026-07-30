import BugReportRoundedIcon from '@mui/icons-material/BugReportRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
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
import { useMemo, useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { HostedVerificationRunHistory } from '../components/HostedVerificationRunHistory'
import {
  deployPlatformCoreService,
  fetchPlatformCoreServices,
  fetchPlatformDiagnostics,
  fetchPlatformDiagnosticsLogs,
  fetchProductServices,
  reconcileProductService,
  restartPlatformCoreService,
  restartProductService,
  type PlatformCoreServiceSummary,
  type PlatformManagedProductServiceSummary,
  type RailwayLogEntrySummary,
} from '../api/platformApi'

function formatOptional(value: string | null | undefined): string {
  return value && value.trim().length > 0 ? value : '—'
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  const normalized = status.toUpperCase()
  if (
    normalized === 'PASSED'
    || normalized === 'READY'
    || normalized === 'SUCCESS'
    || normalized === 'ACTIVE'
    || normalized === 'APPLIED_VERIFIED'
    || normalized.includes('HEALTHY')
  ) {
    return 'success'
  }
  if (normalized === 'RUNNING' || normalized === 'QUEUED' || normalized === 'DEPLOYING' || normalized === 'INITIALIZING') {
    return 'info'
  }
  if (normalized === 'WARNING' || normalized === 'AWAITING_CONFIRMATION' || normalized === 'DEGRADED') {
    return 'warning'
  }
  if (normalized === 'FAILED' || normalized === 'ERROR' || normalized === 'REMOVED' || normalized === 'UNAVAILABLE') {
    return 'error'
  }
  return 'default'
}

function severityColor(entry: RailwayLogEntrySummary): 'default' | 'error' | 'warning' | 'info' | 'success' {
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

function selectBridgeService(services: PlatformManagedProductServiceSummary[]): PlatformManagedProductServiceSummary | null {
  return services.find((service) => service.serviceRef === 'loomai-shopify-bridge-prod')
    ?? services.find((service) => service.serviceRef === 'shopify-bridge-prod')
    ?? services.find((service) => service.baseUrl?.includes('shopify-bridge.loomai.pro'))
    ?? services.find((service) => service.serviceKind === 'SHOPIFY_BRIDGE_SERVICE' && service.environmentScope === 'production')
    ?? services.find((service) => service.serviceKind === 'SHOPIFY_BRIDGE_SERVICE')
    ?? null
}

function coreServiceRows(services: PlatformCoreServiceSummary[]) {
  return services.map((service) => ({
    key: service.serviceRef,
    name: service.displayName,
    kind: service.serviceKind ?? service.managementMode,
    status: service.status,
    observedStatus: service.observedStatus,
    url: service.publicBaseUrl,
    healthUrl: service.healthUrl,
    message: service.message,
    observedAt: service.observedAt,
  }))
}

export function PlatformDiagnosticsPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const [source, setSource] = useState('deployment')
  const [filter, setFilter] = useState('')
  const [coreServiceMessage, setCoreServiceMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const adminEnabled = auth.session?.enabled ? auth.session.canManageUsers : true

  const diagnosticsQuery = useQuery({
    queryKey: ['platform-diagnostics'],
    queryFn: fetchPlatformDiagnostics,
    enabled: adminEnabled,
  })

  const logsQuery = useQuery({
    queryKey: ['platform-diagnostics-logs', source, filter],
    queryFn: () => fetchPlatformDiagnosticsLogs({
      source,
      filter: filter.trim() || undefined,
      limit: 150,
    }),
    enabled: adminEnabled,
  })

  const coreServicesQuery = useQuery({
    queryKey: ['platform-core-services'],
    queryFn: fetchPlatformCoreServices,
    enabled: adminEnabled,
  })

  const productServicesQuery = useQuery({
    queryKey: ['product-services'],
    queryFn: fetchProductServices,
    enabled: adminEnabled,
  })

  const bridgeService = useMemo(
    () => selectBridgeService(productServicesQuery.data ?? []),
    [productServicesQuery.data],
  )

  const refreshOperations = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['platform-core-services'] }),
      queryClient.invalidateQueries({ queryKey: ['product-services'] }),
    ])
  }

  const deployCoreServiceMutation = useMutation({
    mutationFn: (serviceRef: string) => deployPlatformCoreService(serviceRef),
    onSuccess: async (summary) => {
      setCoreServiceMessage({ type: 'success', text: `Deploy requested for ${summary.displayName}.` })
      await refreshOperations()
    },
    onError: (error) =>
      setCoreServiceMessage({
        type: 'error',
        text: error instanceof Error ? error.message : 'Failed to request core service deploy.',
      }),
  })

  const restartCoreServiceMutation = useMutation({
    mutationFn: (serviceRef: string) => restartPlatformCoreService(serviceRef),
    onSuccess: async (summary) => {
      setCoreServiceMessage({ type: 'success', text: `Restart requested for ${summary.displayName}.` })
      await refreshOperations()
    },
    onError: (error) =>
      setCoreServiceMessage({
        type: 'error',
        text: error instanceof Error ? error.message : 'Failed to request core service restart.',
      }),
  })

  const reconcileBridgeMutation = useMutation({
    mutationFn: (serviceRef: string) => reconcileProductService(serviceRef),
    onSuccess: async (summary) => {
      setCoreServiceMessage({ type: 'success', text: `Reconcile requested for ${summary.displayName}.` })
      await refreshOperations()
    },
    onError: (error) =>
      setCoreServiceMessage({
        type: 'error',
        text: error instanceof Error ? error.message : 'Failed to reconcile Shopify Bridge service.',
      }),
  })

  const restartBridgeMutation = useMutation({
    mutationFn: (serviceRef: string) => restartProductService(serviceRef),
    onSuccess: async (summary) => {
      setCoreServiceMessage({ type: 'success', text: `Restart requested for ${summary.displayName}.` })
      await refreshOperations()
    },
    onError: (error) =>
      setCoreServiceMessage({
        type: 'error',
        text: error instanceof Error ? error.message : 'Failed to restart Shopify Bridge service.',
      }),
  })

  if (auth.session?.enabled && !auth.session.canManageUsers) {
    return (
      <Alert severity="warning">
        Platform diagnostics are restricted to platform admins because they expose platform-level provider service details and log output.
      </Alert>
    )
  }

  return (
    <Stack spacing={2.5}>
      <Box>
        <Stack direction="row" spacing={1.25} alignItems="center">
          <BugReportRoundedIcon color="primary" />
          <Typography variant="h4">Platform Diagnostics</Typography>
        </Stack>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.75 }}>
          Inspect the platform deployment itself, including provider service discovery, platform-hosted verification runs,
          and platform provider logs.
        </Typography>
      </Box>

      {diagnosticsQuery.isLoading ? (
        <Alert severity="info">Loading platform diagnostics…</Alert>
      ) : diagnosticsQuery.isError ? (
        <Alert severity="error">
          {diagnosticsQuery.error instanceof Error ? diagnosticsQuery.error.message : 'Failed to load platform diagnostics'}
        </Alert>
      ) : diagnosticsQuery.data ? (
        <>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Platform Summary</Typography>
                <Alert severity={diagnosticsQuery.data.railwayService.available ? 'success' : 'warning'}>
                  {diagnosticsQuery.data.summaryMessage}
                </Alert>
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell>Platform</TableCell>
                      <TableCell>{diagnosticsQuery.data.name}</TableCell>
                      <TableCell>Stage</TableCell>
                      <TableCell>{diagnosticsQuery.data.stage}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Phase</TableCell>
                      <TableCell>{diagnosticsQuery.data.currentPhase}</TableCell>
                      <TableCell>Public base URL</TableCell>
                      <TableCell>{diagnosticsQuery.data.publicBaseUrl}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Provisioning mode</TableCell>
                      <TableCell>{diagnosticsQuery.data.provisioningMode}</TableCell>
                      <TableCell>Workspace</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.workspaceId)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Repository</TableCell>
                      <TableCell>{diagnosticsQuery.data.repository}</TableCell>
                      <TableCell>Branch</TableCell>
                      <TableCell>{diagnosticsQuery.data.branch}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.25} alignItems={{ md: 'center' }}>
                  <Typography variant="h6" sx={{ flex: 1 }}>Core Service Operations</Typography>
                  <Button
                    variant="outlined"
                    startIcon={<RefreshRoundedIcon />}
                    onClick={() => {
                      void coreServicesQuery.refetch()
                      void productServicesQuery.refetch()
                    }}
                  >
                    Refresh
                  </Button>
                </Stack>
                {coreServiceMessage ? (
                  <Alert severity={coreServiceMessage.type}>{coreServiceMessage.text}</Alert>
                ) : null}
                {coreServicesQuery.isLoading || productServicesQuery.isLoading ? (
                  <Alert severity="info">Loading service operations…</Alert>
                ) : coreServicesQuery.isError ? (
                  <Alert severity="error">
                    {coreServicesQuery.error instanceof Error ? coreServicesQuery.error.message : 'Failed to load core services.'}
                  </Alert>
                ) : productServicesQuery.isError ? (
                  <Alert severity="warning">
                    {productServicesQuery.error instanceof Error ? productServicesQuery.error.message : 'Failed to load managed product services.'}
                  </Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Service</TableCell>
                        <TableCell>Management</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>URL</TableCell>
                        <TableCell>Last observed</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {coreServiceRows(coreServicesQuery.data ?? []).map((service) => (
                        <TableRow key={service.key} hover>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>{service.name}</Typography>
                              <Typography variant="caption" color="text.secondary">{service.key}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{service.kind}</TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Chip size="small" label={service.status} color={statusColor(service.status)} variant="outlined" />
                              {service.observedStatus ? (
                                <Typography variant="caption" color="text.secondary">{service.observedStatus}</Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap>
                              {service.url ? (
                                <Button
                                  href={service.url}
                                  target="_blank"
                                  rel="noreferrer"
                                  size="small"
                                  variant="text"
                                  startIcon={<OpenInNewRoundedIcon />}
                                >
                                  Open
                                </Button>
                              ) : (
                                <Typography variant="body2">—</Typography>
                              )}
                              {service.healthUrl ? (
                                <Button
                                  href={service.healthUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  size="small"
                                  variant="text"
                                >
                                  Health
                                </Button>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">{formatTimestamp(service.observedAt)}</Typography>
                              <Typography variant="caption" color="text.secondary">{service.message}</Typography>
                            </Stack>
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<RocketLaunchRoundedIcon />}
                                disabled={deployCoreServiceMutation.isPending || restartCoreServiceMutation.isPending}
                                onClick={() => deployCoreServiceMutation.mutate(service.key)}
                              >
                                Deploy
                              </Button>
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<RestartAltRoundedIcon />}
                                disabled={deployCoreServiceMutation.isPending || restartCoreServiceMutation.isPending}
                                onClick={() => restartCoreServiceMutation.mutate(service.key)}
                              >
                                Restart
                              </Button>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      ))}
                      <TableRow hover>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {bridgeService?.displayName ?? 'Shopify Bridge'}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {bridgeService?.serviceRef ?? 'managed product service not found'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>PRODUCT_SERVICE</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={bridgeService?.status ?? 'NOT_FOUND'}
                            color={statusColor(bridgeService?.status ?? 'NOT_FOUND')}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap>
                            {bridgeService?.baseUrl ? (
                              <Button
                                href={bridgeService.baseUrl}
                                target="_blank"
                                rel="noreferrer"
                                size="small"
                                variant="text"
                                startIcon={<OpenInNewRoundedIcon />}
                              >
                                Open
                              </Button>
                            ) : (
                              <Typography variant="body2">—</Typography>
                            )}
                            <Button
                              href={bridgeService ? `/product-services?service=${encodeURIComponent(bridgeService.serviceRef)}` : '/product-services'}
                              size="small"
                              variant="text"
                            >
                              Product Services
                            </Button>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{formatTimestamp(bridgeService?.lastReconciledAt)}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {bridgeService?.lastReconcileMessage ?? bridgeService?.lastProbeMessage ?? 'Managed by Product Services.'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<RocketLaunchRoundedIcon />}
                              disabled={!bridgeService || reconcileBridgeMutation.isPending || restartBridgeMutation.isPending}
                              onClick={() => bridgeService && reconcileBridgeMutation.mutate(bridgeService.serviceRef)}
                            >
                              Reconcile
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<RestartAltRoundedIcon />}
                              disabled={!bridgeService || reconcileBridgeMutation.isPending || restartBridgeMutation.isPending}
                              onClick={() => bridgeService && restartBridgeMutation.mutate(bridgeService.serviceRef)}
                            >
                              Restart
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Provider Discovery</Typography>
                {diagnosticsQuery.data.railwayPreflightError ? (
                  <Alert severity="warning">{diagnosticsQuery.data.railwayPreflightError}</Alert>
                ) : diagnosticsQuery.data.railwayPreflight ? (
                  <Alert severity={diagnosticsQuery.data.railwayPreflight.ready ? 'success' : 'warning'}>
                    Provider preflight is {diagnosticsQuery.data.railwayPreflight.ready ? 'ready' : 'not ready'} for the platform workspace.
                  </Alert>
                ) : null}
                <Table size="small">
                  <TableBody>
                    <TableRow>
                      <TableCell>Public host</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.publicHost)}</TableCell>
                      <TableCell>Matched domain</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.domain)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Project</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.projectName)}</TableCell>
                      <TableCell>Environment</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.environmentName)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Service</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.serviceName)}</TableCell>
                      <TableCell>Latest deployment</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Typography variant="body2">{formatOptional(diagnosticsQuery.data.railwayService.latestDeploymentId)}</Typography>
                          {diagnosticsQuery.data.railwayService.latestDeploymentStatus ? (
                            <Chip
                              size="small"
                              label={diagnosticsQuery.data.railwayService.latestDeploymentStatus}
                              color={statusColor(diagnosticsQuery.data.railwayService.latestDeploymentStatus)}
                              variant="outlined"
                            />
                          ) : null}
                        </Stack>
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Root directory</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.rootDirectory)}</TableCell>
                      <TableCell>Dockerfile</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.dockerfilePath)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Trigger repository</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.triggerRepository)}</TableCell>
                      <TableCell>Trigger branch</TableCell>
                      <TableCell>{formatOptional(diagnosticsQuery.data.railwayService.triggerBranch)}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Recent Platform-Hosted Verification Runs</Typography>
                {(diagnosticsQuery.data.recentHostedVerificationRuns ?? []).length === 0 ? (
                  <Alert severity="info">No platform-hosted verification runs have been recorded yet.</Alert>
                ) : (
                  <HostedVerificationRunHistory
                    runs={diagnosticsQuery.data.recentHostedVerificationRuns.slice(0, 10)}
                    showDeploymentId
                  />
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
                  <Typography variant="h6">Platform Provider Logs</Typography>
                  <TextField
                    select
                    size="small"
                    label="Source"
                    value={source}
                    onChange={(event) => setSource(event.target.value)}
                    sx={{ minWidth: 180 }}
                  >
                    <MenuItem value="deployment">Deployment logs</MenuItem>
                    <MenuItem value="build">Build logs</MenuItem>
                    <MenuItem value="http">HTTP logs</MenuItem>
                  </TextField>
                  <TextField
                    size="small"
                    label="Filter"
                    value={filter}
                    onChange={(event) => setFilter(event.target.value)}
                    placeholder="optional substring"
                    sx={{ minWidth: 220 }}
                  />
                  <Button
                    variant="outlined"
                    startIcon={<RefreshRoundedIcon />}
                    onClick={() => {
                      void diagnosticsQuery.refetch()
                      void logsQuery.refetch()
                    }}
                  >
                    Refresh
                  </Button>
                </Stack>

                {logsQuery.isLoading ? (
                  <Alert severity="info">Loading platform provider logs…</Alert>
                ) : logsQuery.isError ? (
                  <Alert severity="error">
                    {logsQuery.error instanceof Error ? logsQuery.error.message : 'Failed to load platform provider logs'}
                  </Alert>
                ) : logsQuery.data ? (
                  <>
                    <Alert severity={logsQuery.data.available ? 'info' : 'warning'}>{logsQuery.data.message}</Alert>
                    {logsQuery.data.entries.length > 0 ? (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Time</TableCell>
                            <TableCell>Severity</TableCell>
                            <TableCell>Message</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {logsQuery.data.entries.map((entry, index) => (
                            <TableRow key={`${entry.timestamp ?? 'na'}-${index}`} hover>
                              <TableCell>{formatTimestamp(entry.timestamp)}</TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={entry.severity ?? 'UNKNOWN'}
                                  color={severityColor(entry)}
                                  variant="outlined"
                                />
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
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  )
}
