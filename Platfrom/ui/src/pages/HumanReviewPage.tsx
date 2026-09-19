import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import TaskAltRoundedIcon from '@mui/icons-material/TaskAltRounded'
import CancelRoundedIcon from '@mui/icons-material/CancelRounded'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import {
  decideDeploymentHumanReview,
  fetchDeploymentHumanReviewDetail,
  fetchDeploymentHumanReviewInbox,
  type DeploymentHumanReviewTask,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function timestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (status) {
    case 'APPROVED':
    case 'COMPLETED':
      return 'success'
    case 'PENDING':
    case 'IN_REVIEW':
      return 'warning'
    case 'REJECTED':
    case 'EXPIRED':
    case 'FAILED':
      return 'error'
    default:
      return 'default'
  }
}

export function HumanReviewPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const { selectedDeploymentId } = useDeploymentWorkspace()
  const [selectedTaskId, setSelectedTaskId] = useState('')
  const [pendingDecision, setPendingDecision] = useState<'APPROVE' | 'REJECT' | null>(null)
  const customerReviewer = auth.session?.role === 'CUSTOMER_ADMIN'
    && auth.session.authenticationMode === 'SESSION'

  const inboxQuery = useQuery({
    queryKey: ['deployment-human-reviews', selectedDeploymentId],
    queryFn: () => fetchDeploymentHumanReviewInbox(selectedDeploymentId),
    enabled: customerReviewer && selectedDeploymentId.length > 0,
    refetchInterval: 15_000,
  })
  const tasks = inboxQuery.data ?? []
  const selectedTask = useMemo(
    () => tasks.find((task) => task.taskId === selectedTaskId) ?? tasks[0] ?? null,
    [selectedTaskId, tasks],
  )
  const detailQuery = useQuery({
    queryKey: ['deployment-human-review-detail', selectedDeploymentId, selectedTask?.taskId],
    queryFn: () => fetchDeploymentHumanReviewDetail(selectedDeploymentId, selectedTask?.taskId ?? ''),
    enabled: customerReviewer && selectedDeploymentId.length > 0 && selectedTask != null,
  })
  const decisionMutation = useMutation({
    mutationFn: (input: { task: DeploymentHumanReviewTask; decision: 'APPROVE' | 'REJECT' }) =>
      decideDeploymentHumanReview(selectedDeploymentId, input.task.taskId, {
        decision: input.decision,
        expectedVersion: input.task.version,
        decisionId: `platform-review-${globalThis.crypto.randomUUID()}`,
      }),
    onSuccess: async () => {
      setPendingDecision(null)
      await queryClient.invalidateQueries({ queryKey: ['deployment-human-reviews', selectedDeploymentId] })
      await queryClient.invalidateQueries({
        queryKey: ['deployment-human-review-detail', selectedDeploymentId, selectedTask?.taskId],
      })
    },
  })

  if (!customerReviewer) {
    return (
      <Stack spacing={2}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>Human Review</Typography>
        <Alert severity="info">
          Sign in with a customer administrator session to access this deployment's review inbox.
        </Alert>
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Chip label="Customer authority" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
          <Typography variant="h4" sx={{ fontWeight: 800 }}>Human Review</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Governed action proposals awaiting an authorized customer decision.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshRoundedIcon />}
          disabled={!selectedDeploymentId || inboxQuery.isFetching}
          onClick={() => void inboxQuery.refetch()}
          sx={{ alignSelf: { xs: 'stretch', md: 'center' } }}
        >
          Refresh
        </Button>
      </Stack>

      {!selectedDeploymentId ? <Alert severity="info">Select a deployment first.</Alert> : null}
      {inboxQuery.error instanceof Error ? <Alert severity="error">{inboxQuery.error.message}</Alert> : null}
      {detailQuery.error instanceof Error ? <Alert severity="error">{detailQuery.error.message}</Alert> : null}
      {decisionMutation.error instanceof Error ? <Alert severity="error">{decisionMutation.error.message}</Alert> : null}

      {inboxQuery.isLoading ? (
        <Box sx={{ minHeight: 240, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
      ) : tasks.length === 0 && selectedDeploymentId ? (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <TaskAltRoundedIcon color="success" sx={{ fontSize: 42 }} />
          <Typography variant="h6" sx={{ mt: 1 }}>Inbox clear</Typography>
          <Typography color="text.secondary">No review tasks are visible for this customer and deployment.</Typography>
        </Paper>
      ) : tasks.length > 0 ? (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(280px, 0.38fr) minmax(0, 0.62fr)' }, gap: 2.5 }}>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ px: 2.5, py: 2 }}>
              <FactCheckRoundedIcon color="primary" />
              <Typography variant="h6">Review tasks</Typography>
              <Chip size="small" label={tasks.length} />
            </Stack>
            <Divider />
            <List disablePadding>
              {tasks.map((task) => (
                <ListItemButton
                  key={task.taskId}
                  selected={task.taskId === selectedTask?.taskId}
                  onClick={() => setSelectedTaskId(task.taskId)}
                  sx={{ py: 1.75, alignItems: 'flex-start' }}
                >
                  <ListItemText
                    primary={task.title}
                    secondary={
                      <Stack spacing={0.75} sx={{ mt: 0.75 }}>
                        <Typography variant="body2" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                          {task.summary}
                        </Typography>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Chip size="small" color={statusColor(task.status)} label={task.status.replace(/_/g, ' ')} />
                          <Typography variant="caption" color="text.secondary">Expires {timestamp(task.expiresAt)}</Typography>
                        </Stack>
                      </Stack>
                    }
                  />
                </ListItemButton>
              ))}
            </List>
          </Paper>

          <Paper variant="outlined" sx={{ p: 3, minHeight: 360 }}>
            {detailQuery.isLoading || !selectedTask ? (
              <Box sx={{ minHeight: 260, display: 'grid', placeItems: 'center' }}><CircularProgress /></Box>
            ) : (
              <Stack spacing={2.5}>
                <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1.5}>
                  <Box>
                    <Typography variant="overline" color="text.secondary">{selectedTask.type.replace(/_/g, ' ')}</Typography>
                    <Typography variant="h5" sx={{ fontWeight: 800 }}>{selectedTask.title}</Typography>
                  </Box>
                  <Chip color={statusColor(selectedTask.status)} label={selectedTask.status.replace(/_/g, ' ')} />
                </Stack>
                <Typography>{selectedTask.summary}</Typography>
                <Divider />
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Policy</Typography>
                    <Typography>{selectedTask.policyId.name}@{selectedTask.policyId.version}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Version</Typography>
                    <Typography>{selectedTask.version}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Created</Typography>
                    <Typography>{timestamp(selectedTask.createdAt)}</Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Expires</Typography>
                    <Typography>{timestamp(selectedTask.expiresAt)}</Typography>
                  </Box>
                </Box>
                {detailQuery.data?.message ? <Alert severity="info">{detailQuery.data.message}</Alert> : null}
                {detailQuery.data?.failureReason ? <Alert severity="error">{detailQuery.data.failureReason}</Alert> : null}
                {detailQuery.data?.outcome ? (
                  <Alert severity="success">
                    {detailQuery.data.outcome.actionName}: {detailQuery.data.outcome.message}
                  </Alert>
                ) : null}
                {selectedTask.status === 'PENDING' ? (
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="flex-end">
                    <Button
                      color="error"
                      variant="outlined"
                      startIcon={<CancelRoundedIcon />}
                      disabled={!selectedTask.allowedDecisions.includes('REJECT') || decisionMutation.isPending}
                      onClick={() => setPendingDecision('REJECT')}
                    >
                      Reject
                    </Button>
                    <Button
                      color="success"
                      variant="contained"
                      startIcon={<CheckCircleRoundedIcon />}
                      disabled={!selectedTask.allowedDecisions.includes('APPROVE') || decisionMutation.isPending}
                      onClick={() => setPendingDecision('APPROVE')}
                    >
                      Approve
                    </Button>
                  </Stack>
                ) : null}
              </Stack>
            )}
          </Paper>
        </Box>
      ) : null}

      <Dialog open={pendingDecision != null} onClose={() => setPendingDecision(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{pendingDecision === 'APPROVE' ? 'Approve action proposal?' : 'Reject action proposal?'}</DialogTitle>
        <DialogContent>
          <Typography color="text.secondary">
            {selectedTask?.title}. This decision is recorded under {auth.session?.actorId} and cannot be replaced by a platform operator.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingDecision(null)}>Cancel</Button>
          <Button
            color={pendingDecision === 'APPROVE' ? 'success' : 'error'}
            variant="contained"
            disabled={!selectedTask || !pendingDecision || decisionMutation.isPending}
            onClick={() => {
              if (selectedTask && pendingDecision) {
                decisionMutation.mutate({ task: selectedTask, decision: pendingDecision })
              }
            }}
          >
            {decisionMutation.isPending ? 'Recording…' : pendingDecision === 'APPROVE' ? 'Approve' : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
