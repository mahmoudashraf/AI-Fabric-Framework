import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import PublishRoundedIcon from '@mui/icons-material/PublishRounded'
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  applyDeploymentVersion,
  fetchDeploymentDraft,
  fetchDeploymentReleases,
  fetchDeployments,
  fetchDeploymentVersions,
  publishDeploymentDraft,
  type DeploymentDraftResponse,
} from '../api/platformApi'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function countActions(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }

  const actions = config.actions
  return Array.isArray(actions) ? actions.length : 0
}

function countEntitySpaces(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }

  const entities = config['ai-entities']
  return isRecord(entities) ? Object.keys(entities).length : 0
}

function countRoutingActions(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }

  const actions = config.actions
  return isRecord(actions) ? Object.keys(actions).length : 0
}

function readString(config: unknown, key: string): string {
  if (!isRecord(config)) {
    return 'Not configured'
  }

  const value = config[key]
  return typeof value === 'string' && value.length > 0 ? value : 'Not configured'
}

function readBoolean(config: unknown, key: string): string {
  if (!isRecord(config)) {
    return 'Unknown'
  }

  const value = config[key]
  return typeof value === 'boolean' ? String(value) : 'Unknown'
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function summarizeDraft(draft: DeploymentDraftResponse | undefined) {
  if (!draft) {
    return {
      actions: 0,
      entitySpaces: 0,
      routingActions: 0,
      llmProvider: 'Not configured',
      vectorStrategy: 'Not configured',
      authzMode: 'Not configured',
      adminApiKeyEnabled: 'Unknown',
    }
  }

  return {
    actions: countActions(draft.actionsConfig),
    entitySpaces: countEntitySpaces(draft.entityConfig),
    routingActions: countRoutingActions(draft.routingConfig),
    llmProvider: readString(draft.providerConfig, 'llmProvider'),
    vectorStrategy: readString(draft.providerConfig, 'vectorStrategy'),
    authzMode: readString(draft.securityConfig, 'authzMode'),
    adminApiKeyEnabled: readBoolean(draft.securityConfig, 'adminApiKeyEnabled'),
  }
}

export function RevisionsPage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })

  const deployments = deploymentsQuery.data ?? []

  useEffect(() => {
    if (deployments.length === 0) {
      if (selectedDeploymentId !== '') {
        setSelectedDeploymentId('')
      }
      return
    }

    if (!deployments.some((deployment) => deployment.id === selectedDeploymentId)) {
      setSelectedDeploymentId(deployments[0].id)
    }
  }, [deployments, selectedDeploymentId])

  const selectedDeployment = useMemo(
    () => deployments.find((deployment) => deployment.id === selectedDeploymentId) ?? null,
    [deployments, selectedDeploymentId],
  )

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const versionsQuery = useQuery({
    queryKey: ['deployment-versions', selectedDeploymentId],
    queryFn: () => fetchDeploymentVersions(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const publishMutation = useMutation({
    mutationFn: (draftId: string) => publishDeploymentDraft(draftId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-versions', selectedDeploymentId] }),
      ])
    },
  })

  const applyMutation = useMutation({
    mutationFn: ({ deploymentId, versionId }: { deploymentId: string; versionId: string }) =>
      applyDeploymentVersion(deploymentId, versionId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] }),
      ])
    },
  })

  const draftSummary = summarizeDraft(draftQuery.data)

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Revisions" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Draft, publish, apply
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          Phase 2 exposes the real release lifecycle: each deployment has an editable active draft,
          immutable published versions, and applied release history. Railway provisioning is still
          the next phase, but the platform state model is now real.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Choose the deployment whose revision lifecycle you want to inspect.
              </Typography>
            </Box>

            <TextField
              select
              label="Deployment"
              value={selectedDeploymentId}
              onChange={(event) => setSelectedDeploymentId(event.target.value)}
              disabled={deployments.length === 0}
            >
              {deployments.map((deployment) => (
                <MenuItem key={deployment.id} value={deployment.id}>
                  {deployment.name} ({deployment.environment})
                </MenuItem>
              ))}
            </TextField>

            {selectedDeployment ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={selectedDeployment.status} color="primary" />
                <Chip label={`Active: ${selectedDeployment.activeVersion}`} variant="outlined" />
                <Chip label={selectedDeployment.templateId} variant="outlined" />
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to start managing revisions.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeployment ? (
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Active draft</Typography>
                    {draftQuery.isLoading ? (
                      <Typography color="text.secondary">Loading draft...</Typography>
                    ) : draftQuery.isError ? (
                      <Alert severity="error">
                        {draftQuery.error instanceof Error
                          ? draftQuery.error.message
                          : 'Failed to load draft'}
                      </Alert>
                    ) : draftQuery.data ? (
                      <>
                        <Stack spacing={0.5}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {draftQuery.data.id}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Revision {draftQuery.data.revisionNumber} · {draftQuery.data.status}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Updated {formatTimestamp(draftQuery.data.updatedAt)}
                          </Typography>
                        </Stack>
                        <Button
                          variant="contained"
                          startIcon={<PublishRoundedIcon />}
                          disabled={publishMutation.isPending}
                          onClick={() => publishMutation.mutate(draftQuery.data!.id)}
                        >
                          {publishMutation.isPending ? 'Publishing...' : 'Publish draft'}
                        </Button>
                        {publishMutation.isError ? (
                          <Alert severity="error">
                            {publishMutation.error instanceof Error
                              ? publishMutation.error.message
                              : 'Failed to publish draft'}
                          </Alert>
                        ) : null}
                      </>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={8}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Draft snapshot</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        This is the shape of the editable config that will be compiled into immutable
                        deployment artifacts on publish.
                      </Typography>
                    </Box>
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Actions
                            </Typography>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                              {draftSummary.actions}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Entity spaces
                            </Typography>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                              {draftSummary.entitySpaces}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Routed actions
                            </Typography>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                              {draftSummary.routingActions}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Authz mode
                            </Typography>
                            <Typography variant="body1" sx={{ fontWeight: 700 }}>
                              {draftSummary.authzMode}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Divider />

                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={`LLM: ${draftSummary.llmProvider}`} variant="outlined" />
                      <Chip label={`Vector: ${draftSummary.vectorStrategy}`} variant="outlined" />
                      <Chip label={`Admin key: ${draftSummary.adminApiKeyEnabled}`} variant="outlined" />
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Published versions</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Publishing compiles the draft into immutable artifacts and rotates the active draft
                    to the next revision.
                  </Typography>
                </Box>
                {versionsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading versions...</Typography>
                ) : versionsQuery.isError ? (
                  <Alert severity="error">
                    {versionsQuery.error instanceof Error
                      ? versionsQuery.error.message
                      : 'Failed to load versions'}
                  </Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Config hash</TableCell>
                        <TableCell>Reindex</TableCell>
                        <TableCell>Published</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(versionsQuery.data ?? []).map((version) => (
                        <TableRow key={version.id} hover>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {version.versionLabel}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {version.id}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color="primary"
                              icon={<CheckCircleRoundedIcon />}
                              label={version.status}
                            />
                          </TableCell>
                          <TableCell sx={{ fontFamily: 'monospace' }}>
                            {version.configHash.slice(0, 12)}...
                          </TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={version.reindexRequired ? 'warning' : 'success'}
                              icon={version.reindexRequired ? <WarningAmberRoundedIcon /> : <CheckCircleRoundedIcon />}
                              label={version.reindexRequired ? 'Required' : 'No'}
                            />
                          </TableCell>
                          <TableCell>{formatTimestamp(version.publishedAt)}</TableCell>
                          <TableCell align="right">
                            <Button
                              variant="outlined"
                              size="small"
                              startIcon={<RocketLaunchRoundedIcon />}
                              disabled={applyMutation.isPending}
                              onClick={() =>
                                applyMutation.mutate({
                                  deploymentId: selectedDeployment.id,
                                  versionId: version.id,
                                })
                              }
                            >
                              Apply
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
                {applyMutation.isError ? (
                  <Alert severity="error">
                    {applyMutation.error instanceof Error
                      ? applyMutation.error.message
                      : 'Failed to apply version'}
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Release history</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Releases are the applied deployment records. Verification remains a later phase,
                    so newly applied releases are still pending that step.
                  </Typography>
                </Box>
                {releasesQuery.isLoading ? (
                  <Typography color="text.secondary">Loading releases...</Typography>
                ) : releasesQuery.isError ? (
                  <Alert severity="error">
                    {releasesQuery.error instanceof Error
                      ? releasesQuery.error.message
                      : 'Failed to load releases'}
                  </Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Release</TableCell>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Verification</TableCell>
                        <TableCell>Applied</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(releasesQuery.data ?? []).map((release) => (
                        <TableRow key={release.id} hover>
                          <TableCell>{release.id}</TableCell>
                          <TableCell>{release.deploymentVersionId}</TableCell>
                          <TableCell>{release.status}</TableCell>
                          <TableCell>{release.verificationStatus}</TableCell>
                          <TableCell>{formatTimestamp(release.appliedAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  )
}
