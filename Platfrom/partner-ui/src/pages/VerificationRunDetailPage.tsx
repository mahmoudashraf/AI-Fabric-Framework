import { Alert, Box, Button, LinearProgress, Paper, Stack, Typography } from '@mui/material'
import FolderZipOutlinedIcon from '@mui/icons-material/FolderZipOutlined'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { getVerificationRun } from '../api/verification'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime } from '../utils/format'

export function VerificationRunDetailPage() {
  const { runId = '' } = useParams()
  const { api } = useSupabaseAuth()
  const navigate = useNavigate()
  const runQuery = useQuery({ queryKey: ['verification-run', runId], queryFn: () => getVerificationRun(api, runId), enabled: Boolean(runId) })

  if (runQuery.isLoading) return <LinearProgress />
  if (runQuery.isError || !runQuery.data) return <Alert severity="error">Verification run is not available to this workspace.</Alert>
  const run = runQuery.data

  return (
    <>
      <PageHeader
        title={run.packName}
        subtitle={run.shopDomain ?? run.storeAssignmentId}
        breadcrumbs={[{ label: 'Verification', to: '/verification' }, { label: run.packName }]}
        actions={
          <Stack direction="row" spacing={1}>
            {run.evidenceBundleId ? <Button startIcon={<FolderZipOutlinedIcon />} onClick={() => navigate(`/evidence/${run.evidenceBundleId}`)}>Open evidence</Button> : null}
            <Button startIcon={<ReportProblemOutlinedIcon />} onClick={() => navigate('/support')}>Open escalation</Button>
          </Stack>
        }
      />
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
          <Stack spacing={1}>
            <StatusChip status={run.status} />
            <Typography color="text.secondary">Started {formatDateTime(run.startedAt)}</Typography>
          </Stack>
          <Stack direction="row" spacing={3}>
            <Metric label="Passed" value={run.passedSteps} />
            <Metric label="Failed" value={run.failedSteps} />
            <Metric label="Blocked" value={run.blockedSteps} />
          </Stack>
        </Stack>
      </Paper>
      <Stack spacing={1.5}>
        {run.steps.map((step) => (
          <Paper key={step.stepId} sx={{ p: 2 }}>
            <Stack spacing={1}>
              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
                <Typography variant="h3">{step.label}</Typography>
                <StatusChip status={step.status} />
              </Stack>
              <Typography>{step.partnerSafeMessage}</Typography>
              {step.suggestedFix ? <Typography color="text.secondary">{step.suggestedFix}</Typography> : null}
              <Box>
                <Typography variant="caption" color="text.secondary">Evidence</Typography>
                <Stack component="ul" sx={{ pl: 3, mt: 0.5 }}>
                  {step.evidence.map((item) => <Typography component="li" key={item}>{item}</Typography>)}
                </Stack>
              </Box>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </>
  )
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h2">{value}</Typography>
    </Box>
  )
}
