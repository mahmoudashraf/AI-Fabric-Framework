import { Alert, Box, Button, Card, CardContent, Chip, Divider, Grid, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import {
  fetchDeploymentActivity,
  fetchPlatformUserPreferences,
  updatePlatformUserPreferences,
  type DeploymentActivityViewPreferences,
  type PlatformAuditEventSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useEffect, useMemo, useRef, useState } from 'react'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function categoryForEvent(event: PlatformAuditEventSummary): string {
  const action = event.action.toUpperCase()
  if (action.includes('APPROVAL')) {
    return 'Approval'
  }
  if (action.includes('ASSIGNMENT') || action.includes('ACCESS')) {
    return 'Access'
  }
  if (action.includes('PUBLISH') || action.includes('APPLY') || action.includes('VERIFICATION') || action.includes('RELEASE')) {
    return 'Release'
  }
  if (action.includes('POC') || action.includes('PROMPT')) {
    return 'POC'
  }
  if (action.includes('ARCHIVE') || action.includes('DELETE') || action.includes('RESTORE')) {
    return 'Lifecycle'
  }
  return 'Configuration'
}

function categoryColor(
  category: string,
): 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'info' {
  switch (category) {
    case 'Approval':
      return 'warning'
    case 'Access':
      return 'secondary'
    case 'Release':
      return 'primary'
    case 'POC':
      return 'info'
    case 'Lifecycle':
      return 'warning'
    default:
      return 'default'
  }
}

function roleColor(
  role: string,
): 'default' | 'primary' | 'secondary' | 'success' | 'warning' | 'info' {
  switch (role) {
    case 'PLATFORM_ADMIN':
      return 'secondary'
    case 'PLATFORM_OPERATOR':
      return 'primary'
    case 'SYSTEM':
      return 'info'
    default:
      return 'default'
  }
}

function entriesForDetails(details: unknown): Array<[string, string]> {
  if (!details || typeof details !== 'object' || Array.isArray(details)) {
    return []
  }

  return Object.entries(details as Record<string, unknown>)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .slice(0, 8)
    .map(([key, value]) => [
      key,
      typeof value === 'string'
        ? value
        : JSON.stringify(value),
    ])
}

function normalizedText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
}

function activityViewEquals(
  saved: DeploymentActivityViewPreferences | null | undefined,
  current: DeploymentActivityViewPreferences,
): boolean {
  if (!saved) {
    return false
  }
  return saved.categoryFilter === current.categoryFilter
    && saved.actorRoleFilter === current.actorRoleFilter
    && saved.searchTerm === current.searchTerm
}

export function ActivityPage() {
  const queryClient = useQueryClient()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const [categoryFilter, setCategoryFilter] = useState('ALL')
  const [actorRoleFilter, setActorRoleFilter] = useState('ALL')
  const [searchTerm, setSearchTerm] = useState('')
  const viewInitializedRef = useRef(false)
  const viewHydrationRef = useRef(false)

  const activityQuery = useQuery({
    queryKey: ['deployment-activity', selectedDeploymentId],
    queryFn: () => fetchDeploymentActivity(selectedDeploymentId, 100),
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
    const savedView = preferencesQuery.data?.deploymentActivityView
    if (savedView) {
      setCategoryFilter(savedView.categoryFilter ?? 'ALL')
      setActorRoleFilter(savedView.actorRoleFilter ?? 'ALL')
      setSearchTerm(savedView.searchTerm ?? '')
    }
    viewHydrationRef.current = true
    viewInitializedRef.current = true
  }, [preferencesQuery.data, preferencesQuery.isSuccess])

  const events = activityQuery.data ?? []
  const activityView = useMemo<DeploymentActivityViewPreferences>(() => ({
    categoryFilter,
    actorRoleFilter,
    searchTerm,
  }), [actorRoleFilter, categoryFilter, searchTerm])
  const viewMatchesSaved = useMemo(
    () => activityViewEquals(preferencesQuery.data?.deploymentActivityView, activityView),
    [activityView, preferencesQuery.data?.deploymentActivityView],
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
      updatePreferencesMutation.mutate({ deploymentActivityView: activityView })
    }, 600)
    return () => window.clearTimeout(handle)
  }, [
    activityView,
    preferencesQuery.isSuccess,
    updatePreferencesMutation,
    viewMatchesSaved,
  ])

  const filteredEvents = useMemo(() => {
    const searchValue = normalizedText(searchTerm)
    return events.filter((event) => {
      const category = categoryForEvent(event).toUpperCase()
      const matchesCategory = categoryFilter === 'ALL' || category === categoryFilter
      const matchesActor = actorRoleFilter === 'ALL' || event.actorRole === actorRoleFilter
      const matchesSearch = searchValue.length === 0
        || normalizedText(event.action).includes(searchValue)
        || normalizedText(event.actorId).includes(searchValue)
        || normalizedText(event.targetType).includes(searchValue)
        || normalizedText(event.targetId).includes(searchValue)
      return matchesCategory && matchesActor && matchesSearch
    })
  }, [actorRoleFilter, categoryFilter, events, searchTerm])

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Activity" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Deployment activity timeline
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Review the operational history for this deployment only, including release requests,
          approvals, assignment changes, prompt sessions, and lifecycle actions.
        </Typography>
      </Box>

      {!selectedDeploymentId ? (
        <Alert severity="info">Select a deployment to view its recent operational activity.</Alert>
      ) : (
        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={2}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', md: 'center' }}
              >
                <Box>
                  <Typography variant="h6">Recent deployment events</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    {workspace
                      ? `Showing the latest activity recorded for ${workspace.deployment.name}.`
                      : 'Showing the latest activity recorded for the selected deployment.'}
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                  <Chip label={`Visible events: ${filteredEvents.length}`} variant="outlined" />
                  <Button
                    variant="outlined"
                    startIcon={<SyncRoundedIcon />}
                    onClick={() => queryClient.invalidateQueries({ queryKey: ['deployment-activity', selectedDeploymentId] })}
                    disabled={activityQuery.isFetching}
                  >
                    {activityQuery.isFetching ? 'Refreshing...' : 'Refresh'}
                  </Button>
                </Stack>
              </Stack>

              <Grid container spacing={1.5}>
                <Grid item xs={12} md={4}>
                  <TextField
                    fullWidth
                    label="Search activity"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                    helperText="Matches action, actor, or target"
                  />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    fullWidth
                    select
                    label="Category"
                    value={categoryFilter}
                    onChange={(event) => setCategoryFilter(event.target.value)}
                  >
                    <MenuItem value="ALL">All categories</MenuItem>
                    <MenuItem value="APPROVAL">Approval</MenuItem>
                    <MenuItem value="ACCESS">Access</MenuItem>
                    <MenuItem value="RELEASE">Release</MenuItem>
                    <MenuItem value="POC">POC</MenuItem>
                    <MenuItem value="LIFECYCLE">Lifecycle</MenuItem>
                    <MenuItem value="CONFIGURATION">Configuration</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField
                    fullWidth
                    select
                    label="Actor role"
                    value={actorRoleFilter}
                    onChange={(event) => setActorRoleFilter(event.target.value)}
                  >
                    <MenuItem value="ALL">All roles</MenuItem>
                    <MenuItem value="PLATFORM_ADMIN">Platform Admin</MenuItem>
                    <MenuItem value="PLATFORM_OPERATOR">Platform Operator</MenuItem>
                    <MenuItem value="PUBLIC_API_CLIENT">Public API Client</MenuItem>
                    <MenuItem value="SYSTEM">System</MenuItem>
                  </TextField>
                </Grid>
              </Grid>

              <Stack
                direction={{ xs: 'column', md: 'row' }}
                spacing={1}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', md: 'center' }}
              >
                <Typography variant="caption" color="text.secondary">
                  {preferencesQuery.isSuccess
                    ? 'Activity filters are saved automatically for your operator account.'
                    : 'Activity filter sync is unavailable. Filters stay local to this session.'}
                </Typography>
                {updatePreferencesMutation.isPending ? (
                  <Chip label="Saving filters…" size="small" color="info" />
                ) : (preferencesQuery.isSuccess && viewMatchesSaved ? (
                  <Chip label="Filters synced" size="small" color="success" />
                ) : null)}
              </Stack>

              {activityQuery.isLoading ? (
                <Typography color="text.secondary">Loading deployment activity...</Typography>
              ) : activityQuery.isError ? (
                <Alert severity="error">
                  {activityQuery.error instanceof Error
                    ? activityQuery.error.message
                    : 'Failed to load deployment activity.'}
                </Alert>
              ) : events.length === 0 ? (
                <Alert severity="info">No activity has been recorded for this deployment yet.</Alert>
              ) : filteredEvents.length === 0 ? (
                <Alert severity="info">No activity matches the current filters.</Alert>
              ) : (
                <Stack spacing={2}>
                  {filteredEvents.map((event) => {
                    const category = categoryForEvent(event)
                    const detailEntries = entriesForDetails(event.details)
                    return (
                      <Card
                        key={event.id}
                        sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', bgcolor: 'background.default' }}
                      >
                        <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                          <Stack spacing={1.5}>
                            <Stack
                              direction={{ xs: 'column', md: 'row' }}
                              spacing={1.25}
                              justifyContent="space-between"
                              alignItems={{ xs: 'flex-start', md: 'center' }}
                            >
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Chip label={category} color={categoryColor(category)} size="small" />
                                <Chip label={event.actorRole} color={roleColor(event.actorRole)} variant="outlined" size="small" />
                                <Chip label={event.targetType} variant="outlined" size="small" />
                              </Stack>
                              <Typography variant="caption" color="text.secondary">
                                {formatTimestamp(event.createdAt)}
                              </Typography>
                            </Stack>

                            <Box>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                {event.action}
                              </Typography>
                              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                Actor: {event.actorId}
                              </Typography>
                            </Box>

                            <Stack spacing={0.5}>
                              <Typography variant="caption" color="text.secondary">
                                Target
                              </Typography>
                              <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                                {event.targetId}
                              </Typography>
                            </Stack>

                            {detailEntries.length > 0 ? (
                              <>
                                <Divider />
                                <Stack spacing={0.75}>
                                  <Typography variant="caption" color="text.secondary">
                                    Details
                                  </Typography>
                                  {detailEntries.map(([key, value]) => (
                                    <Stack
                                      key={`${event.id}-${key}`}
                                      direction={{ xs: 'column', md: 'row' }}
                                      spacing={1}
                                      justifyContent="space-between"
                                    >
                                      <Typography variant="body2" color="text.secondary">
                                        {key}
                                      </Typography>
                                      <Typography variant="body2" sx={{ fontFamily: 'monospace', maxWidth: 720 }}>
                                        {value}
                                      </Typography>
                                    </Stack>
                                  ))}
                                </Stack>
                              </>
                            ) : null}
                          </Stack>
                        </CardContent>
                      </Card>
                    )
                  })}
                </Stack>
              )}
            </Stack>
          </CardContent>
        </Card>
      )}
    </Stack>
  )
}
