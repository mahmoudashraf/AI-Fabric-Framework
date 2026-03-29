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
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  fetchDeploymentDraft,
  fetchDeployments,
  updateDeploymentDraft,
} from '../api/platformApi'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readString(config: unknown, key: string): string {
  if (!isRecord(config)) {
    return 'Not configured'
  }

  const value = config[key]
  return typeof value === 'string' && value.length > 0 ? value : 'Not configured'
}

function summarizeProviderConfig(config: unknown) {
  const llmProvider = readString(config, 'llmProvider')
  const embeddingProvider = readString(config, 'embeddingProvider')
  const vectorStrategy = readString(config, 'vectorStrategy')
  const runtimeProfile = readString(config, 'runtimeProfile')
  const connectorProfile = readString(config, 'connectorProfile')

  return {
    llmProvider,
    embeddingProvider,
    vectorStrategy,
    runtimeProfile,
    connectorProfile,
    configuredCount: [llmProvider, embeddingProvider, vectorStrategy, runtimeProfile, connectorProfile].filter(
      (value) => value !== 'Not configured',
    ).length,
  }
}

export function ProvidersPage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [editorValue, setEditorValue] = useState(
    '{\n  "llmProvider": "openai",\n  "embeddingProvider": "openai",\n  "vectorStrategy": "lucene",\n  "runtimeProfile": "runtime-dev",\n  "connectorProfile": "connector-hosted"\n}',
  )
  const [parseError, setParseError] = useState<string | null>(null)

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

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (draftQuery.data) {
      setEditorValue(JSON.stringify(draftQuery.data.providerConfig, null, 2))
      setParseError(null)
    }
  }, [draftQuery.data])

  const summary = useMemo(() => {
    try {
      return summarizeProviderConfig(JSON.parse(editorValue))
    } catch {
      return summarizeProviderConfig(null)
    }
  }, [editorValue])

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

  const handleSave = () => {
    if (!draftQuery.data) {
      return
    }

    try {
      const parsed = JSON.parse(editorValue) as unknown
      setParseError(null)
      saveMutation.mutate({
        draftId: draftQuery.data.id,
        providerConfig: parsed,
      })
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Providers" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Provider profile editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          Provider choices remain deployment configuration, not runtime code. This screen manages the
          active draft section that later compiles into runtime and connector provider settings.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Provider config is edited against the active draft for a selected deployment.
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
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h6">Raw provider config</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Keep provider selection explicit and versioned. Later phases can replace this with
                      profile pickers and secret references.
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
                      <TextField
                        multiline
                        minRows={24}
                        fullWidth
                        value={editorValue}
                        onChange={(event) => setEditorValue(event.target.value)}
                        InputProps={{
                          sx: {
                            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                            fontSize: 13,
                          },
                        }}
                      />
                      {parseError ? <Alert severity="error">{parseError}</Alert> : null}
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
                              setEditorValue(JSON.stringify(draftQuery.data.providerConfig, null, 2))
                              setParseError(null)
                            }
                          }}
                        >
                          Reset editor
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
                    <Typography variant="h6">Parsed preview</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      High-level summary of the provider profile currently in the editor.
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
