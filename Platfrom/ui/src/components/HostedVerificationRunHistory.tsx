import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useMemo, useState } from 'react'
import {
  type DeploymentHostedVerificationRunSummary,
  type DeploymentHostedVerificationStepSummary,
} from '../api/platformApi'

type HostedVerificationRunHistoryProps = {
  runs: DeploymentHostedVerificationRunSummary[]
  showDeploymentId?: boolean
}

type HostedVerificationProfileInfo = {
  label: string
  scope: string[]
}

function formatOptionalTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'PASSED' || status === 'READY' || status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'RUNNING' || status === 'QUEUED' || status === 'DEPLOYING' || status === 'INITIALIZING') {
    return 'info'
  }
  if (status === 'WARNING' || status === 'AWAITING_CONFIRMATION' || status === 'TIMED_OUT') {
    return 'warning'
  }
  if (status === 'FAILED' || status === 'ERROR' || status === 'REMOVED') {
    return 'error'
  }
  return 'default'
}

function profileInfo(profile: string): HostedVerificationProfileInfo {
  if (profile === 'ecommerce') {
    return {
      label: 'Ecommerce deployment',
      scope: [
        'Store, REST connector, and runtime health',
        'Store admin endpoint presence and safe action-routing smoke checks',
        'Runtime proxy, indexing overview, and runtime action catalog',
        'Platform health, overview, preflight, workspace, and source-of-truth',
        'Service navigation, readiness, remediation, releases, and verification evidence',
        'Prompt artifact fetch plus runtime prompt-config alignment',
      ],
    }
  }
  return {
    label: 'Vector deployment',
    scope: [
      'REST connector and runtime health',
      'Connector and runtime admin endpoints',
      'Vector spaces and runtime indexing overview',
      'Platform workspace, releases, and verification evidence',
      'Platform artifact fetch and runtime artifact alignment',
      'Optional vector upsert/delete roundtrip when write mode is enabled',
    ],
  }
}

function groupStepsBySection(steps: DeploymentHostedVerificationStepSummary[]): Array<{
  section: string
  steps: DeploymentHostedVerificationStepSummary[]
}> {
  const ordered = new Map<string, DeploymentHostedVerificationStepSummary[]>()
  steps.forEach((step) => {
    const key = step.section?.trim() || 'General'
    const existing = ordered.get(key)
    if (existing) {
      existing.push(step)
      return
    }
    ordered.set(key, [step])
  })
  return Array.from(ordered.entries()).map(([section, sectionSteps]) => ({
    section,
    steps: sectionSteps,
  }))
}

export function HostedVerificationRunHistory({
  runs,
  showDeploymentId = false,
}: HostedVerificationRunHistoryProps) {
  const [expandedRunId, setExpandedRunId] = useState<string | false>(runs[0]?.id ?? false)

  const groupedStepsByRun = useMemo(
    () => new Map(runs.map((run) => [run.id, groupStepsBySection(run.diagnostics.steps)])),
    [runs],
  )

  if (runs.length === 0) {
    return (
      <Alert severity="info">
        No platform-hosted verification runs have been recorded yet.
      </Alert>
    )
  }

  return (
    <Stack spacing={1.5}>
      {runs.map((run) => {
        const profile = profileInfo(run.verificationProfile)
        const groupedSteps = groupedStepsByRun.get(run.id) ?? []
        return (
          <Accordion
            key={run.id}
            expanded={expandedRunId === run.id}
            onChange={(_, isExpanded) => setExpandedRunId(isExpanded ? run.id : false)}
            disableGutters
            elevation={0}
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              '&:before': { display: 'none' },
            }}
          >
            <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
              <Stack spacing={1.25} sx={{ width: '100%' }}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.25} justifyContent="space-between">
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    <Typography variant="subtitle2" sx={{ fontFamily: 'monospace' }}>
                      {run.id}
                    </Typography>
                    <Chip size="small" label={run.status} color={statusColor(run.status)} variant="outlined" />
                    <Chip size="small" label={profile.label} variant="outlined" />
                    <Chip size="small" label={`${run.diagnostics.passCount} pass`} color="success" variant="outlined" />
                    {run.diagnostics.warningCount > 0 ? (
                      <Chip size="small" label={`${run.diagnostics.warningCount} warning`} color="warning" variant="outlined" />
                    ) : null}
                    {run.diagnostics.failCount > 0 ? (
                      <Chip size="small" label={`${run.diagnostics.failCount} fail`} color="error" variant="outlined" />
                    ) : null}
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Completed {formatOptionalTimestamp(run.completedAt)}
                  </Typography>
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {run.summaryMessage}
                </Typography>
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Stack spacing={2}>
                <Alert severity={run.status === 'FAILED' ? 'error' : run.status === 'TIMED_OUT' ? 'warning' : 'info'}>
                  {run.diagnostics.headline}
                </Alert>

                <Table size="small">
                  <TableBody>
                    {showDeploymentId ? (
                      <TableRow>
                        <TableCell>Deployment</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{run.deploymentId}</TableCell>
                        <TableCell>Release</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{run.releaseId}</TableCell>
                      </TableRow>
                    ) : null}
                    <TableRow>
                      <TableCell>Version</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace' }}>{run.deploymentVersionId}</TableCell>
                      <TableCell>Queued</TableCell>
                      <TableCell>{formatOptionalTimestamp(run.startedAt ?? run.createdAt)}</TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell>Runner</TableCell>
                      <TableCell>{run.runnerType}</TableCell>
                      <TableCell>Read/write mode</TableCell>
                      <TableCell>{run.verifyWrite ? 'Write-enabled' : 'Read-only'}</TableCell>
                    </TableRow>
                  </TableBody>
                </Table>

                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    Profile coverage
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {profile.scope.map((item) => (
                      <Chip key={item} size="small" label={item} variant="outlined" />
                    ))}
                  </Stack>
                </Box>

                {groupedSteps.length > 0 ? (
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle2">Verified items</Typography>
                    {groupedSteps.map((group) => (
                      <Box key={`${run.id}-${group.section}`}>
                        <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.75 }}>
                          {group.section}
                        </Typography>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Status</TableCell>
                              <TableCell>Checkpoint</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {group.steps.map((step) => (
                              <TableRow key={`${group.section}-${step.rawLine}`} hover>
                                <TableCell sx={{ width: 140 }}>
                                  <Chip size="small" label={step.status} color={statusColor(step.status)} variant="outlined" />
                                </TableCell>
                                <TableCell>{step.message}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </Box>
                    ))}
                  </Stack>
                ) : null}

                {run.logOutput ? (
                  <Box>
                    <Typography variant="subtitle2" sx={{ mb: 1 }}>
                      Raw output
                    </Typography>
                    <Box
                      component="pre"
                      sx={{
                        m: 0,
                        p: 1.5,
                        maxHeight: 280,
                        overflow: 'auto',
                        bgcolor: '#0f172a',
                        color: '#e2e8f0',
                        border: '1px solid',
                        borderColor: '#1e293b',
                        borderRadius: 1,
                        fontSize: 12,
                        whiteSpace: 'pre-wrap',
                      }}
                    >
                      {run.logOutput}
                    </Box>
                  </Box>
                ) : null}
              </Stack>
            </AccordionDetails>
          </Accordion>
        )
      })}
    </Stack>
  )
}
