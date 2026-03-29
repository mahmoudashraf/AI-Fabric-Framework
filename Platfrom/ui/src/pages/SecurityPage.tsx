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

function readBoolean(config: unknown, key: string): string {
  if (!isRecord(config)) {
    return 'Unknown'
  }

  const value = config[key]
  return typeof value === 'boolean' ? String(value) : 'Unknown'
}

function summarizeSecurityConfig(config: unknown) {
  const authzMode = readString(config, 'authzMode')
  const adminApiKeyEnabled = readBoolean(config, 'adminApiKeyEnabled')
  const connectorApiKeyEnabled = readBoolean(config, 'connectorApiKeyEnabled')
  const authzBaseUrl = readString(config, 'authzBaseUrl')

  return {
    authzMode,
    adminApiKeyEnabled,
    connectorApiKeyEnabled,
    authzBaseUrl,
    configuredCount: [authzMode, adminApiKeyEnabled, connectorApiKeyEnabled].filter(
      (value) => value !== 'Not configured' && value !== 'Unknown',
    ).length,
  }
}

export function SecurityPage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [editorValue, setEditorValue] = useState(
    '{\n  "authzMode": "REMOTE_HTTP",\n  "adminApiKeyEnabled": true,\n  "connectorApiKeyEnabled": true\n}',
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
      setEditorValue(JSON.stringify(draftQuery.data.securityConfig, null, 2))
      setParseError(null)
    }
  }, [draftQuery.data])

  const summary = useMemo(() => {
    try {
      return summarizeSecurityConfig(JSON.parse(editorValue))
    } catch {
      return summarizeSecurityConfig(null)
    }
  }, [editorValue])

  const saveMutation = useMutation({
    mutationFn: ({ draftId, securityConfig }: { draftId: string; securityConfig: unknown }) =>
      updateDeploymentDraft(draftId, { securityConfig }),
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
        securityConfig: parsed,
      })
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Security" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Security config editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          Security stays bounded and versioned at the platform layer. This screen edits the draft
          settings that control runtime protection, connector protection, and remote authz behavior.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Security config is edited against the active draft for a selected deployment.
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
                    <Typography variant="h6">Raw security config</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      This keeps fail-closed runtime settings explicit. Arbitrary secret env vars are
                      still out of scope; this is only for bounded platform-managed security knobs.
                    </Typography>
                  </Box>

                  {draftQuery.isLoading ? (
                    <Typography color="text.secondary">Loading security config...</Typography>
                  ) : draftQuery.isError ? (
                    <Alert severity="error">
                      {draftQuery.error instanceof Error
                        ? draftQuery.error.message
                        : 'Failed to load security config'}
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
                            : 'Failed to save security config'}
                        </Alert>
                      ) : null}
                      {saveMutation.isSuccess ? (
                        <Alert severity="success">Security config draft saved.</Alert>
                      ) : null}
                      <Stack direction="row" spacing={1.5}>
                        <Button
                          variant="contained"
                          startIcon={<SaveRoundedIcon />}
                          onClick={handleSave}
                          disabled={saveMutation.isPending || draftQuery.isLoading}
                        >
                          {saveMutation.isPending ? 'Saving...' : 'Save security config'}
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            if (draftQuery.data) {
                              setEditorValue(JSON.stringify(draftQuery.data.securityConfig, null, 2))
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
                      Readable summary of the security settings currently in the editor.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`${summary.configuredCount}/3 required fields configured`} color="primary" />
                    <Chip label={summary.authzMode} variant="outlined" />
                  </Stack>

                  <Divider />

                  <List dense disablePadding>
                    <ListItem disableGutters>
                      <ListItemText primary="Authorization mode" secondary={summary.authzMode} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Admin API key enabled" secondary={summary.adminApiKeyEnabled} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Connector API key enabled" secondary={summary.connectorApiKeyEnabled} />
                    </ListItem>
                    <ListItem disableGutters>
                      <ListItemText primary="Remote authz base URL" secondary={summary.authzBaseUrl} />
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
