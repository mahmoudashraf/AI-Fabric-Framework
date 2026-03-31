import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import ChatRoundedIcon from '@mui/icons-material/ChatRounded'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
import SendRoundedIcon from '@mui/icons-material/SendRounded'
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
  deleteDeploymentPocConversation,
  fetchDeploymentPocChatSuggestions,
  fetchDeploymentPocConversation,
  queryDeploymentPocChat,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const STATIC_SCENARIOS = [
  'What can this assistant do for this deployment?',
  'Summarize the knowledge sources available to you.',
  'What live actions can you execute safely right now?',
  'What should an operator validate before customer rollout?',
]

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

  useEffect(() => {
    setConversationId('')
    setLastResult(null)
    setDraftQueryText('')
  }, [selectedDeploymentId])

  const scenarioSuggestions = useMemo(() => {
    const dynamic = suggestionsQuery.data?.suggestions ?? []
    return [...STATIC_SCENARIOS, ...dynamic].filter((value, index, array) => array.indexOf(value) === index)
  }, [suggestionsQuery.data?.suggestions])

  const runtimeUnavailable = !workspace?.deployment.runtimeBaseUrl

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

              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {scenarioSuggestions.map((scenario) => (
                  <Chip
                    key={scenario}
                    label={scenario}
                    clickable
                    onClick={() => setDraftQueryText(scenario)}
                    icon={<AutoFixHighRoundedIcon />}
                  />
                ))}
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

              <Stack direction="row" spacing={1.5}>
                <Button
                  variant="contained"
                  startIcon={<SendRoundedIcon />}
                  disabled={runtimeUnavailable || queryMutation.isPending || draftQueryText.trim().length === 0}
                  onClick={() => queryMutation.mutate()}
                >
                  {queryMutation.isPending ? 'Sending...' : 'Send to deployment'}
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
