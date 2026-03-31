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
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

type ActionPreview = {
  name: string
  description: string
  category: string
  requiredParameters: number
}

type RouteEditorState = {
  mode: 'path' | 'url'
  method: string
  target: string
  queryJson: string
  bodyJson: string
  successStatuses: string
  message: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
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

function actionNamesFromConfig(config: unknown): string[] {
  return extractActions(config).map((action) => action.name)
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {}
}

function ensureRecord(parent: Record<string, unknown>, key: string): Record<string, unknown> {
  const current = asRecord(parent[key])
  const cloned = cloneJson(current)
  parent[key] = cloned
  return cloned
}

function normalizeRoutingConfig(config: unknown): Record<string, unknown> {
  const root = cloneJson(asRecord(config))
  const connector = ensureRecord(root, 'connector')
  const inboundAuth = ensureRecord(connector, 'inbound-auth')
  ensureRecord(inboundAuth, 'api-key')
  const upstream = ensureRecord(connector, 'upstream')
  ensureRecord(upstream, 'auth')
  ensureRecord(connector, 'http')
  ensureRecord(connector, 'idempotency')
  const authz = ensureRecord(root, 'authz')
  ensureRecord(authz, 'upstream')
  ensureRecord(ensureRecord(authz, 'upstream'), 'auth')
  ensureRecord(authz, 'http')
  ensureRecord(root, 'actions')
  return root
}

function readStringValue(record: Record<string, unknown>, key: string, fallback = ''): string {
  const value = record[key]
  return typeof value === 'string' ? value : fallback
}

function readBooleanValue(record: Record<string, unknown>, key: string, fallback = false): boolean {
  const value = record[key]
  return typeof value === 'boolean' ? value : fallback
}

function readStatusValues(record: Record<string, unknown>): number[] {
  const successHttpStatus = record['success-http-status']
  if (Array.isArray(successHttpStatus)) {
    return successHttpStatus.flatMap((value) => (typeof value === 'number' ? [value] : []))
  }

  const camelCase = record.successHttpStatus
  if (Array.isArray(camelCase)) {
    return camelCase.flatMap((value) => (typeof value === 'number' ? [value] : []))
  }

  return []
}

function routeRecordForAction(routingConfig: Record<string, unknown>, actionName: string): Record<string, unknown> {
  const actions = asRecord(routingConfig.actions)
  return asRecord(actions[actionName])
}

function createRouteEditor(route: unknown): RouteEditorState {
  const routeRecord = asRecord(route)
  const request = asRecord(routeRecord.request)
  const response = asRecord(routeRecord.response)

  return {
    mode: readStringValue(routeRecord, 'url').trim().length > 0 ? 'url' : 'path',
    method: readStringValue(routeRecord, 'method', 'POST'),
    target: readStringValue(routeRecord, 'url') || readStringValue(routeRecord, 'path'),
    queryJson: JSON.stringify(asRecord(request.query), null, 2),
    bodyJson: JSON.stringify(
      Object.prototype.hasOwnProperty.call(request, 'body') ? request.body : null,
      null,
      2,
    ),
    successStatuses: readStatusValues(response).join(', '),
    message: readStringValue(response, 'message'),
  }
}

function buildRouteEditors(actionNames: string[], routingConfig: Record<string, unknown>): Record<string, RouteEditorState> {
  return actionNames.reduce<Record<string, RouteEditorState>>((accumulator, actionName) => {
    accumulator[actionName] = createRouteEditor(routeRecordForAction(routingConfig, actionName))
    return accumulator
  }, {})
}

function summarizeRoute(editor: RouteEditorState | null) {
  if (!editor) {
    return {
      configured: false,
      method: '—',
      target: 'No route selected',
    }
  }

  return {
    configured: editor.target.trim().length > 0,
    method: editor.method.trim() || 'POST',
    target: editor.target.trim() || 'No route configured',
  }
}

function parseSuccessStatuses(value: string, actionName: string): number[] {
  if (value.trim().length === 0) {
    return []
  }

  return value
    .split(',')
    .map((candidate) => candidate.trim())
    .filter((candidate) => candidate.length > 0)
    .map((candidate) => {
      const parsed = Number(candidate)
      if (!Number.isInteger(parsed) || parsed < 100 || parsed > 599) {
        throw new Error(`Success statuses for ${actionName} must be comma-separated HTTP status codes.`)
      }
      return parsed
    })
}

export function ActionsPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [editorValue, setEditorValue] = useState('{\n  "actions": []\n}')
  const [parseError, setParseError] = useState<string | null>(null)
  const [routingConfig, setRoutingConfig] = useState<Record<string, unknown>>(normalizeRoutingConfig(null))
  const [routeEditors, setRouteEditors] = useState<Record<string, RouteEditorState>>({})
  const [selectedRouteActionName, setSelectedRouteActionName] = useState('')
  const [routingError, setRoutingError] = useState<string | null>(null)
  const canEdit = workspace?.access.canEdit ?? false

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (!draftQuery.data) {
      return
    }

    setEditorValue(JSON.stringify(draftQuery.data.actionsConfig, null, 2))
    setParseError(null)

    const nextRoutingConfig = normalizeRoutingConfig(draftQuery.data.routingConfig)
    const draftActionNames = actionNamesFromConfig(draftQuery.data.actionsConfig)
    setRoutingConfig(nextRoutingConfig)
    setRouteEditors(buildRouteEditors(draftActionNames, nextRoutingConfig))
    setSelectedRouteActionName((current) =>
      draftActionNames.includes(current) ? current : (draftActionNames[0] ?? ''),
    )
    setRoutingError(null)
  }, [draftQuery.data])

  const actionPreview = useMemo(() => {
    try {
      return extractActions(JSON.parse(editorValue))
    } catch {
      return []
    }
  }, [editorValue])

  const routingActionNames = useMemo(() => {
    try {
      return actionNamesFromConfig(JSON.parse(editorValue))
    } catch {
      return draftQuery.data ? actionNamesFromConfig(draftQuery.data.actionsConfig) : []
    }
  }, [draftQuery.data, editorValue])

  useEffect(() => {
    setRouteEditors((previous) => {
      const next: Record<string, RouteEditorState> = {}
      for (const actionName of routingActionNames) {
        next[actionName] =
          previous[actionName] ?? createRouteEditor(routeRecordForAction(routingConfig, actionName))
      }
      return next
    })

    if (routingActionNames.length === 0) {
      if (selectedRouteActionName !== '') {
        setSelectedRouteActionName('')
      }
      return
    }

    if (!routingActionNames.includes(selectedRouteActionName)) {
      setSelectedRouteActionName(routingActionNames[0])
    }
  }, [routingActionNames, routingConfig, selectedRouteActionName])

  const connector = asRecord(routingConfig.connector)
  const inboundAuth = asRecord(connector['inbound-auth'])
  const connectorApiKey = asRecord(inboundAuth['api-key'])
  const connectorUpstream = asRecord(connector.upstream)
  const connectorUpstreamAuth = asRecord(connectorUpstream.auth)
  const authz = asRecord(routingConfig.authz)
  const authzUpstream = asRecord(authz.upstream)
  const authzUpstreamAuth = asRecord(authzUpstream.auth)

  const selectedRouteEditor = selectedRouteActionName
    ? routeEditors[selectedRouteActionName] ?? createRouteEditor(routeRecordForAction(routingConfig, selectedRouteActionName))
    : null

  const selectedRouteSummary = summarizeRoute(selectedRouteEditor)

  const saveMutation = useMutation({
    mutationFn: ({ draftId, actionsConfig }: { draftId: string; actionsConfig: unknown }) =>
      updateDeploymentDraft(draftId, { actionsConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const saveRoutingMutation = useMutation({
    mutationFn: ({ draftId, nextRoutingConfig }: { draftId: string; nextRoutingConfig: unknown }) =>
      updateDeploymentDraft(draftId, { routingConfig: nextRoutingConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const handleSaveActions = () => {
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

  const updateRoutingConfig = (mutator: (next: Record<string, unknown>) => void) => {
    setRoutingConfig((previous) => {
      const next = normalizeRoutingConfig(previous)
      mutator(next)
      return next
    })
  }

  const updateSelectedRoute = (patch: Partial<RouteEditorState>) => {
    if (!selectedRouteActionName) {
      return
    }

    setRoutingError(null)
    setRouteEditors((previous) => ({
      ...previous,
      [selectedRouteActionName]: {
        ...(previous[selectedRouteActionName] ?? createRouteEditor(routeRecordForAction(routingConfig, selectedRouteActionName))),
        ...patch,
      },
    }))
  }

  const buildRoutingConfigForSave = (): Record<string, unknown> => {
    const nextRoutingConfig = normalizeRoutingConfig(routingConfig)
    const nextActions: Record<string, unknown> = {}

    for (const actionName of routingActionNames) {
      const editor = routeEditors[actionName]
      if (!editor) {
        continue
      }

      const target = editor.target.trim()
      if (!target) {
        continue
      }

      const existingRoute = cloneJson(routeRecordForAction(nextRoutingConfig, actionName))
      existingRoute.method = editor.method.trim() || 'POST'

      if (editor.mode === 'url') {
        existingRoute.url = target
        delete existingRoute.path
      } else {
        existingRoute.path = target
        delete existingRoute.url
      }

      const request = asRecord(existingRoute.request)
      const parsedQuery = editor.queryJson.trim().length === 0 ? {} : JSON.parse(editor.queryJson)
      if (!isRecord(parsedQuery)) {
        throw new Error(`Route query for ${actionName} must be a JSON object.`)
      }
      request.query = parsedQuery
      request.body = editor.bodyJson.trim().length === 0 ? null : JSON.parse(editor.bodyJson)
      existingRoute.request = request

      const response = asRecord(existingRoute.response)
      response['success-http-status'] = parseSuccessStatuses(editor.successStatuses, actionName)
      if (editor.message.trim().length > 0) {
        response.message = editor.message.trim()
      } else {
        delete response.message
      }
      existingRoute.response = response
      nextActions[actionName] = existingRoute
    }

    nextRoutingConfig.actions = nextActions
    return nextRoutingConfig
  }

  const handleSaveRouting = () => {
    if (!draftQuery.data) {
      return
    }

    try {
      const nextRoutingConfig = buildRoutingConfigForSave()
      setRoutingError(null)
      saveRoutingMutation.mutate({
        draftId: draftQuery.data.id,
        nextRoutingConfig,
      })
    } catch (error) {
      setRoutingError(error instanceof Error ? error.message : 'Failed to build routing config')
    }
  }

  const resetRoutingEditor = () => {
    if (!draftQuery.data) {
      return
    }

    const nextRoutingConfig = normalizeRoutingConfig(draftQuery.data.routingConfig)
    const draftActionNames = actionNamesFromConfig(draftQuery.data.actionsConfig)
    setRoutingConfig(nextRoutingConfig)
    setRouteEditors(buildRouteEditors(draftActionNames, nextRoutingConfig))
    setSelectedRouteActionName((current) =>
      draftActionNames.includes(current) ? current : (draftActionNames[0] ?? ''),
    )
    setRoutingError(null)
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Actions" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Action catalog and connector routing
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          This screen now covers both sides of action enablement: the runtime action catalog and the
          REST connector mapping that actually executes those actions against a customer app.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                The selected deployment now comes from the shared workspace header, so action and routing edits
                stay in one persistent context while you move across platform sections.
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
                Saving actions or routing config requires deployment editor access or higher.
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
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={7}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Raw actions config</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        The catalog remains editable as raw JSON so the full action contract stays visible.
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
                            onClick={handleSaveActions}
                            disabled={!canEdit || saveMutation.isPending || draftQuery.isLoading}
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
                      <Typography variant="h6">Parsed action preview</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Local parsing shows what the runtime catalog will look like when the draft is published.
                      </Typography>
                    </Box>

                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={`${actionPreview.length} actions detected`} color="primary" />
                      <Chip
                        label={`${actionPreview.reduce((count, action) => count + action.requiredParameters, 0)} required params`}
                        variant="outlined"
                      />
                    </Stack>

                    <Divider />

                    {actionPreview.length === 0 ? (
                      <Alert severity="info">No actions were detected in the current editor value.</Alert>
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
                                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
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

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6">Connector wiring</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    These settings define how the REST connector reaches the customer app and how runtime reaches
                    remote authz through the connector.
                  </Typography>
                </Box>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Connector upstream base URL"
                      value={readStringValue(connectorUpstream, 'base-url')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(next, 'connector'), 'upstream')['base-url'] = event.target.value
                        })
                      }
                      helperText="Used by path-based action routes."
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      select
                      fullWidth
                      label="Upstream auth type"
                      value={readStringValue(connectorUpstreamAuth, 'type', 'NONE') || 'NONE'}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'upstream'), 'auth').type = event.target.value
                        })
                      }
                    >
                      <MenuItem value="NONE">NONE</MenuItem>
                      <MenuItem value="API_KEY">API_KEY</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Upstream auth header"
                      value={readStringValue(connectorUpstreamAuth, 'header', 'Authorization')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'upstream'), 'auth').header = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Upstream auth value"
                      value={readStringValue(connectorUpstreamAuth, 'value')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'upstream'), 'auth').value = event.target.value
                        })
                      }
                      helperText="Example: Bearer token or secret placeholder."
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={readBooleanValue(inboundAuth, 'allow-unauthenticated')}
                          onChange={(event) =>
                            updateRoutingConfig((next) => {
                              ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth')['allow-unauthenticated'] = event.target.checked
                            })
                          }
                        />
                      }
                      label="Allow unauthenticated inbound requests"
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={readBooleanValue(connectorApiKey, 'enabled', true)}
                          onChange={(event) =>
                            updateRoutingConfig((next) => {
                              ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth'), 'api-key').enabled = event.target.checked
                            })
                          }
                        />
                      }
                      label="Enable connector API key protection"
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Connector API key header"
                      value={readStringValue(connectorApiKey, 'header', 'X-AIFABRIC-API-KEY')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth'), 'api-key').header = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Connector API key value"
                      value={readStringValue(connectorApiKey, 'value')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'connector'), 'inbound-auth'), 'api-key').value = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={readBooleanValue(authz, 'enabled')}
                          onChange={(event) =>
                            updateRoutingConfig((next) => {
                              ensureRecord(next, 'authz').enabled = event.target.checked
                            })
                          }
                        />
                      }
                      label="Enable authz proxy endpoint"
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz path"
                      value={readStringValue(authz, 'path', '/api/authz/check')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(next, 'authz').path = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz upstream base URL"
                      value={readStringValue(authzUpstream, 'base-url')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(next, 'authz'), 'upstream')['base-url'] = event.target.value
                        })
                      }
                      helperText="Optional if authz shares the connector upstream service."
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      select
                      fullWidth
                      label="Authz upstream auth type"
                      value={readStringValue(authzUpstreamAuth, 'type', 'NONE') || 'NONE'}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'authz'), 'upstream'), 'auth').type = event.target.value
                        })
                      }
                    >
                      <MenuItem value="NONE">NONE</MenuItem>
                      <MenuItem value="API_KEY">API_KEY</MenuItem>
                    </TextField>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz upstream auth header"
                      value={readStringValue(authzUpstreamAuth, 'header', 'Authorization')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'authz'), 'upstream'), 'auth').header = event.target.value
                        })
                      }
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      fullWidth
                      label="Authz upstream auth value"
                      value={readStringValue(authzUpstreamAuth, 'value')}
                      onChange={(event) =>
                        updateRoutingConfig((next) => {
                          ensureRecord(ensureRecord(ensureRecord(next, 'authz'), 'upstream'), 'auth').value = event.target.value
                        })
                      }
                    />
                  </Grid>
                </Grid>
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={5}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Action route list</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Every runtime action should map to one connector route.
                      </Typography>
                    </Box>

                    {parseError ? (
                      <Alert severity="warning">
                        The current actions JSON is invalid, so routing is using the last valid draft action list.
                      </Alert>
                    ) : null}

                    {routingActionNames.length === 0 ? (
                      <Alert severity="info">Add at least one action before defining routes.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Action</TableCell>
                            <TableCell>Method</TableCell>
                            <TableCell>Target</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {routingActionNames.map((actionName) => {
                            const routeSummary = summarizeRoute(routeEditors[actionName] ?? null)
                            return (
                              <TableRow
                                key={actionName}
                                hover
                                selected={actionName === selectedRouteActionName}
                                onClick={() => setSelectedRouteActionName(actionName)}
                                sx={{ cursor: 'pointer' }}
                              >
                                <TableCell sx={{ fontWeight: 600 }}>{actionName}</TableCell>
                                <TableCell>{routeSummary.method}</TableCell>
                                <TableCell>
                                  <Typography
                                    variant="body2"
                                    color={routeSummary.configured ? 'text.primary' : 'text.secondary'}
                                  >
                                    {routeSummary.target}
                                  </Typography>
                                </TableCell>
                              </TableRow>
                            )
                          })}
                        </TableBody>
                      </Table>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={7}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Selected route editor</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Edit the request template and response mapping for the selected action route.
                      </Typography>
                    </Box>

                    {selectedRouteActionName ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip label={selectedRouteActionName} color="primary" />
                          <Chip label={selectedRouteSummary.method} variant="outlined" />
                          <Chip
                            label={selectedRouteSummary.configured ? 'Configured' : 'Not configured'}
                            color={selectedRouteSummary.configured ? 'success' : 'default'}
                          />
                        </Stack>

                        <Grid container spacing={2}>
                          <Grid item xs={12} md={4}>
                            <TextField
                              select
                              fullWidth
                              label="Route mode"
                              value={selectedRouteEditor?.mode ?? 'path'}
                              onChange={(event) =>
                                updateSelectedRoute({
                                  mode: event.target.value as 'path' | 'url',
                                })
                              }
                            >
                              <MenuItem value="path">Relative path</MenuItem>
                              <MenuItem value="url">Absolute URL</MenuItem>
                            </TextField>
                          </Grid>
                          <Grid item xs={12} md={4}>
                            <TextField
                              select
                              fullWidth
                              label="Method"
                              value={selectedRouteEditor?.method ?? 'POST'}
                              onChange={(event) => updateSelectedRoute({ method: event.target.value })}
                            >
                              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map((method) => (
                                <MenuItem key={method} value={method}>
                                  {method}
                                </MenuItem>
                              ))}
                            </TextField>
                          </Grid>
                          <Grid item xs={12} md={4}>
                            <TextField
                              fullWidth
                              label={selectedRouteEditor?.mode === 'url' ? 'Absolute URL' : 'Relative path'}
                              value={selectedRouteEditor?.target ?? ''}
                              onChange={(event) => updateSelectedRoute({ target: event.target.value })}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              fullWidth
                              label="Success HTTP statuses"
                              value={selectedRouteEditor?.successStatuses ?? ''}
                              onChange={(event) => updateSelectedRoute({ successStatuses: event.target.value })}
                              helperText="Comma-separated list. Leave empty for default 2xx handling."
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              fullWidth
                              label="Success message"
                              value={selectedRouteEditor?.message ?? ''}
                              onChange={(event) => updateSelectedRoute({ message: event.target.value })}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              multiline
                              minRows={10}
                              fullWidth
                              label="Query template JSON"
                              value={selectedRouteEditor?.queryJson ?? '{}'}
                              onChange={(event) => updateSelectedRoute({ queryJson: event.target.value })}
                              InputProps={{
                                sx: {
                                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                  fontSize: 13,
                                },
                              }}
                            />
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <TextField
                              multiline
                              minRows={10}
                              fullWidth
                              label="Body template JSON"
                              value={selectedRouteEditor?.bodyJson ?? 'null'}
                              onChange={(event) => updateSelectedRoute({ bodyJson: event.target.value })}
                              InputProps={{
                                sx: {
                                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                                  fontSize: 13,
                                },
                              }}
                            />
                          </Grid>
                        </Grid>

                        {routingError ? <Alert severity="error">{routingError}</Alert> : null}
                        {saveRoutingMutation.isError ? (
                          <Alert severity="error">
                            {saveRoutingMutation.error instanceof Error
                              ? saveRoutingMutation.error.message
                              : 'Failed to save routing config'}
                          </Alert>
                        ) : null}
                        {saveRoutingMutation.isSuccess ? (
                          <Alert severity="success">Routing config draft saved.</Alert>
                        ) : null}

                        <Stack direction="row" spacing={1.5}>
                          <Button
                            variant="contained"
                            startIcon={<SaveRoundedIcon />}
                            onClick={handleSaveRouting}
                            disabled={!canEdit || saveRoutingMutation.isPending || draftQuery.isLoading}
                          >
                            {saveRoutingMutation.isPending ? 'Saving...' : 'Save routing config'}
                          </Button>
                          <Button variant="outlined" onClick={resetRoutingEditor}>
                            Reset routing editor
                          </Button>
                        </Stack>
                      </>
                    ) : (
                      <Alert severity="info">Select an action to configure its route.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </>
      ) : null}
    </Stack>
  )
}
