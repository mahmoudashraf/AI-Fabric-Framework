import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import BookmarkAddRoundedIcon from '@mui/icons-material/BookmarkAddRounded'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import SendRoundedIcon from '@mui/icons-material/SendRounded'
import StorageRoundedIcon from '@mui/icons-material/StorageRounded'
import UploadFileRoundedIcon from '@mui/icons-material/UploadFileRounded'
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
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, useEffect, useMemo, useRef, useState } from 'react'
import {
  clearDeploymentPocRuntimeVectors,
  createDeploymentPocScenario,
  deleteDeploymentPocConversation,
  deleteDeploymentPocScenario,
  fetchDeploymentIntegrationSummary,
  fetchDeploymentPocChatSuggestions,
  fetchDeploymentPocConversation,
  fetchDeploymentPocPromptSession,
  fetchDeploymentPocRuntimeAuthContext,
  fetchDeploymentPocScenarios,
  fetchDeploymentPocWorkspace,
  getPlatformApiBaseUrl,
  getStoredPlatformApiKey,
  PlatformApiError,
  queryDeploymentPocChat,
  runDeploymentPocImport,
  type DeploymentIntegrationSummary,
  type DeploymentPocAuthPath,
  type DeploymentPocImportRecordRequest,
  type DeploymentPocImportRunSummary,
  type DeploymentPocTraceSummary,
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

function readString(value: unknown) {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null
}

function extractLatestAssistantMessage(result: unknown, traceSummary: DeploymentPocTraceSummary | null) {
  if (traceSummary?.answer) {
    return traceSummary.answer
  }
  if (traceSummary?.message) {
    return traceSummary.message
  }
  if (!isRecord(result)) {
    return 'The runtime returned a result, but no transcript turn was persisted.'
  }

  const sanitizedPayload = isRecord(result.sanitizedPayload) ? result.sanitizedPayload : null
  const safeSummary = sanitizedPayload ? readString(sanitizedPayload.safeSummary) : null
  const sanitizedMessage = sanitizedPayload ? readString(sanitizedPayload.message) : null
  const message = readString(result.message)

  return safeSummary ?? sanitizedMessage ?? message ?? 'The runtime returned a result, but no transcript turn was persisted.'
}

function shouldHydrateConversationTranscript(result: unknown, traceSummary: DeploymentPocTraceSummary | null) {
  const resultType =
    traceSummary?.resultType ??
    (isRecord(result) && typeof result.type === 'string' ? result.type.trim() : null)

  return resultType !== 'ERROR'
}

function readStringList(value: unknown) {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    .map((item) => item.trim())
}

function summarizeActionValidation(value: unknown) {
  if (!isRecord(value)) {
    return {
      missing: [] as string[],
      provenanceMissing: [] as string[],
      sourcesUsed: [] as string[],
    }
  }

  const sourcesUsed = isRecord(value.sourcesUsed)
    ? Object.entries(value.sourcesUsed)
        .filter(([, enabled]) => enabled === true)
        .map(([source]) => source)
    : []

  return {
    missing: readStringList(value.missing),
    provenanceMissing: readStringList(value.provenanceMissing),
    sourcesUsed,
  }
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : '—'
}

function formatDuration(durationMs: number | null | undefined) {
  if (typeof durationMs !== 'number' || Number.isNaN(durationMs)) {
    return null
  }
  return `${Math.max(0, Math.round(durationMs))} ms`
}

function parseImportPayload(payloadText: string): DeploymentPocImportRecordRequest[] {
  const parsed = JSON.parse(payloadText) as unknown
  if (!Array.isArray(parsed)) {
    throw new Error('Import payload must be a JSON array of records.')
  }

  return parsed.map((item, index) => {
    if (!isRecord(item)) {
      throw new Error(`Import record ${index + 1} must be an object.`)
    }

    const id = typeof item.id === 'string' ? item.id.trim() : ''
    if (!id) {
      throw new Error(`Import record ${index + 1} requires a non-empty id.`)
    }

    const content = typeof item.content === 'string' && item.content.trim().length > 0 ? item.content.trim() : undefined
    const entity = isRecord(item.entity) ? item.entity : undefined
    const metadata = isRecord(item.metadata) ? item.metadata : undefined
    if (!content && !entity) {
      throw new Error(`Import record ${index + 1} requires either content or entity.`)
    }

    return { id, content, entity, metadata }
  })
}

function importStatusColor(status: string): 'success' | 'warning' | 'default' {
  if (status === 'SUCCEEDED') {
    return 'success'
  }
  if (status === 'PARTIAL' || status === 'FAILED') {
    return 'warning'
  }
  return 'default'
}

const POC_MIGRATION_STEPS = ['Source', 'Scope', 'Readiness', 'Import'] as const

type MigrationSourceKey = 'TEMPLATE_SAMPLE' | 'JSON_FILE' | 'JSON_PASTE'

const POC_AUTH_PATH_OPTIONS: Array<{
  value: DeploymentPocAuthPath
  label: string
  description: string
}> = [
  {
    value: 'PLATFORM_PRIVATE',
    label: 'Platform private',
    description: 'Platform-backed private assertion on the verified /api/chat/me/* surface.',
  },
  {
    value: 'PUBLIC_AUTHENTICATED',
    label: 'Public authenticated',
    description: 'Signed public browser bearer token with authenticated end-user identity.',
  },
  {
    value: 'PUBLIC_ANONYMOUS',
    label: 'Public anonymous',
    description: 'Signed public browser bearer token with anonymous session identity.',
  },
]

function authPathLabel(authPath: DeploymentPocAuthPath) {
  return POC_AUTH_PATH_OPTIONS.find((option) => option.value === authPath)?.label ?? authPath
}

function authPathDescription(authPath: DeploymentPocAuthPath) {
  return POC_AUTH_PATH_OPTIONS.find((option) => option.value === authPath)?.description ?? authPath
}

function availablePocAuthPaths(integration: DeploymentIntegrationSummary | null | undefined): DeploymentPocAuthPath[] {
  if (!integration) {
    return ['PLATFORM_PRIVATE']
  }

  const options: DeploymentPocAuthPath[] = []
  if (integration.trustedBackendCallerAuthConfigured && integration.privateRuntimeAssertionValidationConfigured) {
    options.push('PLATFORM_PRIVATE')
  }
  if (integration.publicRuntimeTokenValidationConfigured) {
    options.push('PUBLIC_AUTHENTICATED')
  }
  if (integration.anonymousBootstrapSupported) {
    options.push('PUBLIC_ANONYMOUS')
  }
  return options.length > 0 ? options : ['PLATFORM_PRIVATE']
}

const MAX_MODE_WIDGET_SCRIPT_ID = 'platform-poc-max-mode-widget-script'
const MAX_MODE_WIDGET_SCRIPT_VERSION = '2026-04-14-opaque-shell-v1'
const MAX_MODE_WIDGET_SCRIPT_SRC = `/max-mode-widget.iife.js?v=${MAX_MODE_WIDGET_SCRIPT_VERSION}`
const MAX_MODE_WIDGET_STATE_KEY = 'maxmode_widget_state'
const MAX_MODE_WIDGET_PENDING_ATTACHMENTS_KEY = 'maxmode_widget_pending_attachments'

let maxModeWidgetScriptPromise: Promise<void> | null = null

function loadMaxModeWidgetScript() {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('Max Mode widget can only load in a browser context.'))
  }
  if (window.MaxMode) {
    return Promise.resolve()
  }
  if (maxModeWidgetScriptPromise) {
    return maxModeWidgetScriptPromise
  }

  maxModeWidgetScriptPromise = new Promise<void>((resolve, reject) => {
    const existingScript = document.getElementById(MAX_MODE_WIDGET_SCRIPT_ID) as HTMLScriptElement | null
    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(), { once: true })
      existingScript.addEventListener('error', () => reject(new Error('Failed to load Max Mode widget bundle.')), {
        once: true,
      })
      return
    }

    const script = document.createElement('script')
    script.id = MAX_MODE_WIDGET_SCRIPT_ID
    script.src = MAX_MODE_WIDGET_SCRIPT_SRC
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load Max Mode widget bundle.'))
    document.head.appendChild(script)
  }).catch((error) => {
    maxModeWidgetScriptPromise = null
    throw error
  })

  return maxModeWidgetScriptPromise
}

function clearMaxModeWidgetSession() {
  if (typeof window === 'undefined') {
    return
  }
  window.sessionStorage.removeItem(MAX_MODE_WIDGET_STATE_KEY)
  window.sessionStorage.removeItem(MAX_MODE_WIDGET_PENDING_ATTACHMENTS_KEY)
}

function readMaxModeWidgetConversationId() {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    const raw = window.sessionStorage.getItem(MAX_MODE_WIDGET_STATE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as unknown
    if (!isRecord(parsed)) {
      return null
    }
    const conversationId = parsed.conversationId
    return typeof conversationId === 'string' && conversationId.trim().length > 0 ? conversationId.trim() : null
  } catch {
    return null
  }
}

function widgetAdapterBaseUrl(deploymentId: string) {
  return `${getPlatformApiBaseUrl().replace(/\/$/, '')}/api/deployments/${encodeURIComponent(deploymentId)}/poc-widget`
}

function sampleImportRecordsForVectorSpace(vectorSpace: string): DeploymentPocImportRecordRequest[] {
  switch (vectorSpace.trim().toLowerCase()) {
    case 'review':
      return [
        {
          id: 'REV-POC-001',
          content: 'Customers say the waterproof trail shoes stay comfortable during long hikes and wet conditions.',
          metadata: {
            rating: 5,
            sentiment: 'positive',
          },
        },
      ]
    case 'policy':
      return [
        {
          id: 'POL-POC-001',
          content: 'Premium footwear can be returned within 30 days if unworn, with free exchanges for size issues.',
          metadata: {
            policyType: 'returns',
            region: 'global',
          },
        },
      ]
    default:
      return [
        {
          id: 'SKU-POC-001',
          content: 'Premium trail shoes with waterproof lining, free shipping, and a 30-day returns policy.',
          metadata: {
            category: 'Footwear',
            priceBand: 'premium',
          },
        },
      ]
  }
}

function sampleImportPayloadForVectorSpace(vectorSpace: string): string {
  return JSON.stringify(sampleImportRecordsForVectorSpace(vectorSpace), null, 2)
}

function migrationCheckSeverity(status: string): 'success' | 'warning' | 'error' | 'default' {
  if (status === 'READY') {
    return 'success'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'BLOCKED') {
    return 'error'
  }
  return 'default'
}

function contentLengthForRecord(record: DeploymentPocImportRecordRequest): number {
  return record.content?.length ?? JSON.stringify(record.entity ?? {}).length
}

function buildImportRiskSummary(
  parseError: string | null,
  records: DeploymentPocImportRecordRequest[],
  maxRecordsPerRun: number,
  maxContentLength: number,
  readinessStatuses: string[],
) {
  if (parseError) {
    return {
      severity: 'error' as const,
      summary: 'Fix the payload before continuing.',
      details: [parseError],
    }
  }

  const blocked = readinessStatuses.filter((status) => status === 'BLOCKED').length
  const warnings = readinessStatuses.filter((status) => status === 'WARNING').length
  const largestRecord = records.reduce((largest, record) => Math.max(largest, contentLengthForRecord(record)), 0)
  const details: string[] = []

  if (records.length === 0) {
    details.push('Add at least one record before running the import.')
  }
  if (blocked > 0) {
    details.push('Platform readiness checks show at least one blocking dependency for imports.')
  }
  if (records.length > maxRecordsPerRun) {
    details.push(`This batch contains ${records.length} records, above the limit of ${maxRecordsPerRun}.`)
  }
  if (largestRecord > maxContentLength) {
    details.push(`At least one record exceeds the ${maxContentLength}-character content limit.`)
  }
  if (warnings > 0) {
    details.push('Some optional validation capabilities are missing, so post-import verification will be limited.')
  }

  if (details.length === 0) {
    return {
      severity: 'success' as const,
      summary: 'The import plan is ready for execution.',
      details: ['Import transport, payload, and size checks passed for this bounded POC import.'],
    }
  }

  if (blocked > 0 || records.length === 0 || records.length > maxRecordsPerRun || largestRecord > maxContentLength) {
    return {
      severity: 'error' as const,
      summary: 'The import plan has blocking issues.',
      details,
    }
  }

  return {
    severity: 'warning' as const,
    summary: 'The import can proceed, but validation coverage is partial.',
    details,
  }
}

export function PocPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [migrationStep, setMigrationStep] = useState(0)
  const [migrationSource, setMigrationSource] = useState<MigrationSourceKey>('TEMPLATE_SAMPLE')
  const [selectedAuthPath, setSelectedAuthPath] = useState<DeploymentPocAuthPath>('PLATFORM_PRIVATE')
  const [draftQueryText, setDraftQueryText] = useState('')
  const [conversationId, setConversationId] = useState('')
  const [lastQueryText, setLastQueryText] = useState('')
  const [lastResult, setLastResult] = useState<unknown>(null)
  const [lastTraceSummary, setLastTraceSummary] = useState<DeploymentPocTraceSummary | null>(null)
  const [importLabel, setImportLabel] = useState('Operator POC import')
  const [importVectorSpace, setImportVectorSpace] = useState('product')
  const [importPayloadText, setImportPayloadText] = useState(
    JSON.stringify(
      [
        {
          id: 'SKU-POC-001',
          content: 'Premium trail shoes with waterproof lining and free shipping.',
          metadata: {
            category: 'Footwear',
            priceBand: 'premium',
          },
        },
      ],
      null,
      2,
    ),
  )
  const [lastImportRun, setLastImportRun] = useState<DeploymentPocImportRunSummary | null>(null)
  const [widgetScriptReady, setWidgetScriptReady] = useState(false)
  const [widgetScriptError, setWidgetScriptError] = useState<string | null>(null)
  const [widgetLifecycleLabel, setWidgetLifecycleLabel] = useState<'closed' | 'open' | 'error'>('closed')
  const [widgetLifecycleDetail, setWidgetLifecycleDetail] = useState<string | null>(null)
  const [widgetVersion, setWidgetVersion] = useState<string | null>(null)
  const [widgetReloadNonce, setWidgetReloadNonce] = useState(0)
  const importFileInputRef = useRef<HTMLInputElement | null>(null)
  const runtimeUnavailable = !workspace?.deployment.runtimeBaseUrl
  const canOperate = workspace?.access.canOperate ?? false

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

  const promptSessionQuery = useQuery({
    queryKey: ['deployment-poc-prompt-session', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocPromptSession(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const integrationSummaryQuery = useQuery({
    queryKey: ['deployment-poc-integration-summary', selectedDeploymentId],
    queryFn: () => fetchDeploymentIntegrationSummary(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const runtimeAuthContextQuery = useQuery({
    queryKey: ['deployment-poc-runtime-auth-context', selectedDeploymentId, selectedAuthPath],
    queryFn: () => fetchDeploymentPocRuntimeAuthContext(selectedDeploymentId, selectedAuthPath),
    enabled: selectedDeploymentId.length > 0 && Boolean(workspace?.deployment.runtimeBaseUrl),
  })

  const conversationQuery = useQuery({
    queryKey: ['deployment-poc-conversation', selectedDeploymentId, conversationId, selectedAuthPath],
    queryFn: () => fetchDeploymentPocConversation(selectedDeploymentId, conversationId, selectedAuthPath),
    enabled:
      selectedDeploymentId.length > 0 &&
      conversationId.trim().length > 0 &&
      Boolean(workspace?.deployment.runtimeBaseUrl),
    retry: (failureCount, error) =>
      !(error instanceof PlatformApiError && error.status === 404) && failureCount < 3,
  })

  const suggestionsQuery = useQuery({
    queryKey: [
      'deployment-poc-suggestions',
      selectedDeploymentId,
      workspace?.deployment.name,
      workspace?.template.name,
      selectedAuthPath,
    ],
    queryFn: () =>
      fetchDeploymentPocChatSuggestions(selectedDeploymentId, {
        content: `Deployment ${workspace?.deployment.name ?? ''} using template ${workspace?.template.name ?? ''}`,
        maxSuggestions: 4,
        authPath: selectedAuthPath,
      }),
    enabled: selectedDeploymentId.length > 0 && Boolean(workspace?.deployment.runtimeBaseUrl),
  })

  const queryMutation = useMutation({
    mutationFn: (queryText: string) =>
      queryDeploymentPocChat(selectedDeploymentId, {
        query: queryText,
        conversationId: conversationId || undefined,
        authPath: selectedAuthPath,
      }),
    onSuccess: async (response, queryText) => {
      setDraftQueryText('')
      setLastQueryText(queryText)
      const hydrateConversationTranscript = shouldHydrateConversationTranscript(response.result, response.traceSummary)
      if (response.conversationId && hydrateConversationTranscript) {
        setConversationId(response.conversationId)
      }
      setLastResult(response.result)
      setLastTraceSummary(response.traceSummary)
      if (response.conversationId && hydrateConversationTranscript) {
        await queryClient.invalidateQueries({
          queryKey: ['deployment-poc-conversation', selectedDeploymentId, response.conversationId, selectedAuthPath],
        })
      }
    },
  })

  const resetConversationMutation = useMutation({
    mutationFn: async () => {
      const widgetConversationId = readMaxModeWidgetConversationId()
      if (widgetConversationId) {
        await deleteDeploymentPocConversation(selectedDeploymentId, widgetConversationId, selectedAuthPath)
      }
    },
    onSuccess: async () => {
      const previousConversationId = readMaxModeWidgetConversationId()
      clearMaxModeWidgetSession()
      window.MaxMode?.destroy()
      setWidgetLifecycleLabel('closed')
      setWidgetLifecycleDetail(null)
      setWidgetReloadNonce((current) => current + 1)
      setConversationId('')
      setLastQueryText('')
      setLastResult(null)
      setLastTraceSummary(null)
      if (previousConversationId) {
        await queryClient.invalidateQueries({
          queryKey: ['deployment-poc-conversation', selectedDeploymentId, previousConversationId, selectedAuthPath],
        })
      }
    },
  })

  useEffect(() => {
    if (
      !(conversationQuery.error instanceof PlatformApiError) ||
      conversationQuery.error.status !== 404 ||
      lastResult != null ||
      lastTraceSummary != null
    ) {
      return
    }
    setConversationId('')
  }, [conversationQuery.error, lastResult, lastTraceSummary])

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

  const importMutation = useMutation({
    mutationFn: () =>
      runDeploymentPocImport(selectedDeploymentId, {
        datasetLabel: importLabel.trim() || 'Operator POC import',
        vectorSpace: importVectorSpace.trim(),
        records: parseImportPayload(importPayloadText),
      }),
    onSuccess: async (response) => {
      setLastImportRun(response)
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
    setMigrationStep(0)
    setMigrationSource('TEMPLATE_SAMPLE')
    setSelectedAuthPath('PLATFORM_PRIVATE')
    clearMaxModeWidgetSession()
    window.MaxMode?.destroy()
    setWidgetLifecycleLabel('closed')
    setWidgetLifecycleDetail(null)
    setWidgetScriptError(null)
    setConversationId('')
    setLastQueryText('')
    setLastResult(null)
    setLastTraceSummary(null)
    setDraftQueryText('')
    setLastImportRun(null)
  }, [selectedDeploymentId])

  const transcriptUnavailable =
    conversationQuery.error instanceof PlatformApiError && conversationQuery.error.status === 404
  const fallbackTranscriptTurn =
    transcriptUnavailable && lastResult
      ? {
          timestamp: null,
          userQuery: lastQueryText || 'Last query',
          aiResponse: extractLatestAssistantMessage(lastResult, lastTraceSummary),
        }
      : null
  const transcriptTurns = fallbackTranscriptTurn
    ? [fallbackTranscriptTurn]
    : (conversationQuery.data?.turns ?? [])

  const dynamicSuggestions = suggestionsQuery.data?.suggestions ?? []
  const integrationSummary = integrationSummaryQuery.data
  const supportedAuthPaths = useMemo(
    () => availablePocAuthPaths(integrationSummary),
    [integrationSummary],
  )
  const promptPreviewCompatible = selectedAuthPath === 'PLATFORM_PRIVATE'
  const widgetBaseUrl = useMemo(
    () => (selectedDeploymentId ? widgetAdapterBaseUrl(selectedDeploymentId) : ''),
    [selectedDeploymentId],
  )
  const widgetAuthSuffix = useMemo(
    () => `authPath=${encodeURIComponent(selectedAuthPath)}`,
    [selectedAuthPath],
  )
  const widgetConversationItemUrlTemplate = useMemo(
    () => (widgetBaseUrl ? `${widgetBaseUrl}/chat/me/conversations/{conversationId}?${widgetAuthSuffix}` : ''),
    [widgetAuthSuffix, widgetBaseUrl],
  )
  const countsByEntityType = pocWorkspaceQuery.data?.indexing.countsByEntityType ?? {}
  const migrationGuide = pocWorkspaceQuery.data?.migration
  const migrationSources = migrationGuide?.supportedSources ?? []
  const supportedVectorSpaces = migrationGuide?.supportedVectorSpaces ?? []
  const visibleWarnings = [...(pocWorkspaceQuery.data?.warnings ?? [])]
  const recentImports = pocWorkspaceQuery.data?.recentImports ?? []
  const promptSession = promptSessionQuery.data
  const selectedMigrationSource = migrationSources.find((source) => source.key === migrationSource) ?? null
  const importTransportCheck = migrationGuide?.readinessChecks.find((check) => check.key === 'IMPORT_TRANSPORT') ?? null
  const parsedImport = useMemo(() => {
    try {
      return {
        records: parseImportPayload(importPayloadText),
        error: null as string | null,
      }
    } catch (error) {
      return {
        records: [] as DeploymentPocImportRecordRequest[],
        error: error instanceof Error ? error.message : 'Invalid import payload.',
      }
    }
  }, [importPayloadText])
  const largestRecordSize = useMemo(
    () => parsedImport.records.reduce((largest, record) => Math.max(largest, contentLengthForRecord(record)), 0),
    [parsedImport.records],
  )
  const importRisk = useMemo(
    () => buildImportRiskSummary(
      parsedImport.error,
      parsedImport.records,
      migrationGuide?.maxRecordsPerRun ?? 100,
      migrationGuide?.maxContentLength ?? 16000,
      migrationGuide?.readinessChecks.map((check) => check.status) ?? [],
    ),
    [
      migrationGuide?.maxContentLength,
      migrationGuide?.maxRecordsPerRun,
      migrationGuide?.readinessChecks,
      parsedImport.error,
      parsedImport.records,
    ],
  )
  const actionValidationSummary = useMemo(
    () => summarizeActionValidation(lastTraceSummary?.actionValidation ?? null),
    [lastTraceSummary],
  )
  const importTransportBlocked = importTransportCheck?.status === 'BLOCKED'

  useEffect(() => {
    if (!supportedAuthPaths.includes(selectedAuthPath)) {
      setSelectedAuthPath(supportedAuthPaths[0])
    }
  }, [selectedAuthPath, supportedAuthPaths])

  useEffect(() => {
    clearMaxModeWidgetSession()
    window.MaxMode?.destroy()
    setWidgetLifecycleLabel('closed')
    setWidgetLifecycleDetail(null)
    setWidgetReloadNonce((current) => current + 1)
    setConversationId('')
    setLastQueryText('')
    setLastResult(null)
    setLastTraceSummary(null)
  }, [selectedAuthPath])

  useEffect(() => {
    if (!selectedDeploymentId || runtimeUnavailable || !canOperate || !widgetBaseUrl) {
      return
    }

    let cancelled = false
    setWidgetScriptError(null)

    loadMaxModeWidgetScript()
      .then(() => {
        if (cancelled || !window.MaxMode) {
          return
        }
        const platformApiKey = getStoredPlatformApiKey()
        window.MaxMode.destroy()
        window.MaxMode.init({
          apiConfig: {
            chatBaseUrl: widgetBaseUrl,
            defaultHeaders: platformApiKey ? { 'X-PLATFORM-API-KEY': platformApiKey } : undefined,
            fetchCredentials: 'include',
            runtimeRoutes: {
              chatQueryUrl: `${widgetBaseUrl}/chat/me/query?${widgetAuthSuffix}`,
              suggestionsUrl: `${widgetBaseUrl}/chat/me/suggestions?${widgetAuthSuffix}`,
              authContextUrl: `${widgetBaseUrl}/chat/me/auth-context?${widgetAuthSuffix}`,
              conversationsUrl: `${widgetBaseUrl}/chat/me/conversations?${widgetAuthSuffix}`,
              conversationItemUrlTemplate: widgetConversationItemUrlTemplate,
            },
            runtimeAuth: {
              probeAuthContextOnOpen: false,
            },
          },
          integrationMode: 'backend-mediated-private-runtime',
          launcher: false,
          position: 'bottom-right',
          features: {
            cart: false,
            debug: true,
            conversations: true,
            quickActions: true,
          },
          onEvent: (event) => {
            if (event.type === 'widget:opened') {
              setWidgetLifecycleLabel('open')
            } else if (event.type === 'widget:closed') {
              setWidgetLifecycleLabel('closed')
            } else if (event.type === 'error') {
              setWidgetLifecycleLabel('error')
              setWidgetLifecycleDetail(readString((event.data as { message?: unknown } | undefined)?.message) ?? 'Widget error')
            }
          },
        })
        setWidgetScriptReady(true)
        setWidgetVersion(window.MaxMode.version ?? null)
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        setWidgetScriptError(error instanceof Error ? error.message : 'Failed to load Max Mode widget.')
        setWidgetLifecycleLabel('error')
      })

    return () => {
      cancelled = true
      window.MaxMode?.destroy()
    }
  }, [
    canOperate,
    runtimeUnavailable,
    selectedDeploymentId,
    widgetAuthSuffix,
    widgetBaseUrl,
    widgetConversationItemUrlTemplate,
    widgetReloadNonce,
  ])

  const canContinueFromScope = importVectorSpace.trim().length > 0
    && parsedImport.error == null
    && parsedImport.records.length > 0
  const canContinueFromReadiness = importRisk.severity !== 'error'
  const importExecutionDisabled = !canOperate
    || importTransportBlocked
    || importMutation.isPending
    || parsedImport.error != null
    || parsedImport.records.length === 0
    || importRisk.severity === 'error'

  const migrationNextDisabled = migrationStep === 0
    ? migrationSources.length === 0
    : migrationStep === 1
      ? !canContinueFromScope
      : migrationStep === 2
        ? !canContinueFromReadiness
        : false

  useEffect(() => {
    if (!migrationGuide) {
      return
    }
    if (!supportedVectorSpaces.includes(importVectorSpace)) {
      setImportVectorSpace(migrationGuide.defaultVectorSpace)
    }
    if (importLabel === 'Operator POC import') {
      setImportLabel(migrationGuide.suggestedDatasetLabel)
    }
  }, [importLabel, importVectorSpace, migrationGuide, supportedVectorSpaces])

  const handleImportFileSelection = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    try {
      const text = await file.text()
      setImportPayloadText(text)
    } finally {
      event.target.value = ''
    }
  }

  const openMaxModeWidget = () => {
    if (!widgetScriptReady || !window.MaxMode) {
      return
    }
    window.MaxMode.open()
  }

  const closeMaxModeWidget = () => {
    window.MaxMode?.close()
  }

  const openWidgetForPrompt = async (prompt: string) => {
    const trimmedPrompt = prompt.trim()
    if (!trimmedPrompt) {
      return
    }
    try {
      await navigator.clipboard.writeText(trimmedPrompt)
      setWidgetLifecycleDetail('Prompt copied. Paste it into the widget composer.')
    } catch {
      setWidgetLifecycleDetail('Open the widget and paste the scenario prompt manually.')
    }
    openMaxModeWidget()
  }

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
              <Chip label={`Auth path: ${authPathLabel(selectedAuthPath)}`} color="primary" variant="outlined" />
              <Chip
                label={
                  promptSession?.active
                    ? `Prompt hot apply: ${promptSession.sessionLabel ?? 'active'}`
                    : 'Prompt hot apply: inactive'
                }
                color={promptSession?.active ? 'secondary' : 'default'}
                variant="outlined"
              />
              {workspace?.deployment.activeVersion ? (
                <Chip label={`Active version: ${workspace.deployment.activeVersion}`} variant="outlined" />
              ) : null}
            </Stack>

            <TextField
              select
              label="POC auth path"
              size="small"
              value={selectedAuthPath}
              onChange={(event) => setSelectedAuthPath(event.target.value as DeploymentPocAuthPath)}
              sx={{ maxWidth: 320 }}
              disabled={runtimeUnavailable || supportedAuthPaths.length <= 1}
              helperText={authPathDescription(selectedAuthPath)}
            >
              {POC_AUTH_PATH_OPTIONS.filter((option) => supportedAuthPaths.includes(option.value)).map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>

            {runtimeUnavailable ? (
              <Alert severity="warning">
                This deployment does not have a runtime URL yet. Apply the deployment before using the embedded POC
                chat console.
              </Alert>
            ) : !canOperate ? (
              <Alert severity="info">
                This deployment role is read-only. POC chat, dataset import, and reset operations require operator access or higher.
              </Alert>
            ) : (
              <Stack spacing={1.5}>
                <Alert severity="info">
                  Scenario suggestions are deployment-aware. Use them to test prompt behavior, retrieval, and live
                  actions in a controlled operator session.
                </Alert>
                {integrationSummaryQuery.isError ? (
                  <Alert severity="warning">
                    Integration posture could not be loaded, so the POC auth-path selector falls back to the private platform path.
                  </Alert>
                ) : null}
                {!promptPreviewCompatible && promptSession?.active ? (
                  <Alert severity="warning">
                    Prompt hot apply stays scoped to the platform-private POC path. Public auth-path simulation suppresses prompt-preview overrides.
                  </Alert>
                ) : null}
                {promptSession?.active ? (
                  <Alert severity="success">
                    Prompt hot apply is active for this operator session with {promptSession.promptKeyCount} prompt
                    override{promptSession.promptKeyCount === 1 ? '' : 's'}. Clear it from the Prompts page when you
                    want to return to the saved deployment behavior.
                  </Alert>
                ) : null}
              </Stack>
            )}

            {runtimeAuthContextQuery.isSuccess ? (
              <Card variant="outlined" sx={{ borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Stack
                      direction={{ xs: 'column', md: 'row' }}
                      spacing={1.5}
                      justifyContent="space-between"
                      alignItems={{ xs: 'flex-start', md: 'center' }}
                    >
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                          Runtime auth context
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {selectedAuthPath === 'PLATFORM_PRIVATE'
                            ? 'First-party proof of the verified runtime identity used by the platform POC proxy.'
                            : 'Proof of the browser-token identity the platform is using to simulate the selected public runtime path.'}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Selected path: ${authPathLabel(selectedAuthPath)}`} size="small" color="primary" />
                        <Chip label={`Auth mode: ${runtimeAuthContextQuery.data.authMode ?? '—'}`} size="small" variant="outlined" />
                        <Chip label={`Subject type: ${runtimeAuthContextQuery.data.subjectType ?? '—'}`} size="small" variant="outlined" />
                        <Chip
                          label={selectedAuthPath === 'PLATFORM_PRIVATE' ? 'Verified context' : 'Public bearer'}
                          color="success"
                          size="small"
                        />
                      </Stack>
                    </Stack>

                    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                      <strong>Subject:</strong> {runtimeAuthContextQuery.data.subjectId ?? '—'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Caller:</strong> {runtimeAuthContextQuery.data.callerType ?? '—'}
                    </Typography>
                    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                      <strong>Session:</strong> {runtimeAuthContextQuery.data.sessionId ?? '—'}
                    </Typography>
                    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                      <strong>Scope:</strong> {runtimeAuthContextQuery.data.deploymentId ?? '—'} / {runtimeAuthContextQuery.data.customerId ?? '—'} / {runtimeAuthContextQuery.data.tenantId ?? '—'}
                    </Typography>
                    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
                      <strong>Issuer:</strong> {runtimeAuthContextQuery.data.issuer ?? '—'}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Expires:</strong> {formatDateTime(runtimeAuthContextQuery.data.expiresAt)}
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {runtimeAuthContextQuery.data.grantedScopes.map((scope) => (
                        <Chip key={scope} label={scope} size="small" />
                      ))}
                    </Stack>
                    {runtimeAuthContextQuery.data.warnings.length > 0 ? (
                      <Alert severity="warning">
                        {runtimeAuthContextQuery.data.warnings.join(', ')}
                      </Alert>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            {runtimeAuthContextQuery.isError ? (
              <Alert severity="warning">
                {runtimeAuthContextQuery.error instanceof Error
                  ? runtimeAuthContextQuery.error.message
                  : 'Runtime auth context is unavailable for this deployment.'}
              </Alert>
            ) : null}
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
                        !canOperate ||
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

            <Stack direction={{ xs: 'column', xl: 'row' }} spacing={2.5} alignItems="stretch">
              <Card variant="outlined" sx={{ flex: 1.2, borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', lg: 'center' }} spacing={1.5}>
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                          POC migration wizard
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Guide a bounded import through source selection, target entity scope, readiness checks, and
                          execution without leaving the deployment workspace.
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip
                          label={selectedMigrationSource?.label ?? 'Choose source'}
                          color="primary"
                          variant="outlined"
                        />
                        <Chip label={`Vector space: ${importVectorSpace || '—'}`} variant="outlined" />
                        <Chip label={`Records: ${parsedImport.records.length}`} variant="outlined" />
                      </Stack>
                    </Stack>

                    <Stepper activeStep={migrationStep} alternativeLabel>
                      {POC_MIGRATION_STEPS.map((label) => (
                        <Step key={label}>
                          <StepLabel>{label}</StepLabel>
                        </Step>
                      ))}
                    </Stepper>

                    {migrationGuide?.warnings.map((warning) => (
                      <Alert key={warning} severity="warning">
                        {warning}
                      </Alert>
                    ))}

                    {migrationStep === 0 ? (
                      <Grid container spacing={1.5}>
                        {migrationSources.map((source) => (
                          <Grid item xs={12} md={4} key={source.key}>
                            <Card
                              variant="outlined"
                              sx={{
                                borderColor: migrationSource === source.key ? 'primary.main' : 'divider',
                                height: '100%',
                              }}
                            >
                              <CardContent>
                                <Stack spacing={1.5} sx={{ height: '100%' }}>
                                  <Box>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                      {source.label}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                                      {source.description}
                                    </Typography>
                                  </Box>
                                  <Button
                                    variant={migrationSource === source.key ? 'contained' : 'outlined'}
                                    onClick={() => setMigrationSource(source.key as MigrationSourceKey)}
                                  >
                                    {migrationSource === source.key ? 'Selected' : 'Use this source'}
                                  </Button>
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        ))}
                      </Grid>
                    ) : null}

                    {migrationStep === 1 ? (
                      <Stack spacing={1.5}>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                          <TextField
                            label="Dataset label"
                            value={importLabel}
                            onChange={(event) => setImportLabel(event.target.value)}
                            sx={{ flex: 1 }}
                          />
                          <TextField
                            select
                            label="Vector space"
                            value={importVectorSpace}
                            onChange={(event) => setImportVectorSpace(event.target.value)}
                            sx={{ width: { xs: '100%', md: 240 } }}
                          >
                            {supportedVectorSpaces.map((vectorSpace) => (
                              <MenuItem key={vectorSpace} value={vectorSpace}>
                                {vectorSpace}
                              </MenuItem>
                            ))}
                          </TextField>
                        </Stack>

                        <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
                          <input
                            ref={importFileInputRef}
                            type="file"
                            accept="application/json,.json"
                            hidden
                            onChange={handleImportFileSelection}
                          />
                          {migrationSource === 'JSON_FILE' ? (
                            <Button
                              variant="outlined"
                              startIcon={<UploadFileRoundedIcon />}
                              disabled={!canOperate}
                              onClick={() => importFileInputRef.current?.click()}
                            >
                              Load JSON file
                            </Button>
                          ) : null}
                          {migrationSource === 'TEMPLATE_SAMPLE' ? (
                            <Button
                              variant="outlined"
                              onClick={() => {
                                setImportLabel(`${migrationGuide?.suggestedDatasetLabel ?? 'Operator POC import'} (${importVectorSpace})`)
                                setImportPayloadText(
                                  sampleImportPayloadForVectorSpace(importVectorSpace || migrationGuide?.defaultVectorSpace || 'default'),
                                )
                              }}
                            >
                              Load template sample
                            </Button>
                          ) : null}
                          <Button
                            variant="outlined"
                            color="inherit"
                            onClick={() => {
                              const resetVectorSpace = importVectorSpace || migrationGuide?.defaultVectorSpace || 'default'
                              setImportLabel(migrationGuide?.suggestedDatasetLabel ?? 'Operator POC import')
                              setImportPayloadText(sampleImportPayloadForVectorSpace(resetVectorSpace))
                            }}
                          >
                            Reset sample
                          </Button>
                        </Stack>

                        {migrationSource === 'TEMPLATE_SAMPLE' ? (
                          <Alert severity="info">
                            Template sample mode loads a small starter batch for the selected vector space so you can
                            validate indexing and chatbot behavior quickly.
                          </Alert>
                        ) : migrationSource === 'JSON_FILE' ? (
                          <Alert severity="info">
                            Upload a small sanitized JSON array. This path is intended for workshop-safe proof-of-concept
                            batches, not full migration loads.
                          </Alert>
                        ) : (
                          <Alert severity="info">
                            Paste a JSON array directly. Each record requires `id` and either `content` or `entity`.
                          </Alert>
                        )}

                        {parsedImport.error ? (
                          <Alert severity="error">{parsedImport.error}</Alert>
                        ) : null}

                        <TextField
                          label="Dataset JSON"
                          multiline
                          minRows={12}
                          value={importPayloadText}
                          onChange={(event) => setImportPayloadText(event.target.value)}
                          helperText={`Provide a JSON array. This path is capped at ${migrationGuide?.maxRecordsPerRun ?? 100} records and ${migrationGuide?.maxContentLength ?? 16000} characters per content field.`}
                        />
                      </Stack>
                    ) : null}

                    {migrationStep === 2 ? (
                      <Stack spacing={1.5}>
                        <Alert severity={importRisk.severity}>{importRisk.summary}</Alert>
                        <Grid container spacing={1.5}>
                          {(migrationGuide?.readinessChecks ?? []).map((check) => (
                            <Grid item xs={12} md={6} key={check.key}>
                              <Card variant="outlined" sx={{ height: '100%' }}>
                                <CardContent>
                                  <Stack spacing={1}>
                                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                        {check.label}
                                      </Typography>
                                      <Chip
                                        label={check.status}
                                        size="small"
                                        color={migrationCheckSeverity(check.status)}
                                        variant="outlined"
                                      />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary">
                                      {check.message}
                                    </Typography>
                                  </Stack>
                                </CardContent>
                              </Card>
                            </Grid>
                          ))}
                        </Grid>
                        <Grid container spacing={1.5}>
                          <Grid item xs={12} md={6}>
                            <Card variant="outlined">
                              <CardContent>
                                <Stack spacing={1}>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                    Import scope
                                  </Typography>
                                  <Typography variant="body2"><strong>Source:</strong> {selectedMigrationSource?.label ?? migrationSource}</Typography>
                                  <Typography variant="body2"><strong>Dataset:</strong> {importLabel || '—'}</Typography>
                                  <Typography variant="body2"><strong>Vector space:</strong> {importVectorSpace || '—'}</Typography>
                                  <Typography variant="body2"><strong>Records:</strong> {parsedImport.records.length}</Typography>
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                          <Grid item xs={12} md={6}>
                            <Card variant="outlined">
                              <CardContent>
                                <Stack spacing={1}>
                                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                    Payload sizing
                                  </Typography>
                                  <Typography variant="body2"><strong>Largest record:</strong> {largestRecordSize} chars</Typography>
                                  <Typography variant="body2"><strong>Per-record limit:</strong> {migrationGuide?.maxContentLength ?? 16000} chars</Typography>
                                  <Typography variant="body2"><strong>Batch limit:</strong> {migrationGuide?.maxRecordsPerRun ?? 100} records</Typography>
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        </Grid>
                        {importRisk.details.map((detail) => (
                          <Typography key={detail} variant="body2" color="text.secondary">
                            {detail}
                          </Typography>
                        ))}
                      </Stack>
                    ) : null}

                    {migrationStep === 3 ? (
                      <Stack spacing={1.5}>
                        {importMutation.isError ? (
                          <Alert severity="error">
                            {importMutation.error instanceof Error ? importMutation.error.message : 'Import failed'}
                          </Alert>
                        ) : null}

                        {lastImportRun ? (
                          <Alert severity={lastImportRun.status === 'SUCCEEDED' ? 'success' : 'warning'}>
                            Import {lastImportRun.status.toLowerCase()}: {lastImportRun.importedCount} imported,{' '}
                            {lastImportRun.failedCount} failed.
                            {lastImportRun.errorMessage ? ` ${lastImportRun.errorMessage}` : ''}
                          </Alert>
                        ) : null}

                        <Card variant="outlined">
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                Final review
                              </Typography>
                              <Typography variant="body2"><strong>Source:</strong> {selectedMigrationSource?.label ?? migrationSource}</Typography>
                              <Typography variant="body2"><strong>Dataset:</strong> {importLabel || '—'}</Typography>
                              <Typography variant="body2"><strong>Vector space:</strong> {importVectorSpace || '—'}</Typography>
                              <Typography variant="body2"><strong>Records:</strong> {parsedImport.records.length}</Typography>
                              <Typography variant="body2"><strong>Readiness:</strong> {importRisk.summary}</Typography>
                            </Stack>
                          </CardContent>
                        </Card>

                        {importTransportBlocked ? (
                          <Alert severity="warning">
                            {importTransportCheck?.message ?? 'This deployment does not yet expose a usable import transport.'}
                          </Alert>
                        ) : null}
                      </Stack>
                    ) : null}

                    <Stack direction="row" justifyContent="space-between" spacing={1.5}>
                      <Button
                        variant="outlined"
                        disabled={migrationStep === 0}
                        onClick={() => setMigrationStep((current) => Math.max(0, current - 1))}
                      >
                        Back
                      </Button>
                      <Stack direction="row" spacing={1.5}>
                        {migrationStep < POC_MIGRATION_STEPS.length - 1 ? (
                          <Button
                            variant="contained"
                            disabled={migrationNextDisabled}
                            onClick={() => setMigrationStep((current) => Math.min(POC_MIGRATION_STEPS.length - 1, current + 1))}
                          >
                            Continue
                          </Button>
                        ) : (
                          <Button
                            variant="contained"
                            startIcon={<StorageRoundedIcon />}
                            disabled={importExecutionDisabled}
                            onClick={() => importMutation.mutate()}
                          >
                            {importMutation.isPending ? 'Importing...' : 'Run import'}
                          </Button>
                        )}
                      </Stack>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <Card variant="outlined" sx={{ flex: 0.8, borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Recent import runs
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Keep the import loop transparent so operators can see what data was loaded and whether the
                      connector accepted it.
                    </Typography>
                    {recentImports.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No import runs yet for this deployment.
                      </Typography>
                    ) : (
                      recentImports.map((importRun) => (
                        <Card key={importRun.id} variant="outlined" sx={{ borderColor: 'divider' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="center">
                                <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                  {importRun.datasetLabel}
                                </Typography>
                                <Chip
                                  label={importRun.status}
                                  color={importStatusColor(importRun.status)}
                                  size="small"
                                  variant="outlined"
                                />
                              </Stack>
                              <Typography variant="caption" color="text.secondary">
                                {formatDateTime(importRun.createdAt)} · {importRun.vectorSpace}
                              </Typography>
                              <Typography variant="body2">
                                <strong>Records:</strong> {importRun.recordCount} · <strong>Imported:</strong>{' '}
                                {importRun.importedCount} · <strong>Failed:</strong> {importRun.failedCount}
                              </Typography>
                              {importRun.errorMessage ? (
                                <Typography variant="body2" color="warning.main">
                                  {importRun.errorMessage}
                                </Typography>
                              ) : null}
                            </Stack>
                          </CardContent>
                        </Card>
                      ))
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Stack direction={{ xs: 'column', xl: 'row' }} spacing={3} alignItems="stretch">
        <Card sx={{ flex: 1.1, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={1.5}>
                <Box>
                  <Typography variant="h6">Live widget POC</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 920 }}>
                    The dummy chat console has been replaced by the deployed Max Mode widget bundle. The widget talks
                    to a platform-owned adapter that still honors the selected POC auth path for runtime simulation.
                  </Typography>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Button
                    variant="contained"
                    startIcon={<OpenInNewRoundedIcon />}
                    disabled={!canOperate || runtimeUnavailable || !widgetScriptReady}
                    onClick={openMaxModeWidget}
                  >
                    Open widget
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={!widgetScriptReady}
                    onClick={closeMaxModeWidget}
                  >
                    Close
                  </Button>
                  <Button
                    variant="outlined"
                    color="warning"
                    startIcon={<DeleteSweepRoundedIcon />}
                    disabled={!canOperate || runtimeUnavailable || resetConversationMutation.isPending}
                    onClick={() => resetConversationMutation.mutate()}
                  >
                    {resetConversationMutation.isPending ? 'Resetting...' : 'Reset widget conversation'}
                  </Button>
                </Stack>
              </Stack>

              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={`Bundle: ${widgetScriptReady ? 'loaded' : 'loading'}`} color={widgetScriptReady ? 'success' : 'default'} variant="outlined" />
                <Chip label={`Lifecycle: ${widgetLifecycleLabel}`} color={widgetLifecycleLabel === 'error' ? 'warning' : 'primary'} variant="outlined" />
                <Chip label={`Auth path: ${authPathLabel(selectedAuthPath)}`} color="primary" variant="outlined" />
                <Chip label="Transport: Platform adapter" variant="outlined" />
                {widgetVersion ? <Chip label={`Widget v${widgetVersion}`} variant="outlined" /> : null}
                {promptSession?.active ? (
                  <Chip
                    label={promptPreviewCompatible ? 'Prompt hot apply: active' : 'Prompt hot apply: private path only'}
                    color={promptPreviewCompatible ? 'secondary' : 'default'}
                    variant="outlined"
                  />
                ) : null}
              </Stack>

              {widgetScriptError ? <Alert severity="error">{widgetScriptError}</Alert> : null}
              {resetConversationMutation.isError ? (
                <Alert severity="error">
                  {resetConversationMutation.error instanceof Error
                    ? resetConversationMutation.error.message
                    : 'Widget conversation reset failed'}
                </Alert>
              ) : null}
              {!widgetScriptError && widgetLifecycleDetail ? (
                <Alert severity={widgetLifecycleLabel === 'error' ? 'warning' : 'info'}>
                  {widgetLifecycleDetail}
                </Alert>
              ) : null}

              <Card variant="outlined" sx={{ borderStyle: 'dashed', borderColor: 'divider' }}>
                <CardContent>
                  <Stack spacing={1.25}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Operator flow
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Use the buttons above to launch the real widget overlay. Scenario prompts below copy to the
                      clipboard and open the widget because the published IIFE bundle does not yet expose a composer
                      prefill API.
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Button
                        size="small"
                        variant="outlined"
                        component="a"
                        href={MAX_MODE_WIDGET_SCRIPT_SRC}
                        target="_blank"
                        rel="noreferrer"
                        startIcon={<OpenInNewRoundedIcon />}
                      >
                        Open deployed bundle
                      </Button>
                      <Chip label={widgetBaseUrl || 'Widget adapter unavailable'} size="small" variant="outlined" sx={{ maxWidth: '100%' }} />
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <Divider />

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
                              startIcon={<ContentCopyRoundedIcon />}
                              onClick={() => void openWidgetForPrompt(scenario.prompt)}
                              disabled={!canOperate || runtimeUnavailable || !widgetScriptReady}
                            >
                              Copy & open
                            </Button>
                            {scenario.editable ? (
                              <Button
                                size="small"
                                color="warning"
                                onClick={() => deleteScenarioMutation.mutate(scenario.id)}
                                disabled={!canOperate || deleteScenarioMutation.isPending}
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
                        onClick={() => void openWidgetForPrompt(suggestion)}
                        icon={<AutoFixHighRoundedIcon />}
                        variant="outlined"
                      />
                    ))}
                  </Stack>
                ) : null}
              </Stack>
            </Stack>
          </CardContent>
        </Card>

        <Card sx={{ width: { xs: '100%', xl: 420 }, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Typography variant="h6">Widget integration details</Typography>

              <Stack spacing={1}>
                <Typography variant="body2" color="text.secondary">
                  Browser boundary
                </Typography>
                <Typography variant="body2">
                  The widget stays browser-to-platform. The selected POC auth path is applied server-side by the
                  platform adapter when it calls the deployment runtime.
                </Typography>
              </Stack>

              <Stack spacing={1}>
                <Typography variant="body2" color="text.secondary">
                  Session and auth
                </Typography>
                <Typography variant="body2">
                  The widget adapter uses platform credentials with cross-origin cookie support and an operator API-key
                  fallback when present.
                </Typography>
              </Stack>

              <Stack spacing={1}>
                <Typography variant="body2" color="text.secondary">
                  Prompt preview
                </Typography>
                <Typography variant="body2">
                  Prompt hot-apply still works on the platform-private path because the platform adapter injects the
                  effective prompt preview before proxying widget queries to runtime.
                </Typography>
              </Stack>

              <Stack spacing={1}>
                <Typography variant="body2" color="text.secondary">
                  Current adapter routes
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
                  {jsonPreview({
                    chatBaseUrl: widgetBaseUrl,
                    chatQueryUrl: `${widgetBaseUrl}/chat/me/query?${widgetAuthSuffix}`,
                    suggestionsUrl: `${widgetBaseUrl}/chat/me/suggestions?${widgetAuthSuffix}`,
                    authContextUrl: `${widgetBaseUrl}/chat/me/auth-context?${widgetAuthSuffix}`,
                    conversationsUrl: `${widgetBaseUrl}/chat/me/conversations?${widgetAuthSuffix}`,
                    conversationItemUrlTemplate: widgetConversationItemUrlTemplate,
                  })}
                </Box>
              </Stack>

              <Alert severity="info">
                Widget debug mode is enabled for the POC. Use the widget’s own inspector for request and result
                details until the POC page is wired to consume widget message events.
              </Alert>
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </Stack>
  )
}
