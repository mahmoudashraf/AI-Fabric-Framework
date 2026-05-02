import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import HealthAndSafetyRoundedIcon from '@mui/icons-material/HealthAndSafetyRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import StopRoundedIcon from '@mui/icons-material/StopRounded'
import TerminalRoundedIcon from '@mui/icons-material/TerminalRounded'
import TuneRoundedIcon from '@mui/icons-material/TuneRounded'
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
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import {
  deleteDeploymentProviderResource,
  fetchDeploymentProviderResourceLogs,
  fetchDeploymentProviderResourceStatus,
  fetchDeploymentProviderResources,
  fetchDeploymentTargetProfilePreflight,
  fetchDeploymentTargetProfiles,
  patchDeploymentTargetProfile,
  restartDeploymentProviderResource,
  startDeploymentProviderResource,
  stopDeploymentProviderResource,
  type DeploymentProviderPreflightSummary,
  type DeploymentProviderResourceActionSummary,
  type DeploymentProviderResourceHandleSummary,
  type DeploymentProviderResourceLogsSummary,
  type DeploymentProviderResourceStatusSummary,
  type DeploymentTargetProfileSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

type DeploymentProviderOperationsPanelProps = {
  deploymentId: string
}

type ProviderResourceAction = 'start' | 'stop' | 'restart' | 'delete'

function statusChipColor(status: string | null | undefined): 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info' {
  switch ((status ?? '').toUpperCase()) {
    case 'READY':
    case 'PASSED':
    case 'ACTIVE':
    case 'RUNNING':
    case 'OK':
      return 'success'
    case 'FAILED':
    case 'ERROR':
    case 'BLOCKED':
    case 'UNHEALTHY':
      return 'error'
    case 'WARNING':
    case 'DEGRADED':
    case 'DEPLOY_REQUESTED':
      return 'warning'
    case 'COOLIFY':
      return 'secondary'
    case 'RAILWAY_API':
    case 'RAILWAY_STUB':
      return 'primary'
    default:
      return 'default'
  }
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '-'
}

function profileSummary(profile: DeploymentTargetProfileSummary): string {
  const parts = [
    profile.providerType,
    profile.environmentName,
    profile.region,
    profile.sourceStrategy,
  ].filter(Boolean)
  return parts.join(' · ')
}

function resourceUrl(resource: DeploymentProviderResourceHandleSummary): string | null {
  if (!resource.fqdn) {
    return null
  }
  return resource.fqdn.startsWith('http://') || resource.fqdn.startsWith('https://')
    ? resource.fqdn
    : `https://${resource.fqdn}`
}

function actionReason(action: ProviderResourceAction): string {
  return `Operator requested ${action} from the Platform provider operations UI.`
}

export function DeploymentProviderOperationsPanel({ deploymentId }: DeploymentProviderOperationsPanelProps) {
  const queryClient = useQueryClient()
  const auth = usePlatformAuth()
  const [lastPreflight, setLastPreflight] = useState<DeploymentProviderPreflightSummary | null>(null)
  const [lastStatus, setLastStatus] = useState<DeploymentProviderResourceStatusSummary | null>(null)
  const [logsDialogOpen, setLogsDialogOpen] = useState(false)

  const authDisabled = auth.session?.enabled === false
  const isPlatformAdmin = authDisabled || auth.session?.role === 'PLATFORM_ADMIN'
  const canOperateProviderResources = authDisabled
    || auth.session?.role === 'PLATFORM_ADMIN'
    || auth.session?.role === 'PLATFORM_OPERATOR'
    || auth.session?.canOperateDeployments === true

  const targetProfilesQuery = useQuery({
    queryKey: ['deployment-target-profiles'],
    queryFn: () => fetchDeploymentTargetProfiles(),
  })

  const resourcesQuery = useQuery({
    queryKey: ['deployment-provider-resources', deploymentId],
    queryFn: () => fetchDeploymentProviderResources({ deploymentId }),
    enabled: deploymentId.length > 0,
    refetchInterval: (query) => (
      ((query.state.data as DeploymentProviderResourceHandleSummary[] | undefined) ?? [])
        .some((resource) => !['ACTIVE', 'DELETED'].includes(resource.status))
        ? 10_000
        : false
    ),
  })

  const runtimeDefaultProfile = useMemo(
    () => (targetProfilesQuery.data ?? []).find((profile) => profile.active && profile.defaultForRuntime) ?? null,
    [targetProfilesQuery.data],
  )

  const preflightMutation = useMutation({
    mutationFn: fetchDeploymentTargetProfilePreflight,
    onSuccess: (data) => setLastPreflight(data),
  })

  const patchProfileMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof patchDeploymentTargetProfile>[1] }) =>
      patchDeploymentTargetProfile(id, payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-target-profiles'] })
    },
  })

  const statusMutation = useMutation({
    mutationFn: fetchDeploymentProviderResourceStatus,
    onSuccess: (data) => setLastStatus(data),
  })

  const logsMutation = useMutation({
    mutationFn: (handleId: string) => fetchDeploymentProviderResourceLogs(handleId, 200),
    onSuccess: () => setLogsDialogOpen(true),
  })

  const resourceActionMutation = useMutation({
    mutationFn: ({ handleId, action }: { handleId: string; action: ProviderResourceAction }) => {
      if (action === 'start') {
        return startDeploymentProviderResource(handleId, actionReason(action))
      }
      if (action === 'stop') {
        return stopDeploymentProviderResource(handleId, actionReason(action))
      }
      if (action === 'restart') {
        return restartDeploymentProviderResource(handleId, actionReason(action))
      }
      return deleteDeploymentProviderResource(handleId, actionReason(action))
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-provider-resources', deploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases', deploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', deploymentId] }),
      ])
    },
  })

  const renderProfileActions = (profile: DeploymentTargetProfileSummary) => (
    <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap" useFlexGap>
      <Tooltip title="Run provider preflight for this target profile">
        <span>
          <Button
            size="small"
            variant="outlined"
            startIcon={<HealthAndSafetyRoundedIcon />}
            disabled={preflightMutation.isPending}
            onClick={() => preflightMutation.mutate(profile.id)}
          >
            Preflight
          </Button>
        </span>
      </Tooltip>
      {isPlatformAdmin && !profile.active ? (
        <Button
          size="small"
          variant="outlined"
          startIcon={<CheckCircleRoundedIcon />}
          disabled={patchProfileMutation.isPending}
          onClick={() => patchProfileMutation.mutate({ id: profile.id, payload: { active: true } })}
        >
          Activate
        </Button>
      ) : null}
      {isPlatformAdmin ? (
        <>
          <Button
            size="small"
            variant={profile.defaultForRuntime ? 'contained' : 'outlined'}
            startIcon={<TuneRoundedIcon />}
            disabled={patchProfileMutation.isPending || profile.defaultForRuntime}
            onClick={() =>
              patchProfileMutation.mutate({
                id: profile.id,
                payload: { active: true, defaultForRuntime: true },
              })}
          >
            Runtime default
          </Button>
          <Button
            size="small"
            variant={profile.defaultForRestartableServices ? 'contained' : 'outlined'}
            startIcon={<TuneRoundedIcon />}
            disabled={patchProfileMutation.isPending || profile.defaultForRestartableServices}
            onClick={() =>
              patchProfileMutation.mutate({
                id: profile.id,
                payload: { active: true, defaultForRestartableServices: true },
              })}
          >
            Service default
          </Button>
        </>
      ) : null}
    </Stack>
  )

  const renderResourceActions = (resource: DeploymentProviderResourceHandleSummary) => {
    const running = resourceActionMutation.isPending || statusMutation.isPending || logsMutation.isPending
    return (
      <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap" useFlexGap>
        <Tooltip title="Refresh provider-observed status for this resource">
          <span>
            <Button
              size="small"
              variant="outlined"
              startIcon={<RefreshRoundedIcon />}
              disabled={running || !canOperateProviderResources}
              onClick={() => statusMutation.mutate(resource.id)}
            >
              Status
            </Button>
          </span>
        </Tooltip>
        <Tooltip title="Fetch recent provider logs">
          <span>
            <Button
              size="small"
              variant="outlined"
              startIcon={<TerminalRoundedIcon />}
              disabled={running || !canOperateProviderResources}
              onClick={() => logsMutation.mutate(resource.id)}
            >
              Logs
            </Button>
          </span>
        </Tooltip>
        <Button
          size="small"
          variant="outlined"
          startIcon={<PlayArrowRoundedIcon />}
          disabled={running || !canOperateProviderResources}
          onClick={() => resourceActionMutation.mutate({ handleId: resource.id, action: 'start' })}
        >
          Start
        </Button>
        <Button
          size="small"
          variant="outlined"
          startIcon={<StopRoundedIcon />}
          disabled={running || !canOperateProviderResources}
          onClick={() => resourceActionMutation.mutate({ handleId: resource.id, action: 'stop' })}
        >
          Stop
        </Button>
        <Button
          size="small"
          variant="outlined"
          startIcon={<RestartAltRoundedIcon />}
          disabled={running || !canOperateProviderResources}
          onClick={() => resourceActionMutation.mutate({ handleId: resource.id, action: 'restart' })}
        >
          Restart
        </Button>
        {isPlatformAdmin ? (
          <Button
            size="small"
            variant="outlined"
            color="error"
            startIcon={<DeleteOutlineRoundedIcon />}
            disabled={running}
            onClick={() => {
              if (window.confirm(`Delete provider resource ${resource.resourceKind} (${resource.id})?`)) {
                resourceActionMutation.mutate({ handleId: resource.id, action: 'delete' })
              }
            }}
          >
            Delete
          </Button>
        ) : null}
      </Stack>
    )
  }

  const actionResult = resourceActionMutation.data as DeploymentProviderResourceActionSummary | undefined
  const logsResult = logsMutation.data as DeploymentProviderResourceLogsSummary | undefined

  return (
    <Stack spacing={2.5}>
      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment target profiles</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Apply defaults and provider preflight are controlled here. Runtime apply uses the active runtime default unless a version apply explicitly chooses a profile.
              </Typography>
            </Box>

            {runtimeDefaultProfile ? (
              <Alert severity={runtimeDefaultProfile.providerType === 'COOLIFY' ? 'success' : 'info'}>
                Runtime default: <strong>{runtimeDefaultProfile.name}</strong> ({runtimeDefaultProfile.id}) · {profileSummary(runtimeDefaultProfile)}
              </Alert>
            ) : (
              <Alert severity="warning">No active runtime default target profile is configured.</Alert>
            )}

            {targetProfilesQuery.isLoading ? (
              <Typography color="text.secondary">Loading target profiles...</Typography>
            ) : targetProfilesQuery.isError ? (
              <Alert severity="error">
                {targetProfilesQuery.error instanceof Error
                  ? targetProfilesQuery.error.message
                  : 'Failed to load deployment target profiles'}
              </Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Profile</TableCell>
                    <TableCell>Provider</TableCell>
                    <TableCell>State</TableCell>
                    <TableCell>Defaults</TableCell>
                    <TableCell>Updated</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(targetProfilesQuery.data ?? []).map((profile) => (
                    <TableRow key={profile.id} hover>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {profile.name}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {profile.id} · {profile.environmentName} · {profile.sourceStrategy}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={profile.providerType} color={statusChipColor(profile.providerType)} variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={profile.active ? 'Active' : 'Inactive'}
                          color={profile.active ? 'success' : 'default'}
                          variant={profile.active ? 'filled' : 'outlined'}
                        />
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {profile.defaultForRuntime ? <Chip size="small" label="Runtime" color="success" /> : null}
                          {profile.defaultForRestartableServices ? <Chip size="small" label="Services" color="success" /> : null}
                          {!profile.defaultForRuntime && !profile.defaultForRestartableServices ? (
                            <Chip size="small" label="Explicit only" variant="outlined" />
                          ) : null}
                        </Stack>
                      </TableCell>
                      <TableCell>{formatTimestamp(profile.updatedAt)}</TableCell>
                      <TableCell align="right">{renderProfileActions(profile)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}

            {patchProfileMutation.isError ? (
              <Alert severity="error">
                {patchProfileMutation.error instanceof Error
                  ? patchProfileMutation.error.message
                  : 'Failed to update target profile'}
              </Alert>
            ) : null}
            {patchProfileMutation.isSuccess ? (
              <Alert severity="success">Target profile updated.</Alert>
            ) : null}
            {preflightMutation.isError ? (
              <Alert severity="error">
                {preflightMutation.error instanceof Error
                  ? preflightMutation.error.message
                  : 'Failed to run target profile preflight'}
              </Alert>
            ) : null}
            {lastPreflight ? (
              <Alert severity={lastPreflight.status === 'READY' ? 'success' : 'warning'}>
                <Stack spacing={1}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={lastPreflight.status} color={statusChipColor(lastPreflight.status)} />
                    <Chip size="small" label={lastPreflight.providerType} variant="outlined" />
                    <Chip size="small" label={`Profile ${lastPreflight.targetProfileId}`} variant="outlined" />
                    {lastPreflight.version ? <Chip size="small" label={`Version ${lastPreflight.version}`} variant="outlined" /> : null}
                  </Stack>
                  <Typography variant="body2">{lastPreflight.message}</Typography>
                  {lastPreflight.checks.length > 0 ? (
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {lastPreflight.checks.map((check) => (
                        <Chip key={check} size="small" label={check} variant="outlined" />
                      ))}
                    </Stack>
                  ) : null}
                </Stack>
              </Alert>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Provider resources</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Runtime and connector handles created by the active provider for this deployment.
              </Typography>
            </Box>

            {resourcesQuery.isLoading ? (
              <Typography color="text.secondary">Loading provider resources...</Typography>
            ) : resourcesQuery.isError ? (
              <Alert severity="error">
                {resourcesQuery.error instanceof Error
                  ? resourcesQuery.error.message
                  : 'Failed to load provider resources'}
              </Alert>
            ) : (resourcesQuery.data ?? []).length === 0 ? (
              <Alert severity="info">No provider resources are recorded for the selected deployment.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Resource</TableCell>
                    <TableCell>Provider</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Endpoint</TableCell>
                    <TableCell>Observed</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(resourcesQuery.data ?? []).map((resource) => {
                    const url = resourceUrl(resource)
                    return (
                      <TableRow key={resource.id} hover>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {resource.resourceKind}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {resource.id} · {resource.targetProfileId}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={resource.providerType} color={statusChipColor(resource.providerType)} variant="outlined" />
                        </TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip size="small" label={resource.status} color={statusChipColor(resource.status)} />
                            {resource.lastObservedStatus ? (
                              <Chip size="small" label={resource.lastObservedStatus} variant="outlined" />
                            ) : null}
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 320 }}>
                          {url ? (
                            <Link href={url} target="_blank" rel="noreferrer" underline="hover">
                              {resource.fqdn}
                            </Link>
                          ) : (
                            <Typography variant="body2" color="text.secondary">-</Typography>
                          )}
                        </TableCell>
                        <TableCell>{formatTimestamp(resource.lastObservedAt)}</TableCell>
                        <TableCell align="right">{renderResourceActions(resource)}</TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            )}

            {statusMutation.isError ? (
              <Alert severity="error">
                {statusMutation.error instanceof Error
                  ? statusMutation.error.message
                  : 'Failed to refresh provider resource status'}
              </Alert>
            ) : null}
            {lastStatus ? (
              <Alert severity={lastStatus.status === 'ACTIVE' ? 'success' : 'info'}>
                {lastStatus.handleId}: {lastStatus.status} · observed {lastStatus.observedStatus ?? '-'} · {lastStatus.fqdn ?? 'no endpoint'}
              </Alert>
            ) : null}
            {resourceActionMutation.isError ? (
              <Alert severity="error">
                {resourceActionMutation.error instanceof Error
                  ? resourceActionMutation.error.message
                  : 'Provider resource action failed'}
              </Alert>
            ) : null}
            {actionResult ? (
              <Alert severity={actionResult.status === 'FAILED' ? 'error' : 'success'}>
                {actionResult.action} {actionResult.handleId}: {actionResult.message}
              </Alert>
            ) : null}
            {logsMutation.isError ? (
              <Alert severity="error">
                {logsMutation.error instanceof Error
                  ? logsMutation.error.message
                  : 'Failed to fetch provider resource logs'}
              </Alert>
            ) : null}
            {!canOperateProviderResources ? (
              <Alert severity="info">Provider resource actions require platform operator access.</Alert>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Dialog open={logsDialogOpen} onClose={() => setLogsDialogOpen(false)} fullWidth maxWidth="lg">
        <DialogTitle>Provider Resource Logs</DialogTitle>
        <DialogContent dividers>
          {logsResult ? (
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={logsResult.handleId} variant="outlined" />
                <Chip label={logsResult.providerType} variant="outlined" />
                <Chip label={`${logsResult.lines} lines`} variant="outlined" />
                <Chip label={`Fetched ${formatTimestamp(logsResult.fetchedAt)}`} variant="outlined" />
              </Stack>
              <Divider />
              <Box
                component="pre"
                sx={{
                  m: 0,
                  p: 2,
                  borderRadius: 1,
                  bgcolor: 'grey.950',
                  color: 'grey.100',
                  overflow: 'auto',
                  maxHeight: '62vh',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontSize: 13,
                  lineHeight: 1.5,
                }}
              >
                {logsResult.logs || 'No logs returned.'}
              </Box>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLogsDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
