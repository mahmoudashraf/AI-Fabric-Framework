import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import GroupRoundedIcon from '@mui/icons-material/GroupRounded'
import LockPersonRoundedIcon from '@mui/icons-material/LockPersonRounded'
import PersonAddAltRoundedIcon from '@mui/icons-material/PersonAddAltRounded'
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
import { useMemo, useState } from 'react'
import {
  deleteDeploymentAssignment,
  fetchDeploymentAssignments,
  fetchPlatformUsers,
  upsertDeploymentAssignment,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function roleChipColor(role: string): 'secondary' | 'primary' | 'default' {
  if (role === 'DEPLOYMENT_ADMIN') {
    return 'secondary'
  }
  if (role === 'DEPLOYMENT_EDITOR' || role === 'DEPLOYMENT_OPERATOR') {
    return 'primary'
  }
  return 'default'
}

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

export function AccessPage() {
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const canManageAssignments = auth.session?.enabled ? auth.session.canManageUsers : true
  const [selectedUserId, setSelectedUserId] = useState('')
  const [assignmentRole, setAssignmentRole] = useState('DEPLOYMENT_OPERATOR')
  const [notice, setNotice] = useState<string | null>(null)

  const assignmentsQuery = useQuery({
    queryKey: ['deployment-assignments', selectedDeploymentId],
    queryFn: () => fetchDeploymentAssignments(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const usersQuery = useQuery({
    queryKey: ['platform-users', 'assignment-options'],
    queryFn: fetchPlatformUsers,
    enabled: canManageAssignments,
  })

  const assignMutation = useMutation({
    mutationFn: () => upsertDeploymentAssignment(selectedDeploymentId, {
      userId: selectedUserId,
      assignmentRole,
    }),
    onSuccess: async (assignment) => {
      setNotice(`${assignment.userEmail} now has ${assignment.assignmentRole} access.`)
      setSelectedUserId('')
      setAssignmentRole('DEPLOYMENT_OPERATOR')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-assignments', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
      ])
    },
  })

  const removeMutation = useMutation({
    mutationFn: (assignmentId: string) => deleteDeploymentAssignment(selectedDeploymentId, assignmentId),
    onSuccess: async () => {
      setNotice('Deployment assignment removed.')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-assignments', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
      ])
    },
  })

  const assignments = assignmentsQuery.data ?? []
  const assignableUsers = useMemo(() => (
    (usersQuery.data ?? []).filter((user) => user.status === 'ACTIVE')
  ), [usersQuery.data])
  const metrics = useMemo(() => ({
    total: assignments.length,
    admins: assignments.filter((assignment) => assignment.assignmentRole === 'DEPLOYMENT_ADMIN').length,
    operators: assignments.filter((assignment) => assignment.assignmentRole === 'DEPLOYMENT_OPERATOR').length,
    viewers: assignments.filter((assignment) => assignment.assignmentRole === 'DEPLOYMENT_VIEWER').length,
  }), [assignments])

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Access" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Deployment access and assignments
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Deployment assignments now define which deployments platform operators can even see. This is the first
          visibility boundary for enterprise access, before finer deployment-role enforcement is layered in.
        </Typography>
      </Box>

      {notice ? <Alert severity="success">{notice}</Alert> : null}
      {assignmentsQuery.error instanceof Error ? <Alert severity="error">{assignmentsQuery.error.message}</Alert> : null}
      {assignMutation.error instanceof Error ? <Alert severity="error">{assignMutation.error.message}</Alert> : null}
      {removeMutation.error instanceof Error ? <Alert severity="error">{removeMutation.error.message}</Alert> : null}

      {!selectedDeploymentId ? (
        <Alert severity="info">
          Select a deployment in the workspace header first.
        </Alert>
      ) : null}

      {workspace ? (
        <Grid container spacing={2.5}>
          <Grid item xs={12} md={3}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Assignments</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.total}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={3}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Deployment admins</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.admins}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={3}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Deployment operators</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.operators}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={3}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Typography variant="overline" color="text.secondary">Viewers</Typography>
                <Typography variant="h4" sx={{ fontWeight: 800 }}>{metrics.viewers}</Typography>
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
                  <PersonAddAltRoundedIcon color="primary" />
                  <Typography variant="h6">Assign user</Typography>
                </Stack>

                {canManageAssignments ? (
                  <>
                    <TextField
                      select
                      label="Platform user"
                      value={selectedUserId}
                      onChange={(event) => setSelectedUserId(event.target.value)}
                      disabled={selectedDeploymentId.length === 0}
                    >
                      <MenuItem value="">Select user</MenuItem>
                      {assignableUsers.map((user) => (
                        <MenuItem key={user.id} value={user.id}>
                          {user.displayName} ({user.email})
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField
                      select
                      label="Assignment role"
                      value={assignmentRole}
                      onChange={(event) => setAssignmentRole(event.target.value)}
                    >
                      <MenuItem value="DEPLOYMENT_ADMIN">Deployment admin</MenuItem>
                      <MenuItem value="DEPLOYMENT_EDITOR">Deployment editor</MenuItem>
                      <MenuItem value="DEPLOYMENT_OPERATOR">Deployment operator</MenuItem>
                      <MenuItem value="DEPLOYMENT_VIEWER">Deployment viewer</MenuItem>
                    </TextField>
                    <Button
                      variant="contained"
                      startIcon={<LockPersonRoundedIcon />}
                      disabled={assignMutation.isPending || selectedDeploymentId.length === 0 || selectedUserId.length === 0}
                      onClick={() => {
                        setNotice(null)
                        assignMutation.mutate()
                      }}
                    >
                      Save assignment
                    </Button>
                  </>
                ) : (
                  <Alert severity="info">
                    Assignment changes require the <code>PLATFORM_ADMIN</code> role. Operators can view only the
                    assignments for deployments they already have access to.
                  </Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <GroupRoundedIcon color="primary" />
                  <Typography variant="h6">Assigned users</Typography>
                </Stack>
                <Stack spacing={1.5}>
                  {assignments.map((assignment) => (
                    <Card key={assignment.id} variant="outlined" sx={{ borderRadius: 3 }}>
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
                                {assignment.userDisplayName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {assignment.userEmail}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip label={assignment.platformRole} variant="outlined" />
                              <Chip label={assignment.assignmentRole} color={roleChipColor(assignment.assignmentRole)} />
                            </Stack>
                          </Stack>
                          <Grid container spacing={1.5}>
                            <Grid item xs={12} md={6}>
                              <Typography variant="caption" color="text.secondary">Granted</Typography>
                              <Typography variant="body2">{formatTimestamp(assignment.createdAt)}</Typography>
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <Typography variant="caption" color="text.secondary">Updated</Typography>
                              <Typography variant="body2">{formatTimestamp(assignment.updatedAt)}</Typography>
                            </Grid>
                          </Grid>
                          {canManageAssignments ? (
                            <Stack direction="row" justifyContent="flex-end">
                              <Button
                                variant="outlined"
                                color="error"
                                startIcon={<DeleteOutlineRoundedIcon />}
                                disabled={removeMutation.isPending}
                                onClick={() => {
                                  setNotice(null)
                                  removeMutation.mutate(assignment.id)
                                }}
                              >
                                Remove assignment
                              </Button>
                            </Stack>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                  {assignments.length === 0 ? (
                    <Alert severity="info">
                      No explicit deployment assignments yet. Admins still see all deployments globally.
                    </Alert>
                  ) : null}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  )
}
