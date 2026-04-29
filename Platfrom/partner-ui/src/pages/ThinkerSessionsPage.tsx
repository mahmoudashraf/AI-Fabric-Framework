import ManageSearchOutlinedIcon from '@mui/icons-material/ManageSearchOutlined'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined'
import {
  Alert,
  Box,
  Button,
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
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import type { PartnerThinkerSession } from '../api/schemas'
import { listPartnerStores } from '../api/stores'
import { createThinkerEscalation, getThinkerSession, listStoreThinkerSessions } from '../api/thinker'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime, titleize } from '../utils/format'

function severityFor(session: PartnerThinkerSession): string {
  if (session.riskClass === 'HIGH') return 'HIGH'
  if (session.status === 'BLOCKED') return 'HIGH'
  return 'MEDIUM'
}

export function ThinkerSessionsPage() {
  const { api } = useSupabaseAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedStoreId, setSelectedStoreId] = useState(searchParams.get('storeId') ?? '')
  const [selectedSessionId, setSelectedSessionId] = useState(searchParams.get('sessionId') ?? '')
  const [title, setTitle] = useState('')
  const [severity, setSeverity] = useState('MEDIUM')
  const [nextAction, setNextAction] = useState('')

  const storesQuery = useQuery({ queryKey: ['stores'], queryFn: () => listPartnerStores(api) })
  const stores = storesQuery.data ?? []
  const sessionsQuery = useQuery({
    queryKey: ['thinker-sessions', selectedStoreId],
    queryFn: () => listStoreThinkerSessions(api, selectedStoreId),
    enabled: Boolean(selectedStoreId),
  })
  const detailQuery = useQuery({
    queryKey: ['thinker-session', selectedSessionId],
    queryFn: () => getThinkerSession(api, selectedSessionId),
    enabled: Boolean(selectedSessionId),
  })

  const sessions = sessionsQuery.data ?? []
  const selectedSession = detailQuery.data ?? null
  const selectedStore = stores.find((store) => store.id === selectedStoreId) ?? null

  useEffect(() => {
    const firstStore = stores[0]
    if (!selectedStoreId && firstStore) {
      setSelectedStoreId(firstStore.id)
    }
  }, [selectedStoreId, stores])

  useEffect(() => {
    const firstSession = sessions[0]
    if (!selectedSessionId && firstSession) {
      setSelectedSessionId(firstSession.id)
    }
  }, [selectedSessionId, sessions])

  useEffect(() => {
    const params = new URLSearchParams()
    if (selectedStoreId) params.set('storeId', selectedStoreId)
    if (selectedSessionId) params.set('sessionId', selectedSessionId)
    setSearchParams(params, { replace: true })
  }, [selectedSessionId, selectedStoreId, setSearchParams])

  useEffect(() => {
    if (selectedSession) {
      setTitle(`Thinker diagnosis: ${titleize(selectedSession.session.issueCategory)}`)
      setSeverity(severityFor(selectedSession.session))
      setNextAction(selectedSession.plan?.recommendedNextStep ?? selectedSession.session.recommendation ?? '')
    }
  }, [selectedSession])

  const health = useMemo(() => {
    const blocked = sessions.filter((session) => ['BLOCKED', 'FAILED', 'INSUFFICIENT_EVIDENCE'].includes(session.status)).length
    const escalated = sessions.filter((session) => session.status === 'WRITE_REQUIRED_ESCALATED').length
    return { blocked, escalated, total: sessions.length }
  }, [sessions])

  const escalationMutation = useMutation({
    mutationFn: () => {
      if (!selectedSession) throw new Error('Select a Thinker session first.')
      return createThinkerEscalation(api, selectedSession.session.id, {
        title,
        severity,
        nextAction,
      })
    },
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ['escalations'] })
      navigate(`/support/${result.supportEscalationId}`)
    },
  })

  if (storesQuery.isLoading) {
    return <LinearProgress />
  }

  if (storesQuery.isError) {
    return <Alert severity="error">Assigned stores could not be loaded.</Alert>
  }

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Thinker sessions"
        subtitle="Merchant-approved diagnostic evidence and governed escalation handoff."
        breadcrumbs={[{ label: 'Dashboard', to: '/' }, { label: 'Thinker sessions' }]}
      />

      {stores.length === 0 ? (
        <Alert severity="info">No merchant-approved stores are assigned to this partner workspace.</Alert>
      ) : null}
      {escalationMutation.isError ? (
        <Alert severity="error">{escalationMutation.error instanceof Error ? escalationMutation.error.message : 'Escalation could not be created.'}</Alert>
      ) : null}

      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ShieldOutlinedIcon color="primary" />
                <Typography variant="h2">Scope</Typography>
              </Stack>
              <FormControl fullWidth size="small">
                <InputLabel id="thinker-store-select">Store</InputLabel>
                <Select
                  labelId="thinker-store-select"
                  label="Store"
                  value={selectedStoreId}
                  onChange={(event) => {
                    setSelectedStoreId(event.target.value)
                    setSelectedSessionId('')
                  }}
                >
                  {stores.map((store) => (
                    <MenuItem key={store.id} value={store.id}>{store.merchantName} - {store.shopDomain}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              {selectedStore ? (
                <Stack spacing={1}>
                  <StatusChip status={selectedStore.assignmentStatus} />
                  <Typography color="text.secondary">{selectedStore.topBlocker}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip size="small" label={selectedStore.plan} />
                    <Chip size="small" label={titleize(selectedStore.readinessStatus)} variant="outlined" />
                    <Chip size="small" label={`${selectedStore.permissions.length} permissions`} variant="outlined" />
                  </Stack>
                </Stack>
              ) : null}
            </Stack>
          </Paper>
        </Grid>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={4}>
                <Metric label="Captured sessions" value={String(health.total)} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Metric label="Needs escalation" value={String(health.escalated)} />
              </Grid>
              <Grid item xs={12} sm={4}>
                <Metric label="Blocked" value={String(health.blocked)} />
              </Grid>
            </Grid>
            <Alert severity="info" sx={{ mt: 2 }}>
              Partner access is read-scoped to assigned stores. Operator raw references, provider credentials, and cross-tenant identifiers are not exposed.
            </Alert>
          </Paper>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={4}>
          <Paper sx={{ p: 2 }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ManageSearchOutlinedIcon color="primary" />
                <Typography variant="h2">Session queue</Typography>
              </Stack>
              {sessionsQuery.isLoading ? <LinearProgress /> : null}
              {sessions.length === 0 ? (
                <Alert severity="info">No Thinker sessions have been captured for this store.</Alert>
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
                          <StatusChip status={session.status} />
                          <Typography variant="caption" color="text.secondary">{formatDateTime(session.startedAt)}</Typography>
                        </Stack>
                        <Typography fontWeight={700}>{titleize(session.issueCategory)}</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                          {session.userQuestion}
                        </Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip size="small" label={`${session.evidenceCount} evidence`} />
                          <Chip size="small" label={session.riskClass} variant="outlined" />
                        </Stack>
                      </Stack>
                    </Paper>
                  ))}
                </Stack>
              )}
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Paper sx={{ p: 2 }}>
            {detailQuery.isLoading ? <LinearProgress /> : null}
            {!selectedSession ? (
              <Alert severity="info">Select a session to review diagnosis and create a governed support handoff.</Alert>
            ) : (
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} justifyContent="space-between">
                  <Box>
                    <Typography variant="h2">{titleize(selectedSession.session.issueCategory)}</Typography>
                    <Typography color="text.secondary">{selectedSession.session.userQuestion}</Typography>
                  </Box>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <StatusChip status={selectedSession.session.status} />
                    <Chip label={selectedSession.session.riskClass} variant="outlined" />
                  </Stack>
                </Stack>
                {selectedSession.session.userSafeAnswer ? (
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="caption" color="text.secondary">Shopper-safe answer</Typography>
                    <Typography sx={{ mt: 0.5 }}>{selectedSession.session.userSafeAnswer}</Typography>
                  </Paper>
                ) : null}
                {selectedSession.plan ? (
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="caption" color="text.secondary">Recommended next step</Typography>
                    <Typography sx={{ mt: 0.5 }}>{selectedSession.plan.recommendedNextStep}</Typography>
                    <Divider sx={{ my: 1.5 }} />
                    <Typography variant="body2" color="text.secondary">{selectedSession.plan.userExplanation}</Typography>
                  </Paper>
                ) : null}
                <Grid container spacing={1.5}>
                  <Grid item xs={12} md={6}>
                    <Typography fontWeight={700} sx={{ mb: 1 }}>Evidence</Typography>
                    <Stack spacing={1}>
                      {selectedSession.evidence.map((item) => (
                        <Paper key={item.id} variant="outlined" sx={{ p: 1.5 }}>
                          <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 0.75 }}>
                            <Chip size="small" label={item.kind} />
                            <Chip size="small" label={item.freshness} variant="outlined" />
                          </Stack>
                          <Typography variant="body2">{item.safeSummary}</Typography>
                        </Paper>
                      ))}
                    </Stack>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Typography fontWeight={700} sx={{ mb: 1 }}>Resolver preview</Typography>
                    {selectedSession.resolverProposals.length === 0 ? (
                      <Alert severity="info">No Resolver proposal is attached yet.</Alert>
                    ) : (
                      <Stack spacing={1}>
                        {selectedSession.resolverProposals.map((proposal) => (
                          <Paper key={proposal.id} variant="outlined" sx={{ p: 1.5 }}>
                            <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 0.75 }}>
                              <StatusChip status={proposal.status} />
                              <Chip size="small" label={proposal.actionFamily} variant="outlined" />
                            </Stack>
                            <Typography variant="body2">{proposal.proposalText}</Typography>
                            {proposal.latestPolicyDecision ? (
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                                {proposal.latestPolicyDecision.userReason}
                              </Typography>
                            ) : null}
                          </Paper>
                        ))}
                      </Stack>
                    )}
                  </Grid>
                </Grid>
                <Divider />
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <ReportProblemOutlinedIcon color="warning" />
                    <Typography variant="h2">Create support handoff</Typography>
                  </Stack>
                  <Grid container spacing={1.5}>
                    <Grid item xs={12} md={7}>
                      <TextField fullWidth size="small" label="Title" value={title} onChange={(event) => setTitle(event.target.value)} />
                    </Grid>
                    <Grid item xs={12} md={5}>
                      <TextField fullWidth select size="small" label="Severity" value={severity} onChange={(event) => setSeverity(event.target.value)}>
                        {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((value) => <MenuItem key={value} value={value}>{titleize(value)}</MenuItem>)}
                      </TextField>
                    </Grid>
                    <Grid item xs={12}>
                      <TextField fullWidth size="small" label="Next action" value={nextAction} onChange={(event) => setNextAction(event.target.value)} multiline minRows={2} />
                    </Grid>
                  </Grid>
                  <Button
                    variant="contained"
                    startIcon={<ReportProblemOutlinedIcon />}
                    disabled={title.trim().length < 3 || escalationMutation.isPending}
                    onClick={() => escalationMutation.mutate()}
                  >
                    Create escalation
                  </Button>
                </Stack>
              </Stack>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Stack>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h2">{value}</Typography>
    </Stack>
  )
}
