import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode } from 'react'
import {
  dispatchPlatformVerificationSuiteRun,
  fetchShopifyReadinessAuditState,
  type ShopifyReadinessAuditAnswerResultSummary,
  type PlatformVerificationSuiteRunSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

function statusColor(status: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch ((status ?? '').toUpperCase()) {
    case 'PASSED':
    case 'READY':
    case 'DESIGN_PARTNER_READY':
    case 'TECHNICAL_READY':
      return 'success'
    case 'RUNNING':
    case 'QUEUED':
    case 'PARTIAL':
    case 'STALE':
      return 'warning'
    case 'FAILED':
    case 'TIMED_OUT':
    case 'BLOCKED':
    case 'NOT_READY':
      return 'error'
    default:
      return 'default'
  }
}

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : 'not run yet'
}

function activeRun(run: PlatformVerificationSuiteRunSummary | null): boolean {
  return ['QUEUED', 'RUNNING'].includes((run?.status ?? '').toUpperCase())
}

function failedChecks(result: ShopifyReadinessAuditAnswerResultSummary): string {
  const failures = Object.entries(result.checks ?? {})
    .filter(([, passed]) => !passed)
    .map(([name]) => name)
  return failures.length ? failures.join(', ') : 'none'
}

export function ShopifyReadinessAuditPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const stateQuery = useQuery({
    queryKey: ['shopify-readiness-audit'],
    queryFn: fetchShopifyReadinessAuditState,
    refetchInterval: (query) => activeRun(query.state.data?.latestRun ?? null) ? 3000 : false,
  })
  const dispatchMutation = useMutation({
    mutationFn: () => {
      const suiteKey = stateQuery.data?.definition.suiteKey
      if (!suiteKey) {
        throw new Error('Shopify readiness audit definition is not loaded yet.')
      }
      return dispatchPlatformVerificationSuiteRun(suiteKey, { allowControlPlaneRepair: false })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['shopify-readiness-audit'] })
      await queryClient.invalidateQueries({ queryKey: ['verification-suites', 'runs'] })
      await queryClient.invalidateQueries({ queryKey: ['verification-suites', 'release-gate'] })
    },
  })

  const state = stateQuery.data
  const definition = state?.definition
  const latestRun = state?.latestRun ?? null
  const stage = state?.latestStage ?? null
  const decision = state?.decision ?? 'NOT_READY'
  const queryResults = state?.answerResults ?? []
  const checklistResults = state?.checklistResults ?? []
  const canDispatch = auth.session?.enabled ? auth.session.role === 'PLATFORM_ADMIN' : true

  if (stateQuery.isLoading) {
    return (
      <Stack spacing={3}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>Shopify readiness audit</Typography>
        <LinearProgress />
      </Stack>
    )
  }

  if (stateQuery.isError || !state || !definition) {
    return (
      <Alert severity="error">
        {stateQuery.error instanceof Error ? stateQuery.error.message : 'Failed to load Shopify readiness audit.'}
      </Alert>
    )
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="First Product Gate" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Shopify Companion readiness audit
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 1120 }}>
          Operator-owned readiness review for product truth, live verification, answer quality, evidence, and the final design-partner decision.
        </Typography>
      </Box>

      {!canDispatch ? (
        <Alert severity="info">Platform operator access can review the audit. Running the suite requires PLATFORM_ADMIN.</Alert>
      ) : null}
      {dispatchMutation.isError ? (
        <Alert severity="error">
          {dispatchMutation.error instanceof Error ? dispatchMutation.error.message : 'Failed to start readiness audit.'}
        </Alert>
      ) : null}
      {state.freshnessStatus === 'STALE' ? (
        <Alert severity="warning">The latest readiness evidence is stale. Run the audit again before using this as release or design-partner proof.</Alert>
      ) : null}
      {state.blockers.length > 0 ? (
        <Alert severity={decision === 'DESIGN_PARTNER_READY' || decision === 'TECHNICAL_READY' ? 'warning' : 'error'}>
          {state.blockers.join(' ')}
        </Alert>
      ) : null}

      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Decision" value={decision} color={statusColor(decision)} detail={state.nextHandoff} />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Latest run" value={latestRun?.status ?? 'NOT_RUN'} color={statusColor(latestRun?.status)} detail={formatTimestamp(state.evidenceCompletedAt)} />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Evidence" value={state.freshnessStatus} color={statusColor(state.freshnessStatus)} detail={`${state.evidenceArtifacts.length} artifact references`} />
        </Grid>
        <Grid item xs={12} md={3}>
          <SummaryCard title="Answer pack" value={`${definition.queryPack.length} queries`} detail={`${queryResults.filter((result) => result.passed).length}/${queryResults.length || definition.queryPack.length} latest passed`} />
        </Grid>
      </Grid>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'center' }}>
            <Box>
              <Typography variant="h6">Audit runner</Typography>
              <Typography variant="body2" color="text.secondary">
                Suite <code>{definition.suiteKey}</code> targets <code>{definition.targetStore}</code>, writes a compact evidence packet under <code>{definition.artifactRoot}</code>, and stores answer results on the control plane.
              </Typography>
            </Box>
            <Button
              variant="contained"
              startIcon={<PlayArrowRoundedIcon />}
              disabled={!canDispatch || dispatchMutation.isPending || activeRun(latestRun)}
              onClick={() => dispatchMutation.mutate()}
            >
              {activeRun(latestRun) ? 'Audit running…' : 'Run readiness audit'}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={6}>
          <SectionCard icon={<FactCheckRoundedIcon />} title="Checklist">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Gate</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Blocking</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(checklistResults.length ? checklistResults : definition.checklist.map((item) => ({ ...item, status: 'NOT_RUN', evidence: definition.artifactRoot, notes: 'Awaiting first audit run.' }))).map((item) => (
                  <TableRow key={item.key}>
                    <TableCell>
                      <Typography variant="subtitle2">{item.label}</Typography>
                      <Typography variant="caption" color="text.secondary">{item.passCriteria}</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>{item.evidence}</Typography>
                    </TableCell>
                    <TableCell>{item.category}</TableCell>
                    <TableCell>
                      <Stack spacing={0.75} alignItems="flex-start">
                        <Chip size="small" label={item.status} color={statusColor(item.status)} variant="outlined" />
                        <Chip size="small" label={item.blocking ? 'Blocking' : 'Advisory'} color={item.blocking ? 'warning' : 'default'} variant="outlined" />
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </SectionCard>
        </Grid>
        <Grid item xs={12} lg={6}>
          <SectionCard icon={<AssessmentOutlinedIcon />} title="Query pack">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Query</TableCell>
                  <TableCell>Tier</TableCell>
                  <TableCell>Surface</TableCell>
                  <TableCell>Expected</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {definition.queryPack.map((query) => (
                  <TableRow key={query.queryId}>
                    <TableCell>
                      <Typography variant="subtitle2">{query.queryId}</Typography>
                      <Typography variant="caption" color="text.secondary">{query.query}</Typography>
                    </TableCell>
                    <TableCell>{query.tierProfile}</TableCell>
                    <TableCell>{query.surface}</TableCell>
                    <TableCell>
                      <Typography variant="body2">{query.expectedBehavior}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        Required: {query.requiredConcepts.join(', ') || 'none'} · Forbidden: {query.forbiddenClaims.join(', ') || 'global defaults'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </SectionCard>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={7}>
          <SectionCard icon={<ReportProblemOutlinedIcon />} title="Answer results">
            {queryResults.length === 0 ? (
              <Alert severity="info">No answer-quality result lines are recorded yet. Run the readiness audit to populate this section.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Query</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>HTTP</TableCell>
                    <TableCell>Failure</TableCell>
                    <TableCell>Checks</TableCell>
                    <TableCell>Answer preview</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {queryResults.map((result) => (
                    <TableRow key={result.queryId}>
                      <TableCell>{result.queryId}</TableCell>
                      <TableCell><Chip size="small" label={result.passed ? 'PASS' : 'FAIL'} color={result.passed ? 'success' : 'error'} variant="outlined" /></TableCell>
                      <TableCell>{result.httpStatus}</TableCell>
                      <TableCell>{result.failureCategory ?? 'none'}</TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">{failedChecks(result)}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">
                          {result.answerPreview || `${result.answerLength} chars`}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </SectionCard>
        </Grid>
        <Grid item xs={12} lg={5}>
          <SectionCard icon={<AssessmentOutlinedIcon />} title="Evidence and decision">
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {definition.decisionOptions.map((option) => (
                  <Chip key={option} label={option} color={option === decision ? statusColor(option) : 'default'} variant={option === decision ? 'filled' : 'outlined'} />
                ))}
              </Stack>
              <Divider />
              <Typography variant="subtitle2">Evidence packet</Typography>
              {state.evidenceArtifacts.map((artifact) => (
                <Box key={artifact.name}>
                  <Typography variant="body2"><code>{artifact.path}</code></Typography>
                  <Typography variant="caption" color="text.secondary">{artifact.category} · {artifact.description}</Typography>
                </Box>
              ))}
              {stage?.summaryMessage ? (
                <>
                  <Divider />
                  <Typography variant="subtitle2">Latest stage summary</Typography>
                  <Typography variant="body2" color="text.secondary">{stage.summaryMessage}</Typography>
                </>
              ) : null}
            </Stack>
          </SectionCard>
        </Grid>
      </Grid>
    </Stack>
  )
}

function SummaryCard({ title, value, detail, color = 'default' }: {
  title: string
  value: string
  detail: string
  color?: 'success' | 'warning' | 'error' | 'default'
}) {
  return (
    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
      <CardContent>
        <Stack spacing={1}>
          <Typography variant="caption" color="text.secondary">{title}</Typography>
          <Chip label={value} color={color} variant="outlined" sx={{ alignSelf: 'flex-start', fontWeight: 700 }} />
          <Typography variant="body2" color="text.secondary">{detail}</Typography>
        </Stack>
      </CardContent>
    </Card>
  )
}

function SectionCard({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            {icon}
            <Typography variant="h6">{title}</Typography>
          </Stack>
          {children}
        </Stack>
      </CardContent>
    </Card>
  )
}
