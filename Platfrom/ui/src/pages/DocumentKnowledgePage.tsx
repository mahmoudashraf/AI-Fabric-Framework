import CloudOutlinedIcon from '@mui/icons-material/CloudOutlined'
import DeleteSweepRoundedIcon from '@mui/icons-material/DeleteSweepRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import DescriptionRoundedIcon from '@mui/icons-material/DescriptionRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import FolderOpenRoundedIcon from '@mui/icons-material/FolderOpenRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import PreviewRoundedIcon from '@mui/icons-material/PreviewRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  Link,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  deleteDocumentStorageBinding,
  cleanupDocumentRetention,
  discoverDocumentSources,
  fetchDeploymentDraft,
  fetchDeploymentMarketplaceInstalls,
  fetchDeploymentTargetProfiles,
  fetchDocumentConnectorStatus,
  fetchDocumentRetentionStatus,
  fetchDocumentSourceDetail,
  fetchDocumentSources,
  fetchDocumentStorageBindings,
  indexDocumentSource,
  previewDocumentSource,
  reconcileDocumentSource,
  refreshDocumentSource,
  registerDocumentSource,
  removeDocumentIndex,
  resolveDeploymentMarketplaceInstall,
  updateDeploymentMarketplaceInstall,
  upsertDocumentStorageBinding,
  verifyDocumentRetrieval,
  type DeploymentMarketplaceInstallSummary,
  type DocumentSourceSummary,
  type UpsertDocumentStorageBindingRequest,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const DOCUMENT_PLUGIN_IDS = new Set([
  'mkp-data-document-knowledge-s3',
  'mkp-data-document-knowledge-mounted-demo',
])

type BindingForm = {
  connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE' | 'MOUNTED_FOLDER'
  targetProfileId: string
  endpoint: string
  region: string
  bucket: string
  prefix: string
  accessKey: string
  secretKey: string
  sessionToken: string
  pathStyleAccess: boolean
  objectVersioningAvailable: boolean
  allowInsecureEndpoint: boolean
}

const emptyBindingForm: BindingForm = {
  connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE',
  targetProfileId: '',
  endpoint: '',
  region: 'us-east-1',
  bucket: '',
  prefix: '',
  accessKey: '',
  secretKey: '',
  sessionToken: '',
  pathStyleAccess: true,
  objectVersioningAvailable: false,
  allowInsecureEndpoint: false,
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function asRecord(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {}
}

function documentDataset(config: unknown): Record<string, unknown> | null {
  const datasets = asRecord(config).datasets
  if (!Array.isArray(datasets)) return null
  return datasets.find((value) => (
    isRecord(value) && value.ingestionMode === 'EXTERNAL_DOCUMENT_STORAGE'
  )) as Record<string, unknown> | undefined ?? null
}

function documentInstall(
  installs: DeploymentMarketplaceInstallSummary[],
  dataset: Record<string, unknown> | null,
): DeploymentMarketplaceInstallSummary | null {
  const installId = typeof dataset?.marketplaceInstallId === 'string' ? dataset.marketplaceInstallId : ''
  return installs.find((install) => install.id === installId)
    ?? installs.find((install) => DOCUMENT_PLUGIN_IDS.has(install.pluginId))
    ?? null
}

function statusColor(status: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  const value = (status ?? '').toUpperCase()
  if (['READY', 'ACTIVE', 'COMPLETED', 'SUCCEEDED', 'DELETED'].includes(value)) return 'success'
  if (['FAILED', 'DELETE_FAILED', 'ERROR', 'UNAVAILABLE'].includes(value)) return 'error'
  if (value) return 'warning'
  return 'default'
}

function formatBytes(value: number): string {
  if (!Number.isFinite(value) || value < 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : 'Operation failed.'
}

function configWithBinding(config: unknown, bindingRef: string): Record<string, unknown> {
  return { ...asRecord(config), documentStorageBindingRef: bindingRef }
}

export function DocumentKnowledgePage() {
  const queryClient = useQueryClient()
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const [bindingForm, setBindingForm] = useState<BindingForm>(emptyBindingForm)
  const [selectedSourceId, setSelectedSourceId] = useState('')
  const [discoveryCursor, setDiscoveryCursor] = useState<string | undefined>()
  const [retrievalQuery, setRetrievalQuery] = useState('Summarize the approved document evidence.')
  const [removeSource, setRemoveSource] = useState<DocumentSourceSummary | null>(null)

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const installsQuery = useQuery({
    queryKey: ['deployment-marketplace-installs', selectedDeploymentId],
    queryFn: () => fetchDeploymentMarketplaceInstalls(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const profilesQuery = useQuery({
    queryKey: ['deployment-target-profiles'],
    queryFn: () => fetchDeploymentTargetProfiles(),
    enabled: selectedDeploymentId.length > 0,
  })
  const bindingsQuery = useQuery({
    queryKey: ['document-storage-bindings', selectedDeploymentId],
    queryFn: () => fetchDocumentStorageBindings(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const dataset = useMemo(
    () => documentDataset(draftQuery.data?.marketplaceDatasetConfig),
    [draftQuery.data?.marketplaceDatasetConfig],
  )
  const datasetId = typeof dataset?.datasetId === 'string' ? dataset.datasetId : 'document-knowledge'
  const install = useMemo(
    () => documentInstall(installsQuery.data ?? [], dataset),
    [dataset, installsQuery.data],
  )
  const matchingProfiles = useMemo(() => (
    (profilesQuery.data ?? []).filter((profile) => (
      profile.active
      && (!selectedDeploymentSummary?.environment
        || !profile.environmentName
        || profile.environmentName.toLowerCase() === selectedDeploymentSummary.environment.toLowerCase())
    ))
  ), [profilesQuery.data, selectedDeploymentSummary?.environment])

  useEffect(() => {
    if (!bindingForm.targetProfileId && matchingProfiles.length > 0) {
      setBindingForm((current) => ({ ...current, targetProfileId: matchingProfiles[0].id }))
    }
  }, [bindingForm.targetProfileId, matchingProfiles])

  const documentConfigured = workspace?.documentKnowledgeConfigured ?? dataset != null
  const documentLive = workspace?.documentKnowledgeLive ?? false
  const canAdmin = workspace?.access.canAdmin ?? false
  const canOperate = workspace?.access.canOperate ?? false

  const connectorQuery = useQuery({
    queryKey: ['document-connector-status', selectedDeploymentId],
    queryFn: () => fetchDocumentConnectorStatus(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && documentLive,
    retry: false,
  })
  const sourcesQuery = useQuery({
    queryKey: ['document-sources', selectedDeploymentId],
    queryFn: () => fetchDocumentSources(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && documentLive,
    refetchInterval: (query) => {
      const sources = query.state.data as DocumentSourceSummary[] | undefined
      return sources?.some((source) => ['INDEXING', 'REPLACING', 'DELETE_PENDING', 'DELETING'].includes(source.status))
        ? 3_000
        : false
    },
  })
  const retentionQuery = useQuery({
    queryKey: ['document-retention-status', selectedDeploymentId],
    queryFn: () => fetchDocumentRetentionStatus(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0 && documentLive,
    retry: false,
  })
  const detailQuery = useQuery({
    queryKey: ['document-source-detail', selectedDeploymentId, selectedSourceId],
    queryFn: () => fetchDocumentSourceDetail(selectedDeploymentId, selectedSourceId),
    enabled: selectedDeploymentId.length > 0 && selectedSourceId.length > 0 && documentLive,
    refetchInterval: 4_000,
  })

  const invalidateDocumentState = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['document-sources', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['document-source-detail', selectedDeploymentId] }),
      queryClient.invalidateQueries({ queryKey: ['document-connector-status', selectedDeploymentId] }),
    ])
  }

  const saveBindingMutation = useMutation({
    mutationFn: async () => {
      const payload: UpsertDocumentStorageBindingRequest = bindingForm.connectorType === 'MOUNTED_FOLDER'
        ? {
            targetProfileId: bindingForm.targetProfileId,
            connectorType: 'MOUNTED_FOLDER',
            mountedRoot: '/app/document-sources',
          }
        : {
            targetProfileId: bindingForm.targetProfileId,
            connectorType: 'S3_COMPATIBLE_OBJECT_STORAGE',
            endpoint: bindingForm.endpoint,
            region: bindingForm.region,
            bucket: bindingForm.bucket,
            prefix: bindingForm.prefix,
            accessKey: bindingForm.accessKey,
            secretKey: bindingForm.secretKey,
            sessionToken: bindingForm.sessionToken || undefined,
            pathStyleAccess: bindingForm.pathStyleAccess,
            objectVersioningAvailable: bindingForm.objectVersioningAvailable,
            allowInsecureEndpoint: bindingForm.allowInsecureEndpoint,
          }
      const binding = await upsertDocumentStorageBinding(selectedDeploymentId, payload)
      if (install) {
        await updateDeploymentMarketplaceInstall(selectedDeploymentId, install.id, {
          config: configWithBinding(install.config, binding.bindingRef),
        })
        await resolveDeploymentMarketplaceInstall(selectedDeploymentId, install.id)
      }
      return binding
    },
    onSuccess: async () => {
      setBindingForm((current) => ({ ...current, accessKey: '', secretKey: '', sessionToken: '' }))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['document-storage-bindings', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-marketplace-installs', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const deleteBindingMutation = useMutation({
    mutationFn: (bindingRef: string) => deleteDocumentStorageBinding(selectedDeploymentId, bindingRef),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['document-storage-bindings', selectedDeploymentId] }),
  })
  const discoveryMutation = useMutation({
    mutationFn: (cursor?: string) => discoverDocumentSources(selectedDeploymentId, {
      datasetId,
      cursor,
      limit: 50,
    }),
    onSuccess: (result) => setDiscoveryCursor(result.nextCursor ?? undefined),
  })
  const registerMutation = useMutation({
    mutationFn: (objectReference: string) => registerDocumentSource(selectedDeploymentId, {
      datasetId,
      objectReference,
      visibility: 'tenant',
    }),
    onSuccess: async (source) => {
      setSelectedSourceId(source.sourceId)
      await invalidateDocumentState()
    },
  })
  const previewMutation = useMutation({
    mutationFn: (sourceId: string) => previewDocumentSource(selectedDeploymentId, sourceId),
  })
  const commandMutation = useMutation({
    mutationFn: ({ sourceId, command }: { sourceId: string; command: 'refresh' | 'index' | 'reconcile' }) => {
      if (command === 'refresh') return refreshDocumentSource(selectedDeploymentId, sourceId)
      if (command === 'index') return indexDocumentSource(selectedDeploymentId, sourceId)
      return reconcileDocumentSource(selectedDeploymentId, sourceId)
    },
    onSuccess: invalidateDocumentState,
  })
  const removeMutation = useMutation({
    mutationFn: (sourceId: string) => removeDocumentIndex(selectedDeploymentId, sourceId),
    onSuccess: async () => {
      setRemoveSource(null)
      await invalidateDocumentState()
    },
  })
  const retrievalMutation = useMutation({
    mutationFn: () => verifyDocumentRetrieval(selectedDeploymentId, { query: retrievalQuery, limit: 8 }),
  })
  const retentionMutation = useMutation({
    mutationFn: () => cleanupDocumentRetention(selectedDeploymentId),
    onSuccess: () => queryClient.invalidateQueries({
      queryKey: ['document-retention-status', selectedDeploymentId],
    }),
  })

  if (!selectedDeploymentId) {
    return <Alert severity="info">Select a deployment to open Document Knowledge.</Alert>
  }
  if (draftQuery.isLoading || installsQuery.isLoading) {
    return <Stack alignItems="center" sx={{ py: 8 }}><CircularProgress /></Stack>
  }
  if (!documentConfigured) {
    return (
      <Alert severity="info">
        This deployment does not include the Document Knowledge DATA capability.{' '}
        <Link component={RouterLink} to="/marketplace">Open Marketplace</Link>
      </Alert>
    )
  }

  const selectedSource = detailQuery.data?.source
    ?? (sourcesQuery.data ?? []).find((source) => source.sourceId === selectedSourceId)
    ?? null

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800 }}>Document Knowledge</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>
            Files remain in your storage. LoomAI stores derived indexing state and active retrieval evidence.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip label={documentLive ? 'Live capability' : 'Configuration pending release'} color={documentLive ? 'success' : 'warning'} />
          <Chip label={datasetId} variant="outlined" />
        </Stack>
      </Stack>

      {!documentLive ? (
        <Alert severity="warning">
          Configure the target-scoped source binding, publish the draft, and apply a verified runtime before using document operations.
        </Alert>
      ) : null}

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2.5}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Source binding</Typography>
              <Typography variant="body2" color="text.secondary">Customer-owned storage, read-only connector authority.</Typography>
            </Box>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={bindingForm.connectorType}
              onChange={(_, value) => value && setBindingForm((current) => ({ ...current, connectorType: value }))}
              disabled={!canAdmin}
            >
              <ToggleButton value="S3_COMPATIBLE_OBJECT_STORAGE"><CloudOutlinedIcon sx={{ mr: 1 }} />S3 compatible</ToggleButton>
              <ToggleButton value="MOUNTED_FOLDER"><FolderOpenRoundedIcon sx={{ mr: 1 }} />Mounted demo</ToggleButton>
            </ToggleButtonGroup>
          </Stack>

          {bindingForm.connectorType === 'MOUNTED_FOLDER' ? (
            <Alert severity="warning">Demo / small data only. The read-only mount path is fixed at /app/document-sources.</Alert>
          ) : null}

          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField
                select fullWidth size="small" label="Target profile"
                value={bindingForm.targetProfileId}
                onChange={(event) => setBindingForm((current) => ({ ...current, targetProfileId: event.target.value }))}
                disabled={!canAdmin}
              >
                {matchingProfiles.map((profile) => (
                  <MenuItem key={profile.id} value={profile.id}>{profile.name} · {profile.providerType}</MenuItem>
                ))}
              </TextField>
            </Grid>
            {bindingForm.connectorType === 'S3_COMPATIBLE_OBJECT_STORAGE' ? (
              <>
                <Grid item xs={12} md={8}>
                  <TextField fullWidth size="small" label="Endpoint origin" placeholder="https://s3.example.com"
                    value={bindingForm.endpoint} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, endpoint: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Region" value={bindingForm.region} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, region: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Bucket" value={bindingForm.bucket} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, bucket: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Allowed prefix" value={bindingForm.prefix} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, prefix: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" label="Access key" value={bindingForm.accessKey} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, accessKey: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" type="password" label="Secret key" value={bindingForm.secretKey} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, secretKey: event.target.value }))} />
                </Grid>
                <Grid item xs={12} md={4}>
                  <TextField fullWidth size="small" type="password" label="Session token (optional)" value={bindingForm.sessionToken} disabled={!canAdmin}
                    onChange={(event) => setBindingForm((current) => ({ ...current, sessionToken: event.target.value }))} />
                </Grid>
                <Grid item xs={12}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                    <FormControlLabel control={<Checkbox checked={bindingForm.pathStyleAccess} onChange={(event) => setBindingForm((current) => ({ ...current, pathStyleAccess: event.target.checked }))} />} label="Path-style access" />
                    <FormControlLabel control={<Checkbox checked={bindingForm.objectVersioningAvailable} onChange={(event) => setBindingForm((current) => ({ ...current, objectVersioningAvailable: event.target.checked }))} />} label="Object versioning available" />
                    <FormControlLabel control={<Checkbox checked={bindingForm.allowInsecureEndpoint} onChange={(event) => setBindingForm((current) => ({ ...current, allowInsecureEndpoint: event.target.checked }))} />} label="Allow HTTP outside production" />
                  </Stack>
                </Grid>
              </>
            ) : null}
          </Grid>

          {saveBindingMutation.isError ? <Alert severity="error">{errorText(saveBindingMutation.error)}</Alert> : null}
          {saveBindingMutation.isSuccess ? <Alert severity="success">Binding saved and the DATA install draft was resolved.</Alert> : null}
          {!install ? <Alert severity="warning">The Document Knowledge DATA install is missing; binding configuration cannot be compiled into the deployment draft.</Alert> : null}
          <Stack direction="row" justifyContent="flex-end">
            <Button
              variant="contained" startIcon={<SaveRoundedIcon />}
              disabled={!canAdmin || !bindingForm.targetProfileId || saveBindingMutation.isPending}
              onClick={() => saveBindingMutation.mutate()}
            >
              Save binding
            </Button>
          </Stack>

          {(bindingsQuery.data ?? []).length > 0 ? <Divider /> : null}
          {(bindingsQuery.data ?? []).map((binding) => (
            <Stack key={binding.bindingRef} direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }} justifyContent="space-between">
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={binding.connectorType.replace(/_/g, ' ')} variant="outlined" />
                <Chip label={binding.targetProfileId} variant="outlined" />
                <Chip label={binding.credentialsPresent ? 'Credentials bound' : 'Credentials missing'} color={binding.credentialsPresent ? 'success' : 'error'} />
                <Chip label={binding.status} color={statusColor(binding.status)} />
              </Stack>
              <Tooltip title="Remove binding reference and managed credentials">
                <span>
                  <IconButton
                    aria-label="Remove document storage binding"
                    disabled={!canAdmin || deleteBindingMutation.isPending}
                    onClick={() => {
                      if (globalThis.confirm('Remove this binding and its managed credentials? Customer source objects will not be changed.')) {
                        deleteBindingMutation.mutate(binding.bindingRef)
                      }
                    }}
                  ><DeleteOutlineRoundedIcon /></IconButton>
                </span>
              </Tooltip>
            </Stack>
          ))}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2.5}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Connector and discovery</Typography>
              <Typography variant="body2" color="text.secondary">Eligible .txt and configured .json objects inside the approved scope.</Typography>
            </Box>
            <Stack direction="row" spacing={1}>
              <Chip
                label={connectorQuery.data?.ready ? 'Connector ready' : documentLive ? 'Connector unavailable' : 'Not live'}
                color={connectorQuery.data?.ready ? 'success' : 'warning'}
              />
              {connectorQuery.data?.connectorType ? <Chip label={connectorQuery.data.connectorType.replace(/_/g, ' ')} variant="outlined" /> : null}
              <Tooltip title="Refresh connector status"><span><IconButton disabled={!documentLive} onClick={() => connectorQuery.refetch()}><RefreshRoundedIcon /></IconButton></span></Tooltip>
            </Stack>
          </Stack>
          {connectorQuery.isError ? <Alert severity="error">{errorText(connectorQuery.error)}</Alert> : null}
          {connectorQuery.data?.errorCode ? <Alert severity="error">{connectorQuery.data.errorCode}</Alert> : null}
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined" startIcon={<SearchRoundedIcon />}
              disabled={!documentLive || !connectorQuery.data?.ready || discoveryMutation.isPending}
              onClick={() => discoveryMutation.mutate(undefined)}
            >Discover sources</Button>
            {discoveryCursor ? (
              <Button variant="text" onClick={() => discoveryMutation.mutate(discoveryCursor)}>Next page</Button>
            ) : null}
          </Stack>
          {discoveryMutation.isError ? <Alert severity="error">{errorText(discoveryMutation.error)}</Alert> : null}
          {discoveryMutation.data ? (
            <TableContainer>
              <Table size="small">
                <TableHead><TableRow><TableCell>Source</TableCell><TableCell>Type</TableCell><TableCell>Size</TableCell><TableCell>Modified</TableCell><TableCell>Status</TableCell><TableCell align="right">Action</TableCell></TableRow></TableHead>
                <TableBody>
                  {discoveryMutation.data.sources.map((source) => (
                    <TableRow key={source.objectReference}>
                      <TableCell>{source.displayName}</TableCell>
                      <TableCell>{source.mediaType}</TableCell>
                      <TableCell>{formatBytes(source.contentLength)}</TableCell>
                      <TableCell>{formatTime(source.lastModified)}</TableCell>
                      <TableCell><Chip size="small" label={source.registeredSourceId ? 'Registered' : 'Eligible'} color={source.registeredSourceId ? 'success' : 'default'} /></TableCell>
                      <TableCell align="right">
                        <Button
                          size="small" startIcon={<DescriptionRoundedIcon />}
                          disabled={!!source.registeredSourceId || !canOperate || registerMutation.isPending}
                          onClick={() => registerMutation.mutate(source.objectReference)}
                        >Register</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2.5}>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Registered sources</Typography>
              <Typography variant="body2" color="text.secondary">Queue acceptance and completed vector indexing remain distinct states.</Typography>
            </Box>
            <Tooltip title="Refresh sources"><span><IconButton disabled={!documentLive} onClick={() => sourcesQuery.refetch()}><RefreshRoundedIcon /></IconButton></span></Tooltip>
          </Stack>
          {sourcesQuery.isError ? <Alert severity="error">{errorText(sourcesQuery.error)}</Alert> : null}
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow><TableCell>Source</TableCell><TableCell>Status</TableCell><TableCell>Type</TableCell><TableCell>Size</TableCell><TableCell>Active</TableCell><TableCell>Candidate</TableCell><TableCell>Updated</TableCell></TableRow></TableHead>
              <TableBody>
                {(sourcesQuery.data ?? []).map((source) => (
                  <TableRow
                    hover key={source.sourceId} selected={source.sourceId === selectedSourceId}
                    onClick={() => { setSelectedSourceId(source.sourceId); previewMutation.reset() }}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>{source.displayName}</TableCell>
                    <TableCell><Chip size="small" label={source.status} color={statusColor(source.status)} /></TableCell>
                    <TableCell>{source.mediaType}</TableCell>
                    <TableCell>{formatBytes(source.contentLength)}</TableCell>
                    <TableCell>{source.activeVersion ?? '—'}</TableCell>
                    <TableCell>{source.candidateVersion}</TableCell>
                    <TableCell>{formatTime(source.updatedAt)}</TableCell>
                  </TableRow>
                ))}
                {(sourcesQuery.data ?? []).length === 0 ? <TableRow><TableCell colSpan={7}>No registered document sources.</TableCell></TableRow> : null}
              </TableBody>
            </Table>
          </TableContainer>

          {selectedSource ? (
            <>
              <Divider />
              <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={2}>
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>{selectedSource.displayName}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                    <Chip size="small" label={selectedSource.status} color={statusColor(selectedSource.status)} />
                    <Chip size="small" label={`Active v${selectedSource.activeVersion ?? '—'}`} variant="outlined" />
                    <Chip size="small" label={`Candidate v${selectedSource.candidateVersion}`} variant="outlined" />
                    <Chip size="small" label={`Revision ${selectedSource.providerRevisionFingerprint.slice(0, 12)}`} variant="outlined" />
                  </Stack>
                </Box>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Button size="small" variant="outlined" startIcon={<PreviewRoundedIcon />} disabled={previewMutation.isPending} onClick={() => previewMutation.mutate(selectedSource.sourceId)}>Preview</Button>
                  <Button size="small" variant="contained" startIcon={<PlayArrowRoundedIcon />} disabled={!canOperate || commandMutation.isPending} onClick={() => commandMutation.mutate({ sourceId: selectedSource.sourceId, command: 'index' })}>Index</Button>
                  <Button size="small" variant="outlined" startIcon={<SyncRoundedIcon />} disabled={!canOperate || commandMutation.isPending} onClick={() => commandMutation.mutate({ sourceId: selectedSource.sourceId, command: 'refresh' })}>Refresh revision</Button>
                  <Button size="small" variant="outlined" startIcon={<FactCheckRoundedIcon />} disabled={!canOperate || commandMutation.isPending} onClick={() => commandMutation.mutate({ sourceId: selectedSource.sourceId, command: 'reconcile' })}>Reconcile</Button>
                  <Button size="small" color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />} disabled={!canOperate || removeMutation.isPending} onClick={() => setRemoveSource(selectedSource)}>Remove index</Button>
                </Stack>
              </Stack>
              {selectedSource.failureCode ? <Alert severity="error">{selectedSource.failureCode}: {selectedSource.failureMessage}</Alert> : null}
              {commandMutation.isError ? <Alert severity="error">{errorText(commandMutation.error)}</Alert> : null}
              {commandMutation.data ? <Alert severity="success">Operation: {commandMutation.data.outcome}</Alert> : null}

              {previewMutation.data ? (
                <Stack spacing={1.5}>
                  <Typography variant="subtitle2">Bounded preview · {previewMutation.data.chunkCount} chunks · {previewMutation.data.totalContentLength} characters</Typography>
                  {previewMutation.data.chunks.map((chunk) => (
                    <Box key={chunk.chunkId} sx={{ borderLeft: '3px solid', borderColor: 'primary.main', pl: 2, py: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">Chunk {chunk.chunkIndex + 1}/{chunk.chunkCount}</Typography>
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{chunk.contentPreview}</Typography>
                    </Box>
                  ))}
                </Stack>
              ) : null}

              {(detailQuery.data?.manifests ?? []).length > 0 ? (
                <TableContainer>
                  <Table size="small">
                    <TableHead><TableRow><TableCell>Version</TableCell><TableCell>State</TableCell><TableCell>Chunks</TableCell><TableCell>Index work</TableCell><TableCell>Delete work</TableCell><TableCell>Failure</TableCell></TableRow></TableHead>
                    <TableBody>
                      {(detailQuery.data?.manifests ?? []).map((manifest) => (
                        <TableRow key={manifest.manifestId}>
                          <TableCell>v{manifest.sourceVersion}</TableCell>
                          <TableCell><Chip size="small" label={manifest.state} color={statusColor(manifest.state)} /></TableCell>
                          <TableCell>{manifest.chunkCount}</TableCell>
                          <TableCell>{manifest.work.filter((work) => work.operation === 'INDEX' && work.successful).length}/{manifest.acceptedIndexWorkCount}</TableCell>
                          <TableCell>{manifest.work.filter((work) => work.operation === 'DELETE' && work.successful).length}/{manifest.acceptedDeleteWorkCount}</TableCell>
                          <TableCell>{manifest.failureCode ?? '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              ) : null}
            </>
          ) : null}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 750 }}>Retrieval proof</Typography>
            <Typography variant="body2" color="text.secondary">Tenant-scoped active source, version, and chunk evidence.</Typography>
          </Box>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
            <TextField fullWidth size="small" label="Proof query" value={retrievalQuery} onChange={(event) => setRetrievalQuery(event.target.value)} />
            <Button variant="contained" startIcon={<SearchRoundedIcon />} disabled={!documentLive || !retrievalQuery.trim() || retrievalMutation.isPending} onClick={() => retrievalMutation.mutate()}>Verify</Button>
          </Stack>
          {retrievalMutation.isError ? <Alert severity="error">{errorText(retrievalMutation.error)}</Alert> : null}
          {retrievalMutation.data ? (
            <Stack spacing={1.5}>
              <Alert severity={retrievalMutation.data.evidenceCount > 0 ? 'success' : 'warning'}>
                {retrievalMutation.data.evidenceCount} active evidence result{retrievalMutation.data.evidenceCount === 1 ? '' : 's'} in {retrievalMutation.data.processingTimeMs} ms.
              </Alert>
              {retrievalMutation.data.evidence.map((item) => (
                <Box key={item.entityId} sx={{ borderLeft: '3px solid', borderColor: 'success.main', pl: 2 }}>
                  <Typography variant="subtitle2">{item.sourceName} · v{item.sourceVersion} · chunk {item.chunkIndex + 1}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{item.content}</Typography>
                </Box>
              ))}
            </Stack>
          ) : null}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5 }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>Runtime evidence retention</Typography>
              <Typography variant="body2" color="text.secondary">
                Terminal LoomAI lifecycle evidence only. Customer source files are never removed.
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<DeleteSweepRoundedIcon />}
              disabled={!documentLive || !canOperate || retentionMutation.isPending}
              onClick={() => retentionMutation.mutate()}
            >
              Run cleanup
            </Button>
          </Stack>
          {retentionQuery.isError ? <Alert severity="error">{errorText(retentionQuery.error)}</Alert> : null}
          {retentionMutation.isError ? <Alert severity="error">{errorText(retentionMutation.error)}</Alert> : null}
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label={`Manifest evidence ${retentionQuery.data?.evidenceRetention ?? '—'}`} variant="outlined" />
            <Chip label={`Idempotency evidence ${retentionQuery.data?.commandRetention ?? '—'}`} variant="outlined" />
            <Chip label={`Batch ${retentionQuery.data?.batchSize ?? '—'}`} variant="outlined" />
            <Chip
              label={`Last sweep ${formatTime(retentionQuery.data?.lastCleanup?.completedAt)}`}
              color={retentionQuery.data?.lastCleanup ? 'success' : 'default'}
            />
          </Stack>
          {retentionMutation.data ? (
            <Alert severity="success">
              Removed {retentionMutation.data.manifestsDeleted} expired manifests, {retentionMutation.data.workDeleted} work records, and {retentionMutation.data.commandsDeleted} command records. Customer files changed: no.
            </Alert>
          ) : null}
        </Stack>
      </Paper>

      <Dialog open={removeSource != null} onClose={() => setRemoveSource(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Remove indexed document evidence?</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mt: 1 }}>
            This deletes the exact indexed chunks and LoomAI registration for {removeSource?.displayName}. The customer source object is not deleted or changed.
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRemoveSource(null)}>Cancel</Button>
          <Button color="error" variant="contained" startIcon={<DeleteOutlineRoundedIcon />} disabled={removeMutation.isPending} onClick={() => removeSource && removeMutation.mutate(removeSource.sourceId)}>Remove index</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
