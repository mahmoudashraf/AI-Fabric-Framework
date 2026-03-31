import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import RestoreRoundedIcon from '@mui/icons-material/RestoreRounded'
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
  clearDeploymentPocPromptSession,
  createDeploymentPromptRevision,
  fetchDeploymentDraft,
  fetchDeploymentPocPromptSession,
  fetchDeploymentPromptRevisions,
  queryDeploymentPocChat,
  restoreDeploymentPromptRevision,
  updateDeploymentPocPromptSession,
  type DeploymentPocChatQueryResponse,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const PROMPT_FIELDS = [
  {
    key: 'systemPrompt',
    label: 'System Prompt',
    description: 'Global assistant operating instructions and tone.',
  },
  {
    key: 'intentExtractionPrompt',
    label: 'Intent Extraction Prompt',
    description: 'Guidance for mapping user input into intents and tasks.',
  },
  {
    key: 'actionSelectionPrompt',
    label: 'Action Selection Prompt',
    description: 'Rules for choosing live read-only and write actions.',
  },
  {
    key: 'clarificationPrompt',
    label: 'Clarification Prompt',
    description: 'Follow-up behavior when more detail is needed from the user.',
  },
  {
    key: 'answerGenerationPrompt',
    label: 'Answer Generation Prompt',
    description: 'How final grounded answers should be composed.',
  },
  {
    key: 'retrievalPrompt',
    label: 'Retrieval Prompt',
    description: 'Instructions for using knowledge and vector search context.',
  },
  {
    key: 'assistantUiPrompt',
    label: 'Assistant UI Prompt',
    description: 'UI-facing behavioral prompt for embedded chat surfaces.',
  },
] as const

type PromptKey = (typeof PROMPT_FIELDS)[number]['key']
type PromptFormState = Record<PromptKey, string>

function defaultPromptState(): PromptFormState {
  return {
    systemPrompt: '',
    intentExtractionPrompt: '',
    actionSelectionPrompt: '',
    clarificationPrompt: '',
    answerGenerationPrompt: '',
    retrievalPrompt: '',
    assistantUiPrompt: '',
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function normalizePromptState(value: unknown): PromptFormState {
  const next = defaultPromptState()
  if (!isRecord(value)) {
    return next
  }

  for (const field of PROMPT_FIELDS) {
    const candidate = value[field.key]
    next[field.key] = typeof candidate === 'string' ? candidate : ''
  }
  return next
}

function countPopulatedPrompts(formState: PromptFormState) {
  return PROMPT_FIELDS.filter((field) => formState[field.key].trim().length > 0).length
}

function statesEqual(left: PromptFormState, right: PromptFormState) {
  return PROMPT_FIELDS.every((field) => left[field.key] === right[field.key])
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString()
}

function createPromptPreviewPayload(formState: PromptFormState) {
  const payload: Record<string, string> = {}
  for (const field of PROMPT_FIELDS) {
    const value = formState[field.key].trim()
    if (!value) {
      continue
    }
    payload[field.key] = value
  }
  return payload
}

function previewOutcomeSummary(response: DeploymentPocChatQueryResponse | undefined | null) {
  if (!response) {
    return {
      resultType: '—',
      answer: 'No preview run yet.',
      action: '—',
      routing: '—',
      evidence: '0',
    }
  }
  return {
    resultType: response.traceSummary?.resultType ?? readPreviewValue(response.result, 'type') ?? '—',
    answer:
      response.traceSummary?.answer ??
      response.traceSummary?.message ??
      readPreviewValue(response.result, 'message') ??
      response.message ??
      'No answer returned.',
    action: response.traceSummary?.executedAction ?? '—',
    routing: response.traceSummary?.routingStrategy ?? '—',
    evidence: String(response.traceSummary?.documentCount ?? 0),
  }
}

function readPreviewValue(result: unknown, key: string) {
  if (typeof result !== 'object' || result === null || Array.isArray(result)) {
    return null
  }
  const candidate = (result as Record<string, unknown>)[key]
  return typeof candidate === 'string' && candidate.trim().length > 0 ? candidate : null
}

export function PromptsPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formState, setFormState] = useState<PromptFormState>(defaultPromptState())
  const [revisionLabel, setRevisionLabel] = useState('')
  const [revisionSummary, setRevisionSummary] = useState('')
  const [previewQuery, setPreviewQuery] = useState('')
  const [hotApplyLabel, setHotApplyLabel] = useState('POC hot apply session')

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const promptRevisionsQuery = useQuery({
    queryKey: ['deployment-prompt-revisions', selectedDeploymentId],
    queryFn: () => fetchDeploymentPromptRevisions(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const promptSessionQuery = useQuery({
    queryKey: ['deployment-poc-prompt-session', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocPromptSession(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (!draftQuery.data) {
      return
    }
    setFormState(normalizePromptState(draftQuery.data.promptConfig))
  }, [draftQuery.data])

  const savedState = useMemo(
    () => normalizePromptState(draftQuery.data?.promptConfig),
    [draftQuery.data],
  )
  const draftDirty = useMemo(() => !statesEqual(formState, savedState), [formState, savedState])
  const populatedPromptCount = useMemo(() => countPopulatedPrompts(formState), [formState])
  const runtimeUnavailable = !workspace?.deployment.runtimeBaseUrl
  const previewPayload = useMemo(() => createPromptPreviewPayload(formState), [formState])

  const baselinePreviewMutation = useMutation({
    mutationFn: () =>
      queryDeploymentPocChat(selectedDeploymentId, {
        query: previewQuery.trim(),
      }),
  })

  const overlayPreviewMutation = useMutation({
    mutationFn: () =>
      queryDeploymentPocChat(selectedDeploymentId, {
        query: previewQuery.trim(),
        promptPreview: previewPayload,
      }),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!draftQuery.data) {
        throw new Error('No active draft available.')
      }
      return updateDeploymentDraft(draftQuery.data.id, { promptConfig: formState })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const createRevisionMutation = useMutation({
    mutationFn: () =>
      createDeploymentPromptRevision(selectedDeploymentId, {
        revisionLabel: revisionLabel.trim() || undefined,
        revisionSummary: revisionSummary.trim() || undefined,
      }),
    onSuccess: async () => {
      setRevisionLabel('')
      setRevisionSummary('')
      await queryClient.invalidateQueries({ queryKey: ['deployment-prompt-revisions', selectedDeploymentId] })
    },
  })

  const restoreRevisionMutation = useMutation({
    mutationFn: (revisionId: string) => restoreDeploymentPromptRevision(selectedDeploymentId, revisionId),
    onSuccess: async (draft) => {
      setFormState(normalizePromptState(draft.promptConfig))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const activatePromptSessionMutation = useMutation({
    mutationFn: () =>
      updateDeploymentPocPromptSession(selectedDeploymentId, {
        sessionLabel: hotApplyLabel.trim() || 'POC hot apply session',
        promptPreview: previewPayload,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-prompt-session', selectedDeploymentId],
      })
    },
  })

  const clearPromptSessionMutation = useMutation({
    mutationFn: () => clearDeploymentPocPromptSession(selectedDeploymentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-prompt-session', selectedDeploymentId],
      })
    },
  })

  return (
    <Stack spacing={3}>
      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt bundle</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Prompt changes are versioned deployment config. Save the draft here, then publish and apply when you
                want the revised prompt bundle to become part of a release.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Change type: Versioned config" color="primary" variant="outlined" />
              <Chip label="Action path: Save Draft -> Publish -> Apply" color="warning" />
              <Chip label={`Filled prompts: ${populatedPromptCount}/${PROMPT_FIELDS.length}`} variant="outlined" />
              {workspace?.draft ? <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" /> : null}
            </Stack>

            <Alert severity={draftDirty ? 'warning' : 'info'}>
              {draftDirty
                ? 'Prompt edits are pending in the editor. Save the draft before publishing or creating a prompt revision snapshot.'
                : 'Prompt bundle matches the saved draft. Revisions can be snapshotted from this saved state.'}
            </Alert>

            <Grid container spacing={2}>
              {PROMPT_FIELDS.map((field) => (
                <Grid item xs={12} md={6} key={field.key}>
                  <TextField
                    label={field.label}
                    helperText={field.description}
                    multiline
                    minRows={7}
                    fullWidth
                    value={formState[field.key]}
                    onChange={(event) =>
                      setFormState((previous) => ({
                        ...previous,
                        [field.key]: event.target.value,
                      }))
                    }
                  />
                </Grid>
              ))}
            </Grid>

            {saveMutation.isError ? (
              <Alert severity="error">
                {saveMutation.error instanceof Error ? saveMutation.error.message : 'Failed to save prompt draft'}
              </Alert>
            ) : null}
            {saveMutation.isSuccess ? <Alert severity="success">Prompt draft saved.</Alert> : null}

            <Stack direction="row" spacing={1.5}>
              <Button
                variant="contained"
                startIcon={<SaveRoundedIcon />}
                disabled={!draftQuery.data || saveMutation.isPending || !draftDirty}
                onClick={() => saveMutation.mutate()}
              >
                {saveMutation.isPending ? 'Saving...' : 'Save prompt draft'}
              </Button>
              <Button
                variant="outlined"
                onClick={() => {
                  setFormState(savedState)
                }}
              >
                Reset to saved
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">POC session hot apply</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Persist the current editor overlay for your deployment POC session so the embedded chatbot uses these
                prompt changes across multiple turns without saving, publishing, or applying a release.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Change type: Operator session overlay" color="secondary" variant="outlined" />
              <Chip label="Action path: Activate -> test in POC -> clear" color="success" variant="outlined" />
              <Chip
                label={
                  promptSessionQuery.data?.active
                    ? `Active session: ${promptSessionQuery.data.sessionLabel ?? 'POC hot apply session'}`
                    : 'No active POC hot apply session'
                }
                variant="outlined"
              />
            </Stack>

            {runtimeUnavailable ? (
              <Alert severity="warning">
                Apply the deployment before activating session hot apply. The POC console must have a live runtime URL.
              </Alert>
            ) : (
              <Alert severity="info">
                Session hot apply is operator-scoped and secured with the runtime admin key. It only affects the
                platform POC console for this deployment until you clear it.
              </Alert>
            )}

            <TextField
              label="Session label"
              placeholder="Workshop prompt tuning"
              fullWidth
              value={hotApplyLabel}
              onChange={(event) => setHotApplyLabel(event.target.value)}
            />

            {activatePromptSessionMutation.isError ? (
              <Alert severity="error">
                {activatePromptSessionMutation.error instanceof Error
                  ? activatePromptSessionMutation.error.message
                  : 'Failed to activate prompt hot apply session'}
              </Alert>
            ) : null}

            {clearPromptSessionMutation.isError ? (
              <Alert severity="error">
                {clearPromptSessionMutation.error instanceof Error
                  ? clearPromptSessionMutation.error.message
                  : 'Failed to clear prompt hot apply session'}
              </Alert>
            ) : null}

            {activatePromptSessionMutation.isSuccess ? (
              <Alert severity="success">
                Prompt hot apply is active for the deployment POC console. Open the POC page to test multi-turn
                behavior with this overlay.
              </Alert>
            ) : null}

            {clearPromptSessionMutation.isSuccess ? (
              <Alert severity="success">Prompt hot apply session cleared.</Alert>
            ) : null}

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <Card variant="outlined" sx={{ flex: 1 }}>
                <CardContent>
                  <Stack spacing={1}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Session status
                    </Typography>
                    <Typography variant="body2">
                      <strong>State:</strong> {promptSessionQuery.data?.active ? 'Active' : 'Inactive'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Prompt keys:</strong> {promptSessionQuery.data?.promptKeyCount ?? 0}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Updated:</strong>{' '}
                      {promptSessionQuery.data?.updatedAt ? formatDateTime(promptSessionQuery.data.updatedAt) : '—'}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {(promptSessionQuery.data?.promptKeys ?? []).map((key) => (
                        <Chip key={key} label={key} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ flex: 1 }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Actions
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Activate the current editor state as a persistent overlay for your operator session, then clear it
                      when you want the POC console back on the saved deployment behavior.
                    </Typography>
                    <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
                      <Button
                        variant="contained"
                        color="secondary"
                        startIcon={<AutoFixHighRoundedIcon />}
                        disabled={
                          runtimeUnavailable ||
                          Object.keys(previewPayload).length === 0 ||
                          activatePromptSessionMutation.isPending
                        }
                        onClick={() => activatePromptSessionMutation.mutate()}
                      >
                        {activatePromptSessionMutation.isPending ? 'Activating...' : 'Activate for POC session'}
                      </Button>
                      <Button
                        variant="outlined"
                        color="warning"
                        disabled={!promptSessionQuery.data?.active || clearPromptSessionMutation.isPending}
                        onClick={() => clearPromptSessionMutation.mutate()}
                      >
                        {clearPromptSessionMutation.isPending ? 'Clearing...' : 'Clear POC session'}
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt preview console</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Run the currently applied deployment through the embedded POC path, then compare it with an
                operator-only request-scoped preview overlay built from the prompt editor. Preview runs do not save,
                publish, or apply anything.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Change type: Operator preview" color="secondary" variant="outlined" />
              <Chip label="Action path: Run preview only" color="success" variant="outlined" />
              <Chip
                label={
                  draftDirty
                    ? 'Preview overlay includes unsaved prompt edits'
                    : 'Preview overlay matches the saved draft'
                }
                variant="outlined"
              />
            </Stack>

            {runtimeUnavailable ? (
              <Alert severity="warning">
                Apply the deployment before using prompt preview. This feature runs against the selected deployment
                runtime and requires a live runtime URL.
              </Alert>
            ) : (
              <Alert severity="info">
                Prompt preview is secured with the runtime admin key and only affects this request. Use it to test
                prompt behavior safely before deciding whether to save, publish, and apply.
              </Alert>
            )}

            <TextField
              label="Preview query"
              placeholder="What discounts can you offer before I cancel?"
              fullWidth
              minRows={3}
              multiline
              value={previewQuery}
              onChange={(event) => setPreviewQuery(event.target.value)}
            />

            <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
              <Button
                variant="outlined"
                startIcon={<PlayArrowRoundedIcon />}
                disabled={runtimeUnavailable || previewQuery.trim().length === 0 || baselinePreviewMutation.isPending}
                onClick={() => baselinePreviewMutation.mutate()}
              >
                {baselinePreviewMutation.isPending ? 'Running baseline...' : 'Run saved prompt bundle'}
              </Button>
              <Button
                variant="contained"
                color="secondary"
                startIcon={<AutoFixHighRoundedIcon />}
                disabled={runtimeUnavailable || previewQuery.trim().length === 0 || overlayPreviewMutation.isPending}
                onClick={() => overlayPreviewMutation.mutate()}
              >
                {overlayPreviewMutation.isPending ? 'Running preview...' : 'Run preview overlay'}
              </Button>
            </Stack>

            {baselinePreviewMutation.isError ? (
              <Alert severity="error">
                {baselinePreviewMutation.error instanceof Error
                  ? baselinePreviewMutation.error.message
                  : 'Failed to run the saved prompt bundle preview'}
              </Alert>
            ) : null}

            {overlayPreviewMutation.isError ? (
              <Alert severity="error">
                {overlayPreviewMutation.error instanceof Error
                  ? overlayPreviewMutation.error.message
                  : 'Failed to run the prompt preview overlay'}
              </Alert>
            ) : null}

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Currently applied deployment
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Uses the active runtime prompt behavior with no preview overlay. Helpful as the baseline before
                        previewing editor changes.
                      </Typography>
                      <Divider />
                      {(() => {
                        const summary = previewOutcomeSummary(baselinePreviewMutation.data)
                        return (
                          <Stack spacing={1}>
                            <Typography variant="body2">
                              <strong>Result:</strong> {summary.resultType}
                            </Typography>
                            <Typography variant="body2">
                              <strong>Action:</strong> {summary.action}
                            </Typography>
                            <Typography variant="body2">
                              <strong>Routing:</strong> {summary.routing}
                            </Typography>
                            <Typography variant="body2">
                              <strong>Evidence docs:</strong> {summary.evidence}
                            </Typography>
                            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                              <strong>Answer:</strong> {summary.answer}
                            </Typography>
                          </Stack>
                        )
                      })()}
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Current editor preview overlay
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Uses the current prompt editor state, including unsaved changes, for this request only.
                      </Typography>
                      <Divider />
                      {(() => {
                        const summary = previewOutcomeSummary(overlayPreviewMutation.data)
                        return (
                          <Stack spacing={1}>
                            <Typography variant="body2">
                              <strong>Result:</strong> {summary.resultType}
                            </Typography>
                            <Typography variant="body2">
                              <strong>Action:</strong> {summary.action}
                            </Typography>
                            <Typography variant="body2">
                              <strong>Routing:</strong> {summary.routing}
                            </Typography>
                            <Typography variant="body2">
                              <strong>Evidence docs:</strong> {summary.evidence}
                            </Typography>
                            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                              <strong>Answer:</strong> {summary.answer}
                            </Typography>
                          </Stack>
                        )
                      })()}
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt revisions</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Prompt revisions are lightweight prompt-only snapshots inside the deployment workspace. They are useful
                for restoring prompt behavior quickly without rolling back the rest of the deployment draft.
              </Typography>
            </Box>

            {draftDirty ? (
              <Alert severity="warning">
                Save the prompt draft before creating a prompt revision. Revisions snapshot the current saved draft
                state, not unsaved editor changes.
              </Alert>
            ) : null}

            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <TextField
                  label="Revision label"
                  placeholder="Grounded commerce baseline"
                  fullWidth
                  value={revisionLabel}
                  onChange={(event) => setRevisionLabel(event.target.value)}
                />
              </Grid>
              <Grid item xs={12} md={8}>
                <TextField
                  label="Revision summary"
                  placeholder="What changed and why?"
                  fullWidth
                  value={revisionSummary}
                  onChange={(event) => setRevisionSummary(event.target.value)}
                />
              </Grid>
            </Grid>

            {createRevisionMutation.isError ? (
              <Alert severity="error">
                {createRevisionMutation.error instanceof Error
                  ? createRevisionMutation.error.message
                  : 'Failed to create prompt revision'}
              </Alert>
            ) : null}
            {createRevisionMutation.isSuccess ? (
              <Alert severity="success">Prompt revision created from the current saved draft.</Alert>
            ) : null}

            <Stack direction="row" spacing={1.5}>
              <Button
                variant="contained"
                startIcon={<HistoryRoundedIcon />}
                disabled={!selectedDeploymentId || draftDirty || createRevisionMutation.isPending}
                onClick={() => createRevisionMutation.mutate()}
              >
                {createRevisionMutation.isPending ? 'Creating...' : 'Create prompt revision'}
              </Button>
            </Stack>

            {restoreRevisionMutation.isError ? (
              <Alert severity="error">
                {restoreRevisionMutation.error instanceof Error
                  ? restoreRevisionMutation.error.message
                  : 'Failed to restore prompt revision'}
              </Alert>
            ) : null}

            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Revision</TableCell>
                  <TableCell>Summary</TableCell>
                  <TableCell>Filled prompts</TableCell>
                  <TableCell>Created by</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(promptRevisionsQuery.data ?? []).map((revision) => (
                  <TableRow key={revision.id} hover>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {revision.revisionLabel}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {revision.id}
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell>{revision.revisionSummary || '—'}</TableCell>
                    <TableCell>{revision.populatedPromptCount}</TableCell>
                    <TableCell>{revision.createdByDisplayName || revision.createdByActorId}</TableCell>
                    <TableCell>{formatDateTime(revision.createdAt)}</TableCell>
                    <TableCell align="right">
                      <Button
                        variant="outlined"
                        size="small"
                        startIcon={<RestoreRoundedIcon />}
                        disabled={restoreRevisionMutation.isPending}
                        onClick={() => restoreRevisionMutation.mutate(revision.id)}
                      >
                        Restore to draft
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {(promptRevisionsQuery.data ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography variant="body2" color="text.secondary">
                        No prompt revisions yet. Save the prompt draft and snapshot the first revision here.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
