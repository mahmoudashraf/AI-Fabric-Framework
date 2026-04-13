import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
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
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  fetchDeploymentDeletionNotifications,
  retriggerRunningDeploymentDeletionOperation,
  type DeploymentDeletionOperationSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusColor(
  status: string,
): 'default' | 'info' | 'warning' | 'success' | 'error' {
  switch (status) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warning'
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'error'
    default:
      return 'default'
  }
}

function renderDetailObject(value: unknown): string {
  try {
    return JSON.stringify(value ?? {}, null, 2)
  } catch {
    return String(value)
  }
}

export function NotificationsPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [selectedOperationId, setSelectedOperationId] = useState('')
  const [actionFeedback, setActionFeedback] = useState<{ severity: 'success' | 'error'; message: string } | null>(null)

  const notificationsQuery = useQuery({
    queryKey: ['deployment-deletion-notifications', statusFilter],
    queryFn: () => fetchDeploymentDeletionNotifications(statusFilter === 'ALL' ? undefined : statusFilter, 100),
    enabled: auth.session?.canManageUsers ?? false,
    refetchInterval: 5000,
  })

  const operations = notificationsQuery.data ?? []

  useEffect(() => {
    if (operations.length === 0) {
      setSelectedOperationId('')
      return
    }
    if (!selectedOperationId || !operations.some((operation) => operation.id === selectedOperationId)) {
      setSelectedOperationId(operations[0].id)
    }
  }, [operations, selectedOperationId])

  const selectedOperation = useMemo(
    () => operations.find((operation) => operation.id === selectedOperationId) ?? null,
    [operations, selectedOperationId],
  )

  const retriggerCleanupMutation = useMutation({
    mutationFn: (operationId: string) => retriggerRunningDeploymentDeletionOperation(operationId),
    onSuccess: async (summary) => {
      setActionFeedback({
        severity: 'success',
        message: `Cleanup retriggered for ${summary.deploymentName}. The same hard-delete operation is running again.`,
      })
      await queryClient.invalidateQueries({ queryKey: ['deployment-deletion-notifications'] })
    },
    onError: (error) => {
      setActionFeedback({
        severity: 'error',
        message: error instanceof Error ? error.message : 'Failed to retrigger cleanup.',
      })
    },
  })

  if (!auth.session?.canManageUsers) {
    return (
      <Alert severity="warning">
        Notifications are reserved for platform administrators because they include destructive cleanup and deletion execution details.
      </Alert>
    )
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Notifications" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Deletion and cleanup notifications
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Track queued deployment deletions, hard-delete cleanup progress, and full failure details. This page is the
          operator record for what was requested and what actually happened.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            {actionFeedback ? (
              <Alert severity={actionFeedback.severity} onClose={() => setActionFeedback(null)}>
                {actionFeedback.message}
              </Alert>
            ) : null}
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', md: 'center' }}
            >
              <TextField
                select
                label="Status"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
                sx={{ minWidth: 240 }}
              >
                <MenuItem value="ALL">All statuses</MenuItem>
                <MenuItem value="QUEUED">Queued</MenuItem>
                <MenuItem value="RUNNING">Running</MenuItem>
                <MenuItem value="FAILED">Failed</MenuItem>
                <MenuItem value="SUCCEEDED">Succeeded</MenuItem>
              </TextField>
              <Button
                variant="outlined"
                startIcon={<SyncRoundedIcon />}
                onClick={() => queryClient.invalidateQueries({ queryKey: ['deployment-deletion-notifications'] })}
                disabled={notificationsQuery.isFetching}
              >
                {notificationsQuery.isFetching ? 'Refreshing…' : 'Refresh'}
              </Button>
            </Stack>

            {notificationsQuery.isLoading ? (
              <Typography color="text.secondary">Loading notifications…</Typography>
            ) : notificationsQuery.isError ? (
              <Alert severity="error">
                {notificationsQuery.error instanceof Error
                  ? notificationsQuery.error.message
                  : 'Failed to load deletion notifications.'}
              </Alert>
            ) : operations.length === 0 ? (
              <Alert severity="info">No deletion or cleanup notifications match the current filter.</Alert>
            ) : (
              <Grid container spacing={2}>
                <Grid item xs={12} md={5}>
                  <Stack spacing={1.5}>
                    {operations.map((operation) => (
                      <Card
                        key={operation.id}
                        sx={{
                          border: '1px solid',
                          borderColor: operation.id === selectedOperationId ? 'primary.main' : 'divider',
                          boxShadow: 'none',
                          cursor: 'pointer',
                        }}
                        onClick={() => setSelectedOperationId(operation.id)}
                      >
                        <CardContent>
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Chip label={operation.status} color={statusColor(operation.status)} size="small" />
                              {operation.hardDelete ? <Chip label="Hard delete" size="small" variant="outlined" /> : null}
                              <Chip label={operation.environmentName} size="small" variant="outlined" />
                            </Stack>
                            <Box>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                {operation.deploymentName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {operation.statusMessage}
                              </Typography>
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                              Requested {formatTimestamp(operation.createdAt)} by {operation.requestedByActorId}
                            </Typography>
                          </Stack>
                        </CardContent>
                      </Card>
                    ))}
                  </Stack>
                </Grid>

                <Grid item xs={12} md={7}>
                  {selectedOperation ? (
                    <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                      <CardContent>
                        <Stack spacing={2}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Chip label={selectedOperation.status} color={statusColor(selectedOperation.status)} />
                            {selectedOperation.hardDelete ? <Chip label="Hard delete" variant="outlined" /> : null}
                            <Button
                              component={Link}
                              to={`/overview?deploymentId=${encodeURIComponent(selectedOperation.deploymentId)}`}
                              variant="outlined"
                              size="small"
                            >
                              Open deployment
                            </Button>
                          </Stack>

                          {selectedOperation.status === 'FAILED' ? (
                            <Alert severity="error">
                              {selectedOperation.errorMessage ?? 'Deletion failed. Review the request and result details below.'}
                            </Alert>
                          ) : null}

                          {selectedOperation.status === 'RUNNING' && selectedOperation.hardDelete ? (
                            <Alert
                              severity="warning"
                              action={
                                <Button
                                  color="inherit"
                                  size="small"
                                  disabled={retriggerCleanupMutation.isPending}
                                  onClick={() => {
                                    setActionFeedback(null)
                                    retriggerCleanupMutation.mutate(selectedOperation.id)
                                  }}
                                >
                                  {retriggerCleanupMutation.isPending ? 'Retriggering…' : 'Retrigger cleanup'}
                                </Button>
                              }
                            >
                              Managed infrastructure cleanup is still running. Use this only when vendor cleanup appears stuck and you need to replay the current hard-delete operation.
                            </Alert>
                          ) : null}

                          <Grid container spacing={2}>
                            <Grid item xs={12} md={6}>
                              <Typography variant="subtitle2">Operation</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {selectedOperation.id}
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="subtitle2">Deployment</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {selectedOperation.deploymentId}
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="subtitle2">Requested</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {formatTimestamp(selectedOperation.createdAt)}
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="subtitle2">Completed</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {formatTimestamp(selectedOperation.completedAt)}
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="subtitle2">Requester</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {selectedOperation.requestedByActorId} · {selectedOperation.requestedByRole}
                              </Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="subtitle2">Reason</Typography>
                              <Typography variant="body2" color="text.secondary">
                                {selectedOperation.requestReason ?? '—'}
                              </Typography>
                            </Grid>
                          </Grid>

                          <Box>
                            <Typography variant="subtitle2" sx={{ mb: 1 }}>
                              Requested scope
                            </Typography>
                            <Box
                              component="pre"
                              sx={{
                                m: 0,
                                p: 2,
                                borderRadius: 2,
                                bgcolor: 'grey.100',
                                overflowX: 'auto',
                                fontSize: 12,
                              }}
                            >
                              {renderDetailObject(selectedOperation.requestDetails)}
                            </Box>
                          </Box>

                          <Box>
                            <Typography variant="subtitle2" sx={{ mb: 1 }}>
                              Execution result
                            </Typography>
                            <Box
                              component="pre"
                              sx={{
                                m: 0,
                                p: 2,
                                borderRadius: 2,
                                bgcolor: 'grey.100',
                                overflowX: 'auto',
                                fontSize: 12,
                              }}
                            >
                              {renderDetailObject(selectedOperation.resultDetails)}
                            </Box>
                          </Box>
                        </Stack>
                      </CardContent>
                    </Card>
                  ) : null}
                </Grid>
              </Grid>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
