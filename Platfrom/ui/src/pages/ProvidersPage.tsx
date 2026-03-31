import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  List,
  ListItem,
  ListItemText,
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

type ProviderFormState = {
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  runtimeProfile: string
  connectorProfile: string
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
    runtimeProfile: readString(record, 'runtimeProfile', 'runtime-dev'),
    connectorProfile: readString(record, 'connectorProfile', 'connector-hosted'),
  }
}

function summarizeProviderConfig(form: ProviderFormState) {
  return {
    llmProvider: form.llmProvider.trim() || 'Not configured',
    embeddingProvider: form.embeddingProvider.trim() || 'Not configured',
    vectorStrategy: form.vectorStrategy.trim() || 'Not configured',
    runtimeProfile: form.runtimeProfile.trim() || 'Not configured',
    connectorProfile: form.connectorProfile.trim() || 'Not configured',
    configuredCount: Object.values(form).filter((value) => value.trim().length > 0).length,
  }
}

export function ProvidersPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formState, setFormState] = useState<ProviderFormState>({
    llmProvider: 'openai',
    embeddingProvider: 'openai',
    vectorStrategy: 'lucene',
    runtimeProfile: 'runtime-dev',
    connectorProfile: 'connector-hosted',
  })

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

  const saveMutation = useMutation({
    mutationFn: ({ draftId, providerConfig }: { draftId: string; providerConfig: unknown }) =>
      updateDeploymentDraft(draftId, { providerConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const handleFieldChange = (key: keyof ProviderFormState, value: string) => {
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
                      Advanced provider-specific secret handling stays at the platform secret layer. This page only
                      manages the portable deployment profile values.
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
                            fullWidth
                            label="LLM provider"
                            value={formState.llmProvider}
                            onChange={(event) => handleFieldChange('llmProvider', event.target.value)}
                            helperText="Examples: openai, anthropic"
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Embedding provider"
                            value={formState.embeddingProvider}
                            onChange={(event) => handleFieldChange('embeddingProvider', event.target.value)}
                            helperText="Examples: openai, voyageai"
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Vector strategy"
                            value={formState.vectorStrategy}
                            onChange={(event) => handleFieldChange('vectorStrategy', event.target.value)}
                            helperText="Examples: lucene, qdrant"
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Runtime profile"
                            value={formState.runtimeProfile}
                            onChange={(event) => handleFieldChange('runtimeProfile', event.target.value)}
                            helperText="Example: runtime-dev"
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Connector profile"
                            value={formState.connectorProfile}
                            onChange={(event) => handleFieldChange('connectorProfile', event.target.value)}
                            helperText="Example: connector-hosted"
                          />
                        </Grid>
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
                          disabled={saveMutation.isPending || draftQuery.isLoading}
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
