import ApprovalRoundedIcon from '@mui/icons-material/ApprovalRounded'
import GavelRoundedIcon from '@mui/icons-material/GavelRounded'
import HistoryToggleOffRoundedIcon from '@mui/icons-material/HistoryToggleOffRounded'
import TaskAltRoundedIcon from '@mui/icons-material/TaskAltRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  approveDeploymentApproval,
  createDeploymentApproval,
  fetchDeploymentApprovals,
  fetchPlatformUserPreferences,
  fetchDeploymentVersions,
  rejectDeploymentApproval,
  updatePlatformUserPreferences,
  type DeploymentApprovalsViewPreferences,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

function normalizedText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function approvalsViewEquals(
  saved: DeploymentApprovalsViewPreferences | null | undefined,
  current: DeploymentApprovalsViewPreferences,
): boolean {
  if (!saved) {
    return false
  }
  return saved.statusFilter === current.statusFilter
    && saved.operationFilter === current.operationFilter
    && saved.mineOnly === current.mineOnly
    && saved.searchTerm === current.searchTerm
}

function statusColor(
  status: string,
): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (status) {
    case 'APPROVED':
    case 'CONSUMED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
    case 'EXPIRED':
      return 'error'
    default:
      return 'default'
  }
}

export function ApprovalsPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const location = useLocation()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const canApprove = auth.session?.enabled ? auth.session.canManageUsers : true
  const currentActorId = auth.session?.actorId ?? ''
  const [operationType, setOperationType] = useState<'APPLY_VERSION' | 'DELETE_DEPLOYMENT'>('APPLY_VERSION')
  const [targetVersionId, setTargetVersionId] = useState('')
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [operationFilter, setOperationFilter] = useState('ALL')
  const [mineOnly, setMineOnly] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const viewInitializedRef = useRef(false)
  const viewHydrationRef = useRef(false)

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const requestedAction = params.get('action')
    const requestedVersionId = params.get('versionId')
    if (requestedAction === 'DELETE_DEPLOYMENT') {
      setOperationType('DELETE_DEPLOYMENT')
    } else if (requestedAction === 'APPLY_VERSION') {
      setOperationType('APPLY_VERSION')
    }
    if (requestedVersionId != null) {
      setTargetVersionId(requestedVersionId)
    }
  }, [location.search])

  const approvalsQuery = useQuery({
    queryKey: ['deployment-approvals', selectedDeploymentId],
    queryFn: () => fetchDeploymentApprovals(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const versionsQuery = useQuery({
    queryKey: ['deployment-versions', selectedDeploymentId],
    queryFn: () => fetchDeploymentVersions(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const preferencesQuery = useQuery({
    queryKey: ['platform-preferences'],
    queryFn: fetchPlatformUserPreferences,
  })
  const updatePreferencesMutation = useMutation({
    mutationFn: updatePlatformUserPreferences,
    onSuccess: (data) => {
      queryClient.setQueryData(['platform-preferences'], data)
    },
  })

  useEffect(() => {
    if (viewInitializedRef.current || !preferencesQuery.isSuccess) {
      return
    }
    const savedView = preferencesQuery.data?.deploymentApprovalsView
    if (savedView) {
      setStatusFilter(savedView.statusFilter ?? 'ALL')
      setOperationFilter(savedView.operationFilter ?? 'ALL')
      setMineOnly(savedView.mineOnly ?? false)
      setSearchTerm(savedView.searchTerm ?? '')
    }
    viewHydrationRef.current = true
    viewInitializedRef.current = true
  }, [preferencesQuery.data, preferencesQuery.isSuccess])

  const requestMutation = useMutation({
    mutationFn: () => createDeploymentApproval(selectedDeploymentId, {
      operationType,
      targetVersionId: operationType === 'APPLY_VERSION' ? targetVersionId : undefined,
      reason,
    }),
    onSuccess: async (approval) => {
      setNotice(`Approval request ${approval.id} submitted for ${approval.operationType}.`)
      setReason('')
      await queryClient.invalidateQueries({ queryKey: ['deployment-approvals', selectedDeploymentId] })
    },
  })

  const approveMutation = useMutation({
    mutationFn: (approvalId: string) => approveDeploymentApproval(approvalId),
    onSuccess: async () => {
      setNotice('Approval request approved.')
      await queryClient.invalidateQueries({ queryKey: ['deployment-approvals', selectedDeploymentId] })
    },
  })

  const rejectMutation = useMutation({
    mutationFn: (approvalId: string) => rejectDeploymentApproval(approvalId),
    onSuccess: async () => {
      setNotice('Approval request rejected.')
      await queryClient.invalidateQueries({ queryKey: ['deployment-approvals', selectedDeploymentId] })
    },
  })

  const approvals = approvalsQuery.data ?? []
  const pendingApprovals = useMemo(
    () => approvals.filter((approval) => approval.status === 'PENDING'),
    [approvals],
  )
  const mine = useMemo(
    () => approvals.filter((approval) => approval.requestedByActorId === currentActorId),
    [approvals, currentActorId],
  )
  const approvalsView = useMemo<DeploymentApprovalsViewPreferences>(() => ({
    statusFilter,
    operationFilter,
    mineOnly,
    searchTerm,
  }), [mineOnly, operationFilter, searchTerm, statusFilter])
  const viewMatchesSaved = useMemo(
    () => approvalsViewEquals(preferencesQuery.data?.deploymentApprovalsView, approvalsView),
    [approvalsView, preferencesQuery.data?.deploymentApprovalsView],
  )

  useEffect(() => {
    if (!preferencesQuery.isSuccess || !viewInitializedRef.current) {
      return
    }
    if (viewHydrationRef.current) {
      viewHydrationRef.current = false
      return
    }
    if (viewMatchesSaved) {
      return
    }
    const handle = window.setTimeout(() => {
      updatePreferencesMutation.mutate({ deploymentApprovalsView: approvalsView })
    }, 600)
    return () => window.clearTimeout(handle)
  }, [
    approvalsView,
    preferencesQuery.isSuccess,
    updatePreferencesMutation,
    viewMatchesSaved,
  ])

  const filteredApprovals = useMemo(() => {
    const searchValue = normalizedText(searchTerm)
    const effectiveMineOnly = !canApprove || mineOnly
    return approvals.filter((approval) => {
      const matchesStatus = statusFilter === 'ALL' || approval.status === statusFilter
      const matchesOperation = operationFilter === 'ALL' || approval.operationType === operationFilter
      const matchesMine = !effectiveMineOnly || approval.requestedByActorId === currentActorId
      const matchesSearch = searchValue.length === 0
        || normalizedText(approval.id).includes(searchValue)
        || normalizedText(approval.requestedByDisplayName ?? approval.requestedByActorId).includes(searchValue)
        || normalizedText(approval.targetVersionLabel ?? approval.targetVersionId).includes(searchValue)
        || normalizedText(approval.requestedReason).includes(searchValue)
      return matchesStatus && matchesOperation && matchesMine && matchesSearch
    })
  }, [approvals, canApprove, currentActorId, mineOnly, operationFilter, searchTerm, statusFilter])

  const requestDisabled = selectedDeploymentId.length === 0
    || reason.trim().length < 8
    || (operationType === 'APPLY_VERSION' && targetVersionId.trim().length === 0)
    || requestMutation.isPending

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Approvals" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Protected deployment actions
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Operators can request approval for guarded deployment actions, and platform admins can approve or reject
          them here. Approved requests are single-use and are consumed when the protected action runs.
        </Typography>
      </Box>

      {notice ? <Alert severity="success">{notice}</Alert> : null}
      {approvalsQuery.error instanceof Error ? <Alert severity="error">{approvalsQuery.error.message}</Alert> : null}
      {requestMutation.error instanceof Error ? <Alert severity="error">{requestMutation.error.message}</Alert> : null}
      {approveMutation.error instanceof Error ? <Alert severity="error">{approveMutation.error.message}</Alert> : null}
      {rejectMutation.error instanceof Error ? <Alert severity="error">{rejectMutation.error.message}</Alert> : null}

      {!selectedDeploymentId ? (
        <Alert severity="info">Select a deployment in the workspace header first.</Alert>
      ) : null}

      {workspace ? (
        <Grid container spacing={2.5}>
          <Grid item xs={12} md={4}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Apply guardrail</Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  {workspace.deployment.approvalRequiredForApply ? 'Required' : 'Direct apply'}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={4}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Delete guardrail</Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  {workspace.deployment.approvalRequiredForDelete ? 'Required' : 'Direct delete'}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={4}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Pending approvals</Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>{pendingApprovals.length}</Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <ApprovalRoundedIcon color="primary" />
                  <Typography variant="h6">Request approval</Typography>
                </Stack>
                <Alert severity="info">
                  Use this for protected apply and delete operations. Approved requests expire automatically if unused.
                </Alert>
                <TextField
                  select
                  label="Operation"
                  value={operationType}
                  onChange={(event) => setOperationType(event.target.value as 'APPLY_VERSION' | 'DELETE_DEPLOYMENT')}
                >
                  <MenuItem value="APPLY_VERSION">Apply version</MenuItem>
                  <MenuItem value="DELETE_DEPLOYMENT">Delete deployment</MenuItem>
                </TextField>
                {operationType === 'APPLY_VERSION' ? (
                  <TextField
                    select
                    label="Target version"
                    value={targetVersionId}
                    onChange={(event) => setTargetVersionId(event.target.value)}
                  >
                    <MenuItem value="">Select version</MenuItem>
                    {(versionsQuery.data ?? []).map((version) => (
                      <MenuItem key={version.id} value={version.id}>
                        {version.versionLabel} ({version.status})
                      </MenuItem>
                    ))}
                  </TextField>
                ) : null}
                <TextField
                  multiline
                  minRows={4}
                  label="Why is this action needed?"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  helperText="Explain the business reason and expected impact."
                />
                <Button
                  variant="contained"
                  startIcon={<TaskAltRoundedIcon />}
                  disabled={requestDisabled}
                  onClick={() => {
                    setNotice(null)
                    requestMutation.mutate()
                  }}
                >
                  {requestMutation.isPending ? 'Submitting…' : 'Submit request'}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <GavelRoundedIcon color="primary" />
                  <Typography variant="h6">Approval history</Typography>
                </Stack>
                {!canApprove ? (
                  <Alert severity="info">
                    You can see only your own approval requests here. Platform admins can resolve pending requests.
                  </Alert>
                ) : null}
                <Grid container spacing={1.5}>
                  <Grid item xs={12} md={4}>
                    <TextField
                      fullWidth
                      label="Search approvals"
                      value={searchTerm}
                      onChange={(event) => setSearchTerm(event.target.value)}
                      helperText="Matches requester, version, reason, or request id"
                    />
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <TextField
                      fullWidth
                      select
                      label="Status"
                      value={statusFilter}
                      onChange={(event) => setStatusFilter(event.target.value)}
                    >
                      <MenuItem value="ALL">All statuses</MenuItem>
                      <MenuItem value="PENDING">Pending</MenuItem>
                      <MenuItem value="APPROVED">Approved</MenuItem>
                      <MenuItem value="CONSUMED">Consumed</MenuItem>
                      <MenuItem value="REJECTED">Rejected</MenuItem>
                      <MenuItem value="EXPIRED">Expired</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <TextField
                      fullWidth
                      select
                      label="Operation"
                      value={operationFilter}
                      onChange={(event) => setOperationFilter(event.target.value)}
                    >
                      <MenuItem value="ALL">All operations</MenuItem>
                      <MenuItem value="APPLY_VERSION">Apply version</MenuItem>
                      <MenuItem value="DELETE_DEPLOYMENT">Delete deployment</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={2}>
                    <FormControlLabel
                      sx={{ height: '100%', alignItems: 'center', m: 0 }}
                      control={(
                        <Checkbox
                          checked={!canApprove || mineOnly}
                          onChange={(event) => setMineOnly(event.target.checked)}
                          disabled={!canApprove}
                        />
                      )}
                      label={canApprove ? 'My requests only' : 'Only my requests'}
                    />
                  </Grid>
                </Grid>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`Visible approvals: ${filteredApprovals.length}`} variant="outlined" />
                  <Chip label={`Pending: ${pendingApprovals.length}`} variant="outlined" />
                  {(!canApprove || mineOnly) ? <Chip label={`Mine: ${mine.length}`} color="secondary" variant="outlined" /> : null}
                </Stack>
                <Stack spacing={1.5}>
                  {filteredApprovals.map((approval) => (
                    <Card key={approval.id} variant="outlined" sx={{ borderRadius: 3 }}>
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Stack
                            direction={{ xs: 'column', md: 'row' }}
                            spacing={1}
                            justifyContent="space-between"
                            alignItems={{ xs: 'flex-start', md: 'center' }}
                          >
                            <Box>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                {approval.operationType === 'APPLY_VERSION'
                                  ? `Apply ${approval.targetVersionLabel ?? approval.targetVersionId ?? 'version'}`
                                  : 'Delete deployment'}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                Requested by {approval.requestedByDisplayName ?? approval.requestedByActorId}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip label={approval.operationType} variant="outlined" />
                              <Chip label={approval.status} color={statusColor(approval.status)} />
                            </Stack>
                          </Stack>

                          <Typography variant="body2" color="text.secondary">
                            {approval.requestedReason}
                          </Typography>

                          <Grid container spacing={1.5}>
                            <Grid item xs={12} md={6}>
                              <Typography variant="caption" color="text.secondary">Requested</Typography>
                              <Typography variant="body2">{formatTimestamp(approval.createdAt)}</Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="caption" color="text.secondary">Updated</Typography>
                              <Typography variant="body2">{formatTimestamp(approval.updatedAt)}</Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="caption" color="text.secondary">Approved by</Typography>
                              <Typography variant="body2">{approval.approvedByDisplayName ?? approval.approvedByActorId ?? '—'}</Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="caption" color="text.secondary">Expires</Typography>
                              <Typography variant="body2">{formatTimestamp(approval.expiresAt)}</Typography>
                            </Grid>
                          </Grid>

                          {approval.resolutionNote ? (
                            <Alert severity="info">{approval.resolutionNote}</Alert>
                          ) : null}

                          {canApprove && approval.status === 'PENDING' ? (
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              <Button
                                variant="outlined"
                                color="error"
                                startIcon={<HistoryToggleOffRoundedIcon />}
                                disabled={approveMutation.isPending || rejectMutation.isPending}
                                onClick={() => {
                                  setNotice(null)
                                  rejectMutation.mutate(approval.id)
                                }}
                              >
                                Reject
                              </Button>
                              <Button
                                variant="contained"
                                startIcon={<TaskAltRoundedIcon />}
                                disabled={approveMutation.isPending || rejectMutation.isPending}
                                onClick={() => {
                                  setNotice(null)
                                  approveMutation.mutate(approval.id)
                                }}
                              >
                                Approve
                              </Button>
                            </Stack>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                  {filteredApprovals.length === 0 ? (
                    <Alert severity="info" icon={<WarningAmberRoundedIcon fontSize="inherit" />}>
                      No approval requests match the current filters for this deployment.
                    </Alert>
                  ) : null}
                </Stack>

                {mine.some((approval) => approval.status === 'APPROVED') ? (
                  <Alert severity="success">
                    You have at least one approved request ready to consume. Return to the relevant deployment action and
                    run it from the platform UI.
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  )
}
