import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded'
import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import PauseCircleOutlineRoundedIcon from '@mui/icons-material/PauseCircleOutlineRounded'
import PlayCircleOutlineRoundedIcon from '@mui/icons-material/PlayCircleOutlineRounded'
import PolicyRoundedIcon from '@mui/icons-material/PolicyRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import TokenRoundedIcon from '@mui/icons-material/TokenRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  cancelVectorizationRun,
  createVectorizationVerificationRun,
  createVectorizationRun,
  fetchVectorizationVerificationRunDetails,
  fetchVectorizationVerificationRuns,
  fetchVectorizationOverview,
  fetchVectorizationPreview,
  fetchVectorizationRunDetails,
  pauseVectorizationRun,
  resumeVectorizationRun,
  retryVectorizationRun,
  rotateVectorizationRunnerToken,
  upsertVectorizationConnection,
  upsertVectorizationPlan,
  updateVectorizationSyncState,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

type AdapterType = 'REST_API' | 'SQL' | 'FILE'
type AuthMode = 'NONE' | 'API_KEY' | 'BEARER' | 'BASIC'

type JsonRecord = Record<string, unknown>

type RestConfigState = {
  baseUrl: string
  path: string
  itemsPath: string
  countPath: string
  paginationMode: string
  pageSize: string
  startingPage: string
  pageParam: string
  pageSizeParam: string
  offsetParam: string
  cursorParam: string
  nextCursorPath: string
  hasMorePath: string
  apiKeyHeader: string
}

type SqlConfigState = {
  jdbcUrl: string
  driverClass: string
  countQuery: string
  pageQuery: string
}

type FileConfigState = {
  filePath: string
  format: string
}

type AuthSecretState = {
  apiKeyPlatformRef: string
  apiKeyLocalAlias: string
  bearerPlatformRef: string
  bearerLocalAlias: string
  usernamePlatformRef: string
  usernameLocalAlias: string
  passwordPlatformRef: string
  passwordLocalAlias: string
}

type EntityMappingState = {
  enabled: boolean
  dataset: string
  recordIdField: string
  recordVersionField: string
  entityFieldMappingsText: string
  metadataFieldMappingsText: string
  useDedicatedDatasetConfig: boolean
  restConfig: RestConfigState
  sqlConfig: SqlConfigState
  fileConfig: FileConfigState
}

function prettyJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2)
}

function parseJson(value: string, label: string): unknown {
  try {
    return JSON.parse(value)
  } catch {
    throw new Error(`${label} must be valid JSON.`)
  }
}

function asRecord(value: unknown): JsonRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as JsonRecord) : {}
}

function asRecordEntry(record: JsonRecord, key: string): JsonRecord {
  return asRecord(record[key])
}

function stringValue(record: JsonRecord, key: string, fallback = ''): string {
  const value = record[key]
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number') {
    return String(value)
  }
  return fallback
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function nonEmptyString(value: string): string | null {
  return value.trim().length > 0 ? value.trim() : null
}

function compactRecord(record: JsonRecord): JsonRecord {
  const output: JsonRecord = {}
  for (const [key, value] of Object.entries(record)) {
    if (value == null) {
      continue
    }
    if (typeof value === 'string' && value.trim().length === 0) {
      continue
    }
    if (Array.isArray(value) && value.length === 0) {
      continue
    }
    if (typeof value === 'object' && !Array.isArray(value) && Object.keys(value as JsonRecord).length === 0) {
      continue
    }
    output[key] = value
  }
  return output
}

function keyValueLines(value: unknown): string {
  const record = asRecord(value)
  return Object.entries(record)
    .map(([key, entry]) => `${key}=${typeof entry === 'string' ? entry : JSON.stringify(entry)}`)
    .join('\n')
}

function parseKeyValueLines(text: string): JsonRecord {
  const record: JsonRecord = {}
  text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const separator = line.includes('=') ? '=' : ':'
      const index = line.indexOf(separator)
      if (index <= 0) {
        return
      }
      const key = line.slice(0, index).trim()
      const value = line.slice(index + 1).trim()
      if (key.length > 0 && value.length > 0) {
        record[key] = value
      }
    })
  return record
}

function defaultRestConfig(): RestConfigState {
  return {
    baseUrl: '',
    path: '',
    itemsPath: '',
    countPath: '',
    paginationMode: 'OFFSET_LIMIT',
    pageSize: '100',
    startingPage: '1',
    pageParam: 'page',
    pageSizeParam: 'limit',
    offsetParam: 'offset',
    cursorParam: 'cursor',
    nextCursorPath: '',
    hasMorePath: '',
    apiKeyHeader: 'Authorization',
  }
}

function defaultSqlConfig(): SqlConfigState {
  return {
    jdbcUrl: '',
    driverClass: '',
    countQuery: '',
    pageQuery: '',
  }
}

function defaultFileConfig(): FileConfigState {
  return {
    filePath: '',
    format: 'JSON_ARRAY',
  }
}

function restConfigFromJson(config: JsonRecord): RestConfigState {
  const defaults = defaultRestConfig()
  return {
    baseUrl: stringValue(config, 'baseUrl', defaults.baseUrl),
    path: stringValue(config, 'path', defaults.path),
    itemsPath: stringValue(config, 'itemsPath', defaults.itemsPath),
    countPath: stringValue(config, 'countPath', defaults.countPath),
    paginationMode: stringValue(config, 'paginationMode', defaults.paginationMode),
    pageSize: stringValue(config, 'pageSize', defaults.pageSize),
    startingPage: stringValue(config, 'startingPage', defaults.startingPage),
    pageParam: stringValue(config, 'pageParam', defaults.pageParam),
    pageSizeParam: stringValue(config, 'pageSizeParam', defaults.pageSizeParam),
    offsetParam: stringValue(config, 'offsetParam', defaults.offsetParam),
    cursorParam: stringValue(config, 'cursorParam', defaults.cursorParam),
    nextCursorPath: stringValue(config, 'nextCursorPath', defaults.nextCursorPath),
    hasMorePath: stringValue(config, 'hasMorePath', defaults.hasMorePath),
    apiKeyHeader: stringValue(config, 'apiKeyHeader', defaults.apiKeyHeader),
  }
}

function sqlConfigFromJson(config: JsonRecord): SqlConfigState {
  return {
    jdbcUrl: stringValue(config, 'jdbcUrl'),
    driverClass: stringValue(config, 'driverClass'),
    countQuery: stringValue(config, 'countQuery'),
    pageQuery: stringValue(config, 'pageQuery'),
  }
}

function fileConfigFromJson(config: JsonRecord): FileConfigState {
  return {
    filePath: stringValue(config, 'filePath', stringValue(config, 'path')),
    format: stringValue(config, 'format', 'JSON_ARRAY'),
  }
}

function serializeRestConfig(config: RestConfigState): JsonRecord {
  return compactRecord({
    baseUrl: nonEmptyString(config.baseUrl),
    path: nonEmptyString(config.path),
    itemsPath: nonEmptyString(config.itemsPath),
    countPath: nonEmptyString(config.countPath),
    paginationMode: nonEmptyString(config.paginationMode),
    pageSize: nonEmptyString(config.pageSize) ? Number(config.pageSize) : null,
    startingPage: nonEmptyString(config.startingPage) ? Number(config.startingPage) : null,
    pageParam: nonEmptyString(config.pageParam),
    pageSizeParam: nonEmptyString(config.pageSizeParam),
    offsetParam: nonEmptyString(config.offsetParam),
    cursorParam: nonEmptyString(config.cursorParam),
    nextCursorPath: nonEmptyString(config.nextCursorPath),
    hasMorePath: nonEmptyString(config.hasMorePath),
    apiKeyHeader: nonEmptyString(config.apiKeyHeader),
  })
}

function serializeSqlConfig(config: SqlConfigState): JsonRecord {
  return compactRecord({
    jdbcUrl: nonEmptyString(config.jdbcUrl),
    driverClass: nonEmptyString(config.driverClass),
    countQuery: nonEmptyString(config.countQuery),
    pageQuery: nonEmptyString(config.pageQuery),
  })
}

function serializeFileConfig(config: FileConfigState): JsonRecord {
  return compactRecord({
    filePath: nonEmptyString(config.filePath),
    format: nonEmptyString(config.format),
  })
}

function emptyEntityMappingState(entityType: string): EntityMappingState {
  return {
    enabled: false,
    dataset: entityType,
    recordIdField: 'id',
    recordVersionField: '',
    entityFieldMappingsText: '',
    metadataFieldMappingsText: '',
    useDedicatedDatasetConfig: false,
    restConfig: defaultRestConfig(),
    sqlConfig: defaultSqlConfig(),
    fileConfig: defaultFileConfig(),
  }
}

function entityStatesFromConfig(
  entityTypes: string[],
  entityScope: string[],
  mappingConfig: JsonRecord,
  connectionConfig: JsonRecord,
  adapterType: AdapterType,
): Record<string, EntityMappingState> {
  const states: Record<string, EntityMappingState> = {}
  const mappingRoot = asRecordEntry(mappingConfig, 'entityMappings')
  const datasetRoot = asRecordEntry(connectionConfig, 'datasets')
  entityTypes.forEach((entityType) => {
    const mapping = asRecordEntry(mappingRoot, entityType)
    const datasetKey = stringValue(mapping, 'dataset', entityType)
    const datasetConfig = asRecordEntry(datasetRoot, datasetKey)
    states[entityType] = {
      enabled: entityScope.includes(entityType),
      dataset: datasetKey,
      recordIdField: stringValue(mapping, 'recordIdField', 'id'),
      recordVersionField: stringValue(mapping, 'recordVersionField'),
      entityFieldMappingsText: keyValueLines(mapping.entityFieldMappings),
      metadataFieldMappingsText: keyValueLines(mapping.metadataFieldMappings),
      useDedicatedDatasetConfig: Object.keys(datasetConfig).length > 0,
      restConfig: restConfigFromJson(datasetConfig),
      sqlConfig: sqlConfigFromJson(datasetConfig),
      fileConfig: fileConfigFromJson(datasetConfig),
    }
  })
  return states
}

function authStateFromSecretReferences(secretReferences: JsonRecord): AuthSecretState {
  const platformRefs = asRecordEntry(secretReferences, 'platformSecretRefs')
  const localAliases = asRecordEntry(secretReferences, 'localSecretAliases')
  return {
    apiKeyPlatformRef: stringValue(platformRefs, 'apiKey'),
    apiKeyLocalAlias: stringValue(localAliases, 'apiKey'),
    bearerPlatformRef: stringValue(platformRefs, 'token'),
    bearerLocalAlias: stringValue(localAliases, 'token'),
    usernamePlatformRef: stringValue(platformRefs, 'username'),
    usernameLocalAlias: stringValue(localAliases, 'username'),
    passwordPlatformRef: stringValue(platformRefs, 'password'),
    passwordLocalAlias: stringValue(localAliases, 'password'),
  }
}

function serializeSecretReferences(authMode: AuthMode, authSecrets: AuthSecretState): JsonRecord {
  const platformSecretRefs: JsonRecord = {}
  const localSecretAliases: JsonRecord = {}
  if (authMode === 'API_KEY') {
    if (authSecrets.apiKeyPlatformRef.trim()) {
      platformSecretRefs.apiKey = authSecrets.apiKeyPlatformRef.trim()
    }
    if (authSecrets.apiKeyLocalAlias.trim()) {
      localSecretAliases.apiKey = authSecrets.apiKeyLocalAlias.trim()
    }
  }
  if (authMode === 'BEARER') {
    if (authSecrets.bearerPlatformRef.trim()) {
      platformSecretRefs.token = authSecrets.bearerPlatformRef.trim()
    }
    if (authSecrets.bearerLocalAlias.trim()) {
      localSecretAliases.token = authSecrets.bearerLocalAlias.trim()
    }
  }
  if (authMode === 'BASIC') {
    if (authSecrets.usernamePlatformRef.trim()) {
      platformSecretRefs.username = authSecrets.usernamePlatformRef.trim()
    }
    if (authSecrets.passwordPlatformRef.trim()) {
      platformSecretRefs.password = authSecrets.passwordPlatformRef.trim()
    }
    if (authSecrets.usernameLocalAlias.trim()) {
      localSecretAliases.username = authSecrets.usernameLocalAlias.trim()
    }
    if (authSecrets.passwordLocalAlias.trim()) {
      localSecretAliases.password = authSecrets.passwordLocalAlias.trim()
    }
  }
  return compactRecord({
    platformSecretRefs,
    localSecretAliases,
  })
}

function syncChipColor(state: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch ((state ?? '').toUpperCase()) {
    case 'IN_SYNC':
      return 'success'
    case 'MANUALLY_CONFIRMED':
    case 'RUNNING':
    case 'SOURCE_EMPTY':
      return 'default'
    case 'BOOTSTRAP_REQUIRED':
    case 'OUT_OF_DATE':
    case 'REINDEX_DEFERRED':
      return 'warning'
    default:
      return 'default'
  }
}

function adapterConfigForType(
  adapterType: AdapterType,
  restConfig: RestConfigState,
  sqlConfig: SqlConfigState,
  fileConfig: FileConfigState,
): JsonRecord {
  if (adapterType === 'SQL') {
    return serializeSqlConfig(sqlConfig)
  }
  if (adapterType === 'FILE') {
    return serializeFileConfig(fileConfig)
  }
  return serializeRestConfig(restConfig)
}

function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return 'Not recorded'
  }
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

function buildRunActionLabel(mode: string): string {
  return mode === 'PLATFORM_MANAGED_AUTO' ? 'Reissue managed runner registration' : 'Rotate runner token'
}

function countValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function coverageChip(
  expected: number | null,
  live: number | null,
): { label: string; color: 'success' | 'warning' | 'default' } {
  if (expected != null && expected <= 0) {
    return { label: 'Source empty', color: 'default' }
  }
  if ((expected ?? 0) > 0 && (live ?? 0) <= 0) {
    return { label: 'Bootstrap required', color: 'warning' }
  }
  if ((live ?? 0) > 0) {
    return { label: 'Indexed', color: 'success' }
  }
  return { label: 'Unknown', color: 'default' }
}

function verificationChipColor(state: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  switch (state) {
    case 'PASSED':
      return 'success'
    case 'FAILED':
    case 'CANCELLED':
      return 'error'
    case 'RUNNING':
      return 'warning'
    default:
      return 'default'
  }
}

export function VectorizationPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [runnerNotice, setRunnerNotice] = useState<string | null>(null)
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null)
  const [selectedVerificationId, setSelectedVerificationId] = useState<string | null>(null)

  const [connectionName, setConnectionName] = useState('Primary source connection')
  const [adapterType, setAdapterType] = useState<AdapterType>('REST_API')
  const [authMode, setAuthMode] = useState<AuthMode>('API_KEY')
  const [globalRestConfig, setGlobalRestConfig] = useState<RestConfigState>(defaultRestConfig())
  const [globalSqlConfig, setGlobalSqlConfig] = useState<SqlConfigState>(defaultSqlConfig())
  const [globalFileConfig, setGlobalFileConfig] = useState<FileConfigState>(defaultFileConfig())
  const [authSecrets, setAuthSecrets] = useState<AuthSecretState>({
    apiKeyPlatformRef: '',
    apiKeyLocalAlias: '',
    bearerPlatformRef: '',
    bearerLocalAlias: '',
    usernamePlatformRef: '',
    usernameLocalAlias: '',
    passwordPlatformRef: '',
    passwordLocalAlias: '',
  })

  const [planName, setPlanName] = useState('Onboarding vectorization')
  const [runnerMode, setRunnerMode] = useState('PLATFORM_MANAGED_AUTO')
  const [runnerTokenValidityHours, setRunnerTokenValidityHours] = useState('72')
  const [batchSize, setBatchSize] = useState('100')
  const [pageSize, setPageSize] = useState('100')
  const [entityStates, setEntityStates] = useState<Record<string, EntityMappingState>>({})
  const [requestedRunEntities, setRequestedRunEntities] = useState<string[]>([])
  const [syncReasonInput, setSyncReasonInput] = useState('')

  const [advancedConnectionEnabled, setAdvancedConnectionEnabled] = useState(false)
  const [advancedSecretsEnabled, setAdvancedSecretsEnabled] = useState(false)
  const [advancedMappingEnabled, setAdvancedMappingEnabled] = useState(false)
  const [advancedExecutionEnabled, setAdvancedExecutionEnabled] = useState(false)
  const [advancedConnectionConfigText, setAdvancedConnectionConfigText] = useState('{}')
  const [advancedSecretReferencesText, setAdvancedSecretReferencesText] = useState('{}')
  const [advancedMappingConfigText, setAdvancedMappingConfigText] = useState('{\n  "entityMappings": {}\n}')
  const [advancedExecutionConfigText, setAdvancedExecutionConfigText] = useState('{\n  "batchSize": 100,\n  "pageSize": 100\n}')

  const canEdit = workspace?.access.canEdit ?? false
  const canOperate = workspace?.access.canOperate ?? false
  const canAdmin = workspace?.access.canAdmin ?? false

  const overviewQuery = useQuery({
    queryKey: ['vectorization-overview', selectedDeploymentId],
    queryFn: () => fetchVectorizationOverview(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const previewQuery = useQuery({
    queryKey: ['vectorization-preview', selectedDeploymentId],
    queryFn: () => fetchVectorizationPreview(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const availableEntities = useMemo(() => {
    const reindexOptions = asRecord(previewQuery.data?.reindexOptions)
    const fromPreview = stringArray(reindexOptions.availableEntities)
    const fromPlan = stringArray(overviewQuery.data?.plan?.activeRevision?.entityScope)
    return Array.from(new Set([...fromPreview, ...fromPlan])).sort()
  }, [overviewQuery.data?.plan?.activeRevision?.entityScope, previewQuery.data?.reindexOptions])

  const runDetailsQuery = useQuery({
    queryKey: ['vectorization-run-details', selectedDeploymentId, selectedRunId],
    queryFn: () => fetchVectorizationRunDetails(selectedDeploymentId, selectedRunId as string),
    enabled: selectedDeploymentId.length > 0 && selectedRunId != null,
  })

  const verificationRunsQuery = useQuery({
    queryKey: ['vectorization-verification-runs', selectedDeploymentId],
    queryFn: () => fetchVectorizationVerificationRuns(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const verificationDetailsQuery = useQuery({
    queryKey: ['vectorization-verification-details', selectedDeploymentId, selectedVerificationId],
    queryFn: () => fetchVectorizationVerificationRunDetails(selectedDeploymentId, selectedVerificationId as string),
    enabled: selectedDeploymentId.length > 0 && selectedVerificationId != null,
    refetchInterval: (query) => {
      const status = (query.state.data as { verificationRun?: { status?: string } } | undefined)?.verificationRun?.status
      return status === 'RUNNING' ? 3000 : false
    },
  })

  useEffect(() => {
    const overview = overviewQuery.data
    if (!overview) {
      return
    }

    const connectionConfig = asRecord(overview.sourceConnection?.connectionConfig)
    const secretReferences = asRecord(overview.sourceConnection?.secretReferences)
    const mappingConfig = asRecord(overview.plan?.activeRevision?.mappingConfig)
    const executionConfig = asRecord(overview.plan?.activeRevision?.executionConfig)
    const activeScope = stringArray(overview.plan?.activeRevision?.entityScope)
    const entities = Array.from(new Set([...availableEntities, ...activeScope])).sort()

    if (overview.sourceConnection) {
      setConnectionName(overview.sourceConnection.name)
      setAdapterType((overview.sourceConnection.adapterType || 'REST_API') as AdapterType)
      setAuthMode((overview.sourceConnection.authMode || 'API_KEY') as AuthMode)
      setGlobalRestConfig(restConfigFromJson(connectionConfig))
      setGlobalSqlConfig(sqlConfigFromJson(connectionConfig))
      setGlobalFileConfig(fileConfigFromJson(connectionConfig))
      setAuthSecrets(authStateFromSecretReferences(secretReferences))
      setAdvancedConnectionConfigText(prettyJson(overview.sourceConnection.connectionConfig))
      setAdvancedSecretReferencesText(prettyJson(overview.sourceConnection.secretReferences))
    }

    if (overview.plan) {
      setPlanName(overview.plan.name)
      setRunnerMode(overview.plan.runnerMode)
      setBatchSize(stringValue(executionConfig, 'batchSize', '100'))
      setPageSize(stringValue(executionConfig, 'pageSize', stringValue(executionConfig, 'batchSize', '100')))
      setAdvancedMappingConfigText(prettyJson(overview.plan.activeRevision?.mappingConfig ?? {}))
      setAdvancedExecutionConfigText(prettyJson(overview.plan.activeRevision?.executionConfig ?? {}))
    }

    setEntityStates(entityStatesFromConfig(entities, activeScope, mappingConfig, connectionConfig, (overview.sourceConnection?.adapterType || 'REST_API') as AdapterType))
  }, [availableEntities, overviewQuery.data])

  useEffect(() => {
    const recentRuns = overviewQuery.data?.recentRuns ?? []
    if (recentRuns.length === 0) {
      setSelectedRunId(null)
      return
    }
    if (!selectedRunId || !recentRuns.some((run) => run.id === selectedRunId)) {
      setSelectedRunId(recentRuns[0].id)
    }
  }, [overviewQuery.data?.recentRuns, selectedRunId])

  useEffect(() => {
    const recentVerifications = verificationRunsQuery.data ?? []
    if (recentVerifications.length === 0) {
      setSelectedVerificationId(null)
      return
    }
    if (!selectedVerificationId || !recentVerifications.some((verification) => verification.id === selectedVerificationId)) {
      setSelectedVerificationId(recentVerifications[0].id)
    }
  }, [verificationRunsQuery.data, selectedVerificationId])

  useEffect(() => {
    setRequestedRunEntities((current) => current.filter((entity) => availableEntities.includes(entity)))
  }, [availableEntities])

  const refreshQueries = () => {
    void queryClient.invalidateQueries({ queryKey: ['vectorization-overview', selectedDeploymentId] })
    void queryClient.invalidateQueries({ queryKey: ['vectorization-preview', selectedDeploymentId] })
    if (selectedRunId) {
      void queryClient.invalidateQueries({ queryKey: ['vectorization-run-details', selectedDeploymentId, selectedRunId] })
    }
    void queryClient.invalidateQueries({ queryKey: ['vectorization-verification-runs', selectedDeploymentId] })
    if (selectedVerificationId) {
      void queryClient.invalidateQueries({ queryKey: ['vectorization-verification-details', selectedDeploymentId, selectedVerificationId] })
    }
  }

  const connectionMutation = useMutation({
    mutationFn: () => {
      const connectionConfig = advancedConnectionEnabled
        ? parseJson(advancedConnectionConfigText, 'Advanced connection config')
        : buildConnectionConfig()
      const secretReferences = advancedSecretsEnabled
        ? parseJson(advancedSecretReferencesText, 'Advanced secret references')
        : serializeSecretReferences(authMode, authSecrets)
      return upsertVectorizationConnection(selectedDeploymentId, {
        name: connectionName,
        adapterType,
        authMode,
        connectionConfig,
        secretReferences,
      })
    },
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const planMutation = useMutation({
    mutationFn: () =>
      upsertVectorizationPlan(selectedDeploymentId, {
        name: planName,
        runnerMode,
        sourceConnectionId: overviewQuery.data?.sourceConnection?.id ?? null,
        entityScope: buildEntityScope(),
        mappingConfig: advancedMappingEnabled ? parseJson(advancedMappingConfigText, 'Advanced mapping config') : buildMappingConfig(),
        executionConfig: advancedExecutionEnabled ? parseJson(advancedExecutionConfigText, 'Advanced execution config') : buildExecutionConfig(),
      }),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const createRunMutation = useMutation({
    mutationFn: (reason: string) =>
      createVectorizationRun(selectedDeploymentId, {
        reason,
        entityTypes: buildRequestedRunScope(),
      }),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const syncStateMutation = useMutation({
    mutationFn: (payload: { action: string; reason?: string }) => updateVectorizationSyncState(selectedDeploymentId, payload),
    onSuccess: () => {
      setFormError(null)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const rotateTokenMutation = useMutation({
    mutationFn: () =>
      rotateVectorizationRunnerToken(selectedDeploymentId, {
        runnerMode,
        validityHours: runnerTokenValidityHours.trim() ? Number(runnerTokenValidityHours) : undefined,
      }),
    onSuccess: (summary) => {
      setFormError(null)
      setRunnerNotice(
        summary.runnerMode === 'PLATFORM_MANAGED_AUTO'
          ? 'Managed runner registration was reissued and the platform-managed secret was updated.'
          : `Runner token issued. Copy it now: ${summary.registrationToken}`
      )
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const runActionMutation = useMutation({
    mutationFn: async (payload: { runId: string; action: 'pause' | 'resume' | 'cancel' | 'retry' }) => {
      switch (payload.action) {
        case 'pause':
          return pauseVectorizationRun(selectedDeploymentId, payload.runId)
        case 'resume':
          return resumeVectorizationRun(selectedDeploymentId, payload.runId)
        case 'cancel':
          return cancelVectorizationRun(selectedDeploymentId, payload.runId)
        case 'retry':
          return retryVectorizationRun(selectedDeploymentId, payload.runId)
      }
    },
    onSuccess: (_, payload) => {
      setFormError(null)
      setSelectedRunId(payload.runId)
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  const verificationMutation = useMutation({
    mutationFn: (payload: { verificationType: string }) =>
      createVectorizationVerificationRun(selectedDeploymentId, {
        verificationType: payload.verificationType,
        entityTypes: buildRequestedRunScope(),
      }),
    onSuccess: (summary) => {
      setFormError(null)
      setSelectedVerificationId(summary.id)
      if (summary.linkedVectorizationRunId) {
        setSelectedRunId(summary.linkedVectorizationRunId)
      }
      refreshQueries()
    },
    onError: (error: Error) => setFormError(error.message),
  })

  function buildEntityScope(): string[] {
    return Object.entries(entityStates)
      .filter(([, entity]) => entity.enabled)
      .map(([entityType]) => entityType)
      .sort()
  }

  function buildRequestedRunScope(): string[] {
    return requestedRunEntities.length > 0 ? Array.from(new Set(requestedRunEntities)).sort() : buildEntityScope()
  }

  function toggleRequestedRunEntity(entityType: string) {
    setRequestedRunEntities((current) =>
      current.includes(entityType) ? current.filter((item) => item !== entityType) : [...current, entityType].sort(),
    )
  }

  function buildConnectionConfig(): JsonRecord {
    const root = adapterConfigForType(adapterType, globalRestConfig, globalSqlConfig, globalFileConfig)
    const datasets: JsonRecord = {}
    Object.values(entityStates).forEach((entity) => {
      if (!entity.useDedicatedDatasetConfig) {
        return
      }
      const datasetKey = entity.dataset.trim()
      if (!datasetKey) {
        return
      }
      datasets[datasetKey] = adapterConfigForType(adapterType, entity.restConfig, entity.sqlConfig, entity.fileConfig)
    })
    if (Object.keys(datasets).length > 0) {
      root.datasets = datasets
    }
    return root
  }

  function buildMappingConfig(): JsonRecord {
    const entityMappings: JsonRecord = {}
    Object.entries(entityStates).forEach(([entityType, entity]) => {
      entityMappings[entityType] = compactRecord({
        dataset: nonEmptyString(entity.dataset),
        recordIdField: nonEmptyString(entity.recordIdField),
        recordVersionField: nonEmptyString(entity.recordVersionField),
        entityFieldMappings: parseKeyValueLines(entity.entityFieldMappingsText),
        metadataFieldMappings: parseKeyValueLines(entity.metadataFieldMappingsText),
      })
    })
    return { entityMappings }
  }

  function buildExecutionConfig(): JsonRecord {
    return compactRecord({
      batchSize: nonEmptyString(batchSize) ? Number(batchSize) : null,
      pageSize: nonEmptyString(pageSize) ? Number(pageSize) : null,
    })
  }

  function updateEntityState(entityType: string, patch: Partial<EntityMappingState>) {
    setEntityStates((current) => ({
      ...current,
      [entityType]: {
        ...(current[entityType] ?? emptyEntityMappingState(entityType)),
        ...patch,
      },
    }))
  }

  function updateEntityRestConfig(entityType: string, patch: Partial<RestConfigState>) {
    setEntityStates((current) => ({
      ...current,
      [entityType]: {
        ...(current[entityType] ?? emptyEntityMappingState(entityType)),
        restConfig: {
          ...(current[entityType]?.restConfig ?? defaultRestConfig()),
          ...patch,
        },
      },
    }))
  }

  function updateEntitySqlConfig(entityType: string, patch: Partial<SqlConfigState>) {
    setEntityStates((current) => ({
      ...current,
      [entityType]: {
        ...(current[entityType] ?? emptyEntityMappingState(entityType)),
        sqlConfig: {
          ...(current[entityType]?.sqlConfig ?? defaultSqlConfig()),
          ...patch,
        },
      },
    }))
  }

  function updateEntityFileConfig(entityType: string, patch: Partial<FileConfigState>) {
    setEntityStates((current) => ({
      ...current,
      [entityType]: {
        ...(current[entityType] ?? emptyEntityMappingState(entityType)),
        fileConfig: {
          ...(current[entityType]?.fileConfig ?? defaultFileConfig()),
          ...patch,
        },
      },
    }))
  }

  function renderAdapterFields(
    currentAdapterType: AdapterType,
    currentRestConfig: RestConfigState,
    setRestConfig: (patch: Partial<RestConfigState>) => void,
    currentSqlConfig: SqlConfigState,
    setSqlConfig: (patch: Partial<SqlConfigState>) => void,
    currentFileConfig: FileConfigState,
    setFileConfig: (patch: Partial<FileConfigState>) => void,
  ) {
    if (currentAdapterType === 'SQL') {
      return (
        <Stack spacing={2}>
          <TextField label="JDBC URL" value={currentSqlConfig.jdbcUrl} onChange={(event) => setSqlConfig({ jdbcUrl: event.target.value })} />
          <TextField label="Driver class" value={currentSqlConfig.driverClass} onChange={(event) => setSqlConfig({ driverClass: event.target.value })} />
          <TextField
            label="Count query"
            multiline
            minRows={3}
            value={currentSqlConfig.countQuery}
            onChange={(event) => setSqlConfig({ countQuery: event.target.value })}
          />
          <TextField
            label="Page query"
            multiline
            minRows={4}
            helperText="Use :limit and :offset placeholders when needed."
            value={currentSqlConfig.pageQuery}
            onChange={(event) => setSqlConfig({ pageQuery: event.target.value })}
          />
        </Stack>
      )
    }

    if (currentAdapterType === 'FILE') {
      return (
        <Stack spacing={2}>
          <TextField label="File path" value={currentFileConfig.filePath} onChange={(event) => setFileConfig({ filePath: event.target.value })} />
          <TextField select label="Format" value={currentFileConfig.format} onChange={(event) => setFileConfig({ format: event.target.value })}>
            <MenuItem value="JSON_ARRAY">JSON array</MenuItem>
            <MenuItem value="JSON_LINES">JSON lines</MenuItem>
            <MenuItem value="CSV">CSV</MenuItem>
          </TextField>
        </Stack>
      )
    }

    return (
      <Stack spacing={2}>
        <TextField label="Base URL" value={currentRestConfig.baseUrl} onChange={(event) => setRestConfig({ baseUrl: event.target.value })} />
        <TextField label="Path" value={currentRestConfig.path} onChange={(event) => setRestConfig({ path: event.target.value })} />
        <TextField label="Items path" value={currentRestConfig.itemsPath} onChange={(event) => setRestConfig({ itemsPath: event.target.value })} />
        <TextField label="Count path" value={currentRestConfig.countPath} onChange={(event) => setRestConfig({ countPath: event.target.value })} />
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField
            select
            label="Pagination mode"
            value={currentRestConfig.paginationMode}
            onChange={(event) => setRestConfig({ paginationMode: event.target.value })}
            fullWidth
          >
            <MenuItem value="OFFSET_LIMIT">Offset/limit</MenuItem>
            <MenuItem value="PAGE_NUMBER">Page number</MenuItem>
            <MenuItem value="CURSOR">Cursor</MenuItem>
            <MenuItem value="NONE">None</MenuItem>
          </TextField>
          <TextField label="Page size" value={currentRestConfig.pageSize} onChange={(event) => setRestConfig({ pageSize: event.target.value })} fullWidth />
        </Stack>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField label="Page param" value={currentRestConfig.pageParam} onChange={(event) => setRestConfig({ pageParam: event.target.value })} fullWidth />
          <TextField label="Page size param" value={currentRestConfig.pageSizeParam} onChange={(event) => setRestConfig({ pageSizeParam: event.target.value })} fullWidth />
        </Stack>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField label="Offset param" value={currentRestConfig.offsetParam} onChange={(event) => setRestConfig({ offsetParam: event.target.value })} fullWidth />
          <TextField label="Cursor param" value={currentRestConfig.cursorParam} onChange={(event) => setRestConfig({ cursorParam: event.target.value })} fullWidth />
        </Stack>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField label="Next cursor path" value={currentRestConfig.nextCursorPath} onChange={(event) => setRestConfig({ nextCursorPath: event.target.value })} fullWidth />
          <TextField label="Has more path" value={currentRestConfig.hasMorePath} onChange={(event) => setRestConfig({ hasMorePath: event.target.value })} fullWidth />
        </Stack>
        <TextField label="API key header" value={currentRestConfig.apiKeyHeader} onChange={(event) => setRestConfig({ apiKeyHeader: event.target.value })} />
      </Stack>
    )
  }

  function renderAuthFields() {
    if (authMode === 'NONE') {
      return <Alert severity="info">No source authentication will be sent by the runner.</Alert>
    }

    if (authMode === 'API_KEY') {
      return (
        <Stack spacing={2}>
          <TextField
            label="Platform secret ref"
            helperText="Resolved for platform-managed runners."
            value={authSecrets.apiKeyPlatformRef}
            onChange={(event) => setAuthSecrets((current) => ({ ...current, apiKeyPlatformRef: event.target.value }))}
          />
          <TextField
            label="Local secret alias"
            helperText="Used by customer-managed remote runners."
            value={authSecrets.apiKeyLocalAlias}
            onChange={(event) => setAuthSecrets((current) => ({ ...current, apiKeyLocalAlias: event.target.value }))}
          />
        </Stack>
      )
    }

    if (authMode === 'BEARER') {
      return (
        <Stack spacing={2}>
          <TextField
            label="Platform secret ref"
            value={authSecrets.bearerPlatformRef}
            onChange={(event) => setAuthSecrets((current) => ({ ...current, bearerPlatformRef: event.target.value }))}
          />
          <TextField
            label="Local secret alias"
            value={authSecrets.bearerLocalAlias}
            onChange={(event) => setAuthSecrets((current) => ({ ...current, bearerLocalAlias: event.target.value }))}
          />
        </Stack>
      )
    }

    return (
      <Stack spacing={2}>
        <TextField
          label="Username platform secret ref"
          value={authSecrets.usernamePlatformRef}
          onChange={(event) => setAuthSecrets((current) => ({ ...current, usernamePlatformRef: event.target.value }))}
        />
        <TextField
          label="Password platform secret ref"
          value={authSecrets.passwordPlatformRef}
          onChange={(event) => setAuthSecrets((current) => ({ ...current, passwordPlatformRef: event.target.value }))}
        />
        <TextField
          label="Username local alias"
          value={authSecrets.usernameLocalAlias}
          onChange={(event) => setAuthSecrets((current) => ({ ...current, usernameLocalAlias: event.target.value }))}
        />
        <TextField
          label="Password local alias"
          value={authSecrets.passwordLocalAlias}
          onChange={(event) => setAuthSecrets((current) => ({ ...current, passwordLocalAlias: event.target.value }))}
        />
      </Stack>
    )
  }

  if (!selectedDeploymentId) {
    return <Alert severity="info">Select a deployment from the workspace header to configure vectorization.</Alert>
  }

  const selectedRunDetails = runDetailsQuery.data
  const selectedVerificationDetails = verificationDetailsQuery.data
  const sourceDiscovery = asRecord(previewQuery.data?.sourceDiscovery ?? overviewQuery.data?.sourceConnection?.discoverySummary ?? {})
  const discoveryCounts = asRecord(sourceDiscovery.countsByEntityType)
  const discoveryMethods = asRecord(sourceDiscovery.countMethodByEntityType)
  const syncReasonDetails = asRecord(overviewQuery.data?.plan?.syncReasonDetails)
  const expectedCounts = asRecord(syncReasonDetails.expectedCountsByEntityType)
  const liveCounts = asRecord(syncReasonDetails.liveCountsByEntityType)
  const coverageEntities = Array.from(
    new Set([...availableEntities, ...Object.keys(expectedCounts), ...Object.keys(liveCounts), ...Object.keys(discoveryCounts)]),
  ).sort()
  const runScopePreview = buildRequestedRunScope()

  return (
    <Stack spacing={3}>
      <Stack spacing={1}>
        <Typography variant="h4" sx={{ fontWeight: 700 }}>
          Vectorization
        </Typography>
        <Typography color="text.secondary">
          Configure source connectivity, entity mapping, runner mode, discovery, bootstrap, and reindex for this deployment through the runtime data-sync boundary.
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap">
          <Chip label={selectedDeploymentSummary?.name ?? selectedDeploymentId} />
          <Chip label={overviewQuery.data?.plan?.syncState ?? 'BOOTSTRAP_REQUIRED'} color={syncChipColor(overviewQuery.data?.plan?.syncState)} />
          <Chip label={overviewQuery.data?.plan?.runnerMode ?? 'PLATFORM_MANAGED_AUTO'} variant="outlined" />
          <Chip label={overviewQuery.data?.runner?.compatibilityStatus ?? 'UNREGISTERED'} variant="outlined" />
        </Stack>
      </Stack>

      {formError ? <Alert severity="error">{formError}</Alert> : null}
      {runnerNotice ? <Alert severity="success">{runnerNotice}</Alert> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} xl={6}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Source Connection</Typography>
                <TextField label="Connection name" value={connectionName} onChange={(event) => setConnectionName(event.target.value)} />
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField select label="Adapter type" value={adapterType} onChange={(event) => setAdapterType(event.target.value as AdapterType)} fullWidth>
                    <MenuItem value="REST_API">REST API</MenuItem>
                    <MenuItem value="SQL">SQL</MenuItem>
                    <MenuItem value="FILE">File</MenuItem>
                  </TextField>
                  <TextField select label="Auth mode" value={authMode} onChange={(event) => setAuthMode(event.target.value as AuthMode)} fullWidth>
                    <MenuItem value="NONE">None</MenuItem>
                    <MenuItem value="API_KEY">API key</MenuItem>
                    <MenuItem value="BEARER">Bearer token</MenuItem>
                    <MenuItem value="BASIC">Basic auth</MenuItem>
                  </TextField>
                </Stack>

                {renderAdapterFields(
                  adapterType,
                  globalRestConfig,
                  (patch) => setGlobalRestConfig((current) => ({ ...current, ...patch })),
                  globalSqlConfig,
                  (patch) => setGlobalSqlConfig((current) => ({ ...current, ...patch })),
                  globalFileConfig,
                  (patch) => setGlobalFileConfig((current) => ({ ...current, ...patch })),
                )}

                <Divider />
                <Typography variant="subtitle1">Source Auth</Typography>
                {renderAuthFields()}

                <Accordion expanded={advancedConnectionEnabled} onChange={(_, expanded) => setAdvancedConnectionEnabled(expanded)}>
                  <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                    <Typography variant="subtitle2">Advanced connection JSON</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <TextField
                      label="Connection config JSON"
                      multiline
                      minRows={10}
                      fullWidth
                      value={advancedConnectionConfigText}
                      onChange={(event) => setAdvancedConnectionConfigText(event.target.value)}
                    />
                  </AccordionDetails>
                </Accordion>

                <Accordion expanded={advancedSecretsEnabled} onChange={(_, expanded) => setAdvancedSecretsEnabled(expanded)}>
                  <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                    <Typography variant="subtitle2">Advanced secret references JSON</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <TextField
                      label="Secret references JSON"
                      multiline
                      minRows={8}
                      fullWidth
                      value={advancedSecretReferencesText}
                      onChange={(event) => setAdvancedSecretReferencesText(event.target.value)}
                    />
                  </AccordionDetails>
                </Accordion>

                <Button
                  variant="contained"
                  startIcon={<SaveRoundedIcon />}
                  disabled={!canEdit || connectionMutation.isPending}
                  onClick={() => connectionMutation.mutate()}
                >
                  Save source connection
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} xl={6}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Plan And Runner</Typography>
                <TextField label="Plan name" value={planName} onChange={(event) => setPlanName(event.target.value)} />
                <TextField select label="Runner mode" value={runnerMode} onChange={(event) => setRunnerMode(event.target.value)} fullWidth>
                  <MenuItem value="PLATFORM_MANAGED_AUTO">Platform managed auto</MenuItem>
                  <MenuItem value="PLATFORM_MANAGED_NONE">No managed runner</MenuItem>
                  <MenuItem value="CUSTOMER_MANAGED_REMOTE">Customer managed remote</MenuItem>
                </TextField>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                  <TextField
                    label="Token validity hours"
                    value={runnerTokenValidityHours}
                    onChange={(event) => setRunnerTokenValidityHours(event.target.value)}
                    fullWidth
                  />
                  <TextField label="Batch size" value={batchSize} onChange={(event) => setBatchSize(event.target.value)} fullWidth />
                  <TextField label="Page size" value={pageSize} onChange={(event) => setPageSize(event.target.value)} fullWidth />
                </Stack>
                <Alert severity="info">
                  Enabled entities below define the active plan scope. Discovery and reindex actions can also use that scope directly.
                </Alert>

                <Accordion expanded={advancedMappingEnabled} onChange={(_, expanded) => setAdvancedMappingEnabled(expanded)}>
                  <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                    <Typography variant="subtitle2">Advanced mapping JSON</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <TextField
                      label="Mapping config JSON"
                      multiline
                      minRows={10}
                      fullWidth
                      value={advancedMappingConfigText}
                      onChange={(event) => setAdvancedMappingConfigText(event.target.value)}
                    />
                  </AccordionDetails>
                </Accordion>

                <Accordion expanded={advancedExecutionEnabled} onChange={(_, expanded) => setAdvancedExecutionEnabled(expanded)}>
                  <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                    <Typography variant="subtitle2">Advanced execution JSON</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <TextField
                      label="Execution config JSON"
                      multiline
                      minRows={8}
                      fullWidth
                      value={advancedExecutionConfigText}
                      onChange={(event) => setAdvancedExecutionConfigText(event.target.value)}
                    />
                  </AccordionDetails>
                </Accordion>

                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="contained"
                    startIcon={<SaveRoundedIcon />}
                    disabled={!canEdit || planMutation.isPending}
                    onClick={() => planMutation.mutate()}
                  >
                    Save plan
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<TokenRoundedIcon />}
                    disabled={!canAdmin || rotateTokenMutation.isPending}
                    onClick={() => rotateTokenMutation.mutate()}
                  >
                    {buildRunActionLabel(runnerMode)}
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">Entity Scope And Mapping</Typography>
            {availableEntities.length === 0 ? (
              <Alert severity="warning">
                No configured deployment entities were detected. Configure entity spaces on the Knowledge page first.
              </Alert>
            ) : (
              availableEntities.map((entityType) => {
                const state = entityStates[entityType] ?? emptyEntityMappingState(entityType)
                return (
                  <Accordion key={entityType} defaultExpanded={state.enabled}>
                    <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip label={entityType} />
                        {state.enabled ? <Chip label="Included in plan" color="success" size="small" /> : <Chip label="Excluded" size="small" variant="outlined" />}
                        {state.useDedicatedDatasetConfig ? <Chip label={`Dataset ${state.dataset || entityType}`} size="small" variant="outlined" /> : null}
                      </Stack>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={2}>
                        <FormControlLabel
                          control={<Switch checked={state.enabled} onChange={(event) => updateEntityState(entityType, { enabled: event.target.checked })} />}
                          label="Include this entity in the active plan scope"
                        />
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                          <TextField
                            label="Dataset key"
                            value={state.dataset}
                            onChange={(event) => updateEntityState(entityType, { dataset: event.target.value })}
                            fullWidth
                          />
                          <TextField
                            label="Record id field"
                            value={state.recordIdField}
                            onChange={(event) => updateEntityState(entityType, { recordIdField: event.target.value })}
                            fullWidth
                          />
                          <TextField
                            label="Record version field"
                            value={state.recordVersionField}
                            onChange={(event) => updateEntityState(entityType, { recordVersionField: event.target.value })}
                            fullWidth
                          />
                        </Stack>
                        <TextField
                          label="Entity field mappings"
                          helperText="One mapping per line in targetField=source.path form."
                          multiline
                          minRows={5}
                          value={state.entityFieldMappingsText}
                          onChange={(event) => updateEntityState(entityType, { entityFieldMappingsText: event.target.value })}
                        />
                        <TextField
                          label="Metadata field mappings"
                          helperText="One mapping per line in targetField=source.path form."
                          multiline
                          minRows={4}
                          value={state.metadataFieldMappingsText}
                          onChange={(event) => updateEntityState(entityType, { metadataFieldMappingsText: event.target.value })}
                        />
                        <FormControlLabel
                          control={
                            <Switch
                              checked={state.useDedicatedDatasetConfig}
                              onChange={(event) => updateEntityState(entityType, { useDedicatedDatasetConfig: event.target.checked })}
                            />
                          }
                          label="Use entity-specific source settings"
                        />
                        {state.useDedicatedDatasetConfig
                          ? renderAdapterFields(
                              adapterType,
                              state.restConfig,
                              (patch) => updateEntityRestConfig(entityType, patch),
                              state.sqlConfig,
                              (patch) => updateEntitySqlConfig(entityType, patch),
                              state.fileConfig,
                              (patch) => updateEntityFileConfig(entityType, patch),
                            )
                          : null}
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                )
              })
            )}
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Verification</Typography>
                <Typography variant="body2" color="text.secondary">
                  Run operator-triggered vectorization verifications to prove control-plane readiness, runner health, and discovery execution. Active sample vectorization and tenant-isolation proof will layer onto this same verification surface.
                </Typography>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="contained"
                    startIcon={<FactCheckRoundedIcon />}
                    disabled={!canOperate || verificationMutation.isPending}
                    onClick={() => verificationMutation.mutate({ verificationType: 'CONTROL_PLANE_READINESS' })}
                  >
                    Control-plane readiness
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<TokenRoundedIcon />}
                    disabled={!canOperate || verificationMutation.isPending}
                    onClick={() => verificationMutation.mutate({ verificationType: 'RUNNER_PROVISIONING_SMOKE' })}
                  >
                    Runner provisioning smoke
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<SearchRoundedIcon />}
                    disabled={!canOperate || verificationMutation.isPending}
                    onClick={() => verificationMutation.mutate({ verificationType: 'SOURCE_DISCOVERY_SMOKE' })}
                  >
                    Discovery smoke
                  </Button>
                </Stack>

                <Grid container spacing={3}>
                  <Grid item xs={12} lg={5}>
                    <Stack spacing={1.5}>
                      <Typography variant="subtitle1">Recent Verification Runs</Typography>
                      {verificationRunsQuery.data?.length ? (
                        verificationRunsQuery.data.map((verificationRun) => (
                          <Box
                            key={verificationRun.id}
                            sx={{
                              p: 2,
                              borderRadius: 2,
                              border: '1px solid',
                              borderColor: selectedVerificationId === verificationRun.id ? 'primary.main' : 'divider',
                              cursor: 'pointer',
                            }}
                            onClick={() => setSelectedVerificationId(verificationRun.id)}
                          >
                            <Stack spacing={1.5}>
                              <Stack direction="row" spacing={1} flexWrap="wrap">
                                <Chip label={verificationRun.verificationType} />
                                <Chip label={verificationRun.executionMode} variant="outlined" />
                                <Chip label={verificationRun.status} color={verificationChipColor(verificationRun.status)} variant={verificationRun.status === 'PASSED' ? 'filled' : 'outlined'} />
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {verificationRun.entityScope.length > 0 ? `Scope: ${verificationRun.entityScope.join(', ')}` : 'Uses plan scope or is read-only'}
                              </Typography>
                              {verificationRun.linkedVectorizationRunId ? (
                                <Typography variant="caption" color="text.secondary">
                                  Linked vectorization run: {verificationRun.linkedVectorizationRunId}
                                </Typography>
                              ) : null}
                            </Stack>
                          </Box>
                        ))
                      ) : (
                        <Alert severity="info">No vectorization verifications have been run for this deployment yet.</Alert>
                      )}
                    </Stack>
                  </Grid>

                  <Grid item xs={12} lg={7}>
                    <Stack spacing={2}>
                      <Typography variant="subtitle1">Verification Details</Typography>
                      {!selectedVerificationId ? (
                        <Alert severity="info">Select a verification run to inspect its evidence and linked execution state.</Alert>
                      ) : verificationDetailsQuery.isLoading ? (
                        <Alert severity="info">Loading verification details…</Alert>
                      ) : selectedVerificationDetails ? (
                        <>
                          <Stack direction="row" spacing={1} flexWrap="wrap">
                            <Chip label={selectedVerificationDetails.verificationRun.verificationType} />
                            <Chip label={selectedVerificationDetails.verificationRun.executionMode} variant="outlined" />
                            <Chip
                              label={selectedVerificationDetails.verificationRun.status}
                              color={verificationChipColor(selectedVerificationDetails.verificationRun.status)}
                              variant={selectedVerificationDetails.verificationRun.status === 'PASSED' ? 'filled' : 'outlined'}
                            />
                            {selectedVerificationDetails.linkedVectorizationRun ? (
                              <Chip label={selectedVerificationDetails.linkedVectorizationRun.id} variant="outlined" />
                            ) : null}
                          </Stack>
                          <TextField
                            label="Verification summary"
                            multiline
                            minRows={5}
                            value={prettyJson(selectedVerificationDetails.verificationRun.summary)}
                            InputProps={{ readOnly: true }}
                          />
                          {selectedVerificationDetails.linkedVectorizationRun ? (
                            <Stack spacing={1}>
                              <Typography variant="subtitle2">Linked vectorization run</Typography>
                              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', md: 'center' }}>
                                <Stack direction="row" spacing={1} flexWrap="wrap">
                                  <Chip label={selectedVerificationDetails.linkedVectorizationRun.reason} />
                                  <Chip label={selectedVerificationDetails.linkedVectorizationRun.status} variant="outlined" />
                                  <Chip label={selectedVerificationDetails.linkedVectorizationRun.runnerMode} variant="outlined" />
                                </Stack>
                                <Button
                                  size="small"
                                  onClick={() => setSelectedRunId(selectedVerificationDetails.linkedVectorizationRun?.id ?? null)}
                                >
                                  Open run diagnostics
                                </Button>
                              </Stack>
                            </Stack>
                          ) : null}
                          <Typography variant="subtitle2">Verification steps</Typography>
                          {selectedVerificationDetails.steps.length ? (
                            selectedVerificationDetails.steps.map((step) => (
                              <Accordion key={step.id}>
                                <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                                  <Stack direction="row" spacing={1} flexWrap="wrap">
                                    <Chip label={step.stepName} size="small" />
                                    <Chip
                                      label={step.status}
                                      size="small"
                                      color={verificationChipColor(step.status)}
                                      variant={step.status === 'PASSED' ? 'filled' : 'outlined'}
                                    />
                                  </Stack>
                                </AccordionSummary>
                                <AccordionDetails>
                                  <Stack spacing={1.5}>
                                    <TextField label="Details" multiline minRows={4} value={prettyJson(step.details)} InputProps={{ readOnly: true }} />
                                    <Typography variant="caption" color="text.secondary">
                                      Updated {formatTimestamp(step.updatedAt)}
                                    </Typography>
                                  </Stack>
                                </AccordionDetails>
                              </Accordion>
                            ))
                          ) : (
                            <Alert severity="info">No step evidence has been stored for this verification yet.</Alert>
                          )}
                        </>
                      ) : (
                        <Alert severity="warning">Verification details are not available.</Alert>
                      )}
                    </Stack>
                  </Grid>
                </Grid>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Runner Status</Typography>
                {overviewQuery.data?.runner ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={overviewQuery.data.runner.runnerMode} variant="outlined" />
                      <Chip label={overviewQuery.data.runner.registrationStatus} />
                      <Chip label={overviewQuery.data.runner.compatibilityStatus} color={overviewQuery.data.runner.compatibilityStatus === 'CURRENT' ? 'success' : 'warning'} />
                    </Stack>
                    <TextField label="Token hint" value={overviewQuery.data.runner.tokenHint ?? 'Not available'} InputProps={{ readOnly: true }} />
                    <TextField label="Token expires" value={formatTimestamp(overviewQuery.data.runner.tokenExpiresAt)} InputProps={{ readOnly: true }} />
                    <TextField label="Runner instance" value={overviewQuery.data.runner.runnerInstanceId ?? 'Not connected'} InputProps={{ readOnly: true }} />
                    <TextField label="Product version" value={overviewQuery.data.runner.productVersion ?? 'Unknown'} InputProps={{ readOnly: true }} />
                    <TextField label="Compatibility version" value={overviewQuery.data.runner.compatibilityVersion ?? 'Unknown'} InputProps={{ readOnly: true }} />
                    <TextField label="Last connected" value={formatTimestamp(overviewQuery.data.runner.lastConnectedAt)} InputProps={{ readOnly: true }} />
                    <TextField label="Last heartbeat" value={formatTimestamp(overviewQuery.data.runner.lastSessionHeartbeatAt)} InputProps={{ readOnly: true }} />
                    <TextField label="Session expires" value={formatTimestamp(overviewQuery.data.runner.lastSessionExpiresAt)} InputProps={{ readOnly: true }} />
                  </>
                ) : (
                  <Alert severity="info">No runner registration exists yet for this deployment.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={7}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Sync State, Discovery, And Coverage</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {(overviewQuery.data?.plan?.syncReasonCodes ?? []).map((reasonCode) => (
                    <Chip key={reasonCode} label={reasonCode} variant="outlined" />
                  ))}
                </Stack>
                <Stack spacing={1}>
                  <Typography variant="subtitle2">Run entity scope</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Select entities for the next discovery/bootstrap/reindex run, or leave empty to use the active plan scope.
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip
                      label="Use plan scope"
                      clickable
                      color={requestedRunEntities.length === 0 ? 'primary' : 'default'}
                      variant={requestedRunEntities.length === 0 ? 'filled' : 'outlined'}
                      onClick={() => setRequestedRunEntities([])}
                    />
                    {availableEntities.map((entityType) => {
                      const selected = requestedRunEntities.includes(entityType)
                      return (
                        <Chip
                          key={entityType}
                          label={entityType}
                          clickable
                          color={selected ? 'primary' : 'default'}
                          variant={selected ? 'filled' : 'outlined'}
                          onClick={() => toggleRequestedRunEntity(entityType)}
                        />
                      )
                    })}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    Next run scope: {runScopePreview.length > 0 ? runScopePreview.join(', ') : 'No entities selected'}
                  </Typography>
                </Stack>
                <TextField
                  label="Reason for sync override"
                  value={syncReasonInput}
                  onChange={(event) => setSyncReasonInput(event.target.value)}
                  helperText="Used for defer and manual confirm audit entries."
                />
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="contained"
                    startIcon={<SearchRoundedIcon />}
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('DISCOVERY')}
                  >
                    Test and discover source
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<AutoFixHighRoundedIcon />}
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('BOOTSTRAP')}
                  >
                    Queue bootstrap
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<AutorenewRoundedIcon />}
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('REINDEX')}
                  >
                    Queue reindex
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={!canOperate || createRunMutation.isPending}
                    onClick={() => createRunMutation.mutate('REFRESH')}
                  >
                    Queue refresh
                  </Button>
                </Stack>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Button
                    variant="text"
                    startIcon={<WarningAmberRoundedIcon />}
                    disabled={!canOperate || syncStateMutation.isPending}
                    onClick={() => syncStateMutation.mutate({ action: 'DEFER_REINDEX', reason: syncReasonInput.trim() || 'Deferred by operator.' })}
                  >
                    Mark deferred
                  </Button>
                  <Button
                    variant="text"
                    startIcon={<PolicyRoundedIcon />}
                    disabled={!canOperate || syncStateMutation.isPending}
                    onClick={() => syncStateMutation.mutate({ action: 'MANUAL_CONFIRM', reason: syncReasonInput.trim() || 'External sync confirmed by operator.' })}
                  >
                    Manual confirm
                  </Button>
                  <Button
                    variant="text"
                    disabled={!canOperate || syncStateMutation.isPending}
                    onClick={() => syncStateMutation.mutate({ action: 'CLEAR_OVERRIDE' })}
                  >
                    Clear override
                  </Button>
                </Stack>
                <Stack spacing={1}>
                  <Typography variant="subtitle2">Discovery counts</Typography>
                  {coverageEntities.length > 0 ? (
                    coverageEntities.map((entityType) => {
                      const discovered = countValue(discoveryCounts[entityType])
                      const method = stringValue(discoveryMethods, entityType, 'UNKNOWN')
                      return (
                        <Box key={`discovery-${entityType}`} sx={{ p: 1.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}>
                          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between">
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip label={entityType} size="small" />
                              <Chip label={method} size="small" variant="outlined" />
                            </Stack>
                            <Typography variant="body2" color="text.secondary">
                              Expected source rows: {discovered ?? 'Unknown'}
                            </Typography>
                          </Stack>
                        </Box>
                      )
                    })
                  ) : (
                    <Alert severity="info">No discovery results have been reported yet.</Alert>
                  )}
                </Stack>

                <Stack spacing={1}>
                  <Typography variant="subtitle2">Indexed coverage</Typography>
                  {coverageEntities.length > 0 ? (
                    coverageEntities.map((entityType) => {
                      const expected = countValue(expectedCounts[entityType] ?? discoveryCounts[entityType])
                      const live = countValue(liveCounts[entityType])
                      const status = coverageChip(expected, live)
                      return (
                        <Box key={`coverage-${entityType}`} sx={{ p: 1.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}>
                          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between">
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip label={entityType} size="small" />
                              <Chip label={status.label} size="small" color={status.color} variant={status.color === 'default' ? 'outlined' : 'filled'} />
                            </Stack>
                            <Typography variant="body2" color="text.secondary">
                              Live indexed rows: {live ?? 'Unknown'}
                            </Typography>
                          </Stack>
                          <Typography variant="caption" color="text.secondary">
                            Expected source rows: {expected ?? 'Unknown'}
                          </Typography>
                        </Box>
                      )
                    })
                  ) : (
                    <Alert severity="info">No entity coverage data is available yet.</Alert>
                  )}
                </Stack>

                <Accordion>
                  <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                    <Typography variant="subtitle2">Raw discovery and coverage JSON</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Stack spacing={2}>
                      <TextField
                        label="Source discovery summary"
                        multiline
                        minRows={8}
                        value={prettyJson(sourceDiscovery)}
                        InputProps={{ readOnly: true }}
                      />
                      <TextField
                        label="Coverage and reindex options"
                        multiline
                        minRows={8}
                        value={prettyJson({
                          syncReasonDetails: overviewQuery.data?.plan?.syncReasonDetails ?? {},
                          reindexOptions: previewQuery.data?.reindexOptions ?? {},
                        })}
                        InputProps={{ readOnly: true }}
                      />
                    </Stack>
                  </AccordionDetails>
                </Accordion>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={5}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Recent Runs</Typography>
                {overviewQuery.data?.recentRuns.length ? (
                  overviewQuery.data.recentRuns.map((run) => (
                    <Box key={run.id} sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: selectedRunId === run.id ? 'primary.main' : 'divider', cursor: 'pointer' }} onClick={() => setSelectedRunId(run.id)}>
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip label={run.reason} />
                          <Chip label={run.status} variant="outlined" />
                          <Chip label={run.requestedStatus} variant="outlined" />
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {run.entityScope.length > 0 ? `Entities: ${run.entityScope.join(', ')}` : 'Uses plan scope'}
                        </Typography>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                          <Button
                            size="small"
                            startIcon={<PauseCircleOutlineRoundedIcon />}
                            disabled={!canOperate || runActionMutation.isPending}
                            onClick={(event) => {
                              event.stopPropagation()
                              runActionMutation.mutate({ runId: run.id, action: 'pause' })
                            }}
                          >
                            Pause
                          </Button>
                          <Button
                            size="small"
                            startIcon={<PlayCircleOutlineRoundedIcon />}
                            disabled={!canOperate || runActionMutation.isPending}
                            onClick={(event) => {
                              event.stopPropagation()
                              runActionMutation.mutate({ runId: run.id, action: 'resume' })
                            }}
                          >
                            Resume
                          </Button>
                          <Button
                            size="small"
                            disabled={!canOperate || runActionMutation.isPending}
                            onClick={(event) => {
                              event.stopPropagation()
                              runActionMutation.mutate({ runId: run.id, action: 'retry' })
                            }}
                          >
                            Retry
                          </Button>
                          <Button
                            size="small"
                            color="warning"
                            disabled={!canOperate || runActionMutation.isPending}
                            onClick={(event) => {
                              event.stopPropagation()
                              runActionMutation.mutate({ runId: run.id, action: 'cancel' })
                            }}
                          >
                            Cancel
                          </Button>
                        </Stack>
                      </Stack>
                    </Box>
                  ))
                ) : (
                  <Alert severity="info">No vectorization runs have been queued for this deployment yet.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={7}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Run Diagnostics</Typography>
                {!selectedRunId ? (
                  <Alert severity="info">Select a run to inspect checkpoints and failure buckets.</Alert>
                ) : runDetailsQuery.isLoading ? (
                  <Alert severity="info">Loading run details…</Alert>
                ) : selectedRunDetails ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={selectedRunDetails.run.reason} />
                      <Chip label={selectedRunDetails.run.status} variant="outlined" />
                      <Chip label={selectedRunDetails.run.requestedStatus} variant="outlined" />
                      {selectedRunDetails.run.runnerInstanceId ? <Chip label={selectedRunDetails.run.runnerInstanceId} variant="outlined" /> : null}
                    </Stack>
                    <TextField label="Progress summary" multiline minRows={5} value={prettyJson(selectedRunDetails.run.progressSummary)} InputProps={{ readOnly: true }} />
                    <TextField label="Checkpoint summary" multiline minRows={4} value={prettyJson(selectedRunDetails.run.checkpointSummary)} InputProps={{ readOnly: true }} />
                    <TextField label="Error summary" multiline minRows={4} value={prettyJson(selectedRunDetails.run.errorSummary)} InputProps={{ readOnly: true }} />

                    <Divider />
                    <Typography variant="subtitle1">Checkpoints</Typography>
                    {selectedRunDetails.checkpoints.length ? (
                      selectedRunDetails.checkpoints.map((checkpoint) => (
                        <Accordion key={checkpoint.id}>
                          <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip label={checkpoint.checkpointType} size="small" />
                              {checkpoint.entityType ? <Chip label={checkpoint.entityType} size="small" variant="outlined" /> : null}
                              {checkpoint.checkpointValue ? <Chip label={checkpoint.checkpointValue} size="small" variant="outlined" /> : null}
                            </Stack>
                          </AccordionSummary>
                          <AccordionDetails>
                            <Stack spacing={1.5}>
                              <TextField label="Progress" multiline minRows={4} value={prettyJson(checkpoint.progress)} InputProps={{ readOnly: true }} />
                              <TextField label="Details" multiline minRows={4} value={prettyJson(checkpoint.details)} InputProps={{ readOnly: true }} />
                              <Typography variant="caption" color="text.secondary">
                                Updated {formatTimestamp(checkpoint.updatedAt)}
                              </Typography>
                            </Stack>
                          </AccordionDetails>
                        </Accordion>
                      ))
                    ) : (
                      <Alert severity="info">No checkpoints have been recorded for this run.</Alert>
                    )}

                    <Divider />
                    <Typography variant="subtitle1">Failure Buckets</Typography>
                    {selectedRunDetails.failureBuckets.length ? (
                      selectedRunDetails.failureBuckets.map((bucket) => (
                        <Accordion key={bucket.id}>
                          <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              {bucket.entityType ? <Chip label={bucket.entityType} size="small" /> : null}
                              <Chip label={bucket.errorCode} size="small" variant="outlined" />
                              <Chip label={`Occurrences ${bucket.occurrences}`} size="small" variant="outlined" />
                            </Stack>
                          </AccordionSummary>
                          <AccordionDetails>
                            <Stack spacing={1.5}>
                              <Typography variant="body2">{bucket.summary}</Typography>
                              <TextField label="Sample" multiline minRows={4} value={prettyJson(bucket.sample)} InputProps={{ readOnly: true }} />
                              <Typography variant="caption" color="text.secondary">
                                Updated {formatTimestamp(bucket.updatedAt)}
                              </Typography>
                            </Stack>
                          </AccordionDetails>
                        </Accordion>
                      ))
                    ) : (
                      <Alert severity="success">No failure buckets were recorded for this run.</Alert>
                    )}
                  </>
                ) : (
                  <Alert severity="warning">Run details are not available.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  )
}
