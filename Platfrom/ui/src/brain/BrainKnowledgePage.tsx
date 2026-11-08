import { useEffect, useState } from 'react'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, List, ListItemButton, ListItemIcon,
  ListItemText, Stack, Tab, TablePagination, Tabs, TextField, Typography, useMediaQuery, useTheme } from '@mui/material'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import SyncOutlined from '@mui/icons-material/SyncOutlined'
import AddRounded from '@mui/icons-material/AddRounded'
import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import { fetchDeploymentIntegrations, fetchDeploymentTargetContext, fetchDocumentSources } from '../api/platformApi'
import { DocumentKnowledgePage } from '../pages/DocumentKnowledgePage'
import { IntegrationsPage } from '../pages/IntegrationsPage'
import { ProvidersPage } from '../pages/ProvidersPage'
import { useBrainWorkspace, useBrainEditor } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

const tasks = {
  storage: ['Storage & uploads', 'Choose hosted storage or connect your own S3-compatible source.'],
  discover: ['Add approved documents', 'Find files in your connected storage and approve them for indexing.'],
  proof: ['Check active evidence', 'Inspect the active evidence available to this environment.'],
  retention: ['Indexing evidence retention', 'Manage derived lifecycle evidence, not your source files.'],
} as const

export function BrainKnowledgePage() {
  const { brainId } = useBrainWorkspace()
  const location = useLocation()
  if (location.pathname.split('/').slice(4).join('/') === 'search') return <Box sx={{ minWidth: 0, minHeight: 0, flex: 1, display: 'flex', flexDirection: 'column' }}>
    <BrainPageHeader title="Find the right knowledge" description="Choose your search service, embedding model and customer data boundary." />
    <BrainKnowledgeTabs view="search" />
    <ProvidersPage brainId={brainId} configurationOnly view="storage" />
  </Box>
  return <BrainKnowledgeSources />
}

function BrainKnowledgeTabs({ view, counts }: { view: string; counts?: { all: number | null; documents: number | null; connections: number | null } }) {
  const { href } = useBrainWorkspace()
  const label = (name: string, count: number | null | undefined) => <Stack component="span" direction="row" gap={0.75} alignItems="center">
    <span>{name}</span>{counts && <Typography component="span" aria-hidden="true" sx={{ fontSize: 11, color: 'text.secondary', minWidth: 18, fontVariantNumeric: 'tabular-nums' }}>{count ?? '\u2014'}</Typography>}
  </Stack>
  return <Tabs value={view} variant="scrollable" scrollButtons="auto" aria-label="Knowledge views" sx={{ borderBottom: 1, borderColor: 'divider', mb: 2, flexShrink: 0 }}>
    <Tab component={RouterLink} to={href('knowledge')} value="" aria-label="All sources" label={label('All sources', counts?.all)} />
    <Tab component={RouterLink} to={href('knowledge/documents')} value="documents" aria-label="Documents" label={label('Documents', counts?.documents)} />
    <Tab component={RouterLink} to={href('knowledge/connections')} value="connections" aria-label="Live data" label={label('Live data', counts?.connections)} />
    <Tab component={RouterLink} to={href('knowledge/storage')} value="storage" label="Storage & uploads" />
    <Tab component={RouterLink} to={href('knowledge/search')} value="search" label="Search settings" />
  </Tabs>
}

const sourceViews = {
  all: { title: 'Your AI knowledge', description: 'Documents and live information available in this environment.', find: 'Find a source',
    empty: 'No knowledge sources yet', detail: 'Bring your knowledge into the conversation',
    guidance: 'Upload approved documents or connect a live system. Select a source to inspect its indexing or sync status.' },
  documents: { title: 'Documents for your AI', description: 'Review guides, policies, indexing status and active versions in this environment.', find: 'Find a document',
    empty: 'No document sources yet', detail: 'Give your AI approved guides and policies',
    guidance: 'Upload files or discover documents in connected storage, then preview and approve them for indexing. Uploading a file alone does not make it searchable.' },
  connections: { title: 'Live information for your AI', description: 'Monitor connected systems and keep their indexed information current.', find: 'Find a live source',
    empty: 'No live data sources yet', detail: 'Keep your AI connected to current information',
    guidance: 'Connect a data capability to sync an approved system. Select a live source to inspect freshness, sync activity and inbound events.' },
} as const

function BrainKnowledgeSources() {
  const { target, release, targetProfileId, href } = useBrainWorkspace()
  const location = useLocation()
  const parts = location.pathname.split('/').slice(4)
  const task = parts[0] ?? ''
  let sourceId = '', invalidPath = false
  try { sourceId = parts[1] ? decodeURIComponent(parts[1]) : '' } catch { invalidPath = true }
  if (parts.length > 2 || (sourceId && !['sources', 'connections'].includes(task)) || (task === 'sources' && !sourceId)) invalidPath = true
  const view = task === 'connections' ? 'connections' : task === 'documents' || task === 'sources' ? 'documents' : task === 'storage' ? 'storage' : ''
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const compact = useMediaQuery(useTheme().breakpoints.down('lg'))
  useEffect(() => { setPage(0) }, [view, search])
  const [sourcePath, sourceQuery] = href('capabilities/add').split('?')
  const sourceParams = new URLSearchParams(sourceQuery)
  sourceParams.set('type', 'DATA')
  const context = useQuery({ queryKey: ['deployment-target-context', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDeploymentTargetContext(target!), enabled: Boolean(target), retry: false })
  const documents = useQuery({ queryKey: ['document-sources', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDocumentSources(target!), enabled: Boolean(target && context.data?.documentDatasetIds.length), retry: false })
  const integrations = useQuery({ queryKey: ['deployment-integrations', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDeploymentIntegrations(target!), enabled: Boolean(target && context.data?.externalIntegrationConfigured), retry: false })
  const documentCount = !target || !context.isSuccess ? null : !context.data.documentDatasetIds.length ? 0 : documents.isSuccess && !documents.isError ? documents.data.length : null
  const connectionCount = !target || !context.isSuccess ? null : !context.data.externalIntegrationConfigured ? 0 : integrations.isSuccess && !integrations.isError ? integrations.data.sources.length : null
  const counts = { documents: documentCount, connections: connectionCount, all: documentCount === null || connectionCount === null ? null : documentCount + connectionCount }
  const presentation = sourceViews[view === 'documents' || view === 'connections' ? view : 'all']
  const rows = [
    ...(documents.data ?? []).map(item => ({ id: item.sourceId, label: item.displayName, kind: 'Document',
      status: item.status, updated: item.updatedAt, error: item.failureMessage,
      detail: item.activeVersion ? `Indexed version ${item.activeVersion}` : 'Not indexed yet', path: `knowledge/sources/${encodeURIComponent(item.sourceId)}`, Icon: DescriptionOutlined })),
    ...(integrations.data?.sources ?? []).map(item => ({ id: item.sourceId, label: item.sourceId, kind: 'Live data',
      status: item.state.status, updated: item.state.lastSuccessAt, error: item.state.errorMessage,
      detail: `${item.state.counts.indexedCount} indexed records`, path: `knowledge/connections/${encodeURIComponent(item.sourceId)}`, Icon: SyncOutlined })),
  ].filter(item => (view !== 'documents' || item.kind === 'Document') && (view !== 'connections' || item.kind === 'Live data'))
  const visible = rows.filter(item => `${item.label} ${item.kind}`.toLowerCase().includes(search.toLowerCase()))
  const selected = rows.find(item => item.id === sourceId && item.kind === (task === 'connections' ? 'Live data' : 'Document'))
  const error = context.error ?? (view === 'connections' ? integrations.error : view === 'documents' ? documents.error : documents.error ?? integrations.error)
  const count = view === 'documents' ? counts.documents : view === 'connections' ? counts.connections : counts.all
  const loading = Boolean(target) && (context.isPending || !context.isError && count === null && !error)
  const sourceQueryReady = task === 'connections' ? integrations.isSuccess || context.isSuccess && !context.data.externalIntegrationConfigured
    : documents.isSuccess || context.isSuccess && !context.data.documentDatasetIds.length
  const taskPage = Object.prototype.hasOwnProperty.call(tasks, task)
  const hasDetail = Boolean(sourceId || taskPage)
  const safePage = Math.min(page, Math.max(0, Math.ceil(visible.length / 7) - 1))
  const backPath = task === 'connections' ? 'knowledge/connections' : 'knowledge/documents'

  if (invalidPath || task && !['documents', 'sources', 'connections'].includes(task) && !taskPage) return <Alert severity="warning">This knowledge page is not available. <Button component={RouterLink} to={href('knowledge')}>Open knowledge</Button></Alert>
  return <>
    <BrainPageHeader stackOnMobile title={presentation.title} description={presentation.description}
      action={<Stack direction="row" gap={1} flexWrap="wrap" justifyContent="flex-end">
        {view !== 'documents' && <Button component={RouterLink} to={sourcePath + '?' + sourceParams} startIcon={<AddRounded />} variant={view === 'connections' ? 'contained' : 'outlined'}>Connect live data</Button>}
        {view !== 'connections' && <Button component={RouterLink} to={href('knowledge/storage')} startIcon={<CloudUploadOutlined />} variant="contained">Upload documents</Button>}
      </Stack>} />
    <BrainKnowledgeTabs view={view} counts={counts} />
    {error && <Alert severity="error" sx={{ mb: 1 }}>{error.message}</Alert>}
    {!target && !taskPage && <Alert severity="info" sx={{ mb: 2 }}>No applied release is available here. You can prepare storage and capabilities before publishing a version. <Button component={RouterLink} to={href('updates')}>Review setup</Button></Alert>}
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0, gap: 3, flexDirection: { xs: 'column', lg: 'row' } }}>
      {(!compact || !hasDetail) && !taskPage && <Box sx={{ width: { lg: 330 }, flexShrink: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <TextField size="small" label={presentation.find} value={search} onChange={event => setSearch(event.target.value)} sx={{ mb: 1.5 }} />
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>{count === null ? target ? 'Source count unavailable until loaded' : 'No applied source inventory' : `${count} ${view === 'documents' ? 'document' : view === 'connections' ? 'live data' : 'knowledge'} ${count === 1 ? 'source' : 'sources'}${search ? ` / ${visible.length} matching` : ''}`}</Typography>
        {loading && <CircularProgress size={24} aria-label="Loading knowledge" />}
        {!loading && !error && target && !visible.length && <Box sx={{ py: 2 }}>
          <Typography variant="body2" fontWeight={600}>{search ? 'No matching sources' : presentation.empty}</Typography>
          {search && <Button size="small" onClick={() => setSearch('')} sx={{ mt: 0.5 }}>Clear search</Button>}
        </Box>}
        <List disablePadding sx={{ ...brainPaneSx, flex: 1 }} aria-label="Knowledge sources">
          {visible.slice(safePage * 7, safePage * 7 + 7).map(row => <ListItemButton key={row.kind + ':' + row.id} component={RouterLink} to={href(row.path)} selected={selected?.path === row.path}
            sx={{ py: 1.5, px: 1.25, borderBottom: 1, borderColor: 'divider', gap: 1.25, alignItems: 'flex-start' }}>
            <ListItemIcon sx={{ minWidth: 22, mt: 0.25 }}><row.Icon fontSize="small" color="primary" /></ListItemIcon>
            <ListItemText primary={row.label} secondary={<Stack component="span" gap={0.5} sx={{ mt: 0.75 }}>
              <Typography component="span" variant="caption" color={row.error ? 'error.main' : 'text.secondary'}>{row.error ?? (row.kind + ' / ' + row.detail)}</Typography>
              <Stack component="span" direction="row" gap={1} alignItems="center"><Chip size="small" label={row.status} color={row.error ? 'error' : 'default'} variant="outlined" />
                <Typography component="span" variant="caption" color="text.secondary">{row.updated ? new Date(row.updated).toLocaleDateString() : 'No sync yet'}</Typography></Stack>
            </Stack>} primaryTypographyProps={{ fontSize: 13, fontWeight: 600, sx: { overflowWrap: 'anywhere' } }} secondaryTypographyProps={{ component: 'span' }} />
          </ListItemButton>)}
        </List>
        {visible.length > 0 && <TablePagination component="div" count={visible.length} page={safePage} rowsPerPage={7} rowsPerPageOptions={[7]}
          labelDisplayedRows={count === null ? ({ from, to }) => `${from}-${to} loaded` : undefined} onPageChange={(_, value) => setPage(value)} />}
      </Box>}
      <Box component="section" aria-label="Knowledge details" sx={{ ...brainPaneSx, flex: 1, pb: 9, borderLeft: { lg: !taskPage ? 1 : 0 }, borderColor: 'divider', pl: { lg: !taskPage ? 3 : 0 } }}>
        {compact && sourceId && <Button component={RouterLink} to={href(backPath)} startIcon={<ArrowBackRounded />} sx={{ mb: 2 }}>Back to sources</Button>}
        {taskPage ? <>
          <Stack direction="row" gap={2} justifyContent="space-between" sx={{ mb: 2 }}><Box><Typography component="h2" fontSize={20} fontWeight={650}>{tasks[task as keyof typeof tasks][0]}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{tasks[task as keyof typeof tasks][1]}</Typography></Box>
            <Button component={RouterLink} to={href('knowledge')} startIcon={<ArrowBackRounded />}>All sources</Button></Stack>
          {!release && task !== 'storage' ? <Alert severity="info">Choose an applied environment before using source operations.</Alert> :
            <DocumentKnowledgePage key={task} initialReleaseId={release?.id} initialTargetProfileId={targetProfileId} lockReleaseSelection workspaceView={task as keyof typeof tasks} />}
        </> : sourceId && sourceQueryReady && !selected ? <Alert severity="error">The requested source is not available in this environment. No other source has been selected.</Alert>
          : sourceId && selected && release ? task === 'connections'
            ? <BrainLiveSourceDetail key={sourceId} sourceId={sourceId} releaseId={release.id} />
            : <DocumentKnowledgePage key={sourceId} initialReleaseId={release.id} initialTargetProfileId={targetProfileId} initialSourceId={sourceId} lockReleaseSelection workspaceView="source" />
          : sourceId && target && !sourceQueryReady ? <CircularProgress size={24} aria-label="Loading source" /> : <Stack spacing={2} sx={{ maxWidth: 540, pt: 2 }}>
            <Typography component="h2" fontSize={20} fontWeight={600}>{presentation.detail}</Typography>
            <Typography variant="body2" color="text.secondary">{presentation.guidance}</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {view !== 'connections' && <Button component={RouterLink} to={href('knowledge/discover')} startIcon={<DescriptionOutlined />}>Add stored documents</Button>}
              <Button component={RouterLink} to={href('knowledge/proof')} startIcon={<FactCheckOutlined />}>Check active evidence</Button></Stack>
            {view !== 'connections' && <Button component={RouterLink} to={href('knowledge/retention')} sx={{ alignSelf: 'flex-start' }}>Indexing retention settings</Button>}
          </Stack>}
      </Box>
    </Box>
  </>
}

function BrainLiveSourceDetail({ sourceId, releaseId }: { sourceId: string; releaseId: string }) {
  const [busy, setBusy] = useState(false)
  useBrainEditor({ dirty: false, busy, message: 'Live source commands use this environment and its selected applied release.' })
  return <IntegrationsPage initialReleaseId={releaseId} initialSourceId={sourceId} dataOnly lockReleaseSelection workspaceView onBusyChange={setBusy} />
}
