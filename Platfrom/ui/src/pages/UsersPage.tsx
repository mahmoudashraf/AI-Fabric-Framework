import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import PersonAddRoundedIcon from '@mui/icons-material/PersonAddRounded'
import SecurityRoundedIcon from '@mui/icons-material/SecurityRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  createPlatformUser,
  deleteDeploymentAssignment,
  fetchPlatformUserAccessOverview,
  resetPlatformUserPassword,
  updatePlatformUser,
  upsertDeploymentAssignment,
  type PlatformUserAccessSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

type CreateUserFormState = {
  email: string
  displayName: string
  password: string
  role: string
}

type EditUserFormState = {
  displayName: string
  role: string
  status: string
}

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : 'Never'
}

function roleChipColor(role: string): 'secondary' | 'primary' | 'default' {
  if (role === 'PLATFORM_ADMIN' || role === 'DEPLOYMENT_ADMIN') {
    return 'secondary'
  }
  if (role === 'PLATFORM_OPERATOR' || role === 'DEPLOYMENT_EDITOR' || role === 'DEPLOYMENT_OPERATOR') {
    return 'primary'
  }
  return 'default'
}

function statusChipColor(status: string): 'success' | 'warning' | 'default' {
  if (status === 'ACTIVE') {
    return 'success'
  }
  if (status === 'DISABLED') {
    return 'warning'
  }
  return 'default'
}

export function UsersPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const canManageUsers = auth.session?.enabled ? auth.session.canManageUsers : true
  const [notice, setNotice] = useState<string | null>(null)
  const [createForm, setCreateForm] = useState<CreateUserFormState>({
    email: '',
    displayName: '',
    password: '',
    role: 'PLATFORM_OPERATOR',
  })
  const [editingUser, setEditingUser] = useState<PlatformUserAccessSummary | null>(null)
  const [editForm, setEditForm] = useState<EditUserFormState>({
    displayName: '',
    role: 'PLATFORM_OPERATOR',
    status: 'ACTIVE',
  })
  const [resetUser, setResetUser] = useState<PlatformUserAccessSummary | null>(null)
  const [resetPassword, setResetPassword] = useState('')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [assignmentRole, setAssignmentRole] = useState('DEPLOYMENT_OPERATOR')

  const usersQuery = useQuery({
    queryKey: ['platform-user-access-overview', selectedDeploymentId],
    queryFn: () => fetchPlatformUserAccessOverview(selectedDeploymentId || undefined),
    enabled: canManageUsers,
  })

  const users = usersQuery.data ?? []
  const selectedUser = useMemo(
    () => users.find((user) => user.id === selectedUserId) ?? users[0] ?? null,
    [selectedUserId, users],
  )

  useEffect(() => {
    if (users.length === 0) {
      setSelectedUserId('')
      return
    }
    setSelectedUserId((current) => (users.some((user) => user.id === current) ? current : users[0].id))
  }, [users])

  useEffect(() => {
    if (editingUser) {
      setEditForm({
        displayName: editingUser.displayName,
        role: editingUser.role,
        status: editingUser.status,
      })
    }
  }, [editingUser])

  useEffect(() => {
    setAssignmentRole(selectedUser?.selectedDeploymentAssignment?.assignmentRole ?? 'DEPLOYMENT_OPERATOR')
  }, [selectedUser?.id, selectedUser?.selectedDeploymentAssignment?.assignmentRole])

  const metrics = useMemo(() => ({
    total: users.length,
    admins: users.filter((user) => user.role === 'PLATFORM_ADMIN' && user.status === 'ACTIVE').length,
    assignedUsers: users.filter((user) => user.assignmentCount > 0).length,
    selectedDeploymentUsers: users.filter((user) => user.selectedDeploymentAssignment != null).length,
  }), [users])

  const invalidateUserAccess = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['platform-user-access-overview'] }),
      queryClient.invalidateQueries({ queryKey: ['platform-users'] }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['deployment-assignments', selectedDeploymentId] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: () => createPlatformUser(createForm),
    onSuccess: async (user) => {
      setNotice(`${user.email} created. The new account is active immediately.`)
      setCreateForm({
        email: '',
        displayName: '',
        password: '',
        role: 'PLATFORM_OPERATOR',
      })
      await invalidateUserAccess()
      setSelectedUserId(user.id)
    },
  })

  const updateMutation = useMutation({
    mutationFn: () => {
      if (!editingUser) {
        throw new Error('No user selected.')
      }
      return updatePlatformUser(editingUser.id, editForm)
    },
    onSuccess: async (user) => {
      setNotice(`${user.email} updated.`)
      setEditingUser(null)
      await invalidateUserAccess()
      await auth.refreshSession()
    },
  })

  const resetPasswordMutation = useMutation({
    mutationFn: () => {
      if (!resetUser) {
        throw new Error('No user selected.')
      }
      return resetPlatformUserPassword(resetUser.id, { password: resetPassword })
    },
    onSuccess: async (user) => {
      setNotice(`Password reset for ${user.email}. Re-apply is not required because this is a platform identity change.`)
      setResetUser(null)
      setResetPassword('')
      await invalidateUserAccess()
    },
  })

  const assignmentMutation = useMutation({
    mutationFn: () => {
      if (!selectedUser) {
        throw new Error('Select a user first.')
      }
      if (selectedDeploymentId.length === 0) {
        throw new Error('Select a deployment first.')
      }
      return upsertDeploymentAssignment(selectedDeploymentId, {
        userId: selectedUser.id,
        assignmentRole,
      })
    },
    onSuccess: async (assignment) => {
      setNotice(`${assignment.userEmail} now has ${assignment.assignmentRole} access for ${workspace?.deployment.name ?? 'the selected deployment'}.`)
      await invalidateUserAccess()
    },
  })

  const removeAssignmentMutation = useMutation({
    mutationFn: () => {
      if (!selectedUser?.selectedDeploymentAssignment) {
        throw new Error('The selected user does not have access to this deployment.')
      }
      if (selectedDeploymentId.length === 0) {
        throw new Error('Select a deployment first.')
      }
      return deleteDeploymentAssignment(selectedDeploymentId, selectedUser.selectedDeploymentAssignment.assignmentId)
    },
    onSuccess: async () => {
      setNotice(`Deployment access removed for ${selectedUser?.email ?? 'the selected user'}.`)
      await invalidateUserAccess()
    },
  })

  if (!canManageUsers) {
    return (
      <Stack spacing={3}>
        <Box>
          <Chip label="User Access" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
            Unified access administration
          </Typography>
        </Box>
        <Alert severity="warning">
          This screen requires the <code>PLATFORM_ADMIN</code> role.
        </Alert>
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="User Access" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Unified access administration
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 1040 }}>
          Manage platform identity and deployment visibility from one workspace. Pick a deployment in the header to see
          which users can access it, adjust their deployment role, and inspect each operator&apos;s broader access footprint.
        </Typography>
      </Box>

      {notice ? <Alert severity="success">{notice}</Alert> : null}
      {usersQuery.error instanceof Error ? <Alert severity="error">{usersQuery.error.message}</Alert> : null}
      {createMutation.error instanceof Error ? <Alert severity="error">{createMutation.error.message}</Alert> : null}
      {updateMutation.error instanceof Error ? <Alert severity="error">{updateMutation.error.message}</Alert> : null}
      {resetPasswordMutation.error instanceof Error ? <Alert severity="error">{resetPasswordMutation.error.message}</Alert> : null}
      {assignmentMutation.error instanceof Error ? <Alert severity="error">{assignmentMutation.error.message}</Alert> : null}
      {removeAssignmentMutation.error instanceof Error ? <Alert severity="error">{removeAssignmentMutation.error.message}</Alert> : null}

      <Grid container spacing={2.5}>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary">Total users</Typography>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.total}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary">Active admins</Typography>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.admins}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary">Users with assignments</Typography>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.assignedUsers}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary">
                {workspace ? `${workspace.deployment.name} access` : 'Selected deployment access'}
              </Typography>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.selectedDeploymentUsers}</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <PersonAddRoundedIcon color="primary" />
                  <Typography variant="h6">Create user</Typography>
                </Stack>
                <TextField
                  label="Email"
                  value={createForm.email}
                  onChange={(event) => setCreateForm((current) => ({ ...current, email: event.target.value }))}
                />
                <TextField
                  label="Display name"
                  value={createForm.displayName}
                  onChange={(event) => setCreateForm((current) => ({ ...current, displayName: event.target.value }))}
                />
                <TextField
                  label="Temporary password"
                  type="password"
                  value={createForm.password}
                  onChange={(event) => setCreateForm((current) => ({ ...current, password: event.target.value }))}
                  helperText="Minimum 10 characters."
                />
                <TextField
                  select
                  label="Role"
                  value={createForm.role}
                  onChange={(event) => setCreateForm((current) => ({ ...current, role: event.target.value }))}
                >
                  <MenuItem value="PLATFORM_OPERATOR">Platform operator</MenuItem>
                  <MenuItem value="PLATFORM_ADMIN">Platform admin</MenuItem>
                </TextField>
                <Button
                  variant="contained"
                  startIcon={<PersonAddRoundedIcon />}
                  disabled={createMutation.isPending}
                  onClick={() => {
                    setNotice(null)
                    createMutation.mutate()
                  }}
                >
                  Create user
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <SecurityRoundedIcon color="primary" />
                  <Typography variant="h6">Selected user workspace</Typography>
                </Stack>

                {!selectedUser ? (
                  <Alert severity="info">
                    Create a platform user or select one from the directory below to manage access.
                  </Alert>
                ) : (
                  <>
                    <Stack
                      direction={{ xs: 'column', md: 'row' }}
                      spacing={1.5}
                      justifyContent="space-between"
                      alignItems={{ xs: 'flex-start', md: 'center' }}
                    >
                      <Box>
                        <Typography variant="h5" sx={{ fontWeight: 800 }}>
                          {selectedUser.displayName}
                        </Typography>
                        <Typography variant="body1" color="text.secondary">
                          {selectedUser.email}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={selectedUser.role} color={roleChipColor(selectedUser.role)} />
                        <Chip label={selectedUser.status} color={statusChipColor(selectedUser.status)} variant="outlined" />
                        <Chip label={`${selectedUser.assignmentCount} deployment assignments`} variant="outlined" />
                      </Stack>
                    </Stack>

                    <Grid container spacing={1.5}>
                      <Grid item xs={12} md={3}>
                        <Typography variant="caption" color="text.secondary">Last login</Typography>
                        <Typography variant="body2">{formatTimestamp(selectedUser.lastLoginAt)}</Typography>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <Typography variant="caption" color="text.secondary">Created</Typography>
                        <Typography variant="body2">{formatTimestamp(selectedUser.createdAt)}</Typography>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <Typography variant="caption" color="text.secondary">Updated</Typography>
                        <Typography variant="body2">{formatTimestamp(selectedUser.updatedAt)}</Typography>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <Typography variant="caption" color="text.secondary">Assignment split</Typography>
                        <Typography variant="body2">
                          A {selectedUser.adminAssignmentCount} / E {selectedUser.editorAssignmentCount} / O {selectedUser.operatorAssignmentCount} / V {selectedUser.viewerAssignmentCount}
                        </Typography>
                      </Grid>
                    </Grid>

                    <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap" useFlexGap>
                      <Button
                        variant="outlined"
                        startIcon={<EditRoundedIcon />}
                        onClick={() => {
                          setNotice(null)
                          setEditingUser(selectedUser)
                        }}
                      >
                        Edit user
                      </Button>
                      <Button
                        variant="outlined"
                        startIcon={<LockResetRoundedIcon />}
                        onClick={() => {
                          setNotice(null)
                          setResetUser(selectedUser)
                        }}
                      >
                        Reset password
                      </Button>
                    </Stack>

                    <Card variant="outlined" sx={{ borderRadius: 3 }}>
                      <CardContent>
                        <Stack spacing={2}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                            Selected deployment access
                          </Typography>
                          {workspace ? (
                            <>
                              <Typography variant="body2" color="text.secondary">
                                Manage {selectedUser.displayName}&apos;s role for {workspace.deployment.name} ({workspace.deployment.environment}).
                              </Typography>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Chip
                                  label={
                                    selectedUser.selectedDeploymentAssignment
                                      ? `Current role: ${selectedUser.selectedDeploymentAssignment.assignmentRole}`
                                      : 'No assignment yet'
                                  }
                                  color={selectedUser.selectedDeploymentAssignment ? roleChipColor(selectedUser.selectedDeploymentAssignment.assignmentRole) : 'default'}
                                  variant={selectedUser.selectedDeploymentAssignment ? 'filled' : 'outlined'}
                                />
                              </Stack>
                              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                                <TextField
                                  select
                                  label="Deployment role"
                                  value={assignmentRole}
                                  onChange={(event) => setAssignmentRole(event.target.value)}
                                  sx={{ minWidth: 240 }}
                                >
                                  <MenuItem value="DEPLOYMENT_ADMIN">Deployment admin</MenuItem>
                                  <MenuItem value="DEPLOYMENT_EDITOR">Deployment editor</MenuItem>
                                  <MenuItem value="DEPLOYMENT_OPERATOR">Deployment operator</MenuItem>
                                  <MenuItem value="DEPLOYMENT_VIEWER">Deployment viewer</MenuItem>
                                </TextField>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Button
                                    variant="contained"
                                    disabled={assignmentMutation.isPending}
                                    onClick={() => {
                                      setNotice(null)
                                      assignmentMutation.mutate()
                                    }}
                                  >
                                    {selectedUser.selectedDeploymentAssignment ? 'Update access' : 'Grant access'}
                                  </Button>
                                  <Button
                                    variant="outlined"
                                    color="error"
                                    startIcon={<DeleteOutlineRoundedIcon />}
                                    disabled={removeAssignmentMutation.isPending || !selectedUser.selectedDeploymentAssignment}
                                    onClick={() => {
                                      setNotice(null)
                                      removeAssignmentMutation.mutate()
                                    }}
                                  >
                                    Remove access
                                  </Button>
                                </Stack>
                              </Stack>
                            </>
                          ) : (
                            <Alert severity="info">
                              Select a deployment in the workspace header to manage deployment-specific access from this user workspace.
                            </Alert>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>

                    <Stack spacing={1.5}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Assigned deployments
                      </Typography>
                      {selectedUser.assignedDeployments.map((assignment) => (
                        <Card key={assignment.assignmentId} variant="outlined" sx={{ borderRadius: 3 }}>
                          <CardContent>
                            <Stack spacing={1.25}>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                spacing={1}
                                justifyContent="space-between"
                                alignItems={{ xs: 'flex-start', md: 'center' }}
                              >
                                <Box>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                    {assignment.deploymentName}
                                  </Typography>
                                  <Typography variant="body2" color="text.secondary">
                                    {assignment.deploymentEnvironment}
                                  </Typography>
                                </Box>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Chip label={assignment.assignmentRole} color={roleChipColor(assignment.assignmentRole)} />
                                  <Chip label={assignment.deploymentStatus} variant="outlined" />
                                </Stack>
                              </Stack>
                              <Grid container spacing={1.5}>
                                <Grid item xs={12} md={4}>
                                  <Typography variant="caption" color="text.secondary">Granted</Typography>
                                  <Typography variant="body2">{formatTimestamp(assignment.createdAt)}</Typography>
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <Typography variant="caption" color="text.secondary">Updated</Typography>
                                  <Typography variant="body2">{formatTimestamp(assignment.updatedAt)}</Typography>
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <Stack direction="row" justifyContent={{ xs: 'flex-start', md: 'flex-end' }}>
                                    <Button
                                      component={RouterLink}
                                      to={`/revisions?deploymentId=${encodeURIComponent(assignment.deploymentId)}`}
                                      variant="outlined"
                                      startIcon={<OpenInNewRoundedIcon />}
                                    >
                                      Open deployment
                                    </Button>
                                  </Stack>
                                </Grid>
                              </Grid>
                            </Stack>
                          </CardContent>
                        </Card>
                      ))}
                      {selectedUser.assignedDeployments.length === 0 ? (
                        <Alert severity="info">
                          This user does not have any explicit deployment assignments yet.
                        </Alert>
                      ) : null}
                    </Stack>
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <ManageAccountsRoundedIcon color="primary" />
              <Typography variant="h6">User directory</Typography>
            </Stack>
            <Stack spacing={1.5}>
              {users.map((user) => {
                const selected = selectedUser?.id === user.id
                return (
                  <Card
                    key={user.id}
                    variant="outlined"
                    sx={{
                      borderRadius: 3,
                      borderColor: selected ? 'primary.main' : 'divider',
                      boxShadow: selected ? '0 0 0 1px rgba(25, 118, 210, 0.08)' : 'none',
                    }}
                  >
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
                              {user.displayName}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {user.email}
                            </Typography>
                          </Box>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip label={user.role} color={roleChipColor(user.role)} />
                            <Chip label={user.status} color={statusChipColor(user.status)} variant="outlined" />
                            <Chip label={`${user.assignmentCount} assignments`} variant="outlined" />
                            {user.selectedDeploymentAssignment ? (
                              <Chip
                                label={`${workspace?.deployment.name ?? 'Selected deployment'}: ${user.selectedDeploymentAssignment.assignmentRole}`}
                                color={roleChipColor(user.selectedDeploymentAssignment.assignmentRole)}
                                variant="outlined"
                              />
                            ) : null}
                          </Stack>
                        </Stack>
                        <Grid container spacing={1.5}>
                          <Grid item xs={12} md={3}>
                            <Typography variant="caption" color="text.secondary">Last login</Typography>
                            <Typography variant="body2">{formatTimestamp(user.lastLoginAt)}</Typography>
                          </Grid>
                          <Grid item xs={12} md={3}>
                            <Typography variant="caption" color="text.secondary">Created</Typography>
                            <Typography variant="body2">{formatTimestamp(user.createdAt)}</Typography>
                          </Grid>
                          <Grid item xs={12} md={3}>
                            <Typography variant="caption" color="text.secondary">Selected deployment</Typography>
                            <Typography variant="body2">
                              {user.selectedDeploymentAssignment?.assignmentRole ?? 'No access'}
                            </Typography>
                          </Grid>
                          <Grid item xs={12} md={3}>
                            <Stack direction="row" justifyContent={{ xs: 'flex-start', md: 'flex-end' }}>
                              <Button
                                variant={selected ? 'contained' : 'outlined'}
                                onClick={() => setSelectedUserId(user.id)}
                              >
                                {selected ? 'Selected' : 'Open workspace'}
                              </Button>
                            </Stack>
                          </Grid>
                        </Grid>
                      </Stack>
                    </CardContent>
                  </Card>
                )
              })}
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Dialog open={editingUser != null} onClose={() => setEditingUser(null)} fullWidth maxWidth="sm">
        <DialogTitle>Edit platform user</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="Display name"
              value={editForm.displayName}
              onChange={(event) => setEditForm((current) => ({ ...current, displayName: event.target.value }))}
            />
            <TextField
              select
              label="Role"
              value={editForm.role}
              onChange={(event) => setEditForm((current) => ({ ...current, role: event.target.value }))}
            >
              <MenuItem value="PLATFORM_OPERATOR">Platform operator</MenuItem>
              <MenuItem value="PLATFORM_ADMIN">Platform admin</MenuItem>
            </TextField>
            <TextField
              select
              label="Status"
              value={editForm.status}
              onChange={(event) => setEditForm((current) => ({ ...current, status: event.target.value }))}
            >
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="DISABLED">Disabled</MenuItem>
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingUser(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={updateMutation.isPending}
            onClick={() => updateMutation.mutate()}
          >
            Save user
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={resetUser != null} onClose={() => setResetUser(null)} fullWidth maxWidth="sm">
        <DialogTitle>Reset user password</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Set a new password for {resetUser?.email}. This affects platform sign-in immediately.
            </Typography>
            <TextField
              label="New password"
              type="password"
              value={resetPassword}
              onChange={(event) => setResetPassword(event.target.value)}
              helperText="Minimum 10 characters."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setResetUser(null)
            setResetPassword('')
          }}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={resetPasswordMutation.isPending}
            onClick={() => resetPasswordMutation.mutate()}
          >
            Reset password
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
