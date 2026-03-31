import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  fetchDeploymentDraft,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type ProviderFormState = {
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  runtimeProfile: string
  connectorProfile: string
  qdrantHost: string
  qdrantPort: string
  qdrantGrpcPort: string
  qdrantPreferGrpc: boolean
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
}

function readString(config: Record<string, unknown>, key: string, fallback = ''): string {
  const value = config[key]
  return typeof value === 'string' ? value : fallback
}

function readProviderForm(config: unknown): ProviderFormState {
  const record = isRecord(config) ? config : {}
  return {
    llmProvider: readString(record, 'llmProvider', 'openai'),
    embeddingProvider: readString(record, 'embeddingProvider', 'openai'),
    vectorStrategy: readString(record, 'vectorStrategy', 'lucene'),
    runtimeProfile: readString(record, 'runtimeProfile', 'runtime-managed'),
    connectorProfile: readString(record, 'connectorProfile', 'connector-hosted'),
    qdrantHost: readString(record, 'qdrantHost'),
    qdrantPort: readString(record, 'qdrantPort', '6333'),
    qdrantGrpcPort: readString(record, 'qdrantGrpcPort', '6334'),
    qdrantPreferGrpc: typeof record.qdrantPreferGrpc === 'boolean' ? record.qdrantPreferGrpc : false,
  }
}

function summarizeProviderConfig(form: ProviderFormState) {
  const configuredCount = [
    form.llmProvider.trim().length > 0,
    form.embeddingProvider.trim().length > 0,
    form.vectorStrategy.trim().length > 0,
    form.runtimeProfile.trim().length > 0,
    form.connectorProfile.trim().length > 0,
    form.vectorStrategy !== 'qdrant' || form.qdrantHost.trim().length > 0,
  ].filter(Boolean).length
  return {
    llmProvider: form.llmProvider.trim() || 'Not configured',
    embeddingProvider: form.embeddingProvider.trim() || 'Not configured',
    vectorStrategy: form.vectorStrategy.trim() || 'Not configured',
    runtimeProfile: form.runtimeProfile.trim() || 'Not configured',
    connectorProfile: form.connectorProfile.trim() || 'Not configured',
    qdrantHost: form.qdrantHost.trim() || 'Not configured',
    qdrantPort: form.qdrantPort.trim() || '6333',
    qdrantGrpcPort: form.qdrantGrpcPort.trim() || '6334',
    qdrantPreferGrpc: String(form.qdrantPreferGrpc),
    configuredCount,
  }
}

function providerFormsEqual(left: ProviderFormState, right: ProviderFormState): boolean {
  return (
    left.llmProvider.trim() === right.llmProvider.trim()
    && left.embeddingProvider.trim() === right.embeddingProvider.trim()
    && left.vectorStrategy.trim() === right.vectorStrategy.trim()
    && left.runtimeProfile.trim() === right.runtimeProfile.trim()
    && left.connectorProfile.trim() === right.connectorProfile.trim()
    && left.qdrantHost.trim() === right.qdrantHost.trim()
    && left.qdrantPort.trim() === right.qdrantPort.trim()
    && left.qdrantGrpcPort.trim() === right.qdrantGrpcPort.trim()
    && left.qdrantPreferGrpc === right.qdrantPreferGrpc
  )
}

export function ProvidersPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formState, setFormState] = useState<ProviderFormState>({
    llmProvider: 'openai',
    embeddingProvider: 'openai',
    vectorStrategy: 'lucene',
    runtimeProfile: 'runtime-managed',
    connectorProfile: 'connector-hosted',
    qdrantHost: '',
    qdrantPort: '6333',
    qdrantGrpcPort: '6334',
    qdrantPreferGrpc: false,
  })
  const canEdit = workspace?.access.canEdit ?? false

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (draftQuery.data) {
      setFormState(readProviderForm(draftQuery.data.providerConfig))
    }
  }, [draftQuery.data])

  const summary = useMemo(() => summarizeProviderConfig(formState), [formState])
  const savedFormState = useMemo(
    () => readProviderForm(draftQuery.data?.providerConfig),
    [draftQuery.data?.providerConfig],
  )
  const draftDirty = useMemo(
    () => (draftQuery.data ? !providerFormsEqual(formState, savedFormState) : false),
    [draftQuery.data, formState, savedFormState],
  )
  const editorState = useMemo(
    () => ({
      dirty: draftDirty,
      label: 'Provider config',
      description: draftDirty
        ? 'Provider profile edits exist only in the current browser buffer until you save the deployment draft.'
        : 'Provider profile editor matches the saved deployment draft.',
    }),
    [draftDirty],
  )
  useDeploymentWorkspaceEditorState(selectedDeploymentId ? editorState : null)

  const saveMutation = useMutation({
    mutationFn: ({ draftId, providerConfig }: { draftId: string; providerConfig: unknown }) =>
      updateDeploymentDraft(draftId, { providerConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const handleFieldChange = <K extends keyof ProviderFormState>(key: K, value: ProviderFormState[K]) => {
    setFormState((previous) => ({
      ...previous,
      [key]: value,
    }))
  }

  const handleSave = () => {
    if (!draftQuery.data) {
      return
    }

    const nextConfig = cloneJson(
      isRecord(draftQuery.data.providerConfig) ? draftQuery.data.providerConfig : {},
    )
    nextConfig.llmProvider = formState.llmProvider.trim()
    nextConfig.embeddingProvider = formState.embeddingProvider.trim()
    nextConfig.vectorStrategy = formState.vectorStrategy.trim()
    nextConfig.runtimeProfile = formState.runtimeProfile.trim()
    nextConfig.connectorProfile = formState.connectorProfile.trim()
    nextConfig.qdrantHost = formState.qdrantHost.trim()
    nextConfig.qdrantPort = formState.qdrantPort.trim()
    nextConfig.qdrantGrpcPort = formState.qdrantGrpcPort.trim()
    nextConfig.qdrantPreferGrpc = formState.qdrantPreferGrpc

    saveMutation.mutate({
      draftId: draftQuery.data.id,
      providerConfig: nextConfig,
    })
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Providers" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Provider profile editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Provider selection stays structured and versioned. This screen edits the bounded provider knobs
          that later compile into runtime and connector deployment settings.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Provider profile changes now follow the deployment selected in the shared workspace header.
              </Typography>
            </Box>

            {workspace ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={workspace.deployment.name} variant="outlined" />
                <Chip label={workspace.deployment.environment} variant="outlined" />
                <Chip label={workspace.deployment.status} color="primary" />
                <Chip label={workspace.template.name} variant="outlined" />
              </Stack>
            ) : null}
            {!canEdit && workspace ? (
              <Alert severity="info">
                Saving provider config requires deployment editor access or higher.
              </Alert>
            ) : null}

            {draftQuery.data ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={`Draft: ${draftQuery.data.id}`} variant="outlined" />
                <Chip label={`Revision ${draftQuery.data.revisionNumber}`} variant="outlined" />
                <Chip label={draftQuery.data.status} color="primary" />
              </Stack>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeploymentId ? (
        <Grid container spacing={2.5}>
          <Grid item xs={12} lg={7}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2.5}>
                  <Box>
                    <Typography variant="h6">Structured provider settings</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Platform-managed deployments expose only the provider and vector combinations that the
                      Railway runtime image can actually run on this branch. Secrets remain in the dedicated
                      security workspace, while deployment-specific non-secret settings stay here.
                    </Typography>
                  </Box>

                  {draftQuery.isLoading ? (
                    <Typography color="text.secondary">Loading provider config...</Typography>
                  ) : draftQuery.isError ? (
                    <Alert severity="error">
                      {draftQuery.error instanceof Error
                        ? draftQuery.error.message
                        : 'Failed to load provider config'}
                    </Alert>
                  ) : (
                    <>
                      <Grid container spacing={2}>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="LLM provider"
                            value={formState.llmProvider}
                            onChange={(event) => handleFieldChange('llmProvider', event.target.value)}
                            helperText="Managed runtime currently supports OpenAI and Anthropic for LLM orchestration."
                          >
                            <MenuItem value="openai">OpenAI</MenuItem>
                            <MenuItem value="anthropic">Anthropic</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Embedding provider"
                            value={formState.embeddingProvider}
                            onChange={(event) => handleFieldChange('embeddingProvider', event.target.value)}
                            helperText="ONNX runs locally in the managed runtime image. OpenAI embeddings use the platform secret store."
                          >
                            <MenuItem value="openai">OpenAI</MenuItem>
                            <MenuItem value="onnx">ONNX</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Vector strategy"
                            value={formState.vectorStrategy}
                            onChange={(event) => handleFieldChange('vectorStrategy', event.target.value)}
                            helperText="Lucene is self-contained. Qdrant requires a managed host below."
                          >
                            <MenuItem value="lucene">Lucene</MenuItem>
                            <MenuItem value="qdrant">Qdrant</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Runtime profile"
                            value={formState.runtimeProfile}
                            onChange={(event) => handleFieldChange('runtimeProfile', event.target.value)}
                            helperText="Use runtime-managed for secure platform-driven deployments. runtime-dev is available for explicit dev-only rollouts."
                          >
                            <MenuItem value="runtime-managed">runtime-managed</MenuItem>
                            <MenuItem value="runtime-dev">runtime-dev</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Connector profile"
                            value={formState.connectorProfile}
                            onChange={(event) => handleFieldChange('connectorProfile', event.target.value)}
                            helperText="connector-hosted exposes the runtime proxy from the REST connector. connector-passive disables those proxy surfaces."
                          >
                            <MenuItem value="connector-hosted">connector-hosted</MenuItem>
                            <MenuItem value="connector-passive">connector-passive</MenuItem>
                          </TextField>
                        </Grid>
                        {formState.vectorStrategy === 'qdrant' ? (
                          <>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Qdrant host"
                                value={formState.qdrantHost}
                                onChange={(event) => handleFieldChange('qdrantHost', event.target.value)}
                                helperText="Required. Hostname or internal address for the target Qdrant cluster."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Qdrant port"
                                value={formState.qdrantPort}
                                onChange={(event) => handleFieldChange('qdrantPort', event.target.value)}
                                helperText="Default 6333"
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Qdrant gRPC port"
                                value={formState.qdrantGrpcPort}
                                onChange={(event) => handleFieldChange('qdrantGrpcPort', event.target.value)}
                                helperText="Default 6334"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.qdrantPreferGrpc}
                                    onChange={(event) => handleFieldChange('qdrantPreferGrpc', event.target.checked)}
                                  />
                                )}
                                label="Prefer Qdrant gRPC transport"
                              />
                            </Grid>
                          </>
                        ) : null}
                      </Grid>

                      {saveMutation.isError ? (
                        <Alert severity="error">
                          {saveMutation.error instanceof Error
                            ? saveMutation.error.message
                            : 'Failed to save provider config'}
                        </Alert>
                      ) : null}
                      {saveMutation.isSuccess ? (
                        <Alert severity="success">Provider config draft saved.</Alert>
                      ) : null}

                      <Stack direction="row" spacing={1.5}>
                          <Button
                            variant="contained"
                            startIcon={<SaveRoundedIcon />}
                            onClick={handleSave}
                            disabled={!canEdit || saveMutation.isPending || draftQuery.isLoading || !draftDirty}
                          >
                            {saveMutation.isPending ? 'Saving...' : 'Save provider config'}
                          </Button>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            if (draftQuery.data) {
                              setFormState(readProviderForm(draftQuery.data.providerConfig))
                            }
                          }}
                        >
                          Reset form
                        </Button>
                      </Stack>
                    </>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} lg={5}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h6">Provider summary</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      High-level summary of the provider profile currently in the form.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`${summary.configuredCount}/5 fields configured`} color="primary" />
                    <Chip label={summary.vectorStrategy} variant="outlined" />
                  </Stack>

                  <Divider />

                  <List dense disablePadding>
                    <ListItem disableGutters>
                      <ListItemText primary="LLM provider" secondary={summary.llmProvider} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Embedding provider" secondary={summary.embeddingProvider} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Vector strategy" secondary={summary.vectorStrategy} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Runtime profile" secondary={summary.runtimeProfile} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Connector profile" secondary={summary.connectorProfile} />
                    </ListItem>
                    {formState.vectorStrategy === 'qdrant' ? (
                      <>
                        <ListItem disableGutters>
                          <ListItemText primary="Qdrant host" secondary={summary.qdrantHost} />
                        </ListItem>
                        <ListItem disableGutters>
                          <ListItemText primary="Qdrant port" secondary={summary.qdrantPort} />
                        </ListItem>
                        <ListItem disableGutters>
                          <ListItemText primary="Qdrant gRPC port" secondary={summary.qdrantGrpcPort} />
                        </ListItem>
                        <ListItem disableGutters>
                          <ListItemText primary="Prefer gRPC" secondary={summary.qdrantPreferGrpc} />
                        </ListItem>
                      </>
                    ) : null}
                  </List>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  )
}
