import { deploymentTerm } from '../deploymentTerminology'
import { ConfigurationSurface } from '../components/ConfigurationSurface'
import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import RestoreRoundedIcon from '@mui/icons-material/RestoreRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  applyDeploymentCuratedModule,
  clearDeploymentPocPromptSession,
  composeDeploymentAiBehaviour,
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
import { AiBehaviourEditor } from '../components/AiBehaviourEditor'
import { effectiveAiBehaviourPreview, promptConfigIdentity, promptConfigRecord, readAiBehaviour } from '../aiBehaviourProfiles'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'
import { useBrainEditor, useOptionalBrainWorkspace } from '../brain/BrainWorkspaceContext'

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
  responseGenerationMaxTokensConcise: string
  responseGenerationMaxTokensStandard: string
  responseGenerationMaxTokensDeep: string
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
    responseGenerationMaxTokensConcise: '',
    responseGenerationMaxTokensStandard: '',
    responseGenerationMaxTokensDeep: '',
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

  const responseGenerationMaxTokensConciseCandidate = value.responseGenerationMaxTokensConcise
  if (
    typeof responseGenerationMaxTokensConciseCandidate === 'number' &&
    Number.isFinite(responseGenerationMaxTokensConciseCandidate)
  ) {
    next.responseGenerationMaxTokensConcise = String(responseGenerationMaxTokensConciseCandidate)
  } else if (
    typeof responseGenerationMaxTokensConciseCandidate === 'string' &&
    responseGenerationMaxTokensConciseCandidate.trim().length > 0
  ) {
    next.responseGenerationMaxTokensConcise = responseGenerationMaxTokensConciseCandidate.trim()
  }

  const responseGenerationMaxTokensStandardCandidate = value.responseGenerationMaxTokensStandard
  if (
    typeof responseGenerationMaxTokensStandardCandidate === 'number' &&
    Number.isFinite(responseGenerationMaxTokensStandardCandidate)
  ) {
    next.responseGenerationMaxTokensStandard = String(responseGenerationMaxTokensStandardCandidate)
  } else if (
    typeof responseGenerationMaxTokensStandardCandidate === 'string' &&
    responseGenerationMaxTokensStandardCandidate.trim().length > 0
  ) {
    next.responseGenerationMaxTokensStandard = responseGenerationMaxTokensStandardCandidate.trim()
  }

  const responseGenerationMaxTokensDeepCandidate = value.responseGenerationMaxTokensDeep
  if (
    typeof responseGenerationMaxTokensDeepCandidate === 'number' &&
    Number.isFinite(responseGenerationMaxTokensDeepCandidate)
  ) {
    next.responseGenerationMaxTokensDeep = String(responseGenerationMaxTokensDeepCandidate)
  } else if (
    typeof responseGenerationMaxTokensDeepCandidate === 'string' &&
    responseGenerationMaxTokensDeepCandidate.trim().length > 0
  ) {
    next.responseGenerationMaxTokensDeep = responseGenerationMaxTokensDeepCandidate.trim()
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
    left.responseGenerationMaxTokensConcise === right.responseGenerationMaxTokensConcise &&
    left.responseGenerationMaxTokensStandard === right.responseGenerationMaxTokensStandard &&
    left.responseGenerationMaxTokensDeep === right.responseGenerationMaxTokensDeep &&
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

function createPromptConfigPayload(formState: PromptFormState, preservedConfig: Record<string, unknown>, changedOnly = false) {
  const payload: Record<string, unknown> = { ...preservedConfig }
  for (const field of PROMPT_FIELDS) payload[field.key] = formState[field.key]
  for (const key of ['ragSimilarityThreshold', 'ragMaxDocumentsUsedForContext', 'ragMaxContextChars',
    'responseGenerationMaxTokensConcise', 'responseGenerationMaxTokensStandard', 'responseGenerationMaxTokensDeep',
    'smartSuggestionsEnabled']) delete payload[key]
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
  const responseGenerationMaxTokensConcise = parsePositiveInteger(formState.responseGenerationMaxTokensConcise)
  if (responseGenerationMaxTokensConcise !== null) {
    payload.responseGenerationMaxTokensConcise = responseGenerationMaxTokensConcise
  }
  const responseGenerationMaxTokensStandard = parsePositiveInteger(formState.responseGenerationMaxTokensStandard)
  if (responseGenerationMaxTokensStandard !== null) {
    payload.responseGenerationMaxTokensStandard = responseGenerationMaxTokensStandard
  }
  const responseGenerationMaxTokensDeep = parsePositiveInteger(formState.responseGenerationMaxTokensDeep)
  if (responseGenerationMaxTokensDeep !== null) {
    payload.responseGenerationMaxTokensDeep = responseGenerationMaxTokensDeep
  }
  if (formState.smartSuggestionsMode === 'enabled') {
    payload.smartSuggestionsEnabled = true
  } else if (formState.smartSuggestionsMode === 'disabled') {
    payload.smartSuggestionsEnabled = false
  }
  if (!changedOnly) return payload
  // Workspace edits leave untouched fields byte-for-byte intact, including absent overrides.
  const original = normalizePromptState(preservedConfig)
  const result = { ...preservedConfig }
  const ownedFields = [...PROMPT_FIELDS.map(field => [field.key, field.key] as const),
    ...(['ragSimilarityThreshold', 'ragMaxDocumentsUsedForContext', 'ragMaxContextChars',
      'responseGenerationMaxTokensConcise', 'responseGenerationMaxTokensStandard', 'responseGenerationMaxTokensDeep'] as const).map(key => [key, key] as const),
    ['smartSuggestionsMode', 'smartSuggestionsEnabled'] as const]
  for (const [formKey, configKey] of ownedFields) {
    if (formState[formKey] === original[formKey]) continue
    if (configKey in payload) result[configKey] = payload[configKey]
    else delete result[configKey]
  }
  return result
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

export function PromptsPage({ workspaceView }: { workspaceView?: 'instructions' | 'advanced' } = {}) {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const brainWorkspace = useOptionalBrainWorkspace()
  const queryClient = useQueryClient()
  const [formState, setFormState] = useState<PromptFormState>(defaultPromptState())
  const [preservedConfig, setPreservedConfig] = useState<Record<string, unknown>>({})
  const [behaviourPending, setBehaviourPending] = useState(false)
  const [behaviourReset, setBehaviourReset] = useState(0)
  const [revisionLabel, setRevisionLabel] = useState('')
  const [revisionSummary, setRevisionSummary] = useState('')
  const [previewQuery, setPreviewQuery] = useState('')
  const [hotApplyLabel, setHotApplyLabel] = useState('POC hot apply session')
  const [advancedView, setAdvancedView] = useState('settings')
  const [instructionKey, setInstructionKey] = useState('systemPrompt')
  const [saveConflict, setSaveConflict] = useState(false)
  const dirtyRef = useRef(false)
  const loadedConfig = useRef<Record<string, unknown>>({})

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const promptRevisionsQuery = useQuery({
    queryKey: ['deployment-prompt-revisions', selectedDeploymentId],
    queryFn: () => fetchDeploymentPromptRevisions(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && (!workspaceView || (workspaceView === 'advanced' && advancedView === 'revisions')),
  })

  const curatedModulesQuery = useQuery({
    queryKey: ['deployment-curated-modules'],
    queryFn: fetchDeploymentCuratedModules,
    enabled: !workspaceView || (workspaceView === 'advanced' && ['curated', 'settings'].includes(advancedView)),
  })

  const baselineQuery = useQuery({
    queryKey: ['deployment-prompt-baseline', selectedDeploymentId],
    queryFn: () => fetchDeploymentPromptBaseline(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && (!workspaceView || (workspaceView === 'advanced' && ['comparison', 'status'].includes(advancedView))),
  })

  const promptSessionQuery = useQuery({
    queryKey: ['deployment-poc-prompt-session', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocPromptSession(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && (!workspaceView || (workspaceView === 'advanced' && ['session', 'status'].includes(advancedView))),
  })

  useEffect(() => {
    setFormState(defaultPromptState())
    setPreservedConfig({})
    setBehaviourPending(false)
  }, [selectedDeploymentId])

  useEffect(() => {
    if (!draftQuery.data || (workspaceView && dirtyRef.current)) {
      return
    }
    setFormState(normalizePromptState(draftQuery.data.promptConfig))
    setPreservedConfig(promptConfigRecord(draftQuery.data.promptConfig))
    loadedConfig.current = promptConfigRecord(draftQuery.data.promptConfig)
  }, [draftQuery.data, workspaceView])

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
  const editorPromptConfig = useMemo(() => createPromptConfigPayload(formState, preservedConfig, Boolean(workspaceView)), [formState, preservedConfig, workspaceView])
  const draftDirty = useMemo(() => workspaceView
    ? promptConfigIdentity(editorPromptConfig) !== promptConfigIdentity(loadedConfig.current)
    : !statesEqual(formState, savedState) ||
    promptConfigIdentity(editorPromptConfig) !== promptConfigIdentity(createPromptConfigPayload(savedState, promptConfigRecord(draftQuery.data?.promptConfig))),
  [formState, savedState, editorPromptConfig, draftQuery.data?.promptConfig, workspaceView])
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
  const parsedResponseGenerationMaxTokensConcise = useMemo(
    () => parsePositiveInteger(formState.responseGenerationMaxTokensConcise),
    [formState.responseGenerationMaxTokensConcise],
  )
  const parsedResponseGenerationMaxTokensStandard = useMemo(
    () => parsePositiveInteger(formState.responseGenerationMaxTokensStandard),
    [formState.responseGenerationMaxTokensStandard],
  )
  const parsedResponseGenerationMaxTokensDeep = useMemo(
    () => parsePositiveInteger(formState.responseGenerationMaxTokensDeep),
    [formState.responseGenerationMaxTokensDeep],
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
  const responseGenerationMaxTokensConciseError =
    formState.responseGenerationMaxTokensConcise.trim().length > 0 &&
    parsedResponseGenerationMaxTokensConcise === null
      ? 'Enter a positive whole number.'
      : null
  const responseGenerationMaxTokensStandardError =
    formState.responseGenerationMaxTokensStandard.trim().length > 0 &&
    parsedResponseGenerationMaxTokensStandard === null
      ? 'Enter a positive whole number.'
      : null
  const responseGenerationMaxTokensDeepError =
    formState.responseGenerationMaxTokensDeep.trim().length > 0 && parsedResponseGenerationMaxTokensDeep === null
      ? 'Enter a positive whole number.'
      : null
  const promptConfigHasValidationErrors = Boolean(
    ragSimilarityThresholdError ||
      ragMaxDocumentsUsedForContextError ||
      ragMaxContextCharsError ||
      responseGenerationMaxTokensConciseError ||
      responseGenerationMaxTokensStandardError ||
      responseGenerationMaxTokensDeepError,
  )
  const editorState = useMemo(
    () => ({
      dirty: draftDirty || behaviourPending,
      label: 'Prompt editor',
      description: behaviourPending ? 'Behaviour choices are not yet applied to the editor.' : draftDirty
        ? `Prompt edits exist only in the current browser buffer until you save the ${deploymentTerm.lowercase} draft or use a session-scoped hot apply.`
        : `Prompt editor matches the saved ${deploymentTerm.lowercase} draft.`,
    }),
    [draftDirty, behaviourPending],
  )
  useDeploymentWorkspaceEditorState(selectedDeploymentId && !workspaceView ? editorState : null)
  dirtyRef.current = draftDirty || behaviourPending
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
  const currentResponseGenerationMaxTokensConcise = formState.responseGenerationMaxTokensConcise.trim() || '—'
  const savedResponseGenerationMaxTokensConcise = savedState.responseGenerationMaxTokensConcise.trim() || '—'
  const publishedResponseGenerationMaxTokensConcise =
    baselineState.responseGenerationMaxTokensConcise.trim() || '—'
  const currentResponseGenerationMaxTokensStandard = formState.responseGenerationMaxTokensStandard.trim() || '—'
  const savedResponseGenerationMaxTokensStandard = savedState.responseGenerationMaxTokensStandard.trim() || '—'
  const publishedResponseGenerationMaxTokensStandard =
    baselineState.responseGenerationMaxTokensStandard.trim() || '—'
  const currentResponseGenerationMaxTokensDeep = formState.responseGenerationMaxTokensDeep.trim() || '—'
  const savedResponseGenerationMaxTokensDeep = savedState.responseGenerationMaxTokensDeep.trim() || '—'
  const publishedResponseGenerationMaxTokensDeep = baselineState.responseGenerationMaxTokensDeep.trim() || '—'
  const currentSmartSuggestionsMode = readAiBehaviour(editorPromptConfig) ? 'Profile controlled' : formatSmartSuggestionsMode(formState.smartSuggestionsMode)
  const savedSmartSuggestionsMode = readAiBehaviour(draftQuery.data?.promptConfig) ? 'Profile controlled' : formatSmartSuggestionsMode(savedState.smartSuggestionsMode)
  const publishedSmartSuggestionsMode = readAiBehaviour(baselineQuery.data?.promptConfig) ? 'Profile controlled' : formatSmartSuggestionsMode(baselineState.smartSuggestionsMode)
  const runtimeUnavailable = !workspace?.deployment.runtimeBaseUrl
  const canEdit = workspace?.access.canEdit ?? false
  const canOperate = workspace?.access.canOperate ?? false
  const promptSession = promptSessionQuery.data
  const savedCompositionQuery = useQuery({
    queryKey: ['deployment-ai-behaviour-effective', selectedDeploymentId, draftQuery.data?.promptConfig],
    queryFn: () => composeDeploymentAiBehaviour(selectedDeploymentId, { promptConfig: promptConfigRecord(draftQuery.data?.promptConfig) }),
    enabled: Boolean(selectedDeploymentId && draftQuery.data && readAiBehaviour(draftQuery.data.promptConfig)) &&
      (!workspaceView || (workspaceView === 'advanced' && advancedView === 'compiled')),
  })
  const releasePreviewState = readAiBehaviour(draftQuery.data?.promptConfig)
    ? normalizePromptState(savedCompositionQuery.data?.effectivePromptConfig) : savedState

  async function compileEditorPreview() {
    const composed = await composeDeploymentAiBehaviour(selectedDeploymentId, { promptConfig: editorPromptConfig })
    return effectiveAiBehaviourPreview(composed.effectivePromptConfig)
  }

  const baselinePreviewMutation = useMutation({
    mutationFn: () =>
      queryDeploymentPocChat(selectedDeploymentId, {
        query: previewQuery.trim(),
      }),
  })

  const overlayPreviewMutation = useMutation({
    mutationFn: async () =>
      queryDeploymentPocChat(selectedDeploymentId, {
        query: previewQuery.trim(),
        promptPreview: await compileEditorPreview(),
      }),
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!draftQuery.data) {
        throw new Error('No active draft available.')
      }
      if (workspaceView) {
        if (!canEdit) throw new Error('Response changes require editor permission.')
        const latest = await fetchDeploymentDraft(selectedDeploymentId)
        if (latest.id !== draftQuery.data.id || promptConfigIdentity(latest.promptConfig) !== promptConfigIdentity(loadedConfig.current)) {
          setSaveConflict(true)
          throw new Error('The saved response settings changed while you were editing. Reload the saved draft before saving again.')
        }
      }
      const composed = await composeDeploymentAiBehaviour(selectedDeploymentId, { promptConfig: editorPromptConfig })
      return updateDeploymentDraft(draftQuery.data.id, { promptConfig: composed.promptConfig })
    },
    onSuccess: async (draft) => {
      if (workspaceView) {
        dirtyRef.current = false
        loadedConfig.current = promptConfigRecord(draft.promptConfig)
        setFormState(normalizePromptState(draft.promptConfig))
        setPreservedConfig(promptConfigRecord(draft.promptConfig))
        queryClient.setQueryData(['deployment-draft', selectedDeploymentId], draft)
      }
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
      dirtyRef.current = false
      loadedConfig.current = promptConfigRecord(draft.promptConfig)
      setFormState(normalizePromptState(draft.promptConfig))
      setPreservedConfig(promptConfigRecord(draft.promptConfig))
      setBehaviourReset(previous => previous + 1)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const activatePromptSessionMutation = useMutation({
    mutationFn: async () =>
      updateDeploymentPocPromptSession(selectedDeploymentId, {
        sessionLabel: hotApplyLabel.trim() || 'POC hot apply session',
        promptPreview: await compileEditorPreview(),
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

  const workspaceBusy = saveMutation.isPending || applyCuratedModuleMutation.isPending || restoreRevisionMutation.isPending ||
    createRevisionMutation.isPending || activatePromptSessionMutation.isPending || clearPromptSessionMutation.isPending ||
    baselinePreviewMutation.isPending || overlayPreviewMutation.isPending
  function discardWorkspaceEdits() {
    dirtyRef.current = false
    loadedConfig.current = promptConfigRecord(draftQuery.data?.promptConfig)
    setFormState(savedState); setPreservedConfig(loadedConfig.current)
    setBehaviourPending(false); setBehaviourReset(previous => previous + 1); setSaveConflict(false); saveMutation.reset()
  }
  useBrainEditor(workspaceView ? {
    dirty: draftDirty || behaviourPending, busy: workspaceBusy,
    canSave: canEdit && Boolean(draftQuery.data) && draftDirty && !behaviourPending && !promptConfigHasValidationErrors && !saveConflict,
    save: canEdit ? () => saveMutation.mutateAsync().catch(() => undefined) : undefined,
    discard: canEdit ? discardWorkspaceEdits : undefined,
    message: !canEdit ? 'Read-only access. Response changes require editor permission.'
      : promptConfigHasValidationErrors ? 'Correct the highlighted fields before saving.'
        : draftDirty ? 'Unsaved changes' : saveMutation.isSuccess || applyCuratedModuleMutation.isSuccess || restoreRevisionMutation.isSuccess
          ? 'Draft saved; live configuration unchanged' : 'Editing shared draft',
    error: saveMutation.isError ? saveMutation.error.message : null,
  } : null)
  const showAdvanced = (section: string) => !workspaceView || (workspaceView === 'advanced' && advancedView === section)
  const profileMetadata = readAiBehaviour(editorPromptConfig)
  const selectedPrompt = PROMPT_FIELDS.find(field => field.key === instructionKey)
  const selectedProfile = profileMetadata?.[instructionKey as 'tone' | 'answerBreadth' | 'answerLength' | 'nextSteps']

  return (
    <ConfigurationSurface><Stack spacing={3} sx={workspaceView ? {
      '& .MuiCard-root': { border: 0, borderRadius: 0, bgcolor: 'transparent', boxShadow: 'none' },
      '& .MuiCardContent-root': { px: 0 },
      '& .MuiAccordion-root': { border: 0, borderRadius: '0 !important', bgcolor: 'transparent' },
      '& .MuiAccordionDetails-root': { px: 0 },
      '& .MuiAccordionSummary-root': { display: 'none' },
    } : undefined}>
      {draftQuery.isError && <Alert severity="error">Could not load the prompt draft. Reload before editing.</Alert>}
      {workspaceView && draftQuery.isPending && <Typography role="status">Loading response settings...</Typography>}
      {workspaceView && !canEdit && <Alert severity="info">Read-only access. Response changes require editor permission.</Alert>}
      {workspaceView && saveConflict && <Alert severity="warning" action={<Button onClick={async () => {
        const result = await draftQuery.refetch()
        if (!result.data || result.isError) return
        dirtyRef.current = false; loadedConfig.current = promptConfigRecord(result.data.promptConfig)
        setFormState(normalizePromptState(result.data.promptConfig)); setPreservedConfig(loadedConfig.current)
        setSaveConflict(false); saveMutation.reset()
      }}>Reload saved draft</Button>}>Your edits are retained. Reloading replaces them with the latest saved settings.</Alert>}
      {workspaceView === 'advanced' && <TextField select size="small" label="Advanced view" value={advancedView}
        SelectProps={{ SelectDisplayProps: { 'aria-label': 'Advanced view', 'aria-labelledby': undefined } }}
        onChange={event => setAdvancedView(event.target.value)} sx={{ maxWidth: 420 }}>
        <MenuItem value="settings">Retrieval and generation settings</MenuItem>
        <MenuItem value="curated">Curated baseline / reset</MenuItem>
        <MenuItem value="comparison">Published baseline comparison</MenuItem>
        <MenuItem value="compiled">Compiled release prompts</MenuItem>
        <MenuItem value="session">Temporary test-session override</MenuItem>
        <MenuItem value="preview">Explicit prompt preview</MenuItem>
        <MenuItem value="revisions">Prompt snapshots</MenuItem>
        <MenuItem value="status">Draft and test-session status</MenuItem>
      </TextField>}
      {workspaceView === 'advanced' && ['session', 'preview', 'status'].includes(advancedView) && <Alert severity="info"
        action={brainWorkspace ? <Button component={RouterLink} to={brainWorkspace.href('try')}>Try it</Button> : undefined}>
        These retained POC tools use the deployment test runtime, not the target selected in this workspace.
        Only the seven managed prompt strings can be overlaid. Connections, actions, model settings and runtime controls are not simulated.
        Explicit tests can incur provider usage; normal actions and confirmations remain in force.
      </Alert>}
      {workspaceView === 'advanced' && ['session', 'status'].includes(advancedView) && promptSessionQuery.isError &&
        <Alert severity="error">{promptSessionQuery.error.message}</Alert>}
      {workspaceView === 'advanced' && advancedView === 'revisions' && promptRevisionsQuery.isError &&
        <Alert severity="error">{promptRevisionsQuery.error.message}</Alert>}
      {workspaceView === 'advanced' && ['comparison', 'status'].includes(advancedView) && baselineQuery.isError &&
        <Alert severity="error">{baselineQuery.error.message}</Alert>}
      {workspaceView === 'instructions' && <Stack spacing={2}>
        <TextField select size="small" label="Instruction set" value={instructionKey}
          SelectProps={{ SelectDisplayProps: { 'aria-label': 'Instruction set', 'aria-labelledby': undefined } }}
          onChange={event => setInstructionKey(event.target.value)} sx={{ maxWidth: 460 }}>
          {PROMPT_FIELDS.map(field => <MenuItem key={field.key} value={field.key}>{field.label}</MenuItem>)}
          {(['tone', 'answerBreadth', 'answerLength', 'nextSteps'] as const).filter(key => profileMetadata?.[key]).map(key =>
            <MenuItem key={key} value={key}>Profile: {({ tone: 'Tone', answerBreadth: 'General advice', answerLength: 'Answer length', nextSteps: 'Helpful next steps' })[key]}</MenuItem>)}
        </TextField>
        {selectedPrompt ? <TextField label={selectedPrompt.label} helperText={selectedPrompt.description} multiline minRows={10} maxRows={18}
          fullWidth value={formState[selectedPrompt.key]} disabled={!canEdit || !draftQuery.data || workspaceBusy}
          onChange={event => { saveMutation.reset(); setFormState(previous => ({ ...previous, [selectedPrompt.key]: event.target.value })) }} />
          : selectedProfile ? <TextField label="Profile instructions" helperText={`${selectedProfile.presetId} / revision ${selectedProfile.revision}. Edited instructions are identified as Custom by the server.`}
            multiline minRows={8} maxRows={18} fullWidth value={selectedProfile.instructions} disabled={!canEdit || workspaceBusy}
            onChange={event => { saveMutation.reset(); setPreservedConfig(previous => ({ ...previous,
              aiBehaviour: { ...promptConfigRecord(previous.aiBehaviour), [instructionKey]: { ...selectedProfile, instructions: event.target.value } },
            })) }} /> : null}
      </Stack>}
      {!workspaceView && <AiBehaviourEditor key={`${selectedDeploymentId}-${behaviourReset}`} deploymentId={selectedDeploymentId}
        promptConfig={editorPromptConfig} canEdit={canEdit && Boolean(draftQuery.data) && !saveMutation.isPending}
        onPendingChange={setBehaviourPending} onApply={config => {
          setPreservedConfig(config)
          setFormState(normalizePromptState(config))
        }} />}

      {!workspaceView && <Box component="section" aria-label="Prompt draft controls" sx={{ borderTop: 1, borderBottom: 1, borderColor: 'divider', py: 2 }}>
        <Stack direction={{ xs: 'column', lg: 'row' }} gap={2} alignItems={{ lg: 'center' }} justifyContent="space-between">
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle2">{draftDirty ? 'Unsaved changes' : 'Saved draft'}</Typography>
            <Typography variant="body2" color={draftDirty || behaviourPending ? 'warning.main' : 'text.secondary'} sx={{ mt: 0.5 }}>
              {behaviourPending ? 'Behaviour choices have not been applied to the editor. Review and apply them, or discard them before saving or testing.'
                : draftDirty ? 'Unsaved editor changes. Saving updates the draft only; the live experience changes after publish and apply.'
                  : 'Editor matches the saved draft. The live experience uses the applied release.'}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1.5} useFlexGap flexWrap="wrap" sx={{ flexShrink: 0 }}>
            <Button variant="outlined" startIcon={<RestoreRoundedIcon />} onClick={() => {
              setFormState(savedState)
              setPreservedConfig(promptConfigRecord(draftQuery.data?.promptConfig))
              setBehaviourReset(previous => previous + 1)
            }}>Reset to saved</Button>
            <Button variant="contained" startIcon={<SaveRoundedIcon />}
              disabled={!canEdit || !draftQuery.data || saveMutation.isPending || !draftDirty || behaviourPending || promptConfigHasValidationErrors}
              onClick={() => saveMutation.mutate()}>{saveMutation.isPending ? 'Saving...' : 'Save prompt draft'}</Button>
          </Stack>
        </Stack>
        {saveMutation.isError && <Alert severity="error" sx={{ mt: 2 }}>{saveMutation.error instanceof Error ? saveMutation.error.message : 'Failed to save prompt draft'}</Alert>}
        {saveMutation.isSuccess && <Alert severity="success" sx={{ mt: 2 }}>Prompt draft saved.</Alert>}
      </Box>}

      {showAdvanced('status') && <Accordion expanded={workspaceView ? true : undefined} disableGutters sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle1">Advanced draft and live status</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt posture</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                Keep the browser editor buffer, saved {deploymentTerm.lowercase} draft, published prompt baseline, and any active POC
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
                <Chip label={`${deploymentTerm.singular} active version: ${workspace.deployment.activeVersion}`} variant="outlined" />
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
                        Local browser state only. Nothing changes in the {deploymentTerm.lowercase} until you save or activate a
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
                        label={`Gen tokens C/S/D: ${currentResponseGenerationMaxTokensConcise}/${currentResponseGenerationMaxTokensStandard}/${currentResponseGenerationMaxTokensDeep}`}
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
                      <Chip
                        label={`Saved gen tokens C/S/D: ${savedResponseGenerationMaxTokensConcise}/${savedResponseGenerationMaxTokensStandard}/${savedResponseGenerationMaxTokensDeep}`}
                        size="small"
                        variant="outlined"
                      />
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
                      <Chip
                        label={`Published gen tokens C/S/D: ${publishedResponseGenerationMaxTokensConcise}/${publishedResponseGenerationMaxTokensStandard}/${publishedResponseGenerationMaxTokensDeep}`}
                        size="small"
                        variant="outlined"
                      />
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
                Editor changes exist only in this browser session right now. Save them to update the {deploymentTerm.lowercase} draft,
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
        </AccordionDetails>
      </Accordion>}

      {showAdvanced('settings') || showAdvanced('curated') ? <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">{workspaceView ? advancedView === 'settings' ? 'Retrieval and generation settings' : 'Curated baseline' : 'Prompt bundle'}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Prompt changes are versioned {deploymentTerm.lowercase} config. Save the draft here, then publish and apply when you
                want the revised prompt bundle to become part of a release.
              </Typography>
            </Box>

            <Accordion expanded={workspaceView ? true : undefined} disableGutters sx={{ boxShadow: 'none', border: '1px solid', borderColor: 'divider', '&:before': { display: 'none' } }}>
              <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle1">Advanced prompts and settings</Typography></AccordionSummary>
              <AccordionDetails>
                <Stack spacing={2.5}>
            {!workspaceView && <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Change type: Versioned config" color="primary" variant="outlined" />
              <Chip label="Action path: Save Draft -> Publish -> Apply" color="warning" />
              <Chip label={`Filled prompts: ${populatedPromptCount}/${PROMPT_FIELDS.length}`} variant="outlined" />
              <Chip label={`RAG threshold: ${formState.ragSimilarityThreshold.trim() || '—'}`} variant="outlined" />
              <Chip label={`Context docs: ${currentRagMaxDocumentsUsedForContext}`} variant="outlined" />
              <Chip label={`Context chars: ${currentRagMaxContextChars}`} variant="outlined" />
              <Chip
                label={`Gen tokens C/S/D: ${currentResponseGenerationMaxTokensConcise}/${currentResponseGenerationMaxTokensStandard}/${currentResponseGenerationMaxTokensDeep}`}
                variant="outlined"
              />
              <Chip label={`Smart suggestions: ${currentSmartSuggestionsMode}`} variant="outlined" />
              {workspace?.draft ? <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" /> : null}
              {currentCuratedModule ? (
                <Chip label={`Curated module: ${currentCuratedModule.name}`} color="secondary" variant="outlined" />
              ) : null}
            </Stack>}

            <Alert severity={draftDirty ? 'warning' : 'info'}>
              {draftDirty
                ? 'Prompt edits are pending in the editor. Save the draft before publishing or creating a prompt revision snapshot.'
                : 'Prompt bundle matches the saved draft. Revisions can be snapshotted from this saved state.'}
            </Alert>
            {!canEdit ? (
              <Alert severity="info">
                Saving prompt config and managing prompt revisions requires {deploymentTerm.lowercase} editor access or higher.
              </Alert>
            ) : null}

            {showAdvanced('curated') && <Card variant="outlined">
              <CardContent>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Curated module baseline
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Curated modules seed the {deploymentTerm.lowercase}-owned prompt bundle. Reapplying a module replaces the saved
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
                                disabled={!canEdit || applyCuratedModuleMutation.isPending || module.id === currentCuratedModuleId || Boolean(workspaceView && (draftDirty || workspaceBusy))}
                                onClick={() => {
                                  if (!workspaceView || window.confirm('Replace the saved prompt bundle with this curated baseline? This saves the shared draft, not a live release.')) applyCuratedModuleMutation.mutate(module.id)
                                }}
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
            </Card>}

            <Grid container spacing={2}>
              {showAdvanced('settings') && <Grid item xs={12}>
                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                          Retrieval tuning
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          Minimum similarity score required for {deploymentTerm.lowercase} RAG retrieval. Lower values widen recall and
                          can help broad catalog summaries; higher values keep grounding stricter.
                        </Typography>
                      </Box>
                      <TextField
                        label="RAG similarity threshold"
                        disabled={Boolean(workspaceView) && (!canEdit || !draftQuery.data || workspaceBusy)}
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
                          `${deploymentTerm.singular}-scoped retrieval threshold. Demo and canonical commerce rollouts default to 0.1.`
                        }
                      />
                      <TextField
                        label="Max context documents"
                        disabled={Boolean(workspaceView) && (!canEdit || !draftQuery.data || workspaceBusy)}
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
                          `Optional ${deploymentTerm.lowercase} override for how many retrieved documents are used to ground final answer generation.`
                        }
                      />
                      <TextField
                        label="Max context characters"
                        disabled={Boolean(workspaceView) && (!canEdit || !draftQuery.data || workspaceBusy)}
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
                          `Optional ${deploymentTerm.lowercase} override for the total retrieved context characters passed into final answer generation.`
                        }
                      />
                      <TextField
                        label="Concise generation max tokens"
                        disabled={Boolean(workspaceView) && (!canEdit || !draftQuery.data || workspaceBusy)}
                        type="number"
                        fullWidth
                        value={formState.responseGenerationMaxTokensConcise}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            responseGenerationMaxTokensConcise: event.target.value,
                          }))
                        }
                        inputProps={{ min: 1, step: 50 }}
                        error={Boolean(responseGenerationMaxTokensConciseError)}
                        helperText={
                          responseGenerationMaxTokensConciseError ??
                          `Optional ${deploymentTerm.lowercase} override for extractor-selected CONCISE answers. Framework default is 400.`
                        }
                      />
                      <TextField
                        label="Standard generation max tokens"
                        disabled={Boolean(workspaceView) && (!canEdit || !draftQuery.data || workspaceBusy)}
                        type="number"
                        fullWidth
                        value={formState.responseGenerationMaxTokensStandard}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            responseGenerationMaxTokensStandard: event.target.value,
                          }))
                        }
                        inputProps={{ min: 1, step: 50 }}
                        error={Boolean(responseGenerationMaxTokensStandardError)}
                        helperText={
                          responseGenerationMaxTokensStandardError ??
                          `Optional ${deploymentTerm.lowercase} override for extractor-selected STANDARD answers. Leave blank to keep provider defaults.`
                        }
                      />
                      <TextField
                        label="Deep generation max tokens"
                        disabled={Boolean(workspaceView) && (!canEdit || !draftQuery.data || workspaceBusy)}
                        type="number"
                        fullWidth
                        value={formState.responseGenerationMaxTokensDeep}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            responseGenerationMaxTokensDeep: event.target.value,
                          }))
                        }
                        inputProps={{ min: 1, step: 50 }}
                        error={Boolean(responseGenerationMaxTokensDeepError)}
                        helperText={
                          responseGenerationMaxTokensDeepError ??
                          `Optional ${deploymentTerm.lowercase} override for extractor-selected DEEP answers. Framework default is 1200.`
                        }
                      />
                      <TextField
                        select
                        label="Smart suggestions"
                        fullWidth
                        disabled={!canEdit || Boolean(readAiBehaviour(editorPromptConfig)) || Boolean(workspaceView && workspaceBusy)}
                        value={formState.smartSuggestionsMode}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            smartSuggestionsMode: event.target.value as SmartSuggestionsMode,
                          }))
                        }
                        helperText={readAiBehaviour(editorPromptConfig)
                          ? 'Next steps are controlled by the behaviour profile. This legacy enrichment setting is retained unchanged.'
                          : 'Controls the extra smart-suggestion enrichment step. Use framework default to inherit the mode-level baseline.'}
                      >
                        <MenuItem value="inherit">Use framework default</MenuItem>
                        <MenuItem value="enabled">Enabled</MenuItem>
                        <MenuItem value="disabled">Disabled</MenuItem>
                      </TextField>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>}
              {!workspaceView && PROMPT_FIELDS.map((field) => (
                <Grid item xs={12} md={6} key={field.key}>
                  <TextField
                    label={field.label}
                    helperText={field.description}
                    multiline
                    minRows={7}
                    maxRows={18}
                    fullWidth
                    value={formState[field.key]}
                    disabled={!canEdit}
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
                </Stack>
              </AccordionDetails>
            </Accordion>

          </Stack>
        </CardContent>
      </Card> : null}

      {showAdvanced('comparison') && <Accordion expanded={workspaceView ? true : undefined} disableGutters sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle1">Advanced baseline comparison</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Base prompt baseline comparison</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Compare the seven base prompts with the published version. Behaviour profile instructions are shown
                separately in Advanced compiled release prompts.
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
        </AccordionDetails>
      </Accordion>}

      {showAdvanced('compiled') && <Accordion expanded={workspaceView ? true : undefined} disableGutters sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle1">Advanced compiled release prompts</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Release prompt preview</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Compiled prompts for the saved draft, including applied behaviour profiles. Unsaved editor changes are not included.
              </Typography>
            </Box>

            {draftDirty ? (
              <Alert severity="warning">
                The preview reflects the saved draft only. Save the editor changes above to update this preview.
              </Alert>
            ) : null}

            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label={`Preview prompts: ${countPopulatedPrompts(releasePreviewState)}/${PROMPT_FIELDS.length}`} variant="outlined" />
              <Chip label={`Saved RAG threshold: ${savedRagSimilarityThreshold}`} variant="outlined" />
              {workspace?.draft ? <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" /> : null}
            </Stack>

            {savedCompositionQuery.isError && <Alert severity="error">Could not compile the saved behaviour profiles. Retry after checking the configuration.</Alert>}
            {savedCompositionQuery.isFetching && <Typography variant="body2">Compiling saved behaviour...</Typography>}
            <Grid container spacing={2}>
              {PROMPT_FIELDS.map((field) => {
                const value = releasePreviewState[field.key]
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
                            sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}
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
        </AccordionDetails>
      </Accordion>}

      {showAdvanced('session') && <Accordion expanded={workspaceView ? true : undefined} disableGutters sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle1">Advanced POC session hot apply</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">POC session hot apply</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Persist the current editor overlay for your {deploymentTerm.lowercase} POC session so the embedded chatbot uses these
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
                Apply the {deploymentTerm.lowercase} before activating session hot apply. The POC console must have a live runtime URL.
              </Alert>
            ) : !canOperate ? (
              <Alert severity="info">
                POC hot apply is available to {deploymentTerm.lowercase} operators and above.
              </Alert>
            ) : workspaceView ? <Alert severity="info">
              This is a temporary, session-scoped seven-string prompt overlay. It does not save the draft or update a release.
            </Alert> : (
              <Alert severity="info">
                Session hot apply is operator-scoped and secured with the runtime admin key. It only affects the
                platform POC console for this {deploymentTerm.lowercase} until you clear it. Only prompts are overridden;
                runtime recommendation controls remain those of the applied release.
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
                Prompt hot apply is active for the {deploymentTerm.lowercase} POC console. Open the POC page to test multi-turn
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
                      when you want the POC console back on the saved {deploymentTerm.lowercase} behavior.
                    </Typography>
                    <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
                      <Button
                        variant="contained"
                        color="secondary"
                        startIcon={<AutoFixHighRoundedIcon />}
                        disabled={
                          !canOperate ||
                          runtimeUnavailable ||
                          !draftQuery.data ||
                          behaviourPending ||
                          promptConfigHasValidationErrors ||
                          (populatedPromptCount === 0 && !readAiBehaviour(editorPromptConfig)) ||
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
        </AccordionDetails>
      </Accordion>}

      {showAdvanced('preview') && <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Try a conversation</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Compare the current live conversation with an editor-only prompt preview. Neither run saves or
                publishes configuration.
              </Typography>
            </Box>

            <Alert severity="info">
              Only the seven managed prompt strings are previewed. Runtime recommendation controls, provider settings
              and deployed state require publish/apply; a live Off setting can still suppress Contextual suggestions.
            </Alert>

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
                Apply the {deploymentTerm.lowercase} before using prompt preview. This feature runs against the selected {deploymentTerm.lowercase}
                runtime and requires a live runtime URL.
              </Alert>
            ) : !canOperate ? (
              <Alert severity="info">
                Prompt preview and live POC evaluation require {deploymentTerm.lowercase} operator access or higher.
              </Alert>
            ) : workspaceView ? <Alert severity="info">
              Prompt preview overlays seven compiled prompt strings for this explicit request only. It is not a full-draft simulation or a sandbox.
            </Alert> : (
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
                {baselinePreviewMutation.isPending ? 'Running baseline...' : workspaceView ? 'Run applied runtime' : 'Run saved prompt bundle'}
              </Button>
              <Button
                variant="contained"
                color="secondary"
                startIcon={<AutoFixHighRoundedIcon />}
                disabled={!canOperate || runtimeUnavailable || !draftQuery.data || behaviourPending || promptConfigHasValidationErrors || previewQuery.trim().length === 0 || overlayPreviewMutation.isPending}
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
                        Currently applied {deploymentTerm.lowercase}
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
      </Card>}

      {showAdvanced('revisions') && <Accordion expanded={workspaceView ? true : undefined} disableGutters sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle1">Advanced prompt revisions</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Prompt revisions</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 960 }}>
                Prompt revisions are lightweight prompt-only snapshots inside the {deploymentTerm.lowercase} workspace. They are useful
                for restoring prompt behavior quickly without rolling back the rest of the {deploymentTerm.lowercase} draft.
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

            <Box sx={{ overflowX: 'auto', maxWidth: '100%' }}>
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
                        disabled={!canEdit || restoreRevisionMutation.isPending || Boolean(workspaceView && (draftDirty || workspaceBusy))}
                        onClick={() => {
                          if (!workspaceView || window.confirm('Restore this prompt snapshot to the shared draft? This is not deployment rollback and does not update a live release.')) restoreRevisionMutation.mutate(revision.id)
                        }}
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
            </Box>
          </Stack>
        </AccordionDetails>
      </Accordion>}
    </Stack></ConfigurationSurface>
  )
}
