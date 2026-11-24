import { useEffect, useRef } from 'react'
import { Alert, Box, IconButton, LinearProgress, Stack, Tooltip, Typography } from '@mui/material'
import PauseOutlined from '@mui/icons-material/PauseOutlined'
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined'
import StopOutlined from '@mui/icons-material/StopOutlined'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import { assistantPage } from './pagePresentation'
import { GoalActivity } from './GoalActivity'
import { goalCoverage, goalNextAction, goalPauseMessage, goalRunning, goalStatus, savedGoalOperations } from './goalPresentation'
import type { AssistantGoal, AssistantGoalCommand, AssistantSession } from './types'

function LatestRequestUpdate({ update }: { update: NonNullable<AssistantGoal['latestRequestUpdate']> }) {
  const entry = useRef<HTMLElement>(null)
  useEffect(() => { entry.current?.scrollIntoView({ block: 'nearest' }) }, [update.requestRevision])
  return <Box ref={entry} component="section" aria-label="Latest goal update" sx={{ mb: .75, minWidth: 0 }}>
    <Typography variant="caption" fontWeight={600} display="block">Your latest update &middot; Revision {update.requestRevision}</Typography>
    <Typography variant="body2" sx={{ fontSize: 13, whiteSpace: 'pre-wrap', maxHeight: 80, overflowY: 'auto' }}>{update.query}</Typography>
  </Box>
}

export function GoalProgress({ session, pending, onControl, compact = false }: { session: AssistantSession; pending: boolean; onControl: (command: AssistantGoalCommand) => void; compact?: boolean }) {
  const goal = session.goal
  if (!goal) return null
  const undone = session.turns.find(turn => turn.id === goal.turnId)?.status === 'UNDONE'
  const coverage = undone ? null : goalCoverage(goal), saved = savedGoalOperations(session), pause = goalPauseMessage(goal)
  const status = undone ? 'Draft changes undone' : goalStatus(goal)
  const nextAction = goalNextAction(goal)
  const savedCount = Math.max(goal.operationCount ?? 0, saved.length)
  const remaining = goal.progress?.remaining ?? []
  const items = goal.progress?.workItems ?? []
  return <Box component="section" aria-label="Autonomous goal" data-goal-state={goal.state} sx={{ borderBlock: 1, borderColor: 'divider', py: 1, flexShrink: 0, minWidth: 0 }}>
    <Stack direction="row" alignItems="center" gap={.5}>
      <Typography variant="caption" fontWeight={700} sx={{ flex: 1, minWidth: 0 }}>Autonomous · Draft only</Typography>
      {goal.canPause && <Tooltip title="Pause goal"><IconButton size="small" aria-label="Pause goal" disabled={pending} onClick={() => onControl('PAUSE')}><PauseOutlined fontSize="small" /></IconButton></Tooltip>}
      {goal.canResume && <Tooltip title="Resume goal"><IconButton size="small" aria-label="Resume goal" disabled={pending} onClick={() => onControl('RESUME')}><PlayArrowOutlined fontSize="small" /></IconButton></Tooltip>}
      {goal.canCancel && <Tooltip title="Cancel goal"><IconButton size="small" aria-label="Cancel goal" disabled={pending} onClick={() => onControl('CANCEL')}><StopOutlined fontSize="small" /></IconButton></Tooltip>}
    </Stack>
    <Typography variant="caption" display="block" data-goal-target sx={{ overflowWrap: 'anywhere' }}>
      Original target: {session.displayName} · {goal.resourceId} · {assistantPage({ resourceKind: goal.resourceKind, resourceId: goal.resourceId, task: goal.task }).title}
    </Typography>
    <Typography variant="caption" color="text.secondary" display="block" sx={{ overflowWrap: 'anywhere' }}>
      Pinned profile: {goal.profileName ?? goal.profileId ?? 'Unavailable'} · Release: {goal.releaseId ?? 'Unavailable'}
    </Typography>
    {compact ? <Typography variant="caption" display="block">{status}</Typography> : <Box sx={{ maxHeight: '24dvh', overflowY: 'auto', mt: .75, minWidth: 0 }}>
      {goal.latestRequestUpdate && <><LatestRequestUpdate update={goal.latestRequestUpdate} /><Typography variant="caption" color="text.secondary" display="block">Original request</Typography></>}
      <Typography variant="body2" sx={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{goal.originalRequest}</Typography>
      {goal.sourceIds && <Box component="details" sx={{ mt: .5, fontSize: 12 }}>
        <summary>Shared references ({goal.sourceIds.length})</summary>
        {goal.sourceIds.map(id => <Typography key={id} variant="caption" display="block">{session.sources.find(source => source.id === id)?.title ?? id}</Typography>)}
        <Typography variant="caption" display="block">{goal.operationIds?.length ? `${goal.operationIds.length} selected operations: ${goal.operationIds.join(', ')}` : 'All operations in the selected references'}</Typography>
      </Box>}
      <Typography role="status" variant="caption" fontWeight={600} display="block" sx={{ mt: .75 }}>{status}{coverage ? ` · ${coverage.completed} of ${coverage.total} covered` : ''}</Typography>
      {coverage && coverage.total > 0 && <LinearProgress aria-label="Requested operation coverage" variant="determinate" value={100 * coverage.completed / coverage.total} sx={{ my: .5 }} />}
      {!coverage && goalRunning(goal) && <LinearProgress aria-label="Goal preparing" sx={{ my: .5 }} />}
      {!undone && goal.progress?.summary && <Typography variant="caption" display="block">{goal.progress.summary}</Typography>}
      {(pause || goal.reasonMessage) && <Alert severity={goal.state === 'WAITING_FOR_INPUT' ? 'info' : 'warning'} sx={{ mt: .75, py: 0, '& .MuiAlert-message': { minWidth: 0 } }}>
        {goal.reasonMessage ?? pause}
        {goal.reasonCode && <Typography variant="caption" display="block">{goal.reasonCode}</Typography>}
      </Alert>}
      {nextAction && <Typography variant="caption" display="block" sx={{ mt: .5 }}>{nextAction}</Typography>}
      {goal.nextEligibleAt && <Typography variant="caption" display="block">Next eligible run: {new Date(goal.nextEligibleAt).toLocaleString()}</Typography>}
      <Stack direction="row" alignItems="center" gap={.5} sx={{ mt: .75 }}>
        <CheckCircleOutlineRounded sx={{ fontSize: 15 }} color={savedCount ? 'success' : 'disabled'} />
        <Typography variant="caption">{savedCount} executed draft {savedCount === 1 ? 'operation' : 'operations'} · Not published</Typography>
      </Stack>
      {!undone && remaining.length > 0 && <Box component="section" aria-label="Remaining work" sx={{ mt: .75 }}>
        <Typography variant="caption" fontWeight={600}>Remaining work</Typography>
        <Box component="ul" sx={{ pl: 2, my: .25 }}>{remaining.map((item, index) => <Typography component="li" variant="caption" key={`${index}:${item}`}>{item}</Typography>)}</Box>
      </Box>}
      {(items.length > 0 || savedCount > 0 || goal.usage || goal.providerDiagnostics) && <GoalActivity key={`${session.id}:${goal.id}:${goal.cancellationEpoch ?? 0}`} sessionId={session.id} goal={goal} initialOperations={saved} />}
    </Box>}
  </Box>
}
