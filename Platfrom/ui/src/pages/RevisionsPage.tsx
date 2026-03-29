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
import { useSearchParams } from 'react-router-dom'
import {
  applyDeploymentVersion,
  fetchDeploymentDraft,
  fetchDeploymentReleases,
  fetchDeployments,
  fetchDeploymentVersions,
  fetchRailwayProvisioningPlan,
  publishDeploymentDraft,
  updateDeploymentSource,
  type DeploymentDraftResponse,
  type DeploymentReleaseSummary,
  type RailwayEnvVarSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

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

function formatOptionalTimestamp(value: string | null | undefined): string {
  return value ? formatTimestamp(value) : '—'
}

function releaseStatusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'APPLIED_VERIFIED') {
    return 'success'
  }
  if (status === 'APPLY_REQUESTED' || status === 'PROVISIONING' || status === 'VERIFYING') {
    return 'info'
  }
  if (status === 'APPLIED_VERIFICATION_FAILED') {
    return 'warning'
  }
  if (status === 'FAILED') {
    return 'error'
  }
  return 'default'
}

function verificationStatusColor(status: string): 'success' | 'warning' | 'info' | 'default' {
  if (status === 'PASSED') {
    return 'success'
  }
  if (status === 'RUNNING') {
    return 'info'
  }
  if (status === 'FAILED') {
    return 'warning'
  }
  return 'default'
}

function provisioningStatusColor(status: string): 'success' | 'warning' | 'info' | 'default' {
  if (status === 'SUCCEEDED' || status === 'PLANNED') {
    return 'success'
  }
  if (status === 'QUEUED' || status === 'RUNNING') {
    return 'info'
  }
  if (status === 'FAILED') {
    return 'warning'
  }
  return 'default'
}

function isReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return ['APPLY_REQUESTED', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
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
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [selectedVersionId, setSelectedVersionId] = useState('')
  const [sourceRepositoryInput, setSourceRepositoryInput] = useState('')
  const [sourceBranchInput, setSourceBranchInput] = useState('')

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
  })

  const deployments = deploymentsQuery.data ?? []
  const requestedDeploymentId = searchParams.get('deploymentId') ?? ''

  useEffect(() => {
    if (deployments.length === 0) {
      if (selectedDeploymentId !== '') {
        setSelectedDeploymentId('')
      }
      return
    }

    const preferredDeploymentId =
      deployments.find((deployment) => deployment.status !== 'DRAFT')?.id ?? deployments[0].id

    if (
      requestedDeploymentId.length > 0
      && deployments.some((deployment) => deployment.id === requestedDeploymentId)
      && selectedDeploymentId !== requestedDeploymentId
    ) {
      setSelectedDeploymentId(requestedDeploymentId)
      return
    }

    if (!deployments.some((deployment) => deployment.id === selectedDeploymentId)) {
      setSelectedDeploymentId(preferredDeploymentId)
    }
  }, [deployments, requestedDeploymentId, selectedDeploymentId])

  useEffect(() => {
    const current = searchParams.get('deploymentId') ?? ''
    if (selectedDeploymentId.length === 0 || current === selectedDeploymentId) {
      return
    }
    const next = new URLSearchParams(searchParams)
    next.set('deploymentId', selectedDeploymentId)
    setSearchParams(next, { replace: true })
  }, [searchParams, selectedDeploymentId, setSearchParams])

  const selectedDeployment = useMemo(
    () => deployments.find((deployment) => deployment.id === selectedDeploymentId) ?? null,
    [deployments, selectedDeploymentId],
  )

  useEffect(() => {
    setSourceRepositoryInput(selectedDeployment?.source.repositoryOverride ?? '')
    setSourceBranchInput(selectedDeployment?.source.branchOverride ?? '')
  }, [selectedDeployment?.id, selectedDeployment?.source.branchOverride, selectedDeployment?.source.repositoryOverride])

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

  const versions = versionsQuery.data ?? []

  useEffect(() => {
    if (versions.length === 0) {
      if (selectedVersionId !== '') {
        setSelectedVersionId('')
      }
      return
    }

    if (!versions.some((version) => version.id === selectedVersionId)) {
      setSelectedVersionId(versions[0].id)
    }
  }, [selectedVersionId, versions])

  const railwayPlanQuery = useQuery({
    queryKey: ['deployment-railway-plan', selectedDeploymentId, selectedVersionId],
    queryFn: () => fetchRailwayProvisioningPlan(selectedDeploymentId, selectedVersionId),
    enabled: selectedDeploymentId.length > 0 && selectedVersionId.length > 0,
  })

  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: (query) => {
      const releases = (query.state.data as DeploymentReleaseSummary[] | undefined) ?? []
      return releases.some(isReleaseInProgress) ? 3000 : false
    },
  })

  const releaseHistory = releasesQuery.data ?? []
  const latestRelease = releaseHistory[0] ?? null
  const inProgressRelease = releaseHistory.find(isReleaseInProgress) ?? null

  useEffect(() => {
    if (!inProgressRelease) {
      return undefined
    }

    const intervalId = window.setInterval(() => {
      void queryClient.invalidateQueries({ queryKey: ['deployments'] })
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [inProgressRelease, queryClient])

  const publishMutation = useMutation({
    mutationFn: (draftId: string) => publishDeploymentDraft(draftId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-versions', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-railway-plan', selectedDeploymentId] }),
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
        queryClient.invalidateQueries({ queryKey: ['deployment-verification-runs', selectedDeploymentId] }),
      ])
    },
  })

  const updateSourceMutation = useMutation({
    mutationFn: ({
      deploymentId,
      repository,
      branch,
    }: {
      deploymentId: string
      repository?: string
      branch?: string
    }) => updateDeploymentSource(deploymentId, { repository, branch }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-railway-plan', selectedDeploymentId] }),
      ])
    },
  })

  const draftSummary = summarizeDraft(draftQuery.data)
  const isPlatformAdmin = auth.session?.role === 'PLATFORM_ADMIN'

  const selectedVersion = useMemo(
    () => versions.find((version) => version.id === selectedVersionId) ?? null,
    [selectedVersionId, versions],
  )

  const plan = railwayPlanQuery.data

  const renderEnvTable = (entries: RailwayEnvVarSummary[]) => (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Key</TableCell>
          <TableCell>Value</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {entries.map((entry) => (
          <TableRow key={entry.key} hover>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.key}</TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.value}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Revisions" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Draft, publish, apply
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          The release lifecycle now stores more than state transitions: publishing creates immutable
          versions, applying produces provisioning evidence, and each release can point to a stored
          verification run.
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
                <Chip label={`Source: ${selectedDeployment.source.branch}`} variant="outlined" />
                {inProgressRelease ? (
                  <Chip
                    label={`Release running: ${inProgressRelease.currentStepDescription ?? inProgressRelease.status}`}
                    color="info"
                  />
                ) : null}
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to start managing revisions.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeployment ? (
        <>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Deployment source</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    This controls which GitHub repo and branch Railway will deploy for this
                    deployment. Keep this admin-only. Customers should choose templates, not raw
                    branches.
                  </Typography>
                </Box>

                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip label={`Effective repo: ${selectedDeployment.source.repository}`} variant="outlined" />
                  <Chip label={`Effective branch: ${selectedDeployment.source.branch}`} variant="outlined" />
                  {selectedDeployment.source.overrideActive ? (
                    <Chip label="Override active" color="secondary" />
                  ) : (
                    <Chip label="Using platform default" variant="outlined" />
                  )}
                </Stack>

                {isPlatformAdmin ? (
                  <>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          label="Repository override"
                          value={sourceRepositoryInput}
                          onChange={(event) => setSourceRepositoryInput(event.target.value)}
                          fullWidth
                          helperText="Optional. Use owner/repo. Leave blank to keep the platform default repository."
                        />
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <TextField
                          label="Branch override"
                          value={sourceBranchInput}
                          onChange={(event) => setSourceBranchInput(event.target.value)}
                          fullWidth
                          helperText="Optional. Leave blank to keep the platform default branch."
                        />
                      </Grid>
                    </Grid>

                    <Stack direction="row" spacing={1}>
                      <Button
                        variant="contained"
                        disabled={updateSourceMutation.isPending}
                        onClick={() =>
                          updateSourceMutation.mutate({
                            deploymentId: selectedDeployment.id,
                            repository: sourceRepositoryInput,
                            branch: sourceBranchInput,
                          })}
                      >
                        {updateSourceMutation.isPending ? 'Saving source...' : 'Save source override'}
                      </Button>
                      <Button
                        variant="outlined"
                        color="inherit"
                        disabled={
                          updateSourceMutation.isPending
                          || (!selectedDeployment.source.repositoryOverride && !selectedDeployment.source.branchOverride)
                        }
                        onClick={() => {
                          setSourceRepositoryInput('')
                          setSourceBranchInput('')
                          updateSourceMutation.mutate({
                            deploymentId: selectedDeployment.id,
                            repository: '',
                            branch: '',
                          })
                        }}
                      >
                        Clear override
                      </Button>
                    </Stack>
                  </>
                ) : (
                  <Alert severity="info">
                    Changing deployment source is restricted to the <code>PLATFORM_ADMIN</code> role.
                  </Alert>
                )}

                {updateSourceMutation.isError ? (
                  <Alert severity="error">
                    {updateSourceMutation.error instanceof Error
                      ? updateSourceMutation.error.message
                      : 'Failed to update deployment source'}
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

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
                      {versions.map((version) => (
                        <TableRow
                          key={version.id}
                          hover
                          selected={version.id === selectedVersionId}
                          onClick={() => setSelectedVersionId(version.id)}
                          sx={{ cursor: 'pointer' }}
                        >
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
                              disabled={applyMutation.isPending || Boolean(inProgressRelease)}
                              onClick={(event) => {
                                event.stopPropagation()
                                applyMutation.mutate({
                                  deploymentId: selectedDeployment.id,
                                  versionId: version.id,
                                })
                              }}
                            >
                              {inProgressRelease ? 'Release running' : 'Apply'}
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
                {applyMutation.isSuccess ? (
                  <Alert severity="info">
                    Apply queued: {applyMutation.data.currentStepDescription ?? applyMutation.data.status}
                  </Alert>
                ) : null}
                {inProgressRelease ? (
                  <Alert severity="info">
                    A release is already running for this deployment. Status and current step refresh automatically.
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Latest release execution</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Applies now run asynchronously. This card shows the newest release, its current
                    step, and any error that blocked the rollout.
                  </Typography>
                </Box>
                {latestRelease ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={latestRelease.status} color={releaseStatusColor(latestRelease.status)} />
                      <Chip
                        label={`Provisioning: ${latestRelease.provisioningStatus}`}
                        color={provisioningStatusColor(latestRelease.provisioningStatus)}
                        variant="outlined"
                      />
                      <Chip
                        label={`Verification: ${latestRelease.verificationStatus}`}
                        color={verificationStatusColor(latestRelease.verificationStatus)}
                        variant="outlined"
                      />
                    </Stack>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Stack spacing={1}>
                          <Typography variant="body2">
                            Current step: <strong>{latestRelease.currentStepDescription ?? 'Not recorded yet'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Release: <strong>{latestRelease.id}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Version: <strong>{latestRelease.deploymentVersionId}</strong>
                          </Typography>
                        </Stack>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Stack spacing={1}>
                          <Typography variant="body2">
                            Updated: <strong>{formatOptionalTimestamp(latestRelease.updatedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Applied: <strong>{formatOptionalTimestamp(latestRelease.appliedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Verification run: <strong>{latestRelease.verificationRunId ?? 'Pending'}</strong>
                          </Typography>
                        </Stack>
                      </Grid>
                    </Grid>
                    {latestRelease.errorMessage ? (
                      <Alert severity="error">{latestRelease.errorMessage}</Alert>
                    ) : null}
                  </>
                ) : (
                  <Alert severity="info">No release has been applied yet for this deployment.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Railway plan preview</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    This preview shows what the platform would send to Railway for the selected version:
                    project naming, service roots, env vars, immutable artifact URLs, and rollout steps.
                  </Typography>
                </Box>

                {selectedVersion ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={selectedVersion.versionLabel} color="primary" />
                    <Chip label={selectedVersion.id} variant="outlined" />
                    <Chip label={selectedVersion.configHash.slice(0, 12)} variant="outlined" />
                  </Stack>
                ) : (
                  <Alert severity="info">Publish a version first to inspect the Railway plan.</Alert>
                )}

                {railwayPlanQuery.isLoading ? (
                  <Typography color="text.secondary">Loading Railway plan...</Typography>
                ) : railwayPlanQuery.isError ? (
                  <Alert severity="error">
                    {railwayPlanQuery.error instanceof Error
                      ? railwayPlanQuery.error.message
                      : 'Failed to load Railway plan'}
                  </Alert>
                ) : plan ? (
                  <>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="overline" color="text.secondary">
                                Project
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                {plan.projectName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {plan.repository} · {plan.branch}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                Mode: {plan.mode}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="overline" color="text.secondary">
                                Runtime service
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                {plan.services.runtime.serviceName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {plan.services.runtime.rootDir ?? 'repo root'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                Dockerfile: {plan.services.runtime.dockerfilePath ?? 'default'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                {plan.services.runtime.baseUrl}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="overline" color="text.secondary">
                                REST connector service
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                {plan.services.restConnector.serviceName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {plan.services.restConnector.rootDir ?? 'repo root'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                Dockerfile: {plan.services.restConnector.dockerfilePath ?? 'default'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                {plan.services.restConnector.baseUrl}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Artifact bundle</Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                actions: {plan.artifactUrls.actions}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                entities: {plan.artifactUrls.entities}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                routing: {plan.artifactUrls.routing}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                manifest: {plan.artifactUrls.manifest}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Rollout steps</Typography>
                              {plan.steps.map((step) => (
                                <Typography key={step.key} variant="body2">
                                  {step.order}. {step.description}
                                </Typography>
                              ))}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Runtime env</Typography>
                              {renderEnvTable(plan.services.runtime.env)}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">REST connector env</Typography>
                              {renderEnvTable(plan.services.restConnector.env)}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>
                  </>
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
                    Releases now capture provisioning status and verification linkage, so this table is
                    the first support-ready rollout evidence view.
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
                ) : releaseHistory.length === 0 ? (
                  <Alert severity="info">No release history exists yet for this deployment.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Release</TableCell>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Current step</TableCell>
                        <TableCell>Verification</TableCell>
                        <TableCell>Updated</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {releaseHistory.map((release) => (
                        <TableRow key={release.id} hover>
                          <TableCell>{release.id}</TableCell>
                          <TableCell>{release.deploymentVersionId}</TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip
                                size="small"
                                label={release.status}
                                color={releaseStatusColor(release.status)}
                              />
                              <Chip
                                size="small"
                                label={release.provisioningStatus}
                                color={provisioningStatusColor(release.provisioningStatus)}
                                variant="outlined"
                              />
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 320 }}>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">
                                {release.currentStepDescription ?? 'Not recorded yet'}
                              </Typography>
                              {release.errorMessage ? (
                                <Typography variant="caption" color="error.main">
                                  {release.errorMessage}
                                </Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Chip
                                size="small"
                                label={release.verificationStatus}
                                color={verificationStatusColor(release.verificationStatus)}
                                variant="outlined"
                              />
                              <Typography variant="caption" color="text.secondary">
                                {release.verificationRunId ?? 'No verification run'}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{formatOptionalTimestamp(release.updatedAt)}</TableCell>
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
