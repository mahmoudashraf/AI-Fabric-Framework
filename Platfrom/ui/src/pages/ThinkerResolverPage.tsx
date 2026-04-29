import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import GppGoodRoundedIcon from '@mui/icons-material/GppGoodRounded'
import ManageSearchRoundedIcon from '@mui/icons-material/ManageSearchRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import PsychologyAltRoundedIcon from '@mui/icons-material/PsychologyAltRounded'
import RuleRoundedIcon from '@mui/icons-material/RuleRounded'
import SecurityRoundedIcon from '@mui/icons-material/SecurityRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  FormControl,
  Grid,
  InputLabel,
  LinearProgress,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useEffect, useMemo, useState } from 'react'
import {
  createResolverProposal,
  createThinkerSession,
  downloadThinkerSessionExport,
  executeResolverProposal,
  fetchDeployments,
  fetchResolverExecutions,
  fetchResolverPolicyDecisions,
  fetchResolverProposals,
  fetchThinkerDeploymentControl,
  fetchThinkerReadiness,
  fetchThinkerSession,
  fetchThinkerSessions,
  runResolverDryRun,
  updateThinkerDeploymentControl,
  type DeploymentSummary,
  type ResolverIntentProposalSummary,
  type ThinkerIssueSessionSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

const TERMINAL_STATUS_COLORS = new Set(['RESOLVED', 'EXECUTED', 'READY', 'PASS', 'ALLOWED', 'VERIFIED'])
const WARNING_STATUS_COLORS = new Set(['WRITE_REQUIRED_ESCALATED', 'DRY_RUN_READY', 'ATTENTION', 'INSUFFICIENT_EVIDENCE'])
const ERROR_STATUS_COLORS = new Set(['BLOCKED', 'FAILED', 'DENIED', 'DISABLED'])

function statusColor(status: string | null | undefined): 'success' | 'warning' | 'error' | 'info' | 'default' {
  const normalized = (status ?? '').toUpperCase()
  if (TERMINAL_STATUS_COLORS.has(normalized)) return 'success'
  if (WARNING_STATUS_COLORS.has(normalized)) return 'warning'
  if (ERROR_STATUS_COLORS.has(normalized)) return 'error'
  if (['RUNNING', 'QUEUED', 'DRY_RUN_COMPLETED'].includes(normalized)) return 'info'
  return 'default'
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return 'not recorded'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString()
}

function compactJson(value: unknown): string {
  if (value == null) return 'none'
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function defaultDeployment(deployments: DeploymentSummary[]): DeploymentSummary | null {
  return deployments.find((deployment) => deployment.templateId?.includes('shopify')) ?? deployments[0] ?? null
}

export function ThinkerResolverPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [selectedSessionId, setSelectedSessionId] = useState('')
  const [shopDomainFilter, setShopDomainFilter] = useState('')
  const [sessionQuestion, setSessionQuestion] = useState('')
  const [sessionAnswer, setSessionAnswer] = useState('')
  const [sessionEvidence, setSessionEvidence] = useState('')
  const [proposalText, setProposalText] = useState('')
  const [confirmationText, setConfirmationText] = useState('')

  const canManage = auth.session?.enabled ? auth.session.role === 'PLATFORM_ADMIN' : true
  const deploymentsQuery = useQuery({ queryKey: ['deployments'], queryFn: fetchDeployments })
  const readinessQuery = useQuery({ queryKey: ['thinker-readiness'], queryFn: fetchThinkerReadiness })
  const sessionsQuery = useQuery({
    queryKey: ['thinker-sessions', selectedDeploymentId, shopDomainFilter],
    queryFn: () => fetchThinkerSessions({
      deploymentId: selectedDeploymentId || undefined,
      shopDomain: shopDomainFilter.trim() || undefined,
    }),
  })
  const selectedSessionQuery = useQuery({
    queryKey: ['thinker-session', selectedSessionId],
    queryFn: () => fetchThinkerSession(selectedSessionId),
    enabled: Boolean(selectedSessionId),
  })
  const controlQuery = useQuery({
    queryKey: ['thinker-control', selectedDeploymentId],
    queryFn: () => fetchThinkerDeploymentControl(selectedDeploymentId),
    enabled: Boolean(selectedDeploymentId),
  })
  const proposalsQuery = useQuery({ queryKey: ['resolver-proposals'], queryFn: fetchResolverProposals })
  const policiesQuery = useQuery({ queryKey: ['resolver-policy-decisions'], queryFn: fetchResolverPolicyDecisions })
  const executionsQuery = useQuery({ queryKey: ['resolver-executions'], queryFn: fetchResolverExecutions })

  const deployments = deploymentsQuery.data ?? []
  const sessions = sessionsQuery.data ?? []
  const selectedSession = selectedSessionQuery.data ?? null
  const sessionProposalIds = new Set(selectedSession?.resolverProposals.map((proposal) => proposal.id) ?? [])
  const visibleProposals = (proposalsQuery.data ?? []).filter((proposal) => (
    selectedSessionId ? proposal.sessionId === selectedSessionId : true
  ))
  const latestProposal = selectedSession?.resolverProposals[0] ?? visibleProposals[0] ?? null
  const idempotencyKey = useMemo(() => {
    if (!latestProposal) return ''
    return `operator-${latestProposal.id}-${Date.now()}`
  }, [latestProposal?.id])

  useEffect(() => {
    if (!selectedDeploymentId && deployments.length > 0) {
      setSelectedDeploymentId(defaultDeployment(deployments)?.id ?? '')
    }
  }, [deployments, selectedDeploymentId])

  useEffect(() => {
    if (!selectedSessionId && sessions.length > 0) {
      setSelectedSessionId(sessions[0].id)
    }
  }, [sessions, selectedSessionId])

  useEffect(() => {
    if (selectedSession && !proposalText) {
      setProposalText(`Create a partner support escalation for ${selectedSession.session.shopDomain ?? selectedSession.session.deploymentId}`)
    }
  }, [proposalText, selectedSession])

  const invalidateThinker = async () => {
    await queryClient.invalidateQueries({ queryKey: ['thinker-readiness'] })
    await queryClient.invalidateQueries({ queryKey: ['thinker-sessions'] })
    await queryClient.invalidateQueries({ queryKey: ['thinker-session'] })
    await queryClient.invalidateQueries({ queryKey: ['thinker-control'] })
    await queryClient.invalidateQueries({ queryKey: ['resolver-proposals'] })
    await queryClient.invalidateQueries({ queryKey: ['resolver-policy-decisions'] })
    await queryClient.invalidateQueries({ queryKey: ['resolver-executions'] })
  }

  const controlMutation = useMutation({
    mutationFn: (payload: { thinkerEnabled?: boolean; resolverPreviewEnabled?: boolean; governedExecutionEnabled?: boolean; disabledActionFamilies?: string[] }) => (
      updateThinkerDeploymentControl(selectedDeploymentId, payload)
    ),
    onSuccess: invalidateThinker,
  })
  const createSessionMutation = useMutation({
    mutationFn: () => createThinkerSession({
      deploymentId: selectedDeploymentId,
      shopDomain: shopDomainFilter.trim() || undefined,
      userQuestion: sessionQuestion.trim(),
      userSafeAnswer: sessionAnswer.trim() || undefined,
      evidence: [{
        kind: 'OPERATOR_DIAGNOSTIC',
        sourceKind: 'OPERATOR_CONSOLE',
        sourceIdentifier: 'thinker-resolver-page',
        freshness: 'FRESH',
        confidence: 0.85,
        summary: sessionEvidence.trim(),
        safeSummary: sessionEvidence.trim(),
      }],
    }),
    onSuccess: async (session) => {
      setSelectedSessionId(session.id)
      setSessionQuestion('')
      setSessionAnswer('')
      setSessionEvidence('')
      await invalidateThinker()
    },
  })
  const createProposalMutation = useMutation({
    mutationFn: () => {
      if (!selectedSession) throw new Error('Select a Thinker session first.')
      return createResolverProposal({
        sessionId: selectedSession.session.id,
        actionId: 'create_support_escalation',
        actionFamily: 'SUPPORT_ESCALATION',
        targetDomain: 'PARTNER_ENABLEMENT',
        productBoundary: 'SHOPIFY_COMPANION',
        proposalText: proposalText.trim(),
        parameters: {
          title: `Thinker diagnosis: ${selectedSession.session.issueCategory}`,
          severity: selectedSession.session.riskClass === 'HIGH' ? 'HIGH' : 'MEDIUM',
          nextAction: selectedSession.plan?.recommendedNextStep ?? selectedSession.session.recommendation,
        },
        sourceEvidenceItemIds: selectedSession.evidence.map((item) => item.id),
      })
    },
    onSuccess: invalidateThinker,
  })
  const dryRunMutation = useMutation({
    mutationFn: (proposalId: string) => runResolverDryRun(proposalId),
    onSuccess: invalidateThinker,
  })
  const executeMutation = useMutation({
    mutationFn: (proposal: ResolverIntentProposalSummary) => executeResolverProposal(proposal.id, {
      idempotencyKey: idempotencyKey || `operator-${proposal.id}`,
      confirmationText: confirmationText.trim(),
      dryRunId: proposal.latestDryRun?.id,
    }),
    onSuccess: async () => {
      setConfirmationText('')
      await invalidateThinker()
    },
  })
  const exportMutation = useMutation({
    mutationFn: (sessionId: string) => downloadThinkerSessionExport(sessionId),
    onSuccess: (blob, sessionId) => downloadBlob(blob, `thinker-session-${sessionId}.md`),
  })

  if (deploymentsQuery.isLoading || readinessQuery.isLoading) {
    return (
      <Stack spacing={3}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>Thinker Resolver</Typography>
        <LinearProgress />
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="006 Governed Issue Resolution" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800 }}>Thinker Resolver</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1, maxWidth: 1180 }}>
          Read-only diagnosis, evidence capture, policy simulation, and governed low-risk support escalation for Shopify Companion deployments.
        </Typography>
      </Box>

      {!canManage ? <Alert severity="info">Operator access can review records. Platform admin access is required to change controls or execute governed actions.</Alert> : null}
      {controlMutation.isError ? <ErrorAlert error={controlMutation.error} fallback="Control update failed." /> : null}
      {createSessionMutation.isError ? <ErrorAlert error={createSessionMutation.error} fallback="Thinker session creation failed." /> : null}
      {createProposalMutation.isError ? <ErrorAlert error={createProposalMutation.error} fallback="Resolver proposal creation failed." /> : null}
      {dryRunMutation.isError ? <ErrorAlert error={dryRunMutation.error} fallback="Resolver dry-run failed." /> : null}
      {executeMutation.isError ? <ErrorAlert error={executeMutation.error} fallback="Resolver execution failed." /> : null}
      {exportMutation.isError ? <ErrorAlert error={exportMutation.error} fallback="Session export failed." /> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <SummaryCard icon={<PsychologyAltRoundedIcon />} title="Readiness" value={readinessQuery.data?.decision ?? 'UNKNOWN'} status={readinessQuery.data?.status ?? 'UNKNOWN'} />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard icon={<ManageSearchRoundedIcon />} title="Sessions" value={`${sessions.length}`} status={sessions.length > 0 ? 'READY' : 'NOT_RUN'} detail="Persisted Thinker issue records" />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard icon={<RuleRoundedIcon />} title="Policies" value={`${policiesQuery.data?.length ?? 0}`} status={policiesQuery.data?.some((item) => item.outcome === 'DENIED') ? 'ATTENTION' : 'READY'} detail="Resolver decisions" />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard icon={<GppGoodRoundedIcon />} title="Executions" value={`${executionsQuery.data?.length ?? 0}`} status={executionsQuery.data?.some((item) => item.status === 'FAILED') ? 'FAILED' : 'READY'} detail="Governed actions" />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={4}>
          <SectionCard title="Deployment Control" icon={<SecurityRoundedIcon />}>
            <Stack spacing={2}>
              <FormControl fullWidth size="small">
                <InputLabel id="thinker-deployment-select">Deployment</InputLabel>
                <Select
                  labelId="thinker-deployment-select"
                  label="Deployment"
                  value={selectedDeploymentId}
                  onChange={(event) => {
                    setSelectedDeploymentId(event.target.value)
                    setSelectedSessionId('')
                  }}
                >
                  {deployments.map((deployment) => (
                    <MenuItem key={deployment.id} value={deployment.id}>
                      {deployment.name} - {deployment.environment}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField size="small" label="Shop domain filter" value={shopDomainFilter} onChange={(event) => setShopDomainFilter(event.target.value)} placeholder="shopping-companion-test.myshopify.com" />
              {controlQuery.isLoading && selectedDeploymentId ? <LinearProgress /> : null}
              {controlQuery.data ? (
                <Stack spacing={1.25}>
                  <ToggleRow
                    label="Thinker diagnosis"
                    checked={controlQuery.data.thinkerEnabled}
                    disabled={!canManage || controlMutation.isPending}
                    onChange={(checked) => controlMutation.mutate({ thinkerEnabled: checked })}
                  />
                  <ToggleRow
                    label="Resolver preview"
                    checked={controlQuery.data.resolverPreviewEnabled}
                    disabled={!canManage || controlMutation.isPending}
                    onChange={(checked) => controlMutation.mutate({ resolverPreviewEnabled: checked })}
                  />
                  <ToggleRow
                    label="Governed execution"
                    checked={controlQuery.data.governedExecutionEnabled}
                    disabled={!canManage || controlMutation.isPending}
                    onChange={(checked) => controlMutation.mutate({ governedExecutionEnabled: checked })}
                  />
                  <Divider />
                  <Stack spacing={0.75}>
                    <Typography variant="subtitle2">Disabled action families</Typography>
                    <Chip
                      label={controlQuery.data.disabledActionFamilies.length ? controlQuery.data.disabledActionFamilies.join(', ') : 'None'}
                      color={controlQuery.data.disabledActionFamilies.length ? 'warning' : 'success'}
                      variant="outlined"
                    />
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={!canManage || controlMutation.isPending}
                      onClick={() => {
                        const disabled = controlQuery.data?.disabledActionFamilies.includes('SUPPORT_ESCALATION')
                        controlMutation.mutate({ disabledActionFamilies: disabled ? [] : ['SUPPORT_ESCALATION'] })
                      }}
                    >
                      Toggle support escalation kill switch
                    </Button>
                  </Stack>
                </Stack>
              ) : (
                <Alert severity="info">Choose a deployment to load Thinker controls.</Alert>
              )}
            </Stack>
          </SectionCard>
        </Grid>

        <Grid item xs={12} lg={8}>
          <SectionCard title="Readiness Scenarios" icon={<GppGoodRoundedIcon />}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Scenario</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Evidence</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(readinessQuery.data?.scenarios ?? []).map((scenario) => (
                  <TableRow key={scenario.key}>
                    <TableCell>
                      <Typography variant="subtitle2">{scenario.label}</Typography>
                      <Typography variant="caption" color="text.secondary">{scenario.key}</Typography>
                    </TableCell>
                    <TableCell><Chip size="small" label={scenario.status} color={statusColor(scenario.status)} variant="outlined" /></TableCell>
                    <TableCell>{scenario.evidence}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </SectionCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={4}>
          <SectionCard title="Captured Sessions" icon={<ManageSearchRoundedIcon />}>
            {sessionsQuery.isLoading ? <LinearProgress /> : null}
            {sessions.length === 0 ? (
              <Alert severity="info">No Thinker sessions are captured for the current filter.</Alert>
            ) : (
              <Stack spacing={1}>
                {sessions.map((session) => (
                  <Paper
                    key={session.id}
                    variant="outlined"
                    onClick={() => setSelectedSessionId(session.id)}
                    sx={{
                      p: 1.5,
                      cursor: 'pointer',
                      borderColor: selectedSessionId === session.id ? 'primary.main' : 'divider',
                      bgcolor: selectedSessionId === session.id ? 'action.selected' : 'background.paper',
                    }}
                  >
                    <Stack spacing={0.75}>
                      <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                        <Chip size="small" label={session.status} color={statusColor(session.status)} variant="outlined" />
                        <Typography variant="caption" color="text.secondary">{formatDateTime(session.startedAt)}</Typography>
                      </Stack>
                      <Typography variant="subtitle2">{session.issueCategory}</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                        {session.userQuestion}
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Chip size="small" label={`${session.evidenceCount} evidence`} />
                        {session.shopDomain ? <Chip size="small" label={session.shopDomain} variant="outlined" /> : null}
                      </Stack>
                    </Stack>
                  </Paper>
                ))}
              </Stack>
            )}
            <Divider sx={{ my: 2 }} />
            <Stack spacing={1.25}>
              <Typography variant="subtitle2">Capture diagnostic session</Typography>
              <TextField size="small" label="Question" value={sessionQuestion} onChange={(event) => setSessionQuestion(event.target.value)} multiline minRows={2} disabled={!canManage || createSessionMutation.isPending} />
              <TextField size="small" label="Safe answer" value={sessionAnswer} onChange={(event) => setSessionAnswer(event.target.value)} multiline minRows={2} disabled={!canManage || createSessionMutation.isPending} />
              <TextField size="small" label="Evidence summary" value={sessionEvidence} onChange={(event) => setSessionEvidence(event.target.value)} multiline minRows={3} disabled={!canManage || createSessionMutation.isPending} />
              <Button
                variant="contained"
                disabled={!canManage || !selectedDeploymentId || sessionQuestion.trim().length < 5 || sessionEvidence.trim().length < 5 || createSessionMutation.isPending}
                onClick={() => createSessionMutation.mutate()}
              >
                Capture session
              </Button>
            </Stack>
          </SectionCard>
        </Grid>

        <Grid item xs={12} lg={8}>
          <SectionCard title="Session Detail" icon={<PsychologyAltRoundedIcon />}>
            {selectedSessionQuery.isLoading ? <LinearProgress /> : null}
            {!selectedSession ? (
              <Alert severity="info">Select a session to inspect evidence, plan, audit events, and Resolver proposals.</Alert>
            ) : (
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5}>
                  <Box>
                    <Typography variant="h6">{selectedSession.session.issueCategory}</Typography>
                    <Typography variant="body2" color="text.secondary">{selectedSession.session.userQuestion}</Typography>
                  </Box>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={selectedSession.session.status} color={statusColor(selectedSession.session.status)} />
                    <Chip label={selectedSession.session.riskClass} variant="outlined" />
                    <Button size="small" startIcon={<DownloadRoundedIcon />} onClick={() => exportMutation.mutate(selectedSession.session.id)}>
                      Export
                    </Button>
                  </Stack>
                </Stack>
                {selectedSession.plan ? (
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="subtitle2">Resolution plan</Typography>
                    <Typography variant="body2" sx={{ mt: 1 }}>{selectedSession.plan.diagnosis}</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{selectedSession.plan.recommendedNextStep}</Typography>
                  </Paper>
                ) : null}
                <Grid container spacing={1.5}>
                  <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>Evidence</Typography>
                    <Stack spacing={1}>
                      {selectedSession.evidence.map((item) => (
                        <Paper key={item.id} variant="outlined" sx={{ p: 1.5 }}>
                          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.75 }}>
                            <Chip size="small" label={item.kind} />
                            <Chip size="small" label={item.freshness} color={statusColor(item.freshness)} variant="outlined" />
                          </Stack>
                          <Typography variant="body2">{item.safeSummary}</Typography>
                          <Typography variant="caption" color="text.secondary">{item.sourceKind} - {item.sourceIdentifier}</Typography>
                        </Paper>
                      ))}
                    </Stack>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>Audit events</Typography>
                    <Stack spacing={1}>
                      {selectedSession.auditEvents.map((event) => (
                        <Paper key={event.id} variant="outlined" sx={{ p: 1.5 }}>
                          <Typography variant="subtitle2">{event.eventType}</Typography>
                          <Typography variant="caption" color="text.secondary">{event.actorRole} - {formatDateTime(event.createdAt)}</Typography>
                        </Paper>
                      ))}
                    </Stack>
                  </Grid>
                </Grid>
              </Stack>
            )}
          </SectionCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <SectionCard title="Resolver Proposals" icon={<RuleRoundedIcon />}>
            <Stack spacing={2}>
              {selectedSession ? (
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Stack spacing={1.25}>
                    <Typography variant="subtitle2">Create support escalation proposal</Typography>
                    <TextField label="Proposal" size="small" value={proposalText} onChange={(event) => setProposalText(event.target.value)} multiline minRows={2} disabled={!canManage || createProposalMutation.isPending} />
                    <Button
                      variant="contained"
                      disabled={!canManage || proposalText.trim().length < 8 || createProposalMutation.isPending}
                      onClick={() => createProposalMutation.mutate()}
                    >
                      Create policy proposal
                    </Button>
                  </Stack>
                </Paper>
              ) : null}
              {visibleProposals.length === 0 ? (
                <Alert severity="info">No Resolver proposals are available for the selected scope.</Alert>
              ) : (
                <Stack spacing={1.25}>
                  {visibleProposals.map((proposal) => (
                    <Paper key={proposal.id} variant="outlined" sx={{ p: 2, borderColor: sessionProposalIds.has(proposal.id) ? 'primary.main' : 'divider' }}>
                      <Stack spacing={1.25}>
                        <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center">
                          <Stack direction="row" spacing={1} flexWrap="wrap">
                            <Chip size="small" label={proposal.status} color={statusColor(proposal.status)} />
                            <Chip size="small" label={proposal.actionFamily} variant="outlined" />
                          </Stack>
                          <Typography variant="caption" color="text.secondary">{formatDateTime(proposal.createdAt)}</Typography>
                        </Stack>
                        <Typography variant="body2">{proposal.proposalText}</Typography>
                        {proposal.latestPolicyDecision ? (
                          <Alert severity={proposal.latestPolicyDecision.outcome === 'ALLOWED' ? 'success' : 'warning'}>
                            {proposal.latestPolicyDecision.userReason}
                          </Alert>
                        ) : null}
                        {proposal.latestDryRun ? (
                          <Paper variant="outlined" sx={{ p: 1.25, bgcolor: 'background.default' }}>
                            <Typography variant="subtitle2">Dry-run</Typography>
                            <Typography variant="body2" color="text.secondary">{proposal.latestDryRun.expectedStateTransition}</Typography>
                            <Typography variant="caption" color="text.secondary">{proposal.latestDryRun.expectedSideEffects.join(', ')}</Typography>
                          </Paper>
                        ) : null}
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<PlayCircleOutlineRoundedIcon />}
                            disabled={!canManage || dryRunMutation.isPending}
                            onClick={() => dryRunMutation.mutate(proposal.id)}
                          >
                            Dry-run
                          </Button>
                          <TextField
                            size="small"
                            label="Execution confirmation"
                            value={confirmationText}
                            onChange={(event) => setConfirmationText(event.target.value)}
                            placeholder="CREATE SUPPORT ESCALATION"
                            disabled={!canManage || executeMutation.isPending}
                            sx={{ flex: 1 }}
                          />
                          <Button
                            size="small"
                            variant="contained"
                            color="warning"
                            disabled={!canManage || confirmationText.trim() !== 'CREATE SUPPORT ESCALATION' || executeMutation.isPending}
                            onClick={() => executeMutation.mutate(proposal)}
                          >
                            Execute
                          </Button>
                        </Stack>
                      </Stack>
                    </Paper>
                  ))}
                </Stack>
              )}
            </Stack>
          </SectionCard>
        </Grid>

        <Grid item xs={12} lg={6}>
          <SectionCard title="Policy And Execution Ledger" icon={<GppGoodRoundedIcon />}>
            <Grid container spacing={1.5}>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Latest policy decisions</Typography>
                <Stack spacing={1}>
                  {(policiesQuery.data ?? []).slice(0, 8).map((policy) => (
                    <Paper key={policy.id} variant="outlined" sx={{ p: 1.5 }}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip size="small" label={policy.outcome} color={statusColor(policy.outcome)} />
                        <Chip size="small" label={policy.riskLevel} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" sx={{ mt: 1 }}>{policy.operatorReason}</Typography>
                      <Typography component="pre" variant="caption" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                        {compactJson({ requiredScopes: policy.requiredScopes, missingScopes: policy.missingScopes })}
                      </Typography>
                    </Paper>
                  ))}
                </Stack>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>Executions</Typography>
                <Stack spacing={1}>
                  {(executionsQuery.data ?? []).slice(0, 8).map((execution) => (
                    <Paper key={execution.id} variant="outlined" sx={{ p: 1.5 }}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip size="small" label={execution.status} color={statusColor(execution.status)} />
                        <Chip size="small" label={execution.verificationStatus} variant="outlined" />
                      </Stack>
                      <Typography variant="body2" sx={{ mt: 1 }}>{execution.actionFamily}</Typography>
                      <Typography component="pre" variant="caption" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                        {compactJson(execution.executionResult)}
                      </Typography>
                    </Paper>
                  ))}
                </Stack>
              </Grid>
            </Grid>
          </SectionCard>
        </Grid>
      </Grid>
    </Stack>
  )
}

function ErrorAlert({ error, fallback }: { error: unknown; fallback: string }) {
  return <Alert severity="error">{error instanceof Error ? error.message : fallback}</Alert>
}

function SummaryCard({
  icon,
  title,
  value,
  status,
  detail,
}: {
  icon: ReactNode
  title: string
  value: string
  status: string
  detail?: string
}) {
  return (
    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
      <CardContent>
        <Stack spacing={1.25}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Box sx={{ color: 'primary.main', display: 'grid', placeItems: 'center' }}>{icon}</Box>
            <Chip size="small" label={status} color={statusColor(status)} variant="outlined" />
          </Stack>
          <Typography variant="caption" color="text.secondary">{title}</Typography>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>{value}</Typography>
          {detail ? <Typography variant="body2" color="text.secondary">{detail}</Typography> : null}
        </Stack>
      </CardContent>
    </Card>
  )
}

function SectionCard({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return (
    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Box sx={{ color: 'primary.main', display: 'grid', placeItems: 'center' }}>{icon}</Box>
            <Typography variant="h6">{title}</Typography>
          </Stack>
          {children}
        </Stack>
      </CardContent>
    </Card>
  )
}

function ToggleRow({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string
  checked: boolean
  disabled: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="center">
      <Typography variant="body2">{label}</Typography>
      <Stack direction="row" spacing={1} alignItems="center">
        <Chip size="small" label={checked ? 'Enabled' : 'Disabled'} color={checked ? 'success' : 'default'} variant="outlined" />
        <Switch checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      </Stack>
    </Stack>
  )
}
