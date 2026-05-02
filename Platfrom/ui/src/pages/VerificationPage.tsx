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
import { type ChangeEvent, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { HostedVerificationRunHistory } from '../components/HostedVerificationRunHistory'
import {
  type DraftValidationIssue,
  dispatchDeploymentHostedVerification,
  fetchDeploymentDraft,
  fetchDeploymentHostedVerificationRuns,
  fetchDeploymentReleases,
  fetchDeploymentTargetProfilePreflight,
  fetchDeploymentTargetProfiles,
  fetchDeploymentVerificationRollouts,
  fetchDeploymentVerificationRuns,
  type DeploymentHostedVerificationRunSummary,
  type DeploymentProviderPreflightSummary,
  type DeploymentTargetProfileSummary,
  validateDeploymentDraft,
  type DeploymentReleaseSummary,
  type DeploymentVerificationRolloutSummary,
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

function findCheckByName(checks: VerificationCheck[], name: string): VerificationCheck | null {
  return checks.find((check) => check.name === name) ?? null
}

function readDetailString(details: unknown, key: string): string | null {
  if (!isRecord(details)) {
    return null
  }
  const value = details[key]
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null
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
    || ['QUEUED', 'RUNNING', 'AWAITING_CONFIRMATION'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
}

function isActivationUnconfirmed(release: DeploymentReleaseSummary | null | undefined): boolean {
  return release?.status === 'PROVISIONING' && release.provisioningStatus === 'AWAITING_CONFIRMATION'
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
    { key: 'connector', label: 'Connector operational surface', passed: 0, warning: 0, failed: 0, skipped: 0 },
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
        ? 'Deployment provider preflight is ready.'
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

type DraftIssueNavigation = {
  label: string
  path: string
}

function issueWorkspacePath(issue: DraftValidationIssue): string {
  if (issue.section === 'actions' || issue.section === 'routing') {
    return '/actions'
  }
  if (issue.section === 'knowledge') {
    return '/knowledge'
  }
  if (issue.section === 'providers') {
    return '/providers'
  }
  if (issue.section === 'security') {
    return '/security'
  }
  if (issue.section === 'prompts') {
    return '/prompts'
  }
  return '/revisions'
}

function issueWorkspaceLabel(issue: DraftValidationIssue): string {
  const path = issueWorkspacePath(issue)
  if (path === '/actions') {
    return issue.section === 'routing' ? 'Open Actions and routing' : 'Open Actions'
  }
  if (path === '/knowledge') {
    return 'Open Knowledge'
  }
  if (path === '/providers') {
    return 'Open Providers'
  }
  if (path === '/security') {
    return 'Open Security'
  }
  if (path === '/prompts') {
    return 'Open Prompts'
  }
  return 'Open Revisions'
}

export function VerificationPage() {
  const auth = usePlatformAuth()
  const { buildWorkspacePath, selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [hostedProfile, setHostedProfile] = useState<'vector' | 'ecommerce' | 'marketplace-runtime'>('vector')
  const [hostedProfileAutoSelectedFor, setHostedProfileAutoSelectedFor] = useState<string | null>(null)
  const canManageHostedVerification = auth.session?.enabled ? auth.session.canManageUsers : true
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

  const targetProfilesQuery = useQuery({
    queryKey: ['deployment-target-profiles'],
    queryFn: () => fetchDeploymentTargetProfiles(),
    enabled: selectedDeploymentId.length > 0,
  })

  const runtimeDefaultTargetProfile = useMemo<DeploymentTargetProfileSummary | null>(
    () => (targetProfilesQuery.data ?? []).find((profile) => profile.active && profile.defaultForRuntime) ?? null,
    [targetProfilesQuery.data],
  )

  const providerPreflightQuery = useQuery({
    queryKey: ['deployment-provider-preflight', runtimeDefaultTargetProfile?.id],
    queryFn: () => fetchDeploymentTargetProfilePreflight(runtimeDefaultTargetProfile!.id),
    enabled: !!runtimeDefaultTargetProfile,
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

  const hostedRunsQuery = useQuery({
    queryKey: ['deployment-hosted-verification-runs', selectedDeploymentId],
    queryFn: () => fetchDeploymentHostedVerificationRuns(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && canManageHostedVerification,
    refetchInterval: (query) => {
      const runs = (query.state.data as DeploymentHostedVerificationRunSummary[] | undefined) ?? []
      return runs.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING') ? 4000 : false
    },
  })

  const verificationRolloutsQuery = useQuery({
    queryKey: ['deployment-verification-rollouts'],
    queryFn: fetchDeploymentVerificationRollouts,
    enabled: canManageHostedVerification,
  })

  useEffect(() => {
    if (!selectedDeploymentId || !verificationRolloutsQuery.data) {
      return
    }
    if (hostedProfileAutoSelectedFor === selectedDeploymentId) {
      return
    }
    const rollout = verificationRolloutsQuery.data.items.find((item) => item.deploymentId === selectedDeploymentId)
    if (!rollout) {
      return
    }
    setHostedProfile(
      rollout.verificationProfile === 'ecommerce' || rollout.verificationProfile === 'marketplace-runtime'
        ? rollout.verificationProfile
        : 'vector',
    )
    setHostedProfileAutoSelectedFor(selectedDeploymentId)
  }, [hostedProfileAutoSelectedFor, selectedDeploymentId, verificationRolloutsQuery.data])

  const dispatchHostedVerificationMutation = useMutation({
    mutationFn: (profile: 'vector' | 'ecommerce' | 'marketplace-runtime') =>
      dispatchDeploymentHostedVerification(selectedDeploymentId, {
        profile,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['deployment-hosted-verification-runs', selectedDeploymentId] })
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
  const authenticatedTokenCheck = useMemo(
    () => findCheckByName(verificationChecks, 'platform_authenticated_runtime_token_creation_ready'),
    [verificationChecks],
  )
  const failedOrWarningChecks = useMemo(
    () => verificationChecks.filter((check) => check.status !== 'PASSED' && check.status !== 'SKIPPED'),
    [verificationChecks],
  )
  const serviceHealth = useMemo(
    () => buildServiceHealthSummaries(verificationChecks),
    [verificationChecks],
  )
  const providerPreflight = providerPreflightQuery.data as DeploymentProviderPreflightSummary | undefined
  const providerPreflightReady = providerPreflight?.status === 'READY'
  const providerPreflightChecks = useMemo(
    () => providerPreflight?.checks ?? [],
    [providerPreflight?.checks],
  )
  const draftIssuesWithNavigation = useMemo(
    () => (validationQuery.data?.issues ?? []).map((issue) => ({
      issue,
      navigation: {
        label: issueWorkspaceLabel(issue),
        path: buildWorkspacePath(issueWorkspacePath(issue)),
      } satisfies DraftIssueNavigation,
    })),
    [buildWorkspacePath, validationQuery.data?.issues],
  )
  const applyGateChecks = useMemo(
    () => buildGateSummary({
      publishReady: validationQuery.data?.publishReady ?? false,
      hasPublishedVersion: workspace?.lifecycle.hasPublishedVersion ?? false,
      preflightReady: providerPreflightReady,
      applyRunning: latestRelease ? isReleaseInProgress(latestRelease) : false,
      approvalRequired: workspace?.deployment.approvalRequiredForApply ?? false,
    }),
    [
      latestRelease,
      providerPreflightReady,
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
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: 0 }}>
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
                  void queryClient.invalidateQueries({ queryKey: ['deployment-target-profiles'] })
                  void queryClient.invalidateQueries({ queryKey: ['deployment-provider-preflight'] })
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
                        {latestRelease.errorMessage ? (
                          <Alert severity={isActivationUnconfirmed(latestRelease) ? 'warning' : 'error'}>
                            {latestRelease.errorMessage}
                          </Alert>
                        ) : null}
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

                {draftIssuesWithNavigation.length > 0 ? (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Severity</TableCell>
                        <TableCell>Finding</TableCell>
                        <TableCell>Section</TableCell>
                        <TableCell>Open</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {draftIssuesWithNavigation.map(({ issue, navigation }) => (
                        <TableRow key={`${issue.section}:${issue.code}:${issue.path}`} hover>
                          <TableCell>
                            <Chip
                              size="small"
                              label={issue.severity}
                              color={issue.severity === 'ERROR' ? 'error' : 'warning'}
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell sx={{ maxWidth: 520 }}>
                            <Button
                              component={Link}
                              to={navigation.path}
                              color="inherit"
                              sx={{
                                justifyContent: 'flex-start',
                                px: 0,
                                py: 0,
                                minWidth: 0,
                                textAlign: 'left',
                                textTransform: 'none',
                              }}
                            >
                              {issue.message}
                            </Button>
                            <Typography variant="caption" color="text.secondary" display="block">
                              {issue.code} · {issue.path}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ textTransform: 'capitalize' }}>{issue.section}</TableCell>
                          <TableCell>
                            <Button component={Link} to={navigation.path} size="small" variant="outlined">
                              {navigation.label}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : null}

                {targetProfilesQuery.isLoading || providerPreflightQuery.isLoading ? (
                  <Alert severity="info">Running deployment provider preflight...</Alert>
                ) : targetProfilesQuery.isError ? (
                  <Alert severity="error">
                    {targetProfilesQuery.error instanceof Error
                      ? targetProfilesQuery.error.message
                      : 'Failed to load deployment target profiles'}
                  </Alert>
                ) : providerPreflightQuery.isError ? (
                  <Alert severity="error">
                    {providerPreflightQuery.error instanceof Error
                      ? providerPreflightQuery.error.message
                      : 'Failed to run deployment provider preflight'}
                  </Alert>
                ) : !runtimeDefaultTargetProfile ? (
                  <Alert severity="error">No active runtime default target profile is configured.</Alert>
                ) : !providerPreflightReady ? (
                  <Stack spacing={1}>
                    <Alert severity="error">
                      <strong>{providerPreflight?.message ?? 'Deployment provider preflight is blocked.'}</strong>
                      <br />
                      Target profile: {runtimeDefaultTargetProfile.name} ({runtimeDefaultTargetProfile.providerType})
                    </Alert>
                    {providerPreflightChecks.map((check) => (
                      <Alert key={check} severity="warning">{check}</Alert>
                    ))}
                  </Stack>
                ) : (
                  <Alert severity="success">
                    Deployment provider preflight is ready for apply on {runtimeDefaultTargetProfile.name} ({runtimeDefaultTargetProfile.providerType}).
                  </Alert>
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
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="h6">Authenticated token path</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Verifies that the platform can mint the private runtime assertion used for authenticated
                    POC and operator traffic on the verified <code>/api/chat/me/*</code> surface.
                  </Typography>
                </Box>

                {authenticatedTokenCheck ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip
                        label={authenticatedTokenCheck.status}
                        color={verificationStatusColor(authenticatedTokenCheck.status)}
                      />
                      {readDetailString(authenticatedTokenCheck.details, 'actualIssuer') ? (
                        <Chip
                          label={`Issuer ${readDetailString(authenticatedTokenCheck.details, 'actualIssuer')}`}
                          variant="outlined"
                        />
                      ) : null}
                      {readDetailString(authenticatedTokenCheck.details, 'actualAudience') ? (
                        <Chip
                          label={`Audience ${readDetailString(authenticatedTokenCheck.details, 'actualAudience')}`}
                          variant="outlined"
                        />
                      ) : null}
                    </Stack>
                    <Typography variant="body2">{authenticatedTokenCheck.message}</Typography>
                    {authenticatedTokenCheck.status !== 'PASSED' ? (
                      <Typography variant="body2" color="text.secondary">
                        {formatDetails(authenticatedTokenCheck.details)}
                      </Typography>
                    ) : null}
                  </>
                ) : (
                  <Alert severity="info">
                    No authenticated-token verification check has been recorded for the latest run yet.
                  </Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

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

          {canManageHostedVerification ? (
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h6">Platform-hosted verification</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Run the deployment verification scripts from the platform deployment itself. This admin-only runner
                      is asynchronous, read-only, and intended for rare operator diagnostics. Fleet-wide canonical rollout
                      orchestration now lives on Verification Ops, while this page stays scoped to the selected deployment.
                    </Typography>
                  </Box>

                  <Alert severity="info">
                    This runner always executes in read-only mode. It uses the active deployment release plus the
                    platform-managed connector and admin secrets already stored in the platform. No deployment writes are
                    performed from this UI path.
                  </Alert>

                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
                    <TextField
                      select
                      label="Verification profile"
                      value={hostedProfile}
                      onChange={(event: ChangeEvent<HTMLInputElement>) =>
                        setHostedProfile(event.target.value as 'vector' | 'ecommerce' | 'marketplace-runtime')
                      }
                      size="small"
                      sx={{ minWidth: 220 }}
                    >
                      <MenuItem value="vector">Vector deployment</MenuItem>
                      <MenuItem value="ecommerce">Ecommerce deployment</MenuItem>
                      <MenuItem value="marketplace-runtime">Marketplace runtime</MenuItem>
                    </TextField>
                    <Button
                      variant="contained"
                      startIcon={<RefreshRoundedIcon />}
                      disabled={!selectedDeploymentId || dispatchHostedVerificationMutation.isPending}
                      onClick={() => dispatchHostedVerificationMutation.mutate(hostedProfile)}
                    >
                      {dispatchHostedVerificationMutation.isPending ? 'Queueing…' : 'Run on platform'}
                    </Button>
                    <Typography variant="body2" color="text.secondary">
                      The platform uses the current active release and verifies it without changing deployment data.
                    </Typography>
                  </Stack>

                  {dispatchHostedVerificationMutation.isError ? (
                    <Alert severity="error">
                      {dispatchHostedVerificationMutation.error instanceof Error
                        ? dispatchHostedVerificationMutation.error.message
                        : 'Failed to queue hosted verification'}
                    </Alert>
                  ) : null}

                  {dispatchHostedVerificationMutation.isSuccess ? (
                    <Alert severity="success">{dispatchHostedVerificationMutation.data.summaryMessage}</Alert>
                  ) : null}

                  {hostedRunsQuery.isLoading ? (
                    <Typography color="text.secondary">Loading hosted verification runs…</Typography>
                  ) : hostedRunsQuery.isError ? (
                    <Alert severity="error">
                      {hostedRunsQuery.error instanceof Error
                        ? hostedRunsQuery.error.message
                        : 'Failed to load hosted verification runs'}
                    </Alert>
                  ) : (hostedRunsQuery.data?.length ?? 0) === 0 ? (
                    <Alert severity="info">No platform-hosted verification runs were recorded for this deployment yet.</Alert>
                  ) : (
                    <HostedVerificationRunHistory runs={(hostedRunsQuery.data ?? []).slice(0, 8)} />
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : null}
        </>
      ) : null}
    </Stack>
  )
}
