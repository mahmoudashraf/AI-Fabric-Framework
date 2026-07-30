import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import PreviewRoundedIcon from '@mui/icons-material/PreviewRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import UpgradeRoundedIcon from '@mui/icons-material/UpgradeRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  MenuItem,
  OutlinedInput,
  Select,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  applyDeploymentEntityConfigMigration,
  fetchDeploymentDraft,
  previewDeploymentEntityConfigMigration,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

const ENTITY_CONFIG_CONTRACT = 'AI_ENTITY_CONFIG_V0_4'
const MAX_PROJECTION_CHARACTERS = 8_000

const ANALYSIS_OPERATIONS = ['CREATE', 'UPDATE', 'DELETE'] as const
const SEARCH_DESTINATIONS = ['SEMANTIC_SEARCH', 'RAG_CONTEXT'] as const
const SEARCH_PREPROCESSING = ['NONE', 'NORMALIZE', 'CLEAN', 'SANITIZE'] as const
const METADATA_DATA_TYPES = [
  'AUTO',
  'STRING',
  'NUMBER',
  'BOOLEAN',
  'DATE',
  'ENUM',
  'ID',
  'JSON',
] as const
const METADATA_DESTINATIONS = [
  'VECTOR_METADATA',
  'LLM_CONTEXT',
  'API_RESPONSE',
] as const

type AnalysisOperation = (typeof ANALYSIS_OPERATIONS)[number]
type SearchDestination = (typeof SEARCH_DESTINATIONS)[number]
type SearchPreprocessing = (typeof SEARCH_PREPROCESSING)[number]
type MetadataDataType = (typeof METADATA_DATA_TYPES)[number]
type MetadataDestination = (typeof METADATA_DESTINATIONS)[number]

type SearchableFieldEditor = {
  name: string
  destinations: SearchDestination[]
  preprocessing: SearchPreprocessing
  maxLength: string
  priority: string
  required: boolean
}

type MetadataFieldEditor = {
  name: string
  dataType: MetadataDataType
  format: string
  description: string
  destinations: MetadataDestination[]
  priority: string
  required: boolean
  sanitizePii: boolean
}

type EntityProvenance = {
  marketplaceManaged?: boolean
  marketplacePluginId?: string
  marketplaceInstallId?: string
  marketplacePluginVersion?: string
}

type EntityEditorState = {
  entityType: string
  indexingEnabled: boolean
  maxCharacters: string
  analysisEnabled: boolean
  analysisAfter: AnalysisOperation[]
  searchableFields: SearchableFieldEditor[]
  metadataFields: MetadataFieldEditor[]
  provenance: EntityProvenance
}

type MultiValueSelectProps<T extends string> = {
  label: string
  value: T[]
  options: readonly T[]
  onChange: (value: T[]) => void
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {}
}

function readText(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback
}

function readNumberText(value: unknown, fallback: number): string {
  return typeof value === 'number' && Number.isFinite(value)
    ? String(value)
    : String(fallback)
}

function readBoolean(value: unknown, fallback = false): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function readEnum<T extends string>(
  value: unknown,
  options: readonly T[],
  fallback: T,
): T {
  if (typeof value !== 'string') {
    return fallback
  }
  const normalized = value.trim().toUpperCase()
  return options.find((option) => option === normalized) ?? fallback
}

function readEnumList<T extends string>(
  value: unknown,
  options: readonly T[],
): T[] {
  if (!Array.isArray(value)) {
    return []
  }
  const selected = new Set(
    value
      .filter((candidate): candidate is string => typeof candidate === 'string')
      .map((candidate) => candidate.trim().toUpperCase()),
  )
  return options.filter((option) => selected.has(option))
}

function readProvenance(entity: Record<string, unknown>): EntityProvenance {
  const provenance: EntityProvenance = {}
  if (typeof entity.marketplaceManaged === 'boolean') {
    provenance.marketplaceManaged = entity.marketplaceManaged
  }
  if (typeof entity.marketplacePluginId === 'string') {
    provenance.marketplacePluginId = entity.marketplacePluginId
  }
  if (typeof entity.marketplaceInstallId === 'string') {
    provenance.marketplaceInstallId = entity.marketplaceInstallId
  }
  if (typeof entity.marketplacePluginVersion === 'string') {
    provenance.marketplacePluginVersion = entity.marketplacePluginVersion
  }
  return provenance
}

function newSearchableField(): SearchableFieldEditor {
  return {
    name: '',
    destinations: ['SEMANTIC_SEARCH', 'RAG_CONTEXT'],
    preprocessing: 'NORMALIZE',
    maxLength: '-1',
    priority: '50',
    required: false,
  }
}

function newMetadataField(): MetadataFieldEditor {
  return {
    name: '',
    dataType: 'AUTO',
    format: '',
    description: '',
    destinations: ['VECTOR_METADATA'],
    priority: '50',
    required: false,
    sanitizePii: false,
  }
}

function newEntity(entityType: string): EntityEditorState {
  return {
    entityType,
    indexingEnabled: true,
    maxCharacters: String(MAX_PROJECTION_CHARACTERS),
    analysisEnabled: false,
    analysisAfter: [],
    searchableFields: [newSearchableField()],
    metadataFields: [],
    provenance: {},
  }
}

function buildSearchableFields(value: unknown): SearchableFieldEditor[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .filter(isRecord)
    .map((field) => ({
      name: readText(field.name),
      destinations: readEnumList(field.destinations, SEARCH_DESTINATIONS),
      preprocessing: readEnum(
        field.preprocessing,
        SEARCH_PREPROCESSING,
        'NORMALIZE',
      ),
      maxLength: readNumberText(field['max-length'], -1),
      priority: readNumberText(field.priority, 50),
      required: readBoolean(field.required),
    }))
}

function buildMetadataFields(value: unknown): MetadataFieldEditor[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .filter(isRecord)
    .map((field) => ({
      name: readText(field.name),
      dataType: readEnum(field['data-type'], METADATA_DATA_TYPES, 'AUTO'),
      format: readText(field.format),
      description: readText(field.description),
      destinations: readEnumList(
        field.destinations,
        METADATA_DESTINATIONS,
      ),
      priority: readNumberText(field.priority, 50),
      required: readBoolean(field.required),
      sanitizePii: readBoolean(field['sanitize-pii']),
    }))
}

function buildEntityEditors(
  config: unknown,
): Record<string, EntityEditorState> {
  const root = asRecord(config)
  const entities = asRecord(root['ai-entities'])

  return Object.entries(entities).reduce<Record<string, EntityEditorState>>(
    (accumulator, [entityType, value]) => {
      const entity = asRecord(value)
      const indexing = asRecord(entity.indexing)
      const analysis = asRecord(entity.analysis)
      accumulator[entityType] = {
        entityType,
        indexingEnabled: readBoolean(indexing.enabled),
        maxCharacters: readNumberText(
          indexing['max-characters'],
          MAX_PROJECTION_CHARACTERS,
        ),
        analysisEnabled: readBoolean(analysis.enabled),
        analysisAfter: readEnumList(
          analysis.after,
          ANALYSIS_OPERATIONS,
        ),
        searchableFields: buildSearchableFields(
          entity['searchable-fields'],
        ),
        metadataFields: buildMetadataFields(entity['metadata-fields']),
        provenance: readProvenance(entity),
      }
      return accumulator
    },
    {},
  )
}

function readVectorDimensions(config: unknown): string {
  const root = asRecord(config)
  const aiConfig = asRecord(root['ai-config'])
  return readNumberText(aiConfig['vector-dimensions'], 512)
}

function parseInteger(
  label: string,
  value: string,
  predicate: (candidate: number) => boolean,
  expected: string,
): number {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || !predicate(parsed)) {
    throw new Error(`${label} ${expected}.`)
  }
  return parsed
}

function assertUniqueNames(
  label: string,
  names: string[],
): void {
  const seen = new Set<string>()
  for (const name of names) {
    const normalized = name.trim().toLowerCase()
    if (normalized.length === 0) {
      throw new Error(`${label} names are required.`)
    }
    if (seen.has(normalized)) {
      throw new Error(`${label} names must be unique.`)
    }
    seen.add(normalized)
  }
}

function normalizeEntityConfig(
  vectorDimensions: string,
  entityEditors: Record<string, EntityEditorState>,
): Record<string, unknown> {
  const dimensions = parseInteger(
    'Vector dimensions',
    vectorDimensions,
    (candidate) => candidate > 0,
    'must be a positive integer',
  )
  const normalizedEntities: Record<string, unknown> = {}
  const entityNames = Object.values(entityEditors).map((entity) =>
    entity.entityType.trim(),
  )
  assertUniqueNames('Entity type', entityNames)

  for (const editor of Object.values(entityEditors)) {
    const entityType = editor.entityType.trim()
    if (!editor.indexingEnabled) {
      throw new Error(
        `${entityType}: indexing must be enabled for a Platform Data Sync entity.`,
      )
    }
    const maxCharacters = parseInteger(
      `${entityType} max characters`,
      editor.maxCharacters,
      (candidate) =>
        candidate >= 1 && candidate <= MAX_PROJECTION_CHARACTERS,
      `must be between 1 and ${MAX_PROJECTION_CHARACTERS}`,
    )
    if (
      editor.analysisEnabled
      && editor.analysisAfter.length === 0
    ) {
      throw new Error(
        `${entityType}: select at least one analysis operation.`,
      )
    }
    if (editor.searchableFields.length === 0) {
      throw new Error(
        `${entityType}: at least one searchable field is required.`,
      )
    }

    assertUniqueNames(
      `${entityType} searchable field`,
      editor.searchableFields.map((field) => field.name),
    )
    assertUniqueNames(
      `${entityType} metadata field`,
      editor.metadataFields.map((field) => field.name),
    )

    let semanticSearchDeclared = false
    const searchableFields = editor.searchableFields.map((field) => {
      if (field.destinations.length === 0) {
        throw new Error(
          `${entityType}.${field.name}: select a search destination.`,
        )
      }
      semanticSearchDeclared =
        semanticSearchDeclared
        || field.destinations.includes('SEMANTIC_SEARCH')
      const maxLength = parseInteger(
        `${entityType}.${field.name} max length`,
        field.maxLength,
        (candidate) => candidate === -1 || candidate > 0,
        'must be -1 or a positive integer',
      )
      const priority = parseInteger(
        `${entityType}.${field.name} priority`,
        field.priority,
        (candidate) => candidate >= 0 && candidate <= 100,
        'must be between 0 and 100',
      )
      return {
        name: field.name.trim(),
        destinations: SEARCH_DESTINATIONS.filter((destination) =>
          field.destinations.includes(destination),
        ),
        preprocessing: field.preprocessing,
        'max-length': maxLength,
        priority,
        required: field.required,
      }
    })
    if (!semanticSearchDeclared) {
      throw new Error(
        `${entityType}: one searchable field must target SEMANTIC_SEARCH.`,
      )
    }

    const metadataFields = editor.metadataFields.map((field) => {
      if (field.destinations.length === 0) {
        throw new Error(
          `${entityType}.${field.name}: select a metadata destination.`,
        )
      }
      if (
        field.format.trim().length > 0
        && field.dataType !== 'DATE'
        && field.dataType !== 'NUMBER'
      ) {
        throw new Error(
          `${entityType}.${field.name}: format is valid only for DATE or NUMBER metadata.`,
        )
      }
      if (field.description.length > 500) {
        throw new Error(
          `${entityType}.${field.name}: description cannot exceed 500 characters.`,
        )
      }
      const priority = parseInteger(
        `${entityType}.${field.name} priority`,
        field.priority,
        (candidate) => candidate >= 0 && candidate <= 100,
        'must be between 0 and 100',
      )
      const normalized: Record<string, unknown> = {
        name: field.name.trim(),
        'data-type': field.dataType,
        destinations: METADATA_DESTINATIONS.filter((destination) =>
          field.destinations.includes(destination),
        ),
        priority,
        required: field.required,
        'sanitize-pii': field.sanitizePii,
      }
      if (field.format.trim().length > 0) {
        normalized.format = field.format.trim()
      }
      if (field.description.trim().length > 0) {
        normalized.description = field.description.trim()
      }
      return normalized
    })

    const entity: Record<string, unknown> = {
      indexing: {
        enabled: true,
        'max-characters': maxCharacters,
      },
      analysis: {
        enabled: editor.analysisEnabled,
        after: ANALYSIS_OPERATIONS.filter((operation) =>
          editor.analysisAfter.includes(operation),
        ),
      },
      'searchable-fields': searchableFields,
    }
    if (metadataFields.length > 0) {
      entity['metadata-fields'] = metadataFields
    }

    const provenance = editor.provenance
    if (typeof provenance.marketplaceManaged === 'boolean') {
      entity.marketplaceManaged = provenance.marketplaceManaged
    }
    if (provenance.marketplacePluginId?.trim()) {
      entity.marketplacePluginId =
        provenance.marketplacePluginId.trim()
    }
    if (provenance.marketplaceInstallId?.trim()) {
      entity.marketplaceInstallId =
        provenance.marketplaceInstallId.trim()
    }
    if (provenance.marketplacePluginVersion?.trim()) {
      entity.marketplacePluginVersion =
        provenance.marketplacePluginVersion.trim()
    }
    normalizedEntities[entityType] = entity
  }

  return {
    'ai-config': {
      'vector-dimensions': dimensions,
    },
    'ai-entities': normalizedEntities,
  }
}

function knowledgeSignature(
  vectorDimensions: string,
  entityEditors: Record<string, EntityEditorState>,
): string {
  const entities = Object.values(entityEditors)
    .map((entity) => ({
      ...entity,
      provenance: { ...entity.provenance },
      searchableFields: entity.searchableFields.map((field) => ({
        ...field,
        destinations: [...field.destinations],
      })),
      metadataFields: entity.metadataFields.map((field) => ({
        ...field,
        destinations: [...field.destinations],
      })),
      analysisAfter: [...entity.analysisAfter],
    }))
    .sort((left, right) =>
      left.entityType.localeCompare(right.entityType),
    )
  return JSON.stringify({
    vectorDimensions: vectorDimensions.trim(),
    entities,
  })
}

function summarizeKnowledge(
  vectorDimensions: string,
  entityEditors: Record<string, EntityEditorState>,
) {
  const entities = Object.values(entityEditors)
  return {
    vectorDimensions: vectorDimensions.trim() || 'Not configured',
    entityCount: entities.length,
    searchableFieldCount: entities.reduce(
      (count, entity) => count + entity.searchableFields.length,
      0,
    ),
    metadataFieldCount: entities.reduce(
      (count, entity) => count + entity.metadataFields.length,
      0,
    ),
    analysisEntityCount: entities.filter(
      (entity) => entity.analysisEnabled,
    ).length,
  }
}

function MultiValueSelect<T extends string>({
  label,
  value,
  options,
  onChange,
}: MultiValueSelectProps<T>) {
  return (
    <FormControl fullWidth size="small">
      <InputLabel>{label}</InputLabel>
      <Select
        multiple
        value={value}
        input={<OutlinedInput label={label} />}
        renderValue={(selected) => selected.join(', ')}
        onChange={(event) => {
          const selected =
            typeof event.target.value === 'string'
              ? event.target.value.split(',')
              : event.target.value
          onChange(
            options.filter((option) =>
              selected.includes(option),
            ),
          )
        }}
      >
        {options.map((option) => (
          <MenuItem key={option} value={option}>
            <Checkbox checked={value.includes(option)} />
            <ListItemText primary={option} />
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}

export function KnowledgePage() {
  const { selectedDeploymentId, workspace } =
    useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [vectorDimensions, setVectorDimensions] = useState('512')
  const [entityEditors, setEntityEditors] = useState<
    Record<string, EntityEditorState>
  >({})
  const [selectedEntityType, setSelectedEntityType] = useState('')
  const [newEntityType, setNewEntityType] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
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
    const nextEntityEditors = buildEntityEditors(
      draftQuery.data.entityConfig,
    )
    const entityTypes = Object.keys(nextEntityEditors)
    setVectorDimensions(
      readVectorDimensions(draftQuery.data.entityConfig),
    )
    setEntityEditors(nextEntityEditors)
    setSelectedEntityType((current) =>
      entityTypes.includes(current)
        ? current
        : (entityTypes[0] ?? ''),
    )
    setNewEntityType('')
    setFormError(null)
  }, [draftQuery.data])

  const contractIsCurrent =
    draftQuery.data?.entityConfigContractVersion
    === ENTITY_CONFIG_CONTRACT
  const selectedEntity = selectedEntityType
    ? (entityEditors[selectedEntityType] ?? null)
    : null
  const summary = useMemo(
    () => summarizeKnowledge(vectorDimensions, entityEditors),
    [entityEditors, vectorDimensions],
  )
  const savedEntityEditors = useMemo(
    () => buildEntityEditors(draftQuery.data?.entityConfig),
    [draftQuery.data?.entityConfig],
  )
  const savedVectorDimensions = useMemo(
    () => readVectorDimensions(draftQuery.data?.entityConfig),
    [draftQuery.data?.entityConfig],
  )
  const draftDirty = useMemo(
    () =>
      draftQuery.data
      && contractIsCurrent
        ? knowledgeSignature(vectorDimensions, entityEditors)
          !== knowledgeSignature(
            savedVectorDimensions,
            savedEntityEditors,
          )
        : false,
    [
      contractIsCurrent,
      draftQuery.data,
      entityEditors,
      savedEntityEditors,
      savedVectorDimensions,
      vectorDimensions,
    ],
  )
  const editorState = useMemo(
    () => ({
      dirty: draftDirty,
      label: 'Knowledge config',
      description: draftDirty
        ? 'Typed entity projection changes are pending.'
        : 'Knowledge editor matches the saved deployment draft.',
    }),
    [draftDirty],
  )
  useDeploymentWorkspaceEditorState(
    selectedDeploymentId ? editorState : null,
  )

  const invalidateDraftState = async () => {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ['deployment-draft', selectedDeploymentId],
      }),
      queryClient.invalidateQueries({
        queryKey: ['deployment-workspace', selectedDeploymentId],
      }),
      queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      queryClient.invalidateQueries({
        queryKey: ['deployment-validation'],
      }),
    ])
  }

  const saveMutation = useMutation({
    mutationFn: ({
      draftId,
      entityConfig,
    }: {
      draftId: string
      entityConfig: unknown
    }) => updateDeploymentDraft(draftId, { entityConfig }),
    onSuccess: invalidateDraftState,
  })

  const migrationPreviewMutation = useMutation({
    mutationFn: (draftId: string) =>
      previewDeploymentEntityConfigMigration(draftId),
  })

  const migrationApplyMutation = useMutation({
    mutationFn: (draftId: string) =>
      applyDeploymentEntityConfigMigration(draftId),
    onSuccess: async () => {
      migrationPreviewMutation.reset()
      await invalidateDraftState()
    },
  })

  const updateSelectedEntity = (
    patch: Partial<EntityEditorState>,
  ) => {
    if (!selectedEntityType) {
      return
    }
    setFormError(null)
    setEntityEditors((previous) => ({
      ...previous,
      [selectedEntityType]: {
        ...(previous[selectedEntityType]
          ?? newEntity(selectedEntityType)),
        ...patch,
      },
    }))
  }

  const updateSearchableField = (
    index: number,
    patch: Partial<SearchableFieldEditor>,
  ) => {
    if (!selectedEntity) {
      return
    }
    updateSelectedEntity({
      searchableFields: selectedEntity.searchableFields.map(
        (field, fieldIndex) =>
          fieldIndex === index ? { ...field, ...patch } : field,
      ),
    })
  }

  const updateMetadataField = (
    index: number,
    patch: Partial<MetadataFieldEditor>,
  ) => {
    if (!selectedEntity) {
      return
    }
    updateSelectedEntity({
      metadataFields: selectedEntity.metadataFields.map(
        (field, fieldIndex) =>
          fieldIndex === index ? { ...field, ...patch } : field,
      ),
    })
  }

  const handleAddEntity = () => {
    const trimmed = newEntityType.trim()
    if (trimmed.length === 0) {
      setFormError('Entity type name is required.')
      return
    }
    const duplicate = Object.keys(entityEditors).some(
      (entityType) =>
        entityType.toLowerCase() === trimmed.toLowerCase(),
    )
    if (duplicate) {
      setFormError(`Entity type already exists: ${trimmed}`)
      return
    }
    setEntityEditors((previous) => ({
      ...previous,
      [trimmed]: newEntity(trimmed),
    }))
    setSelectedEntityType(trimmed)
    setNewEntityType('')
    setFormError(null)
  }

  const handleRemoveEntity = () => {
    if (!selectedEntityType) {
      return
    }
    setEntityEditors((previous) => {
      const next = { ...previous }
      delete next[selectedEntityType]
      setSelectedEntityType(Object.keys(next)[0] ?? '')
      return next
    })
    setFormError(null)
  }

  const handleSave = () => {
    if (!draftQuery.data || !contractIsCurrent) {
      return
    }
    try {
      const entityConfig = normalizeEntityConfig(
        vectorDimensions,
        entityEditors,
      )
      setFormError(null)
      saveMutation.mutate({
        draftId: draftQuery.data.id,
        entityConfig,
      })
    } catch (error) {
      setFormError(
        error instanceof Error
          ? error.message
          : 'Failed to build entity config.',
      )
    }
  }

  const resetForm = () => {
    if (!draftQuery.data) {
      return
    }
    const nextEntityEditors = buildEntityEditors(
      draftQuery.data.entityConfig,
    )
    const entityTypes = Object.keys(nextEntityEditors)
    setVectorDimensions(
      readVectorDimensions(draftQuery.data.entityConfig),
    )
    setEntityEditors(nextEntityEditors)
    setSelectedEntityType((current) =>
      entityTypes.includes(current)
        ? current
        : (entityTypes[0] ?? ''),
    )
    setNewEntityType('')
    setFormError(null)
  }

  const migrationPreview = migrationPreviewMutation.data

  return (
    <Stack spacing={3}>
      <Box>
        <Chip
          label="Knowledge"
          color="primary"
          sx={{ mb: 1.5, fontWeight: 700 }}
        />
        <Typography
          variant="h4"
          sx={{ fontWeight: 800, letterSpacing: 0 }}
        >
          Entity projection editor
        </Typography>
        <Typography
          variant="body1"
          color="text.secondary"
          sx={{ mt: 1.25, maxWidth: 980 }}
        >
          {ENTITY_CONFIG_CONTRACT} lifecycle, retrieval, and metadata
          projection.
        </Typography>
      </Box>

      <Card
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          boxShadow: 'none',
        }}
      >
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">Deployment workspace</Typography>
            {workspace ? (
              <Stack
                direction="row"
                spacing={1}
                flexWrap="wrap"
                useFlexGap
              >
                <Chip label={workspace.deployment.name} variant="outlined" />
                <Chip
                  label={workspace.deployment.environment}
                  variant="outlined"
                />
                <Chip
                  label={workspace.deployment.status}
                  color="primary"
                />
                <Chip label={workspace.template.name} variant="outlined" />
              </Stack>
            ) : null}
            {!canEdit && workspace ? (
              <Alert severity="info">
                Deployment editor access is required to change this draft.
              </Alert>
            ) : null}
            {draftQuery.data ? (
              <Stack
                direction="row"
                spacing={1}
                flexWrap="wrap"
                useFlexGap
              >
                <Chip
                  label={`Draft ${draftQuery.data.id}`}
                  variant="outlined"
                />
                <Chip
                  label={`Revision ${draftQuery.data.revisionNumber}`}
                  variant="outlined"
                />
                <Chip
                  label={
                    draftQuery.data.entityConfigContractVersion
                    ?? 'Unversioned entity contract'
                  }
                  color={contractIsCurrent ? 'success' : 'warning'}
                />
              </Stack>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      {draftQuery.data && !contractIsCurrent ? (
        <Card
          sx={{
            border: '1px solid',
            borderColor: 'warning.main',
            boxShadow: 'none',
          }}
        >
          <CardContent>
            <Stack spacing={2}>
              <Box>
                <Typography variant="h6">
                  Entity contract migration required
                </Typography>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ mt: 0.5 }}
                >
                  Current contract:{' '}
                  {draftQuery.data.entityConfigContractVersion
                    ?? 'unversioned'}
                </Typography>
              </Box>

              {migrationPreview ? (
                <Stack spacing={1}>
                  <Alert
                    severity={
                      migrationPreview.report.blocked
                        ? 'error'
                        : 'info'
                    }
                  >
                    {migrationPreview.report.blocked
                      ? 'Migration is blocked.'
                      : `${migrationPreview.report.convertedEntityTypes.length} entity types are ready for migration.`}
                  </Alert>
                  <Stack
                    direction="row"
                    spacing={1}
                    flexWrap="wrap"
                    useFlexGap
                  >
                    <Chip
                      label={`${migrationPreview.report.warnings.length} warnings`}
                      variant="outlined"
                    />
                    <Chip
                      label={`${migrationPreview.report.droppedKeys.length} removed keys`}
                      variant="outlined"
                    />
                    <Chip
                      label={
                        migrationPreview.report.vectorRebuildRequired
                          ? 'Vector rebuild required'
                          : 'No vector rebuild'
                      }
                      color={
                        migrationPreview.report.vectorRebuildRequired
                          ? 'warning'
                          : 'default'
                      }
                    />
                  </Stack>
                  {migrationPreview.report.blockers.map((blocker) => (
                    <Alert key={`${blocker.code}:${blocker.path}`} severity="error">
                      {blocker.code} at {blocker.path}: {blocker.message}
                    </Alert>
                  ))}
                  {migrationPreview.report.warnings.map((warning) => (
                    <Alert key={`${warning.code}:${warning.path}`} severity="warning">
                      {warning.code} at {warning.path}: {warning.message}
                    </Alert>
                  ))}
                </Stack>
              ) : null}

              {migrationPreviewMutation.isError ? (
                <Alert severity="error">
                  {migrationPreviewMutation.error instanceof Error
                    ? migrationPreviewMutation.error.message
                    : 'Migration preview failed.'}
                </Alert>
              ) : null}
              {migrationApplyMutation.isError ? (
                <Alert severity="error">
                  {migrationApplyMutation.error instanceof Error
                    ? migrationApplyMutation.error.message
                    : 'Migration failed.'}
                </Alert>
              ) : null}

              <Stack direction="row" spacing={1.5}>
                <Button
                  variant="outlined"
                  startIcon={<PreviewRoundedIcon />}
                  onClick={() =>
                    migrationPreviewMutation.mutate(
                      draftQuery.data.id,
                    )
                  }
                  disabled={
                    !canEdit
                    || migrationPreviewMutation.isPending
                    || migrationApplyMutation.isPending
                  }
                >
                  Preview migration
                </Button>
                <Button
                  variant="contained"
                  startIcon={<UpgradeRoundedIcon />}
                  onClick={() =>
                    migrationApplyMutation.mutate(draftQuery.data.id)
                  }
                  disabled={
                    !canEdit
                    || !migrationPreview
                    || migrationPreview.report.blocked
                    || migrationApplyMutation.isPending
                  }
                >
                  Apply migration
                </Button>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      {selectedDeploymentId ? (
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={4}>
              <Card
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  boxShadow: 'none',
                  height: '100%',
                }}
              >
                <CardContent>
                  <Stack spacing={2.5}>
                    <Typography variant="h6">
                      Knowledge structure
                    </Typography>

                    {draftQuery.isLoading ? (
                      <Typography color="text.secondary">
                        Loading entity config...
                      </Typography>
                    ) : draftQuery.isError ? (
                      <Alert severity="error">
                        {draftQuery.error instanceof Error
                          ? draftQuery.error.message
                          : 'Failed to load entity config.'}
                      </Alert>
                    ) : (
                      <>
                        <TextField
                          fullWidth
                          size="small"
                          type="number"
                          label="Vector dimensions"
                          value={vectorDimensions}
                          inputProps={{ min: 1, step: 1 }}
                          disabled={!canEdit || !contractIsCurrent}
                          onChange={(event) => {
                            setVectorDimensions(event.target.value)
                            setFormError(null)
                          }}
                        />

                        <Stack
                          direction={{ xs: 'column', sm: 'row' }}
                          spacing={1.5}
                        >
                          <TextField
                            fullWidth
                            size="small"
                            label="New entity type"
                            value={newEntityType}
                            disabled={!canEdit || !contractIsCurrent}
                            onChange={(event) => {
                              setNewEntityType(event.target.value)
                              setFormError(null)
                            }}
                          />
                          <Button
                            variant="outlined"
                            startIcon={<AddRoundedIcon />}
                            onClick={handleAddEntity}
                            disabled={!canEdit || !contractIsCurrent}
                            sx={{ minWidth: 128 }}
                          >
                            Add entity
                          </Button>
                        </Stack>

                        <Divider />

                        {Object.keys(entityEditors).length === 0 ? (
                          <Alert severity="info">
                            No entity types configured.
                          </Alert>
                        ) : (
                          <List dense disablePadding>
                            {Object.keys(entityEditors).map(
                              (entityType) => (
                                <ListItem
                                  key={entityType}
                                  disablePadding
                                  secondaryAction={
                                    entityType
                                    === selectedEntityType ? (
                                      <Tooltip title="Remove entity">
                                        <span>
                                          <IconButton
                                            size="small"
                                            color="error"
                                            onClick={handleRemoveEntity}
                                            disabled={
                                              !canEdit
                                              || !contractIsCurrent
                                            }
                                          >
                                            <DeleteOutlineRoundedIcon />
                                          </IconButton>
                                        </span>
                                      </Tooltip>
                                    ) : undefined
                                  }
                                >
                                  <ListItemButton
                                    selected={
                                      entityType
                                      === selectedEntityType
                                    }
                                    onClick={() =>
                                      setSelectedEntityType(entityType)
                                    }
                                  >
                                    <ListItemText
                                      primary={entityType}
                                      secondary={`${entityEditors[entityType].searchableFields.length} search fields, ${entityEditors[entityType].metadataFields.length} metadata fields`}
                                    />
                                  </ListItemButton>
                                </ListItem>
                              ),
                            )}
                          </List>
                        )}
                      </>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={8}>
              <Card
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  boxShadow: 'none',
                }}
              >
                <CardContent>
                  <Stack spacing={3}>
                    <Stack
                      direction={{ xs: 'column', sm: 'row' }}
                      justifyContent="space-between"
                      spacing={1}
                    >
                      <Box>
                        <Typography variant="h6">
                          Selected entity
                        </Typography>
                        {selectedEntity ? (
                          <Typography
                            variant="body2"
                            color="text.secondary"
                            sx={{ mt: 0.5 }}
                          >
                            {selectedEntity.entityType}
                          </Typography>
                        ) : null}
                      </Box>
                      {selectedEntity?.provenance
                        .marketplaceManaged ? (
                          <Chip
                            label="Marketplace managed"
                            color="secondary"
                            variant="outlined"
                          />
                        ) : null}
                    </Stack>

                    {selectedEntity ? (
                      <>
                        <Box>
                          <Typography
                            variant="subtitle2"
                            sx={{ mb: 1.5 }}
                          >
                            Lifecycle
                          </Typography>
                          <Grid container spacing={2}>
                            <Grid item xs={12} sm={6}>
                              <FormControlLabel
                                control={
                                  <Switch
                                    checked={
                                      selectedEntity.indexingEnabled
                                    }
                                    disabled
                                  />
                                }
                                label="Indexing enabled"
                              />
                            </Grid>
                            <Grid item xs={12} sm={6}>
                              <TextField
                                fullWidth
                                size="small"
                                type="number"
                                label="Projection character limit"
                                value={selectedEntity.maxCharacters}
                                inputProps={{
                                  min: 1,
                                  max: MAX_PROJECTION_CHARACTERS,
                                  step: 1,
                                }}
                                disabled={
                                  !canEdit || !contractIsCurrent
                                }
                                onChange={(event) =>
                                  updateSelectedEntity({
                                    maxCharacters:
                                      event.target.value,
                                  })
                                }
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <Stack
                                direction={{ xs: 'column', md: 'row' }}
                                alignItems={{
                                  xs: 'flex-start',
                                  md: 'center',
                                }}
                                spacing={1.5}
                              >
                                <FormControlLabel
                                  control={
                                    <Switch
                                      checked={
                                        selectedEntity.analysisEnabled
                                      }
                                      disabled={
                                        !canEdit
                                        || !contractIsCurrent
                                      }
                                      onChange={(event) =>
                                        updateSelectedEntity({
                                          analysisEnabled:
                                            event.target.checked,
                                          analysisAfter:
                                            event.target.checked
                                              ? selectedEntity.analysisAfter
                                              : [],
                                        })
                                      }
                                    />
                                  }
                                  label="Post-index analysis"
                                />
                                {ANALYSIS_OPERATIONS.map(
                                  (operation) => (
                                    <FormControlLabel
                                      key={operation}
                                      control={
                                        <Checkbox
                                          size="small"
                                          checked={selectedEntity.analysisAfter.includes(
                                            operation,
                                          )}
                                          disabled={
                                            !canEdit
                                            || !contractIsCurrent
                                            || !selectedEntity.analysisEnabled
                                          }
                                          onChange={(event) =>
                                            updateSelectedEntity({
                                              analysisAfter:
                                                event.target.checked
                                                  ? [
                                                    ...selectedEntity.analysisAfter,
                                                    operation,
                                                  ]
                                                  : selectedEntity.analysisAfter.filter(
                                                    (value) =>
                                                      value
                                                      !== operation,
                                                  ),
                                            })
                                          }
                                        />
                                      }
                                      label={operation}
                                    />
                                  ),
                                )}
                              </Stack>
                            </Grid>
                          </Grid>
                        </Box>

                        <Divider />

                        <Box>
                          <Stack
                            direction="row"
                            justifyContent="space-between"
                            alignItems="center"
                            sx={{ mb: 1.5 }}
                          >
                            <Typography variant="subtitle2">
                              Searchable fields
                            </Typography>
                            <Tooltip title="Add searchable field">
                              <span>
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    updateSelectedEntity({
                                      searchableFields: [
                                        ...selectedEntity.searchableFields,
                                        newSearchableField(),
                                      ],
                                    })
                                  }
                                  disabled={
                                    !canEdit || !contractIsCurrent
                                  }
                                >
                                  <AddRoundedIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Stack>

                          <Stack divider={<Divider flexItem />}>
                            {selectedEntity.searchableFields.map(
                              (field, index) => (
                                <Box
                                  key={`searchable-${index}`}
                                  sx={{ py: 2 }}
                                >
                                  <Grid
                                    container
                                    spacing={1.5}
                                    alignItems="center"
                                  >
                                    <Grid item xs={12} md={4}>
                                      <TextField
                                        fullWidth
                                        size="small"
                                        label="Field name"
                                        value={field.name}
                                        disabled={
                                          !canEdit
                                          || !contractIsCurrent
                                        }
                                        onChange={(event) =>
                                          updateSearchableField(
                                            index,
                                            {
                                              name: event.target.value,
                                            },
                                          )
                                        }
                                      />
                                    </Grid>
                                    <Grid item xs={12} md={4}>
                                      <FormControl
                                        fullWidth
                                        size="small"
                                      >
                                        <InputLabel>
                                          Preprocessing
                                        </InputLabel>
                                        <Select
                                          label="Preprocessing"
                                          value={field.preprocessing}
                                          disabled={
                                            !canEdit
                                            || !contractIsCurrent
                                          }
                                          onChange={(event) =>
                                            updateSearchableField(
                                              index,
                                              {
                                                preprocessing:
                                                  event.target
                                                    .value as SearchPreprocessing,
                                              },
                                            )
                                          }
                                        >
                                          {SEARCH_PREPROCESSING.map(
                                            (option) => (
                                              <MenuItem
                                                key={option}
                                                value={option}
                                              >
                                                {option}
                                              </MenuItem>
                                            ),
                                          )}
                                        </Select>
                                      </FormControl>
                                    </Grid>
                                    <Grid item xs={6} md={1.5}>
                                      <TextField
                                        fullWidth
                                        size="small"
                                        type="number"
                                        label="Max"
                                        value={field.maxLength}
                                        disabled={
                                          !canEdit
                                          || !contractIsCurrent
                                        }
                                        onChange={(event) =>
                                          updateSearchableField(
                                            index,
                                            {
                                              maxLength:
                                                event.target.value,
                                            },
                                          )
                                        }
                                      />
                                    </Grid>
                                    <Grid item xs={6} md={1.5}>
                                      <TextField
                                        fullWidth
                                        size="small"
                                        type="number"
                                        label="Priority"
                                        value={field.priority}
                                        inputProps={{
                                          min: 0,
                                          max: 100,
                                        }}
                                        disabled={
                                          !canEdit
                                          || !contractIsCurrent
                                        }
                                        onChange={(event) =>
                                          updateSearchableField(
                                            index,
                                            {
                                              priority:
                                                event.target.value,
                                            },
                                          )
                                        }
                                      />
                                    </Grid>
                                    <Grid item xs={12} md={1}>
                                      <Tooltip title="Remove searchable field">
                                        <span>
                                          <IconButton
                                            color="error"
                                            disabled={
                                              !canEdit
                                              || !contractIsCurrent
                                            }
                                            onClick={() =>
                                              updateSelectedEntity({
                                                searchableFields:
                                                  selectedEntity.searchableFields.filter(
                                                    (
                                                      _,
                                                      fieldIndex,
                                                    ) =>
                                                      fieldIndex
                                                      !== index,
                                                  ),
                                              })
                                            }
                                          >
                                            <DeleteOutlineRoundedIcon />
                                          </IconButton>
                                        </span>
                                      </Tooltip>
                                    </Grid>
                                    <Grid item xs={12} md={8}>
                                      <MultiValueSelect
                                        label="Destinations"
                                        value={field.destinations}
                                        options={SEARCH_DESTINATIONS}
                                        onChange={(destinations) =>
                                          updateSearchableField(
                                            index,
                                            { destinations },
                                          )
                                        }
                                      />
                                    </Grid>
                                    <Grid item xs={12} md={4}>
                                      <FormControlLabel
                                        control={
                                          <Checkbox
                                            checked={field.required}
                                            disabled={
                                              !canEdit
                                              || !contractIsCurrent
                                            }
                                            onChange={(event) =>
                                              updateSearchableField(
                                                index,
                                                {
                                                  required:
                                                    event.target
                                                      .checked,
                                                },
                                              )
                                            }
                                          />
                                        }
                                        label="Required"
                                      />
                                    </Grid>
                                  </Grid>
                                </Box>
                              ),
                            )}
                          </Stack>
                        </Box>

                        <Divider />

                        <Box>
                          <Stack
                            direction="row"
                            justifyContent="space-between"
                            alignItems="center"
                            sx={{ mb: 1.5 }}
                          >
                            <Typography variant="subtitle2">
                              Metadata fields
                            </Typography>
                            <Tooltip title="Add metadata field">
                              <span>
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    updateSelectedEntity({
                                      metadataFields: [
                                        ...selectedEntity.metadataFields,
                                        newMetadataField(),
                                      ],
                                    })
                                  }
                                  disabled={
                                    !canEdit || !contractIsCurrent
                                  }
                                >
                                  <AddRoundedIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Stack>

                          {selectedEntity.metadataFields.length === 0 ? (
                            <Alert severity="info">
                              No metadata fields configured.
                            </Alert>
                          ) : (
                            <Stack divider={<Divider flexItem />}>
                              {selectedEntity.metadataFields.map(
                                (field, index) => (
                                  <Box
                                    key={`metadata-${index}`}
                                    sx={{ py: 2 }}
                                  >
                                    <Grid
                                      container
                                      spacing={1.5}
                                      alignItems="center"
                                    >
                                      <Grid item xs={12} md={4}>
                                        <TextField
                                          fullWidth
                                          size="small"
                                          label="Field name"
                                          value={field.name}
                                          disabled={
                                            !canEdit
                                            || !contractIsCurrent
                                          }
                                          onChange={(event) =>
                                            updateMetadataField(
                                              index,
                                              {
                                                name:
                                                  event.target.value,
                                              },
                                            )
                                          }
                                        />
                                      </Grid>
                                      <Grid item xs={12} md={3}>
                                        <FormControl
                                          fullWidth
                                          size="small"
                                        >
                                          <InputLabel>
                                            Data type
                                          </InputLabel>
                                          <Select
                                            label="Data type"
                                            value={field.dataType}
                                            disabled={
                                              !canEdit
                                              || !contractIsCurrent
                                            }
                                            onChange={(event) =>
                                              updateMetadataField(
                                                index,
                                                {
                                                  dataType:
                                                    event.target
                                                      .value as MetadataDataType,
                                                },
                                              )
                                            }
                                          >
                                            {METADATA_DATA_TYPES.map(
                                              (option) => (
                                                <MenuItem
                                                  key={option}
                                                  value={option}
                                                >
                                                  {option}
                                                </MenuItem>
                                              ),
                                            )}
                                          </Select>
                                        </FormControl>
                                      </Grid>
                                      <Grid item xs={6} md={2}>
                                        <TextField
                                          fullWidth
                                          size="small"
                                          type="number"
                                          label="Priority"
                                          value={field.priority}
                                          inputProps={{
                                            min: 0,
                                            max: 100,
                                          }}
                                          disabled={
                                            !canEdit
                                            || !contractIsCurrent
                                          }
                                          onChange={(event) =>
                                            updateMetadataField(
                                              index,
                                              {
                                                priority:
                                                  event.target.value,
                                              },
                                            )
                                          }
                                        />
                                      </Grid>
                                      <Grid item xs={6} md={2}>
                                        <TextField
                                          fullWidth
                                          size="small"
                                          label="Format"
                                          value={field.format}
                                          disabled={
                                            !canEdit
                                            || !contractIsCurrent
                                            || (
                                              field.dataType !== 'DATE'
                                              && field.dataType
                                              !== 'NUMBER'
                                            )
                                          }
                                          onChange={(event) =>
                                            updateMetadataField(
                                              index,
                                              {
                                                format:
                                                  event.target.value,
                                              },
                                            )
                                          }
                                        />
                                      </Grid>
                                      <Grid item xs={12} md={1}>
                                        <Tooltip title="Remove metadata field">
                                          <span>
                                            <IconButton
                                              color="error"
                                              disabled={
                                                !canEdit
                                                || !contractIsCurrent
                                              }
                                              onClick={() =>
                                                updateSelectedEntity({
                                                  metadataFields:
                                                    selectedEntity.metadataFields.filter(
                                                      (
                                                        _,
                                                        fieldIndex,
                                                      ) =>
                                                        fieldIndex
                                                        !== index,
                                                    ),
                                                })
                                              }
                                            >
                                              <DeleteOutlineRoundedIcon />
                                            </IconButton>
                                          </span>
                                        </Tooltip>
                                      </Grid>
                                      <Grid item xs={12} md={7}>
                                        <MultiValueSelect
                                          label="Destinations"
                                          value={field.destinations}
                                          options={
                                            METADATA_DESTINATIONS
                                          }
                                          onChange={(destinations) =>
                                            updateMetadataField(
                                              index,
                                              { destinations },
                                            )
                                          }
                                        />
                                      </Grid>
                                      <Grid item xs={12} md={5}>
                                        <Stack
                                          direction="row"
                                          spacing={1}
                                          flexWrap="wrap"
                                          useFlexGap
                                        >
                                          <FormControlLabel
                                            control={
                                              <Checkbox
                                                checked={
                                                  field.required
                                                }
                                                disabled={
                                                  !canEdit
                                                  || !contractIsCurrent
                                                }
                                                onChange={(event) =>
                                                  updateMetadataField(
                                                    index,
                                                    {
                                                      required:
                                                        event.target
                                                          .checked,
                                                    },
                                                  )
                                                }
                                              />
                                            }
                                            label="Required"
                                          />
                                          <FormControlLabel
                                            control={
                                              <Checkbox
                                                checked={
                                                  field.sanitizePii
                                                }
                                                disabled={
                                                  !canEdit
                                                  || !contractIsCurrent
                                                }
                                                onChange={(event) =>
                                                  updateMetadataField(
                                                    index,
                                                    {
                                                      sanitizePii:
                                                        event.target
                                                          .checked,
                                                    },
                                                  )
                                                }
                                              />
                                            }
                                            label="Sanitize PII"
                                          />
                                        </Stack>
                                      </Grid>
                                      <Grid item xs={12}>
                                        <TextField
                                          fullWidth
                                          size="small"
                                          label="Description"
                                          value={field.description}
                                          inputProps={{ maxLength: 500 }}
                                          disabled={
                                            !canEdit
                                            || !contractIsCurrent
                                          }
                                          onChange={(event) =>
                                            updateMetadataField(
                                              index,
                                              {
                                                description:
                                                  event.target.value,
                                              },
                                            )
                                          }
                                        />
                                      </Grid>
                                    </Grid>
                                  </Box>
                                ),
                              )}
                            </Stack>
                          )}
                        </Box>
                      </>
                    ) : (
                      <Alert severity="info">
                        Select or add an entity type.
                      </Alert>
                    )}

                    {formError ? (
                      <Alert severity="error">{formError}</Alert>
                    ) : null}
                    {saveMutation.isError ? (
                      <Alert severity="error">
                        {saveMutation.error instanceof Error
                          ? saveMutation.error.message
                          : 'Failed to save entity config.'}
                      </Alert>
                    ) : null}
                    {saveMutation.isSuccess ? (
                      <Alert severity="success">
                        Entity config draft saved.
                      </Alert>
                    ) : null}

                    <Stack direction="row" spacing={1.5}>
                      <Button
                        variant="contained"
                        startIcon={<SaveRoundedIcon />}
                        onClick={handleSave}
                        disabled={
                          !canEdit
                          || !contractIsCurrent
                          || saveMutation.isPending
                          || draftQuery.isLoading
                        }
                      >
                        {saveMutation.isPending
                          ? 'Saving...'
                          : 'Save entity config'}
                      </Button>
                      <Button
                        variant="outlined"
                        startIcon={<RestartAltRoundedIcon />}
                        onClick={resetForm}
                        disabled={saveMutation.isPending}
                      >
                        Reset
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              boxShadow: 'none',
            }}
          >
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Knowledge summary</Typography>
                <Stack
                  direction="row"
                  spacing={1}
                  flexWrap="wrap"
                  useFlexGap
                >
                  <Chip
                    label={`Vector dimensions: ${summary.vectorDimensions}`}
                    color="primary"
                  />
                  <Chip
                    label={`${summary.entityCount} entity types`}
                    variant="outlined"
                  />
                  <Chip
                    label={`${summary.searchableFieldCount} searchable fields`}
                    variant="outlined"
                  />
                  <Chip
                    label={`${summary.metadataFieldCount} metadata fields`}
                    variant="outlined"
                  />
                  <Chip
                    label={`${summary.analysisEntityCount} analysis-enabled`}
                    variant="outlined"
                  />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  )
}
