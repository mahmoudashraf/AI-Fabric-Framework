import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded'
import PauseCircleOutlineRoundedIcon from '@mui/icons-material/PauseCircleOutlineRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import PolicyRoundedIcon from '@mui/icons-material/PolicyRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import TokenRoundedIcon from '@mui/icons-material/TokenRounded'
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
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  cancelVectorizationRun,
  createVectorizationRun,
  fetchVectorizationOverview,
  fetchVectorizationPreview,
  pauseVectorizationRun,
  resumeVectorizationRun,
  retryVectorizationRun,
  rotateVectorizationRunnerToken,
  upsertVectorizationConnection,
  upsertVectorizationPlan,
  updateVectorizationSyncState,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function prettyJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2)
}

function parseJson(value: string, label: string): unknown {
  try {
    return JSON.parse(value)
  } catch (error) {
    throw new Error(`${label} must be valid JSON.`)
  }
}

function syncChipColor(state: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch ((state ?? '').toUpperCase()) {
    case 'IN_SYNC':
      return 'success'
    case 'MANUALLY_CONFIRMED':
      return 'default'
    case 'RUNNING':
      return 'default'
    case 'SOURCE_EMPTY':
      return 'default'
    case 'BOOTSTRAP_REQUIRED':
    case 'OUT_OF_DATE':
    case 'REINDEX_DEFERRED':
      return 'warning'
    default:
      return 'default'
  }
}

export function VectorizationPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [issuedToken, setIssuedToken] = useState<string | null>(null)

  const [connectionName, setConnectionName] = useState('Primary source connection')
  const [adapterType, setAdapterType] = useState('REST_API')
  const [authMode, setAuthMode] = useState('API_KEY')
  const [connectionConfigText, setConnectionConfigText] = useState('{\n  "baseUrl": "https://example.com",\n  "path": "/records",\n  "paginationMode": "OFFSET_LIMIT"\n}')
  const [secretReferencesText, setSecretReferencesText] = useState('{\n  "platformSecretRefs": {\n    "apiKey": "SOURCE_API_KEY"\n  },\n  "localSecretAliases": {\n    "apiKey": "SOURCE_API_KEY"\n  }\n}')
  const [discoverySummaryText, setDiscoverySummaryText] = useState('{\n  "countsByEntityType": {},\n  "countMethodByEntityType": {}\n}')

  const [planName, setPlanName] = useState('Onboarding vectorization')
  const [runnerMode, setRunnerMode] = useState('PLATFORM_MANAGED_AUTO')
  const [entityScopeText, setEntityScopeText] = useState('[]')
  const [mappingConfigText, setMappingConfigText] = useState('{\n  "entityMappings": {}\n}')
  const [executionConfigText, setExecutionConfigText] = useState('{\n  "batchSize": 100,\n  "concurrency": 1\n}')
  const [runEntityScope, setRunEntityScope] = useState('')
  const canEdit = workspace?.access.canEdit ?? false
  const canOperate = workspace?.access.canOperate ?? false
  const canAdmin = workspace?.access.canAdmin ?? false

  const overviewQuery = useQuery({
    queryKey: ['vectorization-overview', selectedDeploymentId],
    queryFn: () => fetchVectorizationOverview(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const previewQuery = useQuery({
    queryKey: ['vectorization-preview', selectedDeploymentId],
    queryFn: () => fetchVectorizationPreview(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    const overview = overviewQuery.data
    if (!overview) {
      return
    }

    if (overview.sourceConnection) {
      setConnectionName(overview.sourceConnection.name)
      setAdapterType(overview.sourceConnection.adapterType)
      setAuthMode(overview.sourceConnection.authMode)
      setConnectionConfigText(prettyJson(overview.sourceConnection.connectionConfig))
      setSecretReferencesText(prettyJson(overview.sourceConnection.secretReferences))
      setDiscoverySummaryText(prettyJson(overview.sourceConnection.discoverySummary))
    }

    if (overview.plan) {
      setPlanName(overview.plan.name)
      setRunnerMode(overview.plan.runnerMode)
      setEntityScopeText(prettyJson(overview.plan.activeRevision?.entityScope ?? []))
      setMappingConfigText(prettyJson(overview.plan.activeRevision?.mappingConfig ?? {}))
      setExecutionConfigText(prettyJson(overview.plan.activeRevision?.executionConfig ?? {}))
    }
  }, [overviewQuery.data])

  const refreshQueries = () => {
    void queryClient.invalidateQueries({ queryKey: ['vectorization-overview', selectedDeploymentId] })
    void queryClient.invalidateQueries({ queryKey: ['vectorization-preview', selectedDeploymentId] })
  }

  const connectionMutation = useMutation({
    mutationFn: () =>
      upsertVectorizationConnection(selectedDeploymentId, {
        name: connectionName,
        adapterType,
        authMode,
        connectionConfig: parseJson(connectionConfigText, 'Connection config'),
        secretReferences: parseJson(secretReferencesText, 'Secret references'),
        discoverySummary: parseJson(discoverySummaryText, 'Discovery summary'),
      }),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const planMutation = useMutation({
    mutationFn: () =>
      upsertVectorizationPlan(selectedDeploymentId, {
        name: planName,
        runnerMode,
        sourceConnectionId: overviewQuery.data?.sourceConnection?.id ?? null,
        entityScope: parseJson(entityScopeText, 'Entity scope'),
        mappingConfig: parseJson(mappingConfigText, 'Mapping config'),
        executionConfig: parseJson(executionConfigText, 'Execution config'),
      }),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const createRunMutation = useMutation({
    mutationFn: (reason: string) =>
      createVectorizationRun(selectedDeploymentId, {
        reason,
        entityTypes: runEntityScope
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean),
      }),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const syncStateMutation = useMutation({
    mutationFn: (payload: { action: string; reason?: string }) => updateVectorizationSyncState(selectedDeploymentId, payload),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const rotateTokenMutation = useMutation({
    mutationFn: () => rotateVectorizationRunnerToken(selectedDeploymentId, { runnerMode }),
    onSuccess: (summary) => {
      setIssuedToken(summary.registrationToken)
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const runActionMutation = useMutation({
    mutationFn: async (payload: { runId: string; action: 'pause' | 'resume' | 'cancel' | 'retry' }) => {
      switch (payload.action) {
        case 'pause':
          return pauseVectorizationRun(selectedDeploymentId, payload.runId)
        case 'resume':
          return resumeVectorizationRun(selectedDeploymentId, payload.runId)
        case 'cancel':
          return cancelVectorizationRun(selectedDeploymentId, payload.runId)
        case 'retry':
          return retryVectorizationRun(selectedDeploymentId, payload.runId)
      }
    },
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const previewAvailableEntities = useMemo(() => {
    const reindexOptions = previewQuery.data?.reindexOptions
    if (!reindexOptions || typeof reindexOptions !== 'object' || reindexOptions === null) {
      return []
    }
    const value = (reindexOptions as { availableEntities?: unknown }).availableEntities
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
  }, [previewQuery.data])

  if (!selectedDeploymentId) {
    return (
      <Alert severity="info">
        Select a deployment from the workspace header to configure onboarding vectorization.
      </Alert>
    )
  }

  return (
    <Stack spacing={3}>
      <Stack spacing={1}>
        <Typography variant="h4" sx={{ fontWeight: 700 }}>
          Vectorization
        </Typography>
        <Typography color="text.secondary">
          Deployment-scoped onboarding indexing through the runtime data-sync boundary, with runner auth and sync drift tracked in the platform.
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Chip label={selectedDeploymentSummary?.name ?? selectedDeploymentId} />
          <Chip label={overviewQuery.data?.plan?.syncState ?? 'BOOTSTRAP_REQUIRED'} color={syncChipColor(overviewQuery.data?.plan?.syncState)} />
          <Chip label={overviewQuery.data?.plan?.runnerMode ?? 'PLATFORM_MANAGED_AUTO'} variant="outlined" />
          <Chip label={overviewQuery.data?.runner?.compatibilityStatus ?? 'UNREGISTERED'} variant="outlined" />
        </Stack>
      </Stack>

      {formError ? <Alert severity="error">{formError}</Alert> : null}
      {issuedToken ? (
        <Alert severity="success">
          Runner token issued. Copy it now: <strong>{issuedToken}</strong>
        </Alert>
      ) : null}

      <Grid container spacing={3}>
        <Grid item xs={12} lg={6}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Source Connection</Typography>
                <TextField label="Connection name" value={connectionName} onChange={(event) => setConnectionName(event.target.value)} />
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField label="Adapter type" value={adapterType} onChange={(event) => setAdapterType(event.target.value)} fullWidth />
                  <TextField label="Auth mode" value={authMode} onChange={(event) => setAuthMode(event.target.value)} fullWidth />
                </Stack>
                <TextField
                  label="Connection config JSON"
                  multiline
                  minRows={8}
                  value={connectionConfigText}
                  onChange={(event) => setConnectionConfigText(event.target.value)}
                />
                <TextField
                  label="Secret references JSON"
                  multiline
                  minRows={6}
                  value={secretReferencesText}
                  onChange={(event) => setSecretReferencesText(event.target.value)}
                />
                <TextField
                  label="Discovery summary JSON"
                  multiline
                  minRows={6}
                  value={discoverySummaryText}
                  onChange={(event) => setDiscoverySummaryText(event.target.value)}
                />
                <Button
                  variant="contained"
                  startIcon={<SaveRoundedIcon />}
                  disabled={!canEdit || connectionMutation.isPending}
                  onClick={() => connectionMutation.mutate()}
                >
                  Save source connection
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={6}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Plan And Runner</Typography>
                <TextField label="Plan name" value={planName} onChange={(event) => setPlanName(event.target.value)} />
                <TextField label="Runner mode" value={runnerMode} onChange={(event) => setRunnerMode(event.target.value)} />
                <TextField
                  label="Entity scope JSON"
                  multiline
                  minRows={5}
                  value={entityScopeText}
                  onChange={(event) => setEntityScopeText(event.target.value)}
                />
                <TextField
                  label="Mapping config JSON"
                  multiline
                  minRows={7}
                  value={mappingConfigText}
                  onChange={(event) => setMappingConfigText(event.target.value)}
                />
                <TextField
                  label="Execution config JSON"
                  multiline
                  minRows={5}
                  value={executionConfigText}
                  onChange={(event) => setExecutionConfigText(event.target.value)}
                />
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="contained"
                    startIcon={<SaveRoundedIcon />}
                    disabled={!canEdit || planMutation.isPending}
                    onClick={() => planMutation.mutate()}
                  >
                    Save plan
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<TokenRoundedIcon />}
                    disabled={!canAdmin || rotateTokenMutation.isPending}
                    onClick={() => rotateTokenMutation.mutate()}
                  >
                    Rotate runner token
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={5}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Sync State</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {(overviewQuery.data?.plan?.syncReasonCodes ?? []).map((reasonCode) => (
                    <Chip key={reasonCode} label={reasonCode} variant="outlined" />
                  ))}
                </Stack>
                <TextField
                  label="Selected entities for next run"
                  helperText="Comma-separated configured entity types. Leave empty to use the active plan scope."
                  value={runEntityScope}
                  onChange={(event) => setRunEntityScope(event.target.value)}
                />
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="contained"
                    startIcon={<AutoFixHighRoundedIcon />}
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('BOOTSTRAP')}
                  >
                    Queue bootstrap
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<AutorenewRoundedIcon />}
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('REINDEX')}
                  >
                    Queue reindex
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('REFRESH')}
                  >
                    Queue refresh
                  </Button>
                </Stack>
                <Divider />
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="text"
                    startIcon={<WarningAmberRoundedIcon />}
                    disabled={!canOperate || syncStateMutation.isPending}
                    onClick={() => syncStateMutation.mutate({ action: 'DEFER_REINDEX', reason: 'Deferred by operator.' })}
                  >
                    Mark deferred
                  </Button>
                  <Button
                    variant="text"
                    startIcon={<PolicyRoundedIcon />}
                    disabled={!canOperate || syncStateMutation.isPending}
                    onClick={() => syncStateMutation.mutate({ action: 'MANUAL_CONFIRM', reason: 'External sync confirmed by operator.' })}
                  >
                    Manual confirm
                  </Button>
                  <Button
                    variant="text"
                    disabled={!canOperate || syncStateMutation.isPending}
                    onClick={() => syncStateMutation.mutate({ action: 'CLEAR_OVERRIDE' })}
                  >
                    Clear override
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={7}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Preview And Coverage</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {previewAvailableEntities.map((entity) => (
                    <Chip key={entity} label={entity} />
                  ))}
                </Stack>
                <TextField
                  label="Source discovery"
                  multiline
                  minRows={8}
                  value={prettyJson(previewQuery.data?.sourceDiscovery ?? {})}
                  InputProps={{ readOnly: true }}
                />
                <TextField
                  label="Reindex options"
                  multiline
                  minRows={6}
                  value={prettyJson(previewQuery.data?.reindexOptions ?? {})}
                  InputProps={{ readOnly: true }}
                />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">Recent Runs</Typography>
            {overviewQuery.data?.recentRuns.length ? (
              overviewQuery.data.recentRuns.map((run) => (
                <Box key={run.id} sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}>
                  <Stack spacing={1.5}>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', md: 'center' }}>
                      <Chip label={run.reason} />
                      <Chip label={run.status} variant="outlined" />
                      <Chip label={run.requestedStatus} variant="outlined" />
                      {run.runnerInstanceId ? <Chip label={run.runnerInstanceId} variant="outlined" /> : null}
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      {run.entityScope.length > 0 ? `Entities: ${run.entityScope.join(', ')}` : 'Uses active plan scope'}
                    </Typography>
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                      <Button
                        size="small"
                        startIcon={<PauseCircleOutlineRoundedIcon />}
                        disabled={!canOperate || runActionMutation.isPending}
                        onClick={() => runActionMutation.mutate({ runId: run.id, action: 'pause' })}
                      >
                        Pause
                      </Button>
                      <Button
                        size="small"
                        startIcon={<PlayCircleOutlineRoundedIcon />}
                        disabled={!canOperate || runActionMutation.isPending}
                        onClick={() => runActionMutation.mutate({ runId: run.id, action: 'resume' })}
                      >
                        Resume
                      </Button>
                      <Button
                        size="small"
                        disabled={!canOperate || runActionMutation.isPending}
                        onClick={() => runActionMutation.mutate({ runId: run.id, action: 'retry' })}
                      >
                        Retry
                      </Button>
                      <Button
                        size="small"
                        color="warning"
                        disabled={!canOperate || runActionMutation.isPending}
                        onClick={() => runActionMutation.mutate({ runId: run.id, action: 'cancel' })}
                      >
                        Cancel
                      </Button>
                    </Stack>
                    <TextField
                      label="Progress summary"
                      multiline
                      minRows={3}
                      value={prettyJson(run.progressSummary)}
                      InputProps={{ readOnly: true }}
                    />
                  </Stack>
                </Box>
              ))
            ) : (
              <Alert severity="info">No vectorization runs have been queued for this deployment yet.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
