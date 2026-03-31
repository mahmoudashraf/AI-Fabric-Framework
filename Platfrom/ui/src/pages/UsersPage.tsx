import EditRoundedIcon from '@mui/icons-material/EditRounded'
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import PersonAddRoundedIcon from '@mui/icons-material/PersonAddRounded'
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
import {
  createPlatformUser,
  fetchPlatformUsers,
  resetPlatformUserPassword,
  updatePlatformUser,
  type PlatformUserSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

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
  if (role === 'PLATFORM_ADMIN') {
    return 'secondary'
  }
  if (role === 'PLATFORM_OPERATOR') {
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
  const canManageUsers = auth.session?.enabled ? auth.session.canManageUsers : true
  const [notice, setNotice] = useState<string | null>(null)
  const [createForm, setCreateForm] = useState<CreateUserFormState>({
    email: '',
    displayName: '',
    password: '',
    role: 'PLATFORM_OPERATOR',
  })
  const [editingUser, setEditingUser] = useState<PlatformUserSummary | null>(null)
  const [editForm, setEditForm] = useState<EditUserFormState>({
    displayName: '',
    role: 'PLATFORM_OPERATOR',
    status: 'ACTIVE',
  })
  const [resetUser, setResetUser] = useState<PlatformUserSummary | null>(null)
  const [resetPassword, setResetPassword] = useState('')

  const usersQuery = useQuery({
    queryKey: ['platform-users'],
    queryFn: fetchPlatformUsers,
    enabled: canManageUsers,
  })

  const users = usersQuery.data ?? []
  const metrics = useMemo(() => ({
    total: users.length,
    admins: users.filter((user) => user.role === 'PLATFORM_ADMIN' && user.status === 'ACTIVE').length,
    operators: users.filter((user) => user.role === 'PLATFORM_OPERATOR' && user.status === 'ACTIVE').length,
    disabled: users.filter((user) => user.status === 'DISABLED').length,
  }), [users])

  useEffect(() => {
    if (editingUser) {
      setEditForm({
        displayName: editingUser.displayName,
        role: editingUser.role,
        status: editingUser.status,
      })
    }
  }, [editingUser])

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
      await queryClient.invalidateQueries({ queryKey: ['platform-users'] })
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
      await queryClient.invalidateQueries({ queryKey: ['platform-users'] })
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
      await queryClient.invalidateQueries({ queryKey: ['platform-users'] })
    },
  })

  if (!canManageUsers) {
    return (
      <Stack spacing={3}>
        <Box>
          <Chip label="Users" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
            Platform user administration
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
        <Chip label="Users" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Platform user administration
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Manage platform admins and operators from one place. This is the foundation for enterprise access
          administration before deployment-level assignments are layered on top.
        </Typography>
      </Box>

      {notice ? <Alert severity="success">{notice}</Alert> : null}
      {usersQuery.error instanceof Error ? <Alert severity="error">{usersQuery.error.message}</Alert> : null}
      {createMutation.error instanceof Error ? <Alert severity="error">{createMutation.error.message}</Alert> : null}
      {updateMutation.error instanceof Error ? <Alert severity="error">{updateMutation.error.message}</Alert> : null}
      {resetPasswordMutation.error instanceof Error ? <Alert severity="error">{resetPasswordMutation.error.message}</Alert> : null}

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
              <Typography variant="overline" color="text.secondary">Active operators</Typography>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.operators}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={3}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Typography variant="overline" color="text.secondary">Disabled</Typography>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.disabled}</Typography>
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
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <ManageAccountsRoundedIcon color="primary" />
                  <Typography variant="h6">Current users</Typography>
                </Stack>
                <Stack spacing={1.5}>
                  {users.map((user) => (
                    <Card key={user.id} variant="outlined" sx={{ borderRadius: 3 }}>
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
                            </Stack>
                          </Stack>
                          <Grid container spacing={1.5}>
                            <Grid item xs={12} md={4}>
                              <Typography variant="caption" color="text.secondary">Last login</Typography>
                              <Typography variant="body2">{formatTimestamp(user.lastLoginAt)}</Typography>
                            </Grid>
                            <Grid item xs={12} md={4}>
                              <Typography variant="caption" color="text.secondary">Created</Typography>
                              <Typography variant="body2">{formatTimestamp(user.createdAt)}</Typography>
                            </Grid>
                            <Grid item xs={12} md={4}>
                              <Typography variant="caption" color="text.secondary">Updated</Typography>
                              <Typography variant="body2">{formatTimestamp(user.updatedAt)}</Typography>
                            </Grid>
                          </Grid>
                          <Stack direction="row" spacing={1} justifyContent="flex-end" flexWrap="wrap" useFlexGap>
                            <Button
                              variant="outlined"
                              startIcon={<EditRoundedIcon />}
                              onClick={() => {
                                setNotice(null)
                                setEditingUser(user)
                              }}
                            >
                              Edit user
                            </Button>
                            <Button
                              variant="outlined"
                              startIcon={<LockResetRoundedIcon />}
                              onClick={() => {
                                setNotice(null)
                                setResetUser(user)
                              }}
                            >
                              Reset password
                            </Button>
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

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
