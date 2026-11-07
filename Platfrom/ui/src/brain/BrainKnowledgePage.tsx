import { useState } from 'react'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Divider, List, ListItemButton, ListItemIcon,
  ListItemText, Stack, Tab, TablePagination, Tabs, TextField, Typography } from '@mui/material'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import SyncOutlined from '@mui/icons-material/SyncOutlined'
import AddRounded from '@mui/icons-material/AddRounded'
import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import { fetchDeploymentIntegrations, fetchDeploymentTargetContext, fetchDocumentSources } from '../api/platformApi'
import { DocumentKnowledgePage } from '../pages/DocumentKnowledgePage'
import { IntegrationsPage } from '../pages/IntegrationsPage'
import { useBrainWorkspace, useBrainEditor } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

const tasks = {
  storage: ['Upload documents & storage', 'Choose hosted storage or connect your own S3-compatible source.'],
  discover: ['Add approved documents', 'Find files in your connected storage and approve them for indexing.'],
  proof: ['Check your knowledge', 'Inspect the active evidence available to this environment.'],
  retention: ['Indexing evidence retention', 'Manage derived lifecycle evidence, not your source files.'],
} as const

export function BrainKnowledgePage() {
  const { target, release, targetProfileId, href } = useBrainWorkspace()
  const location = useLocation()
  const parts = location.pathname.split('/').slice(4)
  const task = parts[0] ?? ''
  const sourceId = task === 'sources' && parts[1] ? decodeURIComponent(parts[1]) : ''
  const [view, setView] = useState('sources')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [connectionBusy, setConnectionBusy] = useState(false)
  const [sourcePath, sourceQuery] = href('capabilities/add').split('?')
  const sourceParams = new URLSearchParams(sourceQuery)
  sourceParams.set('type', 'DATA')
  useBrainEditor(task === 'connections' ? { dirty: false, busy: connectionBusy, message: 'Connection operations use the selected applied release.' } : null)
  const context = useQuery({ queryKey: ['deployment-target-context', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDeploymentTargetContext(target!), enabled: Boolean(target), retry: false })
  const documents = useQuery({ queryKey: ['document-sources', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDocumentSources(target!), enabled: Boolean(target && context.data?.documentDatasetIds.length), retry: false })
  const integrations = useQuery({ queryKey: ['deployment-integrations', target?.deploymentId, target?.targetProfileId, target?.releaseId],
    queryFn: () => fetchDeploymentIntegrations(target!), enabled: Boolean(target && context.data?.externalIntegrationConfigured), retry: false })

  if (task && task !== 'sources' && task !== 'connections' && !(task in tasks)) return <Alert severity="warning">This knowledge page is not available. <Button component={RouterLink} to={href('knowledge')}>Open knowledge</Button></Alert>
  if (task) {
    const title = task === 'sources' ? 'Document indexing' : task === 'connections' ? 'Live data connections' : tasks[task as keyof typeof tasks][0]
    return <><BrainPageHeader title={title} action={<Button component={RouterLink} to={href('knowledge')} startIcon={<ArrowBackRounded />}>All knowledge</Button>} />
      <Box sx={brainPaneSx}>{!release && task !== 'storage' ? <Alert severity="info">Choose an applied environment before using source operations. <Button component={RouterLink} to={href('updates')}>Review setup</Button></Alert>
        : task === 'connections' ? <IntegrationsPage initialReleaseId={release?.id} lockReleaseSelection workspaceView onBusyChange={setConnectionBusy} />
          : <DocumentKnowledgePage key={`${task}:${sourceId}`} initialReleaseId={release?.id} initialTargetProfileId={targetProfileId} initialSourceId={sourceId} lockReleaseSelection
            workspaceView={task === 'sources' ? 'source' : task as keyof typeof tasks} />}</Box></>
  }

  const rows = [
    ...(documents.data ?? []).map(item => ({ id: item.sourceId, label: item.displayName, kind: 'Document',
      status: item.status, updated: item.updatedAt, error: item.failureMessage,
      detail: item.activeVersion ? `Indexed version ${item.activeVersion}` : 'Not indexed yet', path: `knowledge/sources/${encodeURIComponent(item.sourceId)}`, Icon: DescriptionOutlined })),
    ...(integrations.data?.sources ?? []).map(item => ({ id: item.sourceId, label: item.sourceId, kind: 'Live data',
      status: item.state.status, updated: item.state.lastSuccessAt, error: item.state.errorMessage,
      detail: `${item.state.counts.indexedCount} indexed records`, path: 'knowledge/connections', Icon: SyncOutlined })),
  ].filter(item => (view !== 'documents' || item.kind === 'Document') && `${item.label} ${item.kind}`.toLowerCase().includes(search.toLowerCase()))
  const error = context.error ?? documents.error ?? integrations.error
  const ready = context.isSuccess
  return <>
    <BrainPageHeader title="What your AI knows" description="Your approved documents and connected live information."
      action={<Stack direction="row" gap={1}><Button component={RouterLink} to={`${sourcePath}?${sourceParams}`} startIcon={<AddRounded />} variant="outlined">Connect a source</Button>
        <Button component={RouterLink} to={href('knowledge/storage')} startIcon={<CloudUploadOutlined />} variant="contained">Upload documents</Button></Stack>} />
    <Tabs value={view} onChange={(_, value) => { setView(value); setPage(0) }} sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}><Tab value="sources" label="All sources" /><Tab value="documents" label="Documents" /></Tabs>
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0, gap: 3, flexDirection: { xs: 'column', lg: 'row' } }}>
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, minWidth: 0 }}>
        <TextField size="small" label="Find a source" value={search} onChange={event => { setSearch(event.target.value); setPage(0) }} sx={{ mb: 1.5 }} />
        {error && <Alert severity="error">{error.message}</Alert>}
        {!target && <Alert severity="info">No applied release is available in this environment. You can prepare capabilities and publish a version before connecting its knowledge.</Alert>}
        {target && context.isPending && <CircularProgress size={24} aria-label="Loading knowledge" />}
        {ready && !rows.length && !documents.isFetching && !integrations.isFetching && <Alert severity="info">No sources match this view. Upload documents or connect an approved source.</Alert>}
        <List disablePadding sx={{ ...brainPaneSx, flex: 1 }} aria-label="Knowledge sources">
          {rows.slice(page * 7, page * 7 + 7).map(row => <ListItemButton key={`${row.kind}:${row.id}`} component={RouterLink} to={href(row.path)} sx={{ py: 1.75, borderBottom: 1, borderColor: 'divider', gap: 2 }}>
            <ListItemIcon sx={{ minWidth: 24 }}><row.Icon color="primary" /></ListItemIcon>
            <ListItemText primary={row.label} secondary={row.error ?? `${row.kind} / ${row.detail}`} primaryTypographyProps={{ fontSize: 13, fontWeight: 600, sx: { overflowWrap: 'anywhere' } }} secondaryTypographyProps={{ fontSize: 12, color: row.error ? 'error.main' : 'text.secondary' }} />
            <Stack alignItems="flex-end" gap={0.5} sx={{ flexShrink: 0 }}><Chip label={row.status} color={row.error ? 'error' : 'default'} variant="outlined" />
              <Typography variant="caption" color="text.secondary">{row.updated ? new Date(row.updated).toLocaleDateString() : 'No sync yet'}</Typography></Stack>
          </ListItemButton>)}
        </List>
        <TablePagination component="div" count={rows.length} page={Math.min(page, Math.max(0, Math.ceil(rows.length / 7) - 1))} rowsPerPage={7} rowsPerPageOptions={[7]} onPageChange={(_, value) => setPage(value)} />
      </Box>
      <Box component="aside" sx={{ width: { lg: 285 }, minWidth: 0, borderLeft: { lg: 1 }, borderColor: 'divider', pl: { lg: 3 } }}>
        <Typography variant="subtitle1" sx={{ mb: 1 }}>Keep answers current</Typography><Typography variant="body2" color="text.secondary">Uploaded files stay in your storage. Indexing makes approved content available to your AI.</Typography>
        <Divider sx={{ my: 2 }} /><Stack alignItems="flex-start" gap={1.5}>
          <Button component={RouterLink} to={href('knowledge/storage')}>Storage & uploads</Button>
          <Button component={RouterLink} to={href('knowledge/discover')}>Add stored documents</Button>
          <Button component={RouterLink} to={href('knowledge/connections')}>Manage live connections</Button>
          <Button component={RouterLink} to={href('knowledge/proof')}>Check active evidence</Button>
          <Button component={RouterLink} to={href('knowledge/retention')}>Technical retention settings</Button>
        </Stack>
      </Box>
    </Box>
  </>
}
