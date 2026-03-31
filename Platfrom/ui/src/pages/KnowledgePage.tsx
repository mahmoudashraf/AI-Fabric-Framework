import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
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
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Stack,
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

type EntityEditorState = {
  entityType: string
  searchableFields: string
  embeddableFields: string
  metadataFields: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {}
}

function ensureRecord(parent: Record<string, unknown>, key: string): Record<string, unknown> {
  const next = asRecord(parent[key])
  const cloned = cloneJson(next)
  parent[key] = cloned
  return cloned
}

function splitTokens(value: string): string[] {
  return value
    .split(/[\n,]/)
    .map((candidate) => candidate.trim())
    .filter((candidate, index, all) => candidate.length > 0 && all.indexOf(candidate) === index)
}

function extractNamedFieldNames(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.flatMap((candidate) => {
    if (!isRecord(candidate)) {
      return []
    }

    const name = candidate.name
    return typeof name === 'string' && name.trim().length > 0 ? [name.trim()] : []
  })
}

function extractMetadataFieldEntries(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.flatMap((candidate) => {
    if (!isRecord(candidate)) {
      return []
    }

    const name = typeof candidate.name === 'string' ? candidate.name.trim() : ''
    const type = typeof candidate.type === 'string' ? candidate.type.trim() : 'string'
    if (name.length === 0) {
      return []
    }
    return [`${name}:${type.length > 0 ? type : 'string'}`]
  })
}

function buildEntityEditors(config: unknown): Record<string, EntityEditorState> {
  const root = asRecord(config)
  const entities = asRecord(root['ai-entities'])

  return Object.entries(entities).reduce<Record<string, EntityEditorState>>((accumulator, [entityType, value]) => {
    const entity = asRecord(value)
    accumulator[entityType] = {
      entityType,
      searchableFields: extractNamedFieldNames(entity['searchable-fields']).join(', '),
      embeddableFields: extractNamedFieldNames(entity['embeddable-fields']).join(', '),
      metadataFields: extractMetadataFieldEntries(entity['metadata-fields']).join(', '),
    }
    return accumulator
  }, {})
}

function readVectorDimensions(config: unknown): string {
  const root = asRecord(config)
  const aiConfig = asRecord(root['ai-config'])
  const value = aiConfig['vector-dimensions']
  return typeof value === 'number' ? String(value) : '512'
}

function buildNamedObjects(existingValue: unknown, names: string[]): Record<string, unknown>[] {
  const existingByName = new Map<string, Record<string, unknown>>()
  if (Array.isArray(existingValue)) {
    for (const candidate of existingValue) {
      if (!isRecord(candidate)) {
        continue
      }
      const name = typeof candidate.name === 'string' ? candidate.name.trim() : ''
      if (name.length > 0) {
        existingByName.set(name, cloneJson(candidate))
      }
    }
  }

  return names.map((name) => {
    const existing = existingByName.get(name) ?? {}
    const next = cloneJson(existing)
    next.name = name
    return next
  })
}

function parseMetadataEntries(value: string): Array<{ name: string; type: string }> {
  return splitTokens(value).map((entry) => {
    const separator = entry.indexOf(':')
    if (separator === -1) {
      return {
        name: entry,
        type: 'string',
      }
    }

    const name = entry.slice(0, separator).trim()
    const type = entry.slice(separator + 1).trim()
    if (name.length === 0) {
      throw new Error('Metadata field entries must use name:type format.')
    }

    return {
      name,
      type: type.length > 0 ? type : 'string',
    }
  })
}

function buildMetadataObjects(
  existingValue: unknown,
  metadataEntries: Array<{ name: string; type: string }>,
): Record<string, unknown>[] {
  const existingByName = new Map<string, Record<string, unknown>>()
  if (Array.isArray(existingValue)) {
    for (const candidate of existingValue) {
      if (!isRecord(candidate)) {
        continue
      }
      const name = typeof candidate.name === 'string' ? candidate.name.trim() : ''
      if (name.length > 0) {
        existingByName.set(name, cloneJson(candidate))
      }
    }
  }

  return metadataEntries.map((entry) => {
    const existing = existingByName.get(entry.name) ?? {}
    const next = cloneJson(existing)
    next.name = entry.name
    next.type = entry.type
    return next
  })
}

function summarizeKnowledge(vectorDimensions: string, entityEditors: Record<string, EntityEditorState>) {
  const entities = Object.values(entityEditors)
  return {
    vectorDimensions: vectorDimensions.trim() || 'Not configured',
    entityCount: entities.length,
    searchableFieldCount: entities.reduce((count, entity) => count + splitTokens(entity.searchableFields).length, 0),
    embeddableFieldCount: entities.reduce((count, entity) => count + splitTokens(entity.embeddableFields).length, 0),
    metadataFieldCount: entities.reduce((count, entity) => count + splitTokens(entity.metadataFields).length, 0),
  }
}

export function KnowledgePage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [vectorDimensions, setVectorDimensions] = useState('512')
  const [entityEditors, setEntityEditors] = useState<Record<string, EntityEditorState>>({})
  const [selectedEntityType, setSelectedEntityType] = useState('')
  const [newEntityType, setNewEntityType] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (!draftQuery.data) {
      return
    }

    const nextEntityEditors = buildEntityEditors(draftQuery.data.entityConfig)
    const entityTypes = Object.keys(nextEntityEditors)
    setVectorDimensions(readVectorDimensions(draftQuery.data.entityConfig))
    setEntityEditors(nextEntityEditors)
    setSelectedEntityType((current) => (entityTypes.includes(current) ? current : (entityTypes[0] ?? '')))
    setNewEntityType('')
    setFormError(null)
  }, [draftQuery.data])

  const selectedEntity = selectedEntityType ? entityEditors[selectedEntityType] ?? null : null
  const summary = useMemo(
    () => summarizeKnowledge(vectorDimensions, entityEditors),
    [entityEditors, vectorDimensions],
  )

  const saveMutation = useMutation({
    mutationFn: ({ draftId, entityConfig }: { draftId: string; entityConfig: unknown }) =>
      updateDeploymentDraft(draftId, { entityConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
      ])
    },
  })

  const updateSelectedEntity = (patch: Partial<EntityEditorState>) => {
    if (!selectedEntityType) {
      return
    }

    setFormError(null)
    setEntityEditors((previous) => ({
      ...previous,
      [selectedEntityType]: {
        ...(previous[selectedEntityType] ?? {
          entityType: selectedEntityType,
          searchableFields: '',
          embeddableFields: '',
          metadataFields: '',
        }),
        ...patch,
      },
    }))
  }

  const handleAddEntity = () => {
    const trimmed = newEntityType.trim()
    if (trimmed.length === 0) {
      setFormError('Entity type name is required.')
      return
    }
    if (entityEditors[trimmed]) {
      setFormError(`Entity type already exists: ${trimmed}`)
      return
    }

    setEntityEditors((previous) => ({
      ...previous,
      [trimmed]: {
        entityType: trimmed,
        searchableFields: '',
        embeddableFields: '',
        metadataFields: '',
      },
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
      const remainingKeys = Object.keys(next)
      setSelectedEntityType(remainingKeys[0] ?? '')
      return next
    })
    setFormError(null)
  }

  const handleSave = () => {
    if (!draftQuery.data) {
      return
    }

    try {
      const parsedDimensions = Number(vectorDimensions)
      if (!Number.isInteger(parsedDimensions) || parsedDimensions <= 0) {
        throw new Error('Vector dimensions must be a positive integer.')
      }

      const nextConfig = cloneJson(
        isRecord(draftQuery.data.entityConfig) ? draftQuery.data.entityConfig : {},
      )
      const aiConfig = ensureRecord(nextConfig, 'ai-config')
      const existingEntities = asRecord(nextConfig['ai-entities'])
      const nextEntities: Record<string, unknown> = {}

      aiConfig['vector-dimensions'] = parsedDimensions

      for (const [entityType, editor] of Object.entries(entityEditors)) {
        const baseEntity = cloneJson(asRecord(existingEntities[entityType]))
        const searchableFields = splitTokens(editor.searchableFields)
        const embeddableFields = splitTokens(editor.embeddableFields)
        const metadataFields = parseMetadataEntries(editor.metadataFields)

        if (searchableFields.length > 0) {
          baseEntity['searchable-fields'] = buildNamedObjects(baseEntity['searchable-fields'], searchableFields)
        } else {
          delete baseEntity['searchable-fields']
        }

        if (embeddableFields.length > 0) {
          baseEntity['embeddable-fields'] = buildNamedObjects(baseEntity['embeddable-fields'], embeddableFields)
        } else {
          delete baseEntity['embeddable-fields']
        }

        if (metadataFields.length > 0) {
          baseEntity['metadata-fields'] = buildMetadataObjects(baseEntity['metadata-fields'], metadataFields)
        } else {
          delete baseEntity['metadata-fields']
        }

        nextEntities[entityType] = baseEntity
      }

      nextConfig['ai-entities'] = nextEntities
      setFormError(null)
      saveMutation.mutate({
        draftId: draftQuery.data.id,
        entityConfig: nextConfig,
      })
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Failed to build entity config')
    }
  }

  const resetForm = () => {
    if (!draftQuery.data) {
      return
    }

    const nextEntityEditors = buildEntityEditors(draftQuery.data.entityConfig)
    const entityTypes = Object.keys(nextEntityEditors)
    setVectorDimensions(readVectorDimensions(draftQuery.data.entityConfig))
    setEntityEditors(nextEntityEditors)
    setSelectedEntityType((current) => (entityTypes.includes(current) ? current : (entityTypes[0] ?? '')))
    setNewEntityType('')
    setFormError(null)
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Knowledge" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Entity and vector-space editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Knowledge config now uses a structured editor for vector dimensions and entity field lists while
          preserving any existing advanced entity properties that are already stored in the draft.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Entity and vector-space edits now follow the deployment selected in the shared workspace header.
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
            <Grid item xs={12} lg={5}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
                <CardContent>
                  <Stack spacing={2.5}>
                    <Box>
                      <Typography variant="h6">Knowledge structure</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Manage vector dimensions and the set of entity types that participate in indexing and retrieval.
                      </Typography>
                    </Box>

                    {draftQuery.isLoading ? (
                      <Typography color="text.secondary">Loading entity config...</Typography>
                    ) : draftQuery.isError ? (
                      <Alert severity="error">
                        {draftQuery.error instanceof Error
                          ? draftQuery.error.message
                          : 'Failed to load entity config'}
                      </Alert>
                    ) : (
                      <>
                        <TextField
                          fullWidth
                          label="Vector dimensions"
                          value={vectorDimensions}
                          onChange={(event) => {
                            setVectorDimensions(event.target.value)
                            setFormError(null)
                          }}
                        />

                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                          <TextField
                            fullWidth
                            label="New entity type"
                            value={newEntityType}
                            onChange={(event) => {
                              setNewEntityType(event.target.value)
                              setFormError(null)
                            }}
                          />
                          <Button variant="outlined" startIcon={<AddRoundedIcon />} onClick={handleAddEntity}>
                            Add entity
                          </Button>
                        </Stack>

                        <Divider />

                        {Object.keys(entityEditors).length === 0 ? (
                          <Alert severity="info">No entity types configured yet.</Alert>
                        ) : (
                          <List dense disablePadding>
                            {Object.keys(entityEditors).map((entityType) => (
                              <ListItem key={entityType} disablePadding secondaryAction={
                                entityType === selectedEntityType ? (
                                  <Button
                                    size="small"
                                    color="error"
                                    startIcon={<DeleteOutlineRoundedIcon />}
                                    onClick={handleRemoveEntity}
                                  >
                                    Remove
                                  </Button>
                                ) : undefined
                              }>
                                <ListItemButton
                                  selected={entityType === selectedEntityType}
                                  onClick={() => setSelectedEntityType(entityType)}
                                >
                                  <ListItemText
                                    primary={entityType}
                                    secondary={`${splitTokens(entityEditors[entityType].searchableFields).length} searchable, ${splitTokens(entityEditors[entityType].embeddableFields).length} embeddable`}
                                  />
                                </ListItemButton>
                              </ListItem>
                            ))}
                          </List>
                        )}
                      </>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={7}>
              <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2.5}>
                    <Box>
                      <Typography variant="h6">Selected entity editor</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        Use comma-separated or newline-separated values. Metadata entries can be written as
                        <code> field:type </code> and default to <code>string</code> if the type is omitted.
                      </Typography>
                    </Box>

                    {selectedEntity ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip label={selectedEntity.entityType} color="primary" />
                          <Chip label={`${splitTokens(selectedEntity.searchableFields).length} searchable`} variant="outlined" />
                          <Chip label={`${splitTokens(selectedEntity.embeddableFields).length} embeddable`} variant="outlined" />
                          <Chip label={`${splitTokens(selectedEntity.metadataFields).length} metadata`} variant="outlined" />
                        </Stack>

                        <Grid container spacing={2}>
                          <Grid item xs={12}>
                            <TextField
                              multiline
                              minRows={4}
                              fullWidth
                              label="Searchable fields"
                              value={selectedEntity.searchableFields}
                              onChange={(event) => updateSelectedEntity({ searchableFields: event.target.value })}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <TextField
                              multiline
                              minRows={4}
                              fullWidth
                              label="Embeddable fields"
                              value={selectedEntity.embeddableFields}
                              onChange={(event) => updateSelectedEntity({ embeddableFields: event.target.value })}
                            />
                          </Grid>
                          <Grid item xs={12}>
                            <TextField
                              multiline
                              minRows={4}
                              fullWidth
                              label="Metadata fields"
                              value={selectedEntity.metadataFields}
                              onChange={(event) => updateSelectedEntity({ metadataFields: event.target.value })}
                            />
                          </Grid>
                        </Grid>
                      </>
                    ) : (
                      <Alert severity="info">Select or add an entity type to edit its fields.</Alert>
                    )}

                    {formError ? <Alert severity="error">{formError}</Alert> : null}
                    {saveMutation.isError ? (
                      <Alert severity="error">
                        {saveMutation.error instanceof Error
                          ? saveMutation.error.message
                          : 'Failed to save entity config'}
                      </Alert>
                    ) : null}
                    {saveMutation.isSuccess ? (
                      <Alert severity="success">Entity config draft saved.</Alert>
                    ) : null}

                    <Stack direction="row" spacing={1.5}>
                      <Button
                        variant="contained"
                        startIcon={<SaveRoundedIcon />}
                        onClick={handleSave}
                        disabled={saveMutation.isPending || draftQuery.isLoading}
                      >
                        {saveMutation.isPending ? 'Saving...' : 'Save entity config'}
                      </Button>
                      <Button variant="outlined" onClick={resetForm}>
                        Reset form
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Knowledge summary</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Snapshot of the current knowledge configuration state in the editor.
                  </Typography>
                </Box>

                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip label={`Vector dimensions: ${summary.vectorDimensions}`} color="primary" />
                  <Chip label={`${summary.entityCount} entity types`} variant="outlined" />
                  <Chip label={`${summary.searchableFieldCount} searchable fields`} variant="outlined" />
                  <Chip label={`${summary.embeddableFieldCount} embeddable fields`} variant="outlined" />
                  <Chip label={`${summary.metadataFieldCount} metadata fields`} variant="outlined" />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}
    </Stack>
  )
}
