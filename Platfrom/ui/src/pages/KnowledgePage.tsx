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

function summarizeEntityConfig(config: unknown) {
  if (!isRecord(config)) {
    return {
      vectorDimensions: 'Not configured',
      entityTypes: [] as string[],
    }
  }

  const aiConfig = isRecord(config['ai-config']) ? config['ai-config'] : {}
  const entities = isRecord(config['ai-entities']) ? config['ai-entities'] : {}

  const vectorDimensions =
    typeof aiConfig['vector-dimensions'] === 'number'
      ? String(aiConfig['vector-dimensions'])
      : 'Not configured'

  return {
    vectorDimensions,
    entityTypes: Object.keys(entities),
  }
}

export function KnowledgePage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [editorValue, setEditorValue] = useState(
    '{\n  "ai-config": {\n    "vector-dimensions": 512\n  },\n  "ai-entities": {}\n}',
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
      setEditorValue(JSON.stringify(draftQuery.data.entityConfig, null, 2))
      setParseError(null)
    }
  }, [draftQuery.data])

  const summary = useMemo(() => {
    try {
      return summarizeEntityConfig(JSON.parse(editorValue))
    } catch {
      return summarizeEntityConfig(null)
    }
  }, [editorValue])

  const saveMutation = useMutation({
    mutationFn: ({ draftId, entityConfig }: { draftId: string; entityConfig: unknown }) =>
      updateDeploymentDraft(draftId, { entityConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
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
        entityConfig: parsed,
      })
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Knowledge" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Entity and vector-space editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          This screen edits the runtime entity configuration on the active draft. For V1 it remains
          a raw JSON editor, but the data now flows through the same versioned draft lifecycle as
          actions.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Entity config is edited against the active draft for a selected deployment.
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
                    <Typography variant="h6">Raw entity config</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Keep the runtime schema config versioned here. Later phases can replace this with
                      structured forms for entity types and fields.
                    </Typography>
                  </Box>

                  {draftQuery.isLoading ? (
                    <Typography color="text.secondary">Loading entity config...</Typography>
                  ) : draftQuery.isError ? (
                    <Alert severity="error">
                      {draftQuery.error instanceof Error
                        ? draftQuery.error.message
                        : 'Failed to load entity config'}
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
                            : 'Failed to save entity config'}
                        </Alert>
                      ) : null}
                      {saveMutation.isSuccess ? (
                        <Alert severity="success">Entity config draft saved.</Alert>
                      ) : null}
                      <Stack direction="row" spacing={1.5}>
                        <Button
                          variant="contained"
                          startIcon={<SaveRoundedIcon />}
                          onClick={handleSave}
                          disabled={saveMutation.isPending || draftQuery.isLoading}
                        >
                          {saveMutation.isPending ? 'Saving...' : 'Save entity config'}
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            if (draftQuery.data) {
                              setEditorValue(JSON.stringify(draftQuery.data.entityConfig, null, 2))
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
                      Basic structural summary of the current entity configuration draft.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`Vector dimensions: ${summary.vectorDimensions}`} />
                    <Chip label={`${summary.entityTypes.length} entity types`} color="primary" />
                  </Stack>

                  <Divider />

                  {summary.entityTypes.length === 0 ? (
                    <Alert severity="info">No entity types detected in the current JSON.</Alert>
                  ) : (
                    <List dense disablePadding>
                      {summary.entityTypes.map((entityType) => (
                        <ListItem key={entityType} disableGutters>
                          <ListItemText
                            primary={entityType}
                            secondary="Configured entity type"
                          />
                        </ListItem>
                      ))}
                    </List>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  )
}
