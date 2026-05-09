import BugReportRoundedIcon from '@mui/icons-material/BugReportRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
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
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { HostedVerificationRunHistory } from '../components/HostedVerificationRunHistory'
import {
  fetchPlatformDiagnostics,
  fetchPlatformDiagnosticsLogs,
  type RailwayLogEntrySummary,
} from '../api/platformApi'

function formatOptional(value: string | null | undefined): string {
  return value && value.trim().length > 0 ? value : '—'
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'PASSED' || status === 'READY' || status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'RUNNING' || status === 'QUEUED' || status === 'DEPLOYING' || status === 'INITIALIZING') {
    return 'info'
  }
  if (status === 'WARNING' || status === 'AWAITING_CONFIRMATION') {
    return 'warning'
  }
  if (status === 'FAILED' || status === 'ERROR' || status === 'REMOVED') {
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

export function PlatformDiagnosticsPage() {
  const auth = usePlatformAuth()
  const [source, setSource] = useState('deployment')
  const [filter, setFilter] = useState('')

  const diagnosticsQuery = useQuery({
    queryKey: ['platform-diagnostics'],
    queryFn: fetchPlatformDiagnostics,
    enabled: auth.session?.enabled ? auth.session.canManageUsers : true,
  })

  const logsQuery = useQuery({
    queryKey: ['platform-diagnostics-logs', source, filter],
    queryFn: () => fetchPlatformDiagnosticsLogs({
      source,
      filter: filter.trim() || undefined,
      limit: 150,
    }),
    enabled: auth.session?.enabled ? auth.session.canManageUsers : true,
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
