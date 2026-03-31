import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import BookmarkAddRoundedIcon from '@mui/icons-material/BookmarkAddRounded'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import SendRoundedIcon from '@mui/icons-material/SendRounded'
import StorageRoundedIcon from '@mui/icons-material/StorageRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  clearDeploymentPocRuntimeVectors,
  createDeploymentPocScenario,
  deleteDeploymentPocConversation,
  deleteDeploymentPocScenario,
  fetchDeploymentPocChatSuggestions,
  fetchDeploymentPocConversation,
  fetchDeploymentPocScenarios,
  fetchDeploymentPocWorkspace,
  queryDeploymentPocChat,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readResultLabel(result: unknown, key: string, fallback = '—') {
  if (!isRecord(result)) {
    return fallback
  }
  const value = result[key]
  if (typeof value === 'string' && value.trim().length > 0) {
    return value
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  return fallback
}

function jsonPreview(value: unknown) {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : '—'
}

export function PocPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [draftQueryText, setDraftQueryText] = useState('')
  const [conversationId, setConversationId] = useState('')
  const [lastResult, setLastResult] = useState<unknown>(null)
  const runtimeUnavailable = !workspace?.deployment.runtimeBaseUrl

  const pocWorkspaceQuery = useQuery({
    queryKey: ['deployment-poc-workspace', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocWorkspace(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const scenarioQuery = useQuery({
    queryKey: ['deployment-poc-scenarios', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocScenarios(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const conversationQuery = useQuery({
    queryKey: ['deployment-poc-conversation', selectedDeploymentId, conversationId],
    queryFn: () => fetchDeploymentPocConversation(selectedDeploymentId, conversationId),
    enabled:
      selectedDeploymentId.length > 0 &&
      conversationId.trim().length > 0 &&
      Boolean(workspace?.deployment.runtimeBaseUrl),
  })

  const suggestionsQuery = useQuery({
    queryKey: ['deployment-poc-suggestions', selectedDeploymentId, workspace?.deployment.name, workspace?.template.name],
    queryFn: () =>
      fetchDeploymentPocChatSuggestions(selectedDeploymentId, {
        content: `Deployment ${workspace?.deployment.name ?? ''} using template ${workspace?.template.name ?? ''}`,
        maxSuggestions: 4,
      }),
    enabled: selectedDeploymentId.length > 0 && Boolean(workspace?.deployment.runtimeBaseUrl),
  })

  const queryMutation = useMutation({
    mutationFn: () =>
      queryDeploymentPocChat(selectedDeploymentId, {
        query: draftQueryText.trim(),
        conversationId: conversationId || undefined,
      }),
    onSuccess: async (response) => {
      setDraftQueryText('')
      if (response.conversationId) {
        setConversationId(response.conversationId)
      }
      setLastResult(response.result)
      if (response.conversationId) {
        await queryClient.invalidateQueries({
          queryKey: ['deployment-poc-conversation', selectedDeploymentId, response.conversationId],
        })
      }
    },
  })

  const resetConversationMutation = useMutation({
    mutationFn: async () => {
      if (!conversationId) {
        return
      }
      await deleteDeploymentPocConversation(selectedDeploymentId, conversationId)
    },
    onSuccess: async () => {
      const previousConversationId = conversationId
      setConversationId('')
      setLastResult(null)
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-conversation', selectedDeploymentId, previousConversationId],
      })
    },
  })

  const clearVectorsMutation = useMutation({
    mutationFn: () =>
      clearDeploymentPocRuntimeVectors(selectedDeploymentId, {
        confirm: true,
        reason: 'platform-poc-reset',
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-workspace', selectedDeploymentId],
      })
    },
  })

  const saveScenarioMutation = useMutation({
    mutationFn: (title: string) =>
      createDeploymentPocScenario(selectedDeploymentId, {
        title,
        category: 'Custom',
        prompt: draftQueryText.trim(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-scenarios', selectedDeploymentId],
      })
    },
  })

  const deleteScenarioMutation = useMutation({
    mutationFn: (scenarioId: string) => deleteDeploymentPocScenario(selectedDeploymentId, scenarioId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-scenarios', selectedDeploymentId],
      })
    },
  })

  useEffect(() => {
    setConversationId('')
    setLastResult(null)
    setDraftQueryText('')
  }, [selectedDeploymentId])

  const dynamicSuggestions = suggestionsQuery.data?.suggestions ?? []
  const countsByEntityType = pocWorkspaceQuery.data?.indexing.countsByEntityType ?? {}
  const visibleWarnings = [...(pocWorkspaceQuery.data?.warnings ?? [])]

  return (
    <Stack spacing={3}>
      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Embedded POC chatbot</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Use the selected deployment directly from the platform to validate grounded answers, action execution,
                and overall operator-facing behavior before external UI integration work starts.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Mode: Deployment-scoped POC console" color="primary" variant="outlined" />
              <Chip label={`Runtime: ${workspace?.deployment.runtimeBaseUrl ? 'connected' : 'not applied'}`} variant="outlined" />
              {workspace?.deployment.activeVersion ? (
                <Chip label={`Active version: ${workspace.deployment.activeVersion}`} variant="outlined" />
              ) : null}
            </Stack>

            {runtimeUnavailable ? (
              <Alert severity="warning">
                This deployment does not have a runtime URL yet. Apply the deployment before using the embedded POC
                chat console.
              </Alert>
            ) : (
              <Alert severity="info">
                Scenario suggestions are deployment-aware. Use them to test prompt behavior, retrieval, and live
                actions in a controlled operator session.
              </Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2.5} justifyContent="space-between">
              <Box>
                <Typography variant="h6">Test data and reset</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 920 }}>
                  Review the current dataset profile, inspect live indexing counts, and clear runtime vectors to get
                  back to a clean proof-of-concept loop without leaving the deployment workspace.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
                <Chip
                  icon={<StorageRoundedIcon />}
                  label={pocWorkspaceQuery.data?.dataset.profileLabel ?? 'Dataset profile'}
                  variant="outlined"
                />
                <Chip
                  label={`Indexing: ${pocWorkspaceQuery.data?.indexing.available ? 'live' : 'unavailable'}`}
                  color={pocWorkspaceQuery.data?.indexing.available ? 'success' : 'default'}
                  variant="outlined"
                />
              </Stack>
            </Stack>

            {visibleWarnings.map((warning) => (
              <Alert key={warning} severity="warning">
                {warning}
              </Alert>
            ))}

            {clearVectorsMutation.isError ? (
              <Alert severity="error">
                {clearVectorsMutation.error instanceof Error
                  ? clearVectorsMutation.error.message
                  : 'Runtime vector reset failed'}
              </Alert>
            ) : null}

            {clearVectorsMutation.isSuccess ? (
              <Alert severity="success">
                Cleared {clearVectorsMutation.data.removedVectors} vectors from the runtime index.
              </Alert>
            ) : null}

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2.5} alignItems="stretch">
              <Card variant="outlined" sx={{ flex: 1, borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Dataset profile
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {pocWorkspaceQuery.data?.dataset.profileDescription ??
                        'The platform will show the active dataset profile here after the deployment is configured.'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Source:</strong> {pocWorkspaceQuery.data?.dataset.configSource ?? '—'}
                    </Typography>
                    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                      <strong>Upstream:</strong> {pocWorkspaceQuery.data?.dataset.upstreamBaseUrl ?? '—'}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {(pocWorkspaceQuery.data?.dataset.entityTypes ?? []).map((entityType) => (
                        <Chip key={entityType} label={entityType} size="small" />
                      ))}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ flex: 1, borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Runtime indexing state
                    </Typography>
                    <Typography variant="body2">
                      <strong>Vector DB:</strong> {pocWorkspaceQuery.data?.indexing.vectorDb ?? '—'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Total vectors:</strong> {pocWorkspaceQuery.data?.indexing.totalVectors ?? 0}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Vector scan:</strong>{' '}
                      {pocWorkspaceQuery.data?.indexing.supportsVectorScan ? 'supported' : 'not reported'}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {Object.entries(countsByEntityType).map(([entityType, count]) => (
                        <Chip key={entityType} label={`${entityType}: ${count}`} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ width: { xs: '100%', md: 320 }, borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Reset controls
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Use the existing conversation reset for chat state, and clear runtime vectors here when you need
                      a clean indexing loop for demos or operator retesting.
                    </Typography>
                    <Button
                      variant="outlined"
                      color="warning"
                      startIcon={<RestartAltRoundedIcon />}
                      disabled={
                        !pocWorkspaceQuery.data?.resetCapabilities.clearRuntimeVectors ||
                        clearVectorsMutation.isPending
                      }
                      onClick={() => {
                        if (!window.confirm('Clear runtime vectors for this deployment POC session?')) {
                          return
                        }
                        clearVectorsMutation.mutate()
                      }}
                    >
                      {clearVectorsMutation.isPending ? 'Clearing vectors...' : 'Clear runtime vectors'}
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Stack direction={{ xs: 'column', xl: 'row' }} spacing={3} alignItems="stretch">
        <Card sx={{ flex: 1, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="h6">Chatbot</Typography>
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<DeleteSweepRoundedIcon />}
                  disabled={!conversationId || resetConversationMutation.isPending}
                  onClick={() => resetConversationMutation.mutate()}
                >
                  {resetConversationMutation.isPending ? 'Resetting...' : 'Reset conversation'}
                </Button>
              </Stack>

              <Stack spacing={1.5}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  Scenario library
                </Typography>
                {scenarioQuery.isError ? (
                  <Alert severity="error">
                    {scenarioQuery.error instanceof Error ? scenarioQuery.error.message : 'Scenario library failed to load'}
                  </Alert>
                ) : null}
                {deleteScenarioMutation.isError ? (
                  <Alert severity="error">
                    {deleteScenarioMutation.error instanceof Error
                      ? deleteScenarioMutation.error.message
                      : 'Scenario delete failed'}
                  </Alert>
                ) : null}
                {(scenarioQuery.data ?? []).map((scenario) => (
                  <Card key={scenario.id} variant="outlined" sx={{ borderColor: 'divider' }}>
                    <CardContent>
                      <Stack spacing={1.25}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1.5}>
                          <Box>
                            <Typography variant="body1" sx={{ fontWeight: 700 }}>
                              {scenario.title}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {scenario.category} · {scenario.source}
                            </Typography>
                          </Box>
                          <Stack direction="row" spacing={1}>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<AutoFixHighRoundedIcon />}
                              onClick={() => setDraftQueryText(scenario.prompt)}
                            >
                              Use
                            </Button>
                            {scenario.editable ? (
                              <Button
                                size="small"
                                color="warning"
                                onClick={() => deleteScenarioMutation.mutate(scenario.id)}
                                disabled={deleteScenarioMutation.isPending}
                              >
                                Delete
                              </Button>
                            ) : null}
                          </Stack>
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {scenario.prompt}
                        </Typography>
                        {scenario.expectedOutcome ? (
                          <Typography variant="body2">
                            <strong>Expected outcome:</strong> {scenario.expectedOutcome}
                          </Typography>
                        ) : null}
                      </Stack>
                    </CardContent>
                  </Card>
                ))}

                {dynamicSuggestions.length > 0 ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    {dynamicSuggestions.map((suggestion) => (
                      <Chip
                        key={suggestion}
                        label={suggestion}
                        clickable
                        onClick={() => setDraftQueryText(suggestion)}
                        icon={<AutoFixHighRoundedIcon />}
                        variant="outlined"
                      />
                    ))}
                  </Stack>
                ) : null}
              </Stack>

              <TextField
                label="Ask the deployment"
                placeholder="Ask a grounded question or try an action-oriented workflow"
                multiline
                minRows={4}
                value={draftQueryText}
                disabled={runtimeUnavailable}
                onChange={(event) => setDraftQueryText(event.target.value)}
              />

              {queryMutation.isError ? (
                <Alert severity="error">
                  {queryMutation.error instanceof Error ? queryMutation.error.message : 'POC query failed'}
                </Alert>
              ) : null}

              {saveScenarioMutation.isError ? (
                <Alert severity="error">
                  {saveScenarioMutation.error instanceof Error
                    ? saveScenarioMutation.error.message
                    : 'Scenario save failed'}
                </Alert>
              ) : null}

              <Stack direction="row" spacing={1.5}>
                <Button
                  variant="contained"
                  startIcon={<SendRoundedIcon />}
                  disabled={runtimeUnavailable || queryMutation.isPending || draftQueryText.trim().length === 0}
                  onClick={() => queryMutation.mutate()}
                >
                  {queryMutation.isPending ? 'Sending...' : 'Send to deployment'}
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<BookmarkAddRoundedIcon />}
                  disabled={runtimeUnavailable || saveScenarioMutation.isPending || draftQueryText.trim().length === 0}
                  onClick={() => {
                    const title = window.prompt('Save this prompt as a reusable scenario title?')
                    if (!title || title.trim().length === 0) {
                      return
                    }
                    saveScenarioMutation.mutate(title.trim())
                  }}
                >
                  {saveScenarioMutation.isPending ? 'Saving...' : 'Save as scenario'}
                </Button>
              </Stack>

              <Divider />

              <Stack spacing={1.5}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  Conversation transcript
                </Typography>
                {(conversationQuery.data?.turns ?? []).length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    No turns yet. Start the conversation with one of the suggested scenarios or your own query.
                  </Typography>
                ) : (
                  (conversationQuery.data?.turns ?? []).map((turn, index) => (
                    <Card key={`${turn.timestamp ?? 'turn'}-${index}`} variant="outlined" sx={{ borderColor: 'divider' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="caption" color="text.secondary">
                            {formatDateTime(turn.timestamp)}
                          </Typography>
                          <Box>
                            <Typography variant="overline" color="text.secondary">
                              User
                            </Typography>
                            <Typography variant="body1">{turn.userQuery || '—'}</Typography>
                          </Box>
                          <Box>
                            <Typography variant="overline" color="text.secondary">
                              Assistant
                            </Typography>
                            <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                              {turn.aiResponse || '—'}
                            </Typography>
                          </Box>
                        </Stack>
                      </CardContent>
                    </Card>
                  ))
                )}
              </Stack>
            </Stack>
          </CardContent>
        </Card>

        <Card sx={{ width: { xs: '100%', xl: 420 }, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Typography variant="h6">Latest orchestration trace</Typography>

              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={`Conversation: ${conversationId || 'new'}`} variant="outlined" />
                <Chip label={`Turns: ${conversationQuery.data?.turns.length ?? 0}`} variant="outlined" />
                <Chip label={`Suggestions: ${suggestionsQuery.data?.suggestions.length ?? 0}`} variant="outlined" />
              </Stack>

              {lastResult ? (
                <>
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      Result type
                    </Typography>
                    <Typography variant="body1">{readResultLabel(lastResult, 'type')}</Typography>
                  </Stack>
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      Success
                    </Typography>
                    <Typography variant="body1">{readResultLabel(lastResult, 'success')}</Typography>
                  </Stack>
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      Message
                    </Typography>
                    <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                      {readResultLabel(lastResult, 'message')}
                    </Typography>
                  </Stack>
                  <Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                      Raw result
                    </Typography>
                    <Box
                      component="pre"
                      sx={{
                        m: 0,
                        p: 1.5,
                        borderRadius: 2,
                        bgcolor: 'grey.950',
                        color: 'grey.100',
                        overflow: 'auto',
                        fontSize: 12,
                        lineHeight: 1.5,
                      }}
                    >
                      {jsonPreview(lastResult)}
                    </Box>
                  </Box>
                </>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Send a query to capture the latest orchestration result and inspect it from the platform.
                </Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </Stack>
  )
}
