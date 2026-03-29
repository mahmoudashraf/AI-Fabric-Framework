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
  fetchDeploymentDraft,
  fetchDeployments,
  updateDeploymentDraft,
} from '../api/platformApi'

type ActionPreview = {
  name: string
  description: string
  category: string
  requiredParameters: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function extractActions(config: unknown): ActionPreview[] {
  if (!isRecord(config)) {
    return []
  }

  const actions = config.actions
  if (!Array.isArray(actions)) {
    return []
  }

  return actions.flatMap((candidate) => {
    if (!isRecord(candidate)) {
      return []
    }

    const name = typeof candidate.name === 'string' ? candidate.name : 'unnamed_action'
    const description =
      typeof candidate.description === 'string' ? candidate.description : 'No description provided.'
    const category = typeof candidate.category === 'string' ? candidate.category : 'uncategorized'
    const requiredParameters = Array.isArray(candidate.requiredParameters)
      ? candidate.requiredParameters.length
      : 0

    return [{ name, description, category, requiredParameters }]
  })
}

export function ActionsPage() {
  const queryClient = useQueryClient()
  const [selectedDeploymentId, setSelectedDeploymentId] = useState('')
  const [editorValue, setEditorValue] = useState('{\n  "actions": []\n}')
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
      setEditorValue(JSON.stringify(draftQuery.data.actionsConfig, null, 2))
      setParseError(null)
    }
  }, [draftQuery.data])

  const actionPreview = useMemo(() => {
    try {
      return extractActions(JSON.parse(editorValue))
    } catch {
      return []
    }
  }, [editorValue])

  const saveMutation = useMutation({
    mutationFn: ({ draftId, actionsConfig }: { draftId: string; actionsConfig: unknown }) =>
      updateDeploymentDraft(draftId, { actionsConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
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
        actionsConfig: parsed,
      })
    } catch (error) {
      setParseError(error instanceof Error ? error.message : 'Invalid JSON')
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Actions" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Action catalog editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          This is the first real draft editor in the platform UI. It updates the active draft in the
          backend, and the revisions flow can then publish that draft into immutable artifacts.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment selection</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Actions are edited against the active draft of a selected deployment.
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
                    <Typography variant="h6">Raw actions config</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      V1 uses a raw JSON editor. Structured action forms and route mapping come later.
                    </Typography>
                  </Box>

                  {draftQuery.isLoading ? (
                    <Typography color="text.secondary">Loading draft actions...</Typography>
                  ) : draftQuery.isError ? (
                    <Alert severity="error">
                      {draftQuery.error instanceof Error
                        ? draftQuery.error.message
                        : 'Failed to load draft actions'}
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
                            fontFamily: 'ui-monospace, SFMono-Regular, SFMono-Regular, Menlo, monospace',
                            fontSize: 13,
                          },
                        }}
                      />

                      {parseError ? <Alert severity="error">{parseError}</Alert> : null}
                      {saveMutation.isError ? (
                        <Alert severity="error">
                          {saveMutation.error instanceof Error
                            ? saveMutation.error.message
                            : 'Failed to save actions config'}
                        </Alert>
                      ) : null}
                      {saveMutation.isSuccess ? (
                        <Alert severity="success">Actions draft saved.</Alert>
                      ) : null}

                      <Stack direction="row" spacing={1.5}>
                        <Button
                          variant="contained"
                          startIcon={<SaveRoundedIcon />}
                          onClick={handleSave}
                          disabled={saveMutation.isPending || draftQuery.isLoading}
                        >
                          {saveMutation.isPending ? 'Saving...' : 'Save actions config'}
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            if (draftQuery.data) {
                              setEditorValue(JSON.stringify(draftQuery.data.actionsConfig, null, 2))
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
                      The editor parses the JSON locally so you can see the resulting action list before
                      publishing.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`${actionPreview.length} actions`} color="primary" />
                  </Stack>

                  <Divider />

                  {actionPreview.length === 0 ? (
                    <Alert severity="info">No actions detected in the current JSON.</Alert>
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Name</TableCell>
                          <TableCell>Category</TableCell>
                          <TableCell>Required params</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {actionPreview.map((action) => (
                          <TableRow key={action.name} hover>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {action.name}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {action.description}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>{action.category}</TableCell>
                            <TableCell>{action.requiredParameters}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
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
