import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
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
import {
  fetchDeploymentReleases,
  fetchDeployments,
  fetchDeploymentVerificationRuns,
  rerunDeploymentVerification,
} from '../api/platformApi'

type VerificationCheck = {
  name: string
  status: string
  message: string
  details?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function summarizeProvisioningDetails(value: unknown) {
  if (!isRecord(value)) {
    return {
      provider: 'Unknown',
      projectName: 'Unknown',
      artifactStrategy: 'Unknown',
      generatedAt: 'Unknown',
    }
  }

  return {
    provider: typeof value.provider === 'string' ? value.provider : 'Unknown',
    projectName: typeof value.projectName === 'string' ? value.projectName : 'Unknown',
    artifactStrategy: typeof value.artifactStrategy === 'string' ? value.artifactStrategy : 'Unknown',
    generatedAt: typeof value.generatedAt === 'string' ? value.generatedAt : 'Unknown',
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
  if (status === 'SKIPPED') {
    return 'default'
  }
  return 'error'
}

export function DiagnosticsPage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [selectedRunId, setSelectedRunId] = useState('')

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })

  const deployments = deploymentsQuery.data ?? []

  useEffect(() => {
    if (deployments.length === 0) {
      if (selectedDeploymentId !== '') {
        setSelectedDeploymentId('')
      }
      return
    }

    if (!deployments.some((deployment) => deployment.id === selectedDeploymentId)) {
      setSelectedDeploymentId(deployments[0].id)
    }
  }, [deployments, selectedDeploymentId])

  const selectedDeployment = useMemo(
    () => deployments.find((deployment) => deployment.id === selectedDeploymentId) ?? null,
    [deployments, selectedDeploymentId],
  )

  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const verificationRunsQuery = useQuery({
    queryKey: ['deployment-verification-runs', selectedDeploymentId],
    queryFn: () => fetchDeploymentVerificationRuns(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
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

  const releaseHistory = releasesQuery.data ?? []
  const verificationRuns = verificationRunsQuery.data ?? []
  const latestRelease = releaseHistory[0] ?? null
  const provisioningSummary = summarizeProvisioningDetails(latestRelease?.provisioningDetails)

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
                        <Chip
                          label={`${latestRelease.provisioningStatus} · ${provisioningSummary.provider}`}
                          color={latestRelease.provisioningStatus === 'PLANNED' ? 'primary' : 'default'}
                          sx={{ alignSelf: 'flex-start' }}
                        />
                        <Typography variant="body2">Project: {provisioningSummary.projectName}</Typography>
                        <Typography variant="body2">Artifact strategy: {provisioningSummary.artifactStrategy}</Typography>
                        <Typography variant="body2">
                          Generated: {provisioningSummary.generatedAt === 'Unknown'
                            ? 'Unknown'
                            : formatTimestamp(provisioningSummary.generatedAt)}
                        </Typography>
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
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Release</TableCell>
                        <TableCell>Version</TableCell>
                        <TableCell>Provisioning</TableCell>
                        <TableCell>Verification</TableCell>
                        <TableCell>Applied</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {releaseHistory.map((release) => (
                        <TableRow key={release.id} hover>
                          <TableCell>{release.id}</TableCell>
                          <TableCell>{release.deploymentVersionId}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">{release.provisioningStatus}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {release.provisioningTarget}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">{release.verificationStatus}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {release.verificationRunId ?? 'No verification run'}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{formatTimestamp(release.appliedAt)}</TableCell>
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
        </>
      ) : null}
    </Stack>
  )
}
