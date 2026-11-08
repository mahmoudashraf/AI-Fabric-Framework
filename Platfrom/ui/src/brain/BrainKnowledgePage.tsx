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
import { useBrainWorkspace, useBrainEditor } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

const tasks = {
  storage: ['Storage & uploads', 'Choose hosted storage or connect your own S3-compatible source.'],
  discover: ['Add approved documents', 'Find files in your connected storage and approve them for indexing.'],
  proof: ['Check active evidence', 'Inspect the active evidence available to this environment.'],
  retention: ['Indexing evidence retention', 'Manage derived lifecycle evidence, not your source files.'],
} as const

export function BrainKnowledgePage() {
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
  const error = context.error ?? documents.error ?? integrations.error
  const sourceQueryReady = task === 'connections' ? integrations.isSuccess || context.isSuccess && !context.data.externalIntegrationConfigured
    : documents.isSuccess || context.isSuccess && !context.data.documentDatasetIds.length
  const taskPage = Object.prototype.hasOwnProperty.call(tasks, task)
  const hasDetail = Boolean(sourceId || taskPage)
  const safePage = Math.min(page, Math.max(0, Math.ceil(visible.length / 7) - 1))
  const backPath = task === 'connections' ? 'knowledge/connections' : 'knowledge/documents'

  if (invalidPath || task && !['documents', 'sources', 'connections'].includes(task) && !taskPage) return <Alert severity="warning">This knowledge page is not available. <Button component={RouterLink} to={href('knowledge')}>Open knowledge</Button></Alert>
  return <>
    <BrainPageHeader stackOnMobile title="What your AI knows" description="Keep answers grounded in your documents and live information."
      action={<Stack direction="row" gap={1} flexWrap="wrap" justifyContent="flex-end"><Button component={RouterLink} to={sourcePath + '?' + sourceParams} startIcon={<AddRounded />} variant="outlined">Connect a source</Button>
        <Button component={RouterLink} to={href('knowledge/storage')} startIcon={<CloudUploadOutlined />} variant="contained">Upload documents</Button></Stack>} />
    <Tabs value={view} variant="scrollable" scrollButtons="auto" aria-label="Knowledge views" sx={{ borderBottom: 1, borderColor: 'divider', mb: 2, flexShrink: 0 }}>
      <Tab component={RouterLink} to={href('knowledge')} value="" label="All sources" />
      <Tab component={RouterLink} to={href('knowledge/documents')} value="documents" label="Documents" />
      <Tab component={RouterLink} to={href('knowledge/connections')} value="connections" label="Live data" />
      <Tab component={RouterLink} to={href('knowledge/storage')} value="storage" label="Storage & uploads" />
    </Tabs>
    {error && <Alert severity="error" sx={{ mb: 1 }}>{error.message}</Alert>}
    {!target && !taskPage && <Alert severity="info" sx={{ mb: 2 }}>No applied release is available here. You can prepare storage and capabilities before publishing a version. <Button component={RouterLink} to={href('updates')}>Review setup</Button></Alert>}
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0, gap: 3, flexDirection: { xs: 'column', lg: 'row' } }}>
      {(!compact || !hasDetail) && !taskPage && <Box sx={{ width: { lg: 330 }, flexShrink: 0, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <TextField size="small" label="Find a source" value={search} onChange={event => setSearch(event.target.value)} sx={{ mb: 1.5 }} />
        {target && context.isPending && <CircularProgress size={24} aria-label="Loading knowledge" />}
        {context.isSuccess && !visible.length && !documents.isFetching && !integrations.isFetching && <Alert severity="info">No sources match this view. Upload documents or connect an approved source.</Alert>}
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
        <TablePagination component="div" count={visible.length} page={safePage} rowsPerPage={7} rowsPerPageOptions={[7]} onPageChange={(_, value) => setPage(value)} />
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
            <Typography component="h2" fontSize={20} fontWeight={600}>Your knowledge, kept current</Typography>
            <Typography variant="body2" color="text.secondary">Select a source to inspect its status, preview documents or sync live information. Source files remain in your storage.</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap"><Button component={RouterLink} to={href('knowledge/discover')} startIcon={<DescriptionOutlined />}>Add stored documents</Button>
              <Button component={RouterLink} to={href('knowledge/proof')} startIcon={<FactCheckOutlined />}>Check active evidence</Button></Stack>
            <Button component={RouterLink} to={href('knowledge/retention')} sx={{ alignSelf: 'flex-start' }}>Indexing retention settings</Button>
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
