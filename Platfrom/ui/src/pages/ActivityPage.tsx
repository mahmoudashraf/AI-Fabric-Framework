import { Alert, Box, Button, Card, CardContent, Chip, Divider, Stack, Typography } from '@mui/material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import {
  fetchDeploymentActivity,
  type PlatformAuditEventSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

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

export function ActivityPage() {
  const queryClient = useQueryClient()
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()

  const activityQuery = useQuery({
    queryKey: ['deployment-activity', selectedDeploymentId],
    queryFn: () => fetchDeploymentActivity(selectedDeploymentId, 100),
    enabled: selectedDeploymentId.length > 0,
  })

  const events = activityQuery.data ?? []

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
                  <Chip label={`Events: ${events.length}`} variant="outlined" />
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
              ) : (
                <Stack spacing={2}>
                  {events.map((event) => {
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
