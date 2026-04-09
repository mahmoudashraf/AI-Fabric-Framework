import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import BookmarkAddRoundedIcon from '@mui/icons-material/BookmarkAddRounded'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
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
  fetchDeploymentPocChatSuggestions,
  fetchDeploymentPocConversation,
  fetchDeploymentPocPromptSession,
  fetchDeploymentPocRuntimeAuthContext,
  fetchDeploymentPocScenarios,
  fetchDeploymentPocWorkspace,
  PlatformApiError,
  queryDeploymentPocChat,
  runDeploymentPocImport,
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
  const [draftQueryText, setDraftQueryText] = useState('')
  const [conversationId, setConversationId] = useState('')
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

  const runtimeAuthContextQuery = useQuery({
    queryKey: ['deployment-poc-runtime-auth-context', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocRuntimeAuthContext(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && Boolean(workspace?.deployment.runtimeBaseUrl),
  })

  const conversationQuery = useQuery({
    queryKey: ['deployment-poc-conversation', selectedDeploymentId, conversationId],
    queryFn: () => fetchDeploymentPocConversation(selectedDeploymentId, conversationId),
    enabled:
      selectedDeploymentId.length > 0 &&
      conversationId.trim().length > 0 &&
      Boolean(workspace?.deployment.runtimeBaseUrl),
    retry: (failureCount, error) =>
      !(error instanceof PlatformApiError && error.status === 404) && failureCount < 3,
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
      setLastTraceSummary(response.traceSummary)
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
      setLastTraceSummary(null)
      await queryClient.invalidateQueries({
        queryKey: ['deployment-poc-conversation', selectedDeploymentId, previousConversationId],
      })
    },
  })

  useEffect(() => {
    if (!(conversationQuery.error instanceof PlatformApiError) || conversationQuery.error.status !== 404) {
      return
    }
    setConversationId('')
    setLastResult(null)
    setLastTraceSummary(null)
  }, [conversationQuery.error])

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
    setConversationId('')
    setLastResult(null)
    setLastTraceSummary(null)
    setDraftQueryText('')
    setLastImportRun(null)
  }, [selectedDeploymentId])

  const dynamicSuggestions = suggestionsQuery.data?.suggestions ?? []
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
                          First-party proof of the verified runtime identity used by the platform POC proxy.
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Auth mode: ${runtimeAuthContextQuery.data.authMode ?? '—'}`} size="small" variant="outlined" />
                        <Chip label={`Subject type: ${runtimeAuthContextQuery.data.subjectType ?? '—'}`} size="small" variant="outlined" />
                        <Chip label="Verified context" color="success" size="small" />
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
        <Card sx={{ flex: 1, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="h6">Chatbot</Typography>
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<DeleteSweepRoundedIcon />}
                  disabled={!canOperate || !conversationId || resetConversationMutation.isPending}
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
                disabled={runtimeUnavailable || !canOperate}
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
                  disabled={!canOperate || runtimeUnavailable || queryMutation.isPending || draftQueryText.trim().length === 0}
                  onClick={() => queryMutation.mutate()}
                >
                  {queryMutation.isPending ? 'Sending...' : 'Send to deployment'}
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<BookmarkAddRoundedIcon />}
                  disabled={!canOperate || runtimeUnavailable || saveScenarioMutation.isPending || draftQueryText.trim().length === 0}
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
                  {lastTraceSummary ? (
                    <>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`Type: ${lastTraceSummary.resultType ?? '—'}`} variant="outlined" />
                        <Chip
                          label={`Success: ${lastTraceSummary.success ? 'true' : 'false'}`}
                          color={lastTraceSummary.success ? 'success' : 'warning'}
                          variant="outlined"
                        />
                        {lastTraceSummary.executedAction ? (
                          <Chip label={`Action: ${lastTraceSummary.executedAction}`} color="primary" variant="outlined" />
                        ) : null}
                        {lastTraceSummary.documentCount > 0 ? (
                          <Chip label={`Documents: ${lastTraceSummary.documentCount}`} variant="outlined" />
                        ) : null}
                        {lastTraceSummary.errorCode ? (
                          <Chip label={`Error: ${lastTraceSummary.errorCode}`} color="warning" variant="outlined" />
                        ) : null}
                      </Stack>

                      <Stack spacing={1}>
                        <Typography variant="body2" color="text.secondary">
                          Operator-facing message
                        </Typography>
                        <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
                          {lastTraceSummary.answer
                            ?? lastTraceSummary.actionSummary
                            ?? lastTraceSummary.message
                            ?? readResultLabel(lastResult, 'message')}
                        </Typography>
                      </Stack>

                      {lastTraceSummary.vectorSpaces.length > 0 || lastTraceSummary.candidateVectorSpaces.length > 0 ? (
                        <Stack spacing={1}>
                          <Typography variant="body2" color="text.secondary">
                            Retrieval routing
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {lastTraceSummary.routingStrategy ? (
                              <Chip label={`Strategy: ${lastTraceSummary.routingStrategy}`} size="small" />
                            ) : null}
                            {lastTraceSummary.vectorSpaces.map((vectorSpace) => (
                              <Chip key={`space-${vectorSpace}`} label={`Used: ${vectorSpace}`} size="small" variant="outlined" />
                            ))}
                            {lastTraceSummary.candidateVectorSpaces.map((vectorSpace) => (
                              <Chip
                                key={`candidate-${vectorSpace}`}
                                label={`Candidate: ${vectorSpace}`}
                                size="small"
                                variant="outlined"
                              />
                            ))}
                          </Stack>
                        </Stack>
                      ) : null}

                      {lastTraceSummary.documents.length > 0 ? (
                        <Stack spacing={1.25}>
                          <Typography variant="body2" color="text.secondary">
                            Grounding evidence
                          </Typography>
                          {lastTraceSummary.documents.map((document, index) => (
                            <Card key={`${document.id ?? document.title ?? 'doc'}-${index}`} variant="outlined" sx={{ borderColor: 'divider' }}>
                              <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                                <Stack spacing={1}>
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    {document.title ?? document.id ?? 'Document'}
                                  </Typography>
                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                    {document.vectorSpace ? <Chip label={document.vectorSpace} size="small" variant="outlined" /> : null}
                                    {document.score != null ? (
                                      <Chip label={`Score: ${document.score.toFixed(2)}`} size="small" variant="outlined" />
                                    ) : null}
                                    {document.source ? <Chip label={document.source} size="small" variant="outlined" /> : null}
                                  </Stack>
                                  {document.url ? (
                                    <Typography
                                      component="a"
                                      href={document.url}
                                      target="_blank"
                                      rel="noreferrer"
                                      variant="caption"
                                      sx={{ wordBreak: 'break-all' }}
                                    >
                                      {document.url}
                                    </Typography>
                                  ) : null}
                                </Stack>
                              </CardContent>
                            </Card>
                          ))}
                        </Stack>
                      ) : null}

                      {actionValidationSummary.missing.length > 0 || actionValidationSummary.provenanceMissing.length > 0 ? (
                        <Stack spacing={1}>
                          <Typography variant="body2" color="text.secondary">
                            Action validation
                          </Typography>
                          {actionValidationSummary.missing.length > 0 ? (
                            <Alert severity="warning">
                              Missing action parameters: {actionValidationSummary.missing.join(', ')}
                            </Alert>
                          ) : null}
                          {actionValidationSummary.provenanceMissing.length > 0 ? (
                            <Alert severity="warning">
                              Provenance gaps: {actionValidationSummary.provenanceMissing.join(', ')}
                            </Alert>
                          ) : null}
                          {actionValidationSummary.sourcesUsed.length > 0 ? (
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              {actionValidationSummary.sourcesUsed.map((source) => (
                                <Chip key={source} label={`Evidence: ${source}`} size="small" variant="outlined" />
                              ))}
                            </Stack>
                          ) : null}
                        </Stack>
                      ) : null}

                      {lastTraceSummary.childResultTypes.length > 0 ? (
                        <Stack spacing={1}>
                          <Typography variant="body2" color="text.secondary">
                            Child results
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {lastTraceSummary.childResultTypes.map((type, index) => (
                              <Chip key={`${type}-${index}`} label={type} size="small" variant="outlined" />
                            ))}
                          </Stack>
                        </Stack>
                      ) : null}
                    </>
                  ) : (
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
                    </>
                  )}
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
