import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
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
  fetchDeploymentReleases,
  fetchDeployments,
  fetchDeploymentVerificationRuns,
  fetchPlatformAuditEvents,
  fetchRailwayPreflight,
  rerunDeploymentVerification,
  type PlatformAuditEventSummary,
  type DeploymentReleaseSummary,
} from '../api/platformApi'

type VerificationCheck = {
  name: string
  status: string
  message: string
  details?: unknown
}

type ProvisioningProgressStep = {
  key: string
  description: string
  status: string
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function formatOptionalTimestamp(value: string | null | undefined): string {
  return value ? formatTimestamp(value) : '—'
}

function summarizeProvisioningDetails(value: unknown) {
  if (!isRecord(value)) {
    return {
      provider: 'Unknown',
      projectName: 'Unknown',
      repository: 'Unknown',
      branch: 'Unknown',
      artifactStrategy: 'Unknown',
      generatedAt: 'Unknown',
      runtimeRootDir: 'Unknown',
      runtimeDockerfilePath: 'Unknown',
      connectorRootDir: 'Unknown',
      connectorDockerfilePath: 'Unknown',
      artifactUrls: {
        actions: 'Unknown',
        entities: 'Unknown',
        routing: 'Unknown',
        manifest: 'Unknown',
      },
      progress: {
        currentStepKey: 'Unknown',
        currentStepDescription: 'No progress recorded',
        currentStepStatus: 'UNKNOWN',
        errorMessage: null,
        steps: [] as ProvisioningProgressStep[],
      },
    }
  }

  const services = isRecord(value.services) ? value.services : {}
  const runtime = isRecord(services.runtime) ? services.runtime : {}
  const restConnector = isRecord(services.restConnector) ? services.restConnector : {}
  const artifactUrls = isRecord(value.artifactUrls) ? value.artifactUrls : {}
  const progressNode = isRecord(value.progress) ? value.progress : {}
  const stepNodes = Array.isArray(progressNode.steps) ? progressNode.steps : []

  return {
    provider: typeof value.provider === 'string' ? value.provider : 'Unknown',
    projectName: typeof value.projectName === 'string' ? value.projectName : 'Unknown',
    repository: typeof value.repository === 'string' ? value.repository : 'Unknown',
    branch: typeof value.branch === 'string' ? value.branch : 'Unknown',
    artifactStrategy: typeof value.artifactStrategy === 'string' ? value.artifactStrategy : 'Unknown',
    generatedAt: typeof value.generatedAt === 'string' ? value.generatedAt : 'Unknown',
    runtimeRootDir: typeof runtime.rootDir === 'string' ? runtime.rootDir : 'Unknown',
    runtimeDockerfilePath: typeof runtime.dockerfilePath === 'string' ? runtime.dockerfilePath : 'Unknown',
    connectorRootDir: typeof restConnector.rootDir === 'string' ? restConnector.rootDir : 'Unknown',
    connectorDockerfilePath:
      typeof restConnector.dockerfilePath === 'string' ? restConnector.dockerfilePath : 'Unknown',
    artifactUrls: {
      actions: typeof artifactUrls.actions === 'string' ? artifactUrls.actions : 'Unknown',
      entities: typeof artifactUrls.entities === 'string' ? artifactUrls.entities : 'Unknown',
      routing: typeof artifactUrls.routing === 'string' ? artifactUrls.routing : 'Unknown',
      manifest: typeof artifactUrls.manifest === 'string' ? artifactUrls.manifest : 'Unknown',
    },
    progress: {
      currentStepKey:
        typeof progressNode.currentStepKey === 'string' ? progressNode.currentStepKey : 'Unknown',
      currentStepDescription:
        typeof progressNode.currentStepDescription === 'string'
          ? progressNode.currentStepDescription
          : 'No progress recorded',
      currentStepStatus:
        typeof progressNode.currentStepStatus === 'string' ? progressNode.currentStepStatus : 'UNKNOWN',
      errorMessage:
        typeof progressNode.errorMessage === 'string' ? progressNode.errorMessage : null,
      steps: stepNodes.flatMap((step): ProvisioningProgressStep[] => {
        if (!isRecord(step)) {
          return []
        }
        return [{
          key: typeof step.key === 'string' ? step.key : 'unknown_step',
          description: typeof step.description === 'string' ? step.description : 'No description',
          status: typeof step.status === 'string' ? step.status : 'UNKNOWN',
          startedAt: typeof step.startedAt === 'string' ? step.startedAt : null,
          completedAt: typeof step.completedAt === 'string' ? step.completedAt : null,
          errorMessage: typeof step.errorMessage === 'string' ? step.errorMessage : null,
        }]
      }),
    },
  }
}

function readChecks(value: unknown): VerificationCheck[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.flatMap((item) => {
    if (!isRecord(item)) {
      return []
    }

    const name = typeof item.name === 'string' ? item.name : 'unknown_check'
    const status = typeof item.status === 'string' ? item.status : 'UNKNOWN'
    const message = typeof item.message === 'string' ? item.message : 'No message'
    return [{ name, status, message, details: item.details }]
  })
}

function formatDetails(value: unknown): string {
  if (value == null) {
    return '—'
  }

  if (typeof value === 'string') {
    return value
  }

  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  if (status === 'PASSED') {
    return 'success'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'SKIPPED') {
    return 'default'
  }
  return 'error'
}

function releaseStatusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'APPLIED_VERIFIED') {
    return 'success'
  }
  if (status === 'APPLY_REQUESTED' || status === 'PROVISIONING' || status === 'VERIFYING') {
    return 'info'
  }
  if (status === 'APPLIED_VERIFICATION_FAILED') {
    return 'warning'
  }
  if (status === 'FAILED') {
    return 'error'
  }
  return 'default'
}

function releaseSignalColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'PASSED' || status === 'SUCCEEDED' || status === 'PLANNED' || status === 'COMPLETED') {
    return 'success'
  }
  if (status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING') {
    return 'info'
  }
  if (status === 'SKIPPED') {
    return 'default'
  }
  if (status === 'FAILED') {
    return 'error'
  }
  return 'warning'
}

function isReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return ['APPLY_REQUESTED', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
}

function summarizeAuditDetails(value: unknown): string {
  if (value == null) {
    return '—'
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

export function DiagnosticsPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [selectedRunId, setSelectedRunId] = useState('')

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })

  const railwayPreflightQuery = useQuery({
    queryKey: ['railway-preflight'],
    queryFn: fetchRailwayPreflight,
  })

  const auditEventsQuery = useQuery({
    queryKey: ['platform-audit-events'],
    queryFn: fetchPlatformAuditEvents,
  })

  const deployments = deploymentsQuery.data ?? []
  const requestedDeploymentId = searchParams.get('deploymentId') ?? ''

  useEffect(() => {
    if (deployments.length === 0) {
      if (selectedDeploymentId !== '') {
        setSelectedDeploymentId('')
      }
      return
    }

    const preferredDeploymentId =
      deployments.find((deployment) => deployment.status !== 'DRAFT')?.id ?? deployments[0].id

    if (
      requestedDeploymentId.length > 0
      && deployments.some((deployment) => deployment.id === requestedDeploymentId)
      && selectedDeploymentId !== requestedDeploymentId
    ) {
      setSelectedDeploymentId(requestedDeploymentId)
      return
    }

    if (!deployments.some((deployment) => deployment.id === selectedDeploymentId)) {
      setSelectedDeploymentId(preferredDeploymentId)
    }
  }, [deployments, requestedDeploymentId, selectedDeploymentId])

  useEffect(() => {
    const current = searchParams.get('deploymentId') ?? ''
    if (selectedDeploymentId.length === 0 || current === selectedDeploymentId) {
      return
    }
    const next = new URLSearchParams(searchParams)
    next.set('deploymentId', selectedDeploymentId)
    setSearchParams(next, { replace: true })
  }, [searchParams, selectedDeploymentId, setSearchParams])

  const selectedDeployment = useMemo(
    () => deployments.find((deployment) => deployment.id === selectedDeploymentId) ?? null,
    [deployments, selectedDeploymentId],
  )

  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: (query) => {
      const releases = (query.state.data as DeploymentReleaseSummary[] | undefined) ?? []
      return releases.some(isReleaseInProgress) ? 3000 : false
    },
  })

  const releaseHistory = releasesQuery.data ?? []
  const latestRelease = releaseHistory[0] ?? null
  const releasesInProgress = releaseHistory.some(isReleaseInProgress)

  const verificationRunsQuery = useQuery({
    queryKey: ['deployment-verification-runs', selectedDeploymentId],
    queryFn: () => fetchDeploymentVerificationRuns(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: releasesInProgress ? 3000 : false,
  })

  const rerunMutation = useMutation({
    mutationFn: (deploymentId: string) => rerunDeploymentVerification(deploymentId),
    onSuccess: async (run) => {
      setSelectedRunId(run.id)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-verification-runs', selectedDeploymentId] }),
      ])
    },
  })

  const verificationRuns = verificationRunsQuery.data ?? []
  const provisioningSummary = summarizeProvisioningDetails(latestRelease?.provisioningDetails)

  useEffect(() => {
    if (!releasesInProgress) {
      return undefined
    }

    const intervalId = window.setInterval(() => {
      void queryClient.invalidateQueries({ queryKey: ['deployments'] })
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [queryClient, releasesInProgress])

  useEffect(() => {
    if (verificationRuns.length === 0) {
      if (selectedRunId !== '') {
        setSelectedRunId('')
      }
      return
    }

    if (!verificationRuns.some((run) => run.id === selectedRunId)) {
      setSelectedRunId(verificationRuns[0].id)
    }
  }, [selectedRunId, verificationRuns])

  const selectedRun = useMemo(
    () => verificationRuns.find((run) => run.id === selectedRunId) ?? verificationRuns[0] ?? null,
    [selectedRunId, verificationRuns],
  )
  const selectedRunChecks = readChecks(selectedRun?.checks)
  const auditEvents = (auditEventsQuery.data ?? []).filter((event: PlatformAuditEventSummary) =>
    !selectedDeploymentId
      || (typeof event.details === 'object'
        && event.details !== null
        && 'deploymentId' in (event.details as Record<string, unknown>)
        && (event.details as Record<string, unknown>).deploymentId === selectedDeploymentId)
      || event.targetId === selectedDeploymentId,
  )

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Diagnostics" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Release evidence and live verification
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          This screen combines deployment endpoints, provisioning evidence, stored verification runs,
          and manual reruns so the platform can act as an operator console instead of only a config editor.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="h6">Railway preflight</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  Read-only provisioning readiness check for Railway API mode, workspace access, public artifact delivery,
                  and required platform secrets.
                </Typography>
              </Box>
              <Button
                variant="outlined"
                startIcon={<SyncRoundedIcon />}
                onClick={() => queryClient.invalidateQueries({ queryKey: ['railway-preflight'] })}
                disabled={railwayPreflightQuery.isFetching}
              >
                {railwayPreflightQuery.isFetching ? 'Refreshing...' : 'Refresh preflight'}
              </Button>
            </Stack>

            {railwayPreflightQuery.isLoading ? (
              <Typography color="text.secondary">Running Railway preflight...</Typography>
            ) : railwayPreflightQuery.isError ? (
              <Alert severity="error">
                {railwayPreflightQuery.error instanceof Error
                  ? railwayPreflightQuery.error.message
                  : 'Failed to run Railway preflight'}
              </Alert>
            ) : railwayPreflightQuery.data ? (
              <>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip
                    label={railwayPreflightQuery.data.ready ? 'Ready to provision' : 'Not ready to provision'}
                    color={railwayPreflightQuery.data.ready ? 'success' : 'warning'}
                  />
                  <Chip label={`Mode: ${railwayPreflightQuery.data.mode}`} variant="outlined" />
                  <Chip
                    label={`Workspace: ${railwayPreflightQuery.data.workspaceName ?? railwayPreflightQuery.data.workspaceId ?? 'Unresolved'}`}
                    variant="outlined"
                  />
                  <Chip
                    label={`Checked: ${formatTimestamp(railwayPreflightQuery.data.checkedAt)}`}
                    variant="outlined"
                  />
                </Stack>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <Stack spacing={1}>
                      <Typography variant="body2">
                        Repository: <strong>{railwayPreflightQuery.data.repository}</strong>
                      </Typography>
                      <Typography variant="body2">
                        Branch: <strong>{railwayPreflightQuery.data.branch}</strong>
                      </Typography>
                      <Typography variant="body2">
                        Public base URL: <strong>{railwayPreflightQuery.data.publicBaseUrl}</strong>
                      </Typography>
                    </Stack>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Check</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell>Message</TableCell>
                          <TableCell>Details</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {railwayPreflightQuery.data.checks.map((check) => (
                          <TableRow key={check.key} hover>
                            <TableCell sx={{ fontFamily: 'monospace' }}>{check.key}</TableCell>
                            <TableCell>
                              <Chip size="small" color={statusColor(check.status)} label={check.status} />
                            </TableCell>
                            <TableCell>{check.message}</TableCell>
                            <TableCell sx={{ fontFamily: 'monospace', maxWidth: 320 }}>
                              {check.details ?? '—'}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Grid>
                </Grid>
              </>
            ) : null}

            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Diagnostics are grouped by deployment because each customer environment remains isolated.
              </Typography>
            </Box>

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField
                select
                fullWidth
                label="Deployment"
                value={selectedDeploymentId}
                onChange={(event) => setSelectedDeploymentId(event.target.value)}
                disabled={deployments.length === 0}
              >
                {deployments.map((deployment) => (
                  <MenuItem key={deployment.id} value={deployment.id}>
                    {deployment.name} ({deployment.environment})
                  </MenuItem>
                ))}
              </TextField>

              <Button
                variant="contained"
                startIcon={<PlayCircleOutlineRoundedIcon />}
                disabled={!selectedDeploymentId || rerunMutation.isPending}
                onClick={() => rerunMutation.mutate(selectedDeploymentId)}
              >
                {rerunMutation.isPending ? 'Running verification...' : 'Rerun verification'}
              </Button>
            </Stack>

            {rerunMutation.isError ? (
              <Alert severity="error">
                {rerunMutation.error instanceof Error
                  ? rerunMutation.error.message
                  : 'Verification rerun failed'}
              </Alert>
            ) : null}

            {rerunMutation.isSuccess ? (
              <Alert severity={rerunMutation.data.status === 'PASSED' ? 'success' : 'warning'}>
                Latest rerun: {rerunMutation.data.summaryMessage}
              </Alert>
            ) : null}

            {selectedDeployment ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={selectedDeployment.status} color="primary" />
                <Chip label={`Active: ${selectedDeployment.activeVersion}`} variant="outlined" />
                <Chip label={selectedDeployment.templateId} variant="outlined" />
                {releasesInProgress ? (
                  <Chip
                    label={`Release running: ${latestRelease?.currentStepDescription ?? latestRelease?.status ?? 'In progress'}`}
                    color="info"
                  />
                ) : null}
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to inspect diagnostics.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeployment ? (
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Current endpoints</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Runtime and connector base URLs currently attached to the deployment record.
                    </Typography>
                    <Typography variant="body2">
                      Runtime: <strong>{selectedDeployment.runtimeBaseUrl ?? 'Not assigned'}</strong>
                    </Typography>
                    <Typography variant="body2">
                      Connector: <strong>{selectedDeployment.connectorBaseUrl ?? 'Not assigned'}</strong>
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Latest provisioning evidence</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Latest stored provisioning result for the deployment’s most recent release.
                    </Typography>
                    {latestRelease ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip
                            label={latestRelease.status}
                            color={releaseStatusColor(latestRelease.status)}
                            sx={{ alignSelf: 'flex-start' }}
                          />
                          <Chip
                            label={`${latestRelease.provisioningStatus} · ${provisioningSummary.provider}`}
                            color={releaseSignalColor(latestRelease.provisioningStatus)}
                            variant="outlined"
                          />
                          <Chip
                            label={provisioningSummary.progress.currentStepStatus}
                            color={releaseSignalColor(provisioningSummary.progress.currentStepStatus)}
                            variant="outlined"
                          />
                        </Stack>
                        <Typography variant="body2">
                          Current step: {provisioningSummary.progress.currentStepDescription}
                        </Typography>
                        <Typography variant="body2">
                          Updated: {formatOptionalTimestamp(latestRelease.updatedAt)}
                        </Typography>
                        <Typography variant="body2">Project: {provisioningSummary.projectName}</Typography>
                        <Typography variant="body2">Repository: {provisioningSummary.repository}</Typography>
                        <Typography variant="body2">Branch: {provisioningSummary.branch}</Typography>
                        <Typography variant="body2">Artifact strategy: {provisioningSummary.artifactStrategy}</Typography>
                        <Typography variant="body2">Runtime root: {provisioningSummary.runtimeRootDir}</Typography>
                        <Typography variant="body2">
                          Runtime Dockerfile: {provisioningSummary.runtimeDockerfilePath}
                        </Typography>
                        <Typography variant="body2">Connector root: {provisioningSummary.connectorRootDir}</Typography>
                        <Typography variant="body2">
                          Connector Dockerfile: {provisioningSummary.connectorDockerfilePath}
                        </Typography>
                        <Typography variant="body2">
                          Generated: {provisioningSummary.generatedAt === 'Unknown'
                            ? 'Unknown'
                            : formatTimestamp(provisioningSummary.generatedAt)}
                        </Typography>
                        {latestRelease.errorMessage || provisioningSummary.progress.errorMessage ? (
                          <Alert severity="error">
                            {latestRelease.errorMessage ?? provisioningSummary.progress.errorMessage}
                          </Alert>
                        ) : null}
                        <Stack spacing={0.5}>
                          <Typography variant="caption" color="text.secondary">
                            Artifact URLs
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            actions: {provisioningSummary.artifactUrls.actions}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            entities: {provisioningSummary.artifactUrls.entities}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            routing: {provisioningSummary.artifactUrls.routing}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            manifest: {provisioningSummary.artifactUrls.manifest}
                          </Typography>
                        </Stack>
                      </>
                    ) : (
                      <Alert severity="info">No release evidence exists yet for this deployment.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Latest verification result</Typography>
                    <Typography variant="body2" color="text.secondary">
                      The latest stored verification run for the selected deployment.
                    </Typography>
                    {selectedRun ? (
                      <>
                        <Chip
                          icon={selectedRun.status === 'PASSED'
                            ? <CheckCircleRoundedIcon />
                            : <WarningAmberRoundedIcon />}
                          color={selectedRun.status === 'PASSED' ? 'success' : 'warning'}
                          label={selectedRun.status}
                          sx={{ alignSelf: 'flex-start' }}
                        />
                        <Typography variant="body2">{selectedRun.summaryMessage}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          Completed {formatTimestamp(selectedRun.completedAt)}
                        </Typography>
                      </>
                    ) : (
                      <Alert severity="info">No verification runs exist yet for this deployment.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Release evidence</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Applied releases with provisioning and verification linkage.
                  </Typography>
                </Box>
                {releasesQuery.isLoading ? (
                  <Typography color="text.secondary">Loading releases...</Typography>
                ) : releasesQuery.isError ? (
                  <Alert severity="error">
                    {releasesQuery.error instanceof Error
                      ? releasesQuery.error.message
                      : 'Failed to load releases'}
                  </Alert>
                ) : releaseHistory.length === 0 ? (
                  <Alert severity="info">No release evidence exists yet for this deployment.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Release</TableCell>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Current step</TableCell>
                        <TableCell>Verification</TableCell>
                        <TableCell>Updated</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {releaseHistory.map((release) => (
                        <TableRow key={release.id} hover>
                          <TableCell>{release.id}</TableCell>
                          <TableCell>{release.deploymentVersionId}</TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip
                                size="small"
                                label={release.status}
                                color={releaseStatusColor(release.status)}
                              />
                              <Chip
                                size="small"
                                label={release.provisioningStatus}
                                color={releaseSignalColor(release.provisioningStatus)}
                                variant="outlined"
                              />
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">
                                {release.currentStepDescription ?? 'Not recorded yet'}
                              </Typography>
                              {release.errorMessage ? (
                                <Typography variant="caption" color="error.main">
                                  {release.errorMessage}
                                </Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Chip
                                size="small"
                                label={release.verificationStatus}
                                color={releaseSignalColor(release.verificationStatus)}
                                variant="outlined"
                              />
                              <Typography variant="caption" color="text.secondary">
                                {release.verificationRunId ?? 'No verification run'}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{formatOptionalTimestamp(release.updatedAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Provisioning step history</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Per-step progress evidence stored on the latest release while Railway apply runs.
                  </Typography>
                </Box>
                {latestRelease ? (
                  provisioningSummary.progress.steps.length === 0 ? (
                    <Alert severity="info">The latest release does not contain detailed step history yet.</Alert>
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Step</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell>Started</TableCell>
                          <TableCell>Completed</TableCell>
                          <TableCell>Error</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {provisioningSummary.progress.steps.map((step) => (
                          <TableRow key={`${latestRelease.id}:${step.key}`} hover>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {step.description}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                  {step.key}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={step.status}
                                color={releaseSignalColor(step.status)}
                              />
                            </TableCell>
                            <TableCell>{formatOptionalTimestamp(step.startedAt)}</TableCell>
                            <TableCell>{formatOptionalTimestamp(step.completedAt)}</TableCell>
                            <TableCell sx={{ maxWidth: 320 }}>
                              {step.errorMessage ?? '—'}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )
                ) : (
                  <Alert severity="info">Apply a version first to inspect provisioning step history.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Verification runs</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Stored verification evidence created during apply and manual reruns.
                  </Typography>
                </Box>
                {verificationRunsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading verification runs...</Typography>
                ) : verificationRunsQuery.isError ? (
                  <Alert severity="error">
                    {verificationRunsQuery.error instanceof Error
                      ? verificationRunsQuery.error.message
                      : 'Failed to load verification runs'}
                  </Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Run</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Summary</TableCell>
                        <TableCell>Completed</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {verificationRuns.map((run) => (
                        <TableRow
                          key={run.id}
                          hover
                          selected={run.id === selectedRun?.id}
                          onClick={() => setSelectedRunId(run.id)}
                          sx={{ cursor: 'pointer' }}
                        >
                          <TableCell>{run.id}</TableCell>
                          <TableCell>{run.verificationType}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={run.status === 'PASSED' ? 'success' : 'warning'}
                              icon={run.status === 'PASSED' ? <CheckCircleRoundedIcon /> : <WarningAmberRoundedIcon />}
                              label={run.status}
                            />
                          </TableCell>
                          <TableCell>{run.summaryMessage}</TableCell>
                          <TableCell>{formatTimestamp(run.completedAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Selected run checks</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Detailed check evidence for the currently selected verification run.
                  </Typography>
                </Box>

                {selectedRun ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={selectedRun.id} variant="outlined" />
                      <Chip label={selectedRun.verificationType} variant="outlined" />
                      <Chip label={selectedRun.status} color={selectedRun.status === 'PASSED' ? 'success' : 'warning'} />
                    </Stack>

                    {selectedRunChecks.length === 0 ? (
                      <Alert severity="info">This verification run does not contain detailed checks.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Check</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Message</TableCell>
                            <TableCell>Details</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {selectedRunChecks.map((check) => (
                            <TableRow key={`${selectedRun.id}:${check.name}`} hover>
                              <TableCell sx={{ fontFamily: 'monospace' }}>{check.name}</TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  color={statusColor(check.status)}
                                  label={check.status}
                                />
                              </TableCell>
                              <TableCell>{check.message}</TableCell>
                              <TableCell sx={{ fontFamily: 'monospace', maxWidth: 360 }}>
                                {formatDetails(check.details)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </>
                ) : (
                  <Alert severity="info">Select or run verification to inspect detailed checks.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Audit events</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Recent privileged platform actions recorded with actor, role, target, and action details.
                  </Typography>
                </Box>
                {auditEventsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading audit events...</Typography>
                ) : auditEventsQuery.isError ? (
                  <Alert severity="error">
                    {auditEventsQuery.error instanceof Error
                      ? auditEventsQuery.error.message
                      : 'Failed to load audit events'}
                  </Alert>
                ) : auditEvents.length === 0 ? (
                  <Alert severity="info">No audit events recorded yet for this deployment.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Time</TableCell>
                        <TableCell>Actor</TableCell>
                        <TableCell>Role</TableCell>
                        <TableCell>Action</TableCell>
                        <TableCell>Target</TableCell>
                        <TableCell>Details</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {auditEvents.map((event) => (
                        <TableRow key={event.id} hover>
                          <TableCell>{formatTimestamp(event.createdAt)}</TableCell>
                          <TableCell>{event.actorId}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={event.actorRole}
                              color={event.actorRole === 'PLATFORM_ADMIN' ? 'secondary' : 'primary'}
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>{event.action}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">{event.targetType}</Typography>
                              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                {event.targetId}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 360, fontFamily: 'monospace' }}>
                            {summarizeAuditDetails(event.details)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  )
}
