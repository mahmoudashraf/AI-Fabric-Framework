import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'
import {
  fetchDeploymentDraft,
  fetchDeploymentReleases,
  fetchDeploymentVerificationRuns,
  fetchRailwayPreflight,
  validateDeploymentDraft,
  type DeploymentReleaseSummary,
  type RailwayPreflightCheckSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

type VerificationCheck = {
  name: string
  status: string
  message: string
  details?: unknown
}

type ServiceHealthSummary = {
  key: string
  label: string
  passed: number
  warning: number
  failed: number
  skipped: number
}

type GateCheckSummary = {
  key: string
  label: string
  status: string
  message: string
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function formatOptionalTimestamp(value: string | null | undefined): string {
  return value ? formatTimestamp(value) : '—'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readChecks(value: unknown): VerificationCheck[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.flatMap((item) => {
    if (!isRecord(item)) {
      return []
    }
    return [{
      name: typeof item.name === 'string' ? item.name : 'unknown_check',
      status: typeof item.status === 'string' ? item.status : 'UNKNOWN',
      message: typeof item.message === 'string' ? item.message : 'No message',
      details: item.details,
    }]
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

function verificationStatusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'PASSED' || status === 'READY') {
    return 'success'
  }
  if (status === 'RUNNING') {
    return 'info'
  }
  if (status === 'WARNING' || status === 'REQUIRED') {
    return 'warning'
  }
  if (status === 'FAILED' || status === 'BLOCKED') {
    return 'error'
  }
  return 'default'
}

function releaseStatusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'APPLIED_VERIFIED') {
    return 'success'
  }
  if (status === 'APPLY_REQUESTED' || status === 'PRE_APPLY_VERIFYING' || status === 'PROVISIONING' || status === 'VERIFYING') {
    return 'info'
  }
  if (status === 'APPLIED_VERIFICATION_FAILED') {
    return 'warning'
  }
  if (status === 'PRE_APPLY_BLOCKED' || status === 'FAILED') {
    return 'error'
  }
  return 'default'
}

function isReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
}

function checkGroupKey(checkName: string): string {
  if (checkName.startsWith('runtime_')) {
    return 'runtime'
  }
  if (checkName.startsWith('connector_')) {
    return 'connector'
  }
  return 'release'
}

function buildServiceHealthSummaries(checks: VerificationCheck[]): ServiceHealthSummary[] {
  const groups: ServiceHealthSummary[] = [
    { key: 'release', label: 'Release control plane', passed: 0, warning: 0, failed: 0, skipped: 0 },
    { key: 'runtime', label: 'Runtime service', passed: 0, warning: 0, failed: 0, skipped: 0 },
    { key: 'connector', label: 'REST connector service', passed: 0, warning: 0, failed: 0, skipped: 0 },
  ]

  checks.forEach((check) => {
    const group = groups.find((candidate) => candidate.key === checkGroupKey(check.name))
    if (!group) {
      return
    }
    if (check.status === 'PASSED') {
      group.passed += 1
      return
    }
    if (check.status === 'WARNING') {
      group.warning += 1
      return
    }
    if (check.status === 'SKIPPED') {
      group.skipped += 1
      return
    }
    group.failed += 1
  })

  return groups
}

function buildGateSummary(args: {
  publishReady: boolean
  hasPublishedVersion: boolean
  preflightReady: boolean
  applyRunning: boolean
  approvalRequired: boolean
}): GateCheckSummary[] {
  const gateChecks: GateCheckSummary[] = [
    {
      key: 'draft_validation',
      label: 'Draft validation',
      status: args.publishReady ? 'PASSED' : 'BLOCKED',
      message: args.publishReady
        ? 'Draft validation passed for publish.'
        : 'Draft validation still has blocking errors.',
    },
    {
      key: 'published_version',
      label: 'Published version',
      status: args.hasPublishedVersion ? 'PASSED' : 'BLOCKED',
      message: args.hasPublishedVersion
        ? 'At least one immutable published version exists for apply.'
        : 'Publish a version before apply can start.',
    },
    {
      key: 'platform_preflight',
      label: 'Platform preflight',
      status: args.preflightReady ? 'PASSED' : 'BLOCKED',
      message: args.preflightReady
        ? 'Railway platform preflight is ready.'
        : 'Platform preflight is still blocked on provisioning prerequisites.',
    },
    {
      key: 'concurrent_release',
      label: 'Concurrent release',
      status: args.applyRunning ? 'WARNING' : 'PASSED',
      message: args.applyRunning
        ? 'Another apply or verification is still running.'
        : 'No release is currently running.',
    },
  ]

  gateChecks.push({
    key: 'approval_guardrail',
    label: 'Approval guardrail',
    status: args.approvalRequired ? 'REQUIRED' : 'PASSED',
    message: args.approvalRequired
      ? 'Apply is protected and needs an approved operation request.'
      : 'This deployment can apply without a separate approval.',
  })

  return gateChecks
}

function summarizeApplyGate(gates: GateCheckSummary[]): { status: string; message: string } {
  if (gates.some((gate) => gate.status === 'BLOCKED')) {
    return {
      status: 'BLOCKED',
      message: 'Release apply is blocked until draft validation and platform prerequisites pass.',
    }
  }
  if (gates.some((gate) => gate.status === 'WARNING')) {
    return {
      status: 'WARNING',
      message: 'Release apply is temporarily waiting because another rollout is still running.',
    }
  }
  if (gates.some((gate) => gate.status === 'REQUIRED')) {
    return {
      status: 'REQUIRED',
      message: 'Release apply is ready once an approved operation request exists.',
    }
  }
  return {
    status: 'READY',
    message: 'Release apply can proceed from a platform readiness perspective.',
  }
}

export function VerificationPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const selectedDeployment = useMemo(
    () => workspace?.deployment ?? selectedDeploymentSummary ?? null,
    [selectedDeploymentSummary, workspace],
  )

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const validationQuery = useQuery({
    queryKey: ['deployment-validation', draftQuery.data?.id],
    queryFn: () => validateDeploymentDraft(draftQuery.data!.id),
    enabled: !!draftQuery.data?.id,
  })

  const railwayPreflightQuery = useQuery({
    queryKey: ['railway-preflight'],
    queryFn: fetchRailwayPreflight,
  })

  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: (query) => {
      const releases = (query.state.data as DeploymentReleaseSummary[] | undefined) ?? []
      return releases.some(isReleaseInProgress) ? 3000 : false
    },
  })

  const verificationRunsQuery = useQuery({
    queryKey: ['deployment-verification-runs', selectedDeploymentId],
    queryFn: () => fetchDeploymentVerificationRuns(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: (query) => {
      const releases = (releasesQuery.data ?? []) as DeploymentReleaseSummary[]
      return releases.some(isReleaseInProgress) ? 3000 : false
    },
  })

  const latestRelease = (releasesQuery.data ?? [])[0] ?? null
  const latestVerification = useMemo(() => {
    const runs = verificationRunsQuery.data ?? []
    if (!latestRelease) {
      return runs[0] ?? null
    }
    return runs.find((run) => run.releaseId === latestRelease.id) ?? runs[0] ?? null
  }, [latestRelease, verificationRunsQuery.data])
  const verificationChecks = useMemo(
    () => readChecks(latestVerification?.checks),
    [latestVerification?.checks],
  )
  const failedOrWarningChecks = useMemo(
    () => verificationChecks.filter((check) => check.status !== 'PASSED' && check.status !== 'SKIPPED'),
    [verificationChecks],
  )
  const serviceHealth = useMemo(
    () => buildServiceHealthSummaries(verificationChecks),
    [verificationChecks],
  )
  const failedPreflightChecks = useMemo(
    () => (railwayPreflightQuery.data?.checks ?? []).filter((check) => check.status === 'FAILED'),
    [railwayPreflightQuery.data?.checks],
  )
  const applyGateChecks = useMemo(
    () => buildGateSummary({
      publishReady: validationQuery.data?.publishReady ?? false,
      hasPublishedVersion: workspace?.lifecycle.hasPublishedVersion ?? false,
      preflightReady: railwayPreflightQuery.data?.ready ?? false,
      applyRunning: latestRelease ? isReleaseInProgress(latestRelease) : false,
      approvalRequired: workspace?.deployment.approvalRequiredForApply ?? false,
    }),
    [
      latestRelease,
      railwayPreflightQuery.data?.ready,
      validationQuery.data?.publishReady,
      workspace?.deployment.approvalRequiredForApply,
      workspace?.lifecycle.hasPublishedVersion,
    ],
  )
  const applyGate = useMemo(() => summarizeApplyGate(applyGateChecks), [applyGateChecks])

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Verification" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Release gate and rollout verification
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          This workspace now joins pre-apply readiness with post-apply evidence so operators can decide
          whether a rollout is safe, understand what is blocking it, and review service-level verification
          after apply.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Verification is scoped to the deployment selected in the shared workspace header.
              </Typography>
            </Box>

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ flex: 1 }}>
                {workspace ? (
                  <>
                    <Chip label={workspace.deployment.name} variant="outlined" />
                    <Chip label={workspace.deployment.environment} variant="outlined" />
                    <Chip label={workspace.template.name} variant="outlined" />
                    <Chip label={`Draft state: ${workspace.lifecycle.savedDraftState}`} variant="outlined" />
                    <Chip label={`Live state: ${workspace.lifecycle.liveState}`} variant="outlined" />
                  </>
                ) : (
                  <Chip label="No deployment selected" variant="outlined" />
                )}
              </Stack>

              <Button
                variant="outlined"
                startIcon={<RefreshRoundedIcon />}
                disabled={!selectedDeploymentId}
                onClick={() => {
                  void queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] })
                  void queryClient.invalidateQueries({ queryKey: ['deployment-validation', draftQuery.data?.id] })
                  void queryClient.invalidateQueries({ queryKey: ['railway-preflight'] })
                  void queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] })
                  void queryClient.invalidateQueries({ queryKey: ['deployment-verification-runs', selectedDeploymentId] })
                  void queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] })
                }}
              >
                Refresh gate
              </Button>
            </Stack>

            {selectedDeployment ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={selectedDeployment.status} color="primary" />
                <Chip label={`Active: ${selectedDeployment.activeVersion ?? '—'}`} variant="outlined" />
                {draftQuery.data ? <Chip label={`Draft ${draftQuery.data.revisionNumber}`} variant="outlined" /> : null}
                {workspace?.deployment.approvalRequiredForApply ? (
                  <Chip label="Apply approval required" color="warning" variant="outlined" />
                ) : null}
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to evaluate release readiness.</Alert>
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
                    <Typography variant="h6">Apply gate</Typography>
                    <Chip
                      color={verificationStatusColor(applyGate.status)}
                      icon={applyGate.status === 'READY' ? <CheckCircleRoundedIcon /> : <WarningAmberRoundedIcon />}
                      label={applyGate.status}
                      sx={{ alignSelf: 'flex-start' }}
                    />
                    <Typography variant="body2" color="text.secondary">
                      {applyGate.message}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {applyGateChecks.map((gate) => (
                        <Chip
                          key={gate.key}
                          size="small"
                          label={`${gate.label}: ${gate.status}`}
                          color={verificationStatusColor(gate.status)}
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Latest rollout</Typography>
                    {latestRelease ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip label={latestRelease.status} color={releaseStatusColor(latestRelease.status)} />
                          <Chip label={`Provisioning: ${latestRelease.provisioningStatus}`} variant="outlined" />
                          <Chip label={`Verification: ${latestRelease.verificationStatus}`} variant="outlined" />
                        </Stack>
                        <Typography variant="body2">
                          Version: <strong>{latestRelease.deploymentVersionId}</strong>
                        </Typography>
                        <Typography variant="body2">
                          Current step: <strong>{latestRelease.currentStepDescription ?? '—'}</strong>
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Updated {formatTimestamp(latestRelease.updatedAt)}
                        </Typography>
                        {latestRelease.errorMessage ? <Alert severity="error">{latestRelease.errorMessage}</Alert> : null}
                      </>
                    ) : (
                      <Alert severity="info">No apply has been started for this deployment yet.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Latest verification run</Typography>
                    {latestVerification ? (
                      <>
                        <Chip
                          label={latestVerification.status}
                          color={verificationStatusColor(latestVerification.status)}
                          sx={{ alignSelf: 'flex-start' }}
                        />
                        <Typography variant="body2">{latestVerification.summaryMessage}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          Completed {formatOptionalTimestamp(latestVerification.completedAt)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Run type {latestVerification.verificationType}
                        </Typography>
                      </>
                    ) : (
                      <Alert severity="info">No verification run exists yet for this deployment.</Alert>
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
                  <Typography variant="h6">Required checks before apply</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    These checks combine draft validation and platform provisioning prerequisites.
                  </Typography>
                </Box>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Check</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Guidance</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {applyGateChecks.map((gate) => (
                      <TableRow key={gate.key} hover>
                        <TableCell>{gate.label}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={gate.status}
                            color={verificationStatusColor(gate.status)}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell>{gate.message}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>

                {validationQuery.data && validationQuery.data.issues.length > 0 ? (
                  <Alert severity={validationQuery.data.publishReady ? 'warning' : 'error'}>
                    Draft validation has {validationQuery.data.errorCount} error(s) and {validationQuery.data.warningCount} warning(s).
                  </Alert>
                ) : (
                  <Alert severity="success">Draft validation has no findings blocking publish.</Alert>
                )}

                {failedPreflightChecks.length > 0 ? (
                  <Stack spacing={1}>
                    {failedPreflightChecks.map((check: RailwayPreflightCheckSummary) => (
                      <Alert key={check.key} severity="error">
                        <strong>{check.message}</strong>
                        <br />
                        {check.details ?? 'No extra details recorded.'}
                      </Alert>
                    ))}
                  </Stack>
                ) : (
                  <Alert severity="success">Railway preflight is ready for apply.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2.5}>
            {serviceHealth.map((summary) => (
              <Grid item xs={12} md={4} key={summary.key}>
                <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Typography variant="h6">{summary.label}</Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Passed ${summary.passed}`} color="success" variant="outlined" />
                        <Chip label={`Warnings ${summary.warning}`} color="warning" variant="outlined" />
                        <Chip label={`Failed ${summary.failed}`} color="error" variant="outlined" />
                        <Chip label={`Skipped ${summary.skipped}`} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {summary.failed > 0
                          ? 'This service still has failed rollout checks.'
                          : summary.warning > 0
                            ? 'This service passed with warnings that should be reviewed.'
                            : latestVerification
                              ? 'This service passed the latest verification run.'
                              : 'No verification run recorded yet.'}
                      </Typography>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Post-apply verification details</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Failed and warning checks are surfaced here with their recorded details so operators can
                    diagnose rollout problems without leaving the deployment workspace.
                  </Typography>
                </Box>

                {latestVerification == null ? (
                  <Alert severity="info">Apply a version first to generate post-apply verification evidence.</Alert>
                ) : failedOrWarningChecks.length === 0 ? (
                  <Alert severity="success">The latest verification run has no failed or warning checks.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Service</TableCell>
                        <TableCell>Check</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Message</TableCell>
                        <TableCell>Details</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {failedOrWarningChecks.map((check) => (
                        <TableRow key={check.name} hover>
                          <TableCell>{checkGroupKey(check.name)}</TableCell>
                          <TableCell sx={{ fontFamily: 'monospace' }}>{check.name}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={check.status}
                              color={verificationStatusColor(check.status)}
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>{check.message}</TableCell>
                          <TableCell sx={{ maxWidth: 320 }}>{formatDetails(check.details)}</TableCell>
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
