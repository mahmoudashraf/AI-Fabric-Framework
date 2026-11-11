import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import CloseRoundedIcon from '@mui/icons-material/CloseRounded'
import CodeRoundedIcon from '@mui/icons-material/CodeRounded'
import ExtensionRoundedIcon from '@mui/icons-material/ExtensionRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined'
import CableRoundedIcon from '@mui/icons-material/CableRounded'
import {
  Alert, Box, Button, Chip, Divider, IconButton, InputAdornment, Link, List, ListItemButton,
  ListItemText, Pagination, Stack, Tab, Tabs, TextField, Tooltip, Typography, Checkbox, FormControlLabel,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useLocation, useMatch } from 'react-router-dom'
import {
  fetchDeploymentDraft, fetchDeploymentMarketplaceInstalls, fetchMarketplacePlugins, fetchDeploymentWorkspace,
  type DeploymentMarketplaceInstallSummary, type MarketplacePluginSummary,
} from '../api/platformApi'
import { ActionsPage, type ActionTechnicalView } from '../pages/ActionsPage'
import { MarketplacePage } from '../pages/MarketplacePage'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'
import { useBrainWorkspace } from './BrainWorkspaceContext'
import { McpConnections } from '../mcp/McpConnections'

type RecordValue = Record<string, unknown>
type Capability = {
  key: string
  label: string
  purpose: string
  action: RecordValue | null
  install: DeploymentMarketplaceInstallSummary | null
  kind: 'action' | 'knowledge' | 'integration'
}

function record(value: unknown): RecordValue {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as RecordValue : {}
}

function text(value: unknown): string { return typeof value === 'string' ? value : '' }
function readable(value: string): string { return value.replace(/[_-]/g, ' ') }

export function capabilityRows(installs: DeploymentMarketplaceInstallSummary[], actionsConfig: unknown): Capability[] {
  const candidates = record(actionsConfig).actions
  const actions = (Array.isArray(candidates) ? candidates : []).map(record).filter((action) => text(action.name))
  const byName = new Map(actions.map((action) => [text(action.name), action]))
  const owned = new Set<string>()
  const rows: Capability[] = []
  for (const install of installs) {
    if (install.pluginType === 'INFERENCE_PROFILE') continue
    for (const actionId of install.contributions.actionIds) {
      const action = byName.get(actionId) ?? null
      owned.add(actionId)
      rows.push({
        key: `install:${install.id}:action:${actionId}`,
        label: text(action?.displayName) || readable(actionId),
        purpose: text(action?.description) || 'Action definition unavailable in the saved draft.',
        action, install, kind: 'action',
      })
    }
    if (install.contributions.actionIds.length > 0 || install.pluginType === 'TEMPLATE') continue
    if (install.pluginType === 'DATA' || install.pluginType === 'ACTION' || install.pluginType === 'INTEGRATION') {
      rows.push({ key: `install:${install.id}`, label: install.pluginDisplayName,
        purpose: install.pluginType === 'DATA' ? 'Connected knowledge source' : 'Connected service',
        action: null, install, kind: install.pluginType === 'DATA' ? 'knowledge' : 'integration' })
    }
  }
  for (const action of actions) {
    const name = text(action.name)
    if (!owned.has(name)) rows.push({ key: `action:${name}`, label: text(action.displayName) || readable(name),
      purpose: text(action.description) || 'No purpose declared.', action, install: null, kind: 'action' })
  }
  return rows
}

export function availableCapabilities(plugins: MarketplacePluginSummary[]): MarketplacePluginSummary[] {
  return plugins.filter((plugin) => plugin.pluginType === 'ACTION' || plugin.pluginType === 'DATA' || plugin.pluginType === 'INTEGRATION')
}

function statusLabel(row: Capability): string {
  if (!row.install) return 'Configured in draft'
  if (row.install.status !== 'ENABLED') return readable(row.install.status)
  if (row.install.readinessStatus === 'READY') return 'Draft ready'
  return readable(row.install.readinessStatus) || 'Readiness unavailable'
}

export function capabilityInputs(action: RecordValue): { name: string; description: string; type: string; required: boolean }[] {
  const required = new Set(Array.isArray(action.requiredParameters) ? action.requiredParameters.filter((value) => typeof value === 'string') : [])
  const params = Array.isArray(action.params) ? action.params.map(record) : []
  const inputs = params.filter((param) => text(param.name)).map((param) => ({ name: text(param.name),
    description: text(param.description), type: text(param.type), required: param.required === true || required.has(text(param.name)) }))
  for (const name of required) if (!inputs.some((input) => input.name === name)) inputs.push({ name, description: '', type: '', required: true })
  return inputs
}

const PAGE_SIZE = 8
const pageSx = {
  height: '100%', minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column',
  '& > .MuiStack-root:first-of-type': { flexWrap: { xs: 'wrap', sm: 'nowrap' } },
  '& .MuiTypography-root': { overflowWrap: 'anywhere' },
} as const

export function BrainCapabilitiesPage() {
  const { brainId, targetProfileId, href } = useBrainWorkspace()
  const location = useLocation()
  const match = useMatch('/smart-brains/:brainId/capabilities/*')
  const detailKey = match?.params['*'] ?? ''
  const available = detailKey === 'add'
  const params = new URLSearchParams(location.search)
  const mcpView = params.get('view') === 'mcp' || params.has('mcp')
  const technical = params.get('view') === 'technical'
  const connection = params.get('view') === 'connection'
  const selectedPluginId = params.get('pluginId') ?? ''
  const actionPanel = params.get('panel')
  const initialActionView: ActionTechnicalView = actionPanel === 'webhooks' || actionPanel === 'deliveries' || actionPanel === 'routing' ? actionPanel : 'catalogue'
  const dataSourcesOnly = available && params.get('type') === 'DATA'
  const setup = available && params.get('view') === 'setup'
  const [catalogueView, setCatalogueView] = useState('recommended')
  const [showDemo, setShowDemo] = useState(false)
  const [expandedFamilies, setExpandedFamilies] = useState<string[]>([])
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const installsQuery = useQuery({ queryKey: ['deployment-marketplace-installs', brainId], queryFn: () => fetchDeploymentMarketplaceInstalls(brainId) })
  const draftQuery = useQuery({ queryKey: ['deployment-draft', brainId], queryFn: () => fetchDeploymentDraft(brainId) })
  const workspaceQuery = useQuery({ queryKey: ['deployment-workspace', brainId], queryFn: () => fetchDeploymentWorkspace(brainId) })
  const customerId = workspaceQuery.data?.deployment.binding?.customerId ?? ''
  const pluginsQuery = useQuery({ queryKey: ['marketplace-plugins', customerId], queryFn: () => fetchMarketplacePlugins(customerId), enabled: available && workspaceQuery.isSuccess })
  const rows = useMemo(() => capabilityRows(installsQuery.data ?? [], draftQuery.data?.actionsConfig), [installsQuery.data, draftQuery.data?.actionsConfig])
  const plugins = useMemo(() => availableCapabilities(pluginsQuery.data ?? []).filter(plugin => !dataSourcesOnly || plugin.pluginType === 'DATA'), [pluginsQuery.data, dataSourcesOnly])
  const needle = search.trim().toLowerCase()
  const filteredRows = rows.filter((row) => `${row.label} ${row.purpose} ${row.install?.pluginDisplayName ?? ''}`.toLowerCase().includes(needle))
  const filteredPlugins = plugins.filter((plugin) => (showDemo || !['DEMO', 'INTERNAL'].includes(plugin.catalogueAudience ?? 'CUSTOMER'))
    && (catalogueView !== 'private' || plugin.visibility === 'CUSTOMER_PRIVATE')
    && (catalogueView !== 'recommended' || !plugin.catalogueFamily || ['DOCUMENT_STORAGE', 'HTTP_JSON'].includes(plugin.catalogueFamily) || plugin.visibility === 'CUSTOMER_PRIVATE')
    && `${plugin.displayName} ${plugin.shortDescription} ${plugin.publisherDisplayName}`.toLowerCase().includes(needle))
  const groupedPlugins = useMemo(() => {
    const groups = new Map<string, MarketplacePluginSummary[]>()
    for (const plugin of filteredPlugins) {
      const key = plugin.visibility === 'CUSTOMER_PRIVATE' ? plugin.id : plugin.catalogueFamily || plugin.id
      groups.set(key, [...(groups.get(key) ?? []), plugin])
    }
    return [...groups].map(([key, options]) => ({ key, options }))
  }, [pluginsQuery.data, catalogueView, showDemo, needle, dataSourcesOnly])
  const selected = params.get('view') === 'list' ? undefined : detailKey && !available ? rows.find((row) => row.key === detailKey) : rows[0]
  const selectedPlugin = plugins.find((plugin) => plugin.id === selectedPluginId)
  const count = available ? groupedPlugins.length : filteredRows.length
  const maxPage = Math.max(1, Math.ceil(count / PAGE_SIZE))
  const currentPage = Math.min(page, maxPage)
  const hasDetail = available ? Boolean(selectedPlugin) : Boolean(selected)

  useEffect(() => { setSearch(''); setPage(1) }, [available, brainId])
  const link = (section: string, extra: Record<string, string> = {}) => {
    const [path, query] = href(section).split('?')
    const next = new URLSearchParams(query)
    if (dataSourcesOnly && section === 'capabilities/add') next.set('type', 'DATA')
    for (const [key, value] of Object.entries(extra)) next.set(key, value)
    return `${path}${next.size ? `?${next}` : ''}`
  }
  const selectedPath = selected ? `capabilities/${encodeURIComponent(selected.key)}` : 'capabilities'
  const loading = installsQuery.isPending || draftQuery.isPending || workspaceQuery.isPending || (available && pluginsQuery.isPending)
  const error = installsQuery.error ?? draftQuery.error ?? workspaceQuery.error ?? (available ? pluginsQuery.error : null)
  const libraryParams = new URLSearchParams({ returnTo: link('capabilities/add') })
  if (customerId) libraryParams.set('customerId', customerId)
  const libraryHref = `/library/plugins?${libraryParams}`
  const creatorHref = `/library/plugins/new/data?${libraryParams}`

  if (mcpView) return <McpConnections key={`${brainId}:${targetProfileId}`} brainId={brainId} targetProfileId={targetProfileId}
    canManage={workspaceQuery.data?.access.canAdmin === true} canImport={workspaceQuery.data?.access.canEdit === true} actionsConfig={draftQuery.data?.actionsConfig} href={href} />

  if (setup && selectedPlugin || connection && selected?.install) {
    const pluginId = setup ? selectedPlugin!.id : selected!.install!.pluginId
    const label = setup ? selectedPlugin!.displayName : selected!.install!.pluginDisplayName
    return <Box sx={pageSx}>
      <BrainPageHeader title={`Connect ${label}`} action={<Button component={RouterLink} to={setup ? link('capabilities/add', { pluginId }) : href(selectedPath)} startIcon={<ArrowBackRoundedIcon />}>Back</Button>} />
      <Box sx={{ ...brainPaneSx, flex: 1 }}><MarketplacePage capabilityView={{ brainId, pluginId, pluginVersion: params.get('pluginVersion') ?? undefined }} /></Box>
    </Box>
  }

  if (detailKey === 'actions') return <Box sx={pageSx}>
    <BrainPageHeader title="Action configuration" action={<Button component={RouterLink} to={href('capabilities')} startIcon={<ArrowBackRoundedIcon />}>Capabilities</Button>} />
    <Box sx={{ ...brainPaneSx, flex: 1 }}><ActionsPage brainId={brainId} workspaceMode initialTechnicalView={initialActionView} /></Box>
  </Box>

  return <Box sx={pageSx}>
    <BrainPageHeader title={dataSourcesOnly ? 'Add knowledge' : 'What your AI can do'} action={<Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" justifyContent="flex-end">
      {!dataSourcesOnly ? <Button component={RouterLink} to={link('capabilities', { view: 'mcp', mcp: 'connect' })} startIcon={<CableRoundedIcon />} disabled={!workspaceQuery.data?.access.canAdmin}>Connect MCP</Button> : null}
      {available ? <>
        <Button component={RouterLink} to={libraryHref} startIcon={<ExtensionRoundedIcon />}>My plugins</Button>
        <Button component={RouterLink} to={creatorHref} variant="outlined" startIcon={<AddRoundedIcon />}>Create a data plugin</Button>
      </> : <>
      <Button component={RouterLink} to={link('capabilities/actions', { panel: 'webhooks' })} size="small">Webhooks &amp; deliveries</Button>
      <Tooltip title="Action configuration"><IconButton component={RouterLink} to={href('capabilities/actions')} aria-label="Action configuration"><CodeRoundedIcon /></IconButton></Tooltip>
      <Button component={RouterLink} to={href('capabilities/add')} variant="contained" startIcon={<AddRoundedIcon />} sx={{ flexShrink: 0 }}>Add capability</Button>
      </>}
    </Stack>} />
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: hasDetail ? 'minmax(280px, .95fr) minmax(340px, 1fr)' : 'minmax(0, 1fr)' }, flex: 1, minHeight: 0 }}>
      <Box sx={{ display: { xs: hasDetail && (Boolean(detailKey && !available) || Boolean(selectedPluginId)) ? 'none' : 'flex', lg: 'flex' }, flexDirection: 'column', minHeight: 0, minWidth: 0, pr: { lg: hasDetail ? 3 : 0 }, pb: { xs: 2, lg: 0 } }}>
        <Tabs value={available ? 'available' : 'connected'} aria-label="Capability views" sx={{ flexShrink: 0, borderBottom: 1, borderColor: 'divider' }}>
          <Tab component={RouterLink} to={href('capabilities')} label={`Connected (${rows.length})`} value="connected" />
          <Tab component={RouterLink} to={href('capabilities/add')} label="Available" value="available" />
          {!dataSourcesOnly ? <Tab component={RouterLink} to={link('capabilities', { view: 'mcp' })} label="MCP connections" value="mcp" /> : null}
        </Tabs>
        {dataSourcesOnly && <Typography variant="caption" color="text.secondary" sx={{ mt: 1.5 }}>Data sources <Link component={RouterLink} to={href('capabilities/add')} sx={{ ml: 1 }}>All capabilities</Link></Typography>}
        {available ? <Tabs value={catalogueView} onChange={(_, value) => { setCatalogueView(value); setPage(1) }} aria-label="Plugin sources" sx={{ mt: 1 }}>
          <Tab value="recommended" label="Recommended" /><Tab value="private" label="My plugins" /><Tab value="all" label="All plugins" />
        </Tabs> : null}
        <TextField size="small" placeholder="Find a capability" inputProps={{ 'aria-label': 'Find a capability' }} value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(1) }} sx={{ my: 2 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> }} />
        {loading ? <Typography color="text.secondary">Loading capabilities...</Typography> : error ? <Alert severity="error" action={<Button onClick={() => { void installsQuery.refetch(); void draftQuery.refetch(); if (available) void pluginsQuery.refetch() }}>Retry</Button>}>{error.message}</Alert> : <>
          <Box sx={{ ...brainPaneSx, flex: 1 }}>
            <List disablePadding aria-label={available ? 'Available capabilities' : 'Connected capabilities'}>
              {available ? groupedPlugins.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map(({ key, options }) => <Box key={key}>
                {options.length > 1 ? <ListItemButton onClick={() => setExpandedFamilies(current => current.includes(key) ? current.filter(value => value !== key) : [...current, key])} aria-expanded={expandedFamilies.includes(key)} sx={{ minHeight: 76, borderBottom: 1, borderColor: 'divider', gap: 1.5 }}>
                  {key === 'DOCUMENT_STORAGE' ? <DescriptionOutlinedIcon color="success" /> : <SyncOutlinedIcon color="primary" />}
                  <ListItemText primary={key === 'DOCUMENT_STORAGE' ? 'Documents from storage' : key === 'HTTP_JSON' ? 'Live information from an API' : 'Source connections'} secondary={`${options.length} options / choose the integration that fits your source`} primaryTypographyProps={{ fontWeight: 600 }} />
                  <Typography variant="caption">{expandedFamilies.includes(key) ? 'Hide options' : 'Show options'}</Typography>
                </ListItemButton> : null}
                {options.length === 1 || expandedFamilies.includes(key) ? options.map(plugin => <ListItemButton key={plugin.id}
                component={RouterLink} to={link('capabilities/add', { pluginId: plugin.id })} selected={selectedPlugin?.id === plugin.id}
                sx={{ borderBottom: 1, borderColor: 'divider', minHeight: 70, gap: 1.5, px: options.length > 1 ? 3 : 1.5 }}>
                {plugin.catalogueFamily === 'DOCUMENT_STORAGE' ? <DescriptionOutlinedIcon color="success" /> : plugin.catalogueFamily === 'HTTP_JSON' ? <SyncOutlinedIcon color="primary" /> : <ExtensionRoundedIcon fontSize="small" color="action" />}
                <ListItemText primary={plugin.displayName} secondary={<>{plugin.shortDescription}<Typography component="span" display="block" variant="caption" sx={{ mt: .5 }}>{plugin.visibility === 'CUSTOMER_PRIVATE' ? 'Private to your workspace' : plugin.publisherDisplayName}{plugin.adapterContract ? ` / ${plugin.adapterContract}` : ''}</Typography></>} primaryTypographyProps={{ fontWeight: 600 }} />
                {installsQuery.data?.some((install) => install.pluginId === plugin.id) ? <Chip size="small" label="Added" variant="outlined" /> : null}
              </ListItemButton>) : null}</Box>) : filteredRows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map((row) => <ListItemButton key={row.key}
                component={RouterLink} to={href(`capabilities/${encodeURIComponent(row.key)}`)} selected={selected?.key === row.key}
                sx={{ borderBottom: 1, borderColor: 'divider', minHeight: 74, px: 1.5, gap: 1.5, alignItems: 'flex-start' }}>
                <ExtensionRoundedIcon fontSize="small" color="action" sx={{ mt: 1 }} />
                <ListItemText primary={row.label} secondary={row.purpose} primaryTypographyProps={{ fontWeight: 600 }} secondaryTypographyProps={{ sx: { display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' } }} />
                <Typography variant="caption" color={row.install?.status === 'DISABLED' ? 'text.secondary' : 'text.primary'} sx={{ minWidth: 88, maxWidth: 120, pt: 1, textAlign: 'right' }}>{statusLabel(row)}</Typography>
              </ListItemButton>)}
            </List>
            {count === 0 ? <Stack spacing={1} sx={{ py: 3 }}><Typography color="text.secondary">{search ? 'No matching capabilities.' : available && catalogueView === 'private' ? 'No reusable plugins in your workspace yet.' : available ? 'No capabilities are available in this view.' : 'No capabilities have been configured.'}</Typography>
              {search ? <Button sx={{ alignSelf: 'flex-start' }} onClick={() => setSearch('')}>Clear search</Button> : available && catalogueView === 'private' ? <Button component={RouterLink} to={creatorHref} sx={{ alignSelf: 'flex-start' }}>Create a data plugin</Button> : null}</Stack> : null}
          </Box>
          {maxPage > 1 ? <Pagination count={maxPage} page={currentPage} onChange={(_, value) => setPage(value)} size="small" sx={{ pt: 2 }} /> : null}
          {available ? <FormControlLabel sx={{ mt: 1, flexShrink: 0 }} control={<Checkbox size="small" checked={showDemo} onChange={(_, checked) => { setShowDemo(checked); setPage(1) }} />} label={<Typography variant="caption">Show demo sources</Typography>} /> : null}
        </>}
      </Box>
      {hasDetail && !loading && !error ? <Box component="section" aria-label="Capability details" sx={{ display: 'flex', flexDirection: 'column', minHeight: 0, minWidth: 0, borderLeft: { lg: 1 }, borderTop: { xs: 1, lg: 0 }, borderColor: { xs: 'divider', lg: 'divider' }, pl: { lg: 3 }, pt: { xs: 2, lg: 0 } }}>
        {available && selectedPlugin ? <>
          <Button component={RouterLink} to={href('capabilities/add')} startIcon={<ArrowBackRoundedIcon />} sx={{ display: { xs: selectedPluginId ? 'inline-flex' : 'none', lg: 'none' }, alignSelf: 'flex-start', mb: 1 }}>Available</Button>
          <Typography component="h2" sx={{ fontSize: 21, fontWeight: 650, mb: 1 }}>{selectedPlugin.displayName}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{selectedPlugin.shortDescription}</Typography>
          <Stack spacing={2} sx={{ ...brainPaneSx, flex: 1 }}>
            <Chip size="small" label={selectedPlugin.visibility === 'CUSTOMER_PRIVATE' ? 'Private to your workspace' : selectedPlugin.publisherDisplayName} sx={{ alignSelf: 'flex-start' }} />
            <Box><Typography variant="subtitle2">What this adds</Typography><Typography variant="body2" color="text.secondary">{selectedPlugin.pluginType === 'DATA' ? 'Approved information your AI can search and use in answers. Your source system remains the source of truth.' : 'A reusable capability with its own declared inputs and access rules.'}</Typography></Box>
            <Box><Typography variant="subtitle2">Setup</Typography><Typography variant="body2" color="text.secondary">Choose version {selectedPlugin.latestVersion ?? '(unavailable)'}, provide its declared connection inputs, then review the Brain draft.</Typography></Box>
            <Typography variant="caption" color="text.secondary">This does not change your live experience or start indexing.</Typography>
            <Button component={RouterLink} to={link('capabilities/add', { pluginId: selectedPlugin.id, view: 'setup' })} variant="contained" disabled={!selectedPlugin.latestVersion || !workspaceQuery.data?.access.canEdit} sx={{ alignSelf: 'flex-start' }}>Set up for this Brain</Button>
          </Stack>
        </> : selected ? <>
          <Stack direction="row" alignItems="flex-start" justifyContent="space-between" gap={1}>
            <Box sx={{ minWidth: 0 }}><Typography component="h2" sx={{ fontSize: 21, fontWeight: 650 }}>{selected.label}</Typography>
              <Chip size="small" icon={<ExtensionRoundedIcon />} label={selected.install?.pluginDisplayName ?? 'Draft action'} variant="outlined" sx={{ my: 1.5, maxWidth: '100%', '& .MuiChip-label': { overflow: 'hidden' } }} /></Box>
            <Tooltip title="Close details"><IconButton component={RouterLink} to={link('capabilities', { view: 'list' })} aria-label="Close details" size="small"><CloseRoundedIcon /></IconButton></Tooltip>
          </Stack>
          <Tabs value={technical ? 'technical' : 'settings'} aria-label="Capability detail view" sx={{ borderBottom: 1, borderColor: 'divider', mb: 2, flexShrink: 0 }}>
            <Tab component={RouterLink} to={href(selectedPath)} value="settings" label="Settings" />
            <Tab component={RouterLink} to={link(selectedPath, { view: 'technical' })} value="technical" label="Technical" />
          </Tabs>
          <Box sx={{ ...brainPaneSx, flex: 1 }}>
            {technical ? selected.kind === 'action' ? <ActionsPage key={`${brainId}:${selected.key}`} brainId={brainId} actionName={text(selected.action?.name)} workspaceMode />
              : selected.install ? <MarketplacePage capabilityView={{ brainId, pluginId: selected.install.pluginId }} /> : null
              : connection && selected.install ? <>
                <Button component={RouterLink} to={href(selectedPath)} startIcon={<ArrowBackRoundedIcon />} sx={{ mb: 2 }}>Capability settings</Button>
                <MarketplacePage capabilityView={{ brainId, pluginId: selected.install.pluginId }} />
              </> : <CapabilitySettings row={selected} href={href} connectionHref={link(selectedPath, { view: 'connection' })} technicalHref={link(selectedPath, { view: 'technical' })} />}
          </Box>
        </> : null}
      </Box> : !loading && !error && ((detailKey && !available) || selectedPluginId) ? <Alert severity="warning">This capability is not available in this Brain. <Link component={RouterLink} to={href('capabilities')}>View connected capabilities</Link></Alert> : null}
    </Box>
  </Box>
}

function CapabilitySettings({ row, href, connectionHref, technicalHref }: { row: Capability; href: (section?: string) => string; connectionHref: string; technicalHref: string }) {
  const inputs = row.action ? capabilityInputs(row.action) : []
  const mcp = row.action?.adapterType === 'mcp-tool' ? record(record(row.action.execution).mcp) : null
  const [mcpPath, mcpQuery] = href('capabilities').split('?')
  const mcpParams = new URLSearchParams(mcpQuery)
  mcpParams.set('view', 'mcp')
  if (text(mcp?.connectionId)) mcpParams.set('mcp', text(mcp?.connectionId))
  return <Stack spacing={2.5} sx={{ pb: 2 }}>
    <Box><Typography variant="subtitle2">What happens</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>{row.purpose}</Typography></Box>
    {row.action ? <>
      <Box><Typography variant="subtitle2">Customer inputs</Typography>
        {inputs.length ? <List dense disablePadding>{inputs.map((input) => <ListItemText key={input.name} primary={`${readable(input.name)}${input.required ? ' (required)' : ' (optional)'}`} secondary={[input.type, input.description].filter(Boolean).join(' - ')} primaryTypographyProps={{ variant: 'body2' }} />)}</List> : <Typography variant="body2" color="text.secondary">No inputs declared.</Typography>}
      </Box>
      <Box><Typography variant="subtitle2">Customer confirmation</Typography><Typography variant="body2" color="text.secondary">{row.action.requiresConfirmation === true ? 'Required before execution.' : row.action.requiresConfirmation === false ? 'Not required by this action definition.' : 'Not declared in the action definition.'}</Typography></Box>
      <Box><Typography variant="subtitle2">Sign-in</Typography><Typography variant="body2" color="text.secondary">{row.action.anonymousAllowed === true ? 'Anonymous access is allowed by this action.' : 'Anonymous access is not enabled.'}</Typography>
        <Link component={RouterLink} to={technicalHref} fontSize={13}>Edit action access &amp; requirements</Link></Box>
    </> : null}
    <Divider />
    <Box><Typography variant="subtitle2">Connected destination</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>{mcp ? `MCP tool: ${text(mcp.toolName) || 'Not declared'}` : row.install?.pluginDisplayName ?? 'Configured action route'}</Typography>
      {mcp ? <><Typography variant="caption" color="text.secondary" display="block">Connection {text(mcp.serverRef) || text(mcp.connectionId) || 'Not declared'}</Typography><Button component={RouterLink} to={`${mcpPath}?${mcpParams}`} size="small" sx={{ px: 0, mt: .5 }}>View MCP connection</Button></> : null}
      {row.install ? <Button component={RouterLink} to={connectionHref} size="small" sx={{ px: 0, mt: .5 }}>View connection</Button> : null}
      {row.kind === 'knowledge' || Boolean(row.install?.contributions.knowledgeSourceIds.length) ? <Link component={RouterLink} to={href('knowledge')} sx={{ display: 'block', mt: 1 }}>Open knowledge sources</Link> : null}
      {row.install?.contributions.conversationChannel ? <Link component={RouterLink} to={href('human-support')} sx={{ display: 'block', mt: 1 }}>Open human support</Link> : null}
    </Box>
    <Box><Typography variant="subtitle2">Installation state</Typography><Typography variant="body2" color="text.secondary">{row.install ? `Draft: ${readable(row.install.status)}. Readiness: ${readable(row.install.readinessStatus)}.` : 'Configured in the shared draft.'}</Typography>
      {row.install ? <Typography variant="caption" color="text.secondary">Reported live state: {readable(row.install.liveState)}. Target status is shown in the workspace header.</Typography> : null}
    </Box>
    {row.install?.warnings.length ? <Alert severity="warning">{row.install.warnings.join(' ')}</Alert> : null}
  </Stack>
}
