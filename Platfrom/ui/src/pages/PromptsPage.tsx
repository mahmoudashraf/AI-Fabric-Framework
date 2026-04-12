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
  applyDeploymentCuratedModule,
  clearDeploymentPocPromptSession,
  createDeploymentPromptRevision,
  fetchDeploymentCuratedModules,
  fetchDeploymentDraft,
  fetchDeploymentPromptBaseline,
  fetchDeploymentPocPromptSession,
  fetchDeploymentPromptRevisions,
  queryDeploymentPocChat,
  restoreDeploymentPromptRevision,
  updateDeploymentPocPromptSession,
  type DeploymentPocChatQueryResponse,
  type DeploymentCuratedModuleSummary,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

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
type SmartSuggestionsMode = 'inherit' | 'enabled' | 'disabled'
type PromptFormState = Record<PromptKey, string> & {
  ragSimilarityThreshold: string
  ragMaxDocumentsUsedForContext: string
  ragMaxContextChars: string
  smartSuggestionsMode: SmartSuggestionsMode
}
type PromptDiffStatus = 'Unchanged' | 'Added' | 'Removed' | 'Modified'
type PromptDiffRow = {
  key: PromptKey
  label: string
  description: string
  baseline: string
  draft: string
  status: PromptDiffStatus
}

function defaultPromptState(): PromptFormState {
  return {
    systemPrompt: '',
    intentExtractionPrompt: '',
    actionSelectionPrompt: '',
    clarificationPrompt: '',
    answerGenerationPrompt: '',
    retrievalPrompt: '',
    assistantUiPrompt: '',
    ragSimilarityThreshold: '',
    ragMaxDocumentsUsedForContext: '',
    ragMaxContextChars: '',
    smartSuggestionsMode: 'inherit',
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

  const thresholdCandidate = value.ragSimilarityThreshold
  if (typeof thresholdCandidate === 'number' && Number.isFinite(thresholdCandidate)) {
    next.ragSimilarityThreshold = String(thresholdCandidate)
  } else if (typeof thresholdCandidate === 'string' && thresholdCandidate.trim().length > 0) {
    next.ragSimilarityThreshold = thresholdCandidate.trim()
  }

  const maxDocumentsCandidate = value.ragMaxDocumentsUsedForContext
  if (typeof maxDocumentsCandidate === 'number' && Number.isFinite(maxDocumentsCandidate)) {
    next.ragMaxDocumentsUsedForContext = String(maxDocumentsCandidate)
  } else if (typeof maxDocumentsCandidate === 'string' && maxDocumentsCandidate.trim().length > 0) {
    next.ragMaxDocumentsUsedForContext = maxDocumentsCandidate.trim()
  }

  const maxContextCharsCandidate = value.ragMaxContextChars
  if (typeof maxContextCharsCandidate === 'number' && Number.isFinite(maxContextCharsCandidate)) {
    next.ragMaxContextChars = String(maxContextCharsCandidate)
  } else if (typeof maxContextCharsCandidate === 'string' && maxContextCharsCandidate.trim().length > 0) {
    next.ragMaxContextChars = maxContextCharsCandidate.trim()
  }

  const smartSuggestionsCandidate = value.smartSuggestionsEnabled
  if (typeof smartSuggestionsCandidate === 'boolean') {
    next.smartSuggestionsMode = smartSuggestionsCandidate ? 'enabled' : 'disabled'
  } else if (typeof smartSuggestionsCandidate === 'string') {
    const normalized = smartSuggestionsCandidate.trim().toLowerCase()
    if (normalized === 'true') {
      next.smartSuggestionsMode = 'enabled'
    } else if (normalized === 'false') {
      next.smartSuggestionsMode = 'disabled'
    }
  }
  return next
}

function readCuratedModuleId(value: unknown): string {
  if (!isRecord(value)) {
    return 'default'
  }
  const curatedModuleId = value.curatedModuleId
  return typeof curatedModuleId === 'string' && curatedModuleId.trim().length > 0
    ? curatedModuleId
    : 'default'
}

function countPopulatedPrompts(formState: PromptFormState) {
  return PROMPT_FIELDS.filter((field) => formState[field.key].trim().length > 0).length
}

function statesEqual(left: PromptFormState, right: PromptFormState) {
  return (
    PROMPT_FIELDS.every((field) => left[field.key] === right[field.key]) &&
    left.ragSimilarityThreshold === right.ragSimilarityThreshold &&
    left.ragMaxDocumentsUsedForContext === right.ragMaxDocumentsUsedForContext &&
    left.ragMaxContextChars === right.ragMaxContextChars &&
    left.smartSuggestionsMode === right.smartSuggestionsMode
  )
}

function diffStatusForPrompt(baseline: string, draft: string): PromptDiffStatus {
  const baselineValue = baseline.trim()
  const draftValue = draft.trim()
  if (!baselineValue && !draftValue) {
    return 'Unchanged'
  }
  if (!baselineValue && draftValue) {
    return 'Added'
  }
  if (baselineValue && !draftValue) {
    return 'Removed'
  }
  return baselineValue === draftValue ? 'Unchanged' : 'Modified'
}

function diffStatusColor(status: PromptDiffStatus): 'default' | 'success' | 'warning' | 'error' | 'info' {
  switch (status) {
    case 'Added':
      return 'success'
    case 'Removed':
      return 'error'
    case 'Modified':
      return 'warning'
    default:
      return 'default'
  }
}

function buildPromptDiffRows(baseline: PromptFormState, draft: PromptFormState): PromptDiffRow[] {
  return PROMPT_FIELDS.map((field) => {
    const baselineValue = baseline[field.key]
    const draftValue = draft[field.key]
    return {
      key: field.key,
      label: field.label,
      description: field.description,
      baseline: baselineValue,
      draft: draftValue,
      status: diffStatusForPrompt(baselineValue, draftValue),
    }
  })
}

function diffSummary(rows: PromptDiffRow[]) {
  const changed = rows.filter((row) => row.status !== 'Unchanged').length
  const added = rows.filter((row) => row.status === 'Added').length
  const removed = rows.filter((row) => row.status === 'Removed').length
  const modified = rows.filter((row) => row.status === 'Modified').length
  return { changed, added, removed, modified }
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

function parseRagSimilarityThreshold(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 1) {
    return null
  }
  return parsed
}

function parsePositiveInteger(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  if (!/^\d+$/.test(trimmed)) {
    return null
  }
  const parsed = Number(trimmed)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function createPromptConfigPayload(formState: PromptFormState) {
  const payload: Record<string, string | number | boolean> = createPromptPreviewPayload(formState)
  const ragSimilarityThreshold = parseRagSimilarityThreshold(formState.ragSimilarityThreshold)
  if (ragSimilarityThreshold !== null) {
    payload.ragSimilarityThreshold = ragSimilarityThreshold
  }
  const ragMaxDocumentsUsedForContext = parsePositiveInteger(formState.ragMaxDocumentsUsedForContext)
  if (ragMaxDocumentsUsedForContext !== null) {
    payload.ragMaxDocumentsUsedForContext = ragMaxDocumentsUsedForContext
  }
  const ragMaxContextChars = parsePositiveInteger(formState.ragMaxContextChars)
  if (ragMaxContextChars !== null) {
    payload.ragMaxContextChars = ragMaxContextChars
  }
  if (formState.smartSuggestionsMode === 'enabled') {
    payload.smartSuggestionsEnabled = true
  } else if (formState.smartSuggestionsMode === 'disabled') {
    payload.smartSuggestionsEnabled = false
  }
  return payload
}

function formatSmartSuggestionsMode(mode: SmartSuggestionsMode) {
  switch (mode) {
    case 'enabled':
      return 'Enabled'
    case 'disabled':
      return 'Disabled'
    default:
      return 'Framework default'
  }
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

  const curatedModulesQuery = useQuery({
    queryKey: ['deployment-curated-modules'],
    queryFn: fetchDeploymentCuratedModules,
  })

  const baselineQuery = useQuery({
    queryKey: ['deployment-prompt-baseline', selectedDeploymentId],
    queryFn: () => fetchDeploymentPromptBaseline(selectedDeploymentId),
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
  const baselineState = useMemo(
    () => normalizePromptState(baselineQuery.data?.promptConfig),
    [baselineQuery.data],
  )
  const curatedModules = curatedModulesQuery.data ?? []
  const currentCuratedModuleId = useMemo(
    () => readCuratedModuleId(draftQuery.data?.providerConfig),
    [draftQuery.data?.providerConfig],
  )
  const currentCuratedModule = useMemo<DeploymentCuratedModuleSummary | null>(
    () => curatedModules.find((module) => module.id === currentCuratedModuleId) ?? null,
    [curatedModules, currentCuratedModuleId],
  )
  const diffRows = useMemo(
    () => buildPromptDiffRows(baselineState, savedState),
    [baselineState, savedState],
  )
  const diffStats = useMemo(() => diffSummary(diffRows), [diffRows])
  const draftDirty = useMemo(() => !statesEqual(formState, savedState), [formState, savedState])
  const parsedRagSimilarityThreshold = useMemo(
    () => parseRagSimilarityThreshold(formState.ragSimilarityThreshold),
    [formState.ragSimilarityThreshold],
  )
  const parsedRagMaxDocumentsUsedForContext = useMemo(
    () => parsePositiveInteger(formState.ragMaxDocumentsUsedForContext),
    [formState.ragMaxDocumentsUsedForContext],
  )
  const parsedRagMaxContextChars = useMemo(
    () => parsePositiveInteger(formState.ragMaxContextChars),
    [formState.ragMaxContextChars],
  )
  const ragSimilarityThresholdError =
    formState.ragSimilarityThreshold.trim().length > 0 && parsedRagSimilarityThreshold === null
      ? 'Enter a number between 0.0 and 1.0.'
      : null
  const ragMaxDocumentsUsedForContextError =
    formState.ragMaxDocumentsUsedForContext.trim().length > 0 && parsedRagMaxDocumentsUsedForContext === null
      ? 'Enter a positive whole number.'
      : null
  const ragMaxContextCharsError =
    formState.ragMaxContextChars.trim().length > 0 && parsedRagMaxContextChars === null
      ? 'Enter a positive whole number.'
      : null
  const promptConfigHasValidationErrors = Boolean(
    ragSimilarityThresholdError || ragMaxDocumentsUsedForContextError || ragMaxContextCharsError,
  )
  const editorState = useMemo(
    () => ({
      dirty: draftDirty,
      label: 'Prompt editor',
      description: draftDirty
        ? 'Prompt edits exist only in the current browser buffer until you save the deployment draft or use a session-scoped hot apply.'
        : 'Prompt editor matches the saved deployment draft.',
    }),
    [draftDirty],
  )
  useDeploymentWorkspaceEditorState(selectedDeploymentId ? editorState : null)
  const savedPromptCount = useMemo(() => countPopulatedPrompts(savedState), [savedState])
  const populatedPromptCount = useMemo(() => countPopulatedPrompts(formState), [formState])
  const publishedPromptCount = baselineQuery.data?.populatedPromptCount ?? countPopulatedPrompts(baselineState)
  const savedRagSimilarityThreshold = savedState.ragSimilarityThreshold.trim() || '—'
  const publishedRagSimilarityThreshold = baselineState.ragSimilarityThreshold.trim() || '—'
  const currentRagMaxDocumentsUsedForContext = formState.ragMaxDocumentsUsedForContext.trim() || '—'
  const savedRagMaxDocumentsUsedForContext = savedState.ragMaxDocumentsUsedForContext.trim() || '—'
  const publishedRagMaxDocumentsUsedForContext = baselineState.ragMaxDocumentsUsedForContext.trim() || '—'
  const currentRagMaxContextChars = formState.ragMaxContextChars.trim() || '—'
  const savedRagMaxContextChars = savedState.ragMaxContextChars.trim() || '—'
  const publishedRagMaxContextChars = baselineState.ragMaxContextChars.trim() || '—'
  const currentSmartSuggestionsMode = formatSmartSuggestionsMode(formState.smartSuggestionsMode)
  const savedSmartSuggestionsMode = formatSmartSuggestionsMode(savedState.smartSuggestionsMode)
  const publishedSmartSuggestionsMode = formatSmartSuggestionsMode(baselineState.smartSuggestionsMode)
  const runtimeUnavailable = !workspace?.deployment.runtimeBaseUrl
  const canEdit = workspace?.access.canEdit ?? false
  const canOperate = workspace?.access.canOperate ?? false
  const previewPayload = useMemo(() => createPromptPreviewPayload(formState), [formState])
  const promptSession = promptSessionQuery.data

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
      return updateDeploymentDraft(draftQuery.data.id, { promptConfig: createPromptConfigPayload(formState) })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const applyCuratedModuleMutation = useMutation({
    mutationFn: (curatedModuleId: string) =>
      applyDeploymentCuratedModule(selectedDeploymentId, { curatedModuleId }),
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
              <Typography variant="h6">Prompt posture</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                Keep the browser editor buffer, saved deployment draft, published prompt baseline, and any active POC
                session override clearly separated while tuning behavior.
              </Typography>
            </Box>

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label={`Editor buffer: ${draftDirty ? 'dirty' : 'saved'}`} color={draftDirty ? 'warning' : 'success'} />
              <Chip
                label={baselineQuery.data?.versionLabel ? `Published baseline: ${baselineQuery.data.versionLabel}` : 'Published baseline: none'}
                variant="outlined"
              />
              <Chip
                label={promptSession?.active ? 'POC override: active' : 'POC override: inactive'}
                color={promptSession?.active ? 'secondary' : 'default'}
                variant="outlined"
              />
              {workspace?.deployment.activeVersion ? (
                <Chip label={`Deployment active version: ${workspace.deployment.activeVersion}`} variant="outlined" />
              ) : null}
            </Stack>

            <Grid container spacing={2}>
              <Grid item xs={12} md={6} lg={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Editor Buffer
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {draftDirty ? 'Unsaved changes' : 'Matches saved draft'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Local browser state only. Nothing changes in the deployment until you save or activate a
                        session override.
                      </Typography>
                      <Chip
                        label={`Filled prompts: ${populatedPromptCount}/${PROMPT_FIELDS.length}`}
                        color={draftDirty ? 'warning' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                      <Chip
                        label={`RAG threshold: ${formState.ragSimilarityThreshold.trim() || '—'}`}
                        color={draftDirty ? 'warning' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                      <Chip
                        label={`Context docs: ${currentRagMaxDocumentsUsedForContext}`}
                        color={draftDirty ? 'warning' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                      <Chip
                        label={`Context chars: ${currentRagMaxContextChars}`}
                        color={draftDirty ? 'warning' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                      <Chip
                        label={`Smart suggestions: ${currentSmartSuggestionsMode}`}
                        color={draftDirty ? 'warning' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6} lg={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Saved Draft
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {workspace?.draft ? `r${workspace.draft.revisionNumber}` : 'Draft'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Version-controlled config stored in the platform. Publish from this state when it is ready.
                      </Typography>
                      <Chip label={`Saved prompts: ${savedPromptCount}/${PROMPT_FIELDS.length}`} size="small" variant="outlined" />
                      <Chip label={`Saved RAG threshold: ${savedRagSimilarityThreshold}`} size="small" variant="outlined" />
                      <Chip label={`Saved context docs: ${savedRagMaxDocumentsUsedForContext}`} size="small" variant="outlined" />
                      <Chip label={`Saved context chars: ${savedRagMaxContextChars}`} size="small" variant="outlined" />
                      <Chip label={`Saved smart suggestions: ${savedSmartSuggestionsMode}`} size="small" variant="outlined" />
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6} lg={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Published Baseline
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {baselineQuery.data?.versionLabel ?? 'Not published'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Last published prompt bundle used for release comparison. It does not change when you edit the
                        draft.
                      </Typography>
                      <Chip label={`Published prompts: ${publishedPromptCount}/${PROMPT_FIELDS.length}`} size="small" variant="outlined" />
                      <Chip label={`Published RAG threshold: ${publishedRagSimilarityThreshold}`} size="small" variant="outlined" />
                      <Chip label={`Published context docs: ${publishedRagMaxDocumentsUsedForContext}`} size="small" variant="outlined" />
                      <Chip label={`Published context chars: ${publishedRagMaxContextChars}`} size="small" variant="outlined" />
                      <Chip label={`Published smart suggestions: ${publishedSmartSuggestionsMode}`} size="small" variant="outlined" />
                      <Typography variant="caption" color="text.secondary">
                        {baselineQuery.data?.publishedAt
                          ? `Published ${formatDateTime(baselineQuery.data.publishedAt)}`
                          : 'Publish a version to establish a baseline.'}
                      </Typography>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6} lg={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        POC Session Override
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {promptSession?.active ? 'Active' : 'Inactive'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Operator-scoped overlay for the platform POC console only. It keeps the last activated values,
                        not the live editor buffer.
                      </Typography>
                      <Chip
                        label={promptSession?.active
                          ? `${promptSession.promptKeyCount} prompt override${promptSession.promptKeyCount === 1 ? '' : 's'}`
                          : 'No prompt overrides'}
                        size="small"
                        color={promptSession?.active ? 'secondary' : 'default'}
                        variant="outlined"
                      />
                      <Typography variant="caption" color="text.secondary">
                        {promptSession?.updatedAt
                          ? `Updated ${formatDateTime(promptSession.updatedAt)}`
                          : 'Activate from the card below when you want to test without publish/apply.'}
                      </Typography>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            {promptSession?.active ? (
              <Alert severity="info">
                The embedded POC console is currently using the active session override. Saving the draft or changing
                the editor does not change the live POC session until you activate the override again or clear it.
              </Alert>
            ) : draftDirty ? (
              <Alert severity="warning">
                Editor changes exist only in this browser session right now. Save them to update the deployment draft,
                or activate POC hot apply to test them without publishing.
              </Alert>
            ) : baselineQuery.data?.versionId ? (
              <Alert severity="success">
                The saved draft is cleanly separated from the published baseline. Use the diff and preview sections
                below to decide whether the next publish is ready.
              </Alert>
            ) : (
              <Alert severity="info">
                No published prompt baseline exists yet. Save the draft, validate it in POC, then publish to establish
                the first release-grade prompt bundle.
              </Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

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
              <Chip label={`RAG threshold: ${formState.ragSimilarityThreshold.trim() || '—'}`} variant="outlined" />
              <Chip label={`Context docs: ${currentRagMaxDocumentsUsedForContext}`} variant="outlined" />
              <Chip label={`Context chars: ${currentRagMaxContextChars}`} variant="outlined" />
              <Chip label={`Smart suggestions: ${currentSmartSuggestionsMode}`} variant="outlined" />
              {workspace?.draft ? <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" /> : null}
              {currentCuratedModule ? (
                <Chip label={`Curated module: ${currentCuratedModule.name}`} color="secondary" variant="outlined" />
              ) : null}
            </Stack>

            <Alert severity={draftDirty ? 'warning' : 'info'}>
              {draftDirty
                ? 'Prompt edits are pending in the editor. Save the draft before publishing or creating a prompt revision snapshot.'
                : 'Prompt bundle matches the saved draft. Revisions can be snapshotted from this saved state.'}
            </Alert>
            {!canEdit ? (
              <Alert severity="info">
                Saving prompt config and managing prompt revisions requires deployment editor access or higher.
              </Alert>
            ) : null}

            <Card variant="outlined">
              <CardContent>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Curated module baseline
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Curated modules seed the deployment-owned prompt bundle. Reapplying a module replaces the saved
                      draft prompt bundle with that module baseline and updates the runtime curated pack metadata for
                      the next publish/apply.
                    </Typography>
                  </Box>

                  {currentCuratedModule ? (
                    <Alert severity="info">
                      Current draft baseline: <strong>{currentCuratedModule.name}</strong>.
                      {currentCuratedModule.runtimeCuratedPack ? (
                        <> Runtime curated pack: <strong>{currentCuratedModule.runtimeCuratedPack}</strong>.</>
                      ) : (
                        <> This module relies on the platform prompt preset without a runtime curated pack override.</>
                      )}
                    </Alert>
                  ) : null}

                  {applyCuratedModuleMutation.isError ? (
                    <Alert severity="error">
                      {applyCuratedModuleMutation.error instanceof Error
                        ? applyCuratedModuleMutation.error.message
                        : 'Failed to apply the curated module baseline.'}
                    </Alert>
                  ) : null}
                  {applyCuratedModuleMutation.isSuccess ? (
                    <Alert severity="success">
                      Curated module baseline applied to the saved draft. Review the updated prompts below, then publish
                      and apply when ready.
                    </Alert>
                  ) : null}

                  <Grid container spacing={1.5}>
                    {curatedModules.map((module) => (
                      <Grid item xs={12} md={6} key={module.id}>
                        <Card variant="outlined" sx={{ height: '100%' }}>
                          <CardContent>
                            <Stack spacing={1.25}>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                  {module.name}
                                </Typography>
                                <Chip size="small" label={`Preset: ${module.promptPresetId}`} variant="outlined" />
                                {module.runtimeCuratedPack ? (
                                  <Chip size="small" label={`Runtime pack: ${module.runtimeCuratedPack}`} />
                                ) : null}
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {module.description}
                              </Typography>
                              <Button
                                variant={module.id === currentCuratedModuleId ? 'contained' : 'outlined'}
                                disabled={!canEdit || applyCuratedModuleMutation.isPending || module.id === currentCuratedModuleId}
                                onClick={() => applyCuratedModuleMutation.mutate(module.id)}
                              >
                                {module.id === currentCuratedModuleId ? 'Current module' : 'Apply baseline to draft'}
                              </Button>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                </Stack>
              </CardContent>
            </Card>

            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                          Retrieval tuning
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          Minimum similarity score required for deployment RAG retrieval. Lower values widen recall and
                          can help broad catalog summaries; higher values keep grounding stricter.
                        </Typography>
                      </Box>
                      <TextField
                        label="RAG similarity threshold"
                        type="number"
                        fullWidth
                        value={formState.ragSimilarityThreshold}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            ragSimilarityThreshold: event.target.value,
                          }))
                        }
                        inputProps={{ min: 0, max: 1, step: 0.05 }}
                        error={Boolean(ragSimilarityThresholdError)}
                        helperText={
                          ragSimilarityThresholdError ??
                          'Deployment-scoped retrieval threshold. Demo and canonical commerce rollouts default to 0.1.'
                        }
                      />
                      <TextField
                        label="Max context documents"
                        type="number"
                        fullWidth
                        value={formState.ragMaxDocumentsUsedForContext}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            ragMaxDocumentsUsedForContext: event.target.value,
                          }))
                        }
                        inputProps={{ min: 1, step: 1 }}
                        error={Boolean(ragMaxDocumentsUsedForContextError)}
                        helperText={
                          ragMaxDocumentsUsedForContextError ??
                          'Optional deployment override for how many retrieved documents are used to ground final answer generation.'
                        }
                      />
                      <TextField
                        label="Max context characters"
                        type="number"
                        fullWidth
                        value={formState.ragMaxContextChars}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            ragMaxContextChars: event.target.value,
                          }))
                        }
                        inputProps={{ min: 1, step: 100 }}
                        error={Boolean(ragMaxContextCharsError)}
                        helperText={
                          ragMaxContextCharsError ??
                          'Optional deployment override for the total retrieved context characters passed into final answer generation.'
                        }
                      />
                      <TextField
                        select
                        label="Smart suggestions"
                        fullWidth
                        value={formState.smartSuggestionsMode}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            smartSuggestionsMode: event.target.value as SmartSuggestionsMode,
                          }))
                        }
                        helperText="Controls whether the orchestrator runs the extra smart-suggestion enrichment step. Use framework default to inherit the mode-level baseline."
                      >
                        <MenuItem value="inherit">Use framework default</MenuItem>
                        <MenuItem value="enabled">Enabled</MenuItem>
                        <MenuItem value="disabled">Disabled</MenuItem>
                      </TextField>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
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
                disabled={!canEdit || !draftQuery.data || saveMutation.isPending || !draftDirty || promptConfigHasValidationErrors}
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
              <Typography variant="h6">Prompt baseline comparison</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Compare the saved draft prompt bundle with the most recently published version. This view helps you
                understand what will change before publishing.
              </Typography>
            </Box>

            {draftDirty ? (
              <Alert severity="warning">
                This comparison uses the saved draft. Save your in-editor changes to refresh the diff.
              </Alert>
            ) : null}

            {baselineQuery.isLoading ? (
              <Typography color="text.secondary">Loading the latest published prompt bundle...</Typography>
            ) : baselineQuery.isError ? (
              <Alert severity="error">
                {baselineQuery.error instanceof Error
                  ? baselineQuery.error.message
                  : 'Failed to load the published prompt baseline.'}
              </Alert>
            ) : baselineQuery.data?.versionId ? (
              <>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip
                    label={`Published version: ${baselineQuery.data.versionLabel ?? baselineQuery.data.versionId}`}
                    variant="outlined"
                  />
                  <Chip
                    label={`Published at: ${baselineQuery.data.publishedAt ? formatDateTime(baselineQuery.data.publishedAt) : '—'}`}
                    variant="outlined"
                  />
                  <Chip label={`Published prompts: ${baselineQuery.data.populatedPromptCount}`} variant="outlined" />
                  <Chip label={`Published RAG threshold: ${publishedRagSimilarityThreshold}`} variant="outlined" />
                  <Chip label={`Draft RAG threshold: ${savedRagSimilarityThreshold}`} variant="outlined" />
                  <Chip label={`Changed prompts: ${diffStats.changed}`} color={diffStats.changed > 0 ? 'warning' : 'success'} />
                  {diffStats.added > 0 ? <Chip label={`Added: ${diffStats.added}`} color="success" variant="outlined" /> : null}
                  {diffStats.modified > 0 ? <Chip label={`Modified: ${diffStats.modified}`} color="warning" variant="outlined" /> : null}
                  {diffStats.removed > 0 ? <Chip label={`Removed: ${diffStats.removed}`} color="error" variant="outlined" /> : null}
                </Stack>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Prompt</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Published</TableCell>
                      <TableCell>Draft</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {diffRows.map((row) => (
                      <TableRow key={row.key} hover>
                        <TableCell sx={{ width: 220 }}>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {row.label}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {row.description}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ width: 140 }}>
                          <Chip label={row.status} size="small" color={diffStatusColor(row.status)} />
                        </TableCell>
                        <TableCell sx={{ width: '35%' }}>
                          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                            {row.baseline.trim().length > 0 ? row.baseline : '—'}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ width: '35%' }}>
                          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                            {row.draft.trim().length > 0 ? row.draft : '—'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </>
            ) : (
              <Alert severity="info">
                No published prompt bundle yet. Publish the draft to establish a baseline for comparison.
              </Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Release prompt preview</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                This view renders the saved draft prompt bundle exactly as it would ship with the next publish. Use it
                to scan for tone, missing guidance, or gaps before creating a release.
              </Typography>
            </Box>

            {draftDirty ? (
              <Alert severity="warning">
                The preview reflects the saved draft only. Save the editor changes above to update this preview.
              </Alert>
            ) : null}

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label={`Preview prompts: ${populatedPromptCount}/${PROMPT_FIELDS.length}`} variant="outlined" />
              <Chip label={`Saved RAG threshold: ${savedRagSimilarityThreshold}`} variant="outlined" />
              {workspace?.draft ? <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" /> : null}
            </Stack>

            <Grid container spacing={2}>
              {PROMPT_FIELDS.map((field) => {
                const value = savedState[field.key]
                return (
                  <Grid item xs={12} md={6} key={field.key}>
                    <Card variant="outlined">
                      <CardContent>
                        <Stack spacing={1}>
                          <Stack spacing={0.25}>
                            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                              {field.label}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {field.description}
                            </Typography>
                          </Stack>
                          <Divider />
                          <Typography
                            variant="body2"
                            color={value.trim().length > 0 ? 'text.primary' : 'text.secondary'}
                            sx={{ whiteSpace: 'pre-wrap' }}
                          >
                            {value.trim().length > 0 ? value : 'Not set yet.'}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                )
              })}
            </Grid>
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
                  promptSession?.active
                    ? `Active session: ${promptSession.sessionLabel ?? 'POC hot apply session'}`
                    : 'No active POC hot apply session'
                }
                variant="outlined"
              />
            </Stack>

            {runtimeUnavailable ? (
              <Alert severity="warning">
                Apply the deployment before activating session hot apply. The POC console must have a live runtime URL.
              </Alert>
            ) : !canOperate ? (
              <Alert severity="info">
                POC hot apply is available to deployment operators and above.
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
                      <strong>State:</strong> {promptSession?.active ? 'Active' : 'Inactive'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Prompt keys:</strong> {promptSession?.promptKeyCount ?? 0}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Updated:</strong>{' '}
                      {promptSession?.updatedAt ? formatDateTime(promptSession.updatedAt) : '—'}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {(promptSession?.promptKeys ?? []).map((key) => (
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
                          !canOperate ||
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
                        disabled={!canOperate || !promptSession?.active || clearPromptSessionMutation.isPending}
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
            ) : !canOperate ? (
              <Alert severity="info">
                Prompt preview and live POC evaluation require deployment operator access or higher.
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
                disabled={!canOperate || runtimeUnavailable || previewQuery.trim().length === 0 || baselinePreviewMutation.isPending}
                onClick={() => baselinePreviewMutation.mutate()}
              >
                {baselinePreviewMutation.isPending ? 'Running baseline...' : 'Run saved prompt bundle'}
              </Button>
              <Button
                variant="contained"
                color="secondary"
                startIcon={<AutoFixHighRoundedIcon />}
                disabled={!canOperate || runtimeUnavailable || previewQuery.trim().length === 0 || overlayPreviewMutation.isPending}
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
                disabled={!canEdit || !selectedDeploymentId || draftDirty || createRevisionMutation.isPending}
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
                        disabled={!canEdit || restoreRevisionMutation.isPending}
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
