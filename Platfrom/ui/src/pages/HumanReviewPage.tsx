import { deploymentTerm } from '../deploymentTerminology'
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import TaskAltRoundedIcon from '@mui/icons-material/TaskAltRounded'
import CancelRoundedIcon from '@mui/icons-material/CancelRounded'
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, IconButton, List, ListItemButton, ListItemText, MenuItem,
  Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  decideDeploymentHumanReview, fetchDeploymentHumanReviewDetail, fetchDeploymentHumanReviewInbox,
  PlatformApiError, type DeploymentHumanReviewDecisionResult, type DeploymentHumanReviewTask,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

type ReviewDecision = 'APPROVE' | 'REJECT'
type ReviewScope = { deploymentId: string; targetProfileId: string; releaseId: string; reviewer: string }
type PendingReviewDecision = ReviewScope & { task: DeploymentHumanReviewTask; decision: ReviewDecision; decisionId: string }
type Props = {
  title?: string
  targetProfileId?: string
  deploymentId?: string
  releaseId?: string
  workspaceView?: boolean
  onBusyChange?: (busy: boolean) => void
}

function timestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (status) {
    case 'APPROVED':
    case 'COMPLETED': return 'success'
    case 'PENDING':
    case 'WAITING_FOR_REVIEW':
    case 'WAITING_FOR_INFORMATION':
    case 'DECIDING':
    case 'IN_REVIEW': return 'warning'
    case 'REJECTED':
    case 'EXPIRED':
    case 'FAILED': return 'error'
    default: return 'default'
  }
}

function selectReviewTask(tasks: DeploymentHumanReviewTask[], taskId: string, autoSelect: boolean) {
  return taskId ? tasks.find(task => task.taskId === taskId) ?? null : autoSelect ? tasks[0] ?? null : null
}

function filterReviewTasks(tasks: DeploymentHumanReviewTask[], status: string, search: string) {
  const term = search.trim().toLowerCase()
  return tasks.filter(task => (!status || task.status === status)
    && [task.taskId, task.title, task.summary, task.type, task.status, task.policyId.name, task.policyId.version]
      .join(' ').toLowerCase().includes(term))
}

function currentReviewTask(taskId: string, inboxTask: DeploymentHumanReviewTask | null, detailTask: DeploymentHumanReviewTask | undefined) {
  return taskId && detailTask?.taskId === taskId && (!inboxTask || detailTask.version >= inboxTask.version) ? detailTask : null
}

function isPendingReviewTask(task: DeploymentHumanReviewTask) {
  return task.status === 'PENDING' || task.status === 'WAITING_FOR_REVIEW'
}

function canDecideReviewTask(task: DeploymentHumanReviewTask, decision: ReviewDecision, now = Date.now()) {
  return isPendingReviewTask(task) && task.allowedDecisions.includes(decision)
    && !(Date.parse(task.expiresAt) <= now)
}

function freezeReviewDecision(task: DeploymentHumanReviewTask, decision: ReviewDecision, scope: ReviewScope, decisionId: string): PendingReviewDecision {
  return { ...scope, task: { ...task, policyId: { ...task.policyId }, allowedDecisions: [...task.allowedDecisions] }, decision, decisionId }
}

function canConfirmReviewDecision(pending: PendingReviewDecision, scope: ReviewScope, task: DeploymentHumanReviewTask | null) {
  // This binds the side effect to the confirmed customer, environment and optimistic task version.
  return Boolean(scope.reviewer && pending.reviewer === scope.reviewer
    && pending.deploymentId === scope.deploymentId && pending.targetProfileId === scope.targetProfileId
    && pending.releaseId === scope.releaseId && task?.taskId === pending.task.taskId
    && task.version === pending.task.version && canDecideReviewTask(pending.task, pending.decision)
    && canDecideReviewTask(task, pending.decision))
}

export function HumanReviewPage({ title = 'Human Review', targetProfileId = '', deploymentId, releaseId = '',
  workspaceView = false, onBusyChange }: Props = {}) {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const workspace = useDeploymentWorkspace()
  const selectedDeploymentId = deploymentId ?? workspace.selectedDeploymentId
  const location = useLocation()
  const navigate = useNavigate()
  const [selectedTaskId, setSelectedTaskId] = useState('')
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [pendingDecision, setPendingDecision] = useState<PendingReviewDecision | null>(null)
  const [receipt, setReceipt] = useState<{ input: PendingReviewDecision; result: DeploymentHumanReviewDecisionResult } | null>(null)
  const session = auth.session
  const customerReviewer = isCustomerSession(session)
  const reviewer = customerReviewer ? JSON.stringify([session.customerId, session.actorId]) : ''
  const scope = { deploymentId: selectedDeploymentId, targetProfileId, releaseId, reviewer }
  const queryScope = [selectedDeploymentId, targetProfileId, releaseId, reviewer]
  const requestedTaskId = workspaceView ? new URLSearchParams(location.search).get('taskId') ?? '' : selectedTaskId
  const enabled = customerReviewer && selectedDeploymentId.length > 0 && targetProfileId.length > 0

  const inboxQuery = useQuery({
    queryKey: ['deployment-human-reviews', ...queryScope],
    queryFn: () => fetchDeploymentHumanReviewInbox(selectedDeploymentId, targetProfileId),
    enabled, refetchInterval: 15_000,
  })
  const tasks = inboxQuery.data ?? []
  const selectedTask = selectReviewTask(tasks, requestedTaskId, !workspaceView)
  const detailTaskId = requestedTaskId || selectedTask?.taskId || ''
  const visibleTasks = filterReviewTasks(tasks, status, search)
  const detailQuery = useQuery({
    queryKey: ['deployment-human-review-detail', ...queryScope, detailTaskId, selectedTask?.version],
    queryFn: () => fetchDeploymentHumanReviewDetail(selectedDeploymentId, targetProfileId, detailTaskId),
    enabled: enabled && Boolean(detailTaskId), refetchInterval: 15_000, retry: false,
  })
  const detail = detailQuery.data?.task.taskId === detailTaskId ? detailQuery.data : undefined
  const task = currentReviewTask(detailTaskId, selectedTask, detail?.task)
  const decisionMutation = useMutation({
    mutationFn: (input: PendingReviewDecision) => {
      const current = task?.taskId === input.task.taskId ? task : null
      if (!customerReviewer || !canConfirmReviewDecision(input, scope, current)) {
        throw new Error('This review task or customer context changed. Cancel and review the current task before deciding.')
      }
      return decideDeploymentHumanReview(input.deploymentId, input.targetProfileId, input.task.taskId, {
        decision: input.decision, expectedVersion: input.task.version, decisionId: input.decisionId,
      })
    },
    retry: false,
    onSuccess: async (result, input) => {
      setReceipt({ input, result })
      setPendingDecision(null)
      await queryClient.invalidateQueries({ queryKey: ['deployment-human-reviews', input.deploymentId, input.targetProfileId] })
      await queryClient.invalidateQueries({ queryKey: ['deployment-human-review-detail', input.deploymentId, input.targetProfileId] })
    },
    onError: async (_error, input) => {
      await queryClient.invalidateQueries({ queryKey: ['deployment-human-reviews', input.deploymentId, input.targetProfileId] })
      await queryClient.invalidateQueries({ queryKey: ['deployment-human-review-detail', input.deploymentId, input.targetProfileId] })
    },
  })
  const busy = pendingDecision != null || decisionMutation.isPending
  useEffect(() => { onBusyChange?.(busy) }, [onBusyChange, busy])
  useEffect(() => () => onBusyChange?.(false), [onBusyChange])
  useEffect(() => {
    if (pendingDecision && pendingDecision.reviewer !== reviewer && !decisionMutation.isPending) {
      setPendingDecision(null)
      decisionMutation.reset()
    }
  }, [pendingDecision, reviewer, decisionMutation.isPending, decisionMutation.reset])

  function chooseTask(taskId: string) {
    if (busy) return
    decisionMutation.reset()
    if (!workspaceView) { setSelectedTaskId(taskId); return }
    const params = new URLSearchParams(location.search)
    if (taskId) params.set('taskId', taskId)
    else params.delete('taskId')
    navigate({ pathname: location.pathname, search: params.toString(), hash: location.hash })
  }

  function openConfirmation(decision: ReviewDecision) {
    if (!customerReviewer || !task || busy || !canDecideReviewTask(task, decision)) return
    decisionMutation.reset()
    setPendingDecision(freezeReviewDecision(task, decision, scope, `platform-review-${globalThis.crypto.randomUUID()}`))
  }

  function closeConfirmation() {
    if (!decisionMutation.isPending) { setPendingDecision(null); decisionMutation.reset() }
  }

  const confirmationTask = task?.taskId === pendingDecision?.task.taskId ? task : null
  const confirmationValid = Boolean(customerReviewer && pendingDecision
    && canConfirmReviewDecision(pendingDecision, scope, confirmationTask))
  const missingTask = detailQuery.error instanceof PlatformApiError && detailQuery.error.status === 404
    || Boolean(detailTaskId && detailQuery.isSuccess && detailQuery.data.task.taskId !== detailTaskId)
  const currentReceipt = receipt && receipt.input.task.taskId === detailTaskId
    && receipt.input.deploymentId === selectedDeploymentId && receipt.input.targetProfileId === targetProfileId
    && receipt.input.releaseId === releaseId && receipt.input.reviewer === reviewer ? receipt.result : null
  const outcome = detail?.outcome ?? currentReceipt?.outcome
  const failure = detail?.failureReason ?? currentReceipt?.failure?.publicMessage
  const paneSx = { minWidth: 0, minHeight: 0, overflow: 'auto', overscrollBehavior: 'contain' } as const

  if (!customerReviewer) {
    return <Stack spacing={2}>
      {!workspaceView && <Typography variant="h4" sx={{ fontWeight: 800 }}>{title}</Typography>}
      <Alert severity="info">Sign in with a customer administrator session to access this {deploymentTerm.lowercase}'s review inbox.</Alert>
    </Stack>
  }

  return <Stack spacing={workspaceView ? 1.5 : 3} sx={workspaceView ? { flex: 1, minHeight: 0, minWidth: 0, overflow: { md: 'hidden' } } : undefined}>
    {!workspaceView && <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
      <Box><Chip label="Customer authority" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800 }}>{title}</Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>Governed action proposals awaiting an authorized customer decision.</Typography></Box>
      <Button variant="outlined" startIcon={<RefreshRoundedIcon />} disabled={!enabled || inboxQuery.isFetching || busy}
        onClick={() => void inboxQuery.refetch()} sx={{ alignSelf: { xs: 'stretch', md: 'center' } }}>Refresh</Button>
    </Stack>}

    {!selectedDeploymentId && <Alert severity="info">Select a {deploymentTerm.lowercase} first.</Alert>}
    {selectedDeploymentId && !targetProfileId && <Alert severity="info">Select an environment to view its review inbox.</Alert>}
    {inboxQuery.error instanceof Error && <Alert severity="error">{inboxQuery.error.message}</Alert>}
    {decisionMutation.error instanceof Error && !pendingDecision && <Alert severity="error">{decisionMutation.error.message}</Alert>}

    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(240px, 0.38fr) minmax(0, 0.62fr)' },
      gap: workspaceView ? 0 : 2.5, flex: workspaceView ? 1 : undefined, minHeight: 0, minWidth: 0, overflow: { md: workspaceView ? 'hidden' : undefined } }}>
      <Box component="section" aria-label="Review tasks" sx={{ display: { xs: workspaceView && requestedTaskId ? 'none' : 'flex', md: 'flex' },
        flexDirection: 'column', minHeight: 0, minWidth: 0, border: workspaceView ? 0 : 1, borderColor: 'divider',
        borderRadius: workspaceView ? 0 : 1, pr: workspaceView ? { md: 2 } : 0 }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ px: workspaceView ? 0 : 2.5, py: 1.5, flexShrink: 0 }}>
          <FactCheckRoundedIcon color="primary" fontSize="small" /><Typography variant="subtitle1">Review tasks</Typography>
          <Chip size="small" label={tasks.length} />
          {workspaceView && <Box sx={{ ml: 'auto !important' }}><Tooltip title="Refresh tasks"><span><IconButton aria-label="Refresh tasks" size="small"
            disabled={!enabled || inboxQuery.isFetching || busy} onClick={() => void inboxQuery.refetch()}><RefreshRoundedIcon fontSize="small" /></IconButton></span></Tooltip></Box>}
        </Stack>
        {workspaceView && <Stack gap={1} sx={{ pb: 1.5, flexShrink: 0 }}>
          <TextField size="small" label="Search tasks" value={search} onChange={event => setSearch(event.target.value)} />
          <TextField select size="small" label="Status" value={status} onChange={event => setStatus(event.target.value)}>
            <MenuItem value="">All statuses</MenuItem>
            {Array.from(new Set([...tasks.map(item => item.status), ...(status ? [status] : [])])).sort().map(value =>
              <MenuItem key={value} value={value}>{value.replace(/_/g, ' ')}</MenuItem>)}
          </TextField>
        </Stack>}
        <Divider />
        <Box sx={workspaceView ? { ...paneSx, flex: 1 } : undefined}>
          {inboxQuery.isLoading && <Box sx={{ p: 3, textAlign: 'center' }}><CircularProgress size={24} aria-label="Loading review tasks" /></Box>}
          {inboxQuery.isSuccess && !tasks.length && <Box sx={{ p: 3, textAlign: 'center' }}>
            <TaskAltRoundedIcon color="success" /><Typography variant="subtitle1">Inbox clear</Typography>
            <Typography variant="body2" color="text.secondary">No review tasks are visible for this customer and {deploymentTerm.lowercase}.</Typography></Box>}
          {tasks.length > 0 && !visibleTasks.length && <Typography color="text.secondary" sx={{ py: 2 }}>No tasks match these filters.</Typography>}
          <List disablePadding>
            {visibleTasks.map(item => <ListItemButton key={item.taskId} selected={item.taskId === detailTaskId} disabled={busy}
              onClick={() => chooseTask(item.taskId)} sx={{ py: 1.5, alignItems: 'flex-start', borderBottom: 1, borderColor: 'divider' }}>
              <ListItemText primary={item.title} primaryTypographyProps={{ fontSize: workspaceView ? 13 : 16, fontWeight: 600, sx: { overflowWrap: 'anywhere' } }}
                secondaryTypographyProps={{ component: 'div' }} secondary={<Stack spacing={0.75} sx={{ mt: 0.75, minWidth: 0 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', overflowWrap: 'anywhere' }}>{item.summary}</Typography>
                  <Stack direction="row" gap={0.75} flexWrap="wrap" alignItems="center">
                    <Chip size="small" color={statusColor(item.status)} label={item.status.replace(/_/g, ' ')} />
                    <Typography variant="caption" color="text.secondary">Expires {timestamp(item.expiresAt)}</Typography>
                  </Stack></Stack>} />
            </ListItemButton>)}
          </List>
        </Box>
        {workspaceView && <Typography variant="caption" color="text.secondary" sx={{ pt: 1, flexShrink: 0 }}>{visibleTasks.length} of {tasks.length} returned tasks</Typography>}
      </Box>

      <Box component="section" aria-label="Task detail" sx={{ display: { xs: workspaceView && !requestedTaskId ? 'none' : 'flex', md: 'flex' },
        flexDirection: 'column', minHeight: workspaceView ? 0 : 360, minWidth: 0, overflow: workspaceView ? 'hidden' : undefined,
        border: workspaceView ? 0 : 1, borderLeft: 1, borderColor: 'divider', borderRadius: workspaceView ? 0 : 1,
        pl: workspaceView ? { xs: 0, md: 2.5 } : 0 }}>
        {workspaceView && <Button startIcon={<ArrowBackRoundedIcon />} onClick={() => chooseTask('')} disabled={busy}
          sx={{ display: { xs: 'inline-flex', md: 'none' }, alignSelf: 'flex-start', mb: 1, flexShrink: 0 }}>Back to tasks</Button>}
        <Box sx={{ ...(workspaceView ? paneSx : {}), flex: 1, p: workspaceView ? 0 : 3, overflowWrap: 'anywhere' }}>
          {missingTask ? <Alert severity="error">The requested review task is unavailable. No other task was selected.<Typography variant="caption" component="div">Task: {detailTaskId}</Typography></Alert>
            : !detailTaskId ? <Typography color="text.secondary" sx={{ py: 3 }}>Select a task to review.</Typography>
              : detailQuery.isError ? <Alert severity="error">{detailQuery.error.message}</Alert>
                : detail && !task ? <Alert severity="warning">The task changed. Refresh its details before deciding.</Alert>
                  : !task ? <Box sx={{ p: 3, textAlign: 'center' }}><CircularProgress size={24} aria-label="Loading task detail" /></Box>
                    : <Stack spacing={2.5}>
                      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1.5} alignItems="flex-start">
                        <Box sx={{ minWidth: 0 }}><Typography variant="overline" color="text.secondary">{task.type.replace(/_/g, ' ')}</Typography>
                          <Typography variant={workspaceView ? 'h6' : 'h5'} sx={{ fontWeight: 700 }}>{task.title}</Typography></Box>
                        <Chip color={statusColor(task.status)} label={task.status.replace(/_/g, ' ')} sx={{ flexShrink: 0 }} />
                      </Stack>
                      <Typography>{task.summary}</Typography><Divider />
                      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
                        {[['Task', task.taskId], ['Policy', `${task.policyId.name}@${task.policyId.version}`], ['Version', task.version],
                          ['Created', timestamp(task.createdAt)], ['Expires', timestamp(task.expiresAt)]].map(([label, value]) => <Box key={label}>
                          <Typography variant="caption" color="text.secondary">{label}</Typography><Typography variant="body2">{value}</Typography></Box>)}
                      </Box>
                      {detail?.message && <Alert severity="info">{detail.message}</Alert>}
                      {task.status === 'APPROVED' && !outcome && !failure && <Alert severity="info">Approval recorded. Execution outcome is not available.</Alert>}
                    </Stack>}
          <Stack spacing={1.5} sx={{ mt: 2 }}>
            {currentReceipt && <Alert severity="info">Decision recorded. Execution is reported separately.</Alert>}
            {failure && <Alert severity="error">{failure}</Alert>}
            {outcome && <Alert severity="info"><Typography variant="subtitle2">Action outcome: {outcome.actionName}</Typography>{outcome.message}</Alert>}
          </Stack>
        </Box>
        {task && isPendingReviewTask(task) && !detailQuery.isError && <Stack direction="row" gap={1.5} flexWrap="wrap" justifyContent="flex-end"
          sx={{ pt: 2, pb: workspaceView ? 8 : 3, px: workspaceView ? 0 : 3, mt: 2, borderTop: 1, borderColor: 'divider', flexShrink: 0 }}>
          <Button color="error" variant="outlined" startIcon={<CancelRoundedIcon />} disabled={busy || !canDecideReviewTask(task, 'REJECT')}
            onClick={() => openConfirmation('REJECT')}>Reject</Button>
          <Button color="success" variant="contained" startIcon={<CheckCircleRoundedIcon />} disabled={busy || !canDecideReviewTask(task, 'APPROVE')}
            onClick={() => openConfirmation('APPROVE')}>Approve</Button>
        </Stack>}
      </Box>
    </Box>

    <Dialog open={pendingDecision != null} onClose={closeConfirmation} maxWidth="sm" fullWidth aria-labelledby="review-confirmation-title">
      <DialogTitle id="review-confirmation-title">{pendingDecision?.decision === 'APPROVE' ? 'Approve action proposal?' : 'Reject action proposal?'}</DialogTitle>
      <DialogContent sx={{ overflowWrap: 'anywhere' }}>
        <Typography>{pendingDecision?.task.title}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Task {pendingDecision?.task.taskId}, version {pendingDecision?.task.version}.
          {' '}This decision is recorded under {auth.session?.actorId} and cannot be replaced by a platform operator.</Typography>
        {pendingDecision?.decision === 'APPROVE' && <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Approval authorizes the proposed action; it does not confirm successful execution.</Typography>}
        {!confirmationValid && !decisionMutation.isPending && <Alert severity="warning" sx={{ mt: 2 }}>This review task or customer context changed. Cancel and review the current task before deciding.</Alert>}
        {decisionMutation.error instanceof Error && <Alert severity="error" sx={{ mt: 2 }}>{decisionMutation.error.message}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={closeConfirmation} disabled={decisionMutation.isPending}>Cancel</Button>
        <Button color={pendingDecision?.decision === 'APPROVE' ? 'success' : 'error'} variant="contained"
          disabled={!confirmationValid || decisionMutation.isPending} onClick={() => { if (pendingDecision && confirmationValid) decisionMutation.mutate(pendingDecision) }}>
          {decisionMutation.isPending ? 'Recording…' : pendingDecision?.decision === 'APPROVE' ? 'Approve' : 'Reject'}
        </Button>
      </DialogActions>
    </Dialog>
  </Stack>
}
